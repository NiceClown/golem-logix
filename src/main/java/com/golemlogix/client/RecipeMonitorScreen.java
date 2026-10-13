package com.golemlogix.client;

import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.menu.RecipeMonitorMenu;
import com.golemlogix.network.MonitorPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Пульт Монитора Рецептов: слева — все Крио-Калькуляторы сети (что делают, ход, состояние), справа — подробности выбранного:
 * заказ, что делается сейчас, оставшиеся шаги, ожидаемые от машин предметы и кнопка отмены.
 */
public class RecipeMonitorScreen extends GolemScreen<RecipeMonitorMenu> {
    private static final int W = 256;
    private static final int H = 206;
    private static final int LIST_X = 8, LIST_Y = 20, ROW_H = 22, ROWS = 8, LIST_W = 112;
    private static final int DX = 126;

    private int scroll = 0;
    @Nullable
    private BlockPos selected = null;
    /** Номер ядра выбранной строки (у ядер мейнфрейма один блок). */
    private int selectedCore = 0;
    private GolemButton cancel;
    /** Предмет под курсором (для подсказки и для клавиш R/U JEI). */
    private ItemStack hoverStack = ItemStack.EMPTY;
    private Rect2i hoverArea = null;

    public RecipeMonitorScreen(RecipeMonitorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        cancel = addRenderableWidget(new GolemButton(leftPos + DX + 54, topPos + H - 24, 66, 16,
                Component.translatable("gui.golem_logix.calc.cancel"), b -> {
            int idx = selectedIndex();
            if (idx >= 0) {
                menuButton(RecipeMonitorMenu.BUTTON_CANCEL_BASE + idx);
            }
        }));
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

    @Nullable
    private MonitorPayload.Cpu selectedCpu() {
        int i = selectedIndex();
        return i < 0 ? null : cpus().get(i);
    }

    // ---------------------------------------------------------------------------------- рисование

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        hoverStack = ItemStack.EMPTY;
        hoverArea = null;
        GolemGui.inset(g, leftPos + LIST_X - 1, topPos + LIST_Y - 1, LIST_W + 2, ROWS * ROW_H + 2);
        GolemGui.inset(g, leftPos + DX - 1, topPos + LIST_Y - 1, W - DX - 7, H - LIST_Y - 30);
        MonitorPayload data = menu.clientData;
        if (data == null) {
            g.drawWordWrap(font, Component.translatable("gui.golem_logix.calc.waiting"), leftPos + LIST_X + 4, topPos + LIST_Y + 4,
                    LIST_W - 8, GolemGui.PARCHMENT);
            cancel.active = false;
            return;
        }
        if (!data.error().isEmpty()) {
            g.drawWordWrap(font, Component.translatable(data.error()), leftPos + LIST_X + 4, topPos + LIST_Y + 4, LIST_W - 8, GolemGui.RED);
            cancel.active = false;
            return;
        }
        List<MonitorPayload.Cpu> list = data.cpus();
        if (list.isEmpty()) {
            g.drawWordWrap(font, Component.translatable("gui.golem_logix.monitor.no_cpus"), leftPos + LIST_X + 4, topPos + LIST_Y + 4,
                    LIST_W - 8, GolemGui.CYAN);
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

    private void drawRow(GuiGraphics g, MonitorPayload.Cpu c, boolean selected, int x, int y, int mx, int my) {
        if (selected) {
            g.fill(x, y, x + LIST_W - 4, y + ROW_H, 0x60235A58);
        }
        boolean idle = c.state() < 0;
        int color = idle ? 0xFF5C6B70 : CalculatorScreen.statusColor(CalculatorScreen.status(c.state()));
        g.fill(x + 1, y + 2, x + 3, y + ROW_H - 2, color);
        ItemStack icon = idle ? new ItemStack(c.core() > 0 ? com.golemlogix.init.ModBlocks.CRYO_MAINFRAME.get() : com.golemlogix.init.ModBlocks.CRYO_CALCULATOR.get())
                : !c.current().isEmpty() ? c.current() : c.targets().isEmpty() ? ItemStack.EMPTY : c.targets().get(0).item();
        g.renderItem(icon, x + 5, y + 3);
        if (!idle && mx >= x + 5 && mx < x + 21 && my >= y + 3 && my < y + 19) {
            hoverStack = icon;
            hoverArea = new Rect2i(x + 5, y + 3, 16, 16);
        }
        String name;
        if (idle) {
            name = Component.translatable("gui.golem_logix.monitor.idle").getString();
        } else {
            MonitorPayload.Step t = c.targets().isEmpty() ? null : c.targets().get(0);
            name = t == null ? "?" : t.left() + "× " + t.item().getHoverName().getString();
            if (c.targets().size() > 1) {
                name += " +" + (c.targets().size() - 1);
            }
        }
        g.drawString(font, font.plainSubstrByWidth(name, LIST_W - 30), x + 24, y + 3, idle ? 0xFF8C9AA0 : GolemGui.PARCHMENT, false);
        if (!idle) {
            double frac = c.total() <= 0 ? 0 : (double) c.done() / c.total();
            GolemGui.bar(g, x + 24, y + 13, LIST_W - 32, 6, frac, GolemGui.CYAN_DARK);
        } else {
            g.drawString(font, font.plainSubstrByWidth(where(c), LIST_W - 30), x + 24, y + 12, 0xFF5C6B70, false);
        }
    }

    /** Где вычислитель: координаты блока, у ядра мейнфрейма — ещё и номер ядра. */
    private static String where(MonitorPayload.Cpu c) {
        return c.core() > 0 ? c.pos().toShortString() + " #" + c.core() : c.pos().toShortString();
    }

    private void drawDetails(GuiGraphics g, MonitorPayload.Cpu c, int x, int y, int mx, int my) {
        int w = W - DX - 13;
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("gui.golem_logix.monitor.cpu", where(c)).getString(), w),
                x, y, GolemGui.CYAN_DARK, false);
        y += 11;
        // сопроцессоры калькулятора — значок с числом у правого края (под ним строка заказа)
        String cop = "×" + c.coprocessors();
        int copX = x + w - 18 - font.width(cop);
        if (c.state() < 0) {
            drawCoprocessors(g, cop, copX, y, mx, my);
            g.drawWordWrap(font, Component.translatable("gui.golem_logix.calc.idle"), x, y + 19, copX - x - 2, GolemGui.CYAN);
            return;
        }
        // заказ: предметы и сколько
        int ix = x;
        for (MonitorPayload.Step t : c.targets()) {
            if (ix + 18 > copX - 2) {
                break;
            }
            stepIcon(g, t, ix, y, mx, my);
            ix += 18;
        }
        drawCoprocessors(g, cop, copX, y, mx, my);
        y += 19;
        CraftingJob.Status st = CalculatorScreen.status(c.state());
        g.drawString(font, Component.translatable("gui.golem_logix.calc.status." + st.name().toLowerCase(Locale.ROOT)), x, y,
                CalculatorScreen.statusColor(st), false);
        y += 11;
        double frac = c.total() <= 0 ? 0 : (double) c.done() / c.total();
        GolemGui.bar(g, x, y, w, 10, frac, GolemGui.CYAN_DARK);
        g.drawCenteredString(font, c.done() + " / " + c.total(), x + w / 2, y + 1, GolemGui.PARCHMENT);
        y += 13;
        if (!c.current().isEmpty()) {
            g.drawString(font, Component.translatable("gui.golem_logix.monitor.now"), x, y + 4, GolemGui.PARCHMENT, false);
            int nx = x + font.width(Component.translatable("gui.golem_logix.monitor.now")) + 3;
            g.renderItem(c.current(), nx, y);
            if (mx >= nx && mx < nx + 16 && my >= y && my < y + 16) {
                hoverStack = c.current();
                hoverArea = new Rect2i(nx, y, 16, 16);
            }
            g.drawString(font, font.plainSubstrByWidth(c.current().getHoverName().getString(), x + w - nx - 20), nx + 19, y + 4,
                    GolemGui.CYAN, false);
        }
        y += 18;
        if (!c.steps().isEmpty()) {
            g.drawString(font, Component.translatable("gui.golem_logix.monitor.steps"), x, y, GolemGui.PARCHMENT, false);
            y += 10;
            y = stepGrid(g, c.steps(), x, y, w, 3, mx, my);
        }
        if (!c.waiting().isEmpty()) {
            g.drawString(font, Component.translatable("gui.golem_logix.monitor.waiting"), x, y, GolemGui.PARCHMENT, false);
            y += 10;
            stepGrid(g, c.waiting(), x, y, w, 1, mx, my);
        }
    }

