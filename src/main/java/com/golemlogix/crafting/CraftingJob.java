package com.golemlogix.crafting;

import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.data.PatternData;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A running autocraft job. It lives inside a Cryo-Calculator:
 * keeps its own inventory (reserved network items and intermediate products)
 * and runs steps as ingredients become ready.
 */
public class CraftingJob {
    public static final class StepState {
        public final PatternData pattern;
        public long remaining;

        public StepState(PatternData pattern, long remaining) {
            this.pattern = pattern;
            this.remaining = remaining;
        }
    }

    /** A portion unloaded into a machine by a Provider: what we still expect from it (for the wait-for-result mode). */
    public static final class InFlight {
        public final BlockPos provider;
        public final Map<ItemKey, Long> left = new LinkedHashMap<>();

        InFlight(BlockPos provider) {
            this.provider = provider.immutable();
        }
    }

    public enum Status {
        RUNNING,
        WAITING_MACHINE,
        NO_PROVIDER,
        NO_SOURCE,
        INVALID_PATTERN,
        DELIVERING,
        DONE,
        PAUSED,
        /** No gateway with the pattern has a machine beside it (on the side it feeds). */
        NO_MACHINE
    }

    private final Map<ItemKey, Long> targets = new LinkedHashMap<>();
    private final List<StepState> steps = new ArrayList<>();
    private final Map<ItemKey, Long> inventory = new LinkedHashMap<>();
    private final Map<ItemKey, Long> awaiting = new LinkedHashMap<>();
    private final List<InFlight> inFlight = new ArrayList<>();
    private long totalExecutions;
    private long doneExecutions;
    private Status status = Status.RUNNING;
    /** Who ordered it (gets the "ready" notification); null for an order from a machine. */
    @Nullable
    private UUID requester;
    /** The export bus with an Autocraft Module that ordered this job (so it does not order again). */
    @Nullable
    private BlockPos requesterBus;
    /** What is being made right now, for the mini model above the calculator. */
    @Nullable
    private ItemKey current;

    public CraftingJob(CraftingPlan plan, Map<ItemKey, Long> reserved) {
        this.targets.putAll(plan.targets());
        for (CraftingPlan.Step s : plan.steps()) {
            steps.add(new StepState(s.pattern(), s.times()));
            totalExecutions += s.times();
        }
        inventory.putAll(reserved);
    }

    private CraftingJob() {
    }

    public CraftingJob requestedBy(@Nullable UUID player, @Nullable BlockPos bus) {
        this.requester = player;
        this.requesterBus = bus == null ? null : bus.immutable();
        return this;
    }

    /** The main ordered item. */
    public ItemKey target() {
        return targets.keySet().iterator().next();
    }

    public long amount() {
        return targets.values().iterator().next();
    }

    public Map<ItemKey, Long> targets() {
        return targets;
    }

    @Nullable
    public UUID requester() {
        return requester;
    }

    @Nullable
    public BlockPos requesterBus() {
        return requesterBus;
    }

    /** What is being made now (the output of the last executed step), otherwise the main order. */
    public ItemKey current() {
        return current != null ? current : target();
    }

    public long totalExecutions() {
        return totalExecutions;
    }

