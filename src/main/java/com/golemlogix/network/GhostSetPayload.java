package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.GolemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент -> сервер: предмет, перетащенный из JEI в призрачную ячейку окна (фильтр шины, настройка интерфейса, сетка
 * кодировщика). Сам предмет у игрока не нужен — ячейка хранит только «призрак».
 */
public record GhostSetPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
    public static final Type<GhostSetPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "ghost_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSetPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.slot);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stack);
            },
            buf -> new GhostSetPayload(buf.readVarInt(), buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GhostSetPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof GolemMenu menu
                    && menu.containerId == p.containerId && p.slot >= 0 && p.slot < menu.slots.size()) {
                menu.setGhost(player, p.slot, p.stack);
                menu.broadcastChanges();
            }
        });
    }
}
