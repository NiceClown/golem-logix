package com.golemlogix.client;

import com.golemlogix.GolemLogixMod;
import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.golemlogix.blocks.ManaSaturatorBlock;
import com.golemlogix.blocks.SaturatorPartBlock;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.LegacyBer;
import com.golemlogix.system.SaturatorStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The living part of the assembled Saturator: two glass columns fill with Source, in the chamber on the anvil a small
 * iron golem flattens the blank into plates in five strikes (without a blank it offers a poppy), on the roof the fans
 * of the installed Cooling Co-Processors spin. All sizes are in "machine" coordinates (X left to right as seen
 * from the front, Y up, Z inwards, a cell = 16), as in the model; the owner stands in cell (1, 2, 0), that is X 16..32,
 * Y 32..48, Z 0..16.
 */
public class ManaSaturatorRenderer extends LegacyBer<ManaSaturatorBlockEntity> {
    private static final Identifier LIQUID = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_liquid.png");
    private static final Identifier PRESS = Identifier.fromNamespaceAndPath(GolemLogixMod.MOD_ID, "textures/entity/saturator_press.png");
    private static final Identifier GOLEM = Identifier.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png");
    /** A golem about 8.6 units tall (the whole chamber is 9.5): feet on the anvil behind the blank, facing the front. */
    private static final float GOLEM_SCALE = 0.2f;
    private static final float ANVIL_TOP = 29.5f;
    private static final float GOLEM_Z = 11.2f;
    private static final float ITEM_Z = 6.2f;

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f CARRIER_POS = new Vector3f();
    private static final Vector3f CARRIER_FWD = new Vector3f();
    private static final float[] ALONG_OUT = new float[5];

