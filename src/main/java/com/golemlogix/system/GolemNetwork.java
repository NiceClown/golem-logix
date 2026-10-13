package com.golemlogix.system;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CableBushingBlock;
import net.minecraft.world.level.block.Block;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Golem Logix network.
 *
 * Built by walking neighbouring node blocks (GolemNode). It needs an Atlas Core powered with Source to work.
 * Every device takes channels; a channel goes from the core along the pipe tree, every pipe has its own limit
 * (basic snow pipe 16, dense frost pipe 64). Channels leave the network for the outside world through Cable Bushings:
 * a complete Atlas Array has 5 of them, each with a "line" of 64 channels (320 in total); Channel Expanders in the array walls
 * raise the capacity of every line. Without the array the core gives only 8 "starter" channels.
 */
public final class GolemNetwork {
    public static final int MAX_NODES = 4096;
    public static final int BARE_CORE_CHANNELS = 8;

    private final Level level;
    private final BlockPos start;

    private BlockPos corePos;
    private AtlasCoreBlockEntity core;
    private AtlasStructure.Result structure;
    private int lineCapacity = BARE_CORE_CHANNELS;
    private int channelCapacity;
    private int channelsUsed;
    private int deviceCount;

    private final Set<BlockPos> nodes = new HashSet<>();
    private final Set<BlockPos> activeDevices = new HashSet<>();
    /** How many channels pass through each conducting node (pipes, bushings), for tooltips and limits. */
    private final Map<BlockPos, Integer> load = new HashMap<>();
    private final List<CopperDriveBayBlockEntity> bays = new ArrayList<>();
    private final List<External> externals = new ArrayList<>();
    private final List<PatternProviderBlockEntity> providers = new ArrayList<>();
    private final List<com.golemlogix.blockentity.CardCatalogBlockEntity> catalogs = new ArrayList<>();
    private final List<BusBlockEntity> buses = new ArrayList<>();
    private final List<com.golemlogix.crafting.CraftingCpu> cpus = new ArrayList<>();
    private final List<SourceHelper.Supplier> sources = new ArrayList<>();
    private int resonators = 0;

    private Map<ItemKey, PatternRef> patternIndex;

    private GolemNetwork(Level level, BlockPos start) {
        this.level = level;
        this.start = start;
    }

    /** An external container behind a Periscope: the priority comes from the periscope itself. */
    public record External(BlockPos target, Direction face, int priority, BlockPos periscope) {
    }

    public record PatternRef(PatternData data, PatternProviderBlockEntity provider) {
    }

    // ------------------------------------------------------------------ cache

    private record CacheKey(ResourceKey<Level> dim, BlockPos pos) {
    }

    private record CacheEntry(GolemNetwork network, long time) {
    }

    private static final Map<CacheKey, CacheEntry> CACHE = new HashMap<>();
    private static final int CACHE_TTL = 8;

    /** A network with a short cache, for ticking devices. */
    public static GolemNetwork get(Level level, BlockPos start) {
        long now = level.getGameTime();
        CacheKey key = new CacheKey(level.dimension(), start.immutable());
        CacheEntry e = CACHE.get(key);
        if (e != null && now - e.time < CACHE_TTL && now >= e.time) {
            return e.network;
        }
        if (CACHE.size() > 8192) {
            Iterator<Map.Entry<CacheKey, CacheEntry>> it = CACHE.entrySet().iterator();
            while (it.hasNext()) {
                CacheEntry c = it.next().getValue();
                if (now - c.time >= CACHE_TTL || now < c.time) {
                    it.remove();
                }
            }
        }
        GolemNetwork net = scan(level, start);
        CacheEntry entry = new CacheEntry(net, now);
        CACHE.put(key, entry);
        // the walk from any node gives the same network: the other nodes (buses, calculators, terminals) get it without walking again
        for (BlockPos p : net.nodes) {
            CACHE.put(new CacheKey(level.dimension(), p), entry);
        }
        return net;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    // ------------------------------------------------------------------- scan

    public static GolemNetwork scan(Level level, BlockPos start) {
        GolemNetwork net = new GolemNetwork(level, start.immutable());
        net.doScan();
        return net;
    }

    private void doScan() {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        Set<BlockPos> seen = new HashSet<>();
        seen.add(start);

        while (!queue.isEmpty() && nodes.size() < MAX_NODES) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof GolemNode)) {
                continue;
            }
            nodes.add(pos);

