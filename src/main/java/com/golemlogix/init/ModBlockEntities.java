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
                    new BlockEntityType<>(ManaSaturatorBlockEntity::new, ModBlocks.MANA_SATURATOR.get()));

    public static final Supplier<BlockEntityType<SaturatorPartBlockEntity>> SATURATOR_PART =
            BLOCK_ENTITY_TYPES.register("saturator_part", () ->
                    new BlockEntityType<>(SaturatorPartBlockEntity::new, ModBlocks.SATURATOR_PART.get()));

    public static final Supplier<BlockEntityType<CreativeSourceJarBlockEntity>> CREATIVE_SOURCE_JAR =
            BLOCK_ENTITY_TYPES.register("creative_source_jar", () ->
                    new BlockEntityType<>(CreativeSourceJarBlockEntity::new, ModBlocks.CREATIVE_SOURCE_JAR.get()));

    public static final Supplier<BlockEntityType<EternalFrostResonatorBlockEntity>> ETERNAL_FROST_RESONATOR =
            BLOCK_ENTITY_TYPES.register("eternal_frost_resonator", () ->
                    new BlockEntityType<>(EternalFrostResonatorBlockEntity::new, ModBlocks.ETERNAL_FROST_RESONATOR.get()));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.CryoJarBlockEntity>> CRYO_JAR =
            BLOCK_ENTITY_TYPES.register("cryo_source_jar", () ->
                    new BlockEntityType<>(com.golemlogix.blockentity.CryoJarBlockEntity::new, ModBlocks.CRYO_SOURCE_JAR.get()));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.DistillationTowerBlockEntity>> DISTILLATION_TOWER =
            BLOCK_ENTITY_TYPES.register("cryo_distillation_tower", () ->
                    new BlockEntityType<>(com.golemlogix.blockentity.DistillationTowerBlockEntity::new, ModBlocks.CRYO_DISTILLATION_TOWER.get()));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.CryoMixerBlockEntity>> CRYO_MIXER =
            BLOCK_ENTITY_TYPES.register("cryo_mixer", () ->
                    new BlockEntityType<>(com.golemlogix.blockentity.CryoMixerBlockEntity::new, ModBlocks.CRYO_MIXER.get()));

    public static final Supplier<BlockEntityType<com.golemlogix.blockentity.TelegraphBlockEntity>> TELEGRAPH =
            BLOCK_ENTITY_TYPES.register("cryo_source_telegraph", () ->
                    new BlockEntityType<>(com.golemlogix.blockentity.TelegraphBlockEntity::new, ModBlocks.CRYO_SOURCE_TELEGRAPH.get()));

    public static final Supplier<BlockEntityType<AtlasCoreBlockEntity>> ATLAS_CORE =
            BLOCK_ENTITY_TYPES.register("atlas_core", () ->
                    new BlockEntityType<>(AtlasCoreBlockEntity::new, ModBlocks.ATLAS_CORE.get()));

    public static final Supplier<BlockEntityType<CopperDriveBayBlockEntity>> COPPER_DRIVE_BAY =
            BLOCK_ENTITY_TYPES.register("copper_drive_bay", () ->
                    new BlockEntityType<>(CopperDriveBayBlockEntity::new, ModBlocks.COPPER_DRIVE_BAY.get()));

    public static final Supplier<BlockEntityType<PatternProviderBlockEntity>> PATTERN_PROVIDER =
            BLOCK_ENTITY_TYPES.register("pattern_provider", () ->
                    new BlockEntityType<>(PatternProviderBlockEntity::new, ModBlocks.PATTERN_PROVIDER.get()));

    public static final Supplier<BlockEntityType<CryoCalculatorBlockEntity>> CRYO_CALCULATOR =
            BLOCK_ENTITY_TYPES.register("cryo_calculator", () ->
                    new BlockEntityType<>(CryoCalculatorBlockEntity::new, ModBlocks.CRYO_CALCULATOR.get()));

    public static final Supplier<BlockEntityType<CryoMainframeBlockEntity>> CRYO_MAINFRAME =
            BLOCK_ENTITY_TYPES.register("cryo_mainframe", () ->
                    new BlockEntityType<>(CryoMainframeBlockEntity::new, ModBlocks.CRYO_MAINFRAME.get()));

    public static final Supplier<BlockEntityType<CardCatalogBlockEntity>> CARD_CATALOG =
            BLOCK_ENTITY_TYPES.register("punch_card_catalog", () ->
                    new BlockEntityType<>(CardCatalogBlockEntity::new, ModBlocks.CARD_CATALOG.get()));

    public static final Supplier<BlockEntityType<SchematicConcentratorBlockEntity>> SCHEMATIC_CONCENTRATOR =
            BLOCK_ENTITY_TYPES.register("schematic_concentrator", () ->
                    new BlockEntityType<>(SchematicConcentratorBlockEntity::new, ModBlocks.SCHEMATIC_CONCENTRATOR.get()));

    public static final Supplier<BlockEntityType<CoolingCoprocessorBlockEntity>> COOLING_COPROCESSOR =
            BLOCK_ENTITY_TYPES.register("cooling_coprocessor", () ->
                    new BlockEntityType<>(CoolingCoprocessorBlockEntity::new, ModBlocks.COOLING_COPROCESSOR.get()));

    public static final Supplier<BlockEntityType<RecipeMonitorBlockEntity>> RECIPE_MONITOR =
            BLOCK_ENTITY_TYPES.register("recipe_monitor", () ->
                    new BlockEntityType<>(RecipeMonitorBlockEntity::new, ModBlocks.RECIPE_MONITOR.get()));

    public static final Supplier<BlockEntityType<PipeBlockEntity>> SNOW_PIPE =
            BLOCK_ENTITY_TYPES.register("snow_pipe", () ->
                    new BlockEntityType<>(PipeBlockEntity::new,
                            ModBlocks.BASIC_SNOW_PIPE.get(), ModBlocks.DENSE_FROST_PIPE.get()));

    public static final Supplier<BlockEntityType<TerminalBlockEntity>> TERMINAL =
            BLOCK_ENTITY_TYPES.register("terminal", () ->
                    new BlockEntityType<>(TerminalBlockEntity::new,
                            ModBlocks.ANALOG_TERMINAL.get(), ModBlocks.CRAFTING_TERMINAL.get()));

    public static final Supplier<BlockEntityType<PeriscopeBlockEntity>> PERISCOPE =
            BLOCK_ENTITY_TYPES.register("periscope", () ->
                    new BlockEntityType<>(PeriscopeBlockEntity::new, ModBlocks.PERISCOPE_STORAGE_BUS.get()));

    public static final Supplier<BlockEntityType<PatternEncoderBlockEntity>> PATTERN_ENCODER =
            BLOCK_ENTITY_TYPES.register("pattern_encoder", () ->
                    new BlockEntityType<>(PatternEncoderBlockEntity::new, ModBlocks.PATTERN_ENCODER.get()));

    public static final Supplier<BlockEntityType<AtlantHeadBlockEntity>> ATLANT_HEAD =
            BLOCK_ENTITY_TYPES.register("atlant_head", () ->
                    new BlockEntityType<>(AtlantHeadBlockEntity::new, ModBlocks.ATLAS_ATLANT.get()));

    public static final Supplier<BlockEntityType<InterfaceBlockEntity>> INTERFACE =
            BLOCK_ENTITY_TYPES.register("golem_interface", () ->
                    new BlockEntityType<>(InterfaceBlockEntity::new, ModBlocks.GOLEM_INTERFACE.get()));

    public static final Supplier<BlockEntityType<BusBlockEntity>> BUS =
            BLOCK_ENTITY_TYPES.register("bus", () ->
                    new BlockEntityType<>(BusBlockEntity::new,
                            ModBlocks.VACUUM_IMPORT_BUS.get(), ModBlocks.INJECTOR_EXPORT_BUS.get()));
}
