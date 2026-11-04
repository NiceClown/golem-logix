package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.StockManometerBlock;
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

/**
 * Stock Manometer dial needle renderer:
 * Renders the rotating brass gauge needle on the front dial face, pointing according to stock / threshold ratio.
 */
public class StockManometerRenderer extends com.golemlogix.client.render.LegacyBer<StockManometerBlockEntity> {
    private static final Identifier NEEDLE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/stock_manometer_needle.png");

    /** The bridges behind the clock (model pixels, as in the model): the middle of the deck across, its top. */
    private static final float[][] BRIDGES = {{8f, 13.2f}, {3.1f, 7.2f}, {12.9f, 7.2f}};
    private static final float BRIDGE_Z0 = 9.2f, BRIDGE_Z1 = 13.9f, TINY_SCALE = 0.11f;
    /** A golem's trip over a bridge and back; it stops a little at each end. */
    private static final float TRIP_TICKS = 180f;

    public StockManometerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StockManometerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(StockManometerBlock.FACING)) {
            return;
        }
        Direction facing = be.getFacing();

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        if (facing == Direction.UP) {
            pose.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (facing == Direction.DOWN) {
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        }
        pose.translate(-0.5f, -0.5f, -0.5f);

        tinyGolems(be, partialTick, pose, buffers, light);

        // the needle's pivot: the dial's centre (8, 8), just proud of the clock face at z = 5.48
        pose.translate(8f / 16f, 8f / 16f, 5.44f / 16f);

        double ratio = be.getThreshold() <= 0 ? 0.0 : (double) be.getStockCount() / Math.max(1.0, 2.0 * be.getThreshold());
        float targetAngle = (float) (-135.0 + Math.clamp(ratio, 0.0, 1.0) * 270.0);
        pose.mulPose(Axis.ZP.rotationDegrees(-targetAngle));

        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(NEEDLE));
        float r = 4.6f / 16f;
        PoseStack.Pose p = pose.last();
        // Quad centred at pivot (0, 0)
        vertex(vc, p, r, -r, 0f, 0f, 1f, light);
        vertex(vc, p, -r, -r, 0f, 1f, 1f, light);
        vertex(vc, p, -r, r, 0f, 1f, 0f, light);
        vertex(vc, p, r, r, 0f, 0f, 0f, light);

        pose.popPose();
    }

    /** One very small copper golem on each bridge, strolling back and forth on its own timing. */
    private static void tinyGolems(StockManometerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = be.getLevel().getGameTime() + partialTick + (be.getBlockPos().hashCode() & 255);
        for (int i = 0; i < BRIDGES.length; i++) {
            float t = ((time + i * 67f) % TRIP_TICKS) / TRIP_TICKS * 2f;
            boolean back = t > 1f;
            float leg = back ? t - 1f : t;
            // the walk takes 80% of each leg
            float k = Mth.clamp(leg / 0.8f, 0f, 1f);
            boolean walking = leg < 0.8f;
            float z = Mth.lerp(back ? 1f - k : k, BRIDGE_Z0, BRIDGE_Z1);
            CopperGolemPuppet.render(pose, buffers, light, new org.joml.Vector3f(BRIDGES[i][0] / 16f, BRIDGES[i][1] / 16f, z / 16f),
                    new org.joml.Vector3f(0f, 0f, back ? -1f : 1f), new org.joml.Vector3f(0f, 1f, 0f), TINY_SCALE,
                    new CopperGolemPuppet.Anim(time * 0.45f + i, walking, false, 0f), 0, null);
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0f, 0f, -1f);
    }
}
