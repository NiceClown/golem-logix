package com.golemlogix.client;

import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.items.TelegraphLinkCardItem;
import com.golemlogix.menu.TelegraphMenu;
import com.golemlogix.system.TelegraphLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Cryo-Source Telegraph window: three signed card slots (Range, Write, Memory; a locked one is crossed out while the other link
 * slot holds a card), the link state and the big Reset button.
 */
public class TelegraphScreen extends GolemScreen<TelegraphMenu> {
    private static final int STATUS_Y = 56;
    private static final int[] BAY_X = {TelegraphMenu.RANGE_X, TelegraphMenu.WRITE_X, TelegraphMenu.MEMORY_X};
    private static final Component LABEL_RANGE = Component.translatable("gui.golem_logix.telegraph.range");
    private static final Component LABEL_WRITE = Component.translatable("gui.golem_logix.telegraph.write");
    private static final Component LABEL_MEMORY = Component.translatable("gui.golem_logix.telegraph.memory");
    private static final Component STATUS_LINKED_SHORT = Component.translatable("gui.golem_logix.telegraph.status.linked_short");
    private static final Component STATUS_WORKING = Component.translatable("gui.golem_logix.telegraph.working");
    private static final Component STATUS_IDLE = Component.translatable("gui.golem_logix.telegraph.idle");
    private static final Component LABEL_BLOCKED = Component.translatable("gui.golem_logix.telegraph.blocked").withStyle(ChatFormatting.RED);
    private static final Component LABEL_RESET = Component.translatable("gui.golem_logix.telegraph.reset");
    private static final Component TIP_RESET = Component.translatable("gui.golem_logix.telegraph.reset.tooltip");

