package com.golemlogix.client;

import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.PixelMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Consumer;

import static com.golemlogix.client.render.PixelMesh.PIXEL;

/**
 * A crew of snowman assemblers on a 16x16 platform (Cryo-Calculator roof, Cryo-Mainframe shelf). While working, each bends
 * down for a packed-ice part, lifts it overhead, tosses it into the floating item projection, and hops; while idle they look
 * around, hop in circles, wave, or doze. When coprocessors accelerate the work, a foreman with a copper atlant head appears
 * at the front edge and directs the crew.
 */
public final class SnowCrew {
    /** Worker placement on the 16x16 platform (pixels), model scale, and work-cycle period (ticks). */
    private record Worker(float x, float z, float scale, float period) {
    }

    private static final Worker[] CREW = {
            new Worker(3.4f, 5.2f, 0.15f, 30f),
            new Worker(12.6f, 5.8f, 0.18f, 34f),
            new Worker(7.0f, 12.2f, 0.12f, 26f)};
    private static final BlockState PART = Blocks.PACKED_ICE.defaultBlockState();
    /** Held part scale (~5 model pixels). */
    private static final float PART_SIZE = 0.3f;
    private static final Vector3f UP_LEFT = new Vector3f(3f, 27f, 5f), UP_RIGHT = new Vector3f(-3f, 27f, 5f);
    private static final Vector3f UP_CENTER = new Vector3f(0f, 27.5f, 5f);
    private static final Vector3f FOREMAN_R0 = new Vector3f(-8f, 17f, 7f), FOREMAN_R1 = new Vector3f(-6f, 28f, 5f);
    private static final Vector3f FOREMAN_L_REST = new Vector3f(7.5f, 12f, 1.5f);
    private static final Vector3f FOREMAN_L_POINT = new Vector3f(3f, 24f, 9f), FOREMAN_R_POINT = new Vector3f(-3f, 24f, 9f);

    // Render-thread scratch buffers (consumed synchronously by SnowmanPuppet.render)
    private static final Vector3f WORKER_POS = new Vector3f();
    private static final Vector3f IDLE_L = new Vector3f(), IDLE_R = new Vector3f();
    private static final Vector3f WORK_L = new Vector3f(), WORK_R = new Vector3f(), WORK_BOX = new Vector3f();
    private static final Vector3f BLEND_L = new Vector3f(), BLEND_R = new Vector3f();
    private static final Vector3f FOREMAN_POS = new Vector3f(), FOREMAN_R = new Vector3f();

    private static final WorkState WORK = new WorkState();
    private static final IdleState IDLE = new IdleState();

    private SnowCrew() {
    }

    private static final class WorkState {
        float bob, squash, pitch, boxSize;
        Vector3f lh, rh;
        boolean hasBox;
    }

    private static final class IdleState {
        float bob, squash, headYaw, headPitch, yaw;
        Vector3f lh, rh;
    }

