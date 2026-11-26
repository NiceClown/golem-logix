package com.golemlogix.crafting;

import com.golemlogix.data.PatternData;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
        int id;
        /** Every node this one depends on (direct or transitive): used so a step never uses a by-product from a step that waits on it. */
        final BitSet deps = new BitSet();
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

    /** A by-product or leftover batch left by one step: only steps that producer does NOT depend on may use it. */
    private static final class Byproduct {
        final Node producer;
        long amount;

        Byproduct(Node producer, long amount) {
            this.producer = producer;
            this.amount = amount;
        }
    }

    private final GolemNetwork net;
    private final Map<ItemKey, Long> stock = new HashMap<>();
    private final Map<ItemKey, List<Byproduct>> surplus = new HashMap<>();
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
        // Fluids count in mB under fluid keys; Liquid Cryo-Source is what the network's Source can make
        for (FluidStack f : net.listFluids()) {
            stock.merge(Ingredients.fluidKey(f), (long) f.getAmount(), Long::sum);
        }
        long cryo = net.cryoSourceAvailable();
        if (cryo > 0) {
            stock.put(Ingredients.fluidKey(CryoSource.stack(1)), cryo);
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
        // The last visited comes first when settling: visit backwards so the first target settles first
        for (int i = wanted.size() - 1; i >= 0; i--) {
            p.visit(wanted.get(i).getKey(), 0).ordered += wanted.get(i).getValue();
        }
        p.propagateDependencies();
        for (int i = p.postOrder.size() - 1; i >= 0; i--) {
            p.settle(p.postOrder.get(i));
        }
        return new CraftingPlan(new LinkedHashMap<>(targets), p.fromStorage, p.buildSteps(), p.missing);
    }

    /** Computes transitive dependencies for every node (iterated to fixed point so cycles of any length propagate). */
    private void propagateDependencies() {
        for (int pass = 0; pass < postOrder.size(); pass++) {
            boolean changed = false;
            for (Node n : postOrder) {
                int before = n.deps.cardinality();
                for (Node child : n.sources) {
                    n.deps.set(child.id);
                    n.deps.or(child.deps);
                }
                if (n.deps.cardinality() != before) {
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    /** Emits steps in dependency order: what later steps need comes first. */
    private List<CraftingPlan.Step> buildSteps() {
        LinkedHashMap<PatternData, Long> steps = new LinkedHashMap<>();
        for (Node n : postOrder) {
            if (n.crafts > 0) {
                steps.merge(n.pattern, n.crafts, Long::sum);
            }
        }
        List<CraftingPlan.Step> list = new ArrayList<>(steps.size());
        for (Map.Entry<PatternData, Long> e : steps.entrySet()) {
            list.add(new CraftingPlan.Step(e.getKey(), e.getValue()));
        }
        return list;
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
            // Backwards: of two ingredients the first settles first and may leave by-products for the second
            for (int i = sources.length - 1; i >= 0; i--) {
                sources[i] = visit(craftedFor(n.needs.get(i)), depth + 1);
            }
            n.sources = List.of(sources);
            onPath.remove(key);
        }
        n.id = postOrder.size();
        postOrder.add(n);
        return n;
    }

    /** What gets crafted for an input: the encoded item or, when a fuzzy input has no pattern for it, another one that will do. */
    private ItemKey craftedFor(PatternData.Need in) {
        Predicate<ItemStack> alt = in.alternatives();
        if (alt != null && net.patternIndex().get(in.key()) == null) {
            for (ItemKey k : net.patternIndex().keySet()) {
                if (!k.equals(in.key()) && !Ingredients.isFluid(k) && alt.test(k.stack()) && !onPath.contains(k)) {
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
        long takenBefore = takeSurplus(n, n.key, need);
        need -= takenBefore;
        long fromStockBefore = takeStock(n.key, need);
        takenBefore += fromStockBefore;
        need -= fromStockBefore;
        need = saturatedAdd(need, n.ordered);
        if (need <= 0) {
            return;
        }
        if (n.pattern == null) {
            missing.merge(n.key, need, CraftingPlanner::saturatedAdd);
            return;
        }
        Map<ItemKey, Long> exactIn = new HashMap<>();
        long selfIn = collectExactInputs(n, exactIn);
        long crafts = computeCrafts(n, need, selfIn);
        if (crafts < 0) {
            return;
        }
        executions += crafts;
        n.crafts = crafts;

        Map<ItemKey, Long> upfrontNeed = planOutputsAndCatalysts(n, need, takenBefore, crafts, exactIn);
        requestIngredients(n, crafts, upfrontNeed);
    }

    /** Sums exact (non-fuzzy) input quantities into exactIn and returns how much of n.key the pattern consumes itself. */
    private static long collectExactInputs(Node n, Map<ItemKey, Long> exactIn) {
        long selfIn = 0;
        for (PatternData.Need in : n.needs) {
            if (in.alternatives() == null) {
                exactIn.merge(in.key(), (long) in.amount(), Long::sum);
                if (in.key().equals(n.key)) {
                    selfIn += in.amount();
                }
            }
        }
        return selfIn;
    }

    /** Returns how many executions of n.pattern produce at least need net output, or -1 (recording missing) if impossible/overflowing. */
    private long computeCrafts(Node n, long need, long selfIn) {
        int per = Math.max(1, n.pattern.outputCount(n.key));
        long denom;
        if (selfIn > 0) {
            if (selfIn >= per) {
                missing.merge(n.key, need, CraftingPlanner::saturatedAdd);
                return -1;
            }
            denom = per - selfIn;
        } else {
            denom = per;
        }
        // Overflow-safe ceiling division for any positive need up to Long.MAX_VALUE
        long crafts = (need - 1) / denom + 1;
        if (crafts > MAX_EXECUTIONS - executions) {
            missing.merge(n.key, need, CraftingPlanner::saturatedAdd);
            return -1;
        }
        return crafts;
    }

    /**
     * Registers leftovers and by-products into surplus for downstream steps, and computes upfront seed quantities for
     * recycled inputs / catalysts (items appearing in both inputs and outputs of the same pattern).
     */
    private Map<ItemKey, Long> planOutputsAndCatalysts(Node n, long need, long takenBefore, long crafts,
                                                       Map<ItemKey, Long> exactIn) {
        Map<ItemKey, Long> upfrontNeed = new HashMap<>();
        for (Map.Entry<ItemKey, Integer> out : n.pattern.outputTotals().entrySet()) {
            ItemKey outKey = out.getKey();
            long outPerCraft = out.getValue();
            Long inPerCraftObj = exactIn.get(outKey);
            if (inPerCraftObj != null && inPerCraftObj > 0) {
                planCatalystOutput(n, outKey, inPerCraftObj, outPerCraft, need, takenBefore, crafts, upfrontNeed);
            } else {
                try {
                    long produced = Math.multiplyExact(crafts, outPerCraft);
                    if (outKey.equals(n.key)) {
                        produced -= need;
                    }
                    addSurplus(n, outKey, produced);
                } catch (ArithmeticException tooMuch) {
                    // Overflow leaves no usable surplus count
                }
            }
        }
        return upfrontNeed;
    }

    private void planCatalystOutput(Node n, ItemKey outKey, long a, long b, long need, long takenBefore, long crafts,
                                    Map<ItemKey, Long> upfrontNeed) {
        if (outKey.equals(n.key)) {
            long upfront = Math.max(0L, a - takenBefore);
            upfrontNeed.put(outKey, upfront);
            try {
                long netProduced = Math.multiplyExact(b - a, crafts);
                long leftover = saturatedAdd(upfront, netProduced - need);
                addSurplus(n, outKey, leftover);
            } catch (ArithmeticException tooMuch) {
                // No surplus if netProduced overflows
            }
        } else {
            try {
                long totalIn = Math.multiplyExact(a, crafts);
                long totalOut = Math.multiplyExact(b, crafts);
                long recycled = totalOut - b;
                long upfront = Math.max(a, totalIn - recycled);
                upfrontNeed.put(outKey, upfront);
                long leftover = totalOut >= totalIn
                        ? saturatedAdd(upfront, totalOut - totalIn)
                        : upfront - (totalIn - totalOut);
                addSurplus(n, outKey, leftover);
            } catch (ArithmeticException tooMuch) {
                upfrontNeed.put(outKey, Long.MAX_VALUE);
            }
        }
    }

    /** Passes demand for every ingredient of n to surplus, stock, or child nodes. */
    private void requestIngredients(Node n, long crafts, Map<ItemKey, Long> upfrontNeed) {
        for (int i = 0; i < n.needs.size(); i++) {
            PatternData.Need in = n.needs.get(i);
            long wanted;
            Long up = in.alternatives() == null ? upfrontNeed.get(in.key()) : null;
            if (up != null) {
                upfrontNeed.put(in.key(), 0L);
                wanted = up;
                if (wanted <= 0) {
                    continue;
                }
            } else {
                try {
                    wanted = Math.multiplyExact((long) in.amount(), crafts);
                } catch (ArithmeticException tooMuch) {
                    missing.merge(n.sources.get(i).key, Long.MAX_VALUE, (a, b) -> Long.MAX_VALUE);
                    continue;
                }
            }
            ask(n, in, n.sources.get(i), wanted);
        }
    }

    /**
     * An input of a step. An exact one is the encoded item; a fuzzy one takes the encoded item from leftovers and stock first, then
     * the other items that may stand for it, and crafts the encoded item (or, without a pattern for it, another one that will do).
     */
    private void ask(Node consumer, PatternData.Need in, Node source, long need) {
        long rest = need;
        Predicate<ItemStack> alt = in.alternatives();
        if (alt != null) {
            rest -= takeSurplus(consumer, in.key(), rest);
            rest -= takeStock(in.key(), rest);
            if (rest > 0) {
                for (ItemKey k : surplusAlternatives(consumer, alt, in.key())) {
                    rest -= takeSurplus(consumer, k, rest);
                    if (rest <= 0) {
                        break;
                    }
                }
            }
            if (rest > 0) {
                for (ItemKey k : alternatives(stock, alt, in.key())) {
                    rest -= takeStock(k, rest);
                    if (rest <= 0) {
                        break;
                    }
                }
            }
        }
        if (rest <= 0) {
            return;
        }
        if (!source.settled) {
            source.demand = saturatedAdd(source.demand, rest);
            return;
        }
        // Around a cycle: this item was settled before this consumer spoke, so only what is on hand can serve it
        rest -= takeSurplus(consumer, source.key, rest);
        rest -= takeStock(source.key, rest);
        if (rest > 0) {
            missing.merge(source.key, rest, CraftingPlanner::saturatedAdd);
        }
    }

    private void addSurplus(Node producer, ItemKey key, long amount) {
        if (amount > 0) {
            surplus.computeIfAbsent(key, k -> new ArrayList<>()).add(new Byproduct(producer, amount));
        }
    }

    /** Takes up to want of key from earlier steps' by-products that do not depend on consumer. */
    private long takeSurplus(Node consumer, ItemKey key, long want) {
        if (want <= 0) {
            return 0;
        }
        List<Byproduct> list = surplus.get(key);
        if (list == null) {
            return 0;
        }
        long got = 0;
        for (Byproduct b : list) {
            if (b.amount <= 0 || b.producer == consumer || b.producer.deps.get(consumer.id)) {
                continue;
            }
            long n = Math.min(b.amount, want - got);
            if (n > 0) {
                b.amount -= n;
                got += n;
                // Consumer now depends on b.producer: propagate so later steps cannot form a mutual byproduct deadlock
                recordDependency(consumer, b.producer);
                if (got >= want) {
                    break;
                }
            }
        }
        return got;
    }

    private void recordDependency(Node consumer, Node producer) {
        consumer.deps.set(producer.id);
        consumer.deps.or(producer.deps);
        for (Node u : postOrder) {
            if (u.deps.get(consumer.id)) {
                u.deps.set(producer.id);
                u.deps.or(producer.deps);
            }
        }
    }

    private List<ItemKey> surplusAlternatives(Node consumer, Predicate<ItemStack> alt, ItemKey encoded) {
        Map<ItemKey, Long> available = new HashMap<>();
        for (Map.Entry<ItemKey, List<Byproduct>> e : surplus.entrySet()) {
            ItemKey k = e.getKey();
            if (k.equals(encoded) || Ingredients.isFluid(k) || !alt.test(k.stack())) {
                continue;
            }
            long sum = 0;
            for (Byproduct b : e.getValue()) {
                if (b.amount > 0 && b.producer != consumer && !b.producer.deps.get(consumer.id)) {
                    sum = saturatedAdd(sum, b.amount);
                }
            }
            if (sum > 0) {
                available.put(k, sum);
            }
        }
        List<ItemKey> out = new ArrayList<>(available.keySet());
        out.sort((a, b) -> Long.compare(available.get(b), available.get(a)));
        return out;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }

    /** Takes up to want of key from network stock and records it in fromStorage. Returns how many were taken. */
    private long takeStock(ItemKey key, long want) {
        if (want <= 0) {
            return 0;
        }
        long n = Math.min(stock.getOrDefault(key, 0L), want);
        if (n > 0) {
            stock.merge(key, -n, Long::sum);
            fromStorage.merge(key, n, Long::sum);
        }
        return Math.max(0, n);
    }

    /** The item types (not fluids) in the map that may stand for an input, apart from the encoded one; the ones with most first. */
    private static List<ItemKey> alternatives(Map<ItemKey, Long> in, Predicate<ItemStack> alt, ItemKey encoded) {
        List<ItemKey> out = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : in.entrySet()) {
            ItemKey k = e.getKey();
            if (e.getValue() > 0 && !k.equals(encoded) && !Ingredients.isFluid(k) && alt.test(k.stack())) {
                out.add(k);
            }
        }
        out.sort((a, b) -> Long.compare(in.get(b), in.get(a)));
        return out;
    }
}
