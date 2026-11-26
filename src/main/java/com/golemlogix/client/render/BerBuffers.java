package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.util.Mth;

import java.util.ArrayDeque;
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
    /** Sentinel light value for {@link Baked#draw}: preserves each vertex's baked light coordinate instead of overriding it. */
    public static final int KEEP_LIGHT = -1;

    private static final BlockDisplayContext BLOCK_CONTEXT = BlockDisplayContext.create();
    private static final PoseStack IDENTITY = new PoseStack();
    private static final PoseStack BAKED_POSE = new PoseStack();
    /** Reusable on the render thread: BlockModelResolver#update clears it and submitMultiLayer copies its parts and tints. */
    private static final BlockModelRenderState BLOCK_STATE = new BlockModelRenderState();
    private static final Vector3f SCRATCH_POS = new Vector3f();
    private static final Vector3f SCRATCH_NORM = new Vector3f();

    /** Small render-thread pool of BerBuffers wrappers (handles nested submissions without per-BE allocations). */
    private static final ArrayDeque<BerBuffers> BUFFER_POOL = new ArrayDeque<>(8);
    /**
     * Render-thread ring arena of vertex {@link Recorder}s so steady-state frames allocate zero float[]/int[] buffers
     * and never trigger {@code Arrays.copyOf} churn. Sized well above a single frame's (BE x RenderType) count including
     * Iris shadow passes; slots still marked {@code inUse} from the current tick are skipped safely.
     */
    private static final int RING_SIZE = 2048;
    private static final int RING_MASK = RING_SIZE - 1;
    private static final Recorder[] RING = new Recorder[RING_SIZE];
    private static int ringCursor = 0;
    private static long clientTickCounter = 0;

    private SubmitNodeCollector collector;
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>(4);
    @Nullable
    private RenderType lastType;
    @Nullable
    private Recorder lastRecorder;

    public BerBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    /** Acquires a reusable render-thread {@link BerBuffers} bound to {@code collector}. */
    public static BerBuffers acquire(SubmitNodeCollector collector) {
        BerBuffers b = BUFFER_POOL.pollLast();
        if (b == null) {
            return new BerBuffers(collector);
        }
        b.collector = collector;
        return b;
    }

    /** Advances the tick epoch so any recorder dropped by a non-rendering custom collector can be reclaimed safely. */
    public static void clientTick() {
        clientTickCounter++;
    }

    /** Releases pooled arena buffers on client disconnect. */
    public static void clearPool() {
        Arrays.fill(RING, null);
        ringCursor = 0;
        BUFFER_POOL.clear();
    }

    private static Recorder acquireRecorder(boolean fastEntityFormat) {
        int idx = ringCursor++ & RING_MASK;
        Recorder r = RING[idx];
        if (r == null) {
            r = new Recorder(Recorder.POOL_INITIAL_VERTICES);
            RING[idx] = r;
        } else if (r.inUse && (clientTickCounter - r.acquiredTick) < 2L) {
            // More than RING_SIZE active submissions in flight at once: fall back to a transient recorder without clobbering.
            r = new Recorder(Recorder.POOL_INITIAL_VERTICES);
        }
        r.prepare(clientTickCounter, fastEntityFormat);
        return r;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        if (renderType == lastType || renderType.equals(lastType)) {
            return lastRecorder;
        }
        Recorder r = recorders.get(renderType);
        if (r == null) {
            r = acquireRecorder(renderType.format() == DefaultVertexFormat.ENTITY);
            recorders.put(renderType, r);
        }
        lastType = renderType;
        lastRecorder = r;
        return r;
    }

    /** Hands everything recorded to the collector (vertices are already in the frame of the pose the renderer got). */
    public void flush() {
        if (recorders.isEmpty()) {
            return;
        }
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
                target.submitCustomGeometry(IDENTITY, type, r);
            } else {
                r.inUse = false;
            }
        }
        recorders.clear();
        lastType = null;
        lastRecorder = null;
    }

    /** Flushes recorded geometry and returns this wrapper to the render-thread pool. */
    public void flushAndRelease() {
        flush();
        collector = null;
        if (BUFFER_POOL.size() < 16) {
            BUFFER_POOL.addLast(this);
        }
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
        /** Small repeated props merge into the block entity's buffer to avoid per-prop PoseStack/lambda submissions. */
        private static final int DIRECT_SUBMIT_THRESHOLD = 384;
        private final Recorder recorder;

        private Baked(Recorder recorder) {
            this.recorder = recorder;
        }

        /** Draws it where the pose stands now. tint: replaces the colour it was built with, 0 keeps that. */
        public void draw(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, int light, int tint) {
            if (buffers instanceof BerBuffers b && recorder.count > 0) {
                if (recorder.count <= DIRECT_SUBMIT_THRESHOLD) {
                    recorder.replay(b.getBuffer(type), pose, light, tint, true);
                } else {
                    boolean fastEntity = type.format() == DefaultVertexFormat.ENTITY;
                    BAKED_POSE.last().set(pose);
                    b.collector.submitCustomGeometry(BAKED_POSE, type, (p, vc) -> recorder.replay(vc, p, light, tint, fastEntity));
                }
            }
        }
    }

    public static Baked bake(BiConsumer<VertexConsumer, PoseStack> draw) {
        Recorder recorder = new Recorder(64);
        draw.accept(recorder, new PoseStack());
        recorder.trim();
        return new Baked(recorder);
    }

    /** Vertices of one render type: position (3f), texture (2f), normal (3f) = 8 floats; colour, overlay, light = 3 ints. */
    private static final class Recorder implements VertexConsumer, SubmitNodeCollector.CustomGeometryRenderer {
        static final int POOL_INITIAL_VERTICES = 128;
        private static final int MAX_RETAINED_VERTICES = 4096;
        private static final int STRIDE_F = 8;

        private float[] f;
        private int[] i;
        private int count;
        boolean inUse;
        long acquiredTick;
        boolean fastEntityFormat;

        Recorder(int initialVertices) {
            this.f = new float[STRIDE_F * initialVertices];
            this.i = new int[3 * initialVertices];
        }

        void prepare(long tick, boolean fastEntityFormat) {
            if (f.length > STRIDE_F * MAX_RETAINED_VERTICES) {
                f = new float[STRIDE_F * MAX_RETAINED_VERTICES];
                i = new int[3 * MAX_RETAINED_VERTICES];
            }
            count = 0;
            inUse = true;
            acquiredTick = tick;
            this.fastEntityFormat = fastEntityFormat;
        }

        private int base() {
            return (count - 1) * STRIDE_F;
        }

        private void grow() {
            f = Arrays.copyOf(f, f.length * 2);
            i = Arrays.copyOf(i, i.length * 2);
        }

        void trim() {
            int fn = count * STRIDE_F;
            if (f.length > fn) {
                f = Arrays.copyOf(f, fn);
                i = Arrays.copyOf(i, count * 3);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            int idx = count++;
            int b = idx * STRIDE_F;
            int c = idx * 3;
            if (b + STRIDE_F > f.length) {
                grow();
            }
            f[b] = x;
            f[b + 1] = y;
            f[b + 2] = z;
            f[b + 3] = 0f;
            f[b + 4] = 0f;
            f[b + 5] = 0f;
            f[b + 6] = 1f;
            f[b + 7] = 0f;
            i[c] = -1;
            i[c + 1] = OverlayTexture.NO_OVERLAY;
            i[c + 2] = LightCoordsUtil.FULL_BRIGHT;
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords,
                              float nx, float ny, float nz) {
            int idx = count++;
            int b = idx * STRIDE_F;
            int c = idx * 3;
            if (b + STRIDE_F > f.length) {
                grow();
            }
            f[b] = x;
            f[b + 1] = y;
            f[b + 2] = z;
            f[b + 3] = u;
            f[b + 4] = v;
            f[b + 5] = nx;
            f[b + 6] = ny;
            f[b + 7] = nz;
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
            f[b + 5] = x;
            f[b + 6] = y;
            f[b + 7] = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        void replay(VertexConsumer vc, PoseStack.Pose pose, int light, int tint, boolean fastEntity) {
            Matrix4f m = pose.pose();
            float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
            float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
            float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
            float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
            Matrix3f nm = pose.normal();
            float n00 = nm.m00(), n01 = nm.m01(), n02 = nm.m02();
            float n10 = nm.m10(), n11 = nm.m11(), n12 = nm.m12();
            float n20 = nm.m20(), n21 = nm.m21(), n22 = nm.m22();
            boolean unitNormal = Math.abs(n00 * n00 + n01 * n01 + n02 * n02 - 1f) < 1e-3f
                    && Math.abs(n10 * n10 + n11 * n11 + n12 * n12 - 1f) < 1e-3f
                    && Math.abs(n20 * n20 + n21 * n21 + n22 * n22 - 1f) < 1e-3f;
            float[] fv = this.f;
            int[] iv = this.i;
            int total = this.count;
            for (int n = 0, b = 0, c = 0; n < total; n++, b += STRIDE_F, c += 3) {
                float x = fv[b], y = fv[b + 1], z = fv[b + 2];
                float nx = fv[b + 5], ny = fv[b + 6], nz = fv[b + 7];
                float tnx = n00 * nx + n10 * ny + n20 * nz;
                float tny = n01 * nx + n11 * ny + n21 * nz;
                float tnz = n02 * nx + n12 * ny + n22 * nz;
                if (!unitNormal) {
                    float lenSq = tnx * tnx + tny * tny + tnz * tnz;
                    if (lenSq > 1e-8f) {
                        float inv = Mth.invSqrt(lenSq);
                        tnx *= inv;
                        tny *= inv;
                        tnz *= inv;
                    }
                }
                float px = m00 * x + m10 * y + m20 * z + m30;
                float py = m01 * x + m11 * y + m21 * z + m31;
                float pz = m02 * x + m12 * y + m22 * z + m32;
                int col = tint != 0 ? tint : iv[c];
                int lgt = light >= 0 ? light : iv[c + 2];
                if (fastEntity) {
                    vc.addVertex(px, py, pz, col, fv[b + 3], fv[b + 4], iv[c + 1], lgt, tnx, tny, tnz);
                } else {
                    vc.addVertex(px, py, pz).setColor(col).setUv(fv[b + 3], fv[b + 4])
                            .setOverlay(iv[c + 1]).setLight(lgt).setNormal(tnx, tny, tnz);
                }
            }
        }

        @Override
        public void render(PoseStack.Pose pose, VertexConsumer vc) {
            replay(vc);
            inUse = false;
        }

        void replay(VertexConsumer vc) {
            float[] fv = this.f;
            int[] iv = this.i;
            int total = this.count;
            // BufferBuilder's 11-arg fastFormat skips writing normals when fullFormat == false (DefaultVertexFormat.BLOCK),
            // so only use 11-arg direct call on DefaultVertexFormat.ENTITY where fullFormat == true.
            if (fastEntityFormat) {
                for (int n = 0, b = 0, c = 0; n < total; n++, b += STRIDE_F, c += 3) {
                    vc.addVertex(fv[b], fv[b + 1], fv[b + 2], iv[c], fv[b + 3], fv[b + 4], iv[c + 1], iv[c + 2],
                            fv[b + 5], fv[b + 6], fv[b + 7]);
                }
            } else {
                for (int n = 0, b = 0, c = 0; n < total; n++, b += STRIDE_F, c += 3) {
                    vc.addVertex(fv[b], fv[b + 1], fv[b + 2]).setColor(iv[c]).setUv(fv[b + 3], fv[b + 4])
                            .setOverlay(iv[c + 1]).setLight(iv[c + 2]).setNormal(fv[b + 5], fv[b + 6], fv[b + 7]);
                }
            }
        }
    }
}
