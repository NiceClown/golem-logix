package com.golemlogix;

import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blocks.AnalogTerminalBlock;
import com.golemlogix.blocks.CraftingTerminalBlock;
import com.golemlogix.blocks.CryoDiodeBlock;
import com.golemlogix.blocks.RecipeMonitorBlock;
import com.golemlogix.blocks.SecurityTerminalBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.*;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.AtlasPart;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

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
        /** /golemlogix_buses: what the buses cost the server over the last few seconds. */
        @SubscribeEvent
        public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
            event.getDispatcher().register(net.minecraft.commands.Commands.literal("golemlogix_buses")
                    .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.translatable("message.golem_logix.bus_load",
                                com.golemlogix.system.BusLoad.opsPerSecond(),
                                String.format(java.util.Locale.ROOT, "%.2f", com.golemlogix.system.BusLoad.millisPerTick()),
                                String.format(java.util.Locale.ROOT, "%.1f", com.golemlogix.system.BusLoad.percentOfTick()),
                                String.format(java.util.Locale.ROOT, "%.2f", com.golemlogix.system.BusLoad.worstMillis()),
                                com.golemlogix.config.GolemConfig.BUS_MAX_MILLIS_PER_TICK.get(),
                                com.golemlogix.system.BusLoad.deferredPerSecond()), false);
                        return 1;
                    }));
        }

        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            GolemNetwork.forgetAll();
            com.golemlogix.system.TelegraphLinks.clear();
        }

        /** After /reload the golem_logix:not_gear tag may have changed: keep disk used-space counters in step with it. */
        @SubscribeEvent
        public static void onTagsReloaded(net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad event) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.overworld() != null) {
                com.golemlogix.system.DiskStore.get(server).recount();
            }
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
        public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
                return;
            }
            BlockPos pos = event.getPos();
            Block block = level.getBlockState(pos).getBlock();
            if (!(block instanceof GolemNode) || block instanceof SecurityTerminalBlock) {
                return;
            }
            GolemNetwork gnet = GolemNetwork.get(level, pos);
            if (!gnet.hasSecurityTerminal()) {
                return;
            }
            Player player = event.getEntity();
            boolean allowed;
            if (block instanceof AnalogTerminalBlock || block instanceof CraftingTerminalBlock) {
                allowed = gnet.hasPermission(player, SecurityPermission.INSERT)
                        || gnet.hasPermission(player, SecurityPermission.EXTRACT)
                        || gnet.hasPermission(player, SecurityPermission.CRAFT);
            } else if (block instanceof RecipeMonitorBlock) {
                allowed = gnet.hasPermission(player, SecurityPermission.CRAFT)
                        || gnet.hasPermission(player, SecurityPermission.BUILD);
            } else {
                allowed = gnet.hasPermission(player, SecurityPermission.BUILD);
            }
            if (!allowed) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                denySecurity(player);
            }
        }

        @SubscribeEvent
        public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide()
                    || !(event.getEntity() instanceof Player player)) {
                return;
            }
            BlockState placedState = event.getPlacedBlock();
            Block placedBlock = placedState.getBlock();
            if (!(placedBlock instanceof GolemNode) && !(placedBlock instanceof AtlasPart)) {
                return;
            }
            BlockPos pos = event.getPos();
            Set<GolemNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
            if (placedBlock instanceof GolemNode) {
                for (Direction dir : Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (!level.isLoaded(next)) {
                        continue;
                    }
                    BlockState nextState = level.getBlockState(next);
                    if (nextState.getBlock() instanceof GolemNode
                            && (GolemNode.linked(level, pos, placedState, next, nextState, dir)
                            || placedBlock instanceof CryoDiodeBlock)) {
                        networks.add(GolemNetwork.get(level, next));
                    }
                }
            }
            if (placedBlock instanceof AtlasPart) {
                collectAtlasPartNetworks(level, pos, networks);
            }
            boolean placingSecurity = placedBlock instanceof SecurityTerminalBlock;
            for (GolemNetwork gnet : networks) {
                if (placingSecurity) {
                    for (SecurityTerminalBlockEntity sec : gnet.securityTerminals()) {
                        if (!sec.isRemoved()
                                && !(sec.getLevel() == level && sec.getBlockPos().equals(pos))
                                && !sec.isOwner(player) && !player.canUseGameMasterBlocks()) {
                            event.setCanceled(true);
                            denySecurity(player);
                            return;
                        }
                    }
                } else if (gnet.hasSecurityTerminal() && !gnet.hasPermission(player, SecurityPermission.BUILD)) {
                    event.setCanceled(true);
                    denySecurity(player);
                    return;
                }
            }
        }

        @SubscribeEvent
        public static void onBlockBreak(BreakBlockEvent event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
                return;
            }
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }
            BlockPos pos = event.getPos();
            Block block = event.getState().getBlock();
            if (block instanceof SecurityTerminalBlock) {
                if (level.getBlockEntity(pos) instanceof SecurityTerminalBlockEntity sec
                        && sec.getOwnerId() != null && !sec.isOwner(player) && !player.canUseGameMasterBlocks()) {
                    event.setCanceled(true);
                    denySecurity(player);
                    return;
                }
                GolemNetwork gnet = GolemNetwork.get(level, pos);
                for (SecurityTerminalBlockEntity other : gnet.securityTerminals()) {
                    if (!other.isRemoved()
                            && !(other.getLevel() == level && other.getBlockPos().equals(pos))
                            && !other.hasPermission(player, SecurityPermission.BUILD)) {
                        event.setCanceled(true);
                        denySecurity(player);
                        return;
                    }
                }
                return;
            }
            if (block instanceof GolemNode) {
                GolemNetwork gnet = GolemNetwork.get(level, pos);
                if (gnet.hasSecurityTerminal() && !gnet.hasPermission(player, SecurityPermission.BUILD)) {
                    event.setCanceled(true);
                    denySecurity(player);
                    return;
                }
            }
            if (block instanceof AtlasPart) {
                Set<GolemNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
                collectAtlasPartNetworks(level, pos, networks);
                for (GolemNetwork gnet : networks) {
                    if (gnet.hasSecurityTerminal() && !gnet.hasPermission(player, SecurityPermission.BUILD)) {
                        event.setCanceled(true);
                        denySecurity(player);
                        return;
                    }
                }
            }
        }

        private static void collectAtlasPartNetworks(Level level, BlockPos start, Set<GolemNetwork> out) {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> visited = new HashSet<>();
            queue.add(start);
            visited.add(start);
            while (!queue.isEmpty()) {
                BlockPos cur = queue.poll();
                if (!cur.equals(start) && level.getBlockState(cur).getBlock() instanceof GolemNode) {
                    out.add(GolemNetwork.get(level, cur));
                }
                for (Direction dir : Direction.values()) {
                    BlockPos next = cur.relative(dir);
                    if (visited.size() > 36 || visited.contains(next) || !level.isLoaded(next)) {
                        continue;
                    }
                    if (level.getBlockState(next).getBlock() instanceof AtlasPart) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        private static void denySecurity(Player player) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
            }
        }
    }
}
