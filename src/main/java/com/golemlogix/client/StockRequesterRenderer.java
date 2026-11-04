package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.StockRequesterBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

/**
 * Copper Order Ledger: the Storekeeper, a small copper golem standing behind the lectern. While an order of the ledger is being
 * crafted it bends over the book and scribbles; otherwise it waits, now and then looking round the workshop. Now and then it turns
 * a page. A poppy on its head shows how the orders go: red blinking (ingredients missing), yellow (no pattern), green (all fine).
 */
public class StockRequesterRenderer extends com.golemlogix.client.render.LegacyBer<StockRequesterBlockEntity> {
    /** Feet on the walnut top behind the lectern (pixels, the block facing north), about 6.5 pixels tall with the antenna. */
    private static final float SCALE = 0.27f, FEET_Y = 11f, FEET_Z = 14.6f;

    public StockRequesterRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final Identifier STEM = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_flower_stem.png");
    private static final Identifier PETALS = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_flower_petals.png");
    private static final Identifier PAGE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_page.png");
    /** A page is turned every PAGE_EVERY ticks (more often while writing), the turn takes PAGE_TICKS. */
    private static final int PAGE_EVERY = 240, PAGE_TICKS = 24;

    @Override
    public void render(StockRequesterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        // the poppy shows the worst status of the ledger's orders: missing ingredients (red) > no pattern (yellow) > fine (green)
        boolean writing = false, missing = false, noPattern = false;
        for (int i = 0; i < StockRequesterBlockEntity.FILTER_SLOTS; i++) {
            int st = be.getSlotStatus(i);
            writing |= st == StockRequesterBlockEntity.STATUS_CRAFTING;
            missing |= st == StockRequesterBlockEntity.STATUS_MISSING_INGREDIENTS;
            noPattern |= st == StockRequesterBlockEntity.STATUS_NO_PATTERN;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        float seed = (be.getBlockPos().hashCode() & 255) * 0.37f;
        int every = writing ? PAGE_EVERY / 2 : PAGE_EVERY;
        float inTurn = (time + seed * 7) % every;
        float turn = inTurn < PAGE_TICKS ? inTurn / PAGE_TICKS : -1f;
        CopperGolemPuppet.Arms arms;
        CopperGolemPuppet.Anim anim;
        if (turn >= 0f) {
            // one hand follows the page over from right to left, the other holds the book
            float sweep = Mth.sin(turn * Mth.PI);
            arms = new CopperGolemPuppet.Arms(-0.9f, 0.05f, -1.0f - 0.35f * sweep, -0.35f + 0.75f * turn, 0f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.3f);
        } else if (writing) {
            // the quill hand scribbles over the page, the other holds the book open
            float s = Mth.sin(time * 0.9f) * 0.12f;
            arms = new CopperGolemPuppet.Arms(-1.05f + s, 0.05f + Mth.cos(time * 0.9f) * 0.05f, -0.85f, -0.1f, 0f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.35f);
        } else {
            arms = new CopperGolemPuppet.Arms(-0.1f, 0.12f, -0.1f, -0.12f, Mth.sin((time + seed * 40) * 0.02f) * 0.7f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f);
        }
        int color = missing ? 0xE02420 : noPattern ? 0xF0C020 : 0x7CFF3A;
        // red blinks fast, yellow slower, green only breathes
        float glow;
        if (missing || noPattern) {
            int period = missing ? 10 : 24;
            glow = (time % period) < period * 0.5f ? 1f : 0.35f;
        } else {
            glow = 0.75f + 0.25f * Mth.sin(time * 0.08f);
        }
        int tint = shade(color, glow);
        int flowerLight = glow > 0.9f ? net.minecraft.util.LightCoordsUtil.FULL_BRIGHT : -1;
        pose.pushPose();
        FormationBusRenderer.orient(pose, facing);
        int lit = FormationBusRenderer.light(be.getLevel(), be.getBlockPos(), facing);
        CopperGolemPuppet.render(pose, buffers, lit, new Vector3f(0.5f, FEET_Y / 16f, FEET_Z / 16f), new Vector3f(0f, 0f, -1f),
                new Vector3f(0f, 1f, 0f), SCALE, anim, 0, null, arms, head -> {
                    PoseStack.Pose p = head.last();
                    cross(buffers.getBuffer(RenderTypes.entityCutout(STEM)), p, 0xFFFFFFFF, lit);
                    cross(buffers.getBuffer(RenderTypes.entityCutout(PETALS)), p, tint, flowerLight < 0 ? lit : flowerLight);
                });
        if (turn >= 0f) {
            page(pose, buffers.getBuffer(RenderTypes.entityCutout(PAGE)), turn, lit);
        }
        pose.popPose();
    }

    /** The poppy on top of the antenna's ball (model pixels, Y down; the ball's top is y = -13): two quads crossed at 45 degrees, 8 by 8. */
    private static void cross(VertexConsumer vc, PoseStack.Pose p, int argb, int light) {
        float cx = 0f, cz = 0f, h = 2.83f / 16f, y0 = -12.6f / 16f, y1 = -20.6f / 16f;
        float[][] planes = {{h, h}, {h, -h}};
        for (float[] d : planes) {
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? 1 : -1;
                vert(vc, p, cx - d[0] * sx, y1, cz - d[1] * sx, 0, 0, argb, light);
                vert(vc, p, cx - d[0] * sx, y0, cz - d[1] * sx, 0, 1, argb, light);
                vert(vc, p, cx + d[0] * sx, y0, cz + d[1] * sx, 1, 1, argb, light);
                vert(vc, p, cx + d[0] * sx, y1, cz + d[1] * sx, 1, 0, argb, light);
            }
        }
    }

    /**
     * The turning page: the right half of the open ledger lifts about the spine (x = 8 on the book's top) and lies down on the left.
     * The book is the model's lectern part, tilted 22.5° about X round (8, 11, 8); its top is y = 12.5, z 3.5..12.5.
     */
    private static void page(PoseStack pose, VertexConsumer vc, float turn, int light) {
        pose.pushPose();
        pose.translate(8f / 16f, 11f / 16f, 8f / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(22.5f));
        pose.translate(0f, 1.56f / 16f, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(180f * turn));
        PoseStack.Pose p = pose.last();
        float w = 5.2f / 16f, z0 = -4.2f / 16f, z1 = 4.2f / 16f;
        vert(vc, p, 0, 0, z0, 0, 0, 0xFFFFFFFF, light);
        vert(vc, p, 0, 0, z1, 0, 1, 0xFFFFFFFF, light);
        vert(vc, p, w, 0, z1, 1, 1, 0xFFFFFFFF, light);
        vert(vc, p, w, 0, z0, 1, 0, 0xFFFFFFFF, light);
        vert(vc, p, w, 0, z0, 1, 0, 0xFFFFFFFF, light);
        vert(vc, p, w, 0, z1, 1, 1, 0xFFFFFFFF, light);
        vert(vc, p, 0, 0, z1, 0, 1, 0xFFFFFFFF, light);
        vert(vc, p, 0, 0, z0, 0, 0, 0xFFFFFFFF, light);
        pose.popPose();
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int argb, int light) {
        vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 1f, 0f);
    }

    private static int shade(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
