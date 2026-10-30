package com.golemlogix.event;

import com.golemlogix.GolemLogixMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * This version is a beta: on joining a world every player is told so in gold - the look of the newest devices will still be
 * reworked - and where to report problems (the issues page opens on a click).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class BetaNotice {
    static final String ISSUES = "https://github.com/NiceClown/golem-logix/issues";

    private BetaNotice() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String version = ModList.get().getModContainerById(GolemLogixMod.MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString()).orElse("");
            Component link = Component.literal("GitHub").withStyle(style -> style.withColor(ChatFormatting.GOLD).withUnderlined(true)
                    .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(ISSUES)))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(ISSUES))));
            player.sendSystemMessage(Component.translatable("message.golem_logix.beta", version, link).withStyle(ChatFormatting.GOLD));
        }
    }
}
