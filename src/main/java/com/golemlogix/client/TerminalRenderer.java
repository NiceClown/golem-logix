package com.golemlogix.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.TerminalBlockEntity;
import com.golemlogix.blocks.AnalogTerminalBlock;
import com.golemlogix.blocks.GolemProperties;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Animation on the terminal screens (only while the network is powered): when the network turns on a golem waves across the whole screen,
 * then a golem walks across the screen carrying crates to a stack. Sprites come from one sheet {@code terminal_anim.png}
 * (coordinates in texels of the 64x64 sheet, see tools/ArtForge.java), the tint depends on the terminal type.
 * The screen takes 14x14 texels of the front texture (x, y = 9..22 of 32).
 */
public class TerminalRenderer extends com.golemlogix.client.render.LegacyBer<TerminalBlockEntity> {
    private static final Identifier SHEET = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/block/terminal_anim.png");
    private static final float SHEET_SIZE = 64f;
    /** Screen size in texels and texel size in block fractions (the front texture is 32x32). */
    private static final int SCREEN = 14;
    private static final float TEXEL = 1f / 32f;

    private static final float WAVE_TICKS = 110f;
    private static final float TICKS_PER_WAVE_FRAME = 7f;
    private static final float WALK = 52f, PAUSE = 14f, CYCLE = 2 * (WALK + PAUSE);

    private static final int GREEN = 0xFF7DFF8C;
    private static final int AMBER = 0xFFFFB347;

    public TerminalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 24;
    }

    @Override
    public void render(TerminalBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        BlockState state = be.getBlockState();
        boolean active = state.getValue(GolemProperties.ACTIVE);
        float time = be.getLevel().getGameTime() + partialTick;
        // network start: "off -> on" before the player's eyes (the first observation only shows the state)
        if (be.animInitialized && active && !be.lastActive) {
            be.waveStart = time;
        }
        be.animInitialized = true;
        be.lastActive = active;
        if (!active) {
            return;
        }
        boolean crt = state.getBlock() instanceof AnalogTerminalBlock;
        int tint = crt ? GREEN : AMBER;
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);

        pose.pushPose();
        Quads.faceModel(pose, facing);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(SHEET));
        PoseStack.Pose p = pose.last();
        final int full = LightCoordsUtil.FULL_BRIGHT;

        // screen background (a circle on the CRT, a square on the crafting terminal)
        sprite(vc, p, 0, 0, SCREEN, SCREEN, crt ? 0 : 16, 0, SCREEN, SCREEN, false, 0xFFFFFFFF, full, 0);

        float sinceWave = time - be.waveStart;
        if (sinceWave >= 0 && sinceWave < WAVE_TICKS) {
            // greeting: a golem across the whole screen waves; at the end it blinks and disappears
            int frame = (int) (sinceWave / TICKS_PER_WAVE_FRAME) % 4;
            boolean blink = sinceWave > WAVE_TICKS - 12f && ((int) (sinceWave / 2f)) % 2 == 0;
            if (!blink) {
                sprite(vc, p, 0, 0, SCREEN, SCREEN, frame * 16, 32, SCREEN, SCREEN, false, tint, full, 1);
            }
        } else {
            scene(vc, p, time + (be.getBlockPos().hashCode() & 127), tint, full);
        }
        pose.popPose();
    }

    /** An everyday scene: a golem carries a crate from the stock on the left to the stack on the right and comes back empty. */
    private void scene(VertexConsumer vc, PoseStack.Pose p, float time, int tint, int light) {
        float t = time % CYCLE;
        int cycle = (int) (time / CYCLE);
        // floor (a bit shorter so it fits in the circle)
        sprite(vc, p, 3, 11, 8, 1, 62, 62, 1, 1, false, tint, light, 1);

        boolean outward = t < WALK + PAUSE;
        float leg = outward ? t : t - (WALK + PAUSE);
        boolean walking = leg < WALK;
        float x = walking ? 1f + 6f * (leg / WALK) : 7f;
        if (!outward) {
            x = walking ? 7f - 6f * (leg / WALK) : 1f;
        }
        int frame = walking ? ((int) (leg / 4f)) % 4 : 1;

        // stock on the left: two crates, one of them in the golem's hands on the way there
        int stock = outward && walking ? 1 : 2;
        // stack on the right: grows from 0 to 3 and resets; a crate appears in it when the golem has brought it
        boolean dropped = !(outward && walking);
        int pile = Math.min(3, cycle % 4 + (dropped ? 1 : 0));
        for (int i = 0; i < stock; i++) {
            sprite(vc, p, 0, 9 - i * 2, 2, 2, 32, 16, 2, 2, false, tint, light, 1);
        }
        for (int i = 0; i < pile; i++) {
            sprite(vc, p, 12, 9 - i * 2, 2, 2, 32, 16, 2, 2, false, tint, light, 1);
        }
        // golem: with a crate on the way there, empty on the way back; going left to right it looks right
        int gx = Math.round(x);
        boolean carry = outward && walking;
        sprite(vc, p, gx, 4, 6, 7, frame * 8, carry ? 16 : 24, 6, 7, !outward, tint, light, 2);
    }

    /**
     * Sprite: the rectangle (sx, sy, w, h) in screen texels (from the top left corner) shows the sheet area
     * (u, v, uw, vh). layer pushes the plane outwards so the layers do not flicker.
     */
    private static void sprite(VertexConsumer vc, PoseStack.Pose p, int sx, int sy, int w, int h, int u, int v, int uw, int vh,
                               boolean flip, int argb, int light, int layer) {
        // the screen is centred on the front face; the viewer's right is decreasing model x
        float offX = sx + w / 2f - SCREEN / 2f;
        float offY = SCREEN / 2f - (sy + h / 2f);
        float cx = 0.5f - offX * TEXEL;
        float cy = 0.5f + offY * TEXEL;
        float z = -(0.002f + 0.0015f * layer);
        // a thin UV gap so filtering does not catch neighbouring sprites of the sheet
        float e = 0.0005f;
        Quads.front(vc, p, cx, cy, z, w * TEXEL, h * TEXEL, 0f,
                u / SHEET_SIZE + e, v / SHEET_SIZE + e, (u + uw) / SHEET_SIZE - e, (v + vh) / SHEET_SIZE - e, flip, argb, light);
    }
}
