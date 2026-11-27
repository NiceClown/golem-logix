package com.golemlogix.integration.jade;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blocks.DistillationTowerBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/** Lines over a Cryo-Distillation Tower (any of its 3 sections): blood buffer, distillation output, and status. */
public enum DistillationTowerProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "distillation_tower");
    private static final String TAG = "golem_logix_distillation_tower";

    public enum Server implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            DistillationTowerBlockEntity tower = DistillationTowerBlock.entityOf(
                    accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
            if (tower == null) {
                return;
            }
            CompoundTag t = new CompoundTag();
            t.putInt("blood", tower.blood().getAmount());
            t.putInt("cap", tower.capacity());
            t.putInt("rate", tower.rate());
            t.putInt("sourceTenths", (int) Math.round(tower.sourcePerSecond() * 10.0));
            t.putInt("status", tower.status());
            data.put(TAG, t);
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(TAG)) {
            return;
        }
        CompoundTag t = accessor.getServerData().getCompoundOrEmpty(TAG);
        int blood = t.getIntOr("blood", 0);
        int cap = t.getIntOr("cap", DistillationTowerBlockEntity.TANK);
        int rate = t.getIntOr("rate", 0);
        double sourcePerSec = t.getIntOr("sourceTenths", 0) / 10.0;
        int status = t.getIntOr("status", 0);

        tooltip.add(Component.translatable("jade.golem_logix.tower.blood", blood, cap).withStyle(ChatFormatting.RED));
        String sourceStr = String.format(Locale.ROOT,
                sourcePerSec == Math.rint(sourcePerSec) ? "%.0f" : "%.1f", sourcePerSec);
        tooltip.add(Component.translatable("jade.golem_logix.tower.rate", sourceStr, rate).withStyle(ChatFormatting.AQUA));
        ChatFormatting statusColor = status == 1 ? ChatFormatting.GREEN : status == 2 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
        tooltip.add(Component.translatable("gui.golem_logix.tower.status." + status).withStyle(statusColor));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
