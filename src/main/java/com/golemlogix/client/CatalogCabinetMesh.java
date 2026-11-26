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
import static com.golemlogix.client.render.PixelMesh.sprite;
import static com.golemlogix.client.render.PixelMesh.wall;

/**
 * Static baked meshes for {@link CardCatalogRenderer}: cabinet carcass, drawer dividers, console backing, roof drum
 * housing, coolant pipes, hanging ivy, pedestal rims, letter drums, and drive gear.
 */
final class CatalogCabinetMesh {
    static final ResourceLocation RUST = modBlock("resonator_body"), BRASS = modBlock("sat_brass"),
            BRONZE = modBlock("sat_bronze"), DARK = modBlock("sat_dark"), DRUM = modBlock("catalog_drum"),
            FROST_PIPE = modBlock("resonator_pipe"), ICE = modBlock("sat_ice"),
            VINE = mcBlock("vine"), PIPE = mcBlock("copper_block");

    private static final float[][] DRUM_X = {{-11.6f, -3.6f}, {-2.2f, 6.2f}, {7.6f, 15.6f}, {17.0f, 27.6f}};
    private static final float[] ROOF_DIVIDERS = {-13f, -3.6f, 6.2f, 15.6f, 27.6f};
    private static final float[] CLAMP_Y = {8f, 18f, 26f};
    private static final float[][] COOLANT_ICICLES = {{-10.5f, 2.2f}, {-3f, 1.4f}, {6.5f, 2.6f}, {17f, 1.6f}};
    private static final float[][] DRAWER_SHELVES = {
            {3.2f, 3.6f},    // under row 0
            {8.0f, 8.52f},   // between rows 0 and 1
            {12.92f, 13.44f},// between rows 1 and 2
            {17.84f, 18.36f},// between rows 2 and 3
            {22.76f, 23.28f},// between rows 3 and 4
            {27.68f, 28.6f}  // above row 4
    };

    record Meshes(TextureAtlasSprite atlasMark, Baked cabinet, Baked[] drums, Baked gear) {
    }

    private static Meshes meshes;

    private CatalogCabinetMesh() {
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

    private static void bakeDrum(VertexConsumer vc, PoseStack pose, float x0, float x1) {
        for (int f = 0; f < 8; f++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(f * 45f));
            box(vc, pose.last(), x0, -1.2f, -2.85f, x1, 1.2f, -2.35f, DRUM, 0xFFFFFFFF, 0);
            pose.popPose();
        }
    }

    private static void bakeCabinet(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        int light = 0;

        // body: plinth, back wall, sides, roof
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, white, light);
        box(vc, p, -15, 2, 29, 31, 30, 32, RUST, white, light);
        box(vc, p, -16, 2, 1.5f, -14, 30, 32, RUST, white, light);
        box(vc, p, 30, 2, 1.5f, 32, 30, 32, RUST, white, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, RUST, white, light);

        // dark bronze outer front frame, aged-brass inner mullions and blind back wall behind drawers
        box(vc, p, -16, 2, -0.5f, -14, 30, 1.5f, BRONZE, white, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 1.5f, BRONZE, white, light);
        box(vc, p, -14, 28.6f, -0.5f, 30, 30, 1.5f, BRONZE, white, light);
        box(vc, p, -14, 2, -0.5f, 30, 3.2f, 1.5f, BRONZE, white, light);
        box(vc, p, 1.9f, 3.2f, -0.3f, 3.2f, 28.6f, 1.5f, BRASS, white, light);
        box(vc, p, 12.8f, 3.2f, -0.3f, 14.1f, 28.6f, 1.5f, BRASS, white, light);
        box(vc, p, -14.0f, 3.2f, 0.6f, 1.9f, 28.6f, 2.0f, DARK, 0xFF2A221C, light);
        box(vc, p, 14.1f, 3.2f, 0.6f, 30.0f, 28.6f, 2.0f, DARK, 0xFF2A221C, light);
        bakeDrawerDividers(vc, p, light);
        wall(vc, p, -14, 3.2f, 30, 28.6f, 13.5f, DARK, 0xFF3A3028, light);

        // middle console static housing
        box(vc, p, 3.2f, 3.2f, 0.3f, 12.8f, 28.6f, 1.6f, BRONZE, white, light);
        box(vc, p, 3.6f, 16.4f, -0.4f, 12.4f, 27.6f, 0.3f, BRASS, 0xFFE8D8BC, light);
        box(vc, p, 4.2f, 14.2f, -0.4f, 11.8f, 15.6f, 0.3f, DARK, white, light);
        box(vc, p, 4.4f, 4.4f, -0.3f, 11.6f, 5.4f, 0.3f, DARK, 0xFF201810, light);
        box(vc, p, 4.0f, 3.9f, -0.5f, 12.0f, 4.4f, 0.3f, BRASS, white, light);
        for (int k = 0; k < 3; k++) {
            float lx = 5.4f + k * 2.6f;
            box(vc, p, lx - 1f, 7.6f, -0.6f, lx + 1f, 9.6f, 0.3f, DARK, white, light);
        }

