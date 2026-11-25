import java.util.ArrayList;
import java.util.List;

/**
 * The Eternal Frost Resonator from the concept: a rusty copper body in a frosted brass frame, on the front a big brass dial with
 * a glowing teal screen (the needle runs in a circle), on the body a glass chamber on brass posts with a rune pad
 * at the bottom (the renderer draws the snow vortex in it, which also rises through the exhaust pipe on the roof), on the sides frosted white
 * pipes loop from the body into the chamber, icicles along the edges. The front faces the player when placed.
 */
final class Resonator {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, SLATE = Paint.SLATE_IRON,
            COPPER = Paint.COPPER, CYAN = Paint.CYAN, FROST = Paint.FROST;
    static final int[] COPPER_W = Atlant.COPPER, PATINA = Atlant.PATINA;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    /** A rusty copper plate in a brass frame: rivets, streaks of green patina, frost along the top edge. */
    static Paint body(long seed, boolean gauges) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.6, 1);
        p.slab(1.1, 1.1, 14.9, 14.9, COPPER, 2.5, 1);
        p.streaks(1.2, 1.2, 14.8, 14.8, 0.05, seed, true);
        p.grain(1.2, 1.2, 14.8, 14.8, 0.04, seed + 1);
        // Neat cast brass rivets without stray oil spots or streaks
        for (double x : new double[]{2.2, 13.8}) {
            for (double y : new double[]{2.2, 8.0, 13.8}) {
                p.rivet(x, y, 0.5, BRASS);
            }
        }
        if (gauges) {
            // two small gauges and a teal plate (as on the right side of the concept)
            p.gauge(8, 5.0, 2.2, 60, rgb(0xb02020));
            p.gauge(8, 10.6, 1.8, 140, rgb(0xb02020));
            p.glowRect(6.2, 13.0, 9.8, 14.0, 0.4, at(CYAN, 3.4), 60);
        }
        p.frostLine(0, 16, 0, 0.7, seed + 2);
        if (!gauges) {
            Carve.runeRow(p, 3.2, 12.8, 6.8, 0.9, (int) (seed + 3), at(COPPER, 0.4), at(CYAN, 4.6));
            Carve.sigil(p, 8, 10.2, 1.7, at(COPPER, 0.5), at(CYAN, 4.6));
        }
        Carve.filigree(p, 3.0, 3.0, 13.0, 13.0, 2.3, at(BRASS, 3.4), at(BRASS, 5.2));
        return p;
    }


    /** Top: a copper plate in a brass edging, frost, a rune notch. */
    static Paint top() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.4, 1);
        p.slab(1.0, 1.0, 15.0, 15.0, COPPER, 2.2, 1);
        p.grain(1.0, 1.0, 15.0, 15.0, 0.07, 9201);
        for (int i = 0; i < 6; i++) {
            p.rect(2.0, 2.5 + i * 2.0, 14.0, 2.9 + i * 2.0, at(COPPER, 1.2));
        }
        p.frostLine(0, 16, 0, 0.9, 9202);
        p.frostLine(0, 16, 15.1, 0.6, 9203);
        return p;
    }

    /**
     * Dial: a toothed brass ring, a glowing teal screen with marks and a rune ring, the needle runs in a circle.
     * Outside the circle it is transparent (the circle needs no square body).
     */
    static Paint dial() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 32;
        p.frameTime = 3;
        // teeth and ring
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            p.disc(8 + Math.cos(a) * 7.1, 8 + Math.sin(a) * 7.1, 0.75, at(BRASS, 2.4));
        }
        p.disc(8, 8, 7.0, at(BRASS, 2.0));
        p.ring(8, 8, 5.9, 6.9, at(BRASS, 3.6));
        p.ring(8, 8, 6.55, 6.75, at(BRASS, 4.8));
        p.disc(8, 8, 5.9, at(IRON, 0.4));
        // glowing screen
        p.glowRect(3.2, 3.2, 12.8, 12.8, 0.4, at(CYAN, 2.2), 40);
        p.discFn(8, 8, 5.5, (u, v) -> {
            double d = Math.hypot(u - 8, v - 8) / 5.5;
            return Paint.ramp(CYAN, 0.55 + 0.25 * (1 - d));
        });
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            p.line(8 + Math.cos(a) * 4.1, 8 + Math.sin(a) * 4.1, 8 + Math.cos(a) * 5.1, 8 + Math.sin(a) * 5.1, i % 3 == 0 ? 0.42 : 0.25,
                    at(CYAN, 6.4));
        }
        p.ring(8, 8, 2.7, 2.95, at(CYAN, 6.2));
        p.ring(8, 8, 1.2, 1.4, at(CYAN, 6.0));
        // the needle (alive): runs in a circle, dark on the light screen
        p.animate(2.5, 2.5, 13.5, 13.5, (q, t) -> {
            double a = t * Math.PI * 2 - Math.PI / 2;
            q.line(8, 8, 8 + Math.cos(a) * 4.6, 8 + Math.sin(a) * 4.6, 0.45, at(IRON, 0.3));
            q.line(8, 8, 8 - Math.cos(a) * 1.2, 8 - Math.sin(a) * 1.2, 0.45, at(IRON, 0.3));
        });
        p.disc(8, 8, 0.75, at(BRASS, 4.2));
        p.line(3.2, 5.2, 5.6, 3.0, 0.3, argb(110, 0xFFFFFF));
        return p;
    }

    /** Frosted chamber glass: pale teal, frost at the edges (thicker at the bottom), highlights. */
    static Paint glass() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double edge = Math.min(Math.min(u, 16 - u), Math.min(v, 16 - v));
            double frost = Math.max(0, 1 - edge / 2.2) + Math.max(0, (v - 12) / 4) * 0.6;
            int a = (int) Math.min(220, 38 + frost * 150);
            return argb(a, frost > 0.35 ? 0xE6F6FF : 0xA8E4F4);
        });
        java.util.Random r = new java.util.Random(9301);
        for (int i = 0; i < 30; i++) {
            double x = r.nextDouble() * 16, y = r.nextDouble() < 0.6 ? 13 + r.nextDouble() * 3 : r.nextDouble() * 2;
            p.sparkle(x, y, argb(220, 0xFFFFFF));
        }
        p.line(3.0, 12.0, 6.5, 2.5, 0.28, argb(90, 0xFFFFFF));
        p.line(4.4, 12.4, 7.8, 3.0, 0.16, argb(70, 0xFFFFFF));
        return p;
    }

    /** The rune pad at the bottom of the chamber: a dark circle, glowing rings and runes. */
    static Paint pad() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 0.6, 1);
        p.disc(8, 8, 7.4, at(BRASS, 2.0));
        p.disc(8, 8, 6.9, at(IRON, 0.3));
        p.ring(8, 8, 6.0, 6.5, at(CYAN, 5.4));
        p.ring(8, 8, 3.2, 3.55, at(CYAN, 5.0));
        p.disc(8, 8, 1.4, at(CYAN, 5.8));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.rune(8 + Math.cos(a) * 4.7 - 0.5, 8 + Math.sin(a) * 4.7 - 0.6, 1.2, i * 3 + 2, at(CYAN, 5.6), at(CYAN, 3.0));
        }
        return p;
    }

    /** Coolant pipe: aged industrial copper/bronze with brass couplings, an azure capillary and light frost. */
    static Paint pipe() {
        Paint p = new Paint(16, 16, K);
        // Base pipe body: industrial copper cylinder with true depth shading (Paint.at, not Paint.ramp which clamped to pale peach)
        p.fill(0, 0, 16, 16, (u, v) -> {
            double cu = Math.abs(u - 7.5) / 7.5;
            double n = Math.sin(u * 2.3 + v * 0.7) * 0.08 + Math.sin(v * 3.1) * 0.04;
            double luma = Math.max(0.0, Math.min(1.0, 1.0 - Math.pow(cu, 1.8) + n));
            int copper = at(COPPER, 1.4 + luma * 2.8);
            if (cu > 0.82) {
                copper = Paint.mix(copper, 0xFF3E1C0E, (cu - 0.82) / 0.18 * 0.65);
            }
            return copper;
        });

        // Lengthwise metallic grinding fibres of the pipe
        p.streaks(0, 0, 16, 16, 0.06, 9402, false);

        // A central sunk capillary with active cryogenic coolant
        for (double v = 0; v < 16; v += 0.25) {
            p.rect(7.0, v, 9.0, v + 0.25, rgb(0x0C161E)); // dark groove
            p.rect(7.4, v, 8.6, v + 0.25, at(CYAN, 4.4));  // azure coolant
            p.rect(7.7, v, 8.3, v + 0.25, at(CYAN, 5.8));  // bright light focus
        }

        // Strong brass flange couplings with bolts every 8 units
        for (double y : new double[]{2.0, 10.0}) {
            p.slab(0, y, 16, y + 2.4, BRASS, 3.2, 1);
            p.rect(0, y, 16, y + 0.4, at(BRASS, 5.0));
            p.rect(0, y + 2.0, 16, y + 2.4, at(BRASS, 1.4));
            for (double x : new double[]{2.0, 6.0, 10.0, 14.0}) {
                p.rivet(x, y + 1.2, 0.42, BRASS);
            }
            // Crystal rime and frost collecting at the clamp joints
            p.frostLine(0, 16, y - 0.4, 0.45, (long) (y * 100));
            p.frostLine(0, 16, y + 2.4, 0.35, (long) (y * 200));
        }

        // Rare ice micro crystals on the cold metal
        java.util.Random r = new java.util.Random(9401);
        for (int i = 0; i < 8; i++) {
            double rx = 1.0 + r.nextDouble() * 14.0, ry = r.nextDouble() * 16.0;
            p.sparkle(rx, ry, argb(210, 0xCEF0FF));
        }
        return p;
    }

    /** Vortex (a renderer texture): a soft teal ribbon with a bright middle and snowflakes, transparent at the edges. */
    static Paint vortex() {
        Paint p = new Paint(8, 32, K);
        p.fill(0, 0, 8, 32, (u, v) -> {
            double across = 1 - Math.abs(u - 4) / 4;
            double along = 0.65 + 0.35 * Math.sin(v * 0.9);
            // the renderer adds colours ("eyes"): transparent has to be black, so the colour is multiplied by the opacity
            double a = Math.pow(Math.max(0, across), 1.6) * 0.9 * along;
            int c = Paint.ramp(CYAN, 4.4 + across * 1.8);
            int rr = (int) (((c >> 16) & 0xFF) * a), gg = (int) (((c >> 8) & 0xFF) * a), bb = (int) ((c & 0xFF) * a);
            return argb(255, (rr << 16) | (gg << 8) | bb);
        });
        java.util.Random r = new java.util.Random(9501);
        for (int i = 0; i < 18; i++) {
            p.sparkle(1.5 + r.nextDouble() * 5, r.nextDouble() * 32, argb(255, 0xFFFFFF));
        }
        return p;
    }

    // ------------------------------------------------------------------ Wave 3 Resonator palette textures

    static Paint resIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, 9601);
        p.frame(0, 0, 16, 16, 0.5, rgb(SLATE[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(SLATE[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1.0, r[1] + 1.0, rgb(SLATE[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(SLATE[5]));
        }
        return p;
    }

    static Paint resBronze() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRONZE, 2.7, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.07, 9611, true);
        p.grain(0, 0, 16, 16, 0.05, 9612);
        p.frame(0, 0, 16, 16, 0.5, rgb(BRONZE[1]));
        p.frame(1.2, 1.2, 14.8, 14.8, 0.4, rgb(BRONZE[4]));
        return p;
    }

    static Paint resBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.8, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, 9621, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(BRASS[2]));
        return p;
    }

    /** Weathered riveted copper panel framed in deepslate-gray steel (preserves shared {@link #body} for Catalog). */
    static Paint resBody(long seed, boolean gauges) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.3, 1);
        for (int j = 2; j < p.h - 2; j++) {
            for (int i = 2; i < p.w - 2; i++) {
                double n = Atlant.hash(i / 2, j / 2, (int) seed) * 0.45 + Atlant.noise(i, j, 10, (int) seed + 1) * 0.55;
                p.px(i, j, rgb(COPPER_W[Math.max(0, Math.min(4, (int) Math.round(n * 4)))]));
            }
        }
        p.frame(1.0, 1.0, 15.0, 15.0, 0.45, rgb(COPPER_W[0]));
        p.rect(1.5, 1.5, 14.5, 2.0, rgb(COPPER_W[5]));
        for (double x : new double[]{2.3, 13.7}) {
            for (double y : new double[]{2.3, 8.0, 13.7}) {
                p.rivet(x, y, 0.45, BRASS);
            }
        }
        p.px(4, 5, rgb(PATINA[0]));
        p.px(p.w - 5, p.h - 6, rgb(PATINA[1]));
        if (gauges) {
            p.gauge(8, 5.0, 2.2, 60, rgb(0xb02020));
            p.gauge(8, 10.6, 1.8, 140, rgb(0xb02020));
            p.glowRect(6.2, 13.0, 9.8, 14.0, 0.4, at(CYAN, 3.4), 60);
        } else {
            Carve.runeRow(p, 3.4, 12.6, 6.8, 0.9, (int) (seed + 3), at(COPPER, 0.4), at(CYAN, 4.6));
            Carve.sigil(p, 8, 10.2, 1.7, at(COPPER, 0.5), at(CYAN, 4.6));
        }
        Carve.filigree(p, 3.0, 3.0, 13.0, 13.0, 2.2, at(BRONZE, 3.2), at(BRASS, 4.8));
        p.frostLine(0, 16, 0, 0.7, seed + 2);
        return p;
    }

    /** Frosted copper upper deck framed in forged deepslate steel. */
    static Paint resTop() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.3, 1);
        for (int j = 2; j < p.h - 2; j++) {
            for (int i = 2; i < p.w - 2; i++) {
                double n = Atlant.hash(i / 2, j / 2, 9631) * 0.45 + Atlant.noise(i, j, 10, 9632) * 0.55;
                p.px(i, j, rgb(COPPER_W[Math.max(0, Math.min(3, (int) Math.round(n * 3)))]));
            }
        }
        for (int i = 0; i < 6; i++) {
            p.rect(2.0, 2.5 + i * 2.0, 14.0, 2.9 + i * 2.0, rgb(COPPER_W[0]));
        }
        p.frostLine(0, 16, 0, 0.9, 9633);
        p.frostLine(0, 16, 15.1, 0.6, 9634);
        return p;
    }

    /** Balanced resonator dial: aged bronze gear bezel, dark slate inner rim, glowing teal screen with rotating dark needle. */
    static Paint resDial() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 32;
        p.frameTime = 3;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            p.disc(8 + Math.cos(a) * 7.1, 8 + Math.sin(a) * 7.1, 0.75, at(BRONZE, 2.2));
        }
        p.disc(8, 8, 7.0, at(BRONZE, 1.8));
        p.ring(8, 8, 6.2, 6.9, at(SLATE, 2.8));
        p.ring(8, 8, 5.9, 6.2, at(BRASS, 3.8));
        p.disc(8, 8, 5.9, at(SLATE, 0.4));
        p.glowRect(3.2, 3.2, 12.8, 12.8, 0.4, at(CYAN, 2.2), 40);
        p.discFn(8, 8, 5.5, (u, v) -> {
            double d = Math.hypot(u - 8, v - 8) / 5.5;
            return Paint.ramp(CYAN, 0.55 + 0.25 * (1 - d));
        });
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            p.line(8 + Math.cos(a) * 4.1, 8 + Math.sin(a) * 4.1, 8 + Math.cos(a) * 5.1, 8 + Math.sin(a) * 5.1, i % 3 == 0 ? 0.42 : 0.25,
                    at(CYAN, 6.4));
        }
        p.ring(8, 8, 2.7, 2.95, at(CYAN, 6.2));
        p.ring(8, 8, 1.2, 1.4, at(CYAN, 6.0));
        p.animate(2.5, 2.5, 13.5, 13.5, (q, t) -> {
            double a = t * Math.PI * 2 - Math.PI / 2;
            q.line(8, 8, 8 + Math.cos(a) * 4.6, 8 + Math.sin(a) * 4.6, 0.45, at(IRON, 0.3));
            q.line(8, 8, 8 - Math.cos(a) * 1.2, 8 - Math.sin(a) * 1.2, 0.45, at(IRON, 0.3));
        });
        p.disc(8, 8, 0.75, at(BRASS, 3.8));
        p.line(3.2, 5.2, 5.6, 3.0, 0.3, argb(110, 0xFFFFFF));
        return p;
    }

    /** Side coolant pipe for Resonator: weathered copper cylinder + forged SLATE_IRON flange couplings + brass bolts. */
    static Paint resPipe() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double cu = Math.abs(u - 7.5) / 7.5;
            double n = Math.sin(u * 2.3 + v * 0.7) * 0.08 + Math.sin(v * 3.1) * 0.04;
            double luma = Math.max(0.0, Math.min(1.0, 1.0 - Math.pow(cu, 1.8) + n));
            int copper = at(COPPER, 1.4 + luma * 2.8);
            if (cu > 0.82) {
                copper = Paint.mix(copper, 0xFF3E1C0E, (cu - 0.82) / 0.18 * 0.65);
            }
            return copper;
        });
        p.streaks(0, 0, 16, 16, 0.06, 9402, false);
        for (double v = 0; v < 16; v += 0.25) {
            p.rect(7.0, v, 9.0, v + 0.25, rgb(0x0C161E));
            p.rect(7.4, v, 8.6, v + 0.25, at(CYAN, 4.4));
            p.rect(7.7, v, 8.3, v + 0.25, at(CYAN, 5.8));
        }
        for (double y : new double[]{2.0, 10.0}) {
            p.slab(0, y, 16, y + 2.4, SLATE, 2.5, 1);
            p.rect(0, y, 16, y + 0.35, at(SLATE, 4.4));
            p.rect(0, y + 2.05, 16, y + 2.4, at(SLATE, 1.0));
            for (double x : new double[]{2.0, 6.0, 10.0, 14.0}) {
                p.rivet(x, y + 1.2, 0.4, BRASS);
            }
            p.frostLine(0, 16, y - 0.4, 0.45, (long) (y * 100));
            p.frostLine(0, 16, y + 2.4, 0.35, (long) (y * 200));
        }
        java.util.Random r = new java.util.Random(9401);
        for (int i = 0; i < 8; i++) {
            double rx = 1.0 + r.nextDouble() * 14.0, ry = r.nextDouble() * 16.0;
            p.sparkle(rx, ry, argb(210, 0xCEF0FF));
        }
        return p;
    }

    static void all() throws Exception {
        body(9101, false).save(Forge2.tex("resonator_body"));
        body(9111, true).save(Forge2.tex("resonator_gauges"));
        top().save(Forge2.tex("resonator_top"));
        dial().save(Forge2.tex("resonator_dial"));
        glass().save(Forge2.tex("resonator_glass"));
        pad().save(Forge2.tex("resonator_pad"));
        pipe().save(Forge2.tex("resonator_pipe"));
        vortex().save(Forge2.entityTex("resonator_vortex"));
        resIron().save(Forge2.tex("res_iron"));
        resBronze().save(Forge2.tex("res_bronze"));
        resBrass().save(Forge2.tex("res_brass"));
        resBody(9641, false).save(Forge2.tex("res_body"));
        resBody(9651, true).save(Forge2.tex("res_gauges"));
        resTop().save(Forge2.tex("res_top"));
        resDial().save(Forge2.tex("res_dial"));
        resPipe().save(Forge2.tex("res_pipe"));
        model();
    }

    /** A coolant pipe loop on one side: x0 = the outer wall of the block (0 or 16), dir = outwards (-1 or +1). */
    static void pipeLoop(List<Models.El> l, double x0, double dir) {
        java.util.function.BiFunction<Double, Double, double[]> span = (d0, d1) -> {
            double a = x0 + dir * d0, b = x0 + dir * d1;
            return new double[]{Math.min(a, b), Math.max(a, b)};
        };
        double[] s;
        s = span.apply(-0.5, 3.8);
        l.add(box(s[0], 3.2, 6.6, s[1], 5.8, 9.4).all("#pipe"));
        s = span.apply(1.15, 3.9);
        l.add(box(s[0], 3.1, 6.5, s[1], 13.7, 9.5).all("#pipe"));
        s = span.apply(-1.6, 3.8);
        l.add(box(s[0], 11.0, 6.6, s[1], 13.6, 9.4).all("#pipe"));
        // forged iron flanges at body & chamber + iron support clamps with small antique brass bolts
        s = span.apply(-0.1, 0.7);
        l.add(box(s[0], 2.8, 6.2, s[1], 6.2, 9.8).all("#iron"));
        s = span.apply(-1.5, -0.8);
        l.add(box(s[0], 10.6, 6.2, s[1], 14.0, 9.8).all("#iron"));
        s = span.apply(0.9, 4.1);
        l.add(box(s[0], 6.8, 6.2, s[1], 7.6, 9.8).all("#iron"));
        l.add(box(s[0], 9.6, 6.2, s[1], 10.4, 9.8).all("#iron"));
        s = span.apply(4.1, 4.35);
        l.add(box(s[0], 6.95, 7.65, s[1], 7.45, 8.35).all("#brass"));
        l.add(box(s[0], 9.75, 7.65, s[1], 10.25, 8.35).all("#brass"));
        s = span.apply(2.2, 2.7);
        l.add(box(s[0], 1.6, 7.6, s[1], 3.2, 8.1).all("#ice"));
    }

    static Models.El box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Models.e(x0, y0, z0, x1, y1, z1);
    }

    static void model() throws Exception {
        List<Models.El> l = new ArrayList<>();
        // plinth and body (front north = dial + recessed iron bezel boss; east = gauges)
        l.add(box(0, 0, 0, 16, 1, 16).all("#iron"));
        l.add(box(0.5, 1, 0.5, 15.5, 8, 15.5).face("north", "#body", 0, 0, 16, 16).face("south", "#body", 0, 0, 16, 16)
                .face("west", "#body", 0, 0, 16, 16).face("east", "#gauges", 0, 0, 16, 16));
        l.add(box(0, 8, 0, 16, 9, 16).all("#iron"));
        l.add(box(5.2, 2.2, -0.45, 10.8, 7.8, 0.5).only("#iron", "north", "east", "west", "up", "down"));
        l.add(box(4.6, 1.6, -0.6, 11.4, 8.4, -0.5).face("north", "#dial", 0, 0, 16, 16).glow(11));
        // rune pad and frosted glass chamber framed by stacked bronze-and-iron posts (zero coplanar overlaps)
        l.add(box(3, 9, 3, 13, 9.2, 13).face("up", "#pad", 0, 0, 16, 16).glow(14));
        l.add(box(2, 9, 2, 14, 15, 14).face("north", "#glass", 0, 0, 16, 16).face("south", "#glass", 0, 0, 16, 16)
                .face("west", "#glass", 0, 0, 16, 16).face("east", "#glass", 0, 0, 16, 16));
        for (double[] c : new double[][]{{1.3, 1.3}, {13.3, 1.3}, {1.3, 13.3}, {13.3, 13.3}}) {
            l.add(box(c[0] - 0.15, 9.0, c[1] - 0.15, c[0] + 1.55, 9.8, c[1] + 1.55).only("#bronze", "north", "south", "east", "west", "up"));
            l.add(box(c[0], 9.8, c[1], c[0] + 1.4, 14.2, c[1] + 1.4).only("#iron", "north", "south", "east", "west"));
            l.add(box(c[0] - 0.15, 14.2, c[1] - 0.15, c[0] + 1.55, 15.0, c[1] + 1.55).only("#bronze", "north", "south", "east", "west", "down"));
        }
        l.add(box(2.7, 11.6, 1.45, 13.3, 12.15, 2.05).only("#bronze", "north", "south", "up", "down"));
        // top frame around the exhaust pipe opening
        l.add(box(1, 15, 1, 15, 16, 5).all("#body").face("up", "#top", 1, 1, 15, 5));
        l.add(box(1, 15, 11, 15, 16, 15).all("#body").face("up", "#top", 1, 11, 15, 15));
        l.add(box(1, 15, 5, 5, 16, 11).all("#body").face("up", "#top", 1, 5, 5, 11));
        l.add(box(11, 15, 5, 15, 16, 11).all("#body").face("up", "#top", 11, 5, 15, 11));
        // exhaust chimney: riveted copper neck (16..18.6) -> forged iron collar (18.6..19.2) -> aged bronze lip (19.2..19.7)
        l.add(box(5, 16, 5, 11, 18.6, 5.8).all("#body"));
        l.add(box(5, 16, 10.2, 11, 18.6, 11).all("#body"));
        l.add(box(5, 16, 5.8, 5.8, 18.6, 10.2).all("#body"));
        l.add(box(10.2, 16, 5.8, 11, 18.6, 10.2).all("#body"));
        l.add(box(4.6, 18.6, 4.6, 11.4, 19.2, 5.8).all("#iron"));
        l.add(box(4.6, 18.6, 10.2, 11.4, 19.2, 11.4).all("#iron"));
        l.add(box(4.6, 18.6, 5.8, 5.8, 19.2, 10.2).all("#iron"));
        l.add(box(10.2, 18.6, 5.8, 11.4, 19.2, 10.2).all("#iron"));
        l.add(box(4.4, 19.2, 4.4, 11.6, 19.7, 5.8).all("#bronze"));
        l.add(box(4.4, 19.2, 10.2, 11.6, 19.7, 11.6).all("#bronze"));
        l.add(box(4.4, 19.2, 5.8, 5.8, 19.7, 10.2).all("#bronze"));
        l.add(box(10.2, 19.2, 5.8, 11.6, 19.7, 10.2).all("#bronze"));
        pipeLoop(l, 0, -1);
        pipeLoop(l, 16, 1);
        for (double[] ic : new double[][]{{1.2, 1.4}, {3.0, 1.0}, {12.6, 1.2}, {14.2, 1.6}}) {
            l.add(box(ic[0], 8 - ic[1], -0.3, ic[0] + 0.45, 8, 0.2).all("#ice"));
            l.add(box(ic[0], 8 - ic[1] * 0.8, 15.8, ic[0] + 0.45, 8, 16.3).all("#ice"));
        }
        for (double[] ic : new double[][]{{2.6, 1.2}, {5.4, 0.8}, {8.8, 1.4}, {11.6, 0.9}, {13.6, 1.1}}) {
            l.add(box(ic[0], 15 - ic[1], 0.7, ic[0] + 0.4, 15, 1.1).all("#ice"));
        }
        Models.model("eternal_frost_resonator", true, "res_body",
                new String[][]{{"body", "res_body"}, {"gauges", "res_gauges"}, {"top", "res_top"},
                        {"iron", "res_iron"}, {"bronze", "res_bronze"}, {"brass", "res_brass"},
                        {"glass", "resonator_glass"}, {"pad", "resonator_pad"},
                        {"pipe", "res_pipe"}, {"ice", "sat_ice"}, {"dial", "res_dial"}},
                l.toArray(new Models.El[0]));
        Models.facing4("eternal_frost_resonator", "eternal_frost_resonator");
        Models.item("eternal_frost_resonator", "eternal_frost_resonator");
        Forge2.addDisplay("eternal_frost_resonator", 225, 0.5);
    }
}
