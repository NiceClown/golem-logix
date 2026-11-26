package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.client.render.PixelMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

import static com.golemlogix.client.render.PixelMesh.PIXEL;

/**
 * Copper Order Ledger renderer: the Storekeeper (a small copper golem behind the lectern) scribbles when an order is
 * crafting, looks around while idle, and periodically turns a page. A poppy atop its antenna signals the worst slot status:
 * fast-blinking red (missing ingredients), slow-blinking yellow (no pattern), or breathing green (healthy). Nearby players
 * also see the tracked filter items laid out flat across the open ledger pages.
 */
public class StockRequesterRenderer extends LegacyBer<StockRequesterBlockEntity> {
    private static final Identifier STEM = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_flower_stem.png");
    private static final Identifier PETALS = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_flower_petals.png");
    private static final Identifier PAGE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_page.png");

    /** Storekeeper feet on the walnut top behind the lectern (~6.5 px tall with antenna). */
    private static final float SCALE = 0.27f, FEET_Y = 11f, FEET_Z = 14.6f;
    /** Page turn cadence (halved while actively writing) and duration in ticks. */
    private static final int PAGE_EVERY = 240, PAGE_TICKS = 24;
    /** Beyond this distance (blocks) page item icons are too small to read and are skipped. */
    private static final double PAGE_SIGHT = 24.0;
    private static final float LECTERN_TILT_DEG = 22.5f;
    private static final double SIN_225 = Math.sin(Math.toRadians(LECTERN_TILT_DEG));
    private static final double COS_225 = Math.cos(Math.toRadians(LECTERN_TILT_DEG));

    private static final int COLOR_MISSING = 0xE02420;
    private static final int COLOR_NO_PATTERN = 0xF0C020;
    private static final int COLOR_OK = 0x7CFF3A;

    private static final Vector3f GOLEM_POS = new Vector3f(0.5f, FEET_Y * PIXEL, FEET_Z * PIXEL);
    private static final Vector3f FWD_NORTH = new Vector3f(0f, 0f, -1f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final ItemStack[] KEPT = new ItemStack[StockRequesterBlockEntity.FILTER_SLOTS];

    public StockRequesterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StockRequesterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean writing = false, missing = false, noPattern = false;
        for (int i = 0; i < StockRequesterBlockEntity.FILTER_SLOTS; i++) {
            int st = be.getSlotStatus(i);
            writing |= st == StockRequesterBlockEntity.STATUS_CRAFTING;
            missing |= st == StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS;
            noPattern |= st == StockRequesterBlockEntity.STATUS_NO_PATTERN;
        }
        float time = (be.getLevel().getGameTime() % 72000L) + partialTick;
        float seed = (be.getBlockPos().hashCode() & 255) * 0.37f;
        int every = writing ? PAGE_EVERY / 2 : PAGE_EVERY;
        float inTurn = (time + seed * 7) % every;
        float turn = inTurn < PAGE_TICKS ? inTurn / PAGE_TICKS : -1f;

        pose.pushPose();
        Quads.orient(pose, facing);
        int lit = MachineParts.frontLight(be.getLevel(), be.getBlockPos(), facing);
        renderStorekeeper(pose, buffers, lit, time, seed, turn, writing, missing, noPattern);
        if (turn >= 0f) {
            renderTurningPage(pose, buffers.getBuffer(RenderTypes.entityCutout(PAGE)), turn, lit);
        }
        if (pagesInSight(be, facing)) {
            renderPageItems(be, pose, buffers, lit);
        }
        pose.popPose();
    }

    private static void renderStorekeeper(PoseStack pose, MultiBufferSource buffers, int lit, float time, float seed,
                                          float turn, boolean writing, boolean missing, boolean noPattern) {
        CopperGolemPuppet.Arms arms;
        CopperGolemPuppet.Anim anim;
        if (turn >= 0f) {
            // sweeps page from right to left while holding the ledger open
            float sweep = Mth.sin(turn * Mth.PI);
            arms = new CopperGolemPuppet.Arms(-0.9f, 0.05f, -1.0f - 0.35f * sweep, -0.35f + 0.75f * turn, 0f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.3f);
        } else if (writing) {
            // scribbles with quill hand over the page
            float s = Mth.sin(time * 0.9f) * 0.12f;
            arms = new CopperGolemPuppet.Arms(-1.05f + s, 0.05f + Mth.cos(time * 0.9f) * 0.05f, -0.85f, -0.1f, 0f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.35f);
        } else {
            arms = new CopperGolemPuppet.Arms(-0.1f, 0.12f, -0.1f, -0.12f, Mth.sin((time + seed * 40) * 0.02f) * 0.7f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f);
        }

        // poppy status priority: missing ingredients (fast red blink) > no pattern (slow yellow blink) > healthy (breathing green)
        int color = missing ? COLOR_MISSING : noPattern ? COLOR_NO_PATTERN : COLOR_OK;
        float glow;
        if (missing || noPattern) {
            int period = missing ? 10 : 24;
            glow = (time % period) < period * 0.5f ? 1f : 0.35f;
        } else {
            glow = 0.75f + 0.25f * Mth.sin(time * 0.08f);
        }
        int tint = PixelMesh.scaledRgb(color, glow);
        int petalLight = glow > 0.9f || !(missing || noPattern) ? LightCoordsUtil.FULL_BRIGHT : lit;

        CopperGolemPuppet.render(pose, buffers, lit, GOLEM_POS, FWD_NORTH, UP, SCALE, anim, 0, null, arms, head -> {
            PoseStack.Pose p = head.last();
            poppyCross(buffers.getBuffer(RenderTypes.entityCutout(STEM)), p, 0xFFFFFFFF, lit);
            poppyCross(buffers.getBuffer(RenderTypes.entityCutout(PETALS)), p, tint, petalLight);
        });
    }

