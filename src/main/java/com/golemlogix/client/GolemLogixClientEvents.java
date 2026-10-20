package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public class GolemLogixClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MANA_SATURATOR.get(), ManaSaturatorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ATLAS_CORE.get(), AtlasCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SNOW_PIPE.get(), SnowPipeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COPPER_DRIVE_BAY.get(), DriveBayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TERMINAL.get(), TerminalRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYO_CALCULATOR.get(), CryoCalculatorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COOLING_COPROCESSOR.get(), CoolingCoprocessorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.RECIPE_MONITOR.get(), RecipeMonitorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INTERFACE.get(), InterfaceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ATLANT_HEAD.get(), AtlantHeadRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYO_MAINFRAME.get(), CryoMainframeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ETERNAL_FROST_RESONATOR.get(), EternalFrostResonatorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CARD_CATALOG.get(), CardCatalogRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SCHEMATIC_CONCENTRATOR.get(), ConcentratorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.TELEGRAPH.get(), TelegraphRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYO_JAR.get(), CryoJarRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DISTILLATION_TOWER.get(), DistillationTowerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CRYO_MIXER.get(), CryoMixerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PERISCOPE.get(), PeriscopeEyeRenderer::new);
    }

    @SubscribeEvent
    public static void registerPipelines(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
        event.registerPipeline(com.golemlogix.client.render.GlowTypes.ADDITIVE_EYES);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CopperGolemPuppet.LAYER, CopperGolemPuppet::createLayer);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TERMINAL.get(), TerminalScreen::new);
        event.register(ModMenus.DRIVE_BAY.get(), DriveBayScreen::new);
        event.register(ModMenus.BUS.get(), BusScreen::new);
        event.register(ModMenus.PATTERN_ENCODER.get(), PatternEncoderScreen::new);
        event.register(ModMenus.PATTERN_PROVIDER.get(), PatternProviderScreen::new);
        event.register(ModMenus.CALCULATOR.get(), CalculatorScreen::new);
        event.register(ModMenus.RECIPE_MONITOR.get(), RecipeMonitorScreen::new);
        event.register(ModMenus.INTERFACE.get(), InterfaceScreen::new);
        event.register(ModMenus.MAINFRAME.get(), MainframeScreen::new);
        event.register(ModMenus.DISTILLATION_TOWER.get(), DistillationTowerScreen::new);
        event.register(ModMenus.CRYO_MIXER.get(), CryoMixerScreen::new);
        event.register(ModMenus.CARD_CATALOG.get(), CardCatalogScreen::new);
        event.register(ModMenus.CONCENTRATOR.get(), ConcentratorScreen::new);
        event.register(ModMenus.TELEGRAPH.get(), TelegraphScreen::new);
    }

    /** The runes of the mod's blocks glow (see GlowModels). */
    @SubscribeEvent
    public static void wrapModels(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult event) {
        GlowModels.wrap(event.getBakingResult(), event.getTextureGetter());
    }

    /** A written punch card shows its result while Shift is held (items/copper_punch_card.json). */
    @SubscribeEvent
    public static void registerItemModels(net.neoforged.neoforge.client.event.RegisterItemModelsEvent event) {
        event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "punch_card"), PunchCardModel.Unbaked.MAP_CODEC);
    }

    /** Liquid Cryo-Source textures (the fluid in the world, tanks, basins, JEI, the planner). */
    @SubscribeEvent
    public static void registerFluidModels(net.neoforged.neoforge.client.event.RegisterFluidModelsEvent event) {
        var still = net.minecraft.resources.Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/cryo_source_still");
        var flow = net.minecraft.resources.Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/cryo_source_flow");
        event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                        new net.minecraft.client.resources.model.sprite.Material(still), new net.minecraft.client.resources.model.sprite.Material(flow),
                        null, (net.neoforged.neoforge.client.fluid.FluidTintSource) null),
                com.golemlogix.init.ModFluids.CRYO_SOURCE, com.golemlogix.init.ModFluids.CRYO_SOURCE_FLOWING);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(com.golemlogix.client.ghost.GolemKeys.CATEGORY);
        event.register(com.golemlogix.client.ghost.GolemKeys.GHOST_CONFIRM);
        event.register(com.golemlogix.client.ghost.GolemKeys.GHOST_CANCEL);
        event.register(com.golemlogix.client.ghost.GolemKeys.OPEN_CHRONOMETER);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "ghost_hud"),
                com.golemlogix.client.ghost.GhostHud::render);
    }
}
