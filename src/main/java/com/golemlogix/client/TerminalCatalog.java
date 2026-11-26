package com.golemlogix.client;

import com.golemlogix.config.GolemClientConfig;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import com.golemlogix.system.Gear;
import com.golemlogix.system.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Data model, filtering, sorting, metadata caching, and persisted view preferences for {@link TerminalScreen}.
 */
final class TerminalCatalog {
    enum SortMode {
        NAME, COUNT, MOD;

        SortMode next() {
            SortMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        String langKey() {
            return "gui.golem_logix.sort." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the list shows: everything together (the default), only items or only fluids. */
    enum ViewMode {
        ALL, ITEMS, FLUIDS;

        ViewMode next() {
            ViewMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        String langKey() {
            return "gui.golem_logix.view." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A list row: either an item with a count or a fluid (then fluid is not empty). */
    record Row(ItemStack stack, long count, boolean craftable, FluidStack fluid, String name, String namespace,
               String countText, boolean gear) {
        boolean isFluid() {
            return !fluid.isEmpty();
        }

        /** Count "in units": buckets for a fluid. */
        double units() {
            return isFluid() ? fluid.getAmount() / 1000.0 : count;
        }
    }

    /** Cached per-ItemKey display metadata so periodic network sync rebuilds skip hover-name/tag/gear recomputation. */
    private record ItemMeta(String lowerName, String namespace, boolean gear) {
    }

    private static final int META_CACHE_LIMIT = 8192;

    private static SortMode sortMode = SortMode.NAME;
    private static boolean reverse = false;
    private static ViewMode viewMode = ViewMode.ALL;
    private static boolean craftableOnly = false;
    private static String lastSearch = "";

    private int builtVersion = -1;
    private boolean dirty = true;
    private final List<Row> rows = new ArrayList<>();
    private final Map<ItemKey, ItemMeta> metaCache = new HashMap<>();
    private final Map<ItemKey, String> tooltipCache = new HashMap<>();

    // ------------------------------------------------------------------ shared preferences & config

    static SortMode sortMode() {
        return sortMode;
    }

    static void cycleSort() {
        sortMode = sortMode.next();
    }

    static boolean reverse() {
        return reverse;
    }

    static void toggleReverse() {
        reverse = !reverse;
    }

    static ViewMode viewMode() {
        return viewMode;
    }

    static void cycleView() {
        viewMode = viewMode.next();
    }

    static boolean craftableOnly() {
        return craftableOnly;
    }

    static void toggleCraftableOnly() {
        craftableOnly = !craftableOnly;
    }

    static String lastSearch() {
        return lastSearch;
    }

    static void setLastSearch(String s) {
        lastSearch = s;
    }

    static GolemClientConfig.JeiSync jeiSync() {
        try {
            return GolemClientConfig.JEI_SYNC.get();
        } catch (IllegalStateException notLoaded) {
            return GolemClientConfig.JeiSync.TWO_WAY;
        }
    }

    static GolemClientConfig.TerminalSize sizeMode() {
        try {
            return GolemClientConfig.TERMINAL_SIZE.get();
        } catch (IllegalStateException notLoaded) {
            return GolemClientConfig.TerminalSize.NORMAL;
        }
    }

    static void setJeiSync(GolemClientConfig.JeiSync mode) {
        try {
            GolemClientConfig.JEI_SYNC.set(mode);
            GolemClientConfig.JEI_SYNC.save();
        } catch (IllegalStateException ignored) {
        }
    }

    static void setSizeMode(GolemClientConfig.TerminalSize mode) {
        try {
            GolemClientConfig.TERMINAL_SIZE.set(mode);
            GolemClientConfig.TERMINAL_SIZE.save();
        } catch (IllegalStateException ignored) {
        }
    }

    // ------------------------------------------------------------------ catalog state

    void markDirty() {
        dirty = true;
    }

    void clearCaches() {
        metaCache.clear();
        tooltipCache.clear();
    }

    int size() {
        return rows.size();
    }

    Row get(int idx) {
        return rows.get(idx);
    }

    /** Rebuilds the filtered and sorted list whenever network data or query/filter state changed. Returns true if rebuilt. */
    boolean ensureBuilt(TerminalMenu menu, String rawQuery, Minecraft mc) {
        if (!dirty && builtVersion == menu.clientVersion) {
            return false;
        }
        rebuild(menu, rawQuery, mc);
        return true;
    }

    int indexOfName(TerminalMenu menu, String rawQuery, Minecraft mc, String part) {
        ensureBuilt(menu, rawQuery, mc);
        String q = part.toLowerCase(Locale.ROOT);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).name().contains(q)) {
                return i;
            }
        }
        return -1;
    }

    private ItemMeta metaOf(ItemKey key, ItemStack stack) {
        if (metaCache.size() > META_CACHE_LIMIT) {
            metaCache.clear();
            tooltipCache.clear();
        }
        return metaCache.computeIfAbsent(key, k -> new ItemMeta(
                stack.getHoverName().getString().toLowerCase(Locale.ROOT),
                BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),
                Gear.is(stack)));
    }

    private String tooltipTextOf(ItemKey key, ItemStack stack, Minecraft mc) {
        return tooltipCache.computeIfAbsent(key, k -> {
            StringBuilder sb = new StringBuilder();
            for (Component line : Screen.getTooltipFromItem(mc, stack)) {
                sb.append(line.getString().toLowerCase(Locale.ROOT)).append('\n');
            }
            return sb.toString();
        });
    }

    private void rebuild(TerminalMenu menu, String rawQuery, Minecraft mc) {
        dirty = false;
        builtVersion = menu.clientVersion;
        rows.clear();

        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        String craftShort = Component.translatable("gui.golem_logix.craft_short").getString();
        TerminalInfoPayload info = menu.clientInfo;
        boolean showItems = viewMode != ViewMode.FLUIDS;
        boolean showFluids = viewMode != ViewMode.ITEMS && info != null;

        // craftable fluids come as fluid ghost keys among the items: they go with the fluids
        List<ItemStack> craftFluids = showFluids ? new ArrayList<>() : null;
        for (TerminalListPayload.Entry e : menu.clientEntries) {
            ItemStack keyStack = e.key();
            if (FluidGhostItem.is(keyStack)) {
                if (craftFluids != null && e.craftable()) {
                    craftFluids.add(keyStack);
                }
                continue;
            }
            if (!showItems || (craftableOnly && !e.craftable())) {
                continue;
            }
            ItemKey key = ItemKey.of(keyStack);
            ItemMeta meta = metaOf(key, keyStack);
            if (matchesItem(key, keyStack, meta, query, mc)) {
                String countText = e.count() > 0 ? GolemGui.formatCount(e.count()) : craftShort;
                rows.add(new Row(keyStack, e.count(), e.craftable(), FluidStack.EMPTY,
                        meta.lowerName(), meta.namespace(), countText, meta.gear()));
            }
        }
        if (showFluids) {
            appendFluids(info.fluids(), craftFluids, query, craftShort);
        }

        Comparator<Row> byName = Comparator.comparing(Row::name);
        Comparator<Row> cmp = switch (sortMode) {
            case NAME -> byName;
            case COUNT -> Comparator.comparingDouble(Row::units).reversed().thenComparing(byName);
            case MOD -> Comparator.comparing(Row::namespace).thenComparing(byName);
        };
        rows.sort(reverse ? cmp.reversed() : cmp);
    }

    private void appendFluids(List<FluidStack> storedFluids, List<ItemStack> craftFluids, String query, String craftShort) {
        for (FluidStack f : storedFluids) {
            if (f.isEmpty()) {
                continue;
            }
            ItemStack ghost = ItemStack.EMPTY;
            for (int i = 0; i < craftFluids.size(); i++) {
                if (FluidStack.isSameFluidSameComponents(FluidGhostItem.fluid(craftFluids.get(i)), f)) {
                    ghost = craftFluids.remove(i);
                    break;
                }
            }
            if (craftableOnly && ghost.isEmpty()) {
                continue;
            }
            String name = f.getHoverName().getString().toLowerCase(Locale.ROOT);
            String ns = BuiltInRegistries.FLUID.getKey(f.getFluid()).getNamespace();
            if (matchesFluid(name, ns, query)) {
                String countText = viewMode == ViewMode.FLUIDS
                        ? GolemGui.formatCount(f.getAmount()) : GolemGui.formatBuckets(f.getAmount());
                rows.add(new Row(ghost, f.getAmount(), !ghost.isEmpty(), f, name, ns, countText, false));
            }
        }
        // none of it in the network, but a pattern makes it: a row to order it from
        for (ItemStack ghost : craftFluids) {
            FluidStack f = FluidGhostItem.fluid(ghost);
            if (f.isEmpty()) {
                continue;
            }
            String name = f.getHoverName().getString().toLowerCase(Locale.ROOT);
            String ns = BuiltInRegistries.FLUID.getKey(f.getFluid()).getNamespace();
            if (matchesFluid(name, ns, query)) {
                rows.add(new Row(ghost, 0, true, f.copyWithAmount(1), name, ns, craftShort, false));
            }
        }
    }

    private static boolean matchesFluid(String name, String namespace, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (query.startsWith("@")) {
            return namespace.contains(query.substring(1));
        }
        if (query.startsWith("#")) {
            return false;
        }
        return name.contains(query);
    }

    private boolean matchesItem(ItemKey key, ItemStack stack, ItemMeta meta, String query, Minecraft mc) {
        if (query.isEmpty()) {
            return true;
        }
        if (query.startsWith("@")) {
            return meta.namespace().contains(query.substring(1));
        }
        if (query.startsWith("#")) {
            return tooltipTextOf(key, stack, mc).contains(query.substring(1));
        }
        return meta.lowerName().contains(query);
    }
}
