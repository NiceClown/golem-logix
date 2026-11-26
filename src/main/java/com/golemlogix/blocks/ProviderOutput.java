package com.golemlogix.blocks;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.Nullable;

/**
 * Where the Pattern Provider feeds ingredients: to all sides (the default, as before) or to one. The arrows on the block
 * faces show the selected side, the "sun" shows feeding to all sides.
 */
public enum ProviderOutput implements StringRepresentable {
    ALL("all", null), DOWN("down", Direction.DOWN), UP("up", Direction.UP), NORTH("north", Direction.NORTH),
    SOUTH("south", Direction.SOUTH), WEST("west", Direction.WEST), EAST("east", Direction.EAST);

    private static final ProviderOutput[] VALUES = values();

    private final String name;
    @Nullable
    private final Direction direction;

    ProviderOutput(String name, @Nullable Direction direction) {
        this.name = name;
        this.direction = direction;
    }

    /** Feeding side; null means all sides. */
    @Nullable
    public Direction direction() {
        return direction;
    }

    public ProviderOutput next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static ProviderOutput of(@Nullable Direction d) {
        if (d == null) {
            return ALL;
        }
        return switch (d) {
            case DOWN -> DOWN;
            case UP -> UP;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
        };
    }

    public static ProviderOutput byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }

    /** Translation key for tooltips. */
    public String key() {
        return "gui.golem_logix.provider.output." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
