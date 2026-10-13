package com.golemlogix.system;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModFluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Liquid Cryo-Source helpers. The network has no stock of it: every mB pumped out is made on the spot from the network's
 * Source ({@link GolemConfig#CRYO_SOURCE_COST} Source per mB). It is never stored on disks and never goes into buckets.
 */
public final class CryoSource {
    private CryoSource() {
    }

    public static boolean is(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(ModFluids.CRYO_SOURCE.get());
    }

    public static FluidStack stack(int amount) {
        return new FluidStack(ModFluids.CRYO_SOURCE.get(), amount);
    }

    /** Source needed for amount mB. */
    public static long cost(long amount) {
        return amount * GolemConfig.CRYO_SOURCE_COST.get();
    }
}
