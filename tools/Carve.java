/**
 * Carving for plain block sides, in the manner of Ars Nouveau: engraved runes of an old alphabet (single, in rows, in rings),
 * brass filigree corners and teal sigils. Sizes are in texture units, so the same carving fits a 16-unit side or a 6-unit bus.
 */
final class Carve {
    private Carve() {
    }

    /** A lighter tone of a colour (the polished lip of an engraved groove). */
    static int lighten(int c, double k) {
        int a = c >>> 24, r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        r = (int) (r + (255 - r) * k);
        g = (int) (g + (255 - g) * k);
        b = (int) (b + (255 - b) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** One rune in an s x 1.6s cell: three of six strokes chosen by the seed, cut in: a bright lip below-right, the groove on top. */
    static void rune(Paint p, double x, double y, double s, int seed, int c) {
        runeStrokes(p, x + Math.max(0.25, s / 3) * 0.7, y + Math.max(0.25, s / 3) * 0.7, s, seed, lighten(c, 0.55));
        runeStrokes(p, x, y, s, seed, c);
    }

    private static void runeStrokes(Paint p, double x, double y, double s, int seed, int c) {
        double h = s * 1.6, w = Math.max(0.25, s / 3);
        double[][] strokes = {{x, y, x, y + h}, {x + s, y, x + s, y + h}, {x, y, x + s, y}, {x, y + h / 2, x + s, y + h / 2},
                {x, y + h, x + s, y}, {x, y + h, x + s, y + h}};
        int drawn = 0;
        for (int k = 0; k < strokes.length && drawn < 3; k++) {
            if (Atlant.hash(seed, k, 517) > 0.45 || k == 5 - drawn) {
                double[] t = strokes[k];
                p.line(t[0], t[1], t[2], t[3], w, c);
                drawn++;
            }
        }
    }

    /** A row of runes between x0 and x1 at y (cells of size s), every few of them inlaid in the bright colour. */
    static void runeRow(Paint p, double x0, double x1, double y, double s, int seed, int c, int inlay) {
        int n = 0;
        for (double x = x0; x + s <= x1 + 1e-6; x += s * 1.5) {
            rune(p, x, y, s, seed * 31 + n, Atlant.hash(seed, n, 211) > 0.72 ? inlay : c);
            n++;
        }
    }

    /** Runes standing round a circle of radius r: count small glyph cells facing the centre (drawn upright, they read as a ring). */
    static void runeRing(Paint p, double cx, double cy, double r, double s, int count, int seed, int c, int inlay) {
        for (int k = 0; k < count; k++) {
            double a = Math.PI * 2 * k / count;
            double x = cx + Math.cos(a) * r - s / 2, y = cy + Math.sin(a) * r - s * 0.8;
            rune(p, x, y, s, seed * 17 + k, k % 4 == 0 ? inlay : c);
        }
    }

    /** Filigree in the corners of a rectangle: an L of wire and a small curl, a bright bead at the corner (size s ~ 3 on a 16 side). */
    static void filigree(Paint p, double x0, double y0, double x1, double y1, double s, int c, int hi) {
        double w = Math.max(0.25, s / 7);
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                double x = cx == 0 ? x0 : x1, y = cy == 0 ? y0 : y1, sx = cx == 0 ? 1 : -1, sy = cy == 0 ? 1 : -1;
                p.line(x, y, x + s * sx, y, w, c);
                p.line(x, y, x, y + s * sy, w, c);
                p.ring(x + s * 0.6 * sx, y + s * 0.6 * sy, s * 0.22, s * 0.22 + w, c);
                p.disc(x, y, w * 1.1, hi);
            }
        }
    }

    /** A sigil: a ring, a triangle inside it and an eye of light in the middle. */
    static void sigil(Paint p, double cx, double cy, double r, int c, int bright) {
        double w = Math.max(0.2, r / 7);
        p.ring(cx, cy, r - w, r, c);
        for (int k = 0; k < 3; k++) {
            double a0 = -Math.PI / 2 + k * 2 * Math.PI / 3, a1 = a0 + 2 * Math.PI / 3, rr = r * 0.82;
            p.line(cx + Math.cos(a0) * rr, cy + Math.sin(a0) * rr, cx + Math.cos(a1) * rr, cy + Math.sin(a1) * rr, w, c);
        }
        p.disc(cx, cy + r * 0.08, r * 0.24, bright);
    }
}
