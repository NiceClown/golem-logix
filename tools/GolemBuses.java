import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Copper Tank, the Formation Bus and the Annihilation Bus (after the concept art: vanilla pixels with Create-like brass and
 * riveted copper). The tank: a copper chest's base and lid with teal patina in the seams, riveted copper posts with brass caps,
 * clear glass with a few highlights, a brass pressure gauge. The Formation Bus: riveted copper panels in a brass frame with rune
 * inlays, a brass mouth low on the front (the golem is the renderer's). The Annihilation Bus: dark iron plates with a brass band,
 * a brass-framed niche in the front with a plank back wall (the iron golem is the renderer's).
 */
final class GolemBuses {
    static final int K = Forge2.K;

    private GolemBuses() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    static int copper(int i, int j, int seed) {
        double t = Atlant.noise(i, j, 14, seed + 1);
        int tone = 4 + (int) Math.round(t * 2 - 0.4);
        if (Atlant.hash(i / 5, j, seed) > 0.94) {
            tone++;
        }
        return rgb(Atlant.COPPER[Math.max(3, Math.min(7, tone))]);
    }

    /** Copper planks laid along, clean joints with a thin line of teal patina in the upright seams. */
    static Paint tankCopper() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, copper(i, j, 811));
            }
        }
        for (double y : new double[]{0, 8}) {
            p.rect(0, y, 16, y + 0.5, rgb(Atlant.COPPER[1]));
            p.rect(0, y + 0.5, 16, y + 1, rgb(Atlant.COPPER[7]));
        }
        for (double x : new double[]{4, 12}) {
            p.rect(x, 0, x + 0.5, 16, rgb(Atlant.PATINA[2]));
            p.rect(x + 0.5, 0, x + 1, 16, rgb(Atlant.PATINA[4]));
        }
        return p;
    }

    /** The lid's top: one copper plate, a ring of rivets, frost at the rim. */
    static Paint tankLid() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, copper(i, j, 821));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(Atlant.COPPER[1]));
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, rgb(Atlant.COPPER[7]));
        for (double x = 2.5; x < 14; x += 3.5) {
            for (double y : new double[]{2.5, 13}) {
                rivet(p, x, y);
                rivet(p, y, x);
            }
        }
        for (int i = 0; i < p.w; i++) {
            if (Atlant.hash(i, 0, 822) > 0.5) {
                p.px(i, 0, rgb(Paint.FROST[5]));
            }
        }
        return p;
    }

    static void rivet(Paint p, double x, double y) {
        p.rect(x, y, x + 1, y + 1, rgb(Atlant.COPPER[0]));
        p.rect(x, y, x + 0.5, y + 0.5, rgb(Atlant.COPPER[8]));
    }

    static void brassRivet(Paint p, double x, double y) {
        p.rect(x, y, x + 1, y + 1, rgb(Paint.BRASS[2]));
        p.rect(x, y, x + 0.5, y + 0.5, rgb(Paint.BRASS[6]));
    }

    /** A post: copper running up, a column of brass rivets every 3 pixels in every 4-pixel column (so any slice of it has some). */
    static Paint tankPost() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.noise(i, j, 10, 832);
                p.px(i, j, rgb(Atlant.COPPER[4 + (int) Math.round(t * 2 - 0.4)]));
            }
        }
        for (double x = 0; x < 16; x += 4) {
            p.rect(x, 0, x + 0.5, 16, rgb(Atlant.COPPER[1]));
            for (double y = 1.5; y < 16; y += 3) {
                brassRivet(p, x + 1.75, y);
            }
        }
        return p;
    }

    /** Clear glass: transparent but for a light rim, two highlight streaks and a speck of frost in a corner (cutout). */
    static Paint tankGlass() {
        Paint p = new Paint(16, 16, K);
        p.frame(0, 0, 16, 16, 0.5, rgb(0xcfe2ea));
        p.line(3, 13, 6, 10, 0.5, rgb(0xffffff));
        p.line(4, 14, 5.5, 12.5, 0.5, rgb(0xffffff));
        p.line(11, 4, 13, 2, 0.5, rgb(0xf2fbff));
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6 - i; j++) {
                if (Atlant.hash(i, j, 841) > 0.45) {
                    p.px(p.w - 1 - i, j, rgb(Paint.FROST[4 + (int) (Atlant.hash(i, j, 842) * 3)]));
                }
            }
        }
        return p;
    }

    /** The gauge's face: a brass bezel, a cream dial with ticks and a red zone near full (the needle is drawn by the renderer). */
    static Paint tankGauge() {
        Paint p = new Paint(16, 16, K);
        p.disc(8, 8, 8, rgb(Paint.BRASS[3]));
        p.disc(8, 8, 7, rgb(Paint.BRASS[5]));
        p.disc(8, 8, 5.8, rgb(Paint.CREAM[5]));
        for (int k = 0; k <= 8; k++) {
            double a = Math.toRadians(150 + k * 30);
            p.line(8 + Math.cos(a) * 4.4, 8 + Math.sin(a) * 4.4, 8 + Math.cos(a) * 5.4, 8 + Math.sin(a) * 5.4, 0.6, rgb(0x2a2420));
        }
        for (int k = 7; k <= 8; k++) {
            double a = Math.toRadians(150 + k * 30);
            p.line(8 + Math.cos(a) * 4.4, 8 + Math.sin(a) * 4.4, 8 + Math.cos(a) * 5.4, 8 + Math.sin(a) * 5.4, 0.8, rgb(0xc8281e));
        }
        p.disc(8, 8, 1, rgb(0x2a2420));
        return p;
    }

    /** A riveted copper panel in a brass frame, rune inlays in the frame (the Formation Bus), with or without the mouth. */
    static Paint formation(boolean front) {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px(i, j, copper(i, j, front ? 851 : 852));
            }
        }
        // four bevelled plates: light on the top and left edges, dark on the bottom and right, seams between them
        for (double[] q : new double[][]{{2, 2, 8, 8}, {8, 2, 14, 8}, {2, 8, 8, 14}, {8, 8, 14, 14}}) {
            p.rect(q[0], q[1], q[2], q[1] + 0.5, rgb(Atlant.COPPER[7]));
            p.rect(q[0], q[1], q[0] + 0.5, q[3], rgb(Atlant.COPPER[7]));
            p.rect(q[0], q[3] - 0.5, q[2], q[3], rgb(Atlant.COPPER[2]));
            p.rect(q[2] - 0.5, q[1], q[2], q[3], rgb(Atlant.COPPER[2]));
        }
        // a small patina diamond where the seams cross
        p.rect(7.5, 7, 8.5, 9, rgb(Atlant.PATINA[3]));
        p.rect(7, 7.5, 9, 8.5, rgb(Atlant.PATINA[3]));
        p.rect(7.5, 7.5, 8.5, 8.5, rgb(Atlant.PATINA[5]));
        // the brass frame with dark bolts at the corners and rune inlays along it
        p.frame(0, 0, 16, 16, 2, rgb(Paint.BRASS[4]));
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.BRASS[2]));
        p.frame(1.5, 1.5, 14.5, 14.5, 0.5, rgb(Paint.BRASS[6]));
        for (double[] c : new double[][]{{0.5, 0.5}, {14, 0.5}, {0.5, 14}, {14, 14}}) {
            p.rect(c[0], c[1], c[0] + 1.5, c[1] + 1.5, rgb(0x23252b));
            p.rect(c[0] + 0.25, c[1] + 0.25, c[0] + 0.75, c[1] + 0.75, rgb(0x5d6573));
        }
        for (double x = 3; x < 13; x += 2.5) {
            p.rect(x, 0.75, x + 1, 1.25, rgb(Paint.CYAN[3]));
            p.rect(x + 0.5, 14.75, x + 1.5, 15.25, rgb(Paint.CYAN[3]));
        }
        for (double y = 3; y < 13; y += 2.5) {
            p.rect(0.75, y, 1.25, y + 1, rgb(Paint.CYAN[3]));
            p.rect(14.75, y + 0.5, 15.25, y + 1.5, rgb(Paint.CYAN[3]));
        }
        for (double[] r : new double[][]{{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
            rivet(p, r[0], r[1]);
        }
        if (front) {
            // the mouth (model y 2..7 -> v 9..14): dark inside, the brass lip is a model of its own
            p.rect(4, 9, 12, 14, rgb(0x120c0a));
            p.rect(4, 9, 12, 9.5, rgb(0x2a1a14));
        }
        return p;
    }

    /** The alcove the Formation Bus's golem grows out of: darkened copper, upright seams, a rivet in each corner. */
    static Paint alcove() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.noise(i, j, 12, 881);
                p.px(i, j, rgb(Atlant.COPPER[1 + (int) Math.round(t)]));
            }
        }
        for (double x : new double[]{4, 12}) {
            p.rect(x, 0, x + 0.5, 16, rgb(Atlant.COPPER[0]));
        }
        for (double[] r : new double[][]{{1.5, 1.5}, {13.5, 1.5}, {1.5, 13.5}, {13.5, 13.5}}) {
            p.rect(r[0], r[1], r[0] + 1, r[1] + 1, rgb(Atlant.COPPER[5]));
        }
        return p;
    }

    /** Dark iron plates with a brass band across and bolted corners (the Annihilation Bus). */
    static Paint annihilation() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.noise(i, j, 12, 862);
                p.px(i, j, rgb(Paint.IRON[3 + (int) Math.round(t * 1.4 - 0.2)]));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.IRON[1]));
        // two plates above and below the band, each with a light top edge
        p.rect(0.5, 0.5, 15.5, 1, rgb(Paint.IRON[5]));
        p.rect(0.5, 9, 15.5, 9.5, rgb(Paint.IRON[5]));
        p.rect(0.5, 6.5, 15.5, 7, rgb(Paint.IRON[2]));
        p.rect(0.5, 15, 15.5, 15.5, rgb(Paint.IRON[2]));
        p.rect(0, 7, 16, 9, rgb(Paint.BRASS[4]));
        p.rect(0, 7, 16, 7.5, rgb(Paint.BRASS[6]));
        p.rect(0, 8.5, 16, 9, rgb(Paint.BRASS[2]));
        for (double x = 1.5; x < 16; x += 4.5) {
            p.rect(x, 7.5, x + 1, 8.5, rgb(0x23252b));
        }
        for (double[] c : new double[][]{{1.5, 2}, {13.5, 2}, {1.5, 12.5}, {13.5, 12.5}}) {
            p.rect(c[0], c[1], c[0] + 1, c[1] + 1, rgb(Paint.IRON[1]));
            p.rect(c[0], c[1], c[0] + 0.5, c[1] + 0.5, rgb(Atlant.PATINA[4]));
        }
        return p;
    }

    /** The niche's back wall: dark planks, a lantern-warm streak from above. */
    static Paint niche() {
        Paint p = new Paint(16, 16, K);
        int[] tones = {0x2a1a10, 0x34200f, 0x3e2814, 0x482f18};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K;
                int plank = (int) (u / 4);
                double t = Atlant.hash(plank, j / 3, 871) * 0.6 + Atlant.noise(i, j, 6, 872) * 0.4;
                p.px(i, j, rgb(tones[(int) Math.min(3, t * 4)]));
                if (u % 4 < 0.5) {
                    p.px(i, j, rgb(0x170e08));
                }
            }
        }
        return p;
    }

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
        Path f = Models.RES.resolve("models/block/" + model + ".json");
        String s = Files.readString(f, StandardCharsets.UTF_8);
        Files.writeString(f, s.replace("  \"ambientocclusion\": false,\n", "  \"ambientocclusion\": false,\n  \"render_type\": \"minecraft:cutout\",\n"),
                StandardCharsets.UTF_8);
    }

    static void models() throws Exception {
        // ---- the tank (inside: base top at 4, lid underside at 13)
        String[][] tt = {{"copper", "tank_copper"}, {"lid", "tank_lid"}, {"post", "tank_post"}, {"glass", "tank_glass"},
                {"brass", "sat_brass"}, {"bolt", "pipe_bolt"}, {"gauge", "tank_gauge"}};
        List<Models.El> t = new ArrayList<>();
        t.add(Models.e(0, 0, 0, 16, 4, 16).only("#copper", "north", "south", "east", "west").face("down", "#lid").face("up", "#lid"));
        t.add(Models.e(0.5, 13, 0.5, 15.5, 16, 15.5).only("#copper", "north", "south", "east", "west").face("up", "#lid").face("down", "#lid"));
        t.add(Models.e(7, 10.5, -0.5, 9, 14, 0.5).all("#copper"));
        for (double[] c : new double[][]{{0.5, 0.5}, {13, 0.5}, {0.5, 13}, {13, 13}}) {
            t.add(Models.e(c[0], 4, c[1], c[0] + 2.5, 13, c[1] + 2.5).only("#post", "north", "south", "east", "west"));
            for (double y : new double[]{4, 11.6}) {
                t.add(Models.e(c[0] - 0.25, y, c[1] - 0.25, c[0] + 2.75, y + 1.4, c[1] + 2.75).all("#brass"));
            }
        }
        t.add(Models.e(3, 4, 1.5, 13, 13, 1.5).only("#glass", "north", "south"));
        t.add(Models.e(3, 4, 14.5, 13, 13, 14.5).only("#glass", "north", "south"));
        t.add(Models.e(1.5, 4, 3, 1.5, 13, 13).only("#glass", "east", "west"));
        t.add(Models.e(14.5, 4, 3, 14.5, 13, 13).only("#glass", "east", "west"));
        // the gauge on the front-right post, on a short brass stem
        t.add(Models.e(13.9, 8, -0.4, 14.6, 8.7, 0.25).all("#brass"));
        t.add(Models.e(12.6, 6.6, -1.2, 15.9, 9.9, -0.4).all("#brass").face("north", "#gauge", 0, 0, 16, 16));
        Models.model("copper_tank", false, "tank_copper", tt, t.toArray(new Models.El[0]));
        cutout("copper_tank");

        // ---- the Formation Bus: the casing round the golem's alcove, the brass sill it rises from, the brass lip of the mouth
        String[][] ft = {{"side", "formation_side"}, {"front", "formation_front"}, {"brass", "sat_brass"}, {"alcove", "formation_alcove"}};
        List<Models.El> f = new ArrayList<>();
        f.add(Models.e(0, 0, 0, 16, 8.5, 16).only("#side", "south", "east", "west", "down").face("north", "#front").face("up", "#side"));
        f.add(Models.e(0, 8.5, 0, 3.5, 16, 16).only("#side", "south", "west", "up").face("north", "#front").face("east", "#alcove"));
        f.add(Models.e(12.5, 8.5, 0, 16, 16, 16).only("#side", "south", "east", "up").face("north", "#front").face("west", "#alcove"));
        f.add(Models.e(3.5, 15, 0, 12.5, 16, 16).only("#side", "south", "up").face("north", "#front").face("down", "#alcove"));
        f.add(Models.e(3.5, 8.5, 3, 12.5, 15, 16).only("#side", "south").face("north", "#alcove"));
        f.add(Models.e(3.5, 8.5, -0.75, 12.5, 9.25, 3).only("#brass", "north", "up", "east", "west"));
        f.add(Models.e(3, 1, -1.5, 13, 2, 0).all("#brass"));
        f.add(Models.e(3, 7, -1.5, 13, 8, 0).all("#brass"));
        f.add(Models.e(3, 2, -1.5, 4, 7, 0).only("#brass", "north", "east", "west"));
        f.add(Models.e(12, 2, -1.5, 13, 7, 0).only("#brass", "north", "east", "west"));
        Models.model("formation_bus", false, "formation_side", ft, f.toArray(new Models.El[0]));
        Models.facing6("formation_bus", "formation_bus");
        Models.item("formation_bus", "formation_bus");

        // ---- the Annihilation Bus: the casing round a brass-framed niche (x 2..14, y 2..14, z 0..5)
        String[][] at = {{"side", "annihilation_side"}, {"brass", "sat_brass"}, {"niche", "annihilation_niche"}};
        List<Models.El> a = new ArrayList<>();
        a.add(Models.e(0, 0, 0, 2, 16, 16).only("#side", "south", "west", "up", "down").face("north", "#brass").face("east", "#niche"));
        a.add(Models.e(14, 0, 0, 16, 16, 16).only("#side", "south", "east", "up", "down").face("north", "#brass").face("west", "#niche"));
        a.add(Models.e(2, 0, 0, 14, 2, 16).only("#side", "south", "down").face("north", "#brass").face("up", "#niche"));
        a.add(Models.e(2, 14, 0, 14, 16, 16).only("#side", "south", "up").face("north", "#brass").face("down", "#niche"));
        a.add(Models.e(2, 2, 5, 14, 14, 16).only("#side", "south").face("north", "#niche"));
        Models.model("annihilation_bus", false, "annihilation_side", at, a.toArray(new Models.El[0]));
        Models.facing6("annihilation_bus", "annihilation_bus");
        Models.item("annihilation_bus", "annihilation_bus");
    }

    static void all() throws Exception {
        tankCopper().save(Forge2.tex("tank_copper"));
        tankLid().save(Forge2.tex("tank_lid"));
        tankPost().save(Forge2.tex("tank_post"));
        tankGlass().save(Forge2.tex("tank_glass"));
        tankGauge().save(Forge2.tex("tank_gauge"));
        formation(false).save(Forge2.tex("formation_side"));
        formation(true).save(Forge2.tex("formation_front"));
        alcove().save(Forge2.tex("formation_alcove"));
        annihilation().save(Forge2.tex("annihilation_side"));
        niche().save(Forge2.tex("annihilation_niche"));
        temper().save(Forge2.RES.resolve("textures/item/plasma_temper.png").toFile());
        belt().save(Forge2.RES.resolve("textures/entity/plasma_belt.png").toFile());
        models();
    }
}
