package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.ConcentratorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
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
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "concentrator_layout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConcentratorLayoutPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.rows.size());
                for (ConcentratorMenu.RowInfo r : p.rows) {
                    buf.writeBoolean(r.header());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.icon());
                    ComponentSerialization.STREAM_CODEC.encode(buf, r.title());
                    buf.writeByte(r.count());
                }
            },
            buf -> {
                int id = buf.readVarInt();
                int n = buf.readVarInt();
                List<ConcentratorMenu.RowInfo> rows = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    boolean header = buf.readBoolean();
                    ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
                    rows.add(new ConcentratorMenu.RowInfo(header, icon, title, buf.readByte()));
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
