package com.golemlogix.client.book;

import com.golemlogix.GolemLogixMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Data of the "Occult Logistics Grimoire" book: sections, entries, pages.
 * Loaded from assets/golem_logix/book/&lt;language&gt;.json (fallback language en_us, then ru_ru).
 */
public final class BookModel {
    public static final class Category {
        public final String id;
        public final String title;
        public final String blurb;
        public final ItemStack icon;
        public final int color;
        public final List<Entry> entries = new ArrayList<>();

        Category(String id, String title, String blurb, ItemStack icon, int color) {
            this.id = id;
            this.title = title;
            this.blurb = blurb;
            this.icon = icon;
            this.color = color;
        }

        /** A section is visible if at least one of its entries is visible (the "Secret Knowledge" section appears after the first trial). */
        public boolean visible() {
            for (Entry e : entries) {
                if (e.visible()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Entry {
        public final String id;
        public final String title;
        public final ItemStack icon;
        public final int x;
        public final int y;
        public final boolean gold;
        public final List<String> parents;
        public final List<JsonObject> pages;
        /**
         * Secret entry: "partial" = visible after the first trial, "full" = only when the recipe is fully open, "mind" = after the
         * first anvil plate (the Dispel method).
         */
        public final String secret;
        /** Mod id the entry is about (its integration): the entry is hidden without that mod; empty = always. */
        public final String requires;
        public Category category;
        /** All content in lower case, for search. */
        public final String searchText;

        Entry(String id, String title, ItemStack icon, int x, int y, boolean gold, List<String> parents, List<JsonObject> pages,
              String secret, String requires) {
            this.requires = requires;
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.x = x;
            this.y = y;
            this.gold = gold;
            this.parents = parents;
            this.pages = pages;
            this.secret = secret;
            StringBuilder sb = new StringBuilder(title.toLowerCase(Locale.ROOT));
            for (JsonObject p : pages) {
                collectText(p, sb);
            }
            this.searchText = sb.toString();
        }

        /** Whether the entry is visible now (secret ones depend on the player's trial progress). */
        public boolean visible() {
            if (showAll) {
                return true;
            }
            // an entry about another mod's integration is shown only when that mod is installed
            if (!requires.isEmpty() && !net.neoforged.fml.ModList.get().isLoaded(requires)) {
                return false;
            }
            return switch (secret) {
                case "partial" -> com.golemlogix.client.ClientSecrets.any();
                case "full" -> com.golemlogix.client.ClientSecrets.all();
                case "mind" -> com.golemlogix.client.ClientSecrets.mind();
                default -> true;
            };
        }

        private static void collectText(JsonElement e, StringBuilder sb) {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                sb.append(' ').append(e.getAsString().toLowerCase(Locale.ROOT));
            } else if (e.isJsonArray()) {
                for (JsonElement x : e.getAsJsonArray()) {
                    collectText(x, sb);
                }
            } else if (e.isJsonObject()) {
                for (var kv : e.getAsJsonObject().entrySet()) {
                    collectText(kv.getValue(), sb);
                }
            }
        }
    }

    public final String title;
    public final List<Category> categories = new ArrayList<>();
    public final Map<String, Entry> byId = new LinkedHashMap<>();

    private BookModel(String title) {
        this.title = title;
    }

    private static BookModel cached;
    private static String cachedLang;
    /** Only for layout checks (developer rig): all entries and pages are visible, as if the secret were revealed. */
    static boolean showAll;

    public static BookModel get() {
        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (cached == null || !lang.equals(cachedLang)) {
            cached = load(lang);
            cachedLang = lang;
        }
        return cached;
    }

    /** Reset the cache (on resource reload). */
    public static void invalidate() {
        cached = null;
    }

    public static ItemStack stack(String id) {
        // "fluid:<fluid id>" is a fluid (a fluid ghost, 1000 mB; "*250" sets the mB)
        if (id.startsWith("fluid:")) {
            ResourceLocation fl = ResourceLocation.tryParse(id.substring(6));
            var fluid = fl == null ? null : BuiltInRegistries.FLUID.getOptional(fl).orElse(null);
            return fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY ? new ItemStack(Items.BARRIER)
                    : com.golemlogix.items.FluidGhostItem.of(new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000));
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return new ItemStack(Items.BARRIER);
        }
        Optional<net.minecraft.world.item.Item> item = BuiltInRegistries.ITEM.getOptional(rl);
        return item.map(ItemStack::new).orElseGet(() -> new ItemStack(Items.BARRIER));
    }

    private static BookModel load(String lang) {
        for (String candidate : new String[]{lang, "en_us", "ru_ru"}) {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "book/" + candidate + ".json");
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (res.isEmpty()) {
                continue;
            }
            try (BufferedReader reader = res.get().openAsReader()) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception ex) {
                GolemLogixMod.LOGGER.error("Failed to load book {}", rl, ex);
            }
        }
        BookModel empty = new BookModel("Golem Logix");
        return empty;
    }

    private static BookModel parse(JsonObject root) {
        BookModel book = new BookModel(root.has("title") ? root.get("title").getAsString() : "Golem Logix");
        for (JsonElement ce : root.getAsJsonArray("categories")) {
            JsonObject c = ce.getAsJsonObject();
            int color = (int) Long.parseLong(c.has("color") ? c.get("color").getAsString().replace("#", "") : "38d9d6", 16) | 0xFF000000;
            Category cat = new Category(c.get("id").getAsString(), c.get("title").getAsString(),
                    c.has("blurb") ? c.get("blurb").getAsString() : "", stack(c.get("icon").getAsString()), color);
            for (JsonElement ee : c.getAsJsonArray("entries")) {
                JsonObject e = ee.getAsJsonObject();
                List<String> parents = new ArrayList<>();
                if (e.has("parents")) {
                    for (JsonElement p : e.getAsJsonArray("parents")) {
                        parents.add(p.getAsString());
                    }
                }
                List<JsonObject> pages = new ArrayList<>();
                for (JsonElement p : e.getAsJsonArray("pages")) {
                    pages.add(p.getAsJsonObject());
                }
                Entry entry = new Entry(e.get("id").getAsString(), e.get("title").getAsString(), stack(e.get("icon").getAsString()),
                        e.get("x").getAsInt(), e.get("y").getAsInt(), e.has("gold") && e.get("gold").getAsBoolean(), parents, pages,
                        e.has("secret") ? e.get("secret").getAsString() : "", e.has("requires") ? e.get("requires").getAsString() : "");
                entry.category = cat;
                cat.entries.add(entry);
                book.byId.put(entry.id, entry);
            }
            book.categories.add(cat);
        }
        return book;
    }

    /**
     * Whether to show a page: the "when" field is "wither" / "dimension" (that trial is passed), "!wither" / "!dimension"
     * (not yet), "both" (both), "one" (exactly one).
     */
    public static boolean pageVisible(JsonObject page) {
        if (!page.has("when") || showAll) {
            return true;
        }
        boolean w = com.golemlogix.client.ClientSecrets.wither(), d = com.golemlogix.client.ClientSecrets.dimension();
        return switch (page.get("when").getAsString()) {
            case "wither" -> w;
            case "dimension" -> d;
            case "!wither" -> !w;
            case "!dimension" -> !d;
            case "both" -> w && d;
            case "one" -> w != d;
            default -> true;
        };
    }

    public static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (!o.has(key)) {
            return out;
        }
        JsonElement e = o.get(key);
        if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) {
                out.add(x.getAsString());
            }
        } else {
            out.add(e.getAsString());
        }
        return out;
    }
}
