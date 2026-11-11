package com.golemlogix.blocks;

import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.StockManometerMenu;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Brass Stock Manometer (Warden):
 * Level emitter device (1 channel). Emits redstone signal based on network stock (ABOVE / BELOW / ANALOG),
 * with optional hysteresis thresholds or Autocraft Module running detection.
 */
public class StockManometerBlock extends DirectionalBlock implements GolemNode, EntityBlock {

    /** What takes a redstone signal from a neighbour; a pack can add its own blocks. */
    public static final net.minecraft.tags.TagKey<Block> RECEIVERS = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "redstone_receivers"));

    /**
     * Whether the block on that side takes this one's signal (a bridge runs to it). The game has no way to ask a block that, so
     * this goes by two signs: the tag, where a repeater only counts at its back and a comparator anywhere but its output; or the
     * question redstone dust asks a neighbour - a block that says yes without being a signal source itself has chosen to
     * connect, which modded machines do. What neither finds, the player can switch on in the window.
     */
    public static boolean isReceiver(BlockGetter level, BlockPos pos, Direction side) {
        BlockState there = level.getBlockState(pos.relative(side));
        if (!there.is(RECEIVERS)) {
            return !there.isSignalSource() && there.canRedstoneConnectTo(level, pos.relative(side), side);
        }
        if (there.getBlock() instanceof net.minecraft.world.level.block.RepeaterBlock) {
            return there.getValue(net.minecraft.world.level.block.DiodeBlock.FACING) == side.getOpposite();
        }
        if (there.getBlock() instanceof net.minecraft.world.level.block.ComparatorBlock) {
            return there.getValue(net.minecraft.world.level.block.DiodeBlock.FACING) != side;
        }
        return true;
    }

    /** The clock head out front, the flange on the block behind; not a full cube, so the set-back parts of the model are lit. */
    private static final java.util.Map<Direction, net.minecraft.world.phys.shapes.VoxelShape> SHAPES = java.util.Map.of(
            Direction.NORTH, Block.box(1, 1, 4.5, 15, 16, 16), Direction.SOUTH, Block.box(1, 1, 0, 15, 16, 11.5),
            Direction.WEST, Block.box(4.5, 1, 1, 16, 16, 15), Direction.EAST, Block.box(0, 1, 1, 11.5, 16, 15),
            Direction.UP, Block.box(1, 0, 0, 15, 11.5, 16), Direction.DOWN, Block.box(1, 4.5, 0, 15, 16, 16));

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
    public static final MapCodec<StockManometerBlock> CODEC = simpleCodec(StockManometerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public StockManometerBlock(Properties properties) {
        super(properties.sound(SoundType.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof StockManometerBlockEntity be) {
            return be.getSignal();
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // Direct signal into the block it is attached to (behind it)
        if (direction == state.getValue(FACING).getOpposite()) {
            if (level.getBlockEntity(pos) instanceof StockManometerBlockEntity be) {
                return be.getSignal();
            }
        }
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StockManometerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.STOCK_MANOMETER.get()) {
            return (lvl, p, st, be) -> StockManometerBlockEntity.serverTick(lvl, p, st, (StockManometerBlockEntity) be);
        }
        return null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Dial faces the direction player placed against
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StockManometerBlockEntity be) {
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new StockManometerMenu(id, inv, be.getFilter(), be.getUpgrades(), be.menuData(), be.getBlockPos()),
                    Component.translatable("gui.golem_logix.title.stock_manometer")),
                    buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof StockManometerBlockEntity be) {
                Containers.dropContents(level, pos, be.getUpgrades());
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
