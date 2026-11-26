package com.golemlogix.client;

import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.client.render.Baked;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.ref.WeakReference;

import static com.golemlogix.client.SnowPipeRenderer.DIRS;
import static com.golemlogix.client.SnowPipeRenderer.renderBlockBox;

/**
 * Warehouse dead-end election and Victorian interior miniature decorations for {@link SnowPipeRenderer}:
 * ensures at most one pipe dead end within a 3-block radius becomes a warehouse dock, and furnishes elected docks with a dark
 * oak plank deck, arched copper trusses, ledger racks, wall gauges, hanging brass lantern, cargo kegs/crates, and ceiling monorail.
 */
final class PipeWarehouseRenderer {
    private static final int WAREHOUSE_RADIUS = 3;
    private static final long CACHE_TTL_TICKS = 40L;
    private static final int MAX_CACHE_ENTRIES = 1024;

    private static final Long2BooleanOpenHashMap[] WAREHOUSE_CACHE = new Long2BooleanOpenHashMap[6];
    static {
        for (int i = 0; i < WAREHOUSE_CACHE.length; i++) {
            WAREHOUSE_CACHE[i] = new Long2BooleanOpenHashMap();
        }
    }

    private static int warehouseCacheSize = 0;
    private static WeakReference<Level> warehouseLevel = new WeakReference<>(null);
    private static long lastCacheClearTime = 0;
    private static final BlockPos.MutableBlockPos PROBE_POS = new BlockPos.MutableBlockPos();

    private PipeWarehouseRenderer() {
    }

    /** Clears the warehouse election cache on world unload or dimension switch. */
    static void clearCache() {
        clearMaps();
        warehouseLevel = new WeakReference<>(null);
        lastCacheClearTime = 0;
    }

    private static void clearMaps() {
        for (Long2BooleanOpenHashMap map : WAREHOUSE_CACHE) {
            map.clear();
        }
        warehouseCacheSize = 0;
    }

    /**
     * Deterministic local-minimum election for warehouse docks: within a 3-block sphere, only the terminal with the lowest hash
     * score becomes a warehouse dock; neighbouring dead ends keep their brass gauge plug.
     */
    static boolean isWarehouse(Level level, BlockPos pos, Direction dir) {
        if (!dir.getAxis().isHorizontal()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (warehouseLevel.get() != level || gameTime < lastCacheClearTime
                || gameTime - lastCacheClearTime > CACHE_TTL_TICKS || warehouseCacheSize > MAX_CACHE_ENTRIES) {
            clearMaps();
            warehouseLevel = new WeakReference<>(level);
            lastCacheClearTime = gameTime;
        }
        Long2BooleanOpenHashMap byDir = WAREHOUSE_CACHE[dir.ordinal()];
        long key = pos.asLong();
        if (byDir.containsKey(key)) {
            return byDir.get(key);
        }

        long myScore = warehouseScore(pos, dir);
        boolean isSmallest = !hasLowerScoreTerminal(level, pos, dir, myScore);

        for (int dx = -WAREHOUSE_RADIUS; dx <= WAREHOUSE_RADIUS && isSmallest; dx++) {
            for (int dy = -WAREHOUSE_RADIUS; dy <= WAREHOUSE_RADIUS && isSmallest; dy++) {
                for (int dz = -WAREHOUSE_RADIUS; dz <= WAREHOUSE_RADIUS && isSmallest; dz++) {
                    int distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > WAREHOUSE_RADIUS * WAREHOUSE_RADIUS || (dx == 0 && dy == 0 && dz == 0)) {
                        continue;
                    }
                    PROBE_POS.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (hasLowerScoreTerminal(level, PROBE_POS, null, myScore)) {
                        isSmallest = false;
                    }
                }
            }
        }

        byDir.put(key, isSmallest);
        warehouseCacheSize++;
        return isSmallest;
    }

