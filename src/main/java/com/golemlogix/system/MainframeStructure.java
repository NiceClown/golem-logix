package com.golemlogix.system;

import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.blocks.MainframePartBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cryo-Mainframe: a 3 x 2 x 2 cabinet and two atlants on the sides that hold a shared cornice above it.
 * <pre>
 *  front view (the front is the side the owner faces):    back (second row):
 *     A  G  G  G  A        G = glass                          C  C  C        C = Atlas Casing
 *     A  G  M  G  A        M = owner (Cryo-Mainframe)         C  C  C
 *                          A = Atlant (two blocks tall)
 * </pre>
 * Cell coordinates: a = sideways (+ is clockwise from the front), h = up, d = depth (from the front backwards). On assembly all cells,
 * atlants included, become invisible parts: the owner's renderer draws the whole multiblock (the atlants are built into it
 * and hold the cornice). Disassembly returns the former blocks.
 */
public final class MainframeStructure {
    /** Part cells: {a, h, d}. 0..4 front glass, 5..10 back row casings, 11..14 atlants (lower, upper; left, right). */
    public static final int[][] CELLS = {
            {-1, 0, 0}, {1, 0, 0}, {-1, 1, 0}, {0, 1, 0}, {1, 1, 0},
            {-1, 0, 1}, {0, 0, 1}, {1, 0, 1}, {-1, 1, 1}, {0, 1, 1}, {1, 1, 1},
            {-2, 0, 0}, {-2, 1, 0}, {2, 0, 0}, {2, 1, 0}};
    public static final int GLASS_CELLS = 5;
    public static final int FIRST_ATLANT = 11;
    /** Atlants: on the sides of the front row (the lower half at the owner's level). */
    public static final int[] ATLANTS = {-2, 2};

    private static boolean restoring = false;

    private MainframeStructure() {
    }

    public static boolean isRestoring() {
        return restoring;
    }

    public static boolean isAtlant(int cell) {
        return cell >= FIRST_ATLANT;
    }

    /** The other half of the same atlant. */
    public static int partner(int cell) {
        return cell % 2 == 1 ? cell + 1 : cell - 1;
    }

    public static BlockPos cellPos(BlockPos controller, Direction facing, int a, int h, int d) {
        return controller.relative(facing.getClockWise(), a).above(h).relative(facing.getOpposite(), d);
    }

    public static BlockPos cellPos(BlockPos controller, Direction facing, int cell) {
        int[] c = CELLS[cell];
        return cellPos(controller, facing, c[0], c[1], c[2]);
    }

    public static BlockPos controllerOf(BlockPos part, Direction facing, int cell) {
        int[] c = CELLS[cell];
        return part.relative(facing.getClockWise(), -c[0]).below(c[1]).relative(facing.getOpposite(), -c[2]);
    }

    /** Which block is needed in a part cell before assembly (and is returned on disassembly). */
    public static Block original(int cell) {
        return isAtlant(cell) ? ModBlocks.ATLAS_ATLANT.get() : cell < GLASS_CELLS ? Blocks.GLASS : ModBlocks.ATLAS_CASING.get();
    }

