package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.AtlasConduitBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A light glow of the runes on all machines, plus smooth cross-block C1 Hermite colour gradients on multi-tone Atlas pillars.
 * Next to a texture X that has runes and glowing inlays the texture generator puts a layer {@code X_glow} (only the glowing
 * pixels). The mod's models are wrapped: on top of every quad with such a texture a copy with the glow layer is drawn, unshaded
 * and with its own light, so the runes are visible at night and slightly brighter than the metal around them by day.
 */
public final class GlowModels {
    /** Light of the copy (0..15): a "light" glow, not a floodlight. */
    private static final int GLOW_LIGHT = 12;
    private static final float LIFT = 0.0008f;
    private static final float GRAD_LIFT = 0.002f;
    private static final float GRAD_SPAN = 6.5f / 16f;
    private static final int GRAD_STRIPS = 4;

    enum PillarTone {
        ICE("block/conduit_ice"),
        BLUE("block/conduit_blue"),
        QUANTUM("block/conduit_quantum");

        final ResourceLocation spriteId;

        PillarTone(String path) {
            this.spriteId = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
        }
    }

    private record NeighborTones(@Nullable PillarTone down, @Nullable PillarTone up) {
    }

    private record GradientKey(BakedModel model, BlockState state, PillarTone self,
                               @Nullable PillarTone down, @Nullable PillarTone up) {
    }

    private static final ModelProperty<NeighborTones> NEIGHBOR_TONES = new ModelProperty<>();

    /** Texture -> its glow layer (empty if there is none). The key is the sprite itself: after a resource reload the sprites are new. */
    private static final Map<TextureAtlasSprite, Optional<TextureAtlasSprite>> SPRITES = new ConcurrentHashMap<>();
    /** Quad -> its glowing copy (empty if the texture has no glow). */
    private static final Map<BakedQuad, Optional<BakedQuad>> QUADS = new ConcurrentHashMap<>();
    /** Cached cross-block vertical colour-gradient overlay quads for multi-tone Atlas pillars. */
    private static final Map<GradientKey, List<BakedQuad>> GRADIENTS = new ConcurrentHashMap<>();

    private GlowModels() {
    }

    /** Wraps the mod's models (called from ModelEvent.ModifyBakingResult). */
    public static void wrap(Map<ModelResourceLocation, BakedModel> models) {
        SPRITES.clear();
        QUADS.clear();
        GRADIENTS.clear();
        models.replaceAll((id, model) -> GolemLogixMod.MOD_ID.equals(id.id().getNamespace()) && !(model instanceof Glowing)
                ? new Glowing(model) : model);
    }

    private static TextureAtlasSprite blockSprite(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(id);
    }

