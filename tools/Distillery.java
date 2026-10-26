/**
 * Icons of the Cryo-Source Jar and the Cryo-Distillation Tower (32x32, the Items style), after the concept art: a dark brick plinth
 * with runes, a glass column in a verdigris copper frame, a louvred brass head under snow with icicles and a bent vent pipe, a little
 * copper golem with a valve wheel on the right. The jar's flask holds a swirl of icy Cryo-Source; the tower's column holds a coil
 * glowing from blood red to ice cyan.
 */
final class Distillery {
    static final int[] BRASS = Paint.BRASS, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET, COPPER = Paint.COPPER;
    static final int[] BRICK = {0x1a0c0c, 0x2c1414, 0x40201c, 0x562c26, 0x6e3a30, 0x8a4c3e, 0xa86252};

    private Distillery() {
    }

    static void all() throws Exception {
        jar().save(Items.itemTex("cryo_source_jar"));
        tower().save(Items.itemTex("cryo_distillation_tower"));
        mixer().save(Items.itemTex("cryo_mixer"));
    }

    /** Dark bricks with mortar lines and a rune band in the given colour. */
    private static void plinth(Paint p, double x0, double y0, double x1, double y1, int rune) {
        p.fill(x0, y0, x1, y1, (u, v) -> {
            int row = (int) Math.floor((v - y0) / 1.2);
            double off = row % 2 == 0 ? 0 : 1.1;
            boolean mortar = ((v - y0) % 1.2) < 0.25 || ((u - x0 + off) % 2.2) < 0.25;
            return mortar ? Paint.ramp(BRICK, 0.1) : Paint.ramp(BRICK, 0.42 + 0.12 * Math.sin(u * 3.1 + row));
        });
        p.rect(x0 - 0.3, y1 - 0.6, x1 + 0.3, y1, Items.at(IRON, 1.2));
        p.rect(x0 - 0.2, y0 - 0.6, x1 + 0.2, y0, Items.at(IRON, 1.6));
        for (int i = 0; i < 4; i++) {
            p.rune(x0 + 0.7 + i * (x1 - x0 - 1.4) / 4, y1 - 2.4, 1.4, 5 + i * 4, rune, 0);
        }
        p.glowRect(x0 + 0.4, y1 - 2.4, x1 - 0.4, y1 - 1.0, 0.8, rune, 80);
    }

    /** The verdigris frame posts and rings around a column. */
    private static void frame(Paint p, double x0, double y0, double x1, double y1) {
        int teal = Items.at(Items.VERDIGRIS, 3.6), tealD = Items.at(Items.VERDIGRIS, 2.2);
        p.rect(x0, y0, x0 + 0.7, y1, teal);
        p.rect(x1 - 0.7, y0, x1, y1, tealD);
        p.rect((x0 + x1) / 2 - 0.25, y0, (x0 + x1) / 2 + 0.25, y1, Items.at(Items.VERDIGRIS, 3.0));
        p.rect(x0 - 0.2, y0 - 0.5, x1 + 0.2, y0, teal);
        p.rect(x0 - 0.2, y1, x1 + 0.2, y1 + 0.5, tealD);
        // rust spots on the copper
        p.rect(x0 + 0.1, y0 + 2.2, x0 + 0.6, y0 + 2.8, Items.at(COPPER, 3.0));
        p.rect(x1 - 0.6, y1 - 2.6, x1 - 0.1, y1 - 2.0, Items.at(COPPER, 2.6));
    }

    /** The louvred brass head under snow with icicles and the vent pipe. */
    private static void head(Paint p, double x0, double y0, double x1, double y1, double pipeX) {
        p.slab(x0, y0, x1, y1, BRASS, 3.4, 1);
        for (double y = y0 + 0.6; y < y1 - 0.4; y += 1.0) {
            p.rect(x0 - 0.4, y, x1 + 0.4, y + 0.45, Items.at(BRASS, 4.8));
            p.rect(x0 - 0.4, y + 0.45, x1 + 0.4, y + 0.6, Items.at(BRASS, 1.8));
        }
        // snow cap with icicles
        p.rect(x0 - 0.6, y0 - 1.1, x1 + 0.6, y0, Paint.rgb(0xeef6fa));
        p.rect(x0 - 0.6, y0 - 0.3, x1 + 0.6, y0, Paint.rgb(0xc8dce6));
        p.icicle(x0 - 0.2, y0, 1.6, 0.6);
        p.icicle(x0 + 2.2, y0, 0.9, 0.4);
        p.icicle(x1 - 1.4, y0, 1.3, 0.5);
        p.icicle(x1 + 0.1, y0, 1.8, 0.6);
        // the vent pipe bending to the side
        p.rect(pipeX - 0.6, y0 - 2.6, pipeX + 0.6, y0 - 1.1, Items.at(BRASS, 4.0));
        p.rect(pipeX - 0.6, y0 - 3.4, pipeX + 2.0, y0 - 2.4, Items.at(BRASS, 4.6));
        p.rect(pipeX + 1.6, y0 - 3.6, pipeX + 2.3, y0 - 2.2, Paint.rgb(0xd8eef6));
    }

