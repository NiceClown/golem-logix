package com.golemlogix.items;

import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.fluid.ItemAccessFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The Copper Tank as an item: it carries its fluid and its capacity. In bucket mode (sneak + right-click to switch) a right-click
 * scoops up a bucket's worth of the fluid looked at, or pours one out where it points, instead of placing the tank.
 */
public class CopperTankItem extends ModBlockItem {
    public CopperTankItem(Block block, String tooltipKey, Properties properties) {
        super(block, tooltipKey, properties);
    }

    public static FluidStack fluid(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.EMPTY).copy();
    }

    public static int capacity(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TANK_CAPACITY.get(), CopperTankBlockEntity.CAPACITY);
    }

    public static boolean bucketMode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BUCKET_MODE.get(), false);
    }

    /** "Water: 5 000 / 16 000 mB", or "Empty: 0 / 16 000 mB". */
    public static Component contentsLine(FluidStack fluid, int capacity) {
        Component name = fluid.isEmpty() ? Component.translatable("message.golem_logix.copper_tank.empty") : fluid.getHoverName();
        return Component.translatable("message.golem_logix.copper_tank.contents", name, mb(fluid.getAmount()), mb(capacity));
    }

    /** 16000 -> "16 000". */
    public static String mb(int amount) {
        StringBuilder sb = new StringBuilder(Integer.toString(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, ' ');
        }
        return sb.toString();
    }

    /** The fluid inside the item, for buckets, the terminal and machines; the capacity follows the item's. */
    public static final class Contents extends ItemAccessFluidHandler {
        public Contents(ItemAccess access) {
            super(access, ModDataComponents.TANK_FLUID.get(), capacity(access.getResource().toStack()));
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return capacity(itemAccess.getResource().toStack());
        }
    }

    /** In bucket mode the tank is not placed: the click goes on to {@link #use}. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (bucketMode(context.getItemInHand())) {
            return InteractionResult.PASS;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            boolean on = !bucketMode(stack);
            stack.set(ModDataComponents.BUCKET_MODE.get(), on);
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(on ? "message.golem_logix.copper_tank.bucket_on"
                        : "message.golem_logix.copper_tank.bucket_off"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!bucketMode(stack)) {
            return InteractionResult.PASS;
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(face), face, stack)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ResourceHandler<FluidResource> contents = stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forPlayerInteraction(player, hand));
        return bucket(contents, player, level, pos, face) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    /**
     * One bucket's worth: scooped up from the fluid (or the fluid-holding block, like a cauldron) at pos if the tank takes it,
     * otherwise poured out into pos if it can hold the fluid (waterlogging) or onto the face beside it.
     */
    public static boolean bucket(@Nullable ResourceHandler<FluidResource> contents, @Nullable Player player, Level level, BlockPos pos,
                                 Direction face) {
        if (contents == null) {
            return false;
        }
        if (!FluidUtil.tryPickupFluid(contents, player, level, pos, face, null).isEmpty()) {
            return true;
        }
        if (!FluidUtil.tryPlaceFluid(contents, player, level, pos, true, null).isEmpty()) {
            return true;
        }
        return !FluidUtil.tryPlaceFluid(contents, player, level, pos.relative(face), true, null).isEmpty();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !fluid(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(13, Math.round(13f * fluid(stack).getAmount() / capacity(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FB8E8;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag tooltipFlag) {
        lines.accept(contentsLine(fluid(stack), capacity(stack)).copy().withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable(bucketMode(stack) ? "tooltip.golem_logix.copper_tank.bucket_on"
                : "tooltip.golem_logix.copper_tank.bucket_off").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, lines, tooltipFlag);
    }
}
