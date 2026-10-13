package com.golemlogix.items;

import com.golemlogix.upgrade.UpgradeType;

/** A machine upgrade: goes into the upgrade slots (for the Saturator, right-click on the machine). */
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
