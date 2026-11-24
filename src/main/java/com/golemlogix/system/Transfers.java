package com.golemlogix.system;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * Small helpers on top of NeoForge 26.1's {@link ResourceHandler} and {@link Transaction} APIs (replacing the removed-deprecated
 * {@code IItemHandler} / {@code IFluidHandler} / old {@code FluidUtil} layer).
 */
public final class Transfers {
    private Transfers() {
    }

    /** Wraps a handler so automation can insert into it but never extract. */
    public static <T extends Resource> ResourceHandler<T> insertOnly(ResourceHandler<T> delegate) {
        return new DelegatingResourceHandler<>(delegate) {
            @Override
            public int extract(int index, T resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return 0;
            }

            @Override
            public int extract(T resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return 0;
            }
        };
    }

    /** Wraps a handler so automation can extract from it but never insert. */
    public static <T extends Resource> ResourceHandler<T> extractOnly(ResourceHandler<T> delegate) {
        return new DelegatingResourceHandler<>(delegate) {
            @Override
            public boolean isValid(int index, T resource) {
                return false;
            }

            @Override
            public int insert(int index, T resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return 0;
            }

            @Override
            public int insert(T resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext tx) {
                TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
                return 0;
            }
        };
    }

    public static FluidStack fluidOf(ItemStack stack) {
        return stack.isEmpty() ? FluidStack.EMPTY : FluidUtil.getFirstStackContained(stack);
    }

    public static boolean hasFluidHandler(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(Capabilities.Fluid.ITEM, ItemAccess.forStack(stack)) != null;
    }

    public static ItemStack filledBucket(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (fluid.getComponentsPatch().isEmpty()) {
            if (fluid.is(Fluids.WATER)) {
                return new ItemStack(Items.WATER_BUCKET);
            }
            if (fluid.is(Fluids.LAVA)) {
                return new ItemStack(Items.LAVA_BUCKET);
            }
        }
        return fluid.getFluidType().getBucket(fluid);
    }

    public static FluidStack getFluid(FluidStacksResourceHandler tank, int index) {
        int amount = tank.getAmountAsInt(index);
        return amount <= 0 ? FluidStack.EMPTY : tank.getResource(index).toStack(amount);
    }

    public static ItemStack insertItem(@Nullable ResourceHandler<ItemResource> handler, ItemStack stack, boolean stacking, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        ItemResource res = ItemResource.of(stack);
        try (Transaction tx = Transaction.openRoot()) {
            int moved = stacking
                    ? ResourceHandlerUtil.insertStacking(handler, res, stack.getCount(), tx)
                    : handler.insert(res, stack.getCount(), tx);
            if (moved > 0 && !simulate) {
                tx.commit();
            }
            return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }
    }

    public static int insertFluid(@Nullable ResourceHandler<FluidResource> handler, FluidStack fluid, boolean simulate) {
        if (handler == null || fluid.isEmpty()) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.insert(FluidResource.of(fluid), fluid.getAmount(), tx);
            if (moved > 0 && !simulate) {
                tx.commit();
            }
            return moved;
        }
    }

    public static FluidStack extractFluid(@Nullable ResourceHandler<FluidResource> handler, FluidStack template, boolean simulate) {
        if (handler == null || template.isEmpty()) {
            return FluidStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.extract(FluidResource.of(template), template.getAmount(), tx);
            if (got <= 0) {
                return FluidStack.EMPTY;
            }
            if (!simulate) {
                tx.commit();
            }
            return template.copyWithAmount(got);
        }
    }

    public static FluidStack extractAnyFluid(@Nullable ResourceHandler<FluidResource> handler, int maxAmount, boolean simulate) {
        if (handler == null || maxAmount <= 0) {
            return FluidStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            var got = ResourceHandlerUtil.extractFirst(handler, r -> !r.isEmpty(), maxAmount, tx);
            if (got == null || got.isEmpty()) {
                return FluidStack.EMPTY;
            }
            if (!simulate) {
                tx.commit();
            }
            return got.resource().toStack(got.amount());
        }
    }
}
