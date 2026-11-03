import java.util.ArrayList;
import java.util.List;

/**
 * The second pass of the block design code over the blocks an art review scored lowest (2026-11-03): no side left plain or
 * cloned. Runs after the older generators and replaces their models.
 * <ul>
 * <li>Channel Expander and Quantum Channel Expander: the lattice window stays on the front and back, the sides get carved copper
 * panels (rune rows round a sigil, filigree corners), the top and bottom a ring of runes round a crystal eye (teal; violet on the
 * quantum one, its mark of rank);</li>
 * <li>Cryo-Calculator: the screen on the front and back only, carved slate-and-brass sides, a rune-ring roof for the snowmen;</li>
 * <li>Cooling Co-Processor: brass rune bands round the base and the lid, a carved lid with a teal core, a brass post;</li>
 * <li>Creative Source Jar: a brass plinth, ribs and neck, a copper rune band, a brass cap with a crystal instead of the cork.</li>
 * </ul>
 */
final class Top6 {
    static final int K = Forge2.K;
    static final int SLATE = 0x2b4560, SLATE_D = 0x16263a, SLATE_L = 0x3d5e80, NIGHT = 0x0a1624;

    private Top6() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    static void all() throws Exception {
        for (boolean quantum : new boolean[]{false, true}) {
            String name = quantum ? "quantum_channel_expander" : "channel_expander";
            int[] glow = quantum ? Paint.VIOLET : Paint.CYAN;
            expanderSide(glow, quantum ? 931 : 921).save(Forge2.tex(name + "_side"));
            expanderTop(glow, quantum ? 932 : 922).save(Forge2.tex(name + "_top"));
            String[][] t = {{"front", name}, {"side", name + "_side"}, {"top", name + "_top"}};
            Models.model(name, false, name, t, Models.e(0, 0, 0, 16, 16, 16).face("north", "#front").face("south", "#front")
                    .face("east", "#side").face("west", "#side").face("up", "#top").face("down", "#top"));
        }
        calculator();
        coprocessor();
        creativeJar();
    }

