package com.golemlogix.client;

import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.golemlogix.blocks.JarForm;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.golemlogix.client.MachineParts.BRASS;
import static com.golemlogix.client.MachineParts.BRICK;
import static com.golemlogix.client.MachineParts.BRICK_TINT;
import static com.golemlogix.client.MachineParts.COPPER;
import static com.golemlogix.client.MachineParts.CRYO;
import static com.golemlogix.client.MachineParts.DARK;
import static com.golemlogix.client.MachineParts.FRAME;
import static com.golemlogix.client.MachineParts.GAUGE;
import static com.golemlogix.client.MachineParts.GLASS;
import static com.golemlogix.client.MachineParts.ICE;
import static com.golemlogix.client.MachineParts.RUNES;
import static com.golemlogix.client.MachineParts.SNOW;
import static com.golemlogix.client.MachineParts.WHITE;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.box;
import static com.golemlogix.client.render.PixelMesh.fitWall;
import static com.golemlogix.client.render.PixelMesh.sprite;

/**
 * The Cryo-Source Jar, two blocks high, drawn from its lower half: a dark brick plinth with a cyan rune band, brass valves,
 * and gauge; a verdigris-framed glass flask holding icy Cryo-Source; a louvred brass cap under snow with icicles and vent
 * pipe; and a small copper golem on a side bracket turning the valve. Joined multiblocks delegate to {@link JarWholeRenderer}.
 * <p>
 * Pixels in the north-facing frame: front is Z 0, viewer's right is -X; Y 0..32 over both halves.
 */
public class CryoJarRenderer extends LegacyBer<CryoJarBlockEntity> {
    /** Inner flask bounds inset a hair from the frame rings (8.5 and 21.5) to prevent coplanar z-fighting. */
    private static final float FLASK_BOTTOM = 8.56f, FLASK_TOP = 21.44f;
    private static final float[] PLINTH_VALVE_X = {10.2f, 12.6f};
    private static final float[][] FRAME_CORNERS = {{2f, 2f}, {12.8f, 2f}, {2f, 12.8f}, {12.8f, 12.8f}};
    private static final float[][] CAP_ICICLES = {{2.4f, 1.6f}, {5.2f, 0.9f}, {8.6f, 2.1f}, {11.8f, 1.2f}};

    private static final Vector3f GOLEM_POS = new Vector3f(-1.8f * PIXEL, 11.2f * PIXEL, 2.8f * PIXEL);
    private static final Vector3f FWD_EAST = new Vector3f(1f, 0f, 0f);
    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final double CREW_SIGHT_SQ = 36.0 * 36.0;
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final PoseStack SCRATCH_POSE = new PoseStack();

    /** Pre-baked static parts of a single jar (invalidated automatically when the block atlas reloads). */
    private record SingleMeshes(TextureAtlasSprite atlasMark, BerBuffers.Baked body,
                                BerBuffers.Baked bracket, BerBuffers.Baked wheel) {
    }

    private static SingleMeshes singleMeshes;

    private static SingleMeshes singleMeshes() {
        TextureAtlasSprite mark = sprite(BRASS);
        if (singleMeshes == null || singleMeshes.atlasMark() != mark) {
            singleMeshes = new SingleMeshes(mark,
                    BerBuffers.bake(CryoJarRenderer::bakeSingleBody),
                    BerBuffers.bake((vc, p) -> {
                        box(vc, p.last(), 1.1f, 9f, 2.3f, 2.1f, 20.5f, 3.3f, COPPER, WHITE, 0);
                        MachineParts.platform(vc, p, -3.4f, 2.0f, 10.6f, 1.2f, 4.4f, 6.8f, 0.8f, 0);
                    }),
                    BerBuffers.bake((vc, p) -> MachineParts.wheelX(vc, p, 0f, 0f, 0f, 2.1f, 0f, 0)));
        }
        return singleMeshes;
    }

