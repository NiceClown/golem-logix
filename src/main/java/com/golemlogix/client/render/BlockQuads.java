package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** A block's model straight into one buffer (no culling, no tint): the build projection's ghosts. */
public final class BlockQuads {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private BlockQuads() {
    }

    public static void draw(PoseStack.Pose pose, VertexConsumer buffer, BlockState state, int light, int overlay) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42L), parts);
        QuadInstance instance = new QuadInstance();
        instance.setColor(0xFFFFFFFF);
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                for (BakedQuad quad : part.getQuads(side)) {
                    buffer.putBakedQuad(pose, quad, instance);
                }
            }
        }
    }
}
