package com.golemlogix.blockentity;

import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.MainframeStructure;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cryo-Mainframe: a cabinet of Cryo-Calculators. Every calculator inside gives several independent cores (autocraft
 * processors, one job per core), and every Compressed Co-Processor scales up all cores at once. In the network the
 * whole cabinet counts as one device on one channel.
 */
public class CryoMainframeBlockEntity extends BlockEntity {
    public static final int CALC_SLOTS = 12;
    public static final int EVENT_VIEWERS = ViewerTracker.EVENT_VIEWERS;
    private static final int BOOST_HOLD = 60;

    private final OwnedContainer calculators = new OwnedContainer(CALC_SLOTS, this,
            s -> s.is(ModBlocks.CRYO_CALCULATOR.get().asItem())) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            syncCores(true);
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.MAINFRAME, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private final ViewerTracker viewerTracker = new ViewerTracker();
    private final List<Core> cores = new ArrayList<>();
    private boolean active = false;
    private boolean boosted = false;
    private long boostUntil = 0;
    private long helpUntil = 0;
    private long lastSync = -100;
    /** Last-synced client summary values (avoids allocating a summary String on every 10-tick check). */
    private int sentCores = -1, sentBusy = -1, sentCalcs = -1, sentWorkers = -1;
    private boolean sentActive, sentBoosted;
    @Nullable
    private Item sentItem0, sentItem1;
    private final VisualHold[] holds = {new VisualHold(), new VisualHold()};

