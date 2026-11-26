package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import static com.golemlogix.client.CardCatalogRenderer.BRASS;
import static com.golemlogix.client.CardCatalogRenderer.BRONZE;
import static com.golemlogix.client.CardCatalogRenderer.DARK;
import static com.golemlogix.client.CardCatalogRenderer.DRUM;
import static com.golemlogix.client.CardCatalogRenderer.FROST_PIPE;
import static com.golemlogix.client.CardCatalogRenderer.ICE;
import static com.golemlogix.client.CardCatalogRenderer.PIPE;
import static com.golemlogix.client.CardCatalogRenderer.RUST;
import static com.golemlogix.client.CardCatalogRenderer.VINE;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.wall;

/**
 * Static mesh baking for {@link CardCatalogRenderer}: cabinet hull and frame, drawer dividers, middle console housing,
 * roof letter-drum bay, coolant pipes, hanging ivy, pedestal rims, letter drums, and roof drive gear.
 */
final class CatalogCabinetMesh {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BACK_NICHE_TINT = 0xFF2A221C;
    private static final int DIVIDER_TINT = 0xFFE4D2B4;
    private static final int AGED_BRASS_TINT = 0xFFE8D8BC;
    private static final int GEAR_TINT = 0xFFFFE6A0;
    private static final int IVY_GREEN = 0xFF5A9A3A;

    private static final float[] ROOF_DIVIDER_X = {-13f, -3.6f, 6.2f, 15.6f, 27.6f};
    private static final float[] PIPE_CLAMP_Y = {8f, 18f, 26f};
    private static final float[][] ROOF_ICICLES = {{-10.5f, 2.2f}, {-3f, 1.4f}, {6.5f, 2.6f}, {17f, 1.6f}};
    private static final float[][] DRAWER_SHELVES = {
            {3.2f, 3.6f},     // under bottom row
            {8.0f, 8.52f},    // between rows 0 and 1
            {12.92f, 13.44f}, // between rows 1 and 2
            {17.84f, 18.36f}, // between rows 2 and 3
            {22.76f, 23.28f}, // between rows 3 and 4
            {27.68f, 28.6f}   // above top row
    };

    private CatalogCabinetMesh() {
    }

