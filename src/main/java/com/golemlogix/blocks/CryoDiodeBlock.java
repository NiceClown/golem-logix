package com.golemlogix.blocks;

import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Cryo-Diode (Frost Valve): directional subnet isolator.
 * Connects two networks without merging their graphs, channels, storage, or crafting.
 * The network on the outlet side (FACING) can draw Source from the supplies of the inlet side.
 */
public class CryoDiodeBlock extends DirectionalBlock implements GolemNode, EntityBlock {
    public static final MapCodec<CryoDiodeBlock> CODEC = simpleCodec(CryoDiodeBlock::new);

    public CryoDiodeBlock(Properties properties) {
        super(properties.sound(SoundType.METAL).strength(2.5f).requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public int channelCost() {
        return 0;
    }

    /**
     * The Cryo-Diode never links into the BFS network graph on any face.
     * This strictly ensures the inlet and outlet sides remain two separate GolemNetwork instances.
     */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CryoDiodeBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // FACING points towards clicked face (direction of flow from player's placement)
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
