package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server: a recipe from JEI (the "+" button).
 * In the crafting terminal it is laid out on the grid: inputs[i] = the options for cell i (the first one found in the network or inventory is taken).
 * In the encoder it is written into the ghost slots: inputs[i] = one item (the first option), outputs = processing outputs.
 *
 * @param crafting a 3x3 crafting table recipe (otherwise processing in a machine, encoder only)
 * @param max      Shift + "+": in the crafting terminal put full stacks
 */
public record RecipeFillPayload(int containerId, boolean crafting, boolean max, List<List<ItemStack>> inputs,
                                List<ItemStack> outputs) implements CustomPacketPayload {
    public static final Type<RecipeFillPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "recipe_fill"));

    /** Limits against bloated packets. */
    public static final int MAX_SLOTS = 9, MAX_OPTIONS = 16, MAX_OUTPUTS = 3;

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeFillPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeBoolean(p.crafting);
                buf.writeBoolean(p.max);
                buf.writeVarInt(Math.min(MAX_SLOTS, p.inputs.size()));
                for (int i = 0; i < Math.min(MAX_SLOTS, p.inputs.size()); i++) {
                    List<ItemStack> opts = p.inputs.get(i);
                    buf.writeVarInt(Math.min(MAX_OPTIONS, opts.size()));
                    for (int k = 0; k < Math.min(MAX_OPTIONS, opts.size()); k++) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, opts.get(k));
                    }
                }
                buf.writeVarInt(Math.min(MAX_OUTPUTS, p.outputs.size()));
                for (int i = 0; i < Math.min(MAX_OUTPUTS, p.outputs.size()); i++) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.outputs.get(i));
                }
            },
            buf -> {
                int id = buf.readVarInt();
                boolean crafting = buf.readBoolean();
                boolean max = buf.readBoolean();
                int n = Math.min(MAX_SLOTS, buf.readVarInt());
                List<List<ItemStack>> inputs = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int m = Math.min(MAX_OPTIONS, buf.readVarInt());
                    List<ItemStack> opts = new ArrayList<>(m);
                    for (int k = 0; k < m; k++) {
                        opts.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                    }
                    inputs.add(opts);
                }
                int o = Math.min(MAX_OUTPUTS, buf.readVarInt());
                List<ItemStack> outputs = new ArrayList<>(o);
                for (int i = 0; i < o; i++) {
                    outputs.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                return new RecipeFillPayload(id, crafting, max, inputs, outputs);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RecipeFillPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.containerMenu.containerId != payload.containerId) {
                return;
            }
            if (player.containerMenu instanceof TerminalMenu menu) {
                menu.fillGrid(player, payload.inputs, payload.max);
            } else if (player.containerMenu instanceof PatternEncoderMenu menu) {
                menu.fillFromRecipe(payload.crafting, payload.inputs, payload.outputs);
            }
        });
    }
}
