import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * The block atlas takes its mipmap levels from its least divisible sprite: one 12x12 texture limits the mipmaps of every block of every
 * mod in the pack, and distant textures shimmer. So this runs last and doubles what falls short of 16 (nearest neighbour, so nothing
 * changes on screen) until every frame divides by 16.
 */
final class MipSafe {
    private static final int LEVELS = 16;
    private static final Pattern SIZE = Pattern.compile("\"(width|height)\"\\s*:\\s*(\\d+)");

    private MipSafe() {
    }

    static void all() throws Exception {
        File[] files = Forge2.RES.resolve("textures/block").toFile().listFiles((d, n) -> n.endsWith(".png"));
        int changed = 0;
        for (File f : files) {
            if (fix(f)) {
                changed++;
            }
        }
        System.out.println("MipSafe: scaled up " + changed + " textures");
    }

    private static boolean fix(File f) throws Exception {
        BufferedImage img = ImageIO.read(f);
        int w = img.getWidth(), h = img.getHeight(), frameW = w, frameH = h % w == 0 ? w : h;
        Path meta = f.toPath().resolveSibling(f.getName() + ".mcmeta");
        String json = Files.exists(meta) ? Files.readString(meta) : null;
        if (json != null) {
            Matcher m = SIZE.matcher(json);
            while (m.find()) {
                if (m.group(1).equals("width")) {
                    frameW = Integer.parseInt(m.group(2));
                } else {
                    frameH = Integer.parseInt(m.group(2));
                }
            }
        }
        int k = 1;
        while (Integer.lowestOneBit(frameW * k) < LEVELS || Integer.lowestOneBit(frameH * k) < LEVELS) {
            k *= 2;
        }
        if (k == 1) {
            return false;
        }
        BufferedImage out = new BufferedImage(w * k, h * k, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h * k; y++) {
            for (int x = 0; x < w * k; x++) {
                out.setRGB(x, y, img.getRGB(x / k, y / k));
            }
        }
        ImageIO.write(out, "png", f);
        if (json != null) {
            int factor = k;
            Files.writeString(meta, SIZE.matcher(json).replaceAll(r -> "\"" + r.group(1) + "\": " + Integer.parseInt(r.group(2)) * factor));
        }
        return true;
    }
}
