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
 * Planner: lays the recipes the wanted items need out as a list of steps, each with how many times to run it.
 * <p>
 * The recipes form a graph where many paths lead to the same ingredient, so walking every path takes exponentially long as recipes
 * get deeper. Instead every item is settled once: the graph is sorted so that an item comes after all that consume it, then in that
 * order each item adds up what its consumers asked for and passes the demand on to its own ingredients. The time depends on the
 * number of recipes involved, not on how deep they nest or how much is ordered, so a plan of a billion executions is as quick as one
 * of ten. It runs on the server thread: nothing here may grow with the amount.
 */
public final class CraftingPlanner {
    /** Not a limit a job could ever meet: it keeps the arithmetic from overflowing. */
    public static final long MAX_EXECUTIONS = 1L << 50;
    /** Recipes nested deeper than this count as not craftable (it keeps the sort off the end of the stack). */
    private static final int MAX_DEPTH = 1024;

    /** An item in the graph: what is asked of it, and how it is made. */
    private static final class Node {
        final ItemKey key;
        /** Null for an item nothing makes. */
        PatternData pattern;
        List<PatternData.Need> needs = List.of();
        /** Per need, what to craft if it has to be crafted. */
        List<Node> sources = List.of();
        /** Ordered outright: made whatever the stock holds. */
        long ordered;
        /** Asked for by consumers: leftovers and stock come first. */
        long demand;
        long crafts;
        boolean settled;

        Node(ItemKey key) {
            this.key = key;
        }
    }

