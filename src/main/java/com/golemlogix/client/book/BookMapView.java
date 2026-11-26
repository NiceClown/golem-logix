package com.golemlogix.client.book;

import com.golemlogix.client.GolemGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static com.golemlogix.client.book.BookLayout.H;
import static com.golemlogix.client.book.BookLayout.W;

/**
 * Interactive constellation map view for the guide book: per-category pan/zoom, dependency links,
 * pulsing unread nodes, and live entry search dropdown.
 */
final class BookMapView {
    private static final ResourceLocation MAP_BG = BookLayout.tex("map_bg");
    private static final ResourceLocation NODES = BookLayout.tex("nodes");
    private static final int NODE = 26;
    private static final int SPACING = 46;
    private static final double MIN_ZOOM = 0.4, MAX_ZOOM = 2.2;
    private static final int AX = 8, AY = 30, AW = W - 16, AH = H - 40;

    private static final Component SEARCH_NOTHING = Component.translatable("gui.golem_logix.book.nothing");
    private static final Component TIP_READ = Component.translatable("gui.golem_logix.book.read").withStyle(ChatFormatting.DARK_GRAY);
    private static final Component TIP_NEW = Component.translatable("gui.golem_logix.book.new").withStyle(ChatFormatting.AQUA);

    private final double[] panX;
    private final double[] panY;
    private final double[] zoom;
    private boolean dragging = false;
    private double dragMoved;

    private String lastSearchQuery = null;
    private List<BookModel.Entry> cachedSearch = List.of();
    private List<Component> hoverTip = null;
    private BookModel.Entry cachedHoverEntry = null;
    private boolean cachedHoverVisited = false;
    private List<Component> cachedHoverTip = null;
    private long cachedZoomPct = Long.MIN_VALUE;
    private Language cachedZoomLang = null;
    private String cachedZoomText = "";

    BookMapView(BookModel book) {
        int n = Math.max(1, book.categories.size());
        this.panX = new double[n];
        this.panY = new double[n];
        this.zoom = new double[n];
        Arrays.fill(this.zoom, 1.0);
        for (int i = 0; i < book.categories.size(); i++) {
            centerMap(book, i);
        }
    }

    private void centerMap(BookModel book, int cat) {
        if (book.categories.isEmpty()) {
            return;
        }
        var entries = book.categories.get(cat).entries;
        if (entries.isEmpty()) {
            return;
        }
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (var e : entries) {
            minX = Math.min(minX, e.x);
            maxX = Math.max(maxX, e.x);
            minY = Math.min(minY, e.y);
            maxY = Math.max(maxY, e.y);
        }
        panX[cat] = -(minX + maxX) / 2 * SPACING;
        panY[cat] = -(minY + maxY) / 2 * SPACING;
    }

    void invalidateSearch() {
        lastSearchQuery = null;
    }

    List<Component> hoverTip() {
        return hoverTip;
    }

    private int nodeX(BookModel.Entry e, int categoryIndex, int cx) {
        return cx + (int) Math.round((e.x * SPACING + panX[categoryIndex]) * zoom[categoryIndex]);
    }

    private int nodeY(BookModel.Entry e, int categoryIndex, int cy) {
        return cy + (int) Math.round((e.y * SPACING + panY[categoryIndex]) * zoom[categoryIndex]);
    }

    private boolean inMapArea(double bx, double by) {
        return bx >= AX && bx < AX + AW && by >= AY && by < AY + AH;
    }

    void drawMap(GuiGraphics g, Font font, BookModel book, BookModel.Category cat, int categoryIndex,
                 Set<String> visited, String query, int ox, int oy, float k, int mx, int my, float partial) {
        if (cat == null) {
            hoverTip = null;
            return;
        }
        // header
        g.drawString(font, cat.title, 12, 10, GolemGui.BRASS_L, false);
        g.drawString(font, cat.blurb, 12 + font.width(cat.title) + 8, 10, 0xFF9A8E78, false);
        // search field frame
        g.fill(W - 130, 6, W - 16, 22, 0xFF1A0F08);
        g.fill(W - 129, 7, W - 17, 21, 0xFF0B1620);

        // map background, tiled 256x256 (1.21.1 scissor is in screen coordinates)
        g.enableScissor(Math.round(ox + AX * k), Math.round(oy + AY * k),
                Math.round(ox + (AX + AW) * k), Math.round(oy + (AY + AH) * k));
        int shiftX = (int) Math.floorMod((long) Math.round(panX[categoryIndex] * zoom[categoryIndex]), 256L);
        int shiftY = (int) Math.floorMod((long) Math.round(panY[categoryIndex] * zoom[categoryIndex]), 256L);
        for (int tx = AX - shiftX - 256; tx < AX + AW; tx += 256) {
            for (int ty = AY - shiftY - 256; ty < AY + AH; ty += 256) {
                g.blit(MAP_BG, tx, ty, 0, 0, 256, 256, 256, 256);
            }
        }
        // dark vignette at the edges
        for (int i = 0; i < 14; i++) {
            int a = (int) (150 * (1 - i / 14f)) << 24;
            g.fill(AX + i, AY, AX + i + 1, AY + AH, a);
            g.fill(AX + AW - i - 1, AY, AX + AW - i, AY + AH, a);
            g.fill(AX, AY + i, AX + AW, AY + i + 1, a);
            g.fill(AX, AY + AH - i - 1, AX + AW, AY + AH - i, a);
        }

        int cx = AX + AW / 2, cy = AY + AH / 2;
        drawLinks(g, book, cat, categoryIndex, visited, cx, cy);
        BookModel.Entry hovered = drawNodes(g, cat, categoryIndex, visited, cx, cy, mx, my, partial);
        g.disableScissor();

        String zoomText = zoomLabel(Math.round(zoom[categoryIndex] * 100));
        g.drawString(font, zoomText, AX + AW - 6 - font.width(zoomText), AY + AH - 12, 0xFF6FA5B5, true);

        if (hovered != null) {
            boolean isVisited = visited.contains(hovered.id);
            if (hovered != cachedHoverEntry || isVisited != cachedHoverVisited) {
                cachedHoverEntry = hovered;
                cachedHoverVisited = isVisited;
                cachedHoverTip = List.of(Component.literal(hovered.title).withStyle(ChatFormatting.GOLD),
                        isVisited ? TIP_READ : TIP_NEW);
            }
            hoverTip = cachedHoverTip;
        } else {
            hoverTip = null;
        }
        drawSearchResults(g, font, book, query, mx, my);
    }

