package com.golemlogix.blocks;

import com.golemlogix.system.AtlasPart;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

/**
 * Casing of the Atlas Array: the walls and crown of the multiblock. Alone it is a plain plate; in an assembled array the core
 * gives it its part (see {@code AtlasStructure.dress}) and the casing's own model grows the array's dress on its outer side: the
 * ice column between the Atlants, the balcony rail on the crown and the icicles over the Atlants.
 */
public class AtlasCasingBlock extends Block implements AtlasPart {
    /** What this casing is in an assembled array. */
    public enum Part implements StringRepresentable {
        /** Not in an assembled array (or behind an expander's column gap). */
        NONE,
        /** The side wall, first Atlant level: the lower half of the ice column outside it. */
        COLUMN_LOW,
        /** The side wall, second Atlant level: the upper half of the ice column, with its cap. */
        COLUMN_HIGH,
        /** The middle of a crown side: the rail along its outer edge. */
        TOP_EDGE,
        /** A crown corner: the rail along both outer edges (FACING and the side counter-clockwise of it), icicles under them. */
        TOP_CORNER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** The outer side the dress faces (for a corner: the first of its two outer sides). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AtlasCasingBlock(Properties properties) {
        super(properties.sound(SoundType.NETHERITE_BLOCK).strength(5.0f, 8.0f).requiresCorrectToolForDrops());
        registerDefaultState(stateDefinition.any().setValue(PART, Part.NONE).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }
}
