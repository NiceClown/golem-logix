package com.golemlogix.gametest;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.items.ForgeHammerItem;
import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.recipe.AnvilCrushingRecipe;
import com.golemlogix.system.EndRodRitual;
import com.golemlogix.system.Recipes;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests covering the 2.3.0 economy updates:
 * - Iron Golem anvil crushing +33% bonus second plate
 * - 3-phase End Rod Cryo-Forging ritual on the unassembled Mana Saturator anvil (including ice checks, melt tiers, reverse reagent order, and Shift+RMB refund)
 * - Chronometer 64-block local range vs Cryo-Source Telegraph 200-block local range
 */
public final class BalanceAndRitualTests {
    private BalanceAndRitualTests() {
    }

    public static void ironGolemAnvilBonusChance(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var ironHolder = server.getRecipeManager().byKey(
                Recipes.key(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "anvil_crushing/flattened_iron_core")));
        helper.assertTrue(ironHolder.isPresent(), "flattened_iron_core anvil crushing recipe must exist");
        AnvilCrushingRecipe ironRecipe = (AnvilCrushingRecipe) ironHolder.get().value();
        helper.assertTrue(Math.abs(ironRecipe.bonusChance() - 0.33f) < 1e-6f,
                "Iron golem anvil recipe bonusChance must be 0.33, got " + ironRecipe.bonusChance());

        int ones = 0, twos = 0;
        RandomSource rng = RandomSource.create(42L);
        for (int i = 0; i < 300; i++) {
            ItemStack roll = ironRecipe.rollResult(rng);
            helper.assertTrue(roll.is(ModItems.FLATTENED_IRON_CORE.get()), "roll result must be flattened_iron_core");
            if (roll.getCount() == 1) {
                ones++;
            } else if (roll.getCount() == 2) {
                twos++;
            } else {
                helper.fail("unexpected roll count " + roll.getCount());
            }
        }
        helper.assertTrue(ones > 150 && twos > 60,
                "seeded rolls over 300 trials should yield ~67% single (" + ones + ") and ~33% double (" + twos + ")");

