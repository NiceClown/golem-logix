package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlantHeadBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Голова Атланта: модель головы, повёрнутая вокруг шеи (8, 10, 8) — сперва по взгляду статуи, потом к игроку. */
public class AtlantHeadRenderer implements BlockEntityRenderer<AtlantHeadBlockEntity> {
    public static final ModelResourceLocation HEAD = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/atlas_atlant_head"));

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
        // модель смотрит на север (-Z); положительный yaw — поворот к игроку справа налево, pitch — кивок вверх
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(-0.5, -10 / 16.0, -0.5);
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null,
                mc.getModelManager().getModel(HEAD), 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
