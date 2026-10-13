package com.golemlogix.integration.jei;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** One Mana Saturator ritual for JEI: the blanks it accepts, its Source, what it makes and how long it takes without coolers. */
public record ManaSaturatorRecipe(
        List<ItemStack> inputs,
        int sourceCost,
        ItemStack output,
        int ticks
) {
    /** A ritual of the mod itself: one blank, five seconds. */
    public ManaSaturatorRecipe(ItemStack input, int sourceCost, ItemStack output) {
        this(List.of(input), sourceCost, output, 100);
    }
}
