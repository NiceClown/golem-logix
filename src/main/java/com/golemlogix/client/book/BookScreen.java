package com.golemlogix.client.book;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.client.GolemGui;
import com.golemlogix.client.ghost.GhostProjection;
import com.golemlogix.system.AtlasBlueprint;
import net.minecraft.world.item.Item;
import com.golemlogix.init.ModBlocks;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Книга «Оккультный Гримуар Логистики».
 * Режим карты: разделы слева, «карта исследований» с узлами-статьями и линиями зависимостей (двигается мышью).
 * Режим статьи: разворот из двух пергаментных страниц с текстом, рецептами, схемами и иллюстрациями.
 */
public class BookScreen extends Screen {
    // размеры в «книжных» координатах (масштабируются под экран)
    private static final int W = 380;
    private static final int H = 240;
    private static final int PAGE_W = 160;
    private static final int PAGE_H = 206;
    private static final int LEFT_PAGE_X = 16;
    private static final int RIGHT_PAGE_X = 204;
    private static final int PAGES_Y = 16;
    private static final int LINE_H = 10;
    private static final int NODE = 26;
    private static final int SPACING = 46;

    private static final ResourceLocation PARCHMENT = tex("parchment");
    private static final ResourceLocation MAP_BG = tex("map_bg");
    private static final ResourceLocation NODES = tex("nodes");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/gui/book/" + name + ".png");
    }

    private static final int INK = 0xFF3B2A18;
    private static final int INK_GOLD = 0xFF8A5A0B;
    private static final int INK_DIM = 0xFF7A6446;

    // ---------------------------------------------------------------- состояние

    /** Состояние на момент закрытия книги: раздел, статья (id или пусто), разворот. Живёт до выхода из игры, потом берётся из файла. */
    private static String[] lastState = null;

    private final BookModel book = BookModel.get();
    private int categoryIndex = 0;
    private BookModel.Entry openEntry = null;
    private List<View> views = new ArrayList<>();
    private int spread = 0;

    private final double[] panX;
    private final double[] panY;
    private final double[] zoom;
    private static final double MIN_ZOOM = 0.4;
    private static final double MAX_ZOOM = 2.2;
    private boolean dragging = false;
    private double dragStartX, dragStartY, dragMoved;

    private EditBox search;
    private final Set<String> visited = new HashSet<>();

    // трансформация «книжных» координат в экранные
    private float k = 1f;
    private int ox, oy;
    private ItemStack hoverStack = ItemStack.EMPTY;
    private Component hoverText = null;

    public BookScreen() {
        super(Component.translatable("item.golem_logix.guide_book"));
        this.panX = new double[Math.max(1, book.categories.size())];
        this.panY = new double[Math.max(1, book.categories.size())];
        this.zoom = new double[Math.max(1, book.categories.size())];
        java.util.Arrays.fill(this.zoom, 1.0);
        loadVisited();
        for (int i = 0; i < book.categories.size(); i++) {
            centerMap(i);
        }
        restoreState();
    }

    // ---------------------------------------------- последняя открытая страница

    private Path stateFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("golem_logix_book_state.txt");
    }

    /** Вспоминает раздел, статью и разворот, на которых игрок закрыл книгу (в памяти или из файла). */
    private void restoreState() {
        String[] saved = lastState;
        if (saved == null) {
            try {
                Path p = stateFile();
                if (Files.exists(p)) {
                    List<String> lines = Files.readAllLines(p);
                    if (lines.size() >= 3) {
                        saved = new String[]{lines.get(0), lines.get(1), lines.get(2)};
                    }
                }
            } catch (IOException ignored) {
            }
        }
        if (saved == null || book.categories.isEmpty()) {
            return;
        }
        try {
            categoryIndex = Mth.clamp(Integer.parseInt(saved[0].trim()), 0, book.categories.size() - 1);
            BookModel.Entry e = saved[1].isBlank() ? null : book.byId.get(saved[1].trim());
            if (e != null && e.visible()) {
                openEntry = e;
                categoryIndex = book.categories.indexOf(e.category);
                spread = Math.max(0, Integer.parseInt(saved[2].trim()));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    /** Книга, открытая сразу на статье (щелчок по сообщению о секретном знании); неизвестная статья — карта. */
    public static BookScreen openAt(String entryId) {
        BookModel.Entry e = BookModel.get().byId.get(entryId);
        if (e != null && e.visible()) {
            lastState = new String[]{Integer.toString(BookModel.get().categories.indexOf(e.category)), entryId, "0"};
        }
        return new BookScreen();
    }

    private void saveState() {
        lastState = new String[]{Integer.toString(categoryIndex), openEntry == null ? "" : openEntry.id, Integer.toString(spread)};
        try {
            Files.writeString(stateFile(), String.join("\n", lastState));
        } catch (IOException ignored) {
        }
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
    }

    // ------------------------------------------------------------ прогресс чтения

    private Path progressFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("golem_logix_book.txt");
    }

    private void loadVisited() {
        try {
            Path p = progressFile();
            if (Files.exists(p)) {
                visited.addAll(Files.readAllLines(p));
            }
        } catch (IOException ignored) {
        }
    }

    private void markVisited(String id) {
        if (visited.add(id)) {
            try {
                Files.writeString(progressFile(), String.join("\n", visited));
            } catch (IOException ignored) {
            }
        }
    }

    private BookModel.Category category() {
        BookModel.Category c = book.categories.get(Mth.clamp(categoryIndex, 0, book.categories.size() - 1));
        if (!c.visible() && !book.categories.isEmpty()) {
            // раздел скрыт (тайна ещё не открыта или снова закрыта) — показываем первый
            categoryIndex = 0;
            c = book.categories.get(0);
        }
        return c;
    }

    private void centerMap(int cat) {
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

    // ----------------------------------------------------------------- init

    @Override
    protected void init() {
        k = Math.min(1f, Math.min((width - 16f) / (W + 56f), (height - 8f) / H));
        ox = Math.round((width - W * k) / 2f + 14f * k);
        oy = Math.round((height - H * k) / 2f);
        search = new EditBox(font, 0, 0, 100, 12, Component.translatable("gui.golem_logix.search"));
        search.setBordered(false);
        search.setMaxLength(40);
        search.setTextColor(0xFFE8D8B0);
        search.setHint(Component.translatable("gui.golem_logix.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> {
        });
        addWidget(search);
        if (openEntry != null) {
            views = buildViews(openEntry);
            spread = Mth.clamp(spread, 0, spreads() - 1);
        }
    }

    // ----------------------------------------------------- построение страниц

    private enum Kind {TEXT, CRAFTING, IMAGE, RITUAL, BLUEPRINT, TABLE, SPOTLIGHT, CHECKLIST, TASKS}

    private record Line(FormattedCharSequence seq, int x, int y, int color) {
    }

    private record View(Kind kind, JsonObject data, String title, List<Line> lines, int hh) {
    }

    /**
     * Текст с кодами «§» (в книге пишут «&») → компонент из кусков со своими стилями. Перенос строк такого компонента не
     * теряет и не путает начертание (у строки с кодами внутри жирный текст «перетекал» за &r, а ширина считалась не та).
     */
    static net.minecraft.network.chat.MutableComponent styled(String s) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY;
        StringBuilder seg = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                if (seg.length() > 0) {
                    out.append(Component.literal(seg.toString()).withStyle(style));
                    seg.setLength(0);
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(++i)));
                if (f != null) {
                    style = f == ChatFormatting.RESET ? net.minecraft.network.chat.Style.EMPTY : style.applyLegacyFormat(f);
                }
                continue;
            }
            seg.append(c);
        }
        if (seg.length() > 0) {
            out.append(Component.literal(seg.toString()).withStyle(style));
        }
        return out;
    }

    /** Разбивает абзацы на строки страницы; возвращает порции, помещающиеся по высоте. */
    private List<List<Line>> flow(List<String> paras, int startY, int limitY, int firstLimitY) {
        List<List<Line>> chunks = new ArrayList<>();
        List<Line> cur = new ArrayList<>();
        int y = startY;
        int limit = firstLimitY;
        for (String raw : paras) {
            String para = raw.replace('&', '§');
            int indent = 0;
            int color = INK;
            String lead = null;
            if (para.startsWith("# ")) {
                para = "§l" + para.substring(2);
                color = INK_GOLD;
                y += 4;
            } else if (para.startsWith("• ") || para.startsWith("- ")) {
                para = para.substring(2);
                indent = 8;
                lead = "•";
            }
            List<FormattedCharSequence> wrapped = font.split(styled(para), PAGE_W - 12 - indent);
            for (int i = 0; i < wrapped.size(); i++) {
                if (y + LINE_H > limit) {
                    chunks.add(cur);
                    cur = new ArrayList<>();
                    y = 8;
                    limit = limitY;
                }
                if (lead != null && i == 0) {
                    cur.add(new Line(Component.literal(lead).getVisualOrderText(), 6, y, INK_GOLD));
                }
                cur.add(new Line(wrapped.get(i), 6 + indent, y, color));
                y += LINE_H;
            }
            y += 3;
        }
        chunks.add(cur);
        return chunks;
    }

    private List<View> buildViews(BookModel.Entry entry) {
        List<View> out = new ArrayList<>();
        for (JsonObject p : entry.pages) {
            if (!BookModel.pageVisible(p)) {
                continue;
            }
            Kind kind = switch (p.get("type").getAsString()) {
                case "crafting" -> Kind.CRAFTING;
                case "image" -> Kind.IMAGE;
                case "ritual" -> Kind.RITUAL;
                case "blueprint" -> Kind.BLUEPRINT;
                case "table" -> Kind.TABLE;
                case "spotlight" -> Kind.SPOTLIGHT;
                case "checklist" -> Kind.CHECKLIST;
                case "tasks" -> Kind.TASKS;
                default -> Kind.TEXT;
            };
            String title = p.has("title") ? p.get("title").getAsString() : null;
            if (title == null && kind == Kind.CRAFTING) {
                ItemStack res = resolveGrid(p).result();
                title = res.isEmpty() ? "?" : res.getHoverName().getString();
            }
            if (title == null && kind == Kind.SPOTLIGHT) {
                title = parseStack(p.get("item").getAsString()).getHoverName().getString();
            }
            List<String> text = BookModel.strings(p, "text");
            int hh = title == null ? 0 : headingHeight(title);
            int condLines = 0;
            if (kind == Kind.RITUAL && p.has("condition")) {
                condLines = font.split(styled("\u00a7o" + p.get("condition").getAsString().replace('&', '\u00a7')), PAGE_W - 16).size();
            }
            // с какой высоты идёт текст после графики
            int start = switch (kind) {
                case TEXT -> hh > 0 ? hh + 2 : 8;
                case CRAFTING -> hh + 6 + 54 + 12;
                case IMAGE -> (hh > 0 ? hh + 4 : 8) + (p.has("size") ? p.get("size").getAsInt() : 144) + 8;
                case RITUAL -> hh + 8 + 20 + (condLines > 0 ? condLines * LINE_H + 6 : 0) + 6;
                case BLUEPRINT -> hh + 14 + 72 + 10;
                case TABLE -> hh + 4 + tableHeight(p) + 8;
                case SPOTLIGHT -> hh + 6 + 48 + 10;
                case CHECKLIST -> hh + 4 + checklistRows(p) * 20 + 18 + 24 + 8;
                case TASKS -> hh + 4 + taskHeight(p) + 6;
            };
            List<List<Line>> chunks = flow(text, start, PAGE_H - 14, PAGE_H - 14);
            for (int i = 0; i < chunks.size(); i++) {
                if (i == 0) {
                    out.add(new View(kind, p, title, chunks.get(0), hh));
                } else {
                    out.add(new View(Kind.TEXT, p, null, chunks.get(i), 0));
                }
            }
        }        if (out.size() % 2 == 1) {
            out.add(new View(Kind.TEXT, new JsonObject(), null, List.of(), 0));
        }
        return out;
    }

    private static int rows(JsonObject p) {
        return p.has("rows") ? p.getAsJsonArray("rows").size() : 0;
    }

    /**
     * Для стенда разработчика: раскладывает все статьи книги текущего языка (все страницы, как будто тайна раскрыта) и
     * сообщает о неудачной вёрстке: «висячая» страница с одной-двумя перенесёнными строками или графика, не оставившая места
     * тексту своей страницы.
     */
    public static List<String> auditLayout() {
        BookScreen s = new BookScreen();
        s.font = Minecraft.getInstance().font;
        List<String> out = new ArrayList<>();
        BookModel.showAll = true;
        try {
            for (BookModel.Category c : BookModel.get().categories) {
                for (BookModel.Entry e : c.entries) {
                    List<View> views = s.buildViews(e);
                    for (int i = 0; i < views.size(); i++) {
                        View v = views.get(i);
                        boolean continuation = i > 0 && v.data() == views.get(i - 1).data() && !v.data().entrySet().isEmpty();
                        String which = auditName(v.data());
                        if (!continuation && v.kind() == Kind.TABLE) {
                            out.addAll(s.auditTable(e.id, v.data(), v.hh()));
                        }
                        if (continuation && v.lines().size() <= 2) {
                            out.add(e.id + " page " + (i + 1) + " " + which + ": only " + v.lines().size() + " line(s) carried over");
                        }
                        // список материалов решётки занимает страницу целиком, его текст нарочно идёт на соседнюю
                        if (!continuation && v.kind() != Kind.CHECKLIST && v.lines().isEmpty() && !BookModel.strings(v.data(), "text").isEmpty()) {
                            out.add(e.id + " page " + (i + 1) + " " + which + ": no room for text after the picture");
                        }
                    }
                }
            }
        } finally {
            BookModel.showAll = false;
        }
        return out;
    }

    /** Страница для отчёта: тип, заголовок или рецепт и начало текста. */
    private static String auditName(JsonObject p) {
        String type = p.has("type") ? p.get("type").getAsString() : "?";
        String name = p.has("title") ? p.get("title").getAsString() : p.has("recipe") ? p.get("recipe").getAsString() : "";
        List<String> text = BookModel.strings(p, "text");
        String start = text.isEmpty() ? "" : text.get(0);
        return "[" + type + (name.isEmpty() ? "" : " \"" + name + "\"") + " / " + (start.length() > 40 ? start.substring(0, 40) + "…" : start) + "]";
    }

    // ----------------------------------------------------------------- открытие

    private void open(BookModel.Entry entry) {
        openEntry = entry;
        views = buildViews(entry);
        spread = 0;
        markVisited(entry.id);
        search.setValue("");
    }

    private void closeEntry() {
        openEntry = null;
        views = new ArrayList<>();
    }

    private int spreads() {
        return Math.max(1, views.size() / 2);
    }

    // ------------------------------------------------------------------ рендер

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        hoverStack = ItemStack.EMPTY;
        hoverText = null;
        double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(k, k, 1f);

        // вкладки-закладки раньше переплёта: обложка ложится поверх их корешков, а не наоборот
        drawTabs(g, (int) bx, (int) by);
        drawCover(g);
        if (openEntry == null) {
            drawMap(g, (int) bx, (int) by, partial);
        } else {
            drawArticle(g, (int) bx, (int) by);
        }
        drawCorners(g);
        g.pose().popPose();

        if (openEntry == null) {
            search.render(g, mouseX, mouseY, partial);
        }
        if (!hoverStack.isEmpty()) {
            g.renderTooltip(font, hoverStack, mouseX, mouseY);
        } else if (hoverText != null) {
            g.renderTooltip(font, hoverText, mouseX, mouseY);
        } else if (openEntry == null && hoverTip != null) {
            g.renderComponentTooltip(font, hoverTip, mouseX, mouseY);
        }
    }

    private void drawCover(GuiGraphics g) {
        // кожаный переплёт
        g.fill(-6, -6, W + 6, H + 6, 0xFF1A0F08);
        g.fill(-4, -4, W + 4, H + 4, 0xFF4A2E1A);
        g.fill(-3, -3, W + 3, H + 3, 0xFF5E3B22);
        g.fill(0, 0, W, H, 0xFF3A2314);
    }

    /** Латунные уголки переплёта — поверх всего содержимого, чтобы интерфейс не залезал на обложку. */
    private void drawCorners(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        for (int[] c : new int[][]{{-6, -6}, {W - 12, -6}, {-6, H - 12}, {W - 12, H - 12}}) {
            g.fill(c[0], c[1], c[0] + 18, c[1] + 18, GolemGui.BRASS_DARK);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 17, c[1] + 17, GolemGui.BRASS);
            g.fill(c[0] + 5, c[1] + 5, c[0] + 13, c[1] + 13, GolemGui.BRASS_DARK);
            g.fill(c[0] + 7, c[1] + 7, c[0] + 11, c[1] + 11, GolemGui.CYAN);
        }
        g.pose().popPose();
    }

    /** Разделы, у которых есть вкладка: только те, где видна хоть одна статья. */
    private List<Integer> tabs() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < book.categories.size(); i++) {
            if (book.categories.get(i).visible()) {
                out.add(i);
            }
        }
        return out;
    }

    private static final int TAB_STEP = 25;

    /** Есть ли в разделе видимые, но ещё не прочитанные статьи. */
    private boolean hasUnread(BookModel.Category c) {
        for (BookModel.Entry e : c.entries) {
            if (e.visible() && !visited.contains(e.id)) {
                return true;
            }
        }
        return false;
    }

    /** Шаг вкладок: все закладки умещаются между латунными уголками переплёта. */
    private int tabStep(int count) {
        return Math.max(18, Math.min(TAB_STEP, (H - 2 * TAB_TOP) / Math.max(1, count)));
    }

    private static final int TAB_TOP = 15;

    /** Левый край вкладки: закладка торчит из-под переплёта, выбранная выдвинута дальше. */
    private int tabX(int i) {
        return i == categoryIndex ? -34 : -30;
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        List<Integer> tabs = tabs();
        long time = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        int step = tabStep(tabs.size()), th = step - 1;
        for (int j = 0; j < tabs.size(); j++) {
            int i = tabs.get(j);
            BookModel.Category c = book.categories.get(i);
            int x = tabX(i);
            int y = TAB_TOP + j * step;
            boolean hover = mx >= x && mx < -6 && my >= y && my < y + th;
            // закладка уходит под переплёт (обложка рисуется поверх её правого края)
            g.fill(x, y, 2, y + th, 0xFF1A0F08);
            g.fill(x + 1, y + 1, 2, y + th - 1, hover ? GolemGui.BRASS_L : (i == categoryIndex ? GolemGui.BRASS : GolemGui.BRASS_DARK));
            g.fill(x + 3, y + 3, 2, y + th - 3, 0xFF16222B);
            g.fill(x + 3, y + 3, x + 5, y + th - 3, c.color);
            g.renderItem(c.icon, x + 7, y + (th - 16) / 2);
            if (hasUnread(c)) {
                // непрочитанное в разделе: пульсирующий огонёк в углу вкладки
                int a = (int) (140 + 115 * Math.sin(time * 0.2 + j));
                g.fill(x + 21, y + 3, x + 25, y + 7, (Math.max(0, Math.min(255, a)) << 24) | 0x38D9D6);
            }
            if (hover) {
                hoverText = Component.literal(c.title);
            }
        }
    }

    // ------------------------------------------------------------------- карта

    private int nodeX(BookModel.Entry e, int cx) {
        return cx + (int) Math.round((e.x * SPACING + panX[categoryIndex]) * zoom[categoryIndex]);
    }

    private int nodeY(BookModel.Entry e, int cy) {
        return cy + (int) Math.round((e.y * SPACING + panY[categoryIndex]) * zoom[categoryIndex]);
    }

    private void drawMap(GuiGraphics g, int mx, int my, float partial) {
        BookModel.Category cat = category();
        int ax = 8, ay = 30, aw = W - 16, ah = H - 40;
        // шапка
        g.drawString(font, "§l" + cat.title, 12, 10, GolemGui.BRASS_L, false);
        g.drawString(font, cat.blurb, 12 + font.width("§l" + cat.title) + 8, 10, 0xFF9A8E78, false);
        // поле поиска
        g.fill(W - 130, 6, W - 16, 22, 0xFF1A0F08);
        g.fill(W - 129, 7, W - 17, 21, 0xFF0B1620);
        search.setX(Math.round(ox + (W - 126) * k));
        search.setY(Math.round(oy + 9 * k));
        search.setWidth(Math.round(106 * k));

        // фон карты, плиткой 256x256
        g.enableScissor(Math.round(ox + ax * k), Math.round(oy + ay * k), Math.round(ox + (ax + aw) * k), Math.round(oy + (ay + ah) * k));
        int shiftX = (int) Math.floorMod((long) Math.round(panX[categoryIndex] * zoom[categoryIndex]), 256L);
        int shiftY = (int) Math.floorMod((long) Math.round(panY[categoryIndex] * zoom[categoryIndex]), 256L);
        for (int tx = ax - shiftX - 256; tx < ax + aw; tx += 256) {
            for (int ty = ay - shiftY - 256; ty < ay + ah; ty += 256) {
                g.blit(MAP_BG, tx, ty, 0, 0, 256, 256, 256, 256);
            }
        }
        // тёмная виньетка по краям
        for (int i = 0; i < 14; i++) {
            int a = (int) (150 * (1 - i / 14f)) << 24;
            g.fill(ax + i, ay, ax + i + 1, ay + ah, a);
            g.fill(ax + aw - i - 1, ay, ax + aw - i, ay + ah, a);
            g.fill(ax, ay + i, ax + aw, ay + i + 1, a);
            g.fill(ax, ay + ah - i - 1, ax + aw, ay + ah - i, a);
        }

        int cx = ax + aw / 2, cy = ay + ah / 2;
        // линии зависимостей
        for (BookModel.Entry e : cat.entries) {
            if (!e.visible()) {
                continue;
            }
            for (String pid : e.parents) {
                BookModel.Entry parent = book.byId.get(pid);
                if (parent == null || parent.category != cat || !parent.visible()) {
                    continue;
                }
                boolean lit = visited.contains(parent.id);
                int c = lit ? 0xFF38D9D6 : 0xFF1F5560;
                int x1 = nodeX(parent, cx), y1 = nodeY(parent, cy), x2 = nodeX(e, cx), y2 = nodeY(e, cy);
                line(g, x1, y1, x2, y1, c);
                line(g, x2, y1, x2, y2, c);
                g.fill(x2 - 2, y1 - 2, x2 + 2, y1 + 2, c);
            }
        }
        // узлы
        BookModel.Entry hovered = null;
        long time = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        float z = (float) zoom[categoryIndex];
        double half = NODE * z / 2.0;
        for (BookModel.Entry e : cat.entries) {
            if (!e.visible()) {
                continue;
            }
            int ncx = nodeX(e, cx), ncy = nodeY(e, cy);
            boolean inside = mx >= ax && mx < ax + aw && my >= ay && my < ay + ah;
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
            }
            g.pose().popPose();
        }
        g.disableScissor();
        // масштаб карты
        String zoomText = Component.translatable("gui.golem_logix.book.zoom", Math.round(zoom[categoryIndex] * 100)).getString();
        g.drawString(font, zoomText, ax + aw - 6 - font.width(zoomText), ay + ah - 12, 0xFF6FA5B5, true);
        if (hovered != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hovered.title).withStyle(ChatFormatting.GOLD));
            tip.add(Component.translatable(visited.contains(hovered.id) ? "gui.golem_logix.book.read" : "gui.golem_logix.book.new")
                    .withStyle(visited.contains(hovered.id) ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA));
            hoverTip = tip;
        } else {
            hoverTip = null;
        }
        drawSearchResults(g, mx, my);
    }

    private List<Component> hoverTip = null;

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int c) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        g.fill(minX - 1, minY - 1, maxX + 1, maxY + 1, c);
        g.fill(minX - 2, minY - 2, maxX + 2, maxY + 2, (c & 0x00FFFFFF) | 0x18000000);
    }

    private List<BookModel.Entry> searchResults() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<BookModel.Entry> out = new ArrayList<>();
        if (q.length() < 2) {
            return out;
        }
        for (BookModel.Category c : book.categories) {
            for (BookModel.Entry e : c.entries) {
                if (!e.visible()) {
                    continue;
                }
                if (e.title.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(0, e);
                } else if (e.searchText.contains(q)) {
                    out.add(e);
                }
            }
        }
        return out.size() > 9 ? new ArrayList<>(out.subList(0, 9)) : out;
    }

    private void drawSearchResults(GuiGraphics g, int mx, int my) {
        List<BookModel.Entry> res = searchResults();
        if (res.isEmpty() && search.getValue().trim().length() >= 2) {
            g.fill(W - 200, 24, W - 10, 38, 0xF0101820);
            g.drawString(font, Component.translatable("gui.golem_logix.book.nothing"), W - 194, 28, 0xFF9A8E78, false);
            return;
        }
        int y = 24;
        for (BookModel.Entry e : res) {
            boolean hover = mx >= W - 200 && mx < W - 10 && my >= y && my < y + 16;
            g.fill(W - 200, y, W - 10, y + 16, hover ? 0xF0233444 : 0xF0101820);
            g.renderItem(e.icon, W - 198, y);
            g.drawString(font, font.plainSubstrByWidth(e.title, 120), W - 178, y + 4, hover ? GolemGui.CYAN : GolemGui.PARCHMENT, false);
            g.drawString(font, font.plainSubstrByWidth(e.category.title, 50), W - 56, y + 4, 0xFF6C7A86, false);
            y += 16;
        }
    }

    // ----------------------------------------------------------------- статья

    private void drawArticle(GuiGraphics g, int mx, int my) {
        clickables.clear();
        // спинка и страницы
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
            drawView(g, views.get(li), LEFT_PAGE_X, PAGES_Y, mx, my, li + 1);
        }
        if (li + 1 < views.size()) {
            drawView(g, views.get(li + 1), RIGHT_PAGE_X, PAGES_Y, mx, my, li + 2);
        }

        // навигация
        navButton(g, mx, my, LEFT_PAGE_X, "◀", spread > 0, Component.translatable("gui.golem_logix.book.prev"));
        navButton(g, mx, my, RIGHT_PAGE_X + PAGE_W - 40, "▶", spread < spreads() - 1, Component.translatable("gui.golem_logix.book.next"));
        navButton(g, mx, my, W / 2 - 20, "☰", true, Component.translatable("gui.golem_logix.book.map"));
    }

    private int navY() {
        return PAGES_Y + PAGE_H + 6;
    }

    private void navButton(GuiGraphics g, int mx, int my, int x, String label, boolean enabled, Component tip) {
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

    private void drawView(GuiGraphics g, View v, int x, int y, int mx, int my, int pageNo) {
        switch (v.kind()) {
            case TEXT -> {
                if (v.title() != null) {
                    heading(g, v.title(), x, y);
                }
            }
            case CRAFTING -> drawCrafting(g, v, x, y, mx, my);
            case IMAGE -> drawImage(g, v, x, y);
            case RITUAL -> drawRitual(g, v, x, y, mx, my);
            case BLUEPRINT -> drawBlueprint(g, v, x, y, mx, my);
            case TABLE -> drawTable(g, v, x, y);
            case SPOTLIGHT -> drawSpotlight(g, v, x, y, mx, my);
            case CHECKLIST -> drawChecklist(g, v, x, y, mx, my);
            case TASKS -> drawTasks(g, v, x, y, mx, my);
        }
        for (Line l : v.lines()) {
            g.drawString(font, l.seq(), x + l.x(), y + l.y(), l.color(), false);
        }
        String no = Integer.toString(pageNo);
        g.drawString(font, no, x + (pageNo % 2 == 1 ? 8 : PAGE_W - 8 - font.width(no)), y + PAGE_H - 11, INK_DIM, false);
    }

    private int headingHeight(String title) {
        return 7 + font.split(styled("\u00a7l" + title), PAGE_W - 12).size() * LINE_H + 4;
    }

    private void heading(GuiGraphics g, String title, int x, int y) {
        String t = "§l" + title;
        List<FormattedCharSequence> lines = font.split(styled(t), PAGE_W - 12);
        int yy = y + 7;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x + 6, yy, INK_GOLD, false);
            yy += LINE_H;
        }
        g.fill(x + 6, yy, x + PAGE_W - 6, yy + 1, 0x80804A0A);
    }

    // ---------------------------------------------------------------- рецепты

    private record Grid(ItemStack[][] cells, ItemStack result) {
    }

    private Grid resolveGrid(JsonObject d) {
        ItemStack[][] cells = new ItemStack[9][];
        for (int i = 0; i < 9; i++) {
            cells[i] = new ItemStack[0];
        }
        ItemStack result = ItemStack.EMPTY;
        if (d.has("recipe")) {
            var level = Minecraft.getInstance().level;
            ResourceLocation id = ResourceLocation.parse(d.get("recipe").getAsString());
            if (level != null) {
                var holder = level.getRecipeManager().byKey(id);
                if (holder.isPresent()) {
                    RecipeHolder<?> rh = holder.get();
                    result = rh.value().getResultItem(level.registryAccess());
                    if (rh.value() instanceof ShapedRecipe shaped) {
                        for (int r = 0; r < shaped.getHeight(); r++) {
                            for (int c = 0; c < shaped.getWidth(); c++) {
                                Ingredient ing = shaped.getIngredients().get(r * shaped.getWidth() + c);
                                cells[r * 3 + c] = ing.getItems();
                            }
                        }
                    } else if (rh.value() instanceof ShapelessRecipe shapeless) {
                        for (int i = 0; i < Math.min(9, shapeless.getIngredients().size()); i++) {
                            cells[i] = shapeless.getIngredients().get(i).getItems();
                        }
                    }
                }
            }
        } else if (d.has("grid")) {
            var arr = d.getAsJsonArray("grid");
            for (int i = 0; i < Math.min(9, arr.size()); i++) {
                JsonElement e = arr.get(i);
                if (!e.isJsonNull() && !e.getAsString().isEmpty()) {
                    cells[i] = new ItemStack[]{BookModel.stack(e.getAsString())};
                }
            }
            result = BookModel.stack(d.get("result").getAsString());
            if (d.has("count")) {
                result.setCount(d.get("count").getAsInt());
            }
        }
        return new Grid(cells, result);
    }

    private void drawCrafting(GuiGraphics g, View v, int x, int y, int mx, int my) {
        Grid grid = resolveGrid(v.data());
        heading(g, v.title(), x, y);
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
        // стрелка
        int ax = x0 + 58, ay = y0 + 25;
        g.fill(ax, ay, ax + 14, ay + 3, INK_GOLD);
        for (int i = 0; i < 4; i++) {
            g.fill(ax + 14 + i, ay - 3 + i, ax + 15 + i, ay + 6 - i, INK_GOLD);
        }
        // результат
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

    // ----------------------------------------------------------- ритуал, станция

    private static ItemStack parseStack(String spec) {
        int star = spec.indexOf('*');
        ItemStack s = BookModel.stack(star < 0 ? spec : spec.substring(0, star));
        if (star >= 0) {
            s.setCount(Integer.parseInt(spec.substring(star + 1)));
        }
        return s;
    }

    private void slotWithItem(GuiGraphics g, ItemStack s, int sx, int sy, int mx, int my) {
        GolemGui.slot(g, sx - 1, sy - 1);
        g.renderItem(s, sx, sy);
        g.renderItemDecorations(font, s, sx, sy);
        if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
            hoverStack = s;
        }
    }

    private void drawRitual(GuiGraphics g, View v, int x, int y, int mx, int my) {
        JsonObject d = v.data();
        if (v.title() != null) {
            heading(g, v.title(), x, y);
        }
        List<String> inputs = BookModel.strings(d, "inputs");
        List<String> outputs = BookModel.strings(d, "outputs");
        ItemStack station = d.has("station") ? parseStack(d.get("station").getAsString()) : ItemStack.EMPTY;
        int total = inputs.size() * 18 + (station.isEmpty() ? 0 : 26) + 24 + outputs.size() * 18;
        int cx = x + (PAGE_W - total) / 2, cy = y + v.hh() + 8;
        for (String in : inputs) {
            slotWithItem(g, parseStack(in), cx + 1, cy, mx, my);
            cx += 18;
        }
        if (!station.isEmpty()) {
            g.drawString(font, "+", cx + 3, cy + 4, INK_GOLD, false);
            cx += 12;
            slotWithItem(g, station, cx + 1, cy, mx, my);
            cx += 18;
        }
        g.fill(cx + 4, cy + 6, cx + 16, cy + 9, INK_GOLD);
        for (int i = 0; i < 4; i++) {
            g.fill(cx + 16 + i, cy + 3 + i, cx + 17 + i, cy + 12 - i, INK_GOLD);
        }
        cx += 24;
        for (String out : outputs) {
            slotWithItem(g, parseStack(out), cx + 1, cy, mx, my);
            cx += 18;
        }
        if (d.has("condition")) {
            List<FormattedCharSequence> cond = font.split(styled("§o" + d.get("condition").getAsString().replace('&', '§')), PAGE_W - 16);
            int yy = y + v.hh() + 8 + 22;
            for (FormattedCharSequence l : cond) {
                g.drawString(font, l, x + 8, yy, INK_DIM, false);
                yy += LINE_H;
            }
        }
    }

    private void drawSpotlight(GuiGraphics g, View v, int x, int y, int mx, int my) {
        JsonObject d = v.data();
        ItemStack s = parseStack(d.get("item").getAsString());
        heading(g, v.title(), x, y);
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

    // ---------------------------------------------------------- схема решётки

    private void drawBlueprint(GuiGraphics g, View v, int x, int y, int mx, int my) {
        JsonObject d = v.data();
        int layer = d.get("layer").getAsInt();
        if (v.title() != null) {
            heading(g, v.title(), x, y);
        }
        long t = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() / 25 : 0;
        ItemStack[] expanders = {new ItemStack(ModBlocks.ATLAS_CASING.get()), new ItemStack(ModBlocks.CHANNEL_EXPANDER.get()),
                new ItemStack(ModBlocks.QUANTUM_CHANNEL_EXPANDER.get())};
        int cell = 24;
        int gx = x + (PAGE_W - cell * 3) / 2, gy = y + v.hh() + 14;
        g.drawCenteredString(font, "▲ N", x + PAGE_W / 2, gy - 11, INK_DIM);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                boolean corner = r != 1 && c != 1, center = r == 1 && c == 1;
                ItemStack s;
                switch (layer) {
                    case 0 -> s = new ItemStack(corner || center ? ModBlocks.CABLE_BUSHING.get() : ModBlocks.ATLAS_CONDUIT.get());
                    case 1, 2 -> s = corner ? new ItemStack(ModBlocks.ATLAS_ATLANT.get())
                            : center ? new ItemStack(ModBlocks.ATLAS_CONDUIT.get()) : expanders[(int) ((t + r * 3 + c) % 3)];
                    default -> s = new ItemStack(center ? ModBlocks.ATLAS_CORE.get() : ModBlocks.ATLAS_CASING.get());
                }
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

    // ---------------------------------------------------------------- таблица

    private void drawTable(GuiGraphics g, View v, int x, int y) {
        JsonObject d = v.data();
        int yy = y + 8;
        if (v.title() != null) {
            heading(g, v.title(), x, y);
            yy = y + v.hh() + 4;
        }
        var header = BookModel.strings(d, "header");
        var rows = d.has("rows") ? d.getAsJsonArray("rows") : new com.google.gson.JsonArray();
        int cols = Math.max(1, header.size());
        int[] widths = columnWidths(d, cols);
        // заголовки и ячейки переносятся внутри своего столбца, строка таблицы растёт по самой длинной ячейке
        int xx = x + 6;
        int hl = 1;
        for (int c = 0; c < header.size(); c++) {
            List<FormattedCharSequence> ls = cellLines(header.get(c), widths[c] - 3, true);
            for (int i = 0; i < ls.size(); i++) {
                g.drawString(font, ls.get(i), xx, yy + i * LINE_H, INK_GOLD, false);
            }
            hl = Math.max(hl, ls.size());
            xx += widths[c];
        }
        yy += hl * LINE_H + 1;
        g.fill(x + 6, yy - 1, x + PAGE_W - 6, yy, 0x80804A0A);
        int idx = 0;
        for (JsonElement re : rows) {
            var row = re.getAsJsonArray();
            List<List<FormattedCharSequence>> cells = new ArrayList<>();
            int rl = 1;
            for (int c = 0; c < cols && c < row.size(); c++) {
                cells.add(cellLines(row.get(c).getAsString(), widths[c] - 3, false));
                rl = Math.max(rl, cells.get(c).size());
            }
            if (idx % 2 == 0) {
                g.fill(x + 5, yy - 1, x + PAGE_W - 5, yy + rl * LINE_H, 0x18804A0A);
            }
            xx = x + 6;
            for (int c = 0; c < cells.size(); c++) {
                for (int i = 0; i < cells.get(c).size(); i++) {
                    g.drawString(font, cells.get(c).get(i), xx, yy + i * LINE_H, INK, false);
                }
                xx += widths[c];
            }
            yy += rl * LINE_H + 2;
            idx++;
        }
    }

    /** Текст ячейки таблицы, перенесённый по ширине столбца (заголовок — жирным). */
    private List<FormattedCharSequence> cellLines(String text, int width, boolean bold) {
        net.minecraft.network.chat.MutableComponent c = styled(text.replace('&', '§'));
        if (bold) {
            c = Component.empty().withStyle(ChatFormatting.BOLD).append(c);
        }
        return font.split(c, width);
    }

    /** Высота таблицы без заголовка страницы: шапка и строки с учётом переносов. */
    private int tableHeight(JsonObject d) {
        var header = BookModel.strings(d, "header");
        int cols = Math.max(1, header.size());
        int[] widths = columnWidths(d, cols);
        int hl = 1;
        for (int c = 0; c < header.size(); c++) {
            hl = Math.max(hl, cellLines(header.get(c), widths[c] - 3, true).size());
        }
        int h = hl * LINE_H + 2;
        if (d.has("rows")) {
            for (JsonElement re : d.getAsJsonArray("rows")) {
                var row = re.getAsJsonArray();
                int rl = 1;
                for (int c = 0; c < cols && c < row.size(); c++) {
                    rl = Math.max(rl, cellLines(row.get(c).getAsString(), widths[c] - 3, false).size());
                }
                h += rl * LINE_H + 2;
            }
        }
        return h;
    }

    private static int[] columnWidths(JsonObject d, int cols) {
        int[] widths = new int[cols];
        for (int i = 0; i < cols; i++) {
            widths[i] = d.has("widths") ? d.getAsJsonArray("widths").get(i).getAsInt() : (PAGE_W - 12) / cols;
        }
        return widths;
    }

    /**
     * Для проверки вёрстки: таблица целиком помещается на странице, столбцы не шире страницы, а каждое слово — в свой столбец
     * (иначе перенос разрежет его посередине).
     */
    private List<String> auditTable(String id, JsonObject d, int hh) {
        List<String> out = new ArrayList<>();
        var header = BookModel.strings(d, "header");
        int cols = Math.max(1, header.size());
        int[] widths = columnWidths(d, cols);
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        if (total > PAGE_W - 12) {
            out.add(id + " table: columns " + total + " px are wider than the page " + (PAGE_W - 12));
        }
        int bottom = hh + 4 + tableHeight(d);
        if (bottom > PAGE_H - 14) {
            out.add(id + " table: " + bottom + " px tall, the page has " + (PAGE_H - 14));
        }
        List<String[]> cells = new ArrayList<>();
        for (int c = 0; c < header.size(); c++) {
            cells.add(new String[]{header.get(c), "B", Integer.toString(c)});
        }
        if (d.has("rows")) {
            for (JsonElement re : d.getAsJsonArray("rows")) {
                var row = re.getAsJsonArray();
                for (int c = 0; c < cols && c < row.size(); c++) {
                    cells.add(new String[]{row.get(c).getAsString(), "", Integer.toString(c)});
                }
            }
        }
        for (String[] cell : cells) {
            int w = widths[Integer.parseInt(cell[2])] - 3;
            for (String word : cell[0].replace('&', '§').split("\\s+")) {
                net.minecraft.network.chat.MutableComponent wc = styled(word);
                int ww = cell[1].isEmpty() ? font.width(wc) : font.width(Component.empty().withStyle(ChatFormatting.BOLD).append(wc));
                if (ww > w) {
                    out.add(id + " table word \"" + word + "\" (" + ww + " px) is wider than its column (" + w + " px)");
                }
            }
        }
        return out;
    }

    // ----------------------------------------------- список материалов + призраки

    private record Clickable(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Clickable> clickables = new ArrayList<>();

    private static AtlasBlueprint.Variant variantOf(JsonObject p) {
        String name = p.has("variant") ? p.get("variant").getAsString() : "full";
        return switch (name) {
            case "tower" -> AtlasBlueprint.Variant.TOWER;
            case "saturator" -> AtlasBlueprint.Variant.SATURATOR;
            case "mainframe" -> AtlasBlueprint.Variant.MAINFRAME;
            case "catalog" -> AtlasBlueprint.Variant.CATALOG;
            default -> AtlasBlueprint.Variant.FULL;
        };
    }

    private int checklistRows(JsonObject p) {
        AtlasBlueprint.Variant var = variantOf(p);
        return AtlasBlueprint.requirements(var).size() + (var == AtlasBlueprint.Variant.FULL ? 1 : 0);
    }

    /** Сколько таких предметов в инвентаре игрока (основной, хотбар, вторая рука). */
    private static int have(Item item) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        int n = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
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

    private void drawChecklist(GuiGraphics g, View v, int x, int y, int mx, int my) {
        if (v.title() != null) {
            heading(g, v.title(), x, y);
        }
        AtlasBlueprint.Variant var = variantOf(v.data());
        int yy = y + v.hh() + 4;
        boolean all = true;
        for (var entry : AtlasBlueprint.requirements(var).entrySet()) {
            ItemStack s = new ItemStack(entry.getKey());
            int have = have(entry.getKey());
            boolean ok = have >= entry.getValue();
            all &= ok;
            String counts = have + "/" + entry.getValue();
            g.fill(x + 6, yy, x + 24, yy + 18, 0xFF2A1A0E);
            g.fill(x + 7, yy + 1, x + 23, yy + 17, 0xFF16222B);
            g.renderItem(s, x + 7, yy + 1);
            g.drawString(font, font.plainSubstrByWidth(s.getHoverName().getString(), PAGE_W - 28 - 24 - font.width(counts) - 4),
                    x + 28, yy + 5, INK, false);
            g.drawString(font, counts, x + PAGE_W - 22 - font.width(counts), yy + 5, ok ? 0xFF2E7D32 : 0xFF9B2C2C, false);
            drawCheck(g, x + PAGE_W - 18, yy + 4, ok);
            if (mx >= x + 7 && mx < x + 23 && my >= yy + 1 && my < yy + 17) {
                hoverStack = s;
            }
            yy += 20;
        }
        if (var == AtlasBlueprint.Variant.FULL) {
            ItemStack s = new ItemStack(ModBlocks.CHANNEL_EXPANDER.get());
            g.fill(x + 6, yy, x + 24, yy + 18, 0xFF2A1A0E);
            g.fill(x + 7, yy + 1, x + 23, yy + 17, 0xFF16222B);
            g.renderItem(s, x + 7, yy + 1);
            String counts = Integer.toString(have(ModBlocks.CHANNEL_EXPANDER.get().asItem()));
            g.drawString(font, font.plainSubstrByWidth(s.getHoverName().getString() + " " + Component.translatable("gui.golem_logix.book.optional").getString(),
                    PAGE_W - 28 - 24 - font.width(counts) - 4), x + 28, yy + 5, INK_DIM, false);
            g.drawString(font, counts, x + PAGE_W - 22 - font.width(counts), yy + 5, INK_DIM, false);
            if (mx >= x + 7 && mx < x + 23 && my >= yy + 1 && my < yy + 17) {
                hoverStack = s;
            }
            yy += 20;
        }
        // итог
        drawCheck(g, x + 8, yy + 4, all);
        g.drawString(font, Component.translatable(all ? "gui.golem_logix.book.have_all" : "gui.golem_logix.book.have_not_all"),
                x + 24, yy + 5, all ? 0xFF2E7D32 : 0xFF9B2C2C, false);
        yy += 18;
        // кнопка призраков
        boolean showing = GhostProjection.isShowing(var);
        int bx = x + 8, bw = PAGE_W - 16, bh = 16;
        boolean hover = mx >= bx && mx < bx + bw && my >= yy && my < yy + bh;
        g.fill(bx, yy, bx + bw, yy + bh, 0xFF1A0F08);
        g.fill(bx + 1, yy + 1, bx + bw - 1, yy + bh - 1, hover ? GolemGui.CYAN : GolemGui.BRASS);
        g.fill(bx + 2, yy + 2, bx + bw - 2, yy + bh - 2, showing ? 0xFF1B4A48 : 0xFF3A2314);
        g.drawCenteredString(font, Component.translatable(showing ? "gui.golem_logix.book.ghost_hide" : "gui.golem_logix.book.ghost_show"),
                bx + bw / 2, yy + 4, hover ? 0xFFFFE9A8 : GolemGui.PARCHMENT);
        clickables.add(new Clickable(bx, yy, bw, bh, () -> {
            if (showing) {
                GhostProjection.clear();
            } else {
                GhostProjection.start(var);
                onClose();
            }
        }));
    }

    // ------------------------------------------------- испытания секретного знания

    private static boolean taskDone(String id) {
        return switch (id) {
            case "wither" -> com.golemlogix.client.ClientSecrets.wither();
            case "dimension" -> com.golemlogix.client.ClientSecrets.dimension();
            default -> false;
        };
    }

    private int taskHeight(JsonObject p) {
        int h = 0;
        if (p.has("tasks")) {
            for (JsonElement t : p.getAsJsonArray("tasks")) {
                String text = t.getAsJsonObject().get("text").getAsString().replace('&', '§');
                h += font.split(styled(text), PAGE_W - 30).size() * LINE_H + 6;
            }
        }
        return h;
    }

    /** Список испытаний с отметками: пройдено — зелёная галочка и зачёркнутый текст, нет — пустая латунная рамка. */
    private void drawTasks(GuiGraphics g, View v, int x, int y, int mx, int my) {
        if (v.title() != null) {
            heading(g, v.title(), x, y);
        }
        int yy = y + v.hh() + 4;
        if (!v.data().has("tasks")) {
            return;
        }
        for (JsonElement t : v.data().getAsJsonArray("tasks")) {
            JsonObject task = t.getAsJsonObject();
            boolean done = taskDone(task.get("id").getAsString());
            g.fill(x + 7, yy, x + 19, yy + 12, 0xFF2A1A0E);
            g.fill(x + 8, yy + 1, x + 18, yy + 11, done ? 0xFFE8F5E0 : 0xFFF2E6C8);
            if (done) {
                drawCheck(g, x + 8, yy + 2, true);
            }
            String text = (done ? "§m" : "") + task.get("text").getAsString().replace('&', '§');
            int ty = yy + 2;
            for (FormattedCharSequence l : font.split(styled(text), PAGE_W - 30)) {
                g.drawString(font, l, x + 24, ty, done ? INK_DIM : INK, false);
                ty += LINE_H;
            }
            yy = Math.max(yy + 14, ty) + 4;
        }
    }

    // ---------------------------------------------------------- иллюстрации

    private void drawImage(GuiGraphics g, View v, int x, int y) {
        JsonObject d = v.data();
        int size = d.has("size") ? d.get("size").getAsInt() : 144;
        int top = y + 8;
        if (v.title() != null) {
            heading(g, v.title(), x, y);
            top = y + v.hh() + 4;
        }
        ResourceLocation tex = tex(d.get("texture").getAsString());
        int ix = x + (PAGE_W - size) / 2;
        g.fill(ix - 2, top - 2, ix + size + 2, top + size + 2, 0x30301808);
        g.blit(tex, ix, top, size, size, 0, 0, 288, 288, 288, 288);
    }

    // ----------------------------------------------------------------- ввод

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;

        // вкладки разделов
        List<Integer> tabs = tabs();
        int step = tabStep(tabs.size());
        for (int j = 0; j < tabs.size(); j++) {
            int i = tabs.get(j);
            int x = tabX(i), y = TAB_TOP + j * step;
            if (bx >= x && bx < -6 && by >= y && by < y + step - 1) {
                categoryIndex = i;
                closeEntry();
                return true;
            }
        }
        if (openEntry != null) {
            return clickArticle(bx, by) || super.mouseClicked(mouseX, mouseY, button);
        }

        // результаты поиска
        List<BookModel.Entry> res = searchResults();
        int y = 24;
        for (BookModel.Entry e : res) {
            if (bx >= W - 200 && bx < W - 10 && by >= y && by < y + 16) {
                categoryIndex = book.categories.indexOf(e.category);
                open(e);
                return true;
            }
            y += 16;
        }

        if (search.mouseClicked(mouseX, mouseY, button)) {
            setFocused(search);
            return true;
        }
        search.setFocused(false);

        // карта: начало перетаскивания / клик по узлу
        int ax = 8, ay = 30, aw = W - 16, ah = H - 40;
        if (bx >= ax && bx < ax + aw && by >= ay && by < ay + ah) {
            dragging = true;
            dragStartX = mouseX;
            dragStartY = mouseY;
            dragMoved = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickArticle(double bx, double by) {
        for (Clickable c : clickables) {
            if (bx >= c.x() && bx < c.x() + c.w() && by >= c.y() && by < c.y() + c.h()) {
                c.action().run();
                return true;
            }
        }
        int ny = navY();
        if (by >= ny && by < ny + 14) {
            if (bx >= LEFT_PAGE_X && bx < LEFT_PAGE_X + 40 && spread > 0) {
                spread--;
                return true;
            }
            if (bx >= RIGHT_PAGE_X + PAGE_W - 40 && bx < RIGHT_PAGE_X + PAGE_W && spread < spreads() - 1) {
                spread++;
                return true;
            }
            if (bx >= W / 2 - 20 && bx < W / 2 + 20) {
                closeEntry();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && openEntry == null) {
            panX[categoryIndex] += dx / k / zoom[categoryIndex];
            panY[categoryIndex] += dy / k / zoom[categoryIndex];
            dragMoved += Math.abs(dx) + Math.abs(dy);
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    private void clampPan() {
        double lim = 300;
        panX[categoryIndex] = Mth.clamp(panX[categoryIndex], -lim - 200, lim + 200);
        panY[categoryIndex] = Mth.clamp(panY[categoryIndex], -lim, lim);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            if (dragMoved < 4 && openEntry == null) {
                double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;
                int ax = 8, ay = 30, aw = W - 16, ah = H - 40;
                int cx = ax + aw / 2, cy = ay + ah / 2;
                double half = NODE * zoom[categoryIndex] / 2.0;
                for (BookModel.Entry e : category().entries) {
                    if (e.visible() && Math.abs(bx - nodeX(e, cx)) <= half && Math.abs(by - nodeY(e, cy)) <= half) {
                        open(e);
                        return true;
                    }
                }            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openEntry == null && book.categories.size() > 0) {
            double bx = (mouseX - ox) / k, by = (mouseY - oy) / k;
            int ax = 8, ay = 30, aw = W - 16, ah = H - 40;
            if (bx >= ax && bx < ax + aw && by >= ay && by < ay + ah && scrollY != 0) {
                int cx = ax + aw / 2, cy = ay + ah / 2;
                double z1 = zoom[categoryIndex];
                double z2 = Mth.clamp(z1 * Math.pow(1.15, Math.signum(scrollY)), MIN_ZOOM, MAX_ZOOM);
                // точка карты под курсором остаётся на месте
                double wx = (bx - cx) / z1 - panX[categoryIndex], wy = (by - cy) / z1 - panY[categoryIndex];
                zoom[categoryIndex] = z2;
                panX[categoryIndex] = (bx - cx) / z2 - wx;
                panY[categoryIndex] = (by - cy) / z2 - wy;
                clampPan();
                return true;
            }
        }
        if (openEntry != null) {
            if (scrollY < 0 && spread < spreads() - 1) {
                spread++;
            } else if (scrollY > 0 && spread > 0) {
                spread--;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (openEntry != null) {
                closeEntry();
                return true;
            }
            if (search.isFocused() && !search.getValue().isEmpty()) {
                search.setValue("");
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (openEntry != null) {
            if ((key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) && spread < spreads() - 1) {
                spread++;
                return true;
            }
            if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) && spread > 0) {
                spread--;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                closeEntry();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                List<BookModel.Entry> r = searchResults();
                if (!r.isEmpty()) {
                    categoryIndex = book.categories.indexOf(r.get(0).category);
                    open(r.get(0));
                }
                return true;
            }
            search.keyPressed(key, scan, mods);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_SLASH) {
            search.setFocused(true);
            setFocused(search);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (openEntry == null && search.isFocused()) {
            return search.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xB0000000);
    }
}
