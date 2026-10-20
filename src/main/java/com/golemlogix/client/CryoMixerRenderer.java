package com.golemlogix.client;

import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.blocks.CryoMixerBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

/**
 * The Cryo-Mixer, two blocks high, drawn from its lower block. Below: a dark brick plinth with a rune band (cyan while it works),
 * louvred brass sides under snow around a verdigris copper module with a gauge and a gear that turns while it works, a brass tray in
 * front with the snow or ice made so far. Above: two glass flasks on brass rings with snowy rims and icicles, water on the left and
 * icy Cryo-Source with a glowing spiral on the right, each piped into the module. A copper golem on its bracket at the side turns the
 * valve (and steps to a free side when something stands there).
 * <p>
 * Pixels in the north-facing frame: the front is Z 0, the viewer's right is -X; Y 0..32 over both blocks.
 */
public class CryoMixerRenderer extends com.golemlogix.client.render.LegacyBer<CryoMixerBlockEntity> {
    private static final Identifier WATER = CryoJarRenderer.mc("water_still"), GLOW = CryoJarRenderer.mc("white_concrete"),
            ICE_BLOCK = CryoJarRenderer.mc("ice"), SNOW_BLOCK = CryoJarRenderer.mc("snow");
    private static final int[] SPIRAL = {0x5CE6F8, 0x7A5CFF, 0xC03CB0};
    /** The flasks' inside (their bottom and top), and their centres across. */
    private static final float FLASK_BOTTOM = 17.06f, FLASK_TOP = 27.4f, WATER_X = 12.1f, CRYO_X = 3.9f, FLASK_Z = 8f;

