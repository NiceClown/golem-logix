package com.golemlogix.upgrade;

/**
 * Redstone Module mode, like the AE2 redstone card and the Refined Storage "redstone mode".
 */
public enum RedstoneMode {
    /** The signal does not matter. */
    IGNORE,
    /** Works while there is a signal. */
    HIGH,
    /** Works while there is no signal. */
    LOW,
    /** One action per pulse (signal edge). */
    PULSE;

    public static RedstoneMode byId(int id) {
        RedstoneMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /** The next mode in a cycle; without pulse for storages, where "one action" makes no sense. */
    public RedstoneMode next(boolean allowPulse) {
        RedstoneMode n = byId(ordinal() + 1);
        return !allowPulse && n == PULSE ? IGNORE : n;
    }

    public String key() {
        return "gui.golem_logix.redstone." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
