package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.VisCondenserBlockEntity;
import com.golemlogix.blocks.VisCondenserBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/** Jade overlay lines for a Vis Condenser (either half): chunk Aura/Flux, configured floor, output rate, and status. */
public enum VisCondenserProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "vis_condenser");
    private static final String TAG = "golem_logix_vis_condenser";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        VisCondenserBlockEntity be = VisCondenserBlock.entityOf(
                accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
        if (be == null) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("visTenths", (int) Math.round(be.chunkVis() * 10.0));
        t.putInt("base", be.chunkBase());
        t.putInt("fluxTenths", (int) Math.round(be.chunkFlux() * 10.0));
        t.putInt("threshold", be.threshold());
        t.putInt("mode", be.mode());
        t.putInt("sourceTenths", (int) Math.round(be.sourcePerSecond() * 10.0));
        t.putInt("status", be.status());
        t.putBoolean("lowVis", be.lowVisWarning());
        data.put(TAG, t);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(TAG)) {
            return;
        }
        CompoundTag t = accessor.getServerData().getCompound(TAG);
        double vis = t.getInt("visTenths") / 10.0;
        int base = t.contains("base") ? t.getInt("base") : 100;
        double flux = t.getInt("fluxTenths") / 10.0;
        int threshold = t.contains("threshold") ? t.getInt("threshold") : 25;
        int mode = t.contains("mode") ? t.getInt("mode") : VisCondenserBlockEntity.MODE_PERCENT;
        double sourcePerSec = t.getInt("sourceTenths") / 10.0;
        int status = t.getInt("status");
        boolean lowVis = t.getBoolean("lowVis");

        tooltip.add(Component.translatable("jade.golem_logix.vis_condenser.aura",
                        String.format(Locale.ROOT, "%.1f", vis), base, String.format(Locale.ROOT, "%.1f", flux))
                .withStyle(lowVis ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(mode == VisCondenserBlockEntity.MODE_PERCENT
                        ? "jade.golem_logix.vis_condenser.floor_pct"
                        : "jade.golem_logix.vis_condenser.floor_vis", threshold)
                .withStyle(ChatFormatting.GOLD));
        String sourceStr = String.format(Locale.ROOT,
                sourcePerSec == Math.rint(sourcePerSec) ? "%.0f" : "%.1f", sourcePerSec);
        tooltip.add(Component.translatable("jade.golem_logix.vis_condenser.rate", sourceStr)
                .withStyle(ChatFormatting.AQUA));
        ChatFormatting statusColor = switch (status) {
            case VisCondenserBlockEntity.STATUS_WORKING -> ChatFormatting.GREEN;
            case VisCondenserBlockEntity.STATUS_LOW_VIS_WORKING, VisCondenserBlockEntity.STATUS_NO_THAUMATURGE -> ChatFormatting.RED;
            default -> ChatFormatting.GOLD;
        };
        tooltip.add(Component.translatable("gui.golem_logix.vis_condenser.status." + status).withStyle(statusColor));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
