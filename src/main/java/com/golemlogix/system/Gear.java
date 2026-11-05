package com.golemlogix.system;

import com.golemlogix.init.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Equipment on a disk. What makes a network slow is not how many items it holds but how many different kinds it must go through, and
 * every worn, enchanted or otherwise individual thing is a kind of its own. So such a thing takes COST pieces of a disk. A thing counts
 * when it does not stack and differs from its plain self. A new sword is one kind however many there are, and things that only carry
 * a fluid, a potion or a book's enchantment come in few kinds: neither counts. Packs add more exceptions with the tag golem_logix:not_gear.
 */
public final class Gear {
    public static final int COST = 4000;

    private Gear() {
    }

    public static boolean is(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() > 1 || stack.getComponentsPatch().isEmpty() || stack.is(ModTags.NOT_GEAR)
                || stack.has(DataComponents.POTION_CONTENTS) || stack.has(DataComponents.STORED_ENCHANTMENTS)) {
            return false;
        }
        return net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack).isEmpty();
    }

    /** The pieces of a disk one of these takes. */
    public static int cost(ItemStack stack) {
        return is(stack) ? COST : 1;
    }
}
