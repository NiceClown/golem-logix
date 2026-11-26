package com.golemlogix.client;

import com.golemlogix.blockentity.CryoMixerBlockEntity;
import com.golemlogix.blocks.CryoMixerBlock;
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
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.BRICK;
import static com.golemlogix.client.MachineParts.BRICK_TINT;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.CRYO;
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
 * The Cryo-Mixer, two blocks high, drawn from its lower block: a dark brick plinth with glowing cyan runes, louvred brass
 * wings around a verdigris module with a gauge and spinning gear, a front output tray holding snow/ice cubes, twin upper
 * glass flasks (water left, Cryo-Source with a glowing spiral right), and a copper golem tending the side valve.
 * <p>
 * Pixels in the north-facing frame: front is Z 0, viewer's right is -X; Y 0..32 over both blocks.
 */
public class CryoMixerRenderer extends LegacyBer<CryoMixerBlockEntity> {
    private static final Identifier WATER = mcBlock("water_still"), GLOW = mcBlock("white_concrete"),
            ICE_BLOCK = mcBlock("ice"), SNOW_BLOCK = mcBlock("snow");
    private static final int[] SPIRAL_COLORS = {0x5CE6F8, 0x7A5CFF, 0xC03CB0};
    /** Inner bounds and horizontal centres of the twin upper flasks. */
    private static final float FLASK_BOTTOM = 17.06f, FLASK_TOP = 27.4f, WATER_X = 12.1f, CRYO_X = 3.9f, FLASK_Z = 8f;
    private static final float[][] SIDE_WINGS = {{1.0f, 5.4f}, {10.6f, 15.0f}};
    private static final float[] FLASK_CENTERS_X = {WATER_X, CRYO_X};

