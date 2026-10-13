import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The second generation of Golem Logix graphics: the 2x2 cabinet, buses and pipes. Density is 4 texels per unit (64 pixels per block).
 * Design code: bronze and brass with rivets, dark iron, a teal glow, purple runes, frost.
 */
public final class Forge2 {
    static final Path RES = Path.of("src/main/resources/assets/golem_logix");
    static final String NS = "golem_logix:block/";
    static final int K = 4;
    static final Path PREVIEW = Path.of("build/preview");

    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, BLUE = Paint.BLUE, CREAM = Paint.CREAM;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    static File tex(String name) {
        return RES.resolve("textures/block/" + name + ".png").toFile();
    }

    /** Textures drawn only by block entity renderers: outside the block atlas (otherwise extra mip warnings and atlas space). */
    static File entityTex(String name) {
        return RES.resolve("textures/entity/" + name + ".png").toFile();
    }

    static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }

    // ============================================================================================ cabinet
    // front layout (units, y down): beam 0..4, plinth 28..32, posts and the openings between them
    static final double P0 = 2.5, M0 = 2.5, M1 = 5.5, P1 = 7.5, D0 = 7.5, D1 = 23.5, P2 = 25.5, G0 = 25.5, G1 = 29.5, P3 = 29.5;
    static final double BEAM = 4, PLINTH = 28;

    /** A wooden/bronze beam: a slab with a lengthwise grain. */
    static void beam(Paint p, double x0, double y0, double x1, double y1, double mid, boolean horizontal, long seed) {
        p.slab(x0, y0, x1, y1, BRONZE, mid, 1);
        p.streaks(x0 + 0.25, y0 + 0.25, x1 - 0.25, y1 - 0.25, 0.09, seed, horizontal);
        p.grain(x0, y0, x1, y1, 0.04, seed + 1);
    }

    /** A brass block with a bolt. */
    static void brassBlock(Paint p, double x0, double y0, double x1, double y1, long seed) {
        p.slab(x0, y0, x1, y1, BRASS, 3.2, 2);
        p.grain(x0, y0, x1, y1, 0.05, seed);
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, r = Math.min(x1 - x0, y1 - y0) * 0.30;
        p.disc(cx, cy, r + 0.18, at(BRASS, 1.0));
        p.screw(cx, cy, r, BRASS, 0.5);
        for (double[] c : new double[][]{{x0 + 0.5, y0 + 0.5}, {x1 - 0.5, y0 + 0.5}, {x0 + 0.5, y1 - 0.5}, {x1 - 0.5, y1 - 0.5}}) {
            p.rivet(c[0], c[1], 0.28, BRASS);
        }
    }

    /** A chain of rhombuses: a carved frieze. */
    static void frieze(Paint p, double x0, double x1, double y0, double y1, double period) {
        double cy = (y0 + y1) / 2, hh = (y1 - y0) / 2 - 0.15;
        for (double cx = x0 + period / 2; cx < x1; cx += period) {
            double[] xs = {cx - period * 0.42, cx, cx + period * 0.42, cx};
            double[] ys = {cy, cy - hh, cy, cy + hh};
            p.poly(xs, ys, at(BRONZE, 1.3));
            double[] xi = {cx - period * 0.28, cx, cx + period * 0.28, cx};
            double[] yi = {cy, cy - hh * 0.62, cy, cy + hh * 0.62};
            p.poly(xi, yi, at(BRONZE, 3.6));
            p.rect(cx - 0.13, cy - 0.13, cx + 0.13, cy + 0.13, at(BRASS, 4.4));
            p.line(cx + period * 0.42, cy, cx + period * 0.58, cy, 0.3, at(BRONZE, 1.3));
        }
    }

    static Paint elevation() {
        Paint p = new Paint(32, 32, K);

        // --- the top beam with a carved frieze, brass corner blocks and a rune plate
        beam(p, 0, 0, 32, BEAM, 2.9, true, 11);
        p.slab(3.5, 0.8, 28.5, 3.2, BRONZE, 1.6, 1);
        frieze(p, 3.8, 28.2, 0.8, 3.2, 1.9);
        brassBlock(p, 0, 0, 3.5, BEAM, 12);
        brassBlock(p, 28.5, 0, 32, BEAM, 13);
        // rune plate in the middle
        p.slab(12.2, 0.3, 19.8, 3.7, BRASS, 3.0, 1);
        p.slab(13.0, 0.9, 19.0, 3.1, IRON, 1.0, 1);
        p.glowRect(13.4, 1.0, 18.6, 3.0, 0.7, at(VIOLET, 3.5), 60);
        p.rune(14.0, 1.2, 1.7, 16, at(VIOLET, 5.2), at(VIOLET, 3.0));
        p.rune(16.6, 1.2, 1.7, 0, at(VIOLET, 5.2), at(VIOLET, 3.0));
        p.rivet(12.7, 0.75, 0.2, BRASS);
        p.rivet(19.3, 0.75, 0.2, BRASS);
        p.rivet(12.7, 3.25, 0.2, BRASS);
        p.rivet(19.3, 3.25, 0.2, BRASS);

        // --- plinth
        beam(p, 0, PLINTH, 32, 32, 2.6, true, 21);
        p.slab(3.5, 28.9, 28.5, 31.1, BRONZE, 1.5, 1);
        frieze(p, 3.8, 28.2, 28.9, 31.1, 1.9);
        brassBlock(p, 0, PLINTH, 3.5, 32, 22);
        brassBlock(p, 28.5, PLINTH, 32, 32, 23);
        p.slab(13.6, 28.3, 18.4, 31.7, BRASS, 3.0, 1);
        p.slab(14.2, 28.9, 17.8, 31.1, IRON, 1.0, 1);
        p.glowRect(14.4, 29.0, 17.6, 31.0, 0.6, at(VIOLET, 3.5), 55);
        p.rune(15.2, 29.1, 1.9, 19, at(VIOLET, 5.2), at(VIOLET, 3.0));

        // --- posts: bronze, with brass bands and rivets
        // the posts are where the model boxes are (cabinetGeometry): 0..P0, M1..P1, D1..P2, P3..32; otherwise a post face took
        // a transparent area of the picture and the sky showed through the cabinet
        double[][] posts = {{0, P0}, {M1, P1}, {D1, P2}, {P3, 32}};
        long seed = 31;
        for (double[] ps : posts) {
            beam(p, ps[0], BEAM, ps[1], PLINTH, 2.9, false, seed++);
            for (double by : new double[]{7.5, 24.0}) {
                p.slab(ps[0], by, ps[1], by + 1.3, BRASS, 3.3, 1);
                p.rivet(ps[0] + 0.55, by + 0.65, 0.2, BRASS);
                p.rivet(ps[1] - 0.55, by + 0.65, 0.2, BRASS);
            }
        }
        // the inner posts get a brass edging of the opening
        for (double x : new double[]{M1, D0, D1, G0}) {
            p.rect(x - 0.12, BEAM, x + 0.12, PLINTH, at(BRASS, 4.2));
        }
        // frost on the cornices and corners
        p.frostLine(0, 32, 0, 0.55, 5);
        p.frostLine(0.3, 3.2, 28, 0.5, 6);
        p.frostLine(28.8, 31.8, 28, 0.5, 7);
        p.icicle(1.2, 4.0, 1.4, 0.7);
        p.icicle(30.6, 4.0, 1.7, 0.7);
        p.icicle(30.4, 32 - 0.01, 0, 0);
        return p;
    }

    /** Cuts the front into four pictures per cell and writes them. */
    static void saveElevation(Paint p) throws Exception {
        // cells: (column 0 = left), row 0 = bottom. The canvas has y down, so the bottom row is the bottom half of the picture.
        p.crop(0, 16, 16, 16).save(tex("cab_front_bl"));
        p.crop(16, 16, 16, 16).save(tex("cab_front_br"));
        p.crop(0, 0, 16, 16).save(tex("cab_front_tl"));
        p.crop(16, 0, 16, 16).save(tex("cab_front_tr"));
    }

    /** A cabinet side wall of 16 (deep) x 32 (high) units. left: sockets and gauges on the left, one socket on the right. */
    static Paint sideWall(boolean left, long seed) {
        Paint p = new Paint(16, 32, K);
        beam(p, 0, 0, 16, 32, 2.8, false, seed);
        // brass bands at the level of the front bands and the beam ends
        p.slab(0, 0, 16, 4, BRONZE, 2.3, 1);
        p.slab(0, 28, 16, 32, BRONZE, 2.3, 1);
        for (double by : new double[]{7.5, 24.0}) {
            p.slab(0, by, 16, by + 1.3, BRASS, 3.3, 1);
            for (double bx : new double[]{1.0, 5.4, 10.6, 15.0}) {
                p.rivet(bx, by + 0.65, 0.22, BRASS);
            }
        }
        if (left) {
            socket(p, 8, 10.6);
            p.gauge(8, 15.3, 1.7, 70, argb(255, 0xb02020));
            p.gauge(8, 19.6, 1.7, 118, argb(255, 0xb02020));
            socket(p, 8, 25.8);
        } else {
            socket(p, 8, 16);
            p.slab(2.5, 10, 13.5, 13, IRON, 1.6, 1);
            p.rune(6.3, 10.3, 2.3, 2, at(CYAN, 5), at(CYAN, 3));
            p.rune(8.9, 10.3, 2.3, 8, at(CYAN, 5), at(CYAN, 3));
            p.slab(2.5, 19, 13.5, 22, IRON, 1.6, 1);
            for (int i = 0; i < 4; i++) {
                p.rect(3.3 + i * 2.4, 19.7, 4.7 + i * 2.4, 21.3, i < 3 ? at(CYAN, 4 - i * 0.5) : at(IRON, 3));
            }
        }
        p.frostLine(0, 16, 0, 0.5, seed + 3);
        return p;
    }

    /** A brass cable socket with a dark hole. */
    static void socket(Paint p, double cx, double cy) {
        p.slab(cx - 3.2, cy - 2.2, cx + 3.2, cy + 2.2, BRASS, 3.2, 1);
        p.sphere(cx, cy, 1.55, BRASS, 0.2, 0.95);
        p.disc(cx, cy, 1.1, at(IRON, 0.3));
        p.disc(cx, cy, 0.7, rgb(0x050607));
        p.rivet(cx - 2.5, cy - 1.5, 0.24, BRASS);
        p.rivet(cx + 2.5, cy - 1.5, 0.24, BRASS);
        p.rivet(cx - 2.5, cy + 1.5, 0.24, BRASS);
        p.rivet(cx + 2.5, cy + 1.5, 0.24, BRASS);
    }

    /** Roof: bronze sheets, a brass edging, a vent grate and frost. */
    static Paint roof() {
        Paint p = new Paint(16, 16, K);
        beam(p, 0, 0, 16, 16, 2.7, true, 61);
        p.slab(0.8, 0.8, 15.2, 15.2, BRONZE, 2.1, 1);
        p.slab(5, 5, 11, 11, IRON, 1.2, 1);
        for (int i = 0; i < 5; i++) {
            p.rect(5.6, 5.7 + i * 1.0, 10.4, 6.2 + i * 1.0, at(IRON, 0.3));
        }
        for (double[] c : new double[][]{{1.6, 1.6}, {14.4, 1.6}, {1.6, 14.4}, {14.4, 14.4}}) {
            p.screw(c[0], c[1], 0.6, BRASS, 0.7);
        }
        p.frostLine(0, 16, 0, 0.8, 62);
        return p;
    }

    /** Back: bronze panels with rivets (a seamless tile). */
    static Paint backPanel() {
        Paint p = new Paint(16, 16, K);
        beam(p, 0, 0, 16, 16, 2.5, false, 71);
        p.slab(1, 1, 15, 15, BRONZE, 2.2, 1);
        for (double[] c : new double[][]{{2.2, 2.2}, {13.8, 2.2}, {2.2, 13.8}, {13.8, 13.8}}) {
            p.rivet(c[0], c[1], 0.4, BRASS);
        }
        p.slab(5.5, 6, 10.5, 10, IRON, 1.4, 1);
        p.rune(7.4, 6.6, 2.8, 12, at(CYAN, 4.8), at(CYAN, 2.5));
        return p;
    }

    /** The back wall of the display: dark iron, vertical seams, brass pipelines and teal pulses. */
    static Paint displayWall() {
        Paint p = new Paint(16, 24, K);
        p.slab(0, 0, 16, 24, IRON, 1.5, 1);
        p.grain(0, 0, 16, 24, 0.06, 81);
        for (double x : new double[]{4, 8, 12}) {
            p.rect(x - 0.1, 0.5, x + 0.1, 23.5, at(IRON, 0.2));
            p.rect(x + 0.1, 0.5, x + 0.25, 23.5, at(IRON, 2.6));
        }
        // a brass pipeline along the top and down the right side, with teal pulses
        p.slab(0.5, 1.2, 15.5, 2.4, BRASS, 2.4, 1);
        for (double x : new double[]{3, 8.5, 13}) {
            p.rect(x, 1.55, x + 1.2, 2.05, at(CYAN, 4.6));
            p.glowRect(x, 1.55, x + 1.2, 2.05, 0.5, at(CYAN, 3.5), 70);
        }
        p.slab(14.2, 2.4, 15.4, 23.2, BRASS, 2.0, 1);
        for (double y : new double[]{6, 12, 18}) {
            p.slab(13.9, y, 15.7, y + 0.9, BRASS, 3.4, 1);
        }
        // rows: brass marks on every shelf and Roman numerals
        String[] numerals = {"I", "II", "III", "IV", "V"};
        for (int r = 0; r < 5; r++) {
            double y = 4.8 * (r + 1) - 0.1;
            p.rect(0.5, y, 13.6, y + 0.18, at(BRASS, 2.0));
        }
        p.rivet(1.0, 3.0, 0.3, BRASS);
        p.rivet(1.0, 21.0, 0.3, BRASS);
        return p;
    }

    /** A shelf board. */
    static Paint shelfBoard() {
        Paint p = new Paint(16, 4, K);
        beam(p, 0, 0, 16, 4, 3.0, true, 91);
        p.rect(0, 0, 16, 0.3, at(BRASS, 4.4));
        for (double x = 1.5; x < 16; x += 4) {
            p.rivet(x, 2.0, 0.22, BRASS);
        }
        return p;
    }

    /** The grate on the left: dark wires in a brass frame, the holes are transparent. */
    static Paint meshPanel() {
        Paint p = new Paint(4, 24, K);
        // a canvas 4 units wide is stretched over a 3-unit opening (a multiple of 16 pixels for the mip levels)
        p.mesh(0, 0, 4, 24, 1.4, 0.45, at(IRON, 2.4), at(IRON, 4.4), 0);
        p.rect(0, 0, 4, 0.45, at(BRASS, 3.4));
        p.rect(0, 23.55, 4, 24, at(BRASS, 2.4));
        p.rect(0, 0, 0.4, 24, at(BRASS, 2.8));
        p.rect(3.6, 0, 4, 24, at(BRASS, 2.0));
        return p;
    }

    static Paint glass(double w, double h, int alpha, int tint, long seed) {
        Paint p = new Paint(w, h, K);
        p.tint(0, 0, w, h, argb(alpha, tint));
        // diagonal highlights
        double s = Math.max(w, h);
        for (double off : new double[]{0.0, 5.0, 11.0}) {
            p.line(0.4 + off * w / 16, h * 0.30 + off, w * 0.9 + off * w / 16, 0.4 + off - 2, 0.28, argb(70, 0xFFFFFF));
        }
        p.line(w * 0.12, h - 0.4, w * 0.12, h - 3.4, 0.25, argb(90, 0xFFFFFF));
        p.rect(0, 0, w, 0.15, argb(110, 0xE0F4FF));
        p.rect(0, h - 0.15, w, h, argb(110, 0xE0F4FF));
        p.rect(0, 0, 0.15, h, argb(110, 0xE0F4FF));
        p.rect(w - 0.15, 0, w, h, argb(110, 0xE0F4FF));
        return p;
    }

    /** The back panel of the right door: a big gauge, valves and pipes. */
    static Paint machinePanel() {
        Paint p = new Paint(4, 24, K);
        p.slab(0, 0, 4, 24, IRON, 1.7, 1);
        p.grain(0, 0, 4, 24, 0.06, 101);
        p.gauge(2, 5.2, 1.5, 50, argb(255, 0xb02020));
        p.gauge(2, 9.4, 1.2, 130, argb(255, 0xb02020));
        p.slab(0.5, 12.5, 3.5, 14, BRASS, 2.8, 1);
        p.rect(1.7, 14, 2.3, 22.8, at(BRASS, 2.2));
        for (double y : new double[]{17, 20.5}) {
            p.slab(0.8, y, 3.2, y + 0.9, BRASS, 3.4, 1);
        }
        p.rune(1.3, 15.4, 2.0, 12, at(CYAN, 4.8), at(CYAN, 2.5));
        return p;
    }

    /** A glass flask with teal liquid (side view). */
    static Paint vial() {
        Paint p = new Paint(4, 20, K);
        // a flask 1.4 units wide in the middle of a 4-unit canvas (the model faces take it through uv)
        p.fill(1.3, 0, 2.7, 20, (u, v) -> {
            double t = (u - 2.0) / 0.7;
            double edge = Math.abs(t);
            int c = Paint.ramp(CYAN, 0.62 + 0.25 * (1 - edge) - 0.1 * edge);
            return edge > 0.85 ? Paint.ramp(CYAN, 0.25) : c;
        });
        p.rect(1.58, 0.4, 1.8, 19.6, argb(210, 0xE2FFFE));
        p.rect(1.3, 0, 2.7, 0.7, at(BRASS, 3.0));
        p.rect(1.3, 19.3, 2.7, 20, at(BRASS, 2.2));
        for (double y = 3; y < 19; y += 3.4) {
            p.rect(1.3, y, 2.7, y + 0.28, at(COPPER, 3.0));
        }
        return p;
    }

    static Paint iceTex() {
        Paint p = new Paint(4, 4, K);
        p.fill(0, 0, 4, 4, (u, v) -> argb(205, Paint.ramp(FROST, 0.45 + 0.4 * (1 - Math.abs(u - 2) / 2) - 0.1 * v / 4) & 0xFFFFFF));
        p.rect(0.6, 0, 1.1, 4, argb(235, 0xFFFFFF));
        return p;
    }

    static Paint flat(int c) {
        Paint p = new Paint(4, 4, K);
        p.rect(0, 0, 4, 4, rgb(c));
        return p;
    }

    /** A general-purpose bronze tile (inner faces of the posts). */
    static Paint beamTile() {
        Paint p = new Paint(16, 16, K);
        beam(p, 0, 0, 16, 16, 2.4, false, 121);
        return p;
    }

    static void cabinetTextures() throws Exception {
        Paint e = elevation();
        saveElevation(e);
        e.save(PREVIEW.resolve("cab_elevation.png").toFile());
        sideWall(true, 41).save(tex("cab_side_l"));
        sideWall(false, 51).save(tex("cab_side_r"));
        roof().save(tex("cab_top"));
        Paint back = CabBack.elevation();
        CabBack.save(back);
        back.save(PREVIEW.resolve("cab_back_elevation.png").toFile());
        displayWall().save(tex("cab_wall"));
        meshPanel().save(tex("cab_mesh"));
        glass(16, 24, 26, 0x9fd8ff, 1).save(tex("cab_glass_d"));
        glass(4, 24, 52, 0x8fe8d8, 2).save(tex("cab_glass_g"));
        machinePanel().save(tex("cab_machine"));
        vial().save(tex("cab_vial"));
        iceTex().save(tex("cab_ice"));
        flat(0x07090b).save(tex("cab_dark"));
        beamTile().save(tex("cab_beam"));
    }

    static Paint brassTile() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 131, true);
        p.grain(0, 0, 16, 16, 0.04, 132);
        return p;
    }

    // ---------------------------------------------------------------- cabinet model
    /** A doorway in the partition: height above the shelf and depth (from the glass to z = DOOR_Z1); see DriveBayRenderer. */
    static final double DOOR_H = 3.4, DOOR_Z1 = 6.0;

    /** Body depth at the back: the last 0.6 units are taken by the relief of the back (screens, coil, pipes). */
    static final double BACKZ = 15.4;

    /** withGlass = the glass of the display and the right door (only for the icon: in the world these are DriveBayRenderer's swinging doors). */
    static BigModel cabinetGeometry(boolean withGlass) {
        BigModel m = new BigModel();
        BigModel.Face elev = BigModel.Face.of("elev"), beamF = BigModel.Face.of("beam"), backF = BigModel.Face.of("back"),
                topF = BigModel.Face.of("top"), brassF = BigModel.Face.of("brass"), darkF = BigModel.Face.of("dark");
        BigModel.Face sideL = BigModel.Face.map("side_l", (x, y, z) -> new double[]{16 - z, (32 - y) / 2});
        BigModel.Face sideR = BigModel.Face.map("side_r", (x, y, z) -> new double[]{z, (32 - y) / 2});
        BigModel.Face wall = BigModel.Face.map("wall", (x, y, z) -> new double[]{x - D0, (28 - y) / 24 * 16});
        BigModel.Face machine = BigModel.Face.map("machine", (x, y, z) -> new double[]{(x - G0) / 4 * 16, (28 - y) / 24 * 16});
        BigModel.Face glassD = BigModel.Face.map("glass_d", (x, y, z) -> new double[]{x - D0, (28 - y) / 24 * 16});
        BigModel.Face glassG = BigModel.Face.map("glass_g", (x, y, z) -> new double[]{(x - G0) / 4 * 16, (28 - y) / 24 * 16});
        BigModel.Face mesh = BigModel.Face.map("mesh", (x, y, z) -> new double[]{(x - M0) / 3 * 16, (28 - y) / 24 * 16});
        BigModel.Face vial = BigModel.Face.map("vial", (x, y, z) -> new double[]{5.2 + (x - 3.4) / 1.4 * 5.6, (26 - y) / 20 * 16});

        // shell: plinth, top beam, four posts
        m.box(0, 0, 0, 32, 4, 16).front(elev).back(backF).left(sideL).right(sideR).top(beamF).bottom(beamF);
        m.box(0, 28, 0, 32, 32, 16).front(elev).back(backF).left(sideL).right(sideR).top(topF).bottom(beamF);
        m.box(0, 4, 0, P0, 28, 16).front(elev).back(backF).left(sideL).right(beamF);
        m.box(M1, 4, 0, P1, 28, 16).front(elev).back(backF).left(beamF).right(beamF);
        m.box(D1, 4, 0, P2, 28, 16).front(elev).back(backF).left(beamF).right(beamF);
        m.box(P3, 4, 0, 32, 28, 16).front(elev).back(backF).left(beamF).right(sideR);

        // the left bay behind the grate: a dark back wall, a glowing flask with copper rings
        m.box(M0, 4, 13.5, M1, 28, BACKZ).front(darkF).back(backF);
        m.box(M0, 4, 1.6, M1, 28, 1.6).front(mesh).back(mesh);
        m.box(3.4, 6, 6, 4.8, 26, 7.2).front(vial.glow(13)).left(vial.glow(13)).right(vial.glow(13)).back(vial.glow(13));
        for (double y : new double[]{9, 16, 23}) {
            m.box(3.2, y, 5.8, 5.0, y + 0.55, 7.4).sides(brassF).top(brassF);
        }

        // display: back wall, five shelves, a partition between the columns, glass
        m.box(D0, 4, 13.5, D1, 28, BACKZ).front(wall).back(backF);
        for (int r = 0; r < 5; r++) {
            double yb = 28 - 4.8 * (r + 1);
            m.box(D0, yb, 1.5, D1, yb + 0.5, 13.5).front(brassF).top(beamF).bottom(beamF).left(beamF).right(beamF);
        }
        // the partition between the columns has a doorway in every row (the archivists walk through them; DriveBayRenderer draws
        // the leaves): behind the doorway a wall of the full row height, above the doorway a brass lintel
        for (int r = 0; r < 5; r++) {
            double yb = 28 - 4.8 * (r + 1) + 0.5, yt = r == 0 ? 28 : yb + 4.3;
            m.box(15.25, yb, DOOR_Z1, 15.75, yt, 13.5).front(beamF).left(beamF).right(beamF).top(beamF);
            m.box(15.25, yb + DOOR_H, 2.5, 15.75, yt, DOOR_Z1).front(brassF).left(brassF).right(brassF).bottom(brassF);
            m.box(15.1, yb, 2.3, 15.9, yb + DOOR_H, 2.5).front(brassF).left(brassF).right(brassF);
        }
        // the display glass is a swinging door drawn by DriveBayRenderer (open while the cabinet window is open)
        if (withGlass) {
            m.box(D0, 4, 0.8, D1, 28, 0.8).front(glassD).back(glassD);
            m.box(G0, 4, 0.8, G1, 28, 0.8).front(glassG).back(glassG);
        }

        // right door: glass, a panel with gauges, a three-dimensional gauge
        m.box(G0, 4, 13.5, G1, 28, BACKZ).front(machine).back(backF);
        m.box(26.4, 16.2, 10.5, 28.6, 18.4, 13.5).front(beamF).sides(brassF);
        // icicles under the top beam
        for (double[] ic : new double[][]{{8.6, 1.6}, {11.4, 2.4}, {13.9, 1.2}, {17.6, 1.8}, {20.3, 2.6}, {22.4, 1.4}, {26.4, 1.5}, {28.3, 2.2}}) {
            m.box(ic[0], 28 - ic[1], 0.4, ic[0] + 0.55, 28, 0.95).all(BigModel.Face.of("ice"));
        }
        CabBack.relief(m, brassF);
        return m;
    }

    /** Adds the display block to a finished model (position in the inventory, the hand, on the ground). */
    static void addDisplay(String model, double guiYaw, double scale) throws Exception {
        Path p = RES.resolve("models/block/" + model + ".json");
        String s = Files.readString(p, StandardCharsets.UTF_8);
        String d = "  \"display\": {\n"
                + "    \"gui\": {\"rotation\": [30, " + (int) guiYaw + ", 0], \"translation\": [0, 0, 0], \"scale\": [" + scale + ", " + scale + ", " + scale + "]},\n"
                + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.5, 0.5, 0.5]},\n"
                + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [0.75, 0.75, 0.75]},\n"
                + "    \"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.375, 0.375, 0.375]},\n"
                + "    \"firstperson_righthand\": {\"rotation\": [0, 45, 0], \"translation\": [0, 0, 0], \"scale\": [0.4, 0.4, 0.4]},\n"
                + "    \"firstperson_lefthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 0, 0], \"scale\": [0.4, 0.4, 0.4]}\n"
                + "  },\n";
        if (!s.contains("\"display\"")) {
            s = s.replace("  \"textures\": {", d + "  \"textures\": {");
            Files.writeString(p, s, StandardCharsets.UTF_8);
        }
    }

    static String texPath(String var, String cell) {
        if (var.equals("elev")) {
            return NS + "cab_front_" + cell;
        }
        if (var.startsWith("elev_")) {
            return NS + "cab_front_" + var.substring(5);
        }
        if (var.equals("back")) {
            return NS + "cab_back_" + cell;
        }
        if (var.startsWith("back_")) {
            return NS + "cab_back_" + var.substring(5);
        }
        return NS + "cab_" + var;
    }

    /** Cells in the world are cutout (drawn before block entities, the glass doors lie on top), the icon is translucent with the glass. */
    static String modelJson(BigModel.Cell cell, String cell2, String display, boolean translucent) {
        StringBuilder sb = new StringBuilder("{\n  \"parent\": \"minecraft:block/block\",\n  \"ambientocclusion\": false,\n");
        sb.append(translucent ? "  \"render_type\": \"minecraft:translucent\",\n" : "  \"render_type\": \"minecraft:cutout\",\n");
        sb.append(display);
        sb.append("  \"textures\": {\n    \"particle\": \"").append(NS).append("cab_beam\"");
        for (String v : cell.vars) {
            sb.append(",\n    \"").append(v).append("\": \"").append(texPath(v, cell2)).append("\"");
        }
        sb.append("\n  },\n  \"elements\": [\n").append(String.join(",\n", cell.elements)).append("\n  ]\n}\n");
        return sb.toString();
    }

    static void cabinetModels() throws Exception {
        BigModel m = cabinetGeometry(false);
        m.lint("copper_drive_bay");
        BigModel withGlass = cabinetGeometry(true);
        String[] rotY = {"", ", \"y\": 90", ", \"y\": 180", ", \"y\": 270"};
        String[] facing = {"north", "east", "south", "west"};
        StringBuilder bs = new StringBuilder("{\n  \"variants\": {\n");
        BigModel.Cell all = new BigModel.Cell();
        boolean firstVar = true;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                String key = BigModel.cellKey(col, row);
                BigModel.Cell cell = m.slice(col, row, 0, 0, false);
                write(RES.resolve("models/block/copper_drive_bay_" + key + ".json"), modelJson(cell, key, "", false));
                BigModel.Cell forItem = withGlass.slice(col, row, 16.0 * (1 - col) - 8, 16.0 * row - 8, true);
                all.elements.addAll(forItem.elements);
                all.vars.addAll(forItem.vars);
                for (int f = 0; f < 4; f++) {
                    if (!firstVar) {
                        bs.append(",\n");
                    }
                    firstVar = false;
                    bs.append("    \"facing=").append(facing[f]).append(",part=").append(key).append("\": {\"model\": \"").append(NS)
                            .append("copper_drive_bay_").append(key).append("\"").append(rotY[f]).append("}");
                }
            }
        }
        write(RES.resolve("blockstates/copper_drive_bay.json"), bs.append("\n  }\n}\n").toString());
        String display = "  \"display\": {\n"
                + "    \"gui\": {\"rotation\": [30, 135, 0], \"translation\": [0, 0, 0], \"scale\": [0.3, 0.3, 0.3]},\n"
                + "    \"ground\": {\"rotation\": [0, 0, 0], \"translation\": [0, 3, 0], \"scale\": [0.2, 0.2, 0.2]},\n"
                + "    \"fixed\": {\"rotation\": [0, 180, 0], \"translation\": [0, 0, 0], \"scale\": [0.3, 0.3, 0.3]},\n"
                + "    \"thirdperson_righthand\": {\"rotation\": [75, 45, 0], \"translation\": [0, 2.5, 0], \"scale\": [0.2, 0.2, 0.2]},\n"
                + "    \"firstperson_righthand\": {\"rotation\": [0, 45, 0], \"translation\": [0, 2, 0], \"scale\": [0.25, 0.25, 0.25]},\n"
                + "    \"firstperson_lefthand\": {\"rotation\": [0, 225, 0], \"translation\": [0, 2, 0], \"scale\": [0.25, 0.25, 0.25]}\n"
                + "  },\n";
        write(RES.resolve("models/block/copper_drive_bay_item.json"), modelJson(all, "bl", display, true));
        write(RES.resolve("models/item/copper_drive_bay.json"), "{\n  \"parent\": \"" + NS + "copper_drive_bay_item\"\n}\n");
        Files.deleteIfExists(RES.resolve("models/block/copper_drive_bay.json"));
    }

    // ----------------------------------------------------- the dynamic part of the cabinet (drawn by DriveBayRenderer)
    /** A six-spoke reel; the renderer rotates it. 2 x 2 units, transparent outside the circle. */
    static Paint reel() {
        // reel: a brass rim, three spokes and a hub; between the spokes it is transparent, so the tape winding under it shows
        Paint p = new Paint(2, 2, 16);
        p.ring(1, 1, 0.9, 0.99, at(BRASS, 1.4));
        p.ring(1, 1, 0.93, 0.97, at(BRASS, 4.6));
        for (int i = 0; i < 3; i++) {
            double a = i * 2 * Math.PI / 3 + 0.4;
            double nx = -Math.sin(a) * 0.09, ny = Math.cos(a) * 0.09;
            p.poly(new double[]{1 + nx * 1.6, 1 + Math.cos(a) * 0.92 + nx, 1 + Math.cos(a) * 0.92 - nx, 1 - nx * 1.6},
                    new double[]{1 + ny * 1.6, 1 + Math.sin(a) * 0.92 + ny, 1 + Math.sin(a) * 0.92 - ny, 1 - ny * 1.6}, at(BRASS, 3.4));
            p.line(1, 1, 1 + Math.cos(a) * 0.9, 1 + Math.sin(a) * 0.9, 0.04, at(BRASS, 5.4));
        }
        p.sphere(1, 1, 0.26, BRASS, 0.5, 1.0);
        p.disc(1, 1, 0.08, at(IRON, 0.1));
        return p;
    }

    /** Tape winding: copper wire with dark grooves; the teal glow is a separate layer {@link #tapeGlow()}. */
    static Paint tapeDisc() {
        Paint p = new Paint(2, 2, 12);
        p.discFn(1, 1, 0.98, (u, v) -> {
            double d = Math.hypot(u - 1, v - 1);
            double ring = 0.5 + 0.5 * Math.cos(d * 34);
            if (ring > 0.62) {
                return Paint.ramp(COPPER, 0.42 + 0.3 * ring);
            }
            return Paint.ramp(COPPER, 0.08 + 0.12 * ring);
        });
        return p;
    }

    /** Glowing windings: the same rings, but only the teal vein on a transparent background (drawn as "eyes"). */
    static Paint tapeGlow() {
        Paint p = new Paint(2, 2, 12);
        p.discFn(1, 1, 0.98, (u, v) -> {
            double d = Math.hypot(u - 1, v - 1);
            double ring = 0.5 + 0.5 * Math.cos(d * 34);
            if (ring > 0.62) {
                return 0;
            }
            return Paint.argb((int) (150 + 100 * (1 - ring)), Paint.ramp(CYAN, 0.7 + 0.3 * (1 - ring)) & 0xFFFFFF);
        });
        return p;
    }

    static void bayTextures() throws Exception {
        Cassette.cabinetTexture().save(entityTex("bay_cassette"));
        for (int i = 0; i < 5; i++) {
            Cassette.icon(i).save(RES.resolve("textures/item/copper_disk_" + new String[]{"1k", "4k", "16k", "64k", "256k"}[i] + ".png").toFile());
        }
        reel().save(entityTex("bay_reel"));
        tapeDisc().save(entityTex("bay_tape"));
        tapeGlow().save(entityTex("bay_tape_glow"));
        Paint white = new Paint(1, 1, 8);
        white.rect(0, 0, 1, 1, rgb(0xFFFFFF));
        white.save(entityTex("bay_white"));
        // glow of the front runes: the same glyphs as on the plates, on a transparent background
        Paint g = new Paint(32, 32, 4);
        g.rune(14.0, 1.2, 1.7, 16, at(VIOLET, 5.6), at(VIOLET, 3.4));
        g.rune(16.6, 1.2, 1.7, 0, at(VIOLET, 5.6), at(VIOLET, 3.4));
        g.rune(15.2, 29.1, 1.9, 19, at(VIOLET, 5.6), at(VIOLET, 3.4));
        g.save(entityTex("bay_runes_glow"));
    }

    // ------------------------------------------------------------------------- pattern provider
    /** Provider feeding side signs: arrows in the face plane, "towards you" (⊙), "away from you" (⊗) and "all sides" (a sun). */
    static final String[] PROVIDER_SIGNS = {"sun", "left", "right", "up", "down", "out", "in"};

    /** Sign sign centred at (cx, cy) with scale k (1 = a sign about 2.4 pixels high). */
    static void providerSign(Paint p, double cx, double cy, String sign, double k) {
        int c = at(BRASS, 4.4), dark = at(IRON, 0.15);
        switch (sign) {
            case "sun" -> {
                p.disc(cx, cy, 0.7 * k, c);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    double r0 = 0.95 * k, r1 = (i % 2 == 0 ? 1.45 : 1.2) * k;
                    p.line(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0, cx + Math.cos(a) * r1, cy + Math.sin(a) * r1, 0.22 * k, c);
                }
            }
            case "out" -> {
                p.ring(cx, cy, 0.85 * k, 1.25 * k, c);
                p.disc(cx, cy, 0.42 * k, c);
            }
            case "in" -> {
                p.ring(cx, cy, 0.85 * k, 1.25 * k, c);
                p.line(cx - 0.6 * k, cy - 0.6 * k, cx + 0.6 * k, cy + 0.6 * k, 0.28 * k, c);
                p.line(cx - 0.6 * k, cy + 0.6 * k, cx + 0.6 * k, cy - 0.6 * k, 0.28 * k, c);
            }
            default -> {
                // an arrow "to the right" (the x axis), rotated to the needed side
                double[][] shaft = {{-1.5, -0.25}, {0.5, -0.25}, {0.5, 0.25}, {-1.5, 0.25}};
                double[][] head = {{0.3, -1.0}, {1.55, 0.0}, {0.3, 1.0}};
                for (double[][] shape : new double[][][]{shaft, head}) {
                    double[] xs = new double[shape.length], ys = new double[shape.length];
                    for (int i = 0; i < shape.length; i++) {
                        double x = shape[i][0] * k, y = shape[i][1] * k;
                        double rx = switch (sign) {
                            case "left" -> -x;
                            case "up", "down" -> y;
                            default -> x;
                        };
                        double ry = switch (sign) {
                            case "up" -> -x;
                            case "down" -> x;
                            default -> y;
                        };
                        xs[i] = cx + rx;
                        ys[i] = cy + ry;
                    }
                    p.poly(xs, ys, c);
                }
            }
        }
        if (dark != 0 && sign.equals("in")) {
            p.disc(cx, cy, 0.18 * k, dark);
        }
    }

    /** Provider face: a bronze frame, a rune strip, a slot with a punch card, an engraved "outwards" arrow, LEDs. */
    static Paint providerFace() {
        return providerFace("right");
    }

    /** The same with the feeding side sign instead of the arrow (sign, see PROVIDER_SIGNS). */
    static Paint providerFace(String sign) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRONZE, 2.9, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.08, 501, true);
        p.grain(0, 0, 16, 16, 0.04, 502);
        // a dark inner panel with a brass edging
        p.slab(1.4, 1.4, 14.6, 14.6, BRASS, 2.6, 1);
        p.slab(2.0, 2.0, 14.0, 14.0, IRON, 1.2, 1);
        p.grain(2.0, 2.0, 14.0, 14.0, 0.07, 503);
        // rune strip
        p.glowRect(3.2, 2.8, 12.8, 4.6, 0.6, at(CYAN, 3.2), 50);
        int[] glyphs = {3, 12, 4, 12, 3};
        for (int i = 0; i < 5; i++) {
            p.rune(3.7 + i * 1.8, 2.9, 1.6, glyphs[i], at(CYAN, 5.2), 0);
        }
        // the feed slot and a punch card sticking out
        p.slab(3.0, 6.3, 13.0, 8.9, BRASS, 2.5, 1);
        p.rect(3.7, 6.9, 12.3, 8.3, rgb(0x050607));
        p.rect(4.6, 5.2, 11.4, 7.4, rgb(0xE6DCB8));
        p.rect(4.6, 5.2, 11.4, 5.4, rgb(0xFFFFFF));
        p.rect(4.6, 7.2, 11.4, 7.4, rgb(0xB0A47C));
        for (int i = 0; i < 12; i++) {
            if ((i * 7 + 3) % 5 < 3) {
                double y = 5.8 + ((i * 3) % 3) * 0.4;
                p.rect(5.0 + i * 0.5, y, 5.3 + i * 0.5, y + 0.3, rgb(0x3A2A18));
            }
        }
        // the feeding side sign (brass): a long arrow sideways, the other signs in the middle of the strip
        if (sign.equals("right") || sign.equals("left")) {
            boolean r = sign.equals("right");
            p.rect(r ? 4.2 : 5.2, 10.0, r ? 10.8 : 11.8, 10.5, at(BRASS, 4.4));
            p.poly(r ? new double[]{10.4, 12.0, 10.4} : new double[]{5.6, 4.0, 5.6}, new double[]{9.2, 10.25, 11.3}, at(BRASS, 4.4));
        } else {
            providerSign(p, 8.0, 10.75, sign, 1.22);
        }
        // LEDs
        int[] led = {0x3CE070, 0xF0C850, 0x45D6D2, 0xF03A2A};
        for (int i = 0; i < 4; i++) {
            p.disc(4.4 + i * 1.6, 13.05, 0.55, at(IRON, 0.2));
            p.disc(4.4 + i * 1.6, 13.05, 0.4, rgb(led[i]));
        }
        // bolts in the corners
        for (double[] c : new double[][]{{0.8, 0.8}, {15.2, 0.8}, {0.8, 15.2}, {15.2, 15.2}}) {
            p.screw(c[0], c[1], 0.5, BRASS, c[0] + c[1]);
        }
        p.frostLine(0, 16, 0, 0.5, 504);
        return p;
    }

    /** Provider top and bottom: a bronze plate with a vent grate. */
    static Paint providerTop() {
        return providerTop(null);
    }

    /** The top with the feeding side sign on a brass washer in the middle of the grate (null = no sign, for the bottom). */
    static Paint providerTop(String sign) {
        Paint p = new Paint(16, 16, K);
        beam(p, 0, 0, 16, 16, 2.7, true, 511);
        p.slab(1.2, 1.2, 14.8, 14.8, BRONZE, 2.1, 1);
        p.slab(4.5, 4.5, 11.5, 11.5, IRON, 1.2, 1);
        for (int i = 0; i < 6; i++) {
            p.rect(5.0, 5.2 + i * 0.95, 11.0, 5.7 + i * 0.95, at(IRON, 0.2));
        }
        for (double[] c : new double[][]{{2.2, 2.2}, {13.8, 2.2}, {2.2, 13.8}, {13.8, 13.8}}) {
            p.screw(c[0], c[1], 0.6, BRASS, c[0]);
        }
        if (sign != null) {
            p.disc(8, 8, 2.75, at(BRASS, 2.0));
            p.disc(8, 8, 2.35, at(IRON, 0.35));
            providerSign(p, 8, 8, sign, 1.45);
        }
        return p;
    }

    /**
     * Provider models by feeding side: on every side face an arrow towards the feeding side (as seen from outside), on
     * the face it feeds "towards you", on the opposite one "away from you"; for all sides a "sun" on all faces.
     */
    static void providerModels() throws Exception {
        for (String s : PROVIDER_SIGNS) {
            providerFace(s).save(tex("provider_face_" + s));
            providerTop(s).save(tex("provider_top_" + s));
        }
        String[] outs = {"all", "down", "up", "north", "south", "west", "east"};
        String[] sides = {"north", "south", "east", "west"};
        StringBuilder bs = new StringBuilder("{\n  \"variants\": {\n");
        for (int o = 0; o < outs.length; o++) {
            String out = outs[o];
            StringBuilder tx = new StringBuilder();
            tx.append("    \"particle\": \"").append(NS).append("provider_face_sun\",\n");
            tx.append("    \"down\": \"").append(NS).append("provider_top\",\n");
            tx.append("    \"up\": \"").append(NS).append("provider_top_").append(topSign(out)).append("\"");
            for (String side : sides) {
                tx.append(",\n    \"").append(side).append("\": \"").append(NS).append("provider_face_").append(faceSign(side, out)).append("\"");
            }
            String name = out.equals("all") ? "pattern_provider" : "pattern_provider_" + out;
            write(RES.resolve("models/block/" + name + ".json"),
                    "{\n  \"parent\": \"minecraft:block/cube\",\n  \"textures\": {\n" + tx + "\n  }\n}\n");
            bs.append("    \"output=").append(out).append("\": {\"model\": \"").append(NS).append(name).append("\"}")
                    .append(o + 1 < outs.length ? ",\n" : "\n");
        }
        write(RES.resolve("blockstates/pattern_provider.json"), bs.append("  }\n}\n").toString());
    }

    /** The sign on side face side when feeding out (the sides as seen by a viewer standing in front of that face). */
    static String faceSign(String side, String out) {
        if (out.equals("all")) {
            return "sun";
        }
        if (out.equals(side)) {
            return "out";
        }
        String opposite = switch (side) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
        if (out.equals(opposite)) {
            return "in";
        }
        if (out.equals("up") || out.equals("down")) {
            return out;
        }
        // on the viewer's left: in front of the north face it is east, the south face west, the east face south, the west face north
        String left = switch (side) {
            case "north" -> "east";
            case "south" -> "west";
            case "east" -> "south";
            default -> "north";
        };
        return out.equals(left) ? "left" : "right";
    }

    /** The sign on the top face (the top of the texture is north, the left is west). */
    static String topSign(String out) {
        return switch (out) {
            case "all" -> "sun";
            case "up" -> "out";
            case "down" -> "in";
            case "north" -> "up";
            case "south" -> "down";
            case "west" -> "left";
            default -> "right";
        };
    }

    static void cabinet() throws Exception {
        providerFace().save(tex("provider_face"));
        providerTop().save(tex("provider_top"));
        providerModels();
        cabinetTextures();
        brassTile().save(tex("cab_brass"));
        bayTextures();
        cabinetModels();
    }

    public static void all() throws Exception {
        cabinet();
        Buses.all();
        Pipes.all();
        Terminals.all();
        Saturator.all();
    }

    public static void main(String[] a) throws Exception {
        all();
        System.out.println("Forge2: done");
    }
}
