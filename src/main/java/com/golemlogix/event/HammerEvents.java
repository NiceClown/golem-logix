package com.golemlogix.event;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.items.ForgeHammerItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Сервер: ЛКМ кузнечным молотом по наковальне не начинает её ломать (удар приходит отдельным пакетом AnvilStrikePayload,
 * его шлёт клиент — см. client.HammerClientEvents).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class HammerEvents {
    private HammerEvents() {
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity().getMainHandItem().getItem() instanceof ForgeHammerItem
                && ForgeHammerItem.isAnvil(event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }
}
