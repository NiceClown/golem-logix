/**
 * The Atlas Array in the game's own materials: rune plates of oxidized copper (the vanilla oxidized copper tones), ice
 * pipes at the base, columns of glowing ice between the Atlants, icicles and a brass rail on top. Flat tones and dark
 * seams like the vanilla copper blocks; the mod's accent is the cyan light of the runes and the ice.
 */
final class AtlasArt {
    static final int K = Forge2.K;
    /** Oxidized copper, dark to light (sampled from oxidized_copper), with the copper that shows through scratches. */
    static final int[] OXID = {0x2c4d41, 0x3b6655, 0x396e59, 0x3e816b, 0x4c9484, 0x53a178, 0x59b292, 0x6ec59f};
    static final int[] ICE = {0x4f7fd0, 0x7ca5f4, 0x92b9fe, 0xa1c3ff, 0xbcd4ff, 0xd8e6ff, 0xf2f8ff};
    static final int RUNE = 0xff1f3a31;
    static final int SCRATCH = 0xffc26b4c;

    private AtlasArt() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    /** Oxidized copper in 2-pixel blocks with a few copper scratches; bright 0 = the middle tones. */
    static void oxidized(Paint p, int seed, double bright) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double tone = Atlant.hash(i / 2, j / 2, seed) * 0.55 + Atlant.noise(i, j, 8, seed + 1) * 0.45;
                p.px(i, j, rgb(OXID[(int) Math.min(OXID.length - 1, Math.max(0, 2 + tone * 4 + bright))]));
                if (Atlant.hash(i, j, seed + 5) > 0.996) {
                    p.px(i, j, SCRATCH);
                }
            }
        }
    }

    /** A raised frame: a light edge on top and left, a dark one on the bottom and right (as on the vanilla cut copper). */
    static void frame(Paint p, double x0, double y0, double x1, double y1) {
        p.rect(x0, y0, x1, y0 + 0.5, rgb(OXID[7]));
        p.rect(x0, y0, x0 + 0.5, y1, rgb(OXID[7]));
        p.rect(x0, y1 - 0.5, x1, y1, rgb(OXID[0]));
        p.rect(x1 - 0.5, y0, x1, y1, rgb(OXID[0]));
    }

    /** The array's rune engraved in a square of side 2r around (cx, cy): a ring, a cross and four ticks, with a cyan core. */
    static void rune(Paint p, double cx, double cy, double r) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / p.k - cx, v = (j + 0.5) / p.k - cy;
                double d = Math.hypot(u, v);
                if (Math.abs(d - r * 0.8) < 0.3) {
                    p.px(i, j, RUNE);
                }
            }
        }
        p.rect(cx - 0.25, cy - r, cx + 0.25, cy + r, RUNE);
        p.rect(cx - r, cy - 0.25, cx + r, cy + 0.25, RUNE);
        p.glowRect(cx - 0.5, cy - 0.5, cx + 0.5, cy + 0.5, 0.01, rgb(0x45d6d2), 255);
        p.glowRect(cx - 0.25, cy - r + 0.5, cx + 0.25, cy - r + 1, 0.01, rgb(0x20a8a6), 255);
        p.glowRect(cx - 0.25, cy + r - 1, cx + 0.25, cy + r - 0.5, 0.01, rgb(0x20a8a6), 255);
    }

    /** Packed ice: big flat facets with light streaks (the vanilla packed ice palette). */
    static void ice(Paint p, double x0, double y0, double x1, double y1, int seed) {
        for (int j = (int) (y0 * p.k); j < (int) (y1 * p.k); j++) {
            for (int i = (int) (x0 * p.k); i < (int) (x1 * p.k); i++) {
                double facet = Atlant.noise(i + j, j - i, 9, seed);
                p.px(i, j, rgb(ICE[(int) Math.min(ICE.length - 2, 1 + facet * 4)]));
                if ((i + j + seed) % 13 == 0 && Atlant.hash(i / 3, j / 3, seed) > 0.6) {
                    p.px(i, j, rgb(ICE[6]));
                }
            }
        }
    }

    /** The side of a casing: a rune plate in a raised frame, rivets in the corners. */
    static Paint casing() {
        Paint p = new Paint(16, 16, K);
        oxidized(p, 11, 0);
        frame(p, 0, 0, 16, 16);
        frame(p, 2, 2, 14, 14);
        rune(p, 8, 8, 4);
        for (double[] c : new double[][]{{0.75, 0.75}, {14.25, 0.75}, {0.75, 14.25}, {14.25, 14.25}}) {
            p.rect(c[0], c[1], c[0] + 1, c[1] + 1, rgb(0xa07a28));
            p.rect(c[0], c[1], c[0] + 0.5, c[1] + 0.5, rgb(0xe0bc58));
        }
        return p;
    }

    /** The top of a casing: four tiles of cut copper around a small rune, like the floor of the array's balcony. */
    static Paint casingTop() {
        Paint p = new Paint(16, 16, K);
        oxidized(p, 12, 0.5);
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                frame(p, a * 8, b * 8, a * 8 + 8, b * 8 + 8);
            }
        }
        p.rect(5.5, 5.5, 10.5, 10.5, rgb(OXID[1]));
        frame(p, 5.5, 5.5, 10.5, 10.5);
        rune(p, 8, 8, 2);
        return p;
    }

    /** The conduit: a copper sleeve with a window on a pipe of glowing ice (vertical, as it stands in the starter tower). */
    static Paint conduit() {
        Paint p = new Paint(16, 16, K);
        oxidized(p, 21, -0.5);
        frame(p, 0, 0, 16, 16);
        p.rect(4, 0, 12, 16, rgb(OXID[0]));
        ice(p, 4.5, 0, 11.5, 16, 3);
        p.glowRect(7, 0, 9, 16, 0.01, rgb(0xd8f6ff), 120);
        for (double y : new double[]{0, 7, 14}) {
            p.rect(3.5, y, 12.5, y + 2, rgb(OXID[3]));
            p.rect(3.5, y, 12.5, y + 0.5, rgb(OXID[7]));
            p.rect(3.5, y + 1.5, 12.5, y + 2, rgb(OXID[0]));
        }
        return p;
    }

    /** The ice column between the Atlants (the renderer stands it on a side of the array): ice with a glowing heart. */
    static Paint column() {
        Paint p = new Paint(16, 16, K);
        ice(p, 0, 0, 16, 16, 7);
        p.glowRect(6.5, 0, 9.5, 16, 0.01, rgb(0xbff6ff), 150);
        p.glowRect(7.5, 0, 8.5, 16, 0.01, rgb(0xf2ffff), 230);
        for (double y = 1; y < 16; y += 5) {
            p.rect(3, y, 6, y + 0.5, rgb(ICE[6]));
            p.rect(10, y + 2, 13, y + 2.5, rgb(ICE[0]));
        }
        return p;
    }

    /**
     * The floor of the mainframe's shelves under the snowmen: even frosted stone, one calm tone with a fine grain and no
     * pattern, so the 16-pixel tiles of a long shelf join without a seam.
     */
    static Paint shelfFloor() {
        int[] tones = {0xa9c4d4, 0xb2ccdb, 0xb9d2e0, 0xc1d8e4};
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.hash(i / 2, j / 2, 31);
                p.px(i, j, rgb(tones[t < 0.2 ? 0 : t < 0.6 ? 1 : t < 0.93 ? 2 : 3]));
            }
        }
        return p;
    }

    static void textures() throws Exception {
        shelfFloor().save(Forge2.tex("mainframe_floor"));
        casing().save(Forge2.tex("atlas_casing"));
        casingTop().save(Forge2.tex("atlas_casing_top"));
        conduit().save(Forge2.tex("atlas_conduit"));
        column().save(Forge2.tex("atlas_ice_column"));
        // the purple glyph that wanders over the faces of an assembled array (AtlasCoreRenderer)
        Paint glyph = new Paint(4, 4, 16);
        glyph.glowRect(1.2, 0.9, 2.8, 3.1, 1.0, Paint.at(Paint.VIOLET, 3.6), 110);
        glyph.rune(1.2, 0.7, 2.6, 11, Paint.at(Paint.VIOLET, 5.8), Paint.at(Paint.VIOLET, 3.4));
        glyph.save(ArtForge.RES.resolve("textures/entity/atlas_glyph.png").toFile());
    }
}
