package com.golemlogix.menu;

import com.golemlogix.init.ModBlocks;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Common base of the mod's menus: player slots, the usual shift-click and "ghost" filter slots.
 */
public abstract class GolemMenu extends AbstractContainerMenu {
    private static final Identifier ARS_SOURCE_JAR = Identifier.fromNamespaceAndPath("ars_nouveau", "source_jar");

    protected int containerSlotCount = 0;
    protected int playerStart = 0;
    protected int playerEnd = 0;

    protected GolemMenu(@Nullable MenuType<?> type, int id) {
        super(type, id);
    }

    /** Menu button "next redstone mode" (shared by all machines with a Redstone Module). */
    public static final int BUTTON_REDSTONE = 10;
    /** Width of the upgrade column to the right of the window. */
    public static final int UPGRADE_COLUMN = 26;

    protected int upgradeStart = -1;
    protected int upgradeEnd = -1;
    @Nullable
    protected UpgradeSpec upgradeSpec;

    /** Column of upgrade slots (x, y = top left of the first slot relative to the window). */
    protected void addUpgradeSlots(Container upgrades, UpgradeSpec spec, int x, int y) {
        upgradeSpec = spec;
        upgradeStart = this.slots.size();
        for (int i = 0; i < upgrades.getContainerSize(); i++) {
            this.addSlot(new UpgradeSlot(upgrades, i, x, y + i * 18, spec));
        }
        upgradeEnd = this.slots.size();
    }

    @Nullable
    public UpgradeSpec upgradeSpec() {
        return upgradeSpec;
    }

    public boolean hasUpgradeSlots() {
        return upgradeStart >= 0 && upgradeEnd > upgradeStart;
    }

    public int upgradeStart() {
        return upgradeStart;
    }

    public int upgradeEnd() {
        return upgradeEnd;
    }

