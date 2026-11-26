package com.golemlogix.client;

import com.google.common.base.Suppliers;
import com.golemlogix.blockentity.CardCatalogBlockEntity;
import com.golemlogix.blocks.CardCatalogBlock;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.CatalogStructure;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.smoothstep;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * Assembled Punch Card Catalog: a rusty copper 3 x 2 x 2 cabinet with twenty brass card drawers (two columns
 * on the left and on the right), a middle porthole screen with a fill gauge and levers, roof letter drums with a drive gear
 * and steaming chimney, and two big copper golems on side copper pedestals matching the statues built into the cabinet.
 * <p>
 * Static cabinet, drum, and gear baking lives in {@link CatalogCabinetMesh}. Coordinates are pixels in the owner's
 * north-facing frame: cabinet X -16..32, Y 0..32, Z 0..32 (front = Z 0), pedestals in X -32..-16 and 32..48.
 */
public class CardCatalogRenderer extends LegacyBer<CardCatalogBlockEntity> {
    public static final int DRAWERS = 20;

    static final Identifier RUST = modBlock("resonator_body"), BRASS = modBlock("sat_brass"), BRONZE = modBlock("sat_bronze"),
            DARK = modBlock("sat_dark"), DRAWER = modBlock("catalog_drawer"), DRUM = modBlock("catalog_drum"),
            SCREEN = modBlock("catalog_screen"), PANEL = modBlock("cryo_panel"), FROST_PIPE = modBlock("resonator_pipe"),
            ICE = modBlock("sat_ice"), CARD = modBlock("encoder_cards"), VINE = mcBlock("vine"), PIPE = mcBlock("copper_block");
    private static final Identifier[] PEDESTAL = {
            mcBlock("copper_block"), mcBlock("exposed_copper"),
            mcBlock("weathered_copper"), mcBlock("oxidized_copper")};

    private static final int WHITE = 0xFFFFFFFF;
    private static final float DRAWER_W = 7.4f, DRAWER_H = 4.4f, DRAWER_CLOSED_Z = 0.6f;
    private static final float[] DRAWER_COL_X = {-13.6f, -5.8f, 14.4f, 22.2f};
    private static final float[][] DRUM_X = {{-11.6f, -3.6f}, {-2.2f, 6.2f}, {7.6f, 15.6f}, {17.0f, 27.6f}};
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f FWD_NORTH = new Vector3f(0f, 0f, -1f);
    private static final Vector3f SCRATCH_POS = new Vector3f();
    private static final Supplier<ItemStack> PUNCH_CARD = Suppliers.memoize(() -> new ItemStack(ModItems.COPPER_PUNCH_CARD.get()));

    private record Meshes(TextureAtlasSprite atlasMark, BerBuffers.Baked cabinet, BerBuffers.Baked[] drums, BerBuffers.Baked gear) {
    }

