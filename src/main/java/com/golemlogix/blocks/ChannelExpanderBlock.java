package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Channel Expander: goes into the side cells of the two middle layers of the Atlas Array and raises the capacity of every
 * Cable Bushing line. Its outer face is one more network exit: a pipe connects to it and carries 64 channels (the quantum one
 * 128), so the base does not have to be rebuilt for every new line. Inside an assembled array it transforms into a glowing
 * conduit pillar (blue-ice for ordinary, shimmering violet for quantum) fused seamlessly with the column above and below.
 */
public class ChannelExpanderBlock extends Block implements AtlasPart, GolemNode {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty PIPE = BooleanProperty.create("pipe");

    private final boolean quantum;

    public ChannelExpanderBlock(boolean quantum, Properties properties) {
        super(properties.sound(SoundType.AMETHYST).strength(5.0f, 8.0f).lightLevel(s -> quantum ? 13 : 10)
                .requiresCorrectToolForDrops().noOcclusion());
        this.quantum = quantum;
        registerDefaultState(stateDefinition.any()
                .setValue(FORMED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(PIPE, false));
    }

    public boolean isQuantum() {
        return quantum;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, FACING, UP, DOWN, PIPE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (!state.getValue(FORMED)) {
            return state.setValue(UP, false).setValue(DOWN, false).setValue(PIPE, false);
        }
        if (direction == Direction.UP) {
            return state.setValue(UP, AtlasConduitBlock.isPillar(neighborState));
        }
        if (direction == Direction.DOWN) {
            return state.setValue(DOWN, AtlasConduitBlock.isPillar(neighborState));
        }
        if (direction == state.getValue(FACING)) {
            boolean linked = !(neighborState.getBlock() instanceof AtlasPart)
                    && GolemNode.linked(level, pos, state, neighborPos, neighborState, direction);
            return state.setValue(PIPE, linked);
        }
        return state;
    }

    /** Inside the array only with its own parts, outside with any network nodes (pipes, buses, devices). */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return other.getBlock() instanceof GolemNode;
    }
}
