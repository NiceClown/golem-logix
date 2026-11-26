package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.BusSpeed;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Golem Interface: one network point on one channel accessible to external automation from any side.
 * <ul>
 * <li><b>Stock</b>: nine ghost slots keeping configured amounts of items or fluids, pulling missing stock and returning excess.</li>
 * <li><b>Input</b>: item inbox and fluid tank forwarded straight into the network (waiting autocraft jobs first).</li>
 * <li>Upgrades: speed co-processors, Autocraft Module, Fuzzy Card, and Redstone Module.</li>
 * </ul>
 */
public class InterfaceBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;
    public static final int TANK = 16000;
    public static final int MB_PER_BUCKET = 1000;
    public static final int MAX_FLUID = 256_000;
    public static final int EVENT_WORK = 1;
    public static final int DATA_SIZE = 2 + 3 * SLOTS;
    public static final int DATA_FLUID = 2, DATA_AMOUNT_LO = 2 + SLOTS, DATA_AMOUNT_HI = 2 + 2 * SLOTS;

    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    private static final int SOUND_COOLDOWN = 12;
    private static final int EVENT_COOLDOWN = 8;
    private static final int INTAKE_RETRY = 5;
    private static final int SATISFIED_TICKS = 200;
    private static final int MAX_IDLE_TICKS = 120;
    private static final int MAX_IDLE_STEPS = 6;
    private static final int OFFLINE_RETRY_TICKS = 20;

    /** Ghost configuration slots (max stack size 64 even for buckets in legacy worlds). */
    private final OwnedContainer config = new OwnedContainer(SLOTS, this, s -> true) {
        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 64;
        }

        @Override
        public void setChanged() {
            invalidateConfigCache();
            super.setChanged();
        }
    };
    private final OwnedContainer stock = new OwnedContainer(SLOTS, this, s -> true) {
        @Override
        public void setChanged() {
            wakeStock();
            super.setChanged();
        }
    };
    private final OwnedContainer inbox = new OwnedContainer(SLOTS, this, s -> true) {
        @Override
        public void setChanged() {
            inboxDirty = true;
            nextIntake = 0;
            super.setChanged();
        }
    };
    private final UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();
    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, TANK) {
        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            nextIntake = 0;
            setChanged();
        }
    };
    private final int[] amount = new int[SLOTS];
    private final FluidStacksResourceHandler fluidStock = new FluidStacksResourceHandler(SLOTS, MAX_FLUID) {
        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return Math.max(wantedFluid(index), getAmountAsInt(index));
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            wakeStock();
            setChanged();
        }
    };
    /** Bit i = 1 keeps container items (e.g. buckets); bit i = 0 keeps the contained fluid. */
    private int itemModeMask = 0;
    /** Cached per-slot fluid resolved from config slots; refreshed lazily when config or itemModeMask changes. */
    private final FluidStack[] cachedFluids = new FluidStack[SLOTS];
    private int fluidSlotMask = 0;
    private boolean configCacheDirty = true;

    private final ResourceHandler<ItemResource> itemResources = new CombinedResourceHandler<>(
            Transfers.extractOnly(VanillaContainerWrapper.of(stock)),
            Transfers.insertOnly(VanillaContainerWrapper.of(inbox)));
    private final ResourceHandler<FluidResource> fluidResources = new CombinedResourceHandler<>(
            Transfers.insertOnly(tank),
            Transfers.extractOnly(fluidStock));

    private long nextIntake = 0;
    private boolean inboxDirty = true;
    private boolean inboxHasItems = false;
    private boolean upgradesDirty = true;
    private boolean hasRedstone = false;
    private long nextRestock = 0;
    private int idleSteps = 0;
    private boolean stockSleeping = false;
    private boolean stockSatisfied = false;
    private long sleepEpoch = 0;
    private long sleepNetworkEpoch = 0;
    private boolean stockUnsatisfied = false;
    private int restockRuns = 0;
    private long lastSound = -1000;
    private long lastEvent = -1000;
    private long lastCraftRequest = -1000;

    /** Client animation state. */
    private long clientLastWork = -1000;
    private int clientSpeed = 0;
    public float activity, activityPrev, phase, phasePrev, spin, spinPrev;

    public InterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTERFACE.get(), pos, state);
        Arrays.fill(cachedFluids, FluidStack.EMPTY);
        this.upgrades = new UpgradeContainer(UpgradeSpec.INTERFACE, this, () -> {
            upgradesDirty = true;
            wakeStock();
            BlockEntities.sendUpdate(this);
        });
    }

    public ResourceHandler<ItemResource> itemResources() {
        return itemResources;
    }

    public ResourceHandler<FluidResource> fluidResources() {
        return fluidResources;
    }

    public FluidStack tankFluid() {
        return Transfers.getFluid(tank, 0);
    }

    public OwnedContainer getConfig() {
        return config;
    }

    public OwnedContainer getStock() {
        return stock;
    }

    public OwnedContainer getInbox() {
        return inbox;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public boolean itemMode(int i) {
        return i >= 0 && i < SLOTS && (itemModeMask & (1 << i)) != 0;
    }

    public void toggleMode(int i) {
        if (i < 0 || i >= SLOTS) {
            return;
        }
        int mask = itemModeMask ^ (1 << i);
        modesChanged(itemModeMask, mask);
        itemModeMask = mask;
        invalidateConfigCache();
        setChanged();
    }

    /** Fluid of the container (or fluid ghost) in a config slot, or empty. */
    public static FluidStack containedFluid(ItemStack cfg) {
        return BlockEntities.ghostFluid(cfg);
    }

    private void ensureConfigCache() {
        if (!configCacheDirty) {
            return;
        }
        int mask = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack cfg = config.getItem(i);
            FluidStack fluid = cfg.isEmpty() ? FluidStack.EMPTY : containedFluid(cfg);
            cachedFluids[i] = fluid;
            if (!cfg.isEmpty() && (FluidGhostItem.is(cfg) || (!itemMode(i) && !fluid.isEmpty()))) {
                mask |= 1 << i;
            }
        }
        fluidSlotMask = mask;
        configCacheDirty = false;
    }

    private FluidStack cachedFluid(int i) {
        ensureConfigCache();
        return cachedFluids[i];
    }

    private void invalidateConfigCache() {
        configCacheDirty = true;
        wakeStock();
    }

    public boolean isStockSleeping() {
        return stockSleeping;
    }

    public int restockRuns() {
        return restockRuns;
    }

    /** Ends restock sleep so stock maintenance resumes on its next turn. */
    public void wakeStock() {
        idleSteps = 0;
        nextRestock = 0;
        stockSleeping = false;
        stockSatisfied = false;
    }

    public boolean isFluidSlot(int i) {
        if (i < 0 || i >= SLOTS) {
            return false;
        }
        ensureConfigCache();
        return (fluidSlotMask & (1 << i)) != 0;
    }

    public FluidStack fluidStock(int i) {
        return i >= 0 && i < SLOTS ? Transfers.getFluid(fluidStock, i) : FluidStack.EMPTY;
    }

    /** Configured target amount (items or mB) depending on the slot mode. */
    public int amount(int i) {
        if (i < 0 || i >= SLOTS) {
            return 0;
        }
        ItemStack cfg = config.getItem(i);
        if (cfg.isEmpty()) {
            return 0;
        }
        if (isFluidSlot(i)) {
            return Math.min(MAX_FLUID, amount[i] > 0 ? amount[i] : cfg.getCount() * MB_PER_BUCKET);
        }
        return Math.min(cfg.getMaxStackSize(), amount[i] > 0 ? amount[i] : cfg.getCount());
    }

    public void setSlot(int i, ItemStack stack, int count) {
        if (i < 0 || i >= SLOTS) {
            return;
        }
        if (stack.isEmpty()) {
            config.setItem(i, ItemStack.EMPTY);
            amount[i] = 0;
        } else {
            config.setItem(i, stack.copyWithCount(1));
            setAmount(i, count);
        }
        setChanged();
    }

    public void setAmount(int i, int value) {
        if (i < 0 || i >= SLOTS) {
            return;
        }
        ItemStack cfg = config.getItem(i);
        if (cfg.isEmpty()) {
            amount[i] = 0;
            return;
        }
        amount[i] = isFluidSlot(i)
                ? Math.max(1, Math.min(MAX_FLUID, value))
                : Math.max(1, Math.min(cfg.getMaxStackSize(), value));
        wakeStock();
        setChanged();
    }

    /** Converts configured amounts between buckets and mB when toggling item/fluid mode. */
    private void modesChanged(int oldMask, int newMask) {
        for (int i = 0; i < SLOTS; i++) {
            int bit = 1 << i;
            if ((oldMask & bit) == (newMask & bit) || cachedFluid(i).isEmpty()) {
                continue;
            }
            int cur = amount[i] > 0 ? amount[i] : config.getItem(i).getCount() * ((oldMask & bit) == 0 ? MB_PER_BUCKET : 1);
            boolean nowItem = (newMask & bit) != 0;
            amount[i] = nowItem
                    ? Math.max(1, Math.min(config.getItem(i).getMaxStackSize(), cur / MB_PER_BUCKET))
                    : Math.max(1, Math.min(MAX_FLUID, cur * MB_PER_BUCKET));
        }
    }

    public int wantedFluid(int i) {
        return isFluidSlot(i) ? amount(i) : 0;
    }

    public int wanted(int i) {
        ItemStack c = config.getItem(i);
        return c.isEmpty() || isFluidSlot(i) ? 0 : amount(i);
    }

    public int interval() {
        return BusSpeed.interval(upgrades.count(UpgradeType.SPEED));
    }

    public int itemsPerOperation() {
        return BusSpeed.items(upgrades.count(UpgradeType.SPEED));
    }

    public int fluidPerOperation() {
        return BusSpeed.fluid(upgrades.count(UpgradeType.SPEED));
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index == 0) {
                    return redstone.mode().ordinal();
                }
                if (index == 1) {
                    return itemModeMask;
                }
                if (index < DATA_AMOUNT_LO) {
                    return Math.min(32767, fluidStock.getAmountAsInt(index - DATA_FLUID) / 10);
                }
                if (index < DATA_AMOUNT_HI) {
                    return amount(index - DATA_AMOUNT_LO) & 0x7FFF;
                }
                return (amount(index - DATA_AMOUNT_HI) >> 15) & 0x7FFF;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    redstone.setMode(RedstoneMode.byId(value));
                    wakeStock();
                    setChanged();
                } else if (index == 1) {
                    int mask = value & ((1 << SLOTS) - 1);
                    modesChanged(itemModeMask, mask);
                    itemModeMask = mask;
                    invalidateConfigCache();
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    // ------------------------------------------------------------------ tick

    private boolean hasRedstone() {
        if (upgradesDirty) {
            hasRedstone = upgrades.has(UpgradeType.REDSTONE);
            upgradesDirty = false;
        }
        return hasRedstone;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, InterfaceBlockEntity be) {
        boolean hasRedstone = be.hasRedstone();
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, be.redstone);
        long time = level.getGameTime();
        boolean pulseMode = hasRedstone && be.redstone.mode() == RedstoneMode.PULSE;
        boolean stockDue;
        if (be.stockSleeping) {
            if (time >= be.nextRestock || be.wokeFromStorageChange(level, pos)) {
                stockDue = true;
            } else if (pulseMode && be.redstone.allows(true, powered, false)) {
                // a rising redstone pulse wakes sleeping stock so intake and restocking run together
                be.wakeStock();
                stockDue = true;
            } else {
                stockDue = false;
            }
        } else {
            stockDue = time >= be.nextRestock && Math.floorMod(time + pos.asLong(), be.interval()) == 0;
        }
        // in pulse mode run intake and restocking together so neither consumes the pulse from the other
        boolean intake = ((pulseMode && stockDue) || time >= be.nextIntake) && be.hasIntake();
        if (pulseMode && intake) {
            stockDue = true;
        }
        if ((!intake && !stockDue) || !be.redstone.allows(hasRedstone, powered, false)) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            if (intake) {
                be.nextIntake = time + INTAKE_RETRY;
            }
            if (stockDue) {
                be.stockSleeping = false;
                be.stockSatisfied = false;
                be.nextRestock = time + Math.max(be.interval(), OFFLINE_RETRY_TICKS);
            }
            return;
        }
        int moved = 0;
        if (intake) {
            int intakeMoved = be.flushInbox(net, Integer.MAX_VALUE) + be.flushTank(net);
            moved += intakeMoved;
            if (intakeMoved == 0) {
                be.nextIntake = time + INTAKE_RETRY;
            }
        }
        if (stockDue) {
            be.stockUnsatisfied = false;
            be.restockRuns++;
            int restockMoved = be.restock(level, net, be.itemsPerOperation()) + be.restockFluids(net);
            moved += restockMoved;
            be.updateStockSleep(net, restockMoved, time);
        }
        if (moved > 0) {
            be.redstone.allows(hasRedstone, powered, true);
            be.signalWork(level, pos, state, time);
        }
    }

    private boolean wokeFromStorageChange(Level level, BlockPos pos) {
        if (!stockSleeping || stockSatisfied || sleepEpoch == GolemNetwork.storageEpoch()) {
            return false;
        }
        // only wake unsatisfied stock when our own network's storage changed
        sleepEpoch = GolemNetwork.storageEpoch();
        if (GolemNetwork.get(level, pos).networkEpoch() == sleepNetworkEpoch) {
            return false;
        }
        wakeStock();
        return true;
    }

    private void updateStockSleep(GolemNetwork net, int restockMoved, long now) {
        if (restockMoved > 0) {
            idleSteps = 0;
            nextRestock = 0;
            stockSleeping = false;
            stockSatisfied = false;
            return;
        }
        if (!stockUnsatisfied) {
            idleSteps = 0;
            stockSleeping = true;
            stockSatisfied = true;
            nextRestock = now + SATISFIED_TICKS;
            return;
        }
        idleSteps = Math.min(MAX_IDLE_STEPS, idleSteps + 1);
        int interval = interval();
        int maxIdle = upgrades.has(UpgradeType.CRAFTING) ? CRAFT_REQUEST_COOLDOWN : MAX_IDLE_TICKS;
        nextRestock = now + Math.max(interval, Math.min(maxIdle, interval << idleSteps));
        stockSleeping = true;
        stockSatisfied = false;
        sleepEpoch = GolemNetwork.storageEpoch();
        sleepNetworkEpoch = net.networkEpoch();
    }

    private void signalWork(Level level, BlockPos pos, BlockState state, long now) {
        if (now - lastEvent >= EVENT_COOLDOWN) {
            lastEvent = now;
            level.blockEvent(pos, state.getBlock(), EVENT_WORK, upgrades.count(UpgradeType.SPEED));
        }
        if (now - lastSound >= SOUND_COOLDOWN) {
            lastSound = now;
            level.playSound(null, pos, ModSounds.VACUUM_SUCTION.get(), SoundSource.BLOCKS, 0.18f, 1.2f);
        }
    }

    private boolean hasIntake() {
        if (inboxDirty) {
            inboxHasItems = !inbox.isEmpty();
            inboxDirty = false;
        }
        return inboxHasItems || tank.getAmountAsInt(0) > 0;
    }

    /** Input inbox -> network (waiting autocraft jobs first, then storage). */
    private int flushInbox(GolemNetwork net, int budget) {
        int moved = 0;
        for (int i = 0; i < SLOTS && budget > 0; i++) {
            ItemStack in = inbox.getItem(i);
            if (in.isEmpty()) {
                continue;
            }
            ItemStack sim = in.copyWithCount(Math.min(budget, in.getCount()));
            int wanted = Math.min(sim.getCount(), net.machineWants(sim));
            ItemStack restSim = net.insertItem(sim.copyWithCount(sim.getCount() - wanted), true);
            int accepted = wanted + (sim.getCount() - wanted - restSim.getCount());
            if (accepted <= 0) {
                continue;
            }
            ItemStack real = in.split(accepted);
            int realCount = real.getCount();
            ItemStack leftover = net.insertFromMachine(real);
            if (!leftover.isEmpty()) {
                if (in.isEmpty()) {
                    inbox.setItem(i, leftover);
                } else {
                    in.grow(leftover.getCount());
                }
            }
            int stored = realCount - leftover.getCount();
            moved += stored;
            budget -= realCount;
            if (stored > 0) {
                inbox.setChanged();
            }
        }
        return moved;
    }

    /** Input tank -> network (waiting autocraft jobs first, then storage). */
    private int flushTank(GolemNetwork network) {
        FluidStack f = tankFluid();
        if (f.isEmpty()) {
            return 0;
        }
        int stored = network.insertFluidFromMachine(f.copy(), null);
        if (stored > 0) {
            int left = Math.max(0, f.getAmount() - stored);
            tank.set(0, left == 0 ? FluidResource.EMPTY : tank.getResource(0), left);
        }
        return stored;
    }

    /** Fluid stock: returns excess/foreign fluid to the network and pulls missing fluid up to budget. */
    private int restockFluids(GolemNetwork network) {
        int moved = 0, budget = fluidPerOperation();
        for (int i = 0; i < SLOTS; i++) {
            FluidStack have = fluidStock(i);
            FluidStack want = isFluidSlot(i) ? cachedFluid(i) : FluidStack.EMPTY;
            if (!have.isEmpty()) {
                int excess = want.isEmpty() || !FluidStack.isSameFluidSameComponents(want, have)
                        ? have.getAmount() : have.getAmount() - wantedFluid(i);
                if (excess > 0) {
                    int back = network.insertFluid(have.copyWithAmount(excess), false);
                    if (back < excess && CryoSource.is(have)) {
                        // Liquid Cryo-Source cannot be stored: excess returns to the aether
                        back = excess;
                    }
                    if (back > 0) {
                        int rem = Math.max(0, have.getAmount() - back);
                        fluidStock.set(i, rem == 0 ? FluidResource.EMPTY : FluidResource.of(have), rem);
                        moved += back;
                    }
                    if (back < excess) {
                        stockUnsatisfied = true;
                    }
                }
            }
            if (want.isEmpty()) {
                continue;
            }
            have = fluidStock(i);
            if (!have.isEmpty() && !FluidStack.isSameFluidSameComponents(want, have)) {
                stockUnsatisfied = true;
                continue;
            }
            int need = wantedFluid(i) - have.getAmount();
            if (need <= 0) {
                continue;
            }
            if (budget - moved <= 0) {
                stockUnsatisfied = true;
                continue;
            }
            FluidStack got = network.extractFluid(want, Math.min(need, budget - moved), false);
            if (!got.isEmpty()) {
                fluidStock.set(i, FluidResource.of(got), have.getAmount() + got.getAmount());
                moved += got.getAmount();
            }
            if (got.getAmount() < need) {
                stockUnsatisfied = true;
            }
        }
        return moved;
    }

    /** Item stock: returns excess/foreign items, pulls missing items, and queues autocraft orders for missing ones. */
    private int restock(Level level, GolemNetwork net, int budget) {
        int moved = 0;
        Map<ItemKey, Long> toCraft = null;
        boolean fuzzy = upgrades.has(UpgradeType.FUZZY);
        Set<ItemKey> netKeys = null;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack cfg = config.getItem(i);
            moved += returnExcessItem(net, i, cfg, fuzzy);
            if (cfg.isEmpty() || isFluidSlot(i)) {
                continue;
            }
            ItemStack have = stock.getItem(i);
            if (!have.isEmpty() && !Fuzzy.matches(cfg, have, fuzzy)) {
                stockUnsatisfied = true;
                continue;
            }
            int need = wanted(i) - have.getCount();
            if (need <= 0) {
                continue;
            }
            if (budget - moved <= 0) {
                stockUnsatisfied = true;
                continue;
            }
            ItemStack plain = Fuzzy.plain(cfg).copyWithCount(1);
            int ask = Math.min(need, budget - moved);
            ItemStack got = net.extractItem(have.isEmpty() ? plain : have.copyWithCount(1), ask, false);
            if (got.isEmpty() && fuzzy && have.isEmpty()) {
                if (netKeys == null) {
                    netKeys = net.listItemsShared().keySet();
                }
                got = extractFirstFuzzy(net, cfg, netKeys, ask);
            }
            if (got.isEmpty()) {
                stockUnsatisfied = true;
                toCraft = queueMissingCraft(net, plain, need, toCraft);
                continue;
            }
            if (have.isEmpty()) {
                stock.setItem(i, got);
            } else {
                have.grow(got.getCount());
                stock.setChanged();
            }
            moved += got.getCount();
            if (got.getCount() < need) {
                stockUnsatisfied = true;
            }
        }
        dispatchCraftOrders(level, net, toCraft);
        return moved;
    }

    private int returnExcessItem(GolemNetwork net, int slot, ItemStack cfg, boolean fuzzy) {
        ItemStack have = stock.getItem(slot);
        if (have.isEmpty()) {
            return 0;
        }
        int excess = cfg.isEmpty() || isFluidSlot(slot) || !Fuzzy.matches(cfg, have, fuzzy)
                ? have.getCount() : have.getCount() - wanted(slot);
        if (excess <= 0) {
            return 0;
        }
        ItemStack rest = net.insertItem(have.copyWithCount(excess), false);
        int returned = excess - rest.getCount();
        if (returned > 0) {
            have.shrink(returned);
            stock.setItem(slot, have.isEmpty() ? ItemStack.EMPTY : have);
        }
        if (returned < excess) {
            stockUnsatisfied = true;
        }
        return returned;
    }

    private static ItemStack extractFirstFuzzy(GolemNetwork net, ItemStack cfg, Set<ItemKey> netKeys, int ask) {
        for (ItemKey k : Fuzzy.candidates(cfg, netKeys, true)) {
            ItemStack got = net.extractItem(k.toStack(1), ask, false);
            if (!got.isEmpty()) {
                return got;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private Map<ItemKey, Long> queueMissingCraft(GolemNetwork net, ItemStack plain, int need,
                                                 @Nullable Map<ItemKey, Long> toCraft) {
        ItemKey key = ItemKey.of(plain);
        if (upgrades.has(UpgradeType.CRAFTING) && net.canCraft(key) && !net.busCraftPending(worldPosition, key)) {
            if (toCraft == null) {
                toCraft = new LinkedHashMap<>();
            }
            toCraft.merge(key, (long) need, Long::sum);
        }
        return toCraft;
    }

    private void dispatchCraftOrders(Level level, GolemNetwork net, @Nullable Map<ItemKey, Long> toCraft) {
        long now = level.getGameTime();
        if (toCraft == null || now - lastCraftRequest < CRAFT_REQUEST_COOLDOWN) {
            return;
        }
        lastCraftRequest = now;
        for (Map.Entry<ItemKey, Long> e : toCraft.entrySet()) {
            if (BlockEntities.requestCraft(net, worldPosition, e.getKey(), e.getValue()).type() == GolemNetwork.StartType.NO_CPU) {
                break;
            }
        }
    }

    // ------------------------------------------------------------- client side

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == EVENT_WORK) {
            if (level != null && level.isClientSide()) {
                clientLastWork = level.getGameTime();
                clientSpeed = param;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, InterfaceBlockEntity be) {
        float target = be.sinceWork(level.getGameTime()) < 30 ? 1f : 0f;
        be.activityPrev = be.activity;
        be.activity += (target - be.activity) * 0.08f;
        float tempo = 1f + 0.6f * be.clientSpeed;
        be.phasePrev = be.phase;
        be.phase += be.activity * tempo * (float) (Math.PI * 2 / 24);
        be.spinPrev = be.spin;
        be.spin += be.activity * (10f + 9f * be.clientSpeed);
    }

    public long sinceWork(long now) {
        return now - clientLastWork;
    }

    public int clientSpeed() {
        return clientSpeed;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Speed", upgrades.count(UpgradeType.SPEED));
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientSpeed = tag.getIntOr("Speed", 0);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ----------------------------------------------------------- persistence

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Config", config.createTag(registries));
        tag.put("Stock", stock.createTag(registries));
        tag.put("Inbox", inbox.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.put("Tank", NbtBridge.saveTank(tank, registries));
        BlockEntities.saveFluidSlots(tag, "FluidStock", fluidStock, registries);
        tag.putInt("ItemModes", itemModeMask);
        tag.putIntArray("Amounts", amount);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        config.fromTag(tag.getListOrEmpty("Config"), registries);
        stock.fromTag(tag.getListOrEmpty("Stock"), registries);
        inbox.fromTag(tag.getListOrEmpty("Inbox"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        upgradesDirty = true;
        NbtBridge.loadTank(tank, registries, tag.getCompoundOrEmpty("Tank"));
        BlockEntities.loadFluidSlots(tag, "FluidStock", fluidStock, registries);
        itemModeMask = tag.getIntOr("ItemModes", 0);
        invalidateConfigCache();
        int[] am = tag.getIntArray("Amounts").orElse(new int[0]);
        Arrays.fill(amount, 0);
        System.arraycopy(am, 0, amount, 0, Math.min(am.length, SLOTS));
        // migrate legacy worlds that kept target amounts as item counts in config slots
        boolean migrated = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack cfg = config.getItem(i);
            if (amount[i] == 0 && cfg.getCount() > 1) {
                amount[i] = amount(i);
                config.getItems().set(i, cfg.copyWithCount(1));
                migrated = true;
            }
        }
        if (migrated) {
            invalidateConfigCache();
        }
        redstone.load(tag);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BlockEntities.save(this, output, this::saveTag);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        BlockEntities.load(input, this::loadTag);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        BlockEntities.load(input, this::readUpdateTag);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        handleUpdateTag(input);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
