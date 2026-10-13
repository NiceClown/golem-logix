package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.items.MagneticDiskItem;
import com.golemlogix.system.DiskStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Динамическая часть дискового шкафа 2x2 — всё, что видно за стеклом витрины:
 * десять кассет (цвет ярлыка — ёмкость диска, четыре светодиода — заполнение), в каждой две катушки с лентой
 * (крутятся, пока сеть запитана, лента перематывается от одной катушки к другой по мере заполнения),
 * медные големы-архивариусы, патрулирующие полки, и пульсирующее свечение рун на фасаде.
 *
 * Все размеры даны в «шкафных» координатах (юниты 1/16 блока): X — слева направо глазами зрителя СПЕРЕДИ (0..32),
 * Y — вверх (0..32), Z — вглубь от фасада; такая же система у моделей шкафа (tools/Forge2.java).
 * От главной (нижней левой) клетки модель смотрит на север, поворот под FACING делает {@link Quads#faceModel}.
 */
public class DriveBayRenderer implements BlockEntityRenderer<CopperDriveBayBlockEntity> {
    private static final ResourceLocation CASSETTE = tex("bay_cassette");
    private static final ResourceLocation REEL = tex("bay_reel");
    private static final ResourceLocation TAPE = tex("bay_tape");
    private static final ResourceLocation TAPE_GLOW = tex("bay_tape_glow");
    private static final ResourceLocation WHITE = tex("bay_white");
    private static final ResourceLocation RUNES = tex("bay_runes_glow");

    /** Цвета ярлыков по ёмкости (1k, 4k, 16k, 64k, 256k): диски предметов — металлы, жидкостей — холодные оттенки. */
    private static final int[] ITEM_LABEL = {0xFFD9803A, 0xFF9AA4B4, 0xFFF0C850, 0xFF55E6E0, 0xFFA070FF};
    private static final int[] FLUID_LABEL = {0xFF7AC8FF, 0xFFC4ECFF, 0xFF3E7CE6, 0xFF2A50B8, 0xFFD070FF};
    /** Светодиоды заполнения: от зелёного к красному. */
    private static final int[] LED = {0xFF3CE070, 0xFF9AE040, 0xFFE8D030, 0xFFF08828, 0xFFF03A2A};
    private static final int LED_OFF = 0xFF1E2622;

    static final int FULL_BRIGHT = 0xF000F0;

    /** Кассета почти во всю ячейку полки и выдвинута вперёд (не прячется у задней стенки); бобины — почти во всю высоту. */
    private static final float COL0 = 8.0f, COL1 = 16.0f, CASSETTE_W = 7.0f, CASSETTE_H = 4.0f;
    private static final float Z_FRONT = 4.6f, Z_BACK = 12.5f;
    private static final float REEL_R = 1.3f, REEL_DY = 2.35f, REEL_X0 = 1.85f, REEL_X1 = 5.15f;
    /** Дверные проёмы перегородки (как в tools/Forge2.java): x середины, высота от полки, z от стекла до глубины проёма. */
    private static final float DOOR_X = 15.5f, DOOR_H = 3.4f, DOOR_Z0 = 2.5f, DOOR_Z1 = 6.0f;
    private static final int[] GOLEM_ROWS = {0, 2, 4};

    public DriveBayRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/" + name + ".png");
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public AABB getRenderBoundingBox(CopperDriveBayBlockEntity be) {
        BlockPos p = be.getBlockPos();
        Direction right = CopperDriveBayBlock.rightOf(be.getBlockState().getValue(CopperDriveBayBlock.FACING));
        return new AABB(p).minmax(new AABB(p.relative(right).above())).inflate(0.25);
    }

    private static int tier(ItemStack disk) {
        int cap = DiskStorage.capacity(disk);
        return cap >= 256000 ? 4 : cap >= 64000 ? 3 : cap >= 16000 ? 2 : cap >= 4000 ? 1 : 0;
    }

    // ------------------------------------------------------------------------ примитивы в шкафных координатах

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int color, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, (16f - x) / 16f, y / 16f, z / 16f).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    /** Прямоугольник лицом к зрителю на глубине z; (u0,v0) — левый верхний угол текстуры. */
    private static void front(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                              float u0, float v0, float u1, float v1, int color, int light) {
        vertex(vc, pose, x0, y1, z, u0, v0, color, light, 0, 0, -1);
        vertex(vc, pose, x1, y1, z, u1, v0, color, light, 0, 0, -1);
        vertex(vc, pose, x1, y0, z, u1, v1, color, light, 0, 0, -1);
        vertex(vc, pose, x0, y0, z, u0, v1, color, light, 0, 0, -1);
    }

    /** Квадрат со стороной 2*half вокруг (cx, cy), повёрнутый на angle градусов в плоскости фасада. */
    private static void spun(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float half, float z, float angle,
                             int color, int light) {
        float c = Mth.cos(angle * Mth.DEG_TO_RAD) * half, s = Mth.sin(angle * Mth.DEG_TO_RAD) * half;
        // углы (левый верх, правый верх, правый низ, левый низ), повёрнутые на угол
        vertex(vc, pose, cx - c - s, cy - s + c, z, 0, 0, color, light, 0, 0, -1);
        vertex(vc, pose, cx + c - s, cy + s + c, z, 1, 0, color, light, 0, 0, -1);
        vertex(vc, pose, cx + c + s, cy + s - c, z, 1, 1, color, light, 0, 0, -1);
        vertex(vc, pose, cx - c + s, cy - s - c, z, 0, 1, color, light, 0, 0, -1);
    }

    /** Боковые грани кассеты (глубина). Центр текстуры WHITE — сплошной цвет. */
    private static void sides(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z0, float z1,
                              int color, int light) {
        // верх (нормаль вверх)
        vertex(vc, pose, x0, y1, z0, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x1, y1, z0, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x1, y1, z1, 0.5f, 0.5f, color, light, 0, 1, 0);
        vertex(vc, pose, x0, y1, z1, 0.5f, 0.5f, color, light, 0, 1, 0);
        // низ
        vertex(vc, pose, x0, y0, z1, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z1, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x1, y0, z0, 0.5f, 0.5f, color, light, 0, -1, 0);
        vertex(vc, pose, x0, y0, z0, 0.5f, 0.5f, color, light, 0, -1, 0);
        // левая (меньший X) и правая
        vertex(vc, pose, x0, y1, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y1, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y0, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x0, y0, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, pose, x1, y1, z0, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y1, z1, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z1, 0.5f, 0.5f, color, light, -1, 0, 0);
        vertex(vc, pose, x1, y0, z0, 0.5f, 0.5f, color, light, -1, 0, 0);
    }

    // ------------------------------------------------------------------------------------- отрисовка

    @Override
    public void render(CopperDriveBayBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!state.hasProperty(CopperDriveBayBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(CopperDriveBayBlock.FACING);
        boolean active = state.getValue(GolemProperties.ACTIVE);
        float time = be.getLevel().getGameTime() + partialTick;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        if (be.getMask() != 0) {
            cassettes(be, p, buffers, light, active, time);
        }
        boolean walking = active && be.getMask() != 0;
        doors(p, buffers, light, walking, time);
        if (walking) {
            golems(pose, buffers, light, time);
        }
        if (active) {
            // руны на цоколе и верхней балке пульсируют
            float pulse = 0.55f + 0.45f * Mth.sin(time * 0.09f);
            int a = (int) (255 * pulse);
            front(buffers.getBuffer(RenderType.eyes(RUNES)), p, 0f, 0f, 32f, 32f, -0.03f, 0f, 0f, 1f, 1f, (a << 24) | 0xFFFFFF, FULL_BRIGHT);
        }
        // стеклянная дверца витрины: распахнута, пока окно шкафа у кого-то открыто
        float open = Mth.lerp(partialTick, be.doorPrev, be.door);
        float ease = open * open * (3f - 2f * open);
        // большая витрина — на петлях у левой стойки, узкая правая дверца с приборами — у правой
        glassDoor(p, buffers, light, 7.5f, 16f, 1f, "cab_glass_d", ease * 105f);
        glassDoor(p, buffers, light, 29.5f, 4f, -1f, "cab_glass_g", ease * 100f);
        pose.popPose();
    }

    private void cassettes(CopperDriveBayBlockEntity be, PoseStack.Pose p, MultiBufferSource buffers, int light, boolean active, float time) {
        // Буферы разных типов нельзя держать одновременно: запрос нового заканчивает предыдущий. Поэтому проходы по текстурам.
        // проход 1: лицевые стороны кассет
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(CASSETTE));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            if (be.getDisks().get(slot).isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1, x1 = x0 + CASSETTE_W;
            float y0 = 28f - 4.8f * (slot / 2 + 1) + 0.5f, y1 = y0 + CASSETTE_H;
            front(body, p, x0, y0, x1, y1, Z_FRONT, 0f, 0f, 1f, 1f, 0xFFFFFFFF, light);
        }
        // проход 2: боковые грани, ярлыки и светодиоды (сплошной цвет)
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1, x1 = x0 + CASSETTE_W;
            float y0 = 28f - 4.8f * (slot / 2 + 1) + 0.5f, y1 = y0 + CASSETTE_H;
            boolean fluid = disk.getItem() instanceof MagneticDiskItem d && d.isFluid();
            int label = (fluid ? FLUID_LABEL : ITEM_LABEL)[tier(disk)];
            sides(solid, p, x0, y0, x1, y1, Z_FRONT, Z_BACK, 0xFF7C3A1E, light);
            // ярлык: светлая бумага из текстуры, поверх тон диска
            front(solid, p, x0 + 0.5f, y1 - 0.75f, x1 - 0.5f, y1 - 0.15f, Z_FRONT - 0.02f, 0.5f, 0.5f, 0.5f, 0.5f, label, light);
            // светодиоды заполнения
            long stored = MagneticDiskItem.getStoredCount(disk);
            long cap = Math.max(1, DiskStorage.capacity(disk));
            int lit = stored <= 0 ? 0 : (int) Math.min(4, 1 + stored * 4 / (cap + 1));
            for (int i = 0; i < 4; i++) {
                float lx = x1 - 2.05f + i * 0.4f;
                front(solid, p, lx, y1 - 0.68f, lx + 0.28f, y1 - 0.22f, Z_FRONT - 0.04f, 0.5f, 0.5f, 0.5f, 0.5f, i < lit ? LED[i] : LED_OFF,
                        i < lit ? FULL_BRIGHT : light);
            }
        }
        // проход 3: намотка ленты за катушками
        VertexConsumer tape = buffers.getBuffer(RenderType.entityCutoutNoCull(TAPE));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            ItemStack disk = be.getDisks().get(slot);
            if (disk.isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1;
            float cy = 28f - 4.8f * (slot / 2 + 1) + 0.5f + CASSETTE_H - REEL_DY;
            float fill = Mth.clamp((float) MagneticDiskItem.getStoredCount(disk) / Math.max(1, DiskStorage.capacity(disk)), 0f, 1f);
            float spin = active ? time * 7f + slot * 29f : slot * 29f;
            spun(tape, p, x0 + REEL_X0, cy, 0.55f + 0.7f * fill, Z_FRONT - 0.05f, spin, 0xFFFFFFFF, light);
            spun(tape, p, x0 + REEL_X1, cy, 1.25f - 0.7f * fill, Z_FRONT - 0.05f, -spin, 0xFFFFFFFF, light);
        }
        // проход 3б: бирюзовое свечение витков (только когда сеть запитана), поверх намотки
        if (active) {
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(TAPE_GLOW));
            for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
                ItemStack disk = be.getDisks().get(slot);
                if (disk.isEmpty()) {
                    continue;
                }
                float x0 = slot % 2 == 0 ? COL0 : COL1;
                float cy = 28f - 4.8f * (slot / 2 + 1) + 0.5f + CASSETTE_H - REEL_DY;
                float fill = Mth.clamp((float) MagneticDiskItem.getStoredCount(disk) / Math.max(1, DiskStorage.capacity(disk)), 0f, 1f);
                float spin = time * 7f + slot * 29f;
                spun(glow, p, x0 + REEL_X0, cy, 0.55f + 0.7f * fill, Z_FRONT - 0.06f, spin, 0xFFFFFFFF, FULL_BRIGHT);
                spun(glow, p, x0 + REEL_X1, cy, 1.25f - 0.7f * fill, Z_FRONT - 0.06f, -spin, 0xFFFFFFFF, FULL_BRIGHT);
            }
        }
        // проход 4: катушки
        VertexConsumer reel = buffers.getBuffer(RenderType.entityCutoutNoCull(REEL));
        for (int slot = 0; slot < CopperDriveBayBlockEntity.SLOTS; slot++) {
            if (be.getDisks().get(slot).isEmpty()) {
                continue;
            }
            float x0 = slot % 2 == 0 ? COL0 : COL1;
            float cy = 28f - 4.8f * (slot / 2 + 1) + 0.5f + CASSETTE_H - REEL_DY;
            float spin = active ? time * 7f + slot * 29f : slot * 29f;
            spun(reel, p, x0 + REEL_X0, cy, REEL_R, Z_FRONT - 0.09f, spin, 0xFFFFFFFF, light);
            spun(reel, p, x0 + REEL_X1, cy, REEL_R, Z_FRONT - 0.09f, -spin * 1.1f, 0xFFFFFFFF, light);
        }
    }

    /** Где по полке сейчас архивариус i (x в шкафных координатах) и идёт ли он вправо. */
    private static float golemX(int i, float time) {
        float phase = (time * 0.0095f + i * 0.37f) % 2f;
        float t = phase < 1f ? phase : 2f - phase;
        t = t * t * (3f - 2f * t);
        return 9.2f + 13.6f * t;
    }

    /**
     * Створки дверей в перегородке между столбцами полок: пара латунных створок «салун» в каждом проёме, распахиваются,
     * когда к проёму подходит архивариус (и закрываются за ним).
     */
    private void doors(PoseStack.Pose p, MultiBufferSource buffers, int light, boolean walking, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for (int r = 0; r < 5; r++) {
            float yb = 28f - 4.8f * (r + 1) + 0.5f;
            float open = 0f;
            for (int i = 0; walking && i < GOLEM_ROWS.length; i++) {
                if (GOLEM_ROWS[i] == r) {
                    float dx = Math.abs(golemX(i, time) - DOOR_X);
                    open = Mth.clamp(1f - (dx - 1.2f) / 1.6f, 0f, 1f);
                    open = open * open * (3f - 2f * open);
                }
            }
            // створка на петле у каждого края проёма; открытая повёрнута на 80° внутрь проёма вдоль перегородки
            float half = (DOOR_Z1 - DOOR_Z0) / 2f, a = open * 80f * Mth.DEG_TO_RAD;
            float y0 = yb + 0.3f, y1 = yb + DOOR_H - 0.4f;
            for (int side = 0; side < 2; side++) {
                float hz = side == 0 ? DOOR_Z0 : DOOR_Z1, dir = side == 0 ? 1f : -1f;
                float tipX = DOOR_X + Mth.sin(a) * half, tipZ = hz + dir * Mth.cos(a) * half;
                leaf(vc, p, DOOR_X, hz, tipX, tipZ, y0, y1, light);
            }
        }
    }

    /** Створка — латунная доска от петли (hx, hz) до края (tx, tz), с тёмной филёнкой. */
    private static void leaf(VertexConsumer vc, PoseStack.Pose p, float hx, float hz, float tx, float tz, float y0, float y1, int light) {
        int brass = 0xFFB8862B, panel = 0xFF6A4A1A;
        quad(vc, p, hx, hz, tx, tz, y0, y1, brass, light);
        float ix0 = hx + (tx - hx) * 0.2f, iz0 = hz + (tz - hz) * 0.2f, ix1 = hx + (tx - hx) * 0.8f, iz1 = hz + (tz - hz) * 0.8f;
        quad(vc, p, ix0 - 0.03f, iz0, ix1 - 0.03f, iz1, y0 + 0.4f, y1 - 0.4f, panel, light);
        quad(vc, p, ix0 + 0.03f, iz0, ix1 + 0.03f, iz1, y0 + 0.4f, y1 - 0.4f, panel, light);
    }

    /** Вертикальный двусторонний прямоугольник между точками (x0, z0) и (x1, z1). */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float z0, float x1, float z1, float y0, float y1, int color, int light) {
        vertex(vc, p, x0, y1, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x1, y1, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x1, y0, z1, 0.5f, 0.5f, color, light, 1, 0, 0);
        vertex(vc, p, x0, y0, z0, 0.5f, 0.5f, color, light, 1, 0, 0);
    }

    /** Стёкла дверец на глубине 0.8 и высоте 4..28 — как было в модели (tools/Forge2.java): витрина 7.5..23.5, правая 25.5..29.5. */
    private static final float GLASS_Z = 0.8f, GLASS_Y0 = 4f, GLASS_Y1 = 28f;

    /**
     * Дверца: латунная рамка, ручка у свободного края и стекло (текстура из атласа блоков). hinge — x петли, width — ширина,
     * dir — куда от петли идёт створка (+1 вправо, -1 влево); angle — на сколько градусов распахнута наружу (к зрителю).
     * Шкаф в мире рисуется слоем cutout (раньше блок-сущностей), поэтому полупрозрачное стекло ложится поверх полок.
     */
    private static void glassDoor(PoseStack.Pose p, MultiBufferSource buffers, int light, float hinge, float width, float dir, String tex,
                                  float angle) {
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite glass = atlas.apply(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + tex));
        TextureAtlasSprite brass = atlas.apply(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/sat_brass"));
        float c = Mth.cos(angle * Mth.DEG_TO_RAD), s = Mth.sin(angle * Mth.DEG_TO_RAD);
        // точка дверцы на расстоянии d от петли и со сдвигом t по нормали (+ — внутрь шкафа)
        java.util.function.BiFunction<Float, Float, float[]> at =
                (d, t) -> new float[]{hinge + dir * (d * c + t * s), GLASS_Z - d * s + t * c};
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        float f = Math.min(0.7f, width * 0.15f);
        // рамка: верх, низ, у петли, у свободного края — с обеих сторон стекла
        for (float t : new float[]{-0.12f, 0.12f}) {
            strip(vc, p, at, 0f, width, GLASS_Y1 - f, GLASS_Y1, t, brass, light);
            strip(vc, p, at, 0f, width, GLASS_Y0, GLASS_Y0 + f, t, brass, light);
            strip(vc, p, at, 0f, f, GLASS_Y0, GLASS_Y1, t, brass, light);
            strip(vc, p, at, width - f, width, GLASS_Y0, GLASS_Y1, t, brass, light);
        }
        // ручка: латунная скоба перед стеклом у свободного края
        float h0 = width - Math.min(2.2f, width * 0.45f), h1 = h0 + 0.7f;
        for (float t : new float[]{-0.9f, -0.5f}) {
            strip(vc, p, at, h0, h1, 14f, 18f, t, brass, light);
        }
        strip(vc, p, at, h0, h1, 14f, 14.5f, -0.7f, brass, light);
        // стекло
        VertexConsumer gv = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        float[] a = at.apply(f, 0f), b = at.apply(width - f, 0f);
        float u0 = glass.getU(f / width), u1 = glass.getU(1f - f / width);
        float v0 = glass.getV(f / (GLASS_Y1 - GLASS_Y0)), v1 = glass.getV(1f - f / (GLASS_Y1 - GLASS_Y0));
        if (dir < 0) {
            float tmp = u0;
            u0 = u1;
            u1 = tmp;
        }
        vertex(gv, p, a[0], GLASS_Y1 - f, a[1], u0, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, b[0], GLASS_Y1 - f, b[1], u1, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, b[0], GLASS_Y0 + f, b[1], u1, v1, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(gv, p, a[0], GLASS_Y0 + f, a[1], u0, v1, 0xFFFFFFFF, light, 0, 0, -1);
    }

    /** Полоса дверцы от d0 до d1 (вдоль створки) и от y0 до y1, сдвинутая по нормали на t, с текстурой спрайта. */
    private static void strip(VertexConsumer vc, PoseStack.Pose p, java.util.function.BiFunction<Float, Float, float[]> at,
                              float d0, float d1, float y0, float y1, float t, TextureAtlasSprite sprite, int light) {
        float[] a = at.apply(d0, t), b = at.apply(d1, t);
        float u0 = sprite.getU(0f), u1 = sprite.getU(Math.min(1f, (d1 - d0) / 16f));
        float v0 = sprite.getV(0f), v1 = sprite.getV(Math.min(1f, (y1 - y0) / 16f));
        vertex(vc, p, a[0], y1, a[1], u0, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, b[0], y1, b[1], u1, v0, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, b[0], y0, b[1], u1, v1, 0xFFFFFFFF, light, 0, 0, -1);
        vertex(vc, p, a[0], y0, a[1], u0, v1, 0xFFFFFFFF, light, 0, 0, -1);
    }

    /** Три архивариуса ходят туда-обратно по полкам перед кассетами (на левом конце берут ящик, на правом оставляют). */
    private void golems(PoseStack pose, MultiBufferSource buffers, int light, float time) {
        int[] rows = GOLEM_ROWS;
        for (int i = 0; i < rows.length; i++) {
            float phase = (time * 0.0095f + i * 0.37f) % 2f;
            boolean toRight = phase < 1f;
            float x = golemX(i, time);
            float y = 28f - 4.8f * (rows[i] + 1) + 0.5f;
            // forward в системе модели: зритель смотрит с севера, его «вправо» — это -x модели
            Vector3f forward = new Vector3f(toRight ? -1f : 1f, 0f, 0f);
            Vector3f at = new Vector3f((16f - x) / 16f, y / 16f, 3.6f / 16f);
            CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(time * 0.42f + i * 2f, true, toRight, 0f);
            CopperGolemPuppet.render(pose, buffers, light, at, forward, new Vector3f(0f, 1f, 0f), 0.115f, anim, i);
        }
    }
}
