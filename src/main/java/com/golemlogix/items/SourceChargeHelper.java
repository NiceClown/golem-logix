package com.golemlogix.items;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shared Source charge storage, jar refilling, and durability bar helpers for charged items (Chronometer, Forge Hammer). */
final class SourceChargeHelper {
    static final int BAR_COLOR = 0xB07CFF;

    private SourceChargeHelper() {
    }

    static int get(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOURCE_CHARGE.get(), 0);
    }

    static void set(ItemStack stack, int charge, int maxCharge) {
        stack.set(ModDataComponents.SOURCE_CHARGE.get(), Math.max(0, Math.min(maxCharge, charge)));
    }

    static boolean isBarVisible(ItemStack stack, int maxCharge) {
        return SourceHelper.sourceRequired() && get(stack) < maxCharge;
    }

    static int barWidth(ItemStack stack, int maxCharge) {
        return Math.round(13f * get(stack) / maxCharge);
    }

    /**
     * Attempts to refill the held item from the clicked Source supplier block entity.
     * Returns true if the block is a Source supplier and Source is required (whether or not charge was actually transferred).
     */
    static boolean tryChargeFromBlock(UseOnContext context, int maxCharge, String chargedKey, String fullKey, float chimePitch) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !SourceHelper.sourceRequired()) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        SourceHelper.Supplier jar = be == null ? null : SourceHelper.asSupplier(be);
        if (jar == null) {
            return false;
        }
        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            int current = get(stack);
            int need = maxCharge - current;
            int got = need <= 0 ? 0 : jar.drain(need);
            int updated = Math.max(0, Math.min(maxCharge, current + got));
            set(stack, updated, maxCharge);
            player.displayClientMessage(Component.translatable(got > 0 ? chargedKey : fullKey, updated, maxCharge)
                    .withStyle(got > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
            if (got > 0) {
                level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, chimePitch);
            }
        }
        return true;
    }
}
