package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.GolemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: a tag picked from the slot window's list for a fuzzy ghost slot ("": none, any components). */
public record FuzzyTagPayload(int containerId, int slot, String tag) implements CustomPacketPayload {
    public static final Type<FuzzyTagPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "fuzzy_tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FuzzyTagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FuzzyTagPayload::containerId,
            ByteBufCodecs.VAR_INT, FuzzyTagPayload::slot,
            ByteBufCodecs.stringUtf8(256), FuzzyTagPayload::tag,
            FuzzyTagPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FuzzyTagPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof GolemMenu menu
                    && menu.containerId == p.containerId() && menu.stillValid(player)) {
                menu.setFuzzyTag(p.slot(), p.tag());
                menu.broadcastChanges();
            }
        });
    }
}
