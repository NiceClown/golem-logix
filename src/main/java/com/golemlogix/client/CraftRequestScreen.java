package com.golemlogix.client;

import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.CraftPlanPayload;
import com.golemlogix.network.CraftRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Autocraft order window with a resource planner (like the AE2 / RS craft confirmation screen): on open and on every
 * amount change the server sends a plan: what will be taken from the network, what will be crafted, what is missing (in red).
 * While something is missing the order cannot be started; if starting failed (no free calculator, the network is unpowered),
 * the reason shows right here, not in chat. A started order closes the window.
 */
public class CraftRequestScreen extends Screen {
    private static final int W = 232;
    private static final int H = 212;
    private static final int GRID_X = 8, GRID_Y = 84, CELL_W = 104, CELL_H = 24, COLS = 2, ROWS = 4;
    private static final int RED = 0xFFE05A4F, STORED = 0xFFB8C8A8;
    private static final int[] ITEM_DELTAS = {-100, -10, -1, 1, 10, 100};
    private static final Component LABEL_CALCULATING = Component.translatable("gui.golem_logix.plan.calculating");
    private static final Component LABEL_START = Component.translatable("gui.golem_logix.craft_start");
    private static final Component LABEL_START_HINT = Component.translatable("gui.golem_logix.plan.start_hint");
    private static final Component LABEL_START_BLOCKED = Component.translatable("gui.golem_logix.plan.start_blocked");
    private static final Component LABEL_CANCEL = Component.translatable("gui.golem_logix.cancel");

    private static int nextRequest = 1;

    private final TerminalScreen parent;
    private final TerminalMenu menu;
    private final ItemStack key;
    private final boolean fluid;
    private EditBox amount;
    private GolemButton startButton;
    private int left;
    private int top;
    private int scroll = 0;
    private String keyNameTruncated = "";
    /** Number of the last plan request sent and of the start request (-1 = no start yet). */
    private int lastRequest = -1, startRequest = -1;
    /** In how many ticks to send a preview after the amount was edited (-1 = not needed). */
    private int previewIn = -1;
    private int lastAmount = -1;
    private CraftPlanPayload cachedPlan;
    private List<List<Component>> cachedCellLines = List.of();
    private String cachedHeaderText = "";

    public CraftRequestScreen(TerminalScreen parent, TerminalMenu menu, ItemStack key) {
        super(Component.translatable("gui.golem_logix.craft_request"));
        this.parent = parent;
        this.menu = menu;
        this.key = key.copyWithCount(1);
        this.fluid = FluidGhostItem.is(this.key);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        keyNameTruncated = font.plainSubstrByWidth(key.getHoverName().getString(), W - 104);

        String old = amount != null ? amount.getValue() : fluid ? "1000" : "1";
        amount = SlotAmountHelper.createAmountBox(font, left + 33, top + 34, 56, 7, "\\d{1,7}", old);
        amount.setResponder(s -> previewIn = 6);
        addRenderableWidget(amount);
        setInitialFocus(amount);

        // a fluid in mB, its steps a tenth of a bucket to ten buckets
        int[] deltas = fluid ? SlotAmountHelper.FLUID_DELTAS : ITEM_DELTAS;
        for (int i = 0; i < deltas.length; i++) {
            int d = deltas[i];
            String label = fluid && Math.abs(d) >= 1000 ? SlotAmountHelper.deltaLabel(d) : (d > 0 ? "+" : "") + d;
            addRenderableWidget(new GolemButton(left + 8 + i * 36, top + 52, 34, 14, Component.literal(label), b -> change(d)));
        }
        startButton = new GolemButton(left + 8, top + 188, 104, 18, () -> LABEL_START,
                () -> canStart() ? LABEL_START_HINT : LABEL_START_BLOCKED, this::canStart, b -> start());
        addRenderableWidget(startButton);
        addRenderableWidget(new GolemButton(left + 120, top + 188, 104, 18, LABEL_CANCEL, b -> onClose()));
        if (lastRequest < 0) {
            requestPreview();
        }
    }

    /** For the screenshot rig: set the amount (the plan is recomputed). */
    public void setAmountForDev(int n) {
        amount.setValue(Integer.toString(n));
    }

