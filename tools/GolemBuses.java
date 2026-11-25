import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wave 1 (P0) redesign of Copper Tank, Mason's Formation Bus and Hewer's Annihilation Bus:
 * crisp 32x32 pixel clusters (K=2) with riveted copper, dark forged cast iron, walnut wood, and muted antique brass.
 */
final class GolemBuses {
    static final int K = Forge2.K;

    private GolemBuses() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    static int at(int[] r, double idx) {
        return Paint.at(r, idx);
    }

    static int copperTone(int i, int j, int seed) {
        double t = Atlant.noise(i, j, 12, seed + 1);
        int tone = 3 + (int) Math.round(t * 2.2 - 0.3);
        if (Atlant.hash(i / 4, j / 2, seed) > 0.94) {
            tone++;
        }
        return rgb(Atlant.COPPER[Math.max(1, Math.min(6, tone))]);
    }

    static void copperRivet(Paint p, double x, double y) {
        p.rect(x, y, x + 1, y + 1, rgb(Atlant.COPPER[0]));
        p.rect(x, y, x + 0.5, y + 0.5, rgb(Atlant.COPPER[6]));
    }

    static void ironRivet(Paint p, double x, double y) {
        p.rect(x, y, x + 1, y + 1, rgb(Paint.SLATE_IRON[0]));
        p.rect(x, y, x + 0.5, y + 0.5, rgb(Paint.SLATE_IRON[5]));
    }

    static void antiqueBrassRivet(Paint p, double x, double y) {
        p.rect(x, y, x + 1, y + 1, rgb(Paint.BRASS[1]));
        p.rect(x, y, x + 0.5, y + 0.5, rgb(Paint.BRASS[4]));
    }

    // ========================================================================= Copper Tank textures

