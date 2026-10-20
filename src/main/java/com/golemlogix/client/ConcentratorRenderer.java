package com.golemlogix.client;

import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.SchematicConcentratorBlockEntity;
import com.golemlogix.blocks.SchematicConcentratorBlock;
import com.golemlogix.client.render.GlowTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Cryo Schematic Concentrator hologram: teal rings with a schematic slowly rotate on the round front screen; while the window
 * is open, glowing "punch cards" fan out of the screen and the rings spin faster. Everything is drawn glowing ({@link GlowTypes}: added
 * to the light, textures with a black background instead of transparency).
 */
public class ConcentratorRenderer extends com.golemlogix.client.render.LegacyBer<SchematicConcentratorBlockEntity> {
    private static final Identifier HOLO = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/concentrator_holo.png"),
            CARD = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/concentrator_card.png");

    public ConcentratorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(SchematicConcentratorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.6);
    }

    @Override
    public void render(SchematicConcentratorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.getBlockState().hasProperty(SchematicConcentratorBlock.FACING)) {
            return;
        }
        Direction facing = be.getBlockState().getValue(SchematicConcentratorBlock.FACING);
        float holo = Mth.lerp(partialTick, be.holoPrev, be.holo);
        holo = holo * holo * (3 - 2 * holo);
        float spin = Mth.lerp(partialTick, be.spinPrev, be.spin);
        float time = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
        pose.pushPose();
        Quads.faceModel(pose, facing);
        // screen centre on the front (pixels): x 8, y 9.3, just in front of the glass
        pose.translate(8f / 16f, 9.3f / 16f, -0.05f / 16f);
        VertexConsumer vc = buffers.getBuffer(GlowTypes.glow(HOLO));
        float bright = 0.55f + 0.45f * holo + 0.05f * Mth.sin(time * 0.15f);
        // schematic rings: two layers rotating against each other
        disc(vc, pose, 5.0f, spin, bright, 0f);
        disc(vc, pose, 3.7f, -spin * 1.7f, bright * 0.7f, -0.12f);
        // fan of holographic cards in front of the screen
        if (holo > 0.01f) {
            VertexConsumer cards = buffers.getBuffer(GlowTypes.glow(CARD));
            for (int k = 0; k < 5; k++) {
                float a = (k - 2) * 24f * holo;
                float bob = Mth.sin(time * 0.12f + k * 1.3f) * 0.4f;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(a));
                pose.translate(0f, (1.2f + bob + Math.abs(k - 2) * -0.6f) / 16f * holo, (-1.5f - 5.5f * holo) / 16f);
                pose.mulPose(Axis.XP.rotationDegrees(-8f));
                float s = holo;
                quad(cards, pose, -2.2f * s, -3f * s, 2.2f * s, 3f * s, 0f, 0f, 1f, 1f, holo * (k == 2 ? 1f : 0.75f));
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static void disc(VertexConsumer vc, PoseStack pose, float r, float angle, float bright, float dz) {
        pose.pushPose();
        pose.translate(0f, 0f, dz / 16f);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        quad(vc, pose, -r, -r, r, r, 0f, 0f, 1f, 1f, bright);
        pose.popPose();
    }

    /** A square facing -Z (towards someone looking at the front), in pixels; brightness multiplies the colour ("eyes" have no transparency). */
    private static void quad(VertexConsumer vc, PoseStack pose, float x0, float y0, float x1, float y1,
                             float u0, float v0, float u1, float v1, float bright) {
        int c = Math.max(0, Math.min(255, (int) (255 * bright)));
        int color = 0xFF000000 | (c << 16) | (c << 8) | c;
        PoseStack.Pose p = pose.last();
        vertex(vc, p, x1 / 16f, y0 / 16f, u0, v1, color);
        vertex(vc, p, x0 / 16f, y0 / 16f, u1, v1, color);
        vertex(vc, p, x0 / 16f, y1 / 16f, u1, v0, color);
        vertex(vc, p, x1 / 16f, y1 / 16f, u0, v0, color);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float u, float v, int color) {
        vc.addVertex(p, x, y, 0f).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(p, 0f, 0f, -1f);
    }
}
