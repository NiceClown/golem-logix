package com.golemlogix.menu;

import com.golemlogix.blockentity.CryoCalculatorBlockEntity;
import com.golemlogix.crafting.CraftingPlan;
import com.golemlogix.crafting.CraftingPlanner;
import com.golemlogix.init.ModMenus;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.PortableChronometerItem;
import com.golemlogix.network.CraftPlanPayload;
import com.golemlogix.network.CraftRequestPayload;
import com.golemlogix.network.TerminalActionPayload;
import com.golemlogix.network.TerminalInfoPayload;
import com.golemlogix.network.TerminalListPayload;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.SourceHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Network terminal menu. The item list does not live in slots: the server sends it in packets,
 * the client draws it (search and sorting happen on the client), and the player's actions come back in packets.
 * The "Crafting Terminal" variant (and the Portable Chronometer) adds a 3x3 grid that refills from the network.
 * The player chooses the list height (the "Size" button): {@link #relayout(int)} recomputes the slot positions on the client.
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
    private Map<ItemKey, Long> lastItems = null;
    private Set<ItemKey> lastCraftable = null;
    private TerminalInfoPayload lastInfo = null;
    private boolean forceSync = true;

    // --- crafting
    private final CraftingContainer craftSlots;
    private final ResultContainer resultSlots = new ResultContainer();
    private final Player player;

    // --- client side
    public final List<TerminalListPayload.Entry> clientEntries = new ArrayList<>();
    public TerminalInfoPayload clientInfo = null;
    public int clientVersion = 0;

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
        // the hotbar slot with the Chronometer is locked (from the offhand only an F key swap could take it, and that is not allowed either)
        if (lockedSlot >= 0 && lockedSlot < Inventory.getSelectionSize()) {
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
    }

    /** The slot with the Chronometer of the open terminal: it cannot be taken, replaced or thrown out. */
    public static final class LockedSlot extends Slot {
        LockedSlot(net.minecraft.world.Container container, int slot, int x, int y) {
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
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        if (lockedSlot >= 0) {
            // any clicks on the Chronometer slot and swapping with it by a hotbar number (or F with the offhand) do nothing
            if (slotId >= 0 && slotId == lockedMenuSlot) {
                return;
            }
            if (clickType == net.minecraft.world.inventory.ClickType.SWAP && button == lockedSlot) {
                return;
            }
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

    private GolemNetwork network() {
        return GolemNetwork.scan(netLevel, anchor);
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

    public void handleAction(ServerPlayer sp, TerminalActionPayload p) {
        if (netLevel == null) {
            return;
        }
        GolemNetwork net = network();
        if (!checkAccess(sp, net)) {
            return;
        }
        ItemStack keyStack = p.key();
        sp.level().playSound(null, sp.blockPosition(), ModSounds.RELAY_CLICK.get(), SoundSource.BLOCKS, 0.25f, 1.4f);
        switch (p.action()) {
            case TAKE_STACK -> take(net, keyStack, Amount.STACK);
            case TAKE_HALF -> take(net, keyStack, Amount.HALF);
            case TAKE_ONE -> take(net, keyStack, Amount.ONE);
            case TO_INVENTORY -> toInventory(net, sp, keyStack);
            case INSERT_ALL -> insertCarried(net, false);
            case INSERT_ONE -> insertCarried(net, true);
            case FLUID_FILL_CARRIED -> fluidFillCarried(net, sp, p.fluid());
            case FLUID_DRAIN_CARRIED -> fluidDrainCarried(net, sp);
            case CLEAR_GRID -> clearGrid(net, sp);
        }
        forceSync = true;
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

    // ------------------------------------------------------------------ fluids

    private void giveContainer(ServerPlayer sp, IFluidHandlerItem handler, ItemStack original) {
        ItemStack result = handler.getContainer();
        if (original.getCount() <= 1) {
            setCarried(result);
        } else {
            original.shrink(1);
            setCarried(original);
            if (!result.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(result);
            }
        }
    }

    private void fluidFillCarried(GolemNetwork net, ServerPlayer sp, FluidStack fluid) {
        ItemStack carried = getCarried();
        if (carried.isEmpty() || fluid.isEmpty()) {
            return;
        }
        ItemStack single = carried.copyWithCount(1);
        IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return;
        }
        int fits = handler.fill(fluid.copyWithAmount(Math.max(1, fluid.getAmount())), IFluidHandler.FluidAction.SIMULATE);
        if (fits <= 0) {
            return;
        }
        FluidStack extracted = net.extractFluid(fluid, fits, false);
        if (extracted.isEmpty()) {
            return;
        }
        int filled = handler.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
        if (filled < extracted.getAmount()) {
            net.insertFluid(extracted.copyWithAmount(extracted.getAmount() - filled), false);
        }
        giveContainer(sp, handler, carried);
    }

    private void fluidDrainCarried(GolemNetwork net, ServerPlayer sp) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        ItemStack single = carried.copyWithCount(1);
        IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return;
        }
        FluidStack sim = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (sim.isEmpty()) {
            return;
        }
        int accepted = net.insertFluid(sim, true);
        if (accepted <= 0) {
            sp.displayClientMessage(Component.translatable("message.golem_logix.fluid.no_room"), true);
            return;
        }
        FluidStack drained = handler.drain(sim.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return;
        }
        net.insertFluid(drained, false);
        giveContainer(sp, handler, carried);
    }

    // ------------------------------------------------------------------ crafting grid

    private void clearGrid(GolemNetwork net, ServerPlayer sp) {
        if (!crafting) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack rest = net.insertItem(s.copy(), false);
            craftSlots.setItem(i, ItemStack.EMPTY);
            if (!rest.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(rest);
            }
        }
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
        boolean netOk = accessError(net) == null;
        for (int i = 0; i < 9; i++) {
            ItemStack s = craftSlots.removeItemNoUpdate(i);
            if (s.isEmpty()) {
                continue;
            }
            if (netOk) {
                s = net.insertItem(s, false);
            }
            if (!s.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(s);
            }
        }
        Inventory inv = sp.getInventory();
        for (int i = 0; i < Math.min(9, options.size()); i++) {
            for (ItemStack opt : options.get(i)) {
                if (opt.isEmpty()) {
                    continue;
                }
                ItemStack key = opt.copyWithCount(1);
                int want = max ? key.getMaxStackSize() : 1;
                int have = netOk ? net.extractItem(key, want, false).getCount() : 0;
                for (int k = 0; k < inv.items.size() && have < want; k++) {
                    ItemStack st = inv.items.get(k);
                    if (ItemStack.isSameItemSameComponents(st, key)) {
                        int take = Math.min(want - have, st.getCount());
                        st.shrink(take);
                        have += take;
                    }
                }
                if (have > 0) {
                    craftSlots.setItem(i, key.copyWithCount(have));
                    break;
                }
            }
        }
        inv.setChanged();
        slotsChanged(craftSlots);
        forceSync = true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && player instanceof ServerPlayer sp && netLevel != null) {
            GolemNetwork net = network();
            if (checkAccess(sp, net)) {
                clearGrid(net, sp);
                forceSync = true;
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
        if (accessError(net) != null) {
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
        forceSync = true;
    }

    private class NetworkResultSlot extends ResultSlot {
        NetworkResultSlot(Player player, CraftingContainer craftSlots, net.minecraft.world.Container container, int slot, int x, int y) {
            super(player, craftSlots, container, slot, x, y);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            ItemStack[] before = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                before[i] = craftSlots.getItem(i).copy();
            }
            super.onTake(player, stack);
            refillGrid(before);
        }
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        // as with the vanilla crafting table: super is not called, otherwise every grid change forces a broadcast
        if (!crafting || container != craftSlots) {
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
        setRemoteSlot(0, result);
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                containerId, incrementStateId(), 0, result));
    }

    // ------------------------------------------------------------------ craft requests

    /**
     * An order from the order window: a preview sends the plan to the window's planner (CraftPlanPayload: what to take from the network, what to
     * craft, what is missing); a start sends the outcome there too (the window closes if the order started, otherwise it shows the reason).
     * Shortages are no longer written to chat.
     */
    public void handleCraftRequest(ServerPlayer sp, CraftRequestPayload p) {
        if (netLevel == null || p.key().isEmpty()) {
            return;
        }
        GolemNetwork net = network();
        if (!checkAccess(sp, net)) {
            sendPlan(sp, p.request(), CraftPlanPayload.Status.OFFLINE, null);
            return;
        }
        ItemKey key = ItemKey.of(p.key());
        long amount = Math.max(1, Math.min(p.amount(), 1_000_000));
        Component name = key.stack().getHoverName();

        if (!net.canCraft(key)) {
            sendPlan(sp, p.request(), CraftPlanPayload.Status.NO_PATTERN, null);
            return;
        }

        if (p.preview()) {
            CraftingPlan plan = CraftingPlanner.plan(net, key, amount);
            sendPlan(sp, p.request(), plan.missing().isEmpty() ? CraftPlanPayload.Status.OK : CraftPlanPayload.Status.MISSING, plan);
            return;
        }

        Map<ItemKey, Long> one = new LinkedHashMap<>();
        one.put(key, amount);
        GolemNetwork.StartResult result = net.startCrafting(one, sp.getUUID(), null);
        CraftPlanPayload.Status status = switch (result.type()) {
            case STARTED -> CraftPlanPayload.Status.STARTED;
            case MISSING -> CraftPlanPayload.Status.MISSING;
            case NO_CPU -> CraftPlanPayload.Status.NO_CPU;
            case RESERVE_FAILED -> CraftPlanPayload.Status.RESERVE_FAILED;
            case OFFLINE -> CraftPlanPayload.Status.OFFLINE;
            case NO_PATTERN -> CraftPlanPayload.Status.NO_PATTERN;
        };
        CraftingPlan plan = result.plan() != null ? result.plan() : CraftingPlanner.plan(net, key, amount);
        sendPlan(sp, p.request(), status, plan);
        if (status == CraftPlanPayload.Status.STARTED) {
            sp.displayClientMessage(Component.translatable("message.golem_logix.craft.started", amount, name, plan.totalExecutions())
                    .withStyle(ChatFormatting.AQUA), true);
        }
        forceSync = true;
    }

    /** The order plan goes to the order window's planner: missing, crafted and taken from the network (in that order). */
    private void sendPlan(ServerPlayer sp, int request, CraftPlanPayload.Status status, @Nullable CraftingPlan plan) {
        List<CraftPlanPayload.Entry> entries = plan == null ? List.of() : planEntries(plan);
        PacketDistributor.sendToPlayer(sp, new CraftPlanPayload(containerId, request, status, plan == null ? 0 : plan.steps().size(),
                plan == null ? 0 : plan.totalExecutions(), entries));
    }

    /** Planner rows: per item, how much to take from the network, how much to craft, how much is missing. */
    public static List<CraftPlanPayload.Entry> planEntries(CraftingPlan plan) {
        Map<ItemKey, long[]> rows = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Long> e : plan.missing().entrySet()) {
            rows.computeIfAbsent(e.getKey(), k -> new long[3])[2] += e.getValue();
        }
        for (CraftingPlan.Step s : plan.steps()) {
            for (Map.Entry<ItemKey, Integer> out : s.pattern().outputTotals().entrySet()) {
                rows.computeIfAbsent(out.getKey(), k -> new long[3])[1] += s.times() * out.getValue();
            }
        }
        for (Map.Entry<ItemKey, Long> e : plan.fromStorage().entrySet()) {
            rows.computeIfAbsent(e.getKey(), k -> new long[3])[0] += e.getValue();
        }
        List<CraftPlanPayload.Entry> entries = new ArrayList<>();
        for (Map.Entry<ItemKey, long[]> e : rows.entrySet()) {
            long[] v = e.getValue();
            entries.add(new CraftPlanPayload.Entry(e.getKey().stack(), v[0], v[1], v[2]));
        }
        entries.sort(java.util.Comparator.comparingInt(e -> e.missing() > 0 ? 0 : e.craft() > 0 ? 1 : 2));
        return entries.size() > CraftPlanPayload.MAX_ENTRIES ? new ArrayList<>(entries.subList(0, CraftPlanPayload.MAX_ENTRIES)) : entries;
    }
    /** Client: the last order plan received (for the order window's planner). */
    @Nullable
    public CraftPlanPayload clientPlan;

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
        Map<ItemKey, Long> targets = new LinkedHashMap<>();
        List<Component> uncraftable = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), counts.size()); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.of(s);
            long n = Math.max(1, Math.min(counts.get(i), 4096));
            if (net.canCraft(key)) {
                targets.merge(key, n, Long::sum);
            } else {
                uncraftable.add(key.stack().getHoverName());
            }
        }
        if (!uncraftable.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < uncraftable.size(); i++) {
                list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(uncraftable.get(i));
            }
            sp.sendSystemMessage(Component.translatable("message.golem_logix.precraft.uncraftable", list).withStyle(ChatFormatting.YELLOW));
        }
        if (targets.isEmpty()) {
            return;
        }
        MutableComponent what = Component.empty();
        int i = 0;
        for (Map.Entry<ItemKey, Long> e : targets.entrySet()) {
            what.append(i++ == 0 ? Component.empty() : Component.literal(", "))
                    .append(Component.literal(e.getValue() + "× ")).append(e.getKey().stack().getHoverName());
        }
        GolemNetwork.StartResult result = net.startCrafting(targets, sp.getUUID(), null);
        ItemKey first = targets.keySet().iterator().next();
        reportStart(sp, result, Component.translatable("message.golem_logix.precraft.started", what), targets.get(first),
                first.stack().getHoverName());
        forceSync = true;
    }

    private void reportStart(ServerPlayer sp, GolemNetwork.StartResult result, Component started, long amount, Component name) {
        switch (result.type()) {
            case STARTED -> sp.sendSystemMessage(started.copy().withStyle(ChatFormatting.AQUA));
            case MISSING -> {
                sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.missing", amount, name).withStyle(ChatFormatting.RED));
                reportMissing(sp, result.plan());
            }
            case NO_CPU -> sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.no_cpu").withStyle(ChatFormatting.RED));
            case RESERVE_FAILED -> sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.reserve_failed").withStyle(ChatFormatting.RED));
            case OFFLINE -> sp.sendSystemMessage(Component.translatable("message.golem_logix.no_power").withStyle(ChatFormatting.RED));
            case NO_PATTERN -> sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.no_pattern", name).withStyle(ChatFormatting.RED));
        }
    }

    private void reportMissing(ServerPlayer sp, @Nullable CraftingPlan plan) {
        if (plan == null) {
            return;
        }
        int shown = 0;
        for (Map.Entry<ItemKey, Long> e : plan.missing().entrySet()) {
            if (shown++ >= 8) {
                sp.sendSystemMessage(Component.literal("  ...").withStyle(ChatFormatting.GRAY));
                break;
            }
            sp.sendSystemMessage(Component.translatable("message.golem_logix.craft.missing_item", e.getValue(), e.getKey().stack().getHoverName())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ wireless: Chronometer charge

    /** The player's Chronometer this terminal was opened through (bound to our core). */
    @Nullable
    private ItemStack chronometer() {
        if (lockedSlot >= 0 && player.getInventory().getItem(lockedSlot).getItem() instanceof PortableChronometerItem) {
            return player.getInventory().getItem(lockedSlot);
        }
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof PortableChronometerItem && anchor != null && PortableChronometerItem.getBound(s) != null
                    && anchor.equals(PortableChronometerItem.getBound(s).pos())) {
                return s;
            }
        }
        for (ItemStack s : player.getInventory().offhand) {
            if (s.getItem() instanceof PortableChronometerItem) {
                return s;
            }
        }
        return null;
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
        if (!forceSync && tick % 10 != 0) {
            return;
        }
        boolean force = forceSync;
        forceSync = false;

        GolemNetwork net = force ? network() : GolemNetwork.get(netLevel, anchor);
        String err = accessError(net);

        if (wireless && tick % 20 == 0 && err == null) {
            drainCharge(sp, net);
        }

        Map<ItemKey, Long> items = err == null ? net.listItems() : Map.of();
        Set<ItemKey> craftable = err == null ? new HashSet<>(net.patternIndex().keySet()) : Set.of();

        if (lastItems == null || !items.equals(lastItems) || !craftable.equals(lastCraftable)) {
            lastItems = new LinkedHashMap<>(items);
            lastCraftable = new HashSet<>(craftable);
            sendList(sp, items, craftable);
        }

        int busy = 0;
        int total = 0;
        if (err == null) {
            for (com.golemlogix.crafting.CraftingCpu cpu : net.cpus()) {
                if (!cpu.isRemoved()) {
                    total++;
                    if (!cpu.isIdle()) {
                        busy++;
                    }
                }
            }
        }
        int charge = -1;
        boolean resonator = false;
        if (wireless && SourceHelper.sourceRequired()) {
            ItemStack chrono = chronometer();
            charge = chrono == null ? 0 : PortableChronometerItem.getCharge(chrono);
            resonator = err == null && net.hasResonator();
        }
        TerminalInfoPayload info = new TerminalInfoPayload(containerId, err == null ? "" : err,
                net.channelsUsed(), net.channelCapacity(),
                err == null ? net.itemsStoredOnDisks() : 0, err == null ? net.itemCapacity() : 0,
                err == null ? net.fluidStoredOnDisks() : 0, err == null ? net.fluidCapacity() : 0,
                busy, total, err == null ? net.listFluids() : List.of(), charge, resonator);
        if (!sameInfo(info, lastInfo)) {
            lastInfo = info;
            PacketDistributor.sendToPlayer(sp, info);
        }
    }

    private static boolean sameInfo(TerminalInfoPayload a, @Nullable TerminalInfoPayload b) {
        if (b == null) {
            return false;
        }
        if (!a.error().equals(b.error()) || a.channelsUsed() != b.channelsUsed() || a.channelCapacity() != b.channelCapacity()
                || a.itemsUsed() != b.itemsUsed() || a.itemsCapacity() != b.itemsCapacity()
                || a.fluidsUsed() != b.fluidsUsed() || a.fluidsCapacity() != b.fluidsCapacity()
                || a.cpuBusy() != b.cpuBusy() || a.cpuTotal() != b.cpuTotal() || a.fluids().size() != b.fluids().size()
                || a.charge() != b.charge() || a.resonator() != b.resonator()) {
            return false;
        }
        for (int i = 0; i < a.fluids().size(); i++) {
            if (!FluidStack.matches(a.fluids().get(i), b.fluids().get(i))) {
                return false;
            }
        }
        return true;
    }

    private void sendList(ServerPlayer sp, Map<ItemKey, Long> items, Set<ItemKey> craftable) {
        List<TerminalListPayload.Entry> all = new ArrayList<>(items.size() + craftable.size());
        for (Map.Entry<ItemKey, Long> e : items.entrySet()) {
            all.add(new TerminalListPayload.Entry(e.getKey().stack(), e.getValue(), craftable.contains(e.getKey())));
        }
        for (ItemKey k : craftable) {
            if (!items.containsKey(k)) {
                all.add(new TerminalListPayload.Entry(k.stack(), 0, true));
            }
        }
        boolean first = true;
        int i = 0;
        do {
            int end = Math.min(all.size(), i + TerminalListPayload.CHUNK);
            PacketDistributor.sendToPlayer(sp, new TerminalListPayload(containerId, first, new ArrayList<>(all.subList(i, end))));
            first = false;
            i = end;
        } while (i < all.size());
    }

    // ------------------------------------------------------------------ client

    public void clientReceiveList(TerminalListPayload p) {
        if (p.reset()) {
            clientEntries.clear();
        }
        clientEntries.addAll(p.entries());
        clientVersion++;
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
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || slot instanceof LockedSlot) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (crafting && index == 0) {
            // as in the crafting table: the result moves to the inventory, the outer loop repeats until it runs out
            stack.getItem().onCraftedBy(stack, player.level(), player);
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (crafting && index >= 1 && index < 10) {
            // from the grid into the network (on the server), otherwise into the inventory
            if (player.level().isClientSide()) {
                return ItemStack.EMPTY;
            }
            if (netLevel != null) {
                GolemNetwork net = network();
                if (accessError(net) == null) {
                    ItemStack rest = net.insertItem(stack.copy(), false);
                    stack.setCount(rest.getCount());
                    forceSync = true;
                }
            }
            if (!stack.isEmpty() && !this.moveItemStackTo(stack, playerStart, playerEnd, false)) {
                slot.setChanged();
                return ItemStack.EMPTY;
            }
        } else {
            // player inventory -> network
            if (player.level().isClientSide() || netLevel == null) {
                return ItemStack.EMPTY;
            }
            GolemNetwork net = network();
            if (accessError(net) != null) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable(accessError(net)), true);
                }
                return ItemStack.EMPTY;
            }
            ItemStack rest = net.insertItem(stack.copy(), false);
            if (rest.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            stack.setCount(rest.getCount());
            forceSync = true;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (crafting && index == 0) {
            player.drop(stack, false);
        }
        return copy;
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
        if (wireless) {
            boolean held = lockedSlot >= 0 ? player.getInventory().getItem(lockedSlot).getItem() instanceof PortableChronometerItem
                    : player.getInventory().contains(s -> s.getItem() instanceof PortableChronometerItem);
            if (!held) {
                return false;
            }
            return PortableChronometerItem.inRange(wirelessTier, player.level(), player.position(), netLevel, anchor);
        }
        if (devicePos == null || expectedBlock == null || !netLevel.getBlockState(devicePos).is(expectedBlock)) {
            return false;
        }
        return player.distanceToSqr(devicePos.getX() + 0.5, devicePos.getY() + 0.5, devicePos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (crafting && !player.level().isClientSide() && player instanceof ServerPlayer sp) {
            GolemNetwork net = netLevel != null ? network() : null;
            boolean netOk = net != null && accessError(net) == null;
            for (int i = 0; i < 9; i++) {
                ItemStack s = craftSlots.removeItemNoUpdate(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (netOk) {
                    s = net.insertItem(s, false);
                }
                if (!s.isEmpty()) {
                    sp.getInventory().placeItemBackInInventory(s);
                }
            }
        }
    }

    /** For the client: the type key for an action. */
    public static ItemStack keyOf(ItemStack stack) {
        return stack.copyWithCount(1);
    }
}
