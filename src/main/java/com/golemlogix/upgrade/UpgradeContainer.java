package com.golemlogix.upgrade;

import com.golemlogix.blockentity.OwnedContainer;
import com.golemlogix.items.UpgradeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Ячейки апгрейдов машины: в каждой по одному апгрейду, машина принимает только свои типы и не больше лимита.
 */
public class UpgradeContainer extends OwnedContainer {
    private final UpgradeSpec spec;
    private final Runnable onChange;

    public UpgradeContainer(UpgradeSpec spec, BlockEntity owner, Runnable onChange) {
        super(spec.slots(), owner, s -> true);
        this.spec = spec;
        this.onChange = onChange;
    }

    public UpgradeContainer(UpgradeSpec spec, BlockEntity owner) {
        this(spec, owner, () -> {
        });
    }

    public UpgradeSpec spec() {
        return spec;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** Можно ли положить апгрейд в ячейку slot (учитывая то, что уже стоит в остальных). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(spec, stack, this, slot);
    }

    /** Общая проверка (её же использует клиентская копия меню). */
    public static boolean accepts(UpgradeSpec spec, ItemStack stack, net.minecraft.world.Container current, int slot) {
        if (!(stack.getItem() instanceof UpgradeItem up)) {
            return false;
        }
        int max = spec.max(up.type());
        if (max <= 0) {
            return false;
        }
        int have = 0;
        for (int i = 0; i < current.getContainerSize(); i++) {
            if (i != slot && current.getItem(i).getItem() instanceof UpgradeItem other && other.type() == up.type()) {
                have++;
            }
        }
        return have < max;
    }

    public int count(UpgradeType type) {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            if (getItem(i).getItem() instanceof UpgradeItem up && up.type() == type) {
                n++;
            }
        }
        return Math.min(n, spec.max(type));
    }

    public boolean has(UpgradeType type) {
        return count(type) > 0;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        onChange.run();
    }
}
