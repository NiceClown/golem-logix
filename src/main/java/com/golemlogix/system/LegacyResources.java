package com.golemlogix.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The mod moves things through slot handlers with a simulate flag (IItemHandler / IFluidHandler); since 1.21.9 NeoForge hands out
 * transactional resource handlers instead. Reading others' blocks: the official adapters. Offering ours: a resource handler over
 * the old one that snapshots the owner's state at the first change in a transaction and puts it back if the transaction is undone.
 */
@SuppressWarnings("removal")
public final class LegacyResources {
    private LegacyResources() {
    }

    // ---- reading other blocks

    @Nullable
    public static IItemHandler items(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<ItemResource> h = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        return h == null ? null : IItemHandler.of(h);
    }

    @Nullable
    public static IFluidHandler fluids(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<FluidResource> h = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return h == null ? null : IFluidHandler.of(h);
    }

    @Nullable
    public static IItemHandler items(@Nullable ResourceHandler<ItemResource> h) {
        return h == null ? null : IItemHandler.of(h);
    }

    @Nullable
    public static IFluidHandler fluids(@Nullable ResourceHandler<FluidResource> h) {
        return h == null ? null : IFluidHandler.of(h);
    }

    // ---- offering ours

    /** Our item handler as a resource handler; snapshot / restore copy and put back the owner's slots. */
    public static <S> ResourceHandler<ItemResource> offerItems(IItemHandler handler, Supplier<S> snapshot, Consumer<S> restore) {
        return new Items<>(handler, snapshot, restore);
    }

    /** Our fluid handler as a resource handler (tank by tank). */
    public static <S> ResourceHandler<FluidResource> offerFluids(IFluidHandler handler, Supplier<S> snapshot, Consumer<S> restore) {
        return new Fluids<>(handler, snapshot, restore);
    }

    private static final class Items<S> extends SnapshotJournal<S> implements ResourceHandler<ItemResource> {
        private final IItemHandler handler;
        private final Supplier<S> snapshot;
        private final Consumer<S> restore;

        Items(IItemHandler handler, Supplier<S> snapshot, Consumer<S> restore) {
            this.handler = handler;
            this.snapshot = snapshot;
            this.restore = restore;
        }

        @Override
        public int size() {
            return handler.getSlots();
        }

        @Override
        public ItemResource getResource(int index) {
            return ItemResource.of(handler.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getStackInSlot(index).getCount();
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int limit = handler.getSlotLimit(index);
            return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return handler.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0) {
                return 0;
            }
            ItemStack offer = resource.toStack(amount);
            ItemStack rest = handler.insertItem(index, offer.copy(), true);
            int fits = amount - rest.getCount();
            if (fits <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return fits - handler.insertItem(index, resource.toStack(fits), false).getCount();
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !resource.matches(handler.getStackInSlot(index))) {
                return 0;
            }
            int can = handler.extractItem(index, amount, true).getCount();
            if (can <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.extractItem(index, can, false).getCount();
        }

        @Override
        protected S createSnapshot() {
            return snapshot.get();
        }

        @Override
        protected void revertToSnapshot(S state) {
            restore.accept(state);
        }
    }

    private static final class Fluids<S> extends SnapshotJournal<S> implements ResourceHandler<FluidResource> {
        private final IFluidHandler handler;
        private final Supplier<S> snapshot;
        private final Consumer<S> restore;

        Fluids(IFluidHandler handler, Supplier<S> snapshot, Consumer<S> restore) {
            this.handler = handler;
            this.snapshot = snapshot;
            this.restore = restore;
        }

        @Override
        public int size() {
            return handler.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            return FluidResource.of(handler.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return handler.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return handler.getTankCapacity(index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return handler.isFluidValid(index, resource.toStack(1));
        }

        /** The old handlers fill as a whole, not tank by tank: only the first valid tank takes. */
        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            if (resource.isEmpty() || amount <= 0 || !handler.isFluidValid(index, resource.toStack(amount))) {
                return 0;
            }
            int fits = handler.fill(resource.toStack(amount), IFluidHandler.FluidAction.SIMULATE);
            if (fits <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.fill(resource.toStack(fits), IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            FluidStack in = handler.getFluidInTank(index);
            if (resource.isEmpty() || amount <= 0 || in.isEmpty() || !resource.matches(in)) {
                return 0;
            }
            int can = handler.drain(resource.toStack(amount), IFluidHandler.FluidAction.SIMULATE).getAmount();
            if (can <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return handler.drain(resource.toStack(can), IFluidHandler.FluidAction.EXECUTE).getAmount();
        }

        @Override
        protected S createSnapshot() {
            return snapshot.get();
        }

        @Override
        protected void revertToSnapshot(S state) {
            restore.accept(state);
        }
    }
}
