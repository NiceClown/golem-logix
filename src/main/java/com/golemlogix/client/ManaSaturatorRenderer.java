package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.client.render.PixelMesh;
import com.golemlogix.system.SaturatorStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The living part of the assembled Saturator: two glass columns fill with Source, in the chamber on the anvil a small
 * iron golem flattens the blank into plates in five strikes (without a blank it offers a poppy), copper carrier golems
 * shuttle blanks and finished plates between the side decks and the anvil ({@link SaturatorCarrierRenderer}), and on the
 * roof the fans of installed Cooling Co-Processors spin.
 * <p>
 * All sizes are in machine coordinates (X left to right as seen from the front, Y up, Z inwards, 1 cell = 16 px);
 * the owner sits at cell {@code (1, 2, 0)}, i.e. X 16..32, Y 32..48, Z 0..16.
 */
public class ManaSaturatorRenderer implements BlockEntityRenderer<ManaSaturatorBlockEntity> {
    private static final ResourceLocation LIQUID =
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_liquid.png");
    private static final ResourceLocation PRESS =
            ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_press.png");
    private static final ResourceLocation GOLEM =
            ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");

    /** Smith golem about 8.6 units tall (chamber is 9.5): feet on the anvil behind the blank, facing front. */
    private static final float GOLEM_SCALE = 0.2f;
    static final float ANVIL_TOP = 29.5f;
    private static final float GOLEM_Z = 11.2f;
    static final float ITEM_Z = 6.2f;

