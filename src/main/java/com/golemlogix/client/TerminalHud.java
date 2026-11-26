package com.golemlogix.client;

import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.system.Gear;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the terminal's status bar, wireless Source charge gauge, fluid droplet badges, and hover tooltips.
 * Formatted status strings are cached per synced {@link TerminalInfoPayload} to avoid per-frame allocations.
 */
final class TerminalHud {
    private static final List<Component> GEAR_TOOLTIP = List.of(
            Component.translatable("gui.golem_logix.gear.title").withStyle(ChatFormatting.YELLOW),
            Component.translatable("gui.golem_logix.gear.why", Gear.COST).withStyle(ChatFormatting.GRAY),
            Component.translatable("gui.golem_logix.gear.click").withStyle(ChatFormatting.DARK_AQUA));
    private static final Component FLUID_HINT = Component.translatable("gui.golem_logix.fluid_hint").withStyle(ChatFormatting.DARK_AQUA);
    private static final Component CRAFTABLE_HINT = Component.translatable("gui.golem_logix.craftable_hint").withStyle(ChatFormatting.AQUA);
    private static final List<Component> RESONATOR_TIP = List.of(
            Component.translatable("tooltip.golem_logix.chronometer.resonator").withStyle(ChatFormatting.LIGHT_PURPLE));

    private TerminalInfoPayload cachedInfo;
    private TerminalCatalog.ViewMode cachedMode;
    private Language cachedLang;
    private String cachedItemsText = "";
    private String cachedBucketsText = "";
    private String cachedSingleText = "";
    private String cachedChannelsText = "";
    private Component cachedError = Component.empty();

    private void ensureInfoCache(TerminalInfoPayload info, TerminalCatalog.ViewMode mode) {
        Language lang = Language.getInstance();
        if (info == cachedInfo && mode == cachedMode && lang == cachedLang) {
            return;
        }
        cachedInfo = info;
        cachedMode = mode;
        cachedLang = lang;
        if (!info.error().isEmpty()) {
            cachedError = Component.translatable(info.error());
            return;
        }
        cachedError = Component.empty();
        cachedChannelsText = Component.translatable("gui.golem_logix.channels_short",
                info.channelsUsed(), info.channelCapacity()).getString();
        if (mode == TerminalCatalog.ViewMode.ALL) {
            cachedItemsText = GolemGui.formatCount(info.itemsUsed()) + "/" + GolemGui.formatCount(info.itemsCapacity());
            cachedBucketsText = GolemGui.formatBuckets(info.fluidsUsed()) + "/" + GolemGui.formatBuckets(info.fluidsCapacity());
        } else if (mode == TerminalCatalog.ViewMode.FLUIDS) {
            cachedSingleText = GolemGui.formatCount(info.fluidsUsed()) + "/" + GolemGui.formatCount(info.fluidsCapacity()) + " mB";
        } else {
            cachedSingleText = GolemGui.formatCount(info.itemsUsed()) + "/" + GolemGui.formatCount(info.itemsCapacity());
        }
    }

    private static int fillColor(double frac) {
        return frac > 0.9 ? GolemGui.RED : frac > 0.7 ? GolemGui.COPPER : GolemGui.CYAN_DARK;
    }

    /** Draws the network capacity and channel status bar beneath the storage grid. */
    void renderInfo(GuiGraphicsExtractor g, Font font, @Nullable TerminalInfoPayload info,
                    TerminalCatalog.ViewMode viewMode, int ix, int iy) {
        GolemGui.inset(g, ix, iy, 162, 12);
        if (info == null) {
            return;
        }
        ensureInfoCache(info, viewMode);
        if (!info.error().isEmpty()) {
            g.centeredText(font, cachedError, ix + 81, iy + 2, GolemGui.RED);
            return;
        }
        double itemFrac = info.itemsCapacity() <= 0 ? 0 : (double) info.itemsUsed() / info.itemsCapacity();
        double fluidFrac = info.fluidsCapacity() <= 0 ? 0 : (double) info.fluidsUsed() / info.fluidsCapacity();
        if (viewMode == TerminalCatalog.ViewMode.ALL) {
            // two bars: items on top, fluids below; the numbers are items and buckets
            int wi = (int) Math.round(160 * Math.min(1.0, itemFrac));
            int wf = (int) Math.round(160 * Math.min(1.0, fluidFrac));
            if (wi > 0) {
                g.fill(ix + 1, iy + 1, ix + 1 + wi, iy + 6, fillColor(itemFrac));
            }
            if (wf > 0) {
                g.fill(ix + 1, iy + 6, ix + 1 + wf, iy + 11, fluidFrac > 0.9 ? GolemGui.RED : 0xFF2A6FC8);
            }
            g.text(font, cachedItemsText, ix + 4, iy + 2, GolemGui.PARCHMENT, true);
            int fx = ix + 4 + font.width(cachedItemsText) + 6;
            drawDropAt(g, fx, iy + 3);
            g.text(font, cachedBucketsText, fx + 7, iy + 2, GolemGui.PARCHMENT, true);
            drawChannels(g, font, fx + 7 + font.width(cachedBucketsText) + 6, ix, iy);
            return;
        }
        double frac = viewMode == TerminalCatalog.ViewMode.FLUIDS ? fluidFrac : itemFrac;
        int w = (int) Math.round(160 * Math.min(1.0, frac));
        if (w > 0) {
            g.fill(ix + 1, iy + 1, ix + 1 + w, iy + 11, fillColor(frac));
        }
        g.text(font, cachedSingleText, ix + 4, iy + 2, GolemGui.PARCHMENT, true);
        drawChannels(g, font, ix + 4 + font.width(cachedSingleText) + 6, ix, iy);
    }

