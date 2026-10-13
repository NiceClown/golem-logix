package com.golemlogix.menu;

import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Приоритет хранилища / шины: целое число, которое меняют кнопками меню (-1, +1, -10, +10, сброс).
 * Чем БОЛЬШЕ число, тем раньше устройство получает предметы (и позже отдаёт их).
 */
public final class Priority {
    public static final int MAX = 999;
    public static final int BUTTON_MINUS_1 = 0;
    public static final int BUTTON_PLUS_1 = 1;
    public static final int BUTTON_MINUS_10 = 2;
    public static final int BUTTON_PLUS_10 = 3;
    public static final int BUTTON_RESET = 4;

    private Priority() {
    }

    /** Данные меню (одно число), синхронизируемые на клиент стандартным механизмом контейнера. */
    public static ContainerData data(IntSupplier getter, IntConsumer setter) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return getter.getAsInt();
            }

            @Override
            public void set(int index, int value) {
                setter.accept(value);
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    /** Применяет нажатие кнопки. Возвращает true, если id принадлежит приоритету. */
    public static boolean handle(ContainerData data, int id) {
        int v = data.get(0);
        switch (id) {
            case BUTTON_MINUS_1 -> v -= 1;
            case BUTTON_PLUS_1 -> v += 1;
            case BUTTON_MINUS_10 -> v -= 10;
            case BUTTON_PLUS_10 -> v += 10;
            case BUTTON_RESET -> v = 0;
            default -> {
                return false;
            }
        }
        data.set(0, Mth.clamp(v, -MAX, MAX));
        return true;
    }
}