package com.golemlogix.devtest;

import com.golemlogix.GolemLogixMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * Developer rig over the network: /golemlogix_dev hook &lt;name&gt; &lt;args...&gt; runs on the server the same scene builders as
 * the single-player screenshot rig ({@link DevScenes}); replies go to the player's chat marked "[dev]", and the client rig records them.
 * Development only: the devtest package is not part of the release jar.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class DevServerCommand {
    private DevServerCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("golemlogix_dev").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("hook").then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String[] t = StringArgumentType.getString(ctx, "args").trim().split("\\s+");
                    String[] a = Arrays.copyOfRange(t, 1, t.length);
                    try {
                        DevScenes.run(t[0], a, player.level(), player, msg -> player.sendSystemMessage(Component.literal("[dev] " + msg)));
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.literal("[dev] ERR hook " + t[0] + ": " + e));
                        GolemLogixMod.LOGGER.error("[DevShots] hook {} failed", t[0], e);
                    }
                    return 1;
                }))));
    }
}
