package com.golemlogix.system;

import net.minecraft.world.item.ItemStack;

/**
 * Item type key (the item + components, without the count). Works as a HashMap key.
 */
public final class ItemKey {
    private final ItemStack stack;
    private final int hash;

    private ItemKey(ItemStack stack) {
        this.stack = stack;
        this.hash = ItemStack.hashItemAndComponents(stack);
    }

    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.copyWithCount(1));
    }

    public ItemStack toStack(int count) {
        return stack.copyWithCount(count);
    }

    /** Reference stack with a count of 1. Do not modify! */
    public ItemStack stack() {
        return stack;
    }

    public boolean matches(ItemStack other) {
        return ItemStack.isSameItemSameComponents(stack, other);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItemKey k && hash == k.hash && ItemStack.isSameItemSameComponents(stack, k.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ItemKey[" + stack + "]";
    }
}
