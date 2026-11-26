package com.golemlogix.client;

import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blocks.CryoMainframeBlock;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.client.render.Baked;
import com.golemlogix.client.render.PixelMesh;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.system.MainframeStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
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
import static com.golemlogix.client.render.PixelMesh.sprite;
import static com.golemlogix.client.render.PixelMesh.wall;

/**
 * The assembled Cryo-Mainframe: a bronze 3 x 2 x 2 cabinet in a brass frame; behind double glass doors sit a glowing teal
 * matrix and two shelves of calculators tended by snowman crews and copper golem engineers; embedded atlants flank the
 * cabinet and a brass crest with a pressure gauge and spinning red valve wheel crowns the roof.
 * <p>
 * Static cabinet geometry is baked by {@link MainframeCabinetMesh}; embedded atlants and copper golems live in
 * {@link MainframeCrew}. Coordinates are pixels in the owner's north-facing frame: X -16..32, Y 0..32, Z 0..32 (front = Z 0).
 */
public class CryoMainframeRenderer implements BlockEntityRenderer<CryoMainframeBlockEntity> {
    static final ResourceLocation BRONZE = modBlock("sat_bronze"), BRASS = modBlock("sat_brass"), DARK = modBlock("sat_dark"),
            MATRIX = modBlock("cryo_panel_matrix"), GLASS = modBlock("sat_glass"), PANEL = modBlock("cryo_panel"),
            GAUGE = modBlock("monitor_gauge"), FLOOR = modBlock("mainframe_floor");

    static final float SHELF_0_FLOOR = 3.2f, SHELF_1_FLOOR = 16.2f;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DOOR_GLASS_TINT = 0xB0DDF6FF;
    private static final int DOOR_HANDLE_TINT = 0xFFFFE6A0;
    private static final Vector3f SCRATCH_PROJ = new Vector3f();

    private record Meshes(TextureAtlasSprite atlasMark, Baked cabinet, Baked valveWheel) {
    }

    private static Meshes meshes;

