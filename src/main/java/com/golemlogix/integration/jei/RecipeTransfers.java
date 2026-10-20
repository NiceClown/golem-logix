package com.golemlogix.integration.jei;

import com.golemlogix.init.ModMenus;
import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.menu.TerminalMenu;
import com.golemlogix.network.RecipeFillPayload;
import com.golemlogix.network.TerminalListPayload;
import com.golemlogix.system.ItemKey;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JEI "+" button for the mod's windows: the crafting terminal lays the recipe out on the grid from the network and the inventory,
 * the encoder writes the recipe into the ghost slots (crafting table as is, any other category as processing in a machine).
 */
final class RecipeTransfers {
    /** Only for the developer rig: treat Ctrl as held (precraft without OS keyboard input). */
    static boolean devCtrl;

    private RecipeTransfers() {
    }

    /** Ingredient options of a cell: the one JEI shows right now comes first. */
    static List<ItemStack> options(IRecipeSlotView view) {
        List<ItemStack> out = new ArrayList<>();
        view.getDisplayedItemStack().ifPresent(s -> out.add(s.copy()));
        view.getItemStacks().forEach(s -> {
            if (out.size() < RecipeFillPayload.MAX_OPTIONS && out.stream().noneMatch(o -> ItemStack.isSameItemSameComponents(o, s))) {
                out.add(s.copy());
            }
        });
        return out;
    }

    // ---------------------------------------------------------------------------------- crafting terminal

    static final class CraftingTerminal implements IRecipeTransferHandler<TerminalMenu, RecipeHolder<CraftingRecipe>> {
        private final IRecipeTransferHandlerHelper helper;

        CraftingTerminal(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends TerminalMenu> getContainerClass() {
            return TerminalMenu.class;
        }

        @Override
        public Optional<MenuType<TerminalMenu>> getMenuType() {
            return Optional.of(ModMenus.TERMINAL.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(TerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            if (!menu.crafting) {
                return helper.createInternalError();
            }
            List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            if (inputs.size() != 9) {
                return helper.createInternalError();
            }
            // what there is: the network list (sent by the server) and the player's inventory; what the network can craft
            Map<ItemKey, Long> have = new HashMap<>();
            java.util.Set<ItemKey> craftable = new java.util.HashSet<>();
            for (TerminalListPayload.Entry e : menu.clientEntries) {
                if (e.count() > 0) {
                    have.merge(ItemKey.of(e.key()), e.count(), Long::sum);
                }
                if (e.craftable()) {
                    craftable.add(ItemKey.of(e.key()));
                }
            }
            for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
                if (!s.isEmpty()) {
                    have.merge(ItemKey.of(s), (long) s.getCount(), Long::sum);
                }
            }
            List<List<ItemStack>> options = new ArrayList<>();
            List<IRecipeSlotView> missing = new ArrayList<>();
            // precraft: for every empty cell the first option the network can craft
            Map<ItemKey, Integer> toOrder = new java.util.LinkedHashMap<>();
            for (IRecipeSlotView view : inputs) {
                List<ItemStack> opts = options(view);
                options.add(opts);
                if (opts.isEmpty()) {
                    continue;
                }
                boolean found = false;
                for (ItemStack o : opts) {
                    ItemKey k = ItemKey.of(o);
                    if (have.getOrDefault(k, 0L) > 0) {
                        have.merge(k, -1L, Long::sum);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missing.add(view);
                    for (ItemStack o : opts) {
                        ItemKey k = ItemKey.of(o);
                        if (craftable.contains(k)) {
                            toOrder.merge(k, maxTransfer ? Math.min(64, o.getMaxStackSize()) : 1, Integer::sum);
                            break;
                        }
                    }
                }
            }
            if (doTransfer) {
                if ((net.minecraft.client.gui.screens.net.minecraft.client.Minecraft.getInstance().hasControlDown() || devCtrl) && !toOrder.isEmpty()) {
                    List<ItemStack> items = new ArrayList<>();
                    List<Integer> counts = new ArrayList<>();
                    for (Map.Entry<ItemKey, Integer> e : toOrder.entrySet()) {
                        items.add(e.getKey().stack());
                        counts.add(e.getValue());
                    }
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new com.golemlogix.network.PrecraftPayload(menu.containerId, items, counts));
                } else {
                    net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RecipeFillPayload(menu.containerId, true, maxTransfer, options, List.of()));
                }
            }
            return missing.isEmpty() ? null : new Missing(missing, !toOrder.isEmpty());
        }
    }