    private void drawCoprocessors(GuiGraphics g, String count, int x, int y, int mx, int my) {
        ItemStack icon = new ItemStack(com.golemlogix.init.ModBlocks.COOLING_COPROCESSOR.get());
        g.renderItem(icon, x, y);
        g.drawString(font, count, x + 17, y + 5, GolemGui.PARCHMENT, false);
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            hoverStack = icon;
            hoverArea = new Rect2i(x, y, 16, 16);
        }
    }

    private int stepGrid(GuiGraphics g, List<MonitorPayload.Step> steps, int x, int y, int w, int maxRows, int mx, int my) {
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

    private void stepIcon(GuiGraphics g, MonitorPayload.Step s, int x, int y, int mx, int my) {
        GolemGui.slot(g, x - 1, y - 1);
        g.renderItem(s.item(), x, y);
        String count = GolemGui.formatCount(s.left());
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.drawString(font, count, (x + 17) * 2 - font.width(count), (y + 11) * 2, 0xFFFFFFFF, true);
        g.pose().popPose();
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            hoverStack = s.item();
            hoverArea = new Rect2i(x, y, 16, 16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, GolemGui.PARCHMENT, false);
        int busy = 0;
        for (MonitorPayload.Cpu c : cpus()) {
            if (c.state() >= 0) {
                busy++;
            }
        }
        String s = Component.translatable("gui.golem_logix.monitor.summary", busy, cpus().size()).getString();
        g.drawString(font, s, imageWidth - 8 - font.width(s), titleLabelY, GolemGui.CYAN, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!hoverStack.isEmpty()) {
            g.renderTooltip(font, hoverStack, mouseX, mouseY);
        }
    }

    /** Для JEI (клавиши R и U): предмет под курсором и его место на экране. */
    @Nullable
    public Object[] ingredientAt(double mouseX, double mouseY) {
        if (hoverArea != null && !hoverStack.isEmpty() && hoverArea.contains((int) mouseX, (int) mouseY)) {
            return new Object[]{hoverStack, hoverArea};
        }
        return null;
    }

    // ---------------------------------------------------------------------------------- ввод

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + ROWS * ROW_H) {
            int idx = scroll + (int) ((mouseY - ly) / ROW_H);
            List<MonitorPayload.Cpu> list = cpus();
            if (idx >= 0 && idx < list.size()) {
                selected = list.get(idx).pos();
                selectedCore = list.get(idx).core();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int lx = leftPos + LIST_X, ly = topPos + LIST_Y;
        if (mouseX >= lx && mouseX < lx + LIST_W && mouseY >= ly && mouseY < ly + ROWS * ROW_H) {
            scroll = Math.max(0, Math.min(Math.max(0, cpus().size() - ROWS), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public List<Rect2i> extraAreas() {
        return List.of();
    }

    @SuppressWarnings("unused")
    private static Component hint(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }
}
