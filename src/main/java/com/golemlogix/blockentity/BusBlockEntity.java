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
 * Общая блок-сущность обеих шин мира.
 * Вакуумная шина импорта забирает из контейнера перед собой в сеть (фильтр необязателен),
 * Инжекторная шина экспорта выгружает из сети в контейнер только то, что записано в фильтре.
 * Ячейка фильтра с ведром (любым контейнером жидкости) задаёт жидкость.
 * Шесть ячеек апгрейдов: до четырёх Сжатых Сопроцессоров (быстрее и больше за раз), Модуль Автокрафта (экспорт заказывает
 * недостающее у калькуляторов) и Модуль Редстоуна.
 */
public class BusBlockEntity extends BlockEntity {
    public static final int FILTER_SLOTS = 9;
    /** Множители «предметов за раз» и интервала по числу ускорителей (0..4). */
    private static final int[] AMOUNT_MULT = {1, 2, 4, 6, 8};
    private static final double[] INTERVAL_MULT = {1.0, 0.75, 0.5, 0.25, 0.125};
    /** Как часто шина с Модулем Автокрафта может заказывать крафт (тики). */
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    /** Не чаще раза в полсекунды звук работы (с ускорителями шина работает почти каждый тик). */
    private static final int SOUND_COOLDOWN = 10;

    private final OwnedContainer filters = new OwnedContainer(FILTER_SLOTS, this, s -> true);
    private UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();

    private int moved = 0;
    /** Приоритет шины: шины с большим числом в одной сети работают раньше (первыми получают доступ к предметам). */
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

    /** Данные окна: 0 — приоритет, 1 — режим редстоуна. */
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

    /** Тиков между операциями с учётом ускорителей. */
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
            // импульсный режим: одно действие на импульс
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
                // то, чего ждёт задача автокрафта, принимается даже при полном хранилище (иначе задача зависнет);
                // обратно в тот же контейнер (если он подключён перископом) предметы не кладутся
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
                    // из того же контейнера в него же не перекладываем (перископ на том же сундуке)
                    ItemStack sim = net.extractItem(want, ask, true, target);
                    if (sim.isEmpty()) {
                        nothingInNetwork = moved == 0;
                        break;
                    }
                    // сначала доливаем в уже начатые стопки (как воронка), потом в пустые ячейки
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
                    // заказываем, только если контейнер вообще примет этот предмет
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

        // Модуль Автокрафта: того, чего нет в сети, — заказ калькулятору (по предмету за раз, не чаще раза в 2 секунды)
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
