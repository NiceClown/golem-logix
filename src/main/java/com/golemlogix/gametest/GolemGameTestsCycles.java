package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The autocrafting tree under the shapes that used to hang or overflow it: a recipe that needs itself, a ring of three, a fuzzy
 * input that leads back into its own path, a pipe loop around the array, and every newer device in a network without a core.
 * Shared helpers (array, network, cards) live in {@link GolemGameTests}.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsCycles {
    private static final String T = "empty";

    /** A recipe that needs itself: the planner does not hang, reports the item missing, and starts no job. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void autocraftSelfCycle(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (com.golemlogix.blockentity.PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.IRON_INGOT)))));
        helper.runAfterDelay(5, () -> {
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            CraftingPlan plan = CraftingPlanner.plan(GolemGameTests.net(helper, bay), iron, 1);
            helper.assertTrue(!plan.missing().isEmpty(), "a recipe that needs itself cannot be made: " + plan.missing());
            helper.assertTrue(GolemGameTests.net(helper, bay).startCrafting(iron, 1).type() != GolemNetwork.StartType.STARTED,
                    "and no job starts");
            helper.succeed();
        });
    }

    /** Three recipes in a ring (iron - gold - diamond - iron): no stack overflow, nothing crafted out of thin air. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void autocraftThreeCycle(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (com.golemlogix.blockentity.PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.DIAMOND)), List.of(new ItemStack(Items.GOLD_INGOT)))));
        provider.getContainer().setItem(2, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.DIAMOND)))));
        helper.runAfterDelay(5, () -> {
            CraftingPlan plan = CraftingPlanner.plan(GolemGameTests.net(helper, bay),
                    ItemKey.of(new ItemStack(Items.IRON_INGOT)), 1);
            helper.assertTrue(!plan.missing().isEmpty(), "a ring of recipes cannot be made: " + plan.missing());
            helper.succeed();
        });
    }

    /**
     * A fuzzy input whose only pattern stands on an item of the same path (oak from birch, "any log"): the planner steps over the
     * alternative instead of walking into itself, and reports the birch as missing rather than crafting the oak out of itself.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void autocraftFuzzyCycleStepsOver(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        var provider = (com.golemlogix.blockentity.PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.BIRCH_LOG)), List.of(new ItemStack(Items.OAK_LOG)), List.of("#minecraft:logs"))));
        helper.runAfterDelay(5, () -> {
            CraftingPlan plan = CraftingPlanner.plan(GolemGameTests.net(helper, bay),
                    ItemKey.of(new ItemStack(Items.OAK_LOG)), 1);
            ItemKey birch = ItemKey.of(new ItemStack(Items.BIRCH_LOG));
            helper.assertTrue(plan.missing().containsKey(birch), "the birch is missing: " + plan.missing());
            helper.assertTrue(!plan.missing().containsKey(ItemKey.of(new ItemStack(Items.OAK_LOG))),
                    "and the oak is not asked of itself: " + plan.missing());
            helper.succeed();
        });
    }

    /** A pipe loop around the array: the network takes the loop in one pass, the devices on it are active, nothing hangs. */
    @GameTest(template = T, timeoutTicks = 120)
    public static void pipeLoopJoinsOnce(GameTestHelper helper) {
        GolemGameTests.baseNetwork(helper);
        for (BlockPos p : new BlockPos[]{new BlockPos(3, 1, 4), new BlockPos(3, 1, 5), new BlockPos(3, 1, 6), new BlockPos(4, 1, 6)}) {
            GolemGameTests.put(helper, p, ModBlocks.BASIC_SNOW_PIPE.get());
        }
        GolemGameTests.put(helper, new BlockPos(2, 1, 5), ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = GolemGameTests.net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(n.isOnline(), "the loop did not break the network");
            helper.assertTrue(n.deviceCount() >= 1, "the terminal on the loop is in the network: " + n.deviceCount());
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(new BlockPos(2, 1, 5))), "and it has a channel");
            helper.succeed();
        });
    }

    /**
     * Every device of the newer ones in a network with no core at all: nothing throws, the network stays offline, and the devices
     * stand by - the counter is dark and nothing is allocated.
     */
    @GameTest(template = T, timeoutTicks = 120)
    public static void newDevicesInDeadNetwork(GameTestHelper helper) {
        // the array without its core: the network is offline whatever the config says about Source
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.put(helper, GolemGameTests.CORE, Blocks.AIR);
        GolemGameTests.put(helper, new BlockPos(2, 1, -1), ModBlocks.BASIC_SNOW_PIPE.get());
        GolemGameTests.put(helper, new BlockPos(2, 2, -1), ModBlocks.CHANNEL_COUNTER.get());
        GolemGameTests.put(helper, new BlockPos(4, 1, -1), ModBlocks.STOCK_MANOMETER.get());
        GolemGameTests.put(helper, new BlockPos(6, 1, -1), ModBlocks.STOCK_REQUESTER.get());
        GolemGameTests.put(helper, new BlockPos(8, 1, -1), ModBlocks.CRYO_DIODE.get());
        GolemGameTests.put(helper, new BlockPos(10, 1, -1), ModBlocks.SECURITY_TERMINAL.get());
        GolemGameTests.put(helper, new BlockPos(12, 1, -1), ModBlocks.COPPER_TANK.get());
        GolemGameTests.put(helper, new BlockPos(14, 1, -1), ModBlocks.FORMATION_BUS.get());
        for (BlockPos p : new BlockPos[]{new BlockPos(16, 1, -1), new BlockPos(17, 1, -1), new BlockPos(16, 1, 0), new BlockPos(17, 1, 0)}) {
            GolemGameTests.put(helper, p, ModBlocks.CRYO_SOURCE_JAR.get());
        }
        helper.runAfterDelay(40, () -> {
            GolemNetwork n = GolemGameTests.net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(!n.isOnline(), "the network has no core");
            var counter = (com.golemlogix.blockentity.ChannelCounterBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, -1));
            helper.assertTrue(counter != null && !counter.online(), "the counter is dark");
            helper.assertTrue(n.channelsUsed() == 0, "nothing is allocated: " + n.channelsUsed());
            helper.succeed();
        });
    }
}
