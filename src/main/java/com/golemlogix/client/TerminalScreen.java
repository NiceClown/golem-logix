package com.golemlogix.client;

import com.golemlogix.client.book.BookScreen;
import com.golemlogix.config.GolemClientConfig;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalActionPayload.Action;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Terminal screen: search, sorting, scrolling; the "Crafting" variant also has a 3x3 grid. The window height follows the "Size" button:
 * normal, tall or full screen (more list rows). In the Portable Chronometer, to the right of the grid there is a charge gauge.
 * By default items and fluids are in one list (combined mode): fluid is counted in buckets (1000 mB = "one item").
 *
 * Filtering/sorting/metadata caching live in {@link TerminalCatalog}; status bar, charge gauge, and tooltips in {@link TerminalHud}.
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    /** Ingredient under the cursor for JEI (R and U keys): an ItemStack or FluidStack and its place on the screen. */
    public record Hit(Object ingredient, Rect2i area) {
    }

    private final boolean crafting;
    private final TerminalCatalog catalog = new TerminalCatalog();
    private final TerminalHud hud = new TerminalHud();

    private EditBox search;
    /** Last JEI search line seen: if it changed in JEI, it is picked up here. */
    private String lastJei;
    private int scroll = 0;
    private boolean draggingScroll = false;
    private List<Rect2i> cachedExtraAreas = List.of();
    private String truncatedTitle = "";

    public TerminalScreen(TerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.crafting = menu.crafting;
        this.imageWidth = TerminalMenu.WIDTH;
        this.imageHeight = TerminalMenu.imageHeight(crafting, menu.rows);
    }

    private int rows() {
        return menu.rows;
    }

    /** How many list rows to show for the selected size and screen height. */
    private int wantedRows() {
        int base = TerminalMenu.defaultRows(crafting);
        int fixed = TerminalMenu.imageHeight(crafting, 0);
        int fit = Math.max(base, (height - 6 - fixed) / 18);
        return switch (TerminalCatalog.sizeMode()) {
            case NORMAL -> base;
            case TALL -> Math.min(fit, base + 4);
            case FULL -> fit;
        };
    }

    @Override
    protected void init() {
        catalog.clearCaches();
        menu.relayout(wantedRows());
        this.imageHeight = TerminalMenu.imageHeight(crafting, rows());
        super.init();
        this.titleLabelX = TerminalMenu.LIST_X;
        this.titleLabelY = 6;
        this.inventoryLabelX = TerminalMenu.LIST_X;
        this.inventoryLabelY = TerminalMenu.invY(crafting, rows()) - 11;
        this.cachedExtraAreas = List.of(new Rect2i(leftPos + imageWidth, topPos + 2, 40, 110));
        this.truncatedTitle = font.plainSubstrByWidth(title.getString(), 82);

        initSearchBox();
        initSideButtons();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private void initSearchBox() {
        search = new EditBox(font, leftPos + 97, topPos + 8, 74, 12, Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(GolemGui.PARCHMENT);
        search.setHint(Component.translatable("gui.golem_logix.search").withStyle(ChatFormatting.DARK_GRAY));
        // search is shared with JEI: on open the JEI line is taken (if JEI is there), what is typed here goes to JEI
        // the mode is chosen with the "JEI" button on the right: no link, only to JEI, both ways
        String jei = TerminalCatalog.jeiSync().readsFromJei() ? ClientHooks.jeiGetSearch.get() : null;
        search.setValue(jei != null ? jei : TerminalCatalog.lastSearch());
        lastJei = jei;
        search.setResponder(s -> {
            TerminalCatalog.setLastSearch(s);
            catalog.markDirty();
            scroll = 0;
            lastJei = s;
            if (TerminalCatalog.jeiSync().sendsToJei()) {
                ClientHooks.jeiSetSearch.accept(s);
            }
        });
        addRenderableWidget(search);
    }

    private void initSideButtons() {
        int bx = leftPos + imageWidth + 2;
        int by = topPos + 4;
        addRenderableWidget(new GolemButton(bx, by, 36, 16,
                () -> Component.translatable(TerminalCatalog.sortMode().langKey()),
                () -> Component.translatable("gui.golem_logix.sort.tooltip"), () -> false,
                b -> {
                    TerminalCatalog.cycleSort();
                    catalog.markDirty();
                }));
        addRenderableWidget(new GolemButton(bx, by + 18, 36, 16,
                () -> Component.translatable(TerminalCatalog.reverse() ? "gui.golem_logix.dir.desc" : "gui.golem_logix.dir.asc"),
                () -> Component.translatable("gui.golem_logix.dir.tooltip"), () -> false,
                b -> {
                    TerminalCatalog.toggleReverse();
                    catalog.markDirty();
                }));
        addRenderableWidget(new GolemButton(bx, by + 36, 36, 16,
                () -> Component.translatable(TerminalCatalog.viewMode().langKey()),
                () -> Component.translatable("gui.golem_logix.view.tooltip"),
                () -> TerminalCatalog.viewMode() != TerminalCatalog.ViewMode.ALL,
                b -> {
                    TerminalCatalog.cycleView();
                    catalog.markDirty();
                    scroll = 0;
                }));
        addRenderableWidget(new GolemButton(bx, by + 54, 36, 16,
                () -> Component.translatable(TerminalCatalog.craftableOnly() ? "gui.golem_logix.filter.craft" : "gui.golem_logix.filter.all"),
                () -> Component.translatable("gui.golem_logix.filter.tooltip"), TerminalCatalog::craftableOnly,
                b -> {
                    TerminalCatalog.toggleCraftableOnly();
                    catalog.markDirty();
                    scroll = 0;
                }));
        addRenderableWidget(new GolemButton(bx, by + 72, 36, 16,
                () -> Component.translatable("gui.golem_logix.size." + TerminalCatalog.sizeMode().name().toLowerCase(Locale.ROOT)),
                () -> Component.translatable("gui.golem_logix.size.tooltip"),
                () -> TerminalCatalog.sizeMode() != GolemClientConfig.TerminalSize.NORMAL,
                b -> {
                    TerminalCatalog.setSizeMode(TerminalCatalog.sizeMode().next());
                    rebuildWidgets();
                }));
        // search sync with JEI (the button is visible only when JEI is installed)
        if (ClientHooks.jeiGetSearch.get() != null) {
            addRenderableWidget(new GolemButton(bx, by + 90, 36, 16,
                    () -> Component.translatable("gui.golem_logix.jei_sync." + TerminalCatalog.jeiSync().name().toLowerCase(Locale.ROOT)),
                    () -> Component.translatable("gui.golem_logix.jei_sync.tooltip"),
                    () -> TerminalCatalog.jeiSync() != GolemClientConfig.JeiSync.OFF,
                    b -> {
                        TerminalCatalog.setJeiSync(TerminalCatalog.jeiSync().next());
                        if (TerminalCatalog.jeiSync().sendsToJei()) {
                            ClientHooks.jeiSetSearch.accept(search.getValue());
                            lastJei = search.getValue();
                        }
                    }));
        }
        if (crafting) {
            int cy = topPos + TerminalMenu.craftY(rows());
            addRenderableWidget(new GolemButton(leftPos + TerminalMenu.LIST_X, cy + 36, 18, 16,
                    () -> Component.literal("v"),
                    () -> Component.translatable("gui.golem_logix.clear_grid"), () -> false,
                    b -> {
                        if (minecraft != null && minecraft.gameMode != null) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                        }
                    }));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        String jei = TerminalCatalog.jeiSync().readsFromJei() ? ClientHooks.jeiGetSearch.get() : null;
        if (jei != null && !jei.equals(lastJei) && search != null && !jei.equals(search.getValue())) {
            lastJei = jei;
            search.setValue(jei);
        }
    }

    /** For rig checks: index of a list row by part of its name (-1 if there is none). */
    public int indexOfName(String part) {
        return catalog.indexOfName(menu, search != null ? search.getValue() : "", minecraft, part);
    }

    private int maxScroll() {
        return Math.max(0, (catalog.size() + TerminalMenu.COLS - 1) / TerminalMenu.COLS - rows());
    }

    /** The sort and view buttons sit to the right of the window: JEI must avoid them, not cover them with its panel. */
    public List<Rect2i> extraAreas() {
        return cachedExtraAreas;
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        for (Rect2i r : cachedExtraAreas) {
            if (r.contains((int) mouseX, (int) mouseY)) {
                return false;
            }
        }
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        if (catalog.ensureBuilt(menu, search != null ? search.getValue() : "", minecraft)) {
            scroll = Math.max(0, Math.min(scroll, maxScroll()));
        }
        GolemGui.panel(g, leftPos, topPos, imageWidth, imageHeight);
        GolemGui.inset(g, leftPos + 91, topPos + 5, 80, 15);

        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < TerminalMenu.COLS; c++) {
                GolemGui.slot(g, lx + c * 18, ly + r * 18);
            }
        }
        renderScrollbar(g, lx + TerminalMenu.COLS * 18 + 3, ly, rows() * 18);
        hud.renderInfo(g, font, menu.clientInfo, TerminalCatalog.viewMode(), lx, topPos + TerminalMenu.infoY(rows()));

        for (Slot slot : menu.slots) {
            GolemGui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        renderLockedChronometerOutline(g);
        if (crafting) {
            int cy = topPos + TerminalMenu.craftY(rows());
            g.drawString(font, "=>", leftPos + 102, cy + 22, GolemGui.CYAN, false);
            if (hasChargeGauge()) {
                TerminalHud.renderChargeGauge(g, minecraft, menu.clientInfo, leftPos + TerminalMenu.GAUGE_X, cy, 54);
            }
        }
        renderEntries(g, mouseX, mouseY);
    }

    private void renderScrollbar(GuiGraphics g, int sbX, int ly, int sbH) {
        g.fill(sbX, ly, sbX + 5, ly + sbH, GolemGui.SLOT_BG);
        int max = maxScroll();
        if (max > 0) {
            int handle = Math.max(10, sbH * rows() / (rows() + max));
            int hy = ly + (sbH - handle) * scroll / max;
            g.fill(sbX, hy, sbX + 5, hy + handle, GolemGui.BRASS);
        } else {
            g.fill(sbX, ly, sbX + 5, ly + sbH, GolemGui.BRASS_DARK);
        }
    }

    private void renderLockedChronometerOutline(GuiGraphics g) {
        // the slot of the Chronometer the window was opened through is locked: a teal frame outlines it
        if (menu.lockedMenuSlot() >= 0 && menu.lockedMenuSlot() < menu.slots.size()) {
            Slot lock = menu.slots.get(menu.lockedMenuSlot());
            int x = leftPos + lock.x - 1, y = topPos + lock.y - 1;
            g.fill(x, y, x + 18, y + 1, GolemGui.CYAN);
            g.fill(x, y + 17, x + 18, y + 18, GolemGui.CYAN);
            g.fill(x, y, x + 1, y + 18, GolemGui.CYAN);
            g.fill(x + 17, y, x + 18, y + 18, GolemGui.CYAN);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x5038D9D6);
        }
    }

    /** For the Chronometer (if Source is needed) a charge gauge to the right of the result. */
    private boolean hasChargeGauge() {
        return menu.wireless && crafting && menu.clientInfo != null && menu.clientInfo.charge() >= 0;
    }

    private void renderEntries(GuiGraphics g, int mouseX, int mouseY) {
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        int hovered = hoveredIndex(mouseX, mouseY);
        TerminalCatalog.ViewMode mode = TerminalCatalog.viewMode();

        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < TerminalMenu.COLS; c++) {
                int idx = (scroll + r) * TerminalMenu.COLS + c;
                if (idx >= catalog.size()) {
                    return;
                }
                int x = lx + c * 18 + 1;
                int y = ly + r * 18 + 1;
                TerminalCatalog.Row row = catalog.get(idx);
                if (row.isFluid()) {
                    drawFluid(g, row.fluid(), x, y);
                    TerminalHud.drawSlotDrop(g, mode, x, y);
                    drawCount(g, row.countText(), x, y, row.count() <= 0 ? GolemGui.CYAN : 0xFFFFFFFF);
                    if (row.craftable() && row.count() > 0) {
                        g.fill(x - 1, y - 1, x + 3, y + 3, GolemGui.CYAN);
                    }
                } else {
                    g.renderItem(row.stack(), x, y);
                    drawCount(g, row.countText(), x, y, row.count() > 0 ? 0xFFFFFFFF : GolemGui.CYAN);
                    if (row.craftable() && row.count() > 0) {
                        g.fill(x - 1, y - 1, x + 3, y + 3, GolemGui.CYAN);
                    }
                    if (row.gear()) {
                        // a yellow exclamation mark in the top right corner: see overGearMark
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        g.fill(x + 11, y - 1, x + 17, y + 8, 0xE0201808);
                        g.fill(x + 13, y, x + 15, y + 4, 0xFFFFD83A);
                        g.fill(x + 13, y + 5, x + 15, y + 7, 0xFFFFD83A);
                        g.pose().popPose();
                    }
                }
                if (idx == hovered) {
                    g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                }
            }
        }
    }

    private void drawCount(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.drawString(font, text, (x + 17) * 2 - font.width(text), (y + 11) * 2, color, true);
        g.pose().popPose();
    }

    private void drawFluid(GuiGraphics g, FluidStack fluid, int x, int y) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation tex = ext.getStillTexture(fluid);
        if (tex == null) {
            return;
        }
        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
        int tint = ext.getTintColor(fluid);
        float a = ((tint >> 24) & 0xFF) / 255f;
        float r = ((tint >> 16) & 0xFF) / 255f;
        float gr = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        g.blit(x, y, 0, 16, 16, sprite, r, gr, b, a == 0 ? 1f : a);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, truncatedTitle, titleLabelX, titleLabelY, GolemGui.PARCHMENT, false);
        if (!crafting) {
            g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GolemGui.PARCHMENT, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        int idx = hoveredIndex(mouseX, mouseY);
        TerminalCatalog.Row hovered = idx >= 0 ? catalog.get(idx) : null;
        TerminalHud.renderTooltips(g, font, minecraft, menu, hovered, overGearMark(mouseX, mouseY),
                hasChargeGauge(), leftPos, topPos, rows(), mouseX, mouseY);
    }

    // ------------------------------------------------------------------ hit testing & JEI

    /** Whether the cursor is on the exclamation mark of a piece of equipment in the list. */
    private boolean overGearMark(double mouseX, double mouseY) {
        int idx = hoveredIndex(mouseX, mouseY);
        if (idx < 0 || !catalog.get(idx).gear()) {
            return false;
        }
        double cx = (mouseX - (leftPos + TerminalMenu.LIST_X)) % 18, cy = (mouseY - (topPos + TerminalMenu.LIST_Y)) % 18;
        return cx >= 11 && cy < 10;
    }

    private int hoveredIndex(double mouseX, double mouseY) {
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        if (mouseX < lx || mouseY < ly || mouseX >= lx + TerminalMenu.COLS * 18 || mouseY >= ly + rows() * 18) {
            return -1;
        }
        int c = (int) ((mouseX - lx) / 18);
        int r = (int) ((mouseY - ly) / 18);
        int idx = (scroll + r) * TerminalMenu.COLS + c;
        return idx < catalog.size() ? idx : -1;
    }

    /** For JEI (R and U keys, bookmarks): what is in the list slot under the cursor. */
    @Nullable
    public Hit ingredientAt(double mouseX, double mouseY) {
        int idx = hoveredIndex(mouseX, mouseY);
        if (idx < 0 || idx >= catalog.size()) {
            return null;
        }
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        int c = (int) ((mouseX - lx) / 18);
        int r = (int) ((mouseY - ly) / 18);
        Rect2i area = new Rect2i(lx + c * 18 + 1, ly + r * 18 + 1, 16, 16);
        TerminalCatalog.Row row = catalog.get(idx);
        return new Hit(row.isFluid() ? row.fluid() : row.stack(), area);
    }

    private boolean overList(double mouseX, double mouseY) {
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        return mouseX >= lx && mouseY >= ly && mouseX < lx + TerminalMenu.COLS * 18 && mouseY < ly + rows() * 18;
    }

    // ------------------------------------------------------------------ input

    private void send(Action action, ItemStack key, FluidStack fluid) {
        PacketDistributor.sendToServer(new TerminalActionPayload(menu.containerId, action, key, fluid));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // a right-click on the search field clears it
        if (button == 1 && search.isMouseOver(mouseX, mouseY)) {
            search.setValue("");
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        // scroll bar
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        int sbX = lx + TerminalMenu.COLS * 18 + 3;
        if (button == 0 && mouseX >= sbX && mouseX < sbX + 5 && mouseY >= ly && mouseY < ly + rows() * 18) {
            draggingScroll = true;
            dragScroll(mouseY);
            return true;
        }
        if (button == 0 && menu.getCarried().isEmpty() && overGearMark(mouseX, mouseY)) {
            // close the terminal first (as Escape would) so wireless charge stops and inventory clicks remain valid
            if (minecraft != null) {
                if (minecraft.player != null) {
                    minecraft.player.closeContainer();
                }
                minecraft.setScreen(BookScreen.openAt("st_gear"));
            }
            return true;
        }
        if (overList(mouseX, mouseY) && handleListClick(mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!search.isMouseOver(mouseX, mouseY) && search.isFocused() && button == 0) {
            search.setFocused(false);
        }
        return handled;
    }

    private boolean handleListClick(double mouseX, double mouseY, int button) {
        ItemStack carried = menu.getCarried();
        int idx = hoveredIndex(mouseX, mouseY);
        TerminalCatalog.Row row = idx >= 0 ? catalog.get(idx) : null;

        IFluidHandlerItem handler = carried.isEmpty() ? null : carried.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
        boolean fullContainer = handler != null && !handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty();

        if (TerminalCatalog.viewMode() == TerminalCatalog.ViewMode.FLUIDS && !carried.isEmpty()) {
            // in the fluid list any click with a container: a full one empties into the network, an empty one takes the selected fluid
            if (handler == null) {
                return true;
            }
            if (fullContainer) {
                send(Action.FLUID_DRAIN_CARRIED, ItemStack.EMPTY, FluidStack.EMPTY);
            } else if (row != null) {
                send(Action.FLUID_FILL_CARRIED, ItemStack.EMPTY, row.fluid());
            }
            return true;
        }
        if (!carried.isEmpty()) {
            if (row != null && row.isFluid() && handler != null) {
                if (fullContainer) {
                    send(Action.FLUID_DRAIN_CARRIED, ItemStack.EMPTY, FluidStack.EMPTY);
                } else {
                    send(Action.FLUID_FILL_CARRIED, ItemStack.EMPTY, row.fluid());
                }
                return true;
            }
            if (button == 1 && fullContainer) {
                send(Action.FLUID_DRAIN_CARRIED, ItemStack.EMPTY, FluidStack.EMPTY);
                return true;
            }
            send(button == 1 ? Action.INSERT_ONE : Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY);
            return true;
        }
        if (row == null) {
            return true;
        }
        boolean ctrl = hasControlDown();
        if (row.isFluid()) {
            if (row.craftable() && (ctrl || row.count() <= 0) && button == 0) {
                if (minecraft != null) {
                    minecraft.setScreen(new CraftRequestScreen(this, menu, row.stack()));
                }
            } else if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("gui.golem_logix.fluid_need_container"), true);
            }
            return true;
        }
        if (row.craftable() && (ctrl || row.count() <= 0) && button == 0) {
            if (minecraft != null) {
                minecraft.setScreen(new CraftRequestScreen(this, menu, row.stack()));
            }
            return true;
        }
        if (row.count() <= 0) {
            return true;
        }
        ItemStack key = TerminalMenu.keyOf(row.stack());
        if (button == 0) {
            send(hasShiftDown() ? Action.TO_INVENTORY : Action.TAKE_STACK, key, FluidStack.EMPTY);
        } else if (button == 1) {
            send(Action.TAKE_HALF, key, FluidStack.EMPTY);
        } else if (button == 2) {
            send(Action.TAKE_ONE, key, FluidStack.EMPTY);
        }
        return true;
    }

    private void dragScroll(double mouseY) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int ly = topPos + TerminalMenu.LIST_Y;
        double f = (mouseY - ly) / (rows() * 18.0);
        scroll = (int) Math.round(Math.max(0, Math.min(1, f)) * max);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingScroll) {
            dragScroll(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mouseX >= leftPos && mouseX < leftPos + imageWidth
                && mouseY >= topPos + TerminalMenu.LIST_Y && mouseY < topPos + TerminalMenu.LIST_Y + rows() * 18) {
            int max = maxScroll();
            if (max > 0) {
                scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (key == GLFW.GLFW_KEY_TAB || (key == GLFW.GLFW_KEY_SLASH && !search.isFocused())) {
            search.setFocused(true);
            setFocused(search);
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
