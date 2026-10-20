package com.golemlogix.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import com.golemlogix.GolemLogixMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Copper golem "puppet" for block entity renderers (orbits around the core, the ring on the top face of the array).
 * The model and textures come from the Copper Age Backport mod (CC0): an 8x6x6 body, a wide head, a ball antenna, long arms.
 * The puppet stands in an "upright" frame: feet at (0,0,0), head up (+Y), face looking forward (+Z).
 */
public final class CopperGolemPuppet {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "copper_golem"), "main");

    private static final Identifier[] TEXTURES = new Identifier[4];
    private static final Identifier[] EYES = new Identifier[4];

    static {
        String[] prefix = {"", "exposed_", "weathered_", "oxidized_"};
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/" + prefix[i] + "copper_golem.png");
            EYES[i] = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/" + prefix[i] + "copper_golem_eyes.png");
        }
    }

    private static ModelPart root;

    private CopperGolemPuppet() {
    }

    /** Model blueprint (coordinates and texture offsets as in the original). */
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
        if (root == null) {
            root = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
        }
        return root;
    }

    /** Reset the cache on resource reload. */
    public static void invalidate() {
        root = null;
    }

    /** Golem pose: phase = step phase (radians), walking = whether it walks, carrying = whether it holds a crate, bow = lean (0..1). */
    public record Anim(float phase, boolean walking, boolean carrying, float bow) {
        public static Anim idle(float time) {
            return new Anim(time * 0.1f, false, false, 0f);
        }
    }

    /**
     * Draws the golem. pos = feet position, forward = where the face looks, up = where the top of the head points (both in the
     * current pose frame), scale = scale (1 = one block tall; a real golem is ~1 block without the antenna), variant 0..3 = oxidation stage.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant) {
        render(pose, buffers, light, pos, forward, up, scale, anim, variant, null);
    }

    /**
     * The same, but when carrying it holds its own thing instead of a crate: held draws it in the "in front of the chest" frame, where 1 is the
     * golem's height, the origin is the middle of the grip, Y up, Z forward.
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant, @org.jetbrains.annotations.Nullable java.util.function.Consumer<PoseStack> held) {
        render(pose, buffers, light, pos, forward, up, scale, anim, variant, held, null);
    }

    /**
     * Custom pose of arms and head (radians, as in ModelPart: xRot < 0 moves an arm forward and up, zRot > 0 on the right one and < 0 on the left
     * moves it away from the body; headYaw > 0 turns the head left). Also supports drawing items right in the hands.
     */
    public record Arms(float rightX, float rightZ, float leftX, float leftZ, float headYaw,
                       @org.jetbrains.annotations.Nullable java.util.function.Consumer<PoseStack> heldRight,
                       @org.jetbrains.annotations.Nullable java.util.function.Consumer<PoseStack> heldLeft) {
        public Arms(float rightX, float rightZ, float leftX, float leftZ, float headYaw) {
            this(rightX, rightZ, leftX, leftZ, headYaw, null, null);
        }
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, Vector3f pos, Vector3f forward, Vector3f up,
                              float scale, Anim anim, int variant, @org.jetbrains.annotations.Nullable java.util.function.Consumer<PoseStack> held,
                              @org.jetbrains.annotations.Nullable Arms arms) {
        Vector3f f = new Vector3f(forward).normalize();
        Vector3f u = new Vector3f(up);
        u.sub(new Vector3f(f).mul(u.dot(f))).normalize();
        Vector3f x = new Vector3f(u).cross(f).normalize();
        Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, u.x, u.y, u.z, f.x, f.y, f.z));

        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(q);
        pose.scale(scale, scale, scale);

        if (anim.carrying) {
            // a crate in outstretched arms in front of the chest (in the upright frame: Z forward, Y up; 16 pixels = 1)
            BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
            float lift = Mth.sin(anim.phase * 2f) * 0.01f;
            pose.pushPose();
            pose.translate(0f, 0.64f - anim.bow * 0.14f + lift, 0.38f + anim.bow * 0.06f);
            if (held != null) {
                held.accept(pose);
            } else {
                pose.scale(0.34f, 0.34f, 0.34f);
                pose.translate(-0.5, -0.5, -0.5);
                dispatcher.renderSingleBlock(Blocks.BARREL.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
        }

        // from the "upright" frame into the model frame (Y down, face to -Z): a 180° turn around X
        pose.mulPose(Axis.XP.rotationDegrees(180f));

        ModelPart m = model();
        ModelPart body = m.getChild("body");
        ModelPart head = body.getChild("head");
        ModelPart rightArm = body.getChild("right_arm");
        ModelPart leftArm = body.getChild("left_arm");
        ModelPart rightLeg = m.getChild("right_leg");
        ModelPart leftLeg = m.getChild("left_leg");

        float swing = anim.walking ? Mth.sin(anim.phase) * 0.9f : 0f;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        if (anim.carrying) {
            float raise = -1.25f + Mth.sin(anim.phase * 2f) * 0.03f;
            rightArm.xRot = raise;
            leftArm.xRot = raise;
            rightArm.zRot = 0.14f;
            leftArm.zRot = -0.14f;
        } else {
            rightArm.xRot = -swing * 0.7f;
            leftArm.xRot = swing * 0.7f;
            rightArm.zRot = 0f;
            leftArm.zRot = 0f;
        }
        body.y = -5f - (anim.walking ? Math.abs(Mth.sin(anim.phase)) * 0.6f : 0f);
        body.xRot = anim.bow * 0.35f;
        head.xRot = anim.bow * 0.5f + (anim.walking ? 0f : Mth.sin(anim.phase * 0.7f) * 0.05f);
        head.yRot = 0f;
        if (arms != null) {
            rightArm.xRot = arms.rightX();
            rightArm.zRot = arms.rightZ();
            leftArm.xRot = arms.leftX();
            leftArm.zRot = arms.leftZ();
            head.yRot = arms.headYaw();
        }

        int v = Mth.clamp(variant, 0, 3);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TEXTURES[v]));
        m.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        VertexConsumer eyes = buffers.getBuffer(RenderTypes.eyes(EYES[v]));
        m.render(pose, eyes, 0xF000F0, OverlayTexture.NO_OVERLAY);

        if (arms != null) {
            if (arms.heldRight() != null) {
                pose.pushPose();
                body.translateAndRotate(pose);
                rightArm.translateAndRotate(pose);
                pose.translate(-1.5f / 16f, 8.5f / 16f, 0f);
                arms.heldRight().accept(pose);
                pose.popPose();
            }
            if (arms.heldLeft() != null) {
                pose.pushPose();
                body.translateAndRotate(pose);
                leftArm.translateAndRotate(pose);
                pose.translate(1.5f / 16f, 8.5f / 16f, 0f);
                arms.heldLeft().accept(pose);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
