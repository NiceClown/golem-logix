package com.golemlogix.system;

import com.golemlogix.recipe.AnvilCrushingRecipe;
import com.golemlogix.recipe.SaturatorRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

/** What recipes show (since 1.21.2 recipes have no getResultItem / getItems: results come from their displays). */
public final class RecipeItems {
    private RecipeItems() {
    }

    public static ItemStack result(Recipe<?> recipe, Level level) {
        if (recipe instanceof SaturatorRecipe s) {
            return s.result();
        }
        if (recipe instanceof AnvilCrushingRecipe a) {
            return a.result();
        }
        if (recipe instanceof ShapelessRecipe shapeless && shapeless.result() != null) {
            return shapeless.result().create();
        }
        for (RecipeDisplay display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(SlotDisplayContext.fromLevel(level));
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** One stack of every item the ingredient takes. */
    public static ItemStack[] stacks(Ingredient ingredient) {
        return ingredient.items().map(ItemStack::new).toArray(ItemStack[]::new);
    }
}
