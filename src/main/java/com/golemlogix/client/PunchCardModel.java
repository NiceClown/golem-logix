package com.golemlogix.client;

import com.golemlogix.data.PatternData;
import com.golemlogix.init.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Модель записанной перфокарты: пока зажат Shift, вместо карты рисуется то, что она делает (как шаблоны AE2) — в инвентаре,
 * в поставщике, в руке. Чистая карта и карта без Shift рисуются как обычно.
 */
public final class PunchCardModel extends BakedModelWrapper<BakedModel> {
    /** Только для снимков DevShots: вести себя так, будто Shift зажат. */
    public static boolean devShift = false;
    private final ItemOverrides overrides;

    public PunchCardModel(BakedModel original) {
        super(original);
        ItemOverrides base = original.getOverrides();
        this.overrides = new ItemOverrides() {
            @Nullable
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                PatternData data = stack.get(ModDataComponents.PATTERN.get());
                if (data != null && (Screen.hasShiftDown() || devShift)) {
                    ItemStack out = data.primaryOutput();
                    if (!out.isEmpty() && !out.is(stack.getItem())) {
                        return Minecraft.getInstance().getItemRenderer().getModel(out, level, entity, seed);
                    }
                }
                return base.resolve(model, stack, level, entity, seed);
            }
        };
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }
}
