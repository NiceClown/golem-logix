package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.golemlogix.client.JarWholeRenderer.FWD_EAST;
import static com.golemlogix.client.JarWholeRenderer.FWD_WEST;
import static com.golemlogix.client.JarWholeRenderer.REACTOR_TOP;
import static com.golemlogix.client.JarWholeRenderer.UP;
import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.BRICK;
import static com.golemlogix.client.MachineParts.BRICK_TINT;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.CRYO;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.DIM_BRASS;
import static com.golemlogix.client.MachineParts.GAUGE;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.RUNES;
import static com.golemlogix.client.MachineParts.SNOW;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;

/**
 * Static mesh baking and animated rendering of the 3x3 Antigravity Reactor ({@link JarWholeRenderer}): a heavy rune-carved
 * brick base, counter-rotating toothed collars and levitating gimbal rings, a floating Cryo-Source sphere with rising icy
 * shards, and a crew of kneeling iron golems, wheel-turning copper golems, and snowball-tossing snow golems.
 */
final class AntigravityReactorRenderer {
    private static final ResourceLocation IRON_GOLEM_TEX =
            ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    private static final float CENTER_X = 24f, CENTER_Z = 24f, SPHERE_Y = 60f;
    private static final float GOLEM_SCALE = 0.36f;
    private static final float SNOWMAN_SCALE = 0.42f;
    private static final int SHARD_COUNT = 10;

    private static final float[][] BASE_ICICLES = {{3f, 2.2f}, {8f, 3f}, {14f, 1.6f}, {21f, 2.6f}, {30f, 1.8f}, {38f, 3.2f}, {44f, 2f}};
    private static final float[][] SNOW_VENTS = {{39f, 4f}, {5f, 39f}};
    private static final float[] SIDE_PIPE_BRACKETS_X = {6f, 16f, 32f, 42f};

    private static final Vector3f THROW_TARGET = new Vector3f(-8f, 19f, 6f);
    private static final Vector3f SCRATCH_POS = new Vector3f();
    private static final Vector3f SCRATCH_HAND = new Vector3f();
    private static final Vector3f SCRATCH_OFF = new Vector3f();
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();

    private AntigravityReactorRenderer() {
    }

