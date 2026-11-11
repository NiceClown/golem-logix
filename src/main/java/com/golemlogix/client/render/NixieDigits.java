package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
        if (digit < 0 || digit > 9) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        float t = Math.max(0.22f, Math.min(w, h) * 0.16f);   // the bar's thickness
        float half = w / 2f, qh = h / 2f, vq = h / 4f;
        String bars = SEGMENTS[digit];
        // horizontal bars: a (top), g (middle), d (bottom)
        if (bars.indexOf('a') >= 0) {
            bar(vc, p, c, right, up, 0f, qh - t / 2f, w - t, t, color);
        }
        if (bars.indexOf('g') >= 0) {
            bar(vc, p, c, right, up, 0f, 0f, w - t, t, color);
        }
        if (bars.indexOf('d') >= 0) {
            bar(vc, p, c, right, up, 0f, -qh + t / 2f, w - t, t, color);
        }
        // vertical bars: f (top-left), b (top-right), e (bottom-left), c (bottom-right)
        if (bars.indexOf('f') >= 0) {
            bar(vc, p, c, right, up, -half + t / 2f, vq, t, vq * 2f - t, color);
        }
        if (bars.indexOf('b') >= 0) {
            bar(vc, p, c, right, up, half - t / 2f, vq, t, vq * 2f - t, color);
        }
        if (bars.indexOf('e') >= 0) {
            bar(vc, p, c, right, up, -half + t / 2f, -vq, t, vq * 2f - t, color);
        }
        if (bars.indexOf('c') >= 0) {
            bar(vc, p, c, right, up, half - t / 2f, -vq, t, vq * 2f - t, color);
        }
    }

    /** One bar: a rectangle of bw x bh, its centre at (dx, dy) from the digit's centre, in the plane of right and up. */
    private static void bar(VertexConsumer vc, PoseStack.Pose p, Vector3f c, Vector3f right, Vector3f up,
                             float dx, float dy, float bw, float bh, int color) {
        Vector3f centre = new Vector3f(c).add(new Vector3f(right).mul(dx)).add(new Vector3f(up).mul(dy));
        Vector3f n = new Vector3f(right).cross(up);
        float hw = bw / 2f, hh = bh / 2f;
        float[][] corners = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
        for (float[] k : corners) {
            Vector3f at = new Vector3f(centre).add(new Vector3f(right).mul(k[0] * hw)).add(new Vector3f(up).mul(k[1] * hh)).div(16f);
            vc.addVertex(p, at.x, at.y, at.z).setColor(color).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0).setNormal(p, n.x, n.y, n.z);
        }
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
