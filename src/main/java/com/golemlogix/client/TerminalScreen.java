package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import com.golemlogix.config.GolemClientConfig;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalActionPayload.Action;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Terminal screen: search, sorting, scrolling; the "Crafting" variant also has a 3x3 grid. The window height follows the "Size" button:
 * normal, tall or full screen (more list rows). In the Portable Chronometer, to the right of the grid there is a green screen
 * where a Wither tries to shoot right into the glass, and the Source charge.
 * By default items and fluids are in one list (combined mode): fluid is counted in buckets,
 * that is 1000 mB in the list is "one item".
 */
public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private enum SortMode {NAME, COUNT, MOD}

    /** What the list shows: everything together (the default), only items or only fluids. */
    private enum ViewMode {ALL, ITEMS, FLUIDS}

    private static SortMode sortMode = SortMode.NAME;
    private static boolean reverse = false;
    private static ViewMode viewMode = ViewMode.ALL;
    private static boolean craftableOnly = false;
    private static String lastSearch = "";
    /** Last JEI search line seen: if it changed in JEI, it is picked up here. */
    private String lastJei;

    @Override
    protected void containerTick() {
        super.containerTick();
        String jei = jeiSync().readsFromJei() ? ClientHooks.jeiGetSearch.get() : null;
        if (jei != null && !jei.equals(lastJei) && search != null && !jei.equals(search.getValue())) {
            lastJei = jei;
            search.setValue(jei);
        }
    }

    /** A list row: either an item with a count or a fluid (then fluid is not empty). */
    private record Row(ItemStack stack, long count, boolean craftable, FluidStack fluid, String name, String namespace) {
        boolean isFluid() {
            return !fluid.isEmpty();
        }

        /** Count "in units": buckets for a fluid. */
        double units() {
            return isFluid() ? fluid.getAmount() / 1000.0 : count;
        }
    }

    /** Ingredient under the cursor for JEI (R and U keys): an ItemStack or FluidStack and its place on the screen. */
    public record Hit(Object ingredient, Rect2i area) {
    }

    private final boolean crafting;
    private EditBox search;
    private int scroll = 0;
    private boolean draggingScroll = false;

    private int builtVersion = -1;
    private boolean dirty = true;
    private final List<Row> rows = new ArrayList<>();
    private final Map<ItemStack, String> nameCache = new HashMap<>();

    public TerminalScreen(TerminalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, TerminalMenu.WIDTH, TerminalMenu.imageHeight(menu.crafting, menu.rows));
        this.crafting = menu.crafting;
    }

    private int rows() {
        return menu.rows;
    }

    private static GolemClientConfig.JeiSync jeiSync() {
        try {
            return GolemClientConfig.JEI_SYNC.get();
        } catch (IllegalStateException notLoaded) {
            return GolemClientConfig.JeiSync.TWO_WAY;
        }
    }

    private static GolemClientConfig.TerminalSize sizeMode() {
        try {
            return GolemClientConfig.TERMINAL_SIZE.get();
        } catch (IllegalStateException notLoaded) {
            return GolemClientConfig.TerminalSize.NORMAL;
        }
    }

    /** How many list rows to show for the selected size and screen height. */
    private int wantedRows() {
        int base = TerminalMenu.defaultRows(crafting);
        int fixed = TerminalMenu.imageHeight(crafting, 0);
        int fit = Math.max(base, (height - 6 - fixed) / 18);
        return switch (sizeMode()) {
            case NORMAL -> base;
            case TALL -> Math.min(fit, base + 4);
            case FULL -> fit;
        };
    }

    @Override
    protected void init() {
        menu.relayout(wantedRows());
        this.imageHeight = TerminalMenu.imageHeight(crafting, rows());
        super.init();
        this.titleLabelX = TerminalMenu.LIST_X;
        this.titleLabelY = 6;
        this.inventoryLabelX = TerminalMenu.LIST_X;
        this.inventoryLabelY = TerminalMenu.invY(crafting, rows()) - 11;

        search = new EditBox(font, leftPos + 97, topPos + 8, 74, 12, Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(GolemGui.PARCHMENT);
        search.setHint(Component.translatable("gui.golem_logix.search").withStyle(ChatFormatting.DARK_GRAY));
        // search is shared with JEI: on open the JEI line is taken (if JEI is there), what is typed here goes to JEI
        // the mode is chosen with the "JEI" button on the right: no link, only to JEI, both ways
        String jei = jeiSync().readsFromJei() ? ClientHooks.jeiGetSearch.get() : null;
        search.setValue(jei != null ? jei : lastSearch);
        lastJei = jei;
        search.setResponder(s -> {
            lastSearch = s;
            dirty = true;
            scroll = 0;
            lastJei = s;
            if (jeiSync().sendsToJei()) {
                ClientHooks.jeiSetSearch.accept(s);
            }
        });
        addRenderableWidget(search);

        int bx = leftPos + imageWidth + 2;
        int by = topPos + 4;
        addRenderableWidget(new GolemButton(bx, by, 36, 16,
                () -> Component.translatable("gui.golem_logix.sort." + sortMode.name().toLowerCase(Locale.ROOT)),
                () -> Component.translatable("gui.golem_logix.sort.tooltip"), () -> false,
                b -> {
                    sortMode = SortMode.values()[(sortMode.ordinal() + 1) % SortMode.values().length];
                    dirty = true;
                }));
        addRenderableWidget(new GolemButton(bx, by + 18, 36, 16,
                () -> Component.translatable(reverse ? "gui.golem_logix.dir.desc" : "gui.golem_logix.dir.asc"),
                () -> Component.translatable("gui.golem_logix.dir.tooltip"), () -> false,
                b -> {
                    reverse = !reverse;
                    dirty = true;
                }));
        addRenderableWidget(new GolemButton(bx, by + 36, 36, 16,
                () -> Component.translatable("gui.golem_logix.view." + viewMode.name().toLowerCase(Locale.ROOT)),
                () -> Component.translatable("gui.golem_logix.view.tooltip"), () -> viewMode != ViewMode.ALL,
                b -> {
                    viewMode = ViewMode.values()[(viewMode.ordinal() + 1) % ViewMode.values().length];
                    dirty = true;
                    scroll = 0;
                }));
        addRenderableWidget(new GolemButton(bx, by + 54, 36, 16,
                () -> Component.translatable(craftableOnly ? "gui.golem_logix.filter.craft" : "gui.golem_logix.filter.all"),
                () -> Component.translatable("gui.golem_logix.filter.tooltip"), () -> craftableOnly,
                b -> {
                    craftableOnly = !craftableOnly;
                    dirty = true;
                    scroll = 0;
                }));
        addRenderableWidget(new GolemButton(bx, by + 72, 36, 16,
                () -> Component.translatable("gui.golem_logix.size." + sizeMode().name().toLowerCase(Locale.ROOT)),
                () -> Component.translatable("gui.golem_logix.size.tooltip"), () -> sizeMode() != GolemClientConfig.TerminalSize.NORMAL,
                b -> {
                    GolemClientConfig.TERMINAL_SIZE.set(sizeMode().next());
                    GolemClientConfig.TERMINAL_SIZE.save();
                    rebuildWidgets();
                }));
        // search sync with JEI (the button is visible only when JEI is installed)
        if (ClientHooks.jeiGetSearch.get() != null) {
            addRenderableWidget(new GolemButton(bx, by + 90, 36, 16,
                    () -> Component.translatable("gui.golem_logix.jei_sync." + jeiSync().name().toLowerCase(Locale.ROOT)),
                    () -> Component.translatable("gui.golem_logix.jei_sync.tooltip"), () -> jeiSync() != GolemClientConfig.JeiSync.OFF,
                    b -> {
                        GolemClientConfig.JEI_SYNC.set(jeiSync().next());
                        GolemClientConfig.JEI_SYNC.save();
                        if (jeiSync().sendsToJei()) {
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
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)));
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    // ------------------------------------------------------------------ data

    private String nameOf(ItemStack stack) {
        return nameCache.computeIfAbsent(stack, s -> s.getHoverName().getString().toLowerCase(Locale.ROOT));
    }

    private void rebuild() {
        dirty = false;
        builtVersion = menu.clientVersion;
        rows.clear();
        nameCache.clear();

        String query = search.getValue().trim().toLowerCase(Locale.ROOT);

        // craftable fluids come as fluid ghost keys among the items: they go with the fluids
        List<ItemStack> craftFluids = new ArrayList<>();
        for (TerminalListPayload.Entry e : menu.clientEntries) {
            if (e.craftable() && com.golemlogix.items.FluidGhostItem.is(e.key())) {
                craftFluids.add(e.key());
            }
        }
        if (viewMode != ViewMode.FLUIDS) {
            for (TerminalListPayload.Entry e : menu.clientEntries) {
                if (craftableOnly && !e.craftable() || com.golemlogix.items.FluidGhostItem.is(e.key())) {
                    continue;
                }
                if (matches(e.key(), query)) {
                    rows.add(new Row(e.key(), e.count(), e.craftable(), FluidStack.EMPTY, nameOf(e.key()),
                            BuiltInRegistries.ITEM.getKey(e.key().getItem()).getNamespace()));
                }
            }
        }
        TerminalInfoPayload info = menu.clientInfo;
        if (viewMode != ViewMode.ITEMS && info != null) {
            List<ItemStack> unseen = new ArrayList<>(craftFluids);
            for (FluidStack f : info.fluids()) {
                ItemStack ghost = unseen.stream().filter(k -> FluidStack.isSameFluidSameComponents(
                        com.golemlogix.items.FluidGhostItem.fluid(k), f)).findFirst().orElse(ItemStack.EMPTY);
                unseen.remove(ghost);
                if (craftableOnly && ghost.isEmpty()) {
                    continue;
                }
                String name = f.getHoverName().getString().toLowerCase(Locale.ROOT);
                String ns = BuiltInRegistries.FLUID.getKey(f.getFluid()).getNamespace();
                if (matchesFluid(name, ns, query)) {
                    rows.add(new Row(ghost, f.getAmount(), !ghost.isEmpty(), f, name, ns));
                }
            }
            // none of it in the network, but a pattern makes it: a row to order it from
            for (ItemStack ghost : unseen) {
                FluidStack f = com.golemlogix.items.FluidGhostItem.fluid(ghost);
                String name = f.getHoverName().getString().toLowerCase(Locale.ROOT);
                String ns = BuiltInRegistries.FLUID.getKey(f.getFluid()).getNamespace();
                if (matchesFluid(name, ns, query)) {
                    rows.add(new Row(ghost, 0, true, f.copyWithAmount(1), name, ns));
                }
            }
        }

        Comparator<Row> byName = Comparator.comparing(Row::name);
        Comparator<Row> cmp = switch (sortMode) {
            case NAME -> byName;
            case COUNT -> Comparator.comparingDouble(Row::units).reversed().thenComparing(byName);
            case MOD -> Comparator.comparing(Row::namespace).thenComparing(byName);
        };
        rows.sort(reverse ? cmp.reversed() : cmp);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    /** For rig checks: index of a list row by part of its name (-1 if there is none). */
    public int indexOfName(String part) {
        if (dirty || builtVersion != menu.clientVersion) {
            rebuild();
        }
        String q = part.toLowerCase(Locale.ROOT);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).name().contains(q)) {
                return i;
            }
        }
        return -1;
    }

    private boolean matchesFluid(String name, String namespace, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (query.startsWith("@")) {
            return namespace.contains(query.substring(1));
        }
        if (query.startsWith("#")) {
            return false;
        }
        return name.contains(query);
    }

    private boolean matches(ItemStack stack, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (query.startsWith("@")) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(query.substring(1));
        }
        if (query.startsWith("#")) {
            String q = query.substring(1);
            for (Component line : getTooltipFromItem(minecraft, stack)) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(q)) {
                    return true;
                }
            }
            return false;
        }
        return nameOf(stack).contains(query);
    }

    private int totalEntries() {
        return rows.size();
    }

    private int maxScroll() {
        return Math.max(0, (totalEntries() + TerminalMenu.COLS - 1) / TerminalMenu.COLS - rows());
    }

    // ------------------------------------------------------------------ render

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        if (dirty || builtVersion != menu.clientVersion) {
            rebuild();
        }
        GolemGui.panel(g, leftPos, topPos, imageWidth, imageHeight);

        // search field
        GolemGui.inset(g, leftPos + 91, topPos + 5, 80, 15);

        // list
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < TerminalMenu.COLS; c++) {
                GolemGui.slot(g, lx + c * 18, ly + r * 18);
            }
        }

        // scroll bar
        int sbX = lx + TerminalMenu.COLS * 18 + 3;
        int sbH = rows() * 18;
        g.fill(sbX, ly, sbX + 5, ly + sbH, GolemGui.SLOT_BG);
        int max = maxScroll();
        if (max > 0) {
            int handle = Math.max(10, sbH * rows() / (rows() + max));
            int hy = ly + (sbH - handle) * scroll / max;
            g.fill(sbX, hy, sbX + 5, hy + handle, GolemGui.BRASS);
        } else {
            g.fill(sbX, ly, sbX + 5, ly + sbH, GolemGui.BRASS_DARK);
        }

        // info line
        renderInfo(g, mouseX, mouseY);

        for (Slot slot : menu.slots) {
            GolemGui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        // the slot of the Chronometer the window was opened through is locked: a teal frame outlines it
        if (menu.lockedMenuSlot() >= 0) {
            Slot lock = menu.slots.get(menu.lockedMenuSlot());
            int x = leftPos + lock.x - 1, y = topPos + lock.y - 1;
            g.fill(x, y, x + 18, y + 1, GolemGui.CYAN);
            g.fill(x, y + 17, x + 18, y + 18, GolemGui.CYAN);
            g.fill(x, y, x + 1, y + 18, GolemGui.CYAN);
            g.fill(x + 17, y, x + 18, y + 18, GolemGui.CYAN);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0x5038D9D6);
        }
        if (crafting) {
            int cy = topPos + TerminalMenu.craftY(rows());
            g.text(font, "=>", leftPos + 102, cy + 22, GolemGui.CYAN, false);
            if (hasChargeGauge()) {
                renderChargeGauge(g, leftPos + TerminalMenu.GAUGE_X, cy, 54);
            }
        }

        renderEntries(g, mouseX, mouseY);
    }

    /** For the Chronometer (if Source is needed) a charge gauge to the right of the result. */
    private boolean hasChargeGauge() {
        return menu.wireless && crafting && menu.clientInfo != null && menu.clientInfo.charge() >= 0;
    }

    /** Vertical Source charge flask: a purple column in a brass mount; fed by the Resonator it is full and shimmers. */
    private void renderChargeGauge(GuiGraphicsExtractor g, int x, int y, int h) {
        TerminalInfoPayload info = menu.clientInfo;
        boolean resonator = info.resonator();
        double frac = resonator ? 1.0 : Math.min(1.0, info.charge() / (double) com.golemlogix.items.PortableChronometerItem.MAX_CHARGE);
        g.fill(x - 1, y - 1, x + 11, y + h + 1, GolemGui.BRASS_DARK);
        g.fill(x, y, x + 10, y + h, GolemGui.SLOT_BG);
        int fill = (int) Math.round((h - 2) * frac);
        if (fill > 0) {
            float t = minecraft.level == null ? 0 : (minecraft.level.getGameTime() % 40) / 40f;
            int top = y + h - 1 - fill;
            g.fill(x + 1, top, x + 9, y + h - 1, 0xFF6A4FC8);
            g.fill(x + 1, top, x + 3, y + h - 1, 0xFF9E80F8);
            if (resonator) {
                // aether stream from the Resonator: a running light stripe
                int band = top + (int) ((y + h - 1 - top) * t);
                g.fill(x + 1, band, x + 9, Math.min(y + h - 1, band + 3), 0xFFE2D6FF);
            }
        }
    }

    private static int fillColor(double frac) {
        return frac > 0.9 ? GolemGui.RED : frac > 0.7 ? GolemGui.COPPER : GolemGui.CYAN_DARK;
    }

    private void renderInfo(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int iy = topPos + TerminalMenu.infoY(rows());
        int ix = leftPos + TerminalMenu.LIST_X;
        TerminalInfoPayload info = menu.clientInfo;
        GolemGui.inset(g, ix, iy, 162, 12);
        if (info == null) {
            return;
        }
        if (!info.error().isEmpty()) {
            g.centeredText(font, Component.translatable(info.error()), ix + 81, iy + 2, GolemGui.RED);
            return;
        }
        double itemFrac = info.itemsCapacity() <= 0 ? 0 : (double) info.itemsUsed() / info.itemsCapacity();
        double fluidFrac = info.fluidsCapacity() <= 0 ? 0 : (double) info.fluidsUsed() / info.fluidsCapacity();
        String left;
        if (viewMode == ViewMode.ALL) {
            // two bars: items on top, fluids below; the numbers are items and buckets
            int wi = (int) Math.round(160 * Math.min(1.0, itemFrac));
            int wf = (int) Math.round(160 * Math.min(1.0, fluidFrac));
            if (wi > 0) {
                g.fill(ix + 1, iy + 1, ix + 1 + wi, iy + 6, fillColor(itemFrac));
            }
            if (wf > 0) {
                g.fill(ix + 1, iy + 6, ix + 1 + wf, iy + 11, fluidFrac > 0.9 ? GolemGui.RED : 0xFF2A6FC8);
            }
            // items, then a drop and the fluid buckets; channels on the right if there is room (otherwise they are in the tooltip)
            String items = GolemGui.formatCount(info.itemsUsed()) + "/" + GolemGui.formatCount(info.itemsCapacity());
            String buckets = GolemGui.formatBuckets(info.fluidsUsed()) + "/" + GolemGui.formatBuckets(info.fluidsCapacity());
            g.text(font, items, ix + 4, iy + 2, GolemGui.PARCHMENT, true);
            int fx = ix + 4 + font.width(items) + 6;
            drawDropAt(g, fx, iy + 3);
            g.text(font, buckets, fx + 7, iy + 2, GolemGui.PARCHMENT, true);
            drawChannels(g, info, fx + 7 + font.width(buckets) + 6, ix, iy);
            return;
        } else {
            boolean fluids = viewMode == ViewMode.FLUIDS;
            double frac = fluids ? fluidFrac : itemFrac;
            int w = (int) Math.round(160 * Math.min(1.0, frac));
            if (w > 0) {
                g.fill(ix + 1, iy + 1, ix + 1 + w, iy + 11, fillColor(frac));
            }
            left = fluids
                    ? GolemGui.formatCount(info.fluidsUsed()) + "/" + GolemGui.formatCount(info.fluidsCapacity()) + " mB"
                    : GolemGui.formatCount(info.itemsUsed()) + "/" + GolemGui.formatCount(info.itemsCapacity());
        }
        g.text(font, left, ix + 4, iy + 2, GolemGui.PARCHMENT, true);
        drawChannels(g, info, ix + 4 + font.width(left) + 6, ix, iy);
    }

    /** "Ch 9/320" at the right edge of the status line, if it does not overlap the text on the left (from = where that text ends). */
    private void drawChannels(GuiGraphicsExtractor g, TerminalInfoPayload info, int from, int ix, int iy) {
        String right = Component.translatable("gui.golem_logix.channels_short", info.channelsUsed(), info.channelCapacity()).getString();
        int x = ix + 158 - font.width(right);
        if (x >= from) {
            g.text(font, right, x, iy + 2, GolemGui.CYAN, true);
        }
    }

    /** A 5x6 drop before the bucket count in the status line. */
    private void drawDropAt(GuiGraphicsExtractor g, int x, int y) {
        int edge = 0xFF0B2A52, body = 0xFF9FE8FF;
        g.fill(x + 1, y, x + 4, y + 1, edge);
        g.fill(x, y + 1, x + 5, y + 5, edge);
        g.fill(x + 1, y + 5, x + 4, y + 6, edge);
        g.fill(x + 2, y + 1, x + 3, y + 2, body);
        g.fill(x + 1, y + 2, x + 4, y + 5, body);
    }

    /** The sort and view buttons sit to the right of the window: JEI must avoid them, not cover them with its panel. */
    public List<Rect2i> extraAreas() {
        return List.of(new Rect2i(leftPos + imageWidth, topPos + 2, 40, 110));
    }

    private void renderEntries(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        int hovered = hoveredIndex(mouseX, mouseY);

        for (int r = 0; r < rows(); r++) {
            for (int c = 0; c < TerminalMenu.COLS; c++) {
                int idx = (scroll + r) * TerminalMenu.COLS + c;
                if (idx >= totalEntries()) {
                    return;
                }
                int x = lx + c * 18 + 1;
                int y = ly + r * 18 + 1;
                Row row = rows.get(idx);
                if (row.isFluid()) {
                    drawFluid(g, row.fluid(), x, y);
                    drawDrop(g, x, y);
                    if (row.count() <= 0) {
                        drawCount(g, Component.translatable("gui.golem_logix.craft_short").getString(), x, y, GolemGui.CYAN);
                    } else {
                        String text = viewMode == ViewMode.FLUIDS ? GolemGui.formatCount(row.fluid().getAmount())
                                : GolemGui.formatBuckets(row.fluid().getAmount());
                        drawCount(g, text, x, y, 0xFFFFFFFF);
                        if (row.craftable()) {
                            g.fill(x - 1, y - 1, x + 3, y + 3, GolemGui.CYAN);
                        }
                    }
                } else {
                    g.item(row.stack(), x, y);
                    if (row.count() > 0) {
                        drawCount(g, GolemGui.formatCount(row.count()), x, y, 0xFFFFFFFF);
                    } else {
                        drawCount(g, Component.translatable("gui.golem_logix.craft_short").getString(), x, y, GolemGui.CYAN);
                    }
                    if (row.craftable() && row.count() > 0) {
                        g.fill(x - 1, y - 1, x + 3, y + 3, GolemGui.CYAN);
                    }
                }
                if (idx == hovered) {
                    g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                }
            }
        }
    }

    private void drawCount(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(0, 0);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, text, (x + 17) * 2 - font.width(text), (y + 11) * 2, color, true);
        g.pose().popMatrix();
    }

    /** A small drop in the slot corner: tells a fluid from an item in the combined list. */
    private void drawDrop(GuiGraphicsExtractor g, int x, int y) {
        if (viewMode == ViewMode.FLUIDS) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(0, 0);
        int edge = 0xFF0B2A52, body = 0xFF9FE8FF;
        g.fill(x + 1, y, x + 4, y + 1, edge);
        g.fill(x, y + 1, x + 5, y + 5, edge);
        g.fill(x + 1, y + 5, x + 4, y + 6, edge);
        g.fill(x + 2, y + 1, x + 3, y + 2, body);
        g.fill(x + 1, y + 2, x + 4, y + 5, body);
        g.fill(x + 1, y + 2, x + 2, y + 3, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    private void drawFluid(GuiGraphicsExtractor g, FluidStack fluid, int x, int y) {
        FluidIcons.draw(g, fluid, x, y);
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
        return idx < totalEntries() ? idx : -1;
    }

    /** For JEI (R and U keys, bookmarks): what is in the list slot under the cursor. */
    @Nullable
    public Hit ingredientAt(double mouseX, double mouseY) {
        int idx = hoveredIndex(mouseX, mouseY);
        if (idx < 0 || idx >= rows.size()) {
            return null;
        }
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        int c = (int) ((mouseX - lx) / 18);
        int r = (int) ((mouseY - ly) / 18);
        Rect2i area = new Rect2i(lx + c * 18 + 1, ly + r * 18 + 1, 16, 16);
        Row row = rows.get(idx);
        return new Hit(row.isFluid() ? row.fluid() : row.stack(), area);
    }

    private boolean overList(double mouseX, double mouseY) {
        int lx = leftPos + TerminalMenu.LIST_X;
        int ly = topPos + TerminalMenu.LIST_Y;
        return mouseX >= lx && mouseY >= ly && mouseX < lx + TerminalMenu.COLS * 18 && mouseY < ly + rows() * 18;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String t = font.plainSubstrByWidth(title.getString(), 82);
        g.text(font, t, titleLabelX, titleLabelY, GolemGui.PARCHMENT, false);
        if (!crafting) {
            g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GolemGui.PARCHMENT, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        renderEntryTooltip(g, mouseX, mouseY);
    }

    private void renderEntryTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int idx = hoveredIndex(mouseX, mouseY);
        if (idx >= 0) {
            List<Component> lines = new ArrayList<>();
            Row row = rows.get(idx);
            if (row.isFluid()) {
                FluidStack f = row.fluid();
                lines.add(f.getHoverName());
                int stored = row.count() <= 0 ? 0 : f.getAmount();
                lines.add(Component.translatable("gui.golem_logix.stored", Component.translatable("gui.golem_logix.stored_fluid",
                        GolemGui.formatBuckets(stored), String.format(Locale.US, "%,d", stored))).withStyle(ChatFormatting.GRAY));
                if (stored > 0) {
                    lines.add(Component.translatable("gui.golem_logix.fluid_hint").withStyle(ChatFormatting.DARK_AQUA));
                }
                if (row.craftable()) {
                    lines.add(Component.translatable("gui.golem_logix.craftable_hint").withStyle(ChatFormatting.AQUA));
                }
            } else {
                lines.addAll(getTooltipFromItem(minecraft, row.stack()));
                lines.add(Component.translatable("gui.golem_logix.stored", String.format(Locale.US, "%,d", row.count()))
                        .withStyle(ChatFormatting.GRAY));
                if (row.craftable()) {
                    lines.add(Component.translatable("gui.golem_logix.craftable_hint").withStyle(ChatFormatting.AQUA));
                }
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        int iy = topPos + TerminalMenu.infoY(rows());
        if (menu.clientInfo != null && mouseX >= leftPos + TerminalMenu.LIST_X && mouseX < leftPos + TerminalMenu.LIST_X + 162 && mouseY >= iy && mouseY < iy + 12) {
            TerminalInfoPayload info = menu.clientInfo;
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.golem_logix.info.items", GolemGui.formatCount(info.itemsUsed()), GolemGui.formatCount(info.itemsCapacity())));
            lines.add(Component.translatable("gui.golem_logix.info.fluids", GolemGui.formatCount(info.fluidsUsed()), GolemGui.formatCount(info.fluidsCapacity())));
            lines.add(Component.translatable("gui.golem_logix.info.channels", info.channelsUsed(), info.channelCapacity()));
            lines.add(Component.translatable("gui.golem_logix.info.cpu", info.cpuBusy(), info.cpuTotal()));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        if (hasChargeGauge()) {
            int cy = topPos + TerminalMenu.craftY(rows());
            int sx = leftPos + TerminalMenu.GAUGE_X;
            if (mouseX >= sx - 1 && mouseX < sx + 11 && mouseY >= cy - 1 && mouseY < cy + 55) {
                g.setComponentTooltipForNextFrame(font, List.of(menu.clientInfo.resonator()
                        ? Component.translatable("tooltip.golem_logix.chronometer.resonator").withStyle(ChatFormatting.LIGHT_PURPLE)
                        : Component.translatable("tooltip.golem_logix.chronometer.charge", menu.clientInfo.charge(),
                        com.golemlogix.items.PortableChronometerItem.MAX_CHARGE).withStyle(ChatFormatting.LIGHT_PURPLE)), mouseX, mouseY);
            }
        }
    }

    // ------------------------------------------------------------------ input

    private void send(Action action, ItemStack key, FluidStack fluid) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new TerminalActionPayload(menu.containerId, action, key, fluid));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
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

        if (overList(mouseX, mouseY)) {
            if (handleListClick(mouseX, mouseY, button)) {
                return true;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (!search.isHovered() && search.isFocused() && button == 0) {
            search.setFocused(false);
        }
        return handled;
    }

    private boolean handleListClick(double mouseX, double mouseY, int button) {
        ItemStack carried = menu.getCarried();
        int idx = hoveredIndex(mouseX, mouseY);
        Row row = idx >= 0 ? rows.get(idx) : null;

        IFluidHandlerItem handler = carried.isEmpty() ? null : net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(carried.copyWithCount(1)).orElse(null);
        boolean fullContainer = handler != null && !handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).isEmpty();

        if (viewMode == ViewMode.FLUIDS) {
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
                // a click with a container on a fluid row in the combined list
                if (fullContainer) {
                    send(Action.FLUID_DRAIN_CARRIED, ItemStack.EMPTY, FluidStack.EMPTY);
                } else {
                    send(Action.FLUID_FILL_CARRIED, ItemStack.EMPTY, row.fluid());
                }
                return true;
            }
            // right-click with a full bucket (any fluid container) on the list pours the fluid into the network
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
        boolean ctrl = net.minecraft.client.Minecraft.getInstance().hasControlDown();
        if (row.isFluid()) {
            if (row.craftable() && (ctrl || row.count() <= 0) && button == 0) {
                minecraft.setScreen(new CraftRequestScreen(this, menu, row.stack()));
            } else if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable("gui.golem_logix.fluid_need_container"));
            }
            return true;
        }
        if (row.craftable() && (ctrl || row.count() <= 0) && button == 0) {
            minecraft.setScreen(new CraftRequestScreen(this, menu, row.stack()));
            return true;
        }
        if (row.count() <= 0) {
            return true;
        }
        ItemStack key = TerminalMenu.keyOf(row.stack());
        if (button == 0) {
            send(net.minecraft.client.Minecraft.getInstance().hasShiftDown() ? Action.TO_INVENTORY : Action.TAKE_STACK, key, FluidStack.EMPTY);
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
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        if (draggingScroll) {
            dragScroll(mouseY);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        draggingScroll = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overList(mouseX, mouseY) || mouseX < leftPos + imageWidth) {
            int max = maxScroll();
            if (max > 0 && mouseY < topPos + TerminalMenu.LIST_Y + rows() * 18) {
                scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (key == GLFW.GLFW_KEY_TAB || (key == GLFW.GLFW_KEY_SLASH && !search.isFocused())) {
            search.setFocused(true);
            setFocused(search);
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
