package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.InterfaceMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент -> сервер: окно настройки ячейки Интерфейса задало количество (штуки или mB). Отрицательное — очистить ячейку.
 */
public record InterfaceAmountPayload(int containerId, int slot, int amount) implements CustomPacketPayload {
    public static final Type<InterfaceAmountPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "interface_amount"));

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
