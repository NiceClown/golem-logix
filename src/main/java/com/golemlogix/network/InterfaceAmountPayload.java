package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.InterfaceMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: the Interface slot settings window set an amount (items or mB). A negative one clears the slot.
 */
public record InterfaceAmountPayload(int containerId, int slot, int amount) implements CustomPacketPayload {
    public static final Type<InterfaceAmountPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "interface_amount"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InterfaceAmountPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.slot);
                buf.writeInt(p.amount);
            },
            buf -> new InterfaceAmountPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InterfaceAmountPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof InterfaceMenu menu
                    && menu.containerId == p.containerId) {
                if (p.amount < 0) {
                    menu.clearSlot(p.slot);
                } else {
                    menu.setAmount(p.slot, p.amount);
                }
                menu.broadcastChanges();
            }
        });
    }
}
