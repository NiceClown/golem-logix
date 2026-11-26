package com.golemlogix.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class AnalogTerminalBlock extends AbstractTerminalBlock {
    public static final MapCodec<AnalogTerminalBlock> CODEC = simpleCodec(AnalogTerminalBlock::new);

    public AnalogTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hasCraftingGrid() {
        return false;
    }

    @Override
    protected String menuTitleKey() {
        return "gui.golem_logix.title.analog_terminal";
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