            if (state.getBlock() instanceof AtlasCoreBlock && corePos == null) {
                corePos = pos;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CopperDriveBayBlockEntity bay) {
                bays.add(bay);
            } else if (be instanceof BusBlockEntity bus) {
                buses.add(bus);
            } else if (be instanceof PatternProviderBlockEntity provider) {
                providers.add(provider);
            } else if (be instanceof com.golemlogix.blockentity.CardCatalogBlockEntity catalog) {
                catalogs.add(catalog);
            } else if (be instanceof CryoCalculatorBlockEntity cpu) {
                cpus.add(cpu);
            } else if (be instanceof com.golemlogix.blockentity.CryoMainframeBlockEntity mainframe) {
                // mainframe cores are separate processors, but they share one channel (the owner block's)
                cpus.addAll(mainframe.cores());
            } else if (be instanceof AtlasCoreBlockEntity c && pos.equals(corePos)) {
                core = c;
            }
            if (state.getBlock() instanceof PeriscopeStorageBusBlock) {
                Direction facing = state.getValue(PeriscopeStorageBusBlock.FACING);
                BlockPos target = pos.relative(facing);
                if (level.isLoaded(target) && !(level.getBlockState(target).getBlock() instanceof GolemNode)) {
                    PeriscopeBlockEntity pb = level.getBlockEntity(pos) instanceof PeriscopeBlockEntity p ? p : null;
                    // the periscope's Redstone Module switches the storage off by signal
                    if (pb == null || pb.storageEnabled()) {
                        externals.add(new External(target, facing.getOpposite(), pb == null ? 0 : pb.getPriority(), pos));
                    }
                }
            }

            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                // a node gets "seen" only if there is a link: a multiblock socket may be reached from another side
                if (!seen.contains(next) && linkable(pos, state, next, dir)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }

        // Source sources next to the nodes; the Resonator is not a source for the network, it powers the Chronometers bound to it
        Set<BlockEntity> seenSources = new HashSet<>();
        for (BlockPos pos : nodes) {
            for (Direction dir : Direction.values()) {
                BlockPos p = pos.relative(dir);
                if (nodes.contains(p) || !level.isLoaded(p)) {
                    continue;
                }
                BlockEntity be = level.getBlockEntity(p);
                if (be != null && seenSources.add(be)) {
                    if (be instanceof com.golemlogix.blockentity.EternalFrostResonatorBlockEntity) {
                        resonators++;
                        continue;
                    }
                    SourceHelper.Supplier s = SourceHelper.asSupplier(be);
                    if (s != null) {
                        sources.add(s);
                    }
                }
            }
        }

