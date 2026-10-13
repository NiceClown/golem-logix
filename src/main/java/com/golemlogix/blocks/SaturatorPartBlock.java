package com.golemlogix.blocks;

import com.golemlogix.blockentity.SaturatorPartBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.GolemNode;
import com.golemlogix.system.SaturatorStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Part of an assembled Mana Saturator. A player never gets this block: it appears in place of casings, the rod and the pipe when
 * the machine is assembled, keeps the former block and returns it on disassembly. The cell (CELL) defines which piece of the shared
 * model to show. Clicking any part works like clicking the owner; the network connects to the sockets (one on each
 * side except the roof). Cooling Co-Processors (COOLER) go on the roof vent grilles: each one speeds up the press.
 */
public class SaturatorPartBlock extends Block implements EntityBlock, GolemNode {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, SaturatorStructure.CELLS - 1);
    /** A Cooling Co-Processor stands on the grille. */
    public static final BooleanProperty COOLER = BooleanProperty.create("cooler");

    public SaturatorPartBlock(Properties properties) {
        super(properties.sound(SoundType.NETHERITE_BLOCK).strength(5.0f, 8.0f).requiresCorrectToolForDrops().noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(state -> 5));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, 0).setValue(COOLER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, COOLER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SaturatorShapes.of(state.getValue(CELL), state.getValue(FACING));
    }

    // ---------------------------------------------------------------------------------------------- network
    /** The network connects only to the sockets (one on the front, at the back, on the sides and at the bottom) and only to blocks outside the machine. */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return other.getBlock() instanceof GolemNode && !(other.getBlock() instanceof SaturatorPartBlock)
                && SaturatorStructure.portFace(self.getValue(CELL), self.getValue(FACING), toOther);
    }

    @Override
    public boolean canLink(BlockState self, BlockState other) {
        return false;
    }

    @Override
    public boolean canLink(BlockState other) {
        return false;
    }

    // --------------------------------------------------------------------------------------- owner
    public static BlockPos controllerOf(BlockPos pos, BlockState state) {
        return SaturatorStructure.controllerOf(pos, state.getValue(FACING), state.getValue(CELL));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Cooling Co-Processor: only on top, on a free vent grille
        if (stack.is(ModBlocks.COOLING_COPROCESSOR.get().asItem()) && SaturatorStructure.isVent(state.getValue(CELL))) {
            if (hit.getDirection() != Direction.UP || state.getValue(COOLER)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable(state.getValue(COOLER)
                            ? "message.golem_logix.saturator.cooler_busy" : "message.golem_logix.saturator.cooler_top"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(COOLER, true), 3);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1.0f, 0.8f);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.5f);
                ((ServerLevel) level).sendParticles(ParticleTypes.SNOWFLAKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        12, 0.25, 0.1, 0.25, 0.02);
                int n = SaturatorStructure.coolers(level, controllerOf(pos, state), state.getValue(FACING));
                player.displayClientMessage(Component.translatable("message.golem_logix.saturator.cooler_added", n,
                        SaturatorStructure.VENTS.length), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        BlockPos master = controllerOf(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof ManaSaturatorBlock) {
            return ms.useItemOn(stack, level, player, hand, hit.withPosition(master));
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Shift + right-click with an empty hand on a roof coprocessor removes it
        if (player.isShiftKeyDown() && state.getValue(COOLER)) {
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(COOLER, false), 3);
                ItemStack back = new ItemStack(ModBlocks.COOLING_COPROCESSOR.get());
                if (!player.getInventory().add(back)) {
                    Block.popResource(level, pos.above(), back);
                }
                level.playSound(null, pos, SoundEvents.COPPER_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        BlockPos master = controllerOf(pos, state);
        BlockState ms = level.getBlockState(master);
        if (ms.getBlock() instanceof ManaSaturatorBlock) {
            return ms.useWithoutItem(level, player, hit.withPosition(master));
        }
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------------------ disassembly and drops
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && !SaturatorStructure.isRestoring()) {
                SaturatorStructure.disassemble(level, controllerOf(pos, state), state.getValue(FACING), pos);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    /** Drops the former block (casing, rod, pipe), not the part itself; a coprocessor that stood on the grille drops too. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> out = new ArrayList<>();
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof SaturatorPartBlockEntity part && !part.getOriginal().isAir()) {
            out.add(new ItemStack(part.getOriginal().getBlock()));
        }
        if (state.getValue(COOLER)) {
            out.add(new ItemStack(ModBlocks.COOLING_COPROCESSOR.get()));
        }
        return out;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof SaturatorPartBlockEntity part && !part.getOriginal().isAir()) {
            return new ItemStack(part.getOriginal().getBlock());
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SaturatorPartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.SATURATOR_PART.get()) {
            return (lvl, p, st, be) -> SaturatorPartBlockEntity.serverTick(lvl, p, st, (SaturatorPartBlockEntity) be);
        }
        return null;
    }
}
