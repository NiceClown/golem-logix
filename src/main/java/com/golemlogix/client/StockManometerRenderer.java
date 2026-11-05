package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.StockManometerBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Stock Manometer dial needle renderer:
 * Renders the rotating brass gauge needle on the front dial face, pointing according to stock / threshold ratio, and the copper
 * bridges to the redstone receivers around it, with very small golems carrying redstone dust over them.
 */
public class StockManometerRenderer extends com.golemlogix.client.render.LegacyBer<StockManometerBlockEntity> {
    private static final Identifier NEEDLE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/stock_manometer_needle.png");

    private static final Identifier COPPER = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_copper_plain.png");
    private static final float TINY_SCALE = 0.11f;
    /** Beyond this (blocks) a golem two pixels tall cannot be made out and is not drawn; the bridges stay, they are the indicator. */
    private static final double TINY_SIGHT = 24.0;
    /** Deck half width and thickness, handrail height, the distance between posts (blocks). */
    private static final float DECK_W = 0.7f / 16f, DECK_T = 0.2f / 16f, RAIL_H = 0.9f / 16f, POST_STEP = 2f / 16f;

    public StockManometerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StockManometerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(StockManometerBlock.FACING)) {
            return;
        }
        Direction facing = be.getFacing();
        bridges(be, partialTick, pose, buffers, light);

        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        if (facing == Direction.UP) {
            pose.mulPose(Axis.XP.rotationDegrees(90f));
        } else if (facing == Direction.DOWN) {
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        }
        pose.translate(-0.5f, -0.5f, -0.5f);

        // the needle's pivot: the dial's centre (8, 8), just proud of the clock face at z = 5.48
        pose.translate(8f / 16f, 8f / 16f, 5.44f / 16f);

        double ratio = be.getThreshold() <= 0 ? 0.0 : (double) be.getStockCount() / Math.max(1.0, 2.0 * be.getThreshold());
        float targetAngle = (float) (-135.0 + Math.clamp(ratio, 0.0, 1.0) * 270.0);
        pose.mulPose(Axis.ZP.rotationDegrees(-targetAngle));

        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(NEEDLE));
        float r = 4.6f / 16f;
        PoseStack.Pose p = pose.last();
        // Quad centred at pivot (0, 0)
        vertex(vc, p, r, -r, 0f, 0f, 1f, light);
        vertex(vc, p, -r, -r, 0f, 1f, 1f, light);
        vertex(vc, p, -r, r, 0f, 1f, 0f, light);
        vertex(vc, p, r, r, 0f, 0f, 0f, light);

        pose.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(StockManometerBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }

    /** The turn of the model for a facing (as the blockstate turns it, round the block's centre). */
    private static org.joml.Quaternionf turn(Direction facing) {
        if (facing == Direction.UP) {
            return Axis.XP.rotationDegrees(90f);
        }
        if (facing == Direction.DOWN) {
            return Axis.XP.rotationDegrees(-90f);
        }
        return Axis.YP.rotationDegrees(180f - facing.toYRot());
    }

    /**
     * The bridges, drawn in the world's directions so the decks slope and the golems stand upright however the clock hangs. A bridge
     * runs from the clock's rim on the side of each neighbour that takes the signal (see hasBridge), and only while that neighbour
     * is there. While the signal is on, its golem carries redstone dust over to the receiver and comes back for more, the faster the
     * stronger the signal; with no signal it waits at the clock, its hands empty.
     */
    private static void bridges(StockManometerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        net.minecraft.world.level.Level level = be.getLevel();
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        org.joml.Quaternionf q = turn(be.getFacing());
        long now = level.getGameTime();
        int steps = (int) Math.max(0, Math.min(3, now - be.bridgeTick));
        be.bridgeTick = now;
        int signal = be.getSignal();
        boolean near = camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < TINY_SIGHT * TINY_SIGHT;
        VertexConsumer copper = buffers.getBuffer(RenderTypes.entityCutout(COPPER));
        for (Direction d : Direction.values()) {
            int i = d.ordinal();
            if (!be.hasBridge(d)) {
                be.bridgePhase[i] = 0f;
                continue;
            }
            org.joml.Vector3f from = rim(q, d);
            org.joml.Vector3f to = landing(level, pos, d, from);
            org.joml.Vector3f along = new org.joml.Vector3f(to).sub(from);
            float length = along.length();
            if (length < 1e-3f) {
                continue;
            }
            deck(copper, pose, from, along, light);
            // the longer the bridge and the weaker the signal, the longer the trip
            float speed = (0.05f + 0.006f * signal) / 16f / length;
            float phase = be.bridgePhase[i];
            for (int k = 0; k < steps; k++) {
                if (phase > 0f || signal > 0) {
                    phase += speed;
                    if (phase >= 2f) {
                        phase = signal > 0 ? phase - 2f : 0f;
                    }
                }
            }
            be.bridgePhase[i] = phase;
            if (!near) {
                continue;
            }
            boolean moving = phase > 0f || signal > 0;
            float shown = moving ? phase + speed * partialTick : 0f;
            boolean carrying = moving && shown < 1f;
            float f = Mth.clamp(shown < 1f ? shown : 2f - shown, 0.08f, 0.94f);
            org.joml.Vector3f at = new org.joml.Vector3f(along).mul(f).add(from).add(0f, DECK_T, 0f);
            float fx = along.x, fz = along.z;
            if (fx * fx + fz * fz < 1e-5f) {
                // on a ladder: facing the clock's axis
                fx = 0.5f - from.x;
                fz = 0.5f - from.z;
                if (fx * fx + fz * fz < 1e-5f) {
                    fx = 1f;
                }
            } else if (moving && shown >= 1f) {
                fx = -fx;
                fz = -fz;
            }
            float time = now + partialTick + i * 13f;
            CopperGolemPuppet.render(pose, buffers, light, at, new org.joml.Vector3f(fx, 0f, fz), new org.joml.Vector3f(0f, 1f, 0f), TINY_SCALE,
                    new CopperGolemPuppet.Anim(moving ? time * 0.45f : time * 0.1f, moving, carrying, 0f), 0, carrying ? hands -> {
                        hands.scale(0.6f, 0.6f, 0.6f);
                        com.golemlogix.client.render.BerBuffers.item(buffers, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE), net.minecraft.world.item.ItemDisplayContext.FIXED,
                                net.minecraft.util.LightCoordsUtil.FULL_BRIGHT, hands, level, 0);
                    } : null);
        }
    }

    /** Where a bridge leaves the clock for that world side (block units): on the rim of the head, clear of the dial, the crown and the body. */
    private static org.joml.Vector3f rim(org.joml.Quaternionf q, Direction d) {
        org.joml.Vector3f l = new org.joml.Vector3f(d.getStepX(), d.getStepY(), d.getStepZ()).rotate(new org.joml.Quaternionf(q).conjugate());
        org.joml.Vector3f m;
        if (Math.abs(l.x) > 0.5f) {
            m = new org.joml.Vector3f(Math.signum(l.x) * 6.4f, 0f, -1f);
        } else if (Math.abs(l.y) > 0.5f) {
            // up the side of the crown, not through it
            m = new org.joml.Vector3f(l.y > 0f ? -2.6f : 0f, Math.signum(l.y) * 6.4f, -1f);
        } else if (l.z < 0f) {
            m = new org.joml.Vector3f(0f, -6f, -3.3f);
        } else {
            m = new org.joml.Vector3f(0f, 5f, 0.5f);
        }
        return m.rotate(q).div(16f).add(0.5f, 0.5f, 0.5f);
    }

    /**
     * Where the bridge ends (block units, from this block's corner): on the top of a low receiver (dust, a repeater, a rail) a little
     * way into its cell, at the face of a tall one - no higher than it starts - and at the cell's edge above or below.
     */
    private static org.joml.Vector3f landing(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, Direction d, org.joml.Vector3f from) {
        net.minecraft.core.BlockPos there = pos.relative(d);
        var shape = level.getBlockState(there).getShape(level, there);
        float top = shape.isEmpty() ? 1f / 16f : (float) shape.max(Direction.Axis.Y);
        org.joml.Vector3f to = new org.joml.Vector3f(from);
        if (d == Direction.UP) {
            to.y = 1f;
        } else if (d == Direction.DOWN) {
            to.y = top - 1f;
        } else {
            boolean low = top <= 0.5f;
            float edge = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : 0f;
            float past = (low ? 3f / 16f : 0f) * d.getAxisDirection().getStep();
            if (d.getAxis() == Direction.Axis.X) {
                to.x = edge + past;
            } else {
                to.z = edge + past;
            }
            to.y = low ? top + 0.01f : Math.min(from.y, top);
        }
        return to;
    }

    /** A deck with a handrail on posts along both edges, and a little foot plate at each end. */
    private static void deck(VertexConsumer vc, PoseStack pose, org.joml.Vector3f from, org.joml.Vector3f along, int light) {
        float length = along.length();
        org.joml.Vector3f z = new org.joml.Vector3f(along).div(length);
        org.joml.Vector3f x = new org.joml.Vector3f(0f, 1f, 0f).cross(z);
        if (x.lengthSquared() < 1e-4f) {
            x.set(1f, 0f, 0f);
        }
        x.normalize();
        org.joml.Vector3f y = new org.joml.Vector3f(z).cross(x);
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z)));
        PoseStack.Pose p = pose.last();
        box(vc, p, -DECK_W, -DECK_T, 0f, DECK_W, 0f, length, light);
        box(vc, p, -DECK_W - 0.1f / 16f, -DECK_T * 1.5f, -0.3f / 16f, DECK_W + 0.1f / 16f, 0.02f / 16f, 0.3f / 16f, light);
        box(vc, p, -DECK_W - 0.1f / 16f, -DECK_T * 1.5f, length - 0.3f / 16f, DECK_W + 0.1f / 16f, 0.02f / 16f, length + 0.3f / 16f, light);
        float w = 0.06f / 16f;
        for (float side : new float[]{-DECK_W, DECK_W}) {
            box(vc, p, side - w, RAIL_H, 0f, side + w, RAIL_H + 2f * w, length, light);
            for (float s = POST_STEP / 2f; s < length; s += POST_STEP) {
                box(vc, p, side - w, 0f, s - w, side + w, RAIL_H, s + w, light);
            }
        }
        pose.popPose();
    }

    /** A box in the given frame, each face with a small piece of the texture. */
    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float[][][] faces = {
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}, {0, 0, -1}},
                {{x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}, {x0, y0, z1}, {0, 0, 1}},
                {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}, {-1, 0, 0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}, {1, 0, 0}},
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {0, 1, 0}},
                {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {0, -1, 0}}};
        float[][] uv = {{0f, 0.25f}, {0f, 0f}, {0.25f, 0f}, {0.25f, 0.25f}};
        for (float[][] f : faces) {
            for (int i = 0; i < 4; i++) {
                vc.addVertex(p, f[i][0], f[i][1], f[i][2]).setColor(0xFFFFFFFF).setUv(uv[i][0], uv[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, f[4][0], f[4][1], f[4][2]);
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0f, 0f, -1f);
    }
}
