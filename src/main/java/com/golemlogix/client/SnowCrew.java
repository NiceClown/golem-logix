package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * A crew of snowman assemblers on a 16x16 platform (the Cryo-Calculator roof, a Cryo-Mainframe shelf). At work each one
 * in its own rhythm bends down for an ice part, lifts it over its head and tosses it into the item projection, then happily
 * hops. When idle they do cute silly things: look around, hop in circles, wave, doze.
 * The foreman with a copper atlant head appears only when the coprocessors really speed up the work, and urges
 * the others on: waves its stick arm and nods.
 */
public final class SnowCrew {
    /** A worker: place on the platform (pixels), model scale (different heights) and the length of its own work beat (ticks). */
    private record Worker(float x, float z, float scale, float period) {
    }

    private static final Worker[] CREW = {
            new Worker(3.4f, 5.2f, 0.15f, 30f),
            new Worker(12.6f, 5.8f, 0.18f, 34f),
            new Worker(7.0f, 12.2f, 0.12f, 26f)};
    private static final BlockState PART = Blocks.PACKED_ICE.defaultBlockState();
    /** A part in the hands, as a fraction of "model pixel × 16": 0.3 ≈ 5 model pixels. */
    private static final float PART_SIZE = 0.3f;
    private static final Vector3f UP_LEFT = new Vector3f(3f, 27f, 5f), UP_RIGHT = new Vector3f(-3f, 27f, 5f);
    private static final Vector3f UP_CENTER = new Vector3f(0f, 27.5f, 5f);

    private SnowCrew() {
    }

    private record WorkData(SnowmanPuppet.Pose pose, Vector3f heldAt, float boxSize) {
    }

    private record IdleData(SnowmanPuppet.Pose pose, float yaw) {
    }

