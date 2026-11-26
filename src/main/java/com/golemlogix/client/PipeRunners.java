package com.golemlogix.client;

import com.golemlogix.blocks.SnowPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side dispatch office for pipe runners: there is exactly one snowman (or pumpkin head) per connected pipe component.
 * It walks the component via a closed depth-first Euler tour of its spanning tree so at any moment only one pipe renders it
 * while all others reject in a single hash lookup.
 * <p>
 * The path is continuous across block boundaries (0.5 from centre); at dead ends the runner stops at a station (0.35 horizontal,
 * 0.22 up, 0 down) and turns 180 degrees in place. Trip indices are cumulative across laps so handed-over cargo always matches
 * what was carried. Timestamps stay in {@code long}/{@code double} to prevent float precision jitter in old worlds.
 */
public final class PipeRunners {
    private static final int MAX_PIPES = 192;
    private static final int LIFETIME = 60;
    private static final int MAX_CACHE_ENTRIES = 4096;
    /** Ticks per block of path (integer so route lap length in ticks is exact). */
    private static final int TICKS_PER_BLOCK = 26;
    /** Ticks per trip of an isolated pipe: 48% walking, 52% turning at its station. */
    private static final int LONELY_TRIP = 60;
    private static final float LONELY_WALK = 0.48f;
    private static final Direction[] DIRS = Direction.values();

    private static final Map<BlockPos, Component> CACHE = new HashMap<>();
    private static WeakReference<Level> cachedLevel = new WeakReference<>(null);

    private PipeRunners() {
    }

    /** A connected pipe component and the runner's closed tour over it. */
    private static final class Component {
        long expires;
        BlockPos[] tour = new BlockPos[0];
        /** Length of the closed route in steps; 0 means an isolated single pipe. */
        int period;
        /** Number of station turns per route lap. */
        int turns;
        /** Cumulative turn index before each step within a lap. */
        int[] tripAtStep = new int[0];
        float[] tripProgressAtStep = new float[0];
    }

    /**
     * Runner state inside a pipe: offset from block centre, movement direction (or arrival direction while turning),
     * turn progress ({@code 0..1}, or {@code -1} when running), incoming/outgoing cargo IDs, and trip progress ({@code 0..1}).
     */
    public record Spot(float dx, float dy, float dz, Direction heading, float turnProgress, int incomingCargo, int outgoingCargo, float tripProgress) {
        public boolean isTurning() {
            return turnProgress >= 0f;
        }
    }

    public static void clear() {
        CACHE.clear();
        cachedLevel = new WeakReference<>(null);
    }

    /** Distance from block centre to the dead-end turnaround station in direction {@code d}. */
    private static float stationDist(Direction d) {
        return switch (d) {
            case UP -> 0.22f;
            case DOWN -> 0f;
            default -> 0.35f;
        };
    }

    private static Component getOrBuild(Level level, BlockPos pos) {
        if (cachedLevel.get() != level) {
            CACHE.clear();
            cachedLevel = new WeakReference<>(level);
        }
        long now = level.getGameTime();
        Component comp = CACHE.get(pos);
        if (comp == null || comp.expires < now || comp.expires - now > LIFETIME * 2L) {
            if (CACHE.size() > MAX_CACHE_ENTRIES) {
                CACHE.clear();
            }
            comp = build(level, pos);
            comp.expires = now + LIFETIME;
            for (BlockPos member : comp.tour) {
                CACHE.put(member, comp);
            }
            CACHE.put(pos.immutable(), comp);
        }
        return comp;
    }

