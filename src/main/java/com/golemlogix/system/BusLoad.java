package com.golemlogix.system;

import com.golemlogix.config.GolemConfig;
import net.minecraft.world.level.Level;

/**
 * The server's limit on buses: how long they may work in one tick (config busMaxMillisPerTick). A bus over the limit waits for the next
 * tick; it loses no throughput, because a bus moves as much as the ticks since its last operation allow, and it cannot be put off for
 * long, so no bus starves. Also the figures the /golemlogix_buses command shows.
 */
public final class BusLoad {
    /** How many ticks in a row one bus can be put off. */
    public static final int MAX_DEFERRALS = 8;
    private static final int WINDOW_TICKS = 100;

    private static long tick = -1;
    private static long spentThisTick;
    private static long windowStart;
    private static long windowOps, windowNanos, windowDeferred, windowWorst;
    private static long lastOps, lastNanos, lastDeferred, lastWorst;

    private BusLoad() {
    }

    private static void roll(Level level) {
        var server = level.getServer();
        if (server == null) {
            return;
        }
        long now = server.getTickCount();
        if (now == tick) {
            return;
        }
        windowWorst = Math.max(windowWorst, spentThisTick);
        tick = now;
        spentThisTick = 0;
        if (now < windowStart) {
            windowOps = windowNanos = windowDeferred = windowWorst = 0;
            windowStart = now;
        } else if (now - windowStart >= WINDOW_TICKS) {
            lastOps = windowOps;
            lastNanos = windowNanos;
            lastDeferred = windowDeferred;
            lastWorst = windowWorst;
            windowOps = windowNanos = windowDeferred = windowWorst = 0;
            windowStart = now;
        }
    }

    /** Whether the buses have used up this tick's time. */
    public static boolean overBudget(Level level) {
        roll(level);
        int limit = GolemConfig.BUS_MAX_MILLIS_PER_TICK.get();
        return limit > 0 && spentThisTick >= limit * 1_000_000L;
    }

    public static void deferred() {
        windowDeferred++;
    }

    public static void ran(Level level, long nanos) {
        roll(level);
        spentThisTick += nanos;
        windowOps++;
        windowNanos += nanos;
    }

    /** The server stopped: clear the previous world's window so the next one does not show stale figures. */
    public static void forget() {
        tick = -1;
        spentThisTick = 0;
        windowStart = 0;
        windowOps = windowNanos = windowDeferred = windowWorst = 0;
        lastOps = lastNanos = lastDeferred = lastWorst = 0;
    }

    /** Operations a second over the last few seconds. */
    public static long opsPerSecond() {
        return lastOps * 20 / WINDOW_TICKS;
    }

    /** Average milliseconds a tick the buses took. */
    public static double millisPerTick() {
        return lastNanos / 1e6 / WINDOW_TICKS;
    }

    public static double worstMillis() {
        return lastWorst / 1e6;
    }

    public static long deferredPerSecond() {
        return lastDeferred * 20 / WINDOW_TICKS;
    }

    /** How much of a tick that is, in percent of the 50 ms there are. */
    public static double percentOfTick() {
        return millisPerTick() * 2;
    }
}
