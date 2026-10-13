package com.golemlogix.blocks;

import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.MainframeMenu;
import com.golemlogix.system.GolemNode;
import com.golemlogix.system.MainframeStructure;
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
 * Крио-Мейнфрейм — хозяин многоблочного шкафа (см. {@link MainframeStructure}). Внутрь кладутся Крио-Калькуляторы
 * (каждый даёт несколько независимых ядер) и Сжатые Сопроцессоры (каждый считается за несколько и ускоряет все ядра).
 * Весь шкаф занимает в сети один канал. Собранный рисуется целиком отрисовщиком: стеклянные двери открываются, пока
 * кто-то смотрит в его окно.
 */
public class CryoMainframeBlock extends HorizontalDirectionalBlock implements EntityBlock, GolemNode {
    public static final MapCodec<CryoMainframeBlock> CODEC = simpleCodec(CryoMainframeBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CryoMainframeBlock(Properties properties) {
        super(properties.sound(SoundType.COPPER).strength(5.0f, 9.0f).requiresCorrectToolForDrops().noOcclusion()
                .lightLevel(state -> state.getValue(FORMED) ? 8 : 3));
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
        // собранный шкаф рисует отрисовщик целиком
        return state.getValue(FORMED) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CryoMainframeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CRYO_MAINFRAME.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, p, st, be) -> CryoMainframeBlockEntity.clientTick(lvl, p, st, (CryoMainframeBlockEntity) be);
        }
        return (lvl, p, st, be) -> CryoMainframeBlockEntity.serverTick(lvl, p, st, (CryoMainframeBlockEntity) be);
    }

    /** События блока (окно открыли или закрыли — двери) передаются блок-сущности, иначе сервер не разошлёт их клиентам. */
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
            List<MainframeStructure.Missing> missing = MainframeStructure.check(level, pos, facing);
            if (missing.isEmpty()) {
                MainframeStructure.form(level, pos, facing);
                player.displayClientMessage(Component.translatable("message.golem_logix.mainframe.formed"), true);
            } else {
                MainframeStructure.Missing m = missing.get(0);
                player.displayClientMessage(Component.translatable("message.golem_logix.mainframe.missing", missing.size(),
                        m.block().getName(), m.pos().getX(), m.pos().getY(), m.pos().getZ()), false);
            }
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof CryoMainframeBlockEntity be) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new MainframeMenu(id, inv, be, be.getCalculators(), be.getUpgrades(), be.menuData(),
                            ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.golem_logix.cryo_mainframe")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof CryoMainframeBlockEntity be) {
                    be.dropContents();
                }
                if (state.getValue(FORMED)) {
                    MainframeStructure.disassemble(level, pos, state.getValue(FACING), pos);
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
