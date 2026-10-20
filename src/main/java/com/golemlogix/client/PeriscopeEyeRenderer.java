package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DirectionalBlock;

/**
 * The eye behind the Periscope Storage Bus monocle: an ender-eye iris with a slit pupil that looks around. Every bus keeps its own
 * rhythm (from its position): it holds a glance for 1.5 to 3.5 seconds, then darts to a new spot in a few ticks; now and then it
 * looks straight ahead. Looking aside squeezes the iris a little, and the glass glint stays on top.
 */
public class PeriscopeEyeRenderer extends com.golemlogix.client.render.LegacyBer<PeriscopeBlockEntity> {
    private static final Identifier EYE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/periscope_eye.png");
    /** Iris radius and how far it may look aside (pixels; the glass of the monocle has a radius of 2.58). */
    private static final float IRIS = 1.3f, REACH = 0.8f;
    /** Ticks of a dart to a new spot. */
    private static final float DART = 4f;

    public PeriscopeEyeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PeriscopeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(DirectionalBlock.FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(DirectionalBlock.FACING);
        long seed = be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L;
        int period = 30 + (int) Math.floorMod(seed >>> 11, 41L);
        double time = be.getLevel().getGameTime() + partialTick + Math.floorMod(seed >>> 23, (long) period);
        long glance = (long) Math.floor(time / period);
        float into = (float) (time - glance * period);
        float[] from = target(seed, glance - 1), to = target(seed, glance);
        float k = Mth.clamp(into / DART, 0f, 1f);
        k = k * k * (3 - 2 * k);
        float lookX = Mth.lerp(k, from[0], to[0]), lookY = Mth.lerp(k, from[1], to[1]);

        pose.pushPose();
        // the model's front is north (z = 0); turn it to the bus's facing, up and down included
        pose.translate(0.5f, 0.5f, 0.5f);
        if (facing == Direction.UP) {
            pose.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (facing == Direction.DOWN) {
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        }
        pose.translate(-0.5f, -0.5f, -0.5f);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityTranslucent(EYE));
        // seen from the front the model's +X is on the viewer's left, so "right" is -X
        float squeezeX = 1f - 0.25f * Math.abs(lookX) / REACH, squeezeY = 1f - 0.25f * Math.abs(lookY) / REACH;
        quad(vc, pose.last(), 8f - lookX, 8f + lookY, -0.05f, IRIS * squeezeX, IRIS * squeezeY, 0f, 0.5f, light);
        // the glint on the glass: up and to the viewer's left, over the iris
        quad(vc, pose.last(), 9.0f, 9.1f, -0.07f, 0.55f, 0.55f, 0.5f, 1f, light);
        pose.popPose();
    }

    /** Where glance n looks (pixels, x to the viewer's right, y up): a quarter of the glances look straight ahead. */
    private static float[] target(long seed, long n) {
        long h = seed ^ (n * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        if ((h & 3) == 0) {
            return new float[]{0f, 0f};
        }
        double angle = ((h >>> 8) & 0xFFFF) / 65536.0 * Math.PI * 2;
        double r = REACH * (0.55 + 0.45 * ((h >>> 24) & 0xFF) / 255.0);
        return new float[]{(float) (Math.cos(angle) * r), (float) (Math.sin(angle) * r)};
    }

    /** A square facing -Z centred at (cx, cy) with half-sizes rx, ry (pixels), the texture's u0..u1 across, the whole height. */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float z, float rx, float ry, float u0, float u1, int light) {
        float x0 = (cx - rx) / 16f, x1 = (cx + rx) / 16f, y0 = (cy - ry) / 16f, y1 = (cy + ry) / 16f, zz = z / 16f;
        // the texture's left goes to the viewer's left (+X)
        vertex(vc, p, x1, y0, zz, u0, 1f, light);
        vertex(vc, p, x0, y0, zz, u1, 1f, light);
        vertex(vc, p, x0, y1, zz, u1, 0f, light);
        vertex(vc, p, x1, y1, zz, u0, 0f, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0f, 0f, -1f);
    }
}
