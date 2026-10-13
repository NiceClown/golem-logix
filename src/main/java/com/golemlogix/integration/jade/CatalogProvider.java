package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.blocks.CatalogPartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Lines over a Punch Card Catalog (any of its parts): how many cards and places. */
public enum CatalogProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "catalog");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockPos master = accessor.getBlock() instanceof CatalogPartBlock
                ? CatalogPartBlock.controllerOf(accessor.getPosition(), accessor.getBlockState()) : accessor.getPosition();
        if (!(accessor.getLevel().getBlockEntity(master) instanceof CardCatalogBlockEntity c)) {
            return;
        }
        if (!c.getBlockState().getValue(CardCatalogBlock.FORMED)) {
            tooltip.add(Component.translatable("jade.golem_logix.catalog.not_formed").withStyle(ChatFormatting.GOLD));
            return;
        }
        tooltip.add(Component.translatable("jade.golem_logix.catalog.cards", c.clientCards(), c.clientCapacity())
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
