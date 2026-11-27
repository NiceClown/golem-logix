package com.golemlogix.blocks;

import com.golemlogix.blockentity.VisCondenserBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.VisCondenserMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Vis Condenser: a 1x2 vertical double-block tower that condenses Thaumaturge chunk Vis into Cryo-Source
 * while respecting a configurable Aura floor with hysteresis and Redstone pause. Section 0 holds the block entity.
 */
public class VisCondenserBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int HEIGHT = 2;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, HEIGHT - 1);

    private static final VoxelShape BASE_SHAPE = Shapes.block();
    private static final VoxelShape UPPER_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 15.0, 14.0);

    public VisCondenserBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECTION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SECTION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SECTION) == 0 ? BASE_SHAPE : UPPER_SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    public static BlockPos baseOf(BlockPos pos, BlockState state) {
        return VerticalMultiblock.baseOf(pos, state, SECTION);
    }

    public static List<BlockPos> cells(BlockPos base) {
        return List.of(base.immutable(), base.above().immutable());
    }

    @Nullable
    public static VisCondenserBlockEntity entityOf(BlockGetter level, BlockPos pos, BlockState state) {
        return VerticalMultiblock.baseEntity(level, pos, state, SECTION, VisCondenserBlockEntity.class);
    }

    /** Places both halves of the condenser (commands, tests). */
    public static void place(Level level, BlockPos base, Direction facing, Block block) {
        VerticalMultiblock.placeAll(level, base, facing, block, FACING, SECTION, HEIGHT);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!VerticalMultiblock.canPlaceAbove(context, HEIGHT)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        VerticalMultiblock.placeAbove(level, pos, state, SECTION, HEIGHT);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos,
                                     BlockPos neighborPos) {
        if (!VerticalMultiblock.isNeighborValid(this, state, direction, neighbor, FACING, SECTION, HEIGHT)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        VerticalMultiblock.destroyBaseIfUpper(this, level, pos, state, player, FACING, SECTION);
        return super.playerWillDestroy(level, pos, state, player);
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        VisCondenserBlockEntity be = entityOf(level, pos, state);
        if (!level.isClientSide() && be != null) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new VisCondenserMenu(id, inv, be.menuData(), ContainerLevelAccess.create(level, be.getBlockPos())),
                    Component.translatable("block.golem_logix.vis_condenser")), buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SECTION) == 0 ? new VisCondenserBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.VIS_CONDENSER.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> ((VisCondenserBlockEntity) be).clientTick()
                : (lvl, p, st, be) -> ((VisCondenserBlockEntity) be).serverTick();
    }
}
