package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.Priority;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Перископ: приоритет подключённого им внешнего хранилища и Модуль Редстоуна, которым хранилище можно включать и выключать.
 */
public class PeriscopeBlockEntity extends BlockEntity {
    private int priority = 0;
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.REDSTONE_ONLY, this);
    private final RedstoneControl redstone = new RedstoneControl();

    public PeriscopeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERISCOPE.get(), pos, state);
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

    /** Подключено ли хранилище сейчас (с Модулем Редстоуна — по сигналу; импульсный режим тут не имеет смысла). */
    public boolean storageEnabled() {
        if (level == null || !upgrades.has(UpgradeType.REDSTONE)) {
            return true;
        }
        boolean powered = level.hasNeighborSignal(worldPosition);
        return switch (redstone.mode()) {
            case HIGH -> powered;
            case LOW -> !powered;
            default -> true;
        };
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
                    RedstoneMode m = RedstoneMode.byId(value);
                    redstone.setMode(m == RedstoneMode.PULSE ? RedstoneMode.IGNORE : m);
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Priority", priority);
        tag.put("Upgrades", upgrades.createTag(registries));
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        priority = tag.getInt("Priority");
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        redstone.load(tag);
    }
}
