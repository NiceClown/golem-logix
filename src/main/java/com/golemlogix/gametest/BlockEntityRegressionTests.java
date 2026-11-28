package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.CryoMixerBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.golemlogix.gametest.GolemGameTests.*;

/**
 * Regression GameTests for block entity fixes (interface fluid autocraft intake, manometer fuzzy & update sync,
 * chained Cryo-Diodes, export bus fluid round-robin, annihilation bus refill preservation, duplicate core formed flag,
 * periscope redstone cache invalidation, and distillation tower multi-network jar discovery).
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockEntityRegressionTests {
    private static final String T = "empty";

    private BlockEntityRegressionTests() {
    }

    /** Fluids piped into a Golem Interface must satisfy waiting processing autocraft jobs (even with no fluid disk). */
    @GameTest(template = T, timeoutTicks = 240)
    public static void beregInterfaceFluidFeedsAutocraft(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        put(helper, CPU, ModBlocks.CRYO_CALCULATOR.get());
        put(helper, PROV, ModBlocks.PATTERN_PROVIDER.get());
        put(helper, PROV.east(), Blocks.CHEST);
        put(helper, BUS_IN, ModBlocks.GOLEM_INTERFACE.get());

        FluidStack bucketWater = new FluidStack(Fluids.WATER, 1000);
        ItemStack waterGhost = FluidGhostItem.of(bucketWater);
        PatternData makeWater = new PatternData(false, List.of(new ItemStack(Items.IRON_INGOT)), List.of(waterGhost));
        PatternData useWater = new PatternData(false, List.of(waterGhost, new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.DIAMOND)));
        ItemStack c1 = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        c1.set(ModDataComponents.PATTERN.get(), makeWater);
        ItemStack c2 = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        c2.set(ModDataComponents.PATTERN.get(), useWater);

        PatternProviderBlockEntity prov = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        prov.getContainer().setItem(0, c1);
        prov.getContainer().setItem(1, c2);

        InterfaceBlockEntity itf = (InterfaceBlockEntity) helper.getBlockEntity(BUS_IN);
        CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(CPU);

        helper.runAfterDelay(25, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 1), false);
            n.insertItem(new ItemStack(Items.DIRT, 1), false);
            var res = n.startCrafting(ItemKey.of(new ItemStack(Items.DIAMOND)), 1);
            helper.assertTrue(res.type() == GolemNetwork.StartType.STARTED, "job started: " + res.type());
        });

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(cpu.getJob() != null && cpu.wants(Ingredients.fluidKey(bucketWater)) == 1000,
                    "job awaits 1000 mB water");
            int inserted = itf.fluids().fill(bucketWater, IFluidHandler.FluidAction.EXECUTE);
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
    @GameTest(template = T, timeoutTicks = 120)
    public static void beregManometerFuzzyAndSync(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        put(helper, BUS_OUT, ModBlocks.STOCK_MANOMETER.get());
        StockManometerBlockEntity mano = (StockManometerBlockEntity) helper.getBlockEntity(BUS_OUT);
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
    @GameTest(template = T, timeoutTicks = 140)
    public static void beregCryoDiodeChain(GameTestHelper helper) {
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
    @GameTest(template = T, timeoutTicks = 160)
    public static void beregExportBusFluidRoundRobin(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_64K.get()));

        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(BUS_OUT);
        bus.getFilters().setItem(0, FluidGhostItem.of(new FluidStack(Fluids.WATER, 1000)));
        bus.getFilters().setItem(1, FluidGhostItem.of(com.golemlogix.system.CryoSource.stack(1000)));
        bus.menuData().set(2, FormationBusBlockEntity.ROUND_ROBIN);

        // Cryo-Mixer (paused by Redstone Module in HIGH mode) accepts Water in flask 0 and Liquid Cryo-Source in flask 1
        BlockPos target = BUS_OUT.north();
        CryoMixerBlock.place(helper.getLevel(), helper.absolutePos(target), Direction.SOUTH, ModBlocks.CRYO_MIXER.get());
        CryoMixerBlockEntity mixer = (CryoMixerBlockEntity) helper.getBlockEntity(target);
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
    @GameTest(template = T, timeoutTicks = 140)
    public static void beregAnnihilationRefillPreservedOnFull(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos busPos = BUS_OUT, front = BUS_OUT.north();
        helper.setBlock(busPos, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.WATER);

        AnnihilationBusBlockEntity bus = (AnnihilationBusBlockEntity) helper.getBlockEntity(busPos);
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
            ItemStack refill = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved.getCompound("Refill"));
            helper.assertTrue(refill.is(Items.BUCKET), "refill template remained empty bucket, got: " + refill);
            helper.succeed();
        });
    }

    /** A bare duplicate Atlas Core next to a formed primary array must report isFormed() == false. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void beregDuplicateCoreNotFormed(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);

        // Place a bare second Atlas Core connected to the primary network via a bushing
        BlockPos dupPos = BUS_OUT;
        put(helper, dupPos, ModBlocks.ATLAS_CORE.get());

        helper.runAfterDelay(45, () -> {
            AtlasCoreBlockEntity primary = (AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            AtlasCoreBlockEntity dup = (AtlasCoreBlockEntity) helper.getBlockEntity(dupPos);
            helper.assertTrue(primary.isFormed() && !primary.isDuplicate(), "primary core formed");
            helper.assertTrue(dup.isDuplicate(), "second core marked duplicate");
            helper.assertTrue(!dup.isFormed(), "bare duplicate core must NOT report isFormed() == true");
            helper.succeed();
        });
    }

    /** Changing a Periscope's Redstone Module / mode must immediately invalidate the network cache. */
    @GameTest(template = T, timeoutTicks = 120)
    public static void beregPeriscopeRedstoneInvalidates(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);

        BlockPos periPos = BUS_OUT;
        BlockPos chestPos = BUS_OUT.north();
        helper.setBlock(periPos, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.NORTH));
        put(helper, chestPos, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 10));

        PeriscopeBlockEntity peri = (PeriscopeBlockEntity) helper.getBlockEntity(periPos);
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
    @GameTest(template = T, timeoutTicks = 140)
    public static void beregTowerSecondNetworkJar(GameTestHelper helper) {
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
        DistillationTowerBlockEntity tower = (DistillationTowerBlockEntity) helper.getBlockEntity(towerPos);
        CompoundTag tag = tower.saveWithFullMetadata(level.registryAccess());
        tag.putDouble("Carry", 25.0);
        var fresh = (DistillationTowerBlockEntity) BlockEntity.loadStatic(
                helper.absolutePos(towerPos), tower.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);

        helper.runAfterDelay(40, () -> {
            CryoJarBlockEntity jar = (CryoJarBlockEntity) helper.getBlockEntity(jarCorner);
            helper.assertTrue(jar != null && jar.available() == 25,
                    "tower filled joined jar on second adjacent network despite disconnected pipe stubs: got "
                            + (jar == null ? -1 : jar.available()));
            helper.succeed();
        });
    }

    /** Satisfied interfaces sleep without re-running restock, wake on stock/config/network changes, and keep fluid cache fresh. */
    @GameTest(template = T, timeoutTicks = 340)
    public static void beregInterfaceSleepAndCache(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        drive.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_64K.get()));
        put(helper, BUS_OUT, ModBlocks.GOLEM_INTERFACE.get());
        InterfaceBlockEntity itf = (InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
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
            ItemStack got = itf.automation().extractItem(0, 2, false);
            helper.assertTrue(got.is(Items.IRON_INGOT) && got.getCount() == 2, "extracted 2 iron");
            helper.assertTrue(!itf.isStockSleeping(), "item extraction woke sleeping interface");
        });

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(itf.getStock().getItem(0).getCount() == 4, "iron restocked after wake");
            helper.assertTrue(itf.isStockSleeping(), "returned to satisfied sleep");
            // Extracting fluid via capability must wake stock immediately
            FluidStack got = itf.fluids().drain(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(got.getFluid() == Fluids.WATER && got.getAmount() == 500, "extracted 500 mB water");
            helper.assertTrue(!itf.isStockSleeping(), "fluid extraction woke sleeping interface");
        });

        helper.runAfterDelay(145, () -> {
            helper.assertTrue(itf.fluidStock(1).getAmount() == 1000, "water restocked after wake");
            // Toggle mode invalidates fluid cache and survives NBT reload
            itf.toggleMode(1);
            helper.assertTrue(!itf.isFluidSlot(1), "toggleMode invalidated fluid slot cache");
            var level = helper.getLevel();
            CompoundTag saved = itf.saveWithFullMetadata(level.registryAccess());
            InterfaceBlockEntity reloaded = (InterfaceBlockEntity) BlockEntity
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
    @GameTest(template = T, timeoutTicks = 420)
    public static void beregBusIdleAndFullImportWake(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        helper.setBlock(BUS_OUT, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        BlockPos outBarrelPos = BUS_OUT.north();
        put(helper, outBarrelPos, Blocks.BARREL);
        var outBarrel = (net.minecraft.world.Container) helper.getBlockEntity(outBarrelPos);
        BusBlockEntity expBus = (BusBlockEntity) helper.getBlockEntity(BUS_OUT);
        expBus.getFilters().setItem(0, new ItemStack(Items.NETHER_STAR));

        helper.setBlock(BUS_IN, ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos inBarrel = BUS_IN.west();
        put(helper, inBarrel, Blocks.BARREL);
        var barrel = (net.minecraft.world.Container) helper.getBlockEntity(inBarrel);
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        BusBlockEntity impBus = (BusBlockEntity) helper.getBlockEntity(BUS_IN);

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
    @GameTest(template = T, timeoutTicks = 260)
    public static void beregCoreSkipsRedundantSync(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(BAY);
        net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 3), false);

        int[] syncs0 = new int[1];
        int[] picks0 = new int[1];
        helper.runAfterDelay(35, () -> {
            AtlasCoreBlockEntity core = (AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            syncs0[0] = core.visualSyncCount();
            picks0[0] = core.cargoPickCount();
            helper.assertTrue(syncs0[0] >= 1 && picks0[0] >= 1, "initial sync and cargo pick ran");
            helper.assertTrue(core.cargoItems().stream().anyMatch(s -> s.is(Items.DIAMOND)), "initial cargo has diamond");
        });

        helper.runAfterDelay(40, GolemNetwork::clearCache);
        helper.runAfterDelay(100, GolemNetwork::clearCache);

        helper.runAfterDelay(160, () -> {
            AtlasCoreBlockEntity core = (AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            helper.assertTrue(core.visualSyncCount() == syncs0[0],
                    "unchanged core skipped visual sync across cache rolls: " + core.visualSyncCount() + " vs " + syncs0[0]);
            helper.assertTrue(core.cargoPickCount() == picks0[0],
                    "unchanged core skipped cargo pick across cache rolls: " + core.cargoPickCount() + " vs " + picks0[0]);
            // Removing the disk on this network must refresh cargo promptly on the next core update
            drive.getContainer().setItem(0, ItemStack.EMPTY);
        });

        helper.runAfterDelay(195, () -> {
            AtlasCoreBlockEntity core = (AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            helper.assertTrue(core.cargoPickCount() > picks0[0], "disk removal triggered cargo refresh");
            helper.assertTrue(core.cargoItems().isEmpty(), "stale diamond cargo cleared after disk removal");
            // Removing Source breaks power -> must trigger visual sync promptly
            helper.setBlock(JAR, Blocks.AIR);
        });

        helper.runAfterDelay(235, () -> {
            AtlasCoreBlockEntity core = (AtlasCoreBlockEntity) helper.getBlockEntity(CORE);
            helper.assertTrue(!core.isPowered(), "core lost power");
            helper.assertTrue(core.visualSyncCount() > syncs0[0], "power change synced visuals immediately");
            helper.succeed();
        });
    }

    /** Inserting a disk or changing priority on a Copper Drive Bay must immediately invalidate the network cache. */
    @GameTest(template = T, timeoutTicks = 80)
    public static void beregDriveBayDiskInvalidatesCache(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        helper.runAfterDelay(15, () -> {
            var level = helper.getLevel();
            BlockPos absBay = helper.absolutePos(bay);
            CopperDriveBayBlockEntity drive = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
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

    /** Interface with Fuzzy Card + #c:buckets tag filter must never overstack maxStackSize=1 water_bucket above 1. */
    @GameTest(template = T, timeoutTicks = 160)
    public static void beregInterfaceFuzzyBucketsNoOverstack(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        put(helper, BUS_OUT, ModBlocks.GOLEM_INTERFACE.get());
        InterfaceBlockEntity itf = (InterfaceBlockEntity) helper.getBlockEntity(BUS_OUT);
        itf.getUpgrades().setItem(itf.getUpgrades().spec().slotOf(UpgradeType.FUZZY),
                new ItemStack(ModItems.FUZZY_CARD.get()));
        var bucketsTag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "buckets"));
        ItemStack bucketFilter = Fuzzy.withTag(new ItemStack(Items.BUCKET), bucketsTag);
        itf.setSlot(0, bucketFilter, 16);

        ItemKey waterBucketKey = ItemKey.of(new ItemStack(Items.WATER_BUCKET));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            for (int i = 0; i < 10; i++) {
                n.insertItem(new ItemStack(Items.WATER_BUCKET), false);
            }
        });

        helper.runAfterDelay(45, () -> {
            ItemStack stocked = itf.getStock().getItem(0);
            helper.assertTrue(stocked.is(Items.WATER_BUCKET) && stocked.getCount() == 1,
                    "water_bucket must be capped to maxStackSize=1, got " + stocked);
            helper.assertTrue(net(helper, bay).itemCountOf(waterBucketKey) == 9,
                    "9 water buckets remain in storage, got " + net(helper, bay).itemCountOf(waterBucketKey));
            // Pre-existing overstack in stock slot (e.g. legacy save) must be trimmed back to 1 and excess returned to network
            itf.getStock().getItem(0).setCount(4);
            itf.wakeStock();
        });

        helper.runAfterDelay(85, () -> {
            ItemStack stocked = itf.getStock().getItem(0);
            helper.assertTrue(stocked.is(Items.WATER_BUCKET) && stocked.getCount() == 1,
                    "overstacked water_bucket trimmed back to 1, got " + stocked);
            helper.assertTrue(net(helper, bay).itemCountOf(waterBucketKey) == 12,
                    "3 excess water buckets returned to network (9 + 3 = 12), got " + net(helper, bay).itemCountOf(waterBucketKey));
            helper.succeed();
        });
    }

    /** Periodic Cryo-Jar integrity check disassembles mismatched controller footprints and detaches orphaned non-controllers. */
    @GameTest(template = T, timeoutTicks = 120)
    public static void beregCryoJarIntegrityValidation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos cornerA = new BlockPos(2, 1, 2);
        BlockPos cornerB = new BlockPos(7, 1, 2);
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                CryoJarBlock.place(level, helper.absolutePos(cornerA.offset(dx, 0, dz)), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
                CryoJarBlock.place(level, helper.absolutePos(cornerB.offset(dx, 0, dz)), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
            }
        }

        helper.runAfterDelay(10, () -> {
            CryoJarBlockEntity ctrlA = (CryoJarBlockEntity) helper.getBlockEntity(cornerA);
            CryoJarBlockEntity partA = (CryoJarBlockEntity) helper.getBlockEntity(cornerA.east());
            CryoJarBlockEntity ctrlB = (CryoJarBlockEntity) helper.getBlockEntity(cornerB);
            CryoJarBlockEntity orphanB = (CryoJarBlockEntity) helper.getBlockEntity(cornerB.east());
            helper.assertTrue(ctrlA.form() == JarForm.GIGA && ctrlB.form() == JarForm.GIGA, "both 2x2 Giga Jars formed");
            ctrlA.putAll(2000);

            // Simulate cell broken while controller chunk was unloaded (skip onRemove apart() by clearing controller ref first)
            partA.setController(null);
            level.setBlock(helper.absolutePos(cornerA.east()), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.assertTrue(ctrlA.form() == JarForm.GIGA, "controller still GIGA before periodic integrity tick");

            // Simulate controller broken while non-controller chunk was unloaded
            ctrlB.setController(null);
            level.setBlock(helper.absolutePos(cornerB), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.assertTrue(orphanB.form() == JarForm.GIGA, "non-controller still GIGA before periodic integrity tick");
        });

        helper.runAfterDelay(40, () -> {
            // Case A: controller saw missing cell and parted remaining 3 jars, preserving all 2000 Source
            int totalA = 0;
            for (BlockPos rel : List.of(cornerA, cornerA.south(), cornerA.offset(1, 0, 1))) {
                CryoJarBlockEntity j = (CryoJarBlockEntity) helper.getBlockEntity(rel);
                helper.assertTrue(j.form() == JarForm.SINGLE && j.controller() == null,
                        "remaining Giga Jar A cell reset to SINGLE at " + rel);
                totalA += j.available();
            }
            helper.assertTrue(totalA == 2000, "parted Giga Jar A preserved 2000 Source, got " + totalA);

            // Case B: orphaned non-controllers saw missing controller and detached to SINGLE
            for (BlockPos rel : List.of(cornerB.east(), cornerB.south(), cornerB.offset(1, 0, 1))) {
                CryoJarBlockEntity j = (CryoJarBlockEntity) helper.getBlockEntity(rel);
                helper.assertTrue(j.form() == JarForm.SINGLE && j.controller() == null,
                        "orphaned Giga Jar B cell detached to SINGLE at " + rel);
            }
            helper.succeed();
        });
    }
}
