package com.golemlogix.client;

import com.golemlogix.blockentity.VisCondenserBlockEntity;
import com.golemlogix.menu.VisCondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * Vis Condenser window: live Aura gauge (Vis bar, Flux overlay, red 10% Flux danger mark, gold stop-floor marker),
 * digital-analog readout of Vis/Flux/floor/Source output and status, plus %/Vis mode toggle and threshold steppers.
 */
public class VisCondenserScreen extends GolemScreen<VisCondenserMenu> {
    private static final int BAR_X = 8, BAR_Y = 16, BAR_W = 160, BAR_H = 16;
    private static final int VIS_COLOR = 0xFF7B52DC, VIS_LIGHT = 0xFFBC9EFF;
    private static final int VIS_DANGER = 0xFFD03848, VIS_DANGER_LIGHT = 0xFFFF7888;
    private static final int FLUX_COLOR = 0xCC9820B8, FLUX_EDGE = 0xFFE860FF;

    public VisCondenserScreen(VisCondenserMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, VisCondenserMenu.IMAGE_WIDTH, VisCondenserMenu.IMAGE_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        int by = topPos + 69;
        addRenderableWidget(new GolemButton(leftPos + 8, by, 44, 14,
                () -> Component.translatable(menu.isPercentMode()
                        ? "gui.golem_logix.vis_condenser.mode.percent"
                        : "gui.golem_logix.vis_condenser.mode.exact"),
                () -> Component.translatable("gui.golem_logix.vis_condenser.mode_tip"),
                () -> false,
                b -> menuButton(VisCondenserMenu.BUTTON_MODE)));
        addRenderableWidget(new GolemButton(leftPos + 54, by, 19, 14, Component.literal("-10"), b -> menuButton(VisCondenserMenu.BUTTON_MINUS_10)));
        addRenderableWidget(new GolemButton(leftPos + 74, by, 17, 14, Component.literal("-5"), b -> menuButton(VisCondenserMenu.BUTTON_MINUS_5)));
        addRenderableWidget(new GolemButton(leftPos + 92, by, 17, 14, Component.literal("-1"), b -> menuButton(VisCondenserMenu.BUTTON_MINUS_1)));
        addRenderableWidget(new GolemButton(leftPos + 111, by, 17, 14, Component.literal("+1"), b -> menuButton(VisCondenserMenu.BUTTON_PLUS_1)));
        addRenderableWidget(new GolemButton(leftPos + 129, by, 17, 14, Component.literal("+5"), b -> menuButton(VisCondenserMenu.BUTTON_PLUS_5)));
        addRenderableWidget(new GolemButton(leftPos + 147, by, 21, 14, Component.literal("+10"), b -> menuButton(VisCondenserMenu.BUTTON_PLUS_10)));
    }

    @Override
    protected void renderExtraBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Live Aura gauge bar
        int bx = leftPos + BAR_X, by = topPos + BAR_Y;
        GolemGui.inset(g, bx, by, BAR_W, BAR_H);
        int innerW = BAR_W - 2;
        double base = Math.max(1.0, menu.base());
        double visFrac = Mth.clamp(menu.vis() / base, 0.0, 1.0);
        double fluxFrac = Mth.clamp(menu.flux() / base, 0.0, 1.0);

        int visW = (int) Math.round(innerW * visFrac);
        if (visW > 0) {
            boolean danger = menu.lowVisWarning();
            g.fill(bx + 1, by + 1, bx + 1 + visW, by + BAR_H - 1, danger ? VIS_DANGER : VIS_COLOR);
            g.fill(bx + 1, by + 1, bx + 1 + visW, by + 3, danger ? VIS_DANGER_LIGHT : VIS_LIGHT);
        }

        // Flux overlay drawn from the right edge inward (showing aura space consumed by Flux)
        int fluxW = (int) Math.round(innerW * fluxFrac);
        if (fluxW > 0) {
            int fx = bx + 1 + innerW - fluxW;
            g.fill(fx, by + 1, bx + 1 + innerW, by + BAR_H - 1, FLUX_COLOR);
            g.fill(fx, by + 1, fx + 1, by + BAR_H - 1, FLUX_EDGE);
        }

