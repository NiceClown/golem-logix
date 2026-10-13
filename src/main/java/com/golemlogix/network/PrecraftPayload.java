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

import java.util.ArrayList;
import java.util.List;

/**
 * Клиент -> сервер: прекрафт (Ctrl + «+» в JEI) — заказать автокрафтом недостающие ингредиенты рецепта.
 * Количество — отдельным числом (может быть больше стака).
 */
public record PrecraftPayload(int containerId, List<ItemStack> items, List<Integer> counts) implements CustomPacketPayload {
    public static final int MAX_ITEMS = 9;

    public static final Type<PrecraftPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "precraft"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrecraftPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                int n = Math.min(MAX_ITEMS, Math.min(p.items.size(), p.counts.size()));
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.items.get(i).copyWithCount(1));
                    buf.writeVarInt(p.counts.get(i));
                }
            },
            buf -> {
                int id = buf.readVarInt();
                int n = Math.min(MAX_ITEMS, buf.readVarInt());
                List<ItemStack> items = new ArrayList<>(n);
                List<Integer> counts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    counts.add(buf.readVarInt());
                }
                return new PrecraftPayload(id, items, counts);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PrecraftPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId) {
                menu.handlePrecraft(player, payload.items(), payload.counts());
            }
        });
    }
}