    private final GolemNetwork net;
    private final Map<ItemKey, Long> stock = new HashMap<>();
    private final Map<ItemKey, Long> surplus = new HashMap<>();
    private final Map<ItemKey, Long> fromStorage = new LinkedHashMap<>();
    private final Map<ItemKey, Long> missing = new LinkedHashMap<>();
    private final Map<ItemKey, Node> nodes = new HashMap<>();
    /** Every item after all that consume it, except along a cycle. */
    private final List<Node> postOrder = new ArrayList<>();
    private final Set<ItemKey> onPath = new HashSet<>();
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
        List<Map.Entry<ItemKey, Long>> wanted = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> t : targets.entrySet()) {
            if (t.getValue() > 0) {
                wanted.add(t);
            }
        }
        // the last visited comes first when settling: backwards, so the first order is settled first
        for (int i = wanted.size() - 1; i >= 0; i--) {
            p.visit(wanted.get(i).getKey(), 0).ordered += wanted.get(i).getValue();
        }
        for (int i = p.postOrder.size() - 1; i >= 0; i--) {
            p.settle(p.postOrder.get(i));
        }
        // steps in dependency order: what the following ones need comes first
        LinkedHashMap<PatternData, Long> steps = new LinkedHashMap<>();
        for (Node n : p.postOrder) {
            if (n.crafts > 0) {
                steps.merge(n.pattern, n.crafts, Long::sum);
            }
        }
        List<CraftingPlan.Step> list = new ArrayList<>();
        for (Map.Entry<PatternData, Long> e : steps.entrySet()) {
            list.add(new CraftingPlan.Step(e.getKey(), e.getValue()));
        }
        return new CraftingPlan(new LinkedHashMap<>(targets), p.fromStorage, list, p.missing);
    }

    /** Depth-first over the recipes: an item is added to the post order after everything it is made from. */
    private Node visit(ItemKey key, int depth) {
        Node known = nodes.get(key);
        if (known != null) {
            return known;
        }
        Node n = new Node(key);
        nodes.put(key, n);
        GolemNetwork.PatternRef ref = net.patternIndex().get(key);
        if (ref != null && depth <= MAX_DEPTH) {
            onPath.add(key);
            n.pattern = ref.data();
            n.needs = ref.data().needs(net.level());
            Node[] sources = new Node[n.needs.size()];
            // backwards: of two ingredients the first is settled first, and may leave by-products for the second
            for (int i = sources.length - 1; i >= 0; i--) {
                sources[i] = visit(craftedFor(n.needs.get(i)), depth + 1);
            }
            n.sources = List.of(sources);
            onPath.remove(key);
        }
        postOrder.add(n);
        return n;
    }

    /** What gets crafted for an input: the encoded item or, when a fuzzy input has no pattern for it, another one that will do. */
    private ItemKey craftedFor(PatternData.Need in) {
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> alt = in.alternatives();
        if (alt != null && net.patternIndex().get(in.key()) == null) {
            for (ItemKey k : net.patternIndex().keySet()) {
                if (!k.equals(in.key()) && !com.golemlogix.system.Ingredients.isFluid(k) && alt.test(k.stack()) && !onPath.contains(k)) {
                    return k;
                }
            }
        }
        return in.key();
    }

    /** All of an item's consumers have spoken: leftovers and stock first, the rest is crafted, and its ingredients are asked for. */
    private void settle(Node n) {
        n.settled = true;
        long need = n.demand;
        need -= take(surplus, n.key, need, false);
        need -= take(stock, n.key, need, true);
        need += n.ordered;
        if (need <= 0) {
            return;
        }
        if (n.pattern == null) {
            missing.merge(n.key, need, Long::sum);
            return;
        }
        int per = Math.max(1, n.pattern.outputCount(n.key));
        long crafts = (need + per - 1) / per;
        if (crafts > MAX_EXECUTIONS - executions) {
            missing.merge(n.key, need, Long::sum);
            return;
        }
        executions += crafts;
        n.crafts = crafts;

        // by-products and leftovers are available to the following consumers
        for (Map.Entry<ItemKey, Integer> out : n.pattern.outputTotals().entrySet()) {
            long produced = crafts * out.getValue();
            if (out.getKey().equals(n.key)) {
                produced -= need;
            }
            if (produced > 0) {
                surplus.merge(out.getKey(), produced, Long::sum);
            }
        }
        for (int i = 0; i < n.needs.size(); i++) {
            PatternData.Need in = n.needs.get(i);
            long wanted;
            try {
                wanted = Math.multiplyExact((long) in.amount(), crafts);
            } catch (ArithmeticException tooMuch) {
                missing.merge(n.sources.get(i).key, Long.MAX_VALUE, (a, b) -> Long.MAX_VALUE);
                continue;
            }
            ask(in, n.sources.get(i), wanted);
        }
    }

    /**
     * An input of a step. An exact one is the encoded item; a fuzzy one takes the encoded item from leftovers and stock first, then
     * the other items that may stand for it, and crafts the encoded item (or, without a pattern for it, another one that will do).
     */
    private void ask(PatternData.Need in, Node source, long need) {
        long rest = need;
        java.util.function.Predicate<net.minecraft.world.item.ItemStack> alt = in.alternatives();
        if (alt != null) {
            rest -= take(surplus, in.key(), rest, false);
            rest -= take(stock, in.key(), rest, true);
            for (ItemKey k : alternatives(surplus, alt, in.key())) {
                rest -= take(surplus, k, rest, false);
            }
            for (ItemKey k : alternatives(stock, alt, in.key())) {
                rest -= take(stock, k, rest, true);
            }
        }
        if (rest <= 0) {
            return;
        }
        if (!source.settled) {
            source.demand = saturatedAdd(source.demand, rest);
            return;
        }
        // around a cycle: this item was settled before this consumer spoke, so only what is on hand can serve it
        rest -= take(surplus, source.key, rest, false);
        rest -= take(stock, source.key, rest, true);
        if (rest > 0) {
            missing.merge(source.key, rest, CraftingPlanner::saturatedAdd);
        }
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    /** Takes up to want of key from leftovers or stock (stock also counts as taken from storage). Returns how many. */
    private long take(Map<ItemKey, Long> from, ItemKey key, long want, boolean storage) {
        if (want <= 0) {
            return 0;
        }
        long n = Math.min(from.getOrDefault(key, 0L), want);
        if (n > 0) {
            from.merge(key, -n, Long::sum);
            if (storage) {
                fromStorage.merge(key, n, Long::sum);
            }
        }
        return Math.max(0, n);
    }

    /** The item types (not fluids) in the map that may stand for an input, apart from the encoded one; the ones with most first. */
    private static List<ItemKey> alternatives(Map<ItemKey, Long> in, java.util.function.Predicate<net.minecraft.world.item.ItemStack> alt,
                                              ItemKey encoded) {
        List<ItemKey> out = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : in.entrySet()) {
            ItemKey k = e.getKey();
            if (e.getValue() > 0 && !k.equals(encoded) && !com.golemlogix.system.Ingredients.isFluid(k) && alt.test(k.stack())) {
                out.add(k);
            }
        }
        out.sort((a, b) -> Long.compare(in.get(b), in.get(a)));
        return out;
    }
}