    public long doneExecutions() {
        return doneExecutions;
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Map<ItemKey, Long> inventory() {
        return inventory;
    }

    public List<StepState> steps() {
        return steps;
    }

    public Map<ItemKey, Long> awaiting() {
        return awaiting;
    }

    public boolean isDone() {
        return status == Status.DONE;
    }

    /** How many of this item the job is still waiting for from machines. */
    public long wants(ItemKey key) {
        return awaiting.getOrDefault(key, 0L);
    }

    public long awaitingTotal() {
        long n = 0;
        for (long v : awaiting.values()) {
            n += v;
        }
        return n;
    }

    /** Whether there are portions unloaded by this Provider whose result has not come back yet. */
    public boolean hasInFlight(BlockPos provider) {
        for (InFlight f : inFlight) {
            if (f.provider.equals(provider)) {
                return true;
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- absorb

    /** Takes from the incoming stack what the job is waiting for. Returns the rest. */
    public ItemStack absorb(ItemStack stack) {
        if (awaiting.isEmpty() || stack.isEmpty()) {
            return stack;
        }
        ItemKey key = ItemKey.of(stack);
        long want = awaiting.getOrDefault(key, 0L);
        if (want <= 0) {
            return stack;
        }
        int take = (int) Math.min(want, stack.getCount());
        awaiting.merge(key, (long) -take, Long::sum);
        if (awaiting.get(key) <= 0) {
            awaiting.remove(key);
        }
        inventory.merge(key, (long) take, Long::sum);
        settleInFlight(key, take);
        return stack.copyWithCount(stack.getCount() - take);
    }

    /** A received result closes portions in order (first unloaded, first received). */
    private void settleInFlight(ItemKey key, long amount) {
        Iterator<InFlight> it = inFlight.iterator();
        while (it.hasNext() && amount > 0) {
            InFlight f = it.next();
            long need = f.left.getOrDefault(key, 0L);
            if (need <= 0) {
                continue;
            }
            long take = Math.min(need, amount);
            amount -= take;
            if (need - take <= 0) {
                f.left.remove(key);
            } else {
                f.left.put(key, need - take);
            }
            if (f.left.isEmpty()) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------- tick

    /**
     * One work cycle. workers = how many pattern executions can be done per cycle.
     */
    public void tick(GolemNetwork net, int workers) {
        if (status == Status.DONE) {
            return;
        }
        Level level = net.level();

        Status blocked = null;
        int budget = workers;

        for (StepState s : steps) {
            if (s.remaining <= 0) {
                continue;
            }
            while (budget > 0 && s.remaining > 0) {
                // the very items of this execution: the encoded ones, or for a fuzzy pattern what may stand for them
                Taken taken = take(s.pattern, level);
                if (taken == null) {
                    break;
                }
                if (s.pattern.crafting()) {
                    List<ItemStack> result = craftResult(level, taken.stacks());
                    if (result == null) {
                        blocked = Status.INVALID_PATTERN;
                        break;
                    }
                    if (!net.hasSource(GolemConfig.CRAFT_STEP_SOURCE.get())) {
                        blocked = Status.NO_SOURCE;
                        break;
                    }
                    net.consumeSource(GolemConfig.CRAFT_STEP_SOURCE.get());
                    consume(taken);
                    for (ItemStack r : result) {
                        add(inventory, r);
                    }
                } else {
                    // every gateway with this recipe, the one holding this very pattern first: the portion goes to the first
                    // whose machine takes it (the same plate may sit on a gateway that has no machine beside it)
                    List<PatternProviderBlockEntity> candidates = net.providersFor(s.pattern);
                    if (candidates.isEmpty()) {
                        blocked = Status.NO_PROVIDER;
                        break;
                    }
                    if (!net.hasSource(GolemConfig.CRAFT_STEP_SOURCE.get())) {
                        blocked = Status.NO_SOURCE;
                        break;
                    }
                    PatternProviderBlockEntity provider = null;
                    List<ItemStack> pushed = null;
                    Status why = Status.NO_MACHINE;
                    for (PatternProviderBlockEntity p : candidates) {
                        // wait mode: until the result of the previous portion has come back, no new one is loaded
                        if (p.isBlocking() && net.providerBusy(p.getBlockPos())) {
                            why = Status.WAITING_MACHINE;
                            continue;
                        }
                        if (!p.redstoneAllows(false)) {
                            if (why == Status.NO_MACHINE) {
                                why = Status.PAUSED;
                            }
                            continue;
                        }
                        if (p.hasMachine()) {
                            why = Status.WAITING_MACHINE;
                        }
                        pushed = p.tryPush(s.pattern, taken.stacks());
                        if (pushed != null) {
                            provider = p;
                            break;
                        }
                    }
                    if (provider == null) {
                        blocked = why;
                        break;
                    }
                    provider.redstoneAllows(true);
                    net.consumeSource(GolemConfig.CRAFT_STEP_SOURCE.get());
                    consume(taken);
                    // what the machine did not accept goes back into the job's inventory
                    for (ItemStack back : pushed) {
                        add(inventory, back);
                    }
                    InFlight f = new InFlight(provider.getBlockPos());
                    for (Map.Entry<ItemKey, Integer> out : s.pattern.outputTotals().entrySet()) {
                        awaiting.merge(out.getKey(), (long) out.getValue(), Long::sum);
                        f.left.merge(out.getKey(), (long) out.getValue(), Long::sum);
                    }
                    if (!f.left.isEmpty()) {
                        inFlight.add(f);
                    }
                }
                ItemStack primary = s.pattern.primaryOutput();
                if (!primary.isEmpty()) {
                    current = ItemKey.of(primary);
                }
                s.remaining--;
                doneExecutions++;
                budget--;
            }
            if (budget <= 0) {
                break;
            }
        }

        boolean allStepsIssued = true;
        for (StepState s : steps) {
            if (s.remaining > 0) {
                allStepsIssued = false;
                break;
            }
        }

        if (allStepsIssued && awaiting.isEmpty()) {
            deliver(net);
        } else if (blocked != null) {
            status = blocked;
        } else if (!awaiting.isEmpty() && budget > 0) {
            status = Status.WAITING_MACHINE;
        } else {
            status = Status.RUNNING;
        }
    }

    private void deliver(GolemNetwork net) {
        current = null;
        boolean left = false;
        List<ItemKey> keys = new ArrayList<>(inventory.keySet());
        for (ItemKey key : keys) {
            long n = net.giveBack(key, inventory.get(key));
            // leftover Liquid Cryo-Source cannot be stored: it goes back into the aether
            if (n <= 0 || isCryo(key)) {
                inventory.remove(key);
            } else {
                inventory.put(key, n);
                left = true;
            }
        }
        if (left) {
            status = Status.DELIVERING;
        } else {
            status = Status.DONE;
        }
    }

    /** Returns all job contents to the network; the rest that did not fit into storage comes back as stacks. */
    public List<ItemStack> refundAll(@Nullable GolemNetwork net) {
        List<ItemStack> overflow = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : inventory.entrySet()) {
            long n = net != null && net.isOnline() ? net.giveBack(e.getKey(), e.getValue()) : e.getValue();
            // fluids that do not fit cannot be dropped as items: they are lost
            if (com.golemlogix.system.Ingredients.isFluid(e.getKey())) {
                continue;
            }
            while (n > 0) {
                int chunk = (int) Math.min(n, Math.max(1, e.getKey().stack().getMaxStackSize()));
                overflow.add(e.getKey().toStack(chunk));
                n -= chunk;
            }
        }
        inventory.clear();
        awaiting.clear();
        inFlight.clear();
        return overflow;
    }

    // ----------------------------------------------------------------- helpers

    /**
     * The items one execution takes from the job's inventory: per input the encoded item, and for a fuzzy input also what may stand
     * for it (the encoded one first). stacks: what goes into the machine or the crafting grid (for a crafting pattern one per cell,
     * empty cells included); used: how much of each kind. Null if something is missing.
     */
    private record Taken(List<ItemStack> stacks, Map<ItemKey, Long> used) {
    }

    @Nullable
    private Taken take(PatternData pattern, Level level) {
        Map<ItemKey, Long> used = new LinkedHashMap<>();
        List<ItemStack> stacks = new ArrayList<>();
        List<ItemStack> inputs = pattern.inputs();
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack s = inputs.get(i);
            if (s.isEmpty()) {
                if (pattern.crafting()) {
                    stacks.add(ItemStack.EMPTY);
                }
                continue;
            }
            ItemKey key = com.golemlogix.system.Ingredients.key(s);
            long amount = com.golemlogix.system.Ingredients.amount(s);
            if (com.golemlogix.system.Ingredients.isFluid(key)) {
                if (free(key, used) < amount) {
                    return null;
                }
                used.merge(key, amount, Long::sum);
                stacks.add(s);
                continue;
            }
            java.util.function.Predicate<ItemStack> alt = pattern.matcher(i, level);
            List<ItemKey> order = new ArrayList<>();
            order.add(key);
            if (alt != null) {
                for (ItemKey k : inventory.keySet()) {
                    if (!k.equals(key) && !com.golemlogix.system.Ingredients.isFluid(k) && alt.test(k.stack())) {
                        order.add(k);
                    }
                }
            }
            long rest = amount;
            for (ItemKey k : order) {
                long n = Math.min(rest, free(k, used));
                // a crafting grid cell holds one item of one kind
                if (n <= 0 || (pattern.crafting() && n < rest)) {
                    continue;
                }
                used.merge(k, n, Long::sum);
                stacks.add(k.toStack((int) n));
                rest -= n;
                if (rest <= 0) {
                    break;
                }
            }
            if (rest > 0) {
                return null;
            }
        }
        return new Taken(stacks, used);
    }

    private long free(ItemKey key, Map<ItemKey, Long> used) {
        return inventory.getOrDefault(key, 0L) - used.getOrDefault(key, 0L);
    }

    private void consume(Taken taken) {
        for (Map.Entry<ItemKey, Long> in : taken.used().entrySet()) {
            long left = inventory.getOrDefault(in.getKey(), 0L) - in.getValue();
            if (left <= 0) {
                inventory.remove(in.getKey());
            } else {
                inventory.put(in.getKey(), left);
            }
        }
    }

    private static void add(Map<ItemKey, Long> map, ItemStack stack) {
        if (!stack.isEmpty()) {
            map.merge(com.golemlogix.system.Ingredients.key(stack), (long) com.golemlogix.system.Ingredients.amount(stack), Long::sum);
        }
    }

    private static boolean isCryo(ItemKey key) {
        return com.golemlogix.system.Ingredients.isFluid(key)
                && com.golemlogix.system.CryoSource.is(com.golemlogix.system.Ingredients.fluid(key, 1));
    }

    /** Crafting table recipe result + the remaining items (buckets and the like), or null if there is no recipe. */
    @Nullable
    public static List<ItemStack> craftResult(Level level, PatternData pattern) {
        return craftResult(level, pattern.inputs());
    }

    /** The same for the items actually in the grid (a fuzzy pattern's substitutes). */
    @Nullable
    public static List<ItemStack> craftResult(Level level, List<ItemStack> cells) {
        List<ItemStack> grid = new ArrayList<>(cells);
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = com.golemlogix.system.Recipes.find(level, RecipeType.CRAFTING, input);
        if (recipe.isEmpty()) {
            return null;
        }
        List<ItemStack> out = new ArrayList<>();
        ItemStack result = recipe.get().value().assemble(input);
        if (result.isEmpty()) {
            return null;
        }
        out.add(result);
        for (ItemStack rem : recipe.get().value().getRemainingItems(input)) {
            if (!rem.isEmpty()) {
                out.add(rem);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ persistence

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        // the old format (one item) is kept for compatibility, the list of orders is saved next to it
        tag.put("Target", com.golemlogix.system.NbtBridge.saveItem(target().toStack(1), provider));
        tag.putLong("Amount", amount());
        tag.put("Targets", saveMap(targets, provider));
        tag.putLong("Total", totalExecutions);
        tag.putLong("Done", doneExecutions);
        tag.putString("Status", status.name());
        if (requester != null) {
            tag.store("Requester", net.minecraft.core.UUIDUtil.CODEC, requester);
        }
        if (requesterBus != null) {
            tag.putLong("RequesterBus", requesterBus.asLong());
        }

        ListTag stepList = new ListTag();
        for (StepState s : steps) {
            CompoundTag t = new CompoundTag();
            PatternData.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), s.pattern)
                    .result().ifPresent(p -> t.put("Pattern", p));
            t.putLong("Remaining", s.remaining);
            stepList.add(t);
        }
        tag.put("Steps", stepList);
        tag.put("Inventory", saveMap(inventory, provider));
        tag.put("Awaiting", saveMap(awaiting, provider));
        ListTag flights = new ListTag();
        for (InFlight f : inFlight) {
            CompoundTag t = new CompoundTag();
            t.putLong("Provider", f.provider.asLong());
            t.put("Left", saveMap(f.left, provider));
            flights.add(t);
        }
        tag.put("InFlight", flights);
        return tag;
    }

    private static ListTag saveMap(Map<ItemKey, Long> map, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<ItemKey, Long> e : map.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.put("Item", com.golemlogix.system.NbtBridge.saveItem(e.getKey().toStack(1), provider));
            t.putLong("Count", e.getValue());
            list.add(t);
        }
        return list;
    }

    private static void loadMap(ListTag list, Map<ItemKey, Long> map, HolderLookup.Provider provider) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            ItemStack stack = com.golemlogix.system.NbtBridge.loadItem(provider, t.getCompoundOrEmpty("Item")).orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                map.put(ItemKey.of(stack), t.getLongOr("Count", 0L));
            }
        }
    }

