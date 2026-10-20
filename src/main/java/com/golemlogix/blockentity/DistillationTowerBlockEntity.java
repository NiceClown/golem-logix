package com.golemlogix.blockentity;

import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.config.GolemConfig;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.init.ModTags;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The tower's work: blood from the tank is distilled into Source (GolemConfig: so many mB a second, so much Source per mB) as long
 * as a jar beside the tower has room. Twice a second; the fraction of Source that does not make a whole unit waits for the next
 * batch. From outside the tank only takes blood: nothing pumps it back out.
 */
public class DistillationTowerBlockEntity extends BlockEntity {
    public static final int TANK = 8000;
    private static final int PERIOD = 10;

    private final FluidTank tank = new FluidTank(TANK, fs -> fs.is(ModTags.CRYO_DISTILLABLE)) {
        @Override
        protected void onContentsChanged() {
            changed();
        }
    };
    /** What pipes and buckets see: filling only. */
    private final IFluidHandler input = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int t) {
            return TANK;
        }

        @Override
        public boolean isFluidValid(int t, FluidStack stack) {
            return tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
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
    private final com.golemlogix.upgrade.UpgradeContainer upgrades =
            new com.golemlogix.upgrade.UpgradeContainer(com.golemlogix.upgrade.UpgradeSpec.DISTILLATION, this);
    /** What the last batch ran into: 0 = no blood, 1 = working, 2 = no jar with room beside the tower (for the window). */
    private int status;
    private double carry;
    private boolean working;
    private long lastWork = -100;
    private boolean dirty;
    private long lastSync = -100;
    private int sentBlood = -1;
    private boolean clientWorkingSent;
    // client
    private int clientBlood;
    private boolean clientWorking;
    /** Client: how hard it works 0..1 (spins up and down smoothly), the blood level 0..1, the coil phase. */
    public float work, workPrev, blood, bloodPrev, phase, phasePrev;

    public DistillationTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISTILLATION_TOWER.get(), pos, state);
    }

    public IFluidHandler input() {
        return input;
    }

    /** What pipes and other mods see: the tank (filling only) as a NeoForge resource handler. */
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> inputResources =
            com.golemlogix.system.LegacyResources.offerFluids(input, () -> tank.getFluid().copy(), tank::setFluid);

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> inputResources() {
        return inputResources;
    }

    public FluidStack blood() {
        return tank.getFluid();
    }

    private void changed() {
        dirty = true;
        setChanged();
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
            int amount = tank.getFluidAmount();
            if (sentBlood < 0 || Math.abs(amount - sentBlood) >= TANK / 50 || (amount == 0) != (sentBlood == 0) || working != clientWorkingSent) {
                sentBlood = amount;
                clientWorkingSent = working;
                lastSync = now;
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /** One batch: as much blood as the rate allows, the tank holds and the jars around can take. */
    void distil(long now) {
        if (tank.isEmpty()) {
            status = 0;
            return;
        }
        double perMb = GolemConfig.DISTILL_SOURCE_PER_MB.get();
        int budget = Math.max(1, rate() * PERIOD / 20);
        var cells = DistillationTowerBlock.cells(worldPosition);
        int room = SourceHelper.fillAround(level, cells, Integer.MAX_VALUE, true);
        if (room <= 0) {
            status = 2;
            return;
        }
        int byRoom = (int) Math.floor((room - carry) / perMb + 1e-9);
        int mb = Math.min(Math.min(budget, tank.getFluidAmount()), Math.max(0, byRoom));
        if (mb <= 0) {
            return;
        }
        tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        double made = mb * perMb + carry;
        int give = (int) Math.floor(made + 1e-9);
        carry = made - give;
        SourceHelper.fillAround(level, cells, give, false);
        lastWork = now;
        status = 1;
        if (now % 40 < PERIOD) {
            level.playSound(null, worldPosition.above(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.35f, 0.6f);
        }
    }

    public com.golemlogix.upgrade.UpgradeContainer getUpgrades() {
        return upgrades;
    }

    /** Blood a second: the base rate and the co-processors' share. */
    public int rate() {
        return GolemConfig.DISTILL_RATE.get() + GolemConfig.DISTILL_COPROCESSOR_BONUS.get() * upgrades.count(com.golemlogix.upgrade.UpgradeType.SPEED);
    }

    /** The window's numbers: blood mB, mB a second, Source a second x10, status, co-processors. */
    public net.minecraft.world.inventory.ContainerData menuData() {
        return new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> tank.getFluidAmount();
                    case 1 -> rate();
                    case 2 -> (int) Math.round(rate() * GolemConfig.DISTILL_SOURCE_PER_MB.get() * 10);
                    case 3 -> tank.isEmpty() ? 0 : status;
                    case 4 -> upgrades.count(com.golemlogix.upgrade.UpgradeType.SPEED);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 5;
            }
        };
    }

    public void dropContents() {
        if (level != null) {
            net.minecraft.world.Containers.dropContents(level, worldPosition, upgrades);
        }
    }

    public Component statusLine() {
        FluidStack f = tank.getFluid();
        Component name = f.isEmpty() ? Component.translatable("block.golem_logix.cryo_distillation_tower.empty") : f.getHoverName();
        return Component.translatable("message.golem_logix.distillation_tower", name, tank.getFluidAmount(), TANK);
    }

    public void clientTick() {
        workPrev = work;
        bloodPrev = blood;
        phasePrev = phase;
        work += ((clientWorking ? 1f : 0f) - work) * (clientWorking ? 0.06f : 0.03f);
        blood += (clientBlood / (float) TANK - blood) * 0.1f;
        phase += 0.02f + 0.18f * work;
        if (clientWorking && level.getRandom().nextInt(4) == 0) {
            // frost breathing out of the louvres, a wisp of steam from the outlet
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
        tag.put("Tank", com.golemlogix.system.NbtBridge.saveTank(tank, registries));
        tag.putDouble("Carry", carry);
        tag.put("Upgrades", upgrades.createTag(registries));
    }

    protected void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        com.golemlogix.system.NbtBridge.loadTank(tank, registries, tag.getCompoundOrEmpty("Tank"));
        carry = tag.getDoubleOr("Carry", 0);
        upgrades.fromTag(tag.getListOrEmpty("Upgrades"), registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Blood", tank.getFluidAmount());
        tag.putBoolean("Working", working);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientBlood = tag.getIntOr("Blood", 0);
        clientWorking = tag.getBooleanOr("Working", false);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- 1.21.6+: the game's ValueInput/ValueOutput over the CompoundTag code above

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

    @Override
    public void handleUpdateTag(net.minecraft.world.level.storage.ValueInput input) {
        readUpdateTag(com.golemlogix.system.NbtBridge.read(input), input.lookup());
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.world.level.storage.ValueInput input) {
        handleUpdateTag(input);
    }

    /** Since 1.21.5 the block's removal side effects run from here, while this entity and its contents are still in place. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && state.getBlock() instanceof com.golemlogix.blocks.RemovalAware r) {
            r.onRemoved(state, level, pos);
        }
    }
}
