package com.golemlogix.blockentity;

import com.golemlogix.blocks.CoolingCoprocessorBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.network.CraftDonePayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cryo-Calculator: the central processor of autocrafting. One job at a time;
 * Cooling Co-Processors right next to it speed up running steps in parallel.
 * While a job runs, a mini model of what it is making right now spins above the calculator.
 */
public class CryoCalculatorBlockEntity extends BlockEntity implements com.golemlogix.crafting.CraftingCpu {
    public static final int CYCLE_TICKS = 4;
    public static final int BASE_WORKERS = 1;
    public static final int WORKERS_PER_COPROCESSOR = 2;
    /** A Compressed Co-Processor in an upgrade slot speeds things up twice as much as a placed one: it needs no room, but compression costs Source. */
    public static final int WORKERS_PER_COMPRESSED = 4;

    @Nullable
    private CraftingJob job;
    private int coprocessors = 0;
    private boolean active = false;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.CALCULATOR, this);
    private final RedstoneControl redstone = new RedstoneControl();
    /** What was last sent to clients as "making now" (so it is only sent on change). */
    private ItemStack sentCurrent = ItemStack.EMPTY;
    private long lastCurrentSync = -100;
    /** Display for clients: even an instant order stays visible for at least VisualHold.TICKS ticks (crafting is not slowed down). */
    private final VisualHold hold = new VisualHold();

    /**
     * Whether the coprocessors speed up work right now (a cycle with several steps at once happened in the last BOOST_HOLD ticks):
     * then a snowman foreman with an atlant head appears above the calculator.
     */
    private boolean boosted = false;
    private long boostUntil = 0;
    private static final int BOOST_HOLD = 60;
    /** Once called, the foreman stays at least this long (client ticks), even if the speed-up was over in a blink. */
    public static final int FOREMAN_MIN_TICKS = 60;

    public boolean isBoosted() {
        return boosted;
    }

    // --- client
    private boolean clientHasJob = false;
    private ItemStack clientCurrent = ItemStack.EMPTY;

    public CryoCalculatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_CALCULATOR.get(), pos, state);
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
    public int getCoprocessors() {
        return coprocessors;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public int getWorkers() {
        return BASE_WORKERS + WORKERS_PER_COPROCESSOR * coprocessors
                + WORKERS_PER_COMPRESSED * upgrades.count(com.golemlogix.upgrade.UpgradeType.SPEED);
    }

    /** How many Compressed Co-Processors sit in the slots. */
    public int getCompressed() {
        return upgrades.count(com.golemlogix.upgrade.UpgradeType.SPEED);
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

    /** How many of an item the current job is waiting for from machines (0 if there is no job). */
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

    /** Cancel: everything in the job goes back to the network (the excess is dropped). */
    @Override
    public void cancelJob() {
        if (job == null || level == null || level.isClientSide()) {
            return;
        }
        GolemNetwork net = GolemNetwork.scan(level, worldPosition);
        List<ItemStack> overflow = job.refundAll(net);
        for (ItemStack s : overflow) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, s);
        }
        job = null;
        setChanged();
        syncToClient();
    }

    /** Drop the contents when the block is broken. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            if (!upgrades.getItem(i).isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, upgrades.removeItemNoUpdate(i));
            }
        }
        if (job == null) {
            return;
        }
        for (var e : job.inventory().entrySet()) {
            // fluids of the job cannot drop as items: they are spilled
            long n = com.golemlogix.system.Ingredients.isFluid(e.getKey()) ? 0 : e.getValue();
            while (n > 0) {
                int chunk = (int) Math.min(n, e.getKey().stack().getMaxStackSize());
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, e.getKey().toStack(chunk));
                n -= chunk;
            }
        }
        job = null;
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CryoCalculatorBlockEntity be) {
        long time = level.getGameTime();
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            be.redstone.observe(powered);
        }
        if ((time + pos.asLong()) % 20 == 0) {
            int n = 0;
            for (Direction dir : Direction.values()) {
                BlockPos p = pos.relative(dir);
                BlockState ns = level.getBlockState(p);
                if (ns.getBlock() instanceof CoolingCoprocessorBlock) {
                    n++;
                    ensureCoprocessorEntity(level, p, ns);
                }
            }
            be.coprocessors = n;
        }

        boolean wasActive = be.active;
        if (be.job == null) {
            be.active = false;
        } else if ((time + pos.asLong()) % CYCLE_TICKS == 0) {
            GolemNetwork net = GolemNetwork.get(level, pos);
            boolean ok = net.isOnline() && net.isDeviceActive(pos);
            boolean allowed = be.redstone.allows(hasRedstone, powered, ok);
            be.active = ok && allowed;
            if (ok && !allowed) {
                if (be.job.status() != CraftingJob.Status.PAUSED) {
                    be.job.setStatus(CraftingJob.Status.PAUSED);
                    be.syncToClient();
                }
            } else if (ok) {
                CraftingJob.Status before = be.job.status();
                long doneBefore = be.job.doneExecutions();
                be.job.tick(net, be.getWorkers());
                long done = be.job.doneExecutions() - doneBefore;
                if (done != 0) {
                    level.playSound(null, pos, ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.3f, 1.2f);
                }
                // the coprocessors really helped: more steps were done in a cycle than one calculator could do
                if (done > BASE_WORKERS) {
                    be.boostUntil = time + BOOST_HOLD;
                }
                boolean justShown = be.hold.show(be.job.current().stack(), time);
                if (be.job.isDone()) {
                    notifyFinished(level, be.job);
                    be.job = null;
                    be.active = false;
                }
                if (justShown) {
                    be.syncToClient();
                }
                be.setChanged();
                if (be.job == null || be.job.status() != before) {
                    be.syncToClient();
                } else {
                    be.syncCurrentIfChanged(time);
                }
            }
        }
        // an order the co-processors finished at once (on a big speed-up: in one cycle) still calls the foreman: it was sped up
        boolean boosted = time < be.boostUntil && (be.active || be.job == null);
        if (boosted != be.boosted) {
            be.boosted = boosted;
            be.syncToClient();
        }
        if (wasActive != be.active) {
            be.syncToClient();
            if (state.hasProperty(GolemProperties.ACTIVE) && state.getValue(GolemProperties.ACTIVE) != be.active) {
                // only the looks change: no neighbour updates (pipes and the network do not care)
                level.setBlock(pos, state.setValue(GolemProperties.ACTIVE, be.active),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            }
        }
        if (be.hold.ended(time)) {
            be.syncToClient();
        }
    }

    /** Mini model above the calculator: sent to clients only when the item changes and at most twice a second. */
    private void syncCurrentIfChanged(long time) {
        if (job == null || time - lastCurrentSync < 10) {
            return;
        }
        ItemStack cur = job.current().stack();
        if (!ItemStack.isSameItemSameComponents(cur, sentCurrent)) {
            lastCurrentSync = time;
            syncToClient();
        }
    }

    /** The order is done: whoever placed it gets a notification (as in AE2). */
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

    /**
     * Coprocessors placed before they had an animation have no block entity: create it and tell clients,
     * so the flywheel starts turning on old blocks too.
     */
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
        job = tag.contains("Job") ? CraftingJob.load(tag.getCompoundOrEmpty("Job"), registries) : null;
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        redstone.load(tag);
    }

    private boolean devWorking = false;
    private ItemStack devItem = ItemStack.EMPTY;

    public void setVisualWorkForDev(boolean on, @Nullable String item) {
        this.devWorking = on;
        if (item != null) {
            var it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(item));
            this.devItem = new ItemStack(it);
        } else {
            this.devItem = ItemStack.EMPTY;
        }
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
            tag.put("Current", com.golemlogix.system.NbtBridge.saveItem(sentCurrent, registries));
        }
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        active = tag.getBooleanOr("Active", false);
        boosted = tag.getBooleanOr("Boosted", false);
        clientHasJob = tag.getBooleanOr("HasJob", false);
        clientCurrent = tag.contains("Current") ? com.golemlogix.system.NbtBridge.loadItem(lookupProvider, tag.getCompoundOrEmpty("Current")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    
    public boolean clientHasJob() {
        return clientHasJob;
    }

    /** Client: what the calculator is making now (empty = idle). */
    public ItemStack clientCurrent() {
        return clientCurrent;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Target key for display (may be null). */
    @Nullable
    public ItemKey targetKey() {
        return job == null ? null : job.target();
    }

    // --------------------------------------------------------------- client animation

    /** Smooth spin-up of the calculator 0..1 (slides and matrix rain) and its phase. */
    public float spin = 0f;
    public float spinPrev = 0f;
    /** Foreman: 0 = none, 1 = stands on the roof (pops up and hides smoothly). */
    public float foreman = 0f;
    public float foremanPrev = 0f;
    /** Ticks the foreman still stays at least, and whether he was called last tick. */
    private int foremanLeft = 0;
    private boolean foremanCalled = false;

    // Visual timer for the minimum work duration (at least 28 ticks = 1.4 seconds)
    // Makes sure an instant craft still plays the whole assembly animation
    // and returns smoothly to rest, without sudden disappearances or teleports.
    public int visualWorkTimer = 0;
    private ItemStack visualItem = ItemStack.EMPTY;

    public ItemStack clientDisplayItem() {
        if (!clientCurrent.isEmpty()) {
            return clientCurrent;
        }
        return visualItem;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CryoCalculatorBlockEntity be) {
        be.spinPrev = be.spin;
        be.foremanPrev = be.foreman;

        boolean on = state.hasProperty(GolemProperties.ACTIVE) ? state.getValue(GolemProperties.ACTIVE) : be.active;
        if (on || !be.clientCurrent.isEmpty()) {
            be.visualWorkTimer = Math.max(be.visualWorkTimer, 32);
            if (!be.clientCurrent.isEmpty()) {
                be.visualItem = be.clientCurrent.copy();
            }
        } else if (be.visualWorkTimer > 0) {
            be.visualWorkTimer--;
        }
        be.foremanLeft = be.boosted && !be.foremanCalled ? FOREMAN_MIN_TICKS : Math.max(0, be.foremanLeft - 1);
        be.foremanCalled = be.boosted;
        // the crew keeps working while the foreman urges it on
        be.visualWorkTimer = Math.max(be.visualWorkTimer, be.foremanLeft);

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
                be.visualItem = ItemStack.EMPTY;
            }
        }

        boolean foremanWanted = (be.boosted || be.foremanLeft > 0) && be.spin > 0.4f;
        be.foreman = Math.max(0f, Math.min(1f, be.foreman + (foremanWanted ? 0.08f : -0.05f)));
    }

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, com.golemlogix.system.NbtBridge.registries(this));
        com.golemlogix.system.NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) {
        readUpdateTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput input) {
        handleUpdateTag(input);
    }
}
