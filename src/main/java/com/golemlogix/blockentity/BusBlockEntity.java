package com.golemlogix.blockentity;

import com.golemlogix.blocks.InjectorExportBusBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.menu.Priority;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

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
 */
public class BusBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    /** Multipliers of "items at a time" and of the interval by the number of speed upgrades (0..4). */
    private static final int[] AMOUNT_MULT = {1, 2, 4, 6, 8};
    private static final double[] INTERVAL_MULT = {1.0, 0.75, 0.5, 0.25, 0.125};
    /** How often a bus with an Autocraft Module may order a craft (ticks). */
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    /** Work sound at most once every half second (with speed upgrades the bus works almost every tick). */
    private static final int SOUND_COOLDOWN = 10;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();

    private int moved = 0;
    /** Bus priority: buses with a bigger number in one network work earlier (they get access to items first). */
    private int priority = 0;
    private long lastSound = -1000;
    private long lastCraftRequest = -1000;

    /**
     * An idle bus (its last operations moved nothing) sleeps longer and longer: its interval, doubled each time, up to a second.
     * A neighbour change, a new filter or upgrade, or (for an export bus) anything entering or leaving the network wakes it.
     */
    private static final int MAX_IDLE_TICKS = 20, MAX_IDLE_STEPS = 5;
    private int idleSteps;
    private long sleepUntil;
    private long sleepEpoch;
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
        sleepUntil = 0;
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

    /** Window data: 0 = priority, 1 = redstone mode. */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? priority : redstone.mode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    setPriority(value);
                } else {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 2;
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
        int n = upgrades.count(UpgradeType.SPEED);
        return Math.max(1, (int) Math.round(GolemConfig.BUS_INTERVAL_TICKS.get() * INTERVAL_MULT[n]));
    }

    public int itemsPerOperation() {
        return GolemConfig.BUS_ITEMS_PER_OPERATION.get() * AMOUNT_MULT[upgrades.count(UpgradeType.SPEED)];
    }

    public int fluidPerOperation() {
        return GolemConfig.BUS_FLUID_PER_OPERATION.get() * AMOUNT_MULT[upgrades.count(UpgradeType.SPEED)];
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, BusBlockEntity be) {
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            be.redstone.observe(powered);
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        int interval = be.interval();
        if (Math.floorMod(level.getGameTime() - net.busRank(be), interval) != 0) {
            return;
        }
        long now = level.getGameTime();
        if (now < be.sleepUntil && !(be.isExport() && be.sleepEpoch != GolemNetwork.storageEpoch())) {
            return;
        }
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
        IItemHandler items = be.caps.items();
        IFluidHandler fluids = be.caps.fluids();
        if (items == null && fluids == null) {
            return;
        }
        be.moved = 0;
        if (be.isExport()) {
            be.doExport(level, net, target, items, fluids);
        } else {
            be.doImport(level, pos, net, target, items, fluids);
        }
        if (be.moved == 0) {
            // nothing to do: sleep for the interval doubled with every idle operation, at most a second
            be.idleSteps = Math.min(MAX_IDLE_STEPS, be.idleSteps + 1);
            be.sleepUntil = now + Math.max(interval, Math.min(MAX_IDLE_TICKS, interval << be.idleSteps));
            be.sleepEpoch = GolemNetwork.storageEpoch();
        } else {
            be.idleSteps = 0;
            be.sleepUntil = 0;
            // pulse mode: one action per pulse
            be.redstone.allows(hasRedstone, powered, true);
            if (now - be.lastSound >= SOUND_COOLDOWN) {
                be.lastSound = now;
                level.playSound(null, pos, be.isExport() ? ModSounds.PNEUMATIC_INJECT.get() : ModSounds.VACUUM_SUCTION.get(),
                        SoundSource.BLOCKS, 0.25f, 1.0f);
                if (level.random.nextInt(3) == 0) {
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
        return FluidUtil.getFluidContained(filter).filter(f -> !f.isEmpty());
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

    private List<FluidStack> fluidFilters() {
        List<FluidStack> list = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            fluidOf(filters.getItem(i)).ifPresent(list::add);
        }
        return list;
    }

    // ---------------------------------------------------------------- import

    private void doImport(Level level, BlockPos pos, GolemNetwork net, BlockPos target, IItemHandler items, IFluidHandler fluids) {
        List<ItemStack> itemFilters = itemFilters();
        List<FluidStack> fluidFilters = fluidFilters();
        boolean noFilters = itemFilters.isEmpty() && fluidFilters.isEmpty();

        if (items != null && (noFilters || !itemFilters.isEmpty())) {
            int budget = itemsPerOperation();
            for (int i = 0; i < items.getSlots() && budget > 0; i++) {
                ItemStack inSlot = items.getStackInSlot(i);
                if (inSlot.isEmpty() || !passes(inSlot, itemFilters, noFilters, fuzzy())) {
                    continue;
                }
                ItemStack sim = items.extractItem(i, Math.min(budget, inSlot.getCount()), true);
                if (sim.isEmpty()) {
                    continue;
                }
                // what an autocraft job is waiting for is accepted even when storage is full (otherwise the job would hang);
                // items are never put back into the same container (if a periscope connects it)
                int wanted = Math.min(sim.getCount(), net.machineWants(sim));
                ItemStack restSim = net.insertItem(sim.copyWithCount(sim.getCount() - wanted), true, target);
                int accepted = wanted + (sim.getCount() - wanted - restSim.getCount());
                if (accepted <= 0) {
                    continue;
                }
                ItemStack real = items.extractItem(i, accepted, false);
                if (real.isEmpty()) {
                    continue;
                }
                ItemStack leftover = net.insertFromMachine(real, target);
                int stored = real.getCount() - leftover.getCount();
                if (!leftover.isEmpty()) {
                    ItemStack back = ItemHandlerHelper.insertItemStacked(items, leftover, false);
                    if (!back.isEmpty()) {
                        Block.popResource(level, pos, back);
                    }
                }
                budget -= real.getCount();
                moved += stored;
            }
        }

        if (fluids != null && (noFilters || !fluidFilters.isEmpty())) {
            int budget = fluidPerOperation();
            for (int i = 0; i < fluids.getTanks() && budget > 0; i++) {
                FluidStack f = fluids.getFluidInTank(i);
                if (f.isEmpty()) {
                    continue;
                }
                if (!noFilters && fluidFilters.stream().noneMatch(ff -> FluidStack.isSameFluidSameComponents(ff, f))) {
                    continue;
                }
                FluidStack sim = fluids.drain(f.copyWithAmount(Math.min(budget, f.getAmount())), IFluidHandler.FluidAction.SIMULATE);
                if (sim.isEmpty()) {
                    continue;
                }
                int accepted = net.insertFluid(sim, true, target);
                if (accepted <= 0) {
                    continue;
                }
                FluidStack real = fluids.drain(sim.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                if (real.isEmpty()) {
                    continue;
                }
                int stored = net.insertFluid(real, false, target);
                if (stored < real.getAmount()) {
                    fluids.fill(real.copyWithAmount(real.getAmount() - stored), IFluidHandler.FluidAction.EXECUTE);
                }
                budget -= real.getAmount();
                moved += stored;
            }
        }
    }

    private static boolean passes(ItemStack stack, List<ItemStack> itemFilters, boolean noFilters, boolean fuzzy) {
        if (noFilters) {
            return true;
        }
        for (ItemStack f : itemFilters) {
            if (com.golemlogix.system.Fuzzy.matches(f, stack, fuzzy)) {
                return true;
            }
        }
        return false;
    }

    /** A Fuzzy Card is installed: filters match any components, or any item of the tag chosen on them. */
    private boolean fuzzy() {
        return upgrades.has(UpgradeType.FUZZY);
    }

    // ---------------------------------------------------------------- export

    private void doExport(Level level, GolemNetwork net, BlockPos target, IItemHandler items, IFluidHandler fluids) {
        int itemBudget = itemsPerOperation();
        int fluidBudget = fluidPerOperation();
        Map<ItemKey, Long> toCraft = new LinkedHashMap<>();

        if (items != null) {
            boolean fuzzy = fuzzy();
            java.util.Set<ItemKey> netKeys = fuzzy ? net.listItems().keySet() : java.util.Set.of();
            for (ItemStack filter : itemFilters()) {
                if (itemBudget <= 0) {
                    break;
                }
                ItemStack want = com.golemlogix.system.Fuzzy.plain(filter).copyWithCount(1);
                // what may go out for this filter: its own item first, with a Fuzzy Card also what the network has that matches it
                List<ItemStack> wants = new ArrayList<>();
                wants.add(want);
                for (ItemKey k : com.golemlogix.system.Fuzzy.candidates(filter, netKeys, fuzzy)) {
                    if (!k.matches(want)) {
                        wants.add(k.toStack(1));
                    }
                }
                int perStack = Math.max(1, want.getMaxStackSize());
                int moved = 0;
                boolean nothingInNetwork = true;
                for (ItemStack one : wants) {
                    boolean stop = false;
                    while (itemBudget - moved > 0) {
                        int ask = Math.min(itemBudget - moved, Math.max(1, one.getMaxStackSize()));
                        // do not move from a container into the same container (a periscope on the same chest)
                        ItemStack sim = net.extractItem(one, ask, true, target);
                        if (sim.isEmpty()) {
                            break;
                        }
                        nothingInNetwork = false;
                        // top up stacks already started first (like a hopper), then empty slots
                        ItemStack restSim = ItemHandlerHelper.insertItemStacked(items, sim, true);
                        int accepted = sim.getCount() - restSim.getCount();
                        if (accepted <= 0) {
                            stop = true;
                            break;
                        }
                        ItemStack real = net.extractItem(one, accepted, false, target);
                        if (real.isEmpty()) {
                            break;
                        }
                        ItemStack rest = ItemHandlerHelper.insertItemStacked(items, real, false);
                        if (!rest.isEmpty()) {
                            net.insertItem(rest, false, target);
                        }
                        moved += real.getCount() - rest.getCount();
                        if (accepted < ask) {
                            stop = true;
                            break;
                        }
                    }
                    if (stop || itemBudget - moved <= 0) {
                        break;
                    }
                }
                itemBudget -= moved;
                this.moved += moved;
                if (nothingInNetwork && upgrades.has(UpgradeType.CRAFTING)) {
                    ItemKey key = ItemKey.of(want);
                    // order only if the container accepts this item at all
                    if (ItemHandlerHelper.insertItemStacked(items, want, true).isEmpty() && net.canCraft(key)
                            && !net.busCraftPending(worldPosition, key)) {
                        toCraft.put(key, (long) Math.min(perStack, itemsPerOperation()));
                    }
                }
            }
        }

        if (fluids != null) {
            for (FluidStack filter : fluidFilters()) {
                if (fluidBudget <= 0) {
                    break;
                }
                FluidStack sim = net.extractFluid(filter, fluidBudget, true, target);
                if (sim.isEmpty()) {
                    continue;
                }
                int accepted = fluids.fill(sim, IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    continue;
                }
                FluidStack real = net.extractFluid(filter, accepted, false, target);
                if (real.isEmpty()) {
                    continue;
                }
                int filled = fluids.fill(real, IFluidHandler.FluidAction.EXECUTE);
                if (filled < real.getAmount()) {
                    net.insertFluid(real.copyWithAmount(real.getAmount() - filled), false, target);
                }
                fluidBudget -= filled;
                this.moved += filled;
            }
        }

        // Autocraft Module: what the network lacks is ordered from a calculator (one item at a time, at most once every 2 seconds)
        long now = level.getGameTime();
        if (!toCraft.isEmpty() && now - lastCraftRequest >= CRAFT_REQUEST_COOLDOWN) {
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
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        filters.fromTag(tag.getListOrEmpty("Filters"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        priority = tag.getIntOr("Priority", 0);
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
}
