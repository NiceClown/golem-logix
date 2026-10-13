package com.golemlogix.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;

/** Инжекторная шина экспорта: выталкивает из сети в контейнер перед собой то, что записано в фильтре. */
public class InjectorExportBusBlock extends AbstractBusBlock {
    public static final MapCodec<InjectorExportBusBlock> CODEC = simpleCodec(InjectorExportBusBlock::new);

    public InjectorExportBusBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isExportBus() {
        return true;
    }

    @Override
    protected BusLinks.Device device() {
        return BusLinks.Device.NOZZLE;
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }
}
