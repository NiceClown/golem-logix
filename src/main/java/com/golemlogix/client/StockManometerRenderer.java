package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.StockManometerBlockEntity;
import com.golemlogix.blocks.StockManometerBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.golemlogix.client.render.PixelMesh.PIXEL;

/**
 * Stock Manometer renderer: draws the rotating brass gauge needle on the front dial face according to stock/threshold ratio,
 * plus copper walkways to neighbouring redstone receivers with tiny copper golems ferrying redstone dust across them.
 */
public class StockManometerRenderer implements BlockEntityRenderer<StockManometerBlockEntity> {
    private static final ResourceLocation NEEDLE =
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/stock_manometer_needle.png");
    private static final ResourceLocation COPPER =
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_copper_plain.png");
    private static final float TINY_SCALE = 0.11f;
    /** Beyond this distance (blocks) the two-pixel golems are skipped while the bridge walkways stay visible. */
    private static final double TINY_SIGHT = 24.0;
    /** Deck half-width, thickness, handrail height, and post spacing (blocks). */
    private static final float DECK_W = 0.7f * PIXEL, DECK_T = 0.2f * PIXEL, RAIL_H = 0.9f * PIXEL, POST_STEP = 2f * PIXEL;

    private static final Direction[] DIRS = Direction.values();
    private static final Quaternionf[] FACING_TURN = new Quaternionf[6];
    private static final Quaternionf[] FACING_INV = new Quaternionf[6];
    static {
        for (Direction d : DIRS) {
            Quaternionf q = d == Direction.UP ? Axis.XP.rotationDegrees(90f)
                    : d == Direction.DOWN ? Axis.XP.rotationDegrees(-90f)
                    : Axis.YP.rotationDegrees(180f - d.toYRot());
            FACING_TURN[d.ordinal()] = q;
            FACING_INV[d.ordinal()] = new Quaternionf(q).conjugate();
        }
    }

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f SCRATCH_LOCAL = new Vector3f();
    private static final Vector3f SCRATCH_FROM = new Vector3f();
    private static final Vector3f SCRATCH_TO = new Vector3f();
    private static final Vector3f SCRATCH_ALONG = new Vector3f();
    private static final Vector3f SCRATCH_AT = new Vector3f();
    private static final Vector3f SCRATCH_FWD = new Vector3f();
    private static final Vector3f DECK_X = new Vector3f();
    private static final Vector3f DECK_Y = new Vector3f();
    private static final Vector3f DECK_Z = new Vector3f();
    private static final Matrix3f DECK_MAT = new Matrix3f();
    private static final Quaternionf DECK_Q = new Quaternionf();
    private static final BlockPos.MutableBlockPos NEIGHBOUR = new BlockPos.MutableBlockPos();

    // Per-bridge scratch storage shared between pass 1 (copper decks) and pass 2 (golems) on the render thread
    private static final boolean[] BRIDGE_ACTIVE = new boolean[6];
    private static final float[] BRIDGE_FROM_X = new float[6], BRIDGE_FROM_Y = new float[6], BRIDGE_FROM_Z = new float[6];
    private static final float[] BRIDGE_ALONG_X = new float[6], BRIDGE_ALONG_Y = new float[6], BRIDGE_ALONG_Z = new float[6];
    private static final float[] BRIDGE_SPEED = new float[6];
    private static ItemStack redstoneStack;

    public StockManometerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(StockManometerBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }

