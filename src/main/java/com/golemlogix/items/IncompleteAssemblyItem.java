package com.golemlogix.items;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A part on Create's assembly line (the transitional item of a create:sequenced_assembly recipe). Like Create's own unfinished
 * parts it shows how far the assembly went: Create keeps the progress in its component "create:sequenced_assembly", read here
 * by name, so the mod needs no Create code (without Create the item is never made).
 */
public class IncompleteAssemblyItem extends Item {
    private static final Identifier COMPONENT = Identifier.fromNamespaceAndPath("create", "sequenced_assembly");
    @Nullable
    private static java.lang.reflect.Method progressMethod;

    public IncompleteAssemblyItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** 0..1, or -1 if the stack carries no assembly progress. */
    public static float progress(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(COMPONENT);
        Object value = type == null ? null : stack.get(type);
        if (value == null) {
            return -1f;
        }
        try {
            if (progressMethod == null || progressMethod.getDeclaringClass() != value.getClass()) {
                progressMethod = value.getClass().getMethod("progress");
            }
            return ((Number) progressMethod.invoke(value)).floatValue();
        } catch (ReflectiveOperationException | ClassCastException e) {
            return -1f;
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return progress(stack) >= 0f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(Mth.clamp(progress(stack), 0f, 1f) * 13f);
    }

    /** From Create's grey-blue to the cryo cyan as the assembly goes on. */
    @Override
    public int getBarColor(ItemStack stack) {
        float t = Mth.clamp(progress(stack), 0f, 1f);
        int from = 0x4A6A7A, to = 0x5CE6F0;
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
