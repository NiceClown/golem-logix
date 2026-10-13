package com.golemlogix.client.ghost;

import com.golemlogix.system.AtlasBlueprint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Подсказка в углу экрана, пока включена проекция: что осталось поставить и какие клавиши нажимать.
 */
public final class GhostHud {
    private GhostHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!GhostProjection.isActive() || mc.level == null || mc.options.hideGui) {
            return;
        }
        var font = mc.font;
        int x = 8, y = 8;
        Component title = Component.translatable(switch (GhostProjection.variant()) {
            case FULL -> "gui.golem_logix.ghost.title.full";
            case SATURATOR -> "gui.golem_logix.ghost.title.saturator";
            case MAINFRAME -> "gui.golem_logix.ghost.title.mainframe";
            case CATALOG -> "gui.golem_logix.ghost.title.catalog";
            default -> "gui.golem_logix.ghost.title.tower";
        });
        Map<Item, Integer> missing = GhostProjection.missingItems(mc.level);
        int rows = Math.max(1, missing.size());
        int w = 176;
        int h = 38 + rows * 18 + 14;
        g.fill(x - 4, y - 4, x + w, y + h, 0xC0140D09);
        g.fill(x - 4, y - 4, x + w, y - 3, 0xFFB8862B);
        g.drawString(font, title, x, y, 0xFFF2D070, true);
        Component state;
        if (GhostProjection.anchor() == null) {
            state = Component.translatable("gui.golem_logix.ghost.aim");
        } else {
            state = Component.translatable(GhostProjection.isLocked() ? "gui.golem_logix.ghost.locked" : "gui.golem_logix.ghost.following");
        }
        g.drawString(font, state, x, y + 11, GhostProjection.isLocked() ? 0xFF6FD68A : 0xFF38D9D6, true);

        int yy = y + 26;
        if (GhostProjection.anchor() != null && missing.isEmpty()) {
            g.drawString(font, Component.translatable("gui.golem_logix.ghost.complete"), x, yy + 4, 0xFF6FD68A, true);
        } else {
            for (Map.Entry<Item, Integer> e : missing.entrySet()) {
                ItemStack s = new ItemStack(e.getKey());
                g.renderItem(s, x, yy);
                g.drawString(font, "\u00d7" + e.getValue() + "  " + font.plainSubstrByWidth(s.getHoverName().getString(), 120), x + 20, yy + 4, 0xFFE8D8B0, true);
                yy += 18;
            }
        }
        Component hint = Component.translatable("gui.golem_logix.ghost.keys",
                GolemKeys.GHOST_CONFIRM.getTranslatedKeyMessage(), GolemKeys.GHOST_CANCEL.getTranslatedKeyMessage());
        g.drawString(font, hint, x, y + h - 14, 0xFF9A8E78, false);
    }
}