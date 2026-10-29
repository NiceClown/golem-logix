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
        // game tests: the gametest package is not in the release jar, so it is reached by name and only when tests run
        if (net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()) {
            try {
                Class.forName("com.golemlogix.gametest.GolemGameTestRegistry").getMethod("register", IEventBus.class).invoke(null, modEventBus);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Golem Logix game tests are not available", e);
            }
        }
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

        /**
         * A Security Terminal guards every block of its network in one place: a right-click on a network block (its window, a
         * disk put into a drive bay, a card into a gateway...) needs the BUILD right; a terminal any of INSERT, EXTRACT or CRAFT
         * (its window then checks each action). The Security Terminal itself checks its owner.
         */
        @SubscribeEvent
        public static void onUseBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
                return;
            }
            net.minecraft.core.BlockPos pos = event.getPos();
            net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
            if (!(block instanceof com.golemlogix.system.GolemNode) || block instanceof com.golemlogix.blocks.SecurityTerminalBlock) {
                return;
            }
            GolemNetwork gnet = GolemNetwork.get(level, pos);
            if (!gnet.hasSecurityTerminal()) {
                return;
            }
            var player = event.getEntity();
            boolean allowed = block instanceof com.golemlogix.blocks.AnalogTerminalBlock || block instanceof com.golemlogix.blocks.CraftingTerminalBlock
                    ? gnet.hasPermission(player, com.golemlogix.security.SecurityPermission.INSERT) || gnet.hasPermission(player, com.golemlogix.security.SecurityPermission.EXTRACT)
                    || gnet.hasPermission(player, com.golemlogix.security.SecurityPermission.CRAFT)
                    : gnet.hasPermission(player, com.golemlogix.security.SecurityPermission.BUILD);
            if (!allowed) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.golem_logix.security_denied"));
                }
            }
        }

        @SubscribeEvent
        public static void onBlockBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
            if (event.getLevel() instanceof Level level && !level.isClientSide()) {
                net.minecraft.core.BlockPos pos = event.getPos();
                net.minecraft.world.level.block.state.BlockState state = event.getState();
                if (state.getBlock() instanceof com.golemlogix.system.GolemNode) {
                    GolemNetwork gnet = GolemNetwork.get(level, pos);
                    if (gnet.hasSecurityTerminal() && !gnet.hasPermission(event.getPlayer(), com.golemlogix.security.SecurityPermission.BUILD)) {
                        event.setCanceled(true);
                        if (event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp) {
                            sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.golem_logix.security_denied"));
                        }
                    }
                }
            }
        }
    }
}
