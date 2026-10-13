import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Генерация JSON-моделей, блокстейтов и моделей предметов (вызывается из ArtForge).
 * Все модели «лицом» смотрят на NORTH (-Z); поворот под FACING делают блокстейты.
 */
public class Models {
    static final Path RES = Path.of("src/main/resources/assets/golem_logix");
    static final String NS = "golem_logix:block/";

    static class El {
        final double[] f, t;
        final LinkedHashMap<String, String> faces = new LinkedHashMap<>();
        String rotation = null;
        String rotAxis = null;
        double rotAngle = 0;
        double[] rotOrigin = null;
        int light = 0;

        /** Свечение граней элемента (0..15): в темноте деталь остаётся яркой, как светящийся камень. */
        El glow(int level) {
            this.light = level;
            return this;
        }

        El(double fx, double fy, double fz, double tx, double ty, double tz) {
            f = new double[]{fx, fy, fz};
            t = new double[]{tx, ty, tz};
        }

        El all(String tex) {
            for (String d : new String[]{"north", "south", "east", "west", "up", "down"}) faces.put(d, face(tex, null));
            return this;
        }

        El face(String dir, String tex, double... uv) {
            faces.put(dir, face(tex, uv.length == 4 ? uv : null));
            return this;
        }

        El only(String tex, String... dirs) {
            for (String d : dirs) faces.put(d, face(tex, null));
            return this;
        }

        El rot(String axis, double angle, double ox, double oy, double oz) {
            rotation = String.format(Locale.ROOT, "\"rotation\": {\"origin\": [%s, %s, %s], \"axis\": \"%s\", \"angle\": %s}",
                    n(ox), n(oy), n(oz), axis, n(angle));
            rotAxis = axis;
            rotAngle = angle;
            rotOrigin = new double[]{ox, oy, oz};
            return this;
        }

        static String face(String tex, double[] uv) {
            return uv == null ? "{\"texture\": \"" + tex + "\"}"
                    : String.format(Locale.ROOT, "{\"uv\": [%s, %s, %s, %s], \"texture\": \"%s\"}", n(uv[0]), n(uv[1]), n(uv[2]), n(uv[3]), tex);
        }

        String json() {
            StringBuilder sb = new StringBuilder("    {\n");
            sb.append(String.format(Locale.ROOT, "      \"from\": [%s, %s, %s],\n      \"to\": [%s, %s, %s],\n",
                    n(f[0]), n(f[1]), n(f[2]), n(t[0]), n(t[1]), n(t[2])));
            if (rotation != null) sb.append("      ").append(rotation).append(",\n");
            sb.append("      \"faces\": {\n");
            int i = 0;
            for (Map.Entry<String, String> e : faces.entrySet()) {
                String v = e.getValue();
                if (!v.contains("\"uv\"")) {
                    // деталь за пределами клетки (над крышей и т. п.): обычная развёртка вылезла бы за текстуру
                    double[] auto = autoUv(e.getKey(), f, t);
                    double[] u = fit(auto[0], auto[2]), w = fit(auto[1], auto[3]);
                    if (u[0] != auto[0] || u[1] != auto[2] || w[0] != auto[1] || w[1] != auto[3]) {
                        v = String.format(Locale.ROOT, "{\"uv\": [%s, %s, %s, %s], ", n(u[0]), n(w[0]), n(u[1]), n(w[1])) + v.substring(1);
                    }
                }
                if (light > 0) {
                    v = v.substring(0, v.length() - 1) + ", \"neoforge_data\": {\"block_light\": " + light + ", \"sky_light\": " + light + "}}";
                }
                sb.append("        \"").append(e.getKey()).append("\": ").append(v).append(++i < faces.size() ? ",\n" : "\n");
            }
            return sb.append("      }\n    }").toString();
        }
    }

