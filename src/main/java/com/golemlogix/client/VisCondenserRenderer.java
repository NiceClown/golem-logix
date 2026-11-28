package com.golemlogix.client;

import com.golemlogix.blockentity.VisCondenserBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.VisCondenserBlock;
import com.golemlogix.client.render.Baked;
import com.golemlogix.client.render.PixelMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.BRICK;
import static com.golemlogix.client.MachineParts.BRICK_TINT;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.FRAME;
import static com.golemlogix.client.MachineParts.GLASS;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.LIGHT_BRASS;
import static com.golemlogix.client.MachineParts.RUNES;
import static com.golemlogix.client.MachineParts.SNOW;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.mcBlock;
import static com.golemlogix.client.render.PixelMesh.scaledRgb;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * The Vis Condenser, two blocks high, drawn from its lower plinth: a brick and brass plinth with state-colored runes and
 * a front brass Aura manometer (live needle + red 10% Flux danger arc); a frosted glass condenser cylinder containing a
 * glowing violet-cyan Vis crystal vortex with snow collar and overhead Cryo-Source Jar pipes; a rotating twin-ring brass
 * armillary sphere on top; and a cantilevered copper golem wearing brass Thaumometer goggles and sweeping a glowing Vis rod.
 * <p>
 * Pixels in the north-facing frame: front is Z 0, viewer's right is -X, viewer's left is +X; Y 0..34.
 */
public class VisCondenserRenderer implements BlockEntityRenderer<VisCondenserBlockEntity> {
    private static final ResourceLocation GLOW = mcBlock("white_concrete");
    private static final ResourceLocation OAK = mcBlock("dark_oak_planks");
    private static final float[][] FRAME_CORNERS = {{1.8f, 1.8f}, {13.0f, 1.8f}, {1.8f, 13.0f}, {13.0f, 13.0f}};
    private static final float[][] ICICLES = {{2.0f, 1.8f}, {5.2f, 1.1f}, {8.4f, 2.2f}, {11.6f, 1.4f}, {13.6f, 1.9f}};

    private static final float DIAL_CX = 8.0f, DIAL_CY = 9.4f, DIAL_Z = -0.05f;
    private static final Vector3f GOLEM_POS = new Vector3f(-3.1f * PIXEL, 17.6f * PIXEL, 6.8f * PIXEL);
    /** Angled 3/4 forward-inward so twin brass Thaumometer goggles and Vis rod read clearly from front & 3/4 views. */
    private static final Vector3f FWD_GOLEM = new Vector3f(0.74f, 0f, -0.67f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final double CREW_SIGHT_SQ = 42.0 * 42.0;

    private static final BlockPos.MutableBlockPos JAR_PROBE = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos SIDE_PROBE = new BlockPos.MutableBlockPos();
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final PoseStack SCRATCH_POSE = new PoseStack();

    private record Meshes(TextureAtlasSprite atlasMark, Baked plinth, BerRingWrapper rings,
                          Baked outerRing, Baked innerRing, Baked balcony) {
    }

    private record BerRingWrapper(Baked condenser) {
    }

    private static Meshes meshes;

    public static void clearBaked() {
        meshes = null;
    }

    private static Meshes meshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (meshes == null || meshes.atlasMark() != mark) {
            meshes = new Meshes(mark,
                    Baked.bake(VisCondenserRenderer::bakePlinth),
                    new BerRingWrapper(Baked.bake(VisCondenserRenderer::bakeCondenser)),
                    Baked.bake((vc, p) -> bakeArmillaryRing(vc, p, 4.1f, 0.72f)),
                    Baked.bake((vc, p) -> bakeArmillaryRing(vc, p, 3.05f, 0.58f)),
                    Baked.bake(VisCondenserRenderer::bakeBalcony));
        }
        return meshes;
    }

