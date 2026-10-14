import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The third generation of Golem Logix graphics: machine upgrades (module cards), the Chronometer screen with a phosphor Wither,
 * the Recipe Monitor (a porthole with a purple vortex), the Cryo-Calculator "matrix rain", the moving parts of the calculator and
 * the coprocessor, the cooler on the Saturator roof.
 */
final class Gen3 {
    static final Path RES = Forge2.RES;
    static final String NS = Forge2.NS;
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, BLUE = Paint.BLUE, CREAM = Paint.CREAM;
    static final int[] RED = {0x200404, 0x4a0808, 0x7c1010, 0xb81c1c, 0xe83a2a, 0xff7a5a, 0xffd0c0};
    static final int[] STEEL = {0x0e1724, 0x1b2a3b, 0x2b4560, 0x3f6080, 0x6c93b8, 0x9cc0dc, 0xd6ecfa};
    static final int[] PHOSPHOR = {0x021208, 0x05301a, 0x0b5a2c, 0x169448, 0x30d070, 0x84f4a4, 0xe0ffe8};
    static final int[] VERDIGRIS = {0x0e2f2e, 0x164b48, 0x216c68, 0x2e928c, 0x48b6ad, 0x76d5cc, 0xb2f4ee};
    static final int[] GOLD = {0x3a2604, 0x6a4808, 0x9a6e10, 0xc8981e, 0xe8c040, 0xf8dc78, 0xfff4c8};

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    static File itemTex(String name) {
        return RES.resolve("textures/item/" + name + ".png").toFile();
    }

    // ============================================================================================ upgrades
    /** A 32x32 module card: a plate with a brass edging, gilded contacts at the bottom, patina and rivets. */
    static Paint card(int[] body, double mid, long seed) {
        Paint p = new Paint(16, 16, 2);
        // Shadow under the cartridge
        p.tint(2.5, 3.5, 14.5, 14.5, argb(75, 0x000000));
        // Brass frame
        p.slab(1.4, 2.4, 14.2, 13.6, BRASS, 3.2, 1);
        p.slab(2.0, 3.0, 13.6, 13.0, body, mid, 1);
        p.grain(2.0, 3.0, 13.6, 13.0, 0.05, seed);

        // Gilded connector contacts at the bottom
        for (double x = 2.8; x < 12.8; x += 1.3) {
            p.rect(x, 12.0, x + 0.8, 13.6, at(GOLD, 5.0));
            p.rect(x, 13.0, x + 0.8, 13.6, at(GOLD, 2.8));
        }

        // Brass rivets in the corners
        p.rivet(2.6, 3.6, 0.42, BRASS);
        p.rivet(13.0, 3.6, 0.42, BRASS);
        return p;
    }

    /** Compressed Co-Processor: a multi-section cryo radiator: copper fins with patina, gold contacts, a frosty cryo conduit and a glowing teal core. */
    static Paint compressedCoprocessor() {
        Paint p = card(COPPER, 2.6, 3101);
        // Oxidized copper with patina on the body
        p.disc(3.6, 5.0, 0.8, at(VERDIGRIS, 4.4));
        p.disc(12.0, 10.0, 0.7, at(VERDIGRIS, 4.2));

        // Radiator shaft
        p.slab(3.0, 4.2, 12.6, 11.4, IRON, 0.9, 1);

        // A stack of copper cooling fins
        for (int i = 0; i < 6; i++) {
            double y = 4.8 + i * 1.05;
            p.rect(3.4, y, 12.2, y + 0.65, at(COPPER, 4.2));
            p.rect(3.4, y + 0.65, 12.2, y + 0.8, at(COPPER, 1.4));
            if (i % 2 == 1) {
                p.rect(3.4, y, 4.8, y + 0.65, at(VERDIGRIS, 4.8));
            }
        }

        // A glowing cryo core in the central groove
        p.rect(6.8, 4.4, 8.8, 11.2, at(CYAN, 5.5));
        p.glowRect(6.6, 4.2, 9.0, 11.4, 1.4, at(CYAN, 4.2), 120);

        // A frosty cooling conduit around the radiator
        p.line(2.4, 6.2, 2.4, 10.4, 0.6, at(FROST, 5.0));
        p.line(13.2, 5.4, 13.2, 9.8, 0.6, at(FROST, 5.0));
        p.frostLine(1.8, 13.8, 2.8, 0.7, 3102);

        // A gear on the chassis
        p.gearStatic(12.0, 4.8, 1.0, 6, 0.18, BRASS, 3.8);

        // Ice sparks
        p.sparkle(4.0, 3.8, rgb(0xFFFFFF));
        p.sparkle(11.8, 10.8, rgb(0xFFFFFF));
        return p;
    }

    /** The Compressed Co-Processor half way through the Create assembly line: three fins of six, the cryo core not lit yet. */
    static Paint incompleteCoprocessor() {
        Paint p = card(COPPER, 2.6, 3101);
        p.slab(3.0, 4.2, 12.6, 11.4, IRON, 0.9, 1);
        for (int i = 0; i < 3; i++) {
            double y = 4.8 + i * 1.05;
            p.rect(3.4, y, 12.2, y + 0.65, at(COPPER, 4.2));
            p.rect(3.4, y + 0.65, 12.2, y + 0.8, at(COPPER, 1.4));
        }
        // the empty groove of the cryo core and a bare conduit
        p.rect(6.8, 4.4, 8.8, 11.2, at(IRON, 1.2));
        p.line(2.4, 6.2, 2.4, 10.4, 0.6, at(FROST, 3.0));
        return p;
    }