    private final ItemRenderer itemRenderer;
    private final ModelPart golem;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ManaSaturatorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
        this.head = golem.getChild("head");
        this.rightArm = golem.getChild("right_arm");
        this.leftArm = golem.getChild("left_arm");
        this.rightLeg = golem.getChild("right_leg");
        this.leftLeg = golem.getChild("left_leg");
    }

    /** Machine coordinates -> owner block fractions (facing north; X grows to the viewer's left, so it is mirrored). */
    static float lx(float x) {
        return (32f - x) / 16f;
    }

    static float ly(float y) {
        return (y - 32f) / 16f;
    }

    static float lz(float z) {
        return z / 16f;
    }

    /** The assembled machine spans 3x3x2 blocks: expand the bounding box so the single owner cell does not cull it. */
    @Override
    public AABB getRenderBoundingBox(ManaSaturatorBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        BlockState state = entity.getBlockState();
        if (!state.hasProperty(ManaSaturatorBlock.FORMED) || !state.getValue(ManaSaturatorBlock.FORMED)
                || !state.hasProperty(ManaSaturatorBlock.FACING)) {
            return new AABB(pos);
        }
        AABB box = new AABB(pos);
        for (BlockPos p : SaturatorStructure.cells(pos, state.getValue(ManaSaturatorBlock.FACING))) {
            box = box.minmax(new AABB(p));
        }
        return box.inflate(0.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(ManaSaturatorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = entity.getBlockState();
        if (!state.hasProperty(ManaSaturatorBlock.FORMED)) {
            return;
        }
        if (!state.getValue(ManaSaturatorBlock.FORMED)) {
            renderManualAnvilItem(entity, partialTick, pose, buffers, level);
            return;
        }
        if (!state.hasProperty(ManaSaturatorBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        float time = (level.getGameTime() % 72000L) + partialTick;
        // sample light above the multiblock: the owner block sits inside solid structure
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos().above());
        light = LightTexture.pack(Math.max(LightTexture.block(light), 10), Math.max(LightTexture.sky(light), 6));
        float progress = entity.isCrafting()
                ? Math.min(1f, entity.getProgressFraction() + partialTick * entity.speed() / (float) entity.currentWork())
                : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        renderColumns(entity, buffers, pose.last(), time);
        renderSmithGolem(entity, pose, buffers, light, time, progress);
        renderAnvilBlank(entity, pose, buffers, light, packedOverlay, time, progress);
        SaturatorCarrierRenderer.render(itemRenderer, entity, pose, buffers, light, packedOverlay, time, partialTick);
        renderFans(entity, level, facing, pose, buffers, light, time);
        // copper lamplighter golem on the top beam (machine Y 44 = 12 px in owner cell)
        LampGolem.render(pose, buffers, light, entity.walker, partialTick, entity.lampState(), time, 32f, 12f);
        pose.popPose();
    }

    /** Two glass Source columns with scrolling ripple UVs. */
    private void renderColumns(ManaSaturatorBlockEntity entity, MultiBufferSource buffers, PoseStack.Pose p, float time) {
        if (entity.getSource() <= 0) {
            return;
        }
        float fill = 0.1f + 0.9f * entity.getSourceFraction();
        float height = 10f * fill;
        float shift = (time * 0.006f) % 1f;
        float wob = entity.isCrafting() ? 0.04f * (float) Math.sin(time * 0.7f) : 0.01f * (float) Math.sin(time * 0.15f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LIQUID));
        float topY = ly(29f + height) + wob / 16f;
        Quads.box(vc, p, lx(12f), ly(29f), lz(4f), lx(4f), topY, lz(12f),
                shift, shift, shift + 0.8f, shift + 0.8f, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
        Quads.box(vc, p, lx(44f), ly(29f), lz(4f), lx(36f), topY, lz(12f),
                shift, shift, shift + 0.8f, shift + 0.8f, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
    }

    /** Strike phase {@code 0..1} within one of the five hammer strikes of a ritual. */
    private static float strikePhase(float progress) {
        return (progress * 5f) % 1f;
    }

    /**
     * Iron golem smith: five wind-up/strike/pause beats per ritual. When waiting for a blank it watches calmly; when completely
     * idle it holds out a poppy like a vanilla village iron golem.
     */
    private void renderSmithGolem(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, float time, float progress) {
        rightLeg.xRot = 0f;
        leftLeg.xRot = 0f;
        boolean hasItem = !entity.getHeldItem().isEmpty() || !entity.bringer.hands.isEmpty();
        boolean flower = false;
        if (entity.isCrafting()) {
            float ph = strikePhase(progress);
            float arm;
            if (ph < 0.65f) {
                float k = ph / 0.65f;
                arm = -1.15f - 0.6f * PixelMesh.smoothstep(k);
            } else if (ph < 0.75f) {
                float k = (ph - 0.65f) / 0.1f;
                arm = -1.75f + 0.6f * k * k;
            } else {
                arm = -1.15f;
            }
            rightArm.xRot = arm;
            leftArm.xRot = arm;
            head.xRot = 0.45f;
            head.yRot = 0f;
        } else if (hasItem) {
            rightArm.xRot = -0.35f;
            leftArm.xRot = -0.35f;
            head.xRot = 0.4f;
            head.yRot = Mth.sin(time * 0.04f) * 0.25f;
        } else {
            flower = true;
            rightArm.xRot = -0.8f + 0.025f * Mth.sin(time * 0.09f);
            leftArm.xRot = 0f;
            head.xRot = 0.1f;
            head.yRot = Mth.sin(time * 0.03f) * 0.35f;
        }
        rightArm.yRot = 0f;
        leftArm.yRot = 0f;

        pose.pushPose();
        pose.translate(lx(24f), ly(ANVIL_TOP), lz(GOLEM_Z));
        pose.scale(-GOLEM_SCALE, -GOLEM_SCALE, GOLEM_SCALE);
        pose.translate(0f, -1.501f, 0f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(GOLEM));
        golem.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
        if (flower) {
            pose.pushPose();
            rightArm.translateAndRotate(pose);
            // matches vanilla IronGolemFlowerLayer placement in the right hand
            pose.translate(-1.1875f, 1.0625f, -0.9375f);
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.scale(0.5f, 0.5f, 0.5f);
            pose.mulPose(Axis.XP.rotationDegrees(-90f));
            pose.translate(-0.5f, -0.5f, -0.5f);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.POPPY.defaultBlockState(), pose, buffers, light,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Blank lying on the anvil: flattens progressively across the ritual and squashes momentarily on each strike. */
    private void renderAnvilBlank(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                  float time, float progress) {
        ItemStack held = entity.getHeldItem();
        if (held.isEmpty()) {
            return;
        }
        boolean block = held.getItem() instanceof BlockItem;
        float squash = 1f;
        if (entity.isCrafting()) {
            float ph = strikePhase(progress);
            float hit = ph >= 0.73f && ph < 0.86f ? 1f - Math.abs(ph - 0.76f) / 0.1f : 0f;
            squash = (1f - 0.45f * progress) * (1f - 0.3f * Math.max(0f, hit));
        }
        pose.pushPose();
        if (block) {
            float size = 0.56f;
            pose.translate(lx(24f), ly(ANVIL_TOP) + size * 0.25f * squash, lz(ITEM_Z));
            pose.scale(1f, squash, 1f);
            pose.mulPose(Axis.YP.rotationDegrees(entity.isCrafting() ? 0f : 18f + Mth.sin(time * 0.05f) * 6f));
            pose.scale(size, size, size);
        } else {
            // flat plates/plasma lie horizontally on the striking face
            pose.translate(lx(24f), ly(ANVIL_TOP) + 0.02f, lz(ITEM_Z));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.mulPose(Axis.ZP.rotationDegrees(15f));
            pose.scale(0.3f, 0.3f, 0.3f);
        }
        itemRenderer.renderStatic(held, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }

    /** Spinning brass fan blades atop installed Cooling Co-Processors on the roof vents. */
    private void renderFans(ManaSaturatorBlockEntity entity, Level level, Direction facing, PoseStack pose, MultiBufferSource buffers,
                            int light, float time) {
        BlockPos controller = entity.getBlockPos();
        VertexConsumer vc = null;
        float speed = entity.isCrafting() ? 22f : 3f;
        for (int vent : SaturatorStructure.VENTS) {
            BlockState s = level.getBlockState(SaturatorStructure.cellPos(controller, facing, vent));
            if (!(s.getBlock() instanceof SaturatorPartBlock) || !s.getValue(SaturatorPartBlock.COOLER)) {
                continue;
            }
            if (vc == null) {
                vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PRESS));
            }
            float cx = 16f * SaturatorStructure.col(vent) + 8f;
            float cz = 16f * SaturatorStructure.dep(vent) + 8f;
            pose.pushPose();
            pose.translate(lx(cx), ly(47.15f), lz(cz));
            pose.mulPose(Axis.YP.rotationDegrees((time * speed + vent * 37f) % 360f));
            PoseStack.Pose p = pose.last();
            for (int b = 0; b < 4; b++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(b * 90f));
                pose.mulPose(Axis.XP.rotationDegrees(14f));
                Quads.box(vc, pose.last(), -0.03f, -0.006f, 0.04f, 0.03f, 0.006f, 0.19f, 0.1f, 0.1f, 0.4f, 0.9f, 0xFFFFFFFF, light);
                pose.popPose();
            }
            Quads.box(vc, p, -0.045f, -0.01f, -0.045f, 0.045f, 0.02f, 0.045f, 0.6f, 0.6f, 0.9f, 0.9f, 0xFFFFE6A0, light);
            pose.popPose();
        }
    }

    /** Standalone unformed anvil: hand-forged blank flattens with each hammer blow; finished plate rests flat and sways gently. */
    private static void renderManualAnvilItem(ManaSaturatorBlockEntity entity, float partialTick, PoseStack pose,
                                              MultiBufferSource buffers, Level level) {
        ItemStack item = entity.anvilItem();
        if (item.isEmpty()) {
            return;
        }
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos().above());
        float time = (level.getGameTime() % 72000L) + partialTick;
        pose.pushPose();
        if (ManaSaturatorBlockEntity.isManualInput(level, item)) {
            float squash = 1f - 0.55f * entity.manualHits() / (float) ManaSaturatorBlockEntity.MANUAL_HITS;
            float size = 0.36f;
            pose.translate(0.5, 15f / 16f + size * squash * 0.5f, 0.5);
            pose.scale(size * (1f + (1f - squash) * 0.35f), size * squash, size * (1f + (1f - squash) * 0.35f));
            Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, level, 0);
        } else {
            pose.translate(0.5, 15.2f / 16f, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(15f * Mth.sin(time * 0.05f)));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.45f, 0.45f, 0.45f);
            Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose,
                    buffers, level, 0);
        }
        pose.popPose();
    }
}
