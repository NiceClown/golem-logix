package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервер -> клиент: заказ автокрафта готов (уведомление-«тост», как в AE2). Количество — в count стаков (может быть больше 99).
 */
public record CraftDonePayload(List<ItemStack> items) implements CustomPacketPayload {
    public static final Type<CraftDonePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "craft_done"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftDonePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.items.size());
                for (ItemStack s : p.items) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.copyWithCount(1));
                    buf.writeVarInt(s.getCount());
                }
            },
            buf -> {
                int n = Math.min(8, buf.readVarInt());
                List<ItemStack> items = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    ItemStack s = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    int count = buf.readVarInt();
                    items.add(s.copyWithCount(Math.max(1, count)));
                }
                return new CraftDonePayload(items);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftDonePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.golemlogix.client.ClientHooks.craftDone(payload.items()));
    }
}
