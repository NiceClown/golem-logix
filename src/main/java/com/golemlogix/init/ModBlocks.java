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
            () -> new CreativeSourceJarBlock(props()));

    public static final DeferredBlock<ManaSaturatorBlock> MANA_SATURATOR = registerBlock("mana_saturator",
            "tooltip.golem_logix.mana_saturator",
            () -> new ManaSaturatorBlock(props()));

    /** Part of an assembled Saturator: it has no item and appears only when the machine is assembled. */
    public static final DeferredBlock<SaturatorPartBlock> SATURATOR_PART = registerPart("saturator_part",
            () -> new SaturatorPartBlock(props()));

    // Central Core & Expansion Bushings
    public static final DeferredBlock<AtlasCoreBlock> ATLAS_CORE = registerBlock("atlas_core",
            "tooltip.golem_logix.atlas_core",
            () -> new AtlasCoreBlock(props()));

    public static final DeferredBlock<AtlasCasingBlock> ATLAS_CASING = registerBlock("atlas_casing",
            "tooltip.golem_logix.atlas_casing",
            () -> new AtlasCasingBlock(props()));

    public static final DeferredBlock<AtlasConduitBlock> ATLAS_CONDUIT = registerBlock("atlas_conduit",
            "tooltip.golem_logix.atlas_conduit",
            () -> new AtlasConduitBlock(props()));

    public static final DeferredBlock<AtlantBlock> ATLAS_ATLANT = registerBlock("atlas_atlant",
            "tooltip.golem_logix.atlas_atlant",
            () -> new AtlantBlock(props()));

    public static final DeferredBlock<ChannelExpanderBlock> CHANNEL_EXPANDER = registerBlock("channel_expander",
            "tooltip.golem_logix.channel_expander",
            () -> new ChannelExpanderBlock(false, props()));

    public static final DeferredBlock<ChannelExpanderBlock> QUANTUM_CHANNEL_EXPANDER = registerBlock("quantum_channel_expander",
            "tooltip.golem_logix.quantum_channel_expander",
            () -> new ChannelExpanderBlock(true, props()));

    public static final DeferredBlock<CableBushingBlock> CABLE_BUSHING = registerBlock("cable_bushing",
            "tooltip.golem_logix.cable_bushing",
            () -> new CableBushingBlock(props()));

    // Snow Cryo Pipes (16 & 64 Channels)
    public static final DeferredBlock<SnowPipeBlock> BASIC_SNOW_PIPE = registerBlock("basic_snow_pipe",
            "tooltip.golem_logix.basic_snow_pipe",
            () -> new SnowPipeBlock(16, false, props()));

    public static final DeferredBlock<SnowPipeBlock> DENSE_FROST_PIPE = registerBlock("dense_frost_pipe",
            "tooltip.golem_logix.dense_frost_pipe",
            () -> new SnowPipeBlock(64, true, props()));

    // Storage Cabinets
    public static final DeferredBlock<CopperDriveBayBlock> COPPER_DRIVE_BAY = registerBlock("copper_drive_bay",
            "tooltip.golem_logix.copper_drive_bay",
            () -> new CopperDriveBayBlock(props()));

    // Terminals & Pattern Encoders
    public static final DeferredBlock<AnalogTerminalBlock> ANALOG_TERMINAL = registerBlock("analog_terminal",
            "tooltip.golem_logix.analog_terminal",
            () -> new AnalogTerminalBlock(props()));

    public static final DeferredBlock<CraftingTerminalBlock> CRAFTING_TERMINAL = registerBlock("crafting_terminal",
            "tooltip.golem_logix.crafting_terminal",
            () -> new CraftingTerminalBlock(props()));

    public static final DeferredBlock<PatternEncoderBlock> PATTERN_ENCODER = registerBlock("pattern_encoder",
            "tooltip.golem_logix.pattern_encoder",
            () -> new PatternEncoderBlock(props()));

    // Autocrafting & CPU
    public static final DeferredBlock<PatternProviderBlock> PATTERN_PROVIDER = registerBlock("pattern_provider",
            "tooltip.golem_logix.pattern_provider",
            () -> new PatternProviderBlock(props()));

    public static final DeferredBlock<CryoCalculatorBlock> CRYO_CALCULATOR = registerBlock("cryo_calculator",
            "tooltip.golem_logix.cryo_calculator",
            () -> new CryoCalculatorBlock(props()));

    public static final DeferredBlock<CoolingCoprocessorBlock> COOLING_COPROCESSOR = registerBlock("cooling_coprocessor",
            "tooltip.golem_logix.cooling_coprocessor",
            () -> new CoolingCoprocessorBlock(props()));

    /** Cryo-Mainframe: a cabinet for many calculators between two atlants. */
    public static final DeferredBlock<CryoMainframeBlock> CRYO_MAINFRAME = registerBlock("cryo_mainframe",
            "tooltip.golem_logix.cryo_mainframe",
            () -> new CryoMainframeBlock(props()));

    /** Part of an assembled mainframe (in place of glass and casings): no item. */
    public static final DeferredBlock<MainframePartBlock> MAINFRAME_PART = registerPart("mainframe_part",
            () -> new MainframePartBlock(props()));

    /** Punch Card Catalog: a cabinet for hundreds of punch cards with crafting-table recipes between two copper golems. */
    public static final DeferredBlock<CardCatalogBlock> CARD_CATALOG = registerBlock("punch_card_catalog",
            "tooltip.golem_logix.punch_card_catalog",
            () -> new CardCatalogBlock(props()));

    /** Part of an assembled catalog (in place of shelves, copper and statues): no item. */
    public static final DeferredBlock<CatalogPartBlock> CATALOG_PART = registerPart("catalog_part",
            () -> new CatalogPartBlock(props()));

    /** Cryo Schematic Concentrator: all punch cards of the network's providers in one window, by machine category. */
    public static final DeferredBlock<SchematicConcentratorBlock> SCHEMATIC_CONCENTRATOR = registerBlock("schematic_concentrator",
            "tooltip.golem_logix.schematic_concentrator",
            () -> new SchematicConcentratorBlock(props()));

    public static final DeferredBlock<RecipeMonitorBlock> RECIPE_MONITOR = registerBlock("recipe_monitor",
            "tooltip.golem_logix.recipe_monitor",
            () -> new RecipeMonitorBlock(props()));

    // a copper chest in glass: a fluid tank that keeps its fluid as an item, a bucket mode, joined in a crafting grid
    public static final DeferredBlock<com.golemlogix.blocks.CopperTankBlock> COPPER_TANK = registerBlock("copper_tank",
            () -> new com.golemlogix.blocks.CopperTankBlock(props()),
            (block, props) -> new com.golemlogix.items.CopperTankItem(block, "tooltip.golem_logix.copper_tank", props.stacksTo(1)));

    // nixie tubes with the network's channels
    public static final DeferredBlock<com.golemlogix.blocks.ChannelCounterBlock> CHANNEL_COUNTER = registerBlock("channel_counter",
            "tooltip.golem_logix.channel_counter",
            () -> new com.golemlogix.blocks.ChannelCounterBlock(props()));

    // World Interaction Buses
    public static final DeferredBlock<VacuumImportBusBlock> VACUUM_IMPORT_BUS = registerBlock("vacuum_import_bus",
            "tooltip.golem_logix.vacuum_import_bus",
            () -> new VacuumImportBusBlock(props()));

    public static final DeferredBlock<InjectorExportBusBlock> INJECTOR_EXPORT_BUS = registerBlock("injector_export_bus",
            "tooltip.golem_logix.injector_export_bus",
            () -> new InjectorExportBusBlock(props()));

    public static final DeferredBlock<PeriscopeStorageBusBlock> PERISCOPE_STORAGE_BUS = registerBlock("periscope_storage_bus",
            "tooltip.golem_logix.periscope_storage_bus",
            () -> new PeriscopeStorageBusBlock(props()));

    public static final DeferredBlock<InterfaceBlock> GOLEM_INTERFACE = registerBlock("golem_interface",
            "tooltip.golem_logix.golem_interface",
            () -> new InterfaceBlock(props()));

    // Late Game Resonator
    /** Cryo-Source Telegraph: links a distant part of the network to the base (Aetherial Linkage cards, range cards). */
    public static final DeferredBlock<TelegraphBlock> CRYO_SOURCE_TELEGRAPH = registerBlock("cryo_source_telegraph",
            "tooltip.golem_logix.cryo_source_telegraph",
            () -> new TelegraphBlock(props()));

    public static final DeferredBlock<EternalFrostResonatorBlock> ETERNAL_FROST_RESONATOR = registerBlock("eternal_frost_resonator",
            "tooltip.golem_logix.eternal_frost_resonator",
            () -> new EternalFrostResonatorBlock(props()));

    // Source without Ars Nouveau: blood distilled into Cryo-Source (Neo Vitae)
    public static final DeferredBlock<com.golemlogix.blocks.CryoJarBlock> CRYO_SOURCE_JAR = registerBlock("cryo_source_jar",
            "tooltip.golem_logix.cryo_source_jar",
            () -> new com.golemlogix.blocks.CryoJarBlock(props()));
    public static final DeferredBlock<com.golemlogix.blocks.DistillationTowerBlock> CRYO_DISTILLATION_TOWER = registerBlock("cryo_distillation_tower",
            "tooltip.golem_logix.cryo_distillation_tower",
            () -> new com.golemlogix.blocks.DistillationTowerBlock(props()));
    // water and Liquid Cryo-Source frozen into snow (for snow golems) or ice
    public static final DeferredBlock<com.golemlogix.blocks.CryoMixerBlock> CRYO_MIXER = registerBlock("cryo_mixer",
            "tooltip.golem_logix.cryo_mixer",
            () -> new com.golemlogix.blocks.CryoMixerBlock(props()));

    /** The block being built right now: since 1.21.2 its properties must carry its id. */
    private static net.minecraft.resources.Identifier building;

    /** Properties of the block being registered, with its id set. */
    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.BLOCK, building));
    }

    private static <T extends Block> DeferredBlock<T> registerPart(String name, Supplier<T> blockSupplier) {
        return BLOCKS.register(name, id -> {
            building = id;
            try {
                return blockSupplier.get();
            } finally {
                building = null;
            }
        });
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, String tooltipKey, Supplier<T> blockSupplier) {
        return registerBlock(name, blockSupplier, (block, props) -> new ModBlockItem(block, tooltipKey, props));
    }

    /** A block with an item of its own kind (the properties come with the id set). */
    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier,
                                                                    java.util.function.BiFunction<T, Item.Properties, ? extends Item> item) {
        DeferredBlock<T> block = registerPart(name, blockSupplier);
        ModItems.ITEMS.register(name, id -> item.apply(block.get(), new Item.Properties()
                .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                .useBlockDescriptionPrefix()));
        return block;
    }
}