    /** The little copper golem on the right, its hands on a brass wheel. */
    private static void golem(Paint p, double x, double y) {
        p.slab(x, y, x + 2.4, y + 2.2, COPPER, 3.4, 1);
        p.rect(x + 0.4, y + 0.7, x + 0.9, y + 1.2, Paint.rgb(0x7ff6ee));
        p.rect(x + 1.4, y + 0.7, x + 1.9, y + 1.2, Paint.rgb(0x7ff6ee));
        p.slab(x + 0.2, y + 2.2, x + 2.2, y + 4.0, COPPER, 2.8, 1);
        p.rect(x + 0.4, y + 2.6, x + 1.0, y + 3.4, Items.at(Items.VERDIGRIS, 3.0));
        p.rect(x + 0.5, y + 4.0, x + 1.0, y + 5.0, Items.at(COPPER, 2.4));
        p.rect(x + 1.4, y + 4.0, x + 1.9, y + 5.0, Items.at(COPPER, 2.2));
        p.ring(x - 0.6, y + 2.8, 0.7, 1.1, Items.at(BRASS, 4.4));
        p.line(x - 0.6, y + 1.7, x - 0.6, y + 3.9, 0.25, Items.at(BRASS, 3.0));
        p.line(x - 1.7, y + 2.8, x + 0.5, y + 2.8, 0.25, Items.at(BRASS, 3.0));
        p.glowRect(x + 0.4, y + 0.7, x + 1.9, y + 1.2, 0.5, Paint.rgb(0x7ff6ee), 80);
    }

    static Paint jar() {
        Paint p = Items.canvas();
        Items.shadow(p, 7.4, 15.0, 5.6, 0.9);
        double x0 = 3.2, x1 = 11.6;
        plinth(p, x0 - 0.6, 11.4, x1 + 0.6, 14.6, Paint.rgb(0x58f0e8));
        // the flask: icy Cryo-Source swirling behind glass
        p.fill(x0 + 0.6, 5.6, x1 - 0.6, 11.0, (u, v) -> {
            double swirl = Math.sin(u * 1.3 + v * 0.9) + 0.6 * Math.sin(u * 0.5 - v * 1.7 + 1.2);
            int ice = Paint.ramp(CYAN, 0.55 + 0.25 * swirl / 1.6 - 0.15 * (v - 5.6) / 5.4);
            return swirl > 0.9 ? Paint.mix(ice, Paint.ramp(VIOLET, 0.6), 0.5) : ice;
        });
        p.rect(x0 + 1.0, 6.0, x0 + 1.6, 10.6, Paint.argb(150, 0xffffff));
        p.glowRect(x0 + 0.8, 6.0, x1 - 0.8, 10.6, 1.0, Paint.rgb(0x8af0ff), 90);
        frame(p, x0, 5.6, x1, 11.0);
        head(p, x0 + 0.2, 3.6, x1 - 0.2, 5.6, (x0 + x1) / 2);
        golem(p, x1 + 0.9, 6.4);
        return p;
    }

