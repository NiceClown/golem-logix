package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.recipe.AnvilCrushingRecipe;
import com.golemlogix.recipe.ChronometerUpgradeRecipe;
import com.golemlogix.recipe.SaturatorRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, GolemLogixMod.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, GolemLogixMod.MOD_ID);

    public static final Supplier<RecipeSerializer<ChronometerUpgradeRecipe>> CHRONOMETER_UPGRADE =
            SERIALIZERS.register("chronometer_upgrade", () -> ChronometerUpgradeRecipe.SERIALIZER);

    // the rituals, from data packs (modpacks can change and add them)
    public static final Supplier<RecipeType<SaturatorRecipe>> SATURATOR_TYPE = TYPES.register("saturator", () -> type("saturator"));
    public static final Supplier<RecipeSerializer<SaturatorRecipe>> SATURATOR = SERIALIZERS.register("saturator", () -> new RecipeSerializer<>(SaturatorRecipe.CODEC, SaturatorRecipe.STREAM_CODEC));
    public static final Supplier<RecipeType<AnvilCrushingRecipe>> ANVIL_CRUSHING_TYPE = TYPES.register("anvil_crushing", () -> type("anvil_crushing"));
    public static final Supplier<RecipeSerializer<AnvilCrushingRecipe>> ANVIL_CRUSHING =
            SERIALIZERS.register("anvil_crushing", () -> new RecipeSerializer<>(AnvilCrushingRecipe.CODEC, AnvilCrushingRecipe.STREAM_CODEC));

    private static <T extends Recipe<?>> RecipeType<T> type(String name) {
        return RecipeType.simple(net.minecraft.resources.Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, name));
    }
}
