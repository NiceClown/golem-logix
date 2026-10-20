package com.golemlogix.menu;

import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blockentity.PatternProviderBlockEntity;
import com.golemlogix.blockentity.SchematicConcentratorBlockEntity;
import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModMenus;
import com.golemlogix.network.ConcentratorLayoutPayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cryo Schematic Concentrator window: the punch cards of all Pattern Providers of the network (and Catalog places) in one scrolling list.
 * Rows are grouped by category: "Crafting Table" (providers with only crafting-table cards, empty ones without a machine, and catalogs),
 * then by the machine the provider feeds (by its block name), and at the end providers with machine cards but no machine.
 * The window slots are the providers' real slots: cards can be taken out and put in; the server shifts the window by rows and sends
 * the layout (headers, icons, labels) in a ConcentratorLayoutPayload packet.
 */
public class ConcentratorMenu extends GolemMenu implements ScrollableMenu {
    public static final int IMAGE_WIDTH = 210;
    public static final int IMAGE_HEIGHT = 240;
    public static final int COLS = 9, ROWS = 6, WINDOW = COLS * ROWS;
    public static final int ICON_X = 9, GRID_X = 30, GRID_Y = 20;
    public static final int SCROLL_X = GRID_X + COLS * 18 + 4, INFO_Y = GRID_Y + ROWS * 18 + 3;
    public static final int DATA_ROW = 0, DATA_SIZE = 1;

    /** A layout row: a category header or a row of slots (count = how many slots in it are active). */
    public record RowInfo(boolean header, ItemStack icon, Component title, int count) {
    }

    /** A row on the server: where its slots come from. */
    private record Entry(RowInfo info, @Nullable Container container, @Nullable BlockEntity owner, int start, boolean crafting, String key) {
    }

    @Nullable
    private final Level level;
    @Nullable
    private final BlockPos pos;
    @Nullable
    private final ServerPlayer player;
    private final ContainerData data;
    private List<Entry> entries = List.of();
    private List<String> sentKeys = List.of();
    private int row = 0;
    private String filter = "";
    private int rebuildIn = 0;
    // client
    private List<RowInfo> clientRows = List.of();
    public int clientVersion = 0;

