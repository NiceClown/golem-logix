package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Blocks that used to be full cubes and no longer are: a chunk saved back then keeps zero light in the block's own cell, the game
 * does not look at it again, and every part of the model set back from the cell's faces renders black. A check of the cell when
 * the block entity loads brings the light in.
 */
public final class StaleLight {
    private StaleLight() {
    }

    public static void recheck(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            BlockPos at = pos.immutable();
            server.getServer().execute(() -> server.getLightEngine().checkBlock(at));
        }
    }
}
