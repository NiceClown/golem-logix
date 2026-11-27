package com.golemlogix.blocks;

import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.integration.thaumaturge.ThaumaturgeCompat;
import com.golemlogix.menu.EssentiaCauldronMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Essentia Cryo-Cauldron: a 1x2 double-block hollowed Iron Golem head cauldron cooled by Ice below,
 * pulling Auram from viewer's left ({@link #primarySide}) and Gelum from viewer's right ({@link #coolantSide})
 * to condense pure Cryo-Source. When Thaumaturge is loaded, a subclass implements {@code IEssentiaStreamPort}.
 */
public class EssentiaCauldronBlock extends Block implements RemovalAware, EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int HEIGHT = 2;
    public static final IntegerProperty SECTION = IntegerProperty.create("section", 0, HEIGHT - 1);

    private static final VoxelShape BASE_SHAPE = Shapes.block();
    private static final VoxelShape UPPER_SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public EssentiaCauldronBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SECTION, 0));
    }

    /** Viewer's left when facing the cauldron front (+X in north-facing model): pulls primary aspect (Auram). */
    public static Direction primarySide(Direction facing) {
        return facing.getClockWise();
    }

    /** Viewer's right when facing the cauldron front (-X in north-facing model): pulls coolant aspect (Gelum). */
    public static Direction coolantSide(Direction facing) {
        return facing.getCounterClockWise();
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
    public static EssentiaCauldronBlockEntity entityOf(BlockGetter level, BlockPos pos, BlockState state) {
        return VerticalMultiblock.baseEntity(level, pos, state, SECTION, EssentiaCauldronBlockEntity.class);
    }

    /** Places both halves of the cauldron (commands, tests). */
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
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (!VerticalMultiblock.isNeighborValid(this, state, direction, neighbor, FACING, SECTION, HEIGHT)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
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

    /** Right-clicking with a Thaumaturge Essentia Phial pours matching Auram or Gelum into the cauldron. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        EssentiaCauldronBlockEntity be = entityOf(level, pos, state);
        if (be == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return ThaumaturgeCompat.interactPhial(stack, level, be, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        EssentiaCauldronBlockEntity be = entityOf(level, pos, state);
        if (!level.isClientSide() && be != null) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new EssentiaCauldronMenu(id, inv, be.getUpgrades(), be.menuData(),
                            ContainerLevelAccess.create(level, be.getBlockPos())),
                    Component.translatable("block.golem_logix.essentia_cryo_cauldron")), buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof EssentiaCauldronBlockEntity be) {
            be.dropContents();
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SECTION) == 0 ? new EssentiaCauldronBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.ESSENTIA_CAULDRON.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> ((EssentiaCauldronBlockEntity) be).clientTick()
                : (lvl, p, st, be) -> ((EssentiaCauldronBlockEntity) be).serverTick();
    }
}
