import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The four 2.1 devices, third look (after the concepts in concepts/v3): closed models - every part has all its faces, so nothing
 * shows the world through it - and the mod's "techno-magic" dress in the manner of Ars Nouveau's carving: engraved rune bands
 * with thin teal inlays, brass filigree corners, sigil medallions, riveted bands. The shared panels (dev_copper_rune,
 * dev_brass_filigree, dev_iron_sigil, dev_iron_band, dev_walnut_inlay) are meant for the plain sides of other blocks too.
 * <ul>
 * <li>Brass Stock Manometer: a copper console on a brass plinth and crown, built like the buses (an octagonal wall flange, a copper body in
 * brass bands) with a big octagonal clock for a head: the dial in a raised bezel (the needle is the renderer's), a crown with a crystal;</li>
 * <li>Copper Order Ledger: a walnut-topped copper cabinet of drawers with brass corner posts, a slanted lectern with an open
 * ledger, an inkwell and a quill (the clerk golem behind it is the renderer's);</li>
 * <li>Cryo-Diode: a glass pipe section with swirling frost (animated), a filigreed brass valve body with arrows towards the
 * outlet (the facing side), a handwheel on a stem, a wide copper flange at the inlet and a narrower nozzle at the outlet;</li>
 * <li>Iron Security Terminal: an iron vault with gold-filigree bands, a brass plinth, crown and corner posts, a door with a
 * green CRT (animated sweep), a combination dial, a card slot, hinges and a teal lock sigil.</li>
 * </ul>
 */
final class DevicesV3 {
    static final int K = Forge2.K;

    private DevicesV3() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    static int argb(int a, int c) {
        return (a << 24) | (c & 0xffffff);
    }

    static File tex(String name) {
        return Forge2.tex(name);
    }

    static File entityTex(String name) {
        return Forge2.RES.resolve("textures/entity/" + name + ".png").toFile();
    }

    /** A palette ramp lo..hi laid as soft noise over the whole canvas. */
    static void fill(Paint p, int[] pal, int lo, int hi, int seed) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.noise(i, j, 10, seed);
                p.px(i, j, rgb(pal[Math.max(0, Math.min(pal.length - 1, lo + (int) Math.round(t * (hi - lo))))]));
            }
        }
    }

    /** A rune: three of six strokes (a 1.5 x 2.5 cell, 0.5 thick) chosen by the seed - an engraved letter of an old alphabet. */
    static void rune(Paint p, double x, double y, int seed, int c) {
        double[][] strokes = {{x, y, x, y + 2.5}, {x + 1.5, y, x + 1.5, y + 2.5}, {x, y, x + 1.5, y}, {x, y + 1.25, x + 1.5, y + 1.25},
                {x, y + 2.5, x + 1.5, y}, {x, y + 2.5, x + 1.5, y + 2.5}};
        int drawn = 0;
        for (int k = 0; k < strokes.length && drawn < 3; k++) {
            if (Atlant.hash(seed, k, 517) > 0.45 || k == 5 - drawn) {
                double[] s = strokes[k];
                p.line(s[0], s[1], s[2], s[3], 0.5, c);
                drawn++;
            }
        }
    }

    /** An engraved band: two grooves and a row of runes between them, a few of them inlaid in glowing teal. */
    static void runeBand(Paint p, double y, double x0, double x1, int seed, int groove, int glyph, int inlay) {
        p.rect(x0, y, x1, y + 0.5, groove);
        p.rect(x0, y + 3.5, x1, y + 4, groove);
        int n = 0;
        for (double x = x0 + 0.75; x + 1.5 <= x1 - 0.5; x += 2.25) {
            rune(p, x, y + 0.75, seed * 31 + n, Atlant.hash(seed, n, 211) > 0.7 ? inlay : glyph);
            n++;
        }
    }

    static void rivet(Paint p, double x, double y, int[] pal) {
        p.rect(x, y, x + 1, y + 1, rgb(pal[1]));
        p.rect(x, y, x + 0.5, y + 0.5, rgb(pal[5]));
    }

    /** Filigree corners: a curl of brass wire in each corner of a panel. */
    static void filigree(Paint p, int c, int hi) {
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                double x = cx == 0 ? 1 : 15, y = cy == 0 ? 1 : 15, sx = cx == 0 ? 1 : -1, sy = cy == 0 ? 1 : -1;
                p.line(x, y, x + 3.5 * sx, y, 0.5, c);
                p.line(x, y, x, y + 3.5 * sy, 0.5, c);
                p.ring(x + 2.25 * sx, y + 2.25 * sy, 0.9, 1.4, c);
                p.rect(Math.min(x, x + 0.5 * sx), Math.min(y, y + 0.5 * sy), Math.max(x, x + 0.5 * sx), Math.max(y, y + 0.5 * sy), hi);
            }
        }
    }

    // ------------------------------------------------------------------ shared panels

    /** Riveted copper with an engraved rune band across the middle, its runes partly inlaid in teal; a thin brass edge. */
    static Paint copperRune(int seed) {
        Paint p = new Paint(16, 16, K);
        fill(p, Atlant.COPPER, 2, 6, seed);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[3]));
        runeBand(p, 6, 1, 15, seed, rgb(Atlant.COPPER[0]), rgb(0x5a2414), rgb(Paint.CYAN[4]));
        p.rect(1, 2.5, 15, 3, rgb(Atlant.COPPER[7]));
        p.rect(1, 13, 15, 13.5, rgb(Atlant.COPPER[1]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            rivet(p, r[0], r[1], Paint.BRASS);
        }
        return p;
    }

    /** Brass with filigree corners, a double border and a sigil medallion with a teal heart. */
    static Paint brassFiligree(int seed) {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.BRASS, 3, 5, seed);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.BRASS[2]));
        filigree(p, rgb(Paint.BRASS[1]), rgb(Paint.BRASS[6]));
        p.ring(8, 8, 2.6, 3.3, rgb(Paint.BRASS[1]));
        p.disc(8, 8, 1.6, rgb(Paint.CYAN[3]));
        p.disc(7.6, 7.6, 0.6, rgb(Paint.CYAN[6]));
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            p.disc(8 + Math.cos(a) * 4.3, 8 + Math.sin(a) * 4.3, 0.45, rgb(Paint.BRASS[6]));
        }
        return p;
    }

    /** A dark iron plate with brass corner brackets and a glowing teal sigil: a ring, a triangle and an eye of light. */
    static Paint ironSigil(int seed) {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.IRON, 2, 4, seed);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.IRON[1]));
        for (double[] c : new double[][]{{0.5, 0.5, 1, 1}, {15.5, 0.5, -1, 1}, {0.5, 15.5, 1, -1}, {15.5, 15.5, -1, -1}}) {
            p.line(c[0], c[1], c[0] + 3 * c[2], c[1], 1, rgb(Paint.BRASS[4]));
            p.line(c[0], c[1], c[0], c[1] + 3 * c[3], 1, rgb(Paint.BRASS[4]));
        }
        p.ring(8, 8, 4.1, 4.7, rgb(Paint.CYAN[3]));
        double r = 3.6;
        for (int k = 0; k < 3; k++) {
            double a0 = -Math.PI / 2 + k * 2 * Math.PI / 3, a1 = a0 + 2 * Math.PI / 3;
            p.line(8 + Math.cos(a0) * r, 8 + Math.sin(a0) * r, 8 + Math.cos(a1) * r, 8 + Math.sin(a1) * r, 0.5, rgb(Paint.CYAN[4]));
        }
        p.disc(8, 8.4, 1, rgb(Paint.CYAN[5]));
        p.disc(8, 8.4, 0.45, rgb(Paint.CYAN[6]));
        return p;
    }

    /** Iron with two gold filigree bands (top and bottom) studded with rune dots and rivets: a vault's sides. */
    static Paint ironBand(int seed) {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.IRON, 2, 4, seed);
        for (double y : new double[]{2, 11.5}) {
            p.rect(0, y, 16, y + 2.5, rgb(Paint.BRASS[4]));
            p.rect(0, y, 16, y + 0.5, rgb(Paint.BRASS[6]));
            p.rect(0, y + 2, 16, y + 2.5, rgb(Paint.BRASS[2]));
            for (double x = 1; x < 16; x += 3) {
                p.ring(x + 0.75, y + 1.25, 0.4, 0.75, rgb(Paint.BRASS[2]));
            }
        }
        p.rect(7.5, 5, 8.5, 11, rgb(Paint.IRON[1]));
        runeBand(p, 6, 2, 14, seed, rgb(Paint.IRON[1]), rgb(Paint.IRON[0]), rgb(Paint.CYAN[3]));
        return p;
    }

    /** Walnut planks with thin teal inlay lines running along and branching like runes. */
    static Paint walnutInlay(int seed) {
        Paint p = new Paint(16, 16, K);
        int[] tones = {0x2a1a10, 0x34200f, 0x3e2814, 0x482f18, 0x553a1f};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double v = (j + 0.5) / K;
                int plank = (int) (v / 4);
                double t = Atlant.hash(plank, i / 5, seed) * 0.5 + Atlant.noise(i, j, 6, seed + 1) * 0.5;
                p.px(i, j, rgb(tones[(int) Math.min(4, t * 5)]));
            }
        }
        for (double y = 4; y < 16; y += 4) {
            p.rect(0, y - 0.5, 16, y, rgb(0x1c110a));
        }
        p.line(1, 2, 9, 2, 0.5, rgb(Paint.CYAN[3]));
        p.line(9, 2, 10.5, 3.5, 0.5, rgb(Paint.CYAN[3]));
        p.line(6, 10, 15, 10, 0.5, rgb(Paint.CYAN[3]));
        p.line(6, 10, 4.5, 11.5, 0.5, rgb(Paint.CYAN[3]));
        p.disc(10.5, 3.5, 0.5, rgb(Paint.CYAN[5]));
        p.disc(4.5, 11.5, 0.5, rgb(Paint.CYAN[5]));
        return p;
    }

    /** Plain brass for trims and posts: a soft vertical gradient and an edge line. */
    static Paint brassTrim(int seed) {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.BRASS, 3, 5, seed);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        p.rect(0.5, 0.5, 15.5, 1, rgb(Paint.BRASS[6]));
        return p;
    }

    /** Clear glass with a faint tint and two highlight streaks (translucent). */
    static Paint glass() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, argb(0x40, 0xc4e2ea));
            }
        }
        p.line(3, 1, 3, 15, 0.5, argb(0xa0, 0xffffff));
        p.line(4.5, 2, 4.5, 9, 0.5, argb(0x70, 0xffffff));
        p.frame(0, 0, 16, 16, 0.5, argb(0x90, 0xd8eef4));
        return p;
    }

    // ------------------------------------------------------------------ ledger: the clerk's poppy and the turning page

    /** The poppy's stem with two leaves (an entity texture, drawn as crossed quads on the clerk's head). */
    static Paint flowerStem() {
        Paint p = new Paint(16, 16, K);
        p.line(8, 16, 8, 5.5, 1.0, rgb(0x2c5220));
        p.line(8.2, 15, 8.2, 6, 0.35, rgb(0x6aa646));
        p.line(8, 13.2, 4.4, 10.6, 1.5, rgb(0x4a8232));
        p.line(7.6, 12.8, 5, 11, 0.4, rgb(0x78b452));
        p.line(8, 11.2, 11.6, 8.8, 1.5, rgb(0x4a8232));
        p.line(8.4, 10.8, 11, 9.2, 0.4, rgb(0x78b452));
        return p;
    }

    /** The poppy's blossom in greys: the renderer tints it red, yellow or green by the ledger's status; a dark heart. */
    static Paint flowerPetals() {
        Paint p = new Paint(16, 16, K);
        p.disc(8, 4.6, 3.8, rgb(0xb4b4b4));
        p.disc(5.4, 5.4, 2.2, rgb(0xe6e6e6));
        p.disc(10.6, 5.4, 2.2, rgb(0xe6e6e6));
        p.disc(8, 2.8, 2.3, rgb(0xf6f6f6));
        p.disc(8, 6.4, 2.0, rgb(0xd4d4d4));
        p.line(8, 1.2, 8, 3.6, 0.3, rgb(0xc8c8c8));
        p.disc(8, 4.8, 1.2, rgb(0x1c1c1c));
        p.disc(7.6, 4.4, 0.35, rgb(0x5a5a3a));
        return p;
    }

    /** A ledger page: cream paper with ruled lines and a column of figures. */
    static Paint page() {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.CREAM, 4, 5, 2171);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.CREAM[2]));
        for (double y = 2.5; y < 15; y += 1.5) {
            p.line(1.5, y, 14.5, y, 0.25, rgb(Paint.CREAM[1]));
            p.line(11, y - 0.9, 13.5, y - 0.9, 0.35, rgb(0x3a3028));
            p.line(2, y - 0.9, 2 + 3 + Atlant.hash((int) (y * 2), 0, 2172) * 5, y - 0.9, 0.3, rgb(0x5a4a3c));
        }
        p.line(10.3, 1, 10.3, 15, 0.25, rgb(0xb02818));
        return p;
    }

    // ------------------------------------------------------------------ manometer

    /** The dial: brass bezel, a cream face with a ring of runes for numbers, ticks and a red zone (the needle is the renderer's). */
    static Paint dial() {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.BRASS, 2, 4, 2161);
        p.disc(8, 8, 8, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 7.4, rgb(Paint.BRASS[5]));
        p.ring(8, 8, 6.9, 7.4, rgb(Paint.BRASS[6]));
        p.disc(8, 8, 6.4, rgb(Paint.CREAM[5]));
        p.ring(8, 8, 6, 6.4, rgb(Paint.CREAM[2]));
        for (int k = 0; k <= 10; k++) {
            double a = Math.toRadians(135 + k * 27);
            p.line(8 + Math.cos(a) * 4.6, 8 + Math.sin(a) * 4.6, 8 + Math.cos(a) * 5.8, 8 + Math.sin(a) * 5.8, k % 5 == 0 ? 0.7 : 0.45,
                    rgb(k >= 9 ? 0xb02818 : 0x2a2420));
        }
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(150 + k * 48);
            p.disc(8 + Math.cos(a) * 3.4, 8 + Math.sin(a) * 3.4, 0.35, rgb(Paint.BRASS[2]));
        }
        p.rect(6, 10.5, 10, 11, rgb(Paint.BRASS[3]));
        p.disc(8, 8, 0.9, rgb(Paint.BRASS[1]));
        return p;
    }

    /** Plain copper for the Manometer's bridges (an entity texture for the renderer), where carving would only read as noise. */
    static Paint copperPlain() {
        Paint p = new Paint(16, 16, K);
        fill(p, Atlant.COPPER, 4, 6, 2191);
        return p;
    }

    static Paint crystal() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = (double) i / p.w * 0.6 + (1 - (double) j / p.h) * 0.4;
                p.px(i, j, rgb(Paint.CYAN[Math.min(6, 2 + (int) (t * 5))]));
            }
        }
        p.line(4, 2, 6, 14, 1, rgb(Paint.CYAN[6]));
        p.line(10, 3, 12, 13, 0.5, rgb(Paint.CYAN[1]));
        return p;
    }

    static Paint liquid() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, argb(0xc8, Paint.CYAN[3 + (int) Math.round(Atlant.noise(i, j, 6, 977))]));
            }
        }
        p.rect(0, 0, 16, 1.5, argb(0xe8, Paint.CYAN[6]));
        return p;
    }

    // ------------------------------------------------------------------ ledger

    /** The cabinet's front: three rows of walnut drawers in a copper frame, brass pulls and cream label plates with runes. */
    static Paint drawers() {
        Paint p = walnutInlay(7310);
        p.frame(0, 0, 16, 16, 1, rgb(Atlant.COPPER[3]));
        p.rect(0, 0, 16, 0.5, rgb(Atlant.COPPER[7]));
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 2; c++) {
                double x0 = 1.5 + c * 6.75, y0 = 1.5 + r * 4.5;
                p.frame(x0, y0, x0 + 6.25, y0 + 4, 0.5, rgb(0x1c110a));
                p.rect(x0 + 1.75, y0 + 0.9, x0 + 4.5, y0 + 1.9, rgb(Paint.CREAM[4]));
                p.line(x0 + 2.1, y0 + 1.4, x0 + 4.1, y0 + 1.4, 0.35, rgb(Paint.CREAM[1]));
                p.rect(x0 + 2.4, y0 + 2.6, x0 + 3.9, y0 + 3.1, rgb(Paint.BRASS[5]));
                p.rect(x0 + 2.4, y0 + 2.6, x0 + 3.9, y0 + 2.8, rgb(Paint.BRASS[6]));
            }
        }
        return p;
    }

    /** The open ledger from above: two cream pages, a dark spine, ruled lines with entries, a red ribbon. */
    static Paint book() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(0x5a2414));
        p.rect(0.75, 0.75, 7.75, 15.25, rgb(Paint.CREAM[5]));
        p.rect(8.25, 0.75, 15.25, 15.25, rgb(Paint.CREAM[5]));
        p.rect(7.75, 0.5, 8.25, 15.5, rgb(0x2a1008));
        for (double y = 2.5; y < 15; y += 1.5) {
            p.line(1.5, y, 7, y, 0.3, rgb(Paint.CREAM[2]));
            p.line(9, y, 14.5, y, 0.3, rgb(Paint.CREAM[2]));
            double len = 2 + Atlant.hash((int) (y * 2), 1, 733) * 3.5;
            p.line(1.75, y - 0.45, 1.75 + len, y - 0.45, 0.35, rgb(0x3a2a20));
            if (y < 9) {
                double len2 = 2 + Atlant.hash((int) (y * 2), 2, 733) * 3.5;
                p.line(9.25, y - 0.45, 9.25 + len2, y - 0.45, 0.35, rgb(0x3a2a20));
            }
        }
        p.rect(10.5, 9.5, 14, 13, rgb(Paint.CREAM[3]));
        rune(p, 11.5, 9.9, 91, rgb(Paint.CYAN[3]));
        p.rect(12, 0, 12.75, 16, rgb(0xa01818));
        return p;
    }

    static Paint quill() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(Paint.CREAM[5]));
        for (double y = 1; y < 15; y += 2) {
            p.line(0, y, 16, y + 1, 0.5, rgb(Paint.CREAM[3]));
        }
        p.rect(7, 0, 9, 16, rgb(Paint.CREAM[6]));
        return p;
    }

    // ------------------------------------------------------------------ diode

    /** Frost swirling inside the pipe (animated): snow motes spiralling along the flow over a cold glow. */
    static Paint frost() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 16;
        p.frameTime = 2;
        p.interpolate = false;
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, argb(0x70, Paint.FROST[2 + (int) Math.round(Atlant.noise(i, j, 8, 4401) * 2)]));
            }
        }
        p.animate(0, 0, 16, 16, (q, t) -> {
            for (int k = 0; k < 14; k++) {
                double y = (Atlant.hash(k, 3, 4402) * 16 - t * 16 + 32) % 16;
                double x = 8 + Math.sin((y / 16 + Atlant.hash(k, 4, 4402)) * Math.PI * 2) * 5.5;
                q.rect(x, y, x + 0.5 + (k % 3 == 0 ? 0.5 : 0), y + 0.5, argb(0xe0, k % 4 == 0 ? Paint.CYAN[5] : 0xffffff));
            }
        });
        return p;
    }

    /** The valve body: filigreed brass with a teal arrow towards the outlet; dir 0 = right, 1 = left, 2 = up (in the texture). */
    /**
     * The valve's side: a dark bronze plate in a brass bevel with an arrow in the manner of Ars Nouveau, cut in and inlaid in teal -
     * a stem swaying like a whiplash from a curl at its tail, two barbs sweeping back from the point into curls of their own, a
     * bead behind the point. dir: 0 points right, 1 left, 2 up.
     */
    static Paint valve(int dir) {
        Paint p = brassFiligree(5520);
        p.rect(2.4, 2.4, 13.6, 13.6, rgb(Paint.BRONZE[1]));
        p.rect(3.4, 3.4, 12.6, 12.6, rgb(Paint.BRONZE[2]));
        p.frame(2.4, 2.4, 13.6, 13.6, 0.5, rgb(Paint.BRASS[3]));
        p.rect(2.4, 2.4, 13.6, 2.7, rgb(Paint.BRASS[5]));
        p.rect(2.4, 2.4, 2.7, 13.6, rgb(Paint.BRASS[5]));
        List<double[]> stem = new ArrayList<>(), barbA = new ArrayList<>(), barbB = new ArrayList<>();
        for (double t = 0; t <= 1.001; t += 0.05) {
            stem.add(new double[]{4.5 + 7.6 * t, 8 + 1.5 * Math.sin(t * Math.PI * 2) * (1 - t)});
            // quadratic curves from the point back, ending in a turn inward
            double u = 1 - t;
            double x = u * u * 12.6 + 2 * u * t * 11.2 + t * t * 8.6;
            double y = u * u * 8 + 2 * u * t * 6.6 + t * t * 4.6;
            barbA.add(new double[]{x, y});
            barbB.add(new double[]{x, 16 - y});
        }
        for (int pass = 0; pass < 3; pass++) {
            int c = rgb(pass == 0 ? Paint.BRONZE[0] : pass == 1 ? Paint.CYAN[3] : Paint.CYAN[5]);
            double extra = pass == 0 ? 0.5 : pass == 1 ? 0 : -0.45;
            curve(p, stem, 0.85 + extra, c, dir);
            curve(p, barbA, 0.75 + extra, c, dir);
            curve(p, barbB, 0.75 + extra, c, dir);
            for (double[] k : new double[][]{{4.6, 8, 1.0, 1}, {8.6, 4.6, 0.75, 1}, {8.6, 11.4, 0.75, 1}, {10.5, 8, 0.8, 0}}) {
                double[] m = turned(k[0], k[1], dir);
                if (k[3] > 0) {
                    p.ring(m[0], m[1], Math.max(0.1, k[2] - 0.45 - extra / 2), k[2] + extra / 2, c);
                } else {
                    p.disc(m[0], m[1], k[2] + extra / 2, c);
                }
            }
        }
        return p;
    }

    private static double[] turned(double x, double y, int dir) {
        return dir == 1 ? new double[]{16 - x, y} : dir == 2 ? new double[]{y, 16 - x} : new double[]{x, y};
    }

    private static void curve(Paint p, List<double[]> pts, double w, int c, int dir) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = turned(pts.get(i)[0], pts.get(i)[1], dir), b = turned(pts.get(i + 1)[0], pts.get(i + 1)[1], dir);
            p.line(a[0], a[1], b[0], b[1], w, c);
        }
    }

    /** The five-handled wheel (an entity texture for the renderer): a brass rim, five spokes with knobs, a teal-capped hub. */
    static Paint wheel5() {
        Paint p = new Paint(16, 16, K);
        p.ring(8, 8, 5.2, 6.4, rgb(Paint.BRASS[4]));
        p.ring(8, 8, 5.9, 6.4, rgb(Paint.BRASS[6]));
        p.ring(8, 8, 5.2, 5.5, rgb(Paint.BRASS[2]));
        for (int k = 0; k < 5; k++) {
            double a = Math.PI * 2 * k / 5 - Math.PI / 2;
            p.line(8, 8, 8 + Math.cos(a) * 5.6, 8 + Math.sin(a) * 5.6, 1.1, rgb(Paint.BRASS[3]));
            p.line(8, 8, 8 + Math.cos(a) * 5.6, 8 + Math.sin(a) * 5.6, 0.4, rgb(Paint.BRASS[5]));
            p.disc(8 + Math.cos(a) * 7.0, 8 + Math.sin(a) * 7.0, 0.95, rgb(Paint.BRASS[5]));
            p.disc(8 + Math.cos(a) * 7.0, 8 + Math.sin(a) * 7.0, 0.45, rgb(Paint.BRASS[6]));
        }
        p.disc(8, 8, 1.9, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 1.2, rgb(Paint.CYAN[4]));
        p.disc(7.6, 7.6, 0.4, rgb(Paint.CYAN[6]));
        return p;
    }

    /** The handwheel: a brass rim, four spokes and a hub on transparency (cutout). */
    static Paint wheel() {
        Paint p = new Paint(16, 16, K);
        p.ring(8, 8, 6.2, 7.6, rgb(Paint.BRASS[4]));
        p.ring(8, 8, 7.1, 7.6, rgb(Paint.BRASS[6]));
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + Math.PI / 4;
            p.line(8, 8, 8 + Math.cos(a) * 6.5, 8 + Math.sin(a) * 6.5, 1, rgb(Paint.BRASS[3]));
        }
        p.disc(8, 8, 1.6, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 0.7, rgb(Paint.CYAN[4]));
        return p;
    }

    /** A copper flange ring with rivets; its middle (radius hole, in texture units) left open for the glass behind. */
    static Paint flange(double hole) {
        Paint p = new Paint(16, 16, K);
        fill(p, Atlant.COPPER, 3, 6, 6620);
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[0]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            rivet(p, r[0], r[1], Paint.BRASS);
        }
        p.ring(8, 8, hole, hole + 0.6, rgb(Paint.BRASS[4]));
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double x = (i + 0.5) / K - 8, y = (j + 0.5) / K - 8;
                if (x * x + y * y < hole * hole) {
                    p.px(i, j, 0);
                }
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ security terminal

    /** The vault door: the iron sigil plate in a gold frame. */
    static Paint door() {
        Paint p = ironSigil(8810);
        p.frame(0, 0, 16, 16, 1, rgb(Paint.BRASS[4]));
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        return p;
    }

    /** The CRT (animated): a green radar - rings, a sweeping beam, blips that light up as it passes. */
    static Paint screen() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 20;
        p.frameTime = 2;
        p.interpolate = false;
        p.rect(0, 0, 16, 16, rgb(0x061a0c));
        p.ring(8, 8, 6.4, 6.9, rgb(0x1f7a3a));
        p.ring(8, 8, 3.7, 4.1, rgb(0x14522a));
        p.line(8, 1.5, 8, 14.5, 0.35, rgb(0x14522a));
        p.line(1.5, 8, 14.5, 8, 0.35, rgb(0x14522a));
        p.animate(0, 0, 16, 16, (q, t) -> {
            double a = t * Math.PI * 2;
            for (int k = 0; k < 4; k++) {
                double b = a - k * 0.18;
                q.line(8, 8, 8 + Math.cos(b) * 6.4, 8 + Math.sin(b) * 6.4, 0.6, rgb(k == 0 ? 0x8cff9e : k == 1 ? 0x46d266 : 0x1f9a3f));
            }
            double[][] blips = {{11, 5, 0.1}, {5, 10.5, 0.55}, {10, 11, 0.8}};
            for (double[] b : blips) {
                double since = (t - b[2] + 1) % 1;
                if (since < 0.35) {
                    q.disc(b[0], b[1], 0.7, rgb(since < 0.12 ? 0xd4ffd8 : 0x46d266));
                }
            }
        });
        return p;
    }

    /** The combination dial: a brass knob with a ring of ticks and a pointer notch. */
    static Paint combo() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(Paint.IRON[2]));
        p.disc(8, 8, 7.6, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 7, rgb(Paint.BRASS[4]));
        for (int k = 0; k < 20; k++) {
            double a = Math.PI * 2 * k / 20;
            p.line(8 + Math.cos(a) * 5.4, 8 + Math.sin(a) * 5.4, 8 + Math.cos(a) * 6.6, 8 + Math.sin(a) * 6.6, k % 5 == 0 ? 0.7 : 0.4,
                    rgb(Paint.BRASS[1]));
        }
        p.disc(8, 8, 4.4, rgb(Paint.BRASS[5]));
        p.disc(8, 8, 3.4, rgb(Paint.BRASS[3]));
        p.rect(7.6, 0.6, 8.4, 2.6, rgb(0xb02818));
        p.disc(7, 7, 1, rgb(Paint.BRASS[6]));
        return p;
    }

    /** The card slot: a brass plate with a black slit and a copper punch card's corner sticking out. */
    static Paint slot() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(Paint.BRASS[4]));
        p.frame(0, 0, 16, 16, 1, rgb(Paint.BRASS[2]));
        p.rect(2, 6.5, 14, 9.5, rgb(0x0a0806));
        p.rect(5, 4, 11, 7.5, rgb(Atlant.COPPER[5]));
        for (double x = 5.5; x < 11; x += 1.5) {
            p.rect(x, 4.75, x + 0.5, 5.25, rgb(Atlant.COPPER[0]));
        }
        return p;
    }

    // ------------------------------------------------------------------ models

    static Models.El e(double a, double b, double c, double d, double f, double g) {
        return Models.e(a, b, c, d, f, g);
    }

    static void models() throws Exception {
        // ---- Brass Stock Manometer (facing6, front north, the back on the block it hangs on): built like our buses - an octagonal
        // flange on the wall, a copper body in brass bands - but its head is a big octagonal clock: the dial in a raised bezel, a
        // pocket-watch crown with a crystal on top. The dial face is the plane z = 5.48 (the needle is the renderer's).
        String[][] mt = {{"copper", "dev_copper_rune"}, {"brass", "dev_brass_filigree"}, {"trim", "dev_brass_trim"},
                {"dial", "dev_manometer_dial"}, {"crystal", "dev_crystal"}};
        List<Models.El> m = new ArrayList<>();
        Buses.oct(m, 2, 2, 14.5, 14, 14, 16, 2.6, "trim", "trim", "trim", "brass", "trim");
        Buses.oct(m, 4.5, 4.5, 8.5, 11.5, 11.5, 14.5, 1.6, "copper", "copper", "trim", null, null);
        Pipes.ring(m, 4.1, 11.9, 11.2, 12, 1.75, "trim", 0.6);
        Buses.oct(m, 1.5, 1.5, 5.5, 14.5, 14.5, 8.5, 3.8, "trim", "brass", "trim", "dial", "brass");
        int bezel = m.size();
        Pipes.ring(m, 1.1, 14.9, 4.7, 5.5, 3.95, "trim", 0.9);
        // the ring's four straight pieces get their inner sides too: from an angle the world showed through the bezel
        String[] inner = {"down", "up", "east", "west"};
        for (int k = 0; k < 4; k++) {
            m.get(bezel + k).face(inner[k], "#trim");
        }
        m.add(e(7.25, 14.5, 6.25, 8.75, 15.25, 7.75).all("#trim"));
        m.add(e(6.5, 15.25, 5.5, 9.5, 16, 8.5).all("#trim").face("up", "#crystal").glow(10));
        Models.model("stock_manometer", true, "dev_brass_filigree", mt, m.toArray(new Models.El[0]));

        // ---- Copper Order Ledger (horizontal, front north): cabinet, lectern sloping down to the front, inkwell, quill
        String[][] lt = {{"copper", "dev_copper_rune"}, {"walnut", "dev_walnut_inlay"}, {"drawers", "dev_ledger_drawers"},
                {"trim", "dev_brass_trim"}, {"book", "dev_ledger_book"}, {"quill", "dev_ledger_quill"}, {"iron", "dev_iron_sigil"}};
        List<Models.El> l = new ArrayList<>();
        l.add(e(0, 0, 0, 16, 2, 16).all("#walnut"));
        l.add(e(1, 2, 1.5, 15, 10, 15).only("#copper", "south", "east", "west").face("north", "#drawers"));
        for (double[] c : new double[][]{{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            l.add(e(c[0], 2, c[1], c[0] + 2, 11.5, c[1] + 2).all("#trim"));
        }
        l.add(e(0.5, 10, 0.5, 15.5, 11, 15.5).all("#walnut"));
        l.add(e(2, 11, 3, 14, 12, 13).all("#walnut").rot("x", 22.5, 8, 11, 8));
        l.add(e(2.5, 12, 3.5, 13.5, 12.5, 12.5).all("#quill").face("up", "#book").rot("x", 22.5, 8, 11, 8));
        l.add(e(12, 11, 13.5, 14.5, 13, 15.5).all("#trim"));
        l.add(e(12.5, 13, 14, 14, 13.25, 15).all("#iron"));
        l.add(e(13, 12.5, 14.25, 13.5, 18, 14.75).all("#quill").rot("z", 22.5, 13.25, 13, 14.5));
        Models.model("stock_requester", false, "dev_copper_rune", lt, l.toArray(new Models.El[0]));

        // ---- Cryo-Diode (facing6 = the outlet; drawn north): the pipe's own straight glass section (the blockstate applies it) with
        // a brass valve on it - copper bands at both ends, the body with arrows to the outlet, a bonnet and a stem; the five-handled
        // wheel on the stem is the renderer's (it turns while Source flows)
        String[][] dt = {{"brass", "dev_brass_filigree"}, {"trim", "dev_brass_trim"}, {"copper", "dev_copper_rune"},
                {"valve_r", "dev_diode_valve_r"}, {"valve_l", "dev_diode_valve_l"}, {"valve_u", "dev_diode_valve_u"}};
        List<Models.El> d = new ArrayList<>();
        d.add(e(3.5, 3.5, 5, 12.5, 12.5, 11).all("#trim").face("east", "#valve_r").face("west", "#valve_l").face("up", "#valve_u")
                .face("down", "#brass"));
        d.add(e(3, 3, 4.25, 13, 13, 5).all("#copper"));
        d.add(e(3, 3, 11, 13, 13, 11.75).all("#copper"));
        d.add(e(5.5, 12.5, 5.5, 10.5, 13.5, 10.5).all("#trim"));
        // the stem runs up through the wheel's hub to a cap, so the wheel sits on its axle
        d.add(e(7.25, 13.5, 7.25, 8.75, 15.9, 8.75).all("#trim"));
        d.add(e(6.75, 15.9, 6.75, 9.25, 16, 9.25).all("#copper"));
        Models.model("cryo_diode", true, "dev_brass_filigree", dt, d.toArray(new Models.El[0]));
        // the blockstate: the pipe's straight section along the facing, the valve turned the same way
        String[] fac = {"north", "east", "south", "west", "up", "down"};
        String[] rot = {"", ", \"y\": 90", ", \"y\": 180", ", \"y\": 270", ", \"x\": 270", ", \"x\": 90"};
        StringBuilder bs = new StringBuilder("{\n  \"multipart\": [\n");
        for (int k = 0; k < 6; k++) {
            bs.append("    {\"when\": {\"facing\": \"").append(fac[k]).append("\"}, \"apply\": {\"model\": \"golem_logix:block/basic_snow_pipe_straight\"")
                    .append(rot[k]).append("}},\n");
            bs.append("    {\"when\": {\"facing\": \"").append(fac[k]).append("\"}, \"apply\": {\"model\": \"golem_logix:block/cryo_diode\"")
                    .append(rot[k]).append("}}").append(k < 5 ? ",\n" : "\n");
        }
        java.nio.file.Files.writeString(Forge2.RES.resolve("blockstates/cryo_diode.json"), bs.append("  ]\n}\n").toString());

        // ---- Iron Security Terminal (horizontal, front north)
        String[][] st = {{"band", "dev_iron_band"}, {"door", "dev_security_door"}, {"trim", "dev_brass_trim"}, {"brass", "dev_brass_filigree"},
                {"screen", "dev_security_screen"}, {"combo", "dev_security_combo"}, {"slot", "dev_security_slot"}, {"iron", "dev_iron_sigil"}};
        List<Models.El> s = new ArrayList<>();
        s.add(e(0, 0, 0, 16, 1.5, 16).all("#trim"));
        s.add(e(1, 1.5, 1, 15, 14.5, 15).only("#band", "south", "east", "west").face("north", "#door"));
        s.add(e(0, 14.5, 0, 16, 16, 16).all("#brass"));
        for (double[] c : new double[][]{{0, 0}, {14.5, 0}, {0, 14.5}, {14.5, 14.5}}) {
            s.add(e(c[0], 1.5, c[1], c[0] + 1.5, 14.5, c[1] + 1.5).all("#trim"));
        }
        s.add(e(2.5, 8.5, 0.25, 8, 13, 1).all("#trim").face("north", "#screen"));
        s.add(e(8.5, 2.5, 0, 13.5, 7.5, 1).all("#trim").face("north", "#combo"));
        s.add(e(2.5, 3.5, 0.5, 7.5, 6.5, 1).all("#trim").face("north", "#slot"));
        s.add(e(13.25, 9.5, 0.25, 14.5, 12.5, 1).all("#trim"));
        Models.model("security_terminal", false, "dev_iron_band", st, s.toArray(new Models.El[0]));

        for (String n : new String[]{"stock_manometer", "stock_requester", "cryo_diode", "security_terminal"}) {
            Models.item(n, n);
        }
    }

    static void all() throws Exception {
        copperRune(7101).save(tex("dev_copper_rune"));
        brassFiligree(7102).save(tex("dev_brass_filigree"));
        ironSigil(7103).save(tex("dev_iron_sigil"));
        ironBand(7104).save(tex("dev_iron_band"));
        walnutInlay(7105).save(tex("dev_walnut_inlay"));
        brassTrim(7106).save(tex("dev_brass_trim"));
        glass().save(tex("dev_glass"));
        dial().save(tex("dev_manometer_dial"));
        crystal().save(tex("dev_crystal"));
        copperPlain().save(entityTex("dev_copper_plain"));
        drawers().save(tex("dev_ledger_drawers"));
        book().save(tex("dev_ledger_book"));
        quill().save(tex("dev_ledger_quill"));
        flowerStem().save(entityTex("dev_ledger_flower_stem"));
        flowerPetals().save(entityTex("dev_ledger_flower_petals"));
        page().save(entityTex("dev_ledger_page"));
        frost().save(tex("dev_diode_frost"));
        valve(0).save(tex("dev_diode_valve_r"));
        valve(1).save(tex("dev_diode_valve_l"));
        valve(2).save(tex("dev_diode_valve_u"));
        wheel().save(tex("dev_diode_wheel"));
        wheel5().save(entityTex("dev_diode_wheel5"));
        flange(5.8).save(tex("dev_diode_inlet"));
        flange(7.1).save(tex("dev_diode_outlet"));
        door().save(tex("dev_security_door"));
        screen().save(tex("dev_security_screen"));
        combo().save(tex("dev_security_combo"));
        slot().save(tex("dev_security_slot"));
        models();
    }
}