    /** Weathered riveted copper & dark-bronze boiler drum with subtle patina only at panel seams. */
    static Paint tankCopper() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, copperTone(i, j, 811));
            }
        }
        // Horizontal rolled boiler seams (bevel light/shadow)
        for (double y : new double[]{0, 8}) {
            p.rect(0, y, 16, y + 0.5, rgb(Atlant.COPPER[1]));
            p.rect(0, y + 0.5, 16, y + 1.0, rgb(Atlant.COPPER[5]));
        }
        p.rect(0, 15.0, 16, 15.5, rgb(Atlant.COPPER[1]));
        p.rect(0, 15.5, 16, 16.0, rgb(Atlant.COPPER[0]));
        // Subtle vertical seams with muted dark bronze bevels and occasional patina flecks
        for (double x : new double[]{4, 12}) {
            p.rect(x, 0, x + 0.5, 16, rgb(Atlant.COPPER[1]));
            p.rect(x + 0.5, 0, x + 1.0, 16, rgb(Atlant.COPPER[4]));
            p.rect(x + 0.5, 1.5, x + 1.0, 2.5, rgb(Atlant.PATINA[0]));
            p.rect(x + 0.5, 9.5, x + 1.0, 10.5, rgb(Atlant.PATINA[1]));
        }
        for (double x : new double[]{1.5, 5.5, 9.5, 13.5}) {
            antiqueBrassRivet(p, x, 2.0);
            antiqueBrassRivet(p, x, 10.0);
        }
        return p;
    }

    /** Riveted octagonal copper/bronze boiler hatch plate with corner bolts & faint patina flecks. */
    static Paint tankLid() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                int c = copperTone(i, j, 821);
                if (Atlant.hash(i / 2, j / 2, 823) > 0.965) {
                    c = rgb(Atlant.PATINA[1]);
                }
                p.px(i, j, c);
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[0]));
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, rgb(Atlant.COPPER[6]));
        p.frame(2.5, 2.5, 13.5, 13.5, 0.5, rgb(Atlant.COPPER[1]));
        p.slab(4.5, 4.5, 11.5, 11.5, Atlant.COPPER, 3.0, 1);
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            ironRivet(p, r[0], r[1]);
        }
        return p;
    }

    /** Deepslate-gray forged steel tie-rod & mounting lug metal. */
    static Paint tankIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, 831);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        for (double y = 2.5; y < 14; y += 5) {
            ironRivet(p, 2.5, y);
            ironRivet(p, 12.5, y);
        }
        return p;
    }

    /** Warm antique brass for gauge bezel & side sight-ring collars. */
    static Paint tankBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 2.9, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, 835, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        return p;
    }

    /** Cutout-safe (binary alpha) boiler glass: crisp frosted-azure rim, frost corner flecks, diagonal specular glints. */
    static Paint tankGlass() {
        Paint p = new Paint(16, 16, K);
        p.frame(0, 0, 16, 16, 0.5, rgb(0x7ac4d8));
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, rgb(0x286478));
        p.line(2.5, 13.0, 5.5, 10.0, 0.5, rgb(0xf2fbff));
        p.line(3.5, 13.5, 5.0, 12.0, 0.5, rgb(0xd0f0f8));
        p.line(10.5, 4.5, 13.0, 2.0, 0.5, rgb(0xf2fbff));
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4 - i; j++) {
                if (Atlant.hash(i, j, 841) > 0.55) {
                    p.px(p.w - 2 - i, j + 1, rgb(Paint.FROST[4]));
                    p.px(i + 1, p.h - 2 - j, rgb(Paint.FROST[3]));
                }
            }
        }
        return p;
    }

    /** High-contrast warm parchment dial face for tank pressure gauge (recessed behind brass bezel). */
    static Paint tankGauge() {
        Paint p = new Paint(16, 16, K);
        p.rect(0, 0, 16, 16, rgb(Paint.SLATE_IRON[1]));
        p.disc(8, 8, 7.6, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 6.8, rgb(Paint.CREAM[5]));
        p.ring(8, 8, 6.3, 6.8, rgb(Paint.CREAM[2]));
        for (int k = 0; k <= 8; k++) {
            double a = Math.toRadians(150 + k * 30);
            int c = k >= 6 ? rgb(0xb8241a) : rgb(0x221c18);
            p.line(8 + Math.cos(a) * 4.4, 8 + Math.sin(a) * 4.4, 8 + Math.cos(a) * 5.8, 8 + Math.sin(a) * 5.8, 0.65, c);
        }
        p.disc(8, 8, 1.1, rgb(Paint.SLATE_IRON[0]));
        return p;
    }

    // ========================================================================= Formation Bus textures

    /** Deepslate-gray forged steel for Mason's Formation Bus gantry legs & chassis. */
    static Paint formIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.6, 1);
        p.grain(0, 0, 16, 16, 0.06, 851);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.SLATE_IRON[4]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            ironRivet(p, r[0], r[1]);
        }
        return p;
    }

    /** Weathered riveted copper with subtle patina seam accents for Formation Bus lintel & bulkhead. */
    static Paint formCopper() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                int c = copperTone(i, j, 855);
                if (Atlant.hash(i, j, 856) > 0.975) {
                    c = rgb(Atlant.PATINA[0]);
                } else if (Atlant.hash(i / 2, j / 2, 857) > 0.94) {
                    c = rgb(Atlant.COPPER[2]);
                }
                p.px(i, j, c);
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[1]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(Atlant.COPPER[1]));
        p.rect(0.5, 8.0, 15.5, 8.5, rgb(Atlant.COPPER[6]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            copperRivet(p, r[0], r[1]);
        }
        return p;
    }

    /** Warm walnut/spruce workbench plank grain. */
    static Paint formWood() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRONZE, 2.2, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.09, 860, true);
        p.grain(0, 0, 16, 16, 0.04, 861);
        for (double y : new double[]{4, 8, 12}) {
            p.rect(0, y, 16, y + 0.5, rgb(Paint.BRONZE[0]));
            p.rect(0, y + 0.5, 16, y + 1.0, rgb(Paint.BRONZE[3]));
        }
        return p;
    }

    /** Warm antique brass for gantry stanchions & hoist trolley. */
    static Paint formBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 2.9, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.06, 864, true);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            antiqueBrassRivet(p, r[0], r[1]);
        }
        return p;
    }

    // ========================================================================= Annihilation Bus textures

    /** Deepslate-gray forged steel with bolted plate seams for Hewer's Annihilation Bus hood & jaw-guard. */
    static Paint anniIron() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.SLATE_IRON, 2.7, 1);
        p.grain(0, 0, 16, 16, 0.07, 870);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.SLATE_IRON[1]));
        p.rect(0.5, 0.5, 15.5, 1.0, rgb(Paint.SLATE_IRON[5]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(Paint.SLATE_IRON[0]));
        p.rect(0.5, 8.0, 15.5, 8.5, rgb(Paint.SLATE_IRON[4]));
        for (double[] r : new double[][]{{1.5, 2.0}, {13.5, 2.0}, {1.5, 13.0}, {13.5, 13.0}}) {
            ironRivet(p, r[0], r[1]);
        }
        return p;
    }

    /** Weathered oxidized copper/bronze with subtle verdigris patina & rivets for the bastion hull. */
    static Paint anniCopper() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double n = Atlant.noise((i / 2) * 2, (j / 2) * 2, 6, 875);
                int c;
                if (n > 0.80) {
                    c = rgb(Atlant.PATINA[0 + (int) Math.min(1, (n - 0.80) * 6)]);
                } else {
                    c = rgb(Atlant.COPPER[Math.max(1, Math.min(5, 2 + (int) Math.round((n - 0.1) * 4)))]);
                }
                p.px(i, j, c);
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[0]));
        p.rect(0.5, 7.5, 15.5, 8.0, rgb(Atlant.COPPER[0]));
        for (double[] r : new double[][]{{2.0, 2.0}, {13.0, 2.0}, {2.0, 13.0}, {13.0, 13.0}}) {
            copperRivet(p, r[0], r[1]);
        }
        return p;
    }

    /** Warm antique brass for Annihilation Bus collar rib & side coil brackets. */
    static Paint anniBrass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, Paint.BRASS, 2.9, 1);
        p.grain(0, 0, 16, 16, 0.05, 878);
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        antiqueBrassRivet(p, 2.0, 7.5);
        antiqueBrassRivet(p, 13.0, 7.5);
        return p;
    }

    /** Glowing violet/cyan Plasma Temper coil crystal for Annihilation Bus side mount. */
    static Paint anniCoil() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                int cx = i / 2;
                int cy = j / 2;
                double band = ((cx + cy * 2) % 6) / 5.0;
                int c = cx >= 6 && cx <= 9 ? Paint.CYAN[4]
                        : cx == 5 || cx == 10 ? Paint.VIOLET[5]
                        : band > 0.5 ? Paint.VIOLET[4] : Paint.VIOLET[2];
                p.px(i, j, rgb(c));
            }
        }
        p.rect(7.5, 0, 8.5, 16, rgb(Paint.CYAN[5]));
        p.rect(0, 0, 16, 1.0, rgb(Paint.VIOLET[1]));
        p.rect(0, 15.0, 16, 16, rgb(Paint.VIOLET[1]));
        return p;
    }

    // ========================================================================= Plasma Temper & Belt (UNTOUCHED)

    /**
     * The Plasma Temper (item): a steel bar in temper colours from straw to blue along its length, and in its middle a bead of the
     * Wither's plasma - black with a violet glow - set in a brass bezel.
     */
    static Paint temper() {
        Paint p = new Paint(16, 16, K);
        int[] temper = {0xe2cf8a, 0xc99a52, 0xa0632e, 0x7c4a9a, 0x4a5cc8, 0x6f8fd0, 0xa9bcd8};
        p.line(2.5, 13.5, 13.5, 2.5, 5, rgb(0x1a1a22));
        for (int k = 0; k < 22; k++) {
            double t0 = k / 22.0, t1 = (k + 1) / 22.0;
            int c = Paint.ramp(temper, t0);
            p.line(3 + 10 * t0, 13 - 10 * t0, 3 + 10 * t1, 13 - 10 * t1, 3.6, rgb(c));
        }
        p.line(3.4, 11.6, 11.6, 3.4, 0.6, Paint.argb(170, 0xffffff));
        p.disc(8, 8, 3.6, rgb(Paint.BRASS[2]));
        p.disc(8, 8, 3.0, rgb(Paint.BRASS[5]));
        p.disc(8, 8, 2.2, rgb(0x120a18));
        p.disc(7.4, 7.4, 1.2, rgb(Paint.VIOLET[3]));
        p.disc(7.1, 7.1, 0.5, rgb(Paint.VIOLET[6]));
        p.px((int) (9 * K), (int) (8.6 * K), rgb(Paint.VIOLET[4]));
        return p;
    }

    /**
     * The Plasma Temper worn as a belt (an entity texture for the Annihilation Bus's iron golem): u 0..24 the strap - temper colours
     * running along it between dark edges, a rivet every six units; u 24..32 the buckle - a brass ring round the violet plasma bead.
     */
    static Paint belt() {
        Paint p = new Paint(32, 8, K);
        int[] temper = {0xe2cf8a, 0xc99a52, 0xa0632e, 0x7c4a9a, 0x4a5cc8, 0x6f8fd0, 0xa9bcd8};
        for (int i = 0; i < 24 * K; i++) {
            double t = Math.abs(((i + 0.5) / K) / 12.0 - 1);
            p.rect(i / (double) K, 0, (i + 1) / (double) K, 8, rgb(Paint.ramp(temper, 1 - t)));
        }
        p.rect(0, 0, 24, 1, rgb(0x1a1a22));
        p.rect(0, 7, 24, 8, rgb(0x1a1a22));
        p.rect(0, 1, 24, 1.5, Paint.argb(150, 0xffffff));
        for (double x = 2.5; x < 24; x += 6) {
            p.rect(x, 3.5, x + 1, 4.5, rgb(0x2a2a33));
        }
        p.rect(24, 0, 32, 8, rgb(Paint.BRASS[3]));
        p.rect(24.5, 0.5, 31.5, 7.5, rgb(Paint.BRASS[5]));
        p.rect(25.5, 1.5, 30.5, 6.5, rgb(0x120a18));
        p.rect(26.5, 2.5, 29, 5, rgb(Paint.VIOLET[3]));
        p.rect(27, 3, 28, 4, rgb(Paint.VIOLET[6]));
        return p;
    }

    static void cutout(String model) throws Exception {
        renderType(model, "cutout");
    }

    static void renderType(String model, String type) throws Exception {
        Path f = Models.RES.resolve("models/block/" + model + ".json");
        String s = Files.readString(f, StandardCharsets.UTF_8);
        Files.writeString(f, s.replace("  \"ambientocclusion\": false,\n", "  \"ambientocclusion\": false,\n  \"render_type\": \"minecraft:" + type + "\",\n"),
                StandardCharsets.UTF_8);
    }

    /** Adds an octagonal prism aligned vertically (along Y) built from 3 non-overlapping axis-aligned slices (zero coplanar faces). */
    static void addOctVertical(List<Models.El> l, double lo, double hi, double y0, double y1, double cut, String sideTex, String capTex) {
        l.add(Models.e(lo + cut, y0, lo, hi - cut, y1, hi).all("#" + sideTex).face("up", "#" + capTex).face("down", "#" + capTex));
        l.add(Models.e(lo, y0, lo + cut, lo + cut, y1, hi - cut).all("#" + sideTex).face("up", "#" + capTex).face("down", "#" + capTex));
        l.add(Models.e(hi - cut, y0, lo + cut, hi, y1, hi - cut).all("#" + sideTex).face("up", "#" + capTex).face("down", "#" + capTex));
    }

    static void models() throws Exception {
        // ---- 1. Copper Tank (octagonal riveted boiler, external dark cast-iron corner tie-rods, cutout glass, centered front gauge)
        String[][] tt = {{"copper", "tank_copper"}, {"lid", "tank_lid"}, {"iron", "tank_iron"}, {"brass", "tank_brass"},
                {"glass", "tank_glass"}, {"gauge", "tank_gauge"}};
        List<Models.El> t = new ArrayList<>();
        // Bottom stepped octagonal base (y=0..3.5)
        addOctVertical(t, 1.5, 14.5, 0.0, 1.0, 1.0, "iron", "lid");
        addOctVertical(t, 1.25, 14.75, 1.0, 3.0, 1.25, "copper", "lid");
        addOctVertical(t, 1.75, 14.25, 3.0, 3.5, 1.0, "iron", "lid");

        // Top stepped octagonal cap (y=12.5..16.0)
        addOctVertical(t, 1.75, 14.25, 12.5, 13.0, 1.0, "iron", "lid");
        addOctVertical(t, 1.25, 14.75, 13.0, 15.0, 1.25, "copper", "lid");
        addOctVertical(t, 2.25, 13.75, 15.0, 16.0, 1.25, "copper", "lid");

        // 4 external dark cast-iron corner tie-rods + bolted upper/lower mounting lugs & hex nuts
        for (double[] c : new double[][]{{1.1, 1.1}, {13.7, 1.1}, {1.1, 13.7}, {13.7, 13.7}}) {
            double rx0 = c[0], rz0 = c[1];
            double mx0 = rx0 < 8 ? 1.0 : 13.2, mz0 = rz0 < 8 ? 1.0 : 13.2;
            // Lower hex nut + bolted lug + rod + upper lug + hex nut (strictly non-overlapping Y intervals)
            t.add(Models.e(rx0, 1.4, rz0, rx0 + 1.2, 2.0, rz0 + 1.2).all("#brass"));
            t.add(Models.e(mx0, 2.0, mz0, mx0 + 1.8, 3.75, mz0 + 1.8).all("#iron"));
            t.add(Models.e(rx0, 3.75, rz0, rx0 + 1.2, 12.25, rz0 + 1.2).all("#iron"));
            t.add(Models.e(mx0, 12.25, mz0, mx0 + 1.8, 14.0, mz0 + 1.8).all("#iron"));
            t.add(Models.e(rx0, 14.0, rz0, rx0 + 1.2, 14.6, rz0 + 1.2).all("#brass"));
        }

        // Thin bronze/iron corner seal posts framing the 4 cutout glass panes (x/z 2.0..14.0, y=3.5..12.5)
        for (double[] s : new double[][]{{2.0, 2.0}, {13.0, 2.0}, {2.0, 13.0}, {13.0, 13.0}}) {
            t.add(Models.e(s[0], 3.5, s[1], s[0] + 1.0, 12.5, s[1] + 1.0).only("#iron", "north", "south", "east", "west"));
        }
        t.add(Models.e(3.0, 3.5, 2.0, 13.0, 12.5, 2.0).only("#glass", "north", "south"));
        t.add(Models.e(3.0, 3.5, 14.0, 13.0, 12.5, 14.0).only("#glass", "north", "south"));
        t.add(Models.e(2.0, 3.5, 3.0, 2.0, 12.5, 13.0).only("#glass", "east", "west"));
        t.add(Models.e(14.0, 3.5, 3.0, 14.0, 12.5, 13.0).only("#glass", "east", "west"));

        // Integrated centered front & rear pressure gauges (north & south faces, centered at 8,8)
        t.add(Models.e(5.75, 5.75, 1.4, 10.25, 10.25, 2.2).only("#iron", "south", "east", "west", "up", "down"));
        t.add(Models.e(5.75, 9.45, 0.9, 10.25, 10.25, 1.4).all("#brass"));
        t.add(Models.e(5.75, 5.75, 0.9, 10.25, 6.55, 1.4).all("#brass"));
        t.add(Models.e(5.75, 6.55, 0.9, 6.55, 9.45, 1.4).all("#brass"));
        t.add(Models.e(9.45, 6.55, 0.9, 10.25, 9.45, 1.4).all("#brass"));
        t.add(Models.e(6.55, 6.55, 1.35, 9.45, 9.45, 1.4).face("north", "#gauge", 0, 0, 16, 16));

        t.add(Models.e(5.75, 5.75, 13.8, 10.25, 10.25, 14.6).only("#iron", "north", "east", "west", "up", "down"));
        t.add(Models.e(5.75, 9.45, 14.6, 10.25, 10.25, 15.1).all("#brass"));
        t.add(Models.e(5.75, 5.75, 14.6, 10.25, 6.55, 15.1).all("#brass"));
        t.add(Models.e(5.75, 6.55, 14.6, 6.55, 9.45, 15.1).all("#brass"));
        t.add(Models.e(9.45, 6.55, 14.6, 10.25, 9.45, 15.1).all("#brass"));
        t.add(Models.e(6.55, 6.55, 14.6, 9.45, 9.45, 14.65).face("south", "#gauge", 0, 0, 16, 16));

        Models.model("copper_tank", false, "tank_copper", tt, t.toArray(new Models.El[0]));
        cutout("copper_tank");

        // ---- 2. Mason's Formation Bus (open cast-iron & riveted copper gantry, walnut workbench, overhead hoist crane)
        String[][] ft = {{"iron", "form_iron"}, {"copper", "form_copper"}, {"wood", "form_wood"}, {"brass", "form_brass"}};
        List<Models.El> f = new ArrayList<>();
        // Antique-brass front corner stanchions + lower walnut shelf (z=0.5..2.5)
        f.add(Models.e(0.5, 0.0, 0.5, 2.75, 4.5, 2.5).all("#brass"));
        f.add(Models.e(13.25, 0.0, 0.5, 15.5, 4.5, 2.5).all("#brass"));
        f.add(Models.e(2.75, 1.0, 1.0, 13.25, 2.0, 2.5).all("#wood"));

        // Lower cast-iron base chassis (z=2.5..15.5) + golem plinth platform (z=5.0..7.75)
        f.add(Models.e(0.5, 0.0, 2.5, 15.5, 2.0, 15.5).all("#iron"));
        f.add(Models.e(4.0, 2.0, 5.0, 12.0, 3.2, 7.75).all("#iron"));

        // Mason's walnut workbench counter (z=1.0..5.0, cleanly in front of golem at z=6.3)
        f.add(Models.e(2.75, 3.5, 1.0, 13.25, 4.75, 5.0).all("#wood"));

        // Open stepped cast-iron gantry A-frame legs (front z=2.5..4.5, rear z=11.5..13.5)
        f.add(Models.e(0.5, 2.0, 2.5, 2.5, 13.5, 4.5).only("#iron", "north", "south", "east", "west"));
        f.add(Models.e(13.5, 2.0, 2.5, 15.5, 13.5, 4.5).only("#iron", "north", "south", "east", "west"));
        f.add(Models.e(0.5, 2.0, 11.5, 2.5, 13.5, 13.5).only("#iron", "north", "south", "east", "west"));
        f.add(Models.e(13.5, 2.0, 11.5, 15.5, 13.5, 13.5).only("#iron", "north", "south", "east", "west"));

        // Side lower & upper gantry rails (inset x so zero coplanar faces with legs)
        for (double[] lr : new double[][]{{0.75, 2.25}, {13.75, 15.25}}) {
            f.add(Models.e(lr[0], 2.0, 4.5, lr[1], 3.5, 11.5).only("#iron", "east", "west", "up"));
            f.add(Models.e(lr[0], 12.0, 4.5, lr[1], 13.5, 11.5).only("#iron", "east", "west", "up", "down"));
        }

        // Stepped top arch shoulders & rear iron tie-bar
        f.add(Models.e(0.5, 13.5, 4.0, 2.5, 15.5, 12.0).all("#iron"));
        f.add(Models.e(13.5, 13.5, 4.0, 15.5, 15.5, 12.0).all("#iron"));
        f.add(Models.e(2.5, 14.0, 11.0, 13.5, 15.5, 12.5).only("#iron", "north", "south", "up", "down"));

        // Riveted copper arch lintel & inner jambs
        f.add(Models.e(2.5, 11.5, 2.5, 13.5, 13.5, 4.5).all("#copper"));
        f.add(Models.e(2.5, 4.5, 3.0, 3.5, 11.5, 4.5).only("#copper", "north", "south", "east", "west"));
        f.add(Models.e(12.5, 4.5, 3.0, 13.5, 11.5, 4.5).only("#copper", "north", "south", "east", "west"));

        // Rear riveted copper network bulkhead + flush network socket plate at z=16 + delivery chute (z=7.75..11.5)
        f.add(Models.e(2.5, 2.0, 11.5, 13.5, 11.5, 15.5).all("#copper"));
        f.add(Models.e(2.5, 11.5, 12.5, 13.5, 13.5, 15.5).all("#iron"));
        f.add(Models.e(4.5, 4.5, 15.5, 11.5, 11.5, 16.0).only("#iron", "south", "east", "west", "up", "down"));
        f.add(Models.e(4.25, 2.0, 7.75, 11.75, 4.25, 11.5).only("#wood", "up", "north", "east", "west"));
        f.add(Models.e(3.5, 2.0, 7.75, 4.25, 5.75, 11.5).all("#copper"));
        f.add(Models.e(11.75, 2.0, 7.75, 12.5, 5.75, 11.5).all("#copper"));

        // Overhead antique-brass hoist crane
        f.add(Models.e(2.5, 14.0, 6.75, 13.5, 15.25, 8.75).only("#brass", "north", "south", "up", "down"));
        f.add(Models.e(6.75, 12.75, 7.0, 9.25, 14.0, 8.5).only("#brass", "north", "south", "east", "west", "down"));
        f.add(Models.e(7.5, 11.8, 7.5, 8.5, 12.75, 8.5).only("#iron", "north", "south", "east", "west", "down"));

        Models.model("formation_bus", false, "form_copper", ft, f.toArray(new Models.El[0]));
        cutout("formation_bus");
        Models.facing6("formation_bus", "formation_bus");
        Models.item("formation_bus", "formation_bus");

        // ---- 3. Hewer's Annihilation Bus (armored iron hood, bolted angled blast-shield, oxidized bronze bastion & Plasma coil)
        String[][] at = {{"iron", "anni_iron"}, {"copper", "anni_copper"}, {"brass", "anni_brass"}, {"coil", "anni_coil"}};
        List<Models.El> a = new ArrayList<>();
        // Armored base chassis: front lower slab under niche (y=0..1.5, z=1.5..6.0) + recessed floor (y=1.5..2.0, z=2.0..6.0) + main base (z=6.0..15.5)
        a.add(Models.e(1.0, 0.0, 1.5, 15.0, 1.5, 6.0).all("#iron"));
        a.add(Models.e(2.5, 1.5, 2.0, 13.5, 2.0, 6.0).only("#iron", "up", "north"));
        a.add(Models.e(1.0, 0.0, 6.0, 15.0, 2.0, 15.5).all("#iron"));

        // Armored dark cast-iron arched front hood (z=1.5..5.5) framing the golem cleanly
        a.add(Models.e(1.25, 1.5, 1.5, 2.75, 11.8, 5.5).only("#iron", "north", "south", "east", "west", "up"));
        a.add(Models.e(13.25, 1.5, 1.5, 14.75, 11.8, 5.5).only("#iron", "north", "south", "east", "west", "up"));
        a.add(Models.e(2.25, 11.8, 1.5, 13.75, 13.25, 5.5).all("#iron"));
        a.add(Models.e(3.75, 13.25, 2.0, 12.25, 14.5, 5.5).all("#iron"));
        a.add(Models.e(2.75, 2.0, 5.5, 13.25, 11.8, 6.0).only("#iron", "north"));

        // Raised antique-brass structural collar rib (z=5.5..7.0)
        a.add(Models.e(1.0, 2.0, 5.5, 2.25, 12.0, 7.0).all("#brass"));
        a.add(Models.e(13.75, 2.0, 5.5, 15.0, 12.0, 7.0).all("#brass"));
        a.add(Models.e(2.0, 12.0, 5.5, 3.25, 14.0, 7.0).all("#brass"));
        a.add(Models.e(12.75, 12.0, 5.5, 14.0, 14.0, 7.0).all("#brass"));
        a.add(Models.e(3.25, 14.0, 5.5, 12.75, 15.5, 7.0).all("#brass"));

        // Stepped arched oxidized-copper/bronze rear bastion hull (z=7.0..15.5) + flush pipe docking pad at z=16
        a.add(Models.e(1.5, 2.0, 7.0, 14.5, 12.0, 15.5).all("#copper").face("north", "#iron"));
        a.add(Models.e(2.75, 12.0, 7.0, 13.25, 14.0, 15.0).all("#copper"));
        a.add(Models.e(4.5, 14.0, 7.0, 11.5, 15.25, 14.5).all("#copper"));
        a.add(Models.e(4.5, 4.5, 15.5, 11.5, 11.5, 16.0).only("#iron", "south", "east", "west", "up", "down"));

        // Stepped cast-iron jaw-guard apron + protruding vertical jaw teeth (lower max y <= 3.6, teeth y <= 4.1)
        a.add(Models.e(2.25, 1.55, 1.0, 13.75, 3.55, 1.65).only("#iron", "north", "south", "up", "down"));
        a.add(Models.e(2.5, 0.85, 0.45, 13.5, 2.55, 1.0).all("#iron"));
        for (double rx : new double[]{3.25, 6.0, 9.0, 11.75}) {
            a.add(Models.e(rx, 1.15, 0.15, rx + 1.0, 2.55, 0.45).all("#iron"));
            a.add(Models.e(rx, 2.55, 0.25, rx + 1.0, 4.05, 1.0).all("#iron"));
        }
        // Hinge bosses attaching blast-shield cleanly to cheeks (y=1.55 so never coplanar with base top y=1.5)
        a.add(Models.e(1.0, 1.55, 1.25, 2.25, 3.75, 3.25).only("#iron", "north", "south", "west", "up", "down"));
        a.add(Models.e(13.75, 1.55, 1.25, 15.0, 3.75, 3.25).only("#iron", "north", "south", "east", "up", "down"));

        // Side Plasma Temper coil mounts (east & west, z=9.0..13.0)
        for (boolean west : new boolean[]{true, false}) {
            double bx0 = west ? 0.5 : 14.5, bx1 = west ? 1.5 : 15.5;
            double cx0 = west ? 0.6 : 14.5, cx1 = west ? 1.5 : 15.4;
            String out = west ? "west" : "east";
            a.add(Models.e(bx0, 4.0, 9.0, bx1, 5.0, 13.0).only("#brass", "north", "south", "up", "down", out));
            a.add(Models.e(bx0, 10.25, 9.0, bx1, 11.25, 13.0).only("#brass", "north", "south", "up", "down", out));
            a.add(Models.e(cx0, 5.0, 10.25, cx1, 10.25, 11.75).only("#coil", "north", "south", out).glow(12));
            a.add(Models.e(cx0, 5.0, 9.1, cx1, 10.25, 9.6).only("#iron", "north", "south", out));
        }

        Models.model("annihilation_bus", false, "anni_copper", at, a.toArray(new Models.El[0]));
        cutout("annihilation_bus");
        Models.facing6("annihilation_bus", "annihilation_bus");
        Models.item("annihilation_bus", "annihilation_bus");
    }

    static void all() throws Exception {
        tankCopper().save(Forge2.tex("tank_copper"));
        tankLid().save(Forge2.tex("tank_lid"));
        tankIron().save(Forge2.tex("tank_iron"));
        tankBrass().save(Forge2.tex("tank_brass"));
        tankGlass().save(Forge2.tex("tank_glass"));
        tankGauge().save(Forge2.tex("tank_gauge"));

        formIron().save(Forge2.tex("form_iron"));
        formCopper().save(Forge2.tex("form_copper"));
        formWood().save(Forge2.tex("form_wood"));
        formBrass().save(Forge2.tex("form_brass"));

        anniIron().save(Forge2.tex("anni_iron"));
        anniCopper().save(Forge2.tex("anni_copper"));
        anniBrass().save(Forge2.tex("anni_brass"));
        anniCoil().save(Forge2.tex("anni_coil"));

        temper().save(Forge2.RES.resolve("textures/item/plasma_temper.png").toFile());
        belt().save(Forge2.RES.resolve("textures/entity/plasma_belt.png").toFile());
        models();
    }
}
