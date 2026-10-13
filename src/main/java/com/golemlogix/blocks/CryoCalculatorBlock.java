package com.golemlogix.blocks;

import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.menu.CalculatorMenu;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Крио-Калькулятор. Свойство ACTIVE включает «матричный дождь» на боковых панелях, пока идёт задача.
 */
public class CryoCalculatorBlock extends Block implements GolemNode, EntityBlock {
    public CryoCalculatorBlock(Properties properties) {
        super(properties.sound(SoundType.AMETHYST).strength(3.5f).lightLevel(state -> 4).requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any().setValue(GolemProperties.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GolemProperties.ACTIVE);
    }

    @Override
    public int channelCost() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CryoCalculatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.CRYO_CALCULATOR.get()) {
            return null;
        }
        if (level.isClientSide()) {
            return (lvl, p, st, be) -> CryoCalculatorBlockEntity.clientTick(lvl, p, st, (CryoCalculatorBlockEntity) be);
        }
        return (lvl, p, st, be) -> CryoCalculatorBlockEntity.serverTick(lvl, p, st, (CryoCalculatorBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CryoCalculatorBlockEntity cpu) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new CalculatorMenu(id, inv, cpu, cpu.getUpgrades(), cpu.menuData(), ContainerLevelAccess.create(level, pos)),
                    Component.translatable("block.golem_logix.cryo_calculator")));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CryoCalculatorBlockEntity cpu) {
                cpu.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
