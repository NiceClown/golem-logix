import java.util.ArrayList;
import java.util.List;

/**
 * Вечный Морозный Резонатор по концепту: ржаво-медный корпус в латунной раме с инеем, на фасаде большой латунный циферблат с
 * бирюзовым светящимся экраном (стрелка бежит по кругу), на корпусе — стеклянная камера на латунных стойках с рунной площадкой
 * внизу (снежный вихрь в ней рисует отрисовщик, он же поднимается через трубу-вытяжку на крыше), по бокам — обмёрзшие белые
 * трубы петлёй из корпуса в камеру, сосульки по краям. Фасад смотрит на игрока при установке.
 */
final class Resonator {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, COPPER = Paint.COPPER, IRON = Paint.IRON, CYAN = Paint.CYAN, FROST = Paint.FROST;

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    static int rgb(int c) {
        return Paint.rgb(c);
    }

    static int argb(int a, int c) {
        return Paint.argb(a, c);
    }

    /** Ржаво-медная плита в латунной раме: заклёпки, потёки зелёной патины, иней по верхнему краю. */
    static Paint body(long seed, boolean gauges) {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.6, 1);
        p.slab(1.1, 1.1, 14.9, 14.9, COPPER, 2.5, 1);
        p.streaks(1.2, 1.2, 14.8, 14.8, 0.05, seed, true);
        p.grain(1.2, 1.2, 14.8, 14.8, 0.04, seed + 1);
        // Аккуратные литые латунные заклёпки без посторонних масляных пятен и потёков
        for (double x : new double[]{2.2, 13.8}) {
            for (double y : new double[]{2.2, 8.0, 13.8}) {
                p.rivet(x, y, 0.5, BRASS);
            }
        }
        if (gauges) {
            // два малых манометра и бирюзовая табличка (как на правом боку концепта)
            p.gauge(8, 5.0, 2.2, 60, rgb(0xb02020));
            p.gauge(8, 10.6, 1.8, 140, rgb(0xb02020));
            p.glowRect(6.2, 13.0, 9.8, 14.0, 0.4, at(CYAN, 3.4), 60);
        }
        p.frostLine(0, 16, 0, 0.7, seed + 2);
        return p;
    }

    /** Верх: медная плита в латунной окантовке, иней, рунная насечка. */
    static Paint top() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 2.4, 1);
        p.slab(1.0, 1.0, 15.0, 15.0, COPPER, 2.2, 1);
        p.grain(1.0, 1.0, 15.0, 15.0, 0.07, 9201);
        for (int i = 0; i < 6; i++) {
            p.rect(2.0, 2.5 + i * 2.0, 14.0, 2.9 + i * 2.0, at(COPPER, 1.2));
        }
        p.frostLine(0, 16, 0, 0.9, 9202);
        p.frostLine(0, 16, 15.1, 0.6, 9203);
        return p;
    }

    /**
     * Циферблат: латунное зубчатое кольцо, бирюзовый светящийся экран с делениями и руным кольцом, стрелка бежит по кругу.
     * Вне круга — прозрачно (кругу не нужен квадратный корпус).
     */
    static Paint dial() {
        Paint p = new Paint(16, 16, K);
        p.animFrames = 32;
        p.frameTime = 3;
        // зубцы и кольцо
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            p.disc(8 + Math.cos(a) * 7.1, 8 + Math.sin(a) * 7.1, 0.75, at(BRASS, 2.4));
        }
        p.disc(8, 8, 7.0, at(BRASS, 2.0));
        p.ring(8, 8, 5.9, 6.9, at(BRASS, 3.6));
        p.ring(8, 8, 6.55, 6.75, at(BRASS, 4.8));
        p.disc(8, 8, 5.9, at(IRON, 0.4));
        // светящийся экран
        p.glowRect(3.2, 3.2, 12.8, 12.8, 0.4, at(CYAN, 2.2), 40);
        p.discFn(8, 8, 5.5, (u, v) -> {
            double d = Math.hypot(u - 8, v - 8) / 5.5;
            return Paint.ramp(CYAN, 0.55 + 0.25 * (1 - d));
        });
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            p.line(8 + Math.cos(a) * 4.1, 8 + Math.sin(a) * 4.1, 8 + Math.cos(a) * 5.1, 8 + Math.sin(a) * 5.1, i % 3 == 0 ? 0.42 : 0.25,
                    at(CYAN, 6.4));
        }
        p.ring(8, 8, 2.7, 2.95, at(CYAN, 6.2));
        p.ring(8, 8, 1.2, 1.4, at(CYAN, 6.0));
        // стрелка (живая): бежит по кругу, тёмная на светлом экране
        p.animate(2.5, 2.5, 13.5, 13.5, (q, t) -> {
            double a = t * Math.PI * 2 - Math.PI / 2;
            q.line(8, 8, 8 + Math.cos(a) * 4.6, 8 + Math.sin(a) * 4.6, 0.45, at(IRON, 0.3));
            q.line(8, 8, 8 - Math.cos(a) * 1.2, 8 - Math.sin(a) * 1.2, 0.45, at(IRON, 0.3));
        });
        p.disc(8, 8, 0.75, at(BRASS, 4.2));
        p.line(3.2, 5.2, 5.6, 3.0, 0.3, argb(110, 0xFFFFFF));
        return p;
    }

    /** Морозное стекло камеры: бледно-бирюзовое, иней у краёв (гуще внизу), блики. */
    static Paint glass() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> {
            double edge = Math.min(Math.min(u, 16 - u), Math.min(v, 16 - v));
            double frost = Math.max(0, 1 - edge / 2.2) + Math.max(0, (v - 12) / 4) * 0.6;
            int a = (int) Math.min(220, 38 + frost * 150);
            return argb(a, frost > 0.35 ? 0xE6F6FF : 0xA8E4F4);
        });
        java.util.Random r = new java.util.Random(9301);
        for (int i = 0; i < 30; i++) {
            double x = r.nextDouble() * 16, y = r.nextDouble() < 0.6 ? 13 + r.nextDouble() * 3 : r.nextDouble() * 2;
            p.sparkle(x, y, argb(220, 0xFFFFFF));
        }
        p.line(3.0, 12.0, 6.5, 2.5, 0.28, argb(90, 0xFFFFFF));
        p.line(4.4, 12.4, 7.8, 3.0, 0.16, argb(70, 0xFFFFFF));
        return p;
    }

    /** Рунная площадка на дне камеры: тёмный круг, светящиеся кольца и руны. */
    static Paint pad() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, IRON, 0.6, 1);
        p.disc(8, 8, 7.4, at(BRASS, 2.0));
        p.disc(8, 8, 6.9, at(IRON, 0.3));
        p.ring(8, 8, 6.0, 6.5, at(CYAN, 5.4));
        p.ring(8, 8, 3.2, 3.55, at(CYAN, 5.0));
        p.disc(8, 8, 1.4, at(CYAN, 5.8));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            p.rune(8 + Math.cos(a) * 4.7 - 0.5, 8 + Math.sin(a) * 4.7 - 0.6, 1.2, i * 3 + 2, at(CYAN, 5.6), at(CYAN, 3.0));
        }
        return p;
    }

    /** Хладагентная труба: состаренная промышленная медь/бронза с латунными муфтами, лазурным капилляром и легким инеем. */
    static Paint pipe() {
        Paint p = new Paint(16, 16, K);
        // Базовое тело трубы: медно-бронзовый цилиндр с глубоким светотеневым градиентом
        p.fill(0, 0, 16, 16, (u, v) -> {
            // u поперек трубы (0..16): по центру (u ≈ 7.5) светлый металлический блик, к краям глубокая тень
            double cu = Math.abs(u - 7.5) / 7.5;
            double n = Math.sin(u * 2.3 + v * 0.7) * 0.08 + Math.sin(v * 3.1) * 0.04;
            double luma = Math.max(0.0, Math.min(1.0, 1.0 - Math.pow(cu, 1.8) + n));
            int copper = Paint.ramp(COPPER, 1.8 + luma * 3.8);
            if (cu > 0.82) {
                copper = Paint.mix(copper, 0xFF180A05, (cu - 0.82) / 0.18);
            }
            return copper;
        });

        // Продольные металлические волокна шлифовки трубы
        p.streaks(0, 0, 16, 16, 0.06, 9402, false);

        // Центральный углубленный капилляр с активным криогенным хладагентом
        for (double v = 0; v < 16; v += 0.25) {
            p.rect(7.0, v, 9.0, v + 0.25, rgb(0x0C161E)); // тёмный паз
            p.rect(7.4, v, 8.6, v + 0.25, at(CYAN, 4.4));  // лазурный хладагент
            p.rect(7.7, v, 8.3, v + 0.25, at(CYAN, 5.8));  // яркий световой фокус
        }

        // Мощные латунные муфты-фланцы с болтами через каждые 8 юнитов
        for (double y : new double[]{2.0, 10.0}) {
            p.slab(0, y, 16, y + 2.4, BRASS, 3.2, 1);
            p.rect(0, y, 16, y + 0.4, at(BRASS, 5.0));
            p.rect(0, y + 2.0, 16, y + 2.4, at(BRASS, 1.4));
            for (double x : new double[]{2.0, 6.0, 10.0, 14.0}) {
                p.rivet(x, y + 1.2, 0.42, BRASS);
            }
            // Кристаллическая изморозь и иней, собирающиеся на стыках хомута
            p.frostLine(0, 16, y - 0.4, 0.45, (long) (y * 100));
            p.frostLine(0, 16, y + 2.4, 0.35, (long) (y * 200));
        }

        // Редкие ледяные микро-кристаллы на холодном металле
        java.util.Random r = new java.util.Random(9401);
        for (int i = 0; i < 8; i++) {
            double rx = 1.0 + r.nextDouble() * 14.0, ry = r.nextDouble() * 16.0;
            p.sparkle(rx, ry, argb(210, 0xCEF0FF));
        }
        return p;
    }

    /** Вихрь (текстура отрисовщика): мягкая бирюзовая лента с яркой серединой и снежинками, прозрачная по краям. */
    static Paint vortex() {
        Paint p = new Paint(8, 32, K);
        p.fill(0, 0, 8, 32, (u, v) -> {
            double across = 1 - Math.abs(u - 4) / 4;
            double along = 0.65 + 0.35 * Math.sin(v * 0.9);
            // отрисовщик складывает цвета («глаза»): прозрачное должно быть чёрным, поэтому цвет умножен на непрозрачность
            double a = Math.pow(Math.max(0, across), 1.6) * 0.9 * along;
            int c = Paint.ramp(CYAN, 4.4 + across * 1.8);
            int rr = (int) (((c >> 16) & 0xFF) * a), gg = (int) (((c >> 8) & 0xFF) * a), bb = (int) ((c & 0xFF) * a);
            return argb(255, (rr << 16) | (gg << 8) | bb);
        });
        java.util.Random r = new java.util.Random(9501);
        for (int i = 0; i < 18; i++) {
            p.sparkle(1.5 + r.nextDouble() * 5, r.nextDouble() * 32, argb(255, 0xFFFFFF));
        }
        return p;
    }

    static void all() throws Exception {
        body(9101, false).save(Forge2.tex("resonator_body"));
        body(9111, true).save(Forge2.tex("resonator_gauges"));
        top().save(Forge2.tex("resonator_top"));
        dial().save(Forge2.tex("resonator_dial"));
        glass().save(Forge2.tex("resonator_glass"));
        pad().save(Forge2.tex("resonator_pad"));
        pipe().save(Forge2.tex("resonator_pipe"));
        vortex().save(Forge2.entityTex("resonator_vortex"));
        model();
    }

    /** Петля хладагентной трубы с одного бока: x0 — внешняя стенка блока (0 или 16), dir — наружу (-1 или +1). */
    static void pipeLoop(List<Models.El> l, double x0, double dir) {
        // d — расстояние наружу от грани блока (отрицательное — внутрь); в модели x растёт к востоку
        java.util.function.BiFunction<Double, Double, double[]> span = (d0, d1) -> {
            double a = x0 + dir * d0, b = x0 + dir * d1;
            return new double[]{Math.min(a, b), Math.max(a, b)};
        };
        double[] s;
        // петля: выход из корпуса, вверх по боку, вход в камеру
        s = span.apply(-0.5, 3.8);
        l.add(box(s[0], 3.2, 6.6, s[1], 5.8, 9.4).all("#pipe"));
        // стояк чуть толще отрезков, чтобы грани не совпадали
        s = span.apply(1.15, 3.9);
        l.add(box(s[0], 3.1, 6.5, s[1], 13.7, 9.5).all("#pipe"));
        s = span.apply(-1.6, 3.8);
        l.add(box(s[0], 11.0, 6.6, s[1], 13.6, 9.4).all("#pipe"));
        // латунные фланцы у корпуса и у камеры, латунные хомуты на стояке
        s = span.apply(-0.1, 0.7);
        l.add(box(s[0], 2.8, 6.2, s[1], 6.2, 9.8).all("#brass"));
        s = span.apply(-1.5, -0.8);
        l.add(box(s[0], 10.6, 6.2, s[1], 14.0, 9.8).all("#brass"));
        // латунные опорные хомуты к стенке
        s = span.apply(0.9, 4.1);
        l.add(box(s[0], 6.8, 6.2, s[1], 7.6, 9.8).all("#brass"));
        l.add(box(s[0], 9.6, 6.2, s[1], 10.4, 9.8).all("#brass"));
        // изящная кристаллическая сосулька с нижнего изгиба
        s = span.apply(2.2, 2.7);
        l.add(box(s[0], 1.6, 7.6, s[1], 3.2, 8.1).all("#ice"));
    }

    static Models.El box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Models.e(x0, y0, z0, x1, y1, z1);
    }

    static void model() throws Exception {
        List<Models.El> l = new ArrayList<>();
        // цоколь и корпус (фасад — север: там циферблат; восток — манометры)
        l.add(box(0, 0, 0, 16, 1, 16).all("#dark"));
        l.add(box(0.5, 1, 0.5, 15.5, 8, 15.5).face("north", "#body", 0, 0, 16, 16).face("south", "#body", 0, 0, 16, 16)
                .face("west", "#body", 0, 0, 16, 16).face("east", "#gauges", 0, 0, 16, 16));
        l.add(box(0, 8, 0, 16, 9, 16).all("#brass"));
        // циферблат на фасаде: только лицевая грань (круг, вне его прозрачно), чуть выступает
        l.add(box(4.6, 1.6, -0.6, 11.4, 8.4, -0.5).face("north", "#dial", 0, 0, 16, 16).glow(11));
        // рунная площадка и стеклянная камера на латунных стойках
        l.add(box(3, 9, 3, 13, 9.2, 13).face("up", "#pad", 0, 0, 16, 16).glow(14));
        l.add(box(2, 9, 2, 14, 15, 14).face("north", "#glass", 0, 0, 16, 16).face("south", "#glass", 0, 0, 16, 16)
                .face("west", "#glass", 0, 0, 16, 16).face("east", "#glass", 0, 0, 16, 16));
        for (double[] c : new double[][]{{1.3, 1.3}, {13.3, 1.3}, {1.3, 13.3}, {13.3, 13.3}}) {
            l.add(box(c[0], 9, c[1], c[0] + 1.4, 15, c[1] + 1.4).all("#brass"));
        }
        l.add(box(2.7, 11.6, 1.5, 13.3, 12.1, 2.1).all("#brass"));
        // верхняя рама вокруг отверстия трубы-вытяжки
        l.add(box(1, 15, 1, 15, 16, 5).all("#body").face("up", "#top", 1, 1, 15, 5));
        l.add(box(1, 15, 11, 15, 16, 15).all("#body").face("up", "#top", 1, 11, 15, 15));
        l.add(box(1, 15, 5, 5, 16, 11).all("#body").face("up", "#top", 1, 5, 5, 11));
        l.add(box(11, 15, 5, 15, 16, 11).all("#body").face("up", "#top", 11, 5, 15, 11));
        // труба-вытяжка: стенки и латунный венец
        l.add(box(5, 16, 5, 11, 19, 5.8).all("#body"));
        l.add(box(5, 16, 10.2, 11, 19, 11).all("#body"));
        l.add(box(5, 16, 5.8, 5.8, 19, 10.2).all("#body"));
        l.add(box(10.2, 16, 5.8, 11, 19, 10.2).all("#body"));
        l.add(box(4.5, 19, 4.5, 11.5, 19.7, 5.3).all("#brass"));
        l.add(box(4.5, 19, 10.7, 11.5, 19.7, 11.5).all("#brass"));
        l.add(box(4.5, 19, 5.3, 5.3, 19.7, 10.7).all("#brass"));
        l.add(box(10.7, 19, 5.3, 11.5, 19.7, 10.7).all("#brass"));
        // обмёрзшие трубы петлёй по бокам
        pipeLoop(l, 0, -1);
        pipeLoop(l, 16, 1);
        // сосульки: с латунного пояса и с верхней рамы (на фасаде — мимо циферблата)
        for (double[] ic : new double[][]{{1.2, 1.4}, {3.0, 1.0}, {12.6, 1.2}, {14.2, 1.6}}) {
            l.add(box(ic[0], 8 - ic[1], -0.3, ic[0] + 0.45, 8, 0.2).all("#ice"));
            l.add(box(ic[0], 8 - ic[1] * 0.8, 15.8, ic[0] + 0.45, 8, 16.3).all("#ice"));
        }
        for (double[] ic : new double[][]{{2.6, 1.2}, {5.4, 0.8}, {8.8, 1.4}, {11.6, 0.9}, {13.6, 1.1}}) {
            l.add(box(ic[0], 15 - ic[1], 0.7, ic[0] + 0.4, 15, 1.1).all("#ice"));
        }
        Models.model("eternal_frost_resonator", true, "resonator_body",
                new String[][]{{"body", "resonator_body"}, {"gauges", "resonator_gauges"}, {"top", "resonator_top"},
                        {"brass", "sat_brass"}, {"dark", "sat_dark"}, {"glass", "resonator_glass"}, {"pad", "resonator_pad"},
                        {"pipe", "resonator_pipe"}, {"ice", "sat_ice"}, {"dial", "resonator_dial"}},
                l.toArray(new Models.El[0]));
        Models.facing4("eternal_frost_resonator", "eternal_frost_resonator");
        Models.item("eternal_frost_resonator", "eternal_frost_resonator");
        Forge2.addDisplay("eternal_frost_resonator", 225, 0.5);
    }
}