    static String n(double v) {
        return v == Math.floor(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%s", v);
    }

    /** Развёртка, которую игра даёт грани без "uv" (как BlockElement.uvsByFace): u0, v0, u1, v1. */
    static double[] autoUv(String dir, double[] f, double[] t) {
        return switch (dir) {
            case "down" -> new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
            case "up" -> new double[]{f[0], f[2], t[0], t[2]};
            case "south" -> new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
            case "west" -> new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
            case "east" -> new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
            default -> new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
        };
    }

    /**
     * Отрезок развёртки [a, b], вылезающий за 0..16, переносится внутрь текстуры: сначала на целое число периодов (как будто
     * текстура повторяется), а если он лежит на стыке — сдвигом к краю. Иначе грань брала бы пиксели соседних текстур атласа.
     */
    static double[] fit(double a, double b) {
        if (a >= -1e-6 && b <= 16 + 1e-6) {
            return new double[]{a, b};
        }
        double s = -16 * Math.floor(a / 16 + 1e-9);
        if (b + s <= 16 + 1e-6) {
            return new double[]{a + s, b + s};
        }
        if (b - a >= 16) {
            return new double[]{0, 16};
        }
        return a < 0 ? new double[]{0, b - a} : new double[]{16 - (b - a), 16};
    }

    static El e(double fx, double fy, double fz, double tx, double ty, double tz) {
        return new El(fx, fy, fz, tx, ty, tz);
    }

    /**
     * Угловые пластины восьмигранной призмы вдоль Z (прямоугольник x0..x1 × y0..y1 со срезанными на c углами). Наружная грань
     * каждой пластины лежит точно на фаске, длина — во всю фаску, толщина t — внутрь: углы смыкаются со сторонами без щелей и
     * выступов (раньше пластина стояла посередине фаски и была короче — вблизи кольца распадались на отдельные планки).
     * endTex — торцы пластин (north/south) на 0.02 глубже торцов призмы: пластины заходят в стороны, совпавшие грани мерцали бы.
     */
    static void octCorners(List<El> l, double x0, double y0, double x1, double y1, double z0, double z1, double c, double t,
                           String faceTex, String endTex, int glow) {
        octCorners(l, x0, y0, x1, y1, z0, z1, c, t, faceTex, null, endTex, endTex, glow);
    }

    /** faceUv — своя развёртка наружной и внутренней граней пластины (null — обычная); northTex/southTex — торцы (null — нет). */
    static void octCorners(List<El> l, double x0, double y0, double x1, double y1, double z0, double z1, double c, double t,
                           String faceTex, double[] faceUv, String northTex, String southTex, int glow) {
        double len = c * Math.sqrt(2) / 2, sh = t / 2 / Math.sqrt(2), mid = (z0 + z1) / 2;
        double n0 = northTex != null ? 0.02 : 0, s0 = southTex != null ? 0.02 : 0;
        double[][] corner = {{x1 - c / 2 - sh, y1 - c / 2 - sh, -45}, {x0 + c / 2 + sh, y1 - c / 2 - sh, 45},
                {x1 - c / 2 - sh, y0 + c / 2 + sh, 45}, {x0 + c / 2 + sh, y0 + c / 2 + sh, -45}};
        for (double[] k : corner) {
            El d = e(k[0] - len, k[1] - t / 2, z0 + n0, k[0] + len, k[1] + t / 2, z1 - s0);
            d.faces.put("up", El.face(faceTex, faceUv));
            d.faces.put("down", El.face(faceTex, faceUv));
            if (northTex != null) {
                d.only(northTex, "north");
            }
            if (southTex != null) {
                d.only(southTex, "south");
            }
            l.add(d.rot("z", k[2], k[0], k[1], mid).glow(glow));
        }
    }

    static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }

