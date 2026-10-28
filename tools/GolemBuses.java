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
        double t = Atlant.hash(i / 2, j / 2, seed) * 0.5 + Atlant.noise(i, j, 8, seed + 1) * 0.5;
        return rgb(Atlant.COPPER[(int) Math.max(1, Math.min(7, 2 + t * 5))]);
    }

    /** Copper planks laid along, joints darker and teal patina creeping out of the seams. */
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
        // patina bleeding along the joints
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double v = (j + 0.5) / K, u = (i + 0.5) / K;
                double d = Math.min(Math.min(Math.abs(v - 8.5), Math.abs(v - 0.5)), Math.min(Math.abs(u - 4.5), Math.abs(u - 12.5)));
                if (d < 1.6 && Atlant.hash(i, j, 812) > 0.55 + d * 0.25) {
                    p.px(i, j, rgb(Atlant.PATINA[1 + (int) (Atlant.hash(i, j, 813) * 4)]));
                }
            }
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
                double t = Atlant.hash(i / 2, j / 6, 831) * 0.6 + Atlant.noise(i, j, 6, 832) * 0.4;
                p.px(i, j, rgb(Atlant.COPPER[(int) Math.max(2, Math.min(7, 3 + t * 4))]));
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

    /** The gauge's face: a brass bezel, a cream dial with ticks, a red needle a little past the middle. */
    static Paint tankGauge() {
        Paint p = new Paint(16, 16, K);
        p.disc(8, 8, 8, rgb(Paint.BRASS[3]));
        p.disc(8, 8, 7, rgb(Paint.BRASS[5]));
        p.disc(8, 8, 5.8, rgb(Paint.CREAM[5]));
        for (int k = 0; k <= 8; k++) {
            double a = Math.toRadians(150 + k * 30);
            p.line(8 + Math.cos(a) * 4.4, 8 + Math.sin(a) * 4.4, 8 + Math.cos(a) * 5.4, 8 + Math.sin(a) * 5.4, 0.6, rgb(0x2a2420));
        }
        double a = Math.toRadians(290);
        p.line(8, 8, 8 + Math.cos(a) * 4.8, 8 + Math.sin(a) * 4.8, 1.0, rgb(0xc8281e));
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
        // four plates with seams and patina
        p.rect(7.75, 2, 8.25, 14, rgb(Atlant.COPPER[0]));
        p.rect(2, 7.75, 14, 8.25, rgb(Atlant.COPPER[0]));
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / K, v = (j + 0.5) / K;
                double d = Math.min(Math.abs(u - 8), Math.abs(v - 8));
                if (u > 2 && u < 14 && v > 2 && v < 14 && d < 1.8 && Atlant.hash(i, j, 853) > 0.6 + d * 0.2) {
                    p.px(i, j, rgb(Atlant.PATINA[1 + (int) (Atlant.hash(i, j, 854) * 4)]));
                }
            }
        }
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
        for (double[] r : new double[][]{{3.5, 3.5}, {11.5, 3.5}, {3.5, 11.5}, {11.5, 11.5}}) {
            rivet(p, r[0], r[1]);
        }
        if (front) {
            // the mouth (model y 1.5..8.5 -> v 7.5..14.5): dark inside, the brass lip is a model of its own
            p.rect(4, 7.5, 12, 14.5, rgb(0x120c0a));
            p.rect(4, 7.5, 12, 8.5, rgb(0x2a1a14));
        }
        return p;
    }

    /** Dark iron plates with a brass band across and patina-studded corners (the Annihilation Bus). */
    static Paint annihilation() {
        Paint p = new Paint(16, 16, K);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double t = Atlant.hash(i / 2, j / 2, 861) * 0.5 + Atlant.noise(i, j, 8, 862) * 0.5;
                p.px(i, j, rgb(Paint.IRON[(int) Math.max(2, Math.min(5, 2 + t * 4))]));
            }
        }
        p.frame(0, 0, 16, 16, 0.5, rgb(Paint.IRON[1]));
        p.rect(0, 7, 16, 9, rgb(Paint.BRASS[4]));
        p.rect(0, 7, 16, 7.5, rgb(Paint.BRASS[6]));
        p.rect(0, 8.5, 16, 9, rgb(Paint.BRASS[2]));
        for (double x = 1.5; x < 16; x += 4.5) {
            p.rect(x, 7.5, x + 1, 8.5, rgb(0x23252b));
        }
        for (double[] c : new double[][]{{1, 1}, {13.5, 1}, {1, 13.5}, {13.5, 13.5}}) {
            p.rect(c[0], c[1], c[0] + 1.5, c[1] + 1.5, rgb(Atlant.PATINA[3]));
            p.rect(c[0] + 0.5, c[1] + 0.5, c[0] + 1, c[1] + 1, rgb(0x23252b));
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

        // ---- the Formation Bus: the casing and the brass lip of its mouth
        String[][] ft = {{"side", "formation_side"}, {"front", "formation_front"}, {"brass", "sat_brass"}};
        List<Models.El> f = new ArrayList<>();
        f.add(Models.e(0, 0, 0, 16, 16, 16).only("#side", "south", "east", "west", "up", "down").face("north", "#front"));
        f.add(Models.e(3, 1, -1.5, 13, 2, 0).all("#brass"));
        f.add(Models.e(3, 8, -1.5, 13, 9, 0).all("#brass"));
        f.add(Models.e(3, 2, -1.5, 4, 8, 0).only("#brass", "north", "east", "west"));
        f.add(Models.e(12, 2, -1.5, 13, 8, 0).only("#brass", "north", "east", "west"));
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
        annihilation().save(Forge2.tex("annihilation_side"));
        niche().save(Forge2.tex("annihilation_niche"));
        models();
    }
}
