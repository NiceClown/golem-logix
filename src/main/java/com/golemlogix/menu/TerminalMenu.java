package com.golemlogix.menu;

import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.init.ModMenus;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.Chronometers;
import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.network.CraftPlanPayload;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import com.golemlogix.security.SecurityPermission;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Network terminal menu. The item list does not live in slots: the server sends it in packets
 * ({@link TerminalSync}), the client draws it (search and sorting happen on the client), and the player's
 * actions come back in packets. The "Crafting Terminal" variant (and the Portable Chronometer) adds a 3x3
 * grid that refills from the network. Autocraft orders live in {@link TerminalOrders} and cursor fluid
 * container operations in {@link TerminalFluids}.
 */
public class TerminalMenu extends GolemMenu {
    public static final int LIST_X = 10;
    public static final int LIST_Y = 24;
    public static final int COLS = 9;
    public static final int WIDTH = 186;

    public static final int CRAFT_GRID_X = 36;
    public static final int RESULT_X = 130;
    /** Chronometer: the Source charge gauge to the right of the result. */
    public static final int GAUGE_X = 160;

    /** Default number of list rows of the window. */
    public static int defaultRows(boolean crafting) {
        return crafting ? 3 : 5;
    }

    /** The status line (12 px) right under the list. */
    public static int infoY(int rows) {
        return LIST_Y + rows * 18 + 3;
    }

    public static int craftY(int rows) {
        return infoY(rows) + 14;
    }

    /**
     * Top of the player inventory; the normal terminal has an "Inventory" label above it (invY - 11). The crafting terminal has no label:
     * this way the window (236 px) fits a 1280x720 screen at GUI scale 3.
     */
    public static int invY(boolean crafting, int rows) {
        return crafting ? craftY(rows) + 54 + 5 : infoY(rows) + 26;
    }

    public static int imageHeight(boolean crafting, int rows) {
        return invY(crafting, rows) + 82;
    }

    // --- shared state
    public final boolean crafting;
    public final boolean wireless;
    /**
     * Inventory slot with the Chronometer the terminal was opened through (0..8 hotbar, 40 offhand; -1 none). While the window
     * is open the Chronometer in it does not move: otherwise it would leave the inventory (to the cursor, the network, the ground) and the window would close.
     */
    public final int lockedSlot;
    private int lockedMenuSlot = -1;
    /** How many list rows are shown (client; the server does not depend on the layout). */
    public int rows;

    // --- server side
    @Nullable
    private final Level netLevel;
    @Nullable
    private final BlockPos anchor;
    @Nullable
    private final BlockPos devicePos;
    @Nullable
    private final Block expectedBlock;
    private final int wirelessTier;
    private final TerminalSync sync = new TerminalSync();
    private long lastDrainTick = -1;

    // --- crafting
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots = new ResultContainer();
    private final Player player;
    private boolean updatingGrid;
    private boolean quickCrafting;
    private int quickCrafted;

    // --- client side
    private final ArrayList<TerminalListPayload.Entry> clientEntryList = new ArrayList<>();
    public final List<TerminalListPayload.Entry> clientEntries = clientEntryList;
    private Object2IntOpenHashMap<EntryKey> clientIndex = newClientIndex(16);
    private int clientIndexCap = 16;
    public TerminalInfoPayload clientInfo = null;
    public int clientVersion = 0;
    /** Client: the last order plan received (for the order window's planner). */
    @Nullable
    public CraftPlanPayload clientPlan;

    /** Non-copying lookup key for {@link #clientIndex}; {@link TerminalListPayload.Entry#key()} is never mutated. */
    private static final class EntryKey {
        final ItemStack stack;
        final int hash;

