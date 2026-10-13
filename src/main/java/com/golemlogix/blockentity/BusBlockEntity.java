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
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
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
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, target, facing.getOpposite());
        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, target, facing.getOpposite());
        if (items == null && fluids == null) {
            return;
        }
        be.moved = 0;
        if (be.isExport()) {
            be.doExport(level, net, target, items, fluids);
        } else {
            be.doImport(level, pos, net, target, items, fluids);
        }
        if (be.moved > 0) {
            // pulse mode: one action per pulse
            be.redstone.allows(hasRedstone, powered, true);
            long now = level.getGameTime();
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
                if (inSlot.isEmpty() || !passes(inSlot, itemFilters, noFilters)) {
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

    private static boolean passes(ItemStack stack, List<ItemStack> itemFilters, boolean noFilters) {
        if (noFilters) {
            return true;
        }
        for (ItemStack f : itemFilters) {
            if (ItemStack.isSameItemSameComponents(f, stack)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- export

    private void doExport(Level level, GolemNetwork net, BlockPos target, IItemHandler items, IFluidHandler fluids) {
        int itemBudget = itemsPerOperation();
        int fluidBudget = fluidPerOperation();
        Map<ItemKey, Long> toCraft = new LinkedHashMap<>();

        if (items != null) {
            for (ItemStack filter : itemFilters()) {
                if (itemBudget <= 0) {
                    break;
                }
                ItemStack want = filter.copyWithCount(1);
                int perStack = Math.max(1, want.getMaxStackSize());
                int moved = 0;
                boolean nothingInNetwork = false;
                while (itemBudget - moved > 0) {
                    int ask = Math.min(itemBudget - moved, perStack);
                    // do not move from a container into the same container (a periscope on the same chest)
                    ItemStack sim = net.extractItem(want, ask, true, target);
                    if (sim.isEmpty()) {
                        nothingInNetwork = moved == 0;
                        break;
                    }
                    // top up stacks already started first (like a hopper), then empty slots
                    ItemStack restSim = ItemHandlerHelper.insertItemStacked(items, sim, true);
                    int accepted = sim.getCount() - restSim.getCount();
                    if (accepted <= 0) {
                        break;
                    }
                    ItemStack real = net.extractItem(want, accepted, false, target);
                    if (real.isEmpty()) {
                        break;
                    }
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(items, real, false);
                    if (!rest.isEmpty()) {
                        net.insertItem(rest, false, target);
                    }
                    moved += real.getCount() - rest.getCount();
                    if (accepted < ask) {
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Filters", filters.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putInt("Priority", priority);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filters.fromTag(tag.getList("Filters", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        priority = tag.getInt("Priority");
        redstone.load(tag);
    }
}
