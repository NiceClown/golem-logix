package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.blocks.AtlantBlock;
import com.golemlogix.blocks.CryoMainframeBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import com.golemlogix.blocks.GolemProperties;
import com.golemlogix.init.ModBlocks;
import com.golemlogix.init.ModItems;
import com.golemlogix.system.MainframeStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Consumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The whole assembled Cryo-Mainframe: a bronze 3 x 2 x 2 cabinet in a brass frame; behind double glass doors
 * a glowing teal matrix and two shelves of calculators on which snowman crews assemble items; on top
 * a brass crest with a pressure gauge and a red valve wheel. Atlants hold the cabinet at the sides (they are real blocks).
 * While someone has the cabinet window open, the doors swing outwards.
 * <p>
 * Coordinates are pixels in the owner's north-facing frame: X -16..32 (three cells across), Y 0..32, Z 0..32 (front = Z 0).
 */
public class CryoMainframeRenderer extends com.golemlogix.client.render.LegacyBer<CryoMainframeBlockEntity> {
    private static final Identifier BRONZE = tex("sat_bronze"), BRASS = tex("sat_brass"), DARK = tex("sat_dark"),
            MATRIX = tex("cryo_panel_matrix"), GLASS = tex("sat_glass"), PANEL = tex("cryo_panel"), GAUGE = tex("monitor_gauge"),
            COPPER = Identifier.withDefaultNamespace("block/copper_block"), FLOOR = tex("mainframe_floor");

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name);
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
        float time = level.getGameTime() + partialTick;
        int light = LevelRenderer.getLightCoords(level, be.getBlockPos().relative(facing));
        light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), 7), LightCoordsUtil.sky(light));
        float doors = Mth.lerp(partialTick, be.doorsPrev, be.doors);
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float foreman = Mth.lerp(partialTick, be.foremanPrev, be.foreman);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;

        // body: plinth, back wall, sides, roof
        box(vc, p, -16, 0, 0, 32, 2, 32, DARK, white, light);
        box(vc, p, -15, 2, 29, 31, 30, 32, BRONZE, white, light);
        box(vc, p, -16, 2, 2, -14, 30, 32, BRONZE, white, light);
        box(vc, p, 30, 2, 2, 32, 30, 32, BRONZE, white, light);
        box(vc, p, -16, 30, 0, 32, 32, 32, BRONZE, white, light);
        // brass front frame and ribs
        box(vc, p, -16, 2, -0.5f, -14, 30, 2, BRASS, white, light);
        box(vc, p, 30, 2, -0.5f, 32, 30, 2, BRASS, white, light);
        box(vc, p, -16, 28, -0.5f, 32, 30, 2, BRASS, white, light);
        box(vc, p, -16, 2, -0.5f, 32, 3, 2, BRASS, white, light);
        for (float x : new float[]{-16.3f, 31.3f}) {
            box(vc, p, x, 0, 30.7f, x + 1f, 32.3f, 32.3f, BRASS, white, light);
        }
        // inside: a glowing matrix over the whole back wall and a brass shelf in the middle
        wall(vc, p, -14, 3, 30, 28, 28.9f, MATRIX, white, LightCoordsUtil.FULL_BRIGHT);
        box(vc, p, -14, 15, 3, 30, 16, 29, BRASS, white, light);
        box(vc, p, -14, 3, 3, 30, 3.2f, 29, FLOOR, white, light);
        box(vc, p, -14, 16, 3, 30, 16.2f, 29, FLOOR, white, light);

        // calculators on the shelves (as many as are inserted), at the back wall
        int calcs = be.clientCalcs();
        BlockState calc = ModBlocks.CRYO_CALCULATOR.get().defaultBlockState().setValue(GolemProperties.ACTIVE, spin > 0.5f);
        for (int k = 0; k < Math.min(calcs, CryoMainframeBlockEntity.CALC_SLOTS); k++) {
            int shelf = k / 6, slot = k % 6;
            pose.pushPose();
            pose.translate((-12.5f + slot * 7.2f) / 16f, (shelf == 0 ? 3.2f : 16.2f) / 16f, 22.5f / 16f);
            pose.scale(5.5f / 16f, 5.5f / 16f, 5.5f / 16f);
            com.golemlogix.client.render.BerBuffers.block(buffers, calc, pose, light);
            pose.popPose();
        }

        // snowman crews on the shelves assemble what the cores are making; the foreman is below when the coprocessors help
        long seed = be.getBlockPos().asLong();
        for (int shelf = 0; shelf < 2; shelf++) {
            float floor = shelf == 0 ? 3.2f : 16.2f;
            pose.pushPose();
            pose.translate(0f, 0f, 5f / 16f);
            float appear = spin * spin * (3 - 2 * spin);
            Vector3f proj = new Vector3f(0.5f, floor / 16f + 0.5f + Mth.sin(time * 0.1f + shelf) * 0.02f, 0.45f);
            float shelfWork = (shelf == 0 || be.clientBusy() > 1 || !be.clientDisplayItem(shelf).isEmpty()) ? spin : 0f;
            SnowCrew.render(pose, buffers, light, time, seed + shelf * 7919L, floor / 16f, shelfWork,
                    shelf == 0 ? foreman : 0f, proj);
            ItemStack current = be.clientDisplayItem(shelf);
            if (!current.isEmpty() && appear > 0.005f) {
                float size = (current.getItem() instanceof BlockItem ? 0.3f : 0.26f) * appear;
                pose.pushPose();
                pose.translate(proj.x, proj.y, proj.z);
                pose.mulPose(Axis.YP.rotationDegrees(time * 3f + shelf * 90f));
                pose.scale(size, size, size);
                com.golemlogix.client.render.BerBuffers.item(buffers, current, ItemDisplayContext.FIXED, LightCoordsUtil.FULL_BRIGHT, pose, level, 0);
                pose.popPose();
            }
            pose.popPose();
        }

        // the atlants are built into the cabinet: torsos come out of the sides, one arm wraps the front corner and holds the post, the other
        // rests on the roof; heads at the top corners follow the player; at the bottom frosted pipes wind out of the housing
        shelfGolems(be, pose, buffers, light, time, spin);
        for (int k = 0; k < 2; k++) {
            embeddedAtlant(be, pose, buffers, light, partialTick, k, time, spin);
        }
        vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        p = pose.last();
        // frieze above the doors: a row of teal lights that run while the cabinet works and flicker quietly when idle
        box(vc, p, -16, 32, -0.5f, 32, 34, 3, BRONZE, white, light);
        for (int k = 0; k < 14; k++) {
            float lx = -12.6f + k * 3f;
            float glow = spin > 0.01f ? (((int) (time / 3) - k) % 14 + 14) % 14 < 4 ? 1f : 0.35f : 0.45f + 0.15f * Mth.sin(time * 0.05f + k);
            int c = 0xFF000000 | ((int) (0x30 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
            box(vc, p, lx, 32.5f, -0.9f, lx + 1.4f, 33.6f, -0.5f, PANEL, c, LightCoordsUtil.FULL_BRIGHT);
        }
        // crest above the cabinet: pressure gauge and red valve wheel (the copper golem on the roof turns it)
        box(vc, p, -16.5f, 34, -1.5f, 32.5f, 37.4f, 16, BRASS, white, light);
        box(vc, p, -15.5f, 37.4f, -0.8f, 31.5f, 38.4f, 15, BRONZE, white, light);
        box(vc, p, -15, 34.6f, -1.8f, 31, 36.8f, -1.5f, BRONZE, white, light);
        box(vc, p, -13.2f, 34.2f, -2.4f, -7.8f, 37.8f, -1.8f, BRASS, white, light);
        fitWall(vc, p, -13f, 34.4f, -8f, 37.6f, -2.45f, GAUGE, white, light);
        valve(pose, vc, 24.5f, 36f, -2.6f, 3.4f, time * (spin > 0.01f ? 2.5f : 0.3f), light);
        roofGolem(be, pose, buffers, light, time, spin);
        vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        p = pose.last();        // copper pipes over the roof and down the back wall
        box(vc, p, -15, 32, 20, 31, 33.6f, 21.6f, COPPER, white, light);
        box(vc, p, -15, 32, 25, 31, 33.6f, 26.6f, COPPER, white, light);
        box(vc, p, 27, 4, 32, 28.6f, 33.6f, 33.6f, COPPER, white, light);
        box(vc, p, -12.6f, 4, 32, -11, 33.6f, 33.6f, COPPER, white, light);

        // doors: leaves hinged at the side posts, swing outwards
        float open = doors * doors * (3 - 2 * doors) * 95f;
        door(pose, buffers, -14f, 8f, open, light);
        door(pose, buffers, 30f, 8f, -open, light);
        pose.popPose();
    }

    private static final Identifier ATLANT_BODY = tex("atlant_body"), ATLANT_LIMB = tex("atlant_limb"),
            ATLANT_BELT = tex("atlant_belt"), CASING = tex("atlas_casing"), FROST_PIPE = tex("resonator_pipe"),
            ICE = tex("sat_ice");

    /**
     * Atlant built into a side (k = 0 left, 1 right): a copper housing at its foot with frosted pipes, the torso comes out
     * of the cabinet wall, the outer arm goes down and wraps the front corner (palm on the brass post), the inner one is raised
     * and rests on the roof crest, the head at the top corner turns to the player. Coordinates of the left one are in pixels; the right one is
     * mirrored around the middle of the cabinet (x = 8).
     */
    private static void embeddedAtlant(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float pt, int k,
                                       float time, float spin) {
        boolean right = k == 1;
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        int white = 0xFFFFFFFF;
        // pedestal housing at the side and the pipes out of it
        mbox(vc, pose, right, -25, 0, 0.5f, -16, 11, 15.5f, CASING, white, light);
        mbox(vc, pose, right, -25.5f, 10.5f, 0, -15.5f, 12, 16, BRASS, white, light);
        for (float z : new float[]{3.2f, 10.0f}) {
            mbox(vc, pose, right, -28.5f, 2.5f, z, -24.5f, 5.5f, z + 2.8f, FROST_PIPE, white, light);
            mbox(vc, pose, right, -29.5f, 2.2f, z - 0.3f, -26.5f, 9.5f, z + 3.1f, FROST_PIPE, white, light);
            mbox(vc, pose, right, -29.8f, 6.4f, z - 0.6f, -26.2f, 7.2f, z + 3.4f, ICE, white, light);
            mbox(vc, pose, right, -28.4f, 0.6f, z + 1.0f, -27.8f, 2.2f, z + 1.6f, ICE, white, light);
        }
        // torso: adjoins the cabinet wall (X <= -16), belt and shoulder plates
        float breathe = 0.25f * Mth.sin(time * 0.05f + k * 2f);
        mbox(vc, pose, right, -26, 12, 2.5f, -16f, 26 + breathe, 13.5f, ATLANT_BODY, white, light);
        mbox(vc, pose, right, -26.5f, 11.5f, 2, -16f, 13.5f, 14, ATLANT_BELT, white, light);
        mbox(vc, pose, right, -29, 22 + breathe, 1.5f, -24.5f, 27 + breathe, 14.5f, ATLANT_BELT, white, light);
        mbox(vc, pose, right, -26.5f, 25.5f + breathe, 2.5f, -16f, 27.5f + breathe, 13.5f, ATLANT_BELT, white, light);
        // outer arm: shoulder -> elbow at the front corner -> palm on the front post
        float grip = spin > 0.01f ? 0.4f * Mth.sin(time * 0.3f + k) : 0f;
        Vector3f shoulder = mv(right, -27.5f, 24.5f + breathe, 6), elbow = mv(right, -28.5f, 16f, -1.5f),
                hand = mv(right, -17.5f, 17.5f + grip, -1.8f);
        limb(vc, pose, shoulder, elbow, 4.2f, ATLANT_LIMB, light);
        limb(vc, pose, elbow, hand, 3.6f, ATLANT_LIMB, light);
        mbox(vc, pose, right, -18.5f, 15.5f + grip, -3.0f, -16.0f, 20 + grip, -0.6f, ATLANT_BELT, white, light);
        // inner arm: raised, the palm rests on the roof cornice at the corner
        Vector3f s2 = mv(right, -22, 26 + breathe, 12), e2 = mv(right, -23.5f, 33.5f, 11), h2 = mv(right, -16.8f, 37.2f, 8);
        limb(vc, pose, s2, e2, 3.8f, ATLANT_LIMB, light);
        limb(vc, pose, e2, h2, 3.4f, ATLANT_LIMB, light);
        mbox(vc, pose, right, -18.0f, 36.8f, 5.6f, -16.0f, 38.2f, 10.4f, ATLANT_BELT, white, light);
        // head at the top corner: the atlant head model (bigger), turns to the player
        float yaw = be.headsPrev[k][0] + Mth.wrapDegrees(be.heads[k][0] - be.headsPrev[k][0]) * pt;
        float pitch = Mth.lerp(pt, be.headsPrev[k][1], be.heads[k][1]);
        Vector3f neck = mv(right, -21.5f, 27.5f + breathe, 8);
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(neck.x / 16f, neck.y / 16f, neck.z / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + (right ? -12f : 12f)));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.scale(0.95f, 0.95f, 0.95f);
        pose.translate(-0.5f, -6f / 16f, -0.5f);
        com.golemlogix.client.render.StandaloneModels.render(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), AtlantHeadRenderer.HEAD, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** A point of the left atlant, or its mirror for the right one (pixels). */
    static Vector3f mv(boolean mirror, float x, float y, float z) {
        return new Vector3f(mirror ? 16f - x : x, y, z);
    }

    /** A box of the left atlant, or its mirror for the right one. */
    static void mbox(VertexConsumer vc, PoseStack pose, boolean mirror, float x0, float y0, float z0, float x1, float y1, float z1,
                             Identifier tex, int color, int light) {
        float a = mirror ? 16f - x1 : x0, b = mirror ? 16f - x0 : x1;
        box(vc, pose.last(), a, y0, z0, b, y1, z1, tex, color, light);
    }

    /** A limb: a bar of thickness t from point from to point to (pixels). */
    static void limb(VertexConsumer vc, PoseStack pose, Vector3f from, Vector3f to, float t, Identifier tex, int light) {
        Vector3f d = new Vector3f(to).sub(from);
        float len = d.length();
        if (len < 1e-3f) {
            return;
        }
        d.normalize();
        pose.pushPose();
        pose.translate(from.x / 16f, from.y / 16f, from.z / 16f);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(0f, 1f, 0f, d.x, d.y, d.z));
        box(vc, pose.last(), -t / 2f, -t / 4f, -t / 2f, t / 2f, len + t / 4f, t / 2f, tex, 0xFFFFFFFF, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------------ copper golems
    /** A thing in the golem's hand: a small item turned with the palm. */
    private static Consumer<PoseStack> held(ItemStack stack, float scale, float rotX, int light, MultiBufferSource buffers) {
        return hp -> {
            hp.scale(scale, scale, scale);
            if (rotX != 0f) {
                hp.mulPose(Axis.XP.rotationDegrees(rotX));
            }
            com.golemlogix.client.render.BerBuffers.item(buffers, stack, ItemDisplayContext.FIXED, light, hp, null, 0);
        };
    }

    /** The golem's pose at this moment: where it stands (blocks), where it looks, animation, arms, what it holds in front of its chest. */
    private record GolemPose(Vector3f at, Vector3f forward, CopperGolemPuppet.Anim anim, CopperGolemPuppet.Arms arms,
                             Consumer<PoseStack> chest) {
    }

    /**
     * The calculators stand at the back of each shelf from z = 22.5 (pixels). A golem facing them stands at FRONT at most: with its arms
     * forward and a bow it reaches about 2.5 px, so it never enters a calculator. The back wall behind them is only for empty shelves.
     */
    private static final float FRONT = 19.6f;

    /** A point on the shelf floor (x, z in pixels) in blocks. */
    private static Vector3f spot(float x, float floor, float z) {
        return new Vector3f(x / 16f, floor, z / 16f);
    }

    /** Moving between two shelf points over the fraction t (0..1): walks looking ahead. */
    private static GolemPose walk(float t, float x0, float z0, float x1, float z1, float floor, float time,
                                  @org.jetbrains.annotations.Nullable Consumer<PoseStack> inHand) {
        float e = t * t * (3f - 2f * t);
        Vector3f at = spot(Mth.lerp(e, x0, x1), floor, Mth.lerp(e, z0, z1));
        Vector3f fwd = new Vector3f(x1 - x0, 0f, z1 - z0);
        if (fwd.lengthSquared() < 1e-4f) {
            fwd.set(0f, 0f, -1f);
        }
        return new GolemPose(at, fwd.normalize(), new CopperGolemPuppet.Anim(time * 0.5f, true, false, 0f),
                new CopperGolemPuppet.Arms(-0.35f, 0.08f, -0.35f, -0.08f, 0f, inHand, null), null);
    }

    /**
     * The golem is drawn with smoothed position and rotation: when the cabinet goes from idle to work (and back), the scene
     * changes, but the golem does not jump; it walks to the new point (and moves its legs while catching up).
     */
    private static void renderSmooth(CryoMainframeBlockEntity be, int idx, PoseStack pose, MultiBufferSource buffers, int light, float time,
                                     GolemPose g, float scale, int variant) {
        float[] s = be.golemSmooth[idx];
        float tyaw = (float) Math.atan2(g.forward().x, g.forward().z);
        if (s[5] == 0f || time < s[4] || time - s[4] > 40f) {
            s[0] = g.at().x;
            s[1] = g.at().y;
            s[2] = g.at().z;
            s[3] = tyaw;
            s[5] = 1f;
        } else {
            float a = 1f - (float) Math.exp(-(time - s[4]) * 0.2f);
            s[0] += (g.at().x - s[0]) * a;
            s[1] += (g.at().y - s[1]) * a;
            s[2] += (g.at().z - s[2]) * a;
            float dyaw = Mth.wrapDegrees((float) Math.toDegrees(tyaw - s[3]));
            s[3] += (float) Math.toRadians(dyaw) * a;
        }
        s[4] = time;
        float lag = (float) Math.hypot(g.at().x - s[0], g.at().z - s[2]);
        CopperGolemPuppet.Anim anim = g.anim();
        if (lag > 0.03f && !anim.walking()) {
            anim = new CopperGolemPuppet.Anim(time * 0.5f, true, anim.carrying(), anim.bow());
        }
        Vector3f fwd = new Vector3f(Mth.sin(s[3]), 0f, Mth.cos(s[3]));
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(s[0], s[1], s[2]), fwd, new Vector3f(0f, 1f, 0f), scale, anim,
                variant, g.chest(), g.arms());
    }

    /**
     * Copper golem on the roof: while the cabinet works it turns the valve wheel; when idle it walks around the crest, taps the
     * gauge, wipes the wheel with a rag, stretches and looks around.
     */
    private static void roofGolem(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = 38.4f / 16f;
        GolemPose g;
        if (spin > 0.3f) {
            float sway = Mth.sin(time * 0.35f) * 0.25f;
            g = new GolemPose(spot(24.5f, floor, 3.2f), new Vector3f(Mth.sin(sway) * 0.3f, 0f, -1f),
                    new CopperGolemPuppet.Anim(time * 0.3f, false, true, 0.25f + 0.1f * Mth.sin(time * 0.7f)), null, hp -> { });
        } else {
            ItemStack rag = new ItemStack(net.minecraft.world.item.Items.PAPER);
            float cyc = ((time + be.getBlockPos().asLong() % 97) / 520f) % 1f;
            if (cyc < 0.26f) {
                // at the gauge: leans over and taps the glass with a finger
                float tap = Math.max(0f, Mth.sin(time * 0.9f));
                g = new GolemPose(spot(-10.5f, floor, 3.4f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.28f),
                        new CopperGolemPuppet.Arms(-1.25f - 0.12f * tap, 0.1f, -0.2f, -0.1f, 0.15f * Mth.sin(time * 0.05f), null, null), null);
            } else if (cyc < 0.36f) {
                g = walk((cyc - 0.26f) / 0.10f, -10.5f, 3.4f, 24.5f, 3.2f, floor, time, null);
            } else if (cyc < 0.68f) {
                // wipes the wheel: the hand moves in a circle
                float a = time * 0.35f;
                g = new GolemPose(spot(24.5f, floor, 3.2f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f),
                        new CopperGolemPuppet.Arms(-1.35f + 0.22f * Mth.sin(a), 0.25f + 0.2f * Mth.cos(a), -0.3f, -0.1f, 0f,
                                held(rag, 0.3f, 0f, light, buffers), null), null);
            } else if (cyc < 0.78f) {
                g = walk((cyc - 0.68f) / 0.10f, 24.5f, 3.2f, 6.5f, 8f, floor, time, null);
            } else {
                // stretches and looks around
                float t = (cyc - 0.78f) / 0.22f;
                float up = Mth.clamp(Mth.sin(t * Mth.PI) * 1.4f, 0f, 1f);
                g = new GolemPose(spot(6.5f, floor, 8f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                        new CopperGolemPuppet.Arms(-2.9f * up, 0.3f * up, -2.9f * up, -0.3f * up, 0.6f * Mth.sin(time * 0.06f), null, null), null);
            }
        }
        renderSmooth(be, 2, pose, buffers, light, time, g, 0.3f, 1);
    }

    /**
     * Copper golems on the cabinet shelves.
     * Shelf 0 is the chief cryo engineer: at work it calibrates a calculator with a wrench, turns the manifold valve and checks a
     * punch card; when idle it wipes the door glass, oils the calculators from an oil can, sweeps frost with a brush and reads the
     * shift log.
     * Shelf 1 is the coprocessor operator: at work it carries a compressed coprocessor to the assemblers and feeds punch cards into the reader;
     * when idle it polishes the glowing matrix, sorts punch cards into stacks, winds the chronometer and stretches.
     */
    private static void shelfGolems(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float spin) {
        renderSmooth(be, 0, pose, buffers, light, time, shelf0Pose(be, buffers, light, time, spin), 0.24f, 1);
        renderSmooth(be, 1, pose, buffers, light, time, shelf1Pose(be, buffers, light, time, spin), 0.24f, 2);
    }

    private static GolemPose shelf0Pose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = 3.2f / 16f;
        Consumer<PoseStack> card = held(new ItemStack(ModItems.COPPER_PUNCH_CARD.get()), 0.4f, 0f, light, buffers);
        Consumer<PoseStack> wrench = held(new ItemStack(net.minecraft.world.item.Items.LEVER), 0.35f, 90f, light, buffers);
        if (spin <= 0.05f) {
            float cyc = ((time + be.getBlockPos().asLong() % 131) / 720f) % 1f;
            if (cyc < 0.20f) {
                // wipes the door glass from inside: the hand with a rag draws circles
                float a = time * 0.4f;
                return new GolemPose(spot(-9f, floor, 5.5f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.04f),
                        new CopperGolemPuppet.Arms(-1.75f + 0.25f * Mth.sin(a), 0.3f + 0.25f * Mth.cos(a), -0.25f, -0.1f, 0.1f * Mth.sin(time * 0.05f),
                                held(new ItemStack(net.minecraft.world.item.Items.PAPER), 0.3f, 0f, light, buffers), null), null);
            }
            if (cyc < 0.26f) {
                return walk((cyc - 0.20f) / 0.06f, -9f, 5.5f, -5.3f, 18.5f, floor, time, null);
            }
            if (cyc < 0.46f) {
                // oils a calculator: leans over, the oil can in its hand "pumps"
                float pump = Mth.sin(time * 0.8f);
                return new GolemPose(spot(-5.3f, floor, 18.5f), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.32f),
                        new CopperGolemPuppet.Arms(-0.95f + 0.12f * pump, 0.12f, -0.5f, -0.12f, 0.1f * pump,
                                held(new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE), 0.32f, 0f, light, buffers), null), null);
            }
            if (cyc < 0.52f) {
                return walk((cyc - 0.46f) / 0.06f, -5.3f, 18.5f, 12f, 17f, floor, time, null);
            }
            if (cyc < 0.74f) {
                // sweeps frost with a brush: walks slowly along the shelf, the hands sweep
                float t = (cyc - 0.52f) / 0.22f;
                float x = 12f + 9f * (0.5f - 0.5f * Mth.cos(t * Mth.PI * 2f));
                float dir = Mth.sin(t * Mth.PI * 2f) >= 0f ? 1f : -1f;
                float sweep = Mth.sin(time * 0.6f);
                return new GolemPose(spot(x, floor, 17f), new Vector3f(dir, 0f, 0.15f), new CopperGolemPuppet.Anim(time * 0.25f, true, false, 0.22f),
                        new CopperGolemPuppet.Arms(-0.7f + 0.25f * sweep, 0.1f, -0.7f + 0.25f * sweep, -0.1f, 0f,
                                held(new ItemStack(net.minecraft.world.item.Items.BRUSH), 0.34f, 90f, light, buffers), null), null);
            }
            if (cyc < 0.80f) {
                return walk((cyc - 0.74f) / 0.06f, 12f, 17f, 2f, 17.5f, floor, time, null);
            }
            // reads the shift log: a book in its hands, head bowed, turns a page now and then
            float flip = Math.max(0f, Mth.sin(time * 0.05f) - 0.9f) * 10f;
            return new GolemPose(spot(2f, floor, 17.5f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f),
                    new CopperGolemPuppet.Arms(-0.95f - 0.5f * flip, 0.18f, -0.95f, -0.18f, 0.12f * Mth.sin(time * 0.02f),
                            held(new ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK), 0.34f, 0f, light, buffers), null), null);
        }

        // work: calculator calibration, manifold valve, punch card check
        float cycle = ((time / 160f) + 0.12f) % 1.0f;
        if (cycle < 0.40f) {
            float ratchet = Mth.sin(time * 0.75f);
            return new GolemPose(spot(-5.3f, floor, FRONT), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.3f, false, false, 0.26f),
                    new CopperGolemPuppet.Arms(-1.15f + 0.28f * ratchet, 0.12f, -0.65f, -0.1f, 0.15f * Mth.sin(time * 0.3f), wrench, null), null);
        }
        if (cycle < 0.48f) {
            return walk((cycle - 0.40f) / 0.08f, -5.3f, FRONT, -12.2f, 19.5f, floor, time, wrench);
        }
        if (cycle < 0.72f) {
            float valvePump = Mth.sin(time * 0.45f);
            return new GolemPose(spot(-12.2f, floor, 19.5f), new Vector3f(-0.8f, 0f, 0.6f),
                    new CopperGolemPuppet.Anim(time * 0.4f, false, false, 0.16f + 0.10f * valvePump),
                    new CopperGolemPuppet.Arms(-1.3f + 0.15f * valvePump, 0.22f, -1.3f - 0.15f * valvePump, -0.22f, 0f, null, null), null);
        }
        if (cycle < 0.80f) {
            return walk((cycle - 0.72f) / 0.08f, -12.2f, 19.5f, 2.0f, 18.0f, floor, time, null);
        }
        return new GolemPose(spot(2.0f, floor, 18.0f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.2f, false, true, 0.08f),
                new CopperGolemPuppet.Arms(-0.95f, 0.14f, -0.95f, -0.14f, 0.18f * Mth.sin(time * 0.1f), null, null), card);
    }

    private static GolemPose shelf1Pose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = 16.2f / 16f;
        Consumer<PoseStack> coprocessor = held(new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()), 0.38f, 0f, light, buffers);
        Consumer<PoseStack> card = held(new ItemStack(ModItems.COPPER_PUNCH_CARD.get()), 0.35f, 90f, light, buffers);
        // calculators on this shelf (slots 7..12): then the golem keeps in front of them instead of going to the back wall
        boolean shelfFilled = be.clientCalcs() > CryoMainframeBlockEntity.CALC_SLOTS / 2;
        float polishZ = shelfFilled ? FRONT : 25.5f, sortZ = shelfFilled ? FRONT : 23.5f;
        if (spin <= 0.05f) {
            float cyc = ((time + be.getBlockPos().asLong() % 113) / 760f + 0.5f) % 1f;
            if (cyc < 0.22f) {
                // polishes the glowing matrix on the back wall (or the calculators in front of it): a rag moves from side to side
                float a = time * 0.3f;
                return new GolemPose(spot(-2f, floor, polishZ), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                        new CopperGolemPuppet.Arms((shelfFilled ? -1.45f : -2.35f) + 0.1f * Mth.cos(a), 0.45f * Mth.sin(a), -0.3f, -0.1f,
                                0.2f * Mth.sin(a), held(new ItemStack(net.minecraft.world.item.Items.PAPER), 0.3f, 0f, light, buffers), null), null);
            }
            if (cyc < 0.28f) {
                return walk((cyc - 0.22f) / 0.06f, -2f, polishZ, 20f, sortZ, floor, time, null);
            }
            if (cyc < 0.50f) {
                // sorts punch cards into stacks: reaches out with the right hand, then the left
                float sw = Mth.sin(time * 0.3f);
                return new GolemPose(spot(20f, floor, sortZ), new Vector3f(0.6f, 0f, 0.8f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.2f),
                        new CopperGolemPuppet.Arms(-0.8f - 0.35f * Math.max(0f, sw), 0.15f, -0.8f - 0.35f * Math.max(0f, -sw), -0.15f, 0.3f * sw,
                                sw > 0f ? card : null, sw <= 0f ? card : null), null);
            }
            if (cyc < 0.56f) {
                return walk((cyc - 0.50f) / 0.06f, 20f, sortZ, 8f, 18f, floor, time, null);
            }
            if (cyc < 0.78f) {
                // winds the chronometer: the watch in the left hand at the chest, the right one turns the crown
                float a = time * 0.5f;
                return new GolemPose(spot(8f, floor, 18f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.1f),
                        new CopperGolemPuppet.Arms(-1.05f + 0.1f * Mth.sin(a), 0.35f + 0.1f * Mth.cos(a), -1.05f, -0.2f, -0.15f, null,
                                held(new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()), 0.32f, 90f, light, buffers)), null);
            }
            if (cyc < 0.84f) {
                return walk((cyc - 0.78f) / 0.06f, 8f, 18f, -9f, 19f, floor, time, null);
            }
            // stretches and looks over the matrix
            float t = (cyc - 0.84f) / 0.16f;
            float up = Mth.clamp(Mth.sin(t * Mth.PI) * 1.4f, 0f, 1f);
            return new GolemPose(spot(-9f, floor, 19f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                    new CopperGolemPuppet.Arms(-2.9f * up, 0.3f * up, -2.9f * up, -0.3f * up, 0.5f * Mth.sin(time * 0.05f), null, null), null);
        }

        // work: a coprocessor to the assemblers, the handover, a punch card into the reader
        float cycle = ((time / 160f) + 0.62f) % 1.0f;
        if (cycle < 0.42f) {
            float t = cycle / 0.42f;
            return new GolemPose(spot(Mth.lerp(t, 22.0f, 6.5f), floor, 18.0f), new Vector3f(-1f, 0f, 0f),
                    new CopperGolemPuppet.Anim(time * 0.5f, true, true, 0.1f), null, coprocessor);
        }
        if (cycle < 0.65f) {
            float lift = Mth.clamp((cycle - 0.42f) / 0.06f, 0f, 1f);
            float rX = Mth.lerp(lift, -1.25f, -1.55f);
            return new GolemPose(spot(6.5f, floor, 17.0f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.3f, false, true, 0f),
                    new CopperGolemPuppet.Arms(rX, 0.2f, rX, -0.2f, 0f, null, null), coprocessor);
        }
        if (cycle < 0.73f) {
            return walk((cycle - 0.65f) / 0.08f, 6.5f, 17.0f, 16.3f, FRONT, floor, time, card);
        }
        float slide = 0.5f + 0.5f * Mth.sin(time * 0.7f);
        return new GolemPose(spot(16.3f, floor, FRONT), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.3f, false, false, 0.15f),
                new CopperGolemPuppet.Arms(-1.3f + 0.25f * slide, 0.08f, -0.6f, -0.15f, 0f, card, null), null);
    }

    /** Door leaf: the hinge at x = hinge, the free edge at x = edge; angle = rotation around the hinge (degrees). */
    private static void door(PoseStack pose, MultiBufferSource buffers, float hinge, float edge, float angle, int light) {
        float w = edge - hinge;
        pose.pushPose();
        pose.translate(hinge / 16f, 0f, 1f / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        float x0 = Math.min(0, w), x1 = Math.max(0, w);
        // door leaf frame
        box(vc, p, x0, 3, -0.6f, x1, 4.4f, 0.6f, BRASS, white, light);
        box(vc, p, x0, 26.6f, -0.6f, x1, 28, 0.6f, BRASS, white, light);
        box(vc, p, x0, 4.4f, -0.6f, x0 + 1.4f, 26.6f, 0.6f, BRASS, white, light);
        box(vc, p, x1 - 1.4f, 4.4f, -0.6f, x1, 26.6f, 0.6f, BRASS, white, light);
        box(vc, p, x0 + 1.4f, 15f, -0.4f, x1 - 1.4f, 16f, 0.4f, BRASS, white, light);
        // handle at the free edge
        float hx = w > 0 ? x1 - 3.2f : x0 + 2.2f;
        box(vc, p, hx, 13f, -1.6f, hx + 1f, 18f, -0.6f, BRASS, 0xFFFFE6A0, light);
        // glass
        VertexConsumer glass = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose gp = pose.last();
        wall(glass, gp, x0 + 1.4f, 4.4f, x1 - 1.4f, 15f, 0f, GLASS, 0xB0DDF6FF, light);
        wall(glass, gp, x0 + 1.4f, 16f, x1 - 1.4f, 26.6f, 0f, GLASS, 0xB0DDF6FF, light);
        pose.popPose();
    }

    /** Red valve wheel: a rim of segments, four spokes and a hub; it turns while the cabinet works. */
    static void valve(PoseStack pose, VertexConsumer vc, float cx, float cy, float z, float r, float rot, int light) {
        int red = 0xFFC8432F, hub = 0xFFFFE6A0;
        pose.pushPose();
        pose.translate(cx / 16f, cy / 16f, z / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(rot));
        for (int k = 0; k < 12; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 30f));
            box(vc, pose.last(), -0.95f, r - 0.5f, -0.5f, 0.95f, r + 0.5f, 0.5f, BRASS, red, light);
            pose.popPose();
        }
        for (int k = 0; k < 4; k++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(k * 90f + 45f));
            box(vc, pose.last(), -0.3f, 0f, -0.3f, 0.3f, r - 0.4f, 0.3f, BRASS, red, light);
            pose.popPose();
        }
        box(vc, pose.last(), -0.9f, -0.9f, -0.8f, 0.9f, 0.9f, 0.6f, BRASS, hub, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------ quads
    static TextureAtlasSprite sprite(Identifier id) {
        return com.golemlogix.client.render.Sprites.block(id);
    }

    /** A flat wall in the z plane (facing -Z), with the texture tiled every 16 pixels. */
    static void wall(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
                             Identifier tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        for (float x = x0; x < x1 - 1e-3f; x += 16f) {
            float xe = Math.min(x1, x + 16f);
            for (float y = y0; y < y1 - 1e-3f; y += 16f) {
                float ye = Math.min(y1, y + 16f);
                float u0 = s.getU((x - x0) % 16f / 16f), u1 = s.getU(((x - x0) % 16f + (xe - x)) / 16f);
                float v0 = s.getV(1f - ((y - y0) % 16f + (ye - y)) / 16f), v1 = s.getV(1f - (y - y0) % 16f / 16f);
                quad(vc, p, new float[]{xe, y, z}, new float[]{x, y, z}, new float[]{x, ye, z}, new float[]{xe, ye, z},
                        u0, v0, u1, v1, color, light, 0, 0, -1);
            }
        }
    }

    /** A wall facing -Z with the whole texture squeezed to its size (dials, plates). */
    static void fitWall(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
                                Identifier tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        quad(vc, p, new float[]{x1, y0, z}, new float[]{x0, y0, z}, new float[]{x0, y1, z}, new float[]{x1, y1, z},
                s.getU0(), s.getV0(), s.getU1(), s.getV1(), color, light, 0, 0, -1);
    }

    /** A box (pixels); every face takes a piece of the texture by its own size (over 16 it tiles). */
    static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                            Identifier tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        // north (-Z) and south (+Z)
        tile(vc, p, s, color, light, x1 - x0, y1 - y0, (a, b) -> new float[]{x1 - a, y0 + b, z0}, 0, 0, -1);
        tile(vc, p, s, color, light, x1 - x0, y1 - y0, (a, b) -> new float[]{x0 + a, y0 + b, z1}, 0, 0, 1);
        // west (-X) and east (+X)
        tile(vc, p, s, color, light, z1 - z0, y1 - y0, (a, b) -> new float[]{x0, y0 + b, z0 + a}, -1, 0, 0);
        tile(vc, p, s, color, light, z1 - z0, y1 - y0, (a, b) -> new float[]{x1, y0 + b, z1 - a}, 1, 0, 0);
        // top and bottom
        tile(vc, p, s, color, light, x1 - x0, z1 - z0, (a, b) -> new float[]{x0 + a, y1, z1 - b}, 0, 1, 0);
        tile(vc, p, s, color, light, x1 - x0, z1 - z0, (a, b) -> new float[]{x0 + a, y0, z0 + b}, 0, -1, 0);
    }

    private interface Map2 {
        float[] at(float a, float b);
    }

    /** A w x h face (pixels) in tiles of 16: at(a, b) is a point of the face, a to the right, b up (as seen from outside). */
    private static void tile(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int color, int light, float w, float h, Map2 at,
                             float nx, float ny, float nz) {
        for (float a = 0; a < w - 1e-3f; a += 16f) {
            float ae = Math.min(w, a + 16f);
            for (float b = 0; b < h - 1e-3f; b += 16f) {
                float be = Math.min(h, b + 16f);
                float u0 = s.getU(0f), u1 = s.getU((ae - a) / 16f);
                float v0 = s.getV(1f - (be - b) / 16f), v1 = s.getV(1f);
                quad(vc, p, at.at(a, b), at.at(ae, b), at.at(ae, be), at.at(a, be), u0, v0, u1, v1, color, light, nx, ny, nz);
            }
        }
    }

    /** a = bottom left, b = bottom right, c = top right, d = top left (from outside); coordinates in pixels. */
    static void quad(VertexConsumer vc, PoseStack.Pose p, float[] a, float[] b, float[] c, float[] d,
                             float u0, float v0, float u1, float v1, int color, int light, float nx, float ny, float nz) {
        vertex(vc, p, a, u0, v1, color, light, nx, ny, nz);
        vertex(vc, p, b, u1, v1, color, light, nx, ny, nz);
        vertex(vc, p, c, u1, v0, color, light, nx, ny, nz);
        vertex(vc, p, d, u0, v0, color, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float[] v, float u, float w, int color, int light,
                               float nx, float ny, float nz) {
        vc.addVertex(p, v[0] / 16f, v[1] / 16f, v[2] / 16f).setColor(color).setUv(u, w).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, nx, ny, nz);
    }
}
