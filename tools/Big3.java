import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A big multiblock model in "machine" coordinates: X left to right as seen from the front, Y up, Z inwards from
 * the front (a 16x16x16 cell, cols x rows x deps cells in all). It is cut into cells: each gets its own JSON with local
 * coordinates (the block model faces north, the block state rotates it; local x is mirrored and grows to the viewer's left).
 * A face stays in a cell only if it is a natural boundary of the box (not a cut mark). Rotated boxes cannot be cut.
 * Front face textures can be "per cell": front/back get the suffix {col}{row}, left/right {dep}{row}.
 */
final class Big3 {
    static final double EPS = 1e-9;

    /** Projection: turns a point on a face (in machine coordinates) into texture coordinates 0..16. */
    interface UvMap {
        double[] at(double x, double y, double z);
    }

    enum Mode {SHARED, COL_ROW, DEP_ROW, COL_DEP}

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

        /** A per-cell texture: _{col}{row} is added to the name. */
        static Face colRow(String tex) {
            return new Face(tex, null, 0, Mode.COL_ROW);
        }

        /** A per-cell texture of the roof (seen from above): _{col}{dep} is added to the name. */
        static Face colDep(String tex) {
            return new Face(tex, null, 0, Mode.COL_DEP);
        }

        /** A per-cell texture of the side walls: _{dep}{row} is added to the name. */
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

    /** Result of cutting one cell: the elements and the set of texture variables used. */
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
                throw new IllegalStateException("a rotated part crosses a cell boundary: " + b.x0 + "," + b.y0 + "," + b.z0);
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
            case COL_DEP -> f.tex + "_" + col + dep;
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

    /**
     * Closes see-through holes: a side left without a face (on the thought that a neighbour covers it) gets one when some of it
     * is in fact open - the world showed through the gaps behind the middle walls. The new face takes the texture of another,
     * whole-machine (shared) face of the same part; rotated parts neither get faces nor count as covering.
     */
    int closeHoles(String name) {
        int closed = 0;
        for (Box b : boxes) {
            if (b.rotAxis != null) {
                continue;
            }
            Face donor = null;
            for (Face f : new Face[]{b.front, b.back, b.left, b.right, b.top, b.bottom}) {
                if (f != null && f.mode == Mode.SHARED && donor == null) {
                    donor = new Face(f.tex, null, f.light, Mode.SHARED);
                }
            }
            if (donor == null) {
                donor = Face.of("sat_bronze");
            }
            for (int side = 0; side < 6; side++) {
                if (faceOf(b, side) == null && exposed(b, side)) {
                    setFace(b, side, donor);
                    closed++;
                }
            }
        }
        if (closed > 0) {
            System.out.println("CLOSED " + closed + " open sides in " + name);
        }
        return closed;
    }

    private static Face faceOf(Box b, int side) {
        return switch (side) {
            case 0 -> b.front;
            case 1 -> b.back;
            case 2 -> b.left;
            case 3 -> b.right;
            case 4 -> b.top;
            default -> b.bottom;
        };
    }

    private static void setFace(Box b, int side, Face f) {
        switch (side) {
            case 0 -> b.front = f;
            case 1 -> b.back = f;
            case 2 -> b.left = f;
            case 3 -> b.right = f;
            case 4 -> b.top = f;
            default -> b.bottom = f;
        }
    }

    /** Some of the side (sampled) has nothing of the machine right outside it. Sides: front z0, back z1, left x0, right x1, top y1, bottom y0. */
    private boolean exposed(Box b, int side) {
        int axis = side < 2 ? 2 : side < 4 ? 0 : 1;
        boolean high = side == 1 || side == 3 || side == 4;
        double[] lo = {b.x0, b.y0, b.z0}, hi = {b.x1, b.y1, b.z1};
        if (hi[axis] - lo[axis] <= EPS) {
            return false;
        }
        int a1 = axis == 0 ? 1 : 0, a2 = axis == 2 ? 1 : 2;
        double probe = high ? hi[axis] + 0.01 : lo[axis] - 0.01;
        for (int u = 0; u < 6; u++) {
            for (int v = 0; v < 6; v++) {
                double[] p = new double[3];
                p[axis] = probe;
                p[a1] = lo[a1] + (hi[a1] - lo[a1]) * (u + 0.5) / 6;
                p[a2] = lo[a2] + (hi[a2] - lo[a2]) * (v + 0.5) / 6;
                boolean in = false;
                for (Box o : boxes) {
                    if (o != b && o.rotAxis == null && p[0] >= o.x0 - EPS && p[0] <= o.x1 + EPS && p[1] >= o.y0 - EPS && p[1] <= o.y1 + EPS
                            && p[2] >= o.z0 - EPS && p[2] <= o.z1 + EPS) {
                        in = true;
                        break;
                    }
                }
                if (!in) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Finds faces of one direction that lie in one plane and overlap in area (z-fighting). */
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
