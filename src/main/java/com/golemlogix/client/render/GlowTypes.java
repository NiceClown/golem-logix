package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * The 1.21.1 "eyes": full bright and added to what is behind, so a black texel adds nothing. Since 26.1 vanilla eyes blend as
 * translucent instead, and the mod's holograms (textures with a black background instead of transparency) turned into dark squares.
 */
public final class GlowTypes {
    public static final RenderPipeline ADDITIVE_EYES = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "pipeline/additive_eyes"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();

    private static final Function<Identifier, RenderType> GLOW = Util.memoize(texture -> RenderType.create("golem_logix_glow",
            RenderSetup.builder(ADDITIVE_EYES).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup()));

    private GlowTypes() {
    }

    /**
     * Shader packs (Iris) do not run this mod's own pipeline: the glow vanished or showed through walls. With Iris the same
     * geometry goes through the game's emissive translucent type, on a copy of the texture whose brightness is its alpha
     * ("<name>_alpha.png").
     */
    private static final boolean IRIS = net.neoforged.fml.ModList.get().isLoaded("iris");
    private static final Function<Identifier, RenderType> SHADER_SAFE = Util.memoize(texture ->
            net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(Identifier.fromNamespaceAndPath(
                    texture.getNamespace(), texture.getPath().replace(".png", "_alpha.png"))));

    /** Glowing geometry with this texture: black is see-through, everything else lights up what is behind it. */
    public static RenderType glow(Identifier texture) {
        return IRIS ? SHADER_SAFE.apply(texture) : GLOW.apply(texture);
    }
}
