package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Small helpers for drawing flat textured rectangles in block entity renderers. */
public final class Quads {
    private static final Quaternionf ROT_SOUTH = Axis.YP.rotationDegrees(180f);
    private static final Quaternionf ROT_EAST = Axis.YP.rotationDegrees(-90f);
    private static final Quaternionf ROT_WEST = Axis.YP.rotationDegrees(90f);
    private static final Quaternionf ROT_UP = Axis.XP.rotationDegrees(90f);
    private static final Quaternionf ROT_DOWN = Axis.XP.rotationDegrees(-90f);

    private Quads() {
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int argb, int light, float nx, float ny, float nz) {
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        float tnx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
        float tny = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
        float tnz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
        float lenSq = tnx * tnx + tny * tny + tnz * tnz;
        if (Math.abs(lenSq - 1f) > 1e-3f && lenSq > 1e-8f) {
            float inv = Mth.invSqrt(lenSq);
            tnx *= inv;
            tny *= inv;
            tnz *= inv;
        }
        vc.addVertex(
                m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                m.m02() * x + m.m12() * y + m.m22() * z + m.m32(),
                argb, u, v, OverlayTexture.NO_OVERLAY, light, tnx, tny, tnz);
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
        if (facing == Direction.NORTH) {
            return;
        }
        pose.translate(0.5, 0.5, 0.5);
        switch (facing) {
            case SOUTH -> pose.mulPose(ROT_SOUTH);
            case EAST -> pose.mulPose(ROT_EAST);
            case WEST -> pose.mulPose(ROT_WEST);
            default -> pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        }
        pose.translate(-0.5, -0.5, -0.5);
    }

    /** Rotate the pose from north-facing to any 6-way block facing (including UP and DOWN, matching blockstate rotations). */
    public static void orient(PoseStack pose, Direction facing) {
        if (facing == Direction.NORTH) {
            return;
        }
        pose.translate(0.5f, 0.5f, 0.5f);
        switch (facing) {
            case SOUTH -> pose.mulPose(ROT_SOUTH);
            case EAST -> pose.mulPose(ROT_EAST);
            case WEST -> pose.mulPose(ROT_WEST);
            case UP -> pose.mulPose(ROT_UP);
            case DOWN -> pose.mulPose(ROT_DOWN);
            default -> {
            }
        }
        pose.translate(-0.5f, -0.5f, -0.5f);
    }
}
