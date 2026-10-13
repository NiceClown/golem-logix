package com.golemlogix.integration.jei;

import net.minecraft.world.item.ItemStack;

public record AnvilCrushRecipe(
        ItemStack golemRepresentation,
        String golemName,
        String healthCondition,
        ItemStack output
) {}
