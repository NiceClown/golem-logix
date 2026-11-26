package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The mod's renderers still draw the 1.21.1 way, into a MultiBufferSource with already transformed vertices. Since 1.21.9 the game
 * collects what a block entity draws first and renders it later: this records the vertices of every render type and hands each
 * set to the collector as custom geometry; items and blocks go straight to the collector (they have their own submissions now).
 */
public final class BerBuffers implements MultiBufferSource {
    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    private static final PoseStack IDENTITY = new PoseStack();
    private static final PoseStack BAKED_POSE = new PoseStack();
    /** Reusable on the render thread: BlockModelResolver#update clears it and submitMultiLayer copies its parts and tints. */
    private static final BlockModelRenderState BLOCK_STATE = new BlockModelRenderState();
    private static final Vector3f SCRATCH_POS = new Vector3f();
    private static final Vector3f SCRATCH_NORM = new Vector3f();

    private final SubmitNodeCollector collector;
    @Nullable
    private RenderType firstType;
    @Nullable
    private Recorder firstRecorder;
    @Nullable
    private Map<RenderType, Recorder> recorders;

    public BerBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (firstType == null) {
            firstType = renderType;
            firstRecorder = new Recorder();
            return firstRecorder;
        }
        if (firstType.equals(renderType)) {
            return firstRecorder;
        }
        if (recorders == null) {
            recorders = new LinkedHashMap<>(4);
            recorders.put(firstType, firstRecorder);
        }
        return recorders.computeIfAbsent(renderType, t -> new Recorder());
    }

    /** Hands everything recorded to the collector (vertices are already in the frame of the pose the renderer got). */
    public void flush() {
        if (firstType == null) {
            return;
        }
        if (recorders == null) {
            Recorder r = firstRecorder;
            if (r.count > 0) {
                collector.submitCustomGeometry(IDENTITY, firstType, (pose, vc) -> r.replay(vc));
            }
        } else {
            // CustomFeatureRenderer stores blended submits in a HashMap<RenderType, List>, so two blended types at the
            // same order (e.g. nixie digits inside translucent lamp glass, or tape glow behind drive-bay glass) draw in
            // hashCode order and the glass depth-culls what sits behind it ~50% of launches. Stepping collector.order()
            // across blended types preserves each renderer's submission order deterministically.
            int blendedOrder = 0;
            for (Map.Entry<RenderType, Recorder> e : recorders.entrySet()) {
                Recorder r = e.getValue();
                if (r.count > 0) {
                    RenderType type = e.getKey();
                    OrderedSubmitNodeCollector target = type.hasBlending() && blendedOrder > 0
                            ? collector.order(blendedOrder)
                            : collector;
                    if (type.hasBlending()) {
                        blendedOrder++;
                    }
                    target.submitCustomGeometry(IDENTITY, type, (pose, vc) -> r.replay(vc));
                }
            }
            recorders.clear();
        }
        firstType = null;
        firstRecorder = null;
    }

    /** An item drawn where the pose stands now (was ItemRenderer#renderStatic). */
    public static void item(MultiBufferSource buffers, ItemStack stack, ItemDisplayContext context, int light, PoseStack pose,
                            @Nullable Level level, int seed) {
        if (stack.isEmpty() || !(buffers instanceof BerBuffers b)) {
            return;
        }
        // ItemStackRenderState hands its internal quad list directly to SubmitNodeCollector without copying, so each call needs its own instance.
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        state.submit(pose, b.collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** A block model drawn where the pose stands now (was BlockRenderDispatcher#renderSingleBlock). */
    public static void block(MultiBufferSource buffers, BlockState blockState, PoseStack pose, int light) {
        if (!(buffers instanceof BerBuffers b)) {
            return;
        }
        Minecraft.getInstance().getBlockModelResolver().update(BLOCK_STATE, blockState, BLOCK_CONTEXT);
        BLOCK_STATE.submitMultiLayer(pose, b.collector, light, OverlayTexture.NO_OVERLAY, 0);
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
                BAKED_POSE.last().set(pose);
                b.collector.submitCustomGeometry(BAKED_POSE, type, (p, vc) -> recorder.replay(vc, p, light, tint));
            }
        }
    }

    public static Baked bake(BiConsumer<VertexConsumer, PoseStack> draw) {
        Recorder recorder = new Recorder();
        draw.accept(recorder, new PoseStack());
        recorder.trim();
        return new Baked(recorder);
    }

    /** Vertices of one render type: position, colour, texture, overlay, light, normal. */
    private static final class Recorder implements VertexConsumer {
        private static final int INITIAL_VERTICES = 16;
        private float[] f = new float[9 * INITIAL_VERTICES];
        private int[] i = new int[3 * INITIAL_VERTICES];
        private int count;

        private int base() {
            return (count - 1) * 9;
        }

        private void grow() {
            f = Arrays.copyOf(f, f.length * 2);
            i = Arrays.copyOf(i, i.length * 2);
        }

        void trim() {
            int fn = count * 9;
            if (f.length > fn) {
                f = Arrays.copyOf(f, fn);
                i = Arrays.copyOf(i, count * 3);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            int idx = count++;
            int b = idx * 9;
            int c = idx * 3;
            if (b + 9 > f.length) {
                grow();
            }
            f[b] = x;
            f[b + 1] = y;
            f[b + 2] = z;
            f[b + 3] = 0f;
            f[b + 4] = 0f;
            f[b + 5] = 0f;
            f[b + 6] = 0f;
            f[b + 7] = 1f;
            f[b + 8] = 0f;
            i[c] = -1;
            i[c + 1] = OverlayTexture.NO_OVERLAY;
            i[c + 2] = 0xF000F0;
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords,
                              float nx, float ny, float nz) {
            int idx = count++;
            int b = idx * 9;
            int c = idx * 3;
            if (b + 9 > f.length) {
                grow();
            }
            f[b] = x;
            f[b + 1] = y;
            f[b + 2] = z;
            f[b + 3] = u;
            f[b + 4] = v;
            f[b + 5] = 0f;
            f[b + 6] = nx;
            f[b + 7] = ny;
            f[b + 8] = nz;
            i[c] = color;
            i[c + 1] = overlayCoords;
            i[c + 2] = lightCoords;
        }

        @Override
        public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            pose.transformPosition(x, y, z, SCRATCH_POS);
            return addVertex(SCRATCH_POS.x, SCRATCH_POS.y, SCRATCH_POS.z);
        }

        @Override
        public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
            pose.transformNormal(x, y, z, SCRATCH_NORM);
            return setNormal(SCRATCH_NORM.x, SCRATCH_NORM.y, SCRATCH_NORM.z);
        }

        @Override
        public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
            Vector3fc normalVec = quad.direction().getUnitVec3f();
            Matrix4f matrix = pose.pose();
            Vector3f normal = pose.transformNormal(normalVec, SCRATCH_NORM);
            int lightEmission = quad.materialInfo().lightEmission();
            int overlay = instance.overlayCoords();
            for (int vertex = 0; vertex < 4; vertex++) {
                Vector3fc position = quad.position(vertex);
                long packedUv = quad.packedUV(vertex);
                int vertexColor = ARGB.multiply(instance.getColor(vertex), quad.bakedColors().color(vertex));
                int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
                matrix.transformPosition(position, SCRATCH_POS);
                float u = UVPair.unpackU(packedUv);
                float v = UVPair.unpackV(packedUv);
                applyBakedNormals(normal, quad.bakedNormals(), vertex, pose.normal());
                addVertex(SCRATCH_POS.x, SCRATCH_POS.y, SCRATCH_POS.z, vertexColor, u, v, overlay, light, normal.x, normal.y, normal.z);
            }
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
            int b = base();
            f[b + 3] = u;
            f[b + 4] = v;
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
            int b = base();
            f[b + 6] = x;
            f[b + 7] = y;
            f[b + 8] = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            f[base() + 5] = width;
            return this;
        }

        void replay(VertexConsumer vc, PoseStack.Pose pose, int light, int tint) {
            Matrix4f m = pose.pose();
            for (int n = 0; n < count; n++) {
                int b = n * 9, c = n * 3;
                float x = f[b], y = f[b + 1], z = f[b + 2];
                pose.transformNormal(f[b + 6], f[b + 7], f[b + 8], SCRATCH_NORM);
                vc.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                                m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                                m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                        .setColor(tint != 0 ? tint : i[c]).setUv(f[b + 3], f[b + 4]).setOverlay(i[c + 1]).setLight(light)
                        .setNormal(SCRATCH_NORM.x, SCRATCH_NORM.y, SCRATCH_NORM.z);
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