    /** Bakes all static cutout cabinet geometry into a single replayable mesh (light is overridden at draw time). */
    static void bakeCabinet(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        bakeHullAndFrame(vc, p, light);
        bakeDrawerDividers(vc, p, light);
        bakeMiddleConsole(vc, p, light);
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
        // plinth, back wall, sides, roof
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, WHITE, light);
        box(vc, p, -15, 2, 29, 31, 30, 32, RUST, WHITE, light);
        box(vc, p, -16, 2, 1.5f, -14, 30, 32, RUST, WHITE, light);
        box(vc, p, 30, 2, 1.5f, 32, 30, 32, RUST, WHITE, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, RUST, WHITE, light);
        // dark bronze outer front frame, aged-brass inner mullions, and blind backing behind the drawer bays
        box(vc, p, -16, 2, -0.5f, -14, 30, 1.5f, BRONZE, WHITE, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 1.5f, BRONZE, WHITE, light);
        box(vc, p, -14, 28.6f, -0.5f, 30, 30, 1.5f, BRONZE, WHITE, light);
        box(vc, p, -14, 2, -0.5f, 30, 3.2f, 1.5f, BRONZE, WHITE, light);
        box(vc, p, 1.9f, 3.2f, -0.3f, 3.2f, 28.6f, 1.5f, BRASS, WHITE, light);
        box(vc, p, 12.8f, 3.2f, -0.3f, 14.1f, 28.6f, 1.5f, BRASS, WHITE, light);
        box(vc, p, -14.0f, 3.2f, 0.6f, 1.9f, 28.6f, 2.0f, DARK, BACK_NICHE_TINT, light);
        box(vc, p, 14.1f, 3.2f, 0.6f, 30.0f, 28.6f, 2.0f, DARK, BACK_NICHE_TINT, light);
    }

    private static void bakeDrawerDividers(VertexConsumer vc, PoseStack.Pose p, int light) {
        // vertical stiles for left drawer bay (X: -14.0..1.9) and right drawer bay (X: 14.1..30.0)
        box(vc, p, -14.0f, 3.2f, 0.2f, -13.6f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, -6.2f, 3.2f, 0.2f, -5.8f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, 1.6f, 3.2f, 0.2f, 1.9f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);

        box(vc, p, 14.1f, 3.2f, 0.2f, 14.4f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, 21.8f, 3.2f, 0.2f, 22.2f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);
        box(vc, p, 29.6f, 3.2f, 0.2f, 30.0f, 28.6f, 0.7f, BRASS, DIVIDER_TINT, light);

        for (float[] sh : DRAWER_SHELVES) {
            box(vc, p, -14.0f, sh[0], 0.2f, 1.9f, sh[1], 0.7f, BRASS, DIVIDER_TINT, light);
            box(vc, p, 14.1f, sh[0], 0.2f, 30.0f, sh[1], 0.7f, BRASS, DIVIDER_TINT, light);
        }
        wall(vc, p, -14, 3.2f, 30, 28.6f, 13.5f, DARK, 0xFF3A3028, light);
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

    private static void bakeRoofAndPiping(VertexConsumer vc, PoseStack.Pose p, int light) {
        // roof drum housing and dividers
        box(vc, p, -13, 32, 0.6f, 29, 33.2f, 11, BRONZE, WHITE, light);
        box(vc, p, -13, 33.2f, 9.6f, 29, 40, 11, RUST, WHITE, light);
        box(vc, p, -13, 39.2f, 0.6f, 29, 40.2f, 11, BRONZE, WHITE, light);
        for (float x : ROOF_DIVIDER_X) {
            box(vc, p, x, 33.2f, 1.2f, x + 1.4f, 39.2f, 9.6f, BRASS, WHITE, light);
        }
        // steaming copper chimney stack
        box(vc, p, 21, 32, 21, 25.6f, 44, 25.6f, PIPE, 0xFFD09070, light);
        box(vc, p, 20.4f, 44, 20.4f, 26.2f, 45.2f, 26.2f, BRASS, WHITE, light);
        box(vc, p, 20.6f, 36, 20.6f, 26f, 37f, 26f, BRASS, WHITE, light);
        // frosted rear coolant pipes, clamps, and icicles
        box(vc, p, -15.6f, 2, 30.4f, -12.6f, 33, 33.4f, FROST_PIPE, WHITE, light);
        box(vc, p, -15.6f, 32, 24.4f, 23, 35, 27.4f, FROST_PIPE, WHITE, light);
        box(vc, p, 28.6f, 2, 30.4f, 31.6f, 30, 33.4f, FROST_PIPE, WHITE, light);
        for (float y : PIPE_CLAMP_Y) {
            box(vc, p, -15.9f, y, 30.1f, -12.3f, y + 1.2f, 33.7f, BRASS, AGED_BRASS_TINT, light);
            box(vc, p, 28.3f, y + 2f, 30.1f, 31.9f, y + 3.2f, 33.7f, BRASS, AGED_BRASS_TINT, light);
        }
        for (float[] ic : ROOF_ICICLES) {
            box(vc, p, ic[0], 32 - ic[1], 25.4f, ic[0] + 0.6f, 32, 26f, ICE, WHITE, light);
        }
    }

    private static void bakeIvyAndPedestalTrim(VertexConsumer vc, PoseStack.Pose p, int light) {
        wall(vc, p, -16.4f, 19f, -10.5f, 32.4f, -0.9f, VINE, IVY_GREEN, light);
        wall(vc, p, 27.8f, 23f, 32.4f, 32.4f, -0.9f, VINE, IVY_GREEN, light);
        wall(vc, p, 5.5f, 28.2f, 10.5f, 32.4f, -0.8f, VINE, IVY_GREEN, light);
        for (int k = 0; k < 2; k++) {
            boolean right = k == 1;
            float px0 = right ? 32f : -32f, px1 = right ? 48f : -16f;
            box(vc, p, px0 - 0.4f, 5.6f, 0.1f, px1 + 0.4f, 6.4f, 15.9f, BRASS, 0xFFF2E6D0, light);
            float a0 = right ? 30f : -18f, a1 = right ? 34f : -14f;
            box(vc, p, a0, 2.2f, 11, a1, 4.8f, 13.6f, FROST_PIPE, WHITE, light);
        }
    }
}
