package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import com.golemlogix.items.FluidGhostItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fluids in GUI slots: the still texture of the fluid, tinted, and fluid ghosts drawn as their fluid with an mB label. */
public final class FluidIcons {
    private FluidIcons() {
    }

    public static void draw(GuiGraphicsExtractor g, FluidStack fluid, int x, int y) {
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        Identifier tex = ext.getStillTexture(fluid);
        if (tex == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(tex);
        int tint = ext.getTintColor(fluid);
        float a = ((tint >> 24) & 0xFF) / 255f;
        float r = ((tint >> 16) & 0xFF) / 255f;
        float gr = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        g.blit(x, y, 0, 16, 16, sprite, r, gr, b, a == 0 ? 1f : a);
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
