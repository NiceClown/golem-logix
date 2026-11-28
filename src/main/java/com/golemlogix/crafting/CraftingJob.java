package com.golemlogix.crafting;

import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.data.PatternData;
import com.golemlogix.system.CraftLoad;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

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

        // Cached crafting-grid lookup per RecipeManager: recipe lookup is the costliest part of a crafting step
        private RecipeManager lastRecipes;
        private List<ItemStack> lastCells;
        private List<ItemStack> lastResult;

        /** The crafting table's result for these cells, or null if there is no such recipe. */
        @Nullable
        public List<ItemStack> craft(Level level, List<ItemStack> cells) {
            RecipeManager recipes = level.getRecipeManager();
            if (lastRecipes == recipes && lastCells != null && ItemStack.listMatches(lastCells, cells)) {
                return lastResult;
            }
            List<ItemStack> result = craftResult(level, cells);
            lastRecipes = result == null ? null : recipes;
            lastCells = result == null ? null : cells;
            lastResult = result;
            return result;
        }
    }

    /** What is unloaded through one Provider and not back yet: what we still expect from it (for the wait-for-result mode). */
    public static final class InFlight {
        public GlobalPos provider;
        public final Map<ItemKey, Long> left = new LinkedHashMap<>();
        boolean legacyDim;

        InFlight(GlobalPos provider) {
            this(provider, false);
        }

        InFlight(GlobalPos provider, boolean legacyDim) {
            this.provider = GlobalPos.of(provider.dimension(), provider.pos().immutable());
            this.legacyDim = legacyDim;
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
    private GlobalPos requesterBus;
    /** True when loaded from a legacy save without dimension info before the owning BE received its Level. */
    private boolean legacyRequesterBusDim;
    /** What is being made right now, for the mini model above the calculator. */
    @Nullable
    private ItemKey current;
    /** Its last cycle ended only because the steps ran out: more of them would have got more done. */
    private boolean starved;

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

    public CraftingJob requestedBy(@Nullable UUID player, @Nullable GlobalPos bus) {
        this.requester = player;
        this.requesterBus = bus == null ? null : GlobalPos.of(bus.dimension(), bus.pos().immutable());
        this.legacyRequesterBusDim = false;
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
    public GlobalPos requesterBus() {
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

    /** Whether an idle processor would get something done by lending this job its steps. */
    public boolean wantsHelp() {
        return starved && status == Status.RUNNING;
    }

    public long remainingExecutions() {
        return totalExecutions - doneExecutions;
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
    public boolean hasInFlight(GlobalPos provider) {
        for (InFlight f : inFlight) {
            if (f.provider.equals(provider)) {
                return true;
            }
        }
        return false;
    }

    /** Binds any dimension-less legacy save entries to the owning calculator's actual dimension once its Level is attached. */
    public void resolveLegacyDimension(ResourceKey<Level> dim) {
        if (legacyRequesterBusDim && requesterBus != null) {
            requesterBus = GlobalPos.of(dim, requesterBus.pos());
            legacyRequesterBusDim = false;
        }
        for (InFlight f : inFlight) {
            if (f.legacyDim) {
                f.provider = GlobalPos.of(dim, f.provider.pos());
                f.legacyDim = false;
            }
        }
    }

    // ----------------------------------------------------------------- absorb

    /** Takes from the incoming stack what the job is waiting for. Returns the rest. */
    public ItemStack absorb(ItemStack stack) {
        if (awaiting.isEmpty() || stack.isEmpty()) {
            return stack;
        }
        int take = absorbAmount(ItemKey.of(stack), stack.getCount());
        return take == 0 ? stack : stack.copyWithCount(stack.getCount() - take);
    }

    /** Takes from an incoming fluid what the job is waiting for (a fluid output of a processing pattern). Returns the mB taken. */
    public int absorbFluid(FluidStack fluid) {
        if (awaiting.isEmpty() || fluid.isEmpty()) {
            return 0;
        }
        return absorbAmount(Ingredients.fluidKey(fluid), fluid.getAmount());
    }

    private int absorbAmount(ItemKey key, int available) {
        long want = awaiting.getOrDefault(key, 0L);
        if (want <= 0) {
            return 0;
        }
        int take = (int) Math.min(want, available);
        long left = want - take;
        if (left <= 0) {
            awaiting.remove(key);
        } else {
            awaiting.put(key, left);
        }
        inventory.merge(key, (long) take, Long::sum);
        settleInFlight(key, take);
        return take;
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
        int stepSource = GolemConfig.CRAFT_STEP_SOURCE.get();

        Status blocked = null;
        int budget = workers;
        long started = System.nanoTime();
        long nanosLeft = CraftLoad.nanosLeft(level);
        boolean outOfTime = false;

        for (StepState s : steps) {
            if (s.remaining <= 0) {
                continue;
            }
            List<PatternProviderBlockEntity> candidates = null;
            while (budget > 0 && s.remaining > 0) {
                // Server tick budget; every cycle still runs at least one step so no job starves
                if (budget < workers && System.nanoTime() - started >= nanosLeft) {
                    outOfTime = true;
                    break;
                }
                // Ingredients for this execution: encoded items, or substitutes for a fuzzy pattern
                Taken taken = take(s.pattern, level);
                if (taken == null) {
                    break;
                }
                Status failure;
                if (s.pattern.crafting()) {
                    failure = runCraftingStep(s, taken, net, level, stepSource);
                } else {
                    if (candidates == null) {
                        candidates = net.providersFor(s.pattern);
                    }
                    failure = runProcessingStep(s, taken, candidates, net, stepSource);
                }
                if (failure != null) {
                    blocked = failure;
                    break;
                }
                ItemStack primary = s.pattern.primaryOutput();
                if (!primary.isEmpty()) {
                    current = ItemKey.of(primary);
                }
                s.remaining--;
                doneExecutions++;
                budget--;
            }
            if (budget <= 0 || outOfTime) {
                break;
            }
        }

        boolean allStepsIssued = allStepsIssued();
        updateStatus(net, allStepsIssued, blocked, budget, workers, outOfTime);
        starved = budget <= 0 && !allStepsIssued;
        CraftLoad.ran(System.nanoTime() - started);
    }

    @Nullable
    private Status runCraftingStep(StepState s, Taken taken, GolemNetwork net, Level level, int stepSource) {
        List<ItemStack> result = s.craft(level, taken.stacks());
        if (result == null) {
            return Status.INVALID_PATTERN;
        }
        if (!net.consumeSource(stepSource)) {
            return Status.NO_SOURCE;
        }
        consume(taken);
        for (ItemStack r : result) {
            add(inventory, r);
        }
        return null;
    }

    @Nullable
    private Status runProcessingStep(StepState s, Taken taken, List<PatternProviderBlockEntity> candidates,
                                     GolemNetwork net, int stepSource) {
        if (candidates.isEmpty()) {
            return Status.NO_PROVIDER;
        }
        if (!net.hasSource(stepSource)) {
            return Status.NO_SOURCE;
        }
        PatternProviderBlockEntity provider = null;
        List<ItemStack> pushed = null;
        Status why = Status.NO_MACHINE;
        for (PatternProviderBlockEntity p : candidates) {
            // Wait mode: until the result of the previous portion returns, no new one is loaded
            if (p.isBlocking() && net.providerBusy(providerKey(p, net))) {
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
            return why;
        }
        provider.redstoneAllows(true);
        net.consumeSource(stepSource);
        consume(taken);
        // What the machine did not accept goes back into the job's inventory
        for (ItemStack back : pushed) {
            add(inventory, back);
        }
        // One record per Provider however many portions are out: a job of millions must not bloat the save
        InFlight f = inFlightOf(providerKey(provider, net));
        for (Map.Entry<ItemKey, Integer> out : s.pattern.outputTotals().entrySet()) {
            awaiting.merge(out.getKey(), (long) out.getValue(), Long::sum);
            f.left.merge(out.getKey(), (long) out.getValue(), Long::sum);
        }
        if (f.left.isEmpty()) {
            inFlight.remove(f);
        }
        return null;
    }

    private boolean allStepsIssued() {
        for (StepState s : steps) {
            if (s.remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void updateStatus(GolemNetwork net, boolean allStepsIssued, @Nullable Status blocked,
                              int budget, int workers, boolean outOfTime) {
        if (allStepsIssued && awaiting.isEmpty()) {
            deliver(net);
        } else if (blocked != null && budget > 0 && !outOfTime) {
            status = blocked;
        } else if (!awaiting.isEmpty() && budget > 0 && !outOfTime) {
            status = Status.WAITING_MACHINE;
        } else if (!allStepsIssued && awaiting.isEmpty() && blocked == null && budget == workers && !outOfTime) {
            status = Status.INVALID_PATTERN;
        } else {
            status = Status.RUNNING;
        }
    }

    private static GlobalPos providerKey(PatternProviderBlockEntity provider, GolemNetwork net) {
        Level lvl = provider.getLevel();
        return GlobalPos.of((lvl != null ? lvl : net.level()).dimension(), provider.getBlockPos());
    }

    private InFlight inFlightOf(GlobalPos provider) {
        for (InFlight f : inFlight) {
            if (f.provider.equals(provider)) {
                return f;
            }
        }
        InFlight f = new InFlight(provider);
        inFlight.add(f);
        return f;
    }

    private void deliver(GolemNetwork net) {
        current = null;
        boolean left = false;
        List<ItemKey> keys = new ArrayList<>(inventory.keySet());
        for (ItemKey key : keys) {
            long n = net.giveBack(key, inventory.get(key));
            // Leftover Liquid Cryo-Source cannot be stored: it returns to the aether
            if (n <= 0 || isCryo(key)) {
                inventory.remove(key);
            } else {
                inventory.put(key, n);
                left = true;
            }
        }
        status = left ? Status.DELIVERING : Status.DONE;
    }

    /** Returns all job contents to the network; the rest that did not fit into storage comes back as stacks. */
    public List<ItemStack> refundAll(@Nullable GolemNetwork net) {
        List<ItemStack> overflow = new ArrayList<>();
        for (Map.Entry<ItemKey, Long> e : inventory.entrySet()) {
            long n = net != null && net.isOnline() ? net.giveBack(e.getKey(), e.getValue()) : e.getValue();
            // Fluids that do not fit cannot be dropped as items
            if (Ingredients.isFluid(e.getKey())) {
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
        List<ItemStack> inputs = pattern.inputs();
        Map<ItemKey, Long> used = new LinkedHashMap<>(inputs.size() * 2);
        List<ItemStack> stacks = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack s = inputs.get(i);
            if (s.isEmpty()) {
                if (pattern.crafting()) {
                    stacks.add(ItemStack.EMPTY);
                }
                continue;
            }
            ItemKey key = Ingredients.key(s);
            long amount = Ingredients.amount(s);
            if (Ingredients.isFluid(key)) {
                if (free(key, used) < amount) {
                    return null;
                }
                used.merge(key, amount, Long::sum);
                stacks.add(s);
                continue;
            }
            Predicate<ItemStack> alt = pattern.matcher(i, level);
            if (alt == null) {
                if (free(key, used) < amount) {
                    return null;
                }
                used.merge(key, amount, Long::sum);
                stacks.add(key.toStack((int) amount));
            } else if (!takeFuzzy(key, amount, alt, pattern.crafting(), used, stacks)) {
                return null;
            }
        }
        return new Taken(stacks, used);
    }

    /** Takes amount of a fuzzy input from the job's inventory (encoded item first, then matching substitutes). */
    private boolean takeFuzzy(ItemKey key, long amount, Predicate<ItemStack> alt, boolean crafting,
                              Map<ItemKey, Long> used, List<ItemStack> stacks) {
        long rest = amount;
        long first = Math.min(rest, free(key, used));
        if (first > 0 && (!crafting || first >= rest)) {
            used.merge(key, first, Long::sum);
            stacks.add(key.toStack((int) first));
            rest -= first;
            if (rest <= 0) {
                return true;
            }
        }
        for (ItemKey k : inventory.keySet()) {
            if (k.equals(key) || Ingredients.isFluid(k) || !alt.test(k.stack())) {
                continue;
            }
            long n = Math.min(rest, free(k, used));
            // A crafting grid cell holds one item of one kind
            if (n <= 0 || (crafting && n < rest)) {
                continue;
            }
            used.merge(k, n, Long::sum);
            stacks.add(k.toStack((int) n));
            rest -= n;
            if (rest <= 0) {
                return true;
            }
        }
        return false;
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
            map.merge(Ingredients.key(stack), (long) Ingredients.amount(stack), Long::sum);
        }
    }

    private static boolean isCryo(ItemKey key) {
        return Ingredients.isFluid(key) && CryoSource.is(Ingredients.fluid(key, 1));
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
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            return null;
        }
        List<ItemStack> out = new ArrayList<>();
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
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
        // Old format (one item) is kept for compatibility; the list of orders is saved next to it
        tag.put("Target", target().toStack(1).save(provider));
        tag.putLong("Amount", amount());
        tag.put("Targets", saveMap(targets, provider));
        tag.putLong("Total", totalExecutions);
        tag.putLong("Done", doneExecutions);
        tag.putString("Status", status.name());
        if (requester != null) {
            tag.putUUID("Requester", requester);
        }
        if (requesterBus != null) {
            tag.putLong("RequesterBus", requesterBus.pos().asLong());
            tag.putString("RequesterBusDim", requesterBus.dimension().location().toString());
        }

        ListTag stepList = new ListTag();
        var ops = provider.createSerializationContext(NbtOps.INSTANCE);
        for (StepState s : steps) {
            CompoundTag t = new CompoundTag();
            PatternData.CODEC.encodeStart(ops, s.pattern)
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
            t.putLong("Provider", f.provider.pos().asLong());
            t.putString("Dim", f.provider.dimension().location().toString());
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
            t.put("Item", e.getKey().toStack(1).save(provider));
            t.putLong("Count", e.getValue());
            list.add(t);
        }
        return list;
    }

    private static void loadMap(ListTag list, Map<ItemKey, Long> map, HolderLookup.Provider provider) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ItemStack stack = ItemStack.parse(provider, t.getCompound("Item")).orElse(ItemStack.EMPTY);
            long count = t.getLong("Count");
            if (!stack.isEmpty() && count > 0) {
                map.merge(ItemKey.of(stack), count, Long::sum);
            }
        }
    }

    @Nullable
    private static ResourceKey<Level> parseDimension(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    @Nullable
    public static CraftingJob load(CompoundTag tag, HolderLookup.Provider provider) {
        return load(tag, provider, null);
    }

    @Nullable
    public static CraftingJob load(CompoundTag tag, HolderLookup.Provider provider, @Nullable ResourceKey<Level> defaultDim) {
        CraftingJob job = new CraftingJob();
        loadMap(tag.getList("Targets", Tag.TAG_COMPOUND), job.targets, provider);
        if (job.targets.isEmpty()) {
            ItemStack target = ItemStack.parse(provider, tag.getCompound("Target")).orElse(ItemStack.EMPTY);
            if (target.isEmpty()) {
                return null;
            }
            job.targets.put(ItemKey.of(target), tag.getLong("Amount"));
        }
        job.totalExecutions = tag.getLong("Total");
        job.doneExecutions = tag.getLong("Done");
        try {
            job.status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException ex) {
            job.status = Status.RUNNING;
        }
        if (tag.hasUUID("Requester")) {
            job.requester = tag.getUUID("Requester");
        }
        if (tag.contains("RequesterBus")) {
            BlockPos busPos = BlockPos.of(tag.getLong("RequesterBus"));
            ResourceKey<Level> busDim = parseDimension(tag, "RequesterBusDim");
            if (busDim != null) {
                job.requesterBus = GlobalPos.of(busDim, busPos);
            } else {
                job.requesterBus = GlobalPos.of(defaultDim != null ? defaultDim : Level.OVERWORLD, busPos);
                job.legacyRequesterBusDim = defaultDim == null;
            }
        }
        ListTag stepList = tag.getList("Steps", Tag.TAG_COMPOUND);
        var ops = provider.createSerializationContext(NbtOps.INSTANCE);
        for (int i = 0; i < stepList.size(); i++) {
            CompoundTag t = stepList.getCompound(i);
            PatternData.CODEC.parse(ops, t.get("Pattern"))
                    .result().ifPresent(p -> job.steps.add(new StepState(p, t.getLong("Remaining"))));
        }
        loadMap(tag.getList("Inventory", Tag.TAG_COMPOUND), job.inventory, provider);
        loadMap(tag.getList("Awaiting", Tag.TAG_COMPOUND), job.awaiting, provider);
        ListTag flights = tag.getList("InFlight", Tag.TAG_COMPOUND);
        for (int i = 0; i < flights.size(); i++) {
            CompoundTag t = flights.getCompound(i);
            BlockPos provPos = BlockPos.of(t.getLong("Provider"));
            ResourceKey<Level> provDim = parseDimension(t, "Dim");
            InFlight f = provDim != null
                    ? new InFlight(GlobalPos.of(provDim, provPos), false)
                    : new InFlight(GlobalPos.of(defaultDim != null ? defaultDim : Level.OVERWORLD, provPos), defaultDim == null);
            loadMap(t.getList("Left", Tag.TAG_COMPOUND), f.left, provider);
            if (!f.left.isEmpty()) {
                job.inFlight.add(f);
            }
        }
        return job;
    }
}
