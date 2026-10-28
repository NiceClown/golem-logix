package com.golemlogix.menu;

import com.golemlogix.blockentity.AnnihilationBusBlockEntity;
import com.golemlogix.init.ModMenus;
import com.golemlogix.items.UpgradeItem;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Annihilation Bus window: the 3x3 filter (blocks to work on, or not to), the golem's tool slot, the upgrades on the right and
 * the click settings on the left (redstone, left / right click, sneaking, allow / deny list, grown only).
 */
public class AnnihilationBusMenu extends GolemMenu {
    public static final int IMAGE_WIDTH = 176, IMAGE_HEIGHT = 212;
    public static final int FILTER_X = 29, TOOL_X = 116, TOOL_Y = 38;
    /** The tool slot comes right after the nine filter slots. */
    public static final int TOOL_SLOT_INDEX = AnnihilationBusBlockEntity.FILTER_SLOTS;
    public static final int BUTTON_CLICK = 20, BUTTON_SNEAK = 21, BUTTON_LIST = 22, BUTTON_GROWN = 23;

    private final Container filters;
    private final ContainerData data;
    private final int toolSlot;

    public static AnnihilationBusMenu client(int id, Inventory inv) {
        return new AnnihilationBusMenu(id, inv, new SimpleContainer(AnnihilationBusBlockEntity.FILTER_SLOTS), new SimpleContainer(1),
                new SimpleContainer(UpgradeSpec.ANNIHILATION_BUS.slots()), new SimpleContainerData(5));
    }

    public AnnihilationBusMenu(int id, Inventory inv, Container filters, Container tool, Container upgrades, ContainerData data) {
        super(ModMenus.ANNIHILATION_BUS.get(), id);
        this.filters = filters;
        this.data = data;
        for (int i = 0; i < filters.getContainerSize(); i++) {
            addSlot(new GhostSlot(filters, i, FILTER_X + 2 + (i % 3) * 18, 20 + (i / 3) * 18));
        }
        toolSlot = slots.size();
        addSlot(new Slot(tool, 0, TOOL_X, TOOL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !(stack.getItem() instanceof UpgradeItem);
            }
        });
        this.containerSlotCount = toolSlot + 1;
        addUpgradeSlots(upgrades, UpgradeSpec.ANNIHILATION_BUS, IMAGE_WIDTH + 5, 8);
        addPlayerSlots(inv, 8, IMAGE_HEIGHT - 82);
        addDataSlots(data);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(0));
    }

    public boolean rightClick() {
        return data.get(1) != 0;
    }

    public boolean sneak() {
        return data.get(2) != 0;
    }

    public boolean denyList() {
        return data.get(3) != 0;
    }

    public boolean grownOnly() {
        return data.get(4) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_REDSTONE -> data.set(0, redstoneMode().next(true).ordinal());
            case BUTTON_CLICK -> data.set(1, rightClick() ? 0 : 1);
            case BUTTON_SNEAK -> data.set(2, sneak() ? 0 : 1);
            case BUTTON_LIST -> data.set(3, denyList() ? 0 : 1);
            case BUTTON_GROWN -> data.set(4, grownOnly() ? 0 : 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (handleGhostClick(slotId, button, clickType)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * From the inventory: an upgrade into its slot, a tool (anything that wears) into the empty tool slot, anything else is copied
     * into the filter. From the tool slot or the upgrades: back into the inventory.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        boolean fromPlayer = index >= playerStart && index < playerEnd;
        if (!fromPlayer || stack.getItem() instanceof UpgradeItem) {
            return super.quickMoveStack(player, index);
        }
        Slot tool = slots.get(toolSlot);
        if (stack.isDamageableItem() && !tool.hasItem()) {
            tool.setByPlayer(stack.split(stack.getCount()));
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < filters.getContainerSize(); i++) {
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

    @Override
    public boolean fuzzyActive(int slotId) {
        return slotId < filters.getContainerSize() && itemGhost(slotId) && hasUpgrade(UpgradeType.FUZZY);
    }
}
