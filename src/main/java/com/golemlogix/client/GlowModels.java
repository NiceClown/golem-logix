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
 * Лёгкое свечение рун на всех машинах. Генератор текстур рядом с текстурой X, на которой есть руны и светящиеся вставки,
 * кладёт слой {@code X_glow} (только светящиеся пиксели). Модели мода оборачиваются: поверх каждого четырёхугольника с
 * такой текстурой рисуется его копия со слоем свечения — без затенения и со своим светом, поэтому ночью руны видны, а днём
 * чуть ярче металла вокруг. Копия на волос сдвинута наружу по нормали, чтобы лечь поверх и полупрозрачных граней.
 */
public final class GlowModels {
    /** Свет копии (0..15): «лёгкое» свечение, не прожектор. */
    private static final int GLOW_LIGHT = 12;
    private static final float LIFT = 0.0008f;

    /** Текстура -> её слой свечения (пусто, если слоя нет). Ключ — сам спрайт: после перезагрузки ресурсов спрайты новые. */
    private static final Map<TextureAtlasSprite, Optional<TextureAtlasSprite>> SPRITES = new ConcurrentHashMap<>();
    /** Четырёхугольник -> его светящаяся копия (пусто, если текстура без свечения). */
    private static final Map<BakedQuad, Optional<BakedQuad>> QUADS = new ConcurrentHashMap<>();

    private GlowModels() {
    }

    /** Оборачивает модели мода (вызывается из ModelEvent.ModifyBakingResult). */
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

    /** Модель мода со слоем свечения. Свечение идёт в слой cutout, а у полупрозрачных моделей — в тот же translucent. */
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
            // без состояния (модель рисует отрисовщик блок-сущности в свой буфер) или без слоя — свечение не добавляем:
            // копии с прозрачными пикселями в чужом, например сплошном, буфере легли бы чёрными пятнами
            if (renderType == null || state == null) {
                return originalModel.getQuads(state, side, rand, data, renderType);
            }
            ChunkRenderTypeSet types = originalModel.getRenderTypes(state, rand, data);
            List<BakedQuad> own = types.contains(renderType) ? originalModel.getQuads(state, side, rand, data, renderType) : List.of();
            if (renderType != glowType(types)) {
                return own;
            }
            // слой свечения: копии всех граней модели (из всех её слоёв) поверх своих граней этого слоя
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

    /** Отдельный проход предмета: только светящиеся копии граней всех проходов, слой cutout. */
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
