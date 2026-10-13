package com.golemlogix.client;

import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Снеговичок в тыквенной голове с коробкой, бегущий по запитанным трубам. На всю связную сеть труб он один
 * (см. {@link PipeRunners}); в базовой трубе виден лишь он головой (тело не влезает), в плотной — целиком.
 */
public class SnowPipeRenderer implements BlockEntityRenderer<PipeBlockEntity> {
    public SnowPipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final net.minecraft.client.resources.model.ModelResourceLocation PLUG_BASIC = plugModel("basic_snow_pipe_plug");
    public static final net.minecraft.client.resources.model.ModelResourceLocation PLUG_DENSE = plugModel("dense_frost_pipe_plug");
    public static final net.minecraft.client.resources.model.ModelResourceLocation BAND_BASIC = plugModel("basic_snow_pipe_band");
    public static final net.minecraft.client.resources.model.ModelResourceLocation BAND_DENSE = plugModel("dense_frost_pipe_band");
    /** Ворота складского дока: тупик со складом закрыт не заглушкой, а рольставней с фонарём. */
    public static final net.minecraft.client.resources.model.ModelResourceLocation DOCK_BASIC = plugModel("basic_snow_pipe_dock");
    public static final net.minecraft.client.resources.model.ModelResourceLocation DOCK_DENSE = plugModel("dense_frost_pipe_dock");

