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
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CopperGolemPuppet.LAYER, CopperGolemPuppet::createLayer);
    }

    /** Moving parts from separate models: coprocessor fins, interface turbines, atlant head, pipe plugs. */
    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(CoolingCoprocessorRenderer.FIN);
        event.register(InterfaceRenderer.TURBINE);
        event.register(InterfaceRenderer.PENDULUM);
        event.register(AtlantHeadRenderer.HEAD);
        event.register(SnowPipeRenderer.PLUG_BASIC);
        event.register(SnowPipeRenderer.PLUG_DENSE);
        event.register(SnowPipeRenderer.DOCK_BASIC);
        event.register(SnowPipeRenderer.DOCK_DENSE);
        event.register(SnowPipeRenderer.BAND_BASIC);
        event.register(SnowPipeRenderer.BAND_DENSE);
    }

    /** Rune glow layers on top of the mod's models. */
    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        GlowModels.wrap(event.getModels());
        // a written punch card shows its result while Shift is held
        var card = net.minecraft.client.resources.model.ModelResourceLocation.inventory(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "copper_punch_card"));
        event.getModels().computeIfPresent(card, (k, m) -> new PunchCardModel(m));
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
        event.register(ModMenus.CARD_CATALOG.get(), CardCatalogScreen::new);
        event.register(ModMenus.CONCENTRATOR.get(), ConcentratorScreen::new);
        event.register(ModMenus.TELEGRAPH.get(), TelegraphScreen::new);
    }

    /** Liquid Cryo-Source textures (tanks, basins, JEI, the planner). */
    @SubscribeEvent
    public static void registerClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        var still = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/cryo_source_still");
        var flow = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/cryo_source_flow");
        event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
            @Override
            public net.minecraft.resources.ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                return flow;
            }
        }, com.golemlogix.init.ModFluids.CRYO_SOURCE_TYPE.get());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(com.golemlogix.client.ghost.GolemKeys.GHOST_CONFIRM);
        event.register(com.golemlogix.client.ghost.GolemKeys.GHOST_CANCEL);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "ghost_hud"),
                com.golemlogix.client.ghost.GhostHud::render);
    }
}
