import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Логотип мода (logo.png для списка модов): латунное кольцо с рунами, ледяной кристалл ядра и надпись. Запуск через ArtForge. */
final class Logo {
    static void make() throws Exception {
        int S = 256;
        Paint p = new Paint(S, S, 1);
        // фон: тёмное железо с мягким радиальным свечением
        p.fill(0, 0, S, S, (u, v) -> {
            double d = Math.hypot(u - 128, v - 112) / 180.0;
            return Paint.mix(Paint.rgb(0x1b2a36), Paint.rgb(0x07090c), Math.min(1, d));
        });
        p.grain(0, 0, S, S, 0.05, 901);
        // рамка из латуни и угловые заклёпки
        p.frame(3, 3, S - 3, S - 3, 5, Paint.at(Paint.BRASS, 3.2));
        p.frame(8, 8, S - 8, S - 8, 2, Paint.at(Paint.BRASS, 1.6));
        for (double[] c : new double[][]{{14, 14}, {S - 14, 14}, {14, S - 14}, {S - 14, S - 14}}) {
            p.screw(c[0], c[1], 6, Paint.BRASS, c[0] * 0.05);
        }
        // кольцо с рунами вокруг ядра
        double cx = 128, cy = 106;
        p.disc(cx, cy, 78, Paint.at(Paint.BRASS, 1.2));
        p.sphere(cx, cy, 76, Paint.BRASS, 0.25, 0.95);
        p.disc(cx, cy, 64, Paint.at(Paint.IRON, 0.3));
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            p.rune(cx + Math.cos(a) * 52 - 5, cy + Math.sin(a) * 52 - 8, 16, i, Paint.at(Paint.CYAN, 5.2), 0);
        }
        p.disc(cx, cy, 40, Paint.at(Paint.BRASS, 1.4));
        p.sphere(cx, cy, 38, Paint.BRASS, 0.2, 0.9);
        p.disc(cx, cy, 31, Paint.at(Paint.IRON, 0.2));
        // кристалл ядра: четыре грани
        double[][][] tri = {{{0, -26}, {22, 0}, {0, 0}}, {{0, -26}, {0, 0}, {-22, 0}}, {{22, 0}, {0, 26}, {0, 0}}, {{-22, 0}, {0, 0}, {0, 26}}};
        double[] tone = {0.95, 0.72, 0.5, 0.32};
        for (int i = 0; i < 4; i++) {
            final double t = tone[i];
            p.polyFn(new double[]{cx + tri[i][0][0], cx + tri[i][1][0], cx + tri[i][2][0]},
                    new double[]{cy + tri[i][0][1], cy + tri[i][1][1], cy + tri[i][2][1]}, (u, v) -> Paint.ramp(Paint.CYAN, t));
        }
        p.line(cx, cy - 26, cx, cy + 26, 1.2, Paint.argb(160, 0xFFFFFF));
        p.glowRect(cx - 4, cy - 4, cx + 4, cy + 4, 30, Paint.at(Paint.CYAN, 4.5), 120);
        // иней по верхней рамке
        p.frostLine(3, S - 3, 3, 4, 902);
        p.icicle(20, 8, 12, 5);
        p.icicle(S - 24, 8, 16, 5);
        p.icicle(130, 8, 9, 4);

        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                img.setRGB(x, y, p.px[y * S + x]);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font f = new Font(Font.SERIF, Font.BOLD, 30);
        g.setFont(f);
        String title = "GOLEM LOGIX";
        int w = g.getFontMetrics().stringWidth(title);
        g.setColor(new Color(0x0c0905));
        g.drawString(title, (S - w) / 2 + 2, 226);
        g.setColor(new Color(Paint.at(Paint.BRASS, 4.6)));
        g.drawString(title, (S - w) / 2, 224);
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 13));
        String sub = "cold · occult · analog logistics";
        int w2 = g.getFontMetrics().stringWidth(sub);
        g.setColor(new Color(Paint.at(Paint.CYAN, 4.4)));
        g.drawString(sub, (S - w2) / 2, 243);
        g.dispose();
        File out = new File("src/main/resources/logo.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }
}
