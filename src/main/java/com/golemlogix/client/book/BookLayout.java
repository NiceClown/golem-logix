package com.golemlogix.client.book;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.AtlasBlueprint;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Guide book page geometry, text pagination, pre-built page view models, and layout auditor.
 */
final class BookLayout {
    static final int W = 380, H = 240;
    static final int PAGE_W = 160, PAGE_H = 206;
    static final int LEFT_PAGE_X = 16, RIGHT_PAGE_X = 204, PAGES_Y = 16;
    static final int LINE_H = 10;

    static final int INK = 0xFF3B2A18;
    static final int INK_GOLD = 0xFF8A5A0B;
    static final int INK_DIM = 0xFF7A6446;
    /** What the book's "&l" marks: a deep copper ink in place of Minecraft's bold, which smears dense (Cyrillic) letters together. */
    static final int INK_EMPHASIS = 0xFF8E2F12;

    enum Kind {TEXT, CRAFTING, IMAGE, RITUAL, BLUEPRINT, TABLE, SPOTLIGHT, CHECKLIST, TASKS}

    record Line(FormattedCharSequence seq, int x, int y, int color) {
    }

    record Grid(ItemStack[][] cells, ItemStack result) {
    }

    record RitualData(List<ItemStack> inputs, ItemStack station, List<ItemStack> outputs, List<FormattedCharSequence> condition) {
    }

    record ImageData(ResourceLocation texture, int size) {
    }

    record TableData(int[] widths, List<List<FormattedCharSequence>> headerCells, int headerLines,
                     List<List<List<FormattedCharSequence>>> rows, int[] rowLines, int totalHeight) {
    }

    record ChecklistRow(Item item, ItemStack icon, String name, int need) {
    }

    record ChecklistData(AtlasBlueprint.Variant variant, List<ChecklistRow> rows, ItemStack optionalIcon, String optionalName) {
    }

    record TaskRow(String id, List<FormattedCharSequence> pendingLines, List<FormattedCharSequence> doneLines) {
    }

    record TasksData(List<TaskRow> rows, int totalHeight) {
    }

    record View(Kind kind, JsonObject data, List<FormattedCharSequence> headingLines, List<Line> lines, int hh, Object extra) {
    }

