package com.golemlogix.blockentity;

import com.golemlogix.blocks.EssentiaCauldronBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.integration.thaumaturge.ThaumaturgeCompat;
import com.golemlogix.system.CauldronMath;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Essentia Cryo-Cauldron: condenses primary aspect (Auram, viewer's left) + coolant aspect (Gelum, viewer's right)
 * over an Ice block underneath into Cryo-Source for adjacent and network-joined jars. Scales with up to 28
 * Compressed Co-Processors, pauses cleanly on Redstone, and boils off uncooled/unbalanced Auram into Flux after a 40-tick grace.
 */
public class EssentiaCauldronBlockEntity extends BlockEntity {
    public static final int TANK_CAPACITY = 64;
    public static final int ICE_NONE = CauldronMath.ICE_NONE;
    public static final int ICE_NORMAL = CauldronMath.ICE_NORMAL;
    public static final int ICE_PACKED = CauldronMath.ICE_PACKED;
    public static final int ICE_BLUE = CauldronMath.ICE_BLUE;

    public static final int STATUS_WORKING = 0;
    public static final int STATUS_NO_AURAM = 1;
    public static final int STATUS_NO_GELUM = 2;
    public static final int STATUS_NO_ICE = 3;
    public static final int STATUS_NO_JAR = 4;
    public static final int STATUS_REDSTONE = 5;
    public static final int STATUS_BOIL_OFF = 6;
    public static final int STATUS_NO_THAUMATURGE = 7;

