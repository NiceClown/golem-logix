import java.util.*;

/**
 * The mod's pipes (from the reference): the basic one is a clear ice tube with light brass bands, the dense one deep blue
 * ice with heavy bronze cuffs. Straight runs are one smooth octagonal tube over the whole block (with a band in the middle),
 * at junctions a cube hub and sleeves with rings, at devices a brass cap sleeve (direction state none/pipe/device).
 * The models face NORTH, the other sides come from rotations in the block state.
 */
final class Pipes {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, BLUE = Paint.BLUE,
            FROST = Paint.FROST, COPPER = Paint.COPPER;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    // ----------------------------------------------------------------------------------------- textures
    /**
     * 16x16 tube glass: across (along u if across=true, otherwise along v) dark edges and highlights, along it uniform
     * (so there is no seam at block joints). seed affects the rare frost sparkles, they repeat along the length.
     */
    static Paint glass(boolean across, boolean dense, long seed) {
        Paint p = new Paint(16, 16, K);
        Random r = new Random(seed);
        double[][] sparks = new double[dense ? 7 : 5][];
        for (int i = 0; i < sparks.length; i++) {
            sparks[i] = new double[]{r.nextDouble(), r.nextDouble()};
        }
        p.fill(0, 0, 16, 16, (u, v) -> {
            double t = across ? u / 16 : v / 16, a = across ? v / 16 : u / 16;
            double e = Math.abs(2 * t - 1);
            if (dense) {
                // Deep sapphire ice with high-quality transparency
                double base = 0.38 + 0.30 * (1 - e * e) + 0.05 * Math.sin(a * Math.PI * 4);
                int c = Paint.ramp(BLUE, base);
                if (e > 0.85) {
                    c = Paint.mix(c, rgb(0x0a1c42), (e - 0.85) / 0.15);
                }
                int alpha = (int) (85 + 85 * Math.pow(e, 2.5));
                if (Math.abs(t - 0.22) < 0.035) {
                    c = Paint.mix(c, rgb(0xe2f0ff), 0.7);
                    alpha = Math.max(alpha, 175);
                }
                return argb(alpha, c & 0xFFFFFF);
            }
            // Clear smooth Create-style pneumatic glass with an azure tint
            int c = Paint.mix(rgb(0x9ee8f5), rgb(0x289cb8), Math.pow(e, 2.2));
            int alpha = (int) (48 + 115 * Math.pow(e, 3.2));
            // A sharp bright crystal glint of Create-style glass
            if (Math.abs(t - 0.22) < 0.03) {
                c = rgb(0xFFFFFF);
                alpha = 195;
            } else if (Math.abs(t - 0.76) < 0.02) {
                c = rgb(0xFFFFFF);
                alpha = 100;
            }
            return argb(alpha, c & 0xFFFFFF);
        });
        for (double[] s : sparks) {
            double x = across ? s[0] * 16 : s[1] * 16, y = across ? s[1] * 16 : s[0] * 16;
            p.over((int) (x * K), (int) (y * K), argb(dense ? 190 : 150, dense ? 0xcfe4ff : 0xFFFFFF));
        }
        return p;
    }