        bakeRoofAndPipes(vc, p, white, light);
    }

    private static void bakeRoofAndPipes(VertexConsumer vc, PoseStack.Pose p, int white, int light) {
        // roof box, drum dividers and steaming stack
        box(vc, p, -13, 32, 0.6f, 29, 33.2f, 11, BRONZE, white, light);
        box(vc, p, -13, 33.2f, 9.6f, 29, 40, 11, RUST, white, light);
        box(vc, p, -13, 39.2f, 0.6f, 29, 40.2f, 11, BRONZE, white, light);
        for (float x : ROOF_DIVIDERS) {
            box(vc, p, x, 33.2f, 1.2f, x + 1.4f, 39.2f, 9.6f, BRASS, white, light);
        }
        box(vc, p, 21, 32, 21, 25.6f, 44, 25.6f, PIPE, 0xFFD09070, light);
        box(vc, p, 20.4f, 44, 20.4f, 26.2f, 45.2f, 26.2f, BRASS, white, light);
        box(vc, p, 20.6f, 36, 20.6f, 26f, 37f, 26f, BRASS, white, light);

        // coolant pipes, clamps and icicles
        box(vc, p, -15.6f, 2, 30.4f, -12.6f, 33, 33.4f, FROST_PIPE, white, light);
        box(vc, p, -15.6f, 32, 24.4f, 23, 35, 27.4f, FROST_PIPE, white, light);
        box(vc, p, 28.6f, 2, 30.4f, 31.6f, 30, 33.4f, FROST_PIPE, white, light);
        for (float y : CLAMP_Y) {
            box(vc, p, -15.9f, y, 30.1f, -12.3f, y + 1.2f, 33.7f, BRASS, 0xFFE8D8BC, light);
            box(vc, p, 28.3f, y + 2f, 30.1f, 31.9f, y + 3.2f, 33.7f, BRASS, 0xFFE8D8BC, light);
        }
        for (float[] ic : COOLANT_ICICLES) {
            box(vc, p, ic[0], 32 - ic[1], 25.4f, ic[0] + 0.6f, 32, 26f, ICE, white, light);
        }

        // hanging ivy
        int green = 0xFF5A9A3A;
        wall(vc, p, -16.4f, 19f, -10.5f, 32.4f, -0.9f, VINE, green, light);
        wall(vc, p, 27.8f, 23f, 32.4f, 32.4f, -0.9f, VINE, green, light);
        wall(vc, p, 5.5f, 28.2f, 10.5f, 32.4f, -0.8f, VINE, green, light);

        // side pedestal brass rims and frosted pipe stubs
        for (int k = 0; k < 2; k++) {
            boolean right = k == 1;
            float px0 = right ? 32f : -32f, px1 = right ? 48f : -16f;
            box(vc, p, px0 - 0.4f, 5.6f, 0.1f, px1 + 0.4f, 6.4f, 15.9f, BRASS, 0xFFF2E6D0, light);
            float a0 = right ? 30f : -18f, a1 = right ? 34f : -14f;
            box(vc, p, a0, 2.2f, 11, a1, 4.8f, 13.6f, FROST_PIPE, white, light);
        }
    }

    private static void bakeDrawerDividers(VertexConsumer vc, PoseStack.Pose p, int light) {
        int dividerTint = 0xFFE4D2B4;
        // vertical stiles of left section (X: -14.0 .. 1.9)
        box(vc, p, -14.0f, 3.2f, 0.2f, -13.6f, 28.6f, 0.7f, BRASS, dividerTint, light);
        box(vc, p, -6.2f, 3.2f, 0.2f, -5.8f, 28.6f, 0.7f, BRASS, dividerTint, light);
        box(vc, p, 1.6f, 3.2f, 0.2f, 1.9f, 28.6f, 0.7f, BRASS, dividerTint, light);
        // vertical stiles of right section (X: 14.1 .. 30.0)
        box(vc, p, 14.1f, 3.2f, 0.2f, 14.4f, 28.6f, 0.7f, BRASS, dividerTint, light);
        box(vc, p, 21.8f, 3.2f, 0.2f, 22.2f, 28.6f, 0.7f, BRASS, dividerTint, light);
        box(vc, p, 29.6f, 3.2f, 0.2f, 30.0f, 28.6f, 0.7f, BRASS, dividerTint, light);
        // horizontal shelves between drawer rows
        for (float[] sh : DRAWER_SHELVES) {
            box(vc, p, -14.0f, sh[0], 0.2f, 1.9f, sh[1], 0.7f, BRASS, dividerTint, light);
            box(vc, p, 14.1f, sh[0], 0.2f, 30.0f, sh[1], 0.7f, BRASS, dividerTint, light);
        }
    }

    private static void bakeGear(VertexConsumer vc, PoseStack pose) {
        box(vc, pose.last(), 0f, -2.6f, -2.6f, 1.2f, 2.6f, 2.6f, BRASS, 0xFFFFE6A0, 0);
        for (int t = 0; t < 10; t++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(t * 36f));
            box(vc, pose.last(), 0f, 2.4f, -0.6f, 1.2f, 3.6f, 0.6f, BRASS, 0xFFFFE6A0, 0);
            pose.popPose();
        }
        box(vc, pose.last(), -0.2f, -0.8f, -0.8f, 1.6f, 0.8f, 0.8f, DARK, 0xFFFFFFFF, 0);
    }
}