    /**
     * Fast culling check for {@link SnowPipeRenderer#shouldRender}: true if the runner can occupy {@code pos} at any
     * {@code partialTick} in {@code [0, 1]} of {@code gameTime}.
     */
    public static boolean mayVisit(Level level, BlockPos pos, long gameTime) {
        Component comp = getOrBuild(level, pos);
        if (comp.period == 0) {
            return true;
        }
        long cycleTicks = (long) TICKS_PER_BLOCK * comp.period;
        long rem = Math.floorMod(gameTime, cycleTicks);
        int i0 = (int) (rem / TICKS_PER_BLOCK);
        if (pos.equals(comp.tour[i0]) || pos.equals(comp.tour[i0 + 1])) {
            return true;
        }
        int i1 = (int) (((rem + 1L) % cycleTicks) / TICKS_PER_BLOCK);
        return i1 != i0 && (pos.equals(comp.tour[i1]) || pos.equals(comp.tour[i1 + 1]));
    }

    /** Runner position in the pipe at {@code pos} at {@code gameTime + partialTick}, or {@code null} if elsewhere. */
    @Nullable
    public static Spot locate(Level level, BlockPos pos, BlockState state, long gameTime, float partialTick) {
        Component comp = getOrBuild(level, pos);
        if (comp.period == 0) {
            return locateLonely(state, gameTime, partialTick);
        }
        long cycleTicks = (long) TICKS_PER_BLOCK * comp.period;
        long cycle = Math.floorDiv(gameTime, cycleTicks);
        double s = (Math.floorMod(gameTime, cycleTicks) + (double) partialTick) / TICKS_PER_BLOCK;
        if (s >= comp.period) {
            s -= comp.period;
            cycle++;
        }
        int i = Math.min(comp.period - 1, (int) s);
        float f = (float) (s - i);
        BlockPos a = comp.tour[i];
        BlockPos b = comp.tour[i + 1];

        int trip = (int) (cycle * comp.turns) + comp.tripAtStep[i];
        int inCargo = trip;
        int outCargo = trip + 1;

        // in-place station turn at a dead end
        if (a.equals(b)) {
            if (!a.equals(pos)) {
                return null;
            }
            BlockPos prev = comp.tour[(i - 1 + comp.period) % comp.period];
            Direction dIn = Direction.fromDelta(a.getX() - prev.getX(), a.getY() - prev.getY(), a.getZ() - prev.getZ());
            if (dIn == null) {
                dIn = Direction.NORTH;
            }
            float sd = stationDist(dIn);
            return new Spot(dIn.getStepX() * sd, dIn.getStepY() * sd, dIn.getStepZ() * sd, dIn, f, inCargo, outCargo, 1f);
        }

        // step between neighbouring pipes a -> b across their shared face
        Direction d = Direction.fromDelta(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
        if (d == null) {
            return null;
        }
        boolean prevWasTurn = comp.tour[(i - 1 + comp.period) % comp.period].equals(a);
        boolean nextIsTurn = comp.tour[(i + 2) % comp.period].equals(b);
        float startDist = prevWasTurn ? -stationDist(d.getOpposite()) : 0f;
        float endDist = nextIsTurn ? stationDist(d) : 0f;
        float distA = 0.5f - startDist;
        float distB = 0.5f + endDist;
        float split = distA / (distA + distB);

        float p0 = comp.tripProgressAtStep[i];
        float p1 = comp.tripProgressAtStep[i + 1];
        if (p1 < p0) {
            p1 = 1f;
        }
        float tripProgress = Mth.lerp(f, p0, p1);

        float dist;
        if (f < split) {
            if (!a.equals(pos)) {
                return null;
            }
            dist = Mth.lerp(f / split, startDist, 0.5f);
        } else {
            if (!b.equals(pos)) {
                return null;
            }
            dist = Mth.lerp((f - split) / (1f - split), -0.5f, endDist);
        }
        return new Spot(d.getStepX() * dist, d.getStepY() * dist, d.getStepZ() * dist, d, -1f, inCargo, outCargo, tripProgress);
    }

    /** Isolated pipe with no neighbouring pipes: walks continuously between its device exits (or blind end) and turns at each. */
    @Nullable
    private static Spot locateLonely(BlockState state, long gameTime, float partialTick) {
        int mask = 0;
        int count = 0;
        Direction first = null;
        for (int i = 0; i < DIRS.length; i++) {
            if (SnowPipeBlock.linked(state, DIRS[i])) {
                mask |= 1 << i;
                if (count == 0) {
                    first = DIRS[i];
                }
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        long k = Math.floorDiv(gameTime, LONELY_TRIP);
        float phase = (float) ((Math.floorMod(gameTime, LONELY_TRIP) + (double) partialTick) / LONELY_TRIP);
        if (phase >= 1f) {
            phase -= 1f;
            k++;
        }
        int n = count == 1 ? 2 : count;
        int fromIdx = (int) Math.floorMod(k, n);
        int toIdx = (int) Math.floorMod(k + 1, n);
        Direction from = count == 1 ? (fromIdx == 0 ? first : first.getOpposite()) : nthDir(mask, fromIdx);
        Direction to = count == 1 ? (toIdx == 0 ? first : first.getOpposite()) : nthDir(mask, toIdx);
        int trip = (int) k;
        if (phase >= LONELY_WALK) {
            float sd = stationDist(to);
            float turn = (phase - LONELY_WALK) / (1f - LONELY_WALK);
            return new Spot(to.getStepX() * sd, to.getStepY() * sd, to.getStepZ() * sd, to, turn, trip + 1, trip + 2, 1f);
        }
        float t = phase / LONELY_WALK;
        float sdFrom = stationDist(from), sdTo = stationDist(to);
        float along = t * (sdFrom + sdTo);
        if (along < sdFrom || sdTo <= 0f) {
            float dist = sdFrom - along;
            return new Spot(from.getStepX() * dist, from.getStepY() * dist, from.getStepZ() * dist, from.getOpposite(), -1f, trip, trip + 1, t);
        }
        float dist = along - sdFrom;
        return new Spot(to.getStepX() * dist, to.getStepY() * dist, to.getStepZ() * dist, to, -1f, trip, trip + 1, t);
    }

    private static Direction nthDir(int mask, int index) {
        int seen = 0;
        for (int i = 0; i < DIRS.length; i++) {
            if ((mask & (1 << i)) != 0) {
                if (seen == index) {
                    return DIRS[i];
                }
                seen++;
            }
        }
        return Direction.NORTH;
    }

    // ------------------------------------------------------------------------------------- graph & tour building

    private static Component build(Level level, BlockPos origin) {
        BlockPos root = origin.immutable();
        Map<BlockPos, EnumMap<Direction, BlockPos>> graph = collectPipeGraph(level, root);
        Component comp = new Component();
        if (graph.size() <= 1) {
            comp.tour = new BlockPos[]{root};
            return comp;
        }
        comp.tour = buildClosedTour(graph, selectStart(graph));
        comp.period = comp.tour.length - 1;
        indexTrips(comp);
        return comp;
    }

    /** Flood-fills up to {@link #MAX_PIPES} mutually linked snow pipes reachable from {@code root}. */
    private static Map<BlockPos, EnumMap<Direction, BlockPos>> collectPipeGraph(Level level, BlockPos root) {
        Map<BlockPos, EnumMap<Direction, BlockPos>> graph = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(root);
        graph.put(root, new EnumMap<>(Direction.class));
        while (!queue.isEmpty() && graph.size() < MAX_PIPES) {
            BlockPos p = queue.poll();
            BlockState ps = level.getBlockState(p);
            if (!(ps.getBlock() instanceof SnowPipeBlock)) {
                continue;
            }
            for (Direction dir : DIRS) {
                if (SnowPipeBlock.link(ps, dir) != SnowPipeBlock.Link.PIPE) {
                    continue;
                }
                BlockPos n = p.relative(dir);
                BlockState ns = level.getBlockState(n);
                if (!(ns.getBlock() instanceof SnowPipeBlock) || SnowPipeBlock.link(ns, dir.getOpposite()) != SnowPipeBlock.Link.PIPE) {
                    continue;
                }
                if (!graph.containsKey(n)) {
                    if (graph.size() >= MAX_PIPES) {
                        continue;
                    }
                    graph.put(n, new EnumMap<>(Direction.class));
                    queue.add(n);
                }
                graph.get(p).put(dir, n);
                graph.get(n).put(dir.getOpposite(), p);
            }
        }
        return graph;
    }

    /** Picks a deterministic tour origin: the lowest-coordinate dead end, or lowest-coordinate pipe in a loop. */
    private static BlockPos selectStart(Map<BlockPos, EnumMap<Direction, BlockPos>> graph) {
        BlockPos start = null;
        BlockPos any = null;
        for (BlockPos p : graph.keySet()) {
            if (any == null || p.asLong() < any.asLong()) {
                any = p;
            }
            if (graph.get(p).size() == 1 && (start == null || p.asLong() < start.asLong())) {
                start = p;
            }
        }
        return start != null ? start : any;
    }

    /** Builds the closed Euler tour with in-place turnaround steps at reversals, rotated so step 0 starts on a turn. */
    private static BlockPos[] buildClosedTour(Map<BlockPos, EnumMap<Direction, BlockPos>> graph, BlockPos start) {
        List<BlockPos> raw = new ArrayList<>(graph.size() * 2);
        dfs(start, graph, new HashSet<>(), raw);
        int n = raw.size() - 1;
        List<BlockPos> cyc = new ArrayList<>(raw.size() * 2);
        for (int k = 0; k < n; k++) {
            BlockPos here = raw.get(k);
            cyc.add(here);
            if (raw.get((k - 1 + n) % n).equals(raw.get((k + 1) % n))) {
                cyc.add(here);
            }
        }
        int first = 0;
        for (int k = 0; k + 1 < cyc.size(); k++) {
            if (cyc.get(k).equals(cyc.get(k + 1))) {
                first = k;
                break;
            }
        }
        List<BlockPos> tour = new ArrayList<>(cyc.size() + 1);
        for (int k = 0; k < cyc.size(); k++) {
            tour.add(cyc.get((first + k) % cyc.size()));
        }
        tour.add(tour.get(0));
        return tour.toArray(new BlockPos[0]);
    }

    /** Precomputes trip index and normalized trip progress {@code [0, 1]} for every step of the closed tour. */
    private static void indexTrips(Component comp) {
        comp.tripAtStep = new int[comp.tour.length];
        comp.tripProgressAtStep = new float[comp.tour.length];

        List<Integer> turnSteps = new ArrayList<>();
        for (int k = 0; k < comp.period; k++) {
            if (comp.tour[k].equals(comp.tour[k + 1])) {
                turnSteps.add(k);
            }
        }
        if (turnSteps.isEmpty()) {
            turnSteps.add(0);
        }
        int m = turnSteps.size();
        comp.turns = m;
        for (int ti = 0; ti < m; ti++) {
            int tCurr = turnSteps.get(ti);
            int tNext = ti + 1 < m ? turnSteps.get(ti + 1) : comp.period;
            comp.tripAtStep[tCurr] = ti;
            comp.tripProgressAtStep[tCurr] = 1f;
            int walkSteps = tNext - tCurr - 1;
            for (int s = 0; s < walkSteps; s++) {
                comp.tripAtStep[tCurr + 1 + s] = ti;
                comp.tripProgressAtStep[tCurr + 1 + s] = (float) s / walkSteps;
            }
        }
        comp.tripAtStep[comp.period] = comp.tripAtStep[0];
        comp.tripProgressAtStep[comp.period] = 0f;
    }

    /** Depth-first walk: appends a closed lap over the spanning tree traversing each edge twice. */
    private static void dfs(BlockPos p, Map<BlockPos, EnumMap<Direction, BlockPos>> graph, Set<BlockPos> seen, List<BlockPos> tour) {
        seen.add(p);
        tour.add(p);
        for (BlockPos n : graph.get(p).values()) {
            if (!seen.contains(n)) {
                dfs(n, graph, seen, tour);
                tour.add(p);
            }
        }
    }
}