        EntryKey(ItemStack stack) {
            this.stack = stack;
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof EntryKey k && hash == k.hash && ItemStack.isSameItemSameComponents(stack, k.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static Object2IntOpenHashMap<EntryKey> newClientIndex(int expected) {
        Object2IntOpenHashMap<EntryKey> map = new Object2IntOpenHashMap<>(expected);
        map.defaultReturnValue(-1);
        return map;
    }

    public static TerminalMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        boolean crafting = buf.readBoolean();
        boolean wireless = buf.readBoolean();
        int locked = wireless ? buf.readVarInt() : -1;
        return new TerminalMenu(id, inv, null, null, null, null, crafting, wireless, 0, locked);
    }

    public TerminalMenu(int id, Inventory inv, @Nullable Level netLevel, @Nullable BlockPos anchor,
                        @Nullable BlockPos devicePos, @Nullable Block expectedBlock,
                        boolean crafting, boolean wireless, int wirelessTier) {
        this(id, inv, netLevel, anchor, devicePos, expectedBlock, crafting, wireless, wirelessTier, -1);
    }

    public TerminalMenu(int id, Inventory inv, @Nullable Level netLevel, @Nullable BlockPos anchor,
                        @Nullable BlockPos devicePos, @Nullable Block expectedBlock,
                        boolean crafting, boolean wireless, int wirelessTier, int lockedSlot) {
        super(ModMenus.TERMINAL.get(), id);
        this.lockedSlot = lockedSlot;
        this.player = inv.player;
        this.netLevel = netLevel;
        this.anchor = anchor;
        this.devicePos = devicePos;
        this.expectedBlock = expectedBlock;
        this.crafting = crafting;
        this.wireless = wireless;
        this.wirelessTier = wirelessTier;
        this.rows = defaultRows(crafting);
        this.craftSlots = new TransientCraftingContainer(this, 3, 3);

        if (crafting) {
            int cy = craftY(rows);
            this.addSlot(new NetworkResultSlot(inv.player, craftSlots, resultSlots, 0, RESULT_X, cy + 18));
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    this.addSlot(new Slot(craftSlots, c + r * 3, CRAFT_GRID_X + c * 18, cy + r * 18));
                }
            }
            this.containerSlotCount = 10;
        }
        addPlayerSlots(inv, LIST_X, invY(crafting, rows));
        lockChronometerSlot(inv);
    }

    /** Locks the inventory slot holding the active Chronometer (Curios slots are outside this menu). */
    private void lockChronometerSlot(Inventory inv) {
        if (lockedSlot < 0 || lockedSlot >= Inventory.INVENTORY_SIZE) {
            return;
        }
        for (int i = playerStart; i < playerEnd; i++) {
            Slot s = this.slots.get(i);
            if (s.container == inv && s.getContainerSlot() == lockedSlot) {
                Slot lock = new LockedSlot(inv, lockedSlot, s.x, s.y);
                lock.index = i;
                this.slots.set(i, lock);
                lockedMenuSlot = i;
            }
        }
    }

