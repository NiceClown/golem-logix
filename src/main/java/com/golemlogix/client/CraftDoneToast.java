package com.golemlogix.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
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

    private Visibility visibility = Visibility.SHOW;

    public CraftDoneToast(List<ItemStack> items) {
        this.items = items;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager toasts, long time) {
        visibility = time >= SHOW_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, long time) {
        int w = width(), h = height();
        g.fill(0, 0, w, h, GolemGui.WOOD_DARK);
        g.fill(1, 1, w - 1, h - 1, GolemGui.BRASS);
        g.fill(2, 2, w - 2, h - 2, GolemGui.WOOD);
        g.fill(2, 2, 4, h - 2, GolemGui.CYAN);
        ItemStack shown = items.isEmpty() ? ItemStack.EMPTY : items.get((int) (time / 1200L % items.size()));
        if (!shown.isEmpty()) {
            g.fakeItem(shown.copyWithCount(1), 8, 8);
        }
        g.text(font, Component.translatable("toast.golem_logix.craft_done"), 30, 7, GolemGui.CYAN, false);
        if (!shown.isEmpty()) {
            String line = shown.getCount() + "× " + shown.getHoverName().getString();
            if (items.size() > 1) {
                line += "  +" + (items.size() - 1);
            }
            g.text(font, font.plainSubstrByWidth(line, w - 36), 30, 18, GolemGui.PARCHMENT, false);
        }
    }
}
