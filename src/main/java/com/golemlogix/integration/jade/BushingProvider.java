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
 * Строка в подсказке над муфтой. Сеть считается на клиенте по видимым блокам (отдельный запрос к серверу не нужен),
 * результат на секунду запоминается, чтобы не обходить сеть каждый кадр.
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
            // scan, а не get: общий кэш сетей нельзя засорять клиентскими объектами в одиночной игре
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