    @Override
    public void render(StockManometerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(StockManometerBlock.FACING)) {
            return;
        }
        renderBridges(be, partialTick, pose, buffers, light);
        renderNeedle(be, pose, buffers, light);
    }

    private static void renderNeedle(StockManometerBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        Direction facing = be.getFacing();
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.mulPose(FACING_TURN[facing.ordinal()]);
        pose.translate(-0.5f, -0.5f, -0.5f);

        // needle pivot: dial centre (8, 8), just proud of the clock face
        pose.translate(8f * PIXEL, 8f * PIXEL, 4.78f * PIXEL);

        double ratio = be.getThreshold() <= 0 ? 0.0 : (double) be.getStockCount() / Math.max(1.0, 2.0 * be.getThreshold());
        float targetAngle = (float) (-135.0 + Math.clamp(ratio, 0.0, 1.0) * 270.0);
        pose.mulPose(Axis.ZP.rotationDegrees(-targetAngle));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(NEEDLE));
        float r = 4.1f * PIXEL;
        PoseStack.Pose p = pose.last();
        vertex(vc, p, r, -r, 0f, 0f, 1f, light);
        vertex(vc, p, -r, -r, 0f, 1f, 1f, light);
        vertex(vc, p, -r, r, 0f, 1f, 0f, light);
        vertex(vc, p, r, r, 0f, 0f, 0f, light);

        pose.popPose();
    }

    /**
     * Bridges in world coordinates so decks slope naturally and golems stand upright regardless of how the clock is mounted.
     * Drawn in two passes: all copper decks on a single VertexConsumer first, then the tiny golems.
     */
    private static void renderBridges(StockManometerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        long now = level.getGameTime();
        int steps = (int) Math.max(0, Math.min(3, now - be.bridgeTick));
        be.bridgeTick = now;
        int signal = be.getSignal();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean near = camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < TINY_SIGHT * TINY_SIGHT;

        boolean anyNear = renderBridgeDecks(be, level, pos, steps, signal, near, pose, buffers, light);
        if (anyNear) {
            renderBridgeGolems(be, level, now, partialTick, signal, pose, buffers, light);
        }
    }

    private static boolean renderBridgeDecks(StockManometerBlockEntity be, Level level, BlockPos pos, int steps, int signal,
                                             boolean near, PoseStack pose, MultiBufferSource buffers, int light) {
        int facingOrd = be.getFacing().ordinal();
        Quaternionf q = FACING_TURN[facingOrd];
        Quaternionf invQ = FACING_INV[facingOrd];
        VertexConsumer copper = null;
        boolean anyNear = false;
        for (int i = 0; i < DIRS.length; i++) {
            Direction d = DIRS[i];
            BRIDGE_ACTIVE[i] = false;
            if (!be.hasBridge(d)) {
                be.bridgePhase[i] = 0f;
                continue;
            }
            rim(q, invQ, d, SCRATCH_FROM);
            landing(level, pos, d, SCRATCH_FROM, SCRATCH_TO);
            SCRATCH_ALONG.set(SCRATCH_TO).sub(SCRATCH_FROM);
            float length = SCRATCH_ALONG.length();
            if (length < 1e-3f) {
                continue;
            }
            if (copper == null) {
                copper = buffers.getBuffer(RenderType.entityCutoutNoCull(COPPER));
            }
            deck(copper, pose, SCRATCH_FROM, SCRATCH_ALONG, length, light);
            float speed = (0.05f + 0.006f * signal) * PIXEL / length;
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
            if (near) {
                BRIDGE_ACTIVE[i] = true;
                BRIDGE_FROM_X[i] = SCRATCH_FROM.x;
                BRIDGE_FROM_Y[i] = SCRATCH_FROM.y;
                BRIDGE_FROM_Z[i] = SCRATCH_FROM.z;
                BRIDGE_ALONG_X[i] = SCRATCH_ALONG.x;
                BRIDGE_ALONG_Y[i] = SCRATCH_ALONG.y;
                BRIDGE_ALONG_Z[i] = SCRATCH_ALONG.z;
                BRIDGE_SPEED[i] = speed;
                anyNear = true;
            }
        }
        return anyNear;
    }

    private static void renderBridgeGolems(StockManometerBlockEntity be, Level level, long now, float partialTick, int signal,
                                           PoseStack pose, MultiBufferSource buffers, int light) {
        if (redstoneStack == null) {
            redstoneStack = new ItemStack(Items.REDSTONE);
        }
        float baseTime = (now % 72000L) + partialTick;
        for (int i = 0; i < DIRS.length; i++) {
            if (!BRIDGE_ACTIVE[i]) {
                continue;
            }
            float phase = be.bridgePhase[i];
            float speed = BRIDGE_SPEED[i];
            boolean moving = phase > 0f || signal > 0;
            float shown = moving ? phase + speed * partialTick : 0f;
            boolean carrying = moving && shown < 1f;
            float f = Mth.clamp(shown < 1f ? shown : 2f - shown, 0.08f, 0.94f);
            float fromX = BRIDGE_FROM_X[i], fromY = BRIDGE_FROM_Y[i], fromZ = BRIDGE_FROM_Z[i];
            float alongX = BRIDGE_ALONG_X[i], alongY = BRIDGE_ALONG_Y[i], alongZ = BRIDGE_ALONG_Z[i];
            SCRATCH_AT.set(fromX + alongX * f, fromY + alongY * f + DECK_T, fromZ + alongZ * f);
            float fx = alongX, fz = alongZ;
            if (fx * fx + fz * fz < 1e-5f) {
                // vertical ladder: face the clock's central axis
                fx = 0.5f - fromX;
                fz = 0.5f - fromZ;
                if (fx * fx + fz * fz < 1e-5f) {
                    fx = 1f;
                }
            } else if (moving && shown >= 1f) {
                fx = -fx;
                fz = -fz;
            }
            SCRATCH_FWD.set(fx, 0f, fz);
            float time = baseTime + i * 13f;
            CopperGolemPuppet.render(pose, buffers, light, SCRATCH_AT, SCRATCH_FWD, UP, TINY_SCALE,
                    new CopperGolemPuppet.Anim(moving ? time * 0.45f : time * 0.1f, moving, carrying, 0f), 0, carrying ? hands -> {
                        hands.scale(0.6f, 0.6f, 0.6f);
                        Minecraft.getInstance().getItemRenderer().renderStatic(redstoneStack,
                                ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                                hands, buffers, level, 0);
                    } : null);
        }
    }

    /** Where a bridge leaves the clock for that world side (block units): docked into the copper flank ports, clear of dial & top crystal tube. */
    private static void rim(Quaternionf q, Quaternionf invQ, Direction d, Vector3f out) {
        SCRATCH_LOCAL.set(d.getStepX(), d.getStepY(), d.getStepZ()).rotate(invQ);
        if (Math.abs(SCRATCH_LOCAL.x) > 0.5f) {
            out.set(Math.signum(SCRATCH_LOCAL.x) * 5.8f, 0f, 1.0f);
        } else if (Math.abs(SCRATCH_LOCAL.y) > 0.5f) {
            // up past the top cryo sight-tube, not through it
            out.set(SCRATCH_LOCAL.y > 0f ? -2.8f : 0f, Math.signum(SCRATCH_LOCAL.y) * 5.6f, 0.5f);
        } else if (SCRATCH_LOCAL.z < 0f) {
            out.set(0f, -6.2f, -3.5f);
        } else {
            out.set(0f, 5.2f, 1.5f);
        }
        out.rotate(q).div(16f).add(0.5f, 0.5f, 0.5f);
    }

    /**
     * Where the bridge ends (block units, from this block's corner): on the top of a low receiver (dust, a repeater, a rail) a little
     * way into its cell, at the face of a tall one - no higher than it starts - and at the cell's edge above or below.
     */
    private static void landing(Level level, BlockPos pos, Direction d, Vector3f from, Vector3f out) {
        NEIGHBOUR.setWithOffset(pos, d);
        var shape = level.getBlockState(NEIGHBOUR).getShape(level, NEIGHBOUR);
        float top = shape.isEmpty() ? PIXEL : (float) shape.max(Direction.Axis.Y);
        out.set(from);
        if (d == Direction.UP) {
            out.y = 1f;
        } else if (d == Direction.DOWN) {
            out.y = top - 1f;
        } else {
            boolean low = top <= 0.5f;
            float edge = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : 0f;
            float past = (low ? 3f * PIXEL : 0f) * d.getAxisDirection().getStep();
            if (d.getAxis() == Direction.Axis.X) {
                out.x = edge + past;
            } else {
                out.z = edge + past;
            }
            out.y = low ? top + 0.01f : Math.min(from.y, top);
        }
    }

    /** A deck with a handrail on posts along both edges, and a little foot plate at each end. */
    private static void deck(VertexConsumer vc, PoseStack pose, Vector3f from, Vector3f along, float length, int light) {
        DECK_Z.set(along).div(length);
        DECK_X.set(UP).cross(DECK_Z);
        if (DECK_X.lengthSquared() < 1e-4f) {
            DECK_X.set(1f, 0f, 0f);
        }
        DECK_X.normalize();
        DECK_Y.set(DECK_Z).cross(DECK_X);
        DECK_MAT.set(DECK_X.x, DECK_X.y, DECK_X.z, DECK_Y.x, DECK_Y.y, DECK_Y.z, DECK_Z.x, DECK_Z.y, DECK_Z.z);
        DECK_Q.setFromNormalized(DECK_MAT);
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(DECK_Q);
        PoseStack.Pose p = pose.last();
        box(vc, p, -DECK_W, -DECK_T, 0f, DECK_W, 0f, length, light);
        box(vc, p, -DECK_W - 0.1f * PIXEL, -DECK_T * 1.5f, -0.3f * PIXEL, DECK_W + 0.1f * PIXEL, 0.02f * PIXEL, 0.3f * PIXEL, light);
        box(vc, p, -DECK_W - 0.1f * PIXEL, -DECK_T * 1.5f, length - 0.3f * PIXEL, DECK_W + 0.1f * PIXEL, 0.02f * PIXEL, length + 0.3f * PIXEL, light);
        float w = 0.06f * PIXEL;
        for (int sIdx = -1; sIdx <= 1; sIdx += 2) {
            float side = sIdx * DECK_W;
            box(vc, p, side - w, RAIL_H, 0f, side + w, RAIL_H + 2f * w, length, light);
            for (float s = POST_STEP / 2f; s < length; s += POST_STEP) {
                box(vc, p, side - w, 0f, s - w, side + w, RAIL_H, s + w, light);
            }
        }
        pose.popPose();
    }

    /** A box in the given frame, each face with a small piece of the texture. */
    private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        face(vc, p, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0f, 0f, -1f, light);
        face(vc, p, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0f, 0f, 1f, light);
        face(vc, p, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1f, 0f, 0f, light);
        face(vc, p, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1f, 0f, 0f, light);
        face(vc, p, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f, light);
        face(vc, p, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0f, -1f, 0f, light);
    }

    private static void face(VertexConsumer vc, PoseStack.Pose p,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, int light) {
        vc.addVertex(p, ax, ay, az).setColor(0xFFFFFFFF).setUv(0f, 0.25f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, bx, by, bz).setColor(0xFFFFFFFF).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx, cy, cz).setColor(0xFFFFFFFF).setUv(0.25f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, dx, dy, dz).setColor(0xFFFFFFFF).setUv(0.25f, 0.25f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, 0f, 0f, -1f);
    }
}
