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

    /**
     * Geometry built once and then only replayed, for machines with thousands of faces: nothing is computed or stored again per frame,
     * and the vertices go straight to the game. Build it with bake, with the pose and the light of no account.
     */
    public static final class Baked {
        private final Recorder recorder;

        private Baked(Recorder recorder) {
            this.recorder = recorder;
        }

        /** Draws it where the pose stands now. tint: replaces the colour it was built with, 0 keeps that. */
        public void draw(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, int light, int tint) {
            if (buffers instanceof BerBuffers b && recorder.count > 0) {
                org.joml.Matrix4f m = new org.joml.Matrix4f(pose.pose());
                org.joml.Matrix3f n = new org.joml.Matrix3f(pose.normal());
                b.collector.submitCustomGeometry(new PoseStack(), type, (p, vc) -> recorder.replay(vc, m, n, light, tint));
            }
        }
    }

    public static Baked bake(java.util.function.BiConsumer<VertexConsumer, PoseStack> draw) {
        Recorder recorder = new Recorder();
        draw.accept(recorder, new PoseStack());
        return new Baked(recorder);
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

        void replay(VertexConsumer vc, org.joml.Matrix4f m, org.joml.Matrix3f n3, int light, int tint) {
            for (int n = 0; n < count; n++) {
                int b = n * 9, c = n * 3;
                float x = f[b], y = f[b + 1], z = f[b + 2], nx = f[b + 6], ny = f[b + 7], nz = f[b + 8];
                vc.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                                m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                        .setColor(tint != 0 ? tint : i[c]).setUv(f[b + 3], f[b + 4]).setOverlay(i[c + 1]).setLight(light)
                        .setNormal(n3.m00() * nx + n3.m10() * ny + n3.m20() * nz, n3.m01() * nx + n3.m11() * ny + n3.m21() * nz,
                                n3.m02() * nx + n3.m12() * ny + n3.m22() * nz);
            }
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
