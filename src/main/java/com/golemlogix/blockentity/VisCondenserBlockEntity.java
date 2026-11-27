package com.golemlogix.blockentity;

import com.golemlogix.blocks.VisCondenserBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.integration.thaumaturge.ThaumaturgeCompat;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.system.VisCondenserMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;

/**
 * Vis Condenser: drains chunk Vis twice per second and condenses it into Cryo-Source for adjacent and network-joined jars.
 * Supports percent or exact Aura stop thresholds with hysteresis, Redstone pause, and Flux pollution below 10% base Vis.
 */
public class VisCondenserBlockEntity extends BlockEntity {
    public static final int MODE_PERCENT = VisCondenserMath.MODE_PERCENT;
    public static final int MODE_EXACT = VisCondenserMath.MODE_EXACT;
    public static final int DEFAULT_THRESHOLD_PERCENT = 25;
    public static final int DEFAULT_THRESHOLD_EXACT = 50;

    public static final int STATUS_WORKING = 0;
    public static final int STATUS_LOW_VIS_WORKING = 1;
    public static final int STATUS_FLOOR_PAUSED = 2;
    public static final int STATUS_NO_JAR = 3;
    public static final int STATUS_REDSTONE = 4;
    public static final int STATUS_NO_THAUMATURGE = 5;

    private static final int PERIOD = 10;

    private final List<BlockPos> cells;

    private int mode = MODE_PERCENT;
    private int thresholdPercent = DEFAULT_THRESHOLD_PERCENT;
    private int thresholdExact = DEFAULT_THRESHOLD_EXACT;
    private boolean pausedByFloor;
    private boolean lowVisWarning;
    private boolean redstonePowered;
    private int status = STATUS_FLOOR_PAUSED;
    private double carry;
    private float cachedVis;
    private int cachedBase;
    private float cachedFlux;
    private boolean auraInitialized;
    private boolean dirty;
    private long lastSync = -100;

    // Last broadcast quantized visual state
    private int sentVisTenths = -1;
    private int sentBase = -1;
    private int sentFluxTenths = -1;
    private int sentThreshold = -1;
    private int sentMode = -1;
    private int sentStatus = -1;
    private boolean sentLowVis;

    // Client-synced visual state (read by VisCondenserRenderer)
    public float clientVis, clientFlux;
    public int clientBase, clientThreshold = DEFAULT_THRESHOLD_PERCENT, clientMode = MODE_PERCENT, clientStatus = STATUS_FLOOR_PAUSED;
    public boolean clientLowVisWarning;
    public float work, workPrev, phase, phasePrev, needle, needlePrev;

