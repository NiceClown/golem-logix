package com.golemlogix.client.book;

import com.golemlogix.client.ClientSecrets;
import com.golemlogix.client.FluidIcons;
import com.golemlogix.client.GolemGui;
import com.golemlogix.client.ghost.GhostProjection;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.AtlasBlueprint;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import static com.golemlogix.client.book.BookLayout.INK;
import static com.golemlogix.client.book.BookLayout.INK_DIM;
import static com.golemlogix.client.book.BookLayout.INK_GOLD;
import static com.golemlogix.client.book.BookLayout.LEFT_PAGE_X;
import static com.golemlogix.client.book.BookLayout.LINE_H;
import static com.golemlogix.client.book.BookLayout.PAGES_Y;
import static com.golemlogix.client.book.BookLayout.PAGE_H;
import static com.golemlogix.client.book.BookLayout.PAGE_W;
import static com.golemlogix.client.book.BookLayout.RIGHT_PAGE_X;
import static com.golemlogix.client.book.BookLayout.W;

/**
 * Renders opened guide-book article spreads (parchment pages, recipes, rituals, blueprints, tables,
 * material checklists, trials, illustrations, and page navigation) and handles article clickables.
 */
final class BookPageRenderer {
    private static final ResourceLocation PARCHMENT = BookLayout.tex("parchment");
    private static final Component PREV_TIP = Component.translatable("gui.golem_logix.book.prev");
    private static final Component NEXT_TIP = Component.translatable("gui.golem_logix.book.next");
    private static final Component MAP_TIP = Component.translatable("gui.golem_logix.book.map");
    private static final Component HAVE_ALL = Component.translatable("gui.golem_logix.book.have_all");
    private static final Component HAVE_NOT_ALL = Component.translatable("gui.golem_logix.book.have_not_all");
    private static final Component GHOST_HIDE = Component.translatable("gui.golem_logix.book.ghost_hide");
    private static final Component GHOST_SHOW = Component.translatable("gui.golem_logix.book.ghost_show");

