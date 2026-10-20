package com.golemlogix.blocks;

import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Cryo-Mixer, two blocks high, placed with one item: a rune plinth with louvred brass sides around a copper module, a tray in front;
 * on top two flasks, water on the left and Liquid Cryo-Source on the right. It freezes the water with the Cryo-Source into snow
 * blocks (fast) or ice (slow). The fluids go in through either block (pipes, buses, a bucket of water); the snow or ice comes out
 * of the tray (buses) or the window. The lower block (section 0) holds everything; CryoMixerRenderer draws it all.
 */
public class CryoMixerBlock extends Block implements RemovalAware, EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int HEIGHT = 2;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, HEIGHT - 1);

    private static final VoxelShape BASE_SHAPE = Shapes.block();
    private static final VoxelShape FLASKS_SHAPE = Shapes.or(Block.box(1.5, 0, 1.5, 7.5, 15, 7.5), Block.box(8.5, 0, 1.5, 14.5, 15, 7.5),
            Block.box(1.5, 0, 8.5, 14.5, 4, 14.5));

    public CryoMixerBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECTION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SECTION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SECTION) == 0 ? BASE_SHAPE : FLASKS_SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    public static BlockPos baseOf(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(SECTION));
    }

    @Nullable
    public static CryoMixerBlockEntity entityOf(BlockGetter level, BlockPos pos, BlockState state) {
        return state.hasProperty(SECTION) && level.getBlockEntity(baseOf(pos, state)) instanceof CryoMixerBlockEntity m ? m : null;
    }

    /** Places a whole mixer (commands, tests). */
    public static void place(Level level, BlockPos base, Direction facing, Block block) {
        for (int i = 0; i < HEIGHT; i++) {
            level.setBlock(base.above(i), block.defaultBlockState().setValue(FACING, facing).setValue(SECTION, i), 3);
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        BlockPos up = pos.above();
        if (!level.isInWorldBounds(up) || !level.getBlockState(up).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(SECTION, 1), 3);
    }

    /** A half whose partner is gone goes too. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        int s = state.getValue(SECTION);
        int expected = direction == Direction.UP ? s + 1 : direction == Direction.DOWN ? s - 1 : -1;
        if (expected >= 0 && expected < HEIGHT && (!neighbor.is(this) || neighbor.getValue(SECTION) != expected
                || neighbor.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    /** Only the lower block drops the mixer: breaking the flasks breaks the lower block with its drop. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(SECTION) != 0) {
            BlockPos base = baseOf(pos, state);
            if (level.getBlockState(base).is(this)) {
                level.destroyBlock(base, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A bucket of water (or any container with a fluid it takes) pours in; an empty hand opens the window. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (FluidUtil.getFluidHandler(stack).isPresent()) {
            return FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())
                    ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        CryoMixerBlockEntity be = entityOf(level, pos, state);
        if (!level.isClientSide() && be != null) {
            player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.golemlogix.menu.CryoMixerMenu(id, inv, be.getOutput(), be.getUpgrades(), be.menuData(),
                            be, net.minecraft.world.inventory.ContainerLevelAccess.create(level, be.getBlockPos())),
                    Component.translatable("block.golem_logix.cryo_mixer")), buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.SUCCESS;
    }

    /** Replaced by another block (see RemovalAware): the snow or ice and the upgrades drop. */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CryoMixerBlockEntity be) {
            be.dropContents();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SECTION) == 0 ? new CryoMixerBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CRYO_MIXER.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> ((CryoMixerBlockEntity) be).clientTick()
                : (lvl, p, st, be) -> ((CryoMixerBlockEntity) be).serverTick();
    }
}
