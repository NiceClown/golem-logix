package com.golemlogix.items;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Forge Hammer: forges blanks on the anvil (an unassembled Mana Saturator) with a left-click on the anvil. Every strike spends
 * Source from the hammer's own charge; the charge is refilled as for the Chronometer, by right-clicking a Source jar with the hammer. Without Ars Nouveau
 * (or with requireSource=false) the hammer works without a charge.
 */
public class ForgeHammerItem extends Item {
    public static final int MAX_CHARGE = 4000;
    public static final int STRIKE_COST = 100;

    public ForgeHammerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getCharge(ItemStack stack) {
        return SourceChargeHelper.get(stack);
    }

    public static void setCharge(ItemStack stack, int charge) {
        SourceChargeHelper.set(stack, charge, MAX_CHARGE);
    }

    /** Whether the charge is enough for a strike (always, without Source). */
    public static boolean canStrike(ItemStack stack) {
        return !SourceHelper.sourceRequired() || getCharge(stack) >= STRIKE_COST;
    }

    /** Spend the charge for a strike. */
    public static void spendStrike(ItemStack stack) {
        if (SourceHelper.sourceRequired()) {
            setCharge(stack, getCharge(stack) - STRIKE_COST);
        }
    }

    /** The anvil is an unassembled Saturator: the hammer strikes it instead of breaking it. */
    public static boolean isAnvil(BlockState state) {
        return state.getBlock() instanceof ManaSaturatorBlock && !state.getValue(ManaSaturatorBlock.FORMED);
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return !isAnvil(state);
    }

    /** Right-click on a Source jar (or another Ars Nouveau device with a store) charges the hammer. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return SourceChargeHelper.tryChargeFromBlock(context, MAX_CHARGE,
                "message.golem_logix.hammer.charged", "message.golem_logix.hammer.nothing_to_charge", 1.2f)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return SourceChargeHelper.isBarVisible(stack, MAX_CHARGE);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return SourceChargeHelper.barWidth(stack, MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return SourceChargeHelper.BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.golem_logix.forge_hammer").withStyle(ChatFormatting.GRAY));
        if (SourceHelper.sourceRequired()) {
            lines.accept(Component.translatable("tooltip.golem_logix.forge_hammer.charge", getCharge(stack), MAX_CHARGE, STRIKE_COST)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        super.appendHoverText(stack, context, display, lines, flag);
    }
}
