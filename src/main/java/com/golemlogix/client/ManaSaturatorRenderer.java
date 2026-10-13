package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.system.SaturatorStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Живая часть собранного Насыщателя: две стеклянные колонны наполняются Source, в камере на наковальне стоит маленький
 * железный голем и пятью ударами расплющивает заготовку в пластины (без заготовки протягивает мак), на крыше крутятся
 * вентиляторы установленных Сопроцессоров Охлаждения. Все размеры — в «машинных» координатах (X слева направо глазами
 * зрителя спереди, Y вверх, Z вглубь, клетка = 16), как в модели; хозяин стоит в клетке (1, 2, 0), то есть X 16..32,
 * Y 32..48, Z 0..16.
 */
public class ManaSaturatorRenderer implements BlockEntityRenderer<ManaSaturatorBlockEntity> {
    private static final ResourceLocation LIQUID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_liquid.png");
    private static final ResourceLocation PRESS = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_press.png");
    private static final ResourceLocation GOLEM = ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    /** Голем ростом ~8.6 юнита (вся камера — 9.5): ноги на наковальне за заготовкой, лицом к фасаду. */
    private static final float GOLEM_SCALE = 0.2f;
    private static final float ANVIL_TOP = 29.5f;
    private static final float GOLEM_Z = 11.2f;
    private static final float ITEM_Z = 6.2f;

    private final ItemRenderer itemRenderer;
    private ModelPart golem;

