package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModFluids;
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
import net.minecraft.world.level.material.Fluids;

import java.util.List;
import java.util.Locale;

/** Cryo-Mixer: water and Liquid Cryo-Source in the two flasks, an arrow, a snow block (fast) or a block of ice (slow). */
public class CryoMixerCategory implements IRecipeCategory<CryoMixerBlockEntity.Mode> {
    public static final IRecipeType<CryoMixerBlockEntity.Mode> TYPE =
            IRecipeType.create(GolemLogixMod.MOD_ID, "cryo_mixer", CryoMixerBlockEntity.Mode.class);

    private static final int W = 150, H = 40;
    private static final int WATER_X = 8, CRYO_X = 36, OUT_X = 112, SLOT_Y = 4;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final Component title;

    public CryoMixerCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.CRYO_MIXER.get());
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.golem_logix.category.cryo_mixer");
    }

    /** Both modes, as JEI recipes. */
    public static List<CryoMixerBlockEntity.Mode> recipes() {
        return List.of(CryoMixerBlockEntity.Mode.values());
    }

    @Override
    public IRecipeType<CryoMixerBlockEntity.Mode> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, CryoMixerBlockEntity.Mode mode, IFocusGroup focuses) {
        // each flask drawn full: the amount is what one block takes
        builder.addSlot(RecipeIngredientRole.INPUT, WATER_X + 1, SLOT_Y + 1).setFluidRenderer(mode.water(), false, 16, 16)
                .addFluidStack(Fluids.WATER, mode.water());
        builder.addSlot(RecipeIngredientRole.INPUT, CRYO_X + 1, SLOT_Y + 1).setFluidRenderer(mode.cryo(), false, 16, 16)
                .addFluidStack(ModFluids.CRYO_SOURCE.get(), mode.cryo());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, SLOT_Y + 1).add(mode.output());
    }

    @Override
    public void draw(CryoMixerBlockEntity.Mode mode, IRecipeSlotsView slotsView, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(g, WATER_X, SLOT_Y);
        slot.draw(g, CRYO_X, SLOT_Y);
        slot.draw(g, OUT_X, SLOT_Y);
        g.text(font, "+", WATER_X + 21, SLOT_Y + 5, 0xFF6A4A2A, false);
        arrow.draw(g, (CRYO_X + 18 + OUT_X) / 2 - 12, SLOT_Y + 1);
        Component line = Component.translatable("jei.golem_logix.mixer.time",
                String.format(Locale.ROOT, "%.1f", mode.ticks() / 20f),
                Component.translatable("gui.golem_logix.mixer.mode." + mode.name().toLowerCase(Locale.ROOT)));
        g.text(font, line, (W - font.width(line)) / 2, SLOT_Y + 25, 0xFF2A6E8A, false);
    }
}
