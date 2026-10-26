package com.golemlogix.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * A snowman "puppet" built from the real snow golem model (two snowballs, a head, stick arms) for renderers: runners in
 * pipes, assemblers on calculators, the foreman with an atlant head. The arms do not stick out sideways as on the golem but reach for
 * given points, for example wrapping a crate in front of the chest.
 * <p>
 * The puppet stands in an "upright" frame: feet at (0, 0, 0), top of the head up (+Y), face forward (+Z); hand and crate points are in
 * model pixels of this frame (the snowman's height without the head is 24, shoulders at height 18).
 */
public final class SnowmanPuppet {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/snow_golem/snow_golem.png");
    /** Shoulders (the left one is +X: facing +Z, the snowman's left arm points to +X). */
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 18f, 0f), RIGHT_SHOULDER = new Vector3f(-5f, 18f, 0f);
    /** The hands wrap a crate in front of the chest: palms on the sides of the crate. */
    public static final Vector3f HOLD_LEFT = new Vector3f(3.2f, 13.5f, 9f), HOLD_RIGHT = new Vector3f(-3.2f, 13.5f, 9f);
    /** Middle of the crate in the hands. */
    public static final Vector3f HOLD_CENTER = new Vector3f(0f, 13.5f, 9.5f);
    /** Arms lowered along the body (a little to the sides). */
    public static final Vector3f REST_LEFT = new Vector3f(9f, 9f, 1.5f), REST_RIGHT = new Vector3f(-9f, 9f, 1.5f);

    public enum Head { SNOW, PUMPKIN, ATLANT }

    /**
     * Pose: bob = hop (pixels), squash = flattening (0 = none, 0.2 = noticeable), headYaw/headPitch in degrees,
     * leftHand/rightHand = where the hands reach.
     */
    public record Pose(float bob, float squash, float headYaw, float headPitch, Vector3f leftHand, Vector3f rightHand) {
        public static Pose holding(float bob) {
            return new Pose(bob, 0f, 0f, 8f, HOLD_LEFT, HOLD_RIGHT);
        }

        public static Pose rest() {
            return new Pose(0f, 0f, 0f, 0f, REST_LEFT, REST_RIGHT);
        }
    }

    private static ModelPart root;

    private SnowmanPuppet() {
    }

    private static ModelPart model() {
        if (root == null) {
            root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SNOW_GOLEM);
        }
        return root;
    }

    /** Reset the cache on resource reload. */
    public static void invalidate() {
        root = null;
    }

    /**
     * Draws the snowman. pos = feet (in the current pose frame), yawDeg = rotation around the vertical (0 = facing +Z of the current
     * pose), scale = model scale (1 = the vanilla snow golem, ~1.75 blocks). held = what is in its hands, drawn in the "upright"
     * frame with the origin at HOLD_CENTER (1 = model pixel / 16).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, float yawDeg, float scale, Head head,
                              Pose p, @Nullable Consumer<PoseStack> held) {
        ModelPart m = model();
        ModelPart headPart = m.getChild("head");
        ModelPart upper = m.getChild("upper_body");
        ModelPart lower = m.getChild("lower_body");
        ModelPart leftArm = m.getChild("left_arm");
        ModelPart rightArm = m.getChild("right_arm");

        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(yawDeg));
        pose.scale(scale, scale, scale);
        pose.translate(0f, p.bob() / 16f, 0f);
        if (p.squash() != 0f) {
            pose.scale(1f + p.squash() * 0.5f, 1f - p.squash(), 1f + p.squash() * 0.5f);
        }
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TEXTURE));

        // arms: in the "upright" frame from the shoulder to the hand (the model's stick lies along +X)
        arm(pose, vc, light, leftArm, LEFT_SHOULDER, p.leftHand());
        arm(pose, vc, light, rightArm, RIGHT_SHOULDER, p.rightHand());
        if (held != null) {
            pose.pushPose();
            pose.translate(HOLD_CENTER.x / 16f, HOLD_CENTER.y / 16f, HOLD_CENTER.z / 16f);
            held.accept(pose);
            pose.popPose();
        }

        // body and head: in the model frame (Y down, face to -Z, feet at y = 24)
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(180f));
        pose.translate(0f, -1.5f, 0f);
        headPart.yRot = -p.headYaw() * ((float) Math.PI / 180f);
        headPart.xRot = -p.headPitch() * ((float) Math.PI / 180f);
        upper.yRot = headPart.yRot * 0.25f;
        lower.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        upper.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        if (head == Head.SNOW) {
            headPart.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        } else {
            pose.pushPose();
            headPart.translateAndRotate(pose);
            pose.translate(0f, -4f / 16f, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(180f));
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            var mc = Minecraft.getInstance();
            if (head == Head.PUMPKIN) {
                pose.scale(0.625f, 0.625f, 0.625f);
                pose.translate(-0.5f, -0.5f, -0.5f);
                com.golemlogix.client.render.BerBuffers.block(buffers, Blocks.CARVED_PUMPKIN.defaultBlockState(), pose, light);
            } else {
                // the copper atlant head (the iron golem's 8x10x8, centred at (8, 11, 6)): in the snow head's place
                pose.translate(-0.5f, -11f / 16f, -6f / 16f);
                com.golemlogix.client.render.StandaloneModels.render(pose.last(), buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), AtlantHeadRenderer.HEAD, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }
        pose.popPose();
        pose.popPose();
    }

    private static void arm(PoseStack pose, VertexConsumer vc, int light, ModelPart arm, Vector3f shoulder, Vector3f hand) {
        Vector3f d = new Vector3f(hand).sub(shoulder);
        if (d.lengthSquared() < 1e-4f) {
            d.set(1f, 0f, 0f);
        }
        d.normalize();
        pose.pushPose();
        pose.translate(shoulder.x / 16f, shoulder.y / 16f, shoulder.z / 16f);
        pose.mulPose(new Quaternionf().rotationTo(1f, 0f, 0f, d.x, d.y, d.z));
        arm.x = arm.y = arm.z = 0f;
        arm.xRot = arm.yRot = arm.zRot = 0f;
        arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
