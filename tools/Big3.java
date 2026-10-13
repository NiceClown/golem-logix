import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Большая модель многоблока в «машинных» координатах: X — слева направо глазами зрителя спереди, Y — вверх, Z — вглубь от
 * фасада (клетка 16x16x16, всего cols x rows x deps клеток). Режется на клетки: каждая получает свой JSON с локальными
 * координатами (модель блока смотрит на север, поворот делает блокстейт; локальный x зеркален — растёт влево от зрителя).
 * Грань остаётся в клетке, только если она — естественная граница бруска (не след разреза). Повёрнутые бруски нельзя резать.
 * Текстуры лицевых граней бывают «по клеткам»: front/back — суффикс {col}{row}, left/right — {dep}{row}.
 */
final class Big3 {
    static final double EPS = 1e-9;

    /** Проекция: переводит точку на грани (в машинных координатах) в координаты текстуры 0..16. */
    interface UvMap {
        double[] at(double x, double y, double z);
    }

    enum Mode {SHARED, COL_ROW, DEP_ROW}

    static final class Face {
        final String tex;
        final UvMap map;
        final int light;
        final Mode mode;

        Face(String tex, UvMap map, int light, Mode mode) {
            this.tex = tex;
            this.map = map;
            this.light = light;
            this.mode = mode;
        }

        static Face of(String tex) {
            return new Face(tex, null, 0, Mode.SHARED);
        }

        static Face map(String tex, UvMap map) {
            return new Face(tex, map, 0, Mode.SHARED);
        }

        /** Текстура по клеткам: к имени добавляется _{col}{row}. */
        static Face colRow(String tex) {
            return new Face(tex, null, 0, Mode.COL_ROW);
        }

        /** Текстура по клеткам боковых стен: к имени добавляется _{dep}{row}. */
        static Face depRow(String tex) {
            return new Face(tex, null, 0, Mode.DEP_ROW);
        }

        Face glow(int level) {
            return new Face(tex, map, level, mode);
        }
    }

    static final class Box {
        double x0, y0, z0, x1, y1, z1;
        Face front, back, left, right, top, bottom;
        String rotAxis;
        double rotAngle;
        double[] rotOrigin;

        Box(double x0, double y0, double z0, double x1, double y1, double z1) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
        }

        Box all(Face f) {
            front = back = left = right = top = bottom = f;
            return this;
        }

        Box sides(Face f) {
            front = back = left = right = f;
            return this;
        }

        Box front(Face f) {
            front = f;
            return this;
        }

        Box back(Face f) {
            back = f;
            return this;
        }

        Box left(Face f) {
            left = f;
            return this;
        }

        Box right(Face f) {
            right = f;
            return this;
        }

        Box top(Face f) {
            top = f;
            return this;
        }

        Box bottom(Face f) {
            bottom = f;
            return this;
        }

