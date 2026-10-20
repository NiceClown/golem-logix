package com.golemlogix.recipe;

import com.golemlogix.init.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.golemlogix.system.Recipes;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The anvil ritual, from data packs ("golem_logix:anvil_crushing"): a falling anvil on a battered golem flattens it into the
 * result. The golem is an entity id, a tag ("#golem_logix:copper_golems") or a list; "entity_id_contains" also accepts any
 * entity whose id contains one of the words (golems from other mods without a tag).
 * <pre>
 * {
 *   "type": "golem_logix:anvil_crushing",
 *   "entity": "minecraft:iron_golem",
 *   "entity_id_contains": ["copper_golem"],   optional
 *   "min_health": 0.5,                        optional (default 0.5)
 *   "max_health": 4.0,                        the golem must be battered down to this
 *   "result": {"id": "golem_logix:flattened_iron_core"},
 *   "icon": "minecraft:iron_block"            optional: what JEI shows for the golem (default: its spawn egg)
 * }
 * </pre>
 */
public record AnvilCrushingRecipe(HolderSet<EntityType<?>> entity, List<String> entityIdContains, float minHealth, float maxHealth,
                                  ItemStack result, Optional<Item> icon) implements Recipe<AnvilCrushingRecipe.Golem> {
    public static final MapCodec<AnvilCrushingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(AnvilCrushingRecipe::entity),
            Codec.STRING.listOf().optionalFieldOf("entity_id_contains", List.of()).forGetter(AnvilCrushingRecipe::entityIdContains),
            Codec.floatRange(0f, 1024f).optionalFieldOf("min_health", 0.5f).forGetter(AnvilCrushingRecipe::minHealth),
            Codec.floatRange(0f, 1024f).fieldOf("max_health").forGetter(AnvilCrushingRecipe::maxHealth),
            ItemStack.CODEC.fieldOf("result").forGetter(AnvilCrushingRecipe::result),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("icon").forGetter(AnvilCrushingRecipe::icon)
    ).apply(i, AnvilCrushingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnvilCrushingRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.holderSet(Registries.ENTITY_TYPE), AnvilCrushingRecipe::entity,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AnvilCrushingRecipe::entityIdContains,
            ByteBufCodecs.FLOAT, AnvilCrushingRecipe::minHealth,
            ByteBufCodecs.FLOAT, AnvilCrushingRecipe::maxHealth,
            ItemStack.STREAM_CODEC, AnvilCrushingRecipe::result,
            ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.ITEM)), AnvilCrushingRecipe::icon,
            AnvilCrushingRecipe::new);

    /** The golem under the anvil (the ritual matches its kind; the health window is checked by the handler). */
    public record Golem(EntityType<?> type) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }

        /** Not empty although it has no items: the recipe manager skips empty inputs without looking at the recipes. */
        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    /** The ritual for this entity's kind (whatever its health), or null. */
    @Nullable
    public static AnvilCrushingRecipe find(Level level, Entity entity) {
        return Recipes.find(level, ModRecipes.ANVIL_CRUSHING_TYPE.get(), new Golem(entity.getType())).map(RecipeHolder::value).orElse(null);
    }

    public boolean accepts(EntityType<?> type) {
        if (entity.contains(type.builtInRegistryHolder())) {
            return true;
        }
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        for (String word : entityIdContains) {
            if (!word.isEmpty() && path.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** Battered enough for the ritual (below this window the anvil simply hurts the golem, above it the golem is too strong). */
    public boolean battered(float health) {
        return health >= minHealth && health <= maxHealth;
    }

    /** The entity types this ritual accepts (JEI): the listed ones, then any registered type whose id contains a word. */
    public List<EntityType<?>> entityTypes() {
        List<EntityType<?>> out = new java.util.ArrayList<>();
        entity.forEach(h -> out.add(h.value()));
        if (!entityIdContains.isEmpty()) {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                if (!out.contains(type) && accepts(type)) {
                    out.add(type);
                }
            }
        }
        return out;
    }

    @Override
    public boolean matches(Golem input, Level level) {
        return accepts(input.type());
    }

    @Override
    public ItemStack assemble(Golem input) {
        return result.copy();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<AnvilCrushingRecipe> getSerializer() {
        return ModRecipes.ANVIL_CRUSHING.get();
    }

    @Override
    public RecipeType<AnvilCrushingRecipe> getType() {
        return ModRecipes.ANVIL_CRUSHING_TYPE.get();
    }
}
