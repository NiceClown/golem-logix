package com.golemlogix.blocks;

import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.ForgeHammerItem;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.system.SaturatorStructure;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
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

    /** Compact anvil before assembly (oriented along FACING). */
    private static final VoxelShape COMPACT_SHAPE_NS = Block.box(0, 0, 2.5, 16, 15.2, 13.5);
    private static final VoxelShape COMPACT_SHAPE_EW = Block.box(2.5, 0, 0, 13.5, 15.2, 16);

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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(FORMED)) {
            return SaturatorShapes.of(SaturatorStructure.CONTROLLER, facing);
        }
        return facing.getAxis() == Direction.Axis.X ? COMPACT_SHAPE_EW : COMPACT_SHAPE_NS;
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ManaSaturatorBlockEntity saturator)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return state.getValue(FORMED)
                ? useItemOnFormed(stack, level, pos, player, saturator)
                : useItemOnUnformed(stack, level, pos, player, saturator);
    }

    /** Unassembled anvil: blanks are placed onto the face for manual forging with the Forge Hammer (left-click). */
    private static ItemInteractionResult useItemOnUnformed(ItemStack stack, Level level, BlockPos pos, Player player,
                                                           ManaSaturatorBlockEntity anvil) {
        if (stack.getItem() instanceof ForgeHammerItem) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(anvil.anvilItem().isEmpty()
                        ? "message.golem_logix.anvil.empty" : "message.golem_logix.anvil.use_hammer"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!ManaSaturatorBlockEntity.isInput(level, stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
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
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.3f, 1.4f);
                player.displayClientMessage(Component.translatable("message.golem_logix.anvil.placed",
                        ManaSaturatorBlockEntity.MANUAL_HITS), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Assembled saturator: Forge Hammer toggles auto-output, Redstone Module installs/cycles mode, blanks queue onto the deck. */
    private static ItemInteractionResult useItemOnFormed(ItemStack stack, Level level, BlockPos pos, Player player,
                                                         ManaSaturatorBlockEntity saturator) {
        if (stack.getItem() instanceof ForgeHammerItem) {
            if (!level.isClientSide()) {
                boolean on = saturator.toggleAutoOutput();
                player.displayClientMessage(Component.translatable("message.golem_logix.saturator.auto_output",
                        Component.translatable(on ? "gui.golem_logix.auto_output.on" : "gui.golem_logix.auto_output.off")), true);
                level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6f, on ? 1.4f : 0.9f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (stack.getItem() instanceof UpgradeItem up && up.type() == UpgradeType.REDSTONE) {
            if (!level.isClientSide()) {
                boolean installed = saturator.installOrCycleRedstone();
                if (installed && !player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.translatable(installed ? "message.golem_logix.saturator.redstone_added"
                                : "message.golem_logix.saturator.redstone_mode",
                        Component.translatable(saturator.redstoneMode().key())), true);
                level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.6f, 1.2f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!ManaSaturatorBlockEntity.isInput(level, stack)) {
            // pipes, torches, etc. place normally next to the saturator instead of being consumed
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
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

    /** Gives stack to player inventory, dropping only any leftover items that did not fit. */
    private static void giveOrDrop(Level level, Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), stack);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ManaSaturatorBlockEntity saturator)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(FORMED)) {
            useWithoutItemUnformed(level, pos, state.getValue(FACING), player, saturator);
            return InteractionResult.CONSUME;
        }
        // Shift + right-click with an empty hand removes the Redstone Module
        if (player.isShiftKeyDown() && saturator.removeRedstoneModule()) {
            giveOrDrop(level, player, new ItemStack(ModItems.REDSTONE_MODULE.get()));
            return InteractionResult.CONSUME;
        }
        ItemStack taken = saturator.takeFromDecks();
        if (!taken.isEmpty()) {
            giveOrDrop(level, player, taken);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.golem_logix.saturator.status",
                saturator.getSource(), ManaSaturatorBlockEntity.BUFFER_MAX, saturator.getCoolers(), SaturatorStructure.VENTS.length,
                Component.translatable(saturator.autoOutput() ? "gui.golem_logix.auto_output.on" : "gui.golem_logix.auto_output.off")), true);
        return InteractionResult.CONSUME;
    }

    private static void useWithoutItemUnformed(Level level, BlockPos pos, Direction facing, Player player,
                                               ManaSaturatorBlockEntity saturator) {
        if (!saturator.anvilItem().isEmpty()) {
            giveOrDrop(level, player, saturator.takeFromAnvil());
            return;
        }
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
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                if (state.getValue(FORMED)) {
                    SaturatorStructure.disassemble(level, pos, state.getValue(FACING), null);
                }
                if (level.getBlockEntity(pos) instanceof ManaSaturatorBlockEntity saturator) {
                    for (ItemStack s : saturator.allItems()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
                    }
                    if (saturator.removeRedstoneModule()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                new ItemStack(ModItems.REDSTONE_MODULE.get()));
                    }
                    saturator.clearForRemoval();
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
