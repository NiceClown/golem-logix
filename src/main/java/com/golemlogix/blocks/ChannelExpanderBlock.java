package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Расширитель Каналов: вставляется в боковые ячейки двух средних слоёв Решётки Атланта и увеличивает ёмкость каждой линии
 * Кабельных Муфт. Его наружная грань — ещё один выход сети: к ней подключают трубу, и она пропускает 64 канала (квантовый —
 * 128), чтобы не перестраивать базу под каждую новую линию. Внутрь решётки он связан с шиной-проводником.
 */
public class ChannelExpanderBlock extends Block implements AtlasPart, GolemNode {
    private final boolean quantum;

    public ChannelExpanderBlock(boolean quantum, Properties properties) {
        super(properties.sound(SoundType.AMETHYST).strength(5.0f, 8.0f).lightLevel(s -> quantum ? 12 : 9).requiresCorrectToolForDrops());
        this.quantum = quantum;
    }

    public boolean isQuantum() {
        return quantum;
    }

    /** Внутри решётки — только со своими частями, снаружи — с любыми узлами сети (трубы, шины, устройства). */
    @Override
    public boolean canLink(BlockState self, BlockState other, Direction toOther) {
        return other.getBlock() instanceof GolemNode;
    }
}
