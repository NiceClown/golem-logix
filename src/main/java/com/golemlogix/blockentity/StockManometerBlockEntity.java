package com.golemlogix.blockentity;

import com.golemlogix.blocks.StockManometerBlock;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Set;

/**
 * Stock Manometer: monitors network stock of one item or fluid (with optional Fuzzy Card matching) and emits a
 * redstone signal in above/below (with hysteresis), analog, or active-autocraft mode.
 */
public class StockManometerBlockEntity extends BlockEntity {
    public static final int BRIDGE_AUTO = 0, BRIDGE_ON = 1, BRIDGE_OFF = 2;
    public static final int MODE_ABOVE = 0, MODE_BELOW = 1, MODE_ANALOG = 2;
    /** menuData().set indices taking whole values rather than 16-bit slots. */
    public static final int SET_THRESHOLD = 10, SET_SECOND = 11, SET_BRIDGE_NEXT = 12;

    private static final int[] POW3 = {1, 3, 9, 27, 81, 243};

    /** Client golem bridge animation state per Direction ordinal. */
    public final float[] bridgePhase = new float[6];
    public long bridgeTick;

    private final OwnedContainer filter = new OwnedContainer(1, this, s -> true);
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.MANOMETER, this);

    /** Packed base-3 bridge modes across the 6 faces. */
    private int bridgeModes = 0;
    private int mode = MODE_ABOVE;
    private int threshold = 100;
    private int hysteresis = 0;
    private int signal = 0;
    private long stockCount = 0;
    private boolean craftRunning = false;

    public StockManometerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STOCK_MANOMETER.get(), pos, state);
    }

    public Container getFilter() {
        return filter;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public int getSignal() {
        return signal;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = Math.floorMod(mode, 3);
        setChanged();
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = Math.max(1, threshold);
        setChanged();
    }

    public int getHysteresis() {
        return hysteresis;
    }

    public void setHysteresis(int hysteresis) {
        this.hysteresis = Math.max(0, hysteresis);
        setChanged();
    }

    public long getStockCount() {
        return stockCount;
    }

    public static int bridgeMode(int packed, Direction side) {
        return packed / POW3[side.ordinal()] % 3;
    }

    public int getBridgeMode(Direction side) {
        return bridgeMode(bridgeModes, side);
    }

    public void setBridgeMode(Direction side, int mode) {
        bridgeModes += (Math.floorMod(mode, 3) - getBridgeMode(side)) * POW3[side.ordinal()];
        setChanged();
        BlockEntities.sendUpdate(this);
    }

    public boolean hasBridge(Direction side) {
        if (level == null) {
            return false;
        }
        return switch (getBridgeMode(side)) {
            case BRIDGE_ON -> {
                BlockPos neighbor = worldPosition.relative(side);
                yield level.isLoaded(neighbor) && !level.getBlockState(neighbor).isAir();
            }
            case BRIDGE_OFF -> false;
            default -> StockManometerBlock.isReceiver(level, worldPosition, side);
        };
    }

    public Direction getFacing() {
        return getBlockState().getValue(StockManometerBlock.FACING);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StockManometerBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        if (!net.isOnline() || !net.isDeviceActive(pos)) {
            be.updateSignal(level, pos, state, 0, 0, false);
            return;
        }
        ItemStack ghost = be.filter.getItem(0);
        if (ghost.isEmpty()) {
            be.updateSignal(level, pos, state, 0, 0, false);
            return;
        }

        boolean fuzzy = be.upgrades.has(UpgradeType.FUZZY);
        ItemStack plainGhost = Fuzzy.plain(ghost);
        // normalize fluid ghosts to 1 mB so CraftingJob targets and fluid checks agree
        ItemKey key = Ingredients.key(plainGhost);
        boolean isFluid = FluidGhostItem.is(plainGhost) || Ingredients.isFluid(key);
        FluidStack fluid = isFluid
                ? (FluidGhostItem.is(plainGhost) ? FluidGhostItem.fluid(plainGhost) : Ingredients.fluid(key, 1))
                : FluidStack.EMPTY;

        if (be.upgrades.count(UpgradeType.CRAFTING) > 0) {
            boolean running = isCraftRunning(level, net, pos, ghost, key, isFluid, fluid, fuzzy);
            be.updateSignal(level, pos, state, running ? 15 : 0, be.stockCount, running);
            return;
        }
        long stock = countStock(net, ghost, key, isFluid, fluid, fuzzy);
        be.updateSignal(level, pos, state, be.computeSignal(stock), stock, false);
    }

    private static boolean isCraftRunning(Level level, GolemNetwork net, BlockPos pos, ItemStack ghost, ItemKey key,
                                          boolean isFluid, FluidStack fluid, boolean fuzzy) {
        if (!fuzzy && net.busCraftPending(GlobalPos.of(level.dimension(), pos), key)) {
            return true;
        }
        for (CraftingCpu cpu : net.cpus()) {
            var job = cpu.isRemoved() ? null : cpu.getJob();
            if (job != null && !job.isDone() && matchesCraftTarget(job.targets().keySet(), ghost, key, isFluid, fluid, fuzzy)) {
                return true;
            }
        }
        return false;
    }

    private static long countStock(GolemNetwork net, ItemStack ghost, ItemKey key,
                                   boolean isFluid, FluidStack fluid, boolean fuzzy) {
        if (isFluid) {
            return fuzzy ? countFuzzyFluid(net, fluid) : net.fluidCountOf(fluid);
        }
        return fuzzy ? countFuzzyItem(net, ghost) : net.itemCountOf(key);
    }

    private static boolean matchesCraftTarget(Set<ItemKey> targets, ItemStack ghost, ItemKey key,
                                              boolean isFluid, FluidStack fluid, boolean fuzzy) {
        if (!fuzzy) {
            return targets.contains(key);
        }
        for (ItemKey target : targets) {
            if (isFluid) {
                if (Ingredients.isFluid(target) && FluidStack.isSameFluid(Ingredients.fluid(target, 1), fluid)) {
                    return true;
                }
            } else if (!Ingredients.isFluid(target) && Fuzzy.matches(ghost, target.stack(), true)) {
                return true;
            }
        }
        return false;
    }

    private static long countFuzzyItem(GolemNetwork net, ItemStack ghost) {
        long total = 0;
        for (var entry : net.listItemsShared().entrySet()) {
            if (!Ingredients.isFluid(entry.getKey()) && Fuzzy.matches(ghost, entry.getKey().stack(), true)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static long countFuzzyFluid(GolemNetwork net, FluidStack fluid) {
        if (CryoSource.is(fluid)) {
            return net.cryoSourceAvailable();
        }
        long total = 0;
        for (FluidStack f : net.listFluids()) {
            if (FluidStack.isSameFluid(f, fluid)) {
                total += f.getAmount();
            }
        }
        return total;
    }

    /** Computes 0..15 output strength according to mode, threshold, and hysteresis band. */
    private int computeSignal(long stock) {
        int on = threshold, off = hysteresis;
        if (mode == MODE_ABOVE) {
            if (off > 0 && off < on) {
                if (stock >= on) {
                    return 15;
                }
                if (stock <= off) {
                    return 0;
                }
                return signal > 0 ? 15 : 0;
            }
            return stock >= on ? 15 : 0;
        }
        if (mode == MODE_BELOW) {
            if (off > on) {
                if (stock <= on) {
                    return 15;
                }
                if (stock >= off) {
                    return 0;
                }
                return signal > 0 ? 15 : 0;
            }
            return stock <= on ? 15 : 0;
        }
        double max = Math.max(1, (double) threshold);
        return (int) Math.clamp(Math.round(15.0 * stock / max), 0, 15);
    }

    private void updateSignal(Level level, BlockPos pos, BlockState state, int newSignal, long newStock, boolean running) {
        boolean signalChanged = this.signal != newSignal;
        boolean stockChanged = this.stockCount != newStock || this.craftRunning != running;
        this.signal = newSignal;
        this.stockCount = newStock;
        this.craftRunning = running;

        BlockState current = state;
        if (signalChanged) {
            current = state.setValue(StockManometerBlock.POWERED, newSignal > 0);
            level.setBlock(pos, current, 3);
            level.updateNeighborsAt(pos, current.getBlock());
            BlockPos attached = pos.relative(current.getValue(StockManometerBlock.FACING).getOpposite());
            if (level.isLoaded(attached)) {
                level.updateNeighborsAt(attached, current.getBlock());
            }
            setChanged();
        }
        if (signalChanged || stockChanged) {
            level.sendBlockUpdated(pos, current, current, 3);
        }
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                int clampedStock = (int) Math.min(stockCount, Integer.MAX_VALUE);
                return switch (index) {
                    case 0 -> mode;
                    case 1 -> threshold & 0xFFFF;
                    case 2 -> threshold >>> 16;
                    case 3 -> hysteresis & 0xFFFF;
                    case 4 -> hysteresis >>> 16;
                    case 5 -> signal;
                    case 6 -> clampedStock & 0xFFFF;
                    case 7 -> clampedStock >>> 16;
                    case 8 -> bridgeModes;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> mode = Math.floorMod(value, 3);
                    case SET_THRESHOLD -> threshold = Math.max(1, value);
                    case SET_SECOND -> hysteresis = Math.max(0, value);
                    case SET_BRIDGE_NEXT -> {
                        Direction side = BlockEntities.DIRECTIONS[Math.floorMod(value, 6)];
                        setBridgeMode(side, getBridgeMode(side) + 1);
                        return;
                    }
                    default -> {
                        return;
                    }
                }
                setChanged();
            }

            @Override
            public int getCount() {
                return 9;
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mode", mode);
        tag.putInt("Threshold", threshold);
        tag.putInt("Hysteresis", hysteresis);
        tag.putInt("Signal", signal);
        tag.putLong("Stock", stockCount);
        tag.put("Filter", filter.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putInt("Bridges", bridgeModes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.contains("Mode") ? tag.getInt("Mode") : MODE_ABOVE;
        threshold = Math.max(1, tag.contains("Threshold") ? tag.getInt("Threshold") : 100);
        hysteresis = tag.getInt("Hysteresis");
        signal = tag.getInt("Signal");
        stockCount = tag.getLong("Stock");
        filter.fromTag(tag.getList("Filter", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        bridgeModes = tag.getInt("Bridges");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Signal", signal);
        tag.putLong("Stock", stockCount);
        tag.putInt("Threshold", threshold);
        tag.putInt("Bridges", bridgeModes);
        return tag;
    }

    /** The client takes only the synced keys (what the renderer shows); the rest keep their values. */
    public void readUpdateTag(CompoundTag tag) {
        signal = tag.getInt("Signal");
        stockCount = tag.getLong("Stock");
        threshold = Math.max(1, tag.contains("Threshold") ? tag.getInt("Threshold") : 100);
        bridgeModes = tag.getInt("Bridges");
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readUpdateTag(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
