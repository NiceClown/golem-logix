package com.golemlogix.items;

import com.golemlogix.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class MagneticDiskItem extends Item {
    private final int capacity;
    private final boolean isFluid;

    public MagneticDiskItem(int capacity, boolean isFluid, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.isFluid = isFluid;
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, TooltipFlag tooltipFlag) {
        List<Component> tooltipComponents = new com.golemlogix.items.TooltipLines(lines);
        String formattedCap = NumberFormat.getNumberInstance(Locale.US).format(capacity);
        String formattedStored = NumberFormat.getNumberInstance(Locale.US).format(getStoredCount(stack));

        if (isFluid) {
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.disk.fluid_capacity", formattedStored + " mB", formattedCap + " mB")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.disk.item_capacity", formattedStored, formattedCap)
                    .withStyle(ChatFormatting.GOLD));
        }

        tooltipComponents.add(Component.translatable("tooltip.golem_logix.disk.no_type_limit")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

        super.appendHoverText(stack, context, display, lines, tooltipFlag);
    }
}
