package com.golemlogix.menu;

import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.TelegraphLinkCardItem;
import com.golemlogix.items.TelegraphRangeCardItem;
import com.golemlogix.system.TelegraphLinks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Cryo-Source Telegraph window: the range card, the Write slot and the Memory slot (signed; while one holds a card the other
 * accepts nothing), the link state and the big Reset button.
 */
public class TelegraphMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 214;
    public static final int SLOT_Y = 32, RANGE_X = 22, WRITE_X = 80, MEMORY_X = 138;
    public static final int BUTTON_RESET = 1;

    @Nullable
    private final TelegraphBlockEntity be;
    private final Container cards;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public static TelegraphMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return new TelegraphMenu(id, inv, null, new SimpleContainer(3), new SimpleContainerData(3), ContainerLevelAccess.NULL);
    }

    public TelegraphMenu(int id, Inventory inv, @Nullable TelegraphBlockEntity be, Container slots, ContainerData data,
                         ContainerLevelAccess access) {
        super(ModMenus.TELEGRAPH.get(), id);
        this.be = be;
        this.cards = slots;
        this.data = data;
        this.access = access;
        this.addSlot(new CardSlot(slots, TelegraphBlockEntity.SLOT_RANGE, RANGE_X, SLOT_Y));
        this.addSlot(new CardSlot(slots, TelegraphBlockEntity.SLOT_WRITE, WRITE_X, SLOT_Y));
        this.addSlot(new CardSlot(slots, TelegraphBlockEntity.SLOT_MEMORY, MEMORY_X, SLOT_Y));
        this.containerSlotCount = 3;
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    /**
     * Shift-click from the inventory sorts linkage cards by meaning: blank cards go to Write (onto the cards already there), a written
     * card goes to Memory, so a card brought from the base is never rewritten by accident. Everything else as usual.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot from = this.slots.get(index);
        if (index < containerSlotCount || from == null || !TelegraphLinkCardItem.isCard(from.getItem())) {
            return super.quickMoveStack(player, index);
        }
        ItemStack stack = from.getItem();
        ItemStack copy = stack.copy();
        Slot write = this.slots.get(TelegraphBlockEntity.SLOT_WRITE);
        Slot memory = this.slots.get(TelegraphBlockEntity.SLOT_MEMORY);
        if (TelegraphLinkCardItem.link(stack) == null || ItemStack.isSameItemSameComponents(stack, write.getItem())) {
            addToWrite(write, stack, stack.getCount());
        } else if (!memory.hasItem() && memory.mayPlace(stack)) {
            memory.setByPlayer(stack.split(1));
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        return copy;
    }

    /** Cards clicked onto the Write slot join the cards there even when those are already written (instead of swapping). */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId == TelegraphBlockEntity.SLOT_WRITE && type == ClickType.PICKUP && (button == 0 || button == 1)) {
            Slot write = this.slots.get(slotId);
            ItemStack carried = getCarried();
            if (write.hasItem() && TelegraphLinkCardItem.isCard(carried) && !ItemStack.isSameItemSameComponents(carried, write.getItem())
                    && addToWrite(write, carried, button == 0 ? carried.getCount() : 1)) {
                return;
            }
        }
        super.clicked(slotId, button, type, player);
    }

    /** Moves up to n cards of the stack into the Write slot; the server writes the whole slot with this telegraph's record. */
    private static boolean addToWrite(Slot write, ItemStack stack, int n) {
        if (!write.mayPlace(stack)) {
            return false;
        }
        ItemStack there = write.getItem();
        int k = Math.min(n, Math.min(stack.getCount(), write.getMaxStackSize(stack) - there.getCount()));
        if (k <= 0) {
            return false;
        }
        if (there.isEmpty()) {
            write.setByPlayer(stack.split(k));
        } else {
            there.grow(k);
            stack.shrink(k);
            write.setChanged();
        }
        return true;
    }

    /**
     * A card slot of the right kind: Range and Memory hold one card, Write a whole stack (all written at once); Write and Memory
     * exclude each other (the client sees the other slot's item too).
     */
    private class CardSlot extends Slot {
        CardSlot(Container c, int i, int x, int y) {
            super(c, i, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return switch (getContainerSlot()) {
                case TelegraphBlockEntity.SLOT_RANGE -> stack.getItem() instanceof TelegraphRangeCardItem;
                case TelegraphBlockEntity.SLOT_WRITE -> TelegraphLinkCardItem.isCard(stack) && cards.getItem(TelegraphBlockEntity.SLOT_MEMORY).isEmpty();
                default -> TelegraphLinkCardItem.isCard(stack) && cards.getItem(TelegraphBlockEntity.SLOT_WRITE).isEmpty();
            };
        }

        @Override
        public int getMaxStackSize() {
            return getContainerSlot() == TelegraphBlockEntity.SLOT_WRITE ? TelegraphBlockEntity.WRITE_STACK : 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(getMaxStackSize(), stack.getMaxStackSize());
        }
    }

    /** The slot holds nothing and does not accept a card now, because the other link slot is in use. */
    public boolean blocked(int slot) {
        int other = slot == TelegraphBlockEntity.SLOT_WRITE ? TelegraphBlockEntity.SLOT_MEMORY : TelegraphBlockEntity.SLOT_WRITE;
        return slot != TelegraphBlockEntity.SLOT_RANGE && cards.getItem(slot).isEmpty() && !cards.getItem(other).isEmpty();
    }

    public ItemStack slotItem(int slot) {
        return cards.getItem(slot);
    }

    public TelegraphLinks.Status status() {
        TelegraphLinks.Status[] all = TelegraphLinks.Status.values();
        return all[Math.max(0, Math.min(all.length - 1, data.get(0)))];
    }

    public int receivers() {
        return data.get(1);
    }

    public boolean active() {
        return data.get(2) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_RESET && be != null) {
            be.reset();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CRYO_SOURCE_TELEGRAPH.get());
    }
}
