package com.golemlogix.client;

import com.google.common.base.Suppliers;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.menu.RecipeMonitorMenu;
import com.golemlogix.network.MonitorPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Recipe Monitor console: on the left all Cryo-Calculators of the network (what they make, progress, state), on the right details of the selected one:
 * the order, what is being made now, the remaining steps, the items expected from machines and a cancel button.
 */
public class RecipeMonitorScreen extends GolemScreen<RecipeMonitorMenu> {
    private static final int W = 256;
    private static final int H = 206;
    private static final int LIST_X = 8, LIST_Y = 20, ROW_H = 22, ROWS = 8, LIST_W = 112;
    private static final int DX = 126;

    private static final Supplier<ItemStack> ICON_MAINFRAME = Suppliers.memoize(() -> new ItemStack(ModBlocks.CRYO_MAINFRAME.get()));
    private static final Supplier<ItemStack> ICON_CALCULATOR = Suppliers.memoize(() -> new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
    private static final Supplier<ItemStack> ICON_COPROCESSOR = Suppliers.memoize(() -> new ItemStack(ModBlocks.COOLING_COPROCESSOR.get()));
    private static final Component LABEL_CANCEL = Component.translatable("gui.golem_logix.calc.cancel");
    private static final Component LABEL_WAITING = Component.translatable("gui.golem_logix.calc.waiting");
    private static final Component LABEL_NO_CPUS = Component.translatable("gui.golem_logix.monitor.no_cpus");
    private static final Component LABEL_CALC_IDLE = Component.translatable("gui.golem_logix.calc.idle");
    private static final Component LABEL_NOW = Component.translatable("gui.golem_logix.monitor.now");
    private static final Component LABEL_STEPS = Component.translatable("gui.golem_logix.monitor.steps");
    private static final Component LABEL_WAITING_STEPS = Component.translatable("gui.golem_logix.monitor.waiting");

    private int scroll = 0;
    @Nullable
    private BlockPos selected = null;
    /** Core number of the selected row (mainframe cores share one block). */
    private int selectedCore = 0;
    private GolemButton cancel;
    /** Item under the cursor (for the tooltip and the JEI R/U keys). */
    private ItemStack hoverStack = ItemStack.EMPTY;
    private Rect2i hoverArea = null;

    private MonitorPayload cachedSummaryData;
    private Language cachedSummaryLang;
    private String cachedSummaryText = "";
    private String cachedIdleText = "";

    public RecipeMonitorScreen(RecipeMonitorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        cancel = addRenderableWidget(new GolemButton(leftPos + DX + 54, topPos + H - 24, 66, 16, LABEL_CANCEL, b -> {
            int idx = selectedIndex();
            if (idx >= 0) {
                menuButton(RecipeMonitorMenu.BUTTON_CANCEL_BASE + idx);
            }
        }));
        cancel.active = false;
    }

    private List<MonitorPayload.Cpu> cpus() {
        return menu.clientData == null ? List.of() : menu.clientData.cpus();
    }

    private int selectedIndex() {
        List<MonitorPayload.Cpu> list = cpus();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).pos().equals(selected) && list.get(i).core() == selectedCore) {
                return i;
            }
        }
        return list.isEmpty() ? -1 : 0;
    }

    private void ensureSummaryCache() {
        MonitorPayload data = menu.clientData;
        Language lang = Language.getInstance();
        if (data == cachedSummaryData && lang == cachedSummaryLang) {
            return;
        }
        cachedSummaryData = data;
        cachedSummaryLang = lang;
        List<MonitorPayload.Cpu> list = data == null ? List.of() : data.cpus();
        int busy = 0;
        for (MonitorPayload.Cpu c : list) {
            if (c.state() >= 0) {
                busy++;
            }
        }
        cachedSummaryText = Component.translatable("gui.golem_logix.monitor.summary", busy, list.size()).getString();
        cachedIdleText = Component.translatable("gui.golem_logix.monitor.idle").getString();
    }

    // ---------------------------------------------------------------------------------- drawing

    @Override
    protected void renderExtraBg(GuiGraphicsExtractor g, float partialTick, int mouseX, int mouseY) {
        hoverStack = ItemStack.EMPTY;
        hoverArea = null;
        ensureSummaryCache();
        GolemGui.inset(g, leftPos + LIST_X - 1, topPos + LIST_Y - 1, LIST_W + 2, ROWS * ROW_H + 2);
        GolemGui.inset(g, leftPos + DX - 1, topPos + LIST_Y - 1, W - DX - 7, H - LIST_Y - 30);
        MonitorPayload data = menu.clientData;
        if (data == null) {
            g.textWithWordWrap(font, LABEL_WAITING, leftPos + LIST_X + 4, topPos + LIST_Y + 4, LIST_W - 8, GolemGui.PARCHMENT);
            cancel.active = false;
            return;
        }
        if (!data.error().isEmpty()) {
            g.textWithWordWrap(font, Component.translatable(data.error()), leftPos + LIST_X + 4, topPos + LIST_Y + 4, LIST_W - 8, GolemGui.RED);
            cancel.active = false;
            return;
        }
        List<MonitorPayload.Cpu> list = data.cpus();
        if (list.isEmpty()) {
            g.textWithWordWrap(font, LABEL_NO_CPUS, leftPos + LIST_X + 4, topPos + LIST_Y + 4, LIST_W - 8, GolemGui.CYAN);
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - ROWS)));
        int sel = selectedIndex();
        for (int r = 0; r < ROWS && scroll + r < list.size(); r++) {
            drawRow(g, list.get(scroll + r), scroll + r == sel, leftPos + LIST_X, topPos + LIST_Y + r * ROW_H, mouseX, mouseY);
        }
        if (list.size() > ROWS) {
            int sbX = leftPos + LIST_X + LIST_W - 3, sbY = topPos + LIST_Y, sbH = ROWS * ROW_H;
            int handle = Math.max(10, sbH * ROWS / list.size());
            int hy = sbY + (sbH - handle) * scroll / Math.max(1, list.size() - ROWS);
            g.fill(sbX, hy, sbX + 2, hy + handle, GolemGui.BRASS);
        }
        MonitorPayload.Cpu cpu = sel >= 0 ? list.get(sel) : null;
        cancel.active = cpu != null && cpu.state() >= 0;
        if (cpu != null) {
            drawDetails(g, cpu, leftPos + DX + 3, topPos + LIST_Y + 3, mouseX, mouseY);
        }
    }

    private void recordHover(ItemStack stack, int x, int y, int mx, int my) {
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            hoverStack = stack;
            hoverArea = new Rect2i(x, y, 16, 16);
        }
    }

    private void drawRow(GuiGraphicsExtractor g, MonitorPayload.Cpu c, boolean isSelected, int x, int y, int mx, int my) {
        if (isSelected) {
            g.fill(x, y, x + LIST_W - 4, y + ROW_H, 0x60235A58);
        }
        boolean idle = c.state() < 0;
        int color = idle ? 0xFF5C6B70 : CalculatorScreen.statusColor(CalculatorScreen.status(c.state()));
        g.fill(x + 1, y + 2, x + 3, y + ROW_H - 2, color);
        ItemStack icon = idle ? (c.core() > 0 ? ICON_MAINFRAME.get() : ICON_CALCULATOR.get())
                : !c.current().isEmpty() ? c.current() : c.targets().isEmpty() ? ItemStack.EMPTY : c.targets().get(0).item();
        g.item(icon, x + 5, y + 3);
        if (!idle) {
            recordHover(icon, x + 5, y + 3, mx, my);
        }
        String name = rowTitle(c, idle);
        g.text(font, font.plainSubstrByWidth(name, LIST_W - 30), x + 24, y + 3, idle ? 0xFF8C9AA0 : GolemGui.PARCHMENT, false);
        if (!idle) {
            double frac = c.total() <= 0 ? 0 : (double) c.done() / c.total();
            GolemGui.bar(g, x + 24, y + 13, LIST_W - 32, 6, frac, GolemGui.CYAN_DARK);
            if (isBlocked(CalculatorScreen.status(c.state()))) {
                boolean lit = (System.currentTimeMillis() / 400) % 2 == 0;
                g.fill(x + LIST_W - 14, y + 3, x + LIST_W - 8, y + 9, lit ? 0xFFFF2020 : 0xFF600000);
            }
        } else {
            g.text(font, font.plainSubstrByWidth(where(c), LIST_W - 30), x + 24, y + 12, 0xFF5C6B70, false);
        }
    }

    private String rowTitle(MonitorPayload.Cpu c, boolean idle) {
        if (idle) {
            return cachedIdleText;
        }
        MonitorPayload.Step t = c.targets().isEmpty() ? null : c.targets().get(0);
        String name = t == null ? "?" : t.left() + "× " + t.item().getHoverName().getString();
        return c.targets().size() > 1 ? name + " +" + (c.targets().size() - 1) : name;
    }

    private static boolean isBlocked(CraftingJob.Status st) {
        return st == CraftingJob.Status.WAITING_MACHINE || st == CraftingJob.Status.NO_MACHINE
                || st == CraftingJob.Status.NO_PROVIDER || st == CraftingJob.Status.NO_SOURCE
                || st == CraftingJob.Status.INVALID_PATTERN || st == CraftingJob.Status.PAUSED;
    }

    /** Where the processor is: block coordinates, plus the core number for a mainframe core. */
    private static String where(MonitorPayload.Cpu c) {
        return c.core() > 0 ? c.pos().toShortString() + " #" + c.core() : c.pos().toShortString();
    }

    private void drawDetails(GuiGraphicsExtractor g, MonitorPayload.Cpu c, int x, int y, int mx, int my) {
        int w = W - DX - 13;
        g.text(font, font.plainSubstrByWidth(Component.translatable("gui.golem_logix.monitor.cpu", where(c)).getString(), w),
                x, y, GolemGui.CYAN_DARK, false);
        y += 11;
        // the calculator's coprocessors: an icon with a number at the right edge (the order line is under it)
        String cop = "×" + c.coprocessors();
        int copX = x + w - 18 - font.width(cop);
        if (c.state() < 0) {
            drawCoprocessors(g, cop, copX, y, mx, my);
            g.textWithWordWrap(font, LABEL_CALC_IDLE, x, y + 19, copX - x - 2, GolemGui.CYAN);
            return;
        }
        drawTargetsRow(g, c, x, y, copX, cop, mx, my);
        y += 19;
        y = drawProgressSection(g, c, x, y, w);
        y = drawCurrentStep(g, c, x, y, w, mx, my);
        drawPendingSteps(g, c, x, y, w, mx, my);
    }

    private void drawTargetsRow(GuiGraphicsExtractor g, MonitorPayload.Cpu c, int x, int y, int copX, String cop, int mx, int my) {
        int ix = x;
        for (MonitorPayload.Step t : c.targets()) {
            if (ix + 18 > copX - 2) {
                break;
            }
            stepIcon(g, t, ix, y, mx, my);
            ix += 18;
        }
        drawCoprocessors(g, cop, copX, y, mx, my);
    }

    private int drawProgressSection(GuiGraphicsExtractor g, MonitorPayload.Cpu c, int x, int y, int w) {
        CraftingJob.Status st = CalculatorScreen.status(c.state());
        g.text(font, Component.translatable("gui.golem_logix.calc.status." + st.name().toLowerCase(Locale.ROOT)), x, y,
                CalculatorScreen.statusColor(st), false);
        y += 11;
        double frac = c.total() <= 0 ? 0 : (double) c.done() / c.total();
        GolemGui.bar(g, x, y, w, 10, frac, GolemGui.CYAN_DARK);
        g.centeredText(font, c.done() + " / " + c.total(), x + w / 2, y + 1, GolemGui.PARCHMENT);
        return y + 13;
    }

    private int drawCurrentStep(GuiGraphicsExtractor g, MonitorPayload.Cpu c, int x, int y, int w, int mx, int my) {
        if (!c.current().isEmpty()) {
            g.text(font, LABEL_NOW, x, y + 4, GolemGui.PARCHMENT, false);
            int nx = x + font.width(LABEL_NOW) + 3;
            g.item(c.current(), nx, y);
            recordHover(c.current(), nx, y, mx, my);
            g.text(font, font.plainSubstrByWidth(c.current().getHoverName().getString(), x + w - nx - 20), nx + 19, y + 4,
                    GolemGui.CYAN, false);
        }
        return y + 18;
    }

    private void drawPendingSteps(GuiGraphicsExtractor g, MonitorPayload.Cpu c, int x, int y, int w, int mx, int my) {
        if (!c.steps().isEmpty()) {
            g.text(font, LABEL_STEPS, x, y, GolemGui.PARCHMENT, false);
            y += 10;
            y = stepGrid(g, c.steps(), x, y, w, 3, mx, my);
        }
        if (!c.waiting().isEmpty()) {
            g.text(font, LABEL_WAITING_STEPS, x, y, GolemGui.PARCHMENT, false);
            y += 10;
            stepGrid(g, c.waiting(), x, y, w, 1, mx, my);
        }
    }

    private void drawCoprocessors(GuiGraphicsExtractor g, String count, int x, int y, int mx, int my) {
        ItemStack icon = ICON_COPROCESSOR.get();
        g.item(icon, x, y);
        g.text(font, count, x + 17, y + 5, GolemGui.PARCHMENT, false);
        recordHover(icon, x, y, mx, my);
    }

    private int stepGrid(GuiGraphicsExtractor g, List<MonitorPayload.Step> steps, int x, int y, int w, int maxRows, int mx, int my) {
        int perRow = Math.max(1, w / 18);
        int i = 0;
        for (MonitorPayload.Step s : steps) {
            int r = i / perRow;
            if (r >= maxRows) {
                break;
            }
            stepIcon(g, s, x + (i % perRow) * 18, y + r * 18, mx, my);
            i++;
        }
        int rows = Math.min(maxRows, (i + perRow - 1) / perRow);
        return y + rows * 18 + 2;
    }

    private void stepIcon(GuiGraphicsExtractor g, MonitorPayload.Step s, int x, int y, int mx, int my) {
        GolemGui.slot(g, x - 1, y - 1);
        g.item(s.item(), x, y);
        String count = GolemGui.formatCount(s.left());
        g.pose().pushMatrix();
        g.pose().scale(0.5f, 0.5f);
        g.text(font, count, (x + 17) * 2 - font.width(count), (y + 11) * 2, 0xFFFFFFFF, true);
        g.pose().popMatrix();
        recordHover(s.item(), x, y, mx, my);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ensureSummaryCache();
        g.text(font, title, titleLabelX, titleLabelY, GolemGui.PARCHMENT, false);
        g.text(font, cachedSummaryText, imageWidth - 8 - font.width(cachedSummaryText), titleLabelY, GolemGui.CYAN, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!hoverStack.isEmpty()) {
            g.setTooltipForNextFrame(font, hoverStack, mouseX, mouseY);
        }
    }

    /** For JEI (R and U keys): the item under the cursor and its place on the screen. */
    @Nullable
    public Object[] ingredientAt(double mouseX, double mouseY) {
        if (hoverArea != null && !hoverStack.isEmpty() && hoverArea.contains((int) mouseX, (int) mouseY)) {
            return new Object[]{hoverStack, hoverArea};
        }
        return null;
    }

    // ---------------------------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (button == 0 && mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + ROWS * ROW_H) {
            int idx = scroll + (int) ((mouseY - ly) / ROW_H);
            List<MonitorPayload.Cpu> list = cpus();
            if (idx >= 0 && idx < list.size()) {
                selected = list.get(idx).pos();
                selectedCore = list.get(idx).core();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (scrollY != 0 && mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + ROWS * ROW_H) {
            scroll = Math.max(0, Math.min(Math.max(0, cpus().size() - ROWS), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public List<Rect2i> extraAreas() {
        return List.of();
    }
}
