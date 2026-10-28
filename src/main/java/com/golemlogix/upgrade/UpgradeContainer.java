package com.golemlogix.upgrade;

import com.golemlogix.blockentity.OwnedContainer;
import com.golemlogix.items.UpgradeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A machine's upgrade slots: one upgrade in each, every slot for one kind (UpgradeSpec.typeAt; the window shows its ghost).
 * Upgrades from an older save are put into their own slots on loading; one with no slot left (the Annihilation Bus took speed
 * upgrades once) waits in strays until the machine drops it.
 */
public class UpgradeContainer extends OwnedContainer {
    private final UpgradeSpec spec;
    private final Runnable onChange;
    private final java.util.List<ItemStack> strays = new java.util.ArrayList<>();

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

    /** Whether an upgrade can go into slot slot (considering what is already in the others). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return accepts(spec, stack, this, slot);
    }

    /** The shared check (the client copy of the menu uses it too): the slot is for this kind of upgrade. */
    public static boolean accepts(UpgradeSpec spec, ItemStack stack, net.minecraft.world.Container current, int slot) {
        return stack.getItem() instanceof UpgradeItem up && spec.typeAt(slot) == up.type();
    }

    /** Loads every upgrade into a slot of its own kind, where it was if that slot is for it (saves from before slots had kinds). */
    @Override
    public void fromTag(net.minecraft.nbt.ListTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        clearContent();
        java.util.List<ItemStack> rest = new java.util.ArrayList<>();
        for (int i = 0; i < tag.size(); i++) {
            var entry = tag.getCompoundOrEmpty(i);
            ItemStack stack = com.golemlogix.system.NbtBridge.loadItem(registries, entry).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            int slot = entry.getIntOr("Slot", -1);
            if (slot >= 0 && slot < getContainerSize() && getItem(slot).isEmpty() && accepts(spec, stack, this, slot)) {
                setItem(slot, stack);
            } else {
                rest.add(stack);
            }
        }
        for (ItemStack s : rest) {
            int to = -1;
            for (int i = 0; i < getContainerSize() && to < 0; i++) {
                if (getItem(i).isEmpty() && accepts(spec, s, this, i)) {
                    to = i;
                }
            }
            if (to >= 0) {
                setItem(to, s);
            } else {
                strays.add(s);
            }
        }
    }

    /** Drops the upgrades that found no slot on loading, in front of the machine. */
    public void dropStrays(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (!strays.isEmpty()) {
            for (ItemStack s : strays) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, s);
            }
            strays.clear();
            setChanged();
        }
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
