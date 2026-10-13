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
 * Mana Saturator blueprint for the book and the build "ghosts". Offsets are counted from the "central box", cell (1, 0, 0)
 * (the dense pipe at the bottom of the front); the machine faces facing. Assembled parts ({@link ModBlocks#SATURATOR_PART})
 * count as correct blocks.
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

    /** Needed items by type (for the material list in the book and the projection hint). */
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
