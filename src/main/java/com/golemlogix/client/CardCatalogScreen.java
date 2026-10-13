package com.golemlogix.client;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.CardCatalogMenu;
import com.golemlogix.network.MenuScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Punch Card Catalog window: a scrolling row of punch cards with a scroll bar and search by recipe result.
 * Places beyond the capacity are locked (a grate); on the right are the Redstone Module and the Pattern Provider slot.
 */
public class CardCatalogScreen extends GolemScreen<CardCatalogMenu> {
    private static final ItemStack PROVIDER = new ItemStack(ModBlocks.PATTERN_PROVIDER.get());
    private EditBox search;
    private int row = 0;
    private boolean dragging = false;

    public CardCatalogScreen(CardCatalogMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, CardCatalogMenu.IMAGE_WIDTH, CardCatalogMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        addRedstoneButton(menu::redstoneMode);
        String old = search != null ? search.getValue() : "";
        search = new EditBox(font, leftPos + 104, topPos + CardCatalogMenu.INFO_Y + 2, 72, 10, Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(GolemGui.PARCHMENT);
        search.setHint(Component.translatable("gui.golem_logix.search").withStyle(ChatFormatting.DARK_GRAY));
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
        // the capacity may have shrunk (providers were taken out): the server already fixed the row, follow it
        if (row > menu.maxRow()) {
            row = menu.maxRow();
        }
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int gx = leftPos + CardCatalogMenu.GRID_X, gy = topPos + CardCatalogMenu.GRID_Y;
        GolemGui.inset(g, gx - 3, gy - 3, CardCatalogMenu.COLS * 18 + 4, CardCatalogMenu.ROWS * 18 + 4);
        // scroll bar
        int sx = leftPos + CardCatalogMenu.SCROLL_X, h = CardCatalogMenu.ROWS * 18;
        GolemGui.inset(g, sx - 1, gy - 1, 8, h + 2);
        int max = menu.maxRow();
        if (max > 0) {
            int handle = Math.max(12, h * CardCatalogMenu.ROWS / menu.totalRows());
            int hy = gy + (h - handle) * Math.min(row, max) / max;
            g.fill(sx, hy, sx + 6, hy + handle, GolemGui.BRASS);
            g.fill(sx, hy, sx + 6, hy + 1, GolemGui.BRASS_L);
            g.fill(sx, hy + handle - 1, sx + 6, hy + handle, GolemGui.BRASS_DARK);
        } else {
            g.fill(sx, gy, sx + 6, gy + h, GolemGui.BRASS_DARK);
        }
        // search field
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
            g.renderItem(PROVIDER, x, y);
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
            int idx = menu.slots.indexOf(hoveredSlot);
            if (idx == menu.providerSlot()) {
                List<Component> lines = new java.util.ArrayList<>();
                lines.add(Component.translatable("gui.golem_logix.catalog.providers", menu.providers(), CardCatalogBlockEntity.MAX_PROVIDERS));
                lines.add(Component.translatable("gui.golem_logix.catalog.providers.hint", CardCatalogBlockEntity.slotsPerProvider())
                        .withStyle(ChatFormatting.GRAY));
                if (hoveredSlot.hasItem()) {
                    lines.add(0, hoveredSlot.getItem().getHoverName());
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
            if (hoveredSlot instanceof CardCatalogMenu.CardSlot s && s.locked() && !s.hasItem()) {
                g.renderComponentTooltip(font, List.of(Component.translatable(menu.searching()
                        ? "gui.golem_logix.catalog.not_found" : "gui.golem_logix.catalog.locked").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    private boolean overScrollbar(double mx, double my) {
        int sx = leftPos + CardCatalogMenu.SCROLL_X, gy = topPos + CardCatalogMenu.GRID_Y;
        return mx >= sx - 1 && mx < sx + 7 && my >= gy && my < gy + CardCatalogMenu.ROWS * 18;
    }

    private void drag(double my) {
        int gy = topPos + CardCatalogMenu.GRID_Y, h = CardCatalogMenu.ROWS * 18;
        double f = (my - gy) / h;
        scrollTo((int) Math.round(Math.max(0, Math.min(1, f)) * menu.maxRow()));
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
        int gx = leftPos + CardCatalogMenu.GRID_X, gy = topPos + CardCatalogMenu.GRID_Y;
        if (mx >= gx - 3 && mx < leftPos + CardCatalogMenu.SCROLL_X + 8 && my >= gy - 3 && my < gy + CardCatalogMenu.ROWS * 18 + 3) {
            scrollTo(row - (int) Math.signum(sy) * (hasShiftDown() ? CardCatalogMenu.ROWS : 1));
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
            scrollTo(row + (key == GLFW.GLFW_KEY_PAGE_DOWN ? CardCatalogMenu.ROWS : -CardCatalogMenu.ROWS));
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