    /** Модель-«конструктор». textures: имя -> текстура (без namespace). */
    /** Ищет грани одного направления, лежащие в одной плоскости и перекрывающиеся по площади (z-fighting). */
    static void checkCoplanar(String name, El[] els) {
        String[] dirs = {"north", "south", "east", "west", "up", "down"};
        for (int i = 0; i < els.length; i++) {
            for (int j = i + 1; j < els.length; j++) {
                for (String d : dirs) {
                    if (!els[i].faces.containsKey(d) || !els[j].faces.containsKey(d)) {
                        continue;
                    }
                    int axis = d.equals("north") || d.equals("south") ? 2 : d.equals("east") || d.equals("west") ? 0 : 1;
                    // повёрнутая деталь сохраняет плоскость только у граней, перпендикулярных оси поворота
                    double[][] fi = footprint(els[i], axis), fj = footprint(els[j], axis);
                    if (fi == null || fj == null) {
                        continue;
                    }
                    boolean max = d.equals("south") || d.equals("east") || d.equals("up");
                    double pi = max ? els[i].t[axis] : els[i].f[axis], pj = max ? els[j].t[axis] : els[j].f[axis];
                    if (Math.abs(pi - pj) > 1e-9) {
                        continue;
                    }
                    if (overlaps(fi, fj)) {
                        boolean rotated = els[i].rotation != null || els[j].rotation != null;
                        System.out.println("Z-FIGHT in " + name + ": elements " + i + " and " + j + " face " + d + (rotated ? " (rotated)" : ""));
                    }
                }
            }
        }
    }

    /**
     * Четыре угла грани с нормалью вдоль axis в её плоскости (две другие координаты по порядку x, y, z) — с учётом поворота
     * детали; null — грань повёрнута вне своей плоскости (деталь вращается вокруг другой оси).
     */
    static double[][] footprint(El e, int axis) {
        int a = axis == 0 ? 1 : 0, b = axis == 2 ? 1 : 2;
        double[][] pts = {{e.f[a], e.f[b]}, {e.t[a], e.f[b]}, {e.t[a], e.t[b]}, {e.f[a], e.t[b]}};
        if (e.rotation == null) {
            return pts;
        }
        int rot = e.rotAxis.equals("x") ? 0 : e.rotAxis.equals("y") ? 1 : 2;
        if (rot != axis) {
            return null;
        }
        double rad = Math.toRadians(e.rotAngle), cos = Math.cos(rad), sin = Math.sin(rad);
        double oa = e.rotOrigin[a], ob = e.rotOrigin[b];
        for (double[] p : pts) {
            double pa = p[0] - oa, pb = p[1] - ob;
            p[0] = oa + pa * cos - pb * sin;
            p[1] = ob + pa * sin + pb * cos;
        }
        return pts;
    }

    /** Пересекаются ли два выпуклых четырёхугольника с ненулевой площадью (теорема о разделяющей оси). */
    static boolean overlaps(double[][] p, double[][] q) {
        for (double[][] poly : new double[][][]{p, q}) {
            for (int i = 0; i < poly.length; i++) {
                double[] u = poly[i], v = poly[(i + 1) % poly.length];
                double nx = -(v[1] - u[1]), ny = v[0] - u[0];
                double minP = 1e18, maxP = -1e18, minQ = 1e18, maxQ = -1e18;
                for (double[] s : p) {
                    double d = s[0] * nx + s[1] * ny;
                    minP = Math.min(minP, d);
                    maxP = Math.max(maxP, d);
                }
                for (double[] s : q) {
                    double d = s[0] * nx + s[1] * ny;
                    minQ = Math.min(minQ, d);
                    maxQ = Math.max(maxQ, d);
                }
                double len = Math.hypot(nx, ny);
                if (Math.min(maxP, maxQ) - Math.max(minP, minQ) <= 1e-6 * len) {
                    return false;
                }
            }
        }
        return true;
    }

    static void model(String name, boolean translucent, String particle, String[][] textures, El... els) throws Exception {
        checkCoplanar(name, els);
        StringBuilder sb = new StringBuilder("{\n  \"parent\": \"minecraft:block/block\",\n  \"ambientocclusion\": false,\n");
        if (translucent) sb.append("  \"render_type\": \"minecraft:translucent\",\n");
        sb.append("  \"textures\": {\n    \"particle\": \"").append(NS).append(particle).append("\"");
        // имя с пространством («minecraft:block/snow») — как есть, иначе текстура мода
        for (String[] t : textures) sb.append(",\n    \"").append(t[0]).append("\": \"").append(t[1].contains(":") ? "" : NS).append(t[1]).append("\"");
        sb.append("\n  },\n  \"elements\": [\n");
        for (int i = 0; i < els.length; i++) sb.append(els[i].json()).append(i + 1 < els.length ? ",\n" : "\n");
        sb.append("  ]\n}\n");
        write(RES.resolve("models/block/" + name + ".json"), sb.toString());
    }

