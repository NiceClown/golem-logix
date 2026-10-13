import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Audit of the mod's resources: references from models/blockstates/items/Java to textures and models, missing files and "orphaned"
 * textures and models. Run from the project root: java tools/Audit.java
 */
public class Audit {
    static final Path ASSETS = Path.of("src/main/resources/assets/golem_logix");
    static final Path SRC = Path.of("src/main/java");

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A flat "key": "value" map from a translation json file. */
    static Map<String, String> jsonStrings(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile("\"([^\"\\\\]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        return out;
    }

    static List<Path> files(Path dir, String ext) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(ext)).sorted().collect(Collectors.toList());
        }
    }

    public static void main(String[] args) throws Exception {
        Set<String> textures = new TreeSet<>(), models = new TreeSet<>();
        for (Path p : files(ASSETS.resolve("textures"), ".png")) {
            textures.add(ASSETS.resolve("textures").relativize(p).toString().replace('\\', '/').replaceAll("\\.png$", ""));
        }
        for (Path p : files(ASSETS.resolve("models"), ".json")) {
            models.add(ASSETS.resolve("models").relativize(p).toString().replace('\\', '/').replaceAll("\\.json$", ""));
        }
        Set<String> usedTex = new HashSet<>(), usedModels = new HashSet<>();
        List<String> problems = new ArrayList<>();
        Pattern ref = Pattern.compile("\"(golem_logix:[a-z0-9_/]+|minecraft:[a-z0-9_/]+)\"");
        Pattern texRef = Pattern.compile("\"(?:parent|texture|[a-z_]+)\"\\s*:\\s*\"golem_logix:((?:block|item|entity|gui)/[a-z0-9_/]+)\"");

        for (Path p : files(ASSETS.resolve("models"), ".json")) {
            String s = read(p);
            // the parent is a model, the values in "textures" are textures
            Matcher pm = Pattern.compile("\"parent\"\\s*:\\s*\"golem_logix:([a-z0-9_/]+)\"").matcher(s);
            while (pm.find()) {
                if (models.contains(pm.group(1))) {
                    usedModels.add(pm.group(1));
                } else {
                    problems.add(p.getFileName() + ": missing parent model " + pm.group(1));
                }
            }
            int ti = s.indexOf("\"textures\"");
            if (ti >= 0) {
                int open = s.indexOf('{', ti), close = s.indexOf('}', open);
                Matcher tm = Pattern.compile("\"golem_logix:([a-z0-9_/]+)\"").matcher(s.substring(open, close));
                while (tm.find()) {
                    if (textures.contains(tm.group(1))) {
                        usedTex.add("textures/" + tm.group(1));
                    } else {
                        problems.add(p.getFileName() + ": missing texture " + tm.group(1));
                    }
                }
            }
        }
        for (Path p : files(ASSETS.resolve("blockstates"), ".json")) {
            Matcher m = Pattern.compile("\"golem_logix:([a-z0-9_/]+)\"").matcher(read(p));
            while (m.find()) {
                String r = m.group(1);
                if (models.contains(r)) {
                    usedModels.add(r);
                } else {
                    problems.add("blockstate " + p.getFileName() + ": missing model " + r);
                }
            }
        }
        // textures/... in Java and resources (GUI, renderers)
        Pattern javaTex = Pattern.compile("\"(textures/[a-z0-9_/]+)\\.png\"|\"(textures/[a-z0-9_/]+/)\"\\s*\\+\\s*[a-zA-Z]");
        List<Path> sources = files(SRC, ".java");
        for (Path p : sources) {
            String s = read(p);
            Matcher m = Pattern.compile("\"((?:textures/)?(?:block|item|entity|gui)[a-z0-9_/]*[a-z0-9_])(?:\\.png)?\"").matcher(s);
            while (m.find()) {
                String r = m.group(1).replaceAll("^textures/", "");
                if (textures.contains(r)) {
                    usedTex.add("textures/" + r);
                }
            }
            Matcher m2 = Pattern.compile("textures/((?:block|item|entity|gui)/[a-z0-9_/]+)\\.png").matcher(s);
            while (m2.find()) {
                if (textures.contains(m2.group(1))) {
                    usedTex.add("textures/" + m2.group(1));
                } else {
                    problems.add(p.getFileName() + ": missing texture " + m2.group(1));
                }
            }
            // names like "bay_" + name or prefix[i] count as used by their common prefix
            Matcher m3 = Pattern.compile("\"((?:textures/)?(?:block|item|entity|gui/book)/)?([a-z0-9_]+_)\"\\s*\\+").matcher(s);
            while (m3.find()) {
                for (String t : textures) {
                    if (t.contains("/" + m3.group(2)) || t.startsWith(m3.group(2))) {
                        usedTex.add("textures/" + t);
                    }
                }
            }
        }
        // the book and other resources
        for (Path p : files(ASSETS.resolve("book"), ".json")) {
            Matcher m = Pattern.compile("\"(?:textures/)?((?:block|item|entity|gui)/[a-z0-9_/]+)(?:\\.png)?\"").matcher(read(p));
            while (m.find()) {
                if (textures.contains(m.group(1))) {
                    usedTex.add("textures/" + m.group(1));
                }
            }
        }
        // items: textures/item/<item name>.png is picked up by the model item/<name>.json through generated
        System.out.println("=== unreferenced textures ===");
        for (String t : textures) {
            if (!usedTex.contains("textures/" + t)) {
                System.out.println("  textures/" + t);
            }
        }
        System.out.println("=== unreferenced models ===");
        for (String m : models) {
            boolean item = m.startsWith("item/");
            if (!usedModels.contains(m) && !item) {
                System.out.println("  models/" + m);
            }
        }
        System.out.println("=== sizes not a multiple of 16 (limit mip levels) ===");
        for (String t : textures) {
            if (t.startsWith("gui/")) {
                continue;
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(ASSETS.resolve("textures/" + t + ".png").toFile());
            if (img.getWidth() % 16 != 0 || img.getHeight() % 16 != 0) {
                System.out.println("  " + t + " " + img.getWidth() + "x" + img.getHeight());
            }
        }
        // translation keys: ru/en match and the presence of keys Java refers to
        System.out.println("=== translations ===");
        Map<String, String> ru = jsonStrings(read(ASSETS.resolve("lang/ru_ru.json"))), en = jsonStrings(read(ASSETS.resolve("lang/en_us.json")));
        for (String k : en.keySet()) {
            if (!ru.containsKey(k)) {
                System.out.println("  missing in ru: " + k);
            }
        }
        for (String k : ru.keySet()) {
            if (!en.containsKey(k)) {
                System.out.println("  missing in en: " + k);
            }
        }
        Pattern key = Pattern.compile("\"((?:gui|message|block|item|tooltip|key|subtitles|jei|config|jade)\\.golem_logix\\.[a-z0-9_.]+)\"");
        Set<String> referenced = new TreeSet<>();
        for (Path p : sources) {
            Matcher m = key.matcher(read(p));
            while (m.find()) {
                referenced.add(m.group(1));
            }
        }
        for (String k : referenced) {
            if (!en.containsKey(k)) {
                System.out.println("  Java refers to a missing key: " + k);
            }
        }
        // keys whose ending is built dynamically ("...status." + name) count by prefix
        for (String k : en.keySet()) {
            boolean used = referenced.contains(k) || referenced.stream().anyMatch(r -> r.endsWith(".") && k.startsWith(r))
                    || k.startsWith("block.golem_logix.") || k.startsWith("item.golem_logix.") || k.startsWith("tooltip.golem_logix.")
                    || k.startsWith("itemGroup.") || k.startsWith("key.");
            if (!used && !k.startsWith("gui.golem_logix.calc.status.")) {
                System.out.println("  key not referenced in Java (check): " + k);
            }
        }
        System.out.println("=== problems ===");
        problems.forEach(p -> System.out.println("  " + p));
        System.out.println("textures: " + textures.size() + ", models: " + models.size());
    }
}
