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
            return;
        }
        double perMb = GolemConfig.DISTILL_SOURCE_PER_MB.get();
        int budget = Math.max(1, GolemConfig.DISTILL_RATE.get() * PERIOD / 20);
        var cells = DistillationTowerBlock.cells(worldPosition);
        int room = SourceHelper.fillAround(level, cells, Integer.MAX_VALUE, true);
        if (room <= 0) {
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
        if (now % 40 < PERIOD) {
            level.playSound(null, worldPosition.above(), SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.35f, 0.6f);
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
        if (clientWorking && level.random.nextInt(4) == 0) {
            // frost breathing out of the louvres, a wisp of steam from the outlet
            double x = worldPosition.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.9;
            double z = worldPosition.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.9;
            level.addParticle(ParticleTypes.SNOWFLAKE, x, worldPosition.getY() + 2.55, z, 0, 0.01, 0);
            if (level.random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 3.05,
                        worldPosition.getZ() + 0.5, 0, 0.03, 0);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putDouble("Carry", carry);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        carry = tag.getDouble("Carry");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Blood", tank.getFluidAmount());
        tag.putBoolean("Working", working);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        clientBlood = tag.getInt("Blood");
        clientWorking = tag.getBoolean("Working");
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