        var copperHolder = server.getRecipeManager().byKey(
                Recipes.key(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "anvil_crushing/flattened_copper_matrix")));
        helper.assertTrue(copperHolder.isPresent(), "flattened_copper_matrix recipe must exist");
        AnvilCrushingRecipe copperRecipe = (AnvilCrushingRecipe) copperHolder.get().value();
        helper.assertTrue(copperRecipe.bonusChance() == 0f, "copper recipe bonusChance must default to 0");
        helper.assertTrue(copperRecipe.rollResult(rng).getCount() == 1, "copper recipe always yields 1");

        helper.succeed();
    }

    public static void endRodThreePhaseRitual(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = GolemGameTests.testPlayer(helper);

        // Anvil 1 at (3, 2, 3): full 3-phase ritual
        BlockPos anvil1 = new BlockPos(3, 2, 3);
        helper.setBlock(anvil1, ModBlocks.MANA_SATURATOR.get().defaultBlockState());
        helper.setBlock(anvil1.north(), Blocks.BLUE_ICE);
        helper.setBlock(anvil1.south(), Blocks.BLUE_ICE);
        helper.setBlock(anvil1.east(), Blocks.BLUE_ICE);
        // Leave west intentionally open to test NEED_ICE

        ManaSaturatorBlockEntity sat1 = helper.getBlockEntity(anvil1, ManaSaturatorBlockEntity.class);
        ItemStack hammer1 = new ItemStack(ModItems.FORGE_HAMMER.get());
        ForgeHammerItem.setCharge(hammer1, ForgeHammerItem.MAX_CHARGE); // 4000 Source = 40 strikes

        // Start ritual with Diamond Block
        sat1.startRodRitual(new ItemStack(Items.DIAMOND_BLOCK));
        helper.assertTrue(sat1.rodStage() == EndRodRitual.PHASE_1_DIAMOND, "started in PHASE_1_DIAMOND");

        // Missing West Blue Ice blocks striking
        helper.assertTrue(sat1.strike(level, helper.absolutePos(anvil1), player, hammer1) == ManaSaturatorBlockEntity.Strike.NEED_ICE,
                "strike without all 4 N/S/E/W Blue Ice must return NEED_ICE");

        // Place 4th Blue Ice block
        helper.setBlock(anvil1.west(), Blocks.BLUE_ICE);

        // Anvil 2 at (8, 2, 3): tests Shift+RMB mid-ritual cancellation & refund
        BlockPos anvil2 = new BlockPos(8, 2, 3);
        helper.setBlock(anvil2, ModBlocks.MANA_SATURATOR.get().defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            helper.setBlock(anvil2.relative(d), Blocks.BLUE_ICE);
        }
        ManaSaturatorBlockEntity sat2 = helper.getBlockEntity(anvil2, ManaSaturatorBlockEntity.class);
        ItemStack hammer2 = new ItemStack(ModItems.FORGE_HAMMER.get());
        ForgeHammerItem.setCharge(hammer2, ForgeHammerItem.MAX_CHARGE);
        sat2.startRodRitual(new ItemStack(Items.DIAMOND_BLOCK));

        int[] phase1Hits = {0};
        int[] phase2Hits = {0};
        int[] phase3Hits = {0};
        int[] anvil2Hits = {0};
        boolean[] anvil2Verified = {false};
        boolean[] finished = {false};

        helper.onEachTick(() -> {
            if (finished[0]) {
                return;
            }
            long t = level.getGameTime();
            // Step Anvil 2 through Phase 1 + 1 Eye -> Shift+RMB cancel test
            if (!anvil2Verified[0] && t % 7 == 0) {
                if (anvil2Hits[0] < EndRodRitual.HITS_PHASE_1) {
                    sat2.strike(level, helper.absolutePos(anvil2), player, hammer2);
                    anvil2Hits[0]++;
                } else {
                    helper.assertTrue(sat2.rodStage() == EndRodRitual.WAIT_EYES, "anvil2 reached WAIT_EYES");
                    var eyeStep = sat2.tryInsertRodReagent(new ItemStack(Items.ENDER_EYE, 1), false);
                    helper.assertTrue(eyeStep.accepted() && sat2.rodEyes() == 1, "anvil2 accepted 1st eye");
                    // Test Shift+RMB empty hand via Block useWithoutItem
                    player.getInventory().clearContent();
                    player.setShiftKeyDown(true);
                    BlockPos abs2 = helper.absolutePos(anvil2);
                    helper.getBlockState(anvil2).useWithoutItem(level, player,
                            new BlockHitResult(Vec3.atCenterOf(abs2), Direction.UP, abs2, false));
                    player.setShiftKeyDown(false);
                    helper.assertTrue(sat2.rodStage() == EndRodRitual.NONE && sat2.anvilItem().isEmpty(),
                            "anvil2 cleared after Shift+RMB cancel");
                    helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_BLOCK) == 1
                                    && player.getInventory().countItem(Items.ENDER_EYE) == 1,
                            "Shift+RMB cancel refunded Diamond Block + 1 Eye of Ender");
                    anvil2Verified[0] = true;
                }
            }

            // Step Anvil 1 through all 3 phases (every 7 ticks to respect MANUAL_COOLDOWN=6)
            if (t % 7 != 1) {
                return;
            }
            int stage = sat1.rodStage();
            if (stage == EndRodRitual.PHASE_1_DIAMOND) {
                var r = sat1.strike(level, helper.absolutePos(anvil1), player, hammer1);
                phase1Hits[0]++;
                if (phase1Hits[0] < EndRodRitual.HITS_PHASE_1) {
                    helper.assertTrue(r == ManaSaturatorBlockEntity.Strike.HIT, "phase1 strike -> HIT");
                } else {
                    helper.assertTrue(r == ManaSaturatorBlockEntity.Strike.ROD_PHASE_DONE, "phase1 final -> ROD_PHASE_DONE");
                    helper.assertTrue(sat1.rodStage() == EndRodRitual.WAIT_EYES, "transitioned to WAIT_EYES");
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        helper.assertBlockPresent(Blocks.PACKED_ICE, anvil1.relative(d));
                    }
                    // Striking during WAIT_EYES must return WAIT_REAGENT
                    helper.assertTrue(sat1.strike(level, helper.absolutePos(anvil1), player, hammer1)
                            == ManaSaturatorBlockEntity.Strike.WAIT_REAGENT, "WAIT_EYES strike -> WAIT_REAGENT");
                    // Feed 2 Eyes of Ender
                    var eyes = sat1.tryInsertRodReagent(new ItemStack(Items.ENDER_EYE, 2), false);
                    helper.assertTrue(eyes.accepted() && eyes.newStage() == EndRodRitual.PHASE_2_VOID,
                            "2 Eyes advance WAIT_EYES -> PHASE_2_VOID");
                }
            } else if (stage == EndRodRitual.PHASE_2_VOID) {
                var r = sat1.strike(level, helper.absolutePos(anvil1), player, hammer1);
                phase2Hits[0]++;
                if (phase2Hits[0] < EndRodRitual.HITS_PHASE_2) {
                    helper.assertTrue(r == ManaSaturatorBlockEntity.Strike.HIT, "phase2 strike -> HIT");
                } else {
                    helper.assertTrue(r == ManaSaturatorBlockEntity.Strike.ROD_PHASE_DONE, "phase2 final -> ROD_PHASE_DONE");
                    helper.assertTrue(sat1.rodStage() == EndRodRitual.WAIT_TEMPER, "transitioned to WAIT_TEMPER");
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        helper.assertBlockPresent(Blocks.ICE, anvil1.relative(d));
                    }
                    // Feed Netherite first, then 2 Ghast Tears (order-independent)
                    var neth = sat1.tryInsertRodReagent(new ItemStack(Items.NETHERITE_INGOT, 1), false);
                    helper.assertTrue(neth.accepted() && neth.newStage() == EndRodRitual.WAIT_TEMPER,
                            "Netherite accepted, waiting for tears");
                    var tears = sat1.tryInsertRodReagent(new ItemStack(Items.GHAST_TEAR, 2), false);
                    helper.assertTrue(tears.accepted() && tears.newStage() == EndRodRitual.PHASE_3_QUENCH,
                            "2 Tears complete WAIT_TEMPER -> PHASE_3_QUENCH");
                }
            } else if (stage == EndRodRitual.PHASE_3_QUENCH) {
                var r = sat1.strike(level, helper.absolutePos(anvil1), player, hammer1);
                phase3Hits[0]++;
                if (phase3Hits[0] < EndRodRitual.HITS_PHASE_3) {
                    helper.assertTrue(r == ManaSaturatorBlockEntity.Strike.HIT, "phase3 strike -> HIT");
                } else {
                    helper.assertTrue(r == ManaSaturatorBlockEntity.Strike.ROD_DONE, "phase3 final -> ROD_DONE");
                    helper.assertTrue(sat1.rodStage() == EndRodRitual.NONE, "ritual ended cleanly");
                    helper.assertTrue(sat1.anvilItem().is(Items.END_ROD) && sat1.anvilItem().getCount() == 1,
                            "anvil now holds 1x End Rod, got " + sat1.anvilItem());
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        helper.assertBlockPresent(Blocks.WATER, anvil1.relative(d));
                    }
                    if (SourceHelper.sourceRequired()) {
                        helper.assertTrue(ForgeHammerItem.getCharge(hammer1) == 0,
                                "all 4000 Source spent across 40 strikes, left " + ForgeHammerItem.getCharge(hammer1));
                    }
                    finished[0] = true;
                    if (anvil2Verified[0]) {
                        helper.succeed();
                    }
                }
            } else if (anvil2Verified[0] && finished[0]) {
                helper.succeed();
            }
        });
    }

    public static void chronometerVsTelegraphRange(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos anchor = BlockPos.ZERO;
        helper.assertTrue(GolemConfig.LOCAL_WIRELESS_RANGE.get() == 64,
                "Chronometer default local range must be 64, got " + GolemConfig.LOCAL_WIRELESS_RANGE.get());
        helper.assertTrue(GolemConfig.LOCAL_TELEGRAPH_RANGE.get() == 200,
                "Telegraph default local range must be 200, got " + GolemConfig.LOCAL_TELEGRAPH_RANGE.get());

        // Chronometer (5-arg): passes at 60 blocks, fails at 70 blocks, Dimension tier (1) passes at 70
        helper.assertTrue(PortableChronometerItem.inRange(0, level, new Vec3(60.5, 0.5, 0.5), level, anchor),
                "Chronometer local tier works at 60 blocks");
        helper.assertTrue(!PortableChronometerItem.inRange(0, level, new Vec3(70.5, 0.5, 0.5), level, anchor),
                "Chronometer local tier rejects 70 blocks (> 64)");
        helper.assertTrue(PortableChronometerItem.inRange(1, level, new Vec3(70.5, 0.5, 0.5), level, anchor),
                "Chronometer dimension tier reaches 70+ blocks");

        // Telegraph (6-arg with LOCAL_TELEGRAPH_RANGE=200): passes at 150 blocks, fails at 210
        int telRange = GolemConfig.LOCAL_TELEGRAPH_RANGE.get();
        helper.assertTrue(PortableChronometerItem.inRange(0, level, new Vec3(150.5, 0.5, 0.5), level, anchor, telRange),
                "Telegraph tier 0 reaches 150 blocks");
        helper.assertTrue(!PortableChronometerItem.inRange(0, level, new Vec3(210.5, 0.5, 0.5), level, anchor, telRange),
                "Telegraph tier 0 rejects 210 blocks (> 200)");

        helper.succeed();
    }
}
