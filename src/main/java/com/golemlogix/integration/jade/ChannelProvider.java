package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.PipeBlockEntity;
import com.golemlogix.blocks.AtlasConduitBlock;
import com.golemlogix.blocks.AtlasCoreBlock;
import com.golemlogix.blocks.CableBushingBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import com.golemlogix.blocks.SnowPipeBlock;
import com.golemlogix.system.ChannelCalculation;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Channels in the Jade tooltip of every network node of the mod, laid out like the AE2 tooltips: a device says "Device Online",
 * "Device Missing Channel" or "Device Offline", a pipe "X of Y Channels"; a bushing shows its line and the whole network, an
 * expander its exit. The numbers come from the server's network (the same one the devices work with), so the tooltip can never
 * disagree with what the devices do. A device without a channel also names what stopped it.
 */
public enum ChannelProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "channels");
    /** The key of this provider's data in the server data (the core's tooltip reads the network totals from it too). */
    static final String TAG = "golem_logix_channels";

    static boolean isNode(BlockState state) {
        return state.getBlock() instanceof GolemNode
                && GolemLogixMod.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    // ----------------------------------------------------------------- server

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return isNode(accessor.getBlockState());
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockPos pos = accessor.getPosition();
        GolemNetwork net = GolemNetwork.get(accessor.getLevel(), pos);
        GolemNetwork.NodeChannels ch = net.channelsAt(pos);
        CompoundTag t = new CompoundTag();
        t.putBoolean("core", net.hasCore());
        t.putBoolean("online", net.isOnline());
        t.putBoolean("needs", ch.needsChannel());
        t.putBoolean("has", ch.hasChannel());
        t.putInt("used", ch.load());
        t.putInt("max", ch.capacity());
        t.putString("why", ch.missing().name());
        if (ch.bottleneck() != null) {
            BlockPos b = ch.bottleneck();
            t.putIntArray("bpos", new int[]{b.getX(), b.getY(), b.getZ()});
            t.putString("bblock", BuiltInRegistries.BLOCK.getKey(accessor.getLevel().getBlockState(b).getBlock()).toString());
        }
        t.putInt("netUsed", net.channelsUsed());
        t.putInt("netMax", net.channelCapacity());
        data.put(TAG, t);
    }

    // ----------------------------------------------------------------- client

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        if (!isNode(state)) {
            return;
        }
        CompoundTag data = accessor.getServerData();
        if (data.contains(TAG)) {
            describe(tooltip, state.getBlock(), data.getCompoundOrEmpty(TAG));
        }
        if (accessor.getBlockEntity() instanceof PipeBlockEntity be && be.plugMask() != 0) {
            MutableComponent sides = Component.empty();
            boolean first = true;
            for (Direction d : Direction.values()) {
                if (be.isPlugged(d)) {
                    if (!first) {
                        sides.append(", ");
                    }
                    sides.append(Component.translatable("direction.golem_logix." + d.getName()));
                    first = false;
                }
            }
            tooltip.add(Component.translatable("jade.golem_logix.plugs", sides).withStyle(ChatFormatting.AQUA));
        }
    }

    private static void describe(ITooltip tooltip, Block block, CompoundTag t) {
        if (block instanceof AtlasCoreBlock || block instanceof AtlasConduitBlock) {
            return; // the controller: the core's own provider shows the network
        }
        boolean core = t.getBooleanOr("core", false);
        int used = t.getIntOr("used", 0);
        int max = t.getIntOr("max", 0);
        if (block instanceof CableBushingBlock) {
            if (!core) {
                tooltip.add(Component.translatable("jade.golem_logix.device.no_core").withStyle(ChatFormatting.RED));
                return;
            }
            tooltip.add(Component.translatable("jade.golem_logix.line", used, max));
            tooltip.add(Component.translatable("jade.golem_logix.network", t.getIntOr("netUsed", 0), t.getIntOr("netMax", 0)));
            return;
        }
        if (block instanceof ChannelExpanderBlock) {
            if (!core) {
                tooltip.add(Component.translatable("jade.golem_logix.device.no_core").withStyle(ChatFormatting.RED));
            } else if (max <= 0) {
                tooltip.add(Component.translatable("jade.golem_logix.expander.closed").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("jade.golem_logix.expander", used, max));
            }
            return;
        }
        if (block instanceof SnowPipeBlock) {
            if (!core) {
                tooltip.add(Component.translatable("jade.golem_logix.device.no_core").withStyle(ChatFormatting.RED));
            } else {
                tooltip.add(channels(used, max));
            }
            return;
        }
        boolean needs = t.getBooleanOr("needs", false);
        boolean has = t.getBooleanOr("has", false);
        if (needs) {
            if (!core) {
                tooltip.add(Component.translatable("jade.golem_logix.device.offline").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("jade.golem_logix.device.no_core").withStyle(ChatFormatting.RED));
            } else if (!t.getBooleanOr("online", false)) {
                tooltip.add(Component.translatable("jade.golem_logix.device.offline").withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("jade.golem_logix.device.no_power").withStyle(ChatFormatting.RED));
            } else if (has) {
                tooltip.add(Component.translatable("jade.golem_logix.device.online").withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("jade.golem_logix.device.missing").withStyle(ChatFormatting.GRAY));
                Component why = reason(t);
                if (why != null) {
                    tooltip.add(why);
                }
            }
        }
        // a device that also carries the channels of devices behind it shows how full it is, like a pipe
        if (core && used > (has ? 1 : 0)) {
            tooltip.add(channels(used, max));
        }
    }

    private static Component channels(int used, int max) {
        return max < 0 ? Component.translatable("jade.golem_logix.channels", used)
                : Component.translatable("jade.golem_logix.channels_of", used, max);
    }

    private static Component reason(CompoundTag t) {
        ChannelCalculation.Missing missing;
        try {
            missing = ChannelCalculation.Missing.valueOf(t.getStringOr("why", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return switch (missing) {
            case NETWORK_FULL -> Component.translatable("jade.golem_logix.why.full", t.getIntOr("netUsed", 0), t.getIntOr("netMax", 0))
                    .withStyle(ChatFormatting.RED);
            case BOTTLENECK -> {
                int[] p = t.getIntArray("bpos").orElse(new int[0]);
                Identifier id = Identifier.tryParse(t.getStringOr("bblock", ""));
                Block b = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
                Component name = b == null ? Component.literal("?") : b.getName();
                yield p.length == 3 ? Component.translatable("jade.golem_logix.why.bottleneck", name, p[0], p[1], p[2])
                        .withStyle(ChatFormatting.RED) : null;
            }
            case NO_ROUTE -> Component.translatable("jade.golem_logix.why.no_route").withStyle(ChatFormatting.RED);
            case NONE -> null;
        };
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
