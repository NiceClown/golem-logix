package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Lines over the Atlas Core: power, channels, Source use, from the state the core sends to clients anyway. */
public enum CoreProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "atlas_core");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof AtlasCoreBlockEntity core)) {
            return;
        }
        if (core.isDuplicate()) {
            tooltip.add(Component.translatable("jade.golem_logix.core.duplicate").withStyle(ChatFormatting.GOLD));
            return;
        }
        if (!core.isPowered()) {
            tooltip.add(Component.translatable("jade.golem_logix.core.off").withStyle(ChatFormatting.RED));
        } else if (core.isInfiniteSource()) {
            tooltip.add(Component.translatable("jade.golem_logix.core.on_free").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("jade.golem_logix.core.on", core.getSourcePerSecond()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("jade.golem_logix.core.channels", core.getChannelsUsed(), core.getChannelCapacity(),
                Component.translatable(core.isFormed() ? "jade.golem_logix.core.array" : "jade.golem_logix.core.tower")));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
