package com.golemlogix.system;

import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.AtlasCasingBlock;
import com.golemlogix.blocks.AtlasConduitBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
                Direction face = dz < 0 ? Direction.NORTH : Direction.SOUTH;
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(pos);
                    if (s.is(ModBlocks.ATLAS_ATLANT.get()) && s.getValue(AtlantBlock.FACING) != face) {
                        level.setBlock(pos, s.setValue(AtlantBlock.FACING, face), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    /**
     * Gives every casing, expander, and conduit of the array around the core its assembled look (formed) or takes it back (not formed).
     * Cheap and idempotent: the core runs it with its check once a second, so an array of an older world dresses itself on load and an
     * array losing a block undresses within a second.
     */
    public static void dress(Level level, BlockPos corePos, boolean formed) {
        BlockPos origin = corePos.below(3);
        // Pass 1: flip Channel Expanders into/out of pillar mode first so Pass 2 sees accurate vertical pillar adjacency
        for (int dy = 1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (cell(dx, dz) != Cell.EDGE) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ChannelExpanderBlock)) {
                        continue;
                    }
                    Direction outer = dx != 0 ? (dx < 0 ? Direction.WEST : Direction.EAST) : (dz < 0 ? Direction.NORTH : Direction.SOUTH);
                    BlockState pre = state.setValue(ChannelExpanderBlock.FORMED, formed)
                            .setValue(ChannelExpanderBlock.FACING, formed ? outer : Direction.NORTH);
                    if (!formed) {
                        pre = pre.setValue(ChannelExpanderBlock.UP, false)
                                .setValue(ChannelExpanderBlock.DOWN, false)
                                .setValue(ChannelExpanderBlock.PIPE, false);
                    }
                    if (pre != state) {
                        level.setBlock(pos, pre, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }

        // Pass 2: finalize casings, expanders (vertical links + outer pipe collar), and conduits (vertical links)
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    BlockState want = state;
                    if (state.getBlock() instanceof AtlasCasingBlock && dy >= 1 && !(dx == 0 && dz == 0)) {
                        want = dressedCasingState(level, origin, dx, dy, dz, state, formed);
                    } else if (state.getBlock() instanceof ChannelExpanderBlock && formed && cell(dx, dz) == Cell.EDGE && (dy == 1 || dy == 2)) {
                        Direction outer = dx != 0 ? (dx < 0 ? Direction.WEST : Direction.EAST) : (dz < 0 ? Direction.NORTH : Direction.SOUTH);
                        BlockPos outerPos = pos.relative(outer);
                        BlockState outerState = level.getBlockState(outerPos);
                        boolean pipe = !(outerState.getBlock() instanceof AtlasPart)
                                && GolemNode.linked(level, pos, state, outerPos, outerState, outer);
                        want = state.setValue(ChannelExpanderBlock.FORMED, true)
                                .setValue(ChannelExpanderBlock.FACING, outer)
                                .setValue(ChannelExpanderBlock.UP, AtlasConduitBlock.isPillar(level.getBlockState(pos.above())))
                                .setValue(ChannelExpanderBlock.DOWN, AtlasConduitBlock.isPillar(level.getBlockState(pos.below())))
                                .setValue(ChannelExpanderBlock.PIPE, pipe);
                    } else if (state.getBlock() instanceof AtlasConduitBlock) {
                        want = state.setValue(AtlasConduitBlock.UP, AtlasConduitBlock.isPillar(level.getBlockState(pos.above())))
                                .setValue(AtlasConduitBlock.DOWN, AtlasConduitBlock.isPillar(level.getBlockState(pos.below())));
                    }
                    if (want != state) {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static BlockState dressedCasingState(Level level, BlockPos origin, int dx, int dy, int dz,
                                                 BlockState state, boolean formed) {
        BlockState want = state.setValue(AtlasCasingBlock.PART, AtlasCasingBlock.Part.NONE)
                .setValue(AtlasCasingBlock.FACING, Direction.NORTH);
        if (!formed) {
            return want;
        }
        Direction xOut = dx < 0 ? Direction.WEST : Direction.EAST;
        Direction zOut = dz < 0 ? Direction.NORTH : Direction.SOUTH;
        boolean corner = dx != 0 && dz != 0;
        if (dy == 3) {
            // A corner faces the outer side whose counter-clockwise neighbour is its other outer side
            Direction face = !corner ? (dx != 0 ? xOut : zOut) : zOut.getCounterClockWise() == xOut ? zOut : xOut;
            return want.setValue(AtlasCasingBlock.PART, corner ? AtlasCasingBlock.Part.TOP_CORNER : AtlasCasingBlock.Part.TOP_EDGE)
                    .setValue(AtlasCasingBlock.FACING, face);
        }
        if (!corner && level.getBlockState(origin.offset(dx, 1, dz)).getBlock() instanceof AtlasCasingBlock
                && level.getBlockState(origin.offset(dx, 2, dz)).getBlock() instanceof AtlasCasingBlock) {
            return want.setValue(AtlasCasingBlock.PART, dy == 1 ? AtlasCasingBlock.Part.COLUMN_LOW : AtlasCasingBlock.Part.COLUMN_HIGH)
                    .setValue(AtlasCasingBlock.FACING, dx != 0 ? xOut : zOut);
        }
        return want;
    }

    /** The starter tower: the core on two Atlas Conduits and a Cable Bushing, one above the other (the first step to the array). */
    public static boolean isTower(Level level, BlockPos corePos) {
        return level.isLoaded(corePos)
                && level.getBlockState(corePos.below()).is(ModBlocks.ATLAS_CONDUIT.get())
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
