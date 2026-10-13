package com.golemlogix.system;

import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blocks.AtlasConduitBlock;
import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CableBushingBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.blocks.TelegraphBlock;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A Golem Logix network.
 *
 * Built by walking neighbouring node blocks (GolemNode); a pair of Cryo-Source Telegraphs joins two parts of a network that are far
 * apart (even in different dimensions), so a node is a block in a level. It needs an Atlas Core powered with Source to work.
 * Every device takes one channel. {@link ChannelCalculation} hands the channels out as Applied Energistics 2 does: from the
 * controller (the core with the conduits of its array) along the shortest routes, dense carriers first, and a device gets a
 * channel only if every node on its route has room. Limits: a basic snow pipe carries 16, a dense frost pipe 64, a Cable Bushing
 * its line (64 in a complete Atlas Array, raised by Channel Expanders in the walls; 8 in a starter tower), the outer face of an
 * expander its exit, a telegraph 64, a device as much as a basic pipe (config "deviceChannels"). The whole network has the sum
 * of its five lines (8 without the array).
 */
public final class GolemNetwork {
    public static final int MAX_NODES = 16384;
    public static final int BARE_CORE_CHANNELS = 8;

    /** A node of a network: a block in a level (Level compares by identity). */
    public record Node(Level level, BlockPos pos) {
    }

    /** The level the walk started in: the methods that take only a position mean this level. */
    private final Level level;
    private final BlockPos start;

    @Nullable
    private Level coreLevel;
    private BlockPos corePos;
    private AtlasCoreBlockEntity core;
    private AtlasStructure.Result structure;
    private int lineCapacity = BARE_CORE_CHANNELS;
    private int channelCapacity = BARE_CORE_CHANNELS;
    private int channelsUsed;
    private int deviceCount;

    /** The nodes in walk order; a node's index is its place in these lists and in the arrays below. */
    private final List<Node> nodeList = new ArrayList<>();
    private final Map<Node, Integer> nodeIndex = new HashMap<>();
    private final List<BlockState> nodeStates = new ArrayList<>();
    /** Channel limit of every node (only with a core). */
    private int[] capacities = new int[0];
    /** The node belongs to the controller: the core and the conduits that carry its channels to the exits. */
    private boolean[] controller = new boolean[0];
    /** The channel assignment; null without a core. */
    @Nullable
    private ChannelCalculation channels;
    private final Set<Node> activeDevices = new HashSet<>();
    private final List<CopperDriveBayBlockEntity> bays = new ArrayList<>();
    private final List<External> externals = new ArrayList<>();
    private final List<PatternProviderBlockEntity> providers = new ArrayList<>();
    private final List<com.golemlogix.blockentity.CardCatalogBlockEntity> catalogs = new ArrayList<>();
    private final List<BusBlockEntity> buses = new ArrayList<>();
    private final List<com.golemlogix.crafting.CraftingCpu> cpus = new ArrayList<>();
    private final List<SourceHelper.Supplier> sources = new ArrayList<>();
    private int resonators = 0;
    /** Blocks next to the nodes that the network uses (Source jars, resonators, containers behind periscopes), with their block. */
    private final Map<Node, Block> peripherals = new HashMap<>();
    /** Telegraph links inside this network (each pair counted once): every one of them costs Source. */
    private int telegraphLinks;
    @Nullable
    private Map<BusBlockEntity, Integer> busRanks;

    private Map<ItemKey, PatternRef> patternIndex;
    private int patternIndexEpoch;

    private GolemNetwork(Level level, BlockPos start) {
        this.level = level;
        this.start = start;
    }

    /** An external container behind a Periscope: the priority comes from the periscope itself. */
    public record External(Level level, BlockPos target, Direction face, int priority, BlockPos periscope) {
    }

    public record PatternRef(PatternData data, PatternProviderBlockEntity provider) {
    }

    private Node node(BlockPos pos) {
        return new Node(level, pos);
    }

    // ------------------------------------------------------------------ cache

