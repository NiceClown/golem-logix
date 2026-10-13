package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Клиентская команда /golemlogix_book &lt;статья&gt;: открывает Гримуар на нужной статье (по ней щёлкают в сообщении
 * о новом секретном знании).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public final class GolemClientCommands {
    private GolemClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("golemlogix_book")
                .then(Commands.argument("entry", StringArgumentType.word()).executes(ctx -> {
                    String entry = StringArgumentType.getString(ctx, "entry");
                    Minecraft mc = Minecraft.getInstance();
                    // после щелчка по чату окно чата ещё открыто: книгу открываем на следующем кадре
                    mc.tell(() -> mc.setScreen(com.golemlogix.client.book.BookScreen.openAt(entry)));
                    return 1;
                })));
    }
}
