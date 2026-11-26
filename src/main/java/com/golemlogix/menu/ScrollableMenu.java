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

    /** Pre-parsed search query so callers scanning many stacks do not re-normalize the filter string per item. */
    record FilterQuery(boolean empty, boolean byMod, String needle, String pathNeedle) {
        public static FilterQuery parse(String filter) {
            if (filter.isEmpty()) {
                return new FilterQuery(true, false, "", "");
            }
            String lower = filter.toLowerCase(Locale.ROOT).trim();
            if (lower.startsWith("@")) {
                return new FilterQuery(false, true, lower.substring(1), "");
            }
            return new FilterQuery(false, false, lower, lower.replace(' ', '_'));
        }

        public boolean matches(ItemStack stack) {
            if (empty) {
                return true;
            }
            if (stack.isEmpty()) {
                return false;
            }
            var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (byMod) {
                return id.getNamespace().contains(needle);
            }
            return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)
                    || id.getPath().contains(pathNeedle);
        }
    }

    /**
     * Whether an item matches the search: by name (in the server language, which in single player is the player's language) and by item id;
     * "@mod" matches by mod.
     */
    static boolean matches(ItemStack stack, String filter) {
        return FilterQuery.parse(filter).matches(stack);
    }
}
