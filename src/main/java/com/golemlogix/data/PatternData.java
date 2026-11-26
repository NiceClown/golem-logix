package com.golemlogix.data;

import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An encoded recipe on a copper punch card.
 *
 * @param crafting true = a 3x3 crafting table recipe (inputs always has 9 positions), false = processing in a machine
 * @param fuzzy    empty = every input exactly as encoded; otherwise one entry per input (see {@link Fuzzy}): "" exactly, "~" the
 *                 same item with any components, "#ns:path" any item of the tag, "@" whatever the crafting recipe accepts there
 */
public record PatternData(boolean crafting, List<ItemStack> inputs, List<ItemStack> outputs, List<String> fuzzy) {
    public static final int GRID = 9;

    public static final Codec<PatternData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("crafting").forGetter(PatternData::crafting),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(PatternData::inputs),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("outputs").forGetter(PatternData::outputs),
            // patterns written before fuzzy mode have no such field: exact
            Codec.STRING.listOf().optionalFieldOf("fuzzy", List.of()).forGetter(PatternData::fuzzy)
    ).apply(i, PatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PatternData::crafting,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, PatternData::inputs,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, PatternData::outputs,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PatternData::fuzzy,
            PatternData::new);

    public PatternData {
        inputs = copyList(inputs);
        outputs = copyList(outputs);
        if (!hasAnyFuzzy(fuzzy)) {
            fuzzy = List.of();
        } else {
            List<String> f = new ArrayList<>(inputs.size());
            int n = Math.min(fuzzy.size(), inputs.size());
            for (int i = 0; i < n; i++) {
                f.add(fuzzy.get(i));
            }
            while (f.size() < inputs.size()) {
                f.add(Fuzzy.EXACT);
            }
            fuzzy = List.copyOf(f);
        }
    }

    /** An exact pattern. */
    public PatternData(boolean crafting, List<ItemStack> inputs, List<ItemStack> outputs) {
        this(crafting, inputs, outputs, List.of());
    }

    private static boolean hasAnyFuzzy(List<String> fuzzy) {
        for (String s : fuzzy) {
            if (!s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> copyList(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (ItemStack s : in) {
            out.add(s.copy());
        }
        return List.copyOf(out);
    }

    private static boolean hasNonEmpty(List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Total ingredients (without empty slots) in order of appearance. */
    public Map<ItemKey, Integer> ingredientTotals() {
        return totals(inputs);
    }

    public Map<ItemKey, Integer> outputTotals() {
        return totals(outputs);
    }

    /** Items count by the stack, fluids (fluid ghosts) in mB under a fluid key. */
    private static Map<ItemKey, Integer> totals(List<ItemStack> stacks) {
        Map<ItemKey, Integer> map = new LinkedHashMap<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                map.merge(Ingredients.key(s), Ingredients.amount(s), Integer::sum);
            }
        }
        return map;
    }

    /** Whether the pattern feeds any fluid into its machine. */
    public boolean hasFluidInputs() {
        for (ItemStack s : inputs) {
            if (FluidGhostItem.is(s)) {
                return true;
            }
        }
        return false;
    }

    /** How many of the given item the recipe gives per execution (a fluid in mB). */
    public int outputCount(ItemKey key) {
        int n = 0;
        for (ItemStack s : outputs) {
            if (!s.isEmpty() && Ingredients.key(s).equals(key)) {
                n += Ingredients.amount(s);
            }
        }
        return n;
    }

    public boolean isValid() {
        return hasNonEmpty(inputs) && hasNonEmpty(outputs);
    }

    public ItemStack primaryOutput() {
        for (ItemStack s : outputs) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- fuzzy mode

    public boolean isFuzzy() {
        return !fuzzy.isEmpty();
    }

    /** The fuzziness of input i ("" = exactly). */
    public String spec(int i) {
        return i < fuzzy.size() ? fuzzy.get(i) : Fuzzy.EXACT;
    }

    /** What may stand for input i, or null if only the encoded item will do (the recipe's choice needs the level's recipes). */
    @Nullable
    public Predicate<ItemStack> matcher(int i, @Nullable Level level) {
        if (i < 0 || i >= inputs.size()) {
            return null;
        }
        return resolveMatcher(i, inputs.get(i), spec(i), level, null);
    }

    @Nullable
    private Predicate<ItemStack> resolveMatcher(int i, ItemStack encoded, String spec, @Nullable Level level,
                                                @Nullable List<Optional<Ingredient>> cachedSubstitutes) {
        if (spec.isEmpty()) {
            return null;
        }
        Ingredient recipeChoice = null;
        if (spec.equals(Fuzzy.RECIPE)) {
            if (level == null || !crafting || i >= GRID) {
                return null;
            }
            List<Optional<Ingredient>> subs = cachedSubstitutes != null ? cachedSubstitutes : Fuzzy.craftingSubstitutes(level, inputs);
            recipeChoice = subs.get(i).orElse(null);
        }
        return Fuzzy.matcher(spec, encoded, recipeChoice);
    }

    /** One kind of input of an execution: the encoded item, how many (mB for fluids), and what else may stand for it (null = nothing). */
    public record Need(ItemKey key, int amount, @Nullable Predicate<ItemStack> alternatives) {
    }

    private record NeedKey(ItemKey key, String spec, int uniqueSlot) {
    }

    /** The inputs of one execution, identical ones (same item, same fuzziness) summed, in order of appearance. */
    public List<Need> needs(@Nullable Level level) {
        Map<NeedKey, Need> map = new LinkedHashMap<>();
        List<Optional<Ingredient>> substitutes = null;
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack s = inputs.get(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemKey key = Ingredients.key(s);
            int amount = Ingredients.amount(s);
            String spec = spec(i);
            boolean isRecipe = spec.equals(Fuzzy.RECIPE);
            if (isRecipe && substitutes == null && level != null && crafting && i < GRID) {
                substitutes = Fuzzy.craftingSubstitutes(level, inputs);
            }
            Predicate<ItemStack> alt = resolveMatcher(i, s, spec, level, substitutes);
            // a recipe's choice differs per cell, so cells with it are not merged
            NeedKey id = new NeedKey(key, spec, isRecipe ? i : -1);
            Need old = map.get(id);
            map.put(id, old != null ? new Need(key, old.amount() + amount, old.alternatives()) : new Need(key, amount, alt));
        }
        return new ArrayList<>(map.values());
    }

    /** The same recipe whatever the inputs' fuzziness: the same kind, the same inputs and outputs as encoded. */
    public boolean sameRecipe(PatternData p) {
        return crafting == p.crafting && ItemStack.listMatches(inputs, p.inputs) && ItemStack.listMatches(outputs, p.outputs);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PatternData p && crafting == p.crafting && fuzzy.equals(p.fuzzy)
                && ItemStack.listMatches(inputs, p.inputs) && ItemStack.listMatches(outputs, p.outputs);
    }

    @Override
    public int hashCode() {
        int h = crafting ? 1 : 0;
        for (ItemStack s : inputs) {
            h = h * 31 + ItemStack.hashItemAndComponents(s) + s.getCount();
        }
        for (ItemStack s : outputs) {
            h = h * 37 + ItemStack.hashItemAndComponents(s) + s.getCount();
        }
        h = h * 41 + fuzzy.hashCode();
        return h;
    }
}