    /**
     * pose is in the block frame (0..1). floorY = platform height, work = how much the crew is "at work" (0 = idle,
     * 1 = working), foreman = foreman appearance 0..1, proj = centre of the item projection (where the parts fly).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, float time, long seed, float floorY, float work,
                              float foreman, Vector3f proj) {
        float blend = ease(work);
        for (int i = 0; i < CREW.length; i++) {
            Worker w = CREW[i];
            Vector3f pos = new Vector3f(w.x / 16f, floorY, w.z / 16f);
            float toCenter = (float) Math.toDegrees(Math.atan2(0.5f - pos.x, 0.5f - pos.z));
            float offset = (float) Math.floorMod(seed * 31 + i * 977L, 1000L) / 1000f;
            if (blend <= 0.001f) {
                IdleData id = computeIdle(time, seed, i, toCenter, offset);
                SnowmanPuppet.render(pose, buffers, light, pos, id.yaw, w.scale, SnowmanPuppet.Head.SNOW, id.pose, null);
            } else if (blend >= 0.999f) {
                WorkData wd = computeWorking(pose, buffers, light, time, w, pos, toCenter, offset, proj, 1f);
                SnowmanPuppet.render(pose, buffers, light, pos, toCenter, w.scale, SnowmanPuppet.Head.SNOW, wd.pose,
                        wd.heldAt == null ? null : hp -> {
                            hp.translate((wd.heldAt.x - SnowmanPuppet.HOLD_CENTER.x) / 16f, (wd.heldAt.y - SnowmanPuppet.HOLD_CENTER.y) / 16f,
                                    (wd.heldAt.z - SnowmanPuppet.HOLD_CENTER.z) / 16f);
                            cube(hp, buffers, light, wd.boxSize);
                        });
            } else {
                IdleData id = computeIdle(time, seed, i, toCenter, offset);
                WorkData wd = computeWorking(pose, buffers, light, time, w, pos, toCenter, offset, proj, blend);
                float yaw = Mth.rotLerp(blend, id.yaw, toCenter);
                SnowmanPuppet.Pose p = new SnowmanPuppet.Pose(
                        Mth.lerp(blend, id.pose.bob(), wd.pose.bob()),
                        Mth.lerp(blend, id.pose.squash(), wd.pose.squash()),
                        Mth.lerp(blend, id.pose.headYaw(), wd.pose.headYaw()),
                        Mth.lerp(blend, id.pose.headPitch(), wd.pose.headPitch()),
                        lerp(id.pose.leftHand(), wd.pose.leftHand(), blend),
                        lerp(id.pose.rightHand(), wd.pose.rightHand(), blend));
                float finalSize = wd.boxSize * blend;
                SnowmanPuppet.render(pose, buffers, light, pos, yaw, w.scale, SnowmanPuppet.Head.SNOW, p,
                        wd.heldAt == null || finalSize <= 0.01f ? null : hp -> {
                            hp.translate((wd.heldAt.x - SnowmanPuppet.HOLD_CENTER.x) / 16f, (wd.heldAt.y - SnowmanPuppet.HOLD_CENTER.y) / 16f,
                                    (wd.heldAt.z - SnowmanPuppet.HOLD_CENTER.z) / 16f);
                            cube(hp, buffers, light, finalSize);
                        });
            }
        }
        if (foreman > 0.001f) {
            foreman(pose, buffers, light, time, floorY, foreman);
        }
    }

    private static WorkData computeWorking(PoseStack pose, MultiBufferSource buffers, int light, float time, Worker w, Vector3f pos,
                                           float yaw, float offset, Vector3f proj, float blend) {
        float f = ((time / w.period) + offset) % 1f;
        Vector3f lh, rh, box = null;
        float bob = 0f, squash = 0f, pitch, boxSize = PART_SIZE;
        if (f < 0.15f) {
            // bent down for a part
            float k = f / 0.15f, dip = Mth.sin(k * Mth.PI);
            squash = 0.14f * dip;
            pitch = 25f;
            lh = new Vector3f(SnowmanPuppet.HOLD_LEFT).add(0f, -4f * dip, 0f);
            rh = new Vector3f(SnowmanPuppet.HOLD_RIGHT).add(0f, -4f * dip, 0f);
            if (k > 0.5f) {
                box = new Vector3f(SnowmanPuppet.HOLD_CENTER).add(0f, -4f * dip, 0f);
                boxSize = PART_SIZE * (k - 0.5f) * 2f;
            }
        } else if (f < 0.45f) {
            // lifts it over its head
            float k = ease((f - 0.15f) / 0.3f);
            pitch = Mth.lerp(k, 10f, -30f);
            lh = lerp(SnowmanPuppet.HOLD_LEFT, UP_LEFT, k);
            rh = lerp(SnowmanPuppet.HOLD_RIGHT, UP_RIGHT, k);
            box = lerp(SnowmanPuppet.HOLD_CENTER, UP_CENTER, k);
            bob = 0.6f * k;
        } else if (f < 0.75f) {
            // tossed it: the part flies into the projection, the arms go down
            float k = (f - 0.45f) / 0.3f;
            pitch = -35f;
            lh = lerp(UP_LEFT, SnowmanPuppet.REST_LEFT, ease(k));
            rh = lerp(UP_RIGHT, SnowmanPuppet.REST_RIGHT, ease(k));
            if (blend > 0.25f) {
                flyingPart(pose, buffers, light, pos, yaw, w.scale, proj, k, (blend - 0.25f) / 0.75f);
            }
        } else {
            // pleased: hopped
            float k = (f - 0.75f) / 0.25f;
            bob = 1.6f * Mth.sin(k * Mth.PI);
            pitch = -10f;
            lh = SnowmanPuppet.REST_LEFT;
            rh = SnowmanPuppet.REST_RIGHT;
        }
        return new WorkData(new SnowmanPuppet.Pose(bob, squash, 0f, pitch, lh, rh), box, boxSize);
    }

    /** A part in flight from the raised hands to the projection: in an arc, shrinking. */
    private static void flyingPart(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, float yaw, float scale,
                                   Vector3f proj, float k, float blendScale) {
        float rad = yaw * Mth.DEG_TO_RAD;
        float sx = UP_CENTER.z * Mth.sin(rad), sz = UP_CENTER.z * Mth.cos(rad);
        Vector3f start = new Vector3f(pos.x + sx / 16f * scale, pos.y + UP_CENTER.y / 16f * scale, pos.z + sz / 16f * scale);
        Vector3f at = lerp(start, proj, k).add(0f, 0.08f * Mth.sin(k * Mth.PI), 0f);
        float size = PART_SIZE * scale * (1f - 0.8f * k) * blendScale;
        if (size <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(k * 540f));
        cube(pose, buffers, light, size);
        pose.popPose();
    }

