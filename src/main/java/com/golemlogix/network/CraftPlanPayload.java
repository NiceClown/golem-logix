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
 * Сервер -> клиент: план заказа автокрафта для планировщика в окне заказа (как экран подтверждения крафта AE2): что
 * возьмётся из сети, что будет создано, чего не хватает, и чем кончился запуск.
 *
 * @param request номер запроса окна (ответ на устаревший запрос окно пропускает)
 */
public record CraftPlanPayload(int containerId, int request, Status status, int steps, long executions,
                               List<Entry> entries) implements CustomPacketPayload {
    public static final Type<CraftPlanPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "craft_plan"));
    /** Больше строк план не шлёт (в окне — прокрутка; остальное в огромных заказах не важно). */
    public static final int MAX_ENTRIES = 256;

    /** Итог: план готов / не хватает ресурсов / заказ запущен / причина, по которой запуск не удался. */
    public enum Status {
        OK, MISSING, STARTED, NO_CPU, RESERVE_FAILED, OFFLINE, NO_PATTERN;

        public String key() {
            return "gui.golem_logix.plan.status." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Строка плана: предмет, сколько взять из сети, сколько создать, сколько не хватает. */
    public record Entry(ItemStack stack, long stored, long craft, long missing) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.request);
                buf.writeVarInt(p.status.ordinal());
                buf.writeVarInt(p.steps);
                buf.writeVarLong(p.executions);
                buf.writeVarInt(p.entries.size());
                for (Entry e : p.entries) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, e.stack);
                    buf.writeVarLong(e.stored);
                    buf.writeVarLong(e.craft);
                    buf.writeVarLong(e.missing);
                }
            },
            buf -> {
                int id = buf.readVarInt();
                int request = buf.readVarInt();
                Status status = Status.values()[Math.min(Status.values().length - 1, buf.readVarInt())];
                int steps = buf.readVarInt();
                long executions = buf.readVarLong();
                int n = Math.min(buf.readVarInt(), MAX_ENTRIES);
                List<Entry> entries = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    entries.add(new Entry(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarLong()));
                }
                return new CraftPlanPayload(id, request, status, steps, executions, entries);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CraftPlanPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId) {
                menu.clientReceivePlan(payload);
            }
        });
    }
}
