package com.golemlogix.client.ghost;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A hint in the screen corner while the projection is on: what is left to place and which keys to press.
 */
public final class GhostHud {
    private static final Component TITLE_FULL = Component.translatable("gui.golem_logix.ghost.title.full");
    private static final Component TITLE_SATURATOR = Component.translatable("gui.golem_logix.ghost.title.saturator");
    private static final Component TITLE_MAINFRAME = Component.translatable("gui.golem_logix.ghost.title.mainframe");
    private static final Component TITLE_CATALOG = Component.translatable("gui.golem_logix.ghost.title.catalog");
    private static final Component TITLE_TOWER = Component.translatable("gui.golem_logix.ghost.title.tower");
    private static final Component STATE_AIM = Component.translatable("gui.golem_logix.ghost.aim");
    private static final Component STATE_LOCKED = Component.translatable("gui.golem_logix.ghost.locked");
    private static final Component STATE_FOLLOWING = Component.translatable("gui.golem_logix.ghost.following");
    private static final Component COMPLETE = Component.translatable("gui.golem_logix.ghost.complete");

    private static final Map<Item, ItemStack> ICON_CACHE = new IdentityHashMap<>();

    private GhostHud() {
    }

    static void clearCache() {
        ICON_CACHE.clear();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!GhostProjection.isActive() || mc.level == null || mc.options.hideGui) {
            return;
        }
        var font = mc.font;
        int x = 8, y = 8;
        Component title = switch (GhostProjection.variant()) {
            case FULL -> TITLE_FULL;
            case SATURATOR -> TITLE_SATURATOR;
            case MAINFRAME -> TITLE_MAINFRAME;
            case CATALOG -> TITLE_CATALOG;
            default -> TITLE_TOWER;
        };
        Map<Item, Integer> missing = GhostProjection.cachedMissingItems(mc.level);
        int rows = Math.max(1, missing.size());
        int w = 176;
        int h = 38 + rows * 18 + 14;
        g.fill(x - 4, y - 4, x + w, y + h, 0xC0140D09);
        g.fill(x - 4, y - 4, x + w, y - 3, 0xFFB8862B);
        g.text(font, title, x, y, 0xFFF2D070, true);
        Component state = GhostProjection.anchor() == null ? STATE_AIM
                : GhostProjection.isLocked() ? STATE_LOCKED : STATE_FOLLOWING;
        g.text(font, state, x, y + 11, GhostProjection.isLocked() ? 0xFF6FD68A : 0xFF38D9D6, true);

        int yy = y + 26;
        if (GhostProjection.anchor() != null && missing.isEmpty()) {
            g.text(font, COMPLETE, x, yy + 4, 0xFF6FD68A, true);
        } else {
            for (Map.Entry<Item, Integer> e : missing.entrySet()) {
                ItemStack s = ICON_CACHE.computeIfAbsent(e.getKey(), ItemStack::new);
                g.item(s, x, yy);
                g.text(font, "\u00d7" + e.getValue() + "  " + font.plainSubstrByWidth(s.getHoverName().getString(), 120), x + 20, yy + 4, 0xFFE8D8B0, true);
                yy += 18;
            }
        }
        Component hint = Component.translatable("gui.golem_logix.ghost.keys",
                GolemKeys.GHOST_CONFIRM.getTranslatedKeyMessage(), GolemKeys.GHOST_CANCEL.getTranslatedKeyMessage());
        g.text(font, hint, x, y + h - 14, 0xFF9A8E78, false);
    }
}