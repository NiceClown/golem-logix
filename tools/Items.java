import java.io.File;
import java.util.Random;

/**
 * Иконки предметов 32x32 (холст 16x16 юнитов, 2 пикселя на юнит), пятое поколение:
 * Воксельный изометрический стиль Magic-Steampunk Golem Logix.
 * Окисленная медь с бирюзовой патиной (verdigris), тёплая латунь, кованое железо,
 * светящиеся руны и логические дорожки, аналоговые манометры, морозный крио-иней.
 */
final class Items {
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, BLUE = Paint.BLUE, CREAM = Paint.CREAM;
    static final int[] LEATHER = {0x1e0e08, 0x3a1c10, 0x5a2c18, 0x7a3e22, 0x98542e, 0xb87040, 0xd89a68};
    static final int[] GOLD = {0x3a2604, 0x6a4808, 0x9a6e10, 0xc8981e, 0xe8c040, 0xf8dc78, 0xfff4c8};
    static final int[] VERDIGRIS = {0x0e2f2e, 0x164b48, 0x216c68, 0x2e928c, 0x48b6ad, 0x76d5cc, 0xb2f4ee};
    static final int[] NETHERITE = {0x141018, 0x241d24, 0x362c38, 0x4a3d4c, 0x625466, 0x7e7084, 0x9e90a6};

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    static File itemTex(String name) {
        return Forge2.RES.resolve("textures/item/" + name + ".png").toFile();
    }

    static Paint canvas() {
        return new Paint(16, 16, 2);
    }

    /** Мягкая тень под предметом (эллипс). */
    static void shadow(Paint p, double cx, double cy, double rx, double ry) {
        for (int j = 0; j < p.h; j++) {
            for (int i = 0; i < p.w; i++) {
                double u = (i + 0.5) / p.k, v = (j + 0.5) / p.k;
                double d = Math.pow((u - cx) / rx, 2) + Math.pow((v - cy) / ry, 2);
                if (d < 1) {
                    p.over(i, j, argb((int) (80 * (1 - d)), 0x000000));
                }
            }
        }
    }

    /** Четырёхугольник (по часовой) с заливкой функцией цвета. */
    static void quad(Paint p, double[] xs, double[] ys, Paint.PixelFn f) {
        p.polyFn(xs, ys, f);
    }

    /** Медь с патиной: бленд медного оттенка с бирюзовой окисью по карте шума. */
    static int copperPatina(double baseT, double noise) {
        if (noise > 0.58) {
            double pt = (noise - 0.58) / 0.42;
            return Paint.mix(Paint.ramp(COPPER, baseT), Paint.ramp(VERDIGRIS, baseT * 0.8 + 0.2), Math.min(1.0, pt * 1.4));
        }
        return Paint.ramp(COPPER, baseT);
    }

    // ============================================================================================ кассеты данных
    /** Цвета тиров: 1k медь, 4k железо, 16k золото, 64k бирюза/алмаз, 256k аметист. */
    static final int[] TIER = {0xD9803A, 0x9AA4B4, 0xF0C850, 0x55E6E0, 0xA070FF};

    /** Магнитная кассета данных: окисленный медный корпус с патиной, латунная накладка с руной тира, бобины с лентой, золотые контакты снизу. */
    static Paint copperDisk(int tier) {
        Paint p = canvas();
        shadow(p, 8.2, 13.4, 7.2, 1.3);
        double x0 = 1.2, x1 = 14.8, y0 = 4.4, y1 = 12.2, th = 1.4;

        // Золотые контакты разъема снизу
        for (double gx = x0 + 3.8; gx < x1 - 3.4; gx += 1.4) {
            p.rect(gx, y1 - 0.2, gx + 0.8, y1 + 1.2, at(GOLD, 4.8));
            p.rect(gx, y1 + 0.8, gx + 0.8, y1 + 1.2, at(GOLD, 2.6));
        }

        // Верхний скошенный торец (толщина корпуса)
        quad(p, new double[]{x0, x0 + 0.9, x1 + 0.9, x1}, new double[]{y0, y0 - th, y0 - th, y0},
                (u, v) -> {
                    double n = 0.5 + 0.5 * Math.sin(u * 7.1 + v * 3.3);
                    return copperPatina(0.65 + 0.12 * (u - x0) / (x1 - x0), n);
                });
        p.line(x0 + 0.9, y0 - th + 0.15, x1 + 0.9, y0 - th + 0.15, 0.3, at(BRASS, 5.5));

        // Правый торец
        quad(p, new double[]{x1, x1 + 0.9, x1 + 0.9, x1}, new double[]{y0, y0 - th, y1 - th, y1},
                (u, v) -> at(COPPER, 1.8));

        // Лицевая панель из окисленной меди с патиной
        Random rnd = new Random(2101 + tier * 17L);
        quad(p, new double[]{x0, x1, x1, x0}, new double[]{y0, y0, y1, y1},
                (u, v) -> {
                    double noise = 0.5 + 0.5 * Math.sin(u * 4.2 + v * 5.8) + 0.2 * Math.sin(u * 12.3);
                    double baseT = 0.48 - 0.12 * (v - y0) / (y1 - y0) + 0.1 * (1.0 - (u - x0) / (x1 - x0));
                    return copperPatina(baseT, noise);
                });
        p.streaks(x0 + 0.3, y0 + 0.3, x1 - 0.3, y1 - 0.3, 0.05, 2101 + tier, true);

        // Патина пятна
        for (int i = 0; i < 4; i++) {
            double px = x0 + 1.2 + rnd.nextDouble() * (x1 - x0 - 2.4);
            double py = y0 + 1.0 + rnd.nextDouble() * (y1 - y0 - 2.0);
            p.disc(px, py, 0.6, at(VERDIGRIS, 4.2));
            p.disc(px, py, 0.3, at(VERDIGRIS, 5.2));
        }

        // Латунная шильда с руной емкости по центру сверху
        int labelColor = rgb(TIER[tier]);
        p.slab(x0 + 1.2, y0 + 0.5, x1 - 1.2, y0 + 2.3, BRASS, 3.8, 1);
        p.rect(x0 + 1.6, y0 + 0.7, x1 - 1.6, y0 + 2.1, at(BRASS, 2.2));
        p.rect(x0 + 1.8, y0 + 0.8, x1 - 1.8, y0 + 2.0, Paint.shade(labelColor, 0.45));

        // Декоративные полоски текста и руна емкости на шильде
        for (int i = 0; i < 2; i++) {
            p.rect(x0 + 2.4, y0 + 1.0 + i * 0.5, x0 + 6.2 - i * 1.0, y0 + 1.2 + i * 0.5, at(CREAM, 4.5));
        }
        // Руна тира
        p.rune(x1 - 3.6, y0 + 0.7, 1.4, 3 + tier * 5, labelColor, at(GOLD, 1.2));
        p.glowRect(x1 - 3.6, y0 + 0.7, x1 - 2.2, y0 + 2.1, 0.4, labelColor, 80);

        // Окно с катушками (углубление)
        double wx0 = x0 + 1.0, wx1 = x1 - 1.0, wy0 = y0 + 2.8, wy1 = y1 - 0.9;
        p.rect(wx0, wy0, wx1, wy1, at(IRON, 0.4));
        p.rect(wx0, wy0, wx1, wy0 + 0.4, at(IRON, 0.1));
        p.rect(wx0, wy1 - 0.3, wx1, wy1, at(IRON, 2.8));

        double cy = (wy0 + wy1) / 2 + 0.1;
        double[] cx = {x0 + 3.6, x1 - 3.6};
        double tape = 1.1 + 0.28 * tier;
        for (int s = 0; s < 2; s++) {
            double r = s == 0 ? Math.min(2.0, tape) : Math.max(1.0, 2.2 - tape * 0.5);
            final double sx = cx[s];
            // Намотанная медная лента с витками
            p.discFn(sx, cy, r, (u, v) -> Paint.ramp(COPPER, 0.22 + 0.14 * (0.5 + 0.5 * Math.cos(Math.hypot(u - sx, v - cy) * 11))));
            p.disc(cx[s], cy, 0.95, at(BRASS, 2.0));
            p.sphere(cx[s], cy, 0.85, BRASS, 0.35, 0.95);
            for (int k = 0; k < 3; k++) {
                double a = k * 2 * Math.PI / 3 + s;
                p.disc(cx[s] + Math.cos(a) * 0.45, cy + Math.sin(a) * 0.45, 0.18, at(IRON, 0.2));
            }
        }
        // Натянутая лента между катушками
        p.line(cx[0], cy + Math.min(2.0, tape) - 0.15, cx[1], cy + Math.max(1.0, 2.2 - tape * 0.5) - 0.15, 0.35, at(COPPER, 1.2));

        // Блик на стекле окна
        p.line(wx0 + 0.6, wy1 - 0.6, wx0 + 2.6, wy0 + 0.6, 0.3, argb(100, 0xFFFFFF));

        // Светодиоды активности / данных (светятся бирюзовым)
        p.disc(x0 + 0.8, y0 + 1.2, 0.35, at(CYAN, 5.5));
        p.glowRect(x0 + 0.6, y0 + 1.0, x0 + 1.0, y0 + 1.4, 0.5, at(CYAN, 4.5), 110);
        p.disc(x1 - 0.8, y0 + 1.2, 0.35, at(CYAN, 5.5));
        p.glowRect(x1 - 1.0, y0 + 1.0, x1 - 0.6, y0 + 1.4, 0.5, at(CYAN, 4.5), 110);

        // Латунные уголки и заклепки
        for (double[] c : new double[][]{{x0, y0}, {x1 - 1.4, y0}, {x0, y1 - 1.4}, {x1 - 1.4, y1 - 1.4}}) {
            p.slab(c[0], c[1], c[0] + 1.4, c[1] + 1.4, BRASS, 3.6, 1);
            p.rivet(c[0] + 0.7, c[1] + 0.7, 0.32, BRASS);
        }

        // Индикатор заполненности/тира внизу
        for (int i = 0; i <= tier; i++) {
            p.rect(x0 + 4.6 + i * 1.2, y1 - 0.55, x0 + 5.4 + i * 1.2, y1 - 0.2, at(CYAN, 5.2));
        }
        p.glowRect(x0 + 4.6, y1 - 0.55, x0 + 5.4 + tier * 1.2, y1 - 0.2, 0.4, at(CYAN, 4.0), 90);
        return p;
    }

