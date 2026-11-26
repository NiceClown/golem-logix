package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blocks.AtlasConduitBlock;
import com.golemlogix.blocks.ChannelExpanderBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A light glow of the runes on all machines. Next to a texture X that has runes and glowing inlays the texture generator
 * puts a layer {@code X_glow} (only the glowing pixels). The mod's block models are wrapped: on top of every quad with
 * such a texture a copy with the glow layer is drawn, unshaded and with its own light, so the runes are visible at night and
 * slightly brighter than the metal around them by day. The copy is pushed out a hair along the normal to lie over translucent faces too.
 * Since 26.1 every quad carries its own layer and light, so the copies simply go into the cutout layer (the translucent one for
 * translucent faces).
 */
public final class GlowModels {
    /** Light of the copy (0..15): a "light" glow, not a floodlight. */
    private static final int GLOW_LIGHT = 12;
    private static final float LIFT = 0.0008f;
    private static final float GRAD_LIFT = 0.0006f;
    private static final float GRAD_SPAN = 6.5f / 16f;
    private static final int GRAD_STRIPS = 4;

    enum PillarTone {
        ICE("block/conduit_ice"),
        BLUE("block/conduit_blue"),
        QUANTUM("block/conduit_quantum");

        final Identifier spriteId;

        PillarTone(String path) {
            this.spriteId = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
        }
    }

    private record GradientKey(BlockStateModelPart part, PillarTone self, @Nullable PillarTone down, @Nullable PillarTone up) {
    }

    /** Texture -> its glow layer (empty if there is none). The key is the sprite itself: after a resource reload the sprites are new. */
    private static final Map<TextureAtlasSprite, Optional<TextureAtlasSprite>> SPRITES = new ConcurrentHashMap<>();
    /** Quad -> its glowing copy (empty if the texture has no glow). */
    private static final Map<BakedQuad, Optional<BakedQuad>> QUADS = new ConcurrentHashMap<>();
    /** Fast path for single-part models (most blocks): avoids creating a List key per collectParts call. */
    private static final Map<BlockStateModelPart, Optional<GlowPart>> SINGLE_PART = new ConcurrentHashMap<>();
    /** Multi-part model parts -> their glow part (empty if nothing glows). */
    private static final Map<List<BlockStateModelPart>, Optional<GlowPart>> PARTS = new ConcurrentHashMap<>();
    /** Cached cross-block vertical color-gradient overlay parts for multi-tone Atlas pillars. */
    private static final Map<GradientKey, Optional<PillarGradientPart>> GRADIENTS = new ConcurrentHashMap<>();
    /** Written on the model-reload worker thread and read by chunk-meshing threads. */
    private static volatile Function<Identifier, TextureAtlasSprite> textures = id -> null;

    private GlowModels() {
    }

