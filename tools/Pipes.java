import java.util.*;

/**
 * The mod's pipes: the basic one is a clear ice tube, the dense one deep blue ice. Every block joint is held by a copper
 * clamp with a bolted ear, a thin layer of snow lies inside along the tube and hoarfrost grows on the glass by the clamps;
 * on a powered network pulses of cold light run along the glass. Straight runs pick a random look per block (the block
 * state's weighted models): plain or with a gate valve and its handwheel; the snow in horizontal pipes picks its drifts apart
 * from that, and every sixth straight horizontal block carries a heat lamp, two nixie tubes the renderer lights with the channel count. Junctions are an
 * open cast fitting (copper edges, brass corners), devices are met by a bolted brass flange with a copper collar. Everything
 * except the glass and the lamp's number is opaque and baked into the chunk.
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
        frost(p, across, seed);
        return p;
    }

    /** Hoarfrost on the glass by the clamps: scattered pale pixels near both ends of the block (along the tube), never a fill. */
    static void frost(Paint p, boolean across, long seed) {
        int sd = (int) seed;
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double along = across ? (j + 0.5) / K : (i + 0.5) / K;
                double d = Math.min(along, 16 - along);
                double f = 1.8 * (0.45 + 0.8 * Atlant.noise(i, j, 5, sd)) - d;
                if (f > 0 && Atlant.hash(i, j, sd + 1) < 0.12 + f * 0.18) {
                    p.over(i, j, argb(f > 0.8 ? 200 : 140, FROST[f > 0.8 ? 6 : 5]));
                }
            }
        }
    }

    /**
     * The glass of a powered pipe: the same glass with pulses of cold light running along the tube, two a block, so the
     * pulses of neighbouring blocks join into one stream.
     */
    static Paint litGlass(Paint p, boolean across, boolean dense) {
        p.animFrames = 32;
        p.frameTime = 2;
        p.animate(0, 0, 16, 16, (q, t) -> {
            for (int j = 0; j < q.h; j++) {
                for (int i = 0; i < q.w; i++) {
                    double along = across ? (j + 0.5) / K : (i + 0.5) / K;
                    double side = across ? (i + 0.5) / K : (j + 0.5) / K;
                    double phase = ((along / 8 - 2 * t) % 1 + 1) % 1;
                    double pulse = Math.pow(Math.max(0, 1 - Math.abs(phase - 0.5) * 3.5), 2);
                    double core = 1 - Math.abs(side / 8 - 1);
                    int a = (int) (pulse * core * (dense ? 120 : 150));
                    if (a > 6) {
                        q.over(i, j, argb(a, dense ? 0x9ed8ff : 0xd8fbff));
                    }
                }
            }
        });
        return p;
    }

    /** Diagonal (narrow) faces: a light "highlight strip"; along them is v. */
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

    /**
     * The clamp: copper in the vanilla tones, a light edge and a dark one across every 1-unit strip (the clamp plates
     * sample v 0..1 and 15..16 of it), darker for the dense pipe.
     */
    static Paint clamp(boolean dense) {
        Paint p = new Paint(16, 16, K);
        int[] cu = Atlant.COPPER;
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double tone = Atlant.hash(i / 2, j / 2, dense ? 451 : 452) * 0.6 + Atlant.noise(i, j, 6, 453) * 0.4;
                int c = cu[(int) Math.max(0, Math.min(cu.length - 1, (dense ? 1.5 : 2.5) + tone * 3.5))];
                if (j % 2 == 0) {
                    c = cu[Math.min(cu.length - 1, (int) ((dense ? 4.5 : 5.5) + tone * 2))];
                }
                p.px(i, j, rgb(c));
            }
        }
        return p;
    }

    /** The bolt: a dark iron hex head with a light corner. */
    static Paint bolt() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> at(IRON, 3.2 + ((int) (u / 2) + (int) (v / 2)) % 2 * 0.4));
        for (int k = 0; k < 16; k += 4) {
            p.rect(k, k, k + 1, k + 1, at(IRON, 5.6));
        }
        return p;
    }

    /** The flange at a device: brass, a fine grain, a dark groove round the inside. */
    static Paint flange() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.9, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 421, false);
        return p;
    }

    /** The handwheel: red paint on iron, a light edge on top, the paint worn through to the iron here and there. */
    static Paint wheel() {
        Paint p = new Paint(16, 16, K);
        int[] red = {0x4a0f0c, 0x6e1812, 0x8f2219, 0xae2e20, 0xc84030};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double tone = Atlant.hash(i / 2, j / 2, 471) * 0.6 + Atlant.noise(i, j, 6, 472) * 0.4;
                int c = red[(int) Math.min(red.length - 1, 1 + tone * 3.2)];
                if (j % 4 == 0) {
                    c = red[4];
                }
                if (Atlant.hash(i, j, 473) > 0.96) {
                    c = at(IRON, 3.0);
                }
                p.px(i, j, rgb(c));
            }
        }
        return p;
    }

    /**
     * The snow inside a pipe: packed snow with blue shade in its hollows and at its edges, low ridges blown along the tube,
     * rare glints of ice; never one flat white.
     */
    static Paint snow() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                double ridge = 0.5 + 0.5 * Math.sin(v * 1.3 + Atlant.noise(i, j, 6, 481) * 3);
                double edge = Math.max(0, Math.abs(u - 8) - 1.6) / 2.2;
                double t = 0.55 + 0.35 * ridge - 0.45 * edge + (Atlant.hash(i / 2, j / 2, 482) - 0.5) * 0.25;
                int c = t > 0.85 ? FROST[6] : t > 0.62 ? FROST[5] : t > 0.42 ? FROST[4] : t > 0.25 ? FROST[3] : FROST[2];
                if (Atlant.hash(i, j, 483) > 0.985) {
                    c = rgb(0xe2fffe);
                }
                p.px(i, j, rgb(c));
            }
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
        litGlass(glass(true, false, 431), true, false).save(Forge2.tex("pipe_glass_u_lit"));
        litGlass(glass(false, false, 431), false, false).save(Forge2.tex("pipe_glass_v_lit"));
        litGlass(glassDiag(false), true, false).save(Forge2.tex("pipe_glass_d_lit"));
        litGlass(glass(true, true, 433), true, true).save(Forge2.tex("pipe_dense_u_lit"));
        litGlass(glass(false, true, 433), false, true).save(Forge2.tex("pipe_dense_v_lit"));
        litGlass(glassDiag(true), true, true).save(Forge2.tex("pipe_dense_d_lit"));
        clamp(false).save(Forge2.tex("pipe_clamp"));
        clamp(true).save(Forge2.tex("pipe_dense_clamp"));
        bolt().save(Forge2.tex("pipe_bolt"));
        wheel().save(Forge2.tex("pipe_wheel"));
        snow().save(Forge2.tex("pipe_snow"));
        flange().save(Forge2.tex("pipe_flange"));
        hubPlate(false).save(Forge2.tex("pipe_hub"));
        hubPlate(true).save(Forge2.tex("pipe_dense_hub"));
    }

    // ----------------------------------------------------------------------------------------- models
    static String[][] texList(boolean dense) {
        String p = dense ? "pipe_dense" : "pipe_glass";
        return new String[][]{{"su", p + "_u"}, {"sv", p + "_v"}, {"sd", p + "_d"},
                {"lu", p + "_u_lit"}, {"lv", p + "_v_lit"}, {"ld", p + "_d_lit"},
                {"clamp", dense ? "pipe_dense_clamp" : "pipe_clamp"}, {"bolt", "pipe_bolt"}, {"wood", "nixie_wood"}, {"socket", "nixie_socket"},
                {"mesh", "nixie_mesh"}, {"nglass", "nixie_glass"},
                {"wheel", "pipe_wheel"},
                {"flange", "pipe_flange"}, {"hub", dense ? "pipe_dense_hub" : "pipe_hub"}, {"plug", "pipe_plug"}, {"dock", "pipe_dock"},
                {"dial", "resonator_dial"}, {"ice", "sat_ice"}, {"brass", "sat_brass"}, {"copper", "resonator_pipe"}, {"dark", "sat_dark"},
                {"snow", "pipe_snow"}};
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
        String[][] tex = texList(dense);
        String particle = dense ? "pipe_dense_u" : "pipe_glass_u";
        PARTS.clear();

        // one clamp at every block joint (each block carries it at its own north end), with a bolted ear on top; opaque,
        // so it costs nothing to sort
        double cl = lo - (dense ? 0.7 : 0.6), ch = 16 - cl, cc = c + (dense ? 0.35 : 0.3);
        List<Models.El> clamps = new ArrayList<>();
        ring(clamps, cl, ch, 0.06, 1.0, cc, "clamp", dense ? 0.8 : 0.65);
        double ew = dense ? 1.4 : 1.1, bw = dense ? 0.55 : 0.45;
        clamps.add(Models.e(8 - ew, ch, 0.06, 8 + ew, ch + (dense ? 1.2 : 1.0), 1.0).only("#clamp", "up", "north", "south", "east", "west"));
        // the bolt through the ear: its head on one side
        double by0 = ch + (dense ? 0.3 : 0.25);
        clamps.add(Models.e(8 + ew, by0, 0.53 - bw / 1.2, 8 + ew + 0.35, by0 + 2 * bw, 0.53 + bw / 1.2).only("#bolt", "up", "down", "north", "south", "east"));

        // the looks of a straight block: plain, or a gate valve with a handwheel over a middle clamp (the heat lamps are the
        // renderer's: they show the channels running through the pipe)
        double mz0 = 7.0, mz1 = 9.0;
        List<Models.El> midClamp = new ArrayList<>();
        ring(midClamp, cl, ch, mz0, mz1, cc, "clamp", dense ? 0.8 : 0.65);
        java.util.Map<String, List<Models.El>> looks = new java.util.LinkedHashMap<>();
        looks.put("", new ArrayList<>());

        // the valve: an octagonal bonnet on the clamp, an iron stem and a red handwheel (an octagonal rim, two spokes, a hub)
        List<Models.El> valve = new ArrayList<>(midClamp);
        double bh = dense ? 1.5 : 1.2, bs2 = dense ? 1.3 : 1.05;
        valve.add(Models.e(8 - bs2, ch, 8 - bs2, 8 + bs2, ch + bh, 8 + bs2).only("#brass", "up", "north", "south", "east", "west"));
        valve.add(Models.e(8 - bs2, ch + 0.05, 8 - bs2, 8 + bs2, ch + bh - 0.1, 8 + bs2).only("#brass", "up", "north", "south", "east", "west")
                .rot("y", 45, 8, ch, 8));
        double wy0 = ch + bh + (dense ? 1.2 : 1.0), wy1 = wy0 + (dense ? 0.6 : 0.5);
        valve.add(Models.e(7.65, ch + bh, 7.65, 8.35, wy0, 8.35).only("#bolt", "north", "south", "east", "west"));
        double ap = dense ? 2.9 : 2.4, rt = dense ? 0.6 : 0.5, edge = 2 * ap * Math.tan(Math.PI / 8) + 0.1;
        for (int k = 0; k < 4; k++) {
            // the four straight sides of the rim, then the four diagonal ones (a little lower, so their tops never meet in one plane)
            double[] sx = {0, 0, -1, 1}, sz = {-1, 1, 0, 0};
            double cx = 8 + sx[k] * (ap - rt / 2), cz = 8 + sz[k] * (ap - rt / 2);
            double hx = sx[k] == 0 ? edge / 2 : rt / 2, hz = sz[k] == 0 ? edge / 2 : rt / 2;
            valve.add(Models.e(cx - hx, wy0, cz - hz, cx + hx, wy1, cz + hz).all("#wheel"));
            double ang = Math.PI / 4 + k * Math.PI / 2, dx = 8 + Math.cos(ang) * (ap - rt / 2), dz = 8 + Math.sin(ang) * (ap - rt / 2);
            valve.add(Models.e(dx - edge / 2, wy0 + 0.03, dz - rt / 2, dx + edge / 2, wy1 - 0.03, dz + rt / 2).all("#wheel")
                    .rot("y", k % 2 == 0 ? 45 : -45, dx, wy0, dz));
        }
        valve.add(Models.e(8 - ap + rt, wy0 + 0.06, 7.8, 8 + ap - rt, wy1 - 0.08, 8.2).only("#wheel", "up", "down", "north", "south"));
        valve.add(Models.e(7.8, wy0 + 0.08, 8 - ap + rt, 8.2, wy1 - 0.1, 8 + ap - rt).only("#wheel", "up", "down", "east", "west"));
        valve.add(Models.e(7.45, wy0 - 0.1, 7.45, 8.55, wy1 + 0.15, 8.55).all("#brass"));
        looks.put("_valve", valve);

        // the heat lamp on the east side of a straight pipe: two nixie tubes on a walnut base held off the middle clamp by a brass
        // bracket (SnowPipeRenderer turns it with the pipe and lights the digits on the tubes' mesh planes at x lx + 2.6)
        List<Models.El> lamp = new ArrayList<>(midClamp), lampGlass = new ArrayList<>();
        double lx = ch;
        lamp.add(Models.e(lx, 7.4, 7.4, lx + 1.0, 8.6, 8.6).only("#brass", "up", "down", "north", "south"));
        lamp.add(Models.e(lx + 1.0, 3.8, 7.2, lx + 1.4, 11.6, 8.8).all("#brass"));
        lamp.add(Models.e(lx + 1.4, 3.8, 4.9, lx + 3.8, 4.6, 11.1).all("#wood"));
        for (double zc : new double[]{8 - 1.35, 8 + 1.35}) {
            double z0 = zc - 1.2, z1 = zc + 1.2;
            lamp.add(Models.e(lx + 1.75, 4.6, z0 + 0.15, lx + 3.45, 5.2, z1 - 0.15).only("#socket", "north", "south", "east", "west", "up"));
            lamp.add(Models.e(lx + 2.45, 5.6, z0 + 0.4, lx + 2.55, 10.4, z1 - 0.4).only("#mesh", "east", "west"));
            lampGlass.add(Models.e(lx + 1.6, 5.2, z0, lx + 3.6, 11, z1).only("#nglass", "north", "south", "east", "west", "up"));
            lampGlass.add(Models.e(lx + 1.9, 11, z0 + 0.4, lx + 3.3, 11.5, z1 - 0.4).only("#nglass", "north", "south", "east", "west", "up"));
        }
        // the tubes' glass is a model of its own: the renderer draws it translucent, after the digits
        Models.model(name + "_lamp_glass", true, particle, tex, lampGlass.toArray(new Models.El[0]));
        Models.model(name + "_lamp", false, particle, tex, lamp.toArray(new Models.El[0]));

        // the snow inside the horizontal pipes: a thin layer on the flat bottom and, by the block, one of a few drifts on it
        // (the block state picks the drift apart from the look); a vertical pipe has none
        double sx0 = lo + c + 0.1, sx1 = hi - c - 0.1, sy0 = lo + 0.05, sy1 = lo + (dense ? 0.55 : 0.4);
        double mx0 = sx0 + (dense ? 0.9 : 0.6), mx1 = sx1 - (dense ? 0.9 : 0.6), my1 = sy1 + (dense ? 0.6 : 0.45);
        java.util.Map<String, List<Models.El>> drifts = new java.util.LinkedHashMap<>();
        double[][][] mounds = {{}, {{2.5, 7.5}}, {{9.0, 13.5}}, {{1.0, 3.5}, {11.5, 14.5}}};
        for (int k = 0; k < mounds.length; k++) {
            List<Models.El> d = new ArrayList<>();
            d.add(Models.e(sx0, sy0, 0, sx1, sy1, 16).only("#snow", "up", "east", "west"));
            for (double[] m : mounds[k]) {
                d.add(Models.e(mx0, sy1, m[0], mx1, my1, m[1]).only("#snow", "up", "north", "south", "east", "west"));
                d.add(Models.e(mx0 + 0.5, my1, m[0] + 0.8, mx1 - 0.5, my1 + 0.25, m[1] - 0.8).only("#snow", "up", "north", "south", "east", "west"));
            }
            drifts.put("_snow" + (k == 0 ? "" : "_" + (char) ('a' + k - 1)), d);
            Models.model(name + "_snow" + (k == 0 ? "" : "_" + (char) ('a' + k - 1)), false, "pipe_snow", tex, d.toArray(new Models.El[0]));
        }
        Models.model(name + "_arm_snow", false, "pipe_snow", tex, Models.e(sx0, sy0, 0, sx1, sy1, lo).only("#snow", "up", "east", "west"));

        for (int glow : new int[]{0, GLOW}) {
            String sfx = glow > 0 ? "_lit" : "";
            String gu = glow > 0 ? "lu" : "su", gv = glow > 0 ? "lv" : "sv", gd = glow > 0 ? "ld" : "sd";
            List<Models.El> straight = new ArrayList<>();
            tube(straight, lo, hi, 0, 16, c, gv, gu, gd, glow);
            straight.addAll(clamps);
            for (var look : looks.entrySet()) {
                List<Models.El> all = new ArrayList<>(straight);
                all.addAll(look.getValue());
                Models.model(name + "_straight" + look.getKey() + sfx, true, particle, tex, all.toArray(new Models.El[0]));
            }
            PARTS.put("straight", straight);

            // a sleeve to the junction box: a tube and the clamp at the block's edge
            List<Models.El> arm = new ArrayList<>();
            tube(arm, lo, hi, 0, lo, c, gv, gu, gd, glow);
            ring(arm, cl, ch, 0.1, 1.0, cc, "clamp", dense ? 0.8 : 0.65);
            Models.model(name + "_arm" + sfx, true, particle, tex, arm.toArray(new Models.El[0]));
            PARTS.put("arm", arm);

            // the hub end where there is no connection (inside the junction box, for the see-through dead ends)
            Models.El endPlate = Models.e(lo, lo, lo + 0.06, hi, hi, lo + 0.06).only("#hub", "north").glow(glow);
            Models.model(name + "_end" + sfx, true, particle, tex, endPlate);
        }

        if (dense) {
            // a joint with a basic pipe: a brass adapter washer closes the dense pipe's end around the thin one, without a gap.
            // The block state puts no cap on this side.
            List<Models.El> reducer = new ArrayList<>();
            washer(reducer, lo - 0.1, hi + 0.1, c - 0.05, 4.0, 12.0, 1.3, 0.0, 0.9, "#flange");
            Models.model(name + "_reducer", true, particle, tex, reducer.toArray(new Models.El[0]));
            PARTS.put("reducer", reducer);
        }

        // the junction: an open cast fitting the size of the tube - copper edges and brass corner castings - so the snowmen running
        // through it and a dead end's warehouse inside it stay in sight
        List<Models.El> hub = new ArrayList<>();
        double tb = dense ? 0.75 : 0.55, cs = dense ? 1.9 : 1.4;
        for (double a : new double[]{lo, hi - tb}) {
            for (double b : new double[]{lo, hi - tb}) {
                hub.add(Models.e(lo + cs, a, b, hi - cs, a + tb, b + tb).all("#clamp"));
                hub.add(Models.e(a, lo + cs, b, a + tb, hi - cs, b + tb).all("#clamp"));
                hub.add(Models.e(a, b, lo + cs, a + tb, b + tb, hi - cs).all("#clamp"));
            }
        }
        for (double x : new double[]{lo - 0.2, hi - cs + 0.2}) {
            for (double y : new double[]{lo - 0.2, hi - cs + 0.2}) {
                for (double z : new double[]{lo - 0.2, hi - cs + 0.2}) {
                    hub.add(Models.e(x, y, z, x + cs, y + cs, z + cs).all("#brass"));
                }
            }
        }
        Models.model(name + "_hub", true, particle, tex, hub.toArray(new Models.El[0]));

        // at a device: a brass flange against it with eight bolts, a copper collar on the tube (nothing dark: the flange is all
        // that shows round a small bus)
        List<Models.El> cap = new ArrayList<>();
        double fl = dense ? lo - 1.8 : lo - 1.7, fh = 16 - fl, fc = c + (dense ? 1.4 : 1.3);
        double cf1 = dense ? 1.1 : 1.05;
        Buses.oct(cap, fl, fl, 0, fh, fh, cf1, fc, "flange", "flange", "flange", "flange", "flange");
        ring(cap, cl, ch, cf1, cf1 + (dense ? 2.0 : 1.8), cc, "clamp", dense ? 0.8 : 0.65);
        double bs = 0.4, br = (ch - 8 + bs + 0.05) / Math.cos(Math.PI / 8); // the bolts clear the collar
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8, bx = 8 + Math.cos(ang) * br, by = 8 + Math.sin(ang) * br;
            cap.add(Models.e(bx - bs, by - bs, cf1, bx + bs, by + bs, cf1 + 0.4).all("#bolt"));
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

        // what the block state draws together in one orientation
        for (List<Models.El> look : looks.values()) {
            for (List<Models.El> d : drifts.values()) {
                checkTogether(name + " straight+look+snow+cap", PARTS.get("straight"), look, d, cap);
            }
        }
        checkTogether(name + " hub+arm+cap", hub, PARTS.get("arm"), cap);
        if (dense) {
            checkTogether(name + " straight+reducer", PARTS.get("straight"), PARTS.get("reducer"));
            checkTogether(name + " hub+arm+reducer", hub, PARTS.get("arm"), PARTS.get("reducer"));
        }

        // item: a straight pipe with its clamps, snow and a valve
        List<Models.El> item = new ArrayList<>();
        tube(item, lo, hi, 0, 16, c, "sv", "su", "sd");
        item.addAll(clamps);
        item.addAll(drifts.get("_snow"));
        item.addAll(valve);
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
                // the game picks one by the block's position: most blocks are plain, every few has a valve
                List<String> pick = new ArrayList<>();
                for (String[] look : new String[][]{{"", "8"}, {"_valve", "2"}}) {
                    pick.add("{\"model\": \"" + Forge2.NS + name + "_straight" + look[0] + lit[p] + "\"" + ax[2] + ", \"weight\": " + look[1] + "}");
                }
                parts.add("    {\"when\": {\"AND\": [" + ax[1] + ", " + power[p] + "]}, \"apply\": [" + String.join(", ", pick) + "]}");
            }
        }
        // snow lies only in horizontal pipes: the straight ones along z and x, and the sleeves to the four sides
        for (int k = 0; k < 2; k++) {
            List<String> pick = new ArrayList<>();
            for (String[] d : new String[][]{{"_snow", "3"}, {"_snow_a", "2"}, {"_snow_b", "2"}, {"_snow_c", "2"}}) {
                pick.add("{\"model\": \"" + Forge2.NS + name + d[0] + "\"" + axes[k][2] + ", \"weight\": " + d[1] + "}");
            }
            parts.add("    {\"when\": " + axes[k][1] + ", \"apply\": [" + String.join(", ", pick) + "]}");
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
            if (!r[0].equals("up") && !r[0].equals("down")) {
                parts.add("    {\"when\": {\"AND\": [" + c(r[0], LINKED) + ", " + ns + "]}, \"apply\": {\"model\": \"" + Forge2.NS + name
                        + "_arm_snow\"" + r[1] + "}}");
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
