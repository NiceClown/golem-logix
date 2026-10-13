package com.golemlogix.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class ModBlockItem extends BlockItem {
    private final String tooltipKey;

    public ModBlockItem(Block block, String tooltipKey, Properties properties) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (tooltipKey != null) {
            tooltipComponents.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.DARK_AQUA));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
