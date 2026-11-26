package com.golemlogix.client;

import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blocks.CryoDiodeBlock;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.client.render.StandaloneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Cryo-Diode: 3D crimson-painted iron and brass handwheel on the valve's stem. It turns slowly while Source flows through the
 * valve and stops where it is when the flow stops.
 */
public class CryoDiodeRenderer extends LegacyBer<CryoDiodeBlockEntity> {
    private static final float SPEED = 2.5f;

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
            long delta = now - be.wheelTick;
            if (turning && delta > 0L) {
                be.wheelAngle = (be.wheelAngle + SPEED * Math.min(3L, delta)) % 360f;
            }
            be.wheelTick = now;
        }
        float angle = be.wheelAngle + (turning ? SPEED * partialTick : 0f);
        pose.pushPose();
        Quads.orient(pose, be.getFacing());
        pose.translate(0.5f, 0f, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-0.5f, 0f, -0.5f);
        var vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        StandaloneModels.render(pose.last(), vc, StandaloneModels.DIODE_WHEEL, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
