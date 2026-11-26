package com.golemlogix.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class ModBlockItem extends BlockItem {
    private final String tooltipKey;

    public ModBlockItem(Block block, String tooltipKey, Properties properties) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, TooltipFlag tooltipFlag) {
        if (tooltipKey != null) {
            lines.accept(Component.translatable(tooltipKey).withStyle(ChatFormatting.DARK_AQUA));
        }
        super.appendHoverText(stack, context, display, lines, tooltipFlag);
    }
}
