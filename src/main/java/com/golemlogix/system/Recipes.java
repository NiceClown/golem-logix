package com.golemlogix.system;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * Recipes on both sides. Since 1.21.2 the client gets no recipes by itself: the server sends the types this mod needs there (the
 * book, JEI, the Saturator's and the anvil's previews: crafting, the two rituals, the Ars Nouveau imbuement if it is installed), and
 * the client keeps what it got (ClientRecipes fills {@link #setClient}).
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID)
public final class Recipes {
    private static final Identifier ARS_IMBUEMENT = Identifier.fromNamespaceAndPath("ars_nouveau", "imbuement");
    private static volatile RecipeMap client = RecipeMap.EMPTY;

    private Recipes() {
    }

    public static void setClient(RecipeMap map) {
        client = map;
    }

    /** All the recipes this side knows: the server's, or what the client was sent. */
    public static RecipeMap map(@Nullable Level level) {
        return level instanceof ServerLevel server ? server.recipeAccess().recipeMap() : client;
    }

    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(@Nullable Level level, RecipeType<T> type, I input) {
        if (level == null) {
            return Optional.empty();
        }
        if (level instanceof ServerLevel server) {
            return server.recipeAccess().getRecipeFor(type, input, level);
        }
        return input.isEmpty() ? Optional.empty() : client.getRecipesFor(type, input, level).findFirst();
    }

    public static <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(@Nullable Level level, RecipeType<T> type) {
        return map(level).byType(type);
    }

    public static Optional<RecipeHolder<?>> byId(@Nullable Level level, Identifier id) {
        return Optional.ofNullable(map(level).byKey(key(id)));
    }

    public static ResourceKey<Recipe<?>> key(Identifier id) {
        return ResourceKey.create(Registries.RECIPE, id);
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.sendRecipes(RecipeType.CRAFTING, ModRecipes.SATURATOR_TYPE.get(), ModRecipes.ANVIL_CRUSHING_TYPE.get());
        BuiltInRegistries.RECIPE_TYPE.getOptional(ARS_IMBUEMENT).ifPresent(event::sendRecipes);
    }
}