    @Nullable
    public static CraftingJob load(CompoundTag tag, HolderLookup.Provider provider) {
        CraftingJob job = new CraftingJob();
        loadMap(tag.getListOrEmpty("Targets"), job.targets, provider);
        if (job.targets.isEmpty()) {
            ItemStack target = com.golemlogix.system.NbtBridge.loadItem(provider, tag.getCompoundOrEmpty("Target")).orElse(ItemStack.EMPTY);
            if (target.isEmpty()) {
                return null;
            }
            job.targets.put(ItemKey.of(target), tag.getLongOr("Amount", 0L));
        }
        job.totalExecutions = tag.getLongOr("Total", 0L);
        job.doneExecutions = tag.getLongOr("Done", 0L);
        try {
            job.status = Status.valueOf(tag.getStringOr("Status", ""));
        } catch (IllegalArgumentException ex) {
            job.status = Status.RUNNING;
        }
        job.requester = tag.read("Requester", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        if (tag.contains("RequesterBus")) {
            job.requesterBus = BlockPos.of(tag.getLongOr("RequesterBus", 0L));
        }
        ListTag stepList = tag.getListOrEmpty("Steps");
        for (int i = 0; i < stepList.size(); i++) {
            CompoundTag t = stepList.getCompoundOrEmpty(i);
            PatternData.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), t.get("Pattern"))
                    .result().ifPresent(p -> job.steps.add(new StepState(p, t.getLongOr("Remaining", 0L))));
        }
        loadMap(tag.getListOrEmpty("Inventory"), job.inventory, provider);
        loadMap(tag.getListOrEmpty("Awaiting"), job.awaiting, provider);
        ListTag flights = tag.getListOrEmpty("InFlight");
        for (int i = 0; i < flights.size(); i++) {
            CompoundTag t = flights.getCompoundOrEmpty(i);
            InFlight f = new InFlight(BlockPos.of(t.getLongOr("Provider", 0L)));
            loadMap(t.getListOrEmpty("Left"), f.left, provider);
            if (!f.left.isEmpty()) {
                job.inFlight.add(f);
            }
        }
        return job;
    }
}
