package com.golemlogix.system;

/**
 * Pure placement eligibility rules for the Formation Bus (unit-testable without Minecraft classes).
 */
public final class FormationBusMath {
    private FormationBusMath() {
    }

    /** Whether a block can be placed into a target spot given fluid-replacement preference, replaceability, and fluid emptiness. */
    public static boolean canPlaceInto(boolean replaceFluids, boolean canBeReplaced, boolean fluidEmpty) {
        return canBeReplaced && (replaceFluids || fluidEmpty);
    }
}
