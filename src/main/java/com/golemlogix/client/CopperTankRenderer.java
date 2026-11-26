package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CopperTankBlockEntity;
import com.golemlogix.client.render.LegacyBer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Copper Tank: the fluid behind the glass, as high as the tank is full (the copper frame and the glass are the baked model). A
 * fluid lighter than air (a gas) hangs from the lid instead; a glowing one (lava) is drawn at full brightness. The needle of the
 * gauge on the front post shows how full it is: from the dial's lower left (empty) round to its lower right (full).
 */
public class CopperTankRenderer extends LegacyBer<CopperTankBlockEntity> {
    /** The inside of the tank (pixels): within the glass chamber (2..14), between the base (top at 3.5) and the lid (underside at 12.5). */
    private static final float IN0 = 2.2f, IN1 = 13.8f, BOTTOM = 3.55f, TOP = 12.45f;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/bay_white.png");
    /** The gauge's face (pixels): centered on BOTH north (z = 1.35) and south (z = 14.65) glass walls so the dial is always readable. */
    private static final float GAUGE_X = 8.0f, GAUGE_Y = 8.0f, GAUGE_Z_N = 1.24f, GAUGE_Z_S = 14.76f;

    public CopperTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Needles on both north and south dials: from empty (lower left) around top to full (lower right). */
    private static void needle(PoseStack.Pose p, MultiBufferSource buffers, float fill, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(WHITE));
        double a = Math.toRadians(150 + 240 * fill);
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        float len = 1.15f, w = 0.15f;
        // North face: texture u -> -x, v -> -y
        float dxN = -cos, dyN = -sin;
        float nxN = -dyN * w, nyN = dxN * w;
        float zN = GAUGE_Z_N / 16f;
        needleVert(vc, p, (GAUGE_X - nxN) / 16f, (GAUGE_Y - nyN) / 16f, zN, -1f, light);
        needleVert(vc, p, (GAUGE_X + nxN) / 16f, (GAUGE_Y + nyN) / 16f, zN, -1f, light);
        needleVert(vc, p, (GAUGE_X + dxN * len + nxN) / 16f, (GAUGE_Y + dyN * len + nyN) / 16f, zN, -1f, light);
        needleVert(vc, p, (GAUGE_X + dxN * len - nxN) / 16f, (GAUGE_Y + dyN * len - nyN) / 16f, zN, -1f, light);
        // South face: mirrored u -> +x, v -> -y
        float dxS = cos, dyS = -sin;
        float nxS = -dyS * w, nyS = dxS * w;
        float zS = GAUGE_Z_S / 16f;
        needleVert(vc, p, (GAUGE_X + dxS * len - nxS) / 16f, (GAUGE_Y + dyS * len - nyS) / 16f, zS, 1f, light);
        needleVert(vc, p, (GAUGE_X + dxS * len + nxS) / 16f, (GAUGE_Y + dyS * len + nyS) / 16f, zS, 1f, light);
        needleVert(vc, p, (GAUGE_X + nxS) / 16f, (GAUGE_Y + nyS) / 16f, zS, 1f, light);
        needleVert(vc, p, (GAUGE_X - nxS) / 16f, (GAUGE_Y - nyS) / 16f, zS, 1f, light);
    }

    private static void needleVert(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float nz, int light) {
        vc.addVertex(p, x, y, z).setColor(0xFFC8281E).setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0f, 0f, nz);
    }

    @Override
    public void render(CopperTankBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        FluidStack fluid = be.fluid();
        float fill = fluid.isEmpty() ? 0f : Math.min(1f, fluid.getAmount() / (float) Math.max(1, be.capacity()));
        needle(pose.last(), buffers, fill, light);
        if (fluid.isEmpty()) {
            return;
        }
        float height = Math.max(0.3f, (TOP - BOTTOM) * fill);
        boolean gas = fluid.getFluidType().isLighterThanAir();
        float y0 = gas ? TOP - height : BOTTOM, y1 = gas ? TOP : BOTTOM + height;
        int tint = FluidIcons.tint(fluid);
        if ((tint >>> 24) == 0) {
            tint |= 0xFF000000;
        }
        int lit = fluid.getFluidType().getLightLevel(fluid) > 0 ? LightCoordsUtil.FULL_BRIGHT : light;
        TextureAtlasSprite s = FluidIcons.still(fluid);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();
        float span = IN1 - IN0;
        float u0 = s.getU(0f), u1 = s.getU(span / 16f);
        float v0 = s.getV(0f), vSide = s.getV((y1 - y0) / 16f), vCap = s.getV(span / 16f);
        float i0 = IN0 / 16f, i1 = IN1 / 16f, b0 = y0 / 16f, b1 = y1 / 16f;
        // the four sides, the top and (seen through the base's gap only when lifted) the bottom
        face(vc, p, i1, b0, i0, i0, b0, i0, i0, b1, i0, i1, b1, i0, u0, u1, v0, vSide, tint, lit, 0f, 0f, -1f);
        face(vc, p, i0, b0, i1, i1, b0, i1, i1, b1, i1, i0, b1, i1, u0, u1, v0, vSide, tint, lit, 0f, 0f, 1f);
        face(vc, p, i0, b0, i0, i0, b0, i1, i0, b1, i1, i0, b1, i0, u0, u1, v0, vSide, tint, lit, -1f, 0f, 0f);
        face(vc, p, i1, b0, i1, i1, b0, i0, i1, b1, i0, i1, b1, i1, u0, u1, v0, vSide, tint, lit, 1f, 0f, 0f);
        face(vc, p, i0, b1, i1, i1, b1, i1, i1, b1, i0, i0, b1, i0, u0, u1, v0, vCap, tint, lit, 0f, 1f, 0f);
        face(vc, p, i0, b0, i0, i1, b0, i0, i1, b0, i1, i0, b0, i1, u0, u1, v0, vCap, tint, lit, 0f, -1f, 0f);
    }

    /**
     * One face (counter-clockwise seen from outside, starting bottom left), the sprite laid on it at one pixel a
     * texel so a low level shows a strip of the texture rather than all of it squeezed.
     */
    private static void face(VertexConsumer vc, PoseStack.Pose p,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float u1, float v0, float v1, int color, int light, float nx, float ny, float nz) {
        vc.addVertex(p, ax, ay, az).setColor(color).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, bx, by, bz).setColor(color).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, cx, cy, cz).setColor(color).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
        vc.addVertex(p, dx, dy, dz).setColor(color).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }
}
