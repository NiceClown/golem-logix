package com.golemlogix.blockentity;

import com.golemlogix.blocks.InjectorExportBusBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.menu.Priority;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared block entity of both world buses.
 * The Vacuum Import Bus pulls from the container in front of it into the network (the filter is optional),
 * the Injector Export Bus unloads from the network into the container only what is set in the filter.
 * A filter slot holding a bucket (any fluid container) selects a fluid.
 * Six upgrade slots: up to four Compressed Co-Processors (faster and more at a time), an Autocraft Module (export orders
 * what is missing from the calculators) and a Redstone Module.
 * An Export Bus filter slot can name an amount to keep (the slot window, see FilterSlotScreen): the bus then tops the container in
 * front up to it and no further, like a regulator - say, 16 coal in a furnace.
 * The Export Bus goes through its filter in order (the first slot the network has goes first), round robin (each operation
 * starts at the slot after the one that went out last) or at random (FormationBusBlockEntity.IN_ORDER, ROUND_ROBIN, RANDOM).
 * The Import Bus filter is an allow list (only what it names comes in) or a deny list (everything but that).
 */
public class BusBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    /** How often a bus with an Autocraft Module may order a craft (ticks). */
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    /** Work sound at most once every half second (with speed upgrades the bus works almost every tick). */
    private static final int SOUND_COOLDOWN = 10;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();

    private int moved = 0;
    /** Export: the order of the filter slots, and the slot round robin starts at next time. */
    private int order = FormationBusBlockEntity.IN_ORDER;
    private int next;
    /** Import: the filter is a deny list. */
    private boolean denyList;
    /** Bus priority: buses with a bigger number in one network work earlier (they get access to items first). */
    private int priority = 0;
    private long lastSound = -1000;
    private long lastCraftRequest = -1000;

    /**
     * When a bus works. A bus goes as often as its load calls for, and never loses speed by it, because it moves as much as the ticks
     * since its last operation allow:
     * <ul>
     * <li>a bus with a lot to move works at its interval (a heavy operation halves the wait, so it is at full speed in a few);</li>
     * <li>a trickle waits for a batch: a light operation doubles the wait, up to a second;</li>
     * <li>a bus with nothing to do sleeps: its interval, doubled with every idle operation, up to two seconds. A change in the block
     * in front, a new filter or upgrade or (for an export bus) anything entering or leaving its network wakes it.</li>
     * </ul>
     * Until nextRun comes, a bus only compares two numbers.
     */
    private static final int MAX_IDLE_TICKS = 40, MAX_IDLE_STEPS = 6, MAX_LIGHT_TICKS = 20, CREDIT_TICKS = 20;
    /** An operation that moves less than this (items; 62 mB count as an item) is light, one that moves this twice over is heavy. */
    private static final int LIGHT_LOAD = 16;
    private int idleSteps;
    private int period;
    private long nextRun;
    private long lastRun;
    private int deferrals;
    private boolean sleeping;
    private long sleepEpoch;
    private long sleepNetworkEpoch;
    private int fluidMoved;
    /** What this operation may move. */
    private int opItems, opFluid;
    /** Operations so far (the game test counts them). */
    public int operations;
    /** The handlers of the block in front, kept until NeoForge reports a change there (which also wakes the bus). */
    @org.jetbrains.annotations.Nullable
    private com.golemlogix.system.TargetCaps caps;

    public BusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUS.get(), pos, state);
        this.upgrades = new UpgradeContainer(state.getBlock() instanceof InjectorExportBusBlock ? UpgradeSpec.EXPORT_BUS : UpgradeSpec.IMPORT_BUS, this);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int value) {
        this.priority = Mth.clamp(value, -Priority.MAX, Priority.MAX);
        setChanged();
        // the network remembers the order of its buses
        if (level != null && !level.isClientSide()) {
            GolemNetwork.clearCache();
        }
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    /** Its last operation moved nothing, so it is sleeping between operations. */
    public boolean idle() {
        return idleSteps > 0;
    }

    /** Ends the idle sleep: the bus works at its next turn. */
    public void wake() {
        idleSteps = 0;
        period = 0;
        nextRun = 0;
        sleeping = false;
    }

    /** The block in front changed. A sleeping bus wakes; a working one keeps its pace (its own moves change that block too). */
    public void nudge() {
        if (sleeping) {
            wake();
        }
    }

    /** Filters, upgrades, priority or the redstone mode changed: look again at once. */
    @Override
    public void setChanged() {
        super.setChanged();
        wake();
    }

    public RedstoneControl redstone() {
        return redstone;
    }

    /** Window data: 0 = priority, 1 = redstone mode, 2 = the order (Export Bus) or the deny list (Import Bus). */
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

    /** Ticks between operations, counting speed upgrades. */
    public int interval() {
        return com.golemlogix.upgrade.BusSpeed.interval(upgrades.count(UpgradeType.SPEED));
    }

    public int itemsPerOperation() {
        return com.golemlogix.upgrade.BusSpeed.items(upgrades.count(UpgradeType.SPEED));
    }

    public int fluidPerOperation() {
        return com.golemlogix.upgrade.BusSpeed.fluid(upgrades.count(UpgradeType.SPEED));
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, BusBlockEntity be) {
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            be.redstone.observe(powered);
        }
        long now = level.getGameTime();
        if (now < be.nextRun) {
            if (!be.sleeping || !be.isExport() || be.sleepEpoch == GolemNetwork.storageEpoch()) {
                return;
            }
            // something changed in some network: an export bus wakes only if it was its own
            be.sleepEpoch = GolemNetwork.storageEpoch();
            if (GolemNetwork.get(level, pos).networkEpoch() == be.sleepNetworkEpoch) {
                return;
            }
            be.wake();
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        int interval = be.interval();
        if (be.period < interval) {
            be.period = interval;
        }
        // at full speed the buses of a network take turns by priority; a bus that was put off goes at once
        if (be.period == interval && be.deferrals == 0) {
            int phase = Math.floorMod(now - net.busRank(be), interval);
            if (phase != 0) {
                be.nextRun = now + interval - phase;
                return;
            }
        }
        be.nextRun = now + be.period;
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            return;
        }
        if (!be.redstone.allows(hasRedstone, powered, false)) {
            return;
        }
        Direction facing = state.getValue(DirectionalBlock.FACING);
        BlockPos target = pos.relative(facing);
        if (!level.isLoaded(target)) {
            return;
        }
        be.caps = com.golemlogix.system.TargetCaps.of(be.caps, (net.minecraft.server.level.ServerLevel) level, target, facing.getOpposite(),
                () -> !be.isRemoved(), be::wake);
        ResourceHandler<ItemResource> items = be.caps.items();
        ResourceHandler<FluidResource> fluids = be.caps.fluids();
        if (items == null && fluids == null) {
            return;
        }
        if (be.deferrals < com.golemlogix.system.BusLoad.MAX_DEFERRALS && com.golemlogix.system.BusLoad.overBudget(level)) {
            be.deferrals++;
            be.nextRun = now + 1;
            com.golemlogix.system.BusLoad.deferred();
            return;
        }
        be.deferrals = 0;
        // as much as the ticks since the last operation allow
        long ticks = Math.max(interval, Math.min(Math.max(interval, CREDIT_TICKS), now - be.lastRun));
        be.opItems = (int) (be.itemsPerOperation() * ticks / interval);
        be.opFluid = (int) (be.fluidPerOperation() * ticks / interval);
        be.moved = 0;
        be.fluidMoved = 0;
        be.operations++;
        long started = System.nanoTime();
        if (be.isExport()) {
            be.doExport(level, net, target, items, fluids);
        } else {
            be.doImport(level, pos, net, target, items, fluids);
        }
        com.golemlogix.system.BusLoad.ran(level, System.nanoTime() - started);
        be.lastRun = now;
        if (be.moved == 0) {
            // nothing to do: sleep for the interval doubled with every idle operation
            be.idleSteps = Math.min(MAX_IDLE_STEPS, be.idleSteps + 1);
            be.period = interval;
            be.nextRun = now + Math.max(interval, Math.min(MAX_IDLE_TICKS, interval << be.idleSteps));
            be.sleeping = true;
            be.sleepEpoch = GolemNetwork.storageEpoch();
            be.sleepNetworkEpoch = net.networkEpoch();
        } else {
            be.idleSteps = 0;
            be.sleeping = false;
            int load = be.moved - be.fluidMoved + be.fluidMoved / 62;
            if (load < LIGHT_LOAD) {
                be.period = Math.max(interval, Math.min(MAX_LIGHT_TICKS, be.period * 2));
            } else if (load >= LIGHT_LOAD * 2) {
                be.period = Math.max(interval, be.period / 2);
            }
            be.nextRun = now + be.period;
            // pulse mode: one action per pulse
            be.redstone.allows(hasRedstone, powered, true);
            if (now - be.lastSound >= SOUND_COOLDOWN) {
                be.lastSound = now;
                level.playSound(null, pos, be.isExport() ? ModSounds.PNEUMATIC_INJECT.get() : ModSounds.VACUUM_SUCTION.get(),
                        SoundSource.BLOCKS, 0.25f, 1.0f);
                if (level.getRandom().nextInt(3) == 0) {
                    level.playSound(null, pos, ModSounds.TUBE_SCURRY.get(), SoundSource.BLOCKS, 0.2f, 1.0f);
                }
            }
        }
    }

    private static Optional<FluidStack> fluidOf(ItemStack filter) {
        if (filter.isEmpty()) {
            return Optional.empty();
        }
        FluidStack ghost = com.golemlogix.items.FluidGhostItem.fluid(filter);
        if (!ghost.isEmpty()) {
            return Optional.of(ghost);
        }
        FluidStack f = Transfers.fluidOf(filter);
        return f.isEmpty() ? Optional.empty() : Optional.of(f);
    }

    private List<ItemStack> itemFilters() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack s = filters.getItem(i);
            if (!s.isEmpty() && fluidOf(s).isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    /** The filter slots in the export order: from the first, from the slot after the last one exported, or shuffled. */
    private int[] exportOrder(Level level) {
        int[] slots = new int[FILTER_SLOTS];
        int start = order == FormationBusBlockEntity.ROUND_ROBIN ? next : 0;
        for (int k = 0; k < FILTER_SLOTS; k++) {
            slots[k] = (start + k) % FILTER_SLOTS;
        }
        if (order == FormationBusBlockEntity.RANDOM) {
            for (int k = FILTER_SLOTS - 1; k > 0; k--) {
                int r = level.getRandom().nextInt(k + 1), t = slots[k];
                slots[k] = slots[r];
                slots[r] = t;
            }
        }
        return slots;
    }

    private List<FluidStack> fluidFilters() {
        List<FluidStack> list = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            fluidOf(filters.getItem(i)).ifPresent(list::add);
        }
        return list;
    }

    // ---------------------------------------------------------------- import

    private void doImport(Level level, BlockPos pos, GolemNetwork net, BlockPos target,
                          ResourceHandler<ItemResource> items, ResourceHandler<FluidResource> fluids) {
        List<ItemStack> itemFilters = itemFilters();
        List<FluidStack> fluidFilters = fluidFilters();
        boolean noFilters = itemFilters.isEmpty() && fluidFilters.isEmpty();

        if (items != null && (denyList || noFilters || !itemFilters.isEmpty())) {
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
                    if (!passes(inSlot, itemFilters, noFilters, fuzzy(), denyList)) {
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
                    // what an autocraft job is waiting for is accepted even when storage is full (otherwise the job would hang);
                    // items are never put back into the same container (if a periscope connects it)
                    int wanted = Math.min(sim.getCount(), net.machineWants(sim));
                    ItemStack restSim = net.insertItem(sim.copyWithCount(sim.getCount() - wanted), true, target);
                    int accepted = wanted + (sim.getCount() - wanted - restSim.getCount());
                    if (accepted <= 0) {
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
                    ItemStack real = r.toStack(got);
                    ItemStack leftover = net.insertFromMachine(real, target);
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

        if (fluids != null && (denyList || noFilters || !fluidFilters.isEmpty())) {
            int budget = opFluid;
            for (int i = 0; i < fluids.size() && budget > 0; i++) {
                FluidResource r = fluids.getResource(i);
                int amt = fluids.getAmountAsInt(i);
                if (r.isEmpty() || amt <= 0) {
                    continue;
                }
                FluidStack f = r.toStack(Math.min(budget, amt));
                if (!noFilters && fluidFilters.stream().anyMatch(ff -> FluidStack.isSameFluidSameComponents(ff, f)) == denyList) {
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
                FluidStack real = r.toStack(got);
                int stored = net.insertFluidFromMachine(real, target);
                if (stored < got) {
                    Transfers.insertFluid(fluids, r.toStack(got - stored), false);
                }
                budget -= got;
                moved += stored;
                fluidMoved += stored;
            }
        }
    }

    /** An allow list takes what it names (an empty filter: everything), a deny list everything it does not name. */
    private static boolean passes(ItemStack stack, List<ItemStack> itemFilters, boolean noFilters, boolean fuzzy, boolean deny) {
        if (noFilters) {
            return true;
        }
        for (ItemStack f : itemFilters) {
            if (com.golemlogix.system.Fuzzy.matches(f, stack, fuzzy)) {
                return !deny;
            }
        }
        return deny;
    }

    /** A Fuzzy Card is installed: filters match any components, or any item of the tag chosen on them. */
    private boolean fuzzy() {
        return upgrades.has(UpgradeType.FUZZY);
    }

    // ---------------------------------------------------------------- export

    private void doExport(Level level, GolemNetwork net, BlockPos target,
                          ResourceHandler<ItemResource> items, ResourceHandler<FluidResource> fluids) {
        int itemBudget = opItems;
        int fluidBudget = opFluid;
        Map<ItemKey, Long> toCraft = null;

        int[] slotOrder = exportOrder(level);
        boolean fuzzy = fuzzy();
        java.util.Set<ItemKey> netKeys = null;
        for (int slot : slotOrder) {
            ItemStack filter = filters.getItem(slot);
            if (filter.isEmpty()) {
                continue;
            }
            FluidStack fluidFilter = fluidOf(filter).orElse(FluidStack.EMPTY);
            if (!fluidFilter.isEmpty()) {
                if (fluids == null || fluidBudget <= 0) {
                    continue;
                }
                int cap = fluidBudget;
                int keep = com.golemlogix.menu.BusMenu.keep(filter);
                if (keep > 0) {
                    long have = 0;
                    for (int t = 0; t < fluids.size(); t++) {
                        FluidResource r = fluids.getResource(t);
                        int amt = fluids.getAmountAsInt(t);
                        if (!r.isEmpty() && amt > 0 && r.matches(fluidFilter)) {
                            have += amt;
                        }
                    }
                    cap = (int) Math.min(cap, keep - have);
                    if (cap <= 0) {
                        continue;
                    }
                }
                FluidStack sim = net.extractFluid(fluidFilter, cap, true, target);
                if (sim.isEmpty()) {
                    continue;
                }
                int accepted = Transfers.insertFluid(fluids, sim, true);
                if (accepted <= 0) {
                    continue;
                }
                FluidStack real = net.extractFluid(fluidFilter, accepted, false, target);
                if (real.isEmpty()) {
                    continue;
                }
                int filled = Transfers.insertFluid(fluids, real, false);
                if (filled < real.getAmount()) {
                    net.insertFluid(real.copyWithAmount(real.getAmount() - filled), false, target);
                }
                fluidBudget -= filled;
                this.moved += filled;
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
            ItemStack want = com.golemlogix.system.Fuzzy.plain(filter).copyWithCount(1);
            // a keep amount: only what the container still lacks of it
            int keep = com.golemlogix.menu.BusMenu.keep(filter);
            int cap = itemBudget;
            if (keep > 0) {
                long have = 0;
                for (int s = 0; s < items.size(); s++) {
                    ItemResource r = items.getResource(s);
                    int amt = items.getAmountAsInt(s);
                    if (!r.isEmpty() && amt > 0 && com.golemlogix.system.Fuzzy.matches(filter, r.toStack(1), fuzzy)) {
                        have += amt;
                    }
                }
                cap = (int) Math.min(cap, keep - have);
                if (cap <= 0) {
                    continue;
                }
            }
            // what may go out for this filter: its own item first, with a Fuzzy Card also what the network has that matches it
            List<ItemStack> wants = new ArrayList<>();
            wants.add(want);
            if (fuzzy) {
                if (netKeys == null) {
                    netKeys = net.listItems().keySet();
                }
                for (ItemKey k : com.golemlogix.system.Fuzzy.candidates(filter, netKeys, true)) {
                    if (!k.matches(want)) {
                        wants.add(k.toStack(1));
                    }
                }
            }
            int perStack = Math.max(1, want.getMaxStackSize());
            int slotMoved = 0;
            boolean nothingInNetwork = true;
            for (ItemStack one : wants) {
                boolean stop = false;
                while (cap - slotMoved > 0) {
                    int ask = Math.min(cap - slotMoved, Math.max(1, one.getMaxStackSize()));
                    // do not move from a container into the same container (a periscope on the same chest)
                    ItemStack sim = net.extractItem(one, ask, true, target);
                    if (sim.isEmpty()) {
                        break;
                    }
                    nothingInNetwork = false;
                    // top up stacks already started first (like a hopper), then empty slots
                    ItemStack restSim = Transfers.insertItem(items, sim, true, true);
                    int accepted = sim.getCount() - restSim.getCount();
                    if (accepted <= 0) {
                        stop = true;
                        break;
                    }
                    ItemStack real = net.extractItem(one, accepted, false, target);
                    if (real.isEmpty()) {
                        break;
                    }
                    int realCount = real.getCount();
                    ItemStack rest = Transfers.insertItem(items, real, true, false);
                    if (!rest.isEmpty()) {
                        ItemStack unreturned = net.insertItem(rest, false, target);
                        if (!unreturned.isEmpty()) {
                            Block.popResource(level, worldPosition, unreturned);
                        }
                    }
                    slotMoved += realCount - rest.getCount();
                    if (accepted < ask) {
                        stop = true;
                        break;
                    }
                }
                if (stop || cap - slotMoved <= 0) {
                    break;
                }
            }
            itemBudget -= slotMoved;
            this.moved += slotMoved;
            if (slotMoved > 0 && order == FormationBusBlockEntity.ROUND_ROBIN) {
                // round robin: one slot an operation, the next operation starts after it
                next = (slot + 1) % FILTER_SLOTS;
                break;
            }
            if (nothingInNetwork && upgrades.has(UpgradeType.CRAFTING)) {
                ItemKey key = ItemKey.of(want);
                // order only if the container accepts this item at all
                if (Transfers.insertItem(items, want, true, true).isEmpty() && net.canCraft(key)
                        && !net.busCraftPending(worldPosition, key)) {
                    if (toCraft == null) {
                        toCraft = new LinkedHashMap<>();
                    }
                    toCraft.put(key, (long) Math.min(perStack, Math.min(itemsPerOperation(), cap)));
                }
            }
        }

        // Autocraft Module: what the network lacks is ordered from a calculator (one item at a time, at most once every 2 seconds)
        long now = level.getGameTime();
        if (toCraft != null && now - lastCraftRequest >= CRAFT_REQUEST_COOLDOWN) {
            lastCraftRequest = now;
            for (Map.Entry<ItemKey, Long> e : toCraft.entrySet()) {
                Map<ItemKey, Long> one = new LinkedHashMap<>();
                one.put(e.getKey(), e.getValue());
                GolemNetwork.StartResult r = net.startCrafting(one, null, worldPosition);
                if (r.type() == GolemNetwork.StartType.NO_CPU) {
                    break;
                }
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

    /** Since 1.21.5 the block's removal side effects run from here, while this entity and its contents are still in place. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
