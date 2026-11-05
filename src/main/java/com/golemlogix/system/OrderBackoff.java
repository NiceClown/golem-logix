package com.golemlogix.system;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Automatic orders that fail for lack of ingredients wait before they are planned again: 1 second, then 2, 4 and so on up to 30. A
 * device that cannot get what it wants must not plan every moment for ever. When the network's contents have changed since, the wait
 * is at most 2 seconds, so an order goes through soon after its ingredients arrive, and a busy network cannot make it plan constantly.
 */
public final class OrderBackoff {
    private static final int FIRST = 20, MAX = 600, AFTER_CHANGE = 40, PRUNE_AT = 4096;

    private record Order(BlockPos device, Set<ItemKey> targets) {
    }

    private static final class Wait {
        long failedAt;
        int ticks = FIRST;
        long epoch;
    }

    private static final Map<Order, Wait> WAITS = new HashMap<>();

    private OrderBackoff() {
    }

    /** Whether this order must not be planned yet. */
    public static boolean waiting(BlockPos device, Set<ItemKey> targets, long now, long epoch) {
        Wait w = WAITS.get(new Order(device, targets));
        if (w == null) {
            return false;
        }
        long pause = w.epoch == epoch ? w.ticks : Math.min(w.ticks, AFTER_CHANGE);
        return now >= w.failedAt && now - w.failedAt < pause;
    }

    public static void failed(BlockPos device, Set<ItemKey> targets, long now, long epoch) {
        if (WAITS.size() >= PRUNE_AT) {
            WAITS.values().removeIf(w -> now - w.failedAt > 2L * MAX || now < w.failedAt);
        }
        Order order = new Order(device.immutable(), Set.copyOf(targets));
        Wait w = WAITS.get(order);
        if (w == null) {
            w = new Wait();
            WAITS.put(order, w);
        } else {
            w.ticks = Math.min(MAX, w.ticks * 2);
        }
        w.failedAt = now;
        w.epoch = epoch;
    }

    public static void succeeded(BlockPos device, Set<ItemKey> targets) {
        if (!WAITS.isEmpty()) {
            WAITS.remove(new Order(device, targets));
        }
    }

    /** The server stopped. */
    public static void forget() {
        WAITS.clear();
    }
}
