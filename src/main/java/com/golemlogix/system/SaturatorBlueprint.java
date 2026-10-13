package com.golemlogix.system;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чертёж Насыщателя Маны для книги и «призраков» стройки. Смещения считаются от «центрального ящика» — клетки (1, 0, 0)
 * (плотная труба внизу фасада); машина смотрит фасадом в сторону facing. Собранные части ({@link ModBlocks#SATURATOR_PART})
 * считаются правильными блоками.
 */
public final class SaturatorBlueprint {
    private SaturatorBlueprint() {
    }

    public static List<AtlasBlueprint.Cell> cells(Direction facing) {
        List<AtlasBlueprint.Cell> out = new ArrayList<>();
        Direction right = SaturatorStructure.right(facing);
        Direction back = facing.getOpposite();
        for (int i = 0; i < SaturatorStructure.CELLS; i++) {
            int col = SaturatorStructure.col(i), row = SaturatorStructure.row(i), dep = SaturatorStructure.dep(i);
            int dx = right.getStepX() * (col - 1) + back.getStepX() * dep;
            int dz = right.getStepZ() * (col - 1) + back.getStepZ() * dep;
            Block need = SaturatorStructure.required(i);
            BlockState state = need.defaultBlockState();
            if (i == SaturatorStructure.CONTROLLER) {
                state = state.setValue(ManaSaturatorBlock.FACING, facing);
            }
            out.add(new AtlasBlueprint.Cell(dx, row, dz, state, List.of(need, ModBlocks.SATURATOR_PART.get())));
        }
        return out;
    }

    /** Нужные предметы по типам (для списка материалов в книге и подсказки проекции). */
    public static Map<Item, Integer> requirements() {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (int i = 0; i < SaturatorStructure.CELLS; i++) {
            need.merge(SaturatorStructure.required(i).asItem(), 1, Integer::sum);
        }
        Map<Item, Integer> sorted = new LinkedHashMap<>();
        for (Block b : new Block[]{ModBlocks.MANA_SATURATOR.get(), net.minecraft.world.level.block.Blocks.END_ROD,
                ModBlocks.DENSE_FROST_PIPE.get(), ModBlocks.ATLAS_CASING.get()}) {
            if (need.containsKey(b.asItem())) {
                sorted.put(b.asItem(), need.get(b.asItem()));
            }
        }
        return sorted;
    }
}