    private static boolean hasLowerScoreTerminal(Level level, BlockPos pos, Direction skipDir, long myScore) {
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity pipe)) {
            return false;
        }
        int mask = SnowPipeRenderer.terminalMask(pipe, pipe.getBlockState());
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) != 0 && DIRS[i] != skipDir && warehouseScore(pos, DIRS[i]) < myScore) {
                return true;
            }
        }
        return false;
    }

    private static long warehouseScore(BlockPos pos, Direction dir) {
        long x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ (dir.ordinal() * 1000003L);
    }

    /** Renders the single ENTITYBLOCK_ANIMATED corner parcel chest inside an elected 1.21.1 warehouse dock. */
    static void renderCornerChest(PoseStack pose, Direction dir, MultiBufferSource buffers, boolean dense, int light, int overlay) {
        float floorY = dense ? 0.14f : 0.27f;
        float leftX = dense ? 0.15f : 0.28f;
        float chestS = dense ? 0.15f : 0.09f;
        pose.pushPose();
        Quads.orient(pose, dir);
        renderBlockBox(pose, Minecraft.getInstance().getBlockRenderer(), buffers, Blocks.CHEST.defaultBlockState(),
                leftX + (dense ? 0.12f : 0.08f), floorY + (dense ? 0.08f : 0.05f), 0.28f, chestS, light, overlay);
        pose.popPose();
    }

    /** Bakes the static warehouse interior into {@code vc} (split by ambient vs full-bright warm props). */
    static void bakeDecorations(PoseStack pose, VertexConsumer vc, boolean dense, boolean warmOnly) {
        float floorY = dense ? 0.14f : 0.27f;
        float ceilY = dense ? 0.86f : 0.73f;
        float leftX = dense ? 0.15f : 0.28f;
        float rightX = dense ? 0.85f : 0.72f;
        float rackDepth = dense ? 0.09f : 0.05f;
        float rackX = leftX + rackDepth / 2f;

        if (warmOnly) {
            if (dense) {
                bakeBox(pose, vc, Blocks.AMETHYST_CLUSTER.defaultBlockState(), rackX, floorY + 0.20f, 0.38f, rackDepth * 0.8f, 0.11f, 0.12f);
            }
            float lanternY = ceilY - (dense ? 0.15f : 0.10f);
            bakeBox(pose, vc, Blocks.CHAIN.defaultBlockState(), 0.50f, ceilY - (dense ? 0.05f : 0.03f), 0.32f, 0.025f, dense ? 0.06f : 0.04f, 0.025f);
            bakeBox(pose, vc, Blocks.LANTERN.defaultBlockState(), 0.50f, lanternY, 0.32f, dense ? 0.10f : 0.07f, dense ? 0.12f : 0.08f, dense ? 0.10f : 0.07f);
            return;
        }

        float deckWidth = dense ? 0.60f : 0.30f;
        bakeBox(pose, vc, Blocks.DARK_OAK_PLANKS.defaultBlockState(), 0.50f, floorY + 0.01f, 0.54f, deckWidth, 0.02f, 0.54f);

        float pillarThick = dense ? 0.04f : 0.028f;
        float pillarH = ceilY - floorY;
        float pillarMidY = (floorY + ceilY) / 2f;
        BlockState rib = Blocks.CUT_COPPER_SLAB.defaultBlockState();
        for (float z : new float[]{0.28f, 0.68f}) {
            bakeBox(pose, vc, rib, leftX + pillarThick / 2f, pillarMidY, z, pillarThick, pillarH, pillarThick);
            bakeBox(pose, vc, rib, rightX - pillarThick / 2f, pillarMidY, z, pillarThick, pillarH, pillarThick);
            bakeBox(pose, vc, rib, 0.50f, ceilY - 0.02f, z, deckWidth, 0.03f, pillarThick);
        }

        bakeBox(pose, vc, Blocks.DARK_OAK_SLAB.defaultBlockState(), rackX, floorY + 0.12f, 0.46f, rackDepth, 0.02f, 0.32f);
        bakeBox(pose, vc, Blocks.BOOKSHELF.defaultBlockState(), rackX, floorY + 0.07f, 0.38f, rackDepth * 0.9f, 0.09f, 0.14f);
        bakeBox(pose, vc, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), rackX, floorY + 0.07f, 0.52f, rackDepth * 0.9f, 0.09f, 0.14f);
        if (dense) {
            bakeBox(pose, vc, Blocks.DARK_OAK_SLAB.defaultBlockState(), rackX, floorY + 0.26f, 0.46f, rackDepth, 0.02f, 0.32f);
            bakeBox(pose, vc, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), rackX, floorY + 0.28f, 0.52f, rackDepth * 0.8f, 0.02f, 0.12f);
        }

        float rightWallDist = dense ? 0.06f : 0.04f;
        bakeBox(pose, vc, Blocks.COPPER_BLOCK.defaultBlockState(), rightX - rightWallDist / 2f, floorY + (dense ? 0.28f : 0.16f), 0.42f, rightWallDist, dense ? 0.18f : 0.12f, 0.14f);
        bakeBox(pose, vc, Blocks.LIGHTNING_ROD.defaultBlockState(), rightX - rightWallDist / 2f, floorY + (dense ? 0.42f : 0.26f), 0.42f, 0.025f, dense ? 0.10f : 0.06f, 0.025f);

        float barrelS = dense ? 0.16f : 0.10f;
        bakeBox(pose, vc, Blocks.BARREL.defaultBlockState(), rightX - (dense ? 0.12f : 0.08f), floorY + (dense ? 0.10f : 0.06f), 0.28f, barrelS, barrelS, barrelS);
        if (dense) {
            bakeBox(pose, vc, Blocks.BARREL.defaultBlockState(), rightX - 0.11f, floorY + 0.07f, 0.44f, 0.13f, 0.13f, 0.13f);
        }
        float rawS = dense ? 0.09f : 0.05f;
        bakeBox(pose, vc, Blocks.RAW_COPPER_BLOCK.defaultBlockState(), leftX + (dense ? 0.11f : 0.07f), floorY + (dense ? 0.20f : 0.12f), 0.28f, rawS, rawS, rawS);
        bakeBox(pose, vc, Blocks.HEAVY_CORE.defaultBlockState(), leftX + (dense ? 0.12f : 0.08f), floorY + 0.04f, 0.65f, dense ? 0.08f : 0.05f, dense ? 0.05f : 0.03f, dense ? 0.08f : 0.05f);

        float railSpan = dense ? 0.09f : 0.055f;
        float railY = ceilY - 0.035f;
        float railThick = dense ? 0.035f : 0.022f;
        bakeBox(pose, vc, rib, 0.50f - railSpan, railY, 0.54f, railThick, railThick, 0.54f);
        bakeBox(pose, vc, rib, 0.50f + railSpan, railY, 0.54f, railThick, railThick, 0.54f);
        bakeBox(pose, vc, Blocks.COPPER_BLOCK.defaultBlockState(), 0.50f, railY - 0.005f, 0.26f, railSpan * 2f + railThick * 1.5f, railThick * 1.4f, 0.04f);
        bakeBox(pose, vc, Blocks.COPPER_BLOCK.defaultBlockState(), 0.50f, railY - 0.02f, 0.40f, dense ? 0.14f : 0.09f, dense ? 0.04f : 0.025f, dense ? 0.12f : 0.08f);
    }

    private static void bakeBox(PoseStack pose, VertexConsumer vc, BlockState state,
                                float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        pose.translate(-0.5f, -0.5f, -0.5f);
        Baked.drawBlock(pose.last(), vc, state);
        pose.popPose();
    }
}
