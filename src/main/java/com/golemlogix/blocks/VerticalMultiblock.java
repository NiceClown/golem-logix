package com.golemlogix.blocks;

import com.golemlogix.system.Transfers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Shared placement, neighbor-validation, destruction, and base-entity lookup for single-column vertical multiblocks
 * whose section 0 holds the block entity and drops the item (Cryo-Mixer, Cryo-Distillation Tower).
 */
final class VerticalMultiblock {
    private VerticalMultiblock() {
    }

    static BlockPos baseOf(BlockPos pos, BlockState state, IntegerProperty section) {
        return pos.below(state.getValue(section));
    }

    @Nullable
    static <T extends BlockEntity> T baseEntity(BlockGetter level, BlockPos pos, BlockState state, IntegerProperty section, Class<T> type) {
        if (!state.hasProperty(section)) {
            return null;
        }
        BlockPos base = baseOf(pos, state, section);
        if (level instanceof Level l && !l.isLoaded(base)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(base);
        return type.isInstance(be) ? type.cast(be) : null;
    }

    static boolean canPlaceAbove(BlockPlaceContext context, int height) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        for (int i = 1; i < height; i++) {
            BlockPos above = pos.above(i);
            if (!level.isInWorldBounds(above) || !level.getBlockState(above).canBeReplaced(context)) {
                return false;
            }
        }
        return true;
    }

    static void placeAbove(Level level, BlockPos base, BlockState baseState, IntegerProperty section, int height) {
        for (int i = 1; i < height; i++) {
            level.setBlock(base.above(i), baseState.setValue(section, i), Block.UPDATE_ALL);
        }
    }

    static void placeAll(Level level, BlockPos base, Direction facing, Block block,
                         EnumProperty<Direction> facingProp, IntegerProperty section, int height) {
        for (int i = 0; i < height; i++) {
            level.setBlock(base.above(i), block.defaultBlockState().setValue(facingProp, facing).setValue(section, i), Block.UPDATE_ALL);
        }
    }

    /** Returns false when the vertical neighbor along UP/DOWN no longer matches the expected adjacent section. */
    static boolean isNeighborValid(Block self, BlockState state, Direction direction, BlockState neighbor,
                                   EnumProperty<Direction> facingProp, IntegerProperty section, int height) {
        int s = state.getValue(section);
        int expected = direction == Direction.UP ? s + 1 : direction == Direction.DOWN ? s - 1 : -1;
        if (expected < 0 || expected >= height) {
            return true;
        }
        return neighbor.is(self)
                && neighbor.getValue(section) == expected
                && neighbor.getValue(facingProp) == state.getValue(facingProp);
    }

    /** Breaking any upper section destroys the base (section 0) so only the base drops the block item. */
    static void destroyBaseIfUpper(Block self, Level level, BlockPos pos, BlockState state, Player player,
                                   EnumProperty<Direction> facingProp, IntegerProperty section) {
        if (level.isClientSide() || state.getValue(section) == 0) {
            return;
        }
        BlockPos base = baseOf(pos, state, section);
        BlockState baseState = level.getBlockState(base);
        if (baseState.is(self) && baseState.getValue(section) == 0
                && baseState.getValue(facingProp) == state.getValue(facingProp)) {
            level.destroyBlock(base, !player.isCreative(), player);
        }
    }

    /** Handles bucket / fluid-container right-clicks on any section of the column. */
    static InteractionResult interactFluid(ItemStack stack, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (Transfers.hasFluidHandler(stack)) {
            return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection(), null)
                    ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
