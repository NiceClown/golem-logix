package com.golemlogix.crafting;

import com.golemlogix.data.PatternData;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Planner: unfolds the punch card recipe tree for the wanted item
 * and lays it out as a linear list of steps (depth-first, a step is added after its dependencies,
 * topological order).
 */
public final class CraftingPlanner {
    public static final long MAX_EXECUTIONS = 200_000L;
    private static final int MAX_DEPTH = 64;

    private final GolemNetwork net;
    private final Map<ItemKey, Long> stock = new HashMap<>();
    private final Map<ItemKey, Long> surplus = new HashMap<>();
    private final Map<ItemKey, Long> fromStorage = new LinkedHashMap<>();
    private final Map<ItemKey, Long> missing = new LinkedHashMap<>();
    private final LinkedHashMap<PatternData, Long> steps = new LinkedHashMap<>();
    private final Set<ItemKey> visiting = new HashSet<>();
    private long executions = 0;

    private CraftingPlanner(GolemNetwork net) {
        this.net = net;
        for (Map.Entry<ItemKey, Long> e : net.listItems().entrySet()) {
            stock.put(e.getKey(), e.getValue());
        }
        // fluids count in mB under fluid keys; Liquid Cryo-Source is what the network's Source can make
        for (net.neoforged.neoforge.fluids.FluidStack f : net.listFluids()) {
            stock.merge(com.golemlogix.system.Ingredients.fluidKey(f), (long) f.getAmount(), Long::sum);
        }
        long cryo = net.cryoSourceAvailable();
        if (cryo > 0) {
            stock.put(com.golemlogix.system.Ingredients.fluidKey(com.golemlogix.system.CryoSource.stack(1)), cryo);
        }
    }

    public static CraftingPlan plan(GolemNetwork net, ItemKey target, long amount) {
        Map<ItemKey, Long> one = new LinkedHashMap<>();
        one.put(target, amount);
        return plan(net, one);
    }

    /** A plan for several items at once (precrafting what is missing): shared stock and shared leftovers for all of them. */
    public static CraftingPlan plan(GolemNetwork net, Map<ItemKey, Long> targets) {
        CraftingPlanner p = new CraftingPlanner(net);
        for (Map.Entry<ItemKey, Long> t : targets.entrySet()) {
            if (t.getValue() > 0) {
                p.resolve(t.getKey(), t.getValue(), 0, false);
            }
        }
        List<CraftingPlan.Step> list = new ArrayList<>();
        for (Map.Entry<PatternData, Long> e : p.steps.entrySet()) {
            list.add(new CraftingPlan.Step(e.getKey(), e.getValue()));
        }
        return new CraftingPlan(new LinkedHashMap<>(targets), p.fromStorage, list, p.missing);
    }

    /** useStock = false for the requested item: crafting always makes the ordered amount instead of taking it from stock. */
    private void resolve(ItemKey key, long need, int depth, boolean useStock) {
        if (need <= 0) {
            return;
        }

        long fromSurplus = useStock ? Math.min(surplus.getOrDefault(key, 0L), need) : 0;
        if (fromSurplus > 0) {
            surplus.merge(key, -fromSurplus, Long::sum);
            need -= fromSurplus;
        }
        long fromStock = useStock ? Math.min(stock.getOrDefault(key, 0L), need) : 0;
        if (fromStock > 0) {
            stock.merge(key, -fromStock, Long::sum);
            fromStorage.merge(key, fromStock, Long::sum);
            need -= fromStock;
        }
        if (need <= 0) {
            return;
        }

        GolemNetwork.PatternRef ref = net.patternIndex().get(key);
        if (ref == null || visiting.contains(key) || depth > MAX_DEPTH) {
            missing.merge(key, need, Long::sum);
            return;
        }

        PatternData pattern = ref.data();
        int per = Math.max(1, pattern.outputCount(key));
        long crafts = (need + per - 1) / per;
        executions += crafts;
        if (executions > MAX_EXECUTIONS) {
            missing.merge(key, need, Long::sum);
            return;
        }

        // by-products and leftovers are available to the following consumers
        for (Map.Entry<ItemKey, Integer> out : pattern.outputTotals().entrySet()) {
            long produced = crafts * out.getValue();
            if (out.getKey().equals(key)) {
                produced -= need;
            }
            if (produced > 0) {
                surplus.merge(out.getKey(), produced, Long::sum);
            }
        }

        visiting.add(key);
        for (Map.Entry<ItemKey, Integer> in : pattern.ingredientTotals().entrySet()) {
            resolve(in.getKey(), (long) in.getValue() * crafts, depth + 1, true);
        }
        visiting.remove(key);

        steps.merge(pattern, crafts, Long::sum);
    }
}
