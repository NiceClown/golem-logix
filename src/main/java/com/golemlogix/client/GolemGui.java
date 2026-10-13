package com.golemlogix.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Примитивы отрисовки интерфейсов в оккульт-аналоговом стиле: тёмное дерево, латунные кромки,
 * бирюзовый акцент. Всё рисуется кодом — текстуры GUI не нужны.
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

    /** Основная панель окна. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WOOD_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BRASS_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BRASS);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, WOOD);
        // внутренняя тень сверху-слева и свет снизу-справа
        g.fill(x + 3, y + 3, x + w - 3, y + 4, WOOD_DARK);
        g.fill(x + 3, y + 3, x + 4, y + h - 3, WOOD_DARK);
        g.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, WOOD_LIGHT);
        g.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, WOOD_LIGHT);
        // заклёпки по углам
        rivet(g, x + 5, y + 5);
        rivet(g, x + w - 8, y + 5);
        rivet(g, x + 5, y + h - 8);
        rivet(g, x + w - 8, y + h - 8);
    }

    private static void rivet(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, COPPER);
        g.fill(x, y, x + 1, y + 1, PARCHMENT);
    }

    /** Углублённая рамка (под списки, статус). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_EDGE_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BG);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_EDGE_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_EDGE_LIGHT);
    }

    /** Фон ячейки инвентаря 18x18 (x, y — левый верхний угол ячейки, а не предмета). */
    public static void slot(GuiGraphics g, int x, int y) {
        inset(g, x, y, 18, 18);
    }

    public static void bar(GuiGraphics g, int x, int y, int w, int h, double fraction, int color) {
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

    /** Объём жидкости в вёдрах (1000 mB = 1) для списка терминала: до тысячи — с десятыми, дальше сокращённо. */
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
