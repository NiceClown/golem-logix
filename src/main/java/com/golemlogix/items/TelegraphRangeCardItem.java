package com.golemlogix.items;

import com.golemlogix.config.GolemConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A range card for the Cryo-Source Telegraph, the same tiers as the Portable Chronometer: without a card the link reaches
 * localWirelessRange blocks in one dimension, the Aether Resonance Core the whole dimension, the Void-Glass Prism any dimension.
 */
public class TelegraphRangeCardItem extends Item {
    private final int tier;

    public TelegraphRangeCardItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    /** The range tier a telegraph gets from its range slot: 0 without a card. */
    public static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof TelegraphRangeCardItem card ? card.tier : 0;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier >= 2;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.golem_logix.telegraph_range." + tier, GolemConfig.LOCAL_WIRELESS_RANGE.get())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.golem_logix.telegraph_range.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
