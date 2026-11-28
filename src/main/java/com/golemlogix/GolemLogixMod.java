package com.golemlogix;

import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blocks.AnalogTerminalBlock;
import com.golemlogix.blocks.CraftingTerminalBlock;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashSet;
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
                    .requires(src -> src.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
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
        public static void onTagsReloaded(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
            if (event.getUpdateCause() == net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
                var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null && server.overworld() != null) {
                    com.golemlogix.system.DiskStore.get(server).recount();
                }
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
         * (its window then checks each action); a Recipe Monitor CRAFT or BUILD. The Security Terminal itself checks its owner.
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
                notifySecurityDenied(player);
            }
        }

        @SubscribeEvent
        public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
            if (!(event.getLevel() instanceof Level level) || level.isClientSide()
                    || !(event.getEntity() instanceof Player player)) {
                return;
            }
            Block placed = event.getPlacedBlock().getBlock();
            boolean isNode = placed instanceof GolemNode;
            boolean isAtlas = placed instanceof AtlasPart;
            if (!isNode && !isAtlas) {
                return;
            }
            BlockPos pos = event.getPos();
            boolean isSecurityTerminal = placed instanceof SecurityTerminalBlock;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (!level.isLoaded(neighbor)) {
                    continue;
                }
                Block nb = level.getBlockState(neighbor).getBlock();
                if (nb instanceof GolemNode) {
                    if (placementDenied(GolemNetwork.get(level, neighbor), pos, player, isSecurityTerminal)) {
                        event.setCanceled(true);
                        GolemNetwork.clearCache();
                        notifySecurityDenied(player);
                        return;
                    }
                } else if (isAtlas && nb instanceof AtlasPart
                        && atlasSecurityDenied(level, neighbor, player, pos, isSecurityTerminal)) {
                    event.setCanceled(true);
                    GolemNetwork.clearCache();
                    notifySecurityDenied(player);
                    return;
                }
            }
        }

        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent event) {
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
                        && !canManageTerminal(sec, player)) {
                    event.setCanceled(true);
                    notifySecurityDenied(player);
                }
                return;
            }
            if (block instanceof GolemNode) {
                GolemNetwork gnet = GolemNetwork.get(level, pos);
                if (gnet.hasSecurityTerminal() && !gnet.hasPermission(player, SecurityPermission.BUILD)) {
                    event.setCanceled(true);
                    notifySecurityDenied(player);
                    return;
                }
            }
            if (block instanceof AtlasPart && atlasSecurityDenied(level, pos, player, null, false)) {
                event.setCanceled(true);
                notifySecurityDenied(player);
            }
        }

        private static boolean canManageTerminal(SecurityTerminalBlockEntity sec, Player player) {
            return sec.getOwnerId() == null || sec.isOwner(player) || player.canUseGameMasterBlocks();
        }

        private static boolean placementDenied(GolemNetwork gnet, BlockPos placedPos, Player player, boolean isSecurityTerminal) {
            boolean hasOtherTerminal = false;
            for (SecurityTerminalBlockEntity sec : gnet.securityTerminals()) {
                if (sec.isRemoved() || (sec.getLevel() == gnet.level() && sec.getBlockPos().equals(placedPos))) {
                    continue;
                }
                hasOtherTerminal = true;
                if (isSecurityTerminal && !canManageTerminal(sec, player)) {
                    return true;
                }
            }
            if (!hasOtherTerminal || isSecurityTerminal) {
                return false;
            }
            return !gnet.hasPermission(player, SecurityPermission.BUILD);
        }

        private static boolean atlasSecurityDenied(Level level, BlockPos start, Player player,
                                                   @Nullable BlockPos placedPos, boolean isSecurityTerminal) {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            if (placedPos != null) {
                seen.add(placedPos);
            }
            queue.add(start);
            seen.add(start);
            int visited = 0;
            while (!queue.isEmpty() && visited < 36) {
                BlockPos cur = queue.poll();
                visited++;
                for (Direction dir : Direction.values()) {
                    BlockPos next = cur.relative(dir);
                    if (!seen.add(next) || !level.isLoaded(next)) {
                        continue;
                    }
                    Block nb = level.getBlockState(next).getBlock();
                    if (nb instanceof GolemNode) {
                        GolemNetwork gnet = GolemNetwork.get(level, next);
                        boolean denied = placedPos != null
                                ? placementDenied(gnet, placedPos, player, isSecurityTerminal)
                                : gnet.hasSecurityTerminal() && !gnet.hasPermission(player, SecurityPermission.BUILD);
                        if (denied) {
                            return true;
                        }
                    }
                    if (nb instanceof AtlasPart) {
                        queue.add(next);
                    }
                }
            }
            return false;
        }

        private static void notifySecurityDenied(Player player) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.golem_logix.security_denied"), true);
            }
        }
    }
}
