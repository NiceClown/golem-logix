package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Vector3f;

import static com.golemlogix.client.JarWholeRenderer.FWD_EAST;
import static com.golemlogix.client.JarWholeRenderer.FWD_SOUTH;
import static com.golemlogix.client.JarWholeRenderer.FWD_WEST;
import static com.golemlogix.client.JarWholeRenderer.UP;
import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.BRICK;
import static com.golemlogix.client.MachineParts.BRICK_TINT;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.CRYO;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.DIM_BRASS;
import static com.golemlogix.client.MachineParts.FRAME;
import static com.golemlogix.client.MachineParts.GAUGE;
import static com.golemlogix.client.MachineParts.GLASS;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.LIGHT_BRASS;
import static com.golemlogix.client.MachineParts.SNOW;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.limb;
import static com.golemlogix.client.render.PixelMesh.smoothstep;

/**
 * Static mesh baking and animated rendering of the 2x2 Giga Jar ({@link JarWholeRenderer}): a large glass tank on a brick
 * rune plinth topped by a snowy deck, a lattice samson post, a nodding walking-beam pumpjack with crank/counterweight/piston,
 * and three copper golems tending the valve, gauge, and ladder.
 */
final class GigaJarRenderer {
    private static final float CRANK_RADIUS = 3.2f;
    private static final float BEAM_PIVOT_X = 16f, BEAM_PIVOT_Y = 60f;
    private static final float TAIL_ARM = 12.5f, HEAD_ARM = 12f;
    private static final float CRANK_X = 25.4f, CRANK_Y = 41f, CRANK_Z = 20.2f;
    private static final float TANK_BOTTOM = 8.26f, TANK_TOP = 26.74f;
    private static final float GOLEM_SCALE = 0.36f;

    private static final float[][] TANK_CORNERS = {{1.6f, 1.6f}, {28.9f, 1.6f}, {1.6f, 28.9f}, {28.9f, 28.9f}};
    private static final float[][] DECK_SNOW = {{1.2f, 1.2f, 7f, 4f}, {25.8f, 1.2f, 30.8f, 3.4f}, {1.2f, 26f, 5f, 30.8f}, {26.5f, 27f, 30.8f, 30.8f}};
    private static final float[][] DECK_ICICLES = {{3f, 1.6f}, {6.5f, 2.4f}, {11f, 1.3f}, {18f, 2f}, {23.4f, 1.5f}, {28f, 2.6f}};
    private static final float[][] SAMSON_FEET = {{9f, 9f}, {23f, 9f}, {23f, 23f}, {9f, 23f}};
    private static final float[][] SAMSON_TOPS = {{14f, 14f}, {18f, 14f}, {18f, 18f}, {14f, 18f}};
    private static final float[] SAMSON_LEVELS = {0f, 0.3f, 0.58f, 0.82f};

    private static final Vector3f SCRATCH_POS = new Vector3f();

    private GigaJarRenderer() {
    }

