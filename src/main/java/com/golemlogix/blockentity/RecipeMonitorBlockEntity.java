package com.golemlogix.blockentity;

import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Recipe Monitor: once a second it counts the network's running jobs (how many punch cards swirl in the porthole).
 */
public class RecipeMonitorBlockEntity extends BlockEntity {
    private int jobs = 0;
    private boolean online = false;
    /** Client: cards appear and disappear smoothly (0..number of jobs). */
    public float shownCards = 0f;
    public float shownCardsPrev = 0f;

    public RecipeMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECIPE_MONITOR.get(), pos, state);
    }

    public int jobs() {
        return jobs;
    }

    public boolean online() {
        return online;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RecipeMonitorBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        GolemNetwork net = GolemNetwork.get(level, pos);
        boolean on = net.accessError(pos) == null;
        int n = 0;
        boolean fault = false;
        if (on) {
            for (com.golemlogix.crafting.CraftingCpu cpu : net.cpus()) {
                if (!cpu.isRemoved() && !cpu.isIdle()) {
                    n++;
                    var job = cpu.getJob();
                    if (job != null) {
                        var st = job.status();
                        fault |= st == com.golemlogix.crafting.CraftingJob.Status.NO_PROVIDER
                                || st == com.golemlogix.crafting.CraftingJob.Status.NO_SOURCE
                                || st == com.golemlogix.crafting.CraftingJob.Status.INVALID_PATTERN;
                    }
                }
            }
        }
        if (n != be.jobs || on != be.online || fault != be.fault) {
            be.jobs = n;
            be.online = on;
            be.fault = fault;
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /** Some job is stuck (no provider, no Source or a wrong recipe): the lamp glows red. */
    private boolean fault = false;

    public boolean fault() {
        return fault;
    }

    /** Lamp signal: 0 = idle (purple), 1 = working (blinks green), 2 = failure or no connection (red). */
    public int lampState() {
        if (!online || fault) {
            return 2;
        }
        return jobs > 0 ? 1 : 0;
    }

    /** Client: copper lamplighter golem on the roof (wanders over the 4.5..11.5 area when idle, around the middle). */
    public final LampWalker walker = new LampWalker(8f, 8f);

    private void walk(Level level) {
        var r = level.random;
        walker.tick(lampState() == 0, 8f, 8f, r, () -> {
            float gx = 4.5f + r.nextFloat() * 7f, gz = 4.5f + r.nextFloat() * 7f;
            if (Math.abs(gx - 8f) < 2.2f && Math.abs(gz - 8f) < 2.2f) {
                gz = gz < 8f ? 4.5f : 11.5f;
            }
            return new float[]{gx, gz};
        });
    }
    public static void clientTick(Level level, BlockPos pos, BlockState state, RecipeMonitorBlockEntity be) {
        be.shownCardsPrev = be.shownCards;
        float target = Math.min(6, be.jobs);
        be.shownCards += (target - be.shownCards) * 0.08f;
        be.walk(level);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("Jobs", jobs);
        tag.putBoolean("Online", online);
        tag.putBoolean("Fault", fault);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        jobs = tag.getInt("Jobs");
        online = tag.getBoolean("Online");
        fault = tag.getBoolean("Fault");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A block update carries the same tag as chunk loading (by default loadAdditional would read it and lose the counter). */
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        handleUpdateTag(pkt.getTag(), lookupProvider);
    }
}