    /**
     * Renders the three snowmen and optional foreman in the block frame ({@code 0..1}): {@code floorY} = platform height,
     * {@code work} = idle-to-working blend ({@code 0..1}), {@code foreman} = foreman appearance ({@code 0..1}),
     * {@code proj} = target centre of the floating item projection.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, float time, long seed, float floorY, float work,
                              float foreman, Vector3f proj) {
        float blend = ease(work);
        for (int i = 0; i < CREW.length; i++) {
            renderWorker(pose, buffers, light, time, seed, floorY, blend, proj, i);
        }
        if (foreman > 0.001f) {
            renderForeman(pose, buffers, light, time, floorY, foreman);
        }
    }

    private static void renderWorker(PoseStack pose, MultiBufferSource buffers, int light, float time, long seed,
                                     float floorY, float blend, Vector3f proj, int i) {
        Worker w = CREW[i];
        WORKER_POS.set(w.x * PIXEL, floorY, w.z * PIXEL);
        float toCenter = (float) Math.toDegrees(Math.atan2(0.5f - WORKER_POS.x, 0.5f - WORKER_POS.z));
        float offset = (float) Math.floorMod(seed * 31 + i * 977L, 1000L) / 1000f;

        if (blend <= 0.001f) {
            computeIdle(time, seed, i, toCenter, offset);
            SnowmanPuppet.Pose p = new SnowmanPuppet.Pose(IDLE.bob, IDLE.squash, IDLE.headYaw, IDLE.headPitch, IDLE.lh, IDLE.rh);
            SnowmanPuppet.render(pose, buffers, light, WORKER_POS, IDLE.yaw, w.scale, SnowmanPuppet.Head.SNOW, p, null);
        } else if (blend >= 0.999f) {
            computeWorking(pose, buffers, light, time, w, WORKER_POS, toCenter, offset, proj, 1f);
            SnowmanPuppet.Pose p = new SnowmanPuppet.Pose(WORK.bob, WORK.squash, 0f, WORK.pitch, WORK.lh, WORK.rh);
            SnowmanPuppet.render(pose, buffers, light, WORKER_POS, toCenter, w.scale, SnowmanPuppet.Head.SNOW, p,
                    WORK.hasBox ? heldIceBox(buffers, light, WORK.boxSize) : null);
        } else {
            computeIdle(time, seed, i, toCenter, offset);
            computeWorking(pose, buffers, light, time, w, WORKER_POS, toCenter, offset, proj, blend);
            float yaw = Mth.rotLerp(blend, IDLE.yaw, toCenter);
            BLEND_L.set(IDLE.lh).lerp(WORK.lh, blend);
            BLEND_R.set(IDLE.rh).lerp(WORK.rh, blend);
            SnowmanPuppet.Pose p = new SnowmanPuppet.Pose(
                    Mth.lerp(blend, IDLE.bob, WORK.bob),
                    Mth.lerp(blend, IDLE.squash, WORK.squash),
                    Mth.lerp(blend, IDLE.headYaw, 0f),
                    Mth.lerp(blend, IDLE.headPitch, WORK.pitch),
                    BLEND_L, BLEND_R);
            float finalSize = WORK.boxSize * blend;
            SnowmanPuppet.render(pose, buffers, light, WORKER_POS, yaw, w.scale, SnowmanPuppet.Head.SNOW, p,
                    WORK.hasBox && finalSize > 0.01f ? heldIceBox(buffers, light, finalSize) : null);
        }
    }

    @Nullable
    private static Consumer<PoseStack> heldIceBox(MultiBufferSource buffers, int light, float size) {
        float bx = WORK_BOX.x, by = WORK_BOX.y, bz = WORK_BOX.z;
        return hp -> {
            hp.translate((bx - SnowmanPuppet.HOLD_CENTER.x) * PIXEL,
                    (by - SnowmanPuppet.HOLD_CENTER.y) * PIXEL,
                    (bz - SnowmanPuppet.HOLD_CENTER.z) * PIXEL);
            cube(hp, buffers, light, size);
        };
    }

    private static void computeWorking(PoseStack pose, MultiBufferSource buffers, int light, float time, Worker w, Vector3f pos,
                                       float yaw, float offset, Vector3f proj, float blend) {
        float f = ((time / w.period) + offset) % 1f;
        WORK.bob = 0f;
        WORK.squash = 0f;
        WORK.hasBox = false;
        WORK.boxSize = PART_SIZE;
        if (f < 0.15f) {
            // bend down for a packed-ice part
            float k = f / 0.15f, dip = Mth.sin(k * Mth.PI);
            WORK.squash = 0.14f * dip;
            WORK.pitch = 25f;
            WORK.lh = WORK_L.set(SnowmanPuppet.HOLD_LEFT).add(0f, -4f * dip, 0f);
            WORK.rh = WORK_R.set(SnowmanPuppet.HOLD_RIGHT).add(0f, -4f * dip, 0f);
            if (k > 0.5f) {
                WORK_BOX.set(SnowmanPuppet.HOLD_CENTER).add(0f, -4f * dip, 0f);
                WORK.hasBox = true;
                WORK.boxSize = PART_SIZE * (k - 0.5f) * 2f;
            }
        } else if (f < 0.45f) {
            // lift overhead
            float k = ease((f - 0.15f) / 0.3f);
            WORK.pitch = Mth.lerp(k, 10f, -30f);
            WORK.lh = WORK_L.set(SnowmanPuppet.HOLD_LEFT).lerp(UP_LEFT, k);
            WORK.rh = WORK_R.set(SnowmanPuppet.HOLD_RIGHT).lerp(UP_RIGHT, k);
            WORK_BOX.set(SnowmanPuppet.HOLD_CENTER).lerp(UP_CENTER, k);
            WORK.hasBox = true;
            WORK.bob = 0.6f * k;
        } else if (f < 0.75f) {
            // tossed: part arcs into the floating projection while arms lower
            float k = (f - 0.45f) / 0.3f;
            WORK.pitch = -35f;
            float ek = ease(k);
            WORK.lh = WORK_L.set(UP_LEFT).lerp(SnowmanPuppet.REST_LEFT, ek);
            WORK.rh = WORK_R.set(UP_RIGHT).lerp(SnowmanPuppet.REST_RIGHT, ek);
            if (blend > 0.25f) {
                flyingPart(pose, buffers, light, pos, yaw, w.scale, proj, k, (blend - 0.25f) / 0.75f);
            }
        } else {
            // pleased little hop
            float k = (f - 0.75f) / 0.25f;
            WORK.bob = 1.6f * Mth.sin(k * Mth.PI);
            WORK.pitch = -10f;
            WORK.lh = SnowmanPuppet.REST_LEFT;
            WORK.rh = SnowmanPuppet.REST_RIGHT;
        }
    }

    /** Packed-ice part arcing and spinning from raised hands into the item projection. */
    private static void flyingPart(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, float yaw, float scale,
                                   Vector3f proj, float k, float blendScale) {
        float size = PART_SIZE * scale * (1f - 0.8f * k) * blendScale;
        if (size <= 0.001f) {
            return;
        }
        float rad = yaw * Mth.DEG_TO_RAD;
        float startX = pos.x + UP_CENTER.z * Mth.sin(rad) * PIXEL * scale;
        float startY = pos.y + UP_CENTER.y * PIXEL * scale;
        float startZ = pos.z + UP_CENTER.z * Mth.cos(rad) * PIXEL * scale;
        float atX = Mth.lerp(k, startX, proj.x);
        float atY = Mth.lerp(k, startY, proj.y) + 0.08f * Mth.sin(k * Mth.PI);
        float atZ = Mth.lerp(k, startZ, proj.z);
        pose.pushPose();
        pose.translate(atX, atY, atZ);
        pose.mulPose(Axis.YP.rotationDegrees(k * 540f));
        cube(pose, buffers, light, size);
        pose.popPose();
    }

