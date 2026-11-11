package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Autocrafting, mainframe, jar and temper tests backported from main (2.1.4). */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport8 {
    private static final String T = "empty";

    // The same positions as in GolemGameTests
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos JAR = ORIGIN.below();
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    // ----------------------------------------------------------- helpers

    /**
     * A real reload: the block entity is replaced by a new object that knows only what was saved, and the network is built again.
     * Loading a tag into the same object proves nothing, it still remembers everything.
     */
    private static <E extends BlockEntity> E reload(GameTestHelper helper, BlockPos rel, Class<E> type) {
        BlockPos abs = helper.absolutePos(rel);
        var level = helper.getLevel();
        var old = level.getBlockEntity(abs);
        CompoundTag tag = old.saveWithFullMetadata(level.registryAccess());
        var fresh = BlockEntity.loadStatic(abs, old.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);
        GolemNetwork.refresh(level, abs);
        return type.cast(fresh);
    }

    /** The stick and plank recipes on a provider, and a large storage. */
    private static BlockPos hugeRig(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        for (int i = 0; i < 4; i++) {
            drive.getContainer().setItem(i, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        }
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
        }
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(true, grid, List.of(new ItemStack(Items.STICK, 4)))));
        return bay;
    }

    /** A north-facing Cryo-Mainframe with the owner at ctl: glass, casings at the back and atlants on the sides. */
    private static void buildMainframe(GameTestHelper helper, BlockPos ctl) {
        helper.setBlock(ctl, ModBlocks.CRYO_MAINFRAME.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.CryoMainframeBlock.FACING, Direction.NORTH));
        BlockPos abs = helper.absolutePos(ctl);
        // the atlants face east: their direction does not matter for assembly
        for (int i = 0; i < com.golemlogix.system.MainframeStructure.CELLS.length; i++) {
            BlockPos p = com.golemlogix.system.MainframeStructure.cellPos(abs, Direction.NORTH, i);
            helper.getLevel().setBlock(p, com.golemlogix.system.MainframeStructure.originalState(i, Direction.EAST), 3);
        }
    }

    private static CryoCalculatorBlockEntity calc(GameTestHelper helper, BlockPos rel) {
        return (CryoCalculatorBlockEntity) helper.getBlockEntity(rel);
    }

    // ----------------------------------------------------------- autocrafting

    /** Two Cryo-Calculators run two different orders at the same time; both finish with right results and no items lost or duplicated. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void autocraftTwoCalculatorsAtOnce(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos cpu1 = CPU;
        BlockPos cpu2 = BUS_OUT;
        helper.setBlock(cpu1, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(cpu2, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());

        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);

        // Pattern 1: 1 Oak Log -> 4 Oak Planks
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        // Pattern 2: 1 Iron Block -> 9 Iron Ingots
        provider.getContainer().setItem(1, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.IRON_BLOCK))));

        ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey ingotsKey = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        ItemKey logKey = ItemKey.of(new ItemStack(Items.OAK_LOG));
        ItemKey ironBlockKey = ItemKey.of(new ItemStack(Items.IRON_BLOCK));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 5), false);
            n.insertItem(new ItemStack(Items.IRON_BLOCK, 3), false);

            var start1 = n.startCrafting(planksKey, 20);
            var start2 = n.startCrafting(ingotsKey, 27);
            helper.assertTrue(start1.type() == GolemNetwork.StartType.STARTED, "order 1 started");
            helper.assertTrue(start2.type() == GolemNetwork.StartType.STARTED, "order 2 started");

            CryoCalculatorBlockEntity be1 = calc(helper, cpu1);
            CryoCalculatorBlockEntity be2 = calc(helper, cpu2);
            helper.assertTrue(!be1.isIdle() && !be2.isIdle(), "both calculators running at once");

            helper.succeedWhen(() -> {
                helper.assertTrue(be1.isIdle() && be2.isIdle(), "both calculators finished and idle");
                helper.assertTrue(n.itemCountOf(planksKey) == 20, "20 planks delivered");
                helper.assertTrue(n.itemCountOf(ingotsKey) == 27, "27 iron ingots delivered");
                helper.assertTrue(n.itemCountOf(logKey) == 0, "all 5 oak logs consumed cleanly");
                helper.assertTrue(n.itemCountOf(ironBlockKey) == 0, "all 3 iron blocks consumed cleanly");
            });
        });
    }

    /** Source runs out during a job; job does not lose items, and finishes once power returns. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void autocraftSurvivesPowerLoss(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey logKey = ItemKey.of(new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            if (!com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(n.isOnline(), "without Ars Nouveau the network needs no Source");
                helper.succeed();
                return;
            }

            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            var start = n.startCrafting(planksKey, 20);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "job started");

            CryoCalculatorBlockEntity cpu = calc(helper, CPU);
            helper.assertTrue(!cpu.isIdle(), "calculator working on job");

            // Cut power by removing Creative Source Jar
            helper.setBlock(JAR, Blocks.AIR);

            // the core looks at its supply once a second, at a moment of its own: this waits for that whatever the moment
            helper.runAfterDelay(30, () -> {
                GolemNetwork nOffline = GolemGameTests.net(helper, bay);
                helper.assertTrue(!nOffline.isOnline(), "network goes offline without Source");
                helper.assertTrue(!cpu.isIdle(), "job preserved in calculator across power loss");

                // Restore power
                GolemGameTests.powered(helper);

                helper.runAfterDelay(20, () -> {
                    GolemNetwork nRestored = GolemGameTests.net(helper, bay);
                    helper.assertTrue(nRestored.isOnline(), "network online after power restored");

                    helper.succeedWhen(() -> {
                        helper.assertTrue(cpu.isIdle(), "job finishes once power returns");
                        helper.assertTrue(nRestored.itemCountOf(planksKey) == 20, "20 planks delivered with no loss");
                        helper.assertTrue(nRestored.itemCountOf(logKey) == 5, "remaining 5 oak logs intact in storage");
                    });
                });
            });
        });
    }

    /**
     * A job of a nested plan (sticks from planks from logs) is saved and reloaded while part way through,
     * then finishes with the right result and no items lost or duplicated.
     */
    @GameTest(template = T, timeoutTicks = 400)
    public static void autocraftMultiStepSurvivesReload(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);

        // Pattern 1: 1 Oak Log -> 4 Oak Planks
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        // Pattern 2: 2 Oak Planks -> 4 Sticks
        java.util.List<ItemStack> stickGrid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            stickGrid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
        }
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(true, stickGrid, List.of(new ItemStack(Items.STICK, 4)))));

        ItemKey stickKey = ItemKey.of(new ItemStack(Items.STICK));
        ItemKey plankKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey logKey = ItemKey.of(new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            // 5 logs: 2 needed for 8 planks -> 16 sticks; 3 logs should remain untouched
            n.insertItem(new ItemStack(Items.OAK_LOG, 5), false);

            var start = n.startCrafting(stickKey, 16);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "multi-step job started: " + start.type());

            CryoCalculatorBlockEntity cpu = calc(helper, CPU);
            helper.assertTrue(!cpu.isIdle(), "calculator actively working on job");

            // Part way through (after 5 ticks, job has started execution of steps)
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(!cpu.isIdle(), "job still in progress when reloaded");
                CryoCalculatorBlockEntity loaded = reload(helper, CPU, CryoCalculatorBlockEntity.class);
                helper.assertTrue(!loaded.isIdle(), "job preserved in calculator after reload");

                helper.succeedWhen(() -> {
                    GolemNetwork nAfter = GolemGameTests.net(helper, bay);
                    helper.assertTrue(loaded.isIdle(), "calculator finishes job");
                    helper.assertTrue(nAfter.itemCountOf(stickKey) == 16, "exactly 16 sticks delivered (got " + nAfter.itemCountOf(stickKey) + ")");
                    helper.assertTrue(nAfter.itemCountOf(plankKey) == 0, "no intermediate planks leaked or duplicated (got " + nAfter.itemCountOf(plankKey) + ")");
                    helper.assertTrue(nAfter.itemCountOf(logKey) == 3, "exactly 3 oak logs remaining in storage (got " + nAfter.itemCountOf(logKey) + ")");
                });
            });
        });
    }

    /**
     * A portion is inside a machine when the world is reloaded. The job still knows what it waits for: in wait-for-result mode it
     * does not load the next portion, and what the machine hands back goes to the job, which goes on to the step that needs it.
     */
    @GameTest(template = T, timeoutTicks = 500)
    public static void autocraftMachineStepSurvivesReload(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.setBlocking(true);
        // Machine step: 1 raw iron -> 1 ingot. Crafting step that needs its result: 9 ingots -> 1 block
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(new ItemStack(Items.IRON_INGOT));
        }
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(true, grid, List.of(new ItemStack(Items.IRON_BLOCK)))));

        BlockPos machinePos = PROV.east();
        helper.setBlock(machinePos, Blocks.CHEST);
        ChestBlockEntity machine = (ChestBlockEntity) helper.getBlockEntity(machinePos);
        ItemKey raw = ItemKey.of(new ItemStack(Items.RAW_IRON));
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        ItemKey block = ItemKey.of(new ItemStack(Items.IRON_BLOCK));
        java.util.function.IntSupplier inMachine = () -> {
            int count = 0;
            for (int i = 0; i < machine.getContainerSize(); i++) {
                count += machine.getItem(i).getCount();
            }
            return count;
        };

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 12), false);
            var start = n.startCrafting(block, 1);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "the job started: " + start.type());
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(inMachine.getAsInt() == 1, "one portion in the machine in wait mode (got " + inMachine.getAsInt() + ")");
            CryoCalculatorBlockEntity loaded = reload(helper, CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(!loaded.isIdle(), "the job is still there after the reload");
        });
        helper.runAfterDelay(50, () -> helper.assertTrue(inMachine.getAsInt() == 1,
                "it still waits for that portion after the reload (in the machine: " + inMachine.getAsInt() + ")"));
        // From now on the machine works
        for (int t = 51; t <= 350; t++) {
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < machine.getContainerSize(); i++) {
                    ItemStack in = machine.getItem(i);
                    if (in.is(Items.RAW_IRON)) {
                        int count = in.getCount();
                        machine.setItem(i, ItemStack.EMPTY);
                        GolemGameTests.net(helper, bay).insertFromMachine(new ItemStack(Items.IRON_INGOT, count));
                    }
                }
            });
        }
        helper.runAfterDelay(52, () -> helper.succeedWhen(() -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            helper.assertTrue(n.itemCountOf(block) == 1, "the block was made (got " + n.itemCountOf(block) + ")");
            helper.assertTrue(n.itemCountOf(ingot) == 0, "no ingot left over (got " + n.itemCountOf(ingot) + ")");
            helper.assertTrue(n.itemCountOf(raw) == 3, "exactly nine raw iron were used (left " + n.itemCountOf(raw) + ")");
            helper.assertTrue(calc(helper, CPU).isIdle(), "the calculator is free");
        }));
    }

    /**
     * An order of hundreds of thousands of items goes through. Sticks: 150000 crafts from planks, 75000 crafts of planks from logs.
     * Every step is accounted for, and the server's time limit holds it back within a tick without stopping it.
     */
    @GameTest(template = T, timeoutTicks = 1500)
    public static void autocraftHugeOrder(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        ItemKey stick = ItemKey.of(new ItemStack(Items.STICK));
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey log = ItemKey.of(new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 100_000), false);
            var start = n.startCrafting(stick, 600_000);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "the huge order started: " + start.type());
            helper.assertTrue(start.plan().totalExecutions() == 225_000, "225000 executions, got " + start.plan().totalExecutions());

            CraftingJob job = calc(helper, CPU).getJob();
            job.tick(n, 1_000_000);
            long first = job.doneExecutions();
            helper.assertTrue(first > 0 && first < 225_000, "the time limit stopped the cycle part way (did " + first + ")");
            helper.assertTrue(job.status() == CraftingJob.Status.RUNNING, "and the job goes on: " + job.status());
            job.tick(n, 1_000_000);
            helper.assertTrue(job.doneExecutions() > first, "past the limit a job still does a step");
        });
        // the limit is shared with all the other jobs on the server: this takes a part of it, to leave them theirs
        for (int t = 6; t <= 1400; t++) {
            helper.runAfterDelay(t, () -> {
                CraftingJob job = calc(helper, CPU).getJob();
                if (job != null) {
                    job.tick(GolemGameTests.net(helper, bay), 1000);
                }
            });
        }
        helper.runAfterDelay(7, () -> helper.succeedWhen(() -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            helper.assertTrue(calc(helper, CPU).isIdle(), "the calculator finishes");
            helper.assertTrue(n.itemCountOf(stick) == 600_000, "600000 sticks, got " + n.itemCountOf(stick));
            helper.assertTrue(n.itemCountOf(plank) == 0, "no planks left, got " + n.itemCountOf(plank));
            helper.assertTrue(n.itemCountOf(log) == 25_000, "25000 logs left, got " + n.itemCountOf(log));
        }));
    }

    /**
     * Planning does not depend on the depth of the recipes or on the amount. Thirty levels of three items, each made from all three
     * of the level below: 3^30 paths through the recipes, which no walk over them would ever finish. A shared ingredient is rounded
     * up once, not per consumer, and steps come after what they need.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void plannerDeepAndHuge(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            List<net.minecraft.world.item.Item> items = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                    .filter(i -> i != Items.AIR && new ItemStack(i).getMaxStackSize() == 64).toList();
            int base = 100, width = 3, levels = 30;
            for (int l = 1; l <= levels; l++) {
                for (int j = 0; j < width; j++) {
                    List<ItemStack> in = new java.util.ArrayList<>();
                    for (int k = 0; k < width; k++) {
                        in.add(new ItemStack(items.get(base + (l - 1) * width + k)));
                    }
                    ItemStack out = new ItemStack(items.get(base + l * width + j));
                    n.patternIndex().put(ItemKey.of(out), new GolemNetwork.PatternRef(new PatternData(false, in, List.of(out)), null));
                }
            }
            var plan = com.golemlogix.crafting.CraftingPlanner.plan(n, ItemKey.of(new ItemStack(items.get(base + levels * width))), 1);
            long powerOf3 = 1;
            for (int i = 0; i < levels - 1; i++) {
                powerOf3 *= 3;
            }
            // one at the top, then the levels below take 3, 9, 27... executions in all, down to 3^29: the sum of 3^0..3^29
            long executions = (powerOf3 * 3 - 1) / 2;
            helper.assertTrue(plan.totalExecutions() == executions, "executions " + plan.totalExecutions() + ", expected " + executions);
            helper.assertTrue(plan.steps().size() == 1 + width * (levels - 1), "one step per recipe used, got " + plan.steps().size());
            helper.assertTrue(plan.missing().size() == width, "the three base items are missing, got " + plan.missing().size());
            for (long need : plan.missing().values()) {
                helper.assertTrue(need == powerOf3, "each base item is needed " + powerOf3 + " times, got " + need);
            }

            // A and B both need one plank, a craft gives four, there is one log
            n.patternIndex().clear();
            ItemStack a = new ItemStack(Items.STICK), b = new ItemStack(Items.BOWL), top = new ItemStack(Items.BEETROOT_SOUP);
            PatternData plank = new PatternData(false, List.of(new ItemStack(Items.OAK_LOG)), List.of(new ItemStack(Items.OAK_PLANKS, 4)));
            PatternData topPattern = new PatternData(false, List.of(a, b), List.of(top));
            n.patternIndex().put(ItemKey.of(top), new GolemNetwork.PatternRef(topPattern, null));
            n.patternIndex().put(ItemKey.of(a), new GolemNetwork.PatternRef(new PatternData(false, List.of(new ItemStack(Items.OAK_PLANKS)), List.of(a)), null));
            n.patternIndex().put(ItemKey.of(b), new GolemNetwork.PatternRef(new PatternData(false, List.of(new ItemStack(Items.OAK_PLANKS)), List.of(b)), null));
            n.patternIndex().put(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), new GolemNetwork.PatternRef(plank, null));
            n.insertItem(new ItemStack(Items.OAK_LOG, 1), false);
            var shared = com.golemlogix.crafting.CraftingPlanner.plan(n, ItemKey.of(top), 1);
            helper.assertTrue(shared.missing().isEmpty(), "one log is enough for both: " + shared.missing());
            helper.assertTrue(shared.totalExecutions() == 4, "four executions, got " + shared.totalExecutions());
            helper.assertTrue(shared.steps().get(0).pattern().equals(plank), "the planks come first");
            helper.assertTrue(shared.steps().get(3).pattern().equals(topPattern), "the order comes last");

            GolemNetwork.patternsChanged();
            helper.succeed();
        });
    }

    /**
     * An idle calculator lends a running job its steps, so one order goes as fast as the two together. It stays free: a new order
     * takes it at once, and both finish with the right results.
     */
    @GameTest(template = T, timeoutTicks = 600)
    public static void idleCalculatorLendsItsSteps(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        helper.setBlock(BUS_OUT, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        CryoCalculatorBlockEntity[] cpus = {calc(helper, CPU), calc(helper, BUS_OUT)};
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey stick = ItemKey.of(new ItemStack(Items.STICK));
        int[] owner = new int[1];

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 104), false);
            n.insertItem(new ItemStack(Items.OAK_PLANKS, 2), false);
            var start = n.startCrafting(plank, 400);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "the first order started: " + start.type());
            owner[0] = cpus[0].isIdle() ? 1 : 0;
        });
        // 40 ticks are 10 cycles of one step each: alone it could do 11 at most
        helper.runAfterDelay(45, () -> {
            CraftingJob job = cpus[owner[0]].getJob();
            CryoCalculatorBlockEntity other = cpus[1 - owner[0]];
            helper.assertTrue(job.doneExecutions() >= 16, "two calculators work on it (done " + job.doneExecutions() + ")");
            helper.assertTrue(other.isIdle(), "the helper has no job of its own");
            helper.assertTrue(other.helpedJob() == job, "and knows whom it helps");

            GolemNetwork n = GolemGameTests.net(helper, bay);
            var second = n.startCrafting(stick, 4);
            helper.assertTrue(second.type() == GolemNetwork.StartType.STARTED, "a new order is taken: " + second.type());
            helper.assertTrue(!other.isIdle(), "by the calculator that was helping, at once");
            helper.assertTrue(other.helpedJob() == null || other.getJob() != null, "which now works on its own");
        });
        helper.runAfterDelay(46, () -> helper.succeedWhen(() -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            helper.assertTrue(cpus[0].isIdle() && cpus[1].isIdle(), "both finish");
            helper.assertTrue(n.itemCountOf(plank) == 400, "400 planks, got " + n.itemCountOf(plank));
            helper.assertTrue(n.itemCountOf(stick) == 4, "4 sticks, got " + n.itemCountOf(stick));
        }));
    }

    /** A calculator under a Redstone Module keeps the pace of its signal: it neither gets help nor gives it. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void redstoneCalculatorKeepsToItself(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        helper.setBlock(BUS_OUT, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        CryoCalculatorBlockEntity one = calc(helper, CPU);
        CryoCalculatorBlockEntity two = calc(helper, BUS_OUT);
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        int redstoneSlot = com.golemlogix.upgrade.UpgradeSpec.CALCULATOR.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE);
        // Both ignore the signal, so they work; the module alone is what should keep them apart
        for (CryoCalculatorBlockEntity c : List.of(one, two)) {
            c.getUpgrades().setItem(redstoneSlot, new ItemStack(ModItems.REDSTONE_MODULE.get()));
            c.menuData().set(0, com.golemlogix.upgrade.RedstoneMode.IGNORE.ordinal());
        }
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 100), false);
            helper.assertTrue(n.startCrafting(plank, 400).type() == GolemNetwork.StartType.STARTED, "the order started");
        });
        helper.runAfterDelay(45, () -> {
            CryoCalculatorBlockEntity owner = one.isIdle() ? two : one;
            CryoCalculatorBlockEntity other = one.isIdle() ? one : two;
            long done = owner.getJob().doneExecutions();
            helper.assertTrue(done >= 7 && done <= 12, "it works alone at one step a cycle (done " + done + ")");
            helper.assertTrue(other.helpedJob() == null, "the other one does not help");

            // The module on the job alone: it gets no help
            other.getUpgrades().setItem(redstoneSlot, ItemStack.EMPTY);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(other.helpedJob() == null, "a job under a module gets no help");
                // The module on the idle one alone: it gives none
                owner.getUpgrades().setItem(redstoneSlot, ItemStack.EMPTY);
                other.getUpgrades().setItem(redstoneSlot, new ItemStack(ModItems.REDSTONE_MODULE.get()));
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(other.helpedJob() == null, "a calculator under a module gives no help");
                    // No modules at all: it helps
                    other.getUpgrades().setItem(redstoneSlot, ItemStack.EMPTY);
                    helper.runAfterDelay(20, () -> {
                        helper.assertTrue(other.helpedJob() == owner.getJob(), "positive control: without modules it helps");
                        helper.succeed();
                    });
                });
            });
        });
    }

    /**
     * Thousands of portions inside machines take one record in the save per Provider, not one each. They survive a reload, and every
     * result comes back to the job.
     */
    @GameTest(template = T, timeoutTicks = 1800)
    public static void autocraftManyPortionsKeepTheSaveSmall(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        ((PatternProviderBlockEntity) helper.getBlockEntity(PROV)).getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        helper.setBlock(PROV.east(), Blocks.CHEST);
        ChestBlockEntity machine = (ChestBlockEntity) helper.getBlockEntity(PROV.east());
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 1500), false);
            helper.assertTrue(n.startCrafting(ingot, 1500).type() == GolemNetwork.StartType.STARTED, "the order started");
        });
        boolean[] reloaded = new boolean[1];
        for (int t = 6; t <= 1700; t++) {
            helper.runAfterDelay(t, () -> {
                CraftingJob job = calc(helper, CPU).getJob();
                if (job != null && job.remainingExecutions() > 0) {
                    job.tick(GolemGameTests.net(helper, bay), 1500);
                    return;
                }
                if (job == null) {
                    return;
                }
                // all are out: first the save, then the machine hands everything back
                if (!reloaded[0]) {
                    reloaded[0] = true;
                    helper.assertTrue(job.awaitingTotal() == 1500, "1500 portions are out, got " + job.awaitingTotal());
                    int chars = job.save(helper.getLevel().registryAccess()).toString().length();
                    helper.assertTrue(chars < 3000, "the job's save stays small (" + chars + " characters)");
                    reload(helper, CPU, CryoCalculatorBlockEntity.class);
                    return;
                }
                for (int i = 0; i < machine.getContainerSize(); i++) {
                    int count = machine.getItem(i).getCount();
                    if (count > 0) {
                        machine.setItem(i, ItemStack.EMPTY);
                        GolemGameTests.net(helper, bay).insertFromMachine(new ItemStack(Items.IRON_INGOT, count));
                    }
                }
            });
        }
        helper.runAfterDelay(7, () -> helper.succeedWhen(() -> {
            helper.assertTrue(reloaded[0], "the portions went out and the job was reloaded");
            helper.assertTrue(calc(helper, CPU).isIdle(), "the job finishes");
            long ingots = GolemGameTests.net(helper, bay).itemCountOf(ingot);
            helper.assertTrue(ingots == 1500, "every ingot came back, got " + ingots);
        }));
    }

    // ----------------------------------------------------------- mainframe

    /** The idle cores of a mainframe lend a running job their steps, and stay free: a new order takes a core at once. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void idleMainframeCoresLend(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos ctl = new BlockPos(5, 1, 1);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_BUTTON), new ItemStack(Items.OAK_PLANKS))));
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        helper.runAfterDelay(5, () -> {
            GolemGameTests.put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
            buildMainframe(helper, ctl);
        });
        helper.runAfterDelay(35, () -> {
            var mf = (CryoMainframeBlockEntity) helper.getBlockEntity(ctl);
            mf.getCalculators().setItem(0, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
            mf.getCalculators().setItem(5, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
            helper.assertTrue(mf.cores().size() == 4, "four cores, got " + mf.cores().size());
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 510), false);
            helper.assertTrue(n.startCrafting(plank, 2000).type() == GolemNetwork.StartType.STARTED, "the order started");
        });
        // 40 ticks are 10 cycles: one core does 11 steps at most, four about 40
        helper.runAfterDelay(75, () -> {
            var mf = (CryoMainframeBlockEntity) helper.getBlockEntity(ctl);
            helper.assertTrue(mf.busyCores() == 1, "one core holds the order, busy: " + mf.busyCores());
            GolemNetwork n = GolemGameTests.net(helper, bay);
            long done = 0;
            for (var cpu : n.cpus()) {
                done += cpu.getJob() == null ? 0 : cpu.getJob().doneExecutions();
            }
            helper.assertTrue(done >= 28, "the idle cores help (done " + done + ")");
            var second = n.startCrafting(ItemKey.of(new ItemStack(Items.OAK_BUTTON)), 1);
            helper.assertTrue(second.type() == GolemNetwork.StartType.STARTED, "a new order is taken at once: " + second.type());
            helper.assertTrue(mf.busyCores() == 2, "by a core that was helping, busy: " + mf.busyCores());
            helper.succeed();
        });
    }

    // ----------------------------------------------------------- jars and temper

    /**
     * Cryo-Source Jars join: four in a square into a Giga Jar, nine into a reactor. Their Source is added up, they hold a little more
     * than apart and count as one supplier; a jar broken out takes the whole apart and the rest share out the Source. Three in a row
     * or at different heights stay jars.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void cryoJarsJoin(GameTestHelper helper) {
        var level = helper.getLevel();
        Block jarBlock = ModBlocks.CRYO_SOURCE_JAR.get();
        int one = com.golemlogix.config.GolemConfig.CRYO_JAR_CAPACITY.get();
        int gigaBonus = com.golemlogix.config.GolemConfig.CRYO_JAR_GIGA_BONUS.get(), superBonus = com.golemlogix.config.GolemConfig.CRYO_JAR_SUPER_BONUS.get();
        java.util.function.Function<BlockPos, CryoJarBlockEntity> jar = p -> (CryoJarBlockEntity) helper.getBlockEntity(p);
        java.util.function.Consumer<BlockPos> put = p -> CryoJarBlock.place(level, helper.absolutePos(p), Direction.SOUTH, jarBlock);
        java.util.function.Function<BlockPos, JarForm> form = p -> helper.getBlockState(p).getValue(CryoJarBlock.FORM);
        BlockPos c = new BlockPos(2, 1, 2);

        // three in a row and one a block higher are no square
        put.accept(c);
        put.accept(c.east());
        put.accept(c.south());
        put.accept(c.east().south().above());
        jar.apply(c).fill(1000, false);
        jar.apply(c.east()).fill(2000, false);
        jar.apply(c.south()).fill(3000, false);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(form.apply(c) == JarForm.SINGLE, "three jars and a higher one stay jars");
            // the fourth where it belongs
            helper.setBlock(c.east().south().above(2), Blocks.AIR);
            helper.setBlock(c.east().south().above(), Blocks.AIR);
        });
        helper.runAfterDelay(8, () -> put.accept(c.east().south()));
        helper.runAfterDelay(12, () -> {
            for (BlockPos p : List.of(c, c.east(), c.south(), c.east().south())) {
                helper.assertTrue(form.apply(p) == JarForm.GIGA, "the square is a Giga Jar at " + p);
                helper.assertTrue(helper.getBlockState(p.above()).getValue(CryoJarBlock.FORM) == JarForm.GIGA, "the upper halves too");
            }
            var whole = jar.apply(c);
            helper.assertTrue(whole.isController(), "the corner with the lowest x and z holds it");
            helper.assertTrue(whole.capacity() == one * 4 * (100 + gigaBonus) / 100, "four jars and a bonus: " + whole.capacity());
            helper.assertTrue(whole.available() == 6000, "their Source is added up: " + whole.available());
            helper.assertTrue(jar.apply(c.east().south()).available() == 6000, "any jar answers for the whole");
            helper.assertTrue(jar.apply(c.east().south().above()).available() == 6000, "and any half");
            helper.assertTrue(com.golemlogix.system.SourceHelper.asSupplier(jar.apply(c.south().above())) == whole, "as one supplier");
            helper.assertTrue(jar.apply(c.east()).drain(500) == 500 && whole.available() == 5500, "drawn from any jar");
            helper.assertTrue(jar.apply(c.south()).fill(Integer.MAX_VALUE, false) == whole.capacity() - 5500, "and filled, to the top of the whole");
            whole.drain(whole.available() - 8000);

            // a save and a load: the jar that is not the corner still knows the whole
            CompoundTag tag = jar.apply(c.east()).saveWithFullMetadata(level.registryAccess());
            var abs = helper.absolutePos(c.east());
            var fresh = BlockEntity.loadStatic(abs, helper.getBlockState(c.east()), tag, level.registryAccess());
            level.setBlockEntity(fresh);
            helper.assertTrue(((CryoJarBlockEntity) fresh).available() == 8000, "after a reload too");

            // Grow it to nine
            for (BlockPos p : List.of(c.east(2), c.east(2).south(), c.south(2), c.east().south(2), c.east(2).south(2))) {
                put.accept(p);
            }
        });
        helper.runAfterDelay(16, () -> {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    helper.assertTrue(form.apply(c.offset(x, 0, z)) == JarForm.SUPER, "all nine are a reactor");
                }
            }
            var whole = jar.apply(c);
            helper.assertTrue(whole.capacity() == one * 9 * (100 + superBonus) / 100, "nine jars and a bonus: " + whole.capacity());
            helper.assertTrue(whole.available() == 8000, "with the Giga Jar's Source inside: " + whole.available());
            whole.fill(20000, false);
            helper.assertTrue(whole.available() == 28000, "it fills past what jars hold");

            // Break the middle: the eight share out what is there
            level.destroyBlock(helper.absolutePos(c.east().south()), true);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertBlockNotPresent(jarBlock, c.east().south());
            int total = 0, jars = 0;
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (x == 1 && z == 1) {
                        continue;
                    }
                    var j = jar.apply(c.offset(x, 0, z));
                    helper.assertTrue(form.apply(c.offset(x, 0, z)) == JarForm.SINGLE, "with a hole in the middle no square is left");
                    total += j.available();
                    jars++;
                }
            }
            helper.assertTrue(jars == 8 && total == 28000, "the eight kept the Source: " + total);

            // A square breaking with more Source than the jars left can hold keeps what they hold
            var corner = jar.apply(c);
            helper.assertTrue(corner.fill(Integer.MAX_VALUE, true) == one - corner.available(), "a lone jar is its own size again");

            // Fill the hole: all nine again. Then break a corner: a square of four is left, and it joins by itself
            put.accept(c.east().south());
        });
        helper.runAfterDelay(24, () -> {
            helper.assertTrue(form.apply(c) == JarForm.SUPER, "nine again make a reactor");
            helper.assertTrue(jar.apply(c).available() == 28000, "with the Source of all eight: " + jar.apply(c).available());
            level.destroyBlock(helper.absolutePos(c), true);
        });
        helper.runAfterDelay(28, () -> {
            int gigas = 0, total = 0;
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    if (x + z == 0) {
                        continue;
                    }
                    BlockPos p = c.offset(x, 0, z);
                    if (form.apply(p) == JarForm.GIGA) {
                        gigas++;
                        if (jar.apply(p).isController()) {
                            total += jar.apply(p).available();
                        }
                    } else {
                        total += jar.apply(p).available();
                    }
                }
            }
            helper.assertTrue(gigas >= 4 && gigas % 4 == 0, "what is left joined into Giga Jars, jars in them: " + gigas);
            helper.assertTrue(total == 28000, "and kept the Source: " + total);
            helper.succeed();
        });
    }

    /** The temper lifts a tool to netherite's level for its own kind of block, and adds a level of Fortune. */
    @GameTest(template = T, timeoutTicks = 40)
    public static void temperLiftsTools(GameTestHelper helper) {
        var reg = helper.getLevel().registryAccess();
        var obsidian = Blocks.OBSIDIAN.defaultBlockState();
        ItemStack wood = new ItemStack(Items.WOODEN_PICKAXE);
        helper.assertTrue(!wood.isCorrectToolForDrops(obsidian), "a wooden pickaxe does not harvest obsidian");
        ItemStack hand = com.golemlogix.blockentity.AnnihilationBusBlockEntity.temperedHand(wood, obsidian, reg);
        helper.assertTrue(hand.isCorrectToolForDrops(obsidian), "tempered, it does");
        helper.assertTrue(!wood.isEnchanted(), "the real tool stays as it was");
        var fortune = reg.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(fortune, hand) == 1, "with Fortune I");
        ItemStack rich = new ItemStack(Items.IRON_PICKAXE);
        rich.enchant(fortune, 3);
        helper.assertTrue(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(fortune,
                com.golemlogix.blockentity.AnnihilationBusBlockEntity.temperedHand(rich, Blocks.STONE.defaultBlockState(), reg)) == 4, "III becomes IV");
        helper.assertTrue(!com.golemlogix.blockentity.AnnihilationBusBlockEntity.temperLifts(new ItemStack(Items.WOODEN_SHOVEL), obsidian), "a shovel stays a shovel");
        // worn and enchanted things are equipment whatever else they are
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(3);
        helper.assertTrue(com.golemlogix.system.Gear.is(worn) && com.golemlogix.system.Gear.is(rich), "worn and enchanted tools count");
        helper.succeed();
    }
}
