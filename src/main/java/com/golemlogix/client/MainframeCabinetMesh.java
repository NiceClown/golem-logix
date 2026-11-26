package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
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
import static com.golemlogix.client.render.PixelMesh.PIXEL;
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
    private static final Identifier COPPER = mcBlock("copper_block");
    private static final Identifier CASING = modBlock("atlas_casing");
    private static final Identifier RIM_COPPER = modBlock("atlas_rim_copper");
    private static final Identifier RIM_IRON = modBlock("atlas_rim_iron");
    private static final Identifier FROST_PIPE = modBlock("resonator_pipe");
    private static final Identifier ICE = modBlock("sat_ice");
    private static final Identifier PANEL = modBlock("cryo_panel");
    private static final Identifier SPRUCE = mcBlock("spruce_planks");
    private static final Identifier STRIPPED = mcBlock("stripped_spruce_log");
    private static final Identifier WOOL = mcBlock("white_wool");
    private static final Identifier BLANKET = mcBlock("light_blue_wool");
    private static final Identifier RED = mcBlock("red_wool");
    private static final Identifier BLUE = mcBlock("blue_wool");
    private static final Identifier PAPER = mcBlock("white_concrete");
    private static final Identifier SNOW = mcBlock("snow");
    private static final Identifier BLUE_ICE = mcBlock("blue_ice");

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
    private static final Identifier[] GARLAND_FLAGS = {RED, BLANKET, WOOL, BLUE};

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
        bakeExteriorSidesAndBack(vc, p, light);
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
        MachineParts.scaledBlock(pose, buffers, lantern, 0f, 15f - lanternDrop, 11f, 0.35f, 0f, LightCoordsUtil.FULL_BRIGHT);
        MachineParts.scaledBlock(pose, buffers, lantern, 19f, 15f - lanternDrop, 11f, 0.35f, 0f, LightCoordsUtil.FULL_BRIGHT);
        MachineParts.scaledBlock(pose, buffers, lantern, 0f, 28.9f - lanternDrop, 11f, 0.35f, 0f, LightCoordsUtil.FULL_BRIGHT);
        MachineParts.scaledBlock(pose, buffers, lantern, 19f, 28.9f - lanternDrop, 11f, 0.35f, 0f, LightCoordsUtil.FULL_BRIGHT);
    }

    private static void bakeHullAndFrame(VertexConsumer vc, PoseStack.Pose p, int light) {
        // Strictly disjoint core hull: plinth (y=0..2), left (-16..-14), right (30..32), back (-14..30), roof (y=30..32)
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, WHITE, light);
        box(vc, p, -14, 2, 29, 30, 30, 32, RIM_IRON, WHITE, light);
        box(vc, p, -16, 2, 2, -14, 30, 32, RIM_COPPER, WHITE, light);
        box(vc, p, 30, 2, 2, 32, 30, 32, RIM_COPPER, WHITE, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, RIM_COPPER, WHITE, light);
        // Aged-brass outer front frame: horizontal bars span strictly between vertical posts (-14..30) for zero Z-fighting
        box(vc, p, -16, 2, -0.5f, -14, 30, 2, BRASS, FRAME_TINT, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 2, BRASS, FRAME_TINT, light);
        box(vc, p, -14, 28, -0.5f, 30, 30, 2, BRASS, FRAME_TINT, light);
        box(vc, p, -14, 2, -0.5f, 30, 3, 2, BRASS, FRAME_TINT, light);
    }

    private static void bakeShelves(VertexConsumer vc, PoseStack.Pose p, int light) {
        box(vc, p, -13.95f, 15, 3, 29.95f, 16, 28.95f, BRASS, WHITE, light);
        box(vc, p, -13.95f, 3, 3, 29.95f, SHELF_0_FLOOR, 28.95f, BRONZE, WHITE, light);
        box(vc, p, -13.95f, 16, 3, 29.95f, SHELF_1_FLOOR, 28.95f, BRONZE, WHITE, light);
        shelfFloor(vc, p, SHELF_0_FLOOR + 0.02f, light);
        shelfFloor(vc, p, SHELF_1_FLOOR + 0.02f, light);
    }

    /** Floor picture of a shelf over its whole top (x -13.95..29.95, z 3..28.95 pixels): 44 x 26 units of the 48 x 32 texture. */
    private static void shelfFloor(VertexConsumer vc, PoseStack.Pose p, float y, int light) {
        TextureAtlasSprite s = sprite(FLOOR);
        quad(vc, p, -13.95f, y, 28.95f, 29.95f, y, 28.95f, 29.95f, y, 3, -13.95f, y, 3,
                s.getU(0f), s.getV(0f), s.getU(44f / 48f), s.getV(26f / 32f), WHITE, light, 0, 1, 0);
    }

    /** Static workshop props inside the cabinet: snowdrifts, snowball crate, bookshelf, notice board, cot, ice painting, hooks, garlands. */
    private static void bakeFurniture(VertexConsumer vc, PoseStack.Pose p, int light) {
        for (float base : SHELF_FLOORS) {
            float floor = base + 0.03f;
            box(vc, p, -13.9f, floor, 3.05f, -10.5f, floor + 0.8f, 5.5f, SNOW, WHITE, light);
            box(vc, p, -13, floor + 0.8f, 3.05f, -11.5f, floor + 1.4f, 4.5f, SNOW, WHITE, light);
        }
        // downstairs: snowball crate
        box(vc, p, 23.5f, SHELF_0_FLOOR + 0.03f, 3.5f, 29.5f, 6, 8.5f, STRIPPED, WHITE, light);
        box(vc, p, 24, 6, 4, 29, 6.3f, 8, SNOW, WHITE, light);
        for (float[] b : SNOWBALL_OFFSETS) {
            box(vc, p, b[0], 6.3f, b[1], b[0] + 1.3f, 7.5f, b[1] + 1.3f, SNOW, WHITE, light);
        }
        // right wall shelf with books
        box(vc, p, 26, 10, 9, 29.95f, 10.6f, 20, SPRUCE, WHITE, light);
        box(vc, p, 29, 8.5f, 10, 29.95f, 10, 10.8f, STRIPPED, WHITE, light);
        box(vc, p, 29, 8.5f, 18, 29.95f, 10, 18.8f, STRIPPED, WHITE, light);
        box(vc, p, 27, 10.6f, 15, 29.5f, 13.4f, 15.8f, RED, WHITE, light);
        box(vc, p, 27, 10.6f, 15.8f, 29.5f, 13, 16.6f, BLUE, WHITE, light);
        box(vc, p, 27, 10.6f, 16.6f, 29.5f, 12.4f, 17.3f, BRASS, WHITE, light);
        // left wall notice board with papers and red pin
        box(vc, p, -13.95f, 6, 7, -13.4f, 12, 15, SPRUCE, WHITE, light);
        box(vc, p, -13.4f, 6.8f, 8, -13.2f, 9.8f, 10.5f, PAPER, WHITE, light);
        box(vc, p, -13.4f, 8.2f, 11, -13.2f, 11.2f, 13.5f, PAPER, WHITE, light);
        box(vc, p, -13.4f, 7, 12, -13.1f, 7.8f, 14, PAPER, WHITE, light);
        box(vc, p, -13.2f, 9.2f, 9, -12.8f, 9.6f, 9.4f, RED, WHITE, light);
        // upstairs: cot, pillow, blanket and framed ice picture
        box(vc, p, 23.5f, SHELF_1_FLOOR + 0.03f, 4, 29.5f, 17.6f, 13.5f, STRIPPED, WHITE, light);
        box(vc, p, 24, 17.6f, 4.5f, 29, 18.6f, 13.4f, WOOL, WHITE, light);
        box(vc, p, 24.5f, 18.6f, 4.8f, 28.5f, 19.4f, 6.8f, WOOL, WHITE, light);
        box(vc, p, 23.8f, 18.91f, 7.5f, 29.2f, 19.2f, 13.45f, BLANKET, WHITE, light);
        box(vc, p, 23.5f, SHELF_1_FLOOR + 0.03f, 13.5f, 29.5f, 20.5f, 14.2f, STRIPPED, WHITE, light);
        box(vc, p, 29.4f, 21, 5.5f, 29.95f, 25.5f, 12, STRIPPED, WHITE, light);
        box(vc, p, 29.2f, 21.6f, 6.1f, 29.4f, 24.9f, 11.4f, BLUE_ICE, WHITE, light);
        // upstairs left wall wrench rail and two copper wrenches
        box(vc, p, -13.95f, 24, 6, -13.4f, 24.6f, 15, BRASS, WHITE, light);
        for (float z : WRENCH_Z) {
            box(vc, p, -13.38f, 21f, z, -12.95f, 23.98f, z + 0.7f, COPPER, WHITE, light);
            box(vc, p, -13.38f, 20f, z - 0.4f, -12.95f, 21f, z + 1.1f, COPPER, WHITE, light);
        }
        // sagging flag garlands behind the glass on both shelves (upstairs hangs lower to clear the top door frame)
        for (float ceiling : GARLAND_CEILINGS) {
            for (int k = 0; k < 13; k++) {
                float x = -12.5f + k * 3.3f;
                float t = (x + 12.5f) / 39.6f;
                float sag = 1.0f * 4f * t * (1f - t);
                float y = ceiling - 0.8f - sag;
                box(vc, p, x, y, 3.6f, x + 3.3f, y + 0.25f, 3.8f, WOOL, GARLAND_CORD_TINT, light);
                box(vc, p, x + 0.6f, y - 1.6f, 3.62f, x + 2.2f, y, 3.75f, GARLAND_FLAGS[k % GARLAND_FLAGS.length], WHITE, light);
            }
        }
    }

    /** Static pedestal housings and frosted coolant pipes under both embedded side atlants (strictly non-overlapping with x=+-16 & y=0). */
    private static void bakeAtlantPedestals(VertexConsumer vc, PoseStack pose, int light) {
        for (int k = 0; k < 2; k++) {
            boolean right = k == 1;
            mbox(vc, pose, right, -25, 0.02f, 0.5f, -16.02f, 10.5f, 15.5f, CASING, WHITE, light);
            mbox(vc, pose, right, -25.5f, 10.5f, 0, -16.02f, 12, 16, BRASS, WHITE, light);
            for (int zi = 0; zi < 2; zi++) {
                float z = zi == 0 ? 3.2f : 10.0f;
                mbox(vc, pose, right, -26.5f, 2.5f, z, -25.02f, 5.5f, z + 2.8f, FROST_PIPE, WHITE, light);
                mbox(vc, pose, right, -29.5f, 2.2f, z - 0.3f, -26.5f, 6.4f, z + 3.1f, FROST_PIPE, WHITE, light);
                mbox(vc, pose, right, -29.5f, 7.2f, z - 0.3f, -26.5f, 9.5f, z + 3.1f, FROST_PIPE, WHITE, light);
                mbox(vc, pose, right, -29.8f, 6.4f, z - 0.6f, -26.2f, 7.2f, z + 3.4f, ICE, WHITE, light);
                mbox(vc, pose, right, -28.4f, 0.6f, z + 1.0f, -27.8f, 2.2f, z + 1.6f, ICE, WHITE, light);
            }
        }
    }

    /**
     * Sculpted 3D side and rear exterior architecture for the Cryo-Mainframe:
     * 3-bay rear Cryo-Radiator & Arcane Backplane (dual frosted fin heat-exchangers + central diagnostic spine)
     * and framed side coolant manifold bays behind the Atlants — every sub-box strictly disjoint so zero Z-fighting occurs.
     */
    private static void bakeExteriorSidesAndBack(VertexConsumer vc, PoseStack.Pose p, int light) {
        // --- 1. Exposed left & right rear side bays (Z = 16.4..31.4, behind the Atlants)
        for (int s = 0; s < 2; s++) {
            boolean right = s == 1;
            float xIn = right ? 32.02f : -16.65f;
            float xOut = right ? 32.65f : -16.02f;
            float xDeep0 = right ? 32.02f : -17.15f;
            float xDeep1 = right ? 33.15f : -16.02f;

            // Vertical aged-brass side pilasters & horizontal mid-deck belt
            box(vc, p, xDeep0, 2.02f, 16.3f, xDeep1, 29.98f, 17.6f, BRASS, FRAME_TINT, light);
            box(vc, p, xDeep0, 2.02f, 30.2f, xDeep1, 29.98f, 31.6f, BRASS, FRAME_TINT, light);
            box(vc, p, xIn, 28.6f, 17.6f, xOut, 29.98f, 30.2f, BRASS, FRAME_TINT, light);
            box(vc, p, xIn, 15.0f, 17.6f, xOut, 16.4f, 30.2f, BRASS, FRAME_TINT, light);
            box(vc, p, xIn, 2.02f, 17.6f, xOut, 3.4f, 30.2f, BRASS, FRAME_TINT, light);

            // Recessed dark-slate side armor backing inside upper & lower bays
            float back0 = right ? 32.02f : -16.32f;
            float back1 = right ? 32.32f : -16.02f;
            box(vc, p, back0, 3.4f, 17.6f, back1, 15.0f, 30.2f, DARK, WHITE, light);
            box(vc, p, back0, 16.4f, 17.6f, back1, 28.6f, 30.2f, DARK, WHITE, light);

            // Upper side bay: 3 horizontal frosted cryo-fin louvers + brass sight-glass bezel
            float fin0 = right ? 32.32f : -16.85f;
            float fin1 = right ? 32.85f : -16.32f;
            for (float fy : new float[]{18.4f, 21.6f, 24.8f}) {
                box(vc, p, fin0, fy, 18.6f, fin1, fy + 1.3f, 25.8f, ICE, WHITE, light);
            }
            box(vc, p, fin0, 18.0f, 26.8f, fin1, 27.0f, 29.2f, BRASS, WHITE, light);

            // Lower side bay: bolted dark-iron cryo-collector chest with brass straps & frost pipe feeding toward Atlant
            float man0 = right ? 32.32f : -17.25f;
            float man1 = right ? 33.25f : -16.32f;
            box(vc, p, man0, 5.2f, 19.6f, man1, 12.8f, 28.4f, DARK, WHITE, light);
            float strap0 = right ? 33.25f : -17.45f;
            float strap1 = right ? 33.45f : -17.25f;
            box(vc, p, strap0, 5.6f, 20.4f, strap1, 12.4f, 21.6f, BRASS, FRAME_TINT, light);
            box(vc, p, strap0, 5.6f, 26.4f, strap1, 12.4f, 27.6f, BRASS, FRAME_TINT, light);
            box(vc, p, fin0, 7.6f, 17.6f, fin1, 10.4f, 19.6f, FROST_PIPE, WHITE, light);
        }

        // --- 2. Rear 3-Bay Cryo-Radiator & Arcane Backplane (Z = 32.02..34.2, X = -16..32)
        // Four vertical aged-brass structural pilasters dividing the back into 3 bays
        float[][] rearPosts = {{-16.35f, -14.5f}, {-0.9f, 0.7f}, {15.3f, 16.9f}, {30.5f, 32.35f}};
        for (float[] rp : rearPosts) {
            box(vc, p, rp[0], 0.02f, 32.02f, rp[1], 32.3f, 33.25f, BRASS, FRAME_TINT, light);
        }
        // Disjoint horizontal cornice, mid-deck belt, and base rail across each of the 3 bays
        float[][] bays = {{-14.5f, -0.9f}, {0.7f, 15.3f}, {16.9f, 30.5f}};
        for (float[] b : bays) {
            box(vc, p, b[0], 29.2f, 32.02f, b[1], 31.2f, 32.95f, BRASS, FRAME_TINT, light);
            box(vc, p, b[0], 14.9f, 32.02f, b[1], 16.5f, 32.95f, BRASS, FRAME_TINT, light);
            box(vc, p, b[0], 2.02f, 32.02f, b[1], 3.4f, 32.95f, BRASS, FRAME_TINT, light);
            // Dark recessed armor wells inside upper & lower bay openings
            box(vc, p, b[0], 3.4f, 32.02f, b[1], 14.9f, 32.35f, DARK, WHITE, light);
            box(vc, p, b[0], 16.5f, 32.02f, b[1], 29.2f, 32.35f, DARK, WHITE, light);
        }

        // Left & Right Rear Bays: Dual-tier frosted Cryo-Fin Heat-Sink Louvers + vertical frost collector headers
        for (int side = 0; side < 2; side++) {
            float bx0 = side == 0 ? -14.5f : 16.9f;
            float bx1 = side == 0 ? -0.9f : 30.5f;
            float hdr0 = side == 0 ? -13.7f : 27.5f;
            float hdr1 = side == 0 ? -11.5f : 29.7f;
            float finX0 = side == 0 ? -11.1f : 17.7f;
            float finX1 = side == 0 ? -1.7f : 27.1f;

            // Vertical frosted manifold header rising from plinth up onto roof (y=3.4..33.8) + brass clamps
            box(vc, p, hdr0, 3.4f, 32.35f, hdr1, 33.8f, 34.15f, FROST_PIPE, WHITE, light);
            for (float cy : new float[]{8.2f, 18.6f, 26.8f}) {
                box(vc, p, hdr0 - 0.25f, cy, 32.35f, hdr1 + 0.25f, cy + 1.1f, 34.4f, BRASS, WHITE, light);
            }
            // Lower & upper stepped cryo-fins
            for (float fy : new float[]{4.6f, 7.2f, 9.8f, 12.4f, 17.8f, 20.4f, 23.0f, 25.6f}) {
                box(vc, p, finX0, fy, 32.35f, finX1, fy + 1.25f, 33.25f, ICE, WHITE, light);
            }
            // Copper return strap at outer edge
            float cx0 = side == 0 ? bx0 + 0.2f : bx1 - 0.6f;
            box(vc, p, cx0, 3.4f, 32.35f, cx0 + 0.4f, 29.2f, 32.7f, COPPER, WHITE, light);
        }

        // Center Rear Bay (X = 0.7..15.3): Arcane Data-Bus Backplane & Diagnostic Hatch
        // Lower junction chest with pressure dial & twin copper busbars
        box(vc, p, 2.1f, 4.2f, 32.35f, 13.9f, 13.8f, 33.45f, RIM_COPPER, WHITE, light);
        box(vc, p, 5.3f, 6.3f, 33.45f, 10.7f, 11.7f, 33.85f, BRASS, WHITE, light);
        quad(vc, p, 5.6f, 6.6f, 33.88f, 10.4f, 6.6f, 33.88f, 10.4f, 11.4f, 33.88f, 5.6f, 11.4f, 33.88f,
                sprite(GAUGE).getU0(), sprite(GAUGE).getV0(), sprite(GAUGE).getU1(), sprite(GAUGE).getV1(), WHITE, light, 0, 0, 1);
        box(vc, p, 3.2f, 13.8f, 32.45f, 4.4f, 17.4f, 33.25f, COPPER, WHITE, light);
        box(vc, p, 11.6f, 13.8f, 32.45f, 12.8f, 17.4f, 33.25f, COPPER, WHITE, light);

        // Upper inspection bezel framing the glowing Matrix core window (drawn full-bright in CryoMainframeRenderer)
        box(vc, p, 2.0f, 17.4f, 32.35f, 14.0f, 18.6f, 33.35f, BRASS, WHITE, light);
        box(vc, p, 2.0f, 27.0f, 32.35f, 14.0f, 28.2f, 33.35f, BRASS, WHITE, light);
        box(vc, p, 2.0f, 18.6f, 32.35f, 3.2f, 27.0f, 33.35f, BRASS, WHITE, light);
        box(vc, p, 12.8f, 18.6f, 32.35f, 14.0f, 27.0f, 33.35f, BRASS, WHITE, light);
        // Protective horizontal brass bars across the rear matrix window
        box(vc, p, 3.2f, 21.1f, 33.05f, 12.8f, 21.7f, 33.4f, BRASS, FRAME_TINT, light);
        box(vc, p, 3.2f, 23.9f, 33.05f, 12.8f, 24.5f, 33.4f, BRASS, FRAME_TINT, light);
    }

    /** Frieze backing, brass roof crest, pressure gauge, and roof coolant manifolds (all strictly above y=32.02). */
    private static void bakeCrestAndPipes(VertexConsumer vc, PoseStack.Pose p, int light) {
        box(vc, p, -15.95f, 32.02f, -0.5f, 31.95f, 34, 3, BRONZE, WHITE, light);
        box(vc, p, -16.5f, 34, -1.5f, 32.5f, 37.4f, 16, BRASS, WHITE, light);
        box(vc, p, -15.5f, 37.4f, -0.8f, 31.5f, 38.4f, 15, BRONZE, WHITE, light);
        box(vc, p, -15, 34.6f, -1.8f, 31, 36.8f, -1.51f, BRONZE, WHITE, light);
        box(vc, p, -13.2f, 34.2f, -2.4f, -7.8f, 37.8f, -1.81f, BRASS, WHITE, light);
        fitWall(vc, p, -13f, 34.4f, -8f, 37.6f, -2.45f, GAUGE, WHITE, light);
        // Twin roof manifold pipes elevated on brass saddles (zero coplanar contact with roof y=32)
        for (float pz : new float[]{20.0f, 25.2f}) {
            box(vc, p, -13.7f, 32.35f, pz, 29.7f, 33.9f, pz + 1.8f, FROST_PIPE, WHITE, light);
            for (float sx : new float[]{-8.0f, 7.2f, 22.4f}) {
                box(vc, p, sx, 32.02f, pz - 0.2f, sx + 1.6f, 32.35f, pz + 2.0f, BRASS, WHITE, light);
            }
        }
        // Elbows joining rear vertical frost headers (z=32.35..34.15) forward to the roof manifolds
        box(vc, p, -13.7f, 32.35f, 21.8f, -11.5f, 33.8f, 32.35f, FROST_PIPE, WHITE, light);
        box(vc, p, 27.5f, 32.35f, 21.8f, 29.7f, 33.8f, 32.35f, FROST_PIPE, WHITE, light);
    }
}
