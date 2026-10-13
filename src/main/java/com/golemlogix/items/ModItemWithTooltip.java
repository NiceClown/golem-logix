package com.golemlogix.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ModItemWithTooltip extends Item {
    private final String tooltipKey;

    public ModItemWithTooltip(String tooltipKey, Properties properties) {
        super(properties);
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
