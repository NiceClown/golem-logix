package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.function.BiConsumer;

/**
 * Geometry built once and then only replayed, for machines with thousands of faces: nothing is computed again per frame, the stored
 * vertices go straight into the buffer of the render type, moved by the pose. Build it with bake, with the pose and the light of no
 * account (the light is given when it is drawn).
 */
public final class Baked {
    private final Recorder recorder;

    private Baked(Recorder recorder) {
        this.recorder = recorder;
    }

    /** Records what draw puts into a vertex consumer, under an identity pose. */
    public static Baked bake(BiConsumer<VertexConsumer, PoseStack> draw) {
        Recorder recorder = new Recorder();
        draw.accept(recorder, new PoseStack());
        return new Baked(recorder);
    }

    /** Draws it where the pose stands now. tint: replaces the colour it was built with, 0 keeps that. */
    public void draw(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, int light, int tint) {
        if (recorder.count > 0) {
            recorder.replay(buffers.getBuffer(type), pose.pose(), pose.normal(), light, tint);
        }
    }

    /** Vertices of one render type: position, colour, texture, overlay, light, normal. */
    private static final class Recorder implements VertexConsumer {
        private float[] f = new float[8 * 128];
        private int[] i = new int[3 * 128];
        private int count;

        private int base() {
            return (count - 1) * 8;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            count++;
            if (count * 8 > f.length || count * 3 > i.length) {
                f = Arrays.copyOf(f, f.length * 2);
                i = Arrays.copyOf(i, i.length * 2);
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
            f[base() + 5] = x;
            f[base() + 6] = y;
            f[base() + 7] = z;
            return this;
        }

        void replay(VertexConsumer vc, Matrix4f m, Matrix3f n3, int light, int tint) {
            for (int n = 0; n < count; n++) {
                int b = n * 8, c = n * 3;
                float x = f[b], y = f[b + 1], z = f[b + 2], nx = f[b + 5], ny = f[b + 6], nz = f[b + 7];
                float tx = n3.m00() * nx + n3.m10() * ny + n3.m20() * nz;
                float ty = n3.m01() * nx + n3.m11() * ny + n3.m21() * nz;
                float tz = n3.m02() * nx + n3.m12() * ny + n3.m22() * nz;
                float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (len > 1e-6f) {
                    tx /= len;
                    ty /= len;
                    tz /= len;
                }
                vc.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                                m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                        .setColor(tint != 0 ? tint : i[c]).setUv(f[b + 3], f[b + 4]).setOverlay(i[c + 1]).setLight(light)
                        .setNormal(tx, ty, tz);
            }
        }
    }
}
