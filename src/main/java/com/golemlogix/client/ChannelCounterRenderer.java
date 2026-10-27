package com.golemlogix.client;

import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blocks.ChannelCounterBlock;
import com.golemlogix.client.render.NixieDigits;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Vector3f;

/**
 * Channel Counter: the digits in its three nixie tubes, on both sides of the meshes so they read from the front and from behind
 * (the base, the tubes and their meshes are the baked model, moved as the mount moves them). The channels through the node glow in the load colours, its
 * capacity in the classic nixie orange; leading zeros stay dark, and with no network every tube is dark.
 */
public class ChannelCounterRenderer extends com.golemlogix.client.render.LegacyBer<ChannelCounterBlockEntity> {
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
        String text = String.valueOf(Math.min(999, be.showsCapacity() ? be.capacity() : be.used()));
        int color = be.showsCapacity() ? NIXIE_ORANGE : NixieDigits.loadColor(be.used(), be.capacity());
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(ChannelCounterBlock.FACING));
        Vector3f up = new Vector3f(0, 1, 0);
        ChannelCounterBlock.Mount mount = be.getBlockState().getValue(ChannelCounterBlock.MOUNT);
        float y = DIGIT_Y + mount.dy, z = MESH_Z + mount.dz;
        for (int place = 0; place < text.length(); place++) {
            int digit = text.charAt(text.length() - 1 - place) - '0';
            // from the front (north) the ones are on the right, at the smallest x; from behind, at the largest
            NixieDigits.digit(pose.last(), buffers, digit, new Vector3f(TUBE_X[place], y, z - GAP), DIGIT_W, DIGIT_H,
                    new Vector3f(-1, 0, 0), up, color);
            NixieDigits.digit(pose.last(), buffers, digit, new Vector3f(TUBE_X[2 - place], y, z + GAP), DIGIT_W, DIGIT_H,
                    new Vector3f(1, 0, 0), up, color);
        }
        pose.popPose();
    }
}
