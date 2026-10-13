package com.golemlogix.items;

import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Map;

/**
 * Copper punch card: blank ones stack up to 64, a written one is single (each has its own recipe, and a stack of
 * identical cards in a provider would run one job, a mess).
 */
public class CopperPunchCardItem extends Item {
    public CopperPunchCardItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    public static boolean hasPattern(ItemStack stack) {
        return stack.has(ModDataComponents.PATTERN.get());
    }

    /** A written card: the recipe and a stack size of one. */
    public static ItemStack written(Item card, PatternData data) {
        ItemStack s = new ItemStack(card, 1);
        s.set(ModDataComponents.PATTERN.get(), data);
        s.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        return s;
    }

    /** Shift + right-click with a written card in hand erases the recipe: the card is blank again and stacks. */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
                                                                       net.minecraft.world.entity.player.Player player,
                                                                       net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && hasPattern(stack)) {
            if (!level.isClientSide()) {
                player.setItemInHand(hand, new ItemStack(stack.getItem(), stack.getCount()));
                player.displayClientMessage(Component.translatable("message.golem_logix.card.cleared"), true);
                level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.8f);
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasPattern(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        PatternData data = stack.get(ModDataComponents.PATTERN.get());
        if (data != null) {
            tooltipComponents.add(Component.translatable(data.crafting()
                    ? "tooltip.golem_logix.punch_card.mode_crafting"
                    : "tooltip.golem_logix.punch_card.mode_processing").withStyle(ChatFormatting.GREEN));
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.punch_card.inputs").withStyle(ChatFormatting.GRAY));
            for (Map.Entry<com.golemlogix.system.ItemKey, Integer> e : data.ingredientTotals().entrySet()) {
                tooltipComponents.add(line(e.getValue(), e.getKey().stack()));
            }
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.punch_card.outputs").withStyle(ChatFormatting.GRAY));
            for (Map.Entry<com.golemlogix.system.ItemKey, Integer> e : data.outputTotals().entrySet()) {
                tooltipComponents.add(line(e.getValue(), e.getKey().stack()));
            }
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.punch_card.clear_hint")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.punch_card.blank")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    private static MutableComponent line(int count, ItemStack stack) {
        return Component.literal("  " + count + "x ").append(stack.getHoverName()).withStyle(ChatFormatting.AQUA);
    }
}
