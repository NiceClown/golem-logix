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
