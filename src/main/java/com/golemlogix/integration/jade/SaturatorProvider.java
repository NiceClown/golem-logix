package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.system.EndRodRitual;
import com.golemlogix.system.SaturatorStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/** Lines over a Mana Saturator (any of its parts): coprocessors on the roof, speed, Redstone mode, or active End Rod ritual. */
public enum SaturatorProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "saturator");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockPos master = accessor.getBlock() instanceof SaturatorPartBlock
                ? SaturatorPartBlock.controllerOf(accessor.getPosition(), accessor.getBlockState()) : accessor.getPosition();
        if (master == null || !accessor.getLevel().isLoaded(master)
                || !(accessor.getLevel().getBlockEntity(master) instanceof ManaSaturatorBlockEntity sat)) {
            return;
        }
        if (!sat.getBlockState().getValue(ManaSaturatorBlock.FORMED)) {
            int stage = sat.rodStage();
            if (stage == EndRodRitual.NONE) {
                return;
            }
            tooltip.add(Component.translatable("jade.golem_logix.saturator.rod_title")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(sat.rodStatusPrompt().withStyle(
                    EndRodRitual.isHammerStage(stage) ? ChatFormatting.AQUA : ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("jade.golem_logix.saturator.rod_progress",
                            sat.rodTotalHits(), EndRodRitual.TOTAL_HITS)
                    .withStyle(ChatFormatting.GRAY));
            if (EndRodRitual.isHammerStage(stage)
                    && !ManaSaturatorBlockEntity.hasRequiredRodIce(accessor.getLevel(), master, stage)) {
                tooltip.add(Component.translatable("message.golem_logix.rod_ritual.need_ice."
                                + EndRodRitual.requiredIceTier(stage))
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        float speed = sat.speed() / (float) ManaSaturatorBlockEntity.BASE_SPEED;
        tooltip.add(Component.translatable("jade.golem_logix.saturator.coolers", sat.getCoolers(), SaturatorStructure.VENTS.length,
                String.format(Locale.ROOT, "%.1f", speed)).withStyle(ChatFormatting.AQUA));
        if (sat.hasRedstoneModule()) {
            tooltip.add(Component.translatable("jade.golem_logix.saturator.redstone", Component.translatable(sat.redstoneMode().key()))
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
