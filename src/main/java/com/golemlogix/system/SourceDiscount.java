package com.golemlogix.system;

import java.util.Locale;

/**
 * Exact fractional Source discount accumulator (in hundredths of a Source) for the Giga Jar network bonus.
 * <p>
 * Maintains the conservation law {@code 100 * totalDrained - creditHundredths == totalBaseCost * (100 - discountPercent)}
 * with {@code creditHundredths} always in {@code [0, 99]}. Paying whole units up front and carrying the fractional savings
 * as credit ensures:
 * <ul>
 *     <li>An empty Source reservoir with zero credit never powers a network for free on the first tick.</li>
 *     <li>Small repeated costs (such as 1 Source/s core upkeep or 2 Source crafting steps) retain the exact discount percentage
 *     over time instead of losing it to per-tick integer rounding.</li>
 *     <li>Exact multiples (such as 100 Source at 7% discount) drain 93 Source immediately with zero leftover drift.</li>
 * </ul>
 */
public final class SourceDiscount {
    private int creditHundredths;

    public int creditHundredths() {
        return creditHundredths;
    }

    public void setCreditHundredths(int credit) {
        this.creditHundredths = Math.clamp(credit, 0, 99);
    }

    public void reset() {
        this.creditHundredths = 0;
    }

    /** Non-stacking network discount percent: applies once when at least one Giga Jar is connected. */
    public static int discountPercent(boolean hasGigaJar, int configuredPercent) {
        return hasGigaJar ? Math.clamp(configuredPercent, 0, 100) : 0;
    }

    /** Effective discounted Source cost in hundredths (for exact UI/Jade display). */
    public static int effectiveCostHundredths(int baseAmount, int discountPercent) {
        if (baseAmount <= 0) {
            return 0;
        }
        int d = Math.clamp(discountPercent, 0, 100);
        long hundredths = (long) baseAmount * (100 - d);
        return (int) Math.min(Integer.MAX_VALUE, hundredths);
    }

    /** Formats a rate stored in hundredths cleanly: whole numbers without decimals, otherwise up to 2 decimal places. */
    public static String formatRate(int hundredths) {
        if (hundredths <= 0) {
            return "0";
        }
        if (hundredths % 100 == 0) {
            return String.valueOf(hundredths / 100);
        }
        if (hundredths % 10 == 0) {
            return String.format(Locale.ROOT, "%.1f", hundredths / 100.0);
        }
        return String.format(Locale.ROOT, "%.2f", hundredths / 100.0);
    }

    /**
     * Whole Source units that must be drained right now to pay {@code baseAmount} at {@code discountPercent},
     * accounting for any accumulated credit. Does not mutate state.
     */
    public int previewDrain(int baseAmount, int discountPercent) {
        if (baseAmount <= 0) {
            return 0;
        }
        int d = Math.clamp(discountPercent, 0, 100);
        if (d == 0) {
            return baseAmount;
        }
        if (d == 100) {
            return 0;
        }
        long owed = (long) baseAmount * (100 - d) - creditHundredths;
        if (owed <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (owed + 99) / 100);
    }

    /**
     * Records payment of {@code baseAmount} at {@code discountPercent} and updates accumulated credit.
     * Returns the whole Source units drained (identical to {@link #previewDrain}).
     */
    public int charge(int baseAmount, int discountPercent) {
        if (baseAmount <= 0) {
            return 0;
        }
        int d = Math.clamp(discountPercent, 0, 100);
        if (d == 0) {
            creditHundredths = 0;
            return baseAmount;
        }
        if (d == 100) {
            return 0;
        }
        long owed = (long) baseAmount * (100 - d) - creditHundredths;
        if (owed <= 0) {
            creditHundredths = (int) (-owed);
            return 0;
        }
        long drain = (owed + 99) / 100;
        creditHundredths = (int) (drain * 100 - owed);
        return (int) Math.min(Integer.MAX_VALUE, drain);
    }

    /**
     * Maximum base Source amount payable from {@code availableSource} at {@code discountPercent} with current credit.
     * Guaranteed that for any {@code b <= result} (fitting in int), {@code previewDrain(b, discountPercent) <= availableSource}.
     */
    public long maxAffordableBase(long availableSource, int discountPercent) {
        if (availableSource < 0) {
            return 0;
        }
        int d = Math.clamp(discountPercent, 0, 100);
        if (d == 0) {
            return availableSource;
        }
        if (d == 100) {
            return Long.MAX_VALUE;
        }
        int rate = 100 - d;
        long clamped = Math.min(availableSource, (Long.MAX_VALUE - 99) / 100);
        return (clamped * 100 + creditHundredths) / rate;
    }
}
