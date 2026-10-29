package com.golemlogix.items;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.security.SecurityPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Identity Punch Card: bound to a player on right-click. Inserted into a Security Terminal
 * to grant network access permissions (Insert, Extract, Craft, Build).
 */
public class IdentityPunchCardItem extends Item {
    public IdentityPunchCardItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isBound(ItemStack stack) {
        return stack.has(ModDataComponents.IDENTITY_OWNER_ID.get());
    }

    public static UUID getOwnerId(ItemStack stack) {
        return stack.get(ModDataComponents.IDENTITY_OWNER_ID.get());
    }

    public static String getOwnerName(ItemStack stack) {
        String name = stack.get(ModDataComponents.IDENTITY_OWNER_NAME.get());
        return name != null ? name : "";
    }

    public static int getPermissions(ItemStack stack) {
        Integer perms = stack.get(ModDataComponents.IDENTITY_PERMISSIONS.get());
        return perms != null ? perms : SecurityPermission.ALL;
    }

    public static void setPermissions(ItemStack stack, int permissions) {
        stack.set(ModDataComponents.IDENTITY_PERMISSIONS.get(), permissions);
    }

    public static ItemStack of(UUID id, String name, int permissions) {
        ItemStack s = new ItemStack(com.golemlogix.init.ModItems.IDENTITY_PUNCH_CARD.get());
        s.set(ModDataComponents.IDENTITY_OWNER_ID.get(), id);
        s.set(ModDataComponents.IDENTITY_OWNER_NAME.get(), name);
        s.set(ModDataComponents.IDENTITY_PERMISSIONS.get(), permissions);
        return s;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isBound(stack)) {
            if (!level.isClientSide()) {
                stack.set(ModDataComponents.IDENTITY_OWNER_ID.get(), player.getUUID());
                stack.set(ModDataComponents.IDENTITY_OWNER_NAME.get(), player.getScoreboardName());
                stack.set(ModDataComponents.IDENTITY_PERMISSIONS.get(), SecurityPermission.ALL);
                player.sendOverlayMessage(Component.translatable("message.golem_logix.identity_card.bound", player.getScoreboardName()));
                level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isBound(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> lines, TooltipFlag tooltipFlag) {
        if (isBound(stack)) {
            lines.accept(Component.translatable("tooltip.golem_logix.identity_card.bound", getOwnerName(stack))
                    .withStyle(ChatFormatting.GOLD));
            int perms = getPermissions(stack);
            StringBuilder sb = new StringBuilder();
            if (SecurityPermission.INSERT.test(perms)) sb.append(" [↓]");
            if (SecurityPermission.EXTRACT.test(perms)) sb.append(" [↑]");
            if (SecurityPermission.CRAFT.test(perms)) sb.append(" [⚙]");
            if (SecurityPermission.BUILD.test(perms)) sb.append(" [⚒]");
            lines.accept(Component.translatable("tooltip.golem_logix.identity_card.permissions", sb.toString().trim())
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.accept(Component.translatable("tooltip.golem_logix.identity_card.blank")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
