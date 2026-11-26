package com.golemlogix.client;

import com.golemlogix.menu.ConcentratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Cryo Schematic Concentrator window: the network's providers by category (the header is the machine's icon and name, the rows are the
 * provider's icon and its nine slots), a scroll bar and search by punch card results.
 */
public class ConcentratorScreen extends ScrollSearchScreen<ConcentratorMenu> {
    private static final Component LABEL_EMPTY = Component.translatable("gui.golem_logix.concentrator.empty");
    private static final Component LABEL_NOT_FOUND = Component.translatable("gui.golem_logix.catalog.not_found");

    public ConcentratorScreen(ConcentratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, ConcentratorMenu.IMAGE_WIDTH, ConcentratorMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        initSearchBox(ConcentratorMenu.GRID_X + 2, ConcentratorMenu.INFO_Y + 2, 158, "gui.golem_logix.concentrator.search");
    }

    @Override
    protected int visibleRows() {
        return ConcentratorMenu.ROWS;
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
        return ConcentratorMenu.SCROLL_X;
    }

    @Override
    protected int gridY() {
        return ConcentratorMenu.GRID_Y;
    }

    @Override
    protected boolean isOverScrollArea(double mx, double my) {
        int gy = topPos + ConcentratorMenu.GRID_Y;
        return mx >= leftPos && mx < leftPos + imageWidth && my >= gy - 3 && my < gy + ConcentratorMenu.ROWS * 18 + 3;
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int gx = leftPos + ConcentratorMenu.GRID_X, gy = topPos + ConcentratorMenu.GRID_Y, h = ConcentratorMenu.ROWS * 18;
        GolemGui.inset(g, leftPos + ConcentratorMenu.ICON_X - 3, gy - 3, gx - leftPos - ConcentratorMenu.ICON_X + 9 * 18 + 6, h + 4);
        drawScrollbar(g, menu.row());
        GolemGui.inset(g, gx - 1, topPos + ConcentratorMenu.INFO_Y, 9 * 18 + 2, 13);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        List<ConcentratorMenu.RowInfo> rows = menu.clientRows();
        int gx = leftPos + ConcentratorMenu.GRID_X, gy = topPos + ConcentratorMenu.GRID_Y;
        for (int r = 0; r < ConcentratorMenu.ROWS; r++) {
            int idx = menu.row() + r;
            if (idx < 0 || idx >= rows.size()) {
                break;
            }
            ConcentratorMenu.RowInfo info = rows.get(idx);
            int y = gy + r * 18;
            if (info.header()) {
                // category header: a brass plate across the row, the machine's icon and its name
                int x0 = leftPos + ConcentratorMenu.ICON_X - 2;
                g.fill(x0, y + 1, gx + 9 * 18 - 1, y + 17, GolemGui.BRASS_DARK);
                g.fill(x0, y + 1, gx + 9 * 18 - 1, y + 2, GolemGui.BRASS);
                g.fill(x0, y + 16, gx + 9 * 18 - 1, y + 17, GolemGui.WOOD_DARK);
                g.renderItem(info.icon(), leftPos + ConcentratorMenu.ICON_X, y + 1);
                g.drawString(font, font.plainSubstrByWidth(info.title().getString(), 9 * 18 - 8), gx + 2, y + 5, GolemGui.PARCHMENT, false);
            } else if (!info.icon().isEmpty()) {
                g.renderItem(info.icon(), leftPos + ConcentratorMenu.ICON_X, y + 1);
            }
        }
        if (rows.isEmpty()) {
            g.drawCenteredString(font, search.getValue().isBlank() ? LABEL_EMPTY : LABEL_NOT_FOUND, gx + 81, gy + 50, 0xFF8A7A60);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int ix = leftPos + ConcentratorMenu.ICON_X, gy = topPos + ConcentratorMenu.GRID_Y;
        if (menu.getCarried().isEmpty() && mouseX >= ix - 1 && mouseX < ix + 17 && mouseY >= gy && mouseY < gy + ConcentratorMenu.ROWS * 18) {
            int idx = menu.row() + (mouseY - gy) / 18;
            if (idx >= 0 && idx < menu.clientRows().size()) {
                ConcentratorMenu.RowInfo info = menu.clientRows().get(idx);
                if (!info.icon().isEmpty()) {
                    g.renderTooltip(font, info.title(), mouseX, mouseY);
                    return;
                }
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }
}
