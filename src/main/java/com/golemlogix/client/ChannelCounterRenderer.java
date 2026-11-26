package com.golemlogix.client;

import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blocks.ChannelCounterBlock;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.client.render.NixieDigits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Channel Counter: the digits in its three nixie tubes, on both sides of the meshes so they read from the front and from behind
 * (the base, the tubes and their meshes are the baked model, moved as the mount moves them). The channels through the node glow in the load colours, its
 * capacity in the classic nixie orange; leading zeros stay dark, and with no network every tube is dark.
 */
public class ChannelCounterRenderer extends LegacyBer<ChannelCounterBlockEntity> {
    private static final int NIXIE_ORANGE = 0xFFFF8A2A;
    /** The tubes of the model: centres along x, the mesh plane at z 8, the digit's centre height and size (pixels). */
    private static final float[] TUBE_X = {5.55f, 8.0f, 10.45f};
    private static final float MESH_Z = 8f, DIGIT_Y = 5.6f, DIGIT_W = 1.9f, DIGIT_H = 4.6f, GAP = 0.13f;

    public ChannelCounterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ChannelCounterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.online() || !be.getBlockState().hasProperty(ChannelCounterBlock.MOUNT)) {
            return;
        }
        int val = Math.clamp(be.showsCapacity() ? be.capacity() : be.used(), 0, 999);
        int color = be.showsCapacity() ? NIXIE_ORANGE : NixieDigits.loadColor(be.used(), be.capacity());
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(ChannelCounterBlock.FACING));
        ChannelCounterBlock.Mount mount = be.getBlockState().getValue(ChannelCounterBlock.MOUNT);
        float y = DIGIT_Y + mount.dy, z = MESH_Z + mount.dz;
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = NixieDigits.buffer(buffers);
        int place = 0;
        do {
            int digit = val % 10;
            val /= 10;
            // from the front (north) the ones are on the right, at the smallest x; from behind, at the largest
            NixieDigits.digit(p, vc, digit, TUBE_X[place], y, z - GAP, DIGIT_W, DIGIT_H, -1f, 0f, 0f, 0f, 1f, 0f, color);
            NixieDigits.digit(p, vc, digit, TUBE_X[2 - place], y, z + GAP, DIGIT_W, DIGIT_H, 1f, 0f, 0f, 0f, 1f, 0f, color);
            place++;
        } while (val > 0);
        pose.popPose();
    }
}
