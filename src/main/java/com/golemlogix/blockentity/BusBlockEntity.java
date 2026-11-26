package com.golemlogix.blockentity;

import com.golemlogix.blocks.InjectorExportBusBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.menu.Priority;
import com.golemlogix.system.BusLoad;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.TargetCaps;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.BusSpeed;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared block entity of both world buses:
 * <ul>
 * <li>Vacuum Import Bus: pulls items and fluids from the container in front into the network (allow/deny filter).</li>
 * <li>Injector Export Bus: pushes filtered items and fluids from the network into the container in front (in order,
 * round-robin, or random; optional per-slot keep-amount cap and autocrafting).</li>
 * </ul>
 * Adaptive pacing slows idle or trickle buses to save server time while granting throughput credit for elapsed ticks.
 */
public class BusBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    /** How often a bus with an Autocraft Module may order a craft (ticks). */
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    /** Work sound cooldown (ticks). */
    private static final int SOUND_COOLDOWN = 10;
    private static final int MAX_IDLE_TICKS = 200, MAX_IDLE_CRAFT_TICKS = 40, MAX_IDLE_STEPS = 8;
    private static final int MAX_LIGHT_TICKS = 20, CREDIT_TICKS = 20;
    /** An operation moving less than this (items; 62 mB count as 1 item) is light; twice this is heavy. */
    private static final int LIGHT_LOAD = 16;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private final UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();
    /** Reusable slot traversal buffer and cached per-slot filter fluids (refreshed only on filter edits). */
    private final int[] slotOrder = new int[FILTER_SLOTS];
    private final FluidStack[] filterFluids = new FluidStack[FILTER_SLOTS];
    private boolean filterCacheDirty = true;
    private boolean hasItemFilter, hasFluidFilter;
    private boolean upgradesDirty = true;
    private boolean hasRedstone;

    private int moved = 0;
    /** Export: filter slot traversal mode and round-robin cursor. */
    private int order = FormationBusBlockEntity.IN_ORDER;
    private int next;
    /** Import: treat the filter as a deny list instead of an allow list. */
    private boolean denyList;
    /** Higher-priority buses in the same network act earlier in the cycle. */
    private int priority = 0;
    private long lastSound = -1000;
    private long lastCraftRequest = -1000;

    private int idleSteps;
    private int period;
    private long nextRun;
    private long lastRun;
    private int deferrals;
    private boolean sleeping;
    private boolean sleepWaitStorage;
    private long sleepEpoch;
    private long sleepNetworkEpoch;
    private int fluidMoved;
    /** Throughput budget for the current operation. */
    private int opItems, opFluid;
    /** Operations executed so far (inspected by GameTests). */
    public int operations;
    /** Cached target capabilities invalidated automatically when the block in front changes. */
    @Nullable
    private TargetCaps caps;

    public BusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUS.get(), pos, state);
        Arrays.fill(filterFluids, FluidStack.EMPTY);
        this.upgrades = new UpgradeContainer(
                state.getBlock() instanceof InjectorExportBusBlock ? UpgradeSpec.EXPORT_BUS : UpgradeSpec.IMPORT_BUS, this);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
        // the network remembers bus priority order
        if (level != null && !level.isClientSide()) {
            GolemNetwork.clearCache();
        }
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    /** True when the last operation moved nothing and the bus is sleeping between runs. */
    public boolean idle() {
        return idleSteps > 0;
    }

    /** Ends idle sleep so the bus runs on its next turn. */
    public void wake() {
        idleSteps = 0;
        period = 0;
        nextRun = 0;
        sleeping = false;
        sleepWaitStorage = false;
    }

    /** Neighbour changed: wake if sleeping, keep current pace if already working. */
    public void nudge() {
        if (sleeping) {
            wake();
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        filterCacheDirty = true;
        upgradesDirty = true;
        wake();
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    /** Window data: 0 = priority, 1 = redstone mode, 2 = order (Export Bus) or deny list (Import Bus). */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? priority : index == 1 ? redstone.mode().ordinal() : isExport() ? order : denyList ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    setPriority(value);
                } else if (index == 1) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                } else if (isExport()) {
                    order = Math.floorMod(value, 3);
                    next = 0;
                    setChanged();
                } else {
                    denyList = value != 0;
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public OwnedContainer getFilters() {
        return filters;
    }

    public boolean isExport() {
        return getBlockState().getBlock() instanceof InjectorExportBusBlock;
    }

    public int interval() {
        return BusSpeed.interval(upgrades.count(UpgradeType.SPEED));
    }

    public int itemsPerOperation() {
        return BusSpeed.items(upgrades.count(UpgradeType.SPEED));
    }

    public int fluidPerOperation() {
        return BusSpeed.fluid(upgrades.count(UpgradeType.SPEED));
    }

    private boolean fuzzy() {
        return upgrades.has(UpgradeType.FUZZY);
    }

    private boolean hasRedstone() {
        if (upgradesDirty) {
            hasRedstone = upgrades.has(UpgradeType.REDSTONE);
            upgradesDirty = false;
        }
        return hasRedstone;
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, BusBlockEntity be) {
        boolean hasRedstone = be.hasRedstone();
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, be.redstone);
        long now = level.getGameTime();
        if (now < be.nextRun && !be.wokeFromStorageChange(level, pos)) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        int interval = be.interval();
        if (be.period < interval) {
            be.period = interval;
        }
        // at full speed buses take turns by priority; a deferred bus runs immediately
        if (be.period == interval && be.deferrals == 0) {
            int phase = Math.floorMod(now - net.busRank(be), interval);
            if (phase != 0) {
                be.nextRun = now + interval - phase;
                return;
            }
        }
        be.nextRun = now + be.period;
        if (!net.isOnline() || !net.isDeviceActive(pos) || !be.redstone.allows(hasRedstone, powered, false)) {
            return;
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);
        BlockPos target = pos.relative(facing);
        if (!level.isLoaded(target)) {
            return;
        }
        be.caps = TargetCaps.of(be.caps, (ServerLevel) level, target, facing.getOpposite(), () -> !be.isRemoved(), be::wake);
        ResourceHandler<ItemResource> items = be.caps.items();
        ResourceHandler<FluidResource> fluids = be.caps.fluids();
        if (items == null && fluids == null) {
            return;
        }
        if (be.deferrals < BusLoad.MAX_DEFERRALS && BusLoad.overBudget(level)) {
            be.deferrals++;
            be.nextRun = now + 1;
            BusLoad.deferred();
            return;
        }
        be.runOperation(level, pos, net, target, items, fluids, interval, hasRedstone, powered, now);
    }

    private boolean wokeFromStorageChange(Level level, BlockPos pos) {
        if (!sleeping || (!isExport() && !sleepWaitStorage) || sleepEpoch == GolemNetwork.storageEpoch()) {
            return false;
        }
        // something changed in some network: wake only if it was ours
        sleepEpoch = GolemNetwork.storageEpoch();
        if (GolemNetwork.get(level, pos).networkEpoch() == sleepNetworkEpoch) {
            return false;
        }
        wake();
        return true;
    }

    private void runOperation(Level level, BlockPos pos, GolemNetwork net, BlockPos target,
                              @Nullable ResourceHandler<ItemResource> items, @Nullable ResourceHandler<FluidResource> fluids,
                              int interval, boolean hasRedstone, boolean powered, long now) {
        deferrals = 0;
        sleepWaitStorage = false;
        long ticks = Math.max(interval, Math.min(Math.max(interval, CREDIT_TICKS), now - lastRun));
        opItems = (int) (itemsPerOperation() * ticks / interval);
        opFluid = (int) (fluidPerOperation() * ticks / interval);
        moved = 0;
        fluidMoved = 0;
        operations++;

        long started = System.nanoTime();
        if (isExport()) {
            doExport(level, net, target, items, fluids);
        } else {
            doImport(level, pos, net, target, items, fluids);
        }
        BusLoad.ran(level, System.nanoTime() - started);
        lastRun = now;
        updatePace(level, pos, net, interval, hasRedstone, powered, now);
    }

    private void updatePace(Level level, BlockPos pos, GolemNetwork net, int interval,
                            boolean hasRedstone, boolean powered, long now) {
        if (moved == 0) {
            idleSteps = Math.min(MAX_IDLE_STEPS, idleSteps + 1);
            period = interval;
            int maxIdle = isExport() && upgrades.has(UpgradeType.CRAFTING) ? MAX_IDLE_CRAFT_TICKS : MAX_IDLE_TICKS;
            nextRun = now + Math.max(interval, Math.min(maxIdle, interval << idleSteps));
            sleeping = true;
            sleepEpoch = GolemNetwork.storageEpoch();
            sleepNetworkEpoch = net.networkEpoch();
            return;
        }
        idleSteps = 0;
        sleeping = false;
        sleepWaitStorage = false;
        int load = moved - fluidMoved + fluidMoved / 62;
        if (load < LIGHT_LOAD) {
            period = Math.max(interval, Math.min(MAX_LIGHT_TICKS, period * 2));
        } else if (load >= LIGHT_LOAD * 2) {
            period = Math.max(interval, period / 2);
        }
        nextRun = now + period;
        redstone.allows(hasRedstone, powered, true);
        if (now - lastSound >= SOUND_COOLDOWN) {
            lastSound = now;
            level.playSound(null, pos, isExport() ? ModSounds.PNEUMATIC_INJECT.get() : ModSounds.VACUUM_SUCTION.get(),
                    SoundSource.BLOCKS, 0.25f, 1.0f);
            if (level.getRandom().nextInt(3) == 0) {
                level.playSound(null, pos, ModSounds.TUBE_SCURRY.get(), SoundSource.BLOCKS, 0.2f, 1.0f);
            }
        }
    }

    /** Refreshes cached filter fluids and item/fluid presence flags when filters have been modified. */
    private void ensureFilterCache() {
        if (!filterCacheDirty) {
            return;
        }
        boolean items = false, fluids = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            FluidStack fluid = BlockEntities.ghostFluid(f);
            filterFluids[i] = fluid;
            if (!f.isEmpty()) {
                if (fluid.isEmpty()) {
                    items = true;
                } else {
                    fluids = true;
                }
            }
        }
        hasItemFilter = items;
        hasFluidFilter = fluids;
        filterCacheDirty = false;
    }

    // ---------------------------------------------------------------- import

    private void doImport(Level level, BlockPos pos, GolemNetwork net, BlockPos target,
                          @Nullable ResourceHandler<ItemResource> items, @Nullable ResourceHandler<FluidResource> fluids) {
        ensureFilterCache();
        boolean noFilters = !hasItemFilter && !hasFluidFilter;
        if (items != null && (denyList || noFilters || hasItemFilter)) {
            importItems(level, pos, net, target, items, noFilters);
        }
        if (fluids != null && (denyList || noFilters || hasFluidFilter)) {
            importFluids(net, target, fluids, noFilters);
        }
    }

    private void importItems(Level level, BlockPos pos, GolemNetwork net, BlockPos target,
                             ResourceHandler<ItemResource> items, boolean noFilters) {
        boolean fuzzy = fuzzy();
        int budget = opItems;
        for (int i = 0; i < items.size() && budget > 0; i++) {
            while (budget > 0) {
                ItemResource r = items.getResource(i);
                int amt = items.getAmountAsInt(i);
                if (r.isEmpty() || amt <= 0) {
                    break;
                }
                int takeMax = Math.min(Math.min(budget, amt), Math.max(1, r.getMaxStackSize()));
                ItemStack inSlot = r.toStack(takeMax);
                if (!passesItemImport(inSlot, noFilters, fuzzy)) {
                    break;
                }
                int simGot;
                try (Transaction simTx = Transaction.openRoot()) {
                    simGot = items.extract(i, r, inSlot.getCount(), simTx);
                }
                if (simGot <= 0) {
                    break;
                }
                ItemStack sim = r.toStack(simGot);
                // autocraft jobs waiting for this item accept it even when storage is full
                int wanted = Math.min(sim.getCount(), net.machineWants(sim));
                ItemStack restSim = net.insertItem(sim.copyWithCount(sim.getCount() - wanted), true, target);
                int accepted = wanted + (sim.getCount() - wanted - restSim.getCount());
                if (accepted <= 0) {
                    sleepWaitStorage = true;
                    break;
                }
                int got;
                try (Transaction tx = Transaction.openRoot()) {
                    got = items.extract(i, r, accepted, tx);
                    if (got > 0) {
                        tx.commit();
                    }
                }
                if (got <= 0) {
                    break;
                }
                ItemStack leftover = net.insertFromMachine(r.toStack(got), target);
                int stored = got - leftover.getCount();
                if (!leftover.isEmpty()) {
                    ItemStack back = Transfers.insertItem(items, leftover, true, false);
                    if (!back.isEmpty()) {
                        Block.popResource(level, pos, back);
                    }
                }
                budget -= got;
                moved += stored;
                if (stored < accepted || got < takeMax) {
                    break;
                }
            }
        }
    }

    private void importFluids(GolemNetwork net, BlockPos target, ResourceHandler<FluidResource> fluids, boolean noFilters) {
        int budget = opFluid;
        for (int i = 0; i < fluids.size() && budget > 0; i++) {
            FluidResource r = fluids.getResource(i);
            int amt = fluids.getAmountAsInt(i);
            if (r.isEmpty() || amt <= 0) {
                continue;
            }
            FluidStack f = r.toStack(Math.min(budget, amt));
            if (!passesFluidImport(f, noFilters)) {
                continue;
            }
            int simGot;
            try (Transaction simTx = Transaction.openRoot()) {
                simGot = fluids.extract(i, r, f.getAmount(), simTx);
            }
            if (simGot <= 0) {
                continue;
            }
            FluidStack sim = r.toStack(simGot);
            int wanted = Math.min(sim.getAmount(), net.machineWantsFluid(sim));
            int accepted = wanted + net.insertFluid(sim.copyWithAmount(sim.getAmount() - wanted), true, target);
            if (accepted <= 0) {
                sleepWaitStorage = true;
                continue;
            }
            int got;
            try (Transaction tx = Transaction.openRoot()) {
                got = fluids.extract(i, r, accepted, tx);
                if (got > 0) {
                    tx.commit();
                }
            }
            if (got <= 0) {
                continue;
            }
            int stored = net.insertFluidFromMachine(r.toStack(got), target);
            if (stored < got) {
                Transfers.insertFluid(fluids, r.toStack(got - stored), false);
            }
            budget -= got;
            moved += stored;
            fluidMoved += stored;
        }
    }

    private boolean passesItemImport(ItemStack stack, boolean noFilters, boolean fuzzy) {
        if (noFilters) {
            return true;
        }
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack f = filters.getItem(i);
            if (!f.isEmpty() && filterFluids[i].isEmpty() && Fuzzy.matches(f, stack, fuzzy)) {
                return !denyList;
            }
        }
        return denyList;
    }

    private boolean passesFluidImport(FluidStack fluid, boolean noFilters) {
        if (noFilters) {
            return true;
        }
        for (int i = 0; i < FILTER_SLOTS; i++) {
            FluidStack ff = filterFluids[i];
            if (!ff.isEmpty() && FluidStack.isSameFluidSameComponents(ff, fluid)) {
                return !denyList;
            }
        }
        return denyList;
    }

    // ---------------------------------------------------------------- export

    private void doExport(Level level, GolemNetwork net, BlockPos target,
                          @Nullable ResourceHandler<ItemResource> items, @Nullable ResourceHandler<FluidResource> fluids) {
        ensureFilterCache();
        int itemBudget = opItems;
        int fluidBudget = opFluid;
        Map<ItemKey, Long> toCraft = null;
        Set<ItemKey> netKeys = null;
        boolean fuzzy = fuzzy();

        BlockEntities.fillSlotOrder(slotOrder, order, next, level.getRandom());
        for (int slot : slotOrder) {
            ItemStack filter = filters.getItem(slot);
            if (filter.isEmpty()) {
                continue;
            }
            FluidStack fluidFilter = filterFluids[slot];
            if (!fluidFilter.isEmpty()) {
                if (fluids == null || fluidBudget <= 0) {
                    continue;
                }
                int filled = exportFluidSlot(net, target, fluids, filter, fluidFilter, fluidBudget);
                fluidBudget -= filled;
                moved += filled;
                fluidMoved += filled;
                if (filled > 0 && order == FormationBusBlockEntity.ROUND_ROBIN) {
                    next = (slot + 1) % FILTER_SLOTS;
                    break;
                }
                continue;
            }
            if (items == null || itemBudget <= 0) {
                continue;
            }
            int cap = remainingItemCap(items, filter, itemBudget, fuzzy);
            if (cap <= 0) {
                continue;
            }
            if (fuzzy && netKeys == null) {
                netKeys = net.listItemsShared().keySet();
            }
            ItemStack want = Fuzzy.plain(filter).copyWithCount(1);
            int slotMoved = exportItemSlot(level, net, target, items, filter, want, cap, fuzzy, netKeys);
            boolean nothingInNetwork = slotMoved < 0;
            if (nothingInNetwork) {
                slotMoved = 0;
            }
            itemBudget -= slotMoved;
            moved += slotMoved;
            if (slotMoved > 0 && order == FormationBusBlockEntity.ROUND_ROBIN) {
                next = (slot + 1) % FILTER_SLOTS;
                break;
            }
            if (nothingInNetwork && upgrades.has(UpgradeType.CRAFTING)) {
                ItemKey key = ItemKey.of(want);
                if (Transfers.insertItem(items, want, true, true).isEmpty()
                        && net.canCraft(key) && !net.busCraftPending(worldPosition, key)) {
                    if (toCraft == null) {
                        toCraft = new LinkedHashMap<>();
                    }
                    int perStack = Math.max(1, want.getMaxStackSize());
                    toCraft.put(key, (long) Math.min(perStack, Math.min(itemsPerOperation(), cap)));
                }
            }
        }
        dispatchCraftOrders(level, net, toCraft);
    }

    private int exportFluidSlot(GolemNetwork net, BlockPos target, ResourceHandler<FluidResource> fluids,
                                ItemStack filter, FluidStack fluidFilter, int budget) {
        int cap = budget;
        int keep = BusMenu.keep(filter);
        if (keep > 0) {
            long have = countMatchingFluid(fluids, fluidFilter);
            if (have >= keep) {
                return 0;
            }
            cap = (int) Math.min(cap, keep - have);
        }
        FluidStack sim = net.extractFluid(fluidFilter, cap, true, target);
        if (sim.isEmpty()) {
            return 0;
        }
        int accepted = Transfers.insertFluid(fluids, sim, true);
        if (accepted <= 0) {
            return 0;
        }
        FluidStack real = net.extractFluid(fluidFilter, accepted, false, target);
        if (real.isEmpty()) {
            return 0;
        }
        int filled = Transfers.insertFluid(fluids, real, false);
        if (filled < real.getAmount()) {
            net.insertFluid(real.copyWithAmount(real.getAmount() - filled), false, target);
        }
        return filled;
    }

    private static long countMatchingFluid(ResourceHandler<FluidResource> fluids, FluidStack fluidFilter) {
        long have = 0;
        for (int t = 0; t < fluids.size(); t++) {
            FluidResource r = fluids.getResource(t);
            int amt = fluids.getAmountAsInt(t);
            if (!r.isEmpty() && amt > 0 && r.matches(fluidFilter)) {
                have += amt;
            }
        }
        return have;
    }

    private static int remainingItemCap(ResourceHandler<ItemResource> items, ItemStack filter, int budget, boolean fuzzy) {
        int keep = BusMenu.keep(filter);
        if (keep <= 0) {
            return budget;
        }
        long have = 0;
        for (int s = 0; s < items.size(); s++) {
            ItemResource r = items.getResource(s);
            int amt = items.getAmountAsInt(s);
            if (!r.isEmpty() && amt > 0 && Fuzzy.matches(filter, r.toStack(1), fuzzy)) {
                have += amt;
            }
        }
        return have >= keep ? 0 : (int) Math.min(budget, keep - have);
    }

    /** Scratch state for {@link #exportItemSlot} (avoids per-slot allocations). */
    private int exportMoved;
    private boolean exportFound;
    private boolean exportBlocked;

    /**
     * Exports matching items for one filter slot up to {@code cap}.
     * Returns moved count (>= 0), or {@code -1} when nothing matching existed in the network (eligible for autocrafting).
     */
    private int exportItemSlot(Level level, GolemNetwork net, BlockPos target, ResourceHandler<ItemResource> items,
                               ItemStack filter, ItemStack want, int cap, boolean fuzzy, @Nullable Set<ItemKey> netKeys) {
        exportMoved = 0;
        exportFound = false;
        exportBlocked = false;
        exportSingleItem(level, net, target, items, want, cap);
        if (fuzzy && !exportBlocked && cap - exportMoved > 0 && netKeys != null) {
            for (ItemKey k : Fuzzy.candidates(filter, netKeys, true)) {
                if (k.matches(want)) {
                    continue;
                }
                exportSingleItem(level, net, target, items, k.toStack(1), cap);
                if (exportBlocked || cap - exportMoved <= 0) {
                    break;
                }
            }
        }
        return exportFound ? exportMoved : -1;
    }

    private void exportSingleItem(Level level, GolemNetwork net, BlockPos target, ResourceHandler<ItemResource> items,
                                  ItemStack template, int cap) {
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (cap - exportMoved > 0) {
            int ask = Math.min(cap - exportMoved, maxStack);
            ItemStack sim = net.extractItem(template, ask, true, target);
            if (sim.isEmpty()) {
                return;
            }
            exportFound = true;
            // top up existing stacks first (like a hopper), then empty slots
            ItemStack restSim = Transfers.insertItem(items, sim, true, true);
            int accepted = sim.getCount() - restSim.getCount();
            if (accepted <= 0) {
                exportBlocked = true;
                return;
            }
            ItemStack real = net.extractItem(template, accepted, false, target);
            if (real.isEmpty()) {
                return;
            }
            int realCount = real.getCount();
            ItemStack rest = Transfers.insertItem(items, real, true, false);
            if (!rest.isEmpty()) {
                ItemStack unreturned = net.insertItem(rest, false, target);
                if (!unreturned.isEmpty()) {
                    Block.popResource(level, worldPosition, unreturned);
                }
            }
            exportMoved += realCount - rest.getCount();
            if (accepted < ask) {
                exportBlocked = true;
                return;
            }
        }
    }

    private void dispatchCraftOrders(Level level, GolemNetwork net, @Nullable Map<ItemKey, Long> toCraft) {
        long now = level.getGameTime();
        if (toCraft == null || now - lastCraftRequest < CRAFT_REQUEST_COOLDOWN) {
            return;
        }
        lastCraftRequest = now;
        for (Map.Entry<ItemKey, Long> e : toCraft.entrySet()) {
            if (BlockEntities.requestCraft(net, worldPosition, e.getKey(), e.getValue()).type() == GolemNetwork.StartType.NO_CPU) {
                break;
            }
        }
    }

    // ----------------------------------------------------------- persistence

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Filters", filters.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putInt("Priority", priority);
        tag.putInt("Order", order);
        tag.putInt("Next", next);
        tag.putBoolean("DenyList", denyList);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        filters.fromTag(tag.getListOrEmpty("Filters"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        priority = tag.getIntOr("Priority", 0);
        order = Math.floorMod(tag.getIntOr("Order", FormationBusBlockEntity.IN_ORDER), 3);
        next = Math.floorMod(tag.getIntOr("Next", 0), FILTER_SLOTS);
        denyList = tag.getBooleanOr("DenyList", false);
        redstone.load(tag);
        filterCacheDirty = true;
        upgradesDirty = true;
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
