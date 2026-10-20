package com.golemlogix.system;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.items.FluidGhostItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Fuzzy mode (RS's fuzzy mode, AE2's Fuzzy Card and pattern substitutions), one rule for the whole mod:
 * <ul>
 * <li>a fuzzy ghost without a tag takes the same item with any components (damage, enchantments, a name);</li>
 * <li>with a tag chosen on it ({@link ModDataComponents#FUZZY_TAG}) it takes any item of the tag: stained glass for glass with
 * {@code #c:glass_blocks};</li>
 * <li>a fuzzy crafting table pattern takes in every cell what its recipe itself accepts there (any planks where the recipe says
 * planks), so the recipe always still works;</li>
 * <li>fluids always match exactly.</li>
 * </ul>
 * Fuzzy ghosts are filters of buses and the interface with a Fuzzy Card, and processing inputs in the encoder's fuzzy mode.
 */
public final class Fuzzy {
    /** Per-input fuzziness of a pattern: exact, the same item with any components, a tag ("#ns:path"), the recipe's own choice. */
    public static final String EXACT = "", ANY_COMPONENTS = "~", RECIPE = "@";

    private Fuzzy() {
    }

    @Nullable
    public static TagKey<Item> tag(ItemStack ghost) {
        return ghost.get(ModDataComponents.FUZZY_TAG.get());
    }

    /** The ghost without its fuzzy tag: the item it stands for (exact matching, extraction templates, keys). */
    public static ItemStack plain(ItemStack ghost) {
        if (!ghost.has(ModDataComponents.FUZZY_TAG.get())) {
            return ghost;
        }
        ItemStack copy = ghost.copy();
        copy.remove(ModDataComponents.FUZZY_TAG.get());
        return copy;
    }

    public static ItemStack withTag(ItemStack ghost, @Nullable TagKey<Item> tag) {
        ItemStack copy = ghost.copy();
        if (tag == null) {
            copy.remove(ModDataComponents.FUZZY_TAG.get());
        } else {
            copy.set(ModDataComponents.FUZZY_TAG.get(), tag);
        }
        return copy;
    }

    /** Whether stack can stand for ghost: exactly, or fuzzily (the same item with any components, or any item of the tag). */
    public static boolean matches(ItemStack ghost, ItemStack stack, boolean fuzzy) {
        if (ghost.isEmpty() || stack.isEmpty()) {
            return false;
        }
        if (!fuzzy || FluidGhostItem.is(ghost) || FluidGhostItem.is(stack)) {
            return ItemStack.isSameItemSameComponents(plain(ghost), stack);
        }
        TagKey<Item> t = tag(ghost);
        return t != null ? stack.is(t) : stack.is(ghost.getItem());
    }

    /** The tags of the ghost's item in the order a click goes through them: the common "c:" tags first, then Minecraft's, then others. */
    public static List<TagKey<Item>> tagsOf(ItemStack ghost) {
        if (ghost.isEmpty() || FluidGhostItem.is(ghost)) {
            return List.of();
        }
        return ghost.getItem().builtInRegistryHolder().tags()
                .sorted(Comparator.<TagKey<Item>>comparingInt(t -> t.location().getNamespace().equals("c") ? 0
                                : t.location().getNamespace().equals("minecraft") ? 1 : 2)
                        .thenComparing(t -> t.location().toString()))
                .toList();
    }

    /** The next choice on a click: no tag (any components) -> the first tag -> ... -> the last one -> no tag again. */
    public static ItemStack cycle(ItemStack ghost) {
        List<TagKey<Item>> tags = tagsOf(ghost);
        if (tags.isEmpty()) {
            return withTag(ghost, null);
        }
        TagKey<Item> cur = tag(ghost);
        int i = cur == null ? -1 : tags.indexOf(cur);
        return withTag(ghost, i + 1 < tags.size() ? tags.get(i + 1) : null);
    }

    public static int tagSize(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.get(tag).map(HolderSet.Named::size).orElse(0);
    }

    /** The network item types that can stand for the ghost, the exact one first. */
    public static List<ItemKey> candidates(ItemStack ghost, Collection<ItemKey> keys, boolean fuzzy) {
        ItemKey exact = ItemKey.of(plain(ghost));
        List<ItemKey> out = new ArrayList<>();
        if (keys.contains(exact)) {
            out.add(exact);
        }
        if (fuzzy) {
            for (ItemKey k : keys) {
                if (!k.equals(exact) && !Ingredients.isFluid(k) && matches(ghost, k.stack(), true)) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /** Tooltip line for a fuzzy ghost. */
    public static Component describe(ItemStack ghost) {
        TagKey<Item> t = tag(ghost);
        Component what = t == null ? Component.translatable("tooltip.golem_logix.fuzzy.any_components")
                : Component.translatable("tooltip.golem_logix.fuzzy.tag", "#" + t.location(), tagSize(t));
        return Component.translatable("tooltip.golem_logix.fuzzy", what).withStyle(ChatFormatting.AQUA);
    }

    // ---------------------------------------------------------------- patterns

    /** The fuzziness string of a processing input ghost: its tag, or "the same item with any components" (fluids: exact). */
    public static String specOf(ItemStack ghost) {
        if (ghost.isEmpty() || FluidGhostItem.is(ghost)) {
            return EXACT;
        }
        TagKey<Item> t = tag(ghost);
        return t == null ? ANY_COMPONENTS : "#" + t.location();
    }

    /** The tag of a "#ns:path" fuzziness string, or null. */
    @Nullable
    public static TagKey<Item> tagOfSpec(String spec) {
        if (!spec.startsWith("#")) {
            return null;
        }
        Identifier id = Identifier.tryParse(spec.substring(1));
        return id == null ? null : TagKey.create(Registries.ITEM, id);
    }

    /** What may stand for a pattern input: null = exactly the encoded item. */
    @Nullable
    public static Predicate<ItemStack> matcher(String spec, ItemStack encoded, @Nullable Ingredient recipeChoice) {
        if (spec.isEmpty() || encoded.isEmpty() || FluidGhostItem.is(encoded)) {
            return null;
        }
        if (spec.equals(RECIPE)) {
            return recipeChoice == null || recipeChoice.isEmpty() ? null : recipeChoice;
        }
        TagKey<Item> t = tagOfSpec(spec);
        Item item = encoded.getItem();
        return t != null ? s -> s.is(t) : s -> s.is(item);
    }

    /** Per recipe map (a reload makes a new one): grid (item keys, "-" for an empty cell) -> what each cell accepts. */
    private static final Map<RecipeMap, Map<List<Object>, List<Optional<Ingredient>>>> SUBSTITUTES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * For a crafting table grid (9 cells): in every cell, what the recipe accepts there (nothing where the cell is empty or
     * the recipe is of a special kind: then only the encoded item). Like AE2's substitutions: the recipe still works with them.
     */
    public static List<Optional<Ingredient>> craftingSubstitutes(Level level, List<ItemStack> grid) {
        Map<List<Object>, List<Optional<Ingredient>>> cache = SUBSTITUTES.computeIfAbsent(Recipes.map(level),
                m -> Collections.synchronizedMap(new HashMap<>()));
        List<ItemStack> nine = new ArrayList<>();
        List<Object> key = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
            nine.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
            key.add(s.isEmpty() ? "-" : ItemKey.of(s));
        }
        List<Optional<Ingredient>> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<Optional<Ingredient>> out = new ArrayList<>(Collections.nCopies(9, Optional.empty()));
        CraftingInput.Positioned pos = CraftingInput.ofPositioned(3, 3, nine);
        CraftingInput in = pos.input();
        Optional<RecipeHolder<CraftingRecipe>> found = Recipes.find(level, RecipeType.CRAFTING, in);
        if (found.isPresent()) {
            CraftingRecipe recipe = found.get().value();
            if (recipe instanceof ShapedRecipe shaped && in.width() == shaped.getWidth() && in.height() == shaped.getHeight()) {
                List<Optional<Ingredient>> ings = shaped.getIngredients();
                int w = shaped.getWidth(), h = shaped.getHeight();
                boolean mirrored = !fits(ings, w, h, in, false);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int cell = (pos.top() + y) * 3 + pos.left() + x;
                        Optional<Ingredient> ing = ings.get((mirrored ? w - 1 - x : x) + y * w);
                        if (!nine.get(cell).isEmpty() && ing.isPresent()) {
                            out.set(cell, ing);
                        }
                    }
                }
            } else if (recipe instanceof ShapelessRecipe) {
                List<Ingredient> pool = new ArrayList<>(recipe.placementInfo().ingredients());
                for (int cell = 0; cell < 9; cell++) {
                    ItemStack s = nine.get(cell);
                    if (s.isEmpty()) {
                        continue;
                    }
                    for (int k = 0; k < pool.size(); k++) {
                        if (pool.get(k).test(s)) {
                            out.set(cell, Optional.of(pool.remove(k)));
                            break;
                        }
                    }
                }
            }
        }
        List<Optional<Ingredient>> result = List.copyOf(out);
        cache.put(key, result);
        return result;
    }

    private static boolean fits(List<Optional<Ingredient>> ings, int w, int h, CraftingInput in, boolean mirrored) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!Ingredient.testOptionalIngredient(ings.get((mirrored ? w - 1 - x : x) + y * w), in.getItem(x, y))) {
                    return false;
                }
            }
        }
        return true;
    }
}
