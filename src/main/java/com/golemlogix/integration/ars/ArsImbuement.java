package com.golemlogix.integration.ars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Ars Nouveau Imbuement Chamber recipes that turn one item into a Source Gem or a Source Gem block (amethyst, an amethyst
 * block, lapis: no pedestal items). The assembled Mana Saturator runs them twice as fast as the chamber for a third more Source.
 * <p>
 * The recipes are read from the game's recipe manager, so a modpack that changes them changes the Saturator too. There is no
 * compile dependency on Ars Nouveau: every recipe of the type "ars_nouveau:imbuement" is encoded with its own codec and its
 * fields ("input", "output", "source", "pedestalItems") are read from the result.
 */
public final class ArsImbuement {
    private static final Identifier TYPE = Identifier.fromNamespaceAndPath("ars_nouveau", "imbuement");
    private static final Set<Identifier> OUTPUTS = Set.of(
            Identifier.fromNamespaceAndPath("ars_nouveau", "source_gem"),
            Identifier.fromNamespaceAndPath("ars_nouveau", "source_gem_block"));
    /** The chamber draws Source from jars 200 per second and never finishes before 100 ticks. */
    private static final int CHAMBER_SOURCE_PER_SECOND = 200, CHAMBER_MIN_TICKS = 100;
    /** The Saturator: twice as fast, a third more Source. */
    public static final float SOURCE_FACTOR = 1.33f;

    /**
     * One recipe: what goes in, what comes out, the chamber's Source cost and time, and what the Saturator asks for it.
     */
    public record Entry(Ingredient input, ItemStack output, int chamberSource, int chamberTicks, int source, int ticks) {
    }

    /** Parsed recipes per recipe manager (the server and the client have their own; a /reload makes a new one). */
    private static final Map<RecipeManager, List<Entry>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private ArsImbuement() {
    }

    /** Ticks the Imbuement Chamber needs for a recipe with this Source cost. */
    public static int chamberTicks(int source) {
        int seconds = (source + CHAMBER_SOURCE_PER_SECOND - 1) / CHAMBER_SOURCE_PER_SECOND;
        return Math.max(CHAMBER_MIN_TICKS, seconds * 20);
    }

    public static List<Entry> all(@Nullable Level level) {
        if (level == null) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        List<Entry> list = CACHE.get(manager);
        if (list == null) {
            list = read(level, manager);
            CACHE.put(manager, list);
        }
        return list;
    }

    @Nullable
    public static Entry find(@Nullable Level level, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        for (Entry e : all(level)) {
            if (e.input.test(input)) {
                return e;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Entry> read(Level level, RecipeManager manager) {
        RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(TYPE);
        if (type == null) {
            return List.of();
        }
        DynamicOps<JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<Entry> out = new ArrayList<>();
        for (RecipeHolder<?> holder : (List<RecipeHolder<?>>) (List) manager.getAllRecipesFor((RecipeType) type)) {
            try {
                Recipe<?> recipe = holder.value();
                MapCodec<Recipe<?>> codec = (MapCodec<Recipe<?>>) (MapCodec) recipe.getSerializer().codec();
                JsonElement json = codec.codec().encodeStart(ops, recipe).result().orElse(null);
                if (json == null || !json.isJsonObject()) {
                    continue;
                }
                JsonObject o = json.getAsJsonObject();
                if (o.has("pedestalItems") && o.get("pedestalItems").isJsonArray() && !o.getAsJsonArray("pedestalItems").isEmpty()) {
                    continue;
                }
                ItemStack result = o.has("output") ? ItemStack.CODEC.parse(ops, o.get("output")).result().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
                if (result.isEmpty() || !OUTPUTS.contains(BuiltInRegistries.ITEM.getKey(result.getItem()))) {
                    continue;
                }
                Ingredient input = o.has("input") ? Ingredient.CODEC_NONEMPTY.parse(ops, o.get("input")).result().orElse(null) : null;
                int source = o.has("source") ? o.get("source").getAsInt() : 0;
                if (input == null || input.isEmpty()) {
                    continue;
                }
                int chamber = chamberTicks(source);
                out.add(new Entry(input, result, source, chamber, Math.round(source * SOURCE_FACTOR), Math.max(1, chamber / 2)));
            } catch (RuntimeException e) {
                // a recipe this reader does not understand is skipped; the others still work
            }
        }
        return List.copyOf(out);
    }
}
