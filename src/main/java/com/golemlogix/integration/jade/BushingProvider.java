package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.CableBushingBlock;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * A line in the tooltip over a bushing. The network is computed on the client from the visible blocks (no separate request to the server),
 * and the result is remembered for a second so the network is not walked every frame.
 */
public enum BushingProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "bushing_channels");
    private BlockPos cachedPos;
    private long cachedTime = Long.MIN_VALUE;
    private Component cachedText = Component.empty();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockPos pos = accessor.getPosition();
        long now = accessor.getLevel().getGameTime();
        if (!pos.equals(cachedPos) || now - cachedTime > 20 || now < cachedTime) {
            // scan, not get: the shared network cache must not be littered with client objects in single player
            GolemNetwork net = GolemNetwork.scan(accessor.getLevel(), pos);
            cachedText = net.hasCore()
                    ? Component.translatable("jade.golem_logix.bushing", net.loadAt(pos), net.lineCapacity(), net.channelsUsed(), net.channelCapacity())
                    : CableBushingBlock.describe(net, pos);
            cachedPos = pos.immutable();
            cachedTime = now;
        }
        tooltip.add(cachedText);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
