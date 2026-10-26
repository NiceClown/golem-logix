package com.golemlogix.init;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, GolemLogixMod.MOD_ID);

    public static final Supplier<BlockEntityType<ManaSaturatorBlockEntity>> MANA_SATURATOR =
            BLOCK_ENTITY_TYPES.register("mana_saturator", () ->
                    BlockEntityType.Builder.of(ManaSaturatorBlockEntity::new, ModBlocks.MANA_SATURATOR.get()).build(null));

    public static final Supplier<BlockEntityType<SaturatorPartBlockEntity>> SATURATOR_PART =
            BLOCK_ENTITY_TYPES.register("saturator_part", () ->
                    BlockEntityType.Builder.of(SaturatorPartBlockEntity::new, ModBlocks.SATURATOR_PART.get()).build(null));

    public static final Supplier<BlockEntityType<CreativeSourceJarBlockEntity>> CREATIVE_SOURCE_JAR =
            BLOCK_ENTITY_TYPES.register("creative_source_jar", () ->
                    BlockEntityType.Builder.of(CreativeSourceJarBlockEntity::new, ModBlocks.CREATIVE_SOURCE_JAR.get()).build(null));

    public static final Supplier<BlockEntityType<EternalFrostResonatorBlockEntity>> ETERNAL_FROST_RESONATOR =
            BLOCK_ENTITY_TYPES.register("eternal_frost_resonator", () ->
                    BlockEntityType.Builder.of(EternalFrostResonatorBlockEntity::new, ModBlocks.ETERNAL_FROST_RESONATOR.get()).build(null));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.CryoJarBlockEntity>> CRYO_JAR =
            BLOCK_ENTITY_TYPES.register("cryo_source_jar", () ->
                    BlockEntityType.Builder.of(com.golemlogix.blockentity.CryoJarBlockEntity::new, ModBlocks.CRYO_SOURCE_JAR.get()).build(null));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.DistillationTowerBlockEntity>> DISTILLATION_TOWER =
            BLOCK_ENTITY_TYPES.register("cryo_distillation_tower", () ->
                    BlockEntityType.Builder.of(com.golemlogix.blockentity.DistillationTowerBlockEntity::new, ModBlocks.CRYO_DISTILLATION_TOWER.get()).build(null));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.CryoMixerBlockEntity>> CRYO_MIXER =
            BLOCK_ENTITY_TYPES.register("cryo_mixer", () ->
                    BlockEntityType.Builder.of(com.golemlogix.blockentity.CryoMixerBlockEntity::new, ModBlocks.CRYO_MIXER.get()).build(null));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.TelegraphBlockEntity>> TELEGRAPH =
            BLOCK_ENTITY_TYPES.register("cryo_source_telegraph", () ->
                    BlockEntityType.Builder.of(com.golemlogix.blockentity.TelegraphBlockEntity::new, ModBlocks.CRYO_SOURCE_TELEGRAPH.get()).build(null));

    public static final Supplier<BlockEntityType<AtlasCoreBlockEntity>> ATLAS_CORE =
            BLOCK_ENTITY_TYPES.register("atlas_core", () ->
                    BlockEntityType.Builder.of(AtlasCoreBlockEntity::new, ModBlocks.ATLAS_CORE.get()).build(null));

    public static final Supplier<BlockEntityType<CopperDriveBayBlockEntity>> COPPER_DRIVE_BAY =
            BLOCK_ENTITY_TYPES.register("copper_drive_bay", () ->
                    BlockEntityType.Builder.of(CopperDriveBayBlockEntity::new, ModBlocks.COPPER_DRIVE_BAY.get()).build(null));

    public static final Supplier<BlockEntityType<PatternProviderBlockEntity>> PATTERN_PROVIDER =
            BLOCK_ENTITY_TYPES.register("pattern_provider", () ->
                    BlockEntityType.Builder.of(PatternProviderBlockEntity::new, ModBlocks.PATTERN_PROVIDER.get()).build(null));

    public static final Supplier<BlockEntityType<CryoCalculatorBlockEntity>> CRYO_CALCULATOR =
            BLOCK_ENTITY_TYPES.register("cryo_calculator", () ->
                    BlockEntityType.Builder.of(CryoCalculatorBlockEntity::new, ModBlocks.CRYO_CALCULATOR.get()).build(null));

    public static final Supplier<BlockEntityType<CryoMainframeBlockEntity>> CRYO_MAINFRAME =
            BLOCK_ENTITY_TYPES.register("cryo_mainframe", () ->
                    BlockEntityType.Builder.of(CryoMainframeBlockEntity::new, ModBlocks.CRYO_MAINFRAME.get()).build(null));

    public static final Supplier<BlockEntityType<CardCatalogBlockEntity>> CARD_CATALOG =
            BLOCK_ENTITY_TYPES.register("punch_card_catalog", () ->
                    BlockEntityType.Builder.of(CardCatalogBlockEntity::new, ModBlocks.CARD_CATALOG.get()).build(null));

    public static final Supplier<BlockEntityType<SchematicConcentratorBlockEntity>> SCHEMATIC_CONCENTRATOR =
            BLOCK_ENTITY_TYPES.register("schematic_concentrator", () ->
                    BlockEntityType.Builder.of(SchematicConcentratorBlockEntity::new, ModBlocks.SCHEMATIC_CONCENTRATOR.get()).build(null));

    public static final Supplier<BlockEntityType<CoolingCoprocessorBlockEntity>> COOLING_COPROCESSOR =
            BLOCK_ENTITY_TYPES.register("cooling_coprocessor", () ->
                    BlockEntityType.Builder.of(CoolingCoprocessorBlockEntity::new, ModBlocks.COOLING_COPROCESSOR.get()).build(null));

    public static final Supplier<BlockEntityType<RecipeMonitorBlockEntity>> RECIPE_MONITOR =
            BLOCK_ENTITY_TYPES.register("recipe_monitor", () ->
                    BlockEntityType.Builder.of(RecipeMonitorBlockEntity::new, ModBlocks.RECIPE_MONITOR.get()).build(null));

    public static final Supplier<BlockEntityType<PipeBlockEntity>> SNOW_PIPE =
            BLOCK_ENTITY_TYPES.register("snow_pipe", () ->
                    BlockEntityType.Builder.of(PipeBlockEntity::new,
                            ModBlocks.BASIC_SNOW_PIPE.get(), ModBlocks.DENSE_FROST_PIPE.get()).build(null));

    public static final Supplier<BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITY_TYPES.register("terminal", () ->
                    BlockEntityType.Builder.of(TerminalBlockEntity::new,
                            ModBlocks.ANALOG_TERMINAL.get(), ModBlocks.CRAFTING_TERMINAL.get()).build(null));

    public static final Supplier<BlockEntityType<PeriscopeBlockEntity>> PERISCOPE =
            BLOCK_ENTITY_TYPES.register("periscope", () ->
                    BlockEntityType.Builder.of(PeriscopeBlockEntity::new, ModBlocks.PERISCOPE_STORAGE_BUS.get()).build(null));

    public static final Supplier<BlockEntityType<PatternEncoderBlockEntity>> PATTERN_ENCODER =
            BLOCK_ENTITY_TYPES.register("pattern_encoder", () ->
                    BlockEntityType.Builder.of(PatternEncoderBlockEntity::new, ModBlocks.PATTERN_ENCODER.get()).build(null));

    public static final Supplier<BlockEntityType<AtlantHeadBlockEntity>> ATLANT_HEAD =
            BLOCK_ENTITY_TYPES.register("atlant_head", () ->
                    BlockEntityType.Builder.of(AtlantHeadBlockEntity::new, ModBlocks.ATLAS_ATLANT.get()).build(null));

    public static final Supplier<BlockEntityType<InterfaceBlockEntity>> INTERFACE =
            BLOCK_ENTITY_TYPES.register("golem_interface", () ->
                    BlockEntityType.Builder.of(InterfaceBlockEntity::new, ModBlocks.GOLEM_INTERFACE.get()).build(null));

    public static final Supplier<BlockEntityType<BusBlockEntity>> BUS =
            BLOCK_ENTITY_TYPES.register("bus", () ->
                    BlockEntityType.Builder.of(BusBlockEntity::new,
                            ModBlocks.VACUUM_IMPORT_BUS.get(), ModBlocks.INJECTOR_EXPORT_BUS.get()).build(null));
}
