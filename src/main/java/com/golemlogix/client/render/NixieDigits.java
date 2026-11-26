package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * The glowing digits of the nixie tubes (the Channel Counter): a seven-segment figure drawn from solid bars, tinted with the load
 * colour.
 * <p>
 * A picture of the digits would not do: the strokes of the sheet are a fraction of a pixel wide on a tube that is two pixels
 * across, and they disappear into the filtering. Bars of a known width stay readable at any distance.
 */
public final class NixieDigits {
    /** A plain white square, tinted per quad - the same sheet the copper tank's needle uses. */
    private static final ResourceLocation WHITE =
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/bay_white.png");

    /** The bars of the seven-segment figure, from top-left a, top-right b, bottom-right c, bottom d, bottom-left e, top-left f, middle g. */
    private static final String[] SEGMENTS = {
            "abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgecd", "abc", "abcdefg", "abcdfg"
    };

    private NixieDigits() {
    }

    /**
     * One digit (0..9) centred at c (pixels of the block), w wide and h high, in the plane of right and up (unit vectors, the face
     * looks along right x up), tinted with color (ARGB).
     */
    public static void digit(PoseStack.Pose p, MultiBufferSource buffers, int digit, Vector3f c, float w, float h, Vector3f right, Vector3f up,
                             int color) {
        digit(p, buffers, digit, c.x, c.y, c.z, w, h, right.x, right.y, right.z, up.x, up.y, up.z, color);
    }

    /** Scalar form of {@link #digit}: avoids per-digit Vector3f allocations at call sites. */
    public static void digit(PoseStack.Pose p, MultiBufferSource buffers, int digit,
                             float cx, float cy, float cz, float w, float h,
                             float rx, float ry, float rz, float ux, float uy, float uz, int color) {
        if (digit < 0 || digit > 9) {
            return;
        }
        digit(p, buffer(buffers), digit, cx, cy, cz, w, h, rx, ry, rz, ux, uy, uz, color);
    }

    /** Direct-buffer form for drawing several digits without re-looking up the glow buffer. */
    public static void digit(PoseStack.Pose p, VertexConsumer vc, int digit,
                             float cx, float cy, float cz, float w, float h,
                             float rx, float ry, float rz, float ux, float uy, float uz, int color) {
        if (digit < 0 || digit > 9) {
            return;
        }
        float nx = ry * uz - rz * uy, ny = rz * ux - rx * uz, nz = rx * uy - ry * ux;
        float t = Math.max(0.22f, Math.min(w, h) * 0.16f);   // the bar's thickness
        float half = w * 0.5f, qh = h * 0.5f, vq = h * 0.25f;
        String bars = SEGMENTS[digit];
        // horizontal bars: a (top), g (middle), d (bottom)
        if (bars.indexOf('a') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, 0f, qh - t * 0.5f, w - t, t, color);
        }
        if (bars.indexOf('g') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, 0f, 0f, w - t, t, color);
        }
        if (bars.indexOf('d') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, 0f, -qh + t * 0.5f, w - t, t, color);
        }
        // vertical bars: f (top-left), b (top-right), e (bottom-left), c (bottom-right)
        if (bars.indexOf('f') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, -half + t * 0.5f, vq, t, vq * 2f - t, color);
        }
        if (bars.indexOf('b') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, half - t * 0.5f, vq, t, vq * 2f - t, color);
        }
        if (bars.indexOf('e') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, -half + t * 0.5f, -vq, t, vq * 2f - t, color);
        }
        if (bars.indexOf('c') >= 0) {
            bar(vc, p, cx, cy, cz, rx, ry, rz, ux, uy, uz, nx, ny, nz, half - t * 0.5f, -vq, t, vq * 2f - t, color);
        }
    }

    /** The shared glow buffer for drawing a batch of nixie digits. */
    public static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
    }

    /** One bar: a rectangle of bw x bh, its centre at (dx, dy) from the digit's centre, in the plane of right and up. */
    private static void bar(VertexConsumer vc, PoseStack.Pose p,
                            float cx, float cy, float cz,
                            float rx, float ry, float rz, float ux, float uy, float uz,
                            float nx, float ny, float nz,
                            float dx, float dy, float bw, float bh, int color) {
        float ox = cx + rx * dx + ux * dy;
        float oy = cy + ry * dx + uy * dy;
        float oz = cz + rz * dx + uz * dy;
        float hw = bw * 0.5f, hh = bh * 0.5f;
        float hrx = rx * hw, hry = ry * hw, hrz = rz * hw;
        float hux = ux * hh, huy = uy * hh, huz = uz * hh;
        float inv16 = PixelMesh.PIXEL;
        vc.addVertex(p, (ox - hrx + hux) * inv16, (oy - hry + huy) * inv16, (oz - hrz + huz) * inv16)
                .setColor(color).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (ox - hrx - hux) * inv16, (oy - hry - huy) * inv16, (oz - hrz - huz) * inv16)
                .setColor(color).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (ox + hrx - hux) * inv16, (oy + hry - huy) * inv16, (oz + hrz - huz) * inv16)
                .setColor(color).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (ox + hrx + hux) * inv16, (oy + hry + huy) * inv16, (oz + hrz + huz) * inv16)
                .setColor(color).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    /** The load colours: cyan while there is room, amber as it fills, red when (nearly) full; a dim ember for nothing at all. */
    public static int loadColor(int load, int capacity) {
        if (load <= 0) {
            return 0xFF5A3A26;
        }
        float ratio = load / (float) Math.max(1, capacity);
        return ratio < 0.5f ? 0xFF6FF4FF : ratio < 0.85f ? 0xFFFFB347 : 0xFFFF5038;
    }
}
