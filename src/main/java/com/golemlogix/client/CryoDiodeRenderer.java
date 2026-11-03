package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blocks.CryoDiodeBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Cryo-Diode: the five-handled brass wheel on the valve's stem. It turns slowly while Source flows through the valve and stops
 * where it is when the flow stops.
 */
public class CryoDiodeRenderer extends com.golemlogix.client.render.LegacyBer<CryoDiodeBlockEntity> {
    private static final Identifier WHEEL = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_diode_wheel5.png");
    /** The wheel round the stem at (8, y, 8) of the north-facing frame: two layers for its thickness, its radius (pixels). */
    private static final float Y0 = 14.6f, Y1 = 15.3f, R = 5.6f, SPEED = 2.5f;

    public CryoDiodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CryoDiodeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(CryoDiodeBlock.FACING)) {
            return;
        }
        long now = be.getLevel().getGameTime();
        boolean turning = be.flowing(now);
        // the angle is kept on the block entity, so a stop leaves the wheel where it is
        if (now != be.wheelTick) {
            if (turning) {
                be.wheelAngle += SPEED * Math.min(3, now - be.wheelTick);
            }
            be.wheelTick = now;
        }
        float angle = be.wheelAngle + (turning ? SPEED * partialTick : 0f);
        pose.pushPose();
        FormationBusRenderer.orient(pose, be.getFacing());
        pose.translate(0.5f, 0, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(WHEEL));
        PoseStack.Pose p = pose.last();
        float r = R / 16f;
        for (float y : new float[]{Y0 / 16f, Y1 / 16f}) {
            quad(vc, p, y, r, light, 1);
            quad(vc, p, y, r, light, -1);
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p, float y, float r, int light, int up) {
        float[][] c = {{-r, -r, 0, 0}, {-r, r, 0, 1}, {r, r, 1, 1}, {r, -r, 1, 0}};
        for (int i = 0; i < 4; i++) {
            float[] v = c[up > 0 ? i : 3 - i];
            vc.addVertex(p, v[0], y, v[1]).setColor(0xFFFFFFFF).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(p, 0, up, 0);
        }
    }
}
