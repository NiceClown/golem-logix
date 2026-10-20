package com.golemlogix.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Primitives for drawing interfaces in the occult-analog style: dark wood, brass edges,
 * a teal accent. Everything is drawn by code, no GUI textures are needed.
 */
public final class GolemGui {
    public static final int WOOD_DARK = 0xFF1E140E;
    public static final int WOOD = 0xFF2E2016;
    public static final int WOOD_LIGHT = 0xFF4A3322;
    public static final int BRASS = 0xFFB8862B;
    public static final int BRASS_DARK = 0xFF6E4E17;
    public static final int BRASS_L = 0xFFF2D070;
    public static final int COPPER = 0xFFB87333;
    public static final int CYAN = 0xFF38D9D6;
    public static final int CYAN_DARK = 0xFF1B6E6C;
    public static final int PARCHMENT = 0xFFE8D8B0;
    public static final int SLOT_BG = 0xFF140D09;
    public static final int SLOT_EDGE_DARK = 0xFF0B0705;
    public static final int SLOT_EDGE_LIGHT = 0xFF5A4230;
    public static final int RED = 0xFFE05A4F;
    public static final int GREEN = 0xFF6FD68A;

    private GolemGui() {
    }

    /** Main window panel. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WOOD_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BRASS_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BRASS);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, WOOD);
        // inner shadow at the top left and light at the bottom right
        g.fill(x + 3, y + 3, x + w - 3, y + 4, WOOD_DARK);
        g.fill(x + 3, y + 3, x + 4, y + h - 3, WOOD_DARK);
        g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, WOOD_LIGHT);
        g.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, WOOD_LIGHT);
        // rivets in the corners
        rivet(g, x + 5, y + 5);
        rivet(g, x + w - 8, y + 5);
        rivet(g, x + 5, y + h - 8);
        rivet(g, x + w - 8, y + h - 8);
    }

    private static void rivet(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, COPPER);
        g.fill(x, y, x + 1, y + 1, PARCHMENT);
    }

    /** A sunken frame (for lists, status). */
    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_EDGE_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BG);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_EDGE_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_EDGE_LIGHT);
    }

    /** Background of an 18x18 inventory slot (x, y are the top left corner of the slot, not of the item). */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        inset(g, x, y, 18, 18);
    }

    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, int color) {
        inset(g, x, y, w, h);
        int fill = (int) Math.round((w - 2) * Math.max(0, Math.min(1, fraction)));
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x40FFFFFF);
        }
    }

    public static String formatCount(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        if (n < 1_000_000) {
            return trim(n / 1000.0) + "k";
        }
        if (n < 1_000_000_000L) {
            return trim(n / 1_000_000.0) + "M";
        }
        return trim(n / 1_000_000_000.0) + "G";
    }

    /** Fluid volume in buckets (1000 mB = 1) for the terminal list: with tenths up to a thousand, abbreviated beyond. */
    public static String formatBuckets(long mb) {
        if (mb <= 0) {
            return "0";
        }
        if (mb < 50) {
            return "<0.1";
        }
        return mb < 1_000_000 ? trim(mb / 1000.0) : formatCount(mb / 1000);
    }

    private static String trim(double v) {
        if (v >= 100) {
            return Long.toString(Math.round(v));
        }
        String s = String.format(java.util.Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
