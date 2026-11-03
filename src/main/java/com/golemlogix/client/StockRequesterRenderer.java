package com.golemlogix.client;

import com.golemlogix.blockentity.StockRequesterBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

/**
 * Copper Order Ledger: the Storekeeper, a small copper golem standing behind the lectern. While an order of the ledger is being
 * crafted it bends over the book and scribbles; otherwise it waits, now and then looking round the workshop.
 */
public class StockRequesterRenderer extends com.golemlogix.client.render.LegacyBer<StockRequesterBlockEntity> {
    /** Feet on the walnut top behind the lectern (pixels, the block facing north), about 6.5 pixels tall with the antenna. */
    private static final float SCALE = 0.27f, FEET_Y = 11f, FEET_Z = 14.6f;

    public StockRequesterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StockRequesterBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || !be.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean writing = false;
        for (int i = 0; i < StockRequesterBlockEntity.FILTER_SLOTS; i++) {
            writing |= be.getSlotStatus(i) == StockRequesterBlockEntity.STATUS_CRAFTING;
        }
        float time = be.getLevel().getGameTime() + partialTick;
        float seed = (be.getBlockPos().hashCode() & 255) * 0.37f;
        CopperGolemPuppet.Arms arms;
        CopperGolemPuppet.Anim anim;
        if (writing) {
            // the quill hand scribbles over the page, the other holds the book open
            float s = Mth.sin(time * 0.9f) * 0.12f;
            arms = new CopperGolemPuppet.Arms(-1.05f + s, 0.05f + Mth.cos(time * 0.9f) * 0.05f, -0.85f, -0.1f, 0f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.35f);
        } else {
            arms = new CopperGolemPuppet.Arms(-0.1f, 0.12f, -0.1f, -0.12f, Mth.sin((time + seed * 40) * 0.02f) * 0.7f);
            anim = new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f);
        }
        pose.pushPose();
        FormationBusRenderer.orient(pose, facing);
        CopperGolemPuppet.render(pose, buffers, FormationBusRenderer.light(be.getLevel(), be.getBlockPos(), facing),
                new Vector3f(0.5f, FEET_Y / 16f, FEET_Z / 16f), new Vector3f(0f, 0f, -1f), new Vector3f(0f, 1f, 0f), SCALE, anim, 0, null, arms);
        pose.popPose();
    }
}
