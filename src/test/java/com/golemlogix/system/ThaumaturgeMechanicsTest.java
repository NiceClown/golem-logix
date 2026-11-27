package com.golemlogix.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaumaturgeMechanicsTest {

    @Test
    void visCondenserClampAndFloorThresholds() {
        assertEquals(0, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_PERCENT, -10));
        assertEquals(25, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_PERCENT, 25));
        assertEquals(95, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_PERCENT, 120));

        assertEquals(0, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_EXACT, -5));
        assertEquals(50, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_EXACT, 50));
        assertEquals(500, VisCondenserMath.clampThreshold(VisCondenserMath.MODE_EXACT, 999));

        assertEquals(50.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_PERCENT, 25, 200), 1e-9);
        assertEquals(0.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_PERCENT, 25, 0), 1e-9);
        assertEquals(50.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_EXACT, 50, 200), 1e-9);
        assertEquals(50.0, VisCondenserMath.floorVis(VisCondenserMath.MODE_EXACT, 50, 0), 1e-9);
    }

    @Test
    void visCondenserHysteresisPercentAndExact() {
        double pctFloor = VisCondenserMath.floorVis(VisCondenserMath.MODE_PERCENT, 25, 200);
        double pctResume = VisCondenserMath.resumeVis(VisCondenserMath.MODE_PERCENT, 25, 200, 5, 5);
        assertEquals(50.0, pctFloor, 1e-9);
        assertEquals(60.0, pctResume, 1e-9);

        assertFalse(VisCondenserMath.nextPausedState(false, 51.0, pctFloor, pctResume));
        assertTrue(VisCondenserMath.nextPausedState(false, 50.0, pctFloor, pctResume));
        assertTrue(VisCondenserMath.nextPausedState(false, 49.0, pctFloor, pctResume));
        assertTrue(VisCondenserMath.nextPausedState(true, 55.0, pctFloor, pctResume));
        assertTrue(VisCondenserMath.nextPausedState(true, 59.99, pctFloor, pctResume));
        assertFalse(VisCondenserMath.nextPausedState(true, 60.0, pctFloor, pctResume));
        assertFalse(VisCondenserMath.nextPausedState(true, 65.0, pctFloor, pctResume));

        assertEquals(200.0, VisCondenserMath.resumeVis(VisCondenserMath.MODE_PERCENT, 95, 200, 10, 5), 1e-9);

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
        assertTrue(VisCondenserMath.shouldClearPauseOnThresholdChange(45.0, 40.0));
        assertFalse(VisCondenserMath.shouldClearPauseOnThresholdChange(45.0, 45.0));
        assertFalse(VisCondenserMath.shouldClearPauseOnThresholdChange(45.0, 60.0));
    }

    @Test
    void visCondenserLowVisFluxTriggerAndSafeZeroFlux() {
        assertFalse(VisCondenserMath.isLowVis(20.1, 200, 10));
        assertTrue(VisCondenserMath.isLowVis(20.0, 200, 10));
        assertTrue(VisCondenserMath.isLowVis(5.0, 200, 10));
        assertFalse(VisCondenserMath.isLowVis(5.0, 0, 10));
        assertFalse(VisCondenserMath.isLowVis(5.0, 200, 0));

        assertEquals(0.0, VisCondenserMath.fluxForDrain(0.5, 25.0, 200, 10, 0.05), 1e-9);
        assertEquals(0.0, VisCondenserMath.fluxForDrain(0.5, 20.01, 200, 10, 0.05), 1e-9);

        assertEquals(0.025, VisCondenserMath.fluxForDrain(0.5, 20.0, 200, 10, 0.05), 1e-9);
        assertEquals(0.05, VisCondenserMath.fluxForDrain(1.0, 10.0, 200, 10, 0.05), 1e-9);

        assertEquals(0.0, VisCondenserMath.fluxForDrain(0.0, 5.0, 200, 10, 0.05), 1e-9);
        assertEquals(0.0, VisCondenserMath.fluxForDrain(1.0, 5.0, 200, 10, 0.0), 1e-9);
    }

    @Test
    void visCondenserFractionalCarryConservation() {
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

        CauldronMath.IceStep burst = CauldronMath.consumeIce(CauldronMath.ICE_BLUE, 100, 3200, normal, packed, blue);
        assertEquals(CauldronMath.ICE_NORMAL, burst.newTier());
        assertEquals(900, burst.newCharge());
        assertEquals(2, burst.degradedSteps());
    }

    @Test
    void cauldronRatesAndCoprocessorScaling() {
        assertEquals(1.0, CauldronMath.reactionsPerSecond(1.0, 0.5, 0), 1e-9);
        assertEquals(40.0, CauldronMath.sourcePerSecond(1.0, 0.5, 0, 40), 1e-9);

        assertEquals(3.0, CauldronMath.reactionsPerSecond(1.0, 0.5, 4), 1e-9);
        assertEquals(120.0, CauldronMath.sourcePerSecond(1.0, 0.5, 4, 40), 1e-9);

        assertEquals(15.0, CauldronMath.reactionsPerSecond(1.0, 0.5, 28), 1e-9);
        assertEquals(600.0, CauldronMath.sourcePerSecond(1.0, 0.5, 28, 40), 1e-9);

        assertEquals(1.0, CauldronMath.reactionsPerSecond(1.0, 0.5, -3), 1e-9);
        assertEquals(0.0, CauldronMath.sourcePerSecond(1.0, 0.5, 4, -10), 1e-9);
    }

    @Test
    void cauldronBoilOffGraceTimerAndSafeStates() {
        CauldronMath.BoilStep balanced = CauldronMath.stepBoilOff(
                false, 10, 2, 2, CauldronMath.ICE_NORMAL, 35, 40, 40);
        assertEquals(0, balanced.nextTimer());
        assertFalse(balanced.boiling());
        assertFalse(balanced.emitFlux());

        CauldronMath.BoilStep redstone = CauldronMath.stepBoilOff(
                true, 10, 0, 2, CauldronMath.ICE_NONE, 45, 40, 40);
        assertEquals(0, redstone.nextTimer());
        assertFalse(redstone.boiling());
        assertFalse(redstone.emitFlux());

        CauldronMath.BoilStep noAuram = CauldronMath.stepBoilOff(
                false, 0, 0, 2, CauldronMath.ICE_NONE, 30, 40, 40);
        assertEquals(0, noAuram.nextTimer());
        assertFalse(noAuram.boiling());
        assertFalse(noAuram.emitFlux());

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

    @Test
    void formationBusCanPlaceIntoMatrix() {
        assertTrue(FormationBusMath.canPlaceInto(true, true, true));
        assertTrue(FormationBusMath.canPlaceInto(true, true, false));
        assertFalse(FormationBusMath.canPlaceInto(true, false, true));
        assertFalse(FormationBusMath.canPlaceInto(true, false, false));

        assertTrue(FormationBusMath.canPlaceInto(false, true, true));
        assertFalse(FormationBusMath.canPlaceInto(false, true, false));
        assertFalse(FormationBusMath.canPlaceInto(false, false, true));
        assertFalse(FormationBusMath.canPlaceInto(false, false, false));
    }

    @Test
    void cauldronBalancedFourSecondRate() {
        assertEquals(0.25, CauldronMath.reactionsPerSecond(0.25, 0.125, 0), 1e-9);
        assertEquals(40.0, CauldronMath.sourcePerSecond(0.25, 0.125, 0, 160), 1e-9);
        assertEquals(3.75, CauldronMath.reactionsPerSecond(0.25, 0.125, 28), 1e-9);
        assertEquals(600.0, CauldronMath.sourcePerSecond(0.25, 0.125, 28, 160), 1e-9);
    }
}
