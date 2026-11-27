package com.golemlogix.system;

/**
 * Pure state machine and ice-tier rules for the 3-phase pre-End Cryo-Forging Ritual on an unassembled
 * Mana Saturator anvil, turning a Diamond Block + 2x Eye of Ender + 2x Ghast Tear + 1x Netherite Ingot +
 * 4 surrounding Blue Ice blocks + 40 Forge Hammer strikes (4,000 Source) into one End Rod.
 * Kept free of {@code net.minecraft.*} imports so it can run directly under pure JUnit tests.
 */
public final class EndRodRitual {
    public static final int NONE = 0;
    public static final int PHASE_1_DIAMOND = 1;
    public static final int WAIT_EYES = 2;
    public static final int PHASE_2_VOID = 3;
    public static final int WAIT_TEMPER = 4;
    public static final int PHASE_3_QUENCH = 5;

    public static final int HITS_PHASE_1 = 15;
    public static final int HITS_PHASE_2 = 15;
    public static final int HITS_PHASE_3 = 10;
    public static final int TOTAL_HITS = HITS_PHASE_1 + HITS_PHASE_2 + HITS_PHASE_3;

    public static final int EYES_REQUIRED = 2;
    public static final int TEARS_REQUIRED = 2;
    public static final int NETHERITE_REQUIRED = 1;

    public static final int ICE_NONE = 0;
    public static final int ICE_NORMAL = 1;
    public static final int ICE_PACKED = 2;
    public static final int ICE_BLUE = 3;

    public static final int REAGENT_NONE = 0;
    public static final int REAGENT_EYE = 1;
    public static final int REAGENT_TEAR = 2;
    public static final int REAGENT_NETHERITE = 3;

    public record ReagentStep(boolean accepted, int taken, int newStage, int newEyes, int newTears, int newNetherite) {
    }

    public record StrikeStep(int newStage, int newHits, boolean phaseCompleted, boolean finished) {
    }

    private EndRodRitual() {
    }

    public static boolean isHammerStage(int stage) {
        return stage == PHASE_1_DIAMOND || stage == PHASE_2_VOID || stage == PHASE_3_QUENCH;
    }

    public static boolean isWaitStage(int stage) {
        return stage == WAIT_EYES || stage == WAIT_TEMPER;
    }

    public static int hitsForStage(int stage) {
        return switch (stage) {
            case PHASE_1_DIAMOND -> HITS_PHASE_1;
            case PHASE_2_VOID -> HITS_PHASE_2;
            case PHASE_3_QUENCH -> HITS_PHASE_3;
            default -> 0;
        };
    }

    public static int completedHitsBefore(int stage) {
        return switch (stage) {
            case PHASE_1_DIAMOND -> 0;
            case WAIT_EYES, PHASE_2_VOID -> HITS_PHASE_1;
            case WAIT_TEMPER, PHASE_3_QUENCH -> HITS_PHASE_1 + HITS_PHASE_2;
            default -> TOTAL_HITS;
        };
    }

    /** Normalized 0..1 progress across all 40 strikes of the full ritual. */
    public static float overallProgress(int stage, int hits) {
        if (stage == NONE) {
            return 0f;
        }
        int total = completedHitsBefore(stage) + Math.max(0, Math.min(hitsForStage(stage), hits));
        return Math.max(0f, Math.min(1f, total / (float) TOTAL_HITS));
    }

    /** Required horizontal ice tier on all 4 N/S/E/W sides of the anvil for the given hammer phase. */
    public static int requiredIceTier(int stage) {
        return switch (stage) {
            case PHASE_1_DIAMOND -> ICE_BLUE;
            case PHASE_2_VOID -> ICE_PACKED;
            case PHASE_3_QUENCH -> ICE_NORMAL;
            default -> ICE_NONE;
        };
    }

    /** Returns the next cooler-to-warmer degraded ice tier (BLUE -> PACKED -> NORMAL -> NONE). */
    public static int degradeIceTier(int tier) {
        return switch (tier) {
            case ICE_BLUE -> ICE_PACKED;
            case ICE_PACKED -> ICE_NORMAL;
            case ICE_NORMAL -> ICE_NONE;
            default -> ICE_NONE;
        };
    }

    /** Returns true when all 4 cardinal ice tiers meet the minimum tier for the active hammer phase. */
    public static boolean hasSurroundingIce(int stage, int northTier, int southTier, int eastTier, int westTier) {
        int req = requiredIceTier(stage);
        return req > ICE_NONE && northTier >= req && southTier >= req && eastTier >= req && westTier >= req;
    }

    /** Advances one hammer strike during an active hammer phase. */
    public static StrikeStep stepStrike(int stage, int hits) {
        int target = hitsForStage(stage);
        if (target <= 0) {
            return new StrikeStep(stage, hits, false, false);
        }
        int nextHits = hits + 1;
        if (nextHits < target) {
            return new StrikeStep(stage, nextHits, false, false);
        }
        return switch (stage) {
            case PHASE_1_DIAMOND -> new StrikeStep(WAIT_EYES, 0, true, false);
            case PHASE_2_VOID -> new StrikeStep(WAIT_TEMPER, 0, true, false);
            case PHASE_3_QUENCH -> new StrikeStep(NONE, 0, true, true);
            default -> new StrikeStep(stage, nextHits, false, false);
        };
    }

    /** Inserts matching reagent during {@link #WAIT_EYES} or {@link #WAIT_TEMPER} (tears/netherite accepted in any order). */
    public static ReagentStep insertReagent(int stage, int eyes, int tears, int netherite, int reagentKind, int count) {
        if (count <= 0 || reagentKind == REAGENT_NONE) {
            return new ReagentStep(false, 0, stage, eyes, tears, netherite);
        }
        if (stage == WAIT_EYES && reagentKind == REAGENT_EYE && eyes < EYES_REQUIRED) {
            int take = Math.min(count, EYES_REQUIRED - eyes);
            int nextEyes = eyes + take;
            int nextStage = nextEyes >= EYES_REQUIRED ? PHASE_2_VOID : WAIT_EYES;
            return new ReagentStep(true, take, nextStage, nextEyes, tears, netherite);
        }
        if (stage == WAIT_TEMPER) {
            if (reagentKind == REAGENT_TEAR && tears < TEARS_REQUIRED) {
                int take = Math.min(count, TEARS_REQUIRED - tears);
                int nextTears = tears + take;
                int nextStage = (nextTears >= TEARS_REQUIRED && netherite >= NETHERITE_REQUIRED) ? PHASE_3_QUENCH : WAIT_TEMPER;
                return new ReagentStep(true, take, nextStage, eyes, nextTears, netherite);
            }
            if (reagentKind == REAGENT_NETHERITE && netherite < NETHERITE_REQUIRED) {
                int take = Math.min(count, NETHERITE_REQUIRED - netherite);
                int nextNeth = netherite + take;
                int nextStage = (tears >= TEARS_REQUIRED && nextNeth >= NETHERITE_REQUIRED) ? PHASE_3_QUENCH : WAIT_TEMPER;
                return new ReagentStep(true, take, nextStage, eyes, tears, nextNeth);
            }
        }
        return new ReagentStep(false, 0, stage, eyes, tears, netherite);
    }

    /** True if canceling requires Shift+RMB empty hand so players never abort a multi-phase ritual by accident. */
    public static boolean requiresSneakToCancel(int stage, int hits) {
        return stage > PHASE_1_DIAMOND || (stage == PHASE_1_DIAMOND && hits > 0);
    }
}
