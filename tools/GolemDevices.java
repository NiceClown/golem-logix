import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel art textures and 3D models for the four redesigned Golem Logix devices:
 * 1. Brass Stock Manometer (Warden) - Console apparatus block with dial, twin cryo tubes, stepped brass brackets (Variant B)
 * 2. Copper Order Ledger (Storekeeper) - Clerk's lectern desk with open ledger, drawers, card slot & copper golem puppet (Variant 2 / B)
 * 3. Cryo-Diode / Frost Valve - Inline pipe fitting matching basic pipe size, frosted quartz cylinder, handwheel, nozzle (Variant 1 / A)
 * 4. Iron Security Terminal (Gatekeeper) - Heavy vault cabinet with green phosphor CRT screen, brass combination dial, card slot (Variant 3)
 * 5. Identity Punch Card - Copper punch card item with fingerprint-punched pattern and crimson wax seal
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

    // ============================================================================================ 1. Stock Manometer (Variant B)
    static Paint manometerFront() {
        Paint p = new Paint(16, 16, K);
        // Dark iron console backing plate
        p.slab(0, 0, 16, 16, IRON, 2.5, 1);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, at(IRON, 1.0));

        // Stepped brass corner reinforcement brackets
        for (double[] c : new double[][]{{0.5, 0.5}, {12.5, 0.5}, {0.5, 12.5}, {12.5, 12.5}}) {
            p.rect(c[0], c[1], c[0] + 3.0, c[1] + 3.0, at(BRASS, 4.0));
            p.frame(c[0], c[1], c[0] + 3.0, c[1] + 3.0, 0.35, at(BRASS, 2.0));
            p.rivet(c[0] + 1.5, c[1] + 1.5, 0.45, BRASS);
        }

        // Circular dial recess in center (x: 4.5..11.5, y: 4.5..11.5)
        p.disc(8.0, 8.0, 4.0, at(IRON, 1.5));
        p.ring(8.0, 8.0, 3.6, 4.0, at(IRON, 1.0));

        // Twin cryo-tube channels on left and right
        p.rect(1.5, 2.0, 3.5, 14.0, at(IRON, 1.6));
        p.rect(12.5, 2.0, 14.5, 14.0, at(IRON, 1.6));
        p.line(2.5, 2.0, 2.5, 14.0, 0.3, at(CYAN, 3.0));
        p.line(13.5, 2.0, 13.5, 14.0, 0.3, at(CYAN, 3.0));

        // Redstone terminal on bottom center
        p.rect(6.0, 14.0, 10.0, 15.5, at(BRASS, 3.2));
        p.rect(7.0, 14.2, 9.0, 15.3, 0xFFE02020);
        p.disc(8.0, 14.75, 0.4, 0xFFFFA0A0);

        // Top cable conduit plate
        p.rect(6.0, 0.5, 10.0, 2.0, at(BRASS, 3.5));
        p.rivet(6.8, 1.25, 0.3, BRASS);
        p.rivet(9.2, 1.25, 0.3, BRASS);
        return p;
    }

    static Paint manometerSide() {
        Paint p = new Paint(16, 16, K);
        // Dark iron panel
        p.slab(0, 0, 16, 16, IRON, 2.6, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(IRON, 1.2));

        // Stepped brass bands at top and bottom
        p.rect(0, 0, 16, 2.5, at(BRASS, 3.5));
        p.line(0, 2.5, 16, 2.5, 0.35, at(BRASS, 1.8));
        p.rect(0, 13.5, 16, 16, at(BRASS, 3.5));
        p.line(0, 13.5, 16, 13.5, 0.35, at(BRASS, 5.0));

        // Corner rivets
        p.rivet(2.0, 1.25, 0.4, BRASS);
        p.rivet(14.0, 1.25, 0.4, BRASS);
        p.rivet(2.0, 14.75, 0.4, BRASS);
        p.rivet(14.0, 14.75, 0.4, BRASS);

        // Center vertical iron reinforcing band with rivets
        p.rect(7.0, 2.5, 9.0, 13.5, at(IRON, 3.4));
        p.line(7.0, 2.5, 7.0, 13.5, 0.3, at(IRON, 4.5));
        p.line(9.0, 2.5, 9.0, 13.5, 0.3, at(IRON, 1.5));
        p.rivet(8.0, 5.0, 0.35, BRASS);
        p.rivet(8.0, 8.0, 0.35, BRASS);
        p.rivet(8.0, 11.0, 0.35, BRASS);
        return p;
    }

    static Paint manometerTop() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 2.8, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(IRON, 1.2));
        // Stepped brass border
        p.frame(0.8, 0.8, 15.2, 15.2, 1.2, at(BRASS, 3.5));
        p.rivet(2.0, 2.0, 0.4, BRASS);
        p.rivet(14.0, 2.0, 0.4, BRASS);
        p.rivet(2.0, 14.0, 0.4, BRASS);
        p.rivet(14.0, 14.0, 0.4, BRASS);
        // Central conduit / vent flange
        p.ring(8.0, 8.0, 2.5, 4.5, at(BRASS, 4.2));
        p.disc(8.0, 8.0, 2.5, at(IRON, 1.5));
        return p;
    }

    static Paint manometerDial() {
        Paint p = new Paint(16, 16, K);
        // Dial parchment disc
        double cx = 8.0, cy = 8.0, rOut = 7.5, rIn = 6.2;
        p.disc(cx, cy, rOut, at(BRASS, 4.2));
        p.ring(cx, cy, rIn, rOut, at(BRASS, 5.5));
        p.ring(cx, cy, rIn - 0.4, rIn, at(BRASS, 2.0));
        p.disc(cx, cy, rIn - 0.4, at(CREAM, 4.2));
        p.grain(cx - rIn, cy - rIn, cx + rIn, cy + rIn, 0.02, 601);

        // Safe zone cyan arc (left) and danger threshold red arc (right)
        for (int a = -135; a <= -30; a += 5) {
            double rad = Math.toRadians(a - 90);
            p.disc(cx + Math.cos(rad) * 4.9, cy + Math.sin(rad) * 4.9, 0.35, at(CYAN, 4.0));
        }
        for (int a = 30; a <= 135; a += 5) {
            double rad = Math.toRadians(a - 90);
            p.disc(cx + Math.cos(rad) * 4.9, cy + Math.sin(rad) * 4.9, 0.35, 0xFFD83020);
        }

        // Radial graduation tick marks
        for (int a = -135; a <= 135; a += 30) {
            double rad = Math.toRadians(a - 90);
            double x0 = cx + Math.cos(rad) * 5.4;
            double y0 = cy + Math.sin(rad) * 5.4;
            double x1 = cx + Math.cos(rad) * 4.0;
            double y1 = cy + Math.sin(rad) * 4.0;
            p.line(x0, y0, x1, y1, 0.45, 0xFF282018);
        }
        // Sub-ticks
        for (int a = -120; a <= 120; a += 30) {
            double rad = Math.toRadians(a - 90);
            double x0 = cx + Math.cos(rad) * 5.4;
            double y0 = cy + Math.sin(rad) * 5.4;
            double x1 = cx + Math.cos(rad) * 4.5;
            double y1 = cy + Math.sin(rad) * 4.5;
            p.line(x0, y0, x1, y1, 0.3, 0xFF484034);
        }

        // Center brass boss
        p.disc(cx, cy, 1.4, at(BRASS, 4.8));
        p.disc(cx, cy, 0.6, at(IRON, 1.8));
        return p;
    }

    static Paint manometerGlass() {
        Paint p = new Paint(16, 16, K);
        // Translucent frosted quartz glass with frost rime
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                p.px(x, y, argb(80, at(FROST, 3.5)));
            }
        }
        // Frost edge highlights
        p.line(1.0, 0, 1.0, 16.0, 0.5, argb(160, at(FROST, 5.5)));
        p.line(14.0, 0, 14.0, 16.0, 0.5, argb(160, at(FROST, 5.5)));
        p.line(5.0, 2.0, 7.0, 14.0, 0.4, argb(110, 0xFFFFFF));
        return p;
    }

    static Paint manometerFluid() {
        Paint p = new Paint(16, 16, K);
        // Intense glowing cyan cryo fluid
        p.slab(0, 0, 16, 16, CYAN, 5.0, 1);
        p.line(0, 2.0, 16, 2.0, 0.6, at(CYAN, 6.0));
        p.line(0, 14.0, 16, 14.0, 0.4, at(CYAN, 4.0));
        // Bubbles / cryo crystals
        p.disc(4.0, 6.0, 0.8, at(CYAN, 6.0));
        p.disc(11.0, 10.0, 0.7, at(CYAN, 6.0));
        p.disc(6.0, 12.0, 0.5, 0xFFFFFFFF);
        return p;
    }

    static Paint manometerNeedle() {
        // 16x16 entity texture: needle pointing straight UP (0 deg = 12 o'clock), pivot at (8, 8)
        Paint p = new Paint(16, 16, 2);
        p.disc(8.0, 8.0, 1.8, at(BRASS, 3.0));
        p.disc(8.0, 8.0, 1.2, at(BRASS, 5.2));
        p.disc(8.0, 8.0, 0.6, at(IRON, 1.5));

        // Needle blade pointing up to y = 1.5
        double[] xs = {7.3, 8.0, 8.7, 8.4, 7.6};
        double[] ys = {8.0, 1.5, 8.0, 9.5, 9.5};
        p.poly(xs, ys, at(IRON, 1.8));
        p.line(8.0, 2.0, 8.0, 7.5, 0.45, 0xFFE03020); // Bright red needle highlight line
        return p;
    }

    // ============================================================================================ 2. Stock Requester (Variant 2 / B)
    static Paint requesterTop() {
        Paint p = new Paint(16, 16, K);
        // Warm polished copper desk surface
        p.slab(0, 0, 16, 16, COPPER, 3.2, 3);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.5, at(COPPER, 1.6));
        p.rivet(2.0, 2.0, 0.4, COPPER);
        p.rivet(14.0, 2.0, 0.4, COPPER);
        p.rivet(2.0, 14.0, 0.4, COPPER);
        p.rivet(14.0, 14.0, 0.4, COPPER);
        // Subtle verdigris / patina in joints
        p.line(0.8, 1.0, 15.2, 1.0, 0.3, at(Paint.BLUE, 3.0));
        return p;
    }

    static Paint requesterFront() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, COPPER, 3.0, 4);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(COPPER, 1.5));
        // 3 tiers of filing drawers
        for (double dy : new double[]{2.0, 6.5, 11.0}) {
            p.rect(2.0, dy, 14.0, dy + 3.5, at(COPPER, 2.4));
            p.frame(2.0, dy, 14.0, dy + 3.5, 0.35, at(COPPER, 1.2));
            // Brass label plate holder
            p.rect(4.5, dy + 0.8, 11.5, dy + 2.7, at(BRASS, 4.2));
            // Cream paper label card inside holder
            p.rect(5.2, dy + 1.1, 10.8, dy + 2.4, at(CREAM, 4.5));
            p.line(5.8, dy + 1.75, 10.2, dy + 1.75, 0.25, 0xFF403020);
            // Brass drawer pull drop handle below label
            p.rect(7.0, dy + 2.8, 9.0, dy + 3.2, at(BRASS, 5.0));
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
        for (double y = 4.5; y <= 11.5; y += 1.8) {
            p.rect(3.5, y, 12.5, y + 0.9, at(BRASS, 3.5));
            p.line(3.5, y, 12.5, y, 0.25, at(BRASS, 5.2));
            p.line(3.5, y + 0.9, 12.5, y + 0.9, 0.2, at(COPPER, 1.2));
        }
        return p;
    }

    static Paint requesterBook() {
        Paint p = new Paint(16, 16, K);
        // Open ledger book: cream parchment with ruled lines & leather spine
        p.slab(0, 0, 16, 16, CREAM, 4.5, 1);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.4, at(BRONZE, 2.0));
        // Center spine crease
        p.line(8.0, 0, 8.0, 16.0, 0.8, at(BRONZE, 1.5));
        p.line(7.6, 0, 7.6, 16.0, 0.3, at(BRONZE, 2.5));
        p.line(8.4, 0, 8.4, 16.0, 0.3, at(BRONZE, 2.5));

        // Ruled ledger lines (left and right page)
        for (double ly = 2.0; ly < 15.0; ly += 1.8) {
            p.line(1.5, ly, 7.2, ly, 0.3, 0x80504030);
            p.line(8.8, ly, 14.5, ly, 0.3, 0x80504030);
        }
        // Red ledger margin line
        p.line(2.8, 1.0, 2.8, 15.0, 0.25, 0x90C03030);
        p.line(13.2, 1.0, 13.2, 15.0, 0.25, 0x90C03030);
        return p;
    }

    static Paint requesterGolem() {
        Paint p = new Paint(16, 16, K);
        // Miniature copper golem head and body texture
        p.slab(0, 0, 16, 16, COPPER, 3.2, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(COPPER, 1.5));
        // Golem face: large dark inquisitive eyes on upper face
        p.rect(3.0, 4.0, 6.5, 8.5, 0xFF140D09);
        p.rect(9.5, 4.0, 13.0, 8.5, 0xFF140D09);
        p.disc(4.8, 6.0, 0.6, at(COPPER, 5.0)); // Eye highlight
        p.disc(11.2, 6.0, 0.6, at(COPPER, 5.0));

        // Patina streaks in seams
        p.line(0, 10.0, 16, 10.0, 0.4, at(Paint.BLUE, 3.2));
        p.line(8.0, 10.0, 8.0, 16.0, 0.4, at(Paint.BLUE, 3.2));
        // Copper rivets on brow
        p.rivet(2.5, 2.2, 0.3, COPPER);
        p.rivet(13.5, 2.2, 0.3, COPPER);
        return p;
    }

    static Paint requesterCard() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, COPPER, 3.5, 1);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.35, at(BRASS, 4.5));
        // Punch hole matrix with glowing cyan back-light
        for (double y = 3.0; y <= 13.0; y += 2.5) {
            for (double x = 3.0; x <= 13.0; x += 2.5) {
                p.disc(x, y, 0.6, at(IRON, 1.2));
                p.disc(x, y, 0.35, at(CYAN, 5.5));
            }
        }
        return p;
    }

    // ============================================================================================ 3. Cryo-Diode (Variant 1 / A)
    static Paint cryoDiodeSide() {
        Paint p = new Paint(16, 16, K);
        // Cast brass valve body
        p.slab(0, 0, 16, 16, BRASS, 3.8, 3);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.4, at(BRASS, 2.0));

        // Raised central valve housing
        p.rect(3.5, 3.0, 12.5, 13.0, at(BRASS, 3.2));
        p.frame(3.5, 3.0, 12.5, 13.0, 0.4, at(BRASS, 5.0));

        // Glowing cyan directional flow arrow (pointing forward towards outlet at left)
        double[] xs = {4.5, 7.5, 7.5, 11.5, 11.5, 7.5, 7.5};
        double[] ys = {8.0, 5.0, 6.5, 6.5, 9.5, 9.5, 11.0};
        p.poly(xs, ys, at(CYAN, 5.5));
        p.line(5.0, 8.0, 11.0, 8.0, 0.6, at(CYAN, 6.5));

        // Frost rime at base
        for (double x = 1.0; x < 15.0; x += 2.2) {
            p.line(x, 15.5, x + 0.8, 13.5, 0.35, at(FROST, 5.5));
        }
        return p;
    }

    static Paint cryoDiodeOutlet() {
        Paint p = new Paint(16, 16, K);
        // Outlet nozzle face with circular cryo aperture
        p.slab(0, 0, 16, 16, BRASS, 4.0, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(BRASS, 2.2));
        // Circular stepped nozzle ring
        p.ring(8.0, 8.0, 3.5, 6.2, at(BRASS, 5.2));
        p.ring(8.0, 8.0, 3.2, 3.8, at(BRASS, 2.0));
        // Luminous cyan cryo emitter vortex
        p.disc(8.0, 8.0, 3.2, at(CYAN, 4.5));
        p.disc(8.0, 8.0, 1.8, at(CYAN, 6.2));
        p.disc(8.0, 8.0, 0.8, 0xFFFFFFFF);

        // Corner hex bolts
        p.rivet(2.5, 2.5, 0.45, BRASS);
        p.rivet(13.5, 2.5, 0.45, BRASS);
        p.rivet(2.5, 13.5, 0.45, BRASS);
        p.rivet(13.5, 13.5, 0.45, BRASS);
        return p;
    }

    static Paint cryoDiodeInlet() {
        Paint p = new Paint(16, 16, K);
        // Inlet flange with mechanical check-valve cross-grille
        p.slab(0, 0, 16, 16, COPPER, 3.2, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(COPPER, 1.5));
        p.ring(8.0, 8.0, 3.5, 6.0, at(BRASS, 4.0));
        p.disc(8.0, 8.0, 3.5, at(IRON, 1.5));
        // Heavy iron check-valve cross-grille
        p.line(4.5, 8.0, 11.5, 8.0, 0.6, at(IRON, 3.5));
        p.line(8.0, 4.5, 8.0, 11.5, 0.6, at(IRON, 3.5));
        p.disc(8.0, 8.0, 1.0, at(BRASS, 4.5));

        // 4 corner bolts
        p.rivet(2.5, 2.5, 0.45, BRASS);
        p.rivet(13.5, 2.5, 0.45, BRASS);
        p.rivet(2.5, 13.5, 0.45, BRASS);
        p.rivet(13.5, 13.5, 0.45, BRASS);
        return p;
    }

    static Paint cryoDiodeGlass() {
        Paint p = new Paint(16, 16, K);
        // Frosted quartz cylinder with condensation
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                p.px(x, y, argb(85, at(FROST, 3.8)));
            }
        }
        p.line(0, 2.0, 16, 2.0, 0.4, argb(140, 0xFFFFFF));
        p.line(0, 14.0, 16, 14.0, 0.4, argb(140, 0xFFFFFF));
        p.line(4.0, 0, 4.0, 16.0, 0.3, argb(120, at(CYAN, 4.0)));
        p.line(12.0, 0, 12.0, 16.0, 0.3, argb(120, at(CYAN, 4.0)));
        return p;
    }

    static Paint cryoDiodeCore() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, CYAN, 5.2, 1);
        p.line(0, 8.0, 16, 8.0, 1.0, at(CYAN, 6.2));
        p.line(0, 8.0, 16, 8.0, 0.4, 0xFFFFFFFF);
        return p;
    }

    static Paint cryoDiodeHandwheel() {
        Paint p = new Paint(16, 16, K);
        // Cast brass valve handwheel
        p.ring(8.0, 8.0, 5.0, 7.2, at(BRASS, 5.0));
        p.ring(8.0, 8.0, 5.0, 5.5, at(BRASS, 2.5));
        p.disc(8.0, 8.0, 2.5, at(BRASS, 4.0));
        p.disc(8.0, 8.0, 1.2, at(IRON, 1.8));
        // 4 Spokes
        p.line(1.5, 8.0, 14.5, 8.0, 0.8, at(BRASS, 4.5));
        p.line(8.0, 1.5, 8.0, 14.5, 0.8, at(BRASS, 4.5));
        return p;
    }

    // ============================================================================================ 4. Security Terminal (Variant 3)
    static Paint securityFront() {
        Paint p = new Paint(16, 16, K);
        // Heavy vault door front plate
        p.slab(0, 0, 16, 16, IRON, 2.4, 1);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.5, at(IRON, 1.0));

        // Heavy brass corner reinforcements
        for (double[] c : new double[][]{{0.8, 0.8}, {12.2, 0.8}, {0.8, 12.2}, {12.2, 12.2}}) {
            p.rect(c[0], c[1], c[0] + 3.0, c[1] + 3.0, at(BRASS, 3.8));
            p.rivet(c[0] + 1.5, c[1] + 1.5, 0.45, BRASS);
        }

        // CRT Monitor hood recess (upper left, x: 2.5..9.5, y: 7.5..13.5)
        p.rect(2.5, 2.5, 9.5, 8.5, at(IRON, 1.2));
        p.frame(2.5, 2.5, 9.5, 8.5, 0.4, at(BRASS, 3.5));

        // Combination dial mounting plate (upper right, x: 10.5..14.5, y: 3.5..7.5)
        p.rect(10.5, 3.5, 14.5, 7.5, at(BRASS, 3.5));
        p.disc(12.5, 5.5, 1.8, at(BRASS, 5.0));

        // Card slot entry bezel (lower center, y: 10.5..13.5)
        p.rect(3.5, 10.5, 12.5, 13.5, at(BRASS, 3.8));
        p.rect(4.2, 11.4, 11.8, 12.6, at(IRON, 1.0));
        p.line(4.5, 12.0, 11.5, 12.0, 0.35, 0xFF000000);

        // Status indicator lamp bezels
        p.disc(6.0, 14.5, 0.5, 0xFFE02020); // Locked red
        p.disc(10.0, 14.5, 0.5, at(CYAN, 5.5)); // Authorized cyan
        return p;
    }

    static Paint securitySide() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 2.5, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(IRON, 1.0));
        // Vertical brass ribs with heavy rivets
        for (double x : new double[]{3.5, 8.0, 12.5}) {
            p.rect(x - 0.7, 1.5, x + 0.7, 14.5, at(BRASS, 3.5));
            p.line(x - 0.7, 1.5, x - 0.7, 14.5, 0.25, at(BRASS, 5.0));
            p.rivet(x, 3.0, 0.35, BRASS);
            p.rivet(x, 8.0, 0.35, BRASS);
            p.rivet(x, 13.0, 0.35, BRASS);
        }
        return p;
    }

    static Paint securityScreen() {
        Paint p = new Paint(16, 16, K);
        // Green phosphor CRT radar display
        p.rect(0, 0, 16, 16, 0xFF071408);
        p.frame(0.5, 0.5, 15.5, 15.5, 0.5, 0xFF0E2812);

        // Concentric radar rings
        p.ring(8.0, 8.0, 6.0, 6.4, 0xFF186020);
        p.ring(8.0, 8.0, 3.8, 4.2, 0xFF186020);
        p.ring(8.0, 8.0, 1.6, 2.0, 0xFF208828);

        // Radar grid crosshairs
        p.line(1.0, 8.0, 15.0, 8.0, 0.35, 0xFF1A6822);
        p.line(8.0, 1.0, 8.0, 15.0, 0.35, 0xFF1A6822);

        // Sweeping radar beam / blip
        p.line(8.0, 8.0, 13.5, 4.0, 0.6, 0xFF50FF60);
        p.disc(11.0, 5.8, 0.7, 0xFF88FFA0);
        p.disc(8.0, 8.0, 0.8, 0xFF50FF60);
        return p;
    }

    static Paint securityDial() {
        Paint p = new Paint(16, 16, K);
        // Rotary brass combination dial
        p.disc(8.0, 8.0, 7.0, at(BRASS, 4.2));
        p.ring(8.0, 8.0, 5.8, 7.0, at(BRASS, 5.5));
        p.ring(8.0, 8.0, 5.8, 6.0, at(BRASS, 2.0));
        p.disc(8.0, 8.0, 5.8, at(BRASS, 3.5));

        // Dial graduation marks
        for (int a = 0; a < 360; a += 30) {
            double rad = Math.toRadians(a);
            p.line(8.0 + Math.cos(rad) * 6.6, 8.0 + Math.sin(rad) * 6.6,
                    8.0 + Math.cos(rad) * 5.2, 8.0 + Math.sin(rad) * 5.2, 0.4, 0xFF181208);
        }
        // Center turning handle boss
        p.disc(8.0, 8.0, 2.2, at(BRASS, 5.2));
        p.rect(7.0, 4.5, 9.0, 11.5, at(BRASS, 5.8));
        p.disc(8.0, 8.0, 0.8, at(IRON, 1.8));
        return p;
    }

    static Paint securityTop() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 2.8, 2);
        p.frame(0.8, 0.8, 15.2, 15.2, 0.4, at(IRON, 1.2));
        p.frame(0.8, 0.8, 15.2, 15.2, 1.2, at(BRASS, 3.5));
        p.rivet(2.0, 2.0, 0.4, BRASS);
        p.rivet(14.0, 2.0, 0.4, BRASS);
        p.rivet(2.0, 14.0, 0.4, BRASS);
        p.rivet(14.0, 14.0, 0.4, BRASS);
        // Center access plate
        p.rect(5.0, 5.0, 11.0, 11.0, at(IRON, 3.4));
        p.frame(5.0, 5.0, 11.0, 11.0, 0.35, at(BRASS, 4.2));
        return p;
    }

    // ============================================================================================ 5. Identity Punch Card Item
    static Paint identityPunchCard() {
        Paint p = new Paint(16, 16, 2);
        Items.shadow(p, 8.4, 13.6, 7.2, 1.2);
        double x0 = 1.0, x1 = 15.0, y0 = 3.0, y1 = 13.0;

        // Bevelled top-left corner
        double[] xs = {x0 + 1.8, x1, x1, x0, x0}, ys = {y0, y0, y1, y1, y0 + 1.8};
        p.poly(xs, ys, at(COPPER, 3.8));
        p.grain(x0, y0, x1, y1, 0.03, 501);

        // Brass edging
        p.line(x0 + 1.8, y0 + 0.2, x1, y0 + 0.2, 0.35, at(BRASS, 5.0));
        p.line(x0 + 0.2, y0 + 1.8, x0 + 0.2, y1, 0.35, at(BRASS, 4.5));
        p.line(x0 + 0.2, y0 + 1.8, x0 + 1.8, y0 + 0.2, 0.35, at(BRASS, 5.5));
        p.rect(x0, y1 - 0.35, x1, y1, at(BRASS, 2.5));
        p.rect(x1 - 0.35, y0, x1, y1, at(BRASS, 2.8));

        // Intricate fingerprint / portrait punched hole pattern with glowing cyan backlight
        double[][] holes = {
                {3.0, 5.0}, {5.0, 5.0}, {7.0, 5.0},
                {2.5, 7.0}, {4.5, 7.0}, {6.5, 7.0}, {8.5, 7.0},
                {3.0, 9.0}, {5.0, 9.0}, {7.0, 9.0},
                {4.0, 11.0}, {6.0, 11.0}
        };
        for (double[] h : holes) {
            p.disc(h[0], h[1], 0.5, at(IRON, 1.2));
            p.disc(h[0], h[1], 0.3, at(CYAN, 5.5));
        }

        // Crimson wax seal on right side
        double wx = 11.2, wy = 8.0;
        p.disc(wx, wy, 2.4, at(WAX, 2));
        p.disc(wx, wy, 2.0, at(WAX, 3));
        p.disc(wx, wy, 1.5, at(WAX, 1));
        // Stamped gold occult eye monogram
        p.disc(wx, wy, 0.65, at(GOLD, 4.5));
        p.disc(wx, wy, 0.3, at(GOLD, 6.0));

        // Frost on top right corner
        p.disc(14.0, 3.5, 0.6, at(FROST, 5.0));
        return p;
    }

    // ============================================================================================ 3D Models
    static void models() throws Exception {
        // ---------------------------------------------------------------------------------------- 1. Stock Manometer (Variant B)
        String[][] mt = {
                {"front", "stock_manometer_front"},
                {"casing", "stock_manometer_side"},
                {"top", "stock_manometer_top"},
                {"dial", "stock_manometer_dial"},
                {"brass", "sat_brass"},
                {"glass", "stock_manometer_glass"},
                {"fluid", "stock_manometer_fluid"}
        };
        List<Models.El> me = new ArrayList<>();
        // Main base plinth
        me.add(Models.e(0, 0, 1, 16, 2, 16)
                .only("#casing", "south", "east", "west", "down")
                .face("north", "#front"));
        // Main chassis body
        me.add(Models.e(1, 2, 1, 15, 14, 16)
                .only("#casing", "south", "east", "west")
                .face("north", "#front"));
        // Top crown plate
        me.add(Models.e(0, 14, 1, 16, 16, 16)
                .only("#casing", "south", "east", "west")
                .face("north", "#front")
                .face("up", "#top"));

        // 4 Stepped brass front corner brackets
        me.add(Models.e(0, 0, 0, 3.0, 3.0, 1).only("#brass", "north", "east", "up"));
        me.add(Models.e(13.0, 0, 0, 16, 3.0, 1).only("#brass", "north", "west", "up"));
        me.add(Models.e(0, 13.0, 0, 3.0, 16, 1).only("#brass", "north", "east", "down"));
        me.add(Models.e(13.0, 13.0, 0, 16, 16, 1).only("#brass", "north", "west", "down"));

        // Left cryo manometer tube: brass mounts, cyan glowing fluid, frosted glass tube
        me.add(Models.e(1.2, 2.0, 0.2, 3.2, 3.5, 1.0).only("#brass", "north", "east", "west", "up"));
        me.add(Models.e(1.2, 12.5, 0.2, 3.2, 14.0, 1.0).only("#brass", "north", "east", "west", "down"));
        me.add(Models.e(1.5, 3.5, 0.4, 2.9, 10.5, 0.8).only("#fluid", "north", "east", "west", "up").glow(15));
        me.add(Models.e(1.2, 3.5, 0.2, 3.2, 12.5, 1.0).only("#glass", "north", "east", "west"));

        // Right cryo manometer tube
        me.add(Models.e(12.8, 2.0, 0.2, 14.8, 3.5, 1.0).only("#brass", "north", "east", "west", "up"));
        me.add(Models.e(12.8, 12.5, 0.2, 14.8, 14.0, 1.0).only("#brass", "north", "east", "west", "down"));
        me.add(Models.e(13.1, 3.5, 0.4, 14.5, 8.5, 0.8).only("#fluid", "north", "east", "west", "up").glow(15));
        me.add(Models.e(12.8, 3.5, 0.2, 14.8, 12.5, 1.0).only("#glass", "north", "east", "west"));

        // Central brass dial bezel rim (spans z: -0.4 to 0.0)
        me.add(Models.e(4.5, 11.0, -0.4, 11.5, 11.5, 0.0).only("#brass", "north", "up", "down", "east", "west"));
        me.add(Models.e(4.5, 4.5, -0.4, 11.5, 5.0, 0.0).only("#brass", "north", "up", "down", "east", "west"));
        me.add(Models.e(4.5, 5.0, -0.4, 5.0, 11.0, 0.0).only("#brass", "north", "up", "down", "east", "west"));
        me.add(Models.e(11.0, 5.0, -0.4, 11.5, 11.0, 0.0).only("#brass", "north", "up", "down", "east", "west"));
        // Dial housing sides
        me.add(Models.e(4.5, 4.5, 0.0, 11.5, 11.5, 1.0).only("#brass", "east", "west", "up", "down"));
        // Recessed dial face at z = 0.0 (needle in renderer is at z = -0.02)
        me.add(Models.e(5.0, 5.0, 0.0, 11.0, 11.0, 0.05).face("north", "#dial", 0, 0, 16, 16));
        // Center brass boss behind needle pivot
        me.add(Models.e(7.4, 7.4, -0.15, 8.6, 8.6, 0.0).only("#brass", "north", "east", "west", "up", "down"));

        // Bottom redstone terminal conduit & jewel
        me.add(Models.e(6.5, 0.5, 0.2, 9.5, 2.0, 1.0).only("#brass", "north", "east", "west", "up", "down"));
        me.add(Models.e(7.5, 0.8, 0.0, 8.5, 1.7, 0.2).face("north", "#front").glow(12));

        // Top cable conduit fitting
        me.add(Models.e(6.5, 16.0, 6.5, 9.5, 17.5, 9.5).only("#brass", "north", "south", "east", "west", "up"));

        Models.model("stock_manometer", true, "stock_manometer_front", mt, me.toArray(new Models.El[0]));
        Models.facing6("stock_manometer", "stock_manometer");
        Models.item("stock_manometer", "stock_manometer");

        // ---------------------------------------------------------------------------------------- 2. Stock Requester / Ledger (Variant 2 / B)
        String[][] rt = {
                {"top", "stock_requester_top"},
                {"front", "stock_requester_front"},
                {"side", "stock_requester_side"},
                {"book", "stock_requester_book"},
                {"golem", "stock_requester_golem"},
                {"card", "stock_requester_card"},
                {"brass", "sat_brass"}
        };
        List<Models.El> re = new ArrayList<>();
        // 4 Corner desk feet
        re.add(Models.e(1.0, 0, 1.5, 3.5, 2.0, 3.5).only("#side", "north", "south", "east", "west", "down"));
        re.add(Models.e(12.5, 0, 1.5, 15.0, 2.0, 3.5).only("#side", "north", "south", "east", "west", "down"));
        re.add(Models.e(1.0, 0, 13.0, 3.5, 2.0, 15.0).only("#side", "north", "south", "east", "west", "down"));
        re.add(Models.e(12.5, 0, 13.0, 15.0, 2.0, 15.0).only("#side", "north", "south", "east", "west", "down"));
        // Plinth shelf base
        re.add(Models.e(1.5, 1.0, 2.0, 14.5, 2.5, 14.5).only("#side", "north", "south", "east", "west", "down"));
        // Filing drawer cabinet body
        re.add(Models.e(1.5, 2.5, 2.0, 14.5, 9.5, 14.5)
                .face("north", "#front")
                .only("#side", "south", "east", "west"));

        // Slanted lectern desk steps (sloping upward from front to back)
        re.add(Models.e(0.5, 9.5, 0.5, 15.5, 10.5, 3.5).only("#top", "north", "south", "east", "west", "up", "down"));
        re.add(Models.e(2.0, 10.5, 1.5, 14.0, 11.2, 2.2).only("#brass", "north", "south", "east", "west", "up")); // Book-stop lip
        re.add(Models.e(1.0, 10.5, 3.5, 15.0, 11.5, 8.5).only("#top", "north", "east", "west", "up"));
        re.add(Models.e(1.0, 11.5, 8.5, 15.0, 12.5, 13.5).only("#top", "north", "east", "west", "up"));
        re.add(Models.e(1.0, 12.5, 13.5, 15.0, 13.5, 15.5).only("#top", "north", "south", "east", "west", "up"));

        // Open Ledger Book lying on the desk
        re.add(Models.e(2.5, 11.5, 3.8, 10.5, 11.8, 10.5).only("#book", "north", "south", "east", "west", "down"));
        re.add(Models.e(2.6, 11.8, 4.0, 6.4, 12.0, 10.3).only("#book", "north", "south", "east", "west", "up")); // Left page
        re.add(Models.e(6.6, 11.8, 4.0, 10.4, 12.0, 10.3).only("#book", "north", "south", "east", "west", "up")); // Right page
        re.add(Models.e(6.4, 11.8, 4.0, 6.6, 11.9, 10.3).only("#book", "north", "south", "up")); // Spine crease

        // Brass inkwell and stylus
        re.add(Models.e(11.5, 11.5, 4.0, 13.5, 13.0, 6.0).only("#brass", "north", "south", "east", "west", "up"));
        re.add(Models.e(12.2, 13.0, 4.6, 12.8, 15.5, 5.4).only("#brass", "north", "south", "east", "west", "up"));

        // Punch card slot & inserted punch card
        re.add(Models.e(11.2, 12.5, 7.5, 14.5, 13.0, 10.5).only("#brass", "north", "south", "east", "west", "up"));
        re.add(Models.e(11.8, 13.0, 8.2, 14.0, 15.2, 8.8)
                .face("north", "#card")
                .face("south", "#card")
                .face("up", "#card")
                .face("east", "#card")
                .face("west", "#card")
                .glow(10));

        // Miniature Copper Golem Clerk Puppet (standing behind the desk)
        re.add(Models.e(5.5, 11.5, 12.5, 10.5, 13.5, 15.0).only("#golem", "north", "south", "east", "west")); // Torso
        re.add(Models.e(5.0, 13.5, 11.0, 11.0, 16.5, 15.5)
                .face("north", "#golem", 0, 0, 16, 16)
                .only("#golem", "south", "east", "west", "up")); // Head with face
        re.add(Models.e(7.4, 13.7, 10.2, 8.6, 15.0, 11.0).only("#golem", "north", "east", "west", "up", "down")); // Nose
        re.add(Models.e(7.5, 16.5, 13.0, 8.5, 18.0, 14.0).only("#brass", "north", "south", "east", "west")); // Antenna rod
        re.add(Models.e(7.0, 18.0, 12.5, 9.0, 19.5, 14.5).only("#brass", "north", "south", "east", "west", "up", "down")); // Antenna ball
        re.add(Models.e(3.6, 11.5, 10.5, 5.0, 13.5, 13.5).only("#golem", "north", "south", "east", "west", "up")); // Left arm
        re.add(Models.e(11.0, 11.5, 10.5, 12.4, 13.5, 13.5).only("#golem", "north", "south", "east", "west", "up")); // Right arm

        Models.model("stock_requester", false, "stock_requester_top", rt, re.toArray(new Models.El[0]));
        Models.facing4("stock_requester", "stock_requester");
        Models.item("stock_requester", "stock_requester");

        // ---------------------------------------------------------------------------------------- 3. Cryo-Diode (Variant 1 / A)
        String[][] dt = {
                {"outlet", "cryo_diode_outlet"},
                {"inlet", "cryo_diode_inlet"},
                {"side", "cryo_diode_side"},
                {"glass", "cryo_diode_glass"},
                {"core", "cryo_diode_core"},
                {"handwheel", "cryo_diode_handwheel"},
                {"brass", "sat_brass"}
        };
        List<Models.El> de = new ArrayList<>();
        // Inlet Flange Collar (South, z: 14.5..16.0)
        de.add(Models.e(3.0, 3.0, 14.5, 13.0, 13.0, 16.0)
                .face("south", "#inlet")
                .only("#brass", "north", "east", "west", "up", "down"));
        // Flange corner bolts
        de.add(Models.e(3.2, 3.2, 14.0, 4.8, 4.8, 14.5).only("#brass", "north", "east", "west", "down"));
        de.add(Models.e(11.2, 3.2, 14.0, 12.8, 4.8, 14.5).only("#brass", "north", "east", "west", "down"));
        de.add(Models.e(3.2, 11.2, 14.0, 4.8, 12.8, 14.5).only("#brass", "north", "east", "west", "up"));
        de.add(Models.e(11.2, 11.2, 14.0, 12.8, 12.8, 14.5).only("#brass", "north", "east", "west", "up"));

        // Frosted Quartz Glass Section (z: 8.5..14.5)
        de.add(Models.e(5.0, 5.0, 8.5, 11.0, 11.0, 14.5).only("#core", "east", "west", "up", "down").glow(15)); // Glowing cryo stream
        de.add(Models.e(4.0, 4.0, 8.5, 12.0, 12.0, 14.5).only("#glass", "east", "west", "up", "down")); // Frosted quartz glass
        // Brass retention bands
        de.add(Models.e(3.8, 3.8, 8.5, 12.2, 12.2, 9.2).only("#brass", "north", "east", "west", "up", "down"));
        de.add(Models.e(3.8, 3.8, 13.8, 12.2, 12.2, 14.5).only("#brass", "south", "east", "west", "up", "down"));

        // Central Valve Body & Handwheel (z: 4.5..8.5)
        de.add(Models.e(3.5, 3.5, 4.5, 12.5, 12.5, 8.5)
                .only("#side", "north", "south", "east", "west", "down")
                .face("up", "#brass")
                .glow(12)); // Directional arrows on sides glow
        de.add(Models.e(6.5, 12.5, 5.5, 9.5, 14.2, 7.5).only("#brass", "north", "south", "east", "west")); // Bonnet neck
        de.add(Models.e(7.0, 14.2, 6.0, 9.0, 15.5, 7.0).only("#brass", "north", "south", "east", "west", "up")); // Hub
        de.add(Models.e(7.4, 14.65, 4.5, 8.6, 15.15, 8.5).only("#brass", "east", "west")); // Spoke NS (inner sides)
        de.add(Models.e(5.0, 14.65, 5.9, 11.0, 15.15, 7.1).only("#brass", "north", "south")); // Spoke EW (inner sides)
        de.add(Models.e(4.5, 14.6, 4.5, 11.5, 15.2, 8.5)
                .face("up", "#handwheel")
                .face("down", "#handwheel")
                .only("#brass", "north", "south", "east", "west")); // Wheel rim

        // Outlet Nozzle & Aperture (North, z: 0.0..4.5)
        de.add(Models.e(4.0, 4.0, 1.5, 12.0, 12.0, 4.5).only("#brass", "east", "west", "up", "down")); // Tapered nozzle
        de.add(Models.e(4.5, 4.5, 0.0, 11.5, 11.5, 1.5)
                .face("north", "#outlet")
                .only("#brass", "east", "west", "up", "down")
                .glow(14)); // Glowing outlet aperture

        Models.model("cryo_diode", true, "cryo_diode_side", dt, de.toArray(new Models.El[0]));
        Models.facing6("cryo_diode", "cryo_diode");
        Models.item("cryo_diode", "cryo_diode");

        // ---------------------------------------------------------------------------------------- 4. Security Terminal (Variant 3)
        String[][] st = {
                {"front", "security_terminal_front"},
                {"side", "security_terminal_side"},
                {"screen", "security_terminal_screen"},
                {"dial", "security_terminal_dial"},
                {"top", "security_terminal_top"},
                {"card", "stock_requester_card"},
                {"brass", "sat_brass"}
        };
        List<Models.El> se = new ArrayList<>();
        // Flared Base Plinth (y: 0..3)
        se.add(Models.e(0, 0, 0, 16, 2.5, 16).only("#side", "north", "south", "east", "west", "down"));
        se.add(Models.e(0.5, 2.5, 0.5, 15.5, 3.0, 15.5).only("#side", "north", "south", "east", "west"));

        // Main Vault Body (y: 3..14)
        se.add(Models.e(1.5, 3.0, 1.5, 14.5, 14.0, 15.5)
                .face("north", "#front")
                .only("#side", "south", "east", "west"));

        // 4 Heavy Riveted Corner Pilasters
        se.add(Models.e(0, 2.5, 0, 2.5, 14.5, 2.5).only("#side", "north", "west", "up", "down"));
        se.add(Models.e(13.5, 2.5, 0, 16, 14.5, 2.5).only("#side", "north", "east", "up", "down"));
        se.add(Models.e(0, 2.5, 13.5, 2.5, 14.5, 16).only("#side", "south", "west", "up", "down"));
        se.add(Models.e(13.5, 2.5, 13.5, 16, 14.5, 16).only("#side", "south", "east", "up", "down"));

        // Heavy vault hinges on West side
        se.add(Models.e(0.2, 10.5, 1.2, 1.5, 12.5, 3.2).only("#brass", "north", "south", "west", "up", "down"));
        se.add(Models.e(0.2, 4.5, 1.2, 1.5, 6.5, 3.2).only("#brass", "north", "south", "west", "up", "down"));

        // Flared Top Crown (y: 14..16)
        se.add(Models.e(0.5, 14.0, 0.5, 15.5, 15.0, 15.5).only("#side", "north", "south", "east", "west"));
        se.add(Models.e(0, 15.0, 0, 16, 16.0, 16)
                .only("#side", "north", "south", "east", "west")
                .face("up", "#top"));

        // Green Phosphor CRT Screen Hood & Face (upper left)
        se.add(Models.e(2.8, 12.8, 0.2, 9.2, 13.5, 1.5).only("#brass", "north", "east", "west", "up", "down"));
        se.add(Models.e(2.8, 7.5, 0.2, 9.2, 8.2, 1.5).only("#brass", "north", "east", "west", "up", "down"));
        se.add(Models.e(2.8, 8.2, 0.2, 3.5, 12.8, 1.5).only("#brass", "north", "east", "west", "up", "down"));
        se.add(Models.e(8.5, 8.2, 0.2, 9.2, 12.8, 1.5).only("#brass", "north", "east", "west", "up", "down"));
        se.add(Models.e(3.5, 8.2, 1.0, 8.5, 12.8, 1.05)
                .face("north", "#screen", 0, 0, 16, 16)
                .glow(15)); // Glowing green radar CRT

        // Rotary Brass Combination Dial Lock (upper right)
        se.add(Models.e(10.2, 8.5, 0.5, 14.2, 12.5, 1.5).only("#brass", "north", "east", "west", "up", "down"));
        se.add(Models.e(10.8, 9.1, -0.2, 13.6, 11.9, 0.5)
                .face("north", "#dial", 0, 0, 16, 16)
                .only("#brass", "east", "west", "up", "down"));
        se.add(Models.e(11.8, 9.5, -0.6, 12.6, 11.5, -0.2).only("#brass", "north", "south", "east", "west", "up", "down"));

        // Identity Card Slot & Inserted Card (lower front)
        se.add(Models.e(3.2, 4.2, 0.5, 12.8, 6.5, 1.5).only("#brass", "north", "east", "west", "up", "down"));
        se.add(Models.e(3.8, 4.9, 0.3, 12.2, 5.8, 0.5).only("#side", "north", "east", "west", "up", "down"));
        se.add(Models.e(4.5, 5.1, -0.3, 11.5, 5.6, 0.5)
                .face("north", "#card")
                .face("south", "#card")
                .face("up", "#card")
                .face("down", "#card")
                .glow(10));

        Models.model("security_terminal", false, "security_terminal_front", st, se.toArray(new Models.El[0]));
        Models.facing4("security_terminal", "security_terminal");
        Models.item("security_terminal", "security_terminal");

        // ---------------------------------------------------------------------------------------- 5. Identity Punch Card item
        String itemJson = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/identity_punch_card\"\n  }\n}\n";
        Forge2.write(Forge2.RES.resolve("models/item/identity_punch_card.json"), itemJson);
    }

    public static void all() throws Exception {
        manometerFront().save(tex("stock_manometer_front"));
        manometerSide().save(tex("stock_manometer_side"));
        manometerTop().save(tex("stock_manometer_top"));
        manometerDial().save(tex("stock_manometer_dial"));
        manometerGlass().save(tex("stock_manometer_glass"));
        manometerFluid().save(tex("stock_manometer_fluid"));
        manometerNeedle().save(entityTex("stock_manometer_needle"));

        requesterTop().save(tex("stock_requester_top"));
        requesterFront().save(tex("stock_requester_front"));
        requesterSide().save(tex("stock_requester_side"));
        requesterBook().save(tex("stock_requester_book"));
        requesterGolem().save(tex("stock_requester_golem"));
        requesterCard().save(tex("stock_requester_card"));

        cryoDiodeSide().save(tex("cryo_diode_side"));
        cryoDiodeOutlet().save(tex("cryo_diode_outlet"));
        cryoDiodeInlet().save(tex("cryo_diode_inlet"));
        cryoDiodeGlass().save(tex("cryo_diode_glass"));
        cryoDiodeCore().save(tex("cryo_diode_core"));
        cryoDiodeHandwheel().save(tex("cryo_diode_handwheel"));

        securityFront().save(tex("security_terminal_front"));
        securitySide().save(tex("security_terminal_side"));
        securityScreen().save(tex("security_terminal_screen"));
        securityDial().save(tex("security_terminal_dial"));
        securityTop().save(tex("security_terminal_top"));

        identityPunchCard().save(itemTex("identity_punch_card"));

        models();
    }
}
