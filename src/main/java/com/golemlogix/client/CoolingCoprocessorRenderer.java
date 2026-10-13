package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CoolingCoprocessorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Рёбра Сопроцессора Охлаждения — «маховик»: пока соседний калькулятор работает, пять рёбер радиатора ритмично сжимаются к
 * середине и расходятся; разгон и выбег плавные (ход и размах нарастают и гаснут вместе).
 */
public class CoolingCoprocessorRenderer implements BlockEntityRenderer<CoolingCoprocessorBlockEntity> {
    public static final ModelResourceLocation FIN = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/cooling_coprocessor_fin"));
    /** Покой рёбер (нижний край, пиксели) и центр, к которому они сжимаются. */
    private static final float[] REST = {3.5f, 5.5f, 7.5f, 9.5f, 11.5f};
    private static final float CENTER = 7.5f;

    public CoolingCoprocessorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CoolingCoprocessorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float speed = Mth.lerp(partialTick, be.speedPrev, be.speed);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        // сжатие 0..1: (1 - cos)/2 — мягкий ход без рывков в крайних точках
        float squeeze = (1f - Mth.cos(phase)) * 0.5f * speed;
        BakedModel fin = Minecraft.getInstance().getModelManager().getModel(FIN);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        for (int i = 0; i < REST.length; i++) {
            float y = REST[i] + (CENTER - REST[i]) * 0.7f * squeeze;
            // крайние рёбра чуть отстают — волна по радиатору
            float lag = (Mth.sin(phase - i * 0.35f) * 0.35f) * speed;
            pose.pushPose();
            pose.translate(0, (y + lag) / 16f, 0);
            renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, fin, 1f, 1f, 1f,
                    light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
        }
    }
}
