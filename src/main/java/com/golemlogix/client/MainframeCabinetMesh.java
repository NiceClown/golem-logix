package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import static com.golemlogix.client.CryoMainframeRenderer.BRASS;
import static com.golemlogix.client.CryoMainframeRenderer.BRONZE;
import static com.golemlogix.client.CryoMainframeRenderer.DARK;
import static com.golemlogix.client.CryoMainframeRenderer.FLOOR;
import static com.golemlogix.client.CryoMainframeRenderer.GAUGE;
import static com.golemlogix.client.CryoMainframeRenderer.SHELF_0_FLOOR;
import static com.golemlogix.client.CryoMainframeRenderer.SHELF_1_FLOOR;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.mbox;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.quad;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * Static geometry baking and interior vanilla block props for {@link CryoMainframeRenderer}: cabinet hull, brass frame,
 * shelf floors, workshop furniture, atlant pedestal housings, roof crest/pipes, and the red valve wheel.
 */
final class MainframeCabinetMesh {
    private static final ResourceLocation COPPER = mcBlock("copper_block");
    private static final ResourceLocation CASING = modBlock("atlas_casing");
    private static final ResourceLocation FROST_PIPE = modBlock("resonator_pipe");
    private static final ResourceLocation ICE = modBlock("sat_ice");
    private static final ResourceLocation SPRUCE = mcBlock("spruce_planks");
    private static final ResourceLocation STRIPPED = mcBlock("stripped_spruce_log");
    private static final ResourceLocation WOOL = mcBlock("white_wool");
    private static final ResourceLocation BLANKET = mcBlock("light_blue_wool");
    private static final ResourceLocation RED = mcBlock("red_wool");
    private static final ResourceLocation BLUE = mcBlock("blue_wool");
    private static final ResourceLocation PAPER = mcBlock("white_concrete");
    private static final ResourceLocation SNOW = mcBlock("snow");
    private static final ResourceLocation BLUE_ICE = mcBlock("blue_ice");

    private static final int WHITE = 0xFFFFFFFF;
    private static final int FRAME_TINT = 0xFFE4D4BA;
    private static final int GARLAND_CORD_TINT = 0xFFB8B0A0;
    private static final int VALVE_RED = 0xFFC8432F;
    private static final int VALVE_HUB = 0xFFFFE6A0;
    private static final float VALVE_RADIUS = 3.4f;

    private static final float[] SHELF_FLOORS = {SHELF_0_FLOOR, SHELF_1_FLOOR};
    private static final float[][] SNOWBALL_OFFSETS = {{24.4f, 4.4f}, {26.3f, 4.6f}, {27.6f, 6.2f}, {25, 6.4f}, {26.6f, 5.2f}};
    private static final float[] WRENCH_Z = {8f, 12f};
    private static final float[] GARLAND_CEILINGS = {15f, 27.6f};
    private static final ResourceLocation[] GARLAND_FLAGS = {RED, BLANKET, WOOL, BLUE};

    private MainframeCabinetMesh() {
    }

    /** Bakes all static cutout cabinet geometry into a single replayable mesh (light is overridden at draw time). */
    static void bakeCabinet(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        bakeHullAndFrame(vc, p, light);
        bakeShelves(vc, p, light);
        bakeFurniture(vc, p, light);
        bakeAtlantPedestals(vc, pose, light);
        bakeCrestAndPipes(vc, p, light);
    }

