package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Models the renderers draw by hand (turbines, fins, the atlant head, pipe plugs and bands): baked as standalone quad collections and
 * drawn quad by quad into a buffer.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public final class StandaloneModels {
    private static final Map<Identifier, StandaloneModelKey<QuadCollection>> KEYS = new LinkedHashMap<>();

    public static final Identifier FIN = add("block/cooling_coprocessor_fin");
    public static final Identifier TURBINE = add("block/golem_interface_turbine");
    public static final Identifier PENDULUM = add("block/golem_interface_pendulum");
    public static final Identifier ATLANT_HEAD = add("block/atlas_atlant_head");
    public static final Identifier ATLAS_ICE_COLUMN = add("block/atlas_ice_column");
    public static final Identifier ATLAS_ICE_COLUMN_TOP = add("block/atlas_ice_column_top");
    public static final Identifier ATLAS_ICICLES = add("block/atlas_icicles");
    public static final Identifier ATLAS_RAIL = add("block/atlas_rail");
    public static final Identifier ATLAS_RAIL_END = add("block/atlas_rail_end");
    public static final Identifier PLUG_BASIC = add("block/basic_snow_pipe_plug");
    public static final Identifier PLUG_DENSE = add("block/dense_frost_pipe_plug");
    public static final Identifier BAND_BASIC = add("block/basic_snow_pipe_band");
    public static final Identifier BAND_DENSE = add("block/dense_frost_pipe_band");
    public static final Identifier DOCK_BASIC = add("block/basic_snow_pipe_dock");
    public static final Identifier DOCK_DENSE = add("block/dense_frost_pipe_dock");

    private StandaloneModels() {
    }

    private static Identifier add(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
        KEYS.put(id, new StandaloneModelKey<>(id::toString));
        return id;
    }

    @SubscribeEvent
    public static void register(ModelEvent.RegisterStandalone event) {
        KEYS.forEach((id, key) -> event.register(key, SimpleUnbakedStandaloneModel.quadCollection(id)));
    }

    public static List<BakedQuad> quads(Identifier model) {
        StandaloneModelKey<QuadCollection> key = KEYS.get(model);
        QuadCollection q = key == null ? null : Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        return q == null ? List.of() : q.getAll();
    }

    /** All the model's quads into the buffer, tinted, at the given light (was ModelBlockRenderer#renderModel). */
    public static void render(PoseStack.Pose pose, VertexConsumer buffer, Identifier model, float r, float g, float b, int light, int overlay) {
        com.mojang.blaze3d.vertex.QuadInstance instance = new com.mojang.blaze3d.vertex.QuadInstance();
        instance.setColor(net.minecraft.util.ARGB.colorFromFloat(1f, r, g, b));
        instance.setLightCoords(light);
        instance.setOverlayCoords(overlay);
        for (BakedQuad quad : quads(model)) {
            buffer.putBakedQuad(pose, quad, instance);
        }
    }
}
