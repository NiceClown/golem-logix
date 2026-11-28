package com.golemlogix.menu;

import com.golemlogix.system.CryoSource;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.ResourceStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import javax.annotation.Nullable;

/**
 * Fluid container fill/drain transactions for the player's cursor item in {@link TerminalMenu}.
 */
final class TerminalFluids {
    private TerminalFluids() {
    }

    @Nullable
    private static ResourceHandler<FluidResource> cursorFluidHandler(AbstractContainerMenu menu, ServerPlayer sp) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return null;
        }
        return ItemAccess.forPlayerCursor(sp, menu).oneByOne().getCapability(Capabilities.Fluid.ITEM);
    }

    static void fillCarried(AbstractContainerMenu menu, GolemNetwork net, ServerPlayer sp, FluidStack fluid) {
        // Liquid Cryo-Source is pumped out by automation only, never into buckets or other containers by hand
        if (fluid.isEmpty() || CryoSource.is(fluid)) {
            return;
        }
        ResourceHandler<FluidResource> handler = cursorFluidHandler(menu, sp);
        if (handler == null) {
            return;
        }
        FluidResource res = FluidResource.of(fluid);
        int fits;
        try (Transaction simTx = Transaction.openRoot()) {
            fits = handler.insert(res, Math.max(1, fluid.getAmount()), simTx);
        }
        if (fits <= 0) {
            return;
        }
        FluidStack avail = net.extractFluid(fluid, fits, true);
        if (avail.isEmpty()) {
            return;
        }
        int willTake;
        try (Transaction simTx2 = Transaction.openRoot()) {
            willTake = handler.insert(FluidResource.of(avail), avail.getAmount(), simTx2);
        }
        if (willTake <= 0) {
            return;
        }
        FluidStack extracted = net.extractFluid(fluid, willTake, false);
        if (extracted.isEmpty()) {
            return;
        }
        int filled;
        try (Transaction tx = Transaction.openRoot()) {
            filled = handler.insert(FluidResource.of(extracted), extracted.getAmount(), tx);
            if (filled > 0) {
                tx.commit();
            }
        }
        if (filled < extracted.getAmount()) {
            net.insertFluid(extracted.copyWithAmount(extracted.getAmount() - filled), false);
        }
    }

    static void drainCarried(AbstractContainerMenu menu, GolemNetwork net, ServerPlayer sp) {
        ResourceHandler<FluidResource> handler = cursorFluidHandler(menu, sp);
        if (handler == null) {
            return;
        }
        ResourceStack<FluidResource> sim;
        try (Transaction simTx = Transaction.openRoot()) {
            sim = ResourceHandlerUtil.extractFirst(handler, r -> !r.isEmpty(), Integer.MAX_VALUE, simTx);
        }
        if (sim == null || sim.isEmpty()) {
            return;
        }
        int accepted = net.insertFluid(sim.resource().toStack(sim.amount()), true);
        if (accepted <= 0) {
            sp.sendOverlayMessage(Component.translatable("message.golem_logix.fluid.no_room"));
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int got = handler.extract(sim.resource(), accepted, tx);
            if (got > 0) {
                tx.commit();
                net.insertFluid(sim.resource().toStack(got), false);
            }
        }
    }
}
