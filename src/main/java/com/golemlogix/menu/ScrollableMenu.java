package com.golemlogix.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * A menu with a scrolling grid of slots and search (catalog, concentrator): the client sends the first visible row and
 * the search line (MenuScrollPayload packet), the server shifts and filters the slot window, and the usual menu sync
 * sends the new items.
 */
public interface ScrollableMenu {
    void view(int row, String filter);

    /**
     * Whether an item matches the search: by name (in the server language, which in single player is the player's language) and by item id;
     * "@mod" matches by mod.
     */
    static boolean matches(ItemStack stack, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }
        String f = filter.toLowerCase(Locale.ROOT).trim();
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (f.startsWith("@")) {
            return id.getNamespace().contains(f.substring(1));
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(f) || id.getPath().contains(f.replace(' ', '_'));
    }
}