    private static void computeIdle(float time, long seed, int i, float toCenter, float offset) {
        float t = time + offset * 400f;
        long window = (long) Math.floor(t / 120f);
        long h = seed * 6364136223846793005L + i * 1442695040888963407L + window * 0x9E3779B97F4A7C15L;
        int activity = (int) Math.floorMod(h >>> 33, 4L);
        float local = t - window * 120f;
        float fade = Mth.clamp(Math.min(local, 120f - local) / 15f, 0f, 1f);
        IDLE.yaw = toCenter;
        IDLE.bob = 0f;
        IDLE.squash = 0f;
        IDLE.headYaw = 0f;
        switch (activity) {
            case 0 -> {
                // looks around
                IDLE.yaw = toCenter + 60f * Mth.sin(t * 0.04f) * fade;
                IDLE.headYaw = 35f * Mth.sin(t * 0.09f) * fade;
                IDLE.headPitch = -5f;
                IDLE.lh = SnowmanPuppet.REST_LEFT;
                IDLE.rh = SnowmanPuppet.REST_RIGHT;
            }
            case 1 -> {
                // hops in a circle on the spot
                IDLE.yaw = toCenter + local * 3f * fade;
                float hop = Math.abs(Mth.sin(t * 0.3f)) * fade;
                IDLE.bob = 2.2f * hop;
                IDLE.squash = 0.12f * (1f - hop) * fade;
                IDLE.headPitch = -10f;
                IDLE.lh = IDLE_L.set(9f, 18f + 6f * hop, 2f);
                IDLE.rh = IDLE_R.set(-9f, 18f + 6f * hop, 2f);
            }
            case 2 -> {
                // faces outward and waves
                IDLE.yaw = toCenter + 180f * fade;
                float wave = Mth.sin(t * 0.5f);
                IDLE.headPitch = -15f * fade;
                IDLE.lh = SnowmanPuppet.REST_LEFT;
                IDLE.rh = IDLE_R.set(-9f + 2f * wave, 27f, 3f + 3f * wave);
                IDLE_L.set(SnowmanPuppet.REST_RIGHT).lerp(IDLE_R, fade);
                IDLE.rh = IDLE_L;
            }
            default -> {
                // dozes with drooping head
                float nod = 0.5f + 0.5f * Mth.sin(t * 0.05f);
                IDLE.squash = 0.04f * Mth.sin(t * 0.08f) * fade;
                IDLE.headPitch = (20f + 12f * nod) * fade;
                IDLE.lh = SnowmanPuppet.REST_LEFT;
                IDLE.rh = SnowmanPuppet.REST_RIGHT;
            }
        }
    }

    /** Foreman with a copper atlant head at the front edge: pumps its arm, nods, and occasionally points at the projection. */
    private static void renderForeman(PoseStack pose, MultiBufferSource buffers, int light, float time, float floorY, float appear) {
        float pop = easeOutBack(appear);
        float beat = time / 14f * Mth.TWO_PI;
        float pump = 0.5f + 0.5f * Mth.sin(beat);
        Vector3f left, right;
        if (Mth.sin(time * 0.021f) > 0.85f) {
            left = FOREMAN_L_POINT;
            right = FOREMAN_R_POINT;
        } else {
            left = FOREMAN_L_REST;
            right = FOREMAN_R.set(FOREMAN_R0).lerp(FOREMAN_R1, pump);
        }
        SnowmanPuppet.Pose p = new SnowmanPuppet.Pose(0.8f * Math.abs(Mth.sin(beat * 0.5f)), 0f, 12f * Mth.sin(time * 0.05f),
                8f * Mth.sin(beat), left, right);
        FOREMAN_POS.set(0.5f, floorY, 14.2f * PIXEL);
        SnowmanPuppet.render(pose, buffers, light, FOREMAN_POS, 180f, 0.2f * pop, SnowmanPuppet.Head.ATLANT, p, null);
    }

    private static void cube(PoseStack pose, MultiBufferSource buffers, int light, float size) {
        if (size <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.scale(size, size, size);
        pose.translate(-0.5f, -0.5f, -0.5f);
        BerBuffers.block(buffers, PART, pose, light);
        pose.popPose();
    }

    private static float ease(float k) {
        return PixelMesh.smoothstep(Mth.clamp(k, 0f, 1f));
    }

    private static float easeOutBack(float k) {
        k = Mth.clamp(k, 0f, 1f);
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (k - 1f) * (k - 1f) * (k - 1f) + c1 * (k - 1f) * (k - 1f);
    }
}
