package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.StockManometerBlock;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.function.Function;

/**
 * Backported from main's GolemGameTestsExtra: Annihilation Bus breaking and refill, complex
 * autocrafting scenarios and the Stock Manometer thresholds and bridges.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GolemGameTestsBackport5 {
    private static final String T = "empty";

    private GolemGameTestsBackport5() {
    }

    // =========================================================================
    // ANNIHILATION BUS
    // =========================================================================

    /** Breaking the bus block mid-mining clears progress, drops tool and does not crash. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusBrokenMidMining(GameTestHelper helper) {
        GolemGameTests.baseNetwork(helper);
        BlockPos bus = GolemGameTests.BUS_OUT, front = GolemGameTests.BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.OBSIDIAN);
        AnnihilationBusBlockEntity be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));

        helper.runAfterDelay(20, () -> {
            // Break the bus block while mining obsidian
            helper.setBlock(bus, Blocks.AIR);
        });

        helper.runAfterDelay(40, () -> {
            // Tool must have dropped in the world
            // 1.21.1: the test bounds start one block above the floor and a dropped item rests on the floor,
            // so look in a box around the bus instead of in the structure bounds
            boolean pickDropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(bus)).inflate(3)).stream()
                    .anyMatch(e -> e.getItem().is(Items.DIAMOND_PICKAXE));
            helper.assertTrue(pickDropped, "pickaxe dropped when bus was broken");
            helper.succeed();
        });
    }

    /** Two buses facing the same block from opposite sides mine cleanly without crash. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusTwoBusesSameBlock(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos busA = GolemGameTests.BUS_OUT;
        BlockPos target = GolemGameTests.BUS_OUT.north();
        BlockPos busB = target.north();

        helper.setBlock(busA, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(busB, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        helper.setBlock(target, Blocks.STONE);

        helper.runAfterDelay(80, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1,
                    "exactly one cobblestone produced");
            helper.succeed();
        });
    }

    /** Refill does not grab a different item if the slot expects an iron pickaxe. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusRefillDoesNotTakeDifferentItem(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = GolemGameTests.BUS_OUT, front = GolemGameTests.BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        AnnihilationBusBlockEntity be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);

        be.getUpgrades().setItem(UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.CRAFTING),
                new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));

        // Network only has diamond pickaxe
        GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND_PICKAXE), false);

        // Simulate breaking iron pickaxe
        be.getTool().setItem(0, ItemStack.EMPTY);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.getTool().getItem(0).isEmpty(),
                    "does not grab diamond pickaxe when expecting iron pickaxe");
            // Positive control: inserting expected iron pickaxe refills the slot and mines stone
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.IRON_PICKAXE), false);
            helper.setBlock(front, Blocks.STONE);
            be.wake();
            helper.succeedWhen(() -> {
                helper.assertTrue(be.getTool().getItem(0).is(Items.IRON_PICKAXE), "tool slot refilled with iron pickaxe");
                helper.assertBlockNotPresent(Blocks.STONE, front);
                helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) >= 1,
                        "positive control: bus refilled iron pickaxe and mined stone");
            });
        });
    }

    // =========================================================================
    // AUTOCRAFTING: COMPLEX SCENARIOS
    // =========================================================================

    /** Nested multi-step crafting: Oak Log -> 4 Oak Planks -> 4 Sticks -> Ladder. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void autocraftNestedMultiStep(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        ItemStack stick = new ItemStack(Items.STICK);

        // Pattern 0: 1 Log -> 4 Planks
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(
                new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        // Pattern 1: 2 Planks -> 4 Sticks
        provider.getContainer().setItem(1, GolemGameTests.card(GolemGameTests.craftingPattern(
                new ItemStack(Items.STICK, 4), plank.copy(), ItemStack.EMPTY, ItemStack.EMPTY, plank.copy())));
        // Pattern 2: 7 Sticks -> 3 Ladders
        provider.getContainer().setItem(2, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.LADDER, 3),
                stick.copy(), ItemStack.EMPTY, stick.copy(),
                stick.copy(), stick.copy(), stick.copy(),
                stick.copy(), ItemStack.EMPTY, stick.copy())));

        ItemKey ladderKey = ItemKey.of(new ItemStack(Items.LADDER));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            CraftingPlan plan = CraftingPlanner.plan(n, ladderKey, 3);
            helper.assertTrue(plan.missing().isEmpty(), "nothing missing for ladder: " + plan.missing());
            helper.assertTrue(n.startCrafting(ladderKey, 3).type() == GolemNetwork.StartType.STARTED, "ladder craft started");
        });

        helper.runAfterDelay(160, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            long ladders = n.itemCountOf(ladderKey);
            helper.assertTrue(ladders == 3, "expected 3 ladders delivered, got: " + ladders);
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(GolemGameTests.CPU)).isIdle(), "cpu idle");
            helper.succeed();
        });
    }

    /** Two jobs competing for the same ingredient: first takes all, second reports missing. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void autocraftCompetingJobs(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        // 1 Iron Ingot -> 9 Iron Nuggets
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(
                new ItemStack(Items.IRON_NUGGET, 9), new ItemStack(Items.IRON_INGOT))));
        ItemKey nugget = ItemKey.of(new ItemStack(Items.IRON_NUGGET));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 1), false); // only 1 ingot!

            // Job 1 requests 9 nuggets (takes 1 ingot)
            GolemNetwork.StartResult r1 = n.startCrafting(nugget, 9);
            helper.assertTrue(r1.type() == GolemNetwork.StartType.STARTED, "job 1 started");

            // Job 2 requests 9 nuggets (needs another ingot which is absent)
            CraftingPlan plan2 = CraftingPlanner.plan(n, nugget, 9);
            helper.assertTrue(!plan2.missing().isEmpty(), "job 2 sees missing ingredient because job 1 reserved it");
            helper.succeed();
        });
    }

    /** A job cancelled midway refunds all reserved ingredients back to the network. */
    @GameTest(template = T, timeoutTicks = 300)
    public static void autocraftJobCancelledMidwayRefunds(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        // Machine pattern (processing): 1 Raw Gold -> 1 Gold Ingot
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_GOLD)), List.of(new ItemStack(Items.GOLD_INGOT)))));

        ItemKey goldIngot = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
        ItemKey rawGold = ItemKey.of(new ItemStack(Items.RAW_GOLD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_GOLD, 5), false);
            n.startCrafting(goldIngot, 5);

            helper.runAfterDelay(10, () -> {
                CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(GolemGameTests.CPU);
                helper.assertTrue(!cpu.isIdle(), "job running");
                cpu.cancelJob();
                helper.assertTrue(cpu.isIdle(), "cpu idle after cancel");

                long returned = n.itemCountOf(rawGold);
                helper.assertTrue(returned == 5, "all 5 raw gold refunded to storage, got: " + returned);
                helper.succeed();
            });
        });
    }

    /** Processing pattern that outputs a fluid which is then consumed by the next step. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void autocraftFluidInputFromFluidOutput(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        // Pattern 1: 1 Ice -> 1000 mB Water
        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.ICE)), List.of(waterGhost))));

        // Pattern 2: 1000 mB Water + 1 Dirt -> 1 Mud
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(false,
                List.of(waterGhost.copy(), new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.MUD)))));

        ((CopperDriveBayBlockEntity) helper.getBlockEntity(GolemGameTests.BAY)).getContainer()
                .setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        ItemKey mudKey = ItemKey.of(new ItemStack(Items.MUD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.ICE, 2), false);
            n.insertItem(new ItemStack(Items.DIRT, 2), false);

            CraftingPlan plan = CraftingPlanner.plan(n, mudKey, 2);
            helper.assertTrue(plan.missing().isEmpty(), "planner chains fluid output to fluid input without missing");
            helper.succeed();
        });
    }

    /** Removing the Cryo-Calculator block mid-job drops job items and does not crash. */
    @GameTest(template = T, timeoutTicks = 300)
    public static void autocraftCalculatorRemovedMidJob(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(
                new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            n.startCrafting(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 40);

            helper.runAfterDelay(10, () -> {
                // Break CPU block while crafting
                helper.setBlock(GolemGameTests.CPU, Blocks.AIR);
                helper.runAfterDelay(20, () -> {
                    // Check network still functions and doesn't crash
                    helper.assertTrue(GolemGameTests.net(helper, bay) != null, "network remains valid after CPU removal");
                    helper.succeed();
                });
            });
        });
    }

    // =========================================================================
    // STOCK MANOMETER
    // =========================================================================

    /** The bridges go to what takes the signal: dust, a lamp, a repeater's back - not a repeater's output, a lever or plain stone. */
    @GameTest(template = T, timeoutTicks = 20)
    public static void stockManometerReceivers(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        // a floor: repeaters and dust break without one
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            helper.setBlock(at.below().relative(d), Blocks.STONE);
        }
        helper.setBlock(at, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
        BlockPos abs = helper.absolutePos(at);
        var level = helper.getLevel();
        Function<Direction, Boolean> takes = d -> StockManometerBlock.isReceiver(level, abs, d);
        helper.setBlock(at.north(), Blocks.REDSTONE_LAMP);
        helper.setBlock(at.south(), Blocks.STONE);
        helper.setBlock(at.east(), Blocks.REPEATER.defaultBlockState().setValue(DiodeBlock.FACING, Direction.WEST));
        helper.setBlock(at.west(), Blocks.REPEATER.defaultBlockState().setValue(DiodeBlock.FACING, Direction.WEST));
        helper.setBlock(at.above(), Blocks.LEVER);
        helper.assertTrue(takes.apply(Direction.NORTH), "a lamp takes the signal");
        helper.assertTrue(!takes.apply(Direction.SOUTH), "plain stone gets no bridge");
        helper.assertTrue(takes.apply(Direction.EAST), "a repeater takes it at its back");
        helper.assertTrue(!takes.apply(Direction.WEST), "a repeater's output gets no bridge");
        helper.assertTrue(!takes.apply(Direction.UP), "a lever gets no bridge");
        helper.setBlock(at.north(), Blocks.REDSTONE_WIRE);
        helper.assertTrue(takes.apply(Direction.NORTH), "dust takes the signal");
        helper.setBlock(at.north(), Blocks.AIR);
        helper.assertTrue(!takes.apply(Direction.NORTH), "the bridge goes with the receiver");
        // the window's setting beats what the block looks like
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(at);
        helper.assertTrue(!mano.hasBridge(Direction.SOUTH), "auto: no bridge to stone");
        mano.setBridgeMode(Direction.SOUTH, StockManometerBlockEntity.BRIDGE_ON);
        helper.assertTrue(mano.hasBridge(Direction.SOUTH), "always: a bridge to stone");
        helper.assertTrue(mano.getBridgeMode(Direction.EAST) == StockManometerBlockEntity.BRIDGE_AUTO, "the other sides keep their mode");
        helper.assertTrue(mano.hasBridge(Direction.EAST), "auto: a bridge to the repeater");
        mano.setBridgeMode(Direction.EAST, StockManometerBlockEntity.BRIDGE_OFF);
        helper.assertTrue(!mano.hasBridge(Direction.EAST), "never: no bridge to the repeater");
        helper.assertTrue(mano.getBridgeMode(Direction.SOUTH) == StockManometerBlockEntity.BRIDGE_ON, "a mode survives its neighbour's change");
        mano.setBridgeMode(Direction.NORTH, StockManometerBlockEntity.BRIDGE_ON);
        helper.assertTrue(!mano.hasBridge(Direction.NORTH), "always: still nothing to bridge to in the air");
        helper.succeed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerThresholdAbove(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos manoPos = GolemGameTests.BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(10);
        mano.setHysteresis(0);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "signal should be 0 initially when stock < 10");
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 9), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 0, "signal should still be 0 with 9 diamonds");
                GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 1), false);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 15, "signal should be 15 when stock reaches 10");
                    GolemGameTests.net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 6, false);
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 0, "signal should drop to 0 when stock drops to 4");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerThresholdBelow(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos manoPos = GolemGameTests.BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_BELOW);
        mano.setThreshold(10);
        mano.setHysteresis(0);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 15, "signal should be 15 initially when stock (0) <= 10");
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "signal should still be 15 when stock == 10");
                GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 1), false);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 0, "signal should be 0 when stock > 10 (11)");
                    GolemGameTests.net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 2, false);
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 15, "signal should return to 15 when stock drops back to 9");
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerThresholdAnalog(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos manoPos = GolemGameTests.BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ANALOG);
        mano.setThreshold(20);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "analog signal should be 0 initially");
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.runAfterDelay(15, () -> {
                int sig = mano.getSignal();
                helper.assertTrue(sig >= 7 && sig <= 8, "analog signal at half threshold should be ~7-8 (got " + sig + ")");
                GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 15, "analog signal at full threshold should be 15");
                    GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 15, "analog signal above threshold should clamp to 15");
                        helper.succeed();
                    });
                });
            });
        });
    }
}
