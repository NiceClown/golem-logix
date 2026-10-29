package com.golemlogix.blockentity;

import com.golemlogix.blocks.StockManometerBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.system.Fuzzy;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.Ingredients;
import com.golemlogix.system.ItemKey;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StockManometerBlockEntity extends BlockEntity {
    public static final int MODE_ABOVE = 0;
    public static final int MODE_BELOW = 1;
    public static final int MODE_ANALOG = 2;

    private final OwnedContainer filter = new OwnedContainer(1, this, s -> true);
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.MANOMETER, this);

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

    public Direction getFacing() {
        return getBlockState().getValue(StockManometerBlock.FACING);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StockManometerBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 10 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        boolean active = net.isOnline() && net.isDeviceActive(pos);
        if (!active) {
            be.updateSignal(level, pos, state, 0, 0, false);
            return;
        }

        ItemStack ghost = be.filter.getItem(0);
        if (ghost.isEmpty()) {
            be.updateSignal(level, pos, state, 0, 0, false);
            return;
        }

        boolean hasCraftingUpgrade = be.upgrades.count(UpgradeType.CRAFTING) > 0;
        ItemStack plainGhost = Fuzzy.plain(ghost);
        ItemKey key = ItemKey.of(plainGhost);

        if (hasCraftingUpgrade) {
            boolean running = net.busCraftPending(pos, key);
            if (!running) {
                for (com.golemlogix.crafting.CraftingCpu cpu : net.cpus()) {
                    var job = cpu.isRemoved() ? null : cpu.getJob();
                    if (job != null && !job.isDone() && job.targets().containsKey(key)) {
                        running = true;
                        break;
                    }
                }
            }
            int newSignal = running ? 15 : 0;
            be.updateSignal(level, pos, state, newSignal, be.stockCount, running);
            return;
        }

        long stock;
        if (FluidGhostItem.is(plainGhost) || Ingredients.isFluid(key)) {
            var fluid = FluidGhostItem.is(plainGhost) ? FluidGhostItem.fluid(plainGhost).getFluid() : Ingredients.fluid(key, 1).getFluid();
            stock = net.fluidCountOf(fluid);
        } else {
            stock = net.itemCountOf(key);
        }

        int newSignal = be.computeSignal(stock);
        be.updateSignal(level, pos, state, newSignal, stock, false);
    }

    private int computeSignal(long stock) {
        int low = threshold;
        int high = hysteresis > threshold ? hysteresis : threshold;

        if (mode == MODE_ABOVE) {
            if (hysteresis > threshold) {
                if (stock >= high) return 15;
                if (stock <= low) return 0;
                return signal; // Latch
            }
            return stock >= threshold ? 15 : 0;
        } else if (mode == MODE_BELOW) {
            if (hysteresis > threshold) {
                if (stock <= low) return 15;
                if (stock >= high) return 0;
                return signal; // Latch
            }
            return stock <= threshold ? 15 : 0;
        } else {
            // Analog mode: 0..15 proportional to stock / threshold
            double max = Math.max(1, (double) threshold);
            return (int) Math.clamp(Math.round(15.0 * stock / max), 0, 15);
        }
    }

    private void updateSignal(Level level, BlockPos pos, BlockState state, int newSignal, long newStock, boolean running) {
        boolean signalChanged = this.signal != newSignal;
        boolean stockChanged = this.stockCount != newStock || this.craftRunning != running;
        this.signal = newSignal;
        this.stockCount = newStock;
        this.craftRunning = running;

        if (signalChanged) {
            level.setBlock(pos, state.setValue(StockManometerBlock.POWERED, newSignal > 0), 3);
            level.updateNeighborsAt(pos, state.getBlock());
            Direction attached = state.getValue(StockManometerBlock.FACING).getOpposite();
            level.updateNeighborsAt(pos.relative(attached), state.getBlock());
            setChanged();
        }
        if (signalChanged || stockChanged) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> mode;
                    case 1 -> threshold;
                    case 2 -> hysteresis;
                    case 3 -> signal;
                    case 4 -> (int) (stockCount & 0xFFFF);
                    case 5 -> (int) ((stockCount >> 16) & 0xFFFF);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> mode = value;
                    case 1 -> threshold = value;
                    case 2 -> hysteresis = value;
                    case 3 -> signal = value;
                    case 4 -> stockCount = (stockCount & 0xFFFF_0000L) | (value & 0xFFFF);
                    case 5 -> stockCount = (stockCount & 0xFFFFL) | (((long) value) << 16);
                }
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        CompoundTag tag = new CompoundTag();
        saveTag(tag, com.golemlogix.system.NbtBridge.registries(this));
        com.golemlogix.system.NbtBridge.write(output, tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        loadTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Mode", mode);
        tag.putInt("Threshold", threshold);
        tag.putInt("Hysteresis", hysteresis);
        tag.putInt("Signal", signal);
        tag.putLong("Stock", stockCount);
        tag.put("Filter", filter.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        mode = tag.getIntOr("Mode", MODE_ABOVE);
        threshold = Math.max(1, tag.getIntOr("Threshold", 100));
        hysteresis = tag.getIntOr("Hysteresis", 0);
        signal = tag.getIntOr("Signal", 0);
        stockCount = tag.getLongOr("Stock", 0);
        filter.fromTag(tag.getListOrEmpty("Filter"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Signal", signal);
        tag.putLong("Stock", stockCount);
        tag.putInt("Threshold", threshold);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
