package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;

/** Iron casing of the Atlas Array: the walls and crown of the multiblock. */
public class AtlasCasingBlock extends Block implements AtlasPart {
    public AtlasCasingBlock(Properties properties) {
        super(properties.sound(SoundType.NETHERITE_BLOCK).strength(5.0f, 8.0f).requiresCorrectToolForDrops());
    }
}