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
 * Стенд разработчика по сети: /golemlogix_dev hook &lt;имя&gt; &lt;арг...&gt; выполняет на сервере те же сборщики сцен, что и
 * одиночный стенд кадров ({@link DevScenes}); ответы уходят игроку в чат с меткой «[dev]» — их записывает клиентский стенд.
 * Только для разработки: пакет devtest в релизный jar не входит.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class DevServerCommand {
    private DevServerCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("golemlogix_dev").requires(s -> s.hasPermission(2))
                .then(Commands.literal("hook").then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String[] t = StringArgumentType.getString(ctx, "args").trim().split("\\s+");
                    String[] a = Arrays.copyOfRange(t, 1, t.length);
                    try {
                        DevScenes.run(t[0], a, player.serverLevel(), player, msg -> player.sendSystemMessage(Component.literal("[dev] " + msg)));
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.literal("[dev] ERR hook " + t[0] + ": " + e));
                        GolemLogixMod.LOGGER.error("[DevShots] hook {} failed", t[0], e);
                    }
                    return 1;
                }))));
    }
}
