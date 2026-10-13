package com.golemlogix.system;

import com.golemlogix.blockentity.SaturatorPartBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Mana Saturator, a 3x3x2 multiblock (width x height x depth). The owner is the "Saturator" block in the upper middle of the front
 * wall; below it an End Rod (the press base), and below that a dense frost pipe, the "central box" through which
 * the machine connects to the network; the other fifteen cells are Atlas Casing. When all blocks stand correctly they are replaced
 * by part blocks {@link SaturatorPartBlock} with a single machine model, and on disassembly they come back as they were.
 *
 * <pre>
 * cell = (col, row, dep): col 0..2 left to right as seen from the front, row 0..2 bottom to top, dep 0 = front, 1 = back
 * </pre>
 */
public final class SaturatorStructure {
    public static final int COLS = 3, ROWS = 3, DEPTH = 2, CELLS = COLS * ROWS * DEPTH;

    public static int index(int col, int row, int dep) {
        return col + COLS * row + COLS * ROWS * dep;
    }

    public static int col(int index) {
        return index % COLS;
    }

    public static int row(int index) {
        return (index / COLS) % ROWS;
    }

    public static int dep(int index) {
        return index / (COLS * ROWS);
    }

    /** The central box: the network socket on the front (and at the bottom). */
    public static final int PORT = index(1, 0, 0);
    /** Sockets at the back and on the sides (the back half of the bottom row). */
    public static final int PORT_BACK = index(1, 0, 1);
    public static final int PORT_LEFT = index(0, 0, 1);
    public static final int PORT_RIGHT = index(2, 0, 1);
    /** All cells with network sockets: one on every side of the machine except the top. */
    public static final int[] PORTS = {PORT, PORT_BACK, PORT_LEFT, PORT_RIGHT};
    /** End Rod, the press base. */
    public static final int ROD = index(1, 1, 0);
    /** The owner (the block with the block entity). */
    public static final int CONTROLLER = index(1, 2, 0);
    /** Vent grilles on the roof: Cooling Co-Processors go here (the top row, except the owner). */
    public static final int[] VENTS = {index(0, 2, 0), index(2, 2, 0), index(0, 2, 1), index(1, 2, 1), index(2, 2, 1)};

    public static boolean isVent(int index) {
        for (int v : VENTS) {
            if (v == index) {
                return true;
            }
        }
        return false;
    }

    /** Whether the cell has a network socket on side dir (the side in the world, the machine faces facing). */
    public static boolean portFace(int index, Direction facing, Direction dir) {
        if (index == PORT) {
            return dir == facing || dir == Direction.DOWN;
        }
        if (index == PORT_BACK) {
            return dir == facing.getOpposite();
        }
        if (index == PORT_LEFT) {
            return dir == right(facing).getOpposite();
        }
        if (index == PORT_RIGHT) {
            return dir == right(facing);
        }
        return false;
    }

    private static final ThreadLocal<Boolean> RESTORING = ThreadLocal.withInitial(() -> false);

    private SaturatorStructure() {
    }

    /** Which way from the owner the machine's "right" lies (looking at it from the front). */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos cellPos(BlockPos controller, Direction facing, int index) {
        return controller.relative(right(facing), col(index) - 1).above(row(index) - 2).relative(facing.getOpposite(), dep(index));
    }

    /** The owner position from a cell position. */
    public static BlockPos controllerOf(BlockPos pos, Direction facing, int index) {
        return pos.relative(right(facing), 1 - col(index)).above(2 - row(index)).relative(facing, dep(index));
    }

    /** Which block must be placed in the cell before assembly. */
    public static Block required(int index) {
        if (index == PORT) {
            return ModBlocks.DENSE_FROST_PIPE.get();
        }
        if (index == ROD) {
            return Blocks.END_ROD;
        }
        if (index == CONTROLLER) {
            return ModBlocks.MANA_SATURATOR.get();
        }
        return ModBlocks.ATLAS_CASING.get();
    }

    /** All positions of the machine (for Source sources, render bounds, hints). */
    public static List<BlockPos> cells(BlockPos controller, Direction facing) {
        List<BlockPos> out = new ArrayList<>(CELLS);
        for (int i = 0; i < CELLS; i++) {
            out.add(cellPos(controller, facing, i));
        }
        return out;
    }

    /**
     * @param missingPos the first cell with a wrong block (null if the machine is assembled)
     * @param expected   which block was expected there (for the player hint)
     */
    public record Result(boolean valid, @Nullable BlockPos missingPos, @Nullable Block expected) {
    }