    public VisCondenserRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(VisCondenserBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 1.1, p.getY(), p.getZ() - 1.1, p.getX() + 2.1, p.getY() + 2.5, p.getZ() + 2.1);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(VisCondenserBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(VisCondenserBlock.FACING) || state.getValue(VisCondenserBlock.SECTION) != 0) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightColor(level, pos.above());
        int lightTop = LevelRenderer.getLightColor(level, pos.above(2));
        float work = Mth.lerp(partialTick, be.workPrev, be.work);
        float phase = Mth.lerp(partialTick, be.phasePrev, be.phase);
        float needle = Mth.lerp(partialTick, be.needlePrev, be.needle);
        float time = ((level.getGameTime() + Math.floorMod(pos.asLong(), 59L)) % 72000L) + partialTick;
        int status = be.clientStatus;
        boolean danger = status == VisCondenserBlockEntity.STATUS_LOW_VIS_WORKING || be.clientLowVisWarning;
        boolean floorPaused = status == VisCondenserBlockEntity.STATUS_FLOOR_PAUSED;

        Meshes baked = meshes();
        RenderType solid = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer vc = buffers.getBuffer(solid);

        // World-aligned overhead outlet pipes connecting to adjacent Cryo-Source Jars
        renderJarPipes(vc, pose.last(), level, pos, lightTop);

