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
 * Client: left-click with the Forge Hammer on the anvil is a strike, not breaking. While the button is held the event comes every tick:
 * strikes go no faster than the hammer cooldown (the server checks it too).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
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
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new AnvilStrikePayload(event.getPos()));
    }
}
