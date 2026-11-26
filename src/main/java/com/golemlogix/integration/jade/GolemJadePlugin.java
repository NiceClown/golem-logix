package com.golemlogix.integration.jade;

import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.blocks.CatalogPartBlock;
import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.blocks.MainframePartBlock;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import net.minecraft.world.level.block.Block;
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
        registration.registerBlockDataProvider(ChannelProvider.INSTANCE, Block.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CoreProvider.INSTANCE, AtlasCoreBlock.class);
        registration.registerBlockComponent(SaturatorProvider.INSTANCE, ManaSaturatorBlock.class);
        registration.registerBlockComponent(SaturatorProvider.INSTANCE, SaturatorPartBlock.class);
        registration.registerBlockComponent(MainframeProvider.INSTANCE, CryoMainframeBlock.class);
        registration.registerBlockComponent(MainframeProvider.INSTANCE, MainframePartBlock.class);
        registration.registerBlockComponent(CatalogProvider.INSTANCE, CardCatalogBlock.class);
        registration.registerBlockComponent(CatalogProvider.INSTANCE, CatalogPartBlock.class);
        // channels on all network nodes (devices, pipes, bushings, expanders): the provider picks the mod's blocks itself
        registration.registerBlockComponent(ChannelProvider.INSTANCE, Block.class);
    }
}
