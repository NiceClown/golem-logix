package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.BusSpeed;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
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

    /** Ghost configuration slots (max stack size 64 even for buckets in legacy worlds). */
    private final OwnedContainer config = new OwnedContainer(SLOTS, this, s -> true) {
        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 64;
        }
    };
    private final OwnedContainer stock = new OwnedContainer(SLOTS, this, s -> true);
    private final OwnedContainer inbox = new OwnedContainer(SLOTS, this, s -> true);
    private final UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();
    private final Automation automation = new Automation();
    private final FluidTank tank = new FluidTank(TANK) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidIntake fluidIntake = new FluidIntake();
    private final int[] amount = new int[SLOTS];
    private final FluidStack[] fluidStock = new FluidStack[SLOTS];
    /** Bit i = 1 keeps container items (e.g. buckets); bit i = 0 keeps the contained fluid. */
    private int itemModeMask = 0;

    {
        Arrays.fill(fluidStock, FluidStack.EMPTY);
    }

    private long nextIntake = 0;
    private long lastSound = -1000;
    private long lastEvent = -1000;
    private long lastCraftRequest = -1000;

    /** Client animation state. */
    private long clientLastWork = -1000;
    private int clientSpeed = 0;
    public float activity, activityPrev, phase, phasePrev, spin, spinPrev;

    public InterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTERFACE.get(), pos, state);
        this.upgrades = new UpgradeContainer(UpgradeSpec.INTERFACE, this, () -> BlockEntities.sendUpdate(this));
    }

    public IItemHandler automation() {
        return automation;
    }

    public IFluidHandler fluids() {
        return fluidIntake;
    }

    public FluidStack tankFluid() {
        return tank.getFluid();
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
        setChanged();
    }

    /** Fluid of the container (or fluid ghost) in a config slot, or empty. */
    public static FluidStack containedFluid(ItemStack cfg) {
        return BlockEntities.ghostFluid(cfg);
    }

    public boolean isFluidSlot(int i) {
        if (i < 0 || i >= SLOTS) {
            return false;
        }
        ItemStack cfg = config.getItem(i);
        return FluidGhostItem.is(cfg) || (!itemMode(i) && !containedFluid(cfg).isEmpty());
    }

    public FluidStack fluidStock(int i) {
        return i >= 0 && i < SLOTS ? fluidStock[i] : FluidStack.EMPTY;
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
        setChanged();
    }

    /** Converts configured amounts between buckets and mB when toggling item/fluid mode. */
    private void modesChanged(int oldMask, int newMask) {
        for (int i = 0; i < SLOTS; i++) {
            int bit = 1 << i;
            if ((oldMask & bit) == (newMask & bit) || containedFluid(config.getItem(i)).isEmpty()) {
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
                    return Math.min(32767, fluidStock[index - DATA_FLUID].getAmount() / 10);
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
                    setChanged();
                } else if (index == 1) {
                    int mask = value & ((1 << SLOTS) - 1);
                    modesChanged(itemModeMask, mask);
                    itemModeMask = mask;
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, InterfaceBlockEntity be) {
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = BlockEntities.pollRedstone(level, pos, hasRedstone, be.redstone);
        long time = level.getGameTime();
        boolean pulseMode = hasRedstone && be.redstone.mode() == RedstoneMode.PULSE;
        boolean stockDue = Math.floorMod(time + pos.asLong(), be.interval()) == 0;
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
            be.nextIntake = time + INTAKE_RETRY;
            return;
        }
        int moved = 0;
        if (intake) {
            moved = be.flushInbox(net, Integer.MAX_VALUE) + be.flushTank(net);
            if (moved == 0) {
                be.nextIntake = time + INTAKE_RETRY;
            }
        }
        if (stockDue) {
            moved += be.restock(level, net, be.itemsPerOperation());
            moved += be.restockFluids(net);
        }
        if (moved > 0) {
            be.redstone.allows(hasRedstone, powered, true);
            be.signalWork(level, pos, state, time);
        }
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
        return !inbox.isEmpty() || !tank.getFluid().isEmpty();
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
        FluidStack f = tank.getFluid();
        if (f.isEmpty()) {
            return 0;
        }
        int stored = network.insertFluidFromMachine(f.copy(), null);
        if (stored > 0) {
            tank.drain(stored, IFluidHandler.FluidAction.EXECUTE);
        }
        return stored;
    }

    /** Fluid stock: returns excess/foreign fluid to the network and pulls missing fluid up to budget. */
    private int restockFluids(GolemNetwork network) {
        int moved = 0, budget = fluidPerOperation();
        for (int i = 0; i < SLOTS; i++) {
            FluidStack have = fluidStock[i];
            FluidStack want = isFluidSlot(i) ? containedFluid(config.getItem(i)) : FluidStack.EMPTY;
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
                        have.shrink(back);
                        if (have.isEmpty()) {
                            fluidStock[i] = FluidStack.EMPTY;
                        }
                        moved += back;
                        setChanged();
                    }
                }
            }
            if (want.isEmpty() || budget - moved <= 0) {
                continue;
            }
            have = fluidStock[i];
            if (!have.isEmpty() && !FluidStack.isSameFluidSameComponents(want, have)) {
                continue;
            }
            int need = wantedFluid(i) - have.getAmount();
            if (need <= 0) {
                continue;
            }
            FluidStack got = network.extractFluid(want, Math.min(need, budget - moved), false);
            if (!got.isEmpty()) {
                if (have.isEmpty()) {
                    fluidStock[i] = got.copy();
                } else {
                    have.grow(got.getAmount());
                }
                moved += got.getAmount();
                setChanged();
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
            if (cfg.isEmpty() || isFluidSlot(i) || budget - moved <= 0) {
                continue;
            }
            ItemStack have = stock.getItem(i);
            if (!have.isEmpty() && !Fuzzy.matches(cfg, have, fuzzy)) {
                continue;
            }
            int need = wanted(i) - have.getCount();
            if (need <= 0) {
                continue;
            }
            ItemStack plain = Fuzzy.plain(cfg).copyWithCount(1);
            int ask = Math.min(need, budget - moved);
            ItemStack got = net.extractItem(have.isEmpty() ? plain : have.copyWithCount(1), ask, false);
            if (got.isEmpty() && fuzzy && have.isEmpty()) {
                if (netKeys == null) {
                    netKeys = net.listItems().keySet();
                }
                got = extractFirstFuzzy(net, cfg, netKeys, ask);
            }
            if (got.isEmpty()) {
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

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientSpeed = tag.getInt("Speed");
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ----------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Config", config.createTag(registries));
        tag.put("Stock", stock.createTag(registries));
        tag.put("Inbox", inbox.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        BlockEntities.saveFluidSlots(tag, "FluidStock", fluidStock, registries);
        tag.putInt("ItemModes", itemModeMask);
        tag.putIntArray("Amounts", amount);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        config.fromTag(tag.getList("Config", Tag.TAG_COMPOUND), registries);
        stock.fromTag(tag.getList("Stock", Tag.TAG_COMPOUND), registries);
        inbox.fromTag(tag.getList("Inbox", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        BlockEntities.loadFluidSlots(tag, "FluidStock", fluidStock, registries);
        itemModeMask = tag.getInt("ItemModes");
        int[] am = tag.getIntArray("Amounts");
        Arrays.fill(amount, 0);
        System.arraycopy(am, 0, amount, 0, Math.min(am.length, SLOTS));
        // migrate legacy worlds that kept target amounts as item counts in config slots
        for (int i = 0; i < SLOTS; i++) {
            ItemStack cfg = config.getItem(i);
            if (amount[i] == 0 && cfg.getCount() > 1) {
                amount[i] = amount(i);
                config.getItems().set(i, cfg.copyWithCount(1));
            }
        }
        redstone.load(tag);
    }

    // ------------------------------------------------------------ automation

    /**
     * Fluids from any side: tank 0 is input (fill only, goes to the network), tanks 1..9 are stock per slot (drain only).
     */
    private final class FluidIntake implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1 + SLOTS;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            if (t < 0 || t > SLOTS) {
                return FluidStack.EMPTY;
            }
            return t == 0 ? tank.getFluid() : fluidStock[t - 1];
        }

        @Override
        public int getTankCapacity(int t) {
            if (t < 0 || t > SLOTS) {
                return 0;
            }
            return t == 0 ? TANK : Math.max(wantedFluid(t - 1), fluidStock[t - 1].getAmount());
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return t == 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            for (int i = 0; i < SLOTS; i++) {
                if (FluidStack.isSameFluidSameComponents(fluidStock[i], resource)) {
                    return drainSlot(i, resource.getAmount(), action);
                }
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < SLOTS; i++) {
                if (!fluidStock[i].isEmpty()) {
                    return drainSlot(i, maxDrain, action);
                }
            }
            return FluidStack.EMPTY;
        }

        private FluidStack drainSlot(int i, int max, FluidAction action) {
            FluidStack have = fluidStock[i];
            int n = Math.min(max, have.getAmount());
            if (n <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack out = have.copyWithAmount(n);
            if (action.execute()) {
                have.shrink(n);
                if (have.isEmpty()) {
                    fluidStock[i] = FluidStack.EMPTY;
                }
                setChanged();
            }
            return out;
        }
    }

    /**
     * Automation access from any side: slots 0..8 are output (extract only), 9..17 are input (insert only).
     */
    private final class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOTS * 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= SLOTS * 2) {
                return ItemStack.EMPTY;
            }
            return slot < SLOTS ? stock.getItem(slot) : inbox.getItem(slot - SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < SLOTS || slot >= SLOTS * 2 || stack.isEmpty()) {
                return stack;
            }
            int i = slot - SLOTS;
            ItemStack cur = inbox.getItem(i);
            if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) {
                return stack;
            }
            int limit = Math.min(stack.getMaxStackSize(), 64);
            int room = limit - cur.getCount();
            if (room <= 0) {
                return stack;
            }
            int n = Math.min(room, stack.getCount());
            if (!simulate) {
                if (cur.isEmpty()) {
                    inbox.setItem(i, stack.copyWithCount(n));
                } else {
                    cur.grow(n);
                    inbox.setChanged();
                }
            }
            return n == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= SLOTS || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack cur = stock.getItem(slot);
            if (cur.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, cur.getCount());
            if (simulate) {
                return cur.copyWithCount(n);
            }
            ItemStack out = cur.split(n);
            stock.setChanged();
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < SLOTS * 2 ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= SLOTS && slot < SLOTS * 2;
        }
    }
}
