package com.golemlogix.client;

import com.golemlogix.menu.ConcentratorMenu;
import com.golemlogix.network.MenuScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Cryo Schematic Concentrator window: the network's providers by category (the header is the machine's icon and name, the rows are the
 * provider's icon and its nine slots), a scroll bar and search by punch card results.
 */
public class ConcentratorScreen extends GolemScreen<ConcentratorMenu> {
    private EditBox search;
    private int row = 0;
    private boolean dragging = false;

    public ConcentratorScreen(ConcentratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, ConcentratorMenu.IMAGE_WIDTH, ConcentratorMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        String old = search != null ? search.getValue() : "";
        search = new EditBox(font, leftPos + ConcentratorMenu.GRID_X + 2, topPos + ConcentratorMenu.INFO_Y + 2, 158, 10,
                Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(GolemGui.PARCHMENT);
        search.setHint(Component.translatable("gui.golem_logix.concentrator.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(old);
        search.setResponder(s -> {
            row = 0;
            send();
        });
        addRenderableWidget(search);
    }

    private void send() {
        PacketDistributor.sendToServer(new MenuScrollPayload(menu.containerId, row, search.getValue()));
    }

    private void scrollTo(int r) {
        int n = Math.max(0, Math.min(menu.maxRow(), r));
        if (n != row) {
            row = n;
            send();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (row > menu.maxRow()) {
            row = menu.maxRow();
        }
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int gx = leftPos + ConcentratorMenu.GRID_X, gy = topPos + ConcentratorMenu.GRID_Y, h = ConcentratorMenu.ROWS * 18;
        GolemGui.inset(g, leftPos + ConcentratorMenu.ICON_X - 3, gy - 3, gx - leftPos - ConcentratorMenu.ICON_X + 9 * 18 + 6, h + 4);
        int sx = leftPos + ConcentratorMenu.SCROLL_X;
        GolemGui.inset(g, sx - 1, gy - 1, 8, h + 2);
        int max = menu.maxRow();
        if (max > 0) {
            int handle = Math.max(12, h * ConcentratorMenu.ROWS / menu.totalRows());
            int hy = gy + (h - handle) * Math.min(menu.row(), max) / max;
            g.fill(sx, hy, sx + 6, hy + handle, GolemGui.BRASS);
            g.fill(sx, hy, sx + 6, hy + 1, GolemGui.BRASS_L);
            g.fill(sx, hy + handle - 1, sx + 6, hy + handle, GolemGui.BRASS_DARK);
        } else {
            g.fill(sx, gy, sx + 6, gy + h, GolemGui.BRASS_DARK);
        }
        GolemGui.inset(g, gx - 1, topPos + ConcentratorMenu.INFO_Y, 9 * 18 + 2, 13);
    }

    @Override
    protected void renderOverlay(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        List<ConcentratorMenu.RowInfo> rows = menu.clientRows();
        int gx = leftPos + ConcentratorMenu.GRID_X, gy = topPos + ConcentratorMenu.GRID_Y;
        for (int r = 0; r < ConcentratorMenu.ROWS; r++) {
            int idx = menu.row() + r;
            if (idx >= rows.size()) {
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
            Component empty = Component.translatable(search.getValue().isBlank()
                    ? "gui.golem_logix.concentrator.empty" : "gui.golem_logix.catalog.not_found");
            g.drawCenteredString(font, empty, gx + 81, gy + 50, 0xFF8A7A60);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int ix = leftPos + ConcentratorMenu.ICON_X, gy = topPos + ConcentratorMenu.GRID_Y;
        if (menu.getCarried().isEmpty() && mouseX >= ix - 1 && mouseX < ix + 17 && mouseY >= gy && mouseY < gy + ConcentratorMenu.ROWS * 18) {
            int idx = menu.row() + (mouseY - gy) / 18;
            if (idx < menu.clientRows().size()) {
                ConcentratorMenu.RowInfo info = menu.clientRows().get(idx);
                if (!info.icon().isEmpty()) {
                    g.renderTooltip(font, info.title(), mouseX, mouseY);
                    return;
                }
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    private boolean overScrollbar(double mx, double my) {
        int sx = leftPos + ConcentratorMenu.SCROLL_X, gy = topPos + ConcentratorMenu.GRID_Y;
        return mx >= sx - 1 && mx < sx + 7 && my >= gy && my < gy + ConcentratorMenu.ROWS * 18;
    }

    private void drag(double my) {
        int gy = topPos + ConcentratorMenu.GRID_Y, h = ConcentratorMenu.ROWS * 18;
        scrollTo((int) Math.round(Math.max(0, Math.min(1, (my - gy) / h)) * menu.maxRow()));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overScrollbar(mx, my) && menu.maxRow() > 0) {
            dragging = true;
            drag(my);
            return true;
        }
        if (button == 1 && search.isMouseOver(mx, my)) {
            search.setValue("");
            return true;
        }
        boolean handled = super.mouseClicked(mx, my, button);
        if (!search.isMouseOver(mx, my) && search.isFocused()) {
            search.setFocused(false);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            drag(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int gy = topPos + ConcentratorMenu.GRID_Y;
        if (mx >= leftPos && mx < leftPos + imageWidth && my >= gy - 3 && my < gy + ConcentratorMenu.ROWS * 18 + 3) {
            scrollTo(row - (int) Math.signum(sy) * (hasShiftDown() ? ConcentratorMenu.ROWS : 1));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (search.isFocused()) {
            search.keyPressed(key, scanCode, modifiers);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            scrollTo(row + (key == GLFW.GLFW_KEY_PAGE_DOWN ? ConcentratorMenu.ROWS : -ConcentratorMenu.ROWS));
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (search.isFocused()) {
            return search.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }
}
