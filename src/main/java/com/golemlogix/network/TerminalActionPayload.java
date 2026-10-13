package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.TerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент -> сервер: действие игрока в терминале.
 */
public record TerminalActionPayload(int containerId, Action action, ItemStack key, FluidStack fluid) implements CustomPacketPayload {
    public static final Type<TerminalActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "terminal_action"));

    public enum Action {
        /** ЛКМ по предмету: взять стак. */
        TAKE_STACK,
        /** ПКМ по предмету: взять половину стака. */
        TAKE_HALF,
        /** СКМ: взять один предмет. */
        TAKE_ONE,
        /** Shift+ЛКМ: стак сразу в инвентарь. */
        TO_INVENTORY,
        /** ЛКМ с предметом в курсоре по списку: положить всё. */
        INSERT_ALL,
        /** ПКМ с предметом в курсоре по списку: положить один. */
        INSERT_ONE,
        /** С ёмкостью в курсоре по жидкости: наполнить. */
        FLUID_FILL_CARRIED,
        /** С полной ёмкостью в курсоре: слить в сеть. */
        FLUID_DRAIN_CARRIED,
        /** Положить предмет из сетки крафта обратно в сеть (кнопка). */
        CLEAR_GRID
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeEnum(p.action);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.key);
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, p.fluid);
            },
            buf -> new TerminalActionPayload(buf.readVarInt(), buf.readEnum(Action.class),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), FluidStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerminalActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof TerminalMenu menu && menu.containerId == payload.containerId) {
                menu.handleAction(player, payload);
            }
        });
    }
}
