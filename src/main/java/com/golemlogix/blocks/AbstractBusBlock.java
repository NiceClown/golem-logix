package com.golemlogix.blocks;

import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Common base of both buses: faces a container, takes 1 channel, keeps a filter.
 */
public abstract class AbstractBusBlock extends DirectionalBlock implements GolemNode, EntityBlock {
    protected AbstractBusBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(2.5f).noOcclusion().requiresCorrectToolForDrops());
        this.registerDefaultState(BusLinks.withNoLinks(this.stateDefinition.any().setValue(FACING, Direction.NORTH)));
    }

    protected abstract boolean isExportBus();

    /** The working node at the front (defines the block shape). */
    protected abstract BusLinks.Device device();

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BusLinks.shape(state, device());
    }

    /** A bus is a step: we collide only with its lower half, so it can be walked onto without jumping. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BusLinks.collision(state, device());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return BusLinks.update(state, direction, neighborState, level, pos);
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BusBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.BUS.get()) {
            return (lvl, p, st, be) -> BusBlockEntity.serverTick(lvl, p, st, (BusBlockEntity) be);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof BusBlockEntity bus) {
            int kind = isExportBus() ? BusMenu.KIND_EXPORT : BusMenu.KIND_IMPORT;
            sp.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new BusMenu(id, inv, bus.getFilters(), bus.getUpgrades(), kind, bus.menuData()),
                            Component.translatable(isExportBus() ? "gui.golem_logix.title.export_bus" : "gui.golem_logix.title.import_bus")),
                    buf -> buf.writeVarInt(kind));
        }
        return InteractionResult.CONSUME;
    }

    /** Upgrades drop together with the bus (the filter is a ghost, nothing to drop there). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BusBlockEntity bus) {
            net.minecraft.world.Containers.dropContents(level, pos, bus.getUpgrades());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
        return BusLinks.connected(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        BusLinks.addProperties(builder);
    }
}