    private BookLayout() {
    }

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/gui/book/" + name + ".png");
    }

    /**
     * Text with "§" codes (the book uses "&") -> a component of pieces with their own styles. Wrapping such a component does not
     * lose or mix up the formatting (in a line with codes inside, bold text "leaked" past &r and the width was measured wrong).
     * "&l" is drawn in the emphasis ink, not bold (see INK_EMPHASIS).
     */
    static MutableComponent styled(String s) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder seg = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                if (seg.length() > 0) {
                    out.append(Component.literal(seg.toString()).withStyle(style));
                    seg.setLength(0);
                }
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(++i)));
                if (f == ChatFormatting.BOLD) {
                    style = style.withColor(INK_EMPHASIS);
                } else if (f != null) {
                    style = f == ChatFormatting.RESET ? Style.EMPTY : style.applyLegacyFormat(f);
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

    /** Splits paragraphs into page lines; returns the portions that fit by height. */
    private static List<List<Line>> flow(Font font, List<String> paras, int startY, int limitY, int firstLimitY) {
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
                para = para.substring(2);
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

    /** Resolves all visible pages of an entry into pre-measured two-page spread views. */
    static List<View> buildViews(Font font, BookModel.Entry entry) {
        List<View> out = new ArrayList<>();
        for (JsonObject p : entry.pages) {
            if (!BookModel.pageVisible(p)) {
                continue;
            }
            Kind kind = kindOf(p.get("type").getAsString());
            String title = p.has("title") ? p.get("title").getAsString() : null;
            Object extra = null;
            switch (kind) {
                case CRAFTING -> {
                    Grid grid = resolveGrid(p);
                    extra = grid;
                    if (title == null) {
                        title = grid.result().isEmpty() ? "?" : grid.result().getHoverName().getString();
                    }
                }
                case SPOTLIGHT -> {
                    ItemStack item = parseStack(p.get("item").getAsString());
                    extra = item;
                    if (title == null) {
                        title = item.getHoverName().getString();
                    }
                }
                case IMAGE -> extra = new ImageData(tex(p.get("texture").getAsString()), p.has("size") ? p.get("size").getAsInt() : 144);
                case RITUAL -> extra = buildRitual(font, p);
                case TABLE -> extra = buildTable(font, p);
                case CHECKLIST -> extra = buildChecklist(p);
                case TASKS -> extra = buildTasks(font, p);
                default -> {
                }
            }
            List<String> text = BookModel.strings(p, "text");
            List<FormattedCharSequence> headingLines = title == null ? List.of() : font.split(styled(title), PAGE_W - 12);
            int hh = title == null ? 0 : 7 + headingLines.size() * LINE_H + 4;
            int start = textStartY(kind, hh, extra);
            List<List<Line>> chunks = flow(font, text, start, PAGE_H - 14, PAGE_H - 14);
            for (int i = 0; i < chunks.size(); i++) {
                if (i == 0) {
                    out.add(new View(kind, p, headingLines, chunks.get(0), hh, extra));
                } else {
                    out.add(new View(Kind.TEXT, p, List.of(), chunks.get(i), 0, null));
                }
            }
        }
        if (out.size() % 2 == 1) {
            out.add(new View(Kind.TEXT, new JsonObject(), List.of(), List.of(), 0, null));
        }
        return out;
    }

    private static Kind kindOf(String type) {
        return switch (type) {
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
    }

    /** Vertical offset where body text begins beneath a page's heading and graphics. */
    private static int textStartY(Kind kind, int hh, Object extra) {
        return switch (kind) {
            case TEXT -> hh > 0 ? hh + 2 : 8;
            case CRAFTING -> hh + 6 + 54 + 12;
            case IMAGE -> (hh > 0 ? hh + 4 : 8) + ((ImageData) extra).size() + 8;
            case RITUAL -> {
                int condLines = ((RitualData) extra).condition().size();
                yield hh + 8 + 20 + (condLines > 0 ? condLines * LINE_H + 6 : 0) + 6;
            }
            case BLUEPRINT -> hh + 14 + 72 + 10;
            case TABLE -> hh + 4 + ((TableData) extra).totalHeight() + 8;
            case SPOTLIGHT -> hh + 6 + 48 + 10;
            case CHECKLIST -> {
                ChecklistData cl = (ChecklistData) extra;
                int rows = cl.rows().size() + (cl.optionalIcon() != null ? 1 : 0);
                yield hh + 4 + rows * 20 + 18 + 24 + 8;
            }
            case TASKS -> hh + 4 + ((TasksData) extra).totalHeight() + 6;
        };
    }

    // ----------------------------------------------------------------- page data builders

    private static Grid resolveGrid(JsonObject d) {
        ItemStack[][] cells = new ItemStack[9][];
        for (int i = 0; i < 9; i++) {
            cells[i] = new ItemStack[0];
        }
        ItemStack result = ItemStack.EMPTY;
        if (d.has("recipe")) {
            var level = Minecraft.getInstance().level;
            ResourceLocation id = ResourceLocation.tryParse(d.get("recipe").getAsString());
            if (level != null && id != null) {
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

    private static ItemStack parseStack(String spec) {
        int star = spec.indexOf('*');
        ItemStack s = BookModel.stack(star < 0 ? spec : spec.substring(0, star));
        if (star >= 0) {
            int n = Integer.parseInt(spec.substring(star + 1));
            // for a fluid the number is mB
            s = FluidGhostItem.is(s) ? FluidGhostItem.withAmount(s, n) : s.copyWithCount(n);
        }
        return s;
    }

    private static RitualData buildRitual(Font font, JsonObject d) {
        List<ItemStack> inputs = new ArrayList<>();
        for (String in : BookModel.strings(d, "inputs")) {
            inputs.add(parseStack(in));
        }
        ItemStack station = d.has("station") ? parseStack(d.get("station").getAsString()) : ItemStack.EMPTY;
        List<ItemStack> outputs = new ArrayList<>();
        for (String out : BookModel.strings(d, "outputs")) {
            outputs.add(parseStack(out));
        }
        List<FormattedCharSequence> cond = d.has("condition")
                ? font.split(styled("§o" + d.get("condition").getAsString().replace('&', '§')), PAGE_W - 16)
                : List.of();
        return new RitualData(inputs, station, outputs, cond);
    }

    private static TableData buildTable(Font font, JsonObject d) {
        var header = BookModel.strings(d, "header");
        JsonArray rawRows = d.has("rows") ? d.getAsJsonArray("rows") : new JsonArray();
        int cols = Math.max(1, header.size());
        int[] widths = columnWidths(d, cols);
        List<List<FormattedCharSequence>> headerCells = new ArrayList<>(header.size());
        int hl = 1;
        for (int c = 0; c < header.size(); c++) {
            List<FormattedCharSequence> ls = cellLines(font, header.get(c), widths[c] - 3, true);
            headerCells.add(ls);
            hl = Math.max(hl, ls.size());
        }
        int totalHeight = hl * LINE_H + 2;
        List<List<List<FormattedCharSequence>>> rows = new ArrayList<>(rawRows.size());
        int[] rowLines = new int[rawRows.size()];
        int idx = 0;
        for (JsonElement re : rawRows) {
            var row = re.getAsJsonArray();
            List<List<FormattedCharSequence>> cells = new ArrayList<>(cols);
            int rl = 1;
            for (int c = 0; c < cols && c < row.size(); c++) {
                List<FormattedCharSequence> ls = cellLines(font, row.get(c).getAsString(), widths[c] - 3, false);
                cells.add(ls);
                rl = Math.max(rl, ls.size());
            }
            rows.add(cells);
            rowLines[idx++] = rl;
            totalHeight += rl * LINE_H + 2;
        }
        return new TableData(widths, headerCells, hl, rows, rowLines, totalHeight);
    }

    /** Table cell text wrapped to the column width (the header in the emphasis ink). */
    private static List<FormattedCharSequence> cellLines(Font font, String text, int width, boolean bold) {
        MutableComponent c = styled(text.replace('&', '§'));
        if (bold) {
            c = Component.empty().withStyle(s -> s.withColor(INK_EMPHASIS)).append(c);
        }
        return font.split(c, width);
    }

    private static int[] columnWidths(JsonObject d, int cols) {
        int[] widths = new int[cols];
        JsonArray wa = d.has("widths") ? d.getAsJsonArray("widths") : null;
        int fallback = (PAGE_W - 12) / cols;
        for (int i = 0; i < cols; i++) {
            widths[i] = wa != null && i < wa.size() ? wa.get(i).getAsInt() : fallback;
        }
        return widths;
    }

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

    private static ChecklistData buildChecklist(JsonObject p) {
        AtlasBlueprint.Variant var = variantOf(p);
        List<ChecklistRow> rows = new ArrayList<>();
        for (var entry : AtlasBlueprint.requirements(var).entrySet()) {
            ItemStack icon = new ItemStack(entry.getKey());
            rows.add(new ChecklistRow(entry.getKey(), icon, icon.getHoverName().getString(), entry.getValue()));
        }
        ItemStack optIcon = null;
        String optName = null;
        if (var == AtlasBlueprint.Variant.FULL) {
            optIcon = new ItemStack(ModBlocks.CHANNEL_EXPANDER.get());
            optName = optIcon.getHoverName().getString() + " " + Component.translatable("gui.golem_logix.book.optional").getString();
        }
        return new ChecklistData(var, rows, optIcon, optName);
    }

    private static TasksData buildTasks(Font font, JsonObject p) {
        List<TaskRow> out = new ArrayList<>();
        int h = 0;
        if (p.has("tasks")) {
            for (JsonElement t : p.getAsJsonArray("tasks")) {
                JsonObject task = t.getAsJsonObject();
                String raw = task.get("text").getAsString().replace('&', '§');
                List<FormattedCharSequence> pending = font.split(styled(raw), PAGE_W - 30);
                List<FormattedCharSequence> done = font.split(styled("§m" + raw), PAGE_W - 30);
                out.add(new TaskRow(task.get("id").getAsString(), pending, done));
                h += pending.size() * LINE_H + 6;
            }
        }
        return new TasksData(out, h);
    }

    // ----------------------------------------------------------------- layout audit

    /**
     * For the developer rig: lays out all book entries of the current language (all pages, as if the secret were revealed) and
     * reports bad layout: a "widow" page with one or two wrapped lines, or graphics that left no room
     * for the text of its page.
     */
    static List<String> auditLayout(Font font) {
        List<String> out = new ArrayList<>();
        BookModel.showAll = true;
        try {
            for (BookModel.Category c : BookModel.get().categories) {
                for (BookModel.Entry e : c.entries) {
                    List<View> views = buildViews(font, e);
                    for (int i = 0; i < views.size(); i++) {
                        View v = views.get(i);
                        boolean continuation = i > 0 && v.data() == views.get(i - 1).data() && !v.data().entrySet().isEmpty();
                        String which = auditName(v.data());
                        if (!continuation && v.kind() == Kind.TABLE) {
                            out.addAll(auditTable(font, e.id, v.data(), v.hh()));
                        }
                        if (continuation && v.lines().size() <= 2) {
                            out.add(e.id + " page " + (i + 1) + " " + which + ": only " + v.lines().size() + " line(s) carried over");
                        }
                        // the array's material list takes a whole page, its text deliberately goes to the next one
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

    /**
     * For layout checks: the whole table fits on the page, the columns are not wider than the page, and every word fits its column
     * (otherwise wrapping would cut it in the middle).
     */
    private static List<String> auditTable(Font font, String id, JsonObject d, int hh) {
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
        int bottom = hh + 4 + buildTable(font, d).totalHeight();
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
                MutableComponent wc = styled(word);
                int ww = font.width(wc);
                if (ww > w) {
                    out.add(id + " table word \"" + word + "\" (" + ww + " px) is wider than its column (" + w + " px)");
                }
            }
        }
        return out;
    }

    /** A page for the report: type, title or recipe, and the start of the text. */
    private static String auditName(JsonObject p) {
        String type = p.has("type") ? p.get("type").getAsString() : "?";
        String name = p.has("title") ? p.get("title").getAsString() : p.has("recipe") ? p.get("recipe").getAsString() : "";
        List<String> text = BookModel.strings(p, "text");
        String start = text.isEmpty() ? "" : text.get(0);
        return "[" + type + (name.isEmpty() ? "" : " \"" + name + "\"") + " / " + (start.length() > 40 ? start.substring(0, 40) + "…" : start) + "]";
    }
}
