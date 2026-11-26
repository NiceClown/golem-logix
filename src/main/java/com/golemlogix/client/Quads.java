package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

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
        float hw = w * 0.5f, hh = h * 0.5f;
        float uLeft = flipX ? u1 : u0, uRight = flipX ? u0 : u1;
        // winding order: we look from -Z, so X grows from right to left, hence x is mirrored
        if (rot == 0f) {
            vertex(vc, pose, cx + hw, cy + hh, z, uLeft, v0, argb, light, 0, 0, -1);
            vertex(vc, pose, cx - hw, cy + hh, z, uRight, v0, argb, light, 0, 0, -1);
            vertex(vc, pose, cx - hw, cy - hh, z, uRight, v1, argb, light, 0, 0, -1);
            vertex(vc, pose, cx + hw, cy - hh, z, uLeft, v1, argb, light, 0, 0, -1);
            return;
        }
        double rad = Math.toRadians(rot);
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        float hcX = hw * c, hsX = hw * s, hcY = hh * c, hsY = hh * s;
        // corners in original loop order: (-hw, +hh), (+hw, +hh), (+hw, -hh), (-hw, -hh)
        vertex(vc, pose, cx + hcX + hsY, cy - hsX + hcY, z, uLeft, v0, argb, light, 0, 0, -1);
        vertex(vc, pose, cx - hcX + hsY, cy + hsX + hcY, z, uRight, v0, argb, light, 0, 0, -1);
        vertex(vc, pose, cx - hcX - hsY, cy + hsX - hcY, z, uRight, v1, argb, light, 0, 0, -1);
        vertex(vc, pose, cx + hcX - hsY, cy - hsX - hcY, z, uLeft, v1, argb, light, 0, 0, -1);
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

    /** Rotate the pose so the model's horizontal front (north) faces the block's horizontal FACING. */
    public static void faceModel(PoseStack pose, Direction facing) {
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
    }

    /** Rotate the pose from north-facing to any 6-way block facing (including UP and DOWN, matching blockstate rotations). */
    public static void orient(PoseStack pose, Direction facing) {
        pose.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180f));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90f));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90f));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(90f));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90f));
            default -> {
            }
        }
        pose.translate(-0.5f, -0.5f, -0.5f);
    }
}
