package com.golemlogix.blocks;

import com.golemlogix.blockentity.SchematicConcentratorBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.ConcentratorMenu;
import com.golemlogix.system.GolemNetwork;
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
 * Крио-концентратор схем: в одном окне — перфокарты всех Поставщиков Шаблонов сети (и Картотек), по категориям: верстачные
 * вместе, остальные — по машине, которую кормит поставщик. Как менеджер автокрафта RS или терминал доступа к шаблонам AE2.
 */
public class SchematicConcentratorBlock extends HorizontalDirectionalBlock implements EntityBlock, GolemNode {
    public static final MapCodec<SchematicConcentratorBlock> CODEC = simpleCodec(SchematicConcentratorBlock::new);

    public SchematicConcentratorBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(3.0f, 6.0f).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 6));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SchematicConcentratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.SCHEMATIC_CONCENTRATOR.get()) {
            return null;
        }
        return (lvl, p, st, be) -> SchematicConcentratorBlockEntity.clientTick(lvl, p, st, (SchematicConcentratorBlockEntity) be);
    }

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
        if (player instanceof ServerPlayer sp) {
            String err = GolemNetwork.scan(level, pos).accessError(pos);
            if (err != null) {
                sp.displayClientMessage(Component.translatable(err), true);
                return InteractionResult.CONSUME;
            }
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ConcentratorMenu(id, inv, level, pos),
                    Component.translatable("block.golem_logix.schematic_concentrator")));
        }
        return InteractionResult.CONSUME;
    }
}
