package com.golemlogix.menu;

import com.golemlogix.data.PatternData;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.CopperPunchCardItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Pattern encoder: 3x3 ghost inputs, up to 3 ghost outputs, a blank punch card at the input,
 * the encoded one at the output. Modes: crafting table (the recipe is found by itself) and processing (any machine).
 */
public class PatternEncoderMenu extends GolemMenu {
    public static final int IMAGE_HEIGHT = 214;
    public static final int GRID_START = 0;
    public static final int OUT_START = 9;
    public static final int CARD_IN = 12;
    public static final int CARD_OUT = 13;

    public static final int BUTTON_TOGGLE = 0;
    public static final int BUTTON_ENCODE = 1;
    public static final int BUTTON_CLEAR = 2;
    public static final int BUTTON_LOAD = 3;
    public static final int BUTTON_FUZZY = 4;

    public static final int SLOT_COUNT = 14;

    private final Container container;
    private final net.minecraft.world.inventory.ContainerData data;
    private final Level level;
    private boolean updating = false;
    private final java.util.function.Consumer<Container> listener = this::slotsChanged;

    private final ContainerLevelAccess access;

    public static PatternEncoderMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new PatternEncoderMenu(id, inv, new com.golemlogix.blockentity.ListeningContainer(SLOT_COUNT), new net.minecraft.world.inventory.SimpleContainerData(2),
                ContainerLevelAccess.NULL);
    }

    /** container and data belong to the encoder block entity (on the client they are empty copies filled by window sync). */
    public PatternEncoderMenu(int id, Inventory inv, Container container, net.minecraft.world.inventory.ContainerData data,
                              ContainerLevelAccess access) {
        super(ModMenus.PATTERN_ENCODER.get(), id);
        this.level = inv.player.level();
        this.access = access;
        this.container = container;
        this.data = data;

        for (int i = 0; i < 9; i++) {
            this.addSlot(new GhostSlot(container, i, 30 + (i % 3) * 18, 18 + (i / 3) * 18));
        }
        for (int i = 0; i < 3; i++) {
            this.addSlot(new GhostSlot(container, OUT_START + i, 118, 18 + i * 18));
        }
        this.addSlot(new Slot(container, CARD_IN, 30, 84) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CopperPunchCardItem;
            }
        });
        // an already written punch card can be put into the finished card slot: its recipe opens in the grid for editing, "Stamp"
        // overwrites the same card (no blank card is spent)
        this.addSlot(new Slot(container, CARD_OUT, 118, 84) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CopperPunchCardItem.hasPattern(stack);
            }

            @Override
            public void setByPlayer(ItemStack stack, ItemStack old) {
                super.setByPlayer(stack, old);
                if (!level.isClientSide() && CopperPunchCardItem.hasPattern(stack) && !ItemStack.matches(stack, old)) {
                    loadPattern(stack.get(ModDataComponents.PATTERN.get()));
                }
            }
        });
        this.containerSlotCount = SLOT_COUNT;
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        this.addDataSlots(data);

        if (container instanceof com.golemlogix.blockentity.ListeningContainer sc) {
            sc.addListener(listener);
        }
    }

    public boolean isCraftingMode() {
        return data.get(0) == 0;
    }

    /** Fuzzy mode: a crafting table recipe takes in each cell what the recipe accepts there; a processing input, its chosen tag or any components. */
    public boolean isFuzzy() {
        return data.get(1) != 0;
    }

    @Override
    public boolean fuzzyActive(int slotId) {
        return isFuzzy() && slotId >= GRID_START && slotId < GRID_START + 9 && itemGhost(slotId);
    }

    /** A tag is chosen for processing inputs only: a crafting recipe decides for itself. */
    @Override
    public boolean canCycleFuzzy(int slotId) {
        return fuzzyActive(slotId) && !isCraftingMode();
    }

    @Override
    public net.minecraft.network.chat.Component fuzzyLine(int slotId) {
        if (fuzzyActive(slotId) && isCraftingMode()) {
            return net.minecraft.network.chat.Component.translatable("tooltip.golem_logix.fuzzy",
                    net.minecraft.network.chat.Component.translatable("tooltip.golem_logix.fuzzy.recipe")).withStyle(net.minecraft.ChatFormatting.AQUA);
        }
        return super.fuzzyLine(slotId);
    }

    @Override
    public void setGhost(Player player, int slotId, ItemStack stack) {
        if (com.golemlogix.items.FluidGhostItem.is(stack) && slotId >= OUT_START && slotId < OUT_START + 3) {
            return;
        }
        super.setGhost(player, slotId, stack);
        refreshCraftingResult();
    }

    public Container getContainer() {
        return container;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        // fluids are only ingredients of processing patterns: never outputs and never in the crafting table grid
        boolean fluid = com.golemlogix.items.FluidGhostItem.is(ghostFor(getCarried()));
        if (fluid && clickType == ContainerInput.PICKUP && isGhost(slotId)
                && ((slotId >= OUT_START && slotId < OUT_START + 3) || isCraftingMode())) {
            return;
        }
        if (handleGhostClick(slotId, button, clickType)) {
            // the crafting table recipe output is computed by itself and cannot be changed by hand
            if (isCraftingMode() && slotId >= OUT_START && slotId < OUT_START + 3) {
                refreshCraftingResult();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public void slotsChanged(Container changed) {
        super.slotsChanged(changed);
        if (!updating) {
            refreshCraftingResult();
        }
    }

    private void refreshCraftingResult() {
        if (!isCraftingMode()) {
            return;
        }
        updating = true;
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack s = container.getItem(i);
                if (com.golemlogix.items.FluidGhostItem.is(s)) {
                    container.setItem(i, ItemStack.EMPTY);
                } else if (!s.isEmpty() && s.getCount() != 1) {
                    container.setItem(i, s.copyWithCount(1));
                }
            }
            List<ItemStack> grid = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                grid.add(container.getItem(i));
            }
            List<ItemStack> result = CraftingJob.craftResult(level, new PatternData(true, grid, List.of()));
            ItemStack out = result == null ? ItemStack.EMPTY : result.get(0);
            if (!ItemStack.matches(container.getItem(OUT_START), out)) {
                container.setItem(OUT_START, out);
            }
            for (int i = 1; i < 3; i++) {
                if (!container.getItem(OUT_START + i).isEmpty()) {
                    container.setItem(OUT_START + i, ItemStack.EMPTY);
                }
            }
        } finally {
            updating = false;
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_TOGGLE -> {
                data.set(0, isCraftingMode() ? 1 : 0);
                updating = true;
                for (int i = 0; i < 3; i++) {
                    container.setItem(OUT_START + i, ItemStack.EMPTY);
                }
                updating = false;
                refreshCraftingResult();
                return true;
            }
            case BUTTON_CLEAR -> {
                updating = true;
                for (int i = 0; i < 12; i++) {
                    container.setItem(i, ItemStack.EMPTY);
                }
                updating = false;
                refreshCraftingResult();
                return true;
            }
            case BUTTON_LOAD -> {
                loadFromCard();
                return true;
            }
            case BUTTON_FUZZY -> {
                data.set(1, isFuzzy() ? 0 : 1);
                if (!isFuzzy()) {
                    // exact again: the chosen tags go away with it
                    updating = true;
                    for (int i = 0; i < 9; i++) {
                        ItemStack s = container.getItem(i);
                        if (com.golemlogix.system.Fuzzy.tag(s) != null) {
                            container.setItem(i, com.golemlogix.system.Fuzzy.plain(s));
                        }
                    }
                    updating = false;
                }
                return true;
            }
            case BUTTON_ENCODE -> {
                encode(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * A recipe from JEI ("+"): crafting table = nine cells in place (one item each), processing in a machine = inputs in a row
     * (identical ones are summed) and up to three outputs. The slots are ghosts: items are not taken from anywhere.
     */
    public void fillFromRecipe(boolean craftingRecipe, List<List<ItemStack>> inputs, List<ItemStack> outputs) {
        updating = true;
        try {
            for (int i = 0; i < 12; i++) {
                container.setItem(i, ItemStack.EMPTY);
            }
            data.set(0, craftingRecipe ? 0 : 1);
            if (craftingRecipe) {
                for (int i = 0; i < Math.min(9, inputs.size()); i++) {
                    ItemStack first = firstOf(inputs.get(i));
                    boolean usable = !first.isEmpty() && !com.golemlogix.items.FluidGhostItem.is(first);
                    container.setItem(i, usable ? first.copyWithCount(1) : ItemStack.EMPTY);
                }
            } else {
                int slot = 0;
                for (List<ItemStack> opts : inputs) {
                    ItemStack first = firstOf(opts);
                    if (first.isEmpty()) {
                        continue;
                    }
                    boolean fluid = com.golemlogix.items.FluidGhostItem.is(first);
                    boolean merged = false;
                    for (int k = 0; k < slot; k++) {
                        ItemStack ex = container.getItem(k);
                        if (fluid && com.golemlogix.system.Ingredients.key(ex).equals(com.golemlogix.system.Ingredients.key(first))) {
                            // the same fluid twice: one ghost with the summed mB
                            container.setItem(k, com.golemlogix.items.FluidGhostItem.withAmount(ex,
                                    com.golemlogix.items.FluidGhostItem.amount(ex) + com.golemlogix.items.FluidGhostItem.amount(first)));
                            merged = true;
                            break;
                        }
                        if (!fluid && ItemStack.isSameItemSameComponents(ex, first)) {
                            container.setItem(k, ex.copyWithCount(Math.min(99, ex.getCount() + first.getCount())));
                            merged = true;
                            break;
                        }
                    }
                    if (!merged && slot < 9) {
                        container.setItem(slot++, fluid ? first.copyWithCount(1) : first.copyWithCount(Math.max(1, Math.min(99, first.getCount()))));
                    }
                }
                for (int i = 0; i < Math.min(3, outputs.size()); i++) {
                    ItemStack out = outputs.get(i);
                    if (!out.isEmpty() && !com.golemlogix.items.FluidGhostItem.is(out)) {
                        container.setItem(OUT_START + i, out.copyWithCount(Math.max(1, Math.min(99, out.getCount()))));
                    }
                }
            }
        } finally {
            updating = false;
        }
        refreshCraftingResult();
        broadcastChanges();
    }

    private static ItemStack firstOf(List<ItemStack> options) {
        for (ItemStack s : options) {
            if (!s.isEmpty()) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private void loadFromCard() {
        PatternData fromIn = container.getItem(CARD_IN).get(ModDataComponents.PATTERN.get());
        PatternData fromOut = container.getItem(CARD_OUT).get(ModDataComponents.PATTERN.get());
        loadPattern(fromIn != null ? fromIn : fromOut);
    }

    /** Open the card's recipe in the grid (for editing). */
    void loadPattern(PatternData pattern) {
        if (pattern == null) {
            return;
        }
        updating = true;
        for (int i = 0; i < 12; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        data.set(0, pattern.crafting() ? 0 : 1);
        data.set(1, pattern.isFuzzy() ? 1 : 0);
        for (int i = 0; i < Math.min(9, pattern.inputs().size()); i++) {
            ItemStack in = pattern.inputs().get(i).copy();
            // a processing input's chosen tag goes back onto its ghost
            container.setItem(i, pattern.crafting() ? in : com.golemlogix.system.Fuzzy.withTag(in,
                    com.golemlogix.system.Fuzzy.tagOfSpec(pattern.spec(i))));
        }
        for (int i = 0; i < Math.min(3, pattern.outputs().size()); i++) {
            container.setItem(OUT_START + i, pattern.outputs().get(i).copy());
        }
        updating = false;
        refreshCraftingResult();
        broadcastChanges();
    }

    private void encode(Player player) {
        ItemStack card = container.getItem(CARD_IN);
        // a written card lies in the output slot: overwrite it (recipe editing), no blank card is needed
        boolean rewrite = CopperPunchCardItem.hasPattern(container.getItem(CARD_OUT));
        if (!rewrite && (card.isEmpty() || !(card.getItem() instanceof CopperPunchCardItem))) {
            player.sendOverlayMessage(Component.translatable("message.golem_logix.encoder.no_card"));
            return;
        }
        if (!rewrite && !container.getItem(CARD_OUT).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.golem_logix.encoder.output_full"));
            return;
        }

        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        // fuzziness per input: the recipe's own choice in a crafting grid, the ghost's tag (or any components) in processing
        List<String> fuzzy = new ArrayList<>();
        boolean crafting = isCraftingMode();
        if (crafting) {
            for (int i = 0; i < 9; i++) {
                ItemStack s = com.golemlogix.system.Fuzzy.plain(container.getItem(i));
                inputs.add(s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1));
                fuzzy.add(isFuzzy() && !s.isEmpty() ? com.golemlogix.system.Fuzzy.RECIPE : com.golemlogix.system.Fuzzy.EXACT);
            }
            List<ItemStack> result = CraftingJob.craftResult(level, new PatternData(true, inputs, List.of()));
            if (result == null) {
                player.sendOverlayMessage(Component.translatable("message.golem_logix.encoder.no_recipe"));
                return;
            }
            outputs.add(result.get(0));
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) {
                    inputs.add(com.golemlogix.system.Fuzzy.plain(s).copy());
                    fuzzy.add(isFuzzy() ? com.golemlogix.system.Fuzzy.specOf(s) : com.golemlogix.system.Fuzzy.EXACT);
                }
            }
            for (int i = 0; i < 3; i++) {
                ItemStack s = container.getItem(OUT_START + i);
                if (!s.isEmpty()) {
                    outputs.add(s.copy());
                }
            }
        }
        PatternData data = new PatternData(crafting, inputs, outputs, fuzzy);
        if (!data.isValid()) {
            player.sendOverlayMessage(Component.translatable("message.golem_logix.encoder.incomplete"));
            return;
        }

        if (rewrite) {
            ItemStack out = container.getItem(CARD_OUT);
            container.setItem(CARD_OUT, CopperPunchCardItem.written(out.getItem(), data));
            player.sendOverlayMessage(Component.translatable("message.golem_logix.encoder.rewritten"));
            return;
        }
        ItemStack encoded = CopperPunchCardItem.written(card.getItem(), data);
        card.shrink(1);
        container.setItem(CARD_IN, card.isEmpty() ? ItemStack.EMPTY : card);
        container.setItem(CARD_OUT, encoded);
        player.sendOverlayMessage(Component.translatable("message.golem_logix.encoder.done"));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || isGhost(index)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == CARD_IN || index == CARD_OUT) {
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CopperPunchCardItem) {
            // a written card goes to the editing slot (if it is free), a blank one to the blank stock
            if (CopperPunchCardItem.hasPattern(stack) && container.getItem(CARD_OUT).isEmpty()) {
                ItemStack one = stack.split(1);
                this.slots.get(CARD_OUT).setByPlayer(one, ItemStack.EMPTY);
            } else if (CopperPunchCardItem.hasPattern(stack) || !this.moveItemStackTo(stack, CARD_IN, CARD_IN + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // the contents stay in the encoder (blank cards, the finished card, the recipe)
        if (container instanceof com.golemlogix.blockentity.ListeningContainer sc) {
            sc.removeListener(listener);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.PATTERN_ENCODER.get());
    }
}
