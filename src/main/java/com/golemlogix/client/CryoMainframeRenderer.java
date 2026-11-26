package com.golemlogix.client;

import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.MainframeStructure;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.smoothstep;
import static com.golemlogix.client.render.PixelMesh.sprite;
import static com.golemlogix.client.render.PixelMesh.wall;

/**
 * The whole assembled Cryo-Mainframe: a bronze 3 x 2 x 2 cabinet in a brass frame; behind double glass doors
 * a glowing teal matrix and two shelves of calculators on which snowman crews assemble items; on top
 * a brass crest with a pressure gauge and a red valve wheel. Atlants hold the cabinet at the sides (they are real blocks).
 * While someone has the cabinet window open, the doors swing outwards.
 * <p>
 * Static cabinet geometry lives in {@link MainframeCabinetMesh}; animated atlants and copper golems live in {@link MainframeCrew}.
 * Coordinates are pixels in the owner's north-facing frame: X -16..32 (three cells across), Y 0..32, Z 0..32 (front = Z 0).
 */
public class CryoMainframeRenderer extends LegacyBer<CryoMainframeBlockEntity> {
    static final Identifier BRONZE = modBlock("sat_bronze"), BRASS = modBlock("sat_brass"), DARK = modBlock("sat_dark"),
            MATRIX = modBlock("cryo_panel_matrix"), GLASS = modBlock("sat_glass"), PANEL = modBlock("cryo_panel"),
            GAUGE = modBlock("monitor_gauge"), FLOOR = modBlock("mainframe_floor");

    static final float SHELF_0_FLOOR = 3.2f, SHELF_1_FLOOR = 16.2f;
    private static final int FRIEZE_LED_COUNT = 14;
    private static final float DOOR_MAX_ANGLE = 95f;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DOOR_HANDLE_TINT = 0xFFFFE6A0;
    private static final int DOOR_GLASS_TINT = 0xB0DDF6FF;

    private static final Vector3f SCRATCH_PROJ = new Vector3f();

    private record Meshes(TextureAtlasSprite atlasMark, BerBuffers.Baked cabinet, BerBuffers.Baked valveWheel) {
    }

