package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.TelegraphBlockEntity;
import com.golemlogix.blocks.TelegraphBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.CryoMainframeRenderer.box;
import static com.golemlogix.client.CryoMainframeRenderer.fitWall;
import static com.golemlogix.client.CryoMainframeRenderer.limb;

/**
 * The whole Cryo-Source Telegraph, drawn from its main cell. A rusty copper base with frost on its edges, gauges and a rune panel;
 * on the back right a mast: a crystal in a brass cage with copper coils and vacuum tubes, a lattice tower with two small crystals
 * and an iced dish on top. On the front left a copper golem operator keys a Morse message ("GOLEM") on a brass key; the desk lamp
 * lights with every press and a pulse of blue light and mana runs up the mast. At the start of each message the incoming Source
 * strikes the top crystal: it flashes and hums. Everything moves only on the client, from the game time.
 * <p>
 * Pixels in the main cell's north-facing frame: the front is Z 0, "right" (seen from the front) is -X, so the base spans X -16..16,
 * Z 0..32; the mast stands on X -16..0, Z 16..32.
 */
public class TelegraphRenderer extends com.golemlogix.client.render.LegacyBer<TelegraphBlockEntity> {
    private static final Identifier RUST = mc("exposed_copper"), VERDIGRIS = mc("weathered_copper"),
            CUT = mc("exposed_cut_copper"), COPPER = mc("copper_block"), BRASS = tex("sat_brass"), DARK = tex("sat_dark"),
            ICE = tex("sat_ice"), GAUGE = tex("monitor_gauge"), DIAL = tex("resonator_dial"), RUNES = tex("cryo_panel_matrix"),
            GLASS = tex("sat_glass"), CRYSTAL = tex("core_crystal"), GEM = tex("sat_crystal"), WHITE = mc("white_concrete");
    /** Rusty tint over the vanilla exposed copper (on its own it reads pink). */
    private static final int RUST_TINT = 0xFFB07A62;
    /** Ticks a pulse needs to run from the base to the dish. */
    private static final float PULSE_TICKS = 16f;
    private static final float MAST_X = -8f, MAST_Z = 24f, MAST_BOTTOM = 16f, MAST_TOP = 41f;
    /** The key's knob, right under the operator's right hand when it presses (worked out from CopperGolemPuppet's arm). */
    private static final float KEY_X = 12.9f, KEY_Z = 9.5f;

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name);
    }

    private static Identifier mc(String name) {
        return Identifier.withDefaultNamespace("block/" + name);
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
        float time = level.getGameTime() + Math.floorMod(pos.asLong(), 97L) + partialTick;
        long tick = (long) Math.floor(time);
        boolean down = wake > 0.95f && TelegraphBlockEntity.keyDown(tick);
        float flash = TelegraphBlockEntity.crystalFlash(time) * wake;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        base(vc, pose, p, light, wake, time);
        mast(vc, pose, light, wake, flash, time);
        desk(vc, pose, light, down, wake);
        key(vc, pose, light, down);
        pulses(vc, pose.last(), tick, time, wake);

        // the operator: stands at the back of the front left cell facing the front, the right hand on the key
        float bob = down ? -0.02f : 0f;
        // working: the right hand hovers over the knob and comes down on it; idle: it hangs beside the key
        CopperGolemPuppet.Arms arms = wake > 0.05f
                ? new CopperGolemPuppet.Arms(down ? -0.66f : -0.95f, 0.05f, -0.25f, -0.1f, 0.12f * Mth.sin(time * 0.03f))
                : new CopperGolemPuppet.Arms(0.1f, 0.12f, -0.15f, -0.08f, 0.6f * Mth.sin(time * 0.02f));
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(9.5f / 16f, 15f / 16f, 11.5f / 16f), new Vector3f(0f, 0f, -1f),
                new Vector3f(0f, 1f, 0f), 0.58f, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f + bob), 1, null, arms);
        pose.popPose();
    }

    // ------------------------------------------------------------------ base

    private static void base(VertexConsumer vc, PoseStack pose, PoseStack.Pose p, int light, float wake, float time) {
        int white = 0xFFFFFFFF;
        box(vc, p, -16.3f, 0, -0.3f, 16.3f, 1.6f, 32.3f, DARK, white, light);
        box(vc, p, -16, 1.6f, 0, 16, 13, 32, RUST, RUST_TINT, light);
        box(vc, p, -15.6f, 13, 0.4f, 15.6f, 15, 31.6f, CUT, 0xFFA87460, light);
        // brass corner straps and a belt
        for (float[] c : new float[][]{{-16.5f, -0.5f}, {14.5f, -0.5f}, {-16.5f, 30.5f}, {14.5f, 30.5f}}) {
            box(vc, p, c[0], 0, c[1], c[0] + 2f, 15.4f, c[1] + 2f, BRASS, white, light);
        }
        box(vc, p, -16.2f, 9.5f, -0.2f, 16.2f, 10.5f, 32.2f, BRASS, 0xFFD8C090, light);
        // frost along the top edges and icicles under the front edge
        box(vc, p, -16.2f, 14.6f, -0.4f, 16.2f, 15.6f, 1.0f, ICE, white, light);
        box(vc, p, -16.2f, 14.6f, 31f, 16.2f, 15.6f, 32.4f, ICE, white, light);
        box(vc, p, -16.4f, 14.6f, -0.4f, -15f, 15.6f, 32.4f, ICE, white, light);
        box(vc, p, 15f, 14.6f, -0.4f, 16.4f, 15.6f, 32.4f, ICE, white, light);
        float[][] icicles = {{-13f, 2.4f}, {-9.5f, 1.4f}, {-4f, 3.2f}, {1.5f, 1.8f}, {6f, 2.8f}, {12f, 1.2f}};
        for (float[] i : icicles) {
            box(vc, p, i[0], 14.6f - i[1], -0.6f, i[0] + 0.8f, 14.6f, 0.2f, ICE, white, light);
            box(vc, p, i[0] + 0.2f, 14.6f - i[1] - 0.8f, -0.4f, i[0] + 0.6f, 14.6f - i[1], 0f, ICE, white, light);
        }
        // the front: two gauges and a gear dial on the left cell, a glowing rune panel on the right one
        fitWall(vc, p, 2f, 5f, 7f, 10f, -0.06f, GAUGE, white, light);
        fitWall(vc, p, 9f, 5f, 14f, 10f, -0.06f, GAUGE, white, light);
        box(vc, p, 4f, 2f, -0.8f, 6f, 3.6f, 0f, DARK, white, light);
        box(vc, p, 10.5f, 2f, -0.8f, 12.5f, 3.6f, 0f, BRASS, 0xFFE0C070, light);
        float glow = 0.35f + 0.65f * wake * (0.75f + 0.25f * Mth.sin(time * 0.15f));
        int rune = 0xFF000000 | ((int) (0x40 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xE8 * glow);
        fitWall(vc, p, -14f, 2.5f, -2f, 8.5f, -0.06f, RUNES, rune, wake > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);
        box(vc, p, -14.6f, 2f, -0.5f, -1.4f, 2.5f, 0f, BRASS, white, light);
        box(vc, p, -14.6f, 8.5f, -0.5f, -1.4f, 9f, 0f, BRASS, white, light);
        // gear dials on the right side face and on the back
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(90f));
        fitWall(vc, pose.last(), -14f, 3f, -6f, 11f, -16.06f, DIAL, white, light);
        fitWall(vc, pose.last(), -28f, 4f, -22f, 10f, -16.06f, GAUGE, white, light);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90f));
        fitWall(vc, pose.last(), 6f, 3f, 14f, 11f, -16.06f, DIAL, white, light);
        pose.popPose();
        // a copper cable from the key along the deck to the foot of the mast
        box(vc, p, KEY_X + 1f, 15f, 8.4f, KEY_X + 1.8f, 15.7f, 19.8f, COPPER, white, light);
        box(vc, p, MAST_X, 15f, 18.8f, KEY_X + 1f, 15.7f, 19.8f, COPPER, white, light);
    }

    // ------------------------------------------------------------------ mast

    private static void mast(VertexConsumer vc, PoseStack pose, int light, float wake, float flash, float time) {
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        float cx = MAST_X, cz = MAST_Z;
        // the crystal cage (second block)
        box(vc, p, cx - 7, 15f, cz - 7, cx + 7, 17f, cz + 7, DARK, white, light);
        for (float[] c : new float[][]{{-6.5f, -6.5f}, {4.5f, -6.5f}, {-6.5f, 4.5f}, {4.5f, 4.5f}}) {
            box(vc, p, cx + c[0], 17f, cz + c[1], cx + c[0] + 2f, 31f, cz + c[1] + 2f, BRASS, white, light);
        }
        // copper coils on the front posts
        for (float y = 19f; y < 30f; y += 1.6f) {
            box(vc, p, cx - 7.1f, y, cz - 7.1f, cx - 3.9f, y + 0.9f, cz - 3.9f, COPPER, white, light);
            box(vc, p, cx + 3.9f, y, cz - 7.1f, cx + 7.1f, y + 0.9f, cz - 3.9f, COPPER, white, light);
        }
        // vacuum tubes at the sides with a warm filament
        int fil = wake > 0.05f ? 0xFFFFB050 : 0xFF6A4020;
        for (float side : new float[]{-1f, 1f}) {
            float x0 = cx + side * 7.6f - 1.2f;
            box(vc, p, x0, 18.5f, cz - 1.2f, x0 + 2.4f, 28f, cz + 1.2f, GLASS, 0xFFC8F0FF, light);
            box(vc, p, x0 + 0.8f, 19f, cz - 0.4f, x0 + 1.6f, 26f, cz + 0.4f, WHITE, fil, wake > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);
            box(vc, p, x0 - 0.2f, 28f, cz - 1.4f, x0 + 2.6f, 29.5f, cz + 1.4f, BRASS, white, light);
        }
        // diagonal braces
        limb(vc, pose, new Vector3f(cx - 5.5f, 18f, cz - 5.5f), new Vector3f(cx + 5.5f, 30f, cz - 5.5f), 0.8f, BRASS, light);
        limb(vc, pose, new Vector3f(cx - 5.5f, 18f, cz + 5.5f), new Vector3f(cx + 5.5f, 30f, cz + 5.5f), 0.8f, BRASS, light);
        // the crystal: violet, glowing, it flashes white when the Source strikes it
        float b = Math.min(1f, 0.62f + 0.23f * wake + 0.4f * flash);
        int crystal = color(b, Math.min(1f, b * 0.9f + 0.25f * flash), b);
        pose.pushPose();
        pose.translate(cx / 16f, 24f / 16f, cz / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(45f + time * (0.4f + 2f * flash) * wake));
        // a gem: wide in the middle, narrowing to points above and below
        box(vc, pose.last(), -2.0f, -2.5f, -2.0f, 2.0f, 2.5f, 2.0f, GEM, crystal, LightCoordsUtil.FULL_BRIGHT);
        box(vc, pose.last(), -1.4f, -4.5f, -1.4f, 1.4f, 4.5f, 1.4f, GEM, crystal, LightCoordsUtil.FULL_BRIGHT);
        box(vc, pose.last(), -0.7f, -6.3f, -0.7f, 0.7f, 6.3f, 0.7f, GEM, crystal, LightCoordsUtil.FULL_BRIGHT);
        pose.popPose();
        box(vc, p, cx - 7.2f, 31f, cz - 7.2f, cx + 7.2f, 32.5f, cz + 7.2f, BRASS, white, light);
        box(vc, p, cx - 7.4f, 32.2f, cz - 7.4f, cx + 7.4f, 33f, cz + 7.4f, ICE, white, light);
        // the light track on the mast's front (the pulses run up it)
        box(vc, p, cx - 0.4f, MAST_BOTTOM, cz - 7.5f, cx + 0.4f, 31f, cz - 7.2f, DARK, 0xFF5A8898, light);

        // the lattice tower (third block) with two small crystals
        float top = MAST_TOP;
        for (float[] c : new float[][]{{-5f, -5f}, {5f, -5f}, {-5f, 5f}, {5f, 5f}}) {
            limb(vc, pose, new Vector3f(cx + c[0], 33f, cz + c[1]), new Vector3f(cx + c[0] * 0.45f, top, cz + c[1] * 0.45f), 1.1f, BRASS, light);
        }
        for (float y = 35f; y < top; y += 3f) {
            float r = 5f - (y - 33f) / (top - 33f) * 2.6f;
            box(vc, p, cx - r, y, cz - r, cx + r, y + 0.6f, cz - r + 0.6f, COPPER, white, light);
            box(vc, p, cx - r, y, cz + r - 0.6f, cx + r, y + 0.6f, cz + r, COPPER, white, light);
            box(vc, p, cx - r, y, cz - r, cx - r + 0.6f, y + 0.6f, cz + r, COPPER, white, light);
            box(vc, p, cx + r - 0.6f, y, cz - r, cx + r, y + 0.6f, cz + r, COPPER, white, light);
        }
        int small = color(Math.min(1f, 0.7f * b + 0.3f), Math.min(1f, 0.85f * b + 0.15f), 1f);
        box(vc, p, cx - 3f, 34.5f, cz - 1f, cx - 1f, 39f, cz + 1f, CRYSTAL, small, LightCoordsUtil.FULL_BRIGHT);
        box(vc, p, cx + 1f, 35.5f, cz - 1f, cx + 3f, 39.5f, cz + 1f, CRYSTAL, small, LightCoordsUtil.FULL_BRIGHT);

        // the dish on top: tilted to the front and slowly sweeping the sky while the telegraph works
        pose.pushPose();
        pose.translate(cx / 16f, (top + 0.5f) / 16f, cz / 16f);
        pose.mulPose(Axis.YP.rotationDegrees(25f * Mth.sin(time * 0.012f) * wake));
        pose.mulPose(Axis.XP.rotationDegrees(-30f));
        PoseStack.Pose d = pose.last();
        box(vc, d, -1.5f, -0.5f, -1.5f, 1.5f, 1f, 1.5f, DARK, white, light);
        for (int k = 0; k < 8; k++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(k * 45f));
            pose.mulPose(Axis.XP.rotationDegrees(-18f));
            box(vc, pose.last(), -2.4f, 0.4f, 1.2f, 2.4f, 1.2f, 7.2f, BRASS, 0xFFE6C88A, light);
            box(vc, pose.last(), -2.6f, 0.9f, 6.6f, 2.6f, 1.6f, 7.6f, ICE, white, light);
            // icicles under the rim
            if (k % 2 == 0) {
                box(vc, pose.last(), -0.5f, -1.8f, 6.6f, 0.5f, 0.4f, 7.4f, ICE, white, light);
            }
            pose.popPose();
        }
        // the feed horn: a brass spike with a glowing tip
        limb(vc, pose, new Vector3f(0f, 1f, 0f), new Vector3f(0f, 6.5f, 0f), 0.9f, BRASS, light);
        int tip = color(0.4f + 0.6f * flash, 0.85f, 1f);
        box(vc, pose.last(), -0.7f, 6.3f, -0.7f, 0.7f, 7.7f, 0.7f, WHITE, tip, wake > 0.05f ? LightCoordsUtil.FULL_BRIGHT : light);
        pose.popPose();
    }

    // ------------------------------------------------------------------ the desk: lamp and a ledger

    private static void desk(VertexConsumer vc, PoseStack pose, int light, boolean down, float wake) {
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        // a banker's lamp on the front right cell
        box(vc, p, -6.5f, 15f, 3f, -2.5f, 15.7f, 7f, BRASS, white, light);
        limb(vc, pose, new Vector3f(-4.5f, 15.6f, 6f), new Vector3f(-4.5f, 19.6f, 6f), 0.7f, BRASS, light);
        limb(vc, pose, new Vector3f(-4.5f, 19.6f, 6f), new Vector3f(-4.5f, 20.2f, 4.2f), 0.6f, BRASS, light);
        box(vc, p, -7.5f, 19.2f, 1.8f, -1.5f, 20.6f, 5.2f, VERDIGRIS, white, light);
        int bulb = down ? 0xFFFFE08A : (wake > 0.05f ? 0xFF8A7440 : 0xFF4A4030);
        // the bulb hangs below the shade, so it is seen lighting up with every press
        box(vc, p, -6.0f, 17.6f, 2.3f, -3.0f, 19.2f, 4.7f, WHITE, bulb, down ? LightCoordsUtil.FULL_BRIGHT : light);
        if (down) {
            box(vc, p, -7.0f, 15.05f, 1.3f, -2.0f, 15.15f, 5.7f, WHITE, 0xFF8A7A4A, LightCoordsUtil.FULL_BRIGHT);
        }
        // a ledger with a brass clasp and an inkwell
        box(vc, p, -14f, 15f, 2.5f, -9f, 16.2f, 8.5f, mc("dark_oak_planks"), 0xFF8A5A3A, light);
        box(vc, p, -13.6f, 16.2f, 2.9f, -9.4f, 16.4f, 8.1f, WHITE, 0xFFE8DCC0, light);
        box(vc, p, -12.5f, 15f, 10f, -10.5f, 17f, 12f, DARK, white, light);
    }

    // ------------------------------------------------------------------ the key

    private static void key(VertexConsumer vc, PoseStack pose, int light, boolean down) {
        PoseStack.Pose p = pose.last();
        int white = 0xFFFFFFFF;
        // a straight key under the operator's right hand: the lever runs from the pivot at the front towards the golem,
        // the knob overhangs the base right where the hand comes down (KEY_X/KEY_Z, see the operator's arms)
        box(vc, p, KEY_X - 1.7f, 15f, 2.8f, KEY_X + 1.7f, 15.6f, 8.4f, DARK, white, light);
        box(vc, p, KEY_X - 1f, 15.6f, 3.9f, KEY_X + 1f, 16.4f, 5.3f, BRASS, white, light);
        box(vc, p, KEY_X - 0.4f, 15.6f, 7.4f, KEY_X + 0.4f, 15.7f, 8.2f, BRASS, white, light);
        pose.pushPose();
        pose.translate(KEY_X / 16f, 16.2f / 16f, 4.6f / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(down ? 6f : 0f));
        box(vc, pose.last(), -0.35f, -0.2f, -0.8f, 0.35f, 0.3f, 5.2f, BRASS, 0xFFF0D080, light);
        box(vc, pose.last(), -0.8f, 0.3f, KEY_Z - 4.6f - 0.7f, 0.8f, 0.9f, KEY_Z - 4.6f + 0.7f, DARK, white, light);
        pose.popPose();
    }

    // ------------------------------------------------------------------ pulses up the mast

    private static void pulses(VertexConsumer vc, PoseStack.Pose p, long tick, float time, float wake) {
        if (wake < 0.95f) {
            return;
        }
        for (int k = 0; k < PULSE_TICKS; k++) {
            long s = tick - k;
            if (!TelegraphBlockEntity.keyPressed(s)) {
                continue;
            }
            float t = (time - s) / PULSE_TICKS;
            if (t < 0f || t > 1f) {
                continue;
            }
            float y = MAST_BOTTOM + t * (MAST_TOP - MAST_BOTTOM);
            float fade = 1f - t * 0.5f;
            int c = color(0.35f * fade, 0.95f * fade, 1f * fade);
            float z = y < 31f ? MAST_Z - 7.9f : MAST_Z - 3.2f;
            box(vc, p, MAST_X - 1.1f, y, z, MAST_X + 1.1f, y + 2.4f, z + 1.2f, WHITE, c, LightCoordsUtil.FULL_BRIGHT);
        }
    }

    private static int color(float r, float g, float b) {
        return 0xFF000000 | ((int) (Mth.clamp(r, 0f, 1f) * 255) << 16) | ((int) (Mth.clamp(g, 0f, 1f) * 255) << 8)
                | (int) (Mth.clamp(b, 0f, 1f) * 255);
    }
}
