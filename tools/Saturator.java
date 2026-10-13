import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Насыщатель Маны — многоблок 3x3x2 (48x48x32 юнита): по концепту — латунная машина с рунными панелями, стеклянными колоннами
 * Source, прессом над наковальней и кристаллом на верхней балке. Фасад, спинка и боковины — большие «картины», нарезанные
 * по клеткам блоков; поверх — объёмные детали (колонны, стойки, наковальня, кристалл). Пресс, уровень Source в колоннах и
 * предмет рисует {@code ManaSaturatorRenderer}. Форму клеток в Java ({@code SaturatorShapes}) нужно держать в соответствии с
 * коробками {@link #geometry()}.
 *
 * <pre>
 * высоты (Y): цоколь 0..3, крылья 3..28, колонны и камера пресса 28..39, верхняя балка 39..44, кристалл 44..48
 * глубина (Z): 0..32; камера пресса и пьедестал — в передней половине (Z 2..14), задняя половина центра — глухая стенка
 * </pre>
 */
final class Saturator {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM, AMBER = Terminals.AMBER;
    static final int RED = Paint.argb(255, 0xb02020);

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    // ================================================================================================ детали-«кирпичики»
    static void runeTablet(Paint p, double x0, double y0, double x1, double y1, int[] glyphs) {
        p.slab(x0, y0, x1, y1, BRASS, 3.0, 1);
        p.slab(x0 + 0.6, y0 + 0.6, x1 - 0.6, y1 - 0.6, IRON, 0.8, 1);
        p.glowRect(x0 + 0.8, y0 + 0.8, x1 - 0.8, y1 - 0.8, 0.7, at(VIOLET, 3.5), 60);
        double h = y1 - y0 - 1.8, w = (x1 - x0 - 1.4) / glyphs.length;
        for (int i = 0; i < glyphs.length; i++) {
            p.rune(x0 + 0.9 + i * w + w * 0.18, y0 + 0.9, h, glyphs[i], at(VIOLET, 5.4), at(VIOLET, 3.0));
        }
        for (double[] c : new double[][]{{x0 + 0.3, y0 + 0.3}, {x1 - 0.3, y0 + 0.3}, {x0 + 0.3, y1 - 0.3}, {x1 - 0.3, y1 - 0.3}}) {
            p.rivet(c[0], c[1], 0.16, BRASS);
        }
    }

    /** Окно с шестернями и медным големом, работающим за стеклом. */
    static void golemWindow(Paint p, double x0, double y0, double x1, double y1) {
        p.slab(x0, y0, x1, y1, BRASS, 2.8, 1);
        p.slab(x0 + 0.5, y0 + 0.5, x1 - 0.5, y1 - 0.5, IRON, 0.7, 1);
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        p.gear(x0 + 1.5, y0 + 1.7, 1.1, 8, 0.2, BRASS, 3.0);
        p.gear(x1 - 1.6, y0 + 2.0, 1.4, 9, 0.4, COPPER, 3.0);
        // голем: квадратная голова с антенной, тело, руки
        double gx = cx - 0.2, gy = y1 - 3.4;
        p.slab(gx - 0.7, gy, gx + 0.7, gy + 1.0, COPPER, 3.4, 1);
        p.rect(gx - 0.1, gy - 0.5, gx + 0.1, gy, at(COPPER, 4.5));
        p.disc(gx - 0.35, gy + 0.4, 0.14, rgb(0x050607));
        p.disc(gx + 0.35, gy + 0.4, 0.14, rgb(0x050607));
        p.slab(gx - 0.9, gy + 1.0, gx + 0.9, gy + 2.2, COPPER, 2.8, 1);
        p.rect(gx - 1.3, gy + 1.1, gx - 0.9, gy + 2.0, at(COPPER, 2.4));
        p.rect(gx + 0.9, gy + 1.1, gx + 1.3, gy + 2.0, at(COPPER, 2.4));
        p.rect(x0 + 0.6, y0 + 0.6, x1 - 0.6, y0 + 0.75, argb(110, 0xE0F4FF));
        p.line(x0 + 0.7, y1 - 1.6, x0 + 2.0, y0 + 0.9, 0.18, argb(70, 0xFFFFFF));
    }

    /** Левая и правая панели: большой рунный квадрат в резной раме. */
    static void wingPanel(Paint p, double x0, int glyph, long seed) {
        double x1 = x0 + 16, y0 = 20, y1 = 45;
        Forge2.beam(p, x0, y0, x1, y1, 2.5, false, seed);
        p.grain(x0, y0, x1, y1, 0.04, seed + 1);
        // карниз-верхняя кромка
        p.slab(x0, y0, x1, y0 + 1.7, BRASS, 3.2, 1);
        for (double x = x0 + 1.3; x < x1; x += 2.9) {
            p.rivet(x, y0 + 0.85, 0.2, BRASS);
        }
        // рама
        p.slab(x0 + 2.0, y0 + 3.0, x1 - 2.0, y1 - 5.4, BRASS, 2.7, 1);
        p.slab(x0 + 2.7, y0 + 3.7, x1 - 2.7, y1 - 6.1, IRON, 0.8, 1);
        p.grain(x0 + 2.7, y0 + 3.7, x1 - 2.7, y1 - 6.1, 0.07, seed + 2);
        double cx = x0 + 8, cy = (y0 + 3.7 + y1 - 6.1) / 2;
        // резной узор: ромб из тонких светлых линий и кольцо
        double r = 4.4;
        for (int i = 0; i < 4; i++) {
            double a0 = i * Math.PI / 2 + Math.PI / 4, a1 = a0 + Math.PI / 2;
            p.line(cx + Math.cos(a0) * r, cy + Math.sin(a0) * r, cx + Math.cos(a1) * r, cy + Math.sin(a1) * r, 0.16, at(IRON, 3.3));
        }
        p.ring(cx, cy, 3.4, 3.55, at(IRON, 3.0));
        p.glowRect(cx - 1.6, cy - 3.0, cx + 1.6, cy + 3.0, 1.6, at(VIOLET, 3.4), 80);
        p.rune(cx - 1.6, cy - 3.0, 6.0, glyph, at(VIOLET, 5.8), at(VIOLET, 3.2));
        // угловые болты рамы и нижняя плашка с манометром
        for (double[] c : new double[][]{{x0 + 3.3, y0 + 4.3}, {x1 - 3.3, y0 + 4.3}, {x0 + 3.3, y1 - 6.7}, {x1 - 3.3, y1 - 6.7}}) {
            p.screw(c[0], c[1], 0.45, BRASS, c[0]);
        }
        p.slab(x0 + 2.0, y1 - 4.4, x1 - 2.0, y1 - 0.6, BRASS, 2.6, 1);
        p.slab(x0 + 2.6, y1 - 3.8, x1 - 2.6, y1 - 1.2, IRON, 0.9, 1);
        p.gauge(cx - 2.3, y1 - 2.5, 1.0, glyph * 7 % 100 + 40, RED);
        p.gauge(cx + 2.3, y1 - 2.5, 1.0, 130 - glyph * 5 % 60, RED);
        // иней на карнизе и сосульки
        p.frostLine(x0, x1, y0 - 0.1, 0.7, seed + 3);
        p.icicle(x0 + 3.0, y0 + 1.7, 1.3, 0.55);
        p.icicle(x1 - 4.0, y0 + 1.7, 1.7, 0.55);
    }

    /** Центральный ящик (плотная труба): патрубок под трубу сети, окно с големом, манометры. */
    static void crate(Paint p) {
        double x0 = 16, x1 = 32, y0 = 32, y1 = 45;
        Forge2.beam(p, x0, y0, x1, y1, 2.4, true, 1101);
        p.grain(x0, y0, x1, y1, 0.05, 1102);
        p.slab(x0, y0, x1, y0 + 1.5, BRASS, 3.2, 1);
        for (double x = x0 + 1.2; x < x1; x += 2.6) {
            p.rivet(x, y0 + 0.75, 0.2, BRASS);
        }
        // патрубок: латунное кольцо с бирюзовой кромкой и тёмным отверстием (сюда подключается труба сети)
        // патрубок компактный (6.4 юнита): окно с големом слева и манометры справа остаются открытыми
        double cx = 24, cy = 40;
        p.disc(cx, cy, 3.2, at(BRASS, 1.1));
        p.sphere(cx, cy, 3.05, BRASS, 0.25, 0.95);
        p.ring(cx, cy, 2.3, 2.45, at(BRASS, 5.0));
        p.disc(cx, cy, 2.2, rgb(0x061419));
        p.ring(cx, cy, 1.9, 2.1, at(CYAN, 4.8));
        p.glowRect(cx - 1.9, cy - 1.9, cx + 1.9, cy + 1.9, 0.4, at(CYAN, 3.2), 40);
        p.disc(cx, cy, 1.6, rgb(0x040a0d));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.screw(cx + Math.cos(a) * 2.7, cy + Math.sin(a) * 2.7, 0.22, BRASS, a);
        }
        // окно и приборы по бокам
        golemWindow(p, x0 + 0.9, y0 + 3.2, x0 + 4.7, y1 - 2.0);
        p.gauge(x1 - 2.6, y0 + 5.6, 1.35, 60, RED);
        p.gauge(x1 - 2.6, y0 + 9.7, 1.1, 130, RED);
        p.slab(x1 - 4.1, y1 - 2.6, x1 - 1.2, y1 - 1.5, BRASS, 3.0, 1);
        // угловые накладки
        for (double[] c : new double[][]{{x0, y1 - 2.0}, {x1 - 2.0, y1 - 2.0}}) {
            Forge2.brassBlock(p, c[0], c[1], c[0] + 2.0, c[1] + 2.0, (long) c[0]);
        }
        p.frostLine(x0, x1, y0 + 1.5, 0.45, 1103);
    }

    static void beamFront(Paint p) {
        double y0 = 4, y1 = 9;
        Forge2.beam(p, 0, y0, 48, y1, 2.9, true, 1001);
        p.slab(4.4, y0 + 0.9, 43.6, y1 - 0.9, BRONZE, 1.6, 1);
        Forge2.frieze(p, 4.6, 43.4, y0 + 0.9, y1 - 0.9, 2.1);
        Forge2.brassBlock(p, 0, y0, 4.4, y1, 1002);
        Forge2.brassBlock(p, 43.6, y0, 48, y1, 1003);
        // манометры на концах и рунная табличка по центру
        p.slab(7.2, y0 + 0.2, 13.0, y1 - 0.2, BRASS, 3.0, 1);
        p.gauge(10.1, (y0 + y1) / 2, 2.0, 72, RED);
        p.slab(35.0, y0 + 0.2, 40.8, y1 - 0.2, BRASS, 3.0, 1);
        p.gauge(37.9, (y0 + y1) / 2, 2.0, 118, RED);
        runeTablet(p, 18.5, y0 + 0.3, 29.5, y1 - 0.3, new int[]{1, 12, 8, 16});
        p.frostLine(0, 48, y0 - 0.05, 0.8, 1004);
        for (double x : new double[]{6.0, 15.0, 33.0, 42.0}) {
            p.icicle(x, y1, 1.2 + (x % 3) * 0.5, 0.6);
        }
    }

    static void plinthFront(Paint p) {
        double y0 = 45, y1 = 48;
        Forge2.beam(p, 0, y0, 48, y1, 2.6, true, 1011);
        p.slab(3.8, y0 + 0.5, 44.2, y1 - 0.5, BRONZE, 1.5, 1);
        Forge2.frieze(p, 4.0, 44.0, y0 + 0.5, y1 - 0.5, 1.9);
        Forge2.brassBlock(p, 0, y0, 3.8, y1, 1012);
        Forge2.brassBlock(p, 44.2, y0, 48, y1, 1013);
        for (double x : new double[]{8.0, 21.8, 35.6}) {
            p.slab(x, y0 + 0.2, x + 4.6, y1 - 0.2, BRASS, 3.0, 1);
            p.slab(x + 0.6, y0 + 0.7, x + 4.0, y1 - 0.7, IRON, 0.9, 1);
            p.rune(x + 1.55, y0 + 0.8, 1.4, (int) x, at(VIOLET, 5.4), at(VIOLET, 3.0));
        }
    }

    static Paint frontElevation() {
        Paint p = new Paint(48, 48, K);
        p.slab(0, 0, 48, 48, IRON, 1.4, 1);
        beamFront(p);
        wingPanel(p, 0, 1, 1021);
        wingPanel(p, 32, 12, 1031);
        crate(p);
        plinthFront(p);
        return p;
    }

    // ================================================================================================ спинка
    static void fan(Paint p, double cx, double cy, double r) {
        p.slab(cx - r - 0.9, cy - r - 0.9, cx + r + 0.9, cy + r + 0.9, BRASS, 2.6, 1);
        p.slab(cx - r - 0.4, cy - r - 0.4, cx + r + 0.4, cy + r + 0.4, IRON, 0.8, 1);
        p.disc(cx, cy, r, at(BRASS, 1.2));
        p.ring(cx, cy, r - 0.45, r, at(BRASS, 4.4));
        // крыльчатка крутится: за цикл — на две лопасти из семи
        p.animate(cx - r + 0.5, cy - r + 0.5, cx + r - 0.5, cy + r - 0.5, (q, t) -> {
            q.disc(cx, cy, r - 0.5, rgb(0x050607));
            for (int b = 0; b < 7; b++) {
                double a0 = b * 2 * Math.PI / 7 + t * 2 * Math.PI * 2 / 7;
                for (double rr = 0.9; rr < r - 0.6; rr += 0.08) {
                    double a = a0 + rr * 0.42;
                    q.disc(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr, 0.28 + rr * 0.06, Paint.ramp(IRON, 0.35 + 0.35 * (rr / r) + 0.1 * Math.sin(a * 2)));
                }
            }
            for (double rr = 1.8; rr < r - 0.5; rr += 1.7) {
                q.ring(cx, cy, rr, rr + 0.1, argb(150, 0x000000));
            }
            q.sphere(cx, cy, 0.8, BRASS, 0.3, 1.0);
            q.disc(cx, cy, 0.25, at(IRON, 0.1));
        });
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            p.screw(cx + Math.cos(a) * (r + 0.1) * 1.18, cy + Math.sin(a) * (r + 0.1) * 1.18, 0.34, BRASS, a);
        }
    }

    /** Латунная труба со светящимися окошками: окошки мигают в случайном порядке (у каждой трубы — свой рисунок). */
    static void pipeRun(Paint p, double x, double y0, double y1, long seed) {
        p.slab(x, y0, x + 1.3, y1, BRASS, 2.4, 1);
        Random r = new Random(seed);
        List<Double> ys = new ArrayList<>();
        for (double y = y0 + 1.0 + r.nextDouble(); y < y1 - 2.0; y += 4.2 + r.nextDouble() * 1.4) {
            ys.add(y);
        }
        int salt = (int) (seed % 1000);
        p.animate(x - 0.2, y0, x + 1.5, y1, (q, t) -> {
            for (int i = 0; i < ys.size(); i++) {
                double y = ys.get(i);
                boolean on = CabBack.blink(t, salt * 7 + i, 8);
                q.rect(x + 0.3, y, x + 1.0, y + 1.7, rgb(0x061a20));
                q.rect(x + 0.4, y + 0.12, x + 0.9, y + 1.58, at(CYAN, on ? 4.6 : 1.8));
                if (on) {
                    q.glowRect(x + 0.4, y + 0.12, x + 0.9, y + 1.58, 0.5, at(CYAN, 3.5), 70);
                }
            }
            for (double y = y0 + 3.0; y < y1 - 1.0; y += 7.5) {
                q.slab(x - 0.2, y, x + 1.5, y + 0.6, BRASS, 3.5, 1);
            }
        });
    }

    /**
     * Разъём сети, нарисованный прямо на стенке (сзади и на боковинах): латунный квадрат 10x10 с кольцом, бирюзовой кромкой
     * и тёмным отверстием — как патрубок фасада, только заподлицо.
     */
    static void portAt(Paint p, double cx, double cy) {
        double s = 10.0 / 16.0;
        p.slab(cx - 5, cy - 5, cx + 5, cy + 5, BRASS, 2.8, 1);
        p.disc(cx, cy, 7.4 * s, at(BRASS, 1.1));
        p.sphere(cx, cy, 7.2 * s, BRASS, 0.25, 0.95);
        p.ring(cx, cy, 5.2 * s, 5.5 * s, at(BRASS, 5.0));
        p.disc(cx, cy, 5.0 * s, rgb(0x061419));
        p.ring(cx, cy, 4.3 * s, 4.7 * s, at(CYAN, 4.8));
        p.glowRect(cx - 4.2 * s, cy - 4.2 * s, cx + 4.2 * s, cy + 4.2 * s, 0.6, at(CYAN, 3.2), 45);
        p.disc(cx, cy, 3.6 * s, rgb(0x040a0d));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.screw(cx + Math.cos(a) * 6.3 * s, cy + Math.sin(a) * 6.3 * s, 0.32, BRASS, a);
        }
        for (double[] c : new double[][]{{cx - 4.4, cy - 4.4}, {cx + 4.4, cy - 4.4}, {cx - 4.4, cy + 4.4}, {cx + 4.4, cy + 4.4}}) {
            p.rivet(c[0], c[1], 0.3, BRASS);
        }
    }

    static Paint backElevation() {
        Paint p = new Paint(48, 48, K);
        p.slab(0, 0, 48, 48, IRON, 1.4, 1);
        p.grain(0, 0, 48, 48, 0.06, 1201);
        beamFront(p);
        p.rect(18.5, 4.3, 29.5, 8.7, rgb(0x0a0c10));
        runeTablet(p, 18.5, 4.3, 29.5, 8.7, new int[]{13, 4, 17, 0});
        plinthFront(p);
        // каркас-стойки по краям и между секциями
        for (double x : new double[]{0, 15.8, 31.2, 46.8}) {
            Forge2.beam(p, x, 9, x + 1.2, 45, 2.9, false, (long) (1210 + x));
        }
        // левая секция: большой вентилятор над решёткой, шестерни наверху
        p.gear(8.0, 14.4, 4.4, 14, 0.1, BRASS, 3.0);
        p.gear(12.8, 18.2, 2.2, 9, 0.35, COPPER, 3.0);
        p.gear(3.3, 18.0, 1.7, 8, 0.2, COPPER, 2.8);
        fan(p, 8.0, 31.0, 5.4);
        Terminals.vent(p, 2.8, 40.0, 13.4, 43.4, 3);
        // центр: жалюзи, рунная плита, янтарный экран — выше, внизу разъём сети
        p.slab(17.6, 10.0, 30.4, 18.2, BRASS, 2.6, 1);
        p.rect(18.2, 10.6, 29.8, 17.6, rgb(0x050607));
        for (int i = 0; i < 5; i++) {
            double y = 10.6 + i * (7.0 / 5);
            p.slab(18.2, y + 0.2, 29.8, y + 0.2 + 7.0 / 5 * 0.6, IRON, 3.2, 1);
        }
        runeTablet(p, 18.0, 19.2, 30.0, 24.2, new int[]{0, 12, 16, 8, 3});
        // янтарный экран и ряд ламп
        p.slab(18.0, 25.2, 30.0, 31.6, BRASS, 2.8, 1);
        // янтарный экран «считает»: столбики пляшут, у каждого свой целый темп; лампы под ним перемигиваются
        Random r = new Random(1221);
        double[] base = new double[14], amp = new double[14], ph = new double[14];
        int[] fq = new int[14];
        for (int i = 0; i < 14; i++) {
            base[i] = 1.2 + r.nextDouble() * 2.2;
            amp[i] = 0.5 + r.nextDouble() * 1.3;
            ph[i] = r.nextDouble() * Math.PI * 2;
            fq[i] = 1 + r.nextInt(4);
        }
        p.animate(18.6, 25.8, 29.4, 33.4, (q, t) -> {
            q.rect(18.6, 25.8, 29.4, 31.0, rgb(0x1d1004));
            for (int i = 0; i < 14; i++) {
                double h = Math.max(0.3, Math.min(4.4, base[i] + amp[i] * Math.sin(2 * Math.PI * fq[i] * t + ph[i])));
                double bx = 19.1 + i * 0.72;
                q.rect(bx, 30.5 - h, bx + 0.5, 30.5, at(AMBER, 3.6 + h / 5 * 2));
            }
            q.glowRect(18.8, 26.0, 29.2, 30.8, 0.5, at(AMBER, 3.0), 40);
            for (int i = 0; i < 6; i++) {
                q.disc(19.6 + i * 2.0, 32.8, 0.42, at(AMBER, CabBack.blink(t, 40 + i, 8) ? 5.0 : 2.0));
            }
        });
        portAt(p, 24.0, 39.5);
        // правая секция: три трубы с бирюзовой энергией, манометры, вентиль
        for (double x : new double[]{34.2, 38.4, 42.6}) {
            pipeRun(p, x, 10.0, 44.0, (long) x);
        }
        p.gauge(37.2, 17.0, 1.7, 70, RED);
        p.gauge(41.4, 28.5, 1.5, 125, RED);
        p.disc(36.1, 38.0, 1.6, at(BRASS, 1.2));
        p.ring(36.1, 38.0, 1.2, 1.55, at(BRASS, 4.5));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 4;
            p.line(36.1 - Math.cos(a) * 1.35, 38.0 - Math.sin(a) * 1.35, 36.1 + Math.cos(a) * 1.35, 38.0 + Math.sin(a) * 1.35, 0.2, at(BRASS, 4.0));
        }
        p.sphere(36.1, 38.0, 0.4, BRASS, 0.4, 1.0);
        p.frostLine(33.0, 47.0, 9.2, 0.6, 1222);
        return p;
    }

    // ================================================================================================ боковины (картина 32 x 48)
    /** Боковина слева: u растёт к фасаду, то есть левый край картины — зад машины. */
    static Paint sideLeft() {
        Paint p = new Paint(32, 48, K);
        p.slab(0, 0, 32, 48, IRON, 1.4, 1);
        Forge2.beam(p, 0, 4, 32, 9, 2.9, true, 1301);
        p.slab(1.0, 4.9, 31.0, 8.1, BRONZE, 1.6, 1);
        Forge2.frieze(p, 1.2, 30.8, 4.9, 8.1, 2.1);
        Forge2.beam(p, 0, 45, 32, 48, 2.6, true, 1302);
        // крыло: рама с решёткой и манометрами выше, в задней половине внизу — разъём сети
        Forge2.beam(p, 0, 20, 32, 45, 2.5, true, 1303);
        p.grain(0, 20, 32, 45, 0.04, 1304);
        p.slab(0, 20, 32, 21.7, BRASS, 3.2, 1);
        p.slab(2.0, 23.0, 30.0, 33.0, BRASS, 2.7, 1);
        p.slab(2.7, 23.7, 29.3, 32.3, IRON, 0.8, 1);
        Terminals.vent(p, 4.0, 25.0, 20.0, 31.0, 5);
        p.gauge(23.6, 28.0, 1.6, 70, RED);
        p.gauge(27.4, 28.0, 1.4, 120, RED);
        // задний край: латунная стойка и участок задней стенки над крылом (Z 28..32 справа налево от 0)
        p.slab(0, 9, 4.0, 20, IRON, 1.0, 1);
        p.gear(2.0, 14.0, 1.8, 8, 0.2, BRASS, 3.0);
        portAt(p, 8.0, 39.5);
        pipeRun(p, 20.0, 35.0, 44.4, 1305);
        pipeRun(p, 25.0, 35.0, 44.4, 1307);
        p.rect(14.0, 33.8, 32, 34.3, at(BRASS, 4.2));
        p.frostLine(0, 32, 19.9, 0.7, 1306);
        return p;
    }

    /** Боковина справа: u растёт к зад, левый край картины — фасад. */
    static Paint sideRight() {
        Paint p = new Paint(32, 48, K);
        p.slab(0, 0, 32, 48, IRON, 1.4, 1);
        Forge2.beam(p, 0, 4, 32, 9, 2.9, true, 1311);
        p.slab(1.0, 4.9, 31.0, 8.1, BRONZE, 1.6, 1);
        Forge2.frieze(p, 1.2, 30.8, 4.9, 8.1, 2.1);
        Forge2.beam(p, 0, 45, 32, 48, 2.6, true, 1312);
        Forge2.beam(p, 0, 20, 32, 45, 2.5, true, 1313);
        p.grain(0, 20, 32, 45, 0.04, 1314);
        p.slab(0, 20, 32, 21.7, BRASS, 3.2, 1);
        p.slab(2.0, 23.0, 30.0, 33.0, BRASS, 2.7, 1);
        p.slab(2.7, 23.7, 29.3, 32.3, IRON, 0.8, 1);
        // рунные плиты и трубы выше, в задней половине внизу — разъём сети
        runeTablet(p, 4.0, 24.2, 14.0, 27.8, new int[]{4, 11});
        runeTablet(p, 4.0, 28.4, 14.0, 32.0, new int[]{16, 0});
        for (double x : new double[]{17.0, 21.0, 25.0}) {
            pipeRun(p, x, 23.8, 32.3, (long) x * 3);
        }
        p.slab(28.0, 9, 32, 20, IRON, 1.0, 1);
        p.gear(30.0, 14.0, 1.8, 8, 0.5, COPPER, 3.0);
        portAt(p, 24.0, 39.5);
        pipeRun(p, 6.0, 35.0, 44.4, 1315);
        pipeRun(p, 11.0, 35.0, 44.4, 1317);
        p.rect(0, 33.8, 18.0, 34.3, at(BRASS, 4.2));
        p.frostLine(0, 32, 19.9, 0.7, 1316);
        return p;
    }

    // ================================================================================================ общие плитки
    static Paint brassTile() {
        Paint p = Forge2.brassTile();
        return p;
    }

    static Paint bronzeTile() {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.7, true, 1401);
        p.slab(1.0, 1.0, 15.0, 15.0, BRONZE, 2.2, 1);
        for (double[] c : new double[][]{{2.2, 2.2}, {13.8, 2.2}, {2.2, 13.8}, {13.8, 13.8}}) {
            p.rivet(c[0], c[1], 0.4, BRASS);
        }
        return p;
    }

    static Paint darkTile() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 1.2, 1);
        p.grain(0, 0, 16, 16, 0.07, 1411);
        return p;
    }

    /** Палуба крыльев (верх): латунная плита с рунным кругом и снегом по краю. */
    static Paint deckTile() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.8, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 1421, true);
        p.slab(1.5, 1.5, 14.5, 14.5, IRON, 0.9, 1);
        p.ring(8, 8, 4.2, 4.5, at(VIOLET, 4.5));
        p.glowRect(6, 6, 10, 10, 1.4, at(VIOLET, 3.2), 60);
        for (double[] c : new double[][]{{0.8, 0.8}, {15.2, 0.8}, {0.8, 15.2}, {15.2, 15.2}}) {
            p.rivet(c[0], c[1], 0.35, BRASS);
        }
        p.frostLine(0, 16, 0, 0.8, 1422);
        return p;
    }

    /** Верх балки: бронзовые листы, вентиляционные прорези, иней. */
    static Paint topTile() {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.7, true, 1431);
        p.slab(1.0, 1.0, 15.0, 15.0, BRONZE, 2.1, 1);
        Terminals.vent(p, 4.5, 4.5, 11.5, 11.5, 5);
        for (double[] c : new double[][]{{2.0, 2.0}, {14.0, 2.0}, {2.0, 14.0}, {14.0, 14.0}}) {
            p.screw(c[0], c[1], 0.55, BRASS, c[0]);
        }
        p.frostLine(0, 16, 0, 0.9, 1432);
        p.frostLine(0, 16, 15.2, 0.7, 1433);
        return p;
    }

    /** Наковальня: латунная плита с фиолетовым кольцом рун и тёмным центром под предметом. */
    static Paint anvilTop() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.8, 1);
        p.slab(1.0, 1.0, 15.0, 15.0, IRON, 0.7, 1);
        p.discFn(8, 8, 5.6, (u, v) -> Paint.ramp(VIOLET, 0.08 + 0.15 * (1 - Math.hypot(u - 8, v - 8) / 5.6)));
        p.ring(8, 8, 5.0, 5.4, at(VIOLET, 5.0));
        p.ring(8, 8, 3.2, 3.4, at(VIOLET, 3.5));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.rune(8 + Math.cos(a) * 4.2 - 0.3, 8 + Math.sin(a) * 4.2 - 0.6, 1.2, i * 2 + 1, at(VIOLET, 5.8), 0);
        }
        p.glowRect(6.5, 6.5, 9.5, 9.5, 1.8, at(VIOLET, 3.4), 70);
        for (double[] c : new double[][]{{1.0, 1.0}, {15.0, 1.0}, {1.0, 15.0}, {15.0, 15.0}}) {
            p.rivet(c[0], c[1], 0.4, BRASS);
        }
        // холодная кромка: синеватый налёт по раме и иней, наросший с краёв
        p.tint(0, 0, 16, 1.0, argb(110, 0x9fd4f0));
        p.tint(0, 15.0, 16, 16, argb(110, 0x9fd4f0));
        p.tint(0, 1.0, 1.0, 15.0, argb(110, 0x9fd4f0));
        p.tint(15.0, 1.0, 16, 15.0, argb(110, 0x9fd4f0));
        p.frostLine(0, 16, 0, 0.6, 1461);
        p.frostLine(0, 16, 15.3, 0.45, 1462);
        rime(p, 1471, 0, 0, 16, 16, 9);
        return p;
    }

    /** Иней-«звёздочки»: короткие белые крестики с голубым ореолом, у краёв прямоугольника гуще. */
    static void rime(Paint p, long seed, double x0, double y0, double x1, double y1, int count) {
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            double t = r.nextDouble(), s = 0.35 + r.nextDouble() * 0.45;
            // ближе к кромке: выбираем сторону и прижимаемся к ней
            double x, y;
            switch (r.nextInt(4)) {
                case 0 -> {
                    x = x0 + t * (x1 - x0);
                    y = y0 + 0.4 + r.nextDouble() * 1.6;
                }
                case 1 -> {
                    x = x0 + t * (x1 - x0);
                    y = y1 - 0.4 - r.nextDouble() * 1.6;
                }
                case 2 -> {
                    x = x0 + 0.4 + r.nextDouble() * 1.6;
                    y = y0 + t * (y1 - y0);
                }
                default -> {
                    x = x1 - 0.4 - r.nextDouble() * 1.6;
                    y = y0 + t * (y1 - y0);
                }
            }
            p.line(x - s, y, x + s, y, 0.22, argb(170, 0xE8F8FF));
            p.line(x, y - s, x, y + s, 0.22, argb(170, 0xE8F8FF));
            p.line(x - s * 0.6, y - s * 0.6, x + s * 0.6, y + s * 0.6, 0.15, argb(110, 0xB6E0F2));
            p.line(x - s * 0.6, y + s * 0.6, x + s * 0.6, y - s * 0.6, 0.15, argb(110, 0xB6E0F2));
        }
    }

    /** Стержень пьедестала: тёмное железо, столбец светящихся рун в латунной рамке, бирюзовые жилы и иней. */
    static Paint pedestal() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 1.3, 1);
        p.grain(0, 0, 16, 16, 0.07, 1491);
        p.slab(5.2, 1.2, 10.8, 14.8, BRASS, 2.7, 1);
        p.slab(5.8, 1.8, 10.2, 14.2, IRON, 0.6, 1);
        p.glowRect(6.2, 2.2, 9.8, 13.8, 0.8, at(VIOLET, 3.2), 55);
        for (int i = 0; i < 3; i++) {
            p.rune(7.0, 2.6 + i * 3.8, 3.0, 5 + i * 6, at(VIOLET, 5.6), at(VIOLET, 3.0));
        }
        for (double x : new double[]{2.2, 13.2}) {
            p.rect(x, 0.5, x + 0.6, 15.5, rgb(0x061a20));
            p.rect(x + 0.15, 0.6, x + 0.45, 15.4, at(CYAN, 4.2));
            p.glowRect(x + 0.15, 0.6, x + 0.45, 15.4, 0.4, at(CYAN, 3.4), 60);
        }
        p.frostLine(0, 16, 0, 0.7, 1492);
        rime(p, 1493, 0, 0, 16, 16, 6);
        return p;
    }

    /** Верх основания пьедестала: бронза, кольцо рун вокруг стержня, латунные болты. */
    static Paint pedestalBase() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRONZE, 2.4, 1);
        p.grain(0, 0, 16, 16, 0.05, 1494);
        p.ring(8, 8, 6.4, 6.9, at(BRASS, 4.4));
        p.ring(8, 8, 4.3, 4.6, at(BRASS, 3.4));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5 + 0.3;
            p.rune(8 + Math.cos(a) * 5.45 - 0.35, 8 + Math.sin(a) * 5.45 - 0.6, 1.2, i * 3 + 4, at(VIOLET, 5.6), 0);
        }
        for (double[] c : new double[][]{{1.2, 1.2}, {14.8, 1.2}, {1.2, 14.8}, {14.8, 14.8}}) {
            p.screw(c[0], c[1], 0.5, BRASS, c[0]);
        }
        p.frostLine(0, 16, 0, 0.6, 1495);
        return p;
    }

    /** Крио-трубка: заиндевелое стекло, бирюзовая жила по оси, ледяные кольца. */
    static Paint cryoTube() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double e = Math.abs((u % 4) - 2) / 2;
            int glass = Paint.mix(Paint.ramp(FROST, 0.55), Paint.ramp(CYAN, 0.7), 1 - e);
            return e < 0.3 ? Paint.ramp(CYAN, 0.85) : glass;
        });
        for (double y = 1; y < 16; y += 3.2) {
            p.rect(0, y, 16, y + 0.4, Paint.ramp(FROST, 0.95));
        }
        return p;
    }

    /** Бока наковальни: холодная сталь с синевой, кованые грани, иней по верхней кромке и пятна изморози. */
    static Paint anvilSide() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> Paint.mix(Paint.ramp(IRON, 0.62 - 0.22 * v / 16), Paint.ramp(FROST, 0.1), 0.32));
        p.streaks(0, 0, 16, 16, 0.08, 1481, true);
        p.grain(0, 0, 16, 16, 0.05, 1482);
        // кованые фаски: светлая верхняя кромка, тёмная нижняя, следы молота
        p.rect(0, 0, 16, 0.6, Paint.mix(at(IRON, 5.4), at(FROST, 3.0), 0.5));
        p.rect(0, 15.4, 16, 16, at(IRON, 0.6));
        Random r = new Random(1483);
        for (int i = 0; i < 7; i++) {
            double x = 1 + r.nextDouble() * 14, y = 2 + r.nextDouble() * 12;
            p.ring(x, y, 0.55, 0.8, argb(255, Paint.mix(at(IRON, 2.0), at(FROST, 1.0), 0.3)));
        }
        p.frostLine(0, 16, 0, 0.8, 1484);
        rime(p, 1485, 0, 0, 16, 16, 7);
        return p;
    }

    /** Цоколь наковальни: тёмный сланец, латунный кант сверху и снизу, одна тонкая бирюзовая рунная линия. */
    static Paint satAnvilBase() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 1.1, 1);
        p.grain(0, 0, 16, 16, 0.06, 1851);
        p.rect(0, 0, 16, 1.4, at(BRASS, 3.8));
        p.rect(0, 1.4, 16, 1.9, at(BRASS, 1.6));
        p.rect(0, 14.4, 16, 16, at(BRASS, 2.2));
        p.glowRect(1.0, 7.4, 15.0, 8.4, 0.4, at(CYAN, 4.6), 70);
        for (double x = 2.0; x < 15; x += 3.0) {
            p.rune(x, 6.6, 2.4, (int) x + 3, at(CYAN, 5.8), at(CYAN, 3.0));
        }
        return p;
    }

    /** Талия наковальни: состаренная бронза, латунные обручи и бирюзовый рунный пояс посередине. */
    static Paint satAnvilCasing() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRONZE, 2.2, 1);
        p.streaks(0.4, 0.4, 15.6, 15.6, 0.08, 1861, false);
        p.slab(0, 0, 16, 2.4, BRASS, 3.0, 1);
        p.slab(0, 13.6, 16, 16, BRASS, 2.6, 1);
        p.rect(0, 6.4, 16, 9.6, rgb(0x10161a));
        p.glowRect(0.4, 6.8, 15.6, 9.2, 0.3, at(CYAN, 3.6), 60);
        for (int i = 0; i < 4; i++) {
            p.rune(1.0 + i * 4.0, 6.7, 2.6, i * 5 + 2, at(CYAN, 6.0), at(CYAN, 3.4));
        }
        return p;
    }

    /** Кованая сталь тела, рога и пятки: тёмная, с бликом по верхней кромке и следами ковки. */
    static Paint satAnvilSteel() {
        Paint p = new Paint(16, 16, K);
        // Paint.at — по номеру ступени палитры (0..6); тёмная кованая сталь со светлой верхней кромкой
        p.fill(0, 0, 16, 16, (u, v) -> at(IRON, 1.6 + 1.3 * (1.0 - v / 16.0) + 0.2 * Math.sin(u * 1.3 + v * 0.4)));
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 1881, true);
        p.rect(0, 0, 16, 0.8, at(IRON, 3.4));
        p.rect(0, 15.2, 16, 16, at(IRON, 0.5));
        Random r = new Random(1882);
        for (int i = 0; i < 5; i++) {
            double x = 1.5 + r.nextDouble() * 13, y = 3 + r.nextDouble() * 10;
            p.line(x - 0.9, y, x + 0.9, y + 0.3, 0.16, at(IRON, 3.6));
        }
        return p;
    }
    /** Ударная площадка наковальни: вороненая сталь со следами ковки, скошенными фасками и рунной микрогравировкой. */
    static Paint satAnvilFace() {
        Paint p = new Paint(16, 16, K);
        // Темная кованая сталь с металлическим блеском
        p.slab(0, 0, 16, 16, IRON, 1.6, 1);
        p.streaks(0.5, 0.5, 15.5, 15.5, 0.08, 1871, true);
        p.grain(0, 0, 16, 16, 0.05, 1872);

        // Фаски по периметру
        p.rect(0, 0, 16, 0.8, at(IRON, 4.2));
        p.rect(0, 15.2, 16, 16, at(IRON, 0.6));
        p.rect(0, 0, 0.8, 16, at(IRON, 3.8));
        p.rect(15.2, 0, 16, 16, at(IRON, 0.8));

        // Следы ударов молота и крестообразные насечки
        Random r = new Random(1873);
        for (int i = 0; i < 6; i++) {
            double x = 2.5 + r.nextDouble() * 11, y = 2.5 + r.nextDouble() * 11;
            p.line(x - 1.2, y - 0.8, x + 1.2, y + 0.8, 0.16, at(IRON, 3.4));
            p.line(x - 0.8, y + 1.0, x + 0.8, y - 1.0, 0.14, at(IRON, 0.8));
        }

        // Рунный оккультный узор в центре
        p.glowRect(6.0, 6.0, 10.0, 10.0, 0.5, at(CYAN, 3.2), 45);
        p.ring(8, 8, 2.4, 2.65, at(CYAN, 4.8));
        p.rune(6.8, 6.8, 2.4, 5, at(CYAN, 5.4), at(CYAN, 3.0));

        p.frostLine(0, 16, 0, 0.5, 1874);
        return p;
    }

    /** Полупрозрачный граненый кристаллический лед глубокого сапфирово-лазурного оттенка. */
    static Paint ice() {
        Paint p = new Paint(4, 4, K);
        p.fill(0, 0, 4, 4, (u, v) -> {
            double diag = ((u - 2) + (v - 2)) / 4.0;
            double edge = Math.max(Math.abs(u - 2) / 2.0, Math.abs(v - 2) / 2.0);
            int base = Paint.mix(rgb(0x4AAED4), rgb(0x86E0FA), 0.5 + 0.5 * diag);
            int alpha = (int) (155 + 85 * Math.pow(edge, 1.5));
            return argb(alpha, base & 0xFFFFFF);
        });
        // Кристаллические грани и световые блики
        p.line(0.2, 0.2, 3.8, 3.8, 0.35, argb(230, 0xFFFFFF));
        p.line(0.4, 0.2, 0.4, 3.8, 0.25, argb(200, 0xE8F8FF));
        p.line(3.6, 0.2, 3.6, 3.8, 0.25, argb(180, 0x2878A0));
        p.sparkle(2.0, 2.0, argb(255, 0xFFFFFF));
        return p;
    }

    /** Задняя стенка камеры пресса (16 x 24): тёмное железо, светящийся рунный круг за предметом, трубы по краям. */
    static Paint chamberWall() {
        Paint p = new Paint(16, 24, K);
        p.slab(0, 0, 16, 24, IRON, 1.0, 1);
        p.grain(0, 0, 16, 24, 0.07, 1441);
        // стенка видна с Y 40 (v = 0) вниз: круг рун на уровне предмета (Y ~ 34) — это v = (40 - 34) * 16 / 24 ~ 4 юнита картины
        double cx = 8, cy = 9.0;
        p.glowRect(cx - 3, cy - 3, cx + 3, cy + 3, 4.0, at(VIOLET, 3.0), 90);
        p.ring(cx, cy, 5.2, 5.5, at(VIOLET, 5.2));
        p.ring(cx, cy, 3.6, 3.75, at(VIOLET, 4.0));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            p.rune(cx + Math.cos(a) * 4.45 - 0.3, cy + Math.sin(a) * 4.45 - 0.55, 1.1, i * 3 + 2, at(VIOLET, 6.0), 0);
        }
        for (int i = 0; i < 5; i++) {
            double a = i * 2 * Math.PI / 5 + 0.3;
            p.line(cx, cy, cx + Math.cos(a) * 3.4, cy + Math.sin(a) * 3.4, 0.12, at(VIOLET, 3.8));
        }
        for (double x : new double[]{0.5, 14.3}) {
            pipeRun(p, x, 0, 24, (long) (x * 10) + 7);
        }
        return p;
    }

    /** Стенка над крыльями (16 x 16) — видна сквозь стеклянные колонны: шестерни и трубка. */
    static Paint wallTile() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 1.0, 1);
        p.grain(0, 0, 16, 16, 0.07, 1451);
        p.gear(5.0, 8.0, 3.8, 12, 0.2, BRASS, 2.8);
        p.gear(11.6, 5.0, 2.4, 9, 0.35, COPPER, 2.8);
        p.gear(12.6, 12.0, 1.7, 8, 0.1, BRASS, 2.6);
        p.slab(0, 14.0, 16, 15.0, BRASS, 2.4, 1);
        return p;
    }

    /** Патрубок для трубы сети: латунный квадрат, кольцо, бирюзовая кромка и тёмное отверстие. 10x10 юнитов на текстуре 16x16. */
    static Paint port() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.8, 1);
        p.disc(8, 8, 7.4, at(BRASS, 1.1));
        p.sphere(8, 8, 7.2, BRASS, 0.25, 0.95);
        p.ring(8, 8, 5.2, 5.5, at(BRASS, 5.0));
        p.disc(8, 8, 5.0, rgb(0x061419));
        p.ring(8, 8, 4.3, 4.7, at(CYAN, 4.8));
        p.glowRect(4.2, 4.2, 11.8, 11.8, 0.9, at(CYAN, 3.2), 45);
        p.disc(8, 8, 3.6, rgb(0x040a0d));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.screw(8 + Math.cos(a) * 6.3, 8 + Math.sin(a) * 6.3, 0.5, BRASS, a);
        }
        for (double[] c : new double[][]{{0.9, 0.9}, {15.1, 0.9}, {0.9, 15.1}, {15.1, 15.1}}) {
            p.rivet(c[0], c[1], 0.45, BRASS);
        }
        return p;
    }

    static Paint glass() {
        Paint p = new Paint(16, 16, K);
        p.tint(0, 0, 16, 16, argb(78, 0x8fe8f0));
        for (double off : new double[]{0.0, 5.0, 11.0}) {
            p.line(1.0 + off * 0.5, 5.0 + off * 0.5, 6.0 + off * 0.9, 1.0 + off * 0.2, 0.3, argb(80, 0xFFFFFF));
        }
        p.rect(0, 0, 16, 0.25, argb(120, 0xE0F4FF));
        p.rect(0, 15.75, 16, 16, argb(120, 0xE0F4FF));
        p.rect(0, 0, 0.25, 16, argb(120, 0xE0F4FF));
        p.rect(15.75, 0, 16, 16, argb(120, 0xE0F4FF));
        return p;
    }

    static Paint crystal() {
        Paint p = new Paint(16, 16, K);
        double[][][] tri = {{{0, 0}, {16, 0}, {8, 8}}, {{0, 0}, {8, 8}, {0, 16}}, {{16, 0}, {16, 16}, {8, 8}}, {{0, 16}, {16, 16}, {8, 8}}};
        double[] tone = {0.74, 0.56, 0.4, 0.26};
        for (int i = 0; i < 4; i++) {
            final double t = tone[i];
            p.polyFn(new double[]{tri[i][0][0], tri[i][1][0], tri[i][2][0]}, new double[]{tri[i][0][1], tri[i][1][1], tri[i][2][1]},
                    (u, v) -> argb(255, Paint.ramp(VIOLET, t - 0.08 * Math.hypot(u - 8, v - 8) / 8) & 0xFFFFFF));
        }
        p.line(0, 0, 8, 8, 0.35, argb(200, 0xE8DCFF));
        p.line(16, 0, 8, 8, 0.25, argb(110, 0xE8DCFF));
        return p;
    }

    // ================================================================================================ динамические текстуры
    /** Жидкость колонн (рисует рендерер): сплошная бирюза с мягкими бесшовными переливами, без пикселей-искр. */
    static Paint liquid() {
        Paint p = new Paint(16, 16, K);
        double t = 2 * Math.PI / 16;
        // только целые гармоники периода 16 — текстура плитками не «шьётся» в квадраты
        p.fill(0, 0, 16, 16, (u, v) -> Paint.ramp(CYAN, 0.5 + 0.045 * Math.sin(u * t + v * t) + 0.035 * Math.cos(2 * v * t - u * t)
                + 0.02 * Math.sin(3 * u * t)));
        return p;
    }

    /** Латунь пресса (плита и шток): гладкая с зерном и заклёпками по краю. */
    static Paint press() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 1511, false);
        p.grain(0, 0, 16, 16, 0.04, 1512);
        p.rect(3.0, 0, 5.0, 16, at(BRASS, 4.4));
        p.rect(11.0, 0, 11.4, 16, at(BRASS, 1.4));
        for (double y = 2; y < 16; y += 4) {
            p.rivet(8, y, 0.45, BRASS);
        }
        return p;
    }

    // ================================================================================================ модель
    /** Рельефная рамка на фасаде: четыре латунных бруска толщиной th по периметру прямоугольника (в картине: x0..x1, y0..y1, y вниз). */
    static void frameRect(Big3 m, Big3.Face brass, double x0, double y0, double x1, double y1, double th, double z1) {
        double yt = 48 - y1, yb = 48 - y0;
        m.box(x0, yb - th, 0, x1, yb, z1).all(brass);
        m.box(x0, yt, 0, x1, yt + th, z1).all(brass);
        m.box(x0, yt + th, 0, x0 + th, yb - th, z1).all(brass);
        m.box(x1 - th, yt + th, 0, x1, yb - th, z1).all(brass);
    }

    /** Выступ на фасаде по прямоугольнику картины (y вниз): лицо — та же картина, бока — латунь. */
    static void raised(Big3 m, Big3.Face brass, Big3.Face face, double x0, double y0, double x1, double y1, double zFront) {
        m.box(x0, 48 - y1, zFront, x1, 48 - y0, 1).front(face).left(brass).right(brass).top(brass).bottom(brass);
    }

    static Big3 geometry() {
        Big3 m = new Big3(3, 3, 2);
        Big3.Face front = Big3.Face.colRow("sat_f"), back = Big3.Face.colRow("sat_b"), left = Big3.Face.depRow("sat_l"),
                right = Big3.Face.depRow("sat_r");
        Big3.Face bronze = Big3.Face.of("sat_bronze"), brass = Big3.Face.of("sat_brass"), dark = Big3.Face.of("sat_dark"),
                deck = Big3.Face.of("sat_deck"), top = Big3.Face.of("sat_top"), glass = Big3.Face.of("sat_glass"),
                wall = Big3.Face.of("sat_wall"), crystalF = Big3.Face.of("sat_crystal").glow(15);
        Big3.Face anvil = Big3.Face.map("sat_anvil", (x, y, z) -> new double[]{(31 - x) * 16 / 14, (z - 2) * 16 / 12}).glow(11);
        Big3.Face chamber = Big3.Face.map("sat_chamber", (x, y, z) -> new double[]{x - 16, (40 - y) * 16 / 24});
        Big3.Face port = Big3.Face.map("sat_port", (x, y, z) -> new double[]{(x - 20.8) * 2.5, (11.2 - y) * 2.5});
        Big3.Face litFront = front.glow(12);

        // цоколь выступает вперёд ступенькой; корпус крыльев, ящика и балки отодвинут на 1 юнит под рельеф.
        // Снизу под центральным ящиком — разъём сети (цоколь разрезан, чтобы у него было своё дно)
        Big3.Face portBottom = Big3.Face.map("sat_port", (x, y, z) -> new double[]{(29 - x) * 1.6, (z - 3) * 1.6});
        m.box(0, 0, 0, 19, 3, 32).front(front).back(back).left(left).top(deck).bottom(dark);
        m.box(29, 0, 0, 48, 3, 32).front(front).back(back).right(right).top(deck).bottom(dark);
        m.box(19, 0, 0, 29, 3, 3).front(front).top(deck).bottom(dark);
        m.box(19, 0, 3, 29, 3, 13).top(deck).bottom(portBottom);
        m.box(19, 0, 13, 29, 3, 32).back(back).top(deck).bottom(dark);
        m.box(0, 3, 1, 16, 28, 32).front(front).back(back).left(left).right(bronze).top(deck);
        m.box(32, 3, 1, 48, 28, 32).front(front).back(back).left(bronze).right(right).top(deck);
        // центральный ящик (подключается труба сети), глухая задняя половина колонны
        m.box(16, 3, 1, 32, 16, 16).front(front).top(deck);
        m.box(16, 3, 16, 32, 16, 32).back(back);
        m.box(16, 16, 16, 32, 39, 32).back(back).front(chamber);
        // пьедестал: основание, стержень (пресс стоит на стержне Края), наковальня
        // пьедестал: бронзовое основание с кольцом рун, тёмный стержень с рунным столбцом, латунные обоймы и четыре
        // крио-трубки по углам (светящаяся бирюзовая жила в инее)
        Big3.Face baseTop = Big3.Face.map("sat_pedestal_base", (x, y, z) -> new double[]{(x - 19) * 1.6, (z - 3) * 1.6}).glow(9);
        Big3.Face stemX = Big3.Face.map("sat_pedestal", (x, y, z) -> new double[]{(x - 21) * 16 / 6, (27 - y) * 16 / 9});
        Big3.Face stemZ = Big3.Face.map("sat_pedestal", (x, y, z) -> new double[]{(z - 5) * 16 / 6, (27 - y) * 16 / 9});
        Big3.Face tube = Big3.Face.of("sat_cryo_tube").glow(10);
        m.box(19, 16, 3, 29, 18, 13).all(bronze).top(baseTop);
        m.box(21, 18, 5, 27, 27, 11).front(stemX).back(stemX).left(stemZ).right(stemZ);
        for (double y : new double[]{18, 26.2}) {
            m.box(20.6, y, 4.6, 27.4, y + 0.8, 11.4).all(brass);
        }
        for (double[] c : new double[][]{{20.2, 4.2}, {27.0, 4.2}, {20.2, 11.0}, {27.0, 11.0}}) {
            m.box(c[0], 18.8, c[1], c[0] + 0.8, 26.2, c[1] + 0.8).sides(tube);
        }
        // наковальня: опора, шейка, рабочая плита с рогом и пяткой; холодная синеватая сталь, иней и сосульки по переднему краю
        Big3.Face anvilSide = Big3.Face.of("sat_anvil_side"), ice = Big3.Face.of("sat_ice");
        m.box(18.5, 27, 3, 29.5, 27.7, 13).all(anvilSide);
        m.box(20.5, 27.7, 4.5, 27.5, 28.4, 11.5).all(anvilSide);
        m.box(17.6, 28.0, 6, 20.5, 28.4, 10).all(anvilSide);
        m.box(27.5, 28.0, 5, 30.4, 28.4, 11).all(anvilSide);
        m.box(17, 28.4, 2, 31, 29.5, 14).all(anvilSide).top(anvil);
        for (double[] ic : new double[][]{{18.3, 0.9}, {21.4, 0.55}, {24.6, 1.1}, {28.2, 0.7}}) {
            m.box(ic[0], 28.4 - ic[1], 2.15, ic[0] + 0.45, 28.4, 2.6).all(ice);
        }
        // стойки камеры пресса
        for (double x : new double[]{17, 29}) {
            for (double z : new double[]{2, 12}) {
                m.box(x, 29.5, z, x + 2, 39, z + 2).all(brass);
            }
        }
        // стенки над крыльями (видны сквозь колонны)
        m.box(0, 28, 28, 16, 39, 32).front(wall).back(back).left(left).right(bronze);
        m.box(32, 28, 28, 48, 39, 32).front(wall).back(back).left(bronze).right(right);
        // стеклянные колонны Source (жидкость рисует рендерер): стекло, три пояса и угловые стяжки
        for (double x : new double[]{3, 35}) {
            m.box(x, 28, 3, x + 10, 39, 13).sides(glass);
            m.box(x - 0.6, 28, 2.4, x + 10.6, 29.4, 13.6).all(brass);
            m.box(x - 0.6, 33.2, 2.4, x + 10.6, 34.4, 13.6).all(brass);
            m.box(x - 0.6, 37.8, 2.4, x + 10.6, 39, 13.6).all(brass);
            for (double cx : new double[]{x - 0.4, x + 9.6}) {
                for (double cz : new double[]{2.6, 12.4}) {
                    m.box(cx, 29.4, cz, cx + 0.8, 37.8, cz + 0.8).all(brass);
                }
            }
        }
        // верхняя балка
        m.box(0, 39, 1, 48, 44, 32).front(front).back(back).left(left).right(right).top(top).bottom(bronze);
        // рельеф фасада: рамы рунных панелей, светящиеся вставки, патрубок, окно, манометры, рунная табличка
        for (double x0 : new double[]{0, 32}) {
            frameRect(m, brass, x0 + 2.0, 23.0, x0 + 14.0, 39.6, 0.7, 1);
            raised(m, brass, litFront, x0 + 2.7, 23.7, x0 + 13.3, 38.9, 0.3);
            frameRect(m, brass, x0 + 2.0, 40.6, x0 + 14.0, 44.4, 0.5, 1);
        }
        // патрубок сети: небольшой (6.4x6.4) и неглубокий, не наезжает на окно с големом и манометры ящика
        m.box(20.8, 4.8, 0.4, 27.2, 11.2, 1).front(port).left(brass).right(brass).top(brass).bottom(brass);
        raised(m, brass, front, 16.9, 35.2, 20.7, 43.0, 0.2);
        for (double[] g : new double[][]{{7.2, 13.0}, {35.0, 40.8}}) {
            raised(m, brass, front, g[0], 4.2, g[1], 8.8, 0.2);
        }
        raised(m, brass, litFront, 18.5, 4.3, 29.5, 8.7, 0.2);
        // кристалл на балке: латунное основание и ступенчатый самоцвет
        m.box(19.5, 44, 3.5, 28.5, 45, 12.5).all(brass);
        m.box(21, 45, 5, 27, 46.5, 11).all(crystalF).rot("y", 45, 24, 45, 8);
        m.box(22.2, 46.5, 6.2, 25.8, 47.5, 9.8).all(crystalF).rot("y", 45, 24, 46.5, 8);
        m.box(23, 47.5, 7, 25, 48, 9).all(crystalF).rot("y", 45, 24, 47.5, 8);
        return m;
    }
    static String modelJson(Big3.Cell cell, String display) {
        StringBuilder sb = new StringBuilder("{\n  \"parent\": \"minecraft:block/block\",\n  \"ambientocclusion\": false,\n");
        sb.append("  \"render_type\": \"minecraft:translucent\",\n");
        sb.append(display);
        sb.append("  \"textures\": {\n    \"particle\": \"").append(Forge2.NS).append("sat_bronze\"");
        for (String v : cell.vars) {
            sb.append(",\n    \"").append(v).append("\": \"").append(Forge2.NS).append(v).append("\"");
        }
        sb.append("\n  },\n  \"elements\": [\n").append(String.join(",\n", cell.elements)).append("\n  ]\n}\n");
        return sb.toString();
    }

    static String cellName(int col, int row, int dep) {
        return "saturator_c" + col + row + dep;
    }

    static final String[] ROT_Y = {"", ", \"y\": 90", ", \"y\": 180", ", \"y\": 270"};
    static final String[] FACING = {"north", "east", "south", "west"};

    /**
     * Насыщатель до сборки (и предмет) — магическая наковальня: сланцевый цоколь с рунной линией, расставленное стальное
     * основание, узкая бронзовая талия с бирюзовым рунным поясом, широкое плоское тело с ударной площадкой, длинный рог на
     * запад и пятка на восток. На площадке куют заготовки вручную (см. ManaSaturatorBlockEntity).
     */
    static void pressModel() throws Exception {
        List<Models.El> l = new ArrayList<>();
        // цоколь и расставленное основание
        l.add(Models.e(1.0, 0.0, 2.5, 15.0, 1.6, 13.5).all("#anvil_base").glow(9));
        l.add(Models.e(2.0, 1.6, 3.5, 14.0, 3.4, 12.5).all("#anvil_steel"));
        l.add(Models.e(3.4, 3.4, 4.5, 12.6, 4.8, 11.5).all("#anvil_steel"));
        // узкая талия с рунным поясом и латунные стяжки по углам
        Models.El waist = Models.e(5.0, 4.8, 5.5, 11.0, 9.6, 10.5).all("#anvil_steel").glow(8);
        for (String side : new String[]{"north", "south", "east", "west"}) {
            waist.face(side, "#anvil_casing", 0, 0, 16, 16);
        }
        l.add(waist);
        // расширение под тело
        l.add(Models.e(3.6, 9.6, 4.5, 12.4, 10.8, 11.5).all("#anvil_steel"));
        // тело наковальни и ударная площадка
        l.add(Models.e(2.4, 10.8, 3.6, 13.0, 14.3, 12.4).all("#anvil_steel"));
        l.add(Models.e(2.6, 14.3, 3.9, 12.8, 14.9, 12.1).all("#anvil_steel").face("up", "#anvil_face", 0, 0, 16, 16).glow(5));
        // рог: сужается к западу
        l.add(Models.e(1.0, 11.6, 5.0, 2.4, 14.3, 11.0).all("#anvil_steel"));
        l.add(Models.e(0.2, 12.6, 6.4, 1.0, 14.1, 9.6).all("#anvil_steel"));
        // пятка с квадратным отверстием наверху
        l.add(Models.e(13.0, 12.2, 4.6, 15.4, 14.3, 11.4).all("#anvil_steel"));
        l.add(Models.e(13.6, 14.3, 7.0, 14.6, 14.32, 9.0).only("#anvil_base", "up"));
        Models.model("mana_saturator_press", false, "sat_anvil_steel",
                new String[][]{{"anvil_base", "sat_anvil_base"}, {"anvil_casing", "sat_anvil_casing"},
                        {"anvil_face", "sat_anvil_face"}, {"anvil_steel", "sat_anvil_steel"}}, l.toArray(new Models.El[0]));
        Models.item("mana_saturator", "mana_saturator_press");
        Forge2.addDisplay("mana_saturator_press", 135, 1.0);
    }
    /** Решётки крыши, на которые ставятся Сопроцессоры Охлаждения: верхний ряд, кроме хозяина (1, 2, 0). */
    static boolean isVent(int col, int row, int dep) {
        return row == 2 && !(col == 1 && dep == 0);
    }

    static void models() throws Exception {
        Big3 m = geometry();
        m.lint("mana_saturator");
        // мультипарт: клетка по (facing, cell) и, на решётках крыши, охладитель при cooler=true
        List<String> parts = new ArrayList<>();
        for (int dep = 0; dep < 2; dep++) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    int index = col + 3 * row + 9 * dep;
                    Big3.Cell cell = m.slice(col, row, dep);
                    String name = cellName(col, row, dep);
                    Forge2.write(Forge2.RES.resolve("models/block/" + name + ".json"), modelJson(cell, ""));
                    for (int f = 0; f < 4; f++) {
                        parts.add("    {\"when\": {\"facing\": \"" + FACING[f] + "\", \"cell\": \"" + index + "\"}, \"apply\": {\"model\": \""
                                + Forge2.NS + name + "\"" + ROT_Y[f] + "}}");
                        if (isVent(col, row, dep)) {
                            parts.add("    {\"when\": {\"facing\": \"" + FACING[f] + "\", \"cell\": \"" + index + "\", \"cooler\": \"true\"}, "
                                    + "\"apply\": {\"model\": \"" + Forge2.NS + "saturator_cooler\"" + ROT_Y[f] + "}}");
                        }
                    }
                }
            }
        }
        Forge2.write(Forge2.RES.resolve("blockstates/saturator_part.json"),
                "{\n  \"multipart\": [\n" + String.join(",\n", parts) + "\n  ]\n}\n");
        // хозяин: до сборки — компактный пресс, после — клетка (1, 2, 0)
        StringBuilder ctl = new StringBuilder("{\n  \"variants\": {\n");
        for (int f = 0; f < 4; f++) {
            ctl.append("    \"facing=").append(FACING[f]).append(",formed=false\": {\"model\": \"").append(Forge2.NS).append("mana_saturator_press\"").append(ROT_Y[f]).append("},\n");
            ctl.append("    \"facing=").append(FACING[f]).append(",formed=true\": {\"model\": \"").append(Forge2.NS).append(cellName(1, 2, 0)).append("\"")
                    .append(ROT_Y[f]).append("}").append(f < 3 ? ",\n" : "\n");
        }
        Forge2.write(Forge2.RES.resolve("blockstates/mana_saturator.json"), ctl.append("  }\n}\n").toString());
        pressModel();
        java.nio.file.Files.deleteIfExists(Forge2.RES.resolve("models/block/mana_saturator.json"));
    }

    // ================================================================================================ запись
    static void textures() throws Exception {
        Paint front = frontElevation();
        Paint back = backElevation();
        Paint left = sideLeft(), right = sideRight();
        front.save(Forge2.PREVIEW.resolve("sat_front.png").toFile());
        back.save(Forge2.PREVIEW.resolve("sat_back.png").toFile());
        left.save(Forge2.PREVIEW.resolve("sat_left.png").toFile());
        right.save(Forge2.PREVIEW.resolve("sat_right.png").toFile());
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                int y0 = 16 * (2 - r);
                if (c != 1 || r != 1) {
                    front.crop(16 * c, y0, 16, 16).save(Forge2.tex("sat_f_" + c + r));   // клетка (1, 1) спереди открыта (пьедестал)
                }
                back.crop(32 - 16 * c, y0, 16, 16).save(Forge2.tex("sat_b_" + c + r));
            }
        }
        for (int d = 0; d < 2; d++) {
            for (int r = 0; r < 3; r++) {
                int y0 = 16 * (2 - r);
                left.crop(d == 0 ? 16 : 0, y0, 16, 16).save(Forge2.tex("sat_l_" + d + r));
                right.crop(16 * d, y0, 16, 16).save(Forge2.tex("sat_r_" + d + r));
            }
        }
        brassTile().save(Forge2.tex("sat_brass"));
        bronzeTile().save(Forge2.tex("sat_bronze"));
        darkTile().save(Forge2.tex("sat_dark"));
        deckTile().save(Forge2.tex("sat_deck"));
        topTile().save(Forge2.tex("sat_top"));
        anvilTop().save(Forge2.tex("sat_anvil"));
        anvilSide().save(Forge2.tex("sat_anvil_side"));
        satAnvilBase().save(Forge2.tex("sat_anvil_base"));
        satAnvilCasing().save(Forge2.tex("sat_anvil_casing"));
        satAnvilFace().save(Forge2.tex("sat_anvil_face"));
        satAnvilSteel().save(Forge2.tex("sat_anvil_steel"));
        ice().save(Forge2.tex("sat_ice"));
        pedestal().save(Forge2.tex("sat_pedestal"));
        pedestalBase().save(Forge2.tex("sat_pedestal_base"));
        cryoTube().save(Forge2.tex("sat_cryo_tube"));
        chamberWall().save(Forge2.tex("sat_chamber"));
        wallTile().save(Forge2.tex("sat_wall"));
        glass().save(Forge2.tex("sat_glass"));
        port().save(Forge2.tex("sat_port"));
        crystal().save(Forge2.tex("sat_crystal"));
        liquid().save(Forge2.entityTex("saturator_liquid"));
        press().save(Forge2.entityTex("saturator_press"));
        for (String old : new String[]{"saturator_stone", "saturator_liquid", "sat_f_11"}) {
            java.nio.file.Files.deleteIfExists(Forge2.tex(old).toPath());
        }
    }

    static void all() throws Exception {
        textures();
        models();
    }
}
