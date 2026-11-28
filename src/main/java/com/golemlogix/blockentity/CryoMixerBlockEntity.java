package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.AutoOutput;
import com.golemlogix.system.CryoSource;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Cryo-Mixer: freezes water and Liquid Cryo-Source into snow blocks (fast) or ice (slow) in its output tray.
 * Compressed Co-Processors scale speed; a Redstone Module gates work (one block per pulse).
 */
public class CryoMixerBlockEntity extends BlockEntity {
    public static final int WATER_TANK = 8000, CRYO_TANK = 4000;
    /** Status values shown in the GUI. */
    public static final int WORKING = 1, NO_WATER = 2, NO_CRYO = 3, TRAY_FULL = 4, PAUSED = 5;

    /** Output mode: fast snow blocks or slow ice blocks. */
    public enum Mode {
        SNOW, ICE;

        public ItemStack output() {
            return new ItemStack(this == SNOW ? Items.SNOW_BLOCK : Items.ICE);
        }

        public int water() {
            return (this == SNOW ? GolemConfig.MIXER_SNOW_WATER : GolemConfig.MIXER_ICE_WATER).get();
        }

        public int cryo() {
            return (this == SNOW ? GolemConfig.MIXER_SNOW_CRYO : GolemConfig.MIXER_ICE_CRYO).get();
        }

        public int ticks() {
            return (this == SNOW ? GolemConfig.MIXER_SNOW_TICKS : GolemConfig.MIXER_ICE_TICKS).get();
        }

        public static Mode byId(int id) {
            return id == 1 ? ICE : SNOW;
        }
    }