    private static Meshes meshes;

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    BerBuffers.bake(MainframeCabinetMesh::bakeCabinet),
                    BerBuffers.bake(MainframeCabinetMesh::bakeValveWheel));
        }
        return meshes;
    }

    public CryoMainframeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CryoMainframeBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState s = be.getBlockState();
        if (!s.hasProperty(CryoMainframeBlock.FACING)) {
            return new AABB(pos);
        }
        Direction f = s.getValue(CryoMainframeBlock.FACING);
        AABB box = new AABB(pos);
        for (int i = 0; i < MainframeStructure.CELLS.length; i++) {
            box = box.minmax(new AABB(MainframeStructure.cellPos(pos, f, i)));
        }
        return box.inflate(1.0);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(CryoMainframeBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CryoMainframeBlock.FORMED) || !state.getValue(CryoMainframeBlock.FORMED)) {
            return;
        }
        Direction facing = state.getValue(CryoMainframeBlock.FACING);
        float time = (level.getGameTime() % 72000L) + partialTick;
        int light = LevelRenderer.getLightCoords(level, be.getBlockPos().relative(facing));
        light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), 7), LightCoordsUtil.sky(light));
        float doors = Mth.lerp(partialTick, be.doorsPrev, be.doors);
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float foreman = Mth.lerp(partialTick, be.foremanPrev, be.foreman);
        Meshes baked = meshes();
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        // static body, frame, shelves, interior furniture boxes, atlant pedestals, crest, gauge and copper pipes
        baked.cabinet().draw(buffers, solid, p, light, 0);

        // inside: glowing matrix over the whole back wall (full bright), plus exterior rear & side sight-glasses
        VertexConsumer vc = buffers.getBuffer(solid);
        wall(vc, p, -13.95f, 3.05f, 29.95f, 28f, 28.9f, MATRIX, WHITE, LightCoordsUtil.FULL_BRIGHT);
        renderExteriorGlows(vc, p, time, spin);

        // vanilla blocks inside the cabinet (barrels, potted orchid, hanging lanterns)
        MainframeCabinetMesh.furnishBlocks(pose, buffers, light);

        // inserted calculators and snowman assembler crews on both shelves
        renderCalculators(be, pose, buffers, light, spin);
        renderSnowCrews(be, level, pose, buffers, light, time, spin, foreman);

        // shelf copper golems and animated side atlants
        MainframeCrew.renderShelfGolems(be, pose, buffers, light, time, spin);
        MainframeCrew.renderEmbeddedAtlants(be, pose, buffers, light, partialTick, time, spin);

        // frieze LEDs and spinning roof valve wheel with roof copper golem
        renderFriezeLeds(buffers.getBuffer(solid), pose.last(), time, spin);
        renderValveWheel(pose, buffers, solid, baked.valveWheel(), light, time, spin);
        MainframeCrew.renderRoofGolem(be, pose, buffers, light, time, spin);

        // double glass doors hinged at the side posts, swinging outwards
        float openAngle = smoothstep(doors) * DOOR_MAX_ANGLE;
        renderDoor(pose, buffers, -14f, 8f, openAngle, light);
        renderDoor(pose, buffers, 30f, 8f, -openAngle, light);
        pose.popPose();
    }

    /** Calculators standing along the back of both shelves (up to 6 per shelf). */
    private static void renderCalculators(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float spin) {
        int calcs = be.clientCalcs();
        BlockState calc = ModBlocks.CRYO_CALCULATOR.get().defaultBlockState().setValue(GolemProperties.ACTIVE, spin > 0.5f);
        for (int k = 0; k < Math.min(calcs, CryoMainframeBlockEntity.CALC_SLOTS); k++) {
            int shelf = k / 6, slot = k % 6;
            float shelfFloor = shelf == 0 ? SHELF_0_FLOOR : SHELF_1_FLOOR;
            pose.pushPose();
            pose.translate((-12.5f + slot * 7.2f) * PIXEL, shelfFloor * PIXEL, 22.5f * PIXEL);
            pose.scale(5.5f * PIXEL, 5.5f * PIXEL, 5.5f * PIXEL);
            BerBuffers.block(buffers, calc, pose, light);
            pose.popPose();
        }
    }

    /** Snowman crews on both shelves assembling the current item projections (foreman on shelf 0 when coprocessors boost). */
    private static void renderSnowCrews(CryoMainframeBlockEntity be, Level level, PoseStack pose, MultiBufferSource buffers,
                                        int light, float time, float spin, float foreman) {
        long seed = be.getBlockPos().asLong();
        float appear = smoothstep(spin);
        for (int shelf = 0; shelf < 2; shelf++) {
            float floor = shelf == 0 ? SHELF_0_FLOOR : SHELF_1_FLOOR;
            pose.pushPose();
            pose.translate(0f, 0f, 5f * PIXEL);
            SCRATCH_PROJ.set(0.5f, floor * PIXEL + 0.5f + Mth.sin(time * 0.1f + shelf) * 0.02f, 0.45f);
            float shelfWork = (shelf == 0 || be.clientBusy() > 1 || !be.clientDisplayItem(shelf).isEmpty()) ? spin : 0f;
            SnowCrew.render(pose, buffers, light, time, seed + shelf * 7919L, floor * PIXEL, shelfWork,
                    shelf == 0 ? foreman : 0f, SCRATCH_PROJ);
            ItemStack current = be.clientDisplayItem(shelf);
            if (!current.isEmpty() && appear > 0.005f) {
                float size = (current.getItem() instanceof BlockItem ? 0.3f : 0.26f) * appear;
                pose.pushPose();
                pose.translate(SCRATCH_PROJ.x, SCRATCH_PROJ.y, SCRATCH_PROJ.z);
                pose.mulPose(Axis.YP.rotationDegrees(time * 3f + shelf * 90f));
                pose.scale(size, size, size);
                BerBuffers.item(buffers, current, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT, pose, level, 0);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    /** Glowing exterior rear matrix inspection window and side cryo-sight glasses (full bright). */
    private static void renderExteriorGlows(VertexConsumer vc, PoseStack.Pose p, float time, float spin) {
        TextureAtlasSprite matrixSprite = sprite(MATRIX);
        TextureAtlasSprite panelSprite = sprite(PANEL);
        // Rear center inspection window (+Z face at z = 32.85f)
        com.golemlogix.client.render.PixelMesh.quad(vc, p,
                3.2f, 18.6f, 32.85f, 12.8f, 18.6f, 32.85f, 12.8f, 27.0f, 32.85f, 3.2f, 27.0f, 32.85f,
                matrixSprite.getU0(), matrixSprite.getV0(), matrixSprite.getU1(), matrixSprite.getV1(),
                WHITE, LightCoordsUtil.FULL_BRIGHT, 0, 0, 1);
        float pulse = spin > 0.01f ? 0.75f + 0.25f * Mth.sin(time * 0.25f) : 0.45f + 0.12f * Mth.sin(time * 0.06f);
        int cyan = 0xFF000000 | ((int) (0x38 * pulse) << 16) | ((int) (0xF2 * pulse) << 8) | (int) (0xEC * pulse);
        // Left & right side cryo-load sight-glass columns inside the brass bezels
        box(vc, p, -16.92f, 18.8f, 27.3f, -16.32f, 26.2f, 28.7f, panelSprite, cyan, LightCoordsUtil.FULL_BRIGHT);
        box(vc, p, 32.32f, 18.8f, 27.3f, 32.92f, 26.2f, 28.7f, panelSprite, cyan, LightCoordsUtil.FULL_BRIGHT);
    }

    /** Frieze above the doors: teal indicator lights chasing while active, softly breathing while idle. */
    private static void renderFriezeLeds(VertexConsumer vc, PoseStack.Pose p, float time, float spin) {
        TextureAtlasSprite panelSprite = sprite(PANEL);
        for (int k = 0; k < FRIEZE_LED_COUNT; k++) {
            float lx = -12.6f + k * 3f;
            float glow = spin > 0.01f
                    ? (((int) (time / 3) - k) % FRIEZE_LED_COUNT + FRIEZE_LED_COUNT) % FRIEZE_LED_COUNT < 4 ? 1f : 0.35f
                    : 0.45f + 0.15f * Mth.sin(time * 0.05f + k);
            int c = 0xFF000000 | ((int) (0x30 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
            box(vc, p, lx, 32.5f, -0.9f, lx + 1.4f, 33.6f, -0.5f, panelSprite, c, LightCoordsUtil.FULL_BRIGHT);
        }
    }

    /** Red valve wheel on the brass crest (turned by the roof copper golem). */
    private static void renderValveWheel(PoseStack pose, MultiBufferSource buffers, RenderType solid, BerBuffers.Baked valveWheel,
                                         int light, float time, float spin) {
        pose.pushPose();
        pose.translate(24.5f * PIXEL, 36f * PIXEL, -2.6f * PIXEL);
        pose.mulPose(Axis.ZP.rotationDegrees(time * (spin > 0.01f ? 2.5f : 0.3f)));
        valveWheel.draw(buffers, solid, pose.last(), light, 0);
        pose.popPose();
    }

    /** Door leaf hinged at {@code x = hinge} with free edge at {@code x = edge}, rotated by {@code angle} degrees. */
    private static void renderDoor(PoseStack pose, MultiBufferSource buffers, float hinge, float edge, float angle, int light) {
        float w = edge - hinge;
        pose.pushPose();
        pose.translate(hinge * PIXEL, 0f, PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        float x0 = Math.min(0, w), x1 = Math.max(0, w);
        // door leaf brass frame and horizontal muntin
        box(vc, p, x0, 3, -0.6f, x1, 4.4f, 0.6f, BRASS, WHITE, light);
        box(vc, p, x0, 26.6f, -0.6f, x1, 28, 0.6f, BRASS, WHITE, light);
        box(vc, p, x0, 4.4f, -0.6f, x0 + 1.4f, 26.6f, 0.6f, BRASS, WHITE, light);
        box(vc, p, x1 - 1.4f, 4.4f, -0.6f, x1, 26.6f, 0.6f, BRASS, WHITE, light);
        box(vc, p, x0 + 1.4f, 15f, -0.4f, x1 - 1.4f, 16f, 0.4f, BRASS, WHITE, light);
        // handle at the free edge
        float hx = w > 0 ? x1 - 3.2f : x0 + 2.2f;
        box(vc, p, hx, 13f, -1.6f, hx + 1f, 18f, -0.6f, BRASS, DOOR_HANDLE_TINT, light);
        // translucent glass panes
        VertexConsumer glass = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose gp = pose.last();
        wall(glass, gp, x0 + 1.4f, 4.4f, x1 - 1.4f, 15f, 0f, GLASS, DOOR_GLASS_TINT, light);
        wall(glass, gp, x0 + 1.4f, 16f, x1 - 1.4f, 26.6f, 0f, GLASS, DOOR_GLASS_TINT, light);
        pose.popPose();
    }
}
