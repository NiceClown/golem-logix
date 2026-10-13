package com.golemlogix.blocks;

import com.golemlogix.system.SaturatorStructure;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Saturator cell shapes. The machine is described by boxes in "machine" coordinates (X left to right as seen from the front,
 * Y up, Z inwards from the front; a cell is 16x16x16) that match the model (tools/Saturator.java). For every cell the boxes
 * are clipped to its bounds, moved into the block's local coordinates (facing north) and rotated by FACING.
 */
public final class SaturatorShapes {
    /** Machine boxes: {x0, y0, z0, x1, y1, z1}. */
    private static final double[][] BOXES = {
            {0, 0, 0, 48, 3, 32},          // plinth
            {0, 3, 1, 16, 28, 32},         // left wing
            {32, 3, 1, 48, 28, 32},        // right wing
            {16, 3, 0, 32, 16, 32},        // central box with the inlet
            {16, 16, 16, 32, 39, 32},      // back wall of the central column
            {19, 16, 3, 29, 18, 13},       // pedestal base
            {21, 18, 5, 27, 27, 11},       // pedestal rod
            {17, 27, 2, 31, 29.5, 14},     // anvil
            {17, 29.5, 2, 19, 39, 4},      // press chamber posts
            {29, 29.5, 2, 31, 39, 4},
            {17, 29.5, 12, 19, 39, 14},
            {29, 29.5, 12, 31, 39, 14},
            {2.4, 28, 2.4, 13.6, 39, 13.6},  // glass columns with bands
            {34.4, 28, 2.4, 45.6, 39, 13.6},
            {0, 28, 28, 16, 39, 32},       // back wall above the wings
            {32, 28, 28, 48, 39, 32},
            {0, 39, 1, 48, 44, 32},        // top beam
            {19.5, 44, 3.5, 28.5, 45, 12.5},  // crystal base
            {21, 45, 5, 27, 48, 11}, // crystal
    };

    private static final Map<Direction, VoxelShape[]> CACHE = new EnumMap<>(Direction.class);

    private SaturatorShapes() {
    }

    /** Shape of cell index for a machine facing facing. */
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
            // local x is mirrored: it grows to the left as seen from the front
            double lx0 = 16.0 * (col + 1) - ax1, lx1 = 16.0 * (col + 1) - ax0;
            local = Shapes.or(local, net.minecraft.world.level.block.Block.box(lx0, ay0 - cy0, az0 - cz0, lx1, ay1 - cy0, az1 - cz0));
        }
        if (local.isEmpty()) {
            return Shapes.block();
        }
        return BusLinks.rotateShape(local.optimize(), facing);
    }
}
