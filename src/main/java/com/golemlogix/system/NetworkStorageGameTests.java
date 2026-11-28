package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * GameTest verifying {@link NetworkStorage.FluidTankKey} against live NeoForge {@link FluidTank}s:
 * independent sided tanks at local index 0 stay distinct (2000 mB), while identical handlers collapse (1000 mB).
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetworkStorageGameTests {
    private NetworkStorageGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void coreSidedFluidTankDedup(GameTestHelper helper) {
        FluidTank tankNorth = new FluidTank(4000);
        FluidTank tankSouth = new FluidTank(4000);
        tankNorth.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        tankSouth.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        // Two independent sided tanks on the same BlockPos, both at local index 0 with 1000 mB water -> 2000 mB
        Set<NetworkStorage.FluidTankKey> seenIndependent = new HashSet<>();
        int totalIndependent = 0;
        if (seenIndependent.add(new NetworkStorage.FluidTankKey(tankNorth, 0))) {
            totalIndependent += tankNorth.getFluidInTank(0).getAmount();
        }
        if (seenIndependent.add(new NetworkStorage.FluidTankKey(tankSouth, 0))) {
            totalIndependent += tankSouth.getFluidInTank(0).getAmount();
        }
        helper.assertTrue(totalIndependent == 2000,
                "two independent sided tanks at index 0 must sum to 2000 mB, got " + totalIndependent);

        // Same handler instance on two faces -> collapses to 1000 mB
        Set<NetworkStorage.FluidTankKey> seenSame = new HashSet<>();
        int totalSame = 0;
        if (seenSame.add(new NetworkStorage.FluidTankKey(tankNorth, 0))) {
            totalSame += tankNorth.getFluidInTank(0).getAmount();
        }
        if (seenSame.add(new NetworkStorage.FluidTankKey(tankNorth, 0))) {
            totalSame += tankNorth.getFluidInTank(0).getAmount();
        }
        helper.assertTrue(totalSame == 1000,
                "same handler instance on two faces must collapse to 1000 mB, got " + totalSame);

        int simDrained = tankNorth.drain(2000, IFluidHandler.FluidAction.SIMULATE).getAmount()
                + tankSouth.drain(2000, IFluidHandler.FluidAction.SIMULATE).getAmount();
        helper.assertTrue(simDrained == 2000 && simDrained == totalIndependent,
                "counted fluid (" + totalIndependent + ") must match drainable fluid (" + simDrained + ")");
        helper.succeed();
    }
}
