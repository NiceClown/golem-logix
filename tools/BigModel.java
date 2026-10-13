import java.nio.file.Path;
import java.util.*;

/**
 * A big model (for the 2x2 multiblock) in "cabinet" coordinates: X left to right as seen from the FRONT (0..32),
 * Y up (0..32), Z inwards from the front (0..16). The model is cut into four block cells (column 0 on the left, row 0 at the bottom);
 * each cell gets its own JSON with local coordinates (the block model faces north, the block state rotates it).
 * Front faces can be painted "per cell" (the front texture is cut into four pictures), the others with any projection.
 */
public final class BigModel {
    static final double EPS = 1e-9;

    /** Projection: turns a point on a face (in cabinet coordinates) into texture coordinates 0..16. */
    interface UvMap {
        double[] at(double x, double y, double z);
    }

    static final class Face {
        final String tex;      // texture variable name, without '#'
        final UvMap map;       // null = the usual layout (per cell)
        final int light;       // 0..15 = face glow

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

        /** A picture of size (w x h units) whose top left corner lies at (ox, oy) of the face plane. */
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

        /** Rotation by 22.5/45 degrees around an axis (x/y/z) through a point (only for parts within one cell). */
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

    /** Result of cutting one cell: the elements and the set of texture variables used. */
    static final class Cell {
        final List<String> elements = new ArrayList<>();
        final Set<String> vars = new LinkedHashSet<>();
    }

    /**
     * Cuts the model into cell (col, row). For an item model dx/dy are passed, the shift of elements into the shared 32x32 model,
     * and cellVar=true, then the elev variable gets the cell suffix.
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
                throw new IllegalStateException("a rotated part crosses a cell boundary: " + b.x0 + "," + b.y0);
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
            // in the item model the elements are shifted: the "usual" unwrap uses coordinates inside the cell, not the shifted ones
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

    /** Finds faces of one direction that lie in one plane and overlap in area (z-fighting flicker). */
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
            System.out.println("Z-FIGHT in " + name + ": parts " + i + " and " + j + ", face " + face);
        }
    }
}
