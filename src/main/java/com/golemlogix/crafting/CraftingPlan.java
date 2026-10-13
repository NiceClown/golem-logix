package com.golemlogix.crafting;

import com.golemlogix.data.PatternData;
import com.golemlogix.system.ItemKey;

import java.util.List;
import java.util.Map;

/**
 * Result of autocraft planning.
 *
 * @param targets     what was ordered and how much (usually one item; a precraft from JEI orders several at once)
 * @param fromStorage how many of which items to take from network storage
 * @param steps       steps in dependency order (first what the following ones need)
 * @param missing     what is missing (not in storage and no pattern)
 */
public record CraftingPlan(Map<ItemKey, Long> targets, Map<ItemKey, Long> fromStorage, List<Step> steps,
                           Map<ItemKey, Long> missing) {

    public record Step(PatternData pattern, long times) {
    }

    /** The first (main) ordered item. */
    public ItemKey target() {
        return targets.keySet().iterator().next();
    }

    /** How much of the main item was ordered. */
    public long amount() {
        return targets.values().iterator().next();
    }

    public boolean isOk() {
        return missing.isEmpty() && !steps.isEmpty();
    }

    public long totalExecutions() {
        long n = 0;
        for (Step s : steps) {
            n += s.times();
        }
        return n;
    }
}
