package com.golemlogix.client;

import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.menu.StockRequesterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

public class StockRequesterScreen extends GolemScreen<StockRequesterMenu> {
    public StockRequesterScreen(StockRequesterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, StockRequesterMenu.IMAGE_WIDTH, StockRequesterMenu.IMAGE_HEIGHT);
    }

    @Override
    protected int filterSlots() {
        return StockRequesterBlockEntity.FILTER_SLOTS;
    }

    @Override
    protected boolean keepsAmounts() {
        return true;
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        super.renderSlot(g, slot);

        if (slot.index < StockRequesterBlockEntity.FILTER_SLOTS && slot.hasItem()) {
            int st = menu.getSlotStatus(slot.index);
            int color = switch (st) {
                case StockRequesterBlockEntity.STATUS_CRAFTING -> GolemGui.CYAN;
                case StockRequesterBlockEntity.STATUS_NO_PATTERN -> 0xFFE0A020;
                case StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS -> GolemGui.RED;
                default -> 0xFF38D970; // OK
            };
            // Small status dot in top-left corner of the slot (over the item)
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(slot.x + 1, slot.y + 1, slot.x + 4, slot.y + 4, color);
            g.pose().popPose();
        }
    }

    /** A filled order slot shows its item and its status instead of the item tooltip. */
    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        Slot slot = hoveredSlot;
        if (slot != null && menu.getCarried().isEmpty() && slot.index < StockRequesterBlockEntity.FILTER_SLOTS && slot.hasItem()) {
            int st = menu.getSlotStatus(slot.index);
            String key = "gui.golem_logix.requester.status." + switch (st) {
                case StockRequesterBlockEntity.STATUS_CRAFTING -> "crafting";
                case StockRequesterBlockEntity.STATUS_NO_PATTERN -> "no_pattern";
                case StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS -> "missing";
                default -> "ok";
            };
            g.renderComponentTooltip(font, List.of(slot.getItem().getHoverName(), Component.translatable(key)), mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, GolemGui.PARCHMENT, false);
        g.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0xFFA89880, false);
    }
}
