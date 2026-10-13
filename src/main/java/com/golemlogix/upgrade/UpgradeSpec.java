package com.golemlogix.upgrade;

/**
 * What a machine accepts: how many slots and which upgrades (and how many speed upgrades).
 */
public record UpgradeSpec(int slots, int maxSpeed, boolean crafting, boolean redstone) {
    /** Import bus: up to 4 speed upgrades and redstone. */
    public static final UpgradeSpec IMPORT_BUS = new UpgradeSpec(6, 4, false, true);
    /** Export bus: up to 4 speed upgrades, autocraft and redstone. */
    public static final UpgradeSpec EXPORT_BUS = new UpgradeSpec(6, 4, true, true);
    /** Interface: like the export bus, speed upgrades, stock autocraft and redstone. */
    public static final UpgradeSpec INTERFACE = new UpgradeSpec(6, 4, true, true);
    /** Cryo-Calculator: up to 4 Compressed Co-Processors (speed-up) and redstone. */
    public static final UpgradeSpec CALCULATOR = new UpgradeSpec(5, 4, false, true);
    /** Cryo-Mainframe: up to 6 Compressed Co-Processors (each with the mainframe multiplier, for all cores) and redstone. */
    public static final UpgradeSpec MAINFRAME = new UpgradeSpec(7, 6, false, true);
    /** Periscope and Pattern Provider: redstone only. */
    public static final UpgradeSpec REDSTONE_ONLY = new UpgradeSpec(1, 0, false, true);

    public int max(UpgradeType type) {
        return switch (type) {
            case SPEED -> maxSpeed;
            case CRAFTING -> crafting ? 1 : 0;
            case REDSTONE -> redstone ? 1 : 0;
        };
    }
}
