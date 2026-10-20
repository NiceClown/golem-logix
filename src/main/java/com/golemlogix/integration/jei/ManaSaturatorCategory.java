package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mana Saturator: on the left a small projection of the whole multiblock (the blocks it is built of, stacked like the machine),
 * on the right a block on the anvil, an arrow, two plates; under them the Source cost and the time.
 */
public class ManaSaturatorCategory implements IRecipeCategory<ManaSaturatorRecipe> {
    public static final IRecipeType<ManaSaturatorRecipe> TYPE =
            IRecipeType.create(GolemLogixMod.MOD_ID, "mana_saturator", ManaSaturatorRecipe.class);

    private static final int W = 150, H = 54;
    private static final int IN_X = 58, OUT_X = 126, SLOT_Y = 10;
    /** The projection's box on the left; the blocks' icons are scaled to fill it. */
    private static final int PROJ_X = 1, PROJ_Y = 1, PROJ_W = 52, PROJ_H = 52;
    private float projectionScale = 1f;
    @org.jetbrains.annotations.Nullable
    private List<Voxel> projection;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;
    private final Component title;

    public ManaSaturatorCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.MANA_SATURATOR.get());
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
        this.title = Component.translatable("jei.golem_logix.category.mana_saturator");
    }

    @Override
    public IRecipeType<ManaSaturatorRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, ManaSaturatorRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, IN_X + 1, SLOT_Y + 1).addItemStacks(recipe.inputs());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 1, SLOT_Y + 1).addItemStack(recipe.output());
    }

    @Override
    public void draw(ManaSaturatorRecipe recipe, IRecipeSlotsView slotsView, GuiGraphicsExtractor g, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        slot.draw(g, IN_X, SLOT_Y);
        slot.draw(g, OUT_X, SLOT_Y);
        arrow.draw(g, (IN_X + OUT_X + 18) / 2 - 12, SLOT_Y + 1);
        // seconds without coolers: 5 for the mod's own rituals, half of the Imbuement Chamber's time for Source Gems
        String seconds = recipe.ticks() % 20 == 0 ? String.valueOf(recipe.ticks() / 20)
                : String.format(java.util.Locale.ROOT, "%.1f", recipe.ticks() / 20f);
        Component cost = Component.translatable("jei.golem_logix.source_cost", recipe.sourceCost(), seconds);
        int right = PROJ_X + PROJ_W + 2;
        g.text(font, cost, right + (W - right - font.width(cost)) / 2, SLOT_Y + 26, 0xFF5A3C9A, false);
        for (Voxel v : projection()) {
            g.pose().pushMatrix();
            g.pose().translate(v.x(), v.y());
            g.pose().scale(projectionScale, projectionScale);
            g.item(v.stack(), 0, 0);
            g.pose().popMatrix();
        }
    }

    /** One block of the projection: its icon and where it goes. */
    private record Voxel(ItemStack stack, float x, float y) {
    }

    /**
     * The machine's blueprint (front to the north) as block icons in the GUI's own isometric view: east goes up-left, south up-right,
     * the far blocks are drawn first. Made once (the items need their components, which they get only after loading).
     */
    private List<Voxel> projection() {
        if (projection == null) {
            List<com.golemlogix.system.AtlasBlueprint.Cell> cells = new ArrayList<>(
                    com.golemlogix.system.SaturatorBlueprint.cells(net.minecraft.core.Direction.NORTH));
            cells.sort(Comparator.comparingInt((com.golemlogix.system.AtlasBlueprint.Cell c) -> -(c.dx() + c.dz()))
                    .thenComparingInt(com.golemlogix.system.AtlasBlueprint.Cell::dy));
            // a block icon is a cube with 10 px edges: its neighbours sit 7.07 px aside and 3.54 px up, a layer 8.66 px higher
            float half = 7.07f, quarter = 3.54f, rise = 8.66f, icon = 16;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float[][] at = new float[cells.size()][];
            for (int i = 0; i < cells.size(); i++) {
                var c = cells.get(i);
                float x = (c.dz() - c.dx()) * half, y = -(c.dx() + c.dz()) * quarter - c.dy() * rise;
                at[i] = new float[]{x, y};
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + icon);
                maxY = Math.max(maxY, y + icon);
            }
            float k = Math.min(1.25f, Math.min(PROJ_W / (maxX - minX), PROJ_H / (maxY - minY)));
            projectionScale = k;
            float ox = PROJ_X + (PROJ_W - (maxX - minX) * k) / 2 - minX * k, oy = PROJ_Y + (PROJ_H - (maxY - minY) * k) / 2 - minY * k;
            List<Voxel> out = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) {
                out.add(new Voxel(new ItemStack(cells.get(i).state().getBlock()), ox + at[i][0] * k, oy + at[i][1] * k));
            }
            projection = out;
        }
        return projection;
    }
}
