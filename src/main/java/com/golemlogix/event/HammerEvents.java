package com.golemlogix.event;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.items.ForgeHammerItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Server: a left-click with the Forge Hammer on the anvil does not start breaking it (the strike comes in a separate AnvilStrikePayload
 * packet sent by the client, see client.HammerClientEvents).
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
