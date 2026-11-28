package com.golemlogix.gametest;

import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.blockentity.CryoDiodeBlockEntity;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.PeriscopeBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.CryoMixerBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.blocks.EssentiaCauldronBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.menu.CalculatorMenu;
import com.golemlogix.menu.CryoMixerMenu;
import com.golemlogix.menu.DistillationTowerMenu;
import com.golemlogix.menu.DriveBayMenu;
import com.golemlogix.menu.EssentiaCauldronMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.golemlogix.gametest.GolemGameTests.*;

/**
 * Targeted regression GameTests for the audit fixes (#1, #2, #5, #6, #7, #10, #11, #14, #18a, #18b).
 */
public final class AuditRegressionTests {
    private AuditRegressionTests() {
    }

    private static ServerPlayer fakePlayer(GameTestHelper helper, String name, BlockPos rel) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos abs = helper.absolutePos(rel);
        p.snapTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0f, 0f);
        return p;
    }

    private static ContainerData shortTruncated(ContainerData backing) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return (short) backing.get(index);
            }

            @Override
            public void set(int index, int value) {
                backing.set(index, value);
            }

            @Override
            public int getCount() {
                return backing.getCount();
            }
        };
    }

    /** #1, #2, #18a: multi-terminal intersection, placement/break guards (including AtlasCasing), RecipeMonitor CRAFT access, live BUILD menu revocation. */
    public static void auditSecurityTakeoverAndMenuRevocation(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        var level = helper.getLevel();
        BlockPos sec1Pos = BUS_OUT;
        BlockPos monitorPos = TERM;
        BlockPos calcPos = CPU;
        helper.setBlock(sec1Pos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(monitorPos, ModBlocks.RECIPE_MONITOR.get().defaultBlockState());
        helper.setBlock(calcPos, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());

        SecurityTerminalBlockEntity sec1 = helper.getBlockEntity(sec1Pos, SecurityTerminalBlockEntity.class);
        ServerPlayer ownerA = fakePlayer(helper, "audit-owner-a", ORIGIN);
        ServerPlayer ownerB = fakePlayer(helper, "audit-owner-b", ORIGIN);
        ServerPlayer builder = fakePlayer(helper, "audit-builder", ORIGIN);
        ServerPlayer crafter = fakePlayer(helper, "audit-crafter", ORIGIN);
        ServerPlayer stranger = fakePlayer(helper, "audit-stranger", ORIGIN);

        sec1.setOwner(ownerA);
        sec1.getCards().setItem(0, IdentityPunchCardItem.of(builder.getUUID(), "audit-builder", SecurityPermission.BUILD.mask));
        sec1.getCards().setItem(1, IdentityPunchCardItem.of(crafter.getUUID(), "audit-crafter", SecurityPermission.CRAFT.mask));

        helper.runAfterDelay(5, () -> {
            BlockPos absBay = helper.absolutePos(bay);
            BlockPos absSec1 = helper.absolutePos(sec1Pos);
            BlockPos absMon = helper.absolutePos(monitorPos);
            BlockPos absCalc = helper.absolutePos(calcPos);

            // #18a: RecipeMonitorBlock allows CRAFT and denies stranger
            BlockHitResult monHit = new BlockHitResult(Vec3.atCenterOf(absMon), Direction.UP, absMon, false);
            var craftUse = new PlayerInteractEvent.RightClickBlock(crafter, InteractionHand.MAIN_HAND, absMon, monHit);
            NeoForge.EVENT_BUS.post(craftUse);
            helper.assertTrue(!craftUse.isCanceled(), "CRAFT player may open RecipeMonitorBlock");
            var strangerUse = new PlayerInteractEvent.RightClickBlock(stranger, InteractionHand.MAIN_HAND, absMon, monHit);
            NeoForge.EVENT_BUS.post(strangerUse);
            helper.assertTrue(strangerUse.isCanceled(), "stranger without CRAFT/BUILD denied RecipeMonitorBlock");

            // #1 onBlockPlace: untrusted GolemNode rejected; non-owner BUILD rejected for SecurityTerminalBlock; owner allowed
            BlockPos placeRel = sec1Pos.north();
            BlockPos absPlace = helper.absolutePos(placeRel);
            BlockState against = level.getBlockState(absSec1);

            helper.setBlock(placeRel, ModBlocks.BASIC_SNOW_PIPE.get());
            BlockSnapshot pipeSnap = BlockSnapshot.create(level.dimension(), level, absPlace);
            var strangerPipePlace = new BlockEvent.EntityPlaceEvent(pipeSnap, against, stranger);
            NeoForge.EVENT_BUS.post(strangerPipePlace);
            helper.assertTrue(strangerPipePlace.isCanceled(), "stranger cannot attach pipe to secured network");
            var builderPipePlace = new BlockEvent.EntityPlaceEvent(pipeSnap, against, builder);
            NeoForge.EVENT_BUS.post(builderPipePlace);
            helper.assertTrue(!builderPipePlace.isCanceled(), "builder may attach pipe to secured network");

            helper.setBlock(placeRel, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            BlockSnapshot secSnap = BlockSnapshot.create(level.dimension(), level, absPlace);
            var builderSecPlace = new BlockEvent.EntityPlaceEvent(secSnap, against, builder);
            NeoForge.EVENT_BUS.post(builderSecPlace);
            helper.assertTrue(builderSecPlace.isCanceled(), "non-owner with BUILD cannot attach SecurityTerminalBlock");
            var ownerSecPlace = new BlockEvent.EntityPlaceEvent(secSnap, against, ownerA);
            NeoForge.EVENT_BUS.post(ownerSecPlace);
            helper.assertTrue(!ownerSecPlace.isCanceled(), "owner may attach second SecurityTerminalBlock");
            helper.setBlock(placeRel, Blocks.AIR);

            // #1 onBlockBreak: non-owner BUILD cannot break SecurityTerminalBlock; untrusted cannot break AtlasCasingBlock
            var builderBreakSec = new BreakBlockEvent(level, absSec1, level.getBlockState(absSec1), builder);
            NeoForge.EVENT_BUS.post(builderBreakSec);
            helper.assertTrue(builderBreakSec.isCanceled(), "non-owner with BUILD cannot break SecurityTerminalBlock");
            var ownerBreakSec = new BreakBlockEvent(level, absSec1, level.getBlockState(absSec1), ownerA);
            NeoForge.EVENT_BUS.post(ownerBreakSec);
            helper.assertTrue(!ownerBreakSec.isCanceled(), "owner may break own SecurityTerminalBlock");

            BlockPos casingAbs = helper.absolutePos(ORIGIN.offset(-1, 3, -1));
            BlockState casingState = level.getBlockState(casingAbs);
            helper.assertTrue(casingState.is(ModBlocks.ATLAS_CASING.get()), "expected crown corner AtlasCasingBlock");
            var strangerBreakCasing = new BreakBlockEvent(level, casingAbs, casingState, stranger);
            NeoForge.EVENT_BUS.post(strangerBreakCasing);
            helper.assertTrue(strangerBreakCasing.isCanceled(), "stranger cannot break AtlasCasingBlock of secured array");
            var builderBreakCasing = new BreakBlockEvent(level, casingAbs, casingState, builder);
            NeoForge.EVENT_BUS.post(builderBreakCasing);
            helper.assertTrue(!builderBreakCasing.isCanceled(), "builder may break AtlasCasingBlock of secured array");

            // #2: Live BUILD revocation closes stillValid on open device menus (OwnedContainer & stillValidNetwork)
            CopperDriveBayBlockEntity bayBe = helper.getBlockEntity(bay, CopperDriveBayBlockEntity.class);
            CryoCalculatorBlockEntity calcBe = helper.getBlockEntity(calcPos, CryoCalculatorBlockEntity.class);
            DriveBayMenu bayMenu = new DriveBayMenu(901, builder.getInventory(), bayBe.getContainer(), bayBe.priorityData());
            CalculatorMenu calcMenu = new CalculatorMenu(902, builder.getInventory(), calcBe, calcBe.getUpgrades(),
                    calcBe.menuData(), ContainerLevelAccess.create(level, absCalc));
            helper.assertTrue(bayMenu.stillValid(builder), "DriveBayMenu valid while builder has BUILD");
            helper.assertTrue(calcMenu.stillValid(builder), "CalculatorMenu valid while builder has BUILD");

            sec1.getCards().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(!bayMenu.stillValid(builder), "DriveBayMenu invalid immediately after BUILD revoked");
            helper.assertTrue(!calcMenu.stillValid(builder), "CalculatorMenu invalid immediately after BUILD revoked");

            // #1: Multi-terminal intersection (fail-closed on conflict)
            BlockPos sec2Pos = BUS_IN;
            helper.setBlock(sec2Pos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            SecurityTerminalBlockEntity sec2 = helper.getBlockEntity(sec2Pos, SecurityTerminalBlockEntity.class);
            sec2.setOwner(ownerB);

            GolemNetwork both = GolemNetwork.refresh(level, absBay);
            helper.assertTrue(both.hasSecurityTerminal() && both.securityTerminals().size() == 2,
                    "both security terminals discovered, got " + both.securityTerminals().size());
            helper.assertTrue(!both.hasPermission(ownerA, SecurityPermission.BUILD),
                    "ownerA denied when sec2 does not grant permission (intersection)");
            helper.assertTrue(!both.hasPermission(ownerB, SecurityPermission.BUILD),
                    "ownerB denied when sec1 does not grant permission (intersection)");

            sec2.getCards().setItem(0, IdentityPunchCardItem.of(ownerA.getUUID(), "audit-owner-a", SecurityPermission.BUILD.mask));
            helper.assertTrue(both.hasPermission(ownerA, SecurityPermission.BUILD),
                    "ownerA allowed when ALL terminals grant BUILD");

            helper.succeed();
        });
    }

    /** #5: Interface Fuzzy Tag #c:buckets never overstacks non-stackable water_bucket above 1 and returns excess without loss. */
    public static void auditInterfaceFuzzyBucketsNoOverstack(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        BlockPos ifacePos = BUS_OUT;
        helper.setBlock(ifacePos, ModBlocks.GOLEM_INTERFACE.get().defaultBlockState());
        InterfaceBlockEntity iface = helper.getBlockEntity(ifacePos, InterfaceBlockEntity.class);
        iface.getUpgrades().setItem(UpgradeSpec.INTERFACE.slotOf(UpgradeType.FUZZY), new ItemStack(ModItems.FUZZY_CARD.get()));

        TagKey<Item> bucketsTag = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "buckets"));
        ItemStack taggedBucket = Fuzzy.withTag(new ItemStack(Items.BUCKET), bucketsTag);
        helper.assertTrue(new ItemStack(Items.BUCKET).is(bucketsTag) && new ItemStack(Items.WATER_BUCKET).is(bucketsTag),
                "#c:buckets tag must contain bucket and water_bucket");
        iface.setSlot(0, taggedBucket, 8);
        helper.assertTrue(!iface.isFluidSlot(0) && iface.wanted(0) == 8,
                "bucket ghost in item mode wants 8");

        ItemKey waterBucket = ItemKey.of(new ItemStack(Items.WATER_BUCKET));
        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            for (int i = 0; i < 4; i++) {
                n.insertItem(new ItemStack(Items.WATER_BUCKET), false);
            }
            iface.wakeStock();
        });

        helper.runAfterDelay(35, () -> {
            ItemStack stocked = iface.getStock().getItem(0);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(stocked.is(Items.WATER_BUCKET) && stocked.getCount() == 1,
                    "water_bucket stocked capped at 1, got " + stocked);
            helper.assertTrue(n.itemCountOf(waterBucket) == 3,
                    "remaining 3 water_buckets preserved in storage, got " + n.itemCountOf(waterBucket));

            // Existing overstack (mutated in-place bypassing SimpleContainer#setItem clamp) is cleanly returned to storage
            stocked.setCount(3);
            iface.wakeStock();
        });

        helper.runAfterDelay(70, () -> {
            ItemStack stocked = iface.getStock().getItem(0);
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(stocked.is(Items.WATER_BUCKET) && stocked.getCount() == 1,
                    "overstacked water_bucket clamped back to 1, got " + stocked);
            helper.assertTrue(n.itemCountOf(waterBucket) == 5,
                    "excess 2 water_buckets returned to storage (total 5), got " + n.itemCountOf(waterBucket));
            helper.succeed();
        });
    }

    /** #7 + #14: Parallel Cryo-Diodes deduplicate shared upstream Source without double-count/voiding; sideways/backwards diodes are blocked. */
    public static void auditCryoDiodeParallelDedupAndSidewaysBlocked(GameTestHelper helper) {
        var level = helper.getLevel();
        // Net A: pipes at (3,1,5)..(5,1,5) + single CryoJar at (3,1,6) with 100 Source
        for (int x = 3; x <= 5; x++) {
            put(helper, new BlockPos(x, 1, 5), ModBlocks.BASIC_SNOW_PIPE.get());
        }
        BlockPos jarRel = new BlockPos(3, 1, 6);
        CryoJarBlock.place(level, helper.absolutePos(jarRel), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        CryoJarBlockEntity jar = helper.getBlockEntity(jarRel, CryoJarBlockEntity.class);
        jar.fill(100, false);

        // Two parallel diodes facing NORTH from Net A (z=5) to Net B (z=3)
        helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.CRYO_DIODE.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(new BlockPos(5, 1, 4), ModBlocks.CRYO_DIODE.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));

        // Net B: pipes at (4,1,3), (5,1,3) + bare core at (4,2,3)
        put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(5, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(4, 2, 3), ModBlocks.ATLAS_CORE.get());

        // #14: Sideways diode A (facing EAST) and backwards diode C (facing SOUTH) touching a 500-Source jar
        BlockPos sideJarRel = new BlockPos(9, 1, 5);
        CryoJarBlock.place(level, helper.absolutePos(sideJarRel), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        CryoJarBlockEntity sideJar = helper.getBlockEntity(sideJarRel, CryoJarBlockEntity.class);
        sideJar.fill(500, false);

        helper.setBlock(new BlockPos(9, 1, 4), ModBlocks.CRYO_DIODE.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.EAST));
        BlockPos probeSideRel = new BlockPos(9, 1, 3);
        helper.setBlock(probeSideRel, ModBlocks.CRYO_DIODE.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));

        BlockPos backJarRel = new BlockPos(11, 1, 4);
        CryoJarBlock.place(level, helper.absolutePos(backJarRel), Direction.SOUTH, ModBlocks.CRYO_SOURCE_JAR.get());
        helper.getBlockEntity(backJarRel, CryoJarBlockEntity.class).fill(500, false);
        helper.setBlock(new BlockPos(12, 1, 4), ModBlocks.CRYO_DIODE.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        BlockPos probeBackRel = new BlockPos(12, 1, 3);
        helper.setBlock(probeBackRel, ModBlocks.CRYO_DIODE.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));

        helper.runAfterDelay(5, () -> {
            GolemNetwork netB = GolemNetwork.refresh(level, helper.absolutePos(new BlockPos(4, 1, 3)));
            helper.assertTrue(netB.sources().size() == 2, "Net B sees 2 diode suppliers, got " + netB.sources().size());

            Set<SourceHelper.Supplier> leaves = new LinkedHashSet<>();
            for (SourceHelper.Supplier s : netB.sources()) {
                s.collectLeafSuppliers(leaves);
            }
            helper.assertTrue(leaves.size() == 1, "parallel diodes collapse to 1 unique leaf supplier, got " + leaves.size());
            int expected = jar.available();
            int avail = leaves.iterator().next().available();
            helper.assertTrue(expected >= 90 && avail == expected,
                    "deduplicated leaf matches physical jar (" + expected + "), got " + avail);

            if (SourceHelper.sourceRequired()) {
                helper.assertTrue(!netB.hasSource(expected + 50), "Net B must not double-count " + expected + " as >=" + (expected + 50));
                helper.assertTrue(!netB.consumeSource(expected + 50), "consuming " + (expected + 50) + " from " + expected + " must fail");
                helper.assertTrue(jar.available() == expected, "failed consume must NOT void Source, jar=" + jar.available());
                helper.assertTrue(netB.consumeSource(50), "consuming 50 from " + expected + " succeeds");
                helper.assertTrue(jar.available() == expected - 50, "jar has " + (expected - 50) + " left after consuming 50, got " + jar.available());
            }

            // #14: sideways and backwards diodes must never expose adjacent jars via inletNetwork
            SourceHelper.Supplier sideOut = helper.getBlockEntity(probeSideRel, CryoDiodeBlockEntity.class).asSupplier(Direction.NORTH);
            helper.assertTrue(sideOut != null && sideOut.available() == 0 && sideOut.drain(100) == 0,
                    "sideways diode behind inlet must supply 0");
            helper.assertTrue(sideJar.available() == 500, "sideways jar untouched");

            SourceHelper.Supplier backOut = helper.getBlockEntity(probeBackRel, CryoDiodeBlockEntity.class).asSupplier(Direction.NORTH);
            helper.assertTrue(backOut != null && backOut.available() == 0 && backOut.drain(100) == 0,
                    "backwards diode behind inlet must supply 0");

            helper.succeed();
        });
    }

    /** #10: TerminalFluids.fillCarried does NOT void 500 mB from an EXTRACT_ONLY Periscope when clicking with an empty Bucket. */
    public static void auditTerminalFluidsExtractOnlyNoVoid(GameTestHelper helper) {
        baseNetwork(helper);
        var level = helper.getLevel();
        BlockPos periPos = BUS_OUT;
        BlockPos tankPos = periPos.north();
        helper.setBlock(periPos, ModBlocks.PERISCOPE_STORAGE_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));
        helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get().defaultBlockState());
        helper.setBlock(TERM, ModBlocks.ANALOG_TERMINAL.get().defaultBlockState());

        PeriscopeBlockEntity peri = helper.getBlockEntity(periPos, PeriscopeBlockEntity.class);
        peri.menuData().set(2, PeriscopeBlockEntity.EXTRACT_ONLY);

        BlockPos absTank = helper.absolutePos(tankPos);
        ResourceHandler<FluidResource> tankCap = level.getCapability(Capabilities.Fluid.BLOCK, absTank, Direction.UP);
        helper.assertTrue(tankCap != null, "copper tank has fluid capability");
        try (Transaction tx = Transaction.openRoot()) {
            int put = tankCap.insert(FluidResource.of(Fluids.WATER), 500, tx);
            helper.assertTrue(put == 500, "inserted 500 mB water into tank");
            tx.commit();
        }

        ServerPlayer player = fakePlayer(helper, "audit-fluid-player", TERM);
        helper.runAfterDelay(5, () -> {
            BlockPos absTerm = helper.absolutePos(TERM);
            GolemNetwork n = GolemNetwork.refresh(level, absTerm);
            helper.assertTrue(n.fluidCountOf(Fluids.WATER) == 500, "network sees 500 mB water");

            TerminalMenu menu = new TerminalMenu(910, player.getInventory(), level, absTerm, absTerm,
                    ModBlocks.ANALOG_TERMINAL.get(), false, false, 0);
            player.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.BUCKET));

            menu.handleAction(player, new TerminalActionPayload(910, TerminalActionPayload.Action.FLUID_FILL_CARRIED,
                    ItemStack.EMPTY, new FluidStack(Fluids.WATER, 1000)));
            CopperTankBlockEntity tankBe = helper.getBlockEntity(tankPos, CopperTankBlockEntity.class);
            helper.assertTrue(menu.getCarried().is(Items.BUCKET), "empty bucket stays empty with only 500 mB");
            helper.assertTrue(tankBe.fluid().getAmount() == 500,
                    "500 mB in EXTRACT_ONLY periscope must NOT be voided, left=" + tankBe.fluid().getAmount());

            // Topping tank to 1000 mB fills the bucket cleanly
            try (Transaction tx = Transaction.openRoot()) {
                tankCap.insert(FluidResource.of(Fluids.WATER), 500, tx);
                tx.commit();
            }
            menu.handleAction(player, new TerminalActionPayload(910, TerminalActionPayload.Action.FLUID_FILL_CARRIED,
                    ItemStack.EMPTY, new FluidStack(Fluids.WATER, 1000)));
            helper.assertTrue(menu.getCarried().is(Items.WATER_BUCKET), "bucket filled once 1000 mB available");
            helper.assertTrue(tankBe.fluid().isEmpty(), "tank drained after filling bucket");
            helper.succeed();
        });
    }

    /** #11: Crafting Terminal Shift+Click on result slot caps crafting at 1 full stack per click. */
    public static void auditTerminalShiftClickCapsAtOneStack(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        var level = helper.getLevel();
        helper.setBlock(TERM, ModBlocks.CRAFTING_TERMINAL.get().defaultBlockState());
        ServerPlayer player = fakePlayer(helper, "audit-craft-shift", TERM);
        player.getInventory().clearContent();

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.OAK_LOG, 64), false);

            BlockPos absTerm = helper.absolutePos(TERM);
            TerminalMenu menu = new TerminalMenu(911, player.getInventory(), level, absTerm, absTerm,
                    ModBlocks.CRAFTING_TERMINAL.get(), true, false, 0);
            player.containerMenu = menu;
            menu.getSlot(1).set(new ItemStack(Items.OAK_LOG, 1));
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS) && menu.getSlot(0).getItem().getCount() == 4,
                    "crafting grid previews 4 oak planks");

            menu.quickMoveStack(player, 0);
            int planks = player.getInventory().countItem(Items.OAK_PLANKS);
            long logsLeft = n.itemCountOf(ItemKey.of(new ItemStack(Items.OAK_LOG))) + menu.getSlot(1).getItem().getCount();
            helper.assertTrue(planks == 64, "Shift+Click capped at 1 stack (64 planks), got " + planks);
            helper.assertTrue(logsLeft == 49, "16 logs crafted (1 initial + 15 refilled -> 49 logs left), got " + logsLeft);
            helper.succeed();
        });
    }

    /** #6: CryoJar periodic integrity validation detaches corrupted controller footprints and orphaned member jars. */
    public static void auditCryoJarIntegrityDetachesOrphan(GameTestHelper helper) {
        var level = helper.getLevel();
        Block jarBlock = ModBlocks.CRYO_SOURCE_JAR.get();

        // Corrupted 2x2 Giga Jar: 3 corners present with FORM=GIGA, 4th corner (3,1,3) missing (AIR)
        BlockPos c00 = new BlockPos(2, 1, 2);
        BlockPos c01 = new BlockPos(2, 1, 3);
        BlockPos c10 = new BlockPos(3, 1, 2);
        BlockPos absC00 = helper.absolutePos(c00);
        for (BlockPos rel : new BlockPos[]{c00, c01, c10}) {
            BlockPos abs = helper.absolutePos(rel);
            CryoJarBlock.place(level, abs, Direction.SOUTH, jarBlock);
            level.setBlock(abs, level.getBlockState(abs).setValue(CryoJarBlock.FORM, JarForm.GIGA),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(abs.above(), level.getBlockState(abs.above()).setValue(CryoJarBlock.FORM, JarForm.GIGA),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.getBlockEntity(rel, CryoJarBlockEntity.class).setController(absC00);
        }
        helper.getBlockEntity(c00, CryoJarBlockEntity.class).putAll(3000);

        // Orphaned non-controller member pointing at an empty AIR controller pos
        BlockPos orphanRel = new BlockPos(7, 1, 2);
        BlockPos orphanAbs = helper.absolutePos(orphanRel);
        CryoJarBlock.place(level, orphanAbs, Direction.SOUTH, jarBlock);
        level.setBlock(orphanAbs, level.getBlockState(orphanAbs).setValue(CryoJarBlock.FORM, JarForm.GIGA),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(orphanAbs.above(), level.getBlockState(orphanAbs.above()).setValue(CryoJarBlock.FORM, JarForm.GIGA),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        helper.getBlockEntity(orphanRel, CryoJarBlockEntity.class).setController(helper.absolutePos(new BlockPos(10, 1, 2)));

        helper.succeedWhen(() -> {
            for (BlockPos rel : new BlockPos[]{c00, c01, c10}) {
                CryoJarBlockEntity jar = helper.getBlockEntity(rel, CryoJarBlockEntity.class);
                helper.assertTrue(jar.form() == JarForm.SINGLE && jar.controller() == null,
                        "corrupted Giga member at " + rel + " reset to SINGLE");
                helper.assertTrue(jar.available() == 1000,
                        "Source distributed evenly (1000 each), got " + jar.available());
            }
            CryoJarBlockEntity orphan = helper.getBlockEntity(orphanRel, CryoJarBlockEntity.class);
            helper.assertTrue(orphan.form() == JarForm.SINGLE && orphan.controller() == null,
                    "orphaned member jar detached to SINGLE");
            helper.assertTrue(level.getBlockState(orphanAbs.above()).getValue(CryoJarBlock.FORM) == JarForm.SINGLE,
                    "orphaned upper half reset to SINGLE");
        });
    }

    /** #18b: 32-bit ContainerData split unpacks > 32767 (and > 65535) cleanly across 16-bit network truncation. */
    public static void auditContainerData32BitUnpacking(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = fakePlayer(helper, "audit-data32", ORIGIN);

        BlockPos towerPos = new BlockPos(2, 1, 2);
        BlockPos caulPos = new BlockPos(6, 1, 2);
        BlockPos mixerPos = new BlockPos(10, 1, 2);
        DistillationTowerBlock.place(level, helper.absolutePos(towerPos), Direction.SOUTH, ModBlocks.CRYO_DISTILLATION_TOWER.get());
        EssentiaCauldronBlock.place(level, helper.absolutePos(caulPos), Direction.SOUTH, ModBlocks.ESSENTIA_CAULDRON.get());
        CryoMixerBlock.place(level, helper.absolutePos(mixerPos), Direction.SOUTH, ModBlocks.CRYO_MIXER.get());

        DistillationTowerBlockEntity tower = helper.getBlockEntity(towerPos, DistillationTowerBlockEntity.class);
        EssentiaCauldronBlockEntity caul = helper.getBlockEntity(caulPos, EssentiaCauldronBlockEntity.class);
        CryoMixerBlockEntity mixer = helper.getBlockEntity(mixerPos, CryoMixerBlockEntity.class);

        CompoundTag mixerTag = new CompoundTag();
        mixerTag.putInt("Progress", 40000);
        mixer.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), mixerTag));

        int oldRate = GolemConfig.DISTILL_RATE.get();
        double oldPerMb = GolemConfig.DISTILL_SOURCE_PER_MB.get();
        double oldCaulSpeed = GolemConfig.CAULDRON_BASE_SPEED.get();
        int oldCaulSource = GolemConfig.CAULDRON_SOURCE_PER_REACTION.get();
        int oldSnowTicks = GolemConfig.MIXER_SNOW_TICKS.get();
        try {
            GolemConfig.DISTILL_RATE.set(80000);
            GolemConfig.DISTILL_SOURCE_PER_MB.set(1.5D);
            GolemConfig.CAULDRON_BASE_SPEED.set(20.0D);
            GolemConfig.CAULDRON_SOURCE_PER_REACTION.set(500);
            GolemConfig.MIXER_SNOW_TICKS.set(70000);

            DistillationTowerMenu towerMenu = new DistillationTowerMenu(920, player.getInventory(),
                    tower.getUpgrades(), shortTruncated(tower.menuData()), ContainerLevelAccess.NULL);
            helper.assertTrue(towerMenu.rate() == 80000,
                    "DistillationTowerMenu rate > 65535 expected 80000, got " + towerMenu.rate());
            helper.assertTrue(Math.abs(towerMenu.sourcePerSecond() - 120000.0) < 0.15,
                    "DistillationTowerMenu sourcePerSecond expected 120000.0, got " + towerMenu.sourcePerSecond());

            EssentiaCauldronMenu caulMenu = new EssentiaCauldronMenu(921, player.getInventory(),
                    caul.getUpgrades(), shortTruncated(caul.menuData()), ContainerLevelAccess.NULL);
            helper.assertTrue(Math.abs(caulMenu.sourcePerSecond() - 10000.0) < 0.15,
                    "EssentiaCauldronMenu sourcePerSecond expected 10000.0, got " + caulMenu.sourcePerSecond());

            CryoMixerMenu mixerMenu = new CryoMixerMenu(922, player.getInventory(),
                    mixer.getOutput(), mixer.getUpgrades(), shortTruncated(mixer.menuData()), mixer, ContainerLevelAccess.NULL);
            helper.assertTrue(mixerMenu.ticksPerBlock() == 70000,
                    "CryoMixerMenu ticksPerBlock expected 70000, got " + mixerMenu.ticksPerBlock());
            helper.assertTrue(mixerMenu.progress() == 40000,
                    "CryoMixerMenu progress > 32767 expected 40000, got " + mixerMenu.progress());
        } finally {
            GolemConfig.DISTILL_RATE.set(oldRate);
            GolemConfig.DISTILL_SOURCE_PER_MB.set(oldPerMb);
            GolemConfig.CAULDRON_BASE_SPEED.set(oldCaulSpeed);
            GolemConfig.CAULDRON_SOURCE_PER_REACTION.set(oldCaulSource);
            GolemConfig.MIXER_SNOW_TICKS.set(oldSnowTicks);
        }
        helper.succeed();
    }

    /**
     * P1: Two Periscope Storage Buses on opposite halves of a vanilla double chest must not double-count stock or
     * simulated extraction/insertion, while preserving each Periscope's own filters and access mode.
     */
    public static void auditDoubleChestPeriscopesNoDoubleCount(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        var level = helper.getLevel();

        // Pipe run from east corner bushing (6,1,6) -> (7,1,6) & (7,1,7) -> two Periscopes facing EAST at (8,1,6) & (8,1,7)
        put(helper, new BlockPos(7, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(7, 1, 7), ModBlocks.BASIC_SNOW_PIPE.get());
        BlockPos periAPos = new BlockPos(8, 1, 6);
        BlockPos periBPos = new BlockPos(8, 1, 7);
        periscope(helper, periAPos, Direction.EAST);
        periscope(helper, periBPos, Direction.EAST);

        // Vanilla double chest facing WEST: RIGHT at (9,1,6) connects SOUTH to LEFT at (9,1,7)
        BlockPos chestRightPos = new BlockPos(9, 1, 6);
        BlockPos chestLeftPos = new BlockPos(9, 1, 7);
        helper.setBlock(chestRightPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(chestLeftPos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST)
                .setValue(ChestBlock.TYPE, ChestType.LEFT));

        BlockPos absChest = helper.absolutePos(chestRightPos);
        Container dc = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, level.getBlockState(absChest), level, absChest, true);
        helper.assertTrue(dc != null && dc.getContainerSize() == 54,
                "double chest must expose 54 combined slots, got " + (dc == null ? "null" : dc.getContainerSize()));
        dc.setItem(0, new ItemStack(Items.DIAMOND, 20));
        dc.setItem(30, new ItemStack(Items.IRON_INGOT, 15));

        PeriscopeBlockEntity periA = helper.getBlockEntity(periAPos, PeriscopeBlockEntity.class);
        PeriscopeBlockEntity periB = helper.getBlockEntity(periBPos, PeriscopeBlockEntity.class);
        ItemKey diamondKey = ItemKey.of(new ItemStack(Items.DIAMOND));
        ItemKey ironKey = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(20, () -> {
            GolemNetwork n = GolemNetwork.refresh(level, helper.absolutePos(ORIGIN));
            helper.assertTrue(n.isOnline(), "network online");

            // 1. Both Periscopes unfiltered: 20 diamonds + 15 iron counted once, not doubled
            var listed = n.listItems();
            helper.assertTrue(listed.getOrDefault(diamondKey, 0L) == 20L,
                    "listItems must report 20 diamonds, got " + listed.getOrDefault(diamondKey, 0L));
            helper.assertTrue(n.itemCountOf(diamondKey) == 20L,
                    "itemCountOf must report 20 diamonds, got " + n.itemCountOf(diamondKey));
            helper.assertTrue(n.itemCountOf(ironKey) == 15L,
                    "itemCountOf must report 15 iron, got " + n.itemCountOf(ironKey));
            ItemStack simExtract = n.extractItem(new ItemStack(Items.DIAMOND), 40, true);
            helper.assertTrue(simExtract.getCount() == 20,
                    "simulated extract(40) from 20-diamond double chest must return 20, got " + simExtract.getCount());
            helper.assertTrue(dc.getItem(0).getCount() == 20, "simulated extract left chest untouched");

            // 1b. skipTarget on either half skips BOTH halves of the double chest (no cross-half bus loop)
            BlockPos absRight = helper.absolutePos(chestRightPos);
            BlockPos absLeft = helper.absolutePos(chestLeftPos);
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIAMOND), 20, true, absRight).isEmpty(),
                    "skipTarget on RIGHT half must skip both halves on extract");
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIAMOND), 20, true, absLeft).isEmpty(),
                    "skipTarget on LEFT half must skip both halves on extract");
            helper.assertTrue(n.insertItem(new ItemStack(Items.GOLD_INGOT, 5), true, absLeft).getCount() == 5,
                    "skipTarget on LEFT half must skip both halves on insert");

            // 1c. Real extract with BOTH Periscopes in BOTH mode takes exact 20 and empties stock
            ItemStack realBoth = n.extractItem(new ItemStack(Items.DIAMOND), 40, false);
            helper.assertTrue(realBoth.getCount() == 20 && dc.getItem(0).isEmpty() && n.itemCountOf(diamondKey) == 0L,
                    "real extract(40) with both Periscopes in BOTH mode must take exact 20, got " + realBoth.getCount());
            dc.setItem(0, new ItemStack(Items.DIAMOND, 20));

            // 2. Simulated insert must not double-count free space across the two Periscopes
            dc.clearContent();
            for (int s = 0; s < 53; s++) {
                dc.setItem(s, new ItemStack(Items.COBBLESTONE, 64));
            }
            ItemStack simInsertRem = n.insertItem(new ItemStack(Items.DIAMOND, 128), true);
            helper.assertTrue(simInsertRem.getCount() == 64,
                    "simulated insert(128) into 1-free-slot double chest must leave 64 remainder, got " + simInsertRem.getCount());
            helper.assertTrue(dc.getItem(53).isEmpty(), "simulated insert left slot 53 empty");

            // 3. Distinct per-Periscope filters: A allows diamonds, B allows iron -> both visible cleanly
            dc.clearContent();
            dc.setItem(0, new ItemStack(Items.DIAMOND, 20));
            dc.setItem(30, new ItemStack(Items.IRON_INGOT, 15));
            periA.getFilters().setItem(0, new ItemStack(Items.DIAMOND));
            periB.getFilters().setItem(0, new ItemStack(Items.IRON_INGOT));

            var filteredList = n.listItems();
            helper.assertTrue(filteredList.getOrDefault(diamondKey, 0L) == 20L,
                    "filtered listItems sees 20 diamonds via Periscope A, got " + filteredList.getOrDefault(diamondKey, 0L));
            helper.assertTrue(filteredList.getOrDefault(ironKey, 0L) == 15L,
                    "filtered listItems sees 15 iron via Periscope B, got " + filteredList.getOrDefault(ironKey, 0L));
            helper.assertTrue(n.itemCountOf(diamondKey) == 20L && n.itemCountOf(ironKey) == 15L,
                    "filtered itemCountOf sees both 20 diamonds and 15 iron");
            helper.assertTrue(n.extractItem(new ItemStack(Items.DIAMOND), 40, true).getCount() == 20,
                    "filtered sim extract sees 20 diamonds");
            helper.assertTrue(n.extractItem(new ItemStack(Items.IRON_INGOT), 40, true).getCount() == 15,
                    "filtered sim extract sees 15 iron");
            helper.assertTrue(n.insertItem(new ItemStack(Items.GOLD_INGOT, 5), true).getCount() == 5,
                    "unmatched gold rejected by both periscopes");

            // 4. Distinct access modes: A = INSERT_ONLY, B = EXTRACT_ONLY on same double chest
            periA.getFilters().clearContent();
            periB.getFilters().clearContent();
            periA.menuData().set(2, PeriscopeBlockEntity.INSERT_ONLY);
            periB.menuData().set(2, PeriscopeBlockEntity.EXTRACT_ONLY);
            helper.assertTrue(n.itemCountOf(diamondKey) == 20L,
                    "EXTRACT_ONLY Periscope B still exposes 20 diamonds when Periscope A is INSERT_ONLY");
            helper.assertTrue(n.insertItem(new ItemStack(Items.GOLD_INGOT, 5), true).isEmpty(),
                    "INSERT_ONLY Periscope A accepts gold when Periscope B is EXTRACT_ONLY");
            ItemStack realTaken = n.extractItem(new ItemStack(Items.DIAMOND), 40, false);
            helper.assertTrue(realTaken.getCount() == 20 && n.itemCountOf(diamondKey) == 0L,
                    "real extract takes exact 20 diamonds and empties stock");

            // 5. WorldlyContainer (Furnace TOP + SIDE) multi-face Periscopes: distinct real slots (input 0 vs fuel 1)
            // must NOT collide even though both sided handlers use local index 0, and both faces accept coal on sim insert.
            periA.menuData().set(2, PeriscopeBlockEntity.EXTRACT_ONLY);
            periB.menuData().set(2, PeriscopeBlockEntity.EXTRACT_ONLY);
            BlockPos furnacePos = new BlockPos(3, 1, 2);
            helper.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState());
            put(helper, new BlockPos(3, 1, 4), ModBlocks.BASIC_SNOW_PIPE.get());
            put(helper, new BlockPos(3, 2, 4), ModBlocks.BASIC_SNOW_PIPE.get());
            put(helper, new BlockPos(3, 2, 3), ModBlocks.BASIC_SNOW_PIPE.get());
            periscope(helper, new BlockPos(3, 1, 3), Direction.NORTH);
            periscope(helper, new BlockPos(3, 2, 2), Direction.DOWN);

            FurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos, FurnaceBlockEntity.class);
            furnace.setItem(0, new ItemStack(Items.RAW_COPPER, 12));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));

            GolemNetwork nf = GolemNetwork.refresh(level, helper.absolutePos(ORIGIN));
            ItemKey rawCopperKey = ItemKey.of(new ItemStack(Items.RAW_COPPER));
            ItemKey coalKey = ItemKey.of(new ItemStack(Items.COAL));
            var furnaceList = nf.listItems();
            helper.assertTrue(furnaceList.getOrDefault(rawCopperKey, 0L) == 12L
                            && furnaceList.getOrDefault(coalKey, 0L) == 8L,
                    "WorldlyContainer Furnace TOP+SIDE must expose both input (12 raw copper) and fuel (8 coal), got "
                            + furnaceList.getOrDefault(rawCopperKey, 0L) + " / " + furnaceList.getOrDefault(coalKey, 0L));
            helper.assertTrue(nf.itemCountOf(rawCopperKey) == 12L && nf.itemCountOf(coalKey) == 8L,
                    "Furnace itemCountOf must not drop SIDE fuel due to local slot 0 collision");
            helper.assertTrue(nf.extractItem(new ItemStack(Items.RAW_COPPER), 20, true).getCount() == 12
                            && nf.extractItem(new ItemStack(Items.COAL), 20, true).getCount() == 8,
                    "Furnace simulated extract sees exact input and fuel counts across faces");

            furnace.clearContent();
            helper.assertTrue(nf.insertItem(new ItemStack(Items.COAL, 128), true).isEmpty(),
                    "Furnace TOP (input) + SIDE (fuel) must accept 128 coal across both faces on sim insert");
            helper.assertTrue(nf.insertItem(new ItemStack(Items.COAL, 192), true).getCount() == 64,
                    "Furnace TOP + SIDE must leave 64 remainder from 192 coal (max 128 across 2 slots)");
            helper.assertTrue(furnace.isEmpty(), "Furnace sim insert left furnace empty");
            helper.succeed();
        });
    }

    /**
     * P2: Waiting Pattern Providers and pending bus craft orders are keyed by GlobalPos (dimension + BlockPos)
     * so identical XYZ in different dimensions never collide, with backwards-compatible NBT persistence.
     */
    public static void auditCrossDimensionCraftingKeys(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        var level = helper.getLevel();
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        helper.setBlock(PROV, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(PROV.east(), Blocks.CHEST);

        PatternProviderBlockEntity provider = helper.getBlockEntity(PROV, PatternProviderBlockEntity.class);
        provider.getContainer().setItem(0, card(new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)))));
        provider.setBlocking(true);

        BlockPos sameBusPos = helper.absolutePos(BUS_OUT);
        BlockPos absProv = helper.absolutePos(PROV);
        ItemKey ingotKey = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 2), false);
            GlobalPos overworldBus = GlobalPos.of(level.dimension(), sameBusPos);
            GlobalPos netherBus = GlobalPos.of(Level.NETHER, sameBusPos);
            var started = n.startCrafting(Map.of(ingotKey, 2L), null, overworldBus);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "craft started for overworld bus");
            helper.assertTrue(n.busCraftPending(overworldBus, ingotKey), "overworld bus sees its own pending job");
            helper.assertTrue(!n.busCraftPending(netherBus, ingotKey),
                    "nether bus at identical XYZ must NOT collide with overworld bus pending job");
        });

        helper.runAfterDelay(35, () -> {
            GolemNetwork n = net(helper, bay);
            GlobalPos overworldProv = GlobalPos.of(level.dimension(), absProv);
            GlobalPos netherProv = GlobalPos.of(Level.NETHER, absProv);
            helper.assertTrue(n.providerBusy(overworldProv), "overworld blocking provider is busy awaiting result");
            helper.assertTrue(!n.providerBusy(netherProv),
                    "nether provider at identical XYZ must NOT collide with overworld busy provider");

            CraftingJob job = helper.getBlockEntity(CPU, CryoCalculatorBlockEntity.class).getJob();
            helper.assertTrue(job != null, "calculator holds active job");

            // NBT round-trip preserves explicit dimensions for both requesterBus and InFlight providers
            var registries = level.registryAccess();
            CompoundTag tag = job.save(registries);
            helper.assertTrue(level.dimension().identifier().toString().equals(tag.getStringOr("RequesterBusDim", "")),
                    "saved RequesterBusDim matches level dimension");
            var flights = tag.getListOrEmpty("InFlight");
            helper.assertTrue(flights.size() == 1
                            && level.dimension().identifier().toString().equals(flights.getCompoundOrEmpty(0).getStringOr("Dim", "")),
                    "saved InFlight entry carries Dim");

            tag.putString("RequesterBusDim", Level.NETHER.identifier().toString());
            flights.getCompoundOrEmpty(0).putString("Dim", Level.END.identifier().toString());
            CraftingJob crossDimLoaded = CraftingJob.load(tag, registries);
            helper.assertTrue(crossDimLoaded != null, "cross-dim job loaded");
            helper.assertTrue(GlobalPos.of(Level.NETHER, sameBusPos).equals(crossDimLoaded.requesterBus()),
                    "loaded requesterBus preserves Nether dimension");
            helper.assertTrue(crossDimLoaded.hasInFlight(GlobalPos.of(Level.END, absProv))
                            && !crossDimLoaded.hasInFlight(overworldProv),
                    "loaded InFlight preserves End dimension and does not match Overworld");

            // Legacy pre-dimension save compatibility (missing RequesterBusDim and Dim)
            CompoundTag legacyTag = tag.copy();
            legacyTag.remove("RequesterBusDim");
            legacyTag.getListOrEmpty("InFlight").getCompoundOrEmpty(0).remove("Dim");

            CraftingJob legacyWithDim = CraftingJob.load(legacyTag, registries, Level.NETHER);
            helper.assertTrue(legacyWithDim != null
                            && GlobalPos.of(Level.NETHER, sameBusPos).equals(legacyWithDim.requesterBus())
                            && legacyWithDim.hasInFlight(netherProv),
                    "legacy NBT defaults both requesterBus and InFlight to provided level dimension");

            CraftingJob legacyDeferred = CraftingJob.load(legacyTag, registries, null);
            helper.assertTrue(legacyDeferred != null, "legacy NBT loads before setLevel");
            legacyDeferred.resolveLegacyDimension(Level.END);
            helper.assertTrue(GlobalPos.of(Level.END, sameBusPos).equals(legacyDeferred.requesterBus())
                            && legacyDeferred.hasInFlight(GlobalPos.of(Level.END, absProv)),
                    "resolveLegacyDimension rebinds legacy entries once CPU receives its level");

            helper.succeed();
        });
    }

    /**
     * P1: When two blocking Pattern Providers both have an in-flight batch of the same item and a result returns
     * through the second provider, the second provider's own batch settles first so provider 1 stays busy.
     */
    public static void auditProviderReturnSettlesOwnBatch(GameTestHelper helper) {
        BlockPos bay = baseNetwork(helper);
        var level = helper.getLevel();
        helper.setBlock(CPU, ModBlocks.CRYO_CALCULATOR.get().defaultBlockState());
        BlockPos prov1Pos = PROV;
        BlockPos prov2Pos = BUS_OUT;
        helper.setBlock(prov1Pos, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        helper.setBlock(prov2Pos, ModBlocks.PATTERN_PROVIDER.get().defaultBlockState());
        BlockPos chest1Pos = prov1Pos.east();
        BlockPos chest2Pos = prov2Pos.north();
        helper.setBlock(chest1Pos, Blocks.CHEST);
        helper.setBlock(chest2Pos, Blocks.CHEST);

        PatternData smeltIron = new PatternData(false,
                List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT)));
        PatternProviderBlockEntity p1 = helper.getBlockEntity(prov1Pos, PatternProviderBlockEntity.class);
        PatternProviderBlockEntity p2 = helper.getBlockEntity(prov2Pos, PatternProviderBlockEntity.class);
        p1.getContainer().setItem(0, card(smeltIron));
        p2.getContainer().setItem(0, card(smeltIron));
        p1.setBlocking(true);
        p2.setBlocking(true);

        GlobalPos gp1 = GlobalPos.of(level.dimension(), helper.absolutePos(prov1Pos));
        GlobalPos gp2 = GlobalPos.of(level.dimension(), helper.absolutePos(prov2Pos));
        ItemKey ingotKey = ItemKey.of(new ItemStack(Items.IRON_INGOT));

        helper.runAfterDelay(5, () -> {
            GolemNetwork n = net(helper, bay);
            n.insertItem(new ItemStack(Items.RAW_IRON, 3), false);
            var started = n.startCrafting(Map.of(ingotKey, 3L), null, null);
            helper.assertTrue(started.type() == GolemNetwork.StartType.STARTED, "3x iron craft started");
        });

        helper.runAfterDelay(30, () -> {
            GolemNetwork n = net(helper, bay);
            ChestBlockEntity c1 = helper.getBlockEntity(chest1Pos, ChestBlockEntity.class);
            ChestBlockEntity c2 = helper.getBlockEntity(chest2Pos, ChestBlockEntity.class);
            helper.assertTrue(c1.countItem(Items.RAW_IRON) == 1 && c2.countItem(Items.RAW_IRON) == 1,
                    "both providers pushed 1 raw iron each, got c1=" + c1.countItem(Items.RAW_IRON) + " c2=" + c2.countItem(Items.RAW_IRON));
            helper.assertTrue(n.providerBusy(gp1) && n.providerBusy(gp2), "both blocking providers are busy");

            // Return 1 iron ingot specifically through provider 2 -> provider 2 must be freed, NOT provider 1
            p2.getReturns().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        });

        helper.runAfterDelay(65, () -> {
            GolemNetwork n = net(helper, bay);
            ChestBlockEntity c1 = helper.getBlockEntity(chest1Pos, ChestBlockEntity.class);
            ChestBlockEntity c2 = helper.getBlockEntity(chest2Pos, ChestBlockEntity.class);
            helper.assertTrue(c1.countItem(Items.RAW_IRON) == 1,
                    "provider 1 must remain busy and NOT receive batch 3, c1=" + c1.countItem(Items.RAW_IRON));
            helper.assertTrue(c2.countItem(Items.RAW_IRON) == 2,
                    "provider 2 must settle its own batch and immediately accept batch 3, c2=" + c2.countItem(Items.RAW_IRON));
            helper.assertTrue(n.providerBusy(gp1) && n.providerBusy(gp2), "both providers busy again (p1 original, p2 batch 3)");

            // Return batch 3 through provider 2 -> provider 2 becomes free while provider 1 still waits
            p2.getReturns().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        });

        helper.runAfterDelay(85, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(!n.providerBusy(gp2) && n.providerBusy(gp1),
                    "after returning through p2, p2 must be free and p1 still busy");
            p1.getReturns().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
        });

        helper.runAfterDelay(105, () -> {
            GolemNetwork n = net(helper, bay);
            helper.assertTrue(!n.providerBusy(gp1) && !n.providerBusy(gp2), "both providers idle after final return");
            helper.assertTrue(n.itemCountOf(ingotKey) == 3L, "all 3 iron ingots stored, got " + n.itemCountOf(ingotKey));
            helper.succeed();
        });
    }

    /**
     * P2 + Perf: Two Periscopes on both halves of a Cryo-Mixer must not double-count its output tray; two Periscopes
     * on two faces of one Copper Tank must not double-count water; two Periscopes on distinct chests take the fast path.
     */
    public static void auditCryoMixerAndSharedTankDedup(GameTestHelper helper) {
        buildArray(helper, 0, 0);
        powered(helper);
        var level = helper.getLevel();

        // East pipe branch: (7,1,6) -> Periscopes facing EAST at (8,1,6) and (8,2,6) targeting both halves of a Cryo-Mixer at (9,1,6)
        put(helper, new BlockPos(7, 1, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(7, 2, 6), ModBlocks.BASIC_SNOW_PIPE.get());
        periscope(helper, new BlockPos(8, 1, 6), Direction.EAST);
        periscope(helper, new BlockPos(8, 2, 6), Direction.EAST);
        BlockPos mixerBase = new BlockPos(9, 1, 6);
        CryoMixerBlock.place(level, helper.absolutePos(mixerBase), Direction.NORTH, ModBlocks.CRYO_MIXER.get());
        CryoMixerBlockEntity mixer = helper.getBlockEntity(mixerBase, CryoMixerBlockEntity.class);
        mixer.getOutput().setItem(0, new ItemStack(Items.ICE, 20));

        // North branch from NW corner bushing (4,1,4): Copper Tank at (3,1,2) with two Periscopes on SOUTH (3,1,3) and EAST (4,1,2)
        put(helper, new BlockPos(3, 1, 4), ModBlocks.BASIC_SNOW_PIPE.get());
        put(helper, new BlockPos(4, 1, 3), ModBlocks.BASIC_SNOW_PIPE.get());
        periscope(helper, new BlockPos(3, 1, 3), Direction.NORTH);
        periscope(helper, new BlockPos(4, 1, 2), Direction.WEST);
        BlockPos tankPos = new BlockPos(3, 1, 2);
        helper.setBlock(tankPos, ModBlocks.COPPER_TANK.get().defaultBlockState());
        ResourceHandler<FluidResource> tankCap = level.getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(tankPos), Direction.UP);
        helper.assertTrue(tankCap != null, "copper tank capability present");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(tankCap.insert(FluidResource.of(Fluids.WATER), 1000, tx) == 1000, "inserted 1000 mB water");
            tx.commit();
        }

        ItemKey iceKey = ItemKey.of(new ItemStack(Items.ICE));
        helper.runAfterDelay(20, () -> {
            GolemNetwork n = GolemNetwork.refresh(level, helper.absolutePos(ORIGIN));
            helper.assertTrue(n.isOnline(), "network online");

            // Cryo-Mixer two halves: 20 ice visible, 20 sim-extractable, canonical skipTarget, 20 real-extractable
            helper.assertTrue(n.listItems().getOrDefault(iceKey, 0L) == 20L,
                    "Cryo-Mixer listItems must show 20 ice across both halves, got " + n.listItems().getOrDefault(iceKey, 0L));
            helper.assertTrue(n.itemCountOf(iceKey) == 20L,
                    "Cryo-Mixer itemCountOf must show 20 ice, got " + n.itemCountOf(iceKey));
            helper.assertTrue(n.extractItem(new ItemStack(Items.ICE), 40, true).getCount() == 20,
                    "Cryo-Mixer sim extract(40) must return 20");
            helper.assertTrue(n.extractItem(new ItemStack(Items.ICE), 20, true, helper.absolutePos(mixerBase.above())).isEmpty(),
                    "skipTarget on Cryo-Mixer top half must skip bottom half too");
            helper.assertTrue(n.extractItem(new ItemStack(Items.ICE), 40, false).getCount() == 20
                            && n.itemCountOf(iceKey) == 0L,
                    "Cryo-Mixer real extract(40) takes exact 20 ice and empties tray");

            // Shared Copper Tank on two faces: 1000 mB water, not 2000 mB
            helper.assertTrue(n.fluidCountOf(Fluids.WATER) == 1000L,
                    "shared Copper Tank must report 1000 mB water across 2 faces, got " + n.fluidCountOf(Fluids.WATER));
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
