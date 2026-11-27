package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.EssentiaCauldronBlockEntity;
import com.golemlogix.blocks.EssentiaCauldronBlock;
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

/** Jade overlay lines for an Essentia Cryo-Cauldron (either half): Auram/Gelum buffers, Ice tier, Source/s, and status. */
public enum EssentiaCauldronProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "essentia_cryo_cauldron");
    private static final String TAG = "golem_logix_essentia_cryo_cauldron";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        EssentiaCauldronBlockEntity be = EssentiaCauldronBlock.entityOf(
                accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
        if (be == null) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("auram", be.auram());
        t.putInt("gelum", be.gelum());
        t.putInt("cap", be.capacity());
        t.putInt("iceTier", be.iceTier());
        t.putInt("icePct", (int) Math.round(be.iceFill() * 100.0));
        t.putInt("sourceTenths", (int) Math.round(be.sourcePerSecond() * 10.0));
        t.putInt("status", be.status());
        t.putBoolean("boilOff", be.boilOff());
        data.put(TAG, t);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(TAG)) {
            return;
        }
        CompoundTag t = accessor.getServerData().getCompound(TAG);
        int auram = t.getInt("auram");
        int gelum = t.getInt("gelum");
        int cap = t.contains("cap") ? t.getInt("cap") : EssentiaCauldronBlockEntity.TANK_CAPACITY;
        int iceTier = t.getInt("iceTier");
        int icePct = t.getInt("icePct");
        double sourcePerSec = t.getInt("sourceTenths") / 10.0;
        int status = t.getInt("status");
        boolean boilOff = t.getBoolean("boilOff");

        tooltip.add(Component.translatable("jade.golem_logix.cauldron.essentia", auram, cap, gelum, cap)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        Component iceLine = iceTier > 0
                ? Component.translatable("gui.golem_logix.cauldron.ice." + iceTier, icePct).withStyle(ChatFormatting.AQUA)
                : Component.translatable("gui.golem_logix.cauldron.ice.0").withStyle(ChatFormatting.RED);
        tooltip.add(iceLine);
        String sourceStr = String.format(Locale.ROOT,
                sourcePerSec == Math.rint(sourcePerSec) ? "%.0f" : "%.1f", sourcePerSec);
        tooltip.add(Component.translatable("jade.golem_logix.cauldron.rate", sourceStr)
                .withStyle(ChatFormatting.AQUA));
        ChatFormatting statusColor = switch (status) {
            case EssentiaCauldronBlockEntity.STATUS_WORKING -> ChatFormatting.GREEN;
            case EssentiaCauldronBlockEntity.STATUS_BOIL_OFF, EssentiaCauldronBlockEntity.STATUS_NO_ICE,
                 EssentiaCauldronBlockEntity.STATUS_NO_THAUMATURGE -> ChatFormatting.RED;
            default -> ChatFormatting.GOLD;
        };
        tooltip.add(Component.translatable(boilOff
                        ? "gui.golem_logix.cauldron.boil_warn"
                        : "gui.golem_logix.cauldron.status." + status)
                .withStyle(statusColor));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
