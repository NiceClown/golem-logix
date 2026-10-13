package com.golemlogix;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.*;
import com.golemlogix.system.GolemNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(GolemLogixMod.MOD_ID)
public class GolemLogixMod {
    public static final String MOD_ID = "golem_logix";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GolemLogixMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Awakening Golem Logix — Cold, Occult & Analog Logistics...");

        // Register Deferred Registers
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, GolemConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.golemlogix.config.GolemClientConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Golem Logix common setup initialized successfully.");
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static class GameEvents {
        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            GolemNetwork.clearCache();
        }
    }
}
