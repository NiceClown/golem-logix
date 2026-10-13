package com.golemlogix.blocks;

import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Monocle periscope: connects the container in front of it (a chest, a fluid tank) as network storage.
 * Right-click opens the priority window.
 */
public class PeriscopeStorageBusBlock extends DirectionalBlock implements GolemNode, EntityBlock {
    public static final MapCodec<PeriscopeStorageBusBlock> CODEC = simpleCodec(PeriscopeStorageBusBlock::new);

    public PeriscopeStorageBusBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(2.5f).noOcclusion().requiresCorrectToolForDrops());
        this.registerDefaultState(BusLinks.withNoLinks(this.stateDefinition.any().setValue(FACING, Direction.NORTH)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BusLinks.shape(state, BusLinks.Device.LENS);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BusLinks.collision(state, BusLinks.Device.LENS);
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

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PeriscopeBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof PeriscopeBlockEntity be) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new BusMenu(id, inv, new SimpleContainer(0) {
                @Override
                public boolean stillValid(Player pl) {
                    return Container.stillValidBlockEntity(be, pl);
                }
            }, be.getUpgrades(), BusMenu.KIND_PERISCOPE, be.menuData()), net.minecraft.network.chat.Component.translatable("gui.golem_logix.title.periscope")), buf -> buf.writeVarInt(BusMenu.KIND_PERISCOPE));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PeriscopeBlockEntity be) {
            net.minecraft.world.Containers.dropContents(level, pos, be.getUpgrades());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** A redstone signal nearby may have switched the storage on or off: the network is rescanned without the cache. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PeriscopeBlockEntity be
                && be.getUpgrades().has(com.golemlogix.upgrade.UpgradeType.REDSTONE)) {
            com.golemlogix.system.GolemNetwork.clearCache();
        }
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