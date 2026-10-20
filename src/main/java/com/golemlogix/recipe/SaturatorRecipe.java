package com.golemlogix.recipe;

import com.golemlogix.init.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.golemlogix.system.Recipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A Mana Saturator ritual, from data packs ("golem_logix:saturator"): the assembled Saturator turns one blank into the result for
 * Source; with "hand_result" the blank can also be forged by hand with the Forge Hammer on the unassembled anvil.
 * <pre>
 * {
 *   "type": "golem_logix:saturator",
 *   "ingredient": {"item": "minecraft:iron_block"},
 *   "result": {"id": "golem_logix:flattened_iron_core", "count": 2},
 *   "hand_result": {"id": "golem_logix:flattened_iron_core"},   optional: forging by hand
 *   "source": 1000,                                             optional, Source per ritual (default 1000)
 *   "ticks": 100,                                               optional, time without coolers (default 100 = 5 s)
 *   "secret": false                                             optional, hidden in JEI until the secrets are known
 * }
 * </pre>
 * The Ars Nouveau Source Gem recipes are not here: they are read from the Imbuement Chamber's own recipes (ArsImbuement).
 */
public record SaturatorRecipe(Ingredient ingredient, ItemStack result, ItemStack handResult, int source, int ticks, boolean secret)
        implements Recipe<SingleRecipeInput> {
    public static final int DEFAULT_SOURCE = 1000, DEFAULT_TICKS = 100;

    public static final MapCodec<SaturatorRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(SaturatorRecipe::ingredient),
            ItemStack.CODEC.fieldOf("result").forGetter(SaturatorRecipe::result),
            ItemStack.CODEC.optionalFieldOf("hand_result").forGetter(r -> r.handResult.isEmpty() ? Optional.empty() : Optional.of(r.handResult)),
            Codec.intRange(0, 1_000_000).optionalFieldOf("source", DEFAULT_SOURCE).forGetter(SaturatorRecipe::source),
            Codec.intRange(1, 72_000).optionalFieldOf("ticks", DEFAULT_TICKS).forGetter(SaturatorRecipe::ticks),
            Codec.BOOL.optionalFieldOf("secret", false).forGetter(SaturatorRecipe::secret)
    ).apply(i, (ing, res, hand, src, t, sec) -> new SaturatorRecipe(ing, res, hand.orElse(ItemStack.EMPTY), src, t, sec)));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaturatorRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, SaturatorRecipe::ingredient,
            ItemStack.STREAM_CODEC, SaturatorRecipe::result,
            ItemStack.OPTIONAL_STREAM_CODEC, SaturatorRecipe::handResult,
            ByteBufCodecs.VAR_INT, SaturatorRecipe::source,
            ByteBufCodecs.VAR_INT, SaturatorRecipe::ticks,
            ByteBufCodecs.BOOL, SaturatorRecipe::secret,
            SaturatorRecipe::new);

    /** The ritual for this blank, or null. Both sides have the recipes (Recipes sends them to clients). */
    @Nullable
    public static SaturatorRecipe find(@Nullable Level level, ItemStack input) {
        if (level == null || input.isEmpty()) {
            return null;
        }
        return Recipes.find(level, ModRecipes.SATURATOR_TYPE.get(), new SingleRecipeInput(input)).map(RecipeHolder::value).orElse(null);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return result.copy();
    }

    /** Not a recipe book recipe. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<SaturatorRecipe> getSerializer() {
        return ModRecipes.SATURATOR.get();
    }

    @Override
    public RecipeType<SaturatorRecipe> getType() {
        return ModRecipes.SATURATOR_TYPE.get();
    }
}
