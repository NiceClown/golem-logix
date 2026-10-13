import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Интерфейс Голема (по концепту: медно-бронзовый куб с ящичками, манометрами, шестернями и инеем). Латунный каркас по рёбрам,
 * утопленные бронзовые панели, на КАЖДОЙ из шести сторон — квадратный латунный патрубок-раструб (разъём сети и приём
 * предметов) с тёмной воронкой, рядом — плоский метроном рельефом; турбины в раструбах и маятники метрономов рисует
 * {@code InterfaceRenderer}
 * (крутятся и качаются, пока интерфейс работает; темп — по числу ускорителей).
 */
final class Interface {
    static final int K = Forge2.K;
    static final int[] BRASS = Paint.BRASS, BRONZE = Paint.BRONZE, IRON = Paint.IRON, CYAN = Paint.CYAN, VIOLET = Paint.VIOLET,
            FROST = Paint.FROST, COPPER = Paint.COPPER, CREAM = Paint.CREAM;
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

    // ============================================================================================ текстуры
    /** Тёмная воронка в центре грани: кольца, уходящие вглубь, и бирюзовый отблеск сети на дне. */
    static void funnel(Paint p, double cx, double cy, double r) {
        p.discFn(cx, cy, r, (u, v) -> {
            double d = Math.hypot(u - cx, v - cy) / r;
            double rings = 0.5 + 0.5 * Math.cos(d * 18);
            return Paint.ramp(IRON, 0.04 + 0.22 * d + 0.06 * rings);
        });
        p.ring(cx, cy, r - 0.35, r, at(BRASS, 1.4));
        p.disc(cx, cy, r * 0.28, at(CYAN, 2.2));
        p.glowRect(cx - r * 0.2, cy - r * 0.2, cx + r * 0.2, cy + r * 0.2, r * 0.35, at(CYAN, 4.2), 90);
    }

    /** Латунный ящичек картотеки с ручкой-скобой и табличкой. */
    static void drawer(Paint p, double x0, double y0, double x1, double y1, long seed) {
        p.slab(x0, y0, x1, y1, BRASS, 3.0, 1);
        p.slab(x0 + 0.35, y0 + 0.35, x1 - 0.35, y1 - 0.35, BRASS, 2.4, 1);
        double cx = (x0 + x1) / 2;
        p.rect(cx - 0.9, y0 + 0.6, cx + 0.9, y0 + 1.1, rgb(0xE6DCB8));
        p.rect(cx - 0.6, y0 + 0.75, cx + 0.3, y0 + 0.85, argb(160, 0x3a2a18));
        p.rect(cx - 1.1, y1 - 1.0, cx + 1.1, y1 - 0.75, at(BRASS, 5.4));
        p.rect(cx - 1.1, y1 - 0.75, cx - 0.85, y1 - 0.45, at(BRASS, 4.0));
        p.rect(cx + 0.85, y1 - 0.75, cx + 1.1, y1 - 0.45, at(BRASS, 4.0));
    }