    static void cube(String name, String tex) throws Exception {
        write(RES.resolve("models/block/" + name + ".json"),
                "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \"" + NS + tex + "\"\n  }\n}\n");
    }

    static void item(String name, String parent) throws Exception {
        write(RES.resolve("models/item/" + name + ".json"), "{\n  \"parent\": \"" + NS + parent + "\"\n}\n");
    }

    static void single(String name, String model) throws Exception {
        write(RES.resolve("blockstates/" + name + ".json"),
                "{\n  \"variants\": {\n    \"\": {\"model\": \"" + NS + model + "\"}\n  }\n}\n");
    }

    /** Блокстейт по FACING (шесть направлений, модель смотрит на север). */
    static void facing6(String name, String model) throws Exception {
        String[][] v = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"},
                {"up", ", \"x\": 270"}, {"down", ", \"x\": 90"}};
        StringBuilder sb = new StringBuilder("{\n  \"variants\": {\n");
        for (int i = 0; i < v.length; i++) {
            sb.append("    \"facing=").append(v[i][0]).append("\": {\"model\": \"").append(NS).append(model).append("\"").append(v[i][1]).append("}")
                    .append(i + 1 < v.length ? ",\n" : "\n");
        }
        write(RES.resolve("blockstates/" + name + ".json"), sb.append("  }\n}\n").toString());
    }

    static void facing4(String name, String model) throws Exception {
        String[][] v = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"}};
        StringBuilder sb = new StringBuilder("{\n  \"variants\": {\n");
        for (int i = 0; i < v.length; i++) {
            sb.append("    \"facing=").append(v[i][0]).append("\": {\"model\": \"").append(NS).append(model).append("\"").append(v[i][1]).append("}")
                    .append(i + 1 < v.length ? ",\n" : "\n");
        }
        write(RES.resolve("blockstates/" + name + ".json"), sb.append("  }\n}\n").toString());
    }

    static void generate() throws Exception {
        // ============================================================ многоблок
        write(RES.resolve("models/block/atlas_casing.json"),
                "{\n  \"parent\": \"minecraft:block/cube_bottom_top\",\n  \"textures\": {\n    \"top\": \"" + NS + "atlas_casing_top\",\n"
                        + "    \"bottom\": \"" + NS + "atlas_casing\",\n    \"side\": \"" + NS + "atlas_casing\"\n  }\n}\n");
        single("atlas_casing", "atlas_casing");
        item("atlas_casing", "atlas_casing");

        cube("atlas_conduit", "atlas_conduit");
        single("atlas_conduit", "atlas_conduit");
        item("atlas_conduit", "atlas_conduit");

        cube("channel_expander", "channel_expander");
        single("channel_expander", "channel_expander");
        item("channel_expander", "channel_expander");
        cube("quantum_channel_expander", "quantum_channel_expander");
        single("quantum_channel_expander", "quantum_channel_expander");
        item("quantum_channel_expander", "quantum_channel_expander");

        cube("cable_bushing", "cable_bushing");
        single("cable_bushing", "cable_bushing");
        item("cable_bushing", "cable_bushing");

        // Ядро: рунный постамент, латунные прихваты и кристалл, торчащий над кольцом
        model("atlas_core", false, "atlas_core",
                new String[][]{{"rune", "atlas_core"}, {"casing", "atlas_casing"}, {"brass", "brass_plate"}, {"crystal", "core_crystal"}},
                e(0, 0, 0, 16, 3, 16).all("#casing"),
                e(1, 3, 1, 15, 9, 15).all("#rune"),
                e(1, 9, 1, 3.5, 13, 3.5).all("#brass"), e(12.5, 9, 1, 15, 13, 3.5).all("#brass"),
                e(1, 9, 12.5, 3.5, 13, 15).all("#brass"), e(12.5, 9, 12.5, 15, 13, 15).all("#brass"),
                e(5, 9, 5, 11, 19, 11).all("#crystal").glow(15),
                e(6, 19, 6, 10, 22, 10).all("#crystal").glow(15));
        single("atlas_core", "atlas_core");
        item("atlas_core", "atlas_core");

        // Атлант: мускулистый атлетический силуэт с тонким профилем по оси Z (глубина ~5-6 вокселей)
        // Нижняя половина: поножи, рельефные икры, коленные чашечки, квадрицепсы, атлетический пояс
        model("atlas_atlant_lower", false, "atlant_body",
                new String[][]{{"body", "atlant_body"}, {"limb", "atlant_limb"}, {"belt", "atlant_belt"}, {"plate", "atlant_plate"}},
                // сапоги-поножи (крепкая стойка, тонкий профиль Z 4.2..10.2)
                e(2.8, 0, 4.2, 7.2, 2.0, 10.2).all("#belt"), e(8.8, 0, 4.2, 13.2, 2.0, 10.2).all("#belt"),
                // рельефные икры (основа)
                e(3.2, 2.0, 5.0, 6.8, 6.5, 9.8).all("#limb"), e(9.2, 2.0, 5.0, 12.8, 6.5, 9.8).all("#limb"),
                // рельеф икроножных мышц (передний выступ)
                e(3.6, 2.5, 4.3, 6.4, 5.5, 4.95).all("#plate"), e(9.6, 2.5, 4.3, 12.4, 5.5, 4.95).all("#plate"),
                // рельефные коленные щитки/чашечки
                e(2.8, 5.5, 4.1, 7.2, 7.2, 4.9).all("#belt"), e(8.8, 5.5, 4.1, 13.2, 7.2, 4.9).all("#belt"),
                // мускулистые квадрицепсы (бёдра)
                e(3.2, 6.5, 4.8, 6.8, 10.5, 9.8).all("#limb"), e(9.2, 6.5, 4.8, 12.8, 10.5, 9.8).all("#limb"),
                // рельефная передняя пластина бедра
                e(3.5, 7.2, 4.2, 6.5, 10.2, 4.75).all("#plate"), e(9.5, 7.2, 4.2, 12.5, 10.2, 4.75).all("#plate"),
                // атлетический пояс и пряжка (сужение в талии)
                e(2.5, 10.5, 4.8, 13.5, 13.5, 10.0).all("#belt"),
                e(6.2, 10.8, 4.0, 9.8, 13.8, 5.0).all("#belt"),
                // низ торса (V-образное сужение к талии)
                e(3.5, 13.5, 5.0, 12.5, 16.0, 9.8).all("#body"),
                e(7.0, 13.5, 4.2, 9.0, 16.0, 4.95).all("#belt"));
        // Верхняя половина: рельефный торс, 6 кубиков пресса, пекторали, широчайшие мышцы (V-крылья), дельтоиды, бицепсы, руки-опоры
        model("atlas_atlant_upper", false, "atlant_body",
                new String[][]{{"body", "atlant_body"}, {"limb", "atlant_limb"}, {"belt", "atlant_belt"}, {"head", "atlant_head"}, {"plate", "atlant_plate"}},
                // нижняя часть торса (основа)
                e(3.8, 0, 4.8, 12.2, 3.5, 9.6).all("#body"),
                // рельефный пресс: нижние кубики
                e(4.4, 0.4, 4.0, 7.6, 1.8, 4.75).all("#plate"),
                e(8.4, 0.4, 4.0, 11.6, 1.8, 4.75).all("#plate"),
                // рельефный пресс: средние кубики
                e(4.4, 2.0, 3.9, 7.6, 3.4, 4.75).all("#plate"),
                e(8.4, 2.0, 3.9, 11.6, 3.4, 4.75).all("#plate"),
                // широчайшие мышцы спины (V-образный силуэт)
                e(1.8, 2.5, 4.6, 3.4, 6.8, 9.2).all("#body"),
                e(12.6, 2.5, 4.6, 14.2, 6.8, 9.2).all("#body"),
                // мощный рельефный торс (грудная клетка с центральным Оккультным Ядром)
                e(3.2, 3.5, 4.4, 12.8, 9.0, 9.6).all("#body"),
                // выступающие пекторали (грудные мышцы)
                e(3.6, 4.2, 3.4, 7.6, 8.4, 4.35).all("#plate"),
                e(8.4, 4.2, 3.4, 12.4, 8.4, 4.35).all("#plate"),
                // шейный воротник
                e(5.0, 8.5, 5.5, 11.0, 10.0, 9.5).all("#belt"),
                // широкие рельефные дельтовидные наплечники
                e(-0.8, 4.5, 3.8, 3.2, 9.5, 9.8).all("#belt"),
                e(12.8, 4.5, 3.8, 16.8, 9.5, 9.8).all("#belt"),
                // руки-бицепсы
                e(-0.2, 9.5, 4.2, 3.5, 14.0, 9.6).all("#limb"),
                e(12.5, 9.5, 4.2, 16.2, 14.0, 9.6).all("#limb"),
                // рельефный пик бицепса
                e(0.2, 10.5, 3.5, 3.2, 13.0, 4.15).all("#plate"),
                e(12.8, 10.5, 3.5, 15.8, 13.0, 4.15).all("#plate"),
                // жест удержания матрицы: расширенные кисти-опоры на верхнем срезе Y 14..16
                e(0, 14.0, 3.0, 4.0, 16.0, 10.5).all("#belt"),
                e(12.0, 14.0, 3.0, 16.0, 16.0, 10.5).all("#belt"),
                e(0, 15.0, 2.0, 4.0, 16.0, 2.95).all("#belt"),
                e(12.0, 15.0, 2.0, 16.0, 16.0, 2.95).all("#belt"));
        // голова — отдельно: её поворачивает к игроку отрисовщик (AtlantHeadRenderer), шея — в (8, 10, 8)
        model("atlas_atlant_head", false, "atlant_body",
                new String[][]{{"body", "atlant_body"}, {"belt", "atlant_belt"}, {"head", "atlant_head"}},
                e(4.8, 10, 5.0, 11.2, 15.5, 10.5).all("#body").face("north", "#head", 0, 0, 16, 16),
                e(4.2, 13, 4.2, 11.8, 15.4, 4.95).all("#belt"), // надбровный рельеф шлема
                e(7.0, 15.5, 5.0, 9.0, 16.5, 10.5).all("#belt"));
        facing4Half("atlas_atlant");
        item("atlas_atlant", "atlas_atlant_lower");

        // шкаф, шины и трубы строит второе поколение графики (tools/Forge2.java, Buses.java, Pipes.java)

        // ============================================================ крио-блоки
        // Крио-Калькулятор (горки и «матрица») и Сопроцессор Охлаждения (рёбра-маховик) — в Gen3: их части движутся
        single("cooling_coprocessor", "cooling_coprocessor");

        // резонатор строит tools/Resonator.java

        // ============================================================== банка
        model("creative_source_jar", true, "jar_glass",
                new String[][]{{"glass", "jar_glass"}, {"liquid", "jar_liquid"}, {"brass", "brass_plate"}, {"cork", "cork"}},
                e(4, 1, 4, 12, 11, 12).all("#liquid"),
                e(3, 0, 3, 13, 12, 13).all("#glass"),
                e(4, 12, 4, 12, 13, 12).all("#brass"),
                e(5, 13, 5, 11, 15, 11).all("#cork"));
        single("creative_source_jar", "creative_source_jar");
        item("creative_source_jar", "creative_source_jar");

        // поставщик шаблонов: модели по стороне подачи пишет Forge2.providerModels

    }

    /** Блокстейт Атланта: две половины, 4 направления. */
    static void facing4Half(String name) throws Exception {
        String[][] v = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"}};
        StringBuilder sb = new StringBuilder("{\n  \"variants\": {\n");
        int total = v.length * 2, i = 0;
        for (String half : new String[]{"lower", "upper"}) {
            for (String[] d : v) {
                sb.append("    \"facing=").append(d[0]).append(",half=").append(half).append("\": {\"model\": \"").append(NS)
                        .append(name).append("_").append(half).append("\"").append(d[1]).append("}")
                        .append(++i < total ? ",\n" : "\n");
            }
        }
        write(RES.resolve("blockstates/" + name + ".json"), sb.append("  }\n}\n").toString());
    }
}
