package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.CalculatorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: job state of a Cryo-Calculator.
 *
 * @param state -1 = idle, otherwise the ordinal of CraftingJob.Status
 */
public record CalculatorStatusPayload(int containerId, int state, long done, long total, int coprocessors, int workers,
                                      long awaiting, boolean online, ItemStack target, long amount,
                                      List<ItemStack> contents) implements CustomPacketPayload {
    public static final Type<CalculatorStatusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "calculator_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CalculatorStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.state);
                buf.writeVarLong(p.done);
                buf.writeVarLong(p.total);
                buf.writeVarInt(p.coprocessors);
                buf.writeVarInt(p.workers);
                buf.writeVarLong(p.awaiting);
                buf.writeBoolean(p.online);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.target);
                buf.writeVarLong(p.amount);
                buf.writeVarInt(p.contents.size());
                for (ItemStack s : p.contents) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
                }
            },
            buf -> {
                int id = buf.readVarInt();
                int state = buf.readVarInt();
                long done = buf.readVarLong();
                long total = buf.readVarLong();
                int cop = buf.readVarInt();
                int workers = buf.readVarInt();
                long awaiting = buf.readVarLong();
                boolean online = buf.readBoolean();
                ItemStack target = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                long amount = buf.readVarLong();
                int n = buf.readVarInt();
                List<ItemStack> contents = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    contents.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                return new CalculatorStatusPayload(id, state, done, total, cop, workers, awaiting, online, target, amount, contents);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CalculatorStatusPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof CalculatorMenu menu && menu.containerId == payload.containerId) {
                menu.clientStatus = payload;
            }
        });
    }
}
