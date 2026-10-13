/**
 * Магнитная кассета — один рисунок для шкафа (текстура 56x28) и для иконок дисков предметов (32x32):
 * медный корпус с латунными винтами, тёмное окно с двумя катушками и цветной ярлык ёмкости.
 * Размеры задаются в «юнитах» кассеты 7 x 3.5; scale растягивает рисунок, ox/oy — сдвиг по холсту.
 */
final class Cassette {
    static final int[] COPPER = Paint.COPPER, BRASS = Paint.BRASS, IRON = Paint.IRON, CYAN = Paint.CYAN;

    /** Цвета ярлыков по ёмкости: 1k медь, 4k железо, 16k золото, 64k алмаз, 256k аметист. */
    static final int[] TIER_LABEL = {0xD9803A, 0x9AA4B4, 0xF0C850, 0x55E6E0, 0xA070FF};

    static int at(int[] r, double i) {
        return Paint.at(r, i);
    }

    /**
     * @param label    цвет ярлыка; 0 — белая основа (рендерер красит её в цвет диска)
     * @param pips     сколько отметок ёмкости рисовать под окном (0 — не рисовать)
     */
    static void draw(Paint p, double ox, double oy, double s, int label, int pips) {
        double w = 7 * s, h = 3.5 * s;
        // корпус: медь со скосом, зерно
        p.slab(ox, oy, ox + w, oy + h, COPPER, 3.2, 1);
        p.streaks(ox + 0.1 * s, oy + 0.1 * s, ox + w - 0.1 * s, oy + h - 0.1 * s, 0.05, 211, true);
        // ярлык сверху
        double ly0 = oy + 0.18 * s, ly1 = oy + 0.9 * s;
        p.rect(ox + 0.55 * s, ly0, ox + w - 0.55 * s, ly1, label == 0 ? Paint.rgb(0xE6E0CC) : Paint.rgb(label));
        p.rect(ox + 0.55 * s, ly0, ox + w - 0.55 * s, ly0 + 0.1 * s, Paint.rgb(0xFFFFFF) & 0xDDFFFFFF | 0x22000000);
        p.rect(ox + 0.55 * s, ly1 - 0.1 * s, ox + w - 0.55 * s, ly1, Paint.argb(90, 0x000000));
        // окно с катушками
        double wx0 = ox + 0.55 * s, wx1 = ox + w - 0.55 * s, wy0 = oy + 1.05 * s, wy1 = oy + 3.0 * s;
        p.rect(wx0, wy0, wx1, wy1, at(IRON, 0.25));
        p.rect(wx0, wy0, wx1, wy0 + 0.12 * s, at(IRON, 0.0));
        p.rect(wx0, wy1 - 0.12 * s, wx1, wy1, at(IRON, 3.4));
        for (double cx : new double[]{ox + 1.9 * s, ox + 5.1 * s}) {
            double cy = (wy0 + wy1) / 2;
            double r = 0.72 * s;
            p.disc(cx, cy, r, at(BRASS, 1.6));
            p.sphere(cx, cy, r * 0.88, BRASS, 0.3, 0.9);
            p.disc(cx, cy, r * 0.42, at(IRON, 0.3));
            p.disc(cx, cy, Math.max(0.12, r * 0.12), at(BRASS, 4.6));
        }
        // лента между катушками
        p.rect(ox + 1.9 * s, (wy0 + wy1) / 2 + 0.55 * s, ox + 5.1 * s, (wy0 + wy1) / 2 + 0.7 * s, at(COPPER, 1.2));
        // винты в углах
        for (double[] c : new double[][]{{0.3, 0.3}, {6.7, 0.3}, {0.3, 3.2}, {6.7, 3.2}}) {
            p.screw(ox + c[0] * s, oy + c[1] * s, Math.max(0.18, 0.2 * s), BRASS, c[0] + c[1]);
        }
        // отметки ёмкости под окном
        for (int i = 0; i < pips; i++) {
            double px = ox + (2.2 + i * 0.55) * s;
            p.rect(px, oy + 3.14 * s, px + 0.3 * s, oy + 3.3 * s, at(BRASS, 5.0));
        }
    }

    /**
     * Текстура кассеты шкафа 7 x 4: узкий ярлык сверху и большое окно почти во всю высоту — бобины (их рисует и вращает
     * DriveBayRenderer, радиус 1.3) занимают окно целиком. Центры бобин: x 1.85 и 5.15, y 2.35 от верха.
     */
    static Paint cabinetTexture() {
        Paint p = new Paint(7, 4, 8);
        p.slab(0, 0, 7, 4, COPPER, 3.2, 1);
        p.streaks(0.1, 0.1, 6.9, 3.9, 0.05, 211, true);
        p.rect(0.5, 0.15, 6.5, 0.75, Paint.rgb(0xE6E0CC));
        p.rect(0.5, 0.65, 6.5, 0.75, Paint.argb(90, 0x000000));
        // окно с бобинами: тёмный провал, латунные ободки гнёзд, лента между гнёздами
        p.rect(0.35, 0.9, 6.65, 3.8, at(IRON, 0.2));
        p.rect(0.35, 0.9, 6.65, 1.0, at(IRON, 0.0));
        p.rect(0.35, 3.7, 6.65, 3.8, at(IRON, 3.4));
        for (double cx : new double[]{1.85, 5.15}) {
            p.ring(cx, 2.35, 1.32, 1.42, at(BRASS, 2.0));
        }
        for (double[] c : new double[][]{{0.25, 0.25}, {6.75, 0.25}, {0.25, 3.75}, {6.75, 3.75}}) {
            p.screw(c[0], c[1], 0.18, BRASS, c[0] + c[1]);
        }
        return p;
    }

    /** Иконка диска 32x32 (16x16 юнитов при плотности 2). */
    static Paint icon(int tier) {
        Paint p = new Paint(16, 16, 2);
        draw(p, 1, 4.2, 2, TIER_LABEL[tier], tier + 1);
        // латунные угловые накладки (как на старых иконках) — на 32 пикселях читаются лучше винтов
        double x0 = 1, y0 = 4.2, x1 = 15, y1 = 11.2;
        for (double[] c : new double[][]{{x0, y0, 1, 1}, {x1 - 1.5, y0, 1.5, 1}, {x0, y1 - 1.5, 1, 1.5}, {x1 - 1.5, y1 - 1.5, 1.5, 1.5}}) {
            p.slab(c[0], c[1], c[0] + c[2] + 0.5, c[1] + c[3] + 0.5, BRASS, 3.4, 1);
        }
        // тонкая подложка-тень под кассетой
        for (int x = 3; x < 29; x++) {
            p.over(x, 24, Paint.argb(70, 0x000000));
        }
        return p;
    }
}
