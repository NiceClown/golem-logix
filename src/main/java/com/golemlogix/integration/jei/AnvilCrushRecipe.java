package com.golemlogix.integration.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One anvil ritual for JEI: what stands for the golem, its name, how battered it must be, and what it gives. */
public record AnvilCrushRecipe(
        ItemStack golemRepresentation,
        Component golemName,
        Component healthCondition,
        ItemStack output
) {}
