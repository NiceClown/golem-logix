package com.golemlogix.blockentity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.function.Supplier;

/**
 * Медный голем-фонарщик на крыше машины (только клиент): ходит между точками, пока машина простаивает, а при работе или
 * аварии возвращается «домой» и стоит с лампой. Координаты — пиксели крыши в системе модели машины.
 */
public final class LampWalker {
    public float x, z, xPrev, zPrev, heading, headingPrev, stride, stridePrev;
    private float goalX, goalZ;
    private int pause;
    /** Номер точки маршрута (для машин с маршрутом по кругу). */
    public int ring;

    public LampWalker(float x, float z) {
        this.x = this.xPrev = this.goalX = x;
        this.z = this.zPrev = this.goalZ = z;
    }

    /** Идёт ли сейчас (для анимации шага). */
    public boolean walking() {
        return Math.abs(x - xPrev) + Math.abs(z - zPrev) > 0.01f;
    }

    /**
     * Шаг: idle — гуляет (следующую точку даёт nextGoal), иначе — идёт домой (homeX, homeZ) и стоит там.
     */
    public void tick(boolean idle, float homeX, float homeZ, RandomSource random, Supplier<float[]> nextGoal) {
        xPrev = x;
        zPrev = z;
        headingPrev = heading;
        stridePrev = stride;
        float gx = idle ? goalX : homeX, gz = idle ? goalZ : homeZ;
        float dx = gx - x, dz = gz - z, d = (float) Math.hypot(dx, dz);
        if (d > 0.15f) {
            float sp = Math.min(d, 0.12f);
            x += dx / d * sp;
            z += dz / d * sp;
            float want = (float) Math.toDegrees(Math.atan2(dx, dz));
            heading += Mth.wrapDegrees(want - heading) * 0.25f;
            stride += sp * 1.6f;
        } else if (idle && --pause <= 0) {
            float[] g = nextGoal.get();
            goalX = g[0];
            goalZ = g[1];
            pause = 20 + random.nextInt(40);
        }
    }
}