    /**
     * What is remembered for one level. The server and the client keep separate maps: in single player both run in one process, and
     * a network walked over the client's copy of the world must never serve the server.
     */
    private static final class LevelCache {
        /** Networks walked from this level, under every node of theirs in this level. */
        final Map<BlockPos, CacheEntry> byPos = new HashMap<>();
        /** Nodes and peripherals of the remembered networks in this level, with their blocks: tells a real change from a look change. */
        final Map<BlockPos, Block> known = new HashMap<>();
    }

    private record CacheEntry(GolemNetwork network, long time, int version) {
    }

    private static final Map<Level, LevelCache> SERVER_CACHE = new WeakHashMap<>();
    private static final Map<Level, LevelCache> CLIENT_CACHE = new WeakHashMap<>();
    /**
     * Raised on the server when a node or a peripheral appears, disappears or becomes another block, and on explicit changes (plugs,
     * multiblocks, priorities, telegraph cards): every remembered network is stale then (a telegraph can join levels).
     */
    private static int serverVersion;
    /**
     * A network is walked again after this many ticks even without a known change: a safety net for what no event reports. The
     * real changes make it stale at once, so the networks are not rescanned every few ticks any more.
     */
    private static final int CACHE_TTL = 100;
    /** Where nodes changed during this tick: the networks there are recomputed at the end of the tick. */
    private static final Map<Level, Set<BlockPos>> CHANGED = new WeakHashMap<>();
    /** Raised when the punch cards of a provider or a catalog change: the remembered pattern indexes are rebuilt. */
    private static volatile int patternEpoch;

    private static LevelCache cacheOf(Level level) {
        return (level.isClientSide() ? CLIENT_CACHE : SERVER_CACHE).computeIfAbsent(level, l -> new LevelCache());
    }

    private static int versionFor(Level level) {
        return level.isClientSide() ? 0 : serverVersion;
    }

    /** A network with a cache, for ticking devices. */
    public static GolemNetwork get(Level level, BlockPos start) {
        LevelCache cache = cacheOf(level);
        long now = level.getGameTime();
        CacheEntry e = cache.byPos.get(start);
        if (e != null && e.version == versionFor(level) && now - e.time < CACHE_TTL && now >= e.time) {
            return e.network;
        }
        return remember(level, scan(level, start), now);
    }

    /** Walks the network now and remembers it for all its nodes. */
    public static GolemNetwork refresh(Level level, BlockPos start) {
        return remember(level, scan(level, start), level.getGameTime());
    }

    private static GolemNetwork remember(Level level, GolemNetwork net, long now) {
        LevelCache cache = cacheOf(level);
        if (cache.byPos.size() > 65536) {
            cache.byPos.values().removeIf(c -> now - c.time > 1200 || now < c.time);
        }
        CacheEntry entry = new CacheEntry(net, now, versionFor(level));
        cache.byPos.put(net.start, entry);
        // the walk from any node gives the same network: the other nodes (buses, calculators, terminals) get it without walking again
        for (Node n : net.nodeList) {
            if (n.level == level) {
                cache.byPos.put(n.pos, entry);
            }
        }
        if (!level.isClientSide()) {
            for (int i = 0; i < net.nodeList.size(); i++) {
                Node n = net.nodeList.get(i);
                knownOf(n.level).put(n.pos, net.nodeStates.get(i).getBlock());
            }
            for (Map.Entry<Node, Block> p : net.peripherals.entrySet()) {
                knownOf(p.getKey().level).put(p.getKey().pos, p.getValue());
            }
        }
        return net;
    }

    private static Map<BlockPos, Block> knownOf(Level level) {
        LevelCache c = cacheOf(level);
        if (c.known.size() > 262144) {
            // forget everything once in a long while: the next walks repopulate it
            c.known.clear();
            serverVersion++;
        }
        return c.known;
    }

    /** Every remembered network on the server becomes stale (a multiblock formed, a periscope switched, a priority changed). */
    public static void clearCache() {
        serverVersion++;
    }

    /** The punch cards of a provider or a catalog changed. */
    public static void patternsChanged() {
        patternEpoch++;
    }

