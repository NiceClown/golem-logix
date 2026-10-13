package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Anvil ritual: on the left a "scene" (an anvil falls on a golem), on the right the golem's name and the needed health, below an arrow to the plate.
 */
public class AnvilCrushCategory implements IRecipeCategory<AnvilCrushRecipe> {
    public static final RecipeType<AnvilCrushRecipe> TYPE =
            RecipeType.create(GolemLogixMod.MOD_ID, "anvil_crushing", AnvilCrushRecipe.class);

    private static final int W = 150, H = 56;
    private static final int SCENE_X = 10, ANVIL_Y = 2, GOLEM_Y = 36, OUT_X = 124, TEXT_X = 36;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final Component title;

    public AnvilCrushCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(W, H);
        this.icon = guiHelper.createDrawableItemLike(Blocks.ANVIL);
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.golem_logix.category.anvil_crushing");
    }

    @Override
    public RecipeType<AnvilCrushRecipe> getRecipeType() {
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

    /** For older JEI versions where the size is taken only from the background. */
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
    public void setRecipe(IRecipeLayoutBuilder builder, AnvilCrushRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, SCENE_X + 1, ANVIL_Y + 1).addItemStack(new ItemStack(Blocks.ANVIL));
        builder.addSlot(RecipeIngredientRole.INPUT, SCENE_X + 1, GOLEM_Y + 1).addItemStack(recipe.golemRepresentation());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, GOLEM_Y + 1).addItemStack(recipe.output());
    }

    @Override
    public void draw(AnvilCrushRecipe recipe, IRecipeSlotsView slotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(g, SCENE_X, ANVIL_Y);
        slot.draw(g, SCENE_X, GOLEM_Y);
        slot.draw(g, OUT_X, GOLEM_Y);
        // the anvil falls: a dotted line between the slots
        for (int y = ANVIL_Y + 20; y < GOLEM_Y - 1; y += 3) {
            g.fill(SCENE_X + 8, y, SCENE_X + 10, y + 2, 0xFF7A7A7A);
        }
        arrow.draw(g, 90, GOLEM_Y + 1);
        g.drawString(font, Component.translatable(recipe.golemName()), TEXT_X, 4, 0xFF8A5A1E, false);
        g.drawString(font, "❤", TEXT_X, 17, 0xFFD02828, false);
        g.drawString(font, Component.translatable(recipe.healthCondition()), TEXT_X + 10, 17, 0xFFB0581A, false);
    }
}