    /**
     * Something is neither in the network nor in the inventory: the button is highlighted, the missing cells are red, but the transfer still works.
     * If the network can craft what is missing, Ctrl + "+" orders it through autocrafting (precraft).
     */
    private record Missing(List<IRecipeSlotView> slots, boolean canOrder) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return canOrder ? 0x8038D9D6 : 0x80FFA040;
        }

        @Override
        public List<Component> getTooltip() {
            if (canOrder) {
                return List.of(Component.translatable("jei.golem_logix.transfer.missing").withStyle(ChatFormatting.GOLD),
                        Component.translatable("jei.golem_logix.transfer.precraft").withStyle(ChatFormatting.AQUA));
            }
            return List.of(Component.translatable("jei.golem_logix.transfer.missing").withStyle(ChatFormatting.GOLD));
        }

        @Override
        public void showError(GuiGraphicsExtractor g, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
            g.pose().pushMatrix();
            g.pose().translate(recipeX, recipeY);
            for (IRecipeSlotView slot : slots) {
                slot.drawHighlight(g, 0x66FF3030);
            }
            g.pose().popMatrix();
        }
    }

    // ---------------------------------------------------------------------------------- encoder

    static final class EncoderCrafting implements IRecipeTransferHandler<PatternEncoderMenu, RecipeHolder<CraftingRecipe>> {
        private final IRecipeTransferHandlerHelper helper;

        EncoderCrafting(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends PatternEncoderMenu> getContainerClass() {
            return PatternEncoderMenu.class;
        }

        @Override
        public Optional<MenuType<PatternEncoderMenu>> getMenuType() {
            return Optional.of(ModMenus.PATTERN_ENCODER.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(PatternEncoderMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
            if (inputs.size() != 9) {
                return helper.createInternalError();
            }
            if (doTransfer) {
                List<List<ItemStack>> options = new ArrayList<>();
                for (IRecipeSlotView view : inputs) {
                    options.add(options(view));
                }
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RecipeFillPayload(menu.containerId, true, false, options, List.of()));
            }
            return null;
        }
    }

    /** Any other recipe (furnace, other mods' machines) is encoded as processing: item inputs and up to three item outputs. */
    static final class EncoderProcessing implements IUniversalRecipeTransferHandler<PatternEncoderMenu> {
        private final IRecipeTransferHandlerHelper helper;

        EncoderProcessing(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends PatternEncoderMenu> getContainerClass() {
            return PatternEncoderMenu.class;
        }

        @Override
        public Optional<MenuType<PatternEncoderMenu>> getMenuType() {
            return Optional.of(ModMenus.PATTERN_ENCODER.get());
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(PatternEncoderMenu menu, Object recipe, IRecipeSlotsView slots,
                                                   Player player, boolean maxTransfer, boolean doTransfer) {
            if (recipe instanceof AnvilCrushRecipe) {
                // a golem cannot be put into a machine
                return helper.createInternalError();
            }
            List<List<ItemStack>> inputs = new ArrayList<>();
            for (IRecipeSlotView view : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
                List<ItemStack> opts = options(view);
                if (!opts.isEmpty()) {
                    inputs.add(opts);
                    continue;
                }
                // a fluid ingredient (a Create basin, a mixer...): a fluid ghost with the recipe's amount
                var fluid = view.getDisplayedIngredient(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK);
                if (fluid.isPresent() && !fluid.get().isEmpty()) {
                    inputs.add(List.of(com.golemlogix.items.FluidGhostItem.of(fluid.get().copy())));
                }
            }
            List<ItemStack> outputs = new ArrayList<>();
            for (IRecipeSlotView view : slots.getSlotViews(RecipeIngredientRole.OUTPUT)) {
                view.getDisplayedItemStack().ifPresent(s -> {
                    if (outputs.size() < RecipeFillPayload.MAX_OUTPUTS) {
                        outputs.add(s.copy());
                    }
                });
            }
            if (inputs.isEmpty() || outputs.isEmpty()) {
                return helper.createUserErrorWithTooltip(Component.translatable("jei.golem_logix.transfer.no_items"));
            }
            if (inputs.size() > 9) {
                inputs = inputs.subList(0, 9);
            }
            if (doTransfer) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RecipeFillPayload(menu.containerId, false, false, inputs, outputs));
            }
            return null;
        }
    }
}
