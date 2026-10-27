import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The mod's three buses: a narrow device in the middle of the block. At the front the working feed or unload node (by purpose), behind it the technical
 * block (an 8x8x6 brass box with a rune plate on top), on all other sides sockets for pipes. When a network node is next to it
 * (a pipe, another bus, a terminal), the socket stretches into a mini tube to the very edge of the block.
 * <ul>
 * <li>Vacuum (import): a funnel with a turbine, a teal star on the roof;</li>
 * <li>Injector (export): a long nozzle barrel, a purple star;</li>
 * <li>Periscope: a monocle lens, an ice star.</li>
 * </ul>
 * The models face NORTH (-Z); the block state rotates them (a multipart by facing and by the connection flags north..down).
 */
final class Buses {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    // ------------------------------------------------------------------------------------------- textures
    /** A brass plate tile of 16x16 units (the shared plating). */
    static Paint brassPlate() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 301, true);
        p.grain(0, 0, 16, 16, 0.04, 302);
        p.frame(1.3, 1.3, 14.7, 14.7, 0.2, at(BRASS, 1.2));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.1, at(BRASS, 4.7));
        for (double t = 2.6; t < 14; t += 2.9) {
            for (double[] c : new double[][]{{t, 0.65}, {t, 15.35}, {0.65, t}, {15.35, t}}) {
                p.rivet(c[0], c[1], 0.3, BRASS);
            }
        }
        for (double[] c : new double[][]{{0, 0}, {16, 0}, {0, 16}, {16, 16}}) {
            double x = Math.min(c[0], 16 - 3.5), y = Math.min(c[1], 16 - 3.5);
            p.tint(x, y, x + 3.5, y + 3.5, argb(26, 0x120a04));
        }
        return p;
    }

    /** An eight-pointed star (two squares at 45 degrees) with volume: lighter at the top left. */
    static void star(Paint p, double cx, double cy, double r, int[] ramp) {
        double[] xs = new double[16], ys = new double[16];
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 - Math.PI / 2, rr = i % 2 == 0 ? r : r * 0.6;
            xs[i] = cx + Math.cos(a) * rr;
            ys[i] = cy + Math.sin(a) * rr;
        }
        double[] xo = new double[16], yo = new double[16];
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8 - Math.PI / 2, rr = (i % 2 == 0 ? r : r * 0.6) + 0.2;
            xo[i] = cx + Math.cos(a) * rr;
            yo[i] = cy + Math.sin(a) * rr;
        }
        p.poly(xo, yo, at(ramp, 0.6));
        p.polyFn(xs, ys, (u, v) -> {
            double lit = 0.5 + 0.5 * (-(u - cx) * 0.6 - (v - cy) * 0.8) / r;
            return Paint.ramp(ramp, 0.5 + 0.45 * lit - 0.12 * Math.hypot(u - cx, v - cy) / r);
        });
        p.sphere(cx, cy, r * 0.22, ramp, 0.8, 1.0);
    }

    /**
     * The 6x6 roof of the technical block: a dark rune panel in a brass frame whose sign says from above what the bus does - the
     * Import Bus four cyan arrows pointing in, the Export Bus four amber arrows pointing out, the Periscope a violet watching eye -
     * each in its own colour, with its own row of runes along the edges.
     */
    static Paint top(int variant) {
        int[] ac = variant == 0 ? CYAN : variant == 1 ? new int[]{0x2a1404, 0x5a2c08, 0x8a4a10, 0xc07018, 0xf0a030, 0xffd070, 0xfff0c0}
                : VIOLET;
        Paint p = new Paint(6, 6, K);
        p.slab(0, 0, 6, 6, BRASS, 3.0, 1);
        p.slab(0.55, 0.55, 5.45, 5.45, IRON, 0.7, 1);
        p.grain(0.55, 0.55, 5.45, 5.45, 0.06, 321 + variant);
        int core = at(ac, 5.2), dim = at(ac, 3.0);
        double w = 0.5;
        if (variant == 2) {
            // the eye: an almond of two arcs, the iris ring and a bright pupil
            for (double t = 0; t <= 1.0001; t += 0.05) {
                double x = 1.0 + 4.0 * t, y = 0.95 * Math.sin(Math.PI * t);
                p.line(x, 3 - y, x + 0.2, 3 - y, w, core);
                p.line(x, 3 + y, x + 0.2, 3 + y, w, core);
            }
            p.ring(3, 3, 0.75, 1.05, dim);
            p.glowRect(2.75, 2.75, 3.25, 3.25, 0.01, at(ac, 6.0), 255);
        } else {
            // four arrowheads on the sides: pointing at the centre (in) or out at the frame (out)
            boolean in = variant == 0;
            for (int k = 0; k < 4; k++) {
                double ax = k == 0 ? 0 : k == 1 ? 0 : k == 2 ? 1 : -1, ay = k == 0 ? 1 : k == 1 ? -1 : 0;
                double far = in ? 2.4 : 2.5, near = in ? 0.85 : 0.95, half = in ? 1.25 : 1.15;
                double baseD = in ? far : near, tipD = in ? near : far;
                double bx = 3 + ax * baseD, by = 3 + ay * baseD, tx = 3 + ax * tipD, ty = 3 + ay * tipD;
                p.poly(new double[]{bx - ay * half, bx + ay * half, tx}, new double[]{by - ax * half, by + ax * half, ty}, core);
            }
            p.glowRect(2.75, 2.75, 3.25, 3.25, 0.01, dim, 255);
        }
        for (double[] c : new double[][]{{0.28, 0.28}, {5.72, 0.28}, {0.28, 5.72}, {5.72, 5.72}}) {
            p.rivet(c[0], c[1], 0.17, BRASS);
        }
        return p;
    }

    /** A 6x6 side panel of the technical block: a brass frame, dark iron, a socket nest in the middle. */
    static Paint side(int variant) {
        Paint p = new Paint(6, 6, K);
        p.slab(0, 0, 6, 6, BRASS, 3.0, 1);
        p.slab(0.5, 0.5, 5.5, 5.5, IRON, 1.5, 1);
        p.grain(0.5, 0.5, 5.5, 5.5, 0.07, 311 + variant);
        p.frame(0.85, 0.85, 5.15, 5.15, 0.1, at(IRON, 2.9));
        // nest: the socket (4x4) covers the middle, a brass rim shows around it
        p.disc(3, 3, 2.55, at(BRASS, 1.2));
        p.ring(3, 3, 2.25, 2.55, at(BRASS, 4.6));
        p.disc(3, 3, 2.0, at(IRON, 0.3));
        // indicators in the corners
        int[] led = variant == 0 ? CYAN : variant == 1 ? VIOLET : FROST;
        p.disc(1.05, 1.05, 0.22, at(led, 4.8));
        p.disc(4.95, 4.95, 0.22, at(led, 3.4));
        for (double[] c : new double[][]{{0.3, 0.3}, {5.7, 0.3}, {0.3, 5.7}, {5.7, 5.7}}) {
            p.rivet(c[0], c[1], 0.17, BRASS);
        }
        return p;
    }

    /** An end cap socket (4x4): a brass ring around a dark hole. */
    static Paint socket() {
        Paint p = new Paint(4, 4, K);
        p.slab(0, 0, 4, 4, BRASS, 2.8, 1);
        p.sphere(2, 2, 1.75, BRASS, 0.2, 0.95);
        p.disc(2, 2, 1.25, at(IRON, 0.2));
        p.disc(2, 2, 0.85, rgb(0x040506));
        p.ring(2, 2, 0.85, 0.98, at(IRON, 3.4));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            p.rivet(2 + Math.cos(a) * 1.5, 2 + Math.sin(a) * 1.5, 0.16, BRASS);
        }
        return p;
    }

    /** The vacuum bus funnel (12x12): a dark funnel with turbine blades and a teal hub. */
    static Paint funnel() {
        Paint p = new Paint(12, 12, K);
        // the turbine turns: one blade's step per cycle of 12 frames, blended into a light blur
        p.animFrames = 12;
        p.frameTime = 1;
        p.slab(0, 0, 12, 12, BRASS, 2.8, 1);
        p.disc(6, 6, 5.85, at(BRASS, 1.2));
        p.discFn(6, 6, 5.5, (u, v) -> {
            double d = Math.hypot(u - 6, v - 6) / 5.5;
            return Paint.ramp(IRON, 0.05 + 0.25 * d);
        });
        for (double[] c : new double[][]{{0.7, 0.7}, {11.3, 0.7}, {0.7, 11.3}, {11.3, 11.3}}) {
            p.rivet(c[0], c[1], 0.34, BRASS);
        }
        // the living detail: the nine copper blades, the rim over their tips and the hub with its cyan jewel on top
        p.animate(0.1, 0.1, 11.9, 11.9, (q, t) -> {
            for (int b = 0; b < 9; b++) {
                double a0 = (b + t) * 2 * Math.PI / 9;
                for (double r = 0.9; r < 5.2; r += 0.1) {
                    double a = a0 + r * 0.16;
                    double x = 6 + r * Math.cos(a), y = 6 + r * Math.sin(a);
                    q.disc(x, y, 0.4 + r * 0.04, Paint.ramp(COPPER, 0.25 + 0.55 * (r / 5.2) + 0.1 * Math.sin(a * 3)));
                }
            }
            q.ring(6, 6, 5.5, 5.85, at(BRASS, 4.0));
            q.sphere(6, 6, 1.0, BRASS, 0.3, 1.0);
            q.disc(6, 6, 0.5, at(CYAN, 4.8));
            q.glowRect(5.75, 5.75, 6.25, 6.25, 0.8, at(CYAN, 4.2), 90);
        });
        return p;
    }

    /** The end of the injector nozzle (6x6): a dark hole with a violet dot that blinks once every two seconds. */
    static Paint nozzle() {
        Paint p = new Paint(6, 6, K);
        p.animFrames = 20;
        p.frameTime = 2;
        p.slab(0, 0, 6, 6, BRASS, 2.6, 1);
        p.disc(3, 3, 2.7, at(BRASS, 1.0));
        p.sphere(3, 3, 2.6, BRASS, 0.3, 0.9);
        p.disc(3, 3, 1.85, rgb(0x050607));
        p.ring(3, 3, 1.85, 2.02, at(IRON, 3.0));
        // the living detail: the dot, dim most of the time, a short flash with a halo at the start of the cycle
        p.animate(1.2, 1.2, 4.8, 4.8, (q, t) -> {
            double f = t < 0.3 ? Math.sin(t / 0.3 * Math.PI) : 0;
            if (f > 0.05) {
                q.glowRect(2.7, 2.7, 3.3, 3.3, 0.6 + 0.6 * f, at(VIOLET, 5.0), (int) Math.round(150 * f));
            }
            q.disc(3, 3, 0.8, at(VIOLET, 1.4 + 2.0 * f));
            q.disc(2.85, 2.85, 0.36, at(VIOLET, 3.4 + 2.4 * f));
            if (f > 0.5) {
                q.sparkle(2.8, 2.8, argb(255, 0xFFFFFF));
            }
        });
        return p;
    }

    /**
     * The periscope monocle (10x10): a mount with a ring of runes and a clear lens. On the block the eye behind the glass is
     * drawn by the renderer (it looks around); the item's lens has it painted in the middle.
     */
    static Paint lens(boolean eye) {
        Paint p = new Paint(10, 10, K);
        p.slab(0, 0, 10, 10, BRASS, 2.4, 1);
        p.disc(5, 5, 4.9, at(BRASS, 1.2));
        p.sphere(5, 5, 4.8, BRASS, 0.3, 0.95);
        p.disc(5, 5, 3.95, at(IRON, 0.4));
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            if (K >= 4) {
                p.rune(5 + Math.cos(a) * 3.45 - 0.3, 5 + Math.sin(a) * 3.45 - 0.5, 1.0, i * 3 + 1, at(CYAN, 5.2), 0);
            } else {
                // two pixels are too few for a rune: glowing marks like a dial's, every third one bigger
                double r = i % 3 == 0 ? 0.45 : 0.26, x = 5 + Math.cos(a) * 3.35, y = 5 + Math.sin(a) * 3.35;
                p.glowRect(x - r, y - r, x + r, y + r, 0.01, at(CYAN, i % 3 == 0 ? 5.6 : 4.8), 255);
            }
        }
        p.disc(5, 5, 2.75, at(BRASS, 1.4));
        p.discFn(5, 5, 2.58, (u, v) -> {
            double dx = (u - 5) / 2.58, dy = (v - 5) / 2.58, d = Math.hypot(dx, dy);
            double lit = 0.5 + 0.5 * (-dx * 0.5 - dy * 0.7);
            return Paint.mix(rgb(0x1b3a52), rgb(0xcfe9ff), Math.max(0, Math.min(1, 0.25 + 0.55 * lit - d * 0.2)));
        });
        if (eye) {
            iris(p, 5, 5, 1.3);
            // the glint on the glass, mixed into the texels (a translucent texel would make the face translucent in 26.1)
            p.discFn(4, 3.9, 0.55, (u, v) -> Paint.mix(p.get((int) (u * 6.4), (int) (v * 6.4)), rgb(0xFFFFFF), 0.8));
        }
        return p;
    }

    /** The ender-eye iris: teal-green fibres around a dark slit pupil, darker at the rim (centre and radius in units). */
    static void iris(Paint p, double cx, double cy, double r) {
        p.discFn(cx, cy, r, (u, v) -> {
            double dx = (u - cx) / r, dy = (v - cy) / r, d = Math.hypot(dx, dy);
            if (Math.hypot(dx / 0.2, dy / 0.62) <= 1) {
                return rgb(0x050b0a);
            }
            double fibre = 0.5 + 0.5 * Math.sin(Math.atan2(dy, dx) * 9 + d * 5);
            int c = Paint.mix(rgb(0x145a4c), rgb(0x8fe8c4), Math.max(0, Math.min(1, 0.15 + 0.75 * d * (0.75 + 0.25 * fibre))));
            return d > 0.82 ? Paint.mix(c, rgb(0x0b332b), Math.min(1, (d - 0.82) / 0.18) * 0.85) : c;
        });
    }

    /** The periscope's eye for its renderer (entity texture 16x8 units): the iris on the left, the glass glint on the right. */
    static Paint eye() {
        Paint p = new Paint(16, 8, 4);
        iris(p, 4, 4, 3.9);
        p.discFn(12, 4, 3.9, (u, v) -> {
            double d = Math.hypot(u - 12, v - 4) / 3.9;
            int a = (int) Math.round(220 * Math.max(0, 1 - d * d));
            return a <= 0 ? 0 : argb(a, 0xFFFFFF);
        });
        return p;
    }

    /** Striped barrel steel. */
    static Paint steel() {
        Paint p = new Paint(4, 16, K);
        p.slab(0, 0, 4, 16, IRON, 4.0, 1);
        p.grain(0, 0, 4, 16, 0.05, 331);
        for (double y = 0.8; y < 16; y += 1.6) {
            p.rect(0, y, 4, y + 0.22, at(IRON, 1.6));
            p.rect(0, y + 0.22, 4, y + 0.36, at(IRON, 5.6));
        }
        return p;
    }

    static void textures() throws Exception {
        brassPlate().save(Forge2.tex("bus_plate"));
        for (int i = 0; i < 3; i++) {
            side(i).save(Forge2.tex("bus_side_" + i));
            top(i).save(Forge2.tex("bus_top_" + i));
        }
        socket().save(Forge2.tex("bus_socket"));
        funnel().save(Forge2.tex("bus_funnel"));
        nozzle().save(Forge2.tex("bus_nozzle"));
        lens(false).save(Forge2.tex("bus_lens"));
        lens(true).save(Forge2.tex("bus_lens_item"));
        eye().save(Forge2.entityTex("periscope_eye"));
        steel().save(Forge2.tex("bus_steel"));
        for (String old : new String[]{"bus_crystal_cyan", "bus_crystal_violet", "bus_crystal_ice", "bus_vial", "bus_gauge", "bus_lens_2", "bus_flange"}) {
            Files.deleteIfExists(Forge2.tex(old).toPath());
        }
    }

    // ---------------------------------------------------------------------------------------------- models
    static final double[] FULL_UV = {0, 0, 16, 16};

    static double[] sub(double[] uv, double fx, double fy) {
        double du = uv[2] - uv[0], dv = uv[3] - uv[1];
        return new double[]{uv[0] + du * fx, uv[1] + dv * fy, uv[2] - du * fx, uv[3] - dv * fy};
    }

    /**
     * An octagonal prism along Z: a "plus" of two boxes and four diagonal plates at the corners. east/west are side panels,
     * up/down the top and bottom, the ends as needed (the whole texture on the bounding square).
     */
    static void oct(List<Models.El> l, double x0, double y0, double z0, double x1, double y1, double z1, double c,
                    String side, String topBottom, String diag, String endNorth, String endSouth) {
        double w = x1 - x0, h = y1 - y0;
        Models.El a = Models.e(x0, y0 + c, z0, x1, y1 - c, z1);
        a.faces.put("east", Models.El.face("#" + side, FULL_UV));
        a.faces.put("west", Models.El.face("#" + side, FULL_UV));
        l.add(a);
        Models.El b = Models.e(x0 + c, y0, z0, x1 - c, y1, z1);
        b.faces.put("up", Models.El.face("#" + topBottom, FULL_UV));
        b.faces.put("down", Models.El.face("#" + topBottom, FULL_UV));
        l.add(b);
        if (endNorth != null) {
            Models.El n = Models.e(x0, y0 + c, z0 - 0.02, x1, y1 - c, z0);
            n.faces.put("north", Models.El.face("#" + endNorth, sub(FULL_UV, 0, c / h)));
            l.add(n);
            Models.El n2 = Models.e(x0 + c, y0, z0 - 0.01, x1 - c, y1, z0);
            n2.faces.put("north", Models.El.face("#" + endNorth, sub(FULL_UV, c / w, 0)));
            l.add(n2);
        }
        if (endSouth != null) {
            Models.El s = Models.e(x0, y0 + c, z1, x1, y1 - c, z1 + 0.02);
            s.faces.put("south", Models.El.face("#" + endSouth, sub(FULL_UV, 0, c / h)));
            l.add(s);
            Models.El s2 = Models.e(x0 + c, y0, z1, x1 - c, y1, z1 + 0.01);
            s2.faces.put("south", Models.El.face("#" + endSouth, sub(FULL_UV, c / w, 0)));
            l.add(s2);
        }
        if (diag != null) {
            // corner plates as thick as the whole cut corner (c/√2): they also close the corner triangles of the ends, where the "plus" of
            // two boxes left see-through holes
            Models.octCorners(l, x0, y0, x1, y1, z0, z1, c, c / Math.sqrt(2), "#" + diag, null,
                    endNorth != null ? "#" + diag : null, endSouth != null ? "#" + diag : null, 0);
        }
    }

    // The bus takes the front half of the block: the working node Z 0..3.2, the 6.4x6.4 technical block at Z 3.2..8 (as BusLinks in Java).
    static final double LO = 4.8, HI = 11.2, Z0 = 3.2, Z1 = 8, MID = 5.6;
    /** How far the middle of the body (and the side tubes) is shifted from the middle of the block towards the bus face. */
    static final double SHIFT = 8 - MID;

    /** The shared body: a 6.4x6.4x4.8 technical block, a rune plate roof with a star, forged bands. */
    static List<Models.El> common() {
        List<Models.El> l = new ArrayList<>();
        oct(l, LO, LO, Z0, HI, HI, Z1, 0.8, "side", "plate", "plate", "plate", "plate");
        // brass bands along the body edges (relief, like forged hoops)
        Pipes.ring(l, LO - 0.3, HI + 0.3, Z0 + 0.2, Z0 + 0.8, 0.9, "plate", 0.6);
        Pipes.ring(l, LO - 0.3, HI + 0.3, Z1 - 0.8, Z1 - 0.2, 0.9, "plate", 0.6);
        Models.El pad = Models.e(5.6, HI, Z0 + 1.0, 10.4, HI + 0.4, Z1 - 1.0).only("#plate", "east", "west", "north", "south");
        pad.faces.put("up", Models.El.face("#top", FULL_UV));
        l.add(pad.glow(6));
        return l;
    }

    /** A short socket (3.2x3.2 units) at a face of the body: toward-north, stands at z = zFace and sticks out 1.2 units to the north. */
    static Models.El stubBox(double zFace) {
        Models.El s = Models.e(6.4, 6.4, zFace - 1.2, 9.6, 9.6, zFace).only("#plate", "east", "west", "up", "down", "south");
        s.faces.put("north", Models.El.face("#socket", FULL_UV));
        return s;
    }

    /** Sockets on four sides (except the face and the star) for the item model: the side ones in the middle of the body. */
    static List<Models.El> itemStubs() {
        List<Models.El> l = new ArrayList<>();
        Models.El east = Models.e(HI, 6.4, MID - 1.6, HI + 1.2, 9.6, MID + 1.6).only("#plate", "north", "south", "up", "down", "west");
        east.faces.put("east", Models.El.face("#socket", FULL_UV));
        Models.El west = Models.e(LO - 1.2, 6.4, MID - 1.6, LO, 9.6, MID + 1.6).only("#plate", "north", "south", "up", "down", "east");
        west.faces.put("west", Models.El.face("#socket", FULL_UV));
        Models.El down = Models.e(6.4, LO - 1.2, MID - 1.6, 9.6, LO, MID + 1.6).only("#plate", "north", "south", "east", "west", "up");
        down.faces.put("down", Models.El.face("#socket", FULL_UV));
        Models.El back = Models.e(6.4, 6.4, Z1, 9.6, 9.6, Z1 + 1.2).only("#plate", "east", "west", "up", "down", "north");
        back.faces.put("south", Models.El.face("#socket", FULL_UV));
        l.add(east);
        l.add(west);
        l.add(down);
        l.add(back);
        return l;
    }

    static String[][] pipeTex() {
        return new String[][]{{"plate", "bus_plate"}, {"socket", "bus_socket"}, {"su", "pipe_glass_u"}, {"sv", "pipe_glass_v"},
                {"sd", "pipe_glass_d"}, {"band", "pipe_band"}};
    }

    /** Shifting the elements (together with the rotation centre). */
    static List<Models.El> shifted(List<Models.El> l, double dx, double dy, double dz) {
        for (Models.El e : l) {
            e.f[0] += dx;
            e.t[0] += dx;
            e.f[1] += dy;
            e.t[1] += dy;
            e.f[2] += dz;
            e.t[2] += dz;
            if (e.rotOrigin != null) {
                e.rot(e.rotAxis, e.rotAngle, e.rotOrigin[0] + dx, e.rotOrigin[1] + dy, e.rotOrigin[2] + dz);
            }
        }
        return l;
    }

    /** A mini tube "to the north" from the body face (z = zb) to the block edge: a rim at the body, glass, a rim at the edge. */
    static List<Models.El> arm(double zb) {
        List<Models.El> arm = new ArrayList<>();
        Pipes.tube(arm, 6, 10, 0, zb, 0.7, "sv", "su", "sd");
        Pipes.ring(arm, 5.4, 10.6, zb - 1.0, zb, 0.9, "band");
        Pipes.ring(arm, 5.4, 10.6, 0, 1.0, 0.9, "band");
        return arm;
    }

    /** Shifts of the side sockets in the "to the north" model: towards the middle of the body, wherever the bus faces. */
    static final String[] SHIFTS = {"px", "nx", "py", "ny"};

    static double[] shiftOf(String s) {
        return switch (s) {
            case "px" -> new double[]{SHIFT, 0};
            case "nx" -> new double[]{-SHIFT, 0};
            case "py" -> new double[]{0, SHIFT};
            default -> new double[]{0, -SHIFT};
        };
    }

    /** Shared mini tubes and sockets (a separate "to the north" model for each direction). */
    static void sharedModels() throws Exception {
        // the side ones: at the body face (z = LO from the block edge), shifted to the middle of the body; the back ones on the axis, from the back face (z = 8)
        for (String s : SHIFTS) {
            double[] d = shiftOf(s);
            Models.model("bus_stub_cross_" + s, false, "bus_plate", pipeTex(),
                    shifted(new ArrayList<>(List.of(stubBox(LO))), d[0], d[1], 0).toArray(new Models.El[0]));
            Models.model("bus_arm_cross_" + s, true, "bus_plate", pipeTex(), shifted(arm(LO), d[0], d[1], 0).toArray(new Models.El[0]));
        }
        Models.model("bus_stub_back", false, "bus_plate", pipeTex(), stubBox(16 - Z1));
        Models.model("bus_arm_back", true, "bus_plate", pipeTex(), arm(16 - Z1).toArray(new Models.El[0]));
        for (String old : new String[]{"bus_stub_cross", "bus_arm_cross"}) {
            Files.deleteIfExists(Forge2.RES.resolve("models/block/" + old + ".json"));
        }
    }

    static void bus(String name, int variant, List<Models.El> front, String[][] extraTex) throws Exception {
        List<Models.El> els = new ArrayList<>(common());
        els.addAll(front);
        List<String[]> tex = new ArrayList<>(List.of(
                new String[]{"plate", "bus_plate"}, new String[]{"side", "bus_side_" + variant},
                new String[]{"top", "bus_top_" + variant}, new String[]{"socket", "bus_socket"}));
        tex.addAll(List.of(extraTex));
        // the body and the working node without sockets: the block state adds the sockets and mini tubes
        Models.model(name + "_body", false, "bus_plate", tex.toArray(new String[0][]), els.toArray(new Models.El[0]));
        List<Models.El> item = new ArrayList<>(els);
        item.addAll(itemStubs());
        // in the inventory it is centred (the bus itself is in the front half of the block); the block model is already written
        shifted(item, 0, 0, 8 - (Z1 + 1.2) / 2);
        Models.model(name + "_item", false, "bus_plate", tex.toArray(new String[0][]), item.toArray(new Models.El[0]));
        Models.item(name, name + "_item");
        Forge2.addDisplay(name + "_item", 135, 1.0);
        blockstate(name);
    }

    static final String[][] ROT = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"},
            {"up", ", \"x\": 270"}, {"down", ", \"x\": 90"}};

    static String opposite(String d) {
        return switch (d) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            case "up" -> "down";
            default -> "up";
        };
    }

    /** Where the model's "up" (the side with the star) points for the given facing. */
    static String localUp(String facing) {
        return switch (facing) {
            case "up" -> "south";
            case "down" -> "north";
            default -> "up";
        };
    }

    /**
     * Multipart: the body by facing, and for each direction a socket or a mini tube (the back face and the sides have different lengths).
     * The side with the star (the model's top) has no socket without a pipe: there is the rune plate, and a connected pipe comes out of it.
     */
    static void blockstate(String name) throws Exception {
        List<String> parts = new ArrayList<>();
        for (String[] f : ROT) {
            parts.add("    {\"when\": {\"facing\": \"" + f[0] + "\"}, \"apply\": {\"model\": \"" + Forge2.NS + name + "_body\"" + f[1] + "}}");
        }
        for (String[] d : ROT) {
            String back = opposite(d[0]);
            boolean backStub = !localUp(back).equals(d[0]);
            for (String link : new String[]{"false", "true"}) {
                boolean arm = link.equals("true");
                String kind = arm ? "arm" : "stub";
                if (arm || backStub) {
                    parts.add("    {\"when\": {\"AND\": [{\"facing\": \"" + back + "\"}, {\"" + d[0] + "\": \"" + link + "\"}]}, \"apply\": {\"model\": \""
                            + Forge2.NS + "bus_" + kind + "_back\"" + d[1] + "}}");
                }
                // the side ones: their own model shifted towards the bus face for each pair (side, where the bus faces)
                for (String[] f : ROT) {
                    if (f[0].equals(d[0]) || f[0].equals(back) || (!arm && localUp(f[0]).equals(d[0]))) {
                        continue;
                    }
                    String[] r = crossRotation(d[0], f[0]);
                    parts.add("    {\"when\": {\"AND\": [{\"facing\": \"" + f[0] + "\"}, {\"" + d[0] + "\": \"" + link + "\"}]}, \"apply\": {\"model\": \""
                            + Forge2.NS + "bus_" + kind + "_cross_" + r[0] + "\"" + r[1] + "}}");
                }
            }
        }
        Forge2.write(Forge2.RES.resolve("blockstates/" + name + ".json"), "{\n  \"multipart\": [\n" + String.join(",\n", parts) + "\n  ]\n}\n");
    }

    static int[] vec(String d) {
        return switch (d) {
            case "north" -> new int[]{0, 0, -1};
            case "south" -> new int[]{0, 0, 1};
            case "east" -> new int[]{1, 0, 0};
            case "west" -> new int[]{-1, 0, 0};
            case "up" -> new int[]{0, 1, 0};
            default -> new int[]{0, -1, 0};
        };
    }

    /** Block state rotation: first x (90: y,z -> z,-y), then y (90: x,z -> -z,x), as for models with "x"/"y". */
    static int[] rotate(int[] v, int x, int y) {
        int[] r = v.clone();
        for (int i = 0; i < x / 90; i++) {
            r = new int[]{r[0], r[2], -r[1]};
        }
        for (int i = 0; i < y / 90; i++) {
            r = new int[]{-r[2], r[1], r[0]};
        }
        return r;
    }

    /**
     * The side socket model and the rotation for side d of a bus facing f: the model's "north" must go to d, and the model's shift
     * (towards the middle of the body) towards f. Returns {model suffix, rotation string}.
     */
    static String[] crossRotation(String d, String f) {
        for (String s : SHIFTS) {
            double[] sh = shiftOf(s);
            int[] sv = {(int) Math.signum(sh[0]), (int) Math.signum(sh[1]), 0};
            for (int x = 0; x < 360; x += 90) {
                for (int y = 0; y < 360; y += 90) {
                    if (Arrays.equals(rotate(vec("north"), x, y), vec(d)) && Arrays.equals(rotate(sv, x, y), vec(f))) {
                        return new String[]{s, (x != 0 ? ", \"x\": " + x : "") + (y != 0 ? ", \"y\": " + y : "")};
                    }
                }
            }
        }
        throw new IllegalStateException("no rotation for side " + d + " of facing " + f);
    }
    static void models() throws Exception {
        sharedModels();
        for (String old : new String[]{"vacuum_import_bus", "injector_export_bus", "periscope_storage_bus"}) {
            Files.deleteIfExists(Forge2.RES.resolve("models/block/" + old + ".json"));
        }

        // The working nodes are at Z 0..3.2, the whole bus is in the front half of the block.
        // --- Vacuum bus (import): a flat funnel with a turbine, narrowing towards the body
        List<Models.El> vac = new ArrayList<>();
        oct(vac, 2.5, 2.5, 0, 13.5, 13.5, 1.2, 2.4, "plate", "plate", "plate", "funnel", "plate");
        oct(vac, 3.6, 3.6, 1.2, 12.4, 12.4, 2.2, 1.9, "plate", "plate", "plate", null, "plate");
        oct(vac, 4.6, 4.6, 2.2, 11.4, 11.4, Z0, 1.4, "plate", "plate", "plate", null, null);
        bus("vacuum_import_bus", 0, vac, new String[][]{{"funnel", "bus_funnel"}});

        // --- Injector bus (export): a short nozzle barrel with rings
        List<Models.El> inj = new ArrayList<>();
        oct(inj, 5.2, 5.2, 0.4, 10.8, 10.8, Z0, 0.9, "steel", "steel", "steel", null, null);
        oct(inj, 4.6, 4.6, 1.0, 11.4, 11.4, 1.5, 1.2, "plate", "plate", "plate", "plate", "plate");
        oct(inj, 4.6, 4.6, 2.2, 11.4, 11.4, 2.7, 1.2, "plate", "plate", "plate", "plate", "plate");
        oct(inj, 5.2, 5.2, 0, 10.8, 10.8, 0.4, 0.9, "plate", "plate", "plate", "nozzle", "plate");
        bus("injector_export_bus", 1, inj, new String[][]{{"nozzle", "bus_nozzle"}, {"steel", "bus_steel"}});

        // --- Periscope bus: a monocle lens on a short neck
        List<Models.El> per = new ArrayList<>();
        oct(per, 3, 3, 0, 13, 13, 1.4, 2.2, "plate", "plate", "plate", "lens", "plate");
        oct(per, 5, 5, 1.4, 11, 11, Z0, 1.2, "plate", "plate", "plate", null, null);
        bus("periscope_storage_bus", 2, per, new String[][]{{"lens", "bus_lens"}});
        // the item has no renderer: its lens has the eye painted in the middle
        Files.writeString(Forge2.RES.resolve("models/item/periscope_storage_bus.json"), "{\n  \"parent\": \"golem_logix:block/periscope_storage_bus_item\",\n"
                + "  \"textures\": {\n    \"lens\": \"golem_logix:block/bus_lens_item\"\n  }\n}\n");
    }

    static void all() throws Exception {
        textures();
        models();
    }
}
