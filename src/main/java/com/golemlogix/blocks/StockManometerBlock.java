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
public class StockManometerBlock extends DirectionalBlock implements GolemNode, EntityBlock, RemovalAware {
    public static final MapCodec<StockManometerBlock> CODEC = simpleCodec(StockManometerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public StockManometerBlock(Properties properties) {
        super(properties.sound(SoundType.METAL).strength(3.0f).requiresCorrectToolForDrops());
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
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof StockManometerBlockEntity be) {
            Containers.dropContents(level, pos, be.getUpgrades());
        }
    }
}
