package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Conducting bus inside the Atlas Array. It links only to other parts of the array
 * (the core, bushings, other conduits), so channels leave the array only through Cable Bushings.
 * Stacked conduits (and formed expanders above/below them) merge into a single seamless glowing column.
 */
public class AtlasConduitBlock extends Block implements GolemNode, AtlasPart {
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public AtlasConduitBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(4.5f, 6.0f).requiresCorrectToolForDrops().noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false));
    }

    /** Whether {@code state} renders as a vertical glowing Atlas pillar that merges seamlessly above/below. */
    public static boolean isPillar(BlockState state) {
        if (state.getBlock() instanceof AtlasConduitBlock) {
            return true;
        }
        return state.getBlock() instanceof ChannelExpanderBlock && state.getValue(ChannelExpanderBlock.FORMED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState()
                .setValue(UP, isPillar(level.getBlockState(pos.above())))
                .setValue(DOWN, isPillar(level.getBlockState(pos.below())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.UP) {
            return state.setValue(UP, isPillar(neighborState));
        }
        if (direction == Direction.DOWN) {
            return state.setValue(DOWN, isPillar(neighborState));
        }
        return state;
    }

    @Override
    public boolean canLink(BlockState other) {
        return other.getBlock() instanceof AtlasPart;
    }
}