    private final ModelPart golem;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ManaSaturatorRenderer(BlockEntityRendererProvider.Context context) {
        this.golem = context.bakeLayer(ModelLayers.IRON_GOLEM);
        this.head = golem.getChild("head");
        this.rightArm = golem.getChild("right_arm");
        this.leftArm = golem.getChild("left_arm");
        this.rightLeg = golem.getChild("right_leg");
        this.leftLeg = golem.getChild("left_leg");
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
            renderAnvilItem(entity, partialTick, pose, buffers, level);
            return;
        }
        if (!state.hasProperty(ManaSaturatorBlock.FACING)) {
            return;
        }
        Direction facing = state.getValue(ManaSaturatorBlock.FACING);
        float time = (level.getGameTime() % 72000L) + partialTick;
        // the light is taken above the machine: the owner itself stands inside and does not light its own air
        int light = LevelRenderer.getLightCoords(level, entity.getBlockPos().above());
        light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), 10), Math.max(LightCoordsUtil.sky(light), 6));
        // ritual progress between ticks, smoothly (the server only sends the start and the end)
        float progress = entity.isCrafting()
                ? Math.min(1f, entity.getProgressFraction() + partialTick * entity.speed() / (float) entity.currentWork())
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
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(LIQUID));
        float topY = ly(29f + height) + wob / 16f;
        Quads.box(vc, p, lx(12f), ly(29f), lz(4f), lx(4f), topY, lz(12f),
                shift, shift, shift + 0.8f, shift + 0.8f, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
        Quads.box(vc, p, lx(44f), ly(29f), lz(4f), lx(36f), topY, lz(12f),
                shift, shift, shift + 0.8f, shift + 0.8f, 0xFFFFFFFF, LightCoordsUtil.FULL_BRIGHT);
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
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(GOLEM));
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
            com.golemlogix.client.render.BerBuffers.block(buffers, Blocks.POPPY.defaultBlockState(), pose, light);
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
        com.golemlogix.client.render.BerBuffers.item(buffers, held, ItemDisplayContext.FIXED, light, pose, entity.getLevel(), 0);
        pose.popPose();
    }

    // ============================================================================ copper carrier golems and decks
    /** A carrier golem about 4.5 units tall: it passes between the glass column and the press chamber. */
    private static final float CARRIER_SCALE = 0.28f;
    /**
     * The bringer's path from the left back deck to the anvil (machine coordinates x, y, z): along the deck clear of the column's
     * bands, to the deck's edge, then a hop over the gap up onto the anvil plate (it no longer walks through the plate's edge).
     */
    private static final float[][] PATH_IN = {{11.4f, 28f, 21.2f}, {14.7f, 28f, 16.0f}, {14.7f, 28f, 9.4f}, {15.3f, 28f, 8.4f},
            {16.6f, 30.2f, 8.0f}, {18.0f, 29.5f, 7.9f}, {19.3f, 29.5f, 7.8f}};
    /** The taker's path: mirrored, from the right deck. */
    private static final float[][] PATH_OUT = mirror(PATH_IN);
    /** Precomputed horizontal segment lengths (shared by PATH_IN and its mirror PATH_OUT). */
    private static final float[] PATH_SEG = new float[PATH_IN.length - 1];
    private static final float PATH_LEN;
    static {
        float total = 0f;
        for (int i = 0; i < PATH_SEG.length; i++) {
            PATH_SEG[i] = (float) Math.hypot(PATH_IN[i + 1][0] - PATH_IN[i][0], PATH_IN[i + 1][2] - PATH_IN[i][2]);
            total += PATH_SEG[i];
        }
        PATH_LEN = total;
    }
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

    /** Fills {@code out} with (x, y, z, dx, dz) along the polyline path at fraction f (0 = start, 1 = end). */
    private static void along(float[][] path, float f, float[] out) {
        float d = Mth.clamp(f, 0f, 1f) * PATH_LEN;
        for (int i = 0; i < PATH_SEG.length; i++) {
            float segLen = PATH_SEG[i];
            if (d <= segLen || i == PATH_SEG.length - 1) {
                float k = segLen <= 0f ? 0f : Mth.clamp(d / segLen, 0f, 1f);
                float[] a = path[i], b = path[i + 1];
                out[0] = Mth.lerp(k, a[0], b[0]);
                out[1] = Mth.lerp(k, a[1], b[1]);
                out[2] = Mth.lerp(k, a[2], b[2]);
                out[3] = b[0] - a[0];
                out[4] = b[2] - a[2];
                return;
            }
            d -= segLen;
        }
        float[] last = path[path.length - 1];
        out[0] = last[0];
        out[1] = last[1];
        out[2] = last[2];
        out[3] = 0f;
        out[4] = 1f;
    }

    /**
     * Copper carrier golem by its phase (see ManaSaturatorBlockEntity.Carrier): at home it stands at its deck and glances
     * now at it, now at the smith; to the anvil and back it walks along the path (bowing at the start of a step to take or put down); at
     * the anvil it waits behind the smith. deck = its deck, toAnvilForward = the path from the deck to the anvil.
     */
    private void carrier(ManaSaturatorBlockEntity entity, ManaSaturatorBlockEntity.Carrier c, float[][] path, float[] deck, PoseStack pose,
                         MultiBufferSource buffers, int light, int overlay, float time, float pt, int variant, float lookPhase) {
        float len = PATH_LEN, walkT = ManaSaturatorBlockEntity.WALK, w = ManaSaturatorBlockEntity.WAIT_AT;
        float left = Math.max(0f, c.timer - pt);
        float px, py, pz, fx, fz;
        boolean walking = false;
        float bow = 0f, dist = 0f;
        switch (c.phase) {
            case ManaSaturatorBlockEntity.TO_ANVIL -> {
                float f = 1f - left / walkT;
                along(path, f, ALONG_OUT);
                px = ALONG_OUT[0]; py = ALONG_OUT[1]; pz = ALONG_OUT[2];
                fx = ALONG_OUT[3]; fz = ALONG_OUT[4];
                walking = left > 0f;
                dist = f * len;
                float since = walkT - left;
                if (since < BOW_TICKS) {
                    // has just picked it up from the deck
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
            }
            case ManaSaturatorBlockEntity.TO_WAIT, ManaSaturatorBlockEntity.HOP -> {
                // the waiting zone is the deck's front corner (w of the path); the hop takes it from there onto the anvil
                boolean hop = c.phase == ManaSaturatorBlockEntity.HOP;
                float span = hop ? ManaSaturatorBlockEntity.HOP_TICKS : ManaSaturatorBlockEntity.TO_WAIT_TICKS;
                float k = 1f - left / span, f = hop ? w + (1f - w) * k : w * k;
                along(path, f, ALONG_OUT);
                px = ALONG_OUT[0]; py = ALONG_OUT[1]; pz = ALONG_OUT[2];
                fx = ALONG_OUT[3]; fz = ALONG_OUT[4];
                walking = left > 0f;
                dist = f * len;
                float since = span - left;
                if (!hop && since < BOW_TICKS) {
                    // has just picked it up from the deck
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
            }
            case ManaSaturatorBlockEntity.AT_WAIT -> {
                // waits on its corner, now watching the smith, now glancing back along the deck
                along(path, w, ALONG_OUT);
                px = ALONG_OUT[0]; py = ALONG_OUT[1]; pz = ALONG_OUT[2];
                float look = Mth.sin(time * 0.03f + lookPhase) * 0.5f + 0.5f;
                fx = Mth.lerp(look * 0.4f, SPOT_ANVIL[0] - px, deck[0] - px);
                fz = Mth.lerp(look * 0.4f, SPOT_ANVIL[2] - pz, deck[2] - pz);
            }
            case ManaSaturatorBlockEntity.BACK -> {
                float f = w * left / ManaSaturatorBlockEntity.TO_WAIT_TICKS;
                along(path, f, ALONG_OUT);
                px = ALONG_OUT[0]; py = ALONG_OUT[1]; pz = ALONG_OUT[2];
                if (left > 0f) {
                    fx = -ALONG_OUT[3]; fz = -ALONG_OUT[4];
                } else {
                    fx = deck[0] - px; fz = deck[2] - pz;
                }
                walking = left > 0f;
                dist = (w - f) * len;
            }
            case ManaSaturatorBlockEntity.AT_ANVIL -> {
                float[] last = path[path.length - 1];
                px = last[0]; py = last[1]; pz = last[2];
                fx = SPOT_ANVIL[0] - px; fz = SPOT_ANVIL[2] - pz;
                bow = 0.12f + 0.08f * Mth.sin(time * 0.35f + variant);
            }
            case ManaSaturatorBlockEntity.TO_DECK -> {
                float f = left / walkT;
                along(path, f, ALONG_OUT);
                px = ALONG_OUT[0]; py = ALONG_OUT[1]; pz = ALONG_OUT[2];
                walking = left > 0f;
                if (walking) {
                    fx = -ALONG_OUT[3]; fz = -ALONG_OUT[4];
                } else {
                    fx = deck[0] - px; fz = deck[2] - pz;
                }
                dist = (1f - f) * len;
                float since = walkT - left;
                if (since < BOW_TICKS) {
                    // has just put it on the anvil or taken it off
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
            }
            default -> {
                float[] first = path[0];
                px = first[0]; py = first[1]; pz = first[2];
                float look = Mth.sin(time * 0.021f + lookPhase) * 0.5f + 0.5f;
                fx = Mth.lerp(look, deck[0] - px, SPOT_ANVIL[0] - px);
                fz = Mth.lerp(look, deck[2] - pz, SPOT_ANVIL[2] - pz);
            }
        }
        ItemStack item = c.hands;
        boolean hold = !item.isEmpty();
        CARRIER_POS.set(lx(px), ly(py), lz(pz));
        if (Math.abs(fx) + Math.abs(fz) < 1e-4f) {
            fz = 1f;
        }
        // the model's x is mirrored: the direction along machine X goes into the pose with a minus sign
        CARRIER_FWD.set(-fx, 0f, fz);
        CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(walking ? dist * 1.3f : time * 0.1f, walking, hold, Mth.clamp(bow, 0f, 1f));
        CopperGolemPuppet.render(pose, buffers, light, CARRIER_POS, CARRIER_FWD, UP, CARRIER_SCALE, anim, variant,
                hold ? hp -> {
                    boolean block = item.getItem() instanceof BlockItem;
                    float sc = block ? 0.75f : 0.55f;
                    hp.scale(sc, sc, sc);
                    BerBuffers.item(buffers, item, ItemDisplayContext.FIXED, light, hp, entity.getLevel(), 0);
                } : null);
    }

    private void renderCarriers(ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                float time, float pt) {
        carrier(entity, entity.bringer, PATH_IN, SPOT_IN, pose, buffers, light, overlay, time, pt, 1, 0f);
        carrier(entity, entity.taker, PATH_OUT, SPOT_OUT, pose, buffers, light, overlay, time, pt, 2, 2f);
        // the queue of blanks on the left deck (up to three in a stack) and the finished plates on the right one slowly turn
        ItemStack in = entity.inputDeck();
        for (int i = 0; i < Math.min(3, in.getCount()); i++) {
            deckItem(pose, buffers, light, entity, in, SPOT_IN[0] + (i % 2) * 1.6f - 0.8f * (i / 2), SPOT_IN[1] + i * 0.02f,
                    SPOT_IN[2] - i * 1.9f, i * 17f);
        }
        ItemStack out = entity.outputDeck();
        if (!out.isEmpty()) {
            deckItem(pose, buffers, light, entity, out, SPOT_OUT[0], SPOT_OUT[1], SPOT_OUT[2], time * 1.5f);
            if (out.getCount() > 2) {
                deckItem(pose, buffers, light, entity, out, SPOT_OUT[0], SPOT_OUT[1] + 0.6f, SPOT_OUT[2], time * 1.5f + 40f);
            }
        }
    }

    private void deckItem(PoseStack pose, MultiBufferSource buffers, int light, ManaSaturatorBlockEntity entity, ItemStack item,
                          float sx, float sy, float sz, float spin) {
        boolean block = item.getItem() instanceof BlockItem;
        pose.pushPose();
        if (block) {
            float size = 0.42f;
            pose.translate(lx(sx), ly(sy) + size * 0.25f, lz(sz));
            pose.mulPose(Axis.YP.rotationDegrees(25f + spin));
            pose.scale(size, size, size);
        } else {
            pose.translate(lx(sx), ly(sy) + 0.02f, lz(sz));
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.3f, 0.3f, 0.3f);
        }
        BerBuffers.item(buffers, item, ItemDisplayContext.FIXED, light, pose, entity.getLevel(), 0);
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
                vc = buffers.getBuffer(RenderTypes.entityCutout(PRESS));
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
        int light = LevelRenderer.getLightCoords(level, entity.getBlockPos().above());
        float time = (level.getGameTime() % 72000L) + partialTick;
        pose.pushPose();
        if (ManaSaturatorBlockEntity.isManualInput(level, item)) {
            float squash = 1f - 0.55f * entity.manualHits() / (float) ManaSaturatorBlockEntity.MANUAL_HITS;
            float size = 0.36f;
            pose.translate(0.5, 15f / 16f + size * squash * 0.5f, 0.5);
            pose.scale(size * (1f + (1f - squash) * 0.35f), size * squash, size * (1f + (1f - squash) * 0.35f));
            BerBuffers.item(buffers, item, ItemDisplayContext.FIXED, light, pose, level, 0);
        } else {
            pose.translate(0.5, 15.2f / 16f, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(15f * Mth.sin(time * 0.05f)));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.scale(0.45f, 0.45f, 0.45f);
            BerBuffers.item(buffers, item, ItemDisplayContext.FIXED, light, pose, level, 0);
        }
        pose.popPose();
    }
}