    /** Whether an upgrade of this type sits in the window (the client shows buttons by it). */
    public boolean hasUpgrade(UpgradeType type) {
        if (!hasUpgradeSlots()) {
            return false;
        }
        for (int i = upgradeStart; i < upgradeEnd; i++) {
            if (this.slots.get(i).getItem().getItem() instanceof UpgradeItem up && up.type() == type) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- fuzzy ghosts

    /** Whether this ghost slot matches fuzzily now (a filter with a Fuzzy Card, an encoder input in fuzzy mode): the screen marks it. */
    public boolean fuzzyActive(int slotId) {
        return false;
    }

    /** Whether a middle-click (or Ctrl+click) on this ghost slot chooses its tag. */
    public boolean canCycleFuzzy(int slotId) {
        return fuzzyActive(slotId);
    }

    /** The tooltip line of a fuzzy ghost slot (null: none). */
    @Nullable
    public Component fuzzyLine(int slotId) {
        ItemStack s = slotId >= 0 && slotId < this.slots.size() ? this.slots.get(slotId).getItem() : ItemStack.EMPTY;
        return fuzzyActive(slotId) && !s.isEmpty() ? Fuzzy.describe(s) : null;
    }

    /** Server: the next tag of a fuzzy ghost (FuzzyCyclePayload). */
    public void cycleFuzzy(int slotId) {
        if (isGhost(slotId) && canCycleFuzzy(slotId)) {
            Slot slot = this.slots.get(slotId);
            if (!slot.getItem().isEmpty() && !FluidGhostItem.is(slot.getItem())) {
                slot.set(Fuzzy.cycle(slot.getItem()));
            }
        }
    }

    /** The slot window's tag list: the ghost takes the tag of its item with this id ("": none, the same item with any components). */
    public void setFuzzyTag(int slotId, String tagId) {
        if (!isGhost(slotId) || !canCycleFuzzy(slotId)) {
            return;
        }
        Slot slot = this.slots.get(slotId);
        ItemStack s = slot.getItem();
        if (s.isEmpty() || FluidGhostItem.is(s)) {
            return;
        }
        if (tagId.isEmpty()) {
            slot.set(Fuzzy.withTag(s, null));
            return;
        }
        for (var t : Fuzzy.tagsOf(s)) {
            if (t.location().toString().equals(tagId)) {
                slot.set(Fuzzy.withTag(s, t));
                return;
            }
        }
    }

    /** A non-empty item ghost in slot slotId (fuzziness applies to items only). */
    protected boolean itemGhost(int slotId) {
        if (!isGhost(slotId)) {
            return false;
        }
        ItemStack s = this.slots.get(slotId).getItem();
        return !s.isEmpty() && !FluidGhostItem.is(s);
    }

    /** Shift-click with an upgrade from the inventory goes to a free suitable upgrade slot. */
    protected boolean moveUpgradeIn(ItemStack stack) {
        if (!hasUpgradeSlots() || !(stack.getItem() instanceof UpgradeItem)) {
            return false;
        }
        for (int i = upgradeStart; i < upgradeEnd; i++) {
            Slot s = this.slots.get(i);
            if (!s.mayPlace(stack) || s.hasItem() && !ItemStack.isSameItemSameComponents(s.getItem(), stack)) {
                continue;
            }
            int n = Math.min(s.getMaxStackSize(stack) - s.getItem().getCount(), stack.getCount());
            if (n <= 0) {
                continue;
            }
            if (s.hasItem()) {
                s.getItem().grow(n);
                stack.shrink(n);
                s.setChanged();
            } else {
                s.setByPlayer(stack.split(n));
            }
            return true;
        }
        return false;
    }

    /** Upgrade slot: only a type that suits this machine and no more than the slot holds of it. */
    public static class UpgradeSlot extends Slot {
        private final UpgradeSpec spec;

        public UpgradeSlot(Container container, int slot, int x, int y, UpgradeSpec spec) {
            super(container, slot, x, y);
            this.spec = spec;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return UpgradeContainer.accepts(spec, stack, container, getContainerSlot());
        }

        @Override
        public int getMaxStackSize() {
            UpgradeType type = spec.typeAt(getContainerSlot());
            return type == null ? 1 : spec.perSlot(type);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return getMaxStackSize();
        }
    }

    protected void addPlayerSlots(Inventory inv, int x, int y) {
        playerStart = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, x + col * 18, y + 58));
        }
        playerEnd = this.slots.size();
    }

    /**
     * The game's merge pass tops up any slot holding the same item without asking mayPlace, so a shift-click poured real items into
     * a ghost filter showing that item (an upgrade shift-clicked into a bus with a ghost of it: what did not fit the upgrade slot was
     * gone). Ghost slots are left out of the move, the rest is the game's own.
     */
    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startSlot, int endSlot, boolean backwards) {
        int from = Math.max(0, startSlot);
        int to = Math.min(endSlot, this.slots.size());
        if (from >= to) {
            return false;
        }
        boolean ghosts = false;
        for (int i = from; i < to; i++) {
            if (this.slots.get(i) instanceof GhostSlot) {
                ghosts = true;
                break;
            }
        }
        if (!ghosts) {
            return super.moveItemStackTo(stack, from, to, backwards);
        }
        // runs of real slots between the ghosts, in the same order the game walks them
        boolean changed = false;
        List<int[]> runs = new ArrayList<>();
        int runStart = -1;
        for (int i = from; i <= to; i++) {
            boolean real = i < to && !(this.slots.get(i) instanceof GhostSlot);
            if (real && runStart < 0) {
                runStart = i;
            } else if (!real && runStart >= 0) {
                runs.add(new int[]{runStart, i});
                runStart = -1;
            }
        }
        if (backwards) {
            Collections.reverse(runs);
        }
        // first top up stacks in every run, then fill empty slots (as the game does over the whole range)
        for (int[] r : runs) {
            if (stack.isEmpty()) {
                break;
            }
            changed |= mergeOnly(stack, r[0], r[1], backwards);
        }
        for (int[] r : runs) {
            if (stack.isEmpty()) {
                break;
            }
            if (super.moveItemStackTo(stack, r[0], r[1], backwards)) {
                changed = true;
            }
        }
        return changed;
    }

    /** The game's merge pass alone, over real slots only. */
    private boolean mergeOnly(ItemStack stack, int start, int end, boolean backwards) {
        if (!stack.isStackable()) {
            return false;
        }
        boolean changed = false;
        for (int k = 0; k < end - start && !stack.isEmpty(); k++) {
            Slot slot = this.slots.get(backwards ? end - 1 - k : start + k);
            ItemStack target = slot.getItem();
            if (!target.isEmpty() && slot.mayPlace(stack) && ItemStack.isSameItemSameComponents(stack, target)) {
                int max = Math.min(slot.getMaxStackSize(target), target.getMaxStackSize());
                int move = Math.min(stack.getCount(), max - target.getCount());
                if (move > 0) {
                    target.grow(move);
                    stack.shrink(move);
                    slot.setChanged();
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Finalizes a quickMoveStack transfer after moving items out of slot; returns copy or EMPTY if nothing moved. */
    protected static ItemStack finishQuickMove(Slot slot, ItemStack stack, ItemStack copy, Player player) {
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || slot instanceof GhostSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < containerSlotCount || (hasUpgradeSlots() && index >= upgradeStart && index < upgradeEnd)) {
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (moveUpgradeIn(stack)) {
            // the upgrade went into its slot
        } else if (containerSlotCount <= 0 || !this.moveItemStackTo(stack, 0, containerSlotCount, false)) {
            return ItemStack.EMPTY;
        }

        return finishQuickMove(slot, stack, copy, player);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof GhostSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canDragTo(slot);
    }

    /** Copies stack into the first empty filter slot unless an equivalent item is already present in any filter slot. */
    protected static boolean copyToFilter(Container filters, int filterCount, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        int limit = Math.min(filterCount, filters.getContainerSize());
        int firstEmpty = -1;
        for (int i = 0; i < limit; i++) {
            ItemStack f = filters.getItem(i);
            if (f.isEmpty()) {
                if (firstEmpty < 0) {
                    firstEmpty = i;
                }
            } else if (ItemStack.isSameItemSameComponents(Fuzzy.plain(f), stack)) {
                return false;
            }
        }
        if (firstEmpty >= 0) {
            filters.setItem(firstEmpty, stack.copyWithCount(1));
            return true;
        }
        return false;
    }

    /** Server-side BUILD check for network-device menus when a Security Terminal is present on the network. */
    protected static boolean hasBuildPermission(Player player, @Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null || level.isClientSide() || !level.isLoaded(pos)) {
            return true;
        }
        return GolemNetwork.get(level, pos).hasPermission(player, SecurityPermission.BUILD);
    }

    /** Upper bound on fluid ghost amounts (1 000 buckets in mB) to prevent overflow in crafting calculations. */
    public static final int MAX_FLUID_GHOST = 1_000_000;

    /** A "ghost" slot: keeps a copy of an item for a filter/pattern without consuming anything. */
    public static class GhostSlot extends Slot {
        public GhostSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 99;
        }
    }

    public boolean isGhost(int slotId) {
        return slotId >= 0 && slotId < this.slots.size() && this.slots.get(slotId) instanceof GhostSlot;
    }

    /** A ghost from JEI (GhostSetPayload packet): a copy goes into the slot, the player does not need the item itself. */
    public void setGhost(Player player, int slotId, ItemStack stack) {
        if (!isGhost(slotId)) {
            return;
        }
        Slot slot = this.slots.get(slotId);
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
            return;
        }
        if (FluidGhostItem.is(stack)) {
            int mb = Math.clamp(FluidGhostItem.amount(stack), 1, MAX_FLUID_GHOST);
            slot.set(FluidGhostItem.withAmount(stack, mb));
            return;
        }
        slot.set(stack.copyWithCount(Math.clamp(stack.getCount(), 1, slot.getMaxStackSize())));
    }

    /**
     * A click on a ghost slot:
     * with an item on the cursor it sets a copy (left-click: 1, +1 if it is the same item; right-click: decreases);
     * without an item, left-click +1, right-click -1, Shift clears.
     * Returns true if the click was handled.
     */
    protected boolean handleGhostClick(int slotId, int button, ContainerInput clickType) {
        if (!isGhost(slotId)) {
            return false;
        }
        Slot slot = this.slots.get(slotId);
        ItemStack cur = slot.getItem();
        ItemStack carried = getCarried();

        if (clickType == ContainerInput.QUICK_MOVE) {
            slot.set(ItemStack.EMPTY);
            return true;
        }
        if (clickType != ContainerInput.PICKUP) {
            return true;
        }
        carried = ghostFor(carried);
        if (!carried.isEmpty()) {
            if (!cur.isEmpty() && Ingredients.key(cur).equals(Ingredients.key(carried))) {
                adjust(slot, cur, button == 1 ? -1 : 1);
            } else {
                slot.set(carried.copyWithCount(1));
            }
        } else if (!cur.isEmpty()) {
            adjust(slot, cur, button == 1 ? -1 : 1);
        }
        return true;
    }

    /** A Liquid Cryo-Source ghost is set with this many mB, and clicks change it by the same step. */
    public static final int FLUID_GHOST_STEP = 250;

    /**
     * What a click with this item puts into a ghost slot: a Source jar (a Cryo-Source Jar, an Ars Nouveau one or the Creative one)
     * stands for Liquid Cryo-Source, which has no bucket or other container of its own; anything else is itself.
     */
    public static ItemStack ghostFor(ItemStack carried) {
        if (carried.isEmpty()) {
            return carried;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(carried.getItem());
        boolean jar = id.equals(ARS_SOURCE_JAR)
                || carried.is(ModBlocks.CREATIVE_SOURCE_JAR.get().asItem())
                || carried.is(ModBlocks.CRYO_SOURCE_JAR.get().asItem());
        return jar ? FluidGhostItem.of(CryoSource.stack(FLUID_GHOST_STEP)) : carried;
    }

    private void adjust(Slot slot, ItemStack cur, int delta) {
        if (FluidGhostItem.is(cur)) {
            // a fluid ghost counts mB: a click changes it by a step
            int mb = FluidGhostItem.amount(cur) + delta * FLUID_GHOST_STEP;
            slot.set(mb <= 0 ? ItemStack.EMPTY : FluidGhostItem.withAmount(cur, Math.min(mb, 64_000)));
            return;
        }
        int n = cur.getCount() + delta;
        if (n <= 0) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.set(cur.copyWithCount(Math.min(n, slot.getMaxStackSize())));
        }
    }
}