    /** Bakes the static Giga Jar structure (plinth, frame, snowy deck, samson post, gearbox, and optional near details). */
    static void bakeStatic(VertexConsumer vc, PoseStack pose, boolean detail) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        bakePlinthAndTankFrame(vc, pose, p, detail, light);
        bakeSamsonPost(vc, pose, p, detail, light);
        bakeGearboxAndControls(vc, p, detail, light);
    }

    /** Renders the Giga Jar: baked structure, glowing runes, pumpjack mechanism, crew, Source fluid, and glass. */
    static void render(PoseStack pose, MultiBufferSource buffers, JarWholeRenderer.Meshes meshes, int light,
                       float time, float fill, boolean on, boolean crew, boolean detail, float ambient) {
        RenderType solid = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        (detail ? meshes.gigaNear() : meshes.gigaFar()).draw(buffers, solid, pose.last(), light, 0);
        meshes.gigaRunes().draw(buffers, solid, pose.last(), on ? LightTexture.FULL_BRIGHT : light, MachineParts.cyanRuneColor(time, on));

        VertexConsumer vc = buffers.getBuffer(solid);
        renderPumpjack(vc, pose, light, time, on);

        float turn = MachineParts.valveTurnAngle(time, on);
        if (detail) {
            MachineParts.wheelX(vc, pose, 3f, 35.8f, 3.6f, 2.2f, turn, light);
        }
        if (crew) {
            renderCrew(pose, buffers, light, time, turn, on);
        }
        renderFluidAndGlass(pose, buffers, fill, ambient);
    }

    private static void bakePlinthAndTankFrame(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, boolean detail, int light) {
        // plinth
        box(vc, p, 0.4f, 0, 0.4f, 31.6f, 1.4f, 31.6f, DARK, WHITE, light);
        box(vc, p, 0.8f, 1.4f, 0.8f, 31.2f, 6.2f, 31.2f, BRICK, BRICK_TINT, light);
        box(vc, p, 0.5f, 6.2f, 0.5f, 31.5f, 7.4f, 31.5f, DARK, WHITE, light);
        JarWholeRenderer.cornerSnow(vc, p, 32f, 7.4f, detail, light);

        // tank frame: four corner posts, four mid-side posts, bottom ring
        for (float[] c : TANK_CORNERS) {
            box(vc, p, c[0], 7.4f, c[1], c[0] + 1.5f, 27.6f, c[1] + 1.5f, FRAME, WHITE, light);
        }
        box(vc, p, 15.6f, 7.4f, 1.8f, 16.4f, 27.6f, 2.6f, FRAME, WHITE, light);
        box(vc, p, 15.6f, 7.4f, 29.4f, 16.4f, 27.6f, 30.2f, FRAME, WHITE, light);
        box(vc, p, 1.8f, 7.4f, 15.6f, 2.6f, 27.6f, 16.4f, FRAME, WHITE, light);
        box(vc, p, 29.4f, 7.4f, 15.6f, 30.2f, 27.6f, 16.4f, FRAME, WHITE, light);
        box(vc, p, 1.4f, 7.4f, 1.4f, 30.6f, 8.2f, 30.6f, FRAME, WHITE, light);

        // upper deck with lip, snow patches, icicles, ladder and catwalks
        box(vc, p, 1.2f, 26.8f, 1.2f, 30.8f, 28.4f, 30.8f, FRAME, WHITE, light);
        box(vc, p, 1.8f, 28.4f, 1.8f, 30.2f, 30.8f, 30.2f, BRICK, BRICK_TINT, light);
        box(vc, p, 1.2f, 30.8f, 1.2f, 30.8f, 31.6f, 30.8f, BRICK, 0xFFE0B8A8, light);
        for (float[] s : DECK_SNOW) {
            box(vc, p, s[0], 31.6f, s[1], s[2], 32.2f, s[3], SNOW, WHITE, light);
        }
        if (detail) {
            for (float[] i : DECK_ICICLES) {
                box(vc, p, i[0], 26.8f - i[1], 1.0f, i[0] + 0.6f, 26.8f, 1.6f, ICE, WHITE, light);
            }
            // ladder up the right side from plinth to catwalk, plus a valve catwalk on the left
            JarWholeRenderer.ladder(vc, p, 31.2f, 7.4f, 30.8f, 20f, 25f, light);
            JarWholeRenderer.catwalk(vc, pose, 30.4f, 36.4f, 18.6f, 26.4f, 30.8f, 30.4f, 25.4f, light);
            JarWholeRenderer.catwalk(vc, pose, -4.4f, 1.6f, 0.6f, 7f, 30.8f, 1.6f, 25.4f, light);
        }
    }

    /** Lattice samson post: four tapered brass legs, horizontal girders, cross-braces, and top saddle bearing. */
    private static void bakeSamsonPost(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, boolean detail, int light) {
        float y0 = 31.6f, y1 = 56f;
        for (int i = 0; i < 4; i++) {
            box(vc, p, SAMSON_FEET[i][0] - 1.4f, y0, SAMSON_FEET[i][1] - 1.4f,
                    SAMSON_FEET[i][0] + 1.4f, y0 + 0.8f, SAMSON_FEET[i][1] + 1.4f, DARK, WHITE, light);
            limb(vc, pose, SAMSON_FEET[i][0], y0, SAMSON_FEET[i][1], SAMSON_TOPS[i][0], y1, SAMSON_TOPS[i][1], 1.3f, BRASS, light);
        }
        for (int l = 0; l < SAMSON_LEVELS.length - 1; l++) {
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                Vector3f a = lerpLeg(SAMSON_FEET[i], SAMSON_TOPS[i], y0, y1, SAMSON_LEVELS[l]);
                Vector3f b = lerpLeg(SAMSON_FEET[j], SAMSON_TOPS[j], y0, y1, SAMSON_LEVELS[l]);
                Vector3f c = lerpLeg(SAMSON_FEET[i], SAMSON_TOPS[i], y0, y1, SAMSON_LEVELS[l + 1]);
                Vector3f d = lerpLeg(SAMSON_FEET[j], SAMSON_TOPS[j], y0, y1, SAMSON_LEVELS[l + 1]);
                limb(vc, pose, c, d, 0.9f, FRAME, light);
                if (detail) {
                    limb(vc, pose, a, d, 0.6f, FRAME, light);
                    limb(vc, pose, b, c, 0.6f, FRAME, light);
                }
            }
        }
        box(vc, p, 13.2f, 56f, 13.2f, 18.8f, 57.6f, 18.8f, FRAME, WHITE, light);
        box(vc, p, 15f, 57.6f, 13.6f, 17f, 59.4f, 18.4f, BRASS, LIGHT_BRASS, light);
    }

    private static void bakeGearboxAndControls(VertexConsumer vc, PoseStack.Pose p, boolean detail, int light) {
        // gearbox at back right and rod stuffing box on the deck
        box(vc, p, 22.4f, 31.6f, 21f, 29.4f, 36.2f, 28.4f, FRAME, WHITE, light);
        box(vc, p, 22f, 36.2f, 20.6f, 29.8f, 37f, 28.8f, BRASS, DIM_BRASS, light);
        box(vc, p, 24f, 37f, 20.8f, 26.8f, 43f, 22.4f, FRAME, WHITE, light);
        box(vc, p, 0.6f, 31.6f, 14f, 4.6f, 33.2f, 18f, BRASS, DIM_BRASS, light);
        if (detail) {
            // valve stand at deck front left and gauge stand for the second golem
            box(vc, p, 3.6f, 31.6f, 3f, 4.8f, 36.6f, 4.2f, COPPER, WHITE, light);
            box(vc, p, 2.8f, 31.6f, 2.2f, 5.6f, 32.2f, 5f, BRASS, DIM_BRASS, light);
            box(vc, p, 21.6f, 31.6f, 8f, 27.4f, 38f, 9f, FRAME, WHITE, light);
            fitWall(vc, p, 22.2f, 32.6f, 26.8f, 37.2f, 7.7f, GAUGE, WHITE, light);
        }
    }

    /** Moving pumpjack parts: crank wheel, counterweight, pitman arm, nodding walking beam with horse head, rod, and piston. */
    private static void renderPumpjack(VertexConsumer vc, PoseStack pose, int light, float time, boolean on) {
        PoseStack.Pose p = pose.last();
        float crank = on ? time * 0.075f : 0.6f;
        float tailRise = CRANK_RADIUS * Mth.sin(crank);
        float beamAngle = (float) Math.atan2(tailRise, TAIL_ARM);

        // crank wheel and brass counterweight lobe on the front of the gearbox
        MachineParts.wheelZ(vc, pose, CRANK_X, CRANK_Y, CRANK_Z, 4f, crank * Mth.RAD_TO_DEG, light);
        pose.pushPose();
        pose.translate(CRANK_X * PIXEL, CRANK_Y * PIXEL, (CRANK_Z - 1f) * PIXEL);
        pose.mulPose(Axis.ZP.rotation(crank));
        PoseStack.Pose cw = pose.last();
        box(vc, cw, -4.2f, -1.1f, -0.5f, 0f, 1.1f, 0.5f, BRASS, WHITE, light);
        box(vc, cw, -7.6f, -3f, -0.8f, -4.2f, 3f, 0.8f, BRASS, LIGHT_BRASS, light);
        box(vc, cw, -8.6f, -2f, -0.8f, -7.6f, 2f, 0.8f, BRASS, WHITE, light);
        pose.popPose();

        // pitman arm linking crank pin to walking beam tail
        float pinX = CRANK_X + CRANK_RADIUS * Mth.cos(crank);
        float pinY = CRANK_Y + CRANK_RADIUS * Mth.sin(crank);
        float pinZ = CRANK_Z - 0.6f;
        float tailX = BEAM_PIVOT_X + TAIL_ARM * Mth.cos(beamAngle);
        float tailY = BEAM_PIVOT_Y + TAIL_ARM * Mth.sin(beamAngle);
        limb(vc, pose, pinX, pinY, pinZ, tailX, tailY, 19.6f, 0.9f, BRASS, light);
        box(vc, p, pinX - 0.7f, pinY - 0.7f, pinZ - 0.9f, pinX + 0.7f, pinY + 0.7f, pinZ + 0.5f, DARK, WHITE, light);

        // walking beam, snowy ridge, saddle hub, and curved brass horse head
        pose.pushPose();
        pose.translate(BEAM_PIVOT_X * PIXEL, BEAM_PIVOT_Y * PIXEL, 16f * PIXEL);
        pose.mulPose(Axis.ZP.rotation(beamAngle));
        PoseStack.Pose b = pose.last();
        box(vc, b, -HEAD_ARM + 1f, -1.2f, -1.7f, TAIL_ARM + 1f, 1.2f, 1.7f, FRAME, WHITE, light);
        box(vc, b, -HEAD_ARM + 1f, 1.2f, -1.2f, TAIL_ARM + 1f, 1.7f, 1.2f, SNOW, WHITE, light);
        box(vc, b, -1.2f, -1.6f, -2f, 1.2f, 1.6f, 2f, BRASS, LIGHT_BRASS, light);
        box(vc, b, TAIL_ARM - 1f, -1.6f, -2.8f, TAIL_ARM + 1.4f, 1.6f, 3.6f, BRASS, WHITE, light);
        for (int i = 0; i < 7; i++) {
            float k = i / 6f * 2f - 1f;
            float x = -HEAD_ARM - 1.4f * Mth.sqrt(Math.max(0f, 1f - k * k)) - 0.3f;
            box(vc, b, x - 0.9f, k * 5.6f - 0.9f, -1.5f, -HEAD_ARM + 1.2f, k * 5.6f + 0.9f, 1.5f,
                    BRASS, i % 3 == 0 ? LIGHT_BRASS : WHITE, light);
        }
        pose.popPose();

        // polished rod and submerged piston hanging straight down from the horse head into the tank
        float rodTop = BEAM_PIVOT_Y - HEAD_ARM * Mth.sin(beamAngle) - 5.4f;
        float rodX = BEAM_PIVOT_X - HEAD_ARM - 1.4f;
        box(vc, p, rodX - 0.45f, rodTop - 44f, 15.55f, rodX + 0.45f, rodTop, 16.45f, BRASS, LIGHT_BRASS, light);
        box(vc, p, rodX - 2.2f, rodTop - 47.4f, 13.8f, rodX + 2.2f, rodTop - 44f, 18.2f, FRAME, WHITE, light);
    }

    /** Three copper golems: valve tender on the left catwalk, gauge watcher on the deck, and ladder climber on the right. */
    private static void renderCrew(PoseStack pose, MultiBufferSource buffers, int light, float time, float turn, boolean on) {
        // at the valve, facing it
        float reach = on ? -1.15f + 0.12f * Mth.sin(turn * Mth.DEG_TO_RAD) : -0.9f;
        SCRATCH_POS.set(-1f * PIXEL, 31.4f * PIXEL, 3.6f * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_EAST, UP,
                GOLEM_SCALE, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.08f), 1, null,
                new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(time * 0.017f)));
        // at the gauge, looking it over and scratching its antenna
        float look = 0.25f * Mth.sin(time * 0.023f);
        SCRATCH_POS.set(24.5f * PIXEL, 31.6f * PIXEL, 3.2f * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_SOUTH, UP,
                GOLEM_SCALE, new CopperGolemPuppet.Anim(time * 0.08f, false, false, 0.03f), 2, null,
                new CopperGolemPuppet.Arms(-0.5f + 0.2f * Mth.sin(time * 0.05f), 0.15f, -0.2f, -0.1f, look));
        // on the ladder: climbs up, pauses, climbs down, pauses
        float cycle = (time * 0.0035f) % 1f;
        float k = cycle < 0.4f ? cycle / 0.4f : cycle < 0.5f ? 1f : cycle < 0.9f ? 1f - (cycle - 0.5f) / 0.4f : 0f;
        float climb = smoothstep(k);
        boolean moving = cycle < 0.4f || (cycle > 0.5f && cycle < 0.9f);
        float hands = moving ? 0.35f * Mth.sin(time * 0.35f) : 0f;
        SCRATCH_POS.set(35.2f * PIXEL, (7.4f + 17f * climb) * PIXEL, 22.5f * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_WEST, UP,
                GOLEM_SCALE, new CopperGolemPuppet.Anim(time * 0.15f, false, false, 0f), 0, null,
                new CopperGolemPuppet.Arms(-1.9f + hands, 0.1f, -1.9f - hands, -0.1f, 0f));
    }

    /** Translucent Source column and outer glass (glass skipped under shader packs, see {@link JarWholeRenderer}). */
    private static void renderFluidAndGlass(PoseStack pose, MultiBufferSource buffers, float fill, float ambient) {
        boolean pack = JarWholeRenderer.shaderPackActive();
        VertexConsumer tv = buffers.getBuffer(JarWholeRenderer.clearType(pack));
        PoseStack.Pose t = pose.last();
        if (fill > 0.002f) {
            box(tv, t, 3.1f, TANK_BOTTOM, 3.1f, 28.9f, TANK_BOTTOM + (TANK_TOP - TANK_BOTTOM) * fill, 28.9f,
                    CRYO, 0xC8FFFFFF, LightTexture.FULL_BRIGHT);
        }
        if (!pack) {
            int glass = 0x90000000 | ((int) (0xE8 * ambient) << 16) | ((int) (0xF8 * ambient) << 8) | (int) (0xFF * ambient);
            box(tv, t, 2.6f, 8.23f, 2.6f, 29.4f, 26.77f, 29.4f, GLASS, glass, LightTexture.FULL_BRIGHT);
        }
    }

    private static Vector3f lerpLeg(float[] foot, float[] top, float y0, float y1, float k) {
        return new Vector3f(Mth.lerp(k, foot[0], top[0]), Mth.lerp(k, y0, y1), Mth.lerp(k, foot[1], top[1]));
    }
}
