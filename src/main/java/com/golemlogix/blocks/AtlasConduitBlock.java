package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNode;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Проводящая шина внутри Решётки Атланта. Связана только с другими частями решётки
 * (ядром, муфтами, такими же шинами), чтобы каналы выходили наружу лишь через Кабельные Муфты.
 */
public class AtlasConduitBlock extends Block implements GolemNode, AtlasPart {
    public AtlasConduitBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(4.5f, 6.0f).requiresCorrectToolForDrops());
    }

    @Override
    public boolean canLink(BlockState other) {
        return other.getBlock() instanceof AtlasPart;
    }
}