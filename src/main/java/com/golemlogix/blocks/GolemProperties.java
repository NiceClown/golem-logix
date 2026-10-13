package com.golemlogix.blocks;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class GolemProperties {
    /** The network is powered: turns on the pipe and drive cabinet animations. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private GolemProperties() {
    }
}