    // ============================================================================================ криогенные кассеты жидкостей
    static final int[][] FLUID = {
            {0x0d4547, 0x136f70, 0x20a8a6, 0x45d6d2, 0x96f2ee, 0xe2fffe, 0xffffff},
            {0x0d3a5a, 0x165a86, 0x2a86b8, 0x4cb0e0, 0x8ad4f6, 0xd0f0ff, 0xffffff},
            {0x0a2a66, 0x14448e, 0x2066c0, 0x3c8ae6, 0x76b2f8, 0xc4e0ff, 0xffffff},
            {0x141a5c, 0x232e8a, 0x3446b8, 0x5266e0, 0x8a98f6, 0xccd4ff, 0xffffff},
            VIOLET};

    /** Крио-капсула жидкости: окисленная медная рама, стеклянная колба с бурлящей маной, конденсаторные ребра, золотые контакты. */
    static Paint fluidDisk(int tier) {
        Paint p = canvas();
        shadow(p, 8.0, 14.6, 5.4, 1.2);
        int[] frame = tier == 4 ? NETHERITE : BRASS;
        int[] liq = FLUID[tier];
        double x0 = 4.4, x1 = 11.6, y0 = 1.4, y1 = 14.2;

        // Золотые контакты снизу
        for (double gx = x0 + 1.2; gx < x1 - 1.0; gx += 1.4) {
            p.rect(gx, y1 + 0.1, gx + 0.8, y1 + 1.3, at(GOLD, 4.8));
            p.rect(gx, y1 + 0.9, gx + 0.8, y1 + 1.3, at(GOLD, 2.5));
        }

        // Боковые охладительные ребра радиатора с инеем
        for (int i = 0; i < 5; i++) {
            double ry = y0 + 3.2 + i * 1.8;
            p.rect(x0 - 1.6, ry, x0 - 0.4, ry + 0.6, at(COPPER, 2.8));
            p.rect(x0 - 1.6, ry, x0 - 1.2, ry + 0.6, at(FROST, 5.0));
            p.rect(x1 + 0.4, ry, x1 + 1.6, ry + 0.6, at(COPPER, 2.4));
            p.rect(x1 + 1.2, ry, x1 + 1.6, ry + 0.6, at(FROST, 5.0));
        }

        // Массивные верхний и нижний колпаки с латунными фланцами и патиной
        for (double[] c : new double[][]{{y0, y0 + 2.2}, {y1 - 2.2, y1}}) {
            p.slab(x0 - 0.4, c[0], x1 + 0.4, c[1], frame, 3.2, 1);
            p.rect(x0 + 0.4, c[0] + 0.7, x1 - 0.4, c[0] + 1.1, at(frame, 1.6));
            p.rivet(x0 + 0.6, (c[0] + c[1]) / 2, 0.32, frame);
            p.rivet(x1 - 0.6, (c[0] + c[1]) / 2, 0.32, frame);
        }
        p.frostLine(x0 - 0.4, x1 + 0.4, y0, 0.6, 2201 + tier);

        // Вертикальные стойки рамы
        p.slab(x0 - 0.5, y0 + 1.8, x0 + 0.5, y1 - 1.8, frame, 2.8, 1);
        p.slab(x1 - 0.5, y0 + 1.8, x1 + 0.5, y1 - 1.8, frame, 2.4, 1);

        // Колба: темное стекло сверху, вихревая светящаяся крио-жидкость снизу
        double gx0 = x0 + 0.6, gx1 = x1 - 0.6, gy0 = y0 + 2.2, gy1 = y1 - 2.2;
        double level = gy1 - (gy1 - gy0) * (0.58 + 0.08 * tier);
        p.fill(gx0, gy0, gx1, gy1, (u, v) -> {
            double t = (u - gx0) / (gx1 - gx0), cyl = 1 - Math.pow(2 * t - 1, 2);
            if (v < level) {
                return Paint.mix(rgb(0x07121c), rgb(0x284858), 0.2 + 0.45 * cyl);
            }
            double swirl = 0.5 + 0.5 * Math.sin(u * 5.0 + v * 3.0);
            return Paint.ramp(liq, 0.28 + 0.48 * cyl + 0.1 * swirl - 0.12 * (v - level) / (gy1 - level));
        });

        // Мениск и стеклянные блики
        p.rect(gx0, level, gx1, level + 0.35, at(liq, 5.8));
        p.line(gx0 + 0.4, gy0 + 0.4, gx0 + 0.7, gy1 - 0.4, 0.3, argb(170, 0xFFFFFF));
        p.line(gx1 - 0.7, gy0 + 0.8, gx1 - 0.5, gy1 - 0.8, 0.25, argb(80, 0xFFFFFF));

        // Пузырьки и свечение жидкости
        Random r = new Random(2211 + tier);
        for (int i = 0; i < 5; i++) {
            double bx = gx0 + 1.0 + r.nextDouble() * (gx1 - gx0 - 2.0);
            double by = level + 0.6 + r.nextDouble() * (gy1 - level - 1.2);
            p.ring(bx, by, 0.18, 0.38, argb(220, Paint.ramp(liq, 0.95) & 0xFFFFFF));
        }

        // Светящаяся руна внутри жидкости
        p.rune(7.2, level + 0.8, Math.min(3.4, gy1 - level - 1.2), 2 + tier * 5, at(liq, 6.0), at(liq, 3.4));
        p.glowRect(gx0 + 0.4, level, gx1 - 0.4, gy1, 0.8, at(liq, 4.4), 85);

        // Засечки шкалы уровня на боковой стойке
        for (int i = 0; i <= tier + 1; i++) {
            p.rect(x1 - 0.3, gy1 - 0.6 - i * 1.1, x1 + 0.4, gy1 - 0.35 - i * 1.1, at(CYAN, 5.4));
        }
        return p;
    }

