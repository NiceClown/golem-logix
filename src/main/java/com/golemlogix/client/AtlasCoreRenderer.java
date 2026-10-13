package com.golemlogix.client;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Запитанное Ядро Атланта:
 * — вокруг кристалла по орбитам бегают медные големы (три орбиты у достроенной решётки, одна у «стартовой башни»);
 * — у достроенной решётки по верхней грани (кольцо из восьми Корпусов вокруг ядра) мини-големы носят ящики между стопками в углах.
 */
public class AtlasCoreRenderer implements BlockEntityRenderer<AtlasCoreBlockEntity> {
    private static final int ORBITS = 3;
    /** Центр орбиты — кристалл, торчащий над кольцом (в долях блока). */
    private static final float CENTER_Y = 0.95f;
    private static final float ORBIT_RADIUS = 0.58f;
    private static final float ORBIT_GOLEM_SCALE = 0.17f;

    // верхняя грань: кольцо по центрам восьми Корпусов (ядро в середине), высота — верх Корпусов
    private static final float RING_Y = 1.0f;
    private static final float WALK_TICKS = 52f;
    private static final float PAUSE_TICKS = 30f;
    private static final float CYCLE = 2f * (WALK_TICKS + PAUSE_TICKS);
    private static final float RING_GOLEM_SCALE = 0.2f;
    private static final float[][] CORNERS = {{-0.5f, -0.5f}, {1.5f, -0.5f}, {1.5f, 1.5f}, {-0.5f, 1.5f}};

