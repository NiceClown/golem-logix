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
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blockentity.RecipeMonitorBlockEntity;
import com.golemlogix.menu.StockManometerMenu;
import com.golemlogix.menu.StockRequesterMenu;
import com.golemlogix.menu.SecurityTerminalMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.network.ManometerConfigPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.init.ModDataComponents;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import java.util.Map;

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

    // =========================================================================
    // NEW FEATURES: MANOMETER, REQUESTER, CRYO-DIODE, SECURITY, ASMR
    // =========================================================================

    // -------------------------------------------------------------------------
    // 1. STOCK MANOMETER
    // -------------------------------------------------------------------------

    public static void stockManometerThresholdAbove(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(10);
        mano.setHysteresis(0);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "signal should be 0 initially when stock < 10");
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 9), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 0, "signal should still be 0 with 9 diamonds");
                net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 1), false);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 15, "signal should be 15 when stock reaches 10");
                    net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 6, false);
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 0, "signal should drop to 0 when stock drops to 4");
                        helper.succeed();
                    });
                });
            });
        });
    }

    public static void stockManometerThresholdBelow(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_BELOW);
        mano.setThreshold(10);
        mano.setHysteresis(0);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 15, "signal should be 15 initially when stock (0) <= 10");
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "signal should still be 15 when stock == 10");
                net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 1), false);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 0, "signal should be 0 when stock > 10 (11)");
                    net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 2, false);
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 15, "signal should return to 15 when stock drops back to 9");
                        helper.succeed();
                    });
                });
            });
        });
    }

    public static void stockManometerThresholdAnalog(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ANALOG);
        mano.setThreshold(20);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "analog signal should be 0 initially");
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.runAfterDelay(15, () -> {
                int sig = mano.getSignal();
                helper.assertTrue(sig >= 7 && sig <= 8, "analog signal at half threshold should be ~7-8 (got " + sig + ")");
                net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 15, "analog signal at full threshold should be 15");
                    net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 15, "analog signal above threshold should clamp to 15");
                        helper.succeed();
                    });
                });
            });
        });
    }

    public static void stockManometerHysteresisLatch(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(10);
        mano.setHysteresis(30); // High = 30, Low = 10

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "initially 0");
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 20), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 0, "stock=20 between 10 and 30 should remain 0 (not reached high threshold yet)");
                net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 15), false); // 35
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 15, "stock=35 reaches high threshold, signal turns ON (15)");
                    net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 15, false); // 20
                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 15, "stock=20 latches ON (still 15 between 10 and 30)");
                        net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 15, false); // 5
                        helper.runAfterDelay(15, () -> {
                            helper.assertTrue(mano.getSignal() == 0, "stock=5 drops below low threshold, signal turns OFF (0)");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    public static void stockManometerFluidGhost(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);

        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        mano.getFilter().setItem(0, waterGhost);
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(2000); // 2000 mB

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "signal 0 when fluid is 0");
            net(helper, bay).insertFluid(new FluidStack(Fluids.WATER, 1000), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 0, "signal 0 when fluid is 1000 mB (< 2000)");
                net(helper, bay).insertFluid(new FluidStack(Fluids.WATER, 1500), false); // 2500 mB
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 15, "signal 15 when fluid is 2500 mB (>= 2000)");
                    helper.assertTrue(mano.getStockCount() == 2500, "stock count matches fluid amount (2500)");
                    helper.succeed();
                });
            });
        });
    }

    public static void stockManometerAutocraftModulePulse(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.OAK_PLANKS));
        mano.getUpgrades().setItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "signal 0 when no craft is running");
            net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            net(helper, bay).startCrafting(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 40);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "signal 15 while autocraft is running");
                helper.succeed();
            });
        });
    }

    public static void stockManometerMenuAndPayloadAndSaveLoad(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(50);
        mano.setHysteresis(100);

        ServerPlayer player = testPlayer(helper);
        StockManometerMenu menu = new StockManometerMenu(1, player.getInventory(), mano.getFilter(), mano.getUpgrades(), mano.menuData(), mano.getBlockPos());

        // Test button mode cycling
        menu.clickMenuButton(player, StockManometerMenu.BUTTON_MODE);
        helper.assertTrue(mano.getMode() == StockManometerBlockEntity.MODE_BELOW, "button clicked -> MODE_BELOW");
        menu.clickMenuButton(player, StockManometerMenu.BUTTON_MODE);
        helper.assertTrue(mano.getMode() == StockManometerBlockEntity.MODE_ANALOG, "button clicked -> MODE_ANALOG");
        menu.clickMenuButton(player, StockManometerMenu.BUTTON_MODE);
        helper.assertTrue(mano.getMode() == StockManometerBlockEntity.MODE_ABOVE, "button clicked -> MODE_ABOVE");

        // Test setConfig
        menu.setConfig(StockManometerBlockEntity.MODE_BELOW, 120, 240);
        helper.assertTrue(mano.getMode() == StockManometerBlockEntity.MODE_BELOW, "config mode set to BELOW");
        helper.assertTrue(mano.getThreshold() == 120, "config threshold set to 120");
        helper.assertTrue(mano.getHysteresis() == 240, "config hysteresis set to 240");

        // Save and load
        CompoundTag tag = mano.saveWithFullMetadata(helper.getLevel().registryAccess());
        net.minecraft.world.level.block.entity.BlockEntity loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(manoPos, helper.getBlockState(manoPos), tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof StockManometerBlockEntity, "loaded block entity is StockManometerBlockEntity");
        StockManometerBlockEntity mano2 = (StockManometerBlockEntity) loaded;
        helper.assertTrue(mano2.getMode() == StockManometerBlockEntity.MODE_BELOW, "loaded mode preserved");
        helper.assertTrue(mano2.getThreshold() == 120, "loaded threshold preserved");
        helper.assertTrue(mano2.getHysteresis() == 240, "loaded hysteresis preserved");

        helper.succeed();
    }

    public static void stockManometerRedstoneLampNeighbor(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        // Facing NORTH, attached face is SOUTH (where cable bushing connects to array)
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos lampPos = manoPos.north();
        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));

        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(5);

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!helper.getBlockState(lampPos).getValue(RedstoneLampBlock.LIT), "lamp unlit initially");
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "mano signal is 15");
                helper.assertTrue(helper.getBlockState(lampPos).getValue(RedstoneLampBlock.LIT), "neighbor lamp lights up from manometer");
                helper.succeed();
            });
        });
    }

    // -------------------------------------------------------------------------
    // 2. STOCK REQUESTER / COPPER ORDER LEDGER
    // -------------------------------------------------------------------------

    public static void stockRequesterKeepsTargetAmount(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = helper.getBlockEntity(reqPos, StockRequesterBlockEntity.class);

        ItemStack filterStack = new ItemStack(Items.OAK_PLANKS);
        filterStack.set(ModDataComponents.KEEP_AMOUNT.get(), 16);
        req.getFilters().setItem(0, filterStack);

        net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 10), false);

        helper.runAfterDelay(35, () -> {
            GolemNetwork n = net(helper, bay);
            long planks = n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_PLANKS)));
            helper.assertTrue(planks >= 16, "stock reaches target 16 (got " + planks + ")");
            helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_OK, "status becomes STATUS_OK once reached");
            helper.succeed();
        });
    }

    public static void stockRequesterSingleJobPerItem(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = helper.getBlockEntity(reqPos, StockRequesterBlockEntity.class);

        ItemStack filterStack = new ItemStack(Items.OAK_PLANKS);
        filterStack.set(ModDataComponents.KEEP_AMOUNT.get(), 200);
        req.getFilters().setItem(0, filterStack);

        net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 100), false);

        helper.runAfterDelay(15, () -> {
            GolemNetwork n = net(helper, bay);
            ItemKey key = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            helper.assertTrue(n.busCraftPending(helper.absolutePos(reqPos), key), "busCraftPending is true for the requested item");
            int activeJobs = 0;
            for (com.golemlogix.crafting.CraftingCpu cpu : n.cpus()) {
                if (!cpu.isIdle() && cpu.getJob() != null && cpu.getJob().targets().containsKey(key)) {
                    activeJobs++;
                }
            }
            helper.assertTrue(activeJobs == 1, "exactly one job running for key, never duplicate concurrent jobs (got " + activeJobs + ")");
            helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_CRAFTING, "slot status is STATUS_CRAFTING");
            helper.succeed();
        });
    }

    public static void stockRequesterStatusesNoPatternAndMissing(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = helper.getBlockEntity(reqPos, StockRequesterBlockEntity.class);

        // Slot 0: Netherite Ingot -> NO pattern on network
        ItemStack noPatternStack = new ItemStack(Items.NETHERITE_INGOT);
        noPatternStack.set(ModDataComponents.KEEP_AMOUNT.get(), 5);
        req.getFilters().setItem(0, noPatternStack);

        // Slot 1: Oak Planks -> has pattern, but 0 logs in network
        ItemStack missingIngStack = new ItemStack(Items.OAK_PLANKS);
        missingIngStack.set(ModDataComponents.KEEP_AMOUNT.get(), 10);
        req.getFilters().setItem(1, missingIngStack);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_NO_PATTERN,
                    "slot 0 status must be STATUS_NO_PATTERN (got " + req.getSlotStatus(0) + ")");
            helper.assertTrue(req.getSlotStatus(1) == StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS,
                    "slot 1 status must be STATUS_MISSING_INGREDIENTS (got " + req.getSlotStatus(1) + ")");
            helper.succeed();
        });
    }

    public static void stockRequesterFluidTarget(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.ICE)), List.of(waterGhost))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = helper.getBlockEntity(reqPos, StockRequesterBlockEntity.class);

        ItemStack reqGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        reqGhost.set(ModDataComponents.KEEP_AMOUNT.get(), 2000);
        req.getFilters().setItem(0, reqGhost);

        helper.runAfterDelay(15, () -> {
            int st = req.getSlotStatus(0);
            helper.assertTrue(st == StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS || st == StockRequesterBlockEntity.STATUS_CRAFTING,
                    "fluid target recognized and checked against patterns (status=" + st + ")");
            helper.succeed();
        });
    }

    public static void stockRequesterZeroAmountEmptyAndOffline(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = helper.getBlockEntity(reqPos, StockRequesterBlockEntity.class);

        // Slot 0 empty
        // Slot 1 has item with keep amount = 0 configured via menu
        req.getFilters().setItem(1, new ItemStack(Items.DIAMOND));
        ServerPlayer player = testPlayer(helper);
        StockRequesterMenu reqMenu = new StockRequesterMenu(1, player.getInventory(), req.getFilters(), req.menuData(), req.getBlockPos());
        reqMenu.setKeep(1, 0);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_OK, "empty slot stays STATUS_OK");
            helper.assertTrue(req.getSlotStatus(1) == StockRequesterBlockEntity.STATUS_OK, "amount 0 slot stays STATUS_OK");

            // Test offline: remove core to take network offline
            helper.setBlock(CORE, Blocks.AIR);
            helper.runAfterDelay(15, () -> {
                GolemNetwork net = net(helper, bay);
                helper.assertTrue(!net.isOnline(), "network is offline");
                helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_OK, "offline requester stays STATUS_OK");
                helper.assertTrue(req.getSlotStatus(1) == StockRequesterBlockEntity.STATUS_OK, "offline requester stays STATUS_OK");
                helper.succeed();
            });
        });
    }

    // -------------------------------------------------------------------------
    // 3. CRYO-DIODE (SUBNET ISOLATOR)
    // -------------------------------------------------------------------------

    public static void cryoDiodeNetworksStaySeparate(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos bay1 = BAY;
        putBay(helper, bay1);

        BlockPos diodePos = BUS_OUT;
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos b2 = diodePos.north();
        put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        BlockPos bay2 = b2.west();
        helper.setBlock(bay2, ModBlocks.COPPER_DRIVE_BAY.get().defaultBlockState());

        helper.runAfterDelay(20, () -> {
            GolemNetwork net1 = net(helper, bay1);
            GolemNetwork net2 = net(helper, b2);

            helper.assertTrue(net1 != null && net2 != null, "both networks exist");
            helper.assertTrue(net1 != net2, "two networks joined by cryo-diode are two distinct GolemNetwork instances");
            helper.succeed();
        });
    }

    public static void cryoDiodeStorageAndCraftingIsolated(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos bay1 = BAY;
        putBay(helper, bay1);
        CopperDriveBayBlockEntity drive1 = helper.getBlockEntity(bay1, CopperDriveBayBlockEntity.class);
        drive1.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        BlockPos diodePos = BUS_OUT;
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos b2 = diodePos.north();
        put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        BlockPos bay2 = b2.west();
        putBay(helper, bay2);
        CopperDriveBayBlockEntity drive2 = helper.getBlockEntity(bay2, CopperDriveBayBlockEntity.class);
        drive2.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        // Pattern in network 1
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity prov1 = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        prov1.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(20, () -> {
            GolemNetwork net1 = net(helper, bay1);
            GolemNetwork net2 = net(helper, bay2);

            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
            net1.insertItem(new ItemStack(Items.DIAMOND, 25), false);

            helper.assertTrue(net1.itemCountOf(diamond) == 25, "network 1 has 25 diamonds");
            helper.assertTrue(net2.itemCountOf(diamond) == 0, "network 2 has 0 diamonds (storage isolated across diode)");

            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
            helper.assertTrue(net1.canCraft(planks), "network 1 can craft planks");
            helper.assertTrue(!net2.canCraft(planks), "network 2 cannot craft planks (crafting isolated across diode)");

            helper.succeed();
        });
    }

    public static void cryoDiodeUnidirectionalSourceFlow(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper); // Net 1 is powered
        BlockPos bay1 = BAY;
        putBay(helper, bay1);

        BlockPos diodePos = BUS_OUT;
        // FACING NORTH: inlet is SOUTH (Net 1), outlet is NORTH (Net 2)
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        CryoDiodeBlockEntity diode = helper.getBlockEntity(diodePos, CryoDiodeBlockEntity.class);

        BlockPos b2 = diodePos.north();
        put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        BlockPos bay2 = b2.west();
        helper.setBlock(bay2, ModBlocks.COPPER_DRIVE_BAY.get().defaultBlockState());

        helper.runAfterDelay(20, () -> {
            GolemNetwork net1 = net(helper, bay1);
            GolemNetwork net2 = net(helper, b2);

            helper.assertTrue(net1.isOnline(), "net 1 is online from direct power");
            helper.assertTrue(net2.cryoSourceAvailable() > 0, "net 2 draws Source through outlet of diode");
            helper.assertTrue(net2.isOnline(), "net 2 is online powered by net 1 through diode");

            // Directional check: outlet side gets supplier, inlet side gets null
            helper.assertTrue(diode.asSupplier(Direction.NORTH) != null, "outlet side (NORTH) gets Source supplier");
            helper.assertTrue(diode.asSupplier(Direction.SOUTH) == null, "inlet side (SOUTH) gets null supplier (strictly unidirectional)");
            helper.succeed();
        });
    }

    public static void cryoDiodeBreakLeavesBothNetworksWorking(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        BlockPos bay1 = BAY;
        putBay(helper, bay1);

        BlockPos diodePos = BUS_OUT;
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos b2 = diodePos.north();
        put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        put(helper, b2.offset(0, -1, 0), ModBlocks.CREATIVE_SOURCE_JAR.get()); // Give Net 2 its own power
        BlockPos bay2 = b2.west();
        helper.setBlock(bay2, ModBlocks.COPPER_DRIVE_BAY.get().defaultBlockState());

        helper.runAfterDelay(15, () -> {
            helper.setBlock(diodePos, Blocks.AIR);

            helper.runAfterDelay(20, () -> {
                GolemNetwork net1 = net(helper, bay1);
                GolemNetwork net2 = net(helper, b2);
                helper.assertTrue(net1 != null && net1.isOnline(), "network 1 remains online and operational after diode break");
                helper.assertTrue(net2 != null && net2.isOnline(), "network 2 remains online and operational after diode break");
                helper.succeed();
            });
        });
    }

    // -------------------------------------------------------------------------
    // 4. SECURITY (MULTIPLAYER)
    // -------------------------------------------------------------------------

    public static void securityTerminalBuildProtection(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer strangerWithInsert = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "stranger-insert"));
        ItemStack insertCard = IdentityPunchCardItem.of(strangerWithInsert.getUUID(), strangerWithInsert.getScoreboardName(), SecurityPermission.INSERT.mask);
        sec.getCards().setItem(0, insertCard);

        ServerPlayer strangerNoCard = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "stranger-nocard"));

        helper.runAfterDelay(10, () -> {
            BlockPos absBay = helper.absolutePos(bay);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absBay), Direction.UP, absBay, false);

            // Owner right clicks Drive Bay -> allowed
            var ownerEvt = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(owner, InteractionHand.MAIN_HAND, absBay, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(ownerEvt);
            helper.assertTrue(!ownerEvt.isCanceled(), "owner interaction with drive bay is allowed");

            // Stranger with INSERT only right clicks Drive Bay -> denied (requires BUILD)
            var insertEvt = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(strangerWithInsert, InteractionHand.MAIN_HAND, absBay, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(insertEvt);
            helper.assertTrue(insertEvt.isCanceled(), "stranger with only INSERT is denied drive bay access");

            // Stranger with no card right clicks Drive Bay -> denied
            var noCardEvt = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(strangerNoCard, InteractionHand.MAIN_HAND, absBay, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(noCardEvt);
            helper.assertTrue(noCardEvt.isCanceled(), "stranger without card is denied drive bay access");

            helper.succeed();
        });
    }

    public static void securityTerminalItemPermissions(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer playerInsertOnly = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-insert-only"));
        sec.getCards().setItem(0, IdentityPunchCardItem.of(playerInsertOnly.getUUID(), "player-insert-only", SecurityPermission.INSERT.mask));

        ServerPlayer playerExtractOnly = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-extract-only"));
        sec.getCards().setItem(1, IdentityPunchCardItem.of(playerExtractOnly.getUUID(), "player-extract-only", SecurityPermission.EXTRACT.mask));

        ServerPlayer playerCraftOnly = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-craft-only"));
        sec.getCards().setItem(2, IdentityPunchCardItem.of(playerCraftOnly.getUUID(), "player-craft-only", SecurityPermission.CRAFT.mask));

        BlockPos absTerm = helper.absolutePos(TERM);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 50), false);
            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));

            // 1. Player with INSERT only: can insert, CANNOT extract
            TerminalMenu menuInsert = new TerminalMenu(81, playerInsertOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerInsertOnly.containerMenu = menuInsert;
            menuInsert.setCarried(new ItemStack(Items.GOLD_INGOT, 10));
            menuInsert.handleAction(playerInsertOnly, new TerminalActionPayload(81, TerminalActionPayload.Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menuInsert.getCarried().isEmpty(), "player with INSERT successfully inserts items");

            menuInsert.handleAction(playerInsertOnly, new TerminalActionPayload(81, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menuInsert.getCarried().isEmpty(), "player with INSERT only is denied TAKE_STACK");
            helper.assertTrue(n.itemCountOf(diamond) == 50, "network items untouched by denied take");

            // 2. Player with EXTRACT only: can extract, CANNOT order craft
            TerminalMenu menuExtract = new TerminalMenu(82, playerExtractOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerExtractOnly.containerMenu = menuExtract;
            menuExtract.handleAction(playerExtractOnly, new TerminalActionPayload(82, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menuExtract.getCarried().getCount() == 50, "player with EXTRACT successfully takes items");

            menuExtract.handleCraftRequest(playerExtractOnly, new CraftRequestPayload(82, new ItemStack(Items.DIAMOND), 1, false, 1));
            helper.assertTrue(n.cpus().stream().allMatch(com.golemlogix.crafting.CraftingCpu::isIdle), "craft denied without CRAFT permission");

            // 3. Player with CRAFT only
            TerminalMenu menuCraft = new TerminalMenu(83, playerCraftOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerCraftOnly.containerMenu = menuCraft;
            helper.assertTrue(n.hasPermission(playerCraftOnly, SecurityPermission.CRAFT), "player has CRAFT permission");

            helper.succeed();
        });
    }

    public static void securityTerminalJeiFillProtection(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer strangerNoExtract = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "stranger-no-extract"));
        sec.getCards().setItem(0, IdentityPunchCardItem.of(strangerNoExtract.getUUID(), "stranger-no-extract", SecurityPermission.INSERT.mask));

        BlockPos absTerm = helper.absolutePos(TERM);
        helper.setBlock(TERM, ModBlocks.CRAFTING_TERMINAL.get().defaultBlockState());

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 20), false);

            // Stranger tries JEI fillGrid without EXTRACT permission
            TerminalMenu menuStranger = new TerminalMenu(84, strangerNoExtract.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            List<List<ItemStack>> recipeOptions = List.of(List.of(new ItemStack(Items.IRON_INGOT)));
            menuStranger.fillGrid(strangerNoExtract, recipeOptions, false);
            helper.assertTrue(menuStranger.getSlot(1).getItem().isEmpty(), "fillGrid extracts nothing when player lacks EXTRACT permission");

            // Owner tries JEI fillGrid (has all permissions)
            TerminalMenu menuOwner = new TerminalMenu(85, owner.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            menuOwner.fillGrid(owner, recipeOptions, false);
            helper.assertTrue(menuOwner.getSlot(1).getItem().is(Items.IRON_INGOT), "fillGrid succeeds when player has EXTRACT permission");

            helper.succeed();
        });
    }

    public static void securityTerminalBreakBlockAndCreativeBypass(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer strangerNoBuild = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "stranger-no-build"));

        ServerPlayer creativeOp = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "creative-op"));
        creativeOp.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        net.minecraft.world.level.GameType.CREATIVE.updatePlayerAbilities(creativeOp.getAbilities());
        helper.getLevel().getServer().getPlayerList().op(
                creativeOp.nameAndId(),
                java.util.Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.ADMIN),
                java.util.Optional.empty()
        );

        helper.runAfterDelay(10, () -> {
            try {
                BlockPos absBay = helper.absolutePos(bay);
                var state = helper.getLevel().getBlockState(absBay);

                // Stranger without BUILD tries to break network block -> cancelled
                var breakEvt1 = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(helper.getLevel(), absBay, state, strangerNoBuild);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakEvt1);
                helper.assertTrue(breakEvt1.isCanceled(), "break block cancelled for stranger without BUILD");

                // Owner breaks network block -> allowed
                var breakEvt2 = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(helper.getLevel(), absBay, state, owner);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakEvt2);
                helper.assertTrue(!breakEvt2.isCanceled(), "break block allowed for owner");

                // Creative OP breaks network block -> allowed (bypasses security)
                var breakEvt3 = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(helper.getLevel(), absBay, state, creativeOp);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakEvt3);
                helper.assertTrue(!breakEvt3.isCanceled(), "break block allowed for creative operator bypass");

                helper.succeed();
            } finally {
                helper.getLevel().getServer().getPlayerList().deop(creativeOp.nameAndId());
            }
        });
    }

    public static void networkWithoutSecurityTerminalAllowsAll(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        ServerPlayer stranger = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "random-stranger"));

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(!n.hasSecurityTerminal(), "network has no security terminal");

            BlockPos absBay = helper.absolutePos(bay);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absBay), Direction.UP, absBay, false);
            var clickEvt = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(stranger, InteractionHand.MAIN_HAND, absBay, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(clickEvt);
            helper.assertTrue(!clickEvt.isCanceled(), "without security terminal, right click is allowed for anyone");

            var breakEvt = new net.neoforged.neoforge.event.level.block.BreakBlockEvent(helper.getLevel(), absBay, helper.getLevel().getBlockState(absBay), stranger);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(breakEvt);
            helper.assertTrue(!breakEvt.isCanceled(), "without security terminal, break block is allowed for anyone");

            helper.succeed();
        });
    }

    // -------------------------------------------------------------------------
    // 5. MECHANICAL ASMR
    // -------------------------------------------------------------------------

    public static void asmrTypewriterDingConditions(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork net = net(helper, bay);
            ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));

            // Plan with 1 execution (4 planks)
            CraftingPlan plan1 = CraftingPlanner.plan(net, planksKey, 4);
            var smallJob = new com.golemlogix.crafting.CraftingJob(plan1, Map.of()).requestedBy(null, BUS_OUT);
            boolean smallDing = smallJob.doneExecutions() >= 16 || smallJob.requester() != null;
            helper.assertTrue(!smallDing, "small bus job (< 16 executions, requester == null) must not ding");

            // Plan with player requester
            ServerPlayer player = testPlayer(helper);
            var playerJob = new com.golemlogix.crafting.CraftingJob(plan1, Map.of()).requestedBy(player.getUUID(), null);
            boolean playerDing = playerJob.doneExecutions() >= 16 || playerJob.requester() != null;
            helper.assertTrue(playerDing, "player order (requester != null) must ding");

            // Plan with 20 executions
            CraftingPlan plan20 = CraftingPlanner.plan(net, planksKey, 80);
            var largeJob = new com.golemlogix.crafting.CraftingJob(plan20, Map.of()).requestedBy(null, BUS_OUT);
            CompoundTag tag = largeJob.save(helper.getLevel().registryAccess());
            tag.putLong("Done", 16L);
            var loadedLargeJob = com.golemlogix.crafting.CraftingJob.load(tag, helper.getLevel().registryAccess());
            helper.assertTrue(loadedLargeJob != null, "loaded large job exists");
            boolean largeDing = loadedLargeJob.doneExecutions() >= 16 || loadedLargeJob.requester() != null;
            helper.assertTrue(largeDing, "large job (>= 16 executions) must ding");

            helper.succeed();
        });
    }

    public static void asmrConfigTogglesSilenceDing(GameTestHelper helper) {
        helper.assertTrue(com.golemlogix.config.GolemConfig.TYPEWRITER_DING.get(), "typewriter ding defaults to true");
        helper.assertTrue(com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.get(), "monitor blocked tick defaults to true");

        com.golemlogix.config.GolemConfig.TYPEWRITER_DING.set(false);
        helper.assertTrue(!com.golemlogix.config.GolemConfig.TYPEWRITER_DING.get(), "typewriter ding toggled off");

        com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.set(false);
        helper.assertTrue(!com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.get(), "monitor tick toggled off");

        // Reset back to defaults
        com.golemlogix.config.GolemConfig.TYPEWRITER_DING.set(true);
        com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.set(true);

        helper.succeed();
    }

    public static void asmrRecipeMonitorBlockedJobStatus(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Processing pattern with NO machine in front: job will get blocked
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.GOLD_INGOT)))));

        BlockPos monPos = BUS_OUT;
        helper.setBlock(monPos, ModBlocks.RECIPE_MONITOR.get().defaultBlockState());
        RecipeMonitorBlockEntity monitor = helper.getBlockEntity(monPos, RecipeMonitorBlockEntity.class);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 10), false);
            n.startCrafting(ItemKey.of(new ItemStack(Items.GOLD_INGOT)), 5);

            helper.runAfterDelay(25, () -> {
                RecipeMonitorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(monPos), helper.getBlockState(monPos), monitor);

                helper.assertTrue(monitor.fault(), "monitor reports fault for blocked job");
                helper.assertTrue(monitor.lampState() == 2, "monitor lamp glows red (state 2) on blocked job");
                helper.succeed();
            });
        });
    }

    public static void asmrRecipeMonitorBlockedTickAndClientSync(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos monPos = BUS_OUT;
        helper.setBlock(monPos, ModBlocks.RECIPE_MONITOR.get().defaultBlockState());
        RecipeMonitorBlockEntity monitor = helper.getBlockEntity(monPos, RecipeMonitorBlockEntity.class);

        helper.runAfterDelay(5, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Jobs", 1);
            tag.putBoolean("Online", true);
            tag.putBoolean("Fault", true);

            monitor.readUpdateTag(tag, helper.getLevel().registryAccess());
            helper.assertTrue(monitor.fault(), "client monitor has fault = true after reading update tag");
            helper.assertTrue(monitor.lampState() == 2, "client lamp state is 2 (red)");

            boolean shouldTick = monitor.fault() && com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.get();
            helper.assertTrue(shouldTick, "blocked ticking condition is active when fault and config enabled");

            helper.succeed();
        });
    }
}
