package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.ConcentratorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: layout of the Cryo Schematic Concentrator window rows (after a search): category headers and slot rows
 * (icon, label, how many slots in the row). The punch cards themselves come with the usual menu slot sync.
 */
public record ConcentratorLayoutPayload(int containerId, List<ConcentratorMenu.RowInfo> rows) implements CustomPacketPayload {
    public static final Type<ConcentratorLayoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "concentrator_layout"));

    public static final int MAX_ROWS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, ConcentratorLayoutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                int n = Math.min(MAX_ROWS, p.rows.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    ConcentratorMenu.RowInfo r = p.rows.get(i);
                    buf.writeBoolean(r.header());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.icon());
                    ComponentSerialization.STREAM_CODEC.encode(buf, r.title());
                    buf.writeByte(r.count());
                }
            },
            buf -> {
                int id = buf.readVarInt();
                int n = PayloadCodecs.readCount(buf, MAX_ROWS);
                List<ConcentratorMenu.RowInfo> rows = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    boolean header = buf.readBoolean();
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
                    int count = Math.clamp(buf.readByte(), 0, ConcentratorMenu.COLS);
                    rows.add(new ConcentratorMenu.RowInfo(header, icon, title, count));
                }
                return new ConcentratorLayoutPayload(id, rows);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConcentratorLayoutPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof ConcentratorMenu menu && menu.containerId == p.containerId) {
                menu.setClientRows(p.rows);
            }
        });
    }
}
