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

    /** Aged antique brass for trims and posts: a soft vertical gradient and an edge line. */
    static Paint brassTrim(int seed) {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.AGED_BRASS, 2, 4, seed);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.AGED_BRASS[1]));
        p.rect(0.5, 0.5, 15.5, 1, rgb(Paint.AGED_BRASS[5]));
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
        // it ends where the blossom begins: two layers in one plane would flicker against each other
        p.line(8, 16, 8, 8.8, 1.0, rgb(0x2c5220));
        p.line(8.2, 15, 8.2, 9, 0.35, rgb(0x6aa646));
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

    /**
     * The turning page: plain paper with a soft edge. An entity texture has no mipmaps, so anything fine on it shimmers while the
     * page turns.
     */
    static Paint page() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(Paint.CREAM[5]));
        p.frame(0, 0, 16, 16, 1, rgb(Paint.CREAM[4]));
        return p;
    }

    // ------------------------------------------------------------------ manometer

    /** High-contrast circular warm parchment dial face in an antique-brass inner rim (needle is rendered by BER). */
    static Paint dial() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(Paint.SLATE_IRON[1]));
        p.disc(8, 8, 7.8, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 7.1, rgb(Paint.BRASS[4]));
        p.disc(8, 8, 6.4, rgb(Paint.CREAM[5]));
        p.ring(8, 8, 5.9, 6.4, rgb(Paint.CREAM[2]));
        for (int k = 0; k <= 10; k++) {
            double a = Math.toRadians(135 + k * 27);
            double r0 = k % 5 == 0 ? 4.1 : 4.6;
            int col = k >= 8 ? rgb(0xb8241a) : rgb(0x221c18);
            p.line(8 + Math.cos(a) * r0, 8 + Math.sin(a) * r0, 8 + Math.cos(a) * 5.8, 8 + Math.sin(a) * 5.8,
                    k % 5 == 0 ? 0.7 : 0.45, col);
        }
        p.rect(6.2, 10.6, 9.8, 11.2, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 1.0, rgb(Paint.SLATE_IRON[0]));
        return p;
    }

    /** Deepslate-gray forged steel pressure manifold texture for Stock Manometer. */
    static Paint manoIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, 2111);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.SLATE_IRON[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            rivet(p, r[0], r[1], Paint.SLATE_IRON);
        }
        return p;
    }

    /** Weathered riveted copper for Stock Manometer top cap & flank bridge ports. */
    static Paint manoCopper() {
        Paint p = new Paint(16, 16, K);
        fill(p, Atlant.COPPER, 1, 5, 2121);
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[0]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(Atlant.COPPER[6]));
        p.rect(1.0, 7.5, 15.0, 8.0, rgb(Atlant.COPPER[1]));
        p.rect(1.0, 8.0, 15.0, 8.5, rgb(Atlant.COPPER[5]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1, r[1] + 1, rgb(Atlant.COPPER[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(Atlant.COPPER[6]));
        }
        p.px(2, 3, rgb(Atlant.PATINA[0]));
        p.px(p.w - 3, p.h - 4, rgb(Atlant.PATINA[1]));
        return p;
    }

    /** Warm antique bronze-brass for Stock Manometer notched dial bezel. */
    static Paint manoBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 2.9, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, 2131, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[1]));
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, rgb(Paint.BRASS[4]));
        return p;
    }

    /** Vertical luminous cyan cryo sight-tube texture with crisp highlight streak. */
    static Paint manoTube() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K;
                double e = Math.abs(u - 8.0) / 8.0;
                int c = e > 0.72 ? Paint.CYAN[2] : e < 0.25 ? Paint.CYAN[5] : Paint.CYAN[4];
                p.px(i, j, rgb(c));
            }
        }
        p.rect(4.5, 0.5, 6.0, 15.5, rgb(Paint.CYAN[6]));
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.CYAN[1]));
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
        // faint ruling only: the renderer draws what the ledger keeps on these pages
        for (double y = 2.5; y < 15; y += 2) {
            p.line(2.75, y, 7.25, y, 0.3, rgb(Paint.CREAM[4]));
            p.line(8.75, y, 12.75, y, 0.3, rgb(Paint.CREAM[4]));
        }
        p.rect(13, 0, 13.4, 16, rgb(0xa01818));
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

    /** Deepslate-gray forged steel with bolted bezel highlights for Cryo-Diode end flanges, window trim, and stem. */
    static Paint diodeIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.7, 1);
        p.grain(0, 0, 16, 16, 0.06, 4410);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.SLATE_IRON[5]));
        return p;
    }

    /** Weathered riveted copper with crisp panel bevels for Cryo-Diode central valve housing. */
    static Paint diodeCopper() {
        Paint p = new Paint(16, 16, K);
        fill(p, Atlant.COPPER, 1, 5, 4420);
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[0]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(Atlant.COPPER[6]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(Atlant.COPPER[1]));
        p.rect(0.5, 8.0, 15.5, 8.5, rgb(Atlant.COPPER[5]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1, r[1] + 1, rgb(Atlant.COPPER[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(Atlant.COPPER[6]));
        }
        p.px(2, 2, rgb(Atlant.PATINA[1]));
        p.px(p.w - 3, p.h - 3, rgb(Atlant.PATINA[0]));
        return p;
    }

    /** Warm antique brass for Cryo-Diode bonnet gland nut & handwheel center hub. */
    static Paint diodeBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 3.0, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, 4430, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        return p;
    }

    /** Translucent frosted ice sight-glass for Cryo-Diode side windows (clear interior so 3D arrow shines through). */
    static Paint diodeGlass() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                double d = Math.min(Math.min(u, 16 - u), Math.min(v, 16 - v));
                if (d < 1.2) {
                    p.px(i, j, argb(0xb5, Paint.FROST[4]));
                } else {
                    p.px(i, j, argb(0x52, Paint.FROST[2]));
                }
            }
        }
        p.line(2.5, 12.5, 5.5, 9.5, 0.5, argb(0xc0, 0xffffff));
        p.line(10.5, 5.0, 13.5, 2.0, 0.5, argb(0xb0, 0xffffff));
        return p;
    }

    /** Luminous frost-cyan directional chevron crystal texture for Cryo-Diode core. */
    static Paint diodeArrow() {
        Paint p = new Paint(16, 16, K);
        fill(p, Paint.CYAN, 2, 5, 4440);
        p.frame(0, 0, 16, 16, 1.0, rgb(Paint.CYAN[2]));
        p.frame(1.0, 1.0, 15.0, 15.0, 1.0, rgb(Paint.CYAN[5]));
        p.rect(2.0, 2.0, 14.0, 14.0, rgb(Paint.CYAN[4]));
        p.rect(3.5, 3.5, 10.0, 10.0, rgb(Paint.CYAN[6]));
        p.rect(5.5, 5.5, 8.0, 8.0, rgb(0xe8ffff));
        return p;
    }

    // ------------------------------------------------------------------ security terminal

    /** Warm workshop walnut planking matching Order Ledger & Pattern Encoder. */
    static Paint secWood() {
        Paint p = new Paint(16, 16, K);
        int[] tones = {0x321f12, 0x3e2716, 0x4b301b, 0x583a21, 0x674629};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double v = (j + 0.5) / K;
                int plank = (int) (v / 4);
                double t = Atlant.hash(plank, i / 5, 8801) * 0.5 + Atlant.noise(i, j, 6, 8802) * 0.5;
                p.px(i, j, rgb(tones[(int) Math.min(4, t * 5)]));
            }
        }
        for (double y = 4; y < 16; y += 4) {
            p.rect(0, y - 0.5, 16, y, rgb(0x22140b));
        }
        return p;
    }

    /** Forged deepslate-gray steel with crisp bevel & rivets for Security Terminal plinth, pilasters & cornice. */
    static Paint secIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, 8811);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.frame(1.0, 1.0, 15.0, 15.0, 0.5, rgb(Paint.SLATE_IRON[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            rivet(p, r[0], r[1], Paint.SLATE_IRON);
        }
        return p;
    }

    /** Warm antique brass for Security Terminal CRT hood & punch-card reader shelf. */
    static Paint secBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 3.0, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, 8821, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        return p;
    }

    /** Sculpted antique-brass vault keyhole escutcheon plate for Security Terminal sides. */
    static Paint secKeyhole() {
        Paint p = secBrass();
        p.slab(1.5, 1.5, 14.5, 14.5, Paint.BRASS, 2.3, 1);
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.BRASS[1]));
        p.frame(2.0, 2.0, 14.0, 14.0, 0.5, rgb(Paint.BRASS[5]));
        for (double[] r : new double[][]{{2.5, 2.5}, {12.5, 2.5}, {2.5, 12.5}, {12.5, 12.5}}) {
            rivet(p, r[0], r[1], Paint.BRASS);
        }
        // Keyhole cutout silhouette with crisp inner shadow & highlight bevel rim
        p.disc(8.0, 6.2, 2.75, rgb(Paint.BRASS[5]));
        p.disc(8.0, 6.2, 2.35, rgb(Paint.SLATE_IRON[0]));
        double[] xsOut = {6.2, 9.8, 10.7, 5.3};
        double[] ysOut = {7.0, 7.0, 13.0, 13.0};
        p.poly(xsOut, ysOut, rgb(Paint.BRASS[5]));
        double[] xsMid = {6.45, 9.55, 10.4, 5.6};
        double[] ysMid = {7.0, 7.0, 12.8, 12.8};
        p.poly(xsMid, ysMid, rgb(Paint.SLATE_IRON[0]));
        p.disc(8.0, 6.2, 2.05, rgb(0x16181d));
        double[] xs = {6.7, 9.3, 10.0, 6.0};
        double[] ys = {7.0, 7.0, 12.4, 12.4};
        p.poly(xs, ys, rgb(0x16181d));
        return p;
    }

    /** Deepslate-gray steel back panel with cooling louvers and centered Frost Pipe socket. */
    static Paint secBack() {
        Paint p = secIron();
        Terminals.vent(p, 3.0, 2.0, 13.0, 7.0, 4);
        p.slab(5.0, 8.5, 11.0, 14.0, Paint.BRASS, 3.0, 1);
        p.disc(8.0, 11.25, 1.9, rgb(0x16181d));
        p.ring(8.0, 11.25, 1.9, 2.4, rgb(Paint.BRASS[5]));
        return p;
    }

    /** Glowing status indicator jewel face (red or green). */
    static Paint secLed(boolean red) {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(red ? 0x64100c : 0x0c4e1c));
        p.frame(0, 0, 16, 16, 1.5, rgb(Paint.IRON[0]));
        p.rect(2.5, 2.5, 13.5, 13.5, rgb(red ? 0xe03224 : 0x38d868));
        p.rect(3.5, 3.5, 7.0, 7.0, rgb(red ? 0xff9a8a : 0xaaffbf));
        return p;
    }

    /** The CRT (animated): a green radar - rings, a sweeping beam, blips that light up as it passes. */
    static Paint screen() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 20;
        p.frameTime = 2;
        p.interpolate = false;
        p.rect(0, 0, 16, 16, rgb(0x031208));
        p.ring(8, 8, 6.2, 6.8, rgb(0x1f7a3a));
        p.ring(8, 8, 3.5, 4.0, rgb(0x14522a));
        p.line(8, 1.5, 8, 14.5, 0.4, rgb(0x14522a));
        p.line(1.5, 8, 14.5, 8, 0.4, rgb(0x14522a));
        p.animate(0, 0, 16, 16, (q, t) -> {
            double a = t * Math.PI * 2;
            for (int k = 0; k < 4; k++) {
                double b = a - k * 0.18;
                q.line(8, 8, 8 + Math.cos(b) * 6.3, 8 + Math.sin(b) * 6.3, 0.65,
                        rgb(k == 0 ? 0x8cff9e : k == 1 ? 0x46d266 : 0x1f9a3f));
            }
            double[][] blips = {{11, 5, 0.1}, {5, 10.5, 0.55}, {10, 11, 0.8}};
            for (double[] b : blips) {
                double since = (t - b[2] + 1) % 1;
                if (since < 0.35) {
                    q.disc(b[0], b[1], 0.75, rgb(since < 0.12 ? 0xd4ffd8 : 0x46d266));
                }
            }
        });
        return p;
    }

    // ------------------------------------------------------------------ models

    static Models.El e(double a, double b, double c, double d, double f, double g) {
        return Models.e(a, b, c, d, f, g);
    }

    static void models() throws Exception {
        // ---- 1. Stock Manometer (facing6, front north, back on z=16; preserves BER bridges & miniature redstone golems)
        String[][] mt = {{"iron", "dev_mano_iron"}, {"copper", "dev_mano_copper"}, {"brass", "dev_mano_brass"},
                {"dial", "dev_manometer_dial"}, {"tube", "dev_mano_tube"}};
        List<Models.El> m = new ArrayList<>();
        // Bolted dark cast-iron rear wall flange (z=14.5..16.0) + 4 brass corner bolt studs
        m.add(e(1.5, 1.5, 14.5, 14.5, 14.5, 16.0).all("#iron"));
        for (double[] b : new double[][]{{2.25, 2.25}, {12.75, 2.25}, {2.25, 12.75}, {12.75, 12.75}}) {
            m.add(e(b[0], b[1], 14.1, b[0] + 1.0, b[1] + 1.0, 14.5).only("#brass", "north", "up", "down", "east", "west"));
        }
        // Dark cast-iron pressure manifold core + bolted lower baseplate + riveted copper top cap + side bridge ports
        m.add(e(2.75, 2.5, 5.75, 13.25, 12.5, 14.5).all("#iron"));
        m.add(e(2.0, 1.0, 4.75, 14.0, 2.5, 15.0).all("#iron"));
        for (double bx : new double[]{2.1, 13.3}) {
            m.add(e(bx, 2.5, 4.9, bx + 0.6, 2.9, 5.5).only("#brass", "north", "south", "east", "west", "up"));
            m.add(e(bx, 2.5, 13.8, bx + 0.6, 2.9, 14.4).only("#brass", "north", "south", "east", "west", "up"));
        }
        m.add(e(2.5, 12.5, 5.5, 13.5, 13.5, 14.75).all("#copper"));
        m.add(e(1.8, 6.75, 7.5, 2.75, 9.25, 10.5).all("#copper"));
        m.add(e(13.25, 6.75, 7.5, 14.2, 9.25, 10.5).all("#copper"));
        // Deeply recessed antique-brass octagonal bezel (3.75..5.75) + inner sides + knurled teeth + recessed dial face at z=4.9
        int bezel = m.size();
        Pipes.ring(m, 2.25, 13.75, 3.75, 5.75, 3.1, "brass", 1.05);
        String[] inner = {"down", "up", "east", "west"};
        for (int k = 0; k < 4; k++) {
            m.get(bezel + k).face(inner[k], "#iron");
        }
        m.add(e(7.25, 13.75, 4.0, 8.75, 14.1, 5.25).only("#brass", "north", "south", "east", "west", "up"));
        m.add(e(7.25, 1.9, 4.0, 8.75, 2.25, 5.25).only("#brass", "north", "south", "east", "west", "down"));
        m.add(e(1.9, 7.25, 4.0, 2.25, 8.75, 5.25).only("#brass", "north", "south", "west", "up", "down"));
        m.add(e(13.75, 7.25, 4.0, 14.1, 8.75, 5.25).only("#brass", "north", "south", "east", "up", "down"));
        m.add(e(3.2, 3.2, 4.9, 12.8, 12.8, 5.73).only("#iron", "east", "west", "up", "down").face("north", "#dial", 0, 0, 16, 16));
        // Top vertical glowing cyan cryo sight-tube
        m.add(e(6.0, 13.5, 8.0, 10.0, 14.5, 12.0).only("#iron", "north", "south", "east", "west", "up"));
        m.add(e(6.75, 14.5, 8.75, 9.25, 16.25, 11.25).only("#tube", "north", "south", "east", "west").glow(14));
        m.add(e(6.5, 16.25, 8.5, 9.5, 16.75, 11.5).all("#iron"));
        Models.model("stock_manometer", false, "dev_mano_iron", mt, m.toArray(new Models.El[0]));
        GolemBuses.cutout("stock_manometer");

        // ---- Copper Order Ledger (horizontal, front north): UNTOUCHED BENCHMARK ----
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

        // ---- 2. Cryo-Diode (facing6 = outlet north; self-contained pipe stubs + bolted iron flanges + copper chamber + glowing 3D chevron + 3D wheel)
        String[][] dt = {{"iron", "dev_diode_iron"}, {"copper", "dev_diode_copper"}, {"brass", "dev_diode_brass"},
                {"fglass", "dev_diode_glass"}, {"arrow", "dev_diode_arrow"}, {"pipe_u", "pipe_glass_u"}, {"pipe_v", "pipe_glass_v"},
                {"pipe_d", "pipe_glass_d"}, {"clamp", "pipe_clamp"}, {"wheel", "pipe_wheel"}};
        List<Models.El> d = new ArrayList<>();
        // Inlet & outlet pipe stubs with copper clamps (stopping cleanly at z=2.05 / z=13.95)
        Pipes.tube(d, 4.0, 12.0, 0.0, 2.05, 1.3, "pipe_u", "pipe_v", "pipe_d");
        Pipes.ring(d, 3.4, 12.6, 0.06, 1.0, 1.6, "clamp", 0.65);
        Pipes.tube(d, 4.0, 12.0, 13.95, 16.0, 1.3, "pipe_u", "pipe_v", "pipe_d");
        Pipes.ring(d, 3.4, 12.6, 15.0, 15.94, 1.6, "clamp", 0.65);

        // Heavy bolted dark cast-iron octagonal flanges (z=2.05..4.0 & z=12.0..13.95) + solid inner seal cores
        Pipes.ring(d, 2.25, 13.75, 2.05, 4.0, 2.75, "iron", 1.75);
        d.add(e(4.25, 4.25, 2.5, 11.75, 11.75, 4.0).all("#iron"));
        Pipes.ring(d, 2.25, 13.75, 12.0, 13.95, 2.75, "iron", 1.75);
        d.add(e(4.25, 4.25, 12.0, 11.75, 11.75, 13.5).all("#iron"));
        for (double[] b : new double[][]{{7.6, 2.6}, {7.6, 12.6}, {2.6, 7.6}, {12.6, 7.6},
                {3.75, 3.75}, {11.45, 3.75}, {3.75, 11.45}, {11.45, 11.45}}) {
            d.add(e(b[0], b[1], 1.7, b[0] + 0.8, b[1] + 0.8, 2.05).only("#iron", "north", "up", "down", "east", "west"));
            d.add(e(b[0], b[1], 13.95, b[0] + 0.8, b[1] + 0.8, 14.3).only("#iron", "south", "up", "down", "east", "west"));
        }

        // Framed riveted copper valve chamber (z=4.0..12.0, x/y 3.25..12.75)
        d.add(e(3.25, 11.25, 4.0, 12.75, 12.75, 12.0).all("#copper"));
        d.add(e(3.25, 3.25, 4.0, 12.75, 4.75, 12.0).all("#copper"));
        for (double cx : new double[]{3.25, 11.25}) {
            for (double cz : new double[]{4.0, 10.75}) {
                d.add(e(cx, 4.75, cz, cx + 1.5, 11.25, cz + 1.25).only("#copper", "north", "south", "east", "west"));
            }
        }
        // Inner dark cast-iron seal walls & window sills
        d.add(e(4.75, 4.75, 4.2, 11.25, 11.25, 4.9).only("#iron", "south", "east", "west"));
        d.add(e(4.75, 4.75, 11.1, 11.25, 11.25, 11.8).only("#iron", "north", "east", "west"));
        for (boolean west : new boolean[]{true, false}) {
            double x0 = west ? 3.5 : 11.75, x1 = west ? 4.25 : 12.5;
            d.add(e(x0, 4.75, 5.25, x1, 5.25, 10.75).all("#iron"));
            d.add(e(x0, 10.75, 5.25, x1, 11.25, 10.75).all("#iron"));
        }
        // Single-layer frosted sight-glass panes on east & west windows (zero double-translucent glitches)
        d.add(e(3.8, 5.25, 5.25, 3.9, 10.75, 10.75).only("#fglass", "west"));
        d.add(e(12.1, 5.25, 5.25, 12.2, 10.75, 10.75).only("#fglass", "east"));

        // 3D glowing cyan directional chevron arrow pointing NORTH (-Z) inside chamber
        d.add(e(5.1, 7.25, 7.8, 10.9, 8.75, 10.6).all("#arrow").glow(14));
        d.add(e(5.1, 5.75, 6.8, 10.9, 10.25, 7.8).all("#arrow").glow(14));
        d.add(e(5.1, 6.5, 6.0, 10.9, 9.5, 6.8).all("#arrow").glow(14));
        d.add(e(5.1, 7.25, 5.2, 10.9, 8.75, 6.0).all("#arrow").glow(14));

        // Top bonnet, brass gland nut & iron axle stem (y=12.75..16.0)
        d.add(e(5.25, 12.75, 5.25, 10.75, 13.5, 10.75).only("#iron", "north", "south", "east", "west", "up"));
        for (double bx : new double[]{5.5, 9.75}) {
            for (double bz : new double[]{5.5, 9.75}) {
                d.add(e(bx, 13.5, bz, bx + 0.75, 13.8, bz + 0.75).only("#brass", "north", "south", "east", "west", "up"));
            }
        }
        d.add(e(6.25, 13.5, 6.25, 9.75, 14.5, 9.75).only("#brass", "north", "south", "east", "west", "up"));
        d.add(e(7.25, 14.5, 7.25, 8.75, 15.65, 8.75).only("#iron", "north", "south", "east", "west"));
        d.add(e(6.9, 15.65, 6.9, 9.1, 16.0, 9.1).all("#brass"));

        Models.model("cryo_diode", true, "dev_diode_copper", dt, d.toArray(new Models.El[0]));
        Models.facing6("cryo_diode", "cryo_diode");

        // 3D Crimson-Iron & Antique-Brass Handwheel (standalone BER model + combined item model)
        List<Models.El> wheel = new ArrayList<>();
        double ap = 4.6, rt = 0.95, edge = 2 * ap * Math.tan(Math.PI / 8) + 0.1;
        double wy0 = 14.6, wy1 = 15.6;
        for (int k = 0; k < 4; k++) {
            double[] sx = {0, 0, -1, 1}, sz = {-1, 1, 0, 0};
            double cx = 8 + sx[k] * (ap - rt / 2), cz = 8 + sz[k] * (ap - rt / 2);
            double hx = sx[k] == 0 ? edge / 2 : rt / 2, hz = sz[k] == 0 ? edge / 2 : rt / 2;
            wheel.add(e(cx - hx, wy0, cz - hz, cx + hx, wy1, cz + hz).all("#wheel"));
            double ang = Math.PI / 4 + k * Math.PI / 2, dx = 8 + Math.cos(ang) * (ap - rt / 2), dz = 8 + Math.sin(ang) * (ap - rt / 2);
            wheel.add(e(dx - edge / 2, wy0 + 0.03, dz - rt / 2, dx + edge / 2, wy1 - 0.03, dz + rt / 2).all("#wheel")
                    .rot("y", k % 2 == 0 ? 45 : -45, dx, wy0, dz));
        }
        // 4 spokes cleanly outside hub (6.75..9.25) and inside rim
        wheel.add(e(4.2, 14.8, 7.55, 6.75, 15.4, 8.45).only("#wheel", "up", "down", "north", "south"));
        wheel.add(e(9.25, 14.8, 7.55, 11.8, 15.4, 8.45).only("#wheel", "up", "down", "north", "south"));
        wheel.add(e(7.55, 14.8, 4.2, 8.45, 15.4, 6.75).only("#wheel", "up", "down", "east", "west"));
        wheel.add(e(7.55, 14.8, 9.25, 8.45, 15.4, 11.8).only("#wheel", "up", "down", "east", "west"));
        wheel.add(e(6.75, 14.55, 6.75, 9.25, 15.65, 9.25).all("#brass"));
        Models.model("cryo_diode_wheel", false, "pipe_wheel",
                new String[][]{{"wheel", "pipe_wheel"}, {"brass", "dev_diode_brass"}}, wheel.toArray(new Models.El[0]));
        List<Models.El> dItem = new ArrayList<>(d);
        dItem.addAll(wheel);
        Models.model("cryo_diode_item", true, "dev_diode_copper", dt, dItem.toArray(new Models.El[0]));
        Models.item("cryo_diode", "cryo_diode_item");

        // ---- 3. Security Terminal (horizontal, front north: walnut & cast-iron vault console matching Order Ledger)
        String[][] st = {{"wood", "dev_sec_wood"}, {"iron", "dev_sec_iron"}, {"brass", "dev_sec_brass"}, {"copper", "dev_diode_copper"},
                {"screen", "dev_security_screen"}, {"keyhole", "dev_sec_keyhole"}, {"back", "dev_sec_back"},
                {"led_r", "dev_sec_led_r"}, {"led_g", "dev_sec_led_g"}};
        List<Models.El> s = new ArrayList<>();
        // Dark cast-iron plinth & stepped cornice roof
        s.add(e(0.5, 0.0, 0.5, 15.5, 1.5, 15.5).all("#iron"));
        s.add(e(0.75, 13.5, 0.75, 15.25, 14.3, 15.25).all("#iron"));
        s.add(e(0.25, 14.3, 0.25, 15.75, 15.2, 15.75).all("#iron"));
        s.add(e(1.25, 15.2, 1.25, 14.75, 16.0, 14.75).all("#iron"));
        // 4 heavy cast-iron corner pilasters
        for (double[] c : new double[][]{{0.75, 0.75}, {13.75, 0.75}, {0.75, 13.75}, {13.75, 13.75}}) {
            s.add(e(c[0], 1.5, c[1], c[0] + 1.5, 13.5, c[1] + 1.5).only("#iron", "north", "south", "east", "west"));
        }
        // Dark espresso walnut cabinet body strictly inside pilasters
        s.add(e(1.75, 1.5, 1.75, 14.25, 13.5, 14.25).only("#wood", "north", "east", "west").face("south", "#back"));
        // Deeply recessed animated CRT radar screen + protruding antique-brass glare hood & bezel
        s.add(e(4.5, 6.25, 1.4, 11.5, 12.25, 1.75).only("#iron", "south", "east", "west").face("north", "#screen", 0, 0, 16, 16).glow(10));
        s.add(e(4.0, 12.25, 0.25, 12.0, 13.0, 1.75).only("#brass", "north", "east", "west", "up", "down"));
        s.add(e(5.25, 13.0, 0.45, 10.75, 13.45, 1.75).only("#brass", "north", "east", "west", "up"));
        s.add(e(4.25, 5.5, 0.85, 11.75, 6.25, 1.75).only("#brass", "north", "east", "west", "up", "down"));
        s.add(e(3.5, 9.0, 0.45, 4.5, 12.25, 1.75).only("#brass", "north", "east", "west", "up", "down"));
        s.add(e(11.5, 9.0, 0.45, 12.5, 12.25, 1.75).only("#brass", "north", "east", "west", "up", "down"));
        s.add(e(3.75, 6.25, 0.85, 4.5, 9.0, 1.75).only("#brass", "north", "east", "west", "down"));
        s.add(e(11.5, 6.25, 0.85, 12.25, 9.0, 1.75).only("#brass", "north", "east", "west", "down"));
        // Protruding antique-brass punch-card reader tray, dark slot slit & protruding copper punch card tab
        s.add(e(3.0, 3.25, 1.15, 10.25, 4.75, 1.75).only("#brass", "north", "east", "west", "up"));
        s.add(e(3.75, 3.6, 1.05, 9.5, 4.1, 1.15).only("#iron", "north", "east", "west", "up", "down"));
        s.add(e(5.0, 3.65, 0.35, 8.5, 4.0, 1.05).only("#copper", "north", "east", "west", "up", "down"));
        s.add(e(3.25, 2.25, 0.2, 10.0, 3.25, 1.75).only("#brass", "north", "east", "west", "up", "down"));
        // Status indicator jewel pod (crimson & emerald lights)
        s.add(e(11.0, 2.25, 1.05, 13.0, 5.0, 1.75).only("#iron", "north", "east", "west", "up", "down"));
        s.add(e(11.5, 3.7, 0.8, 12.5, 4.55, 1.05).only("#led_r", "north", "east", "west", "up", "down").glow(12));
        s.add(e(11.5, 2.6, 0.8, 12.5, 3.45, 1.05).only("#led_g", "north", "east", "west", "up", "down").glow(12));
        // Side antique-brass vault keyhole escutcheon plates
        s.add(e(1.2, 4.0, 4.25, 1.75, 11.5, 11.75).only("#brass", "north", "south", "west", "up", "down"));
        s.add(e(1.05, 4.75, 5.0, 1.2, 10.75, 11.0).only("#brass", "north", "south", "up", "down").face("west", "#keyhole", 0, 0, 16, 16));
        s.add(e(14.25, 4.0, 4.25, 14.8, 11.5, 11.75).only("#brass", "north", "south", "east", "up", "down"));
        s.add(e(14.8, 4.75, 5.0, 14.95, 10.75, 11.0).only("#brass", "north", "south", "up", "down").face("east", "#keyhole", 0, 0, 16, 16));
        Models.model("security_terminal", false, "dev_sec_wood", st, s.toArray(new Models.El[0]));

        for (String n : new String[]{"stock_manometer", "stock_requester", "security_terminal"}) {
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
        manoIron().save(tex("dev_mano_iron"));
        manoCopper().save(tex("dev_mano_copper"));
        manoBrass().save(tex("dev_mano_brass"));
        manoTube().save(tex("dev_mano_tube"));
        crystal().save(tex("dev_crystal"));
        copperPlain().save(entityTex("dev_copper_plain"));

        drawers().save(tex("dev_ledger_drawers"));
        book().save(tex("dev_ledger_book"));
        quill().save(tex("dev_ledger_quill"));
        flowerStem().save(entityTex("dev_ledger_flower_stem"));
        flowerPetals().save(entityTex("dev_ledger_flower_petals"));
        page().save(entityTex("dev_ledger_page"));

        diodeIron().save(tex("dev_diode_iron"));
        diodeCopper().save(tex("dev_diode_copper"));
        diodeBrass().save(tex("dev_diode_brass"));
        diodeGlass().save(tex("dev_diode_glass"));
        diodeArrow().save(tex("dev_diode_arrow"));

        secWood().save(tex("dev_sec_wood"));
        secIron().save(tex("dev_sec_iron"));
        secBrass().save(tex("dev_sec_brass"));
        secKeyhole().save(tex("dev_sec_keyhole"));
        secBack().save(tex("dev_sec_back"));
        secLed(true).save(tex("dev_sec_led_r"));
        secLed(false).save(tex("dev_sec_led_g"));
        screen().save(tex("dev_security_screen"));
        models();
    }
}
