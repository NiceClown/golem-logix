package com.golemlogix.client;

import com.golemlogix.blocks.SnowPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Клиентская «диспетчерская» бегунков: на всю связную сеть труб приходится ровно один снеговичок (или его голова).
 * Он обходит все трубы по обходу дерева в глубину (туда и обратно), поэтому в каждый момент рисует его
 * только одна труба — остальные тратят на проверку один поиск в хэш-таблице.
 * <p>
 * Путь непрерывен: на стыке блоков бегунок ровно на границе (0.5 от центра) — его дорисовывает соседняя труба с той же
 * точки; в тупике он останавливается на «станции» (0.35 от центра, по вертикали 0.22 вверх и 0 вниз) и там же
 * разворачивается, отрисовщик крутит его на 180° за время разворота. Номер рейса сквозной (не обнуляется на круге
 * маршрута), поэтому сданный груз — ровно тот, что бегунок нёс. Время считается в long/double: в старых мирах игровое
 * время большое, и float терял доли тика — бег шёл рывками.
 */
public final class PipeRunners {
    private static final int MAX_PIPES = 192;
    private static final int LIFETIME = 60;
    /** Тиков на один блок пути (целое: длина круга маршрута в тиках тоже целая). */
    private static final int TICKS_PER_BLOCK = 26;
    /** Тиков на один рейс одинокой трубы: 48% — бег, 52% — разворот на станции. */
    private static final int LONELY_TRIP = 60;
    private static final float LONELY_WALK = 0.48f;

    private static final Map<BlockPos, Component> CACHE = new HashMap<>();

    private PipeRunners() {
    }

    /** Связная группа труб и маршрут бегунка по ней. */
    private static final class Component {
        long expires;
        BlockPos[] tour = new BlockPos[0];
        /** Длина замкнутого маршрута (число шагов); 0 — одинокая труба. */
        int period;
        /** Сколько разворотов за круг маршрута. */
        int turns;
        /** Номер последнего разворота перед шагом (в пределах круга). */
        int[] tripAtStep = new int[0];
        float[] tripProgressAtStep = new float[0];
    }

    /**
     * Где в этой трубе сейчас бегунок: смещение от центра блока, направление движения (на развороте — куда он смотрел,
     * когда пришёл), прогресс разворота (0..1 или -1), номера сдаваемого/нового груза и прогресс рейса (0..1).
     */
    public record Spot(float dx, float dy, float dz, Direction heading, float turnProgress, int incomingCargo, int outgoingCargo, float tripProgress) {
        public boolean isTurning() {
            return turnProgress >= 0f;
        }
    }

    public static void clear() {
        CACHE.clear();
    }

    /** Насколько далеко от центра стоит станция в тупике направления d. */
    private static float stationDist(Direction d) {
        return switch (d) {
            case UP -> 0.22f;
            case DOWN -> 0f;
            default -> 0.35f;
        };
    }