    /** Bakes the 12-segment red valve wheel centred at the origin in the XY plane. */
    static void bakeValveWheel(VertexConsumer vc, PoseStack pose) {
        pose.pushPose();
        for (int k = 0; k < 12; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 30f));
            box(vc, pose.last(), -0.95f, VALVE_RADIUS - 0.5f, -0.5f, 0.95f, VALVE_RADIUS + 0.5f, 0.5f, BRASS, VALVE_RED, 0);
            pose.popPose();
        }
        for (int k = 0; k < 4; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 90f + 45f));
            box(vc, pose.last(), -0.3f, 0f, -0.3f, 0.3f, VALVE_RADIUS - 0.4f, 0.3f, BRASS, VALVE_RED, 0);
            pose.popPose();
        }
        box(vc, pose.last(), -0.9f, -0.9f, -0.8f, 0.9f, 0.9f, 0.6f, BRASS, VALVE_HUB, 0);
        pose.popPose();
    }

    /** Vanilla block props inside the cabinet: barrels, potted orchid, and four hanging lanterns. */
    static void furnishBlocks(PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState barrel = Blocks.BARREL.defaultBlockState();
        MachineParts.scaledBlock(pose, buffers, barrel, 26.5f, SHELF_0_FLOOR, 14.5f, 0.3f, 15f, light);
        MachineParts.scaledBlock(pose, buffers, barrel, 26.5f, 8f, 14.5f, 0.3f, -20f, light);
        MachineParts.scaledBlock(pose, buffers, Blocks.POTTED_BLUE_ORCHID.defaultBlockState(), 27.8f, 10.6f, 11.5f, 0.45f, 0f, light);
        MachineParts.scaledBlock(pose, buffers, barrel, 21.8f, SHELF_1_FLOOR, 4.2f, 0.28f, 40f, light);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        float lanternDrop = 0.35f * 16f;
        MachineParts.scaledBlock(pose, buffers, lantern, 0f, 15f - lanternDrop, 11f, 0.35f, 0f, LightTexture.FULL_BRIGHT);
        MachineParts.scaledBlock(pose, buffers, lantern, 19f, 15f - lanternDrop, 11f, 0.35f, 0f, LightTexture.FULL_BRIGHT);
        MachineParts.scaledBlock(pose, buffers, lantern, 0f, 28.9f - lanternDrop, 11f, 0.35f, 0f, LightTexture.FULL_BRIGHT);
        MachineParts.scaledBlock(pose, buffers, lantern, 19f, 28.9f - lanternDrop, 11f, 0.35f, 0f, LightTexture.FULL_BRIGHT);
    }

    private static void bakeHullAndFrame(VertexConsumer vc, PoseStack.Pose p, int light) {
        // body: plinth, back wall, sides, roof
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, WHITE, light);
        box(vc, p, -15, 2, 29, 31, 30, 32, BRONZE, WHITE, light);
        box(vc, p, -16, 2, 2, -14, 30, 32, BRONZE, WHITE, light);
        box(vc, p, 30, 2, 2, 32, 30, 32, BRONZE, WHITE, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, BRONZE, WHITE, light);
        // aged-brass outer front frame and rear corner ribs (slightly deeper tint than door frames for hierarchy)
        box(vc, p, -16, 2, -0.5f, -14, 30, 2, BRASS, FRAME_TINT, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 2, BRASS, FRAME_TINT, light);
        box(vc, p, -16, 28, -0.5f, 32, 30, 2, BRASS, FRAME_TINT, light);
        box(vc, p, -16, 2, -0.5f, 32, 3, 2, BRASS, FRAME_TINT, light);
        box(vc, p, -16.3f, 0, 30.7f, -15.3f, 32.3f, 32.3f, BRASS, WHITE, light);
        box(vc, p, 31.3f, 0, 30.7f, 32.3f, 32.3f, 32.3f, BRASS, WHITE, light);
    }

    private static void bakeShelves(VertexConsumer vc, PoseStack.Pose p, int light) {
        box(vc, p, -14, 15, 3, 30, 16, 29, BRASS, WHITE, light);
        box(vc, p, -14, 3, 3, 30, SHELF_0_FLOOR, 29, BRONZE, WHITE, light);
        box(vc, p, -14, 16, 3, 30, SHELF_1_FLOOR, 29, BRONZE, WHITE, light);
        shelfFloor(vc, p, 3.21f, light);
        shelfFloor(vc, p, 16.21f, light);
    }

    /** Floor picture of a shelf over its whole top (x -14..30, z 3..29 pixels): 44 x 26 units of the 48 x 32 texture. */
    private static void shelfFloor(VertexConsumer vc, PoseStack.Pose p, float y, int light) {
        TextureAtlasSprite s = sprite(FLOOR);
        quad(vc, p, -14, y, 29, 30, y, 29, 30, y, 3, -14, y, 3,
                s.getU(0f), s.getV(0f), s.getU(44f / 48f), s.getV(26f / 32f), WHITE, light, 0, 1, 0);
    }

    /** Static workshop props inside the cabinet: snowdrifts, snowball crate, bookshelf, notice board, cot, ice painting, hooks, garlands. */
    private static void bakeFurniture(VertexConsumer vc, PoseStack.Pose p, int light) {
        for (float floor : SHELF_FLOORS) {
            box(vc, p, -14, floor, 3, -10.5f, floor + 0.8f, 5.5f, SNOW, WHITE, light);
            box(vc, p, -13, floor + 0.8f, 3, -11.5f, floor + 1.4f, 4.5f, SNOW, WHITE, light);
        }
        // downstairs: snowball crate
        box(vc, p, 23.5f, SHELF_0_FLOOR, 3.5f, 29.5f, 6, 8.5f, STRIPPED, WHITE, light);
        box(vc, p, 24, 6, 4, 29, 6.3f, 8, SNOW, WHITE, light);
        for (float[] b : SNOWBALL_OFFSETS) {
            box(vc, p, b[0], 6.3f, b[1], b[0] + 1.3f, 7.5f, b[1] + 1.3f, SNOW, WHITE, light);
        }
        // right wall shelf with books
        box(vc, p, 26, 10, 9, 30, 10.6f, 20, SPRUCE, WHITE, light);
        box(vc, p, 29, 8.5f, 10, 30, 10, 10.8f, STRIPPED, WHITE, light);
        box(vc, p, 29, 8.5f, 18, 30, 10, 18.8f, STRIPPED, WHITE, light);
        box(vc, p, 27, 10.6f, 15, 29.5f, 13.4f, 15.8f, RED, WHITE, light);
        box(vc, p, 27, 10.6f, 15.8f, 29.5f, 13, 16.6f, BLUE, WHITE, light);
        box(vc, p, 27, 10.6f, 16.6f, 29.5f, 12.4f, 17.3f, BRASS, WHITE, light);
        // left wall notice board with papers and red pin
        box(vc, p, -14, 6, 7, -13.4f, 12, 15, SPRUCE, WHITE, light);
        box(vc, p, -13.4f, 6.8f, 8, -13.2f, 9.8f, 10.5f, PAPER, WHITE, light);
        box(vc, p, -13.4f, 8.2f, 11, -13.2f, 11.2f, 13.5f, PAPER, WHITE, light);
        box(vc, p, -13.4f, 7, 12, -13.1f, 7.8f, 14, PAPER, WHITE, light);
        box(vc, p, -13.2f, 9.2f, 9, -12.8f, 9.6f, 9.4f, RED, WHITE, light);
        // upstairs: cot, pillow, blanket and framed ice picture
        box(vc, p, 23.5f, SHELF_1_FLOOR, 4, 29.5f, 17.6f, 14, STRIPPED, WHITE, light);
        box(vc, p, 24, 17.6f, 4.5f, 29, 18.6f, 13.5f, WOOL, WHITE, light);
        box(vc, p, 24.5f, 18.6f, 4.8f, 28.5f, 19.4f, 6.8f, WOOL, WHITE, light);
        box(vc, p, 23.8f, 18.6f, 7.5f, 29.2f, 18.9f, 13.6f, BLANKET, WHITE, light);
        box(vc, p, 23.5f, SHELF_1_FLOOR, 13.5f, 29.5f, 20.5f, 14.2f, STRIPPED, WHITE, light);
        box(vc, p, 29.4f, 21, 5.5f, 30, 25.5f, 12, STRIPPED, WHITE, light);
        box(vc, p, 29.2f, 21.6f, 6.1f, 29.4f, 24.9f, 11.4f, BLUE_ICE, WHITE, light);
        // upstairs left wall wrench rail and two copper wrenches
        box(vc, p, -14, 24, 6, -13.4f, 24.6f, 15, BRASS, WHITE, light);
        for (float z : WRENCH_Z) {
            box(vc, p, -13.6f, 20.5f, z, -13f, 24, z + 0.7f, COPPER, WHITE, light);
            box(vc, p, -13.6f, 20, z - 0.4f, -13f, 21, z + 1.1f, COPPER, WHITE, light);
        }
        // sagging flag garlands behind the glass on both shelves (upstairs hangs lower to clear the top door frame)
        for (float ceiling : GARLAND_CEILINGS) {
            for (int k = 0; k < 13; k++) {
                float x = -12.5f + k * 3.3f;
                float t = (x + 12.5f) / 39.6f;
                float sag = 1.0f * 4f * t * (1f - t);
                float y = ceiling - 0.8f - sag;
                box(vc, p, x, y, 3.6f, x + 3.3f, y + 0.25f, 3.8f, WOOL, GARLAND_CORD_TINT, light);
                box(vc, p, x + 0.6f, y - 1.6f, 3.6f, x + 2.2f, y, 3.75f, GARLAND_FLAGS[k % GARLAND_FLAGS.length], WHITE, light);
            }
        }
    }

    /** Static pedestal housings and frosted coolant pipes under both embedded side atlants. */
    private static void bakeAtlantPedestals(VertexConsumer vc, PoseStack pose, int light) {
        for (int k = 0; k < 2; k++) {
            boolean right = k == 1;
            mbox(vc, pose, right, -25, 0, 0.5f, -16, 11, 15.5f, CASING, WHITE, light);
            mbox(vc, pose, right, -25.5f, 10.5f, 0, -15.5f, 12, 16, BRASS, WHITE, light);
            for (int zi = 0; zi < 2; zi++) {
                float z = zi == 0 ? 3.2f : 10.0f;
                mbox(vc, pose, right, -28.5f, 2.5f, z, -24.5f, 5.5f, z + 2.8f, FROST_PIPE, WHITE, light);
                mbox(vc, pose, right, -29.5f, 2.2f, z - 0.3f, -26.5f, 9.5f, z + 3.1f, FROST_PIPE, WHITE, light);
                mbox(vc, pose, right, -29.8f, 6.4f, z - 0.6f, -26.2f, 7.2f, z + 3.4f, ICE, WHITE, light);
                mbox(vc, pose, right, -28.4f, 0.6f, z + 1.0f, -27.8f, 2.2f, z + 1.6f, ICE, WHITE, light);
            }
        }
    }

    /** Frieze backing, brass roof crest, pressure gauge, and rear copper piping. */
    private static void bakeCrestAndPipes(VertexConsumer vc, PoseStack.Pose p, int light) {
        box(vc, p, -16, 32, -0.5f, 32, 34, 3, BRONZE, WHITE, light);
        box(vc, p, -16.5f, 34, -1.5f, 32.5f, 37.4f, 16, BRASS, WHITE, light);
        box(vc, p, -15.5f, 37.4f, -0.8f, 31.5f, 38.4f, 15, BRONZE, WHITE, light);
        box(vc, p, -15, 34.6f, -1.8f, 31, 36.8f, -1.5f, BRONZE, WHITE, light);
        box(vc, p, -13.2f, 34.2f, -2.4f, -7.8f, 37.8f, -1.8f, BRASS, WHITE, light);
        fitWall(vc, p, -13f, 34.4f, -8f, 37.6f, -2.45f, GAUGE, WHITE, light);
        box(vc, p, -15, 32, 20, 31, 33.6f, 21.6f, COPPER, WHITE, light);
        box(vc, p, -15, 32, 25, 31, 33.6f, 26.6f, COPPER, WHITE, light);
        box(vc, p, 27, 4, 32, 28.6f, 33.6f, 33.6f, COPPER, WHITE, light);
        box(vc, p, -12.6f, 4, 32, -11, 33.6f, 33.6f, COPPER, WHITE, light);
    }
}
