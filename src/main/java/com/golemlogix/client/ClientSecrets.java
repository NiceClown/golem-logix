package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Client copy of the secret recipe progress: the book shows the pages, JEI the recipes.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSecrets {
    private static boolean wither;
    private static boolean dimension;

    private ClientSecrets() {
    }

    public static boolean wither() {
        return wither;
    }

    public static boolean dimension() {
        return dimension;
    }

    /** At least one trial is passed: the page with a hint to the second one is visible. */
    public static boolean any() {
        return wither || dimension;
    }

    /** Both trials are passed: the whole recipe is open. */
    public static boolean all() {
        return wither && dimension;
    }

    public static void set(boolean w, boolean d) {
        boolean changed = w != wither || d != dimension;
        wither = w;
        dimension = d;
        if (changed) {
            com.golemlogix.client.book.BookModel.invalidate();
            if (ModList.get().isLoaded("jei")) {
                com.golemlogix.integration.jei.GolemLogixJeiPlugin.applySecrets();
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        wither = false;
        dimension = false;
    }
}
