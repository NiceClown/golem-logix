package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.StockManometerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ManometerConfigPayload(int containerId, int mode, int threshold, int hysteresis) implements CustomPacketPayload {
    public static final Type<ManometerConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "manometer_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ManometerConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.mode);
                buf.writeInt(p.threshold);
                buf.writeInt(p.hysteresis);
            },
            buf -> new ManometerConfigPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ManometerConfigPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof StockManometerMenu menu
                    && menu.containerId == p.containerId) {
                menu.setConfig(p.mode, p.threshold, p.hysteresis);
            }
        });
    }
}
