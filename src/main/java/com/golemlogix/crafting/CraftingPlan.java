package com.golemlogix.crafting;

import com.golemlogix.data.PatternData;
import com.golemlogix.system.ItemKey;

import java.util.List;
import java.util.Map;

/**
 * Результат планирования автокрафта.
 *
 * @param targets     что заказано и сколько (обычно один предмет; прекрафт из JEI заказывает сразу несколько)
 * @param fromStorage сколько каких предметов нужно взять из хранилища сети
 * @param steps       шаги в порядке зависимостей (сначала то, что нужно для следующих)
 * @param missing     чего не хватает (нет в хранилище и нет шаблона)
 */
public record CraftingPlan(Map<ItemKey, Long> targets, Map<ItemKey, Long> fromStorage, List<Step> steps,
                           Map<ItemKey, Long> missing) {

    public record Step(PatternData pattern, long times) {
    }

    /** Первый (главный) заказанный предмет. */
    public ItemKey target() {
        return targets.keySet().iterator().next();
    }

    /** Сколько заказано главного предмета. */
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
