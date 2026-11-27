package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.ForgeHammerItem;
import com.golemlogix.system.EndRodRitual;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * JEI recipe category documenting the 3-phase pre-End Cryo-Forging Ritual on an unassembled
 * Mana Saturator anvil (Diamond Block + 2x Eye of Ender + 2x Ghast Tear + Netherite Ingot + 4x Blue Ice -> End Rod).
 */
public class EndRodRitualCategory implements IRecipeCategory<EndRodRitualCategory.Recipe> {
    public record Recipe(ItemStack output) {
    }

    public static final RecipeType<Recipe> TYPE =
            RecipeType.create(GolemLogixMod.MOD_ID, "end_rod_ritual", Recipe.class);

    private static final int W = 198, H = 114;
    private static final int BROWN = 0xFF6A4A2A;
    private static final int SUB = 0xFF4F5D73;
    private static final int ACTIVE_BG = 0x1A4CC9F0;

    private static final int TOP_Y = 4;
    private static final int ROW_1_Y = 30;
    private static final int ROW_2_Y = 58;
    private static final int ROW_3_Y = 86;
    private static final int OUT_X = 174;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final Component title;

    public EndRodRitualCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(W, H);
        this.icon = guiHelper.createDrawableItemLike(Items.END_ROD);
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.golem_logix.category.end_rod_ritual");
    }

    public static List<Recipe> recipes() {
        return List.of(new Recipe(new ItemStack(Items.END_ROD)));
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 5, TOP_Y + 1)
                .addItemStack(new ItemStack(ModBlocks.MANA_SATURATOR.get()))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.station_tip").withStyle(ChatFormatting.AQUA)));
        builder.addSlot(RecipeIngredientRole.CATALYST, 35, TOP_Y + 1)
                .addItemStack(new ItemStack(ModItems.FORGE_HAMMER.get()))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.hammer_tip",
                                        EndRodRitual.TOTAL_HITS, EndRodRitual.TOTAL_HITS * ForgeHammerItem.STRIKE_COST)
                                .withStyle(ChatFormatting.LIGHT_PURPLE)));

        // Phase 1: Diamond Block + 4x Blue Ice
        builder.addSlot(RecipeIngredientRole.INPUT, 19, ROW_1_Y + 1)
                .addItemStack(new ItemStack(Items.DIAMOND_BLOCK))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.step1_tip").withStyle(ChatFormatting.AQUA)));
        builder.addSlot(RecipeIngredientRole.INPUT, 47, ROW_1_Y + 1)
                .addItemStack(new ItemStack(Items.BLUE_ICE, 4))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.ice_blue_tip").withStyle(ChatFormatting.BLUE)));

        // Phase 2: 2x Eye of Ender + 4x Packed Ice (degraded from Blue Ice)
        builder.addSlot(RecipeIngredientRole.INPUT, 19, ROW_2_Y + 1)
                .addItemStack(new ItemStack(Items.ENDER_EYE, EndRodRitual.EYES_REQUIRED))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.step2_tip").withStyle(ChatFormatting.GREEN)));
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 47, ROW_2_Y + 1)
                .addItemStack(new ItemStack(Items.PACKED_ICE, 4))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.ice_packed_tip").withStyle(ChatFormatting.BLUE)));

        // Phase 3: 2x Ghast Tear + 1x Netherite Ingot + 4x Ice (degraded from Packed Ice) -> End Rod
        builder.addSlot(RecipeIngredientRole.INPUT, 19, ROW_3_Y + 1)
                .addItemStack(new ItemStack(Items.GHAST_TEAR, EndRodRitual.TEARS_REQUIRED))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.step3_tip").withStyle(ChatFormatting.GOLD)));
        builder.addSlot(RecipeIngredientRole.INPUT, 39, ROW_3_Y + 1)
                .addItemStack(new ItemStack(Items.NETHERITE_INGOT, EndRodRitual.NETHERITE_REQUIRED))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.step3_tip").withStyle(ChatFormatting.GOLD)));
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 59, ROW_3_Y + 1)
                .addItemStack(new ItemStack(Items.ICE, 4))
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.ice_normal_tip").withStyle(ChatFormatting.BLUE)));

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, ROW_3_Y + 1)
                .addItemStack(recipe.output())
                .addRichTooltipCallback((view, tip) -> tip.add(
                        Component.translatable("jei.golem_logix.end_rod.output_tip").withStyle(ChatFormatting.GRAY)));
    }

    @Override
    public void draw(Recipe recipe, IRecipeSlotsView slotsView, GuiGraphics g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int activePhase = (int) ((System.currentTimeMillis() / 1600L) % 3);
        int highlightY = activePhase == 0 ? ROW_1_Y - 2 : (activePhase == 1 ? ROW_2_Y - 2 : ROW_3_Y - 2);
        g.fill(2, highlightY, W - 2, highlightY + 22, ACTIVE_BG);

        // Top header slots + total cost
        slot.draw(g, 4, TOP_Y);
        slot.draw(g, 34, TOP_Y);
        g.drawString(font, "+", 25, TOP_Y + 5, BROWN, false);
        g.drawString(font, Component.translatable("jei.golem_logix.end_rod.total",
                EndRodRitual.TOTAL_HITS, EndRodRitual.TOTAL_HITS * ForgeHammerItem.STRIKE_COST), 56, TOP_Y + 1, BROWN, false);
        g.drawString(font, Component.translatable("jei.golem_logix.end_rod.cancel_note"), 56, TOP_Y + 10, SUB, false);
        g.fill(4, 25, W - 4, 26, 0x336A4A2A);

        // Phase 1 row
        g.drawString(font, "1.", 5, ROW_1_Y + 5, BROWN, false);
        slot.draw(g, 18, ROW_1_Y);
        g.drawString(font, "+", 38, ROW_1_Y + 5, BROWN, false);
        slot.draw(g, 46, ROW_1_Y);
        arrow.draw(g, 72, ROW_1_Y + 1);
        g.drawString(font, Component.translatable("jei.golem_logix.end_rod.strikes", EndRodRitual.HITS_PHASE_1), 100, ROW_1_Y + 5, BROWN, false);

        // Phase 2 row
        g.drawString(font, "2.", 5, ROW_2_Y + 5, BROWN, false);
        slot.draw(g, 18, ROW_2_Y);
        g.drawString(font, "+", 38, ROW_2_Y + 5, BROWN, false);
        slot.draw(g, 46, ROW_2_Y);
        arrow.draw(g, 72, ROW_2_Y + 1);
        g.drawString(font, Component.translatable("jei.golem_logix.end_rod.strikes", EndRodRitual.HITS_PHASE_2), 100, ROW_2_Y + 5, BROWN, false);

        // Phase 3 row
        g.drawString(font, "3.", 5, ROW_3_Y + 5, BROWN, false);
        slot.draw(g, 18, ROW_3_Y);
        slot.draw(g, 38, ROW_3_Y);
        slot.draw(g, 58, ROW_3_Y);
        arrow.draw(g, 82, ROW_3_Y + 1);
        g.drawString(font, Component.translatable("jei.golem_logix.end_rod.strikes", EndRodRitual.HITS_PHASE_3), 109, ROW_3_Y + 5, BROWN, false);
        slot.draw(g, OUT_X, ROW_3_Y);
    }
}
