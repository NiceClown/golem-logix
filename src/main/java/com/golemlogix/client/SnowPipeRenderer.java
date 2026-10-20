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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.world.Container;
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
 * A snowman with a pumpkin head and a crate running through powered pipes. There is one per connected pipe network
 * (see {@link PipeRunners}); in a basic pipe only its head is visible (the body does not fit), in a dense one the whole snowman.
 */
public class SnowPipeRenderer extends com.golemlogix.client.render.LegacyBer<PipeBlockEntity> {
    public SnowPipeRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final net.minecraft.resources.Identifier PLUG_BASIC = com.golemlogix.client.render.StandaloneModels.PLUG_BASIC;
    public static final net.minecraft.resources.Identifier PLUG_DENSE = com.golemlogix.client.render.StandaloneModels.PLUG_DENSE;
    public static final net.minecraft.resources.Identifier BAND_BASIC = com.golemlogix.client.render.StandaloneModels.BAND_BASIC;
    public static final net.minecraft.resources.Identifier BAND_DENSE = com.golemlogix.client.render.StandaloneModels.BAND_DENSE;
    /** Warehouse dock door: a dead end with a warehouse is closed not by a plug but by a roller shutter with a lamp. */
    public static final net.minecraft.resources.Identifier DOCK_BASIC = com.golemlogix.client.render.StandaloneModels.DOCK_BASIC;
    public static final net.minecraft.resources.Identifier DOCK_DENSE = com.golemlogix.client.render.StandaloneModels.DOCK_DENSE;

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
     * Returns the dead end / terminal directions of this pipe:
     * 1. Any side with a plug installed (isPlugged).
     * 2. Any open dead end of the line (if the pipe has exactly 1 PIPE connection and the opposite side is free, Link.NONE).
     * 3. A single pipe (no links) gets a terminal on its north side.
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
            // a single pipe: a plug only on the blind end, if it has one exit (otherwise it would close off a device)
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

