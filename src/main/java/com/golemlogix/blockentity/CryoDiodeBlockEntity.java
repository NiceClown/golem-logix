package com.golemlogix.blockentity;

import com.golemlogix.blocks.CryoDiodeBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public class CryoDiodeBlockEntity extends BlockEntity {
    /** Per-diode cycle guard: allows daisy-chained diodes while breaking loops cleanly. */
    private static final ThreadLocal<Set<CryoDiodeBlockEntity>> VISITING =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    public CryoDiodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_DIODE.get(), pos, state);
    }

    /** When Source last flowed through (game time; synced to clients at most once a second): the handwheel turns while it flows. */
    private long lastFlow = -1000;
    private long lastSync = -1000;
    /** Client: the wheel's angle (degrees) and the game time it was last advanced. */
    public float wheelAngle;
    public long wheelTick;

    public boolean flowing(long now) {
        return now - lastFlow < 50;
    }

    private void flowed() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        lastFlow = now;
        if (!level.isClientSide() && now - lastSync >= 20) {
            lastSync = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("LastFlow", lastFlow);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LastFlow")) {
            lastFlow = tag.getLong("LastFlow");
        }
    }

    public Direction getFacing() {
        return getBlockState().getValue(CryoDiodeBlock.FACING);
    }

    @Nullable
    private SourceHelper.Supplier directInlet(BlockPos inletPos, Direction facing) {
        BlockEntity inletBe = level.getBlockEntity(inletPos);
        if (inletBe instanceof CryoDiodeBlockEntity diode) {
            return diode.asSupplier(facing);
        }
        return inletBe != null ? SourceHelper.asSupplier(inletBe) : null;
    }

    @Nullable
    private GolemNetwork inletNetwork(BlockPos inletPos) {
        Block block = level.getBlockState(inletPos).getBlock();
        if (!(block instanceof com.golemlogix.system.GolemNode) || block instanceof CryoDiodeBlock) {
            return null;
        }
        return GolemNetwork.get(level, inletPos);
    }

    private final SourceHelper.Supplier outletSupplier = new SourceHelper.Supplier() {
        @Override
        public void collectLeafSuppliers(Set<SourceHelper.Supplier> out) {
            if (isRemoved() || level == null) return;
            Set<CryoDiodeBlockEntity> visiting = VISITING.get();
            if (!visiting.add(CryoDiodeBlockEntity.this)) return;
            try {
                Direction facing = getFacing();
                BlockPos inletPos = worldPosition.relative(facing.getOpposite());
                if (!level.isLoaded(inletPos)) return;
                SourceHelper.Supplier direct = directInlet(inletPos, facing);
                if (direct != null) {
                    direct.collectLeafSuppliers(out);
                    return;
                }
                GolemNetwork inletNet = inletNetwork(inletPos);
                if (inletNet != null) {
                    for (SourceHelper.Supplier s : inletNet.sources()) {
                        s.collectLeafSuppliers(out);
                    }
                }
            } finally {
                visiting.remove(CryoDiodeBlockEntity.this);
            }
        }

        @Override
        public boolean infinite() {
            if (isRemoved() || level == null) return false;
            Set<CryoDiodeBlockEntity> visiting = VISITING.get();
            if (!visiting.add(CryoDiodeBlockEntity.this)) return false;
            try {
                Direction facing = getFacing();
                BlockPos inletPos = worldPosition.relative(facing.getOpposite());
                if (!level.isLoaded(inletPos)) return false;
                SourceHelper.Supplier direct = directInlet(inletPos, facing);
                if (direct != null) return direct.infinite();
                GolemNetwork inletNet = inletNetwork(inletPos);
                return inletNet != null && inletNet.hasInfiniteSource();
            } finally {
                visiting.remove(CryoDiodeBlockEntity.this);
            }
        }

        @Override
        public int available() {
            if (isRemoved() || level == null) return 0;
            Set<CryoDiodeBlockEntity> visiting = VISITING.get();
            if (!visiting.add(CryoDiodeBlockEntity.this)) return 0;
            try {
                Direction facing = getFacing();
                BlockPos inletPos = worldPosition.relative(facing.getOpposite());
                if (!level.isLoaded(inletPos)) return 0;
                SourceHelper.Supplier direct = directInlet(inletPos, facing);
                if (direct != null) return direct.available();
                GolemNetwork inletNet = inletNetwork(inletPos);
                if (inletNet == null) return 0;
                if (inletNet.hasInfiniteSource()) return Integer.MAX_VALUE;
                Set<SourceHelper.Supplier> leaves = new LinkedHashSet<>();
                for (SourceHelper.Supplier s : inletNet.sources()) {
                    s.collectLeafSuppliers(leaves);
                }
                long total = 0;
                for (SourceHelper.Supplier s : leaves) {
                    total += s.available();
                }
                return (int) Math.min(total, Integer.MAX_VALUE);
            } finally {
                visiting.remove(CryoDiodeBlockEntity.this);
            }
        }

        @Override
        public int drain(int amount) {
            return drainUnique(amount, new HashSet<>());
        }

        @Override
        public int drainUnique(int amount, Set<SourceHelper.Supplier> seen) {
            if (isRemoved() || level == null || amount <= 0) return 0;
            Set<CryoDiodeBlockEntity> visiting = VISITING.get();
            if (!visiting.add(CryoDiodeBlockEntity.this)) return 0;
            try {
                Direction facing = getFacing();
                BlockPos inletPos = worldPosition.relative(facing.getOpposite());
                if (!level.isLoaded(inletPos)) return 0;
                SourceHelper.Supplier direct = directInlet(inletPos, facing);
                if (direct != null) {
                    int got = direct.drainUnique(amount, seen);
                    if (got > 0) flowed();
                    return got;
                }
                GolemNetwork inletNet = inletNetwork(inletPos);
                if (inletNet == null) return 0;
                if (inletNet.hasInfiniteSource()) {
                    flowed();
                    return amount;
                }
                int taken = 0;
                for (SourceHelper.Supplier s : inletNet.sources()) {
                    taken += s.drainUnique(amount - taken, seen);
                    if (taken >= amount) break;
                }
                if (taken > 0) flowed();
                return taken;
            } finally {
                visiting.remove(CryoDiodeBlockEntity.this);
            }
        }
    };

    /**
     * Supplies Source to an adjacent node if that node is on the diode's OUTLET side.
     * @param fromDiodeToNode The direction from this diode to the querying node.
     */
    @Nullable
    public SourceHelper.Supplier asSupplier(Direction fromDiodeToNode) {
        if (fromDiodeToNode != getFacing()) {
            return null; // Only the outlet side can draw Source!
        }
        return outletSupplier;
    }
}
