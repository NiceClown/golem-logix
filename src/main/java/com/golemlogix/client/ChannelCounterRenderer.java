package com.golemlogix.client;

import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blocks.ChannelCounterBlock;
import com.golemlogix.client.render.NixieDigits;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Vector3f;

/**
 * Channel Counter: the digits in its four nixie tubes (the base, the tubes and their meshes are the baked model). Channels in use
 * glow in the load colours, the capacity in the classic nixie orange. Leading zeros stay dark, and an offline network leaves every
 * tube dark.
 */
public class ChannelCounterRenderer extends com.golemlogix.client.render.LegacyBer<ChannelCounterBlockEntity> {
    private static final int NIXIE_ORANGE = 0xFFFF8A2A;

    public ChannelCounterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ChannelCounterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.online() || !be.getBlockState().hasProperty(ChannelCounterBlock.FACING)) {
            return;
        }
        String text = String.valueOf(Math.min(9999, be.showsCapacity() ? be.capacity() : be.used()));
        int color = be.showsCapacity() ? NIXIE_ORANGE : NixieDigits.loadColor(be.used(), be.capacity());
        pose.pushPose();
        Quads.faceModel(pose, be.getBlockState().getValue(ChannelCounterBlock.FACING));
        // the model's tubes stand at x 1.25 + i * 3.5 (3 wide) with their meshes at z 8; the digits face north just in front of them,
        // and seen from the north the tube at the smallest x is on the right: it shows the ones
        Vector3f right = new Vector3f(-1, 0, 0), up = new Vector3f(0, 1, 0);
        for (int place = 0; place < text.length(); place++) {
            int digit = text.charAt(text.length() - 1 - place) - '0';
            float x = 1.25f + place * 3.5f + 1.5f;
            NixieDigits.digit(pose.last(), buffers, digit, new Vector3f(x, 8.4f, 7.88f), 2.6f, 6.6f, right, up, color);
        }
        pose.popPose();
    }
}