    private static final int PULL_INTERVAL = 5;
    private static final int MAX_PULL_PER_CYCLE = 16;

    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.ESSENTIA_CAULDRON, this);
    private final List<BlockPos> cells;

    private int auram;
    private int gelum;
    private int iceTier;
    private int iceCharge;
    private int boilTimer;
    private boolean boilOff;
    private boolean redstonePaused;
    private double reactionProgress;
    private int sourceCarry;
    private int status = STATUS_NO_AURAM;
    private long contentRevision;
    private boolean tubesRefreshed;
    private long lastSync = -100;

    // Last broadcast quantized visual state
    private int sentAuram = -1;
    private int sentGelum = -1;
    private int sentIceTier = -1;
    private int sentIcePct = -1;
    private int sentStatus = -1;
    private boolean sentBoilOff;

    // Client-synced visual state (read by EssentiaCauldronRenderer)
    public int clientAuram, clientGelum, clientIceTier, clientStatus = STATUS_NO_AURAM;
    public float clientIceFill;
    public boolean clientBoilOff;
    public float work, workPrev, phase, phasePrev;

    public EssentiaCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_CAULDRON.get(), pos, state);
        this.cells = EssentiaCauldronBlock.cells(pos);
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(EssentiaCauldronBlock.FACING) ? s.getValue(EssentiaCauldronBlock.FACING) : Direction.NORTH;
    }

    public Direction primarySide() {
        return EssentiaCauldronBlock.primarySide(facing());
    }

    public Direction coolantSide() {
        return EssentiaCauldronBlock.coolantSide(facing());
    }

    public int auram() {
        return auram;
    }

    public int gelum() {
        return gelum;
    }

    public int capacity() {
        return TANK_CAPACITY;
    }

    public int primarySpace() {
        return Math.max(0, TANK_CAPACITY - auram);
    }

    public int coolantSpace() {
        return Math.max(0, TANK_CAPACITY - gelum);
    }

    public int addPrimary(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int accept = Math.min(amount, primarySpace());
        if (accept > 0 && !simulate) {
            auram += accept;
            contentRevision++;
            setChanged();
        }
        return accept;
    }

    public int addCoolant(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int accept = Math.min(amount, coolantSpace());
        if (accept > 0 && !simulate) {
            gelum += accept;
            contentRevision++;
            setChanged();
        }
        return accept;
    }

    /** Direct tank mutation for transactional {@code IEssentiaStorage} snapshots (commit triggers {@link #onStorageCommitted()}). */
    public void restoreTanksRaw(int newAuram, int newGelum) {
        auram = Math.max(0, Math.min(TANK_CAPACITY, newAuram));
        gelum = Math.max(0, Math.min(TANK_CAPACITY, newGelum));
    }

    public void onStorageCommitted() {
        contentRevision++;
        setChanged();
    }

    public long contentRevision() {
        return contentRevision;
    }

    public boolean isRedstonePaused() {
        return redstonePaused;
    }

    public int iceTier() {
        return iceTier;
    }

    public int iceCharge() {
        return iceCharge;
    }

    public int iceMaxCharge() {
        return CauldronMath.iceChargeForTier(
                iceTier,
                GolemConfig.CAULDRON_ICE_CHARGE.get(),
                GolemConfig.CAULDRON_PACKED_ICE_CHARGE.get(),
                GolemConfig.CAULDRON_BLUE_ICE_CHARGE.get());
    }

    public float iceFill() {
        int max = iceMaxCharge();
        return iceTier <= ICE_NONE || max <= 0 ? 0f : Math.max(0f, Math.min(1f, iceCharge / (float) max));
    }

    public int status() {
        return status;
    }

    public boolean boilOff() {
        return boilOff;
    }

    public int boilTimer() {
        return boilTimer;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public double reactionsPerSecond() {
        return CauldronMath.reactionsPerSecond(
                GolemConfig.CAULDRON_BASE_SPEED.get(),
                GolemConfig.CAULDRON_COPROCESSOR_BONUS.get(),
                upgrades.count(UpgradeType.SPEED));
    }

    public double sourcePerSecond() {
        return CauldronMath.sourcePerSecond(
                GolemConfig.CAULDRON_BASE_SPEED.get(),
                GolemConfig.CAULDRON_COPROCESSOR_BONUS.get(),
                upgrades.count(UpgradeType.SPEED),
                GolemConfig.CAULDRON_SOURCE_PER_REACTION.get());
    }

    public void dropContents() {
        BlockEntities.dropContainers(level, worldPosition, upgrades);
    }

    private static int detectIceTier(BlockState state) {
        if (state.is(Blocks.BLUE_ICE)) {
            return ICE_BLUE;
        }
        if (state.is(Blocks.PACKED_ICE)) {
            return ICE_PACKED;
        }
        if (state.is(Blocks.ICE)) {
            return ICE_NORMAL;
        }
        return ICE_NONE;
    }

    private void syncIceBelow() {
        BlockPos below = worldPosition.below();
        int worldTier = level.isLoaded(below) ? detectIceTier(level.getBlockState(below)) : ICE_NONE;
        if (worldTier != iceTier) {
            iceTier = worldTier;
            iceCharge = iceMaxCharge();
            setChanged();
        } else if (iceTier > ICE_NONE && iceCharge > iceMaxCharge()) {
            iceCharge = iceMaxCharge();
        }
    }

    private void applyIceDegrade(int newTier) {
        BlockPos below = worldPosition.below();
        if (!level.isLoaded(below)) {
            return;
        }
        BlockState nextState = switch (newTier) {
            case ICE_PACKED -> Blocks.PACKED_ICE.defaultBlockState();
            case ICE_NORMAL -> Blocks.ICE.defaultBlockState();
            default -> Blocks.WATER.defaultBlockState();
        };
        level.setBlock(below, nextState, Block.UPDATE_ALL);
        if (newTier == ICE_NONE) {
            level.playSound(null, below, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35f, 1.4f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SPLASH, below.getX() + 0.5, below.getY() + 0.9, below.getZ() + 0.5,
                        8, 0.3, 0.1, 0.3, 0.05);
            }
        } else {
            level.playSound(null, below, SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.4f, 1.2f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SNOWFLAKE, below.getX() + 0.5, below.getY() + 0.9, below.getZ() + 0.5,
                        6, 0.3, 0.1, 0.3, 0.02);
            }
        }
    }

    public void serverTick() {
        long now = level.getGameTime();
        if (!tubesRefreshed) {
            tubesRefreshed = ThaumaturgeCompat.refreshAdjacentTubes(level, worldPosition);
        }
        syncIceBelow();
        if (!ThaumaturgeCompat.isLoaded()) {
            status = STATUS_NO_THAUMATURGE;
            boilOff = false;
            boilTimer = 0;
            syncIfNeeded(now);
            return;
        }

        redstonePaused = level.hasNeighborSignal(worldPosition) || level.hasNeighborSignal(worldPosition.above());
        if (redstonePaused) {
            if (boilTimer != 0 || boilOff || status != STATUS_REDSTONE) {
                boilTimer = 0;
                boilOff = false;
                status = STATUS_REDSTONE;
                setChanged();
            }
            syncIfNeeded(now);
            return;
        }

        // Actively pull matching Essentia from adjacent Thaumaturge tubes / jars every 5 ticks
        if ((now + worldPosition.asLong()) % PULL_INTERVAL == 0) {
            int pSpace = primarySpace();
            if (pSpace > 0) {
                int pulled = ThaumaturgeCompat.pullEssentia(level, worldPosition, primarySide(), true, Math.min(pSpace, MAX_PULL_PER_CYCLE));
                if (pulled > 0) {
                    addPrimary(pulled, false);
                }
            }
            int cSpace = coolantSpace();
            if (cSpace > 0) {
                int pulled = ThaumaturgeCompat.pullEssentia(level, worldPosition, coolantSide(), false, Math.min(cSpace, MAX_PULL_PER_CYCLE));
                if (pulled > 0) {
                    addCoolant(pulled, false);
                }
            }
        }

        int primaryCost = GolemConfig.CAULDRON_PRIMARY_COST.get();
        int coolantCost = GolemConfig.CAULDRON_COOLANT_COST.get();
        int sourcePerReaction = GolemConfig.CAULDRON_SOURCE_PER_REACTION.get();

        // Step boil-off state (grace window prevents spurious Flux while a Formation Bus replaces melted Ice)
        CauldronMath.BoilStep boil = CauldronMath.stepBoilOff(
                false, auram, gelum, coolantCost, iceTier, boilTimer,
                GolemConfig.CAULDRON_BOIL_GRACE_TICKS.get(),
                GolemConfig.CAULDRON_BOIL_INTERVAL_TICKS.get());
        boilTimer = boil.nextTimer();
        boilOff = boil.boiling();
        if (boil.emitFlux() && auram > 0) {
            auram = Math.max(0, auram - 1);
            contentRevision++;
            float flux = GolemConfig.CAULDRON_BOIL_FLUX.get().floatValue();
            ThaumaturgeCompat.polluteAura(level, worldPosition.above(), flux, true);
            level.playSound(null, worldPosition.above(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.35f, 1.3f);
            setChanged();
        }

        // Deliver any carried Source first
        if (sourceCarry > 0) {
            int delivered = SourceHelper.fillAroundAndNetworks(level, cells, sourceCarry, false);
            if (delivered > 0) {
                sourceCarry -= delivered;
                setChanged();
            }
        }

        if (boilOff) {
            status = STATUS_BOIL_OFF;
            reactionProgress = 0.0;
            syncIfNeeded(now);
            return;
        }
        if (iceTier == ICE_NONE) {
            status = STATUS_NO_ICE;
            reactionProgress = 0.0;
            syncIfNeeded(now);
            return;
        }
        if (auram < primaryCost) {
            status = STATUS_NO_AURAM;
            reactionProgress = 0.0;
            syncIfNeeded(now);
            return;
        }
        if (gelum < coolantCost) {
            status = STATUS_NO_GELUM;
            reactionProgress = 0.0;
            syncIfNeeded(now);
            return;
        }
        if (sourceCarry > 0) {
            status = STATUS_NO_JAR;
            syncIfNeeded(now);
            return;
        }
        int initialRoom = SourceHelper.fillAroundAndNetworks(level, cells, sourcePerReaction, true);
        if (initialRoom <= 0) {
            status = STATUS_NO_JAR;
            syncIfNeeded(now);
            return;
        }

        status = STATUS_WORKING;
        reactionProgress += reactionsPerSecond() / 20.0;
        int normalCharge = GolemConfig.CAULDRON_ICE_CHARGE.get();
        int packedCharge = GolemConfig.CAULDRON_PACKED_ICE_CHARGE.get();
        int blueCharge = GolemConfig.CAULDRON_BLUE_ICE_CHARGE.get();

        boolean reacted = false;
        while (reactionProgress >= 1.0 && auram >= primaryCost && gelum >= coolantCost && iceTier > ICE_NONE) {
            int room = reacted ? SourceHelper.fillAroundAndNetworks(level, cells, sourcePerReaction, true) : initialRoom;
            if (room <= 0) {
                reactionProgress = Math.min(reactionProgress, 1.0);
                status = STATUS_NO_JAR;
                break;
            }
            reactionProgress -= 1.0;
            auram -= primaryCost;
            gelum -= coolantCost;
            contentRevision++;
            reacted = true;

            int delivered = SourceHelper.fillAroundAndNetworks(level, cells, sourcePerReaction, false);
            sourceCarry += Math.max(0, sourcePerReaction - delivered);

            CauldronMath.IceStep iceStep = CauldronMath.consumeIce(
                    iceTier, iceCharge, sourcePerReaction, normalCharge, packedCharge, blueCharge);
            iceCharge = iceStep.newCharge();
            if (iceStep.degradedSteps() > 0) {
                iceTier = iceStep.newTier();
                applyIceDegrade(iceTier);
            }
        }
        if (reacted) {
            setChanged();
            if (now % 30 == 0) {
                level.playSound(null, worldPosition.above(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.3f, 0.9f);
            }
        }
        syncIfNeeded(now);
    }

    private void syncIfNeeded(long now) {
        if (now - lastSync < 5) {
            return;
        }
        int icePct = Math.round(iceFill() * 100f);
        if (auram != sentAuram || gelum != sentGelum || iceTier != sentIceTier
                || Math.abs(icePct - sentIcePct) >= 2 || (icePct == 0) != (sentIcePct == 0)
                || status != sentStatus || boilOff != sentBoilOff) {
            sentAuram = auram;
            sentGelum = gelum;
            sentIceTier = iceTier;
            sentIcePct = icePct;
            sentStatus = status;
            sentBoilOff = boilOff;
            lastSync = now;
            BlockEntities.sendUpdate(this);
        }
    }

    public static final int DATA_COUNT = 9;

    private int sourceTenths() {
        return (int) Math.clamp(Math.round(sourcePerSecond() * 10.0), 0L, Integer.MAX_VALUE);
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> auram;
                    case 1 -> gelum;
                    case 2 -> iceTier;
                    case 3 -> Math.round(iceFill() * 100f);
                    case 4 -> sourceTenths() & 0xFFFF;
                    case 5 -> status;
                    case 6 -> upgrades.count(UpgradeType.SPEED);
                    case 7 -> boilOff ? 1 : 0;
                    case 8 -> sourceTenths() >>> 16;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    // ----------------------------------------------------------------- client

    public void clientTick() {
        workPrev = work;
        phasePrev = phase;
        boolean active = clientStatus == STATUS_WORKING;
        work += ((active ? 1f : 0f) - work) * (active ? 0.08f : 0.04f);
        phase += 0.02f + (clientBoilOff ? 0.30f : 0.20f * work);
        if (level != null) {
            if (clientBoilOff && level.getRandom().nextInt(3) == 0) {
                double x = worldPosition.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.6;
                double z = worldPosition.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.6;
                level.addParticle(ParticleTypes.WITCH, x, worldPosition.getY() + 1.15, z, 0.0, 0.04, 0.0);
            } else if (active && level.getRandom().nextInt(5) == 0) {
                double x = worldPosition.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.55;
                double z = worldPosition.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.55;
                level.addParticle(ParticleTypes.SNOWFLAKE, x, worldPosition.getY() + 1.15, z, 0.0, 0.02, 0.0);
            }
        }
    }

    // ------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Auram", auram);
        tag.putInt("Gelum", gelum);
        tag.putInt("IceTier", iceTier);
        tag.putInt("IceCharge", iceCharge);
        tag.putInt("BoilTimer", boilTimer);
        tag.putBoolean("BoilOff", boilOff);
        tag.putDouble("ReactionProgress", reactionProgress);
        tag.putInt("SourceCarry", sourceCarry);
        tag.put("Upgrades", upgrades.createTag(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        auram = Math.max(0, Math.min(TANK_CAPACITY, tag.getInt("Auram")));
        gelum = Math.max(0, Math.min(TANK_CAPACITY, tag.getInt("Gelum")));
        iceTier = tag.contains("IceTier") ? Math.max(ICE_NONE, Math.min(ICE_BLUE, tag.getInt("IceTier"))) : ICE_NONE;
        iceCharge = Math.max(0, tag.getInt("IceCharge"));
        boilTimer = Math.max(0, tag.getInt("BoilTimer"));
        boilOff = tag.getBoolean("BoilOff");
        reactionProgress = Math.max(0.0, tag.getDouble("ReactionProgress"));
        sourceCarry = Math.max(0, tag.getInt("SourceCarry"));
        upgrades.fromTag(tag.getList("Upgrades", Tag.TAG_COMPOUND), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Auram", auram);
        tag.putInt("Gelum", gelum);
        tag.putInt("IceTier", iceTier);
        tag.putFloat("IceFill", iceFill());
        tag.putInt("Status", status);
        tag.putBoolean("BoilOff", boilOff);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientAuram = tag.getInt("Auram");
        clientGelum = tag.getInt("Gelum");
        clientIceTier = tag.contains("IceTier") ? tag.getInt("IceTier") : ICE_NONE;
        clientIceFill = tag.getFloat("IceFill");
        clientStatus = tag.contains("Status") ? tag.getInt("Status") : STATUS_NO_AURAM;
        clientBoilOff = tag.getBoolean("BoilOff");
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
