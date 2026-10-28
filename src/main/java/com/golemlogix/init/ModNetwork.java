package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.network.CalculatorStatusPayload;
import com.golemlogix.network.CraftDonePayload;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.network.MonitorPayload;
import com.golemlogix.network.PrecraftPayload;
import com.golemlogix.network.RecipeFillPayload;
import com.golemlogix.network.SecretStatePayload;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public class ModNetwork {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("5");
        registrar.playToClient(TerminalListPayload.TYPE, TerminalListPayload.STREAM_CODEC, TerminalListPayload::handle);
        registrar.playToClient(TerminalInfoPayload.TYPE, TerminalInfoPayload.STREAM_CODEC, TerminalInfoPayload::handle);
        registrar.playToClient(CalculatorStatusPayload.TYPE, CalculatorStatusPayload.STREAM_CODEC, CalculatorStatusPayload::handle);
        registrar.playToClient(CraftDonePayload.TYPE, CraftDonePayload.STREAM_CODEC, CraftDonePayload::handle);
        registrar.playToClient(MonitorPayload.TYPE, MonitorPayload.STREAM_CODEC, MonitorPayload::handle);
        registrar.playToClient(SecretStatePayload.TYPE, SecretStatePayload.STREAM_CODEC, SecretStatePayload::handle);
        registrar.playToClient(com.golemlogix.network.CraftPlanPayload.TYPE, com.golemlogix.network.CraftPlanPayload.STREAM_CODEC,
                com.golemlogix.network.CraftPlanPayload::handle);
        registrar.playToClient(com.golemlogix.network.ConcentratorLayoutPayload.TYPE, com.golemlogix.network.ConcentratorLayoutPayload.STREAM_CODEC,
                com.golemlogix.network.ConcentratorLayoutPayload::handle);
        registrar.playToServer(TerminalActionPayload.TYPE, TerminalActionPayload.STREAM_CODEC, TerminalActionPayload::handle);
        registrar.playToServer(CraftRequestPayload.TYPE, CraftRequestPayload.STREAM_CODEC, CraftRequestPayload::handle);
        registrar.playToServer(RecipeFillPayload.TYPE, RecipeFillPayload.STREAM_CODEC, RecipeFillPayload::handle);
        registrar.playToServer(PrecraftPayload.TYPE, PrecraftPayload.STREAM_CODEC, PrecraftPayload::handle);
        registrar.playToServer(com.golemlogix.network.GhostSetPayload.TYPE, com.golemlogix.network.GhostSetPayload.STREAM_CODEC,
                com.golemlogix.network.GhostSetPayload::handle);
        registrar.playToServer(com.golemlogix.network.InterfaceAmountPayload.TYPE, com.golemlogix.network.InterfaceAmountPayload.STREAM_CODEC,
                com.golemlogix.network.InterfaceAmountPayload::handle);
        registrar.playToServer(com.golemlogix.network.BusKeepPayload.TYPE, com.golemlogix.network.BusKeepPayload.STREAM_CODEC,
                com.golemlogix.network.BusKeepPayload::handle);
        registrar.playToServer(com.golemlogix.network.AnvilStrikePayload.TYPE, com.golemlogix.network.AnvilStrikePayload.STREAM_CODEC,
                com.golemlogix.network.AnvilStrikePayload::handle);
        registrar.playToServer(com.golemlogix.network.OpenChronometerPayload.TYPE, com.golemlogix.network.OpenChronometerPayload.STREAM_CODEC,
                com.golemlogix.network.OpenChronometerPayload::handle);
        registrar.playToServer(com.golemlogix.network.FuzzyCyclePayload.TYPE, com.golemlogix.network.FuzzyCyclePayload.STREAM_CODEC,
                com.golemlogix.network.FuzzyCyclePayload::handle);
        registrar.playToServer(com.golemlogix.network.MenuScrollPayload.TYPE, com.golemlogix.network.MenuScrollPayload.STREAM_CODEC,
                com.golemlogix.network.MenuScrollPayload::handle);
    }
}
