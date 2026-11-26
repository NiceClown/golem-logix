package com.golemlogix.blocks;

import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.DistillationTowerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Cryo-Distillation Tower, three blocks high, placed with one item: a rune plinth with valves and a gauge, a glass column with a
 * coil where the blood rises and freezes, a louvred brass head under snow with the outlet. Blood (Essentia Vitae from Neo Vitae, or
 * any fluid of the tag golem_logix:cryo_distillable) goes in through any of its blocks, Source comes out into the Cryo-Source Jars
 * (and Ars Nouveau jars) touching it. The plinth (section 0) holds the tank; everything is drawn by DistillationTowerRenderer.
 */
public class DistillationTowerBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int HEIGHT = 3;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, HEIGHT - 1);

    private static final VoxelShape BASE_SHAPE = Shapes.block();
    private static final VoxelShape COLUMN_SHAPE = Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);
    private static final VoxelShape HEAD_SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 12, 15), Block.box(5, 12, 5, 11, 16, 11));

    public DistillationTowerBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECTION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SECTION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(SECTION)) {
            case 0 -> BASE_SHAPE;
            case 1 -> COLUMN_SHAPE;
            default -> HEAD_SHAPE;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    public static BlockPos baseOf(BlockPos pos, BlockState state) {
        return VerticalMultiblock.baseOf(pos, state, SECTION);
    }

    /** All the tower's blocks, from the plinth up. */
    public static List<BlockPos> cells(BlockPos base) {
        List<BlockPos> out = new ArrayList<>(HEIGHT);
        for (int i = 0; i < HEIGHT; i++) {
            out.add(base.above(i));
        }
        return out;
    }

    @Nullable
    public static DistillationTowerBlockEntity entityOf(BlockGetter level, BlockPos pos, BlockState state) {
        return VerticalMultiblock.baseEntity(level, pos, state, SECTION, DistillationTowerBlockEntity.class);
    }

    /** Places a whole tower (commands, tests). */
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

    /** A section whose neighbour above or below is gone goes too: the whole tower breaks. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (!VerticalMultiblock.isNeighborValid(this, state, direction, neighbor, FACING, SECTION, HEIGHT)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Only the plinth drops the tower: breaking another section breaks the plinth with its drop. */
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

    /** A bucket (or any fluid container) pours blood in; an empty hand shows the tank. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return VerticalMultiblock.interactFluid(stack, level, pos, player, hand, hit);
    }

    /** An empty hand opens the window: the tank, the pace and the co-processor slots. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        DistillationTowerBlockEntity be = entityOf(level, pos, state);
        if (!level.isClientSide() && be != null) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new DistillationTowerMenu(id, inv, be.getUpgrades(), be.menuData(),
                            ContainerLevelAccess.create(level, be.getBlockPos())),
                    Component.translatable("block.golem_logix.cryo_distillation_tower")), buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide() && level.getBlockEntity(pos) instanceof DistillationTowerBlockEntity be) {
            be.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SECTION) == 0 ? new DistillationTowerBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.DISTILLATION_TOWER.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> ((DistillationTowerBlockEntity) be).clientTick()
                : (lvl, p, st, be) -> ((DistillationTowerBlockEntity) be).serverTick();
    }
}
