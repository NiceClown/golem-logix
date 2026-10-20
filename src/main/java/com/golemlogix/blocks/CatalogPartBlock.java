package com.golemlogix.blocks;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.system.CatalogStructure;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Part of an assembled Punch Card Catalog (in place of shelves, copper blocks and statues). It is not drawn itself: the owner's
 * renderer draws the whole cabinet with the golems; clicks go to the owner; when broken it drops the former block and the cabinet is disassembled.
 */
public class CatalogPartBlock extends Block implements GolemNode {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, CatalogStructure.CELLS.length - 1);

    public CatalogPartBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(4.0f, 8.0f).noOcclusion().pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> 5));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return true;
    }

    public static BlockPos controllerOf(BlockPos pos, BlockState state) {
        return CatalogStructure.controllerOf(pos, state.getValue(FACING), state.getValue(CELL));
    }

    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return other.getBlock() instanceof GolemNode;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos master = controllerOf(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof CardCatalogBlock) {
            return ms.useItemOn(stack, level, player, hand, hit.withPosition(master));
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos master = controllerOf(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof CardCatalogBlock) {
            return ms.useWithoutItem(level, player, hit.withPosition(master));
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && !CatalogStructure.isRestoring()) {
                CatalogStructure.disassemble(level, controllerOf(pos, state), state.getValue(FACING), pos);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    /** Drops the former block (the owner remembers it): a shelf, a copper block of the same oxidation, a golem statue. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        int cell = state.getValue(CELL);
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin != null && params.getLevel().getBlockEntity(controllerOf(BlockPos.containing(origin), state))
                instanceof CardCatalogBlockEntity be && be.original(cell) != null) {
            return List.of(new ItemStack(be.original(cell).getBlock()));
        }
        return List.of(new ItemStack(CatalogStructure.fallback(cell)));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockPos master = controllerOf(pos, state);
        return level.getBlockState(master).getBlock() instanceof CardCatalogBlock c ? new ItemStack(c) : ItemStack.EMPTY;
    }
}
