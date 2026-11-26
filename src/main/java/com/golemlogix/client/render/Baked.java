package com.golemlogix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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
        recorder.trim();
        return new Baked(recorder);
    }

    /** Draws it where the pose stands now. tint: replaces the colour it was built with, 0 keeps that. */
    public void draw(MultiBufferSource buffers, RenderType type, PoseStack.Pose pose, int light, int tint) {
        if (recorder.count > 0) {
            recorder.replay(buffers.getBuffer(type), pose, light, tint);
        }
    }

    /** Vertices of one render type: position, colour, texture, overlay, normal (light is supplied at replay time). */
    private static final class Recorder implements VertexConsumer {
        private float[] f = new float[8 * 128];
        private int[] i = new int[2 * 128];
        private int count;

        private int base() {
            return (count - 1) * 8;
        }

        void trim() {
            if (count * 8 < f.length) {
                f = Arrays.copyOf(f, count * 8);
            }
            if (count * 2 < i.length) {
                i = Arrays.copyOf(i, count * 2);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            count++;
            if (count * 8 > f.length || count * 2 > i.length) {
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
            int c = (count - 1) * 2;
            i[c] = -1;
            i[c + 1] = OverlayTexture.NO_OVERLAY;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            i[(count - 1) * 2] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            i[(count - 1) * 2] = color;
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
            i[(count - 1) * 2 + 1] = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            f[base() + 5] = x;
            f[base() + 6] = y;
            f[base() + 7] = z;
            return this;
        }

        void replay(VertexConsumer vc, PoseStack.Pose pose, int light, int tint) {
            Matrix4f m = pose.pose();
            float m00 = m.m00(), m10 = m.m10(), m20 = m.m20(), m30 = m.m30();
            float m01 = m.m01(), m11 = m.m11(), m21 = m.m21(), m31 = m.m31();
            float m02 = m.m02(), m12 = m.m12(), m22 = m.m22(), m32 = m.m32();
            Vector3f norm = new Vector3f();
            for (int n = 0; n < count; n++) {
                int b = n * 8, c = n * 2;
                float x = f[b], y = f[b + 1], z = f[b + 2];
                pose.transformNormal(f[b + 5], f[b + 6], f[b + 7], norm);
                vc.addVertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31,
                                m02 * x + m12 * y + m22 * z + m32)
                        .setColor(tint != 0 ? tint : i[c]).setUv(f[b + 3], f[b + 4]).setOverlay(i[c + 1]).setLight(light)
                        .setNormal(norm.x, norm.y, norm.z);
            }
        }
    }
}
