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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Bus menu: 3x3 ghost filter slots, priority, upgrade slots to the right of the window and buttons on the left: the redstone
 * mode (with a Redstone Module), the order (Export and Formation Buses), allow / deny list (Import Bus and Periscope) and the
 * periscope's access (both ways, insert only, extract only), as the storage bus of AE2 or RS.
 */
public class BusMenu extends GolemMenu {
    public static final int IMAGE_HEIGHT = 212;
    public static final int IMAGE_WIDTH = 176;

    /** Left edge of the filter grid frame: the grid and the priority panel (54) stand symmetrically in the window. */
    public static final int FILTER_X = 29;

    public static final int KIND_IMPORT = 0;
    public static final int KIND_EXPORT = 1;
    public static final int KIND_PERISCOPE = 2;
    /** The Formation Bus: an export-bus filter and upgrades, no priority. */
    public static final int KIND_FORMATION = 3;
    /** The order button of the Export and Formation Buses (in order / round robin / at random). */
    public static final int BUTTON_ORDER = 30;
    /** Allow / deny list (Import Bus, Periscope). */
    public static final int BUTTON_LIST = 31;
    /** The periscope's access: both ways, insert only, extract only. */
    public static final int BUTTON_ACCESS = 32;

    private final Container filters;
    private final int kind;
    private final ContainerData data;

    public static UpgradeSpec specOf(int kind) {
        return switch (kind) {
            case KIND_EXPORT, KIND_FORMATION -> UpgradeSpec.EXPORT_BUS;
            case KIND_IMPORT -> UpgradeSpec.IMPORT_BUS;
            default -> UpgradeSpec.PERISCOPE;
        };
    }

    public static BusMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        return new BusMenu(id, inv, new SimpleContainer(BusBlockEntity.FILTER_SLOTS),
                new SimpleContainer(specOf(kind).slots()), kind, new SimpleContainerData(kind == KIND_PERISCOPE ? 4 : 3));
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

    public boolean isFormation() {
        return kind == KIND_FORMATION;
    }

    /** How much the container in front should hold of what a filter ghost names (0: export without a limit). */
    public static int keep(ItemStack filter) {
        return filter.getOrDefault(com.golemlogix.init.ModDataComponents.KEEP_AMOUNT.get(), 0);
    }

    public int keepAt(int slot) {
        return slot >= 0 && slot < filters.getContainerSize() ? keep(filters.getItem(slot)) : 0;
    }

    public ItemStack filterAt(int slot) {
        return slot >= 0 && slot < filters.getContainerSize() ? filters.getItem(slot) : ItemStack.EMPTY;
    }

    /** The Export Bus slot window: how much to keep in the container (0: no limit), or a negative amount to clear the slot. */
    public void setKeep(int slot, int amount) {
        if (!isExport() || slot < 0 || slot >= filters.getContainerSize() || filters.getItem(slot).isEmpty()) {
            return;
        }
        if (amount < 0) {
            filters.setItem(slot, ItemStack.EMPTY);
            return;
        }
        ItemStack f = filters.getItem(slot).copy();
        if (amount == 0) {
            f.remove(com.golemlogix.init.ModDataComponents.KEEP_AMOUNT.get());
        } else {
            f.set(com.golemlogix.init.ModDataComponents.KEEP_AMOUNT.get(), amount);
        }
        filters.setItem(slot, f);
    }

    /** The Export and Formation Buses go through their filter slots in an order. */
    public boolean hasOrder() {
        return isFormation() || isExport();
    }

    /** The order of the filter slots (FormationBusBlockEntity.IN_ORDER, ROUND_ROBIN, RANDOM). */
    public int order() {
        return hasOrder() ? data.get(2) : 0;
    }

    /** The Import Bus and the Periscope filter as an allow or a deny list. */
    public boolean hasList() {
        return isPeriscope() || kind == KIND_IMPORT;
    }

    public boolean denyList() {
        return hasList() && data.get(isPeriscope() ? 3 : 2) != 0;
    }

    /** The periscope's access (PeriscopeBlockEntity.BOTH, INSERT_ONLY, EXTRACT_ONLY). */
    public int access() {
        return isPeriscope() ? data.get(2) : 0;
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
        if (id == BUTTON_ORDER && hasOrder()) {
            data.set(2, (order() + 1) % 3);
            return true;
        }
        if (id == BUTTON_LIST && hasList()) {
            data.set(isPeriscope() ? 3 : 2, denyList() ? 0 : 1);
            return true;
        }
        if (id == BUTTON_ACCESS && isPeriscope()) {
            data.set(2, (access() + 1) % 3);
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
