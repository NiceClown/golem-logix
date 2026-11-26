package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.client.render.BerBuffers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

import static com.golemlogix.client.render.PixelMesh.PIXEL;

/**
 * Copper golem puppet for block-entity renderers (orbits around the Atlas Core, carrier golems on the Saturator, shelf
 * engineers on the Mainframe, valve tenders on jars/towers/mixers, archivists in the Drive Bay).
 * Uses the game's copper golem box geometry and oxidation textures (0..3). Stands in an upright frame: feet at {@code (0, 0, 0)},
 * head up ({@code +Y}), face forward ({@code +Z}).
 */
public final class CopperGolemPuppet {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "copper_golem"), "main");

    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier[] EYES = new Identifier[4];
    private static final Quaternionf ROT_X_180 = Axis.XP.rotationDegrees(180f);
    /** Scratch math objects on the render thread to avoid per-golem allocations every frame. */
    private static final Vector3f SCRATCH_F = new Vector3f();
    private static final Vector3f SCRATCH_U = new Vector3f();
    private static final Vector3f SCRATCH_X = new Vector3f();
    private static final Matrix3f SCRATCH_MAT = new Matrix3f();
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();

    static {
        String[] suffix = {"", "_exposed", "_weathered", "_oxidized"};
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = Identifier.withDefaultNamespace("textures/entity/copper_golem/copper_golem" + suffix[i] + ".png");
            EYES[i] = Identifier.withDefaultNamespace("textures/entity/copper_golem/copper_golem_eyes" + suffix[i] + ".png");
        }
    }

    private static EntityModelSet bakedFrom;
    private static ModelPart root, bodyPart, headPart, rightArmPart, leftArmPart, rightLegPart, leftLegPart;

    private CopperGolemPuppet() {
    }

    /** Model blueprint matching the vanilla copper golem dimensions and UV layout. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 15).addBox(-4f, -6f, -3f, 8f, 6f, 6f),
                PartPose.offset(0f, -5f, 0f));
        body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4f, -5f, -5f, 8f, 5f, 10f, new CubeDeformation(0.015f))
                        .texOffs(56, 0).addBox(-1f, -2f, -6f, 2f, 3f, 2f)
                        .texOffs(37, 8).addBox(-1f, -9f, -1f, 2f, 4f, 2f, new CubeDeformation(-0.015f))
                        .texOffs(37, 0).addBox(-2f, -13f, -2f, 4f, 4f, 4f, new CubeDeformation(-0.015f)),
                PartPose.offset(0f, -6f, 0f));
        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(36, 16).addBox(-3f, -1f, -2f, 3f, 10f, 4f),
                PartPose.offset(-4f, -6f, 0f));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(50, 16).addBox(0f, -1f, -2f, 3f, 10f, 4f),
                PartPose.offset(4f, -6f, 0f));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 27).addBox(-4f, 0f, -2f, 4f, 5f, 4f),
                PartPose.offset(0f, -5f, 0f));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(16, 27).addBox(0f, 0f, -2f, 4f, 5f, 4f),
                PartPose.offset(0f, -5f, 0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static ModelPart model() {
        EntityModelSet current = Minecraft.getInstance().getEntityModels();
        if (root == null || bakedFrom != current) {
            bakedFrom = current;
            root = current.bakeLayer(LAYER);
            bodyPart = root.getChild("body");
            headPart = bodyPart.getChild("head");
            rightArmPart = bodyPart.getChild("right_arm");
            leftArmPart = bodyPart.getChild("left_arm");
            rightLegPart = root.getChild("right_leg");
            leftLegPart = root.getChild("left_leg");
        }
        return root;
    }

    /** Resets the cached baked model on resource reload. */
    public static void invalidate() {
        bakedFrom = null;
        root = null;
        bodyPart = headPart = rightArmPart = leftArmPart = rightLegPart = leftLegPart = null;
    }

    /** Golem pose: {@code phase} = step phase (radians), {@code walking}, {@code carrying}, {@code bow} = forward lean (0..1). */
    public record Anim(float phase, boolean walking, boolean carrying, float bow) {
        public static Anim idle(float time) {
            return new Anim(time * 0.1f, false, false, 0f);
        }
    }

    /**
     * Custom arm and head angles (radians, ModelPart convention: {@code xRot < 0} raises forward/up, {@code zRot > 0} right /
     * {@code < 0} left abducts away from torso, {@code headYaw > 0} turns left) plus optional in-hand prop callbacks.
     */
    public record Arms(float rightX, float rightZ, float leftX, float leftZ, float headYaw,
                       @Nullable Consumer<PoseStack> heldRight,
                       @Nullable Consumer<PoseStack> heldLeft) {
        public Arms(float rightX, float rightZ, float leftX, float leftZ, float headYaw) {
            this(rightX, rightZ, leftX, leftZ, headYaw, null, null);
        }
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant) {
        render(pose, buffers, light, pos, forward, up, scale, anim, variant, null);
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant, @Nullable Consumer<PoseStack> held) {
        render(pose, buffers, light, pos, forward, up, scale, anim, variant, held, null);
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant, @Nullable Consumer<PoseStack> held, @Nullable Arms arms) {
        render(pose, buffers, light, pos, forward, up, scale, anim, variant, held, arms, null);
    }

    /**
     * Draws the golem at {@code pos} facing {@code forward} with head toward {@code up}, oxidation {@code variant} (0..3),
     * optional chest-carried prop {@code held} (defaults to a barrel when {@code anim.carrying}), optional {@code arms}
     * override, and optional {@code onHead} callback in the head's model frame (Y down, face to -Z).
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant, @Nullable Consumer<PoseStack> held,
                              @Nullable Arms arms, @Nullable Consumer<PoseStack> onHead) {
        Quaternionf q = buildBasisRotation(forward, up);

        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(q);
        pose.scale(scale, scale, scale);

        if (anim.carrying) {
            renderChestCarry(pose, buffers, light, anim, held);
        }

        // convert from upright frame (+Y up, +Z forward) to ModelPart frame (Y down, face -Z)
        pose.mulPose(ROT_X_180);

        ModelPart m = model();
        applyPose(anim, arms);

        int v = Mth.clamp(variant, 0, 3);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TEXTURES[v]));
        m.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        VertexConsumer eyes = buffers.getBuffer(RenderTypes.eyes(EYES[v]));
        m.render(pose, eyes, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        renderAttachments(pose, arms, onHead);
        pose.popPose();
    }

    /** Orthonormal basis quaternion aligning local +Z to {@code forward} and +Y to orthogonalized {@code up}. */
    private static Quaternionf buildBasisRotation(Vector3f forward, Vector3f up) {
        Vector3f f = SCRATCH_F.set(forward).normalize();
        float dot = up.dot(f);
        Vector3f u = SCRATCH_U.set(up.x - f.x * dot, up.y - f.y * dot, up.z - f.z * dot).normalize();
        Vector3f x = SCRATCH_X.set(u).cross(f).normalize();
        return SCRATCH_QUAT.setFromNormalized(SCRATCH_MAT.set(x.x, x.y, x.z, u.x, u.y, u.z, f.x, f.y, f.z));
    }

    private static void renderChestCarry(PoseStack pose, MultiBufferSource buffers, int light, Anim anim,
                                         @Nullable Consumer<PoseStack> held) {
        float lift = Mth.sin(anim.phase * 2f) * 0.01f;
        pose.pushPose();
        pose.translate(0f, 0.64f - anim.bow * 0.14f + lift, 0.38f + anim.bow * 0.06f);
        if (held != null) {
            held.accept(pose);
        } else {
            pose.scale(0.34f, 0.34f, 0.34f);
            pose.translate(-0.5, -0.5, -0.5);
            BerBuffers.block(buffers, Blocks.BARREL.defaultBlockState(), pose, light);
        }
        pose.popPose();
    }

    private static void applyPose(Anim anim, @Nullable Arms arms) {
        float swing = anim.walking ? Mth.sin(anim.phase) * 0.9f : 0f;
        rightLegPart.xRot = swing;
        leftLegPart.xRot = -swing;
        if (anim.carrying) {
            float raise = -1.25f + Mth.sin(anim.phase * 2f) * 0.03f;
            rightArmPart.xRot = raise;
            leftArmPart.xRot = raise;
            rightArmPart.zRot = 0.14f;
            leftArmPart.zRot = -0.14f;
        } else {
            rightArmPart.xRot = -swing * 0.7f;
            leftArmPart.xRot = swing * 0.7f;
            rightArmPart.zRot = 0f;
            leftArmPart.zRot = 0f;
        }
        bodyPart.y = -5f - (anim.walking ? Math.abs(Mth.sin(anim.phase)) * 0.6f : 0f);
        bodyPart.xRot = anim.bow * 0.35f;
        headPart.xRot = anim.bow * 0.5f + (anim.walking ? 0f : Mth.sin(anim.phase * 0.7f) * 0.05f);
        headPart.yRot = 0f;
        if (arms != null) {
            rightArmPart.xRot = arms.rightX();
            rightArmPart.zRot = arms.rightZ();
            leftArmPart.xRot = arms.leftX();
            leftArmPart.zRot = arms.leftZ();
            headPart.yRot = arms.headYaw();
        }
    }

    private static void renderAttachments(PoseStack pose, @Nullable Arms arms, @Nullable Consumer<PoseStack> onHead) {
        if (onHead != null) {
            pose.pushPose();
            bodyPart.translateAndRotate(pose);
            headPart.translateAndRotate(pose);
            onHead.accept(pose);
            pose.popPose();
        }
        if (arms != null) {
            if (arms.heldRight() != null) {
                pose.pushPose();
                bodyPart.translateAndRotate(pose);
                rightArmPart.translateAndRotate(pose);
                pose.translate(-1.5f * PIXEL, 8.5f * PIXEL, 0f);
                arms.heldRight().accept(pose);
                pose.popPose();
            }
            if (arms.heldLeft() != null) {
                pose.pushPose();
                bodyPart.translateAndRotate(pose);
                leftArmPart.translateAndRotate(pose);
                pose.translate(1.5f * PIXEL, 8.5f * PIXEL, 0f);
                arms.heldLeft().accept(pose);
                pose.popPose();
            }
        }
    }
}
