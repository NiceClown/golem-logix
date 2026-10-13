package com.golemlogix.integration.jade;

import com.golemlogix.blocks.CableBushingBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Подсказки Jade (необязательная зависимость): сколько каналов занято на линии Кабельной Муфты.
 * Класс подгружается только когда установлен Jade.
 */
@WailaPlugin
public class GolemJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BushingProvider.INSTANCE, CableBushingBlock.class);
        registration.registerBlockComponent(CoreProvider.INSTANCE, com.golemlogix.blocks.AtlasCoreBlock.class);
        registration.registerBlockComponent(SaturatorProvider.INSTANCE, com.golemlogix.blocks.ManaSaturatorBlock.class);
        registration.registerBlockComponent(SaturatorProvider.INSTANCE, com.golemlogix.blocks.SaturatorPartBlock.class);
        registration.registerBlockComponent(MainframeProvider.INSTANCE, com.golemlogix.blocks.CryoMainframeBlock.class);
        registration.registerBlockComponent(MainframeProvider.INSTANCE, com.golemlogix.blocks.MainframePartBlock.class);
        registration.registerBlockComponent(CatalogProvider.INSTANCE, com.golemlogix.blocks.CardCatalogBlock.class);
        registration.registerBlockComponent(CatalogProvider.INSTANCE, com.golemlogix.blocks.CatalogPartBlock.class);
        // каналы у всех остальных узлов сети (устройства, трубы, шины, расширители): провайдер сам отбирает блоки мода
        registration.registerBlockComponent(ChannelProvider.INSTANCE, net.minecraft.world.level.block.Block.class);
    }
}
