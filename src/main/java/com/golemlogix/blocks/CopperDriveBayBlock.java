package com.golemlogix.blocks;

import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.DriveBayMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Drive Cabinet, a 2x2x1 multiblock (two cells wide, two high, one deep). Placed with one item, like a door:
 * facing the player, the "bottom left" part (looking at the cabinet from the front) where you clicked, the others to the right and above.
 * The block entity with the disks lives only in the "main" bottom left part; the other parts only conduct the network and forward clicks.
 */
public class CopperDriveBayBlock extends Block implements com.golemlogix.blocks.RemovalAware, EntityBlock, GolemNode {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** Cabinet part: right = how many cells to the right of the main one (as seen from the front), up = how many above. */
    public enum Part implements StringRepresentable {
        BOTTOM_LEFT("bl", 0, 0),
        BOTTOM_RIGHT("br", 1, 0),
        TOP_LEFT("tl", 0, 1),
        TOP_RIGHT("tr", 1, 1);

        private final String key;
        public final int right;
        public final int up;

        Part(String key, int right, int up) {
            this.key = key;
            this.right = right;
            this.up = up;
        }

        public static Part of(int right, int up) {
            return values()[(up << 1) | right];
        }

        /** The part mirrored horizontally (for Mirror). */
        public Part mirrored() {
            return of(1 - right, up);
        }

        @Override
        public String getSerializedName() {
            return key;
        }
    }

    public CopperDriveBayBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.5f).requiresCorrectToolForDrops().noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(state -> state.getValue(GolemProperties.ACTIVE) ? 5 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART, Part.BOTTOM_LEFT).setValue(GolemProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, GolemProperties.ACTIVE);
    }

    // ------------------------------------------------------------------ multiblock geometry

    /** Which way from the main part the "right" side of the cabinet goes (looking at the cabinet from the front). */
    public static Direction rightOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos master, Direction facing, Part part) {
        return master.relative(rightOf(facing), part.right).above(part.up);
    }

    /** Position of the main part from the position of any other part. */
    public static BlockPos masterOf(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.relative(rightOf(state.getValue(FACING)), -part.right).below(part.up);
    }

    /** Cabinet block entity from any of its four parts. */
    @Nullable
    public static CopperDriveBayBlockEntity entityOf(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(masterOf(pos, state)) instanceof CopperDriveBayBlockEntity bay ? bay : null;
    }

    /** Places the whole cabinet (for commands, tests and generators); the main part goes to master. */
    public static void place(LevelAccessor level, BlockPos master, Direction facing) {
        Block block = ModBlocks.COPPER_DRIVE_BAY.get();
        for (Part part : Part.values()) {
            level.setBlock(partPos(master, facing, part),
                    block.defaultBlockState().setValue(FACING, facing).setValue(PART, part), Block.UPDATE_CLIENTS);
        }
        if (level instanceof Level lvl) {
            for (Part part : Part.values()) {
                BlockPos p = partPos(master, facing, part);
                lvl.updateNeighborsAt(p, block);
            }
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        for (Part part : Part.values()) {
            BlockPos p = partPos(master, facing, part);
            if (!level.isInWorldBounds(p) || (part != Part.BOTTOM_LEFT && !level.getBlockState(p).canBeReplaced(context))) {
                return null;
            }
        }
        return this.defaultBlockState().setValue(FACING, facing).setValue(PART, Part.BOTTOM_LEFT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (Part part : Part.values()) {
            if (part != Part.BOTTOM_LEFT) {
                level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), 3);
            }
        }
    }

    /** A part without its edge "partner" breaks: the whole cabinet disappears. */
    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        Part part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        Direction sidePartner = part.right == 0 ? rightOf(facing) : rightOf(facing).getOpposite();
        Direction verticalPartner = part.up == 0 ? Direction.UP : Direction.DOWN;
        if (direction == sidePartner || direction == verticalPartner) {
            Part expected = direction == sidePartner ? Part.of(1 - part.right, part.up) : Part.of(part.right, 1 - part.up);
            if (!neighbor.is(this) || neighbor.getValue(PART) != expected || neighbor.getValue(FACING) != facing) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            // in creative mode the other parts are removed quietly so the item does not drop again
            BlockPos master = masterOf(pos, state);
            for (Part part : Part.values()) {
                BlockPos other = partPos(master, state.getValue(FACING), part);
                BlockState otherState = level.getBlockState(other);
                if (!other.equals(pos) && otherState.is(this)) {
                    level.setBlock(other, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, other, Block.getId(otherState));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        // a mirror swaps the cabinet's "left/right": parts trade places horizontally
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(PART, state.getValue(PART).mirrored());
    }

    // ------------------------------------------------------------------------------ network and window

    @Override
    public int channelCost() {
        return 1;
    }

    /** Only the main part takes a channel. */
    @Override
    public int channelCost(BlockState state) {
        return state.getValue(PART) == Part.BOTTOM_LEFT ? 1 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.BOTTOM_LEFT ? new CopperDriveBayBlockEntity(pos, state) : null;
    }

    /** Block events (the window was opened, the display door) go to the block entity, otherwise the server would not send them to clients. */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    /** Client: the display door opens and closes smoothly. */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide() && type == com.golemlogix.init.ModBlockEntities.COPPER_DRIVE_BAY.get()
                ? (lvl, p, st, be) -> CopperDriveBayBlockEntity.clientTick(lvl, p, st, (CopperDriveBayBlockEntity) be)
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        CopperDriveBayBlockEntity bay = entityOf(level, pos, state);
        if (bay != null) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new DriveBayMenu(id, inv, bay.getContainer(), bay.priorityData()),
                    Component.translatable("block.golem_logix.copper_drive_bay")));
        }
        return InteractionResult.CONSUME;
    }

    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CopperDriveBayBlockEntity bay) {
            for (ItemStack disk : bay.getDisks()) {
                if (!disk.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disk.copy());
                }
            }
        }
    }
}