    /**
     * Fuzzy Card: a copper cartridge with a frosted glass lens; behind the frost three blurred shapes (a square, a disc, a diamond)
     * that the lens takes for one, and a cyan "~" on its brass rim.
     */
    static Paint fuzzyCard() {
        Paint p = card(COPPER, 2.7, 3401);
        p.disc(3.6, 4.8, 0.8, at(VERDIGRIS, 4.5));
        p.slab(2.8, 4.0, 12.8, 11.4, IRON, 0.8, 1);
        // the brass lens ring and the frosted glass in it
        p.disc(8.0, 7.6, 3.6, at(BRASS, 3.8));
        p.disc(8.0, 7.6, 3.0, at(FROST, 4.6));
        // three shapes seen through the frost, all in one soft cyan
        p.rect(5.6, 6.0, 7.0, 7.4, at(CYAN, 3.4));
        p.disc(9.6, 6.6, 0.8, at(CYAN, 3.6));
        p.line(7.4, 9.2, 8.2, 8.4, 0.5, at(CYAN, 3.2));
        p.line(8.2, 8.4, 9.0, 9.2, 0.5, at(CYAN, 3.2));
        p.glowRect(5.0, 5.2, 11.0, 10.0, 1.2, at(CYAN, 4.4), 70);
        // a "~" on the rim: same enough
        p.line(5.4, 11.9, 6.6, 11.3, 0.35, at(CYAN, 5.4));
        p.line(6.6, 11.3, 7.9, 11.9, 0.35, at(CYAN, 5.4));
        p.line(7.9, 11.9, 9.2, 11.3, 0.35, at(CYAN, 5.4));
        p.line(9.2, 11.3, 10.5, 11.9, 0.35, at(CYAN, 5.4));
        p.sparkle(11.6, 4.2, rgb(0xFFFFFF));
        return p;
    }

    /** Autocraft Module: a brass cartridge with an anvil, clock gears, vacuum tubes with glowing sparks and gold contacts. */
    static Paint autocraftModule() {
        Paint p = card(BRONZE, 3.2, 3201);
        // Copper plates with patina
        p.slab(2.8, 4.0, 12.8, 11.4, COPPER, 3.0, 1);
        p.disc(3.8, 4.8, 0.7, at(VERDIGRIS, 4.5));

        // Mechanism shelf: a gear train on the right
        p.gearStatic(10.8, 8.2, 1.6, 8, 0.25, BRASS, 4.2);
        p.gearStatic(11.4, 5.8, 1.2, 6, 0.2, IRON, 3.2);

        // A plaque with an anvil
        p.slab(3.2, 4.6, 8.8, 10.8, BRASS, 3.6, 1);
        p.frame(3.2, 4.6, 8.8, 10.8, 0.3, at(BRASS, 1.8));

        // An anvil silhouette of blued steel
        p.rect(4.0, 5.6, 8.0, 6.6, at(IRON, 1.2)); // top
        p.rect(4.8, 6.6, 7.2, 8.0, at(IRON, 1.6)); // waist
        p.rect(4.2, 8.0, 7.8, 9.6, at(IRON, 1.0)); // base
        p.line(4.0, 5.6, 8.0, 5.6, 0.25, at(IRON, 4.0)); // anvil highlight

        // Vacuum indicator tubes (amber and teal) with filaments
        // The amber tube on top
        p.rect(3.6, 3.4, 7.2, 4.4, at(IRON, 0.4));
        p.rect(4.0, 3.6, 6.8, 4.2, argb(220, 0xF8B830));
        p.glowRect(4.0, 3.6, 6.8, 4.2, 0.8, argb(255, 0xFFC040), 100);

        // The teal tube on the right
        p.rect(8.8, 3.6, 12.4, 4.6, at(IRON, 0.4));
        p.rect(9.2, 3.8, 12.0, 4.4, at(CYAN, 5.4));
        p.glowRect(9.2, 3.8, 12.0, 4.4, 0.8, at(CYAN, 4.2), 110);

        // Latch lever
        p.rect(3.4, 10.4, 6.8, 11.2, at(BRASS, 4.8));
        p.rivet(4.2, 10.8, 0.25, BRASS);
        return p;
    }

    /** Redstone Module: a heavy oxidized copper cartridge, a central faceted redstone crystal, toggle switches, analog dials and glowing buses. */
    static Paint redstoneModule() {
        Paint p = card(COPPER, 2.8, 3301);
        // Patina on the copper body
        p.disc(3.6, 4.8, 0.8, at(VERDIGRIS, 4.6));
        p.disc(12.0, 10.2, 0.7, at(VERDIGRIS, 4.4));

        // The inner recess of the chassis
        p.slab(2.8, 4.0, 12.8, 11.4, IRON, 0.8, 1);

        // Miniature needle dials / potentiometers
        p.disc(4.6, 5.6, 1.1, at(BRASS, 3.6));
        p.disc(4.6, 5.6, 0.8, at(CREAM, 4.8));
        p.line(4.6, 5.6, 5.2, 5.2, 0.25, at(RED, 4.5)); // a little red needle

        p.disc(11.0, 5.6, 1.1, at(BRASS, 3.6));
        p.disc(11.0, 5.6, 0.8, at(CREAM, 4.8));
        p.line(11.0, 5.6, 10.6, 5.1, 0.25, at(RED, 4.5));

        // Toggle switches at the bottom
        p.rect(4.0, 9.4, 5.2, 10.8, at(BRASS, 4.2));
        p.rect(4.4, 9.0, 4.8, 10.2, at(IRON, 4.5));

        p.rect(10.4, 9.4, 11.6, 10.8, at(BRASS, 4.2));
        p.rect(10.8, 9.0, 11.2, 10.2, at(IRON, 4.5));

        // Redstone energy conductors with pulsing
        p.line(4.6, 6.7, 6.8, 7.8, 0.35, at(RED, 5.0));
        p.line(11.0, 6.7, 8.8, 7.8, 0.35, at(RED, 5.0));
        p.line(6.8, 9.6, 8.8, 9.6, 0.35, at(RED, 5.0));

        // A faceted redstone crystal in the middle with runes and a glow
        p.poly(new double[]{7.8, 6.4, 7.8, 9.2}, new double[]{5.2, 7.6, 10.0, 7.6}, at(RED, 5.4));
        p.poly(new double[]{7.8, 6.8, 7.8, 8.8}, new double[]{5.8, 7.6, 9.4, 7.6}, at(RED, 6.0));
        p.glowRect(6.2, 5.2, 9.4, 10.0, 1.4, at(RED, 4.5), 130);

        // Glowing redstone sparks
        p.sparkle(6.2, 6.2, at(RED, 5.5));
        p.sparkle(9.4, 8.8, at(RED, 5.5));
        return p;
    }

