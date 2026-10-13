import java.util.ArrayList;
import java.util.List;

/**
 * Three "cabinet" network devices: the Analog terminal (a CRT, green phosphor), the Crafting terminal (an amber screen, a crafting table)
 * and the Punch Card Encoder (nixie tubes, paper tape, a card magazine). The front is the screen that
 * {@code TerminalRenderer} draws over (a 7x7 unit window exactly in the middle); each has its own sides, back and roof, so that on approach
 * you can tell which block it is: the CRT has a picture tube sticking out at the back, the encoder a card magazine on top, the crafting terminal a work plate.
 * All textures are 64x64 (4 texels per unit), the models face NORTH, the block state rotates them.
 */
final class Terminals {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM;
    static final int[] GREEN = {0x03180a, 0x07301a, 0x0f5a2c, 0x1c9448, 0x3cd070, 0x8cf4a4, 0xe0ffe8};
    static final int[] AMBER = {0x261304, 0x4a2608, 0x80420c, 0xc26a14, 0xf09a2c, 0xffc860, 0xfff0c8};

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    // ------------------------------------------------------------------------------------------ details
    /** The base of any 16x16 panel: a dark iron frame and a bronze plate with grain and rivets in the corners. */
    static Paint plate(long seed, int[] ramp, double mid) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 1.3, 1);
        p.slab(1.0, 1.0, 15.0, 15.0, ramp, mid, 1);
        p.streaks(1.2, 1.2, 14.8, 14.8, 0.08, seed, true);
        p.grain(1.0, 1.0, 15.0, 15.0, 0.05, seed + 1);
        for (double[] c : new double[][]{{2.0, 2.0}, {14.0, 2.0}, {2.0, 14.0}, {14.0, 14.0}}) {
            p.rivet(c[0], c[1], 0.38, BRASS);
        }
        return p;
    }

    /** A vent grate: dark slots with a light edge under each. */
    static void vent(Paint p, double x0, double y0, double x1, double y1, int slots) {
        p.slab(x0 - 0.3, y0 - 0.3, x1 + 0.3, y1 + 0.3, IRON, 1.3, 1);
        double step = (y1 - y0) / slots;
        for (int i = 0; i < slots; i++) {
            double y = y0 + i * step;
            p.rect(x0, y + 0.1, x1, y + step * 0.62, rgb(0x050607));
            p.rect(x0, y + step * 0.62, x1, y + step * 0.82, at(IRON, 3.4));
        }
    }

    static void knob(Paint p, double cx, double cy, double r, double tick) {
        p.disc(cx, cy, r + 0.22, at(IRON, 0.2));
        p.sphere(cx, cy, r, BRASS, 0.3, 1.0);
        p.line(cx, cy, cx + Math.cos(tick) * r * 0.85, cy + Math.sin(tick) * r * 0.85, Math.max(0.25, r * 0.24), at(IRON, 0.2));
    }

    static void led(Paint p, double cx, double cy, int[] ramp, double bright) {
        p.disc(cx, cy, 0.5, at(IRON, 0.2));
        p.disc(cx, cy, 0.36, at(ramp, bright));
        p.glowRect(cx - 0.1, cy - 0.1, cx + 0.1, cy + 0.1, 0.5, at(ramp, bright), 70);
    }

    /** A toggle lever. */
    static void toggle(Paint p, double cx, double cy, boolean up) {
        p.slab(cx - 0.55, cy - 0.8, cx + 0.55, cy + 0.8, IRON, 1.0, 1);
        p.line(cx, cy, cx, cy + (up ? -1.0 : 1.0), 0.3, at(BRASS, 4.0));
        p.sphere(cx, cy + (up ? -1.0 : 1.0), 0.32, BRASS, 0.4, 1.0);
    }

    /** A brass plate with a rune. */
    static void tag(Paint p, double x, double y, double w, double h, int glyph, int[] glow) {
        p.slab(x, y, x + w, y + h, BRASS, 3.0, 1);
        p.rect(x + 0.35, y + 0.35, x + w - 0.35, y + h - 0.35, rgb(0x0a0c10));
        p.glowRect(x + 0.4, y + 0.4, x + w - 0.4, y + h - 0.4, 0.4, at(glow, 3.2), 55);
        p.rune(x + w / 2 - h * 0.18, y + h * 0.17, h * 0.66, glyph, at(glow, 5.4), 0);
    }

    static final String[] DIGITS = {"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001010010", "111101111101111", "111101111001111"};

    /** A nixie tube digit (a 3x5 font, a cell of cell units). */
    static void digit(Paint p, double x, double y, int d, double cell, int core, int halo) {
        String g = DIGITS[d];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 3; c++) {
                if (g.charAt(r * 3 + c) == '1') {
                    p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, core);
                }
            }
        }
        p.glowRect(x, y, x + 3 * cell, y + 5 * cell, cell * 1.2, halo, 60);
    }

    // ------------------------------------------------------------------------------- analog terminal (CRT)
    static Paint analogFront() {
        Paint p = plate(601, BRONZE, 2.7);
        p.slab(1.9, 1.9, 14.1, 14.1, IRON, 1.0, 1);
        p.grain(1.9, 1.9, 14.1, 14.1, 0.07, 603);
        // the screen ring and glass (the renderer draws over it: a 7x7 circle in the middle)
        p.disc(8, 8, 4.75, at(BRASS, 1.2));
        p.ring(8, 8, 4.0, 4.65, at(BRASS, 4.4));
        p.disc(8, 8, 3.95, at(IRON, 0.2));
        p.discFn(8, 8, 3.55, (u, v) -> {
            double dx = (u - 8) / 3.55, dy = (v - 8) / 3.55, d = Math.hypot(dx, dy);
            return Paint.ramp(GREEN, 0.04 + 0.1 * (1 - d) + 0.08 * Math.max(0, -dx * 0.5 - dy * 0.6));
        });
        p.line(5.9, 5.3, 8.5, 4.7, 0.3, argb(80, 0xFFFFFF));
        // ventilation on top, knobs and lamps at the bottom
        vent(p, 4.0, 2.6, 12.0, 3.9, 3);
        knob(p, 4.2, 12.6, 0.85, 0.6);
        knob(p, 6.7, 12.6, 0.85, 2.2);
        for (int i = 0; i < 4; i++) {
            led(p, 9.2 + i * 1.3, 12.6, GREEN, i < 3 ? 4.5 : 2.0);
        }
        p.frostLine(1.0, 15.0, 0, 0.5, 604);
        return p;
    }

    /** CRT side: a cooling grate and a carrying handle; the front (u = 16) is the edge at the screen. */
    static Paint analogSide() {
        Paint p = plate(611, BRONZE, 2.6);
        vent(p, 3.0, 3.2, 12.4, 12.6, 8);
        // a bracket handle at the front edge
        p.slab(13.3, 4.0, 14.3, 12.0, BRASS, 3.0, 1);
        p.rect(13.5, 5.0, 14.1, 11.0, at(BRASS, 1.0));
        tag(p, 1.6, 1.8, 3.6, 1.4, 12, GREEN);
        return p;
    }

    /** CRT back: the picture tube itself sticks out in the middle, around it sockets, cables and a plate. */
    static Paint analogBack() {
        Paint p = plate(621, IRON, 1.6);
        p.slab(1.0, 1.0, 15.0, 15.0, IRON, 1.5, 1);
        p.grain(1.0, 1.0, 15.0, 15.0, 0.07, 622);
        for (double[] c : new double[][]{{2.0, 2.0}, {14.0, 2.0}, {2.0, 14.0}, {14.0, 14.0}}) {
            p.rivet(c[0], c[1], 0.38, BRASS);
        }
        // cable sockets at the bottom and a red warning plate at the top
        for (double x : new double[]{3.6, 12.4}) {
            p.slab(x - 1.4, 12.2, x + 1.4, 14.2, BRASS, 3.0, 1);
            p.disc(x, 13.2, 0.65, rgb(0x050607));
        }
        tag(p, 5.5, 1.7, 5.0, 1.5, 4, GREEN);
        p.slab(1.6, 6.5, 2.8, 9.6, BRASS, 2.4, 1);
        p.slab(13.2, 6.5, 14.4, 9.6, BRASS, 2.4, 1);
        // a cable hanging from a socket
        p.line(3.6, 14.2, 3.6, 15.4, 0.5, at(COPPER, 3.0));
        p.line(12.4, 14.2, 12.4, 15.4, 0.5, at(COPPER, 3.0));
        return p;
    }

    /** CRT roof: a vent grate and two screws, frost along the front edge. */
    static Paint analogTop() {
        Paint p = plate(631, BRONZE, 2.5);
        vent(p, 4.5, 4.5, 11.5, 11.5, 6);
        for (double[] c : new double[][]{{3.0, 3.0}, {13.0, 3.0}, {3.0, 13.0}, {13.0, 13.0}}) {
            p.screw(c[0], c[1], 0.7, BRASS, c[0]);
        }
        p.frostLine(1.0, 15.0, 0, 0.5, 632);
        return p;
    }

    /** Picture tube (the bump at the back): brass bands and teal veins. */
    static Paint crt() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 2.2, 1);
        p.grain(0, 0, 16, 16, 0.07, 641);
        for (double y : new double[]{1.0, 7.4, 13.0}) {
            p.slab(0, y, 16, y + 2.0, BRASS, 3.0, 1);
            for (double x = 1.5; x < 16; x += 3.5) {
                p.rivet(x, y + 1.0, 0.3, BRASS);
            }
        }
        for (double x : new double[]{4.0, 8.0, 12.0}) {
            p.rect(x, 3.2, x + 0.3, 7.2, at(GREEN, 4.2));
            p.glowRect(x, 3.2, x + 0.3, 7.2, 0.5, at(GREEN, 3.2), 70);
        }
        return p;
    }

    // --------------------------------------------------------------------------------------- crafting terminal
    static Paint craftFront() {
        Paint p = plate(701, BRONZE, 2.8);
        p.slab(1.9, 1.9, 14.1, 14.1, IRON, 1.0, 1);
        p.grain(1.9, 1.9, 14.1, 14.1, 0.07, 703);
        // a square amber window (the renderer draws it)
        p.slab(3.9, 3.9, 12.1, 12.1, BRASS, 3.0, 2);
        p.rect(4.4, 4.4, 11.6, 11.6, at(AMBER, 0.55));
        p.fill(4.4, 4.4, 11.6, 11.6, (u, v) -> Paint.ramp(AMBER, 0.14 + 0.1 * (1 - Math.hypot(u - 8, v - 8) / 5)));
        p.line(5.2, 5.2, 7.6, 4.8, 0.3, argb(70, 0xFFFFFF));
        // a 3x3 crafting table sign above the screen, toggles and lamps at the bottom
        p.slab(5.9, 2.2, 10.1, 3.7, BRASS, 3.0, 1);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                p.rect(6.3 + c * 1.1, 2.5 + r * 0.4, 6.9 + c * 1.1, 2.8 + r * 0.4, at(AMBER, 3.6));
            }
        }
        toggle(p, 4.3, 13.0, true);
        toggle(p, 6.1, 13.0, false);
        toggle(p, 7.9, 13.0, true);
        for (int i = 0; i < 3; i++) {
            led(p, 10.3 + i * 1.4, 13.0, AMBER, i < 2 ? 4.8 : 2.0);
        }
        p.frostLine(1.0, 15.0, 0, 0.5, 704);
        return p;
    }

    /** Crafting side: a rack with a hammer and a saw on brass hooks; the front (u = 16) is the edge at the screen. */
    static Paint craftSide() {
        Paint p = plate(711, BRONZE, 2.6);
        p.slab(2.6, 2.6, 13.4, 13.4, IRON, 0.9, 1);
        p.grain(2.6, 2.6, 13.4, 13.4, 0.07, 712);
        // a workbench scene: a board on trestles, the saw goes back and forth and drops sawdust, the hammer above it taps
        p.slab(3.0, 10.6, 13.0, 11.8, BRONZE, 4.2, 1);
        p.streaks(3.1, 10.7, 12.9, 11.7, 0.1, 713, true);
        for (double x : new double[]{4.0, 11.4}) {
            p.slab(x, 11.8, x + 0.6, 13.2, BRONZE, 2.0, 1);
        }
        p.animFrames = 48;
        p.frameTime = 2;
        p.interpolate = false;
        p.animate(2.6, 2.6, 13.4, 13.4, (q, t) -> {
            double s = Math.sin(2 * Math.PI * 3 * t), dx = 1.3 * s;
            // a cut in the board and sawdust under it
            q.rect(8.5, 10.6, 8.75, 11.2, at(BRONZE, 1.0));
            for (int i = 0; i < 4; i++) {
                double fall = (t * 4 + i * 0.25) % 1.0;
                q.rect(8.3 + (i % 2) * 0.5, 11.9 + fall * 1.2, 8.5 + (i % 2) * 0.5, 12.1 + fall * 1.2, at(CREAM, 5.0));
            }
            saw(q, 3.6 + dx, 10.5, 6.2);
            // the hammer swings on its hook
            double a = Math.toRadians(-35 + 18 * Math.sin(2 * Math.PI * t + 1.0));
            double hx = 8.5, hy = 3.6, ex = hx + Math.cos(a + Math.PI / 2) * 3.4, ey = hy + Math.sin(a + Math.PI / 2) * 3.4;
            q.line(hx, hy, ex, ey, 0.6, at(BRONZE, 4.2));
            q.line(ex - Math.cos(a) * 1.1, ey - Math.sin(a) * 1.1, ex + Math.cos(a) * 1.1, ey + Math.sin(a) * 1.1, 0.9, at(IRON, 4.8));
            q.sphere(hx, hy, 0.45, BRASS, 0.3, 1.0);
        });
        tag(p, 1.6, 1.8, 3.6, 1.4, 6, AMBER);
        return p;
    }

    /**
     * A hacksaw: a trapezoid steel blade with teeth along the bottom edge and a shine along the back, a wooden "D" handle
     * on the right with brass screws. (x, y) is the bottom left end of the blade, len the blade length.
     */
    static void saw(Paint p, double x, double y, double len) {
        double h0 = 1.2, h1 = 2.4;
        p.polyFn(new double[]{x, x + len, x + len, x}, new double[]{y, y, y - h1, y - h0},
                (u, v) -> Paint.ramp(IRON, 0.62 + 0.25 * (y - v) / h1));
        p.line(x, y - h0 + 0.1, x + len, y - h1 + 0.1, 0.18, at(IRON, 6.0));
        for (double tx = x + 0.1; tx < x + len - 0.3; tx += 0.5) {
            p.poly(new double[]{tx, tx + 0.5, tx + 0.25}, new double[]{y, y, y + 0.4}, at(IRON, 3.2));
        }
        // handle: a wooden "D" with a slot and two brass screws
        double hx = x + len - 0.2;
        p.slab(hx, y - h1 - 0.6, hx + 2.2, y + 0.4, BRONZE, 4.0, 1);
        p.rect(hx + 0.6, y - h1 + 0.3, hx + 1.6, y - 0.4, at(IRON, 0.6));
        p.rivet(hx + 0.3, y - h1 + 0.1, 0.22, BRASS);
        p.rivet(hx + 0.3, y - 0.3, 0.22, BRASS);
    }

    /** Crafting back: a gear train under a brass cover and a "3x3" plate. */
    static Paint craftBack() {
        Paint p = plate(721, BRONZE, 2.4);
        p.slab(2.0, 2.0, 14.0, 14.0, IRON, 1.0, 1);
        p.grain(2.0, 2.0, 14.0, 14.0, 0.07, 722);
        p.gear(5.6, 9.0, 3.2, 12, 0.1, BRASS, 3.1);
        p.gear(10.6, 6.2, 2.2, 9, 0.35, COPPER, 3.0);
        p.gear(11.6, 11.4, 1.5, 8, 0.2, BRASS, 2.8);
        p.slab(1.6, 1.6, 6.4, 3.2, BRASS, 3.0, 1);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                p.rect(2.0 + c * 1.35, 1.95 + r * 0.38, 2.9 + c * 1.35, 2.3 + r * 0.38, at(AMBER, 3.6));
            }
        }
        // a tube with a valve
        p.slab(1.5, 13.4, 14.5, 14.3, BRASS, 2.4, 1);
        p.sphere(8.0, 13.85, 0.8, BRASS, 0.3, 1.0);
        return p;
    }

    /** Centre of a work plate cell (0..8). */
    static double[] cell(int i) {
        return new double[]{2.7 + (i % 3) * 3.5 + 1.55, 2.7 + (i / 3) * 3.5 + 1.55};
    }

    /** A block on the plate seen from above: a square of the block colour with a light edge and a shadow. */
    static void block(Paint p, double cx, double cy, double s, int color) {
        p.tint(cx - s / 2 + 0.2, cy - s / 2 + 0.25, cx + s / 2 + 0.2, cy + s / 2 + 0.25, argb(90, 0x000000));
        p.rect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, color);
        p.rect(cx - s / 2, cy - s / 2, cx + s / 2, cy - s / 2 + 0.25, Paint.shade(color, 1.35));
        p.rect(cx - s / 2, cy - s / 2, cx - s / 2 + 0.25, cy + s / 2, Paint.shade(color, 1.2));
        p.rect(cx - s / 2, cy + s / 2 - 0.25, cx + s / 2, cy + s / 2, Paint.shade(color, 0.6));
    }

    /** A flat copper golem seen from above: body, a head with an antenna, arms forward (along (dx, dy)), legs stepping. */
    static void flatGolem(Paint p, double cx, double cy, double dx, double dy, double step, int carried) {
        double len = Math.hypot(dx, dy);
        double fx = len < 1e-6 ? 0 : dx / len, fy = len < 1e-6 ? 1 : dy / len, sx = -fy, sy = fx;
        // legs (stepping in turn), body, arms to the load, head and antenna
        for (int k = -1; k <= 1; k += 2) {
            double off = k * 0.45 * Math.sin(step * Math.PI * 2);
            p.disc(cx + sx * k * 0.5 - fx * 0.6 + fx * off, cy + sy * k * 0.5 - fy * 0.6 + fy * off, 0.35, at(COPPER, 2.0));
        }
        p.disc(cx, cy, 0.95, at(COPPER, 1.0));
        p.disc(cx, cy, 0.85, at(COPPER, 3.4));
        for (int k = -1; k <= 1; k += 2) {
            p.line(cx + sx * k * 0.8, cy + sy * k * 0.8, cx + sx * k * 0.6 + fx * 1.3, cy + sy * k * 0.6 + fy * 1.3, 0.35, at(COPPER, 2.6));
        }
        if (carried != 0) {
            block(p, cx + fx * 1.7, cy + fy * 1.7, 1.3, carried);
        }
        p.disc(cx - fx * 0.15, cy - fy * 0.15, 0.6, at(COPPER, 4.6));
        p.disc(cx + fx * 0.15 + sx * 0.25, cy + fy * 0.15 + sy * 0.25, 0.13, at(CYAN, 5.4));
        p.disc(cx + fx * 0.15 - sx * 0.25, cy + fy * 0.15 - sy * 0.25, 0.13, at(CYAN, 5.4));
        p.disc(cx - fx * 0.35, cy - fy * 0.35, 0.18, at(COPPER, 6.0));
    }

    /**
     * Crafting roof: the work plate, a 3x3 grid of copper cells in a brass frame; a flat copper golem drags blocks
     * between the cells (two blocks swap places there and back, the cycle is closed), the other blocks lie still.
     */
    static Paint craftTop() {
        Paint p = plate(731, BRONZE, 2.6);
        p.slab(2.0, 2.0, 14.0, 14.0, BRASS, 2.6, 1);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                double x = 2.7 + c * 3.5, y = 2.7 + r * 3.5;
                p.slab(x, y, x + 3.1, y + 3.1, COPPER, 2.8, 1);
                p.streaks(x + 0.2, y + 0.2, x + 2.9, y + 2.9, 0.07, 732 + r * 3 + c, true);
            }
        }
        p.frostLine(1.0, 15.0, 0, 0.5, 733);
        int stone = rgb(0x7e7e7e), wood = rgb(0xa8834e), gold = rgb(0xf0c850), iron = rgb(0xd8d8d8);
        // still blocks and two "wandering" ones: A (stone) and B (gold); moves: A 0->4, B 8->0, A 4->8, B 0->4, A 8->0, B 4->8
        int[][] moves = {{0, 0, 4}, {1, 8, 0}, {0, 4, 8}, {1, 0, 4}, {0, 8, 0}, {1, 4, 8}};
        int[] colors = {stone, gold};
        p.animFrames = 60;
        p.frameTime = 2;
        p.interpolate = false;
        p.animate(2.0, 2.0, 14.0, 14.0, (q, t) -> {
            block(q, cell(2)[0], cell(2)[1], 2.0, wood);
            block(q, cell(6)[0], cell(6)[1], 2.0, iron);
            int[] pos = {0, 8};
            double m = t * moves.length;
            int cur = (int) Math.floor(m);
            double f = m - cur;
            for (int i = 0; i < cur; i++) {
                pos[moves[i][0]] = moves[i][2];
            }
            int who = moves[cur][0];
            // move phase: 0..0.4 walks empty to the block, 0.4..0.5 takes it, 0.5..0.9 carries it, 0.9..1 puts it down
            double[] prevDrop = cur == 0 ? cell(moves[moves.length - 1][2]) : cell(moves[cur - 1][2]);
            double[] from = cell(moves[cur][1]), to = cell(moves[cur][2]);
            boolean carrying = f >= 0.45 && f < 0.95;
            for (int b = 0; b < 2; b++) {
                if (!(b == who && carrying)) {
                    int at = b == who && f >= 0.95 ? moves[cur][2] : pos[b];
                    block(q, cell(at)[0], cell(at)[1], 2.0, colors[b]);
                }
            }
            double gx, gy, dx, dy;
            if (f < 0.4) {
                double k = smooth(f / 0.4);
                gx = prevDrop[0] + (from[0] - prevDrop[0]) * k;
                gy = prevDrop[1] + (from[1] - prevDrop[1]) * k;
                dx = from[0] - prevDrop[0];
                dy = from[1] - prevDrop[1];
            } else if (f < 0.5) {
                gx = from[0];
                gy = from[1];
                dx = to[0] - from[0];
                dy = to[1] - from[1];
            } else if (f < 0.9) {
                double k = smooth((f - 0.5) / 0.4);
                gx = from[0] + (to[0] - from[0]) * k;
                gy = from[1] + (to[1] - from[1]) * k;
                dx = to[0] - from[0];
                dy = to[1] - from[1];
            } else {
                gx = to[0];
                gy = to[1];
                dx = to[0] - from[0];
                dy = to[1] - from[1];
            }
            // the golem stands in front of the cell (the load above it), not on the block itself
            double len = Math.max(1e-6, Math.hypot(dx, dy));
            double back = f >= 0.4 ? 1.7 : 1.7 * smooth(f / 0.4);
            flatGolem(q, gx - dx / len * back, gy - dy / len * back, dx, dy, t * 18, carrying ? colors[who] : 0);
        });
        return p;
    }

    static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    // ---------------------------------------------------------------------------------------------- encoder
    /** The encoder typewriter beat: frames per cycle, frames per strike, strikes per line. */
    static final int ENC_FRAMES = 96, ENC_STRIKE = 4, ENC_LINE = 12;

    /** A nixie tube digit without its own halo (the living digits share a still halo: the glow layer is not animated). */
    static void digitPlain(Paint p, double x, double y, int d, double cell, int core) {
        String g = DIGITS[d];
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 3; c++) {
                if (g.charAt(r * 3 + c) == '1') {
                    p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, core);
                }
            }
        }
    }

    /**
     * A mechanical split-flap display:
     * The halves of the plates fold from top to bottom.
     * flipPhase == -1: the still digit dCur.
     * flipPhase == 0: the top flap of dCur tips forward/down, revealing the top of dNext.
     * flipPhase == 1: the falling flap passes the middle line, casting a shadow downwards.
     * flipPhase == 2: the flap snaps shut on the bottom half, fixing dNext.
     */
    static void splitFlapDigit(Paint p, double x, double y, int dCur, int dNext, int flipPhase, double cell, int core, int bg) {
        double w = 3 * cell + 0.4, h = 5 * cell + 0.4;
        double bx = x - 0.2, by = y - 0.2;
        // Background plate of the display
        p.rect(bx, by, bx + w, by + h, bg);
        p.rect(bx, by, bx + w, by + 0.12, at(IRON, 1.4));
        p.rect(bx, by + h - 0.12, bx + w, by + h, at(IRON, 0.4));
        p.rect(bx, by, bx + 0.12, by + h, at(IRON, 0.9));
        p.rect(bx + w - 0.12, by, bx + w, by + h, at(IRON, 0.4));

        double midY = y + 2.5 * cell; // middle line of the flap cut

        String gCur = DIGITS[dCur];
        String gNext = DIGITS[dNext];

        if (flipPhase == -1) {
            // The still digit dCur
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 3; c++) {
                    if (gCur.charAt(r * 3 + c) == '1') {
                        p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, core);
                    }
                }
            }
        } else if (flipPhase == 0) {
            // Phase 0: the top flap starts falling forward/down, dNext opens at the top
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 3; c++) {
                    if (gNext.charAt(r * 3 + c) == '1') {
                        p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, Paint.shade(core, 0.85));
                    }
                }
            }
            // The squashed falling flap of the old digit near the middle line
            for (int c = 0; c < 3; c++) {
                if (gCur.charAt(c) == '1' || gCur.charAt(3 + c) == '1') {
                    p.rect(x + c * cell, midY - 0.35, x + (c + 1) * cell, midY, Paint.shade(core, 0.6));
                }
            }
            // The old digit dCur is still visible at the bottom
            for (int r = 3; r < 5; r++) {
                for (int c = 0; c < 3; c++) {
                    if (gCur.charAt(r * 3 + c) == '1') {
                        p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, core);
                    }
                }
            }
        } else if (flipPhase == 1) {
            // Phase 1: the flap flies over the middle line, the top of dNext is fully visible, a shadow falls at the bottom
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 3; c++) {
                    if (gNext.charAt(r * 3 + c) == '1') {
                        p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, core);
                    }
                }
            }
            // Shadow and the squashed falling plate on the bottom half
            p.tint(bx, midY, bx + w, midY + 1.2 * cell, argb(160, 0x000000));
            for (int c = 0; c < 3; c++) {
                if (gNext.charAt(9 + c) == '1' || gNext.charAt(12 + c) == '1') {
                    p.rect(x + c * cell, midY + 0.08, x + (c + 1) * cell, midY + 0.45, Paint.shade(core, 0.7));
                }
            }
        } else if (flipPhase == 2) {
            // Phase 2: the flap snaps shut on the bottom part, fixing dNext
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 3; c++) {
                    if (gNext.charAt(r * 3 + c) == '1') {
                        p.rect(x + c * cell, y + r * cell, x + (c + 1) * cell, y + (r + 1) * cell, core);
                    }
                }
            }
            // Highlight of the latch strike
            p.rect(bx + 0.2, by + h - 0.2, bx + w - 0.2, by + h, Paint.shade(core, 1.2));
        }

        // A horizontal mechanical seam between the flaps
        p.line(bx, midY, bx + w, midY, 0.15, rgb(0x060402));
        p.line(bx, midY + 0.08, bx + w, midY + 0.08, 0.08, rgb(0x281c10));
        // Brass hinge rivets at the edges
        p.disc(bx + 0.1, midY, 0.14, at(BRASS, 4.0));
        p.disc(bx + w - 0.1, midY, 0.14, at(BRASS, 4.0));
    }

    /**
     * The encoder face is alive, like a typewriter: a split-flap counter display, a punch card in
     * the slot moves left step by step, a little hammer above the slot strikes and punches a hole, the keys are pressed in turn; at the end
     * of a line the carriage returns, a second row is punched, then the card is replaced with a blank one.
     */
    static Paint encoderFront() {
        Paint p = plate(801, BRONZE, 2.7);
        p.animFrames = ENC_FRAMES;
        p.frameTime = 4;
        p.interpolate = false;
        p.slab(1.9, 1.9, 14.1, 14.1, IRON, 1.0, 1);
        p.grain(1.9, 1.9, 14.1, 14.1, 0.07, 803);
        // a nixie-style split-flap display behind glass: the digits flip from top to bottom, every digit strictly on its own
        p.slab(2.9, 3.0, 13.1, 6.6, BRASS, 2.6, 1);
        p.rect(3.3, 3.4, 12.7, 6.2, rgb(0x0a0604));
        p.glowRect(3.6, 3.7, 12.4, 5.95, 0.45, at(AMBER, 3.0), 40);

        // Sequences of random digits for the 4 display digits
        int[] SEQ3 = {7, 2, 9, 4, 1, 8, 3, 5};
        int[] SEQ2 = {6, 1, 8, 3};
        int[] SEQ1 = {4, 9};
        int[] SEQ0 = {2, 7};

        p.animate(3.3, 3.4, 12.7, 6.2, (q, t) -> {
            int f = (int) Math.floor(t * ENC_FRAMES) % ENC_FRAMES;

            // Digit 3 (ones): flips every 12 frames (f = 0..2, 12..14, 24..26, ...)
            int step3 = f / 12;
            int fRel3 = f % 12;
            int dCur3 = SEQ3[step3 % SEQ3.length];
            int dNext3 = SEQ3[(step3 + 1) % SEQ3.length];
            int phase3 = fRel3 < 3 ? fRel3 : -1;
            int show3 = (phase3 == -1 && fRel3 >= 3) ? dNext3 : dCur3;

            // Digit 2 (tens): flips every 24 frames with an offset of 6 (f = 6..8, 30..32, 54..56, 78..80)
            int f2 = (f + 96 - 6) % 96;
            int step2 = f2 / 24;
            int fRel2 = f2 % 24;
            int dCur2 = SEQ2[step2 % SEQ2.length];
            int dNext2 = SEQ2[(step2 + 1) % SEQ2.length];
            int phase2 = fRel2 < 3 ? fRel2 : -1;
            int show2 = (phase2 == -1 && fRel2 >= 3) ? dNext2 : dCur2;

            // Digit 1 (hundreds): flips every 48 frames with an offset of 18 (f = 18..20, 66..68)
            int f1 = (f + 96 - 18) % 96;
            int step1 = f1 / 48;
            int fRel1 = f1 % 48;
            int dCur1 = SEQ1[step1 % SEQ1.length];
            int dNext1 = SEQ1[(step1 + 1) % SEQ1.length];
            int phase1 = fRel1 < 3 ? fRel1 : -1;
            int show1 = (phase1 == -1 && fRel1 >= 3) ? dNext1 : dCur1;

            // Digit 0 (thousands): flips at f = 42..44 and f = 90..92
            int phase0 = -1, dCur0 = SEQ0[0], dNext0 = SEQ0[1];
            if (f >= 42 && f < 45) {
                phase0 = f - 42;
                dCur0 = SEQ0[0];
                dNext0 = SEQ0[1];
            } else if (f >= 90 && f < 93) {
                phase0 = f - 90;
                dCur0 = SEQ0[1];
                dNext0 = SEQ0[0];
            }
            int show0 = (f >= 45 && f < 90) ? SEQ0[1] : SEQ0[0];

            double cell = 0.44;
            int core = at(AMBER, 5.6);
            int cardBg = rgb(0x160e08);

            splitFlapDigit(q, 3.8, 3.75, (phase0 >= 0 ? dCur0 : show0), (phase0 >= 0 ? dNext0 : show0), phase0, cell, core, cardBg);
            splitFlapDigit(q, 6.0, 3.75, (phase1 >= 0 ? dCur1 : show1), (phase1 >= 0 ? dNext1 : show1), phase1, cell, core, cardBg);
            splitFlapDigit(q, 8.2, 3.75, (phase2 >= 0 ? dCur2 : show2), (phase2 >= 0 ? dNext2 : show2), phase2, cell, core, cardBg);
            splitFlapDigit(q, 10.4, 3.75, (phase3 >= 0 ? dCur3 : show3), (phase3 >= 0 ? dNext3 : show3), phase3, cell, core, cardBg);
        });
        // the feed slot (the card in it is a living detail)
        p.slab(2.9, 7.4, 13.1, 9.8, BRASS, 2.6, 1);
        p.rect(3.4, 8.0, 12.6, 9.3, rgb(0x050607));
        int[] row0 = {1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 1}, row1 = {0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0};
        final double head = 8.6;
        p.animate(3.4, 6.2, 12.6, 9.4, (q, t) -> {
            int f = (int) Math.floor(t * ENC_FRAMES) % ENC_FRAMES;
            int line = f / (ENC_STRIKE * ENC_LINE);
            int step = (f % (ENC_STRIKE * ENC_LINE)) / ENC_STRIKE;
            boolean strike = f % ENC_STRIKE == 0;
            // carriage: the card moves a step left after every strike
            double dx = -step * 0.38;
            double x0 = 4.4 + dx, x1 = 11.6 + dx;
            double cx0 = Math.max(3.5, x0), cx1 = Math.min(12.5, x1);
            q.rect(cx0, 6.9, cx1, 8.7, rgb(0xE6DCB8));
            q.rect(cx0, 6.9, cx1, 7.1, rgb(0xFFFFFF));
            q.rect(cx0, 8.5, cx1, 8.7, rgb(0xB0A47C));
            // holes: column i is punched once it has been under the hammer (columns every 0.38 from the right edge of the line)
            for (int i = 0; i < ENC_LINE; i++) {
                double hx = head - (i - step) * 0.38;
                if (hx < cx0 || hx + 0.26 > cx1) {
                    continue;
                }
                if (line > 0 && row0[i] == 1) {
                    q.rect(hx, 7.35, hx + 0.26, 7.65, rgb(0x3A2A18));
                }
                boolean done = i < step || i == step && !strike;
                int[] row = line == 0 ? row0 : row1;
                if (done && row[i] == 1) {
                    q.rect(hx, line == 0 ? 7.35 : 7.85, hx + 0.26, line == 0 ? 7.65 : 8.15, rgb(0x3A2A18));
                }
            }
            // the little hammer above the slot: goes down to the card on a strike
            double hy = strike ? 6.75 : 6.3;
            q.rect(head - 0.25, hy - 0.2, head + 0.51, hy + 0.15, at(BRASS, 4.0));
            q.rect(head + 0.05, 6.2, head + 0.21, hy - 0.2, at(IRON, 1.8));
        });
        // keys (the pressed one lower and darker) and lamps
        p.animate(2.9, 10.8, 10.4, 12.7, (q, t) -> {
            int f = (int) Math.floor(t * ENC_FRAMES) % ENC_FRAMES;
            int step = (f % (ENC_STRIKE * ENC_LINE)) / ENC_STRIKE;
            boolean strike = f % ENC_STRIKE == 0;
            int pressed = strike ? (step * 3 + f / (ENC_STRIKE * ENC_LINE)) % 5 : -1;
            for (int i = 0; i < 5; i++) {
                boolean down = i == pressed;
                q.slab(3.0 + i * 1.5, 11.0 + (down ? 0.25 : 0), 4.2 + i * 1.5, 12.4 + (down ? 0.25 : 0), BRASS, down ? 2.0 : 3.0, 1);
            }
        });
        for (int i = 0; i < 3; i++) {
            led(p, 11.0 + i * 1.2, 11.7, i == 0 ? GREEN : i == 1 ? AMBER : VIOLET, 4.6);
        }
        // brass plate: every click of the hammer prints a new random rune (of 57 variants)
        p.animate(5.0, 12.8, 11.0, 14.8, (q, t) -> {
            int f = (int) Math.floor(t * ENC_FRAMES) % ENC_FRAMES;
            int step = f / ENC_STRIKE;
            boolean strike = f % ENC_STRIKE == 0;
            int glyph = Math.floorMod(step * 37 + (step * step * 13) + 7, Paint.RUNES.length);
            q.slab(5.6, 13.1, 10.4, 14.4, BRASS, strike ? 2.6 : 3.0, 1);
            int core = strike ? at(VIOLET, 6.2) : at(VIOLET, 5.4);
            int halo = strike ? at(VIOLET, 4.8) : at(VIOLET, 3.2);
            q.glowRect(6.0, 13.5, 10.0, 14.0, strike ? 0.6 : 0.4, halo, strike ? 90 : 55);
            q.rune(8.0 - 1.3 * 0.18, 13.1 + 1.3 * 0.17, 1.3 * 0.66, glyph, core, 0);
        });
        p.frostLine(1.0, 15.0, 0, 0.5, 804);
        return p;
    }

    /** Encoder side: a tape of perforated paper with holes crawling out of the slot; the front is the edge at the screen. */
    static Paint encoderSide() {
        Paint p = plate(811, BRONZE, 2.6);
        p.animFrames = ENC_FRAMES;
        p.frameTime = 4;
        p.interpolate = false;
        p.slab(2.4, 2.4, 13.6, 13.6, IRON, 0.9, 1);
        p.grain(2.4, 2.4, 13.6, 13.6, 0.07, 812);
        // the slot tray and the perforated tape: the tape is fed in jerks, a step on every strike of the machine
        p.slab(2.8, 6.6, 13.2, 9.8, BRASS, 2.6, 1);
        p.rect(3.2, 7.1, 12.8, 9.3, rgb(0x050607));
        p.animate(3.2, 5.4, 12.8, 8.0, (q, t) -> {
            int f = (int) Math.floor(t * ENC_FRAMES) % ENC_FRAMES;
            double shift = (f / ENC_STRIKE) * 0.5;
            q.rect(3.2, 5.4, 12.8, 8.0, rgb(0xE6DCB8));
            q.rect(3.2, 5.4, 12.8, 5.6, rgb(0xFFFFFF));
            q.rect(3.2, 7.8, 12.8, 8.0, rgb(0xB0A47C));
            for (int i = 0; i < 48; i++) {
                double x = 12.5 - ((i * 0.5 + shift) % 24.0);
                if (x < 3.3 || x > 12.5) {
                    continue;
                }
                if ((i * 5 + 1) % 7 < 4) {
                    q.rect(x, 5.9, x + 0.28, 6.18, rgb(0x3A2A18));
                }
                if ((i * 3 + 2) % 5 < 3) {
                    q.rect(x, 6.8, x + 0.28, 7.08, rgb(0x3A2A18));
                }
            }
        });
        // the paper goes down to a drum: the folds crawl down at the same step
        p.rect(4.4, 10.2, 11.6, 13.2, rgb(0xE6DCB8));
        p.animate(4.4, 10.2, 11.6, 13.2, (q, t) -> {
            int f = (int) Math.floor(t * ENC_FRAMES) % ENC_FRAMES;
            double shift = ((f / ENC_STRIKE) * 0.25) % 0.5;
            for (int i = 0; i < 6; i++) {
                q.line(4.4, 10.4 + i * 0.5 + shift, 11.6, 10.4 + i * 0.5 + shift, 0.12, rgb(0xB0A47C));
            }
        });
        for (double[] c : new double[][]{{4.4, 13.0}, {11.6, 13.0}}) {
            p.sphere(c[0], c[1], 0.7, BRASS, 0.3, 1.0);
        }
        tag(p, 1.6, 1.6, 3.6, 1.2, 8, VIOLET);
        return p;
    }

    /** Encoder back: a paper tape reel in a brass window, a rune plate. */
    static Paint encoderBack() {
        Paint p = plate(821, BRONZE, 2.5);
        p.slab(2.0, 2.6, 14.0, 12.4, BRASS, 2.8, 1);
        p.rect(2.6, 3.2, 13.4, 11.8, rgb(0x0a0604));
        // reel: a paper roll and brass cheeks
        p.disc(8, 7.5, 3.9, rgb(0xB0A47C));
        p.discFn(8, 7.5, 3.6, (u, v) -> {
            double d = Math.hypot(u - 8, v - 7.5);
            return Paint.ramp(CREAM, 0.45 + 0.12 * Math.sin(d * 9) + 0.2 * (1 - d / 3.6));
        });
        p.disc(8, 7.5, 1.0, at(BRASS, 2.0));
        p.sphere(8, 7.5, 0.7, BRASS, 0.3, 1.0);
        p.ring(8, 7.5, 3.6, 3.95, at(BRASS, 4.2));
        tag(p, 4.4, 13.0, 7.2, 1.5, 11, VIOLET);
        for (double x : new double[]{2.4, 13.6}) {
            p.sphere(x, 13.7, 0.55, BRASS, 0.3, 1.0);
        }
        return p;
    }

    /** Encoder roof: bronze with a slanted magazine window (under the tray). */
    static Paint encoderTop() {
        Paint p = plate(831, BRONZE, 2.5);
        p.slab(2.4, 2.4, 13.6, 13.6, IRON, 1.1, 1);
        p.grain(2.4, 2.4, 13.6, 13.6, 0.07, 832);
        p.frostLine(1.0, 15.0, 0, 0.5, 833);
        return p;
    }

    /** Magazine end: a stack of punch cards (cream cards, black hole edges). */
    static Paint cards() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> Paint.ramp(CREAM, 0.55 + 0.25 * Math.sin(v * 5.0) * 0.5 + 0.15));
        for (double y = 0.6; y < 16; y += 1.0) {
            p.rect(0, y, 16, y + 0.12, at(CREAM, 1.2));
        }
        for (int i = 0; i < 40; i++) {
            double x = 0.6 + (i * 7) % 14, y = 0.4 + ((i * 11) % 15);
            p.rect(x, y, x + 0.34, y + 0.4, rgb(0x3A2A18));
        }
        return p;
    }

    /** A brass magazine wall. */
    static Paint hopperWall() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 841, true);
        for (double x = 2; x < 16; x += 4) {
            p.rivet(x, 8, 0.45, BRASS);
        }
        return p;
    }

    static Paint bottom() {
        Paint p = plate(851, IRON, 1.8);
        vent(p, 4.0, 4.0, 12.0, 12.0, 6);
        return p;
    }

    static void textures() throws Exception {
        analogFront().save(Forge2.tex("analog_terminal_front"));
        analogSide().save(Forge2.tex("analog_terminal_side"));
        analogBack().save(Forge2.tex("analog_terminal_back"));
        analogTop().save(Forge2.tex("analog_terminal_top"));
        crt().save(Forge2.tex("terminal_crt"));
        craftFront().save(Forge2.tex("crafting_terminal_front"));
        craftSide().save(Forge2.tex("crafting_terminal_side"));
        craftBack().save(Forge2.tex("crafting_terminal_back"));
        craftTop().save(Forge2.tex("crafting_terminal_top"));
        encoderFront().save(Forge2.tex("pattern_encoder_front"));
        encoderSide().save(Forge2.tex("pattern_encoder_side"));
        encoderBack().save(Forge2.tex("pattern_encoder_back"));
        encoderTop().save(Forge2.tex("pattern_encoder_top"));
        cards().save(Forge2.tex("encoder_cards"));
        hopperWall().save(Forge2.tex("encoder_hopper"));
        bottom().save(Forge2.tex("terminal_bottom"));
        java.nio.file.Files.deleteIfExists(Forge2.tex("copper_casing").toPath());
    }

    // ---------------------------------------------------------------------------------------------- models
    static final double[] FULL = {0, 0, 16, 16};

    /** A box with the given faces: texture variable names (null = no face) and uv if wanted. */
    static Models.El box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Models.e(x0, y0, z0, x1, y1, z1);
    }

    static Models.El face(Models.El e, String dir, String tex, double... uv) {
        e.faces.put(dir, Models.El.face("#" + tex, uv.length == 4 ? uv : FULL));
        return e;
    }

    /**
     * Device body: depth d (along Z). The sides are drawn from the right part of the texture (the front is the edge u = 16), so the east
     * face has uv = [16 - d, 0, 16, 16], the west one mirrored.
     */
    static Models.El body(double d, double h, String name) {
        Models.El b = box(0, 0, 0, 16, h, d);
        double v1 = 16, v0 = 16 - h;
        face(b, "north", name + "_front", 0, v0, 16, v1);
        face(b, "east", name + "_side", 16 - d, v0, 16, v1);
        face(b, "west", name + "_side", 16, v0, 16 - d, v1);
        face(b, "up", name + "_top", 0, 0, 16, d);
        face(b, "down", "bottom", 0, 0, 16, d);
        face(b, "south", name + "_back", 0, v0, 16, v1);
        return b;
    }

    static String[][] tex(String name, String... more) {
        List<String[]> l = new ArrayList<>(List.of(new String[]{name + "_front", name + "_front"}, new String[]{name + "_side", name + "_side"},
                new String[]{name + "_back", name + "_back"}, new String[]{name + "_top", name + "_top"}, new String[]{"bottom", "terminal_bottom"}));
        for (int i = 0; i + 1 < more.length; i += 2) {
            l.add(new String[]{more[i], more[i + 1]});
        }
        return l.toArray(new String[0][]);
    }

    static void models() throws Exception {
        // --- Analog terminal: a body 12 deep, the picture tube sticks out at the back
        List<Models.El> a = new ArrayList<>();
        a.add(body(12, 16, "analog_terminal"));
        Models.El tube1 = box(3, 3, 12, 13, 13, 14);
        for (String d : new String[]{"east", "west", "up", "down", "south"}) {
            face(tube1, d, "crt");
        }
        a.add(tube1);
        Models.El tube2 = box(5, 5, 14, 11, 11, 16);
        for (String d : new String[]{"east", "west", "up", "down", "south"}) {
            face(tube2, d, "crt");
        }
        a.add(tube2);
        Models.model("analog_terminal", false, "analog_terminal_front", tex("analog_terminal", "crt", "terminal_crt"), a.toArray(new Models.El[0]));

        // --- Crafting terminal: a 16x15x16 body and a work plate on top
        List<Models.El> c = new ArrayList<>();
        Models.El cb = body(16, 15, "crafting_terminal");
        c.add(cb);
        Models.El plate = box(1, 15, 1, 15, 16, 15);
        face(plate, "up", "crafting_terminal_top", 1, 1, 15, 15);
        for (String d : new String[]{"north", "south", "east", "west"}) {
            face(plate, d, "crafting_terminal_top");
        }
        c.add(plate);
        Models.model("crafting_terminal", false, "crafting_terminal_front", tex("crafting_terminal"), c.toArray(new Models.El[0]));

        // --- Encoder: a 16x13x16 body and a punch card magazine on top
        List<Models.El> e = new ArrayList<>();
        e.add(body(16, 13, "pattern_encoder"));
        double[][] walls = {{2, 3, 14, 3.7}, {2, 12.3, 14, 13}};
        for (double[] w : walls) {
            Models.El wl = box(w[0], 13, w[1], w[2], 16, w[3]);
            for (String d : new String[]{"north", "south", "east", "west", "up"}) {
                face(wl, d, "hopper");
            }
            e.add(wl);
        }
        for (double[] w : new double[][]{{2, 3.7, 2.7, 12.3}, {13.3, 3.7, 14, 12.3}}) {
            Models.El wl = box(w[0], 13, w[1], w[2], 15.2, w[3]);
            for (String d : new String[]{"north", "south", "east", "west", "up"}) {
                face(wl, d, "hopper");
            }
            e.add(wl);
        }
        Models.El stack = box(2.7, 13, 3.7, 13.3, 15.6, 12.3);
        for (String d : new String[]{"north", "south", "east", "west", "up"}) {
            face(stack, d, "cards");
        }
        e.add(stack);
        Models.model("pattern_encoder", false, "pattern_encoder_front", tex("pattern_encoder", "hopper", "encoder_hopper", "cards", "encoder_cards"),
                e.toArray(new Models.El[0]));
        for (String n : new String[]{"analog_terminal", "crafting_terminal", "pattern_encoder"}) {
            Models.facing4(n, n);
            Models.item(n, n);
            Forge2.addDisplay(n, 135, 0.7);
        }
    }

    static void all() throws Exception {
        textures();
        models();
    }
}
