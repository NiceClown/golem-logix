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
 * Client -> server: a player action in the terminal.
 */
public record TerminalActionPayload(int containerId, Action action, ItemStack key, FluidStack fluid) implements CustomPacketPayload {
    public static final Type<TerminalActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "terminal_action"));

    public enum Action {
        /** Left-click on an item: take a stack. */
        TAKE_STACK,
        /** Right-click on an item: take half a stack. */
        TAKE_HALF,
        /** Middle click: take one item. */
        TAKE_ONE,
        /** Shift + left-click: the stack straight into the inventory. */
        TO_INVENTORY,
        /** Left-click on the list with an item on the cursor: put all of it. */
        INSERT_ALL,
        /** Right-click on the list with an item on the cursor: put one. */
        INSERT_ONE,
        /** With a container on the cursor on a fluid: fill it. */
        FLUID_FILL_CARRIED,
        /** With a full container on the cursor: drain it into the network. */
        FLUID_DRAIN_CARRIED,
        /** Put the items from the crafting grid back into the network (a button). */
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
