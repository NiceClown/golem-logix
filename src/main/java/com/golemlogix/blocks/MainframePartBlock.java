package com.golemlogix.blocks;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import com.golemlogix.system.GolemNode;
import com.golemlogix.system.MainframeStructure;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Part of an assembled Cryo-Mainframe in place of glass or Atlas Casing. It is not drawn itself (the owner's renderer draws
 * the whole cabinet); clicks go to the owner; when broken it returns the former block and disassembles the cabinet. Network pipes can
 * be brought to any part: inside the cabinet all parts are linked to the owner.
 */
public class MainframePartBlock extends Block implements com.golemlogix.blocks.RemovalAware, GolemNode {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, MainframeStructure.CELLS.length - 1);

    public MainframePartBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(4.0f, 8.0f).noOcclusion().pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> 6));
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
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    public static BlockPos controllerOf(BlockPos pos, BlockState state) {
        return MainframeStructure.controllerOf(pos, state.getValue(FACING), state.getValue(CELL));
    }

    // ---------------------------------------------------------------------------------------------- network
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return other.getBlock() instanceof GolemNode;
    }

    // --------------------------------------------------------------------------------------- owner
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        BlockPos master = controllerOf(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof CryoMainframeBlock) {
            return ms.useItemOn(stack, level, player, hand, hit.withPosition(master));
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos master = controllerOf(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof CryoMainframeBlock) {
            return ms.useWithoutItem(level, player, hit.withPosition(master));
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------------------ disassembly and drops
    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide() && !MainframeStructure.isRestoring()) {
            MainframeStructure.disassemble(level, controllerOf(pos, state), state.getValue(FACING), pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        onRemoved(state, level, pos);
    }

    /** Drops the former block (glass stays whole without Silk Touch; one atlant for both halves), not the part itself. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(MainframeStructure.original(state.getValue(CELL))));
    }

    /** Atlants are iron statues: the cabinet part in their place sounds and breaks like them. */
    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity entity) {
        return MainframeStructure.isAtlant(state.getValue(CELL)) ? SoundType.ANVIL : SoundType.COPPER;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        BlockPos master = controllerOf(pos, state);
        return level.getBlockState(master).getBlock() instanceof CryoMainframeBlock mf ? new ItemStack(mf) : ItemStack.EMPTY;
    }
}
