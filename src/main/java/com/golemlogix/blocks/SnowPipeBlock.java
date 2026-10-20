package com.golemlogix.blocks;

import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class SnowPipeBlock extends Block implements com.golemlogix.blocks.RemovalAware, GolemNode, EntityBlock {
    public static final MapCodec<SnowPipeBlock> CODEC = simpleCodec(props -> new SnowPipeBlock(16, false, props));

    /**
     * What is in this direction: nothing, another pipe (solid glass), a device (brass cap) or, for a dense
     * pipe, a basic pipe: then the end is closed by a brass adapter washer with a hole for the thin tube.
     */
    public enum Link implements StringRepresentable {
        NONE("none"), PIPE("pipe"), DEVICE("device"), REDUCER("reducer");

        private final String name;

        Link(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Link> NORTH = EnumProperty.create("north", Link.class);
    public static final EnumProperty<Link> EAST = EnumProperty.create("east", Link.class);
    public static final EnumProperty<Link> SOUTH = EnumProperty.create("south", Link.class);
    public static final EnumProperty<Link> WEST = EnumProperty.create("west", Link.class);
    public static final EnumProperty<Link> UP = EnumProperty.create("up", Link.class);
    public static final EnumProperty<Link> DOWN = EnumProperty.create("down", Link.class);

    public static final Map<Direction, EnumProperty<Link>> LINKS = new EnumMap<>(Direction.class);

    static {
        LINKS.put(Direction.NORTH, NORTH);
        LINKS.put(Direction.EAST, EAST);
        LINKS.put(Direction.SOUTH, SOUTH);
        LINKS.put(Direction.WEST, WEST);
        LINKS.put(Direction.UP, UP);
        LINKS.put(Direction.DOWN, DOWN);
    }

    private final int maxChannels;
    private final boolean isDense;

    private static final VoxelShape CORE_SHAPE = Block.box(4, 4, 4, 12, 12, 12);
    private static final Map<Direction, VoxelShape> ARM_SHAPES = Map.of(
            Direction.NORTH, Block.box(4, 4, 0, 12, 12, 4),
            Direction.SOUTH, Block.box(4, 4, 12, 12, 12, 16),
            Direction.WEST, Block.box(0, 4, 4, 4, 12, 12),
            Direction.EAST, Block.box(12, 4, 4, 16, 12, 12),
            Direction.DOWN, Block.box(4, 0, 4, 12, 4, 12),
            Direction.UP, Block.box(4, 12, 4, 12, 16, 12)
    );
    /** The dense pipe is thicker: 12x12 instead of 8x8. */
    private static final VoxelShape DENSE_CORE_SHAPE = Block.box(2, 2, 2, 14, 14, 14);
    private static final Map<Direction, VoxelShape> DENSE_ARM_SHAPES = Map.of(
            Direction.NORTH, Block.box(2, 2, 0, 14, 14, 2),
            Direction.SOUTH, Block.box(2, 2, 14, 14, 14, 16),
            Direction.WEST, Block.box(0, 2, 2, 2, 14, 14),
            Direction.EAST, Block.box(14, 2, 2, 16, 14, 14),
            Direction.DOWN, Block.box(2, 0, 2, 14, 2, 14),
            Direction.UP, Block.box(2, 14, 2, 14, 16, 14)
    );

    public SnowPipeBlock(int maxChannels, boolean isDense, Properties properties) {
        super(properties.sound(SoundType.GLASS).noOcclusion().strength(1.0f));
        this.maxChannels = maxChannels;
        this.isDense = isDense;

        BlockState def = this.stateDefinition.any().setValue(GolemProperties.ACTIVE, false);
        for (EnumProperty<Link> prop : LINKS.values()) {
            def = def.setValue(prop, Link.NONE);
        }
        this.registerDefaultState(def);
    }

    /** What this pipe has in direction dir. */
    public static Link link(BlockState state, Direction dir) {
        return state.getValue(LINKS.get(dir));
    }

    /** Whether there is a connection in direction dir (to a pipe or a device). */
    public static boolean linked(BlockState state, Direction dir) {
        return link(state, dir) != Link.NONE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public int getMaxChannels() {
        return maxChannels;
    }

    @Override
    public int channelCapacity() {
        return maxChannels;
    }

    public boolean isDense() {
        return isDense;
    }

    /**
     * The shape depends only on which sides have a connection: 64 combinations computed in advance. It cannot be built from
     * boxes on every call: when joining a server the client rebuilds the cache of all 8192 pipe states, and building the
     * shapes took longer than the server waits for the client (joining timed out).
     */
    private static final VoxelShape[] SHAPES = buildShapes(CORE_SHAPE, ARM_SHAPES);
    private static final VoxelShape[] DENSE_SHAPES = buildShapes(DENSE_CORE_SHAPE, DENSE_ARM_SHAPES);
    /** Collisions use only the lower half: the pipe works as a step (it can be walked onto without jumping). */
    private static final VoxelShape[] STEP_SHAPES = stepShapes(SHAPES);
    private static final VoxelShape[] DENSE_STEP_SHAPES = stepShapes(DENSE_SHAPES);

    private static VoxelShape[] stepShapes(VoxelShape[] shapes) {
        VoxelShape[] out = new VoxelShape[shapes.length];
        for (int i = 0; i < shapes.length; i++) {
            out[i] = BusLinks.step(shapes[i]);
        }
        return out;
    }

    private static VoxelShape[] buildShapes(VoxelShape core, Map<Direction, VoxelShape> arms) {
        VoxelShape[] out = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (Direction dir : Direction.values()) {
                if ((mask & (1 << dir.ordinal())) != 0) {
                    shape = Shapes.or(shape, arms.get(dir));
                }
            }
            out[mask] = shape.optimize();
        }
        return out;
    }

    private static int mask(BlockState state) {
        int mask = 0;
        for (Direction dir : Direction.values()) {
            if (linked(state, dir)) {
                mask |= 1 << dir.ordinal();
            }
        }
        return mask;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (isDense ? DENSE_SHAPES : SHAPES)[mask(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (isDense ? DENSE_STEP_SHAPES : STEP_SHAPES)[mask(state)];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();
        for (Direction dir : Direction.values()) {
            state = state.setValue(LINKS.get(dir), linkTo(level, pos, pos.relative(dir), dir));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
        return state.setValue(LINKS.get(direction), linkTo(level, pos, neighborPos, direction));
    }

    /** Recompute all sides (after a plug is added or removed). */
    public BlockState relink(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            state = state.setValue(LINKS.get(dir), linkTo(level, pos, pos.relative(dir), dir));
        }
        return state;
    }

    /** Plugs drop together with the pipe. */
    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof PipeBlockEntity be && be.plugCount() > 0) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new net.minecraft.world.item.ItemStack(com.golemlogix.init.ModItems.PIPE_PLUG.get(), be.plugCount()));
        }
    }

    private Link linkTo(BlockGetter level, BlockPos pos, BlockPos neighborPos, Direction dir) {
        // a side closed by a plug (on this pipe or the neighbouring one) connects to nothing
        if (PipeBlockEntity.plugged(level, pos, dir) || PipeBlockEntity.plugged(level, neighborPos, dir.getOpposite())) {
            return Link.NONE;
        }
        BlockState neighborState = level.getBlockState(neighborPos);
        // the Eternal Resonator is not a network node (it powers Chronometers), but a pipe reaches it and looks connected
        if (neighborState.getBlock() instanceof EternalFrostResonatorBlock) {
            return Link.DEVICE;
        }
        if (neighborState.getBlock() instanceof SnowPipeBlock other) {
            // pipes of different thickness: the thin one enters the adapter washer on the dense one's end with its cuff, without a gap
            if (other.isDense == this.isDense) {
                return Link.PIPE;
            }
            return this.isDense ? Link.REDUCER : Link.DEVICE;
        }
        return neighborState.getBlock() instanceof GolemNode n && n.canLink(neighborState, this.defaultBlockState(), dir.getOpposite())
                ? Link.DEVICE : Link.NONE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, GolemProperties.ACTIVE);
    }
}
