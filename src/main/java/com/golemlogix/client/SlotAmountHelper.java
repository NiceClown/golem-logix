package com.golemlogix.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Shared amount formatting, bucket-suffix parsing, step labels, and numeric input box creation
 * for {@link FilterSlotScreen}, {@link InterfaceSlotScreen}, and {@link CraftRequestScreen}.
 */
final class SlotAmountHelper {
    static final int[] FLUID_DELTAS = {-10_000, -1000, -100, 100, 1000, 10_000};
    static final int[] ITEM_DELTAS = {-64, -10, -1, 1, 10, 64};

    static final Component UNIT_MB = Component.translatable("gui.golem_logix.interface.unit_mb");
    static final Component UNIT_ITEMS = Component.translatable("gui.golem_logix.interface.unit_items");
    static final Component CLEAR_LABEL = Component.translatable("gui.golem_logix.interface.clear");
    static final Component DONE_LABEL = Component.translatable("gui.golem_logix.interface.done");

    private SlotAmountHelper() {
    }

    /** Formats a signed step delta (+64, -10, +1B, -10B). */
    static String deltaLabel(int d) {
        String sign = d > 0 ? "+" : "-";
        int a = Math.abs(d);
        return sign + (a >= 1000 && a % 1000 == 0 ? a / 1000 + "B" : Integer.toString(a));
    }

    /** Formats a fluid volume in mB as buckets ("1B", "1.5B"). */
    static String bucketText(int mb) {
        return mb % 1000 == 0 ? mb / 1000 + "B" : String.format(Locale.ROOT, "%.1fB", mb / 1000f);
    }

    /** Parses a typed non-negative amount; trailing 'B'/'b' (Latin or Cyrillic) multiplies by 1000 for fluids. */
    static int parseAmount(String raw, boolean fluid, int fallback) {
        String s = raw;
        int mult = 1;
        if (!s.isEmpty() && "bBвВ".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
            mult = fluid ? 1000 : 1;
        }
        try {
            return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, Long.parseLong(s) * mult));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Creates a borderless parchment amount EditBox with regex validation. */
    static EditBox createAmountBox(Font font, int x, int y, int w, int maxLen, String regex, String initial) {
        EditBox box = new EditBox(font, x, y, w, 12, Component.empty());
        box.setBordered(false);
        box.setMaxLength(maxLen);
        box.setFilter(s -> s.isEmpty() || s.matches(regex));
        box.setTextColor(GolemGui.PARCHMENT);
        box.setValue(initial);
        return box;
    }

    static boolean isEnterKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }
}
