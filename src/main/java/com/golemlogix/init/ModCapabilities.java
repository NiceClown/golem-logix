package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.SaturatorPartBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Доступ автоматики к блокам мода. Насыщатель Маны: предмет на наковальню и готовые пластины — через любую часть машины. */
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
        // Интерфейс: выдача запаса и приём в сеть — с любой стороны
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INTERFACE.get(), (be, side) -> be.automation());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.INTERFACE.get(), (be, side) -> be.fluids());
    }
}
