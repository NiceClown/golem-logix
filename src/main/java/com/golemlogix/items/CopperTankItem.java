package com.golemlogix.items;

import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;
import net.neoforged.neoforge.fluids.capability.wrappers.BucketPickupHandlerWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    public static final class Contents extends FluidHandlerItemStack {
        public Contents(ItemStack stack) {
            super(ModDataComponents.TANK_FLUID, stack, capacity(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            capacity = capacity(container);
            return super.fill(resource, action);
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity(container);
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            boolean on = !bucketMode(stack);
            stack.set(ModDataComponents.BUCKET_MODE.get(), on);
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(on ? "message.golem_logix.copper_tank.bucket_on"
                        : "message.golem_logix.copper_tank.bucket_off"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!bucketMode(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(face), face, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }
        IFluidHandlerItem contents = stack.getCapability(Capabilities.FluidHandler.ITEM);
        return bucket(contents, player, hand, level, pos, face) ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.fail(stack);
    }

    /**
     * One bucket's worth: scooped up from the fluid (or the fluid-holding block, like a cauldron) at pos if the tank takes it,
     * otherwise poured out into pos if it can hold the fluid (waterlogging) or onto the face beside it.
     */
    public static boolean bucket(@Nullable IFluidHandler contents, @Nullable Player player, Level level, BlockPos pos,
                                 Direction face) {
        return bucket(contents, player, InteractionHand.MAIN_HAND, level, pos, face);
    }

    public static boolean bucket(@Nullable IFluidHandler contents, @Nullable Player player, InteractionHand hand, Level level,
                                 BlockPos pos, Direction face) {
        if (contents == null) {
            return false;
        }
        if (pickup(contents, player, level, pos, face)) {
            return true;
        }
        FluidStack inside = contents.getFluidInTank(0);
        if (inside.isEmpty()) {
            return false;
        }
        FluidStack bucket = inside.copyWithAmount(FluidType.BUCKET_VOLUME);
        if (FluidUtil.tryPlaceFluid(player, level, hand, pos, contents, bucket)) {
            return true;
        }
        return FluidUtil.tryPlaceFluid(player, level, hand, pos.relative(face), contents, bucket);
    }

    /** A bucket's worth from the fluid source (or the fluid-holding block) at pos into the tank. */
    private static boolean pickup(IFluidHandler contents, @Nullable Player player, Level level, BlockPos pos, Direction face) {
        IFluidHandler source;
        if (level.getBlockState(pos).getBlock() instanceof BucketPickup pickup) {
            source = new BucketPickupHandlerWrapper(player, pickup, level, pos);
        } else {
            source = FluidUtil.getFluidHandler(level, pos, face).orElse(null);
        }
        if (source == null) {
            return false;
        }
        FluidStack moved = FluidUtil.tryFluidTransfer(contents, source, FluidType.BUCKET_VOLUME, true);
        if (moved.isEmpty()) {
            return false;
        }
        SoundEvent sound = moved.getFluidType().getSound(moved, SoundActions.BUCKET_FILL);
        if (sound != null) {
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return true;
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.add(contentsLine(fluid(stack), capacity(stack)).copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(bucketMode(stack) ? "tooltip.golem_logix.copper_tank.bucket_on"
                : "tooltip.golem_logix.copper_tank.bucket_off").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, tooltipFlag);
    }
}
