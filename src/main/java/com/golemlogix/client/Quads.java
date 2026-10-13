package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Мелкие помощники для рисования плоских текстурированных прямоугольников в отрисовщиках блок-сущностей. */
public final class Quads {
    private Quads() {
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int argb, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Горизонтальный прямоугольник (лицом вверх) на высоте y. Координаты — доли блока. */
    public static void top(VertexConsumer vc, PoseStack.Pose pose, float x0, float z0, float x1, float z1, float y,
                           float u0, float v0, float u1, float v1, int argb, int light) {
        vertex(vc, pose, x0, y, z1, u0, v1, argb, light, 0, 1, 0);
        vertex(vc, pose, x1, y, z1, u1, v1, argb, light, 0, 1, 0);
        vertex(vc, pose, x1, y, z0, u1, v0, argb, light, 0, 1, 0);
        vertex(vc, pose, x0, y, z0, u0, v0, argb, light, 0, 1, 0);
    }

    /**
     * Прямоугольник на передней (северной, лицом к -Z) плоскости z: центр (cx, cy), размер w x h, поворот rot градусов
     * вокруг центра (против часовой стрелки глазами наблюдателя). flipX зеркалит текстуру по горизонтали.
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
        // порядок обхода: смотрим из -Z, значит X растёт справа налево — зеркалим x
        for (int i = 3; i >= 0; i--) {
            float x = corner[i][0], y = corner[i][1];
            float rx = x * c - y * s, ry = x * s + y * c;
            vertex(vc, pose, cx - rx, cy + ry, z, uv[i][0], uv[i][1], argb, light, 0, 0, -1);
        }
    }

    /**
     * Параллелепипед (доли блока), все шесть граней с одной областью текстуры u0..u1 x v0..v1 (для брусков и жидкости).
     * Для типов отрисовки без отсечения обратных граней порядок обхода не важен.
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

    /** Повернуть позу так, чтобы «перед» модели (север) смотрел по FACING блока. */
    public static void faceModel(PoseStack pose, net.minecraft.core.Direction facing) {
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        pose.translate(-0.5, -0.5, -0.5);
    }
}
