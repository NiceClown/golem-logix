package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.blockentity.DistillationTowerBlockEntity;
import com.golemlogix.blocks.DistillationTowerBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.CryoJarRenderer.BRASS;
import static com.golemlogix.client.CryoJarRenderer.BRICK;
import static com.golemlogix.client.CryoJarRenderer.BRICK_TINT;
import static com.golemlogix.client.CryoJarRenderer.COPPER;
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

/**
 * The Cryo-Distillation Tower, three blocks high, drawn from its plinth. The plinth: dark bricks with blood-red runes, two brass
 * valves and a gauge. The column: a glass case in a verdigris frame; blood fills it as high as the tank is full, and a coil climbs
 * it, its rings under the blood glowing from blood red through orange and violet to icy cyan as the blood rises and freezes into
 * Source. The head: louvred brass under snow and
 * icicles with the outlet bent to the side. A copper golem on the right turns the valve on the feed pipe while the tower works.
 * <p>
 * Pixels in the north-facing frame: the front is Z 0, the viewer's right is -X; Y 0..48.
 */
public class DistillationTowerRenderer extends com.golemlogix.client.render.LegacyBer<DistillationTowerBlockEntity> {
    private static final Identifier BLOOD = CryoJarRenderer.mc("water_still"), GLOW = CryoJarRenderer.mc("white_concrete");
    private static final float COLUMN_BOTTOM = 14.5f, COLUMN_TOP = 33.5f;
    /** The coil's colours from the bottom up: blood, ember, violet, ice. */
    private static final int[] COIL = {0xD02424, 0xE8661E, 0xC03CB0, 0x7A5CFF, 0x5CE6F8};

    public DistillationTowerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(DistillationTowerBlockEntity be) {
        BlockPos p = be.getBlockPos();
        // the outlet pipes reach over the jars standing against the tower
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
        float time = level.getGameTime() + Math.floorMod(pos.asLong(), 53L) + partialTick;

        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        // the outlet pipes to the jars, in the world's own directions (before the head is turned to face the tower's front)
        boolean piped = jarPipes(vc, pose.last(), level, pos, lightTop);

        Direction facing = state.getValue(DistillationTowerBlock.FACING);
        // the golem's corner (the feed pipe, the valve, the bracket) goes to a free side, see CryoJarRenderer.golemTurn
        float golemTurn = CryoJarRenderer.golemTurn(level, pos, facing, 2);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        // ---- the plinth
        box(vc, p, 0, 0, 0, 16, 2, 16, DARK, WHITE, light);
        box(vc, p, 0.6f, 2, 0.6f, 15.4f, 12.6f, 15.4f, BRICK, BRICK_TINT, light);
        box(vc, p, 0.2f, 12.6f, 0.2f, 15.8f, 14.4f, 15.8f, DARK, WHITE, light);
        float runeGlow = 0.45f + 0.55f * work * (0.75f + 0.25f * Mth.sin(time * 0.12f));
        int rune = 0xFF000000 | ((int) (0xF0 * runeGlow) << 16) | ((int) (0x30 * runeGlow) << 8) | (int) (0x28 * runeGlow);
        fitWall(vc, p, 1.2f, 2.4f, 14.8f, 5.6f, 0.54f, RUNES, rune, LightCoordsUtil.FULL_BRIGHT);
        for (float x : new float[]{9.4f, 12.2f}) {
            box(vc, p, x, 7.2f, -0.2f, x + 1.4f, 8.6f, 0.6f, BRASS, WHITE, light);
            box(vc, p, x - 0.4f, 8.6f, -0.3f, x + 1.8f, 9.2f, 0.4f, BRASS, 0xFFE8C878, light);
        }
        fitWall(vc, p, 2.2f, 6.6f, 7.4f, 11.8f, 0.5f, GAUGE, WHITE, light);
        box(vc, p, 4.4f, 5.8f, 0.2f, 5.2f, 6.6f, 0.6f, BRASS, WHITE, light);

        // ---- the column frame and the feed pipe on the right with the golem's valve
        for (float[] c : new float[][]{{1.2f, 1.2f}, {13.4f, 1.2f}, {1.2f, 13.4f}, {13.4f, 13.4f}}) {
            box(vc, p, c[0], 14.4f, c[1], c[0] + 1.4f, 34.4f, c[1] + 1.4f, FRAME, WHITE, light);
        }
        box(vc, p, 1, 14.4f, 1, 15, 15.4f, 15, FRAME, WHITE, light);
        box(vc, p, 1, 33.4f, 1, 15, 34.4f, 15, FRAME, WHITE, light);
        float turn = work > 0.05f ? phase * 14f : 0f;
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(golemTurn), 0.5f, 0.5f, 0.5f);
        box(vc, pose.last(), -0.6f, 14.4f, 3.2f, 0.8f, 34f, 4.6f, COPPER, WHITE, light);
        CryoJarRenderer.wheel(vc, pose, -1.2f, 24f, 3.9f, 2.4f, turn, light);
        CryoJarRenderer.platform(vc, pose, -5f, 1.2f, 17.4f, 2.2f, 5.8f, 13.6f, 0.2f, light);
        pose.popPose();

