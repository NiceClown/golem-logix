package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.PatternEncoderMenu;
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
 * Клиент -> сервер: рецепт из JEI (кнопка «+»).
 * В терминале крафта — разложить по сетке: inputs[i] — варианты для клетки i (берётся первый, который есть в сети или инвентаре).
 * В кодировщике — записать в призрачные слоты: inputs[i] — один предмет (первый вариант), outputs — выходы обработки.
 *
 * @param crafting рецепт верстака 3x3 (иначе — обработка в машине, только для кодировщика)
 * @param max      Shift+«+»: в терминале крафта класть по полному стаку
 */
public record RecipeFillPayload(int containerId, boolean crafting, boolean max, List<List<ItemStack>> inputs,
                                List<ItemStack> outputs) implements CustomPacketPayload {
    public static final Type<RecipeFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "recipe_fill"));

    /** Ограничения против раздутых пакетов. */
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
