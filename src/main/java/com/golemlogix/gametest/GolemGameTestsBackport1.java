package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests backported from main (2.1.4): Copper Tank, Mason's Formation Bus, Hewer's Annihilation Bus and the Export Bus.
 * Shared helpers (array, network, test player) live in {@link GolemGameTests}.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport1 {
    private static final String T = "empty";

    /** The same point as GolemGameTests.BUS_OUT: next to a corner bushing of the array. */
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    private static int count(Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    // ----------------------------------------------------------- copper tank

    /** A Copper Tank broken with fluid in it drops itself with that fluid and capacity; placed again, the fluid is back. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void copperTankKeepsFluid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1), again = new BlockPos(3, 1, 1);
        helper.setBlock(pos, ModBlocks.COPPER_TANK.get());
        var be = (com.golemlogix.blockentity.CopperTankBlockEntity) helper.getBlockEntity(pos);
        int in = be.tank().fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 20_000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(in == 16_000, "one tank takes 16 000 mB, took " + in);
        be.tank().drain(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 11_000), IFluidHandler.FluidAction.EXECUTE);
        BlockPos abs = helper.absolutePos(pos);
        List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(helper.getBlockState(pos), helper.getLevel(), abs, be);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModBlocks.COPPER_TANK.get().asItem()), "the tank drops itself: " + drops);
        ItemStack item = drops.get(0);
        helper.assertTrue(com.golemlogix.items.CopperTankItem.fluid(item).getAmount() == 5_000
                && com.golemlogix.items.CopperTankItem.fluid(item).is(net.minecraft.world.level.material.Fluids.WATER), "with its water: " + item.getComponents());
        helper.setBlock(again, ModBlocks.COPPER_TANK.get());
        var placed = (com.golemlogix.blockentity.CopperTankBlockEntity) helper.getBlockEntity(again);
        placed.applyComponentsFromItemStack(item);
        helper.assertTrue(placed.fluid().getAmount() == 5_000 && placed.capacity() == 16_000 && placed.signal() == 5,
                "placed again it holds 5 000 of 16 000 mB: " + placed.fluid().getAmount() + "/" + placed.capacity() + ", signal " + placed.signal());
        helper.succeed();
    }

    /** Copper Tanks joined in a crafting grid: the capacities and the fluid add up; two different fluids do not join. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void copperTankJoins(GameTestHelper helper) {
        ItemStack water = new ItemStack(ModBlocks.COPPER_TANK.get());
        water.set(com.golemlogix.init.ModDataComponents.TANK_FLUID.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(
                new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 3_000)));
        ItemStack big = new ItemStack(ModBlocks.COPPER_TANK.get());
        big.set(com.golemlogix.init.ModDataComponents.TANK_CAPACITY.get(), 32_000);
        ItemStack lava = new ItemStack(ModBlocks.COPPER_TANK.get());
        lava.set(com.golemlogix.init.ModDataComponents.TANK_FLUID.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(
                new FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1_000)));
        ItemStack joined = com.golemlogix.recipe.CopperTankJoinRecipe.join(CraftingInput.of(3, 1, List.of(water, ItemStack.EMPTY, big)));
        helper.assertTrue(com.golemlogix.items.CopperTankItem.capacity(joined) == 48_000
                && com.golemlogix.items.CopperTankItem.fluid(joined).getAmount() == 3_000, "16 000 + 32 000 mB with the water: " + joined.getComponents());
        helper.assertTrue(com.golemlogix.recipe.CopperTankJoinRecipe.join(CraftingInput.of(2, 1, List.of(water, lava))).isEmpty(),
                "water and lava do not join");
        helper.assertTrue(com.golemlogix.recipe.CopperTankJoinRecipe.join(CraftingInput.of(1, 1, List.of(water))).isEmpty(),
                "one tank is no recipe");
        helper.succeed();
    }

    /** Bucket mode: a full tank pours a bucket's worth into the air, then scoops the source back up. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void copperTankBucket(GameTestHelper helper) {
        BlockPos floor = new BlockPos(2, 1, 2), spot = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        ItemStack tank = new ItemStack(ModBlocks.COPPER_TANK.get());
        tank.set(com.golemlogix.init.ModDataComponents.TANK_FLUID.get(), net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(
                new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2_000)));
        var contents = new com.golemlogix.items.CopperTankItem.Contents(tank);
        helper.assertTrue(com.golemlogix.items.CopperTankItem.bucket(contents, null, helper.getLevel(), helper.absolutePos(floor), Direction.UP),
                "pours onto the stone");
        helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(spot)).isSourceOfType(net.minecraft.world.level.material.Fluids.WATER),
                "a water source above the stone");
        helper.assertTrue(com.golemlogix.items.CopperTankItem.fluid(tank).getAmount() == 1_000, "one bucket left: " + tank.getComponents());
        helper.assertTrue(com.golemlogix.items.CopperTankItem.bucket(contents, null, helper.getLevel(), helper.absolutePos(spot), Direction.UP),
                "scoops it back");
        helper.assertTrue(com.golemlogix.items.CopperTankItem.fluid(tank).getAmount() == 2_000
                && helper.getLevel().getFluidState(helper.absolutePos(spot)).isEmpty(), "two buckets again, the water gone");
        helper.succeed();
    }

    // ----------------------------------------------------------- golem buses

    /**
     * Mason's Formation Bus: the copper golem takes stone named in the filter out of the network and sets it down in front; when the
     * block is taken away it sets down the next one.
     */
    @GameTest(template = T, timeoutTicks = 200)
    public static void formationBusPlaces(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.AIR);
        var be = (com.golemlogix.blockentity.FormationBusBlockEntity) helper.getBlockEntity(bus);
        be.getFilters().setItem(0, new ItemStack(Items.STONE));
        helper.runAfterDelay(5, () -> GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.STONE, 3), false));
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.STONE))) == 2, "one stone left the network");
            helper.setBlock(front, Blocks.AIR);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.STONE))) == 1, "the next stone went down");
            helper.succeed();
        });
    }

    /**
     * Hewer's Annihilation Bus with no tool: the iron golem mines stone into the network as cobblestone (an iron pickaxe's work),
     * leaves obsidian alone (iron cannot harvest it), and with "grown only" leaves young wheat but takes ripe wheat.
     */
    @GameTest(template = T, timeoutTicks = 400)
    public static void annihilationBusMines(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = (com.golemlogix.blockentity.AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        helper.runAfterDelay(80, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1, "the stone is cobblestone in the network");
            helper.setBlock(front, Blocks.OBSIDIAN);
        });
        helper.runAfterDelay(160, () -> {
            helper.assertBlockPresent(Blocks.OBSIDIAN, front);
            be.menuData().set(4, 1);
            helper.setBlock(front.below(), Blocks.FARMLAND);
            helper.setBlock(front, Blocks.WHEAT.defaultBlockState());
        });
        helper.runAfterDelay(220, () -> {
            helper.assertBlockPresent(Blocks.WHEAT, front);
            helper.setBlock(front, ((net.minecraft.world.level.block.CropBlock) Blocks.WHEAT).getStateForAge(7));
        });
        helper.runAfterDelay(300, () -> {
            helper.assertBlockNotPresent(Blocks.WHEAT, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.WHEAT))) >= 1, "ripe wheat harvested into the network");
            helper.succeed();
        });
    }

    /** Hewer's Annihilation Bus on right click with shears: it carves the pumpkin in front and the seeds go into the network. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusClicks(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.PUMPKIN);
        var be = (com.golemlogix.blockentity.AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        be.menuData().set(1, com.golemlogix.blockentity.AnnihilationBusBlockEntity.RIGHT);
        be.getTool().setItem(0, new ItemStack(Items.SHEARS));
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.CARVED_PUMPKIN, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.PUMPKIN_SEEDS))) >= 1, "the seeds went into the network");
            helper.assertTrue(be.getTool().getItem(0).is(Items.SHEARS) && be.getTool().getItem(0).getDamageValue() == 1, "the shears wore a little");
            helper.succeed();
        });
    }

    /** Mason's Formation Bus round robin: stone, then a log, then stone again, as the spot in front is cleared. */
    @GameTest(template = T, timeoutTicks = 240)
    public static void formationBusRoundRobin(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.AIR);
        var be = (com.golemlogix.blockentity.FormationBusBlockEntity) helper.getBlockEntity(bus);
        be.getFilters().setItem(0, new ItemStack(Items.STONE));
        be.getFilters().setItem(1, new ItemStack(Items.OAK_LOG));
        be.menuData().set(2, com.golemlogix.blockentity.FormationBusBlockEntity.ROUND_ROBIN);
        helper.runAfterDelay(5, () -> {
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.STONE, 5), false);
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 5), false);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.setBlock(front, Blocks.AIR);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertBlockPresent(Blocks.OAK_LOG, front);
            helper.setBlock(front, Blocks.AIR);
        });
        helper.runAfterDelay(180, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.succeed();
        });
    }

    // ----------------------------------------------------------- export bus

    /** The Export Bus round robin: with plenty of both, cobblestone and dirt both go out (in order only the first would). */
    @GameTest(template = T, timeoutTicks = 120)
    public static void exportBusRoundRobin(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_OUT);
        bus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        bus.getFilters().setItem(1, new ItemStack(Items.DIRT));
        bus.menuData().set(2, com.golemlogix.blockentity.FormationBusBlockEntity.ROUND_ROBIN);
        BlockPos chest = new BlockPos(6, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest);
        helper.runAfterDelay(5, () -> {
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.COBBLESTONE, 64), false);
            GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIRT, 64), false);
        });
        List<net.minecraft.world.item.Item> history = new ArrayList<>();
        int[] last = {0, 0};
        for (int t = 6; t < 60; t++) {
            helper.runAfterDelay(t, () -> {
                int cobble = count(c, Items.COBBLESTONE);
                int dirt = count(c, Items.DIRT);
                if (cobble > last[0]) {
                    history.add(Items.COBBLESTONE);
                    last[0] = cobble;
                }
                if (dirt > last[1]) {
                    history.add(Items.DIRT);
                    last[1] = dirt;
                }
            });
        }
        helper.runAfterDelay(60, () -> {
            int cobble = 0, dirt = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                cobble += st.is(Items.COBBLESTONE) ? st.getCount() : 0;
                dirt += st.is(Items.DIRT) ? st.getCount() : 0;
            }
            helper.assertTrue(cobble > 0 && dirt > 0, "round robin sends both: cobblestone " + cobble + ", dirt " + dirt);
            helper.assertTrue(history.size() >= 3, "multiple exports occurred, got " + history.size());
            for (int i = 0; i < history.size() - 1; i++) {
                helper.assertTrue(history.get(i) != history.get(i + 1), "items must strictly alternate: " + history);
            }
            helper.succeed();
        });
    }

    /** The Export Bus keeping 10 cobblestone in a chest: it tops the chest up to 10, and again after some is taken out. */
    @GameTest(template = T, timeoutTicks = 160)
    public static void exportBusKeeps(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_OUT);
        ItemStack filter = new ItemStack(Items.COBBLESTONE);
        filter.set(com.golemlogix.init.ModDataComponents.KEEP_AMOUNT.get(), 10);
        bus.getFilters().setItem(0, filter);
        BlockPos chest = new BlockPos(6, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest);
        helper.runAfterDelay(5, () -> GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.COBBLESTONE, 64), false));
        java.util.function.IntSupplier inChest = () -> {
            int n = 0;
            for (int i = 0; i < c.getContainerSize(); i++) {
                n += c.getItem(i).is(Items.COBBLESTONE) ? c.getItem(i).getCount() : 0;
            }
            return n;
        };
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(inChest.getAsInt() == 10, "the chest holds exactly 10, got " + inChest.getAsInt());
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (c.getItem(i).is(Items.COBBLESTONE)) {
                    c.getItem(i).shrink(4);
                    break;
                }
            }
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(inChest.getAsInt() == 10, "topped up to 10 again, got " + inChest.getAsInt());
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 64 - 14, "14 went out in all");
            helper.succeed();
        });
    }

    /** The Export Bus slot window's server side: a keep amount is set on the ghost, 0 takes it off, a negative amount clears the slot. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void exportBusSlotWindow(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
        bus.getFilters().setItem(0, new ItemStack(Items.COAL));
        ServerPlayer player = GolemGameTests.testPlayer(helper);
        var menu = new com.golemlogix.menu.BusMenu(55, player.getInventory(), bus.getFilters(), bus.getUpgrades(),
                com.golemlogix.menu.BusMenu.KIND_EXPORT, bus.menuData());
        menu.setKeep(0, 16);
        helper.assertTrue(menu.keepAt(0) == 16 && bus.getFilters().getItem(0).is(Items.COAL), "16 coal to keep, the filter stays coal");
        helper.assertTrue(com.golemlogix.system.Fuzzy.matches(bus.getFilters().getItem(0), new ItemStack(Items.COAL), false),
                "the keep amount does not stop the filter matching coal");
        menu.setKeep(0, 0);
        helper.assertTrue(menu.keepAt(0) == 0 && !bus.getFilters().getItem(0).isEmpty(), "0: no limit, the filter stays");
        menu.setKeep(0, -1);
        helper.assertTrue(bus.getFilters().getItem(0).isEmpty(), "a negative amount clears the slot");
        helper.succeed();
    }
}