    static void upgrades() throws Exception {
        compressedCoprocessor().save(itemTex("compressed_coprocessor"));
        incompleteCoprocessor().save(itemTex("incomplete_compressed_coprocessor"));
        autocraftModule().save(itemTex("autocraft_module"));
        redstoneModule().save(itemTex("redstone_module"));
        fuzzyCard().save(itemTex("fuzzy_card"));
        for (String n : new String[]{"compressed_coprocessor", "incomplete_compressed_coprocessor", "autocraft_module", "redstone_module", "fuzzy_card"}) {
            Forge2.write(RES.resolve("models/item/" + n + ".json"),
                    "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/" + n + "\"\n  }\n}\n");
        }
    }

    // ============================================================================================ Chronometer device
    /** Item screen frames: 0-3 rest (3 slightly lower), 4-5 charge, 6-8 the skull flies at the viewer, 9 a flash, 10-12 a crack. */
    static final int CHRONO_FRAMES = 13;
    /** Frame order and duration (ticks): a long rest, a quick volley, a slowly fading crack. */
    static final int[][] CHRONO_TIMELINE = {{0, 12}, {1, 12}, {2, 12}, {3, 12}, {0, 10}, {4, 6}, {5, 6}, {6, 2}, {7, 2}, {8, 2},
            {9, 2}, {10, 8}, {11, 5}, {12, 6}};

    /**
     * Portable Chronometer: a brass pocket device (not a flask): a watch crown on top, buttons on the sides,
     * a green screen in an iron frame, a Source gauge and a speaker grille. On the screen a flat Wither charges a volley every few seconds,
     * the skull flies straight at the viewer, the glass flashes and cracks (an animated 32x32 texture).
     */
    static void chronometerItem() throws Exception {
        Paint strip = new Paint(32, 32 * CHRONO_FRAMES, 1);
        for (int f = 0; f < CHRONO_FRAMES; f++) {
            strip.paste(chronoFrame(f), 0, 32 * f);
        }
        strip.save(itemTex("portable_chronometer"));
        StringBuilder sb = new StringBuilder("{\n  \"animation\": {\n    \"frames\": [");
        for (int i = 0; i < CHRONO_TIMELINE.length; i++) {
            sb.append(i == 0 ? "" : ", ").append("{\"index\": ").append(CHRONO_TIMELINE[i][0]).append(", \"time\": ")
                    .append(CHRONO_TIMELINE[i][1]).append("}");
        }
        Forge2.write(RES.resolve("textures/item/portable_chronometer.png.mcmeta"), sb.append("]\n  }\n}\n").toString());
        Forge2.write(RES.resolve("models/item/portable_chronometer.json"),
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/portable_chronometer\"\n  }\n}\n");
    }

    static Paint chronoFrame(int f) {
        Paint p = new Paint(32, 32, 1);
        // The watch crown with knurling and a hanging ring on top
        p.ring(16, 1.5, 2.5, 3.8, at(BRASS, 3.5)); // hanging ring
        p.rect(14, 3, 18, 6, at(BRASS, 2.2));
        p.slab(12, 1, 20, 5, BRASS, 4.0, 1);
        for (int x = 13; x < 20; x += 2) {
            p.rect(x, 2, x + 1, 4, at(BRASS, 1.8));
        }

        // Body: oxidized copper with teal patina and a brass edging
        p.slab(4, 5, 28, 31, COPPER, 3.4, 1);
        p.grain(4, 5, 28, 31, 0.05, 7301);

        // Patina on the chronometer body
        p.disc(6, 8, 1.8, at(VERDIGRIS, 4.6));
        p.disc(26, 28, 1.8, at(VERDIGRIS, 4.4));
        p.disc(6, 27, 1.4, at(VERDIGRIS, 4.5));

        // Rounded bevelled body corners (an octagon of a pocket watch)
        for (int[] c : new int[][]{{4, 5}, {5, 5}, {4, 6}, {27, 5}, {26, 5}, {27, 6},
                {4, 30}, {5, 30}, {4, 29}, {27, 30}, {26, 30}, {27, 29}}) {
            p.px(c[0], c[1], 0);
        }

        // Push buttons on the sides of the chronometer
        p.slab(1, 11, 4, 15, BRASS, 3.0, 1);
        p.slab(28, 11, 31, 15, BRASS, 3.0, 1);

        // A brass bezel around the screen
        p.rect(6, 7, 26, 24, at(BRASS, 4.2));
        p.frame(6, 7, 26, 24, 1, at(BRASS, 5.6));

        // The screen frame and the picture tube
        p.rect(7, 8, 25, 23, at(IRON, 0.6));
        p.frame(7, 8, 25, 23, 1, at(IRON, 2.4));
        p.rect(7, 22, 25, 23, at(BRASS, 4.8));

        screen(p, 8, 9, f);

        // Body rivets
        for (double[] r : new double[][]{{5.5, 7.5}, {26.5, 7.5}, {5.5, 28.5}, {26.5, 28.5}}) {
            p.px((int) r[0], (int) r[1], at(BRASS, 5.8));
        }

        // The Source gauge (purple glass) and the speaker grille
        p.rect(7, 25, 14, 28, at(IRON, 0.8));
        p.rect(8, 26, 12, 27, at(VIOLET, 4.5));
        p.px(8, 26, at(VIOLET, 6.0));
        p.glowRect(8, 26, 12, 27, 0.5, at(VIOLET, 4.5), 70);

        for (int y = 25; y < 29; y += 2) {
            p.rect(17, y, 25, y + 1, at(BRASS, 1.4));
        }

        // Frost on the rim
        p.frostLine(7, 25, 7, 0.4, 7302);
        return p.outlined(rgb(0x1a1206));
    }

