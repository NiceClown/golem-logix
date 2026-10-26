package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.SaturatorPartBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Automation access to the mod's blocks. Mana Saturator: an item onto the anvil and the finished plates through any part of the machine. */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModCapabilities {
    private ModCapabilities() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.MANA_SATURATOR.get(),
                (be, side) -> be.automation());
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> level.getBlockEntity(SaturatorPartBlock.controllerOf(pos, state))
                        instanceof ManaSaturatorBlockEntity saturator ? saturator.automation() : null,
                ModBlocks.SATURATOR_PART.get());
        // Interface: handing out stock and taking into the network, from any side
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INTERFACE.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.INTERFACE.get(), (be, side) -> be.fluids());
        // Cryo-Distillation Tower: blood goes in through any of its blocks
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    var tower = com.golemlogix.blocks.DistillationTowerBlock.entityOf(level, pos, state);
                    return tower == null ? null : tower.input();
                },
                ModBlocks.CRYO_DISTILLATION_TOWER.get());
        // Cryo-Mixer: water and Cryo-Source go in through either block, the snow or ice comes out of the tray
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    var mixer = com.golemlogix.blocks.CryoMixerBlock.entityOf(level, pos, state);
                    return mixer == null ? null : mixer.input();
                },
                ModBlocks.CRYO_MIXER.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    var mixer = com.golemlogix.blocks.CryoMixerBlock.entityOf(level, pos, state);
                    return mixer == null ? null : mixer.tray();
                },
                ModBlocks.CRYO_MIXER.get());
    }
}
