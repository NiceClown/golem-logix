import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * Offline preview of block JSON models (isometric, z-buffer). Does not depend on Minecraft.
 * Run: java -cp <out> ModelPreview <output.png> <angle: 0 = from NE, 1 = from NW> name1 name2 ...
 */
public class ModelPreview {
    static final Path RES = Path.of("src/main/resources/assets/golem_logix");
    static final Map<String, BufferedImage> TEX = new HashMap<>();

    static class Face {
        double[][] corners = new double[4][3]; // TL TR BR BL
        double[] uv;
        BufferedImage tex;
        double shade;
    }

    static BufferedImage tex(String ref) throws Exception {
        String key = ref.replace("golem_logix:", "");
        if (TEX.containsKey(key)) return TEX.get(key);
        // vanilla textures come from a folder of extracted ones, if given (-Dpreview.vanilla=dir, files by their base name)
        File f = key.startsWith("minecraft:")
                ? new File(System.getProperty("preview.vanilla", "."), key.substring(key.lastIndexOf('/') + 1) + ".png")
                : RES.resolve("textures/" + key + ".png").toFile();
        BufferedImage img = f.exists() ? ImageIO.read(f) : null;
        TEX.put(key, img);
        return img;
    }

    static String json(String s, String key) {
        return null;
    }

