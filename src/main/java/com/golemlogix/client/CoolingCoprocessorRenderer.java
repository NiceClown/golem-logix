package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CoolingCoprocessorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Cooling Co-Processor fins as a "flywheel": while the neighbouring calculator works, the five radiator fins rhythmically squeeze
 * towards the middle and spread out; spin-up and run-down are smooth (stroke and amplitude grow and fade together).
 */
public class CoolingCoprocessorRenderer extends com.golemlogix.client.render.LegacyBer<CoolingCoprocessorBlockEntity> {
    public static final net.minecraft.resources.Identifier FIN = com.golemlogix.client.render.StandaloneModels.FIN;
    /** Rest position of the fins (bottom edge, pixels) and the centre they squeeze towards. */
    private static final float[] REST = {3.5f, 5.5f, 7.5f, 9.5f, 11.5f};
    private static final float CENTER = 7.5f;

    public CoolingCoprocessorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CoolingCoprocessorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float speed = Mth.lerp(partialTick, be.speedPrev, be.speed);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        // squeeze 0..1: (1 - cos)/2 gives a soft stroke without jerks at the extremes
        float squeeze = (1f - Mth.cos(phase)) * 0.5f * speed;
        net.minecraft.resources.Identifier fin = FIN;
        for (int i = 0; i < REST.length; i++) {
            float y = REST[i] + (CENTER - REST[i]) * 0.7f * squeeze;
            // the outer fins lag a little: a wave across the radiator
            float lag = (Mth.sin(phase - i * 0.35f) * 0.35f) * speed;
            pose.pushPose();
            pose.translate(0, (y + lag) / 16f, 0);
            com.golemlogix.client.render.StandaloneModels.render(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), fin, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
