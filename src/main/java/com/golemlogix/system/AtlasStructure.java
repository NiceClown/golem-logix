package com.golemlogix.system;

import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Atlas Array, a 3x3x4 multiblock. The core stands in the middle of the top layer.
 *
 * <pre>
 * layer 3 (top):  casing casing casing / casing  CORE  casing / casing casing casing
 * layer 2:        atlant (upper) in the corners, conduit in the middle, casing or expander on the sides
 * layer 1:        atlant (lower) in the corners, conduit in the middle, casing or expander on the sides
 * layer 0 (base): bushings in the corners and in the middle, conduits on the sides
 * </pre>
 */
public final class AtlasStructure {
    public static final int BUSHINGS = 5;

    private AtlasStructure() {
    }

    /**
     * @param missingPos     the first position with a wrong block (null if the array is complete)
     * @param expected       which block was expected there (for the player hint)
     * @param expanders      ordinary channel expanders in the walls
     * @param quantumExpanders quantum expanders
     */
    public record Result(boolean valid, @Nullable BlockPos missingPos, @Nullable Block expected,
                         int expanders, int quantumExpanders) {
    }

    private enum Cell {CORNER, EDGE, CENTER}

    private static Cell cell(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return Cell.CENTER;
        }
        return dx != 0 && dz != 0 ? Cell.CORNER : Cell.EDGE;
    }

    /** Blocks allowed in a cell; the first one is the "main" one (for the hint). */
    private static List<Supplier<? extends Block>> allowed(int dy, Cell cell) {
        return switch (dy) {
            case 0 -> cell == Cell.EDGE ? List.of(ModBlocks.ATLAS_CONDUIT) : List.of(ModBlocks.CABLE_BUSHING);
            case 1, 2 -> switch (cell) {
                case CORNER -> List.of(ModBlocks.ATLAS_ATLANT);
                case CENTER -> List.of(ModBlocks.ATLAS_CONDUIT);
                case EDGE -> List.of(ModBlocks.ATLAS_CASING, ModBlocks.CHANNEL_EXPANDER, ModBlocks.QUANTUM_CHANNEL_EXPANDER);
            };
            default -> cell == Cell.CENTER ? List.of(ModBlocks.ATLAS_CORE) : List.of(ModBlocks.ATLAS_CASING);
        };
    }

    /** Turns the Atlants to face outwards: the north corners face north, the south ones south. */
    public static void orientAtlants(Level level, BlockPos corePos) {
        BlockPos origin = corePos.below(3);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                net.minecraft.core.Direction face = dz < 0 ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(pos);
                    if (s.is(ModBlocks.ATLAS_ATLANT.get()) && s.getValue(AtlantBlock.FACING) != face) {
                        level.setBlock(pos, s.setValue(AtlantBlock.FACING, face), 18);
                    }
                }
            }
        }
    }

    /** The starter tower: the core on two Atlas Conduits and a Cable Bushing, one above the other (the first step to the array). */
    public static boolean isTower(Level level, BlockPos corePos) {
        return level.getBlockState(corePos.below()).is(ModBlocks.ATLAS_CONDUIT.get())
                && level.getBlockState(corePos.below(2)).is(ModBlocks.ATLAS_CONDUIT.get())
                && level.getBlockState(corePos.below(3)).is(ModBlocks.CABLE_BUSHING.get());
    }

    public static Result check(Level level, BlockPos corePos) {
        BlockPos origin = corePos.below(3);
        int expanders = 0;
        int quantum = 0;
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    Cell cell = cell(dx, dz);
                    List<Supplier<? extends Block>> options = allowed(dy, cell);
                    if (!level.isLoaded(pos)) {
                        return new Result(false, pos, options.get(0).get(), 0, 0);
                    }
                    BlockState state = level.getBlockState(pos);
                    boolean ok = false;
                    for (Supplier<? extends Block> option : options) {
                        if (state.is(option.get())) {
                            ok = true;
                            break;
                        }
                    }
                    if (ok && dy >= 1 && dy <= 2 && cell == Cell.CORNER) {
                        // atlant: the lower half on level 1, the upper one on level 2
                        DoubleBlockHalf half = state.getValue(AtlantBlock.HALF);
                        ok = half == (dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                    }
                    if (!ok) {
                        return new Result(false, pos, options.get(0).get(), 0, 0);
                    }
                    if (cell == Cell.EDGE && (dy == 1 || dy == 2)) {
                        if (state.is(ModBlocks.CHANNEL_EXPANDER.get())) {
                            expanders++;
                        } else if (state.is(ModBlocks.QUANTUM_CHANNEL_EXPANDER.get())) {
                            quantum++;
                        }
                    }
                }
            }
        }
        return new Result(true, null, null, expanders, quantum);
    }
}