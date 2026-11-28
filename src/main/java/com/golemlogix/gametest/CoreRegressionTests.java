package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.BusBlockEntity;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.CryoDiodeBlock;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.CryoMixerBlock;
import com.golemlogix.blocks.MainframePartBlock;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.blocks.TelegraphBlock;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.items.TelegraphLinkCardItem;
import com.golemlogix.menu.CalculatorMenu;
import com.golemlogix.menu.DriveBayMenu;
import com.golemlogix.menu.RecipeMonitorMenu;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.DiskStorage;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.MainframeStructure;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.system.TelegraphLinks;
import com.golemlogix.upgrade.UpgradeSpec;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
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

    private static ServerPlayer fakePlayer(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static boolean postPlace(GameTestHelper helper, BlockPos relPos, BlockState placed, BlockPos relAgainst, ServerPlayer player) {
        var level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(relPos);
        BlockSnapshot snap = BlockSnapshot.create(level.dimension(), level, absPos);
        level.setBlock(absPos, placed, 3);
        var evt = new BlockEvent.EntityPlaceEvent(snap, helper.getBlockState(relAgainst), player);
        NeoForge.EVENT_BUS.post(evt);
        level.setBlock(absPos, Blocks.AIR.defaultBlockState(), 3);
        return evt.isCanceled();
    }

    /**
     * Multi-terminal intersection, placement/break protection (GolemNode, SecurityTerminalBlock, AtlasCasingBlock),
     * RecipeMonitor CRAFT right-click access, and live BUILD/CRAFT revocation closing stillValid on device menus.
     */
    @GameTest(template = T, timeoutTicks = 120)
    public static void coreSecurityHardeningAndLiveMenus(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos bay = GolemGameTests.BAY;
        GolemGameTests.putBay(helper, bay);

        BlockPos secPosA = GolemGameTests.BUS_OUT;
        BlockPos calcPos = GolemGameTests.TERM;
        BlockPos monPos = GolemGameTests.BUS_IN;
        helper.setBlock(secPosA, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(calcPos, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(monPos, ModBlocks.RECIPE_MONITOR.get().defaultBlockState());

        ServerPlayer ownerA = fakePlayer(helper, "sec-owner-a");
        ServerPlayer ownerB = fakePlayer(helper, "sec-owner-b");
        ServerPlayer builder = fakePlayer(helper, "sec-builder");
        ServerPlayer crafter = fakePlayer(helper, "sec-crafter");
        ServerPlayer extractor = fakePlayer(helper, "sec-extractor");
        ServerPlayer stranger = fakePlayer(helper, "sec-stranger");

        SecurityTerminalBlockEntity secA = (SecurityTerminalBlockEntity) helper.getBlockEntity(secPosA);
        secA.setOwner(ownerA);
        secA.getCards().setItem(0, IdentityPunchCardItem.of(builder.getUUID(), "sec-builder", SecurityPermission.BUILD.mask));
        secA.getCards().setItem(1, IdentityPunchCardItem.of(crafter.getUUID(), "sec-crafter", SecurityPermission.CRAFT.mask));
        secA.getCards().setItem(2, IdentityPunchCardItem.of(extractor.getUUID(), "sec-extractor", SecurityPermission.EXTRACT.mask));

        helper.runAfterDelay(10, () -> {
            var level = helper.getLevel();
            BlockPos absSecA = helper.absolutePos(secPosA);
            BlockPos absCalc = helper.absolutePos(calcPos);
            BlockPos absMon = helper.absolutePos(monPos);
            BlockPos absBay = helper.absolutePos(bay);
            // Position players within reach of devices for stillValid checks
            builder.setPos(absBay.getX() + 0.5, absBay.getY(), absBay.getZ() + 0.5);
            crafter.setPos(absMon.getX() + 0.5, absMon.getY(), absMon.getZ() + 0.5);

            // 1. Placement checks: untrusted pipe denied, BUILD pipe allowed, non-owner BUILD SecurityTerminal denied, owner allowed
            BlockPos placeSpot = secPosA.north();
            helper.assertTrue(postPlace(helper, placeSpot, ModBlocks.BASIC_SNOW_PIPE.get().defaultBlockState(), secPosA, stranger),
                    "stranger cannot place GolemNode adjacent to secured network");
            helper.assertTrue(!postPlace(helper, placeSpot, ModBlocks.BASIC_SNOW_PIPE.get().defaultBlockState(), secPosA, builder),
                    "player with BUILD can place GolemNode adjacent to secured network");
            helper.assertTrue(postPlace(helper, placeSpot, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState(), secPosA, builder),
                    "non-owner with BUILD cannot place SecurityTerminal onto secured network");
            helper.assertTrue(!postPlace(helper, placeSpot, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState(), secPosA, ownerA),
                    "owner can place second SecurityTerminal onto secured network");

            // 2. Break checks: non-owner with BUILD cannot break SecurityTerminal; stranger cannot break AtlasCasingBlock
            var breakSecBuilder = new BlockEvent.BreakEvent(level, absSecA, level.getBlockState(absSecA), builder);
            NeoForge.EVENT_BUS.post(breakSecBuilder);
            helper.assertTrue(breakSecBuilder.isCanceled(), "non-owner with BUILD cannot break SecurityTerminal");
            var breakSecOwner = new BlockEvent.BreakEvent(level, absSecA, level.getBlockState(absSecA), ownerA);
            NeoForge.EVENT_BUS.post(breakSecOwner);
            helper.assertTrue(!breakSecOwner.isCanceled(), "owner can break SecurityTerminal");

            BlockPos casingRel = GolemGameTests.ORIGIN.offset(1, 3, 1); // top corner AtlasCasingBlock (pure AtlasPart)
            BlockPos absCasing = helper.absolutePos(casingRel);
            var breakCasingStranger = new BlockEvent.BreakEvent(level, absCasing, level.getBlockState(absCasing), stranger);
            NeoForge.EVENT_BUS.post(breakCasingStranger);
            helper.assertTrue(breakCasingStranger.isCanceled(), "stranger cannot break AtlasCasingBlock of secured Atlas Array");

            // 3. RecipeMonitorBlock right-click allows CRAFT, denies EXTRACT-only
            BlockHitResult monHit = new BlockHitResult(Vec3.atCenterOf(absMon), Direction.UP, absMon, false);
            var useMonCraft = new PlayerInteractEvent.RightClickBlock(crafter, InteractionHand.MAIN_HAND, absMon, monHit);
            NeoForge.EVENT_BUS.post(useMonCraft);
            helper.assertTrue(!useMonCraft.isCanceled(), "CRAFT-only player can open RecipeMonitorBlock");
            var useMonExtract = new PlayerInteractEvent.RightClickBlock(extractor, InteractionHand.MAIN_HAND, absMon, monHit);
            NeoForge.EVENT_BUS.post(useMonExtract);
            helper.assertTrue(useMonExtract.isCanceled(), "EXTRACT-only player cannot open RecipeMonitorBlock");

            // 4. Live BUILD / CRAFT revocation closes stillValid on open device menus
            CopperDriveBayBlockEntity bayBe = (CopperDriveBayBlockEntity) helper.getBlockEntity(bay);
            CryoCalculatorBlockEntity calcBe = (CryoCalculatorBlockEntity) helper.getBlockEntity(calcPos);
            DriveBayMenu bayMenu = new DriveBayMenu(201, builder.getInventory(), bayBe.getContainer(), bayBe.priorityData());
            CalculatorMenu calcMenu = new CalculatorMenu(202, builder.getInventory(), calcBe, calcBe.getUpgrades(),
                    calcBe.menuData(), ContainerLevelAccess.create(level, absCalc));
            RecipeMonitorMenu monMenu = new RecipeMonitorMenu(203, crafter.getInventory(), level, absMon,
                    ContainerLevelAccess.create(level, absMon));

            helper.assertTrue(bayMenu.stillValid(builder), "DriveBayMenu valid with BUILD");
            helper.assertTrue(calcMenu.stillValid(builder), "CalculatorMenu valid with BUILD");
            helper.assertTrue(monMenu.stillValid(crafter), "RecipeMonitorMenu valid with CRAFT");

            secA.togglePermission(0, SecurityPermission.BUILD);
            secA.togglePermission(1, SecurityPermission.CRAFT);
            helper.assertTrue(!bayMenu.stillValid(builder), "DriveBayMenu closes immediately on BUILD revocation");
            helper.assertTrue(!calcMenu.stillValid(builder), "CalculatorMenu closes immediately on BUILD revocation");
            helper.assertTrue(!monMenu.stillValid(crafter), "RecipeMonitorMenu closes immediately on CRAFT revocation");

            // Restore builder BUILD on secA and add a second terminal owned by ownerB: intersection must deny builder until granted on both
            secA.togglePermission(0, SecurityPermission.BUILD);
            BlockPos secPosB = secPosA.north();
            helper.setBlock(secPosB, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            SecurityTerminalBlockEntity secB = (SecurityTerminalBlockEntity) helper.getBlockEntity(secPosB);
            secB.setOwner(ownerB);

            GolemNetwork twoTermNet = GolemNetwork.refresh(level, absBay);
            helper.assertTrue(twoTermNet.securityTerminals().size() == 2, "network tracks both security terminals");
            helper.assertTrue(!twoTermNet.hasPermission(builder, SecurityPermission.BUILD),
                    "intersection denies player allowed on only one of two security terminals");
            secB.getCards().setItem(0, IdentityPunchCardItem.of(builder.getUUID(), "sec-builder", SecurityPermission.BUILD.mask));
            helper.assertTrue(twoTermNet.hasPermission(builder, SecurityPermission.BUILD),
                    "intersection allows player granted BUILD on both security terminals");

            helper.succeed();
        });
    }

    /**
     * Parallel Cryo-Diodes sharing one upstream jar must deduplicate Source (no 2x count / no voiding),
     * and sideways / backwards Cryo-Diodes at an inlet must not leak Source.
     */
    @GameTest(template = T, timeoutTicks = 80)
    public static void coreParallelAndSidewaysCryoDiodes(GameTestHelper helper) {
        var level = helper.getLevel();
        // Network A with a single Cryo-Source Jar holding 100 Source
        BlockPos jarA = new BlockPos(2, 2, 3);
        CryoJarBlock.place(level, helper.absolutePos(jarA), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        CryoJarBlockEntity jarBeA = (CryoJarBlockEntity) helper.getBlockEntity(jarA);
        jarBeA.putAll(100);
        helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(3, 2, 5), ModBlocks.BASIC_SNOW_PIPE.get());

        // Two parallel EAST-facing diodes from Net A -> Net B
        helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(CryoDiodeBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(4, 2, 5), ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(CryoDiodeBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(5, 2, 3), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(5, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.BASIC_SNOW_PIPE.get());

        // Sideways diode (NORTH) touching a 500-Source jar on its WEST side, placed at the WEST inlet of an EAST diode -> Net C
        BlockPos jarC = new BlockPos(2, 2, 8);
        CryoJarBlock.place(level, helper.absolutePos(jarC), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        ((CryoJarBlockEntity) helper.getBlockEntity(jarC)).putAll(500);
        helper.setBlock(new BlockPos(3, 2, 8), ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(CryoDiodeBlock.FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(4, 2, 8), ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(CryoDiodeBlock.FACING, Direction.EAST));
        BlockPos netCPipe = new BlockPos(5, 2, 8);
        helper.setBlock(netCPipe, ModBlocks.BASIC_SNOW_PIPE.get());

        helper.runAfterDelay(5, () -> {
            GolemNetwork netB = GolemNetwork.scan(level, helper.absolutePos(new BlockPos(5, 2, 4)));
            helper.assertTrue(netB.sources().size() == 2, "Net B sees 2 parallel diode suppliers, got " + netB.sources().size());
            if (SourceHelper.sourceRequired()) {
                helper.assertTrue(netB.hasSource(100), "Net B can draw up to 100 Source from shared jar");
                helper.assertTrue(!netB.hasSource(101), "Parallel diodes must NOT double-count shared 100-Source jar");
                helper.assertTrue(!netB.consumeSource(150), "consumeSource(150) fails cleanly when only 100 exists");
                helper.assertTrue(jarBeA.available() == 100, "failed consumeSource must not void jar Source, left=" + jarBeA.available());
                helper.assertTrue(netB.consumeSource(80), "consumeSource(80) succeeds");
                helper.assertTrue(jarBeA.available() == 20, "shared jar retains 20 Source after draining 80, got " + jarBeA.available());
            }

            GolemNetwork netC = GolemNetwork.scan(level, helper.absolutePos(netCPipe));
            int leaked = 0;
            for (SourceHelper.Supplier s : netC.sources()) {
                leaked += s.available();
            }
            helper.assertTrue(leaked == 0, "sideways diode at inlet must supply 0 Source, leaked=" + leaked);
            helper.succeed();
        });
    }

    /**
     * Two Periscope Storage Buses on opposite halves of a vanilla double chest (and on distinct faces of a
     * WorldlyContainer Furnace) must not double-count stock or simulated/real transfers, must match skipTarget
     * canonically across halves, and must preserve each Periscope's independent filters and access mode.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void coreDoubleChestTwoPeriscopesNoDoubleCount(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bushing = new BlockPos(4, 2, 4);
        helper.setBlock(bushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bushing.above(), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bushing.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());
        helper.setBlock(bushing.east(), ModBlocks.BASIC_SNOW_PIPE.get());

        BlockPos periAPos = new BlockPos(4, 2, 5);
        BlockPos periBPos = new BlockPos(5, 2, 5);
        BlockPos chestAPos = new BlockPos(4, 2, 6);
        BlockPos chestBPos = new BlockPos(5, 2, 6);
        helper.setBlock(periAPos, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.SOUTH));
        helper.setBlock(periBPos, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.SOUTH));
        helper.setBlock(chestAPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(chestBPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));

        ChestBlockEntity chestA = (ChestBlockEntity) helper.getBlockEntity(chestAPos);
        ChestBlockEntity chestB = (ChestBlockEntity) helper.getBlockEntity(chestBPos);
        PeriscopeBlockEntity periA = (PeriscopeBlockEntity) helper.getBlockEntity(periAPos);
        PeriscopeBlockEntity periB = (PeriscopeBlockEntity) helper.getBlockEntity(periBPos);
        chestA.setItem(0, new ItemStack(Items.DIAMOND, 20));

        ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = GolemNetwork.refresh(level, helper.absolutePos(bushing));
            helper.assertTrue(n.itemCountOf(diamond) == 20L,
                    "double chest with 2 periscopes must count 20 diamonds, got " + n.itemCountOf(diamond));
            helper.assertTrue(n.listItems().getOrDefault(diamond, 0L) == 20L,
                    "listItems must count 20 diamonds, got " + n.listItems().getOrDefault(diamond, 0L));
            int simExtract = n.extractItem(new ItemStack(Items.DIAMOND), 40, true).getCount();
            helper.assertTrue(simExtract == 20,
                    "simulated extract(40) from 20-diamond double chest must return 20, got " + simExtract);

            // Real extract with BOTH Periscopes in BOTH mode
            int realExtract = n.extractItem(new ItemStack(Items.DIAMOND), 15, false).getCount();
            helper.assertTrue(realExtract == 15 && n.itemCountOf(diamond) == 5L,
                    "real extract(15) in BOTH mode leaves 5 diamonds, got taken=" + realExtract + " left=" + n.itemCountOf(diamond));
            helper.assertTrue(n.insertItem(new ItemStack(Items.DIAMOND, 15), false).isEmpty() && n.itemCountOf(diamond) == 20L,
                    "re-inserting 15 diamonds restores 20");

            // Canonical skipTarget across opposite halves of the double chest
            helper.assertTrue(n.insertItem(new ItemStack(Items.DIAMOND, 5), true, helper.absolutePos(chestAPos)).getCount() == 5,
                    "skipTarget on chestA half must also skip Periscope on chestB half for insert");
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIAMOND), 5, true, helper.absolutePos(chestBPos)).isEmpty(),
                    "skipTarget on chestB half must also skip Periscope on chestA half for extract");

            // Unfiltered access-mode asymmetry: periA=INSERT_ONLY, periB=EXTRACT_ONLY -> diamonds still visible & extractable via B
            periA.menuData().set(2, PeriscopeBlockEntity.INSERT_ONLY);
            periB.menuData().set(2, PeriscopeBlockEntity.EXTRACT_ONLY);
            helper.assertTrue(n.itemCountOf(diamond) == 20L && n.listItems().getOrDefault(diamond, 0L) == 20L,
                    "INSERT_ONLY on A must not hide diamonds from unfiltered EXTRACT_ONLY on B");
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIAMOND), 40, true).getCount() == 20,
                    "simulated extract works via B when A is INSERT_ONLY");
            helper.assertTrue(n.insertItem(new ItemStack(Items.DIAMOND, 5), true).isEmpty(),
                    "simulated insert works via A when B is EXTRACT_ONLY");
            periA.menuData().set(2, PeriscopeBlockEntity.BOTH);
            periB.menuData().set(2, PeriscopeBlockEntity.BOTH);

            // Fill remaining 53 slots so only 44 diamond space remains -> simulate insert(64) must leave 20, not 0
            for (int i = 1; i < 27; i++) {
                chestA.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            for (int i = 0; i < 27; i++) {
                chestB.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            int simLeftover = n.insertItem(new ItemStack(Items.DIAMOND, 64), true).getCount();
            helper.assertTrue(simLeftover == 20,
                    "simulated insert(64) with 44 free slots must leave 20, got " + simLeftover);

            // Separate per-Periscope filters (diamonds on A, iron on B) and independent access mode
            chestA.clearContent();
            chestB.clearContent();
            chestA.setItem(0, new ItemStack(Items.DIAMOND, 20));
            chestB.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
            periA.getFilters().setItem(0, new ItemStack(Items.DIAMOND));
            periB.getFilters().setItem(0, new ItemStack(Items.IRON_INGOT));

            helper.assertTrue(n.itemCountOf(diamond) == 20L && n.itemCountOf(iron) == 12L,
                    "separate per-Periscope filters must expose both diamonds and iron");
            helper.assertTrue(n.listItems().getOrDefault(diamond, 0L) == 20L
                            && n.listItems().getOrDefault(iron, 0L) == 12L,
                    "listItems must include slots accepted by each Periscope's own filter");
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIAMOND), 40, true).getCount() == 20
                            && n.extractItem(new ItemStack(Items.IRON_INGOT), 40, true).getCount() == 12,
                    "simulated extract respects per-Periscope filters without double counting");
            helper.assertTrue(n.insertItem(new ItemStack(Items.GOLD_INGOT, 5), false).getCount() == 5,
                    "gold rejected when neither Periscope allows it");
            helper.assertTrue(n.insertItem(new ItemStack(Items.IRON_INGOT, 4), false).isEmpty()
                            && n.itemCountOf(iron) == 16L,
                    "iron insert accepted through Periscope B filter");

            periA.menuData().set(2, PeriscopeBlockEntity.INSERT_ONLY);
            helper.assertTrue(n.itemCountOf(diamond) == 0L
                            && n.extractItem(new ItemStack(Items.DIAMOND), 5, true).isEmpty(),
                    "INSERT_ONLY on Periscope A blocks diamond count/extract");
            helper.assertTrue(n.insertItem(new ItemStack(Items.DIAMOND, 3), false).isEmpty(),
                    "INSERT_ONLY on Periscope A still allows diamond insert");
            periA.menuData().set(2, PeriscopeBlockEntity.BOTH);
            helper.assertTrue(n.itemCountOf(diamond) == 23L,
                    "restoring BOTH exposes all 23 diamonds, got " + n.itemCountOf(diamond));

            // WorldlyContainer (Furnace TOP input slot 0 + SIDE fuel slot 1): distinct real slots must not collide
            BlockPos furnacePos = new BlockPos(1, 2, 4);
            helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
            helper.setBlock(new BlockPos(3, 3, 4), ModBlocks.BASIC_SNOW_PIPE.get());
            helper.setBlock(new BlockPos(2, 3, 4), ModBlocks.BASIC_SNOW_PIPE.get());
            helper.setBlock(new BlockPos(2, 2, 4), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                    .setValue(PeriscopeStorageBusBlock.FACING, Direction.WEST));
            helper.setBlock(new BlockPos(1, 3, 4), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                    .setValue(PeriscopeStorageBusBlock.FACING, Direction.DOWN));
            helper.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState());
        });

        helper.runAfterDelay(15, () -> {
            GolemNetwork n = GolemNetwork.refresh(level, helper.absolutePos(bushing));
            FurnaceBlockEntity furnace = (FurnaceBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 4));
            ItemKey dirt = ItemKey.of(new ItemStack(Items.DIRT));
            ItemKey coal = ItemKey.of(new ItemStack(Items.COAL));
            ItemKey oakLog = ItemKey.of(new ItemStack(Items.OAK_LOG));

            furnace.setItem(0, new ItemStack(Items.DIRT, 5));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
            var listed = n.listItems();
            helper.assertTrue(listed.getOrDefault(dirt, 0L) == 5L && listed.getOrDefault(coal, 0L) == 8L,
                    "Furnace TOP and SIDE handler-local slot 0 must map to real slots 0 and 1, got dirt="
                            + listed.getOrDefault(dirt, 0L) + " coal=" + listed.getOrDefault(coal, 0L));

            // Same item in BOTH slot 0 (input) and slot 1 (fuel): count, sim extract, and sim insert across both faces
            furnace.setItem(0, new ItemStack(Items.OAK_LOG, 20));
            furnace.setItem(1, new ItemStack(Items.OAK_LOG, 15));
            helper.assertTrue(n.itemCountOf(oakLog) == 35L && n.listItems().getOrDefault(oakLog, 0L) == 35L,
                    "Furnace TOP(20) + SIDE(15) oak_log must total 35, got " + n.itemCountOf(oakLog));
            helper.assertTrue(n.extractItem(new ItemStack(Items.OAK_LOG), 60, true).getCount() == 35,
                    "Furnace sim extract(60) across TOP + SIDE must yield 35");

            // 14 room in slot 0 + 14 room in slot 1 = 28 room; sim insert(64) must leave 36 (chest filters reject logs)
            furnace.setItem(0, new ItemStack(Items.OAK_LOG, 50));
            furnace.setItem(1, new ItemStack(Items.OAK_LOG, 50));
            int logLeftover = n.insertItem(new ItemStack(Items.OAK_LOG, 64), true).getCount();
            helper.assertTrue(logLeftover == 36,
                    "Furnace sim insert(64) across TOP(14) + SIDE(14) must leave 36, got " + logLeftover);
            helper.succeed();
        });
    }

    /**
     * Waiting Pattern Providers and pending bus craft orders must key by GlobalPos so identical XYZ in different
     * dimensions never collide, and CraftingJob NBT round-trip must preserve dimension while remaining backwards-compatible.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void coreCrossDimProviderAndBusKeysNoCollision(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos cpuPos = bushing.south();
        BlockPos provPos = bushing.west();
        helper.setBlock(cpuPos, ModBlocks.CRYO_CALCULATOR.get());
        helper.setBlock(provPos, ModBlocks.PATTERN_PROVIDER.get());
        helper.setBlock(provPos.west(), Blocks.CHEST);

        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(provPos);
        provider.setBlocking(true);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));

        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));
        BlockPos sharedXyz = helper.absolutePos(provPos);
        GlobalPos overworldKey = GlobalPos.of(level.dimension(), sharedXyz);
        GlobalPos netherKey = GlobalPos.of(Level.NETHER, sharedXyz);
        GlobalPos endKey = GlobalPos.of(Level.END, sharedXyz);

        helper.runAfterDelay(25, () -> {
            GolemNetwork net = GolemNetwork.refresh(level, helper.absolutePos(bushing));
            net.insertItem(new ItemStack(Items.RAW_IRON, 2), false);
            var start = net.startCrafting(Map.of(ingot, 2L), null, overworldKey);
            helper.assertTrue(start.type() == GolemNetwork.StartType.STARTED, "job started: " + start.type());
            helper.assertTrue(net.busCraftPending(overworldKey, ingot), "overworld bus order must be pending");
            helper.assertTrue(!net.busCraftPending(netherKey, ingot),
                    "nether bus at identical XYZ must not collide with overworld bus order");
        });

        helper.runAfterDelay(45, () -> {
            GolemNetwork net = GolemNetwork.get(level, helper.absolutePos(bushing));
            helper.assertTrue(net.providerBusy(overworldKey), "overworld provider must be busy while awaiting machine output");
            helper.assertTrue(!net.providerBusy(netherKey),
                    "nether provider at identical XYZ must not collide with overworld provider");

            CraftingJob job = ((CryoCalculatorBlockEntity) helper.getBlockEntity(cpuPos)).getJob();
            helper.assertTrue(job != null && job.hasInFlight(overworldKey) && !job.hasInFlight(netherKey),
                    "CraftingJob inFlight distinguishes dimensions at identical XYZ");

            // NBT round-trip preserves explicit non-Overworld dimension
            var registries = level.registryAccess();
            CompoundTag saved = job.save(registries);
            helper.assertTrue(level.dimension().location().toString().equals(saved.getString("RequesterBusDim")),
                    "saved NBT stores RequesterBusDim");
            ListTag flights = saved.getList("InFlight", Tag.TAG_COMPOUND);
            helper.assertTrue(flights.size() == 1
                            && level.dimension().location().toString().equals(flights.getCompound(0).getString("Dim")),
                    "saved InFlight stores Dim");

            saved.putString("RequesterBusDim", Level.NETHER.location().toString());
            flights.getCompound(0).putString("Dim", Level.NETHER.location().toString());
            CraftingJob netherLoaded = CraftingJob.load(saved, registries, Level.OVERWORLD);
            helper.assertTrue(netherLoaded != null && netherKey.equals(netherLoaded.requesterBus())
                            && netherLoaded.hasInFlight(netherKey) && !netherLoaded.hasInFlight(overworldKey),
                    "NBT round-trip must preserve Nether dimension even when loaded with Overworld default");

            // Legacy NBT without Dim / RequesterBusDim defaults to job's level dimension (direct and deferred via setLevel)
            CompoundTag legacy = saved.copy();
            legacy.remove("RequesterBusDim");
            legacy.getList("InFlight", Tag.TAG_COMPOUND).getCompound(0).remove("Dim");

            CraftingJob legacyDirect = CraftingJob.load(legacy, registries, Level.END);
            helper.assertTrue(legacyDirect != null && endKey.equals(legacyDirect.requesterBus())
                            && legacyDirect.hasInFlight(endKey),
                    "legacy NBT without Dim must default to supplied level dimension");

            CraftingJob legacyDeferred = CraftingJob.load(legacy, registries);
            helper.assertTrue(legacyDeferred != null, "legacy NBT loads cleanly with null level");
            legacyDeferred.resolveLegacyDimension(Level.NETHER);
            helper.assertTrue(netherKey.equals(legacyDeferred.requesterBus()) && legacyDeferred.hasInFlight(netherKey),
                    "deferred resolveLegacyDimension binds legacy NBT to BE's attached dimension");
            helper.succeed();
        });
    }

    /**
     * Two blocking Pattern Providers both dispatching raw iron: returning an ingot through provider 2 must settle
     * provider 2's own waiting batch first so provider 1 stays busy and provider 2 accepts the next batch.
     */
    @GameTest(template = T, timeoutTicks = 140)
    public static void coreProviderReturnSettlesOwnBatch(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bushing = buildTinyNetwork(helper);
        BlockPos cpuPos = bushing.south();
        BlockPos p1Pos = bushing.west();
        BlockPos p2Pos = bushing.north();
        BlockPos c1Pos = p1Pos.west();
        BlockPos c2Pos = p2Pos.north();
        helper.setBlock(cpuPos, ModBlocks.CRYO_CALCULATOR.get());
        helper.setBlock(p1Pos, ModBlocks.PATTERN_PROVIDER.get());
        helper.setBlock(p2Pos, ModBlocks.PATTERN_PROVIDER.get());
        helper.setBlock(c1Pos, Blocks.CHEST);
        helper.setBlock(c2Pos, Blocks.CHEST);

        PatternData smeltIron = new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        PatternProviderBlockEntity p1 = (PatternProviderBlockEntity) helper.getBlockEntity(p1Pos);
        PatternProviderBlockEntity p2 = (PatternProviderBlockEntity) helper.getBlockEntity(p2Pos);
        p1.getContainer().setItem(0, card(smeltIron));
        p2.getContainer().setItem(0, card(smeltIron));
        p1.setBlocking(true);
        p2.setBlocking(true);

        GlobalPos gp1 = GlobalPos.of(level.dimension(), helper.absolutePos(p1Pos));
        GlobalPos gp2 = GlobalPos.of(level.dimension(), helper.absolutePos(p2Pos));
        ItemKey ingot = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(10, () -> {
            GolemNetwork net = GolemNetwork.refresh(level, helper.absolutePos(bushing));
            net.insertItem(new ItemStack(Items.RAW_IRON, 3), false);
            var started = net.startCrafting(Map.of(ingot, 3L), null, null);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "3x iron craft started: " + started.type());
        });

        helper.runAfterDelay(35, () -> {
            GolemNetwork net = GolemNetwork.get(level, helper.absolutePos(bushing));
            ChestBlockEntity c1 = (ChestBlockEntity) helper.getBlockEntity(c1Pos);
            ChestBlockEntity c2 = (ChestBlockEntity) helper.getBlockEntity(c2Pos);
            helper.assertTrue(c1.countItem(Items.RAW_IRON) == 1 && c2.countItem(Items.RAW_IRON) == 1,
                    "both providers pushed 1 raw iron each, c1=" + c1.countItem(Items.RAW_IRON) + " c2=" + c2.countItem(Items.RAW_IRON));
            helper.assertTrue(net.providerBusy(gp1) && net.providerBusy(gp2), "both blocking providers busy");
            p2.getReturns().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        });

        helper.runAfterDelay(70, () -> {
            GolemNetwork net = GolemNetwork.get(level, helper.absolutePos(bushing));
            ChestBlockEntity c1 = (ChestBlockEntity) helper.getBlockEntity(c1Pos);
            ChestBlockEntity c2 = (ChestBlockEntity) helper.getBlockEntity(c2Pos);
            helper.assertTrue(c1.countItem(Items.RAW_IRON) == 1,
                    "provider 1 must remain busy and NOT receive batch 3, c1=" + c1.countItem(Items.RAW_IRON));
            helper.assertTrue(c2.countItem(Items.RAW_IRON) == 2,
                    "provider 2 must settle its own batch and accept batch 3, c2=" + c2.countItem(Items.RAW_IRON));
            p2.getReturns().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        });

        helper.runAfterDelay(90, () -> {
            GolemNetwork net = GolemNetwork.get(level, helper.absolutePos(bushing));
            helper.assertTrue(!net.providerBusy(gp2) && net.providerBusy(gp1),
                    "after returning through p2, p2 must be free and p1 still busy");
            p1.getReturns().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        });

        helper.runAfterDelay(115, () -> {
            GolemNetwork net = GolemNetwork.get(level, helper.absolutePos(bushing));
            helper.assertTrue(!net.providerBusy(gp1) && !net.providerBusy(gp2), "both providers idle after final return");
            helper.assertTrue(net.itemCountOf(ingot) == 3L, "all 3 iron ingots stored, got " + net.itemCountOf(ingot));
            helper.succeed();
        });
    }

    /**
     * Two Periscopes on both halves of a Cryo-Mixer must not double-count its output tray, and two Periscopes on two
     * faces of one Copper Tank must not double-count its water.
     */
    @GameTest(template = T, timeoutTicks = 100)
    public static void coreCryoMixerAndSharedTankDedup(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bushing = new BlockPos(4, 2, 4);
        helper.setBlock(bushing, ModBlocks.CABLE_BUSHING.get());
        helper.setBlock(bushing.above(), ModBlocks.ATLAS_CORE.get());
        helper.setBlock(bushing.below(), ModBlocks.CREATIVE_SOURCE_JAR.get());

        // East pipe column + 2 Periscopes on bottom & top halves of a Cryo-Mixer
        helper.setBlock(new BlockPos(5, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(5, 3, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(6, 2, 4), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(6, 3, 4), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.EAST));
        BlockPos mixerBase = new BlockPos(7, 2, 4);
        CryoMixerBlock.place(level, helper.absolutePos(mixerBase), Direction.NORTH, ModBlocks.CRYO_MIXER.get());
        CryoMixerBlockEntity mixer = (CryoMixerBlockEntity) helper.getBlockEntity(mixerBase);
        mixer.getOutput().setItem(0, new ItemStack(Items.ICE, 20));

        // West/South branch + 2 Periscopes on NORTH & EAST faces of one Copper Tank at (2,2,6)
        helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(2, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(3, 2, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        helper.setBlock(new BlockPos(2, 2, 5), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(3, 2, 6), ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, Direction.WEST));
        BlockPos tankPos = new BlockPos(2, 2, 6);
        helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get());
        IFluidHandler tankCap = level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(tankPos), Direction.UP);
        helper.assertTrue(tankCap != null && tankCap.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000,
                "filled 1000 mB water into Copper Tank");

        ItemKey ice = ItemKey.of(new ItemStack(Items.ICE));
        helper.runAfterDelay(15, () -> {
            GolemNetwork n = GolemNetwork.refresh(level, helper.absolutePos(bushing));
            helper.assertTrue(n.listItems().getOrDefault(ice, 0L) == 20L && n.itemCountOf(ice) == 20L,
                    "Cryo-Mixer 2 halves must show 20 ice, got " + n.itemCountOf(ice));
            helper.assertTrue(n.extractItem(new ItemStack(Items.ICE), 40, true).getCount() == 20,
                    "Cryo-Mixer sim extract(40) must return 20");
            helper.assertTrue(n.extractItem(new ItemStack(Items.ICE), 20, true, helper.absolutePos(mixerBase.above())).isEmpty(),
                    "skipTarget on Cryo-Mixer top half must skip bottom half too");
            helper.assertTrue(n.extractItem(new ItemStack(Items.ICE), 40, false).getCount() == 20
                            && n.itemCountOf(ice) == 0L,
                    "Cryo-Mixer real extract(40) takes exact 20 ice");

            helper.assertTrue(n.fluidCountOf(Fluids.WATER) == 1000L,
                    "shared Copper Tank must report 1000 mB water, got " + n.fluidCountOf(Fluids.WATER));
            long listedWater = n.listFluids().stream()
                    .filter(f -> f.getFluid() == Fluids.WATER)
                    .mapToLong(FluidStack::getAmount).sum();
            helper.assertTrue(listedWater == 1000L, "listFluids must report 1000 mB water, got " + listedWater);
            helper.assertTrue(n.extractFluid(new FluidStack(Fluids.WATER, 2000), 2000, true).getAmount() == 1000,
                    "sim extractFluid(2000) from shared Copper Tank must return 1000 mB");
            helper.succeed();
        });
    }
}