    /** Bakes the static 3x3 brick base, recessed side panels, gauges, piping, wheel stands, and vent snowdrifts. */
    static void bakeStatic(VertexConsumer vc, PoseStack pose, boolean detail) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        float top = REACTOR_TOP;
        // base: dark foot, brick core, upper rim
        box(vc, p, 0.3f, 0, 0.3f, 47.7f, 1.8f, 47.7f, DARK, WHITE, light);
        box(vc, p, 0.8f, 1.8f, 0.8f, 47.2f, 29.6f, 47.2f, BRICK, BRICK_TINT, light);
        box(vc, p, 0.4f, 29.6f, 0.4f, 47.6f, top, 47.6f, BRICK, 0xFFE0B8A8, light);
        bakeSides(vc, pose, detail, light);
        JarWholeRenderer.cornerSnow(vc, p, 48f, top, detail, light);
        box(vc, p, 11f, top, 11f, 37f, top + 0.3f, 37f, DARK, WHITE, light);
        if (!detail) {
            return;
        }
        for (float[] i : BASE_ICICLES) {
            box(vc, p, i[0], 29.6f - i[1], 0.2f, i[0] + 0.7f, 29.6f, 0.9f, ICE, WHITE, light);
        }
        // hand-wheel stands on three sides of the top deck
        box(vc, p, 22f, top, 4.4f, 26f, top + 0.6f, 8f, BRASS, DIM_BRASS, light);
        box(vc, p, 23.4f, top, 5.6f, 24.6f, top + 5f, 6.8f, COPPER, WHITE, light);
        box(vc, p, 40f, top, 22f, 43.6f, top + 0.6f, 26f, BRASS, DIM_BRASS, light);
        box(vc, p, 41.2f, top, 23.4f, 42.4f, top + 5f, 24.6f, COPPER, WHITE, light);
        box(vc, p, 4.4f, top, 22f, 8f, top + 0.6f, 26f, BRASS, DIM_BRASS, light);
        box(vc, p, 5.6f, top, 23.4f, 6.8f, top + 5f, 24.6f, COPPER, WHITE, light);
        // snowdrifts where the two snow golems stand
        for (float[] v : SNOW_VENTS) {
            box(vc, p, v[0] - 3f, top, v[1] - 2.6f, v[0] + 3f, top + 0.5f, v[1] + 2.6f, SNOW, WHITE, light);
            box(vc, p, v[0] - 1.6f, top + 0.5f, v[1] - 3f, v[0] + 1.6f, top + 0.9f, v[1] + 3f, SNOW, WHITE, light);
        }
    }

    /** Bakes the four recessed side rune panels and the glowing rune floor of the central well. */
    static void bakeRunes(VertexConsumer vc, PoseStack pose) {
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(side * 90f), 1.5f, 0f, 1.5f);
            fitWall(vc, pose.last(), 13.4f, 15f, 34.6f, 25.6f, 0.25f, RUNES, WHITE, 0);
            pose.popPose();
        }
        box(vc, pose.last(), 13f, REACTOR_TOP + 0.5f - 0.02f, 13f, 35f, REACTOR_TOP + 0.5f, 35f, RUNES, WHITE, 0);
    }

    /** Renders the reactor: base, runes, rotating rings/gimbal, hand wheels, crew/snowballs, floating sphere, and shards. */
    static void render(PoseStack pose, MultiBufferSource buffers, JarWholeRenderer.Meshes meshes,
                       CryoJarRenderer.IronGolemParts ironGolem, int light, float time, float fill,
                       boolean on, boolean crew, boolean detail) {
        RenderType solid = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        int runeLight = on ? LightTexture.FULL_BRIGHT : light;
        int rune = MachineParts.cyanRuneColor(time, on);
        (detail ? meshes.reactorNear() : meshes.reactorFar()).draw(buffers, solid, pose.last(), light, 0);
        meshes.reactorRunes().draw(buffers, solid, pose.last(), runeLight, rune);

        renderRingsAndGimbal(pose, buffers, meshes, solid, light, runeLight, rune, time, on, detail);

        float turn = MachineParts.valveTurnAngle(time, on);
        if (detail) {
            renderHandWheels(buffers.getBuffer(solid), pose, light, time, turn, on);
        }
        if (crew) {
            renderCrew(pose, buffers, ironGolem, light, time, turn, on);
            renderFrostSnowballs(buffers.getBuffer(solid), pose.last(), time, on, light);
        }
        renderSphereAndShards(pose, buffers, meshes, solid, light, time, fill, on);
    }

    private static void bakeSides(VertexConsumer vc, PoseStack pose, boolean detail, int light) {
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(side * 90f), 1.5f, 0f, 1.5f);
            PoseStack.Pose p = pose.last();
            box(vc, p, 12f, 13.6f, 0.5f, 36f, 27f, 0.82f, DARK, WHITE, light);
            box(vc, p, 11.4f, 13f, 0.3f, 36.6f, 13.6f, 0.82f, BRASS, DIM_BRASS, light);
            box(vc, p, 11.4f, 27f, 0.3f, 36.6f, 27.6f, 0.82f, BRASS, DIM_BRASS, light);
            if (detail) {
                fitWall(vc, p, 3f, 18f, 9f, 24f, 0.45f, GAUGE, WHITE, light);
                box(vc, p, 4f, 2.4f, 0.2f, 44f, 4.6f, 0.8f, COPPER, WHITE, light);
                for (float x : SIDE_PIPE_BRACKETS_X) {
                    box(vc, p, x - 0.8f, 1.8f, 0f, x + 0.8f, 5.2f, 0.9f, BRASS, DIM_BRASS, light);
                }
                box(vc, p, 40.4f, 4.6f, 0.2f, 42.6f, 12f, 0.8f, COPPER, WHITE, light);
            }
            pose.popPose();
        }
    }

    private static void renderRingsAndGimbal(PoseStack pose, MultiBufferSource buffers, JarWholeRenderer.Meshes meshes,
                                             RenderType solid, int light, int runeLight, int rune, float time, boolean on, boolean detail) {
        float spin = on ? time : 0f;
        if (detail) {
            // toothed collar round the well
            pose.pushPose();
            pose.translate(CENTER_X * PIXEL, (REACTOR_TOP + 0.9f) * PIXEL, CENTER_Z * PIXEL);
            pose.mulPose(Axis.YP.rotationDegrees(-spin * 0.25f));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            meshes.collar().draw(buffers, solid, pose.last(), light, 0);
            pose.popPose();
        }
        // toothed ring low over the well, held up by the two kneeling iron golems
        pose.pushPose();
        pose.translate(CENTER_X * PIXEL, 39f * PIXEL, CENTER_Z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(spin * 0.5f));
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        meshes.lowRing().draw(buffers, solid, pose.last(), light, 0);
        pose.popPose();

        // levitating three-axis gimbal: vertical outer ring, horizontal brass ring, tumbling inner copper ring
        pose.pushPose();
        pose.translate(CENTER_X * PIXEL, SPHERE_Y * PIXEL, CENTER_Z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(spin * 0.35f));
        meshes.outerRing().draw(buffers, solid, pose.last(), light, 0);
        meshes.outerRunes().draw(buffers, solid, pose.last(), runeLight, rune);
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        (detail ? meshes.levelRing() : meshes.levelRingFar()).draw(buffers, solid, pose.last(), light, 0);
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7f + 35f));
        meshes.innerRing().draw(buffers, solid, pose.last(), light, 0);
        pose.popPose();
    }

    private static void renderHandWheels(VertexConsumer vc, PoseStack pose, int light, float time, float turn, boolean on) {
        MachineParts.wheelX(vc, pose, 22.8f, REACTOR_TOP + 4.2f, 6.2f, 2.2f, turn, light);
        MachineParts.wheelX(vc, pose, 40.6f, REACTOR_TOP + 4.2f, 24f, 2.2f, turn, light);
        MachineParts.wheelX(vc, pose, 7.4f, REACTOR_TOP + 4.2f, 24f, 2.2f, -turn, light);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(side * 90f), 1.5f, 0f, 1.5f);
            MachineParts.wheelZ(vc, pose, 41.5f, 13f, 0.05f, 2.4f, MachineParts.valveTurnAngle(time + side * 23f, on), light);
            pose.popPose();
        }
    }

    private static void renderSphereAndShards(PoseStack pose, MultiBufferSource buffers, JarWholeRenderer.Meshes meshes,
                                              RenderType solid, int light, float time, float fill, boolean on) {
        // levitating Source sphere (grows with fill, freezes to ice when empty)
        pose.pushPose();
        pose.translate(CENTER_X * PIXEL, (SPHERE_Y + 0.8f * Mth.sin(time * 0.03f)) * PIXEL, CENTER_Z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.6f));
        float radius = on ? 4f + 7.4f * (float) Math.cbrt(fill) : 3f;
        pose.scale(radius, radius, radius);
        (on ? meshes.ballCryo() : meshes.ballIce()).draw(buffers, solid, pose.last(), on ? LightTexture.FULL_BRIGHT : light, 0);
        pose.popPose();

        if (!on) {
            return;
        }
        // drifting icy droplets rising spirally around the sphere
        VertexConsumer tv = buffers.getBuffer(JarWholeRenderer.clearType(JarWholeRenderer.shaderPackActive()));
        PoseStack.Pose t = pose.last();
        for (int i = 0; i < SHARD_COUNT; i++) {
            float k = (time * 0.006f + i * 0.137f) % 1f;
            float a = i * 2.399f + time * 0.012f;
            float r = 9f + 8f * ((i * 0.37f) % 1f);
            float size = 0.35f + 0.25f * (i % 3);
            float x = CENTER_X + r * Mth.cos(a), z = CENTER_Z + r * Mth.sin(a), y = REACTOR_TOP + 6f + k * 52f;
            ResourceLocation drop = i % 2 == 0 ? CRYO : ICE;
            box(tv, t, x - size, y - size, z - size, x + size, y + size, z + size, drop, 0xE0FFFFFF, LightTexture.FULL_BRIGHT);
            box(tv, t, x - size / 2f, y + size, z - size / 2f, x + size / 2f, y + size * 2f, z + size / 2f, drop, 0xE0FFFFFF, LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderCrew(PoseStack pose, MultiBufferSource buffers, CryoJarRenderer.IronGolemParts golem,
                                   int light, float time, float turn, boolean on) {
        // two kneeling iron golems supporting the toothed ring on their shoulders
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 11.2f : 36.8f, z = i == 0 ? 11.2f : 36.8f;
            float yaw = (float) Math.toDegrees(Math.atan2(x - CENTER_X, z - CENTER_Z));
            renderIronGolem(pose, buffers, golem, x, REACTOR_TOP, z, yaw, GOLEM_SCALE, time + i * 17f, on, light);
        }
        // three copper golems working the top hand wheels
        float reach = on ? -1.15f + 0.12f * Mth.sin(turn * Mth.DEG_TO_RAD) : -0.9f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(time * 0.017f));
        SCRATCH_POS.set(18.4f * PIXEL, REACTOR_TOP * PIXEL, 6.2f * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_EAST, UP,
                GOLEM_SCALE, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.08f), 0, null, arms);
        SCRATCH_POS.set(36.2f * PIXEL, REACTOR_TOP * PIXEL, 24f * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_EAST, UP,
                GOLEM_SCALE, new CopperGolemPuppet.Anim(time * 0.09f, false, false, 0.08f), 1, null, arms);
        SCRATCH_POS.set(11.8f * PIXEL, REACTOR_TOP * PIXEL, 24f * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_WEST, UP,
                GOLEM_SCALE, new CopperGolemPuppet.Anim(time * 0.11f, false, false, 0.08f), 2, null, arms);
        // two snow golems on opposite corners tossing frost at the sphere
        for (int i = 0; i < 2; i++) {
            float x = SNOW_VENTS[i][0], z = SNOW_VENTS[i][1];
            float yaw = (float) Math.toDegrees(Math.atan2(CENTER_X - x, CENTER_Z - z));
            float k = throwPhase(time, i);
            SCRATCH_HAND.set(SnowmanPuppet.REST_RIGHT).lerp(THROW_TARGET, k);
            SnowmanPuppet.Pose sp = new SnowmanPuppet.Pose(0.4f * k, 0f, 6f * Mth.sin(time * 0.02f + i), 4f, SnowmanPuppet.REST_LEFT, SCRATCH_HAND);
            SCRATCH_POS.set(x * PIXEL, (REACTOR_TOP + 0.9f) * PIXEL, z * PIXEL);
            SnowmanPuppet.render(pose, buffers, light, SCRATCH_POS, yaw, SNOWMAN_SCALE, SnowmanPuppet.Head.PUMPKIN, sp, null);
        }
    }

    /** 0..1: how far a snow golem's throwing arm is extended at this moment. */
    private static float throwPhase(float time, int who) {
        float c = (time * 0.012f + who * 0.5f) % 1f;
        return c < 0.15f ? Mth.sin(c / 0.15f * Mth.PI) : 0f;
    }

    /** Snowballs arcing from the snow golems' hands into the floating sphere. */
    private static void renderFrostSnowballs(VertexConsumer vc, PoseStack.Pose p, float time, boolean on, int light) {
        if (!on) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            float x = SNOW_VENTS[i][0], z = SNOW_VENTS[i][1];
            float c = (time * 0.012f + i * 0.5f) % 1f;
            if (c < 0.1f || c > 0.42f) {
                continue;
            }
            float k = (c - 0.1f) / 0.32f;
            float bx = Mth.lerp(k, x, CENTER_X);
            float bz = Mth.lerp(k, z, CENTER_Z);
            float by = Mth.lerp(k, REACTOR_TOP + 8f, SPHERE_Y) + 6f * Mth.sin(k * Mth.PI);
            box(vc, p, bx - 0.7f, by - 0.7f, bz - 0.7f, bx + 0.7f, by + 0.7f, bz + 0.7f, SNOW, WHITE, light);
        }
    }

    /** Kneeling iron golem with arms braced upwards against the toothed ring. */
    private static void renderIronGolem(PoseStack pose, MultiBufferSource buffers, CryoJarRenderer.IronGolemParts golem,
                                        float x, float y, float z, float yaw, float scale, float time, boolean on, int light) {
        float strain = on ? 0.05f * Mth.sin(time * 0.05f) : 0f;
        poseArm(golem.rightArm(), -11f, -9.2f, -6.2f, 1.6f, 0.42f, 0f, 1.74f + strain);
        poseArm(golem.leftArm(), 11f, 9.2f, -6.2f, 1.6f, 0.42f, 0f, -1.74f - strain);
        golem.rightLeg().xRot = -1.45f;
        golem.leftLeg().xRot = 1.25f;
        golem.head().xRot = 0.55f + strain;
        golem.head().yRot = 0f;
        pose.pushPose();
        pose.translate(x * PIXEL, (y - 4.4f) * PIXEL, z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
        pose.scale(-scale, -scale, scale);
        pose.translate(0f, -1.501f, 0f);
        golem.root().render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(IRON_GOLEM_TEX)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Rotates an iron golem's arm around its own shoulder {@code (boxX, 0, 0)} and anchors that shoulder at {@code (sx, sy, sz)}. */
    private static void poseArm(ModelPart arm, float boxX, float sx, float sy, float sz, float xRot, float yRot, float zRot) {
        arm.xRot = xRot;
        arm.yRot = yRot;
        arm.zRot = zRot;
        Vector3f off = SCRATCH_QUAT.rotationZYX(zRot, yRot, xRot).transform(SCRATCH_OFF.set(boxX, 0f, 0f));
        arm.x = sx - off.x;
        arm.y = sy - off.y;
        arm.z = sz - off.z;
    }
}
