package com.golemlogix.blockentity;

import com.golemlogix.integration.ars.ArsImbuement;
import com.golemlogix.recipe.SaturatorRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Recipe resolution for {@link ManaSaturatorBlockEntity}: unified lookup across mod Saturator
 * recipes and Ars Nouveau Imbuement Chamber gem recipes, plus manual anvil hand-forging outputs.
 */
final class SaturatorRecipes {
    /** Resolved ritual output, Source cost, and total work units for a single lookup per tick. */
    record Info(ItemStack output, int source, int work) {
    }

    private SaturatorRecipes() {
    }

    @Nullable
    static Info resolve(@Nullable Level level, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        SaturatorRecipe own = SaturatorRecipe.find(level, input);
        if (own != null) {
            return new Info(own.result(), own.source(), Math.max(1, own.ticks() * ManaSaturatorBlockEntity.BASE_SPEED));
        }
        ArsImbuement.Entry gem = ArsImbuement.find(level, input);
        if (gem != null) {
            return new Info(gem.output(), gem.source(), Math.max(1, gem.ticks() * ManaSaturatorBlockEntity.BASE_SPEED));
        }
        return null;
    }

    static boolean isInput(@Nullable Level level, ItemStack stack) {
        return resolve(level, stack) != null;
    }

    static ItemStack outputFor(@Nullable Level level, ItemStack input) {
        Info info = resolve(level, input);
        return info == null ? ItemStack.EMPTY : info.output().copy();
    }

    static int requiredSource(@Nullable Level level, ItemStack input) {
        Info info = resolve(level, input);
        return info != null ? info.source() : SaturatorRecipe.DEFAULT_SOURCE;
    }

    static int ritualWork(@Nullable Level level, ItemStack input) {
        Info info = resolve(level, input);
        return info != null ? info.work() : ManaSaturatorBlockEntity.MAX_PROGRESS;
    }

    static ItemStack manualOutputFor(@Nullable Level level, ItemStack input) {
        SaturatorRecipe recipe = SaturatorRecipe.find(level, input);
        return recipe == null ? ItemStack.EMPTY : recipe.handResult().copy();
    }

    static boolean isManualInput(@Nullable Level level, ItemStack stack) {
        return !manualOutputFor(level, stack).isEmpty();
    }
}
