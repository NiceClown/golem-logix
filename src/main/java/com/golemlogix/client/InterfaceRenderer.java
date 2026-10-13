package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Подвижные детали Интерфейса: турбины в раструбах всех шести сторон и маятники плоских метрономов на четырёх боковых гранях.
 */
public class InterfaceRenderer implements BlockEntityRenderer<InterfaceBlockEntity> {
    public static final ModelResourceLocation TURBINE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/golem_interface_turbine"));
    public static final ModelResourceLocation PENDULUM = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/golem_interface_pendulum"));
    /**
     * Метрономы плашмя на боковых гранях (как в tools/Interface.java: слева от раструба глазами зрителя, ось на высоте 5.2,
     * перед корпусом на глубине 0.42 от плоскости блока): x, z оси в пикселях и поворот модели маятника лицом наружу.
     */
    private static final float[][] METRONOMES = {{13.15f, 0.42f, 0f}, {2.85f, 15.58f, 180f}, {0.42f, 2.85f, 90f}, {15.58f, 13.15f, -90f}};
    private static final float PIVOT_Y = 5.2f;
    /** Размах маятника: в плоскости грани он не задевает ни раструб, ни ребро каркаса. */
    private static final float SWING = 11f;

    public InterfaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(InterfaceBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        BakedModel turbine = mc.getModelManager().getModel(TURBINE);
        BakedModel pendulum = mc.getModelManager().getModel(PENDULUM);
        var renderer = mc.getBlockRenderer().getModelRenderer();
        var buffer = buffers.getBuffer(RenderType.cutout());
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float activity = Mth.lerp(partialTick, be.activityPrev, be.activity);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);

        for (Direction d : Direction.values()) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            switch (d) {
                case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
                case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                default -> {
                }
            }
            // соседние стороны крутятся в разные стороны — как шестерни
            pose.mulPose(Axis.ZP.rotationDegrees(d.ordinal() % 2 == 0 ? spin : -spin));
            pose.translate(-0.5, -0.5, -0.5);
            renderer.renderModel(pose.last(), buffer, null, turbine, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    RenderType.cutout());
            pose.popPose();
        }

        // маятник: перевёрнутый (ось внизу), качается в плоскости грани; размах растёт с разгоном, соседние грани — в противофазе
        for (int i = 0; i < METRONOMES.length; i++) {
            float[] m = METRONOMES[i];
            float swing = SWING * activity * Mth.sin(phase + i * Mth.HALF_PI);
            pose.pushPose();
            pose.translate(m[0] / 16f, PIVOT_Y / 16f, m[1] / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(m[2]));
            pose.mulPose(Axis.ZP.rotationDegrees(swing));
            pose.translate(-0.5, 0, -0.5);
            renderer.renderModel(pose.last(), buffer, null, pendulum, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    RenderType.cutout());
            pose.popPose();
        }
    }
}
