/**
 * The Atlant in the game's own style: an iron golem cast as a caryatid, holding the array up with raised arms. The frame is
 * the vanilla iron golem's grey iron; copper is laid over it (the chest plate, the bands at the joints, the belt, the hands
 * and feet, the brow), with rare flecks of patina and the golem's vines; the mod's accent is the cyan rune and the eyes.
 * Textures are drawn in block space (16 units at 2 pixels a unit), so every face of the model samples them at the same density.
 */
final class Atlant {
    static final int K = Forge2.K;
    /** The iron golem's iron, dark to light (sampled from iron_golem). */
    static final int[] IRON = {0x4f4a43, 0x5e5850, 0x746d63, 0x8a8377, 0xa39c8f, 0xb8b1a4, 0xcbc4b7, 0xdcd6ca};
    /** Vanilla copper (copper_block) and patina (weathered/oxidized copper) tones, dark to light. */
    static final int[] COPPER = {0x7a3c28, 0x904931, 0x9a5038, 0xa75a40, 0xb26247, 0xc26b4c, 0xc87456, 0xd67b5b, 0xe3826c};
    static final int[] PATINA = {0x3b6655, 0x3e816b, 0x4c9484, 0x53a178, 0x64a077, 0x66a977, 0x7ab799};
    static final int[] LEAF = {0x2f5216, 0x3f6b1f, 0x5a8a2e, 0x6fa83a};
    static final int SEAM = 0xff3e3833;

