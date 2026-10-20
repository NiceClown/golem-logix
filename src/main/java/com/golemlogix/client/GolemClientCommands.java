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
 * Client command /golemlogix_book &lt;entry&gt;: opens the Grimoire at the given entry (it is clicked in the message
 * about new secret knowledge).
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
                    // after a click in chat the chat screen is still open: the book is opened on the next frame
                    mc.schedule(() -> mc.setScreen(com.golemlogix.client.book.BookScreen.openAt(entry)));
                    return 1;
                })));
    }
}
