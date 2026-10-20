package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Moving parts of the Interface: turbines in the funnels on all six sides and pendulums of flat metronomes on the four side faces.
 */
public class InterfaceRenderer implements BlockEntityRenderer<InterfaceBlockEntity> {
    public static final ModelResourceLocation TURBINE = ModelResourceLocation.standalone(
            Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/golem_interface_turbine"));
    public static final ModelResourceLocation PENDULUM = ModelResourceLocation.standalone(
            Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/golem_interface_pendulum"));
    /**
     * Metronomes lying flat on the side faces (as in tools/Interface.java: left of the funnel as seen by the viewer, axis at height 5.2,
     * in front of the body at depth 0.42 from the block plane): x, z of the axis in pixels and the rotation of the pendulum model facing outwards.
     */
    private static final float[][] METRONOMES = {{13.15f, 0.42f, 0f}, {2.85f, 15.58f, 180f}, {0.42f, 2.85f, 90f}, {15.58f, 13.15f, -90f}};
    private static final float PIVOT_Y = 5.2f;
    /** Pendulum swing: in the face plane it touches neither the funnel nor the frame edge. */
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
            // neighbouring sides spin in opposite directions, like gears
            pose.mulPose(Axis.ZP.rotationDegrees(d.ordinal() % 2 == 0 ? spin : -spin));
            pose.translate(-0.5, -0.5, -0.5);
            renderer.renderModel(pose.last(), buffer, null, turbine, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    RenderType.cutout());
            pose.popPose();
        }

        // pendulum: upside down (axis at the bottom), swings in the face plane; the swing grows with the spin-up, neighbouring faces are in antiphase
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
