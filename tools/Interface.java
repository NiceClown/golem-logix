import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Golem Interface (from the concept: a copper-bronze cube with little drawers, gauges, gears and frost). A brass frame along the edges,
 * sunk bronze panels, on EACH of the six sides a square brass inlet funnel (a network socket and an item
 * input) with a dark funnel, next to it a flat relief metronome; the turbines in the funnels and the metronome pendulums are drawn by
 * {@code InterfaceRenderer}
 * (they spin and swing while the interface works; the tempo follows the number of speed upgrades).
 */
final class Interface {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM;
    static final int RED = Paint.argb(255, 0xb02020);

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    // ============================================================================================ textures
    /** A dark funnel in the middle of a face: rings going deep and a teal network sheen at the bottom. */
    static void funnel(Paint p, double cx, double cy, double r) {
        p.discFn(cx, cy, r, (u, v) -> {
            double d = Math.hypot(u - cx, v - cy) / r;
            double rings = 0.5 + 0.5 * Math.cos(d * 18);
            return Paint.ramp(IRON, 0.04 + 0.22 * d + 0.06 * rings);
        });
        p.ring(cx, cy, r - 0.35, r, at(BRASS, 1.4));
        p.disc(cx, cy, r * 0.28, at(CYAN, 2.2));
        p.glowRect(cx - r * 0.2, cy - r * 0.2, cx + r * 0.2, cy + r * 0.2, r * 0.35, at(CYAN, 4.2), 90);
    }

    /** A little brass card drawer with a bracket handle and a plate. */
    static void drawer(Paint p, double x0, double y0, double x1, double y1, long seed) {
        p.slab(x0, y0, x1, y1, BRASS, 3.0, 1);
        p.slab(x0 + 0.35, y0 + 0.35, x1 - 0.35, y1 - 0.35, BRASS, 2.4, 1);
        double cx = (x0 + x1) / 2;
        p.rect(cx - 0.9, y0 + 0.6, cx + 0.9, y0 + 1.1, rgb(0xE6DCB8));
        p.rect(cx - 0.6, y0 + 0.75, cx + 0.3, y0 + 0.85, argb(160, 0x3a2a18));
        p.rect(cx - 1.1, y1 - 1.0, cx + 1.1, y1 - 0.75, at(BRASS, 5.4));
        p.rect(cx - 1.1, y1 - 0.75, cx - 0.85, y1 - 0.45, at(BRASS, 4.0));
        p.rect(cx + 0.85, y1 - 0.75, cx + 1.1, y1 - 0.45, at(BRASS, 4.0));
    }

