package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Насыщатель Маны: блок на наковальне, стрелка, две пластины; под стрелкой — цена в Source и время. */
public class ManaSaturatorCategory implements IRecipeCategory<ManaSaturatorRecipe> {
    public static final RecipeType<ManaSaturatorRecipe> TYPE =
            RecipeType.create(GolemLogixMod.MOD_ID, "mana_saturator", ManaSaturatorRecipe.class);

    private static final int W = 150, H = 40;
    private static final int IN_X = 26, OUT_X = 106, SLOT_Y = 4;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final Component title;

    public ManaSaturatorCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(W, H);
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.MANA_SATURATOR.get());
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.golem_logix.category.mana_saturator");
    }

    @Override
    public RecipeType<ManaSaturatorRecipe> getRecipeType() {
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

    /** Для старых версий JEI, где размер берётся только из фона. */
    @SuppressWarnings("removal")
    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ManaSaturatorRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, IN_X + 1, SLOT_Y + 1).addItemStack(recipe.inputBlock());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, SLOT_Y + 1).addItemStack(recipe.output());
    }

    @Override
    public void draw(ManaSaturatorRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(g, IN_X, SLOT_Y);
        slot.draw(g, OUT_X, SLOT_Y);
        arrow.draw(g, (IN_X + OUT_X + 18) / 2 - 12, SLOT_Y + 1);
        Component cost = Component.translatable("jei.golem_logix.source_cost", recipe.sourceCost());
        g.drawString(font, cost, (W - font.width(cost)) / 2, SLOT_Y + 24, 0xFF5A3C9A, false);
    }
}
