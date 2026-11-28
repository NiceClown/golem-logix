package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * GameTest verifying {@link NetworkStorage.FluidTankSeen} against live NeoForge {@link FluidStacksResourceHandler}s:
 * independent sided tanks at local index 0 stay distinct (2000 mB), while identical or aliased wrapper handlers collapse (1000 mB).
 */
public final class NetworkStorageGameTests {
    private NetworkStorageGameTests() {
    }

    public static void auditSidedFluidTankDedup(GameTestHelper helper) {
        FluidResource water = FluidResource.of(Fluids.WATER);
        GlobalPos sharedPos = GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, 10));

        // 1. Two independent sided tanks on the same BlockPos, both at local index 0 with 1000 mB water -> 2000 mB
        FluidStacksResourceHandler tankNorth = new FluidStacksResourceHandler(1, 4000);
        FluidStacksResourceHandler tankSouth = new FluidStacksResourceHandler(1, 4000);
        tankNorth.set(0, water, 1000);
        tankSouth.set(0, water, 1000);

        NetworkStorage.FluidTankSeen seenIndependent = new NetworkStorage.FluidTankSeen();
        int totalIndependent = 0;
        if (seenIndependent.markNew(sharedPos, tankNorth, 0, water, 1000)) {
            totalIndependent += tankNorth.getAmountAsInt(0);
        }
        if (seenIndependent.markNew(sharedPos, tankSouth, 0, water, 1000)) {
            totalIndependent += tankSouth.getAmountAsInt(0);
        }
        helper.assertTrue(totalIndependent == 2000,
                "two independent sided tanks at index 0 must sum to 2000 mB, got " + totalIndependent);
        helper.assertTrue(tankNorth.getAmountAsInt(0) == 1000 && tankSouth.getAmountAsInt(0) == 1000,
                "rollback probe must leave both independent tanks untouched at 1000 mB");

        // 2. Same handler instance on two faces -> O(1) dedup -> 1000 mB
        NetworkStorage.FluidTankSeen seenSame = new NetworkStorage.FluidTankSeen();
        int totalSame = 0;
        if (seenSame.markNew(sharedPos, tankNorth, 0, water, 1000)) {
            totalSame += tankNorth.getAmountAsInt(0);
        }
        if (seenSame.markNew(sharedPos, tankNorth, 0, water, 1000)) {
            totalSame += tankNorth.getAmountAsInt(0);
        }
        helper.assertTrue(totalSame == 1000, "same handler on two faces must collapse to 1000 mB, got " + totalSame);

        // 3. Two distinct wrapper instances over ONE shared backing tank -> transactional probe detects alias -> 1000 mB
        FluidStacksResourceHandler backing = new FluidStacksResourceHandler(1, 4000);
        backing.set(0, water, 1000);
        ResourceHandler<FluidResource> wrapNorth = wrap(backing);
        ResourceHandler<FluidResource> wrapSouth = wrap(backing);

        NetworkStorage.FluidTankSeen seenAliased = new NetworkStorage.FluidTankSeen();
        int totalAliased = 0;
        if (seenAliased.markNew(sharedPos, wrapNorth, 0, water, 1000)) {
            totalAliased += wrapNorth.getAmountAsInt(0);
        }
        if (seenAliased.markNew(sharedPos, wrapSouth, 0, water, 1000)) {
            totalAliased += wrapSouth.getAmountAsInt(0);
        }
        helper.assertTrue(totalAliased == 1000,
                "distinct wrappers over one backing tank must collapse to 1000 mB, got " + totalAliased);
        helper.assertTrue(backing.getAmountAsInt(0) == 1000,
                "rollback probe must leave aliased backing tank untouched at 1000 mB");

        // 4. Verify live Transaction drain across both independent tanks yields 2000 mB matching counted total
        int drained;
        try (Transaction tx = Transaction.openRoot()) {
            drained = tankNorth.extract(water, 2000, tx) + tankSouth.extract(water, 2000, tx);
        }
        helper.assertTrue(drained == 2000 && drained == totalIndependent,
                "counted fluid (" + totalIndependent + ") must match extractable fluid (" + drained + ")");
        helper.succeed();
    }

    private static ResourceHandler<FluidResource> wrap(ResourceHandler<FluidResource> delegate) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public FluidResource getResource(int index) {
                return delegate.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return delegate.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                return delegate.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                return delegate.isValid(index, resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext tx) {
                return delegate.insert(index, resource, amount, tx);
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext tx) {
                return delegate.extract(index, resource, amount, tx);
            }
        };
    }
}
