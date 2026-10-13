package com.golemlogix.blockentity;

import net.minecraft.world.item.ItemStack;

/**
 * Server-side "display" for the client animation of the processors: what to show and until which tick. A short order can
 * start and finish between two state broadcasts (the block entity packet goes out once at the end of a tick, the mainframe sends
 * its summary at most once every 10 ticks), and the client would not see any work at all. The display keeps "working, making X"
 * for at least {@link #TICKS} ticks after the last step seen. It does not affect crafting itself, only what players see.
 */
public final class VisualHold {
    public static final int TICKS = 40;
    private ItemStack item = ItemStack.EMPTY;
    private long until = Long.MIN_VALUE;
    private boolean reported = true;

    /** Mark work on an item (an empty item extends the window without changing what is shown). Returns true if the display has just turned on. */
    public boolean show(ItemStack stack, long now) {
        boolean wasActive = active(now);
        if (!stack.isEmpty()) {
            item = stack.copyWithCount(1);
        }
        until = now + TICKS;
        reported = false;
        return !wasActive;
    }

    public boolean active(long now) {
        return now < until;
    }

    public ItemStack item(long now) {
        return active(now) ? item : ItemStack.EMPTY;
    }

    /** The window has just closed (true once per window): time to tell clients "idle". */
    public boolean ended(long now) {
        if (!reported && !active(now)) {
            reported = true;
            return true;
        }
        return false;
    }
}
