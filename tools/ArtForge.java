import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Генератор текстур 32x32 и 3D-моделей блоков Golem Logix (оккульт-аналоговый стиль:
 * тёмное железо, медь, латунь, бирюзовое свечение, иней).
 * Запуск: java tools/ArtForge.java   (из корня проекта, JDK 21)
 */
public class ArtForge {
    static final Path RES = Path.of("src/main/resources/assets/golem_logix");
    static final int S = 32;

    // ----------------------------------------------------------------- палитра
    static final int IRON = 0x3b3e47, IRON_D = 0x22242a, IRON_L = 0x5d626e, IRON_XL = 0x7b8190;
    static final int COPPER = 0xb87333, COPPER_D = 0x6e3f18, COPPER_L = 0xe39a5c;
    static final int BRASS = 0xc99a2e, BRASS_D = 0x6f5012, BRASS_L = 0xf2d070;
    static final int CYAN = 0x38d9d6, CYAN_D = 0x15605f, CYAN_L = 0xc2fffd;
    static final int ICE = 0x8fc9ea, ICE_D = 0x4a7fa8, FROST = 0xdff3fb, STEEL_BLUE = 0x39506a;
    static final int VIOLET = 0xa070ff, VIOLET_D = 0x4a2a8a, GOLD = 0xf0c850;

    static int rgb(int c) { return 0xFF000000 | c; }

    static int argb(int a, int c) { return (a << 24) | (c & 0xFFFFFF); }

    static int shade(int c, double f) {
        int a = c >>> 24;
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (c & 255) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        int aa = a >>> 24, ba = b >>> 24;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    // ------------------------------------------------------------------ холст
    static class Tex {
        final int[] px = new int[S * S];

        Tex() {
        }

        Tex(int c) {
            Arrays.fill(px, rgb(c));
        }

        void set(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < S && y < S) px[y * S + x] = c < 0 || (c >>> 24) != 0 ? (((c >>> 24) == 0) ? rgb(c) : c) : c;
        }

        /** Светящиеся пиксели (жилы, огоньки): по ним save пишет слой свечения {@code имя_glow.png}. */
        final boolean[] lit = new boolean[S * S];

        void put(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < S && y < S) {
                px[y * S + x] = ((c >>> 24) == 0 && c != 0) ? rgb(c) : c;
                lit[y * S + x] = false;
            }
        }

        int get(int x, int y) {
            return px[y * S + x];
        }

