package com.golemlogix.blockentity;

import com.golemlogix.blocks.CoolingCoprocessorBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.network.CraftDonePayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cryo-Calculator: single-job autocrafting CPU. Adjacent Cooling Co-Processors and installed Compressed Co-Processors
 * execute multiple steps per cycle; idle calculators lend spare steps to busy jobs in the same network.
 */
public class CryoCalculatorBlockEntity extends BlockEntity implements CraftingCpu {
    public static final int CYCLE_TICKS = 4;
    public static final int BASE_WORKERS = 1;
    public static final int WORKERS_PER_COPROCESSOR = 2;
    public static final int WORKERS_PER_COMPRESSED = 4;
    /** Minimum ticks the foreman stays visible on clients after a boosted cycle. */
    public static final int FOREMAN_MIN_TICKS = 60;

    private static final int HELP_HOLD = 20;
    private static final int BOOST_HOLD = 60;

    @Nullable
    private CraftingJob job;
    private int coprocessors = 0;
    private boolean active = false;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.CALCULATOR, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private ItemStack sentCurrent = ItemStack.EMPTY;
    private long lastCurrentSync = -100;
    private final VisualHold hold = new VisualHold();
    @Nullable
    private CraftingJob helped;
    private long helpUntil;
    private boolean boosted = false;
    private long boostUntil = 0;

    // --- client
    private boolean clientHasJob = false;
    private ItemStack clientCurrent = ItemStack.EMPTY;
    private ItemStack visualItem = ItemStack.EMPTY;
    private final CraftingCpus.Animation animation = new CraftingCpus.Animation();
    /** Smooth spin-up 0..1, foreman visibility 0..1, and visual work hold timer (read by renderer / coprocessors). */
    public float spin = 0f, spinPrev = 0f;
    public float foreman = 0f, foremanPrev = 0f;
    public int visualWorkTimer = 0;

    private boolean devWorking = false;
    private ItemStack devItem = ItemStack.EMPTY;

