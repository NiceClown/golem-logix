package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.system.CatalogStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.CryoMainframeRenderer.box;
import static com.golemlogix.client.CryoMainframeRenderer.fitWall;
import static com.golemlogix.client.CryoMainframeRenderer.quad;
import static com.golemlogix.client.CryoMainframeRenderer.sprite;
import static com.golemlogix.client.CryoMainframeRenderer.wall;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Собранная Картотека перфокарт: ржаво-медный шкаф 3 x 2 x 2 с двадцатью латунными ящиками-картотеками (по два столбца
 * слева и справа), в середине — бирюзовый экран-иллюминатор с указателем по списку карт, шкала заполнения и рычажки; на крыше
 * — буквенные барабаны с шестернёй и труба с паром; по бокам на медных постаментах стоят два больших медных голема (их
 * окисленность — как у статуй, из которых шкаф собран): ближняя рука держит угол крыши, дальняя — перфокарту. Пока окно
 * картотеки открыто, ящики выдвигаются и видно карты, барабаны перебирают буквы, големы оборачиваются к шкафу.
 * <p>
 * Координаты — в пикселях системы хозяина «лицом на север»: шкаф X -16..32, Y 0..32, Z 0..32 (фасад — Z 0), големы — в
 * столбцах X -32..-16 и 32..48 переднего ряда.
 */
