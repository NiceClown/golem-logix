package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент -> сервер: заказ автокрафта предмета.
 *
 * @param preview true — только показать план (без запуска)
 * @param request номер запроса окна: сервер возвращает его в CraftPlanPayload
 */
public record CraftRequestPayload(int containerId, ItemStack key, int amount, boolean preview, int request) implements CustomPacketPayload {
    public static final Type<CraftRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "craft_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.key);
                buf.writeVarInt(p.amount);
                buf.writeBoolean(p.preview);
                buf.writeVarInt(p.request);
            },
            buf -> new CraftRequestPayload(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId) {
                menu.handleCraftRequest(player, payload);
            }
        });
    }
}