    // --- client
    private int clientCores, clientBusy, clientCalcs, clientWorkers;
    private boolean clientBoosted;
    private final ItemStack[] clientCurrent = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] visualItems = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final CraftingCpus.Animation animation = new CraftingCpus.Animation();
    /** Door opening 0..1, work spin-up 0..1, and foreman visibility 0..1 (read by renderer). */
    public float doors, doorsPrev, spin, spinPrev, foreman, foremanPrev;
    public int visualWorkTimer = 0;
    /** Smoothed poses of the three copper golems (shelves 0 and 1, roof): x, y, z, yaw, last frame time, "already set". */
    public final float[][] golemSmooth = new float[3][6];
    /** Heads of the built-in atlants (left, right): {yaw, pitch}, current and previous tick. */
    public final float[][] heads = {{0f, 0f}, {0f, 0f}}, headsPrev = {{0f, 0f}, {0f, 0f}};

    private boolean devWorking = false;
    private ItemStack devItem0 = ItemStack.EMPTY, devItem1 = ItemStack.EMPTY;

    public CryoMainframeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_MAINFRAME.get(), pos, state);
    }

    public OwnedContainer getCalculators() {
        return calculators;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public List<Core> cores() {
        return cores;
    }

    public boolean formed() {
        BlockState s = getBlockState();
        return s.hasProperty(CryoMainframeBlock.FORMED) && s.getValue(CryoMainframeBlock.FORMED);
    }

    public int calculatorCount() {
        int n = 0;
        for (int i = 0; i < calculators.getContainerSize(); i++) {
            if (!calculators.getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public static int coresPerCalculator() {
        return GolemConfig.MAINFRAME_CORES_PER_CALCULATOR.get();
    }

    public static int coprocessorMultiplier() {
        return GolemConfig.MAINFRAME_COPROCESSOR_MULTIPLIER.get();
    }

    public int workersPerCore() {
        return CryoCalculatorBlockEntity.BASE_WORKERS
                + CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED * upgrades.count(UpgradeType.SPEED) * coprocessorMultiplier();
    }

    public boolean isBoosted() {
        return boosted;
    }

    public int busyCores() {
        int n = 0;
        for (Core c : cores) {
            if (c.job != null) {
                n++;
            }
        }
        return n;
    }

    /** Adjusts core count to match installed calculators, refunding jobs from removed cores when {@code refund} is true. */
    private void syncCores(boolean refund) {
        int target = calculatorCount() * coresPerCalculator();
        boolean changed = cores.size() != target;
        while (cores.size() < target) {
            cores.add(new Core(cores.size()));
        }
        while (cores.size() > target) {
            Core gone = cores.remove(cores.size() - 1);
            if (gone.job != null && refund) {
                gone.refund();
            }
        }
        if (changed && level != null && !level.isClientSide()) {
            GolemNetwork.clearCache();
        }
    }

    // ------------------------------------------------------------------------------------------------ core

    /** Independent autocraft processor inside the cabinet; all cores share one block and channel. */
    public final class Core implements CraftingCpu {
        private final int index;
        @Nullable
        private CraftingJob job;

        Core(int index) {
            this.index = index;
        }

        @Override
        @Nullable
        public CraftingJob getJob() {
            return job;
        }

        @Override
        public void startJob(CraftingJob newJob) {
            job = newJob;
            setChanged();
            syncNow();
        }

        @Override
        public void cancelJob() {
            if (job == null || level == null || level.isClientSide()) {
                return;
            }
            refund();
            setChanged();
            syncNow();
        }

        private void refund() {
            if (job == null || level == null || level.isClientSide()) {
                job = null;
                return;
            }
            CraftingCpus.refundAndDrop(level, worldPosition, job, GolemNetwork.get(level, worldPosition), 1.0);
            job = null;
        }

        @Override
        public long wants(ItemKey key) {
            return job == null ? 0L : job.wants(key);
        }

        @Override
        public ItemStack absorb(ItemStack stack) {
            if (job == null) {
                return stack;
            }
            ItemStack rest = job.absorb(stack);
            if (rest.getCount() != stack.getCount()) {
                setChanged();
            }
            return rest;
        }

        @Override
        public int absorbFluid(FluidStack fluid) {
            int n = job == null ? 0 : job.absorbFluid(fluid);
            if (n > 0) {
                setChanged();
            }
            return n;
        }

        @Override
        public boolean isRemoved() {
            return CryoMainframeBlockEntity.this.isRemoved() || !cores.contains(this);
        }

        @Override
        public boolean acceptsHelp() {
            return formed() && !upgrades.has(UpgradeType.REDSTONE);
        }

        @Override
        public BlockPos cpuPos() {
            return worldPosition;
        }

        @Override
        @Nullable
        public Level cpuLevel() {
            return level;
        }

        @Override
        public int core() {
            return index + 1;
        }

        @Override
        public int getWorkers() {
            return workersPerCore();
        }

        @Override
        public int getCoprocessors() {
            return upgrades.count(UpgradeType.SPEED) * coprocessorMultiplier();
        }

        @Override
        public boolean canRun(GolemNetwork net) {
            return formed() && CraftingCpu.super.canRun(net);
        }
    }

    // ------------------------------------------------------------------------------------------------ window

    /** Window data: 0 = redstone mode, 1 = cores, 2 = busy, 3 = steps per core, 4 = calculators, 5 = multiplier. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> redstone.mode().ordinal();
                    case 1 -> cores.size();
                    case 2 -> busyCores();
                    case 3 -> workersPerCore();
                    case 4 -> calculatorCount();
                    case 5 -> coprocessorMultiplier();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    public int viewers() {
        return viewerTracker.viewers();
    }

    public void startOpen() {
        viewerTracker.open(this, () -> {
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.3f);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.6f);
        });
    }

    public void stopOpen() {
        viewerTracker.close(this, () ->
                level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.3f));
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        return viewerTracker.handleEvent(id, param) || super.triggerEvent(id, param);
    }

    /** Refunds running jobs to the network (dropping overflow) and drops calculators and upgrades. */
    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        GolemNetwork refundTo = GolemNetwork.aroundRemoved(level, worldPosition);
        for (Core c : cores) {
            if (c.job != null) {
                CraftingCpus.refundAndDrop(level, worldPosition, c.job, refundTo, 0.5);
                c.job = null;
            }
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        dropContainer(calculators, x, y, z);
        dropContainer(upgrades, x, y, z);
        cores.clear();
    }

    private void dropContainer(Container container, double x, double y, double z) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, x, y, z, container.removeItemNoUpdate(i));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ work

    public static void serverTick(Level level, BlockPos pos, BlockState state, CryoMainframeBlockEntity be) {
        long time = level.getGameTime();
        if ((time + pos.asLong()) % 20 == 0) {
            be.checkStructure(level, pos, state.getValue(CryoMainframeBlock.FACING));
        }
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, be.redstone);
        boolean wasActive = be.active;
        boolean needSync = false;
        if ((time + pos.asLong()) % CryoCalculatorBlockEntity.CYCLE_TICKS == 0) {
            needSync = be.tickCycle(level, pos, hasRedstone, powered, time);
        }
        boolean boostedNow = time < be.boostUntil && (be.active || be.busyCores() == 0);
        if (boostedNow != be.boosted || wasActive != be.active) {
            be.boosted = boostedNow;
            needSync = true;
        }
        for (VisualHold h : be.holds) {
            needSync |= h.ended(time);
        }
        if (needSync) {
            be.syncNow();
        }
    }

    private void checkStructure(Level level, BlockPos pos, Direction facing) {
        if (!BlockEntities.allLoaded(level, MainframeStructure.CELLS.length, i -> MainframeStructure.cellPos(pos, facing, i))) {
            return;
        }
        boolean valid = MainframeStructure.valid(level, pos, facing);
        if (formed() && !valid) {
            MainframeStructure.disassemble(level, pos, facing, null);
        } else if (!formed() && valid) {
            MainframeStructure.form(level, pos, facing);
        }
    }

    /** Reusable per-cycle tracking fields (avoids allocating a scratch array every 4-tick cycle). */
    private int cycleShelf;
    private boolean cycleClicked;
    private boolean cycleNeedSync;

    private boolean tickCycle(Level level, BlockPos pos, boolean hasRedstone, boolean powered, long time) {
        boolean hasOwnJob = busyCores() > 0;
        if (!formed() || (!hasOwnJob && (cores.isEmpty() || hasRedstone))) {
            active = false;
            return summaryChanged(time);
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        boolean ok = net.isOnline() && net.isDeviceActive(pos);
        // only consume a redstone pulse when an owned job is ready to step
        boolean allowed = redstone.allows(hasRedstone, powered, ok && hasOwnJob);
        int workers = workersPerCore();

        cycleShelf = 0;
        cycleClicked = false;
        cycleNeedSync = false;
        tickOwnCores(level, net, ok, allowed, workers, time);
        if (ok && !hasRedstone) {
            lendIdleCores(net, workers, time);
        }
        if (cycleClicked) {
            level.playSound(null, pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.3f, 1.0f);
        }
        if (hasOwnJob) {
            setChanged();
        }
        active = ok && allowed && (busyCores() > 0 || time < helpUntil);
        return cycleNeedSync || summaryChanged(time);
    }

    private void tickOwnCores(Level level, GolemNetwork net, boolean ok, boolean allowed, int workers, long time) {
        for (Core core : cores) {
            CraftingJob job = core.job;
            if (job == null) {
                continue;
            }
            int myShelf = cycleShelf < holds.length ? cycleShelf++ : -1;
            if (myShelf >= 0 && holds[myShelf].show(job.current().stack(), time)) {
                cycleNeedSync = true;
            }
            if (!ok) {
                continue;
            }
            if (!allowed) {
                if (job.status() != CraftingJob.Status.PAUSED) {
                    job.setStatus(CraftingJob.Status.PAUSED);
                    cycleNeedSync = true;
                }
                continue;
            }
            CraftingJob.Status before = job.status();
            long doneBefore = job.doneExecutions();
            job.tick(net, workers);
            if (myShelf >= 0) {
                holds[myShelf].show(job.current().stack(), time);
            }
            long done = job.doneExecutions() - doneBefore;
            if (done > 0) {
                cycleClicked = true;
            }
            if (done > CryoCalculatorBlockEntity.BASE_WORKERS) {
                boostUntil = time + BOOST_HOLD;
            }
            if (job.isDone()) {
                CryoCalculatorBlockEntity.notifyFinished(level, job);
                core.job = null;
                cycleNeedSync = true;
            } else if (job.status() != before) {
                cycleNeedSync = true;
            }
        }
    }

    private void lendIdleCores(GolemNetwork net, int workers, long time) {
        for (int idle = cores.size() - busyCores(); idle > 0; idle--) {
            CraftingJob target = net.jobWantingHelp();
            if (target == null) {
                break;
            }
            long before = target.doneExecutions();
            target.tick(net, workers);
            long done = target.doneExecutions() - before;
            if (done <= 0) {
                break;
            }
            cycleClicked = true;
            helpUntil = time + 20;
            if (done > CryoCalculatorBlockEntity.BASE_WORKERS) {
                boostUntil = time + BOOST_HOLD;
            }
            if (cycleShelf < holds.length && holds[cycleShelf++].show(target.current().stack(), time)) {
                cycleNeedSync = true;
            }
        }
    }

    private boolean summaryChanged(long time) {
        if (time - lastSync < 10) {
            return false;
        }
        int busy = 0;
        Item item0 = null, item1 = null;
        for (Core c : cores) {
            if (c.job != null) {
                if (busy == 0) {
                    item0 = c.job.current().stack().getItem();
                } else if (busy == 1) {
                    item1 = c.job.current().stack().getItem();
                }
                busy++;
            }
        }
        return cores.size() != sentCores || busy != sentBusy || active != sentActive || boosted != sentBoosted
                || calculatorCount() != sentCalcs || workersPerCore() != sentWorkers
                || item0 != sentItem0 || item1 != sentItem1;
    }

    private void recordSentSummary() {
        int busy = 0;
        Item item0 = null, item1 = null;
        for (Core c : cores) {
            if (c.job != null) {
                if (busy == 0) {
                    item0 = c.job.current().stack().getItem();
                } else if (busy == 1) {
                    item1 = c.job.current().stack().getItem();
                }
                busy++;
            }
        }
        sentCores = cores.size();
        sentBusy = busy;
        sentActive = active;
        sentBoosted = boosted;
        sentCalcs = calculatorCount();
        sentWorkers = workersPerCore();
        sentItem0 = item0;
        sentItem1 = item1;
    }

    private void syncNow() {
        if (level != null && !level.isClientSide()) {
            lastSync = level.getGameTime();
            BlockEntities.sendUpdate(this);
        }
    }

    // ------------------------------------------------------------------------------------------------ client

    public int clientCores() {
        return clientCores;
    }

    public int clientBusy() {
        return clientBusy;
    }

    public int clientCalcs() {
        return clientCalcs;
    }

    public int clientWorkers() {
        return clientWorkers;
    }

    public ItemStack clientCurrent(int shelf) {
        return clientCurrent[shelf];
    }

    public ItemStack clientDisplayItem(int shelf) {
        return !clientCurrent[shelf].isEmpty() ? clientCurrent[shelf] : visualItems[shelf];
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CryoMainframeBlockEntity be) {
        if (state.getValue(CryoMainframeBlock.FORMED)) {
            Direction facing = state.getValue(CryoMainframeBlock.FACING);
            for (int k = 0; k < 2; k++) {
                be.headsPrev[k][0] = be.heads[k][0];
                be.headsPrev[k][1] = be.heads[k][1];
                BlockPos head = MainframeStructure.cellPos(pos, facing, MainframeStructure.ATLANTS[k], 1, 0);
                AtlantHeadBlockEntity.track(level, head, facing, be.heads[k]);
            }
        }
        be.doorsPrev = be.doors;
        be.doors = Math.max(0f, Math.min(1f, be.doors + (be.viewerTracker.isClientOpen() ? 0.09f : -0.07f)));

        boolean on = be.clientBusy > 0 || !be.clientCurrent[0].isEmpty() || !be.clientCurrent[1].isEmpty();
        if (on) {
            for (int i = 0; i < 2; i++) {
                if (!be.clientCurrent[i].isEmpty()) {
                    be.visualItems[i] = be.clientCurrent[i];
                }
            }
        }
        be.animation.visualWorkTimer = be.visualWorkTimer;
        if (be.animation.tick(on, be.clientBoosted)) {
            be.visualItems[0] = ItemStack.EMPTY;
            be.visualItems[1] = ItemStack.EMPTY;
        }
        be.spin = be.animation.spin;
        be.spinPrev = be.animation.spinPrev;
        be.foreman = be.animation.foreman;
        be.foremanPrev = be.animation.foremanPrev;
        be.visualWorkTimer = be.animation.visualWorkTimer;
    }

    // ------------------------------------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Calculators", calculators.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        ListTag jobs = new ListTag();
        for (Core c : cores) {
            if (c.job != null) {
                CompoundTag e = new CompoundTag();
                e.putInt("Core", c.index);
                e.put("Job", c.job.save(registries));
                jobs.add(e);
            }
        }
        tag.put("Jobs", jobs);
        redstone.save(tag);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null) {
            for (Core c : cores) {
                if (c.job != null) {
                    c.job.resolveLegacyDimension(level.dimension());
                }
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cores.clear();
        calculators.fromTag(tag.getList("Calculators", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        syncCores(false);
        var defaultDim = level == null ? null : level.dimension();
        ListTag jobs = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag e = jobs.getCompound(i);
            int idx = e.getInt("Core");
            if (idx >= 0 && idx < cores.size()) {
                cores.get(idx).job = CraftingJob.load(e.getCompound("Job"), registries, defaultDim);
            }
        }
        redstone.load(tag);
    }

    public void setVisualWorkForDev(boolean on, @Nullable String item0, @Nullable String item1) {
        this.devWorking = on;
        this.devItem0 = CraftingCpus.resolveDevItem(item0);
        this.devItem1 = CraftingCpus.resolveDevItem(item1);
        setChanged();
        syncNow();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Cores", cores.size());
        long now = level == null ? 0 : level.getGameTime();
        int showing = 0;
        for (VisualHold h : holds) {
            showing += h.active(now) ? 1 : 0;
        }
        tag.putInt("Busy", devWorking ? 2 : Math.max(busyCores(), showing));
        tag.putInt("Calcs", calculatorCount());
        tag.putInt("Workers", workersPerCore());
        tag.putBoolean("Boosted", devWorking || boosted);
        tag.putBoolean("Open", viewerTracker.isOpen());
        if (devWorking) {
            if (!devItem0.isEmpty()) {
                tag.put("Cur0", devItem0.save(registries));
            }
            if (!devItem1.isEmpty()) {
                tag.put("Cur1", devItem1.save(registries));
            }
        } else {
            int shown = 0;
            for (Core c : cores) {
                if (c.job != null && shown < 2) {
                    ItemStack cur = c.job.current().stack();
                    if (!cur.isEmpty()) {
                        tag.put("Cur" + shown, cur.save(registries));
                    }
                    shown++;
                }
            }
            for (int i = shown; i < 2; i++) {
                ItemStack held = holds[i].item(now);
                if (!held.isEmpty()) {
                    tag.put("Cur" + i, held.save(registries));
                }
            }
        }
        recordSentSummary();
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientCores = tag.getInt("Cores");
        clientBusy = tag.getInt("Busy");
        clientCalcs = tag.getInt("Calcs");
        clientWorkers = tag.getInt("Workers");
        clientBoosted = tag.getBoolean("Boosted");
        viewerTracker.setClientOpen(tag.getBoolean("Open"));
        for (int i = 0; i < 2; i++) {
            String key = "Cur" + i;
            clientCurrent[i] = tag.contains(key)
                    ? ItemStack.parse(registries, tag.getCompound(key)).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
        }
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Summary of the cabinet for Jade tooltips and checks. */
    public Map<String, Integer> stats() {
        return Map.of("cores", cores.size(), "busy", busyCores(), "workers", workersPerCore(), "calcs", calculatorCount());
    }
}
