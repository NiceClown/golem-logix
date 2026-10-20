package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: which secret recipe trials are passed and whether the Dispel method is known (for the book and JEI).
 */
public record SecretStatePayload(boolean wither, boolean dimension, boolean mind) implements CustomPacketPayload {
    public static final Type<SecretStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "secret_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SecretStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SecretStatePayload::wither,
            ByteBufCodecs.BOOL, SecretStatePayload::dimension,
            ByteBufCodecs.BOOL, SecretStatePayload::mind,
            SecretStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecretStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.golemlogix.client.ClientSecrets.set(payload.wither(), payload.dimension(), payload.mind()));
    }
}
