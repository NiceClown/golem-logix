package com.golemlogix.system;

/**
 * Pure reaction rate, Ice degradation, and Flux boil-off calculations for the Essentia Cryo-Cauldron.
 */
public final class CauldronMath {
    public static final int ICE_NONE = 0;
    public static final int ICE_NORMAL = 1;
    public static final int ICE_PACKED = 2;
    public static final int ICE_BLUE = 3;

    public record IceStep(int newTier, int newCharge, int degradedSteps) {
    }

    public record BoilStep(int nextTimer, boolean boiling, boolean emitFlux) {
    }

    private CauldronMath() {
    }

    public static double reactionsPerSecond(double baseSpeed, double bonusPerCoprocessor, int coprocessors) {
        return Math.max(0.0, baseSpeed) + Math.max(0.0, bonusPerCoprocessor) * Math.max(0, coprocessors);
    }

    public static double sourcePerSecond(double baseSpeed, double bonusPerCoprocessor, int coprocessors, int sourcePerReaction) {
        return reactionsPerSecond(baseSpeed, bonusPerCoprocessor, coprocessors) * Math.max(0, sourcePerReaction);
    }

    public static int iceChargeForTier(int tier, int normalCharge, int packedCharge, int blueCharge) {
        return switch (tier) {
            case ICE_BLUE -> Math.max(1, blueCharge);
            case ICE_PACKED -> Math.max(1, packedCharge);
            case ICE_NORMAL -> Math.max(1, normalCharge);
            default -> 0;
        };
    }

    public static int nextIceTier(int tier) {
        return switch (tier) {
            case ICE_BLUE -> ICE_PACKED;
            case ICE_PACKED -> ICE_NORMAL;
            default -> ICE_NONE;
        };
    }

    /**
     * Subtracts {@code sourceProduced} from the active Ice block's cooling charge, stepping down
     * {@code Blue Ice -> Packed Ice -> Ice -> Water (ICE_NONE)} whenever charge is exhausted.
     */
    public static IceStep consumeIce(int tier, int charge, int sourceProduced,
                                     int normalCharge, int packedCharge, int blueCharge) {
        if (tier <= ICE_NONE || sourceProduced <= 0) {
            return new IceStep(Math.max(ICE_NONE, tier), Math.max(0, charge), 0);
        }
        int curTier = tier;
        int curCharge = Math.max(0, charge);
        int remaining = sourceProduced;
        int degraded = 0;
        while (remaining > 0 && curTier > ICE_NONE) {
            if (remaining < curCharge) {
                curCharge -= remaining;
                remaining = 0;
            } else {
                remaining -= curCharge;
                curTier = nextIceTier(curTier);
                degraded++;
                curCharge = iceChargeForTier(curTier, normalCharge, packedCharge, blueCharge);
            }
        }
        return new IceStep(curTier, curCharge, degraded);
    }

    /**
     * Advances the uncooled / Gelum-starved boil-off timer by one tick.
     * During ticks {@code 1..graceTicks}, {@code boiling=false} and {@code emitFlux=false} so a Formation Bus
     * replacing melted Ice produces zero spurious Flux. Starting at tick {@code graceTicks + 1} and every
     * {@code intervalTicks} thereafter, {@code emitFlux=true}.
     */
    public static BoilStep stepBoilOff(boolean redstonePaused, int auram, int gelum, int coolantCost,
                                       int iceTier, int boilTimer, int graceTicks, int intervalTicks) {
        boolean balanced = iceTier > ICE_NONE && gelum >= Math.max(1, coolantCost);
        if (redstonePaused || auram <= 0 || balanced) {
            return new BoilStep(0, false, false);
        }
        int grace = Math.max(0, graceTicks);
        int interval = Math.max(1, intervalTicks);
        int next = Math.max(0, boilTimer) + 1;
        if (next <= grace) {
            return new BoilStep(next, false, false);
        }
        boolean emit = (next - grace - 1) % interval == 0;
        return new BoilStep(next, true, emit);
    }
}
