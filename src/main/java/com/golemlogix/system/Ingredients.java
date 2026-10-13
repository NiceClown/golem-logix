package com.golemlogix.system;

import com.golemlogix.items.FluidGhostItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Pattern ingredients are items or fluids. A fluid is kept as a {@link FluidGhostItem} stack whose amount lives in a
 * component; for counting, its key is the fluid with amount 1 and its quantity is in mB.
 */
public final class Ingredients {
    private Ingredients() {
    }

    public static ItemKey key(ItemStack stack) {
        FluidStack f = FluidGhostItem.fluid(stack);
        return f.isEmpty() ? ItemKey.of(stack) : fluidKey(f);
    }

    /** Items: the stack count; fluids: mB. */
    public static int amount(ItemStack stack) {
        FluidStack f = FluidGhostItem.fluid(stack);
        return f.isEmpty() ? stack.getCount() : f.getAmount();
    }

    public static ItemKey fluidKey(FluidStack fluid) {
        return ItemKey.of(FluidGhostItem.of(fluid.copyWithAmount(1)));
    }

    public static boolean isFluid(ItemKey key) {
        return FluidGhostItem.is(key.stack());
    }

    /** The fluid of a fluid key with the given amount. */
    public static FluidStack fluid(ItemKey key, int amount) {
        return FluidGhostItem.fluid(key.stack()).copyWithAmount(amount);
    }
}
