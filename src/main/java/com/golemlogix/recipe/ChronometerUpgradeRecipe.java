package com.golemlogix.recipe;

import com.golemlogix.init.ModDataComponents;
import com.golemlogix.init.ModItems;
import com.golemlogix.init.ModRecipes;
import com.golemlogix.items.PortableChronometerItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Улучшение дальности Портативного Хронометра с сохранением привязки к Ядру:
 * Хронометр + Око Эндера + Сплющенная Медная Матрица -> уровень «измерение»;
 * Хронометр (уровень «измерение») + Квантово-Оккультная Матрица -> «межпространственный».
 */
public class ChronometerUpgradeRecipe extends CustomRecipe {
    public ChronometerUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Parsed(ItemStack chronometer, int targetTier) {
    }

    private static Parsed parse(CraftingInput input) {
        ItemStack chronometer = ItemStack.EMPTY;
        boolean eye = false;
        boolean matrix = false;
        boolean occult = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.getItem() instanceof PortableChronometerItem) {
                if (!chronometer.isEmpty()) {
                    return null;
                }
                chronometer = s;
            } else if (s.is(Items.ENDER_EYE) && !eye) {
                eye = true;
            } else if (s.is(ModItems.FLATTENED_COPPER_MATRIX.get()) && !matrix) {
                matrix = true;
            } else if (s.is(ModItems.INTERDIMENSIONAL_OCCULT_MATRIX.get()) && !occult) {
                occult = true;
            } else {
                return null;
            }
        }
        if (chronometer.isEmpty()) {
            return null;
        }
        int tier = PortableChronometerItem.getTier(chronometer).ordinal();
        if (eye && matrix && !occult && tier == 0) {
            return new Parsed(chronometer, 1);
        }
        if (occult && !eye && !matrix && tier == 1) {
            return new Parsed(chronometer, 2);
        }
        return null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return parse(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Parsed p = parse(input);
        if (p == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = p.chronometer.copyWithCount(1);
        result.set(ModDataComponents.WIRELESS_TIER.get(), p.targetTier);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.CHRONOMETER_UPGRADE.get();
    }
}
