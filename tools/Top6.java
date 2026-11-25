import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Wave 2 (P1) redesign of Channel Expander, Quantum Channel Expander, Cryo-Calculator, Cooling Co-Processor,
 * and Creative Source Jar:
 * deep 3D voxel geometry + Paint.SLATE_IRON (deepslate gray steel), weathered riveted copper, warm antique brass,
 * warm workshop walnut, and glowing frost-cyan / amethyst-violet crystal cores.
 * Preserves all shared textures (cryo_panel, cryo_fin, dev_*, sat_*) and BER alignments.
 */
final class Top6 {
    static final int K = Forge2.K;
    static final int[] SLATE = Paint.SLATE_IRON, BRASS = Paint.BRASS, BRONZE = Paint.BRONZE,
            CYAN = Paint.CYAN, VIOLET = Paint.VIOLET, FROST = Paint.FROST, CREAM = Paint.CREAM;
    static final int[] COPPER = Atlant.COPPER, PATINA = Atlant.PATINA;

    private Top6() {
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    static int at(int[] r, double idx) {
        return Paint.at(r, idx);
    }

    static Models.El e(double a, double b, double c, double d, double f, double g) {
        return Models.e(a, b, c, d, f, g);
    }

    static File tex(String name) {
        return Forge2.tex(name);
    }

    // ========================================================================= Shared Wave 2 material textures

    /** Forged deepslate-gray steel plate with bevels, subtle grain, and corner rivets (matches Wave 1 SLATE_IRON). */
    static Paint slateIron(int seed) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, seed);
        p.frame(0, 0, 16, 16, 0.5, rgb(SLATE[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(SLATE[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1, r[1] + 1, rgb(SLATE[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(SLATE[5]));
        }
        return p;
    }

    /** Weathered riveted copper plate with muted dark patina flecks (no bright mint blobs). */
    static Paint weatheredCopper(int seed) {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.hash(i / 2, j / 2, seed) * 0.45 + Atlant.noise(i, j, 10, seed + 1) * 0.55;
                int c = rgb(COPPER[Math.max(1, Math.min(5, 1 + (int) Math.round(n * 4)))]);
                p.px(i, j, c);
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(COPPER[0]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(COPPER[6]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(COPPER[1]));
        p.rect(0.5, 8.0, 15.5, 8.5, rgb(COPPER[5]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1, r[1] + 1, rgb(COPPER[0]));
            p.rect(r[0], r[1], r[0] + 0.5, r[1] + 0.5, rgb(COPPER[6]));
        }
        p.px(2, 3, rgb(PATINA[0]));
        p.px(p.w - 3, p.h - 4, rgb(PATINA[1]));
        return p;
    }

    /** Warm antique brass with brushed streaks & dark bronze rim. */
    static Paint warmBrass(int seed) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.9, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, seed, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(BRASS[2]));
        return p;
    }

    // ========================================================================= 1 & 2. Expanders

    static Paint expCore(boolean quantum) {
        Paint p = new Paint(16, 16, K);
        int bg = quantum ? rgb(0x120824) : rgb(0x071a24);
        p.rect(0, 0, 16, 16, bg);
        if (!quantum) {
            for (double y = 2.0; y < 14.5; y += 2.0) {
                p.rect(1.0, y, 15.0, y + 1.0, rgb(CYAN[4]));
                p.rect(2.5, y + 0.25, 13.5, y + 0.75, rgb(CYAN[5]));
            }
            p.rect(6.5, 1.0, 9.5, 15.0, rgb(CYAN[4]));
            p.rect(7.5, 1.5, 8.5, 14.5, rgb(CYAN[6]));
        } else {
            for (double y = 2.0; y < 14.5; y += 2.0) {
                p.rect(1.5, y, 14.5, y + 1.1, rgb(VIOLET[2]));
                p.rect(3.0, y + 0.25, 13.0, y + 0.75, rgb(VIOLET[4]));
            }
            p.disc(8, 8, 2.8, rgb(VIOLET[4]));
            p.disc(8, 8, 1.4, rgb(VIOLET[5]));
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(SLATE[0]));
        return p;
    }

    static Paint expPrism() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K;
                double d = Math.abs(u - 8.0) / 8.0;
                int c = d > 0.72 ? VIOLET[1] : d > 0.32 ? VIOLET[3] : VIOLET[4];
                p.px(i, j, rgb(c));
            }
        }
        p.rect(0.5, 0.5, 15.5, 2.5, rgb(VIOLET[5]));
        p.rect(4.5, 4.0, 10.5, 12.5, rgb(VIOLET[4]));
        p.rect(6.0, 5.5, 8.5, 10.0, rgb(0xd8c8ff));
        p.frame(0, 0, 16, 16, 0.5, rgb(VIOLET[0]));
        return p;
    }

