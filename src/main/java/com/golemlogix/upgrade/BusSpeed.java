package com.golemlogix.upgrade;

import com.golemlogix.config.GolemConfig;

/**
 * How fast Compressed Co-Processors make a bus. The rate grows in a straight line with every upgrade: four give 20 items a tick, about
 * what a fully sped-up AE2 bus does, and the 28 a bus can hold give 128. An upgraded bus does not work more often than every 5 ticks, it
 * moves more at a time: the same throughput with a fifth of the operations of working every tick, which is what spares the server.
 */
public final class BusSpeed {
    /** The busiest a bus gets (ticks between operations). */
    private static final int FASTEST_INTERVAL = 5;
    private static final double ITEMS_PER_UPGRADE = 2.25, FLUID_PER_UPGRADE = 4.75;

    private BusSpeed() {
    }

    public static int interval(int upgrades) {
        int base = GolemConfig.BUS_INTERVAL_TICKS.get();
        return upgrades == 0 ? base : Math.min(base, FASTEST_INTERVAL);
    }

    public static int items(int upgrades) {
        return amount(GolemConfig.BUS_ITEMS_PER_OPERATION.get(), 1 + ITEMS_PER_UPGRADE * upgrades, upgrades);
    }

    public static int fluid(int upgrades) {
        return amount(GolemConfig.BUS_FLUID_PER_OPERATION.get(), 1 + FLUID_PER_UPGRADE * upgrades, upgrades);
    }

    /** What the base rate times this multiplier comes to over one interval. */
    private static int amount(int perBaseInterval, double multiplier, int upgrades) {
        double perTick = (double) perBaseInterval / GolemConfig.BUS_INTERVAL_TICKS.get();
        return Math.max(1, (int) Math.round(perTick * multiplier * interval(upgrades)));
    }
}
