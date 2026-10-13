package com.golemlogix.menu;

import com.golemlogix.blockentity.InterfaceBlockEntity;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Interface window: the "keep in stock" row (ghosts; the amount is a separate number in the slot settings window, the gear under the
 * slot or a click with an empty hand), under it the output row (what is in stock now) and the input row (items can be put here
 * by hand and go straight to the network). On the right the upgrade column, on the left the redstone mode.
 * <p>
 * Config slots can be neither scrolled with the wheel nor "dragged": any click except a click with an item (set) and a Shift-click
 * (clear) is ignored; scrolling mods used to manage to pull items out of them.
 */
public class InterfaceMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 214;
    /** The config row, under it the slot buttons (gear and mode, TOGGLE_Y, height 8), the stock row and the input row. */
    public static final int CONFIG_Y = 28, TOGGLE_Y = 46, STOCK_Y = 68, INBOX_Y = 98;
    /** Menu buttons "switch the mode of slot i": BUTTON_MODE + i. */
    public static final int BUTTON_MODE = 20;

    private final Container config;
    private final ContainerData data;
    /** The interface itself (on the server): the amount and the item of the slots change through it. */
    @Nullable
    private final InterfaceBlockEntity be;

    public static InterfaceMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new InterfaceMenu(id, inv, new SimpleContainer(InterfaceBlockEntity.SLOTS), new SimpleContainer(InterfaceBlockEntity.SLOTS),
                new SimpleContainer(InterfaceBlockEntity.SLOTS), new SimpleContainer(UpgradeSpec.INTERFACE.slots()),
                new SimpleContainerData(InterfaceBlockEntity.DATA_SIZE), null);
    }

    public InterfaceMenu(int id, Inventory inv, Container config, Container stock, Container inbox, Container upgrades, ContainerData data,
                         @Nullable InterfaceBlockEntity be) {
        super(ModMenus.INTERFACE.get(), id);
        this.config = config;
        this.data = data;
        this.be = be;
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            this.addSlot(new GhostSlot(config, i, 8 + i * 18, CONFIG_Y));
        }
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            this.addSlot(new TakeOnlySlot(stock, i, 8 + i * 18, STOCK_Y));
        }
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            this.addSlot(new Slot(inbox, i, 8 + i * 18, INBOX_Y));
        }
        this.containerSlotCount = InterfaceBlockEntity.SLOTS * 3;
        addUpgradeSlots(upgrades, UpgradeSpec.INTERFACE, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    /** A slot the player can only take from (putting in is not allowed: the interface fills the stock itself). */
    static class TakeOnlySlot extends Slot {
        TakeOnlySlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    public ItemStack configItem(int i) {
        return config.getItem(i);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(0));
    }

    /** Slot i is in "item" mode (keeps the buckets themselves), not "fluid". */
    public boolean itemMode(int i) {
        return (data.get(1) & (1 << i)) != 0;
    }

    /** The config slot keeps fluid (it holds a container with fluid and the mode is "fluid"). */
    public boolean isFluidSlot(int i) {
        return !itemMode(i) && !InterfaceBlockEntity.containedFluid(config.getItem(i)).isEmpty();
    }

    /** Fluid stock of slot i, mB (accurate to ten). */
    public int fluidAmount(int i) {
        return data.get(InterfaceBlockEntity.DATA_FLUID + i) * 10;
    }

    /** The set amount of a slot: items or mB. */
    public int amount(int i) {
        return (data.get(InterfaceBlockEntity.DATA_AMOUNT_LO + i) & 0x7FFF) | ((data.get(InterfaceBlockEntity.DATA_AMOUNT_HI + i) & 0x7FFF) << 15);
    }

    /** The slot amount limit: up to 256 buckets in mB for fluid, a stack for an item. */
    public int maxAmount(int i) {
        ItemStack cfg = config.getItem(i);
        return cfg.isEmpty() ? 0 : isFluidSlot(i) ? InterfaceBlockEntity.MAX_FLUID : cfg.getMaxStackSize();
    }

    /** Server: set a slot amount (InterfaceAmountPayload packet from the settings window). */
    public void setAmount(int i, int value) {
        if (be != null && i >= 0 && i < InterfaceBlockEntity.SLOTS) {
            be.setAmount(i, value);
        }
    }

    /** Server: clear a config slot. */
    public void clearSlot(int i) {
        if (be != null && i >= 0 && i < InterfaceBlockEntity.SLOTS) {
            be.setSlot(i, ItemStack.EMPTY, 0);
        }
    }

    /** Default amount for a new ghost: a bucket for fluid, for an item as many as in the cursor stack. */
    private int defaultAmount(int i, ItemStack stack) {
        boolean fluid = !itemMode(i) && !InterfaceBlockEntity.containedFluid(stack).isEmpty();
        return fluid ? stack.getCount() * InterfaceBlockEntity.MB_PER_BUCKET : stack.getCount();
    }

    @Override
    public void setGhost(Player player, int slotId, ItemStack stack) {
        if (isGhost(slotId) && be != null) {
            int i = this.slots.get(slotId).getContainerSlot();
            be.setSlot(i, stack, defaultAmount(i, stack.copyWithCount(1)));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            data.set(0, redstoneMode().next(true).ordinal());
            return true;
        }
        if (id >= BUTTON_MODE && id < BUTTON_MODE + InterfaceBlockEntity.SLOTS) {
            data.set(1, data.get(1) ^ (1 << (id - BUTTON_MODE)));
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isGhost(slotId)) {
            int i = this.slots.get(slotId).getContainerSlot();
            ItemStack carried = getCarried();
            if (clickType == ClickType.QUICK_MOVE) {
                clearSlot(i);
            } else if (clickType == ClickType.PICKUP && !carried.isEmpty() && be != null) {
                // an item on the cursor: set it (the amount follows the cursor stack, then it is edited in the settings window)
                be.setSlot(i, carried, defaultAmount(i, carried));
            }
            // everything else (wheel, dragging, a click with an empty hand, which opens the settings window on the client) is skipped
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = this.slots.get(index).getItem();
        boolean fromPlayer = index >= playerStart && index < playerEnd;
        if (!fromPlayer || stack.getItem() instanceof UpgradeItem) {
            return super.quickMoveStack(player, index);
        }
        // shift-click from the inventory goes to the input row: the item goes straight to the network (when terminals are far away)
        int inboxStart = InterfaceBlockEntity.SLOTS * 2;
        ItemStack copy = stack.copy();
        if (!moveItemStackTo(stack, inboxStart, inboxStart + InterfaceBlockEntity.SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        this.slots.get(index).setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return config.stillValid(player);
    }
}
