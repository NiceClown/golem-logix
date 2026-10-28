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
    private static final Identifier BELT = Identifier.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "textures/entity/plasma_belt.png");
    /** About 10 pixels tall, on the niche floor (y 2), in the middle of its depth (z 0..5). */
    private static final float SCALE = 0.235f, FLOOR = 2f, GOLEM_Z = 2.9f;
    private final ModelPart golem;

    public AnnihilationBusRenderer(BlockEntityRendererProvider.Context context) {
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
    }

    /** A box in model units (pixels of the model, divided here), every face showing the texture region u0..u1, v0..v1. */
    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            float u0, float v0, float u1, float v1, int light) {
        float[][][] faces = {
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x0, y0, z1}, {x0, y1, z1}, {x1, y1, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}, {1, 0, 0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}, {0, -1, 0}},
                {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}, {0, 1, 0}}};
        float[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(p, f[i][0] / 16f, f[i][1] / 16f, f[i][2] / 16f).setColor(0xFFFFFFFF).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, f[4][0], f[4][1], f[4][2]);
            }
        }
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
        if (be.belted()) {
            // the Plasma Temper worn as a belt round the waist (the body's lower box: x -5..5, y 9.5..15.5, z -3.5..3.5), its
            // plasma buckle in front
            pose.pushPose();
            golem.getChild("body").translateAndRotate(pose);
            VertexConsumer bv = buffers.getBuffer(RenderTypes.entityCutout(BELT));
            PoseStack.Pose bp = pose.last();
            box(bv, bp, -5.4f, 11f, -3.9f, 5.4f, 13.5f, 3.9f, 0f, 0f, 0.75f, 1f, front);
            box(bv, bp, -1.7f, 10.6f, -4.5f, 1.7f, 13.9f, -3.9f, 0.75f, 0f, 1f, 1f, front);
            pose.popPose();
        }

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