    private void drawLinks(GuiGraphics g, BookModel book, BookModel.Category cat, int categoryIndex,
                           Set<String> visited, int cx, int cy) {
        for (BookModel.Entry e : cat.entries) {
            if (!e.visible()) {
                continue;
            }
            for (String pid : e.parents) {
                BookModel.Entry parent = book.byId.get(pid);
                if (parent == null || parent.category != cat || !parent.visible()) {
                    continue;
                }
                int c = visited.contains(parent.id) ? 0xFF38D9D6 : 0xFF1F5560;
                int x1 = nodeX(parent, categoryIndex, cx), y1 = nodeY(parent, categoryIndex, cy);
                int x2 = nodeX(e, categoryIndex, cx), y2 = nodeY(e, categoryIndex, cy);
                line(g, x1, y1, x2, y1, c);
                line(g, x2, y1, x2, y2, c);
                g.fill(x2 - 2, y1 - 2, x2 + 2, y1 + 2, c);
            }
        }
    }

    private BookModel.Entry drawNodes(GuiGraphics g, BookModel.Category cat, int categoryIndex,
                                      Set<String> visited, int cx, int cy, int mx, int my, float partial) {
        BookModel.Entry hovered = null;
        long time = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        float z = (float) zoom[categoryIndex];
        double half = NODE * z / 2.0;
        boolean inside = inMapArea(mx, my);
        for (BookModel.Entry e : cat.entries) {
            if (!e.visible()) {
                continue;
            }
            int ncx = nodeX(e, categoryIndex, cx), ncy = nodeY(e, categoryIndex, cy);
            boolean hover = inside && Math.abs(mx - ncx) <= half && Math.abs(my - ncy) <= half;
            if (hover) {
                hovered = e;
            }
            int sprite = e.gold ? 2 : hover ? 1 : 0;
            g.pose().pushPose();
            g.pose().translate(ncx, ncy, 0);
            g.pose().scale(z, z, 1f);
            g.blit(NODES, -NODE / 2, -NODE / 2, sprite * NODE, 0, NODE, NODE, NODE * 4, NODE);
            g.renderItem(e.icon, -8, -8);
            if (!visited.contains(e.id)) {
                float pulse = 0.35f + 0.35f * (float) Math.sin((time + partial) * 0.15 + e.x + e.y);
                RenderSystem.enableBlend();
                g.setColor(1f, 1f, 1f, pulse);
                g.blit(NODES, -NODE / 2, -NODE / 2, 3 * NODE, 0, NODE, NODE, NODE * 4, NODE);
                g.setColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            }
            g.pose().popPose();
        }
        return hovered;
    }

    private String zoomLabel(long pct) {
        Language lang = Language.getInstance();
        if (pct != cachedZoomPct || lang != cachedZoomLang) {
            cachedZoomPct = pct;
            cachedZoomLang = lang;
            cachedZoomText = Component.translatable("gui.golem_logix.book.zoom", pct).getString();
        }
        return cachedZoomText;
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        g.fill(minX - 1, minY - 1, maxX + 1, maxY + 1, c);
        g.fill(minX - 2, minY - 2, maxX + 2, maxY + 2, (c & 0x00FFFFFF) | 0x18000000);
    }

