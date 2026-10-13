package com.golemlogix.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;

/** Вакуумная шина импорта: всасывает предметы и жидкости из контейнера перед собой в сеть. */
public class VacuumImportBusBlock extends AbstractBusBlock {
    public static final MapCodec<VacuumImportBusBlock> CODEC = simpleCodec(VacuumImportBusBlock::new);

    public VacuumImportBusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isExportBus() {
        return false;
    }

    @Override
    protected BusLinks.Device device() {
        return BusLinks.Device.FUNNEL;
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }
}