    public VisCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VIS_CONDENSER.get(), pos, state);
        this.cells = VisCondenserBlock.cells(pos);
    }

    public float chunkVis() {
        if (level != null && !level.isClientSide() && ThaumaturgeCompat.isLoaded()) {
            refreshAuraCache();
            return cachedVis;
        }
        return clientVis;
    }

    public int chunkBase() {
        if (level != null && !level.isClientSide() && ThaumaturgeCompat.isLoaded()) {
            refreshAuraCache();
            return cachedBase;
        }
        return clientBase;
    }

    public float chunkFlux() {
        if (level != null && !level.isClientSide() && ThaumaturgeCompat.isLoaded()) {
            refreshAuraCache();
            return cachedFlux;
        }
        return clientFlux;
    }

    public int mode() {
        return mode;
    }

    public int threshold() {
        return mode == MODE_PERCENT ? thresholdPercent : thresholdExact;
    }

    public void setMode(int newMode) {
        int clamped = newMode == MODE_EXACT ? MODE_EXACT : MODE_PERCENT;
        if (this.mode != clamped) {
            this.mode = clamped;
            checkImmediateUnpause();
            setChanged();
        }
    }

    public void setThreshold(int value) {
        int clamped = VisCondenserMath.clampThreshold(mode, value);
        boolean changed = mode == MODE_PERCENT ? thresholdPercent != clamped : thresholdExact != clamped;
        if (mode == MODE_PERCENT) {
            thresholdPercent = clamped;
        } else {
            thresholdExact = clamped;
        }
        if (changed) {
            checkImmediateUnpause();
            setChanged();
        }
    }

    public void adjustThreshold(int delta) {
        setThreshold(threshold() + delta);
    }

    public int status() {
        return status;
    }

    public boolean pausedByFloor() {
        return pausedByFloor;
    }

    public boolean lowVisWarning() {
        return lowVisWarning;
    }

    public boolean redstonePowered() {
        return redstonePowered;
    }

    public double effectiveFloor() {
        return VisCondenserMath.floorVis(mode, threshold(), chunkBase());
    }

    public double sourcePerSecond() {
        return GolemConfig.VIS_CONDENSER_DRAIN_PER_SEC.get() * GolemConfig.VIS_CONDENSER_SOURCE_PER_VIS.get();
    }

    private void refreshAuraCache() {
        ThaumaturgeCompat.AuraSnapshot snap = ThaumaturgeCompat.readAura(level, worldPosition);
        cachedVis = snap.vis();
        cachedBase = snap.base();
        cachedFlux = snap.flux();
        auraInitialized = true;
    }

    private void checkImmediateUnpause() {
        if (level == null || level.isClientSide() || !ThaumaturgeCompat.isLoaded()) {
            return;
        }
        refreshAuraCache();
        double newFloor = VisCondenserMath.floorVis(mode, threshold(), cachedBase);
        if (VisCondenserMath.shouldClearPauseOnThresholdChange(cachedVis, newFloor)) {
            pausedByFloor = false;
        }
    }

    @Override
    public void setChanged() {
        dirty = true;
        super.setChanged();
    }

    public void serverTick() {
        long now = level.getGameTime();
        if (!ThaumaturgeCompat.isLoaded()) {
            status = STATUS_NO_THAUMATURGE;
            lowVisWarning = false;
            syncIfNeeded(now);
            return;
        }
        redstonePowered = level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.above());
        if (!auraInitialized) {
            refreshAuraCache();
            dirty = true;
        }
        if (redstonePowered) {
            if (status != STATUS_REDSTONE || lowVisWarning) {
                status = STATUS_REDSTONE;
                lowVisWarning = false;
                dirty = true;
            }
        } else if ((now + worldPosition.asLong()) % PERIOD == 0) {
            condense(now);
        }
        syncIfNeeded(now);
    }

    private void condense(long now) {
        refreshAuraCache();
        if (redstonePowered) {
            status = STATUS_REDSTONE;
            lowVisWarning = false;
            return;
        }
        // Deliver any fractional carried Source first
        if (carry >= 1.0) {
            int owed = (int) Math.floor(carry + 1e-9);
            int put = SourceHelper.fillAroundAndNetworks(level, cells, owed, false);
            if (put > 0) {
                carry = Math.max(0.0, carry - put);
                setChanged();
            }
            if (carry >= 1.0) {
                status = STATUS_NO_JAR;
                lowVisWarning = false;
                return;
            }
        }

        double floor = VisCondenserMath.floorVis(mode, threshold(), cachedBase);
        double resume = VisCondenserMath.resumeVis(
                mode, threshold(), cachedBase,
                GolemConfig.VIS_CONDENSER_HYSTERESIS_PERCENT.get(),
                GolemConfig.VIS_CONDENSER_HYSTERESIS_EXACT.get());
        boolean nextPaused = VisCondenserMath.nextPausedState(pausedByFloor, cachedVis, floor, resume);
        if (nextPaused != pausedByFloor) {
            pausedByFloor = nextPaused;
            setChanged();
        }
        if (pausedByFloor) {
            status = STATUS_FLOOR_PAUSED;
            lowVisWarning = false;
            return;
        }

        int room = SourceHelper.fillAroundAndNetworks(level, cells, Integer.MAX_VALUE, true);
        if (room <= 0) {
            status = STATUS_NO_JAR;
            lowVisWarning = false;
            return;
        }

        double drainPerSec = GolemConfig.VIS_CONDENSER_DRAIN_PER_SEC.get();
        double sourcePerVis = GolemConfig.VIS_CONDENSER_SOURCE_PER_VIS.get();
        double batchVis = drainPerSec * PERIOD / 20.0;
        double aboveFloor = Math.max(0.0, cachedVis - floor);
        double byRoom = Math.max(0.0, (room - carry) / sourcePerVis);
        float toDrain = (float) Math.min(Math.min(batchVis, aboveFloor), byRoom);
        if (toDrain <= 1e-4f) {
            if (aboveFloor <= 1e-4) {
                pausedByFloor = true;
                status = STATUS_FLOOR_PAUSED;
            } else {
                status = STATUS_NO_JAR;
            }
            lowVisWarning = false;
            return;
        }

        float drained = ThaumaturgeCompat.drainVis(level, worldPosition, toDrain, false);
        if (drained <= 0f) {
            refreshAuraCache();
            pausedByFloor = true;
            status = STATUS_FLOOR_PAUSED;
            lowVisWarning = false;
            setChanged();
            return;
        }

        VisCondenserMath.CarryStep step = VisCondenserMath.stepCarry(drained, sourcePerVis, carry);
        int give = step.wholeSource();
        carry = step.nextCarry();
        if (give > 0) {
            int delivered = SourceHelper.fillAroundAndNetworks(level, cells, give, false);
            carry += Math.max(0, give - delivered);
        }

        refreshAuraCache();
        int lowPct = GolemConfig.VIS_CONDENSER_LOW_VIS_PERCENT.get();
        double fluxPerVis = GolemConfig.VIS_CONDENSER_LOW_VIS_FLUX.get();
        double flux = VisCondenserMath.fluxForDrain(drained, cachedVis, cachedBase, lowPct, fluxPerVis);
        if (flux > 0.0) {
            ThaumaturgeCompat.polluteAura(level, worldPosition.above(), (float) flux, true);
            refreshAuraCache();
            lowVisWarning = true;
            status = STATUS_LOW_VIS_WORKING;
        } else if (VisCondenserMath.isLowVis(cachedVis, cachedBase, lowPct)) {
            lowVisWarning = true;
            status = STATUS_LOW_VIS_WORKING;
        } else {
            lowVisWarning = false;
            status = STATUS_WORKING;
        }
        // Latch floor pause immediately when this drain hits the configured floor so ambient aura trickle
        // between 10-tick cycles does not bypass hysteresis.
        if (cachedVis <= floor + 1e-3 || toDrain >= aboveFloor - 1e-4) {
            pausedByFloor = true;
            if (!lowVisWarning) {
                status = STATUS_FLOOR_PAUSED;
            }
        }
        setChanged();
        if (now % 60 < PERIOD) {
            level.playSound(null, worldPosition.above(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.2f, 1.3f);
        }
    }

    private void syncIfNeeded(long now) {
        if (!dirty && now - lastSync < 10) {
            return;
        }
        if (now - lastSync < 5) {
            return;
        }
        dirty = false;
        int visTenths = Math.round(cachedVis * 10f);
        int fluxTenths = Math.round(cachedFlux * 10f);
        int th = threshold();
        if (visTenths != sentVisTenths || cachedBase != sentBase || fluxTenths != sentFluxTenths
                || th != sentThreshold || mode != sentMode || status != sentStatus || lowVisWarning != sentLowVis) {
            sentVisTenths = visTenths;
            sentBase = cachedBase;
            sentFluxTenths = fluxTenths;
            sentThreshold = th;
            sentMode = mode;
            sentStatus = status;
            sentLowVis = lowVisWarning;
            lastSync = now;
            BlockEntities.sendUpdate(this);
        }
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> Math.round(chunkVis() * 10f);
                    case 1 -> chunkBase();
                    case 2 -> Math.round(chunkFlux() * 10f);
                    case 3 -> threshold();
                    case 4 -> mode;
                    case 5 -> status;
                    case 6 -> (int) Math.round(sourcePerSecond() * 10.0);
                    case 7 -> (lowVisWarning ? 1 : 0) | (pausedByFloor ? 2 : 0) | (redstonePowered ? 4 : 0);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 3) {
                    setThreshold(value);
                } else if (index == 4) {
                    setMode(value);
                }
            }

            @Override
            public int getCount() {
                return 8;
            }
        };
    }

    // ----------------------------------------------------------------- client

    public void clientTick() {
        workPrev = work;
        phasePrev = phase;
        needlePrev = needle;
        boolean active = clientStatus == STATUS_WORKING || clientStatus == STATUS_LOW_VIS_WORKING;
        work += ((active ? 1f : 0f) - work) * (active ? 0.08f : 0.04f);
        float targetNeedle = clientBase > 0 ? Mth.clamp(clientVis / (float) clientBase, 0f, 1f) : 0f;
        needle += (targetNeedle - needle) * 0.12f;
        float speedBoost = clientStatus == STATUS_LOW_VIS_WORKING ? 0.26f : 0.18f;
        phase += 0.02f + speedBoost * work;
        if (active && level != null && level.getRandom().nextInt(5) == 0) {
            double x = worldPosition.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.7;
            double z = worldPosition.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.7;
            level.addParticle(clientStatus == STATUS_LOW_VIS_WORKING ? ParticleTypes.WITCH : ParticleTypes.SNOWFLAKE,
                    x, worldPosition.getY() + 1.65, z, 0.0, 0.015, 0.0);
        }
    }

    // ------------------------------------------------------------ persistence

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Mode", mode);
        tag.putInt("ThresholdPercent", thresholdPercent);
        tag.putInt("ThresholdExact", thresholdExact);
        tag.putBoolean("PausedByFloor", pausedByFloor);
        tag.putDouble("Carry", carry);
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        mode = tag.getIntOr("Mode", MODE_PERCENT) == MODE_EXACT ? MODE_EXACT : MODE_PERCENT;
        thresholdPercent = VisCondenserMath.clampThreshold(MODE_PERCENT, tag.getIntOr("ThresholdPercent", DEFAULT_THRESHOLD_PERCENT));
        thresholdExact = VisCondenserMath.clampThreshold(MODE_EXACT, tag.getIntOr("ThresholdExact", DEFAULT_THRESHOLD_EXACT));
        pausedByFloor = tag.getBooleanOr("PausedByFloor", false);
        carry = Math.max(0.0, tag.getDoubleOr("Carry", 0.0));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Vis", cachedVis);
        tag.putInt("Base", cachedBase);
        tag.putFloat("Flux", cachedFlux);
        tag.putInt("Threshold", threshold());
        tag.putInt("Mode", mode);
        tag.putInt("Status", status);
        tag.putBoolean("LowVis", lowVisWarning);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientVis = tag.getFloatOr("Vis", 0f);
        clientBase = tag.getIntOr("Base", 0);
        clientFlux = tag.getFloatOr("Flux", 0f);
        clientThreshold = tag.getIntOr("Threshold", DEFAULT_THRESHOLD_PERCENT);
        clientMode = tag.getIntOr("Mode", MODE_PERCENT);
        clientStatus = tag.getIntOr("Status", STATUS_FLOOR_PAUSED);
        clientLowVisWarning = tag.getBooleanOr("LowVis", false);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
