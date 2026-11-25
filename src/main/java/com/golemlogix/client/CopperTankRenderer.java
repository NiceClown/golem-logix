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
    /** The inside of the tank (pixels): within the glass chamber (2..14), between the base (top at 3.5) and the lid (underside at 12.5). */
    private static final float IN0 = 2.2f, IN1 = 13.8f, BOTTOM = 3.55f, TOP = 12.45f;

    public CopperTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final net.minecraft.resources.Identifier WHITE =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "textures/entity/bay_white.png");
    /** The gauge's face (pixels): centered on BOTH north (z = 1.35) and south (z = 14.65) glass walls so the dial is always readable. */
    private static final float GAUGE_X = 8.0f, GAUGE_Y = 8.0f, GAUGE_Z_N = 1.24f, GAUGE_Z_S = 14.76f;

    /** Needles on both north and south dials: from empty (lower left) around top to full (lower right). */
    private static void needle(PoseStack.Pose p, MultiBufferSource buffers, float fill, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
        double a = Math.toRadians(150 + 240 * fill);
        float len = 1.15f, w = 0.15f;
        // North face: texture u -> -x, v -> -y
        float dxN = (float) -Math.cos(a), dyN = (float) -Math.sin(a);
        float nxN = -dyN * w, nyN = dxN * w;
        float[][] cN = {{GAUGE_X - nxN, GAUGE_Y - nyN}, {GAUGE_X + nxN, GAUGE_Y + nyN},
                {GAUGE_X + dxN * len + nxN, GAUGE_Y + dyN * len + nyN}, {GAUGE_X + dxN * len - nxN, GAUGE_Y + dyN * len - nyN}};
        for (float[] v : cN) {
            vc.addVertex(p, v[0] / 16f, v[1] / 16f, GAUGE_Z_N / 16f).setColor(0xFFC8281E).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
        }
        // South face: mirrored u -> +x, v -> -y
        float dxS = (float) Math.cos(a), dyS = (float) -Math.sin(a);
        float nxS = -dyS * w, nyS = dxS * w;
        float[][] cS = {{GAUGE_X + dxS * len - nxS, GAUGE_Y + dyS * len - nyS}, {GAUGE_X + dxS * len + nxS, GAUGE_Y + dyS * len + nyS},
                {GAUGE_X + nxS, GAUGE_Y + nyS}, {GAUGE_X - nxS, GAUGE_Y - nyS}};
        for (float[] v : cS) {
            vc.addVertex(p, v[0] / 16f, v[1] / 16f, GAUGE_Z_S / 16f).setColor(0xFFC8281E).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
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