    /** Wraps the mod's block models (called from ModelEvent.ModifyBakingResult with its texture getter). */
    public static void wrap(ModelBakery.BakingResult result, Function<Identifier, TextureAtlasSprite> textureGetter) {
        SPRITES.clear();
        QUADS.clear();
        SINGLE_PART.clear();
        PARTS.clear();
        GRADIENTS.clear();
        textures = textureGetter;
        result.blockStateModels().replaceAll((state, model) ->
                GolemLogixMod.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()) && !(model instanceof Glowing)
                        ? new Glowing(model) : model);
    }

    @Nullable
    private static TextureAtlasSprite glowSprite(TextureAtlasSprite sprite) {
        return SPRITES.computeIfAbsent(sprite, s -> {
            Identifier name = s.contents().name();
            if (!GolemLogixMod.MOD_ID.equals(name.getNamespace()) || name.getPath().endsWith("_glow")) {
                return Optional.empty();
            }
            // most textures have no glow layer: ask the atlas only for one that exists (a missing sprite is logged with a stack trace)
            Identifier file = name.withPath(p -> "textures/" + p + "_glow.png");
            if (Minecraft.getInstance().getResourceManager().getResource(file).isEmpty()) {
                return Optional.empty();
            }
            TextureAtlasSprite g = textures.apply(name.withSuffix("_glow"));
            return g == null || g.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? Optional.empty() : Optional.of(g);
        }).orElse(null);
    }

    @Nullable
    private static BakedQuad glowOf(BakedQuad quad) {
        return QUADS.computeIfAbsent(quad, q -> {
            TextureAtlasSprite base = q.materialInfo().sprite(), glow = glowSprite(base);
            if (glow == null) {
                return Optional.empty();
            }
            Direction d = q.direction();
            Vector3f[] pos = new Vector3f[4];
            long[] uv = new long[4];
            for (int i = 0; i < 4; i++) {
                pos[i] = new Vector3f(q.position(i)).add(d.getStepX() * LIFT, d.getStepY() * LIFT, d.getStepZ() * LIFT);
                float u = (UVPair.unpackU(q.packedUV(i)) - base.getU0()) / (base.getU1() - base.getU0());
                float v = (UVPair.unpackV(q.packedUV(i)) - base.getV0()) / (base.getV1() - base.getV0());
                uv[i] = UVPair.pack(glow.getU(u), glow.getV(v));
            }
            boolean translucent = q.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
            BakedQuad.MaterialInfo info = new BakedQuad.MaterialInfo(glow, translucent ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT,
                    translucent ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet(), -1, false, GLOW_LIGHT, false);
            return Optional.of(new BakedQuad(pos[0], pos[1], pos[2], pos[3], uv[0], uv[1], uv[2], uv[3], d, info, q.bakedNormals(),
                    BakedColors.DEFAULT));
        }).orElse(null);
    }

    /** A mod block model with a glow part: the glowing copies of the faces of all its parts. */
    static final class Glowing extends DelegateBlockStateModel {
        Glowing(BlockStateModel original) {
            super(original);
        }

        @Override
        @Deprecated
        public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
            int from = parts.size();
            super.collectParts(random, parts);
            addGlow(parts, from);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            int from = parts.size();
            super.collectParts(level, pos, state, random, parts);
            int baseEnd = parts.size();
            addGlow(parts, from);
            addPillarGradient(level, pos, state, parts, from, baseEnd);
        }

        private static void addPillarGradient(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                              List<BlockStateModelPart> parts, int from, int baseEnd) {
            PillarTone selfTone = toneOf(state);
            if (selfTone == null || baseEnd <= from) {
                return;
            }
            boolean hasDown = state.getValue(BlockStateProperties.DOWN);
            boolean hasUp = state.getValue(BlockStateProperties.UP);
            if (!hasDown && !hasUp) {
                return;
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
                return;
            }
            for (int i = from; i < baseEnd; i++) {
                BlockStateModelPart candidate = parts.get(i);
                if (hasPillarCoreQuads(candidate, selfTone.spriteId)) {
                    GradientKey key = new GradientKey(candidate, selfTone, downTone, upTone);
                    Optional<PillarGradientPart> grad = GRADIENTS.computeIfAbsent(key, GlowModels::buildGradient);
                    grad.ifPresent(parts::add);
                    break;
                }
            }
        }

        private static void addGlow(List<BlockStateModelPart> parts, int from) {
            int count = parts.size() - from;
            if (count <= 0) {
                return;
            }
            if (count == 1) {
                BlockStateModelPart single = parts.get(from);
                Optional<GlowPart> cached = SINGLE_PART.get(single);
                if (cached == null) {
                    GlowPart glow = new GlowPart(List.of(single));
                    cached = glow.hasAny() ? Optional.of(glow) : Optional.empty();
                    SINGLE_PART.putIfAbsent(single, cached);
                }
                if (cached.isPresent()) {
                    parts.add(cached.get());
                }
                return;
            }
            List<BlockStateModelPart> view = from == 0 ? parts : parts.subList(from, parts.size());
            Optional<GlowPart> cached = PARTS.get(view);
            if (cached == null) {
                List<BlockStateModelPart> copy = List.copyOf(view);
                GlowPart glow = new GlowPart(copy);
                cached = glow.hasAny() ? Optional.of(glow) : Optional.empty();
                PARTS.putIfAbsent(copy, cached);
            }
            if (cached.isPresent()) {
                parts.add(cached.get());
            }
        }
    }

    /** Only the glowing copies of the faces of the parts, unshaded and without ambient occlusion. */
    static final class GlowPart implements BlockStateModelPart {
        private static final Direction[] SIDES = Direction.values();
        private final Material.Baked particleMaterial;
        @SuppressWarnings("unchecked")
        private final List<BakedQuad>[] sides = new List[SIDES.length];
        private final List<BakedQuad> unculled;
        private final int materialFlags;

        GlowPart(List<BlockStateModelPart> parts) {
            this.particleMaterial = parts.get(0).particleMaterial();
            this.unculled = glowing(parts, null);
            int flags = hasTranslucent(unculled) ? BakedQuad.FLAG_TRANSLUCENT : 0;
            for (Direction d : SIDES) {
                List<BakedQuad> list = glowing(parts, d);
                sides[d.ordinal()] = list;
                if (flags == 0 && hasTranslucent(list)) {
                    flags = BakedQuad.FLAG_TRANSLUCENT;
                }
            }
            this.materialFlags = flags;
        }

        private static List<BakedQuad> glowing(List<BlockStateModelPart> parts, @Nullable Direction side) {
            List<BakedQuad> out = null;
            for (BlockStateModelPart p : parts) {
                for (BakedQuad q : p.getQuads(side)) {
                    BakedQuad g = glowOf(q);
                    if (g != null) {
                        if (out == null) {
                            out = new ArrayList<>();
                        }
                        out.add(g);
                    }
                }
            }
            return out == null ? List.of() : out;
        }

        private static boolean hasTranslucent(List<BakedQuad> quads) {
            for (BakedQuad q : quads) {
                if (q.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT) {
                    return true;
                }
            }
            return false;
        }

        boolean hasAny() {
            if (!unculled.isEmpty()) {
                return true;
            }
            for (List<BakedQuad> l : sides) {
                if (!l.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? unculled : sides[direction.ordinal()];
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public TriState ambientOcclusion() {
            return TriState.FALSE;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particleMaterial;
        }

        @Override
        public int materialFlags() {
            return materialFlags;
        }
    }

    @Nullable
    private static PillarTone toneOf(BlockState state) {
        if (state.getBlock() instanceof AtlasConduitBlock) {
            return PillarTone.ICE;
        }
        if (state.getBlock() instanceof ChannelExpanderBlock exp && state.getValue(ChannelExpanderBlock.FORMED)) {
            return exp.isQuantum() ? PillarTone.QUANTUM : PillarTone.BLUE;
        }
        return null;
    }

    private static boolean hasPillarCoreQuads(BlockStateModelPart part, Identifier spriteId) {
        for (BakedQuad q : part.getQuads(null)) {
            if (q.direction().getAxis().isHorizontal() && q.materialInfo().sprite().contents().name().equals(spriteId)) {
                return true;
            }
        }
        return false;
    }

    private static float hermite(float u) {
        return u * u * (3f - 2f * u);
    }

    private static Optional<PillarGradientPart> buildGradient(GradientKey key) {
        TextureAtlasSprite baseSprite = textures.apply(key.self().spriteId);
        TextureAtlasSprite downSprite = key.down() != null ? textures.apply(key.down().spriteId) : null;
        TextureAtlasSprite upSprite = key.up() != null ? textures.apply(key.up().spriteId) : null;
        if (baseSprite == null || (downSprite == null && upSprite == null)) {
            return Optional.empty();
        }
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad q : key.part().getQuads(null)) {
            if (!q.direction().getAxis().isHorizontal()
                    || !q.materialInfo().sprite().contents().name().equals(key.self().spriteId)) {
                continue;
            }
            if (downSprite != null) {
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
            if (upSprite != null) {
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
        return out.isEmpty() ? Optional.empty() : Optional.of(new PillarGradientPart(key.part().particleMaterial(), List.copyOf(out)));
    }

    private static BakedQuad sliceGradientQuad(BakedQuad q, TextureAtlasSprite baseSprite, TextureAtlasSprite overSprite,
                                               float yLo, float yHi, int aLo, int aHi) {
        Direction d = q.direction();
        int[] bot = new int[4];
        int[] top = new int[4];
        for (int i = 0; i < 4; i++) {
            float xi = q.position(i).x(), zi = q.position(i).z();
            int bestB = 0, bestT = 0;
            float distB = Float.MAX_VALUE, distT = Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                float dx = q.position(k).x() - xi, dz = q.position(k).z() - zi;
                float dist = dx * dx + dz * dz;
                if (q.position(k).y() < 0.5f) {
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

        Vector3f[] pos = new Vector3f[4];
        long[] uv = new long[4];
        int[] col = new int[4];
        float baseU0 = baseSprite.getU0(), baseDU = baseSprite.getU1() - baseU0;
        float baseV0 = baseSprite.getV0(), baseDV = baseSprite.getV1() - baseV0;
        for (int i = 0; i < 4; i++) {
            boolean isTop = q.position(i).y() > 0.5f;
            float ty = isTop ? yHi : yLo;
            int alpha = isTop ? aHi : aLo;
            int b = bot[i], t = top[i];
            Vector3fc pb = q.position(b), pt = q.position(t);
            pos[i] = new Vector3f(
                    Mth.lerp(ty, pb.x(), pt.x()) + d.getStepX() * GRAD_LIFT,
                    Mth.lerp(ty, pb.y(), pt.y()) + d.getStepY() * GRAD_LIFT,
                    Mth.lerp(ty, pb.z(), pt.z()) + d.getStepZ() * GRAD_LIFT);
            float uNorm = (UVPair.unpackU(q.packedUV(i)) - baseU0) / baseDU;
            float vBot = (UVPair.unpackV(q.packedUV(b)) - baseV0) / baseDV;
            float vTop = (UVPair.unpackV(q.packedUV(t)) - baseV0) / baseDV;
            float vNorm = Mth.lerp(ty, vBot, vTop);
            uv[i] = UVPair.pack(overSprite.getU(uNorm), overSprite.getV(vNorm));
            col[i] = (alpha << 24) | 0x00FFFFFF;
        }
        BakedQuad.MaterialInfo info = new BakedQuad.MaterialInfo(overSprite, ChunkSectionLayer.TRANSLUCENT,
                Sheets.translucentBlockItemSheet(), -1, q.materialInfo().shade(), q.materialInfo().lightEmission(), false);
        return new BakedQuad(pos[0], pos[1], pos[2], pos[3], uv[0], uv[1], uv[2], uv[3], d, info,
                q.bakedNormals(), BakedColors.of(col[0], col[1], col[2], col[3]));
    }

    /** Translucent per-vertex alpha gradient overlay blending neighbouring pillar tones across block boundaries. */
    static final class PillarGradientPart implements BlockStateModelPart {
        private final Material.Baked particleMaterial;
        private final List<BakedQuad> unculled;
        private final int materialFlags;

        PillarGradientPart(Material.Baked particleMaterial, List<BakedQuad> unculled) {
            this.particleMaterial = particleMaterial;
            this.unculled = unculled;
            int flags = 0;
            for (BakedQuad q : unculled) {
                flags |= q.materialInfo().flags();
            }
            this.materialFlags = flags;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return direction == null ? unculled : List.of();
        }

        @Override
        @Deprecated
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public TriState ambientOcclusion() {
            return TriState.FALSE;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particleMaterial;
        }

        @Override
        public int materialFlags() {
            return materialFlags;
        }
    }
}
