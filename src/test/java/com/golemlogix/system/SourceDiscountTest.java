package com.golemlogix.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceDiscountTest {

    @Test
    void discountPercentIsNonStackingAndClamped() {
        assertEquals(0, SourceDiscount.discountPercent(false, 7));
        assertEquals(7, SourceDiscount.discountPercent(true, 7));
        assertEquals(0, SourceDiscount.discountPercent(true, -5));
        assertEquals(100, SourceDiscount.discountPercent(true, 150));
    }

    @Test
    void smallUpkeepCostsRetainExactSevenPercentSavings() {
        SourceDiscount meter = new SourceDiscount();
        long totalDrained = 0;
        for (int step = 1; step <= 100; step++) {
            int preview = meter.previewDrain(1, 7);
            int actual = meter.charge(1, 7);
            assertEquals(preview, actual, "preview must match charge at step " + step);
            totalDrained += actual;
            assertTrue(meter.creditHundredths() >= 0 && meter.creditHundredths() <= 99,
                    "credit must stay in [0, 99], got " + meter.creditHundredths());
            assertEquals(step * 93L, totalDrained * 100L - meter.creditHundredths(),
                    "exact hundredths conservation violated at step " + step);
        }
        assertEquals(93L, totalDrained, "100 ticks of 1 Source/s at -7% must drain exactly 93 Source");
        assertEquals(0, meter.creditHundredths(), "credit should return to 0 on exact 100-unit boundary");
    }

    @Test
    void emptyReservoirNeverPowersOnFirstTickAnd93SourceLastsExactly100Ticks() {
        SourceDiscount meter = new SourceDiscount();
        assertEquals(1, meter.previewDrain(1, 7), "fresh meter must require 1 Source on tick 1");

        int pool = 93;
        int poweredTicks = 0;
        for (int tick = 1; tick <= 120; tick++) {
            int need = meter.previewDrain(1, 7);
            if (pool < need) {
                break;
            }
            pool -= meter.charge(1, 7);
            poweredTicks++;
        }
        assertEquals(100, poweredTicks, "93 Source must sustain 1 Source/s for exactly 100 ticks at -7%");
        assertEquals(0, pool);
        assertEquals(1, meter.previewDrain(1, 7), "tick 101 must require 1 Source again");
    }

    @Test
    void craftingStepsOfTwoSourceRetainExactDiscount() {
        SourceDiscount meter = new SourceDiscount();
        long drained = 0;
        for (int i = 0; i < 100; i++) {
            drained += meter.charge(2, 7);
        }
        assertEquals(186L, drained, "100 craft steps * 2 Source at -7% must drain exactly 186 Source");
        assertEquals(0, meter.creditHundredths());
    }

    @Test
    void exactMultiplesAndMixedConsumersConserveHundredths() {
        SourceDiscount single = new SourceDiscount();
        assertEquals(93, single.charge(100, 7));
        assertEquals(0, single.creditHundredths());
        assertEquals(930, single.charge(1000, 7));
        assertEquals(0, single.creditHundredths());

        SourceDiscount mixed = new SourceDiscount();
        int[] sequence = {1, 2, 10, 150, 150, 37, 1, 2, 500, 147};
        long baseSum = 0;
        long drainSum = 0;
        for (int cost : sequence) {
            int p = mixed.previewDrain(cost, 7);
            int d = mixed.charge(cost, 7);
            assertEquals(p, d);
            baseSum += cost;
            drainSum += d;
            assertEquals(baseSum * 93L, drainSum * 100L - mixed.creditHundredths());
        }
        assertEquals(1000L, baseSum);
        assertEquals(930L, drainSum);
        assertEquals(0, mixed.creditHundredths());
    }

    @Test
    void removingDiscountResetsCreditAndChargesFullPrice() {
        SourceDiscount meter = new SourceDiscount();
        for (int i = 0; i < 10; i++) {
            meter.charge(1, 7);
        }
        assertEquals(70, meter.creditHundredths());
        assertEquals(1, meter.previewDrain(1, 0));
        assertEquals(1, meter.charge(1, 0));
        assertEquals(0, meter.creditHundredths());
        assertEquals(100, meter.charge(100, 0));
    }

    @Test
    void maxAffordableBaseIsTightAndConsistentWithPreviewDrain() {
        int[] discounts = {0, 7, 15, 50, 99};
        int[] credits = {0, 1, 7, 50, 93, 99};
        for (int d : discounts) {
            for (int c : credits) {
                SourceDiscount meter = new SourceDiscount();
                meter.setCreditHundredths(c);
                for (int avail = 0; avail <= 200; avail++) {
                    long maxBase = meter.maxAffordableBase(avail, d);
                    assertTrue(meter.previewDrain((int) maxBase, d) <= avail,
                            "maxAffordableBase=" + maxBase + " must be affordable with avail=" + avail + ", d=" + d + ", c=" + c);
                    assertTrue(meter.previewDrain((int) maxBase + 1, d) > avail,
                            "maxAffordableBase+1 must exceed avail=" + avail + ", d=" + d + ", c=" + c);
                }
            }
        }
        SourceDiscount free = new SourceDiscount();
        assertEquals(Long.MAX_VALUE, free.maxAffordableBase(0, 100));
        assertEquals(0, free.charge(500, 100));
        assertEquals(0, free.charge(0, 7));
        assertEquals(0, free.charge(-10, 7));
    }

    @Test
    void effectiveCostAndRateFormatting() {
        assertEquals(93, SourceDiscount.effectiveCostHundredths(1, 7));
        assertEquals(1023, SourceDiscount.effectiveCostHundredths(11, 7));
        assertEquals(9300, SourceDiscount.effectiveCostHundredths(100, 7));
        assertEquals(0, SourceDiscount.effectiveCostHundredths(10, 100));

        assertEquals("0", SourceDiscount.formatRate(0));
        assertEquals("1", SourceDiscount.formatRate(100));
        assertEquals("0.93", SourceDiscount.formatRate(93));
        assertEquals("10.23", SourceDiscount.formatRate(1023));
        assertEquals("1.4", SourceDiscount.formatRate(140));
        assertEquals("93", SourceDiscount.formatRate(9300));
    }
}
