package com.golemlogix.menu;

import com.golemlogix.system.CryoSource;
import com.golemlogix.system.GolemNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Fluid container fill/drain operations for the player's cursor item in {@link TerminalMenu}.
 */
final class TerminalFluids {
    private TerminalFluids() {
    }

    private static void giveContainer(AbstractContainerMenu menu, ServerPlayer sp, IFluidHandlerItem handler, ItemStack original) {
        ItemStack result = handler.getContainer();
        if (original.getCount() <= 1) {
            menu.setCarried(result);
        } else {
            original.shrink(1);
            menu.setCarried(original);
            if (!result.isEmpty()) {
                sp.getInventory().placeItemBackInInventory(result);
            }
        }
    }

    static void fillCarried(AbstractContainerMenu menu, GolemNetwork net, ServerPlayer sp, FluidStack fluid) {
        ItemStack carried = menu.getCarried();
        // Liquid Cryo-Source is pumped out by automation only, never into buckets or other containers by hand
        if (carried.isEmpty() || fluid.isEmpty() || CryoSource.is(fluid)) {
            return;
        }
        ItemStack single = carried.copyWithCount(1);
        IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return;
        }
        int fits = handler.fill(fluid.copyWithAmount(Math.max(1, fluid.getAmount())), IFluidHandler.FluidAction.SIMULATE);
        if (fits <= 0) {
            return;
        }
        FluidStack avail = net.extractFluid(fluid, fits, true);
        if (avail.isEmpty()) {
            return;
        }
        int willTake = handler.fill(avail, IFluidHandler.FluidAction.SIMULATE);
        if (willTake <= 0) {
            return;
        }
        FluidStack extracted = net.extractFluid(fluid, willTake, false);
        if (extracted.isEmpty()) {
            return;
        }
        int filled = handler.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
        if (filled < extracted.getAmount()) {
            net.insertFluid(extracted.copyWithAmount(extracted.getAmount() - filled), false);
        }
        if (filled > 0) {
            giveContainer(menu, sp, handler, carried);
        }
    }

    static void drainCarried(AbstractContainerMenu menu, GolemNetwork net, ServerPlayer sp) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        ItemStack single = carried.copyWithCount(1);
        IFluidHandlerItem handler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return;
        }
        FluidStack sim = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (sim.isEmpty()) {
            return;
        }
        int accepted = net.insertFluid(sim, true);
        if (accepted <= 0) {
            sp.displayClientMessage(Component.translatable("message.golem_logix.fluid.no_room"), true);
            return;
        }
        FluidStack drained = handler.drain(sim.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return;
        }
        net.insertFluid(drained, false);
        giveContainer(menu, sp, handler, carried);
    }
}
