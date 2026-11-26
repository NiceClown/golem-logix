package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.BlockQuads;
import com.golemlogix.client.render.Sprites;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
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
    /** Scratch math objects on the render thread to avoid per-golem allocations and PoseStack mixin overhead every frame. */
    private static final Vector3f SCRATCH_F = new Vector3f();
    private static final Vector3f SCRATCH_U = new Vector3f();
    private static final Vector3f SCRATCH_X = new Vector3f();
    private static final Matrix3f SCRATCH_MAT = new Matrix3f();
    private static final Quaternionf SCRATCH_QUAT = new Quaternionf();
    private static final Quaternionf SCRATCH_PART_QUAT = new Quaternionf();
    private static final Matrix4f BASE_MAT = new Matrix4f();
    private static final Matrix3f BASE_NORM = new Matrix3f();
    private static final Matrix4f MODEL_MAT = new Matrix4f();
    private static final Matrix3f MODEL_NORM = new Matrix3f();
    private static final Matrix4f BODY_MAT = new Matrix4f();
    private static final Matrix3f BODY_NORM = new Matrix3f();
    private static final Matrix4f PART_MAT = new Matrix4f();
    private static final Matrix3f PART_NORM = new Matrix3f();
    private static final PoseStack BARREL_POSE = new PoseStack();

    static {
        String[] suffix = {"", "_exposed", "_weathered", "_oxidized"};
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = Identifier.withDefaultNamespace("textures/entity/copper_golem/copper_golem" + suffix[i] + ".png");
            EYES[i] = Identifier.withDefaultNamespace("textures/entity/copper_golem/copper_golem_eyes" + suffix[i] + ".png");
        }
    }

    private static EntityModelSet bakedFrom;
    private static ModelPart root, bodyPart, headPart, rightArmPart, leftArmPart, rightLegPart, leftLegPart;
    private static PartMesh bodyMesh, headMesh, eyesMesh, rightArmMesh, leftArmMesh, rightLegMesh, leftLegMesh;
    private static TextureAtlasSprite barrelAtlasMark;
    private static BerBuffers.Baked carriedBarrelMesh;

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

    private static void ensureBaked() {
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

            bodyMesh = capturePart(bodyPart, false, headPart, rightArmPart, leftArmPart);
            headMesh = capturePart(headPart, false);
            eyesMesh = capturePart(headPart, true);
            rightArmMesh = capturePart(rightArmPart, false);
            leftArmMesh = capturePart(leftArmPart, false);
            rightLegMesh = capturePart(rightLegPart, false);
            leftLegMesh = capturePart(leftLegPart, false);
        }
    }

    private static BerBuffers.Baked carriedBarrel() {
        TextureAtlasSprite mark = Sprites.block(MachineParts.BRASS);
        if (carriedBarrelMesh == null || barrelAtlasMark != mark) {
            barrelAtlasMark = mark;
            carriedBarrelMesh = BerBuffers.bake((vc, p) -> {
                p.scale(0.34f, 0.34f, 0.34f);
                p.translate(-0.5f, -0.5f, -0.5f);
                BlockQuads.draw(p.last(), vc, Blocks.BARREL.defaultBlockState(), 0, OverlayTexture.NO_OVERLAY);
            });
        }
        return carriedBarrelMesh;
    }

    /** Resets the cached baked model on resource reload. */
    public static void invalidate() {
        bakedFrom = null;
        root = null;
        bodyPart = headPart = rightArmPart = leftArmPart = rightLegPart = leftLegPart = null;
        bodyMesh = headMesh = eyesMesh = rightArmMesh = leftArmMesh = rightLegMesh = leftLegMesh = null;
        barrelAtlasMark = null;
        carriedBarrelMesh = null;
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
        ensureBaked();
        Quaternionf q = buildBasisRotation(forward, up);

        PoseStack.Pose caller = pose.last();
        BASE_MAT.set(caller.pose()).translate(pos.x, pos.y, pos.z).rotate(q).scale(scale);
        BASE_NORM.set(caller.normal()).rotate(q);

        if (anim.carrying && held == null) {
            float lift = Mth.sin(anim.phase * 2f) * 0.01f;
            PoseStack.Pose bp = BARREL_POSE.last();
            bp.pose().set(BASE_MAT).translate(0f, 0.64f - anim.bow * 0.14f + lift, 0.38f + anim.bow * 0.06f);
            bp.normal().set(BASE_NORM);
            carriedBarrel().draw(buffers, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), bp, light, 0);
        }

        // Compute joint angles (matching ModelPart conventions)
        float swing = anim.walking ? Mth.sin(anim.phase) * 0.9f : 0f;
        float rightLegX = swing;
        float leftLegX = -swing;
        float rightArmX, rightArmZ, leftArmX, leftArmZ;
        if (anim.carrying) {
            float raise = -1.25f + Mth.sin(anim.phase * 2f) * 0.03f;
            rightArmX = raise;
            leftArmX = raise;
            rightArmZ = 0.14f;
            leftArmZ = -0.14f;
        } else {
            rightArmX = -swing * 0.7f;
            leftArmX = swing * 0.7f;
            rightArmZ = 0f;
            leftArmZ = 0f;
        }
        float bodyY = -5f - (anim.walking ? Math.abs(Mth.sin(anim.phase)) * 0.6f : 0f);
        float bodyX = anim.bow * 0.35f;
        float headX = anim.bow * 0.5f + (anim.walking ? 0f : Mth.sin(anim.phase * 0.7f) * 0.05f);
        float headYaw = 0f;
        if (arms != null) {
            rightArmX = arms.rightX();
            rightArmZ = arms.rightZ();
            leftArmX = arms.leftX();
            leftArmZ = arms.leftZ();
            headYaw = arms.headYaw();
        }

        // Convert from upright frame (+Y up, +Z forward) to ModelPart frame (+Y down, face -Z) without PoseStack.pushPose
        MODEL_MAT.set(BASE_MAT).rotateX(Mth.PI);
        MODEL_NORM.set(BASE_NORM).rotateX(Mth.PI);

        int v = Mth.clamp(variant, 0, 3);
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TEXTURES[v]));

        // Legs (children of root)
        PART_MAT.set(MODEL_MAT).translate(0f, -5f * PIXEL, 0f).rotateX(rightLegX);
        PART_NORM.set(MODEL_NORM).rotateX(rightLegX);
        rightLegMesh.emit(vc, PART_MAT, PART_NORM, light);

        PART_MAT.set(MODEL_MAT).translate(0f, -5f * PIXEL, 0f).rotateX(leftLegX);
        PART_NORM.set(MODEL_NORM).rotateX(leftLegX);
        leftLegMesh.emit(vc, PART_MAT, PART_NORM, light);

        // Body (child of root)
        BODY_MAT.set(MODEL_MAT).translate(0f, bodyY * PIXEL, 0f).rotateX(bodyX);
        BODY_NORM.set(MODEL_NORM).rotateX(bodyX);
        bodyMesh.emit(vc, BODY_MAT, BODY_NORM, light);

        // Arms (children of body)
        SCRATCH_PART_QUAT.rotationZYX(rightArmZ, 0f, rightArmX);
        PART_MAT.set(BODY_MAT).translate(-4f * PIXEL, -6f * PIXEL, 0f).rotate(SCRATCH_PART_QUAT);
        PART_NORM.set(BODY_NORM).rotate(SCRATCH_PART_QUAT);
        rightArmMesh.emit(vc, PART_MAT, PART_NORM, light);

        SCRATCH_PART_QUAT.rotationZYX(leftArmZ, 0f, leftArmX);
        PART_MAT.set(BODY_MAT).translate(4f * PIXEL, -6f * PIXEL, 0f).rotate(SCRATCH_PART_QUAT);
        PART_NORM.set(BODY_NORM).rotate(SCRATCH_PART_QUAT);
        leftArmMesh.emit(vc, PART_MAT, PART_NORM, light);

        // Head (child of body) + single-quad glowing eyes reuse the exact same head matrices
        SCRATCH_PART_QUAT.rotationZYX(0f, headYaw, headX);
        PART_MAT.set(BODY_MAT).translate(0f, -6f * PIXEL, 0f).rotate(SCRATCH_PART_QUAT);
        PART_NORM.set(BODY_NORM).rotate(SCRATCH_PART_QUAT);
        headMesh.emit(vc, PART_MAT, PART_NORM, light);

        VertexConsumer eyes = buffers.getBuffer(RenderTypes.eyes(EYES[v]));
        eyesMesh.emit(eyes, PART_MAT, PART_NORM, LightCoordsUtil.FULL_BRIGHT);

        boolean hasHeldCallback = anim.carrying && held != null;
        boolean hasAttachmentCallbacks = onHead != null
                || (arms != null && (arms.heldRight() != null || arms.heldLeft() != null));
        if (hasHeldCallback || hasAttachmentCallbacks) {
            pose.pushPose();
            pose.translate(pos.x, pos.y, pos.z);
            pose.mulPose(q);
            pose.scale(scale, scale, scale);
            if (hasHeldCallback) {
                float lift = Mth.sin(anim.phase * 2f) * 0.01f;
                pose.pushPose();
                pose.translate(0f, 0.64f - anim.bow * 0.14f + lift, 0.38f + anim.bow * 0.06f);
                held.accept(pose);
                pose.popPose();
            }
            if (hasAttachmentCallbacks) {
                bodyPart.y = bodyY;
                bodyPart.xRot = bodyX;
                headPart.xRot = headX;
                headPart.yRot = headYaw;
                rightArmPart.xRot = rightArmX;
                rightArmPart.zRot = rightArmZ;
                leftArmPart.xRot = leftArmX;
                leftArmPart.zRot = leftArmZ;
                pose.mulPose(ROT_X_180);
                renderAttachments(pose, arms, onHead);
            }
            pose.popPose();
        }
    }

    /** Orthonormal basis quaternion aligning local +Z to {@code forward} and +Y to orthogonalized {@code up}. */
    private static Quaternionf buildBasisRotation(Vector3f forward, Vector3f up) {
        Vector3f f = SCRATCH_F.set(forward).normalize();
        float dot = up.dot(f);
        Vector3f u = SCRATCH_U.set(up.x - f.x * dot, up.y - f.y * dot, up.z - f.z * dot).normalize();
        Vector3f x = SCRATCH_X.set(u).cross(f).normalize();
        return SCRATCH_QUAT.setFromNormalized(SCRATCH_MAT.set(x.x, x.y, x.z, u.x, u.y, u.z, f.x, f.y, f.z));
    }

    private static PartMesh capturePart(ModelPart part, boolean filterEyes, ModelPart... childrenToHide) {
        for (ModelPart c : childrenToHide) {
            c.visible = false;
        }
        part.resetPose();
        part.x = 0f;
        part.y = 0f;
        part.z = 0f;
        part.xRot = 0f;
        part.yRot = 0f;
        part.zRot = 0f;
        CaptureConsumer cap = new CaptureConsumer();
        part.render(new PoseStack(), cap, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        part.resetPose();
        for (ModelPart c : childrenToHide) {
            c.visible = true;
        }
        return cap.toMesh(filterEyes);
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

    /** Pre-baked rigid ModelPart vertices (stride 8: x, y, z, u, v, nx, ny, nz). */
    private static final class PartMesh {
        private final float[] data;
        private final int vertexCount;

        PartMesh(float[] data, int vertexCount) {
            this.data = data;
            this.vertexCount = vertexCount;
        }

        void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, int light) {
            float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
            float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
            float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
            float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
            float n00 = nm.m00(), n01 = nm.m01(), n02 = nm.m02();
            float n10 = nm.m10(), n11 = nm.m11(), n12 = nm.m12();
            float n20 = nm.m20(), n21 = nm.m21(), n22 = nm.m22();
            float[] d = this.data;
            int total = this.vertexCount;
            int ov = OverlayTexture.NO_OVERLAY;
            float tnx = 0f, tny = 1f, tnz = 0f;
            for (int v = 0, b = 0; v < total; v++, b += 8) {
                if ((v & 3) == 0) {
                    float nx = d[b + 5], ny = d[b + 6], nz = d[b + 7];
                    tnx = n00 * nx + n10 * ny + n20 * nz;
                    tny = n01 * nx + n11 * ny + n21 * nz;
                    tnz = n02 * nx + n12 * ny + n22 * nz;
                    float lenSq = tnx * tnx + tny * tny + tnz * tnz;
                    if (Math.abs(lenSq - 1f) > 1e-3f && lenSq > 1e-8f) {
                        float inv = Mth.invSqrt(lenSq);
                        tnx *= inv;
                        tny *= inv;
                        tnz *= inv;
                    }
                }
                float x = d[b], y = d[b + 1], z = d[b + 2];
                vc.addVertex(
                        m00 * x + m10 * y + m20 * z + m30,
                        m01 * x + m11 * y + m21 * z + m31,
                        m02 * x + m12 * y + m22 * z + m32,
                        -1, d[b + 3], d[b + 4], ov, light, tnx, tny, tnz);
            }
        }
    }

    private static final class CaptureConsumer implements VertexConsumer {
        private float[] buf = new float[8 * 128];
        private int count;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            int b = count++ * 8;
            if (b + 8 > buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[b] = x;
            buf[b + 1] = y;
            buf[b + 2] = z;
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords,
                              float nx, float ny, float nz) {
            int b = count++ * 8;
            if (b + 8 > buf.length) {
                buf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[b] = x;
            buf[b + 1] = y;
            buf[b + 2] = z;
            buf[b + 3] = u;
            buf[b + 4] = v;
            buf[b + 5] = nx;
            buf[b + 6] = ny;
            buf[b + 7] = nz;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            int b = (count - 1) * 8;
            buf[b + 3] = u;
            buf[b + 4] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            int b = (count - 1) * 8;
            buf[b + 5] = x;
            buf[b + 6] = y;
            buf[b + 7] = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        PartMesh toMesh(boolean filterEyes) {
            if (!filterEyes) {
                return new PartMesh(Arrays.copyOf(buf, count * 8), count);
            }
            // In copper_golem_eyes*.png (64x64), only pixels x=[11..16], y=[12..13] have non-zero alpha (head front quad).
            float[] out = new float[count * 8];
            int outVerts = 0;
            for (int v = 0; v + 3 < count; v += 4) {
                float minU = 1f, maxU = 0f, minV = 1f, maxV = 0f;
                for (int k = 0; k < 4; k++) {
                    float u = buf[(v + k) * 8 + 3];
                    float vt = buf[(v + k) * 8 + 4];
                    if (u < minU) minU = u;
                    if (u > maxU) maxU = u;
                    if (vt < minV) minV = vt;
                    if (vt > maxV) maxV = vt;
                }
                if (maxU > 10.5f / 64f && minU < 17.5f / 64f && maxV > 11.5f / 64f && minV < 14.5f / 64f) {
                    System.arraycopy(buf, v * 8, out, outVerts * 8, 4 * 8);
                    outVerts += 4;
                }
            }
            if (outVerts == 0) {
                return new PartMesh(Arrays.copyOf(buf, count * 8), count);
            }
            return new PartMesh(Arrays.copyOf(out, outVerts * 8), outVerts);
        }
    }
}