    /** A 16x12 green screen (top left corner sx, sy): scanlines, the Wither and the volley frame. */
    static void screen(Paint p, int sx, int sy, int f) {
        int bg = at(PHOSPHOR, 1.0), scan = at(PHOSPHOR, 0.5);
        for (int y = 0; y < 12; y++) {
            p.rect(sx, sy + y, sx + 16, sy + y + 1, y % 2 == 0 ? bg : scan);
        }
        // the picture tube corners are rounded
        for (int[] c : new int[][]{{0, 0}, {15, 0}, {0, 11}, {15, 11}}) {
            p.px(sx + c[0], sy + c[1], at(IRON, 0.6));
        }
        if (f == 9) {
            // the impact flash: the screen turns white
            p.rect(sx, sy, sx + 16, sy + 12, at(PHOSPHOR, 6.0));
            p.rect(sx + 3, sy + 2, sx + 13, sy + 10, rgb(0xFFFFFF));
            return;
        }
        boolean charge = f == 4 || f == 5;
        int bob = f == 2 || f == 3 ? 1 : 0;
        witherPixels(p, sx, sy + bob, charge, f == 5, f == 1);
        if (f >= 6 && f <= 8) {
            // the skull flies out of the mouth at the viewer: it grows and goes down to the middle of the screen
            int s = f == 6 ? 2 : f == 7 ? 4 : 8;
            int cy = sy + (f == 6 ? 5 : f == 7 ? 5 : 6);
            skullPixels(p, sx + 8 - s / 2, cy - s / 2, s);
        }
        if (f >= 10) {
            // a crack from the impact point; dimmer with every frame
            int a = f == 10 ? 240 : f == 11 ? 180 : 110;
            crackPixels(p, sx, sy, argb(a, 0xF0FFF4));
            if (f == 11) {
                java.util.Random r = new java.util.Random(7311);
                for (int i = 0; i < 18; i++) {
                    p.over(sx + r.nextInt(16), sy + r.nextInt(12), argb(150, 0xB8FFC8));
                }
            }
        }
    }

    /** A front-facing pixel Wither: three heads on a shoulder beam, a spine with ribs, a tail. */
    static void witherPixels(Paint p, int sx, int sy, boolean charge, boolean open, boolean glance) {
        int body = at(PHOSPHOR, 4.4), edge = at(PHOSPHOR, 3.2), dark = at(PHOSPHOR, 0.2);
        int eye = charge ? at(PHOSPHOR, 6.0) : dark;
        // shoulder beam
        p.rect(sx + 2, sy + 5, sx + 14, sy + 6, edge);
        // side heads 3x3 (when "glancing" the eyes are shifted)
        for (int hx : new int[]{1, 12}) {
            p.rect(sx + hx, sy + 3, sx + hx + 3, sy + 6, body);
            p.px(sx + hx + (glance ? 0 : 1), sy + 4, eye);
        }
        // the central 5x4 head with a rounded crown
        p.rect(sx + 6, sy + 1, sx + 9, sy + 2, body);
        p.rect(sx + 5, sy + 2, sx + 10, sy + 5, body);
        p.px(sx + 6, sy + 3, eye);
        p.px(sx + 8, sy + 3, eye);
        p.px(sx + 7, sy + 4, open ? at(PHOSPHOR, 6.0) : dark);
        // spine, ribs, tail
        p.rect(sx + 7, sy + 6, sx + 8, sy + 11, edge);
        p.rect(sx + 5, sy + 7, sx + 10, sy + 8, body);
        p.rect(sx + 5, sy + 9, sx + 10, sy + 10, body);
        p.px(sx + 8, sy + 11, edge);
        if (charge) {
            // a charge halo around the central head
            for (int[] g : new int[][]{{4, 2}, {10, 2}, {7, 0}, {4, 4}, {10, 4}}) {
                p.over(sx + g[0], sy + g[1], argb(open ? 200 : 120, 0xC8FFD8));
            }
        }
    }

