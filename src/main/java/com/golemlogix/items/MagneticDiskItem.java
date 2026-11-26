package com.golemlogix.items;

import com.golemlogix.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MagneticDiskItem extends Item {
    private final int capacity;
    private final boolean isFluid;
    private final String formattedCapacity;

    public MagneticDiskItem(int capacity, boolean isFluid, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.isFluid = isFluid;
        this.formattedCapacity = isFluid ? formatWithCommas(capacity) + " mB" : formatWithCommas(capacity);
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isFluid() {
        return isFluid;
    }

    public static int getStoredCount(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.STORED.get(), 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStoredCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * getStoredCount(stack) / (float) capacity), 1, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = Math.max(0.0F, 1.0F - getStoredCount(stack) / (float) capacity);
        // teal -> amber -> red as it fills
        return Mth.hsvToRgb(0.5F * f, 0.8F, 0.95F);
    }

    private static String formatWithCommas(int value) {
        String s = Integer.toString(value);
        int minIndex = value < 0 ? 1 : 0;
        if (s.length() - minIndex <= 3) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        for (int i = sb.length() - 3; i > minIndex; i -= 3) {
            sb.insert(i, ',');
        }
        return sb.toString();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, TooltipFlag tooltipFlag) {
        String formattedStored = formatWithCommas(getStoredCount(stack));

        if (isFluid) {
            lines.accept(Component.translatable("tooltip.golem_logix.disk.fluid_capacity", formattedStored + " mB", formattedCapacity)
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.accept(Component.translatable("tooltip.golem_logix.disk.item_capacity", formattedStored, formattedCapacity)
                    .withStyle(ChatFormatting.GOLD));
        }

        lines.accept(Component.translatable("tooltip.golem_logix.disk.no_type_limit")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        super.appendHoverText(stack, context, display, lines, tooltipFlag);
    }
}
