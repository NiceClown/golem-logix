package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.AutoOutput;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.RedstoneControl;
import com.golemlogix.upgrade.RedstoneMode;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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

    private final FluidStacksResourceHandler water = new FluidStacksResourceHandler(1, WATER_TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.is(Fluids.WATER);
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final FluidStacksResourceHandler cryo = new FluidStacksResourceHandler(1, CRYO_TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return CryoSource.is(resource.toStack(1));
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final OwnedContainer output = new OwnedContainer(1, this, s -> false);
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

    private final ResourceHandler<FluidResource> fluidResources =
            Transfers.insertOnly(new CombinedResourceHandler<>(water, cryo));
    private final ResourceHandler<ItemResource> itemResources =
            Transfers.extractOnly(VanillaContainerWrapper.of(output));

    public CryoMixerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_MIXER.get(), pos, state);
        this.autoOutputCells = List.of(pos.immutable(), pos.above().immutable());
    }

    public ResourceHandler<FluidResource> fluidResources() {
        return fluidResources;
    }

    public ResourceHandler<ItemResource> itemResources() {
        return itemResources;
    }

    public FluidStack water() {
        return Transfers.getFluid(water, 0);
    }

    public FluidStack cryo() {
        return Transfers.getFluid(cryo, 0);
    }

    private static void consume(FluidStacksResourceHandler tank, int mb) {
        int left = Math.max(0, tank.getAmountAsInt(0) - mb);
        tank.set(0, left == 0 ? FluidResource.EMPTY : tank.getResource(0), left);
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
            ItemStack tray = output.getItem(0);
            ItemStack rest = AutoOutput.push(level, autoOutputCells, tray);
            if (rest.getCount() != tray.getCount()) {
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
        int w = water.getAmountAsInt(0) * 20 / WATER_TANK;
        int c = cryo.getAmountAsInt(0) * 20 / CRYO_TANK;
        ItemStack tray = output.getItem(0);
        int trayCount = Math.min(3, tray.getCount());
        Item trayItem = tray.getItem();
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
        ItemStack tray = output.getItem(0);
        if (!tray.isEmpty() && (!tray.is(expected) || tray.getCount() >= tray.getMaxStackSize())) {
            status = TRAY_FULL;
            return;
        }
        if (water.getAmountAsInt(0) < mode.water()) {
            status = NO_WATER;
            return;
        }
        if (cryo.getAmountAsInt(0) < mode.cryo()) {
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
        consume(water, mode.water());
        consume(cryo, mode.cryo());
        if (tray.isEmpty()) {
            output.setItem(0, mode.output());
        } else {
            tray.grow(1);
            output.setChanged();
        }
        level.playSound(null, worldPosition, mode == Mode.SNOW ? SoundEvents.SNOW_PLACE : SoundEvents.GLASS_PLACE, SoundSource.BLOCKS,
                0.45f, mode == Mode.SNOW ? 1.1f : 1.4f);
        setChanged();
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> water.getAmountAsInt(0);
                    case 1 -> cryo.getAmountAsInt(0);
                    case 2 -> progress;
                    case 3 -> ticksPerBlock();
                    case 4 -> mode.ordinal();
                    case 5 -> status;
                    case 6 -> upgrades.count(UpgradeType.SPEED);
                    case 7 -> redstone.mode().ordinal();
                    case 8 -> autoOutput ? 1 : 0;
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
                return 9;
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

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Water", NbtBridge.saveTank(water, registries));
        tag.put("Cryo", NbtBridge.saveTank(cryo, registries));
        tag.put("Output", output.createTag(registries));
        tag.put("Upgrades", upgrades.createTag(registries));
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("Progress", progress);
        tag.putBoolean("AutoOutput", autoOutput);
        redstone.save(tag);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        NbtBridge.loadTank(water, registries, tag.getCompoundOrEmpty("Water"));
        NbtBridge.loadTank(cryo, registries, tag.getCompoundOrEmpty("Cryo"));
        output.fromTag(tag.getListOrEmpty("Output"), registries);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
        mode = Mode.byId(tag.getIntOr("Mode", 0));
        progress = tag.getIntOr("Progress", 0);
        autoOutput = tag.getBooleanOr("AutoOutput", true);
        redstone.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Water", water.getAmountAsInt(0) * 20 / WATER_TANK);
        tag.putInt("Cryo", cryo.getAmountAsInt(0) * 20 / CRYO_TANK);
        ItemStack t = output.getItem(0);
        tag.putInt("Tray", t.isEmpty() ? 0 : Math.min(3, t.getCount()) * (t.is(Items.ICE) ? -1 : 1));
        tag.putBoolean("Working", working);
        tag.putInt("Mode", mode.ordinal());
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientWater = tag.getIntOr("Water", 0);
        clientCryo = tag.getIntOr("Cryo", 0);
        clientTray = tag.getIntOr("Tray", 0);
        clientWorking = tag.getBooleanOr("Working", false);
        clientMode = Mode.byId(tag.getIntOr("Mode", 0));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        BlockEntities.save(this, out, this::saveTag);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        BlockEntities.load(in, this::loadTag);
    }

    @Override
    public void handleUpdateTag(ValueInput in) {
        BlockEntities.load(in, this::readUpdateTag);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput in) {
        handleUpdateTag(in);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        BlockEntities.onPreRemove(this, pos, state);
    }
}
