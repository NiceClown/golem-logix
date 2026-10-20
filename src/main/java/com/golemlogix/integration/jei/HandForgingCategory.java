package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.ForgeHammerItem;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Forging by hand on the Mana Saturator's anvil (the machine not assembled): a blank, "+", four Forge Hammer strikes (they light up one
 * after another), "=", one plate. Under them the strikes and the Source they take from the hammer's own charge.
 */
public class HandForgingCategory implements IRecipeCategory<HandForgingCategory.Recipe> {
    public static final IRecipeType<Recipe> TYPE = IRecipeType.create(GolemLogixMod.MOD_ID, "hand_forging", Recipe.class);

    /** One blank (or several that make the same plate) and the plate it becomes. */
    public record Recipe(List<ItemStack> inputs, ItemStack output) {
    }

    private static final int W = 150, H = 40;
    private static final int IN_X = 4, OUT_X = 128, SLOT_Y = 6, HAMMER_X = 36;
    private static final int TEXT = 0xFF6A4A2A, LIT = 0xFF38D9D6;

    private final IDrawable icon;
    private final IDrawable slot;
    private final Component title;

    public HandForgingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.FORGE_HAMMER.get());
        this.slot = guiHelper.getSlotDrawable();
        this.title = Component.translatable("jei.golem_logix.category.hand_forging");
    }

    @Override
    public IRecipeType<Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return W;
    }

    @Override
    public int getHeight() {
        return H;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, IN_X + 1, SLOT_Y + 1).addItemStacks(recipe.inputs());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, SLOT_Y + 1).addItemStack(recipe.output());
        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION).addItemStack(new ItemStack(ModItems.FORGE_HAMMER.get()));
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slotsView, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(g, IN_X, SLOT_Y);
        slot.draw(g, OUT_X, SLOT_Y);
        g.text(font, "+", IN_X + 24, SLOT_Y + 5, TEXT, false);
        // four strikes: each hammer lights up in turn, then the plate is done and it starts again
        int hits = ManaSaturatorBlockEntity.MANUAL_HITS;
        long step = System.currentTimeMillis() / 450 % (hits + 2);
        ItemStack hammer = new ItemStack(ModItems.FORGE_HAMMER.get());
        for (int i = 0; i < hits; i++) {
            int x = HAMMER_X + i * 20;
            if (i < step) {
                g.fill(x - 1, SLOT_Y, x + 17, SLOT_Y + 18, 0x5538D9D6);
            }
            g.item(hammer, x, SLOT_Y + 1);
        }
        int eqX = HAMMER_X + hits * 20 + 2;
        g.text(font, "=", eqX, SLOT_Y + 5, step > hits ? LIT : TEXT, false);
        Component cost = Component.translatable("jei.golem_logix.hand_forging.cost", hits, hits * ForgeHammerItem.STRIKE_COST);
        g.text(font, cost, (W - font.width(cost)) / 2, SLOT_Y + 24, TEXT, false);
    }
}
