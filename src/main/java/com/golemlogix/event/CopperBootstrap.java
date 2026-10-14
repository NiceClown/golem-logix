package com.golemlogix.event;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.init.ModItems;
import com.golemlogix.recipe.AnvilCrushingRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;

/**
 * The first Copper Matrix comes from a copper golem (the anvil or the Dispel): the Saturator needs a matrix itself, Create's mixer a
 * running network. Copper Age Backport is optional, so a game may have no copper golem at all and no modpack recipe for the
 * matrix either: then the progression stops at the first matrix, and every player who joins is told what is missing.
 */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class CopperBootstrap {
    /** The mod's own recipes that need a matrix, a copper golem or a running network first: none of them makes the first matrix. */
    private static final Set<ResourceLocation> NOT_A_START = Set.of(
            id("saturator/flattened_copper_matrix"),
            id("ars/raw_copper_mind"),
            id("create/pressing_torn_copper_mind"));

    private CopperBootstrap() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, path);
    }

    /** No anvil ritual with an existing golem and no other recipe (a modpack's) makes a Copper Matrix. */
    public static boolean firstMatrixImpossible(MinecraftServer server) {
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (NOT_A_START.contains(holder.id())) {
                continue;
            }
            if (holder.value() instanceof AnvilCrushingRecipe anvil) {
                if (anvil.result().is(ModItems.FLATTENED_COPPER_MATRIX.get()) && !anvil.entityTypes().isEmpty()) {
                    return false;
                }
                continue;
            }
            if (holder.value().getResultItem(server.registryAccess()).is(ModItems.FLATTENED_COPPER_MATRIX.get())) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && firstMatrixImpossible(player.server)) {
            player.sendSystemMessage(Component.translatable("message.golem_logix.no_copper_golem").withStyle(ChatFormatting.GOLD));
        }
    }
}
