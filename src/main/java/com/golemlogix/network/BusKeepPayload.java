package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.BusMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: the Export Bus filter slot window set how much the container in front should hold of that slot's item (or
 * fluid, in mB); 0 exports without a limit, a negative amount clears the slot.
 */
public record BusKeepPayload(int containerId, int slot, int amount) implements CustomPacketPayload {
    public static final Type<BusKeepPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "bus_keep"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BusKeepPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.slot);
                buf.writeInt(p.amount);
            },
            buf -> new BusKeepPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BusKeepPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof BusMenu menu
                    && menu.containerId == p.containerId) {
                menu.setKeep(p.slot, p.amount);
                menu.broadcastChanges();
            }
        });
    }
}