    /** "Ch 9/320" at the right edge of the status line, if it does not overlap the text on the left. */
    private void drawChannels(GuiGraphicsExtractor g, Font font, int from, int ix, int iy) {
        int x = ix + 158 - font.width(cachedChannelsText);
        if (x >= from) {
            g.text(font, cachedChannelsText, x, iy + 2, GolemGui.CYAN, true);
        }
    }

    /** A 5x6 drop before the bucket count in the status line. */
    static void drawDropAt(GuiGraphicsExtractor g, int x, int y) {
        int edge = 0xFF0B2A52, body = 0xFF9FE8FF;
        g.fill(x + 1, y, x + 4, y + 1, edge);
        g.fill(x, y + 1, x + 5, y + 5, edge);
        g.fill(x + 1, y + 5, x + 4, y + 6, edge);
        g.fill(x + 2, y + 1, x + 3, y + 2, body);
        g.fill(x + 1, y + 2, x + 4, y + 5, body);
    }

    /** A small drop in the slot corner: tells a fluid from an item in the combined list. */
    static void drawSlotDrop(GuiGraphicsExtractor g, TerminalCatalog.ViewMode viewMode, int x, int y) {
        if (viewMode == TerminalCatalog.ViewMode.FLUIDS) {
            return;
        }
        drawDropAt(g, x, y);
        g.fill(x + 1, y + 2, x + 2, y + 3, 0xFFFFFFFF);
    }

    /** Vertical Source charge flask: a purple column in a brass mount; fed by the Resonator it is full and shimmers. */
    static void renderChargeGauge(GuiGraphicsExtractor g, Minecraft mc, TerminalInfoPayload info, int x, int y, int h) {
        boolean resonator = info.resonator();
        double frac = resonator ? 1.0 : Math.min(1.0, info.charge() / (double) PortableChronometerItem.MAX_CHARGE);
        g.fill(x - 1, y - 1, x + 11, y + h + 1, GolemGui.BRASS_DARK);
        g.fill(x, y, x + 10, y + h, GolemGui.SLOT_BG);
        int fill = (int) Math.round((h - 2) * frac);
        if (fill > 0) {
            float t = mc == null || mc.level == null ? 0 : (mc.level.getGameTime() % 40) / 40f;
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

    /** Draws hover tooltips for the equipment badge, storage entry, status bar, or wireless charge gauge. */
    static void renderTooltips(GuiGraphicsExtractor g, Font font, Minecraft mc, TerminalMenu menu,
                               @Nullable TerminalCatalog.Row hoveredRow, boolean overGearMark, boolean hasChargeGauge,
                               int leftPos, int topPos, int rows, int mouseX, int mouseY) {
        if (overGearMark) {
            g.setComponentTooltipForNextFrame(font, GEAR_TOOLTIP, mouseX, mouseY);
            return;
        }
        if (hoveredRow != null) {
            renderRowTooltip(g, font, mc, hoveredRow, mouseX, mouseY);
            return;
        }
        int iy = topPos + TerminalMenu.infoY(rows);
        if (menu.clientInfo != null && mouseX >= leftPos + TerminalMenu.LIST_X && mouseX < leftPos + TerminalMenu.LIST_X + 162
                && mouseY >= iy && mouseY < iy + 12) {
            TerminalInfoPayload info = menu.clientInfo;
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.golem_logix.info.items", GolemGui.formatCount(info.itemsUsed()), GolemGui.formatCount(info.itemsCapacity())),
                    Component.translatable("gui.golem_logix.info.fluids", GolemGui.formatCount(info.fluidsUsed()), GolemGui.formatCount(info.fluidsCapacity())),
                    Component.translatable("gui.golem_logix.info.channels", info.channelsUsed(), info.channelCapacity()),
                    Component.translatable("gui.golem_logix.info.cpu", info.cpuBusy(), info.cpuTotal())), mouseX, mouseY);
            return;
        }
        if (hasChargeGauge) {
            int cy = topPos + TerminalMenu.craftY(rows);
            int sx = leftPos + TerminalMenu.GAUGE_X;
            if (mouseX >= sx - 1 && mouseX < sx + 11 && mouseY >= cy - 1 && mouseY < cy + 55) {
                g.setComponentTooltipForNextFrame(font, menu.clientInfo.resonator() ? RESONATOR_TIP
                        : List.of(Component.translatable("tooltip.golem_logix.chronometer.charge", menu.clientInfo.charge(),
                        PortableChronometerItem.MAX_CHARGE).withStyle(ChatFormatting.LIGHT_PURPLE)), mouseX, mouseY);
            }
        }
    }

    private static void renderRowTooltip(GuiGraphicsExtractor g, Font font, Minecraft mc,
                                         TerminalCatalog.Row row, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (row.isFluid()) {
            FluidStack f = row.fluid();
            lines.add(f.getHoverName());
            int stored = row.count() <= 0 ? 0 : f.getAmount();
            lines.add(Component.translatable("gui.golem_logix.stored", Component.translatable("gui.golem_logix.stored_fluid",
                    GolemGui.formatBuckets(stored), String.format(Locale.US, "%,d", stored))).withStyle(ChatFormatting.GRAY));
            if (stored > 0) {
                lines.add(FLUID_HINT);
            }
            if (row.craftable()) {
                lines.add(CRAFTABLE_HINT);
            }
        } else {
            lines.addAll(Screen.getTooltipFromItem(mc, row.stack()));
            lines.add(Component.translatable("gui.golem_logix.stored", String.format(Locale.US, "%,d", row.count()))
                    .withStyle(ChatFormatting.GRAY));
            if (row.craftable()) {
                lines.add(CRAFTABLE_HINT);
            }
        }
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }
}
