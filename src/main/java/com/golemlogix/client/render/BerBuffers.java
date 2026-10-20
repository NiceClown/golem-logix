package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's renderers still draw the 1.21.1 way, into a MultiBufferSource with already transformed vertices. Since 1.21.9 the game
 * collects what a block entity draws first and renders it later: this records the vertices of every render type and hands each
 * set to the collector as custom geometry; items and blocks go straight to the collector (they have their own submissions now).
 */
public final class BerBuffers implements MultiBufferSource {
    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    private final SubmitNodeCollector collector;
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    public BerBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return recorders.computeIfAbsent(renderType, t -> new Recorder());
    }

    /** Hands everything recorded to the collector (vertices are already in the frame of the pose the renderer got). */
    public void flush() {
        PoseStack identity = new PoseStack();
        for (Map.Entry<RenderType, Recorder> e : recorders.entrySet()) {
            Recorder r = e.getValue();
            if (r.count > 0) {
                collector.submitCustomGeometry(identity, e.getKey(), (pose, vc) -> r.replay(vc));
            }
        }
        recorders.clear();
    }

    /** An item drawn where the pose stands now (was ItemRenderer#renderStatic). */
    public static void item(MultiBufferSource buffers, ItemStack stack, ItemDisplayContext context, int light, PoseStack pose,
                            @Nullable Level level, int seed) {
        if (stack.isEmpty() || !(buffers instanceof BerBuffers b)) {
            return;
        }
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        state.submit(pose, b.collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** A block model drawn where the pose stands now (was BlockRenderDispatcher#renderSingleBlock). */
    public static void block(MultiBufferSource buffers, BlockState blockState, PoseStack pose, int light) {
        if (!(buffers instanceof BerBuffers b)) {
            return;
        }
        BlockModelRenderState state = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(state, blockState, BLOCK_CONTEXT);
        state.submitMultiLayer(pose, b.collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    /** Vertices of one render type: position, colour, texture, overlay, light, normal. */
    private static final class Recorder implements VertexConsumer {
        private float[] f = new float[9 * 128];
        private int[] i = new int[3 * 128];
        private int count;

        private int base() {
            return (count - 1) * 9;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            count++;
            if (count * 9 > f.length || count * 3 > i.length) {
                f = java.util.Arrays.copyOf(f, f.length * 2);
                i = java.util.Arrays.copyOf(i, i.length * 2);
            }
            int b = base();
            f[b] = x;
            f[b + 1] = y;
            f[b + 2] = z;
            f[b + 3] = 0f;
            f[b + 4] = 0f;
            f[b + 5] = 0f;
            f[b + 6] = 1f;
            f[b + 7] = 0f;
            f[b + 8] = 1f;
            int c = (count - 1) * 3;
            i[c] = -1;
            i[c + 1] = OverlayTexture.NO_OVERLAY;
            i[c + 2] = 0xF000F0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            i[(count - 1) * 3] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            i[(count - 1) * 3] = color;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            f[base() + 3] = u;
            f[base() + 4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            i[(count - 1) * 3 + 1] = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            i[(count - 1) * 3 + 2] = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            f[base() + 6] = x;
            f[base() + 7] = y;
            f[base() + 8] = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            f[base() + 5] = width;
            return this;
        }

        void replay(VertexConsumer vc) {
            for (int n = 0; n < count; n++) {
                int b = n * 9, c = n * 3;
                vc.addVertex(f[b], f[b + 1], f[b + 2]).setColor(i[c]).setUv(f[b + 3], f[b + 4]).setOverlay(i[c + 1]).setLight(i[c + 2])
                        .setNormal(f[b + 6], f[b + 7], f[b + 8]);
            }
        }
    }
}
