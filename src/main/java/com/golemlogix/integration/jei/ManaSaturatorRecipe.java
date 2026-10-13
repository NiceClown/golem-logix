package com.golemlogix.integration.jei;

import net.minecraft.world.item.ItemStack;

public record ManaSaturatorRecipe(
        ItemStack inputBlock,
        int sourceCost,
        ItemStack output
) {}
