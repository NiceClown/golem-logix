package com.golemlogix.client;

import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DirectionalBlock;

/**
 * Annihilation Bus: the small iron golem standing in the brass niche of the front, the tool in its right hand (an iron pickaxe
 * when the slot is empty). Mining, it swings again and again at the block in front; clicking, it reaches out once per use; idle,
 * it rests the tool on its shoulder and looks about.
 */
public class AnnihilationBusRenderer extends com.golemlogix.client.render.LegacyBer<AnnihilationBusBlockEntity> {
    private static final Identifier GOLEM = Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    /** About 10 pixels tall, on the niche floor (y 2), in the middle of its depth (z 0..5). */
    private static final float SCALE = 0.235f, FLOOR = 2f, GOLEM_Z = 2.9f;
    private final ModelPart golem;

    public AnnihilationBusRenderer(BlockEntityRendererProvider.Context context) {
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
    }

    @Override
    public void render(AnnihilationBusBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(DirectionalBlock.FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(DirectionalBlock.FACING);
        int front = LevelRenderer.getLightCoords(be.getLevel(), be.getBlockPos().relative(facing));
        float time = be.getLevel().getGameTime() + partialTick;

        ModelPart head = golem.getChild("head"), rightArm = golem.getChild("right_arm"), leftArm = golem.getChild("left_arm");
        golem.getChild("right_leg").xRot = 0f;
        golem.getChild("left_leg").xRot = 0f;
        float since = time - be.lastUse();
        if (be.swinging()) {
            // mining: a strike every 8 ticks, the tool brought down hard and raised again
            float ph = (time % 8f) / 8f;
            float strike = ph < 0.35f ? ph / 0.35f : 1f - (ph - 0.35f) / 0.65f;
            rightArm.xRot = -2.3f + 1.7f * strike * strike;
            leftArm.xRot = -0.5f;
            head.xRot = 0.35f;
            head.yRot = 0f;
        } else if (be.mode() != AnnihilationBusBlockEntity.LEFT && since < 8f) {
            // a click or a strike: a quick reach forward
            float k = since / 8f;
            rightArm.xRot = -0.4f - 1.1f * Mth.sin(k * Mth.PI);
            leftArm.xRot = 0f;
            head.xRot = 0.3f;
            head.yRot = 0f;
        } else {
            rightArm.xRot = -0.55f + 0.04f * Mth.sin(time * 0.07f);
            leftArm.xRot = 0.02f * Mth.sin(time * 0.05f);
            head.xRot = 0.1f;
            head.yRot = Mth.sin(time * 0.025f) * 0.5f;
        }
        rightArm.yRot = 0f;
        leftArm.yRot = 0f;

        pose.pushPose();
        FormationBusRenderer.orient(pose, facing);
        pose.translate(0.5f, FLOOR / 16f, GOLEM_Z / 16f);
        pose.scale(-SCALE, -SCALE, SCALE);
        pose.translate(0f, -1.501f, 0f);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(GOLEM));
        golem.render(pose, vc, front, OverlayTexture.NO_OVERLAY);

        ItemStack tool = be.getTool().getItem(0);
        if (tool.isEmpty()) {
            tool = new ItemStack(be.mode() == AnnihilationBusBlockEntity.ATTACK ? Items.IRON_SWORD : Items.IRON_PICKAXE);
        }
        pose.pushPose();
        rightArm.translateAndRotate(pose);
        // at the end of the iron golem's right arm (a 30-unit box hanging from the shoulder), the tool pointing forward
        pose.translate(-11f / 16f, 1.75f, 0f);
        pose.mulPose(Axis.XP.rotationDegrees(-90f));
        pose.scale(2.2f, 2.2f, 2.2f);
        com.golemlogix.client.render.BerBuffers.item(buffers, tool, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, front, pose, be.getLevel(), 0);
        pose.popPose();
        pose.popPose();
    }
}
