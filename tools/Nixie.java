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
    static void tube(List<Models.El> l, double x0, double y0, double z0, double w, double h, double d) {
        double zm = z0 + d / 2;
        l.add(Models.e(x0 + 0.2, y0, z0 + 0.2, x0 + w - 0.2, y0 + 0.8, z0 + d - 0.2).only("#socket", "north", "south", "east", "west", "up"));
        l.add(Models.e(x0 + 0.5, y0 + 1.2, zm - 0.05, x0 + w - 0.5, y0 + h - 0.6, zm + 0.05).only("#mesh", "north", "south"));
        l.add(Models.e(x0, y0 + 0.8, z0, x0 + w, y0 + h, z0 + d).only("#glass", "north", "south", "east", "west", "up"));
        l.add(Models.e(x0 + 0.5, y0 + h, z0 + 0.5, x0 + w - 0.5, y0 + h + 0.6, z0 + d - 0.5).only("#glass", "north", "south", "east", "west", "up"));
    }

    /**
     * The Channel Counter: four tubes in a row on a walnut base with a brass edge, the digits facing north (the renderer writes the
     * number on the tubes' mesh planes at z 8).
     */
    static void counterModel() throws Exception {
        List<Models.El> l = new ArrayList<>();
        l.add(Models.e(0.5, 0, 3.5, 15.5, 2.6, 12.5).all("#wood"));
        l.add(Models.e(0.5, 2.6, 3.5, 15.5, 3.0, 12.5).only("#brass", "north", "south", "east", "west", "up"));
        for (int i = 0; i < 4; i++) {
            tube(l, 1.25 + i * 3.5, 3.0, 6.0, 3.0, 9.0, 4.0);
        }
        Models.model("channel_counter", true, "nixie_wood", new String[][]{{"wood", "nixie_wood"}, {"brass", "sat_brass"},
                {"socket", "nixie_socket"}, {"mesh", "nixie_mesh"}, {"glass", "nixie_glass"}}, l.toArray(new Models.El[0]));
        Models.facing4("channel_counter", "channel_counter");
        Models.item("channel_counter", "channel_counter");
        Forge2.write(Forge2.RES.resolve("items/channel_counter.json"),
                "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n    \"model\": \"golem_logix:item/channel_counter\"\n  }\n}\n");
    }

    static void all() throws Exception {
        digits().save(Forge2.RES.resolve("textures/entity/nixie_digits.png").toFile());
        mesh().save(Forge2.tex("nixie_mesh"));
        glass().save(Forge2.tex("nixie_glass"));
        walnut().save(Forge2.tex("nixie_wood"));
        socket().save(Forge2.tex("nixie_socket"));
        counterModel();
    }
}
