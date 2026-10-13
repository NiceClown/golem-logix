import java.util.Random;

/**
 * Спинка шкафа — одна сплошная картина 32x32 юнита (а не четыре плитки), как её видит человек, стоящий сзади. Каркас повторяет
 * фасад (балка сверху, цоколь, стойки на тех же местах, но зеркально), а три «отсека» между стойками несут разные машины:
 * слева — крио-катушка (бирюзовая спираль в стеклянной колбе), в середине — янтарные экраны, вентилятор, жалюзи и рунная табличка,
 * справа — связка труб с манометром и вентилем. Светящиеся места дублируются рельефом (см. {@link #relief}).
 * Картина режется на четыре текстуры по клеткам: нижняя левая клетка модели (взгляд спереди) — это правая нижняя четверть картины.
 */
final class CabBack {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER;
    static final int[] AMBER = Terminals.AMBER;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    // отсеки между стойками (в координатах картины, взгляд сзади)
    static final double AX0 = 2.5, AX1 = 6.5, BX0 = 8.5, BX1 = 24.5, CX0 = 26.5, CX1 = 29.5, FY0 = 4, FY1 = 28;

    static void field(Paint p, double x0, double x1, long seed) {
        p.slab(x0, FY0, x1, FY1, IRON, 1.25, 1);
        p.grain(x0, FY0, x1, FY1, 0.07, seed);
        // «утопленность»: тёмная тень сверху и слева
        p.rect(x0, FY0, x1, FY0 + 0.4, argb(255, 0x07090c));
        p.rect(x0, FY0, x0 + 0.3, FY1, argb(255, 0x07090c));
    }

    static void post(Paint p, double x0, double x1, long seed) {
        Forge2.beam(p, x0, 4, x1, 28, 2.9, false, seed);
        for (double by : new double[]{7.5, 24.0}) {
            p.slab(x0, by, x1, by + 1.3, BRASS, 3.3, 1);
            p.rivet(x0 + 0.55, by + 0.65, 0.2, BRASS);
            p.rivet(x1 - 0.55, by + 0.65, 0.2, BRASS);
        }
        p.rect(x0, 4, x0 + 0.12, 28, at(BRASS, 4.2));
        p.rect(x1 - 0.12, 4, x1, 28, at(BRASS, 4.2));
    }

    static void frame(Paint p) {
        // верхняя балка с фризом, угловыми блоками и рунной табличкой по центру
        Forge2.beam(p, 0, 0, 32, 4, 2.9, true, 901);
        p.slab(3.5, 0.8, 28.5, 3.2, BRONZE, 1.6, 1);
        Forge2.frieze(p, 3.8, 28.2, 0.8, 3.2, 1.9);
        Forge2.brassBlock(p, 0, 0, 3.5, 4, 902);
        Forge2.brassBlock(p, 28.5, 0, 32, 4, 903);
        p.slab(12.2, 0.3, 19.8, 3.7, BRASS, 3.0, 1);
        p.slab(13.0, 0.9, 19.0, 3.1, IRON, 1.0, 1);
        p.glowRect(13.4, 1.0, 18.6, 3.0, 0.7, at(VIOLET, 3.5), 60);
        p.rune(14.0, 1.2, 1.7, 1, at(VIOLET, 5.2), at(VIOLET, 3.0));
        p.rune(16.6, 1.2, 1.7, 8, at(VIOLET, 5.2), at(VIOLET, 3.0));
        for (double[] c : new double[][]{{12.7, 0.75}, {19.3, 0.75}, {12.7, 3.25}, {19.3, 3.25}}) {
            p.rivet(c[0], c[1], 0.2, BRASS);
        }
        // цоколь
        Forge2.beam(p, 0, 28, 32, 32, 2.6, true, 911);
        p.slab(3.5, 28.9, 28.5, 31.1, BRONZE, 1.5, 1);
        Forge2.frieze(p, 3.8, 28.2, 28.9, 31.1, 1.9);
        Forge2.brassBlock(p, 0, 28, 3.5, 32, 912);
        Forge2.brassBlock(p, 28.5, 28, 32, 32, 913);
        p.slab(13.6, 28.3, 18.4, 31.7, BRASS, 3.0, 1);
        p.slab(14.2, 28.9, 17.8, 31.1, IRON, 1.0, 1);
        p.glowRect(14.4, 29.0, 17.6, 31.0, 0.6, at(VIOLET, 3.5), 55);
        p.rune(15.2, 29.1, 1.9, 19, at(VIOLET, 5.2), at(VIOLET, 3.0));
        // стойки: крайние и внутренние
        post(p, 0, 2.5, 921);
        post(p, 29.5, 32, 922);
        post(p, 6.5, 8.5, 923);
        post(p, 24.5, 26.5, 924);
    }

