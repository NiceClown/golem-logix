import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The mod's three buses: a narrow device in the middle of the block. At the front the working feed or unload node (by purpose), behind it the technical
 * block (an 8x8x6 riveted copper/bronze & deepslate-iron box with a rune plate on top), on all other sides sockets for pipes. When a network node is next to it
 * (a pipe, another bus, a terminal), the socket stretches into a mini tube to the very edge of the block.
 * <ul>
 * <li>Vacuum (import): a forged iron funnel with copper turbine blades, a teal star/arrows on the roof;</li>
 * <li>Injector (export): a fluted iron barrel with copper cooling rings and bronze bevels, amber arrows on the roof;</li>
 * <li>Periscope: an aged bronze monocle bezel over an icy glass lens, a violet watching eye on the roof.</li>
 * </ul>
 * The models face NORTH (-Z); the block state rotates them (a multipart by facing and by the connection flags north..down).
 */
final class Buses {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, SLATE = Paint.SLATE_IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM;
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

    // ------------------------------------------------------------------------------------------- textures

    /** Forged deepslate-gray steel plate with bevels, subtle grain, and corner rivets (matches Wave 1/2 SLATE_IRON). */
    static Paint busIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.7, 1);
        p.grain(0, 0, 16, 16, 0.06, 303);
        p.frame(0, 0, 16, 16, 0.5, rgb(SLATE[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(SLATE[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1.0, r[1] + 1.0, rgb(SLATE[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(SLATE[5]));
        }
        return p;
    }

    /** Weathered riveted copper plating with dark rim, center seam, and subtle dark patina flecks. */
    static Paint busCopper() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.hash(i / 2, j / 2, 304) * 0.45 + Atlant.noise(i, j, 10, 305) * 0.55;
                int c = rgb(COPPER_W[Math.max(0, Math.min(4, (int) Math.round(n * 4)))]);
                p.px(i, j, c);
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(COPPER_W[0]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(COPPER_W[5]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(COPPER_W[0]));
        p.rect(0.5, 8.0, 15.5, 8.5, rgb(COPPER_W[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1.0, r[1] + 1.0, rgb(COPPER_W[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(COPPER_W[5]));
        }
        p.px(3, 4, rgb(PATINA[0]));
        p.px(p.w - 4, p.h - 5, rgb(PATINA[1]));
        return p;
    }

    /** Deep aged bronze plating with brushed streaks, shadow border, and antique brass corner rivets. */
    static Paint busBronze() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRONZE, 2.7, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.07, 307, true);
        p.grain(0, 0, 16, 16, 0.05, 308);
        p.frame(0, 0, 16, 16, 0.5, rgb(BRONZE[1]));
        p.frame(1.2, 1.2, 14.8, 14.8, 0.4, rgb(BRONZE[4]));
        for (double[] r : new double[][]{{1.6, 1.6}, {14.4, 1.6}, {1.6, 14.4}, {14.4, 14.4}}) {
            p.rivet(r[0], r[1], 0.35, BRASS);
        }
        return p;
    }

    /** Toned antique brass accent trim plate with deeper bronze chamfer. */
    static Paint brassPlate() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.4, 1);
        p.streaks(0.4, 0.4, 15.6, 15.6, 0.07, 301, true);
        p.grain(0, 0, 16, 16, 0.04, 302);
        p.frame(0, 0, 16, 16, 0.55, at(BRONZE, 1.4));
        p.frame(1.4, 1.4, 14.6, 14.6, 0.25, at(BRASS, 4.3));
        for (double t = 3.0; t < 13.5; t += 3.3) {
            for (double[] c : new double[][]{{t, 0.7}, {t, 15.3}, {0.7, t}, {15.3, t}}) {
                p.rivet(c[0], c[1], 0.3, BRASS);
            }
        }
        return p;
    }

    /**
     * Native 8x8 (= 16x16 px) roof of the technical block: dark SLATE_IRON panel in aged bronze/copper rim with brass corner rivets
     * whose sign tells from above what the bus does - inward cyan arrows, outward amber arrows, or a violet eye.
     */
    static Paint top(int variant) {
        int[] ac = variant == 0 ? CYAN : variant == 1 ? new int[]{0x2a1404, 0x5a2c08, 0x8a4a10, 0xc07018, 0xf0a030, 0xffd070, 0xfff0c0}
                : VIOLET;
        Paint p = new Paint(8, 8, K);
        p.slab(0, 0, 8, 8, BRONZE, 2.6, 1);
        p.frame(0, 0, 8, 8, 0.3, at(BRONZE, 1.2));
        p.slab(0.9, 0.9, 7.1, 7.1, SLATE, 0.7, 1);
        p.grain(0.9, 0.9, 7.1, 7.1, 0.06, 321 + variant);
        int core = at(ac, 5.2), dim = at(ac, 3.0);
        double w = 0.6;
        if (variant == 2) {
            for (double t = 0; t <= 1.0001; t += 0.05) {
                double x = 1.4 + 5.2 * t, y = 1.25 * Math.sin(Math.PI * t);
                p.line(x, 4 - y, x + 0.2, 4 - y, w, core);
                p.line(x, 4 + y, x + 0.2, 4 + y, w, core);
            }
            p.ring(4, 4, 0.95, 1.35, dim);
            p.glowRect(3.65, 3.65, 4.35, 4.35, 0.01, at(ac, 6.0), 255);
        } else {
            boolean in = variant == 0;
            for (int k = 0; k < 4; k++) {
                double ax = k == 0 ? 0 : k == 1 ? 0 : k == 2 ? 1 : -1, ay = k == 0 ? 1 : k == 1 ? -1 : 0;
                double far = in ? 3.1 : 3.2, near = in ? 1.1 : 1.2, half = in ? 1.55 : 1.45;
                double baseD = in ? far : near, tipD = in ? near : far;
                double bx = 4 + ax * baseD, by = 4 + ay * baseD, tx = 4 + ax * tipD, ty = 4 + ay * tipD;
                p.poly(new double[]{bx - ay * half, bx + ay * half, tx}, new double[]{by - ax * half, by + ax * half, ty}, core);
            }
            p.glowRect(3.65, 3.65, 4.35, 4.35, 0.01, dim, 255);
        }
        for (double[] c : new double[][]{{0.5, 0.5}, {7.5, 0.5}, {0.5, 7.5}, {7.5, 7.5}}) {
            p.rivet(c[0], c[1], 0.25, BRASS);
        }
        return p;
    }

    /** Native 8x8 (= 16x16 px) side panel of the technical block: aged bronze rim, forged SLATE_IRON body, dark recessed socket nest. */
    static Paint side(int variant) {
        Paint p = new Paint(8, 8, K);
        p.slab(0, 0, 8, 8, BRONZE, 2.5, 1);
        p.frame(0, 0, 8, 8, 0.3, at(BRONZE, 1.2));
        p.slab(0.8, 0.8, 7.2, 7.2, SLATE, 1.8, 1);
        p.grain(0.8, 0.8, 7.2, 7.2, 0.07, 311 + variant);
        p.frame(1.15, 1.15, 6.85, 6.85, 0.15, at(SLATE, 3.3));
        // nest: thin antique brass collar + dark gunmetal well (4x4 socket covers middle when present)
        p.disc(4, 4, 3.25, at(BRASS, 1.4));
        p.ring(4, 4, 2.85, 3.25, at(BRASS, 4.1));
        p.disc(4, 4, 2.55, at(SLATE, 0.4));
        int[] led = variant == 0 ? CYAN : variant == 1 ? VIOLET : FROST;
        Carve.sigil(p, 4, 4, 1.9, at(led, 2.3), at(led, 5.0));
        p.disc(1.4, 1.4, 0.3, at(led, 4.8));
        p.disc(6.6, 6.6, 0.3, at(led, 3.6));
        for (double[] c : new double[][]{{0.5, 0.5}, {7.5, 0.5}, {0.5, 7.5}, {7.5, 7.5}}) {
            p.rivet(c[0], c[1], 0.25, BRASS);
        }
        return p;
    }

    /** Native 8x8 (= 16x16 px) pipe socket end cap: forged SLATE_IRON flange with thin antique-brass rim & deep dark bore. */
    static Paint socket() {
        Paint p = new Paint(8, 8, K);
        p.slab(0, 0, 8, 8, BRONZE, 2.3, 1);
        p.disc(4, 4, 3.55, at(SLATE, 1.2));
        p.sphere(4, 4, 3.45, SLATE, 0.3, 0.9);
        p.disc(4, 4, 2.45, at(SLATE, 0.3));
        p.disc(4, 4, 1.65, rgb(0x05070a));
        p.ring(4, 4, 1.65, 1.95, at(BRASS, 3.8));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            p.rivet(4 + Math.cos(a) * 2.95, 4 + Math.sin(a) * 2.95, 0.25, BRASS);
        }
        return p;
    }

    /** Vacuum bus intake funnel (16x16 units = 32x32 px): forged SLATE_IRON rim, weathered copper turbine blades, cyan hub. */
    static Paint funnel() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 12;
        p.frameTime = 1;
        p.slab(0, 0, 16, 16, SLATE, 2.5, 1);
        p.disc(8, 8, 7.7, at(SLATE, 0.9));
        p.discFn(8, 8, 7.25, (u, v) -> {
            double d = Math.hypot(u - 8, v - 8) / 7.25;
            return Paint.ramp(SLATE, 0.08 + 0.28 * d);
        });
        for (double[] c : new double[][]{{1.0, 1.0}, {15.0, 1.0}, {1.0, 15.0}, {15.0, 15.0}}) {
            p.rivet(c[0], c[1], 0.45, BRASS);
        }
        p.animate(0.2, 0.2, 15.8, 15.8, (q, t) -> {
            for (int b = 0; b < 9; b++) {
                double a0 = (b + t) * 2 * Math.PI / 9;
                for (double r = 1.2; r < 6.85; r += 0.14) {
                    double a = a0 + r * 0.12;
                    double x = 8 + r * Math.cos(a), y = 8 + r * Math.sin(a);
                    q.disc(x, y, 0.5 + r * 0.055, Paint.ramp(COPPER_W, 0.12 + 0.42 * (r / 6.85) + 0.08 * Math.sin(a * 3)));
                }
            }
            q.ring(8, 8, 6.85, 7.65, at(SLATE, 1.0));
            q.ring(8, 8, 7.15, 7.55, at(BRONZE, 2.8));
            q.sphere(8, 8, 1.35, BRASS, 0.3, 0.95);
            q.disc(8, 8, 0.7, at(CYAN, 4.9));
            q.glowRect(7.65, 7.65, 8.35, 8.35, 1.0, at(CYAN, 4.2), 90);
        });
        return p;
    }

    /** Native 8x8 (= 16x16 px) injector muzzle: forged SLATE_IRON bore collar with brass inner lip and pulsing violet core. */
    static Paint nozzle() {
        Paint p = new Paint(8, 8, K);
        p.animFrames = 20;
        p.frameTime = 2;
        p.slab(0, 0, 8, 8, SLATE, 2.5, 1);
        p.disc(4, 4, 3.6, at(SLATE, 1.4));
        p.sphere(4, 4, 3.45, SLATE, 0.3, 0.9);
        p.disc(4, 4, 2.5, rgb(0x060709));
        p.ring(4, 4, 2.45, 2.75, at(BRASS, 3.8));
        p.animate(1.6, 1.6, 6.4, 6.4, (q, t) -> {
            double f = t < 0.3 ? Math.sin(t / 0.3 * Math.PI) : 0;
            if (f > 0.05) {
                q.glowRect(3.6, 3.6, 4.4, 4.4, 0.8 + 0.8 * f, at(VIOLET, 5.0), (int) Math.round(150 * f));
            }
            q.disc(4, 4, 1.05, at(VIOLET, 1.4 + 2.0 * f));
            q.disc(3.8, 3.8, 0.48, at(VIOLET, 3.4 + 2.4 * f));
            if (f > 0.5) {
                q.sparkle(3.75, 3.75, argb(255, 0xFFFFFF));
            }
        });
        return p;
    }

    /**
     * Periscope monocle (16x16 units = 32x32 px): scaled 1.6x from the 10x10 canvas so its glass radius (4.13 of 16 -> 2.58
     * world units) still matches PeriscopeEyeRenderer (IRIS=1.3, REACH=0.8) while upgrading the collar to bronze + slate iron.
     */
    static Paint lens(boolean eye) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRONZE, 2.4, 1);
        p.disc(8, 8, 7.84, at(BRONZE, 1.2));
        p.sphere(8, 8, 7.68, BRONZE, 0.25, 0.9);
        p.ring(8, 8, 6.32, 7.4, at(SLATE, 2.8));
        p.disc(8, 8, 6.32, at(SLATE, 0.45));
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            double r = i % 3 == 0 ? 0.56 : 0.34, x = 8 + Math.cos(a) * 5.45, y = 8 + Math.sin(a) * 5.45;
            p.glowRect(x - r, y - r, x + r, y + r, 0.01, at(CYAN, i % 3 == 0 ? 5.6 : 4.8), 255);
        }
        p.disc(8, 8, 4.55, at(BRASS, 1.6));
        p.ring(8, 8, 4.13, 4.55, at(BRASS, 4.2));
        p.discFn(8, 8, 4.13, (u, v) -> {
            double dx = (u - 8) / 4.13, dy = (v - 8) / 4.13, d = Math.hypot(dx, dy);
            double lit = 0.5 + 0.5 * (-dx * 0.5 - dy * 0.7);
            return Paint.mix(rgb(0x1b3a52), rgb(0xcfe9ff), Math.max(0, Math.min(1, 0.25 + 0.55 * lit - d * 0.2)));
        });
        if (eye) {
            iris(p, 8, 8, 2.08);
            p.discFn(6.4, 6.24, 0.88, (u, v) -> Paint.mix(p.get((int) (u * K), (int) (v * K)), rgb(0xFFFFFF), 0.8));
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

    /** Fluted deepslate-iron injector barrel steel (16x16 units = 32x32 px). */
    static Paint steel() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 3.2, 1);
        p.grain(0, 0, 16, 16, 0.06, 331);
        for (double y = 1.0; y < 16; y += 2.0) {
            p.rect(0, y, 16, y + 0.45, at(SLATE, 1.3));
            p.rect(0, y + 0.45, 16, y + 0.8, at(SLATE, 5.2));
        }
        return p;
    }

    static void textures() throws Exception {
        brassPlate().save(Forge2.tex("bus_plate"));
        busIron().save(Forge2.tex("bus_iron"));
        busCopper().save(Forge2.tex("bus_copper"));
        busBronze().save(Forge2.tex("bus_bronze"));
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
            Models.octCorners(l, x0, y0, x1, y1, z0, z1, c, c / Math.sqrt(2), "#" + diag, null,
                    endNorth != null ? "#" + diag : null, endSouth != null ? "#" + diag : null, 0);
        }
    }

    // The bus takes the front half of the block: the working node Z 0..3.2, the 6.4x6.4 technical block at Z 3.2..8 (as BusLinks in Java).
    static final double LO = 4.8, HI = 11.2, Z0 = 3.2, Z1 = 8, MID = 5.6;
    /** How far the middle of the body (and the side tubes) is shifted from the middle of the block towards the bus face. */
    static final double SHIFT = 8 - MID;

    /** The shared body: a 6.4x6.4x4.8 technical block, a rune plate roof with antique brass trim, forged deepslate-iron bands. */
    static List<Models.El> common() {
        List<Models.El> l = new ArrayList<>();
        oct(l, LO, LO, Z0, HI, HI, Z1, 0.8, "side", "copper", "bronze", "iron", "iron");
        Pipes.ring(l, LO - 0.3, HI + 0.3, Z0 + 0.2, Z0 + 0.8, 0.9, "iron", 0.6);
        Pipes.ring(l, LO - 0.3, HI + 0.3, Z1 - 0.8, Z1 - 0.2, 0.9, "iron", 0.6);
        Models.El pad = Models.e(5.6, HI, Z0 + 1.0, 10.4, HI + 0.4, Z1 - 1.0).only("#plate", "east", "west", "north", "south");
        pad.faces.put("up", Models.El.face("#top", FULL_UV));
        l.add(pad.glow(6));
        return l;
    }

    /** A short socket (3.2x3.2 units) at a face of the body: toward-north, stands at z = zFace and sticks out 1.2 units to the north. */
    static Models.El stubBox(double zFace) {
        Models.El s = Models.e(6.4, 6.4, zFace - 1.2, 9.6, 9.6, zFace).only("#iron", "east", "west", "up", "down", "south");
        s.faces.put("north", Models.El.face("#socket", FULL_UV));
        return s;
    }

    /** Sockets on four sides (except the face and the star) for the item model: the side ones in the middle of the body. */
    static List<Models.El> itemStubs() {
        List<Models.El> l = new ArrayList<>();
        Models.El east = Models.e(HI, 6.4, MID - 1.6, HI + 1.2, 9.6, MID + 1.6).only("#iron", "north", "south", "up", "down", "west");
        east.faces.put("east", Models.El.face("#socket", FULL_UV));
        Models.El west = Models.e(LO - 1.2, 6.4, MID - 1.6, LO, 9.6, MID + 1.6).only("#iron", "north", "south", "up", "down", "east");
        west.faces.put("west", Models.El.face("#socket", FULL_UV));
        Models.El down = Models.e(6.4, LO - 1.2, MID - 1.6, 9.6, LO, MID + 1.6).only("#iron", "north", "south", "east", "west", "up");
        down.faces.put("down", Models.El.face("#socket", FULL_UV));
        Models.El back = Models.e(6.4, 6.4, Z1, 9.6, 9.6, Z1 + 1.2).only("#iron", "east", "west", "up", "down", "north");
        back.faces.put("south", Models.El.face("#socket", FULL_UV));
        l.add(east);
        l.add(west);
        l.add(down);
        l.add(back);
        return l;
    }

    static String[][] pipeTex() {
        return new String[][]{{"plate", "bus_plate"}, {"iron", "bus_iron"}, {"copper", "bus_copper"}, {"bronze", "bus_bronze"},
                {"socket", "bus_socket"}, {"su", "pipe_glass_u"}, {"sv", "pipe_glass_v"},
                {"sd", "pipe_glass_d"}, {"band", "bus_iron"}};
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

    /** A mini tube "to the north" from the body face (z = zb) to the block edge: an iron rim at the body, glass, an iron rim at the edge. */
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
        for (String s : SHIFTS) {
            double[] d = shiftOf(s);
            Models.model("bus_stub_cross_" + s, false, "bus_iron", pipeTex(),
                    shifted(new ArrayList<>(List.of(stubBox(LO))), d[0], d[1], 0).toArray(new Models.El[0]));
            Models.model("bus_arm_cross_" + s, true, "bus_iron", pipeTex(), shifted(arm(LO), d[0], d[1], 0).toArray(new Models.El[0]));
        }
        Models.model("bus_stub_back", false, "bus_iron", pipeTex(), stubBox(16 - Z1));
        Models.model("bus_arm_back", true, "bus_iron", pipeTex(), arm(16 - Z1).toArray(new Models.El[0]));
        for (String old : new String[]{"bus_stub_cross", "bus_arm_cross"}) {
            Files.deleteIfExists(Forge2.RES.resolve("models/block/" + old + ".json"));
        }
    }

    static void bus(String name, int variant, List<Models.El> front, String[][] extraTex) throws Exception {
        List<Models.El> els = new ArrayList<>(common());
        els.addAll(front);
        List<String[]> tex = new ArrayList<>(List.of(
                new String[]{"plate", "bus_plate"}, new String[]{"iron", "bus_iron"},
                new String[]{"copper", "bus_copper"}, new String[]{"bronze", "bus_bronze"},
                new String[]{"side", "bus_side_" + variant}, new String[]{"top", "bus_top_" + variant},
                new String[]{"socket", "bus_socket"}));
        tex.addAll(List.of(extraTex));
        Models.model(name + "_body", false, "bus_copper", tex.toArray(new String[0][]), els.toArray(new Models.El[0]));
        List<Models.El> item = new ArrayList<>(els);
        item.addAll(itemStubs());
        shifted(item, 0, 0, 8 - (Z1 + 1.2) / 2);
        Models.model(name + "_item", false, "bus_copper", tex.toArray(new String[0][]), item.toArray(new Models.El[0]));
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
        // --- Vacuum bus (import): forged iron bell + copper/bronze taper towards the body
        List<Models.El> vac = new ArrayList<>();
        oct(vac, 2.5, 2.5, 0, 13.5, 13.5, 1.2, 2.4, "iron", "iron", "iron", "funnel", "iron");
        oct(vac, 3.6, 3.6, 1.2, 12.4, 12.4, 2.2, 1.9, "copper", "copper", "bronze", null, "bronze");
        oct(vac, 4.6, 4.6, 2.2, 11.4, 11.4, Z0, 1.4, "bronze", "bronze", "bronze", null, null);
        bus("vacuum_import_bus", 0, vac, new String[][]{{"funnel", "bus_funnel"}});

        // --- Injector bus (export): fluted deepslate-steel barrel with copper/bronze cooling rings & iron muzzle
        List<Models.El> inj = new ArrayList<>();
        oct(inj, 5.2, 5.2, 0.4, 10.8, 10.8, Z0, 0.9, "steel", "steel", "steel", null, null);
        oct(inj, 4.6, 4.6, 1.0, 11.4, 11.4, 1.5, 1.2, "copper", "copper", "bronze", "copper", "copper");
        oct(inj, 4.6, 4.6, 2.2, 11.4, 11.4, 2.7, 1.2, "copper", "copper", "bronze", "copper", "copper");
        oct(inj, 5.2, 5.2, 0, 10.8, 10.8, 0.4, 0.9, "iron", "iron", "iron", "nozzle", "iron");
        bus("injector_export_bus", 1, inj, new String[][]{{"nozzle", "bus_nozzle"}, {"steel", "bus_steel"}});

        // --- Periscope bus: bronze/iron monocle housing over an icy lens on a riveted copper neck
        List<Models.El> per = new ArrayList<>();
        oct(per, 3, 3, 0, 13, 13, 1.4, 2.2, "bronze", "bronze", "iron", "lens", "iron");
        oct(per, 5, 5, 1.4, 11, 11, Z0, 1.2, "copper", "copper", "iron", null, null);
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
