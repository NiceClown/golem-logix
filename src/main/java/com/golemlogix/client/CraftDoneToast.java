package com.golemlogix.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * "Order ready": a brass plate in the corner of the screen with the item and the count (like the finished craft notification in AE2).
 * Several ordered items take turns.
 */
public class CraftDoneToast implements Toast {
    private static final long SHOW_MS = 5000L;
    private static final Component TITLE = Component.translatable("toast.golem_logix.craft_done");

    private final List<ItemStack> icons;
    private final String[] labels;
    private String[] fittedLabels;
    private int fittedWidth = -1;

    public CraftDoneToast(List<ItemStack> items) {
        int n = items.size();
        this.icons = new java.util.ArrayList<>(n);
        this.labels = new String[n];
        String suffix = n > 1 ? "  +" + (n - 1) : "";
        for (int i = 0; i < n; i++) {
            ItemStack s = items.get(i);
            this.icons.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            this.labels[i] = s.isEmpty() ? "" : s.getCount() + "× " + s.getHoverName().getString() + suffix;
        }
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, GolemGui.WOOD_DARK);
        g.fill(1, 1, w - 1, h - 1, GolemGui.BRASS);
        g.fill(2, 2, w - 2, h - 2, GolemGui.WOOD);
        g.fill(2, 2, 4, h - 2, GolemGui.CYAN);
        int idx = icons.isEmpty() ? -1 : Math.floorMod((int) (time / 1200L), icons.size());
        ItemStack shown = idx < 0 ? ItemStack.EMPTY : icons.get(idx);
        if (!shown.isEmpty()) {
            g.renderFakeItem(shown, 8, 8);
        }
        var font = toasts.getMinecraft().font;
        g.drawString(font, TITLE, 30, 7, GolemGui.CYAN, false);
        if (!shown.isEmpty()) {
            int maxW = w - 36;
            if (fittedLabels == null || fittedWidth != maxW) {
                fittedWidth = maxW;
                fittedLabels = new String[labels.length];
                for (int i = 0; i < labels.length; i++) {
                    fittedLabels[i] = font.plainSubstrByWidth(labels[i], maxW);
                }
            }
            g.drawString(font, fittedLabels[idx], 30, 18, GolemGui.PARCHMENT, false);
        }
        return time >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