    private Atlant() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    /** A hash noise 0..1 for a cell. */
    static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535.0;
    }

    /** Smooth value noise over cells of the given size (pixels). */
    static double noise(int x, int y, int cell, int seed) {
        int cx = Math.floorDiv(x, cell), cy = Math.floorDiv(y, cell);
        double fx = (x - cx * cell) / (double) cell, fy = (y - cy * cell) / (double) cell;
        double a = hash(cx, cy, seed), b = hash(cx + 1, cy, seed), c = hash(cx, cy + 1, seed), d = hash(cx + 1, cy + 1, seed);
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy;
    }

    /** Metal of a palette over a rectangle (units), in 2-pixel blocks like the vanilla textures; base is the darkest tone used. */
    static void metal(Paint p, double x0, double y0, double x1, double y1, int[] ramp, double base, int seed) {
        for (int j = (int) (y0 * p.k); j < (int) Math.min(p.h, y1 * p.k); j++) {
            for (int i = (int) (x0 * p.k); i < (int) Math.min(p.w, x1 * p.k); i++) {
                double tone = hash(i / 2, j / 2, seed) * 0.6 + noise(i, j, 8, seed + 1) * 0.4;
                p.px(i, j, rgb(ramp[(int) Math.min(ramp.length - 1, Math.max(0, base + tone * 3.5))]));
            }
        }
    }

    /** Copper over a rectangle with a light upper edge, a dark lower one and rare flecks of patina (the cold inlay). */
    static void copperBand(Paint p, double x0, double y0, double x1, double y1, int seed) {
        metal(p, x0, y0, x1, y1, COPPER, 3, seed);
        p.rect(x0, y0, x1, y0 + 0.5, rgb(COPPER[8]));
        p.rect(x0, y1 - 0.5, x1, y1, rgb(COPPER[0]));
        for (int j = (int) (y0 * p.k); j < (int) (y1 * p.k); j++) {
            for (int i = (int) (x0 * p.k); i < (int) (x1 * p.k); i++) {
                if (hash(i / 2, j / 2, seed + 9) > 0.93) {
                    p.px(i, j, rgb(PATINA[2 + (int) (hash(i, j, seed + 10) * 4)]));
                }
            }
        }
    }

    /** A vine hanging from y0 to y1 at x (units): a wavy stem with leaves on both sides. */
    static void vine(Paint p, double x, double y0, double y1, int seed) {
        for (double y = y0; y < y1; y += 0.5) {
            double wx = x + Math.round(Math.sin(y * 0.9 + seed) * 1.0) * 0.5;
            p.rect(wx, y, wx + 0.5, y + 0.5, rgb(LEAF[1]));
            int k = (int) (y * 2);
            if (hash(k, seed, 11) > 0.55) {
                double side = hash(k, seed, 12) > 0.5 ? 0.5 : -1.0;
                p.rect(wx + side, y, wx + side + 1, y + 1, rgb(LEAF[hash(k, seed, 13) > 0.5 ? 2 : 3]));
                p.rect(wx + side + (side > 0 ? 0.5 : 0), y + 0.5, wx + side + (side > 0 ? 1 : 0.5), y + 1, rgb(LEAF[0]));
            }
        }
    }

    /**
     * The torso. The upper half of the statue samples v 10..16 of it (its chest), the lower half v 0..3 (the belly), the head's
     * sides v 0..10: grey iron, a copper chest plate in a brass rim with the mod's rune (it glows a little), a copper band
     * at the bottom of the chest, vines hanging from the shoulders.
     */
    static Paint body() {
        Paint p = new Paint(16, 16, K);
        metal(p, 0, 0, 16, 16, IRON, 2.5, 101);
        p.rect(0, 3, 16, 3.5, SEAM);
        p.rect(5, 10, 11, 15, rgb(Paint.BRASS[2]));
        p.rect(5, 10, 11, 10.5, rgb(Paint.BRASS[5]));
        copperBand(p, 5.5, 10.5, 10.5, 14.5, 102);
        p.glowRect(7.5, 11.25, 8.5, 13.75, 0.01, rgb(0x45d6d2), 255);
        p.glowRect(6.75, 12.25, 9.25, 12.75, 0.01, rgb(0x96f2ee), 255);
        copperBand(p, 0, 15, 16, 16, 103);
        vine(p, 2.5, 10, 16, 3);
        vine(p, 2.5, 0, 3, 3);
        vine(p, 13, 10, 14, 5);
        return p;
    }

    /** Arms (v 2..14) and legs (v 6..16): grey iron segments joined by copper bands, a vine on one side. */
    static Paint limb() {
        Paint p = new Paint(16, 16, K);
        metal(p, 0, 0, 16, 16, IRON, 2.5, 202);
        for (double y = 3; y < 16; y += 4.5) {
            copperBand(p, 0, y, 16, y + 1.5, 203 + (int) y);
            p.rect(0, y + 1.5, 16, y + 2, SEAM);
        }
        vine(p, 1.5, 2, 12, 9);
        vine(p, 13.5, 6, 16, 2);
        return p;
    }

    /** The hands (v 0..2), the waist (v 3..6) with brass rivets and the feet (v 14.5..16): copper over the iron. */
    static Paint belt() {
        Paint p = new Paint(16, 16, K);
        metal(p, 0, 0, 16, 16, IRON, 1.5, 303);
        copperBand(p, 0, 0, 16, 2.25, 304);
        for (double x = 0.5; x < 16; x += 2) {
            p.rect(x, 0.5, x + 0.5, 2, rgb(COPPER[0]));
        }
        copperBand(p, 0, 3, 16, 6, 305);
        for (double x = 1; x < 16; x += 3) {
            p.rect(x, 4, x + 1, 5, rgb(Paint.BRASS[3]));
            p.rect(x, 4, x + 0.5, 4.5, rgb(Paint.BRASS[6]));
        }
        copperBand(p, 0, 14.5, 16, 16, 306);
        return p;
    }

    /**
     * The head's face in the iron golem's layout, where its front samples the texture (u 5..11, v 3..10): grey iron, a copper
     * brow over two small deep-set eyes with the mod's cyan light, the long iron nose (its own element samples u 7.25..8.75,
     * v 6.5..9) with its shadow, the mouth line, and a vine down one side as on the vanilla golem.
     */
    static Paint head() {
        Paint p = new Paint(16, 16, K);
        metal(p, 0, 0, 16, 16, IRON, 3, 404);
        copperBand(p, 5, 4, 11, 5.5, 405);
        p.rect(5, 5.5, 11, 6, SEAM);
        p.rect(6, 6, 7.5, 7, rgb(0x231f1b));
        p.rect(8.5, 6, 10, 7, rgb(0x231f1b));
        p.glowRect(6.5, 6, 7.5, 6.5, 0.01, rgb(0x45d6d2), 255);
        p.glowRect(8.5, 6, 9.5, 6.5, 0.01, rgb(0x45d6d2), 255);
        p.rect(7.25, 6.5, 8.75, 9, rgb(IRON[5]));
        p.rect(7.25, 6.5, 7.75, 9, rgb(IRON[7]));
        p.rect(8.25, 6.5, 8.75, 9, rgb(IRON[3]));
        p.rect(6.5, 9, 9.5, 9.5, SEAM);
        p.rect(6.5, 9.5, 9.5, 10, rgb(IRON[2]));
        vine(p, 10.5, 3, 10, 4);
        return p;
    }

    static void textures() throws Exception {
        body().save(Forge2.tex("atlant_body"));
        limb().save(Forge2.tex("atlant_limb"));
        belt().save(Forge2.tex("atlant_belt"));
        head().save(Forge2.tex("atlant_head"));
    }
}
