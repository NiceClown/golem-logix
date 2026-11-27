package com.golemlogix.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaumaturgeMechanicsTest {

    // --------------------------------------------------------- Vis Condenser

    @Test
    void visCondenserClampAndFloorThresholds() {
        // Percent mode clamps to [0, 95]
        assertEquals(0, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_PERCENT, -10));
        assertEquals(25, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_PERCENT, 25));
        assertEquals(95, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_PERCENT, 120));

        // Exact mode clamps to [0, 500]
        assertEquals(0, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_EXACT, -5));
        assertEquals(50, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_EXACT, 50));
        assertEquals(500, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_EXACT, 999));

        // Floor Vis calculation
        assertEquals(50.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_PERCENT, 25, 200), 1e-9);
        assertEquals(0.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_PERCENT, 25, 0), 1e-9);
        assertEquals(50.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_EXACT, 50, 200), 1e-9);
        assertEquals(50.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_EXACT, 50, 0), 1e-9);
    }

    @Test
    void visCondenserHysteresisPercentAndExact() {
        // Percent mode: base=200, floor=25% (50 Vis), hysteresis=+5% -> resume=30% (60 Vis)
        double pctFloor = VisCondenserMath.floorVis(VisCondenserMath.MODE_PERCENT, 25, 200);
        double pctResume = VisCondenserMath.resumeVis(VisCondenserMath.MODE_PERCENT, 25, 200, 5, 5);
        assertEquals(50.0, pctFloor, 1e-9);
        assertEquals(60.0, pctResume, 1e-9);

        // Above floor while unpaused -> runs
        assertFalse(VisCondenserMath.nextPausedState(false, 51.0, pctFloor, pctResume));
        // Hits or drops below floor -> pauses
        assertTrue(VisCondenserMath.nextPausedState(false, 50.0, pctFloor, pctResume));
        assertTrue(VisCondenserMath.nextPausedState(false, 49.0, pctFloor, pctResume));
        // Recovers above floor but still below resume -> stays paused
        assertTrue(VisCondenserMath.nextPausedState(true, 55.0, pctFloor, pctResume));
        assertTrue(VisCondenserMath.nextPausedState(true, 59.99, pctFloor, pctResume));
        // Reaches resume threshold -> unpauses
        assertFalse(VisCondenserMath.nextPausedState(true, 60.0, pctFloor, pctResume));
        assertFalse(VisCondenserMath.nextPausedState(true, 65.0, pctFloor, pctResume));

        // Percent resume caps at 100% of base when threshold=95% and hysteresis=+10%
        assertEquals(200.0, VisCondenserMath.resumeVis(VisCondenserMath.MODE_PERCENT, 95, 200, 10, 5), 1e-9);

        // Exact mode: floor=50 Vis, hysteresis=+5 Vis -> resume=55 Vis
        double exactFloor = VisCondenserMath.floorVis(VisCondenserMath.MODE_EXACT, 50, 200);
        double exactResume = VisCondenserMath.resumeVis(VisCondenserMath.MODE_EXACT, 50, 200, 5, 5);
        assertEquals(50.0, exactFloor, 1e-9);
        assertEquals(55.0, exactResume, 1e-9);
        assertTrue(VisCondenserMath.nextPausedState(false, 50.0, exactFloor, exactResume));
        assertTrue(VisCondenserMath.nextPausedState(true, 54.9, exactFloor, exactResume));
        assertFalse(VisCondenserMath.nextPausedState(true, 55.0, exactFloor, exactResume));
    }

    @Test
    void visCondenserImmediateUnpauseWhenThresholdLowered() {
        // Paused at 45 Vis when floor was 50 Vis -> lowering floor to 40 Vis clears pause immediately
        assertTrue(VisCondenserMath.shouldClearPauseOnThresholdChange(45.0, 40.0));
        // Lowering floor only down to 45 Vis (not strictly below current Vis) does NOT clear pause
        assertFalse(VisCondenserMath.shouldClearPauseOnThresholdChange(45.0, 45.0));
        // Raising floor above current Vis does NOT clear pause
        assertFalse(VisCondenserMath.shouldClearPauseOnThresholdChange(45.0, 60.0));
    }

    @Test
    void visCondenserLowVisFluxTriggerAndSafeZeroFlux() {
        // Base=200, lowVisPercent=10 -> danger zone <= 20.0 Vis
        assertFalse(VisCondenserMath.isLowVis(20.1, 200, 10));
        assertTrue(VisCondenserMath.isLowVis(20.0, 200, 10));
        assertTrue(VisCondenserMath.isLowVis(5.0, 200, 10));
        assertFalse(VisCondenserMath.isLowVis(5.0, 0, 10));
        assertFalse(VisCondenserMath.isLowVis(5.0, 200, 0));

        // Safe zone (>10%): zero Flux emitted
        assertEquals(0.0, VisCondenserMath.fluxForDrain(0.5, 25.0, 200, 10, 0.05), 1e-9);
        assertEquals(0.0, VisCondenserMath.fluxForDrain(0.5, 20.01, 200, 10, 0.05), 1e-9);

        // Danger zone (<=10%): emits 0.05 * drainedVis Flux
        assertEquals(0.025, VisCondenserMath.fluxForDrain(0.5, 20.0, 200, 10, 0.05), 1e-9);
        assertEquals(0.05, VisCondenserMath.fluxForDrain(1.0, 10.0, 200, 10, 0.05), 1e-9);

        // Zero drain or zero flux rate -> zero Flux
        assertEquals(0.0, VisCondenserMath.fluxForDrain(0.0, 5.0, 200, 10, 0.05), 1e-9);
        assertEquals(0.0, VisCondenserMath.fluxForDrain(1.0, 5.0, 200, 10, 0.0), 1e-9);
    }

    @Test
    void visCondenserFractionalCarryConservation() {
        // 0.5 Vis * 15 Source/Vis = 7.5 Source per half-second batch -> alternates 7 and 8 Source, exact 15 per 2 batches
        double carry = 0.0;
        int totalSource = 0;
        for (int i = 0; i < 20; i++) {
            VisCondenserMath.CarryStep step = VisCondenserMath.stepCarry(0.5, 15.0, carry);
            assertEquals(i % 2 == 0 ? 7 : 8, step.wholeSource(), "step " + i);
            totalSource += step.wholeSource();
            carry = step.nextCarry();
            assertTrue(carry >= 0.0 && carry < 1.0, "carry must remain in [0, 1), got " + carry);
        }
        assertEquals(150, totalSource);
        assertEquals(0.0, carry, 1e-9);

        // Odd fractional rate: 0.33 Vis * 10 Source/Vis over 100 steps = exact 330 Source
        carry = 0.0;
        totalSource = 0;
        for (int i = 0; i < 100; i++) {
            VisCondenserMath.CarryStep step = VisCondenserMath.stepCarry(0.33, 10.0, carry);
            totalSource += step.wholeSource();
            carry = step.nextCarry();
        }
        assertEquals(330, totalSource);
        assertEquals(0.0, carry, 1e-6);
    }

    // --------------------------------------------------- Essentia Cryo-Cauldron

    @Test
    void cauldronIceChargeAndDegradationProgression() {
        int normal = 1000, packed = 3000, blue = 9000;
        assertEquals(0, CauldronMath.iceChargeForTier(CauldronMath.ICE_NONE, normal, packed, blue));
        assertEquals(1000, CauldronMath.iceChargeForTier(CauldronMath.ICE_NORMAL, normal, packed, blue));
        assertEquals(3000, CauldronMath.iceChargeForTier(CauldronMath.ICE_PACKED, normal, packed, blue));
        assertEquals(9000, CauldronMath.iceChargeForTier(CauldronMath.ICE_BLUE, normal, packed, blue));

        assertEquals(CauldronMath.ICE_PACKED, CauldronMath.nextIceTier(CauldronMath.ICE_BLUE));
        assertEquals(CauldronMath.ICE_NORMAL, CauldronMath.nextIceTier(CauldronMath.ICE_PACKED));
        assertEquals(CauldronMath.ICE_NONE, CauldronMath.nextIceTier(CauldronMath.ICE_NORMAL));
        assertEquals(CauldronMath.ICE_NONE, CauldronMath.nextIceTier(CauldronMath.ICE_NONE));

        // Step through Blue Ice (9000) -> Packed Ice (3000) -> Ice (1000) -> Water (ICE_NONE) in 40 Source reactions
        int tier = CauldronMath.ICE_BLUE;
        int charge = blue;
        int degradedToPackedAt = -1;
        int degradedToNormalAt = -1;
        int meltedToWaterAt = -1;
        int totalSource = 0;
        for (int reaction = 1; reaction <= 400 && tier > CauldronMath.ICE_NONE; reaction++) {
            CauldronMath.IceStep step = CauldronMath.consumeIce(tier, charge, 40, normal, packed, blue);
            totalSource += 40;
            if (step.degradedSteps() > 0) {
                if (step.newTier() == CauldronMath.ICE_PACKED) {
                    degradedToPackedAt = totalSource;
                } else if (step.newTier() == CauldronMath.ICE_NORMAL) {
                    degradedToNormalAt = totalSource;
                } else if (step.newTier() == CauldronMath.ICE_NONE) {
                    meltedToWaterAt = totalSource;
                }
            }
            tier = step.newTier();
            charge = step.newCharge();
        }
        assertEquals(9000, degradedToPackedAt, "Blue Ice degrades to Packed Ice at 9000 Source");
        assertEquals(12000, degradedToNormalAt, "Packed Ice degrades to Ice at 12000 cumulative Source");
        assertEquals(13000, meltedToWaterAt, "Ice melts to Water at 13000 cumulative Source");
        assertEquals(CauldronMath.ICE_NONE, tier);
        assertEquals(0, charge);

        // Large single burst stepping across multiple tiers conserves remainder accurately
        CauldronMath.IceStep burst = CauldronMath.consumeIce(CauldronMath.ICE_BLUE, 100, 3200, normal, packed, blue);
        assertEquals(CauldronMath.ICE_NORMAL, burst.newTier());
        assertEquals(900, burst.newCharge());
        assertEquals(2, burst.degradedSteps());
    }

    @Test
    void cauldronRatesAndCoprocessorScaling() {
        // Base: 1.0 reaction/s * 40 Source = 40 Source/s
        assertEquals(1.0, CauldronMath.reactionsPerSecond(1.0, 0.5, 0), 1e-9);
        assertEquals(40.0, CauldronMath.sourcePerSecond(1.0, 0.5, 0, 40), 1e-9);

        // 4 Compressed Co-Processors: 1.0 + 4*0.5 = 3.0 reactions/s = 120 Source/s
        assertEquals(3.0, CauldronMath.reactionsPerSecond(1.0, 0.5, 4), 1e-9);
        assertEquals(120.0, CauldronMath.sourcePerSecond(1.0, 0.5, 4, 40), 1e-9);

        // 28 Compressed Co-Processors: 1.0 + 28*0.5 = 15.0 reactions/s = 600 Source/s
        assertEquals(15.0, CauldronMath.reactionsPerSecond(1.0, 0.5, 28), 1e-9);
        assertEquals(600.0, CauldronMath.sourcePerSecond(1.0, 0.5, 28, 40), 1e-9);

        // Negative values safely clamped
        assertEquals(1.0, CauldronMath.reactionsPerSecond(1.0, 0.5, -3), 1e-9);
        assertEquals(0.0, CauldronMath.sourcePerSecond(1.0, 0.5, 4, -10), 1e-9);
    }

    @Test
    void cauldronBoilOffGraceTimerAndSafeStates() {
        // Balanced (Ice present + Gelum >= 2): timer resets to 0, zero boil-off
        CauldronMath.BoilStep balanced = CauldronMath.stepBoilOff(
                false, 10, 2, 2, CauldronMath.ICE_NORMAL, 35, 40, 40);
        assertEquals(0, balanced.nextTimer());
        assertFalse(balanced.boiling());
        assertFalse(balanced.emitFlux());

        // Redstone paused even with Auram > 0 and no Ice / no Gelum: resets cleanly, zero boil-off
        CauldronMath.BoilStep redstone = CauldronMath.stepBoilOff(
                true, 10, 0, 2, CauldronMath.ICE_NONE, 45, 40, 40);
        assertEquals(0, redstone.nextTimer());
        assertFalse(redstone.boiling());
        assertFalse(redstone.emitFlux());

        // No Auram: resets cleanly, zero boil-off
        CauldronMath.BoilStep noAuram = CauldronMath.stepBoilOff(
                false, 0, 0, 2, CauldronMath.ICE_NONE, 30, 40, 40);
        assertEquals(0, noAuram.nextTimer());
        assertFalse(noAuram.boiling());
        assertFalse(noAuram.emitFlux());

        // Uncooled (no Ice) with Auram > 0: ticks 1..40 are grace (zero Flux, boiling=false);
        // ticks 41, 81, 121 emit Flux (boiling=true)
        int timer = 0;
        int emissions = 0;
        for (int tick = 1; tick <= 125; tick++) {
            CauldronMath.BoilStep step = CauldronMath.stepBoilOff(
                    false, 10, 10, 2, CauldronMath.ICE_NONE, timer, 40, 40);
            timer = step.nextTimer();
            assertEquals(tick, timer);
            if (tick <= 40) {
                assertFalse(step.boiling(), "tick " + tick + " must be in grace window");
                assertFalse(step.emitFlux(), "tick " + tick + " must not emit Flux during grace");
            } else {
                assertTrue(step.boiling(), "tick " + tick + " must be boiling after grace");
                boolean expectEmit = tick == 41 || tick == 81 || tick == 121;
                assertEquals(expectEmit, step.emitFlux(), "tick " + tick + " Flux emission mismatch");
                if (step.emitFlux()) {
                    emissions++;
                }
            }
        }
        assertEquals(3, emissions);

        // Gelum-starved (Ice present, Gelum < 2, Auram > 0) also triggers after grace
        CauldronMath.BoilStep gelumDryGrace = CauldronMath.stepBoilOff(
                false, 5, 1, 2, CauldronMath.ICE_BLUE, 39, 40, 40);
        assertEquals(40, gelumDryGrace.nextTimer());
        assertFalse(gelumDryGrace.boiling());
        assertFalse(gelumDryGrace.emitFlux());

        CauldronMath.BoilStep gelumDryEmit = CauldronMath.stepBoilOff(
                false, 5, 1, 2, CauldronMath.ICE_BLUE, 40, 40, 40);
        assertEquals(41, gelumDryEmit.nextTimer());
        assertTrue(gelumDryEmit.boiling());
        assertTrue(gelumDryEmit.emitFlux());
    }

    // --------------------------------------------------- Formation Bus fluid matrix

    @Test
    void formationBusCanPlaceIntoMatrix() {
        // replaceFluids=true: places whenever canBeReplaced is true (air OR fluid)
        assertTrue(FormationBusMath.canPlaceInto(true, true, true));
        assertTrue(FormationBusMath.canPlaceInto(true, true, false));
        assertFalse(FormationBusMath.canPlaceInto(true, false, true));
        assertFalse(FormationBusMath.canPlaceInto(true, false, false));

        // replaceFluids=false: places ONLY when canBeReplaced AND fluidEmpty
        assertTrue(FormationBusMath.canPlaceInto(false, true, true));
        assertFalse(FormationBusMath.canPlaceInto(false, true, false));
        assertFalse(FormationBusMath.canPlaceInto(false, false, true));
        assertFalse(FormationBusMath.canPlaceInto(false, false, false));
    }

    @Test
    void cauldronBalancedFourSecondRate() {
        // Updated defaults: 0.25 react/s (+0.125/coprocessor) * 160 Source = 40 base, 600 at 28 co-processors
        assertEquals(0.25, CauldronMath.reactionsPerSecond(0.25, 0.125, 0), 1e-9);
        assertEquals(40.0, CauldronMath.sourcePerSecond(0.25, 0.125, 0, 160), 1e-9);
        assertEquals(3.75, CauldronMath.reactionsPerSecond(0.25, 0.125, 28), 1e-9);
        assertEquals(600.0, CauldronMath.sourcePerSecond(0.25, 0.125, 28, 160), 1e-9);
    }
}