    private static net.minecraft.client.resources.model.ModelResourceLocation plugModel(String name) {
        return net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "block/" + name));
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    private static final class WarehouseKey {
        final long pos;
        final int dir;

        WarehouseKey(BlockPos p, Direction d) {
            this.pos = p.asLong();
            this.dir = d.ordinal();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WarehouseKey that)) return false;
            return pos == that.pos && dir == that.dir;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(pos) * 31 + dir;
        }
    }

    private static final Map<WarehouseKey, Boolean> WAREHOUSE_CACHE = new HashMap<>();
    private static long lastCacheClearTime = 0;

    /**
     * Возвращает направления тупиков/терминалов для данной трубы:
     * 1. Любая сторона с установленной заглушкой (isPlugged).
     * 2. Любой открытый тупик линии (если у трубы ровно 1 соединение типа PIPE, а противоположная сторона свободна Link.NONE).
     * 3. Одиночная труба (без связей) получает терминал на северной стороне.
     */
    public static List<Direction> getTerminalDirections(PipeBlockEntity be, BlockState state) {
        List<Direction> terminals = new ArrayList<>(2);
        for (Direction d : Direction.values()) {
            if (be.isPlugged(d)) {
                terminals.add(d);
            }
        }
        int pipeLinks = 0;
        Direction singlePipeDir = null;
        for (Direction d : Direction.values()) {
            if (SnowPipeBlock.link(state, d) == SnowPipeBlock.Link.PIPE) {
                pipeLinks++;
                singlePipeDir = d;
            }
        }
        if (pipeLinks == 1) {
            Direction deadEnd = singlePipeDir.getOpposite();
            if (SnowPipeBlock.link(state, deadEnd) == SnowPipeBlock.Link.NONE && !terminals.contains(deadEnd)) {
                terminals.add(deadEnd);
            }
        } else if (pipeLinks == 0) {
            // одиночная труба: заглушка — только на глухом конце, если выход у неё один (иначе она бы закрыла устройство)
            Direction only = null;
            int links = 0;
            for (Direction d : Direction.values()) {
                if (SnowPipeBlock.linked(state, d)) {
                    links++;
                    only = d;
                }
            }
            if (links == 1 && SnowPipeBlock.link(state, only.getOpposite()) == SnowPipeBlock.Link.NONE
                    && !terminals.contains(only.getOpposite())) {
                terminals.add(only.getOpposite());
            }
        }
        return terminals;
    }

    /**
     * Проверяет, является ли данный тупик трубы Складом.
     * Правило: склады не должны нагромождаться вплотную (в радиусе 3 блоков не более 1 склада).
     * На разных ветках сети (на расстоянии >= 4 блоков) каждая ветка получает полноценный склад.
     * В остальных случаях (когда склад уже есть в радиусе 3 блоков) остаётся латунная заглушка с манометром.
     */
    public static boolean isWarehouse(Level level, BlockPos pos, Direction dir) {
        if (!dir.getAxis().isHorizontal()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (gameTime - lastCacheClearTime > 40L || WAREHOUSE_CACHE.size() > 1024) {
            WAREHOUSE_CACHE.clear();
            lastCacheClearTime = gameTime;
        }
        WarehouseKey key = new WarehouseKey(pos, dir);
        Boolean cached = WAREHOUSE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        long myScore = getWarehouseScore(pos, dir);
        boolean isSmallest = true;

        // Проверяем другие стороны того же блока (если на одном блоке несколько тупиков)
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity thisBe) {
            List<Direction> myTerminals = getTerminalDirections(thisBe, thisBe.getBlockState());
            for (Direction otherDir : myTerminals) {
                if (otherDir != dir && getWarehouseScore(pos, otherDir) < myScore) {
                    isSmallest = false;
                    break;
                }
            }
        }

        // Радиус поиска конкурентов r = 3 блока (предотвращает слипание складов на параллельных соседних трубах)
        final int r = 3;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r && isSmallest; dx++) {
            for (int dy = -r; dy <= r && isSmallest; dy++) {
                for (int dz = -r; dz <= r && isSmallest; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > r * r || (dx == 0 && dy == 0 && dz == 0)) {
                        continue;
                    }
                    mpos.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockEntity(mpos) instanceof PipeBlockEntity otherBe) {
                        List<Direction> otherTerminals = getTerminalDirections(otherBe, otherBe.getBlockState());
                        for (Direction otherDir : otherTerminals) {
                            long otherScore = getWarehouseScore(mpos, otherDir);
                            if (otherScore < myScore) {
                                isSmallest = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        WAREHOUSE_CACHE.put(key, isSmallest);
        return isSmallest;
    }

    private static long getWarehouseScore(BlockPos pos, Direction dir) {
        long x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ (dir.ordinal() * 1000003L);
    }

    /**
     * Декорации Склада Викторианской эпохи внутри тупика трубы (Victorian Industrial Warehouse):
     * - Деревянный дощатый настил платформы (темный дуб)
     * - Кованые арочные чугунные/латунные нервюры и стойки фермы на стеклянных стенах с верхними стяжками
     * - Настенный промышленный стеллаж: бухгалтерские книги, гроссбухи, картотечные папки и алхимические колбы
     * - Настенный медный распределительный манометр на правой стене
     * - Подвесной викторианский латунный фонарь на цепочке с теплым светом
     * - Штабели грузов на платформе: дубовые бочонки с обручами, посылочный сундук, медные упаковочные контейнеры, весы
     * - Двойной потолочный монорельс вдоль всего блока с кареткой
     * - При этом центральный проход свободен, а стекло трубы остаётся полупрозрачным!
     */
    private static void renderWarehouseDecorations(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                                   boolean dense, int light) {
        float floorY = dense ? 0.14f : 0.27f;
        float ceilY = dense ? 0.86f : 0.73f;
        float leftX = dense ? 0.15f : 0.28f;
        float rightX = dense ? 0.85f : 0.72f;

        int warmLight = 0xF000F0; // Максимальная яркость для фонаря и освещенных витрин

        // 1. Деревянный дощатый настил платформы викторианского склада
        float deckWidth = dense ? 0.60f : 0.30f;
        box(pose, dispatcher, buffers, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                0.50f, floorY + 0.01f, 0.54f, deckWidth, 0.02f, 0.54f, light, OverlayTexture.NO_OVERLAY);

        // 2. Кованые викторианские арочные фермы и нервюры на стенах
        float pillarThick = dense ? 0.04f : 0.028f;
        float pillarH = ceilY - floorY;
        float pillarMidY = (floorY + ceilY) / 2f;
        BlockState ironRib = Blocks.CUT_COPPER_SLAB.defaultBlockState();

        // Пара стоек 1 (внутри склада Z = 0.28f, без пересечения заглушки)
        box(pose, dispatcher, buffers, ironRib, leftX + pillarThick / 2f, pillarMidY, 0.28f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, rightX - pillarThick / 2f, pillarMidY, 0.28f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, 0.50f, ceilY - 0.02f, 0.28f, deckWidth, 0.03f, pillarThick, light, OverlayTexture.NO_OVERLAY);

        // Пара стоек 2 (вдоль склада Z = 0.68f)
        box(pose, dispatcher, buffers, ironRib, leftX + pillarThick / 2f, pillarMidY, 0.68f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, rightX - pillarThick / 2f, pillarMidY, 0.68f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, 0.50f, ceilY - 0.02f, 0.68f, deckWidth, 0.03f, pillarThick, light, OverlayTexture.NO_OVERLAY);

        // 3. Викторианский промышленный стеллаж на левой стене
        float rackDepth = dense ? 0.09f : 0.05f;
        float rackX = leftX + rackDepth / 2f;
        // Полка 1 (нижняя) с гроссбухами и архивом
        box(pose, dispatcher, buffers, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                rackX, floorY + 0.12f, 0.46f, rackDepth, 0.02f, 0.32f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.BOOKSHELF.defaultBlockState(),
                rackX, floorY + 0.07f, 0.38f, rackDepth * 0.9f, 0.09f, 0.14f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.CHISELED_BOOKSHELF.defaultBlockState(),
                rackX, floorY + 0.07f, 0.52f, rackDepth * 0.9f, 0.09f, 0.14f, light, OverlayTexture.NO_OVERLAY);

        if (dense) {
            // Полка 2 (средняя, только в плотной трубе) с алхимическими эликсирами и приборами
            box(pose, dispatcher, buffers, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                    rackX, floorY + 0.26f, 0.46f, rackDepth, 0.02f, 0.32f, light, OverlayTexture.NO_OVERLAY);
            box(pose, dispatcher, buffers, Blocks.AMETHYST_CLUSTER.defaultBlockState(),
                    rackX, floorY + 0.20f, 0.38f, rackDepth * 0.8f, 0.11f, 0.12f, warmLight, OverlayTexture.NO_OVERLAY);
            box(pose, dispatcher, buffers, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),
                    rackX, floorY + 0.28f, 0.52f, rackDepth * 0.8f, 0.02f, 0.12f, light, OverlayTexture.NO_OVERLAY);
        }

        // 4. Правая стена: викторианский манометр и распределительная арматура
        float rightWallDist = dense ? 0.06f : 0.04f;
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                rightX - rightWallDist / 2f, floorY + (dense ? 0.28f : 0.16f), 0.42f, rightWallDist, dense ? 0.18f : 0.12f, 0.14f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.LIGHTNING_ROD.defaultBlockState(),
                rightX - rightWallDist / 2f, floorY + (dense ? 0.42f : 0.26f), 0.42f, 0.025f, dense ? 0.10f : 0.06f, 0.025f, light, OverlayTexture.NO_OVERLAY);

        // 5. Подвесной викторианский латунный фонарь на цепочке
        float lanternY = ceilY - (dense ? 0.15f : 0.10f);
        box(pose, dispatcher, buffers, Blocks.CHAIN.defaultBlockState(),
                0.50f, ceilY - (dense ? 0.05f : 0.03f), 0.32f, 0.025f, dense ? 0.06f : 0.04f, 0.025f, warmLight, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.LANTERN.defaultBlockState(),
                0.50f, lanternY, 0.32f, dense ? 0.10f : 0.07f, dense ? 0.12f : 0.08f, dense ? 0.10f : 0.07f, warmLight, OverlayTexture.NO_OVERLAY);

        // 6. Штабели викторианских грузов на платформе
        // Правый угол: викторианские дубовые бочонки
        box(pose, dispatcher, buffers, Blocks.BARREL.defaultBlockState(),
                rightX - (dense ? 0.12f : 0.08f), floorY + (dense ? 0.10f : 0.06f), 0.28f, dense ? 0.16f : 0.10f, light, OverlayTexture.NO_OVERLAY);
        if (dense) {
            box(pose, dispatcher, buffers, Blocks.BARREL.defaultBlockState(),
                    rightX - 0.11f, floorY + 0.07f, 0.44f, 0.13f, light, OverlayTexture.NO_OVERLAY);
        }

        // Левый угол: посылочный сундук и медный ящик
        box(pose, dispatcher, buffers, Blocks.CHEST.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + (dense ? 0.08f : 0.05f), 0.28f, dense ? 0.15f : 0.09f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
                leftX + (dense ? 0.11f : 0.07f), floorY + (dense ? 0.20f : 0.12f), 0.28f, dense ? 0.09f : 0.05f, light, OverlayTexture.NO_OVERLAY);

        // Платформенные чугунные весы
        box(pose, dispatcher, buffers, Blocks.HEAVY_CORE.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + 0.04f, 0.65f, dense ? 0.08f : 0.05f, dense ? 0.05f : 0.03f, dense ? 0.08f : 0.05f, light, OverlayTexture.NO_OVERLAY);

        // 7. Сдвоенный потолочный монорельс склада со стопором (Ceiling Monorail with Stopper)
        float railSpan = dense ? 0.09f : 0.055f;
        float railY = ceilY - 0.035f;
        float railThick = dense ? 0.035f : 0.022f;
        // Рельсы начинаются строго от Z = 0.27f и никогда не пересекают заглушку!
        box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0.50f - railSpan, railY, 0.54f, railThick, railThick, 0.54f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0.50f + railSpan, railY, 0.54f, railThick, railThick, 0.54f, light, OverlayTexture.NO_OVERLAY);
        // Медный буфер-стопор на конце монорельса
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0.50f, railY - 0.005f, 0.26f, railSpan * 2f + railThick * 1.5f, railThick * 1.4f, 0.04f, light, OverlayTexture.NO_OVERLAY);

        // Потолочная каретка манипулятора на рельсе
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0.50f, railY - 0.02f, 0.40f, dense ? 0.14f : 0.09f, dense ? 0.04f : 0.025f, dense ? 0.12f : 0.08f, light, OverlayTexture.NO_OVERLAY);
    }

    /** Заглушки на закрытых сторонах и тупиках труб: модель «лицом на север», повёрнутая к своей стороне. */
    private static void plugs(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        List<Direction> terminals = getTerminalDirections(be, state);
        if (terminals.isEmpty()) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        var plugModel = mc.getModelManager().getModel(dense ? PLUG_DENSE : PLUG_BASIC);
        var dockModel = mc.getModelManager().getModel(dense ? DOCK_DENSE : DOCK_BASIC);
        var renderer = mc.getBlockRenderer().getModelRenderer();
        var dispatcher = mc.getBlockRenderer();
        for (net.minecraft.core.Direction d : terminals) {
            boolean warehouse = be.getLevel() != null && isWarehouse(be.getLevel(), be.getBlockPos(), d);
            var model = warehouse ? dockModel : plugModel;
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            switch (d) {
                case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case UP -> pose.mulPose(Axis.XP.rotationDegrees(90));
                case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                default -> {
                }
            }
            pose.translate(-0.5, -0.5, -0.5);
            renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, model, 1f, 1f, 1f, light,
                    OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.cutout());

            // Если тупик выбран Складом (не более 1 склада в радиусе 3 блоков) — рендерим викторианские декорации склада
            if (warehouse) {
                renderWarehouseDecorations(pose, dispatcher, buffers, dense, light);
            }

            pose.popPose();
        }
    }

    /**
     * Опорные кронштейны с манометром: рендерятся строго с ритмом в 4 блока (интервал 4 блока вдоль трассы).
     * Прямые участки между ними — гладкие бесшовные стеклянные трубы без лишних колец.
     */
    private static void brackets(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        if (Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), 4) != 0) {
            return;
        }

        var north = state.getValue(SnowPipeBlock.NORTH);
        var south = state.getValue(SnowPipeBlock.SOUTH);
        var east = state.getValue(SnowPipeBlock.EAST);
        var west = state.getValue(SnowPipeBlock.WEST);
        var up = state.getValue(SnowPipeBlock.UP);
        var down = state.getValue(SnowPipeBlock.DOWN);

        boolean zLinked = north != SnowPipeBlock.Link.NONE && south != SnowPipeBlock.Link.NONE;
        boolean xLinked = east != SnowPipeBlock.Link.NONE && west != SnowPipeBlock.Link.NONE;
        boolean yLinked = up != SnowPipeBlock.Link.NONE && down != SnowPipeBlock.Link.NONE;

        net.minecraft.core.Direction.Axis straightAxis = null;
        if (zLinked && east == SnowPipeBlock.Link.NONE && west == SnowPipeBlock.Link.NONE && up == SnowPipeBlock.Link.NONE && down == SnowPipeBlock.Link.NONE) {
            straightAxis = net.minecraft.core.Direction.Axis.Z;
        } else if (xLinked && north == SnowPipeBlock.Link.NONE && south == SnowPipeBlock.Link.NONE && up == SnowPipeBlock.Link.NONE && down == SnowPipeBlock.Link.NONE) {
            straightAxis = net.minecraft.core.Direction.Axis.X;
        } else if (yLinked && north == SnowPipeBlock.Link.NONE && south == SnowPipeBlock.Link.NONE && east == SnowPipeBlock.Link.NONE && west == SnowPipeBlock.Link.NONE) {
            straightAxis = net.minecraft.core.Direction.Axis.Y;
        }

        if (straightAxis == null) {
            return;
        }

        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        var model = mc.getModelManager().getModel(dense ? BAND_DENSE : BAND_BASIC);
        var renderer = mc.getBlockRenderer().getModelRenderer();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (straightAxis == net.minecraft.core.Direction.Axis.X) {
            pose.mulPose(Axis.YP.rotationDegrees(90));
        } else if (straightAxis == net.minecraft.core.Direction.Axis.Y) {
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.translate(-0.5, -0.5, -0.5);

        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, model, 1f, 1f, 1f, light,
                OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public void render(PipeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null) {
            return;
        }
        plugs(be, state, pose, buffers, light);
        brackets(be, state, pose, buffers, light);
        if (!state.getValue(GolemProperties.ACTIVE)) {
            return;
        }
        float time = (be.getLevel().getGameTime() % 24000L) + partialTick;
        PipeRunners.Spot spot = PipeRunners.locate(be.getLevel(), be.getBlockPos(), state, be.getLevel().getGameTime(), partialTick);
        if (spot == null) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        float turn = spot.turnProgress();
        boolean turning = spot.isTurning();
        float tripProgress = spot.tripProgress();

        // Выбор предметов доставки из сети
        ItemStack inItem = getCargoItem(spot.incomingCargo(), be.getLevel(), be.getBlockPos());
        ItemStack outItem = getCargoItem(spot.outgoingCargo(), be.getLevel(), be.getBlockPos());
        BlockState carriedBox = getCargoBox(spot.outgoingCargo());

        pose.pushPose();
        pose.translate(0.5 + spot.dx(), 0.5 + spot.dy(), 0.5 + spot.dz());

        float baseYaw = switch (spot.heading()) {
            case NORTH -> 0f;
            case EAST -> 270f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            case UP, DOWN -> 180f; // Стабильная ориентация лицом вперед (Юг) в вертикальных трубах
        };

        boolean vertical = spot.heading().getAxis() == Direction.Axis.Y;
        float yaw = baseYaw;
        float step = (float) Math.sin(time * 0.55f);

        if (turning) {
            // Подвесная механическая рука с монорельсом и медным големом сверху на всех разворотах (со складом и без)
            // Ориентируется строго вдоль оси трубы (baseYaw)
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(baseYaw));
            renderMechanicalArmAndGolem(pose, dispatcher, buffers, itemRenderer, be.getLevel(), outItem, dense, turn, time, light, overlay, spot.heading());
            pose.popPose();

            // Во время спокойного разворота снеговика (turn in 0..1):
            // 0.00..0.35: сгружает старый предмет на пол платформы (он исчезает)
            // 0.35..0.65: плавный пустой разворот на 180 градусов с опущенными руками
            // 0.65..0.80: медный голем сверху разжимает руки и роняет новый предмет, голем ловит его
            // 0.80..1.00: амортизация удара, предмет зафиксирован в руках/на голове, готов к пути
            // на станции бегунок смотрит туда, откуда пришёл, и плавно разворачивается к выходу (в вертикальной трубе — нет)
            if (vertical || turn < 0.35f) {
                yaw = baseYaw;
            } else if (turn <= 0.65f) {
                float rot = (turn - 0.35f) / 0.30f;
                yaw = baseYaw + (rot * rot * (3f - 2f * rot)) * 180f;
            } else {
                yaw = baseYaw + 180f;
            }
            step = 0f;
        }

        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        // лёгкая покачивающаяся походка во время бега
        if (!turning) {
            pose.mulPose(Axis.ZP.rotationDegrees(step * 3f));
            pose.translate(0, Math.abs(step) * 0.012, 0);
        }

        boolean isVertical = spot.heading().getAxis() == Direction.Axis.Y;
        if (dense) {
            // целый снеговик: модель снежного голема, голова-тыква; руки-палки
            final float s = 0.2f;
            final float feet = isVertical ? -0.16f : -0.355f;
            final float boxSize = 0.42f; // в координатах куклы голема

            if (!turning) {
                // Обычный бег: морфинг предмета в ящик прямо в руках на середине пути (tripProgress ~ 0.5)
                // 0.00..0.44: несёт сам предмет в руках
                // 0.44..0.56: предмет прямо в руках превращается в ящик
                // 0.56..1.00: несёт закрытый ящик
                float morphStart = 0.44f, morphEnd = 0.56f;
                boolean beforeMorph = tripProgress < morphStart;
                boolean afterMorph = tripProgress > morphEnd;

                SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                        SnowmanPuppet.Pose.holding(Math.abs(step) * 0.6f),
                        hp -> {
                            if (beforeMorph) {
                                renderItem(itemRenderer, buffers, be.getLevel(), outItem, hp, 0, 0, 0, 0.44f, time * 3f, light, overlay);
                            } else if (afterMorph) {
                                box(hp, dispatcher, buffers, carriedBox, 0, 0, 0, boxSize, light, overlay);
                            } else {
                                float m = (tripProgress - morphStart) / (morphEnd - morphStart);
                                if (m < 0.5f) {
                                    float p = m / 0.5f;
                                    float sc = (1f - p) * 0.44f;
                                    renderItem(itemRenderer, buffers, be.getLevel(), outItem, hp, 0, 0, 0, sc, time * (3f + p * 12f), light, overlay);
                                } else {
                                    float p = (m - 0.5f) / 0.5f;
                                    float sc = p * boxSize;
                                    box(hp, dispatcher, buffers, carriedBox, 0, 0, 0, sc, light, overlay);
                                }
                            }
                        });
            } else {
                // Плавная анимация на станции: опускание старого -> пустой разворот -> сброс медным големом и ловля
                if (turn < 0.35f) {
                    // ФАЗА 1: Плавное опускание старого предмета на пол платформы
                    float pDown = turn / 0.35f;
                    float handY = Mth.lerp(pDown, 13.5f, 3.5f);
                    float handZ = Mth.lerp(pDown, 9.0f, 11.5f);
                    Vector3f lHand = new Vector3f(3.2f, handY, handZ);
                    Vector3f rHand = new Vector3f(-3.2f, handY, handZ);
                    SnowmanPuppet.Pose downPose = new SnowmanPuppet.Pose(0f, pDown * 0.12f, 0f, pDown * 20f, lHand, rHand);

                    float cargoY = Mth.lerp(pDown, 0f, -0.60f);
                    float cargoZ = Mth.lerp(pDown, 0f, 0.12f);
                    // При касании пола предмет растворяется в станции
                    float itemScale = pDown > 0.75f ? (1f - (pDown - 0.75f) / 0.25f) * 0.44f : 0.44f;

                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            downPose,
                            hp -> {
                                if (itemScale > 0.01f) {
                                    renderItem(itemRenderer, buffers, be.getLevel(), inItem, hp, 0, cargoY, cargoZ, itemScale, time * 3f, light, overlay);
                                }
                            });
                } else if (turn < 0.65f) {
                    // ФАЗА 2: Разворот с пустыми руками (старый предмет сдан, новый пока у медного голема сверху)
                    SnowmanPuppet.Pose turnPose = new SnowmanPuppet.Pose(0f, 0f, 0f, 0f, SnowmanPuppet.REST_LEFT, SnowmanPuppet.REST_RIGHT);
                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            turnPose, null);
                } else if (turn < 0.80f) {
                    // ФАЗА 3a: Снеговик вскидывает руки навстречу падающему сверху предмету
                    float pReach = (turn - 0.65f) / 0.15f;
                    float handY = Mth.lerp(pReach, 9.0f, 18.0f);
                    float handZ = Mth.lerp(pReach, 1.5f, 7.5f);
                    SnowmanPuppet.Pose reachPose = new SnowmanPuppet.Pose(0f, 0f, 0f, -14f, new Vector3f(3.2f, handY, handZ), new Vector3f(-3.2f, handY, handZ));
                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            reachPose, null);
                } else {
                    // ФАЗА 3b: Поймал! Пружинящая амортизация удара и приведение предмета в руки
                    float pSettle = (turn - 0.80f) / 0.20f;
                    float squash = (float) Math.sin((1f - pSettle) * Math.PI) * 0.14f;
                    float handY = Mth.lerp(pSettle, 18.0f, 13.5f);
                    float handZ = Mth.lerp(pSettle, 7.5f, 9.0f);
                    SnowmanPuppet.Pose catchPose = new SnowmanPuppet.Pose(0f, squash, 0f, Mth.lerp(pSettle, -14f, 8f), new Vector3f(3.2f, handY, handZ), new Vector3f(-3.2f, handY, handZ));

                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            catchPose,
                            hp -> renderItem(itemRenderer, buffers, be.getLevel(), outItem, hp, 0, 0, 0, 0.44f, time * 3f, light, overlay));
                }
            }
        } else {
            // Базовая труба: тыквенная голова
            final float smallBoxScale = 0.11f;

            if (!turning) {
                box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                // Обычный бег: морфинг предмета в ящик прямо на голове
                float morphStart = 0.44f, morphEnd = 0.56f;
                boolean beforeMorph = tripProgress < morphStart;
                boolean afterMorph = tripProgress > morphEnd;

                if (beforeMorph) {
                    renderItem(itemRenderer, buffers, be.getLevel(), outItem, pose, 0, 0.15, 0, 0.12f, time * 3f, light, overlay);
                } else if (afterMorph) {
                    box(pose, dispatcher, buffers, carriedBox, 0, 0.16, 0, smallBoxScale, light, overlay);
                } else {
                    float m = (tripProgress - morphStart) / (morphEnd - morphStart);
                    if (m < 0.5f) {
                        float p = m / 0.5f;
                        float sc = (1f - p) * 0.12f;
                        renderItem(itemRenderer, buffers, be.getLevel(), outItem, pose, 0, 0.15, 0, sc, time * (3f + p * 12f), light, overlay);
                    } else {
                        float p = (m - 0.5f) / 0.5f;
                        float sc = p * smallBoxScale;
                        box(pose, dispatcher, buffers, carriedBox, 0, 0.16, 0, sc, light, overlay);
                    }
                }
            } else {
                // Разворот маленького снеговика: прыжком скидывает предмет с головы -> разворачивается -> ловит новый от медного голема
                if (turn < 0.35f) {
                    // ФАЗА 1: Задорный прыжок со сбросом предмета с головы вперед-вниз на пол
                    float pDown = turn / 0.35f;
                    float hop = (float) Math.sin(pDown * Math.PI) * 0.03f;
                    box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, hop, 0, 0.19f, light, overlay);

                    float flingP = Mth.clamp(pDown / 0.85f, 0f, 1f);
                    float flingY = Mth.lerp(flingP * flingP, 0.15f + hop, -0.14f);
                    float flingZ = Mth.lerp(flingP, 0.0f, 0.18f);
                    float sc = flingP > 0.70f ? (1f - (flingP - 0.70f) / 0.30f) * 0.12f : 0.12f;
                    if (sc > 0.005f) {
                        renderItem(itemRenderer, buffers, be.getLevel(), inItem, pose, 0, flingY, flingZ, sc, time * 3f + flingP * 120f, light, overlay);
                    }
                } else if (turn < 0.65f) {
                    // ФАЗА 2: Пустой плавный разворот
                    box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                } else if (turn < 0.80f) {
                    // ФАЗА 3a: Тыква ожидает сброса предмета сверху
                    box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                } else {
                    // ФАЗА 3b: Предмет пойман на макушку тыквы с мягкой амортизацией
                    float pSettle = (turn - 0.80f) / 0.20f;
                    float squashHop = (float) -Math.sin((1f - pSettle) * Math.PI) * 0.03f;
                    box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, squashHop, 0, 0.19f, light, overlay);
                    renderItem(itemRenderer, buffers, be.getLevel(), outItem, pose, 0, 0.15f + squashHop, 0, 0.12f, time * 3f, light, overlay);
                }
            }
        }
        pose.popPose();
    }

    /**
     * Подвесная механическая рука с монорельсом и медным големом на развороте бегунка:
     * - Сдвоенный потолочный монорельс вдоль оси Z под потолком трубы.
     * - Потолочная каретка манипулятора.
     * - Телескопическая штанга и медный голем строго внутри внутреннего объема трубы.
     * - Медный голем держит новый груз и роняет его с ускорением свободного падения снеговику в руки / на тыкву.
     */
    private static void renderMechanicalArmAndGolem(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                                   ItemRenderer itemRenderer, @Nullable Level level, ItemStack outItem,
                                                   boolean dense, float turn, float time, int light, int overlay, Direction heading) {
        float ceilingY = dense ? 0.28f : 0.165f;
        float targetFeetY = dense ? 0.02f : 0.01f;
        float retractedFeetY = dense ? 0.10f : 0.07f;
        float golemScale = dense ? 0.095f : 0.055f;

        boolean isVertical = heading.getAxis() == Direction.Axis.Y;
        float railSpan = dense ? 0.09f : 0.055f;
        float railY = ceilingY + 0.02f;
        float railThick = dense ? 0.035f : 0.022f;

        // Компактный потолочный фланец крепления каретки манипулятора
        float bracketSize = dense ? 0.18f : 0.12f;
        box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0, ceilingY + 0.02f, 0, bracketSize, railThick, bracketSize, light, overlay);

        // 2. Выдвижение механической руки из каретки потолка
        float ext;
        if (turn < 0.28f) {
            ext = Mth.clamp(turn / 0.28f, 0f, 1f);
        } else if (turn < 0.82f) {
            ext = 1f;
        } else {
            ext = 1f - Mth.clamp((turn - 0.82f) / 0.18f, 0f, 1f);
        }
        ext = (float) Mth.smoothstep(ext);

        float currentFeetY = Mth.lerp(ext, retractedFeetY, targetFeetY);

        // 3. Потолочная каретка монорельса
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0, ceilingY, 0, dense ? 0.13f : 0.08f, dense ? 0.035f : 0.022f, dense ? 0.11f : 0.07f, light, overlay);

        // 4. Телескопическая штанга / полиспаст
        float rodTop = ceilingY - 0.015f;
        float rodBottom = currentFeetY + golemScale * 1.15f;
        if (rodTop > rodBottom) {
            float rodH = rodTop - rodBottom;
            box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                    0, (rodTop + rodBottom) / 2f, 0, dense ? 0.035f : 0.022f, rodH, dense ? 0.035f : 0.022f, light, overlay);
        }

        // 5. Медный манипулятор-захват (люлька)
        box(pose, dispatcher, buffers, Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
                0, currentFeetY + golemScale * 1.15f, 0, dense ? 0.06f : 0.038f, 0.02f, dense ? 0.06f : 0.038f, light, overlay);

        // 6. Медный голем на подвесе (Copper Golem Puppet)
        boolean holding = turn < 0.70f;
        CopperGolemPuppet.Anim golemAnim = new CopperGolemPuppet.Anim(time * 0.1f, false, holding, 0.12f);
        CopperGolemPuppet.Arms arms = holding ? null : new CopperGolemPuppet.Arms(0.1f, -0.55f, 0.1f, 0.55f, 0f);

        Vector3f golemPos = new Vector3f(0f, currentFeetY, 0f);
        Vector3f forward = new Vector3f(0f, -0.20f, 0.98f).normalize();
        Vector3f up = new Vector3f(0f, 1f, 0f);

        CopperGolemPuppet.render(pose, buffers, light, golemPos, forward, up, golemScale, golemAnim, 0,
                hp -> {
                    // Груз в руках медного голема до момента сброса
                    float cargoScale = dense ? 0.28f : 0.16f;
                    renderItem(itemRenderer, buffers, level, outItem, hp, 0, 0, 0, cargoScale, time * 3f, light, overlay);
                },
                arms);

        // 7. Падающий груз в момент сброса (turn in 0.70 .. 0.82)
        if (turn >= 0.70f && turn < 0.82f) {
            float dropP = (turn - 0.70f) / 0.12f;
            float dropCurve = dropP * dropP; // ускорение свободного падения

            float startY = currentFeetY + golemScale * 0.60f;
            float targetY = dense ? 0.00f : -0.02f;
            float curY = Mth.lerp(dropCurve, startY, targetY);
            float curZ = Mth.lerp(dropP, 0.04f, 0.0f);
            float sc = dense ? 0.28f : 0.16f;

            renderItem(itemRenderer, buffers, level, outItem, pose, 0, curY, curZ, sc, time * 3f + dropP * 90f, light, overlay);
        }
    }

    // Набор доступных транспортировочных ящиков курьерской службы
    private static BlockState getCargoBox(int cargoId) {
        final BlockState[] BOX_TEMPLATES = {
                Blocks.BARREL.defaultBlockState(),
                Blocks.BARREL.defaultBlockState(),
                Blocks.COPPER_BLOCK.defaultBlockState(),
                Blocks.BARREL.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(),
                Blocks.BARREL.defaultBlockState()
        };
        return BOX_TEMPLATES[Math.floorMod(cargoId, BOX_TEMPLATES.length)];
    }

    private static final List<ItemStack> FLUID_BUCKETS = List.of(
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.LAVA_BUCKET),
            new ItemStack(Items.MILK_BUCKET)
    );

    private static final List<ItemStack> FALLBACK_ITEMS = List.of(
            new ItemStack(ModItems.COPPER_PUNCH_CARD.get()),
            new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get()),
            new ItemStack(ModItems.FLATTENED_IRON_CORE.get()),
            new ItemStack(ModItems.FLATTENED_CRYO_SUBSTRATE.get()),
            new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()),
            new ItemStack(ModItems.COPPER_DISK_16K.get()),
            new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()),
            new ItemStack(ModItems.PIPE_PLUG.get()),
            new ItemStack(ModItems.AUTOCRAFT_MODULE.get()),
            new ItemStack(Items.COPPER_INGOT),
            new ItemStack(Items.RAW_COPPER),
            new ItemStack(Items.REDSTONE),
            new ItemStack(Items.LAPIS_LAZULI),
            new ItemStack(Items.AMETHYST_SHARD),
            new ItemStack(Items.SNOWBALL),
            new ItemStack(Items.BLUE_ICE),
            new ItemStack(Items.COMPASS),
            new ItemStack(Items.CLOCK),
            new ItemStack(Items.GOLD_INGOT),
            new ItemStack(Items.IRON_INGOT)
    );

    private static final class CargoPool {
        long expireTime = 0;
        final List<ItemStack> items = new ArrayList<>();
        final List<ItemStack> fluids = new ArrayList<>();
    }

    private static final Map<BlockPos, CargoPool> CARGO_POOLS = new HashMap<>();

    private static ItemStack getCargoItem(int cargoId, @Nullable Level level, @Nullable BlockPos pos) {
        List<ItemStack> netItems = null;
        List<ItemStack> netFluids = null;

        if (level != null && pos != null) {
            long now = level.getGameTime();
            CargoPool pool = CARGO_POOLS.get(pos);
            if (pool == null || pool.expireTime < now) {
                if (CARGO_POOLS.size() > 512) {
                    CARGO_POOLS.clear();
                }
                pool = new CargoPool();
                pool.expireTime = now + 20L;
                try {
                    GolemNetwork net = GolemNetwork.get(level, pos);
                    if (net != null) {
                        Map<ItemKey, Long> items = net.listItems();
                        for (ItemKey k : items.keySet()) {
                            pool.items.add(k.toStack(1));
                        }
                        List<FluidStack> fluids = net.listFluids();
                        for (FluidStack f : fluids) {
                            if (!f.isEmpty()) {
                                Item b = f.getFluid().getBucket();
                                if (b != null && b != Items.AIR) {
                                    pool.fluids.add(new ItemStack(b));
                                } else {
                                    ItemStack fb = FluidUtil.getFilledBucket(f);
                                    if (!fb.isEmpty()) {
                                        pool.fluids.add(fb);
                                    }
                                }
                            }
                        }
                    }
                    if (pool.items.isEmpty()) {
                        for (Direction dir : Direction.values()) {
                            var nbe = level.getBlockEntity(pos.relative(dir));
                            if (nbe instanceof Container container && container.getContainerSize() > 0) {
                                for (int s = 0; s < container.getContainerSize(); s++) {
                                    ItemStack st = container.getItem(s);
                                    if (!st.isEmpty()) {
                                        pool.items.add(st.copyWithCount(1));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                CARGO_POOLS.put(pos.immutable(), pool);
            }
            netItems = pool.items;
            netFluids = pool.fluids;
        }

        // 1 раз из 10 - ведро с жидкостью из сети (ровно 10% шанс)
        boolean fluidRoll = (Math.floorMod(cargoId, 10) == 7);
        if (fluidRoll) {
            if (netFluids != null && !netFluids.isEmpty()) {
                return netFluids.get(Math.floorMod(cargoId / 10, netFluids.size()));
            }
            return FLUID_BUCKETS.get(Math.floorMod(cargoId / 10, FLUID_BUCKETS.size()));
        }

        // В остальных 9 случаях - реальный предмет из сети
        if (netItems != null && !netItems.isEmpty()) {
            return netItems.get(Math.floorMod(cargoId, netItems.size()));
        }
        return FALLBACK_ITEMS.get(Math.floorMod(cargoId, FALLBACK_ITEMS.size()));
    }

    private static void renderItem(ItemRenderer itemRenderer, MultiBufferSource buffers, @Nullable Level level, ItemStack stack,
                                   PoseStack pose, double x, double y, double z, float scale, float rotDeg, int light, int overlay) {
        if (stack.isEmpty() || scale <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(rotDeg));
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, level, 0);
        pose.popPose();
    }

    /** Кубик размера size с центром в (x, y, z) относительно текущей точки. */
    private static void box(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers, BlockState state,
                            double x, double y, double z, float size, int light, int overlay) {
        box(pose, dispatcher, buffers, state, x, y, z, size, size, size, light, overlay);
    }

    /** Параллелепипед размеров (sx, sy, sz) с центром в (x, y, z) относительно текущей точки. */
    private static void box(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers, BlockState state,
                            double x, double y, double z, float sx, float sy, float sz, int light, int overlay) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        pose.translate(-0.5, -0.5, -0.5);
        dispatcher.renderSingleBlock(state, pose, buffers, light, overlay);
        pose.popPose();
    }
}
