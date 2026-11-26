package com.golemlogix.client;

import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.client.render.NixieDigits;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.GolemNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A snowman with a pumpkin head and a crate running through powered pipes. There is one per connected pipe network
 * (see {@link PipeRunners}); in a basic pipe only its head is visible (the body does not fit), in a dense one the whole snowman.
 */
public class SnowPipeRenderer implements BlockEntityRenderer<PipeBlockEntity> {
    public SnowPipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final net.minecraft.client.resources.model.ModelResourceLocation PLUG_BASIC = plugModel("basic_snow_pipe_plug");
    public static final net.minecraft.client.resources.model.ModelResourceLocation PLUG_DENSE = plugModel("dense_frost_pipe_plug");
    /** Warehouse dock door: a dead end with a warehouse is closed not by a plug but by a roller shutter with a lamp. */
    public static final net.minecraft.client.resources.model.ModelResourceLocation DOCK_BASIC = plugModel("basic_snow_pipe_dock");
    public static final net.minecraft.client.resources.model.ModelResourceLocation DOCK_DENSE = plugModel("dense_frost_pipe_dock");
    /** Heat lamp: baked body (cutout) and glass (translucent), drawn every sixth block of a straight horizontal pipe. */
    public static final net.minecraft.client.resources.model.ModelResourceLocation LAMP_BASIC = plugModel("basic_snow_pipe_lamp");
    public static final net.minecraft.client.resources.model.ModelResourceLocation LAMP_DENSE = plugModel("dense_frost_pipe_lamp");
    public static final net.minecraft.client.resources.model.ModelResourceLocation LAMP_GLASS_BASIC = plugModel("basic_snow_pipe_lamp_glass");
    public static final net.minecraft.client.resources.model.ModelResourceLocation LAMP_GLASS_DENSE = plugModel("dense_frost_pipe_lamp_glass");

    private static final Direction[] DIRS = Direction.values();
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f ARM_FORWARD = new Vector3f(0f, -0.20f, 0.98f).normalize();
    private static final SnowmanPuppet.Pose TURN_POSE =
            new SnowmanPuppet.Pose(0f, 0f, 0f, 0f, SnowmanPuppet.REST_LEFT, SnowmanPuppet.REST_RIGHT);
    private static final CopperGolemPuppet.Arms OPEN_ARMS = new CopperGolemPuppet.Arms(0.1f, -0.55f, 0.1f, 0.55f, 0f);
    private static final Vector3f FEET_POS = new Vector3f();
    private static final Vector3f GOLEM_POS = new Vector3f();
    private static final Vector3f HAND_L = new Vector3f();
    private static final Vector3f HAND_R = new Vector3f();

