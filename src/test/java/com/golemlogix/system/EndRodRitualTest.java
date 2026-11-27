package com.golemlogix.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndRodRitualTest {

    @Test
    void iceTierHierarchyAndDegradation() {
        assertEquals(EndRodRitual.ICE_PACKED, EndRodRitual.degradeIceTier(EndRodRitual.ICE_BLUE));
        assertEquals(EndRodRitual.ICE_NORMAL, EndRodRitual.degradeIceTier(EndRodRitual.ICE_PACKED));
        assertEquals(EndRodRitual.ICE_NONE, EndRodRitual.degradeIceTier(EndRodRitual.ICE_NORMAL));
        assertEquals(EndRodRitual.ICE_NONE, EndRodRitual.degradeIceTier(EndRodRitual.ICE_NONE));

        assertTrue(EndRodRitual.hasSurroundingIce(EndRodRitual.PHASE_1_DIAMOND,
                EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE));
        assertFalse(EndRodRitual.hasSurroundingIce(EndRodRitual.PHASE_1_DIAMOND,
                EndRodRitual.ICE_BLUE, EndRodRitual.ICE_PACKED, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE));
        assertTrue(EndRodRitual.hasSurroundingIce(EndRodRitual.PHASE_2_VOID,
                EndRodRitual.ICE_PACKED, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_PACKED, EndRodRitual.ICE_PACKED));
        assertTrue(EndRodRitual.hasSurroundingIce(EndRodRitual.PHASE_3_QUENCH,
                EndRodRitual.ICE_NORMAL, EndRodRitual.ICE_PACKED, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_NORMAL));
        assertFalse(EndRodRitual.hasSurroundingIce(EndRodRitual.WAIT_EYES,
                EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE, EndRodRitual.ICE_BLUE));
    }

    @Test
    void fullThreePhaseStateMachineAndReagentOrdering() {
        int stage = EndRodRitual.PHASE_1_DIAMOND;
        int hits = 0;

        // Phase 1: 15 strikes
        for (int i = 1; i <= 14; i++) {
            var s = EndRodRitual.stepStrike(stage, hits);
            assertFalse(s.phaseCompleted());
            assertFalse(s.finished());
            assertEquals(EndRodRitual.PHASE_1_DIAMOND, s.newStage());
            assertEquals(i, s.newHits());
            stage = s.newStage();
            hits = s.newHits();
        }
        var p1Done = EndRodRitual.stepStrike(stage, hits);
        assertTrue(p1Done.phaseCompleted());
        assertFalse(p1Done.finished());
        assertEquals(EndRodRitual.WAIT_EYES, p1Done.newStage());
        stage = p1Done.newStage();
        hits = p1Done.newHits();
        assertEquals(0.375f, EndRodRitual.overallProgress(stage, hits), 1e-5f);

        // Wrong reagent rejected during WAIT_EYES
        var wrong = EndRodRitual.insertReagent(stage, 0, 0, 0, EndRodRitual.REAGENT_TEAR, 2);
        assertFalse(wrong.accepted());

        // Feed 2 Eyes of Ender in a single stack (capped at 2)
        var rEye = EndRodRitual.insertReagent(stage, 0, 0, 0, EndRodRitual.REAGENT_EYE, 5);
        assertTrue(rEye.accepted());
        assertEquals(2, rEye.taken());
        assertEquals(2, rEye.newEyes());
        assertEquals(EndRodRitual.PHASE_2_VOID, rEye.newStage());
        stage = rEye.newStage();

        // Phase 2: 15 strikes -> WAIT_TEMPER
        for (int i = 0; i < 15; i++) {
            var s = EndRodRitual.stepStrike(stage, hits);
            stage = s.newStage();
            hits = s.newHits();
        }
        assertEquals(EndRodRitual.WAIT_TEMPER, stage);
        assertEquals(0.75f, EndRodRitual.overallProgress(stage, hits), 1e-5f);

        // Phase 3 reagents in reverse order: Netherite Ingot first, then 1 tear, then 1 tear
        var rNeth = EndRodRitual.insertReagent(stage, 2, 0, 0, EndRodRitual.REAGENT_NETHERITE, 1);
        assertTrue(rNeth.accepted());
        assertEquals(EndRodRitual.WAIT_TEMPER, rNeth.newStage());
        var rTear1 = EndRodRitual.insertReagent(rNeth.newStage(), rNeth.newEyes(), rNeth.newTears(), rNeth.newNetherite(),
                EndRodRitual.REAGENT_TEAR, 1);
        assertTrue(rTear1.accepted());
        assertEquals(EndRodRitual.WAIT_TEMPER, rTear1.newStage());
        var rTear2 = EndRodRitual.insertReagent(rTear1.newStage(), rTear1.newEyes(), rTear1.newTears(), rTear1.newNetherite(),
                EndRodRitual.REAGENT_TEAR, 4);
        assertTrue(rTear2.accepted());
        assertEquals(1, rTear2.taken());
        assertEquals(EndRodRitual.PHASE_3_QUENCH, rTear2.newStage());
        stage = rTear2.newStage();

        // Phase 3: 10 strikes -> NONE & finished
        EndRodRitual.StrikeStep last = null;
        for (int i = 0; i < 10; i++) {
            last = EndRodRitual.stepStrike(stage, hits);
            stage = last.newStage();
            hits = last.newHits();
        }
        assertTrue(last.phaseCompleted());
        assertTrue(last.finished());
        assertEquals(EndRodRitual.NONE, stage);
    }

    @Test
    void shiftCancelRuleMatchesProgress() {
        assertFalse(EndRodRitual.requiresSneakToCancel(EndRodRitual.NONE, 0));
        assertFalse(EndRodRitual.requiresSneakToCancel(EndRodRitual.PHASE_1_DIAMOND, 0));
        assertTrue(EndRodRitual.requiresSneakToCancel(EndRodRitual.PHASE_1_DIAMOND, 1));
        assertTrue(EndRodRitual.requiresSneakToCancel(EndRodRitual.WAIT_EYES, 0));
        assertTrue(EndRodRitual.requiresSneakToCancel(EndRodRitual.WAIT_TEMPER, 0));
    }
}
