package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.blocks.ChannelCounterBlock;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.blocks.TelegraphBlock;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.DiskStore;
import com.golemlogix.system.Gear;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.JarMerge;
import com.golemlogix.system.TelegraphLinks;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regression GameTests backported from main (2.1.5): calculator break refund, Giga Jar -> Reactor merge,
 * Stock Manometer hysteresis, Interface pulse intake+restock, autocraft catalysts and transitive byproducts,
 * cross-dimension network epochs, Distillation Tower carry delivery, disk gear recounting, free CPU checks,
 * Channel Counter client sync, and Plasma Temper +1 Looting.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GolemGameTestsBackport12 {
    private static final String T = "empty";

    private GolemGameTestsBackport12() {
    }

    private static <B extends BlockEntity> B reload(GameTestHelper helper, BlockPos rel, Class<B> type) {
        BlockPos abs = helper.absolutePos(rel);
        var level = helper.getLevel();
        var old = level.getBlockEntity(abs);
        CompoundTag tag = old.saveWithFullMetadata(level.registryAccess());
        var fresh = BlockEntity.loadStatic(abs, old.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);
        GolemNetwork.refresh(level, abs);
        return type.cast(fresh);
    }

    /** A calculator broken in the middle of a job gives reserved items back to the network and drops none into the world. */
    @GameTest(template = T, timeoutTicks = 300)
    public static void brokenCalculatorRefundsToNetwork(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_GOLD)), List.of(new ItemStack(Items.GOLD_INGOT)))));
        ItemKey goldIngot = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
        ItemKey rawGold = ItemKey.of(new ItemStack(Items.RAW_GOLD));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_GOLD, 5), false);
            n.startCrafting(goldIngot, 5);
            helper.runAfterDelay(10, () -> {
                CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(GolemGameTests.CPU);
                helper.assertTrue(!cpu.isIdle(), "job running");
                helper.getLevel().destroyBlock(helper.absolutePos(GolemGameTests.CPU), true);
                helper.runAfterDelay(2, () -> {
                    long back = GolemGameTests.net(helper, bay).itemCountOf(rawGold);
                    var around = new AABB(helper.absolutePos(GolemGameTests.CPU)).inflate(3);
                    int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                            e -> e.getItem().is(Items.RAW_GOLD)).stream().mapToInt(e -> e.getItem().getCount()).sum();
                    helper.assertTrue(back == 5 && dropped == 0, "the raw gold went back to storage: " + back + ", dropped " + dropped);
                    helper.succeed();
                });
            });
        });
    }

    /** A full Giga Jar grown into a reactor keeps all of its Source, and non-corner cells update comparators. */
    @GameTest(template = T, timeoutTicks = 80)
    public static void fullGigaJarGrowsWithoutLoss(GameTestHelper helper) {
        var level = helper.getLevel();
        Block jarBlock = ModBlocks.CRYO_SOURCE_JAR.get();
        java.util.function.Function<BlockPos, CryoJarBlockEntity> jar =
                p -> (CryoJarBlockEntity) helper.getBlockEntity(p);
        java.util.function.Consumer<BlockPos> put = p -> CryoJarBlock.place(level, helper.absolutePos(p), Direction.SOUTH, jarBlock);
        BlockPos c = new BlockPos(1, 1, 1);
        for (BlockPos p : List.of(c, c.east(), c.south(), c.east().south())) {
            put.accept(p);
        }
        helper.runAfterDelay(4, () -> {
            helper.setBlock(c.east().north().below(), Blocks.STONE);
            helper.setBlock(c.east().north(), Blocks.COMPARATOR.defaultBlockState()
                    .setValue(ComparatorBlock.FACING, Direction.SOUTH));
            var whole = jar.apply(c);
            helper.assertTrue(whole.form() == JarForm.GIGA, "the four joined");
        });
        helper.runAfterDelay(8, () -> {
            var whole = jar.apply(c);
            whole.fill(Integer.MAX_VALUE, false);
            helper.assertTrue(whole.available() == whole.capacity(), "the Giga Jar is full: " + whole.available());
        });
        helper.runAfterDelay(30, () -> {
            var cmp = (ComparatorBlockEntity) helper.getBlockEntity(c.east().north());
            helper.assertTrue(cmp.getOutputSignal() == 15, "a comparator at a jar that is not the corner sees the full whole: " + cmp.getOutputSignal());
            for (BlockPos p : List.of(c.east(2), c.east(2).south(), c.south(2), c.east().south(2), c.east(2).south(2))) {
                put.accept(p);
            }
        });
        helper.runAfterDelay(36, () -> {
            var whole = jar.apply(c);
            int giga = JarMerge.capacity(JarForm.GIGA);
            helper.assertTrue(whole.form() == JarForm.SUPER, "the nine are a reactor");
            helper.assertTrue(whole.available() == giga, "the reactor took all of the Giga Jar: " + whole.available() + " of " + giga);
            helper.succeed();
        });
    }

    /** Stock Manometer ABOVE hysteresis holds 0 or 15; Interface PULSE runs intake and restock together. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void manometerAndInterfaceEdgeFixes(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos manoPos = GolemGameTests.BUS_OUT;
        BlockPos itfPos = GolemGameTests.BUS_IN;
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState());
        helper.setBlock(itfPos, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        var mano = (StockManometerBlockEntity) helper.getBlockEntity(manoPos);
        var itf = (InterfaceBlockEntity) helper.getBlockEntity(itfPos);

        mano.getFilter().setItem(0, new ItemStack(Items.IRON_INGOT));
        mano.setMode(StockManometerBlockEntity.MODE_ANALOG);
        mano.setThreshold(640);

        itf.getUpgrades().setItem(itf.getUpgrades().spec().slotOf(UpgradeType.REDSTONE),
                new ItemStack(ModItems.REDSTONE_MODULE.get()));
        itf.menuData().set(0, RedstoneMode.PULSE.ordinal());
        itf.getConfig().setItem(0, new ItemStack(Items.GOLD_INGOT, 4));
        itf.getInbox().setItem(0, new ItemStack(Items.DIRT, 1));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 300), false);
            n.insertItem(new ItemStack(Items.GOLD_INGOT, 16), false);
        });
        helper.runAfterDelay(25, () -> {
            int analog = mano.getSignal();
            helper.assertTrue(analog > 0 && analog < 15, "analog signal is in-between: " + analog);
            mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
            mano.setThreshold(500);
            mano.setHysteresis(200);
            helper.setBlock(itfPos.west(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(45, () -> {
            int held = mano.getSignal();
            helper.assertTrue(held == 0 || held == 15, "ABOVE hysteresis band holds 0 or 15, not analog " + held);
            helper.assertTrue(itf.getInbox().getItem(0).isEmpty(), "interface flushed inbox on the pulse");
            helper.assertTrue(itf.getStock().getItem(0).is(Items.GOLD_INGOT) && itf.getStock().getItem(0).getCount() == 4,
                    "interface also restocked on the same pulse, stock=" + itf.getStock().getItem(0));
            helper.succeed();
        });
    }

    /** A processing recipe that returns its own input (catalyst) needs only 1 copy up front, reuses it across steps, and refunds it. */
    @GameTest(template = T, timeoutTicks = 300)
    public static void autocraftCatalystReusesSingleItem(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        BlockPos machine = GolemGameTests.PROV.east();
        helper.setBlock(machine, Blocks.BARREL);
        Container machineInv = (Container) helper.getBlockEntity(machine);
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.COBBLESTONE)),
                List.of(new ItemStack(Items.STONE), new ItemStack(Items.DIAMOND)))));

        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 1), false);
            n.insertItem(new ItemStack(Items.COBBLESTONE, 5), false);

            CraftingPlan plan = CraftingPlanner.plan(n, stone, 5);
            helper.assertTrue(plan.missing().isEmpty(), "1 catalyst is enough for 5 crafts: " + plan.missing());
            helper.assertTrue(plan.fromStorage().getOrDefault(diamond, 0L) == 1L,
                    "plans only 1 diamond from storage, got " + plan.fromStorage().get(diamond));
            helper.assertTrue(plan.fromStorage().getOrDefault(cobble, 0L) == 5L,
                    "plans 5 cobblestone from storage, got " + plan.fromStorage().get(cobble));

            GolemNetwork.StartResult res = n.startCrafting(stone, 5);
            helper.assertTrue(res.type() == GolemNetwork.StartType.STARTED, "catalyst craft starts: " + res.type());

            var cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(GolemGameTests.CPU);
            helper.succeedWhen(() -> {
                int diaSlot = -1, cobSlot = -1;
                for (int i = 0; i < machineInv.getContainerSize(); i++) {
                    ItemStack s = machineInv.getItem(i);
                    if (diaSlot < 0 && s.is(Items.DIAMOND) && !s.isEmpty()) {
                        diaSlot = i;
                    } else if (cobSlot < 0 && s.is(Items.COBBLESTONE) && !s.isEmpty()) {
                        cobSlot = i;
                    }
                }
                if (diaSlot >= 0 && cobSlot >= 0) {
                    machineInv.removeItem(diaSlot, 1);
                    machineInv.removeItem(cobSlot, 1);
                    GolemNetwork cur = GolemGameTests.net(helper, bay);
                    cur.insertFromMachine(new ItemStack(Items.STONE, 1));
                    cur.insertFromMachine(new ItemStack(Items.DIAMOND, 1));
                }
                GolemNetwork cur = GolemGameTests.net(helper, bay);
                helper.assertTrue(cpu.isIdle() && cur.itemCountOf(stone) == 5 && cur.itemCountOf(diamond) == 1,
                        "waiting for 5 stone and 1 returned catalyst, stone=" + cur.itemCountOf(stone) + ", diamond=" + cur.itemCountOf(diamond));
            });
        });
    }

    /** A transitive dependent must never consume a future parent's byproduct from planner surplus (no false plan / runtime deadlock). */
    @GameTest(template = T, timeoutTicks = 100)
    public static void autocraftTransitiveByproductNoDeadlock(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);

        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.COAL)), List.of(new ItemStack(Items.GOLD_NUGGET)))));
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.GOLD_NUGGET)), List.of(new ItemStack(Items.IRON_NUGGET)))));
        provider.getContainer().setItem(2, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_NUGGET)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.getContainer().setItem(3, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_NUGGET)))));

        ItemKey coal = ItemKey.of(new ItemStack(Items.COAL));
        ItemKey goldNugget = ItemKey.of(new ItemStack(Items.GOLD_NUGGET));
        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            CraftingPlan missingPlan = CraftingPlanner.plan(n, diamond, 1);
            helper.assertTrue(missingPlan.missing().getOrDefault(coal, 0L) == 1L,
                    "with 0 coal and 0 gold nuggets, must ask for coal instead of borrowing P's future byproduct: " + missingPlan.missing());
            helper.assertTrue(n.startCrafting(diamond, 1).type() == GolemNetwork.StartType.MISSING,
                    "cannot start a deadlocking job without the seed ingredient");

            n.insertItem(new ItemStack(Items.GOLD_NUGGET, 1), false);
            CraftingPlan okPlan = CraftingPlanner.plan(n, diamond, 1);
            helper.assertTrue(okPlan.missing().isEmpty(), "with 1 gold nugget seed, plan succeeds: " + okPlan.missing());
            helper.assertTrue(okPlan.fromStorage().getOrDefault(goldNugget, 0L) == 1L, "takes 1 gold nugget from storage");
            helper.succeed();
        });
    }

    /** Storage changes in one dimension immediately update the shared core epoch on cached telegraph networks in other dimensions. */
    @GameTest(template = T, timeoutTicks = 120)
    public static void crossDimensionNetworkEpochShared(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            helper.succeed();
            return;
        }
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos aRel = new BlockPos(7, 1, 6);
        TelegraphBlock.place(level, helper.absolutePos(aRel), Direction.SOUTH);

        BlockPos o = helper.absolutePos(GolemGameTests.ORIGIN);
        BlockPos b = new BlockPos(o.getX(), 100, o.getZ());
        nether.setChunkForced(b.getX() >> 4, b.getZ() >> 4, true);
        for (BlockPos p : BlockPos.betweenClosed(b.offset(-2, -1, -2), b.offset(3, 4, 3))) {
            nether.setBlock(p, p.getY() == b.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        TelegraphBlock.place(nether, b, Direction.SOUTH);
        BlockPos termPos = b.west();
        nether.setBlock(termPos, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState(), 3);

        helper.runAfterDelay(25, () -> {
            try {
                var a = (TelegraphBlockEntity) helper.getBlockEntity(aRel);
                var bt = (TelegraphBlockEntity) nether.getBlockEntity(b);
                helper.assertTrue(bt != null, "outpost telegraph placed in Nether");
                a.slots().setItem(TelegraphBlockEntity.SLOT_WRITE, new ItemStack(ModItems.AETHERIAL_LINKAGE.get()));
                ItemStack card = a.slots().removeItem(TelegraphBlockEntity.SLOT_WRITE, 1);
                bt.slots().setItem(TelegraphBlockEntity.SLOT_MEMORY, card);
                a.slots().setItem(TelegraphBlockEntity.SLOT_RANGE, new ItemStack(ModItems.VOID_GLASS_PRISM.get()));
                helper.assertTrue(TelegraphLinks.status(bt) == TelegraphLinks.Status.LINKED,
                        "cross-dimension telegraph linked");

                GolemNetwork owNet = GolemNetwork.refresh(level, helper.absolutePos(bay));
                GolemNetwork netherNet = GolemNetwork.refresh(nether, termPos);
                helper.assertTrue(owNet.hasCore() && netherNet.hasCore() && netherNet.corePos().equals(owNet.corePos()),
                        "both sides share the same core");
                long epBefore = netherNet.networkEpoch();
                helper.assertTrue(epBefore == owNet.networkEpoch(), "initial cross-dim epochs match: " + epBefore + " vs " + owNet.networkEpoch());

                owNet.insertItem(new ItemStack(Items.COBBLESTONE, 1), false);
                long epAfterOw = owNet.networkEpoch();
                long epAfterNetherCached = netherNet.networkEpoch();
                helper.assertTrue(epAfterOw > epBefore, "Overworld epoch bumped on insert");
                helper.assertTrue(epAfterNetherCached == epAfterOw,
                        "Nether cached network immediately sees bumped shared epoch: " + epAfterNetherCached + " vs " + epAfterOw);
                helper.succeed();
            } finally {
                for (BlockPos p : BlockPos.betweenClosed(b.offset(-2, -1, -2), b.offset(3, 4, 3))) {
                    nether.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
                nether.setChunkForced(b.getX() >> 4, b.getZ() >> 4, false);
            }
        });
    }

    /** Distillation Tower delivers accumulated fractional carry Source as soon as jar room opens, even with an empty blood tank. */
    @GameTest(template = T, timeoutTicks = 80)
    public static void distillationTowerDeliversCarrySource(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos base = new BlockPos(3, 1, 3);
        BlockPos jarPos = base.east();
        DistillationTowerBlock.place(level, helper.absolutePos(base), Direction.SOUTH,
                ModBlocks.CRYO_DISTILLATION_TOWER.get());
        CryoJarBlock.place(level, helper.absolutePos(jarPos), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());

        var tower = (DistillationTowerBlockEntity) helper.getBlockEntity(base);
        var jar = (CryoJarBlockEntity) helper.getBlockEntity(jarPos);
        jar.fill(jar.capacity(), false);

        CompoundTag tag = tower.saveWithFullMetadata(level.registryAccess());
        tag.putDouble("Carry", 25.0);
        var fresh = (DistillationTowerBlockEntity)
                BlockEntity.loadStatic(helper.absolutePos(base), tower.getBlockState(), tag, level.registryAccess());
        level.setBlockEntity(fresh);

        helper.runAfterDelay(15, () -> {
            helper.assertTrue(jar.available() == jar.capacity(), "while jar is full, carry waits");
            helper.assertTrue(fresh.blood().isEmpty(), "blood tank is completely empty");
            jar.drain(100);
            helper.runAfterDelay(15, () -> {
                int expected = jar.capacity() - 100 + 25;
                helper.assertTrue(jar.available() == expected,
                        "all 25 carried Source delivered with zero blood, jar=" + jar.available() + ", expected=" + expected);
                helper.succeed();
            });
        });
    }

    /** Loading an old disk item into a Drive Bay (and DiskStore recount) repairs both STORED component and gear item counter. */
    @GameTest(template = T, timeoutTicks = 80)
    public static void oldDiskRecountsOnBayLoad(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        CopperDriveBayBlockEntity bayBe = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);

        ItemStack disk = new ItemStack(ModItems.COPPER_DISK_16K.get());
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(7);
        DiskStorage.insertItem(disk, new ItemStack(Items.COBBLESTONE, 5), false);
        DiskStorage.insertItem(disk, sword, false);

        UUID id = DiskStorage.diskId(disk);
        DiskStore store = DiskStore.get(level.getServer());
        DiskStore.Disk data = store.getOrNull(id);
        int expectedStored = 5 + Gear.cost(sword);
        helper.assertTrue(data != null && data.gearItems == 1 && data.storedItems == expectedStored,
                "initial disk counts gearItems=1 and stored=" + expectedStored);

        data.gearItems = 0;
        data.storedItems = 6;
        disk.set(ModDataComponents.STORED.get(), 6);
        store.recount();
        helper.assertTrue(data.gearItems == 1 && data.storedItems == expectedStored,
                "DiskStore.recount() restores gearItems=1 and storedItems=" + expectedStored + ", got gear=" + data.gearItems + ", stored=" + data.storedItems);

        bayBe.getContainer().setItem(0, disk);
        CopperDriveBayBlockEntity reloaded = reload(helper, bay, CopperDriveBayBlockEntity.class);
        helper.runAfterDelay(2, () -> {
            Integer synced = reloaded.getContainer().getItem(0).get(ModDataComponents.STORED.get());
            helper.assertTrue(synced != null && synced == expectedStored,
                    "CopperDriveBay onLoad syncs STORED component back to " + expectedStored + ", got " + synced);
            helper.succeed();
        });
    }

    /** A busy single Cryo-Calculator rejects a second bus/player order with NO_CPU and does not reserve or consume storage items. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void noPlanWithoutFreeCalculator(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(GolemGameTests.CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(GolemGameTests.PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(GolemGameTests.PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_GOLD)), List.of(new ItemStack(Items.GOLD_INGOT)))));

        ItemKey rawGold = ItemKey.of(new ItemStack(Items.RAW_GOLD));
        ItemKey goldIngot = ItemKey.of(new ItemStack(Items.GOLD_INGOT));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_GOLD, 2), false);

            var r1 = n.startCrafting(goldIngot, 1);
            helper.assertTrue(r1.type() == GolemNetwork.StartType.STARTED, "first job occupies the only calculator");
            helper.assertTrue(n.freeCpu() == null, "no free calculator left");

            var busOrder = n.startCrafting(Map.of(goldIngot, 1L), null, helper.absolutePos(GolemGameTests.BUS_OUT));
            helper.assertTrue(busOrder.type() == GolemNetwork.StartType.NO_CPU && busOrder.plan() == null,
                    "bus order returns NO_CPU without planning when no calculator is free");
            var playerOrder = n.startCrafting(goldIngot, 1);
            helper.assertTrue(playerOrder.type() == GolemNetwork.StartType.NO_CPU,
                    "player order also returns NO_CPU when all calculators are busy");
            helper.assertTrue(n.itemCountOf(rawGold) == 1,
                    "second raw gold remains untouched in storage, got " + n.itemCountOf(rawGold));
            helper.succeed();
        });
    }

    /** Channel Counter reads pipe channels + capacity, toggles display mode, and syncs all state through its update tag & save/load. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void channelCounterSyncsToClient(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos pipePos = GolemGameTests.BUS_OUT;
        BlockPos termPos = pipePos.north();
        BlockPos counterPos = pipePos.above();
        helper.setBlock(pipePos, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(termPos, ModBlocks.ANALOG_TERMINAL.get());
        helper.setBlock(counterPos, ModBlocks.CHANNEL_COUNTER.get().defaultBlockState()
                .setValue(ChannelCounterBlock.MOUNT, ChannelCounterBlock.Mount.PIPE_BASIC));

        var level = helper.getLevel();
        var counter = (ChannelCounterBlockEntity) helper.getBlockEntity(counterPos);
        helper.succeedWhen(() -> {
            helper.assertTrue(counter.online() && counter.used() == 1 && counter.capacity() == 16,
                    "counter reads live pipe channels: online=" + counter.online() + ", used=" + counter.used() + ", cap=" + counter.capacity());
            helper.assertTrue(!counter.showsCapacity(), "default mode shows used channels");
            counter.toggleMode();

            CompoundTag pkt = counter.getUpdateTag(level.registryAccess());
            var clientCounter = new ChannelCounterBlockEntity(helper.absolutePos(counterPos), counter.getBlockState());
            clientCounter.handleUpdateTag(pkt, level.registryAccess());
            helper.assertTrue(clientCounter.online() && clientCounter.used() == 1 && clientCounter.capacity() == 16 && clientCounter.showsCapacity(),
                    "update tag syncs used/capacity/online/mode to client");

            var diskReloaded = reload(helper, counterPos, ChannelCounterBlockEntity.class);
            helper.assertTrue(diskReloaded.showsCapacity(), "ShowCapacity mode persists across reload");
        });
    }

    /** Plasma Temper adds Looting to tempered weapons, delivers deterministic sheep loot to storage, and keeps stored tool plain & unworn. */
    @GameTest(template = T, timeoutTicks = 140)
    public static void plasmaTemperLootingDrops(GameTestHelper helper) {
        var reg = helper.getLevel().registryAccess();
        var fortune = reg.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
        var looting = reg.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
        var silk = reg.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        ItemStack plainSword = new ItemStack(Items.IRON_SWORD);
        ItemStack blessedSword = AnnihilationBusBlockEntity.temperedHand(plainSword, Blocks.AIR.defaultBlockState(), reg);
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(looting, blessedSword) >= 1,
                "temperedHand grants Looting >= I");
        ItemStack silkSword = plainSword.copy();
        silkSword.enchant(silk, 1);
        ItemStack blessedSilk = AnnihilationBusBlockEntity.temperedHand(silkSword, Blocks.AIR.defaultBlockState(), reg);
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(fortune, blessedSilk) == 0,
                "temperedHand respects Silk Touch / Fortune mutual exclusion");
        helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(looting, blessedSilk) >= 1,
                "temperedHand still grants Looting alongside Silk Touch");

        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos bus = GolemGameTests.BUS_OUT, front = bus.north();
        helper.setBlock(bus, ModBlocks.ANNIHILATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.AIR);
        helper.setBlock(front.below(), Blocks.STONE);
        var be = (AnnihilationBusBlockEntity) helper.getBlockEntity(bus);
        be.menuData().set(1, AnnihilationBusBlockEntity.ATTACK);
        be.getTool().setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        be.getUpgrades().setItem(UpgradeSpec.ANNIHILATION_BUS.slotOf(UpgradeType.TEMPER), new ItemStack(ModItems.PLASMA_TEMPER.get()));

        var sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, front);
        ItemKey wool = ItemKey.of(new ItemStack(Items.WHITE_WOOL));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(!sheep.isAlive(), "tempered golem killed the sheep");
            GolemNetwork n = GolemGameTests.net(helper, bay);
            helper.assertTrue(n.itemCountOf(wool) >= 1, "guaranteed sheep wool went into storage, got " + n.itemCountOf(wool));
            ItemStack kept = be.getTool().getItem(0);
            helper.assertTrue(!kept.isEnchanted() && kept.getDamageValue() == 0,
                    "kept sword stays unenchanted and undamaged: " + kept);
            helper.succeed();
        });
    }
}
