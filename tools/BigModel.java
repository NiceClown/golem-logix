import java.nio.file.Path;
import java.util.*;

/**
 * Большая модель (для многоблока 2x2) в «шкафных» координатах: X — слева направо глазами зрителя СПЕРЕДИ (0..32),
 * Y — вверх (0..32), Z — вглубь от фасада (0..16). Модель режется на четыре клетки блока (колонка 0 — слева, ряд 0 — внизу);
 * каждая клетка получает свой JSON с локальными координатами (модель блока смотрит на север, поворот делает блокстейт).
 * Лицевые грани можно красить «по клеткам» (текстура фасада режется на четыре картинки), остальные — любой проекцией.
 */
public final class BigModel {
    static final double EPS = 1e-9;

    /** Проекция: переводит точку на грани (в шкафных координатах) в координаты текстуры 0..16. */
    interface UvMap {
        double[] at(double x, double y, double z);
    }

    static final class Face {
        final String tex;      // имя переменной текстуры, без '#'
        final UvMap map;       // null = обычная раскладка (по клетке)
        final int light;       // 0..15 — свечение грани

        Face(String tex, UvMap map, int light) {
            this.tex = tex;
            this.map = map;
            this.light = light;
        }

        static Face of(String tex) {
            return new Face(tex, null, 0);
        }

        static Face map(String tex, UvMap map) {
            return new Face(tex, map, 0);
        }

        /** Картинка размером (w x h юнитов), левый верхний угол которой лежит в (ox, oy) плоскости грани. */
        Face glow(int level) {
            return new Face(tex, map, level);
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

        /** Поворот на 22.5/45 градусов вокруг оси (x/y/z) через точку (только для деталей внутри одной клетки). */
        Box rot(String axis, double angle, double ox, double oy, double oz) {
            rotAxis = axis;
            rotAngle = angle;
            rotOrigin = new double[]{ox, oy, oz};
            return this;
        }
    }

    final List<Box> boxes = new ArrayList<>();

    Box box(double x0, double y0, double z0, double x1, double y1, double z1) {
        Box b = new Box(x0, y0, z0, x1, y1, z1);
        boxes.add(b);
        return b;
    }

    static String n(double v) {
        double r = Math.round(v * 10000.0) / 10000.0;
        return r == Math.floor(r) ? Long.toString((long) r) : String.format(Locale.ROOT, "%s", r);
    }

    static final String[] CELL = {"bl", "br", "tl", "tr"};

    static String cellKey(int col, int row) {
        return CELL[row * 2 + col];
    }

    /** Результат нарезки одной клетки: элементы и набор использованных переменных текстур. */
    static final class Cell {
        final List<String> elements = new ArrayList<>();
        final Set<String> vars = new LinkedHashSet<>();
    }

