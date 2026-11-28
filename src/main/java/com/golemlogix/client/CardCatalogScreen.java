package com.golemlogix.client;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.CardCatalogMenu;
import com.google.common.base.Suppliers;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Punch Card Catalog window: a scrolling row of punch cards with a scroll bar and search by recipe result.
 * Places beyond the capacity are locked (a grate); on the right are the Redstone Module and the Pattern Provider slot.
 */
public class CardCatalogScreen extends ScrollSearchScreen<CardCatalogMenu> {
    private static final Supplier<ItemStack> PROVIDER = Suppliers.memoize(() -> new ItemStack(ModBlocks.PATTERN_PROVIDER.get()));
    private static final List<Component> TIP_NOT_FOUND = List.of(
            Component.translatable("gui.golem_logix.catalog.not_found").withStyle(ChatFormatting.GRAY));
    private static final List<Component> TIP_LOCKED = List.of(
            Component.translatable("gui.golem_logix.catalog.locked").withStyle(ChatFormatting.GRAY));

    public CardCatalogScreen(CardCatalogMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, CardCatalogMenu.IMAGE_WIDTH, CardCatalogMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRedstoneButton(menu::redstoneMode);
        initSearchBox(104, CardCatalogMenu.INFO_Y + 2, 72, "gui.golem_logix.search");
    }

    @Override
    protected int visibleRows() {
        return CardCatalogMenu.ROWS;
    }

    @Override
    protected int maxRow() {
        return menu.maxRow();
    }

    @Override
    protected int totalRows() {
        return menu.totalRows();
    }

    @Override
    protected int scrollBarX() {
        return CardCatalogMenu.SCROLL_X;
    }

    @Override
    protected int gridY() {
        return CardCatalogMenu.GRID_Y;
    }

    @Override
    protected boolean isOverScrollArea(double mx, double my) {
        int gx = leftPos + CardCatalogMenu.GRID_X, gy = topPos + CardCatalogMenu.GRID_Y;
        return mx >= gx - 3 && mx < leftPos + CardCatalogMenu.SCROLL_X + 8
                && my >= gy - 3 && my < gy + CardCatalogMenu.ROWS * 18 + 3;
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int gx = leftPos + CardCatalogMenu.GRID_X, gy = topPos + CardCatalogMenu.GRID_Y;
        GolemGui.inset(g, gx - 3, gy - 3, CardCatalogMenu.COLS * 18 + 4, CardCatalogMenu.ROWS * 18 + 4);
        drawScrollbar(g, row);
        GolemGui.inset(g, leftPos + 101, topPos + CardCatalogMenu.INFO_Y, 80, 13);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        for (int i = 0; i < CardCatalogMenu.WINDOW; i++) {
            if (menu.slots.get(i) instanceof CardCatalogMenu.CardSlot s && s.locked() && !s.hasItem()) {
                int x = leftPos + s.x, y = topPos + s.y;
                g.fill(x - 1, y - 1, x + 17, y + 17, 0xE0120B07);
                // grate of a locked place
                for (int k = 2; k < 16; k += 5) {
                    g.fill(x + k, y + 1, x + k + 1, y + 15, 0xFF4A3322);
                }
                g.fill(x + 1, y + 7, x + 15, y + 8, 0xFF5A4230);
            }
        }
        Slot p = menu.slots.get(menu.providerSlot());
        if (!p.hasItem()) {
            int x = leftPos + p.x, y = topPos + p.y;
            g.renderItem(PROVIDER.get(), x, y);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x, y, x + 16, y + 16, 0xA8241A12);
            g.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int y = CardCatalogMenu.INFO_Y + 3;
        Component info = Component.translatable("gui.golem_logix.catalog.count", menu.cardCount(), menu.capacity());
        g.drawString(font, info, 8, y, menu.capacity() == 0 ? GolemGui.RED : GolemGui.CYAN, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (hoveredSlot != null && menu.getCarried().isEmpty()) {
            int idx = hoveredSlot.index;
            if (idx == menu.providerSlot()) {
                List<Component> lines = new ArrayList<>(3);
                if (hoveredSlot.hasItem()) {
                    lines.add(hoveredSlot.getItem().getHoverName());
                }
                lines.add(Component.translatable("gui.golem_logix.catalog.providers", menu.providers(), CardCatalogBlockEntity.MAX_PROVIDERS));
                lines.add(Component.translatable("gui.golem_logix.catalog.providers.hint", CardCatalogBlockEntity.slotsPerProvider())
                        .withStyle(ChatFormatting.GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
            if (hoveredSlot instanceof CardCatalogMenu.CardSlot s && s.locked() && !s.hasItem()) {
                g.renderComponentTooltip(font, menu.searching() ? TIP_NOT_FOUND : TIP_LOCKED, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}
