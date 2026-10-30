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

        // Center of dial needle pivot (model pixels 8, 8, slightly proud of face z = -0.02)
        pose.translate(8f / 16f, 8f / 16f, -0.02f / 16f);

        double ratio = be.getThreshold() <= 0 ? 0.0 : (double) be.getStockCount() / Math.max(1.0, 2.0 * be.getThreshold());
        float targetAngle = (float) (-135.0 + Math.clamp(ratio, 0.0, 1.0) * 270.0);
        pose.mulPose(Axis.ZP.rotationDegrees(-targetAngle));

        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(NEEDLE));
        float r = 3.6f / 16f;
        PoseStack.Pose p = pose.last();
        // Quad centred at pivot (0, 0)
        vertex(vc, p, r, -r, 0f, 0f, 1f, light);
        vertex(vc, p, -r, -r, 0f, 1f, 1f, light);
        vertex(vc, p, -r, r, 0f, 1f, 0f, light);
        vertex(vc, p, r, r, 0f, 0f, 0f, light);

        pose.popPose();
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0f, 0f, -1f);
    }
}
