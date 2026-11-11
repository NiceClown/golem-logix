package com.golemlogix.blocks;

import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel Counter: three nixie tubes on a small walnut base, lit on both sides with the channels running through the block it is
 * fixed to (a pipe, a bushing, any network node); a right-click shows that block's capacity instead. It is not a network device itself
 * (pipes do not reach for it), it only reads the node it sits on: on top of it (a saddle round a pipe, or a plain foot) or on its side
 * (a bracket to a pipe, or a wall plate).
 */
public class ChannelCounterBlock extends Block implements EntityBlock {
    /**
     * How it is fixed: standing on a block or a pipe, or on a little shelf on the side of one. dy, dz: where the counter sits then
     * (pixels, facing north; on a shelf it is pushed back against the wall, and on a pipe raised to the tube's flat face).
     */
    public enum Mount implements StringRepresentable {
        FLOOR(0, 0), PIPE_BASIC(0, 0), PIPE_DENSE(0, 0), SIDE_BLOCK(0, 5.4f), SIDE_BASIC(7.1f, 5.4f), SIDE_DENSE(6.0f, 5.4f),
        /** On top of a pipe's junction box (the cube a pipe turns into next to a network block): sunk to the box's top. */
        HUB_BASIC(-4f, 0), HUB_DENSE(-2f, 0);

        public final float dy, dz;

        Mount(float dy, float dz) {
            this.dy = dy;
            this.dz = dz;
        }

        public boolean side() {
            return this == SIDE_BLOCK || this == SIDE_BASIC || this == SIDE_DENSE;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The side the tubes face (they are lit on the back too); on a side mount, away from what it hangs on. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public ChannelCounterBlock(Properties properties) {
        super(properties.sound(SoundType.WOOD).strength(1.0f).lightLevel(state -> 4).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.FLOOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    /** Where the node it reads stands: under it, or behind it on a side mount. */
    public static BlockPos attached(BlockPos pos, BlockState state) {
        return state.getValue(MOUNT).side() ? pos.relative(state.getValue(FACING).getOpposite()) : pos.below();
    }

    /** The axis of a straight pipe (linked on both ends of it and nowhere else), or null. */
    @Nullable
    private static Direction.Axis straightAxis(BlockState state) {
        if (!(state.getBlock() instanceof SnowPipeBlock)) {
            return null;
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction pos = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            if (!SnowPipeBlock.linked(state, pos) || !SnowPipeBlock.linked(state, pos.getOpposite())) {
                continue;
            }
            boolean other = false;
            for (Direction d : Direction.values()) {
                if (d.getAxis() != axis && SnowPipeBlock.linked(state, d)) {
                    other = true;
                }
            }
            return other ? null : axis;
        }
        return null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        Direction toPlayer = context.getHorizontalDirection().getOpposite();
        if (face.getAxis().isHorizontal()) {
            // on the side of something: the tubes look away from it
            BlockState wall = level.getBlockState(context.getClickedPos().relative(face.getOpposite()));
            return defaultBlockState().setValue(FACING, face).setValue(MOUNT, sideMount(wall, face));
        }
        BlockState below = level.getBlockState(context.getClickedPos().below());
        Mount m = topMount(below);
        Direction facing = toPlayer;
        if (m == Mount.PIPE_BASIC || m == Mount.PIPE_DENSE) {
            // astride a horizontal pipe: the row of tubes runs along it, the face turned to the player's side of it
            var at = context.getPlayer() != null ? context.getPlayer().position() : context.getClickLocation();
            BlockPos pos = context.getClickedPos();
            facing = straightAxis(below) == Direction.Axis.X ? (at.z > pos.getZ() + 0.5 ? Direction.SOUTH : Direction.NORTH)
                    : (at.x > pos.getX() + 0.5 ? Direction.EAST : Direction.WEST);
        }
        return defaultBlockState().setValue(FACING, facing).setValue(MOUNT, m);
    }

    /**
     * How it stands on what is below: astride a horizontal straight pipe, sunk onto a junction box, or on its foot. A vertical
     * pipe's end reaches the cell's top, so the foot stands on it.
     */
    static Mount topMount(BlockState below) {
        if (below.getBlock() instanceof SnowPipeBlock p) {
            Direction.Axis axis = straightAxis(below);
            if (axis == null) {
                return p.isDense() ? Mount.HUB_DENSE : Mount.HUB_BASIC;
            }
            if (axis.isHorizontal()) {
                return p.isDense() ? Mount.PIPE_DENSE : Mount.PIPE_BASIC;
            }
        }
        return Mount.FLOOR;
    }

    /**
     * How it hangs on the side of a wall: the bracket reaches for a pipe running across or for a junction box (both are lo..hi of
     * their cell, so the shelf is the same); a pipe whose end comes at it, or a block, takes the plate on the cell's face.
     */
    static Mount sideMount(BlockState wall, Direction face) {
        if (wall.getBlock() instanceof SnowPipeBlock p && straightAxis(wall) != face.getAxis()) {
            return p.isDense() ? Mount.SIDE_DENSE : Mount.SIDE_BASIC;
        }
        return Mount.SIDE_BLOCK;
    }

    /** A pipe below or behind changes its shape (a network block was put next to it, a branch added): the mount follows it. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Mount m = state.getValue(MOUNT);
        Direction facing = state.getValue(FACING);
        if (m.side() && direction == facing.getOpposite()) {
            return state.setValue(MOUNT, sideMount(neighbor, facing));
        }
        if (!m.side() && direction == Direction.DOWN) {
            Mount now = topMount(neighbor);
            if ((now == Mount.PIPE_BASIC || now == Mount.PIPE_DENSE) && facing.getAxis() == straightAxis(neighbor)) {
                // the row of tubes must run along the pipe
                facing = facing.getClockWise();
            }
            return state.setValue(MOUNT, now).setValue(FACING, facing);
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, ChannelCounterBlock::shape);
    }

    /** The counter's box (with its shelf on a side mount), turned as the blockstate turns the model. */
    private static VoxelShape shape(BlockState state) {
        Mount m = state.getValue(MOUNT);
        double x0 = 3.5, x1 = 12.5, y0 = Math.max(0, m.dy), y1 = Math.min(16, m.dy + 9.2), z0 = 6 + m.dz, z1 = m.side() ? 16 : 10 + m.dz;
        if (m.side()) {
            y0 = Math.max(0, y0 - 0.8);
        }
        return switch (state.getValue(FACING)) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    /** A right-click switches between the channels running through the node and the most it can carry. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChannelCounterBlockEntity be) {
            be.toggleMode();
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChannelCounterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.CHANNEL_COUNTER.get()) {
            return null;
        }
        return (lvl, p, st, be) -> ((ChannelCounterBlockEntity) be).serverTick();
    }
}