    /** Боковая панель (16x16; видна область 1..15 между рёбрами каркаса). */
    static Paint side(long seed) {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.7, true, seed);
        p.slab(1.4, 1.4, 14.6, 14.6, BRONZE, 2.3, 1);
        p.grain(1.4, 1.4, 14.6, 14.6, 0.05, seed + 1);
        // патина по швам, как на концепте
        Random r = new Random(seed + 2);
        for (int i = 0; i < 9; i++) {
            double x = 1.5 + r.nextDouble() * 13, y = 1.5 + r.nextDouble() * 13;
            p.tint(x, y, x + 0.6 + r.nextDouble() * 1.4, y + 0.3 + r.nextDouble() * 0.6, argb(90, 0x3fae9a));
        }
        // ряд манометров сверху (стрелки оживают — анимированная текстура)
        p.slab(4.0, 1.6, 12.0, 3.9, BRASS, 2.8, 1);
        for (int i = 0; i < 3; i++) {
            p.gauge(5.4 + i * 2.6, 2.75, 1.0, 50 + i * 37 + seed % 17, RED);
        }
        // ящички картотеки снизу по углам и руны по бокам
        drawer(p, 1.8, 11.6, 4.6, 14.2, seed + 3);
        drawer(p, 11.4, 11.6, 14.2, 14.2, seed + 4);
        // слева — ниша под плоский метроном (его рельеф ставит модель), справа — столбец рун
        p.slab(1.6, 4.2, 4.1, 11.6, BRASS, 2.6, 1);
        p.slab(1.9, 4.5, 3.8, 11.3, IRON, 0.7, 1);
        for (int i = 0; i < 3; i++) {
            p.rune(13.0, 4.6 + i * 2.2, 1.6, (int) seed + i * 3 + 7, at(CYAN, 5.0), at(CYAN, 2.4));
        }
        // воронка под патрубком
        funnel(p, 8, 8, 3.2);
        for (double[] c : new double[][]{{1.8, 1.8}, {14.2, 1.8}, {1.8, 10.8}, {14.2, 10.8}}) {
            p.rivet(c[0], c[1], 0.3, BRASS);
        }
        p.frostLine(1.4, 14.6, 1.4, 0.5, seed + 5);
        p.icicle(6.2, 1.4, 0.9, 0.4);
        p.icicle(10.4, 1.4, 1.2, 0.4);
        return p;
    }

    /** Крыша и дно: бронзовый настил, решётки вентиляции, воронка, иней. */
    static Paint top(boolean bottom) {
        Paint p = new Paint(16, 16, K);
        Forge2.beam(p, 0, 0, 16, 16, 2.6, true, bottom ? 7101 : 7102);
        p.slab(1.4, 1.4, 14.6, 14.6, BRONZE, 2.1, 1);
        Terminals.vent(p, 6.0, 1.9, 10.0, 3.6, 3);
        Terminals.vent(p, 6.0, 12.4, 10.0, 14.1, 3);
        funnel(p, 8, 8, 3.2);
        for (int i = 0; i < 2; i++) {
            p.rune(2.4 + i * 9.6, 7.2, 1.6, 12 + i * 9, at(VIOLET, 5.2), at(VIOLET, 2.6));
        }
        if (!bottom) {
            p.frostLine(1.4, 14.6, 1.4, 0.8, 7103);
            p.frostLine(1.4, 14.6, 13.8, 0.6, 7104);
        }
        return p;
    }

    /** Латунь каркаса и патрубков: продольное зерно, заклёпки. */
    static Paint brass() {
        Paint p = new Paint(16, 16, K);
        p.slab(0, 0, 16, 16, BRASS, 3.0, 1);
        p.streaks(0.3, 0.3, 15.7, 15.7, 0.07, 7111, true);
        p.grain(0, 0, 16, 16, 0.04, 7112);
        for (double x = 2; x < 16; x += 4) {
            p.rivet(x, 8, 0.45, BRASS);
        }
        return p;
    }

    /** Лопасти турбины раструба (вне круга — прозрачно; отрисовщик крутит её). */
    static Paint turbine() {
        Paint p = new Paint(8, 8, 8);
        for (int b = 0; b < 7; b++) {
            double a0 = b * 2 * Math.PI / 7;
            for (double rr = 0.7; rr < 3.6; rr += 0.06) {
                double a = a0 + rr * 0.42;
                p.disc(4 + Math.cos(a) * rr, 4 + Math.sin(a) * rr, 0.26 + rr * 0.07,
                        Paint.ramp(COPPER, 0.3 + 0.45 * (rr / 3.6) + 0.1 * Math.sin(a * 2)));
            }
        }
        p.disc(4, 4, 0.95, at(BRASS, 1.4));
        p.sphere(4, 4, 0.85, BRASS, 0.35, 1.0);
        p.disc(4, 4, 0.3, at(CYAN, 5.0));
        p.glowRect(3.9, 3.9, 4.1, 4.1, 0.5, at(CYAN, 4.4), 120);
        return p;
    }

    /** Корпус метронома: красное дерево, латунная шкала с делениями, руна на табличке. */
    static Paint metronome() {
        Paint p = new Paint(16, 16, K);
        p.fill(0, 0, 16, 16, (u, v) -> Paint.mix(rgb(0x4a1c10), rgb(0x7a3420), 0.5 + 0.3 * Math.sin(u * 2.1 + v * 0.3)));
        p.grain(0, 0, 16, 16, 0.08, 7121);
        p.rect(6.6, 1.0, 9.4, 13.0, at(BRASS, 3.6));
        for (int i = 0; i < 9; i++) {
            double y = 1.6 + i * 1.3;
            p.rect(6.9, y, i % 2 == 0 ? 8.8 : 8.0, y + 0.3, at(IRON, 0.6));
        }
        p.slab(4.5, 13.4, 11.5, 15.4, BRASS, 3.2, 1);
        p.rune(7.2, 13.6, 1.6, 4, at(VIOLET, 5.4), 0);
        return p;
    }

    static void textures() throws Exception {
        long[] seeds = {7001, 7002, 7003, 7004};
        for (int i = 0; i < 4; i++) {
            side(seeds[i]).save(Forge2.tex("interface_side_" + i));
        }
        top(false).save(Forge2.tex("interface_top"));
        top(true).save(Forge2.tex("interface_bottom"));
        brass().save(Forge2.tex("interface_brass"));
        turbine().save(Forge2.tex("interface_turbine"));
        metronome().save(Forge2.tex("interface_metronome"));
    }

    // ============================================================================================ модель
    /** Коробка на грани dir: (a, b) — координаты в плоскости грани, d0..d1 — глубина от наружной плоскости внутрь блока. */
    static Models.El onFace(String dir, double a0, double b0, double a1, double b1, double d0, double d1) {
        double[] p = point(dir, a0, b0, d0), q = point(dir, a1, b1, d1);
        return Models.e(Math.min(p[0], q[0]), Math.min(p[1], q[1]), Math.min(p[2], q[2]),
                Math.max(p[0], q[0]), Math.max(p[1], q[1]), Math.max(p[2], q[2]));
    }

    static double[] point(String dir, double a, double b, double d) {
        return switch (dir) {
            case "north" -> new double[]{a, b, d};
            case "south" -> new double[]{a, b, 16 - d};
            case "west" -> new double[]{d, b, a};
            case "east" -> new double[]{16 - d, b, a};
            case "down" -> new double[]{a, d, b};
            default -> new double[]{a, 16 - d, b};
        };
    }

    static final String[] DIRS = {"north", "south", "east", "west", "up", "down"};

    static List<Models.El> body() {
        List<Models.El> l = new ArrayList<>();
        // утопленные панели (сердцевина 1..15)
        l.add(Models.e(1, 1, 1, 15, 15, 15).face("north", "#side0").face("east", "#side1").face("south", "#side2")
                .face("west", "#side3").face("up", "#top").face("down", "#bottom"));
        // латунный каркас: рёбра вдоль X во всю длину, вдоль Y и Z — между ними (без совпадающих граней в углах)
        double t = 1.5;
        for (double a : new double[]{0, 16 - t}) {
            for (double b : new double[]{0, 16 - t}) {
                l.add(Models.e(0, a, b, 16, a + t, b + t).all("#brass"));
                l.add(Models.e(a, t, b, a + t, 16 - t, b + t).all("#brass"));
                l.add(Models.e(a, b, t, a + t, b + t, 16 - t).all("#brass"));
            }
        }
        // патрубки-раструбы на всех шести сторонах: квадратная латунная рамка с фасками и болтами по углам
        for (String d : DIRS) {
            l.add(onFace(d, 4.0, 4.0, 12.0, 5.2, 0, 1).all("#brass"));
            l.add(onFace(d, 4.0, 10.8, 12.0, 12.0, 0, 1).all("#brass"));
            l.add(onFace(d, 4.0, 5.2, 5.2, 10.8, 0, 1).all("#brass"));
            l.add(onFace(d, 10.8, 5.2, 12.0, 10.8, 0, 1).all("#brass"));
            // внутренняя манжета чуть глубже — раструб сужается к воронке
            l.add(onFace(d, 5.2, 5.2, 10.8, 5.8, 0.4, 1).all("#brass"));
            l.add(onFace(d, 5.2, 10.2, 10.8, 10.8, 0.4, 1).all("#brass"));
            l.add(onFace(d, 5.2, 5.8, 5.8, 10.2, 0.4, 1).all("#brass"));
            l.add(onFace(d, 10.2, 5.8, 10.8, 10.2, 0.4, 1).all("#brass"));
        }
        // сосульки под верхними рёбрами
        for (double[] ic : new double[][]{{3.2, 1.0}, {6.8, 1.6}, {11.6, 1.2}}) {
            for (String d : new String[]{"north", "south", "east", "west"}) {
                l.add(onFace(d, ic[0], 14.5 - ic[1], ic[0] + 0.5, 14.5, 0.3, 0.8).all("#ice"));
            }
        }
        // метрономы плашмя на четырёх боковых гранях: невысокий рельеф (0.5) рядом с раструбом, в пределах блока
        for (String d : new String[]{"north", "south", "east", "west"}) {
            metronome(l, d);
        }
        return l;
    }

    /** Середина метронома на грани (координата a) и его низ/верх (b). Маятник рисует отрисовщик с осью в (MET_A, MET_PIVOT). */
    static final double MET_A = 2.85, MET_B0 = 4.6, MET_PIVOT = 5.2, MET_B1 = 11.6;

    /**
     * Плоский метроном на грани: латунный цоколь, сужающийся кверху корпус из четырёх уступов и латунное навершие; лицевые
     * грани уступов берут свою полосу общей картинки — шкала с делениями собирается во всю высоту.
     */
    /**
     * Где метроном на грани d (координата a): там, где на текстуре грани ниша (u = 2.85). У северной и восточной граней
     * развёртка зеркальна (u = 16 - a), поэтому метроном стоит у другого края — и всегда слева от раструба глазами зрителя.
     */
    static double metA(String d) {
        return d.equals("north") || d.equals("east") ? 16 - MET_A : MET_A;
    }

    static void metronome(List<Models.El> l, String d) {
        double MET_A = metA(d);
        l.add(onFace(d, MET_A - 1.1, MET_B0, MET_A + 1.1, MET_PIVOT, 0.5, 1).all("#brass"));
        double[] half = {1.0, 0.85, 0.7, 0.55};
        double h = (MET_B1 - MET_PIVOT) / half.length;
        for (int i = 0; i < half.length; i++) {
            double b0 = MET_PIVOT + i * h, b1 = b0 + h, hw = half[i];
            double v0 = 13 - (b1 - MET_PIVOT) * 12 / (MET_B1 - MET_PIVOT), v1 = 13 - (b0 - MET_PIVOT) * 12 / (MET_B1 - MET_PIVOT);
            l.add(onFace(d, MET_A - hw, b0, MET_A + hw, b1, 0.55, 1).all("#metronome").face(d, "#metronome", 8 - hw * 4.6, v0, 8 + hw * 4.6, v1));
        }
        l.add(onFace(d, MET_A - 0.4, MET_B1, MET_A + 0.4, MET_B1 + 0.4, 0.5, 1).all("#brass"));
    }

    static String[][] tex() {
        return new String[][]{{"side0", "interface_side_0"}, {"side1", "interface_side_1"}, {"side2", "interface_side_2"},
                {"side3", "interface_side_3"}, {"top", "interface_top"}, {"bottom", "interface_bottom"}, {"brass", "interface_brass"},
                {"ice", "sat_ice"}, {"metronome", "interface_metronome"}, {"turbine", "interface_turbine"}, {"rod", "cab_dark"}};
    }

    static void models() throws Exception {
        List<Models.El> body = body();
        Models.checkCoplanar("golem_interface", body.toArray(new Models.El[0]));
        Models.model("golem_interface", false, "interface_brass", tex(), body.toArray(new Models.El[0]));
        Forge2.write(Forge2.RES.resolve("blockstates/golem_interface.json"),
                "{\n  \"variants\": {\n    \"\": {\"model\": \"" + Forge2.NS + "golem_interface\"}\n  }\n}\n");
        // турбина «лицом на север» в раструбе (центр 8, 8, глубина 0.55) и маятник метронома (ось — в начале координат модели)
        Models.model("golem_interface_turbine", false, "interface_turbine", tex(),
                Models.e(5.3, 5.3, 0.55, 10.7, 10.7, 0.55).face("north", "#turbine", 0, 0, 16, 16));
        // маятник — плоский (толщина 0.1): качается в плоскости грани перед корпусом метронома
        Models.model("golem_interface_pendulum", false, "interface_brass", tex(),
                Models.e(7.85, 0, 7.95, 8.15, 5.2, 8.05).all("#rod"),
                Models.e(7.5, 3.2, 7.93, 8.5, 4.0, 8.07).all("#brass"),
                Models.e(7.6, -0.3, 7.91, 8.4, 0.3, 8.09).all("#brass"));
        // предмет: корпус и неподвижные турбины на всех сторонах
        List<Models.El> item = body();
        for (String d : DIRS) {
            Models.El tb = onFace(d, 5.3, 5.3, 10.7, 10.7, 0.55, 0.55);
            tb.faces.put(d, Models.El.face("#turbine", new double[]{0, 0, 16, 16}));
            item.add(tb);
        }
        Models.model("golem_interface_item", true, "interface_brass", tex(), item.toArray(new Models.El[0]));
        Models.item("golem_interface", "golem_interface_item");
        Forge2.addDisplay("golem_interface_item", 225, 0.625);
    }

    static void all() throws Exception {
        textures();
        models();
    }
}
