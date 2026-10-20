package com.golemlogix.items;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import java.util.List;

/**
 * A fluid "ghost": a marker that stands for a fluid and an amount in ghost slots (bus filters, interface stock, pattern
 * inputs), like the wrapped fluid stacks of AE2. It is never a real item in an inventory: ghost slots only keep copies.
 */
public class FluidGhostItem extends Item {
    public FluidGhostItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof FluidGhostItem;
    }

    public static ItemStack of(FluidStack fluid) {
        ItemStack stack = new ItemStack(ModItems.FLUID_GHOST.get());
        stack.set(ModDataComponents.GHOST_FLUID.get(), SimpleFluidContent.copyOf(fluid));
        return stack;
    }

    /** The fluid with its amount (empty if the stack is not a ghost). */
    public static FluidStack fluid(ItemStack stack) {
        if (!is(stack)) {
            return FluidStack.EMPTY;
        }
        SimpleFluidContent content = stack.get(ModDataComponents.GHOST_FLUID.get());
        return content == null ? FluidStack.EMPTY : content.copy();
    }

    public static int amount(ItemStack stack) {
        return fluid(stack).getAmount();
    }

    public static ItemStack withAmount(ItemStack stack, int amount) {
        FluidStack f = fluid(stack);
        return f.isEmpty() || amount <= 0 ? ItemStack.EMPTY : of(f.copyWithAmount(amount));
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack f = fluid(stack);
        return f.isEmpty() ? super.getName(stack) : f.getHoverName();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        List<Component> tooltip = new com.golemlogix.items.TooltipLines(lines);
        FluidStack f = fluid(stack);
        if (!f.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.golem_logix.fluid_ghost.amount", f.getAmount()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.golem_logix.fluid_ghost").withStyle(ChatFormatting.GRAY));
    }
}
