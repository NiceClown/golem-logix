package com.golemlogix.menu;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModMenus;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeSpec;
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

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Punch Card Catalog window: a scrolling 9 x 6 row of slots for punch cards with crafting-table recipes, on the right a column
 * with the Redstone Module and, under it, the Pattern Provider slot (up to a stack; each opens 27 more places).
 * The window slots look into a "window" of the catalog that the server shifts by rows (MenuScrollPayload packet).
 */
public class CardCatalogMenu extends GolemMenu implements ScrollableMenu {
    public static final int IMAGE_WIDTH = 190;
    public static final int IMAGE_HEIGHT = 238;
    public static final int COLS = 9, ROWS = 6, WINDOW = COLS * ROWS;
    public static final int GRID_X = 8, GRID_Y = 18;
    public static final int SCROLL_X = GRID_X + COLS * 18 + 4, INFO_Y = GRID_Y + ROWS * 18 + 3;
    public static final int DATA_CAPACITY = 0, DATA_COUNT = 1, DATA_REDSTONE = 2, DATA_ROW = 3, DATA_VIEW = 4, DATA_SIZE = 5;

    @Nullable
    private final CardCatalogBlockEntity be;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final int providerSlot;
    /** Server: the first visible row, the search and the catalog places found (null = no search, all places in a row). */
    private int row = 0;
    private String filter = "";
    @Nullable
    private int[] view;