    /** A side panel (16x16; the area 1..15 between the frame edges is visible). */
    static Paint side(long seed) {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.7, true, seed);
        p.slab(1.4, 1.4, 14.6, 14.6, BRONZE, 2.3, 1);
        p.grain(1.4, 1.4, 14.6, 14.6, 0.05, seed + 1);
        // patina along the seams, as in the concept
        Random r = new Random(seed + 2);
        for (int i = 0; i < 9; i++) {
            double x = 1.5 + r.nextDouble() * 13, y = 1.5 + r.nextDouble() * 13;
            p.tint(x, y, x + 0.6 + r.nextDouble() * 1.4, y + 0.3 + r.nextDouble() * 0.6, argb(90, 0x3fae9a));
        }
        // a row of gauges on top (the needles come alive: an animated texture)
        p.slab(4.0, 1.6, 12.0, 3.9, BRASS, 2.8, 1);
        for (int i = 0; i < 3; i++) {
            p.gauge(5.4 + i * 2.6, 2.75, 1.0, 50 + i * 37 + seed % 17, RED);
        }
        // card drawers at the bottom corners and runes on the sides
        drawer(p, 1.8, 11.6, 4.6, 14.2, seed + 3);
        drawer(p, 11.4, 11.6, 14.2, 14.2, seed + 4);
        // on the left a niche for the flat metronome (the model places its relief), on the right a column of runes
        p.slab(1.6, 4.2, 4.1, 11.6, BRASS, 2.6, 1);
        p.slab(1.9, 4.5, 3.8, 11.3, IRON, 0.7, 1);
        for (int i = 0; i < 3; i++) {
            p.rune(13.0, 4.6 + i * 2.2, 1.6, (int) seed + i * 3 + 7, at(CYAN, 5.0), at(CYAN, 2.4));
        }
        // the funnel under the inlet
        funnel(p, 8, 8, 3.2);
        for (double[] c : new double[][]{{1.8, 1.8}, {14.2, 1.8}, {1.8, 10.8}, {14.2, 10.8}}) {
            p.rivet(c[0], c[1], 0.3, BRASS);
        }
        p.frostLine(1.4, 14.6, 1.4, 0.5, seed + 5);
        p.icicle(6.2, 1.4, 0.9, 0.4);
        p.icicle(10.4, 1.4, 1.2, 0.4);
        return p;
    }

    /** Roof and bottom: a bronze deck, vent grates, a funnel, frost. */
    static Paint top(boolean bottom) {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.6, true, bottom ? 7101 : 7102);
        p.slab(1.4, 1.4, 14.6, 14.6, BRONZE, 2.1, 1);
        Terminals.vent(p, 6.0, 1.9, 10.0, 3.6, 3);
        Terminals.vent(p, 6.0, 12.4, 10.0, 14.1, 3);
        funnel(p, 8, 8, 3.2);
        for (int i = 0; i < 2; i++) {
            p.rune(2.4 + i * 9.6, 7.2, 1.6, 12 + i * 9, at(VIOLET, 5.2), at(VIOLET, 2.6));
        }
        if (!bottom) {
            p.frostLine(1.4, 14.6, 1.4, 0.8, 7103);
            p.frostLine(1.4, 14.6, 13.8, 0.6, 7104);
        }
        return p;
    }

    /** Brass of the frame and inlets: a lengthwise grain, rivets. */
    static Paint brass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 7111, true);
        p.grain(0, 0, 16, 16, 0.04, 7112);
        for (double x = 2; x < 16; x += 4) {
            p.rivet(x, 8, 0.45, BRASS);
        }
        return p;
    }

    /** Turbine blades of the funnel (transparent outside the circle; the renderer spins it). */
    static Paint turbine() {
        Paint p = new Paint(8, 8, 8);
        for (int b = 0; b < 7; b++) {
            double a0 = b * 2 * Math.PI / 7;
            for (double rr = 0.7; rr < 3.6; rr += 0.06) {
                double a = a0 + rr * 0.42;
                p.disc(4 + Math.cos(a) * rr, 4 + Math.sin(a) * rr, 0.26 + rr * 0.07,
                        Paint.ramp(COPPER, 0.3 + 0.45 * (rr / 3.6) + 0.1 * Math.sin(a * 2)));
            }
        }
        p.disc(4, 4, 0.95, at(BRASS, 1.4));
        p.sphere(4, 4, 0.85, BRASS, 0.35, 1.0);
        p.disc(4, 4, 0.3, at(CYAN, 5.0));
        p.glowRect(3.9, 3.9, 4.1, 4.1, 0.5, at(CYAN, 4.4), 120);
        return p;
    }

    /** Metronome body: mahogany, a brass scale with marks, a rune on a plate. */
    static Paint metronome() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> Paint.mix(rgb(0x4a1c10), rgb(0x7a3420), 0.5 + 0.3 * Math.sin(u * 2.1 + v * 0.3)));
        p.grain(0, 0, 16, 16, 0.08, 7121);
        p.rect(6.6, 1.0, 9.4, 13.0, at(BRASS, 3.6));
        for (int i = 0; i < 9; i++) {
            double y = 1.6 + i * 1.3;
            p.rect(6.9, y, i % 2 == 0 ? 8.8 : 8.0, y + 0.3, at(IRON, 0.6));
        }
        p.slab(4.5, 13.4, 11.5, 15.4, BRASS, 3.2, 1);
        p.rune(7.2, 13.6, 1.6, 4, at(VIOLET, 5.4), 0);
        return p;
    }

    static void textures() throws Exception {
        long[] seeds = {7001, 7002, 7003, 7004};
        for (int i = 0; i < 4; i++) {
            side(seeds[i]).save(Forge2.tex("interface_side_" + i));
        }
        top(false).save(Forge2.tex("interface_top"));
        top(true).save(Forge2.tex("interface_bottom"));
        brass().save(Forge2.tex("interface_brass"));
        turbine().save(Forge2.tex("interface_turbine"));
        metronome().save(Forge2.tex("interface_metronome"));
    }

    // ============================================================================================ model
    /** A box on face dir: (a, b) are coordinates in the face plane, d0..d1 the depth from the outer plane into the block. */
    static Models.El onFace(String dir, double a0, double b0, double a1, double b1, double d0, double d1) {
        double[] p = point(dir, a0, b0, d0), q = point(dir, a1, b1, d1);
        return Models.e(Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.min(p[2], q[2]),
                Math.max(p[0], q[0]), Math.max(p[1], q[1]), Math.max(p[2], q[2]));
    }

    static double[] point(String dir, double a, double b, double d) {
        return switch (dir) {
            case "north" -> new double[]{a, b, d};
            case "south" -> new double[]{a, b, 16 - d};
            case "west" -> new double[]{d, b, a};
            case "east" -> new double[]{16 - d, b, a};
            case "down" -> new double[]{a, d, b};
            default -> new double[]{a, 16 - d, b};
        };
    }

    static final String[] DIRS = {"north", "south", "east", "west", "up", "down"};

    static List<Models.El> body() {
        List<Models.El> l = new ArrayList<>();
        // sunk panels (core 1..15)
        l.add(Models.e(1, 1, 1, 15, 15, 15).face("north", "#side0").face("east", "#side1").face("south", "#side2")
                .face("west", "#side3").face("up", "#top").face("down", "#bottom"));
        // brass frame: edges along X at full length, along Y and Z between them (no coinciding faces in the corners)
        double t = 1.5;
        for (double a : new double[]{0, 16 - t}) {
            for (double b : new double[]{0, 16 - t}) {
                l.add(Models.e(0, a, b, 16, a + t, b + t).all("#brass"));
                l.add(Models.e(a, t, b, a + t, 16 - t, b + t).all("#brass"));
                l.add(Models.e(a, b, t, a + t, b + t, 16 - t).all("#brass"));
            }
        }
        // inlet funnels on all six sides: a square brass frame with bevels and bolts in the corners
        for (String d : DIRS) {
            l.add(onFace(d, 4.0, 4.0, 12.0, 5.2, 0, 1).all("#brass"));
            l.add(onFace(d, 4.0, 10.8, 12.0, 12.0, 0, 1).all("#brass"));
            l.add(onFace(d, 4.0, 5.2, 5.2, 10.8, 0, 1).all("#brass"));
            l.add(onFace(d, 10.8, 5.2, 12.0, 10.8, 0, 1).all("#brass"));
            // the inner cuff is a little deeper: the funnel narrows towards the hole
            l.add(onFace(d, 5.2, 5.2, 10.8, 5.8, 0.4, 1).all("#brass"));
            l.add(onFace(d, 5.2, 10.2, 10.8, 10.8, 0.4, 1).all("#brass"));
            l.add(onFace(d, 5.2, 5.8, 5.8, 10.2, 0.4, 1).all("#brass"));
            l.add(onFace(d, 10.2, 5.8, 10.8, 10.2, 0.4, 1).all("#brass"));
        }
        // icicles under the top edges
        for (double[] ic : new double[][]{{3.2, 1.0}, {6.8, 1.6}, {11.6, 1.2}}) {
            for (String d : new String[]{"north", "south", "east", "west"}) {
                l.add(onFace(d, ic[0], 14.5 - ic[1], ic[0] + 0.5, 14.5, 0.3, 0.8).all("#ice"));
            }
        }
        // metronomes lying flat on the four side faces: a low relief (0.5) next to the funnel, within the block
        for (String d : new String[]{"north", "south", "east", "west"}) {
            metronome(l, d);
        }
        return l;
    }

    /** The middle of the metronome on the face (coordinate a) and its bottom/top (b). The renderer draws the pendulum with its axis at (MET_A, MET_PIVOT). */
    static final double MET_A = 2.85, MET_B0 = 4.6, MET_PIVOT = 5.2, MET_B1 = 11.6;

    /**
     * A flat metronome on a face: a brass base, a body of four steps narrowing upwards and a brass finial; the front
     * faces of the steps take their strip of a shared picture, so the scale with marks spans the full height.
     */
    /**
     * Where the metronome is on face d (coordinate a): where the face texture has the niche (u = 2.85). For the north and east faces
     * the unwrap is mirrored (u = 16 - a), so the metronome stands at the other edge, always to the left of the funnel as the viewer sees it.
     */
    static double metA(String d) {
        return d.equals("north") || d.equals("east") ? 16 - MET_A : MET_A;
    }

    static void metronome(List<Models.El> l, String d) {
        double MET_A = metA(d);
        l.add(onFace(d, MET_A - 1.1, MET_B0, MET_A + 1.1, MET_PIVOT, 0.5, 1).all("#brass"));
        double[] half = {1.0, 0.85, 0.7, 0.55};
        double h = (MET_B1 - MET_PIVOT) / half.length;
        for (int i = 0; i < half.length; i++) {
            double b0 = MET_PIVOT + i * h, b1 = b0 + h, hw = half[i];
            double v0 = 13 - (b1 - MET_PIVOT) * 12 / (MET_B1 - MET_PIVOT), v1 = 13 - (b0 - MET_PIVOT) * 12 / (MET_B1 - MET_PIVOT);
            l.add(onFace(d, MET_A - hw, b0, MET_A + hw, b1, 0.55, 1).all("#metronome").face(d, "#metronome", 8 - hw * 4.6, v0, 8 + hw * 4.6, v1));
        }
        l.add(onFace(d, MET_A - 0.4, MET_B1, MET_A + 0.4, MET_B1 + 0.4, 0.5, 1).all("#brass"));
    }

    static String[][] tex() {
        return new String[][]{{"side0", "interface_side_0"}, {"side1", "interface_side_1"}, {"side2", "interface_side_2"},
                {"side3", "interface_side_3"}, {"top", "interface_top"}, {"bottom", "interface_bottom"}, {"brass", "interface_brass"},
                {"ice", "sat_ice"}, {"metronome", "interface_metronome"}, {"turbine", "interface_turbine"}, {"rod", "cab_dark"}};
    }

    static void models() throws Exception {
        List<Models.El> body = body();
        Models.checkCoplanar("golem_interface", body.toArray(new Models.El[0]));
        Models.model("golem_interface", false, "interface_brass", tex(), body.toArray(new Models.El[0]));
        Forge2.write(Forge2.RES.resolve("blockstates/golem_interface.json"),
                "{\n  \"variants\": {\n    \"\": {\"model\": \"" + Forge2.NS + "golem_interface\"}\n  }\n}\n");
        // the turbine "facing north" in the funnel (centre 8, 8, depth 0.55) and the metronome pendulum (the axis at the model origin)
        Models.model("golem_interface_turbine", false, "interface_turbine", tex(),
                Models.e(5.3, 5.3, 0.55, 10.7, 10.7, 0.55).face("north", "#turbine", 0, 0, 16, 16));
        // the pendulum is flat (thickness 0.1): it swings in the face plane in front of the metronome body
        Models.model("golem_interface_pendulum", false, "interface_brass", tex(),
                Models.e(7.85, 0, 7.95, 8.15, 5.2, 8.05).all("#rod"),
                Models.e(7.5, 3.2, 7.93, 8.5, 4.0, 8.07).all("#brass"),
                Models.e(7.6, -0.3, 7.91, 8.4, 0.3, 8.09).all("#brass"));
        // item: the body and fixed turbines on all sides
        List<Models.El> item = body();
        for (String d : DIRS) {
            Models.El tb = onFace(d, 5.3, 5.3, 10.7, 10.7, 0.55, 0.55);
            tb.faces.put(d, Models.El.face("#turbine", new double[]{0, 0, 16, 16}));
            item.add(tb);
        }
        Models.model("golem_interface_item", true, "interface_brass", tex(), item.toArray(new Models.El[0]));
        Models.item("golem_interface", "golem_interface_item");
        Forge2.addDisplay("golem_interface_item", 225, 0.625);
    }

    static void all() throws Exception {
        textures();
        models();
    }
}
