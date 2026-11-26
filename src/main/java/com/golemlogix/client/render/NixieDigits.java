package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;

/**
 * The glowing wire digits of the nixie tubes (the heat lamps on the pipes, the Channel Counter): one picture of the ten digits,
 * drawn additively over the tube's mesh and tinted - so a lit cathode glows and an unlit tube shows only its dark wires.
 */
public final class NixieDigits {
    private static final Identifier DIGITS = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/nixie_digits.png");

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
        digit(p, buffers.getBuffer(GlowTypes.glow(DIGITS)), digit, cx, cy, cz, w, h, rx, ry, rz, ux, uy, uz, color);
    }

    /** Direct-buffer form for drawing several digits without re-looking up the glow buffer. */
    public static void digit(PoseStack.Pose p, VertexConsumer vc, int digit,
                             float cx, float cy, float cz, float w, float h,
                             float rx, float ry, float rz, float ux, float uy, float uz, int color) {
        if (digit < 0 || digit > 9) {
            return;
        }
        float u0 = digit / 10f, u1 = (digit + 1) / 10f;
        float nx = ry * uz - rz * uy, ny = rz * ux - rx * uz, nz = rx * uy - ry * ux;
        float hw = w * 0.5f, hh = h * 0.5f;
        float hrx = rx * hw, hry = ry * hw, hrz = rz * hw;
        float hux = ux * hh, huy = uy * hh, huz = uz * hh;
        float inv16 = PixelMesh.PIXEL;
        vc.addVertex(p, (cx - hrx + hux) * inv16, (cy - hry + huy) * inv16, (cz - hrz + huz) * inv16)
                .setColor(color).setUv(u0, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (cx - hrx - hux) * inv16, (cy - hry - huy) * inv16, (cz - hrz - huz) * inv16)
                .setColor(color).setUv(u0, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (cx + hrx - hux) * inv16, (cy + hry - huy) * inv16, (cz + hrz - huz) * inv16)
                .setColor(color).setUv(u1, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, (cx + hrx + hux) * inv16, (cy + hry + huy) * inv16, (cz + hrz + huz) * inv16)
                .setColor(color).setUv(u1, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    /** The shared glow buffer for drawing a batch of nixie digits. */
    public static VertexConsumer buffer(MultiBufferSource buffers) {
        return buffers.getBuffer(GlowTypes.glow(DIGITS));
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
