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
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
}
