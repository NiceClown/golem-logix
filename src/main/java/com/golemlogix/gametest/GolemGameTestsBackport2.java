package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.menu.AnnihilationBusMenu;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Game tests backported from main (2.1.x): Hewer's Annihilation Bus (attack, refill, temper, window, unbreakable blocks,
 * liquids and air), the Formation Bus window, intake, bus load adaptation, fluid autocraft output, the periscope's filter and
 * access modes, and the Import Bus deny list.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport2 {
    private static final String T = "empty";

    // the same layout as GolemGameTests
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos BAY = new BlockPos(7, 1, 4);
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos BUS_IN = new BlockPos(3, 1, 4);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    // ------------------------------------------------------------ helpers

    private static GolemNetwork net(GameTestHelper helper, BlockPos rel) {
        return GolemGameTests.net(helper, rel);
    }

    private static int count(Container c, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                n += c.getItem(i).getCount();
            }
        }
        return n;
    }

    private static void periscope(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(com.golemlogix.blocks.PeriscopeStorageBusBlock.FACING, facing));
    }

    /**
     * The window a bare-handed click on a golem bus opens, built the way the server builds it (a FakePlayer cannot open windows, and
     * a mock player's connection refuses the mod's packets), handed to the test player as its open window.
     */
    private static ServerPlayer clickOpen(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = GolemGameTests.testPlayer(helper);
        BlockPos abs = helper.absolutePos(rel);
        player.moveTo(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 2.5, 180f, 30f);
        var block = (com.golemlogix.blocks.GolemPlaneBlock) helper.getBlockState(rel).getBlock();
        var provider = block.menu(helper.getLevel().getBlockEntity(abs));
        helper.assertTrue(provider != null, "the bus has a window");
        player.containerMenu = provider.createMenu(77, player.getInventory(), player);
        return player;
    }

    /** No two slots of a window overlap, and the window's own slots stay inside it (the upgrades in their column to the right). */
    private static void assertSlotsLaidOut(GameTestHelper helper, AbstractContainerMenu menu, int width, int height) {
        for (int i = 0; i < menu.slots.size(); i++) {
            var a = menu.slots.get(i);
            helper.assertTrue(a.x >= 0 && a.y >= 0 && a.y + 16 <= height && a.x + 16 <= width + 30, "slot " + i + " is inside the window");
            for (int j = i + 1; j < menu.slots.size(); j++) {
                var b = menu.slots.get(j);
                helper.assertTrue(Math.abs(a.x - b.x) >= 18 || Math.abs(a.y - b.y) >= 18, "slots " + i + " and " + j + " overlap");
            }
        }
    }

    /** Positive control helper: places stone at front, wakes the bus, and asserts it mines stone into cobblestone in network. */
    private static void assertMinesStone(GameTestHelper helper, BlockPos bay, BlockPos bus, BlockPos front) {
        long before = net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE)));
        helper.setBlock(front, Blocks.STONE);
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        if (be != null) {
            be.wake();
        }
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) > before,
                    "positive control: bus must mine stone");
        });
    }

    // ------------------------------------------------------------ Annihilation Bus

    /** Hewer's Annihilation Bus attacking with a diamond sword: the pig in front dies and its pork goes into the network. */
    @GameTest(template = T, timeoutTicks = 140)
    public static void annihilationBusAttacks(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.AIR);
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        helper.setBlock(front.below(), Blocks.STONE);
        var pig = helper.spawnWithNoFreeWill(EntityType.PIG, front);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(!pig.isAlive(), "the pig is dead: health " + pig.getHealth() + ", last strike " + be.lastUse() + " at "
                    + helper.getLevel().getGameTime() + ", pig at " + pig.blockPosition() + " front " + helper.absolutePos(front));
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.PORKCHOP))) >= 1, "its pork is in the network");
            helper.assertTrue(be.getTool().getItem(0).getDamageValue() > 0, "the sword wore");
            helper.succeed();
        });
    }

    /**
     * The Annihilation Bus with an Autocraft Module refills its tool slot: bone meal topped up from what the network has (even a
     * few), a pickaxe on its last point used till it breaks and replaced by a fresh one; taken out by the player, the slot stays
     * empty. An empty allow list stops it.
     */
    @GameTest(template = T, timeoutTicks = 300)
    public static void annihilationBusRefills(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.AIR);
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        be.getUpgrades().setItem(UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.CRAFTING), new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        be.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        be.getTool().setItem(0, new ItemStack(Items.BONE_MEAL, 60));
        ItemKey meal = ItemKey.of(new ItemStack(Items.BONE_MEAL));
        helper.runAfterDelay(2, () -> net(helper, bay).insertItem(new ItemStack(Items.BONE_MEAL, 3), false));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.getTool().getItem(0).getCount() == 63 && net(helper, bay).itemCountOf(meal) == 0,
                    "the three in the network topped the slot up at once: " + be.getTool().getItem(0));
            be.getTool().setItem(0, ItemStack.EMPTY);
            be.getTool().forget();
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(be.getTool().getItem(0).isEmpty(), "taken out by the player, the slot stays empty");
            ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
            worn.setDamageValue(worn.getMaxDamage() - 1);
            net(helper, bay).insertItem(new ItemStack(Items.IRON_PICKAXE), false);
            be.getTool().setItem(0, worn);
            be.menuData().set(1, AnnihilationBusBlockEntity.LEFT);
            helper.setBlock(front, Blocks.STONE);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            ItemStack t = be.getTool().getItem(0);
            helper.assertTrue(t.is(Items.IRON_PICKAXE) && t.getDamageValue() == 0,
                    "the worn pickaxe broke on the stone, a fresh one took its place: " + t);
            boolean wornBack = net(helper, bay).listItems().keySet().stream().anyMatch(k -> k.stack().is(Items.IRON_PICKAXE));
            helper.assertTrue(!wornBack, "nothing worn went back into the network");
            // an empty allow list: the golem stands still
            be.menuData().set(3, 0);
            helper.setBlock(front, Blocks.STONE);
        });
        helper.runAfterDelay(150, () -> {
            helper.assertBlockPresent(Blocks.STONE, front);
            helper.succeed();
        });
    }

    /**
     * The Plasma Temper in Hewer's Annihilation Bus: a diamond pickaxe on its last point of durability still mines, and comes out
     * of the work as worn as it went in.
     */
    @GameTest(template = T, timeoutTicks = 120)
    public static void annihilationBusTempered(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.STONE);
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.setDamageValue(pick.getMaxDamage() - 1);
        be.getTool().setItem(0, pick);
        be.getUpgrades().setItem(UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.TEMPER), new ItemStack(ModItems.PLASMA_TEMPER.get()));
        helper.runAfterDelay(60, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, front);
            ItemStack t = be.getTool().getItem(0);
            helper.assertTrue(t.is(Items.DIAMOND_PICKAXE) && t.getDamageValue() == t.getMaxDamage() - 1,
                    "the tempered pickaxe mined and did not wear: " + t.getDamageValue() + "/" + t.getMaxDamage());
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 1, "the stone is in the network");
            helper.succeed();
        });
    }

    /**
     * The Annihilation Bus window: a bare-handed click opens it; the setting buttons switch the bus; shift-click puts a tool into the
     * tool slot, copies a block into the filter, an upgrade into its slot, and takes the tool back; the slots are laid out cleanly.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void annihilationBusWindow(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(rel);
        ServerPlayer player = clickOpen(helper, rel);
        try {
            helper.assertTrue(player.containerMenu instanceof AnnihilationBusMenu,
                    "a bare-handed click opens the window, got " + player.containerMenu);
            var menu = (AnnihilationBusMenu) player.containerMenu;
            assertSlotsLaidOut(helper, menu, AnnihilationBusMenu.IMAGE_WIDTH, AnnihilationBusMenu.IMAGE_HEIGHT);
            var data = be.menuData();
            for (int expect : new int[]{1, 2, 0}) {
                menu.clickMenuButton(player, AnnihilationBusMenu.BUTTON_CLICK);
                helper.assertTrue(data.get(1) == expect, "the click button goes break -> use -> attack -> break, got " + data.get(1));
            }
            for (int[] b : new int[][]{{AnnihilationBusMenu.BUTTON_SNEAK, 2},
                    {AnnihilationBusMenu.BUTTON_LIST, 3}, {AnnihilationBusMenu.BUTTON_GROWN, 4}}) {
                // a new bus: sneak off, a deny list (an empty one works on everything), grown only off
                int was = b[0] == AnnihilationBusMenu.BUTTON_LIST ? 1 : 0;
                helper.assertTrue(data.get(b[1]) == was && menu.clickMenuButton(player, b[0]) && data.get(b[1]) == 1 - was,
                        "button " + b[0] + " switches its setting");
                menu.clickMenuButton(player, b[0]);
                helper.assertTrue(data.get(b[1]) == was, "and back again");
            }
            int hotbar0 = menu.slots.size() - 9;
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(1, new ItemStack(Items.STONE, 12));
            player.getInventory().setItem(2, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 2));
            player.getInventory().setItem(3, new ItemStack(ModItems.FUZZY_CARD.get()));
            menu.clicked(hotbar0, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(be.getTool().getItem(0).is(Items.DIAMOND_PICKAXE) && player.getInventory().getItem(0).isEmpty(),
                    "shift-click moves the pickaxe into the tool slot");
            menu.clicked(hotbar0 + 1, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(be.getFilters().getItem(0).is(Items.STONE) && player.getInventory().getItem(1).getCount() == 12,
                    "shift-click copies stone into the filter and keeps the stack");
            menu.clicked(hotbar0 + 2, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(be.getUpgrades().count(UpgradeType.SPEED) == 0
                    && player.getInventory().getItem(2).getCount() == 2, "no co-processors: the golem is as fast as its tool");
            menu.clicked(hotbar0 + 3, 0, ClickType.QUICK_MOVE, player);
            int fuzzySlot = UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.FUZZY);
            helper.assertTrue(be.getUpgrades().getItem(fuzzySlot).is(ModItems.FUZZY_CARD.get()), "the fuzzy card goes into its own slot");
            menu.clicked(AnnihilationBusMenu.TOOL_SLOT_INDEX, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(be.getTool().getItem(0).isEmpty() && player.getInventory().contains(new ItemStack(Items.DIAMOND_PICKAXE)),
                    "shift-click on the tool slot takes the pickaxe back");
            helper.assertTrue(!menu.getSlot(AnnihilationBusMenu.TOOL_SLOT_INDEX)
                    .mayPlace(new ItemStack(ModItems.REDSTONE_MODULE.get())), "an upgrade is no tool");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    /** Unbreakable blocks (bedrock, barrier) must never be mined or harvested. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void annihilationBusUnbreakable(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
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
    @GameTest(template = T, timeoutTicks = 180)
    public static void annihilationBusLiquidAndAir(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.WATER);

        helper.runAfterDelay(50, () -> {
            helper.assertBlockPresent(Blocks.WATER, front);
            helper.setBlock(front, Blocks.AIR);
        });

        helper.runAfterDelay(100, () -> {
            var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
            helper.assertTrue(!be.swinging(), "bus must not swing at air");
            assertMinesStone(helper, bay, bus, front);
        });
    }

    // ------------------------------------------------------------ Formation Bus

    /** The Formation Bus window: a bare-handed click opens the bus window without priority; shift-click copies a block into the filter. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void formationBusWindow(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        var be = (FormationBusBlockEntity) helper.getBlockEntity(rel);
        ServerPlayer player = clickOpen(helper, rel);
        try {
            helper.assertTrue(player.containerMenu instanceof BusMenu bus && bus.isFormation(),
                    "a bare-handed click opens the Formation Bus window, got " + player.containerMenu);
            var menu = (BusMenu) player.containerMenu;
            assertSlotsLaidOut(helper, menu, BusMenu.IMAGE_WIDTH, BusMenu.IMAGE_HEIGHT);
            helper.assertTrue(menu.upgradeSpec() == UpgradeSpec.FORMATION_BUS, "upgrades as on the Export Bus");
            player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 5));
            menu.clicked(menu.slots.size() - 9, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(be.getFilters().getItem(0).is(Items.OAK_LOG) && player.getInventory().getItem(0).getCount() == 5,
                    "shift-click copies the log into the filter");
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    // ------------------------------------------------------------ intake and bus load

    /** What a machine hands to an interface or a provider reaches the network within a couple of ticks, however much it is. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void intakeAtOnce(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(BUS_OUT, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        helper.setBlock(BUS_IN, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        var iface = (InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(BUS_IN);
        helper.runAfterDelay(30, () -> {
            var handler = iface.automation();
            helper.assertTrue(handler.insertItem(9, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(), "intake takes a stack");
            helper.assertTrue(handler.insertItem(10, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty(), "and another");
            provider.getReturns().setItem(0, new ItemStack(Items.GOLD_INGOT, 64));
            helper.runAfterDelay(2, () -> {
                GolemNetwork n = net(helper, bay);
                helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))) == 128,
                        "the interface handed all of it over, network has " + n.itemCountOf(ItemKey.of(new ItemStack(Items.IRON_INGOT))));
                helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))) == 64,
                        "the provider handed all of it over, network has " + n.itemCountOf(ItemKey.of(new ItemStack(Items.GOLD_INGOT))));
                helper.succeed();
            });
        });
    }

    /** A trickle takes few operations and is all carried; a lot to move runs at the bus's full rate at once. */
    @GameTest(template = T, timeoutTicks = 400)
    public static void busAdaptsToLoad(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(BAY)).getContainer().setItem(1, new ItemStack(ModItems.COPPER_DISK_64K.get()));
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_IN);
        bus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
        Container c = (Container) helper.getBlockEntity(chest);
        int[] startOps = new int[1];
        ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT)), cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        helper.runAfterDelay(30, () -> startOps[0] = bus.operations);
        // one ingot every 2 ticks for 100 ticks: at a steady 5 ticks that would be 20 operations
        for (int t = 0; t < 50; t++) {
            helper.runAfterDelay(30 + t * 2, () -> {
                ItemStack in = c.getItem(0);
                c.setItem(0, in.isEmpty() ? new ItemStack(Items.IRON_INGOT) : in.copyWithCount(in.getCount() + 1));
            });
        }
        helper.runAfterDelay(160, () -> {
            int ops = bus.operations - startOps[0];
            helper.assertTrue(net(helper, bay).itemCountOf(iron) == 50, "the trickle all arrived, got " + net(helper, bay).itemCountOf(iron));
            helper.assertTrue(ops <= 12, "a trickle takes few operations, took " + ops);
            for (int i = 0; i < 20; i++) {
                c.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            helper.runAfterDelay(50, () -> {
                long n = net(helper, bay).itemCountOf(cobble);
                // 20 items a tick, with what the last second has saved up
                helper.assertTrue(n >= 800 && n <= 1280, "a lot to move goes at the full rate at once, moved " + n + " in 50 ticks");
                var server = helper.getLevel().getServer();
                try {
                    helper.assertTrue(server.getCommands().getDispatcher().execute("golemlogix_buses", server.createCommandSourceStack()) == 1,
                            "the load command answers");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new IllegalStateException("the load command is missing: " + e.getMessage());
                }
                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------------ autocrafting

    /**
     * A processing pattern that gives a fluid: the order is in mB, the machine gets its ingredients, and the fluid it pours into
     * the gateway's return tanks goes to the job, then into storage.
     */
    @GameTest(template = T, timeoutTicks = 200)
    public static void autocraftFluidOutput(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        BlockPos machine = PROV.east();
        helper.setBlock(machine, Blocks.CHEST);
        Container machineInv = (Container) helper.getBlockEntity(machine);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        ItemStack water = com.golemlogix.items.FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false, List.of(new ItemStack(Items.ICE)), List.of(water))));
        ItemKey waterKey = com.golemlogix.system.Ingredients.fluidKey(new FluidStack(Fluids.WATER, 1));
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(BAY)).getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.ICE, 2), false);
            GolemNetwork.StartResult r = n.startCrafting(waterKey, 2000);
            helper.assertTrue(r.type() == GolemNetwork.StartType.STARTED, "start result " + r.type());
        });
        helper.runAfterDelay(60, () -> {
            int ice = 0;
            for (int i = 0; i < machineInv.getContainerSize(); i++) {
                ice += machineInv.getItem(i).getCount();
            }
            helper.assertTrue(ice == 2, "2000 mB take two runs: two ice into the machine, got " + ice);
            IFluidHandler tanks = provider.fluidReturns();
            int in = tanks.fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(in == 2000, "the return tanks take the water, got " + in);
        });
        helper.runAfterDelay(130, () -> {
            GolemNetwork n = net(helper, bay);
            long stored = 0;
            for (FluidStack f : n.listFluids()) {
                if (f.getFluid() == Fluids.WATER) {
                    stored += f.getAmount();
                }
            }
            helper.assertTrue(stored == 2000, "the water went through the job into storage, got " + stored);
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "the job is done");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------ periscope and import filters

    /**
     * The periscope as a storage bus: an allow list takes only what it names (and shows only that), a deny list everything else;
     * insert only fills the chest without showing it, extract only never puts anything new in.
     */
    @GameTest(template = T, timeoutTicks = 200)
    public static void periscopeFilterAndAccess(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos chestA = new BlockPos(8, 1, 6), chestB = new BlockPos(4, 1, 8);
        helper.setBlock(chestA, Blocks.CHEST);
        helper.setBlock(chestB, Blocks.CHEST);
        periscope(helper, new BlockPos(7, 1, 6), Direction.EAST);
        periscope(helper, new BlockPos(4, 1, 7), Direction.SOUTH);
        var pa = (PeriscopeBlockEntity) helper.getBlockEntity(new BlockPos(7, 1, 6));
        pa.setPriority(5);
        var a = (ChestBlockEntity) helper.getBlockEntity(chestA);
        var b = (ChestBlockEntity) helper.getBlockEntity(chestB);
        a.setItem(0, new ItemStack(Items.DIRT, 5));
        pa.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        var data = pa.menuData();
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = net(helper, ORIGIN);
            ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT)), stone = ItemKey.of(new ItemStack(Items.STONE));
            // allow list: cobblestone only
            helper.assertTrue(n.insertItem(new ItemStack(Items.STONE, 10), false).isEmpty() && count(b, Items.STONE) == 10, "stone skips chest A");
            helper.assertTrue(n.insertItem(new ItemStack(Items.COBBLESTONE, 10), false).isEmpty() && count(a, Items.COBBLESTONE) == 10, "cobble goes to A");
            helper.assertTrue(n.itemCountOf(dirt) == 0, "the dirt in A is outside the filter: unseen");
            // deny list
            data.set(3, 1);
            helper.assertTrue(n.insertItem(new ItemStack(Items.COBBLESTONE, 4), false).isEmpty() && count(b, Items.COBBLESTONE) == 4, "denied cobble goes to B");
            helper.assertTrue(n.itemCountOf(dirt) == 5, "the dirt shows now");
            // insert only, no filter
            data.set(3, 0);
            pa.getFilters().setItem(0, ItemStack.EMPTY);
            data.set(2, PeriscopeBlockEntity.INSERT_ONLY);
            helper.assertTrue(n.insertItem(new ItemStack(Items.STONE, 6), false).isEmpty() && count(a, Items.STONE) == 6, "A still takes stone");
            helper.assertTrue(n.itemCountOf(stone) == 10 && n.itemCountOf(dirt) == 0, "but shows nothing of its own");
            helper.assertTrue(n.extractItem(new ItemStack(Items.STONE), 64, false).getCount() == 10 && count(a, Items.STONE) == 6, "only B gives stone");
            // extract only
            data.set(2, PeriscopeBlockEntity.EXTRACT_ONLY);
            helper.assertTrue(n.insertItem(new ItemStack(Items.GOLD_INGOT, 3), false).isEmpty() && count(b, Items.GOLD_INGOT) == 3, "nothing new into A");
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIRT), 64, false).getCount() == 5 && count(a, Items.DIRT) == 0, "A gives its dirt");
            helper.succeed();
        });
    }

    /** An Import Bus with a deny list takes everything but what its filter names. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void importDenyList(GameTestHelper helper) {
        GolemGameTests.baseNetwork(helper);
        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos chest = new BlockPos(2, 1, 4);
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest);
        c.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        c.setItem(1, new ItemStack(Items.DIRT, 7));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_IN);
        bus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        bus.menuData().set(2, 1);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(c.getItem(0).getCount() == 20 && c.getItem(1).isEmpty(), "the dirt comes in, the denied cobblestone stays");
            GolemNetwork n = net(helper, BAY);
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.DIRT))) == 7, "dirt reached the network");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 0, "denied cobblestone did not reach the network");
            helper.succeed();
        });
    }
}
