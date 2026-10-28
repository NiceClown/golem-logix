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
 * fluid lighter than air (a gas) hangs from the lid instead; a glowing one (lava) is drawn at full brightness. The needle of the
 * gauge on the front post shows how full it is: from the dial's lower left (empty) round to its lower right (full).
 */
public class CopperTankRenderer extends com.golemlogix.client.render.LegacyBer<CopperTankBlockEntity> {
    /** The inside of the tank (pixels): within the glass, between the base (top at 4) and the lid (underside at 13). */
    private static final float IN0 = 1.6f, IN1 = 14.4f, BOTTOM = 4.05f, TOP = 12.95f;

    public CopperTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final net.minecraft.resources.Identifier WHITE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "textures/entity/bay_white.png");
    /** The gauge's face (pixels): centre and the plane just in front of it (the dial is the face at z -1.2 of the model). */
    private static final float GAUGE_X = 14.25f, GAUGE_Y = 8.25f, GAUGE_Z = -1.26f;

    /** The needle: a thin red bar from the centre, at 150 degrees of the dial (lower left) when empty, 390 (lower right) when full. */
    private static void needle(PoseStack.Pose p, MultiBufferSource buffers, float fill, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
        double a = Math.toRadians(150 + 240 * fill);
        // the dial's texture runs right and down; on the north face that is towards -x and -y
        float dx = (float) -Math.cos(a), dy = (float) -Math.sin(a), len = 1.05f, w = 0.14f;
        float nx = -dy * w, ny = dx * w;
        float[][] c = {{GAUGE_X - nx, GAUGE_Y - ny}, {GAUGE_X + nx, GAUGE_Y + ny},
                {GAUGE_X + dx * len + nx, GAUGE_Y + dy * len + ny}, {GAUGE_X + dx * len - nx, GAUGE_Y + dy * len - ny}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 4; i++) {
                float[] v = c[side == 0 ? i : 3 - i];
                vc.addVertex(p, v[0] / 16f, v[1] / 16f, GAUGE_Z / 16f).setColor(0xFFC8281E).setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
            }
        }
    }

    @Override
    public void render(CopperTankBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        FluidStack fluid = be.fluid();
        float fill = fluid.isEmpty() ? 0f : Math.min(1f, fluid.getAmount() / (float) be.capacity());
        needle(pose.last(), buffers, fill, light);
        if (fluid.isEmpty()) {
            return;
        }
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
