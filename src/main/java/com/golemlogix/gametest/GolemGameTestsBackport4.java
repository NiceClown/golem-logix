package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Annihilation Bus game tests backported from main's GolemGameTestsExtra: deny list, right-click mode,
 * attack mode, redstone modes, offline network and vertical facings.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport4 {
    private static final String T = "empty";

    /** Same points as in GolemGameTests (kept here so this class does not depend on their visibility). */
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos CORE = ORIGIN.offset(0, 3, 0);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    /** Positive control helper: places stone at front, wakes the bus, and asserts it mines stone into cobblestone in network. */
    private static void assertMinesStone(GameTestHelper helper, BlockPos bay, BlockPos bus, BlockPos front) {
        long before = GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE)));
        helper.setBlock(front, Blocks.STONE);
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        if (be != null) {
            be.wake();
        }
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) > before,
                    "positive control: bus must mine stone");
        });
    }

    private static AnnihilationBusBlockEntity placeBus(GameTestHelper helper, BlockPos bus, Direction facing) {
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, facing));
        return (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
    }

    /** Deny list blocks the filtered item, but allows other blocks. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusDenyListFiltersItem(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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

    /** Right click mode using Bone Meal on a sapling. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusRightClickBoneMeal(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusRightClickShearsOnSheep(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusRightClickBucketWater(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        be.getTool().setItem(0, new ItemStack(Items.BUCKET));
        helper.setBlock(front, Blocks.WATER);

        helper.runAfterDelay(70, () -> {
            GolemNetwork net = GolemGameTests.net(helper, bay);
            long buckets = net.itemCountOf(ItemKey.of(new ItemStack(Items.WATER_BUCKET)));
            helper.assertTrue(buckets == 1 && be.getTool().getItem(0).isEmpty(),
                    "bucket collected water, got buckets in net: " + buckets + ", tool: " + be.getTool().getItem(0));
            helper.succeed();
        });
    }

    /** Right click toggles a lever. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusRightClickLever(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.LEVER.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.POWERED, false));
        be.wake();

        helper.succeedWhen(() -> {
            helper.assertTrue(be.lastUse() > 0, "the bus interacted with the lever");
            helper.assertTrue(helper.getBlockState(front).getValue(LeverBlock.POWERED), "lever POWERED state changed to true");
        });
    }

    /** Attack mode ignores tamed pets, named mobs, and armor stands. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusAttackIgnoresPetsAndNamedAndArmorStands(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.AIR);

        // Named Pig
        var namedPig = helper.spawnWithNoFreeWill(EntityType.PIG, front);
        namedPig.setCustomName(Component.literal("PetPig"));

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
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusAttackGrownOnlySparesBabies(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusAttackSpawnEggFilter(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusAttackNoKnockback(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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

    /** Redstone modes: HIGH, LOW, IGNORE, PULSE. */
    @GameTest(template = T, timeoutTicks = 350)
    public static void annihilationBusRedstoneModes(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        var be = placeBus(helper, bus, Direction.NORTH);

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
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusNetworkOfflineStops(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        placeBus(helper, bus, Direction.NORTH);
        helper.setBlock(front, Blocks.STONE);

        // Remove Core -> network goes offline
        helper.setBlock(CORE, Blocks.AIR);

        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            // Positive control: place Core back, bus must now mine stone
            GolemGameTests.put(helper, CORE, ModBlocks.ATLAS_CORE.get());
            var core = (AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            if (core != null) {
                core.refresh(helper.getLevel(), helper.absolutePos(CORE));
            }
            assertMinesStone(helper, bay, bus, front);
        });
    }

    /** Bus works in UP and DOWN directions. */
    @GameTest(template = T, timeoutTicks = 250)
    public static void annihilationBusFacingUpAndDown(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT;
        BlockPos above = bus.above();

        // Facing UP
        placeBus(helper, bus, Direction.UP);
        helper.setBlock(above, Blocks.STONE);

        helper.runAfterDelay(70, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, above);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1,
                    "stone above was mined");

            // Facing DOWN
            BlockPos below = bus.below();
            var be = placeBus(helper, bus, Direction.DOWN);
            helper.setBlock(below, Blocks.STONE);
            if (be != null) {
                be.wake();
            }
            helper.succeedWhen(() -> {
                helper.assertBlockNotPresent(Blocks.STONE, below);
                helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 2,
                        "stone below was mined");
            });
        });
    }
}
