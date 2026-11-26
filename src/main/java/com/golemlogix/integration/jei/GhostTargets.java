package com.golemlogix.integration.jei;

import com.golemlogix.client.GolemScreen;
import com.golemlogix.items.FluidGhostItem;
import com.golemlogix.menu.GolemMenu;
import com.golemlogix.menu.PatternEncoderMenu;
import com.golemlogix.network.GhostSetPayload;
import com.golemlogix.system.CryoSource;
import com.golemlogix.system.Transfers;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dragging ingredients from JEI into the ghost slots of the mod's windows: bus filters, Interface settings, the encoder grid.
 * A filter can be set with an item the player does not have. A fluid turns into a full container (a bucket): that is how bus
 * filters and the Interface understand it.
 */
public final class GhostTargets<T extends GolemScreen<?>> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        ItemStack stack = toStack(ingredient.getIngredient(), screen.getMenu() instanceof PatternEncoderMenu);
        List<Target<I>> out = new ArrayList<>();
        if (stack.isEmpty() || !(screen.getMenu() instanceof GolemMenu menu)) {
            return out;
        }
        boolean encoderCrafting = menu instanceof PatternEncoderMenu enc && enc.isCraftingMode();
        if (encoderCrafting && FluidGhostItem.is(stack)) {
            return out;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            if (!menu.isGhost(i)) {
                continue;
            }
            if (encoderCrafting && i >= PatternEncoderMenu.OUT_START && i < PatternEncoderMenu.OUT_START + 3) {
                continue;
            }
            Slot slot = menu.slots.get(i);
            final int index = i;
            Rect2i area = new Rect2i(screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16);
            out.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ingredient) {
                    ClientPacketDistributor.sendToServer(new GhostSetPayload(menu.containerId, index, stack.copyWithCount(1)));
                }
            });
        }
        return out;
    }

    /**
     * A fluid becomes a fluid ghost in the encoder (a pattern ingredient in mB); for filters and the Interface it becomes a full
     * bucket if the fluid has one, and a fluid ghost otherwise (Liquid Cryo-Source has no bucket).
     */
    private static ItemStack toStack(Object ingredient, boolean encoder) {
        if (ingredient instanceof ItemStack s) {
            return s;
        }
        if (ingredient instanceof FluidStack f && !f.isEmpty()) {
            int mb = CryoSource.is(f) ? GolemMenu.FLUID_GHOST_STEP : 1000;
            ItemStack bucket = encoder ? ItemStack.EMPTY : Transfers.filledBucket(f);
            return bucket.isEmpty() ? FluidGhostItem.of(f.copyWithAmount(mb)) : bucket;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onComplete() {
    }
}
