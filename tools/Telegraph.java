import java.util.Random;

/**
 * Icons of the Cryo-Source Telegraph and its cards (32x32, the Items style): the telegraph itself (a rusty copper base, a mast with
 * a violet crystal, a lattice and an iced dish, a little copper operator), the Aetherial Linkage (a rusty plate with two geared
 * crystals and arrows), the Aether Resonance Core (a blue crystal in a wire sphere) and the Void-Glass Prism (a violet vortex
 * with a star in a dark frame, icicles on the corners).
 */
final class Telegraph {
    static final int[] BRASS = Paint.BRASS, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET, FROST = Paint.FROST,
            COPPER = Paint.COPPER;

    private Telegraph() {
    }

    static void all() throws Exception {
        telegraph().save(Items.itemTex("cryo_source_telegraph"));
        linkage().save(Items.itemTex("aetherial_linkage"));
        resonanceCore().save(Items.itemTex("aether_resonance_core"));
        voidPrism().save(Items.itemTex("void_glass_prism"));
    }

    /** Rusty copper with patches of verdigris. */
    private static int rust(double u, double v, double t) {
        // large, rare patches of verdigris: only the peaks of a slow wave turn teal
        double n = 0.36 + 0.3 * Math.sin(u * 0.9 + v * 1.5 + 0.7) + 0.14 * Math.sin(u * 2.3 - v * 1.9);
        return Items.copperPatina(t, n);
    }

    /** Gold contacts under a card. */
    private static void pins(Paint p, double x0, double x1, double y) {
        for (double gx = x0; gx < x1; gx += 1.3) {
            p.rect(gx, y, gx + 0.8, y + 1.6, Items.at(Items.GOLD, 4.6));
            p.rect(gx, y + 1.2, gx + 0.8, y + 1.6, Items.at(Items.GOLD, 2.4));
        }
        p.rect(x0 - 0.4, y - 0.2, x1 + 0.2, y + 0.3, Items.at(IRON, 1.0));
    }

    /** A rusty card body with brass corner caps; corners = frost on the corners. */
    private static void cardBody(Paint p, double x0, double y0, double x1, double y1, boolean dark, boolean frost, long seed) {
        Items.shadow(p, (x0 + x1) / 2 + 0.3, y1 + 2.0, (x1 - x0) / 2 + 0.6, 0.9);
        p.fill(x0, y0, x1, y1, (u, v) -> dark ? Paint.ramp(IRON, 0.22 + 0.1 * Math.sin(u * 2.3 + v * 1.7))
                : rust(u, v, 0.42 - 0.12 * (v - y0) / (y1 - y0)));
        p.streaks(x0, y0, x1, y1, 0.06, seed, true);
        p.frame(x0, y0, x1, y1, 0.5, Items.at(BRASS, dark ? 3.6 : 2.6));
        p.line(x0 + 0.3, y0 + 0.3, x1 - 0.3, y0 + 0.3, 0.3, Items.at(BRASS, 5.4));
        for (double[] c : new double[][]{{x0, y0}, {x1 - 1.6, y0}, {x0, y1 - 1.6}, {x1 - 1.6, y1 - 1.6}}) {
            p.slab(c[0], c[1], c[0] + 1.6, c[1] + 1.6, BRASS, 3.8, 1);
            p.rivet(c[0] + 0.8, c[1] + 0.8, 0.35, BRASS);
        }
        if (frost) {
            p.frostLine(x0, x0 + 2.4, y0 - 0.3, 0.5, seed + 1);
            p.frostLine(x1 - 2.4, x1, y0 - 0.3, 0.5, seed + 2);
            p.icicle(x0 + 0.6, y1, 1.6, 0.7);
            p.icicle(x1 - 0.7, y1, 2.0, 0.8);
            p.icicle(x1 - 1.6, y1, 1.1, 0.5);
        }
    }

    // ------------------------------------------------------------------ the telegraph