public class CardCatalogRenderer implements BlockEntityRenderer<CardCatalogBlockEntity> {
    private static final ResourceLocation RUST = tex("resonator_body"), BRASS = tex("sat_brass"),
            DARK = tex("sat_dark"), PLATE = tex("brass_plate"), DRAWER = tex("catalog_drawer"), DRUM = tex("catalog_drum"),
            SCREEN = tex("catalog_screen"), PANEL = tex("cryo_panel"), FROST_PIPE = tex("resonator_pipe"), ICE = tex("sat_ice"),
            CARD = tex("encoder_cards"), VINE = ResourceLocation.withDefaultNamespace("block/vine"),
            PIPE = ResourceLocation.withDefaultNamespace("block/copper_block");
    private static final ResourceLocation[] PEDESTAL = {
            ResourceLocation.withDefaultNamespace("block/copper_block"), ResourceLocation.withDefaultNamespace("block/exposed_copper"),
            ResourceLocation.withDefaultNamespace("block/weathered_copper"), ResourceLocation.withDefaultNamespace("block/oxidized_copper")};
    public static final int DRAWERS = 20;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name);
    }

    public CardCatalogRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CardCatalogBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState s = be.getBlockState();
        if (!s.hasProperty(CardCatalogBlock.FACING)) {
            return new AABB(pos);
        }
        Direction f = s.getValue(CardCatalogBlock.FACING);
        AABB box = new AABB(pos);
        for (int i = 0; i < CatalogStructure.CELLS.length; i++) {
            box = box.minmax(new AABB(CatalogStructure.cellPos(pos, f, i)));
        }
        return box.inflate(1.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** Левый край, низ ящика i (0..19): столбцы слева направо, ряды снизу вверх. */
    static float drawerX(int i) {
        int col = i / 5;
        return new float[]{-13.6f, -5.8f, 14.4f, 22.2f}[col];
    }

    static float drawerY(int i) {
        return 3.6f + (i % 5) * 4.92f;
    }

    @Override
    public void render(CardCatalogBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CardCatalogBlock.FORMED) || !state.getValue(CardCatalogBlock.FORMED)) {
            return;
        }
        Direction facing = state.getValue(CardCatalogBlock.FACING);
        float time = level.getGameTime() + partialTick;
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().relative(facing));
        light = LightTexture.pack(Math.max(LightTexture.block(light), 6), LightTexture.sky(light));
        float open = Mth.lerp(partialTick, be.drawersPrev, be.drawers);
        open = open * open * (3 - 2 * open);
        float drums = Mth.lerp(partialTick, be.drumsPrev, be.drums);
        int cap = Math.max(1, be.clientCapacity());
        float fill = Math.min(1f, be.clientCards() / (float) cap);
        if (be.clientCapacity() == 0) {
            fill = 0f;
        }

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;

        // корпус: цоколь, задняя стенка, боковины, крыша
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, white, light);
        box(vc, p, -15, 2, 29, 31, 30, 32, RUST, white, light);
        box(vc, p, -16, 2, 1.5f, -14, 30, 32, RUST, white, light);
        box(vc, p, 30, 2, 1.5f, 32, 30, 32, RUST, white, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, RUST, white, light);
        // латунная рама фасада, средние стойки и тёмная ниша за ящиками
        box(vc, p, -16, 2, -0.5f, -14, 30, 1.5f, BRASS, white, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 1.5f, BRASS, white, light);
        box(vc, p, -14, 28.6f, -0.5f, 30, 30, 1.5f, BRASS, white, light);
        box(vc, p, -14, 2, -0.5f, 30, 3.2f, 1.5f, BRASS, white, light);
        box(vc, p, 1.9f, 3.2f, -0.3f, 3.2f, 28.6f, 1.5f, BRASS, white, light);
        box(vc, p, 12.8f, 3.2f, -0.3f, 14.1f, 28.6f, 1.5f, BRASS, white, light);
        // Сплошная глухая задняя стенка прямо за ящиками (полностью закрывает сквозной просвет к стене)
        box(vc, p, -14.0f, 3.2f, 0.6f, 1.9f, 28.6f, 2.0f, DARK, 0xFF2A221C, light);
        box(vc, p, 14.1f, 3.2f, 0.6f, 30.0f, 28.6f, 2.0f, DARK, 0xFF2A221C, light);
        // Горизонтальные и вертикальные разделители ячеек ящиков (направляющие полки и перемычки)
        renderDrawerDividers(vc, p, light);
        wall(vc, p, -14, 3.2f, 30, 28.6f, 13.5f, DARK, 0xFF3A3028, light);

        // ящики: выдвигаются, пока окно открыто (волной), в простое изредка «перебирается» один
        int filled = fill <= 0f ? 0 : Math.max(1, Math.round(fill * DRAWERS));
        int idleDrawer = (int) ((time / 70f) % DRAWERS);
        float idlePhase = (time % 70f) / 70f;
        long seed = be.getBlockPos().asLong();
        for (int i = 0; i < DRAWERS; i++) {
            float wave = 0.5f + 0.5f * Mth.sin(time * 0.07f + i * 2.3f + seed % 7);
            float out = open * (1.2f + 4.6f * wave * wave);
            if (i == (idleDrawer * 7 + 3) % DRAWERS && idlePhase < 0.5f) {
                out = Math.max(out, 3.2f * Mth.sin(idlePhase * 2f * Mth.PI));
            }
            drawer(vc, p, drawerX(i), drawerY(i), out, i < filled, i, light);
        }

        // середина: латунная плита, экран-иллюминатор, шкала заполнения, щель для карт и рычажки
        box(vc, p, 3.2f, 3.2f, 0.3f, 12.8f, 28.6f, 1.6f, PLATE, white, light);
        box(vc, p, 3.6f, 16.4f, -0.4f, 12.4f, 27.6f, 0.3f, BRASS, 0xFFC8A060, light);
        fitWall(vc, p, 3.4f, 17.1f, 12.6f, 26.9f, -0.5f, SCREEN, white, LightTexture.FULL_BRIGHT);
        box(vc, p, 4.2f, 14.2f, -0.4f, 11.8f, 15.6f, 0.3f, DARK, white, light);
        float fx = 4.6f + 6.8f * fill;
        if (fill > 0f) {
            box(vc, p, 4.6f, 14.5f, -0.5f, fx, 15.3f, -0.35f, PANEL, 0xFF40F0E8, LightTexture.FULL_BRIGHT);
        }
        box(vc, p, 4.4f, 4.4f, -0.3f, 11.6f, 5.4f, 0.3f, DARK, 0xFF201810, light);
        box(vc, p, 4.0f, 3.9f, -0.5f, 12.0f, 4.4f, 0.3f, BRASS, white, light);
        for (int k = 0; k < 3; k++) {
            float lx = 5.4f + k * 2.6f;
            float tilt = (k == 1 ? 1 : -1) * 28f * (open > 0.5f ? -1 : 1) + 4f * Mth.sin(time * 0.05f + k);
            pose.pushPose();
            pose.translate(lx / 16f, 8.6f / 16f, -0.2f / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(-25f + tilt));
            box(vc, pose.last(), -0.35f, 0f, -0.35f, 0.35f, 4.6f, 0.35f, BRASS, 0xFFB0B0B0, light);
            box(vc, pose.last(), -0.75f, 4.4f, -0.75f, 0.75f, 5.9f, 0.75f, BRASS, k == 1 ? 0xFFC8432F : 0xFFFFE6A0, light);
            pose.popPose();
            box(vc, p, lx - 1f, 7.6f, -0.6f, lx + 1f, 9.6f, 0.3f, DARK, white, light);
        }

        // крыша: короб буквенных барабанов, шестерня, труба с паром и обмёрзшие трубы
        roof(pose, vc, light, time, drums, open);
        vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        p = pose.last();
        // хладагентные трубы: вниз по левой и правой задней кромке и поперёк крыши
        box(vc, p, -15.6f, 2, 30.4f, -12.6f, 33, 33.4f, FROST_PIPE, white, light);
        box(vc, p, -15.6f, 32, 24.4f, 23, 35, 27.4f, FROST_PIPE, white, light);
        box(vc, p, 28.6f, 2, 30.4f, 31.6f, 30, 33.4f, FROST_PIPE, white, light);
        for (float y : new float[]{8f, 18f, 26f}) {
            box(vc, p, -15.9f, y, 30.1f, -12.3f, y + 1.2f, 33.7f, BRASS, 0xFFC8A060, light);
            box(vc, p, 28.3f, y + 2f, 30.1f, 31.9f, y + 3.2f, 33.7f, BRASS, 0xFFC8A060, light);
        }
        for (float[] ic : new float[][]{{-10.5f, 2.2f}, {-3f, 1.4f}, {6.5f, 2.6f}, {17f, 1.6f}}) {
            box(vc, p, ic[0], 32 - ic[1], 25.4f, ic[0] + 0.6f, 32, 26f, ICE, white, light);
        }

        // плющ: свисает с крыши по углам фасада и по боку
        VertexConsumer vine = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        int green = 0xFF5A9A3A;
        wall(vine, pose.last(), -16.4f, 19f, -10.5f, 32.4f, -0.9f, VINE, green, light);
        wall(vine, pose.last(), 27.8f, 23f, 32.4f, 32.4f, -0.9f, VINE, green, light);
        wall(vine, pose.last(), 5.5f, 28.2f, 10.5f, 32.4f, -0.8f, VINE, green, light);

        // големы по бокам
        for (int k = 0; k < 2; k++) {
            sideGolem(be, pose, buffers, light, time, open, k);
        }
        pose.popPose();
    }

    /** Ящик: лицо с ярлыком и ручкой, бока; выдвинутая часть показывает торчащие перфокарты. */
    private static void drawer(VertexConsumer vc, PoseStack.Pose p, float x, float y, float out, boolean cards, int i, int light) {
        float w = 7.4f, h = 4.4f, z = 0.6f - out;
        int white = 0xFFFFFFFF;
        if (out > 0.05f) {
            // Корпус ящика (полый короб со стенками и дном)
            box(vc, p, x + 0.2f, y + 0.2f, z, x + w - 0.2f, y + 0.5f, 0.6f, RUST, 0xFF8A6A58, light); // дно
            box(vc, p, x + 0.2f, y + 0.5f, z, x + 0.65f, y + h - 0.3f, 0.6f, RUST, 0xFFB09080, light); // левая стенка
            box(vc, p, x + w - 0.65f, y + 0.5f, z, x + w - 0.2f, y + h - 0.3f, 0.6f, RUST, 0xFFB09080, light); // правая стенка

            // Внутреннее наполнение: архивные папочки с выступающими закладками-ярлычками и карточками
            if (out > 0.15f) {
                float startZ = z + 0.45f;
                float endZ = 0.50f;
                int folderIdx = 0;
                for (float cz = startZ; cz < endZ; cz += 0.72f, folderIdx++) {
                    // 1. Папочка-регистратор (плотная манильская бумага)
                    int folderColor = ((folderIdx + i) % 2 == 0) ? 0xFFE8D6B8 : 0xFFDEC29E;
                    box(vc, p, x + 0.75f, y + 0.5f, cz, x + w - 0.75f, y + h - 0.45f, cz + 0.12f, CARD, folderColor, light);

                    // 2. Выступающий вверх ярлычок-закладка папки (3 позиции: слева, по центру, справа)
                    int tabPos = (folderIdx + i * 2) % 3;
                    float tabW = 1.9f;
                    float tabX = switch (tabPos) {
                        case 0 -> x + 0.9f;
                        case 1 -> x + (w - tabW) / 2f;
                        default -> x + w - 0.9f - tabW;
                    };
                    float tabRise = 0.48f;
                    int tabColor = ((folderIdx + i) % 2 == 0) ? 0xFFCCA265 : 0xFFBF9456;
                    box(vc, p, tabX, y + h - 0.45f, cz, tabX + tabW, y + h - 0.45f + tabRise, cz + 0.12f, BRASS, tabColor, light);

                    // 3. Бланки и перфокарты внутри каждой папки
                    float sheetZ = cz + 0.18f;
                    if (sheetZ + 0.36f < endZ) {
                        float sheetRise = 0.15f + ((folderIdx * 5 + i * 3) % 4) * 0.08f;
                        box(vc, p, x + 0.90f, y + 0.5f, sheetZ, x + w - 0.90f, y + h - 0.55f + sheetRise, sheetZ + 0.36f, CARD, 0xFFF5EEDB, light);
                    }
                }
            }
        }
        box(vc, p, x, y, z - 0.4f, x + w, y + h, z, RUST, white, light);
        fitWall(vc, p, x, y, x + w, y + h, z - 0.42f, DRAWER, white, light);
    }

    /** Крыша: латунный короб с четырьмя буквенными барабанами (крутятся, как счётчик), шестерня сбоку, труба с паром сзади. */
    private static void roof(PoseStack pose, VertexConsumer vc, int light, float time, float drums, float open) {
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        box(vc, p, -13, 32, 0.6f, 29, 33.2f, 11, BRASS, white, light);
        box(vc, p, -13, 33.2f, 9.6f, 29, 40, 11, RUST, white, light);
        box(vc, p, -13, 39.2f, 0.6f, 29, 40.2f, 11, BRASS, white, light);
        for (float x : new float[]{-13f, -3.6f, 6.2f, 15.6f, 27.6f}) {
            box(vc, p, x, 33.2f, 1.2f, x + 1.4f, 39.2f, 9.6f, BRASS, white, light);
        }
        // барабаны: восьмигранные призмы вокруг оси X
        float[][] drumX = {{-11.6f, -3.6f}, {-2.2f, 6.2f}, {7.6f, 15.6f}, {17.0f, 27.6f}};
        for (int d = 0; d < 4; d++) {
            float angle = drums * 45f / (1 + (3 - d) * 0.8f) + d * 17f;
            pose.pushPose();
            pose.translate(0f, 36.2f / 16f, 5.4f / 16f);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            for (int f = 0; f < 8; f++) {
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(f * 45f));
                box(vc, pose.last(), drumX[d][0] + 0.2f, -1.2f, -2.85f, drumX[d][1] - 0.2f, 1.2f, -2.35f, DRUM, white, light);
                pose.popPose();
            }
            pose.popPose();
        }
        // шестерня на правом торце короба
        pose.pushPose();
        pose.translate(29.6f / 16f, 36.2f / 16f, 5.4f / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(-drums * 45f * 1.6f - time * (open > 0.1f ? 0f : 0.2f)));
        box(vc, pose.last(), 0f, -2.6f, -2.6f, 1.2f, 2.6f, 2.6f, BRASS, 0xFFFFE6A0, light);
        for (int t = 0; t < 10; t++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(t * 36f));
            box(vc, pose.last(), 0f, 2.4f, -0.6f, 1.2f, 3.6f, 0.6f, BRASS, 0xFFFFE6A0, light);
            pose.popPose();
        }
        box(vc, pose.last(), -0.2f, -0.8f, -0.8f, 1.6f, 0.8f, 0.8f, DARK, white, light);
        pose.popPose();
        // труба с паром сзади справа и её латунный венец
        box(vc, pose.last(), 21, 32, 21, 25.6f, 44, 25.6f, PIPE, 0xFFD09070, light);
        box(vc, pose.last(), 20.4f, 44, 20.4f, 26.2f, 45.2f, 26.2f, BRASS, white, light);
        box(vc, pose.last(), 20.6f, 36, 20.6f, 26f, 37f, 26f, BRASS, white, light);
    }

    /**
     * Голем на боку (k = 0 — слева, 1 — справа): стоит на медном постаменте, вросши плечом в боковину шкафа; ближняя рука
     * поднята и держит угол крыши, дальняя держит перфокарту (пока окно открыто — машет ею), голова поворачивается к ящикам.
     */
    private static void sideGolem(CardCatalogBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float open, int k) {
        boolean right = k == 1;
        int ox = be.golemOxidation(k);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        // Постамент: полный блок 16x16 снаружи шкафа
        float px0 = right ? 32f : -32f, px1 = right ? 48f : -16f;
        box(vc, pose.last(), px0, 0, 0.5f, px1, 6, 15.5f, PEDESTAL[Math.max(0, Math.min(3, ox))], 0xFFFFFFFF, light);
        box(vc, pose.last(), px0 - 0.4f, 5.6f, 0.1f, px1 + 0.4f, 6.4f, 15.9f, BRASS, 0xFFFFFFFF, light);
        // обмёрзшая труба из постамента в боковину
        float a0 = right ? 30f : -18f, a1 = right ? 34f : -14f;
        box(vc, pose.last(), a0, 2.2f, 11, a1, 4.8f, 13.6f, FROST_PIPE, 0xFFFFFFFF, light);

        // Голем стоит по центру своего постамента снаружи шкафа, с гарантированным зазором 2+ пикселя до стенок
        float cx = right ? 40.5f : -24.5f;
        float cz = 7.5f;
        float breathe = Mth.sin(time * 0.06f + k * 2f) * 0.02f;
        float wave = open > 0.05f ? open * (0.35f + 0.25f * Mth.sin(time * 0.25f + k)) : 0.08f * Mth.sin(time * 0.03f + k);
        float look = open > 0.05f ? open * 0.35f : 0.15f * Mth.sin(time * 0.012f + k * 3f);
        float scale = 1.15f + breathe;

        // Отрисовка медной перфокарты, зажатой в руке голема
        java.util.function.Consumer<PoseStack> holdCard = hp -> {
            hp.scale(0.38f, 0.38f, 0.38f);
            hp.mulPose(Axis.XP.rotationDegrees(90f));
            hp.mulPose(Axis.ZP.rotationDegrees(right ? -15f : 15f));
            hp.translate(0f, 0.15f, 0f);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    new net.minecraft.world.item.ItemStack(com.golemlogix.init.ModItems.COPPER_PUNCH_CARD.get()),
                    net.minecraft.world.item.ItemDisplayContext.FIXED,
                    light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    hp,
                    buffers,
                    be.getLevel(),
                    0
            );
        };

        // Внутренняя рука поднята вверх и опирается на внешний угол крыши шкафа;
        // Внешняя рука держит медную перфокарту и машет ею при открытии
        CopperGolemPuppet.Arms arms = right
                ? new CopperGolemPuppet.Arms(-0.65f - wave, 0.10f, -1.30f, -0.05f, look, holdCard, null)
                : new CopperGolemPuppet.Arms(-1.30f, 0.05f, -0.65f - wave, -0.10f, -look, null, holdCard);

        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(cx / 16f, 6f / 16f, cz / 16f), new Vector3f(0f, 0f, -1f),
                new Vector3f(0f, 1f, 0f), scale, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f), ox, null, arms);
    }

    private static void renderDrawerDividers(VertexConsumer vc, PoseStack.Pose p, int light) {
        int white = 0xFFFFFFFF;
        // Вертикальные перемычки и боковые планки левой секции (X: -14.0 .. 1.9)
        box(vc, p, -14.0f, 3.2f, 0.2f, -13.6f, 28.6f, 0.7f, BRASS, white, light);
        box(vc, p, -6.2f, 3.2f, 0.2f, -5.8f, 28.6f, 0.7f, BRASS, white, light);
        box(vc, p, 1.6f, 3.2f, 0.2f, 1.9f, 28.6f, 0.7f, BRASS, white, light);

        // Вертикальные перемычки и боковые планки правой секции (X: 14.1 .. 30.0)
        box(vc, p, 14.1f, 3.2f, 0.2f, 14.4f, 28.6f, 0.7f, BRASS, white, light);
        box(vc, p, 21.8f, 3.2f, 0.2f, 22.2f, 28.6f, 0.7f, BRASS, white, light);
        box(vc, p, 29.6f, 3.2f, 0.2f, 30.0f, 28.6f, 0.7f, BRASS, white, light);

        // Горизонтальные полки между рядами ящиков
        float[][] shelves = {
                {3.2f, 3.6f},    // под нижним рядом
                {8.0f, 8.52f},   // между 0 и 1 рядами
                {12.92f, 13.44f},// между 1 и 2 рядами
                {17.84f, 18.36f},// между 2 и 3 рядами
                {22.76f, 23.28f},// между 3 и 4 рядами
                {27.68f, 28.6f}  // над верхним рядом
        };
        for (float[] sh : shelves) {
            box(vc, p, -14.0f, sh[0], 0.2f, 1.9f, sh[1], 0.7f, BRASS, white, light);
            box(vc, p, 14.1f, sh[0], 0.2f, 30.0f, sh[1], 0.7f, BRASS, white, light);
        }
    }
}
