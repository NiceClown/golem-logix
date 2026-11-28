package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * and {@link ItemStacksResourceHandler}s: independent sided handlers at local index 0 stay distinct, while identical or
 * aliased wrapper handlers (including insert-only and full insert-only wrappers) collapse without mutating backing state.
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

        NetworkStorage.ResourceSlotSeen<FluidResource> seenIndependent = new NetworkStorage.ResourceSlotSeen<>();
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
        NetworkStorage.ResourceSlotSeen<FluidResource> seenSame = new NetworkStorage.ResourceSlotSeen<>();
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

        NetworkStorage.ResourceSlotSeen<FluidResource> seenAliased = new NetworkStorage.ResourceSlotSeen<>();
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

    public static void auditSidedItemAndInsertOnlyFluidDedup(GameTestHelper helper) {
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        GlobalPos sharedPos = GlobalPos.of(Level.OVERWORLD, new BlockPos(20, 64, 20));

        // 1. Bug 2 (items): two independent sided ItemStacksResourceHandlers at local slot 0 with 8 iron each -> 16
        ItemStacksResourceHandler invNorth = new ItemStacksResourceHandler(1);
        ItemStacksResourceHandler invSouth = new ItemStacksResourceHandler(1);
        invNorth.set(0, iron, 8);
        invSouth.set(0, iron, 8);

        NetworkStorage.ResourceSlotSeen<ItemResource> seenItems = new NetworkStorage.ResourceSlotSeen<>();
        int totalItems = 0;
        if (seenItems.markNew(sharedPos, invNorth, 0, iron, 8)) {
            totalItems += invNorth.getAmountAsInt(0);
        }
        if (seenItems.markNew(sharedPos, invSouth, 0, iron, 8)) {
            totalItems += invSouth.getAmountAsInt(0);
        }
        helper.assertTrue(totalItems == 16,
                "two independent sided item handlers at slot 0 must sum to 16, got " + totalItems);
        helper.assertTrue(invNorth.getAmountAsInt(0) == 8 && invSouth.getAmountAsInt(0) == 8,
                "item dedup probe must leave both independent inventories untouched at 8");

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
        NetworkStorage.ResourceSlotSeen<ItemResource> seenSharedItems = new NetworkStorage.ResourceSlotSeen<>();
        int aliasedItems = 0;
        if (seenSharedItems.markNew(sharedPos, itemWrapA, 0, iron, 8)) {
            aliasedItems += itemWrapA.getAmountAsInt(0);
        }
        if (seenSharedItems.markNew(sharedPos, itemWrapB, 0, iron, 8)) {
            aliasedItems += itemWrapB.getAmountAsInt(0);
        }
        helper.assertTrue(aliasedItems == 8 && sharedInv.getAmountAsInt(0) == 8,
                "distinct wrappers over one item handler must collapse to 8 and stay untouched, got " + aliasedItems);

        // 3. Bug 3 (insert-only partial fluid tank): two insertOnly wrappers over ONE 1000/4000 mB tank -> 1000 mB
        FluidStacksResourceHandler partialTank = new FluidStacksResourceHandler(1, 4000);
        partialTank.set(0, water, 1000);
        ResourceHandler<FluidResource> partialNorth = Transfers.insertOnly(partialTank);
        ResourceHandler<FluidResource> partialSouth = Transfers.insertOnly(partialTank);
        NetworkStorage.ResourceSlotSeen<FluidResource> seenPartialInsertOnly = new NetworkStorage.ResourceSlotSeen<>();
        int partialTotal = 0;
        if (seenPartialInsertOnly.markNew(sharedPos, partialNorth, 0, water, 1000)) {
            partialTotal += partialNorth.getAmountAsInt(0);
        }
        if (seenPartialInsertOnly.markNew(sharedPos, partialSouth, 0, water, 1000)) {
            partialTotal += partialSouth.getAmountAsInt(0);
        }
        helper.assertTrue(partialTotal == 1000 && partialTank.getAmountAsInt(0) == 1000,
                "two insertOnly wrappers over one 1000/4000 mB tank must count 1000 mB and stay untouched, got " + partialTotal);

        // 4. Bug 3 (insert-only 100%-full fluid tank): two insertOnly wrappers over ONE 1000/1000 mB tank -> 1000 mB
        FluidStacksResourceHandler fullTank = new FluidStacksResourceHandler(1, 1000);
        fullTank.set(0, water, 1000);
        ResourceHandler<FluidResource> fullNorth = Transfers.insertOnly(fullTank);
        ResourceHandler<FluidResource> fullSouth = Transfers.insertOnly(fullTank);
        NetworkStorage.ResourceSlotSeen<FluidResource> seenFullInsertOnly = new NetworkStorage.ResourceSlotSeen<>();
        int fullTotal = 0;
        if (seenFullInsertOnly.markNew(sharedPos, fullNorth, 0, water, 1000)) {
            fullTotal += fullNorth.getAmountAsInt(0);
        }
        if (seenFullInsertOnly.markNew(sharedPos, fullSouth, 0, water, 1000)) {
            fullTotal += fullSouth.getAmountAsInt(0);
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
        NetworkStorage.ResourceSlotSeen<FluidResource> seenIndInsertOnly = new NetworkStorage.ResourceSlotSeen<>();
        int indInsertTotal = 0;
        if (seenIndInsertOnly.markNew(sharedPos, indInsertA, 0, water, 1000)) {
            indInsertTotal += indInsertA.getAmountAsInt(0);
        }
        if (seenIndInsertOnly.markNew(sharedPos, indInsertB, 0, water, 1000)) {
            indInsertTotal += indInsertB.getAmountAsInt(0);
        }
        helper.assertTrue(indInsertTotal == 2000
                        && indTankA.getAmountAsInt(0) == 1000 && indTankB.getAmountAsInt(0) == 1000,
                "two independent insertOnly 1000/4000 mB tanks must sum to 2000 mB and stay untouched, got " + indInsertTotal);

        // 6. Reverse probe: normal wrapper first + full insertOnly wrapper second over ONE 1000/1000 mB tank -> 1000 mB
        ResourceHandler<FluidResource> fullNormal = wrap(fullTank);
        ResourceHandler<FluidResource> fullInsertOnly = Transfers.insertOnly(fullTank);
        NetworkStorage.ResourceSlotSeen<FluidResource> seenMixedFull = new NetworkStorage.ResourceSlotSeen<>();
        int mixedFullTotal = 0;
        if (seenMixedFull.markNew(sharedPos, fullNormal, 0, water, 1000)) {
            mixedFullTotal += fullNormal.getAmountAsInt(0);
        }
        if (seenMixedFull.markNew(sharedPos, fullInsertOnly, 0, water, 1000)) {
            mixedFullTotal += fullInsertOnly.getAmountAsInt(0);
        }
        helper.assertTrue(mixedFullTotal == 1000 && fullTank.getAmountAsInt(0) == 1000,
                "normal + full insertOnly wrappers over one 1000/1000 mB tank must count 1000 mB, got " + mixedFullTotal);

        // 7. Vanilla Brewing Stand UP (local 0 -> phys 3) + DOWN (local 3 -> phys 3) with full 64/64 Nether Wart -> 64
        ItemResource wart = ItemResource.of(Items.NETHER_WART);
        BrewingStandBlockEntity brew = new BrewingStandBlockEntity(BlockPos.ZERO, Blocks.BREWING_STAND.defaultBlockState());
        brew.setItem(3, new ItemStack(Items.NETHER_WART, 64));
        WorldlyContainerWrapper brewUp = new WorldlyContainerWrapper(brew, Direction.UP);
        WorldlyContainerWrapper brewDown = new WorldlyContainerWrapper(brew, Direction.DOWN);
        int[] upSlots = brew.getSlotsForFace(Direction.UP);
        int[] downSlots = brew.getSlotsForFace(Direction.DOWN);
        NetworkStorage.ResourceSlotSeen<ItemResource> seenBrew = new NetworkStorage.ResourceSlotSeen<>();
        int brewTotal = 0;
        for (int i = 0; i < brewUp.size(); i++) {
            int amt = brewUp.getAmountAsInt(i);
            if (amt > 0 && brewUp.getResource(i).equals(wart)
                    && seenBrew.markNew(sharedPos, brewUp, i, wart, amt, brew, upSlots[i])) {
                brewTotal += amt;
            }
        }
        for (int i = 0; i < brewDown.size(); i++) {
            int amt = brewDown.getAmountAsInt(i);
            if (amt > 0 && brewDown.getResource(i).equals(wart)
                    && seenBrew.markNew(sharedPos, brewDown, i, wart, amt, brew, downSlots[i])) {
                brewTotal += amt;
            }
        }
        helper.assertTrue(brewTotal == 64 && brew.getItem(3).getCount() == 64,
                "Brewing Stand UP+DOWN with 64/64 Nether Wart must collapse to 64, got " + brewTotal);

        // 8. Unextractable WorldlyContainer with distinct physical slots {0} vs {1} at local index 0 holding 64/64 -> 128
        WorldlyContainer lockedSided = unextractableTwoSlotWorldly(
                new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.IRON_INGOT, 64));
        WorldlyContainerWrapper lockedNorth = new WorldlyContainerWrapper(lockedSided, Direction.NORTH);
        WorldlyContainerWrapper lockedSouth = new WorldlyContainerWrapper(lockedSided, Direction.SOUTH);
        NetworkStorage.ResourceSlotSeen<ItemResource> seenLocked = new NetworkStorage.ResourceSlotSeen<>();
        int lockedTotal = 0;
        if (seenLocked.markNew(sharedPos, lockedNorth, 0, iron, 64,
                lockedSided, lockedSided.getSlotsForFace(Direction.NORTH)[0])) {
            lockedTotal += lockedNorth.getAmountAsInt(0);
        }
        if (seenLocked.markNew(sharedPos, lockedSouth, 0, iron, 64,
                lockedSided, lockedSided.getSlotsForFace(Direction.SOUTH)[0])) {
            lockedTotal += lockedSouth.getAmountAsInt(0);
        }
        helper.assertTrue(lockedTotal == 128
                        && lockedSided.getItem(0).getCount() == 64 && lockedSided.getItem(1).getCount() == 64,
                "unextractable WorldlyContainer with distinct physical slots {0} and {1} at 64/64 must sum to 128, got " + lockedTotal);

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
                return delegate.extract(index, resource, amount, tx);
            }
        };
    }
}