        // ---- the head: louvred brass under snow, icicles, the outlet bent to the right
        box(vc, p, 1.6f, 34.4f, 1.6f, 14.4f, 44f, 14.4f, BRASS, 0xFFD8B880, lightTop);
        for (float y = 35.4f; y < 43.5f; y += 1.6f) {
            box(vc, p, 0.9f, y, 0.9f, 15.1f, y + 0.7f, 15.1f, BRASS, WHITE, lightTop);
        }
        box(vc, p, 0.4f, 44f, 0.4f, 15.6f, 45.8f, 15.6f, SNOW, WHITE, lightTop);
        box(vc, p, 3.6f, 45.8f, 3.6f, 12.4f, 46.6f, 12.4f, SNOW, WHITE, lightTop);
        for (float[] i : new float[][]{{1.2f, 2.4f}, {4.4f, 1.2f}, {7.8f, 3.0f}, {10.6f, 1.6f}, {13.8f, 2.2f}}) {
            box(vc, p, i[0], 44f - i[1], 0.1f, i[0] + 0.7f, 44f, 0.8f, ICE, WHITE, lightTop);
            box(vc, p, 0.1f, 44f - i[1] * 0.8f, i[0], 0.8f, 44f, i[0] + 0.7f, ICE, WHITE, lightTop);
        }
        box(vc, p, 6.6f, 46.6f, 6.6f, 9.4f, 50.2f, 9.4f, BRASS, WHITE, lightTop);
        if (!piped) {
            // no jar against the tower: the outlet stands open, bent to the right
            box(vc, p, 2.4f, 48f, 6.6f, 6.6f, 50.2f, 9.4f, BRASS, WHITE, lightTop);
            box(vc, p, 1.6f, 47.6f, 6.2f, 2.6f, 50.6f, 9.8f, ICE, WHITE, lightTop);
        }
        box(vc, p, 6.2f, 50.2f, 6.2f, 9.8f, 50.8f, 9.8f, ICE, WHITE, lightTop);

