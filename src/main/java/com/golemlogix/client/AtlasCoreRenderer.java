package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.AtlasCoreBlockEntity;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Powered Atlas Core:
 * - copper golems run along orbits around the crystal (three orbits on a complete array, one on the "starter tower");
 * - on a complete array, mini golems carry crates between the stacks in the corners along the top face (a ring of eight Casings around the core).
 * The array's dress (the ice columns, the rail, the icicles) belongs to the casings' own models, see AtlasCasingBlock.
 */
public class AtlasCoreRenderer extends LegacyBer<AtlasCoreBlockEntity> {
    private static final int ORBITS = 3;
    /** Orbit centre: the crystal sticking out above the ring and openwork lantern cage (in block fractions). */
    private static final float CENTER_Y = 1.28f;
    private static final float ORBIT_RADIUS = 0.62f;
    private static final float ORBIT_GOLEM_SCALE = 0.17f;

    // top face: a ring through the centres of the eight Casings (the core in the middle), height = top of the Casings
    private static final float RING_Y = 1.0f;
    private static final float WALK_TICKS = 52f;
    private static final float PAUSE_TICKS = 30f;
    private static final int CYCLE_TICKS = 2 * (int) (WALK_TICKS + PAUSE_TICKS);
    private static final float CYCLE = CYCLE_TICKS;
    private static final float RING_GOLEM_SCALE = 0.2f;
    private static final float[][] CORNERS = {{-0.5f, -0.5f}, {1.5f, -0.5f}, {1.5f, 1.5f}, {-0.5f, 1.5f}};

    private static final Vector3f[] ORBIT_U = new Vector3f[ORBITS];
    private static final Vector3f[] ORBIT_V = new Vector3f[ORBITS];
    static {
        for (int i = 0; i < ORBITS; i++) {
            Quaternionf plane = new Quaternionf()
                    .rotateY((float) Math.toRadians(i * 60f))
                    .rotateX((float) Math.toRadians(25f + i * 35f));
            ORBIT_U[i] = plane.transform(new Vector3f(1f, 0f, 0f));
            ORBIT_V[i] = plane.transform(new Vector3f(0f, 0f, 1f));
        }
    }

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f SCRATCH_RADIAL = new Vector3f();
    private static final Vector3f SCRATCH_POS = new Vector3f();
    private static final Vector3f SCRATCH_FWD = new Vector3f();
    private static final float[] GLYPH_A = new float[2];
    private static final float[] GLYPH_B = new float[2];

