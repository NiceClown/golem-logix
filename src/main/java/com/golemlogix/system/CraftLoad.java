package com.golemlogix.system;

import com.golemlogix.config.GolemConfig;
import net.minecraft.world.level.Level;

/**
 * The server's limit on autocrafting: how long all jobs together may work in one tick (config craftMaxMillisPerTick). A job over
 * the limit goes on in its next cycle; every job still does at least one step each cycle, so none starves. However many calculators
 * and mainframe cores a network has, autocrafting cannot make the server lag past this.
 */
public final class CraftLoad {
    private static long tick = -1;
    private static long spentThisTick;

    private CraftLoad() {
    }

    private static void roll(Level level) {
        long now = level.getServer().getTickCount();
        if (now != tick) {
            tick = now;
            spentThisTick = 0;
        }
    }

    /** How many nanoseconds of this tick are left (Long.MAX_VALUE without a limit); not above zero when they are used up. */
    public static long nanosLeft(Level level) {
        roll(level);
        int limit = GolemConfig.CRAFT_MAX_MILLIS_PER_TICK.get();
        return limit <= 0 ? Long.MAX_VALUE : limit * 1_000_000L - spentThisTick;
    }

    public static void ran(long nanos) {
        spentThisTick += nanos;
    }
}