        // ---- inside the glass: the coil, then (translucent) the blood and the glass itself
        coil(vc, pose, phase, work, time, blood);
        // the golem last: its own buffer ends the one above
        float reach = work > 0.05f ? -1.2f + 0.15f * Mth.sin(phase * 0.7f) : -0.85f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.25f, reach, -0.25f, 0.2f * Mth.sin(time * 0.013f));
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(golemTurn), 0.5f, 0.5f, 0.5f);
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(-3.2f / 16f, 18f / 16f, 3.9f / 16f), new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f), 0.45f, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.1f), 1, null, arms);
        pose.popPose();
        VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        // the blood stands in the column as high as the tank is full (the whole column is the tank)
        if (blood > 0.002f) {
            float top = level(blood);
            box(tv, p, 2.8f, COLUMN_BOTTOM + 0.6f, 2.8f, 13.2f, top, 13.2f, BLOOD, 0xE8901018, light);
            // the surface: a lighter skin that trembles while the tower works
            float wave = work > 0.05f ? 0.12f * Mth.sin(time * 0.35f) : 0f;
            box(tv, p, 2.9f, top - 0.25f + wave, 2.9f, 13.1f, top + wave, 13.1f, BLOOD, 0xF0C02838, LightCoordsUtil.FULL_BRIGHT);
        }
        // a hair inside the frame rings (their faces are at 15.4 and 33.4: glass in the same planes flickered)
        box(tv, p, 2.6f, 15.43f, 2.6f, 13.4f, 33.37f, 13.4f, GLASS, 0x80F0F4FF, light);
        pose.popPose();
    }

    /** The height (pixels) the blood reaches in the column for a tank this full (0..1). */
    private static float level(float blood) {
        return COLUMN_BOTTOM + 0.6f + (COLUMN_TOP - COLUMN_BOTTOM - 1.2f) * Math.min(1f, blood);
    }

    /**
     * The coil: five rings stacked up the column, a pulse running up them while the tower works. Only the rings under the blood glow
     * (the rest stand dark and cold), so the column shows from afar how full the tower is.
     */
    private static void coil(VertexConsumer vc, PoseStack pose, float phase, float work, float time, float blood) {
        float surface = blood > 0.002f ? level(blood) : COLUMN_BOTTOM;
        int rings = COIL.length;
        for (int k = 0; k < rings; k++) {
            float y = COLUMN_BOTTOM + 2.6f + k * 3.6f;
            float pulse = 0.5f + 0.5f * Mth.sin(phase * 1.6f - k * 1.2f);
            float b = 0.35f + 0.65f * (0.25f + 0.75f * work) * (0.55f + 0.45f * pulse);
            if (y > surface) {
                b = 0.12f;
            }
            int c = COIL[k];
            int col = 0xFF000000 | ((int) (((c >> 16) & 255) * b) << 16) | ((int) (((c >> 8) & 255) * b) << 8) | (int) ((c & 255) * b);
            pose.pushPose();
            pose.translate(8f / 16f, y / 16f, 8f / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(phase * 22f + k * 17f));
            PoseStack.Pose p = pose.last();
            float r = 3.9f;
            for (int i = 0; i < 12; i++) {
                float a = i * Mth.TWO_PI / 12f;
                float cx = Mth.cos(a) * r, cz = Mth.sin(a) * r;
                // a slight helix: each ring leans towards the next one
                float dy = (i / 12f) * 1.2f;
                box(vc, p, cx - 0.75f, dy - 0.45f, cz - 0.75f, cx + 0.75f, dy + 0.45f, cz + 0.75f, GLOW, col, LightCoordsUtil.FULL_BRIGHT);
            }
            pose.popPose();
        }
        // the core of the column: a faint cold glow that brightens with the work
        float g = 0.2f + 0.5f * work * (0.8f + 0.2f * Mth.sin(time * 0.2f));
        int core = 0xFF000000 | ((int) (0x60 * g) << 16) | ((int) (0xC8 * g) << 8) | (int) (0xFF * g);
        box(vc, pose.last(), 7.4f, COLUMN_BOTTOM + 2f, 7.4f, 8.6f, COLUMN_TOP - 1.5f, 8.6f, GLOW, core, LightCoordsUtil.FULL_BRIGHT);
    }

    /**
     * The outlet pipes to the Cryo-Source Jars standing right against the plinth (on the same ground): from the riser on the head a
     * pipe runs over to each jar, bends down in a brass elbow and drops into the jar's vent. Returns whether any jar is piped.
     * Pixels in the world's own frame of the plinth block (0..16, Y 0..48).
     */
    private static boolean jarPipes(VertexConsumer vc, PoseStack.Pose p, Level level, BlockPos base, int light) {
        boolean any = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = level.getBlockState(base.relative(d));
            // a jar on the same ground; the pipe passes through the block over the jar, so that one must be empty
            if (!(s.getBlock() instanceof com.golemlogix.blocks.CryoJarBlock)
                    || s.getValue(com.golemlogix.blocks.CryoJarBlock.HALF) != DoubleBlockHalf.LOWER
                    || !level.getBlockState(base.relative(d).above(2)).isAir()) {
                continue;
            }
            any = true;
            // along the pipe (from the tower's centre outwards), across it, and the height
            run(vc, p, d, 1.4f, 14.4f, 1.0f, 47.8f, 49.8f, BRASS, WHITE, light);
            run(vc, p, d, 7.4f, 8.4f, 1.3f, 47.5f, 50.1f, ICE, WHITE, light);
            run(vc, p, d, 14.4f, 17.6f, 1.6f, 47.2f, 50.4f, BRASS, 0xFFE8C878, light);
            // down to the top of the jar's vent (its icy cap ends at 32.4 px)
            run(vc, p, d, 14.8f, 17.2f, 1.2f, 32.4f, 47.2f, BRASS, WHITE, light);
            run(vc, p, d, 14.6f, 17.4f, 1.4f, 38.6f, 39.4f, ICE, WHITE, light);
        }
        return any;
    }

    /** A box along direction d: from u0 to u1 pixels out of the block centre, half as wide as w across, from y0 to y1. */
    private static void run(VertexConsumer vc, PoseStack.Pose p, Direction d, float u0, float u1, float w, float y0, float y1,
                            Identifier tex, int tint, int light) {
        float a0 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u0 : -u1);
        float a1 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u1 : -u0);
        if (d.getAxis() == Direction.Axis.X) {
            box(vc, p, a0, y0, 8 - w, a1, y1, 8 + w, tex, tint, light);
        } else {
            box(vc, p, 8 - w, y0, a0, 8 + w, y1, a1, tex, tint, light);
        }
    }
}
