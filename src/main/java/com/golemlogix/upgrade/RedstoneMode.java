package com.golemlogix.upgrade;

/**
 * Режим Модуля Редстоуна — как у карты редстоуна AE2 и «режима редстоуна» Refined Storage.
 */
public enum RedstoneMode {
    /** Сигнал не важен. */
    IGNORE,
    /** Работает, пока есть сигнал. */
    HIGH,
    /** Работает, пока сигнала нет. */
    LOW,
    /** Одно действие на каждый импульс (фронт сигнала). */
    PULSE;

    public static RedstoneMode byId(int id) {
        RedstoneMode[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /** Следующий режим по кругу; без импульса — для хранилищ, где «одно действие» не имеет смысла. */
    public RedstoneMode next(boolean allowPulse) {
        RedstoneMode n = byId(ordinal() + 1);
        return !allowPulse && n == PULSE ? IGNORE : n;
    }

    public String key() {
        return "gui.golem_logix.redstone." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