    /** The state of the former block: an atlant with the right half and facing the cabinet front. */
    public static BlockState originalState(int cell, Direction facing) {
        Block b = original(cell);
        if (!isAtlant(cell)) {
            return b.defaultBlockState();
        }
        return b.defaultBlockState().setValue(AtlantBlock.FACING, facing)
                .setValue(AtlantBlock.HALF, CELLS[cell][1] == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
    }

    /** What is missing: the position and the needed block. */
    public record Missing(BlockPos pos, Block block) {
    }

    private static boolean matches(BlockState s, int cell, Direction facing) {
        if (s.getBlock() instanceof MainframePartBlock) {
            return s.getValue(MainframePartBlock.CELL) == cell && s.getValue(MainframePartBlock.FACING) == facing;
        }
        if (isAtlant(cell)) {
            return s.getBlock() instanceof AtlantBlock
                    && s.getValue(AtlantBlock.HALF) == (CELLS[cell][1] == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        }
        return s.is(original(cell));
    }

    public static List<Missing> check(LevelReader level, BlockPos controller, Direction facing) {
        List<Missing> out = new ArrayList<>();
        for (int i = 0; i < CELLS.length; i++) {
            BlockPos p = cellPos(controller, facing, i);
            if (isAtlant(i)) {
                // an atlant is one item for two cells: check by the lower half (11, 13) together with the upper one
                if (CELLS[i][1] == 0 && !(matches(level.getBlockState(p), i, facing)
                        && matches(level.getBlockState(p.above()), partner(i), facing))) {
                    out.add(new Missing(p, original(i)));
                }
            } else if (!matches(level.getBlockState(p), i, facing)) {
                out.add(new Missing(p, original(i)));
            }
        }
        return out;
    }

    public static boolean valid(LevelReader level, BlockPos controller, Direction facing) {
        return check(level, controller, facing).isEmpty();
    }

    /**
     * Assemble: all cells become parts, the owner FORMED. Atlants are replaced without updating the neighbours' shapes (otherwise the second half
     * of the statue, left without its pair, would disappear by itself), and the neighbours are notified at the end.
     */
    public static void form(Level level, BlockPos controller, Direction facing) {
        restoring = true;
        try {
            for (int i = 0; i < CELLS.length; i++) {
                level.setBlock(cellPos(controller, facing, i), ModBlocks.MAINFRAME_PART.get().defaultBlockState()
                        .setValue(MainframePartBlock.FACING, facing).setValue(MainframePartBlock.CELL, i),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            notifyAll(level, controller, facing);
        } finally {
            restoring = false;
        }
        BlockState cs = level.getBlockState(controller);
        if (cs.getBlock() instanceof CryoMainframeBlock) {
            level.setBlock(controller, cs.setValue(CryoMainframeBlock.FORMED, true), 3);
        }
        GolemNetwork.clearCache();
    }

    /**
     * Disassemble: parts become glass, casings and atlants again (except the broken one, which drops by itself; for a broken atlant
     * the other half disappears too and one item drops), the owner is unassembled.
     */
    public static void disassemble(Level level, BlockPos controller, Direction facing, @Nullable BlockPos broken) {
        int brokenCell = -1;
        for (int i = 0; i < CELLS.length; i++) {
            if (cellPos(controller, facing, i).equals(broken)) {
                brokenCell = i;
            }
        }
        restoring = true;
        try {
            for (int i = 0; i < CELLS.length; i++) {
                BlockPos p = cellPos(controller, facing, i);
                if (i == brokenCell) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof MainframePartBlock) || s.getValue(MainframePartBlock.CELL) != i) {
                    continue;
                }
                boolean gone = brokenCell >= 0 && isAtlant(brokenCell) && i == partner(brokenCell);
                level.setBlock(p, gone ? Blocks.AIR.defaultBlockState() : originalState(i, facing),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            notifyAll(level, controller, facing);
        } finally {
            restoring = false;
        }
        BlockState cs = level.getBlockState(controller);
        if (cs.getBlock() instanceof CryoMainframeBlock && cs.getValue(CryoMainframeBlock.FORMED)) {
            level.setBlock(controller, cs.setValue(CryoMainframeBlock.FORMED, false), 3);
        }
        GolemNetwork.clearCache();
    }

    /** Notify the neighbours of all cells (pipes recompute their joints) once the whole cabinet has been replaced. */
    private static void notifyAll(Level level, BlockPos controller, Direction facing) {
        for (int i = 0; i < CELLS.length; i++) {
            BlockPos p = cellPos(controller, facing, i);
            BlockState s = level.getBlockState(p);
            s.updateNeighbourShapes(level, p, Block.UPDATE_ALL);
            level.updateNeighborsAt(p, s.getBlock());
        }
    }
}