    private static net.minecraft.client.resources.model.ModelResourceLocation plugModel(String name) {
        return net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "block/" + name));
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public boolean shouldRender(PipeBlockEntity be, Vec3 cameraPosition) {
        if (!BlockEntityRenderer.super.shouldRender(be, cameraPosition)) {
            return false;
        }
        Level level = be.getLevel();
        if (level == null) {
            return false;
        }
        BlockState state = be.getBlockState();
        if (terminalMask(be, state) != 0 || SnowPipeBlock.hasLamp(state, be.getBlockPos())) {
            return true;
        }
        return state.hasProperty(GolemProperties.ACTIVE)
                && state.getValue(GolemProperties.ACTIVE)
                && PipeRunners.mayVisit(level, be.getBlockPos(), level.getGameTime());
    }

    private static final Long2BooleanOpenHashMap[] WAREHOUSE_CACHE = new Long2BooleanOpenHashMap[6];
    static {
        for (int i = 0; i < WAREHOUSE_CACHE.length; i++) {
            WAREHOUSE_CACHE[i] = new Long2BooleanOpenHashMap();
        }
    }
    private static int warehouseCacheSize = 0;
    private static WeakReference<Level> warehouseLevel = new WeakReference<>(null);
    private static long lastCacheClearTime = 0;

    private static void clearWarehouseCache() {
        for (Long2BooleanOpenHashMap map : WAREHOUSE_CACHE) {
            map.clear();
        }
        warehouseCacheSize = 0;
    }

    /** Clears static client-side pipe caches (warehouse election and cargo samples) on world/dimension unload. */
    public static void clearCaches() {
        clearWarehouseCache();
        warehouseLevel = new WeakReference<>(null);
        lastCacheClearTime = 0;
        CARGO_POOLS.clear();
        cargoLevel = new WeakReference<>(null);
    }

    /** Bitmask of terminal directions ({@code 1 << dir.ordinal()}); avoids list/array allocations per pipe. */
    private static int terminalMask(PipeBlockEntity be, BlockState state) {
        if (!(state.getBlock() instanceof SnowPipeBlock)) {
            return 0;
        }
        int mask = 0;
        int pipeLinks = 0;
        Direction singlePipeDir = null;
        int anyLinks = 0;
        Direction onlyLinkDir = null;
        for (int i = 0; i < DIRS.length; i++) {
            Direction d = DIRS[i];
            if (be.isPlugged(d)) {
                mask |= 1 << i;
            }
            SnowPipeBlock.Link link = SnowPipeBlock.link(state, d);
            if (link == SnowPipeBlock.Link.PIPE) {
                pipeLinks++;
                singlePipeDir = d;
            }
            if (link != SnowPipeBlock.Link.NONE) {
                anyLinks++;
                onlyLinkDir = d;
            }
        }
        if (pipeLinks == 1) {
            Direction deadEnd = singlePipeDir.getOpposite();
            if (SnowPipeBlock.link(state, deadEnd) == SnowPipeBlock.Link.NONE) {
                mask |= 1 << deadEnd.ordinal();
            }
        } else if (pipeLinks == 0 && anyLinks == 1) {
            // a single pipe: a plug only on the blind end, if it has one exit (otherwise it would close off a device)
            Direction deadEnd = onlyLinkDir.getOpposite();
            if (SnowPipeBlock.link(state, deadEnd) == SnowPipeBlock.Link.NONE) {
                mask |= 1 << deadEnd.ordinal();
            }
        }
        return mask;
    }

    /**
     * Returns the dead end / terminal directions of this pipe:
     * 1. Any side with a plug installed (isPlugged).
     * 2. Any open dead end of the line (if the pipe has exactly 1 PIPE connection and the opposite side is free, Link.NONE).
     * 3. A single pipe (no links) gets a terminal on its north side.
     */
    public static List<Direction> getTerminalDirections(PipeBlockEntity be, BlockState state) {
        int mask = terminalMask(be, state);
        List<Direction> terminals = new ArrayList<>(2);
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) != 0) {
                terminals.add(DIRS[i]);
            }
        }
        return terminals;
    }

    /**
     * Checks whether this dead end of the pipe is a Warehouse.
     * Rule: warehouses must not pile up next to each other (at most 1 warehouse within 3 blocks).
     * On different branches of the network (4 or more blocks apart) every branch gets a full warehouse.
     * Otherwise (when there is already a warehouse within 3 blocks) the brass plug with a gauge stays.
     */
    public static boolean isWarehouse(Level level, BlockPos pos, Direction dir) {
        if (!dir.getAxis().isHorizontal()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (warehouseLevel.get() != level || gameTime < lastCacheClearTime
                || gameTime - lastCacheClearTime > 40L || warehouseCacheSize > 1024) {
            clearWarehouseCache();
            warehouseLevel = new WeakReference<>(level);
            lastCacheClearTime = gameTime;
        }
        Long2BooleanOpenHashMap byDir = WAREHOUSE_CACHE[dir.ordinal()];
        long key = pos.asLong();
        if (byDir.containsKey(key)) {
            return byDir.get(key);
        }

        long myScore = getWarehouseScore(pos, dir);
        boolean isSmallest = true;

        // Check the other sides of the same block (if one block has several dead ends)
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity thisBe) {
            int mask = terminalMask(thisBe, thisBe.getBlockState());
            for (int i = 0; i < DIRS.length; i++) {
                if ((mask & (1 << i)) != 0 && DIRS[i] != dir && getWarehouseScore(pos, DIRS[i]) < myScore) {
                    isSmallest = false;
                    break;
                }
            }
        }

        // Search radius for competitors r = 3 blocks (keeps warehouses from sticking together on parallel neighbouring pipes)
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
                        int mask = terminalMask(otherBe, otherBe.getBlockState());
                        for (int i = 0; i < DIRS.length; i++) {
                            if ((mask & (1 << i)) != 0 && getWarehouseScore(mpos, DIRS[i]) < myScore) {
                                isSmallest = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        byDir.put(key, isSmallest);
        warehouseCacheSize++;
        return isSmallest;
    }

    private static long getWarehouseScore(BlockPos pos, Direction dir) {
        long x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ (dir.ordinal() * 1000003L);
    }

    /**
     * Victorian warehouse decorations inside a pipe dead end (Victorian Industrial Warehouse):
     * - Wooden plank platform deck (dark oak)
     * - Wrought arched cast iron/brass ribs and truss posts on the glass walls with top ties
     * - Industrial wall rack: ledgers, account books, filing folders and alchemical flasks
     * - Copper distribution pressure gauge on the right wall
     * - Hanging Victorian brass lantern on a chain with a warm light
     * - Stacks of cargo on the platform: hooped oak kegs, a parcel chest, copper packing crates, scales
     * - Double ceiling monorail along the whole block with a carriage
     * - The central passage stays free, and the pipe glass stays translucent!
     */
    private static void renderWarehouseDecorations(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                                   boolean dense, int light) {
        float floorY = dense ? 0.14f : 0.27f;
        float ceilY = dense ? 0.86f : 0.73f;
        float leftX = dense ? 0.15f : 0.28f;
        float rightX = dense ? 0.85f : 0.72f;

        int warmLight = 0xF000F0; // Maximum brightness for the lantern and lit displays

        // 1. Wooden plank deck of the Victorian warehouse platform
        float deckWidth = dense ? 0.60f : 0.30f;
        box(pose, dispatcher, buffers, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                0.50f, floorY + 0.01f, 0.54f, deckWidth, 0.02f, 0.54f, light, OverlayTexture.NO_OVERLAY);

        // 2. Wrought Victorian arched trusses and ribs on the walls
        float pillarThick = dense ? 0.04f : 0.028f;
        float pillarH = ceilY - floorY;
        float pillarMidY = (floorY + ceilY) / 2f;
        BlockState ironRib = Blocks.CUT_COPPER_SLAB.defaultBlockState();

        // Pair of posts 1 (inside the warehouse at Z = 0.28f, not crossing the plug)
        box(pose, dispatcher, buffers, ironRib, leftX + pillarThick / 2f, pillarMidY, 0.28f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, rightX - pillarThick / 2f, pillarMidY, 0.28f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, 0.50f, ceilY - 0.02f, 0.28f, deckWidth, 0.03f, pillarThick, light, OverlayTexture.NO_OVERLAY);

        // Pair of posts 2 (along the warehouse at Z = 0.68f)
        box(pose, dispatcher, buffers, ironRib, leftX + pillarThick / 2f, pillarMidY, 0.68f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, rightX - pillarThick / 2f, pillarMidY, 0.68f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, ironRib, 0.50f, ceilY - 0.02f, 0.68f, deckWidth, 0.03f, pillarThick, light, OverlayTexture.NO_OVERLAY);

        // 3. Victorian industrial rack on the left wall
        float rackDepth = dense ? 0.09f : 0.05f;
        float rackX = leftX + rackDepth / 2f;
        // Shelf 1 (bottom) with ledgers and the archive
        box(pose, dispatcher, buffers, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                rackX, floorY + 0.12f, 0.46f, rackDepth, 0.02f, 0.32f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.BOOKSHELF.defaultBlockState(),
                rackX, floorY + 0.07f, 0.38f, rackDepth * 0.9f, 0.09f, 0.14f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.CHISELED_BOOKSHELF.defaultBlockState(),
                rackX, floorY + 0.07f, 0.52f, rackDepth * 0.9f, 0.09f, 0.14f, light, OverlayTexture.NO_OVERLAY);

        if (dense) {
            // Shelf 2 (middle, only in a dense pipe) with alchemical elixirs and instruments
            box(pose, dispatcher, buffers, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                    rackX, floorY + 0.26f, 0.46f, rackDepth, 0.02f, 0.32f, light, OverlayTexture.NO_OVERLAY);
            box(pose, dispatcher, buffers, Blocks.AMETHYST_CLUSTER.defaultBlockState(),
                    rackX, floorY + 0.20f, 0.38f, rackDepth * 0.8f, 0.11f, 0.12f, warmLight, OverlayTexture.NO_OVERLAY);
            box(pose, dispatcher, buffers, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),
                    rackX, floorY + 0.28f, 0.52f, rackDepth * 0.8f, 0.02f, 0.12f, light, OverlayTexture.NO_OVERLAY);
        }

        // 4. Right wall: Victorian pressure gauge and distribution fittings
        float rightWallDist = dense ? 0.06f : 0.04f;
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                rightX - rightWallDist / 2f, floorY + (dense ? 0.28f : 0.16f), 0.42f, rightWallDist, dense ? 0.18f : 0.12f, 0.14f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.LIGHTNING_ROD.defaultBlockState(),
                rightX - rightWallDist / 2f, floorY + (dense ? 0.42f : 0.26f), 0.42f, 0.025f, dense ? 0.10f : 0.06f, 0.025f, light, OverlayTexture.NO_OVERLAY);

        // 5. Hanging Victorian brass lantern on a chain
        float lanternY = ceilY - (dense ? 0.15f : 0.10f);
        box(pose, dispatcher, buffers, Blocks.CHAIN.defaultBlockState(),
                0.50f, ceilY - (dense ? 0.05f : 0.03f), 0.32f, 0.025f, dense ? 0.06f : 0.04f, 0.025f, warmLight, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.LANTERN.defaultBlockState(),
                0.50f, lanternY, 0.32f, dense ? 0.10f : 0.07f, dense ? 0.12f : 0.08f, dense ? 0.10f : 0.07f, warmLight, OverlayTexture.NO_OVERLAY);

        // 6. Stacks of Victorian cargo on the platform
        // Right corner: Victorian oak kegs
        box(pose, dispatcher, buffers, Blocks.BARREL.defaultBlockState(),
                rightX - (dense ? 0.12f : 0.08f), floorY + (dense ? 0.10f : 0.06f), 0.28f, dense ? 0.16f : 0.10f, light, OverlayTexture.NO_OVERLAY);
        if (dense) {
            box(pose, dispatcher, buffers, Blocks.BARREL.defaultBlockState(),
                    rightX - 0.11f, floorY + 0.07f, 0.44f, 0.13f, light, OverlayTexture.NO_OVERLAY);
        }

        // Left corner: parcel chest and copper crate
        box(pose, dispatcher, buffers, Blocks.CHEST.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + (dense ? 0.08f : 0.05f), 0.28f, dense ? 0.15f : 0.09f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
                leftX + (dense ? 0.11f : 0.07f), floorY + (dense ? 0.20f : 0.12f), 0.28f, dense ? 0.09f : 0.05f, light, OverlayTexture.NO_OVERLAY);

        // Cast iron platform scales
        box(pose, dispatcher, buffers, Blocks.HEAVY_CORE.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + 0.04f, 0.65f, dense ? 0.08f : 0.05f, dense ? 0.05f : 0.03f, dense ? 0.08f : 0.05f, light, OverlayTexture.NO_OVERLAY);

        // 7. Double ceiling monorail of the warehouse with a stopper (Ceiling Monorail with Stopper)
        float railSpan = dense ? 0.09f : 0.055f;
        float railY = ceilY - 0.035f;
        float railThick = dense ? 0.035f : 0.022f;
        // The rails start exactly at Z = 0.27f and never cross the plug!
        box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0.50f - railSpan, railY, 0.54f, railThick, railThick, 0.54f, light, OverlayTexture.NO_OVERLAY);
        box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0.50f + railSpan, railY, 0.54f, railThick, railThick, 0.54f, light, OverlayTexture.NO_OVERLAY);
        // Copper buffer stopper at the end of the monorail
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0.50f, railY - 0.005f, 0.26f, railSpan * 2f + railThick * 1.5f, railThick * 1.4f, 0.04f, light, OverlayTexture.NO_OVERLAY);

        // Ceiling carriage of the manipulator on the rail
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0.50f, railY - 0.02f, 0.40f, dense ? 0.14f : 0.09f, dense ? 0.04f : 0.025f, dense ? 0.12f : 0.08f, light, OverlayTexture.NO_OVERLAY);
    }

    /** Plugs on closed sides and pipe dead ends: a north-facing model turned towards its side. */
    private static void plugs(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        int mask = terminalMask(be, state);
        if (mask == 0) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        BakedModel plugModel = null;
        BakedModel dockModel = null;
        var renderer = mc.getBlockRenderer().getModelRenderer();
        var dispatcher = mc.getBlockRenderer();
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            Direction d = DIRS[i];
            boolean warehouse = be.getLevel() != null && isWarehouse(be.getLevel(), be.getBlockPos(), d);
            BakedModel model;
            if (warehouse) {
                if (dockModel == null) dockModel = mc.getModelManager().getModel(dense ? DOCK_DENSE : DOCK_BASIC);
                model = dockModel;
            } else {
                if (plugModel == null) plugModel = mc.getModelManager().getModel(dense ? PLUG_DENSE : PLUG_BASIC);
                model = plugModel;
            }
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

            // If the dead end is chosen as a Warehouse (at most 1 warehouse within 3 blocks), render the Victorian warehouse decorations
            if (warehouse) {
                renderWarehouseDecorations(pose, dispatcher, buffers, dense, light);
            }

            pose.popPose();
        }
    }

    /**
     * The heat lamp on a straight horizontal pipe (every sixth block, see SnowPipeBlock.hasLamp): two nixie tubes on a walnut base
     * from the baked lamp model, and in them the number of channels running through the pipe, glowing cyan while there is room,
     * amber when it fills, red when it is (nearly) full. On the east side of a north-south run, on the south of an east-west one.
     */
    private static void heatLamp(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        var renderer = mc.getBlockRenderer().getModelRenderer();
        var lampModel = mc.getModelManager().getModel(dense ? LAMP_DENSE : LAMP_BASIC);
        pose.pushPose();
        if (SnowPipeBlock.straightAxis(state) == Direction.Axis.X) {
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-90));
            pose.translate(-0.5, -0.5, -0.5);
        }
        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, lampModel, 1f, 1f, 1f, light,
                OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.cutout());
        // the digits face east, in front of the tubes' mesh planes; seen from there the tens tube is on the left (south)
        int load = Math.min(99, be.lampLoad());
        int color = NixieDigits.loadColor(load, be.lampCapacity());
        // Outside the translucent glass wall (x = 16.2 / 18.3) so 1.21.1 translucent glass sorting doesn't swallow emissive digits
        float x = (dense ? 14.7f : 12.6f) + 3.65f;
        VertexConsumer nixie = NixieDigits.buffer(buffers);
        if (load >= 10) {
            NixieDigits.digit(pose.last(), nixie, load / 10, x, 8f, 9.35f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        }
        NixieDigits.digit(pose.last(), nixie, load % 10, x, 8f, 6.65f, 2.2f, 4.4f, 0f, 0f, -1f, 0f, 1f, 0f, color);
        // under Iris/Oculus shader packs, translucent glass can wash out or hide emissive digits behind it: skip glass then
        if (!JarWholeRenderer.shaderPackActive()) {
            var glassModel = mc.getModelManager().getModel(dense ? LAMP_GLASS_DENSE : LAMP_GLASS_BASIC);
            renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.translucent()), null, glassModel, 1f, 1f, 1f, light,
                    OverlayTexture.NO_OVERLAY, net.neoforged.neoforge.client.model.data.ModelData.EMPTY, RenderType.translucent());
        }
        pose.popPose();
    }

    @Override
    public void render(PipeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (be.getLevel() == null) {
            return;
        }
        plugs(be, state, pose, buffers, light);
        if (SnowPipeBlock.hasLamp(state, be.getBlockPos())) {
            heatLamp(be, state, pose, buffers, light);
        }
        if (!state.hasProperty(GolemProperties.ACTIVE) || !state.getValue(GolemProperties.ACTIVE)) {
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

        // Choosing delivery items from the network
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
            case UP, DOWN -> 180f; // Stable orientation facing forward (south) in vertical pipes
        };

        boolean vertical = spot.heading().getAxis() == Direction.Axis.Y;
        float yaw = baseYaw;
        float step = (float) Math.sin(time * 0.55f);

        if (turning) {
            // Hanging mechanical arm with a monorail and a copper golem on top at every turn (with or without a warehouse)
            // Oriented strictly along the pipe axis (baseYaw)
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(baseYaw));
            renderMechanicalArmAndGolem(pose, dispatcher, buffers, itemRenderer, be.getLevel(), outItem, dense, turn, time, light, overlay);
            pose.popPose();

            // During the snowman's calm turn (turn in 0..1):
            // 0.00..0.35: unloads the old item onto the platform floor (it disappears)
            // 0.35..0.65: smooth empty-handed 180-degree turn with lowered arms
            // 0.65..0.80: the copper golem above opens its hands and drops the new item, the snowman catches it
            // 0.80..1.00: absorbing the impact, the item is fixed in the hands/on the head, ready for the road
            // at the station the runner looks where it came from and smoothly turns to the exit (not in a vertical pipe)
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
        // a light swaying gait while running
        if (!turning) {
            pose.mulPose(Axis.ZP.rotationDegrees(step * 3f));
            pose.translate(0, Math.abs(step) * 0.012, 0);
        }

        if (dense) {
            // a whole snowman: the snow golem model with a pumpkin head; stick arms
            final float s = 0.2f;
            final float feet = vertical ? -0.16f : -0.355f;
            final float boxSize = 0.42f; // in the golem puppet's coordinates
            FEET_POS.set(0f, feet, 0f);

            if (!turning) {
                // Normal run: the item morphs into a crate right in the hands in the middle of the path (tripProgress ~ 0.5)
                // 0.00..0.44: carries the item itself in its hands
                // 0.44..0.56: the item turns into a crate right in the hands
                // 0.56..1.00: carries a closed crate
                float morphStart = 0.44f, morphEnd = 0.56f;
                boolean beforeMorph = tripProgress < morphStart;
                boolean afterMorph = tripProgress > morphEnd;

                SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, s, SnowmanPuppet.Head.PUMPKIN,
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
                // Smooth animation at the station: putting down the old item -> empty turn -> drop by the copper golem and the catch
                if (turn < 0.35f) {
                    // PHASE 1: Smoothly lowering the old item onto the platform floor
                    float pDown = turn / 0.35f;
                    float handY = Mth.lerp(pDown, 13.5f, 3.5f);
                    float handZ = Mth.lerp(pDown, 9.0f, 11.5f);
                    HAND_L.set(3.2f, handY, handZ);
                    HAND_R.set(-3.2f, handY, handZ);
                    SnowmanPuppet.Pose downPose = new SnowmanPuppet.Pose(0f, pDown * 0.12f, 0f, pDown * 20f, HAND_L, HAND_R);

                    float cargoY = Mth.lerp(pDown, 0f, -0.60f);
                    float cargoZ = Mth.lerp(pDown, 0f, 0.12f);
                    // When it touches the floor the item dissolves into the station
                    float itemScale = pDown > 0.75f ? (1f - (pDown - 0.75f) / 0.25f) * 0.44f : 0.44f;

                    SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            downPose,
                            hp -> {
                                if (itemScale > 0.01f) {
                                    renderItem(itemRenderer, buffers, be.getLevel(), inItem, hp, 0, cargoY, cargoZ, itemScale, time * 3f, light, overlay);
                                }
                            });
                } else if (turn < 0.65f) {
                    // PHASE 2: Turning with empty hands (the old item is handed over, the new one is still with the copper golem above)
                    SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            TURN_POSE, null);
                } else if (turn < 0.80f) {
                    // PHASE 3a: The snowman throws its arms up towards the item falling from above
                    float pReach = (turn - 0.65f) / 0.15f;
                    float handY = Mth.lerp(pReach, 9.0f, 18.0f);
                    float handZ = Mth.lerp(pReach, 1.5f, 7.5f);
                    HAND_L.set(3.2f, handY, handZ);
                    HAND_R.set(-3.2f, handY, handZ);
                    SnowmanPuppet.Pose reachPose = new SnowmanPuppet.Pose(0f, 0f, 0f, -14f, HAND_L, HAND_R);
                    SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            reachPose, null);
                } else {
                    // PHASE 3b: Caught it! Springy absorption of the impact and settling the item into the hands
                    float pSettle = (turn - 0.80f) / 0.20f;
                    float squash = (float) Math.sin((1f - pSettle) * Math.PI) * 0.14f;
                    float handY = Mth.lerp(pSettle, 18.0f, 13.5f);
                    float handZ = Mth.lerp(pSettle, 7.5f, 9.0f);
                    HAND_L.set(3.2f, handY, handZ);
                    HAND_R.set(-3.2f, handY, handZ);
                    SnowmanPuppet.Pose catchPose = new SnowmanPuppet.Pose(0f, squash, 0f, Mth.lerp(pSettle, -14f, 8f), HAND_L, HAND_R);

                    SnowmanPuppet.render(pose, buffers, light, FEET_POS, 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            catchPose,
                            hp -> renderItem(itemRenderer, buffers, be.getLevel(), outItem, hp, 0, 0, 0, 0.44f, time * 3f, light, overlay));
                }
            }
        } else {
            // Basic pipe: pumpkin head
            final float smallBoxScale = 0.11f;

            if (!turning) {
                box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                // Normal run: the item morphs into a crate right on the head
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
                // Small snowman turn: a hop throws the item off its head -> turns around -> catches a new one from the copper golem
                if (turn < 0.35f) {
                    // PHASE 1: A perky hop that drops the item from the head forward and down onto the floor
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
                    // PHASE 2: Empty smooth turn
                    box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                } else if (turn < 0.80f) {
                    // PHASE 3a: The pumpkin waits for the item to drop from above
                    box(pose, dispatcher, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                } else {
                    // PHASE 3b: The item is caught on top of the pumpkin with a soft cushioning
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
     * Hanging mechanical arm with a monorail and a copper golem at the runner's turn:
     * - Double ceiling monorail along the Z axis under the pipe ceiling.
     * - Ceiling carriage of the manipulator.
     * - Telescopic rod and the copper golem strictly within the pipe's inner volume.
     * - The copper golem holds the new cargo and drops it with free-fall acceleration into the snowman's hands / onto the pumpkin.
     */
    private static void renderMechanicalArmAndGolem(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                                   ItemRenderer itemRenderer, @Nullable Level level, ItemStack outItem,
                                                   boolean dense, float turn, float time, int light, int overlay) {
        float ceilingY = dense ? 0.28f : 0.165f;
        float targetFeetY = dense ? 0.02f : 0.01f;
        float retractedFeetY = dense ? 0.10f : 0.07f;
        float golemScale = dense ? 0.095f : 0.055f;
        float railThick = dense ? 0.035f : 0.022f;

        // Compact ceiling flange mounting the manipulator carriage
        float bracketSize = dense ? 0.18f : 0.12f;
        box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0, ceilingY + 0.02f, 0, bracketSize, railThick, bracketSize, light, overlay);

        // 2. The mechanical arm extends from the ceiling carriage
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

        // 3. Ceiling carriage of the monorail
        box(pose, dispatcher, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0, ceilingY, 0, dense ? 0.13f : 0.08f, dense ? 0.035f : 0.022f, dense ? 0.11f : 0.07f, light, overlay);

        // 4. Telescopic rod / pulley block
        float rodTop = ceilingY - 0.015f;
        float rodBottom = currentFeetY + golemScale * 1.15f;
        if (rodTop > rodBottom) {
            float rodH = rodTop - rodBottom;
            box(pose, dispatcher, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                    0, (rodTop + rodBottom) / 2f, 0, dense ? 0.035f : 0.022f, rodH, dense ? 0.035f : 0.022f, light, overlay);
        }

        // 5. Copper manipulator grab (cradle)
        box(pose, dispatcher, buffers, Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
                0, currentFeetY + golemScale * 1.15f, 0, dense ? 0.06f : 0.038f, 0.02f, dense ? 0.06f : 0.038f, light, overlay);

        // 6. Hanging copper golem (Copper Golem Puppet)
        boolean holding = turn < 0.70f;
        CopperGolemPuppet.Anim golemAnim = new CopperGolemPuppet.Anim(time * 0.1f, false, holding, 0.12f);
        CopperGolemPuppet.Arms arms = holding ? null : OPEN_ARMS;

        GOLEM_POS.set(0f, currentFeetY, 0f);
        CopperGolemPuppet.render(pose, buffers, light, GOLEM_POS, ARM_FORWARD, UP, golemScale, golemAnim, 0,
                hp -> {
                    // Cargo in the copper golem's hands until the drop
                    float cargoScale = dense ? 0.28f : 0.16f;
                    renderItem(itemRenderer, buffers, level, outItem, hp, 0, 0, 0, cargoScale, time * 3f, light, overlay);
                },
                arms);

        // 7. Falling cargo at the moment of the drop (turn in 0.70 .. 0.82)
        if (turn >= 0.70f && turn < 0.82f) {
            float dropP = (turn - 0.70f) / 0.12f;
            float dropCurve = dropP * dropP; // free-fall acceleration

            float startY = currentFeetY + golemScale * 0.60f;
            float targetY = dense ? 0.00f : -0.02f;
            float curY = Mth.lerp(dropCurve, startY, targetY);
            float curZ = Mth.lerp(dropP, 0.04f, 0.0f);
            float sc = dense ? 0.28f : 0.16f;

            renderItem(itemRenderer, buffers, level, outItem, pose, 0, curY, curZ, sc, time * 3f + dropP * 90f, light, overlay);
        }
    }

    private static BlockState[] cargoBoxes;

    // The set of available courier transport crates
    private static BlockState getCargoBox(int cargoId) {
        if (cargoBoxes == null) {
            BlockState barrel = Blocks.BARREL.defaultBlockState();
            cargoBoxes = new BlockState[]{
                    barrel,
                    barrel,
                    Blocks.COPPER_BLOCK.defaultBlockState(),
                    barrel,
                    Blocks.CHEST.defaultBlockState(),
                    barrel
            };
        }
        return cargoBoxes[Math.floorMod(cargoId, cargoBoxes.length)];
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
    private static WeakReference<Level> cargoLevel = new WeakReference<>(null);

    private static ItemStack getCargoItem(int cargoId, @Nullable Level level, @Nullable BlockPos pos) {
        List<ItemStack> netItems = null;
        List<ItemStack> netFluids = null;

        if (level != null && pos != null) {
            if (cargoLevel.get() != level) {
                CARGO_POOLS.clear();
                cargoLevel = new WeakReference<>(level);
            }
            long now = level.getGameTime();
            CargoPool pool = CARGO_POOLS.get(pos);
            if (pool == null || pool.expireTime < now || pool.expireTime - now > 40L) {
                if (CARGO_POOLS.size() > 512) {
                    CARGO_POOLS.clear();
                }
                pool = new CargoPool();
                pool.expireTime = now + 20L;
                try {
                    // samples of the network's contents sent with the core (the client never reads the storage itself)
                    GolemNetwork net = GolemNetwork.get(level, pos);
                    if (net != null && net.core() != null) {
                        pool.items.addAll(net.core().cargoItems());
                        pool.fluids.addAll(net.core().cargoFluids());
                    }
                    if (pool.items.isEmpty()) {
                        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                        for (Direction dir : DIRS) {
                            mpos.setWithOffset(pos, dir);
                            var nbe = level.getBlockEntity(mpos);
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

        // 1 time in 10: a bucket with a fluid from the network (exactly a 10% chance)
        boolean fluidRoll = (Math.floorMod(cargoId, 10) == 7);
        if (fluidRoll) {
            if (netFluids != null && !netFluids.isEmpty()) {
                return netFluids.get(Math.floorMod(cargoId / 10, netFluids.size()));
            }
            return FLUID_BUCKETS.get(Math.floorMod(cargoId / 10, FLUID_BUCKETS.size()));
        }

        // In the other 9 cases: a real item from the network
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

    /** A cube of size size centred at (x, y, z) relative to the current point. */
    private static void box(PoseStack pose, BlockRenderDispatcher dispatcher, MultiBufferSource buffers, BlockState state,
                            double x, double y, double z, float size, int light, int overlay) {
        box(pose, dispatcher, buffers, state, x, y, z, size, size, size, light, overlay);
    }

    /** A box of size (sx, sy, sz) centred at (x, y, z) relative to the current point. */
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
