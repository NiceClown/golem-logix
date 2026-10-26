/**
 * The Atlant in the game's own style: an iron-golem-like caryatid of copper gone green, with vines (as the vanilla iron golem
 * has), holding the array up with raised arms. Flat colours from the vanilla copper and weathered copper palettes, few tones,
 * dark seams; the only accent of the mod is a glowing rune on the chest and the eyes. Textures are drawn in block space
 * (16 units at 2 pixels a unit), so every face of the model samples them at the same density.
 */
final class Atlant {
    static final int K = Forge2.K;
    /** Vanilla copper (copper_block) and patina (weathered/oxidized copper) tones, dark to light. */
    static final int[] COPPER = {0x7a3c28, 0x904931, 0x9a5038, 0xa75a40, 0xb26247, 0xc26b4c, 0xc87456, 0xd67b5b, 0xe3826c};
    static final int[] PATINA = {0x3b6655, 0x3e816b, 0x4c9484, 0x53a178, 0x64a077, 0x66a977, 0x7ab799};
    static final int[] LEAF = {0x2f5216, 0x3f6b1f, 0x5a8a2e, 0x6fa83a};
    static final int SEAM = 0xff6a3322;

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

    /** Copper with patches of patina, in 2-pixel blocks like the vanilla copper blocks; amount 0..1 of green. */
    static void patina(Paint p, int seed, double amount, double bright) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                int bi = i / 2, bj = j / 2;
                double tone = hash(bi, bj, seed) * 0.6 + noise(i, j, 8, seed + 1) * 0.4;
                double green = noise(i, j, 10, seed + 7) * 0.75 + hash(bi, bj, seed + 3) * 0.25;
                int c;
                if (green > 1 - amount) {
                    c = PATINA[(int) Math.min(PATINA.length - 1, Math.max(0, 1 + tone * 4 + bright))];
                } else {
                    c = COPPER[(int) Math.min(COPPER.length - 1, Math.max(0, 3 + tone * 4 + bright))];
                }
                p.px(i, j, rgb(c));
            }
        }
    }

    /** A horizontal seam with a light edge under it (segments of a limb, the joint of plates); y in units. */
    static void seam(Paint p, double y, double x0, double x1) {
        p.rect(x0, y, x1, y + 0.5, SEAM);
        p.rect(x0, y + 0.5, x1, y + 1, rgb(COPPER[7]));
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
     * The torso. The upper half of the statue samples v 10..16 of it (its chest), the lower half v 0..3 (the belly): the
     * chest plate with the mod's rune (it glows a little) and vines hanging from the shoulders over both halves.
     */
    static Paint body() {
        Paint p = new Paint(16, 16, K);
        patina(p, 101, 0.45, 0);
        p.rect(6, 10.5, 10, 14.5, SEAM);
        p.rect(6.5, 11, 9.5, 14, rgb(COPPER[2]));
        p.glowRect(7.5, 11.5, 8.5, 13.5, 0.01, rgb(0x45d6d2), 255);
        p.glowRect(7, 12.25, 9, 12.75, 0.01, rgb(0x96f2ee), 255);
        p.rect(0, 15.5, 16, 16, SEAM);
        vine(p, 2.5, 10, 16, 3);
        vine(p, 2.5, 0, 3, 3);
        vine(p, 12.5, 10, 14.5, 5);
        return p;
    }

    /** Arms (v 2..14) and legs (v 6..16): segments like the iron golem's and a vine on one side. */
    static Paint limb() {
        Paint p = new Paint(16, 16, K);
        patina(p, 202, 0.4, 0);
        for (double y = 3; y < 16; y += 4.5) {
            seam(p, y, 0, 16);
        }
        vine(p, 1.5, 2, 12, 9);
        vine(p, 13.5, 6, 16, 2);
        return p;
    }

    /** The hands (v 0..2), the waist (v 3..6) with brass rivets and the feet (v 14.5..16): darker weathered copper. */
    static Paint belt() {
        Paint p = new Paint(16, 16, K);
        patina(p, 303, 0.65, -1);
        for (double x = 0.5; x < 16; x += 2) {
            p.rect(x, 0, x + 0.5, 2, SEAM);
        }
        for (double x = 1; x < 16; x += 3) {
            p.rect(x, 4, x + 1, 5, rgb(0xc29b3c));
            p.rect(x, 4, x + 0.5, 4.5, rgb(0xe0bc58));
        }
        p.rect(0, 5.5, 16, 6, SEAM);
        p.rect(0, 14.5, 16, 15, rgb(COPPER[7]));
        return p;
    }

    /**
     * The head's face in the iron golem's layout, where its front samples the texture (u 4..12, v 0..10): smooth copper
     * above, the heavy brow over two small deep-set eyes with the mod's cyan light, the long nose (its own element samples
     * u 7..9, v 5..9) with its shadow, a darker chin, and a vine down one side as on the vanilla golem.
     */
    static Paint head() {
        Paint p = new Paint(16, 16, K);
        patina(p, 404, 0.3, 0);
        // the brow: a ledge with light on top and a shadow under it, across the whole face
        p.rect(4, 2.5, 12, 3, rgb(COPPER[8]));
        p.rect(4, 3, 12, 4, rgb(COPPER[5]));
        p.rect(4, 4, 12, 4.5, SEAM);
        // the eyes: dark sockets right under the brow, a cyan glint in each
        p.rect(5, 4.5, 7, 5.5, rgb(0x2a140c));
        p.rect(9, 4.5, 11, 5.5, rgb(0x2a140c));
        p.glowRect(5.5, 4.5, 6.5, 5.5, 0.01, rgb(0x45d6d2), 255);
        p.glowRect(9.5, 4.5, 10.5, 5.5, 0.01, rgb(0x45d6d2), 255);
        // the nose and the cheeks' shade on both sides of it
        p.rect(7, 5, 9, 9, rgb(COPPER[6]));
        p.rect(7, 5, 7.5, 9, rgb(COPPER[8]));
        p.rect(8.5, 5, 9, 9, rgb(COPPER[4]));
        p.rect(6.5, 9, 9.5, 9.5, SEAM);
        // the mouth line and the chin
        p.rect(6, 9.5, 10, 10, rgb(COPPER[2]));
        vine(p, 11, 0, 10, 4);
        return p;
    }

    static void textures() throws Exception {
        body().save(Forge2.tex("atlant_body"));
        limb().save(Forge2.tex("atlant_limb"));
        belt().save(Forge2.tex("atlant_belt"));
        head().save(Forge2.tex("atlant_head"));
    }
}
