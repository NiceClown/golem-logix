package com.golemlogix.client.render;

import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared pixel-space ({@code 1/16} block) geometry primitives, block-atlas lookups, and colour/easing helpers used across
 * procedural block-entity renderers (mainframe, catalog, jars, reactor, tower, mixer, telegraph).
 * <p>
 * All coordinates passed to {@link #box}, {@link #wall}, {@link #fitWall}, {@link #limb}, and {@link #quad} are in model pixels
 * (16 pixels = 1 block). Every face tiles its block sprite every 16 pixels unless explicitly stretched with {@link #fitWall}.
 */
public final class PixelMesh {
    public static final float PIXEL = 1f / 16f;

    private static final Vector3f SCRATCH_LIMB = new Vector3f();
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final PoseStack LIMB_POSE = new PoseStack();

    private PixelMesh() {
    }

    /** Block texture id in this mod's namespace ({@code golem_logix:block/<name>}). */
    public static ResourceLocation modBlock(String name) {
        return ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name);
    }

    /** Block texture id in the vanilla namespace ({@code minecraft:block/<name>}). */
    public static ResourceLocation mcBlock(String name) {
        return ResourceLocation.withDefaultNamespace("block/" + name);
    }

    /** Looks up a sprite on the block atlas. */
    public static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(id);
    }

    /** Standard cubic Hermite smoothstep ({@code 3t^2 - 2t^3}) for {@code t} in {@code [0, 1]}. */
    public static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Packs clamped linear RGB channels ({@code 0..1}) into an opaque ARGB colour. */
    public static int rgb(float r, float g, float b) {
        return 0xFF000000 | ((int) (Mth.clamp(r, 0f, 1f) * 255) << 16)
                | ((int) (Mth.clamp(g, 0f, 1f) * 255) << 8)
                | (int) (Mth.clamp(b, 0f, 1f) * 255);
    }

    /** Scales the RGB channels of a 24-bit colour by {@code scale} and returns an opaque ARGB colour. */
    public static int scaledRgb(int rgb, float scale) {
        int r = (int) (((rgb >> 16) & 255) * scale);
        int g = (int) (((rgb >> 8) & 255) * scale);
        int b = (int) ((rgb & 255) * scale);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A flat wall in the {@code z} plane (facing {@code -Z}), with the texture tiled every 16 pixels. */
    public static void wall(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
                            ResourceLocation tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        for (float x = x0; x < x1 - 1e-3f; x += 16f) {
            float xe = Math.min(x1, x + 16f);
            float u0 = s.getU((x - x0) % 16f / 16f), u1 = s.getU(((x - x0) % 16f + (xe - x)) / 16f);
            for (float y = y0; y < y1 - 1e-3f; y += 16f) {
                float ye = Math.min(y1, y + 16f);
                float v0 = s.getV(1f - ((y - y0) % 16f + (ye - y)) / 16f), v1 = s.getV(1f - (y - y0) % 16f / 16f);
                quad(vc, p, xe, y, z, x, y, z, x, ye, z, xe, ye, z, u0, v0, u1, v1, color, light, 0, 0, -1);
            }
        }
    }

    /** A wall facing {@code -Z} with the whole texture squeezed to its size (dials, gauges, rune plates). */
    public static void fitWall(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
                               ResourceLocation tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        quad(vc, p, x1, y0, z, x0, y0, z, x0, y1, z, x1, y1, z,
                s.getU0(), s.getV0(), s.getU1(), s.getV1(), color, light, 0, 0, -1);
    }

    /** A box in pixels; every face takes a piece of the texture by its own size (over 16 pixels it tiles). */
    public static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                           ResourceLocation tex, int color, int light) {
        box(vc, p, x0, y0, z0, x1, y1, z1, sprite(tex), color, light);
    }

    /** Sprite-pre-fetched form of {@link #box}: avoids repeated atlas lookups inside tight loops. */
    public static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                           TextureAtlasSprite s, int color, int light) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        // north (-Z) and south (+Z)
        tileFace(vc, p, s, color, light, dx, dy, x1, y0, z0, -1, 0, 0, 0, 1, 0, 0, 0, -1);
        tileFace(vc, p, s, color, light, dx, dy, x0, y0, z1, 1, 0, 0, 0, 1, 0, 0, 0, 1);
        // west (-X) and east (+X)
        tileFace(vc, p, s, color, light, dz, dy, x0, y0, z0, 0, 0, 1, 0, 1, 0, -1, 0, 0);
        tileFace(vc, p, s, color, light, dz, dy, x1, y0, z1, 0, 0, -1, 0, 1, 0, 1, 0, 0);
        // top and bottom
        tileFace(vc, p, s, color, light, dx, dz, x0, y1, z1, 1, 0, 0, 0, 0, -1, 0, 1, 0);
        tileFace(vc, p, s, color, light, dx, dz, x0, y0, z0, 1, 0, 0, 0, 0, 1, 0, -1, 0);
    }

    /** A box optionally mirrored across {@code x = 8} (the centre of a 16-pixel block) for symmetric side assemblies. */
    public static void mbox(VertexConsumer vc, PoseStack pose, boolean mirror, float x0, float y0, float z0, float x1, float y1, float z1,
                            ResourceLocation tex, int color, int light) {
        float a = mirror ? 16f - x1 : x0, b = mirror ? 16f - x0 : x1;
        box(vc, pose.last(), a, y0, z0, b, y1, z1, tex, color, light);
    }

    /** A strut or limb: a bar of thickness {@code t} from {@code from} to {@code to} (pixels). */
    public static void limb(VertexConsumer vc, PoseStack pose, Vector3f from, Vector3f to, float t, ResourceLocation tex, int light) {
        limb(vc, pose, from.x, from.y, from.z, to.x, to.y, to.z, t, tex, light);
    }

    /** Scalar form of {@link #limb}: avoids temporary Vector3f/Quaternionf/PoseStack.pushPose allocations. */
    public static void limb(VertexConsumer vc, PoseStack pose, float fx, float fy, float fz, float tx, float ty, float tz, float t,
                            ResourceLocation tex, int light) {
        Vector3f d = SCRATCH_LIMB.set(tx - fx, ty - fy, tz - fz);
        float len = d.length();
        if (len < 1e-3f) {
            return;
        }
        d.div(len);
        SCRATCH_QUAT.rotationTo(0f, 1f, 0f, d.x, d.y, d.z);
        PoseStack.Pose lp = LIMB_POSE.last();
        lp.pose().set(pose.last().pose()).translate(fx * PIXEL, fy * PIXEL, fz * PIXEL).rotate(SCRATCH_QUAT);
        lp.normal().set(pose.last().normal()).rotate(SCRATCH_QUAT);
        box(vc, lp, -t / 2f, -t / 4f, -t / 2f, t / 2f, len + t / 4f, t / 2f, tex, 0xFFFFFFFF, light);
    }

    /** A {@code w x h} face (pixels) in tiles of 16 along affine basis {@code o + a*da + b*db}. */
    private static void tileFace(VertexConsumer vc, PoseStack.Pose p, TextureAtlasSprite s, int color, int light, float w, float h,
                                 float ox, float oy, float oz, float dax, float day, float daz, float dbx, float dby, float dbz,
                                 float nx, float ny, float nz) {
        float u0 = s.getU(0f);
        float v1 = s.getV(1f);
        for (float a = 0; a < w - 1e-3f; a += 16f) {
            float ae = Math.min(w, a + 16f);
            float u1 = s.getU((ae - a) / 16f);
            float ax0 = ox + dax * a, ay0 = oy + day * a, az0 = oz + daz * a;
            float ax1 = ox + dax * ae, ay1 = oy + day * ae, az1 = oz + daz * ae;
            for (float b = 0; b < h - 1e-3f; b += 16f) {
                float be = Math.min(h, b + 16f);
                float v0 = s.getV(1f - (be - b) / 16f);
                quad(vc, p,
                        ax0 + dbx * b, ay0 + dby * b, az0 + dbz * b,
                        ax1 + dbx * b, ay1 + dby * b, az1 + dbz * b,
                        ax1 + dbx * be, ay1 + dby * be, az1 + dbz * be,
                        ax0 + dbx * be, ay0 + dby * be, az0 + dbz * be,
                        u0, v0, u1, v1, color, light, nx, ny, nz);
            }
        }
    }

    /** {@code a} = bottom left, {@code b} = bottom right, {@code c} = top right, {@code d} = top left (from outside); coordinates in pixels. */
    public static void quad(VertexConsumer vc, PoseStack.Pose p,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float cx, float cy, float cz, float dx, float dy, float dz,
                            float u0, float v0, float u1, float v1, int color, int light, float nx, float ny, float nz) {
        Matrix4f m = p.pose();
        float m00 = m.m00() * PIXEL, m01 = m.m01() * PIXEL, m02 = m.m02() * PIXEL;
        float m10 = m.m10() * PIXEL, m11 = m.m11() * PIXEL, m12 = m.m12() * PIXEL;
        float m20 = m.m20() * PIXEL, m21 = m.m21() * PIXEL, m22 = m.m22() * PIXEL;
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        Matrix3f nm = p.normal();
        float tnx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
        float tny = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
        float tnz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
        float lenSq = tnx * tnx + tny * tny + tnz * tnz;
        if (Math.abs(lenSq - 1f) > 1e-3f && lenSq > 1e-8f) {
            float inv = Mth.invSqrt(lenSq);
            tnx *= inv;
            tny *= inv;
            tnz *= inv;
        }
        int ov = OverlayTexture.NO_OVERLAY;
        vc.addVertex(m00 * ax + m10 * ay + m20 * az + m30, m01 * ax + m11 * ay + m21 * az + m31, m02 * ax + m12 * ay + m22 * az + m32)
                .setColor(color).setUv(u0, v1).setOverlay(ov).setLight(light).setNormal(tnx, tny, tnz);
        vc.addVertex(m00 * bx + m10 * by + m20 * bz + m30, m01 * bx + m11 * by + m21 * bz + m31, m02 * bx + m12 * by + m22 * bz + m32)
                .setColor(color).setUv(u1, v1).setOverlay(ov).setLight(light).setNormal(tnx, tny, tnz);
        vc.addVertex(m00 * cx + m10 * cy + m20 * cz + m30, m01 * cx + m11 * cy + m21 * cz + m31, m02 * cx + m12 * cy + m22 * cz + m32)
                .setColor(color).setUv(u1, v0).setOverlay(ov).setLight(light).setNormal(tnx, tny, tnz);
        vc.addVertex(m00 * dx + m10 * dy + m20 * dz + m30, m01 * dx + m11 * dy + m21 * dz + m31, m02 * dx + m12 * dy + m22 * dz + m32)
                .setColor(color).setUv(u0, v0).setOverlay(ov).setLight(light).setNormal(tnx, tny, tnz);
    }
}
