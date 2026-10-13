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
        return values()[(ordinal() + 1) % values().length];
    }

    public static ProviderOutput of(@Nullable Direction d) {
        if (d == null) {
            return ALL;
        }
        for (ProviderOutput o : values()) {
            if (o.direction == d) {
                return o;
            }
        }
        return ALL;
    }

    public static ProviderOutput byId(int id) {
        return values()[Math.floorMod(id, values().length)];
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