    List<BookModel.Entry> searchResults(BookModel book, String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (q.equals(lastSearchQuery)) {
            return cachedSearch;
        }
        lastSearchQuery = q;
        if (q.length() < 2) {
            cachedSearch = List.of();
            return cachedSearch;
        }
        List<BookModel.Entry> titleMatches = new ArrayList<>();
        List<BookModel.Entry> bodyMatches = new ArrayList<>();
        for (BookModel.Category c : book.categories) {
            for (BookModel.Entry e : c.entries) {
                if (!e.visible()) {
                    continue;
                }
                if (e.lowerTitle.contains(q)) {
                    titleMatches.add(e);
                } else if (e.searchText.contains(q)) {
                    bodyMatches.add(e);
                }
            }
        }
        List<BookModel.Entry> out = new ArrayList<>(Math.min(9, titleMatches.size() + bodyMatches.size()));
        for (BookModel.Entry e : titleMatches) {
            if (out.size() >= 9) {
                break;
            }
            out.add(e);
        }
        for (BookModel.Entry e : bodyMatches) {
            if (out.size() >= 9) {
                break;
            }
            out.add(e);
        }
        cachedSearch = out;
        return cachedSearch;
    }

    private void drawSearchResults(GuiGraphics g, Font font, BookModel book, String query, int mx, int my) {
        List<BookModel.Entry> res = searchResults(book, query);
        if (res.isEmpty() && query != null && query.trim().length() >= 2) {
            g.fill(W - 200, 24, W - 10, 38, 0xF0101820);
            g.drawString(font, SEARCH_NOTHING, W - 194, 28, 0xFF9A8E78, false);
            if (mx >= W - 200 && mx < W - 10 && my >= 24 && my < 38) {
                hoverTip = null;
            }
            return;
        }
        int y = 24;
        for (BookModel.Entry e : res) {
            boolean hover = mx >= W - 200 && mx < W - 10 && my >= y && my < y + 16;
            if (hover) {
                hoverTip = null;
            }
            g.fill(W - 200, y, W - 10, y + 16, hover ? 0xF0233444 : 0xF0101820);
            g.renderItem(e.icon, W - 198, y);
            g.drawString(font, font.plainSubstrByWidth(e.title, 120), W - 178, y + 4, hover ? GolemGui.CYAN : GolemGui.PARCHMENT, false);
            g.drawString(font, font.plainSubstrByWidth(e.category.title, 50), W - 56, y + 4, 0xFF6C7A86, false);
            y += 16;
        }
    }

    boolean clickSearchResults(BookModel book, String query, double bx, double by, Consumer<BookModel.Entry> onOpen) {
        List<BookModel.Entry> res = searchResults(book, query);
        int y = 24;
        for (BookModel.Entry e : res) {
            if (bx >= W - 200 && bx < W - 10 && by >= y && by < y + 16) {
                onOpen.accept(e);
                return true;
            }
            y += 16;
        }
        return false;
    }

    boolean startDrag(int categoryCount, double bx, double by) {
        if (categoryCount > 0 && inMapArea(bx, by)) {
            dragging = true;
            dragMoved = 0;
            return true;
        }
        return false;
    }

    boolean mouseDragged(int categoryIndex, int categoryCount, float k, double dx, double dy) {
        if (dragging && categoryCount > 0) {
            panX[categoryIndex] += dx / k / zoom[categoryIndex];
            panY[categoryIndex] += dy / k / zoom[categoryIndex];
            dragMoved += Math.abs(dx) + Math.abs(dy);
            clampPan(categoryIndex);
            return true;
        }
        return false;
    }

    private void clampPan(int categoryIndex) {
        double lim = 300;
        panX[categoryIndex] = Mth.clamp(panX[categoryIndex], -lim - 200, lim + 200);
        panY[categoryIndex] = Mth.clamp(panY[categoryIndex], -lim, lim);
    }

    boolean mouseReleased(BookModel.Category cat, int categoryIndex, double bx, double by, Consumer<BookModel.Entry> onOpen) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        if (dragMoved < 4 && cat != null && inMapArea(bx, by)) {
            int cx = AX + AW / 2, cy = AY + AH / 2;
            double half = NODE * zoom[categoryIndex] / 2.0;
            for (BookModel.Entry e : cat.entries) {
                if (e.visible() && Math.abs(bx - nodeX(e, categoryIndex, cx)) <= half
                        && Math.abs(by - nodeY(e, categoryIndex, cy)) <= half) {
                    onOpen.accept(e);
                    return true;
                }
            }
        }
        return false;
    }

    boolean mouseScrolled(int categoryIndex, int categoryCount, double bx, double by, double scrollY) {
        if (categoryCount > 0 && scrollY != 0 && inMapArea(bx, by)) {
            int cx = AX + AW / 2, cy = AY + AH / 2;
            double z1 = zoom[categoryIndex];
            double z2 = Mth.clamp(z1 * Math.pow(1.15, Math.signum(scrollY)), MIN_ZOOM, MAX_ZOOM);
            // the map point under the cursor stays in place
            double wx = (bx - cx) / z1 - panX[categoryIndex], wy = (by - cy) / z1 - panY[categoryIndex];
            zoom[categoryIndex] = z2;
            panX[categoryIndex] = (bx - cx) / z2 - wx;
            panY[categoryIndex] = (by - cy) / z2 - wy;
            clampPan(categoryIndex);
            return true;
        }
        return false;
    }
}
