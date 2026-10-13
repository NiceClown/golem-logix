package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: a network summary for the terminal (power, channels, capacity, processors, fluids), and for a Chronometer its
 * Source charge (-1 = no charge needed) and whether the Resonator powers it.
 */
public record TerminalInfoPayload(int containerId, String error, int channelsUsed, int channelCapacity,
                                  long itemsUsed, long itemsCapacity, long fluidsUsed, long fluidsCapacity,
                                  int cpuBusy, int cpuTotal, List<FluidStack> fluids, int charge,
                                  boolean resonator) implements CustomPacketPayload {
    public static final Type<TerminalInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "terminal_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalInfoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeUtf(p.error);
                buf.writeVarInt(p.channelsUsed);
                buf.writeVarInt(p.channelCapacity);
                buf.writeVarLong(p.itemsUsed);
                buf.writeVarLong(p.itemsCapacity);
                buf.writeVarLong(p.fluidsUsed);
                buf.writeVarLong(p.fluidsCapacity);
                buf.writeVarInt(p.cpuBusy);
                buf.writeVarInt(p.cpuTotal);
                buf.writeVarInt(p.fluids.size());
                for (FluidStack f : p.fluids) {
                    FluidStack.STREAM_CODEC.encode(buf, f);
                }
                buf.writeInt(p.charge);
                buf.writeBoolean(p.resonator);
            },
            buf -> {
                int id = buf.readVarInt();
                String error = buf.readUtf();
                int cu = buf.readVarInt();
                int cc = buf.readVarInt();
                long iu = buf.readVarLong();
                long ic = buf.readVarLong();
                long fu = buf.readVarLong();
                long fc = buf.readVarLong();
                int busy = buf.readVarInt();
                int total = buf.readVarInt();
                int n = buf.readVarInt();
                List<FluidStack> fluids = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    fluids.add(FluidStack.STREAM_CODEC.decode(buf));
                }
                int charge = buf.readInt();
                boolean resonator = buf.readBoolean();
                return new TerminalInfoPayload(id, error, cu, cc, iu, ic, fu, fc, busy, total, fluids, charge, resonator);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalInfoPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId) {
                menu.clientReceiveInfo(payload);
            }
        });
    }
}
