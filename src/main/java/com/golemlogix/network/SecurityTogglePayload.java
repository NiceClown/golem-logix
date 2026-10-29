package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.menu.SecurityTerminalMenu;
import com.golemlogix.security.SecurityPermission;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SecurityTogglePayload(int containerId, int slot, int permMask) implements CustomPacketPayload {
    public static final Type<SecurityTogglePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "security_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SecurityTogglePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.slot);
                buf.writeVarInt(p.permMask);
            },
            buf -> new SecurityTogglePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecurityTogglePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.containerMenu instanceof SecurityTerminalMenu menu
                    && menu.containerId == p.containerId) {
                menu.togglePermission(player, p.slot, p.permMask);
            }
        });
    }
}
