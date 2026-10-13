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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cryo-Mainframe: a cabinet of Cryo-Calculators. Every calculator inside gives several independent cores (autocraft
 * processors, one job per core), every Compressed Co-Processor counts as several and speeds up all cores at once. In the
 * network the whole cabinet is one device on one channel. The cabinet doors open while someone has its window open.
 */
public class CryoMainframeBlockEntity extends BlockEntity {
    public static final int CALC_SLOTS = 12;
    /** Block event: how many players look into the window (clients open and close the doors). */
    public static final int EVENT_VIEWERS = 1;
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
    private final List<Core> cores = new ArrayList<>();
    private int viewers = 0;
    private boolean active = false;
    private boolean boosted = false;
    private long boostUntil = 0;
    /** What was last sent to clients (so it is only sent on change). */
    private String sentSummary = "";
    private long lastSync = -100;
    /** Displays of the two shelves: even an instant core order stays visible for at least VisualHold.TICKS ticks (crafting is not slowed down). */
    private final VisualHold[] holds = {new VisualHold(), new VisualHold()};

    // --- client
    private int clientCores, clientBusy, clientCalcs, clientWorkers;
    private boolean clientBoosted, clientOpen;
    private final ItemStack[] clientCurrent = {ItemStack.EMPTY, ItemStack.EMPTY};
    /** Door opening 0..1, work spin-up 0..1 and foremen (as on the calculator). */
    public float doors, doorsPrev, spin, spinPrev, foreman, foremanPrev;
    /** Smoothed poses of the three copper golems (shelves 0 and 1, roof): x, y, z, yaw, last frame time, "already set". */
    public final float[][] golemSmooth = new float[3][6];

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

    /** Steps per cycle for each core: as on the calculator, but every coprocessor counts as several. */
    public int workersPerCore() {
        return CryoCalculatorBlockEntity.BASE_WORKERS
                + CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED * upgrades.count(UpgradeType.SPEED) * coprocessorMultiplier();
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

    /** The number of cores follows the number of calculators; jobs of extra cores are cancelled (contents go back to the network). */
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
        // the remembered networks list the cores they found: they are stale now
        if (changed && level != null && !level.isClientSide()) {
            GolemNetwork.clearCache();
        }
    }

