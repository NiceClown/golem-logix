package com.golemlogix.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.DirectionalBlock;

/** Injector Export Bus: pushes what is set in the filter from the network into the container in front of it. */
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