    static Paint expTop(boolean quantum, int seed) {
        Paint p = weatheredCopper(seed);
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(SLATE[2]));
        p.rect(4.0, 7.25, 12.0, 8.75, rgb(SLATE[0]));
        p.rect(4.5, 7.5, 11.5, 8.5, rgb(quantum ? VIOLET[5] : CYAN[5]));
        return p;
    }

    /** Builds either Channel Expander (cyan louvers) or Quantum Expander (mullioned violet void prisms). */
    static void buildExpander(String name, boolean quantum) throws Exception {
        String coreTex = quantum ? "exp_core_quantum" : "exp_core_cyan";
        String topTex = quantum ? "exp_top_quantum" : "exp_top_cyan";
        List<String[]> texList = new ArrayList<>(List.of(
                new String[]{"iron", "exp_iron"}, new String[]{"copper", "exp_copper"},
                new String[]{"brass", "exp_brass"}, new String[]{"core", coreTex}, new String[]{"top", topTex}));
        if (quantum) {
            texList.add(new String[]{"prism", "exp_prism"});
        }
        List<Models.El> l = new ArrayList<>();
        // Glowing inner conduit core
        l.add(e(1.5, 2.0, 1.5, 14.5, 14.0, 14.5).only("#core", "north", "south", "east", "west").glow(13));
        // Bottom & top copper caps slightly inset vertically (0.25..2.0, 14.0..15.75) so clamps on y=0/16 never coplanar
        l.add(e(0.5, 0.25, 0.5, 15.5, 2.0, 15.5).only("#copper", "north", "south", "east", "west")
                .face("up", "#top", 0, 0, 16, 16).face("down", "#top", 0, 0, 16, 16));
        l.add(e(0.5, 14.0, 0.5, 15.5, 15.75, 15.5).only("#copper", "north", "south", "east", "west")
                .face("up", "#top", 0, 0, 16, 16).face("down", "#top", 0, 0, 16, 16));

        // 4 corner bolted SLATE_IRON clamp brackets (top/bottom) + copper middle pilasters
        for (double cx : new double[]{0.0, 13.75}) {
            for (double cz : new double[]{0.0, 13.75}) {
                l.add(e(cx, 0.0, cz, cx + 2.25, 2.5, cz + 2.25).all("#iron"));
                l.add(e(cx, 13.5, cz, cx + 2.25, 16.0, cz + 2.25).all("#iron"));
                double px = cx == 0.0 ? 0.25 : 14.0;
                double pz = cz == 0.0 ? 0.25 : 14.0;
                l.add(e(px, 2.5, pz, px + 1.75, 13.5, pz + 1.75).only("#copper", "north", "south", "east", "west"));
            }
        }

        // 4 symmetrical louvered / prismatic radiator faces (N/S/E/W)
        for (int dir = 0; dir < 4; dir++) {
            boolean ns = dir < 2;
            boolean minSide = (dir % 2) == 0;
            double d0 = minSide ? 0.7 : 14.5;
            double d1 = minSide ? 1.5 : 15.3;
            String outFace = dir == 0 ? "north" : dir == 1 ? "south" : dir == 2 ? "west" : "east";
            if (!quantum) {
                for (double y : new double[]{3.1, 5.1, 7.1, 9.1, 11.1}) {
                    l.add(ns
                            ? e(2.0, y, d0, 14.0, y + 0.9, d1).only("#brass", outFace, minSide ? "south" : "north", "up", "down")
                            : e(d0, y, 2.0, d1, y + 0.9, 14.0).only("#brass", outFace, minSide ? "east" : "west", "up", "down"));
                }
            } else {
                // Vertical center slate divider mullion + upper/lower brass headers
                double md0 = minSide ? 0.55 : 14.5;
                double md1 = minSide ? 1.5 : 15.45;
                l.add(ns
                        ? e(7.3, 2.0, md0, 8.7, 14.0, md1).only("#iron", outFace, minSide ? "south" : "north", "east", "west")
                        : e(md0, 2.0, 7.3, md1, 14.0, 8.7).only("#iron", outFace, minSide ? "east" : "west", "north", "south"));
                double bd0 = minSide ? 0.7 : 14.5;
                double bd1 = minSide ? 1.5 : 15.3;
                for (double bayStart : new double[]{2.0, 8.7}) {
                    double bayEnd = bayStart == 2.0 ? 7.3 : 14.0;
                    for (double hy : new double[]{2.1, 13.3}) {
                        l.add(ns
                                ? e(bayStart, hy, bd0, bayEnd, hy + 0.6, bd1).only("#brass", outFace, minSide ? "south" : "north", "up", "down")
                                : e(bd0, hy, bayStart, bd1, hy + 0.6, bayEnd).only("#brass", outFace, minSide ? "east" : "west", "up", "down"));
                    }
                    // Staggered glowing violet crystal louver shards inside each bay
                    double[][] shards = {
                            {3.3, 0.35, 0.45, 0.85},
                            {5.2, 0.65, 0.25, 0.9},
                            {7.1, 0.30, 0.75, 0.85},
                            {9.0, 0.70, 0.35, 0.9},
                            {10.9, 0.40, 0.55, 0.85}
                    };
                    for (double[] sh : shards) {
                        double y = sh[0], inL = sh[1], inR = sh[2], h = sh[3];
                        double pd0 = minSide ? 0.85 : 14.5;
                        double pd1 = minSide ? 1.5 : 15.15;
                        l.add((ns
                                ? e(bayStart + inL, y, pd0, bayEnd - inR, y + h, pd1)
                                : e(pd0, y, bayStart + inL, pd1, y + h, bayEnd - inR)).all("#prism").glow(14));
                    }
                }
            }
        }
        Models.model(name, false, "exp_copper", texList.toArray(new String[0][]), l.toArray(new Models.El[0]));
        Models.single(name, name);
        Models.item(name, name);
    }

    // ========================================================================= 3. Cryo-Calculator

    static Paint calcTopSnow() {
        Paint p = new Paint(16, 16, K);
        // Snowy roof deck in copper & slate-iron trim (y = 16.0 flush for SnowCrew)
        p.slab(0, 0, 16, 16, COPPER, 2.6, 1);
        p.frame(0, 0, 16, 16, 0.5, rgb(SLATE[1]));
        p.slab(1.5, 1.5, 14.5, 14.5, FROST, 4.4, 1);
        for (int j = 3; j < p.h - 3; j++) {
            for (int i = 3; i < p.w - 3; i++) {
                double n = Atlant.noise(i, j, 6, 9405);
                p.px(i, j, rgb(FROST[n > 0.6 ? 6 : n > 0.25 ? 5 : 4]));
            }
        }
        p.rect(5.5, 5.5, 10.5, 10.5, rgb(SLATE[3]));
        p.rect(6.0, 6.0, 10.0, 10.0, rgb(SLATE[1]));
        for (double y = 6.3; y < 9.7; y += 1.2) {
            p.rect(6.3, y, 9.7, y + 0.5, rgb(CYAN[4]));
        }
        for (double[] c : new double[][]{{1.0, 1.0}, {14.0, 1.0}, {1.0, 14.0}, {14.0, 14.0}}) {
            p.rect(c[0], c[1], c[0] + 1, c[1] + 1, rgb(SLATE[0]));
            p.rect(c[0], c[1], c[0] + 0.5, c[1] + 0.5, rgb(SLATE[5]));
        }
        return p;
    }

    static Paint calcIcicle() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = j / (double) p.h;
                int c = t < 0.25 ? FROST[6] : t < 0.65 ? FROST[4] : FROST[3];
                p.px(i, j, rgb(c));
            }
        }
        p.rect(2.0, 0.0, 5.0, 14.0, rgb(0xffffff));
        return p;
    }

    static Paint calcScreenIdle() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(0x071824));
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, rgb(SLATE[3]));
        // Upper header frost bar & lower static telemetry matrix cells
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                if (u > 1.5 && u < 14.5 && ((v > 2.0 && v < 5.5) || (v > 7.5 && v < 14.0))) {
                    double t = Atlant.hash(i / 2, j / 2, 9411);
                    if (t > 0.68) {
                        p.px(i, j, rgb(t > 0.90 ? CYAN[5] : CYAN[3]));
                    } else if (t > 0.45) {
                        p.px(i, j, rgb(0x0d2638));
                    }
                }
            }
        }
        return p;
    }

    static void calcScreenActive() throws Exception {
        int S = 32, frames = 22;
        BufferedImage out = new BufferedImage(S, S * frames, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(9421);
        int x0 = 4, x1 = 28, y0 = 4, y1 = 28, h = y1 - y0, cols = x1 - x0;
        int[] head = new int[cols], speed = new int[cols], tail = new int[cols];
        boolean[] on = new boolean[cols];
        for (int c = 0; c < cols; c++) {
            head[c] = r.nextInt(h);
            speed[c] = r.nextInt(3) == 0 ? 2 : 1;
            tail[c] = 4 + r.nextInt(6);
            on[c] = c % 2 == 0 || r.nextInt(4) == 0;
        }
        int[][] glyph = new int[cols][h];
        for (int c = 0; c < cols; c++) {
            for (int y = 0; y < h; y++) {
                glyph[c][y] = r.nextInt(4);
            }
        }
        for (int f = 0; f < frames; f++) {
            for (int y = 0; y < S; y++) {
                for (int x = 0; x < S; x++) {
                    out.setRGB(x, f * S + y, (x + y) % 2 == 0 ? 0xFF06131E : 0xFF0A1B29);
                }
            }
            for (int c = 0; c < cols; c++) {
                if (!on[c]) {
                    continue;
                }
                int hy = Math.floorMod(head[c] + f * speed[c], h);
                for (int t = 0; t <= tail[c]; t++) {
                    int y = Math.floorMod(hy - t, h);
                    if (t > 0 && glyph[c][y] == 0) {
                        continue;
                    }
                    double k = t == 0 ? 1.0 : 0.88 - 0.72 * t / (double) tail[c];
                    int col = t == 0 ? rgb(0xE2FFFE) : Paint.ramp(CYAN, Math.max(0.2, k));
                    out.setRGB(x0 + c, f * S + y0 + y, col);
                }
            }
        }
        File outFile = tex("calc_screen_active");
        ImageIO.write(out, "png", outFile);
        Forge2.write(Forge2.RES.resolve("textures/block/calc_screen_active.png.mcmeta"),
                "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}\n");
    }

    static Paint calcCells(boolean active) {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(0x081926));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                double x = 2.0 + col * 3.1, y = 1.8 + row * 3.2;
                int c = active ? ((row + col) % 2 == 0 ? CYAN[5] : CYAN[4])
                        : ((row + col * 2) % 3 == 0 ? CYAN[4] : CYAN[2]);
                p.rect(x, y, x + 2.4, y + 2.4, rgb(c));
                p.rect(x + 0.5, y + 0.5, x + 1.5, y + 1.5, rgb(active ? 0xe8ffff : CYAN[5]));
            }
        }
        return p;
    }

    static void calculator() throws Exception {
        slateIron(9401).save(tex("calc_iron"));
        weatheredCopper(9402).save(tex("calc_copper"));
        warmBrass(9403).save(tex("calc_brass"));
        calcTopSnow().save(tex("calc_top"));
        calcIcicle().save(tex("calc_icicle"));
        calcScreenIdle().save(tex("calc_screen_idle"));
        calcScreenActive();
        calcCells(false).save(tex("calc_cells_idle"));
        calcCells(true).save(tex("calc_cells_active"));

        for (boolean active : new boolean[]{false, true}) {
            String modelName = active ? "cryo_calculator_active" : "cryo_calculator";
            String[][] t = {
                    {"iron", "calc_iron"}, {"copper", "calc_copper"}, {"brass", "calc_brass"},
                    {"top", "calc_top"}, {"icicle", "calc_icicle"},
                    {"screen", active ? "calc_screen_active" : "calc_screen_idle"},
                    {"cells", active ? "calc_cells_active" : "calc_cells_idle"}
            };
            Models.model(modelName, false, "calc_copper", t, calculatorBody(active));
        }

        // Item model merges redesigned 3D calculator body + 3 miniature roof snowmen
        String[][] it = {
                {"iron", "calc_iron"}, {"copper", "calc_copper"}, {"brass", "calc_brass"},
                {"top", "calc_top"}, {"icicle", "calc_icicle"},
                {"screen", "calc_screen_idle"}, {"cells", "calc_cells_idle"}, {"snow", "minecraft:block/snow"}
        };
        List<Models.El> item = new ArrayList<>(List.of(calculatorBody(false)));
        item.add(e(2.4, 16, 3.6, 5.4, 19, 6.6).all("#snow"));
        item.add(e(2.9, 19, 4.1, 4.9, 21, 6.1).all("#snow"));
        item.add(e(10.8, 16, 4.2, 14.4, 19.6, 7.8).all("#snow"));
        item.add(e(11.4, 19.6, 4.8, 13.8, 22, 7.2).all("#snow"));
        item.add(e(5.6, 16, 10.6, 8.2, 18.6, 13.2).all("#snow"));
        item.add(e(6.1, 18.6, 11.1, 7.7, 20.2, 12.7).all("#snow"));
        Models.model("cryo_calculator_item", false, "calc_copper", it, item.toArray(new Models.El[0]));
        Models.item("cryo_calculator", "cryo_calculator_item");
    }

    private static Models.El[] calculatorBody(boolean active) {
        List<Models.El> l = new ArrayList<>();
        // Stepped slate-iron base plinth (y=0..1.75) & snowy copper cornice deck (y=14.5..16.0 flush for SnowCrew)
        l.add(e(0.25, 0.0, 0.25, 15.75, 1.75, 15.75).all("#iron"));
        l.add(e(0.25, 14.5, 0.25, 15.75, 16.0, 15.75).only("#copper", "north", "south", "east", "west", "down")
                .face("up", "#top", 0, 0, 16, 16));

        // 4 corner columns (SLATE_IRON caps/boots + copper shafts, zero Y/plane overlap)
        for (double cx : new double[]{0.5, 13.5}) {
            for (double cz : new double[]{0.5, 13.5}) {
                double bx = cx == 0.5 ? 0.4 : 13.6;
                double bz = cz == 0.5 ? 0.4 : 13.6;
                l.add(e(bx, 1.75, bz, bx + 2.0, 3.5, bz + 2.0).only("#iron", "north", "south", "east", "west", "up"));
                l.add(e(bx, 12.75, bz, bx + 2.0, 14.5, bz + 2.0).only("#iron", "north", "south", "east", "west", "down"));
                double sx = cx == 0.5 ? 0.5 : 13.7;
                double sz = cz == 0.5 ? 0.5 : 13.7;
                l.add(e(sx, 3.5, sz, sx + 1.8, 12.75, sz + 1.8).only("#copper", "north", "south", "east", "west"));
            }
        }

        // Recessed glowing frost-cyan screens (N/S) and perforated logic cells (E/W)
        l.add(e(1.2, 1.75, 1.2, 14.8, 14.5, 14.8)
                .face("north", "#screen", 0, 0, 16, 16)
                .face("south", "#screen", 0, 0, 16, 16)
                .face("east", "#cells", 0, 0, 16, 16)
                .face("west", "#cells", 0, 0, 16, 16)
                .glow(active ? 14 : 11));

        // Horizontal copper & brass transom framing upper crystal header & lower bay on N/S/E/W
        l.add(e(2.3, 9.25, 0.65, 13.7, 10.35, 1.2).only("#copper", "north", "up", "down"));
        l.add(e(2.3, 9.25, 14.8, 13.7, 10.35, 15.35).only("#copper", "south", "up", "down"));
        l.add(e(0.65, 9.25, 2.3, 1.2, 10.35, 13.7).only("#copper", "west", "up", "down"));
        l.add(e(14.8, 9.25, 2.3, 15.35, 10.35, 13.7).only("#copper", "east", "up", "down"));

        // Warm brass sill trim along top header under cornice
        l.add(e(2.4, 13.8, 0.8, 13.6, 14.5, 1.2).only("#brass", "north", "down"));
        l.add(e(2.4, 13.8, 14.8, 13.6, 14.5, 15.2).only("#brass", "south", "down"));
        l.add(e(0.8, 13.8, 2.4, 1.2, 14.5, 13.6).only("#brass", "west", "down"));
        l.add(e(14.8, 13.8, 2.4, 15.2, 14.5, 13.6).only("#brass", "east", "down"));

        // 3x3 perforated SLATE_IRON logic grille on East & West lower bays (y=1.75..9.25, strictly disjoint segments)
        for (boolean west : new boolean[]{true, false}) {
            double x0 = west ? 0.75 : 14.8, x1 = west ? 1.2 : 15.25;
            String out = west ? "west" : "east";
            for (double zBar : new double[]{5.6, 9.6}) {
                l.add(e(x0, 1.75, zBar, x1, 9.25, zBar + 0.8).only("#iron", out, "north", "south"));
            }
            for (double[] span : new double[][]{{2.3, 5.6}, {6.4, 9.6}, {10.4, 13.7}}) {
                for (double yRail : new double[]{4.0, 6.5}) {
                    l.add(e(x0, yRail, span[0], x1, yRail + 0.8, span[1]).only("#iron", out, "up", "down"));
                }
            }
        }

        // Crisp frost icicles tucked under top cornice eaves
        for (double x : new double[]{2.8, 12.6}) {
            l.add(e(x, 12.9, 0.45, x + 0.6, 14.5, 1.0).only("#icicle", "north", "south", "east", "west", "down"));
            l.add(e(x, 12.9, 15.0, x + 0.6, 14.5, 15.55).only("#icicle", "north", "south", "east", "west", "down"));
        }
        return l.toArray(new Models.El[0]);
    }

    // ========================================================================= 4. Cooling Co-Processor

    static Paint copTop() {
        Paint p = weatheredCopper(9511);
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(SLATE[2]));
        p.rect(7.5, 1.5, 8.5, 14.5, rgb(COPPER[0]));
        p.rect(1.5, 7.5, 14.5, 8.5, rgb(COPPER[0]));
        p.disc(8, 8, 3.2, rgb(SLATE[1]));
        p.ring(8, 8, 2.5, 3.2, rgb(SLATE[5]));
        p.disc(8, 8, 1.8, rgb(CYAN[4]));
        p.disc(8, 8, 0.9, rgb(CYAN[6]));
        return p;
    }

    static Paint copSpindle() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, SLATE, 2.5, 1);
        for (double y = 1.5; y < 14.5; y += 3.0) {
            p.rect(1.0, y, 15.0, y + 1.5, rgb(CYAN[4]));
            p.rect(2.5, y + 0.4, 13.5, y + 1.1, rgb(0xe4ffff));
        }
        return p;
    }

    static Paint copFin() {
        Paint p = new Paint(16, 16, K);
        // Frosted silver-slate anodized heat-sink fin (high contrast bright bevel top + cool slate middle + dark under-edge)
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.noise(i, j, 8, 9521);
                int[] silver = {0x4a525e, 0x626c7a, 0x7e8a98, 0x9aa6b4, 0xb6c4d2, 0xd4e2ee};
                p.px(i, j, rgb(silver[Math.max(1, Math.min(5, 2 + (int) Math.round(n * 2.5)))]));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(0x3a424d));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(0xe2eef8));
        p.rect(6.5, 6.5, 9.5, 9.5, rgb(CYAN[4]));
        return p;
    }

    static void coprocessor() throws Exception {
        slateIron(9501).save(tex("cop_iron"));
        weatheredCopper(9502).save(tex("cop_copper"));
        copTop().save(tex("cop_top"));
        copSpindle().save(tex("cop_spindle"));
        copFin().save(tex("cop_fin"));

        String[][] t = {
                {"iron", "cop_iron"}, {"copper", "cop_copper"}, {"top", "cop_top"},
                {"spindle", "cop_spindle"}, {"fin", "cop_fin"}
        };

        List<Models.El> base = new ArrayList<>();
        // Bottom copper press plate (y=0..2.25) & top press plate (y=13.75..16.0)
        base.add(e(0.75, 0.0, 0.75, 15.25, 2.25, 15.25).only("#copper", "north", "south", "east", "west")
                .face("up", "#top", 0, 0, 16, 16).face("down", "#top", 0, 0, 16, 16));
        base.add(e(0.75, 13.75, 0.75, 15.25, 16.0, 15.25).only("#copper", "north", "south", "east", "west")
                .face("up", "#top", 0, 0, 16, 16).face("down", "#top", 0, 0, 16, 16));
        // 4 bolted SLATE_IRON corner clamp lugs (offset y so zero coplanar faces with y=0/2.25/13.75/16)
        for (double cx : new double[]{0.25, 13.0}) {
            for (double cz : new double[]{0.25, 13.0}) {
                base.add(e(cx, 0.25, cz, cx + 2.75, 2.75, cz + 2.75).all("#iron"));
                base.add(e(cx, 13.25, cz, cx + 2.75, 15.75, cz + 2.75).all("#iron"));
            }
        }
        // Inner SLATE_IRON thermal collars strictly inside corner lugs (3.0..13.0)
        base.add(e(3.0, 2.25, 3.0, 13.0, 3.0, 13.0).only("#iron", "north", "south", "east", "west", "up"));
        base.add(e(3.0, 13.0, 3.0, 13.0, 13.75, 13.0).only("#iron", "north", "south", "east", "west", "down"));
        // Central glowing cryo core spindle
        base.add(e(6.25, 3.0, 6.25, 9.75, 13.0, 9.75).only("#spindle", "north", "south", "east", "west").glow(12));
        Models.model("cooling_coprocessor", false, "cop_top", t, base.toArray(new Models.El[0]));

        // Stepped frosted heat-sink fin (animated separately by CoolingCoprocessorRenderer at y=3.5..11.5)
        Models.El[] finShape = {
                e(1.5, 0.0, 1.5, 14.5, 1.0, 14.5).all("#fin"),
                e(0.75, 0.2, 2.25, 1.5, 0.8, 13.75).only("#fin", "north", "south", "west", "up", "down"),
                e(14.5, 0.2, 2.25, 15.25, 0.8, 13.75).only("#fin", "north", "south", "east", "up", "down"),
                e(2.25, 0.2, 0.75, 13.75, 0.8, 1.5).only("#fin", "north", "east", "west", "up", "down"),
                e(2.25, 0.2, 14.5, 13.75, 0.8, 15.25).only("#fin", "south", "east", "west", "up", "down")
        };
        Models.model("cooling_coprocessor_fin", false, "cop_fin", t, finShape);

        List<Models.El> item = new ArrayList<>(base);
        for (double y : new double[]{3.5, 5.5, 7.5, 9.5, 11.5}) {
            item.add(e(1.5, y, 1.5, 14.5, y + 1.0, 14.5).all("#fin"));
            item.add(e(0.75, y + 0.2, 2.25, 1.5, y + 0.8, 13.75).only("#fin", "north", "south", "west", "up", "down"));
            item.add(e(14.5, y + 0.2, 2.25, 15.25, y + 0.8, 13.75).only("#fin", "north", "south", "east", "up", "down"));
            item.add(e(2.25, y + 0.2, 0.75, 13.75, y + 0.8, 1.5).only("#fin", "north", "east", "west", "up", "down"));
            item.add(e(2.25, y + 0.2, 14.5, 13.75, y + 0.8, 15.25).only("#fin", "south", "east", "west", "up", "down"));
        }
        Models.model("cooling_coprocessor_item", false, "cop_top", t, item.toArray(new Models.El[0]));
        Models.item("cooling_coprocessor", "cooling_coprocessor_item");
    }

    // ========================================================================= 5. Creative Source Jar

    static Paint cjarObsidian() {
        Paint p = new Paint(16, 16, K);
        int[] obs = {0x14101e, 0x1e172c, 0x282038, 0x322944, 0x45355e};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.noise(i, j, 8, 9601);
                int c = obs[Math.min(4, (int) (n * 5))];
                if (Atlant.hash(i / 2, j / 2, 9602) > 0.94) {
                    c = VIOLET[4];
                }
                p.px(i, j, rgb(c));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(SLATE[1]));
        return p;
    }

    static Paint cjarGauge() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.8, 1);
        p.disc(8, 8, 6.2, rgb(CREAM[5]));
        p.ring(8, 8, 5.6, 6.2, rgb(CREAM[2]));
        for (int k = 0; k < 5; k++) {
            double a = Math.toRadians(150 + k * 60);
            p.line(8 + Math.cos(a) * 3.8, 8 + Math.sin(a) * 3.8, 8 + Math.cos(a) * 5.2, 8 + Math.sin(a) * 5.2, 0.7, rgb(0x241c18));
        }
        p.line(8, 8, 11.2, 4.5, 0.9, rgb(0xb8241a));
        p.disc(8, 8, 1.2, rgb(SLATE[0]));
        return p;
    }

    static Paint cjarCrown() {
        // Pierced antique-brass filigree gallery cutouts mapped [0..16, 0..16] (binary alpha 0 or 255)
        Paint p = new Paint(16, 16, K);
        int b1 = rgb(BRASS[1]), b3 = rgb(BRASS[3]), b5 = rgb(BRASS[5]);
        p.frame(0, 0, 16, 16, 1.5, b3);
        p.rect(0, 0, 16, 0.5, b5);
        p.rect(0, 15.5, 16, 16, b1);
        for (double x : new double[]{3.5, 7.5, 11.5}) {
            p.rect(x, 1.5, x + 1.0, 14.5, b3);
            p.rect(x, 1.5, x + 0.5, 14.5, b5);
        }
        for (double x : new double[]{2.0, 6.0, 10.0, 14.0}) {
            p.ring(x, 8.0, 1.3, 2.3, b3);
            p.disc(x, 4.0, 0.7, b5);
            p.disc(x, 12.0, 0.7, b5);
        }
        p.rect(1.5, 7.5, 14.5, 8.5, b1);
        return p;
    }

    static Paint cjarGlass() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                double d = Math.min(Math.min(u, 16 - u), Math.min(v, 16 - v));
                p.px(i, j, d < 0.75 ? argb(0xb8, FROST[4]) : argb(0x46, 0xa8e2f4));
            }
        }
        p.line(2.5, 13.0, 2.5, 3.0, 0.65, argb(0xd0, 0xffffff));
        p.line(4.0, 10.5, 4.0, 5.5, 0.5, argb(0x88, 0xffffff));
        return p;
    }

    static Paint cjarCrystal() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double d = Math.hypot((i + 0.5) / K - 8.0, (j + 0.5) / K - 8.0) / 11.3;
                int c = d < 0.35 ? CYAN[5] : d < 0.70 ? VIOLET[4] : VIOLET[3];
                p.px(i, j, rgb(c));
            }
        }
        p.line(1, 1, 15, 15, 0.8, rgb(0xf2ecff));
        p.line(15, 1, 1, 15, 0.8, rgb(CYAN[5]));
        p.frame(0, 0, 16, 16, 0.75, rgb(VIOLET[1]));
        return p;
    }

    static Paint cjarCork(boolean topRune) {
        Paint p = new Paint(16, 16, K);
        int[] cork = {0x6e4726, 0x865933, 0x9e6e42, 0xb68452, 0xc99a68};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.hash(i / 2, j / 2, 9631) * 0.5 + Atlant.noise(i, j, 5, 9632) * 0.5;
                p.px(i, j, rgb(cork[Math.min(4, (int) (n * 5))]));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(cork[0]));
        if (topRune) {
            p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(BRASS[3]));
            p.rune(5.5, 4.0, 8.0, 48, rgb(VIOLET[5]), rgb(VIOLET[2]));
            p.disc(8, 8, 1.1, rgb(CYAN[5]));
        }
        return p;
    }

    static void creativeJar() throws Exception {
        cjarObsidian().save(tex("cjar_obsidian"));
        slateIron(9603).save(tex("cjar_iron"));
        warmBrass(9604).save(tex("cjar_brass"));
        cjarGauge().save(tex("cjar_gauge"));
        cjarCrown().save(tex("cjar_crown"));
        cjarGlass().save(tex("cjar_glass"));
        cjarCrystal().save(tex("cjar_crystal"));
        cjarCork(false).save(tex("cjar_cork"));
        cjarCork(true).save(tex("cjar_cork_top"));

        String[][] t = {
                {"obsidian", "cjar_obsidian"}, {"iron", "cjar_iron"}, {"brass", "cjar_brass"},
                {"gauge", "cjar_gauge"}, {"crown", "cjar_crown"}, {"glass", "cjar_glass"},
                {"crystal", "cjar_crystal"}, {"cork", "cjar_cork"}, {"cork_top", "cjar_cork_top"}
        };
        List<Models.El> l = new ArrayList<>();

        // Stepped crying-obsidian & forged slate-iron pedestal with bolted corner footings
        l.add(e(1.5, 0.0, 1.5, 14.5, 1.5, 14.5).all("#obsidian"));
        for (double cx : new double[]{1.25, 12.75}) {
            for (double cz : new double[]{1.25, 12.75}) {
                l.add(e(cx, 0.1, cz, cx + 2.0, 1.75, cz + 2.0).all("#iron"));
            }
        }
        l.add(e(2.5, 1.5, 2.5, 13.5, 3.0, 13.5).only("#iron", "north", "south", "east", "west", "up"));
        // Hollow brass base frame + dark obsidian interior rune bed (so glass floor reads deep dark, never tan)
        l.add(e(3.25, 3.0, 3.25, 12.75, 3.6, 4.0).only("#brass", "north", "south", "east", "west", "up"));
        l.add(e(3.25, 3.0, 12.0, 12.75, 3.6, 12.75).only("#brass", "north", "south", "east", "west", "up"));
        l.add(e(3.25, 3.0, 4.0, 4.0, 3.6, 12.0).only("#brass", "east", "west", "up"));
        l.add(e(12.0, 3.0, 4.0, 12.75, 3.6, 12.0).only("#brass", "east", "west", "up"));
        l.add(e(4.0, 3.0, 4.0, 12.0, 3.55, 12.0).only("#obsidian", "up"));

        // 4 miniature brass pressure gauges around stepped plinth
        l.add(e(6.75, 1.65, 1.75, 9.25, 3.85, 2.5).only("#brass", "east", "west", "up", "down").face("north", "#gauge", 0, 0, 16, 16));
        l.add(e(6.75, 1.65, 13.5, 9.25, 3.85, 14.25).only("#brass", "east", "west", "up", "down").face("south", "#gauge", 0, 0, 16, 16));
        l.add(e(1.75, 1.65, 6.75, 2.5, 3.85, 9.25).only("#brass", "north", "south", "up", "down").face("west", "#gauge", 0, 0, 16, 16));
        l.add(e(13.5, 1.65, 6.75, 14.25, 3.85, 9.25).only("#brass", "north", "south", "up", "down").face("east", "#gauge", 0, 0, 16, 16));

        // 4 slender brass corner mullions outside the glass chamber (zero overlap with 3.75..12.25 glass walls)
        for (double mx : new double[]{3.25, 12.2}) {
            for (double mz : new double[]{3.25, 12.2}) {
                l.add(e(mx, 3.6, mz, mx + 0.55, 11.6, mz + 0.55).only("#brass", "north", "south", "east", "west"));
            }
        }

        // Single-layer translucent glass chamber
        l.add(e(3.75, 3.6, 3.75, 12.25, 11.6, 12.25).only("#glass", "north", "south", "east", "west", "up"));

        // Floating glowing infinity crystal centered at (8, 7.6, 8) (rotated 45 deg around Y)
        l.add(e(7.25, 4.9, 7.25, 8.75, 5.9, 8.75).only("#crystal", "north", "south", "east", "west", "down").rot("y", 45, 8, 7.6, 8).glow(15));
        l.add(e(6.6, 5.9, 6.6, 9.4, 6.9, 9.4).only("#crystal", "north", "south", "east", "west", "down").rot("y", 45, 8, 7.6, 8).glow(15));
        l.add(e(6.0, 6.9, 6.0, 10.0, 8.3, 10.0).all("#crystal").rot("y", 45, 8, 7.6, 8).glow(15));
        l.add(e(6.6, 8.3, 6.6, 9.4, 9.3, 9.4).only("#crystal", "north", "south", "east", "west", "up").rot("y", 45, 8, 7.6, 8).glow(15));
        l.add(e(7.25, 9.3, 7.25, 8.75, 10.3, 8.75).only("#crystal", "north", "south", "east", "west", "up").rot("y", 45, 8, 7.6, 8).glow(15));

        // Pierced filigree antique-brass crown gallery (y=11.6..14.7, mapped [0,0,16,16] for full scrollwork arches)
        l.add(e(3.25, 11.6, 3.25, 12.75, 12.3, 12.75).all("#brass"));
        for (double cx : new double[]{3.4, 11.85}) {
            for (double cz : new double[]{3.4, 11.85}) {
                l.add(e(cx, 12.3, cz, cx + 0.75, 14.1, cz + 0.75).only("#brass", "north", "south", "east", "west"));
            }
        }
        l.add(e(4.15, 12.3, 3.55, 11.85, 14.1, 3.95)
                .face("north", "#crown", 0, 0, 16, 16).face("south", "#crown", 0, 0, 16, 16).only("#brass", "up", "down"));
        l.add(e(4.15, 12.3, 12.05, 11.85, 14.1, 12.45)
                .face("north", "#crown", 0, 0, 16, 16).face("south", "#crown", 0, 0, 16, 16).only("#brass", "up", "down"));
        l.add(e(3.55, 12.3, 4.15, 3.95, 14.1, 11.85)
                .face("west", "#crown", 0, 0, 16, 16).face("east", "#crown", 0, 0, 16, 16).only("#brass", "up", "down"));
        l.add(e(12.05, 12.3, 4.15, 12.45, 14.1, 11.85)
                .face("west", "#crown", 0, 0, 16, 16).face("east", "#crown", 0, 0, 16, 16).only("#brass", "up", "down"));
        l.add(e(4.0, 14.1, 4.0, 12.0, 14.7, 12.0).only("#brass", "north", "south", "east", "west", "up", "down"));

        // Runic cork stopper
        l.add(e(5.5, 14.7, 5.5, 10.5, 16.0, 10.5).only("#cork", "north", "south", "east", "west")
                .face("up", "#cork_top", 0, 0, 16, 16));

        Models.model("creative_source_jar", true, "cjar_iron", t, l.toArray(new Models.El[0]));
        Models.single("creative_source_jar", "creative_source_jar");
        Models.item("creative_source_jar", "creative_source_jar");
    }

    static void all() throws Exception {
        slateIron(9101).save(tex("exp_iron"));
        weatheredCopper(9102).save(tex("exp_copper"));
        warmBrass(9103).save(tex("exp_brass"));
        expCore(false).save(tex("exp_core_cyan"));
        expCore(true).save(tex("exp_core_quantum"));
        expPrism().save(tex("exp_prism"));
        expTop(false, 9104).save(tex("exp_top_cyan"));
        expTop(true, 9105).save(tex("exp_top_quantum"));

        buildExpander("channel_expander", false);
        buildExpander("quantum_channel_expander", true);
        calculator();
        coprocessor();
        creativeJar();
    }
}
