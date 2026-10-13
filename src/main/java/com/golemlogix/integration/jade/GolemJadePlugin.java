package com.golemlogix.integration.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade tooltips (an optional dependency). The class is loaded only when Jade is installed.
 */
@WailaPlugin
public class GolemJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        // channel numbers come from the server's network, as AE2 sends its grid state (also for blocks without a block entity)
        registration.registerBlockDataProvider(ChannelProvider.INSTANCE, net.minecraft.world.level.block.Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CoreProvider.INSTANCE, com.golemlogix.blocks.AtlasCoreBlock.class);
        registration.registerBlockComponent(SaturatorProvider.INSTANCE, com.golemlogix.blocks.ManaSaturatorBlock.class);
        registration.registerBlockComponent(SaturatorProvider.INSTANCE, com.golemlogix.blocks.SaturatorPartBlock.class);
        registration.registerBlockComponent(MainframeProvider.INSTANCE, com.golemlogix.blocks.CryoMainframeBlock.class);
        registration.registerBlockComponent(MainframeProvider.INSTANCE, com.golemlogix.blocks.MainframePartBlock.class);
        registration.registerBlockComponent(CatalogProvider.INSTANCE, com.golemlogix.blocks.CardCatalogBlock.class);
        registration.registerBlockComponent(CatalogProvider.INSTANCE, com.golemlogix.blocks.CatalogPartBlock.class);
        // channels on all network nodes (devices, pipes, bushings, expanders): the provider picks the mod's blocks itself
        registration.registerBlockComponent(ChannelProvider.INSTANCE, net.minecraft.world.level.block.Block.class);
    }
}
