package com.golemlogix.integration.jei;

import com.golemlogix.client.GolemScreen;
import com.golemlogix.menu.GolemMenu;
import com.golemlogix.network.GhostSetPayload;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Перетаскивание ингредиентов из JEI в призрачные ячейки окон мода: фильтры шин, настройка Интерфейса, сетка кодировщика.
 * Можно настроить фильтр предметом, которого у игрока нет. Жидкость превращается в полную ёмкость (ведро): так её понимают
 * фильтры шин и Интерфейс.
 */
public final class GhostTargets<T extends GolemScreen<?>> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        ItemStack stack = toStack(ingredient.getIngredient());
        List<Target<I>> out = new ArrayList<>();
        if (stack.isEmpty() || !(screen.getMenu() instanceof GolemMenu menu)) {
            return out;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            if (!menu.isGhost(i)) {
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
                    PacketDistributor.sendToServer(new GhostSetPayload(menu.containerId, index, stack.copyWithCount(1)));
                }
            });
        }
        return out;
    }

    private static ItemStack toStack(Object ingredient) {
        if (ingredient instanceof ItemStack s) {
            return s;
        }
        if (ingredient instanceof FluidStack f && !f.isEmpty()) {
            return FluidUtil.getFilledBucket(f.copyWithAmount(1000));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void onComplete() {
    }
}