    public static CardCatalogMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new CardCatalogMenu(id, inv, null, new SimpleContainer(WINDOW), new SimpleContainer(1),
                new SimpleContainer(UpgradeSpec.REDSTONE_ONLY.slots()), new SimpleContainerData(DATA_SIZE), ContainerLevelAccess.NULL);
    }

    public CardCatalogMenu(int id, Inventory inv, CardCatalogBlockEntity be, ContainerLevelAccess access) {
        this(id, inv, be, null, be.getProviders(), be.getUpgrades(), null, access);
    }

    private CardCatalogMenu(int id, Inventory inv, @Nullable CardCatalogBlockEntity be, @Nullable Container window, Container providers,
                            Container upgrades, @Nullable ContainerData clientData, ContainerLevelAccess access) {
        super(ModMenus.CARD_CATALOG.get(), id);
        this.be = be;
        this.access = access;
        this.data = clientData != null ? clientData : serverData(be);
        Container grid = window != null ? window : new Window(be);
        for (int i = 0; i < WINDOW; i++) {
            final int k = i;
            this.addSlot(new CardSlot(grid, i, GRID_X + (i % COLS) * 18, GRID_Y + (i / COLS) * 18, () -> lockedAt(k)));
        }
        this.containerSlotCount = WINDOW;
        addUpgradeSlots(upgrades, UpgradeSpec.REDSTONE_ONLY, IMAGE_WIDTH + 5, 8);
        providerSlot = this.slots.size();
        this.addSlot(new ProviderSlot(providers, 0, IMAGE_WIDTH + 5, 8 + 18, this::minProviders));
        // the column on the right is the module and the providers; GolemScreen draws one panel under it
        upgradeEnd = this.slots.size();
        addPlayerSlots(inv, 15, IMAGE_HEIGHT - 82);
        addDataSlots(data);
        if (be != null) {
            be.startOpen();
        }
    }

    private ContainerData serverData(CardCatalogBlockEntity be) {
        ContainerData base = be.menuData();
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_ROW -> row;
                    case DATA_VIEW -> view == null ? -1 : view.length;
                    default -> base.get(index);
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == DATA_ROW) {
                    row = value;
                } else {
                    base.set(index, value);
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    /** The row slots look into the catalog shifted by the first visible row. */
    private final class Window implements Container {
        private final CardCatalogBlockEntity be;

        Window(CardCatalogBlockEntity be) {
            this.be = be;
        }

        private int abs(int i) {
            return absolute(row * COLS + i);
        }

        private boolean inRange(int i) {
            int a = abs(i);
            return a >= 0 && a < be.getCards().getContainerSize();
        }

        @Override
        public int getContainerSize() {
            return WINDOW;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public ItemStack getItem(int i) {
            return inRange(i) ? be.getCards().getItem(abs(i)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            return inRange(i) ? be.getCards().removeItem(abs(i), count) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            return inRange(i) ? be.getCards().removeItemNoUpdate(abs(i)) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            if (inRange(i)) {
                be.getCards().setItem(abs(i), stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            be.getCards().setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return be.getCards().stillValid(player);
        }

        @Override
        public boolean canPlaceItem(int i, ItemStack stack) {
            return CardCatalogBlockEntity.isCraftingCard(stack);
        }

        @Override
        public void clearContent() {
        }
    }

    /** A row slot: a punch card with a crafting-table recipe, one at a time; beyond the capacity (and outside the search results) it is locked. */
    public static class CardSlot extends Slot {
        private final BooleanSupplier locked;

        CardSlot(Container c, int i, int x, int y, BooleanSupplier locked) {
            super(c, i, x, y);
            this.locked = locked;
        }

        public boolean locked() {
            return locked.getAsBoolean();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked() && CardCatalogBlockEntity.isCraftingCard(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /** Pattern Provider slot: up to a stack; only providers whose places are empty can be taken. */
    public static class ProviderSlot extends Slot {
        private final IntSupplier min;

        ProviderSlot(Container c, int i, int x, int y, IntSupplier min) {
            super(c, i, x, y);
            this.min = min;
        }

        public int removable() {
            return Math.max(0, getItem().getCount() - min.getAsInt());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModBlocks.PATTERN_PROVIDER.get().asItem());
        }

        @Override
        public boolean mayPickup(Player player) {
            return removable() > 0;
        }

        @Override
        public ItemStack remove(int amount) {
            return super.remove(Math.min(amount, removable()));
        }

        @Override
        public int getMaxStackSize() {
            return CardCatalogBlockEntity.MAX_PROVIDERS;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return CardCatalogBlockEntity.MAX_PROVIDERS;
        }
    }

    public int capacity() {
        return data.get(DATA_CAPACITY);
    }

    public int cardCount() {
        return data.get(DATA_COUNT);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(data.get(DATA_REDSTONE));
    }

    private int rowNow() {
        return be != null ? row : data.get(DATA_ROW);
    }

    /** Catalog place behind the k-th slot of the scrolling row (-1 = empty: the search found fewer). */
    private int absolute(int k) {
        return view == null ? k : k < view.length ? view[k] : -1;
    }

    private boolean lockedAt(int i) {
        int k = rowNow() * COLS + i;
        if (be != null) {
            int a = absolute(k);
            return a < 0 || a >= be.capacity();
        }
        int v = data.get(DATA_VIEW);
        return v >= 0 ? k >= v : k >= capacity();
    }

    /** How many places the row scrolls through: the ones found by the search or the whole capacity. */
    private int shown() {
        int v = data.get(DATA_VIEW);
        return v >= 0 ? v : capacity();
    }

    public boolean searching() {
        return data.get(DATA_VIEW) >= 0;
    }

    /** Rows that can be scrolled to (rounded up, but not fewer than the visible ones). */
    public int totalRows() {
        return Math.max(ROWS, (shown() + COLS - 1) / COLS);
    }

    public int maxRow() {
        return totalRows() - ROWS;
    }

    public int row() {
        return rowNow();
    }

    public int providerSlot() {
        return providerSlot;
    }

    public int providers() {
        return slots.get(providerSlot).getItem().getCount();
    }

    private int minProviders() {
        return be != null ? be.minProviders() : 0;
    }

    @Override
    public void view(int newRow, String newFilter) {
        if (be == null) {
            return;
        }
        if (!newFilter.equals(filter)) {
            filter = newFilter;
            if (filter.isBlank()) {
                view = null;
            } else {
                java.util.List<Integer> found = new java.util.ArrayList<>();
                for (int i = 0; i < be.getCards().getContainerSize(); i++) {
                    var data = be.getCards().getItem(i).get(com.golemlogix.init.ModDataComponents.PATTERN.get());
                    if (data != null && data.outputs().stream().anyMatch(s -> ScrollableMenu.matches(s, filter))) {
                        found.add(i);
                    }
                }
                view = found.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        int shown = view != null ? view.length : be.capacity();
        int max = Math.max(0, Math.max(ROWS, (shown + COLS - 1) / COLS) - ROWS);
        row = Math.max(0, Math.min(max, newRow));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE) {
            data.set(DATA_REDSTONE, redstoneMode().next(false).ordinal());
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // a hotbar swap (number keys) would take the whole provider stack past the limit, so it is not allowed while places are taken
        if (slotId == providerSlot && clickType == ClickType.SWAP && minProviders() > 0) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == providerSlot) {
            int n = ((ProviderSlot) slot).removable();
            if (n <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack moving = stack.copyWithCount(n);
            this.moveItemStackTo(moving, playerStart, playerEnd, true);
            int moved = n - moving.getCount();
            if (moved <= 0) {
                return ItemStack.EMPTY;
            }
            slot.remove(moved);
            slot.setChanged();
            return copy;
        }
        if (index < containerSlotCount || (index >= upgradeStart && index < upgradeEnd)) {
            return super.quickMoveStack(player, index);
        }
        // from the inventory: providers go to their slot, punch cards to the first free place of the catalog
        if (stack.is(ModBlocks.PATTERN_PROVIDER.get().asItem())) {
            if (!this.moveItemStackTo(stack, providerSlot, providerSlot + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (CardCatalogBlockEntity.isCraftingCard(stack) && be != null) {
            int cap = be.capacity();
            for (int i = 0; i < cap && !stack.isEmpty(); i++) {
                if (be.getCards().getItem(i).isEmpty()) {
                    be.getCards().setItem(i, stack.split(1));
                }
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
        } else {
            return super.quickMoveStack(player, index);
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (be != null) {
            be.stopOpen();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return (be == null || be.formed()) && stillValid(access, player, ModBlocks.CARD_CATALOG.get());
    }
}
