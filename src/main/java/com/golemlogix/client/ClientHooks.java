package com.golemlogix.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client calls moved out of common classes so that a dedicated server never tries to load client code.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    /**
     * JEI search line (when JEI is installed the plugin plugs its functions in here): the mod's terminals mirror their search into
     * JEI and pick up what the player typed in JEI.
     */
    public static java.util.function.Consumer<String> jeiSetSearch = s -> {
    };
    public static java.util.function.Supplier<String> jeiGetSearch = () -> null;

    /** The local player (or null), for atlant heads that follow them. */
    public static net.minecraft.world.entity.player.Player localPlayer() {
        return Minecraft.getInstance().player;
    }

    public static void openGuideBook() {
        Minecraft.getInstance().setScreen(new com.golemlogix.client.book.BookScreen());
    }

    /** "Order ready" notification and a short chime. */
    public static void craftDone(List<ItemStack> items) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToastManager().addToast(new CraftDoneToast(items));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.6f, 0.45f));
    }
}