    public ManaSaturatorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
    }

    /** Машинные координаты -> доли блока-хозяина (лицом на север; x зеркален: растёт влево от зрителя). */
    private static float lx(float x) {
        return (32f - x) / 16f;
    }

    private static float ly(float y) {
        return (y - 32f) / 16f;
    }

    private static float lz(float z) {
        return z / 16f;
    }

    /** Собранная машина занимает 3x3x2 блока: отрисовку нельзя отсекать по одному блоку хозяина. */
    @Override
    public AABB getRenderBoundingBox(ManaSaturatorBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        if (!entity.getBlockState().getValue(ManaSaturatorBlock.FORMED)) {
            return new AABB(pos);
        }
        AABB box = new AABB(pos);
        for (BlockPos p : SaturatorStructure.cells(pos, entity.getBlockState().getValue(ManaSaturatorBlock.FACING))) {
            box = box.minmax(new AABB(p));
        }
        return box.inflate(0.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(ManaSaturatorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        if (!entity.getBlockState().getValue(ManaSaturatorBlock.FORMED)) {
            renderAnvilItem(entity, partialTick, pose, buffers, level);
            return;
        }
        Direction facing = entity.getBlockState().getValue(ManaSaturatorBlock.FACING);
        float time = level.getGameTime() + partialTick;
        // свет берём над машиной: сам хозяин стоит внутри и свой воздух не освещает
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos().above());
        light = LightTexture.pack(Math.max(LightTexture.block(light), 10), Math.max(LightTexture.sky(light), 6));
        // ход ритуала между тиками — плавно (сервер шлёт только начало и конец)
        float progress = entity.isCrafting()
                ? Math.min(1f, entity.getProgressFraction() + partialTick * entity.speed() / (float) ManaSaturatorBlockEntity.MAX_PROGRESS)
                : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        renderColumns(entity, buffers, pose.last(), time);
        renderGolem(entity, pose, buffers, light, time, progress);
        renderItem(entity, pose, buffers, light, packedOverlay, time, progress);
        renderCarriers(entity, pose, buffers, light, packedOverlay, time, partialTick);
        renderFans(entity, level, facing, pose, buffers, light, time);
        // голем с лампой на крыше: верх балки — Y 44 машины, то есть 12 пикселей клетки хозяина
        LampGolem.render(pose, buffers, light, entity.walker, partialTick, entity.lampState(), time, 32f, 12f);
        pose.popPose();
    }

    /** Две колонны: уровень Source, бегущая рябь по текстуре жидкости. */
    private void renderColumns(ManaSaturatorBlockEntity entity, MultiBufferSource buffers, PoseStack.Pose p, float time) {
        if (entity.getSource() <= 0) {
            return;
        }
        float fill = 0.1f + 0.9f * entity.getSourceFraction();
        float height = 10f * fill;
        float shift = (time * 0.006f) % 1f;
        float wob = entity.isCrafting() ? 0.04f * (float) Math.sin(time * 0.7f) : 0.01f * (float) Math.sin(time * 0.15f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LIQUID));
        for (float x0 : new float[]{4f, 36f}) {
            Quads.box(vc, p, lx(x0 + 8f), ly(29f), lz(4f), lx(x0), ly(29f + height) + wob / 16f, lz(12f),
                    shift, shift, shift + 0.8f, shift + 0.8f, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
        }
    }

    /** Фаза удара 0..1 внутри одного из пяти ударов за ритуал. */
    private static float strikePhase(float progress) {
        return (progress * 5f) % 1f;
    }

    /**
     * Железный голем: замах (руки поднимаются), резкий удар по заготовке и короткая пауза — пять раз за ритуал. Без работы стоит
     * спокойно и чуть покачивает головой; без заготовки протягивает мак, как настоящий железный голем.
     */
    private void renderGolem(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, float time, float progress) {
        ModelPart head = golem.getChild("head");
        ModelPart rightArm = golem.getChild("right_arm");
        ModelPart leftArm = golem.getChild("left_arm");
        ModelPart rightLeg = golem.getChild("right_leg");
        ModelPart leftLeg = golem.getChild("left_leg");
        rightLeg.xRot = 0f;
        leftLeg.xRot = 0f;
        // на наковальне что-то есть или заготовку уже несут — кузнец ждёт; пусто и нечего ждать — протягивает мак
        boolean hasItem = !entity.getHeldItem().isEmpty() || !entity.bringer.hands.isEmpty();
        boolean flower = false;
        if (entity.isCrafting()) {
            float ph = strikePhase(progress);
            float arm;
            if (ph < 0.65f) {
                float k = ph / 0.65f;
                arm = -1.15f - 0.6f * (k * k * (3 - 2 * k));
            } else if (ph < 0.75f) {
                float k = (ph - 0.65f) / 0.1f;
                arm = -1.75f + 0.6f * k * k;
            } else {
                arm = -1.15f;
            }
            rightArm.xRot = arm;
            leftArm.xRot = arm;
            head.xRot = 0.45f;
            head.yRot = 0f;
        } else if (hasItem) {
            rightArm.xRot = -0.35f;
            leftArm.xRot = -0.35f;
            head.xRot = 0.4f;
            head.yRot = Mth.sin(time * 0.04f) * 0.25f;
        } else {
            // нечего ковать — голем протягивает мак (как железные големы деревенским детям)
            flower = true;
            rightArm.xRot = -0.8f + 0.025f * Mth.sin(time * 0.09f);
            leftArm.xRot = 0f;
            head.xRot = 0.1f;
            head.yRot = Mth.sin(time * 0.03f) * 0.35f;
        }
        rightArm.yRot = 0f;
        leftArm.yRot = 0f;

        pose.pushPose();
        pose.translate(lx(24f), ly(ANVIL_TOP), lz(GOLEM_Z));
        pose.scale(-GOLEM_SCALE, -GOLEM_SCALE, GOLEM_SCALE);
        pose.translate(0f, -1.501f, 0f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(GOLEM));
        golem.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        if (flower) {
            pose.pushPose();
            rightArm.translateAndRotate(pose);
            // как слой цветка у ванильного железного голема (IronGolemFlowerLayer): мак в кисти правой руки
            pose.translate(-1.1875f, 1.0625f, -0.9375f);
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.scale(0.5f, 0.5f, 0.5f);
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
            pose.translate(-0.5f, -0.5f, -0.5f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.POPPY.defaultBlockState(), pose, buffers, light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Заготовка лежит на наковальне перед големом; с каждым ударом сплющивается, к концу ритуала почти пластина. */
    private void renderItem(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                            float time, float progress) {
        ItemStack held = entity.getHeldItem();
        if (held.isEmpty()) {
            return;
        }
        boolean block = held.getItem() instanceof BlockItem;
        float squash = 1f;
        if (entity.isCrafting()) {
            float ph = strikePhase(progress);
            float hit = ph >= 0.73f && ph < 0.86f ? 1f - Math.abs(ph - 0.76f) / 0.1f : 0f;
            squash = (1f - 0.45f * progress) * (1f - 0.3f * Math.max(0f, hit));
        }
        pose.pushPose();
        if (block) {
            float size = 0.56f;
            pose.translate(lx(24f), ly(ANVIL_TOP) + size * 0.25f * squash, lz(ITEM_Z));
            pose.scale(1f, squash, 1f);
            pose.mulPose(Axis.YP.rotationDegrees(entity.isCrafting() ? 0f : 18f + Mth.sin(time * 0.05f) * 6f));
            pose.scale(size, size, size);
        } else {
            // плоские предметы (пластины, плазма) лежат на наковальне
            pose.translate(lx(24f), ly(ANVIL_TOP) + 0.02f, lz(ITEM_Z));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.mulPose(Axis.ZP.rotationDegrees(15f));
            pose.scale(0.3f, 0.3f, 0.3f);
        }
        itemRenderer.renderStatic(held, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }

    // ============================================================================ медные големы-носильщики и палубы
    /** Голем-носильщик ростом ~4.5 юнита: проходит между стеклянной колонной и камерой пресса. */
    private static final float CARRIER_SCALE = 0.28f;
    /** Путь подносчика с левой задней палубы на наковальню (машинные координаты x, y, z): мимо колонны, шаг на плиту. */
    private static final float[][] PATH_IN = {{11.4f, 28f, 21.2f}, {14.8f, 28f, 16.0f}, {14.8f, 28f, 9.4f}, {16.5f, 28.9f, 8.3f},
            {19.3f, 29.5f, 7.8f}};
    /** Путь уносчика — зеркальный, с правой палубы. */
    private static final float[][] PATH_OUT = mirror(PATH_IN);
    /** Где лежат заготовка (левая палуба) и готовый предмет (правая), на рунных кругах палуб. */
    private static final float[] SPOT_IN = {7.6f, 28f, 23.4f}, SPOT_OUT = {40.4f, 28f, 23.4f};
    private static final float[] SPOT_ANVIL = {24f, ANVIL_TOP, ITEM_Z};
    /** Сколько тиков в начале шага голем наклоняется (берёт или кладёт). */
    private static final float BOW_TICKS = 5f;

    private static float[][] mirror(float[][] path) {
        float[][] out = new float[path.length][];
        for (int i = 0; i < path.length; i++) {
            out[i] = new float[]{48f - path[i][0], path[i][1], path[i][2]};
        }
        return out;
    }

    /** Точка на ломаной path при доле пути f (0 — начало, 1 — конец): x, y, z и направление (dx, dz). */
    private static float[] along(float[][] path, float f) {
        float total = 0f;
        float[] seg = new float[path.length - 1];
        for (int i = 0; i < seg.length; i++) {
            seg[i] = (float) Math.hypot(path[i + 1][0] - path[i][0], path[i + 1][2] - path[i][2]);
            total += seg[i];
        }
        float d = Mth.clamp(f, 0f, 1f) * total;
        for (int i = 0; i < seg.length; i++) {
            if (d <= seg[i] || i == seg.length - 1) {
                float k = seg[i] <= 0f ? 0f : Mth.clamp(d / seg[i], 0f, 1f);
                float[] a = path[i], b = path[i + 1];
                return new float[]{Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]), b[0] - a[0], b[2] - a[2]};
            }
            d -= seg[i];
        }
        float[] last = path[path.length - 1];
        return new float[]{last[0], last[1], last[2], 0f, 1f};
    }

    private static float pathLength(float[][] path) {
        float total = 0f;
        for (int i = 0; i + 1 < path.length; i++) {
            total += (float) Math.hypot(path[i + 1][0] - path[i][0], path[i + 1][2] - path[i][2]);
        }
        return total;
    }

    /**
     * Медный голем-носильщик по его фазе (см. ManaSaturatorBlockEntity.Carrier): дома — стоит у своей палубы и поглядывает
     * то на неё, то на кузнеца; к наковальне и обратно — идёт по пути (в начале шага наклоняется: берёт или кладёт); у
     * наковальни — ждёт за кузнецом. deck — его палуба, toAnvilForward — путь от палубы к наковальне.
     */
    private void carrier(ManaSaturatorBlockEntity entity, ManaSaturatorBlockEntity.Carrier c, float[][] path, float[] deck, PoseStack pose,
                         MultiBufferSource buffers, int light, int overlay, float time, float pt, int variant, float lookPhase) {
        float len = pathLength(path), walkT = ManaSaturatorBlockEntity.WALK;
        float left = Math.max(0f, c.timer - pt);
        float[] pos, face;
        boolean walking = false;
        float bow = 0f, dist = 0f;
        switch (c.phase) {
            case ManaSaturatorBlockEntity.TO_ANVIL -> {
                float f = 1f - left / walkT;
                float[] at = along(path, f);
                pos = at;
                face = new float[]{at[3], at[4]};
                walking = left > 0f;
                dist = f * len;
                float since = walkT - left;
                if (since < BOW_TICKS) {
                    // только что поднял с палубы
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
            }
            case ManaSaturatorBlockEntity.AT_ANVIL -> {
                pos = path[path.length - 1];
                face = toward(pos, SPOT_ANVIL);
                bow = 0.12f + 0.08f * Mth.sin(time * 0.35f + variant);
            }
            case ManaSaturatorBlockEntity.TO_DECK -> {
                float f = left / walkT;
                float[] at = along(path, f);
                pos = at;
                face = new float[]{-at[3], -at[4]};
                walking = left > 0f;
                dist = (1f - f) * len;
                float since = walkT - left;
                if (since < BOW_TICKS) {
                    // только что положил на наковальню или снял с неё
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
                if (!walking) {
                    face = toward(pos, deck);
                }
            }
            default -> {
                pos = path[0];
                float look = Mth.sin(time * 0.021f + lookPhase) * 0.5f + 0.5f;
                float[] a = toward(pos, deck), b = toward(pos, SPOT_ANVIL);
                face = new float[]{Mth.lerp(look, a[0], b[0]), Mth.lerp(look, a[1], b[1])};
            }
        }
        ItemStack item = c.hands;
        boolean hold = !item.isEmpty();
        org.joml.Vector3f p = new org.joml.Vector3f(lx(pos[0]), ly(pos[1]), lz(pos[2]));
        float fx = face[0], fz = face[1];
        if (Math.abs(fx) + Math.abs(fz) < 1e-4f) {
            fz = 1f;
        }
        // x модели зеркален: направление по машинному X в позе — со знаком минус
        org.joml.Vector3f forward = new org.joml.Vector3f(-fx, 0f, fz);
        CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(walking ? dist * 1.3f : time * 0.1f, walking, hold, Mth.clamp(bow, 0f, 1f));
        CopperGolemPuppet.render(pose, buffers, light, p, forward, new org.joml.Vector3f(0f, 1f, 0f), CARRIER_SCALE, anim, variant,
                hold ? hp -> {
                    boolean block = item.getItem() instanceof BlockItem;
                    float sc = block ? 0.75f : 0.55f;
                    hp.scale(sc, sc, sc);
                    itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, hp, buffers, entity.getLevel(), 0);
                } : null);
    }

    private void renderCarriers(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                float time, float pt) {
        carrier(entity, entity.bringer, PATH_IN, SPOT_IN, pose, buffers, light, overlay, time, pt, 1, 0f);
        carrier(entity, entity.taker, PATH_OUT, SPOT_OUT, pose, buffers, light, overlay, time, pt, 2, 2f);
        // очередь заготовок на левой палубе (до трёх штук стопкой) и готовые пластины на правой — медленно поворачиваются
        ItemStack in = entity.inputDeck();
        for (int i = 0; i < Math.min(3, in.getCount()); i++) {
            deckItem(pose, buffers, light, overlay, entity, in, new float[]{SPOT_IN[0] + (i % 2) * 1.6f - 0.8f * (i / 2), SPOT_IN[1] + i * 0.02f,
                    SPOT_IN[2] - i * 1.9f}, i * 17f);
        }
        ItemStack out = entity.outputDeck();
        if (!out.isEmpty()) {
            deckItem(pose, buffers, light, overlay, entity, out, SPOT_OUT, time * 1.5f);
            if (out.getCount() > 2) {
                deckItem(pose, buffers, light, overlay, entity, out, new float[]{SPOT_OUT[0], SPOT_OUT[1] + 0.6f, SPOT_OUT[2]}, time * 1.5f + 40f);
            }
        }
    }

    /** Направление (dx, dz) из точки from в точку to (машинные координаты). */
    private static float[] toward(float[] from, float[] to) {
        return new float[]{to[0] - from[0], to[2] - from[2]};
    }

    private void deckItem(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ManaSaturatorBlockEntity entity, ItemStack item,
                          float[] spot, float spin) {
        boolean block = item.getItem() instanceof BlockItem;
        pose.pushPose();
        if (block) {
            float size = 0.42f;
            pose.translate(lx(spot[0]), ly(spot[1]) + size * 0.25f, lz(spot[2]));
            pose.mulPose(Axis.YP.rotationDegrees(25f + spin));
            pose.scale(size, size, size);
        } else {
            pose.translate(lx(spot[0]), ly(spot[1]) + 0.02f, lz(spot[2]));
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.3f, 0.3f, 0.3f);
        }
        itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }

    /** Лопасти вентиляторов на установленных Сопроцессорах Охлаждения: при работе машины крутятся быстро, без неё — лениво. */
    private void renderFans(ManaSaturatorBlockEntity entity, Level level, Direction facing, PoseStack pose, MultiBufferSource buffers,
                            int light, float time) {
        BlockPos controller = entity.getBlockPos();
        VertexConsumer vc = null;
        float speed = entity.isCrafting() ? 22f : 3f;
        for (int vent : SaturatorStructure.VENTS) {
            BlockState s = level.getBlockState(SaturatorStructure.cellPos(controller, facing, vent));
            if (!(s.getBlock() instanceof SaturatorPartBlock) || !s.getValue(SaturatorPartBlock.COOLER)) {
                continue;
            }
            if (vc == null) {
                vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PRESS));
            }
            float cx = 16f * SaturatorStructure.col(vent) + 8f, cz = 16f * SaturatorStructure.dep(vent) + 8f;
            pose.pushPose();
            pose.translate(lx(cx), ly(47.15f), lz(cz));
            pose.mulPose(Axis.YP.rotationDegrees((time * speed + vent * 37f) % 360f));
            PoseStack.Pose p = pose.last();
            for (int b = 0; b < 4; b++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(b * 90f));
                pose.mulPose(Axis.XP.rotationDegrees(14f));
                Quads.box(vc, pose.last(), -0.03f, -0.006f, 0.04f, 0.03f, 0.006f, 0.19f, 0.1f, 0.1f, 0.4f, 0.9f, 0xFFFFFFFF, light);
                pose.popPose();
            }
            Quads.box(vc, p, -0.045f, -0.01f, -0.045f, 0.045f, 0.02f, 0.045f, 0.6f, 0.6f, 0.9f, 0.9f, 0xFFFFE6A0, light);
            pose.popPose();
        }
    }

    /**
     * Наковальня до сборки: заготовка лежит на ударной площадке и сплющивается с каждым ударом ручной ковки; готовые
     * пластины лежат плашмя и чуть покачиваются.
     */
    private static void renderAnvilItem(ManaSaturatorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, Level level) {
        ItemStack item = entity.anvilItem();
        if (item.isEmpty()) {
            return;
        }
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos().above());
        float time = level.getGameTime() + partialTick;
        pose.pushPose();
        if (ManaSaturatorBlockEntity.isManualInput(item)) {
            float squash = 1f - 0.55f * entity.manualHits() / (float) ManaSaturatorBlockEntity.MANUAL_HITS;
            float size = 0.36f;
            pose.translate(0.5, 15f / 16f + size * squash * 0.5f, 0.5);
            pose.scale(size * (1f + (1f - squash) * 0.35f), size * squash, size * (1f + (1f - squash) * 0.35f));
            Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, level, 0);
        } else {
            pose.translate(0.5, 15.2f / 16f, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(15f * Mth.sin(time * 0.05f)));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.45f, 0.45f, 0.45f);
            Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, level, 0);
        }
        pose.popPose();
    }
}