    /** The slot with the Chronometer of the open terminal: it cannot be taken, replaced or thrown out. */
    public static final class LockedSlot extends Slot {
        LockedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Number of the locked menu slot (-1 = none): the screen draws a frame on it. */
    public int lockedMenuSlot() {
        return lockedMenuSlot;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (lockedSlot >= 0) {
            // any clicks on the Chronometer slot and swapping with it by a hotbar number (or F with the offhand) do nothing
            if (slotId >= 0 && slotId == lockedMenuSlot) {
                return;
            }
            if (clickType == ClickType.SWAP && button == lockedSlot) {
                return;
            }
        }
        if (crafting && slotId == 0 && clickType == ClickType.QUICK_MOVE) {
            quickCrafting = true;
            quickCrafted = 0;
            try {
                super.clicked(slotId, button, clickType, player);
            } finally {
                boolean crafted = quickCrafted > 0;
                quickCrafting = false;
                quickCrafted = 0;
                if (crafted && player instanceof ServerPlayer sp && !player.level().isClientSide()) {
                    ItemStack res = resultSlots.getItem(0);
                    setRemoteSlot(0, res);
                    sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, res));
                }
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Client: a new list height; the grid and inventory slots move down or up. */
    public void relayout(int newRows) {
        this.rows = newRows;
        if (crafting) {
            int cy = craftY(rows);
            Slot result = this.slots.get(0);
            result.x = RESULT_X;
            result.y = cy + 18;
            for (int i = 0; i < 9; i++) {
                Slot s = this.slots.get(1 + i);
                s.x = CRAFT_GRID_X + (i % 3) * 18;
                s.y = cy + (i / 3) * 18;
            }
        }
        int iy = invY(crafting, rows);
        for (int i = playerStart; i < playerEnd; i++) {
            int k = i - playerStart;
            Slot s = this.slots.get(i);
            s.x = LIST_X + (k % 9) * 18;
            s.y = k < 27 ? iy + (k / 9) * 18 : iy + 58;
        }
    }

    // =================================================================== server

    /**
     * Returns the cached server network for this terminal. Safe without a forced scan because {@code NetworkCache}
     * invalidates synchronously on block/plug/telegraph/multiblock/periscope mutations, while core power, security
     * cards, drive-bay disks/priorities, crafting CPUs, and pattern indexes are read live through cached BE refs.
     */
    private GolemNetwork network() {
        return GolemNetwork.get(netLevel, anchor);
    }

    @Nullable
    private String accessError(GolemNetwork net) {
        return net.accessError(devicePos);
    }

    private boolean checkAccess(ServerPlayer sp, GolemNetwork net) {
        String err = accessError(net);
        if (err != null) {
            sp.displayClientMessage(Component.translatable(err), true);
            return false;
        }
        return true;
    }

    private static boolean requirePermission(ServerPlayer sp, GolemNetwork net, SecurityPermission permission) {
        if (!net.hasPermission(sp, permission)) {
            sp.displayClientMessage(Component.translatable("message.golem_logix.security_denied"), true);
            return false;
        }
        return true;
    }

    public void handleAction(ServerPlayer sp, TerminalActionPayload p) {
        if (netLevel == null) {
            return;
        }
        GolemNetwork net = network();
        if (!checkAccess(sp, net)) {
            return;
        }
        SecurityPermission required = switch (p.action()) {
            case TAKE_STACK, TAKE_HALF, TAKE_ONE, TO_INVENTORY, FLUID_FILL_CARRIED -> SecurityPermission.EXTRACT;
            case INSERT_ALL, INSERT_ONE, FLUID_DRAIN_CARRIED, CLEAR_GRID -> SecurityPermission.INSERT;
        };
        if (!requirePermission(sp, net, required)) {
            return;
        }
        sp.level().playSound(null, sp.blockPosition(), ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.25f, 1.4f);
        ItemStack keyStack = p.key();
        switch (p.action()) {
            case TAKE_STACK -> take(net, keyStack, Amount.STACK);
            case TAKE_HALF -> take(net, keyStack, Amount.HALF);
            case TAKE_ONE -> take(net, keyStack, Amount.ONE);
            case TO_INVENTORY -> toInventory(net, sp, keyStack);
            case INSERT_ALL -> insertCarried(net, false);
            case INSERT_ONE -> insertCarried(net, true);
            case FLUID_FILL_CARRIED -> TerminalFluids.fillCarried(this, net, sp, p.fluid());
            case FLUID_DRAIN_CARRIED -> TerminalFluids.drainCarried(this, net, sp);
            case CLEAR_GRID -> clearGrid(net, sp);
        }
        sync.markDirty();
    }

    private enum Amount {STACK, HALF, ONE}

    private void take(GolemNetwork net, ItemStack keyStack, Amount amount) {
        if (keyStack.isEmpty()) {
            return;
        }
        ItemKey key = ItemKey.of(keyStack);
        ItemStack carried = getCarried();
        if (!carried.isEmpty() && !key.matches(carried)) {
            return;
        }
        int max = key.stack().getMaxStackSize();
        int space = max - carried.getCount();
        if (space <= 0) {
            return;
        }
        long have = net.itemCountOf(key);
        if (have <= 0) {
            return;
        }
        int want = switch (amount) {
            case STACK -> (int) Math.min(max, have);
            case HALF -> (int) Math.max(1, (Math.min(max, have) + 1) / 2);
            case ONE -> 1;
        };
        want = Math.min(want, space);
        ItemStack got = net.extractItem(key.stack(), want, false);
        if (got.isEmpty()) {
            return;
        }
        if (carried.isEmpty()) {
            setCarried(got);
        } else {
            carried.grow(got.getCount());
            setCarried(carried);
        }
    }

    private void toInventory(GolemNetwork net, ServerPlayer sp, ItemStack keyStack) {
        if (keyStack.isEmpty()) {
            return;
        }
        ItemKey key = ItemKey.of(keyStack);
        ItemStack got = net.extractItem(key.stack(), key.stack().getMaxStackSize(), false);
        if (got.isEmpty()) {
            return;
        }
        sp.getInventory().add(got);
        if (!got.isEmpty()) {
            ItemStack rest = net.insertItem(got, false);
            if (!rest.isEmpty()) {
                sp.drop(rest, false);
            }
        }
    }

    private void insertCarried(GolemNetwork net, boolean one) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        int n = one ? 1 : carried.getCount();
        ItemStack rest = net.insertItem(carried.copyWithCount(n), false);
        int accepted = n - rest.getCount();
        if (accepted > 0) {
            carried.shrink(accepted);
            setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        }
    }

