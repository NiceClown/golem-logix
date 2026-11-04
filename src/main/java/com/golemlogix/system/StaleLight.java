package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks that used to be full cubes and no longer are: a chunk saved back then keeps zero light in the block's own cell, the game
 * does not look at it again, and every part of the model set back from the cell's faces renders black. Breaking and placing the
 * block does not help: to the game it has the same light properties as air, so nothing is rechecked.
 * <p>
 * So the cell is checked when the block entity loads, on both sides. The client needs its own check: it may have been sent the old
 * zero, and once the server's value is right the server sees nothing to update. The client's check waits a moment, because the
 * chunk's light data arrives after its block entities.
 */
public final class StaleLight {
    private static final int CLIENT_DELAY_TICKS = 40;
    /** Client: the cells still to check and the game time to do it. */
    private static final Map<BlockPos, Long> CLIENT_CHECKS = new ConcurrentHashMap<>();

    private StaleLight() {
    }

    public static void recheck(Level level, BlockPos pos) {
        BlockPos at = pos.immutable();
        if (level instanceof ServerLevel server) {
            server.getServer().execute(() -> server.getLightEngine().checkBlock(at));
        } else if (level.isClientSide()) {
            CLIENT_CHECKS.put(at, level.getGameTime() + CLIENT_DELAY_TICKS);
        }
    }

    /** Client, every tick. */
    public static void clientTick(Level level) {
        if (CLIENT_CHECKS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        CLIENT_CHECKS.entrySet().removeIf(e -> {
            if (now < e.getValue()) {
                return false;
            }
            if (level.hasChunkAt(e.getKey())) {
                level.getLightEngine().checkBlock(e.getKey());
            }
            return true;
        });
    }
}
