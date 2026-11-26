package com.golemlogix.client;

import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.blocks.TelegraphBlock;
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

import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.limb;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.rgb;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * The whole Cryo-Source Telegraph, drawn from its main cell: a rusty copper base with frosted edges, gauges, and rune panel;
 * a rear mast housing a spinning crystal in a brass cage with copper coils, vacuum tubes, lattice tower, and iced dish; and a
 * copper golem operator keying Morse ("GOLEM") on a brass desk key, flashing the banker's lamp and sending light pulses up the mast.
 * <p>
 * Pixels in the main cell's north-facing frame: front is Z 0, viewer's right is -X; base spans X -16..16, Z 0..32,
 * mast stands on X -16..0, Z 16..32.
 */
public class TelegraphRenderer extends LegacyBer<TelegraphBlockEntity> {
    private static final Identifier RUST = mcBlock("exposed_copper"), VERDIGRIS = mcBlock("weathered_copper"),
            CUT = mcBlock("exposed_cut_copper"), COPPER = mcBlock("copper_block"), BRASS = modBlock("sat_brass"), DARK = modBlock("sat_dark"),
            ICE = modBlock("sat_ice"), GAUGE = modBlock("monitor_gauge"), DIAL = modBlock("resonator_dial"), RUNES = modBlock("cryo_panel_matrix"),
            GLASS = modBlock("sat_glass"), CRYSTAL = modBlock("core_crystal"), GEM = modBlock("sat_crystal"), WHITE = mcBlock("white_concrete"),
            PANEL = modBlock("telegraph_panel"), BAND = modBlock("telegraph_band"), DARK_OAK = mcBlock("dark_oak_planks");

    /** Rusty tint over vanilla exposed copper (untinted it reads too pink). */
    private static final int RUST_TINT = 0xFFB07A62;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Ticks a Morse light pulse takes to travel from the base to the dish. */
    private static final float PULSE_TICKS = 16f;
    private static final float MAST_X = -8f, MAST_Z = 24f, MAST_BOTTOM = 16f, MAST_TOP = 41f;
    /** Brass key knob position directly beneath the operator golem's right hand. */
    private static final float KEY_X = 12.9f, KEY_Z = 9.5f;

    private static final float[][] BASE_CORNERS = {{-16.5f, -0.5f}, {14.5f, -0.5f}, {-16.5f, 30.5f}, {14.5f, 30.5f}};
    private static final float[][] BASE_ICICLES = {{-13f, 2.4f}, {-9.5f, 1.4f}, {-4f, 3.2f}, {1.5f, 1.8f}, {6f, 2.8f}, {12f, 1.2f}};
    private static final float[][][] SIDE_PANELS = {
            {{90f, -16.06f}, {-21.4f, -14.6f}},
            {{-90f, -16.06f}, {16.6f, 22.8f}, {23.6f, 29.8f}},
            {{180f, -32.06f}, {-14.4f, -8.2f}, {-6.6f, -0.4f}, {0.4f, 6.6f}, {8.2f, 14.4f}}
    };
    private static final float[][] BAND_WALLS = {
            {0f, -0.06f, -16f, 16f}, {90f, -16.06f, -32f, 0f}, {-90f, -16.06f, 0f, 32f}, {180f, -32.06f, -16f, 16f}
    };
    private static final float[][] MAST_POSTS = {{-6.5f, -6.5f}, {4.5f, -6.5f}, {-6.5f, 4.5f}, {4.5f, 4.5f}};
    private static final float[][] LATTICE_CORNERS = {{-5f, -5f}, {5f, -5f}, {-5f, 5f}, {5f, 5f}};

    private static final Vector3f OPERATOR_POS = new Vector3f(9.5f * PIXEL, 15f * PIXEL, 11.5f * PIXEL);
    private static final Vector3f FWD_NORTH = new Vector3f(0f, 0f, -1f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);

    private record Meshes(TextureAtlasSprite atlasMark, BerBuffers.Baked body, BerBuffers.Baked dish) {
    }