    public static void clearBaked() {
        meshes = null;
    }

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    Baked.bake(MainframeCabinetMesh::bakeCabinet),
                    Baked.bake(MainframeCabinetMesh::bakeValveWheel));
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
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().relative(facing));
        light = LightTexture.pack(Math.max(LightTexture.block(light), 7), LightTexture.sky(light));
        float doors = Mth.lerp(partialTick, be.doorsPrev, be.doors);
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float foreman = Mth.lerp(partialTick, be.foremanPrev, be.foreman);
        Meshes baked = meshes();
        RenderType solid = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        // static hull, frame, shelves, interior furniture, atlant pedestals, crest, gauge and rear pipes
        baked.cabinet().draw(buffers, solid, p, light, 0);

        // full-bright teal matrix across the back wall, exterior rear & side sight-glasses, and vanilla interior props
        VertexConsumer vc = buffers.getBuffer(solid);
        wall(vc, p, -13.95f, 3.05f, 29.95f, 28f, 28.9f, MATRIX, WHITE, LightTexture.FULL_BRIGHT);
        renderExteriorGlows(vc, p, time, spin);
        MainframeCabinetMesh.furnishBlocks(pose, buffers, light);

        renderInstalledCalculators(be, pose, buffers, light, spin);
        renderShelfSnowCrews(be, level, pose, buffers, light, time, spin, foreman);

        // interior copper golems and animated embedded atlants
        MainframeCrew.renderShelfGolems(be, pose, buffers, light, time, spin);
        MainframeCrew.renderEmbeddedAtlants(be, pose, buffers, light, partialTick, time, spin);

        // animated frieze LEDs, spinning roof valve wheel, and roof valve operator
        renderFriezeLights(buffers.getBuffer(solid), pose.last(), time, spin);
        renderValveWheel(pose, buffers, solid, baked.valveWheel(), light, time, spin);
        MainframeCrew.renderRoofGolem(be, pose, buffers, light, time, spin);

        // double glass doors hinged at the outer posts, swinging outward when opened
        float open = PixelMesh.smoothstep(doors) * 95f;
        renderDoor(pose, buffers, -14f, 8f, open, light);
        renderDoor(pose, buffers, 30f, 8f, -open, light);
        pose.popPose();
    }

    /** Installed calculators arranged along the back of both interior shelves. */
    private static void renderInstalledCalculators(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                                   int light, float spin) {
        int calcs = be.clientCalcs();
        if (calcs <= 0) {
            return;
        }
        BlockState calc = ModBlocks.CRYO_CALCULATOR.get().defaultBlockState().setValue(GolemProperties.ACTIVE, spin > 0.5f);
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (int k = 0; k < Math.min(calcs, CryoMainframeBlockEntity.CALC_SLOTS); k++) {
            int shelf = k / 6, slot = k % 6;
            pose.pushPose();
            pose.translate((-12.5f + slot * 7.2f) * PIXEL, (shelf == 0 ? SHELF_0_FLOOR : SHELF_1_FLOOR) * PIXEL, 22.5f * PIXEL);
            pose.scale(5.5f * PIXEL, 5.5f * PIXEL, 5.5f * PIXEL);
            dispatcher.renderSingleBlock(calc, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    /** Snowman assembler crews and floating item projections on both interior shelves. */
    private static void renderShelfSnowCrews(CryoMainframeBlockEntity be, Level level, PoseStack pose, MultiBufferSource buffers,
                                             int light, float time, float spin, float foreman) {
        long seed = be.getBlockPos().asLong();
        float appear = PixelMesh.smoothstep(spin);
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
                Minecraft.getInstance().getItemRenderer().renderStatic(current, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
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
        PixelMesh.quad(vc, p,
                3.2f, 18.6f, 32.85f, 12.8f, 18.6f, 32.85f, 12.8f, 27.0f, 32.85f, 3.2f, 27.0f, 32.85f,
                matrixSprite.getU0(), matrixSprite.getV0(), matrixSprite.getU1(), matrixSprite.getV1(),
                WHITE, LightTexture.FULL_BRIGHT, 0, 0, 1);
        float pulse = spin > 0.01f ? 0.75f + 0.25f * Mth.sin(time * 0.25f) : 0.45f + 0.12f * Mth.sin(time * 0.06f);
        int cyan = 0xFF000000 | ((int) (0x38 * pulse) << 16) | ((int) (0xF2 * pulse) << 8) | (int) (0xEC * pulse);
        // Left & right side cryo-load sight-glass columns inside the brass bezels
        box(vc, p, -16.92f, 18.8f, 27.3f, -16.32f, 26.2f, 28.7f, panelSprite, cyan, LightTexture.FULL_BRIGHT);
        box(vc, p, 32.32f, 18.8f, 27.3f, 32.92f, 26.2f, 28.7f, panelSprite, cyan, LightTexture.FULL_BRIGHT);
    }

    /** Row of 14 teal indicator lights above the doors: chases while working, breathes gently when idle. */
    private static void renderFriezeLights(VertexConsumer vc, PoseStack.Pose p, float time, float spin) {
        TextureAtlasSprite panelSprite = sprite(PANEL);
        for (int k = 0; k < 14; k++) {
            float lx = -12.6f + k * 3f;
            float glow = spin > 0.01f
                    ? ((((int) (time / 3) - k) % 14 + 14) % 14 < 4 ? 1f : 0.35f)
                    : (0.45f + 0.15f * Mth.sin(time * 0.05f + k));
            int c = 0xFF000000 | ((int) (0x30 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
            box(vc, p, lx, 32.5f, -0.9f, lx + 1.4f, 33.6f, -0.5f, panelSprite, c, LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderValveWheel(PoseStack pose, MultiBufferSource buffers, RenderType solid, Baked valveWheel,
                                         int light, float time, float spin) {
        pose.pushPose();
        pose.translate(24.5f * PIXEL, 36f * PIXEL, -2.6f * PIXEL);
        pose.mulPose(Axis.ZP.rotationDegrees(time * (spin > 0.01f ? 2.5f : 0.3f)));
        valveWheel.draw(buffers, solid, pose.last(), light, 0);
        pose.popPose();
    }

    /** Door leaf hinged at {@code x = hinge} reaching {@code x = edge}, opened outward by {@code angle} degrees. */
    private static void renderDoor(PoseStack pose, MultiBufferSource buffers, float hinge, float edge, float angle, int light) {
        float w = edge - hinge;
        pose.pushPose();
        pose.translate(hinge * PIXEL, 0f, PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        float x0 = Math.min(0, w), x1 = Math.max(0, w);
        // brass door frame and crossbar
        box(vc, p, x0, 3, -0.6f, x1, 4.4f, 0.6f, BRASS, WHITE, light);
        box(vc, p, x0, 26.6f, -0.6f, x1, 28, 0.6f, BRASS, WHITE, light);
        box(vc, p, x0, 4.4f, -0.6f, x0 + 1.4f, 26.6f, 0.6f, BRASS, WHITE, light);
        box(vc, p, x1 - 1.4f, 4.4f, -0.6f, x1, 26.6f, 0.6f, BRASS, WHITE, light);
        box(vc, p, x0 + 1.4f, 15f, -0.4f, x1 - 1.4f, 16f, 0.4f, BRASS, WHITE, light);
        // handle at the free edge
        float hx = w > 0 ? x1 - 3.2f : x0 + 2.2f;
        box(vc, p, hx, 13f, -1.6f, hx + 1f, 18f, -0.6f, BRASS, DOOR_HANDLE_TINT, light);
        // upper and lower glass panes
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose gp = pose.last();
        wall(glass, gp, x0 + 1.4f, 4.4f, x1 - 1.4f, 15f, 0f, GLASS, DOOR_GLASS_TINT, light);
        wall(glass, gp, x0 + 1.4f, 16f, x1 - 1.4f, 26.6f, 0f, GLASS, DOOR_GLASS_TINT, light);
        pose.popPose();
    }
}