    private static IdleData computeIdle(float time, long seed, int i, float toCenter, float offset) {
        float t = time + offset * 400f;
        long window = (long) Math.floor(t / 120f);
        long h = seed * 6364136223846793005L + i * 1442695040888963407L + window * 0x9E3779B97F4A7C15L;
        int activity = (int) Math.floorMod(h >>> 33, 4L);
        float local = t - window * 120f;
        // a smooth start and end of each "activity"
        float fade = Mth.clamp(Math.min(local, 120f - local) / 15f, 0f, 1f);
        float yaw = toCenter;
        SnowmanPuppet.Pose p;
        switch (activity) {
            case 0 -> {
                // looks around
                yaw = toCenter + 60f * Mth.sin(t * 0.04f) * fade;
                p = new SnowmanPuppet.Pose(0f, 0f, 35f * Mth.sin(t * 0.09f) * fade, -5f, SnowmanPuppet.REST_LEFT, SnowmanPuppet.REST_RIGHT);
            }
            case 1 -> {
                // hops in a circle on the spot
                yaw = toCenter + local * 3f * fade;
                float hop = Math.abs(Mth.sin(t * 0.3f)) * fade;
                p = new SnowmanPuppet.Pose(2.2f * hop, 0.12f * (1f - hop) * fade, 0f, -10f,
                        new Vector3f(9f, 18f + 6f * hop, 2f), new Vector3f(-9f, 18f + 6f * hop, 2f));
            }
            case 2 -> {
                // turned away from the centre and waves
                yaw = toCenter + 180f * fade;
                float wave = Mth.sin(t * 0.5f);
                p = new SnowmanPuppet.Pose(0f, 0f, 0f, -15f * fade, SnowmanPuppet.REST_LEFT,
                        lerp(SnowmanPuppet.REST_RIGHT, new Vector3f(-9f + 2f * wave, 27f, 3f + 3f * wave), fade));
            }
            default -> {
                // dozes: the head droops, it breathes
                float nod = 0.5f + 0.5f * Mth.sin(t * 0.05f);
                p = new SnowmanPuppet.Pose(0f, 0.04f * Mth.sin(t * 0.08f) * fade, 0f, (20f + 12f * nod) * fade,
                        SnowmanPuppet.REST_LEFT, SnowmanPuppet.REST_RIGHT);
            }
        }
        return new IdleData(p, yaw);
    }

    /** The foreman with an atlant head at the front edge: waves, nods, hops to the beat, urging the crew on. */
    private static void foreman(PoseStack pose, MultiBufferSource buffers, int light, float time, float floorY, float appear) {
        float pop = easeOutBack(appear);
        float beat = time / 14f * Mth.TWO_PI;
        float pump = 0.5f + 0.5f * Mth.sin(beat);
        Vector3f right = lerp(new Vector3f(-8f, 17f, 7f), new Vector3f(-6f, 28f, 5f), pump);
        Vector3f left = new Vector3f(7.5f, 12f, 1.5f);
        // once every few beats it points at the projection with both hands: "there, there!"
        if (Mth.sin(time * 0.021f) > 0.85f) {
            left = new Vector3f(3f, 24f, 9f);
            right = new Vector3f(-3f, 24f, 9f);
        }
        SnowmanPuppet.Pose p = new SnowmanPuppet.Pose(0.8f * Math.abs(Mth.sin(beat * 0.5f)), 0f, 12f * Mth.sin(time * 0.05f),
                8f * Mth.sin(beat), left, right);
        SnowmanPuppet.render(pose, buffers, light, new Vector3f(0.5f, floorY, 14.2f / 16f), 180f, 0.2f * pop,
                SnowmanPuppet.Head.ATLANT, p, null);
    }

    private static void cube(PoseStack pose, MultiBufferSource buffers, int light, float size) {
        if (size <= 0.001f) {
            return;
        }
        pose.pushPose();
        pose.scale(size, size, size);
        pose.translate(-0.5f, -0.5f, -0.5f);
        com.golemlogix.client.render.BerBuffers.block(buffers, PART, pose, light);
        pose.popPose();
    }

    private static Vector3f lerp(Vector3f a, Vector3f b, float k) {
        return new Vector3f(a).lerp(b, k);
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0f, 1f);
        return k * k * (3f - 2f * k);
    }

    private static float easeOutBack(float k) {
        k = Mth.clamp(k, 0f, 1f);
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (k - 1f) * (k - 1f) * (k - 1f) + c1 * (k - 1f) * (k - 1f);
    }
}
