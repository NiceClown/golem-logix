package com.golemlogix.client;

import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.menu.StockRequesterMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(g, slot, mouseX, mouseY);

        if (slot.index < StockRequesterBlockEntity.FILTER_SLOTS && slot.hasItem()) {
            int st = menu.getSlotStatus(slot.index);
            int color = switch (st) {
                case StockRequesterBlockEntity.STATUS_CRAFTING -> GolemGui.CYAN;
                case StockRequesterBlockEntity.STATUS_NO_PATTERN -> 0xFFE0A020;
                case StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS -> GolemGui.RED;
                default -> 0xFF38D970; // OK
            };
            // Small status dot in top-left corner of the slot
            g.fill(slot.x + 1, slot.y + 1, slot.x + 4, slot.y + 4, color);

            if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                    && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                String key = "gui.golem_logix.requester.status." + switch (st) {
                    case StockRequesterBlockEntity.STATUS_CRAFTING -> "crafting";
                    case StockRequesterBlockEntity.STATUS_NO_PATTERN -> "no_pattern";
                    case StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS -> "missing";
                    default -> "ok";
                };
                Component tip = Component.empty().append(slot.getItem().getHoverName()).append("\n").append(Component.translatable(key));
                g.setTooltipForNextFrame(font, tip, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, GolemGui.PARCHMENT, false);
        g.text(font, playerInventoryTitle, 8, imageHeight - 94, 0xFFA89880, false);
    }
}
