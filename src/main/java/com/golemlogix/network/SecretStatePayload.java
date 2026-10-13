package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер -> клиент: какие испытания секретного рецепта пройдены (для книги и JEI).
 */
public record SecretStatePayload(boolean wither, boolean dimension) implements CustomPacketPayload {
    public static final Type<SecretStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "secret_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SecretStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SecretStatePayload::wither,
            ByteBufCodecs.BOOL, SecretStatePayload::dimension,
            SecretStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecretStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.golemlogix.client.ClientSecrets.set(payload.wither(), payload.dimension()));
    }
}
