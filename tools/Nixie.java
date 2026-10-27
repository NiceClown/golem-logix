import java.util.ArrayList;
import java.util.List;

/**
 * Nixie tubes, for the heat lamps on the pipes and the Channel Counter: thin wire digits that glow (one picture of the ten,
 * drawn additively and tinted by the renderer), the anode mesh behind them with the other cathodes standing dark in it, a glass
 * envelope with highlights, a walnut base and bakelite sockets.
 */
final class Nixie {
    static final int K = Forge2.K;

    private Nixie() {
    }

    static int rgb(int c) {
        return 0xff000000 | c;
    }

    /** A wire along an arc of the ellipse (cx, cy, rx, ry) from a0 to a1 (degrees, 0 = right, 90 = down). */
    static void arc(Paint p, double cx, double cy, double rx, double ry, double a0, double a1, double w, int c) {
        double step = 4;
        double px = cx + rx * Math.cos(Math.toRadians(a0)), py = cy + ry * Math.sin(Math.toRadians(a0));
        for (double a = a0 + step; a <= a1 + 1e-6; a += step) {
            double x = cx + rx * Math.cos(Math.toRadians(a)), y = cy + ry * Math.sin(Math.toRadians(a));
            p.line(px, py, x, y, w, c);
            px = x;
            py = y;
        }
    }

    /** One digit's wire in a 10 x 16 cell at (ox, 0): the classic nixie shapes, a little narrower than a print face. */
    static void digit(Paint p, int d, double ox, double w, int c) {
        double cx = ox + 5;
        switch (d) {
            case 0 -> arc(p, cx, 8, 3.4, 6.4, 0, 360, w, c);
            case 1 -> {
                p.line(cx + 0.4, 1.6, cx + 0.4, 14.4, w, c);
                p.line(cx - 1.8, 3.6, cx + 0.4, 1.6, w, c);
            }
            case 2 -> {
                arc(p, cx, 4.8, 3.2, 3.2, 180, 380, w, c);
                p.line(cx + 2.9, 6.0, cx - 3.3, 14.4, w, c);
                p.line(cx - 3.3, 14.4, cx + 3.4, 14.4, w, c);
            }
            case 3 -> {
                arc(p, cx, 4.6, 3.0, 3.0, 190, 450, w, c);
                arc(p, cx, 11.0, 3.4, 3.4, 270, 520, w, c);
            }
            case 4 -> {
                p.line(cx + 1.6, 1.6, cx - 3.4, 10.6, w, c);
                p.line(cx - 3.4, 10.6, cx + 3.6, 10.6, w, c);
                p.line(cx + 1.6, 1.6, cx + 1.6, 14.4, w, c);
            }
            case 5 -> {
                p.line(cx + 3.2, 1.6, cx - 2.6, 1.6, w, c);
                p.line(cx - 2.6, 1.6, cx - 3.0, 7.0, w, c);
                arc(p, cx, 10.4, 3.4, 4.0, 220, 500, w, c);
            }
            case 6 -> {
                arc(p, cx, 10.6, 3.3, 3.8, 0, 360, w, c);
                p.line(cx + 2.6, 1.6, cx - 3.0, 9.4, w, c);
            }
            case 7 -> {
                p.line(cx - 3.4, 1.6, cx + 3.4, 1.6, w, c);
                p.line(cx + 3.4, 1.6, cx - 1.2, 14.4, w, c);
            }
            case 8 -> {
                arc(p, cx, 4.6, 2.8, 3.0, 0, 360, w, c);
                arc(p, cx, 11.0, 3.4, 3.4, 0, 360, w, c);
            }
            default -> {
                arc(p, cx, 5.4, 3.3, 3.8, 0, 360, w, c);
                p.line(cx + 3.0, 6.6, cx - 2.6, 14.4, w, c);
            }
        }
    }

    /**
     * The ten digits side by side, cells of 10 x 16 units: a bright wire with a soft halo round it, grey on black (the renderer tints
     * it and adds it to what is behind).
     */
    static Paint digits() {
        // drawn additively: black adds nothing, so the halo is a darker grey rather than a thinner alpha
        Paint p = new Paint(100, 16, 4);
        p.fill(0, 0, 100, 16, (u, v) -> rgb(0x000000));
        for (int d = 0; d < 10; d++) {
            digit(p, d, d * 10, 1.6, rgb(0x3a3a3a));
            digit(p, d, d * 10, 0.9, rgb(0x8a8a8a));
            digit(p, d, d * 10, 0.45, rgb(0xffffff));
        }
        return p;
    }

