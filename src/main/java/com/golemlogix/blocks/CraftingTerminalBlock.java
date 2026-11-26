package com.golemlogix.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class CraftingTerminalBlock extends AbstractTerminalBlock {
    public static final MapCodec<CraftingTerminalBlock> CODEC = simpleCodec(CraftingTerminalBlock::new);

    public CraftingTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean hasCraftingGrid() {
        return true;
    }

    @Override
    protected String menuTitleKey() {
        return "gui.golem_logix.title.crafting_terminal";
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
