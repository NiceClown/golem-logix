import java.util.ArrayList;
import java.util.List;

/**
 * Wave 1 (P0) redesign of Golem Interface:
 * Dark espresso walnut core panels, forged cast-iron frame rails & octagonal iris-shutter bezels, riveted weathered-copper
 * corner armor brackets, glowing cyan central apertures behind spinning hollow-center copper iris blades, and antique-bronze
 * clockwork metronomes (preserving InterfaceRenderer alignment 100%).
 */
final class Interface {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, SLATE = Paint.SLATE_IRON,
            CYAN = Paint.CYAN, VIOLET = Paint.VIOLET, FROST = Paint.FROST, COPPER = Atlant.COPPER, CREAM = Paint.CREAM;

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

    /** Deep deepslate-steel recess well behind the spinning iris blades, with a luminous cyan core aperture. */
    static void funnelWell(Paint p, double cx, double cy, double r) {
        p.discFn(cx, cy, r, (u, v) -> {
            double d = Math.hypot(u - cx, v - cy) / r;
            double rings = 0.5 + 0.5 * Math.cos(d * 18);
            return Paint.ramp(SLATE, 0.15 + 0.35 * d + 0.05 * rings);
        });
        p.ring(cx, cy, r - 0.35, r, at(SLATE, 4.2));
        p.disc(cx, cy, r * 0.45, at(CYAN, 4.6));
        p.disc(cx, cy, r * 0.25, at(CYAN, 6.0));
    }

