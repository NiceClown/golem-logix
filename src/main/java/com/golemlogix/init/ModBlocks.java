package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.*;
import com.golemlogix.items.ModBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GolemLogixMod.MOD_ID);

    // Testing & Source Blocks
    public static final DeferredBlock<CreativeSourceJarBlock> CREATIVE_SOURCE_JAR = registerBlock("creative_source_jar",
            "tooltip.golem_logix.creative_source_jar",
            () -> new CreativeSourceJarBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<ManaSaturatorBlock> MANA_SATURATOR = registerBlock("mana_saturator",
            "tooltip.golem_logix.mana_saturator",
            () -> new ManaSaturatorBlock(BlockBehaviour.Properties.of()));

    /** Part of an assembled Saturator: it has no item and appears only when the machine is assembled. */
    public static final DeferredBlock<SaturatorPartBlock> SATURATOR_PART = BLOCKS.register("saturator_part",
            () -> new SaturatorPartBlock(BlockBehaviour.Properties.of()));

    // Central Core & Expansion Bushings
    public static final DeferredBlock<AtlasCoreBlock> ATLAS_CORE = registerBlock("atlas_core",
            "tooltip.golem_logix.atlas_core",
            () -> new AtlasCoreBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<AtlasCasingBlock> ATLAS_CASING = registerBlock("atlas_casing",
            "tooltip.golem_logix.atlas_casing",
            () -> new AtlasCasingBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<AtlasConduitBlock> ATLAS_CONDUIT = registerBlock("atlas_conduit",
            "tooltip.golem_logix.atlas_conduit",
            () -> new AtlasConduitBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<AtlantBlock> ATLAS_ATLANT = registerBlock("atlas_atlant",
            "tooltip.golem_logix.atlas_atlant",
            () -> new AtlantBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<ChannelExpanderBlock> CHANNEL_EXPANDER = registerBlock("channel_expander",
            "tooltip.golem_logix.channel_expander",
            () -> new ChannelExpanderBlock(false, BlockBehaviour.Properties.of()));

    public static final DeferredBlock<ChannelExpanderBlock> QUANTUM_CHANNEL_EXPANDER = registerBlock("quantum_channel_expander",
            "tooltip.golem_logix.quantum_channel_expander",
            () -> new ChannelExpanderBlock(true, BlockBehaviour.Properties.of()));

    public static final DeferredBlock<CableBushingBlock> CABLE_BUSHING = registerBlock("cable_bushing",
            "tooltip.golem_logix.cable_bushing",
            () -> new CableBushingBlock(BlockBehaviour.Properties.of()));

    // Snow Cryo Pipes (16 & 64 Channels)
    public static final DeferredBlock<SnowPipeBlock> BASIC_SNOW_PIPE = registerBlock("basic_snow_pipe",
            "tooltip.golem_logix.basic_snow_pipe",
            () -> new SnowPipeBlock(16, false, BlockBehaviour.Properties.of()));

    public static final DeferredBlock<SnowPipeBlock> DENSE_FROST_PIPE = registerBlock("dense_frost_pipe",
            "tooltip.golem_logix.dense_frost_pipe",
            () -> new SnowPipeBlock(64, true, BlockBehaviour.Properties.of()));

    // Storage Cabinets
    public static final DeferredBlock<CopperDriveBayBlock> COPPER_DRIVE_BAY = registerBlock("copper_drive_bay",
            "tooltip.golem_logix.copper_drive_bay",
            () -> new CopperDriveBayBlock(BlockBehaviour.Properties.of()));

    // Terminals & Pattern Encoders
    public static final DeferredBlock<AnalogTerminalBlock> ANALOG_TERMINAL = registerBlock("analog_terminal",
            "tooltip.golem_logix.analog_terminal",
            () -> new AnalogTerminalBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<CraftingTerminalBlock> CRAFTING_TERMINAL = registerBlock("crafting_terminal",
            "tooltip.golem_logix.crafting_terminal",
            () -> new CraftingTerminalBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<PatternEncoderBlock> PATTERN_ENCODER = registerBlock("pattern_encoder",
            "tooltip.golem_logix.pattern_encoder",
            () -> new PatternEncoderBlock(BlockBehaviour.Properties.of()));

    // Autocrafting & CPU
    public static final DeferredBlock<PatternProviderBlock> PATTERN_PROVIDER = registerBlock("pattern_provider",
            "tooltip.golem_logix.pattern_provider",
            () -> new PatternProviderBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<CryoCalculatorBlock> CRYO_CALCULATOR = registerBlock("cryo_calculator",
            "tooltip.golem_logix.cryo_calculator",
            () -> new CryoCalculatorBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<CoolingCoprocessorBlock> COOLING_COPROCESSOR = registerBlock("cooling_coprocessor",
            "tooltip.golem_logix.cooling_coprocessor",
            () -> new CoolingCoprocessorBlock(BlockBehaviour.Properties.of()));

    /** Cryo-Mainframe: a cabinet for many calculators between two atlants. */
    public static final DeferredBlock<CryoMainframeBlock> CRYO_MAINFRAME = registerBlock("cryo_mainframe",
            "tooltip.golem_logix.cryo_mainframe",
            () -> new CryoMainframeBlock(BlockBehaviour.Properties.of()));

    /** Part of an assembled mainframe (in place of glass and casings): no item. */
    public static final DeferredBlock<MainframePartBlock> MAINFRAME_PART = BLOCKS.register("mainframe_part",
            () -> new MainframePartBlock(BlockBehaviour.Properties.of()));

    /** Punch Card Catalog: a cabinet for hundreds of punch cards with crafting-table recipes between two copper golems. */
    public static final DeferredBlock<CardCatalogBlock> CARD_CATALOG = registerBlock("punch_card_catalog",
            "tooltip.golem_logix.punch_card_catalog",
            () -> new CardCatalogBlock(BlockBehaviour.Properties.of()));

    /** Part of an assembled catalog (in place of shelves, copper and statues): no item. */
    public static final DeferredBlock<CatalogPartBlock> CATALOG_PART = BLOCKS.register("catalog_part",
            () -> new CatalogPartBlock(BlockBehaviour.Properties.of()));

    /** Cryo Schematic Concentrator: all punch cards of the network's providers in one window, by machine category. */
    public static final DeferredBlock<SchematicConcentratorBlock> SCHEMATIC_CONCENTRATOR = registerBlock("schematic_concentrator",
            "tooltip.golem_logix.schematic_concentrator",
            () -> new SchematicConcentratorBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<RecipeMonitorBlock> RECIPE_MONITOR = registerBlock("recipe_monitor",
            "tooltip.golem_logix.recipe_monitor",
            () -> new RecipeMonitorBlock(BlockBehaviour.Properties.of()));

    // World Interaction Buses
    public static final DeferredBlock<VacuumImportBusBlock> VACUUM_IMPORT_BUS = registerBlock("vacuum_import_bus",
            "tooltip.golem_logix.vacuum_import_bus",
            () -> new VacuumImportBusBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<InjectorExportBusBlock> INJECTOR_EXPORT_BUS = registerBlock("injector_export_bus",
            "tooltip.golem_logix.injector_export_bus",
            () -> new InjectorExportBusBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<PeriscopeStorageBusBlock> PERISCOPE_STORAGE_BUS = registerBlock("periscope_storage_bus",
            "tooltip.golem_logix.periscope_storage_bus",
            () -> new PeriscopeStorageBusBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<InterfaceBlock> GOLEM_INTERFACE = registerBlock("golem_interface",
            "tooltip.golem_logix.golem_interface",
            () -> new InterfaceBlock(BlockBehaviour.Properties.of()));

    // Late Game Resonator
    /** Cryo-Source Telegraph: links a distant part of the network to the base (Aetherial Linkage cards, range cards). */
    public static final DeferredBlock<TelegraphBlock> CRYO_SOURCE_TELEGRAPH = registerBlock("cryo_source_telegraph",
            "tooltip.golem_logix.cryo_source_telegraph",
            () -> new TelegraphBlock(BlockBehaviour.Properties.of()));

    public static final DeferredBlock<EternalFrostResonatorBlock> ETERNAL_FROST_RESONATOR = registerBlock("eternal_frost_resonator",
            "tooltip.golem_logix.eternal_frost_resonator",
            () -> new EternalFrostResonatorBlock(BlockBehaviour.Properties.of()));

    // Source without Ars Nouveau: blood distilled into Cryo-Source (Neo Vitae)
    public static final DeferredBlock<com.golemlogix.blocks.CryoJarBlock> CRYO_SOURCE_JAR = registerBlock("cryo_source_jar",
            "tooltip.golem_logix.cryo_source_jar",
            () -> new com.golemlogix.blocks.CryoJarBlock(BlockBehaviour.Properties.of()));
    public static final DeferredBlock<com.golemlogix.blocks.DistillationTowerBlock> CRYO_DISTILLATION_TOWER = registerBlock("cryo_distillation_tower",
            "tooltip.golem_logix.cryo_distillation_tower",
            () -> new com.golemlogix.blocks.DistillationTowerBlock(BlockBehaviour.Properties.of()));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, String tooltipKey, Supplier<T> blockSupplier) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> new ModBlockItem(block.get(), tooltipKey, new Item.Properties()));
        return block;
    }
}
