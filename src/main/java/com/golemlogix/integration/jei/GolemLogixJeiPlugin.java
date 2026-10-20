package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.client.ClientSecrets;
import com.golemlogix.client.GolemScreen;
import com.golemlogix.client.RecipeMonitorScreen;
import com.golemlogix.client.TerminalScreen;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public class GolemLogixJeiPlugin implements IModPlugin {
    public static final Identifier PLUGIN_UID =
            Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "jei_plugin");

    @Nullable
    private static IJeiRuntime runtime;

    /**
     * Secret recipes: visible in JEI only when the player has passed both trials. They are created lazily: JEI creates the plugin while
     * mods are still loading, when the mod's items are not registered yet.
     */
    @Nullable
    private static AnvilCrushRecipe witherRitual;
    /** The Saturator recipes marked "secret" in their data (the Occult Matrix by default). */
    private static List<ManaSaturatorRecipe> secretSaturator = List.of();

    private static AnvilCrushRecipe witherRitual() {
        if (witherRitual == null) {
            witherRitual = new AnvilCrushRecipe(new ItemStack(Items.NETHER_STAR), Component.translatable("jei.golem_logix.golem.wither"),
                    Component.translatable("jei.golem_logix.hp.wither"), new ItemStack(ModItems.IMPLODED_WITHER_PLASMA.get()),
                    net.minecraft.world.entity.EntityType.WITHER, Float.NaN, Float.NaN);
        }
        return witherRitual;
    }

    /** A golem ritual from the data packs, as JEI shows it; null when no loaded mod has such a golem. */
    @Nullable
    private static AnvilCrushRecipe anvilRecipe(com.golemlogix.recipe.AnvilCrushingRecipe r) {
        List<net.minecraft.world.entity.EntityType<?>> types = r.entityTypes();
        if (types.isEmpty()) {
            return null;
        }
        var type = types.get(0);
        ItemStack icon = r.icon().map(ItemStack::new).orElseGet(() -> {
            var egg = net.minecraft.world.item.SpawnEggItem.byId(type);
            return egg != null ? new ItemStack(egg) : new ItemStack(Items.ANVIL);
        });
        int min = net.minecraft.util.Mth.ceil(r.minHealth()), max = net.minecraft.util.Mth.floor(r.maxHealth());
        Component hp = Component.translatable("jei.golem_logix.hp.range", min, max);
        if (net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(type)) {
            @SuppressWarnings("unchecked")
            var living = (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) type;
            double full = net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(living)
                    .getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            hp = Component.translatable("jei.golem_logix.hp.range_of", min, max, (int) Math.round(full));
        }
        return new AnvilCrushRecipe(icon, type.getDescription(), hp, r.result().copy(), type, r.minHealth(), r.maxHealth());
    }

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        applySecrets();
        // the fluid ghost is a marker for ghost slots, not an item a player can have
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                List.of(new net.minecraft.world.item.ItemStack(com.golemlogix.init.ModItems.FLUID_GHOST.get())));
        // the Co-Processor half way through Create's assembly line exists only with Create
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                    List.of(new net.minecraft.world.item.ItemStack(com.golemlogix.init.ModItems.INCOMPLETE_COMPRESSED_COPROCESSOR.get())));
        }
        // the mod's terminal search <-> the JEI search line
        com.golemlogix.client.ClientHooks.jeiSetSearch = text -> {
            if (runtime != null && !text.equals(runtime.getIngredientFilter().getFilterText())) {
                runtime.getIngredientFilter().setFilterText(text);
            }
        };
        com.golemlogix.client.ClientHooks.jeiGetSearch = () -> runtime == null ? null : runtime.getIngredientFilter().getFilterText();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Hide or show the secret recipes by the player's progress. */
    public static void applySecrets() {
        if (runtime == null) {
            return;
        }
        var recipes = runtime.getRecipeManager();
        // the Void-Glass Prism (the telegraph's endless range) is made of the secret matrix: it is a secret too
        List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> prism = new java.util.ArrayList<>();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            level.getRecipeManager().byKey(Identifier.fromNamespaceAndPath(com.golemlogix.GolemLogixMod.MOD_ID, "void_glass_prism"))
                    .filter(h -> h.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe)
                    .ifPresent(h -> prism.add(cast(h)));
        }
        if (ClientSecrets.all()) {
            recipes.unhideRecipes(AnvilCrushCategory.TYPE, List.of(witherRitual()));
            recipes.unhideRecipes(ManaSaturatorCategory.TYPE, secretSaturator);
            recipes.unhideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, prism);
        } else {
            recipes.hideRecipes(AnvilCrushCategory.TYPE, List.of(witherRitual()));
            recipes.hideRecipes(ManaSaturatorCategory.TYPE, secretSaturator);
            recipes.hideRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, prism);
        }
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe> cast(
            net.minecraft.world.item.crafting.RecipeHolder<?> holder) {
        return (net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>) holder;
    }

    /** The search line of the JEI ingredient list (for the developer rig: "@golem_logix" shows all of the mod's items). */
    public static boolean setFilter(String text) {
        if (runtime == null) {
            return false;
        }
        runtime.getIngredientFilter().setFilterText(text);
        return true;
    }

    /** For the rig: how many recipes of the category are visible now (a check of the secret recipes). */
    public static int visibleRecipes(boolean anvil) {
        if (runtime == null) {
            return -1;
        }
        var type = anvil ? AnvilCrushCategory.TYPE : ManaSaturatorCategory.TYPE;
        return (int) runtime.getRecipeManager().createRecipeLookup(type).get().count();
    }

    /**
     * For the developer rig: press JEI "+" for a recipe (a game recipe id, or a furnace item id for processing) in the open window.
     * Returns "ok", the JEI error type, or the reason the transfer is impossible.
     */
    /** The same as devTransfer, but as if Ctrl were held: what is missing is ordered through autocrafting (precraft). */
    public static String devTransfer(Identifier recipeId, boolean max, boolean ctrl) {
        RecipeTransfers.devCtrl = ctrl;
        try {
            return devTransfer(recipeId, max);
        } finally {
            RecipeTransfers.devCtrl = false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String devTransfer(Identifier recipeId, boolean max) {
        if (runtime == null) {
            return "no runtime";
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        var holder = mc.level.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null) {
            return "no recipe " + recipeId;
        }
        mezz.jei.api.recipe.category.IRecipeCategory category = null;
        Object recipe = holder;
        if (holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe
                || holder.value() instanceof net.minecraft.world.item.crafting.SmeltingRecipe) {
            mezz.jei.api.recipe.RecipeType type = holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe
                    ? mezz.jei.api.constants.RecipeTypes.CRAFTING : mezz.jei.api.constants.RecipeTypes.SMELTING;
            category = runtime.getRecipeManager().getRecipeCategory(type);
        } else {
            // another mod's machine (a Create mixer...): the category that shows this recipe
            for (var c : runtime.getRecipeManager().createRecipeCategoryLookup().get().toList()) {
                var found = runtime.getRecipeManager().createRecipeLookup(c.getRecipeType()).get()
                        .filter(r -> r == holder || (r instanceof net.minecraft.world.item.crafting.RecipeHolder<?> rh && rh.id().equals(recipeId)))
                        .findFirst();
                if (found.isPresent()) {
                    category = c;
                    recipe = found.get();
                    break;
                }
            }
            if (category == null) {
                return "no category";
            }
        }
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        if (layout.isEmpty()) {
            return "no layout";
        }
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(mc.player.containerMenu, category);
        if (handler.isEmpty()) {
            return "no handler";
        }
        var error = ((mezz.jei.api.recipe.transfer.IRecipeTransferHandler) handler.get()).transferRecipe(mc.player.containerMenu, recipe,
                ((mezz.jei.api.gui.IRecipeLayoutDrawable<?>) layout.get()).getRecipeSlotsView(), mc.player, max, true);
        return error == null ? "ok" : error.getType().name();
    }

    /** Open the JEI recipe window (recipes or uses of an item); false if JEI is not ready yet. */
    public static boolean showRecipes(ItemStack stack, boolean uses) {
        if (runtime == null) {
            return false;
        }
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                .createFocus(uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack));
        return true;
    }

    /** Open one JEI recipe category by its id (the developer rig: "golem_logix:anvil_crushing", "create:sequenced_assembly"). */
    public static boolean showType(Identifier uid, ItemStack output) {
        if (runtime == null) {
            return false;
        }
        var type = runtime.getJeiHelpers().getRecipeType(uid);
        if (type.isEmpty()) {
            return false;
        }
        if (output.isEmpty()) {
            runtime.getRecipesGui().showTypes(List.of(type.get()));
        } else {
            showOnly(type.get(), output);
        }
        return true;
    }

    /** Only the recipes of the category that make this item. */
    private static <T> void showOnly(mezz.jei.api.recipe.RecipeType<T> type, ItemStack output) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, output);
        List<T> recipes = runtime.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get().toList();
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(type), recipes, List.of(focus));
    }

    /** An item or fluid in a slot as a "clickable" JEI ingredient. */
    private static Optional<? extends IClickableIngredient<?>> clickable(IClickableIngredientFactory factory, Object ingredient, Rect2i area) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            return factory.createBuilder(stack.copyWithCount(1)).buildWithArea(area);
        }
        if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) {
            return factory.createBuilder(NeoForgeTypes.FLUID_STACK, fluid.copyWithAmount(1000)).buildWithArea(area);
        }
        return Optional.empty();
    }

    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        // terminals: the sort buttons to the right of the window are avoided, and the R and U keys work on the list slots
        registration.addGuiContainerHandler(TerminalScreen.class, new IGuiContainerHandler<TerminalScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(TerminalScreen screen) {
                return screen.extraAreas();
            }

            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory factory,
                                                                                              TerminalScreen screen, double mouseX, double mouseY) {
                TerminalScreen.Hit hit = screen.ingredientAt(mouseX, mouseY);
                return hit == null ? Optional.empty() : clickable(factory, hit.ingredient(), hit.area());
            }
        });
        registration.addGuiContainerHandler(RecipeMonitorScreen.class, new IGuiContainerHandler<RecipeMonitorScreen>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory factory,
                                                                                              RecipeMonitorScreen screen, double mouseX, double mouseY) {
                Object[] hit = screen.ingredientAt(mouseX, mouseY);
                return hit == null ? Optional.empty() : clickable(factory, hit[0], (Rect2i) hit[1]);
            }
        });
        // ingredients from JEI into ghost slots (filters, interface settings, encoder grid)
        registration.addGhostIngredientHandler(com.golemlogix.client.BusScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(com.golemlogix.client.InterfaceScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(com.golemlogix.client.PatternEncoderScreen.class, new GhostTargets<>());
        // the upgrade column on the right and the settings buttons on the left of machine windows
        registration.addGenericGuiContainerHandler(GolemScreen.class, new IGuiContainerHandler<GolemScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GolemScreen<?> screen) {
                return screen.extraAreas();
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new RecipeTransfers.CraftingTerminal(helper), mezz.jei.api.constants.RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new RecipeTransfers.EncoderCrafting(helper), mezz.jei.api.constants.RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new RecipeTransfers.EncoderProcessing(helper));
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new AnvilCrushCategory(registration.getJeiHelpers().getGuiHelper()),
                new ManaSaturatorCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Blocks.ANVIL), AnvilCrushCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.MANA_SATURATOR.get()), ManaSaturatorCategory.TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        var manager = level == null ? null : level.getRecipeManager();

        // 0. Cryo-distillation (Source from blood): a page on the tower and the jar where a blood mod is there
        if (com.golemlogix.system.SourceHelper.distillationAvailable()) {
            registration.addIngredientInfo(new ItemStack(com.golemlogix.init.ModBlocks.CRYO_DISTILLATION_TOWER.get()),
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable("jei.golem_logix.info.distillation_tower"));
            registration.addIngredientInfo(new ItemStack(com.golemlogix.init.ModBlocks.CRYO_SOURCE_JAR.get()),
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable("jei.golem_logix.info.cryo_jar"));
        }

        // 1. Anvil rituals: the golem ones from the data packs (golem_logix:anvil_crushing); the Wither one is secret and hidden
        //    until the secret is revealed
        List<AnvilCrushRecipe> anvilRecipes = new java.util.ArrayList<>();
        if (manager != null) {
            for (var holder : manager.getAllRecipesFor(com.golemlogix.init.ModRecipes.ANVIL_CRUSHING_TYPE.get())) {
                AnvilCrushRecipe r = anvilRecipe(holder.value());
                if (r != null) {
                    anvilRecipes.add(r);
                }
            }
        }
        anvilRecipes.add(witherRitual());
        registration.addRecipes(AnvilCrushCategory.TYPE, anvilRecipes);

        // 2. Mana Saturator: its recipes from the data packs (golem_logix:saturator), the secret ones hidden like the Wither
        List<ManaSaturatorRecipe> saturatorRecipes = new java.util.ArrayList<>();
        List<ManaSaturatorRecipe> secret = new java.util.ArrayList<>();
        if (manager != null) {
            for (var holder : manager.getAllRecipesFor(com.golemlogix.init.ModRecipes.SATURATOR_TYPE.get())) {
                com.golemlogix.recipe.SaturatorRecipe s = holder.value();
                ManaSaturatorRecipe r = new ManaSaturatorRecipe(List.of(s.ingredient().getItems()), s.source(), s.result().copy(), s.ticks());
                saturatorRecipes.add(r);
                if (s.secret()) {
                    secret.add(r);
                }
            }
        }
        secretSaturator = List.copyOf(secret);
        // with Ars Nouveau: the Imbuement Chamber's Source Gem recipes, twice as fast for a third more Source
        for (var gem : com.golemlogix.integration.ars.ArsImbuement.all(level)) {
            saturatorRecipes.add(new ManaSaturatorRecipe(List.of(gem.input().getItems()), gem.source(), gem.output(), gem.ticks()));
        }
        registration.addRecipes(ManaSaturatorCategory.TYPE, saturatorRecipes);
    }
}
