package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModSounds;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Golem Interface, like the AE2/RS interface: one network point on one channel that automation can reach from any side.
 * <ul>
 * <li><b>Stock</b>: nine ghost slots "keep N of these": the interface itself takes what is missing from the network and returns
 * the excess; hoppers, other mods' pipes and machines take items from the output slots.</li>
 * <li><b>Input</b>: everything put into the interface from outside goes to the network (autocraft jobs waiting for it come first).</li>
 * <li>Upgrades as on the export bus: up to four Compressed Co-Processors (more often and more at a time), an Autocraft Module (orders
 * the missing stock from calculators) and a Redstone Module.</li>
 * </ul>
 */
public class InterfaceBlockEntity extends BlockEntity {
    public static final int SLOTS = 9;
    private static final int[] AMOUNT_MULT = {1, 2, 4, 6, 8};
    private static final double[] INTERVAL_MULT = {1.0, 0.75, 0.5, 0.25, 0.125};
    private static final int CRAFT_REQUEST_COOLDOWN = 40;
    private static final int SOUND_COOLDOWN = 12;
    /** Block event "the interface did some work" (parameter = number of speed upgrades): the client swings the metronomes and spins the turbines. */
    public static final int EVENT_WORK = 1;
    private static final int EVENT_COOLDOWN = 8;

