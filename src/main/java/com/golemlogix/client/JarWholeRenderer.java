package com.golemlogix.client;

import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.golemlogix.client.CryoJarRenderer.BRASS;
import static com.golemlogix.client.CryoJarRenderer.BRICK;
import static com.golemlogix.client.CryoJarRenderer.BRICK_TINT;
import static com.golemlogix.client.CryoJarRenderer.COPPER;
import static com.golemlogix.client.CryoJarRenderer.CRYO;
import static com.golemlogix.client.CryoJarRenderer.DARK;
import static com.golemlogix.client.CryoJarRenderer.FRAME;
import static com.golemlogix.client.CryoJarRenderer.GAUGE;
import static com.golemlogix.client.CryoJarRenderer.GLASS;
import static com.golemlogix.client.CryoJarRenderer.ICE;
import static com.golemlogix.client.CryoJarRenderer.RUNES;
import static com.golemlogix.client.CryoJarRenderer.SNOW;
import static com.golemlogix.client.CryoJarRenderer.WHITE;
import static com.golemlogix.client.CryoMainframeRenderer.box;
import static com.golemlogix.client.CryoMainframeRenderer.fitWall;
import static com.golemlogix.client.CryoMainframeRenderer.limb;

/**
 * The joined jars, drawn from their corner. Pixels, in a frame where the machine's front is Z 0 and its footprint starts at 0:
 * <ul>
 * <li>the Giga Jar (32 by 32): one great glass tank on a rune plinth, a deck on top, and on the deck a pumpjack: a lattice samson post,
 * a walking beam nodding on it, a crank with a pitman arm behind and a rod that works a piston down in the Source. Copper golems
 * tend it: one at a valve, one at a gauge, one going up and down the ladder.</li>
 * <li>the Antigravity Reactor (48 by 48): a heavy rune-carved base, and over it a sphere of Source floating free inside gimbal rings,
 * larger the fuller it is, with shards drifting up around it. Iron golems walk the great ring round, copper golems keep the wheels,
 * snow golems throw frost at it.</li>
 * </ul>
 * Golems stop being drawn past CREW_SIGHT: from that far they are specks, and they cost the most.
 */
final class JarWholeRenderer {
    private static final Identifier GOLEM = Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    private static final int CREW_SIGHT = 48;
    private static final int SOURCE_GLOW = 8;
    private static final Identifier GRATE = Identifier.withDefaultNamespace("block/iron_trapdoor");
    private static final int LIGHT_BRASS = 0xFFE8C878, DIM_BRASS = 0xFFC8A060;

    private JarWholeRenderer() {
    }

