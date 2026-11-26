package com.golemlogix.client;

import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.BRICK;
import static com.golemlogix.client.MachineParts.BRICK_TINT;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.FRAME;
import static com.golemlogix.client.MachineParts.GAUGE;
import static com.golemlogix.client.MachineParts.GLASS;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.RUNES;
import static com.golemlogix.client.MachineParts.SNOW;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.scaledRgb;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * The Cryo-Distillation Tower, three blocks high, drawn from its plinth: a dark brick plinth with blood-red runes, brass
 * valves, and a gauge; a glass column with a rising blood level and five glowing coil rings shifting from crimson through
 * amber and violet to icy cyan; a louvred brass head under snow and icicles with overhead pipes to adjacent jars; and a
 * copper golem turning the side feed valve while active.
 * <p>
 * Pixels in the north-facing frame: front is Z 0, viewer's right is -X; Y 0..48.
 */
public class DistillationTowerRenderer extends LegacyBer<DistillationTowerBlockEntity> {
    private static final Identifier BLOOD = mcBlock("water_still"), GLOW = mcBlock("white_concrete");
    private static final float COLUMN_BOTTOM = 14.5f, COLUMN_TOP = 33.5f;
    /** Coil ring colours from bottom to top: blood, ember, violet, indigo, ice. */
    private static final int[] COIL_COLORS = {0xD02424, 0xE8661E, 0xC03CB0, 0x7A5CFF, 0x5CE6F8};
    private static final float[] PLINTH_VALVE_X = {9.4f, 12.2f};
    private static final float[][] FRAME_CORNERS = {{1.2f, 1.2f}, {13.4f, 1.2f}, {1.2f, 13.4f}, {13.4f, 13.4f}};
    private static final float[][] HEAD_ICICLES = {{1.2f, 2.4f}, {4.4f, 1.2f}, {7.8f, 3.0f}, {10.6f, 1.6f}, {13.8f, 2.2f}};

