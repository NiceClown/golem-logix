package com.golemlogix.blocks;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

public final class GolemProperties {
    /** Сеть запитана: включает анимации труб и дискового шкафа. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private GolemProperties() {
    }
}