    public TelegraphScreen(TelegraphMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, TelegraphMenu.IMAGE_WIDTH, TelegraphMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new ResetButton(leftPos + 8, topPos + 100, 160, 18, b -> menuButton(TelegraphMenu.BUTTON_RESET)));
    }

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        // the three card bays and the status window
        for (int x : BAY_X) {
            GolemGui.inset(g, leftPos + x - 14, topPos + 18, 44, 34);
        }
        GolemGui.inset(g, leftPos + 7, topPos + STATUS_Y, 162, 40);
        // a thin "wire" from Write to Memory: the card travels between telegraphs
        int wy = topPos + TelegraphMenu.SLOT_Y + 8;
        g.fill(leftPos + TelegraphMenu.WRITE_X + 31, wy, leftPos + TelegraphMenu.MEMORY_X - 15, wy + 1, GolemGui.CYAN_DARK);
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        for (int i = TelegraphBlockEntity.SLOT_WRITE; i <= TelegraphBlockEntity.SLOT_MEMORY; i++) {
            if (menu.blocked(i)) {
                Slot s = menu.slots.get(i);
                int x = leftPos + s.x, y = topPos + s.y;
                g.fill(x, y, x + 16, y + 16, 0xC0201410);
                for (int k = 2; k < 14; k++) {
                    g.fill(x + k, y + k, x + k + 1, y + k + 1, GolemGui.RED);
                    g.fill(x + 15 - k, y + k, x + 16 - k, y + k + 1, GolemGui.RED);
                }
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        label(g, LABEL_RANGE, TelegraphMenu.RANGE_X, GolemGui.BRASS_L);
        label(g, LABEL_WRITE, TelegraphMenu.WRITE_X, GolemGui.GREEN);
        label(g, LABEL_MEMORY, TelegraphMenu.MEMORY_X, GolemGui.CYAN);

        int x = 11, y = STATUS_Y + 4;
        TelegraphLinks.Status status = menu.status();
        boolean writing = TelegraphLinkCardItem.isCard(menu.slotItem(TelegraphBlockEntity.SLOT_WRITE));
        drawFitted(g, statusLine(status, writing), x, y, statusColor(status, writing));
        drawFitted(g, Component.translatable("gui.golem_logix.telegraph.receivers", menu.receivers()), x, y + 11, GolemGui.PARCHMENT);
        drawFitted(g, menu.active() ? STATUS_WORKING : STATUS_IDLE, x, y + 22, menu.active() ? GolemGui.GREEN : 0xFFA89880);
    }

    private Component statusLine(TelegraphLinks.Status status, boolean writing) {
        if (writing) {
            return Component.translatable("gui.golem_logix.telegraph.written", menu.slotItem(TelegraphBlockEntity.SLOT_WRITE).getCount());
        }
        if (status == TelegraphLinks.Status.LINKED) {
            TelegraphLinkCardItem.Link link = TelegraphLinkCardItem.link(menu.slotItem(TelegraphBlockEntity.SLOT_MEMORY));
            return link == null ? STATUS_LINKED_SHORT
                    : Component.translatable("gui.golem_logix.telegraph.status.linked", link.target().pos().getX(),
                    link.target().pos().getY(), link.target().pos().getZ());
        }
        return Component.translatable("gui.golem_logix.telegraph.status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int statusColor(TelegraphLinks.Status status, boolean writing) {
        if (writing) {
            return GolemGui.GREEN;
        }
        if (status == TelegraphLinks.Status.LINKED) {
            return GolemGui.CYAN;
        }
        return status == TelegraphLinks.Status.NO_CARD ? GolemGui.PARCHMENT : GolemGui.RED;
    }

    private void label(GuiGraphicsExtractor g, Component c, int slotX, int color) {
        // a long translation is squeezed into the slot's frame
        int w = font.width(c);
        float fit = Math.min(1f, 42f / Math.max(1, w));
        g.pose().pushMatrix();
        g.pose().translate(slotX + 8 - w * fit / 2f, 21 + (1f - fit) * 4f);
        g.pose().scale(fit, fit);
        g.text(font, c, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** A line that never leaves the status window: a long translation is squeezed. */
    private void drawFitted(GuiGraphicsExtractor g, Component text, int x, int y, int color) {
        int w = font.width(text);
        float fit = Math.min(1f, 154f / Math.max(1, w));
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(fit, fit);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // what each slot is for, while it is empty
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index <= TelegraphBlockEntity.SLOT_MEMORY && !hoveredSlot.hasItem()) {
            int i = hoveredSlot.index;
            String key = i == TelegraphBlockEntity.SLOT_RANGE ? "gui.golem_logix.telegraph.range.hint"
                    : i == TelegraphBlockEntity.SLOT_WRITE ? "gui.golem_logix.telegraph.write.hint" : "gui.golem_logix.telegraph.memory.hint";
            List<Component> lines = new java.util.ArrayList<>(2);
            lines.add(Component.translatable(key, GolemConfig.LOCAL_WIRELESS_RANGE.get()));
            if (menu.blocked(i)) {
                lines.add(LABEL_BLOCKED);
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /** The big red-brass Reset plate. */
    private static class ResetButton extends GolemButton {
        ResetButton(int x, int y, int w, int h, OnPress onPress) {
            super(x, y, w, h, () -> LABEL_RESET, () -> TIP_RESET, () -> false, onPress);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
            boolean hover = isHoveredOrFocused() && active;
            g.fill(getX(), getY(), getX() + width, getY() + height, GolemGui.WOOD_DARK);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, hover ? GolemGui.BRASS_L : GolemGui.BRASS);
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, hover ? 0xFF9A2E24 : 0xFF6E1F18);
            // rivets in the corners
            g.fill(getX() + 4, getY() + 4, getX() + 6, getY() + 6, GolemGui.BRASS_L);
            g.fill(getX() + width - 6, getY() + 4, getX() + width - 4, getY() + 6, GolemGui.BRASS_L);
            g.fill(getX() + 4, getY() + height - 6, getX() + 6, getY() + height - 4, GolemGui.BRASS_L);
            g.fill(getX() + width - 6, getY() + height - 6, getX() + width - 4, getY() + height - 4, GolemGui.BRASS_L);
            var font = net.minecraft.client.Minecraft.getInstance().font;
            float scale = Math.min(1.25f, (width - 12f) / Math.max(1, font.width(getMessage())));
            g.pose().pushMatrix();
            g.pose().translate(getX() + width / 2f, getY() + height / 2f);
            g.pose().scale(scale, scale);
            g.centeredText(font, getMessage(), 0, -4, hover ? 0xFFFFE6A0 : GolemGui.PARCHMENT);
            g.pose().popMatrix();
        }
    }
}
