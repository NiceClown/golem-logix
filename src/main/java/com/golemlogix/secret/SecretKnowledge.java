package com.golemlogix.secret;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.network.SecretStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The secret recipe (Imploded Wither Plasma and Interdimensional Occult Matrix) opens in the book and in JEI only after two
 * trials in any order: defeat the Wither and try to open a Chronometer in another dimension. The first trial gives
 * the "Secret knowledge obtained" advancement and a book page with the second task; both give the full recipe.
 * Progress is stored in the player's advancements (criteria killed_wither and dimension_signal).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class SecretKnowledge {
    public static final ResourceLocation KNOWLEDGE = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "secret_knowledge");
    public static final ResourceLocation REVEALED = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "secret_revealed");
    public static final String WITHER = "killed_wither";
    public static final String DIMENSION = "dimension_signal";

    private SecretKnowledge() {
    }

    /** Player state: [Wither defeated, attempt from another dimension]. */
    public static boolean[] state(ServerPlayer player) {
        AdvancementHolder holder = player.server.getAdvancements().get(REVEALED);
        if (holder == null) {
            return new boolean[]{false, false};
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        return new boolean[]{done(progress, WITHER), done(progress, DIMENSION)};
    }

    private static boolean done(AdvancementProgress progress, String criterion) {
        CriterionProgress c = progress.getCriterion(criterion);
        return c != null && c.isDone();
    }

    public static void sync(ServerPlayer player) {
        boolean[] s = state(player);
        PacketDistributor.sendToPlayer(player, new SecretStatePayload(s[0], s[1]));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    @SubscribeEvent
    public static void onProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        ResourceLocation id = event.getAdvancement().id();
        if (!id.equals(REVEALED)) {
            return;
        }
        boolean[] s = state(sp);
        sync(sp);
        if (event.getProgressType() == AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) {
            // a page opened in the book: clicking the message opens it
            String entry = s[0] && s[1] ? "wither" : "secret_hint";
            Component link = Component.translatable("message.golem_logix.secret.open_book").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/golemlogix_book " + entry))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.golem_logix.secret.open_book_hint"))));
            String key = s[0] && s[1] ? "message.golem_logix.secret.revealed" : "message.golem_logix.secret.page";
            sp.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE).append(" ").append(link));
        }
    }
}