    /** The anode mesh behind the digit: a fine copper grid on dark, the other cathodes standing in it as dark wires. */
    static Paint mesh() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            boolean grid = ((int) (u * K)) % 2 == 0 || ((int) (v * K)) % 2 == 0;
            return grid ? rgb(0x3a2418) : rgb(0x140c08);
        });
        for (int d = 0; d < 10; d += 3) {
            digit(p, d, 3, 0.5, rgb(0x5a3a26));
        }
        return p;
    }

    /** The glass envelope: nearly clear, a cool tint, two upright highlights and a bright rim at the top. */
    static Paint glass() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double e = Math.abs(u / 8 - 1);
            int a = (int) (26 + 40 * Math.pow(e, 3));
            return Paint.argb(a, 0xdcecf4);
        });
        p.rect(3, 1, 4, 15, Paint.argb(150, 0xffffff));
        p.rect(12, 2, 12.5, 14, Paint.argb(90, 0xffffff));
        p.rect(0, 0, 16, 1, Paint.argb(170, 0xeef6fa));
        return p;
    }

    /** A walnut base: warm red-brown grain running along, a darker edge. */
    static Paint walnut() {
        Paint p = new Paint(16, 16, K);
        int[] tones = {0x4a2414, 0x5a2e1a, 0x6a3820, 0x7a4226, 0x8a4c2c};
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double v = (j + 0.5) / K;
                double grain = 0.5 + 0.5 * Math.sin(v * 2.3 + Atlant.noise(i, j, 12, 501) * 4) * 0.7 + 0.3 * Atlant.hash(i / 3, j, 502);
                p.px(i, j, rgb(tones[(int) Math.max(0, Math.min(4, grain * 5))]));
            }
        }
        return p;
    }

    /** The socket: dark bakelite with a copper ring. */
    static Paint socket() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> rgb(((int) u + (int) v) % 5 == 0 ? 0x2a2220 : 0x1c1614));
        p.rect(0, 0, 16, 1.5, rgb(Atlant.COPPER[6]));
        p.rect(0, 14.5, 16, 16, rgb(Atlant.COPPER[2]));
        return p;
    }

    /**
     * One tube standing at (x0, y0, z0) - its front to the north - w wide, h high, d deep: the socket, the mesh plate (the digit is
     * drawn just in front of it, at the depth the renderer is told), the glass envelope and its dome.
     */
    /** Where a side-mounted counter sits (pixels, ChannelCounterBlock.Mount's offsets): back against the wall, the deck's height
     * on a block, a basic pipe and a dense pipe. */
    static final double SIDE_DZ = 5.4;
    static final double[] SIDE_DY = {0, 7.1, 6.0};

    static void tube(List<Models.El> l, double x0, double y0, double z0, double w, double h, double d) {
        double zm = z0 + d / 2;
        l.add(Models.e(x0 + 0.2, y0, z0 + 0.2, x0 + w - 0.2, y0 + 0.8, z0 + d - 0.2).only("#socket", "north", "south", "east", "west", "up"));
        l.add(Models.e(x0 + 0.3, y0 + 1.2, zm - 0.05, x0 + w - 0.3, y0 + h - 0.6, zm + 0.05).only("#mesh", "north", "south"));
        l.add(Models.e(x0, y0 + 0.8, z0, x0 + w, y0 + h, z0 + d).only("#glass", "north", "south", "east", "west", "up"));
        l.add(Models.e(x0 + 0.5, y0 + h, z0 + 0.5, x0 + w - 0.5, y0 + h + 0.6, z0 + d - 0.5).only("#glass", "north", "south", "east", "west", "up"));
    }

    /** The counter itself (facing north) moved by dy, dz pixels: a walnut base with a brass edge and three small tubes on it. */
    static List<Models.El> counterBody(double dy, double dz) {
        List<Models.El> l = new ArrayList<>();
        l.add(Models.e(3.5, dy, 6 + dz, 12.5, dy + 1.6, 10 + dz).all("#wood"));
        l.add(Models.e(3.5, dy + 1.6, 6 + dz, 12.5, dy + 1.9, 10 + dz).only("#brass", "north", "south", "east", "west", "up"));
        for (double cx : new double[]{5.55, 8.0, 10.45}) {
            tube(l, cx - 1.1, dy + 1.9, 6.7 + dz, 2.2, 6.6, 2.6);
        }
        return l;
    }

    /**
     * The Channel Counter (facing north; the digits are lit on both sides of the meshes), one model for each way it is fixed (the
     * offsets of the counter are ChannelCounterBlock.Mount's): a plain foot on a block; astride a horizontal pipe running along x,
     * two brass straps round the tube's top (clear of the pipe's own clamps) with bolts and stems up to the base; on the side of a
     * block or of a pipe behind it (south) a small shelf like the golem's on the Cryo-Distillation Tower - a dark deck with a brass
     * lip, a wall plate with two bolts against the block or the tube's flat face, two brass struts under the deck.
     */
    static void counterModels() throws Exception {
        String[][] tex = {{"wood", "nixie_wood"}, {"brass", "sat_brass"}, {"socket", "nixie_socket"}, {"mesh", "nixie_mesh"},
                {"glass", "nixie_glass"}, {"bolt", "pipe_bolt"}, {"dark", "sat_dark"}};
        String[] mounts = {"floor", "pipe_basic", "pipe_dense", "side_block", "side_basic", "side_dense"};
        for (String m : mounts) {
            List<Models.El> l;
            switch (m) {
                case "pipe_basic", "pipe_dense" -> {
                    l = counterBody(0, 0);
                    // the tube below spans 4..12 (basic) or 2..14 (dense) of its cell: the straps hug it a hair off its faces,
                    // between the pipe's clamps (at the joints and at the middle)
                    double lo = m.equals("pipe_dense") ? 2 : 4;
                    double z0 = lo - 0.65, z1 = 16 - z0, top = 0.05 - lo;
                    for (double x : new double[]{3.6, 11.0}) {
                        l.add(Models.e(x, top, z0, x + 1.4, top + 0.6, z1).all("#brass"));
                        l.add(Models.e(x, -8, z0, x + 1.4, top, z0 + 0.6).only("#brass", "north", "south", "east", "west", "down"));
                        l.add(Models.e(x, -8, z1 - 0.6, x + 1.4, top, z1).only("#brass", "north", "south", "east", "west", "down"));
                        l.add(Models.e(x + 0.4, -6.2, z0 - 0.3, x + 1.0, -5.6, z0).all("#bolt"));
                        l.add(Models.e(x + 0.4, -6.2, z1, x + 1.0, -5.6, z1 + 0.3).all("#bolt"));
                        l.add(Models.e(x + 0.4, top + 0.6, 7.3, x + 1.0, 0, 8.7).only("#brass", "north", "south", "east", "west"));
                    }
                }
                case "side_block", "side_basic", "side_dense" -> {
                    // the wall: the block's face at z 16, or the pipe's tube at 20 (basic) or 18 (dense), whose flat face spans
                    // y 4..12 (basic) or 2..14 (dense), its flat face 5.3..10.7 or 4.2..11.8 - the plate hangs from it down the chamfer
                    double wall = m.equals("side_block") ? 16 : m.equals("side_basic") ? 20 : 18;
                    double deck = m.equals("side_block") ? SIDE_DY[0] : m.equals("side_basic") ? SIDE_DY[1] : SIDE_DY[2];
                    double plate0 = m.equals("side_block") ? -2.4 : m.equals("side_basic") ? 4.3 : 2.3, pz = wall - 0.6;
                    l = counterBody(deck, SIDE_DZ);
                    double front = 6 + SIDE_DZ - 0.6;
                    l.add(Models.e(3.0, deck - 0.6, front, 13.0, deck, pz).only("#dark", "up", "down", "east", "west"));
                    l.add(Models.e(2.8, deck - 0.8, front - 0.2, 13.2, deck + 0.2, front + 0.5).all("#brass"));
                    l.add(Models.e(3.2, plate0, pz, 12.8, deck + 0.6, wall).only("#brass", "north", "east", "west", "up", "down"));
                    for (double x : new double[]{5.0, 10.4}) {
                        l.add(Models.e(x, plate0 + 0.7, pz - 0.3, x + 0.6, plate0 + 1.3, pz).all("#bolt"));
                    }
                    // the struts: from the foot of the plate up to the deck's underside at 22.5 degrees
                    double foot = plate0 + 0.6, len = (deck - 0.6 - foot) / Math.sin(Math.toRadians(22.5));
                    for (double x : new double[]{3.4, 12.0}) {
                        l.add(Models.e(x, foot - 0.3, pz - len, x + 0.6, foot + 0.3, pz).only("#brass", "up", "down", "east", "west", "north")
                                .rot("x", 22.5, x + 0.3, foot, pz));
                    }
                }
                default -> l = counterBody(0, 0);
            }
            Models.model("channel_counter_" + m, true, "nixie_wood", tex, l.toArray(new Models.El[0]));
        }
        String[][] facings = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"}};
        List<String> lines = new ArrayList<>();
        for (String m : mounts) {
            for (String[] f : facings) {
                lines.add("    \"facing=" + f[0] + ",mount=" + m + "\": {\"model\": \"" + Forge2.NS + "channel_counter_" + m + "\"" + f[1] + "}");
            }
        }
        Forge2.write(Forge2.RES.resolve("blockstates/channel_counter.json"),
                "{\n  \"variants\": {\n" + String.join(",\n", lines) + "\n  }\n}\n");
        Models.item("channel_counter", "channel_counter_floor");
        Forge2.write(Forge2.RES.resolve("items/channel_counter.json"),
                "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"golem_logix:item/channel_counter\"\n  }\n}\n");
    }

    static void all() throws Exception {
        digits().save(Forge2.RES.resolve("textures/entity/nixie_digits.png").toFile());
        mesh().save(Forge2.tex("nixie_mesh"));
        glass().save(Forge2.tex("nixie_glass"));
        walnut().save(Forge2.tex("nixie_wood"));
        socket().save(Forge2.tex("nixie_socket"));
        counterModels();
    }
}
