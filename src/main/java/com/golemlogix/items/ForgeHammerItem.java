package com.golemlogix.items;

import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Кузнечный молот: им куют заготовки на наковальне (несобранном Насыщателе Маны) — ЛКМ по наковальне. Каждый удар тратит
 * Source из собственного запаса молота; запас пополняют, как у Хронометра, — ПКМ молотом по банке Source. Без Ars Nouveau
 * (или с requireSource=false) молот работает без заряда.
 */
public class ForgeHammerItem extends Item {
    public static final int MAX_CHARGE = 4000;
    public static final int STRIKE_COST = 100;

    public ForgeHammerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int getCharge(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOURCE_CHARGE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(ModDataComponents.SOURCE_CHARGE.get(), Math.max(0, Math.min(MAX_CHARGE, charge)));
    }

    /** Хватит ли заряда на удар (без Source — всегда). */
    public static boolean canStrike(ItemStack stack) {
        return !SourceHelper.sourceRequired() || getCharge(stack) >= STRIKE_COST;
    }

    /** Списать заряд за удар. */
    public static void spendStrike(ItemStack stack) {
        if (SourceHelper.sourceRequired()) {
            setCharge(stack, getCharge(stack) - STRIKE_COST);
        }
    }

    /** Наковальня — несобранный Насыщатель: по ней молот бьёт, а не ломает её. */
    public static boolean isAnvil(BlockState state) {
        return state.getBlock() instanceof ManaSaturatorBlock && !state.getValue(ManaSaturatorBlock.FORMED);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !isAnvil(state);
    }

    /** ПКМ по банке Source (или другому механизму Ars Nouveau с запасом) — зарядить молот. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        SourceHelper.Supplier jar = be == null ? null : SourceHelper.asSupplier(be);
        if (player == null || jar == null || !SourceHelper.sourceRequired()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            int need = MAX_CHARGE - getCharge(stack);
            int got = need <= 0 ? 0 : jar.drain(need);
            setCharge(stack, getCharge(stack) + got);
            player.displayClientMessage(Component.translatable(got > 0 ? "message.golem_logix.hammer.charged"
                    : "message.golem_logix.hammer.nothing_to_charge", getCharge(stack), MAX_CHARGE)
                    .withStyle(got > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
            if (got > 0) {
                level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return SourceHelper.sourceRequired() && getCharge(stack) < MAX_CHARGE;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getCharge(stack) / MAX_CHARGE);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB07CFF;
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
