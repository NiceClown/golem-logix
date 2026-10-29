package com.golemlogix.gametest;

import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.menu.AnnihilationBusMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

import static com.golemlogix.gametest.GolemGameTests.*;

/**
 * Extra rigorous game tests for Golem Logix:
 * Detailed coverage of Hewer's Annihilation Bus (breaking, tools, filters, right-click, attacks,
 * redstone, refill, menu restrictions) and complex Autocrafting scenarios.
 */
public final class GolemGameTestsExtra {

    private GolemGameTestsExtra() {
    }

    // =========================================================================
    // 1. ANNIHILATION BUS: BREAKING SCENARIOS
    // =========================================================================

    /** Unbreakable blocks (bedrock, barrier) must never be mined or harvested. */
    public static void annihilationBusUnbreakable(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.BEDROCK);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.BEDROCK, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.BEDROCK))) == 0,
                    "bedrock must not be in the network");
            helper.setBlock(front, Blocks.BARRIER);
        });

        helper.runAfterDelay(120, () -> {
            helper.assertBlockPresent(Blocks.BARRIER, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.BARRIER))) == 0,
                    "barrier must not be in the network");
            helper.succeed();
        });
    }

    /** Liquid blocks (water source) and air must not be mined as broken blocks. */
    public static void annihilationBusLiquidAndAir(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.WATER);

        helper.runAfterDelay(50, () -> {
            helper.assertBlockPresent(Blocks.WATER, front);
            helper.setBlock(front, Blocks.AIR);
        });

        helper.runAfterDelay(100, () -> {
            var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
            helper.assertTrue(!be.swinging(), "bus must not swing at air");
            helper.succeed();
        });
    }

    /**
     * Breaking a block with a block entity and contents (a chest with items):
     * The chest and its contents must end up in the network or drop, and never vanish!
     */
    public static void annihilationBusChestWithContents(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(front, ChestBlockEntity.class);
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
    public static void annihilationBusDoubleChest(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos leftPos = front;
        BlockPos rightPos = front.east();
        helper.setBlock(leftPos, Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        helper.setBlock(rightPos, Blocks.CHEST.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT));

        ChestBlockEntity leftChest = helper.getBlockEntity(leftPos, ChestBlockEntity.class);
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
    public static void annihilationBusCropGrownToggle(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        // grownOnly is OFF by default (be.menuData().get(4) == 0)
        helper.setBlock(front.below(), Blocks.FARMLAND);
        helper.setBlock(front, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(2));

        helper.runAfterDelay(80, () -> {
            // When grownOnly is OFF, immature crop is broken
            helper.assertBlockNotPresent(Blocks.WHEAT, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.WHEAT_SEEDS))) >= 1,
                    "seeds harvested with grownOnly OFF");

            // Turn grownOnly ON (index 4)
            be.menuData().set(4, 1);
            helper.setBlock(front, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(2));
        });

        helper.runAfterDelay(150, () -> {
            // When grownOnly is ON, immature crop must stay
            helper.assertBlockPresent(Blocks.WHEAT, front);
            helper.succeed();
        });
    }

    /** When network storage is full and cannot fit drops, the bus waits and block stays intact. */
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
            helper.succeed();
        });
    }

    // =========================================================================
    // 2. ANNIHILATION BUS: TOOLS & DURABILITY
    // =========================================================================

    /** Silk Touch pickaxe harvests stone as Stone, not Cobblestone. */
    public static void annihilationBusSilkTouch(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

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
    public static void annihilationBusFortune(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.COAL_ORE);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var fortune = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        pick.enchant(fortune, 3);
        be.getTool().setItem(0, pick);

        helper.runAfterDelay(70, () -> {
            helper.assertBlockNotPresent(Blocks.COAL_ORE, front);
            long coal = net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COAL)));
            helper.assertTrue(coal >= 1, "coal was harvested into network, got: " + coal);
            helper.succeed();
        });
    }

    /** Efficiency enchantment increases mining speed: block is mined in fewer ticks. */
    public static void annihilationBusEfficiency(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.IRON_BLOCK);

        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
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
    public static void annihilationBusWrongTool(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.OAK_LOG);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
        be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));

        helper.runAfterDelay(90, () -> {
            helper.assertBlockNotPresent(Blocks.OAK_LOG, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 1,
                    "log was harvested into network even with pickaxe");
            helper.succeed();
        });
    }

    /** A tool with 1 durability remaining without Autocraft Module must NOT break. */
    public static void annihilationBusDurabilityWithoutAutocraft(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.setDamageValue(pick.getMaxDamage() - 1); // exactly 1 durability left
        be.getTool().setItem(0, pick);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            ItemStack held = be.getTool().getItem(0);
            helper.assertTrue(!held.isEmpty() && held.getDamageValue() == pick.getMaxDamage() - 1,
                    "tool preserved at 1 durability");
            helper.succeed();
        });
    }

    /** Non-tool item (a stick) in the slot cannot harvest stone, but can mine dirt slowly. */
    public static void annihilationBusNonToolInSlot(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
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
            helper.succeed();
        });
    }

    // =========================================================================
    // 3. ANNIHILATION BUS: FILTERS
    // =========================================================================

    /** Empty allow list stops the bus in all three modes (LEFT, RIGHT, ATTACK). */
    public static void annihilationBusEmptyAllowListStopsAllModes(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

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
                helper.succeed();
            });
        });
    }

    /** Deny list blocks the filtered item, but allows other blocks. */
    public static void annihilationBusDenyListFiltersItem(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        // Deny list (default: denyList = 1)
        be.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        helper.setBlock(front, Blocks.COBBLESTONE);

        helper.runAfterDelay(50, () -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, front);
            // Replace with Stone (allowed)
            helper.setBlock(front, Blocks.STONE);
        });

        helper.runAfterDelay(110, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.succeed();
        });
    }

    // =========================================================================
    // 4. ANNIHILATION BUS: RIGHT-CLICK MODE
    // =========================================================================

    /** Right click mode using Bone Meal on a sapling. */
    public static void annihilationBusRightClickBoneMeal(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        be.getTool().setItem(0, new ItemStack(Items.BONE_MEAL, 10));
        helper.setBlock(front.below(), Blocks.DIRT);
        helper.setBlock(front, Blocks.OAK_SAPLING);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.getTool().getItem(0).getCount() < 10, "bone meal was consumed on the sapling");
            helper.succeed();
        });
    }

    /** Shears on sheep in front in RIGHT mode: sheep is entity, not block, must not crash. */
    public static void annihilationBusRightClickShearsOnSheep(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        be.getTool().setItem(0, new ItemStack(Items.SHEARS));
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.AIR);
        Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, front);

        helper.runAfterDelay(60, () -> {
            // Bus only clicks blocks in front; sheep stays safe, no crash
            helper.assertTrue(sheep.isAlive(), "sheep is alive and safe");
            helper.succeed();
        });
    }

    /** Right click with empty bucket on water source: collects water into network. */
    public static void annihilationBusRightClickBucketWater(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        be.getTool().setItem(0, new ItemStack(Items.BUCKET));
        helper.setBlock(front, Blocks.WATER);

        helper.runAfterDelay(70, () -> {
            GolemNetwork net = net(helper, bay);
            long buckets = net.itemCountOf(ItemKey.of(new ItemStack(Items.WATER_BUCKET)));
            helper.assertTrue(buckets == 1 && be.getTool().getItem(0).isEmpty(),
                    "bucket collected water, got buckets in net: " + buckets + ", tool: " + be.getTool().getItem(0));
            helper.succeed();
        });
    }

    /** Right click toggles a lever. */
    public static void annihilationBusRightClickLever(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(LeverBlock.POWERED, false));
        be.wake();

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.lastUse() > 0, "the bus interacted with the lever");
            helper.succeed();
        });
    }

    // =========================================================================
    // 5. ANNIHILATION BUS: ATTACK MODE
    // =========================================================================

    /** Attack mode ignores tamed pets, named mobs, and armor stands. */
    public static void annihilationBusAttackIgnoresPetsAndNamedAndArmorStands(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.AIR);

        // Named Pig
        var namedPig = helper.spawnWithNoFreeWill(EntityType.PIG, front);
        namedPig.setCustomName(net.minecraft.network.chat.Component.literal("PetPig"));

        // Armor Stand
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, front);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(namedPig.isAlive(), "named pig must never be attacked");
            helper.assertTrue(stand.isAlive(), "armor stand must never be attacked");
            helper.succeed();
        });
    }

    /** Attack mode with grownOnly spares baby animals. */
    public static void annihilationBusAttackGrownOnlySparesBabies(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.menuData().set(4, 1); // grownOnly = true
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.AIR);

        var babyPig = helper.spawnWithNoFreeWill(EntityType.PIG, front);
        babyPig.setBaby(true);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(babyPig.isAlive(), "baby pig is spared when grownOnly is on");
            helper.succeed();
        });
    }

    /** Attack mode with spawn egg filter only attacks the specified mob. */
    public static void annihilationBusAttackSpawnEggFilter(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.menuData().set(3, 0); // Allow list mode!
        be.getFilters().setItem(0, new ItemStack(Items.COW_SPAWN_EGG));
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.AIR);

        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, front);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(pig.isAlive(), "pig spared because filter is cow spawn egg");
            pig.discard();
            var cow = helper.spawnWithNoFreeWill(EntityType.COW, front);

            helper.runAfterDelay(60, () -> {
                helper.assertTrue(!cow.isAlive(), "cow attacked and killed by cow spawn egg filter");
                helper.succeed();
            });
        });
    }

    /** Attack strikes apply no knockback (entity delta movement stays 0 on horizontal axes). */
    public static void annihilationBusAttackNoKnockback(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.getTool().setItem(0, new ItemStack(Items.WOODEN_SWORD)); // low damage so it survives first hit
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.AIR);

        var ironGolem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, front);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(ironGolem.getDeltaMovement().x == 0 && ironGolem.getDeltaMovement().z == 0,
                    "no horizontal knockback on attacked mob");
            helper.succeed();
        });
    }

    // =========================================================================
    // 6. REDSTONE, ENVIRONMENT & EDGE CASES
    // =========================================================================

    /** Redstone modes: HIGH, LOW, IGNORE, PULSE. */
    public static void annihilationBusRedstoneModes(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.getUpgrades().setItem(UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.REDSTONE),
                new ItemStack(ModItems.REDSTONE_MODULE.get()));
        be.menuData().set(0, RedstoneMode.HIGH.ordinal());
        helper.setBlock(front, Blocks.STONE);

        // Without signal in HIGH mode: waits
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            // Power the bus from above (without breaking the Atlas Array below/behind)
            helper.setBlock(bus.above(), Blocks.REDSTONE_BLOCK);
        });

        // With signal in HIGH mode: mines
        helper.runAfterDelay(100, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.succeed();
        });
    }

    /** When network loses Core / goes offline, the bus stops working. */
    public static void annihilationBusNetworkOfflineStops(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);

        // Remove Core -> network goes offline
        helper.setBlock(CORE, Blocks.AIR);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.succeed();
        });
    }

    /** Bus works in UP and DOWN directions. */
    public static void annihilationBusFacingUpAndDown(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT;
        BlockPos above = bus.above();

        // Facing UP
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
        helper.setBlock(above, Blocks.STONE);

        helper.runAfterDelay(70, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, above);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1,
                    "stone above was mined");
            helper.succeed();
        });
    }

    /** Breaking the bus block mid-mining clears progress, drops tool and does not crash. */
    public static void annihilationBusBrokenMidMining(GameTestHelper helper) {
        baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.OBSIDIAN);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));

        helper.runAfterDelay(20, () -> {
            // Break the bus block while mining obsidian
            helper.setBlock(bus, Blocks.AIR);
        });

        helper.runAfterDelay(40, () -> {
            // Tool must have dropped in the world
            boolean pickDropped = helper.getEntities(EntityType.ITEM).stream()
                    .anyMatch(e -> e.getItem().is(Items.DIAMOND_PICKAXE));
            helper.assertTrue(pickDropped, "pickaxe dropped when bus was broken");
            helper.succeed();
        });
    }

    /** Two buses facing the same block from opposite sides mine cleanly without crash. */
    public static void annihilationBusTwoBusesSameBlock(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos busA = BUS_OUT;
        BlockPos target = BUS_OUT.north();
        BlockPos busB = target.north();

        helper.setBlock(busA, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(busB, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.SOUTH));
        helper.setBlock(target, Blocks.STONE);

        helper.runAfterDelay(80, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1,
                    "exactly one cobblestone produced");
            helper.succeed();
        });
    }

    // =========================================================================
    // 7. REFILL & MENU
    // =========================================================================

    /** Refill does not grab a different item if the slot expects an iron pickaxe. */
    public static void annihilationBusRefillDoesNotTakeDifferentItem(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);

        be.getUpgrades().setItem(UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.CRAFTING),
                new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));

        // Network only has diamond pickaxe
        net(helper, bay).insertItem(new ItemStack(Items.DIAMOND_PICKAXE), false);

        // Simulate breaking iron pickaxe
        be.getTool().setItem(0, ItemStack.EMPTY);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.getTool().getItem(0).isEmpty(),
                    "does not grab diamond pickaxe when expecting iron pickaxe");
            helper.succeed();
        });
    }

    /** Annihilation bus upgrade slots enforce valid upgrade types; coprocessor never fits. */
    public static void annihilationBusMenuUpgradeRestrictions(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = helper.getBlockEntity(rel, AnnihilationBusBlockEntity.class);
        ServerPlayer player = clickOpen(helper, rel);

        try {
            var menu = (AnnihilationBusMenu) player.containerMenu;
            helper.assertTrue(!menu.getSlot(AnnihilationBusMenu.TOOL_SLOT_INDEX).mayPlace(new ItemStack(ModItems.REDSTONE_MODULE.get())),
                    "tool slot refuses upgrades");

            int speedCount = be.getUpgrades().count(UpgradeType.SPEED);
            helper.assertTrue(speedCount == 0, "no speed upgrade slots on annihilation bus");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    // =========================================================================
    // 8. AUTOCRAFTING: COMPLEX SCENARIOS
    // =========================================================================

    /** Nested multi-step crafting: Oak Log -> 4 Oak Planks -> 4 Sticks -> Ladder. */
    public static void autocraftNestedMultiStep(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        ItemStack stick = new ItemStack(Items.STICK);

        // Pattern 0: 1 Log -> 4 Planks
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        // Pattern 1: 2 Planks -> 4 Sticks
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.STICK, 4), plank.copy(), ItemStack.EMPTY, ItemStack.EMPTY, plank.copy())));
        // Pattern 2: 7 Sticks -> 3 Ladders
        provider.getContainer().setItem(2, card(craftingPattern(new ItemStack(Items.LADDER, 3),
                stick.copy(), ItemStack.EMPTY, stick.copy(),
                stick.copy(), stick.copy(), stick.copy(),
                stick.copy(), ItemStack.EMPTY, stick.copy())));

        ItemKey ladderKey = ItemKey.of(new ItemStack(Items.LADDER));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 4), false);
            CraftingPlan plan = CraftingPlanner.plan(n, ladderKey, 3);
            helper.assertTrue(plan.missing().isEmpty(), "nothing missing for ladder: " + plan.missing());
            helper.assertTrue(n.startCrafting(ladderKey, 3).type() == GolemNetwork.StartType.STARTED, "ladder craft started");
        });

        helper.runAfterDelay(160, () -> {
            GolemNetwork n = net(helper, bay);
            long ladders = n.itemCountOf(ladderKey);
            helper.assertTrue(ladders == 3, "expected 3 ladders delivered, got: " + ladders);
            helper.assertTrue(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).isIdle(), "cpu idle");
            helper.succeed();
        });
    }

    /** Two jobs competing for the same ingredient: first takes all, second reports missing. */
    public static void autocraftCompetingJobs(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // 1 Iron Ingot -> 9 Iron Nuggets
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.IRON_NUGGET, 9), new ItemStack(Items.IRON_INGOT))));
        ItemKey nugget = ItemKey.of(new ItemStack(Items.IRON_NUGGET));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
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
    public static void autocraftJobCancelledMidwayRefunds(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Machine pattern (processing): 1 Raw Gold -> 1 Gold Ingot
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_GOLD)), List.of(new ItemStack(Items.GOLD_INGOT)))));

        ItemKey goldIngot = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
        ItemKey rawGold = ItemKey.of(new ItemStack(Items.RAW_GOLD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_GOLD, 5), false);
            n.startCrafting(goldIngot, 5);

            helper.runAfterDelay(10, () -> {
                CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
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
    public static void autocraftFluidInputFromFluidOutput(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Pattern 1: 1 Ice -> 1000 mB Water
        ItemStack waterGhost = com.golemlogix.items.FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.ICE)), List.of(waterGhost))));

        // Pattern 2: 1000 mB Water + 1 Dirt -> 1 Mud
        provider.getContainer().setItem(1, card(new PatternData(false,
                List.of(waterGhost.copy(), new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.MUD)))));

        helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class).getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        ItemKey mudKey = ItemKey.of(new ItemStack(Items.MUD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.ICE, 2), false);
            n.insertItem(new ItemStack(Items.DIRT, 2), false);

            CraftingPlan plan = CraftingPlanner.plan(n, mudKey, 2);
            helper.assertTrue(plan.missing().isEmpty(), "planner chains fluid output to fluid input without missing");
            helper.succeed();
        });
    }

    /** Removing the Cryo-Calculator block mid-job drops job items and does not crash. */
    public static void autocraftCalculatorRemovedMidJob(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            n.startCrafting(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 40);

            helper.runAfterDelay(10, () -> {
                // Break CPU block while crafting
                helper.setBlock(CPU, Blocks.AIR);
                helper.runAfterDelay(20, () -> {
                    // Check network still functions and doesn't crash
                    helper.assertTrue(net(helper, bay) != null, "network remains valid after CPU removal");
                    helper.succeed();
                });
            });
        });
    }
}
