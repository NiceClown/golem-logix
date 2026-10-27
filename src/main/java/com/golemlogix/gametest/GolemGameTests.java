package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalActionPayload.Action;
import com.golemlogix.recipe.ChronometerUpgradeRecipe;
import com.golemlogix.system.AtlasBlueprint;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.DiskStore;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks of the mod's logic inside a real server: multiblock, network, disks, buses, autocrafting.
 * Run: gradlew runGameTestServer
 *
 * Layout: a 3x3x4 Atlas Array with its base at ORIGIN (the core on top in the middle), a Creative Source Jar under the central bushing.
 * The network leaves through the base bushings: BAY, CPU, PROV, TERM, BUS_IN, BUS_OUT are points next to the corner bushings.
 */
public class GolemGameTests {
    private static final String T = "empty";

    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos CORE = ORIGIN.offset(0, 3, 0);
    private static final BlockPos JAR = ORIGIN.below();
    private static final BlockPos BAY = new BlockPos(7, 1, 4);
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos TERM = new BlockPos(3, 1, 6);
    private static final BlockPos BUS_IN = new BlockPos(3, 1, 4);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    // ------------------------------------------------------------ helpers

    private static void put(GameTestHelper helper, BlockPos rel, Block block) {
        helper.setBlock(rel, block);
    }

