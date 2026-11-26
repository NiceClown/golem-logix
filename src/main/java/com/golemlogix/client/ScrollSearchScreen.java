package com.golemlogix.client;

import com.golemlogix.network.MenuScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Shared base for screens with a server-backed scrollable grid and search box
 * ({@link CardCatalogScreen} and {@link ConcentratorScreen}).
 */
abstract class ScrollSearchScreen<T extends AbstractContainerMenu> extends GolemScreen<T> {
    protected EditBox search;
    protected int row = 0;
    private boolean dragging = false;

    protected ScrollSearchScreen(T menu, Inventory inv, Component title, int width, int height) {
        super(menu, inv, title, width, height);
    }

    protected abstract int visibleRows();

    protected abstract int maxRow();

    protected abstract int totalRows();

    protected abstract int scrollBarX();

    protected abstract int gridY();

    protected abstract boolean isOverScrollArea(double mx, double my);

    /** Creates or recreates the search box while preserving its text across window resizes. */
    protected void initSearchBox(int relX, int relY, int w, String hintKey) {
        String old = search != null ? search.getValue() : "";
        search = new EditBox(font, leftPos + relX, topPos + relY, w, 10, Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(GolemGui.PARCHMENT);
        search.setHint(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(old);
        search.setResponder(s -> {
            row = 0;
            sendScroll();
        });
        addRenderableWidget(search);
    }

    protected void sendScroll() {
        ClientPacketDistributor.sendToServer(new MenuScrollPayload(menu.containerId, row, search.getValue()));
    }

    protected void scrollTo(int target) {
        int clamped = Math.max(0, Math.min(maxRow(), target));
        if (clamped != row) {
            row = clamped;
            sendScroll();
        }
    }

    /** Renders the sunken track and beveled brass thumb of the grid scrollbar. */
    protected void drawScrollbar(GuiGraphicsExtractor g, int displayRow) {
        int sx = leftPos + scrollBarX();
        int gy = topPos + gridY();
        int h = visibleRows() * 18;
        GolemGui.inset(g, sx - 1, gy - 1, 8, h + 2);
        int max = maxRow();
        if (max > 0) {
            int handle = Math.max(12, h * visibleRows() / Math.max(1, totalRows()));
            int hy = gy + (h - handle) * Math.min(displayRow, max) / max;
            g.fill(sx, hy, sx + 6, hy + handle, GolemGui.BRASS);
            g.fill(sx, hy, sx + 6, hy + 1, GolemGui.BRASS_L);
            g.fill(sx, hy + handle - 1, sx + 6, hy + handle, GolemGui.BRASS_DARK);
        } else {
            g.fill(sx, gy, sx + 6, gy + h, GolemGui.BRASS_DARK);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // capacity or search results may have shrunk on the server: keep local scroll clamped
        if (row > maxRow()) {
            row = maxRow();
        }
    }

    private boolean overScrollbar(double mx, double my) {
        int sx = leftPos + scrollBarX(), gy = topPos + gridY();
        return mx >= sx - 1 && mx < sx + 7 && my >= gy && my < gy + visibleRows() * 18;
    }

    private void dragScrollbar(double my) {
        int gy = topPos + gridY(), h = visibleRows() * 18;
        double f = (my - gy) / h;
        scrollTo((int) Math.round(Math.max(0, Math.min(1, f)) * maxRow()));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (button == 0 && overScrollbar(mx, my) && maxRow() > 0) {
            dragging = true;
            dragScrollbar(my);
            return true;
        }
        if (button == 1 && search.isMouseOver(mx, my)) {
            search.setValue("");
            return true;
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!search.isMouseOver(mx, my) && search.isFocused()) {
            search.setFocused(false);
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && isOverScrollArea(mx, my)) {
            int step = Minecraft.getInstance().hasShiftDown() ? visibleRows() : 1;
            scrollTo(row - (int) Math.signum(sy) * step);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event);
        }
        if (search.isFocused()) {
            search.keyPressed(event);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            scrollTo(row + (key == GLFW.GLFW_KEY_PAGE_DOWN ? visibleRows() : -visibleRows()));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (search.isFocused()) {
            return search.charTyped(event);
        }
        return super.charTyped(event);
    }
}