    private final FluidTank water = new FluidTank(WATER_TANK, fs -> fs.getFluid().isSame(Fluids.WATER)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final FluidTank cryo = new FluidTank(CRYO_TANK, CryoSource::is) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    /** External fluid view: two fill-only flasks (water left, Liquid Cryo-Source right). */
    private final IFluidHandler input = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return t == 0 ? water.getFluid() : t == 1 ? cryo.getFluid() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int t) {
            return t == 0 ? WATER_TANK : t == 1 ? CRYO_TANK : 0;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return t == 0 ? water.isFluidValid(stack) : t == 1 && cryo.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (water.isFluidValid(resource)) {
                return water.fill(resource, action);
            }
            return cryo.isFluidValid(resource) ? cryo.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    private final OwnedContainer output = new OwnedContainer(1, this, s -> false);
    private final IItemHandler tray = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? output.getItem(0) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack in = output.getItem(0);
            if (in.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, in.getCount());
            ItemStack out = in.copyWithCount(n);
            if (!simulate) {
                output.removeItem(0, n);
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 64 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.MIXER, this);
    private final RedstoneControl redstone = new RedstoneControl();
    private final List<BlockPos> autoOutputCells;

    private Mode mode = Mode.SNOW;
    private boolean autoOutput = true;
    private int progress;
    private int status;
    private long lastWork = -100;
    private boolean working;
    private boolean dirty;
    private long lastSync = -100;
    /** Quantized visual state last broadcast to clients (avoids allocating a summary String). */
    private int sentWater = -1, sentCryo = -1, sentTrayCount = -1;
    @Nullable
    private Item sentTrayItem;
    private boolean sentWorking;
    @Nullable
    private Mode sentMode;

    // client
    private int clientWater, clientCryo, clientTray;
    private boolean clientWorking;
    private Mode clientMode = Mode.SNOW;
    /** Client animation state: work spin 0..1, flask fill 0..1, gear angle. */
    public float work, workPrev, waterLevel, waterPrev, cryoLevel, cryoPrev, phase, phasePrev;

    public CryoMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_MIXER.get(), pos, state);
        this.autoOutputCells = List.of(pos.immutable(), pos.above().immutable());
    }

    public IItemHandler tray() {
        return tray;
    }

    public IFluidHandler input() {
        return input;
    }

    public FluidStack water() {
        return water.getFluid();
    }

    public FluidStack cryo() {
        return cryo.getFluid();
    }

    public OwnedContainer getOutput() {
        return output;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            progress = 0;
            setChanged();
        }
    }

    public int status() {
        return status;
    }

    public boolean autoOutput() {
        return autoOutput;
    }

    public int ticksPerBlock() {
        return Math.max(1, (int) Math.ceil(mode.ticks() / (double) (1 + upgrades.count(UpgradeType.SPEED))));
    }

    @Override
    public void setChanged() {
        dirty = true;
        super.setChanged();
    }

    public void serverTick() {
        long now = level.getGameTime();
        boolean hasRedstone = upgrades.has(UpgradeType.REDSTONE);
        boolean powered = hasRedstone && (level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.above()));
        if (hasRedstone) {
            redstone.observe(powered);
        }
        work(hasRedstone, powered, now);
        if (autoOutput && !output.getItem(0).isEmpty() && now % AutoOutput.INTERVAL == 0) {
            ItemStack trayStack = output.getItem(0);
            ItemStack rest = AutoOutput.push(level, autoOutputCells, trayStack);
            if (rest.getCount() != trayStack.getCount()) {
                output.setItem(0, rest);
                setChanged();
            }
        }
        boolean nowWorking = now - lastWork < 10;
        if (nowWorking != working) {
            working = nowWorking;
            dirty = true;
        }
        if (dirty && now - lastSync >= 10) {
            dirty = false;
            syncIfVisuallyChanged(now);
        }
    }

    private void syncIfVisuallyChanged(long now) {
        int w = water.getFluidAmount() * 20 / WATER_TANK;
        int c = cryo.getFluidAmount() * 20 / CRYO_TANK;
        ItemStack trayStack = output.getItem(0);
        int trayCount = Math.min(3, trayStack.getCount());
        Item trayItem = trayStack.getItem();
        if (w != sentWater || c != sentCryo || trayCount != sentTrayCount
                || trayItem != sentTrayItem || working != sentWorking || mode != sentMode) {
            sentWater = w;
            sentCryo = c;
            sentTrayCount = trayCount;
            sentTrayItem = trayItem;
            sentWorking = working;
            sentMode = mode;
            lastSync = now;
            BlockEntities.sendUpdate(this);
        }
    }

    private void work(boolean hasRedstone, boolean powered, long now) {
        Item expected = mode == Mode.SNOW ? Items.SNOW_BLOCK : Items.ICE;
        ItemStack trayStack = output.getItem(0);
        if (!trayStack.isEmpty() && (!trayStack.is(expected) || trayStack.getCount() >= trayStack.getMaxStackSize())) {
            status = TRAY_FULL;
            return;
        }
        if (water.getFluidAmount() < mode.water()) {
            status = NO_WATER;
            return;
        }
        if (cryo.getFluidAmount() < mode.cryo()) {
            status = NO_CRYO;
            return;
        }
        // a new block consumes a pulse when starting; an in-progress block runs to completion
        boolean allowed = progress == 0 ? redstone.allows(hasRedstone, powered, true)
                : redstone.mode() == RedstoneMode.PULSE || redstone.allows(hasRedstone, powered, false);
        if (!allowed) {
            status = PAUSED;
            return;
        }
        status = WORKING;
        lastWork = now;
        progress++;
        if (progress < ticksPerBlock()) {
            return;
        }
        progress = 0;
        water.drain(mode.water(), IFluidHandler.FluidAction.EXECUTE);
        cryo.drain(mode.cryo(), IFluidHandler.FluidAction.EXECUTE);
        if (trayStack.isEmpty()) {
            output.setItem(0, mode.output());
        } else {
            trayStack.grow(1);
            output.setChanged();
        }
        level.playSound(null, worldPosition, mode == Mode.SNOW ? SoundEvents.SNOW_PLACE : SoundEvents.GLASS_PLACE, SoundSource.BLOCKS,
                0.45f, mode == Mode.SNOW ? 1.1f : 1.4f);
        setChanged();
    }

    public static final int DATA_COUNT = 11;

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> water.getFluidAmount();
                    case 1 -> cryo.getFluidAmount();
                    case 2 -> progress & 0xFFFF;
                    case 3 -> ticksPerBlock() & 0xFFFF;
                    case 4 -> mode.ordinal();
                    case 5 -> status;
                    case 6 -> upgrades.count(UpgradeType.SPEED);
                    case 7 -> redstone.mode().ordinal();
                    case 8 -> autoOutput ? 1 : 0;
                    case 9 -> progress >>> 16;
                    case 10 -> ticksPerBlock() >>> 16;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 4) {
                    setMode(Mode.byId(value));
                } else if (index == 8) {
                    autoOutput = value != 0;
                    setChanged();
                } else if (index == 7) {
                    redstone.setMode(RedstoneMode.byId(value));
                    setChanged();
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public void dropContents() {
        BlockEntities.dropContainers(level, worldPosition, output, upgrades);
    }

    // ----------------------------------------------------------------- client

    public int clientTray() {
        return clientTray;
    }

    public Mode clientMode() {
        return clientMode;
    }

    public void clientTick() {
        workPrev = work;
        waterPrev = waterLevel;
        cryoPrev = cryoLevel;
        phasePrev = phase;
        work += ((clientWorking ? 1f : 0f) - work) * (clientWorking ? 0.08f : 0.04f);
        waterLevel += (clientWater / 20f - waterLevel) * 0.1f;
        cryoLevel += (clientCryo / 20f - cryoLevel) * 0.1f;
        phase += 0.01f + 0.2f * work;
        if (clientWorking && level.getRandom().nextInt(5) == 0) {
            double side = level.getRandom().nextBoolean() ? 0.08 : 0.92;
            level.addParticle(ParticleTypes.SNOWFLAKE, worldPosition.getX() + side, worldPosition.getY() + 0.6,
                    worldPosition.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
        }
    }

    // ----------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Water", water.writeToNBT(registries, new CompoundTag()));
        tag.put("Cryo", cryo.writeToNBT(registries, new CompoundTag()));
        tag.put("Output", output.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Progress", progress);
        tag.putBoolean("AutoOutput", autoOutput);
        redstone.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        water.readFromNBT(registries, tag.getCompound("Water"));
        cryo.readFromNBT(registries, tag.getCompound("Cryo"));
        output.fromTag(tag.getList("Output", Tag.TAG_COMPOUND), registries);
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
        mode = Mode.byId(tag.getInt("Mode"));
        progress = tag.getInt("Progress");
        autoOutput = !tag.contains("AutoOutput") || tag.getBoolean("AutoOutput");
        redstone.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Water", water.getFluidAmount() * 20 / WATER_TANK);
        tag.putInt("Cryo", cryo.getFluidAmount() * 20 / CRYO_TANK);
        ItemStack t = output.getItem(0);
        tag.putInt("Tray", t.isEmpty() ? 0 : Math.min(3, t.getCount()) * (t.is(Items.ICE) ? -1 : 1));
        tag.putBoolean("Working", working);
        tag.putInt("Mode", mode.ordinal());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientWater = tag.getInt("Water");
        clientCryo = tag.getInt("Cryo");
        clientTray = tag.getInt("Tray");
        clientWorking = tag.getBoolean("Working");
        clientMode = Mode.byId(tag.getInt("Mode"));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }
}
