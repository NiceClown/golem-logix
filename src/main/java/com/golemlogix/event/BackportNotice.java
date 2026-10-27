package com.golemlogix.event;

import com.golemlogix.GolemLogixMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The 1.21.1 line is a backport of the 26.1.2 one: on joining a world every player is told in red that this is an alpha build and
 * where to report problems (the issues page opens on a click).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class BackportNotice {
    static final String ISSUES = "https://github.com/NiceClown/golem-logix/issues";

    private BackportNotice() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Component link = Component.literal("GitHub").withStyle(style -> style.withColor(ChatFormatting.RED).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ISSUES))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(ISSUES))));
            player.sendSystemMessage(Component.translatable("message.golem_logix.alpha_backport", link).withStyle(ChatFormatting.RED));
        }
    }
}
