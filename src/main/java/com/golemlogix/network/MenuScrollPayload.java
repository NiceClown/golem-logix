package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.ScrollableMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент -> сервер: окно с прокручиваемой сеткой ячеек (картотека, концентратор) прокрутили до строки row или изменили
 * поиск. Ячейки меню на сервере сдвигаются, и обычная синхронизация меню присылает новые предметы.
 */
public record MenuScrollPayload(int containerId, int row, String filter) implements CustomPacketPayload {
    public static final Type<MenuScrollPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "menu_scroll"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuScrollPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.row);
                buf.writeUtf(p.filter, 64);
            },
            buf -> new MenuScrollPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MenuScrollPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof ScrollableMenu menu
                    && player.containerMenu.containerId == p.containerId) {
                menu.view(p.row, p.filter);
                player.containerMenu.broadcastChanges();
            }
        });
    }
}
