package com.golemlogix.blocks;

import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class PatternEncoderBlock extends HorizontalDirectionalBlock implements GolemNode, net.minecraft.world.level.block.EntityBlock {
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new com.golemlogix.blockentity.PatternEncoderBlockEntity(pos, state);
    }

    /** Blank and finished punch cards drop together with the encoder (the recipe in the grid is a ghost). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof com.golemlogix.blockentity.PatternEncoderBlockEntity be) {
            for (int i : new int[]{PatternEncoderMenu.CARD_IN, PatternEncoderMenu.CARD_OUT}) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        be.getItems().removeItemNoUpdate(i));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    public static final MapCodec<PatternEncoderBlock> CODEC = simpleCodec(PatternEncoderBlock::new);

    public PatternEncoderBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.0f).noOcclusion().requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof com.golemlogix.blockentity.PatternEncoderBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new PatternEncoderMenu(id, inv, be.getItems(), be.data(), ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.golem_logix.pattern_encoder")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
