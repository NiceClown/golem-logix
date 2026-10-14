package com.golemlogix.items;

import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModTriggers;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Portable Chronometer: a wireless crafting terminal (with a crafting grid) built into a pocket watch.
 * Bound to an Atlas Core (Shift + right-click on the core). Range: local / within the dimension /
 * interdimensional (upgraded with the Interdimensional Occult Matrix). With Ars Nouveau it spends Source from its own charge, which is
 * refilled from a jar (right-click on the jar), and an Eternal Frost Resonator next to the network powers the Chronometer remotely.
 */
public class PortableChronometerItem extends Item {
    public static final int MAX_CHARGE = 10_000;
    public static final int OPEN_COST = 20;
    public static final int DRAIN_PER_SECOND = 2;

    public PortableChronometerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public enum RangeTier {
        LOCAL("local"),
        DIMENSIONAL("dimensional"),
        CROSS_DIMENSIONAL("cross_dimensional");

        private final String key;

        RangeTier(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    public static RangeTier getTier(ItemStack stack) {
        int t = stack.getOrDefault(ModDataComponents.WIRELESS_TIER.get(), 0);
        return RangeTier.values()[Math.max(0, Math.min(2, t))];
    }

    @Nullable
    public static GlobalPos getBound(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_CORE.get());
    }

    public static int getCharge(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.SOURCE_CHARGE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(ModDataComponents.SOURCE_CHARGE.get(), Math.max(0, Math.min(MAX_CHARGE, charge)));
    }

    public static void setResonator(ItemStack stack, boolean linked) {
        if (stack.getOrDefault(ModDataComponents.RESONATOR_LINK.get(), false) != linked) {
            stack.set(ModDataComponents.RESONATOR_LINK.get(), linked);
        }
    }

    public static boolean inRange(int tier, Level playerLevel, Vec3 playerPos, Level netLevel, BlockPos anchor) {
        if (tier >= 2) {
            return true;
        }
        if (playerLevel.dimension() != netLevel.dimension()) {
            return false;
        }
        if (tier == 1) {
            return true;
        }
        double range = GolemConfig.LOCAL_WIRELESS_RANGE.get();
        return playerPos.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) <= range * range;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown() && level.getBlockState(context.getClickedPos()).getBlock() instanceof AtlasCoreBlock) {
            if (!level.isClientSide()) {
                context.getItemInHand().set(ModDataComponents.BOUND_CORE.get(),
                        GlobalPos.of(level.dimension(), context.getClickedPos()));
                player.displayClientMessage(Component.translatable("message.golem_logix.chronometer.bound")
                        .withStyle(ChatFormatting.AQUA), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        // charging from a Source jar (or any Ars Nouveau device with a Source store)
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        SourceHelper.Supplier jar = be == null ? null : SourceHelper.asSupplier(be);
        if (jar != null && SourceHelper.sourceRequired()) {
            if (!level.isClientSide()) {
                ItemStack stack = context.getItemInHand();
                int need = MAX_CHARGE - getCharge(stack);
                int got = need <= 0 ? 0 : jar.drain(need);
                setCharge(stack, getCharge(stack) + got);
                player.displayClientMessage(Component.translatable(got > 0 ? "message.golem_logix.chronometer.charged"
                        : "message.golem_logix.chronometer.nothing_to_charge", getCharge(stack), MAX_CHARGE)
                        .withStyle(got > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
                if (got > 0) {
                    level.playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        // the slot the Chronometer lies in is locked while the window is open (40 = offhand)
        int locked = hand == InteractionHand.MAIN_HAND ? sp.getInventory().selected : Inventory.SLOT_OFFHAND;
        return open(sp, stack, locked) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /**
     * Opens the terminal of the network this Chronometer is bound to: by a click with it, or by the hotkey for a Chronometer
     * anywhere on the player (Chronometers.find). locked = its inventory slot (40 = offhand), locked while the window is open,
     * or Chronometers.CURIO for one worn in the Curios slot. Returns false (with a message to the player) if it cannot.
     */
    public static boolean open(ServerPlayer sp, ItemStack stack, int locked) {
        Level level = sp.level();
        GlobalPos bound = getBound(stack);
        if (bound == null) {
            sp.displayClientMessage(Component.translatable("message.golem_logix.chronometer.unbound")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // trying to reach the core from another dimension is one of the secret recipe trials
        if (level.dimension() != bound.dimension()) {
            ModTriggers.CHRONOMETER_DIMENSION.get().trigger(sp);
        }
        ServerLevel netLevel = sp.server.getLevel(bound.dimension());
        if (netLevel == null || !(netLevel.isLoaded(bound.pos()) && netLevel.getBlockState(bound.pos()).getBlock() instanceof AtlasCoreBlock)) {
            sp.displayClientMessage(Component.translatable(level.dimension() != bound.dimension()
                    ? "message.golem_logix.chronometer.other_dimension" : "message.golem_logix.chronometer.lost")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        int tier = getTier(stack).ordinal();
        if (!inRange(tier, level, sp.position(), netLevel, bound.pos())) {
            sp.displayClientMessage(Component.translatable(level.dimension() != bound.dimension()
                    ? "message.golem_logix.chronometer.other_dimension" : "message.golem_logix.chronometer.out_of_range")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        GolemNetwork net = GolemNetwork.scan(netLevel, bound.pos());
        String err = net.accessError(null);
        if (err != null) {
            sp.displayClientMessage(Component.translatable(err), true);
            return false;
        }
        // power: the network's Resonator sends Source itself, otherwise the own charge is needed
        if (SourceHelper.sourceRequired()) {
            boolean resonator = net.hasResonator();
            setResonator(stack, resonator);
            if (resonator) {
                setCharge(stack, MAX_CHARGE);
            } else if (getCharge(stack) < OPEN_COST) {
                sp.displayClientMessage(Component.translatable("message.golem_logix.chronometer.empty")
                        .withStyle(ChatFormatting.RED), true);
                return false;
            } else {
                setCharge(stack, getCharge(stack) - OPEN_COST);
            }
        }
        sp.displayClientMessage(Component.translatable("message.golem_logix.chronometer.accessing")
                .withStyle(ChatFormatting.AQUA), true);
        BlockPos anchor = bound.pos();
        // the Chronometer always has a crafting grid
        sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new TerminalMenu(id, inv, netLevel, anchor, null, null, true, true, tier, locked),
                        Component.translatable("container.golem_logix.chronometer")),
                buf -> {
                    buf.writeBoolean(true);
                    buf.writeBoolean(true);
                    buf.writeVarInt(locked);
                });
        return true;
    }

    /**
     * The charge changes every second while the window is open, and when charging: the hand must not "re-grab" the item every time
     * (lower and raise it again), it is the same chronometer, just with another number.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !oldStack.is(newStack.getItem());
    }

    // ------------------------------------------------------------------ charge bar

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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        RangeTier tier = getTier(stack);
        tooltipComponents.add(Component.translatable("tooltip.golem_logix.chronometer.tier." + tier.getKey())
                .withStyle(ChatFormatting.DARK_AQUA));

        GlobalPos bound = getBound(stack);
        if (bound != null) {
            BlockPos p = bound.pos();
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.chronometer.bound", p.getX(), p.getY(), p.getZ(),
                    bound.dimension().location().toString()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.golem_logix.chronometer.unbound_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (SourceHelper.sourceRequired()) {
            if (stack.getOrDefault(ModDataComponents.RESONATOR_LINK.get(), false)) {
                tooltipComponents.add(Component.translatable("tooltip.golem_logix.chronometer.resonator")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltipComponents.add(Component.translatable("tooltip.golem_logix.chronometer.charge", getCharge(stack), MAX_CHARGE)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
