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
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.SERIALIZERS.register(modEventBus);
        ModRecipes.TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModTriggers.TRIGGERS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, GolemConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, com.golemlogix.config.GolemClientConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // the Dispel method: Ars Nouveau's spell events, if Ars Nouveau is installed
        event.enqueueWork(com.golemlogix.integration.ars.ArsCompat::init);
        LOGGER.info("Golem Logix common setup initialized successfully.");
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static class GameEvents {
        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            GolemNetwork.forgetAll();
            com.golemlogix.system.TelegraphLinks.clear();
        }

        /** A block was placed, broken or replaced: if it was a network node, the networks around it are recomputed (see GolemNetwork). */
        @SubscribeEvent
        public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
            if (event.getLevel() instanceof Level level && !level.isClientSide()) {
                GolemNetwork.onBlockChanged(level, event.getPos(), event.getState());
            }
        }

        @SubscribeEvent
        public static void onLevelTick(LevelTickEvent.Post event) {
            if (!event.getLevel().isClientSide()) {
                GolemNetwork.processChanges(event.getLevel());
            }
        }
    }
}