    static void render(CryoJarBlockEntity be, ModelPart ironGolem, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        JarForm form = be.form();
        int width = form.width();
        Direction facing = be.getBlockState().getValue(CryoJarBlock.FACING);
        int around = machineLight(level, pos, width);
        // the glowing Source lights the machine around it, however dark the night
        int light = be.clientSource() > 0 ? LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(around), SOURCE_GLOW), LightCoordsUtil.sky(around)) : around;
        float fill = Math.min(1f, Mth.lerp(partialTick, be.fillPrev, be.fill));
        float time = level.getGameTime() + partialTick;
        boolean charged = be.clientSource() > 0;
        Vec3 cam = com.golemlogix.client.render.LegacyBer.camera();
        boolean crew = cam.distanceToSqr(pos.getX() + width / 2.0, pos.getY() + 2.0, pos.getZ() + width / 2.0) < CREW_SIGHT * CREW_SIGHT;

        // brass sockets where pipes dock, in the world's own directions
        VertexConsumer solid = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                pose.pushPose();
                pose.translate(x, 0, z);
                CryoJarRenderer.sockets(solid, pose.last(), level, pos.offset(x, 0, z), light);
                pose.popPose();
            }
        }
        pose.pushPose();
        // the corner's facing turns the whole machine, about its own middle
        pose.rotateAround(Axis.YP.rotationDegrees(180f - facing.toYRot()), width / 2f, 0f, width / 2f);
        if (form == JarForm.GIGA) {
            giga(pose, buffers, light, time, fill, charged, crew);
        } else {
            reactor(pose, buffers, ironGolem, light, time, fill, charged, crew);
        }
        pose.popPose();
    }

    /**
     * The light the machine stands in: the brightest around it, at both of its levels and over its top. One spot would not do, a pipe
     * or a roof set right on that spot would turn the whole machine black.
     */
    static int machineLight(Level level, BlockPos pos, int width) {
        int block = 0, sky = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= width; x++) {
                for (int z = -1; z <= width; z++) {
                    boolean inside = x >= 0 && x < width && z >= 0 && z < width;
                    // beside it at its two levels, and only over it on the third
                    if (y < 2 ? inside : !inside) {
                        continue;
                    }
                    m.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getBlockState(m).isSolidRender()) {
                        continue;
                    }
                    int c = LevelRenderer.getLightCoords(level, m);
                    block = Math.max(block, LightCoordsUtil.block(c));
                    sky = Math.max(sky, LightCoordsUtil.sky(c));
                }
            }
        }
        return LightCoordsUtil.pack(block, sky);
    }

    // ------------------------------------------------------------------------------------------------ the Giga Jar

    private static void giga(PoseStack pose, MultiBufferSource buffers, int light, float time, float fill, boolean on, boolean crew) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        // the plinth, runes all round
        box(vc, p, 0.4f, 0, 0.4f, 31.6f, 1.4f, 31.6f, DARK, WHITE, light);
        box(vc, p, 0.8f, 1.4f, 0.8f, 31.2f, 6.2f, 31.2f, BRICK, BRICK_TINT, light);
        box(vc, p, 0.5f, 6.2f, 0.5f, 31.5f, 7.4f, 31.5f, DARK, WHITE, light);
        runeBands(vc, pose, 32f, 2f, 5.4f, 0.74f, time, on, light);
        cornerSnow(vc, p, 32f, 7.4f, light);

        // the tank's frame: corner posts, a post in the middle of each side, rings below and above
        for (float[] c : new float[][]{{1.6f, 1.6f}, {28.9f, 1.6f}, {1.6f, 28.9f}, {28.9f, 28.9f}}) {
            box(vc, p, c[0], 7.4f, c[1], c[0] + 1.5f, 27.6f, c[1] + 1.5f, FRAME, WHITE, light);
        }
        box(vc, p, 15.6f, 7.4f, 1.8f, 16.4f, 27.6f, 2.6f, FRAME, WHITE, light);
        box(vc, p, 15.6f, 7.4f, 29.4f, 16.4f, 27.6f, 30.2f, FRAME, WHITE, light);
        box(vc, p, 1.8f, 7.4f, 15.6f, 2.6f, 27.6f, 16.4f, FRAME, WHITE, light);
        box(vc, p, 29.4f, 7.4f, 15.6f, 30.2f, 27.6f, 16.4f, FRAME, WHITE, light);
        box(vc, p, 1.4f, 7.4f, 1.4f, 30.6f, 8.2f, 30.6f, FRAME, WHITE, light);

        // the deck on the tank, a lip under snow
        box(vc, p, 1.2f, 26.8f, 1.2f, 30.8f, 28.4f, 30.8f, FRAME, WHITE, light);
        box(vc, p, 1.8f, 28.4f, 1.8f, 30.2f, 30.8f, 30.2f, BRICK, BRICK_TINT, light);
        box(vc, p, 1.2f, 30.8f, 1.2f, 30.8f, 31.6f, 30.8f, BRICK, 0xFFE0B8A8, light);
        for (float[] s : new float[][]{{1.2f, 1.2f, 7f, 4f}, {25.8f, 1.2f, 30.8f, 3.4f}, {1.2f, 26f, 5f, 30.8f}, {26.5f, 27f, 30.8f, 30.8f}}) {
            box(vc, p, s[0], 31.6f, s[1], s[2], 32.2f, s[3], SNOW, WHITE, light);
        }
        for (float[] i : new float[][]{{3f, 1.6f}, {6.5f, 2.4f}, {11f, 1.3f}, {18f, 2f}, {23.4f, 1.5f}, {28f, 2.6f}}) {
            box(vc, p, i[0], 26.8f - i[1], 1.0f, i[0] + 0.6f, 26.8f, 1.6f, ICE, WHITE, light);
        }

        // the ladder up the right side, from the plinth to a catwalk; another catwalk on the left for the golem at the valve
        ladder(vc, p, 31.2f, 7.4f, 30.8f, 20f, 25f, light);
        catwalk(vc, pose, 30.4f, 36.4f, 18.6f, 26.4f, 30.8f, 30.4f, 25.4f, light);
        catwalk(vc, pose, -4.4f, 1.6f, 0.6f, 7f, 30.8f, 1.6f, 25.4f, light);

        // the samson post: four legs leaning together, girders and cross-braces on all four faces
        float[][] feet = {{9f, 9f}, {23f, 9f}, {23f, 23f}, {9f, 23f}};
        float[][] tops = {{14f, 14f}, {18f, 14f}, {18f, 18f}, {14f, 18f}};
        float y0 = 31.6f, y1 = 56f;
        for (int i = 0; i < 4; i++) {
            box(vc, p, feet[i][0] - 1.4f, y0, feet[i][1] - 1.4f, feet[i][0] + 1.4f, y0 + 0.8f, feet[i][1] + 1.4f, DARK, WHITE, light);
            limb(vc, pose, new Vector3f(feet[i][0], y0, feet[i][1]), new Vector3f(tops[i][0], y1, tops[i][1]), 1.3f, BRASS, light);
        }
        float[] levels = {0f, 0.3f, 0.58f, 0.82f};
        for (int l = 0; l < levels.length - 1; l++) {
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                Vector3f a = lerp(feet[i], tops[i], y0, y1, levels[l]), b = lerp(feet[j], tops[j], y0, y1, levels[l]);
                Vector3f c = lerp(feet[i], tops[i], y0, y1, levels[l + 1]), d = lerp(feet[j], tops[j], y0, y1, levels[l + 1]);
                limb(vc, pose, c, d, 0.9f, FRAME, light);
                limb(vc, pose, a, d, 0.6f, FRAME, light);
                limb(vc, pose, b, c, 0.6f, FRAME, light);
            }
        }
        box(vc, p, 13.2f, 56f, 13.2f, 18.8f, 57.6f, 18.8f, FRAME, WHITE, light);
        box(vc, p, 15f, 57.6f, 13.6f, 17f, 59.4f, 18.4f, BRASS, LIGHT_BRASS, light);

        // what the crank does: it turns, and the beam follows it through the pitman arm
        float crank = on ? time * 0.075f : 0.6f;
        float crankRadius = 3.2f;
        float beamPivotX = 16f, beamPivotY = 60f, tailArm = 12.5f, headArm = 12f;
        float tailRise = crankRadius * Mth.sin(crank);
        float beamAngle = (float) Math.atan2(tailRise, tailArm);

        // the gearbox and the crank at the back right, its wheel on the front of the box
        float crankX = 25.4f, crankY = 41f, crankZ = 20.2f;
        box(vc, p, 22.4f, 31.6f, 21f, 29.4f, 36.2f, 28.4f, FRAME, WHITE, light);
        box(vc, p, 22f, 36.2f, 20.6f, 29.8f, 37f, 28.8f, BRASS, DIM_BRASS, light);
        box(vc, p, 24f, 37f, 20.8f, 26.8f, 43f, 22.4f, FRAME, WHITE, light);
        wheelZ(vc, pose, crankX, crankY, crankZ, 4f, crank * Mth.RAD_TO_DEG, light);
        // the counterweight: a heavy brass lobe opposite the crank pin
        pose.pushPose();
        pose.translate(crankX / 16f, crankY / 16f, (crankZ - 1f) / 16f);
        pose.mulPose(Axis.ZP.rotation(crank));
        PoseStack.Pose cw = pose.last();
        box(vc, cw, -4.2f, -1.1f, -0.5f, 0f, 1.1f, 0.5f, BRASS, WHITE, light);
        box(vc, cw, -7.6f, -3f, -0.8f, -4.2f, 3f, 0.8f, BRASS, LIGHT_BRASS, light);
        box(vc, cw, -8.6f, -2f, -0.8f, -7.6f, 2f, 0.8f, BRASS, WHITE, light);
        pose.popPose();
        Vector3f pin = new Vector3f(crankX + crankRadius * Mth.cos(crank), crankY + crankRadius * Mth.sin(crank), crankZ - 0.6f);
        Vector3f tail = new Vector3f(beamPivotX + tailArm * Mth.cos(beamAngle), beamPivotY + tailArm * Mth.sin(beamAngle), 19.6f);
        limb(vc, pose, pin, tail, 0.9f, BRASS, light);
        box(vc, p, pin.x - 0.7f, pin.y - 0.7f, pin.z - 0.9f, pin.x + 0.7f, pin.y + 0.7f, pin.z + 0.5f, DARK, WHITE, light);

        // the walking beam, the horse head at its near end, snow along the top
        pose.pushPose();
        pose.translate(beamPivotX / 16f, beamPivotY / 16f, 16f / 16f);
        pose.mulPose(Axis.ZP.rotation(beamAngle));
        PoseStack.Pose b = pose.last();
        box(vc, b, -headArm + 1f, -1.2f, -1.7f, tailArm + 1f, 1.2f, 1.7f, FRAME, WHITE, light);
        box(vc, b, -headArm + 1f, 1.2f, -1.2f, tailArm + 1f, 1.7f, 1.2f, SNOW, WHITE, light);
        box(vc, b, -1.2f, -1.6f, -2f, 1.2f, 1.6f, 2f, BRASS, LIGHT_BRASS, light);
        box(vc, b, tailArm - 1f, -1.6f, -2.8f, tailArm + 1.4f, 1.6f, 3.6f, BRASS, WHITE, light);
        // the head: a curved brass plate, thick at the top and bottom
        for (int i = 0; i < 7; i++) {
            float k = i / 6f * 2f - 1f;
            float x = -headArm - 1.4f * Mth.sqrt(Math.max(0f, 1f - k * k)) - 0.3f;
            box(vc, b, x - 0.9f, k * 5.6f - 0.9f, -1.5f, -headArm + 1.2f, k * 5.6f + 0.9f, 1.5f, BRASS, i % 3 == 0 ? LIGHT_BRASS : WHITE, light);
        }
        pose.popPose();

        // the rod hangs from the head straight down through the deck to the piston in the Source
        float rodTop = beamPivotY - headArm * Mth.sin(beamAngle) - 5.4f;
        float rodX = beamPivotX - headArm - 1.4f;
        box(vc, p, rodX - 0.45f, rodTop - 44f, 15.55f, rodX + 0.45f, rodTop, 16.45f, BRASS, LIGHT_BRASS, light);
        box(vc, p, rodX - 2f, 31.6f, 14f, rodX + 2f, 33.2f, 18f, BRASS, DIM_BRASS, light);
        box(vc, p, rodX - 2.2f, rodTop - 47.4f, 13.8f, rodX + 2.2f, rodTop - 44f, 18.2f, FRAME, WHITE, light);

        // the valve the first golem turns, on a stand at the deck's front left, and the gauge the second one watches
        box(vc, p, 3.6f, 31.6f, 3f, 4.8f, 36.6f, 4.2f, COPPER, WHITE, light);
        box(vc, p, 2.8f, 31.6f, 2.2f, 5.6f, 32.2f, 5f, BRASS, DIM_BRASS, light);
        float turn = CryoJarRenderer.turnAngle(time, on);
        CryoJarRenderer.wheel(vc, pose, 3f, 35.8f, 3.6f, 2.2f, turn, light);
        box(vc, p, 21.6f, 31.6f, 8f, 27.4f, 38f, 9f, FRAME, WHITE, light);
        fitWall(vc, p, 22.2f, 32.6f, 26.8f, 37.2f, 7.96f, GAUGE, WHITE, light);

        if (crew) {
            gigaCrew(pose, buffers, light, time, turn, on);
        }

        // the Source and the glass over it
        VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose t = pose.last();
        float bottom = 8.26f, top = 26.74f;
        if (fill > 0.002f) {
            box(tv, t, 3.1f, bottom, 3.1f, 28.9f, bottom + (top - bottom) * fill, 28.9f, CRYO, 0xE0FFFFFF, LightCoordsUtil.FULL_BRIGHT);
        }
        box(tv, t, 2.6f, 8.23f, 2.6f, 29.4f, 26.77f, 29.4f, GLASS, 0x90E8F8FF, light);
    }

    private static void gigaCrew(PoseStack pose, MultiBufferSource buffers, int light, float time, float turn, boolean on) {
        float scale = 0.36f;
        // at the valve, facing it
        float reach = on ? -1.15f + 0.12f * Mth.sin(turn * Mth.DEG_TO_RAD) : -0.9f;
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(-1f / 16f, 31.4f / 16f, 3.6f / 16f), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
                scale, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.08f), 1, null,
                new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(time * 0.017f)));
        // at the gauge, looking it over and scratching its antenna
        float look = 0.25f * Mth.sin(time * 0.023f);
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(24.5f / 16f, 31.6f / 16f, 3.2f / 16f), new Vector3f(0f, 0f, 1f), new Vector3f(0f, 1f, 0f),
                scale, new CopperGolemPuppet.Anim(time * 0.08f, false, false, 0.03f), 2, null,
                new CopperGolemPuppet.Arms(-0.5f + 0.2f * Mth.sin(time * 0.05f), 0.15f, -0.2f, -0.1f, look));
        // on the ladder: up, a rest at the top, down, a rest at the bottom; the hands go hand over hand while it moves
        float cycle = (time * 0.0035f) % 1f;
        float k = cycle < 0.4f ? cycle / 0.4f : cycle < 0.5f ? 1f : cycle < 0.9f ? 1f - (cycle - 0.5f) / 0.4f : 0f;
        float climb = k * k * (3f - 2f * k);
        boolean moving = cycle < 0.4f || (cycle > 0.5f && cycle < 0.9f);
        float hands = moving ? 0.35f * Mth.sin(time * 0.35f) : 0f;
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(35.2f / 16f, (7.4f + 17f * climb) / 16f, 22.5f / 16f), new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
                scale, new CopperGolemPuppet.Anim(time * 0.15f, false, false, 0f), 0, null,
                new CopperGolemPuppet.Arms(-1.9f + hands, 0.1f, -1.9f - hands, -0.1f, 0f));
    }

    // ------------------------------------------------------------------------------------------------ the reactor

    private static void reactor(PoseStack pose, MultiBufferSource buffers, ModelPart ironGolem, int light, float time, float fill, boolean on, boolean crew) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        float top = 31.4f;
        // the base: a dark foot, brick, a rim
        box(vc, p, 0.3f, 0, 0.3f, 47.7f, 1.8f, 47.7f, DARK, WHITE, light);
        box(vc, p, 0.8f, 1.8f, 0.8f, 47.2f, 29.6f, 47.2f, BRICK, BRICK_TINT, light);
        box(vc, p, 0.4f, 29.6f, 0.4f, 47.6f, top, 47.6f, BRICK, 0xFFE0B8A8, light);
        reactorSides(vc, pose, time, on, light);
        cornerSnow(vc, p, 48f, top, light);
        for (float[] i : new float[][]{{3f, 2.2f}, {8f, 3f}, {14f, 1.6f}, {21f, 2.6f}, {30f, 1.8f}, {38f, 3.2f}, {44f, 2f}}) {
            box(vc, p, i[0], 29.6f - i[1], 0.2f, i[0] + 0.7f, 29.6f, 0.9f, ICE, WHITE, light);
        }

        // the well in the middle of the top: a brass rim, a floor of runes, a lit gap
        float glow = on ? 0.75f + 0.25f * Mth.sin(time * 0.06f) : 0.2f;
        int rune = 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xF0 * glow);
        box(vc, p, 11f, top, 11f, 37f, top + 0.3f, 37f, DARK, WHITE, light);
        flatRunes(vc, p, 13f, 35f, top + 0.34f, rune, on ? LightCoordsUtil.FULL_BRIGHT : light);
        pose.pushPose();
        pose.translate(24f / 16f, (top + 0.9f) / 16f, 24f / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(on ? -time * 0.25f : 0f));
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        ring(vc, pose, 12.4f, 1.6f, 1.2f, 24, COPPER, WHITE, light);
        for (int i = 0; i < 16; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 22.5f));
            box(vc, pose.last(), -0.8f, 13.2f, -0.6f, 0.8f, 14.6f, 0.6f, COPPER, 0xFFD89070, light);
            pose.popPose();
        }
        pose.popPose();

        float cx = 24f, cz = 24f, cy = 60f;
        // the toothed ring low over the well, that the iron golems walk round
        pose.pushPose();
        pose.translate(cx / 16f, 39.6f / 16f, cz / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(on ? time * 0.5f : 0f));
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        ring(vc, pose, 17f, 1.8f, 1.8f, 28, BRASS, WHITE, light);
        for (int i = 0; i < 14; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / 14f));
            box(vc, pose.last(), -0.9f, 15f, -1.1f, 0.9f, 16.2f, 1.1f, COPPER, WHITE, light);
            pose.popPose();
        }
        pose.popPose();

        // the gimbal: nothing holds it up. The outer ring stands up and swings slowly, the brass one lies level, the inner one tumbles
        float spin = on ? time : 0f;
        pose.pushPose();
        pose.translate(cx / 16f, cy / 16f, cz / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(spin * 0.35f));
        ring(vc, pose, 20f, 2f, 2.4f, 32, FRAME, WHITE, light);
        runeRing(vc, pose, 20f, 2.5f, 32, rune, on ? LightCoordsUtil.FULL_BRIGHT : light);
        pose.mulPose(Axis.XP.rotationDegrees(90f));
        ring(vc, pose, 16.4f, 1.7f, 2f, 28, BRASS, LIGHT_BRASS, light);
        for (int i = 0; i < 20; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 18f));
            box(vc, pose.last(), -0.6f, 14.7f, -0.7f, 0.6f, 15.6f, 0.7f, BRASS, WHITE, light);
            if (i % 3 == 0) {
                // the ring's plane is XY here: its underside is towards +Z
                box(vc, pose.last(), -0.3f, 16.1f, 1f, 0.3f, 16.7f, 1f + 1.2f + (i % 2), ICE, WHITE, light);
            }
            pose.popPose();
        }
        pose.mulPose(Axis.XP.rotationDegrees(spin * 0.7f + 35f));
        ring(vc, pose, 13.4f, 1f, 1.2f, 24, COPPER, WHITE, light);
        pose.popPose();

        // the hand wheels and stands the copper golems work, on the middle of three sides of the top
        float turn = CryoJarRenderer.turnAngle(time, on);
        p = pose.last();
        box(vc, p, 22f, top, 4.4f, 26f, top + 0.6f, 8f, BRASS, DIM_BRASS, light);
        box(vc, p, 23.4f, top, 5.6f, 24.6f, top + 5f, 6.8f, COPPER, WHITE, light);
        CryoJarRenderer.wheel(vc, pose, 22.8f, top + 4.2f, 6.2f, 2.2f, turn, light);
        box(vc, p, 40f, top, 22f, 43.6f, top + 0.6f, 26f, BRASS, DIM_BRASS, light);
        box(vc, p, 41.2f, top, 23.4f, 42.4f, top + 5f, 24.6f, COPPER, WHITE, light);
        CryoJarRenderer.wheel(vc, pose, 40.6f, top + 4.2f, 24f, 2.2f, turn, light);
        box(vc, p, 4.4f, top, 22f, 8f, top + 0.6f, 26f, BRASS, DIM_BRASS, light);
        box(vc, p, 5.6f, top, 23.4f, 6.8f, top + 5f, 24.6f, COPPER, WHITE, light);
        CryoJarRenderer.wheel(vc, pose, 7.4f, top + 4.2f, 24f, 2.2f, -turn, light);
        // cold vents where the snow golems stand
        for (float[] v : new float[][]{{39f, 4f}, {5f, 39f}}) {
            box(vc, p, v[0] - 3f, top, v[1] - 2.6f, v[0] + 3f, top + 0.5f, v[1] + 2.6f, SNOW, WHITE, light);
            box(vc, p, v[0] - 1.6f, top + 0.5f, v[1] - 3f, v[0] + 1.6f, top + 0.9f, v[1] + 3f, SNOW, WHITE, light);
        }

        if (crew) {
            reactorCrew(pose, buffers, ironGolem, light, time, turn, on, top, cx, cz);
            frost(buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS)), pose.last(), time, on, top, cx, cy, cz, light);
        }

        // the Source, floating: a ball of slices, larger the fuller it is, turning; shards rise around it
        VertexConsumer ballBuffer = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        pose.translate(cx / 16f, (cy + 0.8f * Mth.sin(time * 0.03f)) / 16f, cz / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.6f));
        float radius = on ? 4f + 7.4f * (float) Math.cbrt(fill) : 3f;
        ball(ballBuffer, pose.last(), radius, on ? CRYO : ICE, WHITE, on ? LightCoordsUtil.FULL_BRIGHT : light);
        pose.popPose();
        if (on) {
            VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
            PoseStack.Pose t = pose.last();
            for (int i = 0; i < 10; i++) {
                float k = (time * 0.006f + i * 0.137f) % 1f;
                float a = i * 2.399f + time * 0.012f;
                float r = 9f + 8f * ((i * 0.37f) % 1f);
                float size = 0.35f + 0.25f * (i % 3);
                float x = cx + r * Mth.cos(a), z = cz + r * Mth.sin(a), y = top + 6f + k * 52f;
                Identifier drop = i % 2 == 0 ? CRYO : ICE;
                box(tv, t, x - size, y - size, z - size, x + size, y + size, z + size, drop, 0xE0FFFFFF, LightCoordsUtil.FULL_BRIGHT);
                box(tv, t, x - size / 2f, y + size, z - size / 2f, x + size / 2f, y + size * 2f, z + size / 2f, drop, 0xE0FFFFFF, LightCoordsUtil.FULL_BRIGHT);
            }
        }
    }

    private static void reactorCrew(PoseStack pose, MultiBufferSource buffers, ModelPart golem, int light, float time, float turn, boolean on,
                                    float top, float cx, float cz) {
        // iron golems at two corners, facing the middle, their long arms on the toothed ring; they sway as if walking it round
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 7f : 41f, z = i == 0 ? 7f : 41f;
            ironGolem(pose, buffers, golem, x, top, z, (float) Math.toDegrees(Math.atan2(cx - x, cz - z)), 0.33f, time + i * 17f, on, light);
        }
        // copper golems at the wheels
        float reach = on ? -1.15f + 0.12f * Mth.sin(turn * Mth.DEG_TO_RAD) : -0.9f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(time * 0.017f));
        float scale = 0.36f;
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(18.4f / 16f, top / 16f, 6.2f / 16f), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
                scale, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.08f), 0, null, arms);
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(36.2f / 16f, top / 16f, 24f / 16f), new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
                scale, new CopperGolemPuppet.Anim(time * 0.09f, false, false, 0.08f), 1, null, arms);
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(11.8f / 16f, top / 16f, 24f / 16f), new Vector3f(-1f, 0f, 0f), new Vector3f(0f, 1f, 0f),
                scale, new CopperGolemPuppet.Anim(time * 0.11f, false, false, 0.08f), 2, null, arms);
        // snow golems on the vents, each throwing with one hand now and then
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 39f : 5f, z = i == 0 ? 4f : 39f;
            float yaw = (float) Math.toDegrees(Math.atan2(cx - x, cz - z));
            float k = throwPhase(time, i);
            Vector3f right = new Vector3f(-3f, 12f + 8f * k, 3f + 3f * k);
            SnowmanPuppet.Pose sp = new SnowmanPuppet.Pose(0.4f * k, 0f, 6f * Mth.sin(time * 0.02f + i), 4f, SnowmanPuppet.REST_LEFT, right);
            SnowmanPuppet.render(pose, buffers, light, new Vector3f(x / 16f, (top + 0.9f) / 16f, z / 16f), yaw, 0.42f, SnowmanPuppet.Head.PUMPKIN, sp, null);
        }
    }

    /** 0..1: how far a snow golem's arm is thrown out this moment. */
    private static float throwPhase(float time, int who) {
        float c = (time * 0.012f + who * 0.5f) % 1f;
        return c < 0.15f ? Mth.sin(c / 0.15f * Mth.PI) : 0f;
    }

    /** The snowballs the snow golems throw, on their way from the raised hand to the ball of Source. */
    private static void frost(VertexConsumer vc, PoseStack.Pose p, float time, boolean on, float top, float cx, float cy, float cz, int light) {
        if (!on) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? 39f : 5f, z = i == 0 ? 4f : 39f;
            float c = (time * 0.012f + i * 0.5f) % 1f;
            if (c < 0.1f || c > 0.42f) {
                continue;
            }
            float k = (c - 0.1f) / 0.32f;
            float bx = Mth.lerp(k, x, cx), bz = Mth.lerp(k, z, cz), by = Mth.lerp(k, top + 8f, cy) + 6f * Mth.sin(k * Mth.PI);
            box(vc, p, bx - 0.7f, by - 0.7f, bz - 0.7f, bx + 0.7f, by + 0.7f, bz + 0.7f, SNOW, WHITE, light);
        }
    }

    /** An iron golem on the machine: feet at x, y, z, turned by yaw (0 faces +Z), its arms held forward on the ring. */
    private static void ironGolem(PoseStack pose, MultiBufferSource buffers, ModelPart golem, float x, float y, float z, float yaw, float scale, float time,
                                  boolean on, int light) {
        ModelPart head = golem.getChild("head"), rightArm = golem.getChild("right_arm"), leftArm = golem.getChild("left_arm");
        ModelPart rightLeg = golem.getChild("right_leg"), leftLeg = golem.getChild("left_leg");
        float strain = on ? 0.09f * Mth.sin(time * 0.09f) : 0f;
        rightArm.xRot = -1.55f + strain;
        leftArm.xRot = -1.55f - strain;
        rightArm.yRot = 0f;
        leftArm.yRot = 0f;
        rightLeg.xRot = strain * 2f;
        leftLeg.xRot = -strain * 2f;
        head.xRot = 0.15f;
        head.yRot = 0.12f * Mth.sin(time * 0.02f);
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, z / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
        pose.scale(-scale, -scale, scale);
        pose.translate(0f, -1.501f, 0f);
        golem.render(pose, buffers.getBuffer(RenderTypes.entityCutout(GOLEM)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** The carving on the reactor's four sides: a dark recess with a large rune band, a gauge, and a copper pipe on brackets with a hand wheel. */
    private static void reactorSides(VertexConsumer vc, PoseStack pose, float time, boolean on, int light) {
        float glow = on ? 0.6f + 0.4f * (0.8f + 0.2f * Mth.sin(time * 0.08f)) : 0.18f;
        int rune = 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xF0 * glow);
        int runeLight = on ? LightCoordsUtil.FULL_BRIGHT : light;
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(side * 90f), 1.5f, 0f, 1.5f);
            PoseStack.Pose p = pose.last();
            box(vc, p, 12f, 13.6f, 0.5f, 36f, 27f, 0.82f, DARK, WHITE, light);
            box(vc, p, 11.4f, 13f, 0.3f, 36.6f, 13.6f, 0.82f, BRASS, DIM_BRASS, light);
            box(vc, p, 11.4f, 27f, 0.3f, 36.6f, 27.6f, 0.82f, BRASS, DIM_BRASS, light);
            fitWall(vc, p, 13.4f, 15f, 34.6f, 25.6f, 0.46f, RUNES, rune, runeLight);
            fitWall(vc, p, 3f, 18f, 9f, 24f, 0.72f, GAUGE, WHITE, light);
            box(vc, p, 4f, 2.4f, 0.2f, 44f, 4.6f, 0.8f, COPPER, WHITE, light);
            for (float x : new float[]{6f, 16f, 32f, 42f}) {
                box(vc, p, x - 0.8f, 1.8f, 0f, x + 0.8f, 5.2f, 0.9f, BRASS, DIM_BRASS, light);
            }
            box(vc, p, 40.4f, 4.6f, 0.2f, 42.6f, 12f, 0.8f, COPPER, WHITE, light);
            wheelZ(vc, pose, 41.5f, 13f, 0.05f, 2.4f, CryoJarRenderer.turnAngle(time + side * 23f, on), light);
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------------ shared pieces

    private static Vector3f lerp(float[] foot, float[] top, float y0, float y1, float k) {
        return new Vector3f(Mth.lerp(k, foot[0], top[0]), Mth.lerp(k, y0, y1), Mth.lerp(k, foot[1], top[1]));
    }

    /** A rune band along the foot of each of the four sides. */
    private static void runeBands(VertexConsumer vc, PoseStack pose, float size, float y0, float y1, float z, float time, boolean on, int light) {
        float glow = on ? 0.55f + 0.45f * (0.8f + 0.2f * Mth.sin(time * 0.08f)) : 0.18f;
        int rune = 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xF0 * glow);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(side * 90f), size / 32f, 0f, size / 32f);
            fitWall(vc, pose.last(), 3f, y0, size - 3f, y1, z, RUNES, rune, on ? LightCoordsUtil.FULL_BRIGHT : light);
            pose.popPose();
        }
    }

    /** Snow on the four corners of a ledge, with icicles hanging off. */
    private static void cornerSnow(VertexConsumer vc, PoseStack.Pose p, float size, float y, int light) {
        for (float[] c : new float[][]{{0f, 0f}, {size - 5f, 0f}, {0f, size - 5f}, {size - 5f, size - 5f}}) {
            box(vc, p, c[0] + 0.1f, y, c[1] + 0.1f, c[0] + 4.9f, y + 0.9f, c[1] + 4.9f, SNOW, WHITE, light);
        }
        for (float[] i : new float[][]{{0.8f, 0.2f, 1.8f}, {3.2f, 0.2f, 1.2f}, {size - 3f, 0.2f, 2f}, {size - 1.2f, 2f, 1.3f}, {0.2f, size - 2.4f, 1.6f},
                {size - 2f, size - 0.8f, 1.8f}}) {
            box(vc, p, i[0], y - i[2], i[1], i[0] + 0.6f, y, i[1] + 0.6f, ICE, WHITE, light);
        }
    }

    /** A ladder against a wall at x: two rails and the rungs, from y0 to y1, between z0 and z1. */
    private static void ladder(VertexConsumer vc, PoseStack.Pose p, float x, float y0, float y1, float z0, float z1, int light) {
        box(vc, p, x, y0, z0, x + 1f, y1, z0 + 0.7f, DARK, WHITE, light);
        box(vc, p, x, y0, z1 - 0.7f, x + 1f, y1, z1, DARK, WHITE, light);
        for (float y = y0 + 1.4f; y < y1; y += 2.6f) {
            box(vc, p, x + 0.2f, y, z0 + 0.4f, x + 0.8f, y + 0.5f, z1 - 0.4f, BRASS, WHITE, light);
        }
    }

    /** A brass hand wheel in the XY plane (its axle along Z) at x, y, z, turned by angle degrees. */
    private static void wheelZ(VertexConsumer vc, PoseStack pose, float x, float y, float z, float r, float angle, int light) {
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, z / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        PoseStack.Pose p = pose.last();
        box(vc, p, -r, -0.35f, -0.4f, r, 0.35f, 0.4f, BRASS, WHITE, light);
        box(vc, p, -0.35f, -r, -0.4f, 0.35f, r, 0.4f, BRASS, WHITE, light);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8f;
            float cx = Mth.cos(a) * r, cy = Mth.sin(a) * r;
            box(vc, p, cx - 0.5f, cy - 0.5f, -0.45f, cx + 0.5f, cy + 0.5f, 0.45f, BRASS, LIGHT_BRASS, light);
        }
        box(vc, p, -0.7f, -0.7f, -0.6f, 0.7f, 0.7f, 0.6f, DARK, WHITE, light);
        pose.popPose();
    }

    /** A ring in the current XY plane around the origin, made of straight segments. */
    private static void ring(VertexConsumer vc, PoseStack pose, float radius, float thick, float width, int segments, Identifier tex, int color, int light) {
        float half = radius * Mth.PI / segments + 0.1f;
        for (int i = 0; i < segments; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / segments));
            box(vc, pose.last(), -half, radius - thick / 2f, -width / 2f, half, radius + thick / 2f, width / 2f, tex, color, light);
            pose.popPose();
        }
    }

    /** Glowing rune plates on the front of a ring: every other segment carries one. */
    private static void runeRing(VertexConsumer vc, PoseStack pose, float radius, float faceZ, int segments, int color, int light) {
        for (int i = 0; i < segments; i += 2) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / segments));
            for (float z : new float[]{-faceZ / 2f - 0.02f, faceZ / 2f + 0.02f}) {
                box(vc, pose.last(), -1.3f, radius - 0.7f, z - 0.02f, 1.3f, radius + 0.7f, z + 0.02f, RUNES, color, light);
            }
            pose.popPose();
        }
    }

    /** The rune floor of the well, lying flat. */
    private static void flatRunes(VertexConsumer vc, PoseStack.Pose p, float a, float b, float y, int color, int light) {
        box(vc, p, a, y - 0.02f, a, b, y, b, RUNES, color, light);
    }

    /** A sphere. All its faces carry the same normal: it glows evenly, without the bands the sun's shading would draw on it. */
    private static void ball(VertexConsumer vc, PoseStack.Pose p, float radius, Identifier tex, int color, int light) {
        net.minecraft.client.renderer.texture.TextureAtlasSprite s = CryoMainframeRenderer.sprite(tex);
        int bands = 10, sides = 20;
        for (int b = 0; b < bands; b++) {
            float a0 = -Mth.HALF_PI + Mth.PI * b / bands, a1 = -Mth.HALF_PI + Mth.PI * (b + 1) / bands;
            float y0 = radius * Mth.sin(a0), y1 = radius * Mth.sin(a1), r0 = radius * Mth.cos(a0), r1 = radius * Mth.cos(a1);
            float v0 = s.getV((b % 4) / 4f), v1 = s.getV((b % 4 + 1) / 4f);
            for (int i = 0; i < sides; i++) {
                float t0 = Mth.TWO_PI * i / sides, t1 = Mth.TWO_PI * (i + 1) / sides;
                float u0 = s.getU((i % 4) / 4f), u1 = s.getU((i % 4 + 1) / 4f);
                float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
                CryoMainframeRenderer.quad(vc, p, new float[]{r1 * c0, y1, r1 * s0}, new float[]{r1 * c1, y1, r1 * s1},
                        new float[]{r0 * c1, y0, r0 * s1}, new float[]{r0 * c0, y0, r0 * s0}, u0, v0, u1, v1, color, light, 0f, 1f, 0f);
            }
        }
    }

    /** A grated walkway from x0 to x1 with a rail on its outer side and struts down to the tank's frame at x strutX, height strutY. */
    private static void catwalk(VertexConsumer vc, PoseStack pose, float x0, float x1, float z0, float z1, float y, float strutX, float strutY, int light) {
        PoseStack.Pose p = pose.last();
        box(vc, p, x0, y, z0, x1, y + 0.5f, z1, GRATE, 0xFF6E6A66, light);
        boolean right = strutX < (x0 + x1) / 2f;
        float edge = right ? x1 : x0;
        box(vc, p, edge - 0.3f, y - 0.4f, z0 - 0.2f, edge + 0.3f, y + 0.6f, z1 + 0.2f, BRASS, DIM_BRASS, light);
        for (float z : new float[]{z0 + 0.4f, z1 - 0.4f}) {
            box(vc, p, edge - 0.25f, y + 0.5f, z - 0.25f, edge + 0.25f, y + 4.4f, z + 0.25f, BRASS, WHITE, light);
            limb(vc, pose, new Vector3f(edge, y, z), new Vector3f(strutX, strutY, z), 0.6f, BRASS, light);
        }
        box(vc, p, edge - 0.25f, y + 4.2f, z0, edge + 0.25f, y + 4.7f, z1, BRASS, LIGHT_BRASS, light);
    }
}