    /** Slate-iron ground with soft noise. */
    static void slate(Paint p, int seed) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.noise(i, j, 10, seed);
                p.px(i, j, rgb(t < 0.35 ? SLATE_D : t > 0.75 ? SLATE_L : SLATE));
            }
        }
    }

    /** A brass frame with a lit top-left edge and a shadowed bottom-right one. */
    static void brassFrame(Paint p, double w) {
        p.frame(0, 0, 16, 16, w, rgb(Paint.BRASS[3]));
        p.rect(0, 0, 16, 0.4, rgb(Paint.BRASS[5]));
        p.rect(0, 0, 0.4, 16, rgb(Paint.BRASS[5]));
        p.rect(0, 15.6, 16, 16, rgb(Paint.BRASS[1]));
        p.rect(15.6, 0, 16, 16, rgb(Paint.BRASS[1]));
    }

    static Paint expanderSide(int[] glow, int seed) {
        Paint p = new Paint(16, 16, K);
        slate(p, seed);
        brassFrame(p, 1.0);
        // a copper panel set in the frame
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                if (u > 2.2 && u < 13.8 && v > 2.2 && v < 13.8) {
                    double t = Atlant.noise(i, j, 8, seed + 1);
                    p.px(i, j, rgb(Atlant.COPPER[4 + (int) Math.round(t * 2 - 0.5)]));
                }
            }
        }
        p.frame(2.2, 2.2, 13.8, 13.8, 0.35, rgb(Atlant.COPPER[1]));
        Carve.filigree(p, 3.2, 3.2, 12.8, 12.8, 2.2, rgb(Paint.BRASS[3]), rgb(Paint.BRASS[6]));
        Carve.runeRow(p, 5.2, 11.2, 3.4, 0.8, seed, rgb(Atlant.COPPER[0]), rgb(glow[4]));
        Carve.runeRow(p, 5.2, 11.2, 11.0, 0.8, seed + 7, rgb(Atlant.COPPER[0]), rgb(glow[4]));
        Carve.sigil(p, 8, 8, 2.6, rgb(Atlant.COPPER[0]), rgb(glow[5]));
        for (double[] c : new double[][]{{1.0, 1.0}, {14.0, 1.0}, {1.0, 14.0}, {14.0, 14.0}}) {
            p.rivet(c[0] + 0.5, c[1] + 0.5, 0.45, Paint.BRASS);
        }
        return p;
    }

    static Paint expanderTop(int[] glow, int seed) {
        Paint p = new Paint(16, 16, K);
        slate(p, seed);
        brassFrame(p, 1.0);
        p.disc(8, 8, 5.6, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 5.1, rgb(NIGHT));
        Carve.runeRing(p, 8, 8, 3.9, 0.7, 10, seed, rgb(Paint.BRASS[4]), rgb(glow[4]));
        p.disc(8, 8, 2.0, rgb(glow[2]));
        p.disc(8, 8, 1.4, rgb(glow[4]));
        p.disc(7.5, 7.5, 0.5, rgb(glow[6]));
        Carve.filigree(p, 1.8, 1.8, 14.2, 14.2, 1.8, rgb(Paint.BRASS[3]), rgb(Paint.BRASS[6]));
        return p;
    }

    // ------------------------------------------------------------------ calculator

    /** The calculator's side: a slate case in brass, a dark recessed panel of three rune rows round a brass-and-teal sigil. */
    static Paint calcSide(int seed) {
        Paint p = new Paint(16, 16, K);
        slate(p, seed);
        brassFrame(p, 0.9);
        p.rect(2.4, 2.4, 13.6, 13.6, rgb(NIGHT));
        p.frame(2.4, 2.4, 13.6, 13.6, 0.4, rgb(Paint.BRASS[2]));
        Carve.runeRow(p, 3.4, 12.8, 3.3, 0.75, seed, rgb(SLATE_L), rgb(Paint.CYAN[4]));
        Carve.runeRow(p, 3.4, 12.8, 11.2, 0.75, seed + 5, rgb(SLATE_L), rgb(Paint.CYAN[4]));
        p.ring(8, 8, 2.6, 3.1, rgb(Paint.BRASS[4]));
        Carve.sigil(p, 8, 8, 2.2, rgb(Paint.BRASS[5]), rgb(Paint.CYAN[5]));
        Carve.filigree(p, 3.0, 3.0, 13.0, 13.0, 1.6, rgb(Paint.BRASS[3]), rgb(Paint.BRASS[6]));
        for (double[] c : new double[][]{{1.2, 1.2}, {14.8, 1.2}, {1.2, 14.8}, {14.8, 14.8}}) {
            p.rivet(c[0], c[1], 0.45, Paint.BRASS);
        }
        // frost in the lower corners of the frame
        p.frostLine(0.6, 4, 15.2, 0.5, seed + 9);
        p.frostLine(12, 15.4, 15.2, 0.5, seed + 10);
        return p;
    }

    /** The roof where the snowmen work: a slate deck in brass, a ring of runes round a teal vent. */
    static Paint calcTop(int seed) {
        Paint p = new Paint(16, 16, K);
        slate(p, seed);
        brassFrame(p, 0.9);
        p.disc(8, 8, 6.0, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 5.5, rgb(SLATE_D));
        Carve.runeRing(p, 8, 8, 4.3, 0.75, 10, seed, rgb(Paint.BRASS[4]), rgb(Paint.CYAN[4]));
        for (double y = 6.6; y < 9.6; y += 1.0) {
            p.rect(6.2, y, 9.8, y + 0.45, rgb(Paint.CYAN[3]));
        }
        p.ring(8, 8, 2.5, 2.9, rgb(Paint.BRASS[5]));
        for (double[] c : new double[][]{{1.2, 1.2}, {14.8, 1.2}, {1.2, 14.8}, {14.8, 14.8}}) {
            p.rivet(c[0], c[1], 0.45, Paint.BRASS);
        }
        return p;
    }

    static void calculator() throws Exception {
        calcSide(941).save(Forge2.tex("calc_side"));
        calcTop(942).save(Forge2.tex("calc_top"));
        String[][] t = {{"panel", "cryo_panel"}, {"side", "calc_side"}, {"top", "calc_top"}, {"matrix", "cryo_panel_matrix"}};
        Models.model("cryo_calculator", false, "calc_side", t, Models.e(0, 0, 0, 16, 16, 16).face("north", "#panel")
                .face("south", "#panel").face("east", "#side").face("west", "#side").face("up", "#top").face("down", "#top"));
        Models.model("cryo_calculator_active", false, "calc_side", t, Models.e(0, 0, 0, 16, 16, 16).face("north", "#matrix")
                .face("south", "#matrix").face("east", "#side").face("west", "#side").face("up", "#top").face("down", "#top"));
        String[][] it = {{"panel", "cryo_panel"}, {"side", "calc_side"}, {"top", "calc_top"}, {"snow", "minecraft:block/snow"}};
        Models.model("cryo_calculator_item", false, "calc_side", it,
                Models.e(0, 0, 0, 16, 16, 16).face("north", "#panel").face("south", "#panel").face("east", "#side")
                        .face("west", "#side").face("up", "#top").face("down", "#top"),
                Models.e(2.4, 16, 3.6, 5.4, 19, 6.6).all("#snow"), Models.e(2.9, 19, 4.1, 4.9, 21, 6.1).all("#snow"),
                Models.e(10.8, 16, 4.2, 14.4, 19.6, 7.8).all("#snow"), Models.e(11.4, 19.6, 4.8, 13.8, 22, 7.2).all("#snow"),
                Models.e(5.6, 16, 10.6, 8.2, 18.6, 13.2).all("#snow"), Models.e(6.1, 18.6, 11.1, 7.7, 20.2, 12.7).all("#snow"));
    }

    // ------------------------------------------------------------------ coprocessor

    /**
     * The sides of the coprocessor's base and lid (3 pixels each): the texture's bottom band shows on the base, its top band on the
     * lid - brass with a rune row and bolts; the middle is never seen.
     */
    static Paint copBands(int seed) {
        Paint p = new Paint(16, 16, K);
        slate(p, seed);
        for (double y0 : new double[]{0, 13}) {
            p.rect(0, y0, 16, y0 + 3, rgb(Paint.BRASS[3]));
            p.rect(0, y0, 16, y0 + 0.4, rgb(Paint.BRASS[5]));
            p.rect(0, y0 + 2.6, 16, y0 + 3, rgb(Paint.BRASS[1]));
            p.rect(2.2, y0 + 0.8, 13.8, y0 + 2.2, rgb(Paint.BRASS[2]));
            Carve.runeRow(p, 2.6, 13.4, y0 + 0.9, 0.7, seed + (int) y0, rgb(Paint.BRASS[0]), rgb(Paint.CYAN[4]));
            p.rivet(1.1, y0 + 1.5, 0.45, Paint.BRASS);
            p.rivet(14.9, y0 + 1.5, 0.45, Paint.BRASS);
        }
        return p;
    }

    /** The lid (and the base's underside): brass filigree round a teal core in a vent ring. */
    static Paint copLid(int seed) {
        Paint p = new Paint(16, 16, K);
        slate(p, seed);
        brassFrame(p, 0.9);
        Carve.filigree(p, 2.0, 2.0, 14.0, 14.0, 2.4, rgb(Paint.BRASS[3]), rgb(Paint.BRASS[6]));
        p.disc(8, 8, 5.0, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 4.5, rgb(NIGHT));
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            p.line(8 + Math.cos(a) * 2.2, 8 + Math.sin(a) * 2.2, 8 + Math.cos(a) * 4.3, 8 + Math.sin(a) * 4.3, 0.45, rgb(Paint.BRASS[4]));
        }
        p.disc(8, 8, 1.8, rgb(Paint.CYAN[3]));
        p.disc(8, 8, 1.1, rgb(Paint.CYAN[5]));
        p.disc(7.6, 7.6, 0.4, rgb(Paint.CYAN[6]));
        return p;
    }

    static void coprocessor() throws Exception {
        copBands(951).save(Forge2.tex("cop_bands"));
        copLid(952).save(Forge2.tex("cop_lid"));
        String[][] t = {{"bands", "cop_bands"}, {"lid", "cop_lid"}, {"fin", "cryo_fin"}, {"post", "dev_brass_trim"}};
        Models.model("cooling_coprocessor", false, "cop_lid", t,
                Models.e(0, 0, 0, 16, 3, 16).all("#bands").face("up", "#lid").face("down", "#lid"),
                Models.e(7, 3, 7, 9, 13, 9).all("#post"),
                Models.e(0, 13, 0, 16, 16, 16).all("#bands").face("up", "#lid").face("down", "#lid"));
        Models.model("cooling_coprocessor_fin", false, "cryo_fin", t, Models.e(1, 0, 1, 15, 1, 15).all("#fin"));
        List<Models.El> item = new ArrayList<>();
        item.add(Models.e(0, 0, 0, 16, 3, 16).all("#bands").face("up", "#lid").face("down", "#lid"));
        item.add(Models.e(7, 3, 7, 9, 13, 9).all("#post"));
        for (double y : new double[]{3.5, 5.5, 7.5, 9.5, 11.5}) {
            item.add(Models.e(1, y, 1, 15, y + 1, 15).all("#fin"));
        }
        item.add(Models.e(0, 13, 0, 16, 16, 16).all("#bands").face("up", "#lid").face("down", "#lid"));
        Models.model("cooling_coprocessor_item", false, "cop_lid", t, item.toArray(new Models.El[0]));
    }

    // ------------------------------------------------------------------ creative jar

    static void creativeJar() throws Exception {
        String[][] t = {{"glass", "jar_glass"}, {"liquid", "jar_liquid"}, {"brass", "dev_brass_filigree"}, {"trim", "dev_brass_trim"},
                {"copper", "dev_copper_rune"}, {"crystal", "dev_crystal"}};
        List<Models.El> l = new ArrayList<>();
        l.add(Models.e(4, 1.2, 4, 12, 11, 12).all("#liquid"));
        l.add(Models.e(3, 1, 3, 13, 12, 13).all("#glass"));
        // the plinth, four ribs up the glass's corners, a copper rune band round its middle, the neck and a brass cap with a crystal
        l.add(Models.e(2.5, 0, 2.5, 13.5, 1, 13.5).all("#trim").face("up", "#brass"));
        for (double[] c : new double[][]{{2.6, 2.6}, {12.4, 2.6}, {2.6, 12.4}, {12.4, 12.4}}) {
            l.add(Models.e(c[0], 0.5, c[1], c[0] + 1, 12.4, c[1] + 1).all("#trim"));
        }
        l.add(Models.e(2.8, 5.5, 2.8, 13.2, 6.7, 13.2).only("#copper", "north", "south", "east", "west"));
        l.add(Models.e(4, 12, 4, 12, 13, 12).all("#trim"));
        l.add(Models.e(5, 13, 5, 11, 14.4, 11).all("#trim").face("up", "#brass"));
        l.add(Models.e(7, 14.4, 7, 9, 16, 9).all("#crystal").glow(12));
        Models.model("creative_source_jar", true, "jar_glass", t, l.toArray(new Models.El[0]));
    }
}
