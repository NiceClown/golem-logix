package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blocks.CryoDiodeBlock;
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
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Cryo-Diode: the handwheel on the valve's stem (rendered from the baked model so the spokes and hub have full 3D geometry).
 * Turns slowly while Source flows through the valve and freezes at its angle when flow stops.
 */
public class CryoDiodeRenderer implements BlockEntityRenderer<CryoDiodeBlockEntity> {
    public static final ModelResourceLocation WHEEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/cryo_diode_wheel"));
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
        BakedModel wheel = Minecraft.getInstance().getModelManager().getModel(WHEEL);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();

        pose.pushPose();
        orient(pose, be.getFacing());
        pose.translate(0.5f, 0, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-0.5f, 0, -0.5f);
        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, wheel, 1f, 1f, 1f, light,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    /** Turns the north-facing frame to the block's facing (the same turn as the Formation Bus renderer). */
    private static void orient(PoseStack pose, Direction facing) {
        pose.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180f));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90f));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90f));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90f));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90f));
            default -> {
            }
        }
        pose.translate(-0.5f, -0.5f, -0.5f);
    }
}
