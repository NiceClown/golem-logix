/**
 * The Atlas Array in warm metal with cold inlays: copper plates in brass frames (the vanilla copper tones), cyan runes,
 * hoarfrost in the corners and a few flecks of patina; ice pipes at the base, columns of ice with light flowing up between
 * the Atlants, icicles and a brass rail on top. Flat tones and dark seams like the vanilla copper blocks.
 */
final class AtlasArt {
    static final int K = Forge2.K;
    static final int[] COPPER = Atlant.COPPER;
    static final int[] ICE = {0x4f7fd0, 0x7ca5f4, 0x92b9fe, 0xa1c3ff, 0xbcd4ff, 0xd8e6ff, 0xf2f8ff};
    static final int RUNE = 0xff3a1a10;

    private AtlasArt() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    /** Copper in 2-pixel blocks over a rectangle (units); bright shifts the tones (0 = the middle ones). */
    static void copper(Paint p, double x0, double y0, double x1, double y1, int seed, double bright) {
        for (int j = (int) (y0 * p.k); j < (int) (y1 * p.k); j++) {
            for (int i = (int) (x0 * p.k); i < (int) (x1 * p.k); i++) {
                double tone = Atlant.hash(i / 2, j / 2, seed) * 0.55 + Atlant.noise(i, j, 8, seed + 1) * 0.45;
                p.px(i, j, rgb(COPPER[(int) Math.min(COPPER.length - 1, Math.max(0, 2.5 + tone * 4 + bright))]));
            }
        }
    }

    /** A brass frame of width bw (units): a light outer edge on top and left, a sunk inner edge (shadow top-left). */
    static void brassFrame(Paint p, double x0, double y0, double x1, double y1, double bw) {
        p.rect(x0, y0, x1, y1, rgb(Paint.BRASS[4]));
        p.rect(x0, y0, x1, y0 + 0.5, rgb(Paint.BRASS[6]));
        p.rect(x0, y0, x0 + 0.5, y1, rgb(Paint.BRASS[5]));
        p.rect(x0, y1 - 0.5, x1, y1, rgb(Paint.BRASS[2]));
        p.rect(x1 - 0.5, y0, x1, y1, rgb(Paint.BRASS[3]));
        p.rect(x0 + bw - 0.5, y0 + bw - 0.5, x1 - bw + 0.5, y0 + bw, rgb(Paint.BRASS[2]));
        p.rect(x0 + bw - 0.5, y0 + bw - 0.5, x0 + bw, y1 - bw + 0.5, rgb(Paint.BRASS[2]));
        p.rect(x0 + bw - 0.5, y1 - bw, x1 - bw + 0.5, y1 - bw + 0.5, rgb(Paint.BRASS[6]));
        p.rect(x1 - bw, y0 + bw - 0.5, x1 - bw + 0.5, y1 - bw + 0.5, rgb(Paint.BRASS[5]));
    }

