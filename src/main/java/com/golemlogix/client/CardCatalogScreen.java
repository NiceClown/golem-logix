package com.golemlogix.client;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.CardCatalogMenu;
import com.golemlogix.network.MenuScrollPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new MenuScrollPayload(menu.containerId, row, search.getValue()));
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
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
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
    protected void renderOverlay(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
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
            g.item(PROVIDER, x, y);
            g.pose().pushMatrix();
            g.pose().translate(0, 0);
            g.fill(x, y, x + 16, y + 16, 0xA8241A12);
            g.pose().popMatrix();
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int y = CardCatalogMenu.INFO_Y + 3;
        Component info = Component.translatable("gui.golem_logix.catalog.count", menu.cardCount(), menu.capacity());
        g.text(font, info, 8, y, menu.capacity() == 0 ? GolemGui.RED : GolemGui.CYAN, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
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
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
                return;
            }
            if (hoveredSlot instanceof CardCatalogMenu.CardSlot s && s.locked() && !s.hasItem()) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable(menu.searching()
                        ? "gui.golem_logix.catalog.not_found" : "gui.golem_logix.catalog.locked").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(g, mouseX, mouseY);
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (button == 0 && overScrollbar(mx, my) && menu.maxRow() > 0) {
            dragging = true;
            drag(my);
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
        double mx = event.x(), my = event.y();
        int button = event.button();
        if (dragging) {
            drag(my);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int gx = leftPos + CardCatalogMenu.GRID_X, gy = topPos + CardCatalogMenu.GRID_Y;
        if (mx >= gx - 3 && mx < leftPos + CardCatalogMenu.SCROLL_X + 8 && my >= gy - 3 && my < gy + CardCatalogMenu.ROWS * 18 + 3) {
            scrollTo(row - (int) Math.signum(sy) * (net.minecraft.client.Minecraft.getInstance().hasShiftDown() ? CardCatalogMenu.ROWS : 1));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key(), scanCode = event.scancode(), modifiers = event.modifiers();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event);
        }
        if (search.isFocused()) {
            search.keyPressed(event);
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
            scrollTo(row + (key == GLFW.GLFW_KEY_PAGE_DOWN ? CardCatalogMenu.ROWS : -CardCatalogMenu.ROWS));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        char c = (char) event.codepoint();
        if (search.isFocused()) {
            return search.charTyped(event);
        }
        return super.charTyped(event);
    }
}
