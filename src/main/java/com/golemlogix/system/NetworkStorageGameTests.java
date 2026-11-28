package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.HashSet;
import java.util.Set;

/**
 * GameTests verifying {@link NetworkStorage.HandlerSlotKey} against live NeoForge {@link FluidTank}s and
 * {@link ItemStackHandler}s, plus single-container vanilla {@code InvWrapper} dedup across two Periscopes.
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
        Set<NetworkStorage.HandlerSlotKey> seenIndependent = new HashSet<>();
        int totalIndependent = 0;
        if (seenIndependent.add(new NetworkStorage.HandlerSlotKey(tankNorth, 0))) {
            totalIndependent += tankNorth.getFluidInTank(0).getAmount();
        }
        if (seenIndependent.add(new NetworkStorage.HandlerSlotKey(tankSouth, 0))) {
            totalIndependent += tankSouth.getFluidInTank(0).getAmount();
        }
        helper.assertTrue(totalIndependent == 2000,
                "two independent sided tanks at index 0 must sum to 2000 mB, got " + totalIndependent);

        // Same handler instance on two faces -> collapses to 1000 mB
        Set<NetworkStorage.HandlerSlotKey> seenSame = new HashSet<>();
        int totalSame = 0;
        if (seenSame.add(new NetworkStorage.HandlerSlotKey(tankNorth, 0))) {
            totalSame += tankNorth.getFluidInTank(0).getAmount();
        }
        if (seenSame.add(new NetworkStorage.HandlerSlotKey(tankNorth, 0))) {
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

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void coreSidedItemHandlerDedup(GameTestHelper helper) {
        ItemStackHandler invNorth = new ItemStackHandler(1);
        ItemStackHandler invSouth = new ItemStackHandler(1);
        invNorth.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        invSouth.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));

        // Two independent sided ItemStackHandlers at local slot 0 with 8 iron ingots each -> 16 total (NOT 8)
        Set<Object> seenIndependent = new HashSet<>();
        int totalIndependent = 0;
        if (seenIndependent.add(new NetworkStorage.HandlerSlotKey(invNorth, 0))) {
            totalIndependent += invNorth.getStackInSlot(0).getCount();
        }
        if (seenIndependent.add(new NetworkStorage.HandlerSlotKey(invSouth, 0))) {
            totalIndependent += invSouth.getStackInSlot(0).getCount();
        }
        helper.assertTrue(totalIndependent == 16,
                "two independent ItemStackHandlers at slot 0 must sum to 16, got " + totalIndependent);

        int simExtract = invNorth.extractItem(0, 16, true).getCount()
                + invSouth.extractItem(0, 16, true).getCount();
        helper.assertTrue(simExtract == 16,
                "extractable count across two independent handlers must be 16, got " + simExtract);

        // Same ItemStackHandler instance on two faces -> collapses to 8
        Set<Object> seenSame = new HashSet<>();
        int totalSame = 0;
        if (seenSame.add(new NetworkStorage.HandlerSlotKey(invNorth, 0))) {
            totalSame += invNorth.getStackInSlot(0).getCount();
        }
        if (seenSame.add(new NetworkStorage.HandlerSlotKey(invNorth, 0))) {
            totalSame += invNorth.getStackInSlot(0).getCount();
        }
        helper.assertTrue(totalSame == 8,
                "same ItemStackHandler instance on two faces must collapse to 8, got " + totalSame);

        // Single vanilla Container (Barrel) with 2 Periscopes on NORTH & EAST: distinct InvWrappers share one BE -> 8
        var level = helper.getLevel();
        BlockPos bushing = new BlockPos(4, 2, 4);
        helper.setBlock(bushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bushing.above(), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bushing.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(2, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(3, 2, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(2, 2, 5), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(3, 2, 6), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.WEST));
        BlockPos barrelPos = new BlockPos(2, 2, 6);
        helper.setBlock(barrelPos, Blocks.BARREL);
        ((BarrelBlockEntity) helper.getBlockEntity(barrelPos)).setItem(0, new ItemStack(Items.IRON_INGOT, 8));

        ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        helper.runAfterDelay(10, () -> {
            GolemNetwork net = GolemNetwork.refresh(level, helper.absolutePos(bushing));
            helper.assertTrue(net.itemCountOf(iron) == 8L && net.listItems().getOrDefault(iron, 0L) == 8L,
                    "single Barrel with 2 Periscopes must count 8 iron, got " + net.itemCountOf(iron));
            helper.assertTrue(net.extractItem(new ItemStack(Items.IRON_INGOT), 16, true).getCount() == 8,
                    "single Barrel with 2 Periscopes sim extract(16) must return 8");
            helper.succeed();
        });
    }
}
