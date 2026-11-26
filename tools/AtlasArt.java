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

    /** A brass frame of width bw (units): light bevel on top/left, shadow on bottom/right (preserved for untouched tiles). */
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

    /** Muted antique-brass frame for refined Atlas Casing. */
    static void antiqueFrame(Paint p, double x0, double y0, double x1, double y1, double bw) {
        p.rect(x0, y0, x1, y1, rgb(Paint.BRASS[3]));
        p.rect(x0, y0, x1, y0 + 0.5, rgb(Paint.BRASS[5]));
        p.rect(x0, y0, x0 + 0.5, y1, rgb(Paint.BRASS[4]));
        p.rect(x0, y1 - 0.5, x1, y1, rgb(Paint.BRASS[1]));
        p.rect(x1 - 0.5, y0, x1, y1, rgb(Paint.BRASS[2]));
        p.rect(x0 + bw - 0.5, y0 + bw - 0.5, x1 - bw + 0.5, y0 + bw, rgb(Paint.BRASS[1]));
        p.rect(x0 + bw - 0.5, y0 + bw - 0.5, x0 + bw, y1 - bw + 0.5, rgb(Paint.BRASS[1]));
        p.rect(x0 + bw - 0.5, y1 - bw, x1 - bw + 0.5, y1 - bw + 0.5, rgb(Paint.BRASS[5]));
        p.rect(x1 - bw, y0 + bw - 0.5, x1 - bw + 0.5, y1 - bw + 0.5, rgb(Paint.BRASS[4]));
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

    /** The side of a casing: a beveled riveted copper armor plate in an antique-brass frame, recessed inner panel & clean medallion. */
    static Paint casing() {
        Paint p = new Paint(16, 16, K);
        antiqueFrame(p, 0, 0, 16, 16, 1.5);
        copper(p, 1.5, 1.5, 14.5, 14.5, 11, 0);

        // Beveled recessed inner armor panel replacing the flat circle-cross sticker
        copper(p, 3.5, 3.5, 12.5, 12.5, 19, -0.6);
        p.rect(3.5, 3.5, 12.5, 4.0, rgb(COPPER[0]));
        p.rect(3.5, 3.5, 4.0, 12.5, rgb(COPPER[0]));
        p.rect(3.5, 12.0, 12.5, 12.5, rgb(COPPER[6]));
        p.rect(12.0, 3.5, 12.5, 12.5, rgb(COPPER[6]));

        int groove = rgb(Atlant.COPPER[0]);
        int inlay = rgb(Paint.CYAN[4]);
        int brassDark = rgb(Paint.BRASS[2]);
        int brassHi = rgb(Paint.BRASS[5]);

        Carve.filigree(p, 2.0, 2.0, 14.0, 14.0, 1.5, brassDark, brassHi);
        Carve.runeRow(p, 3.8, 12.2, 2.2, 0.65, 0x51A7, groove, inlay);
        Carve.runeRow(p, 3.8, 12.2, 13.0, 0.65, 0x39E2, groove, inlay);

        // Smooth beveled cast-bronze/antique-brass central medallion with glowing cyan rune jewel
        p.disc(8, 8, 2.4, rgb(COPPER[0]));
        p.ring(8, 8, 1.8, 2.4, rgb(Paint.BRASS[3]));
        p.disc(8, 8, 1.6, rgb(Paint.SLATE_IRON[1]));
        p.glowRect(7.4, 7.4, 8.6, 8.6, 0.01, rgb(0x28b8b6), 255);
        p.glowRect(7.7, 7.7, 8.3, 8.3, 0.01, rgb(0x96f2ee), 255);

        cold(p, 12, 2.0);
        rivets(p, 0.5, 1);
        return p;
    }

    /** Clean weathered riveted copper cornice & pilaster texture (no cut-in-half rune strips when UV-mapped onto thin sills/posts). */
    static Paint rimCopper() {
        Paint p = new Paint(16, 16, K);
        copper(p, 0, 0, 16, 16, 101, 0.1);
        // Clean rolled copper plate bevels and riveted corner studs
        p.frame(0, 0, 16, 16, 0.5, rgb(COPPER[1]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(COPPER[7]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(COPPER[1]));
        p.rect(0.5, 8.0, 15.5, 8.5, rgb(COPPER[6]));
        // Subtle oxidation flecks at top/bottom corners (no random scattered white dots)
        p.px(2, 2, rgb(Atlant.PATINA[1]));
        p.px(p.w - 3, 2, rgb(Atlant.PATINA[1]));
        for (double x : new double[]{2.0, 7.5, 13.0}) {
            p.rect(x, 2.0, x + 0.8, 2.8, rgb(COPPER[0]));
            p.rect(x, 2.0, x + 0.4, 2.4, rgb(COPPER[7]));
            p.rect(x, 13.0, x + 0.8, 13.8, rgb(COPPER[0]));
            p.rect(x, 13.0, x + 0.4, 13.4, rgb(COPPER[7]));
        }
        return p;
    }

    /** Readable deepslate-gray forged steel housing well with bolts. */
    static Paint rimIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.05, 111);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.disc(8, 8, 5.4, rgb(Paint.SLATE_IRON[1]));
        p.ring(8, 8, 5.4, 6.4, rgb(Paint.SLATE_IRON[5]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rivet(r[0] + 0.5, r[1] + 0.5, 0.4, Paint.SLATE_IRON);
        }
        return p;
    }

    /** Warm antique bronze-brass for the 3D parapet valve handwheels. */
    static Paint rimWheel() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 3.1, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.05, 117, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
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

    /** Carved deepslate-steel arcane pedestal plate for Atlas Core crucible (no pitch-black ink). */
    static Paint coreRune() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.5, 1);
        p.grain(0, 0, 16, 16, 0.05, 701);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.SLATE_IRON[0]));
        Carve.filigree(p, 1.5, 1.5, 14.5, 14.5, 2.2, rgb(Paint.BRASS[3]), rgb(Paint.BRASS[5]));
        Carve.runeRow(p, 3.0, 13.0, 2.2, 0.8, 709, rgb(Paint.SLATE_IRON[1]), rgb(Paint.CYAN[4]));
        Carve.runeRow(p, 3.0, 13.0, 12.6, 0.8, 713, rgb(Paint.SLATE_IRON[1]), rgb(Paint.CYAN[4]));
        p.disc(8, 8, 2.8, rgb(Paint.SLATE_IRON[0]));
        p.ring(8, 8, 2.8, 3.5, rgb(Paint.BRASS[3]));
        p.rune(6.8, 5.7, 4.6, 45, rgb(Paint.CYAN[5]), rgb(Paint.CYAN[3]));
        p.glowRect(7.2, 7.2, 8.8, 8.8, 0.3, rgb(Paint.CYAN[5]), 160);
        return p;
    }

    /** Crisp faceted frost-cyan beacon crystal for Atlas Core. */
    static Paint coreCrystal() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double d = Math.hypot((i + 0.5) / K - 8.0, (j + 0.5) / K - 8.0) / 11.3;
                p.px(i, j, rgb(Paint.CYAN[d < 0.25 ? 6 : d < 0.65 ? 5 : 4]));
            }
        }
        p.line(1, 1, 15, 15, 0.8, rgb(0xf0ffff));
        p.line(15, 1, 1, 15, 0.8, rgb(Paint.CYAN[5]));
        p.frame(0, 0, 16, 16, 0.75, rgb(Paint.CYAN[2]));
        return p;
    }

    /** Recessed deepslate-steel socket bore with glowing frost-cyan channel core for Cable Bushing ports. */
    static Paint bushingCore() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 1.4, 1);
        p.disc(8, 8, 5.4, rgb(Paint.SLATE_IRON[0]));
        p.ring(8, 8, 5.4, 6.2, rgb(Paint.SLATE_IRON[4]));
        p.glowRect(6.2, 6.2, 9.8, 9.8, 0.6, rgb(Paint.CYAN[3]), 180);
        p.rect(6.5, 6.5, 9.5, 9.5, rgb(Paint.CYAN[4]));
        p.rect(7.25, 7.25, 8.75, 8.75, rgb(0xe8ffff));
        return p;
    }

    /** Glowing faceted packed-ice core column texture for Atlas Conduit (vertically seamless). */
    static Paint conduitIce() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            double ang = (j * 2.0 * Math.PI) / p.h;
            int pj = (int) Math.round(16 + 10 * Math.sin(ang));
            int qj = (int) Math.round(16 + 10 * Math.cos(ang));
            for (int i = 0; i < p.w; i++) {
                double facet = Atlant.noise(i + pj, qj - i, 7, 29);
                p.px(i, j, rgb(ICE[(int) Math.min(ICE.length - 2, 1 + facet * 4)]));
                if (((i + j) & 7) == 0 && Atlant.hash(i / 2, j / 2, 29) > 0.65) {
                    p.px(i, j, rgb(ICE[6]));
                }
            }
        }
        p.rect(6.0, 0, 10.0, 16, rgb(Paint.CYAN[4]));
        p.rect(6.9, 0, 9.1, 16, rgb(0xdcf6ff));
        return p;
    }

    /** Deep Blue-Ice / Sapphire core column texture for formed Channel Expander inside the Atlas Array (vertically seamless). */
    static Paint conduitBlue() {
        int[] DEEP_BLUE = {0x0d2558, 0x153a82, 0x1f53ad, 0x2f70d8, 0x4e92f0, 0x82bcff, 0xd2ecff};
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            double ang = (j * 2.0 * Math.PI) / p.h;
            int pj = (int) Math.round(20 + 10 * Math.sin(ang));
            int qj = (int) Math.round(20 + 10 * Math.cos(ang));
            for (int i = 0; i < p.w; i++) {
                double facet = Atlant.noise(i + pj, qj - i, 7, 73);
                p.px(i, j, rgb(DEEP_BLUE[(int) Math.min(DEEP_BLUE.length - 2, 1 + facet * 4.2)]));
                if (((i * 3 + j) & 7) == 0 && Atlant.hash(i / 2, j / 2, 73) > 0.62) {
                    p.px(i, j, rgb(DEEP_BLUE[5]));
                }
            }
        }
        p.rect(5.75, 0, 10.25, 16, rgb(0x2d74eb));
        p.rect(6.75, 0, 9.25, 16, rgb(0x70b8ff));
        p.rect(7.45, 0, 8.55, 16, rgb(0xcce8ff));
        return p;
    }

    /** Animated shimmering royal-violet crystal column texture for formed Quantum Channel Expander (vertically seamless). */
    static Paint conduitQuantum() {
        int[] QVIOLET = {0x1a0d3d, 0x2e1966, 0x492a9c, 0x6a44cf, 0x8f66f0, 0xb998ff, 0xf2e6ff};
        Paint p = new Paint(16, 16, K);
        p.animFrames = 24;
        p.frameTime = 2;
        p.interpolate = true;
        p.animate(0, 0, 16, 16, (c, t) -> {
            double phase = t * 2.0 * Math.PI;
            for (int j = 0; j < c.h; j++) {
                double vy = (double) j / c.h;
                double ang = vy * 2.0 * Math.PI;
                int pj = (int) Math.round(24 + 10 * Math.sin(ang));
                int qj = (int) Math.round(24 + 10 * Math.cos(ang));
                // Smooth upward energy wave + secondary harmonic
                double wave = 0.5 + 0.5 * Math.sin(ang * 2.0 + phase) * 0.7 + 0.3 * Math.cos(ang * 3.0 - phase * 2.0);
                for (int i = 0; i < c.w; i++) {
                    double u = (i + 0.5) / c.k;
                    double centerDist = Math.abs(u - 8.0);
                    double facet = Atlant.noise(i + pj, qj - i, 7, 137);
                    int base = QVIOLET[(int) Math.min(QVIOLET.length - 2, Math.max(0, 1 + facet * 3.8))];
                    if (centerDist < 2.6) {
                        double beam = (1.0 - centerDist / 2.6) * (0.78 + 0.22 * wave);
                        int coreCol = centerDist < 0.7 ? 0xf6ecff : centerDist < 1.5 ? 0xd2afff : 0x9660ff;
                        base = Paint.mix(rgb(base), rgb(coreCol), Math.min(1.0, 0.55 + beam * 0.45)) & 0xFFFFFF;
                    }
                    // Twinkling crystal starlight glints distributed across the violet facets
                    double spSeed = Atlant.hash(i, j, 991);
                    if (spSeed > 0.945) {
                        double twinkle = 0.5 + 0.5 * Math.sin(phase * 2.0 + spSeed * 43.0 + i * 0.7);
                        if (twinkle > 0.55) {
                            double k = (twinkle - 0.55) / 0.45;
                            base = Paint.mix(rgb(base), rgb(0xf8f0ff), k * 0.92) & 0xFFFFFF;
                        }
                    }
                    c.px(i, j, rgb(base));
                }
            }
        });
        return p;
    }

    static void textures() throws Exception {
        shelfFloor().save(Forge2.tex("mainframe_floor"));
        casing().save(Forge2.tex("atlas_casing"));
        casingTop().save(Forge2.tex("atlas_casing_top"));
        rimCopper().save(Forge2.tex("atlas_rim_copper"));
        rimIron().save(Forge2.tex("atlas_rim_iron"));
        rimWheel().save(Forge2.tex("atlas_rim_wheel"));
        coreRune().save(Forge2.tex("atlas_core"));
        coreCrystal().save(Forge2.tex("core_crystal"));
        bushingCore().save(Forge2.tex("bushing_core"));
        conduitIce().save(Forge2.tex("conduit_ice"));
        conduitBlue().save(Forge2.tex("conduit_blue"));
        conduitQuantum().save(Forge2.tex("conduit_quantum"));
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
