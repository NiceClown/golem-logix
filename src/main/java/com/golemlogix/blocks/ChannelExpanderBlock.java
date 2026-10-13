package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Channel Expander: goes into the side cells of the two middle layers of the Atlas Array and raises the capacity of every
 * Cable Bushing line. Its outer face is one more network exit: a pipe connects to it and carries 64 channels (the quantum one
 * 128), so the base does not have to be rebuilt for every new line. Inside the array it links to the conduit.
 */
public class ChannelExpanderBlock extends Block implements AtlasPart, GolemNode {
    private final boolean quantum;

    public ChannelExpanderBlock(boolean quantum, Properties properties) {
        super(properties.sound(SoundType.AMETHYST).strength(5.0f, 8.0f).lightLevel(s -> quantum ? 12 : 9).requiresCorrectToolForDrops());
        this.quantum = quantum;
    }

    public boolean isQuantum() {
        return quantum;
    }

    /** Inside the array only with its own parts, outside with any network nodes (pipes, buses, devices). */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return other.getBlock() instanceof GolemNode;
    }
}