    /** The cold inlays: hoarfrost creeping in from the corners and rare flecks of patina, never a fill. */
    static void cold(Paint p, int seed, double reach) {
        double w = p.w / p.k, h = p.h / p.k;
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / p.k, v = (j + 0.5) / p.k;
                double d = Math.min(Math.min(Math.hypot(u, v), Math.hypot(w - u, v)),
                        Math.min(Math.hypot(u, h - v), Math.hypot(w - u, h - v)));
                double f = reach * (0.6 + 0.8 * Atlant.noise(i, j, 6, seed)) - d;
                if (f > 0 && Atlant.hash(i, j, seed + 1) < 0.35 + f * 0.3) {
                    p.px(i, j, rgb(Paint.FROST[f > 1.2 ? 6 : f > 0.5 ? 5 : 3]));
                } else if (Atlant.hash(i / 2, j / 2, seed + 2) > 0.985) {
                    p.px(i, j, rgb(Atlant.PATINA[3 + (int) (Atlant.hash(i, j, seed + 3) * 3)]));
                }
            }
        }
    }

    /** The array's rune cut into the plate around (cx, cy): a ring, a cross and a cyan core with two sparks. */
    static void rune(Paint p, double cx, double cy, double r) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / p.k - cx, v = (j + 0.5) / p.k - cy;
                if (Math.abs(Math.hypot(u, v) - r * 0.8) < 0.3) {
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

    /** Brass rivets of the given size, inset from the four corners. */
    static void rivets(Paint p, double in, double size) {
        for (double[] c : new double[][]{{in, in}, {16 - in - size, in}, {in, 16 - in - size}, {16 - in - size, 16 - in - size}}) {
            p.rect(c[0], c[1], c[0] + size, c[1] + size, rgb(Paint.BRASS[2]));
            p.rect(c[0], c[1], c[0] + size / 2, c[1] + size / 2, rgb(Paint.BRASS[6]));
        }
    }

    /** The side of a casing: a copper rune plate sunk in a brass frame, frost in the corners. */
    static Paint casing() {
        Paint p = new Paint(16, 16, K);
        brassFrame(p, 0, 0, 16, 16, 1.5);
        copper(p, 1.5, 1.5, 14.5, 14.5, 11, 0);
        rune(p, 8, 8, 4.5);
        cold(p, 12, 2.4);
        rivets(p, 0.5, 1);
        return p;
    }

    /** The top of a casing: four copper tiles in brass seams around a small rune, the floor of the array's balcony. */
    static Paint casingTop() {
        Paint p = new Paint(16, 16, K);
        brassFrame(p, 0, 0, 16, 16, 1);
        copper(p, 1, 1, 15, 15, 13, 0.4);
        p.rect(7.75, 1, 8.25, 15, rgb(Paint.BRASS[2]));
        p.rect(1, 7.75, 15, 8.25, rgb(Paint.BRASS[2]));
        p.rect(5.5, 5.5, 10.5, 10.5, rgb(Paint.BRASS[4]));
        copper(p, 6, 6, 10, 10, 14, -0.6);
        rune(p, 8, 8, 1.8);
        cold(p, 15, 2.0);
        return p;
    }

    /** The conduit: a copper sleeve in brass bands with a window on a pipe of ice (vertical, as in the starter tower). */
    static Paint conduit() {
        Paint p = new Paint(16, 16, K);
        brassFrame(p, 0, 0, 16, 16, 1);
        copper(p, 1, 0, 15, 16, 21, -0.4);
        p.rect(4, 0, 12, 16, RUNE);
        ice(p, 4.5, 0, 11.5, 16, 3);
        p.glowRect(7, 0, 9, 16, 0.01, rgb(0xd8f6ff), 120);
        for (double y : new double[]{0, 7, 14}) {
            p.rect(3.5, y, 12.5, y + 2, rgb(Paint.BRASS[4]));
            p.rect(3.5, y, 12.5, y + 0.5, rgb(Paint.BRASS[6]));
            p.rect(3.5, y + 1.5, 12.5, y + 2, rgb(Paint.BRASS[2]));
        }
        cold(p, 22, 2.2);
        return p;
    }

    /** The cable bushing: a copper plate, a brass ring with rivets round a dark socket, a cyan core and frost on the rim. */
    static Paint bushing() {
        Paint p = new Paint(16, 16, K);
        brassFrame(p, 0, 0, 16, 16, 1);
        copper(p, 1, 1, 15, 15, 41, 0);
        p.disc(8, 8, 6.25, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 5.75, rgb(Paint.BRASS[4]));
        p.ring(8, 8, 5, 5.75, rgb(Paint.BRASS[5]));
        p.disc(8, 8, 4.5, rgb(COPPER[0]));
        p.ring(8, 8, 3.75, 4.5, rgb(Paint.BRASS[3]));
        p.disc(8, 8, 3.25, rgb(0x120c0a));
        p.glowRect(7, 7, 9, 9, 0.01, rgb(0x20a8a6), 255);
        p.glowRect(7.5, 7.5, 8.5, 8.5, 0.01, rgb(0x96f2ee), 255);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            double x = 8 + 5.1 * Math.cos(ang), y = 8 + 5.1 * Math.sin(ang);
            p.rect(x - 0.5, y - 0.5, x + 0.5, y + 0.5, rgb(Paint.BRASS[2]));
            p.rect(x - 0.5, y - 0.5, x, y, rgb(Paint.BRASS[6]));
        }
        cold(p, 42, 2.6);
        return p;
    }

    /**
     * The ice column between the Atlants (the renderer stands it on a side of the array, one segment a block): ice with a
     * glowing heart and pulses of light flowing up it; two pulses a segment, so the two segments join into one stream.
     */
    static Paint column() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 32;
        p.frameTime = 2;
        // deep ice, so the light running up it reads from afar: darker facets, a few pale streaks
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double facet = Atlant.noise(i + j, j - i, 9, 7);
                p.px(i, j, rgb(ICE[(int) Math.min(2, facet * 3)]));
            }
        }
        for (double y = 1; y < 16; y += 5) {
            p.rect(3, y, 6, y + 0.5, rgb(ICE[4]));
            p.rect(10, y + 2, 13, y + 2.5, rgb(0x3a62b0));
        }
        p.animate(0, 0, 16, 16, (q, t) -> {
            for (int j = 0; j < q.h; j++) {
                double v = (j + 0.5) / q.k;
                // the texture's top is the segment's top: a pulse moves to smaller v as t grows (two a segment)
                double phase = ((v / 8 + 2 * t) % 1 + 1) % 1;
                double pulse = Math.pow(Math.max(0, 1 - Math.abs(phase - 0.5) * 2.6), 1.6);
                for (int i = 0; i < q.w; i++) {
                    double u = (i + 0.5) / q.k, core = Math.max(0, 1 - Math.abs(u - 8) / (2.0 + 6.0 * pulse));
                    int a = (int) (255 * Math.min(1, core * (0.12 + 0.95 * pulse)));
                    if (a > 8) {
                        q.over(i, j, Paint.argb(a, rgb(pulse > 0.6 ? 0xf2ffff : pulse > 0.3 ? 0xa8f4ff : 0x6fd8f0)));
                    }
                }
            }
        });
        return p;
    }

    /**
     * The floor of a mainframe shelf (44 x 26 units of the 48 x 32 canvas, the front edge on top), drawn as one picture over the
     * whole shelf: a deck of bronze plates with brass rivets, snow drifted against the back and the side walls, hoarfrost,
     * and a dim rune under the crew's projection.
     */
    static Paint shelfFloor() {
        Paint p = new Paint(48, 32, K);
        double w = 44, h = 26;
        // big plates (11 x 8.75) with a light edge on the top and left, a dark seam on the bottom and right
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                double pu = u % 11, pv = v % 8.75;
                double tone = Atlant.hash(i / 2, j / 2, 41) * 0.45 + Atlant.noise(i, j, 12, 42) * 0.55;
                int c = Paint.BRONZE[(int) (2.4 + tone * 2)];
                if (pu > 10.5 || pv > 8.25) {
                    c = Paint.BRONZE[0];
                } else if (pu < 0.5 || pv < 0.5) {
                    c = Paint.BRONZE[4];
                }
                p.px(i, j, rgb(c));
            }
        }
        for (double v = 0; v < h; v += 8.75) {
            for (double u = 0; u < w; u += 11) {
                for (double[] r : new double[][]{{u + 1, v + 1}, {u + 9, v + 1}, {u + 1, v + 7.25}, {u + 9, v + 7.25}}) {
                    p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(Paint.BRASS[5]));
                }
            }
        }
        // the rune under the projection (the middle of the front half), dim cyan lines cut into the plates
        double cx = 22, cy = 7, r = 4.2;
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K - cx, v = (j + 0.5) / K - cy, d = Math.hypot(u, v);
                if (Math.abs(d - r) < 0.3 || Math.abs(d - r * 0.55) < 0.25
                        || (d < r && (Math.abs(u) < 0.25 || Math.abs(v) < 0.25))) {
                    p.px(i, j, rgb(Paint.CYAN[2]));
                }
            }
        }
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                if (u > w || v > h) {
                    continue;
                }
                // hoarfrost: scattered pale pixels where it is cold, never a fill
                double frost = Atlant.noise(i, j, 12, 51);
                if (frost > 0.6 && Atlant.hash(i, j, 52) > 1.6 - frost) {
                    p.px(i, j, rgb(Paint.FROST[Atlant.hash(i, j, 54) > 0.7 ? 5 : 3]));
                }
                // ragged snow drifts against the back wall, thin ones along the sides
                double back = h - v, side = Math.min(u, w - u);
                double drift = Math.max(3.2 * Atlant.noise(i, j, 7, 53) + 0.8 - back * 0.55,
                        1.6 * Atlant.noise(i, j, 5, 55) + 0.2 - side * 0.6);
                if (drift > 0.55) {
                    p.px(i, j, rgb(Paint.FROST[drift > 1.4 ? 6 : drift > 0.9 ? 5 : 4]));
                } else if (drift > 0.35) {
                    p.px(i, j, rgb(Paint.FROST[2]));
                }
            }
        }
        // a few lumps of snow kicked onto the deck by the crew
        for (int n = 0; n < 9; n++) {
            double x = 3 + Atlant.hash(n, 1, 61) * 38, y = 3 + Atlant.hash(n, 2, 61) * 17;
            if (Math.hypot(x - cx, y - cy) < r + 1) {
                continue;
            }
            p.rect(x, y, x + 1.5, y + 1, rgb(Paint.FROST[5]));
            p.rect(x + 0.5, y - 0.5, x + 1, y, rgb(Paint.FROST[6]));
            p.rect(x, y + 1, x + 1.5, y + 1.5, rgb(Paint.FROST[2]));
        }
        return p;
    }

    static void textures() throws Exception {
        shelfFloor().save(Forge2.tex("mainframe_floor"));
        casing().save(Forge2.tex("atlas_casing"));
        casingTop().save(Forge2.tex("atlas_casing_top"));
        conduit().save(Forge2.tex("atlas_conduit"));
        bushing().save(Forge2.tex("cable_bushing"));
        column().save(Forge2.tex("atlas_ice_column"));
        // the purple glyph that wanders over the faces of an assembled array (AtlasCoreRenderer)
        Paint glyph = new Paint(4, 4, 16);
        glyph.glowRect(1.2, 0.9, 2.8, 3.1, 1.0, Paint.at(Paint.VIOLET, 3.6), 110);
        glyph.rune(1.2, 0.7, 2.6, 11, Paint.at(Paint.VIOLET, 5.8), Paint.at(Paint.VIOLET, 3.4));
        glyph.save(ArtForge.RES.resolve("textures/entity/atlas_glyph.png").toFile());
    }
}
