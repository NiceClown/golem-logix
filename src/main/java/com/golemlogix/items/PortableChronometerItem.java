package com.golemlogix.items;

import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModTriggers;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Consumer;

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

        private static final RangeTier[] VALUES = values();
        private final String key;

        RangeTier(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    private record TargetCore(ServerLevel level, BlockPos pos) {
    }

    public static RangeTier getTier(ItemStack stack) {
        int t = stack.getOrDefault(ModDataComponents.WIRELESS_TIER.get(), 0);
        return RangeTier.VALUES[Math.max(0, Math.min(2, t))];
    }

    @Nullable
    public static GlobalPos getBound(ItemStack stack) {
        return stack.get(ModDataComponents.BOUND_CORE.get());
    }

    public static int getCharge(ItemStack stack) {
        return SourceChargeHelper.get(stack);
    }

    public static void setCharge(ItemStack stack, int charge) {
        SourceChargeHelper.set(stack, charge, MAX_CHARGE);
    }

    public static void setResonator(ItemStack stack, boolean linked) {
        if (stack.getOrDefault(ModDataComponents.RESONATOR_LINK.get(), false) != linked) {
            stack.set(ModDataComponents.RESONATOR_LINK.get(), linked);
        }
    }

    public static boolean inRange(int tier, Level playerLevel, Vec3 playerPos, Level netLevel, BlockPos anchor) {
        return inRange(tier, playerLevel, playerPos, netLevel, anchor, GolemConfig.LOCAL_WIRELESS_RANGE.get());
    }

    public static boolean inRange(int tier, Level playerLevel, Vec3 playerPos, Level netLevel, BlockPos anchor, double localRange) {
        if (tier >= 2) {
            return true;
        }
        if (playerLevel.dimension() != netLevel.dimension()) {
            return false;
        }
        if (tier == 1) {
            return true;
        }
        return playerPos.distanceToSqr(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5) <= localRange * localRange;
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
                player.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.bound")
                        .withStyle(ChatFormatting.AQUA));
            }
            return InteractionResult.SUCCESS;
        }
        if (SourceChargeHelper.tryChargeFromBlock(context, MAX_CHARGE,
                "message.golem_logix.chronometer.charged", "message.golem_logix.chronometer.nothing_to_charge", 1.6f)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        // the slot the Chronometer lies in is locked while the window is open (40 = offhand)
        int locked = hand == InteractionHand.MAIN_HAND ? sp.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        return open(sp, stack, locked) ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /**
     * Opens the terminal of the network this Chronometer is bound to: by a click with it, or by the hotkey for a Chronometer
     * anywhere on the player (Chronometers.find). locked = its inventory slot (40 = offhand), locked while the window is open,
     * or Chronometers.CURIO for one worn in the Curios slot. Returns false (with a message to the player) if it cannot.
     */
    public static boolean open(ServerPlayer sp, ItemStack stack, int locked) {
        int tier = getTier(stack).ordinal();
        TargetCore target = resolveTargetCore(sp, stack, tier);
        if (target == null) {
            return false;
        }
        GolemNetwork net = GolemNetwork.scan(target.level(), target.pos());
        String err = net.accessError(null);
        if (err != null) {
            sp.sendOverlayMessage(Component.translatable(err));
            return false;
        }
        if (!hasTerminalAccess(net, sp)) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.security_denied"));
            return false;
        }
        if (!consumeOpenPower(sp, stack, net)) {
            return false;
        }
        sp.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.accessing")
                .withStyle(ChatFormatting.AQUA));
        // the Chronometer always has a crafting grid
        sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new TerminalMenu(id, inv, target.level(), target.pos(), null, null, true, true, tier, locked),
                        Component.translatable("container.golem_logix.chronometer")),
                buf -> {
                    buf.writeBoolean(true);
                    buf.writeBoolean(true);
                    buf.writeVarInt(locked);
                });
        return true;
    }

    @Nullable
    private static TargetCore resolveTargetCore(ServerPlayer sp, ItemStack stack, int tier) {
        Level level = sp.level();
        GlobalPos bound = getBound(stack);
        if (bound == null) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.unbound").withStyle(ChatFormatting.RED));
            return null;
        }
        // trying to reach the core from another dimension is one of the secret recipe trials
        if (level.dimension() != bound.dimension()) {
            ModTriggers.CHRONOMETER_DIMENSION.get().trigger(sp);
        }
        if (tier < 2 && level.dimension() != bound.dimension()) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.other_dimension").withStyle(ChatFormatting.RED));
            return null;
        }
        ServerLevel netLevel = sp.level().getServer().getLevel(bound.dimension());
        if (netLevel == null || !netLevel.isLoaded(bound.pos())
                || !(netLevel.getBlockState(bound.pos()).getBlock() instanceof AtlasCoreBlock)) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.lost").withStyle(ChatFormatting.RED));
            return null;
        }
        if (!inRange(tier, level, sp.position(), netLevel, bound.pos())) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.out_of_range").withStyle(ChatFormatting.RED));
            return null;
        }
        return new TargetCore(netLevel, bound.pos());
    }

    private static boolean hasTerminalAccess(GolemNetwork net, ServerPlayer player) {
        return net.hasPermission(player, SecurityPermission.INSERT)
                || net.hasPermission(player, SecurityPermission.EXTRACT)
                || net.hasPermission(player, SecurityPermission.CRAFT);
    }

    /** Power check: a Resonator on the network powers the Chronometer remotely; otherwise spend own Source charge. */
    private static boolean consumeOpenPower(ServerPlayer sp, ItemStack stack, GolemNetwork net) {
        if (!SourceHelper.sourceRequired()) {
            return true;
        }
        boolean resonator = net.hasResonator();
        setResonator(stack, resonator);
        if (resonator) {
            setCharge(stack, MAX_CHARGE);
            return true;
        }
        int charge = getCharge(stack);
        if (charge < OPEN_COST) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.empty").withStyle(ChatFormatting.RED));
            return false;
        }
        setCharge(stack, charge - OPEN_COST);
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
                                Consumer<Component> lines, TooltipFlag tooltipFlag) {
        RangeTier tier = getTier(stack);
        lines.accept(Component.translatable("tooltip.golem_logix.chronometer.tier." + tier.getKey())
                .withStyle(ChatFormatting.DARK_AQUA));

        GlobalPos bound = getBound(stack);
        if (bound != null) {
            BlockPos p = bound.pos();
            lines.accept(Component.translatable("tooltip.golem_logix.chronometer.bound", p.getX(), p.getY(), p.getZ(),
                    bound.dimension().identifier().toString()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.accept(Component.translatable("tooltip.golem_logix.chronometer.unbound_hint")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        if (SourceHelper.sourceRequired()) {
            if (stack.getOrDefault(ModDataComponents.RESONATOR_LINK.get(), false)) {
                lines.accept(Component.translatable("tooltip.golem_logix.chronometer.resonator")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                lines.accept(Component.translatable("tooltip.golem_logix.chronometer.charge", getCharge(stack), MAX_CHARGE)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        super.appendHoverText(stack, context, display, lines, tooltipFlag);
    }
}
