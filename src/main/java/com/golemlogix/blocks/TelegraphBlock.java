package com.golemlogix.blocks;

import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Cryo-Source Telegraph: a 2x2 rusty copper base three blocks high at its mast, placed with one item (like the Drive Cabinet).
 * Looking at its front: the operator golem sits on the front left cell, the key and the desk lamp stand on the front right one,
 * the mast with the crystal, the lattice and the dish rises from the back right. The base cells are network nodes that carry
 * 64 channels; the main cell (front left base) holds the cards and links to distant telegraphs (see TelegraphLinks).
 * Everything is drawn by TelegraphRenderer from the main cell.
 */
public class TelegraphBlock extends Block implements EntityBlock, GolemNode {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Channels a telegraph carries (like a dense pipe), across its link too. */
    public static final int CHANNELS = 64;

    /** A cell: right = cells to the right of the main one (seen from the front), back = cells behind it, up = cells above. */
    public enum Part implements StringRepresentable {
        BASE("base", 0, 0, 0),
        BASE_RIGHT("base_right", 1, 0, 0),
        BASE_BACK("base_back", 0, 1, 0),
        BASE_BACK_RIGHT("base_back_right", 1, 1, 0),
        OPERATOR("operator", 0, 0, 1),
        DESK("desk", 1, 0, 1),
        MAST("mast", 1, 1, 1),
        DISH("dish", 1, 1, 2);

        private final String key;
        public final int right, back, up;

        Part(String key, int right, int back, int up) {
            this.key = key;
            this.right = right;
            this.back = back;
            this.up = up;
        }

        @Nullable
        public static Part at(int right, int back, int up) {
            for (Part p : values()) {
                if (p.right == right && p.back == back && p.up == up) {
                    return p;
                }
            }
            return null;
        }

        public boolean isBase() {
            return up == 0;
        }

        @Override
        public String getSerializedName() {
            return key;
        }
    }

    private static final VoxelShape OPERATOR_SHAPE = Block.box(2, 0, 2, 14, 12, 14);
    private static final VoxelShape DESK_SHAPE = Block.box(1, 0, 1, 15, 7, 15);
    private static final VoxelShape MAST_SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape DISH_SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public TelegraphBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).noOcclusion().pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BASE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public static boolean isMaster(BlockState state) {
        return state.getBlock() instanceof TelegraphBlock && state.getValue(PART) == Part.BASE;
    }

    // ------------------------------------------------------------------ geometry

    /** "Right" as seen from the front of a block facing this way. */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        return master.relative(rightOf(facing), part.right).relative(facing.getOpposite(), part.back).above(part.up);
    }

    public static BlockPos masterOf(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        return pos.relative(rightOf(facing), -part.right).relative(facing.getOpposite(), -part.back).below(part.up);
    }

    @Nullable
    public static TelegraphBlockEntity entityOf(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(masterOf(pos, state)) instanceof TelegraphBlockEntity t ? t : null;
    }

    /** Places the whole telegraph (commands, tests); the main cell goes to master. */
    public static void place(Level level, BlockPos master, Direction facing) {
        Block block = ModBlocks.CRYO_SOURCE_TELEGRAPH.get();
        for (Part part : Part.values()) {
            level.setBlock(partPos(master, facing, part), block.defaultBlockState().setValue(FACING, facing).setValue(PART, part), 3);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case OPERATOR -> OPERATOR_SHAPE;
            case DESK -> DESK_SHAPE;
            case MAST -> MAST_SHAPE;
            case DISH -> DISH_SHAPE;
            default -> Shapes.block();
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        for (Part part : Part.values()) {
            BlockPos p = partPos(master, facing, part);
            if (!level.isInWorldBounds(p) || (part != Part.BASE && !level.getBlockState(p).canBeReplaced(context))) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.BASE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (part != Part.BASE) {
                level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), 3);
            }
        }
    }

    /** A cell whose neighbouring cell of the telegraph is gone (or became something else) goes too: the whole telegraph breaks. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Part part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        int r = part.right, b = part.back, u = part.up;
        Direction right = rightOf(facing);
        if (direction == right) {
            r++;
        } else if (direction == right.getOpposite()) {
            r--;
        } else if (direction == facing.getOpposite()) {
            b++;
        } else if (direction == facing) {
            b--;
        } else if (direction == Direction.UP) {
            u++;
        } else {
            u--;
        }
        Part expected = Part.at(r, b, u);
        if (expected != null && (!neighbor.is(this) || neighbor.getValue(PART) != expected || neighbor.getValue(FACING) != facing)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * Only the main cell drops the telegraph (an explosion through several cells must not drop several); breaking another cell
     * breaks the main one with its drop.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(PART) != Part.BASE) {
            BlockPos master = masterOf(pos, state);
            if (level.getBlockState(master).is(this)) {
                level.destroyBlock(master, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    // ------------------------------------------------------------------ network

    /** The base links to any network node around it; the mast, the desk and the operator only to the telegraph itself. */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        if (!(other.getBlock() instanceof GolemNode)) {
            return false;
        }
        return self.getValue(PART).isBase() || other.getBlock() instanceof TelegraphBlock;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.BASE ? new TelegraphBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.TELEGRAPH.get()) {
            return null;
        }
        return level.isClientSide()
                ? (lvl, p, st, be) -> TelegraphBlockEntity.clientTick(lvl, p, st, (TelegraphBlockEntity) be)
                : (lvl, p, st, be) -> TelegraphBlockEntity.serverTick(lvl, p, st, (TelegraphBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        TelegraphBlockEntity be = entityOf(level, pos, state);
        if (be != null) {
            player.openMenu(be, buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TelegraphBlockEntity be) {
            be.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
