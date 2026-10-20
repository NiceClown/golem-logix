package com.golemlogix.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.EternalFrostResonatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * The snow vortex of the Eternal Frost Resonator: three glowing teal ribbons spiral up from the rune pad at the
 * bottom of the glass chamber, narrow in the exhaust pipe and spread above the roof; a flickering pillar in the middle. While a network
 * is nearby the vortex spins faster and brighter. The ribbons glow by themselves (unlit) and do not hide the chamber glass.
 */
public class EternalFrostResonatorRenderer implements BlockEntityRenderer<EternalFrostResonatorBlockEntity> {
    private static final Identifier VORTEX = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID,
            "textures/entity/resonator_vortex.png");
    private static final int SEGMENTS = 28;
    private static final float Y0 = 9.3f, Y1 = 24f;

    public EternalFrostResonatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(EternalFrostResonatorBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 0.6, 0);
    }

    /** Spiral radius (pixels) at height fraction h: wide in the chamber, narrow in the pipe, spreading above it. */
    private static float radius(float h) {
        float yy = Y0 + h * (Y1 - Y0);
        if (yy < 15f) {
            float k = (yy - Y0) / (15f - Y0);
            return 1.1f + 2.7f * Mth.sin(k * Mth.PI * 0.85f) + 0.4f * k;
        }
        if (yy < 19.7f) {
            return 1.55f;
        }
        return 1.55f + (yy - 19.7f) / (Y1 - 19.7f) * 2.2f;
    }

    /** Opacity at a height fraction: fades at the very bottom and at the top above the pipe. */
    private static float fade(float h) {
        float yy = Y0 + h * (Y1 - Y0);
        float bottom = Mth.clamp((yy - Y0) / 0.8f, 0f, 1f);
        float top = yy < 19.7f ? 1f : Mth.clamp(1f - (yy - 19.7f) / (Y1 - 19.7f), 0f, 1f);
        return bottom * top;
    }

    @Override
    public void render(EternalFrostResonatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        float speed = be.clientLinked ? 1f : 0.4f;
        float bright = be.clientLinked ? 1f : 0.7f;
        VertexConsumer vc = buffers.getBuffer(RenderTypes.eyes(VORTEX));
        PoseStack.Pose p = pose.last();
        float scroll = time * 0.02f * (0.5f + speed);
        for (int k = 0; k < 3; k++) {
            float phase = k * Mth.TWO_PI / 3f + time * 0.09f * speed;
            for (int i = 0; i < SEGMENTS; i++) {
                float h0 = (float) i / SEGMENTS, h1 = (float) (i + 1) / SEGMENTS;
                float a0 = phase + h0 * 4.5f * Mth.PI, a1 = phase + h1 * 4.5f * Mth.PI;
                float r0 = radius(h0), r1 = radius(h1);
                float y0 = Y0 + h0 * (Y1 - Y0), y1 = Y0 + h1 * (Y1 - Y0);
                float x0 = 8f + Mth.cos(a0) * r0, z0 = 8f + Mth.sin(a0) * r0, x1 = 8f + Mth.cos(a1) * r1, z1 = 8f + Mth.sin(a1) * r1;
                float w = 1.7f;
                int c0 = color(fade(h0) * bright), c1 = color(fade(h1) * bright);
                float v0 = h0 * 2f + scroll, v1 = h1 * 2f + scroll;
                // ribbon: a double-sided quad from a spiral point to the same point w higher
                quad(vc, p, x0, y0, z0, x1, y1, z1, w, v0, v1, c0, c1);
                quad(vc, p, x1, y1, z1, x0, y0, z0, w, v1, v0, c1, c0);
            }
        }
        // flickering pillar in the middle: two crossed strips
        float pulse = (0.55f + 0.25f * Mth.sin(time * 0.2f)) * bright;
        for (int k = 0; k < 2; k++) {
            float dx = k == 0 ? 1.1f : 0f, dz = k == 0 ? 0f : 1.1f;
            column(vc, p, 8f - dx, 8f - dz, 8f + dx, 8f + dz, Y0, 21f, scroll * 1.5f, color(pulse));
            column(vc, p, 8f + dx, 8f + dz, 8f - dx, 8f - dz, Y0, 21f, scroll * 1.5f, color(pulse));
        }
    }

    /** Vertex colour: teal-white with brightness a ("eyes" add colours and ignore transparency, so the colour itself is dimmed). */
    private static int color(float a) {
        float k = Mth.clamp(a, 0f, 1f);
        return 0xFF000000 | ((int) (0xC8 * k) << 16) | ((int) (0xFA * k) << 8) | (int) (0xFF * k);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, float w,
                             float v0, float v1, int c0, int c1) {
        vertex(vc, p, x0, y0, z0, 0f, v0, c0);
        vertex(vc, p, x1, y1, z1, 0f, v1, c1);
        vertex(vc, p, x1, y1 + w, z1, 1f, v1, c1);
        vertex(vc, p, x0, y0 + w, z0, 1f, v0, c0);
    }

    private static void column(VertexConsumer vc, PoseStack.Pose p, float xa, float za, float xb, float zb, float y0, float y1, float v, int c) {
        vertex(vc, p, xa, y0, za, 0f, v, c);
        vertex(vc, p, xb, y0, zb, 1f, v, c);
        vertex(vc, p, xb, y1, zb, 1f, v + 2f, c);
        vertex(vc, p, xa, y1, za, 0f, v + 2f, c);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(p, x / 16f, y / 16f, z / 16f).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }
}
