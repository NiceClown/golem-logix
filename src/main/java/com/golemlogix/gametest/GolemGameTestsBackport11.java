package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blocks.ChannelCounterBlock;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests backported from main (2.1.4): the Channel Counter reads the node it is fixed to - the channels running through it and its
 * capacity - and a right-click switches to the capacity.
 * Shared helpers (array, network, test player) live in {@link GolemGameTests}.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport11 {
    private static final String T = "empty";

    /**
     * A counter astride a basic pipe that carries one terminal: it shows 1 of 16 channels, a right-click shows the capacity, and
     * with the core gone every tube goes out.
     */
    @GameTest(template = T, timeoutTicks = 200)
    public static void channelCounterReadsThePipe(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        GolemGameTests.putBay(helper, GolemGameTests.BAY);
        BlockPos pipe = new BlockPos(7, 1, 6);
        helper.setBlock(pipe, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(pipe.east(), ModBlocks.ANALOG_TERMINAL.get());
        BlockPos counter = pipe.above();
        helper.setBlock(counter, ModBlocks.CHANNEL_COUNTER.get().defaultBlockState()
                .setValue(ChannelCounterBlock.FACING, Direction.NORTH)
                .setValue(ChannelCounterBlock.MOUNT, ChannelCounterBlock.Mount.PIPE_BASIC));

        helper.runAfterDelay(40, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            GolemNetwork net = GolemGameTests.net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(be != null && be.online(), "the counter is online: " + be);
            helper.assertTrue(be.used() == net.loadAt(helper.absolutePos(pipe)),
                    "the counter shows the pipe's load " + net.loadAt(helper.absolutePos(pipe)) + ", got " + be.used());
            helper.assertTrue(be.capacity() == 16, "a basic pipe carries 16, got " + be.capacity());

            be.toggleMode();
            helper.assertTrue(be.showsCapacity(), "a right-click switches to the capacity");
            helper.assertTrue(be.capacity() == 16, "the capacity is the same after the switch");

            // no core: the network is offline and every tube goes out
            helper.setBlock(GolemGameTests.CORE, Blocks.AIR);
        });
        helper.runAfterDelay(160, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(!GolemGameTests.net(helper, GolemGameTests.ORIGIN).isOnline(), "the network itself is offline");
            helper.assertTrue(be != null && !be.online() && be.used() == 0, "offline the counter is dark: " + be);
            helper.succeed();
        });
    }

    /** A counter on a dense pipe shows 64, and the mount follows when the pipe below it changes from basic to dense. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void channelCounterDensePipeAndMountFollows(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos pipe = new BlockPos(7, 1, 6);
        helper.setBlock(pipe, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(pipe.east(), ModBlocks.ANALOG_TERMINAL.get());
        BlockPos counter = pipe.above();
        helper.setBlock(counter, ModBlocks.CHANNEL_COUNTER.get().defaultBlockState()
                .setValue(ChannelCounterBlock.FACING, Direction.NORTH)
                .setValue(ChannelCounterBlock.MOUNT, ChannelCounterBlock.Mount.PIPE_BASIC));

        helper.runAfterDelay(40, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(be != null && be.online() && be.capacity() == 16,
                    "on a basic pipe the capacity is 16, got " + (be == null ? "null" : be.capacity()));
            // the pipe below is replaced by a dense one: the mount follows
            helper.setBlock(pipe, ModBlocks.DENSE_FROST_PIPE.get());
        });
        helper.runAfterDelay(80, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(be != null && be.capacity() == 64, "on a dense pipe the capacity is 64, got " + (be == null ? "null" : be.capacity()));
            helper.succeed();
        });
    }

    /** A counter on the side of a corner bushing reads that bushing (its line and the whole network); it takes no channel of its own. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void channelCounterOnBushingSide(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        // the counter hangs on the east side of the corner bushing at (6,1,6), tubes facing east
        BlockPos bushing = new BlockPos(6, 1, 6);
        BlockPos counter = new BlockPos(7, 1, 6);
        helper.setBlock(counter, ModBlocks.CHANNEL_COUNTER.get().defaultBlockState()
                .setValue(ChannelCounterBlock.FACING, Direction.EAST)
                .setValue(ChannelCounterBlock.MOUNT, ChannelCounterBlock.Mount.SIDE_BLOCK));

        helper.runAfterDelay(40, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            GolemNetwork net = GolemGameTests.net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(be != null && be.online(), "the counter on the bushing is online: " + be);
            helper.assertTrue(be.used() == net.loadAt(helper.absolutePos(bushing)), "used mirrors the bushing's line: " + be.used() + " vs " + net.loadAt(helper.absolutePos(bushing)));
            helper.assertTrue(be.capacity() == 64, "a bushing's line carries 64, got " + be.capacity());
            helper.succeed();
        });
    }

    /** A counter on a plain block (no network node under it) stays dark and does not join the network. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void channelCounterOffTheNetwork(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos floor = new BlockPos(2, 1, 2), counter = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(counter, ModBlocks.CHANNEL_COUNTER.get());
        helper.runAfterDelay(40, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(be != null && !be.online() && be.used() == 0 && be.capacity() == 0,
                    "a counter on stone reads nothing: " + be);
            helper.succeed();
        });
    }

    /** Removing the block the counter is fixed to (a pipe) leaves it dark instead of crashing. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void channelCounterNodeRemoved(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos pipe = new BlockPos(7, 1, 6);
        helper.setBlock(pipe, ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos counter = pipe.above();
        helper.setBlock(counter, ModBlocks.CHANNEL_COUNTER.get().defaultBlockState()
                .setValue(ChannelCounterBlock.FACING, Direction.NORTH)
                .setValue(ChannelCounterBlock.MOUNT, ChannelCounterBlock.Mount.PIPE_BASIC));

        helper.runAfterDelay(40, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(be != null && be.online(), "the counter starts online: " + be);
            // the pipe it is fixed to is taken away
            helper.setBlock(pipe, Blocks.AIR);
        });
        helper.runAfterDelay(160, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(be != null && !be.online() && be.used() == 0, "with its node gone the counter is dark: " + be);
            helper.succeed();
        });
    }

    /** Toggling the mode twice returns to showing the used channels. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void channelCounterToggleTwice(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos floor = new BlockPos(2, 1, 2), counter = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(counter, ModBlocks.CHANNEL_COUNTER.get());
        helper.runAfterDelay(40, () -> {
            var be = (ChannelCounterBlockEntity) helper.getBlockEntity(counter);
            helper.assertTrue(be != null && !be.showsCapacity(), "starts showing used channels");
            be.toggleMode();
            helper.assertTrue(be.showsCapacity(), "after one toggle it shows the capacity");
            be.toggleMode();
            helper.assertTrue(!be.showsCapacity(), "after two toggles it shows the used channels again");
            helper.succeed();
        });
    }
}
