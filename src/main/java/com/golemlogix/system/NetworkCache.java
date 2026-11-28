package com.golemlogix.system;

import com.golemlogix.blockentity.EternalFrostResonatorBlockEntity;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.LevelEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Per-level network cache, storage/pattern epoch tracking, and end-of-tick invalidation for {@link GolemNetwork}.
 */
final class NetworkCache {
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
    /** Where nodes changed during this tick: the networks there are recomputed at the end of the tick. */
    private static final Map<Level, Set<BlockPos>> CHANGED = new WeakHashMap<>();
    /** The storage epoch when each network's own contents last changed; shared across dimensions by the core's position. */
    private static final Map<GlobalPos, Long> EPOCHS = new HashMap<>();

    /**
     * A network is walked again after this many ticks even without a known change: a safety net for what no event reports. The
     * real changes make it stale at once, so the networks are not rescanned every few ticks any more.
     */
    private static final int CACHE_TTL = 100;

    /**
     * Raised on the server when a node or a peripheral appears, disappears or becomes another block, and on explicit changes (plugs,
     * multiblocks, priorities, telegraph cards): every remembered network is stale then (a telegraph can join levels).
     */
    private static int serverVersion;
    /** Grows whenever items or fluids go into or out of any network's storage (server). Sleeping export buses watch it. */
    private static long storageEpoch;
    /** Raised when the punch cards of a provider or a catalog change: the remembered pattern indexes are rebuilt. */
    private static volatile int patternEpoch;

    private NetworkCache() {
    }

    static long storageEpoch() {
        return storageEpoch;
    }

    static int patternEpoch() {
        return patternEpoch;
    }

    static void patternsChanged() {
        patternEpoch++;
    }

    static void clearCache() {
        serverVersion++;
    }

    static void bumpEpoch(GolemNetwork net) {
        long e = ++storageEpoch;
        net.networkEpoch = e;
        GlobalPos anchor = net.anchor();
        if (anchor != null) {
            if (EPOCHS.size() > 65536) {
                EPOCHS.clear();
            }
            EPOCHS.put(anchor, e);
        }
    }

    static long networkEpoch(GolemNetwork net) {
        GlobalPos anchor = net.anchor();
        if (anchor != null) {
            Long e = EPOCHS.get(anchor);
            if (e != null) {
                return e;
            }
        }
        return net.networkEpoch;
    }

    private static LevelCache cacheOf(Level level) {
        return (level.isClientSide() ? CLIENT_CACHE : SERVER_CACHE).computeIfAbsent(level, l -> new LevelCache());
    }

    private static int versionFor(Level level) {
        return level.isClientSide() ? 0 : serverVersion;
    }

    static GolemNetwork get(Level level, BlockPos start) {
        LevelCache cache = cacheOf(level);
        long now = level.getGameTime();
        CacheEntry entry = cache.byPos.get(start);
        if (entry != null && entry.version == versionFor(level) && now - entry.time < CACHE_TTL && now >= entry.time) {
            return entry.network;
        }
        return remember(level, inherit(level, entry, NetworkScanner.scan(level, start)), now);
    }

    static GolemNetwork refresh(Level level, BlockPos start) {
        CacheEntry before = cacheOf(level).byPos.get(start);
        return remember(level, inherit(level, before, NetworkScanner.scan(level, start)), level.getGameTime());
    }

