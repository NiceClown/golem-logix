package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * The item and fluid handlers of the block a bus or a periscope faces, looked up once and kept until NeoForge reports that they
 * changed (BlockCapabilityCache): buses and periscopes used to ask the world for them on every operation. Server only.
 * <p>
 * "Any side" (periscopes) falls back to the unsided handler and then to every side in turn, as the plain lookups did; those
 * extra caches are made only when the first ones find nothing.
 */
@SuppressWarnings("removal")
public final class TargetCaps {
    private final ServerLevel level;
    private final BlockPos pos;
    @Nullable
    private final Direction side;
    private final BooleanSupplier valid;
    private final Runnable onChange;
    private final BlockCapabilityCache<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>, @Nullable Direction> items;
    private final BlockCapabilityCache<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>, @Nullable Direction> fluids;
    @Nullable
    private BlockCapabilityCache<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource>, @Nullable Direction>[] itemsAny;
    @Nullable
    private BlockCapabilityCache<net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource>, @Nullable Direction>[] fluidsAny;

    private TargetCaps(ServerLevel level, BlockPos pos, @Nullable Direction side, BooleanSupplier valid, Runnable onChange) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.valid = valid;
        this.onChange = onChange;
        this.items = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, this.pos, side, valid, onChange);
        this.fluids = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, this.pos, side, valid, onChange);
    }

    /**
     * The caches for this target: the old ones if they are for the same block and side, new ones otherwise.
     *
     * @param valid    false once the owner is gone (NeoForge then drops the listener)
     * @param onChange called when a handler there appears, disappears or changes
     */
    public static TargetCaps of(@Nullable TargetCaps old, ServerLevel level, BlockPos pos, @Nullable Direction side,
                                BooleanSupplier valid, Runnable onChange) {
        if (old != null && old.level == level && old.pos.equals(pos) && old.side == side) {
            return old;
        }
        return new TargetCaps(level, pos, side, valid, onChange);
    }

    @Nullable
    public IItemHandler items() {
        return LegacyResources.items(items.getCapability());
    }

    @Nullable
    public IFluidHandler fluids() {
        return LegacyResources.fluids(fluids.getCapability());
    }

    /** The item handler on this side, or else the unsided one, or else the one on any side. */
    @Nullable
    public IItemHandler itemsAnySide() {
        var h = items.getCapability();
        if (h != null) {
            return LegacyResources.items(h);
        }
        if (itemsAny == null) {
            itemsAny = anyCaches(Capabilities.Item.BLOCK);
        }
        return LegacyResources.items(first(itemsAny));
    }

    /** The fluid handler on this side, or else the unsided one, or else the one on any side. */
    @Nullable
    public IFluidHandler fluidsAnySide() {
        var h = fluids.getCapability();
        if (h != null) {
            return LegacyResources.fluids(h);
        }
        if (fluidsAny == null) {
            fluidsAny = anyCaches(Capabilities.Fluid.BLOCK);
        }
        return LegacyResources.fluids(first(fluidsAny));
    }

    /** Caches for the unsided handler and for each of the six sides, in that order. */
    @SuppressWarnings("unchecked")
    private <T> BlockCapabilityCache<T, @Nullable Direction>[] anyCaches(BlockCapability<T, @Nullable Direction> cap) {
        BlockCapabilityCache<T, @Nullable Direction>[] out = new BlockCapabilityCache[7];
        out[0] = BlockCapabilityCache.create(cap, level, pos, null, valid, onChange);
        for (Direction d : Direction.values()) {
            out[1 + d.ordinal()] = BlockCapabilityCache.create(cap, level, pos, d, valid, onChange);
        }
        return out;
    }

    @Nullable
    private static <T> T first(BlockCapabilityCache<T, @Nullable Direction>[] caches) {
        for (BlockCapabilityCache<T, @Nullable Direction> c : caches) {
            T h = c.getCapability();
            if (h != null) {
                return h;
            }
        }
        return null;
    }
}
