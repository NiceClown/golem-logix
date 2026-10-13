import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Golem minds and Liquid Cryo-Source:
 * raw minds (a battered golem unbound by a Dispel) are glowing lumps of folded metal, snow or ice; torn minds (a block's mind
 * torn by the Create mixer in Liquid Cryo-Source) are the same lumps split in two with cryo-source strands between the halves.
 * Also the fluid ghost icon and the animated still/flowing textures of Liquid Cryo-Source.
 */
final class Minds {
    static final int[] IRON = Paint.IRON, COPPER = Paint.COPPER, FROST = Paint.FROST, CYAN = Paint.CYAN;

    /** Material of a mind: surface ramp, patina ramp (or null) and the colour of its glowing veins. */
    record Material(int[] surface, int[] patina, int vein) {
    }

    static final Material IRON_MIND = new Material(IRON, null, 0x7FE8FF);
    static final Material COPPER_MIND = new Material(COPPER, Items.VERDIGRIS, 0x9CFFE4);
    static final Material SNOW_MIND = new Material(new int[]{0x5f7d96, 0x86a6bf, 0xa9c6db, 0xc9e0ee, 0xe1eff7, 0xf2f9fd, 0xffffff}, null, 0x66D9FF);

    /** Whether (u, v) is inside the two-lobed lump centred at (cx, cy). */
    static boolean inLump(double u, double v, double cx, double cy) {
        double a = Math.pow((u - (cx - 1.9)) / 4.0, 2) + Math.pow((v - (cy + 0.2)) / 4.3, 2);
        double b = Math.pow((u - (cx + 1.8)) / 4.2, 2) + Math.pow((v - (cy - 0.2)) / 4.5, 2);
        return a < 1 || b < 1;
    }

    /** Folded surface: the gyri and sulci of a brain, lit from the top left; a dark rim at the outline. */
    static int surface(Material m, double u, double v, double cx, double cy, long seed, boolean rim) {
        double nx = (u - cx) / 6.0, ny = (v - cy) / 4.6;
        double light = 0.66 - 0.30 * nx - 0.36 * ny - 0.2 * (nx * nx + ny * ny);
        double field = u * 0.9 + 1.6 * Math.sin(v * 1.25 + seed) + 0.8 * Math.sin(u * 0.7 + v * 0.5);
        double s = Math.sin(field * 2.3);
        if (s < -0.5) {
            light -= 0.34;
        } else if (s > 0.55) {
            light += 0.12;
        }
        if (rim) {
            light -= 0.38;
        }
        int c = Paint.ramp(m.surface(), light);
        if (m.patina() != null) {
            double n = 0.5 + 0.5 * Math.sin(u * 2.7 + v * 1.9 + seed) * Math.cos(v * 2.2 - u * 1.3);
            if (n > 0.6) {
                c = Paint.mix(c, Paint.ramp(m.patina(), light * 0.8 + 0.2), Math.min(1, (n - 0.6) * 3));
            }
        }
        return c;
    }

    /** Inside the lump but next to its outline. */
    static boolean onRim(double u, double v, double cx, double cy) {
        double d = 0.55;
        return !inLump(u - d, v, cx, cy) || !inLump(u + d, v, cx, cy) || !inLump(u, v - d, cx, cy) || !inLump(u, v + d, cx, cy);
    }

    /** A raw mind: two hemispheres with the mind's own light glowing in the fissure between them. */
    static Paint raw(Material m, long seed) {
        Paint p = Items.canvas();
        Items.shadow(p, 8, 13.8, 6.4, 1.2);
        double cx = 8, cy = 8;
        p.fill(0, 0, 16, 16, (u, v) -> {
            if (!inLump(u, v, cx, cy)) {
                return 0;
            }
            double fissure = cx + 0.35 * Math.sin(v * 1.7 + seed);
            if (Math.abs(u - fissure) < 0.32 && !onRim(u, v, cx, cy)) {
                return Paint.argb(255, m.vein());
            }
            return surface(m, u, v, cx, cy, seed, onRim(u, v, cx, cy));
        });
        p.glowRect(cx - 0.4, cy - 3.0, cx + 0.4, cy + 3.0, 0.9, Paint.argb(255, m.vein()), 70);
        p.sparkle(cx - 4.4, cy - 3.4, Paint.rgb(0xFFFFFF));
        p.sparkle(cx + 0.2, cy - 4.6, Paint.argb(230, m.vein()));
        return p;
    }

