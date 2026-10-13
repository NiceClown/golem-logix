package com.golemlogix.system;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Маркер: блок является частью сети Голем Логикс (соединяется с соседями вплотную).
 */
public interface GolemNode {
    /** Сколько каналов занимает устройство (0 — инфраструктура: трубы, муфты, ядро). */
    default int channelCost() {
        return 0;
    }

    /** То же с учётом состояния блока: многоблочное устройство занимает канал только одной своей частью. */
    default int channelCost(BlockState state) {
        return channelCost();
    }

    /** Максимум каналов, которые могут пройти через узел (ограничение труб и муфт). */
    default int channelCapacity() {
        return Integer.MAX_VALUE;
    }

    /** Согласен ли узел соединяться с соседним блоком (внутренние части решётки Атланта связаны только друг с другом). */
    default boolean canLink(BlockState other) {
        return true;
    }

    /** То же с учётом собственного состояния узла. */
    default boolean canLink(BlockState self, BlockState other) {
        return canLink(other);
    }

    /**
     * То же с учётом стороны: toOther — направление от этого узла к соседу. Многоблок (Насыщатель) соединяется с сетью только
     * своими разъёмами и только наружу. Сеть, трубы и шины всегда спрашивают именно этот метод.
     */
    default boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return canLink(self, other);
    }

    /** Связаны ли два соседних узла (оба согласны); toB — направление от a к b. Заглушки труб здесь не видны. */
    static boolean linked(BlockState a, BlockState b, Direction toB) {
        return a.getBlock() instanceof GolemNode na && b.getBlock() instanceof GolemNode nb
                && na.canLink(a, b, toB) && nb.canLink(b, a, toB.getOpposite());
    }

    /** То же в мире: сторона, закрытая заглушкой трубы (у любого из двух), связи не даёт. */
    static boolean linked(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos posA, BlockState a,
                          net.minecraft.core.BlockPos posB, BlockState b, Direction toB) {
        return linked(a, b, toB) && !com.golemlogix.blockentity.PipeBlockEntity.plugged(level, posA, toB)
                && !com.golemlogix.blockentity.PipeBlockEntity.plugged(level, posB, toB.getOpposite());
    }
}
