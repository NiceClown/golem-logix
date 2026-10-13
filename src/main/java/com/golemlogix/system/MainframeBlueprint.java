package com.golemlogix.system;

import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чертёж Крио-Мейнфрейма для книги и «призраков» стройки. Смещения — от хозяина (низ фасада посередине); шкаф смотрит
 * фасадом в сторону facing. Собранные части ({@link ModBlocks#MAINFRAME_PART}) считаются правильными блоками.
 */
public final class MainframeBlueprint {
    private MainframeBlueprint() {
    }

    public static List<AtlasBlueprint.Cell> cells(Direction facing) {
        List<AtlasBlueprint.Cell> out = new ArrayList<>();
        out.add(new AtlasBlueprint.Cell(0, 0, 0, ModBlocks.CRYO_MAINFRAME.get().defaultBlockState().setValue(CryoMainframeBlock.FACING, facing),
                List.of(ModBlocks.CRYO_MAINFRAME.get())));
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        for (int i = 0; i < MainframeStructure.CELLS.length; i++) {
            int[] c = MainframeStructure.CELLS[i];
            int dx = right.getStepX() * c[0] + back.getStepX() * c[2];
            int dz = right.getStepZ() * c[0] + back.getStepZ() * c[2];
            out.add(new AtlasBlueprint.Cell(dx, c[1], dz, MainframeStructure.originalState(i, facing),
                    List.of(MainframeStructure.original(i), ModBlocks.MAINFRAME_PART.get())));
        }
        return out;
    }

    /** Нужные предметы по типам (атлант — один на две клетки). */
    public static Map<Item, Integer> requirements() {
        Map<Item, Integer> need = new LinkedHashMap<>();
        need.put(ModBlocks.CRYO_MAINFRAME.get().asItem(), 1);
        for (int i = 0; i < MainframeStructure.CELLS.length; i++) {
            if (MainframeStructure.isAtlant(i) && MainframeStructure.CELLS[i][1] == 1) {
                continue;
            }
            Block b = MainframeStructure.original(i);
            need.merge(b.asItem(), 1, Integer::sum);
        }
        return need;
    }
}
