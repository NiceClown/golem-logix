package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity renderer written the 1.21.1 way (draw the block entity into buffers), on top of the 1.21.9+ two-step one: the
 * render state keeps the block entity and the partial tick, and at submission the old drawing runs into {@link BerBuffers}.
 */
public abstract class LegacyBer<T extends BlockEntity> implements BlockEntityRenderer<T, LegacyBer.State<T>> {
    public static class State<T> extends BlockEntityRenderState {
        @Nullable
        public T blockEntity;
        public float partialTick;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T blockEntity, State<T> state, float partialTick, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.blockEntity == null || state.blockEntity.getLevel() == null) {
            return;
        }
        BerBuffers buffers = new BerBuffers(collector);
        render(state.blockEntity, state.partialTick, poseStack, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        buffers.flush();
    }

    /** The drawing, as before: pose at the block's corner, vertices into the buffers. */
    public abstract void render(T blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay);
}
