package com.golemlogix.client;

import com.golemlogix.blocks.SnowPipeBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.limb;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * Shared material textures, lighting probes, valve-wheel/bracket sub-assemblies, and world-aware helpers used by the cryo
 * machines ({@link CryoJarRenderer}, {@link JarWholeRenderer}, {@link DistillationTowerRenderer}, {@link CryoMixerRenderer}) and
 * directional bus renderers.
 */
final class MachineParts {
    static final ResourceLocation BRICK = mcBlock("nether_bricks"), DARK = modBlock("sat_dark"), BRASS = modBlock("sat_brass"),
            ICE = modBlock("sat_ice"), GLASS = modBlock("sat_glass"), RUNES = modBlock("cryo_panel_matrix"),
            GAUGE = modBlock("monitor_gauge"), FRAME = mcBlock("weathered_copper"), SNOW = mcBlock("snow"),
            CRYO = modBlock("cryo_source_still"), COPPER = mcBlock("copper_block");
    static final int BRICK_TINT = 0xFFC89A8A, WHITE = 0xFFFFFFFF, LIGHT_BRASS = 0xFFF5E2B8, DIM_BRASS = 0xFFDCC49A;

    private static final Direction[] DIRS = Direction.values();
    private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SOCKET_POS = new BlockPos.MutableBlockPos();

    private MachineParts() {
    }

    /**
     * Brightest light around a directional block except behind it (side windows let light in even when a full block sits directly
     * in front of the face).
     */
    static int frontLight(Level level, BlockPos pos, Direction facing) {
        int self = LevelRenderer.getLightColor(level, pos);
        int block = LightTexture.block(self), sky = LightTexture.sky(self);
        Direction back = facing.getOpposite();
        for (Direction d : DIRS) {
            if (d != back) {
                LIGHT_POS.setWithOffset(pos, d);
                int c = LevelRenderer.getLightColor(level, LIGHT_POS);
                block = Math.max(block, LightTexture.block(c));
                sky = Math.max(sky, LightTexture.sky(c));
            }
        }
        return LightTexture.pack(block, sky);
    }

    /**
     * Brass sockets where network pipes dock onto a two-block-tall jar (world-aligned pixels, {@code Y 0..32}).
     * Returns whether a pipe docks from above.
     */
    static boolean jarSockets(VertexConsumer vc, PoseStack.Pose p, Level level, BlockPos pos, int light) {
        boolean above = false;
        for (int half = 0; half < 2; half++) {
            for (Direction d : DIRS) {
                if (d == Direction.DOWN || (d == Direction.UP && half == 0)) {
                    continue;
                }
                SOCKET_POS.set(pos.getX() + d.getStepX(), pos.getY() + half + d.getStepY(), pos.getZ() + d.getStepZ());
                BlockState n = level.getBlockState(SOCKET_POS);
                if (!(n.getBlock() instanceof SnowPipeBlock)
                        || n.getValue(SnowPipeBlock.LINKS.get(d.getOpposite())) != SnowPipeBlock.Link.DEVICE) {
                    continue;
                }
                if (d == Direction.UP) {
                    above = true;
                    box(vc, p, 4.4f, 29.4f, 4.4f, 11.6f, 32f, 11.6f, BRASS, 0xFFE8C878, light);
                    continue;
                }
                // from the block's face 3 px in (to the glass), around the pipe's axis at the middle of the half
                float y = half * 16 + 8;
                float a0 = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 13f : 0f;
                float a1 = a0 + 3f;
                if (d.getAxis() == Direction.Axis.X) {
                    box(vc, p, a0, y - 3.6f, 4.4f, a1, y + 3.6f, 11.6f, BRASS, 0xFFE8C878, light);
                } else {
                    box(vc, p, 4.4f, y - 3.6f, a0, 11.6f, y + 3.6f, a1, BRASS, 0xFFE8C878, light);
                }
            }
        }
        return above;
    }

    /** Degrees a jar/reactor valve wheel has turned: a quarter turn every 70 ticks while active, still when empty. */
    static float valveTurnAngle(float time, boolean on) {
        if (!on) {
            return 0f;
        }
        float cycle = time / 70f;
        float k = cycle - (float) Math.floor(cycle);
        float ease = k < 0.25f ? Mth.sin(k / 0.25f * Mth.HALF_PI) : 1f;
        return ((float) Math.floor(cycle) + ease) * 90f;
    }

