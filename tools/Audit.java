import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Ревизия ресурсов мода: ссылки моделей/блокстейтов/предметов/Java на текстуры и модели, отсутствующие файлы и «осиротевшие»
 * текстуры и модели. Запуск из корня проекта: java tools/Audit.java
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

    /** Плоский словарь "ключ": "значение" из json-файла перевода. */
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
            // родитель — модель, значения в "textures" — текстуры
            Matcher pm = Pattern.compile("\"parent\"\\s*:\\s*\"golem_logix:([a-z0-9_/]+)\"").matcher(s);
            while (pm.find()) {
                if (models.contains(pm.group(1))) {
                    usedModels.add(pm.group(1));
                } else {
                    problems.add(p.getFileName() + ": нет родительской модели " + pm.group(1));
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
                        problems.add(p.getFileName() + ": нет текстуры " + tm.group(1));
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
                    problems.add("blockstate " + p.getFileName() + ": нет модели " + r);
                }
            }
        }
        // textures/... в Java и ресурсах (GUI, рендереры)
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
                    problems.add(p.getFileName() + ": нет текстуры " + m2.group(1));
                }
            }
            // имена вида "bay_" + name или prefix[i] — считаем использованными по общему префиксу
            Matcher m3 = Pattern.compile("\"((?:textures/)?(?:block|item|entity|gui/book)/)?([a-z0-9_]+_)\"\\s*\\+").matcher(s);
            while (m3.find()) {
                for (String t : textures) {
                    if (t.contains("/" + m3.group(2)) || t.startsWith(m3.group(2))) {
                        usedTex.add("textures/" + t);
                    }
                }
            }
        }
        // книга и ресурсы прочие
        for (Path p : files(ASSETS.resolve("book"), ".json")) {
            Matcher m = Pattern.compile("\"(?:textures/)?((?:block|item|entity|gui)/[a-z0-9_/]+)(?:\\.png)?\"").matcher(read(p));
            while (m.find()) {
                if (textures.contains(m.group(1))) {
                    usedTex.add("textures/" + m.group(1));
                }
            }
        }
        // предметы: textures/item/<имя предмета>.png подхватывает модель item/<имя>.json через generated
        System.out.println("=== текстуры без ссылок ===");
        for (String t : textures) {
            if (!usedTex.contains("textures/" + t)) {
                System.out.println("  textures/" + t);
            }
        }
        System.out.println("=== модели без ссылок ===");
        for (String m : models) {
            boolean item = m.startsWith("item/");
            if (!usedModels.contains(m) && !item) {
                System.out.println("  models/" + m);
            }
        }
        System.out.println("=== размеры не кратны 16 (ограничивают mip-уровни) ===");
        for (String t : textures) {
            if (t.startsWith("gui/")) {
                continue;
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(ASSETS.resolve("textures/" + t + ".png").toFile());
            if (img.getWidth() % 16 != 0 || img.getHeight() % 16 != 0) {
                System.out.println("  " + t + " " + img.getWidth() + "x" + img.getHeight());
            }
        }
        // ключи перевода: совпадение ru/en и наличие ключей, на которые ссылается Java
        System.out.println("=== переводы ===");
        Map<String, String> ru = jsonStrings(read(ASSETS.resolve("lang/ru_ru.json"))), en = jsonStrings(read(ASSETS.resolve("lang/en_us.json")));
        for (String k : en.keySet()) {
            if (!ru.containsKey(k)) {
                System.out.println("  нет в ru: " + k);
            }
        }
        for (String k : ru.keySet()) {
            if (!en.containsKey(k)) {
                System.out.println("  нет в en: " + k);
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
                System.out.println("  Java ссылается на отсутствующий ключ: " + k);
            }
        }
        // ключи, у которых строится окончание динамически ("...status." + name), считаем по префиксу
        for (String k : en.keySet()) {
            boolean used = referenced.contains(k) || referenced.stream().anyMatch(r -> r.endsWith(".") && k.startsWith(r))
                    || k.startsWith("block.golem_logix.") || k.startsWith("item.golem_logix.") || k.startsWith("tooltip.golem_logix.")
                    || k.startsWith("itemGroup.") || k.startsWith("key.");
            if (!used && !k.startsWith("gui.golem_logix.calc.status.")) {
                System.out.println("  ключ без ссылок в Java (проверить): " + k);
            }
        }
        System.out.println("=== проблемы ===");
        problems.forEach(p -> System.out.println("  " + p));
        System.out.println("текстур: " + textures.size() + ", моделей: " + models.size());
    }
}
