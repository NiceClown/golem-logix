package com.golemlogix.blocks;

import com.golemlogix.blockentity.CoolingCoprocessorBlockEntity;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Сопроцессор Охлаждения: каналов не занимает, ускоряет примыкающий Крио-Калькулятор.
 * Является узлом сети, чтобы трубы к нему тянулись, но канал не расходует. Рёбра радиатора рисует отрисовщик (анимация).
 */
public class CoolingCoprocessorBlock extends Block implements GolemNode, EntityBlock {
    public CoolingCoprocessorBlock(Properties properties) {
        super(properties.sound(SoundType.AMETHYST).strength(3.0f).lightLevel(state -> 3).requiresCorrectToolForDrops().noOcclusion());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoolingCoprocessorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() && type == ModBlockEntities.COOLING_COPROCESSOR.get()) {
            return (lvl, p, st, be) -> CoolingCoprocessorBlockEntity.clientTick(lvl, p, st, (CoolingCoprocessorBlockEntity) be);
        }
        return null;
    }
}
