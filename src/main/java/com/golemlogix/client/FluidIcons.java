package com.golemlogix.client;

import com.golemlogix.items.FluidGhostItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fluids in GUI slots: the still texture of the fluid, tinted, and fluid ghosts drawn as their fluid with an mB label. */
public final class FluidIcons {
    private FluidIcons() {
    }

    public static void draw(GuiGraphicsExtractor g, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) {
            return;
        }
        int tint = tint(fluid);
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, still(fluid), x, y, 16, 16, (tint >>> 24) == 0 ? tint | 0xFF000000 : tint);
    }

    /** The fluid's still texture (from its fluid model, since 26.1). */
    public static TextureAtlasSprite still(FluidStack fluid) {
        return model(fluid).stillMaterial().sprite();
    }

    /** The fluid's colour as a stack (ARGB, white if it has no tint). */
    public static int tint(FluidStack fluid) {
        var source = model(fluid).fluidTintSource();
        return source == null ? 0xFFFFFFFF : source.colorAsStack(fluid);
    }

    private static net.minecraft.client.renderer.block.FluidModel model(FluidStack fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
    }

    /** "250" below a bucket, "1.5B" from a bucket up. */
    public static String label(int mb) {
        return mb < 1000 ? Integer.toString(mb) : GolemGui.formatBuckets(mb) + "B";
    }

    /** Draws a fluid ghost as its fluid (and its mB if label); false if the stack is not a fluid ghost. */
    public static boolean drawGhost(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, boolean label) {
        FluidStack fluid = FluidGhostItem.fluid(stack);
        if (fluid.isEmpty()) {
            return false;
        }
        draw(g, fluid, x, y);
        if (label) {
            String text = label(fluid.getAmount());
            g.pose().pushMatrix();
            g.pose().translate(0, 0);
            g.pose().scale(0.5f, 0.5f);
            g.text(font, text, (x + 16) * 2 - font.width(text) - 1, (y + 16) * 2 - 8, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
        return true;
    }
}
