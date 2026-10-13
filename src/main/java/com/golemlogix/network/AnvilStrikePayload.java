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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент -> сервер: игрок ударил кузнечным молотом (ЛКМ) по наковальне в pos. */
public record AnvilStrikePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AnvilStrikePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "anvil_strike"));

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
                    || !ForgeHammerItem.isAnvil(player.serverLevel().getBlockState(p.pos))
                    || !(player.serverLevel().getBlockEntity(p.pos) instanceof ManaSaturatorBlockEntity anvil)) {
                return;
            }
            strike(player, anvil, hammer);
        });
    }

    /** Удар и сообщение игроку над хотбаром. */
    public static void strike(ServerPlayer player, ManaSaturatorBlockEntity anvil, ItemStack hammer) {
        switch (anvil.strike(player.serverLevel(), anvil.getBlockPos(), player, hammer)) {
            case HIT -> player.displayClientMessage(Component.translatable("message.golem_logix.anvil.hit",
                    anvil.manualHits(), ManaSaturatorBlockEntity.MANUAL_HITS), true);
            case DONE -> player.displayClientMessage(Component.translatable("message.golem_logix.anvil.done",
                    anvil.anvilItem().getCount(), anvil.anvilItem().getHoverName()), true);
            case NO_SOURCE -> player.displayClientMessage(Component.translatable("message.golem_logix.anvil.no_source",
                    ForgeHammerItem.STRIKE_COST).withStyle(ChatFormatting.RED), true);
            case NOTHING -> player.displayClientMessage(Component.translatable("message.golem_logix.anvil.empty"), true);
            default -> {
            }
        }
    }
}
