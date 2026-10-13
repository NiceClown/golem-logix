package com.golemlogix.upgrade;

import net.minecraft.nbt.CompoundTag;

/**
 * Redstone control state of one machine: the selected mode, the previous signal level and an "accumulated" pulse.
 * Without a Redstone Module the machine ignores the signal.
 */
public final class RedstoneControl {
    private RedstoneMode mode = RedstoneMode.HIGH;
    private boolean lastPowered;
    private boolean pulse;

    public RedstoneMode mode() {
        return mode;
    }

    public void setMode(RedstoneMode mode) {
        this.mode = mode;
        this.pulse = false;
    }

    /** Record the current signal level (once a tick or on every check): a signal edge accumulates a pulse. */
    public void observe(boolean powered) {
        if (powered && !lastPowered) {
            pulse = true;
        }
        lastPowered = powered;
    }

    /**
     * Whether the machine may work now. installed = whether a Redstone Module is installed; consume = in pulse mode,
     * use up the pulse (true where the machine really performs an action).
     */
    public boolean allows(boolean installed, boolean powered, boolean consume) {
        observe(powered);
        if (!installed) {
            return true;
        }
        return switch (mode) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
            case PULSE -> {
                boolean ok = pulse;
                if (ok && consume) {
                    pulse = false;
                }
                yield ok;
            }
        };
    }

    public void save(CompoundTag tag) {
        tag.putInt("RedstoneMode", mode.ordinal());
        tag.putBoolean("RedstoneLast", lastPowered);
    }

    public void load(CompoundTag tag) {
        mode = tag.contains("RedstoneMode") ? RedstoneMode.byId(tag.getInt("RedstoneMode")) : RedstoneMode.HIGH;
        lastPowered = tag.getBoolean("RedstoneLast");
        pulse = false;
    }
}
