package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.GolemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: a middle-click (or Ctrl+click) on a fuzzy ghost slot: its next tag. */
public record FuzzyCyclePayload(int containerId, int slot) implements CustomPacketPayload {
    public static final Type<FuzzyCyclePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "fuzzy_cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuzzyCyclePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FuzzyCyclePayload::containerId,
            ByteBufCodecs.VAR_INT, FuzzyCyclePayload::slot,
            FuzzyCyclePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FuzzyCyclePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof GolemMenu menu
                    && menu.containerId == p.containerId() && menu.stillValid(player)) {
                menu.cycleFuzzy(p.slot());
                menu.broadcastChanges();
            }
        });
    }
}
