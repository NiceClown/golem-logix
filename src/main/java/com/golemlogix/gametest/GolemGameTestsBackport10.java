package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.ProviderOutput;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Game tests backported from main's GolemGameTestsExtra (2.1.4): redstone modes on buses, fuzzy filters on the
 * Formation and Annihilation Bus, provider sides, bus throughput, the fluid Order Ledger and disk moves between cabinets.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GolemGameTestsBackport10 {
    private static final String T = "empty";

    // the same points as in GolemGameTests (next to the corner bushings of the base network)
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos TERM = new BlockPos(3, 1, 6);
    private static final BlockPos BUS_IN = new BlockPos(3, 1, 4);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    private GolemGameTestsBackport10() {
    }

    /**
     * A real reload: the block entity is replaced by a new object that knows only what was saved, and the network is built again.
     * Loading a tag into the same object proves nothing, it still remembers everything.
     */
    private static <B extends net.minecraft.world.level.block.entity.BlockEntity> B reload(GameTestHelper helper, BlockPos rel, Class<B> type) {
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
     * 7. The Redstone Module's modes (HIGH, LOW, IGNORE, PULSE) actually govern the export bus, and the interface;
     * each with a positive control. The Formation Bus at least for HIGH.
     */
    @GameTest(template = T, timeoutTicks = 300)
    public static void redstoneModesOnBuses(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay)).getContainer().setItem(1, new ItemStack(ModItems.COPPER_DISK_64K.get()));
        GolemNetwork n = GolemGameTests.net(helper, bay);
        // enough that emptying the chest between the phases does not run the network dry
        n.insertItem(new ItemStack(Items.COBBLESTONE, 4000), false);
        n.insertItem(new ItemStack(Items.IRON_INGOT, 640), false);
        n.insertItem(new ItemStack(Items.STONE, 64), false);

        // 1. Export Bus at BUS_OUT facing north into chest at BUS_OUT.north()
        BlockPos chestPos = BUS_OUT.north();
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = ((ChestBlockEntity) helper.getBlockEntity(chestPos));
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity exportBus = ((BusBlockEntity) helper.getBlockEntity(BUS_OUT));
        exportBus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        exportBus.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos redstonePos = BUS_OUT.above();

        // 2. Interface at BUS_IN with 5 Iron Ingots in config slot 0
        helper.setBlock(BUS_IN, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        InterfaceBlockEntity iface = ((InterfaceBlockEntity) helper.getBlockEntity(BUS_IN));
        iface.getConfig().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        iface.getUpgrades().setItem(0, new ItemStack(ModItems.REDSTONE_MODULE.get()));
        BlockPos ifaceRedstonePos = BUS_IN.above();

        // 3. Formation Bus at TERM facing north into AIR
        BlockPos formPos = TERM;
        BlockPos formFront = formPos.north();
        helper.setBlock(formFront, Blocks.AIR);
        helper.setBlock(formPos, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        FormationBusBlockEntity formBus = ((FormationBusBlockEntity) helper.getBlockEntity(formPos));
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
    @GameTest(template = T, timeoutTicks = 250)
    public static void fuzzyOnFormationAndAnnihilationBus(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);

        // Network storage has Birch Planks (not Oak Planks)
        GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.BIRCH_PLANKS, 32), false);

        // Tag filter: Oak Planks item with #minecraft:planks tag
        ItemStack tagFilter = Fuzzy.withTag(new ItemStack(Items.OAK_PLANKS), ItemTags.PLANKS);

        // 1. Formation Bus facing north into AIR
        BlockPos formPos = BUS_OUT;
        BlockPos formFront = formPos.north();
        helper.setBlock(formFront, Blocks.AIR);
        helper.setBlock(formPos, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        FormationBusBlockEntity formBus = ((FormationBusBlockEntity) helper.getBlockEntity(formPos));
        formBus.getFilters().setItem(0, tagFilter.copy());

        // 2. Annihilation Bus facing north into Birch Planks
        BlockPos anniPos = TERM;
        BlockPos anniFront = anniPos.north();
        helper.setBlock(anniFront, Blocks.BIRCH_PLANKS);
        helper.setBlock(anniPos, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        AnnihilationBusBlockEntity anniBus = ((AnnihilationBusBlockEntity) helper.getBlockEntity(anniPos));
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
     * 9. Pattern Provider with machines on two sides: with all sides allowed, what does not fit one goes to the other;
     * with a chosen output side only that one is fed, and once a full machine has room the work resumes.
     * Blocking mode: the next portion waits for the result of the previous one.
     */
    @GameTest(template = T, timeoutTicks = 400)
    public static void providerFeedsAnotherSideWhenOneIsFull(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = ((PatternProviderBlockEntity) helper.getBlockEntity(PROV));

        // the array stands to the west of the provider: its free sides are north and south
        BlockPos westPos = PROV.north();
        BlockPos eastPos = PROV.south();
        helper.setBlock(westPos, Blocks.CHEST);
        helper.setBlock(eastPos, Blocks.CHEST);
        ChestBlockEntity westChest = ((ChestBlockEntity) helper.getBlockEntity(westPos));
        ChestBlockEntity eastChest = ((ChestBlockEntity) helper.getBlockEntity(eastPos));

        PatternData ironPattern = new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        provider.getContainer().setItem(0, GolemGameTests.card(ironPattern));

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
            GolemNetwork n = GolemGameTests.net(helper, bay);
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
    @GameTest(template = T, timeoutTicks = 340)
    public static void busThroughputMatchesUpgrades(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay));
        driveBay.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_256K.get()));
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_256K.get()));

        // Double chest at (2, 1, 4) and (2, 1, 3), BUS_IN at (3, 1, 4) faces it
        BlockPos chest1 = new BlockPos(2, 1, 4);
        BlockPos chest2 = new BlockPos(2, 1, 3);
        helper.setBlock(chest1, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(chest2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        ChestBlockEntity c1 = ((ChestBlockEntity) helper.getBlockEntity(chest1));
        ChestBlockEntity c2 = ((ChestBlockEntity) helper.getBlockEntity(chest2));
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
        BusBlockEntity exportBus = ((BusBlockEntity) helper.getBlockEntity(BUS_IN));
        exportBus.getFilters().setItem(0, new ItemStack(Items.COBBLESTONE));
        GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.COBBLESTONE, 20000), false);

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
        Runnable mark = () -> before[0] = GolemGameTests.net(helper, bay).itemCountOf(cobble);
        helper.runAfterDelay(160, mark);
        helper.runAfterDelay(200, () -> {
            long moved = GolemGameTests.net(helper, bay).itemCountOf(cobble) - before[0];
            helper.assertTrue(moved >= 55 && moved <= 110, "import, no upgrades: ~80 items in 40 ticks (got " + moved + ")");
            BusBlockEntity importBus = ((BusBlockEntity) helper.getBlockEntity(BUS_IN));
            importBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
            importBus.wake();
        });
        helper.runAfterDelay(210, () -> {
            fillChest.run();
            mark.run();
        });
        helper.runAfterDelay(230, () -> {
            long moved = GolemGameTests.net(helper, bay).itemCountOf(cobble) - before[0];
            helper.assertTrue(moved >= 250 && moved <= 600, "import, 4 upgrades: ~400 items in 20 ticks (got " + moved + ")");
            BusBlockEntity importBus = ((BusBlockEntity) helper.getBlockEntity(BUS_IN));
            importBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 28));
            importBus.wake();
        });
        helper.runAfterDelay(240, () -> {
            fillChest.run();
            mark.run();
        });
        helper.runAfterDelay(260, () -> {
            long moved = GolemGameTests.net(helper, bay).itemCountOf(cobble) - before[0];
            helper.assertTrue(moved >= 1800 && moved <= 3200, "import, 28 upgrades: ~2560 items in 20 ticks (got " + moved + ")");

            // Fluid through an export bus with 4 upgrades: 2500 mB a tick
            BlockPos tankPos = BUS_OUT.north();
            helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get().defaultBlockState());
            CopperTankBlockEntity tank = ((CopperTankBlockEntity) helper.getBlockEntity(tankPos));
            CompoundTag capTag = new CompoundTag();
            capTag.putInt("Capacity", 250_000);
            tank.loadWithComponents(capTag, helper.getLevel().registryAccess());

            helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
            BusBlockEntity fluidBus = ((BusBlockEntity) helper.getBlockEntity(BUS_OUT));
            fluidBus.getFilters().setItem(0, FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000)));
            fluidBus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 4));
            int stored = GolemGameTests.net(helper, bay).insertFluid(new FluidStack(Fluids.WATER, 200_000), false);
            helper.assertTrue(stored == 200_000, "the fluid disk holds the water for the test (took " + stored + " mB)");
        });
        int[] tankBefore = new int[1];
        helper.runAfterDelay(270, () -> tankBefore[0] = ((CopperTankBlockEntity) helper.getBlockEntity(BUS_OUT.north())).fluid().getAmount());
        helper.runAfterDelay(290, () -> {
            int moved = ((CopperTankBlockEntity) helper.getBlockEntity(BUS_OUT.north())).fluid().getAmount() - tankBefore[0];
            helper.assertTrue(moved >= 35_000 && moved <= 65_000, "fluid export, 4 upgrades: ~50000 mB in 20 ticks (got " + moved + " mB)");
            helper.succeed();
        });
    }

    /**
     * 11. An Order Ledger in fluid mode with a real pattern that makes a fluid:
     * it orders the craft, the fluid reaches its target, and it stops ordering.
     */
    @GameTest(template = T, timeoutTicks = 320)
    public static void stockRequesterKeepsFluid(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay));
        driveBay.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_16K.get()));
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_16K.get()));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = ((PatternProviderBlockEntity) helper.getBlockEntity(PROV));

        // Pattern: 1 Ice -> 1000 mB Water
        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.ICE)), List.of(waterGhost))));

        // The machine: a chest beside the provider; the loop below melts each ice it gets into a bucket's worth
        BlockPos machinePos = PROV.east();
        helper.setBlock(machinePos, Blocks.CHEST);
        ChestBlockEntity machine = ((ChestBlockEntity) helper.getBlockEntity(machinePos));

        GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.ICE, 10), false);
        ItemKey iceKey = ItemKey.of(new ItemStack(Items.ICE));

        helper.setBlock(BUS_OUT, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = ((StockRequesterBlockEntity) helper.getBlockEntity(BUS_OUT));
        ItemStack reqFilter = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        reqFilter.set(ModDataComponents.KEEP_AMOUNT.get(), 2000); // Target = 2000 mB Water
        req.getFilters().setItem(0, reqFilter);

        boolean[] sawCrafting = new boolean[1];
        for (int t = 1; t <= 280; t++) {
            helper.runAfterDelay(t, () -> {
                if (req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_CRAFTING) {
                    sawCrafting[0] = true;
                }
                GolemNetwork n = GolemGameTests.net(helper, bay);
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
            GolemNetwork n = GolemGameTests.net(helper, bay);
            helper.assertTrue(sawCrafting[0], "the ledger reported that it was crafting");
            long water = n.fluidCountOf(Fluids.WATER);
            helper.assertTrue(water == 2000, "water reaches the target of 2000 mB exactly (got " + water + ")");
            helper.assertTrue(n.itemCountOf(iceKey) == 8, "two ice were used (left " + n.itemCountOf(iceKey) + ")");
        });
        helper.runAfterDelay(140, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            CryoCalculatorBlockEntity cpu = ((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU));
            helper.assertTrue(cpu.isIdle(), "no further job once the target is reached");
            helper.assertTrue(req.getSlotStatus(0) == StockRequesterBlockEntity.STATUS_OK, "status is OK once the target is reached");
            helper.assertTrue(n.fluidCountOf(Fluids.WATER) == 2000, "no further water was ordered");
            helper.assertTrue(n.itemCountOf(iceKey) == 8, "no further ice was used");

            // Use some water: it keeps the stock, so it orders just what is missing
            FluidStack taken = n.extractFluid(new FluidStack(Fluids.WATER, 1000), 1000, false);
            helper.assertTrue(taken.getAmount() == 1000, "took 1000 mB out of the network");
        });
        helper.runAfterDelay(260, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            long water = n.fluidCountOf(Fluids.WATER);
            helper.assertTrue(water == 2000, "the used water was made again (got " + water + ")");
            helper.assertTrue(n.itemCountOf(iceKey) == 7, "one more ice was used (left " + n.itemCountOf(iceKey) + ")");
            helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "and the calculator is idle again");
            helper.succeed();
        });
    }

    /**
     * 12. A disk with items taken out of one cabinet and put into another cabinet in the network keeps every item
     * and its count, and items in a disk are not lost when the cabinet is broken and replaced.
     */
    @GameTest(template = T, timeoutTicks = 200)
    public static void networkKeepsContentsAcrossDiskMove(GameTestHelper helper) {
        BlockPos bay1 = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity drive1 = ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay1));
        drive1.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_64K.get()));

        // Second cabinet at (6, 1, 7) facing SOUTH (touches corner bushing at (6, 1, 6))
        BlockPos bay2 = new BlockPos(6, 1, 7);
        GolemGameTests.putBay(helper, bay2);

        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey gold = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
        ItemKey emerald = ItemKey.of(new ItemStack(Items.EMERALD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay1);
            n.insertItem(new ItemStack(Items.DIAMOND, 42), false);
            n.insertItem(new ItemStack(Items.GOLD_INGOT, 128), false);
            n.insertItem(new ItemStack(Items.EMERALD, 15), false);

            helper.assertTrue(n.itemCountOf(diamond) == 42, "initial diamonds stored");
            helper.assertTrue(n.itemCountOf(gold) == 128, "initial gold stored");
            helper.assertTrue(n.itemCountOf(emerald) == 15, "initial emerald stored");

            // 1. Move disk from Cabinet 1 to Cabinet 2
            ItemStack disk = drive1.getContainer().removeItemNoUpdate(0);
            drive1.setChanged();

            GolemNetwork nEmpty = GolemGameTests.net(helper, bay1);
            helper.assertTrue(nEmpty.itemCountOf(diamond) == 0, "storage empty without disk");

            CopperDriveBayBlockEntity drive2 = ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay2));
            drive2.getContainer().setItem(0, disk);
            drive2.setChanged();

            GolemNetwork nMoved = GolemGameTests.net(helper, bay1);
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
                GolemGameTests.putBay(helper, bay2);
                CopperDriveBayBlockEntity driveReplaced = ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay2));
                driveReplaced.getContainer().setItem(0, savedDisk);
                driveReplaced.setChanged();

                helper.runAfterDelay(10, () -> {
                    reload(helper, bay2, CopperDriveBayBlockEntity.class);
                    GolemNetwork nReplaced = GolemGameTests.net(helper, bay1);
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
