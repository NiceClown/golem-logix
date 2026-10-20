package com.golemlogix.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.LampWalker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Copper golem with a signal lamp on a machine roof (Recipe Monitor, Mana Saturator). Lamp: 0 = idle (steady
 * purple, the golem wanders), 1 = working (blinks green, the golem faces the viewer), 2 = failure (glows red).
 */
public final class LampGolem {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/bay_white.png");

    private LampGolem() {
    }

    /**
     * pose is already in the machine model frame (facing north). Roof pixels (x, z) become block fractions as
     * ((originX - x) / 16, y / 16, z / 16): the model's x is mirrored, as in all machine renderers.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, LampWalker w, float partialTick, int state, float time,
                              float originX, float y) {
        float x = Mth.lerp(partialTick, w.xPrev, w.x), z = Mth.lerp(partialTick, w.zPrev, w.z);
        float heading = w.headingPrev + Mth.wrapDegrees(w.heading - w.headingPrev) * partialTick;
        float stride = Mth.lerp(partialTick, w.stridePrev, w.stride);
        boolean walking = w.walking();
        // standing still while working or failing, the golem looks at the viewer (at the model's -Z)
        float yaw = walking || state == 0 ? heading : 180f;
        float fx = Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        Vector3f at = new Vector3f((originX - x) / 16f, y / 16f, z / 16f);
        Vector3f forward = new Vector3f(-fx, 0f, fz);
        int color;
        boolean lit;
        switch (state) {
            case 1 -> {
                lit = ((int) (time / 6)) % 2 == 0;
                color = lit ? 0xFF3CF070 : 0xFF0E3A1C;
            }
            case 2 -> {
                lit = true;
                color = 0xFFF03A2A;
            }
            default -> {
                lit = true;
                int a = (int) (180 + 60 * Mth.sin(time * 0.08f));
                color = 0xFF000000 | ((a * 0xA0 / 255) << 16) | ((a * 0x70 / 255) << 8) | a;
            }
        }
        final int lampColor = color;
        final boolean lampLit = lit;
        CopperGolemPuppet.render(pose, buffers, light, at, forward, new Vector3f(0f, 1f, 0f), 0.2f,
                new CopperGolemPuppet.Anim(stride * 3f, walking, true, 0f), 0, hp -> {
                    float h = 0.14f;
                    var solid = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
                    Quads.box(solid, hp.last(), -h, -0.12f, -h, h, -0.05f, h, 0.5f, 0.5f, 0.5f, 0.5f, 0xFFB8862B, light);
                    Quads.box(solid, hp.last(), -h * 0.8f, -0.05f, -h * 0.8f, h * 0.8f, 0.22f, h * 0.8f, 0.5f, 0.5f, 0.5f, 0.5f,
                            lampColor, lampLit ? LightCoordsUtil.FULL_BRIGHT : light);
                    Quads.box(solid, hp.last(), -h, 0.22f, -h, h, 0.27f, h, 0.5f, 0.5f, 0.5f, 0.5f, 0xFFB8862B, light);
                    Quads.box(solid, hp.last(), -0.02f, 0.27f, -0.02f, 0.02f, 0.36f, 0.02f, 0.5f, 0.5f, 0.5f, 0.5f, 0xFF7A5A1A, light);
                    if (lampLit) {
                        var glow = buffers.getBuffer(RenderTypes.eyes(WHITE));
                        float g = h * 1.6f;
                        Quads.box(glow, hp.last(), -g, -0.1f, -g, g, 0.3f, g, 0.5f, 0.5f, 0.5f, 0.5f,
                                (0x60 << 24) | (lampColor & 0xFFFFFF), LightCoordsUtil.FULL_BRIGHT);
                    }
                });
    }
}