    /** A torn mind: the lump split along a jagged line, the halves apart, cryo-source strands and drops between them. */
    static Paint torn(Material m, long seed) {
        Paint p = Items.canvas();
        Items.shadow(p, 8, 13.8, 6.8, 1.2);
        double cx = 8, cy = 8, gap = 1.25;
        p.fill(0, 0, 16, 16, (u, v) -> {
            double cut = cx + 0.8 * Math.sin(v * 2.4 + seed) + 0.4 * Math.sin(v * 5.1);
            // the left half moved left and down, the right half right and up; the torn edge glows with cryo-source
            if (u < cut - gap && inLump(u + gap, v - 0.5, cx, cy)) {
                if (u > cut - gap - 0.45) {
                    return Paint.at(CYAN, 5.4);
                }
                return surface(m, u + gap, v - 0.5, cx, cy, seed, onRim(u + gap, v - 0.5, cx, cy));
            }
            if (u > cut + gap && inLump(u - gap, v + 0.5, cx, cy)) {
                if (u < cut + gap + 0.45) {
                    return Paint.at(CYAN, 5.4);
                }
                return surface(m, u - gap, v + 0.5, cx, cy, seed, onRim(u - gap, v + 0.5, cx, cy));
            }
            return 0;
        });
        // strands of liquid cryo-source across the gap and a drop falling from it
        for (double v : new double[]{6.0, 9.6}) {
            double cut = cx + 0.8 * Math.sin(v * 2.4 + seed) + 0.4 * Math.sin(v * 5.1);
            p.line(cut - gap, v + 0.25, cut + gap, v - 0.25, 0.28, Paint.argb(210, 0x6FF2F0));
        }
        p.disc(cx + 0.2, 13.4, 0.42, Paint.at(CYAN, 5.0));
        p.sparkle(cx - 5.2, cy - 3.2, Paint.rgb(0xFFFFFF));
        p.sparkle(cx + 0.4, cy - 5.2, Paint.argb(230, 0x9FFFF6));
        return p;
    }

    /** Fluid ghost: a pale drop with a highlight (in GUIs the real fluid is drawn over it). */
    static Paint ghost() {
        Paint p = Items.canvas();
        p.fill(0, 0, 16, 16, (u, v) -> {
            double dx = u - 8, dy = v - 9.6;
            boolean body = dx * dx + dy * dy < 16;
            boolean tip = v < 9.6 && v > 2.2 && Math.abs(dx) < (v - 2.2) * 0.55;
            if (!body && !tip) {
                return 0;
            }
            return Paint.ramp(new int[]{0x3a6f8a, 0x4f8fb0, 0x69acc9, 0x8ac6dd, 0xb2dcec, 0xd8eef6, 0xffffff}, 0.55 - 0.05 * dx - 0.04 * dy);
        });
        p.line(6.2, 8.4, 6.0, 11.0, 0.5, Paint.argb(220, 0xFFFFFF));
        return p.outlined(Paint.rgb(0x1a2a34));
    }

    // ================================================================================== Liquid Cryo-Source textures
    static final int FRAMES = 32;

    /** Colour of the liquid at pixel (x, y) of a tile with the given period, at cycle fraction t (seamless in x, y and t). */
    static int liquid(double x, double y, double period, double t, double flow) {
        double w = 2 * Math.PI / period, tt = 2 * Math.PI * t;
        double yy = y - flow * t * period;
        double swirl = Math.sin(x * w + Math.sin(yy * w * 2 + tt) * 1.3 + tt) * 0.5 + 0.5;
        double ripple = Math.sin((x + yy) * w * 2 - tt) * 0.5 + 0.5;
        double rune = Math.sin(x * w * 3 + tt) * Math.sin(yy * w * 3 - tt);
        double light = 0.42 + 0.22 * swirl + 0.12 * ripple + (rune > 0.86 ? 0.25 : 0);
        int c = Paint.ramp(CYAN, light);
        // occult violet threads in the cold teal
        double thread = Math.sin(x * w * 2 - yy * w + tt * 2);
        if (thread > 0.93) {
            c = Paint.mix(c, Paint.rgb(0xB79CFF), 0.45);
        }
        return 0xE6000000 | (c & 0xFFFFFF);
    }

    static void strip(String name, int size, double flow) throws Exception {
        BufferedImage img = new BufferedImage(size, size * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) {
            double t = f / (double) FRAMES;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    img.setRGB(x, f * size + y, liquid(x + 0.5, y + 0.5, size, t, flow));
                }
            }
        }
        File out = Forge2.RES.resolve("textures/block/" + name + ".png").toFile();
        ImageIO.write(img, "png", out);
        Forge2.write(Forge2.RES.resolve("textures/block/" + name + ".png.mcmeta"),
                "{\n  \"animation\": {\n    \"frametime\": 2,\n    \"interpolate\": true\n  }\n}\n");
    }

    static void model(String name) throws Exception {
        Forge2.write(Forge2.RES.resolve("models/item/" + name + ".json"),
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"golem_logix:item/" + name + "\"\n  }\n}\n");
    }

    static void all() throws Exception {
        Material[] mats = {IRON_MIND, COPPER_MIND, SNOW_MIND};
        String[] names = {"iron", "copper", "snow"};
        for (int i = 0; i < 3; i++) {
            raw(mats[i], 11 + i).save(Items.itemTex("raw_" + names[i] + "_mind"));
            model("raw_" + names[i] + "_mind");
            torn(mats[i], 21 + i).save(Items.itemTex("torn_" + names[i] + "_mind"));
            model("torn_" + names[i] + "_mind");
        }
        ghost().save(Items.itemTex("fluid_ghost"));
        model("fluid_ghost");
        strip("cryo_source_still", 16, 0);
        strip("cryo_source_flow", 32, 1);
        System.out.println("Minds: done");
    }
}