    private int value() {
        try {
            return Math.max(1, Integer.parseInt(amount.getValue()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void change(int delta) {
        amount.setValue(Integer.toString(Math.max(1, Math.min(9_999_999, value() + delta))));
    }

    private void requestPreview() {
        lastRequest = nextRequest++;
        lastAmount = value();
        scroll = 0;
        PacketDistributor.sendToServer(new CraftRequestPayload(menu.containerId, key, value(), true, lastRequest));
    }

    /** Fresh plan (the answer to the last request), or null while the server is computing. */
    private CraftPlanPayload plan() {
        CraftPlanPayload p = menu.clientPlan;
        if (p == null) {
            return null;
        }
        return p.request() == lastRequest || (startRequest >= 0 && p.request() == startRequest) ? p : null;
    }

    private boolean canStart() {
        CraftPlanPayload p = plan();
        return p != null && startRequest < 0 && previewIn < 0 && lastAmount == value() && p.status() == CraftPlanPayload.Status.OK;
    }

    private void start() {
        if (!canStart()) {
            return;
        }
        startRequest = nextRequest++;
        lastRequest = startRequest;
        PacketDistributor.sendToServer(new CraftRequestPayload(menu.containerId, key, value(), false, startRequest));
    }

    @Override
    public void tick() {
        super.tick();
        if (previewIn > 0 && --previewIn == 0) {
            previewIn = -1;
            startRequest = -1;
            requestPreview();
        }
        CraftPlanPayload p = plan();
        if (p != null && startRequest >= 0 && p.request() == startRequest) {
            if (p.status() == CraftPlanPayload.Status.STARTED) {
                onClose();
                return;
            }
            // the start failed: stay in the window with the reason; a new start only after a new preview
            startRequest = -1;
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private static int maxScroll(CraftPlanPayload p) {
        int rows = (p.entries().size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    private void ensurePlanCache(CraftPlanPayload p) {
        if (p == cachedPlan) {
            return;
        }
        cachedPlan = p;
        Component head = p.status() == CraftPlanPayload.Status.OK
                ? Component.translatable("gui.golem_logix.plan.summary", p.steps(), GolemGui.formatCount(p.executions()))
                : Component.translatable(p.status().key());
        cachedHeaderText = font.plainSubstrByWidth(head.getString(), W - 16);
        List<CraftPlanPayload.Entry> entries = p.entries();
        List<List<Component>> built = new ArrayList<>(entries.size());
        for (CraftPlanPayload.Entry e : entries) {
            built.add(cellLines(e));
        }
        cachedCellLines = built;
    }

    /** Background: the world blur (Screen.render calls it itself) and the panel, before buttons and labels so the blur does not touch them. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        GolemGui.panel(g, left, top, W, H);
        GolemGui.slot(g, left + 8, top + 28);
        GolemGui.inset(g, left + 30, top + 30, 62, 16);
        GolemGui.inset(g, left + GRID_X - 2, top + GRID_Y - 2, COLS * CELL_W + 4, ROWS * CELL_H + 4);
        int sx = left + GRID_X + COLS * CELL_W + 5;
        GolemGui.inset(g, sx - 1, top + GRID_Y - 1, 8, ROWS * CELL_H + 2);
        CraftPlanPayload p = plan();
        if (p != null) {
            int max = maxScroll(p);
            int h = ROWS * CELL_H;
            if (max > 0) {
                int handle = Math.max(12, h * ROWS / (ROWS + max));
                int hy = top + GRID_Y + (h - handle) * Math.min(scroll, max) / max;
                g.fill(sx, hy, sx + 6, hy + handle, GolemGui.BRASS);
            } else {
                g.fill(sx, top + GRID_Y, sx + 6, top + GRID_Y + h, GolemGui.BRASS_DARK);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        startButton.active = canStart();
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + 8, top + 7, GolemGui.PARCHMENT, false);
        if (!FluidIcons.drawGhost(g, font, key, left + 9, top + 29, false)) {
            g.renderItem(key, left + 9, top + 29);
        }
        g.drawString(font, keyNameTruncated, left + 98, top + 34, GolemGui.CYAN, false);
        if (fluid) {
            g.drawString(font, "mB", left + 76, top + 34, 0xFF8A7A60, false);
        }

        CraftPlanPayload p = plan();
        int hy = top + 72;
        if (p == null) {
            g.drawString(font, LABEL_CALCULATING, left + 8, hy, 0xFF8A7A60, false);
            return;
        }
        ensurePlanCache(p);
        int statusColor = p.status() == CraftPlanPayload.Status.OK || p.status() == CraftPlanPayload.Status.STARTED ? GolemGui.CYAN : RED;
        g.drawString(font, cachedHeaderText, left + 8, hy, statusColor, false);

        List<CraftPlanPayload.Entry> entries = p.entries();
        if (entries.isEmpty()) {
            return;
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll(p)));
        CraftPlanPayload.Entry hovered = drawPlanGrid(g, entries, mouseX, mouseY);
        if (hovered != null) {
            drawEntryTooltip(g, hovered, mouseX, mouseY);
        }
    }

    private CraftPlanPayload.Entry drawPlanGrid(GuiGraphics g, List<CraftPlanPayload.Entry> entries, int mouseX, int mouseY) {
        Language lang = Language.getInstance();
        CraftPlanPayload.Entry hovered = null;
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                int idx = (scroll + r) * COLS + col;
                if (idx >= entries.size()) {
                    return hovered;
                }
                CraftPlanPayload.Entry e = entries.get(idx);
                int x = left + GRID_X + col * CELL_W, y = top + GRID_Y + r * CELL_H;
                if (e.missing() > 0) {
                    g.fill(x, y, x + CELL_W - 1, y + CELL_H - 1, 0x40E05A4F);
                }
                if (!FluidIcons.drawGhost(g, font, e.stack(), x + 3, y + 4, false)) {
                    g.renderItem(e.stack(), x + 3, y + 4);
                }
                List<Component> lines = cachedCellLines.get(idx);
                for (int k = 0; k < Math.min(2, lines.size()); k++) {
                    Component line = lines.get(k);
                    int color = line.getStyle().getColor() == null ? GolemGui.PARCHMENT
                            : line.getStyle().getColor().getValue() | 0xFF000000;
                    g.drawString(font, lang.getVisualOrder(font.substrByWidth(line, CELL_W - 24)),
                            x + 22, y + 3 + k * 10, color, false);
                }
                if (mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H) {
                    hovered = e;
                }
            }
        }
        return hovered;
    }

    private void drawEntryTooltip(GuiGraphics g, CraftPlanPayload.Entry hovered, int mouseX, int mouseY) {
        List<Component> tip = new ArrayList<>(4);
        tip.add(hovered.stack().getHoverName());
        if (hovered.stored() > 0) {
            tip.add(Component.translatable("gui.golem_logix.plan.tip.stored", hovered.stored()).withStyle(ChatFormatting.GRAY));
        }
        if (hovered.craft() > 0) {
            tip.add(Component.translatable("gui.golem_logix.plan.tip.craft", hovered.craft()).withStyle(ChatFormatting.AQUA));
        }
        if (hovered.missing() > 0) {
            tip.add(Component.translatable("gui.golem_logix.plan.tip.missing", hovered.missing()).withStyle(ChatFormatting.RED));
        }
        g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    /** Two lines for a cell: the shortage first, then crafting, then what is taken from the network. */
    private static List<Component> cellLines(CraftPlanPayload.Entry e) {
        List<Component> out = new ArrayList<>(3);
        if (e.missing() > 0) {
            out.add(Component.translatable("gui.golem_logix.plan.missing", amount(e, e.missing())).withColor(RED));
        }
        if (e.craft() > 0) {
            out.add(Component.translatable("gui.golem_logix.plan.craft", amount(e, e.craft())).withColor(GolemGui.CYAN));
        }
        if (e.stored() > 0) {
            out.add(Component.translatable("gui.golem_logix.plan.stored", amount(e, e.stored())).withColor(STORED));
        }
        return out;
    }

    /** Items by count, fluids (fluid ghosts) in mB. */
    private static String amount(CraftPlanPayload.Entry e, long n) {
        return FluidGhostItem.is(e.stack()) ? GolemGui.formatCount(n) + " mB" : GolemGui.formatCount(n);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        CraftPlanPayload p = plan();
        if (p != null && sy != 0 && mx >= left + GRID_X && mx < left + GRID_X + COLS * CELL_W + 12
                && my >= top + GRID_Y && my < top + GRID_Y + ROWS * CELL_H) {
            scroll = Math.max(0, Math.min(maxScroll(p), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SlotAmountHelper.isEnterKey(keyCode)) {
            start();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
