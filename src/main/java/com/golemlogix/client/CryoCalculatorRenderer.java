package com.golemlogix.client;

import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Cryo-Calculator at work: on the roof a crew of snowmen of different heights assembles the item, tossing ice parts
 * into a mini model of what the calculator is making right now (the output of the current step); when idle the snowmen loaf around.
 * When the coprocessors really speed up the work, a foreman with an atlant head appears (see {@link SnowCrew}).
 * The running "matrix rain" on the side panels is an animated texture of the model in the ACTIVE state.
 */
public class CryoCalculatorRenderer implements BlockEntityRenderer<CryoCalculatorBlockEntity> {
    public CryoCalculatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CryoCalculatorBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1.4, 0);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(CryoCalculatorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float foreman = Mth.lerp(partialTick, be.foremanPrev, be.foreman);
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());

        float appear = spin * spin * (3 - 2 * spin);
        float bob = Mth.sin(time * 0.1f) * 0.03f;
        Vector3f proj = new Vector3f(0.5f, 1.0f + 0.78f * appear + bob, 0.5f);
        SnowCrew.render(pose, buffers, light, time, be.getBlockPos().asLong(), 1.0f, spin, foreman, proj);

        // mini model of the current item: floats above the roof while the calculator works (held by the visual window)
        ItemStack current = be.clientDisplayItem();
        if (!current.isEmpty() && appear > 0.005f) {
            float size = (current.getItem() instanceof BlockItem ? 0.5f : 0.42f) * appear;
            pose.pushPose();
            pose.translate(proj.x, proj.y, proj.z);
            pose.mulPose(Axis.YP.rotationDegrees(time * 3f));
            pose.scale(size, size, size);
            Minecraft.getInstance().getItemRenderer().renderStatic(current, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
                    pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
    }
}
