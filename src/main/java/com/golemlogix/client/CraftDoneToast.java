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
    private final List<ItemStack> items;

    public CraftDoneToast(List<ItemStack> items) {
        this.items = items;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, GolemGui.WOOD_DARK);
        g.fill(1, 1, w - 1, h - 1, GolemGui.BRASS);
        g.fill(2, 2, w - 2, h - 2, GolemGui.WOOD);
        g.fill(2, 2, 4, h - 2, GolemGui.CYAN);
        ItemStack shown = items.isEmpty() ? ItemStack.EMPTY : items.get((int) (time / 1200L % items.size()));
        if (!shown.isEmpty()) {
            g.renderFakeItem(shown.copyWithCount(1), 8, 8);
        }
        var font = toasts.getMinecraft().font;
        g.drawString(font, Component.translatable("toast.golem_logix.craft_done"), 30, 7, GolemGui.CYAN, false);
        if (!shown.isEmpty()) {
            String line = shown.getCount() + "× " + shown.getHoverName().getString();
            if (items.size() > 1) {
                line += "  +" + (items.size() - 1);
            }
            g.drawString(font, font.plainSubstrByWidth(line, w - 36), 30, 18, GolemGui.PARCHMENT, false);
        }
        return time >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
    }
}
