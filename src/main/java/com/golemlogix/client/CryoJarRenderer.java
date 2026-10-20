package com.golemlogix.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.CryoJarBlockEntity;
import com.golemlogix.blocks.CryoJarBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import static com.golemlogix.client.CryoMainframeRenderer.box;
import static com.golemlogix.client.CryoMainframeRenderer.fitWall;

/**
 * The Cryo-Source Jar, two blocks high, drawn from its lower half: a dark brick plinth with a rune band (cold while empty, glowing
 * cyan with Source), two brass valves and a gauge; a glass flask in a verdigris copper frame where the icy Cryo-Source stands as
 * high as the jar is full; a louvred brass cap under snow with icicles and a vent pipe. A small copper golem clings to the right
 * side and turns the valve on the frame now and then.
 * <p>
 * Pixels in the north-facing frame: the front is Z 0, the viewer's right is -X; Y 0..32 over both halves.
 */
public class CryoJarRenderer implements BlockEntityRenderer<CryoJarBlockEntity> {
    static final Identifier BRICK = mc("nether_bricks"), DARK = tex("sat_dark"), BRASS = tex("sat_brass"), ICE = tex("sat_ice"),
            GLASS = tex("sat_glass"), RUNES = tex("cryo_panel_matrix"), GAUGE = tex("monitor_gauge"), FRAME = mc("weathered_copper"),
            SNOW = mc("snow"), CRYO = tex("cryo_source_still"), COPPER = mc("copper_block");
    static final int BRICK_TINT = 0xFFC89A8A, WHITE = 0xFFFFFFFF;
    /** The flask's inside, where the Source stands. */
    private static final float FLASK_BOTTOM = 8.5f, FLASK_TOP = 21.5f;

