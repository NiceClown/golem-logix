package com.golemlogix.crafting;

import com.golemlogix.system.GolemNetwork;
import com.golemlogix.system.ItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Вычислитель автокрафта: одна задача за раз. Это отдельный Крио-Калькулятор или одно из ядер Крио-Мейнфрейма
 * (у всех ядер мейнфрейма один блок, их различает номер core).
 */
public interface CraftingCpu {
    @Nullable
    CraftingJob getJob();

    default boolean isIdle() {
        return getJob() == null;
    }

    void startJob(CraftingJob job);

    /** Отмена: всё содержимое задачи возвращается в сеть. */
    void cancelJob();

    /** Сколько единиц предмета ждёт задача от машин. */
    long wants(ItemKey key);

    /** Принять результат машины: возвращает то, что задаче не нужно. */
    ItemStack absorb(ItemStack stack);

    /** Блок вычислителя убран из мира (или ядро исчезло вместе с калькулятором в мейнфрейме). */
    boolean isRemoved();

    /** Блок, в котором вычислитель стоит (для канала, монитора и подсказок). */
    BlockPos cpuPos();

    /** Номер ядра в мейнфрейме (1..), 0 — отдельный калькулятор. */
    default int core() {
        return 0;
    }

    /** Сколько шагов шаблонов за цикл. */
    int getWorkers();

    /** Число ускорителей для показа в мониторе (приставленные сопроцессоры, у мейнфрейма — сжатые с множителем). */
    int getCoprocessors();

    /** Может ли сейчас взять задачу: у блока есть канал в этой сети. */
    default boolean canRun(GolemNetwork net) {
        return net.isDeviceActive(cpuPos());
    }
}
