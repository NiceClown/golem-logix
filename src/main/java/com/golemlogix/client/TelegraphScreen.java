package com.golemlogix.client;

import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.items.TelegraphLinkCardItem;
import com.golemlogix.menu.TelegraphMenu;
import com.golemlogix.system.TelegraphLinks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
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

    public TelegraphScreen(TelegraphMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, TelegraphMenu.IMAGE_WIDTH, TelegraphMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new ResetButton(leftPos + 8, topPos + 100, 160, 18, b -> menuButton(TelegraphMenu.BUTTON_RESET)));
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // the three card bays and the status window
        for (int x : new int[]{TelegraphMenu.RANGE_X, TelegraphMenu.WRITE_X, TelegraphMenu.MEMORY_X}) {
            GolemGui.inset(g, leftPos + x - 14, topPos + 18, 44, 34);
        }
        GolemGui.inset(g, leftPos + 7, topPos + STATUS_Y, 162, 40);
        // a thin "wire" from Write to Memory: the card travels between telegraphs
        int wy = topPos + TelegraphMenu.SLOT_Y + 8;
        g.fill(leftPos + TelegraphMenu.WRITE_X + 31, wy, leftPos + TelegraphMenu.MEMORY_X - 15, wy + 1, GolemGui.CYAN_DARK);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        for (int i = TelegraphBlockEntity.SLOT_WRITE; i <= TelegraphBlockEntity.SLOT_MEMORY; i++) {
            if (menu.blocked(i)) {
                Slot s = menu.slots.get(i);
                int x = leftPos + s.x, y = topPos + s.y;
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.fill(x, y, x + 16, y + 16, 0xC0201410);
                for (int k = 2; k < 14; k++) {
                    g.fill(x + k, y + k, x + k + 1, y + k + 1, GolemGui.RED);
                    g.fill(x + 15 - k, y + k, x + 16 - k, y + k + 1, GolemGui.RED);
                }
                g.pose().popPose();
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        label(g, "gui.golem_logix.telegraph.range", TelegraphMenu.RANGE_X, GolemGui.BRASS_L);
        label(g, "gui.golem_logix.telegraph.write", TelegraphMenu.WRITE_X, GolemGui.GREEN);
        label(g, "gui.golem_logix.telegraph.memory", TelegraphMenu.MEMORY_X, GolemGui.CYAN);

        int x = 11, y = STATUS_Y + 4;
        TelegraphLinks.Status status = menu.status();
        boolean writing = TelegraphLinkCardItem.isCard(menu.slotItem(TelegraphBlockEntity.SLOT_WRITE));
        Component main;
        int color;
        if (writing) {
            main = Component.translatable("gui.golem_logix.telegraph.written", menu.slotItem(TelegraphBlockEntity.SLOT_WRITE).getCount());
            color = GolemGui.GREEN;
        } else if (status == TelegraphLinks.Status.LINKED) {
            TelegraphLinkCardItem.Link link = TelegraphLinkCardItem.link(menu.slotItem(TelegraphBlockEntity.SLOT_MEMORY));
            main = link == null ? Component.translatable("gui.golem_logix.telegraph.status.linked_short")
                    : Component.translatable("gui.golem_logix.telegraph.status.linked", link.target().pos().getX(),
                    link.target().pos().getY(), link.target().pos().getZ());
            color = GolemGui.CYAN;
        } else {
            main = Component.translatable("gui.golem_logix.telegraph.status." + status.name().toLowerCase(java.util.Locale.ROOT));
            color = status == TelegraphLinks.Status.NO_CARD ? GolemGui.PARCHMENT : GolemGui.RED;
        }
        drawFitted(g, main, x, y, color);
        drawFitted(g, Component.translatable("gui.golem_logix.telegraph.receivers", menu.receivers()), x, y + 11, GolemGui.PARCHMENT);
        drawFitted(g, Component.translatable(menu.active() ? "gui.golem_logix.telegraph.working" : "gui.golem_logix.telegraph.idle"),
                x, y + 22, menu.active() ? GolemGui.GREEN : 0xFFA89880);
    }

    private void label(GuiGraphics g, String key, int slotX, int color) {
        Component c = Component.translatable(key);
        // a long translation is squeezed into the slot's frame
        float fit = Math.min(1f, 42f / Math.max(1, font.width(c)));
        g.pose().pushPose();
        g.pose().translate(slotX + 8 - font.width(c) * fit / 2f, 21 + (1f - fit) * 4f, 0);
        g.pose().scale(fit, fit, 1f);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    /** A line that never leaves the status window: a long translation is squeezed. */
    private void drawFitted(GuiGraphics g, Component text, int x, int y, int color) {
        int w = font.width(text);
        float fit = Math.min(1f, 154f / Math.max(1, w));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(fit, fit, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // what each slot is for, while it is empty
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index <= TelegraphBlockEntity.SLOT_MEMORY && !hoveredSlot.hasItem()) {
            int i = hoveredSlot.index;
            String key = i == TelegraphBlockEntity.SLOT_RANGE ? "gui.golem_logix.telegraph.range.hint"
                    : i == TelegraphBlockEntity.SLOT_WRITE ? "gui.golem_logix.telegraph.write.hint" : "gui.golem_logix.telegraph.memory.hint";
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(Component.translatable(key, GolemConfig.LOCAL_WIRELESS_RANGE.get()));
            if (menu.blocked(i)) {
                lines.add(Component.translatable("gui.golem_logix.telegraph.blocked").withStyle(ChatFormatting.RED));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** The big red-brass Reset plate. */
    private static class ResetButton extends GolemButton {
        ResetButton(int x, int y, int w, int h, OnPress onPress) {
            super(x, y, w, h, () -> Component.translatable("gui.golem_logix.telegraph.reset"),
                    () -> Component.translatable("gui.golem_logix.telegraph.reset.tooltip"), () -> false, onPress);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean hover = isHoveredOrFocused() && active;
            g.fill(getX(), getY(), getX() + width, getY() + height, GolemGui.WOOD_DARK);
            g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, hover ? GolemGui.BRASS_L : GolemGui.BRASS);
            g.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, hover ? 0xFF9A2E24 : 0xFF6E1F18);
            // rivets in the corners
            for (int[] r : new int[][]{{4, 4}, {width - 6, 4}, {4, height - 6}, {width - 6, height - 6}}) {
                g.fill(getX() + r[0], getY() + r[1], getX() + r[0] + 2, getY() + r[1] + 2, GolemGui.BRASS_L);
            }
            var font = net.minecraft.client.Minecraft.getInstance().font;
            g.pose().pushPose();
            g.pose().translate(getX() + width / 2f, getY() + height / 2f, 0);
            g.pose().scale(1.25f, 1.25f, 1f);
            g.drawCenteredString(font, getMessage(), 0, -4, hover ? 0xFFFFE6A0 : GolemGui.PARCHMENT);
            g.pose().popPose();
        }
    }
}
