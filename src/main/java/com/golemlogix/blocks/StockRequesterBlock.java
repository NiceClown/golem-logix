package com.golemlogix.blocks;

import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.StockRequesterMenu;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Copper Order Ledger (Storekeeper):
 * Keeps N of up to 9 items or fluids in the network by automatically placing small crafting orders.
 */
public class StockRequesterBlock extends HorizontalDirectionalBlock implements GolemNode, EntityBlock {
    public static final MapCodec<StockRequesterBlock> CODEC = simpleCodec(StockRequesterBlock::new);

    public StockRequesterBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(2.5f).requiresCorrectToolForDrops().noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StockRequesterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() && type == ModBlockEntities.STOCK_REQUESTER.get()) {
            return (lvl, p, st, be) -> StockRequesterBlockEntity.serverTick(lvl, p, st, (StockRequesterBlockEntity) be);
        }
        return null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof StockRequesterBlockEntity be) {
            sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new StockRequesterMenu(id, inv, be.getFilters(), be.menuData(), be.getBlockPos()),
                    Component.translatable("gui.golem_logix.title.stock_requester")),
                    buf -> buf.writeBlockPos(be.getBlockPos()));
        }
        return InteractionResult.CONSUME;
    }
}
