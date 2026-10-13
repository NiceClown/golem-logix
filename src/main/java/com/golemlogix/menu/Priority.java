package com.golemlogix.menu;

import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Storage / bus priority: an integer changed by menu buttons (-1, +1, -10, +10, reset).
 * The BIGGER the number, the earlier the device gets items (and the later it gives them away).
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

    /** Menu data (one number), synced to the client by the standard container mechanism. */
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

    /** Applies a button press. Returns true if the id belongs to the priority. */
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