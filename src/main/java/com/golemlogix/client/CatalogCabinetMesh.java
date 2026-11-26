package com.golemlogix.client;

import com.golemlogix.client.render.Baked;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.quad;
import static com.golemlogix.client.render.PixelMesh.sprite;
import static com.golemlogix.client.render.PixelMesh.wall;

/**
 * Static baked meshes for {@link CardCatalogRenderer}: cabinet hull and frame, drawer dividers, middle console housing,
 * roof letter-drum bay, pneumatic archive rear & side facades, coolant pipes, hanging ivy, pedestal rims, drums, and gear.
 */
final class CatalogCabinetMesh {
    static final ResourceLocation RUST = modBlock("resonator_body"), BRASS = modBlock("sat_brass"),
            BRONZE = modBlock("sat_bronze"), DARK = modBlock("sat_dark"), DRUM = modBlock("catalog_drum"),
            FROST_PIPE = modBlock("resonator_pipe"), ICE = modBlock("sat_ice"), CARD = modBlock("encoder_cards"),
            VINE = mcBlock("vine"), PIPE = mcBlock("copper_block");

    private static final ResourceLocation RIM_COPPER = modBlock("atlas_rim_copper");
    private static final ResourceLocation GAUGE = modBlock("monitor_gauge");
    private static final ResourceLocation SPRUCE = mcBlock("spruce_planks");

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BACK_NICHE_TINT = 0xFF2A221C;
    private static final int WALNUT_TINT = 0xFF7D583A;
    private static final int DIVIDER_TINT = 0xFFE4D2B4;
    private static final int AGED_BRASS_TINT = 0xFFE8D8BC;
    private static final int GEAR_TINT = 0xFFFFE6A0;
    private static final int IVY_GREEN = 0xFF5A9A3A;

    private static final float[][] DRUM_X = {{-11.6f, -3.6f}, {-2.2f, 6.2f}, {7.6f, 15.6f}, {17.0f, 27.6f}};
    private static final float[] ROOF_DIVIDER_X = {-13f, -3.6f, 6.2f, 15.6f, 27.6f};
    private static final float[][] ROOF_ICICLES = {{-10.5f, 2.2f}, {-3f, 1.4f}, {6.5f, 2.6f}, {17f, 1.6f}};
    private static final float[][] DRAWER_SHELVES = {
            {3.2f, 3.6f},     // under bottom row
            {8.0f, 8.52f},    // between rows 0 and 1
            {12.92f, 13.44f}, // between rows 1 and 2
            {17.84f, 18.36f}, // between rows 2 and 3
            {22.76f, 23.28f}, // between rows 3 and 4
            {27.68f, 28.6f}   // above top row
    };

    record Meshes(TextureAtlasSprite atlasMark, Baked cabinet, Baked[] drums, Baked gear) {
    }

    private static Meshes meshes;

    private CatalogCabinetMesh() {
    }

    static void clearBaked() {
        meshes = null;
    }

