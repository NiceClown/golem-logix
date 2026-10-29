package com.golemlogix.menu;

import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class StockRequesterMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 166;
    public static final int GRID_X = 62;
    public static final int GRID_Y = 20;

    private final Container filters;
    private final ContainerData data;
    private final BlockPos devicePos;

    public static StockRequesterMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return new StockRequesterMenu(id, inv, new SimpleContainer(StockRequesterBlockEntity.FILTER_SLOTS),
                new SimpleContainerData(StockRequesterBlockEntity.FILTER_SLOTS), pos);
    }

    public StockRequesterMenu(int id, Inventory inv, Container filters, ContainerData data, BlockPos devicePos) {
        super(ModMenus.STOCK_REQUESTER.get(), id);
        this.filters = filters;
        this.data = data;
        this.devicePos = devicePos;

        for (int i = 0; i < StockRequesterBlockEntity.FILTER_SLOTS; i++) {
            this.addSlot(new GhostSlot(filters, i, GRID_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        this.containerSlotCount = StockRequesterBlockEntity.FILTER_SLOTS;

        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public BlockPos getDevicePos() {
        return devicePos;
    }

    public int getSlotStatus(int slot) {
        return data.get(slot);
    }

    public void setKeep(int slot, int amount) {
        if (slot < 0 || slot >= filters.getContainerSize() || filters.getItem(slot).isEmpty()) {
            return;
        }
        if (amount < 0) {
            filters.setItem(slot, ItemStack.EMPTY);
            return;
        }
        ItemStack f = filters.getItem(slot).copy();
        if (amount == 0) {
            f.remove(ModDataComponents.KEEP_AMOUNT.get());
        } else {
            f.set(ModDataComponents.KEEP_AMOUNT.get(), amount);
        }
        filters.setItem(slot, f);
        broadcastChanges();
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
        if (!fromPlayer || stack.isEmpty()) {
            return super.quickMoveStack(player, index);
        }
        for (int i = 0; i < containerSlotCount; i++) {
            ItemStack f = filters.getItem(i);
            if (!f.isEmpty() && ItemStack.isSameItemSameComponents(f, stack)) {
                break;
            }
            if (f.isEmpty()) {
                filters.setItem(i, stack.copyWithCount(1));
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return filters.stillValid(player);
    }
}
