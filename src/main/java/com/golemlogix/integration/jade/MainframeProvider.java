package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.blocks.MainframePartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Lines over a Cryo-Mainframe (any of its parts): cores and how many are busy, calculators, steps per cycle per core. */
public enum MainframeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "mainframe");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockPos master = accessor.getBlock() instanceof MainframePartBlock
                ? MainframePartBlock.controllerOf(accessor.getPosition(), accessor.getBlockState()) : accessor.getPosition();
        if (!(accessor.getLevel().getBlockEntity(master) instanceof CryoMainframeBlockEntity mf)) {
            return;
        }
        if (!mf.getBlockState().getValue(CryoMainframeBlock.FORMED)) {
            tooltip.add(Component.translatable("jade.golem_logix.mainframe.not_formed").withStyle(ChatFormatting.GOLD));
            return;
        }
        tooltip.add(Component.translatable("jade.golem_logix.mainframe.cores", mf.clientBusy(), mf.clientCores(), mf.clientCalcs())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("jade.golem_logix.mainframe.workers", mf.clientWorkers()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
