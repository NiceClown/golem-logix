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

        // the two glass gauge tubes at the front corners fill with glowing teal as high as the needle reads
        double fill = be.getThreshold() <= 0 ? 0.0 : Math.clamp((double) be.getStockCount() / Math.max(1.0, 2.0 * be.getThreshold()), 0.0, 1.0);
        if (fill > 0.01) {
            VertexConsumer lv = buffers.getBuffer(RenderTypes.entityTranslucent(LIQUID));
            float top = 3.1f + (float) fill * 9.8f;
            for (float x : new float[]{0.85f, 13.85f}) {
                box(lv, pose.last(), x, 3.05f, 0.85f, x + 1.3f, top, 2.15f, net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
            }
        }

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

    private static final Identifier LIQUID = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_teal_liquid.png");

    /** A box in model pixels, all six faces with the whole texture. */
    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float[][][] faces = {
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}},
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {0, -1, 0}}};
        float[][] uv = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(p, f[i][0] / 16f, f[i][1] / 16f, f[i][2] / 16f).setColor(0xFFFFFFFF).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, f[4][0], f[4][1], f[4][2]);
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0f, 0f, -1f);
    }
}