        if (corePos != null) {
            structure = AtlasStructure.check(level, corePos);
            if (structure.valid()) {
                lineCapacity = GolemConfig.LINE_CHANNELS.get() + GolemConfig.EXPANDER_BONUS.get() * structure.expanders()
                        + GolemConfig.QUANTUM_BONUS.get() * structure.quantumExpanders();
                channelCapacity = lineCapacity * AtlasStructure.BUSHINGS;
            } else {
                lineCapacity = BARE_CORE_CHANNELS;
                channelCapacity = BARE_CORE_CHANNELS;
            }
            allocateChannels();
            // storages and providers without a channel stay silent, like terminals and buses
            bays.removeIf(b -> !activeDevices.contains(b.getBlockPos()));
            externals.removeIf(e -> !activeDevices.contains(e.periscope()));
            providers.removeIf(p -> !activeDevices.contains(p.getBlockPos()));
            catalogs.removeIf(p -> !activeDevices.contains(p.getBlockPos()));
            // two periscopes on one chest must not double its contents
            Set<BlockPos> targets = new HashSet<>();
            externals.removeIf(e -> !targets.add(e.target()));
        } else {
            channelCapacity = BARE_CORE_CHANNELS;
        }
    }

    /** Two neighbouring nodes connect only if each agrees to link with the other (dir = from from to the neighbour). */
    private boolean linkable(BlockPos fromPos, BlockState from, BlockPos nextPos, Direction dir) {
        if (!level.isLoaded(nextPos)) {
            return false;
        }
        return GolemNode.linked(level, fromPos, from, nextPos, level.getBlockState(nextPos), dir);
    }

    /** How many channels the outer face of a Channel Expander gives out (only in a complete array). */
    private int expanderExit(com.golemlogix.blocks.ChannelExpanderBlock ex) {
        if (structure == null || !structure.valid()) {
            return 0;
        }
        return ex.isQuantum() ? GolemConfig.QUANTUM_EXIT.get() : GolemConfig.EXPANDER_EXIT.get();
    }

    /** Capacity of the exit the channels to this node pass through (for tooltips): for a bushing its line, for an expander its own exit. */
    public int exitCapacity(BlockPos pos) {
        Block b = level.getBlockState(pos).getBlock();
        if (b instanceof CableBushingBlock) {
            return lineCapacity;
        }
        if (b instanceof com.golemlogix.blocks.ChannelExpanderBlock ex) {
            return expanderExit(ex);
        }
        return b instanceof GolemNode n ? n.channelCapacity() : 0;
    }

    /** The node is part of the network (the walk reached it). */
    public boolean contains(BlockPos pos) {
        return nodes.contains(pos);
    }

    /** Hands out channels to devices along the path tree from the core. */
    private void allocateChannels() {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        List<BlockPos> order = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(corePos);
        parent.put(corePos, null);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            order.add(pos);
            BlockState from = level.getBlockState(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (nodes.contains(next) && !parent.containsKey(next) && linkable(pos, from, next, dir)) {
                    parent.put(next, pos);
                    queue.add(next);
                }
            }
        }

        for (BlockPos pos : order) {
            BlockState state = level.getBlockState(pos);
            int cost = ((GolemNode) state.getBlock()).channelCost(state);
            if (cost <= 0) {
                continue;
            }
            deviceCount++;
            if (channelsUsed + cost > channelCapacity) {
                continue;
            }
            boolean ok = true;
            boolean exitCapped = false;
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos p = parent.get(pos); p != null && !p.equals(corePos); p = parent.get(p)) {
                Block block = level.getBlockState(p).getBlock();
                int cap;
                if (block instanceof CableBushingBlock) {
                    // only the bushing closest to the device (the "exit" from the array) is limited by the line capacity
                    cap = exitCapped ? Integer.MAX_VALUE : lineCapacity;
                    exitCapped = true;
                } else if (block instanceof com.golemlogix.blocks.ChannelExpanderBlock ex) {
                    // an expander in the array wall is an exit too, with its own capacity
                    cap = exitCapped ? Integer.MAX_VALUE : expanderExit(ex);
                    exitCapped = true;
                } else {
                    cap = ((GolemNode) block).channelCapacity();
                }
                if (load.getOrDefault(p, 0) + cost > cap) {
                    ok = false;
                    break;
                }
                path.add(p);
            }
            if (!ok) {
                continue;
            }
            for (BlockPos p : path) {
                load.merge(p, cost, Integer::sum);
            }
            channelsUsed += cost;
            activeDevices.add(pos);
        }
    }

    // ----------------------------------------------------------------- status

    public Level level() {
        return level;
    }

    public boolean hasCore() {
        return corePos != null;
    }

    @Nullable
    public BlockPos corePos() {
        return corePos;
    }

    @Nullable
    public AtlasCoreBlockEntity core() {
        return core;
    }

    /** There is a core and it is powered with Source. */
    public boolean isOnline() {
        return core != null && core.isPowered();
    }

    public int channelCapacity() {
        return channelCapacity;
    }

    public int channelsUsed() {
        return channelsUsed;
    }

    public int deviceCount() {
        return deviceCount;
    }

    /** How many channels pass through a node now (for example a cable bushing). */
    public int loadAt(BlockPos pos) {
        return load.getOrDefault(pos, 0);
    }

    /** Capacity of one line (one cable bushing). */
    public int lineCapacity() {
        return lineCapacity;
    }

    @Nullable
    public AtlasStructure.Result structure() {
        return structure;
    }

    public boolean isDeviceActive(BlockPos pos) {
        return activeDevices.contains(pos);
    }

    public List<SourceHelper.Supplier> sources() {
        return sources;
    }

    /** An Eternal Frost Resonator stands next to the network: the Chronometers of this network do not need charging. */
    public boolean hasResonator() {
        return resonators > 0;
    }

    public boolean hasInfiniteSource() {
        for (SourceHelper.Supplier s : sources) {
            if (s.infinite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The bus's turn among all buses of the network: 0 for the highest priority one. A bus works on the beat "time - turn",
     * so buses with a bigger priority make their move before the others in every period.
     */
    public int busRank(BusBlockEntity bus) {
        List<BusBlockEntity> sorted = new ArrayList<>(buses);
        sorted.sort(java.util.Comparator.comparingInt(BusBlockEntity::getPriority).reversed()
                .thenComparingLong(b -> b.getBlockPos().asLong()));
        int idx = sorted.indexOf(bus);
        return Math.max(0, idx);
    }

    public List<com.golemlogix.crafting.CraftingCpu> cpus() {
        return cpus;
    }

    public List<PatternProviderBlockEntity> providers() {
        return providers;
    }

    /** Punch Card Catalogs with a channel: the calculators run their crafting-table patterns themselves. */
    public List<com.golemlogix.blockentity.CardCatalogBlockEntity> catalogs() {
        return catalogs;
    }

    /** For the terminal: whether it can be used. Returns null or an error message key. */
    @Nullable
    public String accessError(@Nullable BlockPos devicePos) {
        if (!hasCore()) {
            return "message.golem_logix.no_core";
        }
        if (!isOnline()) {
            return "message.golem_logix.no_power";
        }
        if (devicePos != null && !isDeviceActive(devicePos)) {
            return "message.golem_logix.no_channels";
        }
        return null;
    }

    /**
     * Turns the pipe, cabinet and terminal animations on/off (the active property): the network is powered and, for devices that need
     * a channel, they got one. Before, every node got the flag, and devices beyond the channel limit (or behind a pipe with
     * a smaller capacity) looked as if they worked. The calculator sets its own flag (it means "a job is running").
     */
    public void syncActiveVisuals(boolean active) {
        for (BlockPos pos : nodes) {
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(GolemProperties.ACTIVE) || state.getBlock() instanceof com.golemlogix.blocks.CryoCalculatorBlock) {
                continue;
            }
            boolean on = active && (((GolemNode) state.getBlock()).channelCost(state) <= 0 || activeDevices.contains(pos));
            if (state.getValue(GolemProperties.ACTIVE) != on) {
                level.setBlock(pos, state.setValue(GolemProperties.ACTIVE, on), 18);
            }
        }
    }

    // ---------------------------------------------------------------- storage

    private List<ItemStack> allDisks(boolean fluid) {
        List<ItemStack> disks = new ArrayList<>();
        for (CopperDriveBayBlockEntity bay : bays) {
            if (bay.isRemoved()) {
                continue;
            }
            for (ItemStack disk : bay.getDisks()) {
                if (fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk)) {
                    disks.add(disk);
                }
            }
        }
        return disks;
    }

    private void markBaysChanged() {
        for (CopperDriveBayBlockEntity bay : bays) {
            bay.setChanged();
            bay.refreshVisuals();
        }
    }

    /**
     * Item handler of the container behind a periscope. First the face the periscope is pressed against, then the "general" one (null)
     * and the other faces: many tanks and chests give access only from some sides.
     */
    @Nullable
    private IItemHandler itemHandler(External ex) {
        if (!level.isLoaded(ex.target)) {
            return null;
        }
        IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, ex.face);
        if (h == null) {
            h = level.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, null);
        }
        for (Direction d : Direction.values()) {
            if (h != null) {
                break;
            }
            h = level.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, d);
        }
        return h;
    }

    @Nullable
    private IFluidHandler fluidHandler(External ex) {
        if (!level.isLoaded(ex.target)) {
            return null;
        }
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, ex.face);
        if (h == null) {
            h = level.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, null);
        }
        for (Direction d : Direction.values()) {
            if (h != null) {
                break;
            }
            h = level.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, d);
        }
        return h;
    }

    /** Storages (cabinets and periscopes) from the highest priority to the lowest; equal ones go in network walk order. */
    private record Tier(int priority, List<CopperDriveBayBlockEntity> bays, List<External> exts) {
    }

    private List<Tier> tiers(boolean highestFirst) {
        java.util.Comparator<Integer> order = highestFirst ? java.util.Comparator.<Integer>reverseOrder() : java.util.Comparator.<Integer>naturalOrder();
        java.util.TreeMap<Integer, Tier> map = new java.util.TreeMap<Integer, Tier>(order);
        for (CopperDriveBayBlockEntity bay : bays) {
            if (!bay.isRemoved()) {
                map.computeIfAbsent(bay.getPriority(), p -> new Tier(p, new ArrayList<>(), new ArrayList<>())).bays.add(bay);
            }
        }
        for (External ex : externals) {
            map.computeIfAbsent(ex.priority(), p -> new Tier(p, new ArrayList<>(), new ArrayList<>())).exts.add(ex);
        }
        return new ArrayList<>(map.values());
    }

    private static List<ItemStack> disksOf(Tier tier, boolean fluid) {
        List<ItemStack> disks = new ArrayList<>();
        for (CopperDriveBayBlockEntity bay : tier.bays) {
            for (ItemStack disk : bay.getDisks()) {
                if (fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk)) {
                    disks.add(disk);
                }
            }
        }
        return disks;
    }

    /** All items of the network: type -> count. */
    public Map<ItemKey, Long> listItems() {
        Map<ItemKey, Long> all = new LinkedHashMap<>();
        for (ItemStack disk : allDisks(false)) {
            for (Map.Entry<ItemKey, Integer> e : DiskStorage.items(disk).entrySet()) {
                all.merge(e.getKey(), (long) e.getValue(), Long::sum);
            }
        }
        for (External ex : externals) {
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) {
                    all.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
                }
            }
        }
        return all;
    }

    public long itemCountOf(ItemKey key) {
        long n = 0;
        for (ItemStack disk : allDisks(false)) {
            n += DiskStorage.items(disk).getOrDefault(key, 0);
        }
        for (External ex : externals) {
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && key.matches(s)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    public long itemCapacity() {
        long cap = 0;
        for (ItemStack disk : allDisks(false)) {
            cap += DiskStorage.capacity(disk);
        }
        return cap;
    }

    public long itemsStoredOnDisks() {
        long used = 0;
        for (ItemStack disk : allDisks(false)) {
            used += DiskStorage.storedItems(disk);
        }
        return used;
    }

    public long fluidCapacity() {
        long cap = 0;
        for (ItemStack disk : allDisks(true)) {
            cap += DiskStorage.capacity(disk);
        }
        return cap;
    }

    public long fluidStoredOnDisks() {
        long used = 0;
        for (ItemStack disk : allDisks(true)) {
            used += DiskStorage.storedItems(disk);
        }
        return used;
    }

    /**
     * Puts items into the network. Returns the rest that did not fit.
     * Order: from the highest priority storage to the lowest; within one priority, first the disks
     * that already hold this item, then any disks, then the external containers behind periscopes.
     */
    public ItemStack insertItem(ItemStack input, boolean simulate) {
        return insertItem(input, simulate, null);
    }

    /**
     * The same, but nothing goes into the container skipTarget (if a periscope connects it): an import bus must not return items
     * into the same chest it has just taken them from, otherwise it moves them in circles and clicks for nothing.
     */
    public ItemStack insertItem(ItemStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = input.copy();
        List<Tier> tiers = tiers(true);

        if (simulate) {
            long free = 0;
            for (ItemStack disk : allDisks(false)) {
                free += DiskStorage.capacity(disk) - DiskStorage.storedItems(disk);
            }
            remaining.shrink((int) Math.min(free, remaining.getCount()));
        } else {
            boolean changed = false;
            for (Tier tier : tiers) {
                List<ItemStack> disks = disksOf(tier, false);
                for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                    for (ItemStack disk : disks) {
                        if (remaining.isEmpty()) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsItem(disk, remaining)) {
                            continue;
                        }
                        int n = DiskStorage.insertItem(disk, remaining, false);
                        if (n > 0) {
                            remaining.shrink(n);
                            changed = true;
                        }
                    }
                }
                remaining = insertIntoExternals(tier, remaining, false, skipTarget);
                if (remaining.isEmpty()) {
                    break;
                }
            }
            if (changed) {
                markBaysChanged();
            }
            return remaining;
        }
        // simulation: disks are already counted by the sum of free space, the external ones are checked one by one
        for (Tier tier : tiers) {
            if (remaining.isEmpty()) {
                break;
            }
            remaining = insertIntoExternals(tier, remaining, true, skipTarget);
        }
        return remaining;
    }

    private ItemStack insertIntoExternals(Tier tier, ItemStack remaining, boolean simulate, @Nullable BlockPos skipTarget) {
        for (External ex : tier.exts) {
            if (remaining.isEmpty()) {
                break;
            }
            if (ex.target().equals(skipTarget)) {
                continue;
            }
            IItemHandler h = itemHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getSlots() && !remaining.isEmpty(); i++) {
                remaining = h.insertItem(i, remaining, simulate);
            }
        }
        return remaining;
    }

    /**
     * Takes items from the network (at most amount). Returns the stack received (count may exceed a stack).
     * Takes from the storages with the LOWEST priority first, so the high-priority ones stay the "main warehouse".
     */
    public ItemStack extractItem(ItemStack template, int amount, boolean simulate) {
        return extractItem(template, amount, simulate, null);
    }

    /** The same, but nothing is taken from the container skipTarget: an export bus does not move items from a chest into the same chest. */
    public ItemStack extractItem(ItemStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        int left = amount;
        boolean changed = false;

        for (Tier tier : tiers(false)) {
            if (left <= 0) {
                break;
            }
            for (ItemStack disk : disksOf(tier, false)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractItem(disk, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    changed = true;
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (ex.target().equals(skipTarget)) {
                    continue;
                }
                IItemHandler h = itemHandler(ex);
                if (h == null) {
                    continue;
                }
                for (int i = 0; i < h.getSlots() && left > 0; i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, template)) {
                        ItemStack got = h.extractItem(i, Math.min(left, s.getCount()), simulate);
                        left -= got.getCount();
                    }
                }
            }
        }
        if (changed && !simulate) {
            markBaysChanged();
        }
        int taken = amount - left;
        return taken > 0 ? template.copyWithCount(taken) : ItemStack.EMPTY;
    }
    /** All fluids of the network: type -> mB. */
    public List<FluidStack> listFluids() {
        List<FluidStack> all = new ArrayList<>();
        for (ItemStack disk : allDisks(true)) {
            for (FluidStack f : DiskStorage.fluids(disk)) {
                mergeFluid(all, f);
            }
        }
        for (External ex : externals) {
            IFluidHandler h = fluidHandler(ex);
            if (h == null) {
                continue;
            }
            for (int i = 0; i < h.getTanks(); i++) {
                FluidStack f = h.getFluidInTank(i);
                // Liquid Cryo-Source in a tank behind a periscope is not network stock: the network makes its own from Source
                if (!f.isEmpty() && !CryoSource.is(f)) {
                    mergeFluid(all, f);
                }
            }
        }
        return all;
    }

    private static void mergeFluid(List<FluidStack> all, FluidStack f) {
        for (int i = 0; i < all.size(); i++) {
            FluidStack e = all.get(i);
            if (FluidStack.isSameFluidSameComponents(e, f)) {
                all.set(i, e.copyWithAmount((int) Math.min((long) e.getAmount() + f.getAmount(), Integer.MAX_VALUE)));
                return;
            }
        }
        all.add(f.copy());
    }

    /** Puts fluid into the network (in the same order as items: from the highest priority). Returns how many mB were accepted. */
    public int insertFluid(FluidStack input, boolean simulate) {
        return insertFluid(input, simulate, null);
    }

    public int insertFluid(FluidStack input, boolean simulate, @Nullable BlockPos skipTarget) {
        // Liquid Cryo-Source is never stored: it only exists while automation pumps it out of the network
        if (input.isEmpty() || CryoSource.is(input)) {
            return 0;
        }
        int left = input.getAmount();
        boolean changed = false;
        if (simulate) {
            long free = 0;
            for (ItemStack disk : allDisks(true)) {
                free += DiskStorage.capacity(disk) - DiskStorage.storedItems(disk);
            }
            left = (int) Math.max(0, input.getAmount() - free);
        }
        for (Tier tier : tiers(true)) {
            if (left <= 0) {
                break;
            }
            if (!simulate) {
                List<ItemStack> disks = disksOf(tier, true);
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (ItemStack disk : disks) {
                        if (left <= 0) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsFluid(disk, input)) {
                            continue;
                        }
                        int n = DiskStorage.insertFluid(disk, input.copyWithAmount(left), false);
                        if (n > 0) {
                            left -= n;
                            changed = true;
                        }
                    }
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (ex.target().equals(skipTarget)) {
                    continue;
                }
                IFluidHandler h = fluidHandler(ex);
                if (h == null) {
                    continue;
                }
                int n = h.fill(input.copyWithAmount(left), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                left -= n;
            }
        }
        if (changed) {
            markBaysChanged();
        }
        return input.getAmount() - left;
    }

    /** Takes fluid from the network (from the lowest priority storages first). */
    public FluidStack extractFluid(FluidStack template, int amount, boolean simulate) {
        return extractFluid(template, amount, simulate, null);
    }

    public FluidStack extractFluid(FluidStack template, int amount, boolean simulate, @Nullable BlockPos skipTarget) {
        if (template.isEmpty() || amount <= 0) {
            return FluidStack.EMPTY;
        }
        if (CryoSource.is(template)) {
            return extractCryoSource(amount, simulate);
        }
        int left = amount;
        boolean changed = false;
        for (Tier tier : tiers(false)) {
            if (left <= 0) {
                break;
            }
            for (ItemStack disk : disksOf(tier, true)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractFluid(disk, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    changed = true;
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (ex.target().equals(skipTarget)) {
                    continue;
                }
                IFluidHandler h = fluidHandler(ex);
                if (h == null) {
                    continue;
                }
                FluidStack got = h.drain(template.copyWithAmount(left), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
                left -= got.getAmount();
            }
        }
        if (changed && !simulate) {
            markBaysChanged();
        }
        int taken = amount - left;
        return taken > 0 ? template.copyWithAmount(taken) : FluidStack.EMPTY;
    }

    /** How much Liquid Cryo-Source (mB) the network can make right now from its Source. */
    public long cryoSourceAvailable() {
        if (!isOnline()) {
            return 0;
        }
        long per = GolemConfig.CRYO_SOURCE_COST.get();
        if (per <= 0 || !SourceHelper.sourceRequired() || hasInfiniteSource()) {
            return Integer.MAX_VALUE;
        }
        long total = 0;
        for (SourceHelper.Supplier s : sources) {
            total += s.available();
        }
        return total / per;
    }

    /** Makes Liquid Cryo-Source from the network's Source (all of the amount that the Source covers). */
    private FluidStack extractCryoSource(int amount, boolean simulate) {
        int n = (int) Math.min(amount, cryoSourceAvailable());
        if (n <= 0) {
            return FluidStack.EMPTY;
        }
        if (!simulate && !consumeSource((int) Math.min(CryoSource.cost(n), Integer.MAX_VALUE))) {
            return FluidStack.EMPTY;
        }
        return CryoSource.stack(n);
    }

    /** How much of a fluid the network has (mB); Liquid Cryo-Source counts what the Source can make. */
    public long fluidCountOf(FluidStack fluid) {
        if (CryoSource.is(fluid)) {
            return cryoSourceAvailable();
        }
        long n = 0;
        for (FluidStack f : listFluids()) {
            if (FluidStack.isSameFluidSameComponents(f, fluid)) {
                n += f.getAmount();
            }
        }
        return n;
    }

    /** Stock of an ingredient: items by count, fluid keys in mB. */
    public long countOf(ItemKey key) {
        return Ingredients.isFluid(key) ? fluidCountOf(Ingredients.fluid(key, 1)) : itemCountOf(key);
    }
    // --------------------------------------------------------------- crafting

    /** Index "item -> the pattern that produces it". */
    public Map<ItemKey, PatternRef> patternIndex() {
        if (patternIndex == null) {
            patternIndex = new LinkedHashMap<>();
            for (PatternProviderBlockEntity provider : providers) {
                if (provider.isRemoved()) {
                    continue;
                }
                for (PatternData pattern : provider.getPatterns()) {
                    for (ItemKey out : pattern.outputTotals().keySet()) {
                        patternIndex.putIfAbsent(out, new PatternRef(pattern, provider));
                    }
                }
            }
            for (com.golemlogix.blockentity.CardCatalogBlockEntity catalog : catalogs) {
                if (catalog.isRemoved()) {
                    continue;
                }
                for (PatternData pattern : catalog.patterns()) {
                    for (ItemKey out : pattern.outputTotals().keySet()) {
                        patternIndex.putIfAbsent(out, new PatternRef(pattern, null));
                    }
                }
            }
        }
        return patternIndex;
    }

    public boolean canCraft(ItemKey key) {
        return patternIndex().containsKey(key);
    }

    @Nullable
    public PatternProviderBlockEntity providerFor(PatternData pattern) {
        for (PatternProviderBlockEntity provider : providers) {
            if (!provider.isRemoved() && provider.hasPattern(pattern)) {
                return provider;
            }
        }
        return null;
    }

    public enum StartType {
        STARTED, OFFLINE, NO_PATTERN, MISSING, NO_CPU, RESERVE_FAILED
    }

    public record StartResult(StartType type, CraftingPlan plan) {
    }

    /** Plans and starts autocrafting on the first free Cryo-Calculator. */
    public StartResult startCrafting(ItemKey target, long amount) {
        Map<ItemKey, Long> one = new LinkedHashMap<>();
        one.put(target, amount);
        return startCrafting(one, null, null);
    }

    /** Whether there is a free calculator with a channel. */
    @Nullable
    public com.golemlogix.crafting.CraftingCpu freeCpu() {
        for (com.golemlogix.crafting.CraftingCpu c : cpus) {
            if (!c.isRemoved() && c.isIdle() && c.canRun(this)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Plans and starts autocrafting of several items at once on the first free Cryo-Calculator.
     * requester = the player who gets the "ready" notification; bus = the export bus that ordered the craft itself.
     */
    public StartResult startCrafting(Map<ItemKey, Long> targets, @Nullable java.util.UUID requester, @Nullable BlockPos bus) {
        if (!isOnline()) {
            return new StartResult(StartType.OFFLINE, null);
        }
        if (targets.isEmpty()) {
            return new StartResult(StartType.NO_PATTERN, null);
        }
        for (ItemKey t : targets.keySet()) {
            if (!canCraft(t)) {
                return new StartResult(StartType.NO_PATTERN, null);
            }
        }
        CraftingPlan plan = CraftingPlanner.plan(this, targets);
        if (!plan.missing().isEmpty()) {
            return new StartResult(StartType.MISSING, plan);
        }
        if (plan.steps().isEmpty()) {
            return new StartResult(StartType.NO_PATTERN, plan);
        }
        com.golemlogix.crafting.CraftingCpu cpu = freeCpu();
        if (cpu == null) {
            return new StartResult(StartType.NO_CPU, plan);
        }

        // reserve the source materials: all or nothing
        for (Map.Entry<ItemKey, Long> e : plan.fromStorage().entrySet()) {
            if (countOf(e.getKey()) < e.getValue()) {
                return new StartResult(StartType.RESERVE_FAILED, plan);
            }
        }
        // Liquid Cryo-Source goes last: it is made from Source and cannot be put back if something else fails
        List<Map.Entry<ItemKey, Long>> order = new ArrayList<>(plan.fromStorage().entrySet());
        order.sort(java.util.Comparator.comparing((Map.Entry<ItemKey, Long> e) -> Ingredients.isFluid(e.getKey())
                && CryoSource.is(Ingredients.fluid(e.getKey(), 1))));
        Map<ItemKey, Long> reserved = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Long> e : order) {
            long got = reserve(e.getKey(), e.getValue());
            if (got > 0) {
                reserved.merge(e.getKey(), got, Long::sum);
            }
            if (got < e.getValue()) {
                // a race: return what was taken
                for (Map.Entry<ItemKey, Long> r : reserved.entrySet()) {
                    giveBack(r.getKey(), r.getValue());
                }
                return new StartResult(StartType.RESERVE_FAILED, plan);
            }
        }
        cpu.startJob(new CraftingJob(plan, reserved).requestedBy(requester, bus));
        return new StartResult(StartType.STARTED, plan);
    }

    /** Takes up to amount of an ingredient out of storage (items by count, fluids in mB); returns how much was taken. */
    private long reserve(ItemKey key, long amount) {
        long left = amount;
        boolean fluid = Ingredients.isFluid(key);
        while (left > 0) {
            int chunk = (int) Math.min(left, 1_000_000_000L);
            int got = fluid ? extractFluid(Ingredients.fluid(key, 1), chunk, false).getAmount()
                    : extractItem(key.stack(), chunk, false).getCount();
            if (got <= 0) {
                break;
            }
            left -= got;
        }
        return amount - left;
    }

    /** Puts an ingredient back into storage; returns how much did not fit (Liquid Cryo-Source never fits). */
    public long giveBack(ItemKey key, long amount) {
        long n = amount;
        while (n > 0) {
            int chunk = (int) Math.min(n, 1_000_000_000L);
            int stored = Ingredients.isFluid(key) ? insertFluid(Ingredients.fluid(key, chunk), false)
                    : chunk - insertItem(key.toStack(chunk), false).getCount();
            n -= stored;
            if (stored < chunk) {
                break;
            }
        }
        return n;
    }

    /** The provider waits for the result of the previous portion (wait mode): some job has not received its output yet. */
    public boolean providerBusy(BlockPos provider) {
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            if (!cpu.isRemoved() && cpu.getJob() != null && cpu.getJob().hasInFlight(provider)) {
                return true;
            }
        }
        return false;
    }

    /** A job ordered by this bus for this item is already running. */
    public boolean busCraftPending(BlockPos bus, ItemKey key) {
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            CraftingJob job = cpu.isRemoved() ? null : cpu.getJob();
            if (job != null && bus.equals(job.requesterBus()) && job.targets().containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** Whether there is enough Source to take amount (takes nothing). */
    public boolean hasSource(int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || hasInfiniteSource()) {
            return true;
        }
        long total = 0;
        for (SourceHelper.Supplier s : sources) {
            total += s.available();
        }
        return total >= amount;
    }

    /** Takes Source from the network. Always succeeds without the Source requirement or with an infinite source. */
    public boolean consumeSource(int amount) {
        if (amount <= 0 || !SourceHelper.sourceRequired() || hasInfiniteSource()) {
            return true;
        }
        long total = 0;
        for (SourceHelper.Supplier s : sources) {
            total += s.available();
        }
        if (total < amount) {
            return false;
        }
        int left = amount;
        for (SourceHelper.Supplier s : sources) {
            left -= s.drain(left);
            if (left <= 0) {
                break;
            }
        }
        return true;
    }

    /**
     * Insertion from a machine (an import bus and the like): first the items active autocraft jobs are waiting
     * for, the rest goes to storage.
     */
    public int machineWants(ItemStack stack) {
        long n = 0;
        ItemKey key = ItemKey.of(stack);
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            if (!cpu.isRemoved()) {
                n += cpu.wants(key);
            }
        }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    public ItemStack insertFromMachine(ItemStack stack) {
        return insertFromMachine(stack, null);
    }

    /** skipTarget = the container the items were just taken from (they are not put back into it). */
    public ItemStack insertFromMachine(ItemStack stack, @Nullable BlockPos skipTarget) {
        ItemStack rest = stack;
        for (com.golemlogix.crafting.CraftingCpu cpu : cpus) {
            if (rest.isEmpty()) {
                break;
            }
            if (!cpu.isRemoved()) {
                rest = cpu.absorb(rest);
            }
        }
        return insertItem(rest, false, skipTarget);
    }
}
