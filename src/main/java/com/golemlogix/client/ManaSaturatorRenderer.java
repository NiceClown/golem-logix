package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
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
 * iron golem flattens the blank into plates in five strikes (without a blank it offers a poppy), on the roof the fans
 * of the installed Cooling Co-Processors spin. All sizes are in "machine" coordinates (X left to right as seen
 * from the front, Y up, Z inwards, a cell = 16), as in the model; the owner stands in cell (1, 2, 0), that is X 16..32,
 * Y 32..48, Z 0..16.
 */
public class ManaSaturatorRenderer implements BlockEntityRenderer<ManaSaturatorBlockEntity> {
    private static final ResourceLocation LIQUID = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_liquid.png");
    private static final ResourceLocation PRESS = ResourceLocation.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_press.png");
    private static final ResourceLocation GOLEM = ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    /** A golem about 8.6 units tall (the whole chamber is 9.5): feet on the anvil behind the blank, facing the front. */
    private static final float GOLEM_SCALE = 0.2f;
    private static final float ANVIL_TOP = 29.5f;
    private static final float GOLEM_Z = 11.2f;
    private static final float ITEM_Z = 6.2f;

    private final ItemRenderer itemRenderer;
    private ModelPart golem;

    public ManaSaturatorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
    }

    /** Machine coordinates -> fractions of the owner block (facing north; x is mirrored: it grows to the viewer's left). */
    private static float lx(float x) {
        return (32f - x) / 16f;
    }

    private static float ly(float y) {
        return (y - 32f) / 16f;
    }

    private static float lz(float z) {
        return z / 16f;
    }

    /** The assembled machine takes 3x3x2 blocks: rendering must not be culled by the single owner block. */
    @Override
    public AABB getRenderBoundingBox(ManaSaturatorBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        if (!entity.getBlockState().getValue(ManaSaturatorBlock.FORMED)) {
            return new AABB(pos);
        }
        AABB box = new AABB(pos);
        for (BlockPos p : SaturatorStructure.cells(pos, entity.getBlockState().getValue(ManaSaturatorBlock.FACING))) {
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
        if (!entity.getBlockState().getValue(ManaSaturatorBlock.FORMED)) {
            renderAnvilItem(entity, partialTick, pose, buffers, level);
            return;
        }
        Direction facing = entity.getBlockState().getValue(ManaSaturatorBlock.FACING);
        float time = level.getGameTime() + partialTick;
        // the light is taken above the machine: the owner itself stands inside and does not light its own air
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos().above());
        light = LightTexture.pack(Math.max(LightTexture.block(light), 10), Math.max(LightTexture.sky(light), 6));
        // ritual progress between ticks, smoothly (the server only sends the start and the end)
        float progress = entity.isCrafting()
                ? Math.min(1f, entity.getProgressFraction() + partialTick * entity.speed() / (float) ManaSaturatorBlockEntity.MAX_PROGRESS)
                : 0f;

        pose.pushPose();
        Quads.faceModel(pose, facing);
        renderColumns(entity, buffers, pose.last(), time);
        renderGolem(entity, pose, buffers, light, time, progress);
        renderItem(entity, pose, buffers, light, packedOverlay, time, progress);
        renderCarriers(entity, pose, buffers, light, packedOverlay, time, partialTick);
        renderFans(entity, level, facing, pose, buffers, light, time);
        // golem with a lamp on the roof: the top of the beam is machine Y 44, that is 12 pixels of the owner cell
        LampGolem.render(pose, buffers, light, entity.walker, partialTick, entity.lampState(), time, 32f, 12f);
        pose.popPose();
    }

    /** Two columns: Source level, ripples running over the fluid texture. */
    private void renderColumns(ManaSaturatorBlockEntity entity, MultiBufferSource buffers, PoseStack.Pose p, float time) {
        if (entity.getSource() <= 0) {
            return;
        }
        float fill = 0.1f + 0.9f * entity.getSourceFraction();
        float height = 10f * fill;
        float shift = (time * 0.006f) % 1f;
        float wob = entity.isCrafting() ? 0.04f * (float) Math.sin(time * 0.7f) : 0.01f * (float) Math.sin(time * 0.15f);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(LIQUID));
        for (float x0 : new float[]{4f, 36f}) {
            Quads.box(vc, p, lx(x0 + 8f), ly(29f), lz(4f), lx(x0), ly(29f + height) + wob / 16f, lz(12f),
                    shift, shift, shift + 0.8f, shift + 0.8f, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
        }
    }

    /** Strike phase 0..1 within one of the five strikes of a ritual. */
    private static float strikePhase(float progress) {
        return (progress * 5f) % 1f;
    }

    /**
     * Iron golem: a swing (arms go up), a sharp strike on the blank and a short pause, five times per ritual. When idle it stands
     * calmly and sways its head a little; without a blank it offers a poppy, like a real iron golem.
     */
    private void renderGolem(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, float time, float progress) {
        ModelPart head = golem.getChild("head");
        ModelPart rightArm = golem.getChild("right_arm");
        ModelPart leftArm = golem.getChild("left_arm");
        ModelPart rightLeg = golem.getChild("right_leg");
        ModelPart leftLeg = golem.getChild("left_leg");
        rightLeg.xRot = 0f;
        leftLeg.xRot = 0f;
        // something is on the anvil or a blank is already being carried: the smith waits; empty with nothing to wait for: it offers a poppy
        boolean hasItem = !entity.getHeldItem().isEmpty() || !entity.bringer.hands.isEmpty();
        boolean flower = false;
        if (entity.isCrafting()) {
            float ph = strikePhase(progress);
            float arm;
            if (ph < 0.65f) {
                float k = ph / 0.65f;
                arm = -1.15f - 0.6f * (k * k * (3 - 2 * k));
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
            // nothing to forge: the golem offers a poppy (like iron golems to village children)
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
            // like the flower layer of the vanilla iron golem (IronGolemFlowerLayer): a poppy in the right hand
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

    /** The blank lies on the anvil in front of the golem; it gets flatter with every strike and is almost a plate by the end of the ritual. */
    private void renderItem(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
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
            // flat items (plates, plasma) lie on the anvil
            pose.translate(lx(24f), ly(ANVIL_TOP) + 0.02f, lz(ITEM_Z));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.mulPose(Axis.ZP.rotationDegrees(15f));
            pose.scale(0.3f, 0.3f, 0.3f);
        }
        itemRenderer.renderStatic(held, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }

    // ============================================================================ copper carrier golems and decks
    /** A carrier golem about 4.5 units tall: it passes between the glass column and the press chamber. */
    private static final float CARRIER_SCALE = 0.28f;
    /** The bringer's path from the left back deck to the anvil (machine coordinates x, y, z): past the column, a step onto the plate. */
    private static final float[][] PATH_IN = {{11.4f, 28f, 21.2f}, {14.8f, 28f, 16.0f}, {14.8f, 28f, 9.4f}, {16.5f, 28.9f, 8.3f},
            {19.3f, 29.5f, 7.8f}};
    /** The taker's path: mirrored, from the right deck. */
    private static final float[][] PATH_OUT = mirror(PATH_IN);
    /** Where the blank (left deck) and the finished item (right deck) lie, on the rune circles of the decks. */
    private static final float[] SPOT_IN = {7.6f, 28f, 23.4f}, SPOT_OUT = {40.4f, 28f, 23.4f};
    private static final float[] SPOT_ANVIL = {24f, ANVIL_TOP, ITEM_Z};
    /** How many ticks at the start of a step the golem bows (takes or puts down). */
    private static final float BOW_TICKS = 5f;

    private static float[][] mirror(float[][] path) {
        float[][] out = new float[path.length][];
        for (int i = 0; i < path.length; i++) {
            out[i] = new float[]{48f - path[i][0], path[i][1], path[i][2]};
        }
        return out;
    }

    /** A point on the polyline path at path fraction f (0 = start, 1 = end): x, y, z and direction (dx, dz). */
    private static float[] along(float[][] path, float f) {
        float total = 0f;
        float[] seg = new float[path.length - 1];
        for (int i = 0; i < seg.length; i++) {
            seg[i] = (float) Math.hypot(path[i + 1][0] - path[i][0], path[i + 1][2] - path[i][2]);
            total += seg[i];
        }
        float d = Mth.clamp(f, 0f, 1f) * total;
        for (int i = 0; i < seg.length; i++) {
            if (d <= seg[i] || i == seg.length - 1) {
                float k = seg[i] <= 0f ? 0f : Mth.clamp(d / seg[i], 0f, 1f);
                float[] a = path[i], b = path[i + 1];
                return new float[]{Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]), b[0] - a[0], b[2] - a[2]};
            }
            d -= seg[i];
        }
        float[] last = path[path.length - 1];
        return new float[]{last[0], last[1], last[2], 0f, 1f};
    }

    private static float pathLength(float[][] path) {
        float total = 0f;
        for (int i = 0; i + 1 < path.length; i++) {
            total += (float) Math.hypot(path[i + 1][0] - path[i][0], path[i + 1][2] - path[i][2]);
        }
        return total;
    }

    /**
     * Copper carrier golem by its phase (see ManaSaturatorBlockEntity.Carrier): at home it stands at its deck and glances
     * now at it, now at the smith; to the anvil and back it walks along the path (bowing at the start of a step to take or put down); at
     * the anvil it waits behind the smith. deck = its deck, toAnvilForward = the path from the deck to the anvil.
     */
    private void carrier(ManaSaturatorBlockEntity entity, ManaSaturatorBlockEntity.Carrier c, float[][] path, float[] deck, PoseStack pose,
                         MultiBufferSource buffers, int light, int overlay, float time, float pt, int variant, float lookPhase) {
        float len = pathLength(path), walkT = ManaSaturatorBlockEntity.WALK;
        float left = Math.max(0f, c.timer - pt);
        float[] pos, face;
        boolean walking = false;
        float bow = 0f, dist = 0f;
        switch (c.phase) {
            case ManaSaturatorBlockEntity.TO_ANVIL -> {
                float f = 1f - left / walkT;
                float[] at = along(path, f);
                pos = at;
                face = new float[]{at[3], at[4]};
                walking = left > 0f;
                dist = f * len;
                float since = walkT - left;
                if (since < BOW_TICKS) {
                    // has just picked it up from the deck
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
            }
            case ManaSaturatorBlockEntity.AT_ANVIL -> {
                pos = path[path.length - 1];
                face = toward(pos, SPOT_ANVIL);
                bow = 0.12f + 0.08f * Mth.sin(time * 0.35f + variant);
            }
            case ManaSaturatorBlockEntity.TO_DECK -> {
                float f = left / walkT;
                float[] at = along(path, f);
                pos = at;
                face = new float[]{-at[3], -at[4]};
                walking = left > 0f;
                dist = (1f - f) * len;
                float since = walkT - left;
                if (since < BOW_TICKS) {
                    // has just put it on the anvil or taken it off
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
                if (!walking) {
                    face = toward(pos, deck);
                }
            }
            default -> {
                pos = path[0];
                float look = Mth.sin(time * 0.021f + lookPhase) * 0.5f + 0.5f;
                float[] a = toward(pos, deck), b = toward(pos, SPOT_ANVIL);
                face = new float[]{Mth.lerp(look, a[0], b[0]), Mth.lerp(look, a[1], b[1])};
            }
        }
        ItemStack item = c.hands;
        boolean hold = !item.isEmpty();
        org.joml.Vector3f p = new org.joml.Vector3f(lx(pos[0]), ly(pos[1]), lz(pos[2]));
        float fx = face[0], fz = face[1];
        if (Math.abs(fx) + Math.abs(fz) < 1e-4f) {
            fz = 1f;
        }
        // the model's x is mirrored: the direction along machine X goes into the pose with a minus sign
        org.joml.Vector3f forward = new org.joml.Vector3f(-fx, 0f, fz);
        CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(walking ? dist * 1.3f : time * 0.1f, walking, hold, Mth.clamp(bow, 0f, 1f));
        CopperGolemPuppet.render(pose, buffers, light, p, forward, new org.joml.Vector3f(0f, 1f, 0f), CARRIER_SCALE, anim, variant,
                hold ? hp -> {
                    boolean block = item.getItem() instanceof BlockItem;
                    float sc = block ? 0.75f : 0.55f;
                    hp.scale(sc, sc, sc);
                    itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, hp, buffers, entity.getLevel(), 0);
                } : null);
    }

    private void renderCarriers(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                float time, float pt) {
        carrier(entity, entity.bringer, PATH_IN, SPOT_IN, pose, buffers, light, overlay, time, pt, 1, 0f);
        carrier(entity, entity.taker, PATH_OUT, SPOT_OUT, pose, buffers, light, overlay, time, pt, 2, 2f);
        // the queue of blanks on the left deck (up to three in a stack) and the finished plates on the right one slowly turn
        ItemStack in = entity.inputDeck();
        for (int i = 0; i < Math.min(3, in.getCount()); i++) {
            deckItem(pose, buffers, light, overlay, entity, in, new float[]{SPOT_IN[0] + (i % 2) * 1.6f - 0.8f * (i / 2), SPOT_IN[1] + i * 0.02f,
                    SPOT_IN[2] - i * 1.9f}, i * 17f);
        }
        ItemStack out = entity.outputDeck();
        if (!out.isEmpty()) {
            deckItem(pose, buffers, light, overlay, entity, out, SPOT_OUT, time * 1.5f);
            if (out.getCount() > 2) {
                deckItem(pose, buffers, light, overlay, entity, out, new float[]{SPOT_OUT[0], SPOT_OUT[1] + 0.6f, SPOT_OUT[2]}, time * 1.5f + 40f);
            }
        }
    }

    /** Direction (dx, dz) from point from to point to (machine coordinates). */
    private static float[] toward(float[] from, float[] to) {
        return new float[]{to[0] - from[0], to[2] - from[2]};
    }

    private void deckItem(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ManaSaturatorBlockEntity entity, ItemStack item,
                          float[] spot, float spin) {
        boolean block = item.getItem() instanceof BlockItem;
        pose.pushPose();
        if (block) {
            float size = 0.42f;
            pose.translate(lx(spot[0]), ly(spot[1]) + size * 0.25f, lz(spot[2]));
            pose.mulPose(Axis.YP.rotationDegrees(25f + spin));
            pose.scale(size, size, size);
        } else {
            pose.translate(lx(spot[0]), ly(spot[1]) + 0.02f, lz(spot[2]));
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.3f, 0.3f, 0.3f);
        }
        itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }

    /** Fan blades on the installed Cooling Co-Processors: they spin fast while the machine works and lazily without it. */
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
            float cx = 16f * SaturatorStructure.col(vent) + 8f, cz = 16f * SaturatorStructure.dep(vent) + 8f;
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

    /**
     * The anvil before assembly: the blank lies on the striking face and gets flatter with every strike of forging by hand; finished
     * plates lie flat and sway a little.
     */
    private static void renderAnvilItem(ManaSaturatorBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, Level level) {
        ItemStack item = entity.anvilItem();
        if (item.isEmpty()) {
            return;
        }
        int light = LevelRenderer.getLightColor(level, entity.getBlockPos().above());
        float time = level.getGameTime() + partialTick;
        pose.pushPose();
        if (ManaSaturatorBlockEntity.isManualInput(item)) {
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