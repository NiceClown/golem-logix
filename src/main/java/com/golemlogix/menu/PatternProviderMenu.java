package com.golemlogix.menu;

import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModMenus;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Pattern provider menu: 9 slots for encoded punch cards, a column of return slots on the right (what is put there goes into
 * the network), the wait-for-result mode, a Redstone Module slot.
 */
public class PatternProviderMenu extends GolemMenu {
    public static final int IMAGE_HEIGHT = 204;
    public static final int IMAGE_WIDTH = 176;
    public static final int BUTTON_BLOCKING = 11;
    /** Next feeding side (all -> down -> up -> north -> south -> west -> east -> all). */
    public static final int BUTTON_OUTPUT = 12;
    /** The column of return slots. */
    public static final int RETURN_X = 140, RETURN_Y = 20;

    private final Container container;
    private final ContainerData data;

    public static PatternProviderMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new PatternProviderMenu(id, inv, new SimpleContainer(PatternProviderBlockEntity.SLOTS),
                new SimpleContainer(PatternProviderBlockEntity.RETURN_SLOTS), new SimpleContainer(UpgradeSpec.REDSTONE_ONLY.slots()),
                new SimpleContainerData(3));
    }

    public PatternProviderMenu(int id, Inventory inv, Container container, Container returns, Container upgrades, ContainerData data) {
        super(ModMenus.PATTERN_PROVIDER.get(), id);
        this.container = container;
        this.data = data;
        container.startOpen(inv.player);
        for (int i = 0; i < PatternProviderBlockEntity.SLOTS; i++) {
            this.addSlot(new Slot(container, i, 62 + (i % 3) * 18, 20 + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.has(ModDataComponents.PATTERN.get());
                }
            });
        }
        this.containerSlotCount = PatternProviderBlockEntity.SLOTS;
        for (int i = 0; i < PatternProviderBlockEntity.RETURN_SLOTS; i++) {
            this.addSlot(new Slot(returns, i, RETURN_X, RETURN_Y + i * 18));
        }
        addUpgradeSlots(upgrades, UpgradeSpec.REDSTONE_ONLY, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    /** Shift-click on a return slot takes its items back into the inventory (shift-clicks from the inventory only fill the card slots). */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        int first = PatternProviderBlockEntity.SLOTS;
        if (index >= first && index < first + PatternProviderBlockEntity.RETURN_SLOTS) {
            Slot slot = this.slots.get(index);
            if (!slot.hasItem()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem();
            ItemStack copy = stack.copy();
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return copy;
        }
        return super.quickMoveStack(player, index);
    }

    public boolean isBlocking() {
        return data.get(0) != 0;
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(1));
    }

    public com.golemlogix.blocks.ProviderOutput output() {
        return com.golemlogix.blocks.ProviderOutput.byId(data.get(2));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_BLOCKING) {
            data.set(0, isBlocking() ? 0 : 1);
            return true;
        }
        if (id == BUTTON_REDSTONE) {
            data.set(1, redstoneMode().next(true).ordinal());
            return true;
        }
        if (id == BUTTON_OUTPUT) {
            data.set(2, output().next().ordinal());
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
