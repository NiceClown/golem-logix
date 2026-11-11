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
     * with the Source jar gone every tube goes out.
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
}