    public CryoCalculatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_CALCULATOR.get(), pos, state);
    }

    public boolean isBoosted() {
        return boosted;
    }

    @Override
    public boolean isIdle() {
        return job == null;
    }

    @Override
    @Nullable
    public CraftingJob getJob() {
        return job;
    }

    @Override
    public BlockPos cpuPos() {
        return worldPosition;
    }

    @Override
    @Nullable
    public Level cpuLevel() {
        return getLevel();
    }

    @Override
    public int getCoprocessors() {
        return coprocessors;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public int getWorkers() {
        return BASE_WORKERS + WORKERS_PER_COPROCESSOR * coprocessors
                + WORKERS_PER_COMPRESSED * upgrades.count(UpgradeType.SPEED);
    }

    public int getCompressed() {
        return upgrades.count(UpgradeType.SPEED);
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    /** Window data: 0 = redstone mode. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return redstone.mode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                redstone.setMode(RedstoneMode.byId(value));
                setChanged();
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    @Override
    public void startJob(CraftingJob newJob) {
        this.job = newJob;
        if (level != null) {
            hold.show(newJob.current().stack(), level.getGameTime());
        }
        setChanged();
        syncToClient();
    }

    @Override
    public long wants(ItemKey key) {
        return job == null ? 0L : job.wants(key);
    }

    @Override
    public ItemStack absorb(ItemStack stack, @Nullable net.minecraft.core.GlobalPos provider) {
        if (job == null) {
            return stack;
        }
        ItemStack rest = job.absorb(stack, provider);
        if (rest.getCount() != stack.getCount()) {
            setChanged();
        }
        return rest;
    }

    @Override
    public int absorbFluid(FluidStack fluid, @Nullable net.minecraft.core.GlobalPos provider) {
        int n = job == null ? 0 : job.absorbFluid(fluid, provider);
        if (n > 0) {
            setChanged();
        }
        return n;
    }

    @Override
    public void cancelJob() {
        if (job == null || level == null || level.isClientSide()) {
            return;
        }
        CraftingCpus.refundAndDrop(level, worldPosition, job, GolemNetwork.get(level, worldPosition), 1.0);
        job = null;
        setChanged();
        syncToClient();
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, x, y, z, upgrades.removeItemNoUpdate(i));
            }
        }
        if (job == null) {
            return;
        }
        CraftingCpus.refundAndDrop(level, worldPosition, job, GolemNetwork.aroundRemoved(level, worldPosition), 0.5);
        job = null;
    }

    private void syncToClient() {
        BlockEntities.sendUpdate(this);
    }

    /** Whether a finished job rings the typewriter bell (large job or player-ordered, unless silenced in config). */
    public static boolean dings(CraftingJob job) {
        return GolemConfig.TYPEWRITER_DING.get() && (job.doneExecutions() >= 16 || job.requester() != null);
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, CryoCalculatorBlockEntity be) {
        long time = level.getGameTime();
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, be.redstone);
        if ((time + pos.asLong()) % 20 == 0) {
            be.refreshCoprocessors(level, pos);
        }

        boolean wasActive = be.active;
        boolean needSync = false;
        if (be.job == null) {
            if ((time + pos.asLong()) % CYCLE_TICKS == 0 && !hasRedstone) {
                needSync = be.lendSteps(level, pos, time);
            }
            be.active = time < be.helpUntil;
        } else if ((time + pos.asLong()) % CYCLE_TICKS == 0) {
            needSync = be.tickOwnJob(level, pos, hasRedstone, powered, time);
        }

        boolean boostedNow = time < be.boostUntil && (be.active || be.job == null);
        if (boostedNow != be.boosted) {
            be.boosted = boostedNow;
            needSync = true;
        }
        if (wasActive != be.active) {
            needSync = true;
            syncActiveBlockState(level, pos, state, be.active);
        }
        if (be.hold.ended(time)) {
            needSync = true;
        }
        if (needSync) {
            be.syncToClient();
        }
    }

    private void refreshCoprocessors(Level level, BlockPos pos) {
        int n = 0;
        for (Direction dir : BlockEntities.DIRECTIONS) {
            BlockPos p = pos.relative(dir);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState ns = level.getBlockState(p);
            if (ns.getBlock() instanceof CoolingCoprocessorBlock) {
                n++;
                ensureCoprocessorEntity(level, p, ns);
            }
        }
        coprocessors = n;
    }

    private boolean tickOwnJob(Level level, BlockPos pos, boolean hasRedstone, boolean powered, long time) {
        GolemNetwork net = GolemNetwork.get(level, pos);
        boolean ok = net.isOnline() && net.isDeviceActive(pos);
        boolean allowed = redstone.allows(hasRedstone, powered, ok);
        active = ok && allowed;
        if (!ok) {
            return false;
        }
        if (!allowed) {
            if (job.status() != CraftingJob.Status.PAUSED) {
                job.setStatus(CraftingJob.Status.PAUSED);
                return true;
            }
            return false;
        }
        CraftingJob.Status before = job.status();
        long doneBefore = job.doneExecutions();
        job.tick(net, getWorkers());
        long done = job.doneExecutions() - doneBefore;
        if (done != 0) {
            level.playSound(null, pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.3f, 1.2f);
        }
        if (done > BASE_WORKERS) {
            boostUntil = time + BOOST_HOLD;
        }
        boolean needSync = hold.show(job.current().stack(), time);
        if (job.isDone()) {
            if (dings(job)) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.7f, 1.8f);
            }
            notifyFinished(level, job);
            job = null;
            active = false;
        }
        setChanged();
        return needSync || job == null || job.status() != before || currentChanged(time);
    }

    private static void syncActiveBlockState(Level level, BlockPos pos, BlockState state, boolean active) {
        if (state.hasProperty(GolemProperties.ACTIVE) && state.getValue(GolemProperties.ACTIVE) != active) {
            // visual property only: skip neighbour updates so pipes and network do not re-evaluate
            level.setBlock(pos, state.setValue(GolemProperties.ACTIVE, active),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Idle calculators without a Redstone Module lend steps to busy jobs while staying free for new orders. */
    private boolean lendSteps(Level level, BlockPos pos, long time) {
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            return false;
        }
        CraftingJob target = net.jobWantingHelp();
        if (target == null) {
            return false;
        }
        long before = target.doneExecutions();
        target.tick(net, getWorkers());
        long done = target.doneExecutions() - before;
        if (done <= 0) {
            return false;
        }
        helped = target;
        helpUntil = time + HELP_HOLD;
        level.playSound(null, pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.3f, 1.2f);
        if (done > BASE_WORKERS) {
            boostUntil = time + BOOST_HOLD;
        }
        return hold.show(target.current().stack(), time);
    }

    @Nullable
    public CraftingJob helpedJob() {
        return level != null && level.getGameTime() < helpUntil && helped != null && !helped.isDone() ? helped : null;
    }

    @Override
    public boolean acceptsHelp() {
        return !upgrades.has(UpgradeType.REDSTONE);
    }

    private boolean currentChanged(long time) {
        if (job == null || time - lastCurrentSync < 10) {
            return false;
        }
        if (!ItemStack.isSameItemSameComponents(job.current().stack(), sentCurrent)) {
            lastCurrentSync = time;
            return true;
        }
        return false;
    }

    /** Sends a completion toast packet to the player who ordered {@code done}. */
    public static void notifyFinished(Level level, CraftingJob done) {
        if (done.requester() == null || !(level instanceof ServerLevel sl)) {
            return;
        }
        ServerPlayer player = sl.getServer().getPlayerList().getPlayer(done.requester());
        if (player == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : done.targets().entrySet()) {
            items.add(e.getKey().toStack((int) Math.min(e.getValue(), Integer.MAX_VALUE)));
            if (items.size() >= 8) {
                break;
            }
        }
        PacketDistributor.sendToPlayer(player, new CraftDonePayload(items));
    }

    /** Lazily instantiates block entities for legacy Cooling Co-Processors so their flywheels animate. */
    private static void ensureCoprocessorEntity(Level level, BlockPos p, BlockState state) {
        LevelChunk chunk = level.getChunkAt(p);
        if (chunk.getBlockEntity(p, LevelChunk.EntityCreationType.CHECK) == null) {
            level.getBlockEntity(p);
            level.sendBlockUpdated(p, state, state, 3);
        }
    }

    // --------------------------------------------------------------- persistence

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (job != null) {
            tag.put("Job", job.save(registries));
        }
        tag.put("Upgrades", upgrades.createTag(registries));
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        job = tag.contains("Job")
                ? CraftingJob.load(tag.getCompoundOrEmpty("Job"), registries, level != null ? level.dimension() : null)
                : null;
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        redstone.load(tag);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (job != null && level != null) {
            job.resolveLegacyDimension(level.dimension());
        }
    }

    public void setVisualWorkForDev(boolean on, @Nullable String item) {
        this.devWorking = on;
        this.devItem = CraftingCpus.resolveDevItem(item);
        setChanged();
        syncToClient();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        long now = level == null ? 0 : level.getGameTime();
        boolean showing = hold.active(now);
        tag.putBoolean("Active", devWorking || active || showing);
        tag.putBoolean("Boosted", devWorking || boosted);
        tag.putBoolean("HasJob", devWorking || job != null);
        sentCurrent = devWorking && !devItem.isEmpty() ? devItem : (job != null ? job.current().stack() : hold.item(now));
        if (!sentCurrent.isEmpty()) {
            tag.put("Current", NbtBridge.saveItem(sentCurrent, registries));
        }
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        active = tag.getBooleanOr("Active", false);
        boosted = tag.getBooleanOr("Boosted", false);
        clientHasJob = tag.getBooleanOr("HasJob", false);
        clientCurrent = tag.contains("Current")
                ? NbtBridge.loadItem(lookupProvider, tag.getCompoundOrEmpty("Current")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    public boolean clientHasJob() {
        return clientHasJob;
    }

    public ItemStack clientCurrent() {
        return clientCurrent;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    public ItemKey targetKey() {
        return job == null ? null : job.target();
    }

    // --------------------------------------------------------------- client animation

    public ItemStack clientDisplayItem() {
        return !clientCurrent.isEmpty() ? clientCurrent : visualItem;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CryoCalculatorBlockEntity be) {
        boolean on = state.hasProperty(GolemProperties.ACTIVE) ? state.getValue(GolemProperties.ACTIVE) : be.active;
        boolean activeNow = on || !be.clientCurrent.isEmpty();
        if (activeNow && !be.clientCurrent.isEmpty()) {
            be.visualItem = be.clientCurrent;
        }
        be.animation.visualWorkTimer = be.visualWorkTimer;
        if (be.animation.tick(activeNow, be.boosted)) {
            be.visualItem = ItemStack.EMPTY;
        }
        be.spin = be.animation.spin;
        be.spinPrev = be.animation.spinPrev;
        be.foreman = be.animation.foreman;
        be.foremanPrev = be.animation.foremanPrev;
        be.visualWorkTimer = be.animation.visualWorkTimer;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BlockEntities.save(this, output, this::saveTag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BlockEntities.load(input, this::loadTag);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        BlockEntities.load(input, this::readUpdateTag);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