    // ============================================================================================ перфокарта
    /** Медная перфокарта: тяжелый картридж с латунной рамкой, сеткой пробивок со светящимся бирюзовым светом, патиной и заклепками. */
    static Paint punchCard() {
        Paint p = canvas();
        shadow(p, 8.4, 13.6, 7.2, 1.2);
        double x0 = 1.0, x1 = 15.0, y0 = 3.0, y1 = 13.0;

        // Скошенный верхний левый угол
        double[] xs = {x0 + 1.8, x1, x1, x0, x0}, ys = {y0, y0, y1, y1, y0 + 1.8};
        p.polyFn(xs, ys, (u, v) -> {
            double n = 0.5 + 0.5 * Math.sin(u * 5.2 + v * 4.4);
            double baseT = 0.62 + 0.1 * (1 - (u - x0) / 14) - 0.08 * (v - y0) / 10;
            return copperPatina(baseT, n);
        });
        p.streaks(x0, y0, x1, y1, 0.06, 2301, true);

        // Латунная окантовка картриджа
        p.line(x0 + 1.8, y0 + 0.15, x1, y0 + 0.15, 0.35, at(BRASS, 5.6));
        p.line(x0 + 0.15, y0 + 1.8, x0 + 0.15, y1, 0.35, at(BRASS, 5.2));
        p.line(x0 + 0.1, y0 + 1.8, x0 + 1.8, y0 + 0.1, 0.35, at(BRASS, 5.8));
        p.rect(x0, y1 - 0.35, x1, y1, at(BRASS, 2.0));
        p.rect(x1 - 0.35, y0, x1, y1, at(BRASS, 2.2));

        // Пятна окислившейся патины
        p.disc(x0 + 3.2, y1 - 1.2, 0.7, at(VERDIGRIS, 4.4));
        p.disc(x1 - 2.0, y0 + 2.8, 0.8, at(VERDIGRIS, 4.6));

        // Латунная плашка заголовка с рунами
        p.slab(x0 + 2.4, y0 + 0.6, x0 + 9.6, y0 + 2.2, BRASS, 3.6, 1);
        for (int i = 0; i < 4; i++) {
            p.rune(x0 + 2.8 + i * 1.6, y0 + 0.8, 1.1, i * 4 + 7, at(VIOLET, 5.2), 0);
        }
        p.glowRect(x0 + 2.8, y0 + 0.8, x0 + 9.2, y0 + 2.0, 0.3, at(VIOLET, 4.0), 60);

        // Шестерня-индекс в верхнем правом углу
        p.gearStatic(x1 - 1.8, y0 + 1.4, 1.1, 6, 0.2, BRASS, 3.8);

        // Пробивки: сетка отверстий со светящимся бирюзовым кодом рецепта внутри
        Random r = new Random(2302);
        for (int c = 0; c < 12; c++) {
            for (int row = 0; row < 5; row++) {
                double hx = x0 + 1.2 + c * 1.08, hy = y0 + 3.2 + row * 1.25;
                if (r.nextInt(5) < 3) {
                    // Пробитое отверстие с ярким бирюзовым свечением!
                    p.rect(hx, hy, hx + 0.65, hy + 0.85, at(CYAN, 5.2));
                    p.glowRect(hx - 0.1, hy - 0.1, hx + 0.75, hy + 0.95, 0.3, at(CYAN, 4.0), 80);
                    p.rect(hx, hy, hx + 0.65, hy + 0.2, at(CYAN, 6.0));
                } else {
                    // Непробитый след
                    p.rect(hx + 0.15, hy + 0.3, hx + 0.5, hy + 0.55, at(COPPER, 1.8));
                }
            }
        }

        // Заклепки по углам
        p.rivet(x1 - 1.2, y1 - 1.2, 0.42, BRASS);
        p.rivet(x0 + 1.2, y1 - 1.2, 0.42, BRASS);
        return p;
    }

    // ============================================================================================ сплющенные пластины
    interface PlateFace {
        int at(double s, double t);
    }