    private static Meshes meshes;

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            BerBuffers.Baked[] drums = new BerBuffers.Baked[DRUM_X.length];
            for (int d = 0; d < DRUM_X.length; d++) {
                float x0 = DRUM_X[d][0] + 0.2f, x1 = DRUM_X[d][1] - 0.2f;
                drums[d] = BerBuffers.bake((vc, pose) -> CatalogCabinetMesh.bakeDrum(vc, pose, x0, x1));
            }
            meshes = new Meshes(mark,
                    BerBuffers.bake(CatalogCabinetMesh::bakeCabinet),
                    drums,
                    BerBuffers.bake(CatalogCabinetMesh::bakeGear));
        }
        return meshes;
    }

    public CardCatalogRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CardCatalogBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockState s = be.getBlockState();
        if (!s.hasProperty(CardCatalogBlock.FACING)) {
            return new AABB(pos);
        }
        Direction f = s.getValue(CardCatalogBlock.FACING);
        AABB box = new AABB(pos);
        for (int i = 0; i < CatalogStructure.CELLS.length; i++) {
            box = box.minmax(new AABB(CatalogStructure.cellPos(pos, f, i)));
        }
        return box.inflate(1.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** Left edge and bottom of drawer {@code i} (0..19): columns left to right, rows bottom to top. */
    static float drawerX(int i) {
        return DRAWER_COL_X[i / 5];
    }

    static float drawerY(int i) {
        return 3.6f + (i % 5) * 4.92f;
    }

    @Override
    public void render(CardCatalogBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CardCatalogBlock.FORMED) || !state.getValue(CardCatalogBlock.FORMED)) {
            return;
        }
        Direction facing = state.getValue(CardCatalogBlock.FACING);
        float time = (level.getGameTime() % 72000L) + partialTick;
        int light = LevelRenderer.getLightCoords(level, be.getBlockPos().relative(facing));
        light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), 6), LightCoordsUtil.sky(light));
        float open = smoothstep(Mth.lerp(partialTick, be.drawersPrev, be.drawers));
        float drums = Mth.lerp(partialTick, be.drumsPrev, be.drums);
        int cap = Math.max(1, be.clientCapacity());
        float fill = be.clientCapacity() == 0 ? 0f : Math.min(1f, be.clientCards() / (float) cap);
        Meshes baked = meshes();
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        baked.cabinet().draw(buffers, solid, p, light, 0);

        VertexConsumer vc = buffers.getBuffer(solid);
        TextureAtlasSprite brassSprite = sprite(BRASS);
        renderDrawers(be, vc, p, light, time, open, brassSprite);
        renderConsoleAndLevers(vc, pose, p, light, time, open, fill, brassSprite);
        renderDrumsAndGear(pose, buffers, solid, baked, light, time, open, drums);
        renderSidePedestalsAndGolems(be, vc, pose, p, buffers, light, time, open);

        pose.popPose();
    }

    /** Twenty brass drawers: slide out in a wave while the UI is open; when idle one slides out to be sorted. */
    private static void renderDrawers(CardCatalogBlockEntity be, VertexConsumer vc, PoseStack.Pose p, int light,
                                      float time, float open, TextureAtlasSprite brassSprite) {
        int idleDrawer = (int) ((time / 70f) % DRAWERS);
        float idlePhase = (time % 70f) / 70f;
        long seed = be.getBlockPos().asLong();
        TextureAtlasSprite rustSprite = sprite(RUST), cardSprite = sprite(CARD);
        for (int i = 0; i < DRAWERS; i++) {
            float wave = 0.5f + 0.5f * Mth.sin(time * 0.07f + i * 2.3f + seed % 7);
            float out = open * (1.2f + 4.6f * wave * wave);
            if (i == (idleDrawer * 7 + 3) % DRAWERS && idlePhase < 0.5f) {
                out = Math.max(out, 3.2f * Mth.sin(idlePhase * 2f * Mth.PI));
            }
            renderDrawer(vc, p, drawerX(i), drawerY(i), out, i, light, rustSprite, cardSprite, brassSprite);
        }
    }

    /** Single drawer: front plate with label/handle, plus hollow tray and tabbed filing folders when pulled open. */
    private static void renderDrawer(VertexConsumer vc, PoseStack.Pose p, float x, float y, float out, int i, int light,
                                     TextureAtlasSprite rust, TextureAtlasSprite card, TextureAtlasSprite brass) {
        float z = DRAWER_CLOSED_Z - out;
        if (out > 0.05f) {
            box(vc, p, x + 0.2f, y + 0.2f, z, x + DRAWER_W - 0.2f, y + 0.5f, DRAWER_CLOSED_Z, rust, 0xFF8A6A58, light);
            box(vc, p, x + 0.2f, y + 0.5f, z, x + 0.65f, y + DRAWER_H - 0.3f, DRAWER_CLOSED_Z, rust, 0xFFB09080, light);
            box(vc, p, x + DRAWER_W - 0.65f, y + 0.5f, z, x + DRAWER_W - 0.2f, y + DRAWER_H - 0.3f, DRAWER_CLOSED_Z, rust, 0xFFB09080, light);
            if (out > 0.15f) {
                renderDrawerFolders(vc, p, x, y, z, i, light, card, brass);
            }
        }
        box(vc, p, x, y, z - 0.4f, x + DRAWER_W, y + DRAWER_H, z, rust, WHITE, light);
        fitWall(vc, p, x, y, x + DRAWER_W, y + DRAWER_H, z - 0.42f, DRAWER, WHITE, light);
    }

    /** Archive manila folders with protruding index tabs and punch cards stacked inside an open drawer. */
    private static void renderDrawerFolders(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int i, int light,
                                            TextureAtlasSprite card, TextureAtlasSprite brass) {
        float startZ = z + 0.45f;
        float endZ = 0.50f;
        int folderIdx = 0;
        for (float cz = startZ; cz < endZ; cz += 0.72f, folderIdx++) {
            int folderColor = ((folderIdx + i) % 2 == 0) ? 0xFFE8D6B8 : 0xFFDEC29E;
            box(vc, p, x + 0.75f, y + 0.5f, cz, x + DRAWER_W - 0.75f, y + DRAWER_H - 0.45f, cz + 0.12f, card, folderColor, light);

            int tabPos = (folderIdx + i * 2) % 3;
            float tabW = 1.9f;
            float tabX = switch (tabPos) {
                case 0 -> x + 0.9f;
                case 1 -> x + (DRAWER_W - tabW) / 2f;
                default -> x + DRAWER_W - 0.9f - tabW;
            };
            int tabColor = ((folderIdx + i) % 2 == 0) ? 0xFFCCA265 : 0xFFBF9456;
            box(vc, p, tabX, y + DRAWER_H - 0.45f, cz, tabX + tabW, y + DRAWER_H - 0.45f + 0.48f, cz + 0.12f, brass, tabColor, light);

            float sheetZ = cz + 0.18f;
            if (sheetZ + 0.36f < endZ) {
                float sheetRise = 0.15f + ((folderIdx * 5 + i * 3) % 4) * 0.08f;
                box(vc, p, x + 0.90f, y + 0.5f, sheetZ, x + DRAWER_W - 0.90f, y + DRAWER_H - 0.55f + sheetRise, sheetZ + 0.36f,
                        card, 0xFFF5EEDB, light);
            }
        }
    }

    /** Middle console: glowing porthole screen, teal fill bar, and three tilting brass levers. */
    private static void renderConsoleAndLevers(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, int light,
                                               float time, float open, float fill, TextureAtlasSprite brassSprite) {
        fitWall(vc, p, 3.4f, 17.1f, 12.6f, 26.9f, -0.5f, SCREEN, WHITE, LightCoordsUtil.FULL_BRIGHT);
        // Square 8.0 x 8.0 rear Archivist CRT porthole inside the rear center bezel (+Z face at z = 33.3f)
        TextureAtlasSprite scr = sprite(SCREEN);
        com.golemlogix.client.render.PixelMesh.quad(vc, p,
                2.2f, 18.6f, 33.3f, 10.2f, 18.6f, 33.3f, 10.2f, 26.6f, 33.3f, 2.2f, 26.6f, 33.3f,
                scr.getU0(), scr.getV0(), scr.getU1(), scr.getV1(), WHITE, LightCoordsUtil.FULL_BRIGHT, 0, 0, 1);
        if (fill > 0f) {
            float fx = 4.6f + 6.8f * fill;
            box(vc, p, 4.6f, 14.5f, -0.5f, fx, 15.3f, -0.35f, PANEL, 0xFF40F0E8, LightCoordsUtil.FULL_BRIGHT);
        }
        for (int k = 0; k < 3; k++) {
            float lx = 5.4f + k * 2.6f;
            float tilt = (k == 1 ? 1 : -1) * 28f * (open > 0.5f ? -1 : 1) + 4f * Mth.sin(time * 0.05f + k);
            pose.pushPose();
            pose.translate(lx * PIXEL, 8.6f * PIXEL, -0.2f * PIXEL);
            pose.mulPose(Axis.XP.rotationDegrees(-25f + tilt));
            box(vc, pose.last(), -0.35f, 0f, -0.35f, 0.35f, 4.6f, 0.35f, brassSprite, 0xFFB0B0B0, light);
            box(vc, pose.last(), -0.75f, 4.4f, -0.75f, 0.75f, 5.9f, 0.75f, brassSprite, k == 1 ? 0xFFC8432F : 0xFFFFE6A0, light);
            pose.popPose();
        }
    }

    /** Four rotating roof letter drums and the end drive gear. */
    private static void renderDrumsAndGear(PoseStack pose, MultiBufferSource buffers, RenderType solid, Meshes baked,
                                           int light, float time, float open, float drums) {
        for (int d = 0; d < 4; d++) {
            float angle = drums * 45f / (1 + (3 - d) * 0.8f) + d * 17f;
            pose.pushPose();
            pose.translate(0f, 36.2f * PIXEL, 5.4f * PIXEL);
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            baked.drums()[d].draw(buffers, solid, pose.last(), light, 0);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(29.6f * PIXEL, 36.2f * PIXEL, 5.4f * PIXEL);
        pose.mulPose(Axis.XP.rotationDegrees(-drums * 45f * 1.6f - time * (open > 0.1f ? 0f : 0.2f)));
        baked.gear().draw(buffers, solid, pose.last(), light, 0);
        pose.popPose();
    }

    /** Per-golem copper pedestals (drawn before puppets to keep cutout batching intact) and the two side copper golems. */
    private static void renderSidePedestalsAndGolems(CardCatalogBlockEntity be, VertexConsumer vc, PoseStack pose, PoseStack.Pose p,
                                                     MultiBufferSource buffers, int light, float time, float open) {
        for (int k = 0; k < 2; k++) {
            boolean right = k == 1;
            int ox = Math.max(0, Math.min(3, be.golemOxidation(k)));
            float px0 = right ? 32.02f : -32f, px1 = right ? 48f : -16.02f;
            box(vc, p, px0, 0.02f, 0.5f, px1, 5.6f, 15.5f, PEDESTAL[ox], WHITE, light);
        }
        for (int k = 0; k < 2; k++) {
            renderSideGolem(be, pose, buffers, light, time, open, k);
        }
    }

    /** Side golem (k = 0 left, 1 right): near hand braces the roof corner, far hand waves a punch card when open. */
    private static void renderSideGolem(CardCatalogBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                        int light, float time, float open, int k) {
        boolean right = k == 1;
        int ox = be.golemOxidation(k);
        float cx = right ? 40.5f : -24.5f;
        float cz = 7.5f;
        float breathe = Mth.sin(time * 0.06f + k * 2f) * 0.02f;
        float wave = open > 0.05f ? open * (0.35f + 0.25f * Mth.sin(time * 0.25f + k)) : 0.08f * Mth.sin(time * 0.03f + k);
        float look = open > 0.05f ? open * 0.35f : 0.15f * Mth.sin(time * 0.012f + k * 3f);
        float scale = 1.15f + breathe;

        Consumer<PoseStack> holdCard = hp -> {
            hp.scale(0.38f, 0.38f, 0.38f);
            hp.mulPose(Axis.XP.rotationDegrees(90f));
            hp.mulPose(Axis.ZP.rotationDegrees(right ? -15f : 15f));
            hp.translate(0f, 0.15f, 0f);
            BerBuffers.item(buffers, PUNCH_CARD.get(), ItemDisplayContext.FIXED, light, hp, be.getLevel(), 0);
        };
        CopperGolemPuppet.Arms arms = right
                ? new CopperGolemPuppet.Arms(-0.65f - wave, 0.10f, -1.30f, -0.05f, look, holdCard, null)
                : new CopperGolemPuppet.Arms(-1.30f, 0.05f, -0.65f - wave, -0.10f, -look, null, holdCard);

        SCRATCH_POS.set(cx * PIXEL, 6f * PIXEL, cz * PIXEL);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_POS, FWD_NORTH,
                UP, scale, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f), ox, null, arms);
    }
}
