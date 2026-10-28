package com.golemlogix.client;

import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Formation Bus: the copper golem fused into the front panel above the mouth (its legs are inside the casing), and the block it
 * sets down. During a push the block slides out of the mouth, growing from half size to a whole block, onto the spot in front,
 * the golem's arms following it and its body leaning in; the push lasts the bus's interval, so it keeps pace at full speed.
 */
public class FormationBusRenderer extends com.golemlogix.client.render.LegacyBer<FormationBusBlockEntity> {
    /**
     * The golem (pixels, facing north) in the alcove above the mouth (y 8.5..15, back wall at z 5): 24 model pixels with the antenna,
     * at 0.34 about 8.2 pixels, its legs hidden under the brass sill (top at 9.25) so it rises from it at the waist, its back sunk
     * into the wall - fused into the bus; the antenna's ball ends at 14.8, under the alcove's roof.
     */
    private static final float GOLEM_SCALE = 0.34f, GOLEM_Y = 6.6f, GOLEM_Z = 4.2f;
    /** The block's path: from inside the mouth (centre 8, 4.5, 3) to the centre of the spot in front (8, 8, -8). */
    private static final float MOUTH_Y = 4.5f, MOUTH_Z = 3f;

    public FormationBusRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * The light the golem in the bus is lit by: the brightest round the bus but behind it (the glass side windows let it in) - the
     * spot in front alone goes dark as soon as a whole block stands there.
     */
    static int light(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Direction facing) {
        int block = 0, sky = 0;
        for (Direction d : Direction.values()) {
            if (d != facing.getOpposite()) {
                int c = LevelRenderer.getLightCoords(level, pos.relative(d));
                block = Math.max(block, net.minecraft.util.LightCoordsUtil.block(c));
                sky = Math.max(sky, net.minecraft.util.LightCoordsUtil.sky(c));
            }
        }
        return net.minecraft.util.LightCoordsUtil.pack(block, sky);
    }

    /** Turns the frame from "facing north" to the block's facing (as the blockstate turns the model). */
    static void orient(PoseStack pose, Direction facing) {
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

    @Override
    public void render(FormationBusBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(DirectionalBlock.FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(DirectionalBlock.FACING);
        int front = light(be.getLevel(), be.getBlockPos(), facing);
        float time = be.getLevel().getGameTime() + partialTick;
        ItemStack carrying = be.carrying();
        float push = be.push(partialTick);
        // the push in three beats: take hold (0..0.2), drive it out (0.2..0.85), let go (0.85..1)
        float drive = Mth.clamp((push - 0.2f) / 0.65f, 0f, 1f);
        float out = drive * drive * (3f - 2f * drive);
        boolean holding = !carrying.isEmpty();

        pose.pushPose();
        orient(pose, facing);
        float armX = holding ? -0.9f - 0.75f * out + (push > 0.85f ? 0.6f * (push - 0.85f) / 0.15f : 0f) : -0.25f + 0.05f * Mth.sin(time * 0.08f);
        float headYaw = holding ? 0f : Mth.sin(time * 0.03f) * 0.4f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(armX, 0.18f, armX, -0.18f, headYaw);
        CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, holding ? 0.15f + 0.35f * out : 0f);
        CopperGolemPuppet.render(pose, buffers, front, new Vector3f(0.5f, GOLEM_Y / 16f, GOLEM_Z / 16f), new Vector3f(0f, 0f, -1f),
                new Vector3f(0f, 1f, 0f), GOLEM_SCALE, anim, 0, null, arms);

        if (holding && carrying.getItem() instanceof BlockItem item) {
            BlockState state = item.getBlock().defaultBlockState();
            float size = Mth.lerp(out, 0.5f, 1f);
            float cy = Mth.lerp(out, MOUTH_Y, 8f) / 16f, cz = Mth.lerp(out, MOUTH_Z, -8f) / 16f;
            pose.pushPose();
            pose.translate(0.5f, cy, cz);
            pose.scale(size, size, size);
            pose.translate(-0.5f, -0.5f, -0.5f);
            com.golemlogix.client.render.BerBuffers.block(buffers, state, pose, front);
            pose.popPose();
        }
        pose.popPose();
    }

    /** The block it pushes out reaches into the spot in front. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(FormationBusBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }
}
