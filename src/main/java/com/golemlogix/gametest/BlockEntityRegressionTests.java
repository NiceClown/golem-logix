package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.function.Consumer;

import static com.golemlogix.gametest.GolemGameTests.*;

/**
 * Regression GameTests for block entity fixes (interface fluid autocraft intake, manometer fuzzy & update sync,
 * chained Cryo-Diodes, export bus fluid round-robin, annihilation bus refill preservation, duplicate core formed flag,
 * periscope redstone cache invalidation, and distillation tower multi-network jar discovery).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class BlockEntityRegressionTests {
    private record Test(String name, Consumer<GameTestHelper> body, int maxTicks) {
    }

    private static final List<Test> TESTS = List.of(
            new Test("bereg_interfacefluidfeedsautocraft", BlockEntityRegressionTests::interfaceFluidFeedsAutocraft, 240),
            new Test("bereg_manometerfuzzyandsync", BlockEntityRegressionTests::manometerFuzzyAndSync, 120),
            new Test("bereg_cryodiodechain", BlockEntityRegressionTests::cryoDiodeChain, 140),
            new Test("bereg_exportbusfluidroundrobin", BlockEntityRegressionTests::exportBusFluidRoundRobin, 160),
            new Test("bereg_annihilationrefillpreservedonfull", BlockEntityRegressionTests::annihilationRefillPreservedOnFull, 140),
            new Test("bereg_duplicatecorenotformed", BlockEntityRegressionTests::duplicateCoreNotFormed, 100),
            new Test("bereg_periscoperedstoneinvalidates", BlockEntityRegressionTests::periscopeRedstoneInvalidates, 120),
            new Test("bereg_towersecondnetworkjar", BlockEntityRegressionTests::towerSecondNetworkJar, 140),
            new Test("bereg_interfacesleepandcache", BlockEntityRegressionTests::interfaceSleepAndCache, 340),
            new Test("bereg_busidleandfullimportwake", BlockEntityRegressionTests::busIdleAndFullImportWake, 420),
            new Test("bereg_coreskipsredundantsync", BlockEntityRegressionTests::coreSkipsRedundantSync, 260),
            new Test("bereg_drivebaydiskinvalidatescache", BlockEntityRegressionTests::driveBayDiskInvalidatesCache, 80)
    );

    private BlockEntityRegressionTests() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (Test t : TESTS) {
                helper.register(id(t.name()), t.body());
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterTests(RegisterGameTestsEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                id("blockentity_regression"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }

    /** Fluids piped into a Golem Interface must satisfy waiting processing autocraft jobs (even with no fluid disk). */
    public static void interfaceFluidFeedsAutocraft(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        put(helper, CPU, ModBlocks.CRYO_CALCULATOR.get());
        put(helper, PROV, ModBlocks.PATTERN_PROVIDER.get());
        put(helper, PROV.east(), Blocks.CHEST);
        put(helper, BUS_IN, ModBlocks.GOLEM_INTERFACE.get());

        // Processing recipe: 1 iron ingot -> 1000 mB water (fluid output) -> 1 sponge (wait: test fluid step directly)
        // Two-step job: iron ingot -> 1000 mB water (machine), then 1000 mB water + dirt -> diamond (machine),
        // so the waiting water step is absorbed by the CPU even with NO fluid disk in the network!
        FluidStack bucketWater = new FluidStack(Fluids.WATER, 1000);
        ItemStack waterGhost = FluidGhostItem.of(bucketWater);
        PatternData makeWater = new PatternData(false, List.of(new ItemStack(Items.IRON_INGOT)), List.of(waterGhost));
        PatternData useWater = new PatternData(false, List.of(waterGhost, new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.DIAMOND)));
        ItemStack c1 = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        c1.set(ModDataComponents.PATTERN.get(), makeWater);
        ItemStack c2 = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        c2.set(ModDataComponents.PATTERN.get(), useWater);

        PatternProviderBlockEntity prov = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        prov.getContainer().setItem(0, c1);
        prov.getContainer().setItem(1, c2);

        InterfaceBlockEntity itf = helper.getBlockEntity(BUS_IN, InterfaceBlockEntity.class);
        CryoCalculatorBlockEntity cpu = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class);

        helper.runAfterDelay(25, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 1), false);
            n.insertItem(new ItemStack(Items.DIRT, 1), false);
            var res = n.startCrafting(ItemKey.of(new ItemStack(Items.DIAMOND)), 1);
            helper.assertTrue(res.type() == GolemNetwork.StartType.STARTED, "job started: " + res.type());
        });

        helper.runAfterDelay(50, () -> {
            // Job should now be waiting for 1000 mB water; pour it into the Interface's input tank
            helper.assertTrue(cpu.getJob() != null && cpu.wants(Ingredients.fluidKey(bucketWater)) == 1000,
                    "job awaits 1000 mB water");
            int inserted = Transfers.insertFluid(itf.fluidResources(), bucketWater, false);
            helper.assertTrue(inserted == 1000, "interface tank accepted 1000 mB water");
        });

        helper.runAfterDelay(85, () -> {
            helper.assertTrue(itf.tankFluid().isEmpty(), "interface flushed water into waiting autocraft job");
            helper.assertTrue(cpu.getJob() != null && cpu.wants(Ingredients.fluidKey(bucketWater)) == 0,
                    "job absorbed water from interface tank");
            helper.succeed();
        });
    }

    /** Stock Manometer with Fuzzy Card counts damaged variants and preserves config across readUpdateTag. */
    public static void manometerFuzzyAndSync(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        put(helper, BUS_OUT, ModBlocks.STOCK_MANOMETER.get());
        StockManometerBlockEntity mano = helper.getBlockEntity(BUS_OUT, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.IRON_SWORD));
        mano.getUpgrades().setItem(mano.getUpgrades().spec().slotOf(UpgradeType.FUZZY), new ItemStack(ModItems.FUZZY_CARD.get()));
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(2);
        mano.setHysteresis(1);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            ItemStack fresh = new ItemStack(Items.IRON_SWORD);
            ItemStack worn = new ItemStack(Items.IRON_SWORD);
            worn.setDamageValue(50);
            n.insertItem(fresh, false);
            n.insertItem(worn, false);
        });

        helper.runAfterDelay(35, () -> {
            helper.assertTrue(mano.getStockCount() == 2, "fuzzy manometer counts both swords: " + mano.getStockCount());
            helper.assertTrue(mano.getSignal() == 15, "fuzzy manometer emits signal 15: " + mano.getSignal());
            // Applying update tag must NOT wipe mode, hysteresis, filter or upgrades
            CompoundTag update = mano.getUpdateTag(helper.getLevel().registryAccess());
            mano.readUpdateTag(update);
            helper.assertTrue(mano.getMode() == StockManometerBlockEntity.MODE_ABOVE, "mode kept");
            helper.assertTrue(mano.getHysteresis() == 1, "hysteresis kept");
            helper.assertTrue(!mano.getFilter().getItem(0).isEmpty(), "filter kept");
            helper.assertTrue(mano.getUpgrades().has(UpgradeType.FUZZY), "upgrades kept");
            helper.succeed();
        });
    }

    /** Daisy-chained Cryo-Diodes (Net1 -> Diode1 -> Net2 -> Diode2 -> Net3) must transfer Source past 1 hop. */
    public static void cryoDiodeChain(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);

        // Net 1 -> Diode 1 -> Net 2 (starter tower at d1.north()) -> Diode 2 -> Net 3 (starter tower at d2.north())
        BlockPos d1 = BUS_OUT;
        helper.setBlock(d1, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos n2Base = d1.north();
        put(helper, n2Base, ModBlocks.CABLE_BUSHING.get());
        put(helper, n2Base.above(1), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, n2Base.above(2), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, n2Base.above(3), ModBlocks.ATLAS_CORE.get());

        BlockPos d2 = n2Base.north();
        helper.setBlock(d2, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos n3Base = d2.north();
        put(helper, n3Base, ModBlocks.CABLE_BUSHING.get());
        put(helper, n3Base.above(1), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, n3Base.above(2), ModBlocks.ATLAS_CONDUIT.get());
        put(helper, n3Base.above(3), ModBlocks.ATLAS_CORE.get());

        helper.runAfterDelay(50, () -> {
            GolemNetwork net2 = net(helper, n2Base);
            GolemNetwork net3 = net(helper, n3Base);
            helper.assertTrue(net2.isOnline(), "net 2 online through diode 1");
            helper.assertTrue(net3.cryoSourceAvailable() > 0, "net 3 sees Source through chained diodes");
            helper.assertTrue(net3.isOnline(), "net 3 online through chained diodes");
            helper.succeed();
        });
    }

    /** Export Bus in ROUND_ROBIN mode must advance past fluid slots and alternate between multiple fluid filters. */
    public static void exportBusFluidRoundRobin(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_64K.get()));

        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = helper.getBlockEntity(BUS_OUT, BusBlockEntity.class);
        bus.getFilters().setItem(0, FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000)));
        bus.getFilters().setItem(1, FluidGhostItem.of(com.golemlogix.system.CryoSource.stack(1000)));
        bus.menuData().set(2, FormationBusBlockEntity.ROUND_ROBIN);

        // Cryo-Mixer (paused by Redstone Module in HIGH mode) accepts Water in flask 0 and Liquid Cryo-Source in flask 1
        BlockPos target = BUS_OUT.north();
        com.golemlogix.blocks.CryoMixerBlock.place(helper.getLevel(), helper.absolutePos(target), Direction.SOUTH, ModBlocks.CRYO_MIXER.get());
        var mixer = helper.getBlockEntity(target, com.golemlogix.blockentity.CryoMixerBlockEntity.class);
        mixer.getUpgrades().setItem(mixer.getUpgrades().spec().slotOf(UpgradeType.REDSTONE), new ItemStack(ModItems.REDSTONE_MODULE.get()));
        mixer.menuData().set(7, RedstoneMode.HIGH.ordinal());

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertFluid(new FluidStack(Fluids.WATER, 8000), false);
            n.insertFluid(com.golemlogix.system.CryoSource.stack(8000), false);
        });

        helper.runAfterDelay(110, () -> {
            helper.assertTrue(mixer.water().getAmount() > 0, "water exported in round-robin");
            helper.assertTrue(mixer.cryo().getAmount() > 0, "cryo-source also exported after fluid slot rotated");
            helper.succeed();
        });
    }

    /** Annihilation Bus in RIGHT mode on a full network must NOT overwrite its refill template with a rejected harvest. */
    public static void annihilationRefillPreservedOnFull(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos busPos = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(busPos, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.WATER);

        AnnihilationBusBlockEntity bus = helper.getBlockEntity(busPos, AnnihilationBusBlockEntity.class);
        bus.menuData().set(1, AnnihilationBusBlockEntity.RIGHT);
        bus.getUpgrades().setItem(bus.getUpgrades().spec().slotOf(UpgradeType.CRAFTING), new ItemStack(ModItems.AUTOCRAFT_MODULE.get()));
        bus.getTool().setItem(0, new ItemStack(Items.BUCKET));

        // Fill the 1k disk completely so the scooped water bucket cannot enter the network
        GolemNetwork n = net(helper, bay);
        for (int i = 0; i < 16; i++) {
            n.insertItem(new ItemStack(Items.DIRT, 64), false);
        }

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(bus.getTool().getItem(0).is(Items.WATER_BUCKET),
                    "rejected water bucket stayed in slot: " + bus.getTool().getItem(0));
            CompoundTag saved = bus.saveWithoutMetadata(helper.getLevel().registryAccess());
            ItemStack refill = com.golemlogix.system.NbtBridge
                    .loadItem(helper.getLevel().registryAccess(), saved.getCompoundOrEmpty("Refill"))
                    .orElse(ItemStack.EMPTY);
            helper.assertTrue(refill.is(Items.BUCKET), "refill template remained empty bucket, got: " + refill);
            helper.succeed();
        });
    }

    /** A bare duplicate Atlas Core next to a formed primary array must report isFormed() == false. */
    public static void duplicateCoreNotFormed(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);

        // Place a bare second Atlas Core connected to the primary network via a bushing
        BlockPos dupPos = BUS_OUT;
        put(helper, dupPos, ModBlocks.ATLAS_CORE.get());

        helper.runAfterDelay(45, () -> {
            AtlasCoreBlockEntity primary = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            AtlasCoreBlockEntity dup = helper.getBlockEntity(dupPos, AtlasCoreBlockEntity.class);
            helper.assertTrue(primary.isFormed() && !primary.isDuplicate(), "primary core formed");
            helper.assertTrue(dup.isDuplicate(), "second core marked duplicate");
            helper.assertTrue(!dup.isFormed(), "bare duplicate core must NOT report isFormed() == true");
            helper.succeed();
        });
    }

    /** Changing a Periscope's Redstone Module / mode must immediately invalidate the network cache. */
    public static void periscopeRedstoneInvalidates(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);

        BlockPos periPos = BUS_OUT;
        BlockPos chestPos = BUS_OUT.north();
        helper.setBlock(periPos, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.NORTH));
        put(helper, chestPos, Blocks.CHEST);
        var chest = helper.getBlockEntity(chestPos, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));

        PeriscopeBlockEntity peri = helper.getBlockEntity(periPos, PeriscopeBlockEntity.class);
        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN)).itemCountOf(diamond) == 10,
                    "network sees 10 diamonds initially");
            // Install Redstone Module and set mode to HIGH (no signal -> immediately disabled, no 5s stale cache!)
            peri.getUpgrades().setItem(peri.getUpgrades().spec().slotOf(UpgradeType.REDSTONE),
                    new ItemStack(ModItems.REDSTONE_MODULE.get()));
            peri.menuData().set(1, RedstoneMode.HIGH.ordinal());
            helper.assertTrue(GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN)).itemCountOf(diamond) == 0,
                    "periscope disabled immediately without waiting for 5s cache TTL");
            // Switch to LOW -> immediately re-enabled
            peri.menuData().set(1, RedstoneMode.LOW.ordinal());
            helper.assertTrue(GolemNetwork.get(helper.getLevel(), helper.absolutePos(ORIGIN)).itemCountOf(diamond) == 10,
                    "periscope re-enabled immediately on mode switch");
            helper.succeed();
        });
    }

    /** Distillation Tower touching a disconnected pipe stub on one side must still discover joined jars on another network. */
    public static void towerSecondNetworkJar(GameTestHelper helper) {
        BlockPos towerPos = new BlockPos(4, 1, 4);
        DistillationTowerBlock.place(helper.getLevel(), helper.absolutePos(towerPos), Direction.SOUTH, ModBlocks.CRYO_DISTILLATION_TOWER.get());

        // Disconnected pipe stubs touching SOUTH and WEST (visited before EAST in Direction order)
        put(helper, towerPos.west(), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, towerPos.south(), ModBlocks.BASIC_SNOW_PIPE.get());

        // Real network on the EAST side connected to a 2x2 Giga Cryo-Source Jar
        put(helper, towerPos.east(), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, towerPos.east(2), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos jarCorner = towerPos.east(3);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                CryoJarBlock.place(helper.getLevel(), helper.absolutePos(jarCorner.offset(dx, 0, dz)),
                        Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
            }
        }

        // Seed Carry directly via NBT so the test does not depend on optional Neo Vitae fluids
        var level = helper.getLevel();
        DistillationTowerBlockEntity tower = helper.getBlockEntity(towerPos, DistillationTowerBlockEntity.class);
        CompoundTag tag = tower.saveWithFullMetadata(level.registryAccess());
        tag.putDouble("Carry", 25.0);
        var fresh = (DistillationTowerBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(towerPos), tower.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);

        helper.runAfterDelay(40, () -> {
            CryoJarBlockEntity jar = helper.getBlockEntity(jarCorner, CryoJarBlockEntity.class);
            helper.assertTrue(jar != null && jar.available() == 25,
                    "tower filled joined jar on second adjacent network despite disconnected pipe stubs: got "
                            + (jar == null ? -1 : jar.available()));
            helper.succeed();
        });
    }

    /** Satisfied interfaces sleep without re-running restock, wake on stock/config/network changes, and keep fluid cache fresh. */
    public static void interfaceSleepAndCache(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_64K.get()));
        put(helper, BUS_OUT, ModBlocks.GOLEM_INTERFACE.get());
        InterfaceBlockEntity itf = helper.getBlockEntity(BUS_OUT, InterfaceBlockEntity.class);
        itf.setSlot(0, new ItemStack(Items.IRON_INGOT), 4);
        itf.setSlot(1, new ItemStack(Items.WATER_BUCKET), 1000);

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 16), false);
            n.insertFluid(new FluidStack(Fluids.WATER, 4000), false);
        });

        int[] runsBefore = new int[1];
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(itf.isFluidSlot(1), "slot 1 cached as fluid slot");
            helper.assertTrue(!itf.isFluidSlot(0), "slot 0 cached as item slot");
            helper.assertTrue(itf.getStock().getItem(0).getCount() == 4, "iron stocked");
            helper.assertTrue(itf.fluidStock(1).getAmount() == 1000, "water stocked");
            helper.assertTrue(itf.isStockSleeping(), "satisfied interface entered sleep");
            runsBefore[0] = itf.restockRuns();
        });

        helper.runAfterDelay(95, () -> {
            helper.assertTrue(itf.restockRuns() == runsBefore[0],
                    "satisfied interface skipped restock for 50 ticks, delta=" + (itf.restockRuns() - runsBefore[0]));
            // Extracting items via capability must wake stock immediately
            int got;
            try (Transaction tx = Transaction.openRoot()) {
                got = itf.itemResources().extract(0, ItemResource.of(Items.IRON_INGOT), 2, tx);
                tx.commit();
            }
            helper.assertTrue(got == 2, "extracted 2 iron");
            helper.assertTrue(!itf.isStockSleeping(), "item extraction woke sleeping interface");
        });

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(itf.getStock().getItem(0).getCount() == 4, "iron restocked after wake");
            helper.assertTrue(itf.isStockSleeping(), "returned to satisfied sleep");
            // Extracting fluid via capability must wake stock immediately (slot 0 = tank, slots 1..9 = fluidStock 0..8)
            int got;
            try (Transaction tx = Transaction.openRoot()) {
                got = itf.fluidResources().extract(2, FluidResource.of(Fluids.WATER), 500, tx);
                tx.commit();
            }
            helper.assertTrue(got == 500, "extracted 500 mB water");
            helper.assertTrue(!itf.isStockSleeping(), "fluid extraction woke sleeping interface");
        });

        helper.runAfterDelay(145, () -> {
            helper.assertTrue(itf.fluidStock(1).getAmount() == 1000, "water restocked after wake");
            // Toggle mode invalidates fluid cache and survives NBT reload
            itf.toggleMode(1);
            helper.assertTrue(!itf.isFluidSlot(1), "toggleMode invalidated fluid slot cache");
            var level = helper.getLevel();
            CompoundTag saved = itf.saveWithFullMetadata(level.registryAccess());
            InterfaceBlockEntity reloaded = (InterfaceBlockEntity) net.minecraft.world.level.block.entity.BlockEntity
                    .loadStatic(helper.absolutePos(BUS_OUT), itf.getBlockState(), saved, level.registryAccess());
            helper.assertTrue(reloaded != null && !reloaded.isFluidSlot(1) && !reloaded.isFluidSlot(0),
                    "reloaded interface preserved cached slot modes");
            // Unsatisfied slot sleeps with backoff, then wakes immediately when missing item enters the network
            itf.setSlot(2, new ItemStack(Items.DIAMOND), 2);
        });

        helper.runAfterDelay(195, () -> {
            helper.assertTrue(itf.isStockSleeping(), "unsatisfied interface sleeping on missing diamond");
            helper.assertTrue(itf.getStock().getItem(2).isEmpty(), "no diamonds stocked yet");
            net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 2), false);
        });

        helper.runAfterDelay(210, () -> {
            helper.assertTrue(itf.getStock().getItem(2).getCount() == 2,
                    "unsatisfied interface woke on networkEpoch and stocked diamonds, got " + itf.getStock().getItem(2));
            // Inserting a pre-filled disk must also wake an unsatisfied interface promptly without storageEpoch changing
            itf.setSlot(3, new ItemStack(Items.EMERALD), 2);
        });

        helper.runAfterDelay(255, () -> {
            helper.assertTrue(itf.isStockSleeping(), "unsatisfied interface sleeping on missing emerald");
            ItemStack prefilled = new ItemStack(ModItems.COPPER_DISK_1K.get());
            com.golemlogix.system.DiskStorage.insertItem(prefilled, new ItemStack(Items.EMERALD, 4), false);
            drive.getContainer().setItem(2, prefilled);
        });

        helper.runAfterDelay(275, () -> {
            helper.assertTrue(itf.getStock().getItem(3).getCount() == 2,
                    "unsatisfied interface woke on disk insert and stocked emeralds, got " + itf.getStock().getItem(3));
            helper.succeed();
        });
    }

    /** Idle buses back off past 40t, and stalled import / sleeping export buses wake on both networkEpoch and bay disk swaps. */
    public static void busIdleAndFullImportWake(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos outBarrelPos = BUS_OUT.north();
        put(helper, outBarrelPos, Blocks.BARREL);
        var outBarrel = (net.minecraft.world.Container) helper.getBlockEntity(outBarrelPos, net.minecraft.world.level.block.entity.BlockEntity.class);
        BusBlockEntity expBus = helper.getBlockEntity(BUS_OUT, BusBlockEntity.class);
        expBus.getFilters().setItem(0, new ItemStack(Items.NETHER_STAR));

        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos inBarrel = BUS_IN.west();
        put(helper, inBarrel, Blocks.BARREL);
        var barrel = (net.minecraft.world.Container) helper.getBlockEntity(inBarrel, net.minecraft.world.level.block.entity.BlockEntity.class);
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        BusBlockEntity impBus = helper.getBlockEntity(BUS_IN, BusBlockEntity.class);

        // Fill the 1k disk completely so the import bus stalls on a full network
        GolemNetwork n = net(helper, bay);
        for (int i = 0; i < 16; i++) {
            n.insertItem(new ItemStack(Items.DIRT, 64), false);
        }

        int[] expOps0 = new int[1];
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(expBus.idle(), "idle export bus sleeping");
            helper.assertTrue(impBus.idle(), "stalled import bus sleeping on full network");
            helper.assertTrue(barrel.getItem(0).getCount() == 16, "cobblestone still waiting in barrel");
            expOps0[0] = expBus.operations;
        });

        helper.runAfterDelay(250, () -> {
            int delta = expBus.operations - expOps0[0];
            helper.assertTrue(delta <= 1, "idle export bus backed off past 40t: ran " + delta + " ops in 90t");
            // Free space in the network -> stalled import bus must wake via networkEpoch well before 200t
            net(helper, bay).extractItem(new ItemStack(Items.DIRT), 64, false);
        });

        helper.runAfterDelay(275, () -> {
            helper.assertTrue(barrel.isEmpty(), "stalled import bus woke on networkEpoch and drained barrel");
            helper.assertTrue(net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 16,
                    "all 16 cobblestone imported");
            // Re-fill disk to capacity and stall import bus again so we can test bay-disk wakeup for both buses
            GolemNetwork cur = net(helper, bay);
            cur.insertItem(new ItemStack(Items.DIRT, 48), false);
            barrel.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
        });

        helper.runAfterDelay(355, () -> {
            helper.assertTrue(impBus.idle(), "re-stalled import bus sleeping on full network");
            helper.assertTrue(expBus.idle(), "export bus sleeping on missing nether star");
            // Insert a disk holding 1 nether star (with plenty of free room) -> wakes BOTH stalled import and sleeping export bus
            ItemStack prefilled = new ItemStack(ModItems.COPPER_DISK_1K.get());
            com.golemlogix.system.DiskStorage.insertItem(prefilled, new ItemStack(Items.NETHER_STAR, 1), false);
            drive.getContainer().setItem(1, prefilled);
        });

        helper.runAfterDelay(385, () -> {
            helper.assertTrue(barrel.isEmpty(), "stalled import bus woke on bay disk insert and drained planks");
            helper.assertTrue(outBarrel.getItem(0).is(Items.NETHER_STAR),
                    "sleeping export bus woke on bay disk insert and exported nether star");
            helper.succeed();
        });
    }

    /** Atlas Core skips redundant syncs across cache rolls, refreshes cargo on disk swap, and syncs visuals on power change. */
    public static void coreSkipsRedundantSync(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = helper.getBlockEntity(BAY, CopperDriveBayBlockEntity.class);
        net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 3), false);

        int[] syncs0 = new int[1];
        int[] picks0 = new int[1];
        helper.runAfterDelay(35, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            syncs0[0] = core.visualSyncCount();
            picks0[0] = core.cargoPickCount();
            helper.assertTrue(syncs0[0] >= 1 && picks0[0] >= 1, "initial sync and cargo pick ran");
            helper.assertTrue(core.cargoItems().stream().anyMatch(s -> s.is(Items.DIAMOND)), "initial cargo has diamond");
        });

        helper.runAfterDelay(40, GolemNetwork::clearCache);
        helper.runAfterDelay(100, GolemNetwork::clearCache);

        helper.runAfterDelay(160, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            helper.assertTrue(core.visualSyncCount() == syncs0[0],
                    "unchanged core skipped visual sync across cache rolls: " + core.visualSyncCount() + " vs " + syncs0[0]);
            helper.assertTrue(core.cargoPickCount() == picks0[0],
                    "unchanged core skipped cargo pick across cache rolls: " + core.cargoPickCount() + " vs " + picks0[0]);
            // Removing the disk on this network must refresh cargo promptly on the next core update
            drive.getContainer().setItem(0, ItemStack.EMPTY);
        });

        helper.runAfterDelay(195, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            helper.assertTrue(core.cargoPickCount() > picks0[0], "disk removal triggered cargo refresh");
            helper.assertTrue(core.cargoItems().isEmpty(), "stale diamond cargo cleared after disk removal");
            // Removing Source breaks power -> must trigger visual sync promptly
            helper.setBlock(JAR, Blocks.AIR);
        });

        helper.runAfterDelay(235, () -> {
            AtlasCoreBlockEntity core = helper.getBlockEntity(CORE, AtlasCoreBlockEntity.class);
            if (SourceHelper.sourceRequired()) {
                helper.assertTrue(!core.isPowered(), "core lost power");
                helper.assertTrue(core.visualSyncCount() > syncs0[0], "power change synced visuals immediately");
            } else {
                helper.assertTrue(core.isPowered(), "core stays powered in Free Mode without source jar");
            }
            helper.succeed();
        });
    }

    /** Inserting a disk or changing priority on a Copper Drive Bay must immediately invalidate the network cache. */
    public static void driveBayDiskInvalidatesCache(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(15, () -> {
            var level = helper.getLevel();
            BlockPos absBay = helper.absolutePos(bay);
            CopperDriveBayBlockEntity drive = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
            GolemNetwork n1 = GolemNetwork.get(level, absBay);
            drive.getContainer().setItem(1, new ItemStack(ModItems.COPPER_DISK_1K.get()));
            GolemNetwork n2 = GolemNetwork.get(level, absBay);
            helper.assertTrue(n2 != n1, "disk insertion invalidated network cache immediately");
            drive.setPriority(5);
            GolemNetwork n3 = GolemNetwork.get(level, absBay);
            helper.assertTrue(n3 != n2, "bay priority change invalidated network cache immediately");
            helper.succeed();
        });
    }
}