    // ------------------------------------------------------------------ crafting grid

    private void drainGrid(ServerPlayer sp, @Nullable GolemNetwork net, boolean canInsert, boolean notifySlots) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.removeItemNoUpdate(i);
            if (s.isEmpty()) {
                continue;
            }
            if (canInsert && net != null) {
                s = net.insertItem(s, false);
            }
            if (!s.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(s);
            }
        }
        if (notifySlots) {
            slotsChanged(craftSlots);
        }
    }

    private void clearGrid(GolemNetwork net, ServerPlayer sp) {
        if (!crafting) {
            return;
        }
        drainGrid(sp, net, true, true);
    }

    /**
     * A recipe from JEI ("+"): the grid contents go back to the network, then every cell is filled with the first ingredient option
     * that is in the network (or, if not, in the player's inventory). max = by full stacks, otherwise one each.
     */
    public void fillGrid(ServerPlayer sp, List<List<ItemStack>> options, boolean max) {
        if (!crafting || netLevel == null) {
            return;
        }
        GolemNetwork net = network();
        boolean netOnline = accessError(net) == null;
        // returning items from the grid requires INSERT; pulling ingredients requires EXTRACT
        boolean canInsert = netOnline && net.hasPermission(sp, SecurityPermission.INSERT);
        boolean canExtract = netOnline && net.hasPermission(sp, SecurityPermission.EXTRACT);
        updatingGrid = true;
        try {
            drainGrid(sp, net, canInsert, false);

            Inventory inv = sp.getInventory();
            for (int i = 0; i < Math.min(9, options.size()); i++) {
                fillGridSlot(i, options.get(i), max, net, canExtract, inv);
            }
            inv.setChanged();
        } finally {
            updatingGrid = false;
        }
        updateCraftResult();
        sync.markDirty();
    }

    private void fillGridSlot(int slotIndex, List<ItemStack> opts, boolean max, GolemNetwork net, boolean canExtract, Inventory inv) {
        for (ItemStack opt : opts) {
            if (opt.isEmpty()) {
                continue;
            }
            ItemStack key = opt.copyWithCount(1);
            int want = max ? key.getMaxStackSize() : 1;
            int have = canExtract ? net.extractItem(key, want, false).getCount() : 0;
            for (int k = 0; k < inv.items.size() && have < want; k++) {
                if (k == lockedSlot) {
                    continue;
                }
                ItemStack st = inv.items.get(k);
                if (ItemStack.isSameItemSameComponents(st, key)) {
                    int take = Math.min(want - have, st.getCount());
                    st.shrink(take);
                    if (st.isEmpty()) {
                        inv.setItem(k, ItemStack.EMPTY);
                    }
                    have += take;
                }
            }
            if (have > 0) {
                craftSlots.setItem(slotIndex, key.copyWithCount(have));
                return;
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && player instanceof ServerPlayer sp && netLevel != null) {
            GolemNetwork net = network();
            if (checkAccess(sp, net) && requirePermission(sp, net, SecurityPermission.INSERT)) {
                clearGrid(net, sp);
                sync.markDirty();
            }
            return true;
        }
        return false;
    }

    private void refillGrid(ItemStack[] before) {
        if (netLevel == null || player.level().isClientSide()) {
            return;
        }
        GolemNetwork net = network();
        if (accessError(net) != null || !net.hasPermission(player, SecurityPermission.EXTRACT)) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (!before[i].isEmpty() && craftSlots.getItem(i).isEmpty()) {
                ItemStack got = net.extractItem(before[i], 1, false);
                if (!got.isEmpty()) {
                    craftSlots.setItem(i, got);
                }
            }
        }
        sync.markDirty();
    }

    private class NetworkResultSlot extends ResultSlot {
        NetworkResultSlot(Player player, CraftingContainer craftSlots, Container container, int slot, int x, int y) {
            super(player, craftSlots, container, slot, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            ItemStack[] before = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                before[i] = craftSlots.getItem(i).copy();
            }
            updatingGrid = true;
            try {
                super.onTake(player, stack);
                refillGrid(before);
            } finally {
                updatingGrid = false;
            }
            updateCraftResult();
        }
    }

    @Override
    public void slotsChanged(Container container) {
        // as with the vanilla crafting table: super is not called, otherwise every grid change forces a broadcast
        if (!crafting || updatingGrid || container != craftSlots) {
            return;
        }
        updateCraftResult();
    }

    private void updateCraftResult() {
        if (!crafting) {
            return;
        }
        Level level = player.level();
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = recipe.get();
            if (resultSlots.setRecipeUsed(level, sp, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        resultSlots.setItem(0, result);
        if (!quickCrafting) {
            setRemoteSlot(0, result);
            sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
        }
    }

    // ------------------------------------------------------------------ craft requests

    /**
     * An order from the order window: a preview sends the plan to the window's planner (CraftPlanPayload);
     * a start sends the outcome there too (the window closes if the order started, otherwise it shows the reason).
     */
    public void handleCraftRequest(ServerPlayer sp, CraftRequestPayload p) {
        if (netLevel == null || p.key().isEmpty()) {
            return;
        }
        GolemNetwork net = network();
        if (!checkAccess(sp, net)) {
            TerminalOrders.sendPlan(sp, containerId, p.request(), CraftPlanPayload.Status.OFFLINE, null);
            return;
        }
        if (TerminalOrders.handleCraftRequest(sp, containerId, net, p)) {
            sync.markDirty();
        }
    }

    /** Planner rows: per item, how much to take from the network, how much to craft, how much is missing. */
    public static List<CraftPlanPayload.Entry> planEntries(CraftingPlan plan) {
        return TerminalOrders.planEntries(plan);
    }

    public void clientReceivePlan(CraftPlanPayload p) {
        clientPlan = p;
    }

    /**
     * Precraft (Ctrl + "+" in JEI): one job for all missing ingredients that have patterns.
     * What cannot be crafted is listed in chat.
     */
    public void handlePrecraft(ServerPlayer sp, List<ItemStack> items, List<Integer> counts) {
        if (netLevel == null) {
            return;
        }
        GolemNetwork net = network();
        if (!checkAccess(sp, net)) {
            return;
        }
        if (TerminalOrders.handlePrecraft(sp, net, items, counts)) {
            sync.markDirty();
        }
    }

    // ------------------------------------------------------------------ wireless: Chronometer charge

    /** The player's Chronometer this terminal was opened through (bound to our core). */
    @Nullable
    private ItemStack chronometer() {
        if (lockedSlot >= 0 && lockedSlot < player.getInventory().getContainerSize()
                && player.getInventory().getItem(lockedSlot).getItem() instanceof PortableChronometerItem) {
            return player.getInventory().getItem(lockedSlot);
        }
        // the inventory, the offhand and the Curios slot: the one bound to this network, or else any
        List<ItemStack> carried = Chronometers.carried(player);
        for (ItemStack s : carried) {
            var bound = PortableChronometerItem.getBound(s);
            if (anchor != null && bound != null && anchor.equals(bound.pos())) {
                return s;
            }
        }
        return carried.isEmpty() ? null : carried.get(0);
    }

    /** Once a second while the window is open: the Chronometer spends Source (unless the network's Resonator powers it). */
    private void drainCharge(ServerPlayer sp, GolemNetwork net) {
        ItemStack chrono = chronometer();
        if (chrono == null || !SourceHelper.sourceRequired()) {
            return;
        }
        boolean resonator = net.hasResonator();
        PortableChronometerItem.setResonator(chrono, resonator);
        if (resonator) {
            PortableChronometerItem.setCharge(chrono, PortableChronometerItem.MAX_CHARGE);
            return;
        }
        int charge = PortableChronometerItem.getCharge(chrono) - PortableChronometerItem.DRAIN_PER_SECOND;
        PortableChronometerItem.setCharge(chrono, Math.max(0, charge));
        if (charge <= 0) {
            sp.displayClientMessage(Component.translatable("message.golem_logix.chronometer.empty").withStyle(ChatFormatting.RED), true);
            sp.closeContainer();
        }
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer sp) || netLevel == null) {
            return;
        }
        long tick = player.level().getGameTime();
        if (!sync.isDue(tick)) {
            return;
        }
        sync.clearForce();
        GolemNetwork net = network();
        String err = accessError(net);

        if (wireless && tick % 20 == 0 && tick != lastDrainTick && err == null) {
            lastDrainTick = tick;
            drainCharge(sp, net);
        }
        ItemStack chrono = wireless && SourceHelper.sourceRequired() ? chronometer() : null;
        sync.sync(sp, containerId, net, err, wireless, chrono);
    }

    // ------------------------------------------------------------------ client

    public void clientReceiveList(TerminalListPayload p) {
        List<TerminalListPayload.Entry> incoming = p.entries();
        if (p.reset()) {
            clientEntryList.clear();
            int need = incoming.size() >= TerminalListPayload.CHUNK
                    ? TerminalListPayload.CHUNK * 4 : incoming.size();
            if (need > clientIndexCap) {
                clientIndex = newClientIndex(need);
                clientIndexCap = need;
            } else {
                clientIndex.clear();
            }
        }
        if (!incoming.isEmpty()) {
            clientEntryList.ensureCapacity(clientEntryList.size() + incoming.size());
        }
        for (TerminalListPayload.Entry e : incoming) {
            EntryKey k = new EntryKey(e.key());
            if (e.count() <= 0 && !e.craftable()) {
                int at = clientIndex.removeInt(k);
                if (at >= 0) {
                    // the last entry takes the place of the one that is gone (the screen sorts its own copy)
                    int lastIdx = clientEntryList.size() - 1;
                    TerminalListPayload.Entry last = clientEntryList.remove(lastIdx);
                    if (at < lastIdx) {
                        clientEntryList.set(at, last);
                        clientIndex.put(new EntryKey(last.key()), at);
                    }
                }
            } else {
                int prev = clientIndex.putIfAbsent(k, clientEntryList.size());
                if (prev < 0) {
                    clientEntryList.add(e);
                } else {
                    clientEntryList.set(prev, e);
                }
            }
        }
        clientVersion++;
    }

    /** Client: O(1) lookup of an item's current entry in {@link #clientEntries}, or null if absent. */
    @Nullable
    public TerminalListPayload.Entry clientEntryOf(ItemStack stack) {
        int at = clientIndex.getInt(new EntryKey(stack));
        return at >= 0 ? clientEntryList.get(at) : null;
    }

    public void clientReceiveInfo(TerminalInfoPayload p) {
        clientInfo = p;
        clientVersion++;
    }

    public int craftGridStart() {
        return 1;
    }

    // ------------------------------------------------------------------ slots

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || slot instanceof LockedSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (crafting && index == 0) {
            // craft at most one stack per Shift+Click so a full inventory cannot trigger thousands of crafts in one tick
            int maxStack = stack.getMaxStackSize();
            if (quickCrafting && quickCrafted > 0 && quickCrafted + stack.getCount() > maxStack) {
                return ItemStack.EMPTY;
            }
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
            ItemStack result = finishQuickMove(slot, stack, copy, player);
            if (!result.isEmpty()) {
                boolean hadRemainder = !stack.isEmpty();
                player.drop(stack, false);
                if (quickCrafting) {
                    quickCrafted += copy.getCount();
                    if (hadRemainder || quickCrafted >= maxStack
                            || quickCrafted + slot.getItem().getCount() > maxStack) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            return result;
        }
        if (crafting && index >= 1 && index < 10) {
            if (!quickMoveFromGrid(player, slot, stack)) {
                return ItemStack.EMPTY;
            }
            return finishQuickMove(slot, stack, copy, player);
        }
        if (!quickMoveFromPlayer(player, stack)) {
            return ItemStack.EMPTY;
        }
        return finishQuickMove(slot, stack, copy, player);
    }

    private boolean quickMoveFromGrid(Player player, Slot slot, ItemStack stack) {
        // from the grid into the network (on the server), otherwise into the inventory
        if (player.level().isClientSide()) {
            return false;
        }
        if (netLevel != null) {
            GolemNetwork net = network();
            if (accessError(net) == null && net.hasPermission(player, SecurityPermission.INSERT)) {
                ItemStack rest = net.insertItem(stack.copy(), false);
                stack.setCount(rest.getCount());
                sync.markDirty();
            }
        }
        if (!stack.isEmpty() && !this.moveItemStackTo(stack, playerStart, playerEnd, false)) {
            slot.setChanged();
            return false;
        }
        return true;
    }

    private boolean quickMoveFromPlayer(Player player, ItemStack stack) {
        if (player.level().isClientSide() || netLevel == null) {
            return false;
        }
        GolemNetwork net = network();
        String err = accessError(net);
        if (err != null) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable(err), true);
            }
            return false;
        }
        if (!net.hasPermission(player, SecurityPermission.INSERT)) {
            if (player instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.golem_logix.security_denied"), true);
            }
            return false;
        }
        ItemStack rest = net.insertItem(stack.copy(), false);
        if (rest.getCount() == stack.getCount()) {
            return false;
        }
        stack.setCount(rest.getCount());
        sync.markDirty();
        return true;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (netLevel == null) {
            return true;
        }
        if (anchor == null || !netLevel.isLoaded(anchor)) {
            return false;
        }
        if (wireless ? !stillValidWireless(player) : !stillValidWired(player)) {
            return false;
        }
        GolemNetwork net = GolemNetwork.get(netLevel, anchor);
        return !net.hasSecurityTerminal()
                || net.hasPermission(player, SecurityPermission.INSERT)
                || net.hasPermission(player, SecurityPermission.EXTRACT)
                || net.hasPermission(player, SecurityPermission.CRAFT);
    }

    private boolean stillValidWireless(Player player) {
        boolean held = lockedSlot >= 0
                ? lockedSlot < player.getInventory().getContainerSize()
                        && player.getInventory().getItem(lockedSlot).getItem() instanceof PortableChronometerItem
                : !Chronometers.carried(player).isEmpty();
        return held && PortableChronometerItem.inRange(wirelessTier, player.level(), player.position(), netLevel, anchor);
    }

    private boolean stillValidWired(Player player) {
        return player.level() == netLevel && devicePos != null && expectedBlock != null
                && netLevel.isLoaded(devicePos) && netLevel.getBlockState(devicePos).is(expectedBlock)
                && player.distanceToSqr(devicePos.getX() + 0.5, devicePos.getY() + 0.5, devicePos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (crafting && !player.level().isClientSide() && player instanceof ServerPlayer sp) {
            GolemNetwork net = netLevel != null ? network() : null;
            boolean netOk = net != null && accessError(net) == null && net.hasPermission(sp, SecurityPermission.INSERT);
            drainGrid(sp, net, netOk, false);
        }
    }

    /** For the client: the type key for an action. */
    public static ItemStack keyOf(ItemStack stack) {
        return stack.copyWithCount(1);
    }
}
