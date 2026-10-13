package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность терминалов: данных не хранит, нужна только для анимации экрана на клиенте
 * (включение сети -> голем машет рукой во весь экран, дальше голем таскает ящики).
 */
public class TerminalBlockEntity extends BlockEntity {
    /** Клиентское состояние анимации (на сервере не используется). */
    public boolean animInitialized;
    public boolean lastActive;
    /** Игровое время (в тиках с долями) начала приветствия. */
    public float waveStart = -1000f;

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL.get(), pos, state);
    }
}
