package com.golemlogix.client;

import com.golemlogix.blockentity.ManaSaturatorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import static com.golemlogix.client.ManaSaturatorRenderer.ANVIL_TOP;
import static com.golemlogix.client.ManaSaturatorRenderer.ITEM_Z;
import static com.golemlogix.client.ManaSaturatorRenderer.lx;
import static com.golemlogix.client.ManaSaturatorRenderer.ly;
import static com.golemlogix.client.ManaSaturatorRenderer.lz;

/**
 * Copper carrier golems and input/output deck item stacks for {@link ManaSaturatorRenderer}:
 * the bringer ferries raw blanks from the left rune deck to the anvil and the taker retrieves finished plates to the right deck,
 * following mirrored polylines clear of the Source columns and stepping onto the anvil ledge.
 */
final class SaturatorCarrierRenderer {
    /** Carrier golem (~4.5 px tall) sized to pass between the glass column and the press chamber. */
    private static final float CARRIER_SCALE = 0.28f;
    /** Ticks at the start of a pickup/dropoff leg during which the carrier bows. */
    private static final float BOW_TICKS = 5f;

    /** Bringer's polyline from left deck to anvil (machine px): past the column, then a step onto the plate. */
    private static final float[][] PATH_IN = {
            {11.4f, 28f, 21.2f}, {14.8f, 28f, 16.0f}, {14.8f, 28f, 9.4f}, {16.5f, 28.9f, 8.3f}, {19.3f, 29.5f, 7.8f}
    };
    /** Taker's polyline: mirror of {@link #PATH_IN} from the right deck. */
    private static final float[][] PATH_OUT = mirror(PATH_IN);
    /** Precomputed horizontal segment lengths shared by {@link #PATH_IN} and {@link #PATH_OUT}. */
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

    /** Rune-circle deck origins for blanks (left) and finished plates (right), plus the anvil target. */
    private static final float[] SPOT_IN = {7.6f, 28f, 23.4f};
    private static final float[] SPOT_OUT = {40.4f, 28f, 23.4f};
    private static final float[] SPOT_ANVIL = {24f, ANVIL_TOP, ITEM_Z};

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f CARRIER_POS = new Vector3f();
    private static final Vector3f CARRIER_FWD = new Vector3f();
    private static final float[] ALONG_OUT = new float[5];

    private SaturatorCarrierRenderer() {
    }

    /** Renders both carrier golems and the queued blank / output item stacks resting on the side decks. */
    static void render(ItemRenderer itemRenderer, ManaSaturatorBlockEntity entity, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay, float time, float partialTick) {
        renderCarrier(itemRenderer, entity, entity.bringer, PATH_IN, SPOT_IN, pose, buffers, light, overlay, time, partialTick, 1, 0f);
        renderCarrier(itemRenderer, entity, entity.taker, PATH_OUT, SPOT_OUT, pose, buffers, light, overlay, time, partialTick, 2, 2f);

        // queued blanks on the left deck (up to three stacked)
        ItemStack in = entity.inputDeck();
        for (int i = 0; i < Math.min(3, in.getCount()); i++) {
            renderDeckItem(itemRenderer, pose, buffers, light, overlay, entity, in,
                    SPOT_IN[0] + (i % 2) * 1.6f - 0.8f * (i / 2), SPOT_IN[1] + i * 0.02f, SPOT_IN[2] - i * 1.9f, i * 17f);
        }
        // finished plates rotating slowly on the right deck
        ItemStack out = entity.outputDeck();
        if (!out.isEmpty()) {
            renderDeckItem(itemRenderer, pose, buffers, light, overlay, entity, out, SPOT_OUT[0], SPOT_OUT[1], SPOT_OUT[2], time * 1.5f);
            if (out.getCount() > 2) {
                renderDeckItem(itemRenderer, pose, buffers, light, overlay, entity, out, SPOT_OUT[0], SPOT_OUT[1] + 0.6f, SPOT_OUT[2], time * 1.5f + 40f);
            }
        }
    }

    /** Renders one carrier golem according to its phase state machine ({@link ManaSaturatorBlockEntity.Carrier}). */
    private static void renderCarrier(ItemRenderer itemRenderer, ManaSaturatorBlockEntity entity, ManaSaturatorBlockEntity.Carrier c,
                                      float[][] path, float[] deck, PoseStack pose, MultiBufferSource buffers,
                                      int light, int overlay, float time, float pt, int variant, float lookPhase) {
        float len = PATH_LEN, walkT = ManaSaturatorBlockEntity.WALK;
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
                    bow = Mth.sin((1f - since / BOW_TICKS) * Mth.HALF_PI) * 0.8f;
                }
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
        // machine X is mirrored relative to the model frame
        CARRIER_FWD.set(-fx, 0f, fz);
        CopperGolemPuppet.Anim anim = new CopperGolemPuppet.Anim(walking ? dist * 1.3f : time * 0.1f, walking, hold, Mth.clamp(bow, 0f, 1f));
        CopperGolemPuppet.render(pose, buffers, light, CARRIER_POS, CARRIER_FWD, UP, CARRIER_SCALE, anim, variant,
                hold ? hp -> {
                    float sc = item.getItem() instanceof BlockItem ? 0.75f : 0.55f;
                    hp.scale(sc, sc, sc);
                    itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, hp, buffers, entity.getLevel(), 0);
                } : null);
    }

    private static void renderDeckItem(ItemRenderer itemRenderer, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                       ManaSaturatorBlockEntity entity, ItemStack item, float sx, float sy, float sz, float spin) {
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
        itemRenderer.renderStatic(item, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), 0);
        pose.popPose();
    }

    private static float[][] mirror(float[][] path) {
        float[][] out = new float[path.length][];
        for (int i = 0; i < path.length; i++) {
            out[i] = new float[]{48f - path[i][0], path[i][1], path[i][2]};
        }
        return out;
    }

    /** Fills {@code out} with {@code (x, y, z, dx, dz)} along {@code path} at arc-length fraction {@code f} ({@code 0..1}). */
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
}
