package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.CryoDiodeBlock;
import com.golemlogix.blocks.MainframePartBlock;
import com.golemlogix.blocks.TelegraphBlock;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.TelegraphLinkCardItem;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.MainframeStructure;
import com.golemlogix.system.TelegraphLinks;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Regression GameTests for system/, crafting/, and upgrade/ fixes.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreRegressionTests {
    private static final String T = "empty";

    private CoreRegressionTests() {
    }

    private static ItemStack card(PatternData data) {
        ItemStack stack = new ItemStack(ModItems.COPPER_PUNCH_CARD.get());
        stack.set(ModDataComponents.PATTERN.get(), data);
        return stack;
    }

    /** Builds a tiny powered network (core on a bushing, 8 channels) with a Drive Bay (1K item disk); returns the bushing pos. */
    private static BlockPos buildTinyNetwork(GameTestHelper helper) {
        BlockPos bushing = new BlockPos(4, 2, 4);
        BlockPos bay = new BlockPos(5, 2, 4);
        helper.setBlock(bushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bushing.above(), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bushing.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
        CopperDriveBayBlock.place(helper.getLevel(), helper.absolutePos(bay), Direction.SOUTH);
        CopperDriveBayBlockEntity bayBe = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
        bayBe.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        return bushing;
    }

    /** A Cryo-Diode touched on a non-outlet side before its outlet face in BFS must still supply Source to the receiver network. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void coreCryoDiodeSideVisitedBeforeOutlet(GameTestHelper helper) {
        BlockPos jar = new BlockPos(2, 2, 2);
        BlockPos inletPipe = new BlockPos(3, 2, 2);
        BlockPos diode = new BlockPos(4, 2, 2);
        BlockPos receiverBushing = new BlockPos(4, 2, 3); // touches diode's SOUTH (non-outlet) face directly
        BlockPos receiverCore = new BlockPos(4, 3, 3);
        BlockPos cornerPipe = new BlockPos(5, 2, 3);
        BlockPos outletPipe = new BlockPos(5, 2, 2);      // touches diode's EAST (outlet) face

        helper.setBlock(jar, ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.setBlock(inletPipe, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(diode, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(CryoDiodeBlock.FACING, Direction.EAST));
        helper.setBlock(receiverBushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(receiverCore, ModBlocks.ATLAS_CORE.get());
        helper.setBlock(cornerPipe, ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(outletPipe, ModBlocks.BASIC_SNOW_PIPE.get());

        helper.runAfterDelay(5, () -> {
            GolemNetwork net = GolemNetwork.scan(helper.getLevel(), helper.absolutePos(receiverBushing));
            helper.assertTrue(net.contains(helper.absolutePos(outletPipe)), "receiver network must reach outletPipe");
            helper.assertTrue(net.sources().size() == 1, "expected 1 diode source, got " + net.sources().size());
            helper.assertTrue(net.hasInfiniteSource(), "receiver network must see infinite Source through Cryo-Diode outlet");
            helper.succeed();
        });
    }

    /** Two recipes whose byproducts feed each other must not cancel each other out into a zero-seed deadlocking plan. */
    @GameTest(template = T, timeoutTicks = 80)
    public static void coreMutualByproductNoDeadlock(GameTestHelper helper) {
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos cpu = bushing.south();
        BlockPos prov = bushing.west();
        helper.setBlock(cpu, ModBlocks.CRYO_CALCULATOR.get());
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(prov);

        // Register base recipes for coal and gold_nugget first so their patterns are acyclic
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.CHARCOAL)), List.of(new ItemStack(Items.COAL)))));
        provider.getContainer().setItem(1, card(new PatternData(false,
                List.of(new ItemStack(Items.STICK)), List.of(new ItemStack(Items.GOLD_NUGGET)))));
        // Emerald visits coal in DFS before copper/iron, while 1 emerald in stock keeps coal demand at 1
        provider.getContainer().setItem(2, card(new PatternData(false,
                List.of(new ItemStack(Items.COAL)), List.of(new ItemStack(Items.EMERALD)))));
        // Mutual byproducts: iron needs coal & outputs gold_nugget; copper needs gold_nugget & outputs coal
        provider.getContainer().setItem(3, card(new PatternData(false,
                List.of(new ItemStack(Items.COAL)),
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_NUGGET)))));
        provider.getContainer().setItem(4, card(new PatternData(false,
                List.of(new ItemStack(Items.GOLD_NUGGET)),
                List.of(new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.COAL)))));
        provider.getContainer().setItem(5, card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT), new ItemStack(Items.EMERALD)),
                List.of(new ItemStack(Items.DIAMOND)))));

        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey charcoal = ItemKey.of(new ItemStack(Items.CHARCOAL));
        ItemKey coal = ItemKey.of(new ItemStack(Items.COAL));

        helper.runAfterDelay(25, () -> {
            GolemNetwork net = GolemNetwork.refresh(helper.getLevel(), helper.absolutePos(bushing));
            helper.assertTrue(net.isOnline() && net.canCraft(diamond), "network must be online and see diamond pattern");
            net.insertItem(new ItemStack(Items.EMERALD, 1), false);

            CraftingPlan emptyPlan = CraftingPlanner.plan(net, diamond, 1);
            helper.assertTrue(emptyPlan.missing().getOrDefault(charcoal, 0L) == 1L,
                    "mutual byproduct recipes must require a seed instead of planning with empty storage: " + emptyPlan.missing());
            helper.assertTrue(net.startCrafting(diamond, 1).type() == GolemNetwork.StartType.MISSING,
                    "cannot start a deadlocking mutual-byproduct job without a seed");

            net.insertItem(new ItemStack(Items.COAL, 1), false);
            CraftingPlan seededPlan = CraftingPlanner.plan(net, diamond, 1);
            helper.assertTrue(seededPlan.missing().isEmpty(), "with 1 coal seed, plan succeeds: " + seededPlan.missing());
            helper.assertTrue(seededPlan.fromStorage().getOrDefault(coal, 0L) == 1L,
                    "seeded plan must reserve 1 coal from storage: " + seededPlan.fromStorage());
            helper.succeed();
        });
    }

    /** Huge orders near Long.MAX_VALUE with output > 1 must not overflow ceiling division into negative craft counts. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void corePlannerOverflowNoNegativeCrafts(GameTestHelper helper) {
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos prov = bushing.west();
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(prov);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.COBBLESTONE)),
                List.of(new ItemStack(Items.STONE, 4)))));

        ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));

        helper.runAfterDelay(25, () -> {
            GolemNetwork net = GolemNetwork.refresh(helper.getLevel(), helper.absolutePos(bushing));
            helper.assertTrue(net.isOnline() && net.canCraft(stone), "network must be online and see stone pattern");
            CraftingPlan plan = CraftingPlanner.plan(net, stone, Long.MAX_VALUE);
            helper.assertTrue(!plan.missing().isEmpty(), "Long.MAX_VALUE order must report missing instead of overflowing");
            helper.assertTrue(plan.steps().isEmpty(), "overflowing order must not schedule steps");
            helper.succeed();
        });
    }

    /** Oversized stacks in saved UpgradeContainer slots must clamp to slot max and preserve excess in strays; negative slot queries return null. */
    @GameTest(template = T, timeoutTicks = 40)
    public static void coreUpgradeOversizedSlotSpillsToStrays(GameTestHelper helper) {
        helper.assertTrue(UpgradeSpec.EXPORT_BUS.typeAt(-1) == null, "typeAt(-1) must return null");
        BlockPos busPos = new BlockPos(4, 2, 4);
        helper.setBlock(busPos, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        BusBlockEntity bus = (BusBlockEntity) helper.getBlockEntity(busPos);

        int redstoneSlot = UpgradeSpec.EXPORT_BUS.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE);
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putByte("Slot", (byte) redstoneSlot);
        list.add(new ItemStack(ModItems.REDSTONE_MODULE.get(), 5).save(helper.getLevel().registryAccess(), entry));

        bus.getUpgrades().fromTag(list, helper.getLevel().registryAccess());
        helper.assertTrue(bus.getUpgrades().getItem(redstoneSlot).getCount() == 1,
                "redstone slot must be clamped to 1, got " + bus.getUpgrades().getItem(redstoneSlot).getCount());
        helper.assertTrue(bus.getUpgrades().strayCount() == 1,
                "excess 4 modules must spill to strays, got strayCount=" + bus.getUpgrades().strayCount());
        helper.succeed();
    }

    /** DiskStorage.syncStored clears stale STORED counters and stays idempotent; fluidCountOf counts fluids directly. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void coreDiskSyncStoredAndFluidCount(GameTestHelper helper) {
        ItemStack emptyDisk = new ItemStack(ModItems.COPPER_DISK_1K.get());
        helper.assertTrue(!DiskStorage.syncStored(emptyDisk), "fresh empty disk should not report changed");

        ItemStack staleDisk = new ItemStack(ModItems.COPPER_DISK_1K.get());
        staleDisk.set(ModDataComponents.DISK_ID.get(), UUID.randomUUID());
        staleDisk.set(ModDataComponents.STORED.get(), 42);
        helper.assertTrue(DiskStorage.syncStored(staleDisk), "stale STORED counter on absent disk must be cleared");
        helper.assertTrue(!staleDisk.has(ModDataComponents.STORED.get()), "STORED component should be removed");
        helper.assertTrue(!DiskStorage.syncStored(staleDisk), "second syncStored call must be a no-op");

        BlockPos core = buildTinyNetwork(helper);
        CopperDriveBayBlockEntity bay = (CopperDriveBayBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 4));
        bay.getContainer().setItem(1, new ItemStack(ModItems.FLUID_DISK_4K.get()));

        helper.runAfterDelay(25, () -> {
            GolemNetwork net = GolemNetwork.refresh(helper.getLevel(), helper.absolutePos(core));
            int inserted = net.insertFluid(new FluidStack(Fluids.WATER, 2500), false);
            helper.assertTrue(inserted == 2500, "expected 2500 mB water inserted, got " + inserted);
            helper.assertTrue(net.fluidCountOf(new FluidStack(Fluids.WATER, 1)) == 2500L,
                    "fluidCountOf must match inserted mB");
            helper.succeed();
        });
    }

    /** MainframeStructure.disassemble must only restore parts whose FACING matches the disassembling controller. */
    @GameTest(template = T, timeoutTicks = 40)
    public static void coreMultiblockDisassembleRespectsFacing(GameTestHelper helper) {
        BlockPos controller = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos cell0 = MainframeStructure.cellPos(controller, Direction.NORTH, 0);
        BlockPos cell1 = MainframeStructure.cellPos(controller, Direction.NORTH, 1);

        BlockState foreignPart = ModBlocks.MAINFRAME_PART.get().defaultBlockState()
                .setValue(MainframePartBlock.FACING, Direction.WEST)
                .setValue(MainframePartBlock.CELL, 0);
        BlockState ownPart = ModBlocks.MAINFRAME_PART.get().defaultBlockState()
                .setValue(MainframePartBlock.FACING, Direction.NORTH)
                .setValue(MainframePartBlock.CELL, 1);

        helper.getLevel().setBlock(cell0, foreignPart, 3);
        helper.getLevel().setBlock(cell1, ownPart, 3);

        MainframeStructure.disassemble(helper.getLevel(), controller, Direction.NORTH, null);

        BlockState after0 = helper.getLevel().getBlockState(cell0);
        BlockState after1 = helper.getLevel().getBlockState(cell1);
        helper.assertTrue(after0.is(ModBlocks.MAINFRAME_PART.get()) && after0.getValue(MainframePartBlock.FACING) == Direction.WEST,
                "foreign mainframe part with different FACING must not be touched by disassemble");
        helper.assertTrue(after1.is(Blocks.GLASS), "own mainframe part must be restored to glass, got " + after1);
        helper.succeed();
    }

    /** Two telegraphs pointing at each other must not produce duplicate graph edges or double Source cost. */
    @GameTest(template = T, timeoutTicks = 80)
    public static void coreTelegraphMutualLinkCountedOnce(GameTestHelper helper) {
        BlockPos bushing = new BlockPos(2, 2, 4);
        BlockPos telA = new BlockPos(3, 2, 4);
        BlockPos telB = new BlockPos(8, 2, 4);

        helper.setBlock(bushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bushing.above(), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bushing.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
        TelegraphBlock.place(helper.getLevel(), helper.absolutePos(telA), Direction.SOUTH);
        TelegraphBlock.place(helper.getLevel(), helper.absolutePos(telB), Direction.SOUTH);

        TelegraphBlockEntity beA = (TelegraphBlockEntity) helper.getBlockEntity(telA);
        TelegraphBlockEntity beB = (TelegraphBlockEntity) helper.getBlockEntity(telB);

        ItemStack cardToB = new ItemStack(ModItems.AETHERIAL_LINKAGE.get());
        cardToB.set(ModDataComponents.TELEGRAPH_LINK.get(),
                new TelegraphLinkCardItem.Link(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(telB)), beB.seal()));
        ItemStack cardToA = new ItemStack(ModItems.AETHERIAL_LINKAGE.get());
        cardToA.set(ModDataComponents.TELEGRAPH_LINK.get(),
                new TelegraphLinkCardItem.Link(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(telA)), beA.seal()));

        beA.slots().setItem(TelegraphBlockEntity.SLOT_MEMORY, cardToB);
        beB.slots().setItem(TelegraphBlockEntity.SLOT_MEMORY, cardToA);

        helper.runAfterDelay(5, () -> {
            List<TelegraphLinks.Partner> partnersA = TelegraphLinks.partners(helper.getLevel(), helper.absolutePos(telA));
            helper.assertTrue(partnersA.size() == 1, "mutual link must not duplicate partner edge, got " + partnersA.size());
            GolemNetwork net = GolemNetwork.scan(helper.getLevel(), helper.absolutePos(bushing));
            helper.assertTrue(net.contains(helper.absolutePos(telB)), "network must traverse mutual telegraph link");
            helper.assertTrue(net.telegraphLinks() == 1, "mutual telegraph pair must count as 1 link, got " + net.telegraphLinks());
            helper.succeed();
        });
    }

    /** A global serverVersion bump from an unrelated network must not bump an unchanged network's epoch, while a real change must. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void coreUnrelatedCacheClearPreservesNetworkEpoch(GameTestHelper helper) {
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos absBushing = helper.absolutePos(bushing);

        helper.runAfterDelay(5, () -> {
            GolemNetwork before = GolemNetwork.get(helper.getLevel(), absBushing);
            before.insertItem(new ItemStack(Items.COBBLESTONE, 1), false);
            long epochBefore = before.networkEpoch();
            helper.assertTrue(epochBefore > 0, "insert should bump networkEpoch");

            // Simulate an unrelated network invalidating the global server cache version
            GolemNetwork.clearCache();
            GolemNetwork afterUnrelated = GolemNetwork.get(helper.getLevel(), absBushing);
            helper.assertTrue(afterUnrelated != before, "cache clear should trigger a fresh scan");
            helper.assertTrue(afterUnrelated.networkEpoch() == epochBefore,
                    "unrelated serverVersion bump must preserve unchanged network's epoch: expected "
                            + epochBefore + ", got " + afterUnrelated.networkEpoch());

            // A real change to this network's own topology must bump its epoch
            helper.setBlock(bushing.west(), ModBlocks.BASIC_SNOW_PIPE.get());
            GolemNetwork afterOwnChange = GolemNetwork.get(helper.getLevel(), absBushing);
            helper.assertTrue(afterOwnChange.networkEpoch() > epochBefore,
                    "real topology change on this network must bump networkEpoch");
            helper.succeed();
        });
    }

    /** Cached DiskIndex must stay consistent across priority changes, slot swaps, duplicate disk removal, and direct DiskStorage writes. */
    @GameTest(template = T, timeoutTicks = 60)
    public static void coreDiskIndexTracksLiveMutations(GameTestHelper helper) {
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos bayA = new BlockPos(5, 2, 4);
        BlockPos bayB = new BlockPos(3, 2, 4);
        CopperDriveBayBlock.place(helper.getLevel(), helper.absolutePos(bayB), Direction.SOUTH);
        CopperDriveBayBlockEntity beA = (CopperDriveBayBlockEntity) helper.getBlockEntity(bayA);
        CopperDriveBayBlockEntity beB = (CopperDriveBayBlockEntity) helper.getBlockEntity(bayB);
        beB.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        helper.runAfterDelay(5, () -> {
            GolemNetwork net = GolemNetwork.get(helper.getLevel(), helper.absolutePos(bushing));
            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));

            // 1. Insert into higher-priority bayB, then flip priority so bayA receives the next insert
            beA.setPriority(0);
            beB.setPriority(10);
            net.insertItem(new ItemStack(Items.IRON_INGOT, 20), false);
            helper.assertTrue(DiskStorage.itemCount(beB.getDisks().get(0), iron) == 20, "high-priority bayB should receive first insert");
            helper.assertTrue(net.itemCountOf(iron) == 20L, "itemCountOf should reflect 20 iron");

            beA.setPriority(20);
            net.insertItem(new ItemStack(Items.GOLD_INGOT, 15), false);
            ItemKey gold = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
            helper.assertTrue(DiskStorage.itemCount(beA.getDisks().get(0), gold) == 15, "newly higher-priority bayA should receive gold");

            // 2. Direct DiskStorage write bypassing GolemNetwork must be visible immediately
            DiskStorage.insertItem(beA.getDisks().get(0), new ItemStack(Items.IRON_INGOT, 5), false);
            helper.assertTrue(net.itemCountOf(iron) == 25L, "direct DiskStorage write must invalidate inverted index: got " + net.itemCountOf(iron));

            // 3. Multi-disk extract must pull lowest priority (bayB) first, then bayA
            ItemStack taken = net.extractItem(new ItemStack(Items.IRON_INGOT), 22, false);
            helper.assertTrue(taken.getCount() == 22, "expected 22 iron extracted, got " + taken.getCount());
            helper.assertTrue(DiskStorage.itemCount(beB.getDisks().get(0), iron) == 0, "lower-priority bayB must drain first");
            helper.assertTrue(DiskStorage.itemCount(beA.getDisks().get(0), iron) == 3, "bayA should retain 3 iron");
            helper.assertTrue(net.itemCountOf(iron) == 3L, "remaining iron count must be 3");

            // 4. Warm listItemsForPlanning/listItemsShared in the same tick, then remove disk from slot: must invalidate immediately
            helper.assertTrue(net.listItemsForPlanning().getOrDefault(iron, 0L) == 3L,
                    "warm listItemsForPlanning should see 3 iron before removal");
            beA.getContainer().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(net.itemCountOf(iron) == 0L, "removing disk from bayA must drop iron count to 0");
            helper.assertTrue(net.itemCountOf(gold) == 0L, "removing disk from bayA must drop gold count to 0");
            helper.assertTrue(net.listItemsForPlanning().getOrDefault(iron, 0L) == 0L,
                    "same-tick disk removal must invalidate listItemsForPlanning");
            helper.assertTrue(net.listItemsShared().getOrDefault(gold, 0L) == 0L,
                    "same-tick disk removal must invalidate listItemsShared");
            helper.succeed();
        });
    }
}
