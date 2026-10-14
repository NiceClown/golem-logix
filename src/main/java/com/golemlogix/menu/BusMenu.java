package com.golemlogix.menu;

import com.golemlogix.blockentity.BusBlockEntity;
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
import net.minecraft.world.item.ItemStack;

/**
 * Bus menu: 3x3 ghost filter slots (the periscope has no filter), priority, upgrade slots to the right of the window
 * and the redstone mode (a button on the left when a Redstone Module is installed).
 */
public class BusMenu extends GolemMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int IMAGE_WIDTH = 176;

    /** Left edge of the filter grid frame: the grid and the priority panel (54) stand symmetrically in the window. */
    public static final int FILTER_X = 29;

    public static final int KIND_IMPORT = 0;
    public static final int KIND_EXPORT = 1;
    public static final int KIND_PERISCOPE = 2;

    private final Container filters;
    private final int kind;
    private final ContainerData data;

    public static UpgradeSpec specOf(int kind) {
        return switch (kind) {
            case KIND_EXPORT -> UpgradeSpec.EXPORT_BUS;
            case KIND_IMPORT -> UpgradeSpec.IMPORT_BUS;
            default -> UpgradeSpec.REDSTONE_ONLY;
        };
    }

    public static BusMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        return new BusMenu(id, inv, new SimpleContainer(kind == KIND_PERISCOPE ? 0 : BusBlockEntity.FILTER_SLOTS),
                new SimpleContainer(specOf(kind).slots()), kind, new SimpleContainerData(2));
    }

    public BusMenu(int id, Inventory inv, Container filters, Container upgrades, int kind, ContainerData data) {
        super(ModMenus.BUS.get(), id);
        this.filters = filters;
        this.kind = kind;
        this.data = data;
        for (int i = 0; i < filters.getContainerSize(); i++) {
            this.addSlot(new GhostSlot(filters, i, FILTER_X + 2 + (i % 3) * 18, 20 + (i / 3) * 18));
        }
        this.containerSlotCount = filters.getContainerSize();
        addUpgradeSlots(upgrades, specOf(kind), IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public boolean isExport() {
        return kind == KIND_EXPORT;
    }

    public boolean isPeriscope() {
        return kind == KIND_PERISCOPE;
    }

    public int getPriority() {
        return data.get(0);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(1));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            data.set(1, redstoneMode().next(!isPeriscope()).ordinal());
            return true;
        }
        return Priority.handle(new ContainerData() {
            @Override
            public int get(int index) {
                return data.get(0);
            }

            @Override
            public void set(int index, int value) {
                data.set(0, value);
            }

            @Override
            public int getCount() {
                return 1;
            }
        }, id);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (handleGhostClick(slotId, button, clickType)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack stack = this.slots.get(index).getItem();
        boolean fromPlayer = index >= playerStart && index < playerEnd;
        // upgrades go into their slots (and back), everything else from the inventory is copied into the filter
        if (!fromPlayer || stack.getItem() instanceof UpgradeItem) {
            return super.quickMoveStack(player, index);
        }
        if (!stack.isEmpty()) {
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
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return filters.stillValid(player);
    }

    /** With a Fuzzy Card the item filters match fuzzily, and a middle-click chooses a filter's tag. */
    @Override
    public boolean fuzzyActive(int slotId) {
        return slotId < filters.getContainerSize() && itemGhost(slotId) && hasUpgrade(com.golemlogix.upgrade.UpgradeType.FUZZY);
    }
}
