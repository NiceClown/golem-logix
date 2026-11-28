package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.AtlasCasingBlock;
import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.BusLinks;
import com.golemlogix.blocks.CopperDriveBayBlock;
import com.golemlogix.blocks.CryoMixerBlock;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.blocks.StockManometerBlock;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.menu.SecurityTerminalMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.recipe.CopperTankJoinRecipe;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Recipes;
import com.golemlogix.system.SourceHelper;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Regression GameTests for blocks/, items/, event/, recipe/, init/, data/, and data pack recipes.
 * Self-registers when GameTests are enabled without touching GolemGameTests.java or GolemGameTestRegistry.java.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class BlocksRegressionTests {
    private record TestSpec(String name, Consumer<GameTestHelper> body, int maxTicks) {
    }

    private static final List<TestSpec> TESTS = List.of(
            new TestSpec("blocks_security_terminal_owner_only", BlocksRegressionTests::securityTerminalOwnerOnly, 60),
            new TestSpec("blocks_chronometer_security_and_lost", BlocksRegressionTests::chronometerSecurityAndLost, 100),
            new TestSpec("blocks_stock_manometer_direct_signal_behind", BlocksRegressionTests::stockManometerDirectSignalBehind, 100),
            new TestSpec("blocks_saturator_give_back_no_dupe", BlocksRegressionTests::saturatorGiveBackNoDupe, 60),
            new TestSpec("blocks_rotated_shapes_and_transforms", BlocksRegressionTests::rotatedShapesAndTransforms, 40),
            new TestSpec("blocks_anvil_crushing_ignores_non_anvil_damage", BlocksRegressionTests::anvilCrushingIgnoresNonAnvilDamage, 60),
            new TestSpec("blocks_multiblock_tool_drop_and_pattern_needs", BlocksRegressionTests::multiblockToolDropAndPatternNeeds, 60),
            new TestSpec("blocks_all_core_recipes_load", BlocksRegressionTests::allCoreRecipesLoad, 40)
    );

    private BlocksRegressionTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }

    @SubscribeEvent
    public static void onRegisterFunctions(RegisterEvent event) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (TestSpec t : TESTS) {
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
                id("blocks_regression"), new TestEnvironmentDefinition.AllOf(List.of()));
        for (TestSpec t : TESTS) {
            ResourceKey<Consumer<GameTestHelper>> fn = ResourceKey.create(Registries.TEST_FUNCTION, id(t.name()));
            event.registerTest(id(t.name()), new FunctionGameTestInstance(fn, new TestData<>(env, id("empty"), t.maxTicks(), 0, true)));
        }
    }

    /** FakePlayer that records openMenu calls and overlay messages (stock FakePlayer drops openMenu). */
    private static final class CapturingPlayer extends FakePlayer {
        boolean openedMenu;
        @Nullable
        AbstractContainerMenu lastMenu;
        @Nullable
        Component lastOverlay;

        CapturingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public OptionalInt openMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
            openedMenu = true;
            lastMenu = provider.createMenu(1, getInventory(), this);
            return OptionalInt.of(1);
        }

        @Override
        public void sendOverlayMessage(Component message) {
            lastOverlay = message;
        }

        void resetCapture() {
            openedMenu = false;
            lastMenu = null;
            lastOverlay = null;
        }
    }

    private static CapturingPlayer fakePlayer(GameTestHelper helper, String name) {
        CapturingPlayer p = new CapturingPlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        GameType.SURVIVAL.updatePlayerAbilities(p.getAbilities());
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        p.snapTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0f, 0f);
        return p;
    }

    private static String overlayKey(@Nullable Component c) {
        return c != null && c.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
    }

    private static BlockPos buildSimpleNetwork(GameTestHelper helper, BlockPos origin) {
        // Lone core (links down) -> dense pipe below -> creative source jar to west, drive bay to east (facing SOUTH so it grows east)
        BlockPos core = origin.above();
        helper.setBlock(core, ModBlocks.ATLAS_CORE.get().defaultBlockState());
        helper.setBlock(origin, ModBlocks.DENSE_FROST_PIPE.get().defaultBlockState());
        helper.setBlock(origin.west(), ModBlocks.CREATIVE_SOURCE_JAR.get().defaultBlockState());
        CopperDriveBayBlock.place(helper.getLevel(), helper.absolutePos(origin.east()), Direction.SOUTH);
        CopperDriveBayBlockEntity bay = helper.getBlockEntity(origin.east(), CopperDriveBayBlockEntity.class);
        bay.getContainer().setItem(0, new ItemStack(ModItems.COPPER_DISK_1K.get()));
        return core;
    }

    /** Security Terminal right-click must allow the owner and deny non-owner non-OP strangers. */
    private static void securityTerminalOwnerOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        SecurityTerminalBlockEntity be = helper.getBlockEntity(pos, SecurityTerminalBlockEntity.class);

        CapturingPlayer owner = fakePlayer(helper, "sec-owner");
        CapturingPlayer stranger = fakePlayer(helper, "sec-stranger");
        be.setOwner(owner);

        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        BlockState state = helper.getLevel().getBlockState(abs);

        state.useWithoutItem(helper.getLevel(), stranger, hit);
        helper.assertTrue(!stranger.openedMenu && stranger.lastMenu == null,
                "stranger must not open SecurityTerminalMenu");
        helper.assertTrue("message.golem_logix.security_denied".equals(overlayKey(stranger.lastOverlay)),
                "stranger must receive security_denied overlay message");

        state.useWithoutItem(helper.getLevel(), owner, hit);
        helper.assertTrue(owner.openedMenu && owner.lastMenu instanceof SecurityTerminalMenu,
                "owner must be able to open SecurityTerminalMenu");
        helper.succeed();
    }

    /** Portable Chronometer must enforce Security Terminal permissions and report 'lost' for missing cross-dim cores on Tier 2. */
    private static void chronometerSecurityAndLost(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 1, 3);
        BlockPos core = buildSimpleNetwork(helper, origin);
        BlockPos secPos = origin.north();
        helper.setBlock(secPos, ModBlocks.SECURITY_TERMINAL.get().defaultBlockState());
        SecurityTerminalBlockEntity sec = helper.getBlockEntity(secPos, SecurityTerminalBlockEntity.class);

        CapturingPlayer owner = fakePlayer(helper, "chrono-owner");
        CapturingPlayer stranger = fakePlayer(helper, "chrono-stranger");
        CapturingPlayer cardHolder = fakePlayer(helper, "chrono-card");
        sec.setOwner(owner);
        sec.getCards().setItem(0, IdentityPunchCardItem.of(cardHolder.getUUID(), cardHolder.getScoreboardName(), SecurityPermission.INSERT.mask));

        BlockPos absCore = helper.absolutePos(core);
        ItemStack chrono = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
        chrono.set(ModDataComponents.BOUND_CORE.get(), GlobalPos.of(helper.getLevel().dimension(), absCore));
        PortableChronometerItem.setCharge(chrono, PortableChronometerItem.MAX_CHARGE);

        helper.runAfterDelay(25, () -> {
            stranger.setPos(absCore.getX() + 0.5, absCore.getY() + 1, absCore.getZ() + 0.5);
            cardHolder.setPos(absCore.getX() + 0.5, absCore.getY() + 1, absCore.getZ() + 0.5);
            owner.setPos(absCore.getX() + 0.5, absCore.getY() + 1, absCore.getZ() + 0.5);

            int beforeCharge = PortableChronometerItem.getCharge(chrono);
            boolean strangerOpened = PortableChronometerItem.open(stranger, chrono, 0);
            helper.assertTrue(!strangerOpened && !stranger.openedMenu,
                    "unauthorized player must not open Chronometer on secured network");
            helper.assertTrue(PortableChronometerItem.getCharge(chrono) == beforeCharge,
                    "denied Chronometer open must not drain Source charge");

            boolean cardOpened = PortableChronometerItem.open(cardHolder, chrono, 0);
            helper.assertTrue(cardOpened && cardHolder.lastMenu instanceof TerminalMenu,
                    "player with INSERT card must be able to open Chronometer");

            boolean ownerOpened = PortableChronometerItem.open(owner, chrono, 0);
            helper.assertTrue(ownerOpened && owner.lastMenu instanceof TerminalMenu,
                    "owner must be able to open Chronometer");

            // Tier 2 cross-dimensional Chronometer bound to an unloaded/missing Nether pos must say 'lost', not 'other_dimension'
            ItemStack tier2 = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
            tier2.set(ModDataComponents.WIRELESS_TIER.get(), 2);
            tier2.set(ModDataComponents.BOUND_CORE.get(), GlobalPos.of(Level.NETHER, new BlockPos(99999, 64, 99999)));
            PortableChronometerItem.setCharge(tier2, PortableChronometerItem.MAX_CHARGE);
            owner.resetCapture();
            boolean lostOpened = PortableChronometerItem.open(owner, tier2, 0);
            helper.assertTrue(!lostOpened && "message.golem_logix.chronometer.lost".equals(overlayKey(owner.lastOverlay)),
                    "Tier 2 unloaded cross-dim core must report chronometer.lost, got: " + overlayKey(owner.lastOverlay));

            helper.succeed();
        });
    }

    /** Stock Manometer must emit direct (strong) power into the block it is attached to (behind it, FACING.getOpposite()). */
    private static void stockManometerDirectSignalBehind(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 1, 3);
        buildSimpleNetwork(helper, origin);

        BlockPos manoPos = origin.north();
        // Facing NORTH: attached block behind it is SOUTH? Wait: origin is at SOUTH of manoPos!
        // Put manometer at origin.north(), facing WEST -> attached solid stone at manoPos.east(), lamp at manoPos.east(2).
        helper.setBlock(manoPos, ModBlocks.STOCK_MANOMETER.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.WEST));
        BlockPos attachedStone = manoPos.east();
        BlockPos lampBehind = manoPos.east(2);
        helper.setBlock(attachedStone, Blocks.STONE.defaultBlockState());
        helper.setBlock(lampBehind, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));

        StockManometerBlockEntity mano = helper.getBlockEntity(manoPos, StockManometerBlockEntity.class);
        mano.getFilter().setItem(0, new ItemStack(Items.DIAMOND));
        mano.setMode(StockManometerBlockEntity.MODE_ABOVE);
        mano.setThreshold(5);

        helper.runAfterDelay(25, () -> {
            GolemNetwork.get(helper.getLevel(), helper.absolutePos(origin)).insertItem(new ItemStack(Items.DIAMOND, 10), false);
            helper.runAfterDelay(15, () -> {
                ServerLevel level = helper.getLevel();
                BlockPos absMano = helper.absolutePos(manoPos);
                BlockPos absStone = helper.absolutePos(attachedStone);
                BlockState manoState = level.getBlockState(absMano);
                helper.assertTrue(mano.getSignal() == 15, "manometer signal is 15");
                helper.assertTrue(manoState.getDirectSignal(level, absMano, Direction.WEST) == 15,
                        "direct signal queried from attached side (direction == FACING) must be 15");
                helper.assertTrue(manoState.getDirectSignal(level, absMano, Direction.EAST) == 0,
                        "direct signal on front face (opposite) must be 0");
                helper.assertTrue(level.getDirectSignalTo(absStone) == 15,
                        "solid block behind manometer must receive strong power 15");
                helper.assertTrue(helper.getBlockState(lampBehind).getValue(RedstoneLampBlock.LIT),
                        "redstone lamp beyond attached solid block must light from strong power");
                helper.succeed();
            });
        });
    }

    /** Taking from a Mana Saturator deck when player inventory only has partial space must not duplicate items. */
    private static void saturatorGiveBackNoDupe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.MANA_SATURATOR.get().defaultBlockState()
                .setValue(ManaSaturatorBlock.FACING, Direction.NORTH)
                .setValue(ManaSaturatorBlock.FORMED, true));
        ManaSaturatorBlockEntity sat = helper.getBlockEntity(pos, ManaSaturatorBlockEntity.class);
        for (int i = 0; i < 10; i++) {
            sat.addToDeck(new ItemStack(Items.IRON_BLOCK, 1));
        }

        ServerPlayer player = fakePlayer(helper, "sat-dupe-test");
        BlockPos abs = helper.absolutePos(pos);
        player.setPos(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5);
        // Fill all 36 main inventory slots so slot 0 has room for only 4 iron blocks
        player.getInventory().setItem(0, new ItemStack(Items.IRON_BLOCK, 60));
        for (int i = 1; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player, hit);

        int inInv = player.getInventory().getItem(0).getCount() - 60;
        int onGround = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3.0)).stream()
                .filter(e -> e.getItem().is(Items.IRON_BLOCK))
                .mapToInt(e -> e.getItem().getCount())
                .sum();
        helper.assertTrue(inInv == 4, "expected 4 iron blocks added to inventory, got " + inInv);
        helper.assertTrue(onGround == 6, "expected 6 leftover iron blocks dropped (no dupe), got " + onGround);
        helper.succeed();
    }

    /** Unformed Saturator and upper Cryo-Mixer shapes must rotate by FACING; SnowPipe and BusLinks must rotate links accurately. */
    private static void rotatedShapesAndTransforms(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos p = helper.absolutePos(new BlockPos(2, 1, 2));

        BlockState satNorth = ModBlocks.MANA_SATURATOR.get().defaultBlockState()
                .setValue(ManaSaturatorBlock.FACING, Direction.NORTH).setValue(ManaSaturatorBlock.FORMED, false);
        BlockState satEast = satNorth.setValue(ManaSaturatorBlock.FACING, Direction.EAST);
        VoxelShape sn = satNorth.getShape(level, p);
        VoxelShape se = satEast.getShape(level, p);
        helper.assertTrue(!sn.bounds().equals(se.bounds()), "unformed ManaSaturator shape must rotate for EAST vs NORTH");
        helper.assertTrue(Math.abs(se.min(Direction.Axis.X) - 2.5 / 16.0) < 1e-4 && Math.abs(se.min(Direction.Axis.Z)) < 1e-4,
                "EAST ManaSaturator compact shape must span Z 0..1 and X 2.5..13.5");

        BlockState mixBase = ModBlocks.CRYO_MIXER.get().defaultBlockState().setValue(CryoMixerBlock.SECTION, 1);
        VoxelShape mn = mixBase.setValue(CryoMixerBlock.FACING, Direction.NORTH).getShape(level, p);
        VoxelShape me = mixBase.setValue(CryoMixerBlock.FACING, Direction.EAST).getShape(level, p);
        VoxelShape ms = mixBase.setValue(CryoMixerBlock.FACING, Direction.SOUTH).getShape(level, p);
        helper.assertTrue(Shapes.joinIsNotEmpty(mn, me, BooleanOp.NOT_SAME)
                        && Shapes.joinIsNotEmpty(mn, ms, BooleanOp.NOT_SAME),
                "CryoMixer upper shape must rotate with FACING");

        BlockState pipeEast = ModBlocks.BASIC_SNOW_PIPE.get().defaultBlockState().setValue(SnowPipeBlock.EAST, SnowPipeBlock.Link.PIPE);
        BlockState pipeRot = pipeEast.rotate(Rotation.CLOCKWISE_90);
        helper.assertTrue(pipeRot.getValue(SnowPipeBlock.SOUTH) == SnowPipeBlock.Link.PIPE
                        && pipeRot.getValue(SnowPipeBlock.EAST) == SnowPipeBlock.Link.NONE
                        && pipeRot.getValue(SnowPipeBlock.NORTH) == SnowPipeBlock.Link.NONE
                        && pipeRot.getValue(SnowPipeBlock.WEST) == SnowPipeBlock.Link.NONE,
                "SnowPipeBlock CLOCKWISE_90 must rotate EAST link to SOUTH");

        BlockState busEast = ModBlocks.VACUUM_IMPORT_BUS.get().defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH)
                .setValue(BlockStateProperties.EAST, true);
        BlockState busRot = BusLinks.rotate(busEast, Rotation.CLOCKWISE_90);
        helper.assertTrue(busRot.getValue(DirectionalBlock.FACING) == Direction.EAST
                        && busRot.getValue(BlockStateProperties.SOUTH)
                        && !busRot.getValue(BlockStateProperties.EAST),
                "BusLinks CLOCKWISE_90 must rotate FACING and side links together");

        BlockState cornerNW = ModBlocks.ATLAS_CASING.get().defaultBlockState()
                .setValue(AtlasCasingBlock.PART, AtlasCasingBlock.Part.TOP_CORNER)
                .setValue(AtlasCasingBlock.FACING, Direction.NORTH);
        helper.assertTrue(cornerNW.mirror(Mirror.FRONT_BACK).getValue(AtlasCasingBlock.FACING) == Direction.EAST,
                "AtlasCasing TOP_CORNER NORTH (NW) mirrored FRONT_BACK (flip X) must become EAST (NE)");
        helper.assertTrue(cornerNW.mirror(Mirror.LEFT_RIGHT).getValue(AtlasCasingBlock.FACING) == Direction.WEST,
                "AtlasCasing TOP_CORNER NORTH (NW) mirrored LEFT_RIGHT (flip Z) must become WEST (SW)");
        helper.succeed();
    }

    /** Non-anvil damage must not trigger the anvil golem ritual even when a falling anvil exists nearby. */
    private static void anvilCrushingIgnoresNonAnvilDamage(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 1, 4);
        IronGolem golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, at);
        golem.setHealth(3.0f);

        // Spawn a falling anvil 1 block to the side (within the old 1x2x1 AABB inflate), without dropping it on the golem
        BlockPos aside = helper.absolutePos(at.east().above());
        FallingBlockEntity fb = FallingBlockEntity.fall(helper.getLevel(), aside, Blocks.ANVIL.defaultBlockState());
        fb.setDeltaMovement(0, 0, 0);

        golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 0.5f);
        helper.assertTrue(!golem.isRemoved(), "generic damage near a falling anvil must not crush the golem");
        helper.assertItemEntityNotPresent(ModItems.FLATTENED_IRON_CORE.get(), at, 3.0);
        fb.discard();
        golem.discard();
        helper.succeed();
    }

    /** Multiblock tool check (Atlant/DriveBay), CopperTankJoinRecipe overflow/capacity check, and PatternData bounds/needs. */
    private static void multiblockToolDropAndPatternNeeds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 1. Atlant upper half broken without pickaxe in survival must not drop an Atlant
        BlockPos atlantBase = new BlockPos(2, 1, 2);
        BlockState lower = ModBlocks.ATLAS_ATLANT.get().defaultBlockState()
                .setValue(AtlantBlock.FACING, Direction.NORTH).setValue(AtlantBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upper = lower.setValue(AtlantBlock.HALF, DoubleBlockHalf.UPPER);
        helper.setBlock(atlantBase, lower);
        helper.setBlock(atlantBase.above(), upper);

        ServerPlayer bareHands = fakePlayer(helper, "bare-hands");
        BlockPos absUpper = helper.absolutePos(atlantBase.above());
        upper.getBlock().playerWillDestroy(level, absUpper, upper, bareHands);
        level.removeBlock(absUpper, false);
        helper.assertTrue(helper.getBlockState(atlantBase).isAir(), "Atlant lower half removed with upper");
        helper.assertItemEntityNotPresent(ModBlocks.ATLAS_ATLANT.get().asItem(), atlantBase, 3.0);

        // 1b. Atlant upper half broken WITH a diamond pickaxe in survival must drop exactly 1 Atlant
        helper.setBlock(atlantBase, lower);
        helper.setBlock(atlantBase.above(), upper);
        ServerPlayer miner = fakePlayer(helper, "pickaxe-miner");
        miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        upper.getBlock().playerWillDestroy(level, absUpper, upper, miner);
        level.removeBlock(absUpper, false);
        helper.assertTrue(helper.getBlockState(atlantBase).isAir(), "Atlant lower half removed with pickaxe");
        helper.assertItemEntityCountIs(ModBlocks.ATLAS_ATLANT.get().asItem(), atlantBase, 3.0, 1);

        // 1c. CopperDriveBay non-master part: bare hands drops 0, diamond pickaxe drops 1
        BlockPos bayMaster = new BlockPos(5, 1, 2);
        BlockPos absBayMaster = helper.absolutePos(bayMaster);
        BlockPos absBayTopRight = absBayMaster.east().above();
        CopperDriveBayBlock.place(level, absBayMaster, Direction.SOUTH);
        BlockState partState = level.getBlockState(absBayTopRight);
        partState.getBlock().playerWillDestroy(level, absBayTopRight, partState, bareHands);
        level.removeBlock(absBayTopRight, false);
        helper.assertTrue(helper.getBlockState(bayMaster).isAir(), "DriveBay master removed with non-master");
        helper.assertItemEntityNotPresent(ModBlocks.COPPER_DRIVE_BAY.get().asItem(), bayMaster, 3.0);

        CopperDriveBayBlock.place(level, absBayMaster, Direction.SOUTH);
        partState = level.getBlockState(absBayTopRight);
        partState.getBlock().playerWillDestroy(level, absBayTopRight, partState, miner);
        level.removeBlock(absBayTopRight, false);
        helper.assertTrue(helper.getBlockState(bayMaster).isAir(), "DriveBay master removed with pickaxe");
        helper.assertItemEntityCountIs(ModBlocks.COPPER_DRIVE_BAY.get().asItem(), bayMaster, 3.0, 1);

        // 2. CopperTankJoinRecipe rejects tanks whose combined fluid exceeds combined capacity
        ItemStack badTank1 = new ItemStack(ModBlocks.COPPER_TANK.get());
        badTank1.set(ModDataComponents.TANK_CAPACITY.get(), 1000);
        badTank1.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 2000)));
        ItemStack badTank2 = new ItemStack(ModBlocks.COPPER_TANK.get());
        badTank2.set(ModDataComponents.TANK_CAPACITY.get(), 1000);
        badTank2.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 2000)));
        CraftingInput badIn = CraftingInput.of(2, 1, List.of(badTank1, badTank2));
        helper.assertTrue(CopperTankJoinRecipe.join(badIn).isEmpty(),
                "CopperTankJoinRecipe must reject fluid exceeding total capacity");

        ItemStack ok1 = new ItemStack(ModBlocks.COPPER_TANK.get());
        ok1.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 4000)));
        ItemStack ok2 = new ItemStack(ModBlocks.COPPER_TANK.get());
        ok2.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 6000)));
        ItemStack joined = CopperTankJoinRecipe.join(CraftingInput.of(2, 1, List.of(ok1, ok2)));
        helper.assertTrue(!joined.isEmpty()
                        && joined.getOrDefault(ModDataComponents.TANK_CAPACITY.get(), 0) == CopperTankBlockEntity.CAPACITY * 2
                        && joined.getOrDefault(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.EMPTY).getAmount() == 10000,
                "CopperTankJoinRecipe must combine capacity and fluid cleanly");

        // 3. PatternData bounds check on matcher() and merging in needs()
        PatternData pd = new PatternData(false,
                List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.IRON_INGOT, 3)),
                List.of(new ItemStack(Items.IRON_BLOCK, 1)),
                List.of(Fuzzy.ANY_COMPONENTS, Fuzzy.ANY_COMPONENTS));
        helper.assertTrue(pd.matcher(-1, level) == null && pd.matcher(99, level) == null,
                "PatternData.matcher out-of-range indices must return null");
        List<PatternData.Need> needs = pd.needs(level);
        helper.assertTrue(needs.size() == 1 && needs.get(0).amount() == 5,
                "PatternData.needs must merge identical fuzzy inputs");
        helper.succeed();
    }

    /** Every core (non-optional-mod) recipe in data/golem_logix/recipe/ must load without syntax or reference errors. */
    private static void allCoreRecipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        String[] core = {
                "aetherial_linkage", "aether_resonance_core", "analog_terminal", "annihilation_bus",
                "atlas_atlant", "atlas_casing", "atlas_conduit", "atlas_core", "autocraft_module",
                "basic_snow_pipe", "cable_bushing", "channel_counter", "channel_expander",
                "chronometer_upgrade", "cooling_coprocessor",
                "copper_disk_1k", "copper_disk_4k", "copper_disk_16k", "copper_disk_64k", "copper_disk_256k",
                "copper_drive_bay", "copper_punch_card", "copper_tank", "copper_tank_join",
                "crafting_terminal", "cryo_calculator", "cryo_diode",
                "cryo_mainframe", "cryo_mixer", "cryo_source_telegraph",
                "dense_frost_pipe", "eternal_frost_resonator",
                "fluid_disk_1k", "fluid_disk_4k", "fluid_disk_16k", "fluid_disk_64k", "fluid_disk_256k",
                "forge_hammer", "formation_bus", "fuzzy_card", "golem_interface", "guide_book",
                "identity_punch_card", "injector_export_bus", "mana_saturator", "pattern_encoder",
                "pattern_provider", "periscope_storage_bus", "pipe_plug", "plasma_temper",
                "portable_chronometer", "punch_card_catalog", "quantum_channel_expander",
                "recipe_monitor", "redstone_module", "schematic_concentrator", "security_terminal",
                "stock_manometer", "stock_requester", "vacuum_import_bus", "void_glass_prism",
                "anvil_crushing/flattened_copper_matrix", "anvil_crushing/flattened_cryo_substrate", "anvil_crushing/flattened_iron_core",
                "saturator/compressed_coprocessor", "saturator/flattened_copper_matrix", "saturator/flattened_cryo_substrate",
                "saturator/flattened_iron_core", "saturator/interdimensional_occult_matrix"
        };
        for (String name : core) {
            helper.assertTrue(recipes.byKey(Recipes.key(id(name))).isPresent(), "missing or invalid recipe: " + name);
        }
        boolean distillAvailable = SourceHelper.distillationAvailable();
        boolean distillPresent = recipes.byKey(Recipes.key(id("cryo_distillation_tower"))).isPresent();
        helper.assertTrue(distillPresent == distillAvailable,
                "cryo_distillation_tower recipe presence (" + distillPresent + ") must match distillationAvailable (" + distillAvailable + ")");
        boolean jarExpected = distillAvailable || SourceHelper.thaumaturgeLoaded();
        boolean jarPresent = recipes.byKey(Recipes.key(id("cryo_source_jar"))).isPresent();
        helper.assertTrue(jarPresent == jarExpected,
                "cryo_source_jar recipe presence (" + jarPresent + ") must match neovitae/thaumaturge availability (" + jarExpected + ")");
        helper.succeed();
    }
}