        void fill(int x, int y, int w, int h, int c) {
            for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) put(i, j, c);
        }

        void outline(int x, int y, int w, int h, int c) {
            for (int i = x; i < x + w; i++) {
                put(i, y, c);
                put(i, y + h - 1, c);
            }
            for (int j = y; j < y + h; j++) {
                put(x, j, c);
                put(x + w - 1, j, c);
            }
        }

        /** Скос: свет сверху-слева, тень снизу-справа. */
        void bevel(int x, int y, int w, int h, int light, int dark) {
            for (int i = x; i < x + w; i++) {
                put(i, y, light);
                put(i, y + h - 1, dark);
            }
            for (int j = y; j < y + h; j++) {
                put(x, j, light);
                put(x + w - 1, j, dark);
            }
            put(x + w - 1, y, mix(light, dark, 0.5));
            put(x, y + h - 1, mix(light, dark, 0.5));
        }

        void speckle(int x, int y, int w, int h, double amt, long seed) {
            Random r = new Random(seed);
            for (int j = y; j < y + h; j++)
                for (int i = x; i < x + w; i++) {
                    double f = 1 + (r.nextDouble() - 0.5) * 2 * amt;
                    if (i >= 0 && j >= 0 && i < S && j < S && (px[j * S + i] >>> 24) != 0) px[j * S + i] = shade(px[j * S + i], f);
                }
        }

        void rivet(int cx, int cy, int base, int hi) {
            put(cx, cy, base);
            put(cx + 1, cy, shade(base, 0.8));
            put(cx, cy + 1, shade(base, 0.8));
            put(cx + 1, cy + 1, shade(base, 0.55));
            put(cx, cy, hi);
        }

        void line(int x0, int y0, int x1, int y1, int c) {
            int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
            while (true) {
                put(x0, y0, c);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 > -dy) {
                    err -= dy;
                    x0 += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        }

        void disc(double cx, double cy, double r, int c) {
            for (int j = 0; j < S; j++)
                for (int i = 0; i < S; i++)
                    if (Math.hypot(i + 0.5 - cx, j + 0.5 - cy) <= r) put(i, j, c);
        }

        void ring(double cx, double cy, double r0, double r1, int c) {
            for (int j = 0; j < S; j++)
                for (int i = 0; i < S; i++) {
                    double d = Math.hypot(i + 0.5 - cx, j + 0.5 - cy);
                    if (d >= r0 && d <= r1) put(i, j, c);
                }
        }

        /** Светящийся пиксель с мягким ореолом. */
        void glow(int x, int y, int c) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], ny = y + d[1];
                if (nx >= 0 && ny >= 0 && nx < S && ny < S) px[ny * S + nx] = mix(px[ny * S + nx], rgb(c), 0.35);
            }
            put(x, y, c);
            if (x >= 0 && y >= 0 && x < S && y < S) {
                lit[y * S + x] = true;
            }
        }

        /**
         * Светящаяся руна w x h пикселей (глифы {@link Paint#RUNES}, сетка 4x6) с левым верхним углом (x, y): тёмная гравировка
         * по краю и светлое ядро; ядро попадает в слой свечения.
         */
        void rune(int x, int y, int w, int h, int glyph, int core, int edge) {
            double[][] segs = Paint.RUNES[Math.floorMod(glyph * 7 + x * 31 + y * 17, Paint.RUNES.length)];
            List<int[]> pts = new ArrayList<>();
            for (double[] g : segs) {
                int x0 = x + (int) Math.round(g[0] * (w - 1) / 4.0), y0 = y + (int) Math.round(g[1] * (h - 1) / 6.0);
                int x1 = x + (int) Math.round(g[2] * (w - 1) / 4.0), y1 = y + (int) Math.round(g[3] * (h - 1) / 6.0);
                int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
                while (true) {
                    pts.add(new int[]{x0, y0});
                    if (x0 == x1 && y0 == y1) break;
                    int e2 = 2 * err;
                    if (e2 > -dy) {
                        err -= dy;
                        x0 += sx;
                    }
                    if (e2 < dx) {
                        err += dx;
                        y0 += sy;
                    }
                }
            }
            if (edge != 0) {
                for (int[] p : pts) {
                    for (int[] d : new int[][]{{1, 0}, {0, 1}}) {
                        int nx = p[0] + d[0], ny = p[1] + d[1];
                        if (nx >= 0 && ny >= 0 && nx < S && ny < S && !lit[ny * S + nx]) {
                            px[ny * S + nx] = mix(px[ny * S + nx], rgb(edge), 0.6);
                        }
                    }
                }
            }
            for (int[] p : pts) {
                put(p[0], p[1], core);
                if (p[0] >= 0 && p[1] >= 0 && p[0] < S && p[1] < S) {
                    lit[p[1] * S + p[0]] = true;
                }
            }
        }

        /**
         * Глубокая выгравированная руна (оккультно-древний стиль по концепт-арту):
         * тёмная выемка в броне, латунные фаски травления и яркое лазурное ядро с горящими узлами.
         */
        void engravedRune(int x, int y, int w, int h, int glyph) {
            double[][] segs = Paint.RUNES[Math.floorMod(glyph * 7 + x * 31 + y * 17, Paint.RUNES.length)];
            List<int[]> pts = new ArrayList<>();
            for (double[] g : segs) {
                int x0 = x + (int) Math.round(g[0] * (w - 1) / 4.0), y0 = y + (int) Math.round(g[1] * (h - 1) / 6.0);
                int x1 = x + (int) Math.round(g[2] * (w - 1) / 4.0), y1 = y + (int) Math.round(g[3] * (h - 1) / 6.0);
                int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx - dy;
                while (true) {
                    pts.add(new int[]{x0, y0});
                    if (x0 == x1 && y0 == y1) break;
                    int e2 = 2 * err;
                    if (e2 > -dy) {
                        err -= dy;
                        x0 += sx;
                    }
                    if (e2 < dx) {
                        err += dx;
                        y0 += sy;
                    }
                }
            }
            // 1. Тёмная выемка-желобок вокруг руны
            for (int[] p : pts) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = p[0] + dx, ny = p[1] + dy;
                        if (nx >= 0 && ny >= 0 && nx < S && ny < S && !lit[ny * S + nx]) {
                            px[ny * S + nx] = mix(px[ny * S + nx], 0xFF12141A, 0.75);
                        }
                    }
                }
            }
            // 2. Бронзово-латунный отблеск травления по кромкам
            for (int[] p : pts) {
                int nx = p[0] - 1, ny = p[1] - 1;
                if (nx >= 0 && ny >= 0 && nx < S && ny < S && !lit[ny * S + nx]) {
                    px[ny * S + nx] = mix(px[ny * S + nx], 0xFF6A5530, 0.45);
                }
            }
            // 3. Лазурное сияющее ядро
            for (int[] p : pts) {
                put(p[0], p[1], CYAN);
                if (p[0] >= 0 && p[1] >= 0 && p[0] < S && p[1] < S) {
                    lit[p[1] * S + p[0]] = true;
                }
            }
            // 4. Бело-голубые горящие узлы в конечных и переломных точках
            for (double[] g : segs) {
                int x0 = x + (int) Math.round(g[0] * (w - 1) / 4.0), y0 = y + (int) Math.round(g[1] * (h - 1) / 6.0);
                int x1 = x + (int) Math.round(g[2] * (w - 1) / 4.0), y1 = y + (int) Math.round(g[3] * (h - 1) / 6.0);
                glow(x0, y0, 0xFFE0FFFF);
                glow(x1, y1, 0xFFE0FFFF);
            }
        }

        void copy(Tex src, int sx, int sy, int w, int h, int dx, int dy) {
            for (int j = 0; j < h; j++)
                for (int i = 0; i < w; i++) set(dx + i, dy + j, src.get(sx + i, sy + j));
        }

        void save(String name) throws Exception {
            BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
            for (int j = 0; j < S; j++) for (int i = 0; i < S; i++) img.setRGB(i, j, px[j * S + i]);
            File f = RES.resolve("textures/block/" + name + ".png").toFile();
            f.getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            // слой свечения рун и жил (его рисует поверх модели клиент, см. GlowModels)
            BufferedImage g = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
            boolean any = false;
            for (int i = 0; i < S * S; i++) {
                if (lit[i]) {
                    g.setRGB(i % S, i / S, px[i]);
                    any = true;
                }
            }
            File gf = RES.resolve("textures/block/" + name + "_glow.png").toFile();
            if (any) {
                ImageIO.write(g, "png", gf);
            } else {
                Files.deleteIfExists(gf.toPath());
            }
        }
    }

    // ------------------------------------------------------------ материалы
    /** Панель из листового металла: скос, швы, заклёпки. */
    static Tex plate(int base, int hi, int lo, long seed, boolean seams, int rivetColor) {
        Tex t = new Tex(base);
        t.speckle(0, 0, S, S, 0.07, seed);
        if (seams) {
            t.fill(15, 1, 2, 30, lo);
            t.fill(1, 15, 30, 2, lo);
            t.fill(15, 1, 1, 30, hi);
            t.fill(1, 15, 30, 1, hi);
        }
        t.bevel(0, 0, S, S, lo, lo);
        t.bevel(1, 1, S - 2, S - 2, hi, shade(lo, 1.2));
        if (rivetColor != 0) {
            int[] p = {3, 26};
            for (int x : p) for (int y : p) t.rivet(x, y, rivetColor, shade(rivetColor, 1.4));
        }
        return t;
    }

    static Tex ironPlate(long seed, boolean seams) {
        return plate(IRON, IRON_L, IRON_D, seed, seams, BRASS);
    }

    // ============================================================ ТЕКСТУРЫ
    static void atlasCasing() throws Exception {
        Tex t = ironPlate(11, true);
        // латунные уголки у шва и тонкая бирюзовая жила
        for (int[] c : new int[][]{{13, 13}, {18, 13}, {13, 18}, {18, 18}}) t.rivet(c[0], c[1], BRASS, BRASS_L);
        // фиолетового знака на самой обшивке нет: у собранной решётки он один на грань и бродит по ней (AtlasCoreRenderer)
        t.save("atlas_casing");
        Paint glyph = new Paint(4, 4, 16);
        glyph.glowRect(1.2, 0.9, 2.8, 3.1, 1.0, Paint.at(Paint.VIOLET, 3.6), 110);
        glyph.rune(1.2, 0.7, 2.6, 11, Paint.at(Paint.VIOLET, 5.8), Paint.at(Paint.VIOLET, 3.4));
        glyph.save(RES.resolve("textures/entity/atlas_glyph.png").toFile());
    }

    static void atlasCasingTop() throws Exception {
        Tex t = ironPlate(12, false);
        // латунная инкрустация рамкой и рунный крест в центре
        t.fill(4, 4, 24, 24, BRASS_D);
        t.fill(5, 5, 22, 22, IRON);
        t.speckle(5, 5, 22, 22, 0.06, 13);
        t.outline(5, 5, 22, 22, BRASS);
        t.outline(6, 6, 20, 20, IRON_D);
        for (int[] c : new int[][]{{5, 5}, {25, 5}, {5, 25}, {25, 25}}) t.rivet(c[0], c[1], BRASS_L, 0xFFFFFFFF);
        t.fill(15, 9, 2, 14, CYAN_D);
        t.fill(9, 15, 14, 2, CYAN_D);
        t.glow(16, 16, CYAN);
        for (int k : new int[]{10, 22}) {
            t.glow(16, k, CYAN_D);
            t.glow(k, 16, CYAN_D);
        }
        t.save("atlas_casing_top");
    }

    static void atlasConduit() throws Exception {
        // тоннель из меди и бирюзовых импульсов по железной раме
        Tex t = ironPlate(21, false);
        t.fill(0, 11, S, 10, IRON_D);
        t.fill(0, 12, S, 8, shade(COPPER, 0.5));
        for (int x = 0; x < S; x++) {
            int c = (x % 4 < 2) ? COPPER : COPPER_D;
            t.put(x, 13, c);
            t.put(x, 14, shade(c, 1.15));
            t.put(x, 17, shade(c, 0.9));
            t.put(x, 18, c);
        }
        t.fill(0, 15, S, 2, CYAN_D);
        t.bevel(0, 0, S, S, IRON_L, IRON_D);
        // анимация: синий огонёк бежит по жиле слева направо и оставляет за собой залитую строку; заполнив её, жила гаснет
        // и всё начинается снова (кадры друг под другом, слой свечения — с теми же кадрами)
        int frames = 40;
        BufferedImage img = new BufferedImage(S, S * frames, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glowImg = new BufferedImage(S, S * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) {
            int fill = Math.min(S, f * S / 32);
            boolean fade = f >= 34;
            for (int j = 0; j < S; j++) {
                for (int i = 0; i < S; i++) {
                    int c = t.px[j * S + i];
                    boolean lit = false;
                    if ((j == 15 || j == 16) && i < fill) {
                        int head = fill - 1 - i;
                        c = head < 2 && !fade ? rgb(CYAN_L) : rgb(fade ? mix(rgb(CYAN), rgb(CYAN_D), (f - 33) / 6.0) : CYAN);
                        lit = true;
                    }
                    img.setRGB(i, f * S + j, c);
                    glowImg.setRGB(i, f * S + j, lit ? c : 0);
                }
            }
        }
        File f = RES.resolve("textures/block/atlas_conduit.png").toFile();
        ImageIO.write(img, "png", f);
        ImageIO.write(glowImg, "png", RES.resolve("textures/block/atlas_conduit_glow.png").toFile());
        String meta = "{\n  \"animation\": {\n    \"frametime\": 2,\n    \"interpolate\": false\n  }\n}\n";
        Files.writeString(RES.resolve("textures/block/atlas_conduit.png.mcmeta"), meta);
        Files.writeString(RES.resolve("textures/block/atlas_conduit_glow.png.mcmeta"), meta);
    }

    static void expander(String name, int glow, int glowDark, int glowLight, int frame, int frameDark) throws Exception {
        Tex t = ironPlate(31, false);
        // окно с решёткой и кристаллами
        t.fill(6, 6, 20, 20, frameDark);
        t.fill(7, 7, 18, 18, frame);
        t.fill(8, 8, 16, 16, 0xFF0B1620);
        Random r = new Random(5);
        for (int i = 0; i < 6; i++) {
            int cx = 10 + r.nextInt(12), cy = 10 + r.nextInt(12);
            t.put(cx, cy, glow);
            t.put(cx, cy - 1, glowLight);
            t.put(cx - 1, cy, glowDark);
            t.put(cx + 1, cy, glowDark);
            t.put(cx, cy + 1, glowDark);
        }
        for (int k = 8; k < 24; k += 4) {
            t.fill(k, 8, 1, 16, shade(frame, 0.8));
            t.fill(8, k, 16, 1, shade(frame, 0.8));
        }
        t.glow(16, 16, glowLight);
        t.fill(15, 15, 2, 2, glowLight);
        for (int[] c : new int[][]{{3, 3}, {27, 3}, {3, 27}, {27, 27}}) t.rivet(c[0], c[1], frame, frame);
        t.save(name);
    }

    static void bushing() throws Exception {
        Tex t = ironPlate(41, false);
        t.disc(16, 16, 12.5, BRASS_D);
        t.disc(16, 16, 11.5, BRASS);
        t.ring(16, 16, 10, 11.5, BRASS_L);
        t.disc(16, 16, 9, IRON_D);
        t.ring(16, 16, 7.5, 9, shade(BRASS, 0.7));
        t.disc(16, 16, 6.5, 0xFF0D1417);
        t.disc(16, 16, 3.5, CYAN_D);
        t.disc(16, 16, 2.2, CYAN);
        t.put(15, 15, CYAN_L);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            t.rivet((int) Math.round(16 + 10.2 * Math.cos(ang)) - 1, (int) Math.round(16 + 10.2 * Math.sin(ang)) - 1, BRASS_D, BRASS_L);
        }
        t.save("cable_bushing");
    }

    static void atlant() throws Exception {
        // Базовый металл: тяжелый тёмный оружейный чугун с благородным травлением
        Tex body = new Tex(IRON);
        body.speckle(0, 0, S, S, 0.05, 51);
        body.bevel(0, 0, S, S, IRON_D, IRON_D);
        body.bevel(1, 1, S - 2, S - 2, IRON_L, shade(IRON_D, 1.2));

        // Мощный рельеф нагрудника: центральный броневой щит с глубокими фасками
        body.fill(4, 4, 24, 24, IRON_D);
        body.bevel(4, 4, 24, 24, IRON_L, 0xFF14161C);
        body.fill(6, 6, 20, 20, 0xFF2A2D35);
        body.bevel(6, 6, 20, 20, IRON_L, IRON_D);

        // Стальные заклепки по периметру нагрудника
        for (int x : new int[]{5, 26}) {
            for (int y = 6; y <= 26; y += 5) body.rivet(x, y, IRON_D, IRON_L);
        }

        // Единое центрированное Оккультное Резонансное Ядро Атланта (симметричный реактор)
        // Внешнее бронзовое кольцо/оправа ядра
        body.disc(16, 16, 5.5, 0xFF5A4A2C);
        body.disc(16, 16, 4.8, 0xFF1E2128);
        body.bevel(12, 12, 9, 9, 0xFF786438, 0xFF101216);
        // Внутреннее тёмное крио-ложе и сияющий кристаллический фокус
        body.disc(16, 16, 3.2, 0xFF08141C);
        body.disc(16, 16, 2.0, CYAN_D);
        body.put(15, 15, CYAN_L);
        body.put(16, 15, CYAN_L);
        body.put(15, 16, CYAN);
        body.put(16, 16, CYAN);

        // Строгие контурные энергоканалы, идущие от ядра по швам брони
        for (int y = 7; y <= 24; y++) {
            if (y < 12 || y > 19) {
                body.put(15, y, CYAN_D);
                body.put(16, y, CYAN);
            }
        }
        for (int x = 8; x <= 23; x++) {
            if (x < 12 || x > 19) {
                body.put(x, 15, CYAN_D);
                body.put(x, 16, CYAN);
            }
        }
        // Бронзовые хомуты-муфты на концах энергоканалов
        body.fill(7, 14, 2, 4, 0xFF5A4A2C);
        body.fill(23, 14, 2, 4, 0xFF5A4A2C);

        // Сдержанный крио-иней по верхней кромке и в выемках брони
        for (int x = 2; x < 30; x++) {
            if ((x * 13 + 7) % 6 == 0) body.put(x, 2, 0xFF96B8C8);
            if ((x * 17 + 3) % 7 == 0) body.put(x, 3, 0xFF6D8C9C);
        }
        body.save("atlant_body");

        // Чистая формованная оружейная бронепластина для анатомического рельефа (пресс, пекторали, бедра)
        Tex plate = new Tex(0x282B32);
        plate.speckle(0, 0, S, S, 0.04, 55);
        plate.bevel(0, 0, S, S, IRON_D, 0xFF14161C);
        plate.bevel(1, 1, S - 2, S - 2, IRON_L, IRON_D);
        plate.bevel(3, 3, S - 6, S - 6, 0xFF353942, 0xFF181A20);
        plate.rivet(4, 4, IRON_D, IRON_L);
        plate.rivet(27, 4, IRON_D, IRON_L);
        plate.rivet(4, 27, IRON_D, IRON_L);
        plate.rivet(27, 27, IRON_D, IRON_L);
        plate.save("atlant_plate");

        // Конечности: мощные сегментированные чугунные пластины с глубокими пазами (без хаотичных рун)
        Tex limb = new Tex(0x282B32);
        limb.speckle(0, 0, S, S, 0.05, 52);
        limb.bevel(0, 0, S, S, IRON_D, IRON_D);
        limb.bevel(1, 1, S - 2, S - 2, IRON_L, shade(IRON_D, 1.2));
        // Горизонтальные рельефные сегменты брони
        for (int y = 6; y < S; y += 8) {
            limb.fill(2, y, 28, 2, 0xFF14161C);
            limb.fill(2, y + 2, 28, 1, IRON_L);
            limb.rivet(4, y - 2, IRON_D, IRON_L);
            limb.rivet(26, y - 2, IRON_D, IRON_L);
        }
        // Темная медная трубка вдоль конечности
        for (int y = 3; y < 29; y++) {
            limb.put(15, y, 0xFF3D2614);
            limb.put(16, y, 0xFF6D4526);
            limb.put(17, y, 0xFF4D301A);
        }
        limb.save("atlant_limb");

        // Пояс и портупея: темная вороненая сталь с состаренной бронзой
        Tex belt = plate(0x4A3E2A, 0x6E5E42, 0x2A2316, 53, false, 0);
        for (int x = 3; x < S; x += 8) belt.rivet(x, 14, 0x2A2316, 0x8A7654);
        belt.fill(0, 6, S, 2, 0xFF14161C);
        belt.fill(0, 24, S, 2, 0xFF14161C);
        // Массивная центральная пряжка-кожух
        belt.fill(10, 5, 12, 22, 0xFF22252C);
        belt.bevel(10, 5, 12, 22, 0xFF786438, 0xFF121418);
        // Криогенный энергетический элемент
        belt.fill(13, 10, 6, 12, 0xFF0A1820);
        belt.bevel(13, 10, 6, 12, CYAN_D, 0xFF040E14);
        belt.fill(14, 12, 4, 8, CYAN);
        belt.glow(15, 15, CYAN_L);
        belt.glow(16, 16, CYAN_L);
        belt.save("atlant_belt");

        // Голова: монументальный водолазный шлем-автоматон
        Tex head = new Tex(IRON);
        head.speckle(0, 0, S, S, 0.05, 54);
        head.bevel(0, 0, S, S, IRON_D, IRON_D);
        head.bevel(1, 1, S - 2, S - 2, IRON_L, shade(IRON_D, 1.2));
        // Массивная броневая бровь
        head.fill(2, 6, 28, 4, 0xFF161820);
        head.bevel(2, 6, 28, 4, IRON_L, 0xFF101216);
        head.rivet(4, 7, 0xFF4A3E2A, 0xFF786438);
        head.rivet(26, 7, 0xFF4A3E2A, 0xFF786438);

        // Горизонтальный моно-визор: глубокая тёмная глазница с сияющим лазурным окуляром
        head.fill(7, 11, 18, 6, 0xFF0E1014);
        head.bevel(7, 11, 18, 6, 0xFF524428, 0xFF080A0E); // темная бронзовая оправа
        head.fill(9, 12, 14, 4, 0xFF091720);
        head.fill(10, 13, 12, 2, CYAN);
        for (int x = 11; x <= 18; x++) {
            head.glow(x, 13, CYAN_L);
            head.glow(x, 14, CYAN);
        }

        // Вентиляционная решетка дыхала
        head.fill(10, 19, 12, 9, 0xFF14161C);
        head.bevel(10, 19, 12, 9, IRON_L, 0xFF0A0C0E);
        for (int y = 21; y <= 26; y += 2) {
            head.fill(12, y, 8, 1, 0xFF080A0C);
            head.fill(12, y + 1, 8, 1, 0xFF353A45);
        }
        head.disc(16, 28, 2, 0xFF5E4E2C);
        head.put(16, 28, 0xFF2A2316);
        head.save("atlant_head");
    }

    static void coreParts() throws Exception {
        // кристалл ядра: огранка, свечение от центра
        Tex c = new Tex(CYAN_D);
        for (int j = 0; j < S; j++)
            for (int i = 0; i < S; i++) {
                double d = Math.hypot(i - 15.5, j - 15.5) / 22.0;
                c.put(i, j, mix(rgb(CYAN_L), rgb(CYAN_D), Math.min(1, d * 1.3)));
            }
        c.line(0, 0, 31, 31, shade(CYAN, 1.0));
        c.line(31, 0, 0, 31, shade(CYAN, 1.0));
        c.line(16, 0, 16, 31, argb(255, CYAN_L));
        c.line(0, 16, 31, 16, argb(255, CYAN_L));
        c.bevel(0, 0, S, S, CYAN_L, CYAN_D);
        c.save("core_crystal");
    }

    static void terminalTrim() throws Exception {
        // светящийся индикаторный экран для верхних граней терминалов не нужен; оставляем существующие
    }

    static void brassPlate() throws Exception {
        Tex t = plate(BRASS, BRASS_L, BRASS_D, 71, false, COPPER_D);
        t.fill(0, 15, S, 2, BRASS_D);
        t.fill(0, 17, S, 1, BRASS_L);
        for (int i = 0; i < 3; i++) {
            t.rune(8 + i * 7, 6, 4, 6, i * 6 + 5, mix(rgb(VIOLET), rgb(BRASS_D), 0.2), BRASS_D);
        }
        t.save("brass_plate");
    }

    static void cryo() throws Exception {
        int frames = 24;
        BufferedImage out = new BufferedImage(S, S * frames, BufferedImage.TYPE_INT_ARGB);

        // Базовая пластина: глубокий крио-металл, скос, иней
        Tex base = plate(STEEL_BLUE, 0xFF6c93b8, 0xFF1b2a3b, 81, false, 0xFFBFD8E8);
        base.fill(5, 5, 22, 22, 0xFF0F1A27);
        base.bevel(5, 5, 22, 22, 0xFF1b2a3b, 0xFF6c93b8);
        Random r = new Random(82);
        for (int k = 0; k < 40; k++) {
            int x = r.nextInt(S), y = r.nextInt(S);
            if ((x < 5 || x > 26) && (y < 5 || y > 26)) base.put(x, y, mix(base.get(x, y), rgb(FROST), 0.7));
        }

        // Битовые потоки 6 горизонтальных строк (период 24 для бесшовной циклической анимации)
        int[][] stream = {
            {1, 1, 0, 1, 1, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1},
            {0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 1},
            {1, 0, 0, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0},
            {1, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 0, 1, 1},
            {0, 1, 0, 1, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 0, 1},
            {1, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 0, 1, 0}
        };

        for (int f = 0; f < frames; f++) {
            Tex frame = new Tex();
            System.arraycopy(base.px, 0, frame.px, 0, S * S);

            // Плавная синусоидальная пульсация боковых крио-рун
            for (int i = 0; i < 3; i++) {
                double wavePhase = ((f + i * 8.0) / frames) * 2 * Math.PI;
                double pulse = 0.5 + 0.5 * Math.sin(wavePhase);
                int runeColor = mix(CYAN_D, CYAN_L, pulse);
                frame.rune(1, 8 + i * 6, 3, 5, i * 4 + 1, runeColor, 0xFF1b2a3b);
                frame.rune(28, 8 + i * 6, 3, 5, i * 4 + 3, runeColor, 0xFF1b2a3b);
            }

            // 6 плавно бегущих строк матрицы (четные вправо, нечетные влево) с мягкими затухающими шлейфами
            for (int i = 0; i < 6; i++) {
                int y = 8 + i * 3;
                int dir = (i % 2 == 0) ? 1 : -1;
                int shift = dir * f;
                for (int x = 7; x <= 24; x++) {
                    int idx = Math.floorMod((x - 7) - shift, 24);
                    int bit = stream[i][idx];
                    int pDist = Math.floorMod((x - 7) - shift * 2, 24);
                    int col;
                    if (pDist < 2) {
                        col = argb(255, 0xE2FFFE); // яркая голова пакета
                    } else if (pDist < 4) {
                        col = mix(rgb(CYAN), argb(255, 0xE2FFFE), 0.5); // мягкий ореол
                    } else if (bit == 1) {
                        col = mix(rgb(CYAN_D), rgb(CYAN), 0.7);
                    } else {
                        col = 0xFF0A1624;
                    }
                    frame.put(x, y, col);
                }
            }

            for (int y = 0; y < S; y++) {
                for (int x = 0; x < S; x++) {
                    out.setRGB(x, f * S + y, frame.px[y * S + x]);
                }
            }
        }

        File outFile = RES.resolve("textures/block/cryo_panel.png").toFile();
        outFile.getParentFile().mkdirs();
        ImageIO.write(out, "png", outFile);
        Forge2.write(RES.resolve("textures/block/cryo_panel.png.mcmeta"),
                "{\n  \"animation\": {\n    \"frametime\": 4\n  }\n}\n");
        Files.deleteIfExists(RES.resolve("textures/block/cryo_panel_glow.png"));

        Tex fin = new Tex(0xFF2b4560);
        fin.speckle(0, 0, S, S, 0.08, 83);
        for (int y = 2; y < S; y += 4) {
            fin.fill(0, y, S, 2, 0xFF6c93b8);
            fin.fill(0, y, S, 1, 0xFFB5D6EA);
        }
        fin.fill(14, 0, 4, S, CYAN_D);
        fin.save("cryo_fin");

        Tex ice = new Tex();
        for (int j = 0; j < S; j++)
            for (int i = 0; i < S; i++) {
                double d = Math.hypot(i - 15.5, j - 15.5) / 22;
                ice.put(i, j, mix(rgb(FROST), rgb(ICE_D), Math.min(1, d + ((i * 3 + j * 5) % 7) * 0.03)));
            }
        ice.line(0, 12, 20, 0, argb(255, 0xFFFFFF));
        ice.line(8, 31, 31, 10, argb(255, 0xFFFFFF));
        ice.bevel(0, 0, S, S, 0xFFFFFFFF, ICE_D);
        ice.save("ice_crystal");
    }

    static void resonator() throws Exception {
        Tex r = plate(BRASS, BRASS_L, BRASS_D, 91, false, COPPER_D);
        for (int x = 4; x < S; x += 6) r.fill(x, 6, 1, 20, BRASS_D);
        // руны мороза между рёбрами
        for (int i = 0; i < 4; i++) {
            r.rune(6 + i * 6, 13, 3, 6, i * 3 + 4, rgb(0xB6E0F2), BRASS_D);
        }
        r.save("resonator_brass");
        Tex ring = new Tex(BRASS);
        ring.speckle(0, 0, S, S, 0.08, 92);
        ring.fill(0, 13, S, 6, BRASS_D);
        ring.fill(0, 14, S, 4, BRASS_L);
        ring.fill(0, 15, S, 2, BRASS);
        for (int x = 2; x < S; x += 8) ring.glow(x, 16, CYAN);
        ring.save("resonator_ring");
    }

    static void jar() throws Exception {
        Tex g = new Tex();
        for (int i = 0; i < S * S; i++) g.px[i] = argb(70, 0xa8e4ff);
        g.line(5, 26, 5, 6, argb(190, 0xFFFFFF));
        g.line(7, 22, 7, 10, argb(110, 0xFFFFFF));
        g.outline(0, 0, S, S, argb(150, 0xcfeaff));
        g.save("jar_glass");
        Tex l = new Tex(CYAN);
        Random r = new Random(111);
        for (int j = 0; j < S; j++)
            for (int i = 0; i < S; i++) l.put(i, j, mix(rgb(CYAN_L), rgb(CYAN), (j / 31.0) * 0.9 + (r.nextDouble() * 0.1)));
        for (int k = 0; k < 14; k++) {
            int x = r.nextInt(S - 4) + 2, y = r.nextInt(S - 4) + 2;
            l.put(x, y, 0xFFFFFFFF);
            l.put(x + 1, y, rgb(CYAN_L));
        }
        l.save("jar_liquid");
        Tex c = new Tex(0xB07A44);
        c.speckle(0, 0, S, S, 0.2, 112);
        for (int k = 0; k < 18; k++) c.put((k * 7) % S, (k * 11) % S, 0xFF6e4a28);
        c.bevel(0, 0, S, S, 0xFFD9A06A, 0xFF6e4a28);
        c.save("cork");
    }

    // ===================================================== графика книги
    static void saveGui(BufferedImage img, String name) throws Exception {
        File f = RES.resolve("textures/gui/book/" + name + ".png").toFile();
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    static void bookArt() throws Exception {
        Random r = new Random(7);
        // --- пергамент 256x256: волокна, пятна, затемнённые края
        BufferedImage p = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                double edge = Math.min(Math.min(x, 255 - x), Math.min(y, 255 - y)) / 40.0;
                double dark = 1 - Math.min(1, edge);
                int base = mix(rgb(0xE6D5AE), rgb(0xBFA173), dark * dark * 0.9);
                p.setRGB(x, y, shade(base, 1 + (r.nextDouble() - 0.5) * 0.06));
            }
        for (int i = 0; i < 700; i++) {
            int x = r.nextInt(256), y = r.nextInt(256), len = 2 + r.nextInt(6);
            boolean horiz = r.nextBoolean();
            for (int k = 0; k < len; k++) {
                int xx = horiz ? x + k : x, yy = horiz ? y : y + k;
                if (xx < 256 && yy < 256) p.setRGB(xx, yy, shade(p.getRGB(xx, yy), 0.95));
            }
        }
        for (int i = 0; i < 9; i++) { // кофейные пятна
            double cx = r.nextInt(256), cy = r.nextInt(256), rad = 6 + r.nextInt(14);
            for (int y = 0; y < 256; y++)
                for (int x = 0; x < 256; x++) {
                    double d = Math.hypot(x - cx, y - cy);
                    if (d < rad) p.setRGB(x, y, mix(p.getRGB(x, y), rgb(0xA78A5A), 0.10 * (1 - d / rad)));
                }
        }
        saveGui(p, "parchment");

        // --- фон карты: тёмно-синяя бездна, сетка, звёзды
        BufferedImage m = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) m.setRGB(x, y, rgb(mix(rgb(0x0B1620), rgb(0x10202D), ((x * 7 + y * 3) % 17) / 17.0)));
        for (int i = 0; i < 256; i += 32)
            for (int k = 0; k < 256; k++) {
                m.setRGB(i, k, mix(m.getRGB(i, k), rgb(0x1E4A5C), 0.5));
                m.setRGB(k, i, mix(m.getRGB(k, i), rgb(0x1E4A5C), 0.5));
            }
        for (int i = 0; i < 70; i++) {
            int x = r.nextInt(256), y = r.nextInt(256);
            m.setRGB(x, y, rgb(r.nextBoolean() ? CYAN : 0xCFE9F5));
            if (r.nextInt(5) == 0) {
                m.setRGB((x + 1) & 255, y, mix(m.getRGB((x + 1) & 255, y), rgb(CYAN), 0.4));
                m.setRGB(x, (y + 1) & 255, mix(m.getRGB(x, (y + 1) & 255), rgb(CYAN), 0.4));
            }
        }
        saveGui(m, "map_bg");

        // --- спрайты узлов 26x26: 0 обычный, 1 наведённый, 2 золотой (главная вехa), 3 непрочитанный отсвет
        BufferedImage n = new BufferedImage(26 * 4, 26, BufferedImage.TYPE_INT_ARGB);
        int[][] ringCols = {{BRASS, BRASS_D, BRASS_L}, {CYAN, CYAN_D, CYAN_L}, {GOLD, 0x7a5f12, 0xFFF3B0}, {CYAN_L, CYAN, 0xFFFFFFFF}};
        for (int s = 0; s < 4; s++) {
            for (int y = 0; y < 26; y++)
                for (int x = 0; x < 26; x++) {
                    double d = Math.hypot(x - 12.5, y - 12.5);
                    int c = 0;
                    if (d <= 12.5) c = rgb(ringCols[s][1]);
                    if (d <= 12) c = rgb(ringCols[s][0]);
                    if (d <= 10.2) c = rgb(0x121A22);
                    if (d <= 9.8 && d > 8.6) c = rgb(ringCols[s][1]);
                    if (d <= 8.6) c = rgb(mix(rgb(0x1B2A36), rgb(0x0B1219), d / 9));
                    if (s == 3 && d <= 12.5 && d > 12) c = rgb(0xFFFFFF);
                    n.setRGB(s * 26 + x, y, c);
                }
            for (double a = 0; a < Math.PI * 2; a += Math.PI / 4)
                n.setRGB(s * 26 + (int) Math.round(12.5 + 11.2 * Math.cos(a)), (int) Math.round(12.5 + 11.2 * Math.sin(a)), rgb(ringCols[s][2]));
        }
        saveGui(n, "nodes");
    }

    // ===================================================== анимация экранов терминалов
    static void blit(BufferedImage img, String[] rows, int ox, int oy) {
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++) {
                int v;
                switch (rows[y].charAt(x)) {
                    case '#' -> v = 255;
                    case '+' -> v = 170;
                    case 'o' -> v = 95;
                    case 'c' -> v = 215;
                    case 'k' -> v = 140;
                    default -> v = -1;
                }
                if (v >= 0) img.setRGB(ox + x, oy + y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
    }

    /**
     * Лист 64x64 для TerminalRenderer: фоны экранов (0,0 и 16,0, по 14x14), голем 6x7 с ящиком (y=16) и без (y=24)
     * по 4 кадра шагом 8, ящик 2x2 (32,16), белый пиксель (62,62), приветствие 14x14 по 4 кадра (y=32, шаг 16).
     * Спрайты белые/серые: цвет (зелёный люминофор или янтарь) красит рендерер.
     */
    static void terminalAnim() throws Exception {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        // фон ЭЛТ: тёмный люминофор в круге, строчная развёртка и кольцо радара
        for (int y = 0; y < 14; y++)
            for (int x = 0; x < 14; x++) {
                double d = Math.hypot(x + 0.5 - 7, y + 0.5 - 7);
                if (d > 7.0) continue;
                int c = mix(rgb(0x072412), rgb(0x020b06), d / 7.0);
                if (y % 2 == 0) c = shade(c, 1.25);
                if (Math.abs(d - 4.6) < 0.5) c = mix(c, rgb(0x0f4a22), 0.7);
                img.setRGB(x, y, c);
            }
        // фон крафтового терминала: тёмный янтарь с редкой сеткой
        for (int y = 0; y < 14; y++)
            for (int x = 0; x < 14; x++) {
                int c = rgb(0x1d1004);
                if (x % 3 == 1 && y % 3 == 1) c = rgb(0x32200a);
                if (x == 0 || y == 0 || x == 13 || y == 13) c = rgb(0x0e0801);
                img.setRGB(16 + x, y, c);
            }

        String[] head = {".###..", ".##o..", ".###.."};
        String[][] legs = {{".#..#.", "##..##"}, {"..##..", "..##.."}, {".#.#..", "##.##."}, {"..##..", "..##.."}};
        for (int f = 0; f < 4; f++) {
            blit(img, new String[]{head[0], head[1], head[2], "+##cc.", "###ck.", legs[f][0], legs[f][1]}, f * 8, 16);
            blit(img, new String[]{head[0], head[1], head[2], "+###..", "###...", legs[f][0], legs[f][1]}, f * 8, 24);
        }
        blit(img, new String[]{"cc", "ck"}, 32, 16);
        img.setRGB(62, 62, 0xFFFFFFFF);

        // приветствие: голем анфас, правая рука машет; всё внутри круга радиуса 7, чтобы не вылезать за ЭЛТ
        int[][] hands = {{10, 2}, {11, 3}, {11, 4}, {11, 3}};
        for (int f = 0; f < 4; f++) {
            int ox = f * 16, oy = 32;
            int[][] wave = new int[14][14];
            java.util.function.BiConsumer<int[], Integer> dot = (p, v) -> {
                if (p[0] >= 0 && p[1] >= 0 && p[0] < 14 && p[1] < 14) wave[p[1]][p[0]] = v;
            };
            for (int y = 1; y <= 4; y++) for (int x = 4; x <= 9; x++) dot.accept(new int[]{x, y}, 255);   // голова
            dot.accept(new int[]{5, 2}, 95);
            dot.accept(new int[]{8, 2}, 95);
            for (int x = 5; x <= 8; x++) dot.accept(new int[]{x, 4}, 170);                                  // улыбка-решётка
            dot.accept(new int[]{3, 1}, 170);                                                              // «ушки»
            dot.accept(new int[]{10, 1}, 170);
            dot.accept(new int[]{6, 5}, 140);
            dot.accept(new int[]{7, 5}, 140);                                                              // шея
            for (int y = 6; y <= 11; y++) for (int x = 4; x <= 9; x++) dot.accept(new int[]{x, y}, 255);   // корпус
            for (int y = 8; y <= 9; y++) for (int x = 6; x <= 7; x++) dot.accept(new int[]{x, y}, 95);     // нагрудная панель
            for (int y = 7; y <= 10; y++) for (int x = 2; x <= 3; x++) dot.accept(new int[]{x, y}, 170);   // левая рука висит
            for (int x = 5; x <= 6; x++) for (int y = 12; y <= 13; y++) dot.accept(new int[]{x, y}, 200);  // ноги
            for (int x = 7; x <= 8; x++) for (int y = 12; y <= 13; y++) dot.accept(new int[]{x, y}, 200);
            // правая рука: от плеча (10,7) к кисти
            int hx = hands[f][0], hy = hands[f][1];
            int x0 = 10, y0 = 7;
            int steps = Math.max(Math.abs(hx - x0), Math.abs(hy - y0)) + 1;
            for (int i = 0; i <= steps; i++) {
                dot.accept(new int[]{x0 + (hx - x0) * i / steps, y0 + (hy - y0) * i / steps}, 170);
            }
            for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) dot.accept(new int[]{hx + dx, hy + dy}, 255);
            for (int y = 0; y < 14; y++)
                for (int x = 0; x < 14; x++) {
                    int v = wave[y][x];
                    if (v == 0) continue;
                    if (Math.hypot(x + 0.5 - 7, y + 0.5 - 7) > 7.0) continue;
                    img.setRGB(ox + x, oy + y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
        }
        File f = RES.resolve("textures/block/terminal_anim.png").toFile();
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
    }

    static void all() throws Exception {
        terminalAnim();
        bookArt();
        atlasCasing();
        atlasCasingTop();
        atlasConduit();
        expander("channel_expander", CYAN, CYAN_D, CYAN_L, BRASS, BRASS_D);
        expander("quantum_channel_expander", VIOLET, VIOLET_D, 0xE4D6FF, GOLD, 0xFF7a5f12);
        bushing();
        atlant();
        coreParts();
        brassPlate();
        cryo();
        resonator();
        jar();
    }

    public static void main(String[] args) throws Exception {
        all();
        Models.generate();
        Forge2.all();
        Gen3.all();
        Resonator.all();
        Catalog.all();
        Items.all();
        Interface.all();
        Logo.make();
        System.out.println("ArtForge: готово");
    }
}