    static Paint telegraph() {
        Paint p = Items.canvas();
        Items.shadow(p, 8.2, 14.6, 7.4, 1.1);
        // the base: a rusty copper slab with a brass belt, gauges and runes
        double bx0 = 1.2, bx1 = 14.8, by0 = 10.0, by1 = 14.4;
        p.fill(bx0, by0, bx1, by1, (u, v) -> rust(u, v, 0.48 - 0.16 * (v - by0) / (by1 - by0)));
        p.streaks(bx0, by0, bx1, by1, 0.05, 711, true);
        p.rect(bx0, by0 - 0.6, bx1, by0, Items.at(COPPER, 4.6));
        p.frostLine(bx0, bx1, by0 - 0.9, 0.5, 712);
        p.rect(bx0, by1 - 1.1, bx1, by1 - 0.6, Items.at(BRASS, 3.4));
        p.gauge(3.4, 12.0, 0.95, 220, Paint.rgb(0xd03020));
        p.gauge(5.8, 12.0, 0.95, 300, Paint.rgb(0xd03020));
        for (int i = 0; i < 3; i++) {
            p.rune(8.6 + i * 1.6, 10.9, 1.5, 11 + i * 3, Paint.rgb(0x58f0e8), 0);
        }
        p.icicle(2.4, by1 - 0.2, 1.0, 0.5);
        p.icicle(12.8, by1 - 0.2, 1.3, 0.6);
        // the mast on the right: crystal cage, lattice, dish
        double mx = 11.3;
        p.rect(mx - 2.0, 6.0, mx + 2.0, 6.6, Items.at(BRASS, 4.2));
        p.rect(mx - 1.9, 6.6, mx - 1.4, 9.8, Items.at(BRASS, 3.4));
        p.rect(mx + 1.4, 6.6, mx + 1.9, 9.8, Items.at(BRASS, 2.6));
        for (double y = 7.0; y < 9.6; y += 0.6) {
            p.rect(mx - 2.2, y, mx - 1.2, y + 0.3, Items.at(COPPER, 4.4));
        }
        p.polyFn(new double[]{mx, mx + 1.0, mx, mx - 1.0}, new double[]{6.6, 8.2, 9.8, 8.2},
                (u, v) -> Paint.ramp(VIOLET, 0.55 + 0.4 * (mx - u + 1) / 2 - 0.15 * (v - 6.6) / 3.2));
        p.glowRect(mx - 0.6, 7.2, mx + 0.6, 9.2, 1.2, Paint.rgb(0xb070ff), 110);
        p.line(mx - 1.6, 6.0, mx - 0.6, 3.0, 0.35, Items.at(BRASS, 4.0));
        p.line(mx + 1.6, 6.0, mx + 0.6, 3.0, 0.35, Items.at(BRASS, 3.0));
        p.line(mx - 1.2, 4.6, mx + 1.2, 4.6, 0.3, Items.at(COPPER, 4.0));
        p.disc(mx, 4.4, 0.45, Paint.rgb(0x9ad8ff));
        // the dish: an ellipse tilted toward the viewer, iced rim, a spike
        p.discFn(mx - 0.2, 2.2, 2.4, (u, v) -> {
            double d = Math.hypot((u - mx + 0.2) / 2.4, (v - 2.2) / 1.1);
            return d > 1 ? 0 : Paint.ramp(BRASS, 0.35 + 0.5 * (1 - d) - 0.1 * (u - mx) / 2.4);
        });
        p.frostLine(mx - 2.4, mx + 2.0, 1.1, 0.4, 713);
        p.icicle(mx - 1.6, 3.0, 0.9, 0.4);
        p.line(mx - 0.2, 2.2, mx + 0.8, 0.4, 0.35, Items.at(BRASS, 5.2));
        p.sparkle(mx + 0.9, 0.4, Paint.rgb(0xc8f4ff));
        // the operator: a little copper golem with glowing eyes at its key, the desk lamp
        p.slab(3.0, 6.4, 6.2, 9.2, COPPER, 3.6, 1);
        p.rect(3.4, 7.2, 5.8, 8.0, Items.at(Items.VERDIGRIS, 3.2));
        p.rect(3.8, 7.4, 4.4, 7.9, Paint.rgb(0x7ff6ee));
        p.rect(4.8, 7.4, 5.4, 7.9, Paint.rgb(0x7ff6ee));
        p.glowRect(3.8, 7.4, 5.4, 7.9, 0.5, Paint.rgb(0x7ff6ee), 90);
        p.slab(3.4, 9.2, 5.8, 10.0, COPPER, 3.0, 1);
        p.line(6.0, 9.2, 7.4, 9.7, 0.5, Items.at(COPPER, 3.6));
        p.rect(6.8, 9.6, 8.2, 10.0, Items.at(BRASS, 4.4));
        p.rect(7.6, 8.4, 8.6, 8.8, Items.at(Items.VERDIGRIS, 3.4));
        p.disc(8.1, 9.0, 0.35, Paint.rgb(0xffe08a));
        p.glowRect(7.8, 8.8, 8.4, 9.2, 0.6, Paint.rgb(0xffe08a), 90);
        return p;
    }