    /** Cyan rune colour for jars and reactors: dim when empty, breathing softly while holding Source. */
    static int cyanRuneColor(float time, boolean on) {
        float glow = on ? 0.55f + 0.45f * (0.8f + 0.2f * Mth.sin(time * 0.08f)) : 0.18f;
        return 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xF0 * glow);
    }

    /**
     * Bracketed golem platform on a machine's right side: dark grated deck from {@code x0} to {@code x1} (top at {@code y}),
     * bolted brass wall plate, and two diagonal brass struts down to the plinth at {@code (footX, footY)}.
     */
    static void platform(VertexConsumer vc, PoseStack pose, float x0, float x1, float y, float z0, float z1, float footY, float footX,
                         int light) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite darkSprite = sprite(DARK), brassSprite = sprite(BRASS);
        box(vc, p, x0, y - 0.6f, z0, x1, y, z1, darkSprite, WHITE, light);
        box(vc, p, x0 - 0.2f, y - 0.8f, z0 - 0.2f, x0 + 0.6f, y + 0.2f, z1 + 0.2f, brassSprite, WHITE, light);
        // the bracket plate on the frame with two bolts
        box(vc, p, x1 - 0.5f, y - 2.4f, z0 + 0.2f, x1, y + 0.6f, z1 - 0.2f, brassSprite, 0xFFD8B880, light);
        box(vc, p, x1 - 0.8f, y - 1.6f, z0 + 0.8f, x1 - 0.4f, y - 1.2f, z0 + 1.2f, darkSprite, WHITE, light);
        box(vc, p, x1 - 0.8f, y - 1.6f, z1 - 1.2f, x1 - 0.4f, y - 1.2f, z1 - 0.8f, darkSprite, WHITE, light);
        // two struts from under the deck's outer edge down to the plinth
        limb(vc, pose, x0 + 0.6f, y - 0.6f, z0 + 0.5f, footX, footY, z0 + 0.5f, 0.55f, BRASS, light);
        limb(vc, pose, x0 + 0.6f, y - 0.6f, z1 - 0.5f, footX, footY, z1 - 0.5f, 0.55f, BRASS, light);
        box(vc, p, footX - 0.3f, footY - 0.5f, z0 + 0.1f, footX + 0.3f, footY + 0.5f, z1 - 0.1f, brassSprite, 0xFFC8A060, light);
    }

    /** Brass hand wheel in the YZ plane (axle along X) at {@code (x, y, z)} pixels, turned by {@code angle} degrees. */
    static void wheelX(VertexConsumer vc, PoseStack pose, float x, float y, float z, float r, float angle, int light) {
        pose.pushPose();
        pose.translate(x * PIXEL, y * PIXEL, z * PIXEL);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite brassSprite = sprite(BRASS);
        box(vc, p, -0.5f, -0.35f, -r, 0.5f, 0.35f, r, brassSprite, WHITE, light);
        box(vc, p, -0.5f, -r, -0.35f, 0.5f, r, 0.35f, brassSprite, WHITE, light);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8f;
            float cy = Mth.sin(a) * r, cz = Mth.cos(a) * r;
            box(vc, p, -0.45f, cy - 0.45f, cz - 0.45f, 0.45f, cy + 0.45f, cz + 0.45f, brassSprite, 0xFFF0D080, light);
        }
        box(vc, p, -0.7f, -0.6f, -0.6f, 0.7f, 0.6f, 0.6f, DARK, WHITE, light);
        pose.popPose();
    }

    /** Brass hand wheel in the XY plane (axle along Z) at {@code (x, y, z)} pixels, turned by {@code angle} degrees. */
    static void wheelZ(VertexConsumer vc, PoseStack pose, float x, float y, float z, float r, float angle, int light) {
        pose.pushPose();
        pose.translate(x * PIXEL, y * PIXEL, z * PIXEL);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite brassSprite = sprite(BRASS);
        box(vc, p, -r, -0.35f, -0.4f, r, 0.35f, 0.4f, brassSprite, WHITE, light);
        box(vc, p, -0.35f, -r, -0.4f, 0.35f, r, 0.4f, brassSprite, WHITE, light);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8f;
            float cx = Mth.cos(a) * r, cy = Mth.sin(a) * r;
            box(vc, p, cx - 0.5f, cy - 0.5f, -0.45f, cx + 0.5f, cy + 0.5f, 0.45f, brassSprite, LIGHT_BRASS, light);
        }
        box(vc, p, -0.7f, -0.7f, -0.6f, 0.7f, 0.7f, 0.6f, DARK, WHITE, light);
        pose.popPose();
    }

    /** Renders a scaled block standing with the middle of its base at {@code (x, y, z)} pixels, rotated by {@code yaw} degrees. */
    static void scaledBlock(PoseStack pose, MultiBufferSource buffers, BlockState state, float x, float y, float z, float scale,
                            float yaw, int light) {
        pose.pushPose();
        pose.translate(x * PIXEL, y * PIXEL, z * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5f, 0f, -0.5f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
