package com.golemlogix.crafting;

import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Autocraft processor: one job at a time. It is a standalone Cryo-Calculator or one of the Cryo-Mainframe cores
 * (all mainframe cores share one block, the core number tells them apart).
 */
public interface CraftingCpu {
    @Nullable
    CraftingJob getJob();

    default boolean isIdle() {
        return getJob() == null;
    }

    void startJob(CraftingJob job);

    /** Cancel: everything in the job goes back to the network. */
    void cancelJob();

    /** How many of an item the job is waiting for from machines. */
    long wants(ItemKey key);

    /** Accept a machine's result: returns what the job does not need. */
    ItemStack absorb(ItemStack stack);

    /** The processor block was removed from the world (or the core disappeared together with its calculator in the mainframe). */
    boolean isRemoved();

    /** The block the processor stands in (for the channel, the monitor and tooltips). */
    BlockPos cpuPos();

    /** Core number in the mainframe (1..), 0 for a standalone calculator. */
    default int core() {
        return 0;
    }

    /** How many pattern steps per cycle. */
    int getWorkers();

    /** Number of speed-ups shown in the monitor (placed coprocessors; for the mainframe, compressed ones with the multiplier). */
    int getCoprocessors();

    /** Whether it can take a job now: the block has a channel in this network. */
    default boolean canRun(GolemNetwork net) {
        return net.isDeviceActive(cpuPos());
    }
}
