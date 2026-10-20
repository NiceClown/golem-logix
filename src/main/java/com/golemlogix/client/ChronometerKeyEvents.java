package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.client.ghost.GolemKeys;
import com.golemlogix.network.OpenChronometerPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** The "open Portable Chronometer" key: the server finds the Chronometer (hands, Curios slot, inventory) and opens its terminal. */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public final class ChronometerKeyEvents {
    private ChronometerKeyEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (GolemKeys.OPEN_CHRONOMETER.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new OpenChronometerPayload());
            }
        }
    }
}
