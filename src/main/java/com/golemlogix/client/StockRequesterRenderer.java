package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

/**
 * Copper Order Ledger: the Storekeeper, a small copper golem standing behind the lectern. While an order of the ledger is being
 * crafted it bends over the book and scribbles; otherwise it waits, now and then looking round the workshop. Now and then it turns
 * a page. A poppy on its head shows how the orders go: red blinking (ingredients missing), yellow (no pattern), green (all fine).
 */
public class StockRequesterRenderer implements BlockEntityRenderer<StockRequesterBlockEntity> {
    /** Feet on the walnut top behind the lectern (pixels, the block facing north), about 6.5 pixels tall with the antenna. */
    private static final float SCALE = 0.27f, FEET_Y = 11f, FEET_Z = 14.6f;

    public StockRequesterRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static final ResourceLocation STEM = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_flower_stem.png");
    private static final ResourceLocation PETALS = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_flower_petals.png");
    private static final ResourceLocation PAGE = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/dev_ledger_page.png");
    /** A page is turned every PAGE_EVERY ticks (more often while writing), the turn takes PAGE_TICKS. */
    private static final int PAGE_EVERY = 240, PAGE_TICKS = 24;
    /** Beyond this (blocks) the pictures on the pages are too small to make out and are not drawn. */
    private static final double PAGE_SIGHT = 24.0;

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
        int flowerLight = glow > 0.9f || !(missing || noPattern) ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : -1;
        pose.pushPose();
        FormationBusRenderer.orient(pose, facing);
        int lit = FormationBusRenderer.light(be.getLevel(), be.getBlockPos(), facing);
        CopperGolemPuppet.render(pose, buffers, lit, new Vector3f(0.5f, FEET_Y / 16f, FEET_Z / 16f), new Vector3f(0f, 0f, -1f),
                new Vector3f(0f, 1f, 0f), SCALE, anim, 0, null, arms, head -> {
                    PoseStack.Pose p = head.last();
                    cross(buffers.getBuffer(RenderType.entityCutoutNoCull(STEM)), p, 0xFFFFFFFF, lit);
                    cross(buffers.getBuffer(RenderType.entityCutoutNoCull(PETALS)), p, tint, flowerLight < 0 ? lit : flowerLight);
                });
        if (turn >= 0f) {
            page(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(PAGE)), turn, lit);
        }
        if (pagesInSight(be, facing)) {
            pageItems(be, pose, buffers, lit);
        }
        pose.popPose();
    }

    /**
     * Whether the open pages can be seen from the camera: near enough to make out the pictures, and on the side the pages face (the
     * lectern's tilt turns them towards the back of the block).
     */
    private static boolean pagesInSight(StockRequesterBlockEntity be, Direction facing) {
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        net.minecraft.world.phys.Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = camera.x - (pos.getX() + 0.5), dy = camera.y - (pos.getY() + 12.6 / 16.0), dz = camera.z - (pos.getZ() + 0.5);
        double dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 > PAGE_SIGHT * PAGE_SIGHT) {
            return false;
        }
        Direction back = facing.getOpposite();
        double sin = Math.sin(Math.toRadians(22.5)), cos = Math.cos(Math.toRadians(22.5));
        return dy * cos + (dx * back.getStepX() + dz * back.getStepZ()) * sin > 0.0;
    }

    /**
     * What the ledger keeps, drawn flat on the open pages like the picture on a storage drawer: half on each page, as large as the
     * count allows. The frame is the lectern's part of the model, tilted 22.5 degrees about X round (8, 11, 8), its top y = 12.5.
     */
    private static void pageItems(StockRequesterBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light) {
        java.util.List<net.minecraft.world.item.ItemStack> kept = new java.util.ArrayList<>();
        for (int i = 0; i < StockRequesterBlockEntity.FILTER_SLOTS; i++) {
            net.minecraft.world.item.ItemStack stack = be.getFilters().getItem(i);
            if (!stack.isEmpty()) {
                kept.add(stack);
            }
        }
        int n = kept.size();
        if (n == 0) {
            return;
        }
        int rows = n <= 2 ? 1 : n <= 4 ? 2 : 3, cols = n <= 6 ? 1 : 2;
        float size = n <= 2 ? 4.4f : n <= 4 ? 3.6f : n <= 6 ? 2.6f : 2.2f;
        int perPage = rows * cols, onLeft = (n + 1) / 2;
        pose.pushPose();
        pose.translate(8f / 16f, 11f / 16f, 8f / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(22.5f));
        pose.translate(0f, 1.53f / 16f, 0f);
        for (int i = 0; i < n; i++) {
            boolean left = i < onLeft;
            int at = left ? i : i - onLeft;
            int row = Math.min(at / cols, rows - 1), col = at % cols;
            float pageX = left ? -2.9f : 2.5f;
            float x = cols == 1 ? pageX : pageX + (col == 0 ? -1.2f : 1.2f);
            float z = rows == 1 ? 0f : (row - (rows - 1) / 2f) * (rows == 2 ? 3.9f : 2.75f);
            pose.pushPose();
            pose.translate(x / 16f, 0f, z / 16f);
            // the picture lies on the page, its top towards the far edge for a reader at the back of the block
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
            float s = size / 16f;
            pose.scale(s, s, 0.004f);
            Minecraft.getInstance().getItemRenderer().renderStatic(kept.get(i), net.minecraft.world.item.ItemDisplayContext.GUI, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }
        pose.popPose();
    }

    /** The poppy on top of the antenna's ball (model pixels, Y down; the ball's top is y = -13): two quads crossed at 45 degrees, 8 by 8. */
    private static void cross(VertexConsumer vc, PoseStack.Pose p, int argb, int light) {
        float cx = 0f, cz = 0f, h = 2.83f / 16f, y0 = -12.6f / 16f, y1 = -20.6f / 16f;
        float[][] planes = {{h, h}, {h, -h}};
        for (float[] d : planes) {
            // one quad a plane: this render type draws both sides, a second quad on top of the first would flicker
            vert(vc, p, cx - d[0], y1, cz - d[1], 0, 0, argb, light);
            vert(vc, p, cx - d[0], y0, cz - d[1], 0, 1, argb, light);
            vert(vc, p, cx + d[0], y0, cz + d[1], 1, 1, argb, light);
            vert(vc, p, cx + d[0], y1, cz + d[1], 1, 0, argb, light);
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
        pose.translate(0f, 1.6f / 16f, 0f);
        pose.mulPose(Axis.ZP.rotationDegrees(180f * turn));
        PoseStack.Pose p = pose.last();
        float w = 5.2f / 16f, z0 = -4.2f / 16f, z1 = 4.2f / 16f;
        // lit as if it lay flat all the way over: paper does not go dark while it turns
        float a = Mth.PI * turn, nx = Mth.sin(a), ny = Mth.cos(a);
        float[][] corners = {{0, z0, 0, 0}, {0, z1, 0, 1}, {w, z1, 1, 1}, {w, z0, 1, 0}};
        for (float[] c : corners) {
            vc.addVertex(p, c[0], 0, c[1]).setColor(0xFFFFFFFF).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, 0f);
        }
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