    public AtlasCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AtlasCoreBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (!be.isPowered() || be.getLevel() == null) {
            return;
        }
        long gameTime = be.getLevel().getGameTime();
        float time = (gameTime % 72000L) + partialTick;
        // The core block itself is wedged between casings and sits "in the shade": everything drawn above it is lit with the light of the cell above the core
        int above = LevelRenderer.getLightCoords(be.getLevel(), be.getBlockPos().above());
        light = LightCoordsUtil.pack(
                Math.max(LightCoordsUtil.block(light), LightCoordsUtil.block(above)),
                Math.max(LightCoordsUtil.sky(light), LightCoordsUtil.sky(above)));
        orbits(be, time, pose, buffers, light);
        if (be.isFormed()) {
            ring(gameTime, partialTick, pose, buffers, light);
            glyphs(gameTime, partialTick, time, pose, buffers);
        }
    }

    private static final Identifier GLYPH = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/atlas_glyph.png");
    /** Plating cells on a side face (a = along the face, b = height) in core coordinates: the top row (the ice columns stand below). */
    private static final int[][] SIDE_CELLS = {{-1, 0}, {0, 0}, {1, 0}};
    private static final int[][] TOP_CELLS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final float GLYPH_MOVE = 40f, GLYPH_STAY = 30f, GLYPH_HALF = 0.17f;

    /** Deterministic "random" number 0..1 from the face, step and salt. */
    private static float rnd(int face, long step, int salt) {
        long h = face * 0x9E3779B97F4A7C15L ^ step * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    /** Glyph point on a face at step: a random plating cell and a place in it (a, b in face coordinates). */
    private static void glyphPoint(int face, long step, float[] out) {
        int[][] cells = face == 4 ? TOP_CELLS : SIDE_CELLS;
        int[] c = cells[(int) (rnd(face, step, 1) * cells.length) % cells.length];
        out[0] = c[0] + 0.25f + 0.5f * rnd(face, step, 2);
        out[1] = c[1] + 0.25f + 0.5f * rnd(face, step, 3);
    }

    /**
     * Purple array glyph: one on each of the five visible faces of the assembled multiblock (four sides and the top);
     * the glyph either stays put or smoothly moves to a new random place on the plating of its face.
     */
    private void glyphs(long gameTime, float partialTick, float pulseTime, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.eyes(GLYPH));
        PoseStack.Pose p = pose.last();
        double cycle = GLYPH_MOVE + GLYPH_STAY;
        double fullTime = gameTime + (double) partialTick;
        float e = 0.004f, es = 0.135f;
        for (int face = 0; face < 5; face++) {
            double local = fullTime + face * 17.0;
            long step = (long) Math.floor(local / cycle);
            float k = Mth.clamp((float) ((local - step * cycle) / GLYPH_MOVE), 0f, 1f);
            k = k * k * (3f - 2f * k);
            glyphPoint(face, step, GLYPH_A);
            glyphPoint(face, step + 1, GLYPH_B);
            float u = Mth.lerp(k, GLYPH_A[0], GLYPH_B[0]), v = Mth.lerp(k, GLYPH_A[1], GLYPH_B[1]);
            int alpha = (int) (200 + 55 * Mth.sin(pulseTime * 0.15f + face));
            int color = (Mth.clamp(alpha, 0, 255) << 24) | 0xFFFFFF;
            // origin (glyph centre), "right" and "up" in the face plane, outward normal (sides clear the 3D parapet wheels)
            switch (face) {
                case 0 -> quad(vc, p, u, v, -1f - es, -1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, -1f, color);
                case 1 -> quad(vc, p, u, v, 2f + es, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, color);
                case 2 -> quad(vc, p, -1f - es, v, u, 0f, 0f, 1f, 0f, 1f, 0f, -1f, 0f, 0f, color);
                case 3 -> quad(vc, p, 2f + es, v, u, 0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f, color);
                default -> quad(vc, p, u, 1f + e, v, 1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f, color);
            }
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose p,
                             float cx, float cy, float cz,
                             float rx, float ry, float rz,
                             float ux, float uy, float uz,
                             float nx, float ny, float nz, int color) {
        float hrX = rx * GLYPH_HALF, hrY = ry * GLYPH_HALF, hrZ = rz * GLYPH_HALF;
        float huX = ux * GLYPH_HALF, huY = uy * GLYPH_HALF, huZ = uz * GLYPH_HALF;
        // counterclockwise as seen by someone looking at the face (front side)
        vc.addVertex(p, cx - hrX + huX, cy - hrY + huY, cz - hrZ + huZ).setColor(color).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx - hrX - huX, cy - hrY - huY, cz - hrZ - huZ).setColor(color).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx + hrX - huX, cy + hrY - huY, cz + hrZ - huZ).setColor(color).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx + hrX + huX, cy + hrY + huY, cz + hrZ + huZ).setColor(color).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    private void orbits(AtlasCoreBlockEntity be, float time, PoseStack pose, MultiBufferSource buffers, int light) {
        int count = be.isFormed() ? ORBITS : 1;
        for (int i = 0; i < count; i++) {
            Vector3f u = ORBIT_U[i];
            Vector3f v = ORBIT_V[i];
            float angle = (float) Math.toRadians(time * (2.2f + i * 0.5f) + i * 120f);
            float ca = Mth.cos(angle), sa = Mth.sin(angle);
            SCRATCH_RADIAL.set(u.x * ca + v.x * sa, u.y * ca + v.y * sa, u.z * ca + v.z * sa);
            SCRATCH_POS.set(0.5f + SCRATCH_RADIAL.x * ORBIT_RADIUS, CENTER_Y + SCRATCH_RADIAL.y * ORBIT_RADIUS,
                    0.5f + SCRATCH_RADIAL.z * ORBIT_RADIUS);
            SCRATCH_FWD.set(-u.x * sa + v.x * ca, -u.y * sa + v.y * ca, -u.z * sa + v.z * ca);
            // they walk on the "surface of a sphere" around the crystal: the top of the head points away from the centre
            float arc = angle * ORBIT_RADIUS;
            CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, SCRATCH_FWD, SCRATCH_RADIAL, ORBIT_GOLEM_SCALE,
                    new CopperGolemPuppet.Anim(arc * 14f, true, false, 0f), i);
        }
    }

    /** Four mini golems walk along the sides of the ring and carry crates from stack to stack (one crate each way). */
    private void ring(long gameTime, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        // stacks of crates in the corners
        BlockState barrel = Blocks.BARREL.defaultBlockState();
        for (float[] c : CORNERS) {
            for (int k = 0; k < 2; k++) {
                pose.pushPose();
                pose.translate(0.5f + (c[0] - 0.5f) * 0.97f, RING_Y + 0.09f + k * 0.18f, 0.5f + (c[1] - 0.5f) * 0.97f);
                pose.mulPose(Axis.YP.rotationDegrees(k * 23f + c[0] * 40f));
                pose.scale(0.18f, 0.18f, 0.18f);
                pose.translate(-0.5, -0.5, -0.5);
                BerBuffers.block(buffers, barrel, pose, light);
                pose.popPose();
            }
        }
        float cycleBase = Math.floorMod(gameTime, CYCLE_TICKS) + partialTick;
        for (int s = 0; s < 4; s++) {
            float[] a = CORNERS[s], b = CORNERS[(s + 1) % 4];
            float dx = b[0] - a[0], dz = b[1] - a[1];
            float len = Mth.sqrt(dx * dx + dz * dz);
            dx /= len;
            dz /= len;
            // sideways offset "outwards", so a golem walking along the neighbouring side does not step on this one
            float ox = dz, oz = -dx;
            float pad = 0.28f;
            float t = (cycleBase + s * (CYCLE / 4f)) % CYCLE;
            boolean forwardLeg = t < WALK_TICKS + PAUSE_TICKS;
            float leg = forwardLeg ? t : t - (WALK_TICKS + PAUSE_TICKS);
            float fromX = forwardLeg ? a[0] : b[0], fromZ = forwardLeg ? a[1] : b[1];
            float sx = forwardLeg ? dx : -dx, sz = forwardLeg ? dz : -dz;
            boolean walking = leg < WALK_TICKS;
            float run = walking ? leg / WALK_TICKS : 1f;
            float dist = pad + (len - 2f * pad) * run;
            // the corners are already in core block coordinates (centres of the corner casings): with an extra +0.5 the golems walked along the platform edge
            // and two of the four hung in the air beyond it; the "outward" offset stays inside the band of casings
            float px = fromX + sx * dist + ox * 0.14f;
            float pz = fromZ + sz * dist + oz * 0.14f;
            // at a stop: bow to the stack, put the crate down and take another
            float bow = 0f;
            boolean carrying = true;
            if (!walking) {
                float k = (leg - WALK_TICKS) / PAUSE_TICKS;
                bow = Mth.sin((float) Math.PI * k);
                carrying = k < 0.35f || k > 0.65f;
            }
            SCRATCH_POS.set(px, RING_Y + 0.005f, pz);
            SCRATCH_FWD.set(sx, 0f, sz);
            float phase = (leg * 0.5f) + s;
            CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, SCRATCH_FWD, UP, RING_GOLEM_SCALE,
                    new CopperGolemPuppet.Anim(phase, walking, carrying, bow), s);
        }
    }

    @Override
    public AABB getRenderBoundingBox(AtlasCoreBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(2.2, 2.4, 2.2);
    }
}
