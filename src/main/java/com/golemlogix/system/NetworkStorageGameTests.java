package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import javax.annotation.Nullable;

/**
 * GameTests verifying {@link NetworkStorage.ResourceSlotSeen} against live NeoForge {@link FluidStacksResourceHandler}s
 * and {@link ItemStacksResourceHandler}s: independent sided handlers stay distinct in O(N) even with 4,096 identical
 * slots, while identical, reversed-order, or insert-only aliased wrappers collapse without mutating backing state.
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

        int totalIndependent = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = tankNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, tankNorth, 0, water, a)) {
                totalIndependent += a;
            }
            int b = tankSouth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, tankSouth, 0, water, b)) {
                totalIndependent += b;
            }
        }
        helper.assertTrue(totalIndependent == 2000,
                "two independent sided tanks at index 0 must sum to 2000 mB, got " + totalIndependent);
        helper.assertTrue(tankNorth.getAmountAsInt(0) == 1000 && tankSouth.getAmountAsInt(0) == 1000,
                "rollback must leave both independent tanks untouched at 1000 mB");

        // 2. Same handler instance on two faces -> O(1) dedup -> 1000 mB
        int totalSame = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = tankNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, tankNorth, 0, water, a, null, 0, false)) {
                totalSame += a;
            }
            int b = tankNorth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, tankNorth, 0, water, b, null, 0, false)) {
                totalSame += b;
            }
        }
        helper.assertTrue(totalSame == 1000, "same handler on two faces must collapse to 1000 mB, got " + totalSame);

        // 3. Two distinct wrapper instances over ONE shared backing tank -> 1000 mB
        FluidStacksResourceHandler backing = new FluidStacksResourceHandler(1, 4000);
        backing.set(0, water, 1000);
        ResourceHandler<FluidResource> wrapNorth = wrap(backing);
        ResourceHandler<FluidResource> wrapSouth = wrap(backing);

        int totalAliased = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = wrapNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, wrapNorth, 0, water, a)) {
                totalAliased += a;
            }
            int b = wrapSouth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, wrapSouth, 0, water, b)) {
                totalAliased += b;
            }
        }
        helper.assertTrue(totalAliased == 1000,
                "distinct wrappers over one backing tank must collapse to 1000 mB, got " + totalAliased);
        helper.assertTrue(backing.getAmountAsInt(0) == 1000,
                "rollback must leave aliased backing tank untouched at 1000 mB");

        // 4. Verify live Transaction drain across both independent tanks yields 2000 mB matching counted total
        int drained;
        try (Transaction tx = Transaction.openRoot()) {
            drained = tankNorth.extract(water, 2000, tx) + tankSouth.extract(water, 2000, tx);
        }
        helper.assertTrue(drained == 2000 && drained == totalIndependent,
                "counted fluid (" + totalIndependent + ") must match extractable fluid (" + drained + ")");
        helper.succeed();
    }

    public static void auditSidedItemAndInsertOnlyFluidDedup(GameTestHelper helper) {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        GlobalPos sharedPos = GlobalPos.of(Level.OVERWORLD, new BlockPos(20, 64, 20));

        // 1. Bug 2 (items): two independent sided ItemStacksResourceHandlers at local slot 0 with 8 iron each -> 16
        ItemStacksResourceHandler invNorth = new ItemStacksResourceHandler(1);
        ItemStacksResourceHandler invSouth = new ItemStacksResourceHandler(1);
        invNorth.set(0, iron, 8);
        invSouth.set(0, iron, 8);

        int totalItems = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = invNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, invNorth, 0, iron, a)) {
                totalItems += a;
            }
            int b = invSouth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, invSouth, 0, iron, b)) {
                totalItems += b;
            }
        }
        helper.assertTrue(totalItems == 16,
                "two independent sided item handlers at slot 0 must sum to 16, got " + totalItems);
        helper.assertTrue(invNorth.getAmountAsInt(0) == 8 && invSouth.getAmountAsInt(0) == 8,
                "item dedup must leave both independent inventories untouched at 8");

        int extractedItems;
        try (Transaction tx = Transaction.openRoot()) {
            extractedItems = invNorth.extract(0, iron, 16, tx) + invSouth.extract(0, iron, 16, tx);
        }
        helper.assertTrue(extractedItems == 16 && invNorth.getAmountAsInt(0) == 8 && invSouth.getAmountAsInt(0) == 8,
                "rolled-back Transaction extract across independent item handlers must yield 16 and restore 8/8, got " + extractedItems);

        // 2. Bug 2 (aliased item wrappers): two distinct wrappers over ONE backing item handler -> 8
        ItemStacksResourceHandler sharedInv = new ItemStacksResourceHandler(1);
        sharedInv.set(0, iron, 8);
        ResourceHandler<ItemResource> itemWrapA = wrap(sharedInv);
        ResourceHandler<ItemResource> itemWrapB = wrap(sharedInv);
        int aliasedItems = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = itemWrapA.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, itemWrapA, 0, iron, a)) {
                aliasedItems += a;
            }
            int b = itemWrapB.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, itemWrapB, 0, iron, b)) {
                aliasedItems += b;
            }
        }
        helper.assertTrue(aliasedItems == 8 && sharedInv.getAmountAsInt(0) == 8,
                "distinct wrappers over one item handler must collapse to 8 and stay untouched, got " + aliasedItems);

        // 3. Bug 3 (insert-only partial fluid tank): two insertOnly wrappers over ONE 1000/4000 mB tank -> 1000 mB
        FluidStacksResourceHandler partialTank = new FluidStacksResourceHandler(1, 4000);
        partialTank.set(0, water, 1000);
        ResourceHandler<FluidResource> partialNorth = Transfers.insertOnly(partialTank);
        ResourceHandler<FluidResource> partialSouth = Transfers.insertOnly(partialTank);
        int partialTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = partialNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, partialNorth, 0, water, a)) {
                partialTotal += a;
            }
            int b = partialSouth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, partialSouth, 0, water, b)) {
                partialTotal += b;
            }
        }
        helper.assertTrue(partialTotal == 1000 && partialTank.getAmountAsInt(0) == 1000,
                "two insertOnly wrappers over one 1000/4000 mB tank must count 1000 mB and stay untouched, got " + partialTotal);

        // 4. Bug 3 (insert-only 100%-full fluid tank): two insertOnly wrappers over ONE 1000/1000 mB tank -> 1000 mB
        FluidStacksResourceHandler fullTank = new FluidStacksResourceHandler(1, 1000);
        fullTank.set(0, water, 1000);
        ResourceHandler<FluidResource> fullNorth = Transfers.insertOnly(fullTank);
        ResourceHandler<FluidResource> fullSouth = Transfers.insertOnly(fullTank);
        int fullTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = fullNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, fullNorth, 0, water, a)) {
                fullTotal += a;
            }
            int b = fullSouth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, fullSouth, 0, water, b)) {
                fullTotal += b;
            }
        }
        helper.assertTrue(fullTotal == 1000 && fullTank.getAmountAsInt(0) == 1000,
                "two insertOnly wrappers over one full 1000/1000 mB tank must count 1000 mB and stay untouched, got " + fullTotal);

        // 5. Bug 3 (independent insert-only fluid tanks): two independent 1000/4000 mB insertOnly tanks -> 2000 mB
        FluidStacksResourceHandler indTankA = new FluidStacksResourceHandler(1, 4000);
        FluidStacksResourceHandler indTankB = new FluidStacksResourceHandler(1, 4000);
        indTankA.set(0, water, 1000);
        indTankB.set(0, water, 1000);
        ResourceHandler<FluidResource> indInsertA = Transfers.insertOnly(indTankA);
        ResourceHandler<FluidResource> indInsertB = Transfers.insertOnly(indTankB);
        int indInsertTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = indInsertA.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, indInsertA, 0, water, a)) {
                indInsertTotal += a;
            }
            int b = indInsertB.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, indInsertB, 0, water, b)) {
                indInsertTotal += b;
            }
        }
        helper.assertTrue(indInsertTotal == 2000
                        && indTankA.getAmountAsInt(0) == 1000 && indTankB.getAmountAsInt(0) == 1000,
                "two independent insertOnly 1000/4000 mB tanks must sum to 2000 mB and stay untouched, got " + indInsertTotal);

        // 6. Mixed normal + full insertOnly (both orders!) over ONE 1000/1000 mB tank -> 1000 mB
        ResourceHandler<FluidResource> fullNormal = wrap(fullTank);
        ResourceHandler<FluidResource> fullInsertOnly = Transfers.insertOnly(fullTank);
        int mixedNormalFirst = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = fullNormal.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, fullNormal, 0, water, a)) {
                mixedNormalFirst += a;
            }
            int b = fullInsertOnly.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, fullInsertOnly, 0, water, b)) {
                mixedNormalFirst += b;
            }
        }
        int mixedInsertFirst = 0;
        try (NetworkStorage.ResourceSlotSeen<FluidResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = fullInsertOnly.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, fullInsertOnly, 0, water, a)) {
                mixedInsertFirst += a;
            }
            int b = fullNormal.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, fullNormal, 0, water, b)) {
                mixedInsertFirst += b;
            }
        }
        helper.assertTrue(mixedNormalFirst == 1000 && mixedInsertFirst == 1000 && fullTank.getAmountAsInt(0) == 1000,
                "mixed normal + full insertOnly over one 1000/1000 mB tank must count 1000 mB in both orders, got "
                        + mixedNormalFirst + " / " + mixedInsertFirst);

        // 7. Vanilla Brewing Stand UP (local 0 -> phys 3) + DOWN (local 3 -> phys 3) with full 64/64 Nether Wart -> 64
        ItemResource wart = ItemResource.of(Items.NETHER_WART);
        BrewingStandBlockEntity brew = new BrewingStandBlockEntity(BlockPos.ZERO, Blocks.BREWING_STAND.defaultBlockState());
        brew.setItem(3, new ItemStack(Items.NETHER_WART, 64));
        WorldlyContainerWrapper brewUp = new WorldlyContainerWrapper(brew, Direction.UP);
        WorldlyContainerWrapper brewDown = new WorldlyContainerWrapper(brew, Direction.DOWN);
        int[] upSlots = brew.getSlotsForFace(Direction.UP);
        int[] downSlots = brew.getSlotsForFace(Direction.DOWN);
        int brewTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            for (int i = 0; i < brewUp.size(); i++) {
                int amt = brewUp.getAmountAsInt(i);
                if (amt > 0 && brewUp.getResource(i).equals(wart)
                        && seen.markNew(sharedPos, brewUp, i, wart, amt, brew, upSlots[i], false)) {
                    brewTotal += amt;
                }
            }
            for (int i = 0; i < brewDown.size(); i++) {
                int amt = brewDown.getAmountAsInt(i);
                if (amt > 0 && brewDown.getResource(i).equals(wart)
                        && seen.markNew(sharedPos, brewDown, i, wart, amt, brew, downSlots[i], false)) {
                    brewTotal += amt;
                }
            }
        }
        helper.assertTrue(brewTotal == 64 && brew.getItem(3).getCount() == 64,
                "Brewing Stand UP+DOWN with 64/64 Nether Wart must collapse to 64, got " + brewTotal);

        // 8. Unextractable WorldlyContainer with distinct physical slots {0} vs {1} at local index 0 holding 64/64 -> 128
        WorldlyContainer lockedSided = unextractableTwoSlotWorldly(
                new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.IRON_INGOT, 64));
        WorldlyContainerWrapper lockedNorth = new WorldlyContainerWrapper(lockedSided, Direction.NORTH);
        WorldlyContainerWrapper lockedSouth = new WorldlyContainerWrapper(lockedSided, Direction.SOUTH);
        int lockedTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = lockedNorth.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, lockedNorth, 0, iron, a,
                    lockedSided, lockedSided.getSlotsForFace(Direction.NORTH)[0], false)) {
                lockedTotal += a;
            }
            int b = lockedSouth.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, lockedSouth, 0, iron, b,
                    lockedSided, lockedSided.getSlotsForFace(Direction.SOUTH)[0], false)) {
                lockedTotal += b;
            }
        }
        helper.assertTrue(lockedTotal == 128
                        && lockedSided.getItem(0).getCount() == 64 && lockedSided.getItem(1).getCount() == 64,
                "unextractable WorldlyContainer with distinct physical slots {0} and {1} at 64/64 must sum to 128, got " + lockedTotal);

        // 9. Large 4,096-slot storages: strictly O(N) with ZERO cross-handler candidate reads across all layouts!
        final int largeSlots = 4096;
        ItemStacksResourceHandler largeDistinct = new ItemStacksResourceHandler(largeSlots);
        ItemStacksResourceHandler largeRepeatedA = new ItemStacksResourceHandler(largeSlots);
        ItemStacksResourceHandler largeRepeatedB = new ItemStacksResourceHandler(largeSlots);
        for (int i = 0; i < largeSlots; i++) {
            ItemStack unique = new ItemStack(Items.IRON_INGOT);
            unique.set(DataComponents.CUSTOM_NAME, Component.literal("s" + i));
            largeDistinct.set(i, ItemResource.of(unique), 3);
            largeRepeatedA.set(i, iron, 8);
            largeRepeatedB.set(i, iron, 8);
        }

        // 9a. Same-handler across 2 Periscopes (4,096 distinct items) -> 12,288, zero probe reads
        int[] sameReads = new int[1];
        ResourceHandler<ItemResource> sameHandler = wrap(largeDistinct, sameReads);
        long largeSameTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < largeSlots; i++) {
                    ItemResource r = largeDistinct.getResource(i);
                    int amt = largeDistinct.getAmountAsInt(i);
                    if (amt > 0 && seen.markNew(sharedPos, sameHandler, i, r, amt, null, i, false)) {
                        largeSameTotal += amt;
                    }
                }
            }
        }
        helper.assertTrue(largeSameTotal == largeSlots * 3L && sameReads[0] == 0,
                "4,096-slot same-handler must count " + (largeSlots * 3L) + " with 0 reads, got "
                        + largeSameTotal + " / " + sameReads[0]);

        // 9b. Reviewer Case 1: TWO INDEPENDENT 4,096-slot inventories filled with identical iron ingots -> 65,536,
        //     ZERO cross-handler reads on handler A during handler B (eliminating 16,777,216 O(N^2) reads!)
        int[] indRepReadsA = new int[1];
        ResourceHandler<ItemResource> indRepWrapA = wrap(largeRepeatedA, indRepReadsA);
        ResourceHandler<ItemResource> indRepWrapB = wrap(largeRepeatedB);
        long indRepTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            for (int i = 0; i < largeSlots; i++) {
                int amt = indRepWrapA.getAmountAsInt(i);
                if (amt > 0 && seen.markNew(sharedPos, indRepWrapA, i, iron, amt)) {
                    indRepTotal += amt;
                }
            }
            int readsAfterA = indRepReadsA[0];
            for (int i = 0; i < largeSlots; i++) {
                int amt = indRepWrapB.getAmountAsInt(i);
                if (amt > 0 && seen.markNew(sharedPos, indRepWrapB, i, iron, amt)) {
                    indRepTotal += amt;
                }
            }
            helper.assertTrue(indRepReadsA[0] == readsAfterA,
                    "independent 4,096-slot identical-item pass B must do 0 reads on handler A, did "
                            + (indRepReadsA[0] - readsAfterA));
        }
        helper.assertTrue(indRepTotal == largeSlots * 16L
                        && largeRepeatedA.getAmountAsInt(largeSlots - 1) == 8
                        && largeRepeatedB.getAmountAsInt(largeSlots - 1) == 8,
                "two independent 4,096-slot iron inventories must sum to " + (largeSlots * 16L)
                        + " and stay untouched, got " + indRepTotal);

        // 9c. Reviewer Case 2: TWO ALIASED wrappers over ONE 4,096-slot iron inventory in REVERSE slot order -> 32,768,
        //     ZERO cross-handler reads on handler A during handler B!
        int[] revReadsA = new int[1];
        ResourceHandler<ItemResource> revWrapA = wrap(largeRepeatedA, revReadsA);
        ResourceHandler<ItemResource> revWrapB = reversedWrap(largeRepeatedA);
        long revTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            for (int i = 0; i < largeSlots; i++) {
                int amt = revWrapA.getAmountAsInt(i);
                if (amt > 0 && seen.markNew(sharedPos, revWrapA, i, iron, amt)) {
                    revTotal += amt;
                }
            }
            int readsAfterA = revReadsA[0];
            for (int i = 0; i < largeSlots; i++) {
                int amt = revWrapB.getAmountAsInt(i);
                if (amt > 0 && seen.markNew(sharedPos, revWrapB, i, iron, amt)) {
                    revTotal += amt;
                }
            }
            helper.assertTrue(revReadsA[0] == readsAfterA,
                    "reversed-order aliased 4,096-slot pass B must do 0 reads on handler A, did "
                            + (revReadsA[0] - readsAfterA));
        }
        helper.assertTrue(revTotal == largeSlots * 8L && largeRepeatedA.getAmountAsInt(0) == 8
                        && largeRepeatedA.getAmountAsInt(largeSlots - 1) == 8,
                "reversed-order aliased 4,096-slot wrappers must count " + (largeSlots * 8L)
                        + " and stay untouched, got " + revTotal);

        // 9d. Capped-extract aliased wrappers (extract capped to 1 < amt 8) -> falls back to probe -> 8, untouched
        ItemStacksResourceHandler cappedBacking = new ItemStacksResourceHandler(1);
        cappedBacking.set(0, iron, 8);
        ResourceHandler<ItemResource> cappedA = cappedExtractWrap(cappedBacking, 1);
        ResourceHandler<ItemResource> cappedB = cappedExtractWrap(cappedBacking, 1);
        int cappedTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = cappedA.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, cappedA, 0, iron, a)) {
                cappedTotal += a;
            }
            int b = cappedB.getAmountAsInt(0);
            if (b > 0 && seen.markNew(sharedPos, cappedB, 0, iron, b)) {
                cappedTotal += b;
            }
        }
        helper.assertTrue(cappedTotal == 8 && cappedBacking.getAmountAsInt(0) == 8,
                "capped-extract aliased wrappers must collapse to 8 and restore 8, got " + cappedTotal);

        // 9e. Same-physicalOwner two faces + 3rd non-worldly wrapper aliasing both slots -> 8 total
        ItemStacksResourceHandler physBacking = new ItemStacksResourceHandler(2);
        physBacking.set(0, iron, 4);
        physBacking.set(1, iron, 4);
        Object physOwner = new Object();
        ResourceHandler<ItemResource> physFaceA = wrap(physBacking);
        ResourceHandler<ItemResource> physFaceB = wrap(physBacking);
        ResourceHandler<ItemResource> nonWorldlyWrap = wrap(physBacking);
        int physTotal = 0;
        try (NetworkStorage.ResourceSlotSeen<ItemResource> seen = new NetworkStorage.ResourceSlotSeen<>()) {
            int a = physFaceA.getAmountAsInt(0);
            if (a > 0 && seen.markNew(sharedPos, physFaceA, 0, iron, a, physOwner, 0, true)) {
                physTotal += a;
            }
            int b = physFaceB.getAmountAsInt(1);
            if (b > 0 && seen.markNew(sharedPos, physFaceB, 1, iron, b, physOwner, 1, true)) {
                physTotal += b;
            }
            for (int i = 0; i < 2; i++) {
                int c = nonWorldlyWrap.getAmountAsInt(i);
                if (c > 0 && seen.markNew(sharedPos, nonWorldlyWrap, i, iron, c)) {
                    physTotal += c;
                }
            }
        }
        helper.assertTrue(physTotal == 8 && physBacking.getAmountAsInt(0) == 4 && physBacking.getAmountAsInt(1) == 4,
                "same-physicalOwner faces + 3rd non-worldly alias must count 8 and stay untouched, got " + physTotal);

        helper.succeed();
    }

    private static WorldlyContainer unextractableTwoSlotWorldly(ItemStack slot0, ItemStack slot1) {
        ItemStack[] stacks = new ItemStack[]{slot0, slot1};
        int[] north = new int[]{0};
        int[] south = new int[]{1};
        int[] none = new int[0];
        return new WorldlyContainer() {
            @Override
            public int[] getSlotsForFace(Direction side) {
                return side == Direction.NORTH ? north : side == Direction.SOUTH ? south : none;
            }

            @Override
            public boolean canPlaceItemThroughFace(int index, ItemStack stack, @Nullable Direction direction) {
                return false;
            }

            @Override
            public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
                return false;
            }

            @Override
            public int getContainerSize() {
                return stacks.length;
            }

            @Override
            public boolean isEmpty() {
                return stacks[0].isEmpty() && stacks[1].isEmpty();
            }

            @Override
            public ItemStack getItem(int index) {
                return stacks[index];
            }

            @Override
            public ItemStack removeItem(int index, int count) {
                return stacks[index].split(count);
            }

            @Override
            public ItemStack removeItemNoUpdate(int index) {
                ItemStack prev = stacks[index];
                stacks[index] = ItemStack.EMPTY;
                return prev;
            }

            @Override
            public void setItem(int index, ItemStack stack) {
                stacks[index] = stack;
            }

            @Override
            public void setChanged() {
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }

            @Override
            public void clearContent() {
                stacks[0] = ItemStack.EMPTY;
                stacks[1] = ItemStack.EMPTY;
            }
        };
    }

    private static <R extends Resource> ResourceHandler<R> wrap(ResourceHandler<R> delegate) {
        return wrap(delegate, null);
    }

    private static <R extends Resource> ResourceHandler<R> wrap(ResourceHandler<R> delegate, @Nullable int[] amountReads) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public R getResource(int index) {
                return delegate.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                if (amountReads != null) {
                    amountReads[0]++;
                }
                return delegate.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, R resource) {
                return delegate.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, R resource) {
                return delegate.isValid(index, resource);
            }

            @Override
            public int insert(int index, R resource, int amount, TransactionContext tx) {
                return delegate.insert(index, resource, amount, tx);
            }

            @Override
            public int extract(int index, R resource, int amount, TransactionContext tx) {
                return delegate.extract(index, resource, amount, tx);
            }
        };
    }

    private static <R extends Resource> ResourceHandler<R> reversedWrap(ResourceHandler<R> delegate) {
        return new ResourceHandler<>() {
            private int map(int index) {
                return delegate.size() - 1 - index;
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public R getResource(int index) {
                return delegate.getResource(map(index));
            }

            @Override
            public long getAmountAsLong(int index) {
                return delegate.getAmountAsLong(map(index));
            }

            @Override
            public long getCapacityAsLong(int index, R resource) {
                return delegate.getCapacityAsLong(map(index), resource);
            }

            @Override
            public boolean isValid(int index, R resource) {
                return delegate.isValid(map(index), resource);
            }

            @Override
            public int insert(int index, R resource, int amount, TransactionContext tx) {
                return delegate.insert(map(index), resource, amount, tx);
            }

            @Override
            public int extract(int index, R resource, int amount, TransactionContext tx) {
                return delegate.extract(map(index), resource, amount, tx);
            }
        };
    }

    private static <R extends Resource> ResourceHandler<R> cappedExtractWrap(ResourceHandler<R> delegate, int maxPerCall) {
        return new ResourceHandler<>() {
            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public R getResource(int index) {
                return delegate.getResource(index);
            }

            @Override
            public long getAmountAsLong(int index) {
                return delegate.getAmountAsLong(index);
            }

            @Override
            public long getCapacityAsLong(int index, R resource) {
                return delegate.getCapacityAsLong(index, resource);
            }

            @Override
            public boolean isValid(int index, R resource) {
                return delegate.isValid(index, resource);
            }

            @Override
            public int insert(int index, R resource, int amount, TransactionContext tx) {
                return delegate.insert(index, resource, amount, tx);
            }

            @Override
            public int extract(int index, R resource, int amount, TransactionContext tx) {
                return delegate.extract(index, resource, Math.min(amount, maxPerCall), tx);
            }
        };
    }
}
