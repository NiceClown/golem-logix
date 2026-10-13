package com.golemlogix.system;

import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.init.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чертёж Решётки Атланта для книги и «призраков» стройки: список ячеек относительно центральной муфты основания
 * (0, 0, 0) и список нужных предметов. Вариант TOWER — стартовая башня на 8 каналов, FULL — вся решётка 3x3x4.
 */
public final class AtlasBlueprint {
    public enum Variant {TOWER, FULL, SATURATOR, MAINFRAME, CATALOG}

    /**
     * @param state    состояние, которое нужно поставить (для призрака)
     * @param accepted какие блоки считаются правильными в этой ячейке
     */
    public record Cell(int dx, int dy, int dz, BlockState state, List<Block> accepted) {
        public boolean matches(BlockState actual) {
            if (!accepted.contains(actual.getBlock())) {
                return false;
            }
            if (actual.getBlock() instanceof AtlantBlock) {
                return actual.getValue(AtlantBlock.HALF) == state.getValue(AtlantBlock.HALF);
            }
            return true;
        }
    }

    private AtlasBlueprint() {
    }

    private static Cell cell(int dx, int dy, int dz, Block block) {
        return new Cell(dx, dy, dz, block.defaultBlockState(), List.of(block));
    }

    public static List<Cell> cells(Variant variant) {
        if (variant == Variant.SATURATOR) {
            return SaturatorBlueprint.cells(Direction.SOUTH);
        }
        if (variant == Variant.MAINFRAME) {
            return MainframeBlueprint.cells(Direction.SOUTH);
        }
        if (variant == Variant.CATALOG) {
            return CatalogBlueprint.cells(Direction.SOUTH);
        }
        List<Cell> out = new ArrayList<>();
        Block bushing = ModBlocks.CABLE_BUSHING.get();
        Block conduit = ModBlocks.ATLAS_CONDUIT.get();
        Block casing = ModBlocks.ATLAS_CASING.get();
        if (variant == Variant.TOWER) {
            out.add(cell(0, 0, 0, bushing));
            out.add(cell(0, 1, 0, conduit));
            out.add(cell(0, 2, 0, conduit));
            out.add(cell(0, 3, 0, ModBlocks.ATLAS_CORE.get()));
            return out;
        }
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0;
                    boolean center = dx == 0 && dz == 0;
                    switch (dy) {
                        case 0 -> out.add(cell(dx, dy, dz, corner || center ? bushing : conduit));
                        case 1, 2 -> {
                            if (corner) {
                                BlockState atlant = ModBlocks.ATLAS_ATLANT.get().defaultBlockState()
                                        .setValue(AtlantBlock.FACING, dz < 0 ? Direction.NORTH : Direction.SOUTH)
                                        .setValue(AtlantBlock.HALF, dy == 1 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
                                out.add(new Cell(dx, dy, dz, atlant, List.of(ModBlocks.ATLAS_ATLANT.get())));
                            } else if (center) {
                                out.add(cell(dx, dy, dz, conduit));
                            } else {
                                out.add(new Cell(dx, dy, dz, casing.defaultBlockState(),
                                        List.of(casing, ModBlocks.CHANNEL_EXPANDER.get(), ModBlocks.QUANTUM_CHANNEL_EXPANDER.get())));
                            }
                        }
                        default -> out.add(cell(dx, dy, dz, center ? ModBlocks.ATLAS_CORE.get() : casing));
                    }
                }
            }
        }
        return out;
    }

    /** Нужные предметы (Атлант считается один раз на два блока). */
    public static Map<Item, Integer> requirements(Variant variant) {
        if (variant == Variant.SATURATOR) {
            return SaturatorBlueprint.requirements();
        }
        if (variant == Variant.MAINFRAME) {
            return MainframeBlueprint.requirements();
        }
        if (variant == Variant.CATALOG) {
            return CatalogBlueprint.requirements();
        }
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Cell c : cells(variant)) {
            Block b = c.state().getBlock();
            if (b instanceof AtlantBlock && c.state().getValue(AtlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                continue;
            }
            need.merge(b.asItem(), 1, Integer::sum);
        }
        // порядок для читателя: ядро, муфты, шины, атланты, корпус
        Map<Item, Integer> sorted = new LinkedHashMap<>();
        for (Block b : new Block[]{ModBlocks.ATLAS_CORE.get(), ModBlocks.CABLE_BUSHING.get(), ModBlocks.ATLAS_CONDUIT.get(),
                ModBlocks.ATLAS_ATLANT.get(), ModBlocks.ATLAS_CASING.get()}) {
            if (need.containsKey(b.asItem())) {
                sorted.put(b.asItem(), need.get(b.asItem()));
            }
        }
        return sorted;
    }
}