    // ------------------------------------------------------------------ the cards

    /** Aetherial Linkage: two geared rings with crystals joined by a light arc, runes and arrows. */
    static Paint linkage() {
        Paint p = Items.canvas();
        double x0 = 1.6, y0 = 2.0, x1 = 14.4, y1 = 12.4;
        cardBody(p, x0, y0, x1, y1, false, true, 801);
        pins(p, 4.4, 11.6, y1);
        double cy = 7.6;
        for (double cx : new double[]{5.8, 10.2}) {
            p.gearStatic(cx, cy, 2.4, 10, cx * 0.4, BRASS, 4.2);
            p.disc(cx, cy, 1.5, Items.at(IRON, 0.4));
            p.polyFn(new double[]{cx, cx + 1.0, cx, cx - 1.0}, new double[]{cy - 2.2, cy - 0.2, cy + 1.3, cy - 0.2},
                    (u, v) -> Paint.ramp(CYAN, 0.68 + 0.3 * (cx - u + 1.0) / 2.0));
            p.line(cx, cy - 2.2, cx, cy + 1.3, 0.2, Paint.rgb(0xeaffff));
            p.glowRect(cx - 0.6, cy - 1.4, cx + 0.6, cy + 0.8, 1.2, Paint.rgb(0x8af0ff), 140);
        }
        // the link: a thin light arc between the crystals
        for (double t = 0; t <= 1.0; t += 0.06) {
            double u = 5.8 + 4.4 * t, v = cy - 2.4 - 1.4 * Math.sin(Math.PI * t);
            p.disc(u, v, 0.26, Paint.rgb(0xc8fcff));
        }
        p.glowRect(6.0, cy - 3.8, 10.0, cy - 2.4, 0.7, Paint.rgb(0x8af0ff), 90);
        // arrows and corner runes
        p.line(2.8, 10.6, 4.6, 10.6, 0.3, Paint.rgb(0x58f0e8));
        p.line(4.6, 10.6, 4.0, 10.1, 0.3, Paint.rgb(0x58f0e8));
        p.line(13.2, 10.6, 11.4, 10.6, 0.3, Paint.rgb(0x58f0e8));
        p.line(11.4, 10.6, 12.0, 10.1, 0.3, Paint.rgb(0x58f0e8));
        p.rune(3.0, 3.6, 1.4, 21, Paint.rgb(0x58f0e8), 0);
        p.rune(11.8, 3.6, 1.4, 27, Paint.rgb(0x58f0e8), 0);
        return p;
    }

    /** Aether Resonance Core: a blue crystal in a glowing wire sphere, runes in the corners. */
    static Paint resonanceCore() {
        Paint p = Items.canvas();
        double x0 = 2.4, y0 = 1.2, x1 = 13.6, y1 = 12.6;
        cardBody(p, x0, y0, x1, y1, false, false, 802);
        pins(p, 4.8, 11.2, y1);
        double cx = 8.0, cy = 6.9;
        p.gearStatic(cx, cy, 4.4, 16, 0.2, BRASS, 2.8);
        p.disc(cx, cy, 3.6, Items.at(IRON, 0.5));
        // the wire sphere: a ring, meridians and parallels
        p.ring(cx, cy, 3.1, 3.45, Paint.rgb(0x6ae8f0));
        for (double a = -0.6; a <= 0.61; a += 0.6) {
            final double k = a;
            for (double t = -1; t <= 1.0; t += 0.04) {
                double y = cy + t * 3.2;
                double half = Math.sqrt(Math.max(0, 1 - t * t)) * 3.2;
                p.disc(cx + k * half, y, 0.12, Paint.argb(170, 0x6ae8f0));
            }
        }
        for (double t = -0.5; t <= 0.51; t += 0.5) {
            double y = cy + t * 3.2, half = Math.sqrt(1 - t * t) * 3.2;
            p.line(cx - half, y, cx + half, y, 0.18, Paint.argb(170, 0x6ae8f0));
        }
        p.glowRect(cx - 2.6, cy - 2.6, cx + 2.6, cy + 2.6, 1.4, Paint.rgb(0x6ae8f0), 60);
        // the crystal
        p.polyFn(new double[]{cx, cx + 1.4, cx + 0.9, cx, cx - 0.9, cx - 1.4}, new double[]{cy - 2.6, cy - 0.6, cy + 1.8, cy + 2.6, cy + 1.8, cy - 0.6},
                (u, v) -> Paint.ramp(CYAN, 0.55 + 0.4 * (cx - u + 1.4) / 2.8 - 0.1 * (v - cy) / 2.6));
        p.line(cx, cy - 2.6, cx, cy + 2.6, 0.2, Paint.rgb(0xe8ffff));
        p.glowRect(cx - 0.8, cy - 1.8, cx + 0.8, cy + 1.8, 1.2, Paint.rgb(0x9af8ff), 140);
        p.sparkle(cx + 2.2, cy - 2.4, Paint.rgb(0xe8ffff));
        p.rune(x0 + 0.4, y0 + 0.4, 1.3, 33, Paint.rgb(0x58f0e8), 0);
        p.rune(x1 - 1.7, y1 - 2.0, 1.3, 37, Paint.rgb(0x58f0e8), 0);
        return p;
    }