    private static final Vector3f GOLEM_POS = new Vector3f(-3.2f * PIXEL, 18f * PIXEL, 3.9f * PIXEL);
    private static final Vector3f FWD_EAST = new Vector3f(1f, 0f, 0f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final BlockPos.MutableBlockPos JAR_PROBE = new BlockPos.MutableBlockPos();
    private static final double CREW_SIGHT_SQ = 40.0 * 40.0;
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final PoseStack SCRATCH_POSE = new PoseStack();

    private record Meshes(TextureAtlasSprite atlasMark, BerBuffers.Baked lower, BerBuffers.Baked head,
                          BerBuffers.Baked coilRing, BerBuffers.Baked bracket, BerBuffers.Baked wheel) {
    }

    private static Meshes meshes;

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    BerBuffers.bake(DistillationTowerRenderer::bakeLower),
                    BerBuffers.bake(DistillationTowerRenderer::bakeHead),
                    BerBuffers.bake(DistillationTowerRenderer::bakeCoilRing),
                    BerBuffers.bake((vc, p) -> {
                        box(vc, p.last(), -0.6f, 14.4f, 3.2f, 0.8f, 34f, 4.6f, COPPER, WHITE, 0);
                        MachineParts.platform(vc, p, -5f, 1.2f, 17.4f, 2.2f, 5.8f, 13.6f, 0.2f, 0);
                    }),
                    BerBuffers.bake((vc, p) -> MachineParts.wheelX(vc, p, 0f, 0f, 0f, 2.4f, 0f, 0)));
        }
        return meshes;
    }

    public DistillationTowerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(DistillationTowerBlockEntity be) {
        BlockPos p = be.getBlockPos();
        // outlet pipes extend over neighbouring jars
        return new AABB(p.getX() - 1.1, p.getY(), p.getZ() - 1.1, p.getX() + 2.1, p.getY() + 3.3, p.getZ() + 2.1);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(DistillationTowerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight,
                       int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(DistillationTowerBlock.FACING)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightCoords(level, pos.above());
        int lightTop = LevelRenderer.getLightCoords(level, pos.above(2));
        float work = Mth.lerp(partialTick, be.workPrev, be.work);
        float blood = Mth.lerp(partialTick, be.bloodPrev, be.blood);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        float time = ((level.getGameTime() + Math.floorMod(pos.asLong(), 53L)) % 72000L) + partialTick;
        Meshes baked = meshes();
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

        VertexConsumer vc = buffers.getBuffer(solid);
        // world-aligned outlet pipes connecting to adjacent Cryo-Source Jars
        boolean piped = renderJarPipes(vc, pose.last(), level, pos, lightTop);

        Direction facing = state.getValue(DistillationTowerBlock.FACING);
        boolean showCrew = LegacyBer.camera().distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5) < CREW_SIGHT_SQ;
        float golemTurn = showCrew ? MachineParts.golemTurn(level, pos, facing, 2) : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        baked.lower().draw(buffers, solid, p, light, 0);
        baked.head().draw(buffers, solid, p, lightTop, 0);

        float runeGlow = 0.45f + 0.55f * work * (0.75f + 0.25f * Mth.sin(time * 0.12f));
        int rune = 0xFF000000 | ((int) (0xF0 * runeGlow) << 16) | ((int) (0x30 * runeGlow) << 8) | (int) (0x28 * runeGlow);
        fitWall(vc, p, 1.2f, 2.4f, 14.8f, 5.6f, 0.54f, RUNES, rune, LightCoordsUtil.FULL_BRIGHT);

        if (!piped) {
            // open outlet spout when no adjacent jar is piped
            box(vc, p, 2.4f, 48f, 6.6f, 6.6f, 50.2f, 9.4f, BRASS, WHITE, lightTop);
            box(vc, p, 1.6f, 47.6f, 6.2f, 2.6f, 50.6f, 9.8f, ICE, WHITE, lightTop);
        }

        if (showCrew) {
            float turn = work > 0.05f ? phase * 14f : 0f;
            PoseStack.Pose bp = SCRATCH_POSE.last();
            bp.set(p);
            if (golemTurn != 0f) {
                SCRATCH_QUAT.rotationY(golemTurn * Mth.DEG_TO_RAD);
                bp.pose().rotateAround(SCRATCH_QUAT, 0.5f, 0.5f, 0.5f);
                bp.normal().rotate(SCRATCH_QUAT);
            }
            baked.bracket().draw(buffers, solid, bp, light, 0);

            SCRATCH_QUAT.rotationX(turn * Mth.DEG_TO_RAD);
            bp.pose().translate(-1.2f * PIXEL, 24f * PIXEL, 3.9f * PIXEL).rotate(SCRATCH_QUAT);
            bp.normal().rotate(SCRATCH_QUAT);
            baked.wheel().draw(buffers, solid, bp, light, 0);
        }

        renderCoil(vc, buffers, solid, baked.coilRing(), p, phase, work, time, blood);
        if (showCrew) {
            renderOperatorGolem(p, buffers, golemTurn, light, work, phase, time);
        }
        renderBloodAndGlass(pose, p, buffers, light, blood, work, time);
        pose.popPose();
    }

    private static void renderOperatorGolem(PoseStack.Pose basePose, MultiBufferSource buffers, float golemTurn,
                                            int light, float work, float phase, float time) {
        float reach = work > 0.05f ? -1.2f + 0.15f * Mth.sin(phase * 0.7f) : -0.85f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.25f, reach, -0.25f, 0.2f * Mth.sin(time * 0.013f));
        PoseStack.Pose bp = SCRATCH_POSE.last();
        bp.set(basePose);
        if (golemTurn != 0f) {
            SCRATCH_QUAT.rotationY(golemTurn * Mth.DEG_TO_RAD);
            bp.pose().rotateAround(SCRATCH_QUAT, 0.5f, 0.5f, 0.5f);
            bp.normal().rotate(SCRATCH_QUAT);
        }
        CopperGolemPuppet.render(SCRATCH_POSE, buffers, light, GOLEM_POS, FWD_EAST,
                UP, 0.45f, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.1f), 1, null, arms);
    }

    private static void renderBloodAndGlass(PoseStack pose, PoseStack.Pose p, MultiBufferSource buffers, int light,
                                            float blood, float work, float time) {
        VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        if (blood > 0.002f) {
            float top = bloodLevelY(blood);
            TextureAtlasSprite bloodSprite = sprite(BLOOD);
            box(tv, p, 2.8f, COLUMN_BOTTOM + 0.6f, 2.8f, 13.2f, top, 13.2f, bloodSprite, 0xE8901018, light);
            float wave = work > 0.05f ? 0.12f * Mth.sin(time * 0.35f) : 0f;
            box(tv, p, 2.9f, top - 0.25f + wave, 2.9f, 13.1f, top + wave, 13.1f, bloodSprite, 0xF0C02838, LightCoordsUtil.FULL_BRIGHT);
        }
        // inset a hair inside the frame rings (15.4 and 33.4) to avoid coplanar z-fighting
        box(tv, p, 2.6f, 15.43f, 2.6f, 13.4f, 33.37f, 13.4f, GLASS, 0x80F0F4FF, light);
    }

    private static void bakeLower(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        box(vc, p, 0, 0, 0, 16, 2, 16, DARK, WHITE, light);
        box(vc, p, 0.6f, 2, 0.6f, 15.4f, 12.6f, 15.4f, BRICK, BRICK_TINT, light);
        box(vc, p, 0.2f, 12.6f, 0.2f, 15.8f, 14.4f, 15.8f, DARK, WHITE, light);
        for (float x : PLINTH_VALVE_X) {
            box(vc, p, x, 7.2f, -0.2f, x + 1.4f, 8.6f, 0.6f, BRASS, WHITE, light);
            box(vc, p, x - 0.4f, 8.6f, -0.3f, x + 1.8f, 9.2f, 0.4f, BRASS, 0xFFE8C878, light);
        }
        fitWall(vc, p, 2.2f, 6.6f, 7.4f, 11.8f, 0.5f, GAUGE, WHITE, light);
        box(vc, p, 4.4f, 5.8f, 0.2f, 5.2f, 6.6f, 0.6f, BRASS, WHITE, light);
        for (float[] c : FRAME_CORNERS) {
            box(vc, p, c[0], 14.4f, c[1], c[0] + 1.4f, 34.4f, c[1] + 1.4f, FRAME, WHITE, light);
        }
        box(vc, p, 1, 14.4f, 1, 15, 15.4f, 15, FRAME, WHITE, light);
        box(vc, p, 1, 33.4f, 1, 15, 34.4f, 15, FRAME, WHITE, light);
    }

    private static void bakeHead(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        box(vc, p, 1.6f, 34.4f, 1.6f, 14.4f, 44f, 14.4f, BRASS, 0xFFD8B880, light);
        for (float y = 35.4f; y < 43.5f; y += 1.6f) {
            box(vc, p, 0.9f, y, 0.9f, 15.1f, y + 0.7f, 15.1f, BRASS, WHITE, light);
        }
        box(vc, p, 0.4f, 44f, 0.4f, 15.6f, 45.8f, 15.6f, SNOW, WHITE, light);
        box(vc, p, 3.6f, 45.8f, 3.6f, 12.4f, 46.6f, 12.4f, SNOW, WHITE, light);
        for (float[] i : HEAD_ICICLES) {
            box(vc, p, i[0], 44f - i[1], 0.1f, i[0] + 0.7f, 44f, 0.8f, ICE, WHITE, light);
            box(vc, p, 0.1f, 44f - i[1] * 0.8f, i[0], 0.8f, 44f, i[0] + 0.7f, ICE, WHITE, light);
        }
        box(vc, p, 6.6f, 46.6f, 6.6f, 9.4f, 50.2f, 9.4f, BRASS, WHITE, light);
        box(vc, p, 6.2f, 50.2f, 6.2f, 9.8f, 50.8f, 9.8f, ICE, WHITE, light);
    }

    /** Pixel Y height of the blood surface for tank fill fraction {@code blood} ({@code 0..1}). */
    private static float bloodLevelY(float blood) {
        return COLUMN_BOTTOM + 0.6f + (COLUMN_TOP - COLUMN_BOTTOM - 1.2f) * Math.min(1f, blood);
    }

    private static void bakeCoilRing(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite glowSprite = sprite(GLOW);
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12f;
            float cx = Mth.cos(a) * 3.9f, cz = Mth.sin(a) * 3.9f;
            float dy = (i / 12f) * 1.2f;
            box(vc, p, cx - 0.75f, dy - 0.45f, cz - 0.75f, cx + 0.75f, dy + 0.45f, cz + 0.75f, glowSprite, WHITE, 0);
        }
    }

    /** Five helical coil rings climbing the column; rings submerged in blood glow and pulse while distilling. */
    private static void renderCoil(VertexConsumer vc, MultiBufferSource buffers, RenderType solid, BerBuffers.Baked coilRing,
                                   PoseStack.Pose basePose, float phase, float work, float time, float blood) {
        float surface = blood > 0.002f ? bloodLevelY(blood) : COLUMN_BOTTOM;
        PoseStack.Pose cp = SCRATCH_POSE.last();
        for (int k = 0; k < COIL_COLORS.length; k++) {
            float y = COLUMN_BOTTOM + 2.6f + k * 3.6f;
            float pulse = 0.5f + 0.5f * Mth.sin(phase * 1.6f - k * 1.2f);
            float b = y > surface ? 0.12f : 0.35f + 0.65f * (0.25f + 0.75f * work) * (0.55f + 0.45f * pulse);
            int col = scaledRgb(COIL_COLORS[k], b);
            SCRATCH_QUAT.rotationY((phase * 22f + k * 17f) * Mth.DEG_TO_RAD);
            cp.set(basePose);
            cp.pose().translate(8f * PIXEL, y * PIXEL, 8f * PIXEL).rotate(SCRATCH_QUAT);
            cp.normal().rotate(SCRATCH_QUAT);
            coilRing.draw(buffers, solid, cp, LightCoordsUtil.FULL_BRIGHT, col);
        }
        float g = 0.2f + 0.5f * work * (0.8f + 0.2f * Mth.sin(time * 0.2f));
        int core = 0xFF000000 | ((int) (0x60 * g) << 16) | ((int) (0xC8 * g) << 8) | (int) (0xFF * g);
        box(vc, basePose, 7.4f, COLUMN_BOTTOM + 2f, 7.4f, 8.6f, COLUMN_TOP - 1.5f, 8.6f, sprite(GLOW), core, LightCoordsUtil.FULL_BRIGHT);
    }

    /** Overhead brass outlet pipes running from the tower riser down into adjacent Cryo-Source Jars. */
    private static boolean renderJarPipes(VertexConsumer vc, PoseStack.Pose p, Level level, BlockPos base, int light) {
        boolean any = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            JAR_PROBE.set(base.getX() + d.getStepX(), base.getY(), base.getZ() + d.getStepZ());
            BlockState s = level.getBlockState(JAR_PROBE);
            if (!(s.getBlock() instanceof CryoJarBlock) || s.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            JAR_PROBE.setY(base.getY() + 2);
            if (!level.getBlockState(JAR_PROBE).isAir()) {
                continue;
            }
            any = true;
            pipeRun(vc, p, d, 1.4f, 14.4f, 1.0f, 47.8f, 49.8f, BRASS, WHITE, light);
            pipeRun(vc, p, d, 7.4f, 8.4f, 1.3f, 47.5f, 50.1f, ICE, WHITE, light);
            pipeRun(vc, p, d, 14.4f, 17.6f, 1.6f, 47.2f, 50.4f, BRASS, 0xFFE8C878, light);
            pipeRun(vc, p, d, 14.8f, 17.2f, 1.2f, 32.4f, 47.2f, BRASS, WHITE, light);
            pipeRun(vc, p, d, 14.6f, 17.4f, 1.4f, 38.6f, 39.4f, ICE, WHITE, light);
        }
        return any;
    }

    private static void pipeRun(VertexConsumer vc, PoseStack.Pose p, Direction d, float u0, float u1, float halfW,
                                float y0, float y1, Identifier tex, int tint, int light) {
        float a0 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u0 : -u1);
        float a1 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u1 : -u0);
        if (d.getAxis() == Direction.Axis.X) {
            box(vc, p, a0, y0, 8 - halfW, a1, y1, 8 + halfW, tex, tint, light);
        } else {
            box(vc, p, 8 - halfW, y0, a0, 8 + halfW, y1, a1, tex, tint, light);
        }
    }
}
