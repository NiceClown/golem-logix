package com.golemlogix.system;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker: the block is part of a Golem Logix network (it connects to neighbours right next to it).
 */
public interface GolemNode {
    /** How many channels the device takes (0 for infrastructure: pipes, bushings, the core). */
    default int channelCost() {
        return 0;
    }

    /** The same, depending on the block state: a multiblock device takes a channel with only one of its parts. */
    default int channelCost(BlockState state) {
        return channelCost();
    }

    /** The most channels that can pass through the node (limit of pipes and bushings). */
    default int channelCapacity() {
        return Integer.MAX_VALUE;
    }

    /** Whether the node agrees to connect to the neighbouring block (the inner parts of the Atlas Array link only to each other). */
    default boolean canLink(BlockState other) {
        return true;
    }

    /** The same, depending on the node's own state. */
    default boolean canLink(BlockState self, BlockState other) {
        return canLink(other);
    }

    /**
     * The same, depending on the side: toOther = the direction from this node to the neighbour. A multiblock (the Saturator) connects to the network only
     * with its sockets and only outwards. The network, pipes and buses always ask this method.
     */
    default boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return canLink(self, other);
    }

    /** Whether two neighbouring nodes are linked (both agree); toB = the direction from a to b. Pipe plugs are not visible here. */
    static boolean linked(BlockState a, BlockState b, Direction toB) {
        return a.getBlock() instanceof GolemNode na && b.getBlock() instanceof GolemNode nb
                && na.canLink(a, b, toB) && nb.canLink(b, a, toB.getOpposite());
    }

    /** The same in the world: a side closed by a pipe plug (on either of the two) gives no link. */
    static boolean linked(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos posA, BlockState a,
                          net.minecraft.core.BlockPos posB, BlockState b, Direction toB) {
        return linked(a, b, toB) && !com.golemlogix.blockentity.PipeBlockEntity.plugged(level, posA, toB)
                && !com.golemlogix.blockentity.PipeBlockEntity.plugged(level, posB, toB.getOpposite());
    }
}
