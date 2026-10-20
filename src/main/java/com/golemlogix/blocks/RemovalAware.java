package com.golemlogix.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that does something when it is replaced by another block (drops its contents, takes a multiblock apart). Since 1.21.5 there
 * is no Block#onRemove: a block entity's preRemoveSideEffects calls this while the entity and its contents are still there, and a
 * block without an entity calls it from affectNeighborsAfterRemoval.
 */
public interface RemovalAware {
    void onRemoved(BlockState state, Level level, BlockPos pos);
}
