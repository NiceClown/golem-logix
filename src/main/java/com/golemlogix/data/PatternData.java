package com.golemlogix.data;

import com.golemlogix.system.ItemKey;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An encoded recipe on a copper punch card.
 *
 * @param crafting true = a 3x3 crafting table recipe (inputs always has 9 positions), false = processing in a machine
 */
public record PatternData(boolean crafting, List<ItemStack> inputs, List<ItemStack> outputs) {
    public static final int GRID = 9;

    public static final Codec<PatternData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("crafting").forGetter(PatternData::crafting),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(PatternData::inputs),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("outputs").forGetter(PatternData::outputs)
    ).apply(i, PatternData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PatternData::crafting,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, PatternData::inputs,
            ItemStack.OPTIONAL_LIST_STREAM_CODEC, PatternData::outputs,
            PatternData::new);

    public PatternData {
        inputs = copyList(inputs);
        outputs = copyList(outputs);
    }

    private static List<ItemStack> copyList(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (ItemStack s : in) {
            out.add(s.copy());
        }
        return List.copyOf(out);
    }

    /** Total ingredients (without empty slots) in order of appearance. */
    public Map<ItemKey, Integer> ingredientTotals() {
        return totals(inputs);
    }

    public Map<ItemKey, Integer> outputTotals() {
        return totals(outputs);
    }

    private static Map<ItemKey, Integer> totals(List<ItemStack> stacks) {
        Map<ItemKey, Integer> map = new LinkedHashMap<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                map.merge(ItemKey.of(s), s.getCount(), Integer::sum);
            }
        }
        return map;
    }

    /** How many of the given item the recipe gives per execution. */
    public int outputCount(ItemKey key) {
        int n = 0;
        for (ItemStack s : outputs) {
            if (!s.isEmpty() && key.matches(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    public boolean isValid() {
        boolean anyIn = false;
        for (ItemStack s : inputs) {
            if (!s.isEmpty()) {
                anyIn = true;
                break;
            }
        }
        boolean anyOut = false;
        for (ItemStack s : outputs) {
            if (!s.isEmpty()) {
                anyOut = true;
                break;
            }
        }
        return anyIn && anyOut;
    }

    public ItemStack primaryOutput() {
        for (ItemStack s : outputs) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PatternData p && crafting == p.crafting
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
        return h;
    }
}