    /** Void-Glass Prism: a violet vortex with a star in a dark iron frame with gold, iced corners. */
    static Paint voidPrism() {
        Paint p = Items.canvas();
        double x0 = 2.4, y0 = 1.4, x1 = 13.6, y1 = 12.6;
        cardBody(p, x0, y0, x1, y1, true, true, 803);
        pins(p, 4.8, 11.2, y1);
        double cx = 8.0, cy = 7.0, r = 3.9;
        p.ring(cx, cy, r, r + 0.5, Items.at(Items.GOLD, 4.0));
        p.discFn(cx, cy, r, (u, v) -> {
            double dx = u - cx, dy = v - cy, d = Math.hypot(dx, dy) / r;
            double a = Math.atan2(dy, dx) + d * 4.2;
            double swirl = 0.5 + 0.5 * Math.sin(a * 2.0);
            int c = Paint.mix(Paint.rgb(0x1a0a3a), Paint.ramp(VIOLET, 0.45 + 0.45 * swirl), 0.35 + 0.65 * (1 - d) * swirl + 0.25 * (1 - d));
            return d > 0.92 ? Paint.mix(c, Paint.rgb(0x5a8cff), 0.4) : c;
        });
        p.glowRect(cx - 2.0, cy - 2.0, cx + 2.0, cy + 2.0, 1.6, Paint.rgb(0xb070ff), 70);
        // a six-pointed star at the heart
        double s = 1.7;
        p.line(cx - s, cy + s * 0.58, cx + s, cy + s * 0.58, 0.28, Paint.rgb(0xe0b8ff));
        p.line(cx - s, cy + s * 0.58, cx, cy - s * 1.15, 0.28, Paint.rgb(0xe0b8ff));
        p.line(cx + s, cy + s * 0.58, cx, cy - s * 1.15, 0.28, Paint.rgb(0xe0b8ff));
        p.line(cx - s, cy - s * 0.58, cx + s, cy - s * 0.58, 0.28, Paint.rgb(0xe0b8ff));
        p.line(cx - s, cy - s * 0.58, cx, cy + s * 1.15, 0.28, Paint.rgb(0xe0b8ff));
        p.line(cx + s, cy - s * 0.58, cx, cy + s * 1.15, 0.28, Paint.rgb(0xe0b8ff));
        p.glowRect(cx - 0.5, cy - 0.5, cx + 0.5, cy + 0.5, 1.0, Paint.rgb(0xe0b8ff), 120);
        Random rnd = new Random(804);
        for (int i = 0; i < 4; i++) {
            p.sparkle(cx - 3 + rnd.nextDouble() * 6, cy - 3 + rnd.nextDouble() * 6, Paint.rgb(0xd8c8ff));
        }
        p.gearStatic(x0 + 1.6, y1 - 1.8, 1.0, 8, 0.3, BRASS, 3.0);
        p.gearStatic(x1 - 1.6, y1 - 1.8, 1.0, 8, 0.1, BRASS, 3.0);
        return p;
    }
}
