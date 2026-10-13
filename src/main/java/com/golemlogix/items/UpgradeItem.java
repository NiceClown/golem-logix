package com.golemlogix.items;

import com.golemlogix.upgrade.UpgradeType;

/** Апгрейд машины: кладётся в ячейки апгрейдов (у Насыщателя — ПКМ по машине). */
public class UpgradeItem extends ModItemWithTooltip {
    private final UpgradeType type;

    public UpgradeItem(UpgradeType type, String tooltipKey, Properties properties) {
        super(tooltipKey, properties);
        this.type = type;
    }

    public UpgradeType type() {
        return type;
    }
}