    /** Builds the whole array; expanders/quantum = how many side cells to fill with expanders (up to 8). */
    private static void buildArray(GameTestHelper helper, int expanders, int quantum) {
        int slot = 0;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = ORIGIN.offset(dx, dy, dz);
                    boolean corner = dx != 0 && dz != 0;
                    boolean center = dx == 0 && dz == 0;
                    switch (dy) {
                        case 0 -> put(helper, p, corner || center ? ModBlocks.CABLE_BUSHING.get() : ModBlocks.ATLAS_CONDUIT.get());
                        case 1, 2 -> {
                            if (corner) {
                                BlockState atl = ModBlocks.ATLAS_ATLANT.get().defaultBlockState()
                                        .setValue(AtlantBlock.HALF, dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                                helper.setBlock(p, atl);
                            } else if (center) {
                                put(helper, p, ModBlocks.ATLAS_CONDUIT.get());
                            } else {
                                Block b = ModBlocks.ATLAS_CASING.get();
                                if (slot < expanders) {
                                    b = ModBlocks.CHANNEL_EXPANDER.get();
                                } else if (slot < expanders + quantum) {
                                    b = ModBlocks.QUANTUM_CHANNEL_EXPANDER.get();
                                }
                                slot++;
                                put(helper, p, b);
                            }
                        }
                        default -> put(helper, p, center ? ModBlocks.ATLAS_CORE.get() : ModBlocks.ATLAS_CASING.get());
                    }
                }
            }
        }
    }

    private static void powered(GameTestHelper helper) {
        put(helper, JAR, ModBlocks.CREATIVE_SOURCE_JAR.get());
    }

    /** A 2x2 cabinet: the main part at rel, facing south, the other parts to the right (east) and above. */
    private static void putBay(GameTestHelper helper, BlockPos rel) {
        CopperDriveBayBlock.place(helper.getLevel(), helper.absolutePos(rel), Direction.SOUTH);
    }

    /** A full network: array + jar + cabinet with a 1k disk. */
    private static BlockPos baseNetwork(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        putBay(helper, BAY);
        CopperDriveBayBlockEntity be = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        be.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        return BAY;
    }

    /**
     * A player for window and click checks: a NeoForge FakePlayer at the start of the test area. The GameTest "mock" player joins the player list, and mods
     * like Curios send it packets its connection does not accept; a FakePlayer silently swallows packets.
     */
    private static ServerPlayer testPlayer(GameTestHelper helper) {
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "golem-logix-test");
        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(), profile);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.snapTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0f, 0f);
        return player;
    }

    private static GolemNetwork net(GameTestHelper helper, BlockPos rel) {
        return GolemNetwork.scan(helper.getLevel(), helper.absolutePos(rel));
    }

    /**
     * An assembled array dresses its casings (the side columns, the crown's rail and corners), keeps a gap where an expander stands,
     * dresses itself again from plain casings (an array of a world from before the dress) without being rebuilt, and undresses
     * within a second when a block goes.
     */
    public static void arrayDressesItself(GameTestHelper helper) {
        buildArray(helper, 1, 0);
        var part = com.golemlogix.blocks.AtlasCasingBlock.PART;
        var facing = com.golemlogix.blocks.AtlasCasingBlock.FACING;
        java.util.function.BiFunction<BlockPos, com.golemlogix.blocks.AtlasCasingBlock.Part, Boolean> is = (p, want) ->
                helper.getBlockState(ORIGIN.offset(p)).getValue(part) == want;
        Runnable dressed = () -> {
            helper.assertTrue(is.apply(new BlockPos(0, 1, -1), com.golemlogix.blocks.AtlasCasingBlock.Part.COLUMN_LOW)
                    && is.apply(new BlockPos(0, 2, -1), com.golemlogix.blocks.AtlasCasingBlock.Part.COLUMN_HIGH)
                    && helper.getBlockState(ORIGIN.offset(0, 1, -1)).getValue(facing) == Direction.NORTH, "the north column stands");
            helper.assertTrue(is.apply(new BlockPos(1, 1, 0), com.golemlogix.blocks.AtlasCasingBlock.Part.COLUMN_LOW)
                    && helper.getBlockState(ORIGIN.offset(1, 1, 0)).getValue(facing) == Direction.EAST, "the east column faces east");
            helper.assertTrue(is.apply(new BlockPos(-1, 2, 0), com.golemlogix.blocks.AtlasCasingBlock.Part.NONE),
                    "no column over an expander");
            helper.assertTrue(is.apply(new BlockPos(0, 3, -1), com.golemlogix.blocks.AtlasCasingBlock.Part.TOP_EDGE)
                    && is.apply(new BlockPos(-1, 3, -1), com.golemlogix.blocks.AtlasCasingBlock.Part.TOP_CORNER)
                    && helper.getBlockState(ORIGIN.offset(-1, 3, -1)).getValue(facing) == Direction.NORTH
                    && helper.getBlockState(ORIGIN.offset(1, 3, 1)).getValue(facing) == Direction.SOUTH, "the crown has its rail and corners");
        };
        var core = helper.getBlockEntity(CORE, com.golemlogix.blockentity.AtlasCoreBlockEntity.class);
        core.refresh(helper.getLevel(), helper.absolutePos(CORE));
        dressed.run();
        // an array of an older world: plain casings around a formed core; the core's own second check dresses it
        for (BlockPos p : BlockPos.betweenClosed(ORIGIN.offset(-1, 1, -1), ORIGIN.offset(1, 3, 1))) {
            if (helper.getBlockState(p).getBlock() instanceof com.golemlogix.blocks.AtlasCasingBlock) {
                helper.setBlock(p.immutable(), ModBlocks.ATLAS_CASING.get().defaultBlockState());
            }
        }
        helper.runAfterDelay(25, () -> {
            dressed.run();
            helper.setBlock(ORIGIN.offset(1, 3, 0), Blocks.AIR);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(is.apply(new BlockPos(0, 3, -1), com.golemlogix.blocks.AtlasCasingBlock.Part.NONE)
                        && is.apply(new BlockPos(0, 1, -1), com.golemlogix.blocks.AtlasCasingBlock.Part.NONE), "a broken array undresses");
                helper.succeed();
            });
        });
    }

    // ----------------------------------------------------------- multiblock

    public static void arrayStructure(GameTestHelper helper) {
        buildArray(helper, 3, 2);
        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            AtlasStructure.Result r = AtlasStructure.check(level, helper.absolutePos(CORE));
            helper.assertTrue(r.valid(), "complete array must be valid, missing " + r.missingPos());
            helper.assertTrue(r.expanders() == 3 && r.quantumExpanders() == 2, "expanders counted: " + r.expanders() + "/" + r.quantumExpanders());

            // a casing was removed -> the array is not valid, the hint points at the hole
            BlockPos hole = ORIGIN.offset(1, 3, 0);
            helper.setBlock(hole, Blocks.AIR);
            AtlasStructure.Result broken = AtlasStructure.check(level, helper.absolutePos(CORE));
            helper.assertTrue(!broken.valid(), "hole in the crown");
            helper.assertTrue(helper.absolutePos(hole).equals(broken.missingPos()), "hint points at the hole");
            helper.assertTrue(broken.expected() == ModBlocks.ATLAS_CASING.get(), "hint names the casing");
            put(helper, hole, ModBlocks.ATLAS_CASING.get());

            // an Atlant with the right half at the right level
            BlockPos corner = ORIGIN.offset(1, 2, 1);
            helper.setBlock(corner, ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER));
            helper.assertTrue(!AtlasStructure.check(level, helper.absolutePos(CORE)).valid(), "wrong atlant half is rejected");
            // replacing one half breaks the pair, as with a door; place both again
            helper.setBlock(corner.below(), ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(corner, ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.UPPER));
            helper.assertTrue(AtlasStructure.check(level, helper.absolutePos(CORE)).valid(), "fixed again");

            // an expander outside the side cells (in a corner) does not count
            helper.setBlock(ORIGIN.offset(1, 1, 1), ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            helper.assertTrue(!AtlasStructure.check(level, helper.absolutePos(CORE)).valid(), "expander cannot replace an atlant");
            helper.succeed();
        });
    }

    public static void arrayCapacity(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        helper.runAfterDelay(3, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.structure().valid(), "valid structure");
            helper.assertTrue(n.lineCapacity() == 64 && n.channelCapacity() == 320, "base array: 5 lines x 64 = 320, got " + n.channelCapacity());
            helper.assertTrue(n.isOnline(), "online");

            buildArray(helper, 2, 1);
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.lineCapacity() == 64 + 2 * 16 + 32, "line 128, got " + n2.lineCapacity());
            helper.assertTrue(n2.channelCapacity() == 5 * 128, "total 640, got " + n2.channelCapacity());

            buildArray(helper, 8, 0);
            helper.assertTrue(net(helper, ORIGIN).channelCapacity() == 5 * 192, "8 basic expanders: 960");
            buildArray(helper, 0, 8);
            helper.assertTrue(net(helper, ORIGIN).channelCapacity() == 5 * 320, "8 quantum expanders: 1600");
            helper.succeed();
        });
    }

    /** The core alone on a Cable Bushing (no conduits, so no starter tower): 8 channels, all through the bushing under it. */
    private static void loneCoreOnBushing(GameTestHelper helper) {
        put(helper, ORIGIN.above(), ModBlocks.ATLAS_CORE.get());
        put(helper, ORIGIN, ModBlocks.CABLE_BUSHING.get());
    }

    public static void bareTowerStart(GameTestHelper helper) {
        // the starter tower: core, two conduits, central bushing = 16 channels; 17 terminals in two rows along the bushing
        put(helper, CORE, ModBlocks.ATLAS_CORE.get());
        put(helper, ORIGIN.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, ORIGIN, ModBlocks.CABLE_BUSHING.get());
        powered(helper);
        for (int i = 1; i <= 9; i++) {
            put(helper, ORIGIN.east(i), ModBlocks.ANALOG_TERMINAL.get());
            if (i <= 8) {
                put(helper, ORIGIN.east(i).south(), ModBlocks.ANALOG_TERMINAL.get());
            }
        }
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(!n.structure().valid() && n.isTower(), "a starter tower, no array yet");
            helper.assertTrue(n.channelCapacity() == GolemNetwork.TOWER_CHANNELS && n.lineCapacity() == GolemNetwork.TOWER_CHANNELS,
                    "tower capacity " + n.channelCapacity() + " / line " + n.lineCapacity());
            helper.assertTrue(n.channelsUsed() == 16, "16 of 17 terminals work, got " + n.channelsUsed());
            helper.assertTrue(n.isOnline(), "powered");
            helper.succeed();
        });
    }

    /**
     * The Atlas Core alone: 8 channels, and it connects only through its bottom face (a pipe under it docks, one beside it does not).
     * A pipe docks onto a Cryo-Source Jar too (the network draws Source from it).
     */
    public static void loneCoreLinksDown(GameTestHelper helper) {
        BlockPos core = new BlockPos(5, 3, 5), below = core.below(), side = core.west(), term = new BlockPos(5, 1, 5), lost = core.east();
        // the pipes first: placed by code they take their links from the neighbours placed after them (a player's pipe looks at once)
        put(helper, below, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, side, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, core, ModBlocks.ATLAS_CORE.get());
        put(helper, term, ModBlocks.ANALOG_TERMINAL.get());
        put(helper, lost, ModBlocks.ANALOG_TERMINAL.get());
        powered(helper);
        BlockPos jarPipe = new BlockPos(2, 1, 2);
        put(helper, jarPipe, ModBlocks.BASIC_SNOW_PIPE.get());
        com.golemlogix.blocks.CryoJarBlock.place(helper.getLevel(), helper.absolutePos(jarPipe.east()), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, term);
            helper.assertTrue(n.hasCore() && !n.isTower() && n.channelCapacity() == GolemNetwork.BARE_CORE_CHANNELS,
                    "the core alone gives " + GolemNetwork.BARE_CORE_CHANNELS + ", got " + n.channelCapacity());
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(term)), "the terminal under the core works");
            helper.assertTrue(!n.contains(helper.absolutePos(lost)), "a terminal beside the core is not connected");
            var links = com.golemlogix.blocks.SnowPipeBlock.LINKS;
            var dev = com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE;
            helper.assertTrue(helper.getBlockState(below).getValue(links.get(Direction.UP)) == dev, "the pipe under the core docks onto it");
            helper.assertTrue(helper.getBlockState(side).getValue(links.get(Direction.EAST)) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE,
                    "a pipe beside the core does not");
            helper.assertTrue(helper.getBlockState(jarPipe).getValue(links.get(Direction.EAST)) == dev, "a pipe docks onto a Cryo-Source Jar");
            helper.succeed();
        });
    }

    public static void storageNeedsChannel(GameTestHelper helper) {
        // the core alone on a bushing (8 channels): 8 terminals took them all, the cabinet came ninth and got no channel, so it does not count as storage
        loneCoreOnBushing(helper);
        powered(helper);
        for (int i = 1; i <= 8; i++) {
            put(helper, ORIGIN.east(i), ModBlocks.ANALOG_TERMINAL.get());
        }
        BlockPos bay = ORIGIN.east(9);
        putBay(helper, bay);
        (helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.isOnline(), "powered");
            helper.assertTrue(!n.isDeviceActive(helper.absolutePos(bay)), "the cabinet got no channel");
            helper.assertTrue(n.itemCapacity() == 0, "a cabinet without a channel adds no capacity, got " + n.itemCapacity());
            helper.assertTrue(n.insertItem(new ItemStack(Items.DIAMOND, 10), false).getCount() == 10, "nothing is stored without a channel");
            // one terminal removed -> a channel is free, the cabinet works
            helper.setBlock(ORIGIN.east(8), ModBlocks.BASIC_SNOW_PIPE.get());
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.isDeviceActive(helper.absolutePos(bay)), "the cabinet works once a channel is free");
            helper.assertTrue(n2.insertItem(new ItemStack(Items.DIAMOND, 10), false).isEmpty(), "and stores items");
            helper.succeed();
        });
    }

    public static void pipesAndBusesAreSteps(GameTestHelper helper) {
        // pipes and buses are steps: collisions no higher than half a block (they can be walked onto without jumping), the selection box is unchanged
        BlockPos dense = new BlockPos(1, 1, 2), basic = dense.east(), bus = basic.east(), lone = new BlockPos(1, 1, 5);
        put(helper, dense, ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, basic, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(bus, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(lone, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        helper.runAfterDelay(3, () -> {
            for (BlockPos rel : new BlockPos[]{dense, basic, bus, lone}) {
                BlockPos abs = helper.absolutePos(rel);
                BlockState st = helper.getLevel().getBlockState(abs);
                var collision = st.getCollisionShape(helper.getLevel(), abs);
                var outline = st.getShape(helper.getLevel(), abs);
                helper.assertTrue(!collision.isEmpty(), st.getBlock() + " has a step to stand on");
                helper.assertTrue(collision.max(Direction.Axis.Y) <= 0.5 + 1e-6, st.getBlock() + " collision top " + collision.max(Direction.Axis.Y));
                helper.assertTrue(outline.max(Direction.Axis.Y) > 0.5, st.getBlock() + " outline keeps its full height");
            }
            // a bus with no neighbours behind it is in the front half of the block (only a short socket at the back)
            var shape = helper.getLevel().getBlockState(helper.absolutePos(lone)).getShape(helper.getLevel(), helper.absolutePos(lone));
            helper.assertTrue(shape.min(Direction.Axis.X) >= 0.42, "lone bus fits in the front half, starts at x=" + shape.min(Direction.Axis.X));
            helper.succeed();
        });
    }

    public static void onlyDevicesWithChannelLookActive(GameTestHelper helper) {
        // the core alone (8 channels) with 10 terminals: the ninth and tenth must not "light up" (before, every node got the flag)
        loneCoreOnBushing(helper);
        powered(helper);
        for (int i = 1; i <= 10; i++) {
            put(helper, ORIGIN.east(i), ModBlocks.ANALOG_TERMINAL.get());
        }
        helper.runAfterDelay(5, () -> {
            var core = helper.getBlockEntity(ORIGIN.above(), com.golemlogix.blockentity.AtlasCoreBlockEntity.class);
            core.refresh(helper.getLevel(), helper.absolutePos(ORIGIN.above()));
            int lit = 0;
            for (int i = 1; i <= 10; i++) {
                if (helper.getLevel().getBlockState(helper.absolutePos(ORIGIN.east(i))).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE)) {
                    lit++;
                }
            }
            helper.assertTrue(lit == 8, "only the 8 terminals with a channel look active, lit " + lit);
            helper.succeed();
        });
    }

    public static void pipePlugSeparatesLines(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // line A: corner bushing (4,1,4) -> pipe (3,1,4) -> terminal; line B touches A with a pipe (3,1,3) and leads to its own terminal
        BlockPos a = new BlockPos(3, 1, 4), b = new BlockPos(3, 1, 3);
        put(helper, a, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(2, 1, 4), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, b, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(3, 1, 2), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos t2 = new BlockPos(3, 1, 1);
        put(helper, t2, ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(net(helper, ORIGIN).isDeviceActive(helper.absolutePos(t2)), "line B joins line A without a plug");
            com.golemlogix.items.PipePlugItem.toggle(helper.getLevel(), helper.absolutePos(a), Direction.NORTH, true);
            helper.assertTrue(!net(helper, ORIGIN).contains(helper.absolutePos(t2)), "the plug splits the lines");
            var sa = helper.getLevel().getBlockState(helper.absolutePos(a));
            var sb = helper.getLevel().getBlockState(helper.absolutePos(b));
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(sa, Direction.NORTH) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE
                    && com.golemlogix.blocks.SnowPipeBlock.link(sb, Direction.SOUTH) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE, "both pipes show a closed end");
            com.golemlogix.items.PipePlugItem.toggle(helper.getLevel(), helper.absolutePos(a), Direction.NORTH, false);
            helper.assertTrue(net(helper, ORIGIN).isDeviceActive(helper.absolutePos(t2)), "removing the plug joins them again");
            helper.succeed();
        });
    }

    public static void expanderFaceIsAnExit(GameTestHelper helper) {
        // an expander in the wall (4,2,5): a dense pipe and three terminals at its outer face
        buildArray(helper, 1, 0);
        powered(helper);
        BlockPos expander = ORIGIN.offset(-1, 1, 0);
        put(helper, expander.west(), ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, expander.west(2), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, expander.west().north(), ModBlocks.ANALOG_TERMINAL.get());
        put(helper, expander.west().south(), ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.exitCapacity(helper.absolutePos(expander)) == 64, "expander exit is 64, got " + n.exitCapacity(helper.absolutePos(expander)));
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(expander.west(2))), "a terminal behind the expander works");
            helper.assertTrue(n.loadAt(helper.absolutePos(expander)) >= 3, "three channels go out of the expander, got " + n.loadAt(helper.absolutePos(expander)));
            helper.succeed();
        });
    }

    public static void conduitsStayInside(GameTestHelper helper) {
        // a conduit does not connect to outside blocks, a pipe next to it is not connected
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos outside = ORIGIN.offset(2, 0, 0); // next to a side conduit of the base
        put(helper, outside, ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.channelsUsed() == 0, "terminal touching a conduit is not connected, used " + n.channelsUsed());
            helper.assertTrue(!n.isDeviceActive(helper.absolutePos(outside)), "not part of the network");
            // the same terminal next to a bushing gets connected
            helper.setBlock(outside, Blocks.AIR);
            put(helper, ORIGIN.offset(2, 0, 1), ModBlocks.ANALOG_TERMINAL.get());
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.channelsUsed() == 1, "terminal next to a bushing works, used " + n2.channelsUsed());
            helper.succeed();
        });
    }

    public static void atlantPlacesAsDoubleBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        var level = helper.getLevel();
        BlockState lower = ModBlocks.ATLAS_ATLANT.get().defaultBlockState();
        helper.setBlock(pos, lower);
        helper.setBlock(pos.above(), lower.setValue(AtlantBlock.HALF, DoubleBlockHalf.UPPER));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getBlockState(pos.above()).is(ModBlocks.ATLAS_ATLANT.get()), "upper exists");
            // the upper half was removed -> the lower one disappears by itself
            helper.setBlock(pos.above(), Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(helper.getBlockState(pos).isAir(), "the lower half goes with the upper one");
                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------- storage and network

    public static void storageRoundTrip(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.hasCore(), "core not found");
            helper.assertTrue(n.isOnline(), "network must be powered by the creative jar");
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(bay)), "bay has a channel");

            ItemStack rest = n.insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.assertTrue(rest.isEmpty(), "10 diamonds must fit");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "expected 10 diamonds");

            ItemStack got = n.extractItem(new ItemStack(Items.DIAMOND), 4, false);
            helper.assertTrue(got.getCount() == 4, "extracted 4");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 6, "6 diamonds left");

            CopperDriveBayBlockEntity be = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(0)) == 6, "disk STORED must be 6");

            ItemStack overflow = n.insertItem(new ItemStack(Items.STONE, 64), false);
            for (int i = 0; i < 20; i++) {
                overflow = n.insertItem(overflow.isEmpty() ? new ItemStack(Items.STONE, 64) : overflow, false);
            }
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(0)) == 1000, "disk must be completely full");

            ItemStack sim = n.insertItem(new ItemStack(Items.DIAMOND, 5), true);
            helper.assertTrue(sim.getCount() == 5, "full network accepts nothing");
            helper.succeed();
        });
    }

    /**
     * A comb of terminals along a dense pipe: the spine runs from (x0, 1, z) by dx/dz for len blocks; every pipe but the first gets
     * a column of terminals above it and a full-height column on each side, so no terminal is more than a few devices from the pipe.
     * Returns the number of terminals.
     */
    private static int comb(GameTestHelper helper, int x0, int z0, int dx, int dz, int len) {
        int count = 0;
        for (int i = 0; i < len; i++) {
            BlockPos p = new BlockPos(x0 + dx * i, 1, z0 + dz * i);
            put(helper, p, ModBlocks.DENSE_FROST_PIPE.get());
            for (int y = 2; y <= 5; y++) {
                put(helper, p.atY(y), ModBlocks.ANALOG_TERMINAL.get());
                count++;
            }
            if (i == 0) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                for (int y = 1; y <= 5; y++) {
                    put(helper, p.offset(dz * side, y - 1, dx * side), ModBlocks.ANALOG_TERMINAL.get());
                    count++;
                }
            }
        }
        return count;
    }

    public static void lineLimit(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // two dense combs from one corner bushing (6,1,6): east and south, 60 terminals each
        int east = comb(helper, 7, 6, 1, 0, 5);
        int south = comb(helper, 6, 7, 0, 1, 5);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(east == 60 && south == 60, "combs of 60, got " + east + " and " + south);
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.lineCapacity() == 64, "line 64");
            helper.assertTrue(n.channelsUsed() == 64, "one bushing line passes only 64 channels, got " + n.channelsUsed());
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(6, 1, 6))) == 64, "the bushing carries the whole line");

            // two expanders raise the line to 96: 96 of the 120 devices pass through the same bushing
            buildArray(helper, 2, 0);
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.lineCapacity() == 96, "line 96, got " + n2.lineCapacity());
            helper.assertTrue(n2.channelsUsed() == 96, "96 terminals work now, got " + n2.channelsUsed());
            helper.succeed();
        });
    }

    public static void pipeLimit(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // corner bushing (6,1,6) -> a basic pipe (7,1,6) -> a dense comb of 46 terminals
        put(helper, new BlockPos(7, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        int count = comb(helper, 8, 6, 1, 0, 4);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.deviceCount() == count, "all " + count + " terminals are in the network, got " + n.deviceCount());
            helper.assertTrue(n.channelsUsed() == 16, "basic pipe passes only 16 channels, got " + n.channelsUsed());
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(7, 1, 6))) == 16, "the basic pipe is full");
            helper.setBlock(new BlockPos(7, 1, 6), ModBlocks.DENSE_FROST_PIPE.get().defaultBlockState());
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.channelsUsed() == count, "dense pipe passes all " + count + ", got " + n2.channelsUsed());
            helper.succeed();
        });
    }

    /** As in AE2, a device carries as many channels as a basic pipe (its own included): a slab of 20 terminals with one entry gets 16. */
    public static void devicesCarryLikeBasicPipes(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // 4 x 5 terminals east of the corner bushing (6,1,6); only (7,1,6) touches the bushing
        for (int x = 7; x <= 10; x++) {
            for (int y = 1; y <= 5; y++) {
                put(helper, new BlockPos(x, y, 6), ModBlocks.ANALOG_TERMINAL.get());
            }
        }
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            BlockPos entry = helper.absolutePos(new BlockPos(7, 1, 6));
            helper.assertTrue(n.channelsUsed() == 16, "16 of 20 terminals get a channel, got " + n.channelsUsed());
            helper.assertTrue(n.loadAt(entry) == 16, "the entry terminal carries 16, got " + n.loadAt(entry));
            GolemNetwork.NodeChannels far = n.channelsAt(helper.absolutePos(new BlockPos(10, 5, 6)));
            helper.assertTrue(!far.hasChannel() && far.missing() == com.golemlogix.system.ChannelCalculation.Missing.BOTTLENECK
                    && entry.equals(far.bottleneck()), "the farthest terminal is stopped by the entry terminal: " + far);
            helper.succeed();
        });
    }

    /** Channels never cross the array from one exit to another: the center bushing and the expanders carry only their own devices. */
    public static void noTransitThroughTheArray(GameTestHelper helper) {
        buildArray(helper, 2, 0);
        powered(helper);
        put(helper, TERM, ModBlocks.ANALOG_TERMINAL.get());                  // behind the corner bushing (4,1,6)
        put(helper, new BlockPos(3, 2, 6), ModBlocks.CRAFTING_TERMINAL.get());
        put(helper, new BlockPos(7, 1, 4), ModBlocks.ANALOG_TERMINAL.get());  // behind the corner bushing (6,1,4)
        put(helper, new BlockPos(7, 1, 6), ModBlocks.ANALOG_TERMINAL.get());  // behind the corner bushing (6,1,6)
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.channelsUsed() == 4, "four devices, got " + n.channelsUsed());
            helper.assertTrue(n.loadAt(helper.absolutePos(ORIGIN)) == 0, "the center bushing has no devices, got " + n.loadAt(helper.absolutePos(ORIGIN)));
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(4, 2, 5))) == 0 && n.loadAt(helper.absolutePos(new BlockPos(5, 2, 4))) == 0,
                    "expanders without pipes carry nothing");
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(4, 1, 6))) == 2, "corner line with two terminals");
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(6, 1, 4))) == 1 && n.loadAt(helper.absolutePos(new BlockPos(6, 1, 6))) == 1,
                    "the other corner lines carry one each");
            helper.succeed();
        });
    }

    /** As in AE2, dense runs are served first: with the core's 8 channels the far devices on a dense pipe win over near ones on a basic pipe. */
    public static void denseRunsComeFirst(GameTestHelper helper) {
        loneCoreOnBushing(helper);
        powered(helper);
        // west: a basic pipe with 4 terminals right next to the bushing
        put(helper, new BlockPos(4, 1, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos[] near = {new BlockPos(4, 2, 5), new BlockPos(4, 1, 4), new BlockPos(4, 1, 6), new BlockPos(3, 1, 5)};
        // east: four dense pipes with 8 terminals at their far end
        for (int x = 6; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 5), ModBlocks.DENSE_FROST_PIPE.get());
        }
        BlockPos[] far = {new BlockPos(7, 2, 5), new BlockPos(8, 2, 5), new BlockPos(8, 1, 4), new BlockPos(8, 1, 6),
                new BlockPos(9, 2, 5), new BlockPos(9, 1, 4), new BlockPos(9, 1, 6), new BlockPos(10, 1, 5)};
        for (BlockPos p : near) {
            put(helper, p, ModBlocks.ANALOG_TERMINAL.get());
        }
        for (BlockPos p : far) {
            put(helper, p, ModBlocks.ANALOG_TERMINAL.get());
        }
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, near[0]);
            helper.assertTrue(n.channelsUsed() == 8, "the core alone has 8 channels, got " + n.channelsUsed());
            for (BlockPos p : far) {
                helper.assertTrue(n.isDeviceActive(helper.absolutePos(p)), "far terminal on the dense run has a channel: " + p);
            }
            for (BlockPos p : near) {
                GolemNetwork.NodeChannels c = n.channelsAt(helper.absolutePos(p));
                helper.assertTrue(!c.hasChannel() && c.missing() == com.golemlogix.system.ChannelCalculation.Missing.NETWORK_FULL,
                        "near terminal on the basic pipe waits: " + p + " " + c);
            }
            helper.succeed();
        });
    }

    /** Two cores in one network: the same one runs it from every device, the other one is idle and says so. */
    public static void oneCoreRunsTheNetwork(GameTestHelper helper) {
        // tower A at (5,1,5)..(5,4,5), tower B at (9,1,9)..(9,4,9), their bushings joined by a basic pipe
        for (BlockPos base : new BlockPos[]{ORIGIN, new BlockPos(9, 1, 9)}) {
            put(helper, base, ModBlocks.CABLE_BUSHING.get());
            put(helper, base.above(), ModBlocks.ATLAS_CONDUIT.get());
            put(helper, base.above(2), ModBlocks.ATLAS_CONDUIT.get());
            put(helper, base.above(3), ModBlocks.ATLAS_CORE.get());
        }
        powered(helper);
        for (int x = 6; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        for (int z = 6; z <= 8; z++) {
            put(helper, new BlockPos(9, 1, z), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        BlockPos[] terminals = {new BlockPos(4, 1, 5), new BlockPos(7, 2, 5), new BlockPos(10, 1, 9), new BlockPos(9, 1, 10), new BlockPos(8, 1, 9)};
        for (BlockPos p : terminals) {
            put(helper, p, ModBlocks.ANALOG_TERMINAL.get());
        }
        helper.runAfterDelay(25, () -> {
            GolemNetwork fromA = net(helper, terminals[0]);
            GolemNetwork fromB = net(helper, terminals[2]);
            helper.assertTrue(fromA.corePos().equals(fromB.corePos()), "every device sees the same core: " + fromA.corePos() + " / " + fromB.corePos());
            helper.assertTrue(fromA.corePos().equals(helper.absolutePos(CORE)), "the lower position wins between two towers");
            for (BlockPos p : terminals) {
                BlockPos abs = helper.absolutePos(p);
                helper.assertTrue(fromA.isDeviceActive(abs) == fromB.isDeviceActive(abs), "the same channels from both ends: " + p);
            }
            helper.assertTrue(fromA.channelsUsed() == 5, "five terminals on a tower's 16 channels, got " + fromA.channelsUsed());
            var coreB = helper.getBlockEntity(new BlockPos(9, 4, 9), com.golemlogix.blockentity.AtlasCoreBlockEntity.class);
            var coreA = helper.getBlockEntity(CORE, com.golemlogix.blockentity.AtlasCoreBlockEntity.class);
            coreB.refresh(helper.getLevel(), helper.absolutePos(new BlockPos(9, 4, 9)));
            coreA.refresh(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(coreB.isDuplicate() && !coreA.isDuplicate(), "B is idle, A runs the network");
            helper.succeed();
        });
    }

    /**
     * The network is remembered long now, so what stands next to it must make it stale by itself: a chest put in front of a periscope
     * is network storage in the same tick, and a terminal joins at once.
     */
    public static void peripheralsFollowChanges(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // a periscope on the corner bushing (4,1,6) looking west at (2,1,6)
        helper.setBlock(TERM, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        helper.runAfterDelay(25, () -> {
            var level = helper.getLevel();
            BlockPos abs = helper.absolutePos(TERM);
            ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
            helper.assertTrue(GolemNetwork.get(level, abs).itemCountOf(cobble) == 0, "nothing behind the periscope yet");
            helper.setBlock(TERM.west(), Blocks.CHEST);
            (((Container) helper.getBlockEntity(TERM.west(), net.minecraft.world.level.block.entity.BlockEntity.class))).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
            helper.assertTrue(GolemNetwork.get(level, abs).itemCountOf(cobble) == 12, "the new chest is storage in the same tick");
            helper.setBlock(TERM.west(), Blocks.AIR);
            helper.assertTrue(GolemNetwork.get(level, abs).itemCountOf(cobble) == 0, "a removed chest is gone at once");
            helper.succeed();
        });
    }

    /** A Cryo-Source Telegraph links an outpost: Write records the base telegraph, Memory links to it, Reset breaks every card of it. */
    public static void telegraphLinksAnOutpost(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        var level = helper.getLevel();
        // the base telegraph touches the corner bushing (6,1,6); the outpost one stands apart with a terminal
        BlockPos aRel = new BlockPos(7, 1, 6), bRel = new BlockPos(2, 1, 10), term = new BlockPos(1, 1, 10);
        com.golemlogix.blocks.TelegraphBlock.place(level, helper.absolutePos(aRel), Direction.SOUTH);
        com.golemlogix.blocks.TelegraphBlock.place(level, helper.absolutePos(bRel), Direction.SOUTH);
        put(helper, term, ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(25, () -> {
            var a = helper.getBlockEntity(aRel, com.golemlogix.blockentity.TelegraphBlockEntity.class);
            var b = helper.getBlockEntity(bRel, com.golemlogix.blockentity.TelegraphBlockEntity.class);
            int write = com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_WRITE, memory = com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_MEMORY;
            helper.assertTrue(!net(helper, term).hasCore(), "the outpost is alone before the link");
            a.slots().setItem(write, new ItemStack(ModItems.AETHERIAL_LINKAGE.get()));
            ItemStack card = a.slots().removeItem(write, 1);
            var link = com.golemlogix.items.TelegraphLinkCardItem.link(card);
            helper.assertTrue(link != null && link.target().pos().equals(helper.absolutePos(aRel)), "the Write slot records the base telegraph");
            b.slots().setItem(memory, card);
            helper.assertTrue(com.golemlogix.system.TelegraphLinks.status(b) == com.golemlogix.system.TelegraphLinks.Status.LINKED, "linked");
            GolemNetwork n = net(helper, term);
            helper.assertTrue(n.hasCore() && n.isDeviceActive(helper.absolutePos(term)), "the outpost terminal works through the link");
            helper.assertTrue(n.telegraphLinks() == 1, "one link, got " + n.telegraphLinks());
            helper.assertTrue(!b.mayPlace(write, new ItemStack(ModItems.AETHERIAL_LINKAGE.get())), "Write is locked while Memory holds a card");
            helper.assertTrue(com.golemlogix.system.TelegraphLinks.linkedReceivers(a) == 1, "the base telegraph sees its receiver");
            a.reset();
            helper.assertTrue(com.golemlogix.system.TelegraphLinks.status(b) == com.golemlogix.system.TelegraphLinks.Status.OUTDATED,
                    "a reset outdates the card");
            helper.assertTrue(!net(helper, term).hasCore(), "and breaks the link");
            helper.succeed();
        });
    }

    /**
     * The Write slot takes a whole stack and writes every card; blank cards clicked onto written ones join them; shift-click sends
     * blank cards to Write and a card written elsewhere to Memory, never rewriting it.
     */
    public static void telegraphWritesAStack(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 1, 2);
        com.golemlogix.blocks.TelegraphBlock.place(level, helper.absolutePos(rel), Direction.SOUTH);
        helper.runAfterDelay(5, () -> {
            var be = helper.getBlockEntity(rel, com.golemlogix.blockentity.TelegraphBlockEntity.class);
            int write = com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_WRITE, memory = com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_MEMORY;
            var mine = new com.golemlogix.items.TelegraphLinkCardItem.Link(be.globalPos(), be.seal());
            var elsewhere = new com.golemlogix.items.TelegraphLinkCardItem.Link(
                    net.minecraft.core.GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(9, 1, 9))), 42);
            ServerPlayer player = testPlayer(helper);
            ItemStack foreign = new ItemStack(ModItems.AETHERIAL_LINKAGE.get());
            foreign.set(com.golemlogix.init.ModDataComponents.TELEGRAPH_LINK.get(), elsewhere);
            player.getInventory().setItem(0, new ItemStack(ModItems.AETHERIAL_LINKAGE.get(), 5));
            player.getInventory().setItem(1, foreign);
            var menu = new com.golemlogix.menu.TelegraphMenu(31, player.getInventory(), be, be.slots(),
                    new net.minecraft.world.inventory.SimpleContainerData(3), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
            int hotbar0 = 3 + 27, hotbar1 = hotbar0 + 1;
            menu.clicked(hotbar0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            ItemStack cards = be.slots().getItem(write);
            helper.assertTrue(cards.getCount() == 5 && mine.equals(com.golemlogix.items.TelegraphLinkCardItem.link(cards)),
                    "shift-click puts all 5 blank cards into Write and they are written, got " + cards.getCount());
            menu.setCarried(new ItemStack(ModItems.AETHERIAL_LINKAGE.get(), 3));
            menu.clicked(write, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            cards = be.slots().getItem(write);
            helper.assertTrue(cards.getCount() == 8 && menu.getCarried().isEmpty() && mine.equals(com.golemlogix.items.TelegraphLinkCardItem.link(cards)),
                    "blank cards clicked onto written ones join them, got " + cards.getCount());
            menu.clicked(hotbar1, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(elsewhere.equals(com.golemlogix.items.TelegraphLinkCardItem.link(player.getInventory().getItem(1))),
                    "a card written elsewhere is never rewritten by shift-click");
            be.slots().setItem(write, ItemStack.EMPTY);
            menu.clicked(hotbar1, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
            ItemStack mem = be.slots().getItem(memory);
            helper.assertTrue(mem.getCount() == 1 && elsewhere.equals(com.golemlogix.items.TelegraphLinkCardItem.link(mem)),
                    "a written card goes to Memory as it is");
            helper.succeed();
        });
    }

    /** Without a range card a telegraph stays in its dimension; the Void-Glass Prism on either end links the Nether to the base. */
    public static void telegraphReachesAnotherDimension(GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.server.level.ServerLevel nether = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if (nether == null) {
            helper.succeed();
            return;
        }
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos aRel = new BlockPos(7, 1, 6);
        com.golemlogix.blocks.TelegraphBlock.place(level, helper.absolutePos(aRel), Direction.SOUTH);
        BlockPos o = helper.absolutePos(ORIGIN);
        BlockPos b = new BlockPos(o.getX(), 100, o.getZ());
        nether.setChunkForced(b.getX() >> 4, b.getZ() >> 4, true);
        for (BlockPos p : BlockPos.betweenClosed(b.offset(-2, -1, -2), b.offset(3, 4, 3))) {
            nether.setBlock(p, p.getY() == b.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        com.golemlogix.blocks.TelegraphBlock.place(nether, b, Direction.SOUTH);
        BlockPos termPos = b.west();
        nether.setBlock(termPos, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState(), 3);
        helper.runAfterDelay(25, () -> {
            try {
                var a = helper.getBlockEntity(aRel, com.golemlogix.blockentity.TelegraphBlockEntity.class);
                var bt = (com.golemlogix.blockentity.TelegraphBlockEntity) nether.getBlockEntity(b);
                helper.assertTrue(bt != null, "the outpost telegraph stands in the Nether");
                a.slots().setItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_WRITE, new ItemStack(ModItems.AETHERIAL_LINKAGE.get()));
                ItemStack card = a.slots().removeItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_WRITE, 1);
                bt.slots().setItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_MEMORY, card);
                helper.assertTrue(com.golemlogix.system.TelegraphLinks.status(bt) == com.golemlogix.system.TelegraphLinks.Status.OUT_OF_RANGE,
                        "no range card: another dimension is out of range, got " + com.golemlogix.system.TelegraphLinks.status(bt));
                a.slots().setItem(com.golemlogix.blockentity.TelegraphBlockEntity.SLOT_RANGE, new ItemStack(ModItems.VOID_GLASS_PRISM.get()));
                helper.assertTrue(com.golemlogix.system.TelegraphLinks.status(bt) == com.golemlogix.system.TelegraphLinks.Status.LINKED,
                        "the prism at the base reaches the Nether");
                GolemNetwork n = GolemNetwork.scan(nether, termPos);
                helper.assertTrue(n.hasCore() && n.coreLevel() == level, "the Nether outpost runs on the base core");
                helper.assertTrue(n.isDeviceActive(termPos), "the Nether terminal has a channel");
                helper.succeed();
            } finally {
                for (BlockPos p : BlockPos.betweenClosed(b.offset(-2, -1, -2), b.offset(3, 4, 3))) {
                    nether.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                nether.setChunkForced(b.getX() >> 4, b.getZ() >> 4, false);
            }
        });
    }

    /**
     * The remembered network follows the world at once (as an AE2 grid repaths when a connection changes): a new device is counted in the
     * same tick, and devices cut off from the core go dark at the end of the tick instead of glowing forever.
     */
    public static void networkFollowsChanges(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        put(helper, TERM, ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(25, () -> {
            var level = helper.getLevel();
            helper.assertTrue(GolemNetwork.get(level, helper.absolutePos(TERM)).channelsUsed() == 1, "one terminal");
            helper.assertTrue(helper.getBlockState(TERM).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE), "the terminal is lit");
            put(helper, TERM.west(), ModBlocks.ANALOG_TERMINAL.get());
            helper.assertTrue(GolemNetwork.get(level, helper.absolutePos(TERM)).channelsUsed() == 2, "the new terminal counts in the same tick");
            // the corner bushing goes: both terminals are cut off from the core
            helper.setBlock(new BlockPos(4, 1, 6), Blocks.AIR);
            helper.assertTrue(!GolemNetwork.get(level, helper.absolutePos(TERM)).hasCore(), "no core behind a missing bushing");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(!helper.getBlockState(TERM).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE), "a cut-off terminal goes dark");
                helper.succeed();
            });
        });
    }

    public static void busesMoveItems(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        // import: a bus on the bushing faces west into a chest (2,1,4)
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = ((Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 20));

        // export: a bus faces north into another chest (6,1,2)
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = helper.getBlockEntity(BUS_OUT, BusBlockEntity.class);
        bus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        BlockPos chest2 = new BlockPos(6, 1, 2);
        helper.setBlock(chest2, Blocks.CHEST);
        Container c2 = ((Container) helper.getBlockEntity(chest2, net.minecraft.world.level.block.entity.BlockEntity.class));

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(c.isEmpty(), "import bus must empty the first chest");
            int moved = 0;
            for (int i = 0; i < c2.getContainerSize(); i++) {
                moved += c2.getItem(i).getCount();
            }
            helper.assertTrue(moved == 20, "export bus must deliver all 20 cobblestone, got " + moved);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 0, "nothing left in network");
            helper.succeed();
        });
    }

    /** A copy of a disk (same id, e.g. a creative middle-click) in the network shares the original's contents: it is counted once. */
    public static void copiedDiskCountsOnce(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 10), false);
            var container = (helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class)).getContainer();
            container.setItem(1, container.getItem(0).copy());
            GolemNetwork again = net(helper, bay);
            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
            helper.assertTrue(again.itemCountOf(diamond) == 10, "the copy adds nothing, got " + again.itemCountOf(diamond));
            ItemStack got = again.extractItem(new ItemStack(Items.DIAMOND), 15, false);
            helper.assertTrue(got.getCount() == 10 && again.itemCountOf(diamond) == 0, "only the real 10 come out, got " + got.getCount());
            helper.succeed();
        });
    }

    /** An import bus facing an empty chest falls asleep (no work every interval); the chest filling up wakes it at once. */
    public static void busSleepsAndWakes(GameTestHelper helper) {
        baseNetwork(helper);
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        helper.runAfterDelay(60, () -> {
            BusBlockEntity bus = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);
            helper.assertTrue(bus.idle(), "an import bus facing an empty chest sleeps");
            Container c = ((Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class));
            c.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
            helper.assertTrue(!bus.idle(), "a change in the chest next to it wakes it");
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(c.isEmpty(), "and it imports the items at its next turn");
                helper.succeed();
            });
        });
    }

    /**
     * The core sends a few samples of the network's contents to clients (the snowmen's crates); nothing reads the disks from
     * another thread any more (the single player client used to read the server's storage while the server wrote it).
     */
    public static void coreSendsCargoSamples(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            var level = helper.getLevel();
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 3), false);
            var core = helper.getBlockEntity(CORE, com.golemlogix.blockentity.AtlasCoreBlockEntity.class);
            core.refresh(level, helper.absolutePos(CORE));
            helper.assertTrue(core.cargoItems().stream().anyMatch(s -> s.is(Items.DIAMOND)), "the samples show the diamonds");
            var tag = core.getUpdateTag(level.registryAccess());
            helper.assertTrue(tag.getListOrEmpty("CargoItems").size() == core.cargoItems().size(),
                    "the samples go with the core's update");
            ItemStack disk = (helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class)).getContainer().getItem(0);
            helper.assertTrue(!com.golemlogix.system.DiskStorage.items(disk).isEmpty(), "the server thread reads the disk");
            boolean[] emptyElsewhere = {false};
            Thread other = new Thread(() -> emptyElsewhere[0] = com.golemlogix.system.DiskStorage.items(disk).isEmpty());
            other.start();
            try {
                other.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            helper.assertTrue(emptyElsewhere[0], "another thread gets nothing from the disks");
            helper.succeed();
        });
    }

    public static void interfaceKeepsStockAndAccepts(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        be.getConfig().setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.IRON_INGOT, 32), false));
        // automation (like a hopper or another mod's pipe) sees the interface from any side
        var handler = com.golemlogix.system.LegacyResources.items(helper.getLevel(), helper.absolutePos(BUS_OUT), Direction.UP);
        helper.assertTrue(handler != null, "interface exposes an item handler");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "stock slots refuse insertion");
        helper.assertTrue(handler.insertItem(9, new ItemStack(Items.GOLD_INGOT, 5), false).isEmpty(), "intake accepts anything");
        helper.runAfterDelay(100, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(be.getStock().getItem(0).getCount() == 10, "keeps 10 iron in stock, has " + be.getStock().getItem(0));
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 22, "22 iron left in network");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 5, "intake went into the network");
            helper.assertTrue(be.getInbox().isEmpty(), "intake is empty");
            helper.assertTrue(handler.extractItem(9, 1, true).isEmpty(), "intake cannot be pulled back out");
            ItemStack taken = handler.extractItem(0, 4, false);
            helper.assertTrue(taken.getCount() == 4, "automation takes from stock");
            // keep fewer: the excess goes back to the network
            be.getConfig().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(be.getStock().getItem(0).getCount() == 2, "excess returned, stock " + be.getStock().getItem(0));
                helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 26, "network got the excess back");
                helper.succeed();
            });
        });
    }

    /**
     * Interface window: an item on the cursor sets a ghost (the item itself stays with the player), the amount is set as a number and
     * limited to a stack, clicks with an empty hand and "wheel" clicks take nothing out, Shift-click clears; fluid is
     * in mB, switching the mode converts the units; shift-click from the inventory puts into the input row, and the item goes to the network.
     */
    public static void interfaceMenuAmounts(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        ServerPlayer player = testPlayer(helper);
        var menu = new com.golemlogix.menu.InterfaceMenu(77, player.getInventory(), be.getConfig(), be.getStock(), be.getInbox(),
                be.getUpgrades(), be.menuData(), be);
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
        menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        helper.assertTrue(be.getConfig().getItem(0).is(Items.IRON_INGOT) && be.getConfig().getItem(0).getCount() == 1, "ghost is one item");
        helper.assertTrue(menu.getCarried().getCount() == 5, "the carried stack is not consumed");
        helper.assertTrue(be.amount(0) == 5 && menu.amount(0) == 5, "amount comes from the carried stack, got " + be.amount(0));
        menu.setAmount(0, 40);
        helper.assertTrue(menu.amount(0) == 40, "amount set by number, got " + menu.amount(0));
        menu.setAmount(0, 999);
        helper.assertTrue(be.amount(0) == 64, "item amount is capped by the stack size, got " + be.amount(0));
        menu.setCarried(ItemStack.EMPTY);
        for (var type : new net.minecraft.world.inventory.ContainerInput[]{net.minecraft.world.inventory.ContainerInput.PICKUP,
                net.minecraft.world.inventory.ContainerInput.SWAP, net.minecraft.world.inventory.ContainerInput.CLONE,
                net.minecraft.world.inventory.ContainerInput.THROW, net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT}) {
            menu.clicked(0, 0, type, player);
        }
        helper.assertTrue(menu.getCarried().isEmpty() && player.getInventory().isEmpty(), "nothing can be pulled out of a ghost slot");
        helper.assertTrue(be.getConfig().getItem(0).is(Items.IRON_INGOT) && be.amount(0) == 64, "stray clicks leave the slot alone");
        menu.setAmount(0, 6);
        // fluid from JEI: a bucket ghost, a bucket (1000 mB) by default, then in mB
        menu.setGhost(player, 1, new ItemStack(Items.WATER_BUCKET));
        helper.assertTrue(menu.isFluidSlot(1) && be.amount(1) == 1000, "fluid ghost keeps 1000 mB, got " + be.amount(1));
        menu.setAmount(1, 5500);
        helper.assertTrue(be.amount(1) == 5500, "fluid amount in mB, got " + be.amount(1));
        menu.clickMenuButton(player, com.golemlogix.menu.InterfaceMenu.BUTTON_MODE + 1);
        helper.assertTrue(!menu.isFluidSlot(1) && be.amount(1) == 1, "item mode keeps whole buckets, got " + be.amount(1));
        menu.clickMenuButton(player, com.golemlogix.menu.InterfaceMenu.BUTTON_MODE + 1);
        helper.assertTrue(menu.isFluidSlot(1) && be.amount(1) == 1000, "back to mB, got " + be.amount(1));
        menu.clicked(1, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(be.getConfig().getItem(1).isEmpty(), "shift-click clears the slot");
        // input row: shift-click from the inventory
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 7));
        int hotbar0 = -1;
        for (int k = 0; k < menu.slots.size(); k++) {
            if (menu.slots.get(k).container == player.getInventory() && menu.slots.get(k).getContainerSlot() == 0) {
                hotbar0 = k;
            }
        }
        menu.quickMoveStack(player, hotbar0);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "shift-click moves the stack out of the inventory");
        helper.assertTrue(be.getInbox().countItem(Items.GOLD_INGOT) == 7, "into the intake row");
        helper.assertTrue(menu.getSlot(2 * com.golemlogix.blockentity.InterfaceBlockEntity.SLOTS).mayPlace(new ItemStack(Items.DIRT)),
                "intake slots accept manual placement");
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.IRON_INGOT, 32), false));
        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 7, "manual intake went into the network");
            helper.assertTrue(be.getStock().getItem(0).getCount() == 6, "keeps the configured 6 iron, has " + be.getStock().getItem(0));
            helper.succeed();
        });
    }

    /** A provider with a selected feeding side feeds only the machine on that side; the window button cycles the sides. */
    public static void providerOutputSide(GameTestHelper helper) {
        BlockPos prov = new BlockPos(3, 1, 3);
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(prov.east(), Blocks.CHEST);
        helper.setBlock(prov.west(), Blocks.CHEST);
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);
        PatternData furnace = new PatternData(false, List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        ServerPlayer player = testPlayer(helper);
        var menu = new com.golemlogix.menu.PatternProviderMenu(21, player.getInventory(), provider.getContainer(), provider.getReturns(), provider.getUpgrades(),
                provider.menuData());
        helper.assertTrue(provider.output() == com.golemlogix.blocks.ProviderOutput.ALL, "all sides by default");
        // all -> down -> up -> north -> south -> west
        for (int i = 0; i < 5; i++) {
            menu.clickMenuButton(player, com.golemlogix.menu.PatternProviderMenu.BUTTON_OUTPUT);
        }
        helper.assertTrue(provider.output() == com.golemlogix.blocks.ProviderOutput.WEST, "the button walks the sides, got " + provider.output());
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(provider.tryPush(furnace) != null, "pushed into the west chest");
        }
        var west = ((Container) helper.getBlockEntity(prov.west(), net.minecraft.world.level.block.entity.BlockEntity.class));
        var east = ((Container) helper.getBlockEntity(prov.east(), net.minecraft.world.level.block.entity.BlockEntity.class));
        helper.assertTrue(west.countItem(Items.RAW_IRON) == 3 && east.countItem(Items.RAW_IRON) == 0,
                "only the west machine is fed: west " + west.countItem(Items.RAW_IRON) + ", east " + east.countItem(Items.RAW_IRON));
        helper.setBlock(prov.west(), Blocks.AIR);
        helper.assertTrue(provider.tryPush(furnace) == null, "nothing on the chosen side — no push even though the east chest is free");
        helper.succeed();
    }

    /** Drive cabinet: while its window is open, the display door is open (all viewers count). */
    public static void driveBayDoorFollowsWindow(GameTestHelper helper) {
        putBay(helper, BAY);
        CopperDriveBayBlockEntity bay = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        ServerPlayer a = testPlayer(helper), b = testPlayer(helper);
        var m1 = new com.golemlogix.menu.DriveBayMenu(11, a.getInventory(), bay.getContainer(), bay.priorityData());
        var m2 = new com.golemlogix.menu.DriveBayMenu(12, b.getInventory(), bay.getContainer(), bay.priorityData());
        helper.assertTrue(bay.viewers() == 2, "two players look into the bay, got " + bay.viewers());
        m1.removed(a);
        helper.assertTrue(bay.viewers() == 1, "the door stays open while someone still looks");
        m2.removed(b);
        helper.assertTrue(bay.viewers() == 0, "and closes after the last one");
        helper.succeed();
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

    /**
     * Cryo-Mainframe: assembled from glass, casings and two atlants; two calculators inside give four cores, a Compressed
     * Co-Processor counts as two (9 steps per cycle per core); the whole cabinet takes one channel; an order goes to a cabinet core;
     * the window opens the doors; a broken part disassembles the cabinet and returns the other blocks.
     */
    public static void mainframeAssemblesAndCrafts(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos ctl = new BlockPos(5, 1, 1);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        int[] channelsBefore = new int[1];
        helper.runAfterDelay(5, () -> {
            channelsBefore[0] = net(helper, bay).channelsUsed();
            // a pipe from the array's corner bushing to the back row of the cabinet
            put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
            buildMainframe(helper, ctl);
        });
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "the mainframe assembles");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 1)).is(ModBlocks.MAINFRAME_PART.get()), "glass became a part");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 1)).is(ModBlocks.MAINFRAME_PART.get())
                    && helper.getBlockState(new BlockPos(7, 2, 1)).is(ModBlocks.MAINFRAME_PART.get()), "the atlants are built into the cabinet");
            var mf = helper.getBlockEntity(ctl, com.golemlogix.blockentity.CryoMainframeBlockEntity.class);
            mf.getCalculators().setItem(0, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
            mf.getCalculators().setItem(5, new ItemStack(ModBlocks.CRYO_CALCULATOR.get()));
            mf.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
            helper.assertTrue(mf.cores().size() == 4, "two calculators give four cores, got " + mf.cores().size());
            int expected = CryoCalculatorBlockEntity.BASE_WORKERS + CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED * 2;
            helper.assertTrue(mf.workersPerCore() == expected, "a co-processor counts twice: " + mf.workersPerCore() + " != " + expected);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.channelsUsed() == channelsBefore[0] + 1, "the whole cabinet takes one channel: "
                    + channelsBefore[0] + " -> " + n.channelsUsed());
            helper.assertTrue(n.cpus().size() == 4, "the network sees four cores, got " + n.cpus().size());
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            var started = n.startCrafting(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 16);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "crafting starts on a mainframe core: " + started.type());
            helper.assertTrue(mf.busyCores() == 1, "one core is busy");
            // window: the doors open while someone looks
            ServerPlayer player = testPlayer(helper);
            var menu = new com.golemlogix.menu.MainframeMenu(91, player.getInventory(), mf, mf.getCalculators(), mf.getUpgrades(),
                    mf.menuData(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
            helper.assertTrue(mf.viewers() == 1, "the doors open while the window is open");
            helper.assertTrue(!menu.getSlot(1).mayPlace(new ItemStack(Items.DIRT)), "shelves take only calculators");
            menu.removed(player);
            helper.assertTrue(mf.viewers() == 0, "and close with it");
        });
        helper.runAfterDelay(50, () -> {
            var mf = helper.getBlockEntity(ctl, com.golemlogix.blockentity.CryoMainframeBlockEntity.class);
            helper.assertTrue(mf.busyCores() == 0 && mf.isBoosted(), "the sped-up order is over at once and still calls the foreman");
        });
        helper.runAfterDelay(140, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 16, "the core crafted 16 planks, got "
                    + n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))));
            var mf = helper.getBlockEntity(ctl, com.golemlogix.blockentity.CryoMainframeBlockEntity.class);
            helper.assertTrue(mf.busyCores() == 0, "the core is free again");
            // the glass was broken: the cabinet is disassembled, the other parts are glass and casings again
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 1, 1)), true);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "disassembled");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 1, 1)).is(Blocks.GLASS), "glass is back");
            helper.assertTrue(helper.getBlockState(new BlockPos(5, 1, 2)).is(ModBlocks.ATLAS_CASING.get()), "casing is back");
            helper.assertTrue(mf.cores().size() == 4, "the calculators stay inside");
            helper.assertTrue(net(helper, bay).freeCpu() == null, "an unassembled cabinet takes no orders");
            BlockState lower = helper.getBlockState(new BlockPos(3, 1, 1)), upper = helper.getBlockState(new BlockPos(3, 2, 1));
            helper.assertTrue(lower.is(ModBlocks.ATLAS_ATLANT.get()) && lower.getValue(AtlantBlock.HALF) == DoubleBlockHalf.LOWER
                    && upper.is(ModBlocks.ATLAS_ATLANT.get()) && upper.getValue(AtlantBlock.HALF) == DoubleBlockHalf.UPPER,
                    "both halves of the atlant are back");
            // glass back in place: the cabinet assembles again
            helper.setBlock(new BlockPos(4, 1, 1), Blocks.GLASS);
        });
        helper.runAfterDelay(175, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "reassembled");
            helper.killAllEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class);
            // the upper half of a built-in atlant was broken: one atlant drops, the lower half disappears
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(7, 2, 1)), true);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CryoMainframeBlock.FORMED), "disassembled again");
            helper.assertTrue(helper.getBlockState(new BlockPos(7, 1, 1)).isAir(), "the other half of that atlant is gone");
            helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 1)).is(ModBlocks.ATLAS_ATLANT.get()), "the other atlant is back whole");
            int atlants = 0;
            for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(7, 1, 1))).inflate(3))) {
                if (e.getItem().is(ModBlocks.ATLAS_ATLANT.get().asItem())) {
                    atlants += e.getItem().getCount();
                }
            }
            helper.assertTrue(atlants == 1, "exactly one atlant drops, got " + atlants);
            helper.succeed();
        });
    }

    private static Block statue(String prefix) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(
                net.minecraft.resources.Identifier.withDefaultNamespace(prefix + "copper_golem_statue"));
    }

    /** A north-facing Punch Card Catalog with the owner at ctl: shelves, copper at the back, pedestals and statues (the right one oxidized). */
    private static void buildCatalog(GameTestHelper helper, BlockPos ctl) {
        helper.setBlock(ctl, ModBlocks.CARD_CATALOG.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.CardCatalogBlock.FACING, Direction.NORTH));
        BlockPos abs = helper.absolutePos(ctl);
        for (int i = 0; i < com.golemlogix.system.CatalogStructure.CELLS.length; i++) {
            BlockPos p = com.golemlogix.system.CatalogStructure.cellPos(abs, Direction.NORTH, i);
            Block b = com.golemlogix.system.CatalogStructure.isShelf(i) ? Blocks.BOOKSHELF
                    : com.golemlogix.system.CatalogStructure.isStatueCell(i) ? statue(i == 14 ? "oxidized_" : "")
                    : i == 5 ? Blocks.WAXED_WEATHERED_COPPER : Blocks.COPPER_BLOCK;
            helper.getLevel().setBlock(p, b.defaultBlockState(), 3);
        }
    }

    /**
     * Punch Card Catalog: assembled from shelves, copper and two copper golem statues; without providers it has no places, each
     * provider gives 27; accepts only crafting-table punch cards; providers whose places are taken cannot be removed; search
     * finds a card; its pattern is visible to the network (the cabinet is one channel), a calculator crafts by it without a provider; breaking a part
     * disassembles the cabinet and returns the former blocks (the statue with the same oxidation).
     */
    public static void catalogStoresCraftingCards(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos ctl = new BlockPos(5, 1, 1);
        int[] channelsBefore = new int[1];
        helper.runAfterDelay(5, () -> {
            helper.setBlock(new BlockPos(6, 1, 3), ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
            channelsBefore[0] = net(helper, bay).channelsUsed();
            put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
            buildCatalog(helper, ctl);
        });
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CardCatalogBlock.FORMED), "the catalog assembles");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 2, 1)).is(ModBlocks.CATALOG_PART.get())
                    && helper.getBlockState(new BlockPos(7, 2, 1)).is(ModBlocks.CATALOG_PART.get()), "shelves and statues became parts");
            var cat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.CardCatalogBlockEntity.class);
            helper.assertTrue(cat.golemOxidation(1) == 3 && cat.golemOxidation(0) == 0, "golems take the statues' oxidation");
            ServerPlayer player = testPlayer(helper);
            var menu = new com.golemlogix.menu.CardCatalogMenu(93, player.getInventory(), cat, net.minecraft.world.inventory.ContainerLevelAccess.NULL);
            ItemStack plankCard = card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)));
            helper.assertTrue(cat.capacity() == 0 && !menu.getSlot(0).mayPlace(plankCard), "no providers — no places");
            cat.getProviders().setItem(0, new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), 2));
            helper.assertTrue(cat.capacity() == 54, "two providers give 54 places, got " + cat.capacity());
            helper.assertTrue(menu.getSlot(0).mayPlace(plankCard), "now a crafting card fits");
            PatternData machine = new PatternData(false, List.of(new ItemStack(Items.IRON_ORE)), List.of(new ItemStack(Items.IRON_INGOT)));
            helper.assertTrue(!menu.getSlot(0).mayPlace(card(machine)), "machine patterns stay with providers at machines");
            // a card in a far place: two providers can no longer be taken, the third one can
            cat.getCards().setItem(30, plankCard);
            var provSlot = (com.golemlogix.menu.CardCatalogMenu.ProviderSlot) menu.getSlot(menu.providerSlot());
            helper.assertTrue(provSlot.removable() == 0 && !provSlot.mayPickup(player), "occupied places keep their providers");
            cat.getProviders().setItem(0, new ItemStack(ModBlocks.PATTERN_PROVIDER.get(), 3));
            helper.assertTrue(provSlot.removable() == 1, "the third provider is free to take, removable " + provSlot.removable());
            // shift-click with a card from the inventory puts it into the first free place
            player.getInventory().setItem(0, card(craftingPattern(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS),
                    ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS))));
            int invSlot = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == 0) {
                    invSlot = i;
                }
            }
            menu.quickMoveStack(player, invSlot);
            helper.assertTrue(!cat.getCards().getItem(0).isEmpty(), "shift-click puts the card into the first free place");
            // search: the row shows only the cards found
            menu.view(0, "plank");
            helper.assertTrue(ItemStack.matches(menu.getSlot(0).getItem(), plankCard) && menu.getSlot(1).getItem().isEmpty(),
                    "the search shows the planks card first");
            menu.view(0, "");
            menu.removed(player);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.channelsUsed() == channelsBefore[0] + 1, "the whole cabinet takes one channel: "
                    + channelsBefore[0] + " -> " + n.channelsUsed());
            helper.assertTrue(n.canCraft(ItemKey.of(planks)) && n.canCraft(ItemKey.of(new ItemStack(Items.STICK))),
                    "the network knows the catalog patterns");
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            var started = n.startCrafting(ItemKey.of(planks), 16);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "crafting from a catalog card starts: " + started.type());
        });
        helper.runAfterDelay(160, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(planks)) == 16, "the calculator crafted 16 planks, got " + n.itemCountOf(ItemKey.of(planks)));
            // a shelf was broken: the cabinet is disassembled, the other blocks are in place, the patterns are gone from the network
            helper.getLevel().destroyBlock(helper.absolutePos(new BlockPos(4, 1, 1)), true);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.CardCatalogBlock.FORMED), "disassembled");
            helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 1)).is(Blocks.BOOKSHELF), "shelves are back");
            helper.assertTrue(helper.getBlockState(new BlockPos(7, 2, 1)).is(statue("oxidized_")), "the oxidized statue is back");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.WAXED_WEATHERED_COPPER), "waxed copper is back as it was");
            helper.assertTrue(!net(helper, bay).canCraft(ItemKey.of(planks)), "an unassembled catalog gives no patterns");
            var cat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.CardCatalogBlockEntity.class);
            helper.assertTrue(cat.cardCount() == 2, "the cards stay inside");
            helper.succeed();
        });
    }

    /**
     * Cryo Schematic Concentrator: shows the network's providers by category (crafting-table ones under "Crafting Table", the others by the machine next to them),
     * the window slots are the providers' real slots; shift-click puts a machine card next to the machine; search keeps the needed category.
     */
    public static void concentratorGroupsProviders(GameTestHelper helper) {
        baseNetwork(helper);
        BlockPos craftProv = new BlockPos(4, 1, 7), conc = new BlockPos(6, 1, 7);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(PROV.east(), Blocks.FURNACE);
        helper.setBlock(craftProv, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(conc, ModBlocks.SCHEMATIC_CONCENTRATOR.get().defaultBlockState());
        PatternProviderBlockEntity furnaceProv = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        PatternProviderBlockEntity crafting = helper.getBlockEntity(craftProv, PatternProviderBlockEntity.class);
        ItemStack smelt = card(new PatternData(false, List.of(new ItemStack(Items.IRON_ORE)), List.of(new ItemStack(Items.IRON_INGOT))));
        ItemStack planks = card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)));
        furnaceProv.getContainer().setItem(0, smelt.copy());
        crafting.getContainer().setItem(0, planks.copy());
        helper.runAfterDelay(5, () -> {
            ServerPlayer player = testPlayer(helper);
            var menu = new com.golemlogix.menu.ConcentratorMenu(94, player.getInventory(), helper.getLevel(), helper.absolutePos(conc));
            helper.assertTrue(menu.totalRows() == 4, "two categories with one provider each, rows " + menu.totalRows());
            helper.assertTrue(!menu.getSlot(0).isActive() && menu.getSlot(9).isActive(), "a header row has no slots, a provider row has nine");
            helper.assertTrue(ItemStack.matches(menu.getSlot(9).getItem(), planks), "crafting cards come first");
            helper.assertTrue(ItemStack.matches(menu.getSlot(27).getItem(), smelt), "then the furnace provider");
            // a machine card by shift-click goes next to the machine, not into the crafting-table provider
            ItemStack blast = card(new PatternData(false, List.of(new ItemStack(Items.GOLD_ORE)), List.of(new ItemStack(Items.GOLD_INGOT))));
            player.getInventory().setItem(0, blast.copy());
            int invSlot = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == 0) {
                    invSlot = i;
                }
            }
            menu.quickMoveStack(player, invSlot);
            helper.assertTrue(ItemStack.matches(furnaceProv.getContainer().getItem(1), blast), "the smelting card went to the furnace provider");
            // a card can be taken right out of the window
            ItemStack taken = menu.getSlot(9).remove(1);
            helper.assertTrue(ItemStack.matches(taken, planks) && crafting.getContainer().getItem(0).isEmpty(), "taking a card empties the provider");
            crafting.getContainer().setItem(0, planks.copy());
            menu.view(0, "ingot");
            helper.assertTrue(menu.totalRows() == 2 && ItemStack.matches(menu.getSlot(9).getItem(), smelt), "search keeps only the furnace row");
            var be = helper.getBlockEntity(conc, com.golemlogix.blockentity.SchematicConcentratorBlockEntity.class);
            helper.assertTrue(be.viewers() == 1, "the hologram opens with the window");
            menu.removed(player);
            helper.assertTrue(be.viewers() == 0, "and closes with it");
            helper.succeed();
        });
    }

    public static void interfaceCraftsMissingStock(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        be.getConfig().setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        be.getUpgrades().setItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 4), false));
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(be.getStock().getItem(0).getCount() == 8, "interface ordered and stocked 8 planks, has " + be.getStock().getItem(0));
            helper.succeed();
        });
    }

    private static ItemStack card(PatternData data) {
        return com.golemlogix.items.CopperPunchCardItem.written(ModItems.COPPER_PUNCH_CARD.get(), data);
    }

    private static PatternData craftingPattern(ItemStack output, ItemStack... slots) {
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i < slots.length ? slots[i] : ItemStack.EMPTY);
        }
        return new PatternData(true, grid, List.of(output));
    }

    public static void autocraftTree(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4),
                plank.copy(), ItemStack.EMPTY, ItemStack.EMPTY, plank.copy())));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 1), false);

            ItemKey sticks = ItemKey.of(new ItemStack(Items.STICK));
            helper.assertTrue(n.canCraft(sticks), "sticks must be craftable");
            CraftingPlan plan = CraftingPlanner.plan(n, sticks, 8);
            helper.assertTrue(plan.missing().isEmpty(), "plan has missing items: " + plan.missing());
            helper.assertTrue(plan.steps().size() == 2, "two patterns expected, got " + plan.steps().size());
            helper.assertTrue(plan.totalExecutions() == 3, "1 planks craft + 2 stick crafts, got " + plan.totalExecutions());

            CraftingPlan big = CraftingPlanner.plan(n, sticks, 100);
            helper.assertTrue(!big.missing().isEmpty(), "100 sticks need more logs");

            GolemNetwork.StartResult r = n.startCrafting(sticks, 8);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 0, "log must be reserved");
        });

        helper.runAfterDelay(150, () -> {
            GolemNetwork n = net(helper, bay);
            long sticksNow = n.itemCountOf(ItemKey.of(new ItemStack(Items.STICK)));
            helper.assertTrue(sticksNow == 8, "expected 8 sticks, got " + sticksNow);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 0, "no planks left");
            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(cpu.isIdle(), "calculator must be idle again");
            helper.succeed();
        });
    }

    /**
     * The foreman: an order the co-processors sped up still calls him when it is over at once (it used to end the speed-up in the
     * same tick, so on a big speed-up he never came); he leaves a while later.
     */
    public static void calculatorForemanAfterQuickJob(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
        cpu.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
        ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            GolemNetwork.StartResult r = n.startCrafting(planks, 16);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(net(helper, bay).itemCountOf(planks) == 16, "16 planks crafted");
            helper.assertTrue(cpu.isIdle(), "the order is over");
            helper.assertTrue(cpu.isBoosted(), "the co-processor sped it up: the foreman is called though the order is over");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(!cpu.isBoosted(), "and he leaves a while later");
            helper.succeed();
        });
    }

    /** A fuzzy crafting table pattern takes what the recipe accepts in each cell: birch planks for a crafting table encoded with oak. */
    public static void fuzzyCraftingTakesSubstitutes(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        ItemStack oak = new ItemStack(Items.OAK_PLANKS);
        PatternData exact = craftingPattern(new ItemStack(Items.CRAFTING_TABLE), oak, oak, ItemStack.EMPTY, oak, oak);
        List<String> fuzzy = new ArrayList<>(java.util.Collections.nCopies(9, ""));
        for (int i : new int[]{0, 1, 3, 4}) {
            fuzzy.set(i, com.golemlogix.system.Fuzzy.RECIPE);
        }
        PatternData loose = new PatternData(true, exact.inputs(), exact.outputs(), fuzzy);
        provider.getContainer().setItem(0, card(exact));
        ItemKey table = ItemKey.of(new ItemStack(Items.CRAFTING_TABLE));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.BIRCH_PLANKS, 4), false);
            helper.assertTrue(!CraftingPlanner.plan(n, table, 1).missing().isEmpty(), "an exact pattern wants oak planks");
            provider.getContainer().setItem(0, card(loose));
        });
        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            CraftingPlan plan = CraftingPlanner.plan(n, table, 1);
            helper.assertTrue(plan.missing().isEmpty(), "a fuzzy one takes birch planks: " + plan.missing());
            helper.assertTrue(n.startCrafting(table, 1).type() == GolemNetwork.StartType.STARTED, "started");
        });
        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(table) == 1 && n.itemCountOf(ItemKey.of(new ItemStack(Items.BIRCH_PLANKS))) == 0,
                    "a crafting table out of birch planks");
            helper.succeed();
        });
    }

    /** A fuzzy processing input with a tag: any glass of #c:glass_blocks goes into the machine for "glass". */
    public static void fuzzyProcessingByTag(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        BlockPos machine = PROV.east();
        helper.setBlock(machine, Blocks.CHEST);
        Container machineInv = ((Container) helper.getBlockEntity(machine, net.minecraft.world.level.block.entity.BlockEntity.class));
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.GLASS, 2)),
                List.of(new ItemStack(Items.GLASS_PANE, 16)), List.of("#c:glass_blocks"))));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RED_STAINED_GLASS, 2), false);
            GolemNetwork.StartResult r = n.startCrafting(ItemKey.of(new ItemStack(Items.GLASS_PANE)), 16);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
        });
        helper.runAfterDelay(60, () -> {
            int red = 0;
            for (int i = 0; i < machineInv.getContainerSize(); i++) {
                if (machineInv.getItem(i).is(Items.RED_STAINED_GLASS)) {
                    red += machineInv.getItem(i).getCount();
                }
            }
            helper.assertTrue(red == 2, "the machine gets the red glass for \"glass\", got " + red);
            helper.succeed();
        });
    }

    /** A Fuzzy Card: filters with a tag let any glass through the import bus and out of the export bus; without it, only glass. */
    public static void fuzzyCardOnBuses(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        ItemStack glassAny = com.golemlogix.system.Fuzzy.withTag(new ItemStack(Items.GLASS),
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse("c:glass_blocks")));
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chestIn = new BlockPos(2, 1, 4);
        helper.setBlock(chestIn, Blocks.CHEST);
        Container in = ((Container) helper.getBlockEntity(chestIn, net.minecraft.world.level.block.entity.BlockEntity.class));
        in.setItem(0, new ItemStack(Items.RED_STAINED_GLASS, 5));
        in.setItem(1, new ItemStack(Items.DIRT, 5));
        BusBlockEntity importer = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);
        importer.getFilters().setItem(0, glassAny.copy());
        importer.getUpgrades().setItem(0, new ItemStack(ModItems.FUZZY_CARD.get()));

        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos chestOut = new BlockPos(6, 1, 2);
        helper.setBlock(chestOut, Blocks.CHEST);
        Container out = ((Container) helper.getBlockEntity(chestOut, net.minecraft.world.level.block.entity.BlockEntity.class));
        BusBlockEntity exporter = helper.getBlockEntity(BUS_OUT, BusBlockEntity.class);
        exporter.getFilters().setItem(0, glassAny.copy());

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(in.getItem(0).isEmpty() && in.getItem(1).getCount() == 5, "the glass came in, the dirt stayed");
            helper.assertTrue(out.isEmpty(), "without a card the exporter wants plain glass only");
            exporter.getUpgrades().setItem(0, new ItemStack(ModItems.FUZZY_CARD.get()));
        });
        helper.runAfterDelay(140, () -> {
            int red = 0;
            for (int i = 0; i < out.getContainerSize(); i++) {
                if (out.getItem(i).is(Items.RED_STAINED_GLASS)) {
                    red += out.getItem(i).getCount();
                }
            }
            helper.assertTrue(red == 5, "with the card the red glass goes out, got " + red);
            helper.succeed();
        });
    }

    /** A Fuzzy Card in the Interface: a config slot of "glass" with a tag keeps any glass in stock. */
    public static void fuzzyCardOnInterface(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        be.getConfig().setItem(0, com.golemlogix.system.Fuzzy.withTag(new ItemStack(Items.GLASS, 4),
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Identifier.parse("c:glass_blocks"))));
        be.getUpgrades().setItem(0, new ItemStack(ModItems.FUZZY_CARD.get()));
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.BLUE_STAINED_GLASS, 10), false));
        helper.runAfterDelay(120, () -> {
            ItemStack stock = be.getStock().getItem(0);
            helper.assertTrue(stock.is(Items.BLUE_STAINED_GLASS) && stock.getCount() == 4, "4 blue glass kept for \"glass\", got " + stock);
            helper.succeed();
        });
    }

    public static void autocraftProcessing(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        // the "machine" is a chest behind the provider
        BlockPos machine = PROV.east();
        helper.setBlock(machine, Blocks.CHEST);
        Container machineInv = ((Container) helper.getBlockEntity(machine, net.minecraft.world.level.block.entity.BlockEntity.class));
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 3), false);
            GolemNetwork.StartResult r = n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 3);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
        });

        helper.runAfterDelay(60, () -> {
            int ore = 0;
            for (int i = 0; i < machineInv.getContainerSize(); i++) {
                ore += machineInv.getItem(i).getCount();
            }
            helper.assertTrue(ore == 3, "machine must receive 3 raw iron, got " + ore);
            machineInv.clearContent();
            GolemNetwork n = net(helper, bay);
            ItemStack rest = n.insertFromMachine(new ItemStack(Items.IRON_INGOT, 3));
            helper.assertTrue(rest.isEmpty(), "job absorbs the outputs");
        });

        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            long ingots = n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT)));
            helper.assertTrue(ingots == 3, "expected 3 ingots delivered to storage, got " + ingots);
            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(cpu.isIdle(), "calculator idle after delivery");
            helper.succeed();
        });
    }

    public static void networkNeedsSource(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        putBay(helper, BAY);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.hasCore(), "core");
            if (!com.golemlogix.system.SourceHelper.sourceRequired()) {
                // without Ars Nouveau the network works for free
                helper.assertTrue(n.isOnline(), "without Ars Nouveau the network needs no Source");
                helper.succeed();
                return;
            }
            helper.assertTrue(!n.isOnline(), "no source => offline");
            helper.assertTrue("message.golem_logix.no_power".equals(n.accessError(null)), "access error");
            // the Resonator powers only chronometers, not the network
            put(helper, JAR, ModBlocks.ETERNAL_FROST_RESONATOR.get());
            helper.runAfterDelay(25, () -> {
                GolemNetwork n2 = net(helper, ORIGIN);
                helper.assertTrue(n2.hasResonator() && !n2.isOnline(), "the resonator does not power the network");
                put(helper, JAR, ModBlocks.CREATIVE_SOURCE_JAR.get());
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(net(helper, ORIGIN).isOnline(), "a Source jar does");
                    helper.succeed();
                });
            });
        });
    }

    public static void formationOrientsAtlants(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        helper.runAfterDelay(5, () -> {
            BlockState northCorner = helper.getBlockState(ORIGIN.offset(1, 1, -1));
            BlockState southCorner = helper.getBlockState(ORIGIN.offset(-1, 2, 1));
            helper.assertTrue(northCorner.getValue(AtlantBlock.FACING) == Direction.NORTH, "northern atlants look north");
            helper.assertTrue(southCorner.getValue(AtlantBlock.FACING) == Direction.SOUTH, "southern atlants look south");
            helper.succeed();
        });
    }

    // --------------------------------------------------------------- disks

    public static void fluidDisks(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity be = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        be.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.fluidCapacity() == 4000, "fluid capacity " + n.fluidCapacity());
            helper.assertTrue(n.insertFluid(new FluidStack(Fluids.WATER, 3000), false) == 3000, "3000 mB fit");
            helper.assertTrue(n.insertFluid(new FluidStack(Fluids.LAVA, 5000), false) == 1000, "only 1000 mB left");
            helper.assertTrue(n.listFluids().size() == 2, "water and lava");
            FluidStack got = n.extractFluid(new FluidStack(Fluids.WATER, 1), 1200, false);
            helper.assertTrue(got.getAmount() == 1200, "extracted water " + got.getAmount());
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(1)) == 2800, "disk stored " + DiskStorage.storedItems(be.getDisks().get(1)));
            helper.assertTrue(DiskStorage.insertFluid(be.getDisks().get(0), new FluidStack(Fluids.WATER, 10), false) == 0, "item disk rejects fluids");
            helper.succeed();
        });
    }

    public static void interfaceAcceptsFluids(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var fluids = com.golemlogix.system.LegacyResources.fluids(helper.getLevel(), helper.absolutePos(BUS_OUT), Direction.WEST);
        helper.assertTrue(fluids != null, "interface exposes a fluid handler");
        helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 2500), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 2500,
                "interface takes 2500 mB");
        helper.assertTrue(fluids.drain(100, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(), "cannot be drained back");
        helper.runAfterDelay(120, () -> {
            GolemNetwork n = net(helper, bay);
            FluidStack got = n.extractFluid(new FluidStack(Fluids.WATER, 1), 4000, true);
            helper.assertTrue(got.getAmount() == 2500, "all water went into the network, got " + got.getAmount());
            helper.succeed();
        });
    }

    public static void interfaceStocksFluidPerSlot(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_16K.get()));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        // slot 0: three water buckets in "fluid" mode (the default); slot 1: a water bucket in "item" mode
        be.getConfig().setItem(0, new ItemStack(Items.WATER_BUCKET, 3));
        be.getConfig().setItem(1, new ItemStack(Items.WATER_BUCKET, 1));
        be.toggleMode(1);
        helper.runAfterDelay(2, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertFluid(new FluidStack(Fluids.WATER, 8000), false);
            n.insertItem(new ItemStack(Items.WATER_BUCKET), false);
        });
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(be.fluidStock(0).getAmount() == 3000, "keeps 3000 mB of water, has " + be.fluidStock(0).getAmount());
            helper.assertTrue(be.getStock().getItem(0).isEmpty(), "fluid slot keeps no buckets");
            helper.assertTrue(be.getStock().getItem(1).is(Items.WATER_BUCKET), "item-mode slot keeps the bucket itself");
            var fluids = com.golemlogix.system.LegacyResources.fluids(helper.getLevel(), helper.absolutePos(BUS_OUT), Direction.UP);
            FluidStack out = fluids.drain(1500, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(out.getAmount() == 1500 && out.is(Fluids.WATER), "pipes drain the stocked water, got " + out.getAmount());
            helper.succeed();
        });
    }

    /**
     * Another mod's fluids (Mekanism, if loaded: gradle -Pmekanism): its buckets and its tank in a config slot are recognized as
     * fluid, the interface fills it from the network and gives it to pipes. Without Mekanism the test passes empty.
     */
    public static void interfaceModdedFluids(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
            helper.succeed();
            return;
        }
        var items = net.minecraft.core.registries.BuiltInRegistries.ITEM;
        var fluidsReg = net.minecraft.core.registries.BuiltInRegistries.FLUID;
        ItemStack brineBucket = new ItemStack(items.getValue(net.minecraft.resources.Identifier.parse("mekanism:brine_bucket")), 2);
        ItemStack heavyBucket = new ItemStack(items.getValue(net.minecraft.resources.Identifier.parse("mekanism:heavy_water_bucket")), 1);
        // a Mekanism portable tank filled with sulfuric acid through its own item container
        ItemStack tank = new ItemStack(items.getValue(net.minecraft.resources.Identifier.parse("mekanism:basic_fluid_tank")));
        var acid = fluidsReg.getValue(net.minecraft.resources.Identifier.parse("mekanism:sulfuric_acid"));
        var tankCap = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(tank).orElse(null);
        helper.assertTrue(tankCap != null, "Mekanism tank item exposes a fluid handler");
        // Mekanism fills an item tank with at most a bucket at a time, so in portions
        int filled = 0;
        for (int k = 0; k < 8 && filled < 4000; k++) {
            filled += tankCap.fill(new FluidStack(acid, 4000 - filled), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        ItemStack filledTank = tankCap.getContainer();
        helper.assertTrue(filled == 4000, "tank item took 4000 mB of acid, took " + filled);

        var brine = com.golemlogix.blockentity.InterfaceBlockEntity.containedFluid(brineBucket);
        var heavy = com.golemlogix.blockentity.InterfaceBlockEntity.containedFluid(heavyBucket);
        helper.assertTrue(!brine.isEmpty() && brine.getFluid().builtInRegistryHolder().key().identifier().getPath().contains("brine"), "brine bucket -> " + brine.getFluid());
        helper.assertTrue(!heavy.isEmpty(), "heavy water bucket -> " + heavy.getFluid());
        helper.assertTrue(com.golemlogix.blockentity.InterfaceBlockEntity.containedFluid(filledTank).is(acid), "filled tank -> acid");

        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_64K.get()));
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        be.getConfig().setItem(0, brineBucket);
        be.getConfig().setItem(1, heavyBucket);
        be.getConfig().setItem(2, filledTank.copyWithCount(1));
        helper.runAfterDelay(2, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertFluid(brine.copyWithAmount(5000), false);
            n.insertFluid(heavy.copyWithAmount(5000), false);
            n.insertFluid(new FluidStack(acid, 5000), false);
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(be.fluidStock(0).getAmount() == 2000, "2 buckets of brine kept, has " + be.fluidStock(0).getAmount());
            helper.assertTrue(be.fluidStock(1).getAmount() == 1000, "1 bucket of heavy water kept, has " + be.fluidStock(1).getAmount());
            // one item in a slot = 1000 mB, even though the tank holds more
            helper.assertTrue(be.fluidStock(2).getAmount() == 1000 && be.fluidStock(2).is(acid), "acid by the tank kept, has " + be.fluidStock(2).getAmount());
            var cap = com.golemlogix.system.LegacyResources.fluids(helper.getLevel(), helper.absolutePos(BUS_OUT), Direction.NORTH);
            FluidStack out = cap.drain(brine.copyWithAmount(700), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(out.getAmount() == 700, "pipes drain Mekanism brine, got " + out.getAmount());
            helper.succeed();
        });
    }

    public static void diskStoreRoundTrip(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack itemDisk = new ItemStack(ModItems.COPPER_DISK_4K.get());
        ItemStack fluidDisk = new ItemStack(ModItems.FLUID_DISK_4K.get());
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(7);
        helper.assertTrue(DiskStorage.insertItem(itemDisk, new ItemStack(Items.COBBLESTONE, 500), false) == 500, "500 cobble");
        helper.assertTrue(DiskStorage.insertItem(itemDisk, sword, false) == 1, "component-carrying stack stored separately");
        helper.assertTrue(DiskStorage.insertFluid(fluidDisk, new FluidStack(Fluids.WATER, 1234), false) == 1234, "water");
        java.util.UUID itemId = itemDisk.get(ModDataComponents.DISK_ID.get());
        java.util.UUID fluidId = fluidDisk.get(ModDataComponents.DISK_ID.get());
        helper.assertTrue(itemId != null && fluidId != null && !itemId.equals(fluidId), "disks get unique ids");
        helper.assertTrue(itemDisk.get(ModDataComponents.STORED.get()) == 501, "STORED mirrors the content");

        DiskStore store = DiskStore.get(level.getServer());
        CompoundTag tag = store.save(new CompoundTag(), level.registryAccess());
        DiskStore loaded = DiskStore.load(tag, level.registryAccess());
        DiskStore.Disk a = loaded.getOrNull(itemId);
        helper.assertTrue(a != null && a.storedItems == 501, "items survive the save");
        helper.assertTrue(a.items.get(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 500, "cobblestone count");
        helper.assertTrue(a.items.get(ItemKey.of(sword)) == 1, "damaged sword is a separate type");
        DiskStore.Disk f = loaded.getOrNull(fluidId);
        helper.assertTrue(f != null && f.storedFluids == 1234 && f.fluids.get(0).getAmount() == 1234, "fluids survive the save");

        ItemStack moved = itemDisk.copy();
        helper.assertTrue(DiskStorage.extractItem(moved, new ItemStack(Items.COBBLESTONE), 100, false) == 100, "extract via the copy");
        helper.assertTrue(DiskStorage.storedItems(itemDisk) == 401, "copy and original share the same storage");
        helper.assertTrue(DiskStorage.extractItem(moved, new ItemStack(Items.COBBLESTONE), 1000, false) == 400, "rest of cobble");
        helper.assertTrue(DiskStorage.extractItem(moved, sword, 5, false) == 1, "sword back");
        helper.assertTrue(DiskStorage.storedItems(itemDisk) == 0 && moved.get(ModDataComponents.STORED.get()) == null,
                "empty disk drops its STORED counter");
        helper.succeed();
    }

    // ------------------------------------------------------------- menus

    /**
     * The pipe runner moves without jumps: at any moment exactly one pipe draws it, neighbouring positions are close, at a
     * turn it stands still and looks one way, hands over exactly the cargo it carried and leaves with what it received.
     * An L-shaped line with a branch and a vertical dead end, plus a single pipe between two devices.
     */
    public static void pipeRunnerMovesContinuously(GameTestHelper helper) {
        List<BlockPos> line = new ArrayList<>();
        for (int x = 1; x <= 6; x++) {
            line.add(new BlockPos(x, 1, 1));
        }
        line.add(new BlockPos(4, 1, 2));
        line.add(new BlockPos(4, 1, 3));
        line.add(new BlockPos(6, 2, 1));
        line.add(new BlockPos(6, 3, 1));
        for (BlockPos p : line) {
            helper.setBlock(p, ModBlocks.BASIC_SNOW_PIPE.get());
        }
        BlockPos lonely = new BlockPos(2, 1, 6);
        helper.setBlock(lonely.west(), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(lonely.east(), ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(lonely, ModBlocks.BASIC_SNOW_PIPE.get());
        // setBlock places a pipe without computing links: compute them from the neighbours, as a normal placement does
        List<BlockPos> all = new ArrayList<>(line);
        all.add(lonely);
        for (BlockPos p : all) {
            BlockPos abs = helper.absolutePos(p);
            helper.getLevel().setBlock(abs, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                    helper.getLevel().getBlockState(abs), helper.getLevel(), abs), 3);
        }
        helper.runAfterDelay(3, () -> {
            checkRunner(helper, line, 26 * 40);
            checkRunner(helper, List.of(lonely), 60 * 6);
            helper.succeed();
        });
    }

    private static void checkRunner(GameTestHelper helper, List<BlockPos> pipes, int ticks) {
        var level = helper.getLevel();
        net.minecraft.world.phys.Vec3 last = null;
        com.golemlogix.client.PipeRunners.Spot lastSpot = null;
        long base = 1_234_567L;
        for (int k = 0; k <= ticks * 4; k++) {
            long time = base + k / 4;
            float partial = (k % 4) / 4f;
            com.golemlogix.client.PipeRunners.Spot found = null;
            BlockPos where = null;
            int count = 0;
            for (BlockPos rel : pipes) {
                BlockPos abs = helper.absolutePos(rel);
                var spot = com.golemlogix.client.PipeRunners.locate(level, abs, level.getBlockState(abs), time, partial);
                if (spot != null) {
                    count++;
                    found = spot;
                    where = abs;
                }
            }
            helper.assertTrue(count == 1, "exactly one pipe draws the runner at tick " + k / 4f + ", got " + count);
            net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(where.getX() + 0.5 + found.dx(),
                    where.getY() + 0.5 + found.dy(), where.getZ() + 0.5 + found.dz());
            if (last != null) {
                helper.assertTrue(at.distanceTo(last) < 0.05, "the runner jumps " + at.distanceTo(last) + " blocks at tick " + k / 4f);
                if (found.isTurning() && !lastSpot.isTurning()) {
                    helper.assertTrue(found.incomingCargo() == lastSpot.outgoingCargo(), "hands over the cargo it carried");
                }
                if (!found.isTurning() && lastSpot.isTurning()) {
                    helper.assertTrue(found.outgoingCargo() == lastSpot.outgoingCargo(), "leaves with the cargo it got");
                }
                if (found.isTurning() && lastSpot.isTurning() && found.turnProgress() > lastSpot.turnProgress()) {
                    helper.assertTrue(found.heading() == lastSpot.heading(), "faces one way during a turn");
                }
            }
            last = at;
            lastSpot = found;
        }
    }

    /** Order planner: what is crafted, taken from the network and missing, row by row, missing ones first. */
    public static void craftPlannerRows(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4), new ItemStack(Items.OAK_PLANKS),
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS))));
        helper.runAfterDelay(3, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG), false);
            ItemKey stick = ItemKey.of(new ItemStack(Items.STICK)), plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS)),
                    log = ItemKey.of(new ItemStack(Items.OAK_LOG));
            var ok = TerminalMenu.planEntries(com.golemlogix.crafting.CraftingPlanner.plan(n, stick, 8));
            java.util.Map<ItemKey, com.golemlogix.network.CraftPlanPayload.Entry> rows = new java.util.HashMap<>();
            for (var e : ok) {
                rows.put(ItemKey.of(e.stack()), e);
            }
            helper.assertTrue(rows.get(stick).craft() == 8 && rows.get(plank).craft() == 4 && rows.get(log).stored() == 1
                    && rows.get(log).missing() == 0, "8 sticks: craft 8 sticks and 4 planks from the one log");
            var short_ = TerminalMenu.planEntries(com.golemlogix.crafting.CraftingPlanner.plan(n, stick, 40));
            var first = short_.get(0);
            helper.assertTrue(ItemKey.of(first.stack()).equals(log) && first.missing() == 4 && first.stored() == 1,
                    "40 sticks: the log row comes first with 4 missing, got " + first);
            helper.succeed();
        });
    }

    /**
     * An unassembled Saturator is an anvil: the Forge Hammer turns an iron block into one core in four strikes, every strike
     * spends the hammer's charge (if Source is required); a drained hammer does not strike; only the three base plates are forged by hand.
     */
    public static void saturatorAnvilForgesByHand(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.MANA_SATURATOR.get().defaultBlockState());
        ServerPlayer player = testPlayer(helper);
        var sat = helper.getBlockEntity(at, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
        for (ItemStack only : new ItemStack[]{new ItemStack(ModItems.IMPLODED_WITHER_PLASMA.get()),
                new ItemStack(ModBlocks.COOLING_COPROCESSOR.get())}) {
            helper.assertTrue(!com.golemlogix.blockentity.ManaSaturatorBlockEntity.isManualInput(helper.getLevel(), only), only + " needs the machine");
        }
        boolean source = com.golemlogix.system.SourceHelper.sourceRequired();
        ItemStack hammer = new ItemStack(ModItems.FORGE_HAMMER.get());
        int hits = com.golemlogix.blockentity.ManaSaturatorBlockEntity.MANUAL_HITS;
        int cost = com.golemlogix.items.ForgeHammerItem.STRIKE_COST;
        com.golemlogix.items.ForgeHammerItem.setCharge(hammer, cost * hits + 50);
        sat.placeOnAnvil(new ItemStack(Items.IRON_BLOCK));
        for (int k = 0; k < hits; k++) {
            int strike = k;
            helper.runAfterDelay(2 + k * 8, () -> {
                var r = sat.strike(helper.getLevel(), helper.absolutePos(at), player, hammer);
                helper.assertTrue(r == (strike == hits - 1 ? com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.DONE
                        : com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.HIT), "strike " + strike + " -> " + r);
            });
        }
        helper.runAfterDelay(4 + hits * 8, () -> {
            helper.assertTrue(sat.anvilItem().is(ModItems.FLATTENED_IRON_CORE.get()) && sat.anvilItem().getCount() == 1,
                    "one flattened iron core by hand, got " + sat.anvilItem());
            helper.assertTrue(!source || com.golemlogix.items.ForgeHammerItem.getCharge(hammer) == 50,
                    "each strike spends the hammer's charge, left " + com.golemlogix.items.ForgeHammerItem.getCharge(hammer));
            helper.assertTrue(!helper.getBlockState(at).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "still an anvil");
            // a drained hammer (when Source is needed) does not strike
            sat.takeFromAnvil();
            sat.placeOnAnvil(new ItemStack(Items.PACKED_ICE));
        });
        helper.runAfterDelay(14 + hits * 8, () -> {
            var r = sat.strike(helper.getLevel(), helper.absolutePos(at), player, hammer);
            helper.assertTrue(r == (source ? com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.NO_SOURCE
                    : com.golemlogix.blockentity.ManaSaturatorBlockEntity.Strike.HIT), "an empty hammer -> " + r);
            helper.succeed();
        });
    }
    /**
     * The Chronometer the terminal was opened through cannot be taken out of its slot (click, Shift, Q, number key swap or F with the
     * offhand), otherwise the window would close; other inventory items move as usual.
     */
    public static void chronometerSlotIsLocked(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        BlockPos anchor = helper.absolutePos(CORE);
        var inv = player.getInventory();
        inv.setItem(0, new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));
        inv.setItem(1, new ItemStack(Items.DIRT, 5));
        TerminalMenu menu = new TerminalMenu(79, inv, helper.getLevel(), anchor, null, null, true, true, 0, 0);
        int locked = menu.lockedMenuSlot();
        helper.assertTrue(locked >= 0, "the chronometer's hotbar slot is locked in the menu");
        int dirtSlot = -1, other = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            var s = menu.slots.get(i);
            if (s.container == inv && s.getContainerSlot() == 1) {
                dirtSlot = i;
            }
            if (s.container == inv && s.getContainerSlot() == 9) {
                other = i;
            }
        }
        for (var type : new net.minecraft.world.inventory.ContainerInput[]{net.minecraft.world.inventory.ContainerInput.PICKUP,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, net.minecraft.world.inventory.ContainerInput.THROW,
                net.minecraft.world.inventory.ContainerInput.PICKUP_ALL}) {
            menu.clicked(locked, 0, type, player);
            helper.assertTrue(inv.getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()) && menu.getCarried().isEmpty(),
                    "the chronometer stays put after " + type);
        }
        menu.clicked(other, 0, net.minecraft.world.inventory.ContainerInput.SWAP, player);
        helper.assertTrue(inv.getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()), "the hotbar key of its slot does not swap it out");
        menu.clicked(dirtSlot, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIRT), "other items still move");
        menu.clicked(locked, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIRT) && inv.getItem(0).is(ModItems.PORTABLE_CHRONOMETER.get()),
                "nothing can be dropped onto the chronometer either");
        menu.setCarried(ItemStack.EMPTY);
        // opened from the offhand: swapping with the F key (button 40) does not touch it
        inv.setItem(0, ItemStack.EMPTY);
        inv.setItem(1, new ItemStack(Items.DIRT, 5));
        inv.setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));
        TerminalMenu off = new TerminalMenu(80, inv, helper.getLevel(), anchor, null, null, true, true, 0, net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND);
        off.clicked(dirtSlot, net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, net.minecraft.world.inventory.ContainerInput.SWAP, player);
        helper.assertTrue(inv.getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND).is(ModItems.PORTABLE_CHRONOMETER.get()) && inv.getItem(1).is(Items.DIRT),
                "the offhand chronometer is not swapped away");
        helper.succeed();
    }

    public static void terminalMenuActions(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        ServerPlayer player = testPlayer(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 100), false);
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            BlockPos abs = helper.absolutePos(TERM);
            TerminalMenu menu = new TerminalMenu(77, player.getInventory(), helper.getLevel(), abs, abs,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            ItemStack key = new ItemStack(Items.IRON_INGOT);

            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_STACK, key, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 64, "carried 64, got " + menu.getCarried().getCount());
            helper.assertTrue(n.itemCountOf(iron) == 36, "36 left in network");

            menu.handleAction(player, new TerminalActionPayload(77, Action.INSERT_ONE, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 63, "carried 63");
            helper.assertTrue(n.itemCountOf(iron) == 37, "37 in network");

            menu.handleAction(player, new TerminalActionPayload(77, Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().isEmpty(), "carried empty after insert all");
            helper.assertTrue(n.itemCountOf(iron) == 100, "all 100 back");

            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_HALF, key, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 32, "half stack = 32, got " + menu.getCarried().getCount());
            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_ONE, key, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 33, "plus one");
            menu.handleAction(player, new TerminalActionPayload(77, Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().is(Items.IRON_INGOT), "still iron");

            menu.setCarried(ItemStack.EMPTY);
            menu.handleAction(player, new TerminalActionPayload(77, Action.TO_INVENTORY, key, FluidStack.EMPTY));
            int inInv = 0;
            for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
                if (s.is(Items.IRON_INGOT)) {
                    inInv += s.getCount();
                }
            }
            helper.assertTrue(inInv == 64, "64 moved to inventory, got " + inInv);

            int slot = menu.slots.size() - 1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).getItem().is(Items.IRON_INGOT)) {
                    slot = i;
                    break;
                }
            }
            menu.quickMoveStack(player, slot);
            helper.assertTrue(n.itemCountOf(iron) > 36, "quick move inserted into the network");
            helper.succeed();
        });
    }

    public static void craftingTerminalGrid(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(TERM, ModBlocks.CRAFTING_TERMINAL.get().defaultBlockState());
        ServerPlayer player = testPlayer(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_PLANKS, 10), false);
            BlockPos abs = helper.absolutePos(TERM);
            TerminalMenu menu = new TerminalMenu(78, player.getInventory(), helper.getLevel(), abs, abs,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.STICK), "result is sticks");
            helper.assertTrue(menu.getSlot(0).getItem().getCount() == 4, "4 sticks");

            menu.getSlot(0).onTake(player, menu.getSlot(0).getItem());
            helper.assertTrue(menu.getSlot(1).getItem().is(Items.OAK_PLANKS), "grid refilled from the network (top)");
            helper.assertTrue(menu.getSlot(4).getItem().is(Items.OAK_PLANKS), "grid refilled from the network (bottom)");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 8, "two planks pulled from the network");

            menu.clickMenuButton(player, 0);
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "grid cleared");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS))) == 10, "planks returned");
            helper.succeed();
        });
    }

    public static void patternEncoder(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        // the encoder keeps its contents itself: the window is opened on its block entity
        BlockPos encPos = new BlockPos(2, 1, 2);
        helper.setBlock(encPos, ModBlocks.PATTERN_ENCODER.get());
        var encoder = helper.getBlockEntity(encPos, com.golemlogix.blockentity.PatternEncoderBlockEntity.class);
        PatternEncoderMenu menu = new PatternEncoderMenu(79, player.getInventory(), encoder.getItems(), encoder.data(), ContainerLevelAccess.NULL);
        menu.getContainer().setItem(0, new ItemStack(Items.OAK_PLANKS));
        menu.getContainer().setItem(3, new ItemStack(Items.OAK_PLANKS));
        menu.getContainer().setItem(PatternEncoderMenu.CARD_IN, new ItemStack(ModItems.COPPER_PUNCH_CARD.get(), 3));
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.OUT_START).is(Items.STICK), "recipe preview");
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        ItemStack encoded = menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT);
        helper.assertTrue(!encoded.isEmpty(), "card encoded");
        PatternData data = encoded.get(ModDataComponents.PATTERN.get());
        helper.assertTrue(data != null && data.crafting() && data.primaryOutput().is(Items.STICK), "pattern content");
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.CARD_IN).getCount() == 2, "one blank consumed");
        // blank cards stack up to 64, a written one is single
        helper.assertTrue(new ItemStack(ModItems.COPPER_PUNCH_CARD.get()).getMaxStackSize() == 64, "blank cards stack to 64");
        helper.assertTrue(encoded.getMaxStackSize() == 1, "an encoded card does not stack");

        // editing: a written card put into the finished card slot opens its recipe; "Stamp" overwrites it
        ItemStack card = menu.getContainer().removeItemNoUpdate(PatternEncoderMenu.CARD_OUT);
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_CLEAR);
        menu.getSlot(PatternEncoderMenu.CARD_OUT).setByPlayer(card, ItemStack.EMPTY);
        helper.assertTrue(menu.getContainer().getItem(0).is(Items.OAK_PLANKS), "the card's recipe is back in the grid");
        menu.getContainer().setItem(3, ItemStack.EMPTY);
        menu.getContainer().setItem(0, new ItemStack(Items.OAK_LOG));
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        PatternData edited = menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).get(ModDataComponents.PATTERN.get());
        helper.assertTrue(edited != null && edited.primaryOutput().is(Items.OAK_PLANKS), "the same card now makes planks");
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.CARD_IN).getCount() == 2, "no blank spent on a rewrite");
        // the window was closed: the blank cards stayed in the encoder
        menu.removed(player);
        helper.assertTrue(encoder.getItems().getItem(PatternEncoderMenu.CARD_IN).getCount() == 2, "blank cards stay in the encoder");
        menu = new PatternEncoderMenu(80, player.getInventory(), encoder.getItems(), encoder.data(), ContainerLevelAccess.NULL);

        // Shift + right-click with a written card in hand erases it
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).copy());
        player.setShiftKeyDown(true);
        player.getMainHandItem().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
        helper.assertTrue(!com.golemlogix.items.CopperPunchCardItem.hasPattern(player.getMainHandItem())
                && player.getMainHandItem().is(ModItems.COPPER_PUNCH_CARD.get()), "shift+use clears the card");

        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_CLEAR);
        menu.getContainer().setItem(PatternEncoderMenu.CARD_OUT, ItemStack.EMPTY);
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_TOGGLE);
        helper.assertTrue(!menu.isCraftingMode(), "processing mode");
        menu.getContainer().setItem(0, new ItemStack(Items.RAW_IRON, 2));
        menu.getContainer().setItem(PatternEncoderMenu.OUT_START, new ItemStack(Items.IRON_INGOT, 2));
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        PatternData p2 = menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).get(ModDataComponents.PATTERN.get());
        helper.assertTrue(p2 != null && !p2.crafting() && p2.inputs().get(0).getCount() == 2, "processing pattern keeps counts");

        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_CLEAR);
        menu.getContainer().setItem(PatternEncoderMenu.CARD_OUT, ItemStack.EMPTY);
        menu.clickMenuButton(player, PatternEncoderMenu.BUTTON_ENCODE);
        helper.assertTrue(menu.getContainer().getItem(PatternEncoderMenu.CARD_OUT).isEmpty(), "empty pattern rejected");
        helper.succeed();
    }

    public static void jobPersistence(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON, 2)), List.of(new ItemStack(Items.IRON_INGOT)))));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 8), false);
            GolemNetwork.StartResult r = n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 4);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start " + r.type());
            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            CompoundTag tag = cpu.saveWithoutMetadata(helper.getLevel().registryAccess());
            CryoCalculatorBlockEntity copy = new CryoCalculatorBlockEntity(cpu.getBlockPos(), cpu.getBlockState());
            copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
            helper.assertTrue(copy.getJob() != null, "job survives a save/load round trip");
            helper.assertTrue(copy.getJob().totalExecutions() == 4, "total " + copy.getJob().totalExecutions());
            helper.assertTrue(copy.getJob().target().equals(ItemKey.of(new ItemStack(Items.IRON_INGOT))), "target");
            helper.assertTrue(copy.getJob().inventory().equals(cpu.getJob().inventory()), "reserved inventory");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ data

    public static void dataIsComplete(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        String[] recipes = {"analog_terminal", "atlas_core", "atlas_casing", "atlas_conduit", "atlas_atlant", "channel_expander",
                "quantum_channel_expander", "basic_snow_pipe", "cable_bushing", "cooling_coprocessor",
                "copper_disk_1k", "copper_disk_4k", "copper_disk_16k", "copper_disk_64k", "copper_disk_256k",
                "copper_drive_bay", "copper_punch_card", "crafting_terminal", "cryo_calculator", "dense_frost_pipe",
                "eternal_frost_resonator", "fluid_disk_1k", "fluid_disk_4k", "fluid_disk_16k", "fluid_disk_64k",
                "fluid_disk_256k", "guide_book", "injector_export_bus", "mana_saturator", "pattern_encoder",
                "pattern_provider", "periscope_storage_bus", "portable_chronometer", "vacuum_import_bus", "chronometer_upgrade"};
        for (String r : recipes) {
            Identifier id = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, r);
            helper.assertTrue(server.getRecipeManager().byKey(com.golemlogix.system.Recipes.key(id)).isPresent(), "missing recipe " + r);
        }
        String[] blocks = {"creative_source_jar", "mana_saturator", "atlas_core", "atlas_casing", "atlas_conduit", "atlas_atlant",
                "channel_expander", "quantum_channel_expander", "cable_bushing", "basic_snow_pipe",
                "dense_frost_pipe", "copper_drive_bay", "analog_terminal", "crafting_terminal", "pattern_encoder",
                "pattern_provider", "cryo_calculator", "cooling_coprocessor", "vacuum_import_bus", "injector_export_bus",
                "periscope_storage_bus", "eternal_frost_resonator"};
        for (String b : blocks) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "blocks/" + b));
            helper.assertTrue(server.reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "missing loot table " + b);
        }
        helper.succeed();
    }

    public static void chronometerUpgrade(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = ChronometerUpgradeRecipe.INSTANCE;
        ItemStack chrono = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
        chrono.set(ModDataComponents.BOUND_CORE.get(), GlobalPos.of(level.dimension(), BlockPos.ZERO));

        CraftingInput step1 = CraftingInput.of(3, 1, List.of(chrono, new ItemStack(Items.ENDER_EYE), new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get())));
        helper.assertTrue(recipe.matches(step1, level), "tier 1 upgrade matches");
        ItemStack t1 = recipe.assemble(step1);
        helper.assertTrue(t1.get(ModDataComponents.WIRELESS_TIER.get()) == 1, "tier 1");
        helper.assertTrue(t1.get(ModDataComponents.BOUND_CORE.get()) != null, "binding survives the upgrade");

        CraftingInput skip = CraftingInput.of(2, 1, List.of(chrono, new ItemStack(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get())));
        helper.assertTrue(!recipe.matches(skip, level), "cannot skip the dimensional tier");

        CraftingInput step2 = CraftingInput.of(2, 1, List.of(t1, new ItemStack(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get())));
        helper.assertTrue(recipe.matches(step2, level), "tier 2 upgrade matches");
        helper.assertTrue(recipe.assemble(step2).get(ModDataComponents.WIRELESS_TIER.get()) == 2, "tier 2");
        helper.succeed();
    }

    // ------------------------------------------------------------- book

    private static void checkItem(GameTestHelper helper, String spec, String where) {
        String id = spec.contains("*") ? spec.substring(0, spec.indexOf('*')) : spec;
        if (id.startsWith("fluid:")) {
            Identifier fl = Identifier.tryParse(id.substring(6));
            helper.assertTrue(fl != null && net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(fl), "unknown fluid '" + id + "' in " + where);
            return;
        }
        Identifier rl = Identifier.tryParse(id);
        helper.assertTrue(rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl), "unknown item '" + id + "' in " + where);
    }

    public static void bookDataIsValid(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        java.util.Map<String, String> signatures = new java.util.HashMap<>();
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            StringBuilder sig = new StringBuilder();
            try (var in = GolemGameTests.class.getResourceAsStream("/assets/golem_logix/book/" + lang + ".json")) {
                helper.assertTrue(in != null, "book file " + lang);
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.Set<String> ids = new java.util.HashSet<>();
                for (var ce : root.getAsJsonArray("categories")) {
                    for (var ee : ce.getAsJsonObject().getAsJsonArray("entries")) {
                        String id = ee.getAsJsonObject().get("id").getAsString();
                        helper.assertTrue(ids.add(id), "duplicate entry id " + id);
                    }
                }
                for (var ce : root.getAsJsonArray("categories")) {
                    checkItem(helper, ce.getAsJsonObject().get("icon").getAsString(), "category icon");
                    for (var ee : ce.getAsJsonObject().getAsJsonArray("entries")) {
                        var e = ee.getAsJsonObject();
                        String id = e.get("id").getAsString();
                        // an entry about another mod's integration names that mod's items: they exist only when it is installed
                        String requires = e.has("requires") ? e.get("requires").getAsString() : "";
                        boolean items = requires.isEmpty() || net.neoforged.fml.ModList.get().isLoaded(requires);
                        if (items) {
                            checkItem(helper, e.get("icon").getAsString(), id);
                        }
                        if (e.has("parents")) {
                            for (var p : e.getAsJsonArray("parents")) {
                                helper.assertTrue(ids.contains(p.getAsString()), "entry " + id + " has unknown parent " + p.getAsString());
                            }
                        }
                        helper.assertTrue(e.getAsJsonArray("pages").size() > 0, "entry " + id + " has pages");
                        String secret = e.has("secret") ? e.get("secret").getAsString() : "";
                        helper.assertTrue(java.util.List.of("", "partial", "full", "mind").contains(secret), "entry " + id + ": bad secret " + secret);
                        sig.append(ce.getAsJsonObject().get("id").getAsString()).append("/").append(id).append("@").append(e.get("x")).append(",").append(e.get("y"))
                                .append(e.has("parents") ? e.get("parents").toString() : "[]").append(e.has("gold") ? "G" : "").append(secret)
                                .append(requires.isEmpty() ? "" : "^" + requires).append(":");
                        for (var pe : e.getAsJsonArray("pages")) {
                            var page = pe.getAsJsonObject();
                            String type = page.get("type").getAsString();
                            String when = page.has("when") ? page.get("when").getAsString() : "";
                            helper.assertTrue(java.util.List.of("", "wither", "dimension", "!wither", "!dimension", "both", "one").contains(when),
                                    "entry " + id + ": bad page condition " + when);
                            sig.append(type).append(page.has("recipe") ? page.get("recipe").getAsString() : "")
                                    .append(page.has("texture") ? page.get("texture").getAsString() : "")
                                    .append(page.has("layer") ? page.get("layer").getAsString() : "")
                                    .append(page.has("variant") ? page.get("variant").getAsString() : "")
                                    .append(page.has("station") ? page.get("station").getAsString() : "")
                                    .append(page.has("inputs") ? page.get("inputs").toString() : "")
                                    .append(page.has("outputs") ? page.get("outputs").toString() : "")
                                    .append(page.has("grid") ? page.get("grid").toString() + page.get("result").getAsString() : "")
                                    .append(when.isEmpty() ? "" : "?" + when).append(",");
                            if (type.equals("tasks")) {
                                helper.assertTrue(page.has("tasks") && page.getAsJsonArray("tasks").size() > 0, "entry " + id + ": tasks page without tasks");
                                for (var t : page.getAsJsonArray("tasks")) {
                                    sig.append(t.getAsJsonObject().get("id").getAsString()).append("+");
                                }
                            }
                            if (page.has("recipe") && items) {
                                Identifier rid = Identifier.parse(page.get("recipe").getAsString());
                                helper.assertTrue(server.getRecipeManager().byKey(com.golemlogix.system.Recipes.key(rid)).isPresent(), "entry " + id + ": unknown recipe " + rid);
                            }
                            if (page.has("grid")) {
                                for (var g : page.getAsJsonArray("grid")) {
                                    if (!g.getAsString().isEmpty()) {
                                        checkItem(helper, g.getAsString(), id + " grid");
                                    }
                                }
                                checkItem(helper, page.get("result").getAsString(), id + " result");
                            }
                            for (String key : new String[]{"inputs", "outputs"}) {
                                if (page.has(key) && items) {
                                    for (var s : page.getAsJsonArray(key)) {
                                        checkItem(helper, s.getAsString(), id + " " + key);
                                    }
                                }
                            }
                            for (String key : new String[]{"station", "item"}) {
                                if (page.has(key) && items) {
                                    checkItem(helper, page.get(key).getAsString(), id + " " + key);
                                }
                            }
                            if (type.equals("image")) {
                                String tex = "/assets/golem_logix/textures/gui/book/" + page.get("texture").getAsString() + ".png";
                                helper.assertTrue(GolemGameTests.class.getResource(tex) != null, "missing image " + tex);
                            }
                            if (type.equals("blueprint")) {
                                int layer = page.get("layer").getAsInt();
                                helper.assertTrue(layer >= 0 && layer <= 3, "blueprint layer");
                            }
                            helper.assertTrue(page.has("recipe") || page.has("grid") || !type.equals("crafting"), "crafting page needs a recipe or grid");
                        }
                        sig.append(";");
                    }
                }
                signatures.put(lang, sig.toString());
            }
        }
        helper.assertTrue(signatures.get("ru_ru").equals(signatures.get("en_us")), "ru and en books must have the same structure");
        helper.succeed();
    }

    // ------------------------------------------------- cabinet, bushing, saturator, terminal

    private static BlockPos[] bayCells() {
        return new BlockPos[]{BAY, BAY.east(), BAY.above(), BAY.east().above()};
    }

    public static void bayHasTenSlots(GameTestHelper helper) {
        putBay(helper, BAY);
        CopperDriveBayBlockEntity be = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        helper.assertTrue(CopperDriveBayBlockEntity.SLOTS == 10, "cabinet must have 10 slots");
        helper.assertTrue(be.getContainer().getContainerSize() == 10 && be.getDisks().size() == 10, "container size 10");
        var menu = new com.golemlogix.menu.DriveBayMenu(0, testPlayer(helper).getInventory(), be.getContainer(), be.priorityData());
        helper.assertTrue(menu.slots.stream().filter(s -> s.container == be.getContainer()).count() == 10, "menu shows 10 cassette slots");
        helper.succeed();
    }

    /** The cabinet takes 2x2 cells: parts, direction, a block entity only in the main one. */
    public static void cabinetIsTwoByTwo(GameTestHelper helper) {
        putBay(helper, BAY);
        BlockPos[] cells = bayCells();
        CopperDriveBayBlock.Part[] parts = CopperDriveBayBlock.Part.values();
        for (int i = 0; i < 4; i++) {
            BlockState s = helper.getBlockState(cells[i]);
            helper.assertTrue(s.is(ModBlocks.COPPER_DRIVE_BAY.get()), "cell " + i + " belongs to the cabinet");
            helper.assertTrue(s.getValue(CopperDriveBayBlock.PART) == parts[i], "part of cell " + i);
            helper.assertTrue(s.getValue(CopperDriveBayBlock.FACING) == Direction.SOUTH, "facing of cell " + i);
            helper.assertTrue((helper.getLevel().getBlockEntity(helper.absolutePos(cells[i])) instanceof CopperDriveBayBlockEntity) == (i == 0),
                    "only the master part has a block entity, cell " + i);
            BlockPos m = CopperDriveBayBlock.masterOf(helper.absolutePos(cells[i]), s);
            helper.assertTrue(m.equals(helper.absolutePos(BAY)), "master is found from part " + i);
        }
        helper.succeed();
    }

    /** Any of the four parts breaks: the whole cabinet disappears, the item drops once, the disks are not lost. */
    public static void cabinetBreaksAsOne(GameTestHelper helper) {
        BlockPos[] cells = bayCells();
        for (int victim = 0; victim < 4; victim++) {
            putBay(helper, BAY);
            (helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class)).getContainer().setItem(3, new ItemStack(ModItems.COPPER_DISK_1K.get()));
            helper.getLevel().destroyBlock(helper.absolutePos(cells[victim]), true);
            for (BlockPos c : cells) {
                helper.assertTrue(helper.getBlockState(c).isAir(), "cell " + c + " must vanish after breaking part " + victim);
            }
            helper.assertItemEntityCountIs(ModBlocks.COPPER_DRIVE_BAY.get().asItem(), BAY, 3.0, 1);
            helper.assertItemEntityCountIs(ModItems.COPPER_DISK_1K.get(), BAY, 3.0, 1);
            helper.killAllEntities();
        }
        helper.succeed();
    }

    /** By a player's item: placed whole where there is room, and not placed if even one cell is taken. */
    public static void cabinetPlacementByPlayer(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        player.setYRot(180f);   // looks north: the cabinet will face south
        helper.setBlock(BAY.below(), Blocks.STONE);
        helper.setBlock(BAY.east().above(), Blocks.STONE);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(BAY.below())).add(0, 0.5, 0), Direction.UP, helper.absolutePos(BAY.below()), false);
        ItemStack stack = new ItemStack(ModBlocks.COPPER_DRIVE_BAY.get().asItem());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        var blocked = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(!blocked.consumesAction(), "no room for the upper right part: the cabinet must not be placed");
        helper.assertTrue(helper.getBlockState(BAY).isAir(), "nothing was placed");

        helper.setBlock(BAY.east().above(), Blocks.AIR);
        var placed = stack.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(placed.consumesAction(), "placed when there is room");
        for (BlockPos c : bayCells()) {
            helper.assertTrue(helper.getBlockState(c).is(ModBlocks.COPPER_DRIVE_BAY.get()), "part placed at " + c);
            helper.assertTrue(helper.getBlockState(c).getValue(CopperDriveBayBlock.FACING) == Direction.SOUTH, "faces the player");
        }
        helper.succeed();
    }

    /** The cabinet is one network device (one channel), the active state is spread to all parts. */
    public static void cabinetCountsOnce(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(25, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.deviceCount() == 1, "one cabinet is one device, got " + n.deviceCount());
            helper.assertTrue(n.channelsUsed() == 1, "one channel, got " + n.channelsUsed());
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(bay)), "the master part has the channel");
            for (BlockPos c : bayCells()) {
                helper.assertTrue(helper.getBlockState(c).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE), "part " + c + " is active");
            }
            helper.succeed();
        });
    }

    /** Bus filters keep their slots (the central slot does not move to the first one on a world reload). */
    public static void busFilterKeepsSlots(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.VACUUM_IMPORT_BUS.get());
        var registries = helper.getLevel().registryAccess();
        BusBlockEntity be = helper.getBlockEntity(at, BusBlockEntity.class);
        be.getFilters().setItem(4, new ItemStack(Items.DIAMOND));
        be.getFilters().setItem(8, new ItemStack(Items.GOLD_INGOT));
        CompoundTag saved = be.saveWithFullMetadata(registries);
        BusBlockEntity fresh = new BusBlockEntity(helper.absolutePos(at), helper.getBlockState(at));
        fresh.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(fresh.getFilters().getItem(4).is(Items.DIAMOND), "centre filter slot is kept");
        helper.assertTrue(fresh.getFilters().getItem(8).is(Items.GOLD_INGOT), "last filter slot is kept");
        helper.assertTrue(fresh.getFilters().getItem(0).isEmpty(), "first slot stays empty");
        helper.succeed();
    }

    /** Every block that needs a tool can be mined with a pickaxe (otherwise it would never drop), and every block has a drop and a model. */
    public static void everyBlockIsHarvestable(GameTestHelper helper) {
        for (var entry : ModBlocks.BLOCKS.getEntries()) {
            Block block = entry.get();
            BlockState state = block.defaultBlockState();
            String id = entry.getId().getPath();
            if (state.requiresCorrectToolForDrops()) {
                helper.assertTrue(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), id + " needs a tool but is not in mineable/pickaxe");
            }
            helper.assertTrue(GolemGameTests.class.getResource("/assets/golem_logix/blockstates/" + id + ".json") != null, id + " has a blockstate");
            if (block instanceof com.golemlogix.blocks.SaturatorPartBlock || block instanceof com.golemlogix.blocks.MainframePartBlock
                    || block instanceof com.golemlogix.blocks.CatalogPartBlock) {
                continue; // a part block of an assembled multiblock: it has no item, drop or item model
            }
            helper.assertTrue(block.asItem() != Items.AIR, id + " has an item");
            var lootId = block.getLootTable();
            helper.assertTrue(lootId.isPresent(), id + " has a loot table");
            helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(lootId.get()) != LootTable.EMPTY, id + " loot table exists in data");
            helper.assertTrue(GolemGameTests.class.getResource("/assets/golem_logix/blockstates/" + id + ".json") != null, id + " has a blockstate");
            helper.assertTrue(GolemGameTests.class.getResource("/assets/golem_logix/models/item/" + id + ".json") != null, id + " has an item model");
        }
        helper.succeed();
    }

    /** A pipe joins a pipe of the same thickness with "glass", a pipe of another thickness and devices with a brass sleeve. */
    public static void pipeLinksByKind(GameTestHelper helper) {
        BlockPos p = new BlockPos(2, 1, 2);
        put(helper, p, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, p.east(), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, p.west(), ModBlocks.DENSE_FROST_PIPE.get());
        put(helper, p.north(), ModBlocks.ANALOG_TERMINAL.get());
        BlockState s = helper.getBlockState(p);
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.EAST) == com.golemlogix.blocks.SnowPipeBlock.Link.PIPE, "same kind: plain glass");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.WEST) == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "thin into thick: sleeve");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(p.west()), Direction.EAST) == com.golemlogix.blocks.SnowPipeBlock.Link.REDUCER,
                "thick towards thin: reducer washer closes the gap");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.NORTH) == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "device: sleeve");
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(s, Direction.SOUTH) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE, "nothing: end");
        put(helper, new BlockPos(4, 1, 6), ModBlocks.DENSE_FROST_PIPE.get());
        putBay(helper, new BlockPos(5, 1, 6));
        helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(new BlockPos(4, 1, 6)), Direction.EAST)
                == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "a pipe meets the cabinet with a sleeve");
        helper.succeed();
    }

    /** Disks in the cabinet do not "squeeze" to the front on save; the old format without slot numbers is read too. */
    public static void bayKeepsSlotPositions(GameTestHelper helper) {
        putBay(helper, BAY);
        var registries = helper.getLevel().registryAccess();
        CopperDriveBayBlockEntity be = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        be.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        be.getContainer().setItem(4, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        be.getContainer().setItem(9, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        CompoundTag saved = be.saveWithFullMetadata(registries);
        CopperDriveBayBlockEntity fresh = new CopperDriveBayBlockEntity(helper.absolutePos(BAY), helper.getBlockState(BAY));
        fresh.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
        for (int i = 0; i < 10; i++) {
            net.minecraft.world.item.Item expect = i == 0 ? ModItems.COPPER_DISK_1K.get() : i == 4 ? ModItems.FLUID_DISK_4K.get()
                    : i == 9 ? ModItems.COPPER_DISK_256K.get() : Items.AIR;
            helper.assertTrue(fresh.getDisks().get(i).getItem() == expect, "slot " + i + " keeps its disk, found " + fresh.getDisks().get(i));
        }
        // old format: entries without Slot go in a row from the first slot
        net.minecraft.nbt.ListTag old = new net.minecraft.nbt.ListTag();
        old.add(com.golemlogix.system.NbtBridge.saveItem(new ItemStack(ModItems.COPPER_DISK_4K.get()), registries));
        old.add(com.golemlogix.system.NbtBridge.saveItem(new ItemStack(ModItems.COPPER_DISK_16K.get()), registries));
        CompoundTag legacy = new CompoundTag();
        legacy.put("Disks", old);
        CopperDriveBayBlockEntity oldBe = new CopperDriveBayBlockEntity(helper.absolutePos(BAY), helper.getBlockState(BAY));
        oldBe.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, legacy));
        helper.assertTrue(oldBe.getDisks().get(0).is(ModItems.COPPER_DISK_4K.get()) && oldBe.getDisks().get(1).is(ModItems.COPPER_DISK_16K.get()),
                "legacy list is read in order");
        helper.succeed();
    }

    public static void bushingReportsLoad(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos bushing = new BlockPos(4, 1, 6);
        put(helper, TERM, ModBlocks.ANALOG_TERMINAL.get());
        put(helper, new BlockPos(3, 2, 6), ModBlocks.CRAFTING_TERMINAL.get());
        helper.runAfterDelay(25, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.loadAt(helper.absolutePos(bushing)) == 2, "bushing line carries both terminals, got " + n.loadAt(helper.absolutePos(bushing)));
            helper.assertTrue(n.loadAt(helper.absolutePos(new BlockPos(6, 1, 4))) == 0, "other lines are idle");
            // the terminals turned on together with the network (screen animation)
            helper.assertTrue(helper.getBlockState(TERM).getValue(com.golemlogix.blocks.GolemProperties.ACTIVE), "terminal must become active");
            helper.assertTrue(helper.getBlockEntity(TERM, net.minecraft.world.level.block.entity.BlockEntity.class) instanceof com.golemlogix.blockentity.TerminalBlockEntity, "terminal has a block entity");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ Mana Saturator (3x3x2 multiblock)

    /** Places all machine blocks around the owner (controllerRel), front towards facing; the owner with the right FACING. */
    private static void buildSaturator(GameTestHelper helper, BlockPos controllerRel, Direction facing) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(controllerRel);
        for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
            BlockState s = com.golemlogix.system.SaturatorStructure.required(i).defaultBlockState();
            if (i == com.golemlogix.system.SaturatorStructure.CONTROLLER) {
                s = s.setValue(com.golemlogix.blocks.ManaSaturatorBlock.FACING, facing);
            }
            level.setBlock(com.golemlogix.system.SaturatorStructure.cellPos(at, facing, i), s, 3);
        }
    }

    private static BlockPos satCell(GameTestHelper helper, BlockPos controllerRel, Direction facing, int index) {
        return com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(controllerRel), facing, index);
    }

    public static void saturatorFillsFromNetwork(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // the machine faces north: the "central box" (9,1,9), the network pipe comes to its front (9,1,8) from the corner bushing
        BlockPos near = new BlockPos(9, 3, 9);
        for (int x = 7; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(9, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(9, 1, 8), ModBlocks.BASIC_SNOW_PIPE.get());
        // branches to the other sockets: left (east), right (west), back (south), bottom; and a pipe on the roof, which has no socket
        BlockPos leftStub = new BlockPos(11, 1, 10), rightStub = new BlockPos(7, 1, 10), backStub = new BlockPos(9, 1, 11),
                bottomStub = new BlockPos(9, 0, 9), roofStub = new BlockPos(10, 4, 9);
        for (BlockPos stub : new BlockPos[]{leftStub, rightStub, backStub, bottomStub, roofStub}) {
            put(helper, stub, ModBlocks.BASIC_SNOW_PIPE.get());
        }
        buildSaturator(helper, near, Direction.NORTH);
        BlockPos far = new BlockPos(2, 3, 9);     // on its own, no sources
        buildSaturator(helper, far, Direction.NORTH);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(helper.getBlockState(near).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "networked saturator forms by itself");
            var a = helper.getBlockEntity(near, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            var b = helper.getBlockEntity(far, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(a.getSource() >= 300, "networked saturator must fill from the network, got " + a.getSource());
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(b.getSource() == 0, "isolated saturator stays empty, got " + b.getSource());
            } else {
                helper.assertTrue(b.getSource() > 0, "without Ars Nouveau the saturator fills for free, got " + b.getSource());
            }
            // the pipe sees only the "central box": the port has a sleeve, the wing next to it does not
            var pipe = helper.getBlockState(new BlockPos(9, 1, 8));
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(pipe, Direction.SOUTH) == com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE, "pipe attaches to the port");
            put(helper, new BlockPos(11, 1, 9), ModBlocks.BASIC_SNOW_PIPE.get());
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(new BlockPos(11, 1, 9)), Direction.WEST) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE,
                    "a pipe beside a wing cell does not attach");
            var device = com.golemlogix.blocks.SnowPipeBlock.Link.DEVICE;
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(leftStub), Direction.WEST) == device, "left side port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(rightStub), Direction.EAST) == device, "right side port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(backStub), Direction.NORTH) == device, "back port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(bottomStub), Direction.UP) == device, "bottom port");
            helper.assertTrue(com.golemlogix.blocks.SnowPipeBlock.link(helper.getBlockState(roofStub), Direction.DOWN) == com.golemlogix.blocks.SnowPipeBlock.Link.NONE,
                    "no port on the roof");
            helper.succeed();
        });
    }

    /**
     * Autocrafting plates on the Mana Saturator through a Stamping Gateway at its port: several executions in a row (not only the
     * first) are fed, forged, handed back by the auto-output into the gateway's return slots and delivered.
     */
    public static void saturatorAutocraftsPlates(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        for (int x = 7; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(9, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos prov = new BlockPos(9, 1, 8);
        put(helper, prov, ModBlocks.PATTERN_PROVIDER.get());
        BlockPos near = new BlockPos(9, 3, 9);
        buildSaturator(helper, near, Direction.NORTH);
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);
        ItemKey core = ItemKey.of(new ItemStack(ModItems.FLATTENED_IRON_CORE.get()));
        ItemKey matrix = ItemKey.of(new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get()));
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.IRON_BLOCK)),
                List.of(new ItemStack(ModItems.FLATTENED_IRON_CORE.get())))));
        provider.getContainer().setItem(1, card(new PatternData(false, List.of(new ItemStack(Items.COPPER_BLOCK)),
                List.of(new ItemStack(ModItems.FLATTENED_COPPER_MATRIX.get(), 2)))));
        // the gateway waits for each result before the next portion (the mode most bases use with one machine)
        provider.setBlocking(true);
        helper.runAfterDelay(100, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_BLOCK, 4), false);
            GolemNetwork.StartResult r = n.startCrafting(core, 4);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start " + r.type());
        });
        helper.runAtTickTime(1500, () -> {
            GolemNetwork n = net(helper, bay);
            var cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            var sat = helper.getBlockEntity(near, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            var job = cpu.getJob();
            String state = "cores " + n.itemCountOf(core) + ", job " + (job == null ? "none" : job.status() + " awaiting " + job.awaiting()
                    + " inventory " + job.inventory()) + ", returns " + provider.getReturns().getItem(0) + ", decks " + sat.inputDeck() + " / "
                    + sat.getHeldItem() + " / " + sat.outputDeck();
            helper.assertTrue(n.itemCountOf(core) == 8 && cpu.isIdle(), "four blocks pressed one after another, two plates each: " + state);
            // a second order after the first one is done: another plate on the same gateway (a second pattern)
            n.insertItem(new ItemStack(Items.COPPER_BLOCK, 2), false);
            n.insertItem(new ItemStack(Items.IRON_BLOCK, 1), false);
            GolemNetwork.StartResult r = n.startCrafting(matrix, 4);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "second order starts: " + r.type());
        });
        helper.runAtTickTime(2600, () -> {
            GolemNetwork n = net(helper, bay);
            var cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            var job = cpu.getJob();
            String state = "matrices " + n.itemCountOf(matrix) + ", job " + (job == null ? "none" : job.status() + " awaiting " + job.awaiting()
                    + " inventory " + job.inventory()) + ", returns " + provider.getReturns().getItem(0);
            helper.assertTrue(n.itemCountOf(matrix) == 4 && cpu.isIdle(), "the second pattern works after the first: " + state);
            // and the first pattern again
            GolemNetwork.StartResult r = n.startCrafting(core, 1);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "third order starts: " + r.type());
        });
        helper.runAtTickTime(3300, () -> {
            GolemNetwork n = net(helper, bay);
            var cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            var job = cpu.getJob();
            String state = "cores " + n.itemCountOf(core) + ", job " + (job == null ? "none" : job.status() + " awaiting " + job.awaiting()
                    + " inventory " + job.inventory()) + ", returns " + provider.getReturns().getItem(0);
            helper.assertTrue(n.itemCountOf(core) == 10 && cpu.isIdle(), "the first pattern again: " + state);
            helper.succeed();
        });
    }

    /**
     * A recipe tree through the Saturator: a Compressed Co-Processor needs a Cooling Co-Processor (crafted from two kinds of plates
     * the Saturator makes first) pressed in the Saturator again - three machine patterns and a crafting one on one gateway.
     */
    public static void saturatorPlateTree(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        for (int x = 7; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(9, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos prov = new BlockPos(9, 1, 8);
        put(helper, prov, ModBlocks.PATTERN_PROVIDER.get());
        BlockPos near = new BlockPos(9, 3, 9);
        buildSaturator(helper, near, Direction.NORTH);
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);
        ItemStack core = new ItemStack(ModItems.FLATTENED_IRON_CORE.get()), sub = new ItemStack(ModItems.FLATTENED_CRYO_SUBSTRATE.get());
        ItemStack cooler = new ItemStack(ModBlocks.COOLING_COPROCESSOR.get()), compressed = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.IRON_BLOCK)), List.of(core.copyWithCount(2)))));
        provider.getContainer().setItem(1, card(new PatternData(false, List.of(new ItemStack(Items.PACKED_ICE)), List.of(sub.copyWithCount(2)))));
        provider.getContainer().setItem(2, card(new PatternData(false, List.of(cooler.copy()), List.of(compressed.copy()))));
        provider.getContainer().setItem(3, card(craftingPattern(cooler.copy(), ItemStack.EMPTY, sub.copy(), ItemStack.EMPTY,
                new ItemStack(Items.COPPER_INGOT), core.copy(), new ItemStack(Items.COPPER_INGOT), ItemStack.EMPTY, sub.copy(), ItemStack.EMPTY)));
        helper.runAfterDelay(100, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_BLOCK), false);
            n.insertItem(new ItemStack(Items.PACKED_ICE), false);
            n.insertItem(new ItemStack(Items.COPPER_INGOT, 2), false);
            GolemNetwork.StartResult r = n.startCrafting(ItemKey.of(compressed), 1);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start " + r.type());
        });
        helper.runAtTickTime(1900, () -> {
            GolemNetwork n = net(helper, bay);
            var cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            var sat = helper.getBlockEntity(near, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            var job = cpu.getJob();
            String state = "compressed " + n.itemCountOf(ItemKey.of(compressed)) + ", job " + (job == null ? "none" : job.status() + " awaiting "
                    + job.awaiting() + " inventory " + job.inventory()) + ", returns " + provider.getReturns().getItem(0) + ", decks "
                    + sat.inputDeck() + " / " + sat.getHeldItem() + " / " + sat.outputDeck();
            helper.assertTrue(n.itemCountOf(ItemKey.of(compressed)) == 1 && cpu.isIdle(), "the tree is crafted to the end: " + state);
            helper.succeed();
        });
    }

    public static void saturatorFormsAndRestores(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.WEST);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(ctl);
        // before assembly everything stands as ordinary blocks
        helper.assertTrue(com.golemlogix.system.SaturatorStructure.check(level, abs, Direction.WEST).valid(), "complete machine passes the check");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "machine forms");
            for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
                if (i == com.golemlogix.system.SaturatorStructure.CONTROLLER) {
                    continue;
                }
                BlockPos p = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, i);
                BlockState s = level.getBlockState(p);
                helper.assertTrue(s.getBlock() instanceof com.golemlogix.blocks.SaturatorPartBlock, "cell " + i + " became a part");
                helper.assertTrue(s.getValue(com.golemlogix.blocks.SaturatorPartBlock.CELL) == i, "cell index " + i);
                var be = (com.golemlogix.blockentity.SaturatorPartBlockEntity) level.getBlockEntity(p);
                helper.assertTrue(be.getOriginal().is(com.golemlogix.system.SaturatorStructure.required(i)), "remembers the original block of cell " + i);
            }
            // mining a part gives the former block, not the part
            BlockPos rodPos = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, com.golemlogix.system.SaturatorStructure.ROD);
            var drops = Block.getDrops(level.getBlockState(rodPos), level, rodPos, level.getBlockEntity(rodPos));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(Items.END_ROD), "a part drops its original block");
            // any part broken -> the rest went back to ordinary blocks, the owner is "unassembled" again
            BlockPos wing = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, com.golemlogix.system.SaturatorStructure.index(0, 1, 1));
            level.destroyBlock(wing, false);
            helper.assertTrue(!helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "machine unforms");
            for (int i = 0; i < com.golemlogix.system.SaturatorStructure.CELLS; i++) {
                BlockPos p = com.golemlogix.system.SaturatorStructure.cellPos(abs, Direction.WEST, i);
                if (p.equals(wing)) {
                    continue;
                }
                helper.assertTrue(level.getBlockState(p).is(com.golemlogix.system.SaturatorStructure.required(i)), "cell " + i + " is an ordinary block again");
            }
            helper.assertTrue(level.getBlockState(wing).isAir(), "the broken cell stays empty");
            helper.succeed();
        });
    }

    public static void saturatorCrafts(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        // a Source jar next to the bottom left corner of the machine
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.runAfterDelay(30, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "forms");
            // right-click on any part (a side casing) with a block in hand puts the block on the anvil
            var player = testPlayer(helper);
            ItemStack iron = new ItemStack(Items.IRON_BLOCK, 2);
            BlockPos partPos = com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(ctl), Direction.NORTH, com.golemlogix.system.SaturatorStructure.index(0, 0, 0));
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(partPos), Direction.NORTH, partPos, false);
            var result = helper.getLevel().getBlockState(partPos).useItemOn(iron, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result.consumesAction(), "a part forwards the click to the head");
            helper.assertTrue(sat.inputDeck().is(Items.IRON_BLOCK), "the block lies on the left deck, waiting for the carrier");
            helper.assertTrue(iron.getCount() == 1, "one block was taken from the hand");
        });
        helper.runAfterDelay(380, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.outputDeck().is(ModItems.FLATTENED_IRON_CORE.get()) && sat.outputDeck().getCount() == 2,
                    "iron block becomes two flattened cores on the right deck, got " + sat.outputDeck());
            helper.succeed();
        });
    }
    /** A real Ars Nouveau Source jar powers the network and gets drained (only with -PtestMods; without Ars Nouveau there is nothing to check). */
    public static void arsSourceJarPowersNetwork(GameTestHelper helper) {
        Identifier jarId = Identifier.fromNamespaceAndPath("ars_nouveau", "source_jar");
        if (!com.golemlogix.system.SourceHelper.arsNouveauLoaded() || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(jarId)) {
            helper.succeed();
            return;
        }
        buildArray(helper, 0, 0);
        helper.setBlock(JAR, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(jarId));
        var supplier = com.golemlogix.system.SourceHelper.asSupplier(helper.getBlockEntity(JAR, net.minecraft.world.level.block.entity.BlockEntity.class));
        helper.assertTrue(supplier != null, "an Ars Nouveau jar is a Source supplier");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!net(helper, ORIGIN).isOnline(), "an empty jar does not power the network");
            // fill the jar the same way the Creative Source Jar does
            helper.setBlock(JAR.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
            helper.runAfterDelay(30, () -> {
                helper.setBlock(JAR.below(), Blocks.STONE);
                int full = supplier.available();
                helper.assertTrue(full > 1000, "the creative jar fills the Ars jar, got " + full);
                helper.runAfterDelay(50, () -> {
                    helper.assertTrue(net(helper, ORIGIN).isOnline(), "a filled Ars jar powers the network");
                    helper.assertTrue(supplier.available() < full, "the core drains the jar: " + supplier.available() + " < " + full);
                    helper.succeed();
                });
            });
        });
    }

    /**
     * With Ars Nouveau the assembled Saturator runs the Imbuement Chamber's Source Gem recipes: twice as fast as the chamber
     * (100 -> 50 ticks for a gem, 200 -> 100 for a block) for a third more Source (500 -> 665, 2000 -> 2660). Without Ars there are none.
     */
    public static void saturatorMakesSourceGems(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack shard = new ItemStack(Items.AMETHYST_SHARD);
        var gems = com.golemlogix.integration.ars.ArsImbuement.all(level);
        if (!net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(gems.isEmpty() && !com.golemlogix.blockentity.ManaSaturatorBlockEntity.isInput(level, shard),
                    "no Source Gem rituals without Ars Nouveau");
            helper.succeed();
            return;
        }
        var gemEntry = com.golemlogix.integration.ars.ArsImbuement.find(level, shard);
        var blockEntry = com.golemlogix.integration.ars.ArsImbuement.find(level, new ItemStack(Items.AMETHYST_BLOCK));
        helper.assertTrue(gemEntry != null && gemEntry.chamberSource() == 500 && gemEntry.source() == 665 && gemEntry.chamberTicks() == 100
                && gemEntry.ticks() == 50, "amethyst: 665 Source in 50 ticks (chamber 500 in 100), got " + gemEntry);
        helper.assertTrue(blockEntry != null && blockEntry.source() == 2660 && blockEntry.chamberTicks() == 200 && blockEntry.ticks() == 100,
                "amethyst block: 2660 Source in 100 ticks (chamber 2000 in 200), got " + blockEntry);
        helper.assertTrue(com.golemlogix.integration.ars.ArsImbuement.find(level, new ItemStack(Items.LAPIS_LAZULI)) != null, "lapis too");
        helper.assertTrue(com.golemlogix.blockentity.ManaSaturatorBlockEntity.ritualWork(level, shard) == 500
                && com.golemlogix.blockentity.ManaSaturatorBlockEntity.ritualWork(level, new ItemStack(Items.IRON_BLOCK)) == 1000,
                "a gem ritual is half as long as a plate");
        helper.assertTrue(com.golemlogix.blockentity.ManaSaturatorBlockEntity.requiredSource(level, shard) == 665, "a gem costs 665 Source");
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        BlockPos side = com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(ctl), Direction.NORTH,
                com.golemlogix.system.SaturatorStructure.index(2, 1, 1));
        helper.runAfterDelay(30, () -> {
            var h = com.golemlogix.system.LegacyResources.items(level, side, null);
            helper.assertTrue(h.insertItem(0, new ItemStack(Items.AMETHYST_SHARD, 3), false).isEmpty(), "three shards queue on the left deck");
        });
        helper.runAfterDelay(330, () -> {
            var h = com.golemlogix.system.LegacyResources.items(level, side, null);
            ItemStack out = h.extractItem(1, 64, false);
            helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(out.getItem()).toString().equals("ars_nouveau:source_gem")
                            && out.getCount() == 3,
                    "three Source Gems come out, got " + out);
            helper.succeed();
        });
    }

    /** Automation through any part of the saturator: only a blank goes in, only a finished result comes out. */
    public static void saturatorAutomation(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        BlockPos side = com.golemlogix.system.SaturatorStructure.cellPos(helper.absolutePos(ctl), Direction.NORTH,
                com.golemlogix.system.SaturatorStructure.index(2, 1, 1));
        helper.runAfterDelay(30, () -> {
            var h = com.golemlogix.system.LegacyResources.items(helper.getLevel(), side, null);
            helper.assertTrue(h != null, "a saturator part exposes the anvil to automation");
            helper.assertTrue(h.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "dirt is rejected");
            helper.assertTrue(h.insertItem(1, new ItemStack(Items.IRON_BLOCK), false).getCount() == 1, "the output deck takes nothing");
            helper.assertTrue(h.insertItem(0, new ItemStack(Items.IRON_BLOCK, 3), false).isEmpty(), "three iron blocks queue on the left deck");
            helper.assertTrue(h.extractItem(0, 64, false).isEmpty(), "the blank cannot be pulled back out");
        });
        helper.runAfterDelay(400, () -> {
            var h = com.golemlogix.system.LegacyResources.items(helper.getLevel(), side, null);
            ItemStack out = h.extractItem(1, 64, false);
            // conveyor: the bringer brings the next blank while the smith forges, so all three are done in that time
            helper.assertTrue(out.is(ModItems.FLATTENED_IRON_CORE.get()) && out.getCount() == 6, "six flattened cores come out, got " + out);
            helper.assertTrue(h.getStackInSlot(0).isEmpty(), "the queue is used up");
            helper.succeed();
        });
    }

    // ------------------------------------------------------- anvil ritual

    /** A golem without AI with the given health and an anvil four blocks above it: it falls to the ground through the golem. */
    @SuppressWarnings("unchecked")
    private static net.minecraft.world.entity.Mob ritual(GameTestHelper helper, net.minecraft.world.entity.EntityType<?> type, BlockPos at, float health) {
        var golem = helper.spawnWithNoFreeWill((net.minecraft.world.entity.EntityType<net.minecraft.world.entity.Mob>) type, at);
        if (health > 0) {
            golem.setHealth(health);
        }
        helper.setBlock(at.above(4), Blocks.ANVIL);
        return golem;
    }

    private static void assertCrushed(GameTestHelper helper, net.minecraft.world.entity.Mob golem, BlockPos at, net.minecraft.world.item.Item plate) {
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(plate, at, 2.0);
            helper.assertTrue(golem.isRemoved(), "the golem is flattened (removed)");
        });
    }

    public static void anvilRitualIronGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        assertCrushed(helper, ritual(helper, net.minecraft.world.entity.EntityType.IRON_GOLEM, at, 3f), at, ModItems.FLATTENED_IRON_CORE.get());
    }

    public static void anvilRitualSnowGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        assertCrushed(helper, ritual(helper, net.minecraft.world.entity.EntityType.SNOW_GOLEM, at, 3f), at, ModItems.FLATTENED_CRYO_SUBSTRATE.get());
    }

    public static void anvilRitualCopperGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        assertCrushed(helper, ritual(helper, net.minecraft.world.entity.EntityType.COPPER_GOLEM, at, 3f), at, ModItems.FLATTENED_COPPER_MATRIX.get());
    }

    /** The rituals are data (recipes golem_logix:saturator and golem_logix:anvil_crushing), so modpacks can change and add them. */
    public static void ritualsComeFromData(GameTestHelper helper) {
        var level = helper.getLevel();
        var iron = com.golemlogix.recipe.SaturatorRecipe.find(level, new ItemStack(Items.IRON_BLOCK));
        helper.assertTrue(iron != null && iron.result().is(ModItems.FLATTENED_IRON_CORE.get()) && iron.result().getCount() == 2
                && iron.handResult().getCount() == 1 && iron.source() == 1000 && iron.ticks() == 100, "iron block: 2 cores, 1 by hand");
        var cut = com.golemlogix.recipe.SaturatorRecipe.find(level, new ItemStack(Items.CUT_COPPER));
        helper.assertTrue(cut != null && cut.result().is(ModItems.FLATTENED_COPPER_MATRIX.get()), "cut copper makes copper matrices");
        var plasma = com.golemlogix.recipe.SaturatorRecipe.find(level, new ItemStack(ModItems.IMPLODED_WITHER_PLASMA.get()));
        helper.assertTrue(plasma != null && plasma.secret() && plasma.source() == 2500 && plasma.handResult().isEmpty(),
                "the Occult Matrix: secret, 2500 Source, machine only");
        helper.assertTrue(com.golemlogix.recipe.SaturatorRecipe.find(level, new ItemStack(Items.DIRT)) == null, "dirt is no blank");
        var manager = level.recipeAccess();
        var type = com.golemlogix.init.ModRecipes.ANVIL_CRUSHING_TYPE.get();
        var ironGolem = manager.getRecipeFor(type, new com.golemlogix.recipe.AnvilCrushingRecipe.Golem(net.minecraft.world.entity.EntityType.IRON_GOLEM), level);
        helper.assertTrue(ironGolem.isPresent() && ironGolem.get().value().maxHealth() == 4f
                && ironGolem.get().value().result().is(ModItems.FLATTENED_IRON_CORE.get()), "the iron golem ritual");
        var snow = manager.getRecipeFor(type, new com.golemlogix.recipe.AnvilCrushingRecipe.Golem(net.minecraft.world.entity.EntityType.SNOW_GOLEM), level);
        helper.assertTrue(snow.isPresent() && snow.get().value().battered(3f) && !snow.get().value().battered(4f), "a snow golem below full health");
        helper.assertTrue(manager.getRecipeFor(type, new com.golemlogix.recipe.AnvilCrushingRecipe.Golem(net.minecraft.world.entity.EntityType.COW), level).isEmpty(),
                "no ritual for a cow");
        var copper = manager.recipeMap().byType(type).stream().map(h -> h.value())
                .filter(r -> r.result().is(ModItems.FLATTENED_COPPER_MATRIX.get())).findFirst();
        helper.assertTrue(copper.isPresent() && copper.get().entityIdContains().contains("copper_golem"),
                "the copper ritual takes any mod's copper golem (tag golem_logix:copper_golems or the id)");
        helper.succeed();
    }

    /**
     * The hotkey's Chronometer: the main hand, the offhand, the Curios slot (with Curios), then the inventory; the recipes of this
     * pass load (the hammer with an iron plate, and with the mods: Create's assembly line, Ars Nouveau's Blue Ice imbuement).
     */
    public static void chronometerFoundAnywhere(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        var inv = player.getInventory();
        inv.clearContent();
        helper.assertTrue(com.golemlogix.items.Chronometers.find(player) == null, "no Chronometer, nothing found");
        ItemStack deep = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
        inv.setItem(20, deep);
        var found = com.golemlogix.items.Chronometers.find(player);
        helper.assertTrue(found != null && found.slot() == 20 && found.stack() == deep, "found deep in the inventory");
        ItemStack off = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, off);
        found = com.golemlogix.items.Chronometers.find(player);
        helper.assertTrue(found != null && found.slot() == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, "the offhand comes first");
        if (com.golemlogix.items.Chronometers.CURIOS) {
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            ItemStack worn = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
            helper.assertTrue(com.golemlogix.integration.curios.CuriosCompat.wear(player, worn),
                    "players have a Chronometer slot: " + com.golemlogix.integration.curios.CuriosCompat.describe(player));
            found = com.golemlogix.items.Chronometers.find(player);
            helper.assertTrue(found != null && found.slot() == com.golemlogix.items.Chronometers.CURIO, "the Curios slot before the inventory");
            helper.assertTrue(com.golemlogix.items.Chronometers.carried(player).size() == 2, "both Chronometers are carried");
        }
        inv.clearContent();

        var level = helper.getLevel();
        var grid = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.COPPER_INGOT),
                new ItemStack(Items.PACKED_ICE), new ItemStack(ModItems.FLATTENED_IRON_CORE.get()), new ItemStack(Items.PACKED_ICE),
                ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY));
        var hammer = level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
        helper.assertTrue(hammer.isPresent() && hammer.get().value().assemble(grid).is(ModItems.FORGE_HAMMER.get()),
                "the hammer's head sits on an iron plate");
        var manager = level.recipeAccess();
        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            helper.assertTrue(manager.byKey(com.golemlogix.system.Recipes.key(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "create/sequenced_compressed_coprocessor"))).isPresent(),
                    "Create's Co-Processor assembly line loads");
        }
        if (net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
            helper.assertTrue(manager.byKey(com.golemlogix.system.Recipes.key(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "ars/imbuement_cryo_substrate"))).isPresent(),
                    "the Blue Ice imbuement loads");
        }
        helper.succeed();
    }

    public static void anvilRitualNeedsWoundedGolem(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        var golem = ritual(helper, net.minecraft.world.entity.EntityType.IRON_GOLEM, at, 0f);
        helper.runAfterDelay(40, () -> {
            helper.assertItemEntityNotPresent(ModItems.FLATTENED_IRON_CORE.get(), at, 3.0);
            helper.assertTrue(golem.isAlive() && golem.getHealth() < golem.getMaxHealth(), "a healthy golem only takes the hit");
            helper.succeed();
        });
    }

    // ------------------------------------------------- periscope, fluids, priorities

    /** Test "tank": Magnetite has no fluid capacity, so we provide it through a capability bound to the tank position. */
    @net.neoforged.fml.common.EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
    public static final class TestCaps {
        static final java.util.Map<BlockPos, net.neoforged.neoforge.fluids.capability.templates.FluidTank> TANKS = new java.util.HashMap<>();

        static net.neoforged.neoforge.fluids.capability.templates.FluidTank tank(BlockPos abs) {
            return TANKS.computeIfAbsent(abs.immutable(), p -> new net.neoforged.neoforge.fluids.capability.templates.FluidTank(10000));
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void register(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
            event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                    (level, pos, state, be, side) -> resources(tank(pos)), Blocks.LODESTONE);
            // a "machine" with items and fluids for fluid patterns: a barrel gets a tank too
            event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                    (level, pos, state, be, side) -> resources(tank(pos)), Blocks.BARREL);
        }

        static net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> resources(
                net.neoforged.neoforge.fluids.capability.templates.FluidTank tank) {
            return com.golemlogix.system.LegacyResources.offerFluids(tank, () -> tank.getFluid().copy(), tank::setFluid);
        }
    }

    // ------------------------------------------------- Liquid Cryo-Source, fluid patterns, golem minds

    private static FluidStack cryo(int mb) {
        return com.golemlogix.system.CryoSource.stack(mb);
    }

    /** The network makes Liquid Cryo-Source from Source when it is pumped out; it is never stored, listed or put into a bucket. */
    public static void cryoSourceFromNetwork(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        (helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class)).getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.isOnline() && n.cryoSourceAvailable() >= 1000, "a powered network can make cryo-source");
            FluidStack got = n.extractFluid(cryo(1), 750, false);
            helper.assertTrue(com.golemlogix.system.CryoSource.is(got) && got.getAmount() == 750, "750 mB pumped out, got " + got.getAmount());
            helper.assertTrue(n.insertFluid(cryo(500), false) == 0, "cryo-source is never stored");
            helper.assertTrue(n.listFluids().stream().noneMatch(com.golemlogix.system.CryoSource::is), "and never listed");
            helper.assertTrue(net.neoforged.neoforge.fluids.FluidUtil.getFilledBucket(cryo(1000)).isEmpty(), "it has no bucket");
            helper.succeed();
        });
    }

    /** Fluid ghosts in a pattern count in mB under one fluid key. */
    public static void fluidPatternTotals(GameTestHelper helper) {
        PatternData p = new PatternData(false, List.of(new ItemStack(Items.COPPER_BLOCK),
                com.golemlogix.items.FluidGhostItem.of(cryo(250)), com.golemlogix.items.FluidGhostItem.of(cryo(100))),
                List.of(new ItemStack(ModItems.TORN_COPPER_MIND.get())));
        var totals = p.ingredientTotals();
        ItemKey fluid = com.golemlogix.system.Ingredients.fluidKey(cryo(1));
        helper.assertTrue(totals.get(fluid) == 350, "350 mB of cryo-source, got " + totals.get(fluid));
        helper.assertTrue(totals.get(ItemKey.of(new ItemStack(Items.COPPER_BLOCK))) == 1, "one copper block");
        helper.assertTrue(p.hasFluidInputs(), "the pattern has a fluid input");
        helper.succeed();
    }

    /** An export bus with a cryo-source filter pours it into the tank in front of it. */
    public static void exportBusPumpsCryoSource(GameTestHelper helper) {
        baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos tankRel = BUS_OUT.north();
        helper.setBlock(tankRel, Blocks.LODESTONE);
        var tank = TestCaps.tank(helper.absolutePos(tankRel));
        tank.setFluid(FluidStack.EMPTY);
        var bus = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.BusBlockEntity.class);
        bus.getFilters().setItem(0, com.golemlogix.items.FluidGhostItem.of(cryo(250)));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(com.golemlogix.system.CryoSource.is(tank.getFluid()) && tank.getFluidAmount() >= 1000,
                    "the tank fills with cryo-source, has " + tank.getFluidAmount());
            helper.succeed();
        });
    }

    /** An interface keeps a stock of cryo-source in its tank, and pipes drain it. */
    public static void interfaceStocksCryoSource(GameTestHelper helper) {
        baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var be = helper.getBlockEntity(BUS_OUT, com.golemlogix.blockentity.InterfaceBlockEntity.class);
        be.setSlot(0, com.golemlogix.items.FluidGhostItem.of(cryo(250)), 750);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(be.isFluidSlot(0) && be.amount(0) == 750, "slot 0 keeps 750 mB");
            helper.assertTrue(com.golemlogix.system.CryoSource.is(be.fluidStock(0)) && be.fluidStock(0).getAmount() == 750,
                    "750 mB of cryo-source in stock, has " + be.fluidStock(0).getAmount());
            var fluids = com.golemlogix.system.LegacyResources.fluids(helper.getLevel(), helper.absolutePos(BUS_OUT), Direction.UP);
            FluidStack out = fluids.drain(500, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(com.golemlogix.system.CryoSource.is(out) && out.getAmount() == 500, "pipes drain it, got " + out.getAmount());
            helper.succeed();
        });
    }

    /**
     * Autocrafting with a fluid ingredient: a copper block + 250 mB of cryo-source -> a torn copper mind. The planner takes the
     * cryo-source from the network's Source, the provider pours it into the machine's tank along with the block.
     */
    public static void autocraftWithFluid(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        BlockPos machine = PROV.east();
        helper.setBlock(machine, Blocks.BARREL);
        var tank = TestCaps.tank(helper.absolutePos(machine));
        tank.setFluid(FluidStack.EMPTY);
        Container machineInv = ((Container) helper.getBlockEntity(machine, net.minecraft.world.level.block.entity.BlockEntity.class));
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.COPPER_BLOCK), com.golemlogix.items.FluidGhostItem.of(cryo(250))),
                List.of(new ItemStack(ModItems.TORN_COPPER_MIND.get())))));
        ItemKey torn = ItemKey.of(new ItemStack(ModItems.TORN_COPPER_MIND.get()));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.COPPER_BLOCK, 2), false);
            var plan = com.golemlogix.crafting.CraftingPlanner.plan(n, torn, 2);
            helper.assertTrue(plan.missing().isEmpty(), "nothing missing: " + plan.missing());
            helper.assertTrue(plan.fromStorage().get(com.golemlogix.system.Ingredients.fluidKey(cryo(1))) == 500, "500 mB of cryo-source planned");
            GolemNetwork.StartResult r = n.startCrafting(torn, 2);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
        });
        helper.runAfterDelay(60, () -> {
            int blocks = 0;
            for (int i = 0; i < machineInv.getContainerSize(); i++) {
                blocks += machineInv.getItem(i).is(Items.COPPER_BLOCK) ? machineInv.getItem(i).getCount() : 0;
            }
            helper.assertTrue(blocks == 2, "the machine gets 2 copper blocks, got " + blocks);
            helper.assertTrue(com.golemlogix.system.CryoSource.is(tank.getFluid()) && tank.getFluidAmount() == 500,
                    "and 500 mB of cryo-source, got " + tank.getFluidAmount());
            machineInv.clearContent();
            tank.setFluid(FluidStack.EMPTY);
            ItemStack rest = net(helper, bay).insertFromMachine(new ItemStack(ModItems.TORN_COPPER_MIND.get(), 2));
            helper.assertTrue(rest.isEmpty(), "the job absorbs the torn minds");
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(net(helper, bay).itemCountOf(torn) == 2, "2 torn minds delivered");
            helper.assertTrue((helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    /** A Dispel turns a battered golem into its raw mind, but only for a player who has made a plate on an anvil. */
    public static void dispelUnbindsGolemMind(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        var golem = helper.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, new BlockPos(4, 1, 4));
        golem.setNoAi(true);
        golem.setHealth(3f);
        var healthy = helper.spawn(net.minecraft.world.entity.EntityType.SNOW_GOLEM, new BlockPos(8, 1, 4));
        healthy.setNoAi(true);
        // (a fake player has no advancements: the "first plate" unlock itself is checked in the dev client)
        helper.assertTrue(com.golemlogix.secret.GolemMinds.attempt(player, golem, false) && golem.isAlive(), "unknown method: the golem stays");
        helper.assertTrue(com.golemlogix.secret.GolemMinds.attempt(player, healthy, true) && healthy.isAlive(), "a snow golem at full HP holds on");
        helper.assertTrue(com.golemlogix.secret.GolemMinds.attempt(player, golem, true) && !golem.isAlive(), "the battered iron golem is unbound");
        helper.assertTrue(!com.golemlogix.secret.GolemMinds.attempt(player, player, true), "a player is no golem");
        helper.runAfterDelay(2, () -> {
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(4, 1, 4))).inflate(2));
            helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(ModItems.RAW_IRON_MIND.get())), "a raw iron mind drops");
            helper.succeed();
        });
    }

    /**
     * With Ars Nouveau (-PtestMods): its real EffectResolveEvent for a Dispel on a golem reaches the mod and is cancelled (the fake
     * player does not know the method, so the golem stays and gets a hint).
     */
    public static void arsDispelEvent(GameTestHelper helper) throws Exception {
        if (!net.neoforged.fml.ModList.get().isLoaded("ars_nouveau")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = testPlayer(helper);
        var golem = helper.spawn(net.minecraft.world.entity.EntityType.SNOW_GOLEM, new BlockPos(4, 1, 4));
        golem.setNoAi(true);
        golem.setHealth(2f);
        Class<?> type = Class.forName("com.hollingsworth.arsnouveau.api.event.EffectResolveEvent$Pre");
        Object dispel = Class.forName("com.hollingsworth.arsnouveau.common.spell.effect.EffectDispel").getField("INSTANCE").get(null);
        var ctor = type.getConstructors()[0];
        Object[] args = new Object[ctor.getParameterCount()];
        args[0] = helper.getLevel();
        args[1] = player;
        args[2] = new net.minecraft.world.phys.EntityHitResult(golem);
        args[5] = dispel;
        var event = (net.neoforged.bus.api.Event) ctor.newInstance(args);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(((net.neoforged.bus.api.ICancellableEvent) event).isCanceled(), "the Dispel on a golem goes to the mod");
        helper.assertTrue(golem.isAlive(), "the method is unknown to this player: the golem stays");
        helper.succeed();
    }

    private static void periscope(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.PeriscopeStorageBusBlock.FACING, facing));
    }

    public static void periscopeSeesTanks(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos tankRel = new BlockPos(8, 1, 6);
        periscope(helper, new BlockPos(7, 1, 6), Direction.EAST);
        helper.setBlock(tankRel, Blocks.LODESTONE);
        var tank = TestCaps.tank(helper.absolutePos(tankRel));
        tank.setFluid(new FluidStack(Fluids.WATER, 4000));
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.isOnline(), "powered");
            long water = 0;
            for (FluidStack f : n.listFluids()) {
                if (f.getFluid() == Fluids.WATER) {
                    water += f.getAmount();
                }
            }
            helper.assertTrue(water == 4000, "periscope must list the tank's 4000 mB of water, got " + water);
            helper.assertTrue(n.insertFluid(new FluidStack(Fluids.WATER, 500), false) == 500, "500 mB must be poured into the tank");
            helper.assertTrue(tank.getFluidAmount() == 4500, "tank now holds 4500, got " + tank.getFluidAmount());
            FluidStack got = n.extractFluid(new FluidStack(Fluids.WATER, 1), 1500, false);
            helper.assertTrue(got.getAmount() == 1500 && tank.getFluidAmount() == 3000, "extracted 1500 from the tank");
            helper.succeed();
        });
    }

    public static void storagePriority(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos chestA = new BlockPos(8, 1, 6), chestB = new BlockPos(4, 1, 8);
        helper.setBlock(chestA, Blocks.CHEST);
        helper.setBlock(chestB, Blocks.CHEST);
        periscope(helper, new BlockPos(7, 1, 6), Direction.EAST);
        periscope(helper, new BlockPos(4, 1, 7), Direction.SOUTH);
        var pa = helper.getBlockEntity(new BlockPos(7, 1, 6), com.golemlogix.blockentity.PeriscopeBlockEntity.class);
        var pb = helper.getBlockEntity(new BlockPos(4, 1, 7), com.golemlogix.blockentity.PeriscopeBlockEntity.class);
        pb.setPriority(5);
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.insertItem(new ItemStack(Items.STONE, 10), false).isEmpty(), "stone fits");
            var a = helper.getBlockEntity(chestA, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            var b = helper.getBlockEntity(chestB, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            helper.assertTrue(count(a, Items.STONE) == 0 && count(b, Items.STONE) == 10, "the higher priority chest B gets the stone first");
            pa.setPriority(10);
            GolemNetwork n2 = net(helper, ORIGIN);
            helper.assertTrue(n2.insertItem(new ItemStack(Items.DIRT, 10), false).isEmpty(), "dirt fits");
            helper.assertTrue(count(a, Items.DIRT) == 10 && count(b, Items.DIRT) == 0, "after raising A above B, A gets the dirt");
            // extract from the lowest-priority storage first
            helper.assertTrue(n2.extractItem(new ItemStack(Items.STONE), 4, false).getCount() == 4 && count(b, Items.STONE) == 6, "extraction works");
            helper.succeed();
        });
    }

    private static int count(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    public static void busPriorityRank(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos a = new BlockPos(3, 1, 6), b = new BlockPos(4, 1, 7);
        put(helper, a, ModBlocks.VACUUM_IMPORT_BUS.get());
        put(helper, b, ModBlocks.INJECTOR_EXPORT_BUS.get());
        var ba = helper.getBlockEntity(a, BusBlockEntity.class);
        var bb = helper.getBlockEntity(b, BusBlockEntity.class);
        bb.setPriority(7);
        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.busRank(bb) == 0 && n.busRank(ba) == 1, "higher priority bus goes first");
            ba.setPriority(50);
            helper.assertTrue(net(helper, ORIGIN).busRank(ba) == 0, "now the first bus leads");
            helper.succeed();
        });
    }
    // ------------------------------------------------- autocrafting: chains and edge cases

    private static final BlockPos MACHINE_OUT = new BlockPos(2, 1, 4);

    private static void autocraftRig(GameTestHelper helper) {
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(PROV.east(), Blocks.CHEST);              // "machine input"
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        helper.setBlock(MACHINE_OUT, Blocks.CHEST);              // "machine output", the import bus takes from it
    }

    /**
     * Autocrafting through the Mana Saturator: an "iron block -> 2 cores" pattern, a Provider right against a machine part puts the block on
     * the anvil itself, an import bus at another part returns the plates to the job.
     */
    public static void autocraftThroughSaturator(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        // from the corner bushing (6,1,6): pipes to the east and to the Provider; a calculator by the pipe
        for (int x = 7; x <= 9; x++) {
            put(helper, new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(9, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(7, 1, 7), ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        BlockPos prov = new BlockPos(9, 1, 8);
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        put(helper, new BlockPos(10, 1, 8), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos bus = new BlockPos(11, 1, 8);
        helper.setBlock(bus, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        // the machine faces south: cells x 9..11, y 1..3, z 9..10; the Provider and the bus touch the back bottom cells
        BlockPos ctl = new BlockPos(10, 3, 10);
        buildSaturator(helper, ctl, Direction.SOUTH);
        put(helper, new BlockPos(12, 1, 10), ModBlocks.CREATIVE_SOURCE_JAR.get());
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_BLOCK)), List.of(new ItemStack(ModItems.FLATTENED_IRON_CORE.get(), 2)))));
        ItemKey core = ItemKey.of(new ItemStack(ModItems.FLATTENED_IRON_CORE.get()));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_BLOCK, 1), false);
            helper.assertTrue(n.canCraft(core), "the saturator pattern is known to the network");
            var started = n.startCrafting(core, 2);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "started: " + started.type());
        });
        helper.runAfterDelay(650, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(core) == 2, "two cores arrived through the import bus, got " + n.itemCountOf(core));
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))) == 0, "the iron block was used");
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.getHeldItem().isEmpty() && sat.outputDeck().isEmpty(), "the anvil and the output deck are empty again");
            helper.succeed();
        });
    }

    /** Raw iron -> ingot (machine, 9 times) -> iron block (3x3 crafting table): the whole path through the import bus. */
    public static void autocraftMachineThenCrafting(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        autocraftRig(helper);
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        ItemStack ingot = new ItemStack(Items.IRON_INGOT);
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.IRON_BLOCK),
                ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy(), ingot.copy())));
        Container machineIn = ((Container) helper.getBlockEntity(PROV.east(), net.minecraft.world.level.block.entity.BlockEntity.class));
        Container machineOut = ((Container) helper.getBlockEntity(MACHINE_OUT, net.minecraft.world.level.block.entity.BlockEntity.class));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 9), false);
            ItemKey block = ItemKey.of(new ItemStack(Items.IRON_BLOCK));
            CraftingPlan plan = CraftingPlanner.plan(n, block, 1);
            helper.assertTrue(plan.missing().isEmpty() && plan.steps().size() == 2 && plan.totalExecutions() == 10,
                    "plan: " + plan.steps().size() + " steps, " + plan.totalExecutions() + " executions, missing " + plan.missing());
            helper.assertTrue(plan.steps().get(0).pattern().crafting() == false, "the machine step goes first");
            helper.assertTrue(n.startCrafting(block, 1).type() == GolemNetwork.StartType.STARTED, "started");
        });
        helper.runAfterDelay(120, () -> {
            int raw = 0;
            for (int i = 0; i < machineIn.getContainerSize(); i++) {
                raw += machineIn.getItem(i).getCount();
            }
            helper.assertTrue(raw == 9, "machine received all 9 raw iron, got " + raw);
            machineIn.clearContent();
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT, 9)); // the "machine" has done its work
        });
        helper.runAfterDelay(300, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(machineOut.isEmpty(), "import bus took the ingots out of the machine");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))) == 1, "iron block delivered, got "
                    + n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))));
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 0, "no ingots left over");
            helper.assertTrue((helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    /** Storage is full to the top: the machine output is still accepted, and the finished item is delivered as soon as there is room. */
    public static void autocraftWithFullStorage(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        autocraftRig(helper);
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        Container machineIn = ((Container) helper.getBlockEntity(PROV.east(), net.minecraft.world.level.block.entity.BlockEntity.class));
        Container machineOut = ((Container) helper.getBlockEntity(MACHINE_OUT, net.minecraft.world.level.block.entity.BlockEntity.class));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 1), false);
            helper.assertTrue(n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 1).type() == GolemNetwork.StartType.STARTED, "started");
            // fill the disk to the brim
            for (int i = 0; i < 20; i++) {
                n.insertItem(new ItemStack(Items.STONE, 64), false);
            }
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!machineIn.isEmpty(), "raw iron went to the machine");
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT));
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(machineOut.isEmpty(), "the bus must take the ingot although the storage is full");
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 0, "ingot waits inside the job");
            helper.assertTrue(!(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class)).isIdle(), "job still delivering");
            n.extractItem(new ItemStack(Items.STONE), 64, false);       // made room
        });
        helper.runAfterDelay(200, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 1, "ingot delivered once there is room");
            helper.assertTrue((helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }
    public static void blueprintMatchesArray(GameTestHelper helper) {
        buildArray(helper, 3, 2);
        helper.runAfterDelay(2, () -> {
            var cells = AtlasBlueprint.cells(AtlasBlueprint.Variant.FULL);
            helper.assertTrue(cells.size() == 36, "full blueprint has 36 cells, got " + cells.size());
            for (AtlasBlueprint.Cell c : cells) {
                BlockState actual = helper.getBlockState(ORIGIN.offset(c.dx(), c.dy(), c.dz()));
                helper.assertTrue(c.matches(actual), "cell " + c.dx() + "," + c.dy() + "," + c.dz() + " must match the built array, found " + actual.getBlock());
            }
            var need = AtlasBlueprint.requirements(AtlasBlueprint.Variant.FULL);
            helper.assertTrue(need.get(ModBlocks.ATLAS_CORE.get().asItem()) == 1, "1 core");
            helper.assertTrue(need.get(ModBlocks.CABLE_BUSHING.get().asItem()) == 5, "5 bushings");
            helper.assertTrue(need.get(ModBlocks.ATLAS_CONDUIT.get().asItem()) == 6, "6 conduits");
            helper.assertTrue(need.get(ModBlocks.ATLAS_ATLANT.get().asItem()) == 4, "4 atlants (one item per two blocks)");
            helper.assertTrue(need.get(ModBlocks.ATLAS_CASING.get().asItem()) == 16, "16 casings");
            var tower = AtlasBlueprint.requirements(AtlasBlueprint.Variant.TOWER);
            helper.assertTrue(tower.size() == 3 && tower.get(ModBlocks.ATLAS_CONDUIT.get().asItem()) == 2, "tower: core, 2 conduits, bushing");

            // a wrong Atlant half and an empty cell do not match the blueprint
            AtlasBlueprint.Cell upper = cells.stream().filter(c -> c.dy() == 2 && c.dx() == 1 && c.dz() == 1).findFirst().get();
            helper.assertTrue(!upper.matches(ModBlocks.ATLAS_ATLANT.get().defaultBlockState().setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER)), "wrong half");
            helper.assertTrue(!upper.matches(Blocks.AIR.defaultBlockState()), "air does not match");
            helper.succeed();
        });
    }

    /** The Saturator blueprint matches what assembly requires: 18 cells, the item set, rotation by the front, parts count as correct. */
    public static void saturatorBlueprint(GameTestHelper helper) {
        var need = AtlasBlueprint.requirements(AtlasBlueprint.Variant.SATURATOR);
        helper.assertTrue(need.get(ModBlocks.MANA_SATURATOR.get().asItem()) == 1, "1 saturator head");
        helper.assertTrue(need.get(Items.END_ROD) == 1, "1 end rod");
        helper.assertTrue(need.get(ModBlocks.DENSE_FROST_PIPE.get().asItem()) == 1, "1 dense pipe");
        helper.assertTrue(need.get(ModBlocks.ATLAS_CASING.get().asItem()) == 15, "15 casings");
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            var cells = com.golemlogix.system.SaturatorBlueprint.cells(facing);
            helper.assertTrue(cells.size() == 18, "18 cells for " + facing);
            BlockPos at = new BlockPos(6, 3, 6);
            buildSaturator(helper, at, facing);
            // the blueprint is tied to the "central box": it is two blocks below the owner
            BlockPos port = at.below(2).relative(com.golemlogix.system.SaturatorStructure.right(facing), 0);
            for (AtlasBlueprint.Cell c : cells) {
                BlockState actual = helper.getBlockState(port.offset(c.dx(), c.dy(), c.dz()));
                helper.assertTrue(c.matches(actual), "blueprint cell " + c.dx() + "," + c.dy() + "," + c.dz() + " facing " + facing + " found " + actual.getBlock());
            }
            helper.assertTrue(!cells.get(0).matches(Blocks.AIR.defaultBlockState()), "air does not match");
            helper.assertTrue(cells.get(0).matches(ModBlocks.SATURATOR_PART.get().defaultBlockState()), "a formed part matches");
        }
        helper.succeed();
    }

    // ------------------------------------------------- upgrades, redstone, waiting, precraft

    private static int count(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            n += c.getItem(i).getCount();
        }
        return n;
    }

    /**
     * A bus does not move items in circles: a chest read by a periscope is not storage for "its own" bus. While there is no other storage,
     * import takes nothing and export moves nothing (otherwise items would slide into the first slot and the bus would click
     * forever). Export delivers items from a real disk into this chest as usual.
     */
    public static void busSkipsItsOwnContainer(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        // import (3,1,4) -> chest (2,1,4); the same chest is read by a periscope (2,1,5), pipes from the bushing (4,1,6)
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        put(helper, new BlockPos(3, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(3, 1, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(2, 1, 5), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        Container c = ((Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class));
        c.setItem(5, new ItemStack(Items.COBBLESTONE, 20));
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(net(helper, ORIGIN).itemCountOf(cobble) == 20, "the periscope shows the chest");
            helper.assertTrue(c.getItem(5).getCount() == 20 && c.getItem(0).isEmpty(), "the import bus leaves its own chest alone");
            // in place of the import: an export with cobblestone in the filter
            helper.setBlock(BUS_IN, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
            (helper.getBlockEntity(BUS_IN, BusBlockEntity.class)).getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(c.getItem(5).getCount() == 20 && c.getItem(0).isEmpty(), "the export bus does not refill a chest from itself");
            putBay(helper, BAY);
            (helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
            helper.assertTrue(net(helper, ORIGIN).insertItem(new ItemStack(Items.COBBLESTONE, 5), false).isEmpty(), "5 more go to the disk");
        });
        helper.runAfterDelay(220, () -> {
            CopperDriveBayBlockEntity be = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
            helper.assertTrue(DiskStorage.storedItems(be.getDisks().get(0)) == 0, "the export bus delivered the disk's cobblestone");
            helper.assertTrue(count(c) == 25 && c.getItem(5).getCount() == 25, "all 25 in the chest's stack, got " + count(c));
            helper.succeed();
        });
    }

    /** Six upgrade slots: up to four speed upgrades, an Autocraft Module only on export, one module of each kind; speed upgrades are saved. */
    public static void busUpgradeSlots(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState());
        BusBlockEntity in = helper.getBlockEntity(at, BusBlockEntity.class);
        var up = in.getUpgrades();
        helper.assertTrue(up.getContainerSize() == 6 && up.getMaxStackSize() == 1, "six single-item upgrade slots");
        int baseInterval = in.interval();
        int baseAmount = in.itemsPerOperation();
        ItemStack speed = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(up.canPlaceItem(i, speed), "speed upgrade " + (i + 1) + " fits");
            up.setItem(i, speed.copy());
        }
        helper.assertTrue(!up.canPlaceItem(4, speed), "a fifth speed upgrade is rejected");
        helper.assertTrue(!up.canPlaceItem(4, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "the import bus takes no autocraft module");
        helper.assertTrue(!up.canPlaceItem(4, new ItemStack(Items.DIAMOND)), "not an upgrade");
        helper.assertTrue(up.canPlaceItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get())), "a redstone module fits");
        up.setItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        helper.assertTrue(!up.canPlaceItem(5, new ItemStack(ModItems.REDSTONE_MODULE.get())), "only one redstone module");
        helper.assertTrue(in.itemsPerOperation() == baseAmount * 8, "four upgrades move 8x as much, got " + in.itemsPerOperation());
        helper.assertTrue(in.interval() < baseInterval && in.interval() >= 1, "four upgrades work more often: " + in.interval() + " < " + baseInterval);

        BlockPos at2 = new BlockPos(4, 1, 2);
        helper.setBlock(at2, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState());
        var up2 = (helper.getBlockEntity(at2, BusBlockEntity.class)).getUpgrades();
        helper.assertTrue(up2.canPlaceItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "the export bus takes the autocraft module");
        up2.setItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        helper.assertTrue(!up2.canPlaceItem(1, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "only one autocraft module");

        BlockPos at3 = new BlockPos(6, 1, 2);
        helper.setBlock(at3, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState());
        var up3 = (helper.getBlockEntity(at3, com.golemlogix.blockentity.PeriscopeBlockEntity.class)).getUpgrades();
        helper.assertTrue(up3.getContainerSize() == 1 && !up3.canPlaceItem(0, speed)
                && up3.canPlaceItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get())), "the periscope takes only the redstone module");

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = in.saveWithFullMetadata(registries);
        BusBlockEntity fresh = new BusBlockEntity(helper.absolutePos(at), helper.getBlockState(at));
        fresh.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(fresh.getUpgrades().count(com.golemlogix.upgrade.UpgradeType.SPEED) == 4
                && fresh.getUpgrades().has(com.golemlogix.upgrade.UpgradeType.REDSTONE), "upgrades are saved with the bus");
        helper.succeed();
    }

    /** Cryo-Calculator: up to four Compressed Co-Processors in the slots, each +4 steps per cycle; they add up with the placed ones. */
    public static void calculatorCompressedCoprocessors(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        helper.setBlock(at, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(at.east(), ModBlocks.COOLING_COPROCESSOR.get().defaultBlockState());
        CryoCalculatorBlockEntity cpu = helper.getBlockEntity(at, CryoCalculatorBlockEntity.class);
        var up = cpu.getUpgrades();
        ItemStack speed = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        helper.assertTrue(up.getContainerSize() == 5, "five upgrade slots");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(up.canPlaceItem(i, speed), "compressed co-processor " + (i + 1) + " fits");
            up.setItem(i, speed.copy());
        }
        helper.assertTrue(!up.canPlaceItem(4, speed), "a fifth one does not");
        helper.assertTrue(!up.canPlaceItem(4, new ItemStack(ModItems.AUTOCRAFT_MODULE.get())), "no autocraft module in a calculator");
        helper.assertTrue(up.canPlaceItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get())), "the redstone module fits");
        helper.assertTrue(cpu.getCompressed() == 4, "four counted");
        helper.runAfterDelay(25, () -> {
            int expected = CryoCalculatorBlockEntity.BASE_WORKERS + CryoCalculatorBlockEntity.WORKERS_PER_COPROCESSOR
                    + 4 * CryoCalculatorBlockEntity.WORKERS_PER_COMPRESSED;
            helper.assertTrue(cpu.getCoprocessors() == 1, "the adjacent co-processor is counted");
            helper.assertTrue(cpu.getWorkers() == expected, "steps per cycle " + cpu.getWorkers() + " == " + expected);
            helper.succeed();
        });
    }

    /** Redstone Module modes, as in AE2/RS: with signal, without signal, one action per pulse; without the module the signal does not matter. */
    public static void redstoneModes(GameTestHelper helper) {
        var rc = new com.golemlogix.upgrade.RedstoneControl();
        helper.assertTrue(rc.allows(false, false, false) && rc.allows(false, true, true), "without the module the signal does not matter");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.HIGH);
        helper.assertTrue(!rc.allows(true, false, false) && rc.allows(true, true, false), "HIGH: only with a signal");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.LOW);
        helper.assertTrue(rc.allows(true, false, false) && !rc.allows(true, true, false), "LOW: only without a signal");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.IGNORE);
        helper.assertTrue(rc.allows(true, true, false) && rc.allows(true, false, false), "IGNORE: always");
        rc.setMode(com.golemlogix.upgrade.RedstoneMode.PULSE);
        rc.observe(false);
        helper.assertTrue(!rc.allows(true, false, false), "PULSE: nothing before the first edge");
        rc.observe(true);
        helper.assertTrue(rc.allows(true, true, false), "a rising edge arms one action");
        helper.assertTrue(rc.allows(true, true, true), "the action spends it");
        helper.assertTrue(!rc.allows(true, true, false), "a steady signal gives no second action");
        rc.observe(false);
        rc.observe(true);
        helper.assertTrue(rc.allows(true, true, true), "the next edge gives the next action");
        helper.assertTrue(com.golemlogix.upgrade.RedstoneMode.LOW.next(false) == com.golemlogix.upgrade.RedstoneMode.IGNORE,
                "storage modes skip PULSE");

        // an import bus with the module in "with signal" mode stands still without a signal and works with it
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BusBlockEntity bus = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);
        bus.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = ((Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class));
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(count(c) == 8, "no signal: the bus waits");
            helper.setBlock(BUS_IN.north(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(c.isEmpty(), "with a signal the bus works");
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 8, "into the network");
            helper.succeed();
        });
    }

    /** Provider wait mode: the next portion is not loaded into the machine until the result of the previous one has come back to the job. */
    public static void providerWaitsForResult(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        autocraftRig(helper);
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.setBlocking(true);
        Container machineIn = ((Container) helper.getBlockEntity(PROV.east(), net.minecraft.world.level.block.entity.BlockEntity.class));
        Container machineOut = ((Container) helper.getBlockEntity(MACHINE_OUT, net.minecraft.world.level.block.entity.BlockEntity.class));
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 2), false);
            helper.assertTrue(n.startCrafting(ingot, 2).type() == GolemNetwork.StartType.STARTED, "started");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(count(machineIn) == 1, "while waiting only the first batch is loaded, got " + count(machineIn));
            helper.assertTrue(net(helper, bay).providerBusy(helper.absolutePos(PROV)), "the provider waits for the result");
            machineIn.clearContent();
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT));    // the "machine" has processed the first portion
        });
        helper.runAfterDelay(170, () -> {
            helper.assertTrue(machineOut.isEmpty(), "the bus returned the first ingot");
            helper.assertTrue(count(machineIn) == 1, "the result came back, so the second batch is loaded, got " + count(machineIn));
            machineIn.clearContent();
            machineOut.setItem(0, new ItemStack(Items.IRON_INGOT));
        });
        helper.runAfterDelay(280, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(ingot) == 2, "both ingots delivered, got " + n.itemCountOf(ingot));
            helper.assertTrue((helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    /** Precraft (Ctrl + "+" in JEI): one job for several items at once. */
    public static void precraftSeveralTargets(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4),
                plank.copy(), ItemStack.EMPTY, ItemStack.EMPTY, plank.copy())));
        ItemKey sticks = ItemKey.of(new ItemStack(Items.STICK));
        ItemKey planks = ItemKey.of(plank);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 2), false);
            java.util.Map<ItemKey, Long> targets = new java.util.LinkedHashMap<>();
            targets.put(sticks, 4L);
            targets.put(planks, 4L);
            CraftingPlan plan = CraftingPlanner.plan(n, targets);
            helper.assertTrue(plan.missing().isEmpty(), "two logs are enough for 4 sticks and 4 planks, missing " + plan.missing());
            helper.assertTrue(plan.totalExecutions() == 3, "2 planks crafts + 1 sticks craft, got " + plan.totalExecutions());
            targets.put(sticks, 40L);
            helper.assertTrue(!CraftingPlanner.plan(n, targets).missing().isEmpty(), "40 sticks need more logs");
            targets.put(sticks, 4L);
            GolemNetwork.StartResult r = n.startCrafting(targets, null, null);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "one job for both: " + r.type());
            helper.assertTrue(n.freeCpu() == null, "the only calculator took it");
        });
        helper.runAfterDelay(220, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(sticks) == 4, "4 sticks, got " + n.itemCountOf(sticks));
            helper.assertTrue(n.itemCountOf(planks) >= 4, "at least 4 planks, got " + n.itemCountOf(planks));
            helper.assertTrue((helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class)).isIdle(), "calculator idle");
            helper.succeed();
        });
    }

    // ------------------------------------------------- saturator: cooling and redstone

    private static BlockHitResult hitTop(BlockPos abs) {
        return new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
    }

    /** Coprocessors on the roof grilles: only on top, one per grille, they speed up the press, come off with Shift and are not lost on disassembly. */
    public static void saturatorCoolers(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        int[] vents = com.golemlogix.system.SaturatorStructure.VENTS;
        helper.runAfterDelay(30, () -> {
            var level = helper.getLevel();
            helper.assertTrue(helper.getBlockState(ctl).getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "forms");
            var player = testPlayer(helper);
            ItemStack coolers = new ItemStack(ModBlocks.COOLING_COPROCESSOR.get(), 6);
            BlockPos first = satCell(helper, ctl, Direction.NORTH, vents[0]);
            level.getBlockState(first).useItemOn(coolers, level, player, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(first), Direction.NORTH, first, false));
            helper.assertTrue(!level.getBlockState(first).getValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER) && coolers.getCount() == 6,
                    "a cooler is not mounted from the side");
            for (int v : vents) {
                BlockPos p = satCell(helper, ctl, Direction.NORTH, v);
                var r = level.getBlockState(p).useItemOn(coolers, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hitTop(p));
                helper.assertTrue(r.consumesAction() && level.getBlockState(p).getValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER),
                        "a cooler sits on vent " + v);
            }
            helper.assertTrue(coolers.getCount() == 1, "five coolers used, " + coolers.getCount() + " left");
            level.getBlockState(first).useItemOn(coolers, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hitTop(first));
            helper.assertTrue(coolers.getCount() == 1, "a busy vent takes no second cooler");
        });
        helper.runAfterDelay(70, () -> {
            var level = helper.getLevel();
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.getCoolers() == 5, "the machine counted its coolers, got " + sat.getCoolers());
            helper.assertTrue(sat.speed() == com.golemlogix.blockentity.ManaSaturatorBlockEntity.BASE_SPEED
                    + 5 * com.golemlogix.blockentity.ManaSaturatorBlockEntity.SPEED_PER_COOLER, "five coolers speed it up");
            var player = testPlayer(helper);
            player.setShiftKeyDown(true);
            BlockPos p = satCell(helper, ctl, Direction.NORTH, vents[2]);
            level.getBlockState(p).useWithoutItem(level, player, hitTop(p));
            helper.assertTrue(!level.getBlockState(p).getValue(com.golemlogix.blocks.SaturatorPartBlock.COOLER), "Shift+click takes a cooler off");
            helper.assertTrue(player.getInventory().countItem(ModBlocks.COOLING_COPROCESSOR.get().asItem()) == 1, "and gives it back");
            // disassembling the machine: the four remaining coprocessors drop as items
            level.destroyBlock(satCell(helper, ctl, Direction.NORTH, com.golemlogix.system.SaturatorStructure.ROD), false);
            var box = new net.minecraft.world.phys.AABB(helper.absolutePos(ctl)).inflate(3);
            int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box,
                    e -> e.getItem().is(ModBlocks.COOLING_COPROCESSOR.get().asItem())).stream().mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(dropped == 4, "the other four coolers drop, got " + dropped);
            helper.succeed();
        });
    }

    /** Redstone Module in the Saturator: installed by right-click, another right-click changes the mode; in "with signal" mode without a signal the golem waits. */
    public static void saturatorRedstoneModule(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.runAfterDelay(30, () -> {
            var level = helper.getLevel();
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            var player = testPlayer(helper);
            BlockPos abs = helper.absolutePos(ctl);
            var hit = new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
            ItemStack module = new ItemStack(ModItems.REDSTONE_MODULE.get(), 2);
            level.getBlockState(abs).useItemOn(module, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sat.hasRedstoneModule() && module.getCount() == 1, "the module goes into the machine");
            helper.assertTrue(sat.redstoneMode() == com.golemlogix.upgrade.RedstoneMode.HIGH, "default mode: with a signal");
            for (int i = 0; i < 4; i++) {
                level.getBlockState(abs).useItemOn(module, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            }
            helper.assertTrue(sat.redstoneMode() == com.golemlogix.upgrade.RedstoneMode.HIGH && module.getCount() == 1,
                    "four clicks go round the modes and spend nothing");
            ItemStack iron = new ItemStack(Items.IRON_BLOCK);
            level.getBlockState(abs).useItemOn(iron, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(sat.inputDeck().is(Items.IRON_BLOCK), "the blank waits on the left deck");
        });
        helper.runAfterDelay(170, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.getHeldItem().is(Items.IRON_BLOCK) && !sat.isCrafting(), "without a signal the smith waits");
            helper.setBlock(new BlockPos(3, 1, 5), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(340, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.outputDeck().is(ModItems.FLATTENED_IRON_CORE.get()), "with a signal it forges, got " + sat.outputDeck());
            var player = testPlayer(helper);
            player.setShiftKeyDown(true);
            BlockPos abs = helper.absolutePos(ctl);
            helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
                    new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
            helper.assertTrue(!sat.hasRedstoneModule(), "Shift+click with an empty hand takes the module out");
            helper.assertTrue(player.getInventory().countItem(ModItems.REDSTONE_MODULE.get()) == 1, "and gives it back");
            helper.succeed();
        });
    }

    /** The Resonator does not power the network or fill jars; but a chronometer of a network with a resonator is always full. */
    public static void resonatorOnlyFeedsChronometers(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        BlockPos res = new BlockPos(3, 1, 6);     // right against the corner bushing (4,1,6)
        put(helper, res, ModBlocks.ETERNAL_FROST_RESONATOR.get());
        Identifier jarId = Identifier.fromNamespaceAndPath("ars_nouveau", "source_jar");
        boolean ars = com.golemlogix.system.SourceHelper.arsNouveauLoaded() && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(jarId);
        BlockPos arsJar = new BlockPos(2, 1, 6);  // an Ars Nouveau jar next to the resonator
        if (ars) {
            helper.setBlock(arsJar, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(jarId));
        }
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(com.golemlogix.system.SourceHelper.asSupplier(helper.getBlockEntity(res, net.minecraft.world.level.block.entity.BlockEntity.class)) == null, "the resonator is not a Source supplier");
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.hasResonator(), "the network sees its resonator");
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(!n.isOnline(), "a resonator alone does not power the network");
            }
            if (ars) {
                var supplier = com.golemlogix.system.SourceHelper.asSupplier(helper.getBlockEntity(arsJar, net.minecraft.world.level.block.entity.BlockEntity.class));
                helper.assertTrue(supplier != null && supplier.available() == 0, "the resonator does not fill Ars Nouveau jars");
            }
            powered(helper);
        });
        helper.runAfterDelay(100, () -> {
            var level = helper.getLevel();
            helper.assertTrue(net(helper, ORIGIN).isOnline(), "the jar powers the network");
            var player = testPlayer(helper);
            ItemStack chrono = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
            chrono.set(ModDataComponents.BOUND_CORE.get(), GlobalPos.of(level.dimension(), helper.absolutePos(CORE)));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, chrono);
            chrono.use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(com.golemlogix.items.PortableChronometerItem.getCharge(chrono) == com.golemlogix.items.PortableChronometerItem.MAX_CHARGE,
                        "the resonator fills the chronometer, got " + com.golemlogix.items.PortableChronometerItem.getCharge(chrono));
                helper.assertTrue(Boolean.TRUE.equals(chrono.get(ModDataComponents.RESONATOR_LINK.get())), "the chronometer knows about the resonator");
            }
            player.closeContainer();
            // without a resonator the chronometer charges from a Source jar
            ItemStack plain = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plain);
            BlockPos jar = helper.absolutePos(JAR);
            plain.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hitTop(jar)));
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(com.golemlogix.items.PortableChronometerItem.getCharge(plain) == com.golemlogix.items.PortableChronometerItem.MAX_CHARGE,
                        "a creative jar charges the chronometer to the top");
            }
            helper.succeed();
        });
    }

    // ----------------------------------------------------------- cryo-distillation (Source without Ars Nouveau)

    /**
     * Cryo-Source Jar: a two-block supplier. Both halves touch the network, yet it is one supplier holding the Source in its lower
     * half; the network draws on it; breaking the top takes the whole jar.
     */
    public static void cryoJarFeedsNetwork(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        BlockPos jarPos = new BlockPos(3, 1, 6);  // against the corner bushing (4,1,6); its top against the array above it
        com.golemlogix.blocks.CryoJarBlock.place(helper.getLevel(), helper.absolutePos(jarPos), Direction.SOUTH,
                ModBlocks.CRYO_SOURCE_JAR.get());
        var lower = helper.getBlockEntity(jarPos, com.golemlogix.blockentity.CryoJarBlockEntity.class);
        var upper = helper.getBlockEntity(jarPos.above(), com.golemlogix.blockentity.CryoJarBlockEntity.class);
        int cap = lower.capacity();
        helper.assertTrue(lower.fill(5000, false) == 5000, "the jar takes Source");
        helper.assertTrue(upper.fill(Integer.MAX_VALUE, true) == cap - 5000, "the top half reports the same jar's room");
        helper.assertTrue(com.golemlogix.system.SourceHelper.asSupplier(upper) == lower, "the top half stands for the lower one");
        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            helper.assertTrue(n.sources().size() == 1, "one jar is one supplier, got " + n.sources().size());
            int has = n.sources().get(0).available();
            helper.assertTrue(has > 4900 && has <= 5000, "it has the Source: " + has);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(net(helper, ORIGIN).isOnline(), "the jar powers the network");
            if (com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(lower.available() < 5000, "the core drinks from it: " + lower.available());
            }
            helper.getLevel().destroyBlock(helper.absolutePos(jarPos.above()), false);
            helper.assertBlockNotPresent(ModBlocks.CRYO_SOURCE_JAR.get(), jarPos);
            helper.succeed();
        });
    }

    /**
     * Cryo-Distillation Tower: takes only blood (the tag golem_logix:cryo_distillable) through any of its blocks and never gives it
     * back; distils it into the jar beside it, 1 mB to 1 Source; breaking the middle takes the whole tower. Without Neo Vitae (an
     * empty tag) only the refusals and the tower's shape are checked.
     */
    public static void distillationTowerFillsJar(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = new BlockPos(3, 1, 3);
        BlockPos jarPos = new BlockPos(4, 1, 3);
        com.golemlogix.blocks.DistillationTowerBlock.place(level, helper.absolutePos(base), Direction.SOUTH,
                ModBlocks.CRYO_DISTILLATION_TOWER.get());
        com.golemlogix.blocks.CryoJarBlock.place(level, helper.absolutePos(jarPos), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        var tower = helper.getBlockEntity(base, com.golemlogix.blockentity.DistillationTowerBlockEntity.class);
        var jar = helper.getBlockEntity(jarPos, com.golemlogix.blockentity.CryoJarBlockEntity.class);
        var exec = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        var top = com.golemlogix.system.LegacyResources.fluids(level, helper.absolutePos(base.above(2)), Direction.UP);
        helper.assertTrue(top != null, "the head takes fluids too");
        // Compressed Co-Processors speed it up: up to four, nothing else goes into those slots
        var up = tower.getUpgrades();
        int baseRate = tower.rate();
        ItemStack copro = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        helper.assertTrue(up.getContainerSize() == 4 && !up.canPlaceItem(0, new ItemStack(ModItems.FUZZY_CARD.get())),
                "four slots, co-processors only");
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(up.canPlaceItem(i, copro), "co-processor " + (i + 1) + " fits");
            up.setItem(i, copro.copy());
        }
        helper.assertTrue(tower.rate() == baseRate + 4 * com.golemlogix.config.GolemConfig.DISTILL_COPROCESSOR_BONUS.get(),
                "four co-processors: " + baseRate + " -> " + tower.rate() + " mB a second");
        helper.assertTrue(top.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), exec) == 0,
                "water is no blood");
        net.minecraft.world.level.material.Fluid blood = net.minecraft.core.registries.BuiltInRegistries.FLUID
                .get(com.golemlogix.init.ModTags.CRYO_DISTILLABLE)
                .flatMap(t -> t.stream().map(net.minecraft.core.Holder::value).filter(f -> f.isSource(f.defaultFluidState())).findFirst())
                .orElse(null);
        boolean vitae = net.neoforged.fml.ModList.get().isLoaded("neovitae");
        helper.assertTrue(blood != null || !vitae, "with Neo Vitae Essentia Vitae is blood");
        if (vitae) {
            helper.assertTrue(com.golemlogix.system.SourceHelper.sourceRequired() == com.golemlogix.config.GolemConfig.REQUIRE_SOURCE.get(),
                    "with Neo Vitae the network needs Source");
        }
        if (blood != null) {
            helper.assertTrue(top.fill(new net.neoforged.neoforge.fluids.FluidStack(blood, 1000), exec) == 1000, "blood goes in");
            helper.assertTrue(top.drain(500, exec).isEmpty(), "and does not come back out");
        }
        net.minecraft.world.level.material.Fluid poured = blood;
        helper.runAfterDelay(70, () -> {
            if (poured != null) {
                int left = tower.blood().getAmount();
                helper.assertTrue(left <= 800 && left > 0, "with the co-processors the tower distils fast: " + left + " mB left");
                double perMb = com.golemlogix.config.GolemConfig.DISTILL_SOURCE_PER_MB.get();
                helper.assertTrue(jar.available() == (int) Math.floor((1000 - left) * perMb + 1e-9),
                        "the jar got " + jar.available() + " Source for " + (1000 - left) + " mB");
            }
            level.destroyBlock(helper.absolutePos(base.above()), false);
            helper.assertBlockNotPresent(ModBlocks.CRYO_DISTILLATION_TOWER.get(), base);
            helper.assertBlockNotPresent(ModBlocks.CRYO_DISTILLATION_TOWER.get(), base.above(2));
            helper.succeed();
        });
    }

    /** Cryo-Mixer: each fluid into its own flask, snow fast and ice slow, co-processors, the tray only gives, the Redstone Module. */
    public static void cryoMixerSnowAndIce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = new BlockPos(3, 1, 3);
        com.golemlogix.blocks.CryoMixerBlock.place(level, helper.absolutePos(base), Direction.SOUTH, ModBlocks.CRYO_MIXER.get());
        var mixer = helper.getBlockEntity(base, com.golemlogix.blockentity.CryoMixerBlockEntity.class);
        var exec = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        var bottom = com.golemlogix.system.LegacyResources.fluids(level, helper.absolutePos(base), Direction.NORTH);
        var top = com.golemlogix.system.LegacyResources.fluids(level, helper.absolutePos(base.above()), Direction.UP);
        var tray = com.golemlogix.system.LegacyResources.items(level, helper.absolutePos(base), Direction.NORTH);
        helper.assertTrue(bottom != null && top != null && tray != null, "both blocks take fluids, the tray is reachable");
        helper.assertTrue(bottom.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 8000), exec) == 8000,
                "water goes in below");
        helper.assertTrue(top.fill(new net.neoforged.neoforge.fluids.FluidStack(com.golemlogix.init.ModFluids.CRYO_SOURCE.get(), 1000), exec) == 1000,
                "Liquid Cryo-Source goes in on top");
        helper.assertTrue(mixer.water().getAmount() == 8000 && mixer.cryo().getAmount() == 1000, "each into its own flask");
        helper.assertTrue(top.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1000), exec) == 0,
                "lava is neither");
        helper.assertTrue(top.drain(500, exec).isEmpty() && bottom.drain(500, exec).isEmpty(), "nothing flows back out");
        helper.assertTrue(tray.insertItem(0, new ItemStack(Items.SNOW_BLOCK), true).getCount() == 1, "nothing goes into the tray");
        var snow = com.golemlogix.blockentity.CryoMixerBlockEntity.Mode.SNOW;
        var ice = com.golemlogix.blockentity.CryoMixerBlockEntity.Mode.ICE;
        helper.assertTrue(snow.ticks() < ice.ticks() && snow.output().is(Items.SNOW_BLOCK) && ice.output().is(Items.ICE),
                "snow is fast, ice is slow");
        mixer.setMode(snow);
        var up = mixer.getUpgrades();
        ItemStack copro = new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get());
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(up.canPlaceItem(i, copro), "co-processor " + (i + 1) + " fits");
            up.setItem(i, copro.copy());
        }
        int fast = (int) Math.ceil(snow.ticks() / 5.0);
        helper.assertTrue(mixer.ticksPerBlock() == fast, "four co-processors: " + snow.ticks() + " -> " + mixer.ticksPerBlock() + " ticks");
        helper.runAfterDelay(fast * 3 + 2, () -> {
            int n = mixer.getOutput().getItem(0).getCount();
            helper.assertTrue(mixer.getOutput().getItem(0).is(Items.SNOW_BLOCK) && n >= 3, "snow in the tray: " + n);
            helper.assertTrue(mixer.water().getAmount() == 8000 - n * snow.water() && mixer.cryo().getAmount() == 1000 - n * snow.cryo(),
                    "each snow block takes " + snow.water() + " + " + snow.cryo() + " mB");
            mixer.setMode(ice);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(mixer.status() == com.golemlogix.blockentity.CryoMixerBlockEntity.TRAY_FULL
                        && mixer.getOutput().getItem(0).is(Items.SNOW_BLOCK), "ice waits while snow lies in the tray");
                int waterLeft = mixer.water().getAmount(), cryoLeft = mixer.cryo().getAmount();
                helper.assertTrue(tray.extractItem(0, 64, false).getCount() == n && mixer.getOutput().getItem(0).isEmpty(),
                        "an Import Bus takes the snow");
                int slow = (int) Math.ceil(ice.ticks() / 5.0);
                helper.runAfterDelay(slow + 2, () -> {
                    int k = mixer.getOutput().getItem(0).getCount();
                    helper.assertTrue(mixer.getOutput().getItem(0).is(Items.ICE) && k >= 1, "ice in the tray: " + k);
                    helper.assertTrue(mixer.water().getAmount() == waterLeft - k * ice.water()
                            && mixer.cryo().getAmount() == cryoLeft - k * ice.cryo(), "each block of ice takes " + ice.water() + " + " + ice.cryo() + " mB");
                    // the Redstone Module: "high" without a signal stops it, a signal starts it again
                    up.setItem(4, new ItemStack(ModItems.REDSTONE_MODULE.get()));
                    mixer.menuData().set(7, com.golemlogix.upgrade.RedstoneMode.HIGH.ordinal());
                    helper.runAfterDelay(2, () -> {
                        helper.assertTrue(mixer.status() == com.golemlogix.blockentity.CryoMixerBlockEntity.PAUSED, "no signal, no ice");
                        helper.setBlock(base.east(), Blocks.REDSTONE_BLOCK);
                        helper.runAfterDelay(2, () -> {
                            helper.assertTrue(mixer.status() == com.golemlogix.blockentity.CryoMixerBlockEntity.WORKING, "a signal starts it");
                            level.destroyBlock(helper.absolutePos(base.above()), false);
                            helper.assertBlockNotPresent(ModBlocks.CRYO_MIXER.get(), base);
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    /** Pattern Provider return slots: anything put in from outside goes into the network; nothing comes back out, the cards are unreachable. */
    public static void providerReturnSlots(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        helper.runAfterDelay(5, () -> {
            var handler = com.golemlogix.system.LegacyResources.items(helper.getLevel(), helper.absolutePos(PROV), Direction.UP);
            helper.assertTrue(handler != null && handler.getSlots() == PatternProviderBlockEntity.RETURN_SLOTS, "only the return slots are reachable");
            helper.assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false).isEmpty(), "a machine hands back five ingots");
            helper.assertTrue(handler.extractItem(0, 64, false).isEmpty(), "nothing can be pulled back out");
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(net(helper, bay).itemCountOf(ingot) == 5, "the ingots went into the network, got " + net(helper, bay).itemCountOf(ingot));
            helper.assertTrue(provider.getReturns().isEmpty(), "the return slots are empty again");
            helper.assertTrue(provider.getContainer().getItem(0).has(com.golemlogix.init.ModDataComponents.PATTERN.get()), "the card stayed in place");
            helper.succeed();
        });
    }

    /** Cryo-Mixer auto-output: into a neighbouring Pattern Provider first, then into a chest; switched off it keeps the tray. */
    public static void mixerAutoOutput(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = new BlockPos(3, 1, 3);
        com.golemlogix.blocks.CryoMixerBlock.place(level, helper.absolutePos(base), Direction.SOUTH, ModBlocks.CRYO_MIXER.get());
        BlockPos chest = base.west(), prov = base.above().east();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        var mixer = helper.getBlockEntity(base, com.golemlogix.blockentity.CryoMixerBlockEntity.class);
        helper.assertTrue(mixer.autoOutput(), "auto-output is on by default");
        var exec = net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE;
        mixer.input().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 8000), exec);
        mixer.input().fill(new net.neoforged.neoforge.fluids.FluidStack(com.golemlogix.init.ModFluids.CRYO_SOURCE.get(), 1000), exec);
        for (int i = 0; i < 4; i++) {
            mixer.getUpgrades().setItem(i, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
        }
        Container box = (Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class);
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(count(provider.getReturns(), Items.SNOW_BLOCK) >= 2, "the snow goes to the provider first, got "
                    + count(provider.getReturns(), Items.SNOW_BLOCK));
            helper.assertTrue(box.isEmpty(), "not into the chest while a provider takes it");
            helper.setBlock(prov, Blocks.AIR);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(count(box, Items.SNOW_BLOCK) >= 2, "without a provider the chest gets it, got " + count(box, Items.SNOW_BLOCK));
            mixer.menuData().set(8, 0);
        });
        helper.runAfterDelay(65, () -> {
            int inChest = count(box, Items.SNOW_BLOCK);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(!mixer.autoOutput() && count(box, Items.SNOW_BLOCK) == inChest, "switched off, nothing more leaves");
                helper.assertTrue(mixer.getOutput().getItem(0).getCount() >= 3, "the snow piles up in the tray");
                helper.succeed();
            });
        });
    }

    /** Mana Saturator auto-output: plates go into a chest; the Forge Hammer switches it off; a Pattern Provider has priority. */
    public static void saturatorAutoOutput(GameTestHelper helper) {
        BlockPos ctl = new BlockPos(5, 3, 5);
        buildSaturator(helper, ctl, Direction.NORTH);
        put(helper, new BlockPos(7, 1, 5), ModBlocks.CREATIVE_SOURCE_JAR.get());
        BlockPos chest = new BlockPos(3, 2, 5);
        helper.setBlock(chest, Blocks.CHEST);
        Container box = (Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class);
        var player = testPlayer(helper);
        BlockPos abs = helper.absolutePos(ctl);
        var hit = new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        int[] made = new int[1];
        Runnable feed = () -> helper.getLevel().getBlockState(abs).useItemOn(new ItemStack(Items.IRON_BLOCK), helper.getLevel(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.runAfterDelay(30, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.autoOutput(), "auto-output is on by default");
            feed.run();
        });
        helper.runAfterDelay(250, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            made[0] = count(box, ModItems.FLATTENED_IRON_CORE.get());
            helper.assertTrue(made[0] > 0 && sat.outputDeck().isEmpty(), "the cores went into the chest by itself, chest " + made[0]);
            helper.getLevel().getBlockState(abs).useItemOn(new ItemStack(ModItems.FORGE_HAMMER.get()), helper.getLevel(), player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(!sat.autoOutput(), "the Forge Hammer switches auto-output off");
            feed.run();
        });
        helper.runAfterDelay(510, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat.outputDeck().is(ModItems.FLATTENED_IRON_CORE.get()) && sat.outputDeck().getCount() == made[0]
                    && count(box, ModItems.FLATTENED_IRON_CORE.get()) == made[0], "switched off, the cores stay on the deck");
            helper.setBlock(new BlockPos(5, 1, 7), ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
            sat.toggleAutoOutput();
        });
        helper.runAfterDelay(540, () -> {
            var sat = helper.getBlockEntity(ctl, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            PatternProviderBlockEntity provider = helper.getBlockEntity(new BlockPos(5, 1, 7), PatternProviderBlockEntity.class);
            helper.assertTrue(count(provider.getReturns(), ModItems.FLATTENED_IRON_CORE.get()) == made[0] && sat.outputDeck().isEmpty(),
                    "with a provider behind it the cores go there, not into the chest");
            helper.assertTrue(count(box, ModItems.FLATTENED_IRON_CORE.get()) == made[0], "the chest got nothing more");
            helper.succeed();
        });
    }
}