    /** What to keep: ghost copies, the count is the wanted amount. */
    private final OwnedContainer config = new OwnedContainer(SLOTS, this, s -> true) {
        /** The config holds ghost numbers, not items: a bucket (stack size 1) can be set up to 64 times. */
        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 64;
        }
    };
    /** Output slots (stock under the config slot with the same number). */
    private final OwnedContainer stock = new OwnedContainer(SLOTS, this, s -> true);
    /** Input slots: what was put in from outside waits to be sent to the network. */
    private final OwnedContainer inbox = new OwnedContainer(SLOTS, this, s -> true);
    private final UpgradeContainer upgrades;
    private final RedstoneControl redstone = new RedstoneControl();
    private final Automation automation = new Automation();
    /** Fluid input tank: what pipes or buckets poured into the interface goes to the network. */
    public static final int TANK = 16000;
    private final net.neoforged.neoforge.fluids.capability.templates.FluidTank tank =
            new net.neoforged.neoforge.fluids.capability.templates.FluidTank(TANK) {
                @Override
                protected void onContentsChanged() {
                    setChanged();
                }
            };
    private final FluidIntake fluidIntake = new FluidIntake();

    public net.neoforged.neoforge.fluids.capability.IFluidHandler fluids() {
        return fluidIntake;
    }

    public net.neoforged.neoforge.fluids.FluidStack tankFluid() {
        return tank.getFluid();
    }

    // ------------------------------------------------------------------ fluids in stock
    /** One bucket in a config slot = this many mB in stock. */
    public static final int MB_PER_BUCKET = 1000;
    /** Fluid stock per config slot (in "fluid" mode). */
    private final net.neoforged.neoforge.fluids.FluidStack[] fluidStock = new net.neoforged.neoforge.fluids.FluidStack[SLOTS];
    /** Slot modes: bit i = 1 keeps the items themselves (buckets), 0 keeps the fluid from the container (default). */
    private int itemModeMask = 0;

    {
        java.util.Arrays.fill(fluidStock, net.neoforged.neoforge.fluids.FluidStack.EMPTY);
    }

    public boolean itemMode(int i) {
        return (itemModeMask & (1 << i)) != 0;
    }

    public void toggleMode(int i) {
        int mask = itemModeMask ^ (1 << i);
        modesChanged(itemModeMask, mask);
        itemModeMask = mask;
        setChanged();
    }

    /** Fluid of the container in a config slot (empty if it is not a container with fluid). */
    public static net.neoforged.neoforge.fluids.FluidStack containedFluid(ItemStack cfg) {
        return cfg.isEmpty() ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                : net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(cfg).filter(f -> !f.isEmpty())
                .orElse(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
    }

    /** The slot keeps fluid: the config holds a container with fluid and the mode is not "item". */
    public boolean isFluidSlot(int i) {
        return !itemMode(i) && !containedFluid(config.getItem(i)).isEmpty();
    }

    public net.neoforged.neoforge.fluids.FluidStack fluidStock(int i) {
        return fluidStock[i];
    }

    /** No more fluid than this is kept in one slot (256 buckets). */
    public static final int MAX_FLUID = 256_000;
    /**
     * How much to keep per slot: items ("item" mode) or mB ("fluid" mode); set in the slot settings window.
     * 0 = not set (worlds of older versions): the count in the config slot is used, in buckets for fluids.
     */
    private final int[] amount = new int[SLOTS];

    /** The set amount of a slot (items or mB) depending on the mode. */
    public int amount(int i) {
        ItemStack cfg = config.getItem(i);
        if (cfg.isEmpty()) {
            return 0;
        }
        if (isFluidSlot(i)) {
            return Math.min(MAX_FLUID, amount[i] > 0 ? amount[i] : cfg.getCount() * MB_PER_BUCKET);
        }
        return Math.min(cfg.getMaxStackSize(), amount[i] > 0 ? amount[i] : cfg.getCount());
    }

    /** Put an item (ghost) and an amount into a config slot. */
    public void setSlot(int i, ItemStack stack, int count) {
        if (stack.isEmpty()) {
            config.setItem(i, ItemStack.EMPTY);
            amount[i] = 0;
        } else {
            config.setItem(i, stack.copyWithCount(1));
            setAmount(i, count);
        }
        setChanged();
    }

    /** Set a slot amount (items or mB) within the mode's limits. */
    public void setAmount(int i, int value) {
        ItemStack cfg = config.getItem(i);
        if (cfg.isEmpty()) {
            amount[i] = 0;
            return;
        }
        amount[i] = isFluidSlot(i) ? Math.max(1, Math.min(MAX_FLUID, value)) : Math.max(1, Math.min(cfg.getMaxStackSize(), value));
        setChanged();
    }

    /** Slot modes changed: the amount is converted (buckets <-> mB), so 3 mB are not kept instead of three buckets. */
    private void modesChanged(int oldMask, int newMask) {
        for (int i = 0; i < SLOTS; i++) {
            int bit = 1 << i;
            if ((oldMask & bit) == (newMask & bit) || containedFluid(config.getItem(i)).isEmpty()) {
                continue;
            }
            int cur = amount[i] > 0 ? amount[i] : config.getItem(i).getCount() * ((oldMask & bit) == 0 ? MB_PER_BUCKET : 1);
            boolean nowItem = (newMask & bit) != 0;
            amount[i] = nowItem ? Math.max(1, Math.min(config.getItem(i).getMaxStackSize(), cur / MB_PER_BUCKET))
                    : Math.max(1, Math.min(MAX_FLUID, cur * MB_PER_BUCKET));
        }
    }

    /** How many mB to keep in slot i. */
    public int wantedFluid(int i) {
        return isFluidSlot(i) ? amount(i) : 0;
    }

    public int fluidPerOperation() {
        return GolemConfig.BUS_FLUID_PER_OPERATION.get() * AMOUNT_MULT[upgrades.count(UpgradeType.SPEED)];
    }

    /** Fluid stock: foreign and excess fluid goes to the network, the missing amount comes from it. Returns the mB moved. */
    private int restockFluids(GolemNetwork network) {
        int moved = 0, budget = fluidPerOperation();
        for (int i = 0; i < SLOTS; i++) {
            var have = fluidStock[i];
            var want = isFluidSlot(i) ? containedFluid(config.getItem(i)) : net.neoforged.neoforge.fluids.FluidStack.EMPTY;
            if (!have.isEmpty()) {
                int excess = want.isEmpty() || !net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(want, have)
                        ? have.getAmount() : have.getAmount() - wantedFluid(i);
                if (excess > 0) {
                    int back = network.insertFluid(have.copyWithAmount(excess), false);
                    have.shrink(back);
                    if (have.isEmpty()) {
                        fluidStock[i] = net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                    }
                    moved += back;
                    setChanged();
                }
            }
            if (want.isEmpty() || budget - moved <= 0) {
                continue;
            }
            have = fluidStock[i];
            if (!have.isEmpty() && !net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(want, have)) {
                continue;
            }
            int need = wantedFluid(i) - have.getAmount();
            if (need <= 0) {
                continue;
            }
            var got = network.extractFluid(want, Math.min(need, budget - moved), false);
            if (got.isEmpty()) {
                continue;
            }
            if (have.isEmpty()) {
                fluidStock[i] = got.copy();
            } else {
                have.grow(got.getAmount());
            }
            moved += got.getAmount();
            setChanged();
        }
        return moved;
    }

    /**
     * Fluids from any side: tank 0 is input (fill only, goes to the network), tanks 1..9 are stock per slot (drain only).
     */
    private final class FluidIntake implements net.neoforged.neoforge.fluids.capability.IFluidHandler {
        @Override
        public int getTanks() {
            return 1 + SLOTS;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int t) {
            return t == 0 ? tank.getFluid() : fluidStock[t - 1];
        }

        @Override
        public int getTankCapacity(int t) {
            return t == 0 ? TANK : Math.max(wantedFluid(t - 1), fluidStock[t - 1].getAmount());
        }

        @Override
        public boolean isFluidValid(int t, net.neoforged.neoforge.fluids.FluidStack stack) {
            return t == 0;
        }

        @Override
        public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
            for (int i = 0; i < SLOTS; i++) {
                if (net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(fluidStock[i], resource)) {
                    return drainSlot(i, resource.getAmount(), action);
                }
            }
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        @Override
        public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
            for (int i = 0; i < SLOTS; i++) {
                if (!fluidStock[i].isEmpty()) {
                    return drainSlot(i, maxDrain, action);
                }
            }
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        private net.neoforged.neoforge.fluids.FluidStack drainSlot(int i, int max, FluidAction action) {
            var have = fluidStock[i];
            int n = Math.min(max, have.getAmount());
            if (n <= 0) {
                return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
            }
            var out = have.copyWithAmount(n);
            if (action.execute()) {
                have.shrink(n);
                if (have.isEmpty()) {
                    fluidStock[i] = net.neoforged.neoforge.fluids.FluidStack.EMPTY;
                }
                setChanged();
            }
            return out;
        }
    }

    /** Tank -> network (as much as fits), returns the mB moved. */
    private int flushTank(GolemNetwork network) {
        var f = tank.getFluid();
        if (f.isEmpty()) {
            return 0;
        }
        int amount = Math.min(f.getAmount(), GolemConfig.BUS_FLUID_PER_OPERATION.get() * AMOUNT_MULT[upgrades.count(UpgradeType.SPEED)]);
        int stored = network.insertFluid(f.copyWithAmount(amount), false);
        if (stored > 0) {
            tank.drain(stored, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        return stored;
    }

    private long lastSound = -1000;
    private long lastEvent = -1000;
    private long lastCraftRequest = -1000;

    /** Client: when the interface last worked and how many speed upgrades it has (for the animation). */
    private long clientLastWork = -1000;
    private int clientSpeed = 0;

    public InterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTERFACE.get(), pos, state);
        this.upgrades = new UpgradeContainer(UpgradeSpec.INTERFACE, this, this::upgradesChanged);
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

    public IItemHandler automation() {
        return automation;
    }

    /**
     * Window data: 0 = redstone mode, 1 = slot modes (bits), 2..10 = fluid stock (tens of mB), 11..19 and 20..28 =
     * set amounts of the slots (low and high 15 bits: window data goes over the network as shorts).
     */
    public static final int DATA_SIZE = 2 + 3 * SLOTS;
    public static final int DATA_FLUID = 2, DATA_AMOUNT_LO = 2 + SLOTS, DATA_AMOUNT_HI = 2 + 2 * SLOTS;

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
                } else if (index == 1) {
                    int mask = value & ((1 << SLOTS) - 1);
                    modesChanged(itemModeMask, mask);
                    itemModeMask = mask;
                }
                setChanged();
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    public int interval() {
        int n = upgrades.count(UpgradeType.SPEED);
        return Math.max(1, (int) Math.round(GolemConfig.BUS_INTERVAL_TICKS.get() * INTERVAL_MULT[n]));
    }

    public int itemsPerOperation() {
        return GolemConfig.BUS_ITEMS_PER_OPERATION.get() * AMOUNT_MULT[upgrades.count(UpgradeType.SPEED)];
    }

    /** How many items to keep in slot i (at most a stack; none for a slot in "fluid" mode). */
    public int wanted(int i) {
        ItemStack c = config.getItem(i);
        return c.isEmpty() || isFluidSlot(i) ? 0 : amount(i);
    }

    private void upgradesChanged() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, InterfaceBlockEntity be) {
        boolean hasRedstone = be.upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && level.hasNeighborSignal(pos);
        if (hasRedstone) {
            be.redstone.observe(powered);
        }
        if (Math.floorMod(level.getGameTime() + pos.asLong(), be.interval()) != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos) || !be.redstone.allows(hasRedstone, powered, false)) {
            return;
        }
        int budget = be.itemsPerOperation();
        int moved = be.flushInbox(net, budget);
        moved += be.restock(level, net, Math.max(0, budget - moved));
        moved += be.flushTank(net);
        moved += be.restockFluids(net);
        if (moved > 0) {
            be.redstone.allows(hasRedstone, powered, true);
            long now = level.getGameTime();
            if (now - be.lastEvent >= EVENT_COOLDOWN) {
                be.lastEvent = now;
                level.blockEvent(pos, state.getBlock(), EVENT_WORK, be.upgrades.count(UpgradeType.SPEED));
            }
            if (now - be.lastSound >= SOUND_COOLDOWN) {
                be.lastSound = now;
                level.playSound(null, pos, ModSounds.VACUUM_SUCTION.get(), SoundSource.BLOCKS, 0.18f, 1.2f);
            }
        }
    }

    /** Input -> network: first what autocraft jobs are waiting for (even when storage is full), the rest goes to storage. */
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
            ItemStack leftover = net.insertFromMachine(real);
            if (!leftover.isEmpty()) {
                if (in.isEmpty()) {
                    inbox.setItem(i, leftover);
                } else {
                    in.grow(leftover.getCount());
                }
            }
            int stored = real.getCount() - leftover.getCount();
            moved += stored;
            budget -= real.getCount();
            inbox.setChanged();
        }
        return moved;
    }

    /** Stock: excess and foreign items go back to the network, missing ones come from it; what is absent entirely is ordered with the Autocraft Module. */
    private int restock(Level level, GolemNetwork net, int budget) {
        int moved = 0;
        Map<ItemKey, Long> toCraft = new LinkedHashMap<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack cfg = config.getItem(i);
            ItemStack have = stock.getItem(i);
            // another item is in the slot (the config changed) or there is too much: return it to the network
            if (!have.isEmpty()) {
                int excess = cfg.isEmpty() || isFluidSlot(i) || !ItemStack.isSameItemSameComponents(cfg, have) ? have.getCount()
                        : have.getCount() - wanted(i);
                if (excess > 0) {
                    ItemStack back = have.copyWithCount(excess);
                    ItemStack rest = net.insertItem(back, false);
                    have.shrink(excess - rest.getCount());
                    stock.setItem(i, have.isEmpty() ? ItemStack.EMPTY : have);
                    moved += excess - rest.getCount();
                }
            }
            if (cfg.isEmpty() || isFluidSlot(i) || budget - moved <= 0) {
                continue;
            }
            have = stock.getItem(i);
            if (!have.isEmpty() && !ItemStack.isSameItemSameComponents(cfg, have)) {
                continue; // the foreign item has not left yet (the network is full)
            }
            int need = wanted(i) - have.getCount();
            if (need <= 0) {
                continue;
            }
            ItemStack template = cfg.copyWithCount(1);
            ItemStack got = net.extractItem(template, Math.min(need, budget - moved), false);
            if (got.isEmpty()) {
                ItemKey key = ItemKey.of(template);
                if (upgrades.has(UpgradeType.CRAFTING) && net.canCraft(key) && !net.busCraftPending(worldPosition, key)) {
                    toCraft.merge(key, (long) need, Long::sum);
                }
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
        long now = level.getGameTime();
        if (!toCraft.isEmpty() && now - lastCraftRequest >= CRAFT_REQUEST_COOLDOWN) {
            lastCraftRequest = now;
            for (Map.Entry<ItemKey, Long> e : toCraft.entrySet()) {
                Map<ItemKey, Long> one = new LinkedHashMap<>();
                one.put(e.getKey(), e.getValue());
                if (net.startCrafting(one, null, worldPosition).type() == GolemNetwork.StartType.NO_CPU) {
                    break;
                }
            }
        }
        return moved;
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

    /** Client: spin-up 0..1, metronome phase (radians) and turbine angle (degrees), current and previous tick. */
    public float activity, activityPrev, phase, phasePrev, spin, spinPrev;

    /**
     * Client tick: while the interface works, the turbines in the horns spin and the metronome pendulums swing, faster with
     * speed upgrades. Spin-up and run-down are smooth; at rest the pendulums hang straight down.
     */
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

    /** Client: ticks since the last work (a big number means idle). */
    public long sinceWork(long now) {
        return now - clientLastWork;
    }

    /** Client: speed upgrades (metronome tempo). */
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
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
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
        net.minecraft.nbt.ListTag fl = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (!fluidStock[i].isEmpty()) {
                CompoundTag e = (CompoundTag) fluidStock[i].save(registries);
                e.putByte("Slot", (byte) i);
                fl.add(e);
            }
        }
        tag.put("FluidStock", fl);
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
        java.util.Arrays.fill(fluidStock, net.neoforged.neoforge.fluids.FluidStack.EMPTY);
        net.minecraft.nbt.ListTag fl = tag.getList("FluidStock", Tag.TAG_COMPOUND);
        for (int k = 0; k < fl.size(); k++) {
            CompoundTag e = fl.getCompound(k);
            int i = e.getByte("Slot");
            if (i >= 0 && i < SLOTS) {
                fluidStock[i] = net.neoforged.neoforge.fluids.FluidStack.parseOptional(registries, e);
            }
        }
        itemModeMask = tag.getInt("ItemModes");
        int[] am = tag.getIntArray("Amounts");
        java.util.Arrays.fill(amount, 0);
        System.arraycopy(am, 0, amount, 0, Math.min(am.length, SLOTS));
        // worlds of older versions kept the amount as the item count in the config slot: move it to "Amounts"
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
     * Automation access from any side: slots 0..8 are output (take only), 9..17 are input (insert only, any
     * items). Taking from the input is not allowed, otherwise a hopper under the interface would pull back what was put in.
     */
    private final class Automation implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOTS * 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < SLOTS ? stock.getItem(slot) : inbox.getItem(slot - SLOTS);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < SLOTS || stack.isEmpty()) {
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
            if (slot >= SLOTS || amount <= 0) {
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
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= SLOTS;
        }
    }
}
