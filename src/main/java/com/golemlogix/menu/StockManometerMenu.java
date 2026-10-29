package com.golemlogix.menu;

import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StockManometerMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 184;

    private final Container filter;
    private final ContainerData data;
    private final BlockPos devicePos;

    public static StockManometerMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new StockManometerMenu(id, inv, new SimpleContainer(1),
                new SimpleContainer(UpgradeSpec.MANOMETER.slots()), new SimpleContainerData(6), pos);
    }

    public StockManometerMenu(int id, Inventory inv, Container filter, Container upgrades, ContainerData data, BlockPos devicePos) {
        super(ModMenus.STOCK_MANOMETER.get(), id);
        this.filter = filter;
        this.data = data;
        this.devicePos = devicePos;

        // Ghost filter slot
        this.addSlot(new GhostSlot(filter, 0, 26, 26));
        this.containerSlotCount = 1;

        addUpgradeSlots(upgrades, UpgradeSpec.MANOMETER, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    /** The mode side button: above the threshold -> below it -> proportional -> above. */
    public static final int BUTTON_MODE = 40;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_MODE) {
            data.set(0, (getMode() + 1) % 3);
            broadcastChanges();
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public BlockPos getDevicePos() {
        return devicePos;
    }

    public int getMode() {
        return data.get(0);
    }

    public int getThreshold() {
        return data.get(1);
    }

    public int getHysteresis() {
        return data.get(2);
    }

    public int getSignal() {
        return data.get(3);
    }

    public long getStock() {
        long low = data.get(4) & 0xFFFFL;
        long high = data.get(5) & 0xFFFFL;
        return (high << 16) | low;
    }

    public void setConfig(int mode, int threshold, int hysteresis) {
        data.set(0, mode);
        data.set(1, Math.max(1, threshold));
        data.set(2, Math.max(0, hysteresis));
        broadcastChanges();
    }

    public void setKeep(int slot, int amount) {
        if (slot == 0) {
            data.set(1, Math.max(1, amount));
            broadcastChanges();
        }
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (handleGhostClick(slotId, button, clickType)) {
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
        if (!stack.isEmpty()) {
            filter.setItem(0, stack.copyWithCount(1));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return filter.stillValid(player);
    }

    @Override
    public boolean fuzzyActive(int slotId) {
        return slotId == 0 && itemGhost(0) && hasUpgrade(UpgradeType.FUZZY);
    }
}
