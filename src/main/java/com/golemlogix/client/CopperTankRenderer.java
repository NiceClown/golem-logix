package com.golemlogix.client;

import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Copper Tank: the fluid behind the glass, as high as the tank is full (the copper frame and the glass are the baked model). A
 * fluid lighter than air (a gas) hangs from the lid instead; a glowing one (lava) is drawn at full brightness.
 */
public class CopperTankRenderer extends com.golemlogix.client.render.LegacyBer<CopperTankBlockEntity> {
    /** The inside of the tank (pixels): within the posts and the glass, between the base and the lid. */
    private static final float IN0 = 2.05f, IN1 = 13.95f, BOTTOM = 2.05f, TOP = 13.95f;

    public CopperTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CopperTankBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        FluidStack fluid = be.fluid();
        if (fluid.isEmpty()) {
            return;
        }
        float fill = Math.min(1f, fluid.getAmount() / (float) be.capacity());
        float height = Math.max(0.3f, (TOP - BOTTOM) * fill);
        boolean gas = fluid.getFluidType().isLighterThanAir();
        float y0 = gas ? TOP - height : BOTTOM, y1 = gas ? TOP : BOTTOM + height;
        int tint = FluidIcons.tint(fluid);
        if ((tint >>> 24) == 0) {
            tint |= 0xFF000000;
        }
        int lit = fluid.getFluidType().getLightLevel(fluid) > 0 ? 0xF000F0 : light;
        TextureAtlasSprite s = FluidIcons.still(fluid);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        // the four sides, the top and (seen through the base's gap only when lifted) the bottom
        quad(vc, p, s, tint, lit, new float[][]{{IN1, y0, IN0}, {IN0, y0, IN0}, {IN0, y1, IN0}, {IN1, y1, IN0}}, 0, 0, -1);
        quad(vc, p, s, tint, lit, new float[][]{{IN0, y0, IN1}, {IN1, y0, IN1}, {IN1, y1, IN1}, {IN0, y1, IN1}}, 0, 0, 1);
        quad(vc, p, s, tint, lit, new float[][]{{IN0, y0, IN0}, {IN0, y0, IN1}, {IN0, y1, IN1}, {IN0, y1, IN0}}, -1, 0, 0);
        quad(vc, p, s, tint, lit, new float[][]{{IN1, y0, IN1}, {IN1, y0, IN0}, {IN1, y1, IN0}, {IN1, y1, IN1}}, 1, 0, 0);
        quad(vc, p, s, tint, lit, new float[][]{{IN0, y1, IN1}, {IN1, y1, IN1}, {IN1, y1, IN0}, {IN0, y1, IN0}}, 0, 1, 0);
        quad(vc, p, s, tint, lit, new float[][]{{IN0, y0, IN0}, {IN1, y0, IN0}, {IN1, y0, IN1}, {IN0, y0, IN1}}, 0, -1, 0);
    }

    /**
     * One face (corners in pixels, counter-clockwise seen from outside, starting bottom left), the sprite laid on it at one pixel a
     * texel so a low level shows a strip of the texture rather than all of it squeezed.
     */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int color, int light, float[][] c,
                             float nx, float ny, float nz) {
        float w = (float) Math.hypot(c[1][0] - c[0][0], c[1][2] - c[0][2]);
        float h = c[2][1] - c[1][1] + (float) Math.hypot(c[2][0] - c[1][0], c[2][2] - c[1][2]);
        float[][] uv = {{0, h}, {w, h}, {w, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) {
            vc.addVertex(p, c[i][0] / 16f, c[i][1] / 16f, c[i][2] / 16f).setColor(color)
                    .setUv(s.getU(uv[i][0] / 16f), s.getV(uv[i][1] / 16f)).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(p, nx, ny, nz);
        }
    }
}
