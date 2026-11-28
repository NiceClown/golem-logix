package com.golemlogix.crafting;

import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
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
    default ItemStack absorb(ItemStack stack) {
        return absorb(stack, null);
    }

    /** Accept a machine's result, settling {@code provider}'s own waiting batch first when known. */
    ItemStack absorb(ItemStack stack, @Nullable GlobalPos provider);

    /** Accept a fluid a machine gives back: returns the mB the job took. */
    default int absorbFluid(FluidStack fluid) {
        return absorbFluid(fluid, null);
    }

    /** Accept a fluid a machine gives back, settling {@code provider}'s own waiting batch first when known. */
    default int absorbFluid(FluidStack fluid, @Nullable GlobalPos provider) {
        CraftingJob job = getJob();
        return job == null ? 0 : job.absorbFluid(fluid, provider);
    }

    /** The processor block was removed from the world (or the core disappeared together with its calculator in the mainframe). */
    boolean isRemoved();

    /** The block the processor stands in (for the channel, the monitor and tooltips). */
    BlockPos cpuPos();

    /** The level the processor stands in (may differ from the network's walk level across a Telegraph). */
    @Nullable
    Level cpuLevel();

    /** Core number in the mainframe (1..), 0 for a standalone calculator. */
    default int core() {
        return 0;
    }

    /** How many pattern steps per cycle. */
    int getWorkers();

    /** Number of speed-ups shown in the monitor (placed coprocessors; for the mainframe, compressed ones with the multiplier). */
    int getCoprocessors();

    /** Idle processors may lend this one's job their steps. One under a Redstone Module keeps the pace its signal sets. */
    default boolean acceptsHelp() {
        return true;
    }

    /** Whether it can take a job now: the block has a channel in this network. */
    default boolean canRun(GolemNetwork net) {
        Level l = cpuLevel();
        return l != null && net.isDeviceActive(l, cpuPos());
    }
}
