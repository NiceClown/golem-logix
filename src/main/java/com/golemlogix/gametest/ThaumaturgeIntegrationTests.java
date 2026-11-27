package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.blockentity.FormationBusBlockEntity;
import com.golemlogix.blockentity.VisCondenserBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.EssentiaCauldronBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.blocks.VisCondenserBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.integration.thaumaturge.ThaumaturgeCompat;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.menu.VisCondenserMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.Recipes;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * End-to-end NeoForge GameTests for the Thaumaturge integration (Vis Condenser, Essentia Cryo-Cauldron,
 * Formation Bus fluid replacement toggle, Crucible recipe, and item aspects).
 *
 * <p>Direct references to {@code com.leclowndu93150.thaumaturge.*} are strictly isolated inside {@link ThaumApi},
 * which is only ever touched when {@link SourceHelper#thaumaturgeLoaded()} is true, so all tests run cleanly under
 * both default {@code runGameTestServer} and {@code -PnoThaumaturge runGameTestServer}.
 */
public final class ThaumaturgeIntegrationTests {
    private ThaumaturgeIntegrationTests() {
    }

    /**
     * Formation Bus fluid-replacement toggle works in BOTH modes (with and without Thaumaturge):
     * default is true, persists in NBT, toggles via BusMenu.BUTTON_FLUIDS, refuses water when false, replaces water when true.
     */
    public static void formationBusFluidToggle(GameTestHelper helper) {
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        BlockPos busPos = GolemGameTests.BUS_OUT;
        BlockPos front = busPos.north();
        helper.setBlock(busPos, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(front, Blocks.WATER);

        FormationBusBlockEntity bus = helper.getBlockEntity(busPos, FormationBusBlockEntity.class);
        helper.assertTrue(FormationBusBlockEntity.canPlaceInto(true, true, true)
                && FormationBusBlockEntity.canPlaceInto(true, true, false)
                && !FormationBusBlockEntity.canPlaceInto(true, false, true)
                && !FormationBusBlockEntity.canPlaceInto(true, false, false)
                && FormationBusBlockEntity.canPlaceInto(false, true, true)
                && !FormationBusBlockEntity.canPlaceInto(false, true, false)
                && !FormationBusBlockEntity.canPlaceInto(false, false, true)
                && !FormationBusBlockEntity.canPlaceInto(false, false, false),
                "FormationBusBlockEntity.canPlaceInto truth table matches spec");
        helper.assertTrue(bus.replaceFluids(), "Formation Bus replaceFluids defaults to true");
        helper.assertTrue(bus.menuData().get(3) == 1, "menuData index 3 reflects replaceFluids=true");

        ServerPlayer player = GolemGameTests.testPlayer(helper);
        BusMenu menu = new BusMenu(91, player.getInventory(), bus.getFilters(), bus.getUpgrades(), BusMenu.KIND_FORMATION, bus.menuData());
        helper.assertTrue(menu.replaceFluids(), "BusMenu.replaceFluids() initially true");
        helper.assertTrue(menu.clickMenuButton(player, BusMenu.BUTTON_FLUIDS), "BUTTON_FLUIDS handled");
        helper.assertTrue(!bus.replaceFluids() && !menu.replaceFluids() && bus.menuData().get(3) == 0,
                "toggled replaceFluids to false via BusMenu");

        // Verify NBT round-trip preserves replaceFluids=false
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = bus.saveWithoutMetadata(registries);
        FormationBusBlockEntity copy = new FormationBusBlockEntity(bus.getBlockPos(), bus.getBlockState());
        copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        helper.assertTrue(!copy.replaceFluids(), "replaceFluids=false survives NBT save/load");

        bus.getFilters().setItem(0, new ItemStack(Items.ICE));
        helper.runAfterDelay(5, () -> GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.ICE, 4), false));

        // With replaceFluids=false, water stays untouched
        helper.runAfterDelay(50, () -> {
            helper.assertBlockPresent(Blocks.WATER, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.ICE))) == 4,
                    "no ice spent while replaceFluids=false");
            // Toggle back to true via BusMenu
            helper.assertTrue(menu.clickMenuButton(player, BusMenu.BUTTON_FLUIDS), "toggle back to true");
            helper.assertTrue(bus.replaceFluids() && menu.replaceFluids(), "replaceFluids now true");
        });

        // With replaceFluids=true, Formation Bus replaces water with Ice
        helper.runAfterDelay(100, () -> {
            helper.assertBlockPresent(Blocks.ICE, front);
            helper.assertTrue(GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.ICE))) == 3,
                    "one ice placed into water once replaceFluids=true");
            helper.succeed();
        });
    }

    /**
     * Soft-dependency boundary & Source requirement check (runs in BOTH modes):
     * - With Thaumaturge: thaumaturgeLoaded() && sourceRequired(), cauldron is IEssentiaStreamPort subclass, conditional recipes present.
     * - Without Thaumaturge: machines tick safely into STATUS_NO_THAUMATURGE, compat returns empty/0, conditional recipes absent.
     */
    public static void softDepAndSourceRequired(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        BlockPos condPos = new BlockPos(3, 1, 3);
        BlockPos caulPos = new BlockPos(6, 1, 3);
        VisCondenserBlock.place(level, helper.absolutePos(condPos), Direction.SOUTH, ModBlocks.VIS_CONDENSER.get());
        EssentiaCauldronBlock.place(level, helper.absolutePos(caulPos), Direction.SOUTH, ModBlocks.ESSENTIA_CAULDRON.get());

        boolean loaded = SourceHelper.thaumaturgeLoaded();
        helper.assertTrue(loaded == ThaumaturgeCompat.isLoaded(), "SourceHelper.thaumaturgeLoaded matches ThaumaturgeCompat.isLoaded");

        boolean hasCondRecipe = server.getRecipeManager().byKey(Recipes.key(id("vis_condenser"))).isPresent();
        boolean hasHeadRecipe = server.getRecipeManager().byKey(Recipes.key(id("hollow_golem_head"))).isPresent();
        boolean hasCaulRecipe = server.getRecipeManager().byKey(Recipes.key(id("essentia_cryo_cauldron"))).isPresent();

        if (!loaded) {
            helper.assertTrue(ModBlocks.ESSENTIA_CAULDRON.get().getClass() == EssentiaCauldronBlock.class,
                    "without Thaumaturge, cauldron uses plain EssentiaCauldronBlock");
            helper.assertTrue(!hasCondRecipe && !hasHeadRecipe && !hasCaulRecipe,
                    "conditional Thaumaturge recipes must be absent without Thaumaturge");
            helper.assertTrue(ThaumaturgeCompat.readAura(level, helper.absolutePos(condPos)).base() == 0,
                    "readAura fallback is empty");
            helper.assertTrue(ThaumaturgeCompat.drainVis(level, helper.absolutePos(condPos), 1f, false) == 0f,
                    "drainVis fallback returns 0");
            helper.assertTrue(ThaumaturgeCompat.pullEssentia(level, helper.absolutePos(caulPos), Direction.WEST, true, 8) == 0,
                    "pullEssentia fallback returns 0");
            helper.runAfterDelay(15, () -> {
                VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
                EssentiaCauldronBlockEntity ec = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
                helper.assertTrue(vc.status() == VisCondenserBlockEntity.STATUS_NO_THAUMATURGE,
                        "Vis Condenser enters STATUS_NO_THAUMATURGE, got " + vc.status());
                helper.assertTrue(ec.status() == EssentiaCauldronBlockEntity.STATUS_NO_THAUMATURGE,
                        "Cauldron enters STATUS_NO_THAUMATURGE, got " + ec.status());
                helper.succeed();
            });
            return;
        }

        helper.assertTrue(SourceHelper.sourceRequired() == GolemConfig.REQUIRE_SOURCE.get(),
                "with Thaumaturge loaded, sourceRequired() must be true");
        helper.assertTrue(ModBlocks.ESSENTIA_CAULDRON.get().getClass() != EssentiaCauldronBlock.class,
                "with Thaumaturge loaded, cauldron uses IEssentiaStreamPort subclass");
        helper.assertTrue(hasCondRecipe && hasHeadRecipe && hasCaulRecipe,
                "all 3 conditional Thaumaturge recipes loaded: cond=" + hasCondRecipe + " head=" + hasHeadRecipe + " caul=" + hasCaulRecipe);
        helper.succeed();
    }

    /**
     * Vis Condenser end-to-end:
     * - Drains real chunk Vis, fills adjacent Cryo-Source Jar AND remote network Giga Jar.
     * - Halts cleanly at % floor with 0 Flux, respects +5% hysteresis, unpauses immediately when threshold lowered.
     * - Halts cleanly at exact Vis floor, pauses on Redstone.
     * - Emits real chunk Flux when forced to drain below 10% base Vis.
     */
    public static void visCondenserDrainJarsFloorsAndFlux(GameTestHelper helper) {
        if (!SourceHelper.thaumaturgeLoaded()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        GolemGameTests.buildArray(helper, 0, 0);
        // Pipe from corner bushing (4,1,6) west to (1,1,6); Giga Jar at (1,1,7)..(2,1,8) touching pipe
        for (int x = 3; x >= 1; x--) {
            helper.setBlock(new BlockPos(x, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        BlockPos gigaCorner = new BlockPos(1, 1, 7);
        placeJarSquare(helper, gigaCorner, 2);

        // Vis Condenser at (0,1,6) touching pipe at (1,1,6), plus adjacent single Cryo-Source Jar at (0,1,5)
        BlockPos condPos = new BlockPos(0, 1, 6);
        BlockPos adjJarPos = new BlockPos(0, 1, 5);
        BlockPos redstonePos = new BlockPos(0, 1, 7);
        VisCondenserBlock.place(level, helper.absolutePos(condPos), Direction.NORTH, ModBlocks.VIS_CONDENSER.get());
        CryoJarBlock.place(level, helper.absolutePos(adjJarPos), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());

        BlockPos absCond = helper.absolutePos(condPos);
        int[] baseHolder = {0};
        float[] startVis = {0f};
        int[] sourceAfterFloorPause = {0};
        int[] sourceBeforeRedstone = {0};

        helper.runAfterDelay(5, () -> {
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(giga.form() == JarForm.GIGA, "network Giga Jar formed");
            CryoJarBlockEntity adj = helper.getBlockEntity(adjJarPos, CryoJarBlockEntity.class);
            // Leave only 10 room in adjacent jar so excess Source overflows into the remote network Giga Jar!
            adj.fill(adj.capacity() - 10, false);

            int base = ThaumApi.getBase(level, absCond);
            helper.assertTrue(base >= 20, "chunk aura base positive, got " + base);
            baseHolder[0] = base;

            // Phase 1: Drain from full base Vis into BOTH adjacent jar and network Giga Jar with 0 Flux
            // Equalize cardinal neighbor chunks too so external Aura diffusion does not mask the real Vis drain.
            ThaumApi.clearFlux(level, absCond);
            float highVis = base;
            ThaumApi.setVisArea(level, absCond, highVis);
            startVis[0] = ThaumApi.getVis(level, absCond);
        });

        helper.runAfterDelay(35, () -> {
            CryoJarBlockEntity adj = helper.getBlockEntity(adjJarPos, CryoJarBlockEntity.class);
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            float curVis = ThaumApi.getVis(level, absCond);
            helper.assertTrue(curVis < startVis[0] - 0.5f,
                    "chunk Vis decreased from " + startVis[0] + " to " + curVis);
            helper.assertTrue(adj.available() == adj.capacity(), "adjacent Cryo-Source Jar filled to capacity");
            helper.assertTrue(giga.available() > 0,
                    "remote network Giga Jar received overflow Source, got " + giga.available());
            helper.assertTrue(ThaumApi.getFlux(level, absCond) <= 0.001f,
                    "safe drain above 10% adds 0 Flux, got " + ThaumApi.getFlux(level, absCond));
            helper.assertTrue(vc.status() == VisCondenserBlockEntity.STATUS_WORKING && !vc.lowVisWarning(),
                    "condenser working cleanly above 10%");

            // Phase 2: Percent floor (40%) + hysteresis (+5% -> 45%)
            vc.setMode(VisCondenserBlockEntity.MODE_PERCENT);
            vc.setThreshold(40);
            ThaumApi.setVisArea(level, absCond, baseHolder[0] * 0.405f);
        });

        helper.runAfterDelay(65, () -> {
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(vc.pausedByFloor() && vc.status() == VisCondenserBlockEntity.STATUS_FLOOR_PAUSED,
                    "condenser paused at 40% floor, paused=" + vc.pausedByFloor() + " status=" + vc.status());
            sourceAfterFloorPause[0] = giga.available();
            // Raise Vis to 42% (above 40% floor, below 45% hysteresis resume) -> must STAY paused!
            ThaumApi.setVisArea(level, absCond, baseHolder[0] * 0.42f);
        });

        helper.runAfterDelay(90, () -> {
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(vc.pausedByFloor(), "hysteresis holds pause at 42% (< 45% resume)");
            helper.assertTrue(giga.available() <= sourceAfterFloorPause[0],
                    "no Source generated while held by hysteresis: " + giga.available() + " vs " + sourceAfterFloorPause[0]);

            // Now raise Vis to 47% (>= 45% resume) -> must resume!
            ThaumApi.setVisArea(level, absCond, baseHolder[0] * 0.47f);
            sourceAfterFloorPause[0] = giga.available();
        });

        helper.runAfterDelay(115, () -> {
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(!vc.pausedByFloor() && giga.available() > sourceAfterFloorPause[0],
                    "resumed once Vis rose past floor + hysteresis");

            // Phase 3: Exact Vis floor + immediate unpause via VisCondenserMenu buttons
            ServerPlayer player = GolemGameTests.testPlayer(helper);
            VisCondenserMenu menu = new VisCondenserMenu(92, player.getInventory(), vc.menuData(),
                    ContainerLevelAccess.create(level, absCond));
            helper.assertTrue(menu.clickMenuButton(player, VisCondenserMenu.BUTTON_MODE), "switch to exact mode");
            helper.assertTrue(vc.mode() == VisCondenserBlockEntity.MODE_EXACT, "now in MODE_EXACT");
            int exactFloor = Math.max(20, Math.round(baseHolder[0] * 0.50f));
            vc.setThreshold(exactFloor);
            ThaumApi.setVisArea(level, absCond, exactFloor - 2.0f);
        });

        helper.runAfterDelay(135, () -> {
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            helper.assertTrue(vc.pausedByFloor(), "paused below exact Vis floor");
            // Lower threshold by 10 via VisCondenserMenu -> must IMMEDIATELY clear pausedByFloor
            ServerPlayer player = GolemGameTests.testPlayer(helper);
            VisCondenserMenu menu = new VisCondenserMenu(93, player.getInventory(), vc.menuData(),
                    ContainerLevelAccess.create(level, absCond));
            helper.assertTrue(menu.clickMenuButton(player, VisCondenserMenu.BUTTON_MINUS_10), "lower exact threshold by 10");
            helper.assertTrue(!vc.pausedByFloor(), "lowering threshold below current Vis clears pause immediately");

            // Phase 4: Redstone pause
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            sourceBeforeRedstone[0] = giga.available();
            helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);
        });

        helper.runAfterDelay(160, () -> {
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(vc.status() == VisCondenserBlockEntity.STATUS_REDSTONE && vc.redstonePowered(),
                    "redstone pauses Vis Condenser, status=" + vc.status());
            helper.assertTrue(giga.available() <= sourceBeforeRedstone[0],
                    "no Source generated while redstone-paused");
            helper.setBlock(redstonePos, Blocks.AIR);

            // Phase 5: Force drain below 10% base Vis -> real chunk Flux increases!
            vc.setMode(VisCondenserBlockEntity.MODE_PERCENT);
            vc.setThreshold(0);
            ThaumApi.clearFlux(level, absCond);
            ThaumApi.setVisArea(level, absCond, Math.max(2.0f, baseHolder[0] * 0.08f));
        });

        helper.runAfterDelay(195, () -> {
            VisCondenserBlockEntity vc = helper.getBlockEntity(condPos, VisCondenserBlockEntity.class);
            float flux = ThaumApi.getFlux(level, absCond);
            helper.assertTrue(flux > 0.001f, "draining below 10% Vis must pollute chunk with Flux, got " + flux);
            helper.assertTrue(vc.lowVisWarning() && vc.status() == VisCondenserBlockEntity.STATUS_LOW_VIS_WORKING,
                    "lowVisWarning and STATUS_LOW_VIS_WORKING active, warn=" + vc.lowVisWarning() + " status=" + vc.status());
            helper.succeed();
        });
    }

    /**
     * Essentia Cryo-Cauldron sided Essentia plumbing & Phial right-click:
     * - Left side (facing.getClockWise()) pulls ONLY Auram from Thaumaturge jar+tube; right side (facing.getCounterClockWise()) pulls ONLY Gelum.
     * - Swapped aspects are strictly rejected via tubes AND direct transport/storage capabilities.
     * - Right-clicking with filled Auram / Gelum phials adds matching aspect and returns an empty phial; wrong aspect phial rejected.
     */
    public static void cauldronSidedPlumbingAndPhials(GameTestHelper helper) {
        if (!SourceHelper.thaumaturgeLoaded()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        Direction facing = Direction.SOUTH;
        Direction left = EssentiaCauldronBlock.primarySide(facing);
        Direction right = EssentiaCauldronBlock.coolantSide(facing);
        helper.assertTrue(left == Direction.WEST && right == Direction.EAST,
                "for SOUTH-facing cauldron, viewer's left=WEST (Auram), right=EAST (Gelum)");

        BlockPos caulPos = new BlockPos(5, 2, 5);
        EssentiaCauldronBlock.place(level, helper.absolutePos(caulPos), facing, ModBlocks.ESSENTIA_CAULDRON.get());
        BlockPos absCaul = helper.absolutePos(caulPos);

        // Step 1: Verify direct capability side rejection + place SWAPPED jars+tubes (Gelum on left, Auram on right)
        ThaumApi.assertSidedCapabilities(helper, level, absCaul, left, right);

        BlockPos leftTube = caulPos.relative(left);
        BlockPos rightTube = caulPos.relative(right);
        ThaumApi.placeAndFillJar(level, helper.absolutePos(leftTube.below()), "thaumaturge:gelum", 32);
        ThaumApi.placeTube(level, helper.absolutePos(leftTube));
        ThaumApi.placeAndFillJar(level, helper.absolutePos(rightTube.below()), "thaumaturge:auram", 32);
        ThaumApi.placeTube(level, helper.absolutePos(rightTube));

        // After 30 ticks with swapped jars/tubes, cauldron MUST remain completely empty
        helper.runAfterDelay(30, () -> {
            EssentiaCauldronBlockEntity be = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
            helper.assertTrue(be.auram() == 0 && be.gelum() == 0,
                    "swapped aspects must be rejected: auram=" + be.auram() + ", gelum=" + be.gelum());

            // Step 2: Swap to CORRECT jars+tubes (Auram on left, Gelum on right)
            helper.setBlock(leftTube, Blocks.AIR);
            helper.setBlock(rightTube, Blocks.AIR);
            helper.setBlock(leftTube.below(), Blocks.AIR);
            helper.setBlock(rightTube.below(), Blocks.AIR);
            ThaumApi.placeAndFillJar(level, helper.absolutePos(leftTube.below()), "thaumaturge:auram", 32);
            ThaumApi.placeTube(level, helper.absolutePos(leftTube));
            ThaumApi.placeAndFillJar(level, helper.absolutePos(rightTube.below()), "thaumaturge:gelum", 32);
            ThaumApi.placeTube(level, helper.absolutePos(rightTube));
        });

        // After 60 more ticks, cauldron must have pulled Auram from left jar and Gelum from right jar
        helper.runAfterDelay(90, () -> {
            EssentiaCauldronBlockEntity be = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
            helper.assertTrue(be.auram() > 0, "left tube pulled Auram into cauldron, got " + be.auram());
            helper.assertTrue(be.gelum() > 0, "right tube pulled Gelum into cauldron, got " + be.gelum());
            int leftJarLeft = ThaumApi.jarAmount(level, helper.absolutePos(leftTube.below()), "thaumaturge:auram");
            int rightJarLeft = ThaumApi.jarAmount(level, helper.absolutePos(rightTube.below()), "thaumaturge:gelum");
            helper.assertTrue(leftJarLeft < 32, "left Auram jar drained, remaining=" + leftJarLeft);
            helper.assertTrue(rightJarLeft < 32, "right Gelum jar drained, remaining=" + rightJarLeft);

            // Disconnect tubes so tank levels stay fixed during Phial right-click checks
            helper.setBlock(leftTube, Blocks.AIR);
            helper.setBlock(rightTube, Blocks.AIR);

            // Step 3: Phial right-click checks (survival FakePlayer)
            ServerPlayer player = GolemGameTests.testPlayer(helper);
            player.getAbilities().instabuild = false;
            BlockState caulState = level.getBlockState(absCaul);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absCaul), Direction.SOUTH, absCaul, false);

            // Reset tanks to small values so phials have room
            be.restoreTanksRaw(0, 0);

            ItemStack auramPhial = ThaumApi.makePhial(level, "thaumaturge:auram");
            player.setItemInHand(InteractionHand.MAIN_HAND, auramPhial);
            InteractionResult r1 = caulState.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(r1.consumesAction(), "Auram phial right-click succeeded");
            helper.assertTrue(be.auram() == ThaumApi.phialAmount(),
                    "Auram phial added " + ThaumApi.phialAmount() + " Auram, got " + be.auram());
            helper.assertTrue(ThaumApi.isEmptyPhial(player.getMainHandItem()),
                    "Auram phial returned empty phial, got " + player.getMainHandItem());

            ItemStack gelumPhial = ThaumApi.makePhial(level, "thaumaturge:gelum");
            player.setItemInHand(InteractionHand.MAIN_HAND, gelumPhial);
            InteractionResult r2 = caulState.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(r2.consumesAction(), "Gelum phial right-click succeeded");
            helper.assertTrue(be.gelum() == ThaumApi.phialAmount(),
                    "Gelum phial added " + ThaumApi.phialAmount() + " Gelum, got " + be.gelum());
            helper.assertTrue(ThaumApi.isEmptyPhial(player.getMainHandItem()),
                    "Gelum phial returned empty phial, got " + player.getMainHandItem());

            // Non-matching phial (Ignis) must be rejected and NOT emptied
            int auramBefore = be.auram(), gelumBefore = be.gelum();
            ItemStack ignisPhial = ThaumApi.makePhial(level, "thaumaturge:ignis");
            player.setItemInHand(InteractionHand.MAIN_HAND, ignisPhial);
            caulState.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(be.auram() == auramBefore && be.gelum() == gelumBefore,
                    "non-matching Ignis phial must not fill cauldron");
            helper.assertTrue(!ThaumApi.isEmptyPhial(player.getMainHandItem()),
                    "non-matching Ignis phial must not be emptied");

            helper.succeed();
        });
    }

    /**
     * Essentia Cryo-Cauldron balanced brewing, 0 Flux, Co-Processor speed scaling, and full Ice melting progression:
     * Blue Ice (9000) -> Packed Ice (3000) -> Ice (1000) -> Water in real world ticks!
     */
    public static void cauldronBrewingIceMeltingAndCoprocessors(GameTestHelper helper) {
        if (!SourceHelper.thaumaturgeLoaded()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        BlockPos icePos = new BlockPos(4, 1, 4);
        BlockPos caulPos = icePos.above();
        // Place a 2x2 Giga Jar beside the cauldron so it holds 44,000 Source (> 13,000 needed to melt Blue -> Water!)
        BlockPos gigaCorner = new BlockPos(5, 2, 4);

        helper.setBlock(icePos, Blocks.BLUE_ICE);
        EssentiaCauldronBlock.place(level, helper.absolutePos(caulPos), Direction.SOUTH, ModBlocks.ESSENTIA_CAULDRON.get());
        placeJarSquare(helper, gigaCorner, 2);

        BlockPos absCaul = helper.absolutePos(caulPos);
        ThaumApi.clearFlux(level, absCaul);

        EssentiaCauldronBlockEntity be = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
        helper.assertTrue(Math.abs(be.reactionsPerSecond() - 0.25) < 1e-6 && Math.abs(be.sourcePerSecond() - 40.0) < 1e-6,
                "0 co-processors -> 0.25 react/s, 40 Source/s");

        // Phase 1: Measure 0-coprocessor brewing over 80 ticks (1 reaction = 160 Source)
        be.addPrimary(64, false);
        be.addCoolant(64, false);

        int[] sourceAt85 = {0};
        boolean[] sawPacked = {false};
        boolean[] sawNormal = {false};
        boolean[] sawWater = {false};
        boolean[] phase2Active = {false};

        helper.runAfterDelay(85, () -> {
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(giga.form() == JarForm.GIGA, "adjacent Giga Jar formed");
            helper.assertTrue(giga.available() >= 160 && giga.available() <= 320,
                    "0 co-processors produced ~160 Source in 4s, got " + giga.available());
            helper.assertTrue(ThaumApi.getFlux(level, absCaul) <= 0.001f,
                    "balanced brewing emits 0 Flux");
            sourceAt85[0] = giga.available();

            // Install 28 Compressed Co-Processors -> 3.75 reactions/s = 600 Source/s (15x faster!)
            be.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 28));
            helper.assertTrue(Math.abs(be.reactionsPerSecond() - 3.75) < 1e-6
                            && Math.abs(be.sourcePerSecond() - 600.0) < 1e-6,
                    "28 co-processors -> 3.75 react/s = 600 Source/s, got " + be.sourcePerSecond());
            phase2Active[0] = true;
        });

        // Keep cauldron stocked with Auram + Gelum during Phase 2 so it melts Blue -> Packed -> Ice -> Water cleanly
        helper.onEachTick(() -> {
            if (!phase2Active[0]) {
                return;
            }
            if (helper.getBlockState(icePos).is(Blocks.PACKED_ICE)) {
                sawPacked[0] = true;
            } else if (helper.getBlockState(icePos).is(Blocks.ICE)) {
                sawNormal[0] = true;
            } else if (helper.getBlockState(icePos).is(Blocks.WATER)) {
                sawWater[0] = true;
            }
            if (!sawWater[0]) {
                be.addPrimary(64, false);
                be.addCoolant(64, false);
            } else {
                // Drain remaining Auram so post-melt waiting doesn't boil off before assertion
                be.restoreTanksRaw(0, 0);
            }
        });

        // Verify fast Co-Processor burst after 21 ticks of 28x speed (3.75 react/s * 160 = ~640 Source over 21 ticks)
        helper.runAfterDelay(106, () -> {
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            int delta = giga.available() - sourceAt85[0];
            helper.assertTrue(delta >= 480, "28 co-processors produced >= 480 Source in ~1s burst (vs 40 base/s), got " + delta);
        });

        // By tick 570 (~485 ticks at 600 Source/s = 14,550 Source > 13,000 total Blue+Packed+Ice charge), Ice must have melted all the way to Water!
        helper.runAfterDelay(570, () -> {
            CryoJarBlockEntity giga = helper.getBlockEntity(gigaCorner, CryoJarBlockEntity.class);
            helper.assertTrue(sawPacked[0], "observed Blue Ice -> Packed Ice transition");
            helper.assertTrue(sawNormal[0], "observed Packed Ice -> Ice transition");
            helper.assertTrue(sawWater[0], "observed Ice -> Water melt transition");
            helper.assertBlockPresent(Blocks.WATER, icePos);
            helper.assertTrue(giga.available() >= 13000,
                    "total Source generated across Blue+Packed+Ice must be >= 13000, got " + giga.available());
            helper.assertTrue(ThaumApi.getFlux(level, absCaul) <= 0.001f,
                    "zero Flux emitted during full balanced Blue->Packed->Ice->Water brewing, got " + ThaumApi.getFlux(level, absCaul));
            helper.succeed();
        });
    }

    /**
     * Full Formation Bus Automation Test:
     * - Phase A (replaceFluids = true): Formation Bus facing the Ice cell below an active Cauldron automatically replaces
     *   melted Water with fresh Ice from the network, keeping the Cauldron running seamlessly with 0 Flux!
     * - Phase B (replaceFluids = false): Once Ice melts to Water again, Formation Bus leaves Water untouched!
     */
    public static void cauldronFormationBusAutomation(GameTestHelper helper) {
        if (!SourceHelper.thaumaturgeLoaded()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        BlockPos bay = GolemGameTests.baseNetwork(helper);
        // Formation Bus at BUS_OUT (6,1,3) facing NORTH -> target is (6,1,2)
        BlockPos busPos = GolemGameTests.BUS_OUT;
        BlockPos icePos = busPos.north();
        BlockPos caulPos = icePos.above();
        BlockPos jarPos = caulPos.east();

        helper.setBlock(busPos, ModBlocks.FORMATION_BUS.get().defaultBlockState().setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(icePos, Blocks.ICE);
        EssentiaCauldronBlock.place(level, helper.absolutePos(caulPos), Direction.SOUTH, ModBlocks.ESSENTIA_CAULDRON.get());
        CryoJarBlock.place(level, helper.absolutePos(jarPos), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());

        FormationBusBlockEntity bus = helper.getBlockEntity(busPos, FormationBusBlockEntity.class);
        bus.setReplaceFluids(true);
        bus.getFilters().setItem(0, new ItemStack(Items.ICE));
        // Add speed upgrades to Formation Bus so replacement happens briskly within the 40-tick grace window
        bus.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
        bus.getUpgrades().setItem(1, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));

        EssentiaCauldronBlockEntity caul = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
        caul.getUpgrades().setItem(0, new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get(), 28));

        BlockPos absCaul = helper.absolutePos(caulPos);
        ThaumApi.clearFlux(level, absCaul);

        boolean[] keepFeeding = {true};
        long[] iceAfterPhaseA = {0};

        helper.runAfterDelay(5, () -> GolemGameTests.net(helper, bay).insertItem(new ItemStack(Items.ICE, 10), false));

        helper.onEachTick(() -> {
            if (keepFeeding[0]) {
                caul.addPrimary(64, false);
                caul.addCoolant(64, false);
            }
        });

        // 1000 Source / (600 Source/s) = 34 ticks per Ice block.
        // After 105 ticks: > 2 Ice blocks melted and automatically replaced, Source > 2000, Flux == 0!
        helper.runAfterDelay(105, () -> {
            CryoJarBlockEntity jar = helper.getBlockEntity(jarPos, CryoJarBlockEntity.class);
            long iceLeft = GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.ICE)));
            helper.assertTrue(iceLeft < 10,
                    "Formation Bus (replaceFluids=true) consumed Ice from network to replace melted Water, left=" + iceLeft);
            helper.assertTrue(jar.available() >= 1500,
                    "cauldron kept brewing seamlessly past first 1000-Source Ice block, got " + jar.available());
            helper.assertTrue(ThaumApi.getFlux(level, absCaul) <= 0.001f,
                    "zero Flux during automated Ice replacement, got " + ThaumApi.getFlux(level, absCaul));

            // Phase B: disable replaceFluids -> once current Ice melts to Water, bus MUST leave Water untouched!
            bus.setReplaceFluids(false);
            iceAfterPhaseA[0] = iceLeft;
        });

        // Wait 60 ticks so the current Ice melts to Water (and wait past bus interval to confirm Water stays)
        helper.runAfterDelay(165, () -> {
            keepFeeding[0] = false;
            caul.restoreTanksRaw(0, 0);
            helper.assertBlockPresent(Blocks.WATER, icePos);
            long iceNow = GolemGameTests.net(helper, bay).itemCountOf(ItemKey.of(new ItemStack(Items.ICE)));
            helper.assertTrue(iceNow == iceAfterPhaseA[0],
                    "with replaceFluids=false, Formation Bus must NOT replace Water (ice " + iceAfterPhaseA[0] + " -> " + iceNow + ")");
            helper.succeed();
        });
    }

    /**
     * Essentia Cryo-Cauldron Flux boil-off & Redstone pause:
     * - Uncooled (Water/no Ice) with Auram > 0: zero Flux during 40-tick grace, then boils Auram into real chunk Flux.
     * - Gelum-starved (Ice present, Gelum < 2) with Auram > 0: boils Auram into Flux after grace.
     * - Redstone signal stops boil-off cleanly with 0 extra Flux and unchanged Auram.
     */
    public static void cauldronBoilOffGraceAndRedstone(GameTestHelper helper) {
        if (!SourceHelper.thaumaturgeLoaded()) {
            helper.succeed();
            return;
        }
        var level = helper.getLevel();
        BlockPos belowPos = new BlockPos(4, 1, 4);
        BlockPos caulPos = belowPos.above();
        BlockPos redstonePos = caulPos.east();

        helper.setBlock(belowPos, Blocks.WATER);
        EssentiaCauldronBlock.place(level, helper.absolutePos(caulPos), Direction.SOUTH, ModBlocks.ESSENTIA_CAULDRON.get());
        BlockPos absCaul = helper.absolutePos(caulPos);
        ThaumApi.clearFlux(level, absCaul);

        EssentiaCauldronBlockEntity be = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
        be.addPrimary(20, false);

        float[] fluxAfterUncooled = {0f};
        float[] fluxBeforeRedstone = {0f};
        int[] auramBeforeRedstone = {0};

        // Tick 30: still inside 40-tick grace window -> 0 Flux, auram untouched, boilOff=false
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!be.boilOff() && be.auram() == 20,
                    "during 40-tick grace: boilOff=" + be.boilOff() + " auram=" + be.auram());
            helper.assertTrue(ThaumApi.getFlux(level, absCaul) <= 0.001f,
                    "0 Flux during 40-tick grace, got " + ThaumApi.getFlux(level, absCaul));
        });

        // Tick 50: past 40-tick grace -> boil-off triggered, Auram drained, chunk Flux increased!
        helper.runAfterDelay(50, () -> {
            float flux = ThaumApi.getFlux(level, absCaul);
            helper.assertTrue(be.boilOff() && be.status() == EssentiaCauldronBlockEntity.STATUS_BOIL_OFF,
                    "uncooled cauldron enters STATUS_BOIL_OFF after grace, status=" + be.status());
            helper.assertTrue(be.auram() < 20, "Auram drained during boil-off, got " + be.auram());
            helper.assertTrue(flux > 0.1f, "chunk Flux increased during uncooled boil-off, got " + flux);

            // Reset & test Gelum-starved case (Ice present, Gelum = 1 < 2, Auram = 15)
            helper.setBlock(belowPos, Blocks.ICE);
            be.restoreTanksRaw(15, 1);
            ThaumApi.clearFlux(level, absCaul);
            fluxAfterUncooled[0] = ThaumApi.getFlux(level, absCaul);
        });

        // Wait 55 ticks for Gelum-starved boil-off
        helper.runAfterDelay(105, () -> {
            float flux = ThaumApi.getFlux(level, absCaul);
            helper.assertTrue(be.boilOff() && be.auram() < 15,
                    "Gelum-starved cauldron boils off Auram, boilOff=" + be.boilOff() + " auram=" + be.auram());
            helper.assertTrue(flux > fluxAfterUncooled[0] + 0.1f,
                    "Gelum-starved boil-off increases chunk Flux, got " + flux);

            // Apply Redstone -> must halt boil-off immediately with 0 additional Flux
            helper.setBlock(redstonePos, Blocks.REDSTONE_BLOCK);
        });

        helper.runAfterDelay(110, () -> {
            helper.assertTrue(be.status() == EssentiaCauldronBlockEntity.STATUS_REDSTONE && !be.boilOff(),
                    "Redstone immediately stops boil-off, status=" + be.status() + " boilOff=" + be.boilOff());
            fluxBeforeRedstone[0] = ThaumApi.getFlux(level, absCaul);
            auramBeforeRedstone[0] = be.auram();
        });

        helper.runAfterDelay(170, () -> {
            helper.assertTrue(be.status() == EssentiaCauldronBlockEntity.STATUS_REDSTONE && !be.boilOff(),
                    "remains cleanly redstone-paused");
            helper.assertTrue(be.auram() == auramBeforeRedstone[0],
                    "Auram unchanged under redstone pause: " + be.auram() + " vs " + auramBeforeRedstone[0]);
            helper.assertTrue(ThaumApi.getFlux(level, absCaul) <= fluxBeforeRedstone[0] + 0.001f,
                    "0 additional Flux under redstone pause");
            helper.succeed();
        });
    }

    /**
     * Crucible recipe & item aspect index check:
     * - golem_logix:hollow_golem_head Crucible recipe loaded with no research gate, matches flattened_iron_core + Vacuos(20)+Gelum(20)+Auram(15).
     * - Thaumaturge AspectIndexAccess resolves non-empty aspects for key Golem Logix items with no Auram smelting exploit.
     */
    public static void crucibleRecipeAndItemAspects(GameTestHelper helper) {
        if (!SourceHelper.thaumaturgeLoaded()) {
            helper.succeed();
            return;
        }
        ThaumApi.verifyCrucibleRecipe(helper);
        ThaumApi.verifyItemAspects(helper);
        helper.succeed();
    }

    private static void placeJarSquare(GameTestHelper helper, BlockPos corner, int width) {
        var level = helper.getLevel();
        Block jar = ModBlocks.CRYO_SOURCE_JAR.get();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                CryoJarBlock.place(level, helper.absolutePos(corner.offset(x, 0, z)), Direction.SOUTH, jar);
            }
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }

    /**
     * Isolated Thaumaturge API calls. Loaded ONLY when {@link SourceHelper#thaumaturgeLoaded()} is true.
     */
    private static final class ThaumApi {
        private ThaumApi() {
        }

        static int getBase(Level level, BlockPos pos) {
            return com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getAuraBase(level, pos);
        }

        static float getVis(Level level, BlockPos pos) {
            return com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(level, pos);
        }

        static void setVis(Level level, BlockPos pos, float target) {
            float cur = com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getVis(level, pos);
            if (cur > target) {
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainVis(level, pos, cur - target, false);
            } else if (cur < target) {
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.addVis(level, pos, target - cur);
            }
        }

        static void setVisArea(Level level, BlockPos center, float target) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    setVis(level, center.offset(dx * 16, 0, dz * 16), target);
                }
            }
        }

        static float getFlux(Level level, BlockPos pos) {
            return com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getFlux(level, pos);
        }

        static void clearFlux(Level level, BlockPos pos) {
            float cur = com.leclowndu93150.thaumaturge.api.aura.AuraHelper.getFlux(level, pos);
            if (cur > 0f) {
                com.leclowndu93150.thaumaturge.api.aura.AuraHelper.drainFlux(level, pos, cur, false);
            }
        }

        private static net.minecraft.core.Holder<com.leclowndu93150.thaumaturge.api.aspect.IAspect> resolveAspect(Level level, String id) {
            var reg = level.registryAccess().lookupOrThrow(com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY);
            var key = net.minecraft.resources.ResourceKey.create(
                    com.leclowndu93150.thaumaturge.api.aspect.IAspect.REGISTRY_KEY, Identifier.parse(id));
            return reg.getOrThrow(key);
        }

        static void placeAndFillJar(Level level, BlockPos absPos, String aspectId, int amount) {
            Block jarBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse("thaumaturge:jar_normal"));
            level.setBlock(absPos, jarBlock.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(absPos) instanceof com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar jar) {
                jar.fill(resolveAspect(level, aspectId), amount);
            }
        }

        static void placeTube(Level level, BlockPos absPos) {
            Block tubeBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse("thaumaturge:tube"));
            level.setBlock(absPos, tubeBlock.defaultBlockState(), Block.UPDATE_ALL);
        }

        static int jarAmount(Level level, BlockPos absPos, String aspectId) {
            if (level.getBlockEntity(absPos) instanceof com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar jar) {
                return jar.amountOf(resolveAspect(level, aspectId));
            }
            return -1;
        }

        static ItemStack makePhial(Level level, String aspectId) {
            return com.leclowndu93150.thaumaturge.content.item.PhialItem.makeFilled(resolveAspect(level, aspectId));
        }

        static int phialAmount() {
            return com.leclowndu93150.thaumaturge.content.item.PhialItem.BASE_AMOUNT;
        }

        static boolean isEmptyPhial(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            var storage = stack.getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.ITEM_STORAGE);
            return storage != null && storage.contents().isEmpty();
        }

        static void assertSidedCapabilities(GameTestHelper helper, Level level, BlockPos absCaul, Direction left, Direction right) {
            var auram = resolveAspect(level, "thaumaturge:auram");
            var gelum = resolveAspect(level, "thaumaturge:gelum");

            var leftTrans = level.getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.TRANSPORT, absCaul, left);
            var rightTrans = level.getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.TRANSPORT, absCaul, right);
            helper.assertTrue(leftTrans != null && rightTrans != null, "transport caps present on left & right");
            helper.assertTrue(leftTrans.getSuctionAmount(left) == 128 && auram.equals(leftTrans.getSuctionType(left)),
                    "left face sucks Auram at 128");
            helper.assertTrue(rightTrans.getSuctionAmount(right) == 128 && gelum.equals(rightTrans.getSuctionType(right)),
                    "right face sucks Gelum at 128");
            helper.assertTrue(leftTrans.addEssentia(gelum, 4, left, true) == 0, "left face rejects Gelum");
            helper.assertTrue(rightTrans.addEssentia(auram, 4, right, true) == 0, "right face rejects Auram");
            helper.assertTrue(leftTrans.addEssentia(auram, 4, left, true) == 4, "left face accepts Auram");
            helper.assertTrue(rightTrans.addEssentia(gelum, 4, right, true) == 4, "right face accepts Gelum");
            helper.assertTrue(!leftTrans.isConnectable(Direction.NORTH) && !leftTrans.isConnectable(Direction.SOUTH)
                    && !leftTrans.isConnectable(Direction.UP) && !leftTrans.isConnectable(Direction.DOWN),
                    "non-left/right faces are not connectable");

            var leftStore = level.getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE, absCaul, left);
            var rightStore = level.getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE, absCaul, right);
            var frontStore = level.getCapability(com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities.STORAGE, absCaul, Direction.SOUTH);
            helper.assertTrue(leftStore != null && rightStore != null && frontStore == null,
                    "storage cap only on left/right/null faces");
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                helper.assertTrue(leftStore.insert(gelum, 4, tx) == 0, "left storage rejects Gelum");
                helper.assertTrue(rightStore.insert(auram, 4, tx) == 0, "right storage rejects Auram");
                helper.assertTrue(leftStore.insert(auram, 4, tx) == 4, "left storage accepts Auram in tx");
                helper.assertTrue(rightStore.insert(gelum, 4, tx) == 4, "right storage accepts Gelum in tx");
                // Do not commit tx -> rolls back cleanly to 0!
            }
        }

        static void verifyCrucibleRecipe(GameTestHelper helper) {
            var level = helper.getLevel();
            var server = level.getServer();
            var holderOpt = server.getRecipeManager().byKey(Recipes.key(id("hollow_golem_head")));
            helper.assertTrue(holderOpt.isPresent(), "golem_logix:hollow_golem_head recipe must be loaded");
            var recipe = holderOpt.get().value();
            helper.assertTrue(recipe instanceof com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe,
                    "hollow_golem_head must be a Thaumaturge CrucibleRecipe, got " + recipe.getClass().getName());
            var crucible = (com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe) recipe;
            helper.assertTrue(crucible.researchGate().isEmpty(), "hollow_golem_head Crucible recipe must have NO research gate");

            var vacuos = resolveAspect(level, "thaumaturge:vacuos");
            var gelum = resolveAspect(level, "thaumaturge:gelum");
            var auram = resolveAspect(level, "thaumaturge:auram");

            var exactAspects = com.leclowndu93150.thaumaturge.api.aspect.AspectList.EMPTY
                    .add(vacuos, 20).add(gelum, 20).add(auram, 15);
            ItemStack catalyst = new ItemStack(ModItems.FLATTENED_IRON_CORE.get());
            var input = new com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipeInput(catalyst, exactAspects);
            helper.assertTrue(crucible.matches(input, level),
                    "Crucible recipe matches flattened_iron_core + Vacuos(20)+Gelum(20)+Auram(15)");

            ItemStack result = crucible.assemble(input);
            helper.assertTrue(result.is(ModItems.HOLLOW_GOLEM_HEAD.get()) && result.getCount() == 1,
                    "Crucible recipe produces 1x golem_logix:hollow_golem_head, got " + result);

            // Missing 1 Auram or wrong catalyst must NOT match
            var shortAuram = com.leclowndu93150.thaumaturge.api.aspect.AspectList.EMPTY
                    .add(vacuos, 20).add(gelum, 20).add(auram, 14);
            helper.assertTrue(!crucible.matches(
                    new com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipeInput(catalyst, shortAuram), level),
                    "Crucible recipe must reject Auram(14)");
            helper.assertTrue(!crucible.matches(
                    new com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipeInput(new ItemStack(Items.IRON_INGOT), exactAspects), level),
                    "Crucible recipe must reject wrong catalyst");
        }

        static void verifyItemAspects(GameTestHelper helper) {
            var level = helper.getLevel();
            var auram = resolveAspect(level, "thaumaturge:auram");
            List<Item> keyItems = List.of(
                    ModItems.FLATTENED_IRON_CORE.get(),
                    ModItems.FLATTENED_COPPER_MATRIX.get(),
                    ModItems.FLATTENED_CRYO_SUBSTRATE.get(),
                    ModBlocks.ATLAS_CORE.get().asItem(),
                    ModBlocks.CRYO_SOURCE_JAR.get().asItem(),
                    ModBlocks.VIS_CONDENSER.get().asItem(),
                    ModItems.HOLLOW_GOLEM_HEAD.get(),
                    ModBlocks.ESSENTIA_CAULDRON.get().asItem(),
                    ModItems.COPPER_DISK_1K.get(),
                    ModBlocks.FORMATION_BUS.get().asItem()
            );
            for (Item item : keyItems) {
                var aspects = com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess.of(item);
                helper.assertTrue(aspects != null && !aspects.isEmpty(),
                        "Thaumaturge aspect index must resolve non-empty aspects for " + BuiltInRegistries.ITEM.getKey(item));
            }
            // Anti-exploit check: base plates have 0 Auram; hollow_golem_head and cauldron stay strictly below 15 Auram Crucible cost
            helper.assertTrue(com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess.of(ModItems.FLATTENED_IRON_CORE.get()).amountOf(auram) == 0,
                    "flattened_iron_core has 0 Auram");
            helper.assertTrue(com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess.of(ModItems.FLATTENED_COPPER_MATRIX.get()).amountOf(auram) == 0,
                    "flattened_copper_matrix has 0 Auram");
            helper.assertTrue(com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess.of(ModItems.FLATTENED_CRYO_SUBSTRATE.get()).amountOf(auram) == 0,
                    "flattened_cryo_substrate has 0 Auram");
            helper.assertTrue(com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess.of(ModItems.HOLLOW_GOLEM_HEAD.get()).amountOf(auram) < 15,
                    "hollow_golem_head Auram < 15 Crucible cost");
            helper.assertTrue(com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess.of(ModBlocks.ESSENTIA_CAULDRON.get().asItem()).amountOf(auram) < 15,
                    "essentia_cryo_cauldron Auram < 15 Crucible cost");
        }
    }
}