    static Meshes get() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            Baked[] drums = new Baked[DRUM_X.length];
            for (int d = 0; d < DRUM_X.length; d++) {
                float x0 = DRUM_X[d][0] + 0.2f, x1 = DRUM_X[d][1] - 0.2f;
                drums[d] = Baked.bake((vc, pose) -> bakeDrum(vc, pose, x0, x1));
            }
            meshes = new Meshes(mark,
                    Baked.bake(CatalogCabinetMesh::bakeCabinet),
                    drums,
                    Baked.bake(CatalogCabinetMesh::bakeGear));
        }
        return meshes;
    }

    /** Bakes all static cutout cabinet geometry into a single replayable mesh (light is overridden at draw time). */
    static void bakeCabinet(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        bakeHullAndFrame(vc, p, light);
        bakeDrawerDividers(vc, p, light);
        bakeMiddleConsole(vc, p, light);
        bakeExteriorSidesAndBack(vc, p, light);
        bakeRoofAndPiping(vc, p, light);
        bakeIvyAndPedestalTrim(vc, p, light);
    }

    /** Bakes an 8-sided rotating letter drum spanning {@code x0..x1} around the X axis. */
    static void bakeDrum(VertexConsumer vc, PoseStack pose, float x0, float x1) {
        for (int f = 0; f < 8; f++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(f * 45f));
            box(vc, pose.last(), x0, -1.2f, -2.85f, x1, 1.2f, -2.35f, DRUM, WHITE, 0);
            pose.popPose();
        }
    }

    /** Bakes the 10-tooth brass drive gear at the right end of the roof drum shaft. */
    static void bakeGear(VertexConsumer vc, PoseStack pose) {
        box(vc, pose.last(), 0f, -2.6f, -2.6f, 1.2f, 2.6f, 2.6f, BRASS, GEAR_TINT, 0);
        for (int t = 0; t < 10; t++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(t * 36f));
            box(vc, pose.last(), 0f, 2.4f, -0.6f, 1.2f, 3.6f, 0.6f, BRASS, GEAR_TINT, 0);
            pose.popPose();
        }
        box(vc, pose.last(), -0.2f, -0.8f, -0.8f, 1.6f, 0.8f, 0.8f, DARK, WHITE, 0);
    }

    private static void bakeHullAndFrame(VertexConsumer vc, PoseStack.Pose p, int light) {
        // Strictly disjoint core hull: plinth (0..2), left (-16..-14), right (30..32), back (-14..30), roof (30..32)
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, WHITE, light);
        box(vc, p, -14, 2, 29, 30, 30, 32, RIM_COPPER, WHITE, light);
        box(vc, p, -16, 2, 1.5f, -14, 30, 32, RIM_COPPER, WHITE, light);
        box(vc, p, 30, 2, 1.5f, 32, 30, 32, RIM_COPPER, WHITE, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, RIM_COPPER, WHITE, light);
        // Dark bronze outer front frame, aged-brass inner mullions, and blind backing behind the drawer bays
        box(vc, p, -16, 2, -0.5f, -14, 30, 1.5f, BRONZE, WHITE, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 1.5f, BRONZE, WHITE, light);
        box(vc, p, -14, 28.6f, -0.5f, 30, 30, 1.5f, BRONZE, WHITE, light);
        box(vc, p, -14, 2, -0.5f, 30, 3.2f, 1.5f, BRONZE, WHITE, light);
        box(vc, p, 1.9f, 3.2f, -0.3f, 3.2f, 28.6f, 1.5f, BRASS, WHITE, light);
        box(vc, p, 12.8f, 3.2f, -0.3f, 14.1f, 28.6f, 1.5f, BRASS, WHITE, light);
        box(vc, p, -13.98f, 3.2f, 0.72f, 1.88f, 28.6f, 2.0f, DARK, BACK_NICHE_TINT, light);
        box(vc, p, 14.12f, 3.2f, 0.72f, 29.98f, 28.6f, 2.0f, DARK, BACK_NICHE_TINT, light);
    }

    private static void bakeDrawerDividers(VertexConsumer vc, PoseStack.Pose p, int light) {
        // Vertical stiles for left drawer bay (X: -14.0..1.9) and right drawer bay (X: 14.1..30.0)
        box(vc, p, -14.0f, 3.2f, 0.2f, -13.6f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, -6.2f, 3.2f, 0.2f, -5.8f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, 1.6f, 3.2f, 0.2f, 1.9f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);

        box(vc, p, 14.1f, 3.2f, 0.2f, 14.4f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, 21.8f, 3.2f, 0.2f, 22.2f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, 29.6f, 3.2f, 0.2f, 30.0f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);

        // Horizontal drawer rails span strictly between vertical stiles (zero Z-fighting on z=0.2 / 0.7)
        for (float[] sh : DRAWER_SHELVES) {
            box(vc, p, -13.6f, sh[0], 0.22f, -6.2f, sh[1], 0.68f, BRASS, DIVIDER_TINT, light);
            box(vc, p, -5.8f, sh[0], 0.22f, 1.6f, sh[1], 0.68f, BRASS, DIVIDER_TINT, light);
            box(vc, p, 14.4f, sh[0], 0.22f, 21.8f, sh[1], 0.68f, BRASS, DIVIDER_TINT, light);
            box(vc, p, 22.2f, sh[0], 0.22f, 29.6f, sh[1], 0.68f, BRASS, DIVIDER_TINT, light);
        }
        wall(vc, p, -13.95f, 3.25f, 29.95f, 28.55f, 13.5f, DARK, 0xFF3A3028, light);
    }

    private static void bakeMiddleConsole(VertexConsumer vc, PoseStack.Pose p, int light) {
        box(vc, p, 3.2f, 3.2f, 0.3f, 12.8f, 28.6f, 1.6f, BRONZE, WHITE, light);
        box(vc, p, 3.6f, 16.4f, -0.4f, 12.4f, 27.6f, 0.3f, BRASS, AGED_BRASS_TINT, light);
        box(vc, p, 4.2f, 14.2f, -0.4f, 11.8f, 15.6f, 0.3f, DARK, WHITE, light);
        box(vc, p, 4.4f, 4.4f, -0.3f, 11.6f, 5.4f, 0.3f, DARK, 0xFF201810, light);
        box(vc, p, 4.0f, 3.9f, -0.5f, 12.0f, 4.4f, 0.3f, BRASS, WHITE, light);
        for (int k = 0; k < 3; k++) {
            float lx = 5.4f + k * 2.6f;
            box(vc, p, lx - 1f, 7.6f, -0.6f, lx + 1f, 9.6f, 0.3f, DARK, WHITE, light);
        }
    }

    /**
     * Sculpted 3D side and rear exterior architecture for the Punch Card Catalog:
     * 3-bay Steampunk Pneumatic Card-Sorting Archive & Clockwork Drum-Drive Backhouse on the rear,
     * plus walnut-and-brass archive index panels on both exposed rear side walls — strictly disjoint for zero Z-fighting.
     */
    private static void bakeExteriorSidesAndBack(VertexConsumer vc, PoseStack.Pose p, int light) {
        // --- 1. Exposed left & right rear side bays (Z = 16.2..31.4, behind the Copper Golem pedestals)
        for (int s = 0; s < 2; s++) {
            boolean right = s == 1;
            float xIn = right ? 32.02f : -16.65f;
            float xOut = right ? 32.65f : -16.02f;
            float xPost0 = right ? 32.02f : -17.1f;
            float xPost1 = right ? 33.1f : -16.02f;

            // Vertical bronze pilasters & aged-brass waist/crown rails
            box(vc, p, xPost0, 2.02f, 16.1f, xPost1, 29.98f, 17.4f, BRONZE, WHITE, light);
            box(vc, p, xPost0, 2.02f, 30.2f, xPost1, 29.98f, 31.5f, BRONZE, WHITE, light);
            box(vc, p, xIn, 28.6f, 17.4f, xOut, 29.98f, 30.2f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, xIn, 15.2f, 17.4f, xOut, 16.5f, 30.2f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, xIn, 2.02f, 17.4f, xOut, 3.3f, 30.2f, BRASS, AGED_BRASS_TINT, light);

            // Recessed dark-walnut archive backing
            float w0 = right ? 32.02f : -16.32f;
            float w1 = right ? 32.32f : -16.02f;
            box(vc, p, w0, 3.3f, 17.4f, w1, 15.2f, 30.2f, SPRUCE, WALNUT_TINT, light);
            box(vc, p, w0, 16.5f, 17.4f, w1, 28.6f, 30.2f, SPRUCE, WALNUT_TINT, light);

            // Upper side bay: brass index nameplate + 3 punch-card cassette slots
            float c0 = right ? 32.32f : -16.85f;
            float c1 = right ? 32.85f : -16.32f;
            box(vc, p, c0, 25.4f, 19.8f, c1, 27.4f, 27.8f, BRASS, WHITE, light);
            for (float cz : new float[]{18.8f, 22.4f, 26.0f}) {
                box(vc, p, c0, 17.8f, cz, c1, 24.0f, cz + 2.6f, CARD, 0xFFEAD9BC, light);
                float h0 = right ? 32.85f : -17.1f;
                float h1 = right ? 33.1f : -16.85f;
                box(vc, p, h0, 20.2f, cz + 0.6f, h1, 21.6f, cz + 2.0f, BRASS, AGED_BRASS_TINT, light);
            }
            // Lower side bay: bolted dark-iron pneumatic dispatch chest & frost conduit leading forward to pedestal
            box(vc, p, c0, 5.2f, 19.6f, c1, 13.0f, 28.4f, DARK, WHITE, light);
            float strap0 = right ? 32.85f : -17.08f;
            float strap1 = right ? 33.08f : -16.85f;
            box(vc, p, strap0, 5.8f, 20.6f, strap1, 12.4f, 21.8f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, strap0, 5.8f, 26.2f, strap1, 12.4f, 27.4f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, c0, 3.4f, 13.6f, c1, 5.2f, 19.6f, FROST_PIPE, WHITE, light);
        }

        // --- 2. Rear 3-Bay Pneumatic Archive & Clockwork Drum-Drive Facade (Z = 32.02..34.2)
        float[][] rearPosts = {{-16.35f, -14.6f}, {-0.8f, 0.8f}, {15.2f, 16.8f}, {30.6f, 32.35f}};
        for (float[] rp : rearPosts) {
            box(vc, p, rp[0], 0.02f, 32.02f, rp[1], 31.98f, 33.25f, BRONZE, WHITE, light);
        }
        float[][] bays = {{-14.6f, -0.8f}, {0.8f, 15.2f}, {16.8f, 30.6f}};
        for (float[] b : bays) {
            box(vc, p, b[0], 29.0f, 32.02f, b[1], 30.8f, 32.95f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, b[0], 15.0f, 32.02f, b[1], 16.5f, 32.95f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, b[0], 2.02f, 32.02f, b[1], 3.4f, 32.95f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, b[0], 3.4f, 32.02f, b[1], 15.0f, 32.35f, SPRUCE, WALNUT_TINT, light);
            box(vc, p, b[0], 16.5f, 32.02f, b[1], 29.0f, 32.35f, SPRUCE, WALNUT_TINT, light);
        }

        // Left & Right Rear Bays: Stacked Punch-Card Archive Cassettes + outer frosted pneumatic risers
        for (int side = 0; side < 2; side++) {
            float pipe0 = side == 0 ? -13.9f : 27.6f;
            float pipe1 = side == 0 ? -11.5f : 30.0f;
            float rack0 = side == 0 ? -10.8f : 17.6f;
            // Outer vertical frosted pneumatic riser (rises cleanly to roof at y=34.2) + brass clamps
            box(vc, p, pipe0, 3.4f, 32.35f, pipe1, 34.2f, 34.1f, FROST_PIPE, WHITE, light);
            for (float cy : new float[]{8.0f, 18.2f, 26.4f}) {
                box(vc, p, pipe0 - 0.25f, cy, 32.35f, pipe1 + 0.25f, cy + 1.1f, 34.35f, BRASS, AGED_BRASS_TINT, light);
            }
            // Two tiers of 3 vertical punch-card archive cassettes with brass handles
            for (float tierY : new float[]{4.6f, 17.8f}) {
                box(vc, p, rack0 - 0.3f, tierY - 0.5f, 32.35f, rack0 + 9.3f, tierY, 33.2f, BRASS, DIVIDER_TINT, light);
                for (int c = 0; c < 3; c++) {
                    float cx = rack0 + c * 3.1f;
                    box(vc, p, cx, tierY, 32.35f, cx + 2.6f, tierY + 8.8f, 33.15f, CARD, (c % 2 == 0) ? 0xFFEAD9BC : 0xFFDEC8A4, light);
                    box(vc, p, cx + 0.5f, tierY + 3.6f, 33.15f, cx + 2.1f, tierY + 5.2f, 33.5f, BRASS, AGED_BRASS_TINT, light);
                }
            }
        }

        // Center Rear Bay (X = 0.8..15.2): Clockwork Drum-Drive Shaft, Gearbox & Square Rear CRT Porthole Bezel
        // Lower steam-pneumatic manifold chest + pressure gauge
        box(vc, p, 2.0f, 4.2f, 32.35f, 14.0f, 13.8f, 33.45f, DARK, WHITE, light);
        box(vc, p, 2.0f, 12.8f, 33.45f, 14.0f, 13.8f, 33.68f, BRASS, AGED_BRASS_TINT, light);
        box(vc, p, 2.0f, 4.2f, 33.45f, 14.0f, 5.2f, 33.68f, BRASS, AGED_BRASS_TINT, light);
        box(vc, p, 3.2f, 6.4f, 33.45f, 8.2f, 11.4f, 33.85f, BRASS, WHITE, light);
        TextureAtlasSprite gaugeSprite = sprite(GAUGE);
        quad(vc, p, 3.5f, 6.7f, 33.88f, 7.9f, 6.7f, 33.88f, 7.9f, 11.1f, 33.88f, 3.5f, 11.1f, 33.88f,
                gaugeSprite.getU0(), gaugeSprite.getV0(), gaugeSprite.getU1(), gaugeSprite.getV1(), WHITE, light, 0, 0, 1);
        // Vertical brass clockwork drive shaft connecting gearbox up to the roof letter-drum bay
        box(vc, p, 10.8f, 6.2f, 33.45f, 13.2f, 12.0f, 34.1f, BRASS, AGED_BRASS_TINT, light);
        box(vc, p, 11.4f, 12.0f, 32.35f, 12.6f, 32.6f, 33.6f, BRASS, GEAR_TINT, light);
        // Square 9.2 x 9.2 rear Archivist porthole bezel (round 8.0 x 8.0 CRT screen drawn full-bright in CardCatalogRenderer)
        box(vc, p, 1.6f, 18.0f, 32.35f, 10.8f, 27.2f, 33.25f, BRASS, AGED_BRASS_TINT, light);
    }

    private static void bakeRoofAndPiping(VertexConsumer vc, PoseStack.Pose p, int light) {
        // Roof drum housing and dividers (strictly disjoint: base 32.02..33.2, back wall 33.2..39.2, top cap 39.2..40.2)
        box(vc, p, -13, 32.02f, 0.6f, 29, 33.2f, 11, BRONZE, WHITE, light);
        box(vc, p, -13, 33.2f, 9.6f, 29, 39.2f, 11, RIM_COPPER, WHITE, light);
        box(vc, p, -13, 39.2f, 0.6f, 29, 40.2f, 11, BRONZE, WHITE, light);
        for (float x : ROOF_DIVIDER_X) {
            box(vc, p, x, 33.2f, 1.2f, x + 1.4f, 39.2f, 9.6f, BRASS, WHITE, light);
        }
        // Overhead drive-shaft bridge from rear clockwork shaft (x=11.4..12.6, z=32.35..33.6) forward to drum bay (z=11)
        box(vc, p, 11.2f, 32.02f, 11.0f, 12.8f, 33.4f, 32.35f, BRASS, AGED_BRASS_TINT, light);

        // Steaming copper chimney stack on clean brass base collar (zero overlap with roof y=32 or coolant pipe)
        box(vc, p, 20.4f, 32.02f, 20.4f, 26.2f, 33.0f, 26.2f, BRASS, AGED_BRASS_TINT, light);
        box(vc, p, 21.0f, 33.0f, 21.0f, 25.6f, 36.2f, 25.6f, PIPE, 0xFFD09070, light);
        box(vc, p, 20.6f, 36.2f, 20.6f, 26.0f, 37.2f, 26.0f, BRASS, WHITE, light);
        box(vc, p, 21.0f, 37.2f, 21.0f, 25.6f, 44.0f, 25.6f, PIPE, 0xFFD09070, light);
        box(vc, p, 20.4f, 44.0f, 20.4f, 26.2f, 45.2f, 26.2f, BRASS, WHITE, light);

        // Elevated frosted roof manifold connecting left rear riser (-13.9..-11.5) cleanly into chimney base collar (stops at x=20.4!)
        box(vc, p, -13.9f, 32.4f, 22.1f, 20.4f, 34.8f, 24.5f, FROST_PIPE, WHITE, light);
        box(vc, p, -13.9f, 32.4f, 24.5f, -11.5f, 34.2f, 32.35f, FROST_PIPE, WHITE, light);
        box(vc, p, 27.6f, 32.4f, 22.1f, 30.0f, 34.2f, 32.35f, FROST_PIPE, WHITE, light);
        box(vc, p, 26.2f, 32.4f, 22.1f, 27.6f, 34.2f, 24.5f, FROST_PIPE, WHITE, light);
        for (float sx : new float[]{-7.0f, 3.0f, 14.0f}) {
            box(vc, p, sx, 32.02f, 21.9f, sx + 1.6f, 32.4f, 24.7f, BRASS, AGED_BRASS_TINT, light);
        }
        for (float[] ic : ROOF_ICICLES) {
            box(vc, p, ic[0], 32.02f + 0.38f - ic[1], 24.55f, ic[0] + 0.6f, 32.4f, 25.15f, ICE, WHITE, light);
        }
    }

    private static void bakeIvyAndPedestalTrim(VertexConsumer vc, PoseStack.Pose p, int light) {
        wall(vc, p, -16.4f, 19f, -10.5f, 32.4f, -0.9f, VINE, IVY_GREEN, light);
        wall(vc, p, 27.8f, 23f, 32.4f, 32.4f, -0.9f, VINE, IVY_GREEN, light);
        wall(vc, p, 5.5f, 28.2f, 10.5f, 32.4f, -0.8f, VINE, IVY_GREEN, light);
        for (int k = 0; k < 2; k++) {
            boolean right = k == 1;
            float px0 = right ? 32.02f : -32f, px1 = right ? 48f : -16.02f;
            box(vc, p, px0 - 0.35f, 5.6f, 0.1f, px1 + 0.35f, 6.4f, 15.9f, BRASS, 0xFFF2E6D0, light);
            float a0 = right ? 32.02f : -18f, a1 = right ? 34f : -16.02f;
            box(vc, p, a0, 2.2f, 11, a1, 4.8f, 13.6f, FROST_PIPE, WHITE, light);
        }
    }
}