    /**
     * Нарезает модель на клетку (col, row). Для модели предмета передают dx/dy — сдвиг элементов в общую модель 32x32,
     * и cellVar=true, тогда переменная elev получает суффикс клетки.
     */
    Cell slice(int col, int row, double dx, double dy, boolean cellVar) {
        Cell cell = new Cell();
        double cx0 = 16.0 * col, cx1 = cx0 + 16, cy0 = 16.0 * row, cy1 = cy0 + 16;
        for (Box b : boxes) {
            double ax0 = Math.max(b.x0, cx0), ax1 = Math.min(b.x1, cx1);
            double ay0 = Math.max(b.y0, cy0), ay1 = Math.min(b.y1, cy1);
            if (ax1 - ax0 <= EPS || ay1 - ay0 <= EPS) {
                continue;
            }
            boolean cutL = ax0 > b.x0 + EPS || (Math.abs(ax0 - cx0) < EPS && col == 1);
            boolean cutR = ax1 < b.x1 - EPS || (Math.abs(ax1 - cx1) < EPS && col == 0);
            boolean cutB = ay0 > b.y0 + EPS || (Math.abs(ay0 - cy0) < EPS && row == 1);
            boolean cutT = ay1 < b.y1 - EPS || (Math.abs(ay1 - cy1) < EPS && row == 0);
            if (b.rotAxis != null && (cutL || cutR || cutB || cutT)) {
                throw new IllegalStateException("повёрнутая деталь пересекает границу клетки: " + b.x0 + "," + b.y0);
            }
            double fx0 = 16.0 * (col + 1) - ax1 + dx, fx1 = 16.0 * (col + 1) - ax0 + dx;
            double fy0 = ay0 - cy0 + dy, fy1 = ay1 - cy0 + dy;
            StringBuilder sb = new StringBuilder("    {\n");
            sb.append("      \"from\": [").append(n(fx0)).append(", ").append(n(fy0)).append(", ").append(n(b.z0)).append("],\n");
            sb.append("      \"to\": [").append(n(fx1)).append(", ").append(n(fy1)).append(", ").append(n(b.z1)).append("],\n");
            if (b.rotAxis != null) {
                double ox = 16.0 * (col + 1) - b.rotOrigin[0] + dx, oy = b.rotOrigin[1] - cy0 + dy, oz = b.rotOrigin[2];
                sb.append("      \"rotation\": {\"origin\": [").append(n(ox)).append(", ").append(n(oy)).append(", ").append(n(oz))
                        .append("], \"axis\": \"").append(b.rotAxis).append("\", \"angle\": ").append(n(b.rotAngle)).append("},\n");
            }
            sb.append("      \"faces\": {\n");
            List<String> faces = new ArrayList<>();
            // в модели предмета элементы сдвинуты: «обычная» развёртка берётся по координатам внутри клетки, а не по сдвинутым
            double[][] local = dx == 0 && dy == 0 ? null
                    : new double[][]{{fx0 - dx, fy0 - dy, b.z0}, {fx1 - dx, fy1 - dy, b.z1}};
            addFace(faces, cell, "north", b.front, cellVar, col, row, new double[]{ax0, ay1, b.z0}, new double[]{ax1, ay0, b.z0}, local);
            addFace(faces, cell, "south", b.back, cellVar, col, row, new double[]{ax1, ay1, b.z1}, new double[]{ax0, ay0, b.z1}, local);
            if (!cutL) {
                addFace(faces, cell, "east", b.left, cellVar, col, row, new double[]{ax0, ay1, b.z1}, new double[]{ax0, ay0, b.z0}, local);
            }
            if (!cutR) {
                addFace(faces, cell, "west", b.right, cellVar, col, row, new double[]{ax1, ay1, b.z0}, new double[]{ax1, ay0, b.z1}, local);
            }
            if (!cutT) {
                addFace(faces, cell, "up", b.top, cellVar, col, row, new double[]{ax1, ay1, b.z0}, new double[]{ax0, ay1, b.z1}, local);
            }
            if (!cutB) {
                addFace(faces, cell, "down", b.bottom, cellVar, col, row, new double[]{ax1, ay0, b.z1}, new double[]{ax0, ay0, b.z0}, local);
            }
            if (faces.isEmpty()) {
                continue;
            }
            sb.append(String.join(",\n", faces)).append("\n      }\n    }");
            cell.elements.add(sb.toString());
        }
        return cell;
    }

    private void addFace(List<String> out, Cell cell, String dir, Face f, boolean cellVar, int col, int row, double[] p0, double[] p1,
                         double[][] local) {
        if (f == null) {
            return;
        }
        String var = f.tex;
        if (cellVar && (var.equals("elev") || var.equals("back"))) {
            var = var + "_" + cellKey(col, row);
        }
        cell.vars.add(var);
        StringBuilder sb = new StringBuilder("        \"" + dir + "\": {\"texture\": \"#" + var + "\"");
        if (f.map != null) {
            double[] a = f.map.at(p0[0], p0[1], p0[2]), b = f.map.at(p1[0], p1[1], p1[2]);
            sb.append(", \"uv\": [").append(n(a[0])).append(", ").append(n(a[1])).append(", ").append(n(b[0])).append(", ").append(n(b[1])).append("]");
        } else if (local != null) {
            double[] uv = Models.autoUv(dir, local[0], local[1]);
            sb.append(", \"uv\": [").append(n(uv[0])).append(", ").append(n(uv[1])).append(", ").append(n(uv[2])).append(", ").append(n(uv[3])).append("]");
        }
        if (f.light > 0) {
            sb.append(", \"neoforge_data\": {\"block_light\": ").append(f.light).append(", \"sky_light\": ").append(f.light).append("}");
        }
        out.add(sb.append("}").toString());
    }

    /** Ищет грани одного направления, лежащие в одной плоскости и перекрывающиеся по площади (мерцание z-fighting). */
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