    public AtlasCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AtlasCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!be.isPowered() || be.getLevel() == null) {
            return;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        // Сам блок ядра зажат между корпусами и сидит «в тени»: освещаем всё, что рисуется над ним, светом клетки над ядром
        int above = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        light = Math.max(light & 0xFFFF, above & 0xFFFF) | Math.max(light >> 16, above >> 16) << 16;
        orbits(be, time, pose, buffers, light);
        if (be.isFormed()) {
            ring(time, pose, buffers, light);
            glyphs(time, pose, buffers);
        }
    }

    private static final net.minecraft.resources.ResourceLocation GLYPH = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.golemlogix.GolemLogixMod.MOD_ID, "textures/entity/atlas_glyph.png");
    /** Клетки обшивки на боковой грани (a — вдоль грани, b — высота) в координатах ядра: средний столбец и верхний ряд. */
    private static final int[][] SIDE_CELLS = {{0, -2}, {0, -1}, {-1, 0}, {0, 0}, {1, 0}};
    private static final int[][] TOP_CELLS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final float GLYPH_MOVE = 40f, GLYPH_STAY = 30f, GLYPH_HALF = 0.17f;

    /** Детерминированное «случайное» число 0..1 по грани, шагу и соли. */
    private static float rnd(int face, long step, int salt) {
        long h = face * 0x9E3779B97F4A7C15L ^ step * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Точка знака на грани в шаге step: случайная клетка обшивки и место в ней (a, b в координатах грани). */
    private static float[] glyphPoint(int face, long step) {
        int[][] cells = face == 4 ? TOP_CELLS : SIDE_CELLS;
        int[] c = cells[(int) (rnd(face, step, 1) * cells.length) % cells.length];
        return new float[]{c[0] + 0.25f + 0.5f * rnd(face, step, 2), c[1] + 0.25f + 0.5f * rnd(face, step, 3)};
    }

    /**
     * Фиолетовый знак решётки: по одному на каждой из пяти видимых граней собранного многоблока (четыре боковые и верх);
     * знак то стоит на месте, то плавно перебирается на случайное новое место обшивки своей грани.
     */
    private void glyphs(float time, PoseStack pose, MultiBufferSource buffers) {
        var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(GLYPH));
        PoseStack.Pose p = pose.last();
        float cycle = GLYPH_MOVE + GLYPH_STAY;
        for (int face = 0; face < 5; face++) {
            float local = time + face * 17f;
            long step = (long) Math.floor(local / cycle);
            float k = Mth.clamp((local - step * cycle) / GLYPH_MOVE, 0f, 1f);
            k = k * k * (3f - 2f * k);
            float[] a = glyphPoint(face, step), b = glyphPoint(face, step + 1);
            float u = Mth.lerp(k, a[0], b[0]), v = Mth.lerp(k, a[1], b[1]);
            int alpha = (int) (200 + 55 * Mth.sin(time * 0.15f + face));
            int color = (Mth.clamp(alpha, 0, 255) << 24) | 0xFFFFFF;
            // начало (центр знака), «вправо» и «вверх» в плоскости грани, нормаль наружу
            float e = 0.004f;
            switch (face) {
                case 0 -> quad(vc, p, new Vector3f(u, v, -1 - e), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0), color);
                case 1 -> quad(vc, p, new Vector3f(u, v, 2 + e), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), color);
                case 2 -> quad(vc, p, new Vector3f(-1 - e, v, u), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0), color);
                case 3 -> quad(vc, p, new Vector3f(2 + e, v, u), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), color);
                default -> quad(vc, p, new Vector3f(u, 1 + e, v), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), color);
            }
        }
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose p, Vector3f c, Vector3f right, Vector3f up, int color) {
        Vector3f n = new Vector3f(right).cross(up);
        float h = GLYPH_HALF;
        // против часовой стрелки глазами смотрящего на грань (лицевая сторона)
        float[][] corners = {{-1, 1, 0, 0}, {-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}};
        for (float[] k : corners) {
            Vector3f at = new Vector3f(c).add(new Vector3f(right).mul(k[0] * h)).add(new Vector3f(up).mul(k[1] * h));
            vc.addVertex(p, at.x, at.y, at.z).setColor(color).setUv(k[2], k[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(0xF000F0).setNormal(p, n.x, n.y, n.z);
        }
    }

    private void orbits(AtlasCoreBlockEntity be, float time, PoseStack pose, MultiBufferSource buffers, int light) {
        int count = be.isFormed() ? ORBITS : 1;
        for (int i = 0; i < count; i++) {
            // каждая орбита — своя плоскость
            Quaternionf plane = new Quaternionf().rotateY((float) Math.toRadians(i * 60f)).rotateX((float) Math.toRadians(25f + i * 35f));
            Vector3f u = plane.transform(new Vector3f(1f, 0f, 0f));
            Vector3f v = plane.transform(new Vector3f(0f, 0f, 1f));
            float angle = (float) Math.toRadians(time * (2.2f + i * 0.5f) + i * 120f);
            float ca = Mth.cos(angle), sa = Mth.sin(angle);
            Vector3f radial = new Vector3f(u).mul(ca).add(new Vector3f(v).mul(sa));
            Vector3f pos = new Vector3f(0.5f, CENTER_Y, 0.5f).add(new Vector3f(radial).mul(ORBIT_RADIUS));
            Vector3f forward = new Vector3f(u).mul(-sa).add(new Vector3f(v).mul(ca));
            // шагают по «поверхности шара» вокруг кристалла: макушка смотрит от центра
            float arc = angle * ORBIT_RADIUS;
            CopperGolemPuppet.render(pose, buffers, light, pos, forward, radial, ORBIT_GOLEM_SCALE,
                    new CopperGolemPuppet.Anim(arc * 14f, true, false, 0f), i);
        }
    }

    /** Четыре мини-голема ходят по сторонам кольца и носят ящики от стопки к стопке (по ящику в обе стороны). */
    private void ring(float time, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        // стопки ящиков в углах
        for (float[] c : CORNERS) {
            for (int k = 0; k < 2; k++) {
                pose.pushPose();
                pose.translate(0.5f + (c[0] - 0.5f) * 0.97f, RING_Y + 0.09f + k * 0.18f, 0.5f + (c[1] - 0.5f) * 0.97f);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(k * 23f + c[0] * 40f));
                pose.scale(0.18f, 0.18f, 0.18f);
                pose.translate(-0.5, -0.5, -0.5);
                dispatcher.renderSingleBlock(Blocks.BARREL.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        for (int s = 0; s < 4; s++) {
            float[] a = CORNERS[s], b = CORNERS[(s + 1) % 4];
            float dx = b[0] - a[0], dz = b[1] - a[1];
            float len = Mth.sqrt(dx * dx + dz * dz);
            dx /= len;
            dz /= len;
            // боковое смещение «наружу», чтобы голем, идущий вдоль соседней стороны, не наступал на этого
            float ox = dz, oz = -dx;
            float pad = 0.28f;
            float t = (time + s * (CYCLE / 4f)) % CYCLE;
            boolean forwardLeg = t < WALK_TICKS + PAUSE_TICKS;
            float leg = forwardLeg ? t : t - (WALK_TICKS + PAUSE_TICKS);
            float fromX = forwardLeg ? a[0] : b[0], fromZ = forwardLeg ? a[1] : b[1];
            float sx = forwardLeg ? dx : -dx, sz = forwardLeg ? dz : -dz;
            boolean walking = leg < WALK_TICKS;
            float run = walking ? leg / WALK_TICKS : 1f;
            float dist = pad + (len - 2f * pad) * run;
            // углы уже в координатах блока ядра (центры угловых корпусов): без лишних +0.5 големы шли по краю платформы
            // и двое из четырёх висели в воздухе за ней; смещение «наружу» — внутри полосы корпусов
            float px = fromX + sx * dist + ox * 0.14f;
            float pz = fromZ + sz * dist + oz * 0.14f;
            // на стоянке: наклон к стопке, ящик кладут и берут другой
            float bow = 0f;
            boolean carrying = true;
            if (!walking) {
                float k = (leg - WALK_TICKS) / PAUSE_TICKS;
                bow = Mth.sin((float) Math.PI * k);
                carrying = k < 0.35f || k > 0.65f;
            }
            Vector3f pos = new Vector3f(px, RING_Y + 0.005f, pz);
            Vector3f forward = new Vector3f(sx, 0f, sz);
            float phase = (leg * 0.5f) + s;
            CopperGolemPuppet.render(pose, buffers, light, pos, forward, new Vector3f(0f, 1f, 0f), RING_GOLEM_SCALE,
                    new CopperGolemPuppet.Anim(phase, walking, carrying, bow), s);
        }
    }

    @Override
    public AABB getRenderBoundingBox(AtlasCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.2, 2.4, 2.2);
    }
}
