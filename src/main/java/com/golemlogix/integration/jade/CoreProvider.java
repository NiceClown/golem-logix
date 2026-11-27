package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.system.SourceDiscount;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Lines over the Atlas Core: power, channels, Source use, from the state the core sends to clients anyway. */
public enum CoreProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "atlas_core");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof AtlasCoreBlockEntity core)) {
            return;
        }
        if (core.isDuplicate()) {
            tooltip.add(Component.translatable("jade.golem_logix.core.duplicate").withStyle(ChatFormatting.GOLD));
            return;
        }
        // the live numbers of the server's network (ChannelProvider sends them); the core's own copy is a second old at most
        int used = core.getChannelsUsed();
        int max = core.getChannelCapacity();
        boolean tower = core.isTower();
        int gigaDiscount = core.getGigaDiscountPercent();
        int reactorBonus = core.getReactorBonusPercent();
        int spsHundredths = core.getSourcePerSecondHundredths();
        if (accessor.getServerData().contains(ChannelProvider.TAG)) {
            var t = accessor.getServerData().getCompoundOrEmpty(ChannelProvider.TAG);
            used = t.getIntOr("netUsed", used);
            max = t.getIntOr("netMax", max);
            tower = t.getBooleanOr("tower", tower);
            gigaDiscount = t.getIntOr("gigaDiscount", gigaDiscount);
            reactorBonus = t.getIntOr("reactorBonus", reactorBonus);
            spsHundredths = t.getIntOr("spsHundredths", spsHundredths);
        }
        if (!core.isPowered()) {
            tooltip.add(Component.translatable("jade.golem_logix.core.off").withStyle(ChatFormatting.RED));
        } else if (core.isInfiniteSource()) {
            tooltip.add(Component.translatable("jade.golem_logix.core.on_free").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("jade.golem_logix.core.on", SourceDiscount.formatRate(spsHundredths))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("jade.golem_logix.core.channels", used, max,
                Component.translatable(core.isFormed() ? "jade.golem_logix.core.array"
                        : tower ? "jade.golem_logix.core.tower" : "jade.golem_logix.core.alone")));
        if (gigaDiscount > 0) {
            tooltip.add(Component.translatable("jade.golem_logix.core.giga_discount", gigaDiscount)
                    .withStyle(ChatFormatting.GREEN));
        }
        if (reactorBonus > 0) {
            tooltip.add(Component.translatable("jade.golem_logix.core.reactor_bonus", reactorBonus)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
