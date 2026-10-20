package com.golemlogix.blocks;

import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Cryo-Source Jar: two blocks high, a rune base, a glass flask in a verdigris copper frame and a brass cap with a vent; a copper
 * golem keeps the valve on its side. It stores Source (filled by a Cryo-Distillation Tower) and gives it to the network like an Ars
 * Nouveau jar. Both halves have a block entity so either touches the network; the lower one holds the Source. Drawn by
 * CryoJarRenderer from the lower half.
 */
public class CryoJarBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER_SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(2, 6, 2, 14, 16, 14));
    private static final VoxelShape UPPER_SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 7, 14), Block.box(1.5, 7, 1.5, 14.5, 14, 14.5),
            Block.box(6, 14, 6, 10, 16, 10));

    public CryoJarBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(2.5f).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** The lower half (where the Source is kept) of the jar at pos. */
    public static BlockPos lowerOf(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** Places a whole jar (commands, tests). */
    public static void place(Level level, BlockPos lower, Direction facing, Block block) {
        BlockState s = block.defaultBlockState().setValue(FACING, facing);
        level.setBlock(lower, s.setValue(HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(lower.above(), s.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.isInWorldBounds(pos.above()) || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    /** One half without the other goes too. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction toOther = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == toOther && (!neighbor.is(this) || neighbor.getValue(HALF) == half
                || neighbor.getValue(FACING) != state.getValue(FACING))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Only the lower half drops the jar: breaking the upper one breaks the lower one with its drop. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(this)) {
            level.destroyBlock(pos.below(), !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** A click shows how much Source is inside. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(lowerOf(pos, state)) instanceof CryoJarBlockEntity jar) {
            player.displayClientMessage(Component.translatable("message.golem_logix.cryo_jar", jar.available(), jar.capacity()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(lowerOf(pos, state)) instanceof CryoJarBlockEntity jar && jar.available() > 0
                ? 1 + (int) (14L * jar.available() / Math.max(1, jar.capacity())) : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CryoJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CRYO_JAR.get() || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> ((CryoJarBlockEntity) be).clientTick()
                : (lvl, p, st, be) -> ((CryoJarBlockEntity) be).serverTick();
    }
}