        // Check the other sides of the same block (if one block has several dead ends)
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity thisBe) {
            List<Direction> myTerminals = getTerminalDirections(thisBe, thisBe.getBlockState());
            for (Direction otherDir : myTerminals) {
                if (otherDir != dir && getWarehouseScore(pos, otherDir) < myScore) {
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
    private static void renderWarehouseDecorations(PoseStack pose, MultiBufferSource buffers,
                                                   boolean dense, int light) {
        float floorY = dense ? 0.14f : 0.27f;
        float ceilY = dense ? 0.86f : 0.73f;
        float leftX = dense ? 0.15f : 0.28f;
        float rightX = dense ? 0.85f : 0.72f;

        int warmLight = 0xF000F0; // Maximum brightness for the lantern and lit displays

        // 1. Wooden plank deck of the Victorian warehouse platform
        float deckWidth = dense ? 0.60f : 0.30f;
        box(pose, buffers, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                0.50f, floorY + 0.01f, 0.54f, deckWidth, 0.02f, 0.54f, light, OverlayTexture.NO_OVERLAY);

        // 2. Wrought Victorian arched trusses and ribs on the walls
        float pillarThick = dense ? 0.04f : 0.028f;
        float pillarH = ceilY - floorY;
        float pillarMidY = (floorY + ceilY) / 2f;
        BlockState ironRib = Blocks.CUT_COPPER_SLAB.defaultBlockState();

        // Pair of posts 1 (inside the warehouse at Z = 0.28f, not crossing the plug)
        box(pose, buffers, ironRib, leftX + pillarThick / 2f, pillarMidY, 0.28f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, ironRib, rightX - pillarThick / 2f, pillarMidY, 0.28f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, ironRib, 0.50f, ceilY - 0.02f, 0.28f, deckWidth, 0.03f, pillarThick, light, OverlayTexture.NO_OVERLAY);

        // Pair of posts 2 (along the warehouse at Z = 0.68f)
        box(pose, buffers, ironRib, leftX + pillarThick / 2f, pillarMidY, 0.68f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, ironRib, rightX - pillarThick / 2f, pillarMidY, 0.68f, pillarThick, pillarH, pillarThick, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, ironRib, 0.50f, ceilY - 0.02f, 0.68f, deckWidth, 0.03f, pillarThick, light, OverlayTexture.NO_OVERLAY);

        // 3. Victorian industrial rack on the left wall
        float rackDepth = dense ? 0.09f : 0.05f;
        float rackX = leftX + rackDepth / 2f;
        // Shelf 1 (bottom) with ledgers and the archive
        box(pose, buffers, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                rackX, floorY + 0.12f, 0.46f, rackDepth, 0.02f, 0.32f, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, Blocks.BOOKSHELF.defaultBlockState(),
                rackX, floorY + 0.07f, 0.38f, rackDepth * 0.9f, 0.09f, 0.14f, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, Blocks.CHISELED_BOOKSHELF.defaultBlockState(),
                rackX, floorY + 0.07f, 0.52f, rackDepth * 0.9f, 0.09f, 0.14f, light, OverlayTexture.NO_OVERLAY);

        if (dense) {
            // Shelf 2 (middle, only in a dense pipe) with alchemical elixirs and instruments
            box(pose, buffers, Blocks.DARK_OAK_SLAB.defaultBlockState(),
                    rackX, floorY + 0.26f, 0.46f, rackDepth, 0.02f, 0.32f, light, OverlayTexture.NO_OVERLAY);
            box(pose, buffers, Blocks.AMETHYST_CLUSTER.defaultBlockState(),
                    rackX, floorY + 0.20f, 0.38f, rackDepth * 0.8f, 0.11f, 0.12f, warmLight, OverlayTexture.NO_OVERLAY);
            box(pose, buffers, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),
                    rackX, floorY + 0.28f, 0.52f, rackDepth * 0.8f, 0.02f, 0.12f, light, OverlayTexture.NO_OVERLAY);
        }

        // 4. Right wall: Victorian pressure gauge and distribution fittings
        float rightWallDist = dense ? 0.06f : 0.04f;
        box(pose, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                rightX - rightWallDist / 2f, floorY + (dense ? 0.28f : 0.16f), 0.42f, rightWallDist, dense ? 0.18f : 0.12f, 0.14f, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, Blocks.LIGHTNING_ROD.defaultBlockState(),
                rightX - rightWallDist / 2f, floorY + (dense ? 0.42f : 0.26f), 0.42f, 0.025f, dense ? 0.10f : 0.06f, 0.025f, light, OverlayTexture.NO_OVERLAY);

        // 5. Hanging Victorian brass lantern on a chain
        float lanternY = ceilY - (dense ? 0.15f : 0.10f);
        box(pose, buffers, Blocks.IRON_CHAIN.defaultBlockState(),
                0.50f, ceilY - (dense ? 0.05f : 0.03f), 0.32f, 0.025f, dense ? 0.06f : 0.04f, 0.025f, warmLight, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, Blocks.LANTERN.defaultBlockState(),
                0.50f, lanternY, 0.32f, dense ? 0.10f : 0.07f, dense ? 0.12f : 0.08f, dense ? 0.10f : 0.07f, warmLight, OverlayTexture.NO_OVERLAY);

        // 6. Stacks of Victorian cargo on the platform
        // Right corner: Victorian oak kegs
        box(pose, buffers, Blocks.BARREL.defaultBlockState(),
                rightX - (dense ? 0.12f : 0.08f), floorY + (dense ? 0.10f : 0.06f), 0.28f, dense ? 0.16f : 0.10f, light, OverlayTexture.NO_OVERLAY);
        if (dense) {
            box(pose, buffers, Blocks.BARREL.defaultBlockState(),
                    rightX - 0.11f, floorY + 0.07f, 0.44f, 0.13f, light, OverlayTexture.NO_OVERLAY);
        }

        // Left corner: parcel chest and copper crate
        box(pose, buffers, Blocks.CHEST.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + (dense ? 0.08f : 0.05f), 0.28f, dense ? 0.15f : 0.09f, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
                leftX + (dense ? 0.11f : 0.07f), floorY + (dense ? 0.20f : 0.12f), 0.28f, dense ? 0.09f : 0.05f, light, OverlayTexture.NO_OVERLAY);

        // Cast iron platform scales
        box(pose, buffers, Blocks.HEAVY_CORE.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + 0.04f, 0.65f, dense ? 0.08f : 0.05f, dense ? 0.05f : 0.03f, dense ? 0.08f : 0.05f, light, OverlayTexture.NO_OVERLAY);

        // 7. Double ceiling monorail of the warehouse with a stopper (Ceiling Monorail with Stopper)
        float railSpan = dense ? 0.09f : 0.055f;
        float railY = ceilY - 0.035f;
        float railThick = dense ? 0.035f : 0.022f;
        // The rails start exactly at Z = 0.27f and never cross the plug!
        box(pose, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0.50f - railSpan, railY, 0.54f, railThick, railThick, 0.54f, light, OverlayTexture.NO_OVERLAY);
        box(pose, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                0.50f + railSpan, railY, 0.54f, railThick, railThick, 0.54f, light, OverlayTexture.NO_OVERLAY);
        // Copper buffer stopper at the end of the monorail
        box(pose, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0.50f, railY - 0.005f, 0.26f, railSpan * 2f + railThick * 1.5f, railThick * 1.4f, 0.04f, light, OverlayTexture.NO_OVERLAY);

        // Ceiling carriage of the manipulator on the rail
        box(pose, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0.50f, railY - 0.02f, 0.40f, dense ? 0.14f : 0.09f, dense ? 0.04f : 0.025f, dense ? 0.12f : 0.08f, light, OverlayTexture.NO_OVERLAY);
    }

    /** Plugs on closed sides and pipe dead ends: a north-facing model turned towards its side. */
    private static void plugs(PipeBlockEntity be, BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        List<Direction> terminals = getTerminalDirections(be, state);
        if (terminals.isEmpty()) {
            return;
        }
        boolean dense = state.getBlock() instanceof SnowPipeBlock p && p.isDense();
        var mc = Minecraft.getInstance();
        var plugModel = (dense ? PLUG_DENSE : PLUG_BASIC);
        var dockModel = (dense ? DOCK_DENSE : DOCK_BASIC);
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
            com.golemlogix.client.render.StandaloneModels.render(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), model, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);

            // If the dead end is chosen as a Warehouse (at most 1 warehouse within 3 blocks), render the Victorian warehouse decorations
            if (warehouse) {
                renderWarehouseDecorations(pose, buffers, dense, light);
            }

            pose.popPose();
        }
    }

    /**
     * Support brackets with a gauge: rendered strictly every 4 blocks (a 4-block interval along the run).
     * The straight sections between them are smooth seamless glass tubes without extra rings.
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
        var model = (dense ? BAND_DENSE : BAND_BASIC);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        if (straightAxis == net.minecraft.core.Direction.Axis.X) {
            pose.mulPose(Axis.YP.rotationDegrees(90));
        } else if (straightAxis == net.minecraft.core.Direction.Axis.Y) {
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.translate(-0.5, -0.5, -0.5);

        com.golemlogix.client.render.StandaloneModels.render(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), model, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);
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
            renderMechanicalArmAndGolem(pose, buffers, be.getLevel(), outItem, dense, turn, time, light, overlay, spot.heading());
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

        boolean isVertical = spot.heading().getAxis() == Direction.Axis.Y;
        if (dense) {
            // a whole snowman: the snow golem model with a pumpkin head; stick arms
            final float s = 0.2f;
            final float feet = isVertical ? -0.16f : -0.355f;
            final float boxSize = 0.42f; // in the golem puppet's coordinates

            if (!turning) {
                // Normal run: the item morphs into a crate right in the hands in the middle of the path (tripProgress ~ 0.5)
                // 0.00..0.44: carries the item itself in its hands
                // 0.44..0.56: the item turns into a crate right in the hands
                // 0.56..1.00: carries a closed crate
                float morphStart = 0.44f, morphEnd = 0.56f;
                boolean beforeMorph = tripProgress < morphStart;
                boolean afterMorph = tripProgress > morphEnd;

                SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                        SnowmanPuppet.Pose.holding(Math.abs(step) * 0.6f),
                        hp -> {
                            if (beforeMorph) {
                                renderItem(buffers, be.getLevel(), outItem, hp, 0, 0, 0, 0.44f, time * 3f, light, overlay);
                            } else if (afterMorph) {
                                box(hp, buffers, carriedBox, 0, 0, 0, boxSize, light, overlay);
                            } else {
                                float m = (tripProgress - morphStart) / (morphEnd - morphStart);
                                if (m < 0.5f) {
                                    float p = m / 0.5f;
                                    float sc = (1f - p) * 0.44f;
                                    renderItem(buffers, be.getLevel(), outItem, hp, 0, 0, 0, sc, time * (3f + p * 12f), light, overlay);
                                } else {
                                    float p = (m - 0.5f) / 0.5f;
                                    float sc = p * boxSize;
                                    box(hp, buffers, carriedBox, 0, 0, 0, sc, light, overlay);
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
                    Vector3f lHand = new Vector3f(3.2f, handY, handZ);
                    Vector3f rHand = new Vector3f(-3.2f, handY, handZ);
                    SnowmanPuppet.Pose downPose = new SnowmanPuppet.Pose(0f, pDown * 0.12f, 0f, pDown * 20f, lHand, rHand);

                    float cargoY = Mth.lerp(pDown, 0f, -0.60f);
                    float cargoZ = Mth.lerp(pDown, 0f, 0.12f);
                    // When it touches the floor the item dissolves into the station
                    float itemScale = pDown > 0.75f ? (1f - (pDown - 0.75f) / 0.25f) * 0.44f : 0.44f;

                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            downPose,
                            hp -> {
                                if (itemScale > 0.01f) {
                                    renderItem(buffers, be.getLevel(), inItem, hp, 0, cargoY, cargoZ, itemScale, time * 3f, light, overlay);
                                }
                            });
                } else if (turn < 0.65f) {
                    // PHASE 2: Turning with empty hands (the old item is handed over, the new one is still with the copper golem above)
                    SnowmanPuppet.Pose turnPose = new SnowmanPuppet.Pose(0f, 0f, 0f, 0f, SnowmanPuppet.REST_LEFT, SnowmanPuppet.REST_RIGHT);
                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            turnPose, null);
                } else if (turn < 0.80f) {
                    // PHASE 3a: The snowman throws its arms up towards the item falling from above
                    float pReach = (turn - 0.65f) / 0.15f;
                    float handY = Mth.lerp(pReach, 9.0f, 18.0f);
                    float handZ = Mth.lerp(pReach, 1.5f, 7.5f);
                    SnowmanPuppet.Pose reachPose = new SnowmanPuppet.Pose(0f, 0f, 0f, -14f, new Vector3f(3.2f, handY, handZ), new Vector3f(-3.2f, handY, handZ));
                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            reachPose, null);
                } else {
                    // PHASE 3b: Caught it! Springy absorption of the impact and settling the item into the hands
                    float pSettle = (turn - 0.80f) / 0.20f;
                    float squash = (float) Math.sin((1f - pSettle) * Math.PI) * 0.14f;
                    float handY = Mth.lerp(pSettle, 18.0f, 13.5f);
                    float handZ = Mth.lerp(pSettle, 7.5f, 9.0f);
                    SnowmanPuppet.Pose catchPose = new SnowmanPuppet.Pose(0f, squash, 0f, Mth.lerp(pSettle, -14f, 8f), new Vector3f(3.2f, handY, handZ), new Vector3f(-3.2f, handY, handZ));

                    SnowmanPuppet.render(pose, buffers, light, new Vector3f(0f, feet, 0f), 180f, s, SnowmanPuppet.Head.PUMPKIN,
                            catchPose,
                            hp -> renderItem(buffers, be.getLevel(), outItem, hp, 0, 0, 0, 0.44f, time * 3f, light, overlay));
                }
            }
        } else {
            // Basic pipe: pumpkin head
            final float smallBoxScale = 0.11f;

            if (!turning) {
                box(pose, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                // Normal run: the item morphs into a crate right on the head
                float morphStart = 0.44f, morphEnd = 0.56f;
                boolean beforeMorph = tripProgress < morphStart;
                boolean afterMorph = tripProgress > morphEnd;

                if (beforeMorph) {
                    renderItem(buffers, be.getLevel(), outItem, pose, 0, 0.15, 0, 0.12f, time * 3f, light, overlay);
                } else if (afterMorph) {
                    box(pose, buffers, carriedBox, 0, 0.16, 0, smallBoxScale, light, overlay);
                } else {
                    float m = (tripProgress - morphStart) / (morphEnd - morphStart);
                    if (m < 0.5f) {
                        float p = m / 0.5f;
                        float sc = (1f - p) * 0.12f;
                        renderItem(buffers, be.getLevel(), outItem, pose, 0, 0.15, 0, sc, time * (3f + p * 12f), light, overlay);
                    } else {
                        float p = (m - 0.5f) / 0.5f;
                        float sc = p * smallBoxScale;
                        box(pose, buffers, carriedBox, 0, 0.16, 0, sc, light, overlay);
                    }
                }
            } else {
                // Small snowman turn: a hop throws the item off its head -> turns around -> catches a new one from the copper golem
                if (turn < 0.35f) {
                    // PHASE 1: A perky hop that drops the item from the head forward and down onto the floor
                    float pDown = turn / 0.35f;
                    float hop = (float) Math.sin(pDown * Math.PI) * 0.03f;
                    box(pose, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, hop, 0, 0.19f, light, overlay);

                    float flingP = Mth.clamp(pDown / 0.85f, 0f, 1f);
                    float flingY = Mth.lerp(flingP * flingP, 0.15f + hop, -0.14f);
                    float flingZ = Mth.lerp(flingP, 0.0f, 0.18f);
                    float sc = flingP > 0.70f ? (1f - (flingP - 0.70f) / 0.30f) * 0.12f : 0.12f;
                    if (sc > 0.005f) {
                        renderItem(buffers, be.getLevel(), inItem, pose, 0, flingY, flingZ, sc, time * 3f + flingP * 120f, light, overlay);
                    }
                } else if (turn < 0.65f) {
                    // PHASE 2: Empty smooth turn
                    box(pose, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                } else if (turn < 0.80f) {
                    // PHASE 3a: The pumpkin waits for the item to drop from above
                    box(pose, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, 0, 0, 0.19f, light, overlay);
                } else {
                    // PHASE 3b: The item is caught on top of the pumpkin with a soft cushioning
                    float pSettle = (turn - 0.80f) / 0.20f;
                    float squashHop = (float) -Math.sin((1f - pSettle) * Math.PI) * 0.03f;
                    box(pose, buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), 0, squashHop, 0, 0.19f, light, overlay);
                    renderItem(buffers, be.getLevel(), outItem, pose, 0, 0.15f + squashHop, 0, 0.12f, time * 3f, light, overlay);
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
    private static void renderMechanicalArmAndGolem(PoseStack pose, MultiBufferSource buffers,
                                                   @Nullable Level level, ItemStack outItem,
                                                   boolean dense, float turn, float time, int light, int overlay, Direction heading) {
        float ceilingY = dense ? 0.28f : 0.165f;
        float targetFeetY = dense ? 0.02f : 0.01f;
        float retractedFeetY = dense ? 0.10f : 0.07f;
        float golemScale = dense ? 0.095f : 0.055f;

        boolean isVertical = heading.getAxis() == Direction.Axis.Y;
        float railSpan = dense ? 0.09f : 0.055f;
        float railY = ceilingY + 0.02f;
        float railThick = dense ? 0.035f : 0.022f;

        // Compact ceiling flange mounting the manipulator carriage
        float bracketSize = dense ? 0.18f : 0.12f;
        box(pose, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
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
        box(pose, buffers, Blocks.COPPER_BLOCK.defaultBlockState(),
                0, ceilingY, 0, dense ? 0.13f : 0.08f, dense ? 0.035f : 0.022f, dense ? 0.11f : 0.07f, light, overlay);

        // 4. Telescopic rod / pulley block
        float rodTop = ceilingY - 0.015f;
        float rodBottom = currentFeetY + golemScale * 1.15f;
        if (rodTop > rodBottom) {
            float rodH = rodTop - rodBottom;
            box(pose, buffers, Blocks.CUT_COPPER_SLAB.defaultBlockState(),
                    0, (rodTop + rodBottom) / 2f, 0, dense ? 0.035f : 0.022f, rodH, dense ? 0.035f : 0.022f, light, overlay);
        }

        // 5. Copper manipulator grab (cradle)
        box(pose, buffers, Blocks.RAW_COPPER_BLOCK.defaultBlockState(),
                0, currentFeetY + golemScale * 1.15f, 0, dense ? 0.06f : 0.038f, 0.02f, dense ? 0.06f : 0.038f, light, overlay);

        // 6. Hanging copper golem (Copper Golem Puppet)
        boolean holding = turn < 0.70f;
        CopperGolemPuppet.Anim golemAnim = new CopperGolemPuppet.Anim(time * 0.1f, false, holding, 0.12f);
        CopperGolemPuppet.Arms arms = holding ? null : new CopperGolemPuppet.Arms(0.1f, -0.55f, 0.1f, 0.55f, 0f);

        Vector3f golemPos = new Vector3f(0f, currentFeetY, 0f);
        Vector3f forward = new Vector3f(0f, -0.20f, 0.98f).normalize();
        Vector3f up = new Vector3f(0f, 1f, 0f);

        CopperGolemPuppet.render(pose, buffers, light, golemPos, forward, up, golemScale, golemAnim, 0,
                hp -> {
                    // Cargo in the copper golem's hands until the drop
                    float cargoScale = dense ? 0.28f : 0.16f;
                    renderItem(buffers, level, outItem, hp, 0, 0, 0, cargoScale, time * 3f, light, overlay);
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

            renderItem(buffers, level, outItem, pose, 0, curY, curZ, sc, time * 3f + dropP * 90f, light, overlay);
        }
    }

    // The set of available courier transport crates
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
                    // samples of the network's contents sent with the core (the client never reads the storage itself)
                    GolemNetwork net = GolemNetwork.get(level, pos);
                    if (net != null && net.core() != null) {
                        pool.items.addAll(net.core().cargoItems());
                        pool.fluids.addAll(net.core().cargoFluids());
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

    private static void renderItem(MultiBufferSource buffers, @Nullable Level level, ItemStack stack,
                                   PoseStack pose, double x, double y, double z, float scale, float rotDeg, int light, int overlay) {
        if (stack.isEmpty() || scale <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(rotDeg));
        com.golemlogix.client.render.BerBuffers.item(buffers, stack, ItemDisplayContext.FIXED, light, pose, level, 0);
        pose.popPose();
    }

    /** A cube of size size centred at (x, y, z) relative to the current point. */
    private static void box(PoseStack pose, MultiBufferSource buffers, BlockState state,
                            double x, double y, double z, float size, int light, int overlay) {
        box(pose, buffers, state, x, y, z, size, size, size, light, overlay);
    }

    /** A box of size (sx, sy, sz) centred at (x, y, z) relative to the current point. */
    private static void box(PoseStack pose, MultiBufferSource buffers, BlockState state,
                            double x, double y, double z, float sx, float sy, float sz, int light, int overlay) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        pose.translate(-0.5, -0.5, -0.5);
        com.golemlogix.client.render.BerBuffers.block(buffers, state, pose, light);
        pose.popPose();
    }
}