    /** Положение бегунка в трубе pos в момент gameTime + partialTick или null, если он сейчас в другой трубе. */
    public static Spot locate(Level level, BlockPos pos, BlockState state, long gameTime, float partialTick) {
        long now = level.getGameTime();
        Component comp = CACHE.get(pos);
        if (comp == null || comp.expires < now || comp.expires - now > LIFETIME * 2L) {
            if (CACHE.size() > 4096) {
                CACHE.clear();
            }
            comp = build(level, pos);
            comp.expires = now + LIFETIME;
            for (BlockPos member : comp.tour) {
                CACHE.put(member, comp);
            }
            CACHE.put(pos.immutable(), comp);
        }
        if (comp.period == 0) {
            return lonely(state, gameTime, partialTick);
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

        // разворот на станции в тупике: стоит на месте, смотрит туда, откуда пришёл (отрисовщик крутит его на 180°)
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

        // шаг между соседними трубами a -> b: из станции (или центра) a до границы, от границы до центра (или станции) b
        Direction d = Direction.fromDelta(b.getX() - a.getX(), b.getY() - a.getY(), b.getZ() - a.getZ());
        if (d == null) {
            return null;
        }
        boolean prevWasTurn = comp.tour[(i - 1 + comp.period) % comp.period].equals(a);
        boolean nextIsTurn = comp.tour[(i + 2) % comp.period].equals(b);
        // станция в a лежит в направлении, обратном движению
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

    /**
     * Труба без соседних труб: бегунок ходит от одного её выхода к другому через центр (у трубы с одним выходом — от выхода
     * к глухому концу), на каждой станции спокойно разворачивается. Путь непрерывен.
     */
    private static Spot lonely(BlockState state, long gameTime, float partialTick) {
        List<Direction> stations = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            if (SnowPipeBlock.linked(state, dir)) {
                stations.add(dir);
            }
        }
        if (stations.isEmpty()) {
            return null;
        }
        if (stations.size() == 1) {
            stations.add(stations.get(0).getOpposite());
        }
        long k = Math.floorDiv(gameTime, LONELY_TRIP);
        float phase = (float) ((Math.floorMod(gameTime, LONELY_TRIP) + (double) partialTick) / LONELY_TRIP);
        if (phase >= 1f) {
            phase -= 1f;
            k++;
        }
        int n = stations.size();
        Direction from = stations.get((int) Math.floorMod(k, n));
        Direction to = stations.get((int) Math.floorMod(k + 1, n));
        int trip = (int) k;
        if (phase >= LONELY_WALK) {
            // разворот на станции «to»: это следующий разворот, его груз сдаётся и берётся новый
            float sd = stationDist(to);
            float turn = (phase - LONELY_WALK) / (1f - LONELY_WALK);
            return new Spot(to.getStepX() * sd, to.getStepY() * sd, to.getStepZ() * sd, to, turn, trip + 1, trip + 2, 1f);
        }
        float t = phase / LONELY_WALK;
        float sdFrom = stationDist(from), sdTo = stationDist(to);
        float total = sdFrom + sdTo;
        float along = t * total;
        if (along < sdFrom || sdTo <= 0f) {
            // первая половина: от станции «from» к центру
            float dist = sdFrom - along;
            Direction heading = from.getOpposite();
            return new Spot(from.getStepX() * dist, from.getStepY() * dist, from.getStepZ() * dist, heading, -1f, trip, trip + 1, t);
        }
        float dist = along - sdFrom;
        return new Spot(to.getStepX() * dist, to.getStepY() * dist, to.getStepZ() * dist, to, -1f, trip, trip + 1, t);
    }

    private static Component build(Level level, BlockPos origin) {
        Map<BlockPos, EnumMap<Direction, BlockPos>> graph = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        graph.put(origin.immutable(), new EnumMap<>(Direction.class));
        while (!queue.isEmpty() && graph.size() < MAX_PIPES) {
            BlockPos p = queue.poll();
            BlockState ps = level.getBlockState(p);
            if (!(ps.getBlock() instanceof SnowPipeBlock)) {
                continue;
            }
            for (Direction dir : Direction.values()) {
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
        Component comp = new Component();
        if (graph.size() <= 1) {
            comp.tour = new BlockPos[]{origin.immutable()};
            return comp;
        }
        // старт — тупик с наименьшими координатами (или просто наименьшая труба)
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
        if (start == null) {
            start = any;
        }
        List<BlockPos> raw = new ArrayList<>(graph.size() * 2);
        dfs(start, graph, new HashSet<>(), raw);
        // круг маршрута raw[0..n-1] (raw[n] == raw[0]); где путь поворачивает назад — шаг разворота «на месте»
        int n = raw.size() - 1;
        List<BlockPos> cyc = new ArrayList<>(raw.size() * 2);
        for (int k = 0; k < n; k++) {
            BlockPos here = raw.get(k);
            cyc.add(here);
            if (raw.get((k - 1 + n) % n).equals(raw.get((k + 1) % n))) {
                cyc.add(here);
            }
        }
        // круг начинается с разворота: тогда рейс никогда не переходит через начало круга
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
        comp.tour = tour.toArray(new BlockPos[0]);
        comp.period = comp.tour.length - 1;
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
        return comp;
    }

    /** Обход в глубину: tour — круг по дереву (каждое ребро дважды), начинается и кончается в p. */
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
