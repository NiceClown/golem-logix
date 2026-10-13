package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Small helpers for drawing flat textured rectangles in block entity renderers. */
public final class Quads {
    private Quads() {
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int argb, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** A horizontal rectangle (facing up) at height y. Coordinates are block fractions. */
    public static void top(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y,
                           float u0, float v0, float u1, float v1, int argb, int light) {
        vertex(vc, pose, x0, y, z1, u0, v1, argb, light, 0, 1, 0);
        vertex(vc, pose, x1, y, z1, u1, v1, argb, light, 0, 1, 0);
        vertex(vc, pose, x1, y, z0, u1, v0, argb, light, 0, 1, 0);
        vertex(vc, pose, x0, y, z0, u0, v0, argb, light, 0, 1, 0);
    }

    /**
     * A rectangle on the front (north, facing -Z) plane z: centre (cx, cy), size w x h, rotated by rot degrees
     * around the centre (counterclockwise as seen by the viewer). flipX mirrors the texture horizontally.
     */
    public static void front(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float z, float w, float h, float rot,
                             float u0, float v0, float u1, float v1, boolean flipX, int argb, int light) {
        float hw = w / 2f, hh = h / 2f;
        float c = (float) Math.cos(Math.toRadians(rot)), s = (float) Math.sin(Math.toRadians(rot));
        float[][] corner = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        float[][] uv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
        if (flipX) {
            float[] t = uv[0];
            uv[0] = uv[1];
            uv[1] = t;
            t = uv[2];
            uv[2] = uv[3];
            uv[3] = t;
        }
        // winding order: we look from -Z, so X grows from right to left, hence x is mirrored
        for (int i = 3; i >= 0; i--) {
            float x = corner[i][0], y = corner[i][1];
            float rx = x * c - y * s, ry = x * s + y * c;
            vertex(vc, pose, cx - rx, cy + ry, z, uv[i][0], uv[i][1], argb, light, 0, 0, -1);
        }
    }

    /**
     * A box (block fractions), all six faces with the same texture area u0..u1 x v0..v1 (for bars and fluid).
     * For render types without back-face culling the winding order does not matter.
     */
    public static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                           float u0, float v0, float u1, float v1, int argb, int light) {
        top(vc, pose, x0, z0, x1, z1, y1, u0, v0, u1, v1, argb, light);
        vertex(vc, pose, x0, y0, z0, u0, v0, argb, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z0, u1, v0, argb, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z1, u1, v1, argb, light, 0, -1, 0);
        vertex(vc, pose, x0, y0, z1, u0, v1, argb, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z0, u0, v1, argb, light, 0, 0, -1);
        vertex(vc, pose, x0, y0, z0, u1, v1, argb, light, 0, 0, -1);
        vertex(vc, pose, x0, y1, z0, u1, v0, argb, light, 0, 0, -1);
        vertex(vc, pose, x1, y1, z0, u0, v0, argb, light, 0, 0, -1);
        vertex(vc, pose, x0, y0, z1, u0, v1, argb, light, 0, 0, 1);
        vertex(vc, pose, x1, y0, z1, u1, v1, argb, light, 0, 0, 1);
        vertex(vc, pose, x1, y1, z1, u1, v0, argb, light, 0, 0, 1);
        vertex(vc, pose, x0, y1, z1, u0, v0, argb, light, 0, 0, 1);
        vertex(vc, pose, x0, y0, z0, u0, v1, argb, light, -1, 0, 0);
        vertex(vc, pose, x0, y0, z1, u1, v1, argb, light, -1, 0, 0);
        vertex(vc, pose, x0, y1, z1, u1, v0, argb, light, -1, 0, 0);
        vertex(vc, pose, x0, y1, z0, u0, v0, argb, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z1, u0, v1, argb, light, 1, 0, 0);
        vertex(vc, pose, x1, y0, z0, u1, v1, argb, light, 1, 0, 0);
        vertex(vc, pose, x1, y1, z0, u1, v0, argb, light, 1, 0, 0);
        vertex(vc, pose, x1, y1, z1, u0, v0, argb, light, 1, 0, 0);
    }

    /** Rotate the pose so the model's "front" (north) faces the block's FACING. */
    public static void faceModel(PoseStack pose, net.minecraft.core.Direction facing) {
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
    }
}