    public CryoMixerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CryoMixerBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.6, p.getY(), p.getZ() - 0.6, p.getX() + 1.6, p.getY() + 2.1, p.getZ() + 1.6);
    }

    @Override
    public void render(CryoMixerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CryoMixerBlock.FACING)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightCoords(level, pos.above());
        float work = Mth.lerp(partialTick, be.workPrev, be.work);
        float water = Mth.lerp(partialTick, be.waterPrev, be.waterLevel);
        float cryo = Mth.lerp(partialTick, be.cryoPrev, be.cryoLevel);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        Direction facing = state.getValue(CryoMixerBlock.FACING);
        float golemTurn = CryoJarRenderer.golemTurn(level, pos, facing, 2);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();

        // ---- the plinth: a dark foot, bricks with a rune band, a ledge
        box(vc, p, 0.4f, 0, 0.4f, 15.6f, 1.4f, 15.6f, DARK, WHITE, light);
        box(vc, p, 1, 1.4f, 1, 15, 5, 15, BRICK, BRICK_TINT, light);
        float glow = 0.25f + 0.75f * work * (0.8f + 0.2f * Mth.sin(phase * 0.9f));
        int rune = 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
        fitWall(vc, p, 1.6f, 1.7f, 14.4f, 4.6f, 0.94f, RUNES, rune, work > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);
        box(vc, p, 0.8f, 5, 0.8f, 15.2f, 6, 15.2f, DARK, WHITE, light);

        // ---- the louvred brass sides under snow, with icicles
        for (float[] s : new float[][]{{1.0f, 5.4f}, {10.6f, 15.0f}}) {
            box(vc, p, s[0], 6, 1.6f, s[1], 15, 14.4f, BRASS, 0xFFD8B880, light);
            for (float y = 6.8f; y < 14.4f; y += 1.5f) {
                box(vc, p, s[0] - 0.4f, y, 1.2f, s[1] + 0.4f, y + 0.6f, 14.8f, BRASS, WHITE, light);
            }
            box(vc, p, s[0] - 0.3f, 15, 1.3f, s[1] + 0.3f, 15.8f, 14.7f, SNOW, WHITE, light);
            for (float[] i : new float[][]{{s[0] + 0.4f, 1.4f}, {s[0] + 2.4f, 0.8f}, {s[1] - 1.2f, 1.8f}}) {
                box(vc, p, i[0], 15 - i[1], 1.0f, i[0] + 0.6f, 15, 1.6f, ICE, WHITE, light);
            }
        }

        // ---- the copper module: a gauge on its face, a gear turning while it works, a brass coupling on top
        box(vc, p, 5.4f, 6, 2.2f, 10.6f, 16, 13.8f, FRAME, WHITE, light);
        box(vc, p, 5.2f, 15.4f, 2.0f, 10.8f, 16.2f, 14.0f, FRAME, 0xFFB8D8C8, light);
        fitWall(vc, p, 6.0f, 11.2f, 10.0f, 14.6f, 1.8f, GAUGE, WHITE, light);
        gear(vc, pose, 8f, 8.6f, 1.6f, phase * 40f, light);
        box(vc, p, 6.8f, 16.2f, 6.8f, 9.2f, 17.0f, 9.2f, BRASS, 0xFFE8C878, light);

        // ---- the tray in front with the snow or ice made so far
        box(vc, p, 9.4f, 5.4f, -1.6f, 15.4f, 6.2f, 2.4f, BRASS, 0xFFD8B880, light);
        box(vc, p, 9.4f, 6.2f, -1.6f, 15.4f, 7.2f, -1.0f, BRASS, WHITE, light);
        box(vc, p, 9.4f, 6.2f, -1.0f, 10.0f, 7.2f, 2.4f, BRASS, WHITE, light);
        box(vc, p, 14.8f, 6.2f, -1.0f, 15.4f, 7.2f, 2.4f, BRASS, WHITE, light);
        int tray = be.clientTray();
        Identifier cube = tray < 0 ? ICE_BLOCK : SNOW_BLOCK;
        for (int i = 0; i < Math.min(3, Math.abs(tray)); i++) {
            float x0 = 10.3f + i * 1.5f;
            float y0 = 6.2f + (i == 1 ? 0.3f : 0f);
            box(vc, p, x0, y0, -0.6f + (i % 2) * 0.6f, x0 + 2.2f, y0 + 2.2f, 1.6f + (i % 2) * 0.6f, cube, WHITE, light);
        }

        // ---- the flasks' brass rings, snowy rims, and the pipes from both flasks into the module
        int lightTop = LevelRenderer.getLightCoords(level, pos.above(2));
        for (float cx : new float[]{WATER_X, CRYO_X}) {
            box(vc, p, cx - 3.1f, 15.8f, FLASK_Z - 3.6f, cx + 3.1f, 17.0f, FLASK_Z + 3.6f, BRASS, 0xFFD8B880, lightTop);
            box(vc, p, cx - 3.2f, FLASK_TOP + 0.04f, FLASK_Z - 3.7f, cx + 3.2f, FLASK_TOP + 1.2f, FLASK_Z + 3.7f, SNOW, WHITE, lightTop);
            for (float[] i : new float[][]{{cx - 2.8f, 1.6f}, {cx - 0.6f, 0.9f}, {cx + 1.6f, 1.3f}}) {
                box(vc, p, i[0], FLASK_TOP + 0.04f - i[1], FLASK_Z - 3.9f, i[0] + 0.6f, FLASK_TOP + 0.04f, FLASK_Z - 3.3f, ICE, WHITE, lightTop);
            }
            // a riser up from the rim
            box(vc, p, cx - 0.6f, FLASK_TOP + 1.2f, FLASK_Z - 0.6f, cx + 0.6f, 29.0f, FLASK_Z + 0.6f, COPPER, WHITE, lightTop);
        }
        // over from both risers to the middle, down into the coupling on the module
        box(vc, p, CRYO_X - 0.6f, 29.0f, FLASK_Z - 0.6f, WATER_X + 0.6f, 30.2f, FLASK_Z + 0.6f, COPPER, WHITE, lightTop);
        box(vc, p, 7.4f, 17.0f, 7.4f, 8.6f, 29.0f, 8.6f, COPPER, WHITE, lightTop);
        box(vc, p, 7.0f, 29.4f, 6.9f, 9.0f, 30.8f, 9.1f, BRASS, 0xFFE8C878, lightTop);

        // ---- the spiral in the Cryo-Source flask, glowing while it works
        spiral(vc, pose, cryo, phase, work);

        // ---- the golem's corner (the valve on the right front post, its bracket), turned to a free side
        float turn = work > 0.05f ? phase * 30f : 0f;
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(golemTurn), 0.5f, 0.5f, 0.5f);
        box(vc, pose.last(), 0.2f, 6.4f, 3.0f, 1.2f, 14.6f, 4.0f, COPPER, WHITE, light);
        CryoJarRenderer.wheel(vc, pose, -0.2f, 10.4f, 3.5f, 2.0f, turn, light);
        CryoJarRenderer.platform(vc, pose, -3.8f, 1.0f, 6.0f, 1.6f, 5.4f, 1.4f, 0.4f, light);
        pose.popPose();
        // the golem last: its own buffer ends the one above
        float reach = work > 0.05f ? -1.1f + 0.12f * Mth.sin(phase * 0.8f) : -0.85f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(phase * 0.05f));
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(golemTurn), 0.5f, 0.5f, 0.5f);
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(-2.1f / 16f, 6.6f / 16f, 3.5f / 16f), new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f), 0.36f, new CopperGolemPuppet.Anim(phase * 0.5f, false, false, 0.08f), 1, null, arms);
        pose.popPose();

        // ---- translucent: the water, the Cryo-Source, the glass over them
        VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float span = FLASK_TOP - FLASK_BOTTOM - 0.2f;
        if (water > 0.01f) {
            float top = FLASK_BOTTOM + 0.1f + span * Math.min(1f, water);
            box(tv, p, WATER_X - 2.4f, FLASK_BOTTOM + 0.1f, FLASK_Z - 2.9f, WATER_X + 2.4f, top, FLASK_Z + 2.9f, WATER, 0xD0386EC8, lightTop);
        }
        if (cryo > 0.01f) {
            float top = FLASK_BOTTOM + 0.1f + span * Math.min(1f, cryo);
            box(tv, p, CRYO_X - 2.4f, FLASK_BOTTOM + 0.1f, FLASK_Z - 2.9f, CRYO_X + 2.4f, top, FLASK_Z + 2.9f, CRYO, 0xC8FFFFFF,
                    LightCoordsUtil.FULL_BRIGHT);
        }
        for (float cx : new float[]{WATER_X, CRYO_X}) {
            box(tv, p, cx - 2.8f, FLASK_BOTTOM, FLASK_Z - 3.3f, cx + 2.8f, FLASK_TOP, FLASK_Z + 3.3f, GLASS, 0x90E8F8FF, lightTop);
        }
        pose.popPose();
    }

    /** A brass gear on the module's face (z just in front of it), turned by angle degrees. */
    private static void gear(VertexConsumer vc, PoseStack pose, float cx, float cy, float z, float angle, int light) {
        pose.pushPose();
        pose.rotateAround(Axis.ZP.rotationDegrees(angle), cx / 16f, cy / 16f, z / 16f);
        PoseStack.Pose p = pose.last();
        box(vc, p, cx - 1.6f, cy - 1.6f, z - 0.3f, cx + 1.6f, cy + 1.6f, z + 0.3f, BRASS, 0xFFE8C878, light);
        box(vc, p, cx - 0.5f, cy - 0.5f, z - 0.6f, cx + 0.5f, cy + 0.5f, z, BRASS, 0xFF8A6A30, light);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8f;
            float tx = cx + Mth.cos(a) * 2.1f, ty = cy + Mth.sin(a) * 2.1f;
            box(vc, p, tx - 0.45f, ty - 0.45f, z - 0.25f, tx + 0.45f, ty + 0.45f, z + 0.25f, BRASS, 0xFFD8B880, light);
        }
        pose.popPose();
    }

    /** Three rings of glowing beads climbing the Cryo-Source flask as high as it is full; they turn and pulse while it works. */
    private static void spiral(VertexConsumer vc, PoseStack pose, float level, float phase, float work) {
        float top = FLASK_BOTTOM + 0.3f + (FLASK_TOP - FLASK_BOTTOM - 1f) * Math.min(1f, level);
        for (int k = 0; k < SPIRAL.length; k++) {
            float y = FLASK_BOTTOM + 2.2f + k * 3.0f;
            if (y > top) {
                break;
            }
            float b = 0.35f + 0.65f * (0.3f + 0.7f * work) * (0.6f + 0.4f * Mth.sin(phase * 1.4f - k * 1.3f));
            int c = SPIRAL[k];
            int col = 0xFF000000 | ((int) (((c >> 16) & 255) * b) << 16) | ((int) (((c >> 8) & 255) * b) << 8) | (int) ((c & 255) * b);
            pose.pushPose();
            pose.translate(CRYO_X / 16f, y / 16f, FLASK_Z / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(phase * 30f + k * 40f));
            PoseStack.Pose p = pose.last();
            for (int i = 0; i < 8; i++) {
                float a = i * Mth.TWO_PI / 8f;
                float bx = Mth.cos(a) * 1.7f, bz = Mth.sin(a) * 1.7f, dy = i / 8f * 0.9f;
                box(vc, p, bx - 0.4f, dy - 0.3f, bz - 0.4f, bx + 0.4f, dy + 0.3f, bz + 0.4f, GLOW, col, LightCoordsUtil.FULL_BRIGHT);
            }
            pose.popPose();
        }
    }
}
