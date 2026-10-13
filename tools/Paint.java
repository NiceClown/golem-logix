import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Холст для процедурной пиксельной графики Golem Logix. Все координаты — в «юнитах блока» (1/16 блока, y вниз),
 * плотность задаёт k (текселей на юнит): k=4 даёт 64 пикселя на блок. Палитры — рампы от тёмного к светлому,
 * свет всегда падает сверху-слева.
 */
public final class Paint {
    // ---------------------------------------------------------------- палитры (тёмный -> светлый)
    static final int[] BRASS = {0x2a1d07, 0x523a0f, 0x7b5a1b, 0xa07a28, 0xc29b3c, 0xe0bc58, 0xf8e3a0};
    static final int[] BRONZE = {0x1c0e07, 0x35190c, 0x552b15, 0x74401f, 0x955828, 0xb97538, 0xdba066};
    static final int[] IRON = {0x0c0e11, 0x16191e, 0x23272e, 0x31353f, 0x444a56, 0x5d6573, 0x8089a0};
    static final int[] CYAN = {0x062426, 0x0d4547, 0x136f70, 0x20a8a6, 0x45d6d2, 0x96f2ee, 0xe2fffe};
    static final int[] VIOLET = {0x160b30, 0x2c1a5c, 0x4a2e9a, 0x7050d8, 0x9e80f8, 0xc8b4ff, 0xf2ecff};
    static final int[] FROST = {0x2f5878, 0x558cb0, 0x86bedc, 0xb6e0f2, 0xd8f0fa, 0xeef9ff, 0xffffff};
    static final int[] COPPER = {0x2a1008, 0x522414, 0x7c3a1e, 0xa4552a, 0xc97038, 0xe99354, 0xffc48c};
    static final int[] BLUE = {0x0a1a40, 0x11306e, 0x1a4b9a, 0x2a68c8, 0x4c8be6, 0x86b6f6, 0xcfe4ff};
    static final int[] CREAM = {0x6a5e3c, 0x8c7f56, 0xb3a574, 0xd2c694, 0xe9e0b6, 0xf6efd2, 0xffffff};

    final int w, h;
    final double k;
    final int[] px;
    /**
     * Маска светящихся пикселей (руны, светящиеся вставки): по ней {@link #save} пишет слой {@code имя_glow.png}, который
     * клиент рисует поверх грани со свечением. null — пока ничего светящегося не нарисовано.
     */
    boolean[] lit;
    /** Сейчас рисуется светящееся (руна, свечение): пиксели попадают в маску. */
    private boolean glowing;
    /**
     * Живые детали холста (стрелки манометров, шестерни, вентиляторы, экраны, огоньки): при сохранении текстура становится
     * анимированной — каждый кадр это неподвижный слой {@link #stat} плюс все детали, нарисованные для своей доли цикла t.
     */
    interface Animator {
        /** Нарисовать деталь в доле цикла t (0..1, цикл замкнут: t = 1 совпадает с t = 0). */
        void draw(Paint p, double t);
    }

    record Anim(int x0, int y0, int x1, int y1, Animator fn) {
    }

    final List<Anim> anims = new ArrayList<>();
    /** Неподвижный слой: всё, кроме живых деталей (null — живых деталей нет). */
    private int[] stat;
    /** Сейчас рисуется живая деталь (в неподвижный слой не попадает). */
    private boolean inAnim;
    /** Холст — кадр анимации: живые детали в нём рисуются сразу, без регистрации. */
    private boolean frameCanvas;
    /** Вырезка из большой картины: кадры берутся у неё (смещение — в пикселях картины). */
    private Paint parent;
    private int offX, offY;
    /** Размах стрелок манометров в текущих кадрах (по устройству, см. {@link #gaugeTempo}). */
    static double gaugeAmp = 12;
    /**
     * Свои параметры анимации (0 — по умолчанию): кадров в цикле, тиков на кадр и плавное смешивание кадров. Фигурки,
     * которые ходят по текстуре, смешивать нельзя — двоились бы; шестерни и стрелки смешиваются плавно.
     */
    int animFrames = 0, frameTime = 0;
    boolean interpolate = true;

    Paint(double unitsW, double unitsH, double k) {
        this.k = k;
        this.w = (int) Math.round(unitsW * k);
        this.h = (int) Math.round(unitsH * k);
        this.px = new int[w * h];
    }

    /**
     * Регистрирует живую деталь в прямоугольнике (юниты; в его пределах она двигается) и рисует её первый кадр. Всё, что
     * нарисовано на холсте позже поверх этого места, в кадрах окажется под деталью.
     */
    void animate(double ux0, double uy0, double ux1, double uy1, Animator fn) {
        if (frameCanvas) {
            fn.draw(this, 0);
            return;
        }
        if (stat == null) {
            stat = px.clone();
        }
        anims.add(new Anim((int) Math.floor(ux0 * k) - 1, (int) Math.floor(uy0 * k) - 1, (int) Math.ceil(ux1 * k) + 1,
                (int) Math.ceil(uy1 * k) + 1, fn));
        boolean was = inAnim;
        inAnim = true;
        fn.draw(this, 0);
        inAnim = was;
    }

    /** Кадр f из n (пиксели всей картины): неподвижный слой и живые детали. */
    private final java.util.Map<Integer, int[]> frames = new java.util.HashMap<>();

    private int[] frame(int f, int n) {
        return frames.computeIfAbsent(f, key -> {
            Paint fr = new Paint((double) w / k, (double) h / k, k);
            fr.frameCanvas = true;
            System.arraycopy(stat != null ? stat : px, 0, fr.px, 0, px.length);
            for (Anim a : anims) {
                a.fn().draw(fr, (double) f / n);
            }
            return fr.px;
        });
    }

    /** Есть ли живые детали, попадающие в этот холст (у вырезки — в её окно на картине). */
    boolean animated() {
        Paint root = parent != null ? parent : this;
        for (Anim a : root.anims) {
            int ox = parent != null ? offX : 0, oy = parent != null ? offY : 0;
            if (a.x0() < ox + w && a.x1() > ox && a.y0() < oy + h && a.y1() > oy) {
                return true;
            }
        }
        return false;
    }

