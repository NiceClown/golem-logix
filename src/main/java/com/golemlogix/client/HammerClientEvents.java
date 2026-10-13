package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.items.ForgeHammerItem;
import com.golemlogix.network.AnvilStrikePayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиент: ЛКМ кузнечным молотом по наковальне — удар, а не ломание. Пока кнопка зажата, событие приходит каждый тик:
 * удары идут не чаще перезарядки молота (её же проверяет сервер).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HammerClientEvents {
    private static long lastStrike = -1000;

    private HammerClientEvents() {
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity().getMainHandItem().getItem() instanceof ForgeHammerItem)
                || !ForgeHammerItem.isAnvil(event.getLevel().getBlockState(event.getPos()))) {
            return;
        }
        event.setCanceled(true);
        long now = event.getLevel().getGameTime();
        if (now >= lastStrike && now - lastStrike < ManaSaturatorBlockEntity.MANUAL_COOLDOWN) {
            return;
        }
        lastStrike = now;
        event.getEntity().swing(InteractionHand.MAIN_HAND);
        PacketDistributor.sendToServer(new AnvilStrikePayload(event.getPos()));
    }
}
