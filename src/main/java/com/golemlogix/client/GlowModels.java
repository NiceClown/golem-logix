package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A light glow of the runes on all machines. Next to a texture X that has runes and glowing inlays the texture generator
 * puts a layer {@code X_glow} (only the glowing pixels). The mod's models are wrapped: on top of every quad with
 * such a texture a copy with the glow layer is drawn, unshaded and with its own light, so the runes are visible at night and
 * slightly brighter than the metal around them by day. The copy is pushed out a hair along the normal to lie over translucent faces too.
 */
public final class GlowModels {
    /** Light of the copy (0..15): a "light" glow, not a floodlight. */
    private static final int GLOW_LIGHT = 12;
    private static final float LIFT = 0.0008f;

    /** Texture -> its glow layer (empty if there is none). The key is the sprite itself: after a resource reload the sprites are new. */
    private static final Map<TextureAtlasSprite, Optional<TextureAtlasSprite>> SPRITES = new ConcurrentHashMap<>();
    /** Quad -> its glowing copy (empty if the texture has no glow). */
    private static final Map<BakedQuad, Optional<BakedQuad>> QUADS = new ConcurrentHashMap<>();

    private GlowModels() {
    }

    /** Wraps the mod's models (called from ModelEvent.ModifyBakingResult). */
    public static void wrap(Map<ModelResourceLocation, BakedModel> models) {
        SPRITES.clear();
        QUADS.clear();
        models.replaceAll((id, model) -> GolemLogixMod.MOD_ID.equals(id.id().getNamespace()) && !(model instanceof Glowing)
                ? new Glowing(model) : model);
    }

    @Nullable
    private static TextureAtlasSprite glowSprite(TextureAtlasSprite sprite) {
        return SPRITES.computeIfAbsent(sprite, s -> {
            ResourceLocation name = s.contents().name();
            if (!GolemLogixMod.MOD_ID.equals(name.getNamespace()) || name.getPath().endsWith("_glow")) {
                return Optional.empty();
            }
            TextureAtlasSprite g = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                    .getSprite(name.withSuffix("_glow"));
            return g.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? Optional.empty() : Optional.of(g);
        }).orElse(null);
    }

    @Nullable
    private static BakedQuad glowOf(BakedQuad quad) {
        return QUADS.computeIfAbsent(quad, q -> {
            TextureAtlasSprite base = q.getSprite(), glow = glowSprite(base);
            if (glow == null) {
                return Optional.empty();
            }
            int[] v = q.getVertices().clone();
            Direction d = q.getDirection();
            for (int i = 0; i < 4; i++) {
                int o = i * IQuadTransformer.STRIDE;
                int p = o + IQuadTransformer.POSITION;
                v[p] = Float.floatToRawIntBits(Float.intBitsToFloat(v[p]) + d.getStepX() * LIFT);
                v[p + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[p + 1]) + d.getStepY() * LIFT);
                v[p + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(v[p + 2]) + d.getStepZ() * LIFT);
                int uv = o + IQuadTransformer.UV0;
                v[uv] = Float.floatToRawIntBits(glow.getU(base.getUOffset(Float.intBitsToFloat(v[uv]))));
                v[uv + 1] = Float.floatToRawIntBits(glow.getV(base.getVOffset(Float.intBitsToFloat(v[uv + 1]))));
                v[o + IQuadTransformer.COLOR] = 0xFFFFFFFF;
            }
            BakedQuad out = new BakedQuad(v, -1, d, glow, false, false);
            QuadTransformers.settingEmissivity(GLOW_LIGHT).processInPlace(out);
            return Optional.of(out);
        }).orElse(null);
    }

    private static List<BakedQuad> glowing(List<BakedQuad> quads) {
        List<BakedQuad> out = null;
        for (BakedQuad q : quads) {
            BakedQuad g = glowOf(q);
            if (g != null) {
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(g);
            }
        }
        return out == null ? List.of() : out;
    }

    /** A mod model with a glow layer. The glow goes into the cutout layer, and for translucent models into the same translucent one. */
    static final class Glowing extends BakedModelWrapper<BakedModel> {
        Glowing(BakedModel original) {
            super(original);
        }

        private static RenderType glowType(ChunkRenderTypeSet types) {
            return types.contains(RenderType.translucent()) ? RenderType.translucent() : RenderType.cutout();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            ChunkRenderTypeSet types = originalModel.getRenderTypes(state, rand, data);
            return ChunkRenderTypeSet.union(types, ChunkRenderTypeSet.of(glowType(types)));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                        @Nullable RenderType renderType) {
            // without a state (a block entity renderer draws the model into its own buffer) or without a layer, no glow is added:
            // copies with transparent pixels in someone else's buffer, for example a solid one, would show up as black spots
            if (renderType == null || state == null) {
                return originalModel.getQuads(state, side, rand, data, renderType);
            }
            ChunkRenderTypeSet types = originalModel.getRenderTypes(state, rand, data);
            List<BakedQuad> own = types.contains(renderType) ? originalModel.getQuads(state, side, rand, data, renderType) : List.of();
            if (renderType != glowType(types)) {
                return own;
            }
            // glow layer: copies of all the model's faces (from all its layers) on top of its own faces of this layer
            List<BakedQuad> out = new ArrayList<>(own);
            for (RenderType t : types) {
                out.addAll(glowing(t == renderType ? own : originalModel.getQuads(state, side, rand, data, t)));
            }
            return out;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            List<BakedModel> passes = new ArrayList<>(originalModel.getRenderPasses(stack, fabulous));
            passes.add(new ItemGlowPass(List.copyOf(passes)));
            return passes;
        }
    }

    /** A separate item pass: only the glowing copies of the faces of all passes, in the cutout layer. */
    static final class ItemGlowPass extends BakedModelWrapper<BakedModel> {
        private final List<BakedModel> passes;

        ItemGlowPass(List<BakedModel> passes) {
            super(passes.get(0));
            this.passes = passes;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            List<BakedQuad> out = new ArrayList<>();
            for (BakedModel m : passes) {
                out.addAll(glowing(m.getQuads(state, side, rand)));
            }
            return out;
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return List.of(RenderTypeHelper.getEntityRenderType(RenderType.cutout(), fabulous));
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }
    }
}
