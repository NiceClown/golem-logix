package com.golemlogix.blocks;

import com.golemlogix.system.SaturatorStructure;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Формы клеток Насыщателя. Машина описана коробками в «машинных» координатах (X слева направо глазами зрителя спереди,
 * Y вверх, Z вглубь от фасада; клетка — 16x16x16), совпадающими с моделью (tools/Saturator.java). Для каждой клетки коробки
 * обрезаются по её границам, переводятся в локальные координаты блока (лицом на север) и поворачиваются по FACING.
 */
public final class SaturatorShapes {
    /** Коробки машины: {x0, y0, z0, x1, y1, z1}. */
    private static final double[][] BOXES = {
            {0, 0, 0, 48, 3, 32},          // цоколь
            {0, 3, 1, 16, 28, 32},         // левое крыло
            {32, 3, 1, 48, 28, 32},        // правое крыло
            {16, 3, 0, 32, 16, 32},        // центральный ящик с патрубком
            {16, 16, 16, 32, 39, 32},      // задняя стенка центральной колонны
            {19, 16, 3, 29, 18, 13},       // основание пьедестала
            {21, 18, 5, 27, 27, 11},       // стержень пьедестала
            {17, 27, 2, 31, 29.5, 14},     // наковальня
            {17, 29.5, 2, 19, 39, 4},      // стойки камеры пресса
            {29, 29.5, 2, 31, 39, 4},
            {17, 29.5, 12, 19, 39, 14},
            {29, 29.5, 12, 31, 39, 14},
            {2.4, 28, 2.4, 13.6, 39, 13.6},  // стеклянные колонны с поясами
            {34.4, 28, 2.4, 45.6, 39, 13.6},
            {0, 28, 28, 16, 39, 32},       // задняя стенка над крыльями
            {32, 28, 28, 48, 39, 32},
            {0, 39, 1, 48, 44, 32},        // верхняя балка
            {19.5, 44, 3.5, 28.5, 45, 12.5},  // основание кристалла
            {21, 45, 5, 27, 48, 11}, // кристалл
    };

    private static final Map<Direction, VoxelShape[]> CACHE = new EnumMap<>(Direction.class);

    private SaturatorShapes() {
    }

    /** Форма клетки index для машины, смотрящей в сторону facing. */
    public static synchronized VoxelShape of(int index, Direction facing) {
        VoxelShape[] shapes = CACHE.computeIfAbsent(facing, f -> new VoxelShape[SaturatorStructure.CELLS]);
        if (shapes[index] == null) {
            shapes[index] = build(index, facing);
        }
        return shapes[index];
    }

    private static VoxelShape build(int index, Direction facing) {
        int col = SaturatorStructure.col(index), row = SaturatorStructure.row(index), dep = SaturatorStructure.dep(index);
        double cx0 = 16.0 * col, cy0 = 16.0 * row, cz0 = 16.0 * dep;
        VoxelShape local = Shapes.empty();
        for (double[] b : BOXES) {
            double ax0 = Math.max(b[0], cx0), ax1 = Math.min(b[3], cx0 + 16);
            double ay0 = Math.max(b[1], cy0), ay1 = Math.min(b[4], cy0 + 16);
            double az0 = Math.max(b[2], cz0), az1 = Math.min(b[5], cz0 + 16);
            if (ax1 - ax0 < 1e-6 || ay1 - ay0 < 1e-6 || az1 - az0 < 1e-6) {
                continue;
            }
            // локальный x зеркален: растёт влево глазами зрителя спереди
            double lx0 = 16.0 * (col + 1) - ax1, lx1 = 16.0 * (col + 1) - ax0;
            local = Shapes.or(local, net.minecraft.world.level.block.Block.box(lx0, ay0 - cy0, az0 - cz0, lx1, ay1 - cy0, az1 - cz0));
        }
        if (local.isEmpty()) {
            return Shapes.block();
        }
        return BusLinks.rotateShape(local.optimize(), facing);
    }
}