    @Nullable
    private static TextureAtlasSprite glowSprite(TextureAtlasSprite sprite) {
        return SPRITES.computeIfAbsent(sprite, s -> {
            ResourceLocation name = s.contents().name();
            if (!GolemLogixMod.MOD_ID.equals(name.getNamespace()) || name.getPath().endsWith("_glow")) {
                return Optional.empty();
            }
            ResourceLocation file = name.withPath(p -> "textures/" + p + "_glow.png");
            if (Minecraft.getInstance().getResourceManager().getResource(file).isEmpty()) {
                return Optional.empty();
            }
            TextureAtlasSprite g = blockSprite(name.withSuffix("_glow"));
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

    private static final Direction[] DIRECTIONS = Direction.values();

    private static boolean hasGlowingQuad(List<BakedQuad> quads) {
        for (BakedQuad q : quads) {
            if (glowSprite(q.getSprite()) != null) {
                return true;
            }
        }
        return false;
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

    @Nullable
    private static PillarTone toneOf(@Nullable BlockState state) {
        if (state == null) {
            return null;
        }
        if (state.getBlock() instanceof AtlasConduitBlock) {
            return PillarTone.ICE;
        }
        if (state.getBlock() instanceof ChannelExpanderBlock exp && state.getValue(ChannelExpanderBlock.FORMED)) {
            return exp.isQuantum() ? PillarTone.QUANTUM : PillarTone.BLUE;
        }
        return null;
    }

    private static float hermite(float u) {
        return u * u * (3f - 2f * u);
    }

    private static List<BakedQuad> buildGradient(GradientKey key) {
        TextureAtlasSprite baseSprite = blockSprite(key.self().spriteId);
        TextureAtlasSprite downSprite = key.down() != null ? blockSprite(key.down().spriteId) : null;
        TextureAtlasSprite upSprite = key.up() != null ? blockSprite(key.up().spriteId) : null;
        if (baseSprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())
                || (downSprite == null && upSprite == null)) {
            return List.of();
        }
        RandomSource probe = RandomSource.create(42L);
        List<BakedQuad> baseQuads = key.model().getQuads(key.state(), null, probe, ModelData.EMPTY, null);
        if (baseQuads.isEmpty()) {
            return List.of();
        }
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad q : baseQuads) {
            if (!q.getDirection().getAxis().isHorizontal()
                    || !q.getSprite().contents().name().equals(key.self().spriteId)) {
                continue;
            }
            if (downSprite != null && !downSprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                for (int s = 0; s < GRAD_STRIPS; s++) {
                    float f0 = (float) s / GRAD_STRIPS;
                    float f1 = (float) (s + 1) / GRAD_STRIPS;
                    float yLo = GRAD_SPAN * f0;
                    float yHi = GRAD_SPAN * f1;
                    int aLo = Math.round((1f - hermite(0.5f + 0.5f * f0)) * 255f);
                    int aHi = Math.round((1f - hermite(0.5f + 0.5f * f1)) * 255f);
                    out.add(sliceGradientQuad(q, baseSprite, downSprite, yLo, yHi, aLo, aHi));
                }
            }
            if (upSprite != null && !upSprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                for (int s = 0; s < GRAD_STRIPS; s++) {
                    float f0 = (float) s / GRAD_STRIPS;
                    float f1 = (float) (s + 1) / GRAD_STRIPS;
                    float yLo = (1f - GRAD_SPAN) + GRAD_SPAN * f0;
                    float yHi = (1f - GRAD_SPAN) + GRAD_SPAN * f1;
                    int aLo = Math.round(hermite(0.5f * f0) * 255f);
                    int aHi = Math.round(hermite(0.5f * f1) * 255f);
                    out.add(sliceGradientQuad(q, baseSprite, upSprite, yLo, yHi, aLo, aHi));
                }
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static float qx(int[] qv, int idx) {
        return Float.intBitsToFloat(qv[idx * IQuadTransformer.STRIDE + IQuadTransformer.POSITION]);
    }

    private static float qy(int[] qv, int idx) {
        return Float.intBitsToFloat(qv[idx * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1]);
    }

    private static float qz(int[] qv, int idx) {
        return Float.intBitsToFloat(qv[idx * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 2]);
    }

    private static float qu(int[] qv, int idx) {
        return Float.intBitsToFloat(qv[idx * IQuadTransformer.STRIDE + IQuadTransformer.UV0]);
    }

    private static float qv(int[] qv, int idx) {
        return Float.intBitsToFloat(qv[idx * IQuadTransformer.STRIDE + IQuadTransformer.UV0 + 1]);
    }

    private static BakedQuad sliceGradientQuad(BakedQuad q, TextureAtlasSprite baseSprite, TextureAtlasSprite overSprite,
                                               float yLo, float yHi, int aLo, int aHi) {
        int[] src = q.getVertices();
        Direction d = q.getDirection();
        int[] bot = new int[4];
        int[] top = new int[4];
        for (int i = 0; i < 4; i++) {
            float xi = qx(src, i), zi = qz(src, i);
            int bestB = 0, bestT = 0;
            float distB = Float.MAX_VALUE, distT = Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                float dx = qx(src, k) - xi, dz = qz(src, k) - zi;
                float dist = dx * dx + dz * dz;
                if (qy(src, k) < 0.5f) {
                    if (dist < distB) {
                        distB = dist;
                        bestB = k;
                    }
                } else {
                    if (dist < distT) {
                        distT = dist;
                        bestT = k;
                    }
                }
            }
            bot[i] = bestB;
            top[i] = bestT;
        }

        int[] v = src.clone();
        float baseU0 = baseSprite.getU0(), baseDU = baseSprite.getU1() - baseU0;
        float baseV0 = baseSprite.getV0(), baseDV = baseSprite.getV1() - baseV0;
        for (int i = 0; i < 4; i++) {
            boolean isTop = qy(src, i) > 0.5f;
            float ty = isTop ? yHi : yLo;
            int alpha = Mth.clamp(isTop ? aHi : aLo, 0, 255);
            int b = bot[i], t = top[i];

            float px = Mth.lerp(ty, qx(src, b), qx(src, t)) + d.getStepX() * GRAD_LIFT;
            float py = Mth.lerp(ty, qy(src, b), qy(src, t)) + d.getStepY() * GRAD_LIFT;
            float pz = Mth.lerp(ty, qz(src, b), qz(src, t)) + d.getStepZ() * GRAD_LIFT;

            float uNorm = Mth.clamp((qu(src, i) - baseU0) / baseDU, 0f, 1f);
            float vBot = (qv(src, b) - baseV0) / baseDV;
            float vTop = (qv(src, t) - baseV0) / baseDV;
            float vNorm = Mth.clamp(Mth.lerp(ty, vBot, vTop), 0f, 1f);

            int o = i * IQuadTransformer.STRIDE;
            int p = o + IQuadTransformer.POSITION;
            v[p] = Float.floatToRawIntBits(px);
            v[p + 1] = Float.floatToRawIntBits(py);
            v[p + 2] = Float.floatToRawIntBits(pz);
            v[o + IQuadTransformer.COLOR] = (alpha << 24) | 0x00FFFFFF;
            int uv = o + IQuadTransformer.UV0;
            v[uv] = Float.floatToRawIntBits(overSprite.getU(uNorm));
            v[uv + 1] = Float.floatToRawIntBits(overSprite.getV(vNorm));
            v[o + IQuadTransformer.UV2] = LightTexture.FULL_BRIGHT;
        }
        return new BakedQuad(v, -1, d, overSprite, q.isShade(), false);
    }

    /** A mod model with a glow layer. The glow goes into the cutout layer, and for translucent models into the same translucent one. */
    static final class Glowing extends BakedModelWrapper<BakedModel> {
        private record StateGlow(BlockState state, boolean hasGlow) {
        }

        private final List<BakedModel> singlePasses;
        private volatile StateGlow stateGlow;
        private volatile byte itemGlow; // 0 = unknown, 1 = yes, -1 = no

        Glowing(BakedModel original) {
            super(original);
            this.singlePasses = List.of(original, new ItemGlowPass(List.of(original)));
        }

        private static RenderType glowType(ChunkRenderTypeSet types) {
            return types.contains(RenderType.translucent()) ? RenderType.translucent() : RenderType.cutout();
        }

        private boolean hasBlockGlow(BlockState state, ModelData data, ChunkRenderTypeSet types) {
            StateGlow cached = this.stateGlow;
            if (cached != null && cached.state() == state) {
                return cached.hasGlow();
            }
            boolean found = scanBlockGlow(state, data, types);
            this.stateGlow = new StateGlow(state, found);
            return found;
        }

        private boolean scanBlockGlow(BlockState state, ModelData data, ChunkRenderTypeSet types) {
            RandomSource probe = RandomSource.create(42L);
            for (RenderType t : types) {
                if (hasGlowingQuad(originalModel.getQuads(state, null, probe, data, t))) {
                    return true;
                }
                for (Direction d : DIRECTIONS) {
                    if (hasGlowingQuad(originalModel.getQuads(state, d, probe, data, t))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean hasItemGlow() {
            byte g = this.itemGlow;
            if (g != 0) {
                return g > 0;
            }
            boolean found = modelHasItemGlow(originalModel);
            this.itemGlow = (byte) (found ? 1 : -1);
            return found;
        }

        private static boolean modelHasItemGlow(BakedModel model) {
            RandomSource probe = RandomSource.create(42L);
            if (hasGlowingQuad(model.getQuads(null, null, probe))) {
                return true;
            }
            for (Direction d : DIRECTIONS) {
                if (hasGlowingQuad(model.getQuads(null, d, probe))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            ModelData base = super.getModelData(level, pos, state, modelData);
            PillarTone selfTone = toneOf(state);
            if (selfTone == null) {
                return base;
            }
            boolean hasDown = state.getValue(BlockStateProperties.DOWN);
            boolean hasUp = state.getValue(BlockStateProperties.UP);
            if (!hasDown && !hasUp) {
                return base;
            }
            PillarTone downTone = hasDown ? toneOf(level.getBlockState(pos.below())) : null;
            if (downTone == selfTone) {
                downTone = null;
            }
            PillarTone upTone = hasUp ? toneOf(level.getBlockState(pos.above())) : null;
            if (upTone == selfTone) {
                upTone = null;
            }
            if (downTone == null && upTone == null) {
                return base;
            }
            return base.derive().with(NEIGHBOR_TONES, new NeighborTones(downTone, upTone)).build();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            ChunkRenderTypeSet types = originalModel.getRenderTypes(state, rand, data);
            boolean glow = hasBlockGlow(state, data, types);
            NeighborTones tones = data.get(NEIGHBOR_TONES);
            if (!glow && tones == null) {
                return types;
            }
            ChunkRenderTypeSet extra = glow && tones != null
                    ? ChunkRenderTypeSet.of(glowType(types), RenderType.translucent())
                    : (tones != null ? ChunkRenderTypeSet.of(RenderType.translucent()) : ChunkRenderTypeSet.of(glowType(types)));
            return ChunkRenderTypeSet.union(types, extra);
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
            List<BakedQuad> result = own;

            if (renderType == glowType(types) && hasBlockGlow(state, data, types)) {
                List<BakedQuad> out = null;
                for (RenderType t : types) {
                    List<BakedQuad> g = glowing(t == renderType ? own : originalModel.getQuads(state, side, rand, data, t));
                    if (!g.isEmpty()) {
                        if (out == null) {
                            out = new ArrayList<>(own.size() + g.size());
                            out.addAll(own);
                        }
                        out.addAll(g);
                    }
                }
                if (out != null) {
                    result = out;
                }
            }

            if (renderType == RenderType.translucent() && side == null) {
                NeighborTones tones = data.get(NEIGHBOR_TONES);
                if (tones != null) {
                    PillarTone selfTone = toneOf(state);
                    if (selfTone != null) {
                        GradientKey key = new GradientKey(originalModel, state, selfTone, tones.down(), tones.up());
                        List<BakedQuad> grad = GRADIENTS.computeIfAbsent(key, GlowModels::buildGradient);
                        if (!grad.isEmpty()) {
                            if (result.isEmpty()) {
                                return grad;
                            }
                            List<BakedQuad> combined = new ArrayList<>(result.size() + grad.size());
                            combined.addAll(result);
                            combined.addAll(grad);
                            return combined;
                        }
                    }
                }
            }
            return result;
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            List<BakedModel> base = originalModel.getRenderPasses(stack, fabulous);
            if (base.isEmpty()) {
                return base;
            }
            if (base.size() == 1 && base.get(0) == originalModel) {
                return hasItemGlow() ? singlePasses : base;
            }
            boolean anyGlow = false;
            for (BakedModel m : base) {
                if (modelHasItemGlow(m)) {
                    anyGlow = true;
                    break;
                }
            }
            if (!anyGlow) {
                return base;
            }
            List<BakedModel> passes = new ArrayList<>(base.size() + 1);
            passes.addAll(base);
            passes.add(new ItemGlowPass(List.copyOf(base)));
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
            if (passes.size() == 1) {
                return glowing(passes.get(0).getQuads(state, side, rand));
            }
            List<BakedQuad> out = null;
            for (BakedModel m : passes) {
                List<BakedQuad> g = glowing(m.getQuads(state, side, rand));
                if (!g.isEmpty()) {
                    if (out == null) {
                        out = new ArrayList<>(g.size());
                    }
                    out.addAll(g);
                }
            }
            return out != null ? out : List.of();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                        @Nullable RenderType renderType) {
            return getQuads(state, side, rand);
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
