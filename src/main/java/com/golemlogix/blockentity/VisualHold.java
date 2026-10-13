package com.golemlogix.blockentity;

import net.minecraft.world.item.ItemStack;

/**
 * Серверная «витрина» для клиентской анимации вычислителей: что показывать и до какого тика. Короткий заказ может
 * начаться и закончиться между двумя рассылками состояния (пакет блок-сущности уходит раз в конце тика, мейнфрейм шлёт
 * сводку не чаще раза в 10 тиков) — клиент тогда не увидел бы работы вовсе. Витрина держит «работаю, делаю X» не меньше
 * {@link #TICKS} тиков после последнего замеченного шага. На сам крафт она не влияет — только на то, что видят игроки.
 */
public final class VisualHold {
    public static final int TICKS = 40;
    private ItemStack item = ItemStack.EMPTY;
    private long until = Long.MIN_VALUE;
    private boolean reported = true;

    /** Отметить работу над предметом (пустой предмет — продлить, не меняя показанного). Возвращает true, если витрина только что включилась. */
    public boolean show(ItemStack stack, long now) {
        boolean wasActive = active(now);
        if (!stack.isEmpty()) {
            item = stack.copyWithCount(1);
        }
        until = now + TICKS;
        reported = false;
        return !wasActive;
    }

    public boolean active(long now) {
        return now < until;
    }

    public ItemStack item(long now) {
        return active(now) ? item : ItemStack.EMPTY;
    }

    /** Окно только что закрылось (true один раз за окно): пора разослать клиентам «простой». */
    public boolean ended(long now) {
        if (!reported && !active(now)) {
            reported = true;
            return true;
        }
        return false;
    }
}