    @Nullable
    static GolemNetwork aroundRemoved(Level level, BlockPos pos) {
        for (Direction d : GolemNetwork.DIRECTIONS) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n) || !(level.getBlockState(n).getBlock() instanceof GolemNode)) {
                continue;
            }
            GolemNetwork net = NetworkScanner.scan(level, n);
            if (net.isOnline()) {
                return net;
            }
        }
        return null;
    }

    static void nodesChanged(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        serverVersion++;
        CHANGED.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    static void forgetAll() {
        OrderBackoff.forget();
        BusLoad.forget();
        CraftLoad.forget();
        DiskStore.forgetCachedServer();
        EPOCHS.clear();
        SERVER_CACHE.clear();
        CHANGED.clear();
        serverVersion++;
    }

    /**
     * LevelCache values hold GolemNetwork instances which strongly reference their Level key, so WeakHashMap alone cannot
     * collect unloaded levels. Evicting on LevelEvent.Unload prevents leaking worlds across disconnects and dimension switches;
     * remaining server caches also drop their cached networks in case a cross-dimension Telegraph referenced the unloaded level.
     */
    static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level unloaded)) {
            return;
        }
        if (unloaded.isClientSide()) {
            CLIENT_CACHE.remove(unloaded);
        } else {
            SERVER_CACHE.remove(unloaded);
            CHANGED.remove(unloaded);
            for (LevelCache remaining : SERVER_CACHE.values()) {
                remaining.byPos.clear();
            }
            serverVersion++;
        }
    }

    static void onBlockChanged(Level level, BlockPos pos, BlockState state) {
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
        // Keep what really stands there now: without it a block of the same kind put back where its kind stood before
        // (a jar taken out and set again, a pipe rebuilt) looks like no change at all and the networks stay stale for seconds
        if (cache != null) {
            if (now == null) {
                cache.known.remove(pos);
            } else {
                cache.known.put(pos.immutable(), now);
            }
        }
        serverVersion++;
        CHANGED.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    /** End of a server level tick: the networks where nodes changed get their channels and lights recomputed. */
    static void processChanges(Level level) {
        Set<BlockPos> changed = CHANGED.remove(level);
        if (changed == null) {
            return;
        }
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : changed) {
            rescanIfNode(level, pos, done);
            for (Direction d : GolemNetwork.DIRECTIONS) {
                rescanIfNode(level, pos.relative(d), done);
            }
        }
    }

    private static void rescanIfNode(Level level, BlockPos pos, Set<BlockPos> done) {
        if (done.contains(pos) || !level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof GolemNode)) {
            return;
        }
        GolemNetwork net = refresh(level, pos);
        done.add(pos);
        for (GolemNetwork.Node n : net.nodeList) {
            if (n.level() == level) {
                done.add(n.pos());
            }
        }
        net.syncActiveVisuals(net.isOnline());
    }

    /**
     * A new walk of a network keeps the contents epoch of the walk before it: waiting orders compare it with the epoch they failed
     * at, and a new object starting from 0 every 5 seconds made a changed network look unchanged (orders waited the full 30 seconds)
     * and the other way round. After a change to this network's own nodes, storages or peripherals the contents count as changed;
     * a global {@link #serverVersion} bump from an unrelated network must not bump this network's epoch.
     */
    private static GolemNetwork inherit(Level level, @Nullable CacheEntry before, GolemNetwork fresh) {
        if (before != null && !level.isClientSide()) {
            if (before.version == versionFor(level) || sameTopology(before.network, fresh)) {
                fresh.networkEpoch = before.network.networkEpoch();
            } else {
                bumpEpoch(fresh);
            }
        }
        return fresh;
    }

    /** Whether two scans found the same core, nodes (and block types), active devices, storages, crafting nodes, and peripherals. */
    private static boolean sameTopology(GolemNetwork a, GolemNetwork b) {
        if (a.coreLevel != b.coreLevel
                || !Objects.equals(a.corePos, b.corePos)
                || a.nodeList.size() != b.nodeList.size()
                || a.activeDevices.size() != b.activeDevices.size()
                || a.bays.size() != b.bays.size()
                || a.externals.size() != b.externals.size()
                || a.providers.size() != b.providers.size()
                || a.catalogs.size() != b.catalogs.size()
                || a.cpus.size() != b.cpus.size()
                || a.peripherals.size() != b.peripherals.size()
                || a.sources.size() != b.sources.size()
                || a.gigaJars != b.gigaJars
                || a.reactors != b.reactors
                || a.channelCapacity != b.channelCapacity
                || a.telegraphLinks != b.telegraphLinks) {
            return false;
        }
        for (int i = 0; i < a.nodeList.size(); i++) {
            Integer j = b.nodeIndex.get(a.nodeList.get(i));
            if (j == null || a.nodeStates.get(i).getBlock() != b.nodeStates.get(j).getBlock()) {
                return false;
            }
        }
        return a.peripherals.equals(b.peripherals)
                && a.activeDevices.equals(b.activeDevices)
                && sameElements(a.bays, b.bays)
                && sameElements(a.externals, b.externals)
                && sameElements(a.providers, b.providers)
                && sameElements(a.catalogs, b.catalogs)
                && sameElements(a.cpus, b.cpus);
    }

    private static <T> boolean sameElements(List<T> a, List<T> b) {
        int n = a.size();
        if (n != b.size()) {
            return false;
        }
        if (n == 0) {
            return true;
        }
        if (n == 1) {
            return Objects.equals(a.get(0), b.get(0));
        }
        return a.equals(b) || new HashSet<>(a).equals(new HashSet<>(b));
    }

    private static GolemNetwork remember(Level level, GolemNetwork net, long now) {
        LevelCache cache = cacheOf(level);
        if (cache.byPos.size() > 65536) {
            cache.byPos.values().removeIf(c -> now - c.time > 1200 || now < c.time);
        }
        CacheEntry entry = new CacheEntry(net, now, versionFor(level));
        cache.byPos.put(net.start, entry);
        // The walk from any node in this level gives the same network: sibling devices reuse it without walking again
        for (GolemNetwork.Node n : net.nodeList) {
            if (n.level() == level) {
                cache.byPos.put(n.pos(), entry);
            }
        }
        if (!level.isClientSide()) {
            for (int i = 0; i < net.nodeList.size(); i++) {
                GolemNetwork.Node n = net.nodeList.get(i);
                knownOf(n.level()).put(n.pos(), net.nodeStates.get(i).getBlock());
            }
            for (Map.Entry<GolemNetwork.Node, Block> p : net.peripherals.entrySet()) {
                knownOf(p.getKey().level()).put(p.getKey().pos(), p.getValue());
            }
        }
        return net;
    }

    private static Map<BlockPos, Block> knownOf(Level level) {
        LevelCache c = cacheOf(level);
        if (c.known.size() > 262144) {
            // Forget everything once in a long while: the next walks repopulate it
            c.known.clear();
            serverVersion++;
        }
        return c.known;
    }

    /** A block next to a remembered node that the network would use: a Source supplier, a resonator, a container in front of a periscope. */
    private static boolean peripheralCandidate(Level level, BlockPos pos, BlockState state, LevelCache cache) {
        if (!state.hasBlockEntity()) {
            return false;
        }
        boolean nearNode = false;
        boolean faced = false;
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction d : GolemNetwork.DIRECTIONS) {
            neighbor.setWithOffset(pos, d);
            Block b = cache.known.get(neighbor);
            if (b instanceof GolemNode) {
                nearNode = true;
                if (b instanceof PeriscopeStorageBusBlock && level.isLoaded(neighbor)) {
                    BlockState ns = level.getBlockState(neighbor);
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
        return be instanceof EternalFrostResonatorBlockEntity || (be != null && SourceHelper.asSupplier(be) != null);
    }
}