    // ----------------------------------------------------------------------------------------------- отсек A: крио-катушка
    static void coil(Paint p) {
        field(p, AX0, AX1, 931);
        p.slab(2.85, 4.9, 6.15, 27.1, BRASS, 2.6, 1);
        p.rect(3.35, 5.4, 5.65, 26.6, rgb(0x04141a));
        // живая катушка: двойная медная спираль вращается (бежит вверх), жидкость переливается; бандажи и иней — поверх
        p.animate(2.85, 4.9, 6.15, 27.1, (q, t) -> {
            q.fill(3.6, 5.6, 5.4, 26.4, (u, v) -> {
                double e = Math.abs(u - 4.5) / 0.9;
                return Paint.ramp(CYAN, 0.16 + 0.22 * (1 - e) + 0.06 * Math.sin(v * 2.4 - t * 2 * Math.PI));
            });
            for (double y = 5.7; y < 26.3; y += 0.05) {
                double a = (y - 5.7) * 1.7 + t * 2 * Math.PI;
                for (int s = 0; s < 2; s++) {
                    double ph = a + s * Math.PI, x = 4.5 + 0.72 * Math.sin(ph), depth = Math.cos(ph);
                    q.disc(x, y, 0.2, depth > 0 ? Paint.ramp(COPPER, 0.55 + 0.3 * depth) : Paint.ramp(COPPER, 0.18 + 0.15 * (1 + depth)));
                    if (depth > 0.55) {
                        q.disc(x - 0.04, y - 0.05, 0.08, at(CYAN, 6));
                    }
                }
            }
            q.rect(3.7, 5.8, 3.9, 26.2, argb(110, 0xE2FFFE));
            for (double y : new double[]{6.1, 11.3, 16.5, 21.7, 26.4}) {
                q.slab(2.9, y - 0.45, 6.1, y + 0.45, BRASS, 3.3, 1);
            }
            q.frostLine(2.9, 6.1, 26.6, 0.55, 932);
            q.icicle(3.4, 21.7, 0.9, 0.4);
            q.icicle(5.4, 21.7, 1.2, 0.4);
        });
    }

    /** Огонёк i «в случайном порядке»: цикл делится на slots шагов, в каждом огонёк горит или нет по своему рисунку. */
    static boolean blink(double t, int i, int slots) {
        int s = (int) Math.floor(t * slots) % slots;
        long h = (i * 0x9E3779B97F4A7C15L) ^ (s * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 7) < 5;
    }

    /** Бегущая волна по светящимся вставкам трубы: яркость вставки i в доле цикла t (волна идёт вдоль трубы). */
    static double wave(double t, int i, int n) {
        return 0.5 + 0.5 * Math.cos(2 * Math.PI * (t - (double) i / n));
    }