    private static final Vector3f GOLEM_POS = new Vector3f(-2.1f * PIXEL, 6.6f * PIXEL, 3.5f * PIXEL);
    private static final Vector3f FWD_EAST = new Vector3f(1f, 0f, 0f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

    private record Meshes(TextureAtlasSprite atlasMark, BerBuffers.Baked lower, BerBuffers.Baked upper, BerBuffers.Baked gear) {
    }

    private static Meshes meshes;

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    BerBuffers.bake(CryoMixerRenderer::bakeLower),
                    BerBuffers.bake(CryoMixerRenderer::bakeUpper),
                    BerBuffers.bake(CryoMixerRenderer::bakeGear));
        }
        return meshes;
    }

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
        int lightTop = LevelRenderer.getLightCoords(level, pos.above(2));
        float work = Mth.lerp(partialTick, be.workPrev, be.work);
        float water = Mth.lerp(partialTick, be.waterPrev, be.waterLevel);
        float cryo = Mth.lerp(partialTick, be.cryoPrev, be.cryoLevel);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        Direction facing = state.getValue(CryoMixerBlock.FACING);
        float golemTurn = MachineParts.golemTurn(level, pos, facing, 2);
        Meshes baked = meshes();
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        baked.lower().draw(buffers, solid, p, light, 0);
        baked.upper().draw(buffers, solid, p, lightTop, 0);

        // spinning brass gear on the front module
        pose.pushPose();
        pose.translate(8f * PIXEL, 8.6f * PIXEL, 1.6f * PIXEL);
        pose.mulPose(Axis.ZP.rotationDegrees(phase * 40f));
        baked.gear().draw(buffers, solid, pose.last(), light, 0);
        pose.popPose();

        VertexConsumer vc = buffers.getBuffer(solid);
        float glow = 0.25f + 0.75f * work * (0.8f + 0.2f * Mth.sin(phase * 0.9f));
        int rune = 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
        fitWall(vc, p, 1.6f, 1.7f, 14.4f, 4.6f, 0.94f, RUNES, rune, work > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);

        renderTrayOutput(vc, p, be.clientTray(), light);
        renderSpiral(vc, pose, cryo, phase, work);
        renderGolemBracket(vc, pose, buffers, golemTurn, light, work, phase);
        renderFluidsAndGlass(p, buffers, water, cryo, lightTop);
        pose.popPose();
    }

    private static void renderTrayOutput(VertexConsumer vc, PoseStack.Pose p, int tray, int light) {
        if (tray == 0) {
            return;
        }
        TextureAtlasSprite cubeSprite = sprite(tray < 0 ? ICE_BLOCK : SNOW_BLOCK);
        for (int i = 0; i < Math.min(3, Math.abs(tray)); i++) {
            float x0 = 10.3f + i * 1.5f;
            float y0 = 6.2f + (i == 1 ? 0.3f : 0f);
            box(vc, p, x0, y0, -0.6f + (i % 2) * 0.6f, x0 + 2.2f, y0 + 2.2f, 1.6f + (i % 2) * 0.6f, cubeSprite, WHITE, light);
        }
    }

    private static void renderGolemBracket(VertexConsumer vc, PoseStack pose, MultiBufferSource buffers,
                                           float golemTurn, int light, float work, float phase) {
        float turn = work > 0.05f ? phase * 30f : 0f;
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(golemTurn), 0.5f, 0.5f, 0.5f);
        box(vc, pose.last(), 0.2f, 6.4f, 3.0f, 1.2f, 14.6f, 4.0f, COPPER, WHITE, light);
        MachineParts.wheelX(vc, pose, -0.2f, 10.4f, 3.5f, 2.0f, turn, light);
        MachineParts.platform(vc, pose, -3.8f, 1.0f, 6.0f, 1.6f, 5.4f, 1.4f, 0.4f, light);
        // draw puppet last so its own render type ends the cutout block batch cleanly
        float reach = work > 0.05f ? -1.1f + 0.12f * Mth.sin(phase * 0.8f) : -0.85f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(phase * 0.05f));
        CopperGolemPuppet.render(pose, buffers, light, GOLEM_POS, FWD_EAST,
                UP, 0.36f, new CopperGolemPuppet.Anim(phase * 0.5f, false, false, 0.08f), 1, null, arms);
        pose.popPose();
    }

    private static void renderFluidsAndGlass(PoseStack.Pose p, MultiBufferSource buffers, float water, float cryo, int lightTop) {
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
        TextureAtlasSprite glassSprite = sprite(GLASS);
        box(tv, p, WATER_X - 2.8f, FLASK_BOTTOM, FLASK_Z - 3.3f, WATER_X + 2.8f, FLASK_TOP, FLASK_Z + 3.3f, glassSprite, 0x90E8F8FF, lightTop);
        box(tv, p, CRYO_X - 2.8f, FLASK_BOTTOM, FLASK_Z - 3.3f, CRYO_X + 2.8f, FLASK_TOP, FLASK_Z + 3.3f, glassSprite, 0x90E8F8FF, lightTop);
    }

    private static void bakeLower(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        box(vc, p, 0.4f, 0, 0.4f, 15.6f, 1.4f, 15.6f, DARK, WHITE, light);
        box(vc, p, 1, 1.4f, 1, 15, 5, 15, BRICK, BRICK_TINT, light);
        box(vc, p, 0.8f, 5, 0.8f, 15.2f, 6, 15.2f, DARK, WHITE, light);
        for (float[] s : SIDE_WINGS) {
            box(vc, p, s[0], 6, 1.6f, s[1], 15, 14.4f, BRASS, 0xFFD8B880, light);
            for (float y = 6.8f; y < 14.4f; y += 1.5f) {
                box(vc, p, s[0] - 0.4f, y, 1.2f, s[1] + 0.4f, y + 0.6f, 14.8f, BRASS, WHITE, light);
            }
            box(vc, p, s[0] - 0.3f, 15, 1.3f, s[1] + 0.3f, 15.8f, 14.7f, SNOW, WHITE, light);
            for (float[] i : new float[][]{{s[0] + 0.4f, 1.4f}, {s[0] + 2.4f, 0.8f}, {s[1] - 1.2f, 1.8f}}) {
                box(vc, p, i[0], 15 - i[1], 1.0f, i[0] + 0.6f, 15, 1.6f, ICE, WHITE, light);
            }
        }
        // central verdigris module, gauge, coupling, and front tray
        box(vc, p, 5.4f, 6, 2.2f, 10.6f, 16, 13.8f, FRAME, WHITE, light);
        box(vc, p, 5.2f, 15.4f, 2.0f, 10.8f, 16.2f, 14.0f, FRAME, 0xFFB8D8C8, light);
        fitWall(vc, p, 6.0f, 11.2f, 10.0f, 14.6f, 1.8f, GAUGE, WHITE, light);
        box(vc, p, 6.8f, 16.2f, 6.8f, 9.2f, 17.0f, 9.2f, BRASS, 0xFFE8C878, light);
        box(vc, p, 9.4f, 5.4f, -1.6f, 15.4f, 6.2f, 2.4f, BRASS, 0xFFD8B880, light);
        box(vc, p, 9.4f, 6.2f, -1.6f, 15.4f, 7.2f, -1.0f, BRASS, WHITE, light);
        box(vc, p, 9.4f, 6.2f, -1.0f, 10.0f, 7.2f, 2.4f, BRASS, WHITE, light);
        box(vc, p, 14.8f, 6.2f, -1.0f, 15.4f, 7.2f, 2.4f, BRASS, WHITE, light);
    }

    private static void bakeUpper(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        for (float cx : FLASK_CENTERS_X) {
            box(vc, p, cx - 3.1f, 15.8f, FLASK_Z - 3.6f, cx + 3.1f, 17.0f, FLASK_Z + 3.6f, BRASS, 0xFFD8B880, light);
            box(vc, p, cx - 3.2f, FLASK_TOP + 0.04f, FLASK_Z - 3.7f, cx + 3.2f, FLASK_TOP + 1.2f, FLASK_Z + 3.7f, SNOW, WHITE, light);
            for (float[] i : new float[][]{{cx - 2.8f, 1.6f}, {cx - 0.6f, 0.9f}, {cx + 1.6f, 1.3f}}) {
                box(vc, p, i[0], FLASK_TOP + 0.04f - i[1], FLASK_Z - 3.9f, i[0] + 0.6f, FLASK_TOP + 0.04f, FLASK_Z - 3.3f, ICE, WHITE, light);
            }
            box(vc, p, cx - 0.6f, FLASK_TOP + 1.2f, FLASK_Z - 0.6f, cx + 0.6f, 29.0f, FLASK_Z + 0.6f, COPPER, WHITE, light);
        }
        box(vc, p, CRYO_X - 0.6f, 29.0f, FLASK_Z - 0.6f, WATER_X + 0.6f, 30.2f, FLASK_Z + 0.6f, COPPER, WHITE, light);
        box(vc, p, 7.4f, 17.0f, 7.4f, 8.6f, 29.0f, 8.6f, COPPER, WHITE, light);
        box(vc, p, 7.0f, 29.4f, 6.9f, 9.0f, 30.8f, 9.1f, BRASS, 0xFFE8C878, light);
    }

    private static void bakeGear(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        box(vc, p, -1.6f, -1.6f, -0.3f, 1.6f, 1.6f, 0.3f, BRASS, 0xFFE8C878, 0);
        box(vc, p, -0.5f, -0.5f, -0.6f, 0.5f, 0.5f, 0f, BRASS, 0xFF8A6A30, 0);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8f;
            float tx = Mth.cos(a) * 2.1f, ty = Mth.sin(a) * 2.1f;
            box(vc, p, tx - 0.45f, ty - 0.45f, -0.25f, tx + 0.45f, ty + 0.45f, 0.25f, BRASS, 0xFFD8B880, 0);
        }
    }

    /** Three rings of glowing beads rising inside the Cryo-Source flask up to the liquid surface. */
    private static void renderSpiral(VertexConsumer vc, PoseStack pose, float level, float phase, float work) {
        float top = FLASK_BOTTOM + 0.3f + (FLASK_TOP - FLASK_BOTTOM - 1f) * Math.min(1f, level);
        TextureAtlasSprite glowSprite = sprite(GLOW);
        for (int k = 0; k < SPIRAL_COLORS.length; k++) {
            float y = FLASK_BOTTOM + 2.2f + k * 3.0f;
            if (y > top) {
                break;
            }
            float b = 0.35f + 0.65f * (0.3f + 0.7f * work) * (0.6f + 0.4f * Mth.sin(phase * 1.4f - k * 1.3f));
            int col = scaledRgb(SPIRAL_COLORS[k], b);
            pose.pushPose();
            pose.translate(CRYO_X * PIXEL, y * PIXEL, FLASK_Z * PIXEL);
            pose.mulPose(Axis.YP.rotationDegrees(phase * 30f + k * 40f));
            PoseStack.Pose p = pose.last();
            for (int i = 0; i < 8; i++) {
                float a = i * Mth.TWO_PI / 8f;
                float bx = Mth.cos(a) * 1.7f, bz = Mth.sin(a) * 1.7f, dy = i / 8f * 0.9f;
                box(vc, p, bx - 0.4f, dy - 0.3f, bz - 0.4f, bx + 0.4f, dy + 0.3f, bz + 0.4f, glowSprite, col, LightCoordsUtil.FULL_BRIGHT);
            }
            pose.popPose();
        }
    }
}
