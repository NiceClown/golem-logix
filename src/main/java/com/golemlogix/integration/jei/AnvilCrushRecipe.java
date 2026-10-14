package com.golemlogix.integration.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One anvil ritual for JEI: what stands for the golem (looked up by JEI), its name, the condition as text, what it gives; the
 * golem's entity type for the animated scene and its health window in HP (NaN for the Wither: a time, not health).
 */
public record AnvilCrushRecipe(
        ItemStack golemRepresentation,
        Component golemName,
        Component healthCondition,
        ItemStack output,
        @Nullable EntityType<?> entity,
        float minHealth,
        float maxHealth
) {}
