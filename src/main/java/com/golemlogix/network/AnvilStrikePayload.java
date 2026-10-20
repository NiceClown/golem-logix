package com.golemlogix.network;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.items.ForgeHammerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the player struck the anvil at pos with the Forge Hammer (left-click). */
public record AnvilStrikePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AnvilStrikePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "anvil_strike"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnvilStrikePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBlockPos(p.pos),
            buf -> new AnvilStrikePayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnvilStrikePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack hammer = player.getMainHandItem();
            if (!(hammer.getItem() instanceof ForgeHammerItem) || player.distanceToSqr(p.pos.getCenter()) > 36
                    || !ForgeHammerItem.isAnvil(player.level().getBlockState(p.pos))
                    || !(player.level().getBlockEntity(p.pos) instanceof ManaSaturatorBlockEntity anvil)) {
                return;
            }
            strike(player, anvil, hammer);
        });
    }

    /** The strike and a message to the player above the hotbar. */
    public static void strike(ServerPlayer player, ManaSaturatorBlockEntity anvil, ItemStack hammer) {
        switch (anvil.strike(player.level(), anvil.getBlockPos(), player, hammer)) {
            case HIT -> player.sendOverlayMessage(Component.translatable("message.golem_logix.anvil.hit",
                    anvil.manualHits(), ManaSaturatorBlockEntity.MANUAL_HITS));
            case DONE -> player.sendOverlayMessage(Component.translatable("message.golem_logix.anvil.done",
                    anvil.anvilItem().getCount(), anvil.anvilItem().getHoverName()));
            case NO_SOURCE -> player.sendOverlayMessage(Component.translatable("message.golem_logix.anvil.no_source",
                    ForgeHammerItem.STRIKE_COST).withStyle(ChatFormatting.RED));
            case NOTHING -> player.sendOverlayMessage(Component.translatable("message.golem_logix.anvil.empty"));
            default -> {
            }
        }
    }
}