    /** Diagonal (narrow) faces: a light "highlight strip". */
    static Paint glassDiag(boolean dense) {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double t = u / 16, e = Math.abs(2 * t - 1);
            if (dense) {
                return argb(140, Paint.ramp(BLUE, 0.60 + 0.25 * (1 - e)) & 0xFFFFFF);
            }
            return argb((int) (65 + 75 * (1 - e)), Paint.mix(rgb(0xf0fbff), rgb(0xa0d4e8), e) & 0xFFFFFF);
        });
        return p;
    }

    /** A brass/bronze band in the Create style: a lengthwise grain, gear teeth, rivets and a cinematic animated gauge. */
    static Paint band(boolean dense) {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 24;
        p.frameTime = 1;
        p.interpolate = true;
        int[] ramp = dense ? BRONZE : BRASS;
        p.slab(0, 0, 16, 16, ramp, dense ? 3.4 : 2.7, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.08, dense ? 411 : 412, true);
        p.rect(0, 4.5, 16, 5.0, at(ramp, 1.2));
        p.rect(0, 11.0, 16, 11.5, at(ramp, 1.2));
        p.rect(0, 5.0, 16, 5.5, at(ramp, 5.2));
        p.rect(0, 10.5, 16, 11.0, at(ramp, 5.2));

        // Mechanical Create-style gear teeth around the rim
        for (double x = 1.0; x < 16; x += 3.0) {
            p.rect(x, 1.5, x + 1.2, 3.5, at(ramp, 4.4));
            p.rect(x, 12.5, x + 1.2, 14.5, at(ramp, 4.4));
        }

        // A built-in brass Create-style pressure gauge in the middle of the band
        double gx = 8.0, gy = 8.0;
        double rOuter = dense ? 1.65 : 1.25;
        p.disc(gx, gy, rOuter + 0.12, at(ramp, 1.2)); // dark rim bevel
        p.disc(gx, gy, rOuter, at(ramp, 4.8));        // polished solid brass rim
        p.disc(gx, gy, rOuter - 0.20, at(ramp, 2.0)); // inner bevel
        p.disc(gx, gy, rOuter - 0.32, rgb(0x0e1824)); // contrasting dark trim
        p.disc(gx, gy, rOuter - 0.42, rgb(0xF8F3E8)); // clean cream-white dial

        // A clear coloured scale (gauge mark sectors):
        // Green sector of normal pressure (left 180°..235°)
        // Amber-yellow sector of high pressure (top 235°..290°)
        // Bright red sector of critical overload (top right 290°..355°)
        double rTick0 = rOuter - 0.82;
        double rTick1 = rOuter - 0.48;
        for (int i = 0; i < 9; i++) {
            double a = Math.toRadians(180 + i * 21.5);
            double x0 = gx + Math.cos(a) * rTick0;
            double y0 = gy + Math.sin(a) * rTick0;
            double x1 = gx + Math.cos(a) * rTick1;
            double y1 = gy + Math.sin(a) * rTick1;
            int tickColor = (i < 3) ? rgb(0x288C35) : (i < 5) ? rgb(0xDC9218) : rgb(0xD01818);
            p.line(x0, y0, x1, y1, 0.16, tickColor);
        }
        // An expressive arc of the red overload zone
        double rArc = rOuter - 0.54;
        for (double a = Math.toRadians(290); a <= Math.toRadians(350); a += Math.toRadians(5)) {
            p.line(gx + Math.cos(a) * rArc, gy + Math.sin(a) * rArc,
                   gx + Math.cos(a + 0.10) * rArc, gy + Math.sin(a + 0.10) * rArc, 0.20, rgb(0xD41818));
        }

        // A cinematic animated gauge needle:
        // it jumps and beats furiously into the red overload zone against the stop, as in films,
        // keeping perfect continuous smoothness thanks to harmonic waves and frame interpolation.
        double rNeedle = rOuter - 0.48;
        p.animate(gx - rOuter - 0.2, gy - rOuter - 0.2, gx + rOuter + 0.2, gy + rOuter + 0.2, (fr, t) -> {
            double twoPi = 2.0 * Math.PI;
            double primary = Math.sin(twoPi * t);
            double surge = Math.sin(2.0 * twoPi * t + 0.7);
            double flutter = Math.sin(6.0 * twoPi * t) * 0.65 + Math.sin(10.0 * twoPi * t) * 0.35;
            // Base level 328° (deep in the red zone), it beats against the 354° stop
            double deg = 328.0 + 14.5 * primary + 7.0 * surge + 4.5 * flutter;
            if (deg > 354.0) {
                deg = 354.0 - (deg - 354.0) * 0.25; // a springy bounce off the stop
            }
            double angle = Math.toRadians(deg);
            double nx = gx + rNeedle * Math.cos(angle);
            double ny = gy + rNeedle * Math.sin(angle);
            fr.line(gx, gy, nx, ny, 0.20, rgb(0xB81410)); // bright red needle
            fr.disc(gx, gy, 0.30, at(ramp, 1.2)); // brass axle mount
            fr.disc(gx, gy, 0.16, rgb(0x181818)); // central pin
            fr.line(gx - rOuter * 0.45, gy - rOuter * 0.45, gx - rOuter * 0.15, gy - rOuter * 0.45, 0.15, argb(170, 0xFFFFFF)); // glass highlight
        });

        // Heavy mechanical rivets
        for (double x : new double[]{2.5, 13.5}) {
            p.rivet(x, 8.0, dense ? 0.55 : 0.45, ramp);
        }
        return p;
    }

    /** Cap sleeve flange: brass with bolts. */
    static Paint flange() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.9, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 421, false);
        for (double x = 2; x < 16; x += 4) {
            p.screw(x, 8, 0.8, BRASS, x * 0.9);
        }
        return p;
    }

    /** The tube end at a junction (hub plate): translucent glass with a dark edging. */
    static Paint hubPlate(boolean dense) {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double e = Math.max(Math.abs(u / 8 - 1), Math.abs(v / 8 - 1));
            if (dense) {
                return argb(150, Paint.ramp(BLUE, 0.4 + 0.3 * (1 - e) - (e > 0.88 ? 0.2 : 0)) & 0xFFFFFF);
            }
            return argb((int) (80 + 70 * Math.pow(e, 4)), Paint.mix(rgb(0xc9ecf7), rgb(0x7ab8d4), Math.pow(e, 3)) & 0xFFFFFF);
        });
        return p;
    }

    /**
     * Plug face: a clean octagonal brass flange: eight bolts in a circle, an engraved ring, a dark recess for the
     * copper stopper (the model gives its volume). No random small details: everything is symmetrical around the centre.
     */
    static Paint plug() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double a = Math.atan2(v - 8, u - 8);
            double shine = 0.5 + 0.5 * Math.cos(a - Math.PI * 0.75);
            double r = Math.hypot(u - 8, v - 8) / 8;
            return at(BRASS, 2.4 + shine * 1.6 - r * 0.8);
        });
        p.ring(8, 8, 7.3, 7.9, at(BRASS, 1.3));
        p.ring(8, 8, 5.0, 5.35, at(BRASS, 1.0));
        p.ring(8, 8, 5.35, 5.6, at(BRASS, 4.6));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            p.screw(8 + Math.cos(a) * 6.3, 8 + Math.sin(a) * 6.3, 0.75, BRASS, a);
        }
        p.disc(8, 8, 4.9, rgb(0x161c20));
        p.ring(8, 8, 4.4, 4.9, at(IRON, 0.6));
        return p;
    }

    /**
     * Warehouse dock door (a dead end with a warehouse): a steel roller shutter of horizontal slats in a brass frame, at the bottom
     * a yellow-black bumper, a handle in the middle, a teal lamp above the door. It is clear at once that this is not an ordinary plug.
     */
    static Paint dock() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.6, 1);
        p.slab(2.2, 2.6, 13.8, 14.2, IRON, 1.2, 1);
        for (int k = 0; k < 8; k++) {
            double y = 3.0 + k * 1.3;
            p.rect(2.6, y, 13.4, y + 1.0, at(IRON, 2.6 + (k % 2) * 0.4));
            p.rect(2.6, y, 13.4, y + 0.25, at(IRON, 4.2));
            p.rect(2.6, y + 1.0, 13.4, y + 1.3, at(IRON, 0.6));
        }
        // bumper at the bottom of the door
        for (int k = 0; k < 6; k++) {
            double x = 2.6 + k * 1.8;
            p.poly(new double[]{x, x + 0.9, x + 1.8, x + 0.9}, new double[]{14.0, 12.6, 12.6, 14.0}, rgb(0xE0B020));
        }
        p.rect(2.6, 12.4, 13.4, 12.6, at(IRON, 0.4));
        // handle and lamp
        p.rect(6.6, 11.0, 9.4, 11.6, at(BRASS, 4.4));
        p.rect(6.6, 11.6, 9.4, 11.9, at(BRASS, 1.4));
        p.glowRect(6.4, 0.9, 9.6, 2.1, 0.5, at(CYAN, 5.6), 90);
        for (double[] b : new double[][]{{1.3, 1.3}, {14.7, 1.3}, {1.3, 14.7}, {14.7, 14.7}}) {
            p.rivet(b[0], b[1], 0.55, BRASS);
        }
        return p;
    }
    static void textures() throws Exception {
        plug().save(Forge2.tex("pipe_plug"));
        dock().save(Forge2.tex("pipe_dock"));
        glass(true, false, 431).save(Forge2.tex("pipe_glass_u"));
        glass(false, false, 431).save(Forge2.tex("pipe_glass_v"));
        glassDiag(false).save(Forge2.tex("pipe_glass_d"));
        glass(true, true, 433).save(Forge2.tex("pipe_dense_u"));
        glass(false, true, 433).save(Forge2.tex("pipe_dense_v"));
        glassDiag(true).save(Forge2.tex("pipe_dense_d"));
        band(false).save(Forge2.tex("pipe_band"));
        band(true).save(Forge2.tex("pipe_dense_band"));
        flange().save(Forge2.tex("pipe_flange"));
        hubPlate(false).save(Forge2.tex("pipe_hub"));
        hubPlate(true).save(Forge2.tex("pipe_dense_hub"));
    }

    // ----------------------------------------------------------------------------------------- models
    static String[][] texList(boolean dense) {
        String p = dense ? "pipe_dense" : "pipe_glass";
        return new String[][]{{"su", p + "_u"}, {"sv", p + "_v"}, {"sd", p + "_d"}, {"band", dense ? "pipe_dense_band" : "pipe_band"},
                {"flange", "pipe_flange"}, {"hub", dense ? "pipe_dense_hub" : "pipe_hub"}, {"plug", "pipe_plug"}, {"dock", "pipe_dock"},
                {"dial", "resonator_dial"}, {"ice", "sat_ice"}, {"brass", "sat_brass"}, {"copper", "resonator_pipe"}, {"dark", "sat_dark"}};
    }

    static final double[] UV = {0, 0, 16, 16};

    /**
     * An octagonal tube: A (the wide box) gives the side planes east/west (across = v), B the top/bottom (across = u),
     * the plates are the diagonals. The unwraps across differ, so the su/sv textures are transposed; the whole texture
     * lies across the face, along the tube the pattern is uniform (no seams between blocks).
     */
    static void tube(List<Models.El> l, double lo, double hi, double z0, double z1, double c, String sideTex, String tbTex, String diagTex) {
        tube(l, lo, hi, z0, z1, c, sideTex, tbTex, diagTex, 0);
    }

    /** glow > 0 = the glass glows (a powered network): the block emits no light, so it does not stop mobs from spawning. */
    static void tube(List<Models.El> l, double lo, double hi, double z0, double z1, double c, String sideTex, String tbTex, String diagTex,
                     int glow) {
        Models.El a = Models.e(lo, lo + c, z0, hi, hi - c, z1);
        a.faces.put("east", Models.El.face("#" + sideTex, UV));
        a.faces.put("west", Models.El.face("#" + sideTex, UV));
        l.add(a.glow(glow));
        Models.El b = Models.e(lo + c, lo, z0, hi - c, hi, z1);
        b.faces.put("up", Models.El.face("#" + tbTex, UV));
        b.faces.put("down", Models.El.face("#" + tbTex, UV));
        l.add(b.glow(glow));
        // glass bevel faces: thin plates exactly on the bevel, no gaps at the edges
        Models.octCorners(l, lo, lo, hi, hi, z0, z1, c, 0.4, "#" + diagTex, UV, null, null, glow);
    }

    /** Glass glow of a powered pipe (the face light level; the block itself emits no light). */
    static final int GLOW = 11;

    /**
     * A ring band (a hollow octagon around the Z axis) of eight thin plates of thickness t: brass shows outside, while inside
     * the tube stays transparent (no "diaphragm").
     */
    static void ring(List<Models.El> l, double lo, double hi, double z0, double z1, double c, String tex) {
        ring(l, lo, hi, z0, z1, c, tex, 0.75);
    }

    static void ring(List<Models.El> l, double lo, double hi, double z0, double z1, double c, String tex, double t) {
        l.add(Models.e(lo + c, hi - t, z0, hi - c, hi, z1).only("#" + tex, "up", "north", "south"));
        l.add(Models.e(lo + c, lo, z0, hi - c, lo + t, z1).only("#" + tex, "down", "north", "south"));
        l.add(Models.e(lo, lo + c, z0, lo + t, hi - c, z1).only("#" + tex, "west", "north", "south"));
        l.add(Models.e(hi - t, lo + c, z0, hi, hi - c, z1).only("#" + tex, "east", "north", "south"));
        // the ring corners have ends, otherwise from the front the ring looks like four separate strips
        Models.octCorners(l, lo, lo, hi, hi, z0, z1, c, t, "#" + tex, "#" + tex, 0);
    }
    /**
     * A washer: a flat octagonal ring between an outer octagon (lo..hi, bevel c) and an inner one (inLo..inHi, bevel inC)
     * at depth z0..z1. Side boxes go on the sides, tiles turned by 45° in the corners, halfway between the bevels.
     */
    static void washer(List<Models.El> l, double lo, double hi, double c, double inLo, double inHi, double inC,
                       double z0, double z1, String tex) {
        double bx0 = Math.max(lo + c, inLo), bx1 = Math.min(hi - c, inHi);
        l.add(Models.e(bx0, inHi, z0, bx1, hi, z1).all(tex));
        l.add(Models.e(bx0, lo, z0, bx1, inLo, z1).all(tex));
        l.add(Models.e(lo, lo + c, z0, inLo, hi - c, z1).all(tex));
        l.add(Models.e(inHi, lo + c, z0, hi, hi - c, z1).all(tex));
        double mid = 8, zm = (z0 + z1) / 2;
        double outer = hi - mid - c / 2, inner = inHi - mid - inC / 2;
        double th = (outer - inner) * Math.sqrt(2) + 0.2, len = c * Math.sqrt(2) * 0.98;
        for (int sx : new int[]{-1, 1}) {
            for (int sy : new int[]{-1, 1}) {
                // the ends of the corner tiles are 0.04 deeper than the boxes: the tiles go into the boxes and into the corners of the sleeve ring (whose ends
                // are at 0.02), coinciding faces would flicker
                double d = (outer + inner) / 2, cx = mid + sx * d, cy = mid + sy * d;
                l.add(Models.e(cx - len / 2, cy - th / 2, z0 + 0.04, cx + len / 2, cy + th / 2, z1 - 0.04).all(tex)
                        .rot("z", sx * sy > 0 ? -45 : 45, cx, cy, zm));
            }
        }
    }

    /** Models of one pipe side that the block state draws together: their faces must not flicker against each other. */
    private static final java.util.Map<String, List<Models.El>> PARTS = new java.util.LinkedHashMap<>();

    @SafeVarargs
    private static void checkTogether(String name, List<Models.El>... lists) {
        List<Models.El> all = new ArrayList<>();
        for (List<Models.El> l : lists) {
            all.addAll(l);
        }
        Models.checkCoplanar(name, all.toArray(new Models.El[0]));
    }

    static void pipe(String name, boolean dense) throws Exception {
        double lo = dense ? 2 : 4, hi = 16 - lo, c = dense ? 2.2 : 1.3;
        double rlo = lo - (dense ? 0.9 : 0.8), rhi = 16 - rlo, rc = c + (dense ? 0.4 : 0.3);
        String[][] tex = texList(dense);
        String particle = dense ? "pipe_dense_u" : "pipe_glass_u";
        PARTS.clear();

        // a straight tube over the whole block (also a glowing one, for a powered network)
        for (int glow : new int[]{0, GLOW}) {
            String sfx = glow > 0 ? "_lit" : "";
            List<Models.El> straight = new ArrayList<>();
            tube(straight, lo, hi, 0, 16, c, "sv", "su", "sd", glow);
            Models.model(name + "_straight" + sfx, true, particle, tex, straight.toArray(new Models.El[0]));
            PARTS.put("straight", straight);

            // a sleeve to the neighbouring pipe at a junction: a tube + a ring at the hub (the ring is brass and does not glow)
            List<Models.El> arm = new ArrayList<>();
            tube(arm, lo, hi, 0, lo, c, "sv", "su", "sd", glow);
            ring(arm, rlo, rhi, lo - (dense ? 2.0 : 1.5), lo - (dense ? 0.4 : 0.2), rc, "band");
            Models.model(name + "_arm" + sfx, true, particle, tex, arm.toArray(new Models.El[0]));
            PARTS.put("arm", arm);

            // the hub end where there is no connection (slightly inwards, so it does not coincide with the frame edges)
            Models.El endPlate = Models.e(lo, lo, lo + 0.06, hi, hi, lo + 0.06).only("#hub", "north").glow(glow);
            Models.model(name + "_end" + sfx, true, particle, tex, endPlate);
        }

        // a band in the middle of a straight run
        List<Models.El> band = new ArrayList<>();
        ring(band, rlo, rhi, dense ? 5.8 : 6.4, dense ? 10.2 : 9.6, rc, "band");
        Models.model(name + "_band", true, particle, tex, band.toArray(new Models.El[0]));
        PARTS.put("band", band);

        if (dense) {
            // a joint with a basic pipe: a brass adapter washer closes the dense pipe's end around the thin one, without a gap.
            // The washer is no wider than the pipe itself (1.9..14.1): the sleeve ring (1.1..1.85 and 14.15..14.9) stays outside, their front faces
            // in the z=0 plane do not overlap (otherwise flicker); the block state puts no cap sleeve on this side.
            List<Models.El> reducer = new ArrayList<>();
            washer(reducer, lo - 0.1, hi + 0.1, c - 0.05, 4.0, 12.0, 1.3, 0.0, 0.9, "#flange");
            Models.model(name + "_reducer", true, particle, tex, reducer.toArray(new Models.El[0]));
            PARTS.put("reducer", reducer);
        }

        // hub frame: twelve brass edges and eight corner bolt blocks
        List<Models.El> hub = new ArrayList<>();
        double tb = dense ? 0.7 : 0.5, cs = dense ? 1.9 : 1.3;
        for (double a : new double[]{lo, hi - tb}) {
            for (double b : new double[]{lo, hi - tb}) {
                hub.add(Models.e(lo + cs, a, b, hi - cs, a + tb, b + tb).all("#band"));
                hub.add(Models.e(a, lo + cs, b, a + tb, hi - cs, b + tb).all("#band"));
                hub.add(Models.e(a, b, lo + cs, a + tb, b + tb, hi - cs).all("#band"));
            }
        }
        for (double x : new double[]{lo, hi - cs}) {
            for (double y : new double[]{lo, hi - cs}) {
                for (double z : new double[]{lo, hi - cs}) {
                    hub.add(Models.e(x, y, z, x + cs, y + cs, z + cs).all("#band"));
                }
            }
        }
        Models.model(name + "_hub", true, particle, tex, hub.toArray(new Models.El[0]));

        // at a device: the basic pipe gets a brass sleeve and a flange; on the dense one the end is closed by a washer with a hole exactly for the rim
        // of the bus mini tube (5.4..10.6), otherwise the thin bus tube hung in a wide hollow sleeve and the inside showed through the gap
        List<Models.El> cap = new ArrayList<>();
        double clo = lo - (dense ? 1.2 : 1.0), chi = 16 - clo;
        if (dense) {
            // a solid octagonal cap flange along the pipe outline (no hole and no rotated tiles, so no gaps),
            // the bus mini tube or the device socket rests against it
            Buses.oct(cap, lo - 0.1, lo - 0.1, 0.0, hi + 0.1, hi + 0.1, 0.9, c, "flange", "flange", "flange", "flange", "flange");
        } else {
            // A solid octagonal cap flange (removes see-through gaps when joining buses and devices)
            Buses.oct(cap, clo - 0.6, clo - 0.6, 0.0, chi + 0.6, chi + 0.6, 0.7, c + 1.0, "flange", "flange", "flange", "flange", "flange");
            ring(cap, clo, chi, 0.7, 3.0, c + 0.4, "band");
        }
        Models.model(name + "_cap", true, particle, tex, cap.toArray(new Models.El[0]));

        // Side plug: an octagonal brass flange (the bolts in a circle are the texture), in the middle a stepped copper stopper
        // with a brass ring and a spire handle, above the stopper a small gauge right on the flange, below three short
        // icicles. Everything is symmetrical and stays within the flange outline (except the icicles, which hang within the block).
        List<Models.El> plug = new ArrayList<>();
        double f0 = lo - 1.1, f1 = lo - 0.02;
        Buses.oct(plug, lo - 0.4, lo - 0.4, f0, hi + 0.4, hi + 0.4, f1, c + 0.3, "flange", "flange", "flange", "plug", "flange");
        double bossR = dense ? 3.4 : 2.4, mid = 8;
        Buses.oct(plug, mid - bossR, mid - bossR, f0 - 0.45, mid + bossR, mid + bossR, f0, bossR * 0.42, "dark", "dark", "dark", "dark", null);
        Buses.oct(plug, mid - bossR + 0.7, mid - bossR + 0.7, f0 - 0.75, mid + bossR - 0.7, mid + bossR - 0.7, f0 - 0.45, (bossR - 0.7) * 0.42,
                "brass", "brass", "brass", "brass", null);
        Buses.oct(plug, mid - bossR + 1.3, mid - bossR + 1.3, f0 - 1.05, mid + bossR - 1.3, mid + bossR - 1.3, f0 - 0.75, (bossR - 1.3) * 0.42,
                "dark", "dark", "dark", "dark", null);
        // a cryo sight glass in the middle of the stopper
        plug.add(Models.e(mid - 0.6, mid - 0.6, f0 - 1.35, mid + 0.6, mid + 0.6, f0 - 1.05).all("#ice").glow(12));
        // a gauge on the flange above the stopper
        double gw = dense ? 1.2 : 0.9, gy = mid + bossR + (dense ? 1.5 : 1.15);
        plug.add(Models.e(mid - gw - 0.25, gy - gw - 0.25, f0 - 0.2, mid + gw + 0.25, gy + gw + 0.25, f0).all("#brass"));
        plug.add(Models.e(mid - gw, gy - gw, f0 - 0.26, mid + gw, gy + gw, f0 - 0.2).only("#dial", "north"));
        // icicles under the flange
        double iy = lo - 0.4;
        for (double[] ic : new double[][]{{mid - 0.3, dense ? 1.5 : 1.2}, {mid - (dense ? 2.6 : 1.9), dense ? 0.9 : 0.7}, {mid + (dense ? 2.0 : 1.3), dense ? 0.9 : 0.7}}) {
            plug.add(Models.e(ic[0], iy - ic[1], f0 + 0.15, ic[0] + 0.6, iy, f0 + 0.75).all("#ice"));
        }        Models.model(name + "_plug", false, particle, tex, plug.toArray(new Models.El[0]));

        // warehouse dock door: the same flange, but with a roller shutter; a hood frame and a teal lamp above the door
        List<Models.El> dock = new ArrayList<>();
        Buses.oct(dock, lo - 0.4, lo - 0.4, f0, hi + 0.4, hi + 0.4, f1, c + 0.3, "flange", "flange", "flange", "dock", "flange");
        double dl = lo + (dense ? 1.2 : 0.8), dh = hi - (dense ? 1.2 : 0.8);
        dock.add(Models.e(dl - 0.5, dh - 0.1, f0 - 0.6, dh + 0.5, dh + 0.4, f0).all("#brass"));
        dock.add(Models.e(dl - 0.5, dl - 0.4, f0 - 0.3, dl, dh - 0.1, f0).all("#brass"));
        dock.add(Models.e(dh, dl - 0.4, f0 - 0.3, dh + 0.5, dh - 0.1, f0).all("#brass"));
        Models.model(name + "_dock", false, particle, tex, dock.toArray(new Models.El[0]));

        // what the block state draws together in one orientation: straight + band + sleeve/washer; hub + sleeve + sleeve/washer
        checkTogether(name + " straight+band+cap", PARTS.get("straight"), band, cap);
        checkTogether(name + " hub+arm+cap", hub, PARTS.get("arm"), cap);
        if (dense) {
            checkTogether(name + " straight+band+reducer", PARTS.get("straight"), band, PARTS.get("reducer"));
            checkTogether(name + " hub+arm+reducer", hub, PARTS.get("arm"), PARTS.get("reducer"));
        }

        // item: a straight pipe with a band and caps at the ends
        List<Models.El> item = new ArrayList<>();
        tube(item, lo, hi, 0, 16, c, "sv", "su", "sd");
        ring(item, rlo, rhi, dense ? 5.8 : 6.4, dense ? 10.2 : 9.6, rc, "band");
        ring(item, clo, chi, dense ? 0.9 : 0.7, dense ? 3.6 : 3.0, c + (dense ? 0.6 : 0.4), "band");
        ring(item, clo - 0.6, chi + 0.6, 0.0, dense ? 0.9 : 0.7, c + 1.0, "flange");
        ring(item, clo, chi, 16 - (dense ? 3.6 : 3.0), 16 - (dense ? 0.9 : 0.7), c + (dense ? 0.6 : 0.4), "band");
        ring(item, clo - 0.6, chi + 0.6, 16 - (dense ? 0.9 : 0.7), 16.0, c + 1.0, "flange");
        Models.model(name + "_item", true, particle, tex, item.toArray(new Models.El[0]));
        Models.item(name, name + "_item");
        Forge2.addDisplay(name + "_item", 135, dense ? 0.66 : 0.74);
        blockstate(name, dense);
    }

    static final String LINKED = "pipe|device|reducer";

    static String c(String dir, String v) {
        return "{\"" + dir + "\": \"" + v + "\"}";
    }

    /** The condition "the pipe is not straight" (along no axis is it "two opposite links and nothing else"). */
    static String notStraight() {
        String z = "{\"OR\": [" + c("north", "none") + ", " + c("south", "none") + ", " + c("east", LINKED) + ", " + c("west", LINKED) + ", "
                + c("up", LINKED) + ", " + c("down", LINKED) + "]}";
        String x = "{\"OR\": [" + c("east", "none") + ", " + c("west", "none") + ", " + c("north", LINKED) + ", " + c("south", LINKED) + ", "
                + c("up", LINKED) + ", " + c("down", LINKED) + "]}";
        String y = "{\"OR\": [" + c("up", "none") + ", " + c("down", "none") + ", " + c("north", LINKED) + ", " + c("south", LINKED) + ", "
                + c("east", LINKED) + ", " + c("west", LINKED) + "]}";
        return "{\"AND\": [" + z + ", " + x + ", " + y + "]}";
    }

    static String straight(String a, String b, String o1, String o2, String o3, String o4) {
        return "{\"AND\": [" + c(a, LINKED) + ", " + c(b, LINKED) + ", " + c(o1, "none") + ", " + c(o2, "none") + ", "
                + c(o3, "none") + ", " + c(o4, "none") + "]}";
    }

    /**
     * Pipe block state: glass (straight run, sleeves, ends) in two kinds, normal and glowing (active=true, the network
     * is powered); brass (band, hub, cuffs, adapter washer) does not depend on power.
     */
    static void blockstate(String name, boolean dense) throws Exception {
        String[][] rot = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"},
                {"up", ", \"x\": 270"}, {"down", ", \"x\": 90"}};
        List<String> parts = new ArrayList<>();
        String[] power = {c("active", "false"), c("active", "true")};
        String[] lit = {"", "_lit"};
        // straight runs along three axes and a band in the middle
        String[][] axes = {
                {"z", straight("north", "south", "east", "west", "up", "down"), ""},
                {"x", straight("east", "west", "north", "south", "up", "down"), ", \"y\": 90"},
                {"y", straight("up", "down", "north", "south", "east", "west"), ", \"x\": 90"}};
        for (String[] ax : axes) {
            for (int p = 0; p < 2; p++) {
                parts.add("    {\"when\": {\"AND\": [" + ax[1] + ", " + power[p] + "]}, \"apply\": {\"model\": \"" + Forge2.NS + name
                        + "_straight" + lit[p] + "\"" + ax[2] + "}}");
            }
        }
        String ns = notStraight();
        parts.add("    {\"when\": " + ns + ", \"apply\": {\"model\": \"" + Forge2.NS + name + "_hub\"}}");
        for (String[] r : rot) {
            for (int p = 0; p < 2; p++) {
                parts.add("    {\"when\": {\"AND\": [" + c(r[0], LINKED) + ", " + ns + ", " + power[p] + "]}, \"apply\": {\"model\": \""
                        + Forge2.NS + name + "_arm" + lit[p] + "\"" + r[1] + "}}");
                parts.add("    {\"when\": {\"AND\": [" + c(r[0], "none") + ", " + ns + ", " + power[p] + "]}, \"apply\": {\"model\": \""
                        + Forge2.NS + name + "_end" + lit[p] + "\"" + r[1] + "}}");
            }
            // a sleeve only at a device: at a joint with a thin pipe its place is taken by the adapter washer
            parts.add("    {\"when\": " + c(r[0], "device") + ", \"apply\": {\"model\": \"" + Forge2.NS + name + "_cap\"" + r[1] + "}}");
            if (dense) {
                parts.add("    {\"when\": " + c(r[0], "reducer") + ", \"apply\": {\"model\": \"" + Forge2.NS + name + "_reducer\"" + r[1] + "}}");
            }
        }
        Forge2.write(Forge2.RES.resolve("blockstates/" + name + ".json"), "{\n  \"multipart\": [\n" + String.join(",\n", parts) + "\n  ]\n}\n");
    }

    static void all() throws Exception {
        textures();
        pipe("basic_snow_pipe", false);
        pipe("dense_frost_pipe", true);
    }
}
