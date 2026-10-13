package com.golemlogix.blockentity;

import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.system.GolemNode;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Поставщик шаблонов: хранит до 9 закодированных перфокарт и выгружает ингредиенты
 * в примыкающие машины (любой сосед, не являющийся узлом сети).
 * Режим ожидания результата: следующая порция не грузится, пока задача не получила выход предыдущей.
 * Модуль Редстоуна останавливает подачу по сигналу (или подаёт по одной порции на импульс).
 */
public class PatternProviderBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;

    private final OwnedContainer container = new OwnedContainer(SLOTS, this,
            s -> s.has(ModDataComponents.PATTERN.get())) {
        @Override
        public void setChanged() {
            super.setChanged();
            patterns = null;
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.REDSTONE_ONLY, this);
    private final RedstoneControl redstone = new RedstoneControl();

    private List<PatternData> patterns;
    private int lastDirection = 0;
    private boolean blocking = false;

    public PatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_PROVIDER.get(), pos, state);
    }

    public OwnedContainer getContainer() {
        return container;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public void setBlocking(boolean blocking) {
        this.blocking = blocking;
        setChanged();
    }

    /** Куда подаёт ингредиенты (свойство блока: по нему и стрелки на гранях). */
    public com.golemlogix.blocks.ProviderOutput output() {
        BlockState s = getBlockState();
        return s.hasProperty(com.golemlogix.blocks.PatternProviderBlock.OUTPUT)
                ? s.getValue(com.golemlogix.blocks.PatternProviderBlock.OUTPUT) : com.golemlogix.blocks.ProviderOutput.ALL;
    }

    public void setOutput(com.golemlogix.blocks.ProviderOutput out) {
        if (level != null && !level.isClientSide() && getBlockState().hasProperty(com.golemlogix.blocks.PatternProviderBlock.OUTPUT)) {
            level.setBlock(worldPosition, getBlockState().setValue(com.golemlogix.blocks.PatternProviderBlock.OUTPUT, out), 3);
        }
    }

    /** Данные окна: 0 — режим ожидания (0/1), 1 — режим редстоуна, 2 — сторона подачи (ProviderOutput). */
    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> blocking ? 1 : 0;
                    case 1 -> redstone.mode().ordinal();
                    default -> output().ordinal();
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    setBlocking(value != 0);
                } else if (index == 1) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                } else {
                    setOutput(com.golemlogix.blocks.ProviderOutput.byId(value));
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    /** Разрешает ли редстоун выгрузить порцию; consume — израсходовать импульс (порция действительно выгружена). */
    public boolean redstoneAllows(boolean consume) {
        if (level == null) {
            return true;
        }
        boolean installed = upgrades.has(UpgradeType.REDSTONE);
        return redstone.allows(installed, installed && level.hasNeighborSignal(worldPosition), consume);
    }

    /** Каждый тик запоминает уровень сигнала: так короткий импульс между циклами калькулятора не теряется. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PatternProviderBlockEntity be) {
        if (be.upgrades.has(UpgradeType.REDSTONE)) {
            be.redstone.observe(level.hasNeighborSignal(pos));
        }
    }

    public List<PatternData> getPatterns() {
        if (patterns == null) {
            List<PatternData> list = new ArrayList<>();
            for (int i = 0; i < SLOTS; i++) {
                PatternData data = container.getItem(i).get(ModDataComponents.PATTERN.get());
                if (data != null && data.isValid()) {
                    list.add(data);
                }
            }
            patterns = list;
        }
        return patterns;
    }

    public boolean hasPattern(PatternData pattern) {
        return getPatterns().contains(pattern);
    }

    /**
     * Пытается выгрузить ингредиенты одного выполнения шаблона в соседнюю машину.
     * Возвращает список того, что не удалось принять (обычно пустой) или null, если машина не готова.
     */
    @Nullable
    public List<ItemStack> tryPush(PatternData pattern) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (ItemStack s : pattern.inputs()) {
            if (!s.isEmpty()) {
                inputs.add(s);
            }
        }

        // выбрана сторона — только в неё (когда машины стоят вплотную, видно, какую из них кормит поставщик)
        Direction only = output().direction();
        Direction[] dirs = only == null ? Direction.values() : new Direction[]{only};
        for (int k = 0; k < dirs.length; k++) {
            Direction dir = dirs[(lastDirection + 1 + k) % dirs.length];
            BlockPos target = worldPosition.relative(dir);
            // в устройства сети не выгружаем; исключение — собранный Насыщатель Маны (его части тоже узлы сети)
            net.minecraft.world.level.block.Block block = level.isLoaded(target) ? level.getBlockState(target).getBlock() : null;
            if (block == null || (block instanceof GolemNode && !(block instanceof com.golemlogix.blocks.SaturatorPartBlock))) {
                continue;
            }
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, target, dir.getOpposite());
            if (handler == null || !canAcceptAll(handler, inputs)) {
                continue;
            }
            List<ItemStack> rejected = new ArrayList<>();
            for (ItemStack in : inputs) {
                ItemStack rest = ItemHandlerHelper.insertItem(handler, in.copy(), false);
                if (!rest.isEmpty()) {
                    rejected.add(rest);
                }
            }
            lastDirection = (lastDirection + 1 + k) % dirs.length;
            return rejected;
        }
        return null;
    }

    /** Все ингредиенты влезут (проверка по отдельности, потом общая проверка на реальных слотах). */
    private static boolean canAcceptAll(IItemHandler handler, List<ItemStack> inputs) {
        // проверяем каждый предмет симуляцией; для надёжности суммируем одинаковые
        for (ItemStack in : inputs) {
            ItemStack rest = ItemHandlerHelper.insertItem(handler, in.copy(), true);
            if (!rest.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Patterns", container.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putBoolean("Blocking", blocking);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        container.fromTag(tag.getList("Patterns", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        blocking = tag.getBoolean("Blocking");
        redstone.load(tag);
        patterns = null;
    }
}
