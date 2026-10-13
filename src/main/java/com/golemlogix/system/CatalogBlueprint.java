package com.golemlogix.system;

import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Punch Card Catalog blueprint for the book and the build "ghosts". Offsets are from the owner (bottom middle of the front); the cabinet
 * faces facing. A copper cell accepts copper of any oxidation, a statue cell any copper golem statue;
 * assembled parts ({@link ModBlocks#CATALOG_PART}) count as correct too.
 */
public final class CatalogBlueprint {
    private CatalogBlueprint() {
    }

    private static List<Block> statues() {
        List<Block> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (CatalogStructure.isStatue(b.defaultBlockState())) {
                out.add(b);
            }
        }
        return out;
    }

    public static List<AtlasBlueprint.Cell> cells(Direction facing) {
        List<AtlasBlueprint.Cell> out = new ArrayList<>();
        out.add(new AtlasBlueprint.Cell(0, 0, 0, ModBlocks.CARD_CATALOG.get().defaultBlockState().setValue(CardCatalogBlock.FACING, facing),
                List.of(ModBlocks.CARD_CATALOG.get())));
        Direction right = facing.getClockWise(), back = facing.getOpposite();
        List<Block> statues = statues();
        for (int i = 0; i < CatalogStructure.CELLS.length; i++) {
            int[] c = CatalogStructure.CELLS[i];
            int dx = right.getStepX() * c[0] + back.getStepX() * c[2];
            int dz = right.getStepZ() * c[0] + back.getStepZ() * c[2];
            List<Block> accepted = new ArrayList<>();
            if (CatalogStructure.isStatueCell(i)) {
                accepted.addAll(statues);
            } else if (!CatalogStructure.isShelf(i)) {
                accepted.addAll(CatalogStructure.COPPER);
            }
            accepted.add(CatalogStructure.fallback(i));
            accepted.add(ModBlocks.CATALOG_PART.get());
            BlockState ghost = CatalogStructure.fallback(i).defaultBlockState();
            if (ghost.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                ghost = ghost.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            }
            out.add(new AtlasBlueprint.Cell(dx, c[1], dz, ghost, accepted));
        }
        return out;
    }

    public static Map<Item, Integer> requirements() {
        Map<Item, Integer> need = new LinkedHashMap<>();
        need.put(ModBlocks.CARD_CATALOG.get().asItem(), 1);
        for (int i = 0; i < CatalogStructure.CELLS.length; i++) {
            need.merge(CatalogStructure.fallback(i).asItem(), 1, Integer::sum);
        }
        return need;
    }
}
