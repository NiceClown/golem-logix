package com.golemlogix.blocks;

import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.SaturatorStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mana Saturator, the "head" of a 3x3x2 multiblock (see {@link SaturatorStructure}): a press with an anvil in the upper middle
 * of the front wall. On its own it is a compact anvil block that assembles the machine around itself when the other blocks
 * stand correctly nearby; the assembled machine takes Source from the network through the "central box" (a dense frost pipe) or
 * from jars nearby. Takes no channels.
 */
public class ManaSaturatorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The machine is assembled: the neighbouring blocks are replaced by parts, and this block's model is a piece of the shared model. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /** Compact anvil before assembly. */
    private static final VoxelShape COMPACT_SHAPE = Block.box(0, 0, 2.5, 16, 15.2, 13.5);

    public ManaSaturatorBlock(Properties properties) {
        super(properties.sound(SoundType.NETHERITE_BLOCK).strength(5.0f, 8.0f).noOcclusion().pushReaction(PushReaction.BLOCK)
                .lightLevel(state -> state.getValue(FORMED) ? 9 : 6));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? SaturatorShapes.of(SaturatorStructure.CONTROLLER, state.getValue(FACING)) : COMPACT_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManaSaturatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (blockEntityType == ModBlockEntities.MANA_SATURATOR.get()) {
            return (lvl, p, st, be) -> ManaSaturatorBlockEntity.tick(lvl, p, st, (ManaSaturatorBlockEntity) be);
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!state.getValue(FORMED)) {
            // unassembled anvil: a blank goes onto the face for forging by hand (struck with the Forge Hammer, left-click)
            if (be instanceof ManaSaturatorBlockEntity anvil && stack.getItem() instanceof com.golemlogix.items.ForgeHammerItem) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable(anvil.anvilItem().isEmpty()
                            ? "message.golem_logix.anvil.empty" : "message.golem_logix.anvil.use_hammer"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            if (be instanceof ManaSaturatorBlockEntity anvil && ManaSaturatorBlockEntity.isInput(level, stack)) {
                if (!level.isClientSide()) {
                    if (!ManaSaturatorBlockEntity.isManualInput(level, stack)) {
                        player.displayClientMessage(Component.translatable("message.golem_logix.anvil.needs_machine"), true);
                    } else if (!anvil.anvilItem().isEmpty()) {
                        player.displayClientMessage(Component.translatable("message.golem_logix.anvil.busy"), true);
                    } else {
                        anvil.placeOnAnvil(stack);
                        if (!player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, 1.4f);
                        player.displayClientMessage(Component.translatable("message.golem_logix.anvil.placed",
                                ManaSaturatorBlockEntity.MANUAL_HITS), true);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (be instanceof ManaSaturatorBlockEntity saturator) {
            // the Forge Hammer switches auto-output (finished plates leave by themselves: into a Pattern Provider first, otherwise any inventory)
            if (stack.getItem() instanceof com.golemlogix.items.ForgeHammerItem) {
                if (!level.isClientSide()) {
                    boolean on = saturator.toggleAutoOutput();
                    player.displayClientMessage(Component.translatable("message.golem_logix.saturator.auto_output",
                            Component.translatable(on ? "gui.golem_logix.auto_output.on" : "gui.golem_logix.auto_output.off")), true);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COMPARATOR_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f,
                            on ? 1.4f : 0.9f);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            // Redstone Module: the first time it is installed into the machine, after that right-clicking with it cycles the mode
            if (stack.getItem() instanceof com.golemlogix.items.UpgradeItem up && up.type() == com.golemlogix.upgrade.UpgradeType.REDSTONE) {
                if (!level.isClientSide()) {
                    boolean installed = saturator.installOrCycleRedstone();
                    if (installed && !player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.displayClientMessage(Component.translatable(installed ? "message.golem_logix.saturator.redstone_added"
                                    : "message.golem_logix.saturator.redstone_mode",
                            Component.translatable(saturator.redstoneMode().key())), true);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.COMPARATOR_CLICK, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!ManaSaturatorBlockEntity.isInput(level, stack)) {
                // a pipe, a torch and the like are simply placed next to it (for example a pipe to the inlet), not put on the anvil
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            // a blank goes to the left deck (the queue), the copper bringer golem will carry it
            if (!level.isClientSide()) {
                if (saturator.roomInDeck(stack) > 0) {
                    saturator.addToDeck(stack.copyWithCount(1));
                    stack.shrink(1);
                } else {
                    player.displayClientMessage(Component.translatable("message.golem_logix.saturator.deck_full",
                            ManaSaturatorBlockEntity.DECK_MAX), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** With an empty hand: take the finished items from the right deck, and if there are none, the queue of blanks from the left one. */
    private static boolean giveBack(Level level, Player player, ManaSaturatorBlockEntity saturator) {
        ItemStack taken = saturator.takeFromDecks();
        if (taken.isEmpty()) {
            return false;
        }
        if (!player.getInventory().add(taken.copy())) {
            Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), taken.copy());
        }
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ManaSaturatorBlockEntity saturator)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction facing = state.getValue(FACING);
        if (!state.getValue(FORMED) && !saturator.anvilItem().isEmpty()) {
            // a blank or a finished plate lies on the anvil: right-click with an empty hand takes it
            ItemStack taken = saturator.takeFromAnvil();
            if (!player.getInventory().add(taken)) {
                Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), taken);
            }
            return InteractionResult.CONSUME;
        }
        if (!state.getValue(FORMED)) {
            // unassembled machine: assemble it if possible, otherwise hint at what is missing
            SaturatorStructure.Result r = SaturatorStructure.check(level, pos, facing);
            if (r.valid()) {
                SaturatorStructure.form((ServerLevel) level, pos, facing);
            } else {
                BlockPos m = r.missingPos();
                player.displayClientMessage(Component.translatable("message.golem_logix.saturator.unformed",
                        r.expected().getName(), m.getX(), m.getY(), m.getZ()), false);
                ((ServerLevel) level).sendParticles(ParticleTypes.END_ROD, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5,
                        14, 0.3, 0.3, 0.3, 0.02);
            }
            return InteractionResult.CONSUME;
        }
        // Shift + right-click with an empty hand removes the Redstone Module
        if (player.isShiftKeyDown() && saturator.removeRedstoneModule()) {
            ItemStack module = new ItemStack(com.golemlogix.init.ModItems.REDSTONE_MODULE.get());
            if (!player.getInventory().add(module)) {
                Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), module);
            }
            return InteractionResult.CONSUME;
        }
        if (giveBack(level, player, saturator)) {
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.golem_logix.saturator.status",
                saturator.getSource(), ManaSaturatorBlockEntity.BUFFER_MAX, saturator.getCoolers(), SaturatorStructure.VENTS.length,
                Component.translatable(saturator.autoOutput() ? "gui.golem_logix.auto_output.on" : "gui.golem_logix.auto_output.off")), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && state.getValue(FORMED)) {
                SaturatorStructure.disassemble(level, pos, state.getValue(FACING), null);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ManaSaturatorBlockEntity saturator) {
                for (ItemStack s : saturator.allItems()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
                }
                if (saturator.hasRedstoneModule()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            new ItemStack(com.golemlogix.init.ModItems.REDSTONE_MODULE.get()));
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
