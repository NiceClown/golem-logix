package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.system.Recipes;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** Keeps the recipes the server sent (see Recipes) until the player leaves. */
@EventBusSubscriber(modid = GolemLogixMod.MOD_ID, value = Dist.CLIENT)
public final class ClientRecipes {
    private ClientRecipes() {
    }

    /** Before JEI and the others read them. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onRecipes(RecipesReceivedEvent event) {
        Recipes.setClient(event.getRecipeMap());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Recipes.setClient(RecipeMap.EMPTY);
    }
}