    static void plate(Paint p, double th, int[] edgeRamp, PlateFace face) {
        double cx = 8, cy = 7.4, hw = 7.2, hh = 3.9;
        double[] tx = {cx - hw, cx, cx + hw, cx}, ty = {cy, cy - hh, cy, cy + hh};
        // Торцы (толщина плиты)
        quad(p, new double[]{cx - hw, cx, cx, cx - hw}, new double[]{cy, cy + hh, cy + hh + th, cy + th},
                (u, v) -> Paint.ramp(edgeRamp, 0.44 - 0.1 * (v - cy) / (hh + th)));
        quad(p, new double[]{cx, cx + hw, cx + hw, cx}, new double[]{cy + hh, cy, cy + th, cy + hh + th},
                (u, v) -> Paint.ramp(edgeRamp, 0.26 - 0.08 * (v - cy) / (hh + th)));
        // Верхняя грань: координаты s и t
        quad(p, tx, ty, (u, v) -> {
            double a = (u - cx) / hw, b = (v - cy) / hh;
            double s = (1 + a - b) / 2, t = (1 - a - b) / 2;
            return face.at(Math.max(0, Math.min(1, s)), Math.max(0, Math.min(1, t)));
        });
        // Светлая окантовка по верхним рёбрам, тёмная по нижним
        p.line(cx - hw, cy, cx, cy - hh, 0.32, argb(180, Paint.ramp(edgeRamp, 0.95) & 0xFFFFFF));
        p.line(cx, cy - hh, cx + hw, cy, 0.32, argb(130, Paint.ramp(edgeRamp, 0.85) & 0xFFFFFF));
        p.line(cx - hw, cy + th, cx, cy + hh + th, 0.28, argb(170, 0x000000));
        p.line(cx, cy + hh + th, cx + hw, cy + th, 0.28, argb(170, 0x000000));
    }

    static double[] onPlate(double s, double t) {
        double cx = 8, cy = 7.4, hw = 7.2, hh = 3.9;
        double a = s - t, b = 1 - s - t;
        return new double[]{cx + a * hw, cy + b * hh};
    }

    /** Сплющенный железный сердечник: тяжелый кованый блок с трещинами, заклепками, ступенчатым ядром и светящейся бирюзовой руной. */
    static Paint ironCore() {
        Paint p = canvas();
        shadow(p, 8.4, 13.4, 7.4, 1.3);
        Random r = new Random(2401);
        double[] noise = new double[64];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = r.nextDouble();
        }

        plate(p, 1.5, IRON, (s, t) -> {
            double d = Math.hypot(s - 0.5, t - 0.5) * 2;
            double rings = 0.5 + 0.5 * Math.cos(d * 22);
            double base = 0.52 + 0.12 * (1 - s) - 0.08 * t + 0.05 * noise[(int) (s * 7.99) * 8 + (int) (t * 7.99)];
            // Кованые трещины
            boolean crack = Math.abs(s - t) < 0.04 && d > 0.4 && d < 0.95;
            if (crack) {
                return rgb(0x121418);
            }
            if (d < 0.64) {
                base += 0.1 * rings - 0.06;
            }
            if (Math.abs(d - 0.65) < 0.035) {
                base = 0.88;
            }
            return Paint.ramp(IRON, base);
        });

        // 8 латунных массивных заклепок по углам и граням
        for (double[] st : new double[][]{{0.1, 0.1}, {0.9, 0.1}, {0.1, 0.9}, {0.9, 0.9}, {0.5, 0.08}, {0.08, 0.5}, {0.92, 0.5}, {0.5, 0.92}}) {
            double[] c = onPlate(st[0], st[1]);
            p.rivet(c[0], c[1], 0.38, BRASS);
        }

