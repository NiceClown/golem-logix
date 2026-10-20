package com.golemlogix.client;

import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.blockentity.RecipeMonitorBlockEntity;
import com.golemlogix.blocks.RecipeMonitorBlock;
import com.golemlogix.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Recipe Monitor: in the purple vortex of the porthole punch cards float in a circle, one for every job running in the network
 * (up to six), like the cards in the portal window of the reference. Cards appear and disappear smoothly.
 */
public class RecipeMonitorRenderer implements BlockEntityRenderer<RecipeMonitorBlockEntity> {
    /** Centre of the porthole on the front (model pixels) and the radius of the card orbit. */
    private static final float CX = 8f, CY = 8.5f, RADIUS = 3.6f;

    private ItemStack card;

    public RecipeMonitorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RecipeMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        lampGolem(be, partialTick, pose, buffers, light);
        float shown = Mth.lerp(partialTick, be.shownCardsPrev, be.shownCards);
        if (shown < 0.02f) {
            return;
        }
        if (card == null) {
            card = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        }
        float time = be.getLevel().getGameTime() + partialTick;
        int count = (int) Math.ceil(shown - 0.001f);
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(RecipeMonitorBlock.FACING));
        for (int i = 0; i < count; i++) {
            // the last card "develops" as shown grows
            float alive = Math.min(1f, shown - i);
            float a = (float) Math.toRadians(time * 2.4f + i * 360f / Math.max(1, count));
            float x = CX + Mth.cos(a) * RADIUS, y = CY + Mth.sin(a) * RADIUS;
            pose.pushPose();
            // in front of the vortex (it is sunk into the frame); the model's "north" is the front
            pose.translate(1f - x / 16f, y / 16f, 0.9f / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(180f + Mth.sin(time * 0.08f + i) * 25f));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.05f + i * 1.7f) * 20f));
            float s = 0.2f * alive;
            pose.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(card, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), i);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * Copper lamplighter golem on the roof: when idle the lamp glows steady purple and the golem wanders over the platform; while crafting
     * it stands in the middle and the lamp blinks green; on a failure or with no connection it glows red.
     */
    private void lampGolem(RecipeMonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(RecipeMonitorBlock.FACING));
        LampGolem.render(pose, buffers, light, be.walker, partialTick, be.lampState(), be.getLevel().getGameTime() + partialTick, 16f, 16.4f);
        pose.popPose();
    }
}