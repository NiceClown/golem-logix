package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
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
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

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

    /** Texture -> its glow layer (empty if there is none). The key is the sprite itself: after a resource reload the sprites are new. */
    private static final Map<TextureAtlasSprite, Optional<TextureAtlasSprite>> SPRITES = new ConcurrentHashMap<>();
    /** Quad -> its glowing copy (empty if the texture has no glow). */
    private static final Map<BakedQuad, Optional<BakedQuad>> QUADS = new ConcurrentHashMap<>();
    /** Fast path for single-part models (most blocks): avoids creating a List key per collectParts call. */
    private static final Map<BlockStateModelPart, Optional<GlowPart>> SINGLE_PART = new ConcurrentHashMap<>();
    /** Multi-part model parts -> their glow part (empty if nothing glows). */
    private static final Map<List<BlockStateModelPart>, Optional<GlowPart>> PARTS = new ConcurrentHashMap<>();
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
            addGlow(parts, from);
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
}
