package com.golemlogix.upgrade;

/**
 * What a machine accepts: how many slots and which upgrades (and how many speed upgrades). Each slot is for one kind, in this
 * order: the speed upgrades, then the Autocraft Module, the Redstone Module, the Fuzzy Card and the Plasma Temper.
 */
public record UpgradeSpec(int slots, int maxSpeed, boolean crafting, boolean redstone, boolean fuzzy, boolean temper) {
    public UpgradeSpec(int slots, int maxSpeed, boolean crafting, boolean redstone, boolean fuzzy) {
        this(slots, maxSpeed, crafting, redstone, fuzzy, false);
    }

    /** Import bus: up to 4 speed upgrades, redstone and a Fuzzy Card. */
    public static final UpgradeSpec IMPORT_BUS = new UpgradeSpec(6, 4, false, true, true);
    /** Export bus: up to 4 speed upgrades, autocraft, redstone and a Fuzzy Card. */
    public static final UpgradeSpec EXPORT_BUS = new UpgradeSpec(7, 4, true, true, true);
    /** Annihilation Bus: redstone, a Fuzzy Card and the Plasma Temper - its speed is its tool's (and the tool's enchantments). */
    public static final UpgradeSpec ANNIHILATION_BUS = new UpgradeSpec(3, 0, false, true, true, true);
    /** Interface: like the export bus, speed upgrades, stock autocraft, redstone and a Fuzzy Card. */
    public static final UpgradeSpec INTERFACE = new UpgradeSpec(7, 4, true, true, true);
    /** Cryo-Calculator: up to 4 Compressed Co-Processors (speed-up) and redstone. */
    public static final UpgradeSpec CALCULATOR = new UpgradeSpec(5, 4, false, true, false);
    /** Cryo-Mainframe: up to 6 Compressed Co-Processors (each with the mainframe multiplier, for all cores) and redstone. */
    public static final UpgradeSpec MAINFRAME = new UpgradeSpec(7, 6, false, true, false);
    /** Cryo-Distillation Tower: up to 4 Compressed Co-Processors, each one distils faster. */
    public static final UpgradeSpec DISTILLATION = new UpgradeSpec(4, 4, false, false, false);
    /** Cryo-Mixer: up to 4 Compressed Co-Processors (each one adds the base speed) and redstone. */
    public static final UpgradeSpec MIXER = new UpgradeSpec(5, 4, false, true, false);
    /** Periscope: redstone and a Fuzzy Card (for its filter). */
    public static final UpgradeSpec PERISCOPE = new UpgradeSpec(2, 0, false, true, true);
    /** Periscope and Pattern Provider: redstone only. */
    public static final UpgradeSpec REDSTONE_ONLY = new UpgradeSpec(1, 0, false, true, false);

    /** The kind of upgrade slot slot is for. */
    public UpgradeType typeAt(int slot) {
        int k = slot;
        if (k < maxSpeed) {
            return UpgradeType.SPEED;
        }
        k -= maxSpeed;
        for (UpgradeType t : new UpgradeType[]{UpgradeType.CRAFTING, UpgradeType.REDSTONE, UpgradeType.FUZZY, UpgradeType.TEMPER}) {
            if (max(t) > 0 && k-- == 0) {
                return t;
            }
        }
        return null;
    }

    /** The first slot for this kind of upgrade (-1: none). */
    public int slotOf(UpgradeType type) {
        for (int i = 0; i < slots; i++) {
            if (typeAt(i) == type) {
                return i;
            }
        }
        return -1;
    }

    public int max(UpgradeType type) {
        return switch (type) {
            case SPEED -> maxSpeed;
            case CRAFTING -> crafting ? 1 : 0;
            case REDSTONE -> redstone ? 1 : 0;
            case FUZZY -> fuzzy ? 1 : 0;
            case TEMPER -> temper ? 1 : 0;
        };
    }
}