        Direction facing = state.getValue(VisCondenserBlock.FACING);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean showCrew = cam.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5) < CREW_SIGHT_SQ;
        float golemTurn = showCrew ? balconyTurn(level, pos, facing) : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        baked.plinth().draw(buffers, solid, p, light, 0);
        baked.rings().condenser().draw(buffers, solid, p, lightTop, 0);

        // State-colored runes on the lower plinth
        renderRunes(vc, p, status, danger, floorPaused, work, time, light);
        // Live brass Aura manometer dial with configured red danger arc, gold floor pip, and animated needle
        renderManometer(vc, pose, needle, be.clientThreshold, be.clientMode, be.clientBase, be.clientLowVisPercent, danger, light);
        // Rotating top brass armillary rings + glowing Vis focus bead
        renderArmillary(vc, buffers, solid, baked, pose, phase, work, danger, time, lightTop);
        // Swirling luminous Vis crystal vortex inside the frost condenser
        renderVortex(vc, pose, phase, work, danger, floorPaused, time);

        if (showCrew) {
            renderBalconyAndGolem(baked, solid, pose, buffers, golemTurn, light, work, phase, time, status, danger, floorPaused);
        }

        // Translucent frosted glass cylinder drawn after interior vortex & golem
        VertexConsumer tv = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        box(tv, p, 2.8f, 16.04f, 2.8f, 13.2f, 25.56f, 13.2f, GLASS, 0x84E8F2FF, lightTop);

        pose.popPose();
    }

    /** Picks a clear side for the cantilevered upper balcony: right (-X), left (+X), or back (+Z), avoiding front manometer. */
    private static float balconyTurn(Level level, BlockPos pos, Direction facing) {
        if (isSideClear(level, pos, facing.getCounterClockWise())) {
            return 0f;
        }
        if (isSideClear(level, pos, facing.getClockWise())) {
            return 180f;
        }
        if (isSideClear(level, pos, facing.getOpposite())) {
            return 90f;
        }
        return 0f;
    }

    private static boolean isSideClear(Level level, BlockPos pos, Direction side) {
        SIDE_PROBE.set(pos.getX() + side.getStepX(), pos.getY() + 1, pos.getZ() + side.getStepZ());
        return level.getBlockState(SIDE_PROBE).getCollisionShape(level, SIDE_PROBE).isEmpty();
    }

    private static void renderRunes(VertexConsumer vc, PoseStack.Pose p, int status, boolean danger, boolean floorPaused,
                                    float work, float time, int light) {
        int runeColor;
        boolean fullBright = true;
        if (danger) {
            float pulse = 0.55f + 0.45f * Mth.sin(time * 0.45f);
            runeColor = scaledRgb(0xFF2838, pulse);
        } else if (floorPaused) {
            float pulse = 0.65f + 0.25f * Mth.sin(time * 0.08f);
            runeColor = scaledRgb(0xF5A82E, pulse);
        } else if (status == VisCondenserBlockEntity.STATUS_WORKING) {
            float mix = 0.5f + 0.5f * Mth.sin(time * 0.09f);
            int r = (int) Mth.lerp(mix, 0x56, 0xA8);
            int g = (int) Mth.lerp(mix, 0xE8, 0x78);
            int b = 0xFF;
            float glow = 0.72f + 0.28f * (0.5f + 0.5f * Mth.sin(time * 0.16f));
            runeColor = 0xFF000000 | ((int) (r * glow) << 16) | ((int) (g * glow) << 8) | (int) (b * glow);
        } else {
            runeColor = 0xFF3E4A52;
            fullBright = false;
        }
        fitWall(vc, p, 1.2f, 2.3f, 14.8f, 4.9f, 0.52f, RUNES, runeColor, fullBright ? LightTexture.FULL_BRIGHT : light);
    }

    private static void renderManometer(VertexConsumer vc, PoseStack pose, float needleFrac, int threshold, int mode, int base,
                                        int lowPct, boolean danger, int light) {
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite glowSprite = sprite(GLOW);
        // Cream parchment dial face recessed inside brass bezel
        box(vc, p, DIAL_CX - 2.75f, DIAL_CY - 2.75f, DIAL_Z + 0.02f, DIAL_CX + 2.75f, DIAL_CY + 2.75f, DIAL_Z + 0.35f,
                glowSprite, 0xFFEFE3C2, light);

        // Red Flux danger arc from -135 deg (0%) to configured low-Vis percent on the viewer's lower-left of the dial
        if (lowPct > 0) {
            float span = 270f * Mth.clamp(lowPct / 100f, 0f, 1f);
            int steps = Math.max(2, (int) Math.ceil(span / 9f) + 1);
            int redCol = danger ? 0xFFFF2424 : 0xFFC82828;
            int redLight = danger ? LightTexture.FULL_BRIGHT : light;
            for (int i = 0; i < steps; i++) {
                float deg = -135f + span * i / (steps - 1);
                float rad = deg * Mth.DEG_TO_RAD;
                float sx = DIAL_CX - Mth.sin(rad) * 2.15f;
                float sy = DIAL_CY + Mth.cos(rad) * 2.15f;
                box(vc, p, sx - 0.32f, sy - 0.32f, DIAL_Z - 0.08f, sx + 0.32f, sy + 0.32f, DIAL_Z + 0.02f,
                        glowSprite, redCol, redLight);
            }
        }

        // Brass graduation ticks around the safe arc
        for (int deg = -90; deg <= 135; deg += 45) {
            float rad = deg * Mth.DEG_TO_RAD;
            float sx = DIAL_CX - Mth.sin(rad) * 2.2f;
            float sy = DIAL_CY + Mth.cos(rad) * 2.2f;
            box(vc, p, sx - 0.22f, sy - 0.22f, DIAL_Z - 0.06f, sx + 0.22f, sy + 0.22f, DIAL_Z + 0.02f,
                    DARK, WHITE, light);
        }

        // Gold threshold stop-floor marker pip on the rim
        float floorRatio = mode == VisCondenserBlockEntity.MODE_PERCENT
                ? Mth.clamp(threshold / 100f, 0f, 1f)
                : Mth.clamp(threshold / Math.max(1f, (float) base), 0f, 1f);
        float floorRad = (-135f + floorRatio * 270f) * Mth.DEG_TO_RAD;
        float fx = DIAL_CX - Mth.sin(floorRad) * 2.3f;
        float fy = DIAL_CY + Mth.cos(floorRad) * 2.3f;
        box(vc, p, fx - 0.34f, fy - 0.34f, DIAL_Z - 0.14f, fx + 0.34f, fy + 0.34f, DIAL_Z - 0.01f,
                BRASS, LIGHT_BRASS, LightTexture.FULL_BRIGHT);

        // Live animated manometer needle (0% = -135 deg lower-left, 50% = 0 deg top, 100% = +135 deg lower-right)
        float angle = -135f + Mth.clamp(needleFrac, 0f, 1f) * 270f;
        pose.pushPose();
        pose.translate(DIAL_CX * PIXEL, DIAL_CY * PIXEL, (DIAL_Z - 0.18f) * PIXEL);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        PoseStack.Pose np = pose.last();
        box(vc, np, -0.22f, -0.45f, -0.05f, 0.22f, 2.35f, 0.08f, DARK, 0xFF281812, light);
        box(vc, np, -0.14f, 1.5f, -0.08f, 0.14f, 2.45f, 0.05f, glowSprite, danger ? 0xFFFF3838 : 0xFFE8B040, LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Brass center pivot cap
        box(vc, p, DIAL_CX - 0.45f, DIAL_CY - 0.45f, DIAL_Z - 0.28f, DIAL_CX + 0.45f, DIAL_CY + 0.45f, DIAL_Z - 0.05f,
                BRASS, LIGHT_BRASS, light);
    }

    private static void renderArmillary(VertexConsumer vc, MultiBufferSource buffers, RenderType solid, Meshes baked,
                                        PoseStack pose, float phase, float work, boolean danger, float time, int lightTop) {
        // Gentle idle turn when paused, brisk orbital rotation while actively condensing Vis
        float spin = phase * 24f + time * 0.45f;
        float cx = 8f * PIXEL, cy = 31.8f * PIXEL, cz = 8f * PIXEL;

        // Outer meridian ring
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(18f + 5f * Mth.sin(time * 0.04f)));
        baked.outerRing().draw(buffers, solid, pose.last(), lightTop, 0);
        pose.popPose();

        // Inner orthogonal ecliptic ring
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(-spin * 0.7f));
        pose.mulPose(Axis.ZP.rotationDegrees(90f));
        pose.mulPose(Axis.XP.rotationDegrees(spin * 1.45f));
        baked.innerRing().draw(buffers, solid, pose.last(), lightTop, 0);
        pose.popPose();

        // Glowing central Vis focus bead
        pose.pushPose();
        pose.translate(cx, cy, cz);
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.2f));
        pose.mulPose(Axis.XP.rotationDegrees(45f));
        pose.mulPose(Axis.ZP.rotationDegrees(45f));
        float pulse = 0.65f + 0.35f * Mth.sin(time * (danger ? 0.5f : 0.14f));
        int beadColor = danger ? scaledRgb(0xFF4090, pulse) : scaledRgb(work > 0.05f ? 0xB892FF : 0x7AC8E0, pulse);
        box(vc, pose.last(), -0.95f, -0.95f, -0.95f, 0.95f, 0.95f, 0.95f, sprite(GLOW), beadColor, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static void renderVortex(VertexConsumer vc, PoseStack pose, float phase, float work,
                                     boolean danger, boolean floorPaused, float time) {
        TextureAtlasSprite glowSprite = sprite(GLOW);
        float glowScale = work > 0.05f ? 0.75f + 0.25f * Mth.sin(time * 0.2f) : floorPaused ? 0.42f : 0.22f;
        int coreRgb = danger ? 0xE83878 : floorPaused ? 0xD89840 : 0x6AF0FF;
        int outerRgb = danger ? 0xAA20C8 : floorPaused ? 0xA86E28 : 0x9E6BFF;

        // Central floating faceted Vis crystal
        pose.pushPose();
        pose.translate(8f * PIXEL, (20.8f + 0.45f * Mth.sin(time * 0.1f)) * PIXEL, 8f * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(phase * 30f + time * 1.2f));
        pose.mulPose(Axis.ZP.rotationDegrees(35f));
        pose.mulPose(Axis.XP.rotationDegrees(35f));
        box(vc, pose.last(), -1.55f, -1.55f, -1.55f, 1.55f, 1.55f, 1.55f, glowSprite, scaledRgb(coreRgb, glowScale), LightTexture.FULL_BRIGHT);
        pose.popPose();

        // Swirling Vis vortex wisp cubes rising inside the cylinder
        int wisps = work > 0.05f ? 10 : 5;
        for (int i = 0; i < wisps; i++) {
            float cycle = ((time * (work > 0.05f ? 0.032f : 0.01f) + i / (float) wisps) % 1.0f);
            float y = Mth.lerp(cycle, 16.9f, 24.7f);
            float r = 2.65f * Mth.sin(cycle * Mth.PI) + 0.75f;
            float a = cycle * Mth.TWO_PI * 2f + phase * 1.4f + i * 1.7f;
            float wx = 8f + Mth.cos(a) * r;
            float wz = 8f + Mth.sin(a) * r;
            float sz = (0.36f + 0.22f * Mth.sin(cycle * Mth.PI)) * (work > 0.05f ? 1.0f : 0.75f);
            int col = scaledRgb((i & 1) == 0 ? outerRgb : coreRgb, glowScale * (0.65f + 0.35f * Mth.sin(cycle * Mth.PI)));
            box(vc, pose.last(), wx - sz, y - sz, wz - sz, wx + sz, y + sz, wz + sz, glowSprite, col, LightTexture.FULL_BRIGHT);
        }
    }

    private static void renderBalconyAndGolem(Meshes baked, RenderType solid, PoseStack pose, MultiBufferSource buffers,
                                              float golemTurn, int light, float work, float phase, float time,
                                              int status, boolean danger, boolean floorPaused) {
        PoseStack.Pose bp = SCRATCH_POSE.last();
        bp.pose().set(pose.last().pose());
        bp.normal().set(pose.last().normal());
        if (golemTurn != 0f) {
            SCRATCH_QUAT.rotationY(golemTurn * Mth.DEG_TO_RAD);
            bp.pose().rotateAround(SCRATCH_QUAT, 0.5f, 0.5f, 0.5f);
            bp.normal().rotate(SCRATCH_QUAT);
        }
        baked.balcony().draw(buffers, solid, bp, light, 0);

        // Compute pose for the three distinct Vis Condenser states
        float rightX, rightZ, leftX, leftZ, headYaw, bow;
        if (danger) {
            float tense = time * 0.75f;
            rightX = -1.95f + 0.42f * Mth.sin(tense);
            rightZ = 0.35f + 0.32f * Mth.cos(tense * 1.2f);
            leftX = -1.25f + 0.22f * Mth.sin(tense * 0.9f);
            leftZ = -0.30f;
            headYaw = 0.32f * Mth.sin(tense * 0.6f);
            bow = 0.14f;
        } else if (work > 0.05f) {
            float sweep = phase * 0.9f;
            rightX = -1.85f + 0.34f * Mth.sin(sweep);
            rightZ = 0.30f + 0.28f * Mth.cos(sweep * 0.75f);
            leftX = -0.68f + 0.14f * Mth.sin(sweep * 0.5f);
            leftZ = -0.18f;
            headYaw = 0.18f * Mth.cos(sweep * 0.75f);
            bow = 0.12f;
        } else if (floorPaused) {
            rightX = -0.42f;
            rightZ = 0.14f;
            leftX = -0.22f;
            leftZ = -0.10f;
            headYaw = 0.58f + 0.16f * Mth.sin(time * 0.045f);
            bow = 0.04f;
        } else {
            rightX = -0.55f;
            rightZ = 0.12f;
            leftX = -0.25f;
            leftZ = -0.10f;
            headYaw = 0.2f * Mth.sin(time * 0.025f);
            bow = 0.02f;
        }

        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(
                rightX, rightZ, leftX, leftZ, headYaw,
                wandPose -> renderVisRod(wandPose, buffers, light, work, danger, time),
                null);

        CopperGolemPuppet.render(SCRATCH_POSE, buffers, light, GOLEM_POS, FWD_GOLEM, UP, 0.43f,
                new CopperGolemPuppet.Anim(time * 0.1f, false, false, bow), 1, null, arms,
                headPose -> renderThaumometerGoggles(headPose, buffers, light, danger, floorPaused, time));

        // Subtle floating Vis wisps streaming from wand toward condenser while active, or purple-crimson sparks in danger
        if (work > 0.05f || danger) {
            VertexConsumer vc = buffers.getBuffer(solid);
            TextureAtlasSprite glowSprite = sprite(GLOW);
            int count = danger ? 4 : 3;
            for (int i = 0; i < count; i++) {
                float u = ((time * (danger ? 0.09f : 0.055f) + i / (float) count) % 1.0f);
                float wx = Mth.lerp(u, -1.6f, 4.2f);
                float wy = Mth.lerp(u, 22.8f, 24.4f) + 0.85f * Mth.sin(u * Mth.PI + i);
                float wz = 7.4f + 0.9f * Mth.cos(time * 0.2f + i * 2.1f);
                float sz = danger ? 0.34f : 0.30f;
                int col = danger ? ((i & 1) == 0 ? 0xFFFF3878 : 0xFFC048FF) : ((i & 1) == 0 ? 0xFFC088FF : 0xFF64F4FF);
                box(vc, bp, wx - sz, wy - sz, wz - sz, wx + sz, wy + sz, wz + sz, glowSprite, col, LightTexture.FULL_BRIGHT);
            }
        }
    }

    /** Brass Thaumometer goggles drawn in CopperGolemPuppet's head frame (+Y down, face at -Z = -5). */
    private static void renderThaumometerGoggles(PoseStack pose, MultiBufferSource buffers, int light,
                                                 boolean danger, boolean floorPaused, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        TextureAtlasSprite brassSprite = sprite(BRASS);
        TextureAtlasSprite darkSprite = sprite(DARK);
        TextureAtlasSprite glowSprite = sprite(GLOW);

        box(vc, p, -4.35f, -3.55f, -5.05f, -4.12f, -2.25f, 5.25f, darkSprite, 0xFF6A482A, light);
        box(vc, p, 4.12f, -3.55f, -5.05f, 4.35f, -2.25f, 5.25f, darkSprite, 0xFF6A482A, light);
        box(vc, p, -4.35f, -3.55f, 5.12f, 4.35f, -2.25f, 5.38f, darkSprite, 0xFF6A482A, light);
        box(vc, p, -1.1f, -3.35f, -5.65f, 1.1f, -2.45f, -5.12f, brassSprite, LIGHT_BRASS, light);

        box(vc, p, 0.95f, -4.05f, -6.05f, 3.85f, -1.55f, -5.12f, brassSprite, 0xFFE6C068, light);
        int leftLens = danger ? 0xFFFF4878 : floorPaused ? 0xFFF5B840 : 0xFF5CF6FF;
        box(vc, p, 1.38f, -3.62f, -6.22f, 3.42f, -1.98f, -5.92f, glowSprite, leftLens, LightTexture.FULL_BRIGHT);

        box(vc, p, -3.85f, -4.05f, -6.05f, -0.95f, -1.55f, -5.12f, brassSprite, 0xFFE6C068, light);
        box(vc, p, -3.55f, -3.80f, -6.55f, -1.25f, -1.80f, -6.02f, brassSprite, LIGHT_BRASS, light);
        float flicker = 0.85f + 0.15f * Mth.sin(time * 0.25f);
        int rightLens = danger ? scaledRgb(0xD848FF, flicker) : scaledRgb(0xFFC848, flicker);
        box(vc, p, -3.15f, -3.45f, -6.70f, -1.65f, -2.15f, -6.45f, glowSprite, rightLens, LightTexture.FULL_BRIGHT);
    }

    /** Copper-tipped Vis antenna wand held in the right hand (-Z forward in ModelPart hand frame). */
    private static void renderVisRod(PoseStack pose, MultiBufferSource buffers, int light,
                                     float work, boolean danger, float time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose p = pose.last();
        box(vc, p, -0.6f, -0.6f, -1.6f, 0.6f, 0.6f, 1.8f, OAK, WHITE, light);
        box(vc, p, -0.45f, -0.45f, -9.8f, 0.45f, 0.45f, -1.6f, COPPER, 0xFFF0B888, light);
        box(vc, p, -0.78f, -0.78f, -7.2f, 0.78f, 0.78f, -6.3f, BRASS, LIGHT_BRASS, light);
        box(vc, p, -0.88f, -0.88f, -9.9f, 0.88f, 0.88f, -9.1f, BRASS, LIGHT_BRASS, light);
        float pulse = 0.72f + 0.28f * Mth.sin(time * (danger ? 0.55f : 0.18f));
        int tipCol = danger ? scaledRgb(0xFF3888, pulse) : scaledRgb(work > 0.05f ? 0xC884FF : 0x8E74B8, pulse);
        box(vc, p, -0.95f, -0.95f, -11.9f, 0.95f, 0.95f, -9.9f, sprite(GLOW), tipCol, LightTexture.FULL_BRIGHT);
    }

    private static void bakePlinth(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        box(vc, p, 0f, 0f, 0f, 16f, 2.0f, 16f, DARK, WHITE, 0);
        box(vc, p, 0.6f, 2.0f, 0.6f, 15.4f, 13.4f, 15.4f, BRICK, BRICK_TINT, 0);
        box(vc, p, 0.2f, 13.4f, 0.2f, 15.8f, 15.0f, 15.8f, DARK, WHITE, 0);
        for (float[] c : new float[][]{{0.3f, 0.3f}, {14.1f, 0.3f}, {0.3f, 14.1f}, {14.1f, 14.1f}}) {
            box(vc, p, c[0], 2.0f, c[1], c[0] + 1.6f, 13.4f, c[1] + 1.6f, BRASS, 0xFFD8B878, 0);
        }
        box(vc, p, DIAL_CX - 3.35f, DIAL_CY - 3.35f, 0.0f, DIAL_CX + 3.35f, DIAL_CY + 3.35f, 0.7f, BRASS, 0xFFE6C472, 0);
        box(vc, p, DIAL_CX - 3.05f, DIAL_CY - 3.05f, -0.15f, DIAL_CX + 3.05f, DIAL_CY - 2.72f, 0.1f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, DIAL_CX - 3.05f, DIAL_CY + 2.72f, -0.15f, DIAL_CX + 3.05f, DIAL_CY + 3.05f, 0.1f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, DIAL_CX - 3.05f, DIAL_CY - 2.72f, -0.15f, DIAL_CX - 2.72f, DIAL_CY + 2.72f, 0.1f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, DIAL_CX + 2.72f, DIAL_CY - 2.72f, -0.15f, DIAL_CX + 3.05f, DIAL_CY + 2.72f, 0.1f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, 2.2f, 7.6f, 0.1f, 3.6f, 11.2f, 0.8f, COPPER, WHITE, 0);
        box(vc, p, 12.4f, 7.6f, 0.1f, 13.8f, 11.2f, 0.8f, COPPER, WHITE, 0);
    }

    private static void bakeCondenser(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        box(vc, p, 1.4f, 15.0f, 1.4f, 14.6f, 16.0f, 14.6f, FRAME, WHITE, 0);
        box(vc, p, 1.4f, 25.6f, 1.4f, 14.6f, 26.6f, 14.6f, FRAME, WHITE, 0);
        for (float[] c : FRAME_CORNERS) {
            box(vc, p, c[0], 16.0f, c[1], c[0] + 1.2f, 25.6f, c[1] + 1.2f, FRAME, WHITE, 0);
        }
        box(vc, p, 1.8f, 26.6f, 1.8f, 14.2f, 27.6f, 14.2f, BRASS, 0xFFE0BC70, 0);
        box(vc, p, 1.0f, 27.6f, 1.0f, 15.0f, 28.8f, 15.0f, SNOW, WHITE, 0);
        for (float[] i : ICICLES) {
            box(vc, p, i[0], 27.6f - i[1], 0.8f, i[0] + 0.65f, 27.6f, 1.45f, ICE, WHITE, 0);
            box(vc, p, 0.8f, 27.6f - i[1] * 0.8f, i[0], 1.45f, 27.6f, i[0] + 0.65f, ICE, WHITE, 0);
        }
        box(vc, p, 6.8f, 28.8f, 6.8f, 9.2f, 29.8f, 9.2f, BRASS, WHITE, 0);
        box(vc, p, 6.4f, 29.4f, 6.4f, 9.6f, 30.0f, 9.6f, ICE, WHITE, 0);
    }

    /** Continuous solid 12-segment brass armillary ring formed with connected brass chords. */
    private static void bakeArmillaryRing(VertexConsumer vc, PoseStack pose, float radius, float thickness) {
        int segs = 12;
        for (int i = 0; i < segs; i++) {
            float a0 = i * Mth.TWO_PI / segs;
            float a1 = (i + 1) * Mth.TWO_PI / segs;
            float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
            PixelMesh.limb(vc, pose, x0, y0, 0f, x1, y1, 0f, thickness, BRASS, 0);
        }
        PoseStack.Pose p = pose.last();
        float r = thickness * 0.72f;
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI;
            float cx = Mth.cos(a) * radius, cy = Mth.sin(a) * radius;
            box(vc, p, cx - r, cy - r, -r, cx + r, cy + r, r, BRASS, LIGHT_BRASS, 0);
        }
    }

    private static void bakeBalcony(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        MachineParts.platform(vc, pose, -5.2f, 1.4f, 17.6f, 3.6f, 10.2f, 13.8f, 0.4f, 0);
        box(vc, p, -5.3f, 17.6f, 3.5f, -4.6f, 20.0f, 4.2f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, -5.3f, 17.6f, 9.6f, -4.6f, 20.0f, 10.3f, BRASS, LIGHT_BRASS, 0);
        box(vc, p, -5.2f, 19.5f, 3.6f, -4.7f, 20.0f, 10.2f, BRASS, 0xFFE8C878, 0);
    }

    /** Overhead brass-and-ice outlet pipes auto-connecting from the frost collar to adjacent Cryo-Source Jars. */
    private static void renderJarPipes(VertexConsumer vc, PoseStack.Pose p, Level level, BlockPos base, int light) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            JAR_PROBE.set(base.getX() + d.getStepX(), base.getY(), base.getZ() + d.getStepZ());
            BlockState s = level.getBlockState(JAR_PROBE);
            if (!(s.getBlock() instanceof CryoJarBlock) || s.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            pipeRun(vc, p, d, 5.8f, 8.2f, 0.95f, 25.8f, 27.6f, BRASS, WHITE, light);
            pipeRun(vc, p, d, 6.2f, 8.2f, 0.95f, 25.8f, 34.2f, BRASS, WHITE, light);
            pipeRun(vc, p, d, 6.2f, 17.4f, 1.0f, 32.4f, 34.2f, BRASS, 0xFFE8C878, light);
            pipeRun(vc, p, d, 10.6f, 12.2f, 1.3f, 32.1f, 34.5f, ICE, WHITE, light);
            pipeRun(vc, p, d, 14.8f, 17.2f, 1.2f, 31.4f, 34.0f, BRASS, WHITE, light);
        }
    }

    private static void pipeRun(VertexConsumer vc, PoseStack.Pose p, Direction d, float u0, float u1, float halfW,
                                float y0, float y1, ResourceLocation tex, int tint, int light) {
        float a0 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u0 : -u1);
        float a1 = 8 + (d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? u1 : -u0);
        if (d.getAxis() == Direction.Axis.X) {
            box(vc, p, a0, y0, 8 - halfW, a1, y1, 8 + halfW, tex, tint, light);
        } else {
            box(vc, p, 8 - halfW, y0, a0, 8 + halfW, y1, a1, tex, tint, light);
        }
    }
}
