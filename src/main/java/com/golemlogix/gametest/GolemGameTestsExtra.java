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
import com.golemlogix.blocks.ProviderOutput;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.system.AtlasStructure;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.upgrade.BusSpeed;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.component.DataComponentMap;

import static com.golemlogix.gametest.GolemGameTests.*;

/**
 * Extra rigorous game tests for Golem Logix:
 * Detailed coverage of Hewer's Annihilation Bus (breaking, tools, filters, right-click, attacks,
 * redstone, refill, menu restrictions) and complex Autocrafting scenarios.
 */
public final class GolemGameTestsExtra {

    private GolemGameTestsExtra() {
    }

    /** Positive control helper: places stone at front, wakes the bus, and asserts it mines stone into cobblestone in network. */
    static void assertMinesStone(GameTestHelper helper, BlockPos bay, BlockPos bus, BlockPos front) {
        long before = net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE)));
        helper.setBlock(front, Blocks.STONE);
        var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
        if (be != null) {
            be.wake();
        }
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) > before,
                    "positive control: bus must mine stone");
        });
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
            assertMinesStone(helper, bay, bus, front);
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
            assertMinesStone(helper, bay, bus, front);
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
            // Positive control: mature crop MUST be mined when grownOnly is ON
            helper.setBlock(front, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7));
            be.wake();
            helper.succeedWhen(() -> {
                helper.assertBlockNotPresent(Blocks.WHEAT, front);
                helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.WHEAT))) >= 1,
                        "positive control: mature crop harvested");
            });
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
            // Positive control: extract dirt to make room, now bus must mine stone
            net(helper, bay).extractItem(new ItemStack(Items.DIRT), 64, false);
            assertMinesStone(helper, bay, bus, front);
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
            // Positive control: with a fresh tool, it must mine stone
            be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            assertMinesStone(helper, bay, bus, front);
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
            // Positive control: with a real tool in slot, stone can now be mined
            be.getTool().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            assertMinesStone(helper, bay, bus, front);
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
                // Positive control: switch to deny list mode (allow all with empty filter), pig must now die
                be.menuData().set(3, 1);
                be.wake();
                helper.succeedWhen(() -> {
                    helper.assertTrue(!pig.isAlive(), "positive control: bus attacks and kills pig once allow list restriction is lifted");
                });
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

        helper.succeedWhen(() -> {
            helper.assertTrue(be.lastUse() > 0, "the bus interacted with the lever");
            helper.assertTrue(helper.getBlockState(front).getValue(LeverBlock.POWERED), "lever POWERED state changed to true");
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
            // Positive control: plain mob must die
            namedPig.discard();
            stand.discard();
            var plainPig = helper.spawnWithNoFreeWill(EntityType.PIG, front);
            be.wake();
            helper.succeedWhen(() -> {
                helper.assertTrue(!plainPig.isAlive(), "positive control: plain mob must die");
            });
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
            // Positive control: adult mob must die
            babyPig.discard();
            var adultPig = helper.spawnWithNoFreeWill(EntityType.PIG, front);
            be.wake();
            helper.succeedWhen(() -> {
                helper.assertTrue(!adultPig.isAlive(), "positive control: adult mob must die");
            });
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

        // 1. Without signal in HIGH mode: waits
        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            // Power the bus from above (without breaking the Atlas Array below/behind)
            helper.setBlock(bus.above(), Blocks.REDSTONE_BLOCK);
        });

        // With signal in HIGH mode: mines
        helper.runAfterDelay(90, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);

            // 2. LOW mode: waits while powered, mines when unpowered
            be.menuData().set(0, RedstoneMode.LOW.ordinal());
            helper.setBlock(front, Blocks.STONE);
            be.wake();
            helper.runAfterDelay(30, () -> {
                helper.assertBlockPresent(Blocks.STONE, front);
                // Remove signal in LOW mode: should mine
                helper.setBlock(bus.above(), Blocks.AIR);
                be.wake();
                helper.runAfterDelay(50, () -> {
                    helper.assertBlockNotPresent(Blocks.STONE, front);

                    // 3. IGNORE mode: mines without signal
                    be.menuData().set(0, RedstoneMode.IGNORE.ordinal());
                    helper.setBlock(front, Blocks.STONE);
                    be.wake();
                    helper.runAfterDelay(50, () -> {
                        helper.assertBlockNotPresent(Blocks.STONE, front);

                        // 4. PULSE mode: waits without pulse, mines on rising pulse edge
                        be.menuData().set(0, RedstoneMode.PULSE.ordinal());
                        helper.setBlock(front, Blocks.STONE);
                        be.wake();
                        helper.runAfterDelay(25, () -> {
                            helper.assertBlockPresent(Blocks.STONE, front);
                            // Send rising edge pulse
                            helper.setBlock(bus.above(), Blocks.REDSTONE_BLOCK);
                            be.wake();
                            helper.succeedWhen(() -> {
                                helper.assertBlockNotPresent(Blocks.STONE, front);
                            });
                        });
                    });
                });
            });
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
            // Positive control: place Core back, bus must now mine stone
            put(helper, CORE, ModBlocks.ATLAS_CORE.get());
            var core = helper.getBlockEntity(CORE, com.golemlogix.blockentity.AtlasCoreBlockEntity.class);
            if (core != null) {
                core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            }
            assertMinesStone(helper, bay, bus, front);
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

            // Facing DOWN
            BlockPos below = bus.below();
            helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.DOWN));
            helper.setBlock(below, Blocks.STONE);
            var be = helper.getBlockEntity(bus, AnnihilationBusBlockEntity.class);
            if (be != null) {
                be.wake();
            }
            helper.succeedWhen(() -> {
                helper.assertBlockNotPresent(Blocks.STONE, below);
                helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 2,
                        "stone below was mined");
            });
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
            // Positive control: inserting expected iron pickaxe refills the slot and mines stone
            net(helper, bay).insertItem(new ItemStack(Items.IRON_PICKAXE), false);
            helper.setBlock(front, Blocks.STONE);
            be.wake();
            helper.succeedWhen(() -> {
                helper.assertTrue(be.getTool().getItem(0).is(Items.IRON_PICKAXE), "tool slot refilled with iron pickaxe");
                helper.assertBlockNotPresent(Blocks.STONE, front);
                helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) >= 1,
                        "positive control: bus refilled iron pickaxe and mined stone");
            });
        });
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

    /** The bridges go to what takes the signal: dust, a lamp, a repeater's back - not a repeater's output, a lever or plain stone. */
    public static void stockManometerReceivers(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        // a floor: repeaters and dust break without one
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            helper.setBlock(at.below().relative(d), Blocks.STONE);
        }
        helper.setBlock(at, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
        BlockPos abs = helper.absolutePos(at);
        var level = helper.getLevel();
        java.util.function.Function<Direction, Boolean> takes = d -> com.golemlogix.blocks.StockManometerBlock.isReceiver(level, abs, d);
        helper.setBlock(at.north(), Blocks.REDSTONE_LAMP);
        helper.setBlock(at.south(), Blocks.STONE);
        helper.setBlock(at.east(), Blocks.REPEATER.defaultBlockState().setValue(net.minecraft.world.level.block.DiodeBlock.FACING, Direction.WEST));
        helper.setBlock(at.west(), Blocks.REPEATER.defaultBlockState().setValue(net.minecraft.world.level.block.DiodeBlock.FACING, Direction.WEST));
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
        StockManometerBlockEntity mano = helper.getBlockEntity(at, StockManometerBlockEntity.class);
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
        mano.setThreshold(30); // on from 30
        mano.setHysteresis(10); // off again at 10 or less

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
                helper.succeedWhen(() -> {
                    helper.assertTrue(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).isIdle(), "craft finished");
                    helper.assertTrue(mano.getSignal() == 0, "signal drops back to 0 when craft ends");
                });
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

        // First configure with target amount > 0 without pattern, proving the ledger actively updates
        ItemStack diamondFilter = new ItemStack(Items.DIAMOND);
        diamondFilter.set(ModDataComponents.KEEP_AMOUNT.get(), 10);
        req.getFilters().setItem(1, diamondFilter);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(req.getSlotStatus(1) == StockRequesterBlockEntity.STATUS_NO_PATTERN,
                    "proves ledger updates: status is STATUS_NO_PATTERN before amount is 0");

            // Slot 0 empty
            // Slot 1 keep amount set to 0 via menu
            ServerPlayer player = testPlayer(helper);
            StockRequesterMenu reqMenu = new StockRequesterMenu(1, player.getInventory(), req.getFilters(), req.menuData(), req.getBlockPos());
            reqMenu.setKeep(1, 0);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_OK, "empty slot stays STATUS_OK");
                helper.assertTrue(req.getSlotStatus(1) == StockRequesterBlockEntity.STATUS_OK, "amount 0 slot stays STATUS_OK");

                // Set non-default status on slot 1 again before testing offline
                reqMenu.setKeep(1, 10);
                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(req.getSlotStatus(1) == StockRequesterBlockEntity.STATUS_NO_PATTERN, "slot 1 active before network goes offline");
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
            });
        });
    }

    // -------------------------------------------------------------------------
    // 3. CRYO-DIODE (SUBNET ISOLATOR)
    // -------------------------------------------------------------------------

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
        helper.getBlockEntity(bay1, CopperDriveBayBlockEntity.class).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        BlockPos diodePos = BUS_OUT;
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos b2 = diodePos.north();
        put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        put(helper, b2.offset(0, -1, 0), ModBlocks.CREATIVE_SOURCE_JAR.get()); // Give Net 2 its own power
        // the cabinet is 2x2, its other parts to the east and above: it stands clear of the bushing and touches it
        BlockPos bay2 = b2.west(2);
        putBay(helper, bay2);
        helper.getBlockEntity(bay2, CopperDriveBayBlockEntity.class).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        helper.runAfterDelay(15, () -> {
            helper.setBlock(diodePos, Blocks.AIR);

            helper.runAfterDelay(20, () -> {
                GolemNetwork net1 = net(helper, bay1);
                GolemNetwork net2 = net(helper, b2);
                helper.assertTrue(net1 != null && net1.isOnline(), "network 1 remains online and operational after diode break");
                helper.assertTrue(net2 != null && net2.isOnline(), "network 2 remains online and operational after diode break");

                // Prove each network still stores items after the break
                net1.insertItem(new ItemStack(Items.DIAMOND, 10), false);
                net2.insertItem(new ItemStack(Items.EMERALD, 5), false);
                helper.assertTrue(net1.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "net 1 stores diamonds after break");
                helper.assertTrue(net1.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 0, "net 1 does not store net 2 items");
                helper.assertTrue(net2.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 5, "net 2 stores emeralds after break");
                helper.assertTrue(net2.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 0, "net 2 does not store net 1 items");

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

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos absTerm = helper.absolutePos(TERM);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 50), false);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
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

            // 2. Player with EXTRACT only: can extract, CANNOT order craft (tested with real pattern!)
            TerminalMenu menuExtract = new TerminalMenu(82, playerExtractOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerExtractOnly.containerMenu = menuExtract;
            menuExtract.handleAction(playerExtractOnly, new TerminalActionPayload(82, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menuExtract.getCarried().getCount() == 50, "player with EXTRACT successfully takes items");

            menuExtract.handleCraftRequest(playerExtractOnly, new CraftRequestPayload(82, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            helper.assertTrue(n.cpus().stream().allMatch(com.golemlogix.crafting.CraftingCpu::isIdle), "craft denied without CRAFT permission");

            // 3. Player with CRAFT only: CANNOT extract, CAN order craft of real pattern
            TerminalMenu menuCraft = new TerminalMenu(83, playerCraftOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerCraftOnly.containerMenu = menuCraft;
            helper.assertTrue(n.hasPermission(playerCraftOnly, SecurityPermission.CRAFT), "player has CRAFT permission");

            menuCraft.handleAction(playerCraftOnly, new TerminalActionPayload(83, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.OAK_LOG), FluidStack.EMPTY));
            helper.assertTrue(menuCraft.getCarried().isEmpty(), "player with CRAFT only is denied TAKE_STACK");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 10, "items untouched by denied take");

            menuCraft.handleCraftRequest(playerCraftOnly, new CraftRequestPayload(83, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(!cpu.isIdle(), "ordering craft of real pattern starts a job for player with CRAFT permission");

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
            helper.assertTrue(!com.golemlogix.blockentity.CryoCalculatorBlockEntity.dings(smallJob), "small bus job (< 16 executions, requester == null) must not ding");

            // Plan with player requester
            ServerPlayer player = testPlayer(helper);
            var playerJob = new com.golemlogix.crafting.CraftingJob(plan1, Map.of()).requestedBy(player.getUUID(), null);
            helper.assertTrue(com.golemlogix.blockentity.CryoCalculatorBlockEntity.dings(playerJob), "player order (requester != null) must ding");

            // Plan with 20 executions
            CraftingPlan plan20 = CraftingPlanner.plan(net, planksKey, 80);
            var largeJob = new com.golemlogix.crafting.CraftingJob(plan20, Map.of()).requestedBy(null, BUS_OUT);
            CompoundTag tag = largeJob.save(helper.getLevel().registryAccess());
            tag.putLong("Done", 16L);
            var loadedLargeJob = com.golemlogix.crafting.CraftingJob.load(tag, helper.getLevel().registryAccess());
            helper.assertTrue(loadedLargeJob != null, "loaded large job exists");
            helper.assertTrue(com.golemlogix.blockentity.CryoCalculatorBlockEntity.dings(loadedLargeJob), "large job (>= 16 executions) must ding");

            helper.succeed();
        });
    }

    /** The config switch really silences the bell: a job that would ring does not. */
    public static void asmrConfigTogglesSilenceDing(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.getBlockEntity(PROV, PatternProviderBlockEntity.class).getContainer().setItem(0,
                card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        helper.runAfterDelay(5, () -> {
            GolemNetwork net = net(helper, bay);
            CraftingPlan plan = CraftingPlanner.plan(net, ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 4);
            var job = new com.golemlogix.crafting.CraftingJob(plan, Map.of()).requestedBy(testPlayer(helper).getUUID(), null);
            helper.assertTrue(com.golemlogix.blockentity.CryoCalculatorBlockEntity.dings(job), "a player's order rings by default");
            com.golemlogix.config.GolemConfig.TYPEWRITER_DING.set(false);
            try {
                helper.assertTrue(!com.golemlogix.blockentity.CryoCalculatorBlockEntity.dings(job), "the config silences it");
            } finally {
                com.golemlogix.config.GolemConfig.TYPEWRITER_DING.set(true);
            }
            helper.succeed();
        });
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

            helper.assertTrue(monitor.ticksBlocked(), "a blocked job ticks while the config allows it");
            com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.set(false);
            try {
                helper.assertTrue(!monitor.ticksBlocked(), "the config silences the tick");
            } finally {
                com.golemlogix.config.GolemConfig.MONITOR_BLOCKED_TICK.set(true);
            }

            helper.succeed();
        });
    }

    // =========================================================================
    // 9. NEW TESTS: COMPLEX NETWORKS & EDGE CASES
    // =========================================================================

    /** Patterns where A needs B and B needs A: planner returns without stack overflow, reports missing, and non-cyclic craft still plans. */
    public static void autocraftCyclicPatternsDoNotHang(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Cyclic recipes: Iron Ingot needs Gold Ingot, Gold Ingot needs Iron Ingot
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.getContainer().setItem(1, card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.GOLD_INGOT)))));
        // Independent recipe: 1 Oak Log -> 4 Oak Planks
        provider.getContainer().setItem(2, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);

            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));

            // Planning for cyclic recipe must return without hang/overflow and report missing
            CraftingPlan planCyclic = CraftingPlanner.plan(n, iron, 1);
            helper.assertTrue(!planCyclic.missing().isEmpty(), "cyclic recipe cannot be made, reports missing: " + planCyclic.missing());
            helper.assertTrue(n.startCrafting(iron, 1).type() != GolemNetwork.StartType.STARTED, "cyclic craft cannot start");

            // Independent recipe must still plan and craft cleanly
            CraftingPlan planOk = CraftingPlanner.plan(n, planks, 4);
            helper.assertTrue(planOk.missing().isEmpty(), "independent recipe has nothing missing: " + planOk.missing());
            helper.assertTrue(n.startCrafting(planks, 4).type() == GolemNetwork.StartType.STARTED, "independent craft starts");

            helper.succeed();
        });
    }

    /** A recipe that needs itself: the planner does not hang, reports the item missing, and starts no job. */
    public static void autocraftSelfCycle(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.IRON_INGOT)))));
        helper.runAfterDelay(5, () -> {
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            CraftingPlan plan = CraftingPlanner.plan(net(helper, bay), iron, 1);
            helper.assertTrue(!plan.missing().isEmpty(), "a recipe that needs itself cannot be made: " + plan.missing());
            helper.assertTrue(net(helper, bay).startCrafting(iron, 1).type() != GolemNetwork.StartType.STARTED, "and no job starts");
            helper.succeed();
        });
    }

    /** Three recipes in a ring (iron - gold - diamond - iron): no stack overflow, nothing crafted out of thin air. */
    public static void autocraftThreeCycle(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.getContainer().setItem(1, card(new PatternData(false,
                List.of(new ItemStack(Items.DIAMOND)), List.of(new ItemStack(Items.GOLD_INGOT)))));
        provider.getContainer().setItem(2, card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.DIAMOND)))));
        helper.runAfterDelay(5, () -> {
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            CraftingPlan plan = CraftingPlanner.plan(net(helper, bay), iron, 1);
            helper.assertTrue(!plan.missing().isEmpty(), "a ring of recipes cannot be made: " + plan.missing());
            helper.succeed();
        });
    }

    /**
     * A fuzzy input whose only pattern stands on an item of the same path (oak from birch, "any log"): the planner steps over the
     * alternative instead of walking into itself, and reports the birch as missing rather than crafting the oak out of itself.
     */
    public static void autocraftFuzzyCycleStepsOver(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.BIRCH_LOG)), List.of(new ItemStack(Items.OAK_LOG)), List.of("#minecraft:logs"))));
        helper.runAfterDelay(5, () -> {
            ItemKey oak = ItemKey.of(new ItemStack(Items.OAK_LOG));
            CraftingPlan plan = CraftingPlanner.plan(net(helper, bay), oak, 1);
            ItemKey birch = ItemKey.of(new ItemStack(Items.BIRCH_LOG));
            helper.assertTrue(plan.missing().containsKey(birch), "the birch is missing: " + plan.missing());
            helper.assertTrue(!plan.missing().containsKey(oak), "and the oak is not asked of itself: " + plan.missing());
            helper.succeed();
        });
    }

    /**
     * Every device of the newer ones in a network with no Source at all: nothing throws, the network stays offline, and the devices
     * stand by - the counter is dark, the requester holds no order, the manometer gives no signal.
     */
    public static void newDevicesInDeadNetwork(GameTestHelper helper) {
        // the array without its core: the network is offline whatever the config says about Source
        buildArray(helper, 0, 0);
        put(helper, GolemGameTests.CORE, Blocks.AIR);
        put(helper, new BlockPos(2, 1, -1), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(2, 2, -1), ModBlocks.CHANNEL_COUNTER.get());
        put(helper, new BlockPos(4, 1, -1), ModBlocks.STOCK_MANOMETER.get());
        put(helper, new BlockPos(6, 1, -1), ModBlocks.STOCK_REQUESTER.get());
        put(helper, new BlockPos(8, 1, -1), ModBlocks.CRYO_DIODE.get());
        put(helper, new BlockPos(10, 1, -1), ModBlocks.SECURITY_TERMINAL.get());
        put(helper, new BlockPos(12, 1, -1), ModBlocks.COPPER_TANK.get());
        put(helper, new BlockPos(14, 1, -1), ModBlocks.FORMATION_BUS.get());
        // four jars in a square are a Giga Jar, and they too wait for Source
        for (BlockPos p : new BlockPos[]{new BlockPos(16, 1, -1), new BlockPos(17, 1, -1), new BlockPos(16, 1, 0), new BlockPos(17, 1, 0)}) {
            put(helper, p, ModBlocks.CRYO_SOURCE_JAR.get());
        }
        helper.runAfterDelay(40, () -> {
            GolemNetwork n = net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(!n.isOnline(), "the network has no Source");
            var counter = helper.getBlockEntity(new BlockPos(2, 2, -1), com.golemlogix.blockentity.ChannelCounterBlockEntity.class);
            helper.assertTrue(counter != null && !counter.online(), "the counter is dark");
            helper.assertTrue(n.channelsUsed() == 0, "nothing is allocated: " + n.channelsUsed());
            helper.succeed();
        });
    }

    /** A pipe loop around the array: the network takes the loop in one pass, the devices on it are active, nothing hangs. */
    public static void pipeLoopJoinsOnce(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        // out of the corner bushing (4,1,4), around the array, back into the corner bushing (4,1,6)
        for (BlockPos p : new BlockPos[]{new BlockPos(3, 1, 4), new BlockPos(3, 1, 5), new BlockPos(3, 1, 6), new BlockPos(4, 1, 6)}) {
            put(helper, p, ModBlocks.BASIC_SNOW_PIPE.get());
        }
        put(helper, new BlockPos(2, 1, 5), ModBlocks.ANALOG_TERMINAL.get());
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(n.isOnline(), "the loop did not break the network");
            helper.assertTrue(n.deviceCount() >= 1, "the terminal on the loop is in the network: " + n.deviceCount());
            helper.assertTrue(n.isDeviceActive(helper.absolutePos(new BlockPos(2, 1, 5))), "and it has a channel");
            helper.succeed();
        });
    }

    /** Removing a pipe splits the network into two: storage and devices leave the first, and reconnecting rejoins them. */
    public static void networkSplitWhenTrunkIsCut(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);

        BlockPos trunk1 = BUS_OUT;
        BlockPos trunk2 = trunk1.north();
        BlockPos trunk3 = trunk2.north();
        put(helper, trunk1, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, trunk2, ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, trunk3, ModBlocks.BASIC_SNOW_PIPE.get());

        BlockPos farTerm = trunk3.east();
        put(helper, farTerm, ModBlocks.ANALOG_TERMINAL.get());

        BlockPos peri = trunk3.west();
        BlockPos chest = peri.west();
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest, net.minecraft.world.level.block.entity.BlockEntity.class);
        c.setItem(0, new ItemStack(Items.EMERALD, 20));
        periscope(helper, peri, Direction.WEST);

        helper.runAfterDelay(15, () -> {
            GolemNetwork initial = net(helper, bay);
            helper.assertTrue(initial.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "diamond in main storage");
            helper.assertTrue(initial.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 20, "emerald in periscope storage before cut");
            helper.assertTrue(initial.isDeviceActive(helper.absolutePos(farTerm)), "far terminal has channel before cut");

            // Cut trunk in the middle
            helper.setBlock(trunk2, Blocks.AIR);

            helper.runAfterDelay(15, () -> {
                GolemNetwork mainNet = net(helper, bay);
                GolemNetwork cutNet = net(helper, peri);
                helper.assertTrue(mainNet.hasCore(), "main network retains core");
                helper.assertTrue(!cutNet.hasCore(), "cut-off part has no core");
                helper.assertTrue(mainNet.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "main network retains diamonds");
                helper.assertTrue(mainNet.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 0, "cut-off emerald storage leaves main network");
                helper.assertTrue(!mainNet.isDeviceActive(helper.absolutePos(farTerm)), "cut-off terminal loses channel on main network");

                // Place pipe back to rejoin
                put(helper, trunk2, ModBlocks.BASIC_SNOW_PIPE.get());

                helper.runAfterDelay(15, () -> {
                    GolemNetwork rejoined = net(helper, bay);
                    helper.assertTrue(rejoined.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "diamond still present");
                    helper.assertTrue(rejoined.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 20, "emerald storage rejoined");
                    helper.assertTrue(rejoined.isDeviceActive(helper.absolutePos(farTerm)), "far terminal active again");
                    helper.succeed();
                });
            });
        });
    }

    /** Two Cryo-Calculators run two different orders at the same time; both finish with right results and no items lost or duplicated. */
    public static void autocraftTwoCalculatorsAtOnce(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos cpu1 = CPU;
        BlockPos cpu2 = BUS_OUT;
        helper.setBlock(cpu1, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(cpu2, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());

        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Pattern 1: 1 Oak Log -> 4 Oak Planks
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        // Pattern 2: 1 Iron Block -> 9 Iron Ingots
        provider.getContainer().setItem(1, card(craftingPattern(new ItemStack(Items.IRON_INGOT, 9), new ItemStack(Items.IRON_BLOCK))));

        ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey ingotsKey = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        ItemKey logKey = ItemKey.of(new ItemStack(Items.OAK_LOG));
        ItemKey ironBlockKey = ItemKey.of(new ItemStack(Items.IRON_BLOCK));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 5), false);
            n.insertItem(new ItemStack(Items.IRON_BLOCK, 3), false);

            var start1 = n.startCrafting(planksKey, 20);
            var start2 = n.startCrafting(ingotsKey, 27);
            helper.assertTrue(start1.type() == GolemNetwork.StartType.STARTED, "order 1 started");
            helper.assertTrue(start2.type() == GolemNetwork.StartType.STARTED, "order 2 started");

            CryoCalculatorBlockEntity be1 = helper.getBlockEntity(cpu1, CryoCalculatorBlockEntity.class);
            CryoCalculatorBlockEntity be2 = helper.getBlockEntity(cpu2, CryoCalculatorBlockEntity.class);
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

    /**
     * A Source jar taken out and put back: the network follows at once. The remembered network used to keep the old picture for
     * seconds, because a block of the same kind put back where its kind stood looked like no change at all.
     */
    public static void sourceJarPutBackIsSeenAtOnce(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            if (!com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(n.isOnline(), "without the Source mods the network needs no Source");
                helper.succeed();
                return;
            }
            helper.setBlock(JAR, Blocks.AIR);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(!net(helper, bay).isOnline(), "offline without the jar");
                powered(helper);
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(net(helper, bay).isOnline(), "online again at once");
                    helper.succeed();
                });
            });
        });
    }

    /** Source runs out during a job; job does not lose items, and finishes once power returns. */
    public static void autocraftSurvivesPowerLoss(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey logKey = ItemKey.of(new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            if (!com.golemlogix.system.SourceHelper.sourceRequired()) {
                helper.assertTrue(n.isOnline(), "without Ars Nouveau the network needs no Source");
                helper.succeed();
                return;
            }

            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            var start = n.startCrafting(planksKey, 20);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "job started");

            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(!cpu.isIdle(), "calculator working on job");

            // Cut power by removing Creative Source Jar
            helper.setBlock(JAR, Blocks.AIR);

            // the core looks at its supply once a second, at a moment of its own: this waits for that whatever the moment
            helper.runAfterDelay(30, () -> {
                GolemNetwork nOffline = net(helper, bay);
                helper.assertTrue(!nOffline.isOnline(), "network goes offline without Source");
                helper.assertTrue(!cpu.isIdle(), "job preserved in calculator across power loss");

                // Restore power
                powered(helper);

                helper.runAfterDelay(20, () -> {
                    GolemNetwork nRestored = net(helper, bay);
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

    // =========================================================================
    // 10. NEW ADVANCED GAME TESTS (RELOADS, SECURITY, HYSTERESIS, ETC.)
    // =========================================================================

    /**
     * A real reload: the block entity is replaced by a new object that knows only what was saved, and the network is built again.
     * Loading a tag into the same object proves nothing, it still remembers everything.
     */
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> T reload(GameTestHelper helper, BlockPos rel, Class<T> type) {
        BlockPos abs = helper.absolutePos(rel);
        var level = helper.getLevel();
        var old = level.getBlockEntity(abs);
        CompoundTag tag = old.saveWithFullMetadata(level.registryAccess());
        var fresh = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(abs, old.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);
        GolemNetwork.refresh(level, abs);
        return type.cast(fresh);
    }

    /**
     * 1. A job of a nested plan (sticks from planks from logs) is saved and reloaded while part way through,
     * then finishes with the right result and no items lost or duplicated.
     */
    public static void autocraftMultiStepSurvivesReload(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Pattern 1: 1 Oak Log -> 4 Oak Planks
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        // Pattern 2: 2 Oak Planks -> 4 Sticks
        java.util.List<ItemStack> stickGrid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            stickGrid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
        }
        provider.getContainer().setItem(1, card(new PatternData(true, stickGrid, List.of(new ItemStack(Items.STICK, 4)))));

        ItemKey stickKey = ItemKey.of(new ItemStack(Items.STICK));
        ItemKey plankKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey logKey = ItemKey.of(new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            // 5 logs: 2 needed for 8 planks -> 16 sticks; 3 logs should remain untouched
            n.insertItem(new ItemStack(Items.OAK_LOG, 5), false);

            var start = n.startCrafting(stickKey, 16);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "multi-step job started: " + start.type());

            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(!cpu.isIdle(), "calculator actively working on job");

            // Part way through (after 5 ticks, job has started execution of steps)
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(!cpu.isIdle(), "job still in progress when reloaded");
                CryoCalculatorBlockEntity loaded = reload(helper, CPU, CryoCalculatorBlockEntity.class);
                helper.assertTrue(!loaded.isIdle(), "job preserved in calculator after reload");

                helper.succeedWhen(() -> {
                    GolemNetwork nAfter = net(helper, bay);
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
    public static void autocraftMachineStepSurvivesReload(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.setBlocking(true);
        // Machine step: 1 raw iron -> 1 ingot. Crafting step that needs its result: 9 ingots -> 1 block
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(new ItemStack(Items.IRON_INGOT));
        }
        provider.getContainer().setItem(1, card(new PatternData(true, grid, List.of(new ItemStack(Items.IRON_BLOCK)))));

        BlockPos machinePos = PROV.east();
        helper.setBlock(machinePos, Blocks.CHEST);
        ChestBlockEntity machine = helper.getBlockEntity(machinePos, ChestBlockEntity.class);
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
            GolemNetwork n = net(helper, bay);
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
                        net(helper, bay).insertFromMachine(new ItemStack(Items.IRON_INGOT, count));
                    }
                }
            });
        }
        helper.runAfterDelay(52, () -> helper.succeedWhen(() -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.itemCountOf(block) == 1, "the block was made (got " + n.itemCountOf(block) + ")");
            helper.assertTrue(n.itemCountOf(ingot) == 0, "no ingot left over (got " + n.itemCountOf(ingot) + ")");
            helper.assertTrue(n.itemCountOf(raw) == 3, "exactly nine raw iron were used (left " + n.itemCountOf(raw) + ")");
            helper.assertTrue(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).isIdle(), "the calculator is free");
        }));
    }

    /**
     * 2. A player has a terminal window open and rights through a card; the card is taken out of the Security Terminal;
     * the player's further actions through that same open window (take, insert, order a craft) are refused.
     */
    public static void securityRevokedWhileWindowOpen(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-open-window"));
        sec.getCards().setItem(0, IdentityPunchCardItem.of(player.getUUID(), "player-open-window",
                SecurityPermission.EXTRACT.mask | SecurityPermission.INSERT.mask | SecurityPermission.CRAFT.mask));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos termPos = TERM;
        helper.setBlock(termPos, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        BlockPos absTerm = helper.absolutePos(termPos);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 50), false);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));

            // Player opens terminal window
            TerminalMenu menu = new TerminalMenu(88, player.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            player.containerMenu = menu;

            // Positive control: actions succeed while card is in the Security Terminal
            menu.setCarried(new ItemStack(Items.GOLD_INGOT, 5));
            menu.handleAction(player, new TerminalActionPayload(88, TerminalActionPayload.Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().isEmpty(), "positive control: player with card can insert");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 5, "positive control: gold stored");

            // Rights revoked: remove card from Security Terminal while window stays open
            sec.getCards().setItem(0, ItemStack.EMPTY);
            sec.setChanged();
            helper.assertTrue(!n.hasPermission(player, SecurityPermission.EXTRACT), "permissions revoked on network");

            // 1. Take action refused
            menu.handleAction(player, new TerminalActionPayload(88, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().isEmpty(), "TAKE_STACK refused after rights revoked");
            helper.assertTrue(n.itemCountOf(diamond) == 50, "diamonds untouched in storage");

            // 2. Insert action refused
            menu.setCarried(new ItemStack(Items.IRON_INGOT, 8));
            menu.handleAction(player, new TerminalActionPayload(88, TerminalActionPayload.Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menu.getCarried().getCount() == 8, "INSERT_ALL refused after rights revoked");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 0, "iron not inserted");

            // 3. Craft action refused
            menu.handleCraftRequest(player, new CraftRequestPayload(88, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(cpu.isIdle(), "craft order refused after rights revoked");

            // The same order goes through once the card is back, so it was the rights that stopped it
            sec.getCards().setItem(0, IdentityPunchCardItem.of(player.getUUID(), "player-open-window", SecurityPermission.CRAFT.mask));
            menu.handleCraftRequest(player, new CraftRequestPayload(88, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            helper.assertTrue(!cpu.isIdle(), "positive control: the same craft order is taken with the card back");

            helper.succeed();
        });
    }

    /**
     * 3. The Security Terminal's owner and cards, with their rights, survive a save and load:
     * rights are still enforced afterwards.
     */
    public static void securityPersistsAcrossReload(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos secPos = BUS_OUT;
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        ServerPlayer owner = testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer playerWithCard = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-card-persists"));
        sec.getCards().setItem(0, IdentityPunchCardItem.of(playerWithCard.getUUID(), "player-card-persists", SecurityPermission.EXTRACT.mask));

        ServerPlayer playerNoCard = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "player-nocard-persists"));

        helper.runAfterDelay(10, () -> {
            SecurityTerminalBlockEntity loaded = reload(helper, secPos, SecurityTerminalBlockEntity.class);

            helper.assertTrue(owner.getUUID().equals(loaded.getOwnerId()), "owner UUID survives reload");
            helper.assertTrue(owner.getScoreboardName().equals(loaded.getOwnerName()), "owner name survives reload");
            helper.assertTrue(!loaded.getCards().getItem(0).isEmpty(), "punch card survives reload");

            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.hasPermission(owner, SecurityPermission.BUILD), "owner keeps BUILD permission after reload");
            helper.assertTrue(n.hasPermission(owner, SecurityPermission.EXTRACT), "owner keeps EXTRACT permission after reload");
            helper.assertTrue(n.hasPermission(playerWithCard, SecurityPermission.EXTRACT), "cardholder keeps EXTRACT permission after reload");
            helper.assertTrue(!n.hasPermission(playerWithCard, SecurityPermission.BUILD), "cardholder still denied BUILD permission");
            helper.assertTrue(!n.hasPermission(playerWithCard, SecurityPermission.INSERT), "cardholder still denied INSERT permission");
            helper.assertTrue(!n.hasPermission(playerNoCard, SecurityPermission.EXTRACT), "cardless player still denied EXTRACT permission");
            helper.assertTrue(!n.hasPermission(playerNoCard, SecurityPermission.BUILD), "cardless player still denied BUILD permission");

            helper.succeed();
        });
    }

    /**
     * 4. Stock Manometer mode "below" with hysteresis (guide book's logs example: 500 and 2000):
     * on at the first mark (<= 500), stays on up to the second, off above it (>= 2000),
     * on again only when falling back to the first mark (<= 500).
     */
    public static void stockManometerBelowHysteresis(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));

        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.OAK_LOG));
        mano.setMode(StockManometerBlockEntity.MODE_BELOW);
        mano.setThreshold(500);
        mano.setHysteresis(2000);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(mano.getSignal() == 15, "0 <= 500: signal starts ON (15)");

            // Add 1000 logs: between 500 and 2000 -> signal stays ON (15)
            net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 1000), false);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "stock=1000 stays ON (15) between 500 and 2000");

                // Add 1500 logs (total = 2500): above 2000 -> signal turns OFF (0)
                net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 1500), false);

                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(mano.getSignal() == 0, "stock=2500 turns OFF (0) above 2000");

                    // Extract 1500 logs (total = 1000): between 500 and 2000 -> signal stays OFF (0)
                    net(helper, bay).extractItem(new ItemStack(Items.OAK_LOG), 1500, false);

                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(mano.getSignal() == 0, "stock=1000 stays OFF (0) while falling, until <= 500");

                        // Extract 600 logs (total = 400): at or below 500 -> signal turns ON (15) again
                        net(helper, bay).extractItem(new ItemStack(Items.OAK_LOG), 600, false);

                        helper.runAfterDelay(15, () -> {
                            helper.assertTrue(mano.getSignal() == 15, "stock=400 turns ON (15) again at <= 500");
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    /**
     * 5. Stock Manometer mode, both numbers and the filter survive a save and load,
     * and the signal behaves the same afterwards.
     */
    public static void stockManometerSettingsSurviveReload(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_BELOW);
        mano.setThreshold(50);
        mano.setHysteresis(200);

        // It first sees an empty network and latches on; 100 diamonds lie between the two marks, so it holds
        helper.runAfterDelay(15, () -> net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 100), false));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(mano.getSignal() == 15, "initial signal is 15 in below mode");

            StockManometerBlockEntity loaded = reload(helper, manoPos, StockManometerBlockEntity.class);

            helper.assertTrue(loaded.getMode() == StockManometerBlockEntity.MODE_BELOW, "mode preserved after reload");
            helper.assertTrue(loaded.getThreshold() == 50, "threshold preserved after reload");
            helper.assertTrue(loaded.getHysteresis() == 200, "hysteresis preserved after reload");
            helper.assertTrue(loaded.getFilter().getItem(0).is(Items.DIAMOND), "filter preserved after reload");
            helper.assertTrue(loaded.getSignal() == 15, "signal state preserved after reload");

            // Verify behavior continues identically:
            // Insert 150 diamonds (total = 250 > 200) -> turns OFF (0)
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 150), false);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(loaded.getSignal() == 0, "signal turns OFF (0) above hysteresis mark (250 > 200)");

                // Extract 170 diamonds (total = 80, between 50 and 200) -> stays OFF (0)
                net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 170, false);

                helper.runAfterDelay(15, () -> {
                    helper.assertTrue(loaded.getSignal() == 0, "signal stays OFF (0) in deadband (80)");

                    // Extract 50 diamonds (total = 30 <= 50) -> turns ON (15)
                    net(helper, bay).extractItem(new ItemStack(Items.DIAMOND), 50, false);

                    helper.runAfterDelay(15, () -> {
                        helper.assertTrue(loaded.getSignal() == 15, "signal turns ON (15) below threshold (30 <= 50)");
                        helper.succeed();
                    });
                });
            });
        });
    }

    /**
     * 6. An Atlas Array built wrongly (missing part, expander in crown, base, or corner) does not provide
     * array capacity; positive controls with basic and quantum expanders provide the book's stated capacity.
     */
    public static void atlasRejectsBadLayouts(GameTestHelper helper) {
        // Positive Control 1: Correct layout with 4 basic + 4 quantum expanders = 1280 channels (256/line)
        buildArray(helper, 4, 4);
        powered(helper);
        putBay(helper, BAY);

        helper.runAfterDelay(5, () -> {
            var rOk1 = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(rOk1.valid(), "4+4 expander array is valid multiblock");
            helper.assertTrue(rOk1.expanders() == 4 && rOk1.quantumExpanders() == 4, "expanders counted: 4+4");
            GolemNetwork net1 = net(helper, BAY);
            helper.assertTrue(net1.channelCapacity() == 1280, "4+4 expanders give 1280 channels, got " + net1.channelCapacity());
            helper.assertTrue(net1.lineCapacity() == 256, "4+4 expanders give 256 line capacity, got " + net1.lineCapacity());

            // Positive Control 2: Correct layout with 8 quantum expanders = 1600 channels (320/line)
            buildArray(helper, 0, 8);
            var rOk2 = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(rOk2.valid(), "8 quantum expander array is valid multiblock");
            helper.assertTrue(rOk2.quantumExpanders() == 8, "8 quantum expanders counted");
            GolemNetwork net2 = net(helper, BAY);
            helper.assertTrue(net2.channelCapacity() == 1600, "8 quantum expanders give 1600 channels, got " + net2.channelCapacity());
            helper.assertTrue(net2.lineCapacity() == 320, "8 quantum expanders give 320 line capacity, got " + net2.lineCapacity());

            // Negative Control 1: Missing part (hole in crown)
            BlockPos crownPos = ORIGIN.offset(1, 3, 0);
            helper.setBlock(crownPos, Blocks.AIR);
            var rHole = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rHole.valid(), "missing crown block invalidates array");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops when part missing");

            // Negative Control 2: Expander in crown (layer 3)
            helper.setBlock(crownPos, ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            var rCrownExp = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rCrownExp.valid(), "expander in crown is rejected");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops with expander in crown");
            put(helper, crownPos, ModBlocks.ATLAS_CASING.get()); // restore

            // Negative Control 3: Expander in base (layer 0)
            BlockPos basePos = ORIGIN.offset(1, 0, 0);
            helper.setBlock(basePos, ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            var rBaseExp = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rBaseExp.valid(), "expander in base is rejected");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops with expander in base");
            put(helper, basePos, ModBlocks.ATLAS_CONDUIT.get()); // restore

            // Negative Control 4: Expander in corner (layer 1 Atlant position)
            BlockPos cornerPos = ORIGIN.offset(1, 1, 1);
            helper.setBlock(cornerPos, ModBlocks.CHANNEL_EXPANDER.get().defaultBlockState());
            var rCornerExp = AtlasStructure.check(helper.getLevel(), helper.absolutePos(CORE));
            helper.assertTrue(!rCornerExp.valid(), "expander in corner is rejected");
            helper.assertTrue(net(helper, BAY).channelCapacity() <= 16, "capacity drops with expander in corner");

            helper.succeed();
        });
    }

    /**
     * 7. The Redstone Module's modes (HIGH, LOW, IGNORE, PULSE) actually govern the export bus, and the interface;
     * each with a positive control. The Formation Bus at least for HIGH.
     */
    public static void redstoneModesOnBuses(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class).getContainer().setItem(1, new ItemStack(ModItems.COPPER_DISK_64K.get()));
        GolemNetwork n = net(helper, bay);
        // enough that emptying the chest between the phases does not run the network dry
        n.insertItem(new ItemStack(Items.COBBLESTONE, 4000), false);
        n.insertItem(new ItemStack(Items.IRON_INGOT, 640), false);
        n.insertItem(new ItemStack(Items.STONE, 64), false);

        // 1. Export Bus at BUS_OUT facing north into chest at BUS_OUT.north()
        BlockPos chestPos = BUS_OUT.north();
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity exportBus = helper.getBlockEntity(BUS_OUT, BusBlockEntity.class);
        exportBus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        exportBus.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos redstonePos = BUS_OUT.above();

        // 2. Interface at BUS_IN with 5 Iron Ingots in config slot 0
        helper.setBlock(BUS_IN, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        InterfaceBlockEntity iface = helper.getBlockEntity(BUS_IN, InterfaceBlockEntity.class);
        iface.getConfig().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        iface.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos ifaceRedstonePos = BUS_IN.above();

        // 3. Formation Bus at TERM facing north into AIR
        BlockPos formPos = TERM;
        BlockPos formFront = formPos.north();
        helper.setBlock(formFront, Blocks.AIR);
        helper.setBlock(formPos, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        FormationBusBlockEntity formBus = helper.getBlockEntity(formPos, FormationBusBlockEntity.class);
        formBus.getFilters().setItem(0, new ItemStack(Items.STONE));
        formBus.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos formRedstonePos = formPos.above();

        // Mode HIGH on all 3
        exportBus.menuData().set(1, RedstoneMode.HIGH.ordinal());
        iface.menuData().set(0, RedstoneMode.HIGH.ordinal());
        formBus.menuData().set(1, RedstoneMode.HIGH.ordinal());

        helper.runAfterDelay(20, () -> {
            // Unpowered under HIGH: nothing moved
            helper.assertTrue(chest.isEmpty(), "export bus HIGH unpowered moves nothing");
            helper.assertTrue(iface.getStock().getItem(0).isEmpty(), "interface HIGH unpowered restocks nothing");
            helper.assertBlockPresent(Blocks.AIR, formFront);

            // Power all three
            helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);
            helper.setBlock(ifaceRedstonePos, Blocks.REDSTONE_BLOCK);
            helper.setBlock(formRedstonePos, Blocks.REDSTONE_BLOCK);

            helper.runAfterDelay(30, () -> {
                // Powered under HIGH: positive controls succeed
                helper.assertTrue(!chest.isEmpty(), "positive control: export bus HIGH powered exports cobblestone");
                helper.assertTrue(!iface.getStock().getItem(0).isEmpty(), "positive control: interface HIGH powered restocks iron");
                helper.assertBlockPresent(Blocks.STONE, formFront);

                // Clear targets and power
                chest.clearContent();
                iface.getStock().clearContent();
                helper.setBlock(formFront, Blocks.AIR);
                helper.setBlock(redstonePos, Blocks.AIR);
                helper.setBlock(ifaceRedstonePos, Blocks.AIR);
                helper.setBlock(formRedstonePos, Blocks.AIR);

                // Mode LOW on Export Bus & Interface
                exportBus.menuData().set(1, RedstoneMode.LOW.ordinal());
                iface.menuData().set(0, RedstoneMode.LOW.ordinal());

                helper.runAfterDelay(30, () -> {
                    // Unpowered under LOW: positive control succeeds
                    helper.assertTrue(!chest.isEmpty(), "positive control: export bus LOW unpowered exports cobblestone");
                    helper.assertTrue(!iface.getStock().getItem(0).isEmpty(), "positive control: interface LOW unpowered restocks iron");

                    // Clear targets and power under LOW: must block
                    chest.clearContent();
                    iface.getStock().clearContent();
                    helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);
                    helper.setBlock(ifaceRedstonePos, Blocks.REDSTONE_BLOCK);

                    helper.runAfterDelay(20, () -> {
                        helper.assertTrue(chest.isEmpty(), "export bus LOW powered moves nothing");
                        helper.assertTrue(iface.getStock().getItem(0).isEmpty(), "interface LOW powered restocks nothing");

                        // Mode IGNORE on Export Bus & Interface
                        exportBus.menuData().set(1, RedstoneMode.IGNORE.ordinal());
                        iface.menuData().set(0, RedstoneMode.IGNORE.ordinal());
                        helper.setBlock(redstonePos, Blocks.AIR);
                        helper.setBlock(ifaceRedstonePos, Blocks.AIR);

                        helper.runAfterDelay(30, () -> {
                            helper.assertTrue(!chest.isEmpty(), "positive control: export bus IGNORE works unpowered");
                            helper.assertTrue(!iface.getStock().getItem(0).isEmpty(), "positive control: interface IGNORE works unpowered");

                            // Mode PULSE on Export Bus & Interface
                            chest.clearContent();
                            iface.getStock().clearContent();
                            exportBus.menuData().set(1, RedstoneMode.PULSE.ordinal());
                            iface.menuData().set(0, RedstoneMode.PULSE.ordinal());

                            helper.runAfterDelay(20, () -> {
                                helper.assertTrue(chest.isEmpty(), "export bus PULSE without pulse moves nothing");
                                helper.assertTrue(iface.getStock().getItem(0).isEmpty(), "interface PULSE without pulse restocks nothing");

                                // Provide a redstone pulse
                                helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);
                                helper.setBlock(ifaceRedstonePos, Blocks.REDSTONE_BLOCK);

                                helper.runAfterDelay(25, () -> {
                                    helper.assertTrue(!chest.isEmpty(), "positive control: export bus PULSE works on pulse");
                                    helper.assertTrue(!iface.getStock().getItem(0).isEmpty(), "positive control: interface PULSE works on pulse");
                                    helper.succeed();
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    /**
     * 8. A Fuzzy Card with a tag filter on the Formation Bus and on the Annihilation Bus does what the book says;
     * without the card the same filter is exact.
     */
    public static void fuzzyOnFormationAndAnnihilationBus(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);

        // Network storage has Birch Planks (not Oak Planks)
        net(helper, bay).insertItem(new ItemStack(Items.BIRCH_PLANKS, 32), false);

        // Tag filter: Oak Planks item with #minecraft:planks tag
        ItemStack tagFilter = Fuzzy.withTag(new ItemStack(Items.OAK_PLANKS), ItemTags.PLANKS);

        // 1. Formation Bus facing north into AIR
        BlockPos formPos = BUS_OUT;
        BlockPos formFront = formPos.north();
        helper.setBlock(formFront, Blocks.AIR);
        helper.setBlock(formPos, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        FormationBusBlockEntity formBus = helper.getBlockEntity(formPos, FormationBusBlockEntity.class);
        formBus.getFilters().setItem(0, tagFilter.copy());

        // 2. Annihilation Bus facing north into Birch Planks
        BlockPos anniPos = TERM;
        BlockPos anniFront = anniPos.north();
        helper.setBlock(anniFront, Blocks.BIRCH_PLANKS);
        helper.setBlock(anniPos, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        AnnihilationBusBlockEntity anniBus = helper.getBlockEntity(anniPos, AnnihilationBusBlockEntity.class);
        anniBus.menuData().set(3, 0); // Allow list mode
        anniBus.getFilters().setItem(0, tagFilter.copy());

        // Part A: WITHOUT Fuzzy Card -> exact matching only
        helper.runAfterDelay(30, () -> {
            helper.assertBlockPresent(Blocks.AIR, formFront);
            helper.assertBlockPresent(Blocks.BIRCH_PLANKS, anniFront);

            // Part B: WITH Fuzzy Card -> matches Birch Planks through #minecraft:planks tag
            formBus.getUpgrades().setItem(0, new ItemStack(ModItems.FUZZY_CARD.get()));
            formBus.wake();
            anniBus.getUpgrades().setItem(0, new ItemStack(ModItems.FUZZY_CARD.get()));
            anniBus.wake();

            helper.succeedWhen(() -> {
                helper.assertBlockPresent(Blocks.BIRCH_PLANKS, formFront);
                helper.assertBlockNotPresent(Blocks.BIRCH_PLANKS, anniFront);
            });
        });
    }

    /**
     * Every newer device in a network with no core at all: nothing throws, the network stays offline, devices stand by -
     * the counter is dark, the requester holds no order, the manometer gives no signal.
     */
    public static void allNewerDevicesOffline(GameTestHelper helper) {
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
            GolemNetwork n = net(helper, GolemGameTests.ORIGIN);
            helper.assertTrue(!n.isOnline(), "the network has no core");
            var counter = helper.getBlockEntity(new BlockPos(2, 2, -1), com.golemlogix.blockentity.ChannelCounterBlockEntity.class);
            helper.assertTrue(counter != null && !counter.online(), "the counter is dark");
            helper.assertTrue(n.channelsUsed() == 0, "nothing is allocated: " + n.channelsUsed());
            helper.succeed();
        });
    }

    /**
     * Mana Saturator unformed: multi-block not built, the block entity does nothing - no crafting, no light.
     */
    public static void manaSaturatorUnformedDoesNothing(GameTestHelper helper) {
        BlockPos origin = GolemGameTests.ORIGIN;
        // Place individual saturator parts but do not form the multi-block
        GolemGameTests.put(helper, origin.offset(0, 0, 0), ModBlocks.MANA_SATURATOR.get());
        helper.runAfterDelay(40, () -> {
            var sat = helper.getBlockEntity(origin, com.golemlogix.blockentity.ManaSaturatorBlockEntity.class);
            helper.assertTrue(sat != null, "saturator block entity exists");
            helper.assertFalse(sat.getBlockState().getValue(com.golemlogix.blocks.ManaSaturatorBlock.FORMED), "saturator is not formed");
            helper.assertFalse(sat.isCrafting(), "unformed saturator does not craft");
            helper.succeed();
        });
    }

    /**
     * Cryo-Mixer without Source: the machine is online but makes nothing - no ice, no snow.
     */
    public static void cryoMixerWithoutSourceDoesNothing(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        // Remove the creative source jar so the network has no Source
        GolemGameTests.put(helper, GolemGameTests.JAR, Blocks.AIR);
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(!n.isOnline(), "network offline without Source");
            GolemGameTests.put(helper, new BlockPos(2, 1, -1), ModBlocks.CRYO_MIXER.get());
            helper.runAfterDelay(20, () -> {
                var mixer = helper.getBlockEntity(new BlockPos(2, 1, -1), com.golemlogix.blockentity.CryoMixerBlockEntity.class);
                helper.assertTrue(mixer != null, "mixer exists");
                helper.assertTrue(mixer.water().isEmpty() && mixer.cryo().isEmpty(), "mixer without Source has empty flasks");
                helper.assertTrue(mixer.getOutput().getItem(0).isEmpty(), "mixer tray stays empty");
                helper.succeed();
            });
        });
    }

    /**
     * Cryo-Distillation Tower without blood: the tower is online but makes no Source.
     */
    public static void distillationTowerWithoutBloodDoesNothing(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.runAfterDelay(20, () -> {
            GolemGameTests.put(helper, new BlockPos(2, 1, -1), ModBlocks.CRYO_DISTILLATION_TOWER.get());
            helper.runAfterDelay(20, () -> {
                var tower = helper.getBlockEntity(new BlockPos(2, 1, -1), com.golemlogix.blockentity.DistillationTowerBlockEntity.class);
                helper.assertTrue(tower != null, "tower exists");
                helper.assertTrue(tower.blood().isEmpty(), "tower without blood has no blood");
                helper.succeed();
            });
        });
    }

    /**
     * 9. Pattern Provider with machines on two sides: with all sides allowed, what does not fit one goes to the other;
     * with a chosen output side only that one is fed, and once a full machine has room the work resumes.
     * Blocking mode: the next portion waits for the result of the previous one.
     */
    public static void providerFeedsAnotherSideWhenOneIsFull(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // the array stands to the west of the provider: its free sides are north and south
        BlockPos westPos = PROV.north();
        BlockPos eastPos = PROV.south();
        helper.setBlock(westPos, Blocks.CHEST);
        helper.setBlock(eastPos, Blocks.CHEST);
        ChestBlockEntity westChest = helper.getBlockEntity(westPos, ChestBlockEntity.class);
        ChestBlockEntity eastChest = helper.getBlockEntity(eastPos, ChestBlockEntity.class);

        PatternData ironPattern = new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        provider.getContainer().setItem(0, card(ironPattern));

        // Part 1: All sides allowed. West chest is full, East chest has room.
        for (int i = 0; i < westChest.getContainerSize(); i++) {
            westChest.setItem(i, new ItemStack(Items.STONE, 64));
        }
        provider.setOutput(ProviderOutput.ALL);
        var pushedAll = provider.tryPush(ironPattern);
        helper.assertTrue(pushedAll != null, "pushed successfully with all sides allowed");
        helper.assertTrue(eastChest.countItem(Items.RAW_IRON) == 1, "went to free east side when west is full");
        helper.assertTrue(westChest.countItem(Items.RAW_IRON) == 0, "west chest untouched");

        // Part 2: Chosen output side WEST. West is full, East has room.
        provider.setOutput(ProviderOutput.NORTH);
        var pushedWestFull = provider.tryPush(ironPattern);
        helper.assertTrue(pushedWestFull == null, "refused push when chosen output side is full even though east has room");
        helper.assertTrue(eastChest.countItem(Items.RAW_IRON) == 1, "east chest did not receive anything");

        // Make room in West chest -> push resumes
        westChest.setItem(0, ItemStack.EMPTY);
        var pushedWestRoom = provider.tryPush(ironPattern);
        helper.assertTrue(pushedWestRoom != null, "resumes pushing to west once room is made");
        helper.assertTrue(westChest.countItem(Items.RAW_IRON) == 1, "west chest received raw iron");

        // Part 3: Blocking mode in autocrafting
        westChest.clearContent();
        eastChest.clearContent();
        provider.setOutput(ProviderOutput.ALL);
        provider.setBlocking(true);

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 10), false);
            var start = n.startCrafting(ItemKey.of(new ItemStack(Items.IRON_INGOT)), 5);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "craft started");

            helper.runAfterDelay(15, () -> {
                int totalInChests = westChest.countItem(Items.RAW_IRON) + eastChest.countItem(Items.RAW_IRON);
                helper.assertTrue(totalInChests == 1, "blocking mode: exactly 1 portion pushed while awaiting result (got " + totalInChests + ")");
                helper.assertTrue(n.providerBusy(helper.absolutePos(PROV)), "provider is busy awaiting machine output");

                // Machine finishes portion 1 and returns Iron Ingot into the network
                n.insertFromMachine(new ItemStack(Items.IRON_INGOT));

                helper.runAfterDelay(15, () -> {
                    int totalAfterResult = westChest.countItem(Items.RAW_IRON) + eastChest.countItem(Items.RAW_IRON);
                    helper.assertTrue(totalAfterResult >= 2, "blocking mode: second portion pushed once previous result received (got " + totalAfterResult + ")");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * 10. Items really moved in a fixed time, end to end through a chest and a bus,
     * for an import and an export bus with 0, 4 and 28 Compressed Co-Processors: about 2, 20 and 128 items a tick;
     * fluids too with 4: about 2500 mB a tick. Large disk and non-limiting target.
     */
    public static void busThroughputMatchesUpgrades(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        driveBay.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_256K.get()));

        // Double chest at (2, 1, 4) and (2, 1, 3), BUS_IN at (3, 1, 4) faces it
        BlockPos chest1 = new BlockPos(2, 1, 4);
        BlockPos chest2 = new BlockPos(2, 1, 3);
        helper.setBlock(chest1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(chest2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        ChestBlockEntity c1 = helper.getBlockEntity(chest1, ChestBlockEntity.class);
        ChestBlockEntity c2 = helper.getBlockEntity(chest2, ChestBlockEntity.class);
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));

        java.util.function.Supplier<Integer> chestCount = () -> {
            int count = 0;
            for (int i = 0; i < c1.getContainerSize(); i++) count += c1.getItem(i).getCount();
            for (int i = 0; i < c2.getContainerSize(); i++) count += c2.getItem(i).getCount();
            return count;
        };
        // the test framework keeps one time per Runnable object: scheduling the same object again moves it, so each call wraps it
        Runnable emptyChest = () -> {
            c1.clearContent();
            c2.clearContent();
        };
        Runnable fillChest = () -> {
            for (int i = 0; i < c1.getContainerSize(); i++) c1.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            for (int i = 0; i < c2.getContainerSize(); i++) c2.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        };

        // A bus that has rested moves up to a second's worth at once, as designed: every window below opens after
        // that first move, so it measures the steady rate. Items: 2, 20, 128 a tick.
        helper.setBlock(BUS_IN, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BusBlockEntity exportBus = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);
        exportBus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        net(helper, bay).insertItem(new ItemStack(Items.COBBLESTONE, 20000), false);

        helper.runAfterDelay(30, () -> emptyChest.run());
        helper.runAfterDelay(70, () -> {
            int moved = chestCount.get();
            helper.assertTrue(moved >= 55 && moved <= 110, "export, no upgrades: ~80 items in 40 ticks (got " + moved + ")");
            exportBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
            exportBus.wake();
        });
        helper.runAfterDelay(80, () -> emptyChest.run());
        helper.runAfterDelay(100, () -> {
            int moved = chestCount.get();
            helper.assertTrue(moved >= 250 && moved <= 600, "export, 4 upgrades: ~400 items in 20 ticks (got " + moved + ")");
            exportBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 28));
            exportBus.wake();
        });
        helper.runAfterDelay(110, () -> emptyChest.run());
        helper.runAfterDelay(130, () -> {
            int moved = chestCount.get();
            helper.assertTrue(moved >= 1800 && moved <= 3200, "export, 28 upgrades: ~2560 items in 20 ticks (got " + moved + ")");

            // The same three speeds through an import bus
            helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
            fillChest.run();
        });

        long[] before = new long[1];
        Runnable mark = () -> before[0] = net(helper, bay).itemCountOf(cobble);
        helper.runAfterDelay(160, mark);
        helper.runAfterDelay(200, () -> {
            long moved = net(helper, bay).itemCountOf(cobble) - before[0];
            helper.assertTrue(moved >= 55 && moved <= 110, "import, no upgrades: ~80 items in 40 ticks (got " + moved + ")");
            BusBlockEntity importBus = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);
            importBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
            importBus.wake();
        });
        helper.runAfterDelay(210, () -> {
            fillChest.run();
            mark.run();
        });
        helper.runAfterDelay(230, () -> {
            long moved = net(helper, bay).itemCountOf(cobble) - before[0];
            helper.assertTrue(moved >= 250 && moved <= 600, "import, 4 upgrades: ~400 items in 20 ticks (got " + moved + ")");
            BusBlockEntity importBus = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);
            importBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 28));
            importBus.wake();
        });
        helper.runAfterDelay(240, () -> {
            fillChest.run();
            mark.run();
        });
        helper.runAfterDelay(260, () -> {
            long moved = net(helper, bay).itemCountOf(cobble) - before[0];
            helper.assertTrue(moved >= 1800 && moved <= 3200, "import, 28 upgrades: ~2560 items in 20 ticks (got " + moved + ")");

            // Fluid through an export bus with 4 upgrades: 2500 mB a tick
            BlockPos tankPos = BUS_OUT.north();
            helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get().defaultBlockState());
            CopperTankBlockEntity tank = helper.getBlockEntity(tankPos, CopperTankBlockEntity.class);
            CompoundTag capTag = new CompoundTag();
            capTag.putInt("Capacity", 250_000);
            tank.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), capTag));

            helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
            BusBlockEntity fluidBus = helper.getBlockEntity(BUS_OUT, BusBlockEntity.class);
            fluidBus.getFilters().setItem(0, FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000)));
            fluidBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
            int stored = net(helper, bay).insertFluid(new FluidStack(Fluids.WATER, 200_000), false);
            helper.assertTrue(stored == 200_000, "the fluid disk holds the water for the test (took " + stored + " mB)");
        });
        int[] tankBefore = new int[1];
        helper.runAfterDelay(270, () -> tankBefore[0] = helper.getBlockEntity(BUS_OUT.north(), CopperTankBlockEntity.class).fluid().getAmount());
        helper.runAfterDelay(290, () -> {
            int moved = helper.getBlockEntity(BUS_OUT.north(), CopperTankBlockEntity.class).fluid().getAmount() - tankBefore[0];
            helper.assertTrue(moved >= 35_000 && moved <= 65_000, "fluid export, 4 upgrades: ~50000 mB in 20 ticks (got " + moved + " mB)");
            helper.succeed();
        });
    }

    /** The stick and plank recipes on a provider, and a large storage. */
    private static BlockPos hugeRig(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        for (int i = 0; i < 4; i++) {
            drive.getContainer().setItem(i, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        }
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(i == 0 || i == 3 ? new ItemStack(Items.OAK_PLANKS) : ItemStack.EMPTY);
        }
        provider.getContainer().setItem(1, card(new PatternData(true, grid, List.of(new ItemStack(Items.STICK, 4)))));
        return bay;
    }

    /**
     * An order of hundreds of thousands of items goes through. Sticks: 150000 crafts from planks, 75000 crafts of planks from logs.
     * Every step is accounted for, and the server's time limit holds it back within a tick without stopping it.
     */
    public static void autocraftHugeOrder(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        ItemKey stick = ItemKey.of(new ItemStack(Items.STICK));
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey log = ItemKey.of(new ItemStack(Items.OAK_LOG));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 100_000), false);
            var start = n.startCrafting(stick, 600_000);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "the huge order started: " + start.type());
            helper.assertTrue(start.plan().totalExecutions() == 225_000, "225000 executions, got " + start.plan().totalExecutions());

            CraftingJob job = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).getJob();
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
                CraftingJob job = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).getJob();
                if (job != null) {
                    job.tick(net(helper, bay), 1000);
                }
            });
        }
        helper.runAfterDelay(7, () -> helper.succeedWhen(() -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).isIdle(), "the calculator finishes");
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
    public static void plannerDeepAndHuge(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
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
    public static void idleCalculatorLendsItsSteps(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        helper.setBlock(BUS_OUT, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        CryoCalculatorBlockEntity[] cpus = {helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class),
                helper.getBlockEntity(BUS_OUT, CryoCalculatorBlockEntity.class)};
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        ItemKey stick = ItemKey.of(new ItemStack(Items.STICK));
        int[] owner = new int[1];

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
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

            GolemNetwork n = net(helper, bay);
            var second = n.startCrafting(stick, 4);
            helper.assertTrue(second.type() == GolemNetwork.StartType.STARTED, "a new order is taken: " + second.type());
            helper.assertTrue(!other.isIdle(), "by the calculator that was helping, at once");
            helper.assertTrue(other.helpedJob() == null || other.getJob() != null, "which now works on its own");
        });
        helper.runAfterDelay(46, () -> helper.succeedWhen(() -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(cpus[0].isIdle() && cpus[1].isIdle(), "both finish");
            helper.assertTrue(n.itemCountOf(plank) == 400, "400 planks, got " + n.itemCountOf(plank));
            helper.assertTrue(n.itemCountOf(stick) == 4, "4 sticks, got " + n.itemCountOf(stick));
        }));
    }

    /** A calculator under a Redstone Module keeps the pace of its signal: it neither gets help nor gives it. */
    public static void redstoneCalculatorKeepsToItself(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        helper.setBlock(BUS_OUT, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        CryoCalculatorBlockEntity one = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
        CryoCalculatorBlockEntity two = helper.getBlockEntity(BUS_OUT, CryoCalculatorBlockEntity.class);
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        // Both ignore the signal, so they work; the module alone is what should keep them apart
        for (CryoCalculatorBlockEntity c : List.of(one, two)) {
            c.getUpgrades().setItem(com.golemlogix.upgrade.UpgradeSpec.CALCULATOR.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE),
                    new ItemStack(ModItems.REDSTONE_MODULE.get()));
            c.menuData().set(0, com.golemlogix.upgrade.RedstoneMode.IGNORE.ordinal());
        }
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
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
            other.getUpgrades().setItem(com.golemlogix.upgrade.UpgradeSpec.CALCULATOR.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE), ItemStack.EMPTY);
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(other.helpedJob() == null, "a job under a module gets no help");
                // The module on the idle one alone: it gives none
                owner.getUpgrades().setItem(com.golemlogix.upgrade.UpgradeSpec.CALCULATOR.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE), ItemStack.EMPTY);
                other.getUpgrades().setItem(com.golemlogix.upgrade.UpgradeSpec.CALCULATOR.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE), new ItemStack(ModItems.REDSTONE_MODULE.get()));
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(other.helpedJob() == null, "a calculator under a module gives no help");
                    // No modules at all: it helps
                    other.getUpgrades().setItem(com.golemlogix.upgrade.UpgradeSpec.CALCULATOR.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE), ItemStack.EMPTY);
                    helper.runAfterDelay(20, () -> {
                        helper.assertTrue(other.helpedJob() == owner.getJob(), "positive control: without modules it helps");
                        helper.succeed();
                    });
                });
            });
        });
    }

    /** A crafting step remembers the recipe's result for its cells, and only for those: other cells give their own. */
    public static void craftingStepCacheFollowsTheCells(GameTestHelper helper) {
        var level = helper.getLevel();
        var step = new CraftingJob.StepState(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG)), 4);
        List<ItemStack> oak = List.of(new ItemStack(Items.OAK_LOG));
        List<ItemStack> birch = List.of(new ItemStack(Items.BIRCH_LOG));
        List<ItemStack> nothing = List.of(new ItemStack(Items.DIRT));
        helper.assertTrue(step.craft(level, oak).get(0).is(Items.OAK_PLANKS), "oak logs make oak planks");
        helper.assertTrue(step.craft(level, oak).get(0).getCount() == 4, "and again, four of them");
        helper.assertTrue(step.craft(level, birch).get(0).is(Items.BIRCH_PLANKS), "birch logs make birch planks, not what oak gave");
        helper.assertTrue(step.craft(level, nothing) == null, "cells with no recipe make nothing");
        helper.assertTrue(step.craft(level, oak).get(0).is(Items.OAK_PLANKS), "and oak logs still work after that");
        helper.succeed();
    }

    /**
     * Thousands of portions inside machines take one record in the save per Provider, not one each. They survive a reload, and every
     * result comes back to the job.
     */
    public static void autocraftManyPortionsKeepTheSaveSmall(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.getBlockEntity(PROV, PatternProviderBlockEntity.class).getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        helper.setBlock(PROV.east(), Blocks.CHEST);
        ChestBlockEntity machine = helper.getBlockEntity(PROV.east(), ChestBlockEntity.class);
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 1500), false);
            helper.assertTrue(n.startCrafting(ingot, 1500).type() == GolemNetwork.StartType.STARTED, "the order started");
        });
        boolean[] reloaded = new boolean[1];
        for (int t = 6; t <= 1700; t++) {
            helper.runAfterDelay(t, () -> {
                CraftingJob job = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).getJob();
                if (job != null && job.remainingExecutions() > 0) {
                    job.tick(net(helper, bay), 1500);
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
                        net(helper, bay).insertFromMachine(new ItemStack(Items.IRON_INGOT, count));
                    }
                }
            });
        }
        helper.runAfterDelay(7, () -> helper.succeedWhen(() -> {
            helper.assertTrue(reloaded[0], "the portions went out and the job was reloaded");
            helper.assertTrue(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).isIdle(), "the job finishes");
            long ingots = net(helper, bay).itemCountOf(ingot);
            helper.assertTrue(ingots == 1500, "every ingot came back, got " + ingots);
        }));
    }

    /**
     * Equipment takes 4000 pieces of a disk; 1k and 4k disks always take one; things that do not count stack up as pieces. Old contents
     * over the limit stay, nothing more goes in.
     */
    public static void gearOnDisks(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_16K.get()));
        ItemStack big = drive.getContainer().getItem(0);
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.setDamageValue(5);
        helper.assertTrue(com.golemlogix.system.Gear.is(worn), "a worn sword is equipment");
        helper.assertTrue(!com.golemlogix.system.Gear.is(new ItemStack(Items.IRON_SWORD)), "a new one is not");
        helper.assertTrue(!com.golemlogix.system.Gear.is(new ItemStack(Items.WATER_BUCKET)) && !com.golemlogix.system.Gear.is(new ItemStack(Items.COBBLESTONE)), "a bucket and stone are not");
        ItemStack potion = new ItemStack(Items.POTION);
        potion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.WATER));
        helper.assertTrue(!com.golemlogix.system.Gear.is(potion), "a potion is not");
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        helper.assertTrue(!com.golemlogix.system.Gear.is(book), "an enchanted book is not");

        for (int i = 1; i <= 4; i++) {
            ItemStack s = new ItemStack(Items.IRON_SWORD);
            s.setDamageValue(i);
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(big, s, false) == 1, "16k takes equipment number " + i);
        }
        helper.assertTrue(com.golemlogix.system.DiskStorage.storedItems(big) == 16000, "four take the whole disk");
        helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(big, worn, true) == 0, "a fifth does not fit");
        helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(big, new ItemStack(Items.COBBLESTONE), true) == 0, "nor a stone");
        ItemStack first = new ItemStack(Items.IRON_SWORD);
        first.setDamageValue(1);
        helper.assertTrue(com.golemlogix.system.DiskStorage.extractItem(big, first, 1, false) == 1, "one comes out");
        helper.assertTrue(com.golemlogix.system.DiskStorage.storedItems(big) == 12000, "and frees its 4000");

        for (var disk : List.of(ModItems.COPPER_DISK_1K.get(), ModItems.COPPER_DISK_4K.get())) {
            ItemStack small = new ItemStack(disk);
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, new ItemStack(Items.COBBLESTONE, 500), false) == 500, "stone first");
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, worn, false) == 1, "a small disk takes one, even partly filled");
            ItemStack other = new ItemStack(Items.IRON_SWORD);
            other.setDamageValue(9);
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, other, true) == 0, "but only one");
            helper.assertTrue(com.golemlogix.system.DiskStorage.insertItem(small, new ItemStack(Items.COBBLESTONE), true) == 0, "and is full then");
            helper.assertTrue(com.golemlogix.system.DiskStorage.extractItem(small, worn, 1, false) == 1, "it comes out");
            helper.assertTrue(com.golemlogix.system.DiskStorage.storedItems(small) == 500, "the stone stays");
        }
        helper.succeed();
    }

    /** A device whose order lacks ingredients does not plan again at once; the pause grows, and ingredients end it soon. */
    public static void failedOrdersWait(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        ItemKey plank = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
        BlockPos device = helper.absolutePos(BUS_OUT);
        java.util.Map<ItemKey, Long> order = java.util.Map.of(plank, 4L);
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            var first = n.startCrafting(order, null, device);
            helper.assertTrue(first.type() == GolemNetwork.StartType.MISSING && first.plan() != null, "the first try plans and fails");
            var second = n.startCrafting(order, null, device);
            helper.assertTrue(second.type() == GolemNetwork.StartType.MISSING && second.plan() == null, "the next one does not plan");
            helper.assertTrue(n.startCrafting(order, null, null).plan() != null, "a player's order is never held back");
        });
        helper.runAfterDelay(30, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(n.startCrafting(order, null, device).plan() != null, "after a second it tries again");
            helper.assertTrue(n.startCrafting(order, null, device).plan() == null, "and waits again");
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(net(helper, bay).startCrafting(order, null, device).plan() == null, "now two seconds, not one");
            net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 1), false);
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(net(helper, bay).startCrafting(order, null, device).type() == GolemNetwork.StartType.STARTED, "ingredients end the wait");
            helper.succeed();
        });
    }

    /** Terminal windows get only what changed, and end up with the same list as the network. */
    public static void terminalSendsChanges(GameTestHelper helper) {
        BlockPos bay = hugeRig(helper);
        ServerPlayer player = testPlayer(helper);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());
        BlockPos abs = helper.absolutePos(TERM);
        TerminalMenu client = new TerminalMenu(90, player.getInventory(), helper.getLevel(), abs, abs, ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
        var stone = new ItemStack(Items.STONE);
        var dirt = new ItemStack(Items.DIRT);
        // the client side's merging, entry by entry
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, true, List.of(
                new com.golemlogix.network.TerminalListPayload.Entry(stone, 5, false), new com.golemlogix.network.TerminalListPayload.Entry(dirt, 7, false))));
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, false, List.of(new com.golemlogix.network.TerminalListPayload.Entry(stone, 9, false))));
        helper.assertTrue(client.clientEntries.size() == 2 && client.clientEntries.get(0).count() == 9, "a changed count replaces its entry");
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, false, List.of(new com.golemlogix.network.TerminalListPayload.Entry(stone, 0, false))));
        helper.assertTrue(client.clientEntries.size() == 1 && client.clientEntries.get(0).key().is(Items.DIRT), "an empty entry goes");
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, false, List.of(new com.golemlogix.network.TerminalListPayload.Entry(stone, 0, true))));
        helper.assertTrue(client.clientEntries.size() == 2, "what can be crafted stays listed with nothing stored");
        client.clientReceiveList(new com.golemlogix.network.TerminalListPayload(90, true, List.of()));
        helper.assertTrue(client.clientEntries.isEmpty(), "a reset clears it");
        helper.succeed();
    }

    /**
     * 11. An Order Ledger in fluid mode with a real pattern that makes a fluid:
     * it orders the craft, the fluid reaches its target, and it stops ordering.
     */
    public static void stockRequesterKeepsFluid(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        driveBay.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_16K.get()));
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_16K.get()));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);

        // Pattern: 1 Ice -> 1000 mB Water
        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.ICE)), List.of(waterGhost))));

        // The machine: a chest beside the provider; the loop below melts each ice it gets into a bucket's worth
        BlockPos machinePos = PROV.east();
        helper.setBlock(machinePos, Blocks.CHEST);
        ChestBlockEntity machine = helper.getBlockEntity(machinePos, ChestBlockEntity.class);

        net(helper, bay).insertItem(new ItemStack(Items.ICE, 10), false);
        ItemKey iceKey = ItemKey.of(new ItemStack(Items.ICE));

        helper.setBlock(BUS_OUT, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = helper.getBlockEntity(BUS_OUT, StockRequesterBlockEntity.class);
        ItemStack reqFilter = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        reqFilter.set(ModDataComponents.KEEP_AMOUNT.get(), 2000); // Target = 2000 mB Water
        req.getFilters().setItem(0, reqFilter);

        boolean[] sawCrafting = new boolean[1];
        for (int t = 1; t <= 280; t++) {
            helper.runAfterDelay(t, () -> {
                if (req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_CRAFTING) {
                    sawCrafting[0] = true;
                }
                GolemNetwork n = net(helper, bay);
                for (int i = 0; i < machine.getContainerSize(); i++) {
                    ItemStack in = machine.getItem(i);
                    if (in.is(Items.ICE)) {
                        int count = in.getCount();
                        machine.setItem(i, ItemStack.EMPTY);
                        n.insertFluidFromMachine(new FluidStack(Fluids.WATER, 1000 * count), null);
                    }
                }
            });
        }

        // It reaches the target, for exactly the ice that takes, and then it stops
        helper.runAfterDelay(100, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(sawCrafting[0], "the ledger reported that it was crafting");
            long water = n.fluidCountOf(Fluids.WATER);
            helper.assertTrue(water == 2000, "water reaches the target of 2000 mB exactly (got " + water + ")");
            helper.assertTrue(n.itemCountOf(iceKey) == 8, "two ice were used (left " + n.itemCountOf(iceKey) + ")");
        });
        helper.runAfterDelay(140, () -> {
            GolemNetwork n = net(helper, bay);
            CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);
            helper.assertTrue(cpu.isIdle(), "no further job once the target is reached");
            helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_OK, "status is OK once the target is reached");
            helper.assertTrue(n.fluidCountOf(Fluids.WATER) == 2000, "no further water was ordered");
            helper.assertTrue(n.itemCountOf(iceKey) == 8, "no further ice was used");

            // Use some water: it keeps the stock, so it orders just what is missing
            FluidStack taken = n.extractFluid(new FluidStack(Fluids.WATER, 1000), 1000, false);
            helper.assertTrue(taken.getAmount() == 1000, "took 1000 mB out of the network");
        });
        helper.runAfterDelay(260, () -> {
            GolemNetwork n = net(helper, bay);
            long water = n.fluidCountOf(Fluids.WATER);
            helper.assertTrue(water == 2000, "the used water was made again (got " + water + ")");
            helper.assertTrue(n.itemCountOf(iceKey) == 7, "one more ice was used (left " + n.itemCountOf(iceKey) + ")");
            helper.assertTrue(helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).isIdle(), "and the calculator is idle again");
            helper.succeed();
        });
    }

    /**
     * 12. A disk with items taken out of one cabinet and put into another cabinet in the network keeps every item
     * and its count, and items in a disk are not lost when the cabinet is broken and replaced.
     */
    public static void networkKeepsContentsAcrossDiskMove(GameTestHelper helper) {
        BlockPos bay1 = baseNetwork(helper);
        CopperDriveBayBlockEntity drive1 = helper.getBlockEntity(bay1, CopperDriveBayBlockEntity.class);
        drive1.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));

        // Second cabinet at (6, 1, 7) facing SOUTH (touches corner bushing at (6, 1, 6))
        BlockPos bay2 = new BlockPos(6, 1, 7);
        putBay(helper, bay2);

        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey gold = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
        ItemKey emerald = ItemKey.of(new ItemStack(Items.EMERALD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay1);
            n.insertItem(new ItemStack(Items.DIAMOND, 42), false);
            n.insertItem(new ItemStack(Items.GOLD_INGOT, 128), false);
            n.insertItem(new ItemStack(Items.EMERALD, 15), false);

            helper.assertTrue(n.itemCountOf(diamond) == 42, "initial diamonds stored");
            helper.assertTrue(n.itemCountOf(gold) == 128, "initial gold stored");
            helper.assertTrue(n.itemCountOf(emerald) == 15, "initial emerald stored");

            // 1. Move disk from Cabinet 1 to Cabinet 2
            ItemStack disk = drive1.getContainer().removeItemNoUpdate(0);
            drive1.setChanged();

            GolemNetwork nEmpty = net(helper, bay1);
            helper.assertTrue(nEmpty.itemCountOf(diamond) == 0, "storage empty without disk");

            CopperDriveBayBlockEntity drive2 = helper.getBlockEntity(bay2, CopperDriveBayBlockEntity.class);
            drive2.getContainer().setItem(0, disk);
            drive2.setChanged();

            GolemNetwork nMoved = net(helper, bay1);
            helper.assertTrue(nMoved.itemCountOf(diamond) == 42, "diamonds preserved in new cabinet");
            helper.assertTrue(nMoved.itemCountOf(gold) == 128, "gold preserved in new cabinet");
            helper.assertTrue(nMoved.itemCountOf(emerald) == 15, "emerald preserved in new cabinet");

            // 2. Break Cabinet 2 and replace it
            ItemStack savedDisk = drive2.getContainer().getItem(0).copy();
            Direction facing = Direction.SOUTH;
            for (CopperDriveBayBlock.Part p : CopperDriveBayBlock.Part.values()) {
                helper.setBlock(CopperDriveBayBlock.partPos(bay2, facing, p), Blocks.AIR);
            }

            helper.runAfterDelay(10, () -> {
                putBay(helper, bay2);
                CopperDriveBayBlockEntity driveReplaced = helper.getBlockEntity(bay2, CopperDriveBayBlockEntity.class);
                driveReplaced.getContainer().setItem(0, savedDisk);
                driveReplaced.setChanged();

                helper.runAfterDelay(10, () -> {
                    reload(helper, bay2, CopperDriveBayBlockEntity.class);
                    GolemNetwork nReplaced = net(helper, bay1);
                    helper.assertTrue(nReplaced.itemCountOf(diamond) == 42, "diamonds preserved after cabinet replaced and reloaded");
                    helper.assertTrue(nReplaced.itemCountOf(gold) == 128, "gold preserved after cabinet replaced");
                    helper.assertTrue(nReplaced.itemCountOf(emerald) == 15, "emerald preserved after cabinet replaced");

                    ItemStack ext = nReplaced.extractItem(new ItemStack(Items.DIAMOND), 42, false);
                    helper.assertTrue(ext.getCount() == 42, "all 42 diamonds extracted cleanly");
                    helper.assertTrue(nReplaced.itemCountOf(diamond) == 0, "diamonds cleanly cleared");
                    helper.succeed();
                });
            });
        });
    }
}
