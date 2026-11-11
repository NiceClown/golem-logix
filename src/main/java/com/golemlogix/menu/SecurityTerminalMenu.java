package com.golemlogix.menu;

import com.golemlogix.blockentity.SecurityTerminalBlockEntity;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.IdentityPunchCardItem;
import com.golemlogix.security.SecurityPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SecurityTerminalMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 196;

    private final Container cards;
    private final BlockPos devicePos;
    @Nullable
    private final UUID ownerId;
    private final String ownerName;

    public static SecurityTerminalMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean hasOwner = buf.readBoolean();
        UUID ownerId = hasOwner ? buf.readUUID() : null;
        String ownerName = buf.readUtf();
        return new SecurityTerminalMenu(id, inv, new SimpleContainer(SecurityTerminalBlockEntity.SLOTS), pos, ownerId, ownerName);
    }

    public SecurityTerminalMenu(int id, Inventory inv, Container cards, BlockPos devicePos, @Nullable UUID ownerId, String ownerName) {
        super(ModMenus.SECURITY_TERMINAL.get(), id);
        this.cards = cards;
        this.devicePos = devicePos;
        this.ownerId = ownerId;
        this.ownerName = ownerName;

        // 16 card slots: 2 rows of 8
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 8; col++) {
                int index = row * 8 + col;
                this.addSlot(new Slot(cards, index, 16 + col * 18, 22 + row * 20) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.getItem() instanceof IdentityPunchCardItem;
                    }
                });
            }
        }
        this.containerSlotCount = cards.getContainerSize();
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
    }

    public BlockPos getDevicePos() {
        return devicePos;
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Container getCards() {
        return cards;
    }

    public void togglePermission(Player player, int slot, int permMask) {
        if (slot >= 0 && slot < cards.getContainerSize()) {
            ItemStack card = cards.getItem(slot);
            if (!card.isEmpty() && card.getItem() instanceof IdentityPunchCardItem) {
                int cur = IdentityPunchCardItem.getPermissions(card);
                IdentityPunchCardItem.setPermissions(card, cur ^ permMask);
                cards.setItem(slot, card);
                broadcastChanges();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < containerSlotCount) {
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.getItem() instanceof IdentityPunchCardItem) {
                if (!this.moveItemStackTo(stack, 0, containerSlotCount, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return cards.stillValid(player);
    }
}
