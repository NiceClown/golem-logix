package com.golemlogix.blockentity;

import com.golemlogix.config.GolemConfig;
import com.golemlogix.crafting.CraftingCpu;
import com.golemlogix.crafting.CraftingJob;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Recipe Monitor: once a second counts active autocraft jobs and detects stalled jobs so the porthole cards
 * and roof warning lamp reflect network crafting status.
 */
public class RecipeMonitorBlockEntity extends BlockEntity {
    private static final LampWalker.GoalPicker ROOF_GOAL = RecipeMonitorBlockEntity::pickRoofGoal;

    private int jobs = 0;
    private boolean online = false;
    private boolean fault = false;
    /** Client: smoothed card count shown swirling in the porthole (0..6). */
    public float shownCards = 0f;
    public float shownCardsPrev = 0f;
    /** Client: copper lamplighter golem wandering the roof when idle. */
    public final LampWalker walker = new LampWalker(8f, 8f);

    public RecipeMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECIPE_MONITOR.get(), pos, state);
    }

    public int jobs() {
        return jobs;
    }

    public boolean online() {
        return online;
    }

    public boolean fault() {
        return fault;
    }

    public boolean ticksBlocked() {
        return fault() && GolemConfig.MONITOR_BLOCKED_TICK.get();
    }

    /** Lamp signal: 0 = idle (purple), 1 = working (green), 2 = fault or offline (red). */
    public int lampState() {
        if (!online || fault) {
            return 2;
        }
        return jobs > 0 ? 1 : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RecipeMonitorBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        boolean on = net.accessError(pos) == null;
        int n = 0;
        boolean hasFault = false;
        if (on) {
            for (CraftingCpu cpu : net.cpus()) {
                if (!cpu.isRemoved() && !cpu.isIdle()) {
                    n++;
                    CraftingJob job = cpu.getJob();
                    if (job != null) {
                        CraftingJob.Status st = job.status();
                        hasFault |= st == CraftingJob.Status.NO_PROVIDER
                                || st == CraftingJob.Status.NO_MACHINE
                                || st == CraftingJob.Status.NO_SOURCE
                                || st == CraftingJob.Status.INVALID_PATTERN;
                    }
                }
            }
        }
        if (n != be.jobs || on != be.online || hasFault != be.fault) {
            be.jobs = n;
            be.online = on;
            be.fault = hasFault;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static void pickRoofGoal(RandomSource r, float[] out) {
        float gx = 4.5f + r.nextFloat() * 7f;
        float gz = 4.5f + r.nextFloat() * 7f;
        if (Math.abs(gx - 8f) < 2.2f && Math.abs(gz - 8f) < 2.2f) {
            gz = gz < 8f ? 4.5f : 11.5f;
        }
        out[0] = gx;
        out[1] = gz;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RecipeMonitorBlockEntity be) {
        be.shownCardsPrev = be.shownCards;
        float target = Math.min(6, be.jobs);
        be.shownCards += (target - be.shownCards) * 0.08f;
        be.walker.tick(be.lampState() == 0, 8f, 8f, level.getRandom(), ROOF_GOAL);
        if (be.ticksBlocked() && level.getGameTime() % 20 == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.25f, 2.0f, false);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Jobs", jobs);
        tag.putBoolean("Online", online);
        tag.putBoolean("Fault", fault);
        return tag;
    }

    public void readUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        jobs = tag.getIntOr("Jobs", 0);
        online = tag.getBooleanOr("Online", false);
        fault = tag.getBooleanOr("Fault", false);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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