    /** A skull projectile as pixel drawings (2, 4 or 8 pixels): light, with a dark outline, eye sockets and teeth. */
    static void skullPixels(Paint p, int x, int y, int s) {
        String[] art = switch (s) {
            case 2 -> new String[]{"##", "##"};
            case 4 -> new String[]{
                    ".##.",
                    "#o#o",
                    "####",
                    ".#.#"};
            default -> new String[]{
                    "..####..",
                    ".######.",
                    "##oo#oo#",
                    "##oo#oo#",
                    "########",
                    ".##..##.",
                    ".#.#.#..",
                    "..####.."};
        };
        int body = at(PHOSPHOR, 6.0), dark = at(PHOSPHOR, 0.2), rim = at(PHOSPHOR, 0.8);
        int n = art.length;
        // a dark outline separates the skull from the Wither behind it
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < art[j].length(); i++) {
                if (art[j].charAt(i) == '#') {
                    for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                        p.px(x + i + d[0], y + j + d[1], rim);
                    }
                }
            }
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < art[j].length(); i++) {
                char ch = art[j].charAt(i);
                if (ch == '#') {
                    p.px(x + i, y + j, body);
                } else if (ch == 'o') {
                    p.px(x + i, y + j, dark);
                }
            }
        }
    }

    /** A crack on the screen glass: rays from the impact point. */
    static void crackPixels(Paint p, int sx, int sy, int c) {
        int cx = sx + 8, cy = sy + 6;
        int[][] rays = {{-7, -5}, {7, -6}, {-8, 2}, {8, 3}, {-3, 6}, {4, 6}, {1, -6}};
        for (int[] r : rays) {
            int steps = Math.max(Math.abs(r[0]), Math.abs(r[1]));
            for (int i = 0; i <= steps; i++) {
                int x = cx + Math.round((float) r[0] * i / steps), y = cy + Math.round((float) r[1] * i / steps);
                // a bend in the middle of a ray
                if (i > steps / 2) {
                    y += (r[0] > 0 ? 1 : -1) * ((i - steps / 2) / 3);
                }
                if (x >= sx && x < sx + 16 && y >= sy && y < sy + 12) {
                    p.over(x, y, c);
                }
            }
        }
        p.over(cx, cy, rgb(0xFFFFFF));
    }

    // ============================================================================================ matrix rain
    /**
     * The side panel of a working calculator: the same frame as cryo_panel, and on the screen columns of teal "glyphs" running
     * from top to bottom (a bright head, a fading tail). 22 frames of 2 ticks, a seamless loop.
     */
    static void calculatorMatrix() throws Exception {
        File base = RES.resolve("textures/block/cryo_panel.png").toFile();
        java.awt.image.BufferedImage panel = javax.imageio.ImageIO.read(base);
        int S = 32, frames = 22, sx0 = 6, sx1 = 26, sy0 = 6, sy1 = 26, h = sy1 - sy0;
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(S, S * frames, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(5501);
        int cols = sx1 - sx0;
        int[] head = new int[cols], speed = new int[cols], tail = new int[cols];
        boolean[] on = new boolean[cols];
        for (int c = 0; c < cols; c++) {
            head[c] = r.nextInt(h);
            speed[c] = r.nextInt(3) == 0 ? 2 : 1;
            tail[c] = 4 + r.nextInt(6);
            on[c] = c % 2 == 0 || r.nextInt(3) == 0;
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
                    out.setRGB(x, f * S + y, panel.getRGB(x, y));
                }
            }
            // the screen is darker, the grid of "character cells" barely visible
            for (int y = sy0; y < sy1; y++) {
                for (int x = sx0; x < sx1; x++) {
                    out.setRGB(x, f * S + y, (x + y) % 2 == 0 ? 0xFF07121C : 0xFF0A1824);
                }
            }
            for (int c = 0; c < cols; c++) {
                if (!on[c]) {
                    continue;
                }
                int hy = Math.floorMod(head[c] + f * speed[c], h);
                for (int t = 0; t <= tail[c]; t++) {
                    int y = Math.floorMod(hy - t, h);
                    // a "glyph" flickers: part of the tail pixels go out by the column pattern
                    if (t > 0 && glyph[c][y] == 0) {
                        continue;
                    }
                    double k = t == 0 ? 1.0 : 0.85 - 0.75 * t / (double) tail[c];
                    int col = t == 0 ? rgb(0xE2FFFE) : Paint.ramp(CYAN, Math.max(0.15, k));
                    out.setRGB(sx0 + c, f * S + sy0 + y, col);
                }
            }
        }
        File f = RES.resolve("textures/block/cryo_panel_matrix.png").toFile();
        javax.imageio.ImageIO.write(out, "png", f);
        Forge2.write(RES.resolve("textures/block/cryo_panel_matrix.png.mcmeta"),
                "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}\n");
    }

    // ============================================================================================ Recipe Monitor
    /** Front: a brass frame, a big round porthole (the middle is transparent, the vortex is behind it), bolts, gauges, frost. */
    static Paint monitorFront() {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.7, true, 6101);
        p.slab(0.6, 0.6, 15.4, 15.4, BRONZE, 2.4, 1);
        p.grain(0.6, 0.6, 15.4, 15.4, 0.05, 6102);
        double cx = 8, cy = 8.5;
        // the brass porthole ring with bolts
        p.disc(cx, cy, 6.6, at(BRASS, 1.2));
        p.sphere(cx, cy, 6.4, BRASS, 0.3, 0.95);
        p.ring(cx, cy, 5.2, 5.5, at(BRASS, 5.2));
        p.ring(cx, cy, 4.75, 5.2, at(IRON, 0.4));
        p.ring(cx, cy, 4.55, 4.75, at(CYAN, 5.0));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + 0.3;
            p.screw(cx + Math.cos(a) * 5.95, cy + Math.sin(a) * 5.95, 0.32, BRASS, a);
        }
        // clamp brackets on the sides of the ring, as in the reference
        for (double[] c : new double[][]{{cx - 6.9, cy}, {cx + 6.9, cy}}) {
            p.slab(c[0] - 0.7, c[1] - 1.4, c[0] + 0.7, c[1] + 1.4, BRASS, 3.4, 1);
        }
        p.slab(cx - 1.4, cy - 7.4, cx + 1.4, cy - 6.0, BRASS, 3.4, 1);
        p.slab(cx - 1.4, cy + 6.0, cx + 1.4, cy + 7.2, BRASS, 3.4, 1);
        // the middle is transparent: the vortex is sunk behind it
        int ix0 = (int) Math.floor((cx - 4.6) * K), ix1 = (int) Math.ceil((cx + 4.6) * K);
        int iy0 = (int) Math.floor((cy - 4.6) * K), iy1 = (int) Math.ceil((cy + 4.6) * K);
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                if (Math.hypot((i + 0.5) / K - cx, (j + 0.5) / K - cy) < 4.55) {
                    p.px(i, j, 0);
                }
            }
        }
        // a rune plate under the porthole and frost
        Terminals.tag(p, 5.4, 14.6, 5.2, 1.1, 14, VIOLET);
        for (double[] c : new double[][]{{1.2, 1.2}, {14.8, 1.2}, {1.2, 14.8}, {14.8, 14.8}}) {
            p.rivet(c[0], c[1], 0.4, BRASS);
        }
        p.frostLine(0, 16, 0, 0.7, 6103);
        p.icicle(2.6, 0.6, 1.3, 0.5);
        p.icicle(13.2, 0.6, 1.6, 0.5);
        return p;
    }

    /** A portal vortex frame: a spiral from purple to light blue, light in the middle. */
    static Paint portalFrame(int f, int frames) {
        Paint p = new Paint(16, 16, K);
        double rot = f * Math.PI * 2 / frames;
        Random r = new Random(6201);
        double[][] stars = new double[18][];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new double[]{r.nextDouble() * 8, r.nextDouble() * Math.PI * 2, 0.3 + r.nextDouble() * 0.7};
        }
        p.fill(0, 0, 16, 16, (u, v) -> {
            double dx = u - 8, dy = v - 8, d = Math.hypot(dx, dy) / 8.0, a = Math.atan2(dy, dx);
            double sw = Math.sin(a * 3 - d * 9 + rot * 3);
            double t = 0.15 + 0.55 * (1 - d) + 0.15 * sw;
            int c = Paint.mix(Paint.ramp(VIOLET, t), Paint.ramp(CYAN, t), 0.5 + 0.5 * Math.sin(a * 2 + rot * 2 - d * 4));
            if (d < 0.22) {
                c = Paint.mix(c, rgb(0xF2ECFF), (0.22 - d) / 0.22);
            }
            return c;
        });
        for (double[] s : stars) {
            double rr = (s[0] + f * 0.25 * s[2]) % 8, a = s[1] - rot * 2 * s[2];
            p.sparkle(8 + Math.cos(a) * rr, 8 + Math.sin(a) * rr, rgb(0xE8F6FF));
        }
        return p;
    }

    static Paint monitorSide(boolean left) {
        Paint p = Terminals.plate(left ? 6301 : 6302, BRONZE, 2.6);
        // a window with gears and a frosted tube, as on the side of the reference
        p.slab(2.4, 2.6, 13.6, 9.6, BRASS, 2.7, 1);
        p.slab(2.9, 3.1, 13.1, 9.1, IRON, 0.8, 1);
        p.gear(5.6, 6.3, 2.6, 10, 0.2, BRASS, 3.0);
        p.gear(9.8, 5.0, 1.7, 8, 0.5, COPPER, 3.0);
        p.gear(10.6, 8.0, 1.2, 7, 0.1, BRASS, 2.8);
        p.rect(2.9, 8.5, 13.1, 9.1, argb(120, 0xE0F4FF));
        // gauges and a row of signal lights between them (they wink)
        p.gauge(5.0, 12.3, 1.6, left ? 60 : 110, rgb(0xb02020));
        p.gauge(10.6, 12.3, 1.6, left ? 130 : 45, rgb(0xb02020));
        long salt = left ? 70 : 90;
        p.animate(7.0, 10.4, 8.6, 14.4, (q, t) -> {
            for (int i = 0; i < 3; i++) {
                boolean on = CabBack.blink(t, (int) salt + i, 8);
                int[] col = i == 1 ? CYAN : VIOLET;
                q.disc(7.8, 11.2 + i * 1.25, 0.5, at(IRON, 0.2));
                q.disc(7.8, 11.2 + i * 1.25, 0.36, at(col, on ? 5.2 : 1.6));
            }
        });
        p.frostLine(0, 16, 0, 0.6, left ? 6303 : 6304);
        return p;
    }

    static Paint monitorTop() {
        Paint p = Terminals.plate(6401, BRONZE, 2.3);
        Terminals.vent(p, 3.0, 9.5, 13.0, 13.5, 4);
        p.frostLine(0, 16, 0, 0.8, 6402);
        p.frostLine(0, 16, 15.2, 0.6, 6403);
        return p;
    }

    /**
     * The deck on the monitor roof the copper golem with a lamp walks on: ribbed dark steel (diamond notches, as on
     * a gangway), a brass edging with rivets and a dim rune circle in the middle, the place where the golem stands when idle.
     */
    static Paint monitorDeck() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 0.75, 1);
        p.grain(0, 0, 16, 16, 0.06, 6601);
        // ribbing: short notches in a checkerboard, every other one the other way
        for (int gy = 0; gy < 6; gy++) {
            for (int gx = 0; gx < 6; gx++) {
                double cx = 2.6 + gx * 2.15, cy = 2.6 + gy * 2.15;
                boolean a = ((gx + gy) & 1) == 0;
                double dx = a ? 0.55 : -0.55;
                p.line(cx - dx, cy - 0.55, cx + dx, cy + 0.55, 0.45, at(IRON, 1.9));
                p.line(cx - dx + 0.2, cy - 0.35, cx + dx + 0.2, cy + 0.75, 0.25, at(IRON, 0.25));
            }
        }
        // the rune circle in the middle (dim: only the "parking" rune glows)
        p.ring(8, 8, 2.9, 3.5, at(BRASS, 1.4));
        p.ring(8, 8, 2.35, 2.75, argb(150, 0x8A5CE0));
        p.disc(8, 8, 2.3, at(IRON, 0.55));
        p.rune(7.4, 7.2, 1.6, 7, at(VIOLET, 4.6), at(VIOLET, 2.4));
        // brass edging and rivets
        p.frame(0, 0, 16, 16, 1.3, at(BRASS, 2.4));
        p.frame(1.3, 1.3, 14.7, 14.7, 0.35, at(BRASS, 0.8));
        for (double[] r : new double[][]{{1.9, 1.9}, {14.1, 1.9}, {1.9, 14.1}, {14.1, 14.1}, {8, 0.7}, {8, 15.3}, {0.7, 8}, {15.3, 8}}) {
            p.rivet(r[0], r[1], 0.55, BRASS);
        }
        // frost along the edge
        p.frostLine(0, 16, 0, 0.5, 6602);
        return p;
    }

    static Paint monitorBack() {
        Paint p = Terminals.plate(6501, BRONZE, 2.4);
        Saturator.pipeRun(p, 3.0, 1.5, 14.5, 6502);
        Saturator.pipeRun(p, 11.7, 1.5, 14.5, 6503);
        p.slab(5.5, 4.0, 10.5, 12.0, BRASS, 2.6, 1);
        p.slab(6.0, 4.5, 10.0, 11.5, IRON, 0.9, 1);
        for (int i = 0; i < 4; i++) {
            p.rune(6.6, 5.0 + i * 1.6, 1.2, i * 4 + 1, at(VIOLET, 5.4), at(VIOLET, 3.0));
            p.rune(8.4, 5.0 + i * 1.6, 1.2, i * 3 + 2, at(VIOLET, 5.4), at(VIOLET, 3.0));
        }
        return p;
    }

    static void monitor() throws Exception {
        monitorFront().save(Forge2.tex("monitor_front"));
        monitorSide(true).save(Forge2.tex("monitor_side_l"));
        monitorSide(false).save(Forge2.tex("monitor_side_r"));
        monitorTop().save(Forge2.tex("monitor_top"));
        monitorBack().save(Forge2.tex("monitor_back"));
        monitorDeck().save(Forge2.tex("monitor_deck"));
        // animated vortex: frames one under another
        int frames = 16;
        Paint strip = new Paint(16, 16 * frames, K);
        for (int f = 0; f < frames; f++) {
            strip.paste(portalFrame(f, frames), 0, 16 * f);
        }
        strip.save(Forge2.tex("monitor_portal"));
        Forge2.write(RES.resolve("textures/block/monitor_portal.png.mcmeta"), "{\n  \"animation\": {\n    \"frametime\": 3\n  }\n}\n");

        // model: body, a sunk vortex (glowing), a brass base and a crystal on the roof
        List<Models.El> l = new ArrayList<>();
        Models.El body = Models.e(0, 0, 0, 16, 16, 16);
        body.face("north", "#front", 0, 0, 16, 16).face("south", "#back", 0, 0, 16, 16)
                .face("west", "#side_l", 0, 0, 16, 16).face("east", "#side_r", 0, 0, 16, 16)
                .face("up", "#top", 0, 0, 16, 16).face("down", "#bottom", 0, 0, 16, 16);
        l.add(body);
        // the porthole window on the front is a circle centred at (8, 8.5) from the TOP of the texture, that is at height 16 - 8.5 = 7.5
        // of the model: the vortex and the well walls stand right behind it (they used to be a unit higher and peeked out from under the frame)
        double pc = 7.5, ph = 4.6;
        l.add(Models.e(3.4, pc - ph, 1.2, 12.6, pc + ph, 1.2).face("north", "#portal", 0, 0, 16, 16).glow(15));
        // walls of the porthole "well", so the body does not show through from the side
        l.add(Models.e(3.4, pc - ph, 0.02, 3.6, pc + ph, 1.2).only("#ring", "east"));
        l.add(Models.e(12.4, pc - ph, 0.02, 12.6, pc + ph, 1.2).only("#ring", "west"));
        l.add(Models.e(3.4, pc + ph - 0.2, 0.02, 12.6, pc + ph, 1.2).only("#ring", "down"));
        l.add(Models.e(3.4, pc - ph, 0.02, 12.6, pc - ph + 0.2, 1.2).only("#ring", "up"));
        // on the roof a brass deck platform: the copper golem with a signal lamp walks on it (RecipeMonitorRenderer)
        l.add(Models.e(3.5, 16, 3.5, 12.5, 16.4, 12.5).all("#brass").face("up", "#deck", 0, 0, 16, 16));
        // gauges on the roof (as in the reference)
        l.add(Models.e(1.5, 16, 2.0, 4.5, 18.4, 3.0).all("#brass").face("north", "#gauge", 0, 0, 16, 16));
        l.add(Models.e(11.5, 16, 2.0, 14.5, 18.4, 3.0).all("#brass").face("north", "#gauge", 0, 0, 16, 16));
        Models.model("recipe_monitor", false, "monitor_side_l",
                new String[][]{{"front", "monitor_front"}, {"back", "monitor_back"}, {"side_l", "monitor_side_l"}, {"side_r", "monitor_side_r"},
                        {"top", "monitor_top"}, {"bottom", "sat_dark"}, {"portal", "monitor_portal"}, {"ring", "sat_brass"},
                        {"brass", "sat_brass"}, {"crystal", "sat_crystal"}, {"gauge", "monitor_gauge"}, {"deck", "monitor_deck"}},
                l.toArray(new Models.El[0]));
        // render_type cutout: the middle of the front is transparent
        Path mp = RES.resolve("models/block/recipe_monitor.json");
        String s = Files.readString(mp);
        Files.writeString(mp, s.replace("\"ambientocclusion\": false,\n", "\"ambientocclusion\": false,\n  \"render_type\": \"minecraft:cutout\",\n"));
        Paint g = new Paint(16, 16, K);
        g.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        g.gauge(8, 8, 6.5, 70, rgb(0xb02020));
        g.save(Forge2.tex("monitor_gauge"));
        Models.facing4("recipe_monitor", "recipe_monitor");
        Models.item("recipe_monitor", "recipe_monitor");
        Forge2.addDisplay("recipe_monitor", 225, 0.625);
    }

    // ============================================================================================ calculator and coprocessor
    static void calculatorAndCoprocessor() throws Exception {
        String[][] calcTex = {{"panel", "cryo_panel"}, {"ice", "ice_crystal"}, {"matrix", "cryo_panel_matrix"}};
        // a body without slides (the renderer draws and moves the slides)
        Models.model("cryo_calculator", false, "cryo_panel", new String[][]{{"panel", "cryo_panel"}},
                Models.e(0, 0, 0, 16, 16, 16).all("#panel"));
        // working: a "matrix" runs along the sides
        Models.model("cryo_calculator_active", false, "cryo_panel", calcTex,
                Models.e(0, 0, 0, 16, 16, 16).face("north", "#matrix").face("south", "#matrix").face("east", "#matrix")
                        .face("west", "#matrix").face("up", "#panel").face("down", "#panel"));
        // there are no more ice slides: a crew of snowmen works on the roof (the renderer draws it); on the icon three mini snowmen
        for (String old : new String[]{"cryo_calculator_hill_a", "cryo_calculator_hill_b", "cryo_calculator_hill_c"}) {
            Files.deleteIfExists(RES.resolve("models/block/" + old + ".json"));
        }
        String[][] itemTex = {{"panel", "cryo_panel"}, {"snow", "minecraft:block/snow"}};
        Models.model("cryo_calculator_item", false, "cryo_panel", itemTex,
                Models.e(0, 0, 0, 16, 16, 16).all("#panel"),
                Models.e(2.4, 16, 3.6, 5.4, 19, 6.6).all("#snow"), Models.e(2.9, 19, 4.1, 4.9, 21, 6.1).all("#snow"),
                Models.e(10.8, 16, 4.2, 14.4, 19.6, 7.8).all("#snow"), Models.e(11.4, 19.6, 4.8, 13.8, 22, 7.2).all("#snow"),
                Models.e(5.6, 16, 10.6, 8.2, 18.6, 13.2).all("#snow"), Models.e(6.1, 18.6, 11.1, 7.7, 20.2, 12.7).all("#snow"));
        Forge2.write(RES.resolve("blockstates/cryo_calculator.json"),
                "{\n  \"variants\": {\n    \"active=false\": {\"model\": \"" + NS + "cryo_calculator\"},\n"
                        + "    \"active=true\": {\"model\": \"" + NS + "cryo_calculator_active\"}\n  }\n}\n");
        Models.item("cryo_calculator", "cryo_calculator_item");

        // coprocessor: base, post, lid; the fins are a separate model, the renderer swings them
        String[][] copTex = {{"panel", "cryo_panel"}, {"fin", "cryo_fin"}};
        Models.model("cooling_coprocessor", false, "cryo_panel", copTex,
                Models.e(0, 0, 0, 16, 3, 16).all("#panel"),
                Models.e(7, 3, 7, 9, 13, 9).all("#panel"),
                Models.e(0, 13, 0, 16, 16, 16).all("#panel"));
        Models.model("cooling_coprocessor_fin", false, "cryo_fin", copTex,
                Models.e(1, 0, 1, 15, 1, 15).all("#fin"));
        List<Models.El> item = new ArrayList<>();
        item.add(Models.e(0, 0, 0, 16, 3, 16).all("#panel"));
        item.add(Models.e(7, 3, 7, 9, 13, 9).all("#panel"));
        for (double y : new double[]{3.5, 5.5, 7.5, 9.5, 11.5}) {
            item.add(Models.e(1, y, 1, 15, y + 1, 15).all("#fin"));
        }
        item.add(Models.e(0, 13, 0, 16, 16, 16).all("#panel"));
        Models.model("cooling_coprocessor_item", false, "cryo_panel", copTex, item.toArray(new Models.El[0]));
        Models.item("cooling_coprocessor", "cooling_coprocessor_item");
    }

    // ============================================================================================ cooler on the Saturator roof
    /**
     * A coprocessor placed on a vent grille: a low 10x10 radiator with fins and a teal core on top of
     * the grille (the top of the beam is y 12 of the cell). The renderer draws the fan blades on top.
     */
    static void saturatorCooler() throws Exception {
        List<Models.El> l = new ArrayList<>();
        l.add(Models.e(3, 12, 3, 13, 12.6, 13).all("#brass"));
        for (double x : new double[]{4.4, 6.0, 7.6, 9.2, 10.8}) {
            l.add(Models.e(x, 12.6, 3.8, x + 0.9, 14.6, 12.2).all("#fin"));
        }
        l.add(Models.e(3, 14.6, 3, 13, 15.1, 13).all("#panel"));
        l.add(Models.e(6.6, 15.1, 6.6, 9.4, 15.4, 9.4).all("#core").glow(14));
        for (double[] c : new double[][]{{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
            l.add(Models.e(c[0], 12.6, c[1], c[0] + 1, 14.6, c[1] + 1).all("#brass"));
        }
        Models.model("saturator_cooler", false, "cryo_fin",
                new String[][]{{"brass", "sat_brass"}, {"fin", "cryo_fin"}, {"panel", "cryo_panel"}, {"core", "ice_crystal"}},
                l.toArray(new Models.El[0]));
    }

    // ============================================================================================ Cryo-Mainframe
    /**
     * Front of the unassembled Cryo-Mainframe (and the icon): a bronze plate, a brass frame, behind the glass of the double doors
     * a teal matrix and a small snowman, a red valve in the corner.
     */
    static Paint mainframeFront() {
        Paint p = Terminals.plate(7101, BRONZE, 2.4);
        p.slab(1.2, 1.2, 14.8, 14.8, BRASS, 2.7, 1);
        p.slab(2.2, 2.4, 13.8, 13.8, IRON, 0.45, 1);
        // the matrix behind the glass: teal dots of different brightness
        java.util.Random r = new java.util.Random(7102);
        for (double y = 3.0; y < 13.2; y += 1.25) {
            for (double x = 2.9; x < 13.2; x += 1.3) {
                double lv = 1.5 + r.nextDouble() * 3.6;
                p.rect(x, y, x + 0.7, y + 0.45, at(CYAN, lv));
            }
        }
        // a snowman at the bottom left
        p.disc(5.2, 12.3, 1.05, rgb(0xEEF4F8));
        p.disc(5.2, 10.7, 0.75, rgb(0xF6FAFC));
        p.rect(4.9, 10.5, 5.05, 10.65, rgb(0x202020));
        p.rect(5.35, 10.5, 5.5, 10.65, rgb(0x202020));
        // door leaves: a brass joint in the middle, handles and highlights on the glass
        p.rect(7.75, 2.4, 8.25, 13.8, at(BRASS, 2.2));
        p.rect(6.8, 7.0, 7.3, 9.2, at(BRASS, 3.4));
        p.rect(8.7, 7.0, 9.2, 9.2, at(BRASS, 3.4));
        p.line(3.0, 6.0, 6.0, 3.0, 0.35, argb(90, 0xE8F6FF));
        p.line(9.4, 9.0, 12.6, 5.8, 0.35, argb(70, 0xE8F6FF));
        // a red valve in the top right corner of the frame
        p.ring(13.6, 2.4, 0.9, 1.35, rgb(0xC8432F));
        p.line(12.6, 2.4, 14.6, 2.4, 0.25, rgb(0xA8331F));
        p.line(13.6, 1.4, 13.6, 3.4, 0.25, rgb(0xA8331F));
        p.disc(13.6, 2.4, 0.35, at(BRASS, 3.6));
        for (double[] rv : new double[][]{{1.9, 1.9}, {1.9, 14.1}, {14.1, 14.1}}) {
            p.rivet(rv[0], rv[1], 0.45, BRASS);
        }
        return p;
    }

    static void mainframe() throws Exception {
        mainframeFront().save(Forge2.tex("mainframe_front"));
        Models.model("cryo_mainframe", false, "sat_bronze",
                new String[][]{{"front", "mainframe_front"}, {"side", "sat_bronze"}, {"top", "atlas_casing_top"}, {"bottom", "sat_dark"}},
                Models.e(0, 0, 0, 16, 16, 16).face("north", "#front", 0, 0, 16, 16).face("south", "#side", 0, 0, 16, 16)
                        .face("west", "#side", 0, 0, 16, 16).face("east", "#side", 0, 0, 16, 16)
                        .face("up", "#top", 0, 0, 16, 16).face("down", "#bottom", 0, 0, 16, 16));
        Models.facing4("cryo_mainframe", "cryo_mainframe");
        Models.item("cryo_mainframe", "cryo_mainframe");
        Forge2.addDisplay("cryo_mainframe", 225, 0.625);
        // parts of the assembled cabinet are not drawn (the owner's renderer draws the whole cabinet)
        Forge2.write(RES.resolve("blockstates/mainframe_part.json"),
                "{\n  \"variants\": {\n    \"\": {\"model\": \"minecraft:block/air\"}\n  }\n}\n");
    }

    static void all() throws Exception {
        upgrades();
        mainframe();
        chronometerItem();
        // the screen with the Wither is now on the item itself, the window no longer has it
        Files.deleteIfExists(RES.resolve("textures/gui/chrono_crt.png"));
        calculatorMatrix();
        calculatorAndCoprocessor();
        monitor();
        saturatorCooler();
    }
}
