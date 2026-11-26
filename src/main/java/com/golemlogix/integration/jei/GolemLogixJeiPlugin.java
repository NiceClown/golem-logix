package com.golemlogix.integration.jei;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.client.AnnihilationBusScreen;
import com.golemlogix.client.BusScreen;
import com.golemlogix.client.ClientHooks;
import com.golemlogix.client.ClientSecrets;
import com.golemlogix.client.GolemScreen;
import com.golemlogix.client.InterfaceScreen;
import com.golemlogix.client.PatternEncoderScreen;
import com.golemlogix.client.RecipeMonitorScreen;
import com.golemlogix.client.StockManometerScreen;
import com.golemlogix.client.StockRequesterScreen;
import com.golemlogix.client.TerminalScreen;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.init.ModRecipes;
import com.golemlogix.integration.ars.ArsImbuement;
import com.golemlogix.recipe.AnvilCrushingRecipe;
import com.golemlogix.recipe.SaturatorRecipe;
import com.golemlogix.system.RecipeItems;
import com.golemlogix.system.Recipes;
import com.golemlogix.system.SourceHelper;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public class GolemLogixJeiPlugin implements IModPlugin {
    public static final Identifier PLUGIN_UID =
            Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "jei_plugin");
    private static final String[] SECRET_CRAFTING_IDS = {"void_glass_prism", "plasma_temper"};

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
                    EntityType.WITHER, Float.NaN, Float.NaN);
        }
        return witherRitual;
    }

    /** A golem ritual from the data packs, as JEI shows it; null when no loaded mod has such a golem. */
    @Nullable
    private static AnvilCrushRecipe anvilRecipe(AnvilCrushingRecipe r) {
        List<EntityType<?>> types = r.entityTypes();
        if (types.isEmpty()) {
            return null;
        }
        EntityType<?> type = types.get(0);
        ItemStack icon = r.icon().map(ItemStack::new).orElseGet(() -> SpawnEggItem.byId(type)
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.ANVIL)));
        int min = Mth.ceil(r.minHealth());
        int max = Mth.floor(r.maxHealth());
        Component hp = Component.translatable("jei.golem_logix.hp.range", min, max);
        if (DefaultAttributes.hasSupplier(type)) {
            @SuppressWarnings("unchecked")
            var living = (EntityType<? extends LivingEntity>) type;
            double full = DefaultAttributes.getSupplier(living).getBaseValue(Attributes.MAX_HEALTH);
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
        jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(ModItems.FLUID_GHOST.get())));
        // the Co-Processor half way through Create's assembly line exists only with Create
        if (!ModList.get().isLoaded("create")) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK,
                    List.of(new ItemStack(ModItems.INCOMPLETE_COMPRESSED_COPROCESSOR.get())));
        }
        // the mod's terminal search <-> the JEI search line
        ClientHooks.jeiSetSearch = text -> {
            if (runtime != null && !text.equals(runtime.getIngredientFilter().getFilterText())) {
                runtime.getIngredientFilter().setFilterText(text);
            }
        };
        ClientHooks.jeiGetSearch = () -> runtime == null ? null : runtime.getIngredientFilter().getFilterText();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        AnvilCrushCategory.clearCache();
    }

    /** Hide or show the secret recipes by the player's progress. */
    public static void applySecrets() {
        if (runtime == null) {
            return;
        }
        var recipes = runtime.getRecipeManager();
        // the Void-Glass Prism (the telegraph's endless range) is made of the secret matrix, the Plasma Temper of the Wither's
        // plasma: they are secrets too
        List<RecipeHolder<CraftingRecipe>> prism = new ArrayList<>();
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            for (String id : SECRET_CRAFTING_IDS) {
                Recipes.byId(level, Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, id))
                        .filter(h -> h.value() instanceof CraftingRecipe)
                        .ifPresent(h -> prism.add(castCrafting(h)));
            }
        }
        if (ClientSecrets.all()) {
            recipes.unhideRecipes(AnvilCrushCategory.TYPE, List.of(witherRitual()));
            recipes.unhideRecipes(ManaSaturatorCategory.TYPE, secretSaturator);
            recipes.unhideRecipes(RecipeTypes.CRAFTING, prism);
        } else {
            recipes.hideRecipes(AnvilCrushCategory.TYPE, List.of(witherRitual()));
            recipes.hideRecipes(ManaSaturatorCategory.TYPE, secretSaturator);
            recipes.hideRecipes(RecipeTypes.CRAFTING, prism);
        }
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<CraftingRecipe> castCrafting(RecipeHolder<?> holder) {
        return (RecipeHolder<CraftingRecipe>) holder;
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

    /** Same as {@link #devTransfer(Identifier, boolean)}, with Ctrl held to trigger autocrafting precraft. */
    public static String devTransfer(Identifier recipeId, boolean max, boolean ctrl) {
        RecipeTransfers.devCtrl = ctrl;
        try {
            return devTransfer(recipeId, max);
        } finally {
            RecipeTransfers.devCtrl = false;
        }
    }

    /**
     * For the developer rig: press JEI "+" for a recipe (a game recipe id, or a furnace item id for processing) in the open window.
     * Returns "ok", the JEI error type, or the reason the transfer is impossible.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String devTransfer(Identifier recipeId, boolean max) {
        if (runtime == null) {
            return "no runtime";
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return "no level";
        }
        RecipeHolder<?> holder = Recipes.byId(mc.level, recipeId).orElse(null);
        if (holder == null) {
            return "no recipe " + recipeId;
        }
        CategoryMatch match = findCategoryAndRecipe(recipeId, holder);
        if (match == null) {
            return "no category";
        }
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(match.category, match.recipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        if (layout.isEmpty()) {
            return "no layout";
        }
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(mc.player.containerMenu, match.category);
        if (handler.isEmpty()) {
            return "no handler";
        }
        var error = ((IRecipeTransferHandler) handler.get()).transferRecipe(mc.player.containerMenu, match.recipe,
                ((IRecipeLayoutDrawable<?>) layout.get()).getRecipeSlotsView(), mc.player, max, true);
        return error == null ? "ok" : error.getType().name();
    }

    @SuppressWarnings("rawtypes")
    private record CategoryMatch(IRecipeCategory category, Object recipe) {
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CategoryMatch findCategoryAndRecipe(Identifier recipeId, RecipeHolder<?> holder) {
        if (holder.value() instanceof CraftingRecipe || holder.value() instanceof SmeltingRecipe) {
            IRecipeType type = holder.value() instanceof CraftingRecipe ? RecipeTypes.CRAFTING : RecipeTypes.SMELTING;
            return new CategoryMatch(runtime.getRecipeManager().getRecipeCategory(type), holder);
        }
        // another mod's machine (a Create mixer...): the category that shows this recipe
        for (IRecipeCategory c : runtime.getRecipeManager().createRecipeCategoryLookup().get().toList()) {
            var found = runtime.getRecipeManager().createRecipeLookup(c.getRecipeType()).get()
                    .filter(r -> r == holder || (r instanceof RecipeHolder<?> rh && rh.id().identifier().equals(recipeId)))
                    .findFirst();
            if (found.isPresent()) {
                return new CategoryMatch(c, found.get());
            }
        }
        return null;
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
    private static <T> void showOnly(IRecipeType<T> type, ItemStack output) {
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
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // terminals: the sort buttons to the right of the window are avoided, and the R and U keys work on the list slots
        registration.addGuiContainerHandler(TerminalScreen.class, new IGuiContainerHandler<>() {
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
        registration.addGuiContainerHandler(RecipeMonitorScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory factory,
                                                                                              RecipeMonitorScreen screen, double mouseX, double mouseY) {
                Object[] hit = screen.ingredientAt(mouseX, mouseY);
                return hit == null ? Optional.empty() : clickable(factory, hit[0], (Rect2i) hit[1]);
            }
        });
        // ingredients from JEI into ghost slots (filters, interface settings, encoder grid)
        registration.addGhostIngredientHandler(BusScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(AnnihilationBusScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(InterfaceScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(PatternEncoderScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(StockRequesterScreen.class, new GhostTargets<>());
        registration.addGhostIngredientHandler(StockManometerScreen.class, new GhostTargets<>());
        // the upgrade column on the right and the settings buttons on the left of machine windows
        registration.addGenericGuiContainerHandler(GolemScreen.class, new IGuiContainerHandler<GolemScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GolemScreen<?> screen) {
                return screen.extraAreas();
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(new RecipeTransfers.CraftingTerminal(helper), RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new RecipeTransfers.EncoderCrafting(helper), RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new RecipeTransfers.EncoderProcessing(helper));
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new AnvilCrushCategory(guiHelper),
                new ManaSaturatorCategory(guiHelper),
                new HandForgingCategory(guiHelper),
                new CryoMixerCategory(guiHelper)
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Blocks.ANVIL), AnvilCrushCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.MANA_SATURATOR.get()), ManaSaturatorCategory.TYPE);
        // by hand: the hammer on the Saturator's anvil before the machine is assembled
        registration.addRecipeCatalyst(new ItemStack(ModItems.FORGE_HAMMER.get()), HandForgingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.MANA_SATURATOR.get()), HandForgingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CRYO_MIXER.get()), CryoMixerCategory.TYPE);
        // the Crafting Terminal crafts from the network (and takes JEI's "+"), so it stands among the crafting tables
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CRAFTING_TERMINAL.get()), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        RecipeMap manager = level == null ? null : Recipes.map(level);
        registerDistillationInfo(registration);
        registerAnvilRecipes(registration, manager);
        registerSaturatorAndHandRecipes(registration, level, manager);
        registration.addRecipes(CryoMixerCategory.TYPE, CryoMixerCategory.recipes());
    }

    /** Cryo-distillation (Source from blood): info pages shown when a blood mod is loaded. */
    private static void registerDistillationInfo(IRecipeRegistration registration) {
        if (!SourceHelper.distillationAvailable()) {
            return;
        }
        registration.addIngredientInfo(new ItemStack(ModBlocks.CRYO_DISTILLATION_TOWER.get()),
                VanillaTypes.ITEM_STACK, Component.translatable("jei.golem_logix.info.distillation_tower"));
        registration.addIngredientInfo(new ItemStack(ModBlocks.CRYO_SOURCE_JAR.get()),
                VanillaTypes.ITEM_STACK, Component.translatable("jei.golem_logix.info.cryo_jar"));
    }

    /** Anvil rituals from data packs plus the secret Wither ritual (hidden until revealed). */
    private static void registerAnvilRecipes(IRecipeRegistration registration, @Nullable RecipeMap manager) {
        List<AnvilCrushRecipe> anvilRecipes = new ArrayList<>();
        if (manager != null) {
            for (var holder : manager.byType(ModRecipes.ANVIL_CRUSHING_TYPE.get())) {
                AnvilCrushRecipe r = anvilRecipe(holder.value());
                if (r != null) {
                    anvilRecipes.add(r);
                }
            }
        }
        anvilRecipes.add(witherRitual());
        registration.addRecipes(AnvilCrushCategory.TYPE, anvilRecipes);
    }

    /** Mana Saturator data-pack recipes, hand-forging recipes, and optional Ars Nouveau Imbuement recipes. */
    private static void registerSaturatorAndHandRecipes(IRecipeRegistration registration, @Nullable Level level, @Nullable RecipeMap manager) {
        List<ManaSaturatorRecipe> saturatorRecipes = new ArrayList<>();
        List<ManaSaturatorRecipe> secret = new ArrayList<>();
        List<HandForgingCategory.Recipe> byHand = new ArrayList<>();
        if (manager != null) {
            for (var holder : manager.byType(ModRecipes.SATURATOR_TYPE.get())) {
                SaturatorRecipe s = holder.value();
                List<ItemStack> inputs = List.of(RecipeItems.stacks(s.ingredient()));
                ManaSaturatorRecipe r = new ManaSaturatorRecipe(inputs, s.source(), s.result().copy(), s.ticks());
                saturatorRecipes.add(r);
                if (s.secret()) {
                    secret.add(r);
                } else if (!s.handResult().isEmpty()) {
                    // recipes with a "hand_result" can also be forged by hand with the Forge Hammer
                    byHand.add(new HandForgingCategory.Recipe(inputs, s.handResult()));
                }
            }
        }
        secretSaturator = List.copyOf(secret);
        // with Ars Nouveau: the Imbuement Chamber's Source Gem recipes, twice as fast for a third more Source
        for (var gem : ArsImbuement.all(level)) {
            saturatorRecipes.add(new ManaSaturatorRecipe(List.of(RecipeItems.stacks(gem.input())), gem.source(), gem.output(), gem.ticks()));
        }
        registration.addRecipes(ManaSaturatorCategory.TYPE, saturatorRecipes);
        registration.addRecipes(HandForgingCategory.TYPE, byHand);
    }
}
