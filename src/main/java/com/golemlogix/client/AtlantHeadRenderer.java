package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlantHeadBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Atlant head: the head model rotated around the neck (8, 10, 8), first by the statue's facing, then towards the player. */
public class AtlantHeadRenderer implements BlockEntityRenderer<AtlantHeadBlockEntity> {
    public static final ModelResourceLocation HEAD = ModelResourceLocation.standalone(
            Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/atlas_atlant_head"));

    public AtlantHeadRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AtlantHeadBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.getBlockState().hasProperty(AtlantBlock.FACING)) {
            return;
        }
        float yaw = be.yawPrev + Mth.wrapDegrees(be.yaw - be.yawPrev) * partialTick;
        float pitch = Mth.lerp(partialTick, be.pitchPrev, be.pitch);
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(AtlantBlock.FACING));
        pose.translate(0.5, 10 / 16.0, 0.5);
        // the model faces north (-Z); a positive yaw turns towards a player from right to left, pitch is a nod upwards
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(-0.5, -10 / 16.0, -0.5);
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null,
                mc.getModelManager().getModel(HEAD), 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
