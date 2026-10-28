package com.golemlogix.recipe;

import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModRecipes;
import com.golemlogix.items.CopperTankItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/**
 * Copper Tanks joined in a crafting grid (two to nine, anywhere in it): one tank that holds as much as all of them, up to
 * {@link CopperTankBlockEntity#MAX_CAPACITY}, with all their fluid - which has to be one fluid (empty tanks join any).
 */
public class CopperTankJoinRecipe extends CustomRecipe {
    public static final CopperTankJoinRecipe INSTANCE = new CopperTankJoinRecipe();
    public static final RecipeSerializer<CopperTankJoinRecipe> SERIALIZER =
            new RecipeSerializer<>(MapCodec.unit(INSTANCE), StreamCodec.unit(INSTANCE));

    /** The joined tank, or empty if the grid holds anything else, fewer than two tanks, two fluids or too much. */
    public static ItemStack join(CraftingInput input) {
        int tanks = 0;
        long capacity = 0;
        FluidStack fluid = FluidStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (!(s.getItem() instanceof CopperTankItem)) {
                return ItemStack.EMPTY;
            }
            tanks++;
            capacity += CopperTankItem.capacity(s);
            FluidStack f = CopperTankItem.fluid(s);
            if (f.isEmpty()) {
                continue;
            }
            if (fluid.isEmpty()) {
                fluid = f.copy();
            } else if (FluidStack.isSameFluidSameComponents(fluid, f)) {
                fluid.grow(f.getAmount());
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (tanks < 2 || capacity > CopperTankBlockEntity.MAX_CAPACITY) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(ModBlocks.COPPER_TANK.get());
        result.set(ModDataComponents.TANK_CAPACITY.get(), (int) capacity);
        if (!fluid.isEmpty()) {
            result.set(ModDataComponents.TANK_FLUID.get(), SimpleFluidContent.copyOf(fluid));
        }
        return result;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !join(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return join(input);
    }

    @Override
    public RecipeSerializer<CopperTankJoinRecipe> getSerializer() {
        return ModRecipes.COPPER_TANK_JOIN.get();
    }
}
