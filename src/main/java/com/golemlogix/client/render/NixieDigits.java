package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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
        if (digit < 0 || digit > 9) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(GlowTypes.glow(DIGITS));
        float u0 = digit / 10f, u1 = (digit + 1) / 10f;
        Vector3f n = new Vector3f(right).cross(up);
        float[][] corners = {{-1, 1, u0, 0}, {-1, -1, u0, 1}, {1, -1, u1, 1}, {1, 1, u1, 0}};
        for (float[] k : corners) {
            Vector3f at = new Vector3f(c).add(new Vector3f(right).mul(k[0] * w / 2)).add(new Vector3f(up).mul(k[1] * h / 2)).div(16f);
            vc.addVertex(p, at.x, at.y, at.z).setColor(color).setUv(k[2], k[3]).setOverlay(OverlayTexture.NO_OVERLAY)
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
