package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.StockManometerMenu;
import com.golemlogix.menu.StockRequesterMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Stock Manometer, Stock Requester and Cryo-Diode tests backported from main's GolemGameTestsExtra. */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport6 {
    private static final String T = "empty";

    // The same layout points as in GolemGameTests
    private static final BlockPos ORIGIN = new BlockPos(5, 1, 5);
    private static final BlockPos CORE = ORIGIN.offset(0, 3, 0);
    private static final BlockPos BAY = new BlockPos(7, 1, 4);
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    private static BlockPos baseNetwork(GameTestHelper helper) {
        return GolemGameTests.baseNetwork(helper);
    }

    private static GolemNetwork net(GameTestHelper helper, BlockPos rel) {
        return GolemGameTests.net(helper, rel);
    }

    private static ItemStack card(PatternData data) {
        return GolemGameTests.card(data);
    }

    private static PatternData craftingPattern(ItemStack output, ItemStack... slots) {
        return GolemGameTests.craftingPattern(output, slots);
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        return GolemGameTests.testPlayer(helper);
    }

    // -------------------------------------------------------------------------
    // 1. STOCK MANOMETER
    // -------------------------------------------------------------------------

    @GameTest(template = T, timeoutTicks = 120)
    public static void stockManometerHysteresisLatch(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
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

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerFluidGhost(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);

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

    @GameTest(template = T, timeoutTicks = 150)
    public static void stockManometerAutocraftModulePulse(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
        mano.getFilter().setItem(0, new ItemStack(Items.OAK_PLANKS));
        mano.getUpgrades().setItem(0, new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(mano.getSignal() == 0, "signal 0 when no craft is running");
            net(helper, bay).insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            net(helper, bay).startCrafting(ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 40);

            helper.runAfterDelay(15, () -> {
                helper.assertTrue(mano.getSignal() == 15, "signal 15 while autocraft is running");
                helper.succeedWhen(() -> {
                    helper.assertTrue(((CryoCalculatorBlockEntity) helper.getBlockEntity(CPU)).isIdle(), "craft finished");
                    helper.assertTrue(mano.getSignal() == 0, "signal drops back to 0 when craft ends");
                });
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerMenuAndPayloadAndSaveLoad(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
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
        BlockEntity loaded = BlockEntity.loadStatic(manoPos, helper.getBlockState(manoPos), tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof StockManometerBlockEntity, "loaded block entity is StockManometerBlockEntity");
        StockManometerBlockEntity mano2 = (StockManometerBlockEntity) loaded;
        helper.assertTrue(mano2.getMode() == StockManometerBlockEntity.MODE_BELOW, "loaded mode preserved");
        helper.assertTrue(mano2.getThreshold() == 120, "loaded threshold preserved");
        helper.assertTrue(mano2.getHysteresis() == 240, "loaded hysteresis preserved");

        helper.succeed();
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockManometerRedstoneLampNeighbor(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos manoPos = BUS_OUT;
        // Facing NORTH, attached face is SOUTH (where cable bushing connects to array)
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos lampPos = manoPos.north();
        helper.setBlock(lampPos, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));

        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
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

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockRequesterKeepsTargetAmount(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = (StockRequesterBlockEntity) helper.getBlockEntity(reqPos);

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

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockRequesterSingleJobPerItem(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = (StockRequesterBlockEntity) helper.getBlockEntity(reqPos);

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

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockRequesterStatusesNoPatternAndMissing(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, card(craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = (StockRequesterBlockEntity) helper.getBlockEntity(reqPos);

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

    @GameTest(template = T, timeoutTicks = 100)
    public static void stockRequesterFluidTarget(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity driveBay = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        driveBay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);

        ItemStack waterGhost = FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000));
        provider.getContainer().setItem(0, card(new PatternData(false, List.of(new ItemStack(Items.ICE)), List.of(waterGhost))));

        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = (StockRequesterBlockEntity) helper.getBlockEntity(reqPos);

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

    @GameTest(template = T, timeoutTicks = 150)
    public static void stockRequesterZeroAmountEmptyAndOffline(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos reqPos = BUS_OUT;
        helper.setBlock(reqPos, ModBlocks.STOCK_REQUESTER.get().defaultBlockState());
        StockRequesterBlockEntity req = (StockRequesterBlockEntity) helper.getBlockEntity(reqPos);

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

    @GameTest(template = T, timeoutTicks = 100)
    public static void cryoDiodeStorageAndCraftingIsolated(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos bay1 = BAY;
        GolemGameTests.putBay(helper, bay1);
        CopperDriveBayBlockEntity drive1 = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay1);
        drive1.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        BlockPos diodePos = BUS_OUT;
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos b2 = diodePos.north();
        GolemGameTests.put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        GolemGameTests.put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        GolemGameTests.put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        GolemGameTests.put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        BlockPos bay2 = b2.west();
        GolemGameTests.putBay(helper, bay2);
        CopperDriveBayBlockEntity drive2 = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay2);
        drive2.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        // Pattern in network 1
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity prov1 = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
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

    @GameTest(template = T, timeoutTicks = 100)
    public static void cryoDiodeUnidirectionalSourceFlow(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper); // Net 1 is powered
        BlockPos bay1 = BAY;
        GolemGameTests.putBay(helper, bay1);

        BlockPos diodePos = BUS_OUT;
        // FACING NORTH: inlet is SOUTH (Net 1), outlet is NORTH (Net 2)
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        CryoDiodeBlockEntity diode = (CryoDiodeBlockEntity) helper.getBlockEntity(diodePos);

        BlockPos b2 = diodePos.north();
        GolemGameTests.put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        GolemGameTests.put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        GolemGameTests.put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        GolemGameTests.put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
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
}
