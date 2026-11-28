package com.golemlogix.system;

/**
 * Pure threshold, hysteresis, low-Vis Flux risk, and fractional carry calculations for the Vis Condenser.
 */
public final class VisCondenserMath {
    public static final int MODE_PERCENT = 0;
    public static final int MODE_EXACT = 1;
    public static final int MAX_PERCENT = 95;
    public static final int MAX_EXACT = 500;

    public record CarryStep(int wholeSource, double nextCarry) {
    }

    private VisCondenserMath() {
    }

    public static int clampThreshold(int mode, int threshold) {
        int max = mode == MODE_PERCENT ? MAX_PERCENT : MAX_EXACT;
        return Math.max(0, Math.min(max, threshold));
    }

    /** Effective stop floor in Vis for the active mode, threshold, and chunk base aura. */
    public static double floorVis(int mode, int threshold, int base) {
        int clamped = clampThreshold(mode, threshold);
        if (mode == MODE_PERCENT) {
            return Math.max(0, base) * (clamped / 100.0);
        }
        return clamped;
    }

    /** Vis level at or above which a floor-paused condenser resumes work. */
    public static double resumeVis(int mode, int threshold, int base, int hysteresisPercent, int hysteresisExact) {
        int clamped = clampThreshold(mode, threshold);
        if (mode == MODE_PERCENT) {
            int resumePct = Math.min(100, clamped + Math.max(0, hysteresisPercent));
            return Math.max(0, base) * (resumePct / 100.0);
        }
        return clamped + Math.max(0, hysteresisExact);
    }

    /** Updates paused-by-floor state with hysteresis. */
    public static boolean nextPausedState(boolean wasPaused, double currentVis, double floor, double resume) {
        if (currentVis <= floor) {
            return true;
        }
        if (wasPaused) {
            return currentVis < resume;
        }
        return false;
    }

    /** Whether lowering the threshold below current chunk Vis should immediately release floor pause. */
    public static boolean shouldClearPauseOnThresholdChange(double currentVis, double newFloor) {
        return currentVis > newFloor;
    }

    /** True when chunk Vis is strictly below the low-Vis danger percent of chunk base. */
    public static boolean isLowVis(double vis, int base, int lowVisPercent) {
        return base > 0 && lowVisPercent > 0 && vis < base * (lowVisPercent / 100.0) - 1e-3;
    }

    /** Flux emitted for draining {@code drainedVis} ending at {@code visAfterDrain}. Zero at or above the low-Vis threshold. */
    public static double fluxForDrain(double drainedVis, double visAfterDrain, int base, int lowVisPercent, double fluxPerVis) {
        if (drainedVis <= 0.0 || fluxPerVis <= 0.0) {
            return 0.0;
        }
        return isLowVis(visAfterDrain, base, lowVisPercent) ? drainedVis * fluxPerVis : 0.0;
    }

    /** Converts drained Vis into whole Source plus fractional carry conserved across ticks. */
    public static CarryStep stepCarry(double drainedVis, double sourcePerVis, double carry) {
        double total = Math.max(0.0, carry) + Math.max(0.0, drainedVis) * Math.max(0.0, sourcePerVis);
        int whole = (int) Math.floor(total + 1e-9);
        double nextCarry = Math.max(0.0, total - whole);
        return new CarryStep(whole, nextCarry);
    }
}