    /** Forged deepslate-gray steel frame rails & octagonal iris-shutter bezel metal. */
    static Paint ironTex() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, 7051);
        p.frame(0, 0, 16, 16, 0.5, at(SLATE, 1.0));
        for (double x = 2.0; x < 16; x += 4.0) {
            p.rivet(x, 8.0, 0.35, SLATE);
        }
        return p;
    }

    /** Weathered riveted copper armor brackets for all 8 corners. */
    static Paint copperTex() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.noise(i, j, 10, 7061);
                int idx = Math.max(1, Math.min(6, 2 + (int) Math.round(n * 3.5)));
                p.px(i, j, rgb(COPPER[idx]));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(COPPER[0]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(COPPER[6]));
        p.rect(0.5, 0.5, 1.0, 15.5, rgb(COPPER[6]));
        for (double[] c : new double[][]{{2.2, 2.2}, {13.8, 2.2}, {2.2, 13.8}, {13.8, 13.8}}) {
            p.rect(c[0] - 0.5, c[1] - 0.5, c[0] + 0.5, c[1] + 0.5, rgb(COPPER[0]));
            p.rect(c[0] - 0.5, c[1] - 0.5, c[0], c[1], rgb(COPPER[7]));
        }
        p.px(2, 2, rgb(Atlant.PATINA[0]));
        p.px(p.w - 3, p.h - 3, rgb(Atlant.PATINA[1]));
        return p;
    }

    /** Warm workshop walnut core planking. */
    static void walnutCore(Paint p, long seed) {
        int[] tones = {0x321f12, 0x3e2716, 0x4b301b, 0x583a21, 0x674629};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double v = (j + 0.5) / K;
                int plank = (int) (v / 3.5);
                double t = Atlant.hash(plank, i / 5, (int) seed) * 0.5 + Atlant.noise(i, j, 6, (int) seed + 1) * 0.5;
                p.px(i, j, rgb(tones[(int) Math.min(4, t * 5)]));
            }
        }
        for (double y = 3.5; y < 16; y += 3.5) {
            p.rect(0, y - 0.35, 16, y, rgb(0x22140b));
        }
    }

    /** Side panel (16x16; area 1..15 visible inside frame): warm workshop walnut, recessed metronome niche, funnel well. */
    static Paint side(long seed) {
        Paint p = new Paint(16, 16, K);
        walnutCore(p, seed);
        p.frame(1.0, 1.0, 15.0, 15.0, 0.5, at(SLATE, 1.2));
        // Deepslate-steel & bronze metronome housing trim at left (matches metA / MET_B0..B1)
        p.slab(1.6, 4.3, 4.1, 11.7, BRONZE, 2.2, 1);
        p.slab(1.9, 4.6, 3.8, 11.4, SLATE, 1.4, 1);
        // Subtle steel corner trim studs
        for (double[] c : new double[][]{{1.9, 1.9}, {14.1, 1.9}, {1.9, 14.1}, {14.1, 14.1}}) {
            p.rivet(c[0], c[1], 0.3, SLATE);
        }
        funnelWell(p, 8, 8, 3.2);
        return p;
    }

    /** Top and bottom panels: warm walnut deck, subtle vent louvers, central funnel well. */
    static Paint top(boolean bottom) {
        Paint p = new Paint(16, 16, K);
        walnutCore(p, bottom ? 7101 : 7102);
        p.frame(1.0, 1.0, 15.0, 15.0, 0.5, at(SLATE, 1.2));
        Terminals.vent(p, 5.8, 1.8, 10.2, 3.3, 3);
        Terminals.vent(p, 5.8, 12.7, 10.2, 14.2, 3);
        funnelWell(p, 8, 8, 3.2);
        return p;
    }

    /** Warm antique brass accent for pendulum bob, metronome escutcheon & hub trim. */
    static Paint brass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.06, 7111, true);
        p.grain(0, 0, 16, 16, 0.04, 7112);
        p.frame(0, 0, 16, 16, 0.5, at(BRASS, 2.0));
        return p;
    }

    /** Saturated luminous frost-cyan aperture core crystal behind every turbine. */
    static Paint coreGlow() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, at(CYAN, 3.8));
        p.disc(8, 8, 5.5, at(CYAN, 4.8));
        p.disc(7.2, 7.2, 2.5, at(CYAN, 5.5));
        return p;
    }

    /**
     * Bold curved copper iris-shutter blades with dark-iron edge shadow and a HOLLOW glowing center aperture (r < 0.95 transparent)
     * so the luminous cyan core behind it shines right through the spinning rotor!
     */
    static Paint turbine() {
        Paint p = new Paint(8, 8, 8);
        for (int b = 0; b < 8; b++) {
            double a0 = b * 2 * Math.PI / 8;
            for (double rr = 1.05; rr < 3.65; rr += 0.05) {
                double a = a0 + rr * 0.48;
                double cx = 4 + Math.cos(a) * rr, cy = 4 + Math.sin(a) * rr;
                double w = 0.32 + rr * 0.09;
                p.disc(cx, cy, w, at(SLATE, 0.7));
            }
        }
        for (int b = 0; b < 8; b++) {
            double a0 = b * 2 * Math.PI / 8;
            for (double rr = 1.05; rr < 3.65; rr += 0.05) {
                double a = a0 + rr * 0.48;
                double cx = 4 + Math.cos(a) * rr, cy = 4 + Math.sin(a) * rr;
                double w = 0.32 + rr * 0.09;
                double shade = 0.25 + 0.55 * (rr / 3.65) + 0.12 * Math.sin(a * 2);
                int idx = Math.max(1, Math.min(7, (int) Math.round(shade * 7)));
                p.disc(cx - Math.sin(a) * 0.06, cy + Math.cos(a) * 0.06, w * 0.78, rgb(COPPER[idx]));
            }
        }
        // Hollow bronze collar around open center aperture (clear center r < 0.92!)
        p.ring(4, 4, 0.92, 1.38, at(SLATE, 1.4));
        p.ring(4, 4, 1.02, 1.28, at(BRASS, 3.5));
        return p;
    }

    /** Warm workshop walnut & antique-bronze metronome scale body. */
    static Paint metronome() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> Paint.mix(rgb(0x321f12), rgb(0x583a21), 0.5 + 0.25 * Math.sin(u * 1.9 + v * 0.3)));
        p.grain(0, 0, 16, 16, 0.06, 7121);
        p.rect(6.5, 1.0, 9.5, 13.0, at(BRASS, 3.0));
        for (int i = 0; i < 9; i++) {
            double y = 1.6 + i * 1.3;
            p.rect(6.9, y, i % 2 == 0 ? 8.8 : 8.0, y + 0.3, at(CREAM, 4.5));
        }
        p.slab(4.5, 13.4, 11.5, 15.4, SLATE, 2.4, 1);
        p.rune(7.2, 13.6, 1.6, 4, at(CYAN, 4.8), 0);
        return p;
    }

    static void textures() throws Exception {
        long[] seeds = {7001, 7002, 7003, 7004};
        for (int i = 0; i < 4; i++) {
            side(seeds[i]).save(Forge2.tex("interface_side_" + i));
        }
        top(false).save(Forge2.tex("interface_top"));
        top(true).save(Forge2.tex("interface_bottom"));
        ironTex().save(Forge2.tex("interface_iron"));
        copperTex().save(Forge2.tex("interface_copper"));
        coreGlow().save(Forge2.tex("interface_core"));
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

    static String opposite(String d) {
        return switch (d) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            case "up" -> "down";
            default -> "up";
        };
    }

    static List<Models.El> body() {
        List<Models.El> l = new ArrayList<>();
        // Sunk dark walnut panels (core 1..15)
        l.add(Models.e(1, 1, 1, 15, 15, 15).face("north", "#side0").face("east", "#side1").face("south", "#side2")
                .face("west", "#side3").face("up", "#top").face("down", "#bottom"));

        // Glowing cyan core aperture backing inside all 6 turbine wells (depth 0.94..0.98 behind turbine depth 0.55)
        for (String d : DIRS) {
            l.add(onFace(d, 6.6, 6.6, 9.4, 9.4, 0.94, 0.98).only("#core", d).glow(14));
        }

        // 8 Riveted weathered-copper corner armor caps (0..2.75 & 13.25..16.0)
        for (double x : new double[]{0.0, 13.25}) {
            for (double y : new double[]{0.0, 13.25}) {
                for (double z : new double[]{0.0, 13.25}) {
                    l.add(Models.e(x, y, z, x + 2.75, y + 2.75, z + 2.75).all("#copper"));
                }
            }
        }
        // Stepped copper corner collar lips (2.75..3.4 & 12.6..13.25) wrapping the 12 edges
        for (double[] span : new double[][]{{2.75, 3.4}, {12.6, 13.25}}) {
            for (double a : new double[]{0.05, 14.45}) {
                for (double b : new double[]{0.05, 14.45}) {
                    l.add(Models.e(span[0], a, b, span[1], a + 1.5, b + 1.5).all("#copper"));
                    l.add(Models.e(a, span[0], b, a + 1.5, span[1], b + 1.5).all("#copper"));
                    l.add(Models.e(a, b, span[0], a + 1.5, b + 1.5, span[1]).all("#copper"));
                }
            }
        }
        // 12 Forged dark cast-iron frame rails running cleanly between copper corner collars (3.4..12.6)
        for (double a : new double[]{0.15, 14.6}) {
            for (double b : new double[]{0.15, 14.6}) {
                l.add(Models.e(3.4, a, b, 12.6, a + 1.25, b + 1.25).only("#iron", "up", "down", "north", "south"));
                l.add(Models.e(a, 3.4, b, a + 1.25, 12.6, b + 1.25).only("#iron", "east", "west", "north", "south"));
                l.add(Models.e(a, b, 3.4, a + 1.25, b + 1.25, 12.6).only("#iron", "east", "west", "up", "down"));
            }
        }

        // Sculpted octagonal dark cast-iron iris-shutter bezels on all 6 faces (outer 4.2..11.8, opening 5.2..10.8)
        for (String d : DIRS) {
            l.add(onFace(d, 5.6, 4.2, 10.4, 5.2, 0.0, 1.0).only("#iron", d, "up", "down", "east", "west", "north", "south"));
            l.add(onFace(d, 5.6, 10.8, 10.4, 11.8, 0.0, 1.0).only("#iron", d, "up", "down", "east", "west", "north", "south"));
            l.add(onFace(d, 4.2, 5.6, 5.2, 10.4, 0.0, 1.0).only("#iron", d, "up", "down", "east", "west", "north", "south"));
            l.add(onFace(d, 10.8, 5.6, 11.8, 10.4, 0.0, 1.0).only("#iron", d, "up", "down", "east", "west", "north", "south"));
            // Strip the inward face against the core (depth 1.0) on chamfer links to avoid any coplanar overlap
            for (double[] cr : new double[][]{{4.6, 4.6}, {10.4, 4.6}, {4.6, 10.4}, {10.4, 10.4}}) {
                Models.El ch = onFace(d, cr[0], cr[1], cr[0] + 1.0, cr[1] + 1.0, 0.1, 0.98).all("#iron");
                ch.faces.remove(opposite(d));
                l.add(ch);
            }
        }

        // Metronomes on the 4 side faces (coordinates 100% preserved for InterfaceRenderer)
        for (String d : new String[]{"north", "south", "east", "west"}) {
            metronome(l, d);
        }
        return l;
    }

    static final double MET_A = 2.85, MET_B0 = 4.6, MET_PIVOT = 5.2, MET_B1 = 11.6;

    static double metA(String d) {
        return d.equals("north") || d.equals("east") ? 16 - MET_A : MET_A;
    }

    static void metronome(List<Models.El> l, String d) {
        double MET_A = metA(d);
        l.add(onFace(d, MET_A - 1.1, MET_B0, MET_A + 1.1, MET_PIVOT, 0.5, 1).all("#iron"));
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
                {"side3", "interface_side_3"}, {"top", "interface_top"}, {"bottom", "interface_bottom"},
                {"iron", "interface_iron"}, {"copper", "interface_copper"}, {"brass", "interface_brass"},
                {"core", "interface_core"}, {"metronome", "interface_metronome"}, {"turbine", "interface_turbine"}, {"rod", "cab_dark"}};
    }

    static void models() throws Exception {
        List<Models.El> body = body();
        Models.checkCoplanar("golem_interface", body.toArray(new Models.El[0]));
        Models.model("golem_interface", false, "interface_iron", tex(), body.toArray(new Models.El[0]));
        Forge2.write(Forge2.RES.resolve("blockstates/golem_interface.json"),
                "{\n  \"variants\": {\n    \"\": {\"model\": \"" + Forge2.NS + "golem_interface\"}\n  }\n}\n");
        // The turbine "facing north" inside the iris collar (centre 8, 8, depth 0.55) and the metronome pendulum (pivot at model origin)
        Models.model("golem_interface_turbine", false, "interface_turbine", tex(),
                Models.e(5.3, 5.3, 0.55, 10.7, 10.7, 0.55).face("north", "#turbine", 0, 0, 16, 16));
        Models.model("golem_interface_pendulum", false, "interface_copper", tex(),
                Models.e(7.85, 0, 7.95, 8.15, 5.2, 8.05).all("#rod"),
                Models.e(7.5, 3.2, 7.93, 8.5, 4.0, 8.07).all("#copper"),
                Models.e(7.6, -0.3, 7.91, 8.4, 0.3, 8.09).all("#brass"));
        // Item: body + fixed turbines on all 6 sides
        List<Models.El> item = body();
        for (String d : DIRS) {
            Models.El tb = onFace(d, 5.3, 5.3, 10.7, 10.7, 0.55, 0.55);
            tb.faces.put(d, Models.El.face("#turbine", new double[]{0, 0, 16, 16}));
            item.add(tb);
        }
        Models.model("golem_interface_item", true, "interface_iron", tex(), item.toArray(new Models.El[0]));
        Models.item("golem_interface", "golem_interface_item");
        Forge2.addDisplay("golem_interface_item", 225, 0.625);
    }

    static void all() throws Exception {
        textures();
        models();
    }
}
