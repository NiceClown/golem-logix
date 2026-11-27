package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ChannelCounterBlockEntity;
import com.golemlogix.blocks.ChannelCounterBlock;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Lines over a Channel Counter: live channels/capacity of its monitored node, Reactor bonus, and display mode. */
public enum ChannelCounterProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "channel_counter");
    private static final String TAG = "golem_logix_channel_counter";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();
        BlockPos node = ChannelCounterBlock.attached(pos, accessor.getBlockState());
        boolean online = false;
        int used = 0;
        int cap = 0;
        int reactorBonus = 0;
        if (level.isLoaded(node) && level.getBlockState(node).getBlock() instanceof GolemNode) {
            GolemNetwork net = GolemNetwork.get(level, node);
            GolemNetwork.NodeChannels ch = net.channelsAt(node);
            online = net.hasCore() && net.isOnline() && ch.member();
            used = online ? ch.load() : 0;
            cap = online ? (ch.capacity() > 0 && ch.capacity() < 100000 ? ch.capacity() : net.channelCapacity()) : 0;
            reactorBonus = online ? net.channelBonusPercent() : 0;
        }
        boolean showCap = accessor.getBlockEntity() instanceof ChannelCounterBlockEntity be && be.showsCapacity();
        CompoundTag t = new CompoundTag();
        t.putBoolean("online", online);
        t.putInt("used", used);
        t.putInt("cap", cap);
        t.putBoolean("showCap", showCap);
        t.putInt("reactorBonus", reactorBonus);
        data.put(TAG, t);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        boolean online;
        int used;
        int cap;
        boolean showCap;
        int reactorBonus;
        if (accessor.getServerData().contains(TAG)) {
            CompoundTag t = accessor.getServerData().getCompound(TAG);
            online = t.getBoolean("online");
            used = t.getInt("used");
            cap = t.getInt("cap");
            showCap = t.getBoolean("showCap");
            reactorBonus = t.getInt("reactorBonus");
        } else if (accessor.getBlockEntity() instanceof ChannelCounterBlockEntity be) {
            online = be.online();
            used = be.used();
            cap = be.capacity();
            showCap = be.showsCapacity();
            reactorBonus = 0;
        } else {
            return;
        }

        if (online) {
            tooltip.add(Component.translatable("jade.golem_logix.channels_of", used, cap));
            if (reactorBonus > 0) {
                tooltip.add(Component.translatable("jade.golem_logix.reactor_boost", reactorBonus)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        } else {
            tooltip.add(Component.translatable("jade.golem_logix.device.offline").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(showCap ? "jade.golem_logix.counter.mode_capacity"
                : "jade.golem_logix.counter.mode_used").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
