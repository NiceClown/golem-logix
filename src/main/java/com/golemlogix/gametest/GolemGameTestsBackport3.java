package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Annihilation Bus game tests backported from main's GolemGameTestsExtra: breaking scenarios, tools and
 * durability, the empty allow list.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GolemGameTestsBackport3 {

    private static final String T = "empty";

    /** The same point as GolemGameTests.BUS_OUT: next to a base bushing. */
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    private GolemGameTestsBackport3() {
    }

    private static BlockPos baseNetwork(GameTestHelper helper) {
        return GolemGameTests.baseNetwork(helper);
    }

    private static GolemNetwork net(GameTestHelper helper, BlockPos rel) {
        return GolemGameTests.net(helper, rel);
    }

    private static AnnihilationBusBlockEntity busAt(GameTestHelper helper, BlockPos rel) {
        return (AnnihilationBusBlockEntity) helper.getBlockEntity(rel);
    }

    /** Positive control helper: places stone at front, wakes the bus, and asserts it mines stone into cobblestone in network. */
    private static void assertMinesStone(GameTestHelper helper, BlockPos bay, BlockPos bus, BlockPos front) {
        long before = net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE)));
        helper.setBlock(front, Blocks.STONE);
        if (helper.getBlockEntity(bus) instanceof AnnihilationBusBlockEntity be) {
            be.wake();
        }
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) > before,
                    "positive control: bus must mine stone");
        });
    }

    /**
     * Breaking a block with a block entity and contents (a chest with items):
     * The chest and its contents must end up in the network or drop, and never vanish!
     */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusChestWithContents(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(front);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 5));

        helper.runAfterDelay(80, () -> {
            helper.assertBlockNotPresent(Blocks.CHEST, front);
            GolemNetwork network = net(helper, bay);
            long iron = network.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT)));
            long gold = network.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT)));
            long chests = network.itemCountOf(ItemKey.of(new ItemStack(Items.CHEST)));

            // Check if any dropped in the world
            int droppedIron = helper.getEntities(EntityType.ITEM).stream()
                    .mapToInt(e -> e.getItem().is(Items.IRON_INGOT) ? e.getItem().getCount() : 0).sum();
            int droppedGold = helper.getEntities(EntityType.ITEM).stream()
                    .mapToInt(e -> e.getItem().is(Items.GOLD_INGOT) ? e.getItem().getCount() : 0).sum();

            helper.assertTrue(iron + droppedIron == 10, "iron must not vanish: net=" + iron + " drop=" + droppedIron);
            helper.assertTrue(gold + droppedGold == 5, "gold must not vanish: net=" + gold + " drop=" + droppedGold);
            helper.assertTrue(chests >= 1, "chest block itself must be harvested into network");
            helper.succeed();
        });
    }

    /** Breaking one half of a double chest: items must not vanish. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusDoubleChest(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos leftPos = front;
        BlockPos rightPos = front.east();
        helper.setBlock(leftPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(rightPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));

        ChestBlockEntity leftChest = (ChestBlockEntity) helper.getBlockEntity(leftPos);
        leftChest.setItem(0, new ItemStack(Items.DIAMOND, 4));

        helper.runAfterDelay(90, () -> {
            helper.assertBlockNotPresent(Blocks.CHEST, leftPos);
            GolemNetwork network = net(helper, bay);
            long diamonds = network.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND)));
            int droppedDiamonds = helper.getEntities(EntityType.ITEM).stream()
                    .mapToInt(e -> e.getItem().is(Items.DIAMOND) ? e.getItem().getCount() : 0).sum();
            helper.assertTrue(diamonds + droppedDiamonds == 4, "diamonds must not vanish: net=" + diamonds + " drop=" + droppedDiamonds);
            helper.succeed();
        });
    }

    /** Breaking a two-block door: both halves clear and drop goes to network. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusTwoBlockBedOrDoor(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos lower = front;
        BlockPos upper = front.above();
        helper.setBlock(lower, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));
        helper.setBlock(upper, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT));

        helper.runAfterDelay(80, () -> {
            helper.assertBlockNotPresent(Blocks.OAK_DOOR, lower);
            helper.assertBlockNotPresent(Blocks.OAK_DOOR, upper);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.OAK_DOOR))) >= 1,
                    "door dropped into network");
            helper.succeed();
        });
    }

    /** Crop with grownOnly OFF mines immature crop; with grownOnly ON it ignores it until mature. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusCropGrownToggle(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = busAt(helper, bus);

        // grownOnly is OFF by default (be.menuData().get(4) == 0)
        helper.setBlock(front.below(), Blocks.FARMLAND);
        helper.setBlock(front, ((CropBlock) Blocks.WHEAT).getStateForAge(2));

        helper.runAfterDelay(80, () -> {
            // When grownOnly is OFF, immature crop is broken
            helper.assertBlockNotPresent(Blocks.WHEAT, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.WHEAT_SEEDS))) >= 1,
                    "seeds harvested with grownOnly OFF");

            // Turn grownOnly ON (index 4)
            be.menuData().set(4, 1);
            helper.setBlock(front, ((CropBlock) Blocks.WHEAT).getStateForAge(2));
        });

        helper.runAfterDelay(150, () -> {
            // When grownOnly is ON, immature crop must stay
            helper.assertBlockPresent(Blocks.WHEAT, front);
            // Positive control: mature crop MUST be mined when grownOnly is ON
            helper.setBlock(front, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
            be.wake();
            helper.succeedWhen(() -> {
                helper.assertBlockNotPresent(Blocks.WHEAT, front);
                helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.WHEAT))) >= 1,
                        "positive control: mature crop harvested");
            });
        });
    }

    /** When network storage is full and cannot fit drops, the bus waits and block stays intact. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusFullNetworkWaits(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);

        // Fill network completely (1k disk holds 1024 items max of 1 type)
        GolemNetwork network = net(helper, bay);
        network.insertItem(new ItemStack(Items.DIRT, 64), false);
        for (int i = 0; i < 15; i++) {
            network.insertItem(new ItemStack(Items.DIRT, 64), false);
        }

        helper.runAfterDelay(80, () -> {
            // The stone should stay because cobblestone cannot fit into the disk
            helper.assertBlockPresent(Blocks.STONE, front);
            // Positive control: extract dirt to make room, now bus must mine stone
            net(helper, bay).extractItem(new ItemStack(Items.DIRT), 64, false);
            assertMinesStone(helper, bay, bus, front);
        });
    }

    /** Silk Touch pickaxe harvests stone as Stone, not Cobblestone. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusSilkTouch(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = busAt(helper, bus);

        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var silkTouch = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        pick.enchant(silkTouch, 1);
        be.getTool().setItem(0, pick);

        helper.runAfterDelay(70, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.STONE))) == 1,
                    "silk touch gives real stone");
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 0,
                    "no cobblestone from silk touch");
            helper.succeed();
        });
    }

    /** Fortune pickaxe on coal ore yields multiple coal drops. */
    @GameTest(template = T, timeoutTicks = 350)
    public static void annihilationBusFortune(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.COAL_ORE);
        var be = busAt(helper, bus);

        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var fortune = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        pick.enchant(fortune, 3);
        be.getTool().setItem(0, pick);

        int[] mined = {0};
        final int TARGET = 12;
        helper.succeedWhen(() -> {
            if (helper.getBlockState(front).isAir()) {
                if (mined[0] < TARGET) {
                    mined[0]++;
                    if (mined[0] < TARGET) {
                        helper.setBlock(front, Blocks.COAL_ORE);
                        be.wake();
                    }
                }
            }
            helper.assertTrue(mined[0] == TARGET, "must mine all target ores");
            helper.assertBlockNotPresent(Blocks.COAL_ORE, front);
            long coal = net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COAL)));
            helper.assertTrue(coal >= 1, "coal was harvested into network, got: " + coal);
            helper.assertTrue(coal > TARGET, "Fortune III yielded bonus drops: " + coal + " coal from " + TARGET + " ores");
        });
    }

    /** Efficiency enchantment increases mining speed: block is mined in fewer ticks. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusEfficiency(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.IRON_BLOCK);

        var be = busAt(helper, bus);
        ItemStack efficient = new ItemStack(Items.DIAMOND_PICKAXE);
        var eff = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
        efficient.enchant(eff, 5);
        be.getTool().setItem(0, efficient);

        // Efficiency V breaks iron block in ~5 ticks, well before tick 12
        helper.runAfterDelay(12, () -> {
            helper.assertBlockPresent(Blocks.AIR, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.IRON_BLOCK))) >= 1,
                    "iron block mined quickly with Efficiency V");

            // Now reset with normal un-enchanted pickaxe
            helper.setBlock(front, Blocks.IRON_BLOCK);
            be.getTool().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        });

        // 10 ticks later (at tick 22), normal pickaxe has not finished yet (needs ~19 ticks)
        helper.runAfterDelay(22, () -> {
            helper.assertBlockPresent(Blocks.IRON_BLOCK, front);
        });

        // By tick 35 (23 ticks later), normal pickaxe has finished
        helper.runAfterDelay(35, () -> {
            helper.assertBlockPresent(Blocks.AIR, front);
            helper.succeed();
        });
    }

    /** Wrong tool (pickaxe on wood log): mines slower than axe, but still mines. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusWrongTool(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.OAK_LOG);
        var be = busAt(helper, bus);
        be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));

        helper.runAfterDelay(90, () -> {
            helper.assertBlockNotPresent(Blocks.OAK_LOG, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 1,
                    "log was harvested into network even with pickaxe");
            helper.succeed();
        });
    }

    /** A tool with 1 durability remaining without Autocraft Module must NOT break. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusDurabilityWithoutAutocraft(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = busAt(helper, bus);

        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(pick.getMaxDamage() - 1); // exactly 1 durability left
        be.getTool().setItem(0, pick);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            ItemStack held = be.getTool().getItem(0);
            helper.assertTrue(!held.isEmpty() && held.getDamageValue() == pick.getMaxDamage() - 1,
                    "tool preserved at 1 durability");
            // Positive control: with a fresh tool, it must mine stone
            be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            assertMinesStone(helper, bay, bus, front);
        });
    }

    /** Non-tool item (a stick) in the slot cannot harvest stone, but can mine dirt slowly. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusNonToolInSlot(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = busAt(helper, bus);
        be.getTool().setItem(0, new ItemStack(Items.STICK));

        helper.runAfterDelay(50, () -> {
            // Cannot mine stone with a stick (requires correct tool for drops)
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.setBlock(front, Blocks.DIRT);
        });

        helper.runAfterDelay(120, () -> {
            // Can mine dirt with a stick
            helper.assertBlockNotPresent(Blocks.DIRT, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.DIRT))) == 1,
                    "dirt was mined with a stick");
            // Positive control: with a real tool in slot, stone can now be mined
            be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            assertMinesStone(helper, bay, bus, front);
        });
    }

    /** Empty allow list stops the bus in all three modes (LEFT, RIGHT, ATTACK). */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusEmptyAllowListStopsAllModes(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = busAt(helper, bus);

        // Set allow list (denyList = false, index 3 = 0) with empty filter slots
        be.menuData().set(3, 0);

        // 1. LEFT mode on Stone
        helper.setBlock(front, Blocks.STONE);
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);

            // 2. RIGHT mode on Oak Sapling with bone meal
            be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
            be.getTool().setItem(0, new ItemStack(Items.BONE_MEAL, 10));
            helper.setBlock(front, Blocks.OAK_SAPLING);
        });

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(be.getTool().getItem(0).getCount() == 10, "bone meal not used with empty allow list");

            // 3. ATTACK mode on Pig
            be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
            be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            helper.setBlock(front.below(), Blocks.STONE);
            helper.setBlock(front, Blocks.AIR);
            var pig = helper.spawnWithNoFreeWill(EntityType.PIG, front);

            helper.runAfterDelay(40, () -> {
                helper.assertTrue(pig.isAlive(), "pig not attacked with empty allow list");
                // Positive control: switch to deny list mode (allow all with empty filter), pig must now die
                be.menuData().set(3, 1);
                be.wake();
                helper.succeedWhen(() -> {
                    helper.assertTrue(!pig.isAlive(), "positive control: bus attacks and kills pig once allow list restriction is lifted");
                });
            });
        });
    }
}