    private static Meshes meshes;

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    BerBuffers.bake(TelegraphRenderer::bakeBody),
                    BerBuffers.bake(TelegraphRenderer::bakeDish));
        }
        return meshes;
    }

    public TelegraphRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(TelegraphBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState s = be.getBlockState();
        if (!s.hasProperty(TelegraphBlock.FACING)) {
            return new AABB(pos);
        }
        Direction f = s.getValue(TelegraphBlock.FACING);
        AABB box = new AABB(pos);
        for (TelegraphBlock.Part p : TelegraphBlock.Part.values()) {
            box = box.minmax(new AABB(TelegraphBlock.partPos(pos, f, p)));
        }
        return box.inflate(0.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(TelegraphBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(TelegraphBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(TelegraphBlock.FACING);
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightCoords(level, pos.above());
        light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), 5), LightCoordsUtil.sky(light));
        float wake = Mth.lerp(partialTick, be.wakePrev, be.wake);
        // keep exact long tick for Morse timing so old worlds (>16M ticks) never drop odd ticks or partialTick fractions
        long baseTick = level.getGameTime() + Math.floorMod(pos.asLong(), 97L);
        float time = (baseTick % 72000L) + partialTick;
        boolean down = wake > 0.95f && TelegraphBlockEntity.keyDown(baseTick);
        float flash = TelegraphBlockEntity.crystalFlash(Math.floorMod(baseTick, TelegraphBlockEntity.CYCLE) + partialTick) * wake;
        Meshes baked = meshes();
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        baked.body().draw(buffers, solid, p, light, 0);
        renderDish(pose, buffers, solid, baked.dish(), light, wake, flash, time);

        VertexConsumer vc = buffers.getBuffer(solid);
        renderDynamicParts(vc, pose, p, light, wake, flash, down, baseTick, partialTick, time);
        renderOperator(pose, buffers, light, wake, down, time);
        pose.popPose();
    }

    private static void renderDish(PoseStack pose, MultiBufferSource buffers, RenderType solid, BerBuffers.Baked dish,
                                   int light, float wake, float flash, float time) {
        pose.pushPose();
        pose.translate(MAST_X * PIXEL, (MAST_TOP + 0.5f) * PIXEL, MAST_Z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(25f * Mth.sin(time * 0.012f) * wake));
        pose.mulPose(Axis.XP.rotationDegrees(-30f));
        dish.draw(buffers, solid, pose.last(), light, 0);
        VertexConsumer vc = buffers.getBuffer(solid);
        int tip = rgb(0.4f + 0.6f * flash, 0.85f, 1f);
        box(vc, pose.last(), -0.7f, 6.3f, -0.7f, 0.7f, 7.7f, 0.7f, WHITE, tip, wake > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);
        pose.popPose();
    }

    private static void renderDynamicParts(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, int light, float wake, float flash,
                                           boolean down, long baseTick, float partialTick, float time) {
        TextureAtlasSprite whiteSprite = sprite(WHITE);
        renderRuneAndFilaments(vc, p, whiteSprite, light, wake, time);
        renderCrystals(vc, pose, p, wake, flash, time);
        renderDeskLampAndKey(vc, pose, p, whiteSprite, light, wake, down);
        if (wake >= 0.95f) {
            renderMorsePulses(vc, p, whiteSprite, baseTick, partialTick);
        }
    }

    private static void renderRuneAndFilaments(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite whiteSprite,
                                               int light, float wake, float time) {
        // glowing rune panel on front right of base
        float glow = 0.35f + 0.65f * wake * (0.75f + 0.25f * Mth.sin(time * 0.15f));
        int rune = 0xFF000000 | ((int) (0x40 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
        fitWall(vc, p, -14f, 2.5f, -2f, 8.5f, -0.06f, RUNES, rune, wake > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);

        // vacuum tube filaments flanking the crystal cage
        int fil = wake > 0.05f ? 0xFFFFB050 : 0xFF6A4020;
        int filLight = wake > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light;
        box(vc, p, MAST_X - 8.8f + 0.8f, 19f, MAST_Z - 0.4f, MAST_X - 8.8f + 1.6f, 26f, MAST_Z + 0.4f, whiteSprite, fil, filLight);
        box(vc, p, MAST_X + 6.4f + 0.8f, 19f, MAST_Z - 0.4f, MAST_X + 6.4f + 1.6f, 26f, MAST_Z + 0.4f, whiteSprite, fil, filLight);
    }

    private static void renderCrystals(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, float wake, float flash, float time) {
        // spinning violet main crystal inside the cage
        float b = Math.min(1f, 0.62f + 0.23f * wake + 0.4f * flash);
        int crystal = rgb(b, Math.min(1f, b * 0.9f + 0.25f * flash), b);
        TextureAtlasSprite gemSprite = sprite(GEM);
        pose.pushPose();
        pose.translate(MAST_X * PIXEL, 24f * PIXEL, MAST_Z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(45f + time * (0.4f + 2f * flash) * wake));
        PoseStack.Pose gp = pose.last();
        box(vc, gp, -2.0f, -2.5f, -2.0f, 2.0f, 2.5f, 2.0f, gemSprite, crystal, LightCoordsUtil.FULL_BRIGHT);
        box(vc, gp, -1.4f, -4.5f, -1.4f, 1.4f, 4.5f, 1.4f, gemSprite, crystal, LightCoordsUtil.FULL_BRIGHT);
        box(vc, gp, -0.7f, -6.3f, -0.7f, 0.7f, 6.3f, 0.7f, gemSprite, crystal, LightCoordsUtil.FULL_BRIGHT);
        pose.popPose();

        // two smaller crystals in the upper lattice tower
        int small = rgb(Math.min(1f, 0.7f * b + 0.3f), Math.min(1f, 0.85f * b + 0.15f), 1f);
        TextureAtlasSprite crystalSprite = sprite(CRYSTAL);
        box(vc, p, MAST_X - 3f, 34.5f, MAST_Z - 1f, MAST_X - 1f, 39f, MAST_Z + 1f, crystalSprite, small, LightCoordsUtil.FULL_BRIGHT);
        box(vc, p, MAST_X + 1f, 35.5f, MAST_Z - 1f, MAST_X + 3f, 39.5f, MAST_Z + 1f, crystalSprite, small, LightCoordsUtil.FULL_BRIGHT);
    }

    private static void renderDeskLampAndKey(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, TextureAtlasSprite whiteSprite,
                                             int light, float wake, boolean down) {
        int bulb = down ? 0xFFFFE08A : (wake > 0.05f ? 0xFF8A7440 : 0xFF4A4030);
        box(vc, p, -6.0f, 17.6f, 2.3f, -3.0f, 19.2f, 4.7f, whiteSprite, bulb, down ? LightCoordsUtil.FULL_BRIGHT : light);
        if (down) {
            box(vc, p, -7.0f, 15.05f, 1.3f, -2.0f, 15.15f, 5.7f, whiteSprite, 0xFF8A7A4A, LightCoordsUtil.FULL_BRIGHT);
        }
        pose.pushPose();
        pose.translate(KEY_X * PIXEL, 16.2f * PIXEL, 4.6f * PIXEL);
        pose.mulPose(Axis.XP.rotationDegrees(down ? 6f : 0f));
        PoseStack.Pose kp = pose.last();
        box(vc, kp, -0.35f, -0.2f, -0.8f, 0.35f, 0.3f, 5.2f, BRASS, 0xFFF0D080, light);
        box(vc, kp, -0.8f, 0.3f, KEY_Z - 4.6f - 0.7f, 0.8f, 0.9f, KEY_Z - 4.6f + 0.7f, DARK, OPAQUE_WHITE, light);
        pose.popPose();
    }

    private static void renderMorsePulses(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite whiteSprite,
                                          long baseTick, float partialTick) {
        for (int k = 0; k < PULSE_TICKS; k++) {
            long s = baseTick - k;
            if (!TelegraphBlockEntity.keyPressed(s)) {
                continue;
            }
            float t = (k + partialTick) / PULSE_TICKS;
            if (t < 0f || t > 1f) {
                continue;
            }
            float y = MAST_BOTTOM + t * (MAST_TOP - MAST_BOTTOM);
            float fade = 1f - t * 0.5f;
            int c = rgb(0.35f * fade, 0.95f * fade, 1f * fade);
            float z = y < 31f ? MAST_Z - 7.9f : MAST_Z - 3.2f;
            box(vc, p, MAST_X - 1.1f, y, z, MAST_X + 1.1f, y + 2.4f, z + 1.2f, whiteSprite, c, LightCoordsUtil.FULL_BRIGHT);
        }
    }

    private static void renderOperator(PoseStack pose, MultiBufferSource buffers, int light, float wake, boolean down, float time) {
        float bob = down ? -0.02f : 0f;
        CopperGolemPuppet.Arms arms = wake > 0.05f
                ? new CopperGolemPuppet.Arms(down ? -0.66f : -0.95f, 0.05f, -0.25f, -0.1f, 0.12f * Mth.sin(time * 0.03f))
                : new CopperGolemPuppet.Arms(0.1f, 0.12f, -0.15f, -0.08f, 0.6f * Mth.sin(time * 0.02f));
        CopperGolemPuppet.render(pose, buffers, light, OPERATOR_POS, FWD_NORTH,
                UP, 0.58f, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f + bob), 1, null, arms);
    }

    // ------------------------------------------------------------------ baked static geometry

    private static void bakeBody(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        bakeBase(vc, pose, p, light);
        bakeMast(vc, pose, p, light);
        bakeDeskAndKeyBase(vc, pose, p, light);
    }

    private static void bakeBase(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, int light) {
        box(vc, p, -16.3f, 0, -0.3f, 16.3f, 1.6f, 32.3f, DARK, OPAQUE_WHITE, light);
        box(vc, p, -16, 1.6f, 0, 16, 13, 32, RUST, RUST_TINT, light);
        box(vc, p, -15.6f, 13, 0.4f, 15.6f, 15, 31.6f, CUT, 0xFFA87460, light);
        for (float[] c : BASE_CORNERS) {
            box(vc, p, c[0], 0, c[1], c[0] + 2f, 15.4f, c[1] + 2f, BRASS, OPAQUE_WHITE, light);
        }
        box(vc, p, -16.2f, 9.5f, -0.2f, 16.2f, 10.5f, 32.2f, BRASS, 0xFFD8C090, light);
        // frosted rim and hanging icicles
        box(vc, p, -16.2f, 14.6f, -0.4f, 16.2f, 15.6f, 1.0f, ICE, OPAQUE_WHITE, light);
        box(vc, p, -16.2f, 14.6f, 31f, 16.2f, 15.6f, 32.4f, ICE, OPAQUE_WHITE, light);
        box(vc, p, -16.4f, 14.6f, -0.4f, -15f, 15.6f, 32.4f, ICE, OPAQUE_WHITE, light);
        box(vc, p, 15f, 14.6f, -0.4f, 16.4f, 15.6f, 32.4f, ICE, OPAQUE_WHITE, light);
        for (float[] i : BASE_ICICLES) {
            box(vc, p, i[0], 14.6f - i[1], -0.6f, i[0] + 0.8f, 14.6f, 0.2f, ICE, OPAQUE_WHITE, light);
            box(vc, p, i[0] + 0.2f, 14.6f - i[1] - 0.8f, -0.4f, i[0] + 0.6f, 14.6f - i[1], 0f, ICE, OPAQUE_WHITE, light);
        }
        // front gauges, buttons, rune frame, and side dials/panels/bands
        fitWall(vc, p, 2f, 5f, 7f, 10f, -0.06f, GAUGE, OPAQUE_WHITE, light);
        fitWall(vc, p, 9f, 5f, 14f, 10f, -0.06f, GAUGE, OPAQUE_WHITE, light);
        box(vc, p, 4f, 2f, -0.8f, 6f, 3.6f, 0f, DARK, OPAQUE_WHITE, light);
        box(vc, p, 10.5f, 2f, -0.8f, 12.5f, 3.6f, 0f, BRASS, 0xFFE0C070, light);
        box(vc, p, -14.6f, 2f, -0.5f, -1.4f, 2.5f, 0f, BRASS, OPAQUE_WHITE, light);
        box(vc, p, -14.6f, 8.5f, -0.5f, -1.4f, 9f, 0f, BRASS, OPAQUE_WHITE, light);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(90f));
        fitWall(vc, pose.last(), -14f, 3f, -6f, 11f, -16.06f, DIAL, OPAQUE_WHITE, light);
        fitWall(vc, pose.last(), -28f, 4f, -22f, 10f, -16.06f, GAUGE, OPAQUE_WHITE, light);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        fitWall(vc, pose.last(), 6f, 3f, 14f, 11f, -16.06f, DIAL, OPAQUE_WHITE, light);
        pose.popPose();
        for (float[][] wall : SIDE_PANELS) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(wall[0][0]));
            for (int i = 1; i < wall.length; i++) {
                fitWall(vc, pose.last(), wall[i][0], 2.6f, wall[i][1], 8.9f, wall[0][1], PANEL, OPAQUE_WHITE, light);
            }
            pose.popPose();
        }
        for (float[] w : BAND_WALLS) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(w[0]));
            for (float x = w[2]; x < w[3] - 0.5f; x += 8f) {
                fitWall(vc, pose.last(), x, 10.7f, Math.min(x + 8f, w[3]), 12.7f, w[1], BAND, OPAQUE_WHITE, light);
            }
            pose.popPose();
        }
        // copper conduit linking the key desk to the mast
        box(vc, p, KEY_X + 1f, 15f, 8.4f, KEY_X + 1.8f, 15.7f, 19.8f, COPPER, OPAQUE_WHITE, light);
        box(vc, p, MAST_X, 15f, 18.8f, KEY_X + 1f, 15.7f, 19.8f, COPPER, OPAQUE_WHITE, light);
    }

    private static void bakeMast(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, int light) {
        float cx = MAST_X, cz = MAST_Z;
        box(vc, p, cx - 7, 15f, cz - 7, cx + 7, 17f, cz + 7, DARK, OPAQUE_WHITE, light);
        for (float[] c : MAST_POSTS) {
            box(vc, p, cx + c[0], 17f, cz + c[1], cx + c[0] + 2f, 31f, cz + c[1] + 2f, BRASS, OPAQUE_WHITE, light);
        }
        for (float y = 19f; y < 30f; y += 1.6f) {
            box(vc, p, cx - 7.1f, y, cz - 7.1f, cx - 3.9f, y + 0.9f, cz - 3.9f, COPPER, OPAQUE_WHITE, light);
            box(vc, p, cx + 3.9f, y, cz - 7.1f, cx + 7.1f, y + 0.9f, cz - 3.9f, COPPER, OPAQUE_WHITE, light);
        }
        for (float side : new float[]{-1f, 1f}) {
            float x0 = cx + side * 7.6f - 1.2f;
            box(vc, p, x0, 18.5f, cz - 1.2f, x0 + 2.4f, 28f, cz + 1.2f, GLASS, 0xFFC8F0FF, light);
            box(vc, p, x0 - 0.2f, 28f, cz - 1.4f, x0 + 2.6f, 29.5f, cz + 1.4f, BRASS, OPAQUE_WHITE, light);
        }
        limb(vc, pose, cx - 5.5f, 18f, cz - 5.5f, cx + 5.5f, 30f, cz - 5.5f, 0.8f, BRASS, light);
        limb(vc, pose, cx - 5.5f, 18f, cz + 5.5f, cx + 5.5f, 30f, cz + 5.5f, 0.8f, BRASS, light);
        box(vc, p, cx - 7.2f, 31f, cz - 7.2f, cx + 7.2f, 32.5f, cz + 7.2f, BRASS, OPAQUE_WHITE, light);
        box(vc, p, cx - 7.4f, 32.2f, cz - 7.4f, cx + 7.4f, 33f, cz + 7.4f, ICE, OPAQUE_WHITE, light);
        box(vc, p, cx - 0.4f, MAST_BOTTOM, cz - 7.5f, cx + 0.4f, 31f, cz - 7.2f, DARK, 0xFF5A8898, light);

        // tapered upper lattice tower
        for (float[] c : LATTICE_CORNERS) {
            limb(vc, pose, cx + c[0], 33f, cz + c[1], cx + c[0] * 0.45f, MAST_TOP, cz + c[1] * 0.45f, 1.1f, BRASS, light);
        }
        for (float y = 35f; y < MAST_TOP; y += 3f) {
            float r = 5f - (y - 33f) / (MAST_TOP - 33f) * 2.6f;
            box(vc, p, cx - r, y, cz - r, cx + r, y + 0.6f, cz - r + 0.6f, COPPER, OPAQUE_WHITE, light);
            box(vc, p, cx - r, y, cz + r - 0.6f, cx + r, y + 0.6f, cz + r, COPPER, OPAQUE_WHITE, light);
            box(vc, p, cx - r, y, cz - r, cx - r + 0.6f, y + 0.6f, cz + r, COPPER, OPAQUE_WHITE, light);
            box(vc, p, cx + r - 0.6f, y, cz - r, cx + r, y + 0.6f, cz + r, COPPER, OPAQUE_WHITE, light);
        }
    }

    private static void bakeDeskAndKeyBase(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, int light) {
        // banker's lamp stand and green shade, ledger book, inkwell, and brass key base
        box(vc, p, -6.5f, 15f, 3f, -2.5f, 15.7f, 7f, BRASS, OPAQUE_WHITE, light);
        limb(vc, pose, -4.5f, 15.6f, 6f, -4.5f, 19.6f, 6f, 0.7f, BRASS, light);
        limb(vc, pose, -4.5f, 19.6f, 6f, -4.5f, 20.2f, 4.2f, 0.6f, BRASS, light);
        box(vc, p, -7.5f, 19.2f, 1.8f, -1.5f, 20.6f, 5.2f, VERDIGRIS, OPAQUE_WHITE, light);
        box(vc, p, -14f, 15f, 2.5f, -9f, 16.2f, 8.5f, DARK_OAK, 0xFF8A5A3A, light);
        box(vc, p, -13.6f, 16.2f, 2.9f, -9.4f, 16.4f, 8.1f, WHITE, 0xFFE8DCC0, light);
        box(vc, p, -12.5f, 15f, 10f, -10.5f, 17f, 12f, DARK, OPAQUE_WHITE, light);
        box(vc, p, KEY_X - 1.7f, 15f, 2.8f, KEY_X + 1.7f, 15.6f, 8.4f, DARK, OPAQUE_WHITE, light);
        box(vc, p, KEY_X - 1f, 15.6f, 3.9f, KEY_X + 1f, 16.4f, 5.3f, BRASS, OPAQUE_WHITE, light);
        box(vc, p, KEY_X - 0.4f, 15.6f, 7.4f, KEY_X + 0.4f, 15.7f, 8.2f, BRASS, OPAQUE_WHITE, light);
    }

    private static void bakeDish(VertexConsumer vc, PoseStack pose) {
        box(vc, pose.last(), -1.5f, -0.5f, -1.5f, 1.5f, 1f, 1.5f, DARK, OPAQUE_WHITE, 0);
        for (int k = 0; k < 8; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 45f));
            pose.mulPose(Axis.XP.rotationDegrees(-18f));
            box(vc, pose.last(), -2.4f, 0.4f, 1.2f, 2.4f, 1.2f, 7.2f, BRASS, 0xFFE6C88A, 0);
            box(vc, pose.last(), -2.6f, 0.9f, 6.6f, 2.6f, 1.6f, 7.6f, ICE, OPAQUE_WHITE, 0);
            if (k % 2 == 0) {
                box(vc, pose.last(), -0.5f, -1.8f, 6.6f, 0.5f, 0.4f, 7.4f, ICE, OPAQUE_WHITE, 0);
            }
            pose.popPose();
        }
        limb(vc, pose, 0f, 1f, 0f, 0f, 6.5f, 0f, 0.9f, BRASS, 0);
    }
}
