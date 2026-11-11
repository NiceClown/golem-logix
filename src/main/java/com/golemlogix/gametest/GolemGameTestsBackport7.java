package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.RecipeMonitorBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blocks.PeriscopeStorageBusBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests backported from main's GolemGameTestsExtra (2.1.4): the Cryo Diode break, the Security Terminal, the
 * mechanical ASMR (typewriter ding, Recipe Monitor tick), cyclic patterns and a network split.
 */
@GameTestHolder(GolemLogixMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class GolemGameTestsBackport7 {
    private static final String T = "empty";

    // the same points as in GolemGameTests
    private static final BlockPos BAY = new BlockPos(7, 1, 4);
    private static final BlockPos CPU = new BlockPos(4, 1, 3);
    private static final BlockPos PROV = new BlockPos(7, 1, 6);
    private static final BlockPos TERM = new BlockPos(3, 1, 6);
    private static final BlockPos BUS_OUT = new BlockPos(6, 1, 3);

    private static void periscope(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(PeriscopeStorageBusBlock.FACING, facing));
    }

    private static ServerPlayer stranger(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static SecurityTerminalBlockEntity securityTerminal(GameTestHelper helper, BlockPos secPos) {
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        return (SecurityTerminalBlockEntity) helper.getBlockEntity(secPos);
    }

    // ------------------------------------------------------------ cryo diode

    @GameTest(template = T, timeoutTicks = 150)
    public static void cryoDiodeBreakLeavesBothNetworksWorking(GameTestHelper helper) {
        GolemGameTests.buildArray(helper, 0, 0);
        GolemGameTests.powered(helper);
        BlockPos bay1 = BAY;
        GolemGameTests.putBay(helper, bay1);
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay1)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        BlockPos diodePos = BUS_OUT;
        helper.setBlock(diodePos, ModBlocks.CRYO_DIODE.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos b2 = diodePos.north();
        GolemGameTests.put(helper, b2, ModBlocks.CABLE_BUSHING.get());
        GolemGameTests.put(helper, b2.offset(0, 1, 0), ModBlocks.ATLAS_CONDUIT.get());
        GolemGameTests.put(helper, b2.offset(0, 2, 0), ModBlocks.ATLAS_CONDUIT.get());
        GolemGameTests.put(helper, b2.offset(0, 3, 0), ModBlocks.ATLAS_CORE.get());
        GolemGameTests.put(helper, b2.offset(0, -1, 0), ModBlocks.CREATIVE_SOURCE_JAR.get()); // give net 2 its own power
        // the cabinet is 2x2, its other parts to the east and above: it stands clear of the bushing and touches it
        BlockPos bay2 = b2.west(2);
        GolemGameTests.putBay(helper, bay2);
        ((CopperDriveBayBlockEntity) helper.getBlockEntity(bay2)).getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));

        helper.runAfterDelay(15, () -> {
            helper.setBlock(diodePos, Blocks.AIR);

            helper.runAfterDelay(20, () -> {
                GolemNetwork net1 = GolemGameTests.net(helper, bay1);
                GolemNetwork net2 = GolemGameTests.net(helper, b2);
                helper.assertTrue(net1 != null && net1.isOnline(), "network 1 remains online and operational after diode break");
                helper.assertTrue(net2 != null && net2.isOnline(), "network 2 remains online and operational after diode break");

                // each network still stores items after the break
                net1.insertItem(new ItemStack(Items.DIAMOND, 10), false);
                net2.insertItem(new ItemStack(Items.EMERALD, 5), false);
                helper.assertTrue(net1.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "net 1 stores diamonds after break");
                helper.assertTrue(net1.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 0, "net 1 does not store net 2 items");
                helper.assertTrue(net2.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 5, "net 2 stores emeralds after break");
                helper.assertTrue(net2.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 0, "net 2 does not store net 1 items");

                helper.succeed();
            });
        });
    }

    // ------------------------------------------------------------ security

    @GameTest(template = T, timeoutTicks = 100)
    public static void securityTerminalBuildProtection(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        SecurityTerminalBlockEntity sec = securityTerminal(helper, BUS_OUT);

        ServerPlayer owner = GolemGameTests.testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer strangerWithInsert = stranger(helper, "stranger-insert");
        ItemStack insertCard = IdentityPunchCardItem.of(strangerWithInsert.getUUID(), strangerWithInsert.getScoreboardName(), SecurityPermission.INSERT.mask);
        sec.getCards().setItem(0, insertCard);

        ServerPlayer strangerNoCard = stranger(helper, "stranger-nocard");

        helper.runAfterDelay(10, () -> {
            BlockPos absBay = helper.absolutePos(bay);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absBay), Direction.UP, absBay, false);

            // the owner right clicks the drive bay: allowed
            var ownerEvt = new PlayerInteractEvent.RightClickBlock(owner, InteractionHand.MAIN_HAND, absBay, hit);
            NeoForge.EVENT_BUS.post(ownerEvt);
            helper.assertTrue(!ownerEvt.isCanceled(), "owner interaction with drive bay is allowed");

            // a stranger with INSERT only: denied (the bay requires BUILD)
            var insertEvt = new PlayerInteractEvent.RightClickBlock(strangerWithInsert, InteractionHand.MAIN_HAND, absBay, hit);
            NeoForge.EVENT_BUS.post(insertEvt);
            helper.assertTrue(insertEvt.isCanceled(), "stranger with only INSERT is denied drive bay access");

            // a stranger without a card: denied
            var noCardEvt = new PlayerInteractEvent.RightClickBlock(strangerNoCard, InteractionHand.MAIN_HAND, absBay, hit);
            NeoForge.EVENT_BUS.post(noCardEvt);
            helper.assertTrue(noCardEvt.isCanceled(), "stranger without card is denied drive bay access");

            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 150)
    public static void securityTerminalItemPermissions(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        SecurityTerminalBlockEntity sec = securityTerminal(helper, BUS_OUT);

        ServerPlayer owner = GolemGameTests.testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer playerInsertOnly = stranger(helper, "player-insert-only");
        sec.getCards().setItem(0, IdentityPunchCardItem.of(playerInsertOnly.getUUID(), "player-insert-only", SecurityPermission.INSERT.mask));

        ServerPlayer playerExtractOnly = stranger(helper, "player-extract-only");
        sec.getCards().setItem(1, IdentityPunchCardItem.of(playerExtractOnly.getUUID(), "player-extract-only", SecurityPermission.EXTRACT.mask));

        ServerPlayer playerCraftOnly = stranger(helper, "player-craft-only");
        sec.getCards().setItem(2, IdentityPunchCardItem.of(playerCraftOnly.getUUID(), "player-craft-only", SecurityPermission.CRAFT.mask));

        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        BlockPos absTerm = helper.absolutePos(TERM);
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.DIAMOND, 50), false);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);
            ItemKey diamond = ItemKey.of(new ItemStack(Items.DIAMOND));

            // 1. INSERT only: can insert, cannot extract
            TerminalMenu menuInsert = new TerminalMenu(81, playerInsertOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerInsertOnly.containerMenu = menuInsert;
            menuInsert.setCarried(new ItemStack(Items.GOLD_INGOT, 10));
            menuInsert.handleAction(playerInsertOnly, new TerminalActionPayload(81, TerminalActionPayload.Action.INSERT_ALL, ItemStack.EMPTY, FluidStack.EMPTY));
            helper.assertTrue(menuInsert.getCarried().isEmpty(), "player with INSERT successfully inserts items");

            menuInsert.handleAction(playerInsertOnly, new TerminalActionPayload(81, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menuInsert.getCarried().isEmpty(), "player with INSERT only is denied TAKE_STACK");
            helper.assertTrue(n.itemCountOf(diamond) == 50, "network items untouched by denied take");

            // 2. EXTRACT only: can extract, cannot order a craft (with a real pattern)
            TerminalMenu menuExtract = new TerminalMenu(82, playerExtractOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerExtractOnly.containerMenu = menuExtract;
            menuExtract.handleAction(playerExtractOnly, new TerminalActionPayload(82, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.DIAMOND), FluidStack.EMPTY));
            helper.assertTrue(menuExtract.getCarried().getCount() == 50, "player with EXTRACT successfully takes items");

            menuExtract.handleCraftRequest(playerExtractOnly, new CraftRequestPayload(82, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            helper.assertTrue(n.cpus().stream().allMatch(CraftingCpu::isIdle), "craft denied without CRAFT permission");

            // 3. CRAFT only: cannot extract, can order a craft of a real pattern
            TerminalMenu menuCraft = new TerminalMenu(83, playerCraftOnly.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            playerCraftOnly.containerMenu = menuCraft;
            helper.assertTrue(n.hasPermission(playerCraftOnly, SecurityPermission.CRAFT), "player has CRAFT permission");

            menuCraft.handleAction(playerCraftOnly, new TerminalActionPayload(83, TerminalActionPayload.Action.TAKE_STACK, new ItemStack(Items.OAK_LOG), FluidStack.EMPTY));
            helper.assertTrue(menuCraft.getCarried().isEmpty(), "player with CRAFT only is denied TAKE_STACK");
            helper.assertTrue(n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) == 10, "items untouched by denied take");

            menuCraft.handleCraftRequest(playerCraftOnly, new CraftRequestPayload(83, new ItemStack(Items.OAK_PLANKS), 4, false, 1));
            CryoCalculatorBlockEntity cpu = (CryoCalculatorBlockEntity) helper.getBlockEntity(CPU);
            helper.assertTrue(!cpu.isIdle(), "ordering craft of real pattern starts a job for player with CRAFT permission");

            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void securityTerminalJeiFillProtection(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        SecurityTerminalBlockEntity sec = securityTerminal(helper, BUS_OUT);

        ServerPlayer owner = GolemGameTests.testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer strangerNoExtract = stranger(helper, "stranger-no-extract");
        sec.getCards().setItem(0, IdentityPunchCardItem.of(strangerNoExtract.getUUID(), "stranger-no-extract", SecurityPermission.INSERT.mask));

        BlockPos absTerm = helper.absolutePos(TERM);
        helper.setBlock(TERM, ModBlocks.CRAFTING_TERMINAL.get().defaultBlockState());

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 20), false);

            // a stranger tries the JEI fillGrid without EXTRACT
            TerminalMenu menuStranger = new TerminalMenu(84, strangerNoExtract.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            List<List<ItemStack>> recipeOptions = List.of(List.of(new ItemStack(Items.IRON_INGOT)));
            menuStranger.fillGrid(strangerNoExtract, recipeOptions, false);
            helper.assertTrue(menuStranger.getSlot(1).getItem().isEmpty(), "fillGrid extracts nothing when player lacks EXTRACT permission");

            // the owner tries it (all permissions)
            TerminalMenu menuOwner = new TerminalMenu(85, owner.getInventory(), helper.getLevel(), absTerm, absTerm,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            menuOwner.fillGrid(owner, recipeOptions, false);
            helper.assertTrue(menuOwner.getSlot(1).getItem().is(Items.IRON_INGOT), "fillGrid succeeds when player has EXTRACT permission");

            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void securityTerminalBreakBlockAndCreativeBypass(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        SecurityTerminalBlockEntity sec = securityTerminal(helper, BUS_OUT);

        ServerPlayer owner = GolemGameTests.testPlayer(helper);
        sec.setOwner(owner);

        ServerPlayer strangerNoBuild = stranger(helper, "stranger-no-build");

        ServerPlayer creativeOp = stranger(helper, "creative-op");
        creativeOp.setGameMode(GameType.CREATIVE);
        GameType.CREATIVE.updatePlayerAbilities(creativeOp.getAbilities());
        // 1.21.1: op(profile) grants the server's operator level, which is 0 on the game test server: add a level 4 entry
        helper.getLevel().getServer().getPlayerList().getOps().add(
                new net.minecraft.server.players.ServerOpListEntry(creativeOp.getGameProfile(), 4, false));

        helper.runAfterDelay(10, () -> {
            try {
                BlockPos absBay = helper.absolutePos(bay);
                var state = helper.getLevel().getBlockState(absBay);

                // a stranger without BUILD breaks a network block: cancelled
                var breakEvt1 = new BlockEvent.BreakEvent(helper.getLevel(), absBay, state, strangerNoBuild);
                NeoForge.EVENT_BUS.post(breakEvt1);
                helper.assertTrue(breakEvt1.isCanceled(), "break block cancelled for stranger without BUILD");

                // the owner: allowed
                var breakEvt2 = new BlockEvent.BreakEvent(helper.getLevel(), absBay, state, owner);
                NeoForge.EVENT_BUS.post(breakEvt2);
                helper.assertTrue(!breakEvt2.isCanceled(), "break block allowed for owner");

                // a creative operator: allowed (bypasses security)
                var breakEvt3 = new BlockEvent.BreakEvent(helper.getLevel(), absBay, state, creativeOp);
                NeoForge.EVENT_BUS.post(breakEvt3);
                helper.assertTrue(!breakEvt3.isCanceled(), "break block allowed for creative operator bypass");

                helper.succeed();
            } finally {
                helper.getLevel().getServer().getPlayerList().deop(creativeOp.getGameProfile());
            }
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void networkWithoutSecurityTerminalAllowsAll(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        ServerPlayer stranger = stranger(helper, "random-stranger");

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            helper.assertTrue(!n.hasSecurityTerminal(), "network has no security terminal");

            BlockPos absBay = helper.absolutePos(bay);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absBay), Direction.UP, absBay, false);
            var clickEvt = new PlayerInteractEvent.RightClickBlock(stranger, InteractionHand.MAIN_HAND, absBay, hit);
            NeoForge.EVENT_BUS.post(clickEvt);
            helper.assertTrue(!clickEvt.isCanceled(), "without security terminal, right click is allowed for anyone");

            var breakEvt = new BlockEvent.BreakEvent(helper.getLevel(), absBay, helper.getLevel().getBlockState(absBay), stranger);
            NeoForge.EVENT_BUS.post(breakEvt);
            helper.assertTrue(!breakEvt.isCanceled(), "without security terminal, break block is allowed for anyone");

            helper.succeed();
        });
    }

    // ------------------------------------------------------------ mechanical ASMR

    @GameTest(template = T, timeoutTicks = 100)
    public static void asmrTypewriterDingConditions(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);
        provider.getContainer().setItem(0, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork network = GolemGameTests.net(helper, bay);
            ItemKey planksKey = ItemKey.of(new ItemStack(Items.OAK_PLANKS));

            // a plan with 1 execution (4 planks)
            CraftingPlan plan1 = CraftingPlanner.plan(network, planksKey, 4);
            var smallJob = new CraftingJob(plan1, Map.of()).requestedBy(null, BUS_OUT);
            helper.assertTrue(!CryoCalculatorBlockEntity.dings(smallJob), "small bus job (< 16 executions, requester == null) must not ding");

            // a plan with a player requester
            ServerPlayer player = GolemGameTests.testPlayer(helper);
            var playerJob = new CraftingJob(plan1, Map.of()).requestedBy(player.getUUID(), null);
            helper.assertTrue(CryoCalculatorBlockEntity.dings(playerJob), "player order (requester != null) must ding");

            // a plan with 20 executions
            CraftingPlan plan20 = CraftingPlanner.plan(network, planksKey, 80);
            var largeJob = new CraftingJob(plan20, Map.of()).requestedBy(null, BUS_OUT);
            CompoundTag tag = largeJob.save(helper.getLevel().registryAccess());
            tag.putLong("Done", 16L);
            var loadedLargeJob = CraftingJob.load(tag, helper.getLevel().registryAccess());
            helper.assertTrue(loadedLargeJob != null, "loaded large job exists");
            helper.assertTrue(CryoCalculatorBlockEntity.dings(loadedLargeJob), "large job (>= 16 executions) must ding");

            helper.succeed();
        });
    }

    /** The config switch really silences the bell: a job that would ring does not. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void asmrConfigTogglesSilenceDing(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        ((PatternProviderBlockEntity) helper.getBlockEntity(PROV)).getContainer().setItem(0,
                GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));
        helper.runAfterDelay(5, () -> {
            GolemNetwork network = GolemGameTests.net(helper, bay);
            CraftingPlan plan = CraftingPlanner.plan(network, ItemKey.of(new ItemStack(Items.OAK_PLANKS)), 4);
            var job = new CraftingJob(plan, Map.of()).requestedBy(GolemGameTests.testPlayer(helper).getUUID(), null);
            helper.assertTrue(CryoCalculatorBlockEntity.dings(job), "a player's order rings by default");
            GolemConfig.TYPEWRITER_DING.set(false);
            try {
                helper.assertTrue(!CryoCalculatorBlockEntity.dings(job), "the config silences it");
            } finally {
                GolemConfig.TYPEWRITER_DING.set(true);
            }
            helper.succeed();
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void asmrRecipeMonitorBlockedJobStatus(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);

        // a processing pattern with NO machine in front: the job gets blocked
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false, List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.GOLD_INGOT)))));

        BlockPos monPos = BUS_OUT;
        helper.setBlock(monPos, ModBlocks.RECIPE_MONITOR.get().defaultBlockState());
        RecipeMonitorBlockEntity monitor = (RecipeMonitorBlockEntity) helper.getBlockEntity(monPos);

        helper.runAfterDelay(10, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.IRON_INGOT, 10), false);
            n.startCrafting(ItemKey.of(new ItemStack(Items.GOLD_INGOT)), 5);

            helper.runAfterDelay(25, () -> {
                RecipeMonitorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(monPos), helper.getBlockState(monPos), monitor);

                helper.assertTrue(monitor.fault(), "monitor reports fault for blocked job");
                helper.assertTrue(monitor.lampState() == 2, "monitor lamp glows red (state 2) on blocked job");
                helper.succeed();
            });
        });
    }

    @GameTest(template = T, timeoutTicks = 100)
    public static void asmrRecipeMonitorBlockedTickAndClientSync(GameTestHelper helper) {
        GolemGameTests.baseNetwork(helper);
        BlockPos monPos = BUS_OUT;
        helper.setBlock(monPos, ModBlocks.RECIPE_MONITOR.get().defaultBlockState());
        RecipeMonitorBlockEntity monitor = (RecipeMonitorBlockEntity) helper.getBlockEntity(monPos);

        helper.runAfterDelay(5, () -> {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Jobs", 1);
            tag.putBoolean("Online", true);
            tag.putBoolean("Fault", true);

            // 1.21.1: the update tag is read by handleUpdateTag (main: readUpdateTag)
            monitor.handleUpdateTag(tag, helper.getLevel().registryAccess());
            helper.assertTrue(monitor.fault(), "client monitor has fault = true after reading update tag");
            helper.assertTrue(monitor.lampState() == 2, "client lamp state is 2 (red)");

            helper.assertTrue(monitor.ticksBlocked(), "a blocked job ticks while the config allows it");
            GolemConfig.MONITOR_BLOCKED_TICK.set(false);
            try {
                helper.assertTrue(!monitor.ticksBlocked(), "the config silences the tick");
            } finally {
                GolemConfig.MONITOR_BLOCKED_TICK.set(true);
            }

            helper.succeed();
        });
    }

    // ------------------------------------------------------------ complex networks and edge cases

    /** Patterns where A needs B and B needs A: the planner returns without stack overflow, reports missing, and a non-cyclic craft still plans. */
    @GameTest(template = T, timeoutTicks = 100)
    public static void autocraftCyclicPatternsDoNotHang(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROV);

        // cyclic recipes: an iron ingot needs a gold ingot, a gold ingot needs an iron ingot
        provider.getContainer().setItem(0, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.getContainer().setItem(1, GolemGameTests.card(new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(new ItemStack(Items.GOLD_INGOT)))));
        // an independent recipe: 1 oak log -> 4 oak planks
        provider.getContainer().setItem(2, GolemGameTests.card(GolemGameTests.craftingPattern(new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.OAK_LOG))));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = GolemGameTests.net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 10), false);

            ItemKey iron = ItemKey.of(new ItemStack(Items.IRON_INGOT));
            ItemKey planks = ItemKey.of(new ItemStack(Items.OAK_PLANKS));

            // planning a cyclic recipe returns without a hang or overflow and reports missing
            CraftingPlan planCyclic = CraftingPlanner.plan(n, iron, 1);
            helper.assertTrue(!planCyclic.missing().isEmpty(), "cyclic recipe cannot be made, reports missing: " + planCyclic.missing());
            helper.assertTrue(n.startCrafting(iron, 1).type() != GolemNetwork.StartType.STARTED, "cyclic craft cannot start");

            // the independent recipe still plans and crafts cleanly
            CraftingPlan planOk = CraftingPlanner.plan(n, planks, 4);
            helper.assertTrue(planOk.missing().isEmpty(), "independent recipe has nothing missing: " + planOk.missing());
            helper.assertTrue(n.startCrafting(planks, 4).type() == GolemNetwork.StartType.STARTED, "independent craft starts");

            helper.succeed();
        });
    }

    /** Removing a pipe splits the network into two: storage and devices leave the first, and reconnecting rejoins them. */
    @GameTest(template = T, timeoutTicks = 200)
    public static void networkSplitWhenTrunkIsCut(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.DIAMOND, 10), false);

        BlockPos trunk1 = BUS_OUT;
        BlockPos trunk2 = trunk1.north();
        BlockPos trunk3 = trunk2.north();
        GolemGameTests.put(helper, trunk1, ModBlocks.BASIC_SNOW_PIPE.get());
        GolemGameTests.put(helper, trunk2, ModBlocks.BASIC_SNOW_PIPE.get());
        GolemGameTests.put(helper, trunk3, ModBlocks.BASIC_SNOW_PIPE.get());

        BlockPos farTerm = trunk3.east();
        GolemGameTests.put(helper, farTerm, ModBlocks.ANALOG_TERMINAL.get());

        BlockPos peri = trunk3.west();
        BlockPos chest = peri.west();
        helper.setBlock(chest, Blocks.CHEST);
        Container c = (Container) helper.getBlockEntity(chest);
        c.setItem(0, new ItemStack(Items.EMERALD, 20));
        periscope(helper, peri, Direction.WEST);

        helper.runAfterDelay(15, () -> {
            GolemNetwork initial = GolemGameTests.net(helper, bay);
            helper.assertTrue(initial.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "diamond in main storage");
            helper.assertTrue(initial.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 20, "emerald in periscope storage before cut");
            helper.assertTrue(initial.isDeviceActive(helper.absolutePos(farTerm)), "far terminal has channel before cut");

            // cut the trunk in the middle
            helper.setBlock(trunk2, Blocks.AIR);

            helper.runAfterDelay(15, () -> {
                GolemNetwork mainNet = GolemGameTests.net(helper, bay);
                GolemNetwork cutNet = GolemGameTests.net(helper, peri);
                helper.assertTrue(mainNet.hasCore(), "main network retains core");
                helper.assertTrue(!cutNet.hasCore(), "cut-off part has no core");
                helper.assertTrue(mainNet.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "main network retains diamonds");
                helper.assertTrue(mainNet.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 0, "cut-off emerald storage leaves main network");
                helper.assertTrue(!mainNet.isDeviceActive(helper.absolutePos(farTerm)), "cut-off terminal loses channel on main network");

                // put the pipe back to rejoin
                GolemGameTests.put(helper, trunk2, ModBlocks.BASIC_SNOW_PIPE.get());

                helper.runAfterDelay(15, () -> {
                    GolemNetwork rejoined = GolemGameTests.net(helper, bay);
                    helper.assertTrue(rejoined.itemCountOf(ItemKey.of(new ItemStack(Items.DIAMOND))) == 10, "diamond still present");
                    helper.assertTrue(rejoined.itemCountOf(ItemKey.of(new ItemStack(Items.EMERALD))) == 20, "emerald storage rejoined");
                    helper.assertTrue(rejoined.isDeviceActive(helper.absolutePos(farTerm)), "far terminal active again");
                    helper.succeed();
                });
            });
        });
    }
}
