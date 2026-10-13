package com.golemlogix.blockentity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.function.Supplier;

/**
 * Copper lamplighter golem on a machine roof (client only): walks between points while the machine is idle, and when it works or
 * fails it goes back "home" and stands with its lamp. Coordinates are roof pixels in the machine model's space.
 */
public final class LampWalker {
    public float x, z, xPrev, zPrev, heading, headingPrev, stride, stridePrev;
    private float goalX, goalZ;
    private int pause;
    /** Route point index (for machines with a circular route). */
    public int ring;

    public LampWalker(float x, float z) {
        this.x = this.xPrev = this.goalX = x;
        this.z = this.zPrev = this.goalZ = z;
    }

    /** Whether it is walking now (for the step animation). */
    public boolean walking() {
        return Math.abs(x - xPrev) + Math.abs(z - zPrev) > 0.01f;
    }

    /**
     * Step: when idle it wanders (nextGoal gives the next point), otherwise it walks home (homeX, homeZ) and stands there.
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