    // ----------------------------------------------------------------------------------------------- отсек C: трубы
    static void pipes(Paint p) {
        field(p, CX0, CX1, 941);
        for (double x : new double[]{26.95, 28.15}) {
            p.slab(x, 4.4, x + 0.95, 27.6, BRASS, 2.4, 1);
            // бирюзовые «импульсы энергии» в прорезях: волна бежит по трубе сверху вниз (во второй трубе — со сдвигом)
            final double px0 = x;
            final int shift = x > 27.5 ? 3 : 0;
            p.animate(x, 4.4, x + 0.95, 27.6, (q, t) -> {
                int i = 0;
                for (double y = 5.0; y < 27; y += 3.4, i++) {
                    double b = wave(t, i + shift, 7);
                    q.rect(px0 + 0.2, y, px0 + 0.75, y + 1.4, rgb(0x061a20));
                    q.rect(px0 + 0.28, y + 0.1, px0 + 0.67, y + 1.3, at(CYAN, 2.0 + 3.6 * b));
                    q.glowRect(px0 + 0.28, y + 0.1, px0 + 0.67, y + 1.3, 0.4, at(CYAN, 3.5), (int) (20 + 70 * b));
                }
                for (double y : new double[]{9.0, 15.8, 22.6}) {
                    q.slab(px0 - 0.12, y, px0 + 1.07, y + 0.5, BRASS, 3.5, 1);
                }
            });
        }
        p.gauge(28.0, 13.0, 1.15, 125, argb(255, 0xb02020));
        p.gauge(28.0, 19.2, 0.95, 60, argb(255, 0xb02020));
        // вентиль: колесо со спицами медленно проворачивается
        p.animate(26.8, 23.4, 29.2, 25.8, (q, t) -> {
            q.disc(28.0, 24.6, 1.15, at(BRASS, 1.2));
            q.ring(28.0, 24.6, 0.85, 1.12, at(BRASS, 4.5));
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 4 + t * Math.PI / 2;
                q.line(28.0 - Math.cos(a) * 0.95, 24.6 - Math.sin(a) * 0.95, 28.0 + Math.cos(a) * 0.95, 24.6 + Math.sin(a) * 0.95, 0.16, at(BRASS, 4.0));
            }
            q.sphere(28.0, 24.6, 0.3, BRASS, 0.4, 1.0);
        });
        p.frostLine(CX0, CX1, 26.9, 0.6, 942);
    }

    // ----------------------------------------------------------------------------------------------- отсек B: экраны, вентиляция, руны
    static void screenBezel(Paint p, double x0, double y0, double x1, double y1) {
        p.slab(x0, y0, x1, y1, BRASS, 2.8, 1);
        p.rect(x0 + 0.5, y0 + 0.5, x1 - 0.5, y1 - 0.5, rgb(0x1d1004));
        for (double[] c : new double[][]{{x0 + 0.25, y0 + 0.25}, {x1 - 0.25, y0 + 0.25}, {x0 + 0.25, y1 - 0.25}, {x1 - 0.25, y1 - 0.25}}) {
            p.rivet(c[0], c[1], 0.14, BRASS);
        }
    }

    static void screens(Paint p) {
        // экран 1: гистограмма
        double x0 = 9.0, y0 = 4.6, x1 = 16.0, y1 = 11.2;
        screenBezel(p, x0, y0, x1, y1);
        Random r = new Random(951);
        double[] base = new double[11], amp = new double[11], ph = new double[11];
        int[] freq = new int[11];
        for (int i = 0; i < 11; i++) {
            base[i] = 1.4 + r.nextDouble() * 2.0;
            amp[i] = 0.6 + r.nextDouble() * 1.2;
            ph[i] = r.nextDouble() * Math.PI * 2;
            freq[i] = 1 + r.nextInt(3);
        }
        // живая гистограмма: столбики «дышат», у каждого свой целый темп (цикл замкнут)
        p.animate(x0 + 0.5, y0 + 0.5, x1 - 0.5, y1 - 0.5, (q, t) -> {
            q.fill(x0 + 0.5, y0 + 0.5, x1 - 0.5, y1 - 0.5, (u, v) -> Paint.ramp(AMBER, 0.06 + 0.06 * (1 - (v - y0) / (y1 - y0))));
            for (double gx = x0 + 1.0; gx < x1 - 0.5; gx += 1.0) {
                q.rect(gx, y0 + 0.6, gx + 0.06, y1 - 0.6, at(AMBER, 1.2));
            }
            for (double gy = y0 + 1.2; gy < y1 - 0.5; gy += 1.2) {
                q.rect(x0 + 0.6, gy, x1 - 0.6, gy + 0.06, at(AMBER, 1.2));
            }
            for (int i = 0; i < 11; i++) {
                double h = Math.max(0.4, Math.min(4.6, base[i] + amp[i] * Math.sin(2 * Math.PI * freq[i] * t + ph[i])));
                double bx = x0 + 0.85 + i * 0.52;
                q.rect(bx, y1 - 0.8 - h, bx + 0.38, y1 - 0.8, at(AMBER, 3.6 + (h / 5) * 2));
                q.rect(bx, y1 - 0.8 - h, bx + 0.38, y1 - 0.8 - h + 0.15, at(AMBER, 6));
            }
            q.glowRect(x0 + 0.6, y0 + 0.6, x1 - 0.6, y1 - 0.6, 0.5, at(AMBER, 3.0), 40);
        });
        // экран 2: текст ползёт вверх построчно (12 строк по кругу — цикл замкнут), внизу мигает курсор
        double ax0 = 17.0, ax1 = 24.0;
        screenBezel(p, ax0, y0, ax1, y1);
        double[][][] lines = new double[12][][];
        for (int row = 0; row < 12; row++) {
            int words = 2 + r.nextInt(3);
            lines[row] = new double[words][];
            for (int w = 0; w < words; w++) {
                lines[row][w] = new double[]{0.5 + r.nextDouble() * 1.7, 4.4 + r.nextDouble() * 1.4};
            }
        }
        p.animate(ax0 + 0.5, y0 + 0.5, ax1 - 0.5, y1 - 0.5, (q, t) -> {
            q.fill(ax0 + 0.5, y0 + 0.5, ax1 - 0.5, y1 - 0.5, (u, v) -> Paint.ramp(AMBER, 0.06 + 0.05 * (1 - (v - y0) / (y1 - y0))));
            int first = (int) Math.floor(t * 12);
            for (int row = 0; row < 6; row++) {
                double[][] ln = lines[(first + row) % 12];
                double x = ax0 + 0.8, y = y0 + 0.8 + row * 0.85;
                for (int w = 0; w < ln.length && x < ax1 - 1.0; w++) {
                    q.rect(x, y, Math.min(ax1 - 0.8, x + ln[w][0]), y + 0.3, at(AMBER, ln[w][1]));
                    x += ln[w][0] + 0.5;
                }
            }
            if (((int) Math.floor(t * 24)) % 2 == 0) {
                q.rect(ax0 + 0.8, y0 + 0.8 + 5 * 0.85 + 0.6, ax0 + 1.4, y0 + 0.8 + 5 * 0.85 + 1.0, at(AMBER, 6));
            }
            q.glowRect(ax0 + 0.6, y0 + 0.6, ax1 - 0.6, y1 - 0.6, 0.5, at(AMBER, 3.0), 40);
        });
    }

    /** Лопасти вентилятора в тёмном кожухе радиуса r, повёрнутые на rot; ступица — поверх. */
    static void fanBlades(Paint q, double cx, double cy, double r, int blades, double rot) {
        q.disc(cx, cy, r, rgb(0x050607));
        for (int b = 0; b < blades; b++) {
            double a0 = b * 2 * Math.PI / blades + rot;
            for (double rr = 0.7; rr < r - 0.1; rr += 0.07) {
                double a = a0 + rr * 0.55;
                q.disc(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr, 0.22 + rr * 0.07, Paint.ramp(IRON, 0.35 + 0.35 * (rr / r) + 0.1 * Math.sin(a * 2)));
            }
        }
        for (double rr : new double[]{r * 0.5, r * 0.75}) {
            q.ring(cx, cy, rr, rr + 0.1, argb(160, 0x000000));
        }
        q.sphere(cx, cy, Math.min(0.55, r * 0.2), BRASS, 0.3, 1.0);
        q.disc(cx, cy, 0.18, at(IRON, 0.1));
    }

    static void fanAndVents(Paint p) {
        // вентилятор в латунном квадрате
        double fx0 = 9.0, fy0 = 12.0, fx1 = 16.0, fy1 = 19.0, cx = 12.5, cy = 15.5;
        p.slab(fx0, fy0, fx1, fy1, BRASS, 2.6, 1);
        p.slab(fx0 + 0.5, fy0 + 0.5, fx1 - 0.5, fy1 - 0.5, IRON, 0.8, 1);
        p.disc(cx, cy, 3.05, at(BRASS, 1.2));
        p.ring(cx, cy, 2.7, 3.05, at(BRASS, 4.4));
        // вентилятор крутится: за цикл — на две лопасти
        p.animate(cx - 2.7, cy - 2.7, cx + 2.7, cy + 2.7, (q, t) -> fanBlades(q, cx, cy, 2.65, 6, t * 2 * Math.PI / 3));
        p.sphere(cx, cy, 0.55, BRASS, 0.3, 1.0);
        for (double[] c : new double[][]{{fx0 + 0.55, fy0 + 0.55}, {fx1 - 0.55, fy0 + 0.55}, {fx0 + 0.55, fy1 - 0.55}, {fx1 - 0.55, fy1 - 0.55}}) {
            p.screw(c[0], c[1], 0.32, BRASS, c[0]);
        }
        p.frostLine(fx0 + 0.4, fx1 - 0.4, fy0 + 0.5, 0.45, 961);
        // жалюзи
        double vx0 = 17.0, vy0 = 12.0, vx1 = 24.0, vy1 = 19.0;
        p.slab(vx0, vy0, vx1, vy1, BRASS, 2.6, 1);
        p.rect(vx0 + 0.5, vy0 + 0.5, vx1 - 0.5, vy1 - 0.5, rgb(0x050607));
        double step = (vy1 - vy0 - 1.0) / 7;
        for (int i = 0; i < 7; i++) {
            double y = vy0 + 0.5 + i * step;
            p.slab(vx0 + 0.5, y + 0.12, vx1 - 0.5, y + step * 0.62, IRON, 3.2, 1);
        }
        for (double[] c : new double[][]{{vx0 + 0.25, vy0 + 0.25}, {vx1 - 0.25, vy0 + 0.25}, {vx0 + 0.25, vy1 - 0.25}, {vx1 - 0.25, vy1 - 0.25}}) {
            p.rivet(c[0], c[1], 0.14, BRASS);
        }
    }

    static void lower(Paint p) {
        // слева: две латунные трубы со светящимися вставками, манометр и вентиль
        double x0 = 9.0, x1 = 16.0;
        for (double y : new double[]{20.8, 25.4}) {
            p.slab(x0, y, x1, y + 1.0, BRASS, 2.4, 1);
            // импульсы бегут по трубе слева направо
            final double py = y;
            p.animate(x0, y - 0.2, x1, y + 1.2, (q, t) -> {
                int i = 0;
                for (double x = x0 + 1.0; x < x1 - 1.0; x += 2.4, i++) {
                    double b = wave(t, i, 3);
                    q.rect(x, py + 0.3, x + 1.0, py + 0.7, rgb(0x061a20));
                    q.rect(x + 0.1, py + 0.36, x + 0.9, py + 0.64, at(CYAN, 2.0 + 3.6 * b));
                    q.glowRect(x + 0.1, py + 0.36, x + 0.9, py + 0.64, 0.4, at(CYAN, 3.5), (int) (20 + 70 * b));
                }
                for (double x : new double[]{x0 + 0.6, x1 - 1.2}) {
                    q.slab(x, py - 0.18, x + 0.6, py + 1.18, BRASS, 3.5, 1);
                }
            });
        }
        p.slab(12.2, 21.8, 12.8, 25.4, BRASS, 2.2, 1);
        p.gauge(10.6, 23.6, 1.25, 80, argb(255, 0xb02020));
        p.gauge(14.4, 23.6, 1.25, 140, argb(255, 0xb02020));
        // справа: рунная табличка и ряд янтарных ламп
        double rx0 = 17.0, ry0 = 20.0, rx1 = 24.0, ry1 = 24.9;
        p.slab(rx0, ry0, rx1, ry1, BRASS, 3.0, 1);
        p.rect(rx0 + 0.5, ry0 + 0.5, rx1 - 0.5, ry1 - 0.5, rgb(0x0a0c10));
        p.glowRect(rx0 + 0.6, ry0 + 0.6, rx1 - 0.6, ry1 - 0.6, 0.7, at(VIOLET, 3.5), 60);
        int[] glyphs = {0, 12, 17, 4};
        for (int i = 0; i < 4; i++) {
            p.rune(rx0 + 0.95 + i * 1.5, ry0 + 0.8, 3.0, glyphs[i], at(VIOLET, 5.4), at(VIOLET, 3.0));
        }
        p.slab(rx0, 25.4, rx1, 27.4, BRASS, 2.6, 1);
        p.rect(rx0 + 0.45, 25.85, rx1 - 0.45, 26.95, rgb(0x1d1004));
        // лампы перемигиваются: у каждой свой рисунок включений за цикл
        p.animate(rx0 + 0.45, 25.85, rx1 - 0.45, 26.95, (q, t) -> {
            q.rect(rx0 + 0.45, 25.85, rx1 - 0.45, 26.95, rgb(0x1d1004));
            for (int i = 0; i < 6; i++) {
                boolean on = blink(t, i, 8);
                q.disc(rx0 + 1.0 + i * 1.0, 26.4, 0.34, at(AMBER, on ? 5.0 : 2.0));
                if (on) {
                    q.glowRect(rx0 + 0.9 + i * 1.0, 26.3, rx0 + 1.1 + i * 1.0, 26.5, 0.5, at(AMBER, 4.0), 70);
                }
            }
        });
    }

    static Paint elevation() {
        Paint p = new Paint(32, 32, K);
        p.slab(0, 0, 32, 32, IRON, 1.4, 1);
        frame(p);
        coil(p);
        pipes(p);
        field(p, BX0, BX1, 971);
        screens(p);
        fanAndVents(p);
        lower(p);
        return p;
    }

    /** Режет картину на четыре текстуры клеток (клетка bl — правая нижняя четверть картины и т.д.). */
    static void save(Paint p) throws Exception {
        p.crop(16, 16, 16, 16).save(Forge2.tex("cab_back_bl"));
        p.crop(0, 16, 16, 16).save(Forge2.tex("cab_back_br"));
        p.crop(16, 0, 16, 16).save(Forge2.tex("cab_back_tl"));
        p.crop(0, 0, 16, 16).save(Forge2.tex("cab_back_tr"));
        Files.deleteIfExists(Forge2.tex("cab_back").toPath());
    }

    // ------------------------------------------------------------------------------------------------- рельеф и свечение
    /** Выступ спинки по прямоугольнику картины (юниты, y вниз): лицо — та же картина, но светящаяся, бока — латунь. */
    static void rel(BigModel m, double ix0, double iy0, double ix1, double iy1, int glow, BigModel.Face side) {
        BigModel.Face lit = glow > 0 ? BigModel.Face.of("back").glow(glow) : BigModel.Face.of("back");
        m.box(32 - ix1, 32 - iy1, Forge2.BACKZ, 32 - ix0, 32 - iy0, 16).back(lit).left(side).right(side).top(side).bottom(side);
    }

    static void relief(BigModel m, BigModel.Face brass) {
        rel(m, 3.5, 5.5, 5.5, 26.5, 10, brass);          // колба катушки
        rel(m, 9.5, 5.1, 15.5, 10.7, 13, brass);         // экран-гистограмма
        rel(m, 17.5, 5.1, 23.5, 10.7, 13, brass);        // экран-текст
        rel(m, 17.5, 20.5, 23.5, 24.4, 14, brass);       // рунная табличка
        rel(m, 17.5, 25.9, 23.5, 26.9, 13, brass);       // янтарные лампы
        rel(m, 26.95, 4.4, 27.9, 27.6, 0, brass);        // трубы справа
        rel(m, 28.15, 4.4, 29.1, 27.6, 0, brass);
        rel(m, 9.0, 20.8, 16.0, 21.8, 0, brass);         // горизонтальные трубы слева
        rel(m, 9.0, 25.4, 16.0, 26.4, 0, brass);
    }

    private static final class Files {
        static void deleteIfExists(java.nio.file.Path p) throws java.io.IOException {
            java.nio.file.Files.deleteIfExists(p);
        }
    }
}