        Box rot(String axis, double angle, double ox, double oy, double oz) {
            rotAxis = axis;
            rotAngle = angle;
            rotOrigin = new double[]{ox, oy, oz};
            return this;
        }
    }

    final int cols, rows, deps;
    final List<Box> boxes = new ArrayList<>();

    Big3(int cols, int rows, int deps) {
        this.cols = cols;
        this.rows = rows;
        this.deps = deps;
    }

    Box box(double x0, double y0, double z0, double x1, double y1, double z1) {
        Box b = new Box(x0, y0, z0, x1, y1, z1);
        boxes.add(b);
        return b;
    }

    static String n(double v) {
        double r = Math.round(v * 10000.0) / 10000.0;
        return r == Math.floor(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%s", r);
    }

    /** Результат нарезки одной клетки: элементы и набор использованных переменных текстур. */
    static final class Cell {
        final List<String> elements = new ArrayList<>();
        final Set<String> vars = new LinkedHashSet<>();
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < EPS;
    }

    Cell slice(int col, int row, int dep) {
        Cell cell = new Cell();
        double cx0 = 16.0 * col, cx1 = cx0 + 16, cy0 = 16.0 * row, cy1 = cy0 + 16, cz0 = 16.0 * dep, cz1 = cz0 + 16;
        for (Box b : boxes) {
            double ax0 = Math.max(b.x0, cx0), ax1 = Math.min(b.x1, cx1);
            double ay0 = Math.max(b.y0, cy0), ay1 = Math.min(b.y1, cy1);
            double az0 = Math.max(b.z0, cz0), az1 = Math.min(b.z1, cz1);
            if (ax1 - ax0 <= EPS || ay1 - ay0 <= EPS || az1 - az0 <= EPS) {
                continue;
            }
            boolean natL = same(ax0, b.x0), natR = same(ax1, b.x1), natB = same(ay0, b.y0), natT = same(ay1, b.y1);
            boolean natF = same(az0, b.z0), natK = same(az1, b.z1);
            if (b.rotAxis != null && !(natL && natR && natB && natT && natF && natK)) {
                throw new IllegalStateException("повёрнутая деталь пересекает границу клетки: " + b.x0 + "," + b.y0 + "," + b.z0);
            }
            double fx0 = 16.0 * (col + 1) - ax1, fx1 = 16.0 * (col + 1) - ax0;
            double fy0 = ay0 - cy0, fy1 = ay1 - cy0;
            double fz0 = az0 - cz0, fz1 = az1 - cz0;
            StringBuilder sb = new StringBuilder("    {\n");
            sb.append("      \"from\": [").append(n(fx0)).append(", ").append(n(fy0)).append(", ").append(n(fz0)).append("],\n");
            sb.append("      \"to\": [").append(n(fx1)).append(", ").append(n(fy1)).append(", ").append(n(fz1)).append("],\n");
            if (b.rotAxis != null) {
                double ox = 16.0 * (col + 1) - b.rotOrigin[0], oy = b.rotOrigin[1] - cy0, oz = b.rotOrigin[2] - cz0;
                sb.append("      \"rotation\": {\"origin\": [").append(n(ox)).append(", ").append(n(oy)).append(", ").append(n(oz))
                        .append("], \"axis\": \"").append(b.rotAxis).append("\", \"angle\": ").append(n(b.rotAngle)).append("},\n");
            }
            sb.append("      \"faces\": {\n");
            List<String> faces = new ArrayList<>();
            if (natF) {
                addFace(faces, cell, "north", b.front, col, row, dep, new double[]{ax0, ay1, b.z0}, new double[]{ax1, ay0, b.z0});
            }
            if (natK) {
                addFace(faces, cell, "south", b.back, col, row, dep, new double[]{ax1, ay1, b.z1}, new double[]{ax0, ay0, b.z1});
            }
            if (natL) {
                addFace(faces, cell, "east", b.left, col, row, dep, new double[]{ax0, ay1, az1}, new double[]{ax0, ay0, az0});
            }
            if (natR) {
                addFace(faces, cell, "west", b.right, col, row, dep, new double[]{ax1, ay1, az0}, new double[]{ax1, ay0, az1});
            }
            if (natT) {
                addFace(faces, cell, "up", b.top, col, row, dep, new double[]{ax1, ay1, az0}, new double[]{ax0, ay1, az1});
            }
            if (natB) {
                addFace(faces, cell, "down", b.bottom, col, row, dep, new double[]{ax1, ay0, az1}, new double[]{ax0, ay0, az0});
            }
            if (faces.isEmpty()) {
                continue;
            }
            sb.append(String.join(",\n", faces)).append("\n      }\n    }");
            cell.elements.add(sb.toString());
        }
        return cell;
    }

    private void addFace(List<String> out, Cell cell, String dir, Face f, int col, int row, int dep, double[] p0, double[] p1) {
        if (f == null) {
            return;
        }
        String var = switch (f.mode) {
            case SHARED -> f.tex;
            case COL_ROW -> f.tex + "_" + col + row;
            case DEP_ROW -> f.tex + "_" + dep + row;
        };
        cell.vars.add(var);
        StringBuilder sb = new StringBuilder("        \"" + dir + "\": {\"texture\": \"#" + var + "\"");
        if (f.map != null) {
            double[] a = f.map.at(p0[0], p0[1], p0[2]), b = f.map.at(p1[0], p1[1], p1[2]);
            sb.append(", \"uv\": [").append(n(a[0])).append(", ").append(n(a[1])).append(", ").append(n(b[0])).append(", ").append(n(b[1])).append("]");
        }
        if (f.light > 0) {
            sb.append(", \"neoforge_data\": {\"block_light\": ").append(f.light).append(", \"sky_light\": ").append(f.light).append("}");
        }
        out.add(sb.append("}").toString());
    }

    /** Ищет грани одного направления, лежащие в одной плоскости и перекрывающиеся по площади (z-fighting). */
    void lint(String name) {
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i), b = boxes.get(j);
                if (a.rotAxis != null || b.rotAxis != null) {
                    continue;
                }
                check(name, i, j, a.front, b.front, a.z0, b.z0, a.x0, a.x1, b.x0, b.x1, a.y0, a.y1, b.y0, b.y1, "front");
                check(name, i, j, a.back, b.back, a.z1, b.z1, a.x0, a.x1, b.x0, b.x1, a.y0, a.y1, b.y0, b.y1, "back");
                check(name, i, j, a.left, b.left, a.x0, b.x0, a.z0, a.z1, b.z0, b.z1, a.y0, a.y1, b.y0, b.y1, "left");
                check(name, i, j, a.right, b.right, a.x1, b.x1, a.z0, a.z1, b.z0, b.z1, a.y0, a.y1, b.y0, b.y1, "right");
                check(name, i, j, a.top, b.top, a.y1, b.y1, a.x0, a.x1, b.x0, b.x1, a.z0, a.z1, b.z0, b.z1, "top");
                check(name, i, j, a.bottom, b.bottom, a.y0, b.y0, a.x0, a.x1, b.x0, b.x1, a.z0, a.z1, b.z0, b.z1, "bottom");
            }
        }
    }

    private static void check(String name, int i, int j, Face fa, Face fb, double pa, double pb,
                              double a0, double a1, double b0, double b1, double c0, double c1, double d0, double d1, String face) {
        if (fa == null || fb == null || Math.abs(pa - pb) > EPS) {
            return;
        }
        if (Math.min(a1, b1) - Math.max(a0, b0) > EPS && Math.min(c1, d1) - Math.max(c0, d0) > EPS) {
            System.out.println("Z-FIGHT в " + name + ": детали " + i + " и " + j + ", грань " + face);
        }
    }
}