    /** Пиксели кадра f этого холста (у вырезки — её окно из кадра картины). */
    private int[] framePixels(int f, int n) {
        if (parent == null) {
            return frame(f, n);
        }
        int[] src = parent.frame(f, n), out = new int[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int x = offX + i, y = offY + j;
                out[j * w + i] = x >= 0 && y >= 0 && x < parent.w && y < parent.h ? src[y * parent.w + x] : 0;
            }
        }
        return out;
    }

    /** Записать пиксель и в неподвижный слой (если это не живая деталь). */
    private void put(int i, int c) {
        px[i] = c;
        if (stat != null && !inAnim) {
            stat[i] = c;
        }
    }

    private void markLit(int i, boolean on) {
        if (on && lit == null) {
            lit = new boolean[w * h];
        }
        if (lit != null) {
            lit[i] = on;
        }
    }

    // ------------------------------------------------------------------------- цвета
    static int rgb(int c) {
        return 0xFF000000 | (c & 0xFFFFFF);
    }

    static int argb(int a, int c) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ba = b >>> 24;
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        int al = (int) (aa * (1 - t) + ba * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    static int shade(int c, double f) {
        int a = c >>> 24;
        int r = (int) Math.max(0, Math.min(255, ((c >> 16) & 255) * f));
        int g = (int) Math.max(0, Math.min(255, ((c >> 8) & 255) * f));
        int b = (int) Math.max(0, Math.min(255, (c & 255) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Цвет из рампы: t в [0..1] (0 — самый тёмный), с интерполяцией. */
    static int ramp(int[] r, double t) {
        t = Math.max(0, Math.min(1, t)) * (r.length - 1);
        int i = (int) Math.floor(t);
        if (i >= r.length - 1) {
            return rgb(r[r.length - 1]);
        }
        return mix(rgb(r[i]), rgb(r[i + 1]), t - i);
    }

    /** Цвет из рампы по индексу (дробному). */
    static int at(int[] r, double idx) {
        return ramp(r, idx / (r.length - 1));
    }

    // --------------------------------------------------------------------- пиксели
    int tx(double u) {
        return (int) Math.round(u * k);
    }

    void px(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < w && y < h) {
            put(y * w + x, c);
            markLit(y * w + x, glowing);
        }
    }

    int get(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h ? px[y * w + x] : 0;
    }

    /** Накладывает цвет (с альфой) на то, что уже нарисовано. */
    void over(int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        int a = c >>> 24;
        if (a == 0) {
            return;
        }
        // светящийся слой: заметная часть ореола тоже светится; плотная краска поверх гасит свечение
        if (glowing ? a >= 40 : a >= 128) {
            markLit(y * w + x, glowing);
        }
        int i = y * w + x;
        px[i] = blend(px[i], c);
        if (stat != null && !inAnim) {
            stat[i] = blend(stat[i], c);
        }
    }

    /** Цвет c с альфой поверх d. */
    static int blend(int d, int c) {
        int a = c >>> 24;
        if (a == 255) {
            return c;
        }
        int da = d >>> 24;
        double fa = a / 255.0, fd = da / 255.0;
        double oa = fa + fd * (1 - fa);
        if (oa <= 0) {
            return d;
        }
        int r = (int) ((((c >> 16) & 255) * fa + ((d >> 16) & 255) * fd * (1 - fa)) / oa);
        int g = (int) ((((c >> 8) & 255) * fa + ((d >> 8) & 255) * fd * (1 - fa)) / oa);
        int b = (int) (((c & 255) * fa + (d & 255) * fd * (1 - fa)) / oa);
        return ((int) (oa * 255) << 24) | (r << 16) | (g << 8) | b;
    }

    @FunctionalInterface
    interface PixelFn {
        int at(double u, double v);
    }

    /** Закрашивает прямоугольник (юниты) функцией цвета; 0 — не трогать. */
    void fill(double x0, double y0, double x1, double y1, PixelFn f) {
        int ix0 = (int) Math.floor(x0 * k + 0.5), iy0 = (int) Math.floor(y0 * k + 0.5);
        int ix1 = (int) Math.floor(x1 * k + 0.5), iy1 = (int) Math.floor(y1 * k + 0.5);
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                int c = f.at((i + 0.5) / k, (j + 0.5) / k);
                if (c != 0) {
                    px(i, j, c);
                }
            }
        }
    }

    void rect(double x0, double y0, double x1, double y1, int c) {
        fill(x0, y0, x1, y1, (u, v) -> c);
    }

    /** Накладывает полупрозрачный прямоугольник. */
    void tint(double x0, double y0, double x1, double y1, int c) {
        int ix0 = (int) Math.floor(x0 * k + 0.5), iy0 = (int) Math.floor(y0 * k + 0.5);
        int ix1 = (int) Math.floor(x1 * k + 0.5), iy1 = (int) Math.floor(y1 * k + 0.5);
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                over(i, j, c);
            }
        }
    }

    void erase(double x0, double y0, double x1, double y1) {
        int ix0 = (int) Math.floor(x0 * k + 0.5), iy0 = (int) Math.floor(y0 * k + 0.5);
        int ix1 = (int) Math.floor(x1 * k + 0.5), iy1 = (int) Math.floor(y1 * k + 0.5);
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                px(i, j, 0);
            }
        }
    }

    void frame(double x0, double y0, double x1, double y1, double width, int c) {
        rect(x0, y0, x1, y0 + width, c);
        rect(x0, y1 - width, x1, y1, c);
        rect(x0, y0 + width, x0 + width, y1 - width, c);
        rect(x1 - width, y0 + width, x1, y1 - width, c);
    }

    /** Линейный градиент по вертикали (сверху вниз): рампа от t0 до t1. */
    void gradV(double x0, double y0, double x1, double y1, int[] ramp, double t0, double t1) {
        fill(x0, y0, x1, y1, (u, v) -> ramp(ramp, t0 + (t1 - t0) * (v - y0) / (y1 - y0)));
    }

    void gradH(double x0, double y0, double x1, double y1, int[] ramp, double t0, double t1) {
        fill(x0, y0, x1, y1, (u, v) -> ramp(ramp, t0 + (t1 - t0) * (u - x0) / (x1 - x0)));
    }

    // ------------------------------------------------------------- объём: скос и зерно
    /**
     * Объёмная плита: основной тон mid (индекс рампы), светлая кромка сверху-слева, тёмная снизу-справа,
     * толщина кромки bw текселей (по умолчанию 1).
     */
    void slab(double x0, double y0, double x1, double y1, int[] ramp, double mid, int bw) {
        int ix0 = (int) Math.floor(x0 * k + 0.5), iy0 = (int) Math.floor(y0 * k + 0.5);
        int ix1 = (int) Math.floor(x1 * k + 0.5), iy1 = (int) Math.floor(y1 * k + 0.5);
        int base = at(ramp, mid);
        int hi = at(ramp, Math.min(ramp.length - 1, mid + 1.6));
        int hi2 = at(ramp, Math.min(ramp.length - 1, mid + 0.8));
        int lo = at(ramp, Math.max(0, mid - 1.6));
        int lo2 = at(ramp, Math.max(0, mid - 0.8));
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                int c = base;
                int dl = i - ix0, dt = j - iy0, dr = ix1 - 1 - i, db = iy1 - 1 - j;
                if (dt < bw && dt <= dl && dt <= dr) {
                    c = hi;
                } else if (dl < bw && dl <= dt && dl <= db) {
                    c = hi2;
                } else if (db < bw && db <= dl && db <= dr) {
                    c = lo;
                } else if (dr < bw && dr <= dt && dr <= db) {
                    c = lo2;
                }
                px(i, j, c);
            }
        }
    }

    /** Шум яркости по прямоугольнику (только непрозрачные пиксели). */
    void grain(double x0, double y0, double x1, double y1, double amount, long seed) {
        Random rnd = new Random(seed);
        int ix0 = (int) Math.floor(x0 * k + 0.5), iy0 = (int) Math.floor(y0 * k + 0.5);
        int ix1 = (int) Math.floor(x1 * k + 0.5), iy1 = (int) Math.floor(y1 * k + 0.5);
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                int c = get(i, j);
                if ((c >>> 24) != 0) {
                    px(i, j, shade(c, 1 + (rnd.nextDouble() - 0.5) * 2 * amount));
                }
            }
        }
    }

    /** Прожилки вдоль x (как у шлифованного металла или дерева): яркость строки + шум. */
    void streaks(double x0, double y0, double x1, double y1, double amount, long seed, boolean horizontal) {
        Random rnd = new Random(seed);
        int ix0 = (int) Math.floor(x0 * k + 0.5), iy0 = (int) Math.floor(y0 * k + 0.5);
        int ix1 = (int) Math.floor(x1 * k + 0.5), iy1 = (int) Math.floor(y1 * k + 0.5);
        int n = horizontal ? iy1 - iy0 : ix1 - ix0;
        double[] row = new double[Math.max(1, n)];
        for (int i = 0; i < n; i++) {
            row[i] = 1 + (rnd.nextDouble() - 0.5) * 2 * amount;
        }
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                int c = get(i, j);
                if ((c >>> 24) != 0) {
                    double f = row[horizontal ? j - iy0 : i - ix0] * (1 + (rnd.nextDouble() - 0.5) * amount * 0.6);
                    px(i, j, shade(c, f));
                }
            }
        }
    }

    // --------------------------------------------------------------------- фигуры
    void disc(double cx, double cy, double r, int c) {
        discFn(cx, cy, r, (u, v) -> c);
    }

    void discFn(double cx, double cy, double r, PixelFn f) {
        int x0 = (int) Math.floor((cx - r) * k) - 1, x1 = (int) Math.ceil((cx + r) * k) + 1;
        int y0 = (int) Math.floor((cy - r) * k) - 1, y1 = (int) Math.ceil((cy + r) * k) + 1;
        for (int j = y0; j <= y1; j++) {
            for (int i = x0; i <= x1; i++) {
                double u = (i + 0.5) / k, v = (j + 0.5) / k;
                if (Math.hypot(u - cx, v - cy) <= r) {
                    int c = f.at(u, v);
                    if (c != 0) {
                        px(i, j, c);
                    }
                }
            }
        }
    }

    void ring(double cx, double cy, double r0, double r1, int c) {
        int x0 = (int) Math.floor((cx - r1) * k) - 1, x1 = (int) Math.ceil((cx + r1) * k) + 1;
        int y0 = (int) Math.floor((cy - r1) * k) - 1, y1 = (int) Math.ceil((cy + r1) * k) + 1;
        for (int j = y0; j <= y1; j++) {
            for (int i = x0; i <= x1; i++) {
                double d = Math.hypot((i + 0.5) / k - cx, (j + 0.5) / k - cy);
                if (d >= r0 && d <= r1) {
                    px(i, j, c);
                }
            }
        }
    }

    /** Шар/диск с объёмным светом из левого верхнего угла. */
    void sphere(double cx, double cy, double r, int[] ramp, double lo, double hi) {
        discFn(cx, cy, r, (u, v) -> {
            double dx = (u - cx) / r, dy = (v - cy) / r;
            double lit = 0.5 + 0.5 * (-dx * 0.55 - dy * 0.75) / Math.max(0.2, Math.hypot(dx, dy) + 0.4);
            double edge = Math.hypot(dx, dy);
            double t = lo + (hi - lo) * Math.max(0, Math.min(1, lit - edge * edge * 0.25 + 0.15));
            return ramp(ramp, t);
        });
    }

    void line(double x0, double y0, double x1, double y1, double width, int c) {
        double dx = x1 - x0, dy = y1 - y0, len2 = dx * dx + dy * dy;
        int ix0 = (int) Math.floor((Math.min(x0, x1) - width) * k) - 1, ix1 = (int) Math.ceil((Math.max(x0, x1) + width) * k) + 1;
        int iy0 = (int) Math.floor((Math.min(y0, y1) - width) * k) - 1, iy1 = (int) Math.ceil((Math.max(y0, y1) + width) * k) + 1;
        for (int j = iy0; j <= iy1; j++) {
            for (int i = ix0; i <= ix1; i++) {
                double u = (i + 0.5) / k, v = (j + 0.5) / k;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((u - x0) * dx + (v - y0) * dy) / len2));
                if (Math.hypot(u - (x0 + t * dx), v - (y0 + t * dy)) <= width / 2) {
                    px(i, j, c);
                }
            }
        }
    }

    /** Закрашивает многоугольник (чётно-нечётное правило). */
    void poly(double[] xs, double[] ys, int c) {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        for (int j = (int) Math.floor(minY * k); j <= (int) Math.ceil(maxY * k); j++) {
            for (int i = (int) Math.floor(minX * k); i <= (int) Math.ceil(maxX * k); i++) {
                double u = (i + 0.5) / k, v = (j + 0.5) / k;
                boolean in = false;
                for (int a = 0, b = xs.length - 1; a < xs.length; b = a++) {
                    if ((ys[a] > v) != (ys[b] > v) && u < (xs[b] - xs[a]) * (v - ys[a]) / (ys[b] - ys[a]) + xs[a]) {
                        in = !in;
                    }
                }
                if (in) {
                    px(i, j, c);
                }
            }
        }
    }

    // -------------------------------------------------------------------- детали
    /** Шляпка винта с крестообразным шлицем. */
    void screw(double cx, double cy, double r, int[] ramp, double angle) {
        sphere(cx, cy, r, ramp, 0.25, 0.85);
        double c = Math.cos(angle), s = Math.sin(angle);
        int dark = at(ramp, 0.5);
        line(cx - c * r * 0.75, cy - s * r * 0.75, cx + c * r * 0.75, cy + s * r * 0.75, Math.max(1.0 / k, r * 0.28), dark);
        line(cx + s * r * 0.75, cy - c * r * 0.75, cx - s * r * 0.75, cy + c * r * 0.75, Math.max(1.0 / k, r * 0.28), dark);
    }

    /** Заклёпка: маленькая выпуклая шляпка. */
    void rivet(double cx, double cy, double r, int[] ramp) {
        sphere(cx, cy, r, ramp, 0.3, 1.0);
    }

    /**
     * Шестерня с чёткими зубьями, тёмным контуром, спицами и осью — живая: за цикл анимации поворачивается на целое число
     * зубьев (стык цикла незаметен); соседние шестерни крутятся в разные стороны.
     */
    void gear(double cx, double cy, double r, int teeth, double rot, int[] ramp, double mid) {
        int dir = Math.floorMod((long) Math.round(cx * 3 + cy * 5), 2L) == 0 ? 1 : -1;
        int step = Math.max(1, Math.round(teeth / 4f));
        animate(cx - r - 0.3, cy - r - 0.3, cx + r + 0.3, cy + r + 0.3,
                (p, t) -> p.gearStatic(cx, cy, r, teeth, rot + dir * t * 2 * Math.PI * step / teeth, ramp, mid));
    }

    /** Неподвижная шестерня (кадр). */
    void gearStatic(double cx, double cy, double r, int teeth, double rot, int[] ramp, double mid) {
        Paint g = new Paint((double) w / k, (double) h / k, k);
        int n = teeth * 4;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            double a = rot + i * 2 * Math.PI / n;
            double rr = (i % 4 == 0 || i % 4 == 1) ? r : r * 0.78;
            xs[i] = cx + Math.cos(a) * rr;
            ys[i] = cy + Math.sin(a) * rr;
        }
        g.polyFn(xs, ys, (u, v) -> {
            double dx = u - cx, dy = v - cy;
            double lit = 0.5 + 0.5 * (-dx * 0.6 - dy * 0.8) / Math.max(r, 0.01);
            return at(ramp, mid - 1.0 + 2.0 * lit);
        });
        // ступица, отверстия между спицами
        double body = r * 0.78;
        int hole = at(ramp, 0.3);
        g.disc(cx, cy, body * 0.86, at(ramp, mid - 0.9));
        for (int i = 0; i < 5; i++) {
            double a = rot + i * 2 * Math.PI / 5 + Math.PI / 5;
            g.disc(cx + Math.cos(a) * body * 0.5, cy + Math.sin(a) * body * 0.5, body * 0.2, hole);
        }
        g.sphere(cx, cy, Math.max(0.5, r * 0.3), ramp, 0.4, 1.0);
        g.disc(cx, cy, Math.max(0.12, r * 0.08), hole);
        paste(g.outlined(at(ramp, 0.2)), 0, 0);
    }

    /** Закрашивает многоугольник функцией цвета. */
    void polyFn(double[] xs, double[] ys, PixelFn f) {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        for (int j = (int) Math.floor(minY * k); j <= (int) Math.ceil(maxY * k); j++) {
            for (int i = (int) Math.floor(minX * k); i <= (int) Math.ceil(maxX * k); i++) {
                double u = (i + 0.5) / k, v = (j + 0.5) / k;
                boolean in = false;
                for (int a = 0, b = xs.length - 1; a < xs.length; b = a++) {
                    if ((ys[a] > v) != (ys[b] > v) && u < (xs[b] - xs[a]) * (v - ys[a]) / (ys[b] - ys[a]) + xs[a]) {
                        in = !in;
                    }
                }
                if (in) {
                    int c = f.at(u, v);
                    if (c != 0) {
                        px(i, j, c);
                    }
                }
            }
        }
    }

    /** Копия, у которой пограничные (рядом с прозрачным) непрозрачные пиксели перекрашены в цвет контура. */
    Paint outlined(int edge) {
        Paint p = new Paint((double) w / k, (double) h / k, k);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = px[j * w + i];
                if ((c >>> 24) == 0) {
                    continue;
                }
                boolean border = (get(i - 1, j) >>> 24) == 0 || (get(i + 1, j) >>> 24) == 0
                        || (get(i, j - 1) >>> 24) == 0 || (get(i, j + 1) >>> 24) == 0;
                p.px[j * w + i] = border ? edge : c;
                if (!border && lit != null && lit[j * w + i]) {
                    p.markLit(j * w + i, true);
                }
            }
        }
        return p;
    }

    /** Манометр: латунный ободок, циферблат, деления, стрелка (angle — градусы, 0 = влево, 180 = вправо). */
    void gauge(double cx, double cy, double r, double needleDeg, int needleColor) {
        sphere(cx, cy, r, BRASS, 0.2, 0.95);
        disc(cx, cy, r * 0.8, ramp(IRON, 0.1));
        discFn(cx, cy, r * 0.74, (u, v) -> {
            double dx = (u - cx) / r, dy = (v - cy) / r;
            return ramp(CREAM, 0.6 + 0.35 * (-dx * 0.3 - dy * 0.4));
        });
        for (int i = 0; i <= 8; i++) {
            double a = Math.toRadians(180 + i * 22.5 + 0);
            // дуга сверху: от 180° (влево) до 360° (вправо) идёт через верх
            double x0 = cx + Math.cos(a) * r * 0.5, y0 = cy + Math.sin(a) * r * 0.5;
            double x1 = cx + Math.cos(a) * r * 0.66, y1 = cy + Math.sin(a) * r * 0.66;
            line(x0, y0, x1, y1, Math.max(1.0 / k, r * 0.08), ramp(IRON, i > 6 ? 0.1 : 0.2));
        }
        // стрелка живая: давление «дышит» вокруг исходного значения, с подрагиванием (размах — по устройству)
        double phase = Math.floorMod((long) (cx * 37 + cy * 61 + needleDeg * 13), 628L) / 100.0;
        animate(cx - r, cy - r, cx + r, cy + r, (p, t) -> {
            double a = 2 * Math.PI * t;
            double deg = needleDeg + gaugeAmp * Math.sin(a + phase) + gaugeAmp * 0.35 * Math.sin(3 * a + phase * 2.1);
            p.needle(cx, cy, r, Math.max(4, Math.min(176, deg)), needleColor);
        });
    }

    /** Стрелка, ось и блик на стекле манометра. */
    private void needle(double cx, double cy, double r, double deg, int color) {
        double a = Math.toRadians(180 + deg);
        line(cx, cy, cx + Math.cos(a) * r * 0.62, cy + Math.sin(a) * r * 0.62, Math.max(1.0 / k, r * 0.12), color);
        sphere(cx, cy, r * 0.14, IRON, 0.3, 0.9);
        line(cx - r * 0.45, cy - r * 0.5, cx + r * 0.1, cy - r * 0.62, Math.max(1.0 / k, r * 0.1), argb(150, 0xFFFFFF));
    }

    /** Сетка-решётка (ромбы): провода wire на фоне bg, шаг pitch. */
    void mesh(double x0, double y0, double x1, double y1, double pitch, double wireWidth, int wireLo, int wireHi, int bg) {
        fill(x0, y0, x1, y1, (u, v) -> {
            double a = ((u - x0) + (v - y0)) / pitch, b = ((u - x0) - (v - y0)) / pitch;
            double fa = Math.abs(a - Math.rint(a)) * pitch, fb = Math.abs(b - Math.rint(b)) * pitch;
            double d = Math.min(fa, fb);
            if (d < wireWidth / 2) {
                return (((u - x0) + (v - y0)) * 7 % 3 < 1.5) ? wireHi : wireLo;
            }
            return bg;
        });
    }

    // ----------------------------------------------------------------------- руны
    /** Контуры рун старшего футарка в сетке 0..4 x 0..6 (отрезки). */
    static final double[][][] RUNES = {
            {{0, 0, 0, 6}, {0, 4, 3, 2}, {0, 2, 3, 0}},                             // 0 fehu
            {{0, 0, 0, 6}, {0, 6, 2, 5}, {2, 5, 4, 1}, {4, 1, 4, 0}},              // 1 uruz
            {{0, 0, 0, 6}, {0, 4, 3, 3}, {3, 3, 0, 2}},                             // 2 thurisaz
            {{0, 0, 0, 6}, {0, 6, 3, 4}, {0, 4, 3, 2}},                             // 3 ansuz
            {{0, 0, 0, 6}, {0, 6, 3, 5}, {3, 5, 0, 3}, {0, 3, 3, 0}},               // 4 raido
            {{3, 0, 0, 3}, {0, 3, 3, 6}},                                           // 5 kenaz
            {{0, 0, 4, 6}, {0, 6, 4, 0}},                                           // 6 gebo
            {{0, 0, 0, 6}, {0, 6, 3, 5}, {3, 5, 0, 3}},                             // 7 wunjo
            {{0, 0, 0, 6}, {4, 0, 4, 6}, {0, 4, 4, 2}},                             // 8 hagalaz
            {{2, 0, 2, 6}, {0, 3, 4, 3}, {0, 2, 4, 4}},                             // 9 nauthiz
            {{2, 0, 2, 6}},                                                         // 10 isa
            {{3, 0, 1, 2}, {1, 2, 3, 4}, {3, 4, 1, 6}},                             // 11 sowilo
            {{2, 0, 2, 6}, {0, 2, 2, 6}, {4, 2, 2, 6}},                             // 12 tiwaz
            {{0, 0, 0, 6}, {0, 6, 3, 4.5}, {3, 4.5, 0, 3}, {0, 3, 3, 1.5}, {3, 1.5, 0, 0}}, // 13 berkano
            {{0, 0, 0, 6}, {4, 0, 4, 6}, {0, 6, 4, 3}, {4, 3, 0, 0}},               // 14 mannaz
            {{0, 0, 0, 6}, {0, 6, 3, 4}},                                           // 15 laguz
            {{0, 0, 4, 6}, {0, 6, 4, 0}, {0, 0, 0, 6}, {4, 0, 4, 6}},               // 16 dagaz
            {{2, 6, 0, 4}, {0, 4, 2, 2}, {2, 2, 4, 4}, {4, 4, 2, 6}, {0, 0, 2, 2}, {4, 0, 2, 2}}, // 17 othala
            {{2, 0, 0, 2}, {0, 2, 2, 4}, {2, 2, 4, 4}, {4, 4, 2, 6}},               // 18 jera
            {{2, 0, 2, 6}, {2, 0, 3.6, 1.3}, {2, 6, 0.4, 4.7}},                     // 19 eihwaz
            {{0, 0, 0, 6}, {0, 0, 3, 1.5}, {0, 6, 3, 4.5}, {3, 1.5, 3, 2.3}, {3, 4.5, 3, 3.7}}, // 20 perthro
            {{2, 0, 2, 6}, {2, 2.5, 0, 0}, {2, 2.5, 4, 0}},                         // 21 algiz
            {{0, 0, 0, 6}, {4, 0, 4, 6}, {0, 0, 2, 2.5}, {2, 2.5, 4, 0}},           // 22 ehwaz
            {{2, 0, 4, 3}, {4, 3, 2, 6}, {2, 6, 0, 3}, {0, 3, 2, 0}},               // 23 ingwaz
            // знаки «големской» алхимии
            {{2, 0, 2, 6}, {0, 0, 0, 2}, {4, 0, 4, 2}, {0, 2, 4, 2}, {1, 6, 3, 6}}, // 24 трезубец
            {{2, 0, 4, 5}, {4, 5, 0, 5}, {0, 5, 2, 0}, {2, 2.2, 2, 6}},             // 25 треугольник с осью
            {{0, 3, 2, 1}, {2, 1, 4, 3}, {4, 3, 2, 5}, {2, 5, 0, 3}, {2, 2.6, 2, 3.4}}, // 26 око
            {{0, 0, 4, 0}, {0, 0, 4, 6}, {4, 0, 0, 6}, {0, 6, 4, 6}},               // 27 песочные часы
            {{2, 0, 2, 6}, {0, 4, 2, 6}, {4, 4, 2, 6}, {0, 1.5, 4, 1.5}},           // 28 якорь
            {{0, 0, 4, 0}, {4, 0, 0, 6}, {0, 6, 4, 6}, {1, 3, 3, 3}},               // 29 молния-зет
            // дополнительные руны старшего/младшего футарка, нортумбрийские и алхимические символы (50+ вариантов)
            {{0, 0, 4, 0}, {2, 0, 2, 6}, {0, 3, 2, 1.5}, {4, 4.5, 2, 3}},           // 30 cweorth (жертвенный огонь)
            {{0, 0, 0, 3}, {4, 0, 4, 3}, {0, 3, 4, 3}, {2, 3, 2, 6}},               // 31 calc (чаша)
            {{2, 0, 2, 6}, {0, 3, 4, 3}, {2, 1.5, 0, 3}, {0, 3, 2, 4.5}, {2, 4.5, 4, 3}, {4, 3, 2, 1.5}}, // 32 stan (камень-кристалл)
            {{2, 0, 2, 6}, {0, 0, 4, 0}, {0, 2, 4, 2}, {0, 2, 2, 0}, {4, 2, 2, 0}}, // 33 gar (копьё)
            {{2, 0, 2, 6}, {2, 2, 0, 0}, {2, 2, 4, 0}, {2, 4, 0, 2}, {2, 4, 4, 2}}, // 34 ac (дуб познания)
            {{2, 0, 2, 6}, {0, 2, 4, 2}, {0, 0, 0, 4}, {4, 0, 4, 4}},               // 35 yr (лук лучника)
            {{0, 0, 0, 6}, {0, 5, 3, 3.5}, {0, 3.5, 3, 2}, {0, 2, 3, 0.5}},         // 36 os (божественное слово)
            {{0, 0, 0, 6}, {4, 0, 4, 6}, {0, 3, 4, 3}, {0, 1, 4, 5}},               // 37 ior (мировой змей)
            {{2, 0, 2, 6}, {2, 6, 0, 4}, {2, 6, 4, 4}, {0, 4, 0, 2}, {4, 4, 4, 2}}, // 38 ear (земля)
            {{0, 0, 4, 0}, {4, 0, 4, 3}, {4, 3, 0, 3}, {0, 3, 0, 6}, {0, 6, 4, 6}}, // 39 sol (солнечное колесо)
            {{0, 1, 4, 1}, {4, 1, 4, 5}, {4, 5, 0, 5}, {0, 5, 0, 1}, {0, 3, 4, 3}}, // 40 соль алхимии
            {{2, 0, 0, 3}, {0, 3, 4, 3}, {4, 3, 2, 0}, {2, 3, 2, 6}, {0, 4.5, 4, 4.5}}, // 41 сера алхимии
            {{2, 0, 0, 1.5}, {2, 0, 4, 1.5}, {2, 1.5, 2, 6}, {0, 3, 4, 3}, {0, 4.5, 4, 4.5}}, // 42 ртуть герметизма
            {{2, 0, 2, 6}, {0, 3, 4, 3}, {1, 1, 3, 1}, {1, 5, 3, 5}, {0.5, 2.5, 0.5, 3.5}, {3.5, 2.5, 3.5, 3.5}}, // 43 агисхьяльм (шлем ужаса)
            {{2, 0, 2, 6}, {0, 3, 4, 3}, {0.5, 1.5, 3.5, 4.5}, {3.5, 1.5, 0.5, 4.5}}, // 44 вегвизир (компас путей)
            {{2, 0, 0, 2}, {0, 2, 2, 5}, {2, 5, 4, 2}, {4, 2, 2, 0}, {2, 0, 2, 5}}, // 45 сердце голема
            {{0, 0, 4, 4}, {4, 0, 0, 4}, {0, 2, 4, 2}, {2, 0, 2, 6}, {0, 6, 4, 6}}, // 46 узел матричной ткани
            {{2, 0, 2, 6}, {0, 1.5, 4, 4.5}, {0, 4.5, 4, 1.5}, {1, 3, 3, 3}},       // 47 морозный кристалл
            {{2, 0, 3.5, 5}, {3.5, 5, 0, 2}, {0, 2, 4, 2}, {4, 2, 0.5, 5}, {0.5, 5, 2, 0}}, // 48 квинтэссенция
            {{0, 0, 4, 0}, {0, 0, 1, 4}, {4, 0, 3, 4}, {1, 4, 3, 4}, {2, 4, 2, 6}, {0.5, 6, 3.5, 6}}, // 49 атанор
            {{2, 0, 2, 6}, {2, 1, 0, 0}, {0, 0, 0, 3}, {0, 3, 2, 2}, {2, 1, 4, 0}, {4, 0, 4, 3}, {4, 3, 2, 2}}, // 50 лабрис
            {{0, 1, 2, 0}, {2, 0, 4, 1}, {4, 1, 4, 5}, {4, 5, 2, 6}, {2, 6, 0, 5}, {0, 5, 0, 1}, {1, 3, 3, 3}}, // 51 уроборос
            {{0, 0, 4, 0}, {0, 0, 0, 6}, {4, 0, 4, 6}, {1, 2, 3, 2}, {2, 2, 2, 6}}, // 52 врата архива
            {{2, 0, 2, 6}, {0, 2, 4, 2}, {0, 4, 4, 4}, {1, 1, 3, 1}, {1, 5, 3, 5}}, // 53 астральная монада
            {{2, 0, 0, 5}, {0, 5, 4, 5}, {4, 5, 2, 0}, {2, 2, 0, 2}, {2, 2, 4, 2}}, // 54 трикветр
            {{0, 0, 4, 0}, {0, 6, 4, 6}, {0, 0, 4, 6}, {4, 0, 0, 6}, {1, 3, 3, 3}}, // 55 хронос
            {{2, 0, 0, 6}, {2, 0, 4, 6}, {0, 6, 4, 6}, {1, 3.5, 3, 3.5}, {2, 2.5, 2, 4.5}} // 56 око провидения
    };

    /**
     * Руна высотой height юнитов с левым верхним углом (x, y); нужные тона: ядро и ореол. Номер глифа перемешивается с
     * положением руны: одинаковые номера в разных местах дают разные знаки, а одно и то же место — всегда тот же знак
     * (поэтому слои свечения, нарисованные отдельно по тем же координатам, совпадают с рисунком).
     */
    void rune(double x, double y, double height, int glyph, int core, int halo) {
        double s = height / 6.0;
        int mix = (int) (Math.round(x * 4) * 31 + Math.round(y * 4) * 17);
        double[][] segs = RUNES[Math.floorMod(glyph * 7 + mix, RUNES.length)];
        double wid = Math.max(1.0 / k, s * 0.36);
        boolean was = glowing;
        // светится только яркая руна; тёмная — это гравировка
        int luma = (((core >> 16) & 255) * 299 + ((core >> 8) & 255) * 587 + (core & 255) * 114) / 1000;
        glowing = luma >= 100;
        if (halo != 0) {
            for (double[] g : segs) {
                line(x + g[0] * s, y + g[1] * s, x + g[2] * s, y + g[3] * s, wid + 1.6 / k, halo);
            }
        }
        for (double[] g : segs) {
            line(x + g[0] * s, y + g[1] * s, x + g[2] * s, y + g[3] * s, wid, core);
        }
        glowing = was;
    }

    // ------------------------------------------------------------------- иней и лёд
    /** Сосулька из точки (x, y) вниз длиной len, шириной у основания wd. */
    void icicle(double x, double y, double len, double wd) {
        for (int j = 0; j < (int) (len * k); j++) {
            double t = j / (len * k);
            double half = wd * (1 - t) * 0.5;
            int y0 = (int) Math.round(y * k) + j;
            for (int i = (int) Math.floor((x - half) * k); i <= (int) Math.ceil((x + half) * k); i++) {
                double u = (i + 0.5) / k;
                if (Math.abs(u - x) <= Math.max(half, 0.4 / k)) {
                    double lit = (x - u) / Math.max(half, 0.001) * 0.5 + 0.5;
                    px(i, y0, ramp(FROST, 0.45 + 0.5 * lit - t * 0.2));
                }
            }
        }
    }

    /** Снежная шапка/иней вдоль горизонтального края: неровный белый слой сверху. */
    void frostLine(double x0, double x1, double y, double depth, long seed) {
        Random r = new Random(seed);
        for (int i = (int) Math.round(x0 * k); i < (int) Math.round(x1 * k); i++) {
            int d = (int) Math.round(depth * k * (0.4 + r.nextDouble()));
            for (int j = 0; j < d; j++) {
                px(i, (int) Math.round(y * k) + j, ramp(FROST, 0.85 + 0.15 * r.nextDouble() - j * 0.12));
            }
        }
    }

    void sparkle(double x, double y, int c) {
        int cx = (int) Math.round(x * k), cy = (int) Math.round(y * k);
        px(cx, cy, c);
        over(cx - 1, cy, argb(130, c));
        over(cx + 1, cy, argb(130, c));
        over(cx, cy - 1, argb(130, c));
        over(cx, cy + 1, argb(130, c));
    }

    /** Мягкое свечение вокруг прямоугольника (накладывается поверх). */
    void glowRect(double x0, double y0, double x1, double y1, double spread, int c, int alpha) {
        int ix0 = (int) Math.floor((x0 - spread) * k), iy0 = (int) Math.floor((y0 - spread) * k);
        int ix1 = (int) Math.ceil((x1 + spread) * k), iy1 = (int) Math.ceil((y1 + spread) * k);
        boolean was = glowing;
        glowing = true;
        for (int j = iy0; j < iy1; j++) {
            for (int i = ix0; i < ix1; i++) {
                double u = (i + 0.5) / k, v = (j + 0.5) / k;
                double dx = Math.max(Math.max(x0 - u, 0), u - x1), dy = Math.max(Math.max(y0 - v, 0), v - y1);
                double d = Math.hypot(dx, dy);
                if (d < spread) {
                    over(i, j, argb((int) (alpha * (1 - d / spread) * (1 - d / spread)), c));
                }
            }
        }
        glowing = was;
    }

    // --------------------------------------------------------------------- ввод-вывод
    /** Копия прямоугольной области (в юнитах). */
    Paint crop(double ux, double uy, double uw, double uh) {
        Paint p = new Paint(uw, uh, k);
        int ox = (int) Math.round(ux * k), oy = (int) Math.round(uy * k);
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                p.px[j * p.w + i] = get(ox + i, oy + j);
                int x = ox + i, y = oy + j;
                if (lit != null && x >= 0 && y >= 0 && x < w && y < h && lit[y * w + x]) {
                    p.markLit(j * p.w + i, true);
                }
            }
        }
        // живые детали остаются у картины: кадры вырезки — окна из её кадров
        p.parent = parent != null ? parent : this;
        p.offX = (parent != null ? offX : 0) + ox;
        p.offY = (parent != null ? offY : 0) + oy;
        return p;
    }

    /** Вставка другого холста в позицию (ux, uy). */
    void paste(Paint src, double ux, double uy) {
        int ox = (int) Math.round(ux * k), oy = (int) Math.round(uy * k);
        boolean was = glowing;
        for (int j = 0; j < src.h; j++) {
            for (int i = 0; i < src.w; i++) {
                int c = src.px[j * src.w + i];
                if ((c >>> 24) != 0) {
                    glowing = src.lit != null && src.lit[j * src.w + i];
                    over(ox + i, oy + j, c);
                }
            }
        }
        glowing = was;
    }

    Paint flipH() {
        Paint p = new Paint((double) w / k, (double) h / k, k);
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                p.px[j * w + (w - 1 - i)] = px[j * w + i];
                if (lit != null && lit[j * w + i]) {
                    p.markLit(j * w + (w - 1 - i), true);
                }
            }
        }
        return p;
    }

    /** Кадров в анимации манометров: шаг стрелки мал, с интерполяцией движение плавное. */
    static final int GAUGE_FRAMES = 32;

    /**
     * Темп манометров по устройству (префикс текстуры): {тиков на кадр, размах стрелки в градусах}. Насыщатель-кузня
     * «дышит» часто и широко, архивный шкаф — медленно, монитор рецептов — в среднем темпе, интерфейс — бойко и с большим
     * размахом (через него идёт весь поток).
     */
    static int[] gaugeTempo(String name) {
        if (name.startsWith("sat_")) {
            return new int[]{2, 20};
        }
        if (name.startsWith("cab_")) {
            return new int[]{4, 9};
        }
        if (name.startsWith("monitor_")) {
            return new int[]{2, 15};
        }
        if (name.startsWith("interface_")) {
            return new int[]{2, 24};
        }
        if (name.startsWith("pipe_")) {
            return new int[]{2, 22};
        }
        return new int[]{3, 12};
    }

    /** Текстура из атласа блоков (блоки и предметы): только для неё пишутся анимация манометров и слой свечения. */
    static boolean inBlockAtlas(File f) {
        String p = f.getPath().replace('\\', '/');
        return p.contains("textures/block/") || p.contains("textures/item/");
    }

    void save(File f) throws Exception {
        f.getParentFile().mkdirs();
        String name = f.getName().replace(".png", "");
        boolean atlas = inBlockAtlas(f);
        File meta = new File(f.getPath() + ".mcmeta");
        if (atlas && animated()) {
            int[] tempo = gaugeTempo(name);
            Paint root = parent != null ? parent : this;
            int n = root.animFrames > 0 ? root.animFrames : GAUGE_FRAMES;
            if (root.frameTime > 0) {
                tempo[0] = root.frameTime;
            }
            gaugeAmp = tempo[1];
            root.frames.clear();
            BufferedImage img = new BufferedImage(w, h * n, BufferedImage.TYPE_INT_ARGB);
            for (int fr = 0; fr < n; fr++) {
                int[] frame = framePixels(fr, n);
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        img.setRGB(i, fr * h + j, frame[j * w + i]);
                    }
                }
            }
            ImageIO.write(img, "png", f);
            // размер кадра — явно: у неквадратных текстур игра иначе режет кадры квадратами (мигание, сплюснутые стрелки);
            // раздел gauge — метка «анимацию написал Paint»: её можно стереть, когда живых деталей не станет
            Files.writeString(meta.toPath(), "{\n  \"animation\": {\n    \"frametime\": " + tempo[0] + ",\n    \"width\": " + w
                    + ",\n    \"height\": " + h + ",\n    \"interpolate\": " + root.interpolate + "\n  },\n  \"gauge\": {}\n}\n",
                    StandardCharsets.UTF_8);
        } else {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    img.setRGB(i, j, px[j * w + i]);
                }
            }
            ImageIO.write(img, "png", f);
            if (meta.exists() && Files.readString(meta.toPath()).contains("\"gauge\"")) {
                Files.delete(meta.toPath());
            }
        }
        if (atlas && !name.endsWith("_glow")) {
            saveGlow(new File(f.getParentFile(), name + "_glow.png"));
        }
    }

    /** Слой свечения: светящиеся пиксели в своём цвете, остальное прозрачно. Нет свечения — файла нет. */
    private void saveGlow(File g) throws Exception {
        boolean any = false;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; lit != null && i < px.length; i++) {
            if (lit[i] && (px[i] >>> 24) >= 26) {
                img.setRGB(i % w, i / w, px[i]);
                any = true;
            }
        }
        if (any) {
            ImageIO.write(img, "png", g);
        } else {
            Files.deleteIfExists(g.toPath());
        }
    }
}
