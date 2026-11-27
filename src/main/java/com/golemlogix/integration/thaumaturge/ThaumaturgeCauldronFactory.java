package com.golemlogix.integration.thaumaturge;

import com.golemlogix.blocks.EssentiaCauldronBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Isolation indirection so {@link ThaumaturgeCompat} never references {@link ThaumaturgeCauldronBlock}. */
final class ThaumaturgeCauldronFactory {
    private ThaumaturgeCauldronFactory() {
    }

    static EssentiaCauldronBlock create(BlockBehaviour.Properties properties) {
        return new ThaumaturgeCauldronBlock(properties);
    }
}
