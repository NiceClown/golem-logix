package com.golemlogix.client;

import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.client.render.Baked;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.CRYO;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.DIM_BRASS;
import static com.golemlogix.client.MachineParts.FRAME;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.LIGHT_BRASS;
import static com.golemlogix.client.MachineParts.RUNES;
import static com.golemlogix.client.MachineParts.SNOW;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.limb;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.quad;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * Dispatcher, shared baked mesh cache, Iris shader-pack probe, and shared structural primitives for the joined jar multiblocks:
 * <ul>
 * <li>2x2 Giga Jar ({@link GigaJarRenderer})</li>
 * <li>3x3 Antigravity Reactor ({@link AntigravityReactorRenderer})</li>
 * </ul>
 * Static geometry and turning rings are baked once per atlas reload via {@link Baked}. Past {@link #DETAIL_SIGHT} small
 * fittings are dropped; past {@link #CREW_SIGHT} the animated golems are skipped.
 * <p>
 * Translucent parts: {@code entityTranslucentEmissive} writes no depth, so things behind a tank remain visible; under an
 * active Iris shader pack that type washes out, so Source switches to plain {@code entityTranslucent} and outer glass is skipped.
 */
final class JarWholeRenderer {
    static final float REACTOR_TOP = 31.4f;
    static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    static final Vector3f FWD_EAST = new Vector3f(1f, 0f, 0f);
    static final Vector3f FWD_WEST = new Vector3f(-1f, 0f, 0f);
    static final Vector3f FWD_SOUTH = new Vector3f(0f, 0f, 1f);

    private static final int CREW_SIGHT = 48, DETAIL_SIGHT = 32;
    private static final int SOURCE_GLOW = 8;
    private static final ResourceLocation GRATE = mcBlock("iron_trapdoor");
    private static final BlockPos.MutableBlockPos LIGHT_PROBE = new BlockPos.MutableBlockPos();

    /** Pre-baked meshes per atlas generation: near/far hulls, rune bands, reactor rings, and unit Source/ice spheres. */
    record Meshes(TextureAtlasSprite atlasMark, Baked gigaNear, Baked gigaFar, Baked gigaRunes,
                  Baked reactorNear, Baked reactorFar, Baked reactorRunes, Baked collar, Baked lowRing,
                  Baked outerRing, Baked outerRunes, Baked levelRing, Baked levelRingFar, Baked innerRing,
                  Baked ballCryo, Baked ballIce) {
    }

    private static Meshes meshes;

    private static volatile boolean irisChecked;
    private static MethodHandle irisInUse;
    private static boolean irisValueCached;
    private static long irisNextCheckNanos;
    private static boolean cachedIrisActive;

    private JarWholeRenderer() {
    }

    static void render(CryoJarBlockEntity be, CryoJarRenderer.IronGolemParts ironGolem, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        JarForm form = be.form();
        int width = form.width();
        Direction facing = be.getBlockState().getValue(CryoJarBlock.FACING);
        int around = machineLight(level, pos, width);
        // glowing Source lights the machine around it even in deep darkness
        int light = be.clientSource() > 0 ? LightTexture.pack(Math.max(LightTexture.block(around), SOURCE_GLOW), LightTexture.sky(around)) : around;
        float fill = Math.min(1f, Mth.lerp(partialTick, be.fillPrev, be.fill));
        float time = (level.getGameTime() % 72000L) + partialTick;
        boolean charged = be.clientSource() > 0;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance = cam.distanceToSqr(pos.getX() + width / 2.0, pos.getY() + 2.0, pos.getZ() + width / 2.0);
        boolean crew = distance < CREW_SIGHT * CREW_SIGHT, detail = distance < DETAIL_SIGHT * DETAIL_SIGHT;
        // ambient dimming factor for emissive tank glass
        int skyNow = Math.max(0, LightTexture.sky(light) - level.getSkyDarken());
        float ambient = 0.45f + 0.55f * Math.max(LightTexture.block(light), skyNow) / 15f;
        Meshes baked = meshes();

        // brass pipe-docking sockets in world coordinates
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                pose.pushPose();
                pose.translate(x, 0, z);
                MachineParts.jarSockets(solid, pose.last(), level, pos.offset(x, 0, z), light);
                pose.popPose();
            }
        }
        pose.pushPose();
        // corner facing rotates the whole multiblock about its footprint centre
        pose.rotateAround(Axis.YP.rotationDegrees(180f - facing.toYRot()), width / 2f, 0f, width / 2f);
        if (form == JarForm.GIGA) {
            GigaJarRenderer.render(pose, buffers, baked, light, time, fill, charged, crew, detail, ambient);
        } else {
            AntigravityReactorRenderer.render(pose, buffers, baked, ironGolem, light, time, fill, charged, crew, detail);
        }
        pose.popPose();
    }

    /** Brightest non-solid light around the multiblock perimeter at both levels and above its footprint. */
    static int machineLight(Level level, BlockPos pos, int width) {
        int block = 0, sky = 0;
        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= width; x++) {
                for (int z = -1; z <= width; z++) {
                    boolean inside = x >= 0 && x < width && z >= 0 && z < width;
                    if (y < 2 ? inside : !inside) {
                        continue;
                    }
                    LIGHT_PROBE.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getBlockState(LIGHT_PROBE).isSolidRender(level, LIGHT_PROBE)) {
                        continue;
                    }
                    int c = LevelRenderer.getLightColor(level, LIGHT_PROBE);
                    block = Math.max(block, LightTexture.block(c));
                    sky = Math.max(sky, LightTexture.sky(c));
                }
            }
        }
        return LightTexture.pack(block, sky);
    }

    /** Whether an Iris shader pack is running now (looked up via cached MethodHandle, throttled every 100 ms; false without Iris). */
    static boolean shaderPackActive() {
        if (!irisChecked) {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                irisInUse = MethodHandles.publicLookup()
                        .unreflect(api.getMethod("isShaderPackInUse"))
                        .bindTo(instance)
                        .asType(MethodType.methodType(boolean.class));
            } catch (Throwable t) {
                irisInUse = null;
            }
            irisChecked = true;
        }
        MethodHandle handle = irisInUse;
        if (handle == null) {
            return false;
        }
        long now = System.nanoTime();
        if (irisValueCached && (now - irisNextCheckNanos) < 0L) {
            return cachedIrisActive;
        }
        boolean active;
        try {
            active = (boolean) handle.invokeExact();
        } catch (Throwable t) {
            active = false;
        }
        cachedIrisActive = active;
        irisNextCheckNanos = now + 100_000_000L;
        irisValueCached = true;
        return active;
    }

    static RenderType clearType(boolean shaderPack) {
        return shaderPack ? RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)
                : RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS);
    }

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    Baked.bake((vc, pose) -> GigaJarRenderer.bakeStatic(vc, pose, true)),
                    Baked.bake((vc, pose) -> GigaJarRenderer.bakeStatic(vc, pose, false)),
                    Baked.bake((vc, pose) -> runeBands(vc, pose, 32f, 2f, 5.4f, 0.5f)),
                    Baked.bake((vc, pose) -> AntigravityReactorRenderer.bakeStatic(vc, pose, true)),
                    Baked.bake((vc, pose) -> AntigravityReactorRenderer.bakeStatic(vc, pose, false)),
                    Baked.bake(AntigravityReactorRenderer::bakeRunes),
                    Baked.bake((vc, pose) -> {
                        ring(vc, pose, 12.4f, 1.6f, 1.2f, 24, COPPER, WHITE, 0);
                        teeth(vc, pose, 16, 13.2f, 14.6f, 0.8f, 0.6f, COPPER, 0xFFD89070);
                    }),
                    Baked.bake((vc, pose) -> {
                        ring(vc, pose, 17f, 1.8f, 1.8f, 28, BRASS, WHITE, 0);
                        teeth(vc, pose, 14, 15f, 16.2f, 0.9f, 1.1f, COPPER, WHITE);
                    }),
                    Baked.bake((vc, pose) -> ring(vc, pose, 20f, 2f, 2.4f, 32, FRAME, WHITE, 0)),
                    Baked.bake((vc, pose) -> runeRing(vc, pose, 20f, 2.5f, 32)),
                    Baked.bake((vc, pose) -> {
                        ring(vc, pose, 16.4f, 1.7f, 2f, 28, BRASS, LIGHT_BRASS, 0);
                        teeth(vc, pose, 20, 14.7f, 15.6f, 0.6f, 0.7f, BRASS, WHITE);
                        // icicles on every third tooth: ring lies in XY, underside toward +Z
                        for (int i = 0; i < 20; i += 3) {
                            pose.pushPose();
                            pose.mulPose(Axis.ZP.rotationDegrees(i * 18f));
                            box(vc, pose.last(), -0.3f, 16.1f, 1f, 0.3f, 16.7f, 1f + 1.2f + (i % 2), ICE, WHITE, 0);
                            pose.popPose();
                        }
                    }),
                    Baked.bake((vc, pose) -> ring(vc, pose, 16.4f, 1.7f, 2f, 16, BRASS, LIGHT_BRASS, 0)),
                    Baked.bake((vc, pose) -> ring(vc, pose, 13.4f, 1f, 1.2f, 24, COPPER, WHITE, 0)),
                    Baked.bake((vc, pose) -> ball(vc, pose.last(), 1f, CRYO, WHITE, 0)),
                    Baked.bake((vc, pose) -> ball(vc, pose.last(), 1f, ICE, WHITE, 0)));
        }
        return meshes;
    }

    // ------------------------------------------------------------------------------------------------ shared structural helpers

    /** Snow on the four corners of a ledge, with optional hanging icicles. */
    static void cornerSnow(VertexConsumer vc, PoseStack.Pose p, float size, float y, boolean icicles, int light) {
        for (float[] c : new float[][]{{0f, 0f}, {size - 5f, 0f}, {0f, size - 5f}, {size - 5f, size - 5f}}) {
            box(vc, p, c[0] + 0.1f, y, c[1] + 0.1f, c[0] + 4.9f, y + 0.9f, c[1] + 4.9f, SNOW, WHITE, light);
        }
        if (!icicles) {
            return;
        }
        for (float[] i : new float[][]{{0.8f, 0.2f, 1.8f}, {3.2f, 0.2f, 1.2f}, {size - 3f, 0.2f, 2f}, {size - 1.2f, 2f, 1.3f},
                {0.2f, size - 2.4f, 1.6f}, {size - 2f, size - 0.8f, 1.8f}}) {
            box(vc, p, i[0], y - i[2], i[1], i[0] + 0.6f, y, i[1] + 0.6f, ICE, WHITE, light);
        }
    }

    /** Ladder against a wall at {@code x}: two rails and rungs from {@code y0} to {@code y1} between {@code z0} and {@code z1}. */
    static void ladder(VertexConsumer vc, PoseStack.Pose p, float x, float y0, float y1, float z0, float z1, int light) {
        box(vc, p, x, y0, z0, x + 1f, y1, z0 + 0.7f, DARK, WHITE, light);
        box(vc, p, x, y0, z1 - 0.7f, x + 1f, y1, z1, DARK, WHITE, light);
        for (float y = y0 + 1.4f; y < y1; y += 2.6f) {
            box(vc, p, x + 0.2f, y, z0 + 0.4f, x + 0.8f, y + 0.5f, z1 - 0.4f, BRASS, WHITE, light);
        }
    }

    /** Grated walkway from {@code x0} to {@code x1} with outer handrail and diagonal struts down to {@code (strutX, strutY)}. */
    static void catwalk(VertexConsumer vc, PoseStack pose, float x0, float x1, float z0, float z1, float y,
                        float strutX, float strutY, int light) {
        PoseStack.Pose p = pose.last();
        box(vc, p, x0, y, z0, x1, y + 0.5f, z1, GRATE, 0xFF6E6A66, light);
        boolean right = strutX < (x0 + x1) / 2f;
        float edge = right ? x1 : x0;
        box(vc, p, edge - 0.3f, y - 0.4f, z0 - 0.2f, edge + 0.3f, y + 0.6f, z1 + 0.2f, BRASS, DIM_BRASS, light);
        for (float z : new float[]{z0 + 0.4f, z1 - 0.4f}) {
            box(vc, p, edge - 0.25f, y + 0.5f, z - 0.25f, edge + 0.25f, y + 4.4f, z + 0.25f, BRASS, WHITE, light);
            limb(vc, pose, edge, y, z, strutX, strutY, z, 0.6f, BRASS, light);
        }
        box(vc, p, edge - 0.25f, y + 4.2f, z0, edge + 0.25f, y + 4.7f, z1, BRASS, LIGHT_BRASS, light);
    }

    private static void runeBands(VertexConsumer vc, PoseStack pose, float size, float y0, float y1, float z) {
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.rotateAround(Axis.YP.rotationDegrees(side * 90f), size / 32f, 0f, size / 32f);
            fitWall(vc, pose.last(), 3f, y0, size - 3f, y1, z, RUNES, WHITE, 0);
            pose.popPose();
        }
    }

    private static void teeth(VertexConsumer vc, PoseStack pose, int count, float r0, float r1, float halfWide, float halfDeep,
                              ResourceLocation tex, int color) {
        for (int i = 0; i < count; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / count));
            box(vc, pose.last(), -halfWide, r0, -halfDeep, halfWide, r1, halfDeep, tex, color, 0);
            pose.popPose();
        }
    }

    private static void ring(VertexConsumer vc, PoseStack pose, float radius, float thick, float width, int segments,
                             ResourceLocation tex, int color, int light) {
        float half = radius * Mth.PI / segments + 0.1f;
        for (int i = 0; i < segments; i++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / segments));
            box(vc, pose.last(), -half, radius - thick / 2f, -width / 2f, half, radius + thick / 2f, width / 2f, tex, color, light);
            pose.popPose();
        }
    }

    private static void runeRing(VertexConsumer vc, PoseStack pose, float radius, float faceZ, int segments) {
        for (int i = 0; i < segments; i += 2) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / segments));
            for (float z : new float[]{-faceZ / 2f - 0.15f, faceZ / 2f + 0.15f}) {
                box(vc, pose.last(), -1.3f, radius - 0.7f, z - 0.04f, 1.3f, radius + 0.7f, z + 0.04f, RUNES, WHITE, 0);
            }
            pose.popPose();
        }
    }

    /** Uniform-normal UV sphere so the floating Source core glows evenly without directional light bands. */
    private static void ball(VertexConsumer vc, PoseStack.Pose p, float radius, ResourceLocation tex, int color, int light) {
        TextureAtlasSprite s = sprite(tex);
        int bands = 10, sides = 20;
        for (int b = 0; b < bands; b++) {
            float a0 = -Mth.HALF_PI + Mth.PI * b / bands, a1 = -Mth.HALF_PI + Mth.PI * (b + 1) / bands;
            float y0 = radius * Mth.sin(a0), y1 = radius * Mth.sin(a1), r0 = radius * Mth.cos(a0), r1 = radius * Mth.cos(a1);
            float v0 = s.getV((b % 4) / 4f), v1 = s.getV((b % 4 + 1) / 4f);
            for (int i = 0; i < sides; i++) {
                float t0 = Mth.TWO_PI * i / sides, t1 = Mth.TWO_PI * (i + 1) / sides;
                float u0 = s.getU((i % 4) / 4f), u1 = s.getU((i % 4 + 1) / 4f);
                float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
                quad(vc, p, r1 * c0, y1, r1 * s0, r1 * c1, y1, r1 * s1,
                        r0 * c1, y0, r0 * s1, r0 * c0, y0, r0 * s0, u0, v0, u1, v1, color, light, 0f, 1f, 0f);
            }
        }
    }
}