    static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "block/" + name);
    }

    static Identifier mc(String name) {
        return Identifier.withDefaultNamespace("block/" + name);
    }

    public CryoJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(CryoJarBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 1.5, p.getY() + 2.2, p.getZ() + 1.5);
    }

    @Override
    public void render(CryoJarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(CryoJarBlock.FACING) || state.getValue(CryoJarBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int light = LevelRenderer.getLightColor(level, pos.above());
        float fill = Mth.lerp(partialTick, be.fillPrev, be.fill);
        float time = level.getGameTime() + Math.floorMod(pos.asLong(), 61L) + partialTick;
        boolean charged = be.clientSource() > 0;

        pose.pushPose();
        Quads.faceModel(pose, state.getValue(CryoJarBlock.FACING));
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose p = pose.last();

        // the plinth: a dark foot, brick walls, a rune band on the front, a ledge
        box(vc, p, 0.6f, 0, 0.6f, 15.4f, 1.4f, 15.4f, DARK, WHITE, light);
        box(vc, p, 1, 1.4f, 1, 15, 6.4f, 15, BRICK, BRICK_TINT, light);
        box(vc, p, 0.8f, 6.4f, 0.8f, 15.2f, 7.6f, 15.2f, DARK, WHITE, light);
        float glow = charged ? 0.55f + 0.45f * (0.8f + 0.2f * Mth.sin(time * 0.08f)) : 0.18f;
        int rune = 0xFF000000 | ((int) (0x50 * glow) << 16) | ((int) (0xF0 * glow) << 8) | (int) (0xF0 * glow);
        fitWall(vc, p, 1.6f, 1.8f, 14.4f, 4.4f, 0.94f, RUNES, rune, charged ? LightCoordsUtil.FULL_BRIGHT : light);
        // two brass valves on the left of the front, a gauge on the right
        for (float x : new float[]{10.2f, 12.6f}) {
            box(vc, p, x, 4.4f, 0.3f, x + 1.2f, 5.6f, 1f, BRASS, WHITE, light);
            box(vc, p, x - 0.3f, 5.6f, 0.2f, x + 1.5f, 6.1f, 0.8f, BRASS, 0xFFE8C878, light);
        }
        fitWall(vc, p, 2.2f, 4.2f, 5.8f, 7.8f, 0.5f, GAUGE, WHITE, light);
        box(vc, p, 3.6f, 3.6f, 0.6f, 4.4f, 4.3f, 1f, BRASS, WHITE, light);

        // the frame: four verdigris posts and two rings
        for (float[] c : new float[][]{{2f, 2f}, {12.8f, 2f}, {2f, 12.8f}, {12.8f, 12.8f}}) {
            box(vc, p, c[0], 7.6f, c[1], c[0] + 1.2f, 22.4f, c[1] + 1.2f, FRAME, WHITE, light);
        }
        box(vc, p, 1.8f, 7.6f, 1.8f, 14.2f, 8.5f, 14.2f, FRAME, WHITE, light);
        box(vc, p, 1.8f, 21.5f, 1.8f, 14.2f, 22.4f, 14.2f, FRAME, WHITE, light);
        // the valve pipe up the right front post, with its wheel
        box(vc, p, 1.1f, 9f, 2.3f, 2.1f, 20.5f, 3.3f, COPPER, WHITE, light);

        // the cap: brass with louvres, snow on top, icicles, the vent pipe
        box(vc, p, 2.2f, 22.4f, 2.2f, 13.8f, 28f, 13.8f, BRASS, 0xFFD8B880, light);
        for (float y = 23.2f; y < 27.5f; y += 1.4f) {
            box(vc, p, 1.6f, y, 1.6f, 14.4f, y + 0.6f, 14.4f, BRASS, WHITE, light);
        }
        box(vc, p, 1.4f, 28f, 1.4f, 14.6f, 29.4f, 14.6f, SNOW, WHITE, light);
        for (float[] i : new float[][]{{2.4f, 1.6f}, {5.2f, 0.9f}, {8.6f, 2.1f}, {11.8f, 1.2f}}) {
            box(vc, p, i[0], 28f - i[1], 1.2f, i[0] + 0.6f, 28f, 1.8f, ICE, WHITE, light);
        }
        box(vc, p, 6.8f, 29.4f, 6.8f, 9.2f, 31.4f, 9.2f, BRASS, WHITE, light);
        box(vc, p, 6.6f, 31.4f, 6.6f, 9.4f, 32.4f, 9.4f, ICE, WHITE, light);

        // the golem on its bracket at the right side, its hands on the wheel; now and then it gives it a turn
        float turn = turnAngle(time, charged);
        wheel(vc, pose, 0.6f, 14.2f, 2.8f, 2.1f, turn, light);
        platform(vc, pose, -3.4f, 2.0f, 10.6f, 1.2f, 4.4f, 6.8f, 0.8f, light);
        float reach = charged ? -1.15f + 0.12f * Mth.sin(turn * Mth.DEG_TO_RAD) : -0.9f;
        CopperGolemPuppet.Arms arms = new CopperGolemPuppet.Arms(reach, 0.2f, reach, -0.2f, 0.15f * Mth.sin(time * 0.017f));
        CopperGolemPuppet.render(pose, buffers, light, new Vector3f(-1.8f / 16f, 11.2f / 16f, 2.8f / 16f), new Vector3f(1f, 0f, 0f),
                new Vector3f(0f, 1f, 0f), 0.36f, new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.08f), 1, null, arms);

        // the Cryo-Source and the glass over it
        VertexConsumer tv = buffers.getBuffer(RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        if (fill > 0.002f) {
            float top = FLASK_BOTTOM + (FLASK_TOP - FLASK_BOTTOM) * Math.min(1f, fill);
            int c = 0xE0FFFFFF;
            box(tv, p, 3.4f, FLASK_BOTTOM, 3.4f, 12.6f, top, 12.6f, CRYO, c, LightCoordsUtil.FULL_BRIGHT);
        }
        box(tv, p, 3.0f, 8.5f, 3.0f, 13.0f, 21.5f, 13.0f, GLASS, 0x90E8F8FF, light);
        pose.popPose();
    }

    /** Degrees the valve wheel has turned: a quarter turn every few seconds while the jar holds Source, otherwise still. */
    static float turnAngle(float time, boolean on) {
        if (!on) {
            return 0f;
        }
        float cycle = time / 70f;
        float k = cycle - (float) Math.floor(cycle);
        float ease = k < 0.25f ? Mth.sin(k / 0.25f * Mth.HALF_PI) : 1f;
        return ((float) Math.floor(cycle) + ease) * 90f;
    }

    /**
     * The golem's little platform on the machine's right side and what holds it: a dark grated deck from x0 out to the frame at x1
     * (y = its top), a brass bracket plate bolted to the frame, and two brass struts from the deck's outer edge down to the plinth
     * (at y footY, x footX), so it does not hang in the air.
     */
    static void platform(VertexConsumer vc, PoseStack pose, float x0, float x1, float y, float z0, float z1, float footY, float footX,
                         int light) {
        PoseStack.Pose p = pose.last();
        box(vc, p, x0, y - 0.6f, z0, x1, y, z1, DARK, WHITE, light);
        box(vc, p, x0 - 0.2f, y - 0.8f, z0 - 0.2f, x0 + 0.6f, y + 0.2f, z1 + 0.2f, BRASS, WHITE, light);
        // the bracket plate on the frame with two bolts
        box(vc, p, x1 - 0.5f, y - 2.4f, z0 + 0.2f, x1, y + 0.6f, z1 - 0.2f, BRASS, 0xFFD8B880, light);
        for (float z : new float[]{z0 + 0.8f, z1 - 1.2f}) {
            box(vc, p, x1 - 0.8f, y - 1.6f, z, x1 - 0.4f, y - 1.2f, z + 0.4f, DARK, WHITE, light);
        }
        // two struts from under the deck's outer edge down to the plinth
        for (float z : new float[]{z0 + 0.5f, z1 - 0.5f}) {
            CryoMainframeRenderer.limb(vc, pose, new Vector3f(x0 + 0.6f, y - 0.6f, z), new Vector3f(footX, footY, z), 0.55f, BRASS, light);
        }
        box(vc, p, footX - 0.3f, footY - 0.5f, z0 + 0.1f, footX + 0.3f, footY + 0.5f, z1 - 0.1f, BRASS, 0xFFC8A060, light);
    }

    /** A brass hand wheel in the YZ plane (its axle along X) at x, centred at (y, z), turned by angle degrees. */
    static void wheel(VertexConsumer vc, PoseStack pose, float x, float y, float z, float r, float angle, int light) {
        pose.pushPose();
        pose.translate(x / 16f, y / 16f, z / 16f);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        PoseStack.Pose p = pose.last();
        box(vc, p, -0.5f, -0.35f, -r, 0.5f, 0.35f, r, BRASS, WHITE, light);
        box(vc, p, -0.5f, -r, -0.35f, 0.5f, r, 0.35f, BRASS, WHITE, light);
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8f;
            float cy = Mth.sin(a) * r, cz = Mth.cos(a) * r;
            box(vc, p, -0.45f, cy - 0.45f, cz - 0.45f, 0.45f, cy + 0.45f, cz + 0.45f, BRASS, 0xFFF0D080, light);
        }
        box(vc, p, -0.7f, -0.6f, -0.6f, 0.7f, 0.6f, 0.6f, DARK, WHITE, light);
        pose.popPose();
    }
}
