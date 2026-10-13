package com.golemlogix.upgrade;

/**
 * Что принимает машина: сколько ячеек и какие апгрейды (и сколько ускорителей).
 */
public record UpgradeSpec(int slots, int maxSpeed, boolean crafting, boolean redstone) {
    /** Шина импорта: до 4 ускорителей и редстоун. */
    public static final UpgradeSpec IMPORT_BUS = new UpgradeSpec(6, 4, false, true);
    /** Шина экспорта: до 4 ускорителей, автокрафт и редстоун. */
    public static final UpgradeSpec EXPORT_BUS = new UpgradeSpec(6, 4, true, true);
    /** Интерфейс: как шина экспорта — ускорители, автокрафт запаса и редстоун. */
    public static final UpgradeSpec INTERFACE = new UpgradeSpec(6, 4, true, true);
    /** Крио-Калькулятор: до 4 Сжатых Сопроцессоров (ускорение) и редстоун. */
    public static final UpgradeSpec CALCULATOR = new UpgradeSpec(5, 4, false, true);
    /** Крио-Мейнфрейм: до 6 Сжатых Сопроцессоров (каждый — с множителем мейнфрейма, на все ядра) и редстоун. */
    public static final UpgradeSpec MAINFRAME = new UpgradeSpec(7, 6, false, true);
    /** Перископ и Поставщик Шаблонов: только редстоун. */
    public static final UpgradeSpec REDSTONE_ONLY = new UpgradeSpec(1, 0, false, true);

    public int max(UpgradeType type) {
        return switch (type) {
            case SPEED -> maxSpeed;
            case CRAFTING -> crafting ? 1 : 0;
            case REDSTONE -> redstone ? 1 : 0;
        };
    }
}