    /** Checks the cells; already assembled parts count as correct if they stand in their place. */
    public static Result check(Level level, BlockPos controller, Direction facing) {
        for (int i = 0; i < CELLS; i++) {
            BlockPos pos = cellPos(controller, facing, i);
            Block need = required(i);
            if (!level.isLoaded(pos)) {
                return new Result(false, pos, need);
            }
            BlockState state = level.getBlockState(pos);
            boolean ok = state.is(need);
            if (!ok && state.getBlock() instanceof SaturatorPartBlock) {
                ok = state.getValue(SaturatorPartBlock.CELL) == i && state.getValue(SaturatorPartBlock.FACING) == facing;
            }
            if (!ok) {
                return new Result(false, pos, need);
            }
        }
        return new Result(true, null, null);
    }

    public static boolean isRestoring() {
        return RESTORING.get();
    }

    /** Replaces the machine blocks with part blocks (the owner stays in place and becomes "assembled"). */
    public static void form(ServerLevel level, BlockPos controller, Direction facing) {
        Block part = ModBlocks.SATURATOR_PART.get();
        for (int i = 0; i < CELLS; i++) {
            if (i == CONTROLLER) {
                continue;
            }
            BlockPos pos = cellPos(controller, facing, i);
            BlockState old = level.getBlockState(pos);
            if (old.getBlock() instanceof SaturatorPartBlock) {
                continue;
            }
            level.setBlock(pos, part.defaultBlockState().setValue(SaturatorPartBlock.FACING, facing).setValue(SaturatorPartBlock.CELL, i), 3);
            if (level.getBlockEntity(pos) instanceof SaturatorPartBlockEntity be) {
                be.setup(old, controller);
            }
        }
        BlockState ctl = level.getBlockState(controller);
        if (ctl.getBlock() instanceof ManaSaturatorBlock) {
            level.setBlock(controller, ctl.setValue(ManaSaturatorBlock.FORMED, true), 3);
        }
        level.playSound(null, controller, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, controller.getX() + 0.5, controller.getY() - 0.5, controller.getZ() + 0.5,
                40, 0.9, 0.9, 0.9, 0.04);
    }

    /**
     * Returns all machine parts to their original blocks; skip = the cell that was just destroyed (it is left alone).
     * The owner, if it still stands, becomes "unassembled" again.
     */
    public static void disassemble(Level level, BlockPos controller, Direction facing, @Nullable BlockPos skip) {
        if (RESTORING.get()) {
            return;
        }
        RESTORING.set(true);
        try {
            for (int i = 0; i < CELLS; i++) {
                BlockPos pos = cellPos(controller, facing, i);
                if (i == CONTROLLER || pos.equals(skip) || !level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof SaturatorPartBlock && state.getValue(SaturatorPartBlock.CELL) == i) {
                    restore(level, pos);
                }
            }
            if (level.isLoaded(controller)) {
                BlockState ctl = level.getBlockState(controller);
                if (ctl.getBlock() instanceof ManaSaturatorBlock && ctl.getValue(ManaSaturatorBlock.FORMED)) {
                    level.setBlock(controller, ctl.setValue(ManaSaturatorBlock.FORMED, false), 3);
                }
            }
        } finally {
            RESTORING.set(false);
        }
    }

    /** How many Cooling Co-Processors stand on the roof grilles. */
    public static int coolers(Level level, BlockPos controller, Direction facing) {
        int n = 0;
        for (int v : VENTS) {
            BlockPos p = cellPos(controller, facing, v);
            if (level.isLoaded(p)) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof SaturatorPartBlock && s.getValue(SaturatorPartBlock.COOLER)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Returns one part to its original block (also used when an "orphaned" part heals itself). */
    public static void restore(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        BlockState original = be instanceof SaturatorPartBlockEntity part ? part.getOriginal() : Blocks.AIR.defaultBlockState();
        BlockState current = level.getBlockState(pos);
        if (current.getBlock() instanceof SaturatorPartBlock && current.getValue(SaturatorPartBlock.COOLER)) {
            // a coprocessor from a grille goes back to the player as an item
            Block.popResource(level, pos.above(), new net.minecraft.world.item.ItemStack(ModBlocks.COOLING_COPROCESSOR.get()));
        }
        boolean was = RESTORING.get();
        RESTORING.set(true);
        try {
            level.setBlock(pos, original, 3);
            BlockState updated = Block.updateFromNeighbourShapes(original, level, pos);
            if (updated != original) {
                level.setBlock(pos, updated, 2);
            }
        } finally {
            RESTORING.set(was);
        }
    }
}
