import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel art textures and 3D models for the five new Golem Logix devices:
 * 1. Stock Manometer (Warden)
 * 2. Stock Requester (Storekeeper)
 * 3. Cryo-Diode / Frost Valve
 * 4. Security Terminal
 * 5. Identity Punch Card
 */
public final class GolemDevices {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN,
            VIOLET = Paint.VIOLET, FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM, GOLD = Items.GOLD;
    static final int[] WAX = {0x4a0a0a, 0x7c1414, 0xaa1c1c, 0xd42828, 0xfa4a4a};

    private GolemDevices() {
    }

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
        return Forge2.tex(name);
    }

    static File itemTex(String name) {
        return Forge2.RES.resolve("textures/item/" + name + ".png").toFile();
    }

    static File entityTex(String name) {
        return Forge2.entityTex(name);
    }

    // ============================================================================================ 1. Stock Manometer
    static Paint manometerFront() {
        Paint p = new Paint(16, 16, K);
        // Dark iron backing plate
        p.slab(0, 0, 16, 16, IRON, 2.5, 1);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, at(IRON, 1.0));
        // Rivets on 4 corners
        p.rivet(1.5, 1.5, 0.4, BRASS);
        p.rivet(14.5, 1.5, 0.4, BRASS);
        p.rivet(1.5, 14.5, 0.4, BRASS);
        p.rivet(14.5, 14.5, 0.4, BRASS);

        // Circular brass dial housing
        double cx = 8.0, cy = 8.0, rOut = 6.8, rIn = 5.2;
        p.ring(cx, cy, rIn, rOut, at(BRASS, 4.0));
        p.ring(cx, cy, rOut - 0.5, rOut, at(BRASS, 5.5));
        p.ring(cx, cy, rIn, rIn + 0.4, at(BRASS, 2.0));

        // Dial face (cream parchment)
        p.disc(cx, cy, rIn, at(CREAM, 3.5));
        p.grain(cx - rIn, cy - rIn, cx + rIn, cy + rIn, 0.03, 401);

        // Tick marks around dial circumference
        for (int a = -135; a <= 135; a += 30) {
            double rad = Math.toRadians(a - 90);
            double x0 = cx + Math.cos(rad) * 4.6;
            double y0 = cy + Math.sin(rad) * 4.6;
            double x1 = cx + Math.cos(rad) * 3.8;
            double y1 = cy + Math.sin(rad) * 3.8;
            p.line(x0, y0, x1, y1, 0.4, 0xFF302820);
        }

        // Sub-dial arcs: teal arc on left (safe/normal), red arc on right (threshold)
        p.disc(cx, cy, 1.2, at(BRASS, 5.0));
        p.disc(cx, cy, 0.6, at(IRON, 2.0));

        // Redstone indicator terminals on bottom
        p.rect(6.5, 14.5, 9.5, 16.0, at(COPPER, 3.0));
        p.disc(8.0, 15.0, 0.5, 0xFFFF2020);

        // Frost on top-left rim
        p.disc(3.0, 3.0, 0.8, at(FROST, 5.0));
        p.disc(4.0, 2.2, 0.6, at(FROST, 4.0));
        return p;
    }

    static Paint manometerCasing() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.2, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(BRASS, 1.8));
        p.rivet(2.0, 2.0, 0.4, BRASS);
        p.rivet(14.0, 2.0, 0.4, BRASS);
        p.rivet(2.0, 14.0, 0.4, BRASS);
        p.rivet(14.0, 14.0, 0.4, BRASS);
        // Iron horizontal banding
        p.rect(0, 7.0, 16, 9.0, at(IRON, 3.0));
        p.line(0, 7.0, 16, 7.0, 0.3, at(IRON, 4.5));
        p.line(0, 9.0, 16, 9.0, 0.3, at(IRON, 1.5));
        return p;
    }

    static Paint manometerNeedle() {
        // 16x16 entity texture: needle pointing straight UP (0 deg = 12 o'clock), pivot at (8, 8)
        Paint p = new Paint(16, 16, 2);
        p.disc(8.0, 8.0, 1.8, at(BRASS, 3.0));
        p.disc(8.0, 8.0, 1.2, at(BRASS, 5.2));
        p.disc(8.0, 8.0, 0.6, at(IRON, 1.5));

        // Needle blade pointing up to y = 1.5
        double[] xs = {7.2, 8.0, 8.8, 8.4, 7.6};
        double[] ys = {8.0, 1.5, 8.0, 9.5, 9.5};
        p.poly(xs, ys, at(IRON, 1.8));
        p.line(8.0, 2.0, 8.0, 7.5, 0.4, 0xFFE03020); // Red needle highlight line
        return p;
    }

    // ============================================================================================ 2. Stock Requester
    static Paint requesterTop() {
        Paint p = new Paint(16, 16, K);
        // Copper desk surface
        p.slab(0, 0, 16, 16, COPPER, 3.0, 3);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(COPPER, 1.5));
        // Corner rivets
        p.rivet(2.0, 2.0, 0.4, COPPER);
        p.rivet(14.0, 2.0, 0.4, COPPER);
        p.rivet(2.0, 14.0, 0.4, COPPER);
        p.rivet(14.0, 14.0, 0.4, COPPER);

        // Open ledger book in center (x: 3.5..12.5, y: 3.0..11.0)
        double bx0 = 3.5, bx1 = 12.5, by0 = 3.0, by1 = 11.5;
        // Leather / dark brass book binding
        p.rect(bx0 - 0.4, by0 - 0.4, bx1 + 0.4, by1 + 0.4, at(BRONZE, 2.0));
        // Parchment pages
        p.rect(bx0, by0, 8.0, by1, at(CREAM, 3.8));
        p.rect(8.0, by0, bx1, by1, at(CREAM, 3.4));
        // Center spine crease
        p.line(8.0, by0, 8.0, by1, 0.5, at(BRONZE, 1.5));

        // Ledger ruled writing lines
        for (double ly = by0 + 1.2; ly < by1 - 0.5; ly += 1.4) {
            p.line(bx0 + 0.8, ly, 7.4, ly, 0.25, 0x80504030);
            p.line(8.6, ly, bx1 - 0.8, ly, 0.25, 0x80504030);
        }

        // Stylus rest (brass stylus groove at bottom)
        p.rect(4.0, 12.8, 12.0, 14.2, at(BRASS, 2.0));
        p.line(4.5, 13.5, 11.5, 13.5, 0.4, at(BRASS, 5.0));

        // Punch card slot on top right
        p.rect(13.0, 3.5, 15.0, 4.5, at(IRON, 1.2));
        p.rect(13.2, 3.7, 14.8, 4.3, 0xFF000000);
        return p;
    }

    static Paint requesterFront() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, COPPER, 3.2, 4);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(COPPER, 1.5));
        // Drawers / order ledger compartments
        for (double dy : new double[]{2.5, 7.0, 11.5}) {
            p.rect(2.5, dy, 13.5, dy + 3.2, at(COPPER, 2.4));
            p.frame(2.5, dy, 13.5, dy + 3.2, 0.3, at(COPPER, 1.2));
            // Brass label / pull handle
            p.rect(6.5, dy + 1.1, 9.5, dy + 2.1, at(BRASS, 4.5));
            p.rect(7.2, dy + 1.4, 8.8, dy + 1.8, at(IRON, 2.0));
        }
        return p;
    }

    static Paint requesterSide() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, COPPER, 3.0, 5);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(COPPER, 1.5));
        p.rivet(2.0, 2.0, 0.4, COPPER);
        p.rivet(14.0, 2.0, 0.4, COPPER);
        p.rivet(2.0, 14.0, 0.4, COPPER);
        p.rivet(14.0, 14.0, 0.4, COPPER);
        // Brass ventilation louvers in center
        for (double y = 5.0; y <= 11.0; y += 1.8) {
            p.rect(4.0, y, 12.0, y + 0.8, at(BRASS, 3.5));
            p.line(4.0, y, 12.0, y, 0.2, at(BRASS, 5.0));
        }
        return p;
    }

    // ============================================================================================ 3. Cryo-Diode
    static Paint cryoDiodeSide() {
        Paint p = new Paint(16, 16, K);
        // Dark iron pipe body
        p.slab(0, 0, 16, 16, IRON, 2.8, 6);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.4, at(IRON, 1.2));

        // Brass flange collars on inlet (South/right) and outlet (North/left)
        p.rect(0, 1.5, 2.5, 14.5, at(BRASS, 4.0));
        p.rect(13.5, 1.5, 16.0, 14.5, at(BRASS, 3.2));

        // Central directional check valve body
        p.rect(4.0, 3.5, 12.0, 12.5, at(IRON, 2.2));
        p.frame(4.0, 3.5, 12.0, 12.5, 0.4, at(BRASS, 3.0));

        // Glowing directional arrow pointing forward (from right=inlet to left=outlet)
        // Arrow head at left, tail at right
        double[] xs = {5.0, 7.5, 7.5, 11.0, 11.0, 7.5, 7.5};
        double[] ys = {8.0, 5.5, 6.8, 6.8, 9.2, 9.2, 10.5};
        p.poly(xs, ys, at(CYAN, 5.2));
        p.line(5.5, 8.0, 10.5, 8.0, 0.6, at(CYAN, 6.0));

        // Frost rime at the bottom
        for (double x = 1.0; x < 15.0; x += 2.2) {
            p.line(x, 15.5, x + 0.8, 14.0, 0.4, at(FROST, 5.0));
        }
        return p;
    }

    static Paint cryoDiodeOutlet() {
        Paint p = new Paint(16, 16, K);
        // Outlet port (facing flow direction)
        p.slab(0, 0, 16, 16, BRASS, 4.0, 7);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(BRASS, 2.0));
        // Circular opening
        p.ring(8.0, 8.0, 3.5, 6.0, at(IRON, 3.0));
        p.disc(8.0, 8.0, 3.5, at(CYAN, 4.5));
        p.disc(8.0, 8.0, 2.0, at(CYAN, 6.0)); // Glowing core
        // Frost crystals around rim
        p.rivet(2.5, 2.5, 0.4, BRASS);
        p.rivet(13.5, 2.5, 0.4, BRASS);
        p.rivet(2.5, 13.5, 0.4, BRASS);
        p.rivet(13.5, 13.5, 0.4, BRASS);
        return p;
    }

    static Paint cryoDiodeInlet() {
        Paint p = new Paint(16, 16, K);
        // Inlet port (back face)
        p.slab(0, 0, 16, 16, IRON, 2.5, 8);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(IRON, 1.2));
        // Mechanical check valve grille / flap
        p.ring(8.0, 8.0, 3.5, 6.0, at(BRASS, 3.5));
        p.disc(8.0, 8.0, 3.5, at(IRON, 1.5));
        p.line(4.5, 8.0, 11.5, 8.0, 0.6, at(IRON, 3.5));
        p.line(8.0, 4.5, 8.0, 11.5, 0.6, at(IRON, 3.5));
        return p;
    }

    // ============================================================================================ 4. Security Terminal
    static Paint securityFront() {
        Paint p = new Paint(16, 16, K);
        // Heavy vault door front
        p.slab(0, 0, 16, 16, IRON, 2.2, 9);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.5, at(IRON, 1.0));
        // Heavy brass corner reinforcements
        for (double[] c : new double[][]{{0.8, 0.8}, {12.2, 0.8}, {0.8, 12.2}, {12.2, 12.2}}) {
            p.rect(c[0], c[1], c[0] + 3.0, c[1] + 3.0, at(BRASS, 3.8));
            p.rivet(c[0] + 1.5, c[1] + 1.5, 0.4, BRASS);
        }

        // Heavy circular tumbler lock wheel on left (x: 2..8, y: 5..11)
        double lx = 5.0, ly = 8.0;
        p.ring(lx, ly, 1.5, 2.8, at(BRASS, 4.2));
        p.disc(lx, ly, 1.2, at(IRON, 1.8));
        // Lock spokes
        p.line(lx - 2.6, ly, lx + 2.6, ly, 0.4, at(BRASS, 5.0));
        p.line(lx, ly - 2.6, lx, ly + 2.6, 0.4, at(BRASS, 5.0));

        // Punch card slots on right (x: 9.5..14.5)
        for (double sy : new double[]{4.5, 7.5, 10.5}) {
            p.rect(9.5, sy, 14.5, sy + 1.8, at(BRASS, 3.2));
            p.rect(10.0, sy + 0.4, 14.0, sy + 1.4, at(IRON, 1.0));
            p.line(10.2, sy + 0.9, 13.8, sy + 0.9, 0.3, 0xFF000000);
        }

        // Vacuum tube indicator lamps on top center
        // Red lamp (locked/restricted)
        p.rect(7.0, 1.8, 8.8, 3.2, at(BRASS, 2.5));
        p.disc(7.9, 2.5, 0.6, 0xFFFF2525);
        // Cyan lamp (authorized/active)
        p.rect(9.2, 1.8, 11.0, 3.2, at(BRASS, 2.5));
        p.disc(10.1, 2.5, 0.6, at(CYAN, 5.5));
        return p;
    }

    static Paint securitySide() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 2.4, 10);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(IRON, 1.0));
        // Vertical brass ribs
        for (double x : new double[]{3.5, 8.0, 12.5}) {
            p.rect(x - 0.6, 1.5, x + 0.6, 14.5, at(BRASS, 3.5));
            p.rivet(x, 2.5, 0.3, BRASS);
            p.rivet(x, 13.5, 0.3, BRASS);
        }
        return p;
    }

    // ============================================================================================ 5. Identity Punch Card Item
    static Paint identityPunchCard() {
        Paint p = new Paint(16, 16, 2);
        Items.shadow(p, 8.4, 13.6, 7.2, 1.2);
        double x0 = 1.0, x1 = 15.0, y0 = 3.0, y1 = 13.0;

        // Bevelled top-left corner
        double[] xs = {x0 + 1.8, x1, x1, x0, x0}, ys = {y0, y0, y1, y1, y0 + 1.8};
        p.poly(xs, ys, at(CREAM, 3.5));
        p.grain(x0, y0, x1, y1, 0.04, 501);

        // Brass edging
        p.line(x0 + 1.8, y0 + 0.2, x1, y0 + 0.2, 0.35, at(BRASS, 5.0));
        p.line(x0 + 0.2, y0 + 1.8, x0 + 0.2, y1, 0.35, at(BRASS, 4.5));
        p.line(x0 + 0.2, y0 + 1.8, x0 + 1.8, y0 + 0.2, 0.35, at(BRASS, 5.5));
        p.rect(x0, y1 - 0.35, x1, y1, at(BRASS, 2.5));
        p.rect(x1 - 0.35, y0, x1, y1, at(BRASS, 2.8));

        // Perforated hole grid on left side (x: 2.5..7.5, y: 5.0..11.0)
        for (double py = 5.0; py <= 11.0; py += 2.0) {
            for (double px = 2.8; px <= 6.8; px += 2.0) {
                p.disc(px, py, 0.45, at(IRON, 1.5));
                p.disc(px, py, 0.25, at(CYAN, 4.5)); // Occult cyan underglow through punch holes
            }
        }

        // Crimson wax seal on right side (x: 10.5, y: 8.0)
        double wx = 10.8, wy = 8.0;
        p.disc(wx, wy, 2.2, at(WAX, 2));
        p.disc(wx, wy, 1.8, at(WAX, 3));
        p.disc(wx, wy, 1.4, at(WAX, 1));
        // Stamped occult monogram / eye in wax
        p.disc(wx, wy, 0.6, at(GOLD, 4.5));

        // Frost on top right corner
        p.disc(14.0, 3.5, 0.6, at(FROST, 5.0));
        return p;
    }

    // ============================================================================================ Models & Registration
    static void models() throws Exception {
        // 1. Stock Manometer
        String[][] mt = {
                {"front", "stock_manometer_front"},
                {"side", "stock_manometer_side"},
                {"top", "stock_manometer_side"},
                {"bottom", "stock_manometer_side"}
        };
        List<Models.El> me = new ArrayList<>();
        me.add(Models.e(0, 0, 0, 16, 16, 16)
                .face("north", "#front")
                .face("south", "#side")
                .face("east", "#side")
                .face("west", "#side")
                .face("up", "#top")
                .face("down", "#bottom"));
        Models.model("stock_manometer", false, "stock_manometer_front", mt, me.toArray(new Models.El[0]));
        Models.facing6("stock_manometer", "stock_manometer");
        Models.item("stock_manometer", "stock_manometer");

        // 2. Stock Requester
        String[][] rt = {
                {"top", "stock_requester_top"},
                {"front", "stock_requester_front"},
                {"side", "stock_requester_side"},
                {"bottom", "stock_requester_side"}
        };
        List<Models.El> re = new ArrayList<>();
        re.add(Models.e(0, 0, 0, 16, 16, 16)
                .face("up", "#top")
                .face("north", "#front")
                .face("south", "#side")
                .face("east", "#side")
                .face("west", "#side")
                .face("down", "#bottom"));
        Models.model("stock_requester", false, "stock_requester_top", rt, re.toArray(new Models.El[0]));
        Models.facing4("stock_requester", "stock_requester");
        Models.item("stock_requester", "stock_requester");

        // 3. Cryo-Diode
        String[][] dt = {
                {"front", "cryo_diode_outlet"},
                {"back", "cryo_diode_inlet"},
                {"side", "cryo_diode_side"},
                {"top", "stock_manometer_side"},
                {"bottom", "stock_manometer_side"}
        };
        List<Models.El> de = new ArrayList<>();
        de.add(Models.e(0, 0, 0, 16, 16, 16)
                .face("north", "#front")
                .face("south", "#back")
                .face("east", "#side")
                .face("west", "#side")
                .face("up", "#top")
                .face("down", "#bottom"));
        Models.model("cryo_diode", false, "cryo_diode_side", dt, de.toArray(new Models.El[0]));
        Models.facing6("cryo_diode", "cryo_diode");
        Models.item("cryo_diode", "cryo_diode");

        // 4. Security Terminal
        String[][] st = {
                {"front", "security_terminal_front"},
                {"side", "security_terminal_side"},
                {"top", "stock_manometer_side"},
                {"bottom", "security_terminal_side"}
        };
        List<Models.El> se = new ArrayList<>();
        se.add(Models.e(0, 0, 0, 16, 16, 16)
                .face("north", "#front")
                .face("south", "#side")
                .face("east", "#side")
                .face("west", "#side")
                .face("up", "#top")
                .face("down", "#bottom"));
        Models.model("security_terminal", false, "security_terminal_front", st, se.toArray(new Models.El[0]));
        Models.facing4("security_terminal", "security_terminal");
        Models.item("security_terminal", "security_terminal");

        // 5. Identity Punch Card item model
        String itemJson = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/identity_punch_card\"\n  }\n}\n";
        Forge2.write(Forge2.RES.resolve("models/item/identity_punch_card.json"), itemJson);
    }

    public static void all() throws Exception {
        manometerFront().save(tex("stock_manometer_front"));
        manometerCasing().save(tex("stock_manometer_side"));
        manometerNeedle().save(entityTex("stock_manometer_needle"));

        requesterTop().save(tex("stock_requester_top"));
        requesterFront().save(tex("stock_requester_front"));
        requesterSide().save(tex("stock_requester_side"));

        cryoDiodeSide().save(tex("cryo_diode_side"));
        cryoDiodeOutlet().save(tex("cryo_diode_outlet"));
        cryoDiodeInlet().save(tex("cryo_diode_inlet"));

        securityFront().save(tex("security_terminal_front"));
        securitySide().save(tex("security_terminal_side"));

        identityPunchCard().save(itemTex("identity_punch_card"));

        models();
    }
}
