package com.golemlogix.system;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.blocks.CatalogPartBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Punch Card Catalog: a copper 3 x 2 x 2 cabinet with drawer shelves on the front, held by two copper golems on the sides.
 * <pre>
 *  front view (the front is where the owner faces):        back:
 *     S  B  B  B  S     B = bookshelf                          C  C  C      C = copper block (any oxidation stage,
 *     C  B  K  B  C     K = owner (Punch Card Catalog)         C  C  C          waxed too)
 *                       S = copper golem statue (any) on copper block C
 * </pre>
 * Cells as in the Cryo-Mainframe ({@link MainframeStructure#CELLS}): 0..4 front shelves, 5..10 back row, 11..14 sides
 * (11, 13 pedestals, 12, 14 statues). On assembly all cells become invisible parts, and the owner remembers the former
 * block states and returns them on disassembly.
 */
public final class CatalogStructure {
    public static final int[][] CELLS = MainframeStructure.CELLS;
    private static boolean restoring = false;

    private CatalogStructure() {
    }

    public static boolean isRestoring() {
        return restoring;
    }

    public static BlockPos cellPos(BlockPos controller, Direction facing, int cell) {
        return MainframeStructure.cellPos(controller, facing, cell);
    }

    public static BlockPos controllerOf(BlockPos part, Direction facing, int cell) {
        return MainframeStructure.controllerOf(part, facing, cell);
    }

    /** Copper blocks of all oxidation stages, waxed too. */
    public static final Set<Block> COPPER = Set.of(Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
            Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER);

    /** A copper golem statue of any oxidation stage: the tag golem_logix:copper_golem_statues (modpacks add their own), or any "..copper_golem_statue" block. */
    public static boolean isStatue(BlockState s) {
        return s.is(com.golemlogix.init.ModTags.COPPER_GOLEM_STATUES) || BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("copper_golem_statue");
    }

    /** Oxidation stage of a statue (0 new, 3 oxidized): the golems of the assembled catalog are coloured this way. */
    public static int oxidation(BlockState s) {
        String p = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        return p.contains("oxidized") ? 3 : p.contains("weathered") ? 2 : p.contains("exposed") ? 1 : 0;
    }

    public static boolean isShelf(int cell) {
        return cell < MainframeStructure.GLASS_CELLS;
    }

    public static boolean isStatueCell(int cell) {
        return cell >= MainframeStructure.FIRST_ATLANT && CELLS[cell][1] == 1;
    }

    /** Whether the block fits the cell. */
    public static boolean accepts(int cell, BlockState s) {
        if (isShelf(cell)) {
            return s.is(Blocks.BOOKSHELF);
        }
        if (isStatueCell(cell)) {
            return isStatue(s);
        }
        return COPPER.contains(s.getBlock());
    }

    /** What to show the player if a cell is missing (and what to return if the former state was not remembered). */
    public static Block fallback(int cell) {
        if (isShelf(cell)) {
            return Blocks.BOOKSHELF;
        }
        if (isStatueCell(cell)) {
            Block statue = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("copper_golem_statue"));
            return statue == Blocks.AIR ? Blocks.COPPER_BLOCK : statue;
        }
        return Blocks.COPPER_BLOCK;
    }

    public record Missing(BlockPos pos, Block block) {
    }

    private static boolean matches(BlockState s, int cell, Direction facing) {
        if (s.getBlock() instanceof CatalogPartBlock) {
            return s.getValue(CatalogPartBlock.CELL) == cell && s.getValue(CatalogPartBlock.FACING) == facing;
        }
        return accepts(cell, s);
    }

    public static List<Missing> check(LevelReader level, BlockPos controller, Direction facing) {
        List<Missing> out = new ArrayList<>();
        for (int i = 0; i < CELLS.length; i++) {
            BlockPos p = cellPos(controller, facing, i);
            if (!matches(level.getBlockState(p), i, facing)) {
                out.add(new Missing(p, fallback(i)));
            }
        }
        return out;
    }

    public static boolean valid(LevelReader level, BlockPos controller, Direction facing) {
        return check(level, controller, facing).isEmpty();
    }

    /** Assemble: remember the former blocks in the owner, all cells become parts, the owner becomes FORMED. */
    public static void form(Level level, BlockPos controller, Direction facing) {
        if (!(level.getBlockEntity(controller) instanceof CardCatalogBlockEntity be)) {
            return;
        }
        restoring = true;
        try {
            for (int i = 0; i < CELLS.length; i++) {
                BlockPos p = cellPos(controller, facing, i);
                be.rememberOriginal(i, level.getBlockState(p));
            }
            for (int i = 0; i < CELLS.length; i++) {
                level.setBlock(cellPos(controller, facing, i), ModBlocks.CATALOG_PART.get().defaultBlockState()
                        .setValue(CatalogPartBlock.FACING, facing).setValue(CatalogPartBlock.CELL, i),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            notifyAll(level, controller, facing);
        } finally {
            restoring = false;
        }
        BlockState cs = level.getBlockState(controller);
        if (cs.getBlock() instanceof CardCatalogBlock) {
            level.setBlock(controller, cs.setValue(CardCatalogBlock.FORMED, true), 3);
        }
        be.syncNow();
        GolemNetwork.nodesChanged(level, controller);
    }

    /** Disassemble: parts become the former blocks again (except the broken one, which drops by itself), the owner is unassembled. */
    public static void disassemble(Level level, BlockPos controller, Direction facing, @Nullable BlockPos broken) {
        CardCatalogBlockEntity be = level.getBlockEntity(controller) instanceof CardCatalogBlockEntity c ? c : null;
        restoring = true;
        try {
            for (int i = 0; i < CELLS.length; i++) {
                BlockPos p = cellPos(controller, facing, i);
                if (p.equals(broken)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof CatalogPartBlock && s.getValue(CatalogPartBlock.CELL) == i) {
                    BlockState original = be != null ? be.original(i) : null;
                    level.setBlock(p, original != null ? original : fallback(i).defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
            notifyAll(level, controller, facing);
        } finally {
            restoring = false;
        }
        BlockState cs = level.getBlockState(controller);
        if (cs.getBlock() instanceof CardCatalogBlock && cs.getValue(CardCatalogBlock.FORMED)) {
            level.setBlock(controller, cs.setValue(CardCatalogBlock.FORMED, false), 3);
        }
        GolemNetwork.nodesChanged(level, controller);
    }

    private static void notifyAll(Level level, BlockPos controller, Direction facing) {
        for (int i = 0; i < CELLS.length; i++) {
            BlockPos p = cellPos(controller, facing, i);
            BlockState s = level.getBlockState(p);
            s.updateNeighbourShapes(level, p, Block.UPDATE_ALL);
            level.updateNeighborsAt(p, s.getBlock());
        }
    }
}
