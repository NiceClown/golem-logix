package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GolemLogixMod.MOD_ID);

    public static final Supplier<CreativeModeTab> GOLEM_LOGIX_TAB = CREATIVE_MODE_TABS.register("golem_logix_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.golem_logix"))
                    .icon(() -> new ItemStack(ModItems.FLATTENED_IRON_CORE.get()))
                    .displayItems((params, output) -> {
                        // Guidebook
                        output.accept(ModItems.GUIDE_BOOK.get());

                        // Ritual & Core materials
                        output.accept(ModItems.FLATTENED_IRON_CORE.get());
                        output.accept(ModItems.FLATTENED_COPPER_MATRIX.get());
                        output.accept(ModItems.FLATTENED_CRYO_SUBSTRATE.get());
                        output.accept(ModItems.RAW_IRON_MIND.get());
                        output.accept(ModItems.RAW_COPPER_MIND.get());
                        output.accept(ModItems.RAW_SNOW_MIND.get());
                        output.accept(ModItems.TORN_IRON_MIND.get());
                        output.accept(ModItems.TORN_COPPER_MIND.get());
                        output.accept(ModItems.TORN_SNOW_MIND.get());
                        output.accept(ModItems.IMPLODED_WITHER_PLASMA.get());
                        output.accept(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get());

                        // Multiblock & Core
                        output.accept(ModBlocks.ATLAS_CORE.get());
                        output.accept(ModBlocks.ATLAS_CASING.get());
                        output.accept(ModBlocks.ATLAS_CONDUIT.get());
                        output.accept(ModBlocks.ATLAS_ATLANT.get());
                        output.accept(ModBlocks.CABLE_BUSHING.get());
                        output.accept(ModBlocks.CHANNEL_EXPANDER.get());
                        output.accept(ModBlocks.QUANTUM_CHANNEL_EXPANDER.get());
                        output.accept(ModBlocks.CREATIVE_SOURCE_JAR.get());
                        output.accept(ModBlocks.ETERNAL_FROST_RESONATOR.get());
                        output.accept(ModBlocks.MANA_SATURATOR.get());
                        output.accept(ModItems.FORGE_HAMMER.get());

                        // Pipes
                        output.accept(ModBlocks.BASIC_SNOW_PIPE.get());
                        output.accept(ModBlocks.DENSE_FROST_PIPE.get());
                        output.accept(ModBlocks.CRYO_SOURCE_TELEGRAPH.get());
                        output.accept(ModItems.AETHERIAL_LINKAGE.get());
                        output.accept(ModItems.AETHER_RESONANCE_CORE.get());
                        output.accept(ModItems.VOID_GLASS_PRISM.get());

                        // Storage & Disks
                        output.accept(ModBlocks.COPPER_DRIVE_BAY.get());
                        output.accept(ModItems.COPPER_DISK_1K.get());
                        output.accept(ModItems.COPPER_DISK_4K.get());
                        output.accept(ModItems.COPPER_DISK_16K.get());
                        output.accept(ModItems.COPPER_DISK_64K.get());
                        output.accept(ModItems.COPPER_DISK_256K.get());

                        // Fluid Disks
                        output.accept(ModItems.FLUID_DISK_1K.get());
                        output.accept(ModItems.FLUID_DISK_4K.get());
                        output.accept(ModItems.FLUID_DISK_16K.get());
                        output.accept(ModItems.FLUID_DISK_64K.get());
                        output.accept(ModItems.FLUID_DISK_256K.get());

                        // Terminals & Wireless
                        output.accept(ModBlocks.ANALOG_TERMINAL.get());
                        output.accept(ModBlocks.CRAFTING_TERMINAL.get());
                        output.accept(ModItems.PORTABLE_CHRONOMETER.get());
                        for (int tier = 1; tier <= 2; tier++) {
                            ItemStack upgraded = new ItemStack(ModItems.PORTABLE_CHRONOMETER.get());
                            upgraded.set(ModDataComponents.WIRELESS_TIER.get(), tier);
                            output.accept(upgraded);
                        }

                        // Autocrafting
                        output.accept(ModBlocks.PATTERN_ENCODER.get());
                        output.accept(ModItems.COPPER_PUNCH_CARD.get());
                        output.accept(ModBlocks.PATTERN_PROVIDER.get());
                        output.accept(ModBlocks.CARD_CATALOG.get());
                        output.accept(ModBlocks.SCHEMATIC_CONCENTRATOR.get());
                        output.accept(ModBlocks.CRYO_CALCULATOR.get());
                        output.accept(ModBlocks.CRYO_MAINFRAME.get());
                        output.accept(ModBlocks.COOLING_COPROCESSOR.get());
                        output.accept(ModBlocks.RECIPE_MONITOR.get());

                        // Buses
                        output.accept(ModBlocks.VACUUM_IMPORT_BUS.get());
                        output.accept(ModBlocks.INJECTOR_EXPORT_BUS.get());
                        output.accept(ModBlocks.PERISCOPE_STORAGE_BUS.get());
                        output.accept(ModBlocks.GOLEM_INTERFACE.get());
                        output.accept(ModItems.PIPE_PLUG.get());

                        // Upgrades
                        output.accept(ModItems.COMPRESSED_COPROCESSOR.get());
                        output.accept(ModItems.AUTOCRAFT_MODULE.get());
                        output.accept(ModItems.REDSTONE_MODULE.get());
                    })
                    .build());
}
