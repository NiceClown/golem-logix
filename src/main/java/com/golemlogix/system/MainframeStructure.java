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
 * Крио-Мейнфрейм: шкаф 3 x 2 x 2 и два атланта по бокам, которые держат над ним общий карниз.
 * <pre>
 *  вид спереди (фасад — сторона, куда смотрит хозяин):     сзади (второй ряд):
 *     A  G  G  G  A        G — стекло                         C  C  C        C — Корпус Атланта
 *     A  G  M  G  A        M — хозяин (Крио-Мейнфрейм)       C  C  C
 *                          A — Атлант (двухблочный)
 * </pre>
 * Координаты клетки: a — вбок (+ — по часовой от фасада), h — вверх, d — вглубь (от фасада назад). При сборке все клетки,
 * включая атлантов, становятся невидимыми частями: многоблок целиком рисует отрисовщик хозяина (атланты встроены в него
 * и держат карниз). Разбор возвращает прежние блоки.
 */
public final class MainframeStructure {
    /** Клетки-части: {a, h, d}. 0..4 — стекло фасада, 5..10 — корпуса заднего ряда, 11..14 — атланты (низ, верх; слева, справа). */
    public static final int[][] CELLS = {
            {-1, 0, 0}, {1, 0, 0}, {-1, 1, 0}, {0, 1, 0}, {1, 1, 0},
            {-1, 0, 1}, {0, 0, 1}, {1, 0, 1}, {-1, 1, 1}, {0, 1, 1}, {1, 1, 1},
            {-2, 0, 0}, {-2, 1, 0}, {2, 0, 0}, {2, 1, 0}};
    public static final int GLASS_CELLS = 5;
    public static final int FIRST_ATLANT = 11;
    /** Атланты: по бокам фасадного ряда (нижняя половина на уровне хозяина). */
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

    /** Вторая половина того же атланта. */
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

    /** Какой блок нужен в клетке-части до сборки (и возвращается при разборе). */
    public static Block original(int cell) {
        return isAtlant(cell) ? ModBlocks.ATLAS_ATLANT.get() : cell < GLASS_CELLS ? Blocks.GLASS : ModBlocks.ATLAS_CASING.get();
    }

    /** Состояние прежнего блока: атлант — нужной половиной и лицом к фасаду шкафа. */
    public static BlockState originalState(int cell, Direction facing) {
        Block b = original(cell);
        if (!isAtlant(cell)) {
            return b.defaultBlockState();
        }
        return b.defaultBlockState().setValue(AtlantBlock.FACING, facing)
                .setValue(AtlantBlock.HALF, CELLS[cell][1] == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
    }

    /** Чего не хватает: позиция и нужный блок. */
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
                // атлант — один предмет на две клетки: проверяем по нижней половине (11, 13) вместе с верхней
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
     * Собрать: все клетки — в части, хозяин — FORMED. Атлантов заменяем без обновления формы соседей (иначе вторая половина
     * статуи, оставшись без пары, сама исчезнет), а соседей оповещаем в конце.
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
     * Разобрать: части снова стекло, корпуса и атланты (кроме сломанной — она выпадет сама; у сломанного атланта исчезает
     * и вторая половина, предмет выпадает один), хозяин — не собран.
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

    /** Оповестить соседей всех клеток (трубы пересчитают стыки), когда весь шкаф уже заменён. */
    private static void notifyAll(Level level, BlockPos controller, Direction facing) {
        for (int i = 0; i < CELLS.length; i++) {
            BlockPos p = cellPos(controller, facing, i);
            BlockState s = level.getBlockState(p);
            s.updateNeighbourShapes(level, p, Block.UPDATE_ALL);
            level.updateNeighborsAt(p, s.getBlock());
        }
    }
}