    public static void main(String[] a) throws Exception {
        String out = a[0];
        int angle = Integer.parseInt(a[1]);
        if (a.length > 2 && a[2].startsWith("scene:")) {
            int sz = a.length > 4 ? Integer.parseInt(a[4]) : 900;
            double sc = Double.parseDouble(a[3]);
            ImageIO.write(scene(Files.readString(Path.of(a[2].substring(6))), angle, sz, sc <= 0 ? 0 : sc * sz / 900.0), "png", new File(out));
            return;
        }
        List<String> names = new ArrayList<>(Arrays.asList(a).subList(2, a.length));
        int cell = 260, cols = Math.min(4, names.size());
        int rows = (names.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * cell, rows * (cell + 16), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(105, 112, 128));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < names.size(); i++) {
            BufferedImage img = render(names.get(i), angle, cell);
            g.drawImage(img, (i % cols) * cell, (i / cols) * (cell + 16), null);
            g.setColor(Color.WHITE);
            g.drawString(names.get(i), (i % cols) * cell + 4, (i / cols) * (cell + 16) + cell + 12);
        }
        ImageIO.write(sheet, "png", new File(out));
    }

    // ------------------------------------------------------------------ JSON (minimal parsing)
    static Object parse(String s) {
        return new P(s).value();
    }

    static class P {
        final String s;
        int i = 0;

        P(String s) {
            this.s = s;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                Map<String, Object> m = new LinkedHashMap<>();
                i++;
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    String k = (String) value();
                    ws();
                    i++; // :
                    m.put(k, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            } else if (c == '[') {
                List<Object> l = new ArrayList<>();
                i++;
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            } else if (c == '"') {
                int j = s.indexOf('"', i + 1);
                String r = s.substring(i + 1, j);
                i = j + 1;
                return r;
            } else {
                int j = i;
                while (j < s.length() && ",}] \n\r\t".indexOf(s.charAt(j)) < 0) j++;
                String r = s.substring(i, j);
                i = j;
                if (r.equals("true")) return Boolean.TRUE;
                if (r.equals("false")) return Boolean.FALSE;
                return Double.parseDouble(r);
            }
        }
    }

    static BufferedImage render(String name, int angle, int size) throws Exception {
        return raster(facesOf(name), angle, size, size / 32.0);
    }

    /** A scene of blocks: "model@x,y,z@rotationY" separated by spaces. */
    static BufferedImage scene(String spec, int angle, int size, double scale) throws Exception {
        List<Face> all = new ArrayList<>();
        for (String part : spec.split(" ")) {
            String[] p = part.split("@");
            String[] c = p[1].split(",");
            int ox = Integer.parseInt(c[0]) * 16, oy = Integer.parseInt(c[1]) * 16, oz = Integer.parseInt(c[2]) * 16;
            double rot = Math.toRadians(p.length > 2 ? Double.parseDouble(p[2]) : 0);
            for (Face f : facesOf(p[0])) {
                Face g = new Face();
                g.tex = f.tex;
                g.uv = f.uv;
                g.shade = f.shade;
                g.corners = new double[4][3];
                for (int k = 0; k < 4; k++) {
                    double x = f.corners[k][0] - 8, z = f.corners[k][2] - 8;
                    g.corners[k][0] = x * Math.cos(rot) + z * Math.sin(rot) + 8 + ox;
                    g.corners[k][1] = f.corners[k][1] + oy;
                    g.corners[k][2] = -x * Math.sin(rot) + z * Math.cos(rot) + 8 + oz;
                }
                all.add(g);
            }
        }
        return raster(all, angle, size, scale);
    }

    @SuppressWarnings("unchecked")
    static List<Face> facesOf(String name) throws Exception {
        Map<String, Object> model = (Map<String, Object>) parse(Files.readString(RES.resolve("models/block/" + name + ".json")));
        Map<String, String> textures = new LinkedHashMap<>();
        Map<String, Object> tx = (Map<String, Object>) model.get("textures");
        if (tx != null) for (var e : tx.entrySet()) textures.put(e.getKey(), (String) e.getValue());
        String parent = (String) model.getOrDefault("parent", "");
        List<Face> faces = new ArrayList<>();
        List<Map<String, Object>> elements = (List<Map<String, Object>>) model.get("elements");
        if (elements == null) {
            elements = new ArrayList<>();
            Map<String, Object> cube = new LinkedHashMap<>();
            cube.put("from", List.of(0.0, 0.0, 0.0));
            cube.put("to", List.of(16.0, 16.0, 16.0));
            Map<String, Object> fs = new LinkedHashMap<>();
            for (String d : new String[]{"north", "south", "east", "west", "up", "down"}) {
                String t = parent.endsWith("cube_all") ? "#all" : "#" + d;
                if (parent.endsWith("cube_bottom_top")) {
                    t = d.equals("up") ? "#top" : d.equals("down") ? "#bottom" : "#side";
                }
                if (parent.endsWith("orientable_with_bottom")) {
                    t = d.equals("north") ? "#front" : d.equals("up") ? "#top" : d.equals("down") ? "#bottom" : "#side";
                }
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("texture", t);
                fs.put(d, f);
            }
            cube.put("faces", fs);
            elements.add(cube);
        }
        for (Map<String, Object> el : elements) {
            List<Object> fr = (List<Object>) el.get("from"), to = (List<Object>) el.get("to");
            double[] f = {(Double) fr.get(0), (Double) fr.get(1), (Double) fr.get(2)};
            double[] t = {(Double) to.get(0), (Double) to.get(1), (Double) to.get(2)};
            Map<String, Object> rot = (Map<String, Object>) el.get("rotation");
            Map<String, Object> fs = (Map<String, Object>) el.get("faces");
            for (var fe : fs.entrySet()) {
                Map<String, Object> fd = (Map<String, Object>) fe.getValue();
                String texRef = (String) fd.get("texture");
                String resolved = texRef;
                int guard = 0;
                while (resolved != null && resolved.startsWith("#") && guard++ < 5) resolved = textures.get(resolved.substring(1));
                BufferedImage img = resolved == null ? null : tex(resolved);
                if (img == null) continue;
                Face face = new Face();
                face.tex = img;
                String d = fe.getKey();
                double[][] c;
                double[] uv;
                switch (d) {
                    case "north" -> {
                        c = new double[][]{{t[0], t[1], f[2]}, {f[0], t[1], f[2]}, {f[0], f[1], f[2]}, {t[0], f[1], f[2]}};
                        uv = new double[]{16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]};
                        face.shade = 0.8;
                    }
                    case "south" -> {
                        c = new double[][]{{f[0], t[1], t[2]}, {t[0], t[1], t[2]}, {t[0], f[1], t[2]}, {f[0], f[1], t[2]}};
                        uv = new double[]{f[0], 16 - t[1], t[0], 16 - f[1]};
                        face.shade = 0.8;
                    }
                    case "east" -> {
                        c = new double[][]{{t[0], t[1], t[2]}, {t[0], t[1], f[2]}, {t[0], f[1], f[2]}, {t[0], f[1], t[2]}};
                        uv = new double[]{16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]};
                        face.shade = 0.6;
                    }
                    case "west" -> {
                        c = new double[][]{{f[0], t[1], f[2]}, {f[0], t[1], t[2]}, {f[0], f[1], t[2]}, {f[0], f[1], f[2]}};
                        uv = new double[]{f[2], 16 - t[1], t[2], 16 - f[1]};
                        face.shade = 0.6;
                    }
                    case "up" -> {
                        c = new double[][]{{f[0], t[1], f[2]}, {t[0], t[1], f[2]}, {t[0], t[1], t[2]}, {f[0], t[1], t[2]}};
                        uv = new double[]{f[0], f[2], t[0], t[2]};
                        face.shade = 1.0;
                    }
                    default -> {
                        c = new double[][]{{f[0], f[1], t[2]}, {t[0], f[1], t[2]}, {t[0], f[1], f[2]}, {f[0], f[1], f[2]}};
                        uv = new double[]{f[0], 16 - t[2], t[0], 16 - f[2]};
                        face.shade = 0.5;
                    }
                }
                List<Object> u = (List<Object>) fd.get("uv");
                if (u != null) uv = new double[]{(Double) u.get(0), (Double) u.get(1), (Double) u.get(2), (Double) u.get(3)};
                face.uv = uv;
                if (rot != null) {
                    List<Object> o = (List<Object>) rot.get("origin");
                    double ang = Math.toRadians((Double) rot.get("angle"));
                    String ax = (String) rot.get("axis");
                    for (double[] p : c) {
                        double x = p[0] - (Double) o.get(0), y = p[1] - (Double) o.get(1), z = p[2] - (Double) o.get(2), nx = x, ny = y, nz = z;
                        switch (ax) {
                            case "y" -> {
                                nx = x * Math.cos(ang) + z * Math.sin(ang);
                                nz = -x * Math.sin(ang) + z * Math.cos(ang);
                            }
                            case "x" -> {
                                ny = y * Math.cos(ang) - z * Math.sin(ang);
                                nz = y * Math.sin(ang) + z * Math.cos(ang);
                            }
                            default -> {
                                nx = x * Math.cos(ang) - y * Math.sin(ang);
                                ny = x * Math.sin(ang) + y * Math.cos(ang);
                            }
                        }
                        p[0] = nx + (Double) o.get(0);
                        p[1] = ny + (Double) o.get(1);
                        p[2] = nz + (Double) o.get(2);
                    }
                }
                face.corners = c;
                faces.add(face);
            }
        }
        return faces;
    }

    static BufferedImage raster(List<Face> faces, int angle, int size, double scale) {
        // camera: behind, right and above; angle 0 sees north+east+up, 1 north+west+up
        double[] d = angle == 0 ? new double[]{1, 1.1, -1} : new double[]{-1, 1.1, -1};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        for (int i = 0; i < 3; i++) d[i] /= len;
        autoFit = scale <= 0;
        double[] up = {0, 1, 0};
        double[] r = {up[1] * (-d[2]) - up[2] * (-d[1]), up[2] * (-d[0]) - up[0] * (-d[2]), up[0] * (-d[1]) - up[1] * (-d[0])};
        double rl = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        for (int i = 0; i < 3; i++) r[i] /= rl;
        double[] u = {(-d[1]) * r[2] - (-d[2]) * r[1], (-d[2]) * r[0] - (-d[0]) * r[2], (-d[0]) * r[1] - (-d[1]) * r[0]};
        double offX = 0, offY = 0;
        if (scale <= 0) {
            // auto-fit: fit the projection of all faces into the frame
            double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
            for (Face f : faces) {
                for (double[] c : f.corners) {
                    double x = c[0] - 8, y = c[1] - 8, z = c[2] - 8;
                    double sx = x * r[0] + y * r[1] + z * r[2], sy = -(x * u[0] + y * u[1] + z * u[2]);
                    minX = Math.min(minX, sx);
                    maxX = Math.max(maxX, sx);
                    minY = Math.min(minY, sy);
                    maxY = Math.max(maxY, sy);
                }
            }
            scale = size * 0.94 / Math.max(maxX - minX, maxY - minY);
            offX = -(minX + maxX) / 2 * scale;
            offY = -(minY + maxY) / 2 * scale;
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double[] zbuf = new double[size * size];
        Arrays.fill(zbuf, -1e9);
        // opaque first, transparent after
        for (int pass = 0; pass < 2; pass++) {
            for (Face f : faces) {
                boolean translucent = hasAlpha(f.tex);
                if ((pass == 0) == translucent) continue;
                double[][] sp = new double[4][3];
                for (int k = 0; k < 4; k++) {
                    double x = f.corners[k][0] - 8, y = f.corners[k][1] - 8, z = f.corners[k][2] - 8;
                    sp[k][0] = size / 2.0 + offX + (x * r[0] + y * r[1] + z * r[2]) * scale;
                    sp[k][1] = size / 2.0 + offY + (scaleFixedY() ? 12 : 0) - (x * u[0] + y * u[1] + z * u[2]) * scale;
                    sp[k][2] = x * d[0] + y * d[1] + z * d[2];
                }
                int[][] tri = {{0, 1, 2}, {0, 2, 3}};
                double[][] uvc = {{f.uv[0], f.uv[1]}, {f.uv[2], f.uv[1]}, {f.uv[2], f.uv[3]}, {f.uv[0], f.uv[3]}};
                for (int[] tr : tri) fillTri(img, zbuf, sp, uvc, tr, f);
            }
        }
        return img;
    }

    static boolean autoFit = false;

    static boolean scaleFixedY() {
        return !autoFit;
    }

    static boolean hasAlpha(BufferedImage t) {
        for (int j = 0; j < t.getHeight(); j += 2)
            for (int i = 0; i < t.getWidth(); i += 2) if ((t.getRGB(i, j) >>> 24) < 250) return true;
        return false;
    }

    static void fillTri(BufferedImage img, double[] zb, double[][] sp, double[][] uvc, int[] tr, Face f) {
        double[] a = sp[tr[0]], b = sp[tr[1]], c = sp[tr[2]];
        int minX = (int) Math.floor(Math.min(a[0], Math.min(b[0], c[0]))), maxX = (int) Math.ceil(Math.max(a[0], Math.max(b[0], c[0])));
        int minY = (int) Math.floor(Math.min(a[1], Math.min(b[1], c[1]))), maxY = (int) Math.ceil(Math.max(a[1], Math.max(b[1], c[1])));
        double den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
        if (Math.abs(den) < 1e-9) return;
        for (int y = Math.max(0, minY); y <= Math.min(img.getHeight() - 1, maxY); y++)
            for (int x = Math.max(0, minX); x <= Math.min(img.getWidth() - 1, maxX); x++) {
                double px = x + 0.5, py = y + 0.5;
                double w0 = ((b[1] - c[1]) * (px - c[0]) + (c[0] - b[0]) * (py - c[1])) / den;
                double w1 = ((c[1] - a[1]) * (px - c[0]) + (a[0] - c[0]) * (py - c[1])) / den;
                double w2 = 1 - w0 - w1;
                if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) continue;
                double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                if (z < zb[y * img.getWidth() + x] - 1e-6) continue;
                double uu = w0 * uvc[tr[0]][0] + w1 * uvc[tr[1]][0] + w2 * uvc[tr[2]][0];
                double vv = w0 * uvc[tr[0]][1] + w1 * uvc[tr[1]][1] + w2 * uvc[tr[2]][1];
                int tx = (int) Math.max(0, Math.min(f.tex.getWidth() - 1, Math.floor(uu / 16 * f.tex.getWidth())));
                int ty = (int) Math.max(0, Math.min(f.tex.getHeight() - 1, Math.floor(vv / 16 * f.tex.getHeight())));
                int col = f.tex.getRGB(tx, ty);
                int al = col >>> 24;
                if (al == 0) continue;
                int rr = (int) (((col >> 16) & 255) * f.shade), gg = (int) (((col >> 8) & 255) * f.shade), bb = (int) ((col & 255) * f.shade);
                int dst = img.getRGB(x, y);
                double aa = al / 255.0;
                int da = dst >>> 24;
                int or = (int) (rr * aa + ((dst >> 16) & 255) * (1 - aa) * (da / 255.0)), og = (int) (gg * aa + ((dst >> 8) & 255) * (1 - aa) * (da / 255.0)),
                        ob = (int) (bb * aa + (dst & 255) * (1 - aa) * (da / 255.0));
                int oa = (int) Math.min(255, al + da * (1 - aa));
                img.setRGB(x, y, (oa << 24) | (or << 16) | (og << 8) | ob);
                if (al >= 250) zb[y * img.getWidth() + x] = z;
            }
    }
}
