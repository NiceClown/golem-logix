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
 * Картотека перфокарт: медный шкаф 3 x 2 x 2 с ящиками-полками на фасаде, его держат два медных голема по бокам.
 * <pre>
 *  вид спереди (фасад — куда смотрит хозяин):            сзади:
 *     S  B  B  B  S     B — книжная полка                    C  C  C      C — медный блок (любой степени окисления,
 *     C  B  K  B  C     K — хозяин (Картотека перфокарт)     C  C  C          вощёный тоже)
 *                       S — статуя медного голема (любая) на медном блоке C
 * </pre>
 * Клетки — как у Крио-Мейнфрейма ({@link MainframeStructure#CELLS}): 0..4 полки фасада, 5..10 задний ряд, 11..14 бока
 * (11, 13 — постаменты, 12, 14 — статуи). При сборке все клетки становятся невидимыми частями, а прежние состояния блоков
 * хозяин запоминает и возвращает при разборке.
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

    /** Медные блоки всех степеней окисления, вощёные тоже. */
    public static final Set<Block> COPPER = Set.of(Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
            Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER);

    /** Статуя медного голема (из Copper Age Backport, любая степень окисления). */
    public static boolean isStatue(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("copper_golem_statue");
    }

    /** Степень окисления статуи (0 — новая, 3 — окисленная): так окрашены големы собранной картотеки. */
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

    /** Подходит ли блок для клетки. */
    public static boolean accepts(int cell, BlockState s) {
        if (isShelf(cell)) {
            return s.is(Blocks.BOOKSHELF);
        }
        if (isStatueCell(cell)) {
            return isStatue(s);
        }
        return COPPER.contains(s.getBlock());
    }

    /** Что показать игроку, если клетки не хватает (и что вернуть, если прежнее состояние не запомнилось). */
    public static Block fallback(int cell) {
        if (isShelf(cell)) {
            return Blocks.BOOKSHELF;
        }
        if (isStatueCell(cell)) {
            Block statue = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace("copper_golem_statue"));
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

    /** Собрать: запомнить прежние блоки в хозяине, все клетки — в части, хозяин — FORMED. */
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
        GolemNetwork.clearCache();
    }

    /** Разобрать: части снова прежние блоки (кроме сломанной — она выпадет сама), хозяин — не собран. */
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
        GolemNetwork.clearCache();
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