    private record Clickable(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Clickable> clickables = new ArrayList<>();
    private final Map<Item, Integer> inventoryCounts = new IdentityHashMap<>();
    private ItemStack bpBushing, bpConduit, bpAtlant, bpCore, bpCasing;
    private ItemStack[] bpExpanders;

    private ItemStack hoverStack = ItemStack.EMPTY;
    private Component hoverText = null;

    void resetHover() {
        hoverStack = ItemStack.EMPTY;
        hoverText = null;
    }

    ItemStack hoverStack() {
        return hoverStack;
    }

    Component hoverText() {
        return hoverText;
    }

    void drawArticle(GuiGraphics g, Font font, List<BookLayout.View> views, int spread, int spreads,
                     int mx, int my, Runnable closeBook) {
        clickables.clear();
        // spine and pages
        g.fill(LEFT_PAGE_X - 2, PAGES_Y - 2, RIGHT_PAGE_X + PAGE_W + 2, PAGES_Y + PAGE_H + 2, 0xFF1A0F08);
        g.blit(PARCHMENT, LEFT_PAGE_X, PAGES_Y, 0, 0, PAGE_W, PAGE_H, 256, 256);
        g.blit(PARCHMENT, RIGHT_PAGE_X, PAGES_Y, 0, 40, PAGE_W, PAGE_H, 256, 256);
        for (int i = 0; i < 16; i++) {
            int a = (int) (110 * (1 - i / 16f)) << 24;
            g.fill(LEFT_PAGE_X + PAGE_W - i - 1, PAGES_Y, LEFT_PAGE_X + PAGE_W - i, PAGES_Y + PAGE_H, a);
            g.fill(RIGHT_PAGE_X + i, PAGES_Y, RIGHT_PAGE_X + i + 1, PAGES_Y + PAGE_H, a);
        }
        g.fill(W / 2 - 1, PAGES_Y, W / 2 + 1, PAGES_Y + PAGE_H, 0xFF2A1A0E);

        int li = spread * 2;
        if (li < views.size()) {
            drawView(g, font, views.get(li), LEFT_PAGE_X, PAGES_Y, mx, my, li + 1, closeBook);
        }
        if (li + 1 < views.size()) {
            drawView(g, font, views.get(li + 1), RIGHT_PAGE_X, PAGES_Y, mx, my, li + 2, closeBook);
        }

        // navigation
        navButton(g, font, mx, my, LEFT_PAGE_X, "◀", spread > 0, PREV_TIP);
        navButton(g, font, mx, my, RIGHT_PAGE_X + PAGE_W - 40, "▶", spread < spreads - 1, NEXT_TIP);
        navButton(g, font, mx, my, W / 2 - 20, "☰", true, MAP_TIP);
    }

    boolean clickArticle(double bx, double by, int spread, int spreads, IntConsumer setSpread, Runnable closeEntry) {
        for (Clickable c : clickables) {
            if (bx >= c.x() && bx < c.x() + c.w() && by >= c.y() && by < c.y() + c.h()) {
                c.action().run();
                return true;
            }
        }
        int ny = navY();
        if (by >= ny && by < ny + 14) {
            if (bx >= LEFT_PAGE_X && bx < LEFT_PAGE_X + 40 && spread > 0) {
                setSpread.accept(spread - 1);
                return true;
            }
            if (bx >= RIGHT_PAGE_X + PAGE_W - 40 && bx < RIGHT_PAGE_X + PAGE_W && spread < spreads - 1) {
                setSpread.accept(spread + 1);
                return true;
            }
            if (bx >= W / 2 - 20 && bx < W / 2 + 20) {
                closeEntry.run();
                return true;
            }
        }
        return false;
    }

    private static int navY() {
        return PAGES_Y + PAGE_H + 6;
    }

    private void navButton(GuiGraphics g, Font font, int mx, int my, int x, String label, boolean enabled, Component tip) {
        int w = 40, h = 14, y = navY();
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, 0xFF1A0F08);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, !enabled ? 0xFF2C1D12 : hover ? GolemGui.BRASS_L : GolemGui.BRASS);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, !enabled ? 0xFF33241A : 0xFF3A2314);
        g.drawCenteredString(font, label, x + w / 2, y + 3, !enabled ? 0xFF6A5640 : hover ? 0xFFFFE9A8 : GolemGui.PARCHMENT);
        if (hover) {
            hoverText = tip;
        }
    }

    private void drawView(GuiGraphics g, Font font, BookLayout.View v, int x, int y, int mx, int my,
                          int pageNo, Runnable closeBook) {
        switch (v.kind()) {
            case TEXT -> {
                if (v.hh() > 0) {
                    heading(g, font, v, x, y);
                }
            }
            case CRAFTING -> drawCrafting(g, font, v, x, y, mx, my);
            case IMAGE -> drawImage(g, font, v, x, y);
            case RITUAL -> drawRitual(g, font, v, x, y, mx, my);
            case BLUEPRINT -> drawBlueprint(g, font, v, x, y, mx, my);
            case TABLE -> drawTable(g, font, v, x, y);
            case SPOTLIGHT -> drawSpotlight(g, font, v, x, y, mx, my);
            case CHECKLIST -> drawChecklist(g, font, v, x, y, mx, my, closeBook);
            case TASKS -> drawTasks(g, font, v, x, y);
        }
        for (BookLayout.Line l : v.lines()) {
            g.drawString(font, l.seq(), x + l.x(), y + l.y(), l.color(), false);
        }
        String no = Integer.toString(pageNo);
        g.drawString(font, no, x + (pageNo % 2 == 1 ? 8 : PAGE_W - 8 - font.width(no)), y + PAGE_H - 11, INK_DIM, false);
    }

    private static void heading(GuiGraphics g, Font font, BookLayout.View v, int x, int y) {
        int yy = y + 7;
        for (FormattedCharSequence l : v.headingLines()) {
            g.drawString(font, l, x + 6, yy, INK_GOLD, false);
            yy += LINE_H;
        }
        g.fill(x + 6, yy, x + PAGE_W - 6, yy + 1, 0x80804A0A);
    }

    private void drawCrafting(GuiGraphics g, Font font, BookLayout.View v, int x, int y, int mx, int my) {
        BookLayout.Grid grid = (BookLayout.Grid) v.extra();
        heading(g, font, v, x, y);
        int x0 = x + 30, y0 = y + v.hh() + 6;
        long t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() / 25 : 0;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int sx = x0 + c * 18, sy = y0 + r * 18;
                GolemGui.slot(g, sx - 1, sy - 1);
                ItemStack[] alts = grid.cells()[r * 3 + c];
                if (alts.length > 0) {
                    ItemStack s = alts[(int) (t % alts.length)];
                    g.renderItem(s, sx, sy);
                    if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                        hoverStack = s;
                    }
                }
            }
        }
        // arrow
        int ax = x0 + 58, ay = y0 + 25;
        g.fill(ax, ay, ax + 14, ay + 3, INK_GOLD);
        for (int i = 0; i < 4; i++) {
            g.fill(ax + 14 + i, ay - 3 + i, ax + 15 + i, ay + 6 - i, INK_GOLD);
        }
        // result
        int rx = x0 + 80, ry = y0 + 18;
        GolemGui.slot(g, rx - 1, ry - 1);
        if (!grid.result().isEmpty()) {
            g.renderItem(grid.result(), rx, ry);
            g.renderItemDecorations(font, grid.result(), rx, ry);
            if (mx >= rx && mx < rx + 16 && my >= ry && my < ry + 16) {
                hoverStack = grid.result();
            }
        }
    }

    private void slotWithItem(GuiGraphics g, Font font, ItemStack s, int sx, int sy, int mx, int my) {
        GolemGui.slot(g, sx - 1, sy - 1);
        if (!FluidIcons.drawGhost(g, font, s, sx, sy, true)) {
            g.renderItem(s, sx, sy);
            g.renderItemDecorations(font, s, sx, sy);
        }
        if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
            hoverStack = s;
        }
    }

    private void drawRitual(GuiGraphics g, Font font, BookLayout.View v, int x, int y, int mx, int my) {
        BookLayout.RitualData r = (BookLayout.RitualData) v.extra();
        if (v.hh() > 0) {
            heading(g, font, v, x, y);
        }
        int total = r.inputs().size() * 18 + (r.station().isEmpty() ? 0 : 26) + 24 + r.outputs().size() * 18;
        int cx = x + (PAGE_W - total) / 2, cy = y + v.hh() + 8;
        for (ItemStack in : r.inputs()) {
            slotWithItem(g, font, in, cx + 1, cy, mx, my);
            cx += 18;
        }
        if (!r.station().isEmpty()) {
            g.drawString(font, "+", cx + 3, cy + 4, INK_GOLD, false);
            cx += 12;
            slotWithItem(g, font, r.station(), cx + 1, cy, mx, my);
            cx += 18;
        }
        g.fill(cx + 4, cy + 6, cx + 16, cy + 9, INK_GOLD);
        for (int i = 0; i < 4; i++) {
            g.fill(cx + 16 + i, cy + 3 + i, cx + 17 + i, cy + 12 - i, INK_GOLD);
        }
        cx += 24;
        for (ItemStack out : r.outputs()) {
            slotWithItem(g, font, out, cx + 1, cy, mx, my);
            cx += 18;
        }
        if (!r.condition().isEmpty()) {
            int yy = y + v.hh() + 8 + 22;
            for (FormattedCharSequence l : r.condition()) {
                g.drawString(font, l, x + 8, yy, INK_DIM, false);
                yy += LINE_H;
            }
        }
    }

    private void drawSpotlight(GuiGraphics g, Font font, BookLayout.View v, int x, int y, int mx, int my) {
        ItemStack s = (ItemStack) v.extra();
        heading(g, font, v, x, y);
        int iy = y + v.hh() + 6;
        g.pose().pushPose();
        g.pose().translate(x + PAGE_W / 2f - 24, iy, 0);
        g.pose().scale(3f, 3f, 1f);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
        if (mx >= x + PAGE_W / 2 - 24 && mx < x + PAGE_W / 2 + 24 && my >= iy && my < iy + 48) {
            hoverStack = s;
        }
    }

    private void ensureBlueprintIcons() {
        if (bpExpanders == null) {
            bpBushing = new ItemStack(ModBlocks.CABLE_BUSHING.get());
            bpConduit = new ItemStack(ModBlocks.ATLAS_CONDUIT.get());
            bpAtlant = new ItemStack(ModBlocks.ATLAS_ATLANT.get());
            bpCore = new ItemStack(ModBlocks.ATLAS_CORE.get());
            bpCasing = new ItemStack(ModBlocks.ATLAS_CASING.get());
            bpExpanders = new ItemStack[]{bpCasing, new ItemStack(ModBlocks.CHANNEL_EXPANDER.get()),
                    new ItemStack(ModBlocks.QUANTUM_CHANNEL_EXPANDER.get())};
        }
    }

    private void drawBlueprint(GuiGraphics g, Font font, BookLayout.View v, int x, int y, int mx, int my) {
        JsonObject d = v.data();
        int layer = d.get("layer").getAsInt();
        if (v.hh() > 0) {
            heading(g, font, v, x, y);
        }
        ensureBlueprintIcons();
        long t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() / 25 : 0;
        int cell = 24;
        int gx = x + (PAGE_W - cell * 3) / 2, gy = y + v.hh() + 14;
        g.drawCenteredString(font, "▲ N", x + PAGE_W / 2, gy - 11, INK_DIM);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                boolean corner = r != 1 && c != 1, center = r == 1 && c == 1;
                ItemStack s = switch (layer) {
                    case 0 -> corner || center ? bpBushing : bpConduit;
                    case 1, 2 -> corner ? bpAtlant : center ? bpConduit : bpExpanders[(int) ((t + r * 3 + c) % 3)];
                    default -> center ? bpCore : bpCasing;
                };
                int sx = gx + c * cell + 3, sy = gy + r * cell + 3;
                g.fill(sx - 3, sy - 3, sx + cell - 3, sy + cell - 3, 0xFF2A1A0E);
                g.fill(sx - 2, sy - 2, sx + cell - 4, sy + cell - 4, 0xFF16222B);
                g.renderItem(s, sx, sy);
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    hoverStack = s;
                }
            }
        }
    }

    private static void drawTable(GuiGraphics g, Font font, BookLayout.View v, int x, int y) {
        BookLayout.TableData t = (BookLayout.TableData) v.extra();
        int yy = y + 8;
        if (v.hh() > 0) {
            heading(g, font, v, x, y);
            yy = y + v.hh() + 4;
        }
        int[] widths = t.widths();
        int xx = x + 6;
        for (int c = 0; c < t.headerCells().size(); c++) {
            List<FormattedCharSequence> ls = t.headerCells().get(c);
            for (int i = 0; i < ls.size(); i++) {
                g.drawString(font, ls.get(i), xx, yy + i * LINE_H, INK_GOLD, false);
            }
            xx += widths[c];
        }
        yy += t.headerLines() * LINE_H + 1;
        g.fill(x + 6, yy - 1, x + PAGE_W - 6, yy, 0x80804A0A);
        for (int idx = 0; idx < t.rows().size(); idx++) {
            List<List<FormattedCharSequence>> cells = t.rows().get(idx);
            int rl = t.rowLines()[idx];
            if (idx % 2 == 0) {
                g.fill(x + 5, yy - 1, x + PAGE_W - 5, yy + rl * LINE_H, 0x18804A0A);
            }
            xx = x + 6;
            for (int c = 0; c < cells.size(); c++) {
                List<FormattedCharSequence> ls = cells.get(c);
                for (int i = 0; i < ls.size(); i++) {
                    g.drawString(font, ls.get(i), xx, yy + i * LINE_H, INK, false);
                }
                xx += widths[c];
            }
            yy += rl * LINE_H + 2;
        }
    }

    /** Counts all items in the player's inventory (main, hotbar, offhand) in one pass. */
    private void countInventory() {
        inventoryCounts.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty()) {
                inventoryCounts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (!s.isEmpty()) {
                inventoryCounts.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
    }

    private static void drawCheck(GuiGraphics g, int x, int y, boolean ok) {
        if (ok) {
            int c = 0xFF2E7D32;
            g.fill(x, y + 4, x + 2, y + 6, c);
            g.fill(x + 2, y + 6, x + 4, y + 8, c);
            g.fill(x + 4, y + 4, x + 6, y + 6, c);
            g.fill(x + 6, y + 2, x + 8, y + 4, c);
            g.fill(x + 8, y, x + 10, y + 2, c);
        } else {
            int c = 0xFF9B2C2C;
            for (int i = 0; i < 8; i++) {
                g.fill(x + i, y + i, x + i + 2, y + i + 1, c);
                g.fill(x + 7 - i, y + i, x + 9 - i, y + i + 1, c);
            }
        }
    }

    private void drawChecklist(GuiGraphics g, Font font, BookLayout.View v, int x, int y, int mx, int my,
                               Runnable closeBook) {
        if (v.hh() > 0) {
            heading(g, font, v, x, y);
        }
        BookLayout.ChecklistData cl = (BookLayout.ChecklistData) v.extra();
        countInventory();
        int yy = y + v.hh() + 4;
        boolean all = true;
        for (BookLayout.ChecklistRow row : cl.rows()) {
            int have = inventoryCounts.getOrDefault(row.item(), 0);
            boolean ok = have >= row.need();
            all &= ok;
            String counts = have + "/" + row.need();
            g.fill(x + 6, yy, x + 24, yy + 18, 0xFF2A1A0E);
            g.fill(x + 7, yy + 1, x + 23, yy + 17, 0xFF16222B);
            g.renderItem(row.icon(), x + 7, yy + 1);
            g.drawString(font, font.plainSubstrByWidth(row.name(), PAGE_W - 28 - 24 - font.width(counts) - 4),
                    x + 28, yy + 5, INK, false);
            g.drawString(font, counts, x + PAGE_W - 22 - font.width(counts), yy + 5, ok ? 0xFF2E7D32 : 0xFF9B2C2C, false);
            drawCheck(g, x + PAGE_W - 18, yy + 4, ok);
            if (mx >= x + 7 && mx < x + 23 && my >= yy + 1 && my < yy + 17) {
                hoverStack = row.icon();
            }
            yy += 20;
        }
        if (cl.optionalIcon() != null) {
            g.fill(x + 6, yy, x + 24, yy + 18, 0xFF2A1A0E);
            g.fill(x + 7, yy + 1, x + 23, yy + 17, 0xFF16222B);
            g.renderItem(cl.optionalIcon(), x + 7, yy + 1);
            String counts = Integer.toString(inventoryCounts.getOrDefault(cl.optionalIcon().getItem(), 0));
            g.drawString(font, font.plainSubstrByWidth(cl.optionalName(), PAGE_W - 28 - 24 - font.width(counts) - 4),
                    x + 28, yy + 5, INK_DIM, false);
            g.drawString(font, counts, x + PAGE_W - 22 - font.width(counts), yy + 5, INK_DIM, false);
            if (mx >= x + 7 && mx < x + 23 && my >= yy + 1 && my < yy + 17) {
                hoverStack = cl.optionalIcon();
            }
            yy += 20;
        }
        // total
        drawCheck(g, x + 8, yy + 4, all);
        g.drawString(font, all ? HAVE_ALL : HAVE_NOT_ALL, x + 24, yy + 5, all ? 0xFF2E7D32 : 0xFF9B2C2C, false);
        yy += 18;
        // ghost button
        AtlasBlueprint.Variant var = cl.variant();
        boolean showing = GhostProjection.isShowing(var);
        int bx = x + 8, bw = PAGE_W - 16, bh = 16;
        boolean hover = mx >= bx && mx < bx + bw && my >= yy && my < yy + bh;
        g.fill(bx, yy, bx + bw, yy + bh, 0xFF1A0F08);
        g.fill(bx + 1, yy + 1, bx + bw - 1, yy + bh - 1, hover ? GolemGui.CYAN : GolemGui.BRASS);
        g.fill(bx + 2, yy + 2, bx + bw - 2, yy + bh - 2, showing ? 0xFF1B4A48 : 0xFF3A2314);
        g.drawCenteredString(font, showing ? GHOST_HIDE : GHOST_SHOW, bx + bw / 2, yy + 4, hover ? 0xFFFFE9A8 : GolemGui.PARCHMENT);
        clickables.add(new Clickable(bx, yy, bw, bh, () -> {
            if (showing) {
                GhostProjection.clear();
            } else {
                GhostProjection.start(var);
                closeBook.run();
            }
        }));
    }

    private static boolean taskDone(String id) {
        return switch (id) {
            case "wither" -> ClientSecrets.wither();
            case "dimension" -> ClientSecrets.dimension();
            default -> false;
        };
    }

    /** The list of trials with marks: passed = a green tick and struck-through text, not yet = an empty brass frame. */
    private static void drawTasks(GuiGraphics g, Font font, BookLayout.View v, int x, int y) {
        if (v.hh() > 0) {
            heading(g, font, v, x, y);
        }
        int yy = y + v.hh() + 4;
        for (BookLayout.TaskRow task : ((BookLayout.TasksData) v.extra()).rows()) {
            boolean done = taskDone(task.id());
            g.fill(x + 7, yy, x + 19, yy + 12, 0xFF2A1A0E);
            g.fill(x + 8, yy + 1, x + 18, yy + 11, done ? 0xFFE8F5E0 : 0xFFF2E6C8);
            if (done) {
                drawCheck(g, x + 8, yy + 2, true);
            }
            int ty = yy + 2;
            for (FormattedCharSequence l : (done ? task.doneLines() : task.pendingLines())) {
                g.drawString(font, l, x + 24, ty, done ? INK_DIM : INK, false);
                ty += LINE_H;
            }
            yy = Math.max(yy + 14, ty) + 4;
        }
    }

    private static void drawImage(GuiGraphics g, Font font, BookLayout.View v, int x, int y) {
        BookLayout.ImageData img = (BookLayout.ImageData) v.extra();
        int size = img.size();
        int top = y + 8;
        if (v.hh() > 0) {
            heading(g, font, v, x, y);
            top = y + v.hh() + 4;
        }
        int ix = x + (PAGE_W - size) / 2;
        g.fill(ix - 2, top - 2, ix + size + 2, top + size + 2, 0x30301808);
        g.blit(img.texture(), ix, top, size, size, 0, 0, 288, 288, 288, 288);
    }
}
