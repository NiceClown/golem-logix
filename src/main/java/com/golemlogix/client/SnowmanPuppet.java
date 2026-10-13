package com.golemlogix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * «Кукла» снеговика из настоящей модели снежного голема (два снежных шара, голова, руки-палки) для отрисовщиков: бегуны в
 * трубах, сборщики на калькуляторах, бригадир с головой атланта. Руки не торчат в стороны, как у голема, а тянутся к
 * заданным точкам — например, обхватывают ящик перед грудью.
 * <p>
 * Кукла стоит в «прямой» системе: ноги в (0, 0, 0), макушка вверх (+Y), лицо вперёд (+Z); точки рук и ящика — в пикселях
 * модели этой системы (рост снеговика без головы — 24, плечи на высоте 18).
 */
public final class SnowmanPuppet {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/snow_golem.png");
    /** Плечи (левое — +X: лицом к +Z левая рука снеговика смотрит на +X). */
    private static final Vector3f LEFT_SHOULDER = new Vector3f(5f, 18f, 0f), RIGHT_SHOULDER = new Vector3f(-5f, 18f, 0f);
    /** Руки обхватывают ящик перед грудью: кисти по бокам ящика. */
    public static final Vector3f HOLD_LEFT = new Vector3f(3.2f, 13.5f, 9f), HOLD_RIGHT = new Vector3f(-3.2f, 13.5f, 9f);
    /** Середина ящика в руках. */
    public static final Vector3f HOLD_CENTER = new Vector3f(0f, 13.5f, 9.5f);
    /** Руки опущены вдоль тела (чуть в стороны). */
    public static final Vector3f REST_LEFT = new Vector3f(9f, 9f, 1.5f), REST_RIGHT = new Vector3f(-9f, 9f, 1.5f);

    public enum Head { SNOW, PUMPKIN, ATLANT }

    /**
     * Поза: bob — подскок (пиксели), squash — сплющивание (0 — нет, 0.2 — заметно), headYaw/headPitch — градусы,
     * leftHand/rightHand — куда тянутся кисти.
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

    /** Сбросить кэш при перезагрузке ресурсов. */
    public static void invalidate() {
        root = null;
    }

    /**
     * Рисует снеговика. pos — ноги (в системе текущей позы), yawDeg — поворот вокруг вертикали (0 — лицом к +Z текущей
     * позы), scale — масштаб модели (1 — ванильный снежный голем ~1.75 блока). held — что в руках, рисуется в «прямой»
     * системе с началом в HOLD_CENTER (1 = пиксель модели / 16).
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
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        // руки: в «прямой» системе от плеча к кисти (палка модели лежит вдоль +X)
        arm(pose, vc, light, leftArm, LEFT_SHOULDER, p.leftHand());
        arm(pose, vc, light, rightArm, RIGHT_SHOULDER, p.rightHand());
        if (held != null) {
            pose.pushPose();
            pose.translate(HOLD_CENTER.x / 16f, HOLD_CENTER.y / 16f, HOLD_CENTER.z / 16f);
            held.accept(pose);
            pose.popPose();
        }

        // тело и голова: в системе модели (Y вниз, лицо к -Z, ноги на y = 24)
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
                mc.getBlockRenderer().renderSingleBlock(Blocks.CARVED_PUMPKIN.defaultBlockState(), pose, buffers, light,
                        OverlayTexture.NO_OVERLAY);
            } else {
                // чёрная голова атланта (модель 8x6x8, шея в (8, 10, 8)): чуть крупнее снежной, на её месте
                pose.scale(1.15f, 1.15f, 1.15f);
                pose.translate(-0.5f, -13f / 16f, -0.5f);
                mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null,
                        mc.getModelManager().getModel(AtlantHeadRenderer.HEAD), 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, RenderType.cutout());
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
