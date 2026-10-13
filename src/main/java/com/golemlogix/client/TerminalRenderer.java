package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.TerminalBlockEntity;
import com.golemlogix.blocks.AnalogTerminalBlock;
import com.golemlogix.blocks.GolemProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Анимация на экранах терминалов (только пока сеть запитана): при включении сети голем машет рукой во весь экран,
 * потом по экрану ходит голем и таскает ящики к стопке. Спрайты — из одного листа {@code terminal_anim.png}
 * (координаты в текселях листа 64x64, см. tools/ArtForge.java), оттенок задаёт тип терминала.
 * Экран занимает на лицевой текстуре 14x14 текселей (x, y = 9..22 из 32).
 */
public class TerminalRenderer implements BlockEntityRenderer<TerminalBlockEntity> {
    private static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/block/terminal_anim.png");
    private static final float SHEET_SIZE = 64f;
    /** Размер экрана в текселях и размер текселя в долях блока (лицевая текстура 32x32). */
    private static final int SCREEN = 14;
    private static final float TEXEL = 1f / 32f;

    private static final float WAVE_TICKS = 110f;
    private static final float TICKS_PER_WAVE_FRAME = 7f;
    private static final float WALK = 52f, PAUSE = 14f, CYCLE = 2 * (WALK + PAUSE);

    private static final int GREEN = 0xFF7DFF8C;
    private static final int AMBER = 0xFFFFB347;

    public TerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 24;
    }

    @Override
    public void render(TerminalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        BlockState state = be.getBlockState();
        boolean active = state.getValue(GolemProperties.ACTIVE);
        float time = be.getLevel().getGameTime() + partialTick;
        // запуск сети: «выключено -> включено» на глазах у игрока (первое наблюдение показывает только состояние)
        if (be.animInitialized && active && !be.lastActive) {
            be.waveStart = time;
        }
        be.animInitialized = true;
        be.lastActive = active;
        if (!active) {
            return;
        }
        boolean crt = state.getBlock() instanceof AnalogTerminalBlock;
        int tint = crt ? GREEN : AMBER;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SHEET));
        PoseStack.Pose p = pose.last();
        final int full = LightTexture.FULL_BRIGHT;

        // фон экрана (круг у ЭЛТ, квадрат у крафтового терминала)
        sprite(vc, p, 0, 0, SCREEN, SCREEN, crt ? 0 : 16, 0, SCREEN, SCREEN, false, 0xFFFFFFFF, full, 0);

        float sinceWave = time - be.waveStart;
        if (sinceWave >= 0 && sinceWave < WAVE_TICKS) {
            // приветствие: голем во весь экран машет рукой; под конец мигает и исчезает
            int frame = (int) (sinceWave / TICKS_PER_WAVE_FRAME) % 4;
            boolean blink = sinceWave > WAVE_TICKS - 12f && ((int) (sinceWave / 2f)) % 2 == 0;
            if (!blink) {
                sprite(vc, p, 0, 0, SCREEN, SCREEN, frame * 16, 32, SCREEN, SCREEN, false, tint, full, 1);
            }
        } else {
            scene(vc, p, time + (be.getBlockPos().hashCode() & 127), tint, full);
        }
        pose.popPose();
    }

    /** Будничная сценка: голем носит ящик от запаса слева к стопке справа и возвращается пустым. */
    private void scene(VertexConsumer vc, PoseStack.Pose p, float time, int tint, int light) {
        float t = time % CYCLE;
        int cycle = (int) (time / CYCLE);
        // пол (чуть короче, чтобы помещался в круг)
        sprite(vc, p, 3, 11, 8, 1, 62, 62, 1, 1, false, tint, light, 1);

        boolean outward = t < WALK + PAUSE;
        float leg = outward ? t : t - (WALK + PAUSE);
        boolean walking = leg < WALK;
        float x = walking ? 1f + 6f * (leg / WALK) : 7f;
        if (!outward) {
            x = walking ? 7f - 6f * (leg / WALK) : 1f;
        }
        int frame = walking ? ((int) (leg / 4f)) % 4 : 1;

        // запас слева: два ящика, один из них у голема в руках на пути туда
        int stock = outward && walking ? 1 : 2;
        // стопка справа: растёт от 0 до 3 и сбрасывается; ящик появляется в ней, когда голем его донёс
        boolean dropped = !(outward && walking);
        int pile = Math.min(3, cycle % 4 + (dropped ? 1 : 0));
        for (int i = 0; i < stock; i++) {
            sprite(vc, p, 0, 9 - i * 2, 2, 2, 32, 16, 2, 2, false, tint, light, 1);
        }
        for (int i = 0; i < pile; i++) {
            sprite(vc, p, 12, 9 - i * 2, 2, 2, 32, 16, 2, 2, false, tint, light, 1);
        }
        // голем: с ящиком по пути туда, пустой — обратно; слева направо смотрит вправо
        int gx = Math.round(x);
        boolean carry = outward && walking;
        sprite(vc, p, gx, 4, 6, 7, frame * 8, carry ? 16 : 24, 6, 7, !outward, tint, light, 2);
    }

    /**
     * Спрайт: прямоугольник (sx, sy, w, h) в текселях экрана (от левого верхнего угла) показывает область листа
     * (u, v, uw, vh). layer сдвигает плоскость наружу, чтобы слои не мерцали.
     */
    private static void sprite(VertexConsumer vc, PoseStack.Pose p, int sx, int sy, int w, int h, int u, int v, int uw, int vh,
                               boolean flip, int argb, int light, int layer) {
        // экран центрирован на лицевой грани; правая сторона зрителя — убывающий x модели
        float offX = sx + w / 2f - SCREEN / 2f;
        float offY = SCREEN / 2f - (sy + h / 2f);
        float cx = 0.5f - offX * TEXEL;
        float cy = 0.5f + offY * TEXEL;
        float z = -(0.002f + 0.0015f * layer);
        // тонкий зазор в UV, чтобы фильтрация не цепляла соседние спрайты листа
        float e = 0.0005f;
        Quads.front(vc, p, cx, cy, z, w * TEXEL, h * TEXEL, 0f,
                u / SHEET_SIZE + e, v / SHEET_SIZE + e, (u + uw) / SHEET_SIZE - e, (v + vh) / SHEET_SIZE - e, flip, argb, light);
    }
}