        // Центральный концентрический сердечник со светящейся оккультной руной
        double[] c = onPlate(0.5, 0.5);
        p.disc(c[0], c[1], 1.2, at(IRON, 0.5));
        p.sphere(c[0], c[1], 0.9, IRON, 0.35, 1.0);
        p.ring(c[0], c[1], 0.7, 0.9, at(BRASS, 4.0));
        p.rune(c[0] - 0.6, c[1] - 0.9, 1.8, 8, at(CYAN, 5.6), 0);
        p.glowRect(c[0] - 1.0, c[1] - 1.0, c[0] + 1.0, c[1] + 1.0, 0.8, at(CYAN, 4.4), 100);
        return p;
    }

    /** Сплющенная медная матрица: окисленная медь с бирюзовой патиной, латунной рамкой, золотыми контактами по бокам и светящейся схемой. */
    static Paint copperMatrix() {
        Paint p = canvas();
        shadow(p, 8.4, 13.4, 7.4, 1.3);

        plate(p, 1.3, COPPER, (s, t) -> {
            double gs = s * 8, gt = t * 8;
            double fs = gs - Math.floor(gs), ft = gt - Math.floor(gt);
            boolean trace = (Math.abs(fs - 0.5) < 0.11 && ((int) gt + (int) gs) % 3 != 0)
                    || (Math.abs(ft - 0.5) < 0.11 && ((int) gs * 5 + (int) gt) % 4 != 1);
            boolean pad = Math.abs(fs - 0.5) < 0.22 && Math.abs(ft - 0.5) < 0.22 && ((int) gs * 3 + (int) gt * 7) % 5 == 0;
            double n = 0.5 + 0.5 * Math.sin(s * 15.0 + t * 18.0);
            double baseT = 0.5 + 0.12 * (1 - s) - 0.08 * t;

            if (s < 0.08 || t < 0.08 || s > 0.92 || t > 0.92) {
                return Paint.ramp(BRASS, 0.68);
            }
            if (pad) {
                return Paint.ramp(BRASS, 0.88);
            }
            if (trace) {
                // Светящаяся бирюзовая логическая дорожка
                return at(CYAN, 5.2);
            }
            return copperPatina(baseT, n);
        });

        // Золотые контактные ножки по краям
        for (double st = 0.2; st <= 0.8; st += 0.2) {
            double[] pTop = onPlate(st, 0.04);
            p.rect(pTop[0] - 0.25, pTop[1] - 0.4, pTop[0] + 0.25, pTop[1], at(GOLD, 5.0));
            double[] pRight = onPlate(0.96, st);
            p.rect(pRight[0], pRight[1] - 0.2, pRight[0] + 0.4, pRight[1] + 0.2, at(GOLD, 4.6));
        }

        // Центральный чип / кристалл процессора
        double[] c = onPlate(0.5, 0.5);
        p.slab(c[0] - 1.4, c[1] - 0.9, c[0] + 1.4, c[1] + 0.9, IRON, 0.7, 1);
        p.rect(c[0] - 1.0, c[1] - 0.5, c[0] + 1.0, c[1] + 0.5, at(CYAN, 5.0));
        p.glowRect(c[0] - 1.0, c[1] - 0.5, c[0] + 1.0, c[1] + 0.5, 0.8, at(CYAN, 4.0), 110);

        // Шестерни-инкрустации по углам
        double[] c1 = onPlate(0.18, 0.82);
        p.gearStatic(c1[0], c1[1], 0.8, 6, 0.15, BRASS, 3.4);
        return p;
    }

    /** Сплющенная крио-подложка: блок голубого льда в латунных скобах, сосульки снизу, закрученная метель и руны льда. */
    static Paint cryoSubstrate() {
        Paint p = canvas();
        shadow(p, 8.4, 13.4, 7.4, 1.3);

        plate(p, 1.4, FROST, (s, t) -> {
            double crack = Math.abs(Math.sin(s * 9 + t * 4)) < 0.05 || Math.abs(Math.sin(t * 11 - s * 3)) < 0.04 ? 0.22 : 0;
            double edge = Math.min(Math.min(s, t), Math.min(1 - s, 1 - t));
            double swirl = 0.5 + 0.5 * Math.sin(s * 12 - t * 14);
            double base = 0.44 + 0.2 * (1 - s) - 0.1 * t + crack + 0.08 * swirl;
            if (edge < 0.07) {
                return Paint.ramp(FROST, 0.94);
            }
            return Paint.ramp(FROST, base);
        });

        // Латунные скобы-зажимы по углам с заклепками
        for (double[] st : new double[][]{{0.1, 0.1}, {0.9, 0.1}, {0.1, 0.9}, {0.9, 0.9}}) {
            double[] c = onPlate(st[0], st[1]);
            p.slab(c[0] - 0.6, c[1] - 0.6, c[0] + 0.6, c[1] + 0.6, BRASS, 3.4, 1);
            p.rivet(c[0], c[1], 0.28, BRASS);
        }

        // Сосульки по нижнему периметру
        double[] b0 = onPlate(0.2, 0.95);
        double[] b1 = onPlate(0.5, 0.95);
        double[] b2 = onPlate(0.8, 0.95);
        for (double[] pt : new double[][]{{b0[0], b0[1] + 1.4, 1.2}, {b1[0], b1[1] + 1.4, 1.8}, {b2[0], b2[1] + 1.4, 1.3}}) {
            p.line(pt[0], pt[1], pt[0], pt[1] + pt[2], 0.4, argb(230, 0xA8E4FC));
            p.sparkle(pt[0], pt[1] + pt[2], rgb(0xFFFFFF));
        }

        // Светящаяся крио-снежинка и руны по центру
        double[] c = onPlate(0.5, 0.5);
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI / 3;
            double dx = Math.cos(a) * 2.4, dy = Math.sin(a) * 1.3;
            p.line(c[0] - dx, c[1] - dy, c[0] + dx, c[1] + dy, 0.35, rgb(0xFFFFFF));
        }
        p.disc(c[0], c[1], 0.5, at(CYAN, 5.4));
        p.glowRect(c[0] - 0.6, c[1] - 0.5, c[0] + 0.6, c[1] + 0.5, 1.2, at(CYAN, 4.4), 95);
        p.sparkle(c[0] - 3.4, c[1] - 0.6, rgb(0xFFFFFF));
        p.sparkle(c[0] + 2.8, c[1] + 1.2, rgb(0xFFFFFF));
        return p;
    }

    // ============================================================================================ секретные материалы
    /** Схлопнувшаяся плазма Визера: изометрическая незеритовая клетка, крио-зажимы, фиолетовый войд-вихрь и электрические молнии. */
    static Paint witherPlasma() {
        Paint p = canvas();
        double cx = 8, cy = 8, r = 6.4;
        p.glowRect(cx - 4.5, cy - 4.5, cx + 4.5, cy + 4.5, 3.6, at(VIOLET, 4.4), 90);

        // Внешнее силовое поле и фиолетовый вихрь
        p.discFn(cx, cy, r, (u, v) -> {
            double dx = u - cx, dy = v - cy, d = Math.hypot(dx, dy) / r, a = Math.atan2(dy, dx);
            double sw = 0.5 + 0.5 * Math.sin(a * 4 + d * 13);
            double t = 0.06 + 0.58 * d * d + 0.16 * sw * d;
            int c = Paint.ramp(VIOLET, t);
            if (d > 0.88) {
                c = Paint.mix(c, at(VIOLET, 5.8), (d - 0.88) / 0.12);
            }
            return c;
        });

        // Незеритово-криогенные силовые зажимы клетки
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            double kx = cx + Math.cos(a) * (r - 0.8), ky = cy + Math.sin(a) * (r - 0.8);
            p.slab(kx - 0.9, ky - 0.9, kx + 0.9, ky + 0.9, NETHERITE, 2.8, 1);
            p.rivet(kx, ky, 0.32, BRASS);
            // Крио-наросты на зажимах
            p.line(kx, ky, cx + Math.cos(a) * (r + 0.8), cy + Math.sin(a) * (r + 0.8), 0.45, at(CYAN, 4.8));
        }

        // Электрические дуги крио-плазмы (бирюзовые молнии)
        p.line(cx - 2.8, cy - 1.0, cx - 0.8, cy - 2.2, 0.3, at(CYAN, 5.5));
        p.line(cx - 0.8, cy - 2.2, cx + 1.2, cy - 1.4, 0.3, at(CYAN, 5.5));
        p.line(cx - 1.4, cy + 1.8, cx + 0.8, cy + 2.4, 0.3, at(CYAN, 5.5));
        p.glowRect(cx - 2.5, cy - 2.5, cx + 2.5, cy + 2.5, 1.2, at(CYAN, 4.5), 80);

        // Силуэт черепа Визера в глубине плазмы
        p.rect(cx - 1.6, cy - 1.5, cx + 1.6, cy + 0.8, rgb(0x040106));
        p.rect(cx - 1.1, cy + 0.8, cx + 1.1, cy + 1.6, rgb(0x040106));
        // Светящиеся фиолетовые глазницы
        p.rect(cx - 1.1, cy - 0.8, cx - 0.3, cy - 0.1, at(VIOLET, 6.0));
        p.rect(cx + 0.3, cy - 0.8, cx + 1.1, cy - 0.1, at(VIOLET, 6.0));
        p.glowRect(cx - 1.1, cy - 0.8, cx + 1.1, cy - 0.1, 0.8, at(VIOLET, 5.2), 140);
        // Зубы
        p.rect(cx - 0.8, cy + 1.0, cx - 0.5, cy + 1.6, at(VIOLET, 3.0));
        p.rect(cx + 0.5, cy + 1.0, cx + 0.8, cy + 1.6, at(VIOLET, 3.0));

        // Искры
        Random rr = new Random(2501);
        for (int i = 0; i < 8; i++) {
            double a = rr.nextDouble() * Math.PI * 2, d = r + 0.3 + rr.nextDouble() * 1.2;
            p.sparkle(cx + Math.cos(a) * d, cy + Math.sin(a) * d, rgb(0xECE0FF));
        }
        return p;
    }

    /** Межпространственная оккультная матрица: изометрический гиперкуб с латунными кольцами кардана, звездами внутри, фиолетовыми и бирюзовыми рунами. */
    static Paint occultMatrix() {
        Paint p = canvas();
        double cx = 8, cy = 8;
        p.glowRect(cx - 3.5, cy - 3.5, cx + 3.5, cy + 3.5, 3.4, at(VIOLET, 4.0), 80);

        // 4 внешних луча пространственного разлома
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            double ex = cx + Math.cos(a) * 7.6, ey = cy + Math.sin(a) * 7.6;
            double px = -Math.sin(a) * 1.0, py = Math.cos(a) * 1.0;
            p.poly(new double[]{cx + px * 1.4, ex, cx - px * 1.4}, new double[]{cy + py * 1.4, ey, cy - py * 1.4}, at(GOLD, 3.2));
            p.line(cx, cy, ex, ey, 0.3, at(GOLD, 5.8));
        }

        // Латунное кольцо кардана с шестернями
        p.disc(cx, cy, 6.2, at(GOLD, 1.4));
        p.sphere(cx, cy, 6.0, GOLD, 0.38, 0.95);
        p.disc(cx, cy, 4.6, at(GOLD, 1.1));
        p.disc(cx, cy, 4.4, rgb(0x090514));

        // Оккультные руны на кольце: чередуются фиолетовый и бирюзовый
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            double rx = cx + Math.cos(a) * 5.25, ry = cy + Math.sin(a) * 5.25;
            p.disc(rx, ry, 0.9, at(GOLD, 0.7));
            int runeColor = (i % 2 == 0) ? at(VIOLET, 5.8) : at(CYAN, 5.5);
            p.rune(rx - 0.45, ry - 0.7, 1.4, i * 6 + 1, runeColor, 0);
            p.glowRect(rx - 0.6, ry - 0.8, rx + 0.6, ry + 0.8, 0.5, runeColor, 90);
        }

        // Всевидящее космическое око в центре разлома
        p.polyFn(new double[]{cx - 3.8, cx - 1.8, cx + 1.8, cx + 3.8, cx + 1.8, cx - 1.8},
                new double[]{cy, cy - 2.1, cy - 2.1, cy, cy + 2.1, cy + 2.1}, (u, v) -> Paint.ramp(CREAM, 0.95 - 0.25 * Math.abs(v - cy) / 2.1));
        p.discFn(cx, cy, 1.8, (u, v) -> {
            double d = Math.hypot(u - cx, v - cy) / 1.8, a = Math.atan2(v - cy, u - cx);
            return Paint.ramp(VIOLET, 0.35 + 0.4 * d + 0.08 * Math.sin(a * 9));
        });
        p.disc(cx, cy, 0.8, rgb(0x050208));
        p.glowRect(cx - 1.2, cy - 1.2, cx + 1.2, cy + 1.2, 0.6, at(VIOLET, 5.0), 90);
        p.disc(cx - 0.6, cy - 0.7, 0.35, rgb(0xFFFFFF));

        // Латунные орбитальные рамки
        p.line(cx - 3.8, cy, cx - 1.8, cy - 2.1, 0.28, at(GOLD, 2.2));
        p.line(cx - 1.8, cy - 2.1, cx + 1.8, cy - 2.1, 0.28, at(GOLD, 2.2));
        p.line(cx + 1.8, cy - 2.1, cx + 3.8, cy, 0.28, at(GOLD, 2.2));
        return p;
    }

    // ============================================================================================ оккультный гримуар
    /** Гримуар: тисненая темная кожа, медные уголки с патиной, золоченый обрез, круглый глаз-шестерня голема и светящиеся руны. */
    static Paint guideBook() {
        Paint p = canvas();
        shadow(p, 8.6, 14.6, 6.6, 1.1);
        double x0 = 2.6, x1 = 13.6, y0 = 1.4, y1 = 14.2;

        // Золоченый обрез страниц справа и снизу (толщина книги)
        p.fill(x1, y0 + 0.6, x1 + 1.0, y1 + 0.4, (u, v) -> ((int) (v * 4) % 2 == 0) ? Paint.ramp(GOLD, 0.85) : Paint.ramp(CREAM, 0.65));
        p.fill(x0 + 0.6, y1, x1 + 1.0, y1 + 0.6, (u, v) -> ((int) (u * 4) % 2 == 0) ? Paint.ramp(GOLD, 0.75) : Paint.ramp(CREAM, 0.55));

        // Кожаный переплет и корешок
        p.slab(x0, y0, x1, y1, LEATHER, 3.2, 1);
        p.grain(x0, y0, x1, y1, 0.08, 2601);
        p.slab(x0, y0, x0 + 1.6, y1, LEATHER, 2.0, 1);

        // Шестерни и латунные ребра корешка
        for (double y : new double[]{y0 + 2.0, y1 - 2.6}) {
            p.rect(x0, y, x0 + 1.6, y + 0.5, at(BRASS, 3.8));
        }
        p.gearStatic(x0 + 0.8, (y0 + y1) / 2, 0.9, 6, 0.15, BRASS, 4.0);

        // Тисненая рамка переплета
        p.frame(x0 + 2.2, y0 + 1.0, x1 - 0.9, y1 - 1.0, 0.25, at(LEATHER, 1.2));
        p.frame(x0 + 2.5, y0 + 1.3, x1 - 1.2, y1 - 1.3, 0.14, at(BRASS, 3.2));

        // Центральная круглая эмблема: шестерня-око голема
        double gx = (x0 + 1.6 + x1) / 2 + 0.2, gy = (y0 + y1) / 2 - 0.5;
        p.gearStatic(gx, gy, 3.0, 8, 0.35, COPPER, 3.6);
        p.disc(gx, gy, 2.2, at(BRASS, 3.8));
        p.disc(gx, gy, 1.6, at(IRON, 0.4));

        // Светящееся всевидящее око голема в центре
        p.polyFn(new double[]{gx - 1.4, gx, gx + 1.4, gx}, new double[]{gy, gy - 0.8, gy, gy + 0.8},
                (u, v) -> at(CYAN, 5.2));
        p.disc(gx, gy, 0.45, at(CYAN, 6.0));
        p.glowRect(gx - 1.4, gy - 0.8, gx + 1.4, gy + 0.8, 0.8, at(CYAN, 4.2), 110);

        // Светящиеся руны по периметру обложки
        for (int i = 0; i < 3; i++) {
            p.rune(x0 + 2.8 + i * 2.2, y0 + 1.8, 1.1, i * 3 + 4, at(CYAN, 5.0), 0);
            p.rune(x0 + 2.8 + i * 2.2, y1 - 2.6, 1.1, i * 4 + 7, at(CYAN, 5.0), 0);
        }
        p.glowRect(x0 + 2.5, y0 + 1.6, x1 - 1.5, y0 + 2.8, 0.3, at(CYAN, 3.6), 60);
        p.glowRect(x0 + 2.5, y1 - 2.8, x1 - 1.5, y1 - 1.6, 0.3, at(CYAN, 3.6), 60);

        // Окисленные медные уголки с патиной и латунными заклепками
        for (double[] c : new double[][]{{x1 - 1.6, y0}, {x1 - 1.6, y1 - 1.6}}) {
            p.slab(c[0], c[1], c[0] + 1.6, c[1] + 1.6, COPPER, 3.4, 1);
            p.disc(c[0] + 0.8, c[1] + 0.8, 0.5, at(VERDIGRIS, 4.4));
            p.rivet(c[0] + 0.8, c[1] + 0.8, 0.3, BRASS);
        }

        // Морозный крио-замок-защелка на правом обрезе
        p.slab(x1 - 0.4, 6.8, x1 + 1.4, 8.8, FROST, 4.0, 1);
        p.rect(x1 + 0.2, 7.4, x1 + 0.8, 8.2, at(CYAN, 5.4));
        p.glowRect(x1 + 0.2, 7.4, x1 + 0.8, 8.2, 0.4, at(CYAN, 4.0), 90);

        // Шелковая закладка-лента снизу
        p.rect(x0 + 5.6, y1, x0 + 6.4, y1 + 1.8, at(Gen3.RED, 3.6));
        p.poly(new double[]{x0 + 5.6, x0 + 6.0, x0 + 6.4}, new double[]{y1 + 1.9, y1 + 1.3, y1 + 1.9}, 0);

        // Иней на верхнем крае
        p.frostLine(x0 + 1.6, x1, y0, 0.5, 2602);
        return p;
    }

    // ============================================================================================ заглушка трубы
    /** Заглушка трубы: восьмигранный латунный корпус с патиной, аналоговый манометр, рычаг клапана и светящееся неоновое кольцо уплотнителя. */
    static Paint pipePlug() {
        Paint p = canvas();
        shadow(p, 8.0, 14.4, 6.2, 1.2);
        double cx = 8.0, cy = 8.2, r = 6.2;

        // Восьмигранный латунный фланец с патиной
        double[] xs = new double[8], ys = new double[8], xb = new double[8], yb = new double[8];
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 8 + i * Math.PI / 4;
            xs[i] = cx + Math.cos(a) * r;
            ys[i] = cy + Math.sin(a) * r * 0.88;
            xb[i] = xs[i];
            yb[i] = ys[i] + 1.3;
        }
        p.poly(xb, yb, at(BRASS, 1.6));
        p.polyFn(xs, ys, (u, v) -> {
            double n = 0.5 + 0.5 * Math.sin(u * 5.0 + v * 6.0);
            double base = 0.58 + 0.22 * (-(u - cx) * 0.6 - (v - cy) * 0.8) / r;
            if (n > 0.68) {
                return at(VERDIGRIS, 4.2);
            }
            return Paint.ramp(BRASS, base);
        });

        // Руны на фасках фланца
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 8 + i * Math.PI / 4;
            double rx = cx + Math.cos(a) * 4.9 - 0.4;
            double ry = cy + Math.sin(a) * 4.3 - 0.5;
            p.rune(rx, ry, 0.8, i * 3 + 1, at(BRASS, 1.2), at(BRASS, 4.6));
        }

        // Темная выемка седла
        p.disc(cx, cy, 3.8, rgb(0x141a20));
        p.ring(cx, cy, 3.4, 3.8, rgb(0x0a0e12));

        // Ступенчатая резьбовая пробка из меди с патиной
        p.discFn(cx, cy, 3.2, (u, v) -> {
            double angle = Math.atan2(v - cy, u - cx);
            double shine = 0.5 + 0.5 * Math.cos(angle - Math.PI * 0.75);
            return copperPatina(0.48 + shine * 0.32, 0.5 + 0.5 * Math.sin(u * 10 + v * 8));
        });
        p.ring(cx, cy, 2.5, 2.8, at(COPPER, 1.4));

        p.discFn(cx, cy, 2.3, (u, v) -> {
            double angle = Math.atan2(v - cy, u - cx);
            double shine = 0.5 + 0.5 * Math.cos(angle - Math.PI * 0.75);
            return copperPatina(0.55 + shine * 0.35, 0.5 + 0.5 * Math.cos(u * 12 + v * 9));
        });
        p.ring(cx, cy, 1.6, 1.9, at(COPPER, 1.4));

        // Светящееся неоновое кольцо герметичного крио-уплотнения у основания
        p.ring(cx, cy, 3.7, 4.3, at(CYAN, 5.2));
        p.glowRect(cx - 4.5, cy - 3.8, cx + 4.5, cy + 3.8, 0.8, at(CYAN, 4.2), 100);

        // Верхний рычаг сброса давления / вентиль
        p.slab(cx - 1.8, cy - 7.0, cx + 1.8, cy - 5.8, COPPER, 3.6, 1);
        p.rect(cx - 0.6, cy - 5.8, cx + 0.6, cy - 4.8, at(BRASS, 3.2));
        p.rivet(cx, cy - 6.4, 0.28, BRASS);

        // Аналоговый манометр справа сверху (с живой дрожащей стрелкой в красной зоне!)
        p.gauge(cx + 2.8, cy - 3.6, 2.1, 145, at(Gen3.RED, 4.5));

        // Шестерня механизма слева сверху
        p.gear(cx - 3.0, cy - 3.8, 1.8, 8, 0.22, BRASS, 3.4);
        p.disc(cx - 3.0, cy - 3.8, 0.5, at(IRON, 0.2));

        // Кристаллические сосульки снизу
        for (double[] ic : new double[][]{{cx - 2.6, cy + 5.2, 1.6}, {cx, cy + 5.5, 2.2}, {cx + 2.6, cy + 5.2, 1.4}}) {
            p.line(ic[0], ic[1], ic[0], ic[1] + ic[2], 0.5, argb(230, 0x90DCF8));
            p.sparkle(ic[0], ic[1] + ic[2], argb(255, 0xFFFFFF));
        }

        // Заклёпки по углам
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            p.rivet(cx + Math.cos(a) * 5.4, cy + Math.sin(a) * 4.7, 0.45, BRASS);
        }
        return p;
    }

    /**
     * Кузнечный молот по концепту: тяжёлая головка из морозного железа (бойки обросли льдом, сосульки), медные бандажи с патиной
     * и заклёпками, латунные пояса, на щеке — медная маска голема с бирюзовыми глазами и светящиеся руны; рукоять из дерева,
     * обмотанная кожей, с латунным навершием. Рисуется по диагонали, как инструменты Minecraft (головка — справа вверху).
     */
    static Paint forgeHammer() {
        Paint p = canvas();
        // рукоять: от навершия (внизу слева) к головке
        double nx = 0.7071, ny = 0.7071, dx = 0.7071, dy = -0.7071;
        double hx0 = 1.7, hy0 = 14.3, hx1 = 8.8, hy1 = 7.2, hw = 0.85;
        p.polyFn(new double[]{hx0 + nx * hw, hx1 + nx * hw, hx1 - nx * hw, hx0 - nx * hw},
                new double[]{hy0 + ny * hw, hy1 + ny * hw, hy1 - ny * hw, hy0 - ny * hw}, (u, v) -> {
                    double along = ((u - hx0) * dx + (v - hy0) * dy) / Math.hypot(hx1 - hx0, hy1 - hy0);
                    double across = ((u - hx0) * nx + (v - hy0) * ny) / hw;
                    double shade = 3.2 - across * 1.2;
                    // кожаная обмотка — косые полосы, выше неё — голое дерево
                    if (along > 0.18 && along < 0.72) {
                        double wrap = (along * 26 + across * 0.8) % 2.0;
                        return at(LEATHER, shade + (wrap < 0.35 ? -1.4 : 0));
                    }
                    return at(BRONZE, shade - 0.6 + 0.3 * Math.sin(along * 40));
                });
        // навершие и манжета под головкой
        p.disc(hx0 + 0.2, hy0 - 0.2, 1.25, at(BRASS, 2.0));
        p.disc(hx0 + 0.2, hy0 - 0.2, 0.9, at(BRASS, 4.0));
        p.rivet(hx0 + 0.2, hy0 - 0.2, 0.4, COPPER);
        p.polyFn(new double[]{7.0 + nx * 1.2, 8.6 + nx * 1.2, 8.6 - nx * 1.2, 7.0 - nx * 1.2},
                new double[]{9.0 + ny * 1.2, 7.4 + ny * 1.2, 7.4 - ny * 1.2, 9.0 - ny * 1.2},
                (u, v) -> at(COPPER, 3.0 - ((u - 7.8) * nx + (v - 8.2) * ny)));

        // головка: длинная ось n (вниз вправо), ширина вдоль рукояти d
        double cx = 10.0, cy = 6.0, half = 5.0, wid = 2.6;
        double[] xs = new double[4], ys = new double[4];
        int[][] sg = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            xs[i] = cx + sg[i][0] * nx * half + sg[i][1] * dx * wid;
            ys[i] = cy + sg[i][0] * ny * half + sg[i][1] * dy * wid;
        }
        Random r = new Random(2701);
        double[] noise = new double[128];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = r.nextDouble();
        }
        p.polyFn(xs, ys, (u, v) -> {
            double s = ((u - cx) * nx + (v - cy) * ny) / half;
            double t = ((u - cx) * dx + (v - cy) * dy) / wid;
            double light = 0.55 + 0.35 * t;
            double n = noise[(int) ((s + 1) * 7.9) * 8 + (int) ((t + 1) * 3.95)];
            double as = Math.abs(s);
            if (Math.abs(t) > 0.86) {
                return Paint.ramp(IRON, 0.12);
            }
            if (as > 0.8) {
                // обледеневший боёк
                return Paint.ramp(FROST, 0.55 + 0.35 * light + 0.15 * n);
            }
            if (as > 0.63) {
                return copperPatina(0.35 + 0.45 * light, n);
            }
            if (as > 0.55) {
                return Paint.ramp(BRASS, 0.4 + 0.5 * light);
            }
            return Paint.ramp(IRON, 0.25 + 0.45 * light + 0.06 * n);
        });
        // заклёпки на медных бандажах
        for (int side : new int[]{-1, 1}) {
            for (int k : new int[]{-1, 1}) {
                double s = side * 0.71 * half, t = k * 0.55 * wid;
                p.rivet(cx + s * nx + t * dx, cy + s * ny + t * dy, 0.32, BRASS);
            }
        }
        // медная маска голема на щеке и светящиеся руны по бокам от неё
        double mx = cx - 0.2 * nx + 0.1 * dx, my = cy - 0.2 * ny + 0.1 * dy;
        p.rect(mx - 1.3, my - 1.4, mx + 1.3, my + 1.4, at(COPPER, 1.0));
        p.polyFn(new double[]{mx - 1.1, mx + 1.1, mx + 1.1, mx - 1.1}, new double[]{my - 1.2, my - 1.2, my + 1.2, my + 1.2},
                (u, v) -> Paint.ramp(COPPER, 0.5 + 0.3 * (my - v) / 1.2));
        p.rect(mx - 1.1, my - 1.2, mx + 1.1, my - 0.75, at(COPPER, 4.6));
        p.glowRect(mx - 0.8, my - 0.45, mx - 0.25, my + 0.05, 0.05, at(CYAN, 6.2), 255);
        p.glowRect(mx + 0.25, my - 0.45, mx + 0.8, my + 0.05, 0.05, at(CYAN, 6.2), 255);
        p.rect(mx - 0.3, my + 0.35, mx + 0.3, my + 1.0, at(COPPER, 1.6));
        // светящиеся руны — короткие бирюзовые насечки вдоль щеки между маской и бандажами
        for (int k : new int[]{-1, 1}) {
            for (int j = 0; j < 2; j++) {
                double s = k * (2.0 + j * 0.55), t = j == 0 ? 0.5 : -0.5;
                double ux = cx + s * nx + t * dx, uy = cy + s * ny + t * dy;
                p.glowRect(ux - 0.2, uy - 0.2, ux + 0.2, uy + 0.2, 0.05, at(CYAN, 5.8), 255);
            }
        }
        // латунная пластина с манометром на верхней грани
        double gx = cx + 1.4 * nx + 1.7 * dx, gy = cy + 1.4 * ny + 1.7 * dy;
        p.disc(gx, gy, 0.75, at(BRASS, 2.0));
        p.disc(gx, gy, 0.55, rgb(0xF2EEE2));
        p.line(gx, gy, gx + 0.35, gy - 0.3, 0.14, rgb(0xB81410));
        // сосульки под бойками
        for (int side : new int[]{-1, 1}) {
            double bx = cx + side * 0.86 * half * nx - 1.2 * dx, by = cy + side * 0.86 * half * ny - 1.2 * dy;
            p.line(bx, by, bx - 0.2, by + 1.3, 0.35, argb(230, 0xA8E6F8));
            p.sparkle(bx - 0.2, by + 1.3, argb(255, 0xFFFFFF));
        }
        return p;
    }

    static void all() throws Exception {
        forgeHammer().save(itemTex("forge_hammer"));
        Forge2.write(Forge2.RES.resolve("models/item/forge_hammer.json"),
                "{\n  \"parent\": \"minecraft:item/handheld\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/forge_hammer\"\n  }\n}\n");
        pipePlug().save(itemTex("pipe_plug"));
        Forge2.write(Forge2.RES.resolve("models/item/pipe_plug.json"),
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/pipe_plug\"\n  }\n}\n");
        String[] tiers = {"1k", "4k", "16k", "64k", "256k"};
        for (int i = 0; i < 5; i++) {
            copperDisk(i).save(itemTex("copper_disk_" + tiers[i]));
            fluidDisk(i).save(itemTex("fluid_disk_" + tiers[i]));
        }
        punchCard().save(itemTex("copper_punch_card"));
        ironCore().save(itemTex("flattened_iron_core"));
        copperMatrix().save(itemTex("flattened_copper_matrix"));
        cryoSubstrate().save(itemTex("flattened_cryo_substrate"));
        witherPlasma().save(itemTex("imploded_wither_plasma"));
        occultMatrix().save(itemTex("interdimensional_occult_matrix"));
        guideBook().save(itemTex("guide_book"));
    }
}