    public static ConcentratorMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new ConcentratorMenu(id, inv, null, null);
    }

    public ConcentratorMenu(int id, Inventory inv, @Nullable Level level, @Nullable BlockPos pos) {
        super(ModMenus.CONCENTRATOR.get(), id);
        this.level = level;
        this.pos = pos;
        this.player = level != null && inv.player instanceof ServerPlayer sp ? sp : null;
        Container grid = level != null ? new Window() : new SimpleContainer(WINDOW);
        this.data = level != null ? new ContainerData() {
            @Override
            public int get(int index) {
                return row;
            }

            @Override
            public void set(int index, int value) {
                row = value;
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        } : new SimpleContainerData(DATA_SIZE);
        for (int i = 0; i < WINDOW; i++) {
            this.addSlot(new CardSlot(grid, i, GRID_X + (i % COLS) * 18, GRID_Y + (i / COLS) * 18, this));
        }
        this.containerSlotCount = WINDOW;
        addPlayerSlots(inv, (IMAGE_WIDTH - 162) / 2, IMAGE_HEIGHT - 82);
        addDataSlots(data);
        if (level != null && level.getBlockEntity(pos) instanceof SchematicConcentratorBlockEntity be) {
            be.startOpen();
        }
        if (level != null) {
            entries = build();
        }
    }

    // ------------------------------------------------------------------ rows

    /** The machine the provider feeds (the first suitable side), as an item for the icon and the name; empty if there is none. */
    public static ItemStack machineOf(Level level, PatternProviderBlockEntity p) {
        Direction only = p.output().direction();
        for (Direction d : only == null ? Direction.values() : new Direction[]{only}) {
            BlockPos t = p.getBlockPos().relative(d);
            if (!level.isLoaded(t)) {
                continue;
            }
            BlockState s = level.getBlockState(t);
            if (s.isAir() || (s.getBlock() instanceof GolemNode && !(s.getBlock() instanceof com.golemlogix.blocks.SaturatorPartBlock))) {
                continue;
            }
            if (com.golemlogix.system.LegacyResources.items(level, t, d.getOpposite()) == null) {
                continue;
            }
            ItemStack icon = s.getCloneItemStack(level, t, false);
            return icon.isEmpty() ? new ItemStack(s.getBlock()) : icon;
        }
        return ItemStack.EMPTY;
    }

    private static final class Category {
        final RowInfo header;
        final boolean crafting;
        final List<Entry> rows = new ArrayList<>();

        Category(RowInfo header, boolean crafting) {
            this.header = header;
            this.crafting = crafting;
        }
    }

    private boolean matches(Container c, int start, int count) {
        if (filter.isBlank()) {
            return true;
        }
        for (int k = 0; k < count; k++) {
            PatternData d = c.getItem(start + k).get(ModDataComponents.PATTERN.get());
            if (d != null && d.outputs().stream().anyMatch(s -> ScrollableMenu.matches(s, filter))) {
                return true;
            }
        }
        return false;
    }

    private static String posText(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private List<Entry> build() {
        if (level == null || pos == null) {
            return List.of();
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        // the keys order the categories: crafting table, machines by name, no machine
        Map<String, Category> cats = new TreeMap<>();
        Component craftTitle = Component.translatable("gui.golem_logix.concentrator.crafting");
        List<PatternProviderBlockEntity> providers = new ArrayList<>(net.providers());
        providers.sort(Comparator.comparingDouble(p -> p.getBlockPos().distSqr(pos)));
        for (PatternProviderBlockEntity p : providers) {
            if (p.isRemoved()) {
                continue;
            }
            List<PatternData> pats = p.getPatterns();
            boolean allCrafting = !pats.isEmpty() && pats.stream().allMatch(PatternData::crafting);
            ItemStack machine = allCrafting ? ItemStack.EMPTY : machineOf(level, p);
            Category cat;
            if (allCrafting || (machine.isEmpty() && pats.isEmpty())) {
                cat = cats.computeIfAbsent("0", k -> new Category(new RowInfo(true, new ItemStack(Items.CRAFTING_TABLE), craftTitle, 0), true));
            } else if (!machine.isEmpty()) {
                String name = machine.getHoverName().getString();
                cat = cats.computeIfAbsent("1:" + name, k -> new Category(new RowInfo(true, machine.copyWithCount(1), machine.getHoverName(), 0), false));
            } else {
                cat = cats.computeIfAbsent("2", k -> new Category(new RowInfo(true, new ItemStack(Items.BARRIER),
                        Component.translatable("gui.golem_logix.concentrator.unconnected"), 0), false));
            }
            if (!matches(p.getContainer(), 0, PatternProviderBlockEntity.SLOTS)) {
                continue;
            }
            Component label = Component.translatable("gui.golem_logix.concentrator.provider", posText(p.getBlockPos()));
            if (!machine.isEmpty()) {
                label = label.copy().append(" → ").append(machine.getHoverName());
            }
            cat.rows.add(new Entry(new RowInfo(false, new ItemStack(ModBlocks.PATTERN_PROVIDER.get()), label, PatternProviderBlockEntity.SLOTS),
                    p.getContainer(), p, 0, cat.crafting, "P" + p.getBlockPos().asLong()));
        }
        for (CardCatalogBlockEntity c : net.catalogs()) {
            if (c.isRemoved() || !c.formed()) {
                continue;
            }
            Category cat = cats.computeIfAbsent("0", k -> new Category(new RowInfo(true, new ItemStack(Items.CRAFTING_TABLE), craftTitle, 0), true));
            int cap = c.capacity();
            for (int start = 0; start < cap; start += COLS) {
                int count = Math.min(COLS, cap - start);
                if (!matches(c.getCards(), start, count)) {
                    continue;
                }
                Component label = Component.translatable("gui.golem_logix.concentrator.catalog", posText(c.getBlockPos()), start + 1, start + count);
                cat.rows.add(new Entry(new RowInfo(false, start == 0 ? new ItemStack(ModBlocks.CARD_CATALOG.get()) : ItemStack.EMPTY, label, count),
                        c.getCards(), c, start, true, "C" + c.getBlockPos().asLong() + ":" + start));
            }
        }
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Category> e : cats.entrySet()) {
            Category cat = e.getValue();
            if (cat.rows.isEmpty()) {
                continue;
            }
            out.add(new Entry(cat.header, null, null, 0, cat.crafting, "H" + e.getKey()));
            out.addAll(cat.rows);
        }
        return out;
    }

    @Nullable
    private Entry entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private static boolean live(Entry e) {
        return e != null && e.container != null && e.owner != null && !e.owner.isRemoved();
    }

    /** The window slots look into the list rows shifted by the first visible row. */
    private final class Window implements Container {
        @Nullable
        private Entry at(int i) {
            Entry e = entry(row + i / COLS);
            return live(e) && i % COLS < e.info.count() ? e : null;
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
            Entry e = at(i);
            return e == null ? ItemStack.EMPTY : e.container.getItem(e.start + i % COLS);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            Entry e = at(i);
            return e == null ? ItemStack.EMPTY : e.container.removeItem(e.start + i % COLS, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            Entry e = at(i);
            return e == null ? ItemStack.EMPTY : e.container.removeItemNoUpdate(e.start + i % COLS);
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            Entry e = at(i);
            if (e != null) {
                e.container.setItem(e.start + i % COLS, stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            for (int r = 0; r < ROWS; r++) {
                Entry e = entry(row + r);
                if (live(e)) {
                    e.container.setChanged();
                }
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }
    }

    /** A list slot: active only in a row of slots (not in a header and not past the end of a row). */
    public static class CardSlot extends Slot {
        private final ConcentratorMenu menu;

        CardSlot(Container c, int i, int x, int y, ConcentratorMenu menu) {
            super(c, i, x, y);
            this.menu = menu;
        }

        @Override
        public boolean isActive() {
            return menu.slotActive(getContainerSlot());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (!isActive() || !stack.has(ModDataComponents.PATTERN.get())) {
                return false;
            }
            return !menu.craftingOnly(getContainerSlot()) || CardCatalogBlockEntity.isCraftingCard(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
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

    /** Whether window slot i is active (on the server by the rows, on the client by the layout it received). */
    boolean slotActive(int i) {
        int r = row() + i / COLS;
        if (level != null) {
            Entry e = entry(r);
            return live(e) && i % COLS < e.info.count();
        }
        RowInfo info = r >= 0 && r < clientRows.size() ? clientRows.get(r) : null;
        return info != null && !info.header() && i % COLS < info.count();
    }

    /** A catalog slot: crafting-table cards only. */
    boolean craftingOnly(int i) {
        if (level == null) {
            return false;
        }
        Entry e = entry(row + i / COLS);
        return e != null && e.owner instanceof CardCatalogBlockEntity;
    }

    public int row() {
        return level != null ? row : data.get(DATA_ROW);
    }

    public List<RowInfo> clientRows() {
        return clientRows;
    }

    public void setClientRows(List<RowInfo> rows) {
        clientRows = List.copyOf(rows);
        clientVersion++;
    }

    public int totalRows() {
        return level != null ? entries.size() : clientRows.size();
    }

    public int maxRow() {
        return Math.max(0, totalRows() - ROWS);
    }

    @Override
    public void view(int newRow, String newFilter) {
        if (level == null) {
            return;
        }
        if (!newFilter.equals(filter)) {
            filter = newFilter;
            entries = build();
        }
        row = Math.max(0, Math.min(maxRow(), newRow));
        sendLayout();
    }

    private void sendLayout() {
        List<String> keys = entries.stream().map(Entry::key).toList();
        if (player != null && !keys.equals(sentKeys)) {
            sentKeys = keys;
            PacketDistributor.sendToPlayer(player, new ConcentratorLayoutPayload(containerId, entries.stream().map(Entry::info).toList()));
        }
    }

    @Override
    public void broadcastChanges() {
        if (level != null && --rebuildIn <= 0) {
            // providers appear and disappear, cards get moved: the layout is rebuilt once a second
            rebuildIn = 20;
            entries = build();
            row = Math.max(0, Math.min(maxRow(), row));
            sendLayout();
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = this.slots.get(index);
        if (index < containerSlotCount || slot == null || !slot.hasItem()) {
            return super.quickMoveStack(p, index);
        }
        ItemStack stack = slot.getItem();
        if (level == null || !stack.has(ModDataComponents.PATTERN.get())) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        boolean crafting = CardCatalogBlockEntity.isCraftingCard(stack);
        // a crafting-table card goes to the first free slot of the "Crafting Table" category, a machine card to the first slot at its machine
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (Entry e : entries) {
                if (!live(e) || stack.isEmpty() || (e.owner instanceof CardCatalogBlockEntity && !crafting)) {
                    continue;
                }
                if (pass == 0 && e.crafting != crafting) {
                    continue;
                }
                for (int k = 0; k < e.info.count() && !stack.isEmpty(); k++) {
                    if (e.container.getItem(e.start + k).isEmpty()) {
                        e.container.setItem(e.start + k, stack.split(1));
                    }
                }
            }
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        if (level != null && level.getBlockEntity(pos) instanceof SchematicConcentratorBlockEntity be) {
            be.stopOpen();
        }
    }

    @Override
    public boolean stillValid(Player p) {
        return level == null || (level.getBlockState(pos).is(ModBlocks.SCHEMATIC_CONCENTRATOR.get())
                && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0);
    }
}
