package com.golemlogix.menu;

import com.golemlogix.items.UpgradeItem;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Common base of the mod's menus: player slots, the usual shift-click and "ghost" filter slots.
 */
public abstract class GolemMenu extends AbstractContainerMenu {
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

    /** Shift-click with an upgrade from the inventory goes to a free suitable upgrade slot. */
    protected boolean moveUpgradeIn(ItemStack stack) {
        if (!hasUpgradeSlots() || !(stack.getItem() instanceof UpgradeItem)) {
            return false;
        }
        for (int i = upgradeStart; i < upgradeEnd; i++) {
            Slot s = this.slots.get(i);
            if (!s.hasItem() && s.mayPlace(stack)) {
                s.setByPlayer(stack.split(1));
                return true;
            }
        }
        return false;
    }

    /** Upgrade slot: one item, only a type that suits this machine and no more than the limit. */
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
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
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

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

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
        slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(Math.max(1, Math.min(stack.getCount(), slot.getMaxStackSize()))));
    }

    /**
     * A click on a ghost slot:
     * with an item on the cursor it sets a copy (left-click: 1, +1 if it is the same item; right-click: decreases);
     * without an item, left-click +1, right-click -1, Shift clears.
     * Returns true if the click was handled.
     */
    protected boolean handleGhostClick(int slotId, int button, ClickType clickType) {
        if (!isGhost(slotId)) {
            return false;
        }
        Slot slot = this.slots.get(slotId);
        ItemStack cur = slot.getItem();
        ItemStack carried = getCarried();

        if (clickType == ClickType.QUICK_MOVE) {
            slot.set(ItemStack.EMPTY);
            return true;
        }
        if (clickType != ClickType.PICKUP) {
            return true;
        }
        if (!carried.isEmpty()) {
            if (!cur.isEmpty() && ItemStack.isSameItemSameComponents(cur, carried)) {
                adjust(slot, cur, button == 1 ? -1 : 1);
            } else {
                slot.set(carried.copyWithCount(1));
            }
        } else if (!cur.isEmpty()) {
            adjust(slot, cur, button == 1 ? -1 : 1);
        }
        return true;
    }

    private void adjust(Slot slot, ItemStack cur, int delta) {
        int n = cur.getCount() + delta;
        if (n <= 0) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.set(cur.copyWithCount(Math.min(n, slot.getMaxStackSize())));
        }
    }
}
