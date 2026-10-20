package com.golemlogix.blocks;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.CardCatalogMenu;
import com.golemlogix.system.CatalogStructure;
import com.golemlogix.system.GolemNode;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Punch Card Catalog, the owner of a multiblock (see {@link CatalogStructure}): stores hundreds of punch cards with crafting-table
 * recipes, so dozens of providers are not needed for them. Capacity is set by ordinary Pattern Providers put into its slot
 * (each gives 27 places). The whole cabinet is one device on one channel.
 */
public class CardCatalogBlock extends HorizontalDirectionalBlock implements com.golemlogix.blocks.RemovalAware, EntityBlock, GolemNode {
    public static final MapCodec<CardCatalogBlock> CODEC = simpleCodec(CardCatalogBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CardCatalogBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(4.0f, 8.0f).requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(state -> state.getValue(FORMED) ? 7 : 2));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardCatalogBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CARD_CATALOG.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, p, st, be) -> CardCatalogBlockEntity.clientTick(lvl, p, st, (CardCatalogBlockEntity) be);
        }
        return (lvl, p, st, be) -> CardCatalogBlockEntity.serverTick(lvl, p, st, (CardCatalogBlockEntity) be);
    }

    /** Block events (the window was opened, the drawers slide out) go to the block entity, otherwise the server would not send them to clients. */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction facing = state.getValue(FACING);
        if (!state.getValue(FORMED)) {
            List<CatalogStructure.Missing> missing = CatalogStructure.check(level, pos, facing);
            if (missing.isEmpty()) {
                CatalogStructure.form(level, pos, facing);
                player.sendOverlayMessage(Component.translatable("message.golem_logix.catalog.formed"));
            } else {
                CatalogStructure.Missing m = missing.get(0);
                player.sendSystemMessage(Component.translatable("message.golem_logix.catalog.missing", missing.size(),
                        m.block().getName(), m.pos().getX(), m.pos().getY(), m.pos().getZ()));
            }
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof CardCatalogBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new CardCatalogMenu(id, inv, be, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.golem_logix.punch_card_catalog")));
        }
        return InteractionResult.CONSUME;
    }

    /** Replaced by another block (see RemovalAware). */
    @Override
    public void onRemoved(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            if (state.getValue(FORMED)) {
                CatalogStructure.disassemble(level, pos, state.getValue(FACING), pos);
            }
            if (level.getBlockEntity(pos) instanceof CardCatalogBlockEntity be) {
                be.dropContents();
            }
        }
    }
}
