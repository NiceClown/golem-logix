package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.BusMenu;
import com.golemlogix.menu.GolemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: a bus filter slot window (FilterSlotScreen) cleared the slot (a negative amount), or, on the Export Bus, set how
 * much the container in front should hold of its item (or fluid, in mB; 0: no limit).
 */
public record FilterSlotPayload(int containerId, int slot, int amount) implements CustomPacketPayload {
    public static final Type<FilterSlotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "filter_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterSlotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.slot);
                buf.writeInt(p.amount);
            },
            buf -> new FilterSlotPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FilterSlotPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof GolemMenu menu
                    && menu.containerId == p.containerId && menu.isGhost(p.slot)) {
                if (p.amount < 0) {
                    menu.setGhost(player, p.slot, net.minecraft.world.item.ItemStack.EMPTY);
                } else if (menu instanceof BusMenu bus) {
                    bus.setKeep(p.slot, p.amount);
                } else if (menu instanceof com.golemlogix.menu.StockRequesterMenu req) {
                    req.setKeep(p.slot, p.amount);
                } else if (menu instanceof com.golemlogix.menu.StockManometerMenu mano) {
                    mano.setKeep(p.slot, p.amount);
                }
                menu.broadcastChanges();
            }
        });
    }
}
