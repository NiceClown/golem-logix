package com.golemlogix.blockentity;

import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModTags;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.GolemNode;
import com.golemlogix.system.NbtBridge;
import com.golemlogix.system.SourceHelper;
import com.golemlogix.system.Transfers;
import com.golemlogix.upgrade.UpgradeContainer;
import com.golemlogix.upgrade.UpgradeSpec;
import com.golemlogix.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Cryo-Distillation Tower: distils blood (any fluid in {@link ModTags#CRYO_DISTILLABLE}) into Source twice per second
 * whenever adjacent or network-joined Cryo-Source Jars have room. Fractional Source carries over across batches.
 */
public class DistillationTowerBlockEntity extends BlockEntity {
    public static final int TANK = 8000;
    public static final int TANK_PER_FOUR = 2000;
    private static final int PERIOD = 10;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, TANK) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.toStack(1).is(ModTags.CRYO_DISTILLABLE);
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            return DistillationTowerBlockEntity.this.capacity();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    };
    private final UpgradeContainer upgrades = new UpgradeContainer(UpgradeSpec.DISTILLATION, this);
    private final ResourceHandler<FluidResource> inputResources = Transfers.insertOnly(tank);
    private final List<BlockPos> cells;

    /** Status: 0 = no blood, 1 = working, 2 = no jar with room. */
    private int status;
    private double carry;
    private boolean working;
    private long lastWork = -100;
    private boolean dirty;
    private long lastSync = -100;
    private int sentBlood = -1;
    private int sentCapacity = -1;
    private boolean clientWorkingSent;

    // client
    private int clientBlood;
    private int clientCapacity = TANK;
    private boolean clientWorking;
    public float work, workPrev, blood, bloodPrev, phase, phasePrev;

    public DistillationTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLATION_TOWER.get(), pos, state);
        this.cells = List.copyOf(DistillationTowerBlock.cells(pos));
    }

    public ResourceHandler<FluidResource> inputResources() {
        return inputResources;
    }

    public FluidStack blood() {
        return Transfers.getFluid(tank, 0);
    }

    @Override
    public void setChanged() {
        dirty = true;
        super.setChanged();
    }

    public void serverTick() {
        long now = level.getGameTime();
        if ((now + worldPosition.asLong()) % PERIOD == 0) {
            distil(now);
        }
        boolean nowWorking = now - lastWork < PERIOD * 3;
        if (nowWorking != working) {
            working = nowWorking;
            dirty = true;
        }
        if (dirty && now - lastSync >= 10) {
            dirty = false;
            int amount = tank.getAmountAsInt(0);
            int cap = capacity();
            if (sentBlood < 0 || cap != sentCapacity || Math.abs(amount - sentBlood) >= cap / 50
                    || (amount == 0) != (sentBlood == 0) || working != clientWorkingSent) {
                sentBlood = amount;
                sentCapacity = cap;
                clientWorkingSent = working;
                lastSync = now;
                BlockEntities.sendUpdate(this);
            }
        }
    }

    /** Distils one batch of blood into adjacent and network-joined jars, delivering leftover carry Source first. */
    void distil(long now) {
        int haveBlood = tank.getAmountAsInt(0);
        if (carry < 1 && haveBlood <= 0) {
            status = 0;
            return;
        }
        List<CryoJarBlockEntity> onNetwork = networkJars();
        List<CryoJarBlockEntity> joined = farJars(onNetwork);
        // deliver carried Source first, blood or no blood
        if (carry >= 1) {
            int owed = (int) Math.floor(carry + 1e-9);
            int put = SourceHelper.fillAround(level, cells, owed, false);
            for (CryoJarBlockEntity jar : joined) {
                put += jar.fill(owed - put, false);
            }
            if (put > 0) {
                carry = Math.max(0.0, carry - put);
                setChanged();
            }
            if (carry >= 1) {
                status = 2;
                return;
            }
        }
        if (haveBlood <= 0) {
            status = 0;
            return;
        }
        double perMb = GolemConfig.DISTILL_SOURCE_PER_MB.get();
        int budget = Math.max(1, rate() * PERIOD / 20);

        long roomAll = SourceHelper.fillAround(level, cells, Integer.MAX_VALUE, true);
        for (CryoJarBlockEntity jar : joined) {
            roomAll += jar.fill(Integer.MAX_VALUE, true);
        }
        int room = (int) Math.min(roomAll, Integer.MAX_VALUE);
        if (room <= 0) {
            status = 2;
            return;
        }
        int byRoom = (int) Math.floor((room - carry) / perMb + 1e-9);
        int mb = Math.min(Math.min(budget, haveBlood), Math.max(0, byRoom));
        if (mb <= 0) {
            status = 2;
            return;
        }
        int left = haveBlood - mb;
        tank.set(0, left <= 0 ? FluidResource.EMPTY : tank.getResource(0), left);
        double made = mb * perMb + carry;
        int give = (int) Math.floor(made + 1e-9);
        carry = Math.max(0.0, made - give);
        give -= SourceHelper.fillAround(level, cells, give, false);
        for (CryoJarBlockEntity jar : joined) {
            give -= jar.fill(give, false);
        }
        // any undelivered Source waits in carry for the next batch instead of being lost
        carry += Math.max(0, give);
        lastWork = now;
        status = 1;
        if (now % 40 < PERIOD) {
            level.playSound(null, worldPosition.above(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.35f, 0.6f);
        }
    }

    /** Joined jars across every network touching this tower (including unpowered networks so they can recover). */
    private List<CryoJarBlockEntity> networkJars() {
        Set<GolemNetwork> seenNets = null;
        List<CryoJarBlockEntity> all = null;
        for (BlockPos cell : cells) {
            for (Direction d : BlockEntities.DIRECTIONS) {
                BlockPos p = cell.relative(d);
                if (cells.contains(p) || !level.isLoaded(p) || !(level.getBlockState(p).getBlock() instanceof GolemNode)) {
                    continue;
                }
                GolemNetwork net = GolemNetwork.get(level, p);
                if (seenNets == null) {
                    seenNets = Collections.newSetFromMap(new IdentityHashMap<>());
                }
                if (seenNets.add(net)) {
                    List<CryoJarBlockEntity> jars = net.joinedJars();
                    if (!jars.isEmpty()) {
                        if (all == null) {
                            all = new ArrayList<>(jars);
                        } else {
                            all.addAll(jars);
                        }
                    }
                }
            }
        }
        return all == null ? List.of() : all;
    }

    /** Network jars not already directly adjacent to the tower (adjacent ones are filled by {@link SourceHelper#fillAround}). */
    private List<CryoJarBlockEntity> farJars(Collection<CryoJarBlockEntity> onNetwork) {
        if (onNetwork.isEmpty()) {
            return List.of();
        }
        Set<Object> near = new HashSet<>();
        for (BlockPos cell : cells) {
            for (Direction d : BlockEntities.DIRECTIONS) {
                BlockPos p = cell.relative(d);
                if (!cells.contains(p) && level.isLoaded(p)) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        near.add(SourceHelper.owner(be));
                    }
                }
            }
        }
        List<CryoJarBlockEntity> far = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        for (CryoJarBlockEntity jar : onNetwork) {
            CryoJarBlockEntity owner = jar.owner();
            if (owner != null && !near.contains(owner) && seen.add(owner)) {
                far.add(owner);
            }
        }
        return far;
    }

    public UpgradeContainer getUpgrades() {
        return upgrades;
    }

    public int capacity() {
        int coprocessors = upgrades.count(UpgradeType.SPEED);
        return TANK + TANK_PER_FOUR * (Math.max(0, coprocessors - 1) / 4);
    }

    public int rate() {
        return GolemConfig.DISTILL_RATE.get() + GolemConfig.DISTILL_COPROCESSOR_BONUS.get() * upgrades.count(UpgradeType.SPEED);
    }

    public double sourcePerSecond() {
        return rate() * GolemConfig.DISTILL_SOURCE_PER_MB.get();
    }

    public int status() {
        return tank.getAmountAsInt(0) <= 0 ? 0 : status;
    }

    public ContainerData menuData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> tank.getAmountAsInt(0);
                    case 1 -> rate();
                    case 2 -> (int) Math.round(sourcePerSecond() * 10);
                    case 3 -> status();
                    case 4 -> upgrades.count(UpgradeType.SPEED);
                    case 5 -> capacity();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    public void dropContents() {
        BlockEntities.dropContainers(level, worldPosition, upgrades);
    }

    public Component statusLine() {
        FluidStack f = blood();
        Component name = f.isEmpty() ? Component.translatable("block.golem_logix.cryo_distillation_tower.empty") : f.getHoverName();
        return Component.translatable("message.golem_logix.distillation_tower", name, tank.getAmountAsInt(0), capacity());
    }

    public void clientTick() {
        workPrev = work;
        bloodPrev = blood;
        phasePrev = phase;
        work += ((clientWorking ? 1f : 0f) - work) * (clientWorking ? 0.06f : 0.03f);
        blood += (clientBlood / (float) Math.max(1, clientCapacity) - blood) * 0.1f;
        phase += 0.02f + 0.18f * work;
        if (clientWorking && level.getRandom().nextInt(4) == 0) {
            double x = worldPosition.getX() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.9;
            double z = worldPosition.getZ() + 0.5 + (level.getRandom().nextDouble() - 0.5) * 0.9;
            level.addParticle(ParticleTypes.SNOWFLAKE, x, worldPosition.getY() + 2.55, z, 0, 0.01, 0);
            if (level.getRandom().nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 3.05,
                        worldPosition.getZ() + 0.5, 0, 0.03, 0);
            }
        }
    }

    protected void saveTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Tank", NbtBridge.saveTank(tank, registries));
        tag.putDouble("Carry", carry);
        tag.put("Upgrades", upgrades.createTag(registries));
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        NbtBridge.loadTank(tank, registries, tag.getCompoundOrEmpty("Tank"));
        carry = tag.getDoubleOr("Carry", 0);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Blood", tank.getAmountAsInt(0));
        tag.putInt("Capacity", capacity());
        tag.putBoolean("Working", working);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientBlood = tag.getIntOr("Blood", 0);
        clientCapacity = tag.getIntOr("Capacity", TANK);
        clientWorking = tag.getBooleanOr("Working", false);
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
