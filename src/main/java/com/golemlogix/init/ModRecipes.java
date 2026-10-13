package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.recipe.ChronometerUpgradeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, GolemLogixMod.MOD_ID);

    public static final Supplier<RecipeSerializer<ChronometerUpgradeRecipe>> CHRONOMETER_UPGRADE =
            SERIALIZERS.register("chronometer_upgrade", () -> new SimpleCraftingRecipeSerializer<>(ChronometerUpgradeRecipe::new));
}
