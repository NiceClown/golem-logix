package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * Клиентская копия прогресса секретного рецепта: книга показывает страницы, JEI — рецепты.
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

    /** Хотя бы одно испытание пройдено: видна страница с подсказкой ко второму. */
    public static boolean any() {
        return wither || dimension;
    }

    /** Оба испытания пройдены: рецепт открыт целиком. */
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
