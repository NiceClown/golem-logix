import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Punch Card Catalog and Cryo Schematic Concentrator.
 * <p>
 * Catalog: a rusty copper cabinet with brass card drawers (paper labels, brass bracket handles), in the middle
 * a teal porthole screen with a pointer running over the card list, letter drums on the roof. The assembled cabinet with
 * golems is drawn by the renderer (textures from here); the model here is the unassembled owner (and the icon).
 */
final class Catalog {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, COPPER = Paint.COPPER, IRON = Paint.IRON, CYAN = Paint.CYAN, CREAM = Paint.CREAM,
            BRONZE = Paint.BRONZE;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    /** A drawer front (drawn squeezed into 8 x 5 pixels): rusty copper in a brass frame, a paper label, a bracket handle. */
    static Paint drawer(long seed) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.2, 1);
        p.slab(0.9, 1.4, 15.1, 14.6, COPPER, 2.1, 1);
        p.grain(1.0, 1.5, 15.0, 14.5, 0.05, seed);
        // the brass label frame and the label itself with "writing"
        p.slab(4.0, 2.6, 12.0, 7.6, BRASS, 3.0, 1);
        p.rect(4.6, 3.4, 11.4, 6.8, rgb(0xE8D8B0));
        for (int i = 0; i < 2; i++) {
            double y = 4.1 + i * 1.4;
            p.rect(5.2, y, 5.2 + (i == 0 ? 5.2 : 4.0), y + 0.5, rgb(0x4a3322));
        }
        // bracket handle
        p.rect(5.0, 10.0, 11.0, 11.0, at(BRASS, 4.4));
        p.rect(5.0, 11.0, 11.0, 12.6, at(BRASS, 2.8));
        p.rect(5.0, 12.6, 11.0, 13.0, at(BRASS, 1.2));
        p.rivet(5.0, 11.4, 0.6, BRASS);
        p.rivet(11.0, 11.4, 0.6, BRASS);
        // green patina in the bottom corners
        p.rect(1.0, 13.2, 2.6, 14.6, argb(150, 0x3fa38a));
        p.rect(13.6, 12.6, 15.0, 14.6, argb(120, 0x3fa38a));
        return p;
    }

    /** Letter drum band: brass cells with dark "letters" (runes and digits), 4 in a row. */
    static Paint drum() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.8, 1);
        for (int row = 0; row < 4; row++) {
            double y = row * 4;
            p.rect(0, y, 16, y + 0.4, at(BRASS, 1.0));
            p.rect(0, y + 3.6, 16, y + 4, at(BRASS, 4.6));
            for (int col = 0; col < 4; col++) {
                double x = col * 4 + 0.9;
                p.rect(x - 0.3, y + 0.8, x + 2.5, y + 3.3, rgb(0xE8D8B0));
                if ((row + col) % 2 == 0) {
                    Terminals.digitPlain(p, x + 0.3, y + 1.0, (row * 4 + col * 3) % 10, 0.4, rgb(0x2a1a10));
                } else {
                    p.rune(x + 0.2, y + 1.0, 2.0, row * 5 + col + 1, rgb(0x2a1a10), rgb(0x6a5040));
                }
            }
        }
        return p;
    }

    static void dot(Paint q, int px, int py, int c) {
        if (Math.hypot(px - 32, py - 32) <= 21.6) {
            q.px(px, py, c);
        }
    }

    static void drawArchivistScreenFrame(Paint q, int f) {
        int ox = 16, oy = 16;
        // 1. Phosphor horizontal CRT raster lines
        for (int y = 12; y <= 52; y += 2) {
            for (int x = 12; x <= 52; x++) {
                if (Math.hypot(x - 32, y - 32) <= 21.2) {
                    q.px(x, y, 0xFF08222A);
                }
            }
        }
        // 2. Oscilloscope vector data lines at the top of the screen
        int[] lines = {14, 17};
        for (int ly : lines) {
            for (int x = 20; x <= 44; x++) {
                if ((x + ly * 3 + f * 5) % 7 != 0 && Math.hypot(x - 32, ly - 32) <= 20.5) {
                    q.px(x, ly, 0xFF145C62);
                }
            }
        }

        // Palette of the azure analog terminal (monochrome phosphor of different intensity)
        int PHOS_BG = 0xFF051820;     // deep screen background
        int PHOS_D = 0xFF0E4348;      // shadow outline of the silhouette
        int PHOS_BASE = 0xFF1B7A80;   // base azure body of the golem
        int PHOS_L = 0xFF36C0BA;      // light outlines/edges/details
        int PHOS_HOT = 0xFF8EF2EE;    // bright glowing beam, eye, highlight
        int PHOS_WHITE = 0xFFE4FFFE;  // phosphor glow peak
        int CARD_BG = 0xFF18686E;     // punch card
        int CARD_D = 0xFF0E3E42;      // card edge
        int CARD_HOLE = 0xFF051820;   // perforation holes (the screen showing through)
        int WOOD = 0xFF124C52;        // catalog drawer
        int WOOD_D = 0xFF09292D;

        // Base silhouette of the azure golem:
        // Legs / pedestal (y = 25..29)
        for (int y = 25; y <= 29; y++) {
            for (int x = 12; x <= 19; x++) {
                int c = (x == 12 || x == 19 || y == 29) ? PHOS_D : PHOS_BASE;
                dot(q, ox + x, oy + y, c);
            }
        }
        // Body / torso (y = 16..24, x = 10..21)
        for (int y = 16; y <= 24; y++) {
            for (int x = 10; x <= 21; x++) {
                int c = PHOS_BASE;
                if (x == 10 || y == 24) c = PHOS_D;
                else if (x == 21 || y == 16) c = PHOS_L;
                dot(q, ox + x, oy + y, c);
            }
        }
        // Rivets / indicators on the body
        dot(q, ox + 12, oy + 18, PHOS_L);
        dot(q, ox + 19, oy + 18, PHOS_L);
        dot(q, ox + 12, oy + 22, PHOS_HOT);
        dot(q, ox + 19, oy + 22, PHOS_HOT);

        if (f == 0 || f == 1) {
            // Phase 1: digs in the drawer of punch cards
            // Head leaning forward (y = 9..15, x = 11..20)
            for (int y = 9; y <= 15; y++) {
                for (int x = 11; x <= 20; x++) {
                    int c = (x == 11 || y == 15) ? PHOS_D : PHOS_BASE;
                    dot(q, ox + x, oy + y, c);
                }
            }
            // Antenna with a ball
            dot(q, ox + 15, oy + 6, PHOS_HOT);
            dot(q, ox + 15, oy + 7, PHOS_L);
            dot(q, ox + 15, oy + 8, PHOS_BASE);
            // The eyes look down at the papers
            dot(q, ox + 13, oy + 13, PHOS_WHITE);
            dot(q, ox + 14, oy + 13, PHOS_HOT);
            dot(q, ox + 17, oy + 13, PHOS_WHITE);
            dot(q, ox + 18, oy + 13, PHOS_HOT);

            // An open catalog drawer in front of the golem (y = 18..27, x = 6..25)
            for (int y = 18; y <= 27; y++) {
                for (int x = 6; x <= 25; x++) {
                    int c = (x == 6 || x == 25 || y == 27) ? WOOD_D : (y == 18 ? PHOS_L : WOOD);
                    dot(q, ox + x, oy + y, c);
                }
            }
            // Drawer handle
            for (int x = 14; x <= 17; x++) dot(q, ox + x, oy + 23, PHOS_L);

            // Arms lowered into the drawer
            int armY = f == 0 ? 17 : 16;
            for (int y = armY; y <= 22; y++) {
                dot(q, ox + 8, oy + y, PHOS_BASE);
                dot(q, ox + 9, oy + y, PHOS_L);
                dot(q, ox + 22, oy + y, PHOS_D);
                dot(q, ox + 23, oy + y, PHOS_BASE);
            }

            // Flying punch cards
            int c1y = f == 0 ? 11 : 8;
            int c2y = f == 0 ? 8 : 6;
            // Card 1
            for (int cy = 0; cy < 3; cy++) {
                for (int cx = 0; cx < 5; cx++) {
                    dot(q, ox + 7 + cx, oy + c1y + cy, CARD_BG);
                }
            }
            dot(q, ox + 8, oy + c1y + 1, CARD_HOLE);
            dot(q, ox + 10, oy + c1y + 1, CARD_HOLE);

            // Card 2
            for (int cy = 0; cy < 3; cy++) {
                for (int cx = 0; cx < 5; cx++) {
                    dot(q, ox + 20 + cx, oy + c2y + cy, CARD_BG);
                }
            }
            dot(q, ox + 21, oy + c2y + 1, CARD_HOLE);
            dot(q, ox + 23, oy + c2y + 1, CARD_HOLE);

            if (f == 1) {
                // A third card in the air
                for (int cy = 0; cy < 3; cy++) {
                    for (int cx = 0; cx < 4; cx++) {
                        dot(q, ox + 14 + cx, oy + 9 + cy, CARD_BG);
                    }
                }
                dot(q, ox + 15, oy + 10, CARD_HOLE);
            }
        } else if (f == 2 || f == 3) {
            // Phase 2: examines a punch card through a monocle
            // Head straight (y = 8..15, x = 11..20)
            for (int y = 8; y <= 15; y++) {
                for (int x = 11; x <= 20; x++) {
                    int c = (x == 11 || y == 15) ? PHOS_D : PHOS_BASE;
                    dot(q, ox + x, oy + y, c);
                }
            }
            // Antenna
            dot(q, ox + 15, oy + 5, PHOS_HOT);
            dot(q, ox + 15, oy + 6, PHOS_L);
            dot(q, ox + 15, oy + 7, PHOS_BASE);

            // Right eye
            dot(q, ox + 18, oy + 11, PHOS_HOT);

            // Left eye with a big monocle lens
            for (int my = -2; my <= 2; my++) {
                for (int mx = -2; mx <= 2; mx++) {
                    double d = Math.hypot(mx, my);
                    if (d >= 1.8 && d <= 2.5) {
                        dot(q, ox + 14 + mx, oy + 11 + my, PHOS_L);
                    } else if (d < 1.8) {
                        dot(q, ox + 14 + mx, oy + 11 + my, (f == 3 && mx == -1 && my == -1) ? PHOS_WHITE : 0xFF14585E);
                    }
                }
            }
            if (f == 3) {
                // Highlight on the monocle
                dot(q, ox + 14, oy + 9, PHOS_WHITE);
                dot(q, ox + 14, oy + 13, PHOS_WHITE);
                dot(q, ox + 12, oy + 11, PHOS_WHITE);
                dot(q, ox + 16, oy + 11, PHOS_WHITE);
            }

            // The hands hold a punch card in front of the face
            for (int y = 14; y <= 19; y++) {
                for (int x = 10; x <= 21; x++) {
                    int c = CARD_BG;
                    if (x == 10 || x == 21 || y == 14 || y == 19) c = CARD_D;
                    if ((x + y) % 3 == 0 && x > 11 && x < 20 && y > 14 && y < 19) c = CARD_HOLE;
                    dot(q, ox + x, oy + y, c);
                }
            }
            // The golem's fingers over the card
            dot(q, ox + 9, oy + 16, PHOS_L);
            dot(q, ox + 9, oy + 17, PHOS_BASE);
            dot(q, ox + 22, oy + 16, PHOS_L);
            dot(q, ox + 22, oy + 17, PHOS_BASE);
        } else if (f == 4 || f == 5) {
            // Phase 3: holds a punch card up to the light above its head
            // Head thrown back (y = 9..16, x = 11..20)
            for (int y = 9; y <= 16; y++) {
                for (int x = 11; x <= 20; x++) {
                    int c = (x == 11 || y == 16) ? PHOS_D : PHOS_BASE;
                    dot(q, ox + x, oy + y, c);
                }
            }
            // The eyes look up
            dot(q, ox + 13, oy + 10, PHOS_WHITE);
            dot(q, ox + 14, oy + 10, PHOS_HOT);
            dot(q, ox + 17, oy + 10, PHOS_WHITE);
            dot(q, ox + 18, oy + 10, PHOS_HOT);

            // The arms are raised to the sky
            for (int y = 7; y <= 16; y++) {
                dot(q, ox + 8, oy + y, PHOS_L);
                dot(q, ox + 9, oy + y, PHOS_BASE);
                dot(q, ox + 22, oy + y, PHOS_BASE);
                dot(q, ox + 23, oy + y, PHOS_D);
            }

            // A punch card high above the head (y = 4..8, x = 9..22)
            for (int y = 4; y <= 8; y++) {
                for (int x = 9; x <= 22; x++) {
                    int c = CARD_BG;
                    if (x == 9 || x == 22 || y == 4 || y == 8) c = CARD_D;
                    if ((x + y) % 3 == 0 && x > 10 && x < 21) c = CARD_HOLE;
                    dot(q, ox + x, oy + y, c);
                }
            }

            // Light beams shining down through the perforation
            int beamCol = f == 4 ? 0xFF248E94 : 0xFF45D6D2;
            for (int x = 10; x <= 21; x += 3) {
                for (int y = 9; y <= 15; y++) {
                    dot(q, ox + x, oy + y, beamCol);
                }
            }
        } else {
            // Phase 4: inserts a punch card into an archive cell
            // Head turned to the right
            for (int y = 8; y <= 15; y++) {
                for (int x = 9; x <= 18; x++) {
                    int c = (x == 9 || y == 15) ? PHOS_D : PHOS_BASE;
                    dot(q, ox + x, oy + y, c);
                }
            }
            // Antenna
            dot(q, ox + 13, oy + 5, PHOS_HOT);
            dot(q, ox + 13, oy + 6, PHOS_L);
            dot(q, ox + 13, oy + 7, PHOS_BASE);
            // The eyes look at the catalog
            dot(q, ox + 15, oy + 11, PHOS_WHITE);
            dot(q, ox + 16, oy + 11, PHOS_HOT);

            // Catalog rack on the right (y = 6..28, x = 22..29)
            for (int y = 6; y <= 28; y++) {
                for (int x = 22; x <= 29; x++) {
                    int c = (x == 22 || x == 29) ? PHOS_L : (y % 4 == 0 ? PHOS_HOT : 0xFF082226);
                    dot(q, ox + x, oy + y, c);
                }
            }

            if (f == 6) {
                // The card is going into a slot (half visible)
                for (int y = 10; y <= 18; y++) {
                    for (int x = 19; x <= 24; x++) {
                        int c = CARD_BG;
                        if (x == 19 || y == 10) c = CARD_D;
                        if ((x + y) % 3 == 0) c = CARD_HOLE;
                        dot(q, ox + x, oy + y, c);
                    }
                }
                // The golem's hand inserts the card
                for (int x = 14; x <= 20; x++) {
                    dot(q, ox + x, oy + 14, PHOS_L);
                    dot(q, ox + x, oy + 15, PHOS_BASE);
                }
            } else {
                // The card is pushed in, the hand snaps the lock
                for (int x = 14; x <= 22; x++) {
                    dot(q, ox + x, oy + 14, PHOS_L);
                    dot(q, ox + x, oy + 15, PHOS_BASE);
                }
                // A spark of the lock catch
                dot(q, ox + 23, oy + 14, PHOS_WHITE);
                dot(q, ox + 23, oy + 13, PHOS_HOT);
                dot(q, ox + 23, oy + 15, PHOS_HOT);
                dot(q, ox + 22, oy + 14, PHOS_HOT);
                dot(q, ox + 24, oy + 14, PHOS_HOT);
            }
        }
    }

    /**
     * Porthole screen: a brass ring with teeth and rivets, and inside an animated azure CRT display
     * with a unique 8-phase animation of the archivist golem in the style of monochrome analog terminals.
     */
    static Paint screen() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 8;
        p.frameTime = 6;
        p.interpolate = false;

        // Outer brass bezel with teeth
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            p.disc(8 + Math.cos(a) * 7.1, 8 + Math.sin(a) * 7.1, 0.7, at(BRASS, 2.4));
        }
        p.disc(8, 8, 7.0, at(BRASS, 2.0));
        p.ring(8, 8, 5.9, 6.9, at(BRASS, 3.6));
        p.ring(8, 8, 6.55, 6.75, at(BRASS, 4.8));
        p.disc(8, 8, 5.9, at(IRON, 0.4));

        // CRT screen: a deep dark azure phosphor background
        p.discFn(8, 8, 5.5, (u, v) -> {
            double d = Math.hypot(u - 8, v - 8) / 5.5;
            return rgb(0x051820);
        });

        // The 8-phase animation of the azure archivist right in the CRT screen texture
        p.animate(2.5, 2.5, 13.5, 13.5, (q, t) -> {
            int f = (int) Math.floor(t * 8) % 8;
            drawArchivistScreenFrame(q, f);
        });

        // Porthole glass highlight over the screen
        p.line(3.4, 5.4, 5.8, 3.2, 0.3, argb(80, 0xE0FFFF));
        return p;
    }

    /** Front of the unassembled catalog (and the icon): 2 x 3 drawers in a brass frame, a round screen in the middle. */
    static Paint front() {
        Paint p = Terminals.plate(9911, COPPER, 2.2);
        p.slab(1.0, 1.0, 15.0, 15.0, BRASS, 2.5, 1);
        p.slab(1.6, 1.6, 14.4, 14.4, IRON, 0.5, 1);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                double x = col == 0 ? 1.9 : 10.1, y = 2.0 + row * 4.1;
                p.slab(x, y, x + 4.0, y + 3.7, COPPER, 2.3, 1);
                p.rect(x + 0.8, y + 0.6, x + 3.2, y + 1.6, rgb(0xE8D8B0));
                p.rect(x + 1.2, y + 2.3, x + 2.8, y + 2.8, at(BRASS, 4.0));
            }
        }
        // screen in the middle
        p.disc(8, 6.4, 2.3, at(BRASS, 3.0));
        p.disc(8, 6.4, 1.8, at(CYAN, 3.4));
        p.rect(6.9, 5.6, 9.1, 5.9, at(CYAN, 5.6));
        p.rect(6.9, 6.5, 8.6, 6.8, at(CYAN, 5.6));
        p.rect(6.9, 7.3, 8.9, 7.6, at(CYAN, 4.8));
        // levers under the screen
        for (double x : new double[]{6.6, 8.0, 9.4}) {
            p.rect(x - 0.15, 10.2, x + 0.15, 12.4, at(IRON, 2.0));
            p.disc(x, 10.1, 0.45, rgb(x == 8.0 ? 0xC8432F : 0xB8862B));
        }
        p.rect(5.8, 12.4, 10.2, 13.2, at(BRASS, 2.6));
        return p;
    }

    static void all() throws Exception {
        drawer(9921).save(Forge2.tex("catalog_drawer"));
        drum().save(Forge2.tex("catalog_drum"));
        screen().save(Forge2.tex("catalog_screen"));
        front().save(Forge2.tex("catalog_front"));
        catalogModel();
        concentratorFront().save(Forge2.tex("concentrator_front"));
        concentratorSide().save(Forge2.tex("concentrator_side"));
        holo().save(Forge2.entityTex("concentrator_holo"));
        holoCard().save(Forge2.entityTex("concentrator_card"));
        concentratorModel();
    }

    // ============================================================================================ Cryo Schematic Concentrator
    /** A colour with a black background instead of transparency (for "eyes": they add up with the light). */
    static int premul(double a, int c) {
        a = Math.max(0, Math.min(1, a));
        int rr = (int) (((c >> 16) & 0xFF) * a), gg = (int) (((c >> 8) & 0xFF) * a), bb = (int) ((c & 0xFF) * a);
        return argb(255, (rr << 16) | (gg << 8) | bb);
    }

    /**
     * Concentrator front: rusty copper, a big toothed brass rim around the dark screen glass (the renderer draws the hologram),
     * under it a slot for punch cards and a console: lamps and a lever.
     */
    static Paint concentratorFront() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.6, 1);
        p.slab(1.0, 1.0, 15.0, 15.0, COPPER, 2.3, 1);
        p.streaks(1.1, 1.1, 14.9, 14.9, 0.05, 9951, true);
        // screen rim (centre 8, 6.5 in the texture = 9.5 from the bottom)
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI / 9;
            p.disc(8 + Math.cos(a) * 6.3, 6.5 + Math.sin(a) * 6.3, 0.6, at(BRASS, 2.4));
        }
        p.disc(8, 6.5, 6.2, at(BRASS, 2.0));
        p.ring(8, 6.5, 5.3, 6.1, at(BRASS, 3.8));
        p.ring(8, 6.5, 5.75, 5.9, at(BRASS, 5.0));
        // the glint on the glass is mixed into the screen: a translucent texel would put the whole face into the translucent
        // layer, which 26.1 draws after the hologram and over it
        p.discFn(8, 6.5, 5.3, (u, v) -> {
            double d = Math.hypot(u - 8, v - 6.5) / 5.3;
            int c = Paint.ramp(CYAN, 0.3 + 0.5 * (1 - d));
            double t = Math.max(0, Math.min(1, ((u - 4.2) * 2.2 + (v - 4.6) * -2.0) / (2.2 * 2.2 + 2.0 * 2.0)));
            boolean glint = Math.hypot(u - (4.2 + 2.2 * t), v - (4.6 - 2.0 * t)) <= 0.15;
            return glint ? Paint.mix(c, rgb(0xFFFFFF), 90 / 255.0) : c;
        });
        // card slot, lamps, lever
        p.slab(4.0, 13.0, 12.0, 14.4, IRON, 0.6, 1);
        p.rect(4.6, 13.5, 11.4, 13.9, rgb(0x050505));
        p.rect(7.0, 13.2, 9.0, 13.5, at(COPPER, 4.6));
        p.disc(2.6, 13.6, 0.55, at(CYAN, 5.4));
        p.disc(13.4, 13.6, 0.55, rgb(0xC8432F));
        for (double[] rv : new double[][]{{1.9, 1.9}, {14.1, 1.9}, {1.9, 11.6}, {14.1, 11.6}}) {
            p.rivet(rv[0], rv[1], 0.45, BRASS);
        }
        p.frostLine(0, 16, 0, 0.7, 9953);
        return p;
    }

    /** Concentrator side: rusty copper and a big brass gear (it turns), a gauge. */
    static Paint concentratorSide() {
        Paint p = Resonator.body(9961, false);
        p.animFrames = 24;
        p.frameTime = 2;
        p.disc(8, 8.6, 5.6, at(IRON, 0.5));
        p.animate(1.8, 2.4, 14.2, 14.8, (q, t) -> q.gear(8, 8.6, 5.0, 12, t * Math.PI * 2 / 12, BRASS, 2.8));
        p.disc(8, 8.6, 1.4, at(BRASS, 4.4));
        p.disc(8, 8.6, 0.6, at(IRON, 0.3));
        return p;
    }

    /** Hologram rings: a schematic of concentric rings, runes and "perforation", a black background. */
    static Paint holo() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> argb(255, 0));
        p.fill(0, 0, 16, 16, (u, v) -> {
            double d = Math.hypot(u - 8, v - 8);
            double ring = Math.max(0, 1 - Math.abs(d - 7.0) / 0.35) + Math.max(0, 1 - Math.abs(d - 5.2) / 0.25) * 0.7
                    + Math.max(0, 1 - Math.abs(d - 3.0) / 0.2) * 0.8;
            double glow = d < 7.4 ? 0.12 * (1 - d / 7.4) : 0;
            return premul(Math.min(1, ring + glow), 0x8FF4F0);
        });
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            double rr = i % 2 == 0 ? 6.1 : 6.3;
            p.disc(8 + Math.cos(a) * rr, 8 + Math.sin(a) * rr, 0.28, premul(0.9, 0xC8FFFC));
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.3;
            p.line(8 + Math.cos(a) * 3.2, 8 + Math.sin(a) * 3.2, 8 + Math.cos(a) * 5.0, 8 + Math.sin(a) * 5.0, 0.2, premul(0.7, 0x8FF4F0));
            p.disc(8 + Math.cos(a) * 4.1, 8 + Math.sin(a) * 4.1, 0.45, premul(0.85, 0xE0FFFF));
        }
        p.disc(8, 8, 1.0, premul(0.6, 0xC8FFFC));
        return p;
    }

    /** Holographic punch card: a glowing outline and rows of punched holes. */
    static Paint holoCard() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            boolean inside = u > 2 && u < 14 && v > 1 && v < 15;
            double edge = inside ? Math.min(Math.min(u - 2, 14 - u), Math.min(v - 1, 15 - v)) : 0;
            double a = !inside ? 0 : edge < 0.7 ? 0.95 : 0.16;
            return premul(a, 0x8FF4F0);
        });
        java.util.Random r = new java.util.Random(9971);
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 5; col++) {
                if (r.nextDouble() < 0.55) {
                    p.rect(3.6 + col * 2.0, 3.0 + row * 1.9, 4.6 + col * 2.0, 3.9 + row * 1.9, premul(0.9, 0xE0FFFF));
                }
            }
        }
        p.rect(3.2, 13.2, 12.8, 13.6, premul(0.7, 0x8FF4F0));
        return p;
    }

    static void concentratorModel() throws Exception {
        List<Models.El> l = new ArrayList<>();
        // body: the front with the screen, the sides with the gear and the gauge
        l.add(Models.e(0, 0, 0, 16, 1, 16).all("#dark"));
        l.add(Models.e(0.3, 1, 0.3, 15.7, 15, 15.7).face("north", "#front", 0, 0, 16, 16).face("south", "#body", 0, 0, 16, 16)
                .face("west", "#side", 0, 0, 16, 16).face("east", "#side", 0, 0, 16, 16)
                .face("up", "#top", 0, 0, 16, 16).face("down", "#dark", 0, 0, 16, 16));
        l.add(Models.e(0, 15, 0, 16, 16, 16).all("#brass"));
        // console under the screen: a slanted brass shelf with buttons
        l.add(Models.e(3.5, 2.2, -1.2, 12.5, 3.2, 0.3).all("#brass"));
        for (double x : new double[]{4.6, 6.6, 9.4, 11.4}) {
            l.add(Models.e(x, 3.2, -0.9, x + 0.8, 3.7, -0.1).all(x < 8 ? "#ice" : "#brass"));
        }
        // coolant pipe: out of the side, up and onto the roof, with brass clamps and a crown
        l.add(Models.e(15.4, 3, 10.5, 17.6, 5.2, 12.7).all("#pipe"));
        l.add(Models.e(16.0, 2.9, 10.4, 18.2, 17.4, 12.8).all("#pipe"));
        l.add(Models.e(9.6, 16, 10.4, 16.0, 17.4, 12.8).all("#pipe"));
        l.add(Models.e(9.5, 16.1, 10.3, 12.0, 19.5, 12.9).all("#pipe"));
        l.add(Models.e(9.3, 19.5, 10.1, 12.2, 20.2, 13.1).all("#brass"));
        // Brass mounting clamps to the wall
        l.add(Models.e(15.8, 7.2, 10.2, 18.3, 8.2, 13.0).all("#brass"));
        l.add(Models.e(15.8, 12.2, 10.2, 18.3, 13.2, 13.0).all("#brass"));
        // A crystal drop of ice under the lower bend
        l.add(Models.e(16.2, 1.8, 11.2, 17.2, 2.9, 12.0).all("#ice"));
        // icicles from the top edge of the front
        for (double[] ic : new double[][]{{1.6, 1.2}, {3.4, 0.8}, {12.4, 1.0}, {14.2, 1.4}}) {
            l.add(Models.e(ic[0], 15 - ic[1], -0.3, ic[0] + 0.45, 15, 0.3).all("#ice"));
        }
        Models.model("schematic_concentrator", false, "resonator_body",
                new String[][]{{"front", "concentrator_front"}, {"side", "concentrator_side"}, {"body", "resonator_body"},
                        {"top", "resonator_top"}, {"brass", "sat_brass"}, {"dark", "sat_dark"}, {"pipe", "resonator_pipe"}, {"ice", "sat_ice"}},
                l.toArray(new Models.El[0]));
        Models.facing4("schematic_concentrator", "schematic_concentrator");
        Models.item("schematic_concentrator", "schematic_concentrator");
        Forge2.addDisplay("schematic_concentrator", 225, 0.625);
    }

    static void catalogModel() throws Exception {
        List<Models.El> l = new ArrayList<>();
        l.add(Models.e(0, 0, 0, 16, 16, 16).face("north", "#front", 0, 0, 16, 16).face("south", "#side", 0, 0, 16, 16)
                .face("west", "#side", 0, 0, 16, 16).face("east", "#side", 0, 0, 16, 16)
                .face("up", "#top", 0, 0, 16, 16).face("down", "#bottom", 0, 0, 16, 16));
        Models.model("punch_card_catalog", false, "resonator_body",
                new String[][]{{"front", "catalog_front"}, {"side", "resonator_body"}, {"top", "resonator_top"}, {"bottom", "sat_dark"}},
                l.toArray(new Models.El[0]));
        Models.facing4("punch_card_catalog", "punch_card_catalog");
        Models.item("punch_card_catalog", "punch_card_catalog");
        Forge2.addDisplay("punch_card_catalog", 225, 0.625);
        // parts of the assembled catalog are not drawn (the owner's renderer draws the whole cabinet with the golems)
        Forge2.write(Gen3.RES.resolve("blockstates/catalog_part.json"),
                "{\n  \"variants\": {\n    \"\": {\"model\": \"minecraft:block/air\"}\n  }\n}\n");
    }
}