    /**
     * The Cryo-Mixer: a rune plinth, louvred brass sides under snow around a verdigris copper module with a gear, a tray of snow and
     * ice at the front; on top two flasks with snowy rims (water on the left, swirling Cryo-Source on the right) joined by a pipe;
     * the little golem on the right.
     */
    static Paint mixer() {
        Paint p = Items.canvas();
        Items.shadow(p, 7.6, 15.1, 6.2, 0.9);
        plinth(p, 1.8, 12.2, 13.6, 15.0, Paint.rgb(0x58f0e8));
        // louvred brass sides with snow on top, the copper module between them
        for (double[] s : new double[][]{{2.2, 5.4}, {10.0, 13.2}}) {
            p.slab(s[0], 7.8, s[1], 12.2, BRASS, 3.4, 1);
            for (double y = 8.4; y < 11.8; y += 0.9) {
                p.rect(s[0] - 0.3, y, s[1] + 0.3, y + 0.4, Items.at(BRASS, 4.8));
                p.rect(s[0] - 0.3, y + 0.4, s[1] + 0.3, y + 0.55, Items.at(BRASS, 1.8));
            }
            p.rect(s[0] - 0.4, 7.2, s[1] + 0.4, 7.8, Paint.rgb(0xeef6fa));
            p.icicle(s[0], 7.8, 0.9, 0.4);
            p.icicle(s[1] - 0.6, 7.8, 1.2, 0.4);
        }
        p.slab(5.4, 7.0, 10.0, 12.2, Items.VERDIGRIS, 3.2, 1);
        p.rect(5.4, 7.0, 10.0, 7.5, Items.at(Items.VERDIGRIS, 4.4));
        p.rect(5.8, 9.0, 6.4, 9.6, Items.at(COPPER, 3.0));
        // the gear on the module's face
        p.ring(8.4, 9.8, 0.5, 1.5, Items.at(BRASS, 4.6));
        for (double[] t : new double[][]{{8.2, 7.9}, {8.2, 11.3}, {6.5, 9.6}, {9.9, 9.6}}) {
            p.rect(t[0], t[1], t[0] + 0.5, t[1] + 0.5, Items.at(BRASS, 4.0));
        }
        // the tray at the front with a snow block and an ice block
        p.rect(2.0, 11.2, 6.6, 12.6, Items.at(BRASS, 3.0));
        p.rect(2.0, 11.2, 6.6, 11.6, Items.at(BRASS, 4.6));
        p.slab(2.6, 9.8, 4.4, 11.4, new int[]{0x8aa0b0, 0xa8bccc, 0xc4d6e2, 0xdae8f0, 0xeef6fa, 0xf8fcfe, 0xffffff}, 4.4, 1);
        p.slab(4.4, 10.2, 6.0, 11.4, CYAN, 3.6, 1);
        // the flasks: water on the left, Cryo-Source with its spiral on the right; snowy rims, a pipe between them
        p.fill(2.6, 2.0, 6.4, 7.2, (u, v) -> Paint.ramp(new int[]{0x0c1c4a, 0x14306c, 0x1c4890, 0x2a62b4, 0x4280d0, 0x62a0e6, 0x8ac0f4},
                0.45 + 0.15 * Math.sin(u * 2.1 + v * 0.7) - 0.1 * (v - 2.0) / 5.2));
        p.fill(9.4, 2.0, 13.2, 7.2, (u, v) -> Paint.ramp(CYAN, 0.55 + 0.2 * Math.sin(u * 1.6 - v * 1.1) - 0.12 * (v - 2.0) / 5.2));
        int[] spiral = {0x5ce6f8, 0x7a5cff, 0xc03cb0};
        for (int k = 0; k < 3; k++) {
            double y = 6.2 - k * 1.5;
            int c = Paint.rgb(spiral[k]);
            p.line(9.8, y + 0.4, 12.8, y - 0.3, 0.4, c);
            p.glowRect(9.8, y - 0.4, 12.8, y + 0.6, 0.6, c, 70);
        }
        for (double[] f : new double[][]{{2.6, 6.4}, {9.4, 13.2}}) {
            p.rect(f[0] + 0.3, 2.4, f[0] + 0.8, 6.8, Paint.argb(140, 0xffffff));
            p.rect(f[0] - 0.4, 7.0, f[1] + 0.4, 7.6, Items.at(BRASS, 3.6));
            p.rect(f[0] - 0.5, 1.2, f[1] + 0.5, 2.0, Paint.rgb(0xeef6fa));
            p.icicle(f[0] - 0.2, 2.0, 1.0, 0.4);
            p.icicle(f[1] - 0.4, 2.0, 0.8, 0.4);
        }
        p.line(4.5, 1.1, 4.5, 0.4, 0.5, Items.at(COPPER, 3.4));
        p.line(4.5, 0.4, 7.7, 0.4, 0.5, Items.at(COPPER, 3.6));
        p.line(7.7, 0.4, 7.7, 7.0, 0.5, Items.at(COPPER, 3.0));
        golem(p, 13.7, 7.6);
        return p;
    }

    static Paint tower() {
        Paint p = Items.canvas();
        Items.shadow(p, 7.4, 15.2, 5.8, 0.9);
        double x0 = 3.0, x1 = 11.6;
        plinth(p, x0 - 0.6, 12.0, x1 + 0.6, 15.0, Paint.rgb(0xff4a3a));
        // the column: misty glass, a pool of blood, the coil glowing from red to ice
        p.fill(x0 + 0.6, 5.4, x1 - 0.6, 11.6, (u, v) -> Paint.mix(Paint.rgb(0xd8e4ec), Paint.rgb(0x9cb4c4), (v - 5.4) / 6.2 * 0.6));
        p.rect(x0 + 0.7, 10.4, x1 - 0.7, 11.6, Paint.rgb(0x8a1418));
        int[] coil = {0xd02424, 0xe8661e, 0xc03cb0, 0x7a5cff, 0x5ce6f8};
        for (int k = 0; k < coil.length; k++) {
            double y = 10.4 - k * 1.1;
            int c = Paint.rgb(coil[k]);
            p.line(x0 + 1.0, y + 0.4, x1 - 1.0, y - 0.3, 0.5, c);
            p.line(x0 + 1.0, y + 0.4, x0 + 1.6, y + 0.9, 0.4, Paint.shade(c, 0.7));
            p.line(x1 - 1.0, y - 0.3, x1 - 1.6, y + 0.2, 0.4, Paint.shade(c, 0.7));
            p.glowRect(x0 + 1.0, y - 0.4, x1 - 1.0, y + 0.6, 0.7, c, 70);
        }
        p.rect(x0 + 1.0, 5.8, x0 + 1.5, 10.0, Paint.argb(140, 0xffffff));
        frame(p, x0, 5.4, x1, 11.6);
        head(p, x0 + 0.2, 3.6, x1 - 0.2, 5.4, (x0 + x1) / 2);
        golem(p, x1 + 0.9, 6.6);
        return p;
    }
}
