package com.golemlogix.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Меню с прокручиваемой сеткой ячеек и поиском (картотека, концентратор): клиент присылает первую видимую строку и
 * строку поиска (пакет MenuScrollPayload), сервер сдвигает и фильтрует окно ячеек, а обычная синхронизация меню
 * присылает новые предметы.
 */
public interface ScrollableMenu {
    void view(int row, String filter);

    /**
     * Подходит ли предмет под поиск: по названию (на языке сервера — в одиночной игре это язык игрока) и по id предмета;
     * «@мод» — по моду.
     */
    static boolean matches(ItemStack stack, String filter) {
        if (filter.isEmpty()) {
            return true;
        }
        if (stack.isEmpty()) {
            return false;
        }
        String f = filter.toLowerCase(Locale.ROOT).trim();
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (f.startsWith("@")) {
            return id.getNamespace().contains(f.substring(1));
        }
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(f) || id.getPath().contains(f.replace(' ', '_'));
    }
}
