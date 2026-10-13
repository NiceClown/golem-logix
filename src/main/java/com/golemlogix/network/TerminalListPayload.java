package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: part of the network item list (the list is cut into pieces so a packet does not grow too big).
 */
public record TerminalListPayload(int containerId, boolean reset, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<TerminalListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "terminal_list"));

    public static final int CHUNK = 1200;

    public record Entry(ItemStack key, long count, boolean craftable) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeBoolean(p.reset);
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) {
                    ItemStack.STREAM_CODEC.encode(buf, e.key);
                    buf.writeVarLong(e.count);
                    buf.writeBoolean(e.craftable);
                }
            },
            buf -> {
                int id = buf.readVarInt();
                boolean reset = buf.readBoolean();
                int n = buf.readVarInt();
                List<Entry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    ItemStack key = ItemStack.STREAM_CODEC.decode(buf);
                    long count = buf.readVarLong();
                    boolean craftable = buf.readBoolean();
                    list.add(new Entry(key, count, craftable));
                }
                return new TerminalListPayload(id, reset, list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalListPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId) {
                menu.clientReceiveList(payload);
            }
        });
    }
}
