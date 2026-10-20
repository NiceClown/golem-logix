package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.items.Chronometers;
import com.golemlogix.items.PortableChronometerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the "open Portable Chronometer" key (Ctrl+E by default) was pressed. */
public record OpenChronometerPayload() implements CustomPacketPayload {
    public static final Type<OpenChronometerPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "open_chronometer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChronometerPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenChronometerPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenChronometerPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.isSpectator()) {
                return;
            }
            // a hand, the Curios slot, then anywhere in the inventory
            Chronometers.Found found = Chronometers.find(player);
            if (found == null) {
                player.sendOverlayMessage(Component.translatable("message.golem_logix.chronometer.none")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            PortableChronometerItem.open(player, found.stack(), found.slot());
        });
    }
}
