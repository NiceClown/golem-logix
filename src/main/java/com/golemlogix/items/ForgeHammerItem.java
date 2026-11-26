package com.golemlogix.items;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

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
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !isAnvil(state);
    }

    /** Right-click on a Source jar (or another Ars Nouveau device with a store) charges the hammer. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return SourceChargeHelper.tryChargeFromBlock(context, MAX_CHARGE,
                "message.golem_logix.hammer.charged", "message.golem_logix.hammer.nothing_to_charge", 1.2f)
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide()) : InteractionResult.PASS;
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.golem_logix.forge_hammer").withStyle(ChatFormatting.GRAY));
        if (SourceHelper.sourceRequired()) {
            tooltip.add(Component.translatable("tooltip.golem_logix.forge_hammer.charge", getCharge(stack), MAX_CHARGE, STRIKE_COST)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