    // ------------------------------------------------------------------------------------------------ core
    /** A core is an independent processor; all cores share one block and one channel. */
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
            GolemNetwork net = GolemNetwork.scan(level, worldPosition);
            for (ItemStack s : job.refundAll(net)) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, s);
            }
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
        public boolean isRemoved() {
            return CryoMainframeBlockEntity.this.isRemoved() || !cores.contains(this);
        }

        @Override
        public BlockPos cpuPos() {
            return worldPosition;
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
            return formed() && net.isDeviceActive(worldPosition);
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

    /** How many players are looking into the window now (server). */
    public int viewers() {
        return viewers;
    }

    /** A player opened the window: the doors swing open. */
    public void startOpen() {
        viewers++;
        if (viewers == 1 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.7f, 1.3f);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
        signalViewers();
    }

    public void stopOpen() {
        viewers = Math.max(0, viewers - 1);
        if (viewers == 0 && level != null) {
            level.playSound(null, worldPosition, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.3f);
        }
        signalViewers();
    }

    private void signalViewers() {
        if (level != null && !level.isClientSide()) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_VIEWERS, viewers);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_VIEWERS) {
            clientOpen = param > 0;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Drop the calculators, coprocessors and job contents (when the owner is broken). */
    public void dropContents() {
        if (level == null) {
            return;
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (Core c : cores) {
            if (c.job == null) {
                continue;
            }
            for (var e : c.job.inventory().entrySet()) {
                // fluids of the job cannot drop as items: they are spilled
                long n = com.golemlogix.system.Ingredients.isFluid(e.getKey()) ? 0 : e.getValue();
                while (n > 0) {
                    int chunk = (int) Math.min(n, e.getKey().stack().getMaxStackSize());
                    Containers.dropItemStack(level, x, y, z, e.getKey().toStack(chunk));
                    n -= chunk;
                }
            }
            c.job = null;
        }
        for (int i = 0; i < calculators.getContainerSize(); i++) {
            if (!calculators.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, x, y, z, calculators.removeItemNoUpdate(i));
            }
        }
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, x, y, z, upgrades.removeItemNoUpdate(i));
            }
        }
        cores.clear();
    }

    // ------------------------------------------------------------------------------------------------ work
    public static void serverTick(Level level, BlockPos pos, BlockState state, CryoMainframeBlockEntity be) {
        long time = level.getGameTime();
        Direction facing = state.getValue(CryoMainframeBlock.FACING);
        if ((time + pos.asLong()) % 20 == 0) {
            boolean valid = MainframeStructure.valid(level, pos, facing);
            if (be.formed() && !valid) {
                MainframeStructure.disassemble(level, pos, facing, null);
            } else if (!be.formed() && valid) {
                MainframeStructure.form(level, pos, facing);
            }
            state = level.getBlockState(pos);
        }
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            be.redstone.observe(powered);
        }
        boolean wasActive = be.active;
        if ((time + pos.asLong()) % CryoCalculatorBlockEntity.CYCLE_TICKS == 0) {
            GolemNetwork net = GolemNetwork.get(level, pos);
            boolean ok = be.formed() && net.isOnline() && net.isDeviceActive(pos);
            boolean allowed = be.redstone.allows(hasRedstone, powered, ok);
            boolean anyJob = false, clicked = false, changed = false;
            int workers = be.workersPerCore();
            int shelf = 0;
            for (Core core : be.cores) {
                CraftingJob job = core.job;
                if (job == null) {
                    continue;
                }
                anyJob = true;
                // the first two busy cores are shown on the shelves: the display keeps their item even after the order ends
                int myShelf = shelf < be.holds.length ? shelf++ : -1;
                if (myShelf >= 0) {
                    changed |= be.holds[myShelf].show(job.current().stack(), time);
                }
                if (!ok) {
                    continue;
                }
                if (!allowed) {
                    if (job.status() != CraftingJob.Status.PAUSED) {
                        job.setStatus(CraftingJob.Status.PAUSED);
                        changed = true;
                    }
                    continue;
                }
                CraftingJob.Status before = job.status();
                long doneBefore = job.doneExecutions();
                job.tick(net, workers);
                if (myShelf >= 0) {
                    be.holds[myShelf].show(job.current().stack(), time);
                }
                long done = job.doneExecutions() - doneBefore;
                clicked |= done > 0;
                if (done > CryoCalculatorBlockEntity.BASE_WORKERS) {
                    be.boostUntil = time + BOOST_HOLD;
                }
                if (job.isDone()) {
                    CryoCalculatorBlockEntity.notifyFinished(level, job);
                    core.job = null;
                    changed = true;
                } else if (job.status() != before) {
                    changed = true;
                }
            }
            if (clicked) {
                level.playSound(null, pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.3f, 1.0f);
            }
            if (anyJob) {
                be.setChanged();
            }
            be.active = ok && allowed && be.busyCores() > 0;
            if (changed) {
                be.syncNow();
            } else {
                be.syncIfChanged(time);
            }
        }
        boolean boosted = be.active && time < be.boostUntil;
        if (boosted != be.boosted || wasActive != be.active) {
            be.boosted = boosted;
            be.syncNow();
        }
        boolean ended = false;
        for (VisualHold h : be.holds) {
            ended |= h.ended(time);
        }
        if (ended) {
            be.syncNow();
        }
    }

    private String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(cores.size()).append('/').append(busyCores()).append('/').append(active).append('/').append(boosted)
                .append('/').append(calculatorCount()).append('/').append(workersPerCore());
        int shown = 0;
        for (Core c : cores) {
            if (c.job != null && shown < 2) {
                sb.append('/').append(c.job.current().stack().getItem());
                shown++;
            }
        }
        return sb.toString();
    }

    private void syncIfChanged(long time) {
        if (time - lastSync < 10) {
            return;
        }
        if (!summary().equals(sentSummary)) {
            syncNow();
        }
    }

    private void syncNow() {
        if (level != null && !level.isClientSide()) {
            lastSync = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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

    /** What the first two busy cores are making (projections on the cabinet shelves). */
    public ItemStack clientCurrent(int shelf) {
        return clientCurrent[shelf];
    }

    // Visual timer for the minimum work duration (at least 32 ticks = 1.6 seconds)
    // Makes sure an instant craft still plays the whole assembly animation
    // and returns smoothly to rest, without sudden disappearances or teleports.
    public int visualWorkTimer = 0;
    private final ItemStack[] visualItems = {ItemStack.EMPTY, ItemStack.EMPTY};

    public ItemStack clientDisplayItem(int shelf) {
        if (!clientCurrent[shelf].isEmpty()) {
            return clientCurrent[shelf];
        }
        return visualItems[shelf];
    }

    /** Heads of the built-in atlants (left, right): {yaw, pitch}, current and previous tick. */
    public final float[][] heads = {{0f, 0f}, {0f, 0f}}, headsPrev = {{0f, 0f}, {0f, 0f}};

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
        be.spinPrev = be.spin;
        be.foremanPrev = be.foreman;
        be.doors = Math.max(0f, Math.min(1f, be.doors + (be.clientOpen ? 0.09f : -0.07f)));

        boolean on = be.clientBusy > 0 || !be.clientCurrent[0].isEmpty() || !be.clientCurrent[1].isEmpty();
        if (on) {
            be.visualWorkTimer = Math.max(be.visualWorkTimer, 32);
            for (int i = 0; i < 2; i++) {
                if (!be.clientCurrent[i].isEmpty()) {
                    be.visualItems[i] = be.clientCurrent[i].copy();
                }
            }
        } else if (be.visualWorkTimer > 0) {
            be.visualWorkTimer--;
        }

        boolean activeVisually = on || be.visualWorkTimer > 0;
        if (activeVisually) {
            // Energetic spin-up to the maximum
            be.spin += (1f - be.spin) * 0.14f;
            if (be.spin > 0.98f) {
                be.spin = 1f;
            }
        } else {
            // Smooth return to idle
            be.spin += (0f - be.spin) * 0.05f;
            if (be.spin < 0.005f) {
                be.spin = 0f;
                be.visualItems[0] = ItemStack.EMPTY;
                be.visualItems[1] = ItemStack.EMPTY;
            }
        }
        be.foreman = Math.max(0f, Math.min(1f, be.foreman + (be.clientBoosted && be.spin > 0.4f ? 0.08f : -0.05f)));
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cores.clear();
        calculators.fromTag(tag.getList("Calculators", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        syncCores(false);
        ListTag jobs = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag e = jobs.getCompound(i);
            int idx = e.getInt("Core");
            if (idx >= 0 && idx < cores.size()) {
                cores.get(idx).job = CraftingJob.load(e.getCompound("Job"), registries);
            }
        }
        redstone.load(tag);
    }

    private boolean devWorking = false;
    private ItemStack devItem0 = ItemStack.EMPTY, devItem1 = ItemStack.EMPTY;

    public void setVisualWorkForDev(boolean on, @Nullable String item0, @Nullable String item1) {
        this.devWorking = on;
        if (item0 != null) {
            var i0 = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item0));
            this.devItem0 = new ItemStack(i0);
        } else {
            this.devItem0 = ItemStack.EMPTY;
        }
        if (item1 != null) {
            var i1 = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(item1));
            this.devItem1 = new ItemStack(i1);
        } else {
            this.devItem1 = ItemStack.EMPTY;
        }
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
        tag.putBoolean("Open", viewers > 0);
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
            // shelves without a busy core show the item from the display until it fades
            for (int i = shown; i < 2; i++) {
                ItemStack held = holds[i].item(now);
                if (!held.isEmpty()) {
                    tag.put("Cur" + i, held.save(registries));
                }
            }
        }
        sentSummary = summary();
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientCores = tag.getInt("Cores");
        clientBusy = tag.getInt("Busy");
        clientCalcs = tag.getInt("Calcs");
        clientWorkers = tag.getInt("Workers");
        clientBoosted = tag.getBoolean("Boosted");
        clientOpen = tag.getBoolean("Open");
        for (int i = 0; i < 2; i++) {
            clientCurrent[i] = tag.contains("Cur" + i) ? ItemStack.parse(registries, tag.getCompound("Cur" + i)).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
        }
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
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