    /** Cached iron golem parts for {@link AntigravityReactorRenderer} so children are never looked up by string each frame. */
    record IronGolemParts(ModelPart root, ModelPart head, ModelPart rightArm, ModelPart leftArm, ModelPart rightLeg, ModelPart leftLeg) {
        static IronGolemParts bake(BlockEntityRendererProvider.Context context) {
            ModelPart root = context.bakeLayer(ModelLayers.IRON_GOLEM);
            return new IronGolemParts(root, root.getChild("head"), root.getChild("right_arm"), root.getChild("left_arm"),
                    root.getChild("right_leg"), root.getChild("left_leg"));
        }
    }

    private final IronGolemParts ironGolem;

    public CryoJarRenderer(BlockEntityRendererProvider.Context context) {
        ironGolem = IronGolemParts.bake(context);
    }

    @Override
    public AABB getRenderBoundingBox(CryoJarBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return switch (be.form()) {
            case SINGLE -> new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 1.5, p.getY() + 2.2, p.getZ() + 1.5);
            case GIGA -> new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 2.5, p.getY() + 4.5, p.getZ() + 2.5);
            case SUPER -> new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 3.5, p.getY() + 6.0, p.getZ() + 3.5);
        };
    }

    /**
     * Joined multiblocks render from their corner cell alone, so chunk-section culling of that corner must not hide the whole
     * machine when looking up at the derrick or gimbal rings; {@link #getRenderBoundingBox} still frustum-culls it.
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void render(CryoJarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CryoJarBlock.FACING) || state.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        if (be.form() != JarForm.SINGLE) {
            if (be.isController()) {
                JarWholeRenderer.render(be, ironGolem, partialTick, pose, buffers);
            }
            return;
        }
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightCoords(level, pos.above());
        float fill = Mth.lerp(partialTick, be.fillPrev, be.fill);
        float time = ((level.getGameTime() + Math.floorMod(pos.asLong(), 61L)) % 72000L) + partialTick;
        boolean charged = be.clientSource() > 0;
        RenderType solid = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);

        VertexConsumer vc = buffers.getBuffer(solid);
        boolean pipeAbove = MachineParts.jarSockets(vc, pose.last(), level, pos, light);
        Direction facing = state.getValue(CryoJarBlock.FACING);
        boolean showCrew = LegacyBer.camera().distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) < CREW_SIGHT_SQ;
        float golemTurn = showCrew ? MachineParts.golemTurn(level, pos, facing, 2) : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        PoseStack.Pose p = pose.last();

        SingleMeshes baked = singleMeshes();
        baked.body().draw(buffers, solid, p, light, 0);

        int rune = MachineParts.cyanRuneColor(time, charged);
        fitWall(vc, p, 1.6f, 1.8f, 14.4f, 4.4f, 0.94f, RUNES, rune, charged ? LightCoordsUtil.FULL_BRIGHT : light);
        if (!pipeAbove) {
            box(vc, p, 6.8f, 29.4f, 6.8f, 9.2f, 31.4f, 9.2f, BRASS, WHITE, light);
            box(vc, p, 6.6f, 31.4f, 6.6f, 9.4f, 32.4f, 9.4f, ICE, WHITE, light);
        }

        if (showCrew) {
            renderGolemBracket(baked, solid, pose, buffers, golemTurn, light, time, charged);
        }
        renderFluidAndGlass(p, buffers, fill, light);
        pose.popPose();
    }

    private static void renderGolemBracket(SingleMeshes baked, RenderType solid, PoseStack pose, MultiBufferSource buffers,
                                           float golemTurn, int light, float time, boolean charged) {
        float turn = MachineParts.valveTurnAngle(time, charged);
        PoseStack.Pose bp = SCRATCH_POSE.last();
        bp.set(pose.last());
        if (golemTurn != 0f) {
            SCRATCH_QUAT.rotationY(golemTurn * Mth.DEG_TO_RAD);
            bp.pose().rotateAround(SCRATCH_QUAT, 0.5f, 0.5f, 0.5f);
            bp.normal().rotate(SCRATCH_QUAT);
        }
        baked.bracket().draw(buffers, solid, bp, light, 0);

        // Rotate wheel around its axle on the bracket pose
        SCRATCH_QUAT.rotationX(turn * Mth.DEG_TO_RAD);
        bp.pose().translate(0.6f * PIXEL, 14.2f * PIXEL, 2.8f * PIXEL).rotate(SCRATCH_QUAT);
        bp.normal().rotate(SCRATCH_QUAT);
        baked.wheel().draw(buffers, solid, bp, light, 0);

        // Restore bracket pose on SCRATCH_POSE for CopperGolemPuppet
        bp.set(pose.last());
        if (golemTurn != 0f) {
            SCRATCH_QUAT.rotationY(golemTurn * Mth.DEG_TO_RAD);
            bp.pose().rotateAround(SCRATCH_QUAT, 0.5f, 0.5f, 0.5f);
            bp.normal().rotate(SCRATCH_QUAT);
        }
        float reach = charged ? -1.15f + 0.12f * Mth.sin(turn * Mth.DEG_TO_RAD) : -0.9f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(time * 0.017f));
        CopperGolemPuppet.render(SCRATCH_POSE, buffers, light, GOLEM_POS, FWD_EAST,
                UP, 0.36f, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.08f), 1, null, arms);
    }

    private static void renderFluidAndGlass(PoseStack.Pose p, MultiBufferSource buffers, float fill, int light) {
        VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        if (fill > 0.002f) {
            float top = FLASK_BOTTOM + (FLASK_TOP - FLASK_BOTTOM) * Math.min(1f, fill);
            box(tv, p, 3.4f, FLASK_BOTTOM, 3.4f, 12.6f, top, 12.6f, CRYO, 0xE0FFFFFF, LightCoordsUtil.FULL_BRIGHT);
        }
        box(tv, p, 3.0f, 8.53f, 3.0f, 13.0f, 21.47f, 13.0f, GLASS, 0x90E8F8FF, light);
    }

    private static void bakeSingleBody(VertexConsumer vc, PoseStack pose) {
        PoseStack.Pose p = pose.last();
        int light = 0;
        // brick plinth, valves, and front gauge
        box(vc, p, 0.6f, 0, 0.6f, 15.4f, 1.4f, 15.4f, DARK, WHITE, light);
        box(vc, p, 1, 1.4f, 1, 15, 6.4f, 15, BRICK, BRICK_TINT, light);
        box(vc, p, 0.8f, 6.4f, 0.8f, 15.2f, 7.6f, 15.2f, DARK, WHITE, light);
        for (float x : PLINTH_VALVE_X) {
            box(vc, p, x, 4.4f, 0.3f, x + 1.2f, 5.6f, 1f, BRASS, WHITE, light);
            box(vc, p, x - 0.3f, 5.6f, 0.2f, x + 1.5f, 6.1f, 0.8f, BRASS, 0xFFE8C878, light);
        }
        fitWall(vc, p, 2.2f, 4.2f, 5.8f, 7.8f, 0.5f, GAUGE, WHITE, light);
        box(vc, p, 3.6f, 3.6f, 0.6f, 4.4f, 4.3f, 1f, BRASS, WHITE, light);
        // verdigris corner posts and flask rings
        for (float[] c : FRAME_CORNERS) {
            box(vc, p, c[0], 7.6f, c[1], c[0] + 1.2f, 22.4f, c[1] + 1.2f, FRAME, WHITE, light);
        }
        box(vc, p, 1.8f, 7.6f, 1.8f, 14.2f, 8.5f, 14.2f, FRAME, WHITE, light);
        box(vc, p, 1.8f, 21.5f, 1.8f, 14.2f, 22.4f, 14.2f, FRAME, WHITE, light);
        // louvred brass cap under snow with hanging icicles
        box(vc, p, 2.2f, 22.4f, 2.2f, 13.8f, 28f, 13.8f, BRASS, 0xFFD8B880, light);
        for (float y = 23.2f; y < 27.5f; y += 1.4f) {
            box(vc, p, 1.6f, y, 1.6f, 14.4f, y + 0.6f, 14.4f, BRASS, WHITE, light);
        }
        box(vc, p, 1.4f, 28f, 1.4f, 14.6f, 29.4f, 14.6f, SNOW, WHITE, light);
        for (float[] i : CAP_ICICLES) {
            box(vc, p, i[0], 28f - i[1], 1.2f, i[0] + 0.6f, 28f, 1.8f, ICE, WHITE, light);
        }
    }
}
