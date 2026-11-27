package com.golemlogix.system;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelScalingTest {

    @Test
    void reactorTwentyFivePercentScalesAllStandardChannelLimits() {
        int bonus = ChannelCalculation.bonusPercent(true, 25);
        assertEquals(25, bonus);
        assertEquals(0, ChannelCalculation.bonusPercent(false, 25));

        assertEquals(10, ChannelCalculation.scale(8, bonus));       // bare Atlas Core
        assertEquals(20, ChannelCalculation.scale(16, bonus));      // starter tower, basic pipe, device pass-through
        assertEquals(80, ChannelCalculation.scale(64, bonus));      // bushing line, dense pipe, expander exit, telegraph
        assertEquals(160, ChannelCalculation.scale(128, bonus));    // quantum expander exit, 2 basic + 1 quantum line
        assertEquals(240, ChannelCalculation.scale(192, bonus));    // 8 basic expanders line
        assertEquals(400, ChannelCalculation.scale(320, bonus));    // base array total / 8 quantum expanders line
        assertEquals(1200, ChannelCalculation.scale(960, bonus));   // 8 basic expanders array total
        assertEquals(2000, ChannelCalculation.scale(1600, bonus));  // 8 quantum expanders array total

        assertEquals(64, ChannelCalculation.scale(64, 0));
        assertEquals(0, ChannelCalculation.scale(0, 25));
        assertEquals(ChannelCalculation.UNLIMITED, ChannelCalculation.scale(ChannelCalculation.UNLIMITED, 25));
    }

    @Test
    void singleBushingAndDensePipeScales64To80WithoutBottleneck() {
        int deviceCount = 90;
        int n = 3 + deviceCount;
        int[][] links = new int[n][];
        links[0] = new int[]{1};
        links[1] = new int[]{0, 2};
        int[] pipeNeighbors = new int[1 + deviceCount];
        pipeNeighbors[0] = 1;
        for (int i = 0; i < deviceCount; i++) {
            int dev = 3 + i;
            pipeNeighbors[1 + i] = dev;
            links[dev] = new int[]{2};
        }
        links[2] = pipeNeighbors;

        int[] tiers = new int[n];
        boolean[] needs = new boolean[n];
        tiers[0] = ChannelCalculation.TIER_DENSE;
        tiers[1] = ChannelCalculation.TIER_DENSE;
        tiers[2] = ChannelCalculation.TIER_DENSE;
        for (int i = 3; i < n; i++) {
            tiers[i] = ChannelCalculation.TIER_DEVICE;
            needs[i] = true;
        }

        int[] capBase = new int[n];
        capBase[0] = ChannelCalculation.UNLIMITED;
        capBase[1] = 64;
        capBase[2] = 64;
        for (int i = 3; i < n; i++) capBase[i] = 16;
        ChannelCalculation baseCalc = new ChannelCalculation(links, capBase, tiers, needs, new int[]{0}, 320);
        assertEquals(64, baseCalc.used());
        assertTrue(baseCalc.granted(3 + 63));
        assertFalse(baseCalc.granted(3 + 64));
        assertEquals(ChannelCalculation.Missing.BOTTLENECK, baseCalc.missing(3 + 64));

        int bonus = ChannelCalculation.bonusPercent(true, 25);
        int[] capBoosted = new int[n];
        capBoosted[0] = ChannelCalculation.UNLIMITED;
        capBoosted[1] = ChannelCalculation.scale(64, bonus);
        capBoosted[2] = ChannelCalculation.scale(64, bonus);
        for (int i = 3; i < n; i++) capBoosted[i] = ChannelCalculation.scale(16, bonus);
        ChannelCalculation boostedCalc = new ChannelCalculation(links, capBoosted, tiers, needs, new int[]{0},
                ChannelCalculation.scale(320, bonus));
        assertEquals(80, boostedCalc.used());
        assertEquals(80, boostedCalc.load(1));
        assertEquals(80, boostedCalc.load(2));
        assertTrue(boostedCalc.granted(3 + 79));
        assertFalse(boostedCalc.granted(3 + 80));
        assertEquals(ChannelCalculation.Missing.BOTTLENECK, boostedCalc.missing(3 + 80));
    }

    @Test
    void expandedLineScales128To160AcrossTwoDenseBranches() {
        int perPipe = 90;
        int n = 4 + perPipe * 2;
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        link(adj, 0, 1);
        link(adj, 1, 2);
        link(adj, 1, 3);
        for (int i = 0; i < perPipe; i++) {
            link(adj, 2, 4 + i);
            link(adj, 3, 4 + perPipe + i);
        }
        int[][] links = toArray(adj);
        int[] tiers = new int[n];
        boolean[] needs = new boolean[n];
        tiers[0] = tiers[1] = tiers[2] = tiers[3] = ChannelCalculation.TIER_DENSE;
        for (int i = 4; i < n; i++) {
            tiers[i] = ChannelCalculation.TIER_DEVICE;
            needs[i] = true;
        }

        int[] capBase = new int[n];
        capBase[0] = ChannelCalculation.UNLIMITED;
        capBase[1] = 128;
        capBase[2] = 64;
        capBase[3] = 64;
        for (int i = 4; i < n; i++) capBase[i] = 16;
        ChannelCalculation baseCalc = new ChannelCalculation(links, capBase, tiers, needs, new int[]{0}, 640);
        assertEquals(128, baseCalc.used());

        int bonus = ChannelCalculation.bonusPercent(true, 25);
        int[] capBoosted = new int[n];
        capBoosted[0] = ChannelCalculation.UNLIMITED;
        capBoosted[1] = ChannelCalculation.scale(128, bonus);
        capBoosted[2] = ChannelCalculation.scale(64, bonus);
        capBoosted[3] = ChannelCalculation.scale(64, bonus);
        for (int i = 4; i < n; i++) capBoosted[i] = ChannelCalculation.scale(16, bonus);
        ChannelCalculation boostedCalc = new ChannelCalculation(links, capBoosted, tiers, needs, new int[]{0},
                ChannelCalculation.scale(640, bonus));
        assertEquals(160, boostedCalc.used());
        assertEquals(160, boostedCalc.load(1));
        assertEquals(80, boostedCalc.load(2));
        assertEquals(80, boostedCalc.load(3));
    }

    @Test
    void basicPipeScales16To20() {
        int devices = 25;
        int n = 3 + devices;
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        link(adj, 0, 1);
        link(adj, 1, 2);
        for (int i = 0; i < devices; i++) link(adj, 2, 3 + i);
        int[][] links = toArray(adj);
        int[] tiers = new int[n];
        boolean[] needs = new boolean[n];
        tiers[0] = tiers[1] = ChannelCalculation.TIER_DENSE;
        tiers[2] = ChannelCalculation.TIER_PIPE;
        for (int i = 3; i < n; i++) {
            tiers[i] = ChannelCalculation.TIER_DEVICE;
            needs[i] = true;
        }

        int[] capBase = new int[n];
        capBase[0] = ChannelCalculation.UNLIMITED;
        capBase[1] = 64;
        capBase[2] = 16;
        for (int i = 3; i < n; i++) capBase[i] = 16;
        assertEquals(16, new ChannelCalculation(links, capBase, tiers, needs, new int[]{0}, 320).used());

        int bonus = ChannelCalculation.bonusPercent(true, 25);
        int[] capBoosted = new int[n];
        capBoosted[0] = ChannelCalculation.UNLIMITED;
        capBoosted[1] = ChannelCalculation.scale(64, bonus);
        capBoosted[2] = ChannelCalculation.scale(16, bonus);
        for (int i = 3; i < n; i++) capBoosted[i] = ChannelCalculation.scale(16, bonus);
        assertEquals(20, new ChannelCalculation(links, capBoosted, tiers, needs, new int[]{0},
                ChannelCalculation.scale(320, bonus)).used());
    }

    private static void link(List<List<Integer>> adj, int a, int b) {
        adj.get(a).add(b);
        adj.get(b).add(a);
    }

    private static int[][] toArray(List<List<Integer>> adj) {
        int[][] out = new int[adj.size()][];
        for (int i = 0; i < adj.size(); i++) {
            List<Integer> list = adj.get(i);
            out[i] = new int[list.size()];
            for (int j = 0; j < list.size(); j++) {
                out[i][j] = list.get(j);
            }
        }
        return out;
    }
}