    /** True when the camera is close enough and on the facing side of the tilted ledger pages. */
    private static boolean pagesInSight(StockRequesterBlockEntity be, Direction facing) {
        BlockPos pos = be.getBlockPos();
        double dx = camera.x - (pos.getX() + 0.5), dy = camera.y - (pos.getY() + 12.6 / 16.0), dz = camera.z - (pos.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > PAGE_SIGHT * PAGE_SIGHT) {
            return false;
        }
        Direction back = facing.getOpposite();
        return dy * COS_225 + (dx * back.getStepX() + dz * back.getStepZ()) * SIN_225 > 0.0;
    }

    /** Tracked filter icons laid out flat across the left and right open pages of the tilted lectern book. */
    private static void renderPageItems(StockRequesterBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        int n = 0;
        for (int i = 0; i < StockRequesterBlockEntity.FILTER_SLOTS; i++) {
            ItemStack stack = be.getFilters().getItem(i);
            if (!stack.isEmpty()) {
                KEPT[n++] = stack;
            }
        }
        if (n == 0) {
            return;
        }
        int rows = n <= 2 ? 1 : n <= 4 ? 2 : 3, cols = n <= 6 ? 1 : 2;
        float size = n <= 2 ? 4.4f : n <= 4 ? 3.6f : n <= 6 ? 2.6f : 2.2f;
        int onLeft = (n + 1) / 2;
        pose.pushPose();
        pose.translate(8f * PIXEL, 11f * PIXEL, 8f * PIXEL);
        pose.mulPose(Axis.XP.rotationDegrees(LECTERN_TILT_DEG));
        pose.translate(0f, 1.53f * PIXEL, 0f);
        for (int i = 0; i < n; i++) {
            ItemStack stack = KEPT[i];
            KEPT[i] = null;
            boolean left = i < onLeft;
            int at = left ? i : i - onLeft;
            int row = Math.min(at / cols, rows - 1), col = at % cols;
            float pageX = left ? -2.9f : 2.5f;
            float x = cols == 1 ? pageX : pageX + (col == 0 ? -1.2f : 1.2f);
            float z = rows == 1 ? 0f : (row - (rows - 1) / 2f) * (rows == 2 ? 3.9f : 2.75f);
            pose.pushPose();
            pose.translate(x * PIXEL, 0f, z * PIXEL);
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
            float s = size * PIXEL;
            pose.scale(s, s, 0.004f);
            BerBuffers.item(buffers, stack, ItemDisplayContext.GUI, light, pose, be.getLevel(), 0);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Crossed-quad poppy sitting atop the copper golem's antenna ball (head frame: Y down, ball top at y = -13 px). */
    private static void poppyCross(VertexConsumer vc, PoseStack.Pose p, int argb, int light) {
        float h = 2.83f * PIXEL, y0 = -12.6f * PIXEL, y1 = -20.6f * PIXEL;
        vert(vc, p, -h, y1, -h, 0f, 0f, argb, light);
        vert(vc, p, -h, y0, -h, 0f, 1f, argb, light);
        vert(vc, p, h, y0, h, 1f, 1f, argb, light);
        vert(vc, p, h, y1, h, 1f, 0f, argb, light);

        vert(vc, p, -h, y1, h, 0f, 0f, argb, light);
        vert(vc, p, -h, y0, h, 0f, 1f, argb, light);
        vert(vc, p, h, y0, -h, 1f, 1f, argb, light);
        vert(vc, p, h, y1, -h, 1f, 0f, argb, light);
    }

    /** Turning ledger page pivoting 180 degrees about the spine on the tilted lectern top. */
    private static void renderTurningPage(PoseStack pose, VertexConsumer vc, float turn, int light) {
        pose.pushPose();
        pose.translate(8f * PIXEL, 11f * PIXEL, 8f * PIXEL);
        pose.mulPose(Axis.XP.rotationDegrees(LECTERN_TILT_DEG));
        pose.translate(0f, 1.6f * PIXEL, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(180f * turn));
        PoseStack.Pose p = pose.last();
        float w = 5.2f * PIXEL, z0 = -4.2f * PIXEL, z1 = 4.2f * PIXEL;
        // counter-rotate the shading normal so the turning paper stays evenly lit
        float a = Mth.PI * turn, nx = Mth.sin(a), ny = Mth.cos(a);
        vc.addVertex(p, 0f, 0f, z0).setColor(0xFFFFFFFF).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, 0f);
        vc.addVertex(p, 0f, 0f, z1).setColor(0xFFFFFFFF).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, 0f);
        vc.addVertex(p, w, 0f, z1).setColor(0xFFFFFFFF).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, 0f);
        vc.addVertex(p, w, 0f, z0).setColor(0xFFFFFFFF).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, 0f);
        pose.popPose();
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb, int light) {
        vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 1f, 0f);
    }
}
