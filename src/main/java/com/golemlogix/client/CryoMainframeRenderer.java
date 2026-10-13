package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.CryoMainframeBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.MainframeStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Собранный Крио-Мейнфрейм целиком: бронзовый шкаф 3 x 2 x 2 в латунной раме, за стеклянными двустворчатыми дверями —
 * светящаяся бирюзовая матрица и две полки с калькуляторами, на которых бригады снеговиков собирают изделия; наверху —
 * латунный гребень с манометром и красным вентилем-штурвалом. По бокам шкаф держат атланты (это настоящие блоки).
 * Пока окно шкафа у кого-то открыто, двери распахиваются наружу.
 * <p>
 * Координаты — в пикселях системы хозяина «лицом на север»: X -16..32 (три клетки вбок), Y 0..32, Z 0..32 (фасад — Z 0).
 */
public class CryoMainframeRenderer implements BlockEntityRenderer<CryoMainframeBlockEntity> {
    private static final ResourceLocation BRONZE = tex("sat_bronze"), BRASS = tex("sat_brass"), DARK = tex("sat_dark"),
            MATRIX = tex("cryo_panel_matrix"), GLASS = tex("sat_glass"), PANEL = tex("cryo_panel"), GAUGE = tex("monitor_gauge"),
            COPPER = ResourceLocation.withDefaultNamespace("block/copper_block"), DECK = tex("monitor_deck");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name);
    }

    public CryoMainframeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CryoMainframeBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState s = be.getBlockState();
        if (!s.hasProperty(CryoMainframeBlock.FACING)) {
            return new AABB(pos);
        }
        Direction f = s.getValue(CryoMainframeBlock.FACING);
        AABB box = new AABB(pos);
        for (int i = 0; i < MainframeStructure.CELLS.length; i++) {
            box = box.minmax(new AABB(MainframeStructure.cellPos(pos, f, i)));
        }
        return box.inflate(1.0);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(CryoMainframeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CryoMainframeBlock.FORMED) || !state.getValue(CryoMainframeBlock.FORMED)) {
            return;
        }
        Direction facing = state.getValue(CryoMainframeBlock.FACING);
        float time = level.getGameTime() + partialTick;
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().relative(facing));
        light = LightTexture.pack(Math.max(LightTexture.block(light), 7), LightTexture.sky(light));
        float doors = Mth.lerp(partialTick, be.doorsPrev, be.doors);
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float foreman = Mth.lerp(partialTick, be.foremanPrev, be.foreman);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;

        // корпус: цоколь, задняя стенка, боковины, крыша
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, white, light);
        box(vc, p, -15, 2, 29, 31, 30, 32, BRONZE, white, light);
        box(vc, p, -16, 2, 2, -14, 30, 32, BRONZE, white, light);
        box(vc, p, 30, 2, 2, 32, 30, 32, BRONZE, white, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, BRONZE, white, light);
        // латунная рама фасада и рёбра
        box(vc, p, -16, 2, -0.5f, -14, 30, 2, BRASS, white, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 2, BRASS, white, light);
        box(vc, p, -16, 28, -0.5f, 32, 30, 2, BRASS, white, light);
        box(vc, p, -16, 2, -0.5f, 32, 3, 2, BRASS, white, light);
        for (float x : new float[]{-16.3f, 31.3f}) {
            box(vc, p, x, 0, 30.7f, x + 1f, 32.3f, 32.3f, BRASS, white, light);
        }
        // внутри: светящаяся матрица во всю заднюю стенку и латунная полка посередине
        wall(vc, p, -14, 3, 30, 28, 28.9f, MATRIX, white, LightTexture.FULL_BRIGHT);
        box(vc, p, -14, 15, 3, 30, 16, 29, BRASS, white, light);
        box(vc, p, -14, 3, 3, 30, 3.2f, 29, DECK, 0xFF9AA4B0, light);
        box(vc, p, -14, 16, 3, 30, 16.2f, 29, DECK, 0xFF9AA4B0, light);

        // калькуляторы на полках (сколько вставлено), у задней стенки
        int calcs = be.clientCalcs();
        BlockState calc = ModBlocks.CRYO_CALCULATOR.get().defaultBlockState().setValue(GolemProperties.ACTIVE, spin > 0.5f);
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (int k = 0; k < Math.min(calcs, CryoMainframeBlockEntity.CALC_SLOTS); k++) {
            int shelf = k / 6, slot = k % 6;
            pose.pushPose();
            pose.translate((-12.5f + slot * 7.2f) / 16f, (shelf == 0 ? 3.2f : 16.2f) / 16f, 22.5f / 16f);
            pose.scale(5.5f / 16f, 5.5f / 16f, 5.5f / 16f);
            dispatcher.renderSingleBlock(calc, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        // бригады снеговиков на полках: собирают то, что делают ядра; бригадир — внизу, когда сопроцессоры помогают
        long seed = be.getBlockPos().asLong();
        for (int shelf = 0; shelf < 2; shelf++) {
            float floor = shelf == 0 ? 3.2f : 16.2f;
            pose.pushPose();
            pose.translate(0f, 0f, 5f / 16f);
            float appear = spin * spin * (3 - 2 * spin);
            Vector3f proj = new Vector3f(0.5f, floor / 16f + 0.5f + Mth.sin(time * 0.1f + shelf) * 0.02f, 0.45f);
            float shelfWork = (shelf == 0 || be.clientBusy() > 1 || !be.clientDisplayItem(shelf).isEmpty()) ? spin : 0f;
            SnowCrew.render(pose, buffers, light, time, seed + shelf * 7919L, floor / 16f, shelfWork,
                    shelf == 0 ? foreman : 0f, proj);
            ItemStack current = be.clientDisplayItem(shelf);
            if (!current.isEmpty() && appear > 0.005f) {
                float size = (current.getItem() instanceof BlockItem ? 0.3f : 0.26f) * appear;
                pose.pushPose();
                pose.translate(proj.x, proj.y, proj.z);
                pose.mulPose(Axis.YP.rotationDegrees(time * 3f + shelf * 90f));
                pose.scale(size, size, size);
                Minecraft.getInstance().getItemRenderer().renderStatic(current, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
                pose.popPose();
            }
            pose.popPose();
        }

        // атланты встроены в шкаф: торсы выходят из боковин, одна рука обхватывает угол фасада и держится за стойку, другая
        // лежит на крыше; головы у верхних углов следят за игроком; внизу из кожуха вьются обмёрзшие трубы
        shelfGolems(be, pose, buffers, light, time, spin);
        for (int k = 0; k < 2; k++) {
            embeddedAtlant(be, pose, buffers, light, partialTick, k, time, spin);
        }
        vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        p = pose.last();
        // фриз над дверями: ряд бирюзовых огоньков — бегут, пока шкаф работает, и тихо мерцают в простое
        box(vc, p, -16, 32, -0.5f, 32, 34, 3, BRONZE, white, light);
        for (int k = 0; k < 14; k++) {
            float lx = -12.6f + k * 3f;
            float glow = spin > 0.01f ? (((int) (time / 3) - k) % 14 + 14) % 14 < 4 ? 1f : 0.35f : 0.45f + 0.15f * Mth.sin(time * 0.05f + k);
            int c = 0xFF000000 | ((int) (0x30 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
            box(vc, p, lx, 32.5f, -0.9f, lx + 1.4f, 33.6f, -0.5f, PANEL, c, LightTexture.FULL_BRIGHT);
        }
        // гребень над шкафом: манометр и красный вентиль-штурвал (его крутит медный голем на крыше)
        box(vc, p, -16.5f, 34, -1.5f, 32.5f, 37.4f, 16, BRASS, white, light);
        box(vc, p, -15.5f, 37.4f, -0.8f, 31.5f, 38.4f, 15, BRONZE, white, light);
        box(vc, p, -15, 34.6f, -1.8f, 31, 36.8f, -1.5f, BRONZE, white, light);
        box(vc, p, -13.2f, 34.2f, -2.4f, -7.8f, 37.8f, -1.8f, BRASS, white, light);
        fitWall(vc, p, -13f, 34.4f, -8f, 37.6f, -2.45f, GAUGE, white, light);
        valve(pose, vc, 24.5f, 36f, -2.6f, 3.4f, time * (spin > 0.01f ? 2.5f : 0.3f), light);
        roofGolem(be, pose, buffers, light, time, spin);
        vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        p = pose.last();        // медные трубы по крыше и вниз по задней стенке
        box(vc, p, -15, 32, 20, 31, 33.6f, 21.6f, COPPER, white, light);
        box(vc, p, -15, 32, 25, 31, 33.6f, 26.6f, COPPER, white, light);
        box(vc, p, 27, 4, 32, 28.6f, 33.6f, 33.6f, COPPER, white, light);
        box(vc, p, -12.6f, 4, 32, -11, 33.6f, 33.6f, COPPER, white, light);

        // двери: створки на петлях у боковых стоек, распахиваются наружу
        float open = doors * doors * (3 - 2 * doors) * 95f;
        door(pose, buffers, -14f, 8f, open, light);
        door(pose, buffers, 30f, 8f, -open, light);
        pose.popPose();
    }

    private static final ResourceLocation ATLANT_BODY = tex("atlant_body"), ATLANT_LIMB = tex("atlant_limb"),
            ATLANT_BELT = tex("atlant_belt"), CASING = tex("atlas_casing"), FROST_PIPE = tex("resonator_pipe"),
            ICE = tex("sat_ice");

    /**
     * Атлант, встроенный в боковину (k = 0 — слева, 1 — справа): медный кожух у подножия с обмёрзшими трубами, торс выходит
     * из стенки шкафа, наружная рука опускается и обхватывает угол фасада (ладонь — на латунной стойке), внутренняя поднята
     * и лежит на гребне крыши, голова у верхнего угла поворачивается к игроку. Координаты левого — в пикселях; правый —
     * зеркально относительно середины шкафа (x = 8).
     */
    private static void embeddedAtlant(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float pt, int k,
                                       float time, float spin) {
        boolean right = k == 1;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        int white = 0xFFFFFFFF;
        // кожух-постамент у боковины и трубы из него
        mbox(vc, pose, right, -25, 0, 0.5f, -16, 11, 15.5f, CASING, white, light);
        mbox(vc, pose, right, -25.5f, 10.5f, 0, -15.5f, 12, 16, BRASS, white, light);
        for (float z : new float[]{3.2f, 10.0f}) {
            mbox(vc, pose, right, -28.5f, 2.5f, z, -24.5f, 5.5f, z + 2.8f, FROST_PIPE, white, light);
            mbox(vc, pose, right, -29.5f, 2.2f, z - 0.3f, -26.5f, 9.5f, z + 3.1f, FROST_PIPE, white, light);
            mbox(vc, pose, right, -29.8f, 6.4f, z - 0.6f, -26.2f, 7.2f, z + 3.4f, ICE, white, light);
            mbox(vc, pose, right, -28.4f, 0.6f, z + 1.0f, -27.8f, 2.2f, z + 1.6f, ICE, white, light);
        }
        // торс: примыкает к стенке шкафа (X <= -16), пояс и наплечники
        float breathe = 0.25f * Mth.sin(time * 0.05f + k * 2f);
        mbox(vc, pose, right, -26, 12, 2.5f, -16f, 26 + breathe, 13.5f, ATLANT_BODY, white, light);
        mbox(vc, pose, right, -26.5f, 11.5f, 2, -16f, 13.5f, 14, ATLANT_BELT, white, light);
        mbox(vc, pose, right, -29, 22 + breathe, 1.5f, -24.5f, 27 + breathe, 14.5f, ATLANT_BELT, white, light);
        mbox(vc, pose, right, -26.5f, 25.5f + breathe, 2.5f, -16f, 27.5f + breathe, 13.5f, ATLANT_BELT, white, light);
        // наружная рука: плечо -> локоть у переднего угла -> ладонь на стойке фасада
        float grip = spin > 0.01f ? 0.4f * Mth.sin(time * 0.3f + k) : 0f;
        Vector3f shoulder = mv(right, -27.5f, 24.5f + breathe, 6), elbow = mv(right, -28.5f, 16f, -1.5f),
                hand = mv(right, -17.5f, 17.5f + grip, -1.8f);
        limb(vc, pose, shoulder, elbow, 4.2f, ATLANT_LIMB, light);
        limb(vc, pose, elbow, hand, 3.6f, ATLANT_LIMB, light);
        mbox(vc, pose, right, -18.5f, 15.5f + grip, -3.0f, -16.0f, 20 + grip, -0.6f, ATLANT_BELT, white, light);
        // внутренняя рука: поднята, ладонь лежит на карнизе крыши у угла
        Vector3f s2 = mv(right, -22, 26 + breathe, 12), e2 = mv(right, -23.5f, 33.5f, 11), h2 = mv(right, -16.8f, 37.2f, 8);
        limb(vc, pose, s2, e2, 3.8f, ATLANT_LIMB, light);
        limb(vc, pose, e2, h2, 3.4f, ATLANT_LIMB, light);
        mbox(vc, pose, right, -18.0f, 36.8f, 5.6f, -16.0f, 38.2f, 10.4f, ATLANT_BELT, white, light);
        // голова у верхнего угла: модель головы атланта (крупнее), поворачивается к игроку
        float yaw = be.headsPrev[k][0] + Mth.wrapDegrees(be.heads[k][0] - be.headsPrev[k][0]) * pt;
        float pitch = Mth.lerp(pt, be.headsPrev[k][1], be.heads[k][1]);
        Vector3f neck = mv(right, -21.5f, 27.5f + breathe, 8);
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(neck.x / 16f, neck.y / 16f, neck.z / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + (right ? -12f : 12f)));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.scale(1.55f, 1.55f, 1.55f);
        pose.translate(-0.5f, -10f / 16f, -0.5f);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null,
                mc.getModelManager().getModel(AtlantHeadRenderer.HEAD), 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    /** Точка левого атланта или её зеркало для правого (пиксели). */
    static Vector3f mv(boolean mirror, float x, float y, float z) {
        return new Vector3f(mirror ? 16f - x : x, y, z);
    }

    /** Коробка левого атланта или её зеркало для правого. */
    static void mbox(VertexConsumer vc, PoseStack pose, boolean mirror, float x0, float y0, float z0, float x1, float y1, float z1,
                             ResourceLocation tex, int color, int light) {
        float a = mirror ? 16f - x1 : x0, b = mirror ? 16f - x0 : x1;
        box(vc, pose.last(), a, y0, z0, b, y1, z1, tex, color, light);
    }

    /** Конечность — брусок толщиной t от точки from до точки to (пиксели). */
    static void limb(VertexConsumer vc, PoseStack pose, Vector3f from, Vector3f to, float t, ResourceLocation tex, int light) {
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 1e-3f) {
            return;
        }
        d.normalize();
        pose.pushPose();
        pose.translate(from.x / 16f, from.y / 16f, from.z / 16f);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(0f, 1f, 0f, d.x, d.y, d.z));
        box(vc, pose.last(), -t / 2f, -t / 4f, -t / 2f, t / 2f, len + t / 4f, t / 2f, tex, 0xFFFFFFFF, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------------ медные големы
    /** Вещь в руке голема: маленький предмет, повёрнутый ладонью. */
    private static Consumer<PoseStack> held(ItemStack stack, float scale, float rotX, int light, MultiBufferSource buffers) {
        return hp -> {
            hp.scale(scale, scale, scale);
            if (rotX != 0f) {
                hp.mulPose(Axis.XP.rotationDegrees(rotX));
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, hp,
                    buffers, null, 0);
        };
    }

    /** Поза голема на этот момент: где стоит (блоки), куда смотрит, анимация, руки, что держит перед грудью. */
    private record GolemPose(Vector3f at, Vector3f forward, CopperGolemPuppet.Anim anim, CopperGolemPuppet.Arms arms,
                             Consumer<PoseStack> chest) {
    }

    /** Точка на полу полки (x, z — пиксели) в блоках. */
    private static Vector3f spot(float x, float floor, float z) {
        return new Vector3f(x / 16f, floor, z / 16f);
    }

    /** Переход между двумя точками полки за долю t (0..1): идёт, глядя по ходу. */
    private static GolemPose walk(float t, float x0, float z0, float x1, float z1, float floor, float time,
                                  @org.jetbrains.annotations.Nullable Consumer<PoseStack> inHand) {
        float e = t * t * (3f - 2f * t);
        Vector3f at = spot(Mth.lerp(e, x0, x1), floor, Mth.lerp(e, z0, z1));
        Vector3f fwd = new Vector3f(x1 - x0, 0f, z1 - z0);
        if (fwd.lengthSquared() < 1e-4f) {
            fwd.set(0f, 0f, -1f);
        }
        return new GolemPose(at, fwd.normalize(), new CopperGolemPuppet.Anim(time * 0.5f, true, false, 0f),
                new CopperGolemPuppet.Arms(-0.35f, 0.08f, -0.35f, -0.08f, 0f, inHand, null), null);
    }

    /**
     * Голем рисуется со сглаживанием положения и поворота: когда шкаф переходит от простоя к работе (и обратно), сцена
     * меняется, но голем не перескакивает, а доходит до новой точки (пока догоняет — перебирает ногами).
     */
    private static void renderSmooth(CryoMainframeBlockEntity be, int idx, PoseStack pose, MultiBufferSource buffers, int light, float time,
                                     GolemPose g, float scale, int variant) {
        float[] s = be.golemSmooth[idx];
        float tyaw = (float) Math.atan2(g.forward().x, g.forward().z);
        if (s[5] == 0f || time < s[4] || time - s[4] > 40f) {
            s[0] = g.at().x;
            s[1] = g.at().y;
            s[2] = g.at().z;
            s[3] = tyaw;
            s[5] = 1f;
        } else {
            float a = 1f - (float) Math.exp(-(time - s[4]) * 0.2f);
            s[0] += (g.at().x - s[0]) * a;
            s[1] += (g.at().y - s[1]) * a;
            s[2] += (g.at().z - s[2]) * a;
            float dyaw = Mth.wrapDegrees((float) Math.toDegrees(tyaw - s[3]));
            s[3] += (float) Math.toRadians(dyaw) * a;
        }
        s[4] = time;
        float lag = (float) Math.hypot(g.at().x - s[0], g.at().z - s[2]);
        CopperGolemPuppet.Anim anim = g.anim();
        if (lag > 0.03f && !anim.walking()) {
            anim = new CopperGolemPuppet.Anim(time * 0.5f, true, anim.carrying(), anim.bow());
        }
        Vector3f fwd = new Vector3f(Mth.sin(s[3]), 0f, Mth.cos(s[3]));
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(s[0], s[1], s[2]), fwd, new Vector3f(0f, 1f, 0f), scale, anim,
                variant, g.chest(), g.arms());
    }

    /**
     * Медный голем на крыше: пока шкаф работает, крутит вентиль-штурвал; в простое обходит гребень — постукивает по
     * манометру, протирает штурвал тряпкой, потягивается и оглядывается.
     */
    private static void roofGolem(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = 38.4f / 16f;
        GolemPose g;
        if (spin > 0.3f) {
            float sway = Mth.sin(time * 0.35f) * 0.25f;
            g = new GolemPose(spot(24.5f, floor, 3.2f), new Vector3f(Mth.sin(sway) * 0.3f, 0f, -1f),
                    new CopperGolemPuppet.Anim(time * 0.3f, false, true, 0.25f + 0.1f * Mth.sin(time * 0.7f)), null, hp -> { });
        } else {
            ItemStack rag = new ItemStack(net.minecraft.world.item.Items.PAPER);
            float cyc = ((time + be.getBlockPos().asLong() % 97) / 520f) % 1f;
            if (cyc < 0.26f) {
                // у манометра: наклонился и постукивает пальцем по стеклу
                float tap = Math.max(0f, Mth.sin(time * 0.9f));
                g = new GolemPose(spot(-10.5f, floor, 3.4f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.28f),
                        new CopperGolemPuppet.Arms(-1.25f - 0.12f * tap, 0.1f, -0.2f, -0.1f, 0.15f * Mth.sin(time * 0.05f), null, null), null);
            } else if (cyc < 0.36f) {
                g = walk((cyc - 0.26f) / 0.10f, -10.5f, 3.4f, 24.5f, 3.2f, floor, time, null);
            } else if (cyc < 0.68f) {
                // протирает штурвал: рука ходит по кругу
                float a = time * 0.35f;
                g = new GolemPose(spot(24.5f, floor, 3.2f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f),
                        new CopperGolemPuppet.Arms(-1.35f + 0.22f * Mth.sin(a), 0.25f + 0.2f * Mth.cos(a), -0.3f, -0.1f, 0f,
                                held(rag, 0.3f, 0f, light, buffers), null), null);
            } else if (cyc < 0.78f) {
                g = walk((cyc - 0.68f) / 0.10f, 24.5f, 3.2f, 6.5f, 8f, floor, time, null);
            } else {
                // потягивается и оглядывается
                float t = (cyc - 0.78f) / 0.22f;
                float up = Mth.clamp(Mth.sin(t * Mth.PI) * 1.4f, 0f, 1f);
                g = new GolemPose(spot(6.5f, floor, 8f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                        new CopperGolemPuppet.Arms(-2.9f * up, 0.3f * up, -2.9f * up, -0.3f * up, 0.6f * Mth.sin(time * 0.06f), null, null), null);
            }
        }
        renderSmooth(be, 2, pose, buffers, light, time, g, 0.3f, 1);
    }

    /**
     * Медные големы на полках шкафа.
     * Полка 0 — главный крио-инженер: в работе калибрует калькулятор ключом, крутит вентиль коллектора и сверяет
     * перфокарту; в простое протирает стекло дверей, смазывает калькуляторы из маслёнки, подметает иней щёткой и читает
     * журнал смены.
     * Полка 1 — оператор сопроцессоров: в работе носит сжатый сопроцессор к сборщикам и вставляет перфокарты в считыватель;
     * в простое полирует светящуюся матрицу, раскладывает перфокарты по стопкам, заводит хронометр и потягивается.
     */
    private static void shelfGolems(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float spin) {
        renderSmooth(be, 0, pose, buffers, light, time, shelf0Pose(be, buffers, light, time, spin), 0.24f, 1);
        renderSmooth(be, 1, pose, buffers, light, time, shelf1Pose(be, buffers, light, time, spin), 0.24f, 2);
    }

    private static GolemPose shelf0Pose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = 3.2f / 16f;
        Consumer<PoseStack> card = held(new ItemStack(ModItems.COPPER_PUNCH_CARD.get()), 0.4f, 0f, light, buffers);
        Consumer<PoseStack> wrench = held(new ItemStack(net.minecraft.world.item.Items.LEVER), 0.35f, 90f, light, buffers);
        if (spin <= 0.05f) {
            float cyc = ((time + be.getBlockPos().asLong() % 131) / 720f) % 1f;
            if (cyc < 0.20f) {
                // протирает стекло дверей изнутри: рука с тряпкой выводит круги
                float a = time * 0.4f;
                return new GolemPose(spot(-9f, floor, 5.5f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.04f),
                        new CopperGolemPuppet.Arms(-1.75f + 0.25f * Mth.sin(a), 0.3f + 0.25f * Mth.cos(a), -0.25f, -0.1f, 0.1f * Mth.sin(time * 0.05f),
                                held(new ItemStack(net.minecraft.world.item.Items.PAPER), 0.3f, 0f, light, buffers), null), null);
            }
            if (cyc < 0.26f) {
                return walk((cyc - 0.20f) / 0.06f, -9f, 5.5f, -5.3f, 18.5f, floor, time, null);
            }
            if (cyc < 0.46f) {
                // смазывает калькулятор: наклонился, маслёнка в руке «качает»
                float pump = Mth.sin(time * 0.8f);
                return new GolemPose(spot(-5.3f, floor, 18.5f), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.32f),
                        new CopperGolemPuppet.Arms(-0.95f + 0.12f * pump, 0.12f, -0.5f, -0.12f, 0.1f * pump,
                                held(new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE), 0.32f, 0f, light, buffers), null), null);
            }
            if (cyc < 0.52f) {
                return walk((cyc - 0.46f) / 0.06f, -5.3f, 18.5f, 12f, 17f, floor, time, null);
            }
            if (cyc < 0.74f) {
                // подметает иней щёткой: медленно ходит вдоль полки, руки метут
                float t = (cyc - 0.52f) / 0.22f;
                float x = 12f + 9f * (0.5f - 0.5f * Mth.cos(t * Mth.PI * 2f));
                float dir = Mth.sin(t * Mth.PI * 2f) >= 0f ? 1f : -1f;
                float sweep = Mth.sin(time * 0.6f);
                return new GolemPose(spot(x, floor, 17f), new Vector3f(dir, 0f, 0.15f), new CopperGolemPuppet.Anim(time * 0.25f, true, false, 0.22f),
                        new CopperGolemPuppet.Arms(-0.7f + 0.25f * sweep, 0.1f, -0.7f + 0.25f * sweep, -0.1f, 0f,
                                held(new ItemStack(net.minecraft.world.item.Items.BRUSH), 0.34f, 90f, light, buffers), null), null);
            }
            if (cyc < 0.80f) {
                return walk((cyc - 0.74f) / 0.06f, 12f, 17f, 2f, 17.5f, floor, time, null);
            }
            // читает журнал смены: книга в руках, голова наклонена, изредка перелистывает
            float flip = Math.max(0f, Mth.sin(time * 0.05f) - 0.9f) * 10f;
            return new GolemPose(spot(2f, floor, 17.5f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f),
                    new CopperGolemPuppet.Arms(-0.95f - 0.5f * flip, 0.18f, -0.95f, -0.18f, 0.12f * Mth.sin(time * 0.02f),
                            held(new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK), 0.34f, 0f, light, buffers), null), null);
        }

        // работа: калибровка калькулятора, вентиль коллектора, сверка перфокарты
        float cycle = ((time / 160f) + 0.12f) % 1.0f;
        if (cycle < 0.40f) {
            float ratchet = Mth.sin(time * 0.75f);
            return new GolemPose(spot(-5.3f, floor, 20.5f), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.3f, false, false, 0.26f),
                    new CopperGolemPuppet.Arms(-1.15f + 0.28f * ratchet, 0.12f, -0.65f, -0.1f, 0.15f * Mth.sin(time * 0.3f), wrench, null), null);
        }
        if (cycle < 0.48f) {
            return walk((cycle - 0.40f) / 0.08f, -5.3f, 20.5f, -12.2f, 19.5f, floor, time, wrench);
        }
        if (cycle < 0.72f) {
            float valvePump = Mth.sin(time * 0.45f);
            return new GolemPose(spot(-12.2f, floor, 19.5f), new Vector3f(-0.8f, 0f, 0.6f),
                    new CopperGolemPuppet.Anim(time * 0.4f, false, false, 0.16f + 0.10f * valvePump),
                    new CopperGolemPuppet.Arms(-1.3f + 0.15f * valvePump, 0.22f, -1.3f - 0.15f * valvePump, -0.22f, 0f, null, null), null);
        }
        if (cycle < 0.80f) {
            return walk((cycle - 0.72f) / 0.08f, -12.2f, 19.5f, 2.0f, 18.0f, floor, time, null);
        }
        return new GolemPose(spot(2.0f, floor, 18.0f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.2f, false, true, 0.08f),
                new CopperGolemPuppet.Arms(-0.95f, 0.14f, -0.95f, -0.14f, 0.18f * Mth.sin(time * 0.1f), null, null), card);
    }

    private static GolemPose shelf1Pose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = 16.2f / 16f;
        Consumer<PoseStack> coprocessor = held(new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()), 0.38f, 0f, light, buffers);
        Consumer<PoseStack> card = held(new ItemStack(ModItems.COPPER_PUNCH_CARD.get()), 0.35f, 90f, light, buffers);
        if (spin <= 0.05f) {
            float cyc = ((time + be.getBlockPos().asLong() % 113) / 760f + 0.5f) % 1f;
            if (cyc < 0.22f) {
                // полирует светящуюся матрицу на задней стенке: рука высоко, водит тряпкой из стороны в сторону
                float a = time * 0.3f;
                return new GolemPose(spot(-2f, floor, 25.5f), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                        new CopperGolemPuppet.Arms(-2.35f + 0.1f * Mth.cos(a), 0.45f * Mth.sin(a), -0.3f, -0.1f, 0.2f * Mth.sin(a),
                                held(new ItemStack(net.minecraft.world.item.Items.PAPER), 0.3f, 0f, light, buffers), null), null);
            }
            if (cyc < 0.28f) {
                return walk((cyc - 0.22f) / 0.06f, -2f, 25.5f, 20f, 23.5f, floor, time, null);
            }
            if (cyc < 0.50f) {
                // раскладывает перфокарты по стопкам: тянется то правой, то левой рукой
                float sw = Mth.sin(time * 0.3f);
                return new GolemPose(spot(20f, floor, 23.5f), new Vector3f(0.6f, 0f, 0.8f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.2f),
                        new CopperGolemPuppet.Arms(-0.8f - 0.35f * Math.max(0f, sw), 0.15f, -0.8f - 0.35f * Math.max(0f, -sw), -0.15f, 0.3f * sw,
                                sw > 0f ? card : null, sw <= 0f ? card : null), null);
            }
            if (cyc < 0.56f) {
                return walk((cyc - 0.50f) / 0.06f, 20f, 23.5f, 8f, 18f, floor, time, null);
            }
            if (cyc < 0.78f) {
                // заводит хронометр: часы в левой руке у груди, правая крутит головку
                float a = time * 0.5f;
                return new GolemPose(spot(8f, floor, 18f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.1f),
                        new CopperGolemPuppet.Arms(-1.05f + 0.1f * Mth.sin(a), 0.35f + 0.1f * Mth.cos(a), -1.05f, -0.2f, -0.15f, null,
                                held(new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()), 0.32f, 90f, light, buffers)), null);
            }
            if (cyc < 0.84f) {
                return walk((cyc - 0.78f) / 0.06f, 8f, 18f, -9f, 19f, floor, time, null);
            }
            // потягивается и оглядывает матрицу
            float t = (cyc - 0.84f) / 0.16f;
            float up = Mth.clamp(Mth.sin(t * Mth.PI) * 1.4f, 0f, 1f);
            return new GolemPose(spot(-9f, floor, 19f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                    new CopperGolemPuppet.Arms(-2.9f * up, 0.3f * up, -2.9f * up, -0.3f * up, 0.5f * Mth.sin(time * 0.05f), null, null), null);
        }

        // работа: сопроцессор к сборщикам, передача, перфокарта в считыватель
        float cycle = ((time / 160f) + 0.62f) % 1.0f;
        if (cycle < 0.42f) {
            float t = cycle / 0.42f;
            return new GolemPose(spot(Mth.lerp(t, 22.0f, 6.5f), floor, 18.0f), new Vector3f(-1f, 0f, 0f),
                    new CopperGolemPuppet.Anim(time * 0.5f, true, true, 0.1f), null, coprocessor);
        }
        if (cycle < 0.65f) {
            float lift = Mth.clamp((cycle - 0.42f) / 0.06f, 0f, 1f);
            float rX = Mth.lerp(lift, -1.25f, -1.55f);
            return new GolemPose(spot(6.5f, floor, 17.0f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.3f, false, true, 0f),
                    new CopperGolemPuppet.Arms(rX, 0.2f, rX, -0.2f, 0f, null, null), coprocessor);
        }
        if (cycle < 0.73f) {
            return walk((cycle - 0.65f) / 0.08f, 6.5f, 17.0f, 16.3f, 20.8f, floor, time, card);
        }
        float slide = 0.5f + 0.5f * Mth.sin(time * 0.7f);
        return new GolemPose(spot(16.3f, floor, 20.8f), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.3f, false, false, 0.15f),
                new CopperGolemPuppet.Arms(-1.3f + 0.25f * slide, 0.08f, -0.6f, -0.15f, 0f, card, null), null);
    }

    /** Створка: петля в x = hinge, свободный край в x = edge; angle — поворот вокруг петли (градусы). */
    private static void door(PoseStack pose, MultiBufferSource buffers, float hinge, float edge, float angle, int light) {
        float w = edge - hinge;
        pose.pushPose();
        pose.translate(hinge / 16f, 0f, 1f / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        float x0 = Math.min(0, w), x1 = Math.max(0, w);
        // рама створки
        box(vc, p, x0, 3, -0.6f, x1, 4.4f, 0.6f, BRASS, white, light);
        box(vc, p, x0, 26.6f, -0.6f, x1, 28, 0.6f, BRASS, white, light);
        box(vc, p, x0, 4.4f, -0.6f, x0 + 1.4f, 26.6f, 0.6f, BRASS, white, light);
        box(vc, p, x1 - 1.4f, 4.4f, -0.6f, x1, 26.6f, 0.6f, BRASS, white, light);
        box(vc, p, x0 + 1.4f, 15f, -0.4f, x1 - 1.4f, 16f, 0.4f, BRASS, white, light);
        // ручка у свободного края
        float hx = w > 0 ? x1 - 3.2f : x0 + 2.2f;
        box(vc, p, hx, 13f, -1.6f, hx + 1f, 18f, -0.6f, BRASS, 0xFFFFE6A0, light);
        // стекло
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose gp = pose.last();
        wall(glass, gp, x0 + 1.4f, 4.4f, x1 - 1.4f, 15f, 0f, GLASS, 0xB0DDF6FF, light);
        wall(glass, gp, x0 + 1.4f, 16f, x1 - 1.4f, 26.6f, 0f, GLASS, 0xB0DDF6FF, light);
        pose.popPose();
    }

    /** Красный вентиль-штурвал: обод из сегментов, четыре спицы и ступица; крутится, пока шкаф работает. */
    static void valve(PoseStack pose, VertexConsumer vc, float cx, float cy, float z, float r, float rot, int light) {
        int red = 0xFFC8432F, hub = 0xFFFFE6A0;
        pose.pushPose();
        pose.translate(cx / 16f, cy / 16f, z / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(rot));
        for (int k = 0; k < 12; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 30f));
            box(vc, pose.last(), -0.95f, r - 0.5f, -0.5f, 0.95f, r + 0.5f, 0.5f, BRASS, red, light);
            pose.popPose();
        }
        for (int k = 0; k < 4; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 90f + 45f));
            box(vc, pose.last(), -0.3f, 0f, -0.3f, 0.3f, r - 0.4f, 0.3f, BRASS, red, light);
            pose.popPose();
        }
        box(vc, pose.last(), -0.9f, -0.9f, -0.8f, 0.9f, 0.9f, 0.6f, BRASS, hub, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------ квадраты
    static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /** Плоская стенка в плоскости z (лицом к -Z), с плиткой текстуры по 16 пикселей. */
    static void wall(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
                             ResourceLocation tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        for (float x = x0; x < x1 - 1e-3f; x += 16f) {
            float xe = Math.min(x1, x + 16f);
            for (float y = y0; y < y1 - 1e-3f; y += 16f) {
                float ye = Math.min(y1, y + 16f);
                float u0 = s.getU((x - x0) % 16f / 16f), u1 = s.getU(((x - x0) % 16f + (xe - x)) / 16f);
                float v0 = s.getV(1f - ((y - y0) % 16f + (ye - y)) / 16f), v1 = s.getV(1f - (y - y0) % 16f / 16f);
                quad(vc, p, new float[]{xe, y, z}, new float[]{x, y, z}, new float[]{x, ye, z}, new float[]{xe, ye, z},
                        u0, v0, u1, v1, color, light, 0, 0, -1);
            }
        }
    }

    /** Стенка лицом к -Z с текстурой целиком, сжатой под её размер (циферблаты, таблички). */
    static void fitWall(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
                                ResourceLocation tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        quad(vc, p, new float[]{x1, y0, z}, new float[]{x0, y0, z}, new float[]{x0, y1, z}, new float[]{x1, y1, z},
                s.getU0(), s.getV0(), s.getU1(), s.getV1(), color, light, 0, 0, -1);
    }

    /** Коробка (пиксели); каждая грань берёт кусок текстуры по своему размеру (больше 16 — тянется по плиткам). */
    static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            ResourceLocation tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        // север (-Z) и юг (+Z)
        tile(vc, p, s, color, light, x1 - x0, y1 - y0, (a, b) -> new float[]{x1 - a, y0 + b, z0}, 0, 0, -1);
        tile(vc, p, s, color, light, x1 - x0, y1 - y0, (a, b) -> new float[]{x0 + a, y0 + b, z1}, 0, 0, 1);
        // запад (-X) и восток (+X)
        tile(vc, p, s, color, light, z1 - z0, y1 - y0, (a, b) -> new float[]{x0, y0 + b, z0 + a}, -1, 0, 0);
        tile(vc, p, s, color, light, z1 - z0, y1 - y0, (a, b) -> new float[]{x1, y0 + b, z1 - a}, 1, 0, 0);
        // верх и низ
        tile(vc, p, s, color, light, x1 - x0, z1 - z0, (a, b) -> new float[]{x0 + a, y1, z1 - b}, 0, 1, 0);
        tile(vc, p, s, color, light, x1 - x0, z1 - z0, (a, b) -> new float[]{x0 + a, y0, z0 + b}, 0, -1, 0);
    }

    private interface Map2 {
        float[] at(float a, float b);
    }

    /** Грань w x h (пиксели) плитками по 16: at(a, b) — точка грани, a вправо, b вверх (как смотрит зритель снаружи). */
    private static void tile(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int color, int light, float w, float h, Map2 at,
                             float nx, float ny, float nz) {
        for (float a = 0; a < w - 1e-3f; a += 16f) {
            float ae = Math.min(w, a + 16f);
            for (float b = 0; b < h - 1e-3f; b += 16f) {
                float be = Math.min(h, b + 16f);
                float u0 = s.getU(0f), u1 = s.getU((ae - a) / 16f);
                float v0 = s.getV(1f - (be - b) / 16f), v1 = s.getV(1f);
                quad(vc, p, at.at(a, b), at.at(ae, b), at.at(ae, be), at.at(a, be), u0, v0, u1, v1, color, light, nx, ny, nz);
            }
        }
    }

    /** a — левый низ, b — правый низ, c — правый верх, d — левый верх (снаружи); координаты в пикселях. */
    static void quad(VertexConsumer vc, PoseStack.Pose p, float[] a, float[] b, float[] c, float[] d,
                             float u0, float v0, float u1, float v1, int color, int light, float nx, float ny, float nz) {
        vertex(vc, p, a, u0, v1, color, light, nx, ny, nz);
        vertex(vc, p, b, u1, v1, color, light, nx, ny, nz);
        vertex(vc, p, c, u1, v0, color, light, nx, ny, nz);
        vertex(vc, p, d, u0, v0, color, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float[] v, float u, float w, int color, int light,
                               float nx, float ny, float nz) {
        vc.addVertex(p, v[0] / 16f, v[1] / 16f, v[2] / 16f).setColor(color).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
