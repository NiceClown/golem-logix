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
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.TelegraphLinks;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Regression GameTests for system/, crafting/, and upgrade/ fixes.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class CoreRegressionTests {
    private record Test(String name, Consumer<GameTestHelper> body, int maxTicks) {
    }

    private static final List<Test> TESTS = List.of(
            new Test("core_cryo_diode_side_visited_before_outlet", CoreRegressionTests::cryoDiodeSideVisitedBeforeOutlet, 60),
            new Test("core_mutual_byproduct_no_deadlock", CoreRegressionTests::mutualByproductNoDeadlock, 80),
            new Test("core_planner_overflow_no_negative_crafts", CoreRegressionTests::plannerOverflowNoNegativeCrafts, 60),
            new Test("core_upgrade_oversized_slot_spills_to_strays", CoreRegressionTests::upgradeOversizedSlotSpillsToStrays, 40),
            new Test("core_disk_sync_stored_and_fluid_count", CoreRegressionTests::diskSyncStoredAndFluidCount, 60),
            new Test("core_multiblock_disassemble_respects_facing", CoreRegressionTests::multiblockDisassembleRespectsFacing, 40),
            new Test("core_telegraph_mutual_link_counted_once", CoreRegressionTests::telegraphMutualLinkCountedOnce, 80),
            new Test("core_unrelated_cache_clear_preserves_network_epoch", CoreRegressionTests::unrelatedCacheClearPreservesNetworkEpoch, 60)
    );

    private CoreRegressionTests() {
    }

    @SubscribeEvent
    public static void onRegisterFunctions(RegisterEvent event) {
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
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                id("core_regression"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (Test t : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), 0, true)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
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
        CopperDriveBayBlockEntity bayBe = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
        bayBe.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        return bushing;
    }

    /** A Cryo-Diode touched on a non-outlet side before its outlet face in BFS must still supply Source to the receiver network. */
    private static void cryoDiodeSideVisitedBeforeOutlet(GameTestHelper helper) {
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
    private static void mutualByproductNoDeadlock(GameTestHelper helper) {
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos cpu = bushing.south();
        BlockPos prov = bushing.west();
        helper.setBlock(cpu, ModBlocks.CRYO_CALCULATOR.get());
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get());
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);

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
    private static void plannerOverflowNoNegativeCrafts(GameTestHelper helper) {
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos prov = bushing.west();
        helper.setBlock(prov, ModBlocks.PATTERN_PROVIDER.get());
        PatternProviderBlockEntity provider = helper.getBlockEntity(prov, PatternProviderBlockEntity.class);
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
    private static void upgradeOversizedSlotSpillsToStrays(GameTestHelper helper) {
        helper.assertTrue(UpgradeSpec.EXPORT_BUS.typeAt(-1) == null, "typeAt(-1) must return null");
        BlockPos busPos = new BlockPos(4, 2, 4);
        helper.setBlock(busPos, ModBlocks.INJECTOR_EXPORT_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        BusBlockEntity bus = helper.getBlockEntity(busPos, BusBlockEntity.class);

        int redstoneSlot = UpgradeSpec.EXPORT_BUS.slotOf(com.golemlogix.upgrade.UpgradeType.REDSTONE);
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putByte("Slot", (byte) redstoneSlot);
        list.add(NbtBridge.saveItemInto(new ItemStack(ModItems.REDSTONE_MODULE.get(), 5), helper.getLevel().registryAccess(), entry));

        bus.getUpgrades().fromTag(list, helper.getLevel().registryAccess());
        helper.assertTrue(bus.getUpgrades().getItem(redstoneSlot).getCount() == 1,
                "redstone slot must be clamped to 1, got " + bus.getUpgrades().getItem(redstoneSlot).getCount());
        helper.assertTrue(bus.getUpgrades().strayCount() == 1,
                "excess 4 modules must spill to strays, got strayCount=" + bus.getUpgrades().strayCount());
        helper.succeed();
    }

    /** DiskStorage.syncStored clears stale STORED counters and stays idempotent; fluidCountOf counts fluids directly. */
    private static void diskSyncStoredAndFluidCount(GameTestHelper helper) {
        ItemStack emptyDisk = new ItemStack(ModItems.COPPER_DISK_1K.get());
        helper.assertTrue(!DiskStorage.syncStored(emptyDisk), "fresh empty disk should not report changed");

        ItemStack staleDisk = new ItemStack(ModItems.COPPER_DISK_1K.get());
        staleDisk.set(ModDataComponents.DISK_ID.get(), UUID.randomUUID());
        staleDisk.set(ModDataComponents.STORED.get(), 42);
        helper.assertTrue(DiskStorage.syncStored(staleDisk), "stale STORED counter on absent disk must be cleared");
        helper.assertTrue(!staleDisk.has(ModDataComponents.STORED.get()), "STORED component should be removed");
        helper.assertTrue(!DiskStorage.syncStored(staleDisk), "second syncStored call must be a no-op");

        BlockPos core = buildTinyNetwork(helper);
        CopperDriveBayBlockEntity bay = helper.getBlockEntity(new BlockPos(5, 2, 4), CopperDriveBayBlockEntity.class);
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
    private static void multiblockDisassembleRespectsFacing(GameTestHelper helper) {
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
    private static void telegraphMutualLinkCountedOnce(GameTestHelper helper) {
        BlockPos bushing = new BlockPos(2, 2, 4);
        BlockPos telA = new BlockPos(3, 2, 4);
        BlockPos telB = new BlockPos(8, 2, 4);

        helper.setBlock(bushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bushing.above(), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bushing.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
        TelegraphBlock.place(helper.getLevel(), helper.absolutePos(telA), Direction.SOUTH);
        TelegraphBlock.place(helper.getLevel(), helper.absolutePos(telB), Direction.SOUTH);

        TelegraphBlockEntity beA = helper.getBlockEntity(telA, TelegraphBlockEntity.class);
        TelegraphBlockEntity beB = helper.getBlockEntity(telB, TelegraphBlockEntity.class);

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
    private static void unrelatedCacheClearPreservesNetworkEpoch(GameTestHelper helper) {
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
}