    /**
     * Links changed at pos without a block changing (a pipe plug, a multiblock that formed, a telegraph card): the remembered
     * networks are stale and the one around pos gets new channels and lights at the end of the tick.
     */
    public static void nodesChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        serverVersion++;
        CHANGED.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    /** The server stopped: nothing of its levels is kept. */
    public static void forgetAll() {
        SERVER_CACHE.clear();
        CHANGED.clear();
        serverVersion++;
    }

    /**
     * A block changed on the server (the neighbour notification of NeoForge). The remembered networks become stale only when a node
     * appears, disappears or becomes another block, or when a peripheral does (a Source jar, a resonator, a container in front of a
     * periscope): then the networks around get new channels and lights at the end of the tick, as an AE2 grid repaths when a
     * connection changes. A change of looks (a node's "active" flag, a jar's fill level, a furnace lighting up next to a bus) is not
     * a change of the network.
     */
    public static void onBlockChanged(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }
        LevelCache cache = SERVER_CACHE.get(level);
        Block was = cache == null ? null : cache.known.get(pos);
        Block now;
        if (state.getBlock() instanceof GolemNode) {
            now = state.getBlock();
        } else {
            now = cache != null && peripheralCandidate(level, pos, state, cache) ? state.getBlock() : null;
        }
        if (was == now) {
            return;
        }
        serverVersion++;
        CHANGED.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    /** A block next to a remembered node that the network would use: a Source supplier, a resonator, a container in front of a periscope. */
    private static boolean peripheralCandidate(Level level, BlockPos pos, BlockState state, LevelCache cache) {
        if (!state.hasBlockEntity()) {
            return false;
        }
        boolean nearNode = false;
        boolean faced = false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            Block b = cache.known.get(n);
            if (b instanceof GolemNode) {
                nearNode = true;
                if (b instanceof PeriscopeStorageBusBlock) {
                    BlockState ns = level.getBlockState(n);
                    faced |= ns.getBlock() instanceof PeriscopeStorageBusBlock && ns.getValue(PeriscopeStorageBusBlock.FACING) == d.getOpposite();
                }
            }
        }
        if (!nearNode) {
            return false;
        }
        if (faced) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof com.golemlogix.blockentity.EternalFrostResonatorBlockEntity || (be != null && SourceHelper.asSupplier(be) != null);
    }

    /** End of a server level tick: the networks where nodes changed get their channels and lights recomputed. */
    public static void processChanges(Level level) {
        Set<BlockPos> changed = CHANGED.remove(level);
        if (changed == null) {
            return;
        }
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : changed) {
            for (int d = -1; d < 6; d++) {
                BlockPos p = d < 0 ? pos : pos.relative(Direction.from3DDataValue(d));
                if (done.contains(p) || !level.isLoaded(p) || !(level.getBlockState(p).getBlock() instanceof GolemNode)) {
                    continue;
                }
                GolemNetwork net = refresh(level, p);
                done.add(p);
                for (Node n : net.nodeList) {
                    if (n.level == level) {
                        done.add(n.pos);
                    }
                }
                net.syncActiveVisuals(net.isOnline());
            }
        }
    }

    // ------------------------------------------------------------------- scan

    public static GolemNetwork scan(Level level, BlockPos start) {
        GolemNetwork net = new GolemNetwork(level, start.immutable());
        net.doScan();
        return net;
    }

    private void doScan() {
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Node first = node(start);
        queue.add(first);
        Set<Node> seen = new HashSet<>();
        seen.add(first);
        List<Node[]> linkedTo = new ArrayList<>();
        List<Node> cores = new ArrayList<>();

        while (!queue.isEmpty() && nodeList.size() < MAX_NODES) {
            Node node = queue.poll();
            Level lvl = node.level;
            BlockPos pos = node.pos;
            if (!lvl.isLoaded(pos)) {
                continue;
            }
            BlockState state = lvl.getBlockState(pos);
            if (!(state.getBlock() instanceof GolemNode)) {
                continue;
            }
            nodeIndex.put(node, nodeList.size());
            nodeList.add(node);
            nodeStates.add(state);

            if (state.getBlock() instanceof AtlasCoreBlock) {
                cores.add(node);
            }
            BlockEntity be = lvl.getBlockEntity(pos);
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
            }
            if (state.getBlock() instanceof PeriscopeStorageBusBlock) {
                Direction facing = state.getValue(PeriscopeStorageBusBlock.FACING);
                BlockPos target = pos.relative(facing);
                if (lvl.isLoaded(target)) {
                    BlockState ts = lvl.getBlockState(target);
                    if (!(ts.getBlock() instanceof GolemNode)) {
                        peripherals.put(new Node(lvl, target), ts.getBlock());
                        PeriscopeBlockEntity pb = be instanceof PeriscopeBlockEntity p ? p : null;
                        // the periscope's Redstone Module switches the storage off by signal
                        if (pb == null || pb.storageEnabled()) {
                            externals.add(new External(lvl, target, facing.getOpposite(), pb == null ? 0 : pb.getPriority(), pos));
                        }
                    }
                }
            }

            List<Node> links = new ArrayList<>(6);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                // a node gets "seen" only if there is a link: a multiblock socket may be reached from another side
                if (linkable(lvl, pos, state, next, dir)) {
                    links.add(new Node(lvl, next));
                }
            }
            // a telegraph also links to its distant partners: the host its memory card names and the telegraphs naming it
            if (state.getBlock() instanceof TelegraphBlock && TelegraphBlock.isMaster(state)) {
                for (TelegraphLinks.Partner p : TelegraphLinks.partners(lvl, pos)) {
                    links.add(p.node());
                    if (p.toHost()) {
                        telegraphLinks++;
                    }
                }
            }
            for (Node next : links) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
            linkedTo.add(links.toArray(new Node[0]));
        }
        // the links as node indices, in a fixed order on every node (the order decides who gets a channel first)
        int[][] links = new int[nodeList.size()][];
        for (int i = 0; i < links.length; i++) {
            Node[] targets = linkedTo.get(i);
            int[] idx = new int[targets.length];
            int n = 0;
            for (Node t : targets) {
                Integer j = nodeIndex.get(t);
                if (j != null) {
                    idx[n++] = j;
                }
            }
            links[i] = Arrays.copyOf(idx, n);
        }

        // Source sources next to the nodes; the Resonator is not a source for the network, it powers the Chronometers bound to it
        Set<BlockEntity> seenSources = new HashSet<>();
        for (Node node : nodeList) {
            Level lvl = node.level;
            for (Direction dir : Direction.values()) {
                BlockPos p = node.pos.relative(dir);
                if (nodeIndex.containsKey(new Node(lvl, p)) || !lvl.isLoaded(p)) {
                    continue;
                }
                BlockEntity be = lvl.getBlockEntity(p);
                if (be != null && seenSources.add(be)) {
                    if (be instanceof com.golemlogix.blockentity.EternalFrostResonatorBlockEntity) {
                        resonators++;
                        peripherals.put(new Node(lvl, p), be.getBlockState().getBlock());
                        continue;
                    }
                    SourceHelper.Supplier s = SourceHelper.asSupplier(be);
                    if (s != null) {
                        sources.add(s);
                        peripherals.put(new Node(lvl, p), be.getBlockState().getBlock());
                    }
                }
            }
        }

        Node primary = primaryCore(cores);
        if (primary != null) {
            coreLevel = primary.level;
            corePos = primary.pos;
            core = coreLevel.getBlockEntity(corePos) instanceof AtlasCoreBlockEntity c ? c : null;
            if (structure.valid()) {
                lineCapacity = GolemConfig.LINE_CHANNELS.get() + GolemConfig.EXPANDER_BONUS.get() * structure.expanders()
                        + GolemConfig.QUANTUM_BONUS.get() * structure.quantumExpanders();
                channelCapacity = lineCapacity * AtlasStructure.BUSHINGS;
            } else {
                lineCapacity = BARE_CORE_CHANNELS;
                channelCapacity = BARE_CORE_CHANNELS;
            }
            allocateChannels(links, nodeIndex.get(primary));
            // storages and providers without a channel stay silent, like terminals and buses
            bays.removeIf(b -> !activeDevices.contains(new Node(b.getLevel(), b.getBlockPos())));
            externals.removeIf(e -> !activeDevices.contains(new Node(e.level(), e.periscope())));
            providers.removeIf(p -> !activeDevices.contains(new Node(p.getLevel(), p.getBlockPos())));
            catalogs.removeIf(p -> !activeDevices.contains(new Node(p.getLevel(), p.getBlockPos())));
            // two periscopes on one chest must not double its contents
            Set<Node> targets = new HashSet<>();
            externals.removeIf(e -> !targets.add(new Node(e.level(), e.target())));
        }
    }

    /**
     * The core that runs the network when there are several: a complete array before a tower, then the lowest position (by dimension
     * name, then position). The choice does not depend on where the walk started, so every device agrees on it; the other cores stay idle.
     */
    @Nullable
    private Node primaryCore(List<Node> cores) {
        Node best = null;
        Comparator<Node> order = Comparator.<Node, String>comparing(n -> n.level.dimension().location().toString())
                .thenComparing(n -> n.pos);
        for (Node c : cores) {
            AtlasStructure.Result r = AtlasStructure.check(c.level, c.pos);
            boolean better = best == null || (r.valid() && !structure.valid())
                    || (r.valid() == structure.valid() && order.compare(c, best) < 0);
            if (better) {
                best = c;
                structure = r;
            }
        }
        return best;
    }

    /** Two neighbouring nodes connect only if each agrees to link with the other (dir = from from to the neighbour). */
    private static boolean linkable(Level lvl, BlockPos fromPos, BlockState from, BlockPos nextPos, Direction dir) {
        if (!lvl.isLoaded(nextPos)) {
            return false;
        }
        return GolemNode.linked(lvl, fromPos, from, nextPos, lvl.getBlockState(nextPos), dir);
    }

    /** How many channels the outer face of a Channel Expander gives out (only in a complete array). */
    private int expanderExit(ChannelExpanderBlock ex) {
        if (structure == null || !structure.valid()) {
            return 0;
        }
        return ex.isQuantum() ? GolemConfig.QUANTUM_EXIT.get() : GolemConfig.EXPANDER_EXIT.get();
    }

    /** Capacity of a bushing or an expander exit (for tooltips and tests); the same as {@link #capacityAt} for other nodes. */
    public int exitCapacity(BlockPos pos) {
        Block b = level.getBlockState(pos).getBlock();
        if (b instanceof CableBushingBlock) {
            return lineCapacity;
        }
        if (b instanceof ChannelExpanderBlock ex) {
            return expanderExit(ex);
        }
        return Math.max(0, capacityAt(pos));
    }

    /** The node is part of the network (the walk reached it). */
    public boolean contains(BlockPos pos) {
        return nodeIndex.containsKey(node(pos));
    }

    public boolean contains(Level level, BlockPos pos) {
        return nodeIndex.containsKey(new Node(level, pos));
    }

    /** How many nodes the walk found (in every level). */
    public int nodeCount() {
        return nodeList.size();
    }

    /** The node is one of the array cells (relative to the core on top). */
    private boolean inArray(Node n) {
        if (n.level != coreLevel) {
            return false;
        }
        BlockPos pos = n.pos;
        int dy = corePos.getY() - pos.getY();
        return Math.abs(pos.getX() - corePos.getX()) <= 1 && Math.abs(pos.getZ() - corePos.getZ()) <= 1 && dy >= 0 && dy <= 3;
    }

    /**
     * Hands out the channels (see {@link ChannelCalculation}). The controller is the core with the conduits of a complete array,
     * or, in a starter tower, with the conduits joined to it; its neighbours (the bushings, the expanders) are the exits. Channels
     * never pass from one exit to another through the array, so every line counts only its own devices.
     */
    private void allocateChannels(int[][] links, int coreIdx) {
        int n = nodeList.size();
        boolean formed = structure.valid();
        controller = new boolean[n];
        capacities = new int[n];
        int[] tiers = new int[n];
        boolean[] needs = new boolean[n];

        // the controller nodes in walk order from the core: the walk passes all array cells, or only the conduits of a tower
        List<Integer> controllers = new ArrayList<>();
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(coreIdx);
        seen[coreIdx] = true;
        controller[coreIdx] = true;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            if (controller[i]) {
                controllers.add(i);
            }
            for (int j : links[i]) {
                Block b = nodeStates.get(j).getBlock();
                boolean conduit = b instanceof AtlasConduitBlock && nodeList.get(j).level == coreLevel;
                boolean inside = formed ? inArray(nodeList.get(j)) && b instanceof AtlasPart && !(b instanceof AtlasCoreBlock) : conduit;
                if (!seen[j] && inside) {
                    seen[j] = true;
                    controller[j] = conduit;
                    queue.add(j);
                }
            }
        }

        int deviceChannels = GolemConfig.DEVICE_CHANNELS.get();
        for (int i = 0; i < n; i++) {
            BlockState state = nodeStates.get(i);
            Block b = state.getBlock();
            needs[i] = ((GolemNode) b).channelCost(state) > 0;
            if (needs[i]) {
                deviceCount++;
            }
            if (controller[i]) {
                capacities[i] = ChannelCalculation.UNLIMITED;
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else if (b instanceof AtlasCoreBlock) {
                // another core is idle and carries nothing
                capacities[i] = 0;
                tiers[i] = ChannelCalculation.TIER_DEVICE;
            } else if (b instanceof CableBushingBlock || b instanceof AtlasConduitBlock) {
                capacities[i] = lineCapacity;
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else if (b instanceof ChannelExpanderBlock ex) {
                capacities[i] = expanderExit(ex);
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else if (b instanceof SnowPipeBlock pipe) {
                capacities[i] = pipe.channelCapacity();
                tiers[i] = pipe.isDense() ? ChannelCalculation.TIER_DENSE : ChannelCalculation.TIER_PIPE;
            } else if (b instanceof TelegraphBlock) {
                capacities[i] = TelegraphBlock.CHANNELS;
                tiers[i] = ChannelCalculation.TIER_DENSE;
            } else {
                capacities[i] = deviceChannels;
                tiers[i] = ChannelCalculation.TIER_DEVICE;
            }
        }
        channels = new ChannelCalculation(links, capacities, tiers, needs,
                controllers.stream().mapToInt(Integer::intValue).toArray(), channelCapacity);
        channelsUsed = channels.used();
        for (int i = 0; i < n; i++) {
            if (channels.granted(i)) {
                activeDevices.add(nodeList.get(i));
            }
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

    /** The level of the core (another dimension when a telegraph joins this part to the base). */
    @Nullable
    public Level coreLevel() {
        return coreLevel;
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

    /** Telegraph links of the network (each costs Source every second). */
    public int telegraphLinks() {
        return telegraphLinks;
    }

    /** The complete array of the core is still the same as when the network was walked. */
    public boolean structureValid() {
        return structure != null && structure.valid();
    }

    /** How many channels pass through a node now (for example a cable bushing), its own channel included. */
    public int loadAt(BlockPos pos) {
        Integer i = nodeIndex.get(node(pos));
        return i == null || channels == null ? 0 : channels.load(i);
    }

    /** How many channels the node can carry: -1 for the controller (no limit) and outside a network with a core. */
    public int capacityAt(BlockPos pos) {
        Integer i = nodeIndex.get(node(pos));
        return i == null || channels == null || controller[i] ? -1 : capacities[i];
    }

    /** The core of this network is the given block of the walk's level (false for an idle second core). */
    public boolean isPrimaryCore(BlockPos pos) {
        return coreLevel == level && pos.equals(corePos);
    }

    /**
     * Channels of one node, for the tooltip: whether it needs a channel and got one, how many channels pass through it and how many
     * it can carry, and, for a device without a channel, what stopped it (the full node on its route, if that was the reason).
     */
    public record NodeChannels(boolean member, boolean needsChannel, boolean hasChannel, int load, int capacity,
                               ChannelCalculation.Missing missing, @Nullable BlockPos bottleneck) {
    }

    public NodeChannels channelsAt(BlockPos pos) {
        Integer i = nodeIndex.get(node(pos));
        if (i == null) {
            return new NodeChannels(false, false, false, 0, -1, ChannelCalculation.Missing.NONE, null);
        }
        BlockState state = nodeStates.get(i);
        boolean needs = ((GolemNode) state.getBlock()).channelCost(state) > 0;
        if (channels == null) {
            return new NodeChannels(true, needs, false, 0, -1, ChannelCalculation.Missing.NONE, null);
        }
        int b = channels.bottleneck(i);
        return new NodeChannels(true, needs, channels.granted(i), channels.load(i), controller[i] ? -1 : capacities[i],
                channels.missing(i), b >= 0 ? nodeList.get(b).pos : null);
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
        return activeDevices.contains(node(pos));
    }

    public boolean isDeviceActive(Level level, BlockPos pos) {
        return activeDevices.contains(new Node(level, pos));
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
     * so buses with a bigger priority make their move before the others in every period. Computed once per walk (every bus asks
     * every tick: sorting all buses each time cost O(buses² log buses) a tick).
     */
    public int busRank(BusBlockEntity bus) {
        if (busRanks == null) {
            List<BusBlockEntity> sorted = new ArrayList<>(buses);
            sorted.sort(Comparator.comparingInt(BusBlockEntity::getPriority).reversed()
                    .thenComparingLong(b -> b.getBlockPos().asLong()));
            Map<BusBlockEntity, Integer> ranks = new java.util.IdentityHashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                ranks.put(sorted.get(i), i);
            }
            busRanks = ranks;
        }
        return busRanks.getOrDefault(bus, 0);
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
     * a channel, they got one; pipes and other carriers light up when the walk from the core reaches them. The calculator sets its
     * own flag (it means "a job is running").
     */
    public void syncActiveVisuals(boolean active) {
        for (int i = 0; i < nodeList.size(); i++) {
            Node n = nodeList.get(i);
            if (!n.level.isLoaded(n.pos)) {
                continue;
            }
            BlockState state = n.level.getBlockState(n.pos);
            if (!state.hasProperty(GolemProperties.ACTIVE) || !(state.getBlock() instanceof GolemNode node)
                    || state.getBlock() instanceof com.golemlogix.blocks.CryoCalculatorBlock) {
                continue;
            }
            boolean on = active && channels != null && (node.channelCost(state) > 0 ? channels.granted(i) : channels.reached(i));
            if (state.getValue(GolemProperties.ACTIVE) != on) {
                n.level.setBlock(n.pos, state.setValue(GolemProperties.ACTIVE, on), 18);
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

    /** Only the cabinets whose disks really changed are marked (it was every cabinet of the network on every move). */
    private static void markBaysChanged(Set<CopperDriveBayBlockEntity> touched) {
        for (CopperDriveBayBlockEntity bay : touched) {
            bay.setChanged();
            bay.refreshVisuals();
        }
    }

    /** A disk in its cabinet. */
    private record DiskRef(CopperDriveBayBlockEntity bay, ItemStack disk) {
    }

    /** The container skipTarget of the caller's level is the one behind this periscope. */
    private boolean skips(External ex, @Nullable BlockPos skipTarget) {
        return skipTarget != null && ex.level == level && ex.target.equals(skipTarget);
    }

    /**
     * Item handler of the container behind a periscope. First the face the periscope is pressed against, then the "general" one (null)
     * and the other faces: many tanks and chests give access only from some sides.
     */
    @Nullable
    private static IItemHandler itemHandler(External ex) {
        Level lvl = ex.level;
        if (!lvl.isLoaded(ex.target)) {
            return null;
        }
        IItemHandler h = lvl.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, ex.face);
        if (h == null) {
            h = lvl.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, null);
        }
        for (Direction d : Direction.values()) {
            if (h != null) {
                break;
            }
            h = lvl.getCapability(Capabilities.ItemHandler.BLOCK, ex.target, d);
        }
        return h;
    }

    @Nullable
    private static IFluidHandler fluidHandler(External ex) {
        Level lvl = ex.level;
        if (!lvl.isLoaded(ex.target)) {
            return null;
        }
        IFluidHandler h = lvl.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, ex.face);
        if (h == null) {
            h = lvl.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, null);
        }
        for (Direction d : Direction.values()) {
            if (h != null) {
                break;
            }
            h = lvl.getCapability(Capabilities.FluidHandler.BLOCK, ex.target, d);
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

    private static List<DiskRef> disksOf(Tier tier, boolean fluid) {
        List<DiskRef> disks = new ArrayList<>();
        for (CopperDriveBayBlockEntity bay : tier.bays) {
            for (ItemStack disk : bay.getDisks()) {
                if (fluid ? DiskStorage.isFluidDisk(disk) : DiskStorage.isItemDisk(disk)) {
                    disks.add(new DiskRef(bay, disk));
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
            Set<CopperDriveBayBlockEntity> touched = new HashSet<>();
            for (Tier tier : tiers) {
                List<DiskRef> disks = disksOf(tier, false);
                for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                    for (DiskRef d : disks) {
                        if (remaining.isEmpty()) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsItem(d.disk, remaining)) {
                            continue;
                        }
                        int n = DiskStorage.insertItem(d.disk, remaining, false);
                        if (n > 0) {
                            remaining.shrink(n);
                            touched.add(d.bay);
                        }
                    }
                }
                remaining = insertIntoExternals(tier, remaining, false, skipTarget);
                if (remaining.isEmpty()) {
                    break;
                }
            }
            markBaysChanged(touched);
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
            if (skips(ex, skipTarget)) {
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
        Set<CopperDriveBayBlockEntity> touched = new HashSet<>();

        for (Tier tier : tiers(false)) {
            if (left <= 0) {
                break;
            }
            for (DiskRef d : disksOf(tier, false)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractItem(d.disk, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    touched.add(d.bay);
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (skips(ex, skipTarget)) {
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
        if (!simulate) {
            markBaysChanged(touched);
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
        Set<CopperDriveBayBlockEntity> touched = new HashSet<>();
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
                List<DiskRef> disks = disksOf(tier, true);
                for (int pass = 0; pass < 2 && left > 0; pass++) {
                    for (DiskRef d : disks) {
                        if (left <= 0) {
                            break;
                        }
                        if (pass == 0 && !DiskStorage.containsFluid(d.disk, input)) {
                            continue;
                        }
                        int n = DiskStorage.insertFluid(d.disk, input.copyWithAmount(left), false);
                        if (n > 0) {
                            left -= n;
                            touched.add(d.bay);
                        }
                    }
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (skips(ex, skipTarget)) {
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
        markBaysChanged(touched);
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
        Set<CopperDriveBayBlockEntity> touched = new HashSet<>();
        for (Tier tier : tiers(false)) {
            if (left <= 0) {
                break;
            }
            for (DiskRef d : disksOf(tier, true)) {
                if (left <= 0) {
                    break;
                }
                int n = DiskStorage.extractFluid(d.disk, template, left, simulate);
                if (n > 0) {
                    left -= n;
                    touched.add(d.bay);
                }
            }
            for (External ex : tier.exts) {
                if (left <= 0) {
                    break;
                }
                if (skips(ex, skipTarget)) {
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
        if (!simulate) {
            markBaysChanged(touched);
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

    /** Index "item -> the pattern that produces it"; rebuilt when punch cards change (the network itself is remembered longer). */
    public Map<ItemKey, PatternRef> patternIndex() {
        int epoch = patternEpoch;
        if (patternIndex == null || patternIndexEpoch != epoch) {
            patternIndexEpoch = epoch;
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
