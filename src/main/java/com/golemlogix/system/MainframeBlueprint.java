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
 * Cryo-Mainframe blueprint for the book and the build "ghosts". Offsets are from the owner (bottom middle of the front); the cabinet
 * faces facing. Assembled parts ({@link ModBlocks#MAINFRAME_PART}) count as correct blocks.
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

    /** Needed items by type (an atlant is one for two cells). */
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
