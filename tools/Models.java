import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Generation of JSON block models, block states and item models (called from ArtForge).
 * All models "face" NORTH (-Z); the block states rotate them by FACING.
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
        /** The faces are culled against a full neighbour on their own side (a full-block element). */
        boolean cull = false;

        El cull() {
            this.cull = true;
            return this;
        }

        /** Glow of the element's faces (0..15): in the dark the part stays bright, like glowstone. */
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
                    // a part outside the cell (above the roof and the like): the usual unwrap would stick out of the texture
                    double[] auto = autoUv(e.getKey(), f, t);
                    double[] u = fit(auto[0], auto[2]), w = fit(auto[1], auto[3]);
                    if (u[0] != auto[0] || u[1] != auto[2] || w[0] != auto[1] || w[1] != auto[3]) {
                        v = String.format(Locale.ROOT, "{\"uv\": [%s, %s, %s, %s], ", n(u[0]), n(w[0]), n(u[1]), n(w[1])) + v.substring(1);
                    }
                }
                if (light > 0) {
                    v = v.substring(0, v.length() - 1) + ", \"neoforge_data\": {\"block_light\": " + light + ", \"sky_light\": " + light + "}}";
                }
                if (cull) {
                    v = v.substring(0, v.length() - 1) + ", \"cullface\": \"" + e.getKey() + "\"}";
                }
                sb.append("        \"").append(e.getKey()).append("\": ").append(v).append(++i < faces.size() ? ",\n" : "\n");
            }
            return sb.append("      }\n    }").toString();
        }
    }

    static String n(double v) {
        return v == Math.floor(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%s", v);
    }

    /** The unwrap the game gives a face without "uv" (like BlockElement.uvsByFace): u0, v0, u1, v1. */
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
     * An unwrap segment [a, b] sticking out of 0..16 is moved inside the texture: first by a whole number of periods (as if
     * the texture repeated), and if it lies across the seam, by a shift to the edge. Otherwise the face would take pixels of neighbouring atlas textures.
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
     * Corner plates of an octagonal prism along Z (a rectangle x0..x1 × y0..y1 with corners cut by c). The outer face
     * of each plate lies exactly on the bevel, the length is the whole bevel, the thickness t goes inwards: the corners meet the sides without gaps or
     * bumps (before, the plate stood in the middle of the bevel and was shorter, and up close the rings fell apart into separate strips).
     * endTex = plate ends (north/south) 0.02 deeper than the prism ends: the plates go into the sides, coinciding faces would flicker.
     */
    static void octCorners(List<El> l, double x0, double y0, double x1, double y1, double z0, double z1, double c, double t,
                           String faceTex, String endTex, int glow) {
        octCorners(l, x0, y0, x1, y1, z0, z1, c, t, faceTex, null, endTex, endTex, glow);
    }

    /** faceUv = a custom unwrap of the plate's outer and inner faces (null = the usual one); northTex/southTex = the ends (null = none). */
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

    /** A "constructor" model. textures: name -> texture (without namespace). */
    /** Finds faces of one direction that lie in one plane and overlap in area (z-fighting). */
    static void checkCoplanar(String name, El[] els) {
        String[] dirs = {"north", "south", "east", "west", "up", "down"};
        for (int i = 0; i < els.length; i++) {
            for (int j = i + 1; j < els.length; j++) {
                for (String d : dirs) {
                    if (!els[i].faces.containsKey(d) || !els[j].faces.containsKey(d)) {
                        continue;
                    }
                    int axis = d.equals("north") || d.equals("south") ? 2 : d.equals("east") || d.equals("west") ? 0 : 1;
                    // a rotated part keeps its plane only for faces perpendicular to the rotation axis
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
     * The four corners of a face with its normal along axis, in its plane (the other two coordinates in x, y, z order), counting the
     * part's rotation; null = the face is rotated out of its plane (the part rotates around another axis).
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

    /** Whether two convex quadrilaterals with non-zero area intersect (the separating axis theorem). */
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
        sb.append("  \"textures\": {\n    \"particle\": \"").append(particle.contains(":") ? "" : NS).append(particle).append("\"");
        // a name with a namespace ("minecraft:block/snow") as is, otherwise a mod texture
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

    /** A block state by FACING (six directions, the model faces north). */
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

    /**
     * The casing's looks in an assembled array (AtlasCasingBlock.Part), each the plate itself plus the dress on its outer north side
     * (the block state turns it): the two halves of the glowing ice column standing in front of the side walls, the balcony rail of
     * brass and spruce on the crown edge, and on a crown corner the rail round both outer edges with icicles over the Atlant (kept
     * clear of its raised hands). Baked with the casing, the dress costs no renderer and never fights its neighbours' faces.
     */
    static void casingParts() throws Exception {
        String[][] colTex = {{"side", "atlas_casing"}, {"top", "atlas_casing_top"}, {"ice", "atlas_ice_column"}, {"cap", "brass_plate"}};
        String[][] tex = {{"side", "atlas_casing"}, {"top", "atlas_casing_top"}, {"ice", "atlas_ice_column"}, {"cap", "brass_plate"},
                {"brass", "brass_plate"}, {"wood", "minecraft:block/spruce_planks"}, {"icicle", "minecraft:block/packed_ice"},
                {"rim_cu", "atlas_rim_copper"}, {"rim_fe", "atlas_rim_iron"}, {"rim_br", "atlas_rim_wheel"}};
        java.util.function.Supplier<El> plate = () -> e(0, 0, 0, 16, 16, 16).only("#side", "north", "south", "east", "west", "down")
                .face("up", "#top").cull();
        model("atlas_casing_column_low", false, "atlas_casing", colTex, plate.get(),
                e(3, 0, -3, 13, 2, 0).only("#cap", "north", "east", "west", "up", "down"),
                e(4.5, 2, -2.25, 11.5, 16, 0).only("#ice", "north", "east", "west").glow(15));
        model("atlas_casing_column_high", false, "atlas_casing", colTex, plate.get(),
                e(4.5, 0, -2.25, 11.5, 14, 0).only("#ice", "north", "east", "west").glow(15),
                e(3, 14, -3, 13, 16, 0).only("#cap", "north", "east", "west", "up", "down"));
        // the rail along the north edge, from x0 to x1 (posts at the given x), standing on the crown
        java.util.function.BiFunction<double[], double[], List<El>> railNorth = (span, posts) -> {
            List<El> l = new ArrayList<>();
            for (double p : posts) {
                l.add(e(p, 16, 0.25, p + 2, 21, 2.25).only("#brass", "north", "south", "east", "west"));
            }
            l.add(e(span[0], 18, 0.75, span[1], 19, 1.75).only("#brass", "north", "south", "up", "down", "east", "west"));
            l.add(e(span[0], 21, 0, span[1], 22.5, 2.5).all("#wood"));
            return l;
        };
        List<El> edge = new ArrayList<>(List.of(plate.get()));
        edge.addAll(railNorth.apply(new double[]{0, 16}, new double[]{1, 7, 13}));
        addValveParapet(edge, true);
        model("atlas_casing_top_edge", false, "atlas_casing", tex, edge.toArray(new El[0]));
        List<El> corner = new ArrayList<>(List.of(plate.get()));
        corner.addAll(railNorth.apply(new double[]{0, 16}, new double[]{1, 7, 13}));
        // the west rail goes on from the corner post; its wood and bar start where the north ones end
        corner.add(e(0.25, 16, 7, 2.25, 21, 9).only("#brass", "north", "south", "east", "west"));
        corner.add(e(0.25, 16, 13, 2.25, 21, 15).only("#brass", "north", "south", "east", "west"));
        corner.add(e(0.75, 18, 1.75, 1.75, 19, 16).only("#brass", "east", "west", "up", "down", "south"));
        corner.add(e(0, 21, 2.5, 2.5, 22.5, 16).only("#wood", "east", "west", "up", "down", "south"));
        // 3D riveted copper parapet, bolted cast-iron valve housing well, and antique-brass handwheel on both outer faces + NW corner join cap
        addValveParapet(corner, true);
        addValveParapet(corner, false);
        corner.add(e(-0.9, 13.5, -0.9, 0.0, 16.0, 0.0).only("#rim_cu", "north", "west", "up", "down"));
        corner.add(e(-0.7, 0.0, -0.7, 0.0, 1.5, 0.0).only("#rim_cu", "north", "west", "down"));
        corner.add(e(-0.7, 1.5, -0.7, 0.0, 13.5, 0.0).only("#rim_cu", "north", "west"));
        // icicles under both outer edges, outside the Atlant's hands (4.5..11.5 on either axis)
        double[][] icicles = {{1, 0, 3, 2, 4, 6}, {12.5, 0, 14, 1.5, 3, 4.5}, {0, 3, 1.5, 4.5, 2.5, 4}, {0, 12, 1.5, 13.5, 3.5, 5}};
        for (double[] i : icicles) {
            corner.add(e(i[0], -i[4], i[1], i[2], 0, i[3]).only("#icicle", "north", "south", "east", "west", "down"));
            double w = (i[2] - i[0]) / 4, d = (i[3] - i[1]) / 4;
            corner.add(e(i[0] + w, -i[5], i[1] + d, i[2] - w, -i[4], i[3] - d).only("#icicle", "north", "south", "east", "west", "down"));
        }
        model("atlas_casing_top_corner", false, "atlas_casing", tex, corner.toArray(new El[0]));
        // the block state: no part = the plain plate; the parts turn to their outer side
        StringBuilder sb = new StringBuilder("{\n  \"variants\": {\n");
        String[][] facings = {{"north", ""}, {"east", ", \"y\": 90"}, {"south", ", \"y\": 180"}, {"west", ", \"y\": 270"}};
        String[][] parts = {{"none", "atlas_casing"}, {"column_low", "atlas_casing_column_low"}, {"column_high", "atlas_casing_column_high"},
                {"top_edge", "atlas_casing_top_edge"}, {"top_corner", "atlas_casing_top_corner"}};
        List<String> lines = new ArrayList<>();
        for (String[] p : parts) {
            for (String[] fc : facings) {
                lines.add("    \"facing=" + fc[0] + ",part=" + p[0] + "\": {\"model\": \"" + NS + p[1] + "\"" + (p[0].equals("none") ? "" : fc[1]) + "}");
            }
        }
        write(RES.resolve("blockstates/atlas_casing.json"), sb.append(String.join(",\n", lines)).append("\n  }\n}\n").toString());
    }

    /**
     * Adds the 3D upper parapet relief on either the north outer face (north=true, protruding z <= 0) or west outer face
     * (north=false, protruding x <= 0): frost-lined copper cornice with icicles, base sill, flanking pilasters, bolted
     * cast-iron valve well, and protruding 3D antique-brass spoked handwheel. All inner faces touching plate (z=0/x=0) are omitted.
     */
    private static void addValveParapet(List<El> l, boolean north) {
        class P {
            El box(double u0, double y0, double d0, double u1, double y1, double d1, String tex, String... dirs) {
                El el = north ? e(u0, y0, -d1, u1, y1, -d0) : e(-d1, y0, u0, -d0, y1, u1);
                for (String dir : dirs) {
                    String mapped = north ? dir : switch (dir) {
                        case "north" -> "west";
                        case "south" -> "east";
                        case "west" -> "north";
                        case "east" -> "south";
                        default -> dir;
                    };
                    el.only(tex, mapped);
                }
                return el;
            }
        }
        P p = new P();
        // Upper frost-lined copper cornice + 2 small frost icicles tucked under it
        l.add(p.box(0.0, 13.5, 0.0, 16.0, 16.0, 0.9, "#rim_cu", "north", "up", "down", "east", "west"));
        l.add(p.box(2.75, 12.0, 0.4, 3.5, 13.5, 0.85, "#icicle", "north", "south", "east", "west", "down"));
        l.add(p.box(12.5, 12.25, 0.4, 13.25, 13.5, 0.85, "#icicle", "north", "south", "east", "west", "down"));
        // Lower copper sill & flanking vertical riveted pilasters (disjoint in Y from cornice & sill)
        l.add(p.box(0.0, 0.0, 0.0, 16.0, 1.5, 0.7, "#rim_cu", "north", "up", "down", "east", "west"));
        l.add(p.box(0.0, 1.5, 0.0, 1.75, 13.5, 0.7, "#rim_cu", "north", "east", "west"));
        l.add(p.box(14.25, 1.5, 0.0, 16.0, 13.5, 0.7, "#rim_cu", "north", "east", "west"));

        // Sculpted chamfered cast-iron circular/octagonal valve housing well (u 3.5..12.5, y 2.75..11.25, center 8,7)
        l.add(p.box(4.5, 3.5, 0.0, 11.5, 10.5, 0.35, "#rim_fe", "north"));
        l.add(p.box(5.0, 10.25, 0.0, 11.0, 11.25, 0.85, "#rim_fe", "north", "up", "down", "east", "west"));
        l.add(p.box(5.0, 2.75, 0.0, 11.0, 3.75, 0.85, "#rim_fe", "north", "up", "down", "east", "west"));
        l.add(p.box(3.5, 4.25, 0.0, 4.5, 9.75, 0.85, "#rim_fe", "north", "up", "down", "east", "west"));
        l.add(p.box(11.5, 4.25, 0.0, 12.5, 9.75, 0.85, "#rim_fe", "north", "up", "down", "east", "west"));
        // 4 stepped octagonal iron corner chamfers + tiny bolt studs (inset depth so zero coplanar faces)
        for (double[] c : new double[][]{{4.0, 9.75}, {11.0, 9.75}, {4.0, 3.25}, {11.0, 3.25}}) {
            l.add(p.box(c[0], c[1], 0.0, c[0] + 1.0, c[1] + 1.0, 0.78, "#rim_fe", "north", "up", "down", "east", "west"));
            l.add(p.box(c[0] + 0.15, c[1] + 0.15, 0.78, c[0] + 0.85, c[1] + 0.85, 0.96, "#rim_fe", "north", "up", "down", "east", "west"));
        }

        // Protruding 3D antique-bronze spoked handwheel (compact circular silhouette centered at 8,7, depth 0.95..1.7)
        l.add(p.box(6.0, 9.3, 0.95, 10.0, 10.1, 1.7, "#rim_br", "north", "south", "up", "down", "east", "west"));
        l.add(p.box(6.0, 3.9, 0.95, 10.0, 4.7, 1.7, "#rim_br", "north", "south", "up", "down", "east", "west"));
        l.add(p.box(4.9, 5.0, 0.95, 5.7, 9.0, 1.7, "#rim_br", "north", "south", "up", "down", "east", "west"));
        l.add(p.box(10.3, 5.0, 0.95, 11.1, 9.0, 1.7, "#rim_br", "north", "south", "up", "down", "east", "west"));
        // 4 octagonal wheel rim corner links (inset depth 1.0..1.65)
        for (double[] cr : new double[][]{{5.25, 8.75}, {9.85, 8.75}, {5.25, 4.35}, {9.85, 4.35}}) {
            l.add(p.box(cr[0], cr[1], 1.0, cr[0] + 0.9, cr[1] + 0.9, 1.65, "#rim_br", "north", "south", "up", "down", "east", "west"));
        }
        // 4 spokes cleanly meeting central hub (7.1..8.9, 6.1..7.9)
        l.add(p.box(5.7, 6.65, 1.1, 7.1, 7.35, 1.55, "#rim_br", "north", "south", "up", "down"));
        l.add(p.box(8.9, 6.65, 1.1, 10.3, 7.35, 1.55, "#rim_br", "north", "south", "up", "down"));
        l.add(p.box(7.65, 4.7, 1.1, 8.35, 6.1, 1.55, "#rim_br", "north", "south", "east", "west"));
        l.add(p.box(7.65, 7.9, 1.1, 8.35, 9.3, 1.55, "#rim_br", "north", "south", "east", "west"));
        // Central antique-brass hub & protruding cast-iron spindle nut
        l.add(p.box(7.1, 6.1, 0.35, 8.9, 7.9, 1.75, "#rim_br", "north", "up", "down", "east", "west"));
        l.add(p.box(7.45, 6.45, 1.75, 8.55, 7.55, 2.0, "#rim_fe", "north", "up", "down", "east", "west"));
    }

    /** 3D Cable Bushing: recessed copper chassis, bolted SLATE_IRON corner frame, and 6 flush antique-brass pipe socket collars. */
    private static void cableBushingModel() throws Exception {
        String[][] t = {
                {"copper", "atlas_rim_copper"}, {"iron", "atlas_rim_iron"},
                {"brass", "atlas_rim_wheel"}, {"core", "bushing_core"}
        };
        List<El> l = new ArrayList<>();
        // Recessed weathered copper body
        l.add(e(0.75, 0.75, 0.75, 15.25, 15.25, 15.25).all("#copper"));
        // 8 bolted SLATE_IRON corner blocks (0.2..2.2 & 13.8..15.8)
        for (double cx : new double[]{0.2, 13.8}) {
            for (double cy : new double[]{0.2, 13.8}) {
                for (double cz : new double[]{0.2, 13.8}) {
                    l.add(e(cx, cy, cz, cx + 2.0, cy + 2.0, cz + 2.0).all("#iron"));
                }
            }
        }
        // 12 SLATE_IRON perimeter bars inset slightly (0.3..1.75 & 14.25..15.7) so zero coplanar faces with corners
        for (double a : new double[]{0.3, 14.25}) {
            for (double b : new double[]{0.3, 14.25}) {
                l.add(e(2.2, a, b, 13.8, a + 1.45, b + 1.45).only("#iron", "north", "south", "up", "down"));
                l.add(e(a, 2.2, b, a + 1.45, 13.8, b + 1.45).only("#iron", "north", "south", "east", "west"));
                l.add(e(a, b, 2.2, a + 1.45, b + 1.45, 13.8).only("#iron", "east", "west", "up", "down"));
            }
        }
        // 6 symmetrical protruding antique-brass pipe socket flanges (flush 0.0 / 16.0) + glowing socket bore (#core)
        for (String dir : new String[]{"north", "south", "west", "east", "down", "up"}) {
            boolean min = dir.equals("north") || dir.equals("west") || dir.equals("down");
            double d0 = min ? 0.0 : 15.15, d1 = min ? 0.85 : 16.0;
            double cd0 = min ? 0.45 : 15.2, cd1 = min ? 0.8 : 15.55;
            if (dir.equals("north") || dir.equals("south")) {
                l.add(e(4.25, 10.25, d0, 11.75, 11.75, d1).only("#brass", dir, "east", "west", "up", "down"));
                l.add(e(4.25, 4.25, d0, 11.75, 5.75, d1).only("#brass", dir, "east", "west", "up", "down"));
                l.add(e(4.25, 5.75, d0, 5.75, 10.25, d1).only("#brass", dir, "east", "west"));
                l.add(e(10.25, 5.75, d0, 11.75, 10.25, d1).only("#brass", dir, "east", "west"));
                l.add(e(5.75, 5.75, cd0, 10.25, 10.25, cd1).face(dir, "#core", 0, 0, 16, 16).glow(13));
            } else if (dir.equals("west") || dir.equals("east")) {
                l.add(e(d0, 10.25, 4.25, d1, 11.75, 11.75).only("#brass", dir, "north", "south", "up", "down"));
                l.add(e(d0, 4.25, 4.25, d1, 5.75, 11.75).only("#brass", dir, "north", "south", "up", "down"));
                l.add(e(d0, 5.75, 4.25, d1, 10.25, 5.75).only("#brass", dir, "north", "south"));
                l.add(e(d0, 5.75, 10.25, d1, 10.25, 11.75).only("#brass", dir, "north", "south"));
                l.add(e(cd0, 5.75, 5.75, cd1, 10.25, 10.25).face(dir, "#core", 0, 0, 16, 16).glow(13));
            } else {
                l.add(e(4.25, d0, 10.25, 11.75, d1, 11.75).only("#brass", dir, "north", "south", "east", "west"));
                l.add(e(4.25, d0, 4.25, 11.75, d1, 5.75).only("#brass", dir, "north", "south", "east", "west"));
                l.add(e(4.25, d0, 5.75, 5.75, d1, 10.25).only("#brass", dir, "east", "west"));
                l.add(e(10.25, d0, 5.75, 11.75, d1, 10.25).only("#brass", dir, "east", "west"));
                l.add(e(5.75, cd0, 5.75, 10.25, cd1, 10.25).face(dir, "#core", 0, 0, 16, 16).glow(13));
            }
        }
        model("cable_bushing", false, "atlas_rim_copper", t, l.toArray(new El[0]));
    }

    /**
     * Builds a vertical glowing Atlas pillar variant (used by Atlas Conduit and formed Channel / Quantum Expanders).
     * Connected ends (down=true or up=true) omit horizontal collars, boots, and brass hoops so stacked pillars merge into
     * a single continuous glowing line without copper/brass dividers between blocks.
     */
    static void buildAtlasPillarVariant(String modelName, String coreTex, int lightLevel,
                                        boolean down, boolean up, boolean midBandWhenStandalone) throws Exception {
        String[][] t = {
                {"copper", "atlas_rim_copper"}, {"iron", "atlas_rim_iron"},
                {"brass", "atlas_rim_wheel"}, {"ice", coreTex}
        };
        List<El> l = new ArrayList<>();
        // Central seamless vertical crystal core (y=0..16), omitting internal seam faces where connected
        List<String> coreFaces = new ArrayList<>(List.of("north", "south", "east", "west"));
        if (!up) coreFaces.add("up");
        if (!down) coreFaces.add("down");
        l.add(e(3.75, 0.0, 3.75, 12.25, 16.0, 12.25).only("#ice", coreFaces.toArray(new String[0])).glow(lightLevel));

        // Bottom sealing collar, corner boots & brass base hoops ONLY at the bottom of a pillar run (!down)
        if (!down) {
            l.add(e(3.25, 0.15, 3.25, 12.75, 1.4, 12.75).only("#iron", "north", "south", "east", "west", "up", "down"));
            for (double cx : new double[]{0.0, 13.0}) {
                for (double cz : new double[]{0.0, 13.0}) {
                    l.add(e(cx, 0.0, cz, cx + 3.0, 2.0, cz + 3.0).all("#iron"));
                }
            }
            l.add(e(3.0, 0.25, 0.65, 13.0, 1.75, 2.65).only("#brass", "north", "south", "up", "down"));
            l.add(e(3.0, 0.25, 13.35, 13.0, 1.75, 15.35).only("#brass", "north", "south", "up", "down"));
            l.add(e(0.65, 0.25, 3.0, 2.65, 1.75, 13.0).only("#brass", "east", "west", "up", "down"));
            l.add(e(13.35, 0.25, 3.0, 15.35, 1.75, 13.0).only("#brass", "east", "west", "up", "down"));
        }

        // Top sealing collar, corner boots & brass capital hoops ONLY at the top of a pillar run (!up)
        if (!up) {
            l.add(e(3.25, 14.6, 3.25, 12.75, 15.85, 12.75).only("#iron", "north", "south", "east", "west", "up", "down"));
            for (double cx : new double[]{0.0, 13.0}) {
                for (double cz : new double[]{0.0, 13.0}) {
                    l.add(e(cx, 14.0, cz, cx + 3.0, 16.0, cz + 3.0).all("#iron"));
                }
            }
            l.add(e(3.0, 14.25, 0.65, 13.0, 15.75, 2.65).only("#brass", "north", "south", "up", "down"));
            l.add(e(3.0, 14.25, 13.35, 13.0, 15.75, 15.35).only("#brass", "north", "south", "up", "down"));
            l.add(e(0.65, 14.25, 3.0, 2.65, 15.75, 13.0).only("#brass", "east", "west", "up", "down"));
            l.add(e(13.35, 14.25, 3.0, 15.35, 15.75, 13.0).only("#brass", "east", "west", "up", "down"));
        }

        boolean midBand = midBandWhenStandalone && !down && !up;
        for (double cx : new double[]{0.0, 13.0}) {
            for (double cz : new double[]{0.0, 13.0}) {
                double sx = cx == 0.0 ? 0.35 : 13.15;
                double sz = cz == 0.0 ? 0.35 : 13.15;
                if (midBand) {
                    l.add(e(cx, 7.0, cz, cx + 3.0, 9.0, cz + 3.0).all("#iron"));
                    l.add(e(sx, 2.0, sz, sx + 2.5, 7.0, sz + 2.5).only("#copper", "north", "south", "east", "west"));
                    l.add(e(sx, 9.0, sz, sx + 2.5, 14.0, sz + 2.5).only("#copper", "north", "south", "east", "west"));
                } else {
                    double y0 = down ? 0.0 : 2.0;
                    double y1 = up ? 16.0 : 14.0;
                    l.add(e(sx, y0, sz, sx + 2.5, y1, sz + 2.5).only("#copper", "north", "south", "east", "west"));
                }
            }
        }
        if (midBand) {
            l.add(e(3.0, 7.25, 0.65, 13.0, 8.75, 2.65).only("#brass", "north", "south", "up", "down"));
            l.add(e(3.0, 7.25, 13.35, 13.0, 8.75, 15.35).only("#brass", "north", "south", "up", "down"));
            l.add(e(0.65, 7.25, 3.0, 2.65, 8.75, 13.0).only("#brass", "east", "west", "up", "down"));
            l.add(e(13.35, 7.25, 3.0, 15.35, 8.75, 13.0).only("#brass", "east", "west", "up", "down"));
        }
        model(modelName, false, "atlas_rim_copper", t, l.toArray(new El[0]));
    }

    /** Outer pipe socket collar (facing NORTH, z=0..3.75) when a network pipe docks onto a formed Expander pillar. */
    private static void atlasPillarSocketModel() throws Exception {
        String[][] t = {{"iron", "atlas_rim_iron"}, {"brass", "atlas_rim_wheel"}};
        List<El> l = new ArrayList<>();
        l.add(e(3.25, 3.25, 0.85, 12.75, 12.75, 3.75).only("#iron", "east", "west", "up", "down"));
        l.add(e(2.5, 2.5, 0.0, 13.5, 13.5, 0.85).all("#brass"));
        model("atlas_pillar_socket", false, "atlas_rim_iron", t, l.toArray(new El[0]));
    }

    /** 3D Atlas Conduit & Expander connected pillar variants + blockstates (uniform lightLevel=14 for seamless cross-block gradients). */
    private static void atlasConduitModel() throws Exception {
        buildAtlasPillarVariant("atlas_conduit", "conduit_ice", 14, false, false, true);
        buildAtlasPillarVariant("atlas_conduit_bottom", "conduit_ice", 14, false, true, false);
        buildAtlasPillarVariant("atlas_conduit_mid", "conduit_ice", 14, true, true, false);
        buildAtlasPillarVariant("atlas_conduit_top", "conduit_ice", 14, true, false, false);
        write(RES.resolve("blockstates/atlas_conduit.json"),
                "{\n  \"variants\": {\n"
                        + "    \"down=false,up=false\": {\"model\": \"" + NS + "atlas_conduit\"},\n"
                        + "    \"down=false,up=true\": {\"model\": \"" + NS + "atlas_conduit_bottom\"},\n"
                        + "    \"down=true,up=true\": {\"model\": \"" + NS + "atlas_conduit_mid\"},\n"
                        + "    \"down=true,up=false\": {\"model\": \"" + NS + "atlas_conduit_top\"}\n"
                        + "  }\n}\n");

        atlasPillarSocketModel();
        buildExpanderPillars("channel_expander", "conduit_blue", 14);
        buildExpanderPillars("quantum_channel_expander", "conduit_quantum", 14);
    }

    static void buildExpanderPillars(String name, String coreTex, int lightLevel) throws Exception {
        buildAtlasPillarVariant(name + "_pillar_single", coreTex, lightLevel, false, false, false);
        buildAtlasPillarVariant(name + "_pillar_bottom", coreTex, lightLevel, false, true, false);
        buildAtlasPillarVariant(name + "_pillar_mid", coreTex, lightLevel, true, true, false);
        buildAtlasPillarVariant(name + "_pillar_top", coreTex, lightLevel, true, false, false);
    }

    static void expanderBlockstate(String name) throws Exception {
        write(RES.resolve("blockstates/" + name + ".json"),
                "{\n  \"multipart\": [\n"
                        + "    {\"when\": {\"formed\": \"false\"}, \"apply\": {\"model\": \"" + NS + name + "\"}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"down\": \"false\", \"up\": \"false\"}, \"apply\": {\"model\": \"" + NS + name + "_pillar_single\"}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"down\": \"false\", \"up\": \"true\"}, \"apply\": {\"model\": \"" + NS + name + "_pillar_bottom\"}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"down\": \"true\", \"up\": \"true\"}, \"apply\": {\"model\": \"" + NS + name + "_pillar_mid\"}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"down\": \"true\", \"up\": \"false\"}, \"apply\": {\"model\": \"" + NS + name + "_pillar_top\"}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"pipe\": \"true\", \"facing\": \"north\"}, \"apply\": {\"model\": \"" + NS + "atlas_pillar_socket\"}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"pipe\": \"true\", \"facing\": \"east\"}, \"apply\": {\"model\": \"" + NS + "atlas_pillar_socket\", \"y\": 90}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"pipe\": \"true\", \"facing\": \"south\"}, \"apply\": {\"model\": \"" + NS + "atlas_pillar_socket\", \"y\": 180}},\n"
                        + "    {\"when\": {\"formed\": \"true\", \"pipe\": \"true\", \"facing\": \"west\"}, \"apply\": {\"model\": \"" + NS + "atlas_pillar_socket\", \"y\": 270}}\n"
                        + "  ]\n}\n");
    }

    /** 3D Atlas Core: sculpted runic crucible + openwork lantern cage raising the cyan beacon visibly above the 22.5px rail. */
    private static void atlasCoreModel() throws Exception {
        String[][] t = {
                {"casing", "atlas_casing"}, {"rune", "atlas_core"}, {"copper", "atlas_rim_copper"},
                {"iron", "atlas_rim_iron"}, {"brass", "atlas_rim_wheel"}, {"crystal", "core_crystal"}
        };
        List<El> l = new ArrayList<>();
        // Anchoring plate & recessed glowing runic crucible core
        l.add(e(0.0, 0.0, 0.0, 16.0, 3.0, 16.0).all("#casing"));
        l.add(e(2.0, 3.0, 2.0, 14.0, 10.0, 14.0).only("#rune", "north", "south", "east", "west", "up").glow(10));
        // 4 corner copper buttresses + SLATE_IRON crucible capitals
        for (double cx : new double[]{0.5, 12.75}) {
            for (double cz : new double[]{0.5, 12.75}) {
                l.add(e(cx, 3.0, cz, cx + 2.75, 10.25, cz + 2.75).only("#copper", "north", "south", "east", "west"));
                double kx = cx == 0.5 ? 0.4 : 12.65;
                double kz = cz == 0.5 ? 0.4 : 12.65;
                l.add(e(kx, 10.25, kz, kx + 2.95, 11.5, kz + 2.95).all("#iron"));
            }
        }
        // Stepped warm brass crucible bridge bars spanning cleanly between capitals (3.35..12.65)
        l.add(e(3.35, 10.0, 1.25, 12.65, 11.25, 2.75).only("#brass", "north", "south", "up", "down"));
        l.add(e(3.35, 10.0, 13.25, 12.65, 11.25, 14.75).only("#brass", "north", "south", "up", "down"));
        l.add(e(1.25, 10.0, 3.35, 2.75, 11.25, 12.65).only("#brass", "east", "west", "up", "down"));
        l.add(e(13.25, 10.0, 3.35, 14.75, 11.25, 12.65).only("#brass", "east", "west", "up", "down"));
        // Central SLATE_IRON crystal plinth
        l.add(e(4.5, 10.0, 4.5, 11.5, 13.0, 11.5).only("#iron", "north", "south", "east", "west", "up"));
        // Raised multi-tiered glowing frost-cyan crystal beacon (peaks y=27 above 22.5px balcony rail)
        l.add(e(5.25, 13.0, 5.25, 10.75, 16.5, 10.75).only("#crystal", "north", "south", "east", "west").glow(15));
        l.add(e(4.5, 16.5, 4.5, 11.5, 22.5, 11.5).all("#crystal").glow(15));
        l.add(e(5.25, 22.5, 5.25, 10.75, 25.0, 10.75).only("#crystal", "north", "south", "east", "west", "up").glow(15));
        l.add(e(6.5, 25.0, 6.5, 9.5, 27.0, 9.5).only("#crystal", "north", "south", "east", "west", "up").glow(15));
        // Openwork antique-brass lantern cage struts (r <= 0.42 so orbiting golems at ORBIT_RADIUS=0.62 clear cleanly)
        for (double lx : new double[]{3.25, 11.85}) {
            for (double lz : new double[]{3.25, 11.85}) {
                l.add(e(lx, 11.5, lz, lx + 0.9, 21.5, lz + 0.9).only("#brass", "north", "south", "east", "west"));
            }
        }
        // Hollow upper brass lantern halo ring (y=21.5..22.4, clearing 4.5..11.5 crystal) + 4 SLATE_IRON finials
        l.add(e(3.0, 21.5, 3.0, 13.0, 22.4, 4.25).all("#brass"));
        l.add(e(3.0, 21.5, 11.75, 13.0, 22.4, 13.0).all("#brass"));
        l.add(e(3.0, 21.5, 4.25, 4.25, 22.4, 11.75).only("#brass", "east", "west", "up", "down"));
        l.add(e(11.75, 21.5, 4.25, 13.0, 22.4, 11.75).only("#brass", "east", "west", "up", "down"));
        for (double fx : new double[]{3.05, 11.95}) {
            for (double fz : new double[]{3.05, 11.95}) {
                l.add(e(fx, 22.4, fz, fx + 1.0, 23.5, fz + 1.0).only("#iron", "north", "south", "east", "west", "up"));
            }
        }
        model("atlas_core", false, "atlas_core", t, l.toArray(new El[0]));
    }

    static void generate() throws Exception {
        // ============================================================ multiblock
        write(RES.resolve("models/block/atlas_casing.json"),
                "{\n  \"parent\": \"minecraft:block/cube_bottom_top\",\n  \"textures\": {\n    \"top\": \"" + NS + "atlas_casing_top\",\n"
                        + "    \"bottom\": \"" + NS + "atlas_casing\",\n    \"side\": \"" + NS + "atlas_casing\"\n  }\n}\n");
        casingParts();
        item("atlas_casing", "atlas_casing");

        cableBushingModel();
        single("cable_bushing", "cable_bushing");
        item("cable_bushing", "cable_bushing");

        atlasConduitModel();
        item("atlas_conduit", "atlas_conduit");

        cube("channel_expander", "channel_expander");
        expanderBlockstate("channel_expander");
        item("channel_expander", "channel_expander");
        cube("quantum_channel_expander", "quantum_channel_expander");
        expanderBlockstate("quantum_channel_expander");
        item("quantum_channel_expander", "quantum_channel_expander");

        atlasCoreModel();
        single("atlas_core", "atlas_core");
        item("atlas_core", "atlas_core");
        // Atlant: a caryatid in the iron golem's manner (tools/Atlant.java draws it), a broad torso on short legs, the arms
        // raised to hold the array; plain boxes, the default UVs keep every face at the same texel density
        model("atlas_atlant_lower", false, "atlant_body",
                new String[][]{{"body", "atlant_body"}, {"limb", "atlant_limb"}, {"belt", "atlant_belt"}},
                e(2.5, 0, 5.5, 7.5, 1.5, 10.5).all("#belt"), e(8.5, 0, 5.5, 13.5, 1.5, 10.5).all("#belt"),
                e(3, 1.5, 6, 7, 10, 10).all("#limb"), e(9, 1.5, 6, 13, 10, 10).all("#limb"),
                e(4.5, 10, 6, 11.5, 13, 10).all("#belt"),
                e(1.5, 13, 4, 14.5, 16, 12).all("#body"));
        model("atlas_atlant_upper", false, "atlant_body",
                new String[][]{{"body", "atlant_body"}, {"limb", "atlant_limb"}, {"belt", "atlant_belt"}},
                e(1.5, 0, 4, 14.5, 6, 12).all("#body"),
                e(0, 2, 6, 3, 14, 10).all("#limb"), e(13, 2, 6, 16, 14, 10).all("#limb"),
                e(0, 14, 5, 3.5, 16, 11).all("#belt"), e(12.5, 14, 5, 16, 16, 11).all("#belt"));
        // the head is separate: the renderer (AtlantHeadRenderer) turns it to the player, the neck is at (8, 6, 8)
        model("atlas_atlant_head", false, "atlant_head",
                new String[][]{{"body", "atlant_body"}, {"head", "atlant_head"}},
                // the iron golem's head in proportion to the statue (6 x 7 x 6 on a 13-wide torso), the long nose low on the face
                e(5, 6, 3, 11, 13, 9).all("#body").face("north", "#head"),
                e(7.25, 7, 1.5, 8.75, 9.5, 3).all("#head"));
        facing4Half("atlas_atlant");
        item("atlas_atlant", "atlas_atlant_lower");

        // the cabinet, buses and pipes are built by the second generation of graphics (tools/Forge2.java, Buses.java, Pipes.java)

        // ============================================================ cryo blocks
        // Cryo-Calculator (slides and the "matrix") and the Cooling Co-Processor (flywheel fins) are in Gen3: their parts move
        single("cooling_coprocessor", "cooling_coprocessor");

        // the resonator is built by tools/Resonator.java

        // ============================================================== jar
        model("creative_source_jar", true, "jar_glass",
                new String[][]{{"glass", "jar_glass"}, {"liquid", "jar_liquid"}, {"brass", "brass_plate"}, {"cork", "cork"}},
                e(4, 1, 4, 12, 11, 12).all("#liquid"),
                e(3, 0, 3, 13, 12, 13).all("#glass"),
                e(4, 12, 4, 12, 13, 12).all("#brass"),
                e(5, 13, 5, 11, 15, 11).all("#cork"));
        single("creative_source_jar", "creative_source_jar");
        item("creative_source_jar", "creative_source_jar");

        // pattern provider: the models by feeding side are written by Forge2.providerModels

    }

    /** Atlant block state: two halves, 4 directions. */
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
