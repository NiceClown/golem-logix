package com.golemlogix.upgrade;

import net.minecraft.nbt.CompoundTag;

/**
 * Состояние управления редстоуном одной машины: выбранный режим, прошлый уровень сигнала и «накопленный» импульс.
 * Без Модуля Редстоуна машина сигнал не замечает.
 */
public final class RedstoneControl {
    private RedstoneMode mode = RedstoneMode.HIGH;
    private boolean lastPowered;
    private boolean pulse;

    public RedstoneMode mode() {
        return mode;
    }

    public void setMode(RedstoneMode mode) {
        this.mode = mode;
        this.pulse = false;
    }

    /** Отметить текущий уровень сигнала (раз в тик или при каждой проверке): фронт сигнала копит импульс. */
    public void observe(boolean powered) {
        if (powered && !lastPowered) {
            pulse = true;
        }
        lastPowered = powered;
    }

    /**
     * Можно ли машине работать сейчас. installed — стоит ли Модуль Редстоуна; consume — в режиме импульса
     * израсходовать импульс (true там, где машина действительно делает действие).
     */
    public boolean allows(boolean installed, boolean powered, boolean consume) {
        observe(powered);
        if (!installed) {
            return true;
        }
        return switch (mode) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
            case PULSE -> {
                boolean ok = pulse;
                if (ok && consume) {
                    pulse = false;
                }
                yield ok;
            }
        };
    }

    public void save(CompoundTag tag) {
        tag.putInt("RedstoneMode", mode.ordinal());
        tag.putBoolean("RedstoneLast", lastPowered);
    }

    public void load(CompoundTag tag) {
        mode = tag.contains("RedstoneMode") ? RedstoneMode.byId(tag.getInt("RedstoneMode")) : RedstoneMode.HIGH;
        lastPowered = tag.getBoolean("RedstoneLast");
        pulse = false;
    }
}
