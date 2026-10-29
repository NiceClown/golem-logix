package com.golemlogix.blockentity;

import com.golemlogix.blocks.CryoDiodeBlock;
import com.golemlogix.init.ModBlockEntities;
import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.SourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CryoDiodeBlockEntity extends BlockEntity {
    private static final ThreadLocal<Boolean> DRAIN_LOCK = ThreadLocal.withInitial(() -> false);

    public CryoDiodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRYO_DIODE.get(), pos, state);
    }

    public Direction getFacing() {
        return getBlockState().getValue(CryoDiodeBlock.FACING);
    }

    /**
     * Supplies Source to an adjacent node if that node is on the diode's OUTLET side.
     * @param fromDiodeToNode The direction from this diode to the querying node.
     */
    @Nullable
    public SourceHelper.Supplier asSupplier(Direction fromDiodeToNode) {
        if (fromDiodeToNode != getFacing()) {
            return null; // Only the outlet side can draw Source!
        }
        return new SourceHelper.Supplier() {
            @Override
            public boolean infinite() {
                if (DRAIN_LOCK.get() || level == null) return false;
                DRAIN_LOCK.set(true);
                try {
                    BlockPos inletPos = worldPosition.relative(getFacing().getOpposite());
                    if (!level.isLoaded(inletPos)) return false;
                    BlockEntity inletBe = level.getBlockEntity(inletPos);
                    SourceHelper.Supplier direct = inletBe != null ? SourceHelper.asSupplier(inletBe) : null;
                    if (direct != null) return direct.infinite();
                    GolemNetwork inletNet = GolemNetwork.get(level, inletPos);
                    return inletNet != null && inletNet.hasInfiniteSource();
                } finally {
                    DRAIN_LOCK.set(false);
                }
            }

            @Override
            public int available() {
                if (DRAIN_LOCK.get() || level == null) return 0;
                DRAIN_LOCK.set(true);
                try {
                    BlockPos inletPos = worldPosition.relative(getFacing().getOpposite());
                    if (!level.isLoaded(inletPos)) return 0;
                    BlockEntity inletBe = level.getBlockEntity(inletPos);
                    SourceHelper.Supplier direct = inletBe != null ? SourceHelper.asSupplier(inletBe) : null;
                    if (direct != null) return direct.available();
                    GolemNetwork inletNet = GolemNetwork.get(level, inletPos);
                    if (inletNet == null) return 0;
                    if (inletNet.hasInfiniteSource()) return Integer.MAX_VALUE;
                    int total = 0;
                    for (SourceHelper.Supplier s : inletNet.sources()) {
                        total += s.available();
                    }
                    return total;
                } finally {
                    DRAIN_LOCK.set(false);
                }
            }

            @Override
            public int drain(int amount) {
                if (DRAIN_LOCK.get() || level == null || amount <= 0) return 0;
                DRAIN_LOCK.set(true);
                try {
                    BlockPos inletPos = worldPosition.relative(getFacing().getOpposite());
                    if (!level.isLoaded(inletPos)) return 0;
                    BlockEntity inletBe = level.getBlockEntity(inletPos);
                    SourceHelper.Supplier direct = inletBe != null ? SourceHelper.asSupplier(inletBe) : null;
                    if (direct != null) return direct.drain(amount);
                    GolemNetwork inletNet = GolemNetwork.get(level, inletPos);
                    if (inletNet == null) return 0;
                    if (inletNet.hasInfiniteSource()) return amount;
                    int taken = 0;
                    for (SourceHelper.Supplier s : inletNet.sources()) {
                        int d = s.drain(amount - taken);
                        taken += d;
                        if (taken >= amount) break;
                    }
                    return taken;
                } finally {
                    DRAIN_LOCK.set(false);
                }
            }
        };
    }
}