        // Subtle brass quarter ticks along bottom of gauge
        for (int k = 1; k < 4; k++) {
            int tx = bx + 1 + innerW * k / 4;
            g.fill(tx, by + BAR_H - 4, tx + 1, by + BAR_H - 1, GolemGui.BRASS_DARK);
        }

        // Red Flux danger line at configured low-Vis percent
        int lowPct = menu.lowVisPercent();
        if (lowPct > 0) {
            int redX = bx + 1 + (int) Math.round(innerW * Mth.clamp(lowPct / 100.0, 0.0, 1.0));
            g.fill(redX, by + 1, redX + 1, by + BAR_H - 1, 0xFFFF3838);
            g.fill(redX - 1, by, redX + 2, by + 1, 0xFFFF3838);
        }

        // Gold stop-floor threshold marker
        double floorFrac = menu.isPercentMode()
                ? Mth.clamp(menu.threshold() / 100.0, 0.0, 1.0)
                : Mth.clamp(menu.threshold() / base, 0.0, 1.0);
        int floorX = bx + 1 + (int) Math.round(innerW * floorFrac);
        g.fill(floorX, by + 1, floorX + 1, by + BAR_H - 1, GolemGui.BRASS_L);
        g.fill(floorX - 1, by, floorX + 2, by + 2, GolemGui.BRASS_L);
        g.fill(floorX - 1, by + BAR_H - 2, floorX + 2, by + BAR_H, GolemGui.BRASS_L);

        // Readout inset box below gauge
        GolemGui.inset(g, leftPos + 8, topPos + 34, 160, 32);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int x = 12, right = 164;
        String visStr = fmt1(menu.vis());
        String fluxStr = fmt1(menu.flux());
        String sourceStr = fmtClean(menu.sourcePerSecond());

        Component auraText = Component.translatable("gui.golem_logix.vis_condenser.aura", visStr, menu.base());
        Component fluxText = Component.translatable("gui.golem_logix.vis_condenser.flux", fluxStr);
        int fluxW = font.width(fluxText);
        fitted(g, auraText, x, 37, Math.max(40, right - x - fluxW - 4), GolemGui.PARCHMENT);
        g.drawString(font, fluxText, right - fluxW, 37, menu.flux() > 0.05 ? 0xFFD870FF : 0xFFA89880, false);

        Component floorText = menu.isPercentMode()
                ? Component.translatable("gui.golem_logix.vis_condenser.floor_pct", menu.threshold())
                : Component.translatable("gui.golem_logix.vis_condenser.floor_vis", menu.threshold());
        Component rateText = Component.translatable("gui.golem_logix.vis_condenser.source", sourceStr);
        int rateW = font.width(rateText);
        fitted(g, floorText, x, 46, Math.max(40, right - x - rateW - 4), GolemGui.BRASS_L);
        g.drawString(font, rateText, right - rateW, 46, GolemGui.CYAN, false);

        int status = menu.status();
        int statusColor = switch (status) {
            case VisCondenserBlockEntity.STATUS_WORKING -> GolemGui.GREEN;
            case VisCondenserBlockEntity.STATUS_LOW_VIS_WORKING, VisCondenserBlockEntity.STATUS_NO_THAUMATURGE -> GolemGui.RED;
            default -> GolemGui.COPPER;
        };
        Component statusComp = menu.lowVisWarning() && status == VisCondenserBlockEntity.STATUS_LOW_VIS_WORKING
                ? Component.translatable("gui.golem_logix.vis_condenser.low_vis_warn")
                : Component.translatable("gui.golem_logix.vis_condenser.status." + status);
        fitted(g, statusComp, x, 56, right - x, statusColor);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        super.renderTooltip(g, mouseX, mouseY);
        if (mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + BAR_W
                && mouseY >= topPos + BAR_Y && mouseY < topPos + BAR_Y + BAR_H) {
            g.renderTooltip(font, Component.translatable("gui.golem_logix.vis_condenser.aura_tip",
                    fmt1(menu.vis()), menu.base(), fmt1(menu.flux())), mouseX, mouseY);
        }
    }

    private void fitted(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int w = font.width(text);
        if (w <= maxWidth) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float s = maxWidth / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1 - s) * 4, 0);
        g.pose().scale(s, s, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmtClean(double v) {
        return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%.1f", v);
    }
}
