package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.function.BiConsumer;

/**
 * Geometry built once and then only replayed, for machines with thousands of faces: nothing is computed again per frame, the stored
 * vertices go straight into the buffer of the render type, moved by the pose. Build it with bake, with the pose and the light of no
 * account (the light is given when it is drawn, or preserved when {@link #KEEP_LIGHT} is passed).
 */
public final class Baked {
    /** Sentinel light value for {@link #draw}: preserves each vertex's baked light coordinate instead of overriding it. */
    public static final int KEEP_LIGHT = -1;

    private final Recorder recorder;

    private Baked(Recorder recorder) {
        this.recorder = recorder;
    }

    /** Records what draw puts into a vertex consumer, under an identity pose. */
    public static Baked bake(BiConsumer<VertexConsumer, PoseStack> draw) {
        Recorder recorder = new Recorder(64);
        draw.accept(recorder, new PoseStack());
        recorder.trim();
        return new Baked(recorder);
    }

    /** Emits all baked quads of a {@link BlockState}'s model into {@code vc} for one-time mesh baking. */
    public static void drawBlock(PoseStack.Pose pose, VertexConsumer vc, BlockState state) {
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        dispatcher.getModelRenderer().renderModel(pose, vc, state, model, 1f, 1f, 1f, 0,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** Emits all baked quads of a standalone {@link ModelResourceLocation} into {@code vc} for one-time mesh baking. */
    public static void drawModel(PoseStack.Pose pose, VertexConsumer vc, ModelResourceLocation id) {
        var mc = Minecraft.getInstance();
        BakedModel model = mc.getModelManager().getModel(id);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose, vc, null, model, 1f, 1f, 1f, 0,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** Draws it where the pose stands now. tint: replaces the colour it was built with, 0 keeps that. */
    public void draw(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, int light, int tint) {
        if (recorder.count > 0) {
            recorder.replay(buffers.getBuffer(type), pose, light, tint);
        }
    }

    /** Vertices of one render type: position (3f), texture (2f), normal (3f) = 8 floats; colour, overlay, light = 3 ints. */
    private static final class Recorder implements VertexConsumer {
        private static final int STRIDE_F = 8;

        private float[] f;
        private int[] i;
        private int count;

        Recorder(int initialVertices) {
            this.f = new float[STRIDE_F * initialVertices];
            this.i = new int[3 * initialVertices];
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
            i[c + 2] = LightTexture.FULL_BRIGHT;
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

        void replay(VertexConsumer vc, PoseStack.Pose pose, int light, int tint) {
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
                vc.addVertex(px, py, pz).setColor(col).setUv(fv[b + 3], fv[b + 4])
                        .setOverlay(iv[c + 1]).setLight(lgt).setNormal(tnx, tny, tnz);
            }
        }
    }
}
