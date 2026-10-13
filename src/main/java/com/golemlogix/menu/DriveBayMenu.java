package com.golemlogix.menu;

import com.golemlogix.blockentity.CopperDriveBayBlockEntity;
import com.golemlogix.init.ModMenus;
import com.golemlogix.system.DiskStorage;
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
 * Drive cabinet menu: 10 slots (2 columns x 5 rows, like the cassettes in the cabinet itself) for magnetic item and fluid disks.
 */
public class DriveBayMenu extends GolemMenu {
    public static final int COLS = 2;
    public static final int ROWS = 5;
    public static final int IMAGE_HEIGHT = 212;

    private final Container container;
    private final ContainerData priority;

    public static DriveBayMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new DriveBayMenu(id, inv, new SimpleContainer(CopperDriveBayBlockEntity.SLOTS), new SimpleContainerData(1));
    }

    public DriveBayMenu(int id, Inventory inv, Container container, ContainerData priority) {
        super(ModMenus.DRIVE_BAY.get(), id);
        this.container = container;
        this.priority = priority;
        checkContainerSize(container, CopperDriveBayBlockEntity.SLOTS);
        container.startOpen(inv.player);

        for (int i = 0; i < CopperDriveBayBlockEntity.SLOTS; i++) {
            this.addSlot(new DiskSlot(container, i, 71 + (i % COLS) * 18, 19 + (i / COLS) * 18));
        }
        this.containerSlotCount = CopperDriveBayBlockEntity.SLOTS;
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(priority);
    }

    public int getPriority() {
        return priority.get(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return Priority.handle(priority, id);
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

    private static class DiskSlot extends Slot {
        DiskSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return DiskStorage.isDisk(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
