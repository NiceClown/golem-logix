package com.golemlogix.client;

import com.google.common.base.Suppliers;
import com.golemlogix.blockentity.CryoMainframeBlockEntity;
import com.golemlogix.client.render.BerBuffers;
import com.golemlogix.client.render.StandaloneModels;
import com.golemlogix.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.golemlogix.client.CryoMainframeRenderer.SHELF_0_FLOOR;
import static com.golemlogix.client.CryoMainframeRenderer.SHELF_1_FLOOR;
import static com.golemlogix.client.render.PixelMesh.PIXEL;
import static com.golemlogix.client.render.PixelMesh.limb;
import static com.golemlogix.client.render.PixelMesh.mbox;
import static com.golemlogix.client.render.PixelMesh.modBlock;
import static com.golemlogix.client.render.PixelMesh.smoothstep;

/**
 * Animated characters of the assembled Cryo-Mainframe ({@link CryoMainframeRenderer}): the two embedded side atlants
 * (breathing torsos, gripping arms, player-tracking heads) and the three copper golems (roof valve operator plus the
 * two shelf technicians with smooth idle/work pose transitions).
 */
final class MainframeCrew {
    private static final Identifier ATLANT_BODY = modBlock("atlant_body");
    private static final Identifier ATLANT_LIMB = modBlock("atlant_limb");
    private static final Identifier ATLANT_BELT = modBlock("atlant_belt");

    /**
     * Calculators stand at the back of each shelf from z = 22.5 px. A golem facing them stops at {@code CALC_FRONT} so
     * its outstretched arms and bow never clip into a calculator; empty shelves allow stepping back to the matrix wall.
     */
    private static final float CALC_FRONT = 19.6f;
    private static final float ROOF_FLOOR = 38.4f * PIXEL;
    private static final float SHELF_GOLEM_SCALE = 0.24f;
    private static final float ROOF_GOLEM_SCALE = 0.3f;
    private static final int WHITE = 0xFFFFFFFF;

    private static final Vector3f UP = new Vector3f(0f, 1f, 0f);
    private static final Vector3f SCRATCH_AT = new Vector3f();
    private static final Vector3f SCRATCH_FWD = new Vector3f();

    private static final Supplier<ItemStack> PAPER_STACK = Suppliers.memoize(() -> new ItemStack(Items.PAPER));
    private static final Supplier<ItemStack> CARD_STACK = Suppliers.memoize(() -> new ItemStack(ModItems.COPPER_PUNCH_CARD.get()));
    private static final Supplier<ItemStack> LEVER_STACK = Suppliers.memoize(() -> new ItemStack(Items.LEVER));
    private static final Supplier<ItemStack> HONEY_STACK = Suppliers.memoize(() -> new ItemStack(Items.HONEY_BOTTLE));
    private static final Supplier<ItemStack> BRUSH_STACK = Suppliers.memoize(() -> new ItemStack(Items.BRUSH));
    private static final Supplier<ItemStack> BOOK_STACK = Suppliers.memoize(() -> new ItemStack(Items.WRITABLE_BOOK));
    private static final Supplier<ItemStack> COPROCESSOR_STACK = Suppliers.memoize(() -> new ItemStack(ModItems.COMPRESSED_COPROCESSOR.get()));
    private static final Supplier<ItemStack> CHRONOMETER_STACK = Suppliers.memoize(() -> new ItemStack(ModItems.PORTABLE_CHRONOMETER.get()));

    /** Target pose for a smoothed copper golem: block-space position, facing vector, animation, arm override, and chest prop. */
    private record GolemPose(Vector3f at, Vector3f forward, CopperGolemPuppet.Anim anim, CopperGolemPuppet.Arms arms,
                             Consumer<PoseStack> chest) {
    }

    private MainframeCrew() {
    }

    /** Renders both embedded side atlants (k = 0 left, 1 right): breathing torso, gripping arms, and tracking head. */
    static void renderEmbeddedAtlants(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                      int light, float partialTick, float time, float spin) {
        for (int k = 0; k < 2; k++) {
            renderEmbeddedAtlant(be, pose, buffers, light, partialTick, k, time, spin);
        }
    }

    private static void renderEmbeddedAtlant(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers,
                                             int light, float pt, int k, float time, float spin) {
        boolean right = k == 1;
        VertexConsumer vc = buffers.getBuffer(RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS));
        // torso adjoining the cabinet wall (X <= -16), belt and shoulder plates
        float breathe = 0.25f * Mth.sin(time * 0.05f + k * 2f);
        mbox(vc, pose, right, -26, 12, 2.5f, -16f, 26 + breathe, 13.5f, ATLANT_BODY, WHITE, light);
        mbox(vc, pose, right, -26.5f, 11.5f, 2, -16f, 13.5f, 14, ATLANT_BELT, WHITE, light);
        mbox(vc, pose, right, -29, 22 + breathe, 1.5f, -24.5f, 27 + breathe, 14.5f, ATLANT_BELT, WHITE, light);
        mbox(vc, pose, right, -26.5f, 25.5f + breathe, 2.5f, -16f, 27.5f + breathe, 13.5f, ATLANT_BELT, WHITE, light);
        // outer arm: shoulder -> elbow at front corner -> palm gripping the front post
        float grip = spin > 0.01f ? 0.4f * Mth.sin(time * 0.3f + k) : 0f;
        float sx1 = mirrorX(right, -27.5f), ex1 = mirrorX(right, -28.5f), hx1 = mirrorX(right, -17.5f);
        limb(vc, pose, sx1, 24.5f + breathe, 6f, ex1, 16f, -1.5f, 4.2f, ATLANT_LIMB, light);
        limb(vc, pose, ex1, 16f, -1.5f, hx1, 17.5f + grip, -1.8f, 3.6f, ATLANT_LIMB, light);
        mbox(vc, pose, right, -18.5f, 15.5f + grip, -3.0f, -16.0f, 20 + grip, -0.6f, ATLANT_BELT, WHITE, light);
        // inner arm: raised with palm supporting the roof cornice
        float sx2 = mirrorX(right, -22f), ex2 = mirrorX(right, -23.5f), hx2 = mirrorX(right, -16.8f);
        limb(vc, pose, sx2, 26f + breathe, 12f, ex2, 33.5f, 11f, 3.8f, ATLANT_LIMB, light);
        limb(vc, pose, ex2, 33.5f, 11f, hx2, 37.2f, 8f, 3.4f, ATLANT_LIMB, light);
        mbox(vc, pose, right, -18.0f, 36.8f, 5.6f, -16.0f, 38.2f, 10.4f, ATLANT_BELT, WHITE, light);
        // enlarged atlant head at the upper corner turning toward the nearest player
        float yaw = be.headsPrev[k][0] + Mth.wrapDegrees(be.heads[k][0] - be.headsPrev[k][0]) * pt;
        float pitch = Mth.lerp(pt, be.headsPrev[k][1], be.heads[k][1]);
        pose.pushPose();
        pose.translate(mirrorX(right, -21.5f) * PIXEL, (27.5f + breathe) * PIXEL, 8f * PIXEL);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + (right ? -12f : 12f)));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.scale(1.15f, 1.15f, 1.15f);
        pose.translate(-0.5f, -6f * PIXEL, -0.5f);
        StandaloneModels.render(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), AtlantHeadRenderer.HEAD, 1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * Copper golem on the roof: turns the valve wheel while active; when idle taps the gauge, wipes the wheel with a rag,
     * stretches, and looks around.
     */
    static void renderRoofGolem(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float spin) {
        GolemPose g;
        if (spin > 0.3f) {
            float sway = Mth.sin(time * 0.35f) * 0.25f;
            g = new GolemPose(spot(24.5f, ROOF_FLOOR, 3.2f), new Vector3f(Mth.sin(sway) * 0.3f, 0f, -1f),
                    new CopperGolemPuppet.Anim(time * 0.3f, false, true, 0.25f + 0.1f * Mth.sin(time * 0.7f)), null, hp -> { });
        } else {
            float cyc = ((time + be.getBlockPos().asLong() % 97) / 520f) % 1f;
            if (cyc < 0.26f) {
                // at the gauge: leans over and taps the glass
                float tap = Math.max(0f, Mth.sin(time * 0.9f));
                g = new GolemPose(spot(-10.5f, ROOF_FLOOR, 3.4f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.28f),
                        new CopperGolemPuppet.Arms(-1.25f - 0.12f * tap, 0.1f, -0.2f, -0.1f, 0.15f * Mth.sin(time * 0.05f), null, null), null);
            } else if (cyc < 0.36f) {
                g = walk((cyc - 0.26f) / 0.10f, -10.5f, 3.4f, 24.5f, 3.2f, ROOF_FLOOR, time, null);
            } else if (cyc < 0.68f) {
                // wipes the valve wheel in circles with a paper rag
                float a = time * 0.35f;
                g = new GolemPose(spot(24.5f, ROOF_FLOOR, 3.2f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f),
                        new CopperGolemPuppet.Arms(-1.35f + 0.22f * Mth.sin(a), 0.25f + 0.2f * Mth.cos(a), -0.3f, -0.1f, 0f,
                                held(PAPER_STACK.get(), 0.3f, 0f, light, buffers), null), null);
            } else if (cyc < 0.78f) {
                g = walk((cyc - 0.68f) / 0.10f, 24.5f, 3.2f, 6.5f, 8f, ROOF_FLOOR, time, null);
            } else {
                // stretches arms overhead and looks around
                float t = (cyc - 0.78f) / 0.22f;
                float up = Mth.clamp(Mth.sin(t * Mth.PI) * 1.4f, 0f, 1f);
                g = new GolemPose(spot(6.5f, ROOF_FLOOR, 8f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                        new CopperGolemPuppet.Arms(-2.9f * up, 0.3f * up, -2.9f * up, -0.3f * up, 0.6f * Mth.sin(time * 0.06f), null, null), null);
            }
        }
        renderSmooth(be, 2, pose, buffers, light, time, g, ROOF_GOLEM_SCALE, 1);
    }

    /**
     * Copper golems on the two interior shelves:
     * Shelf 0 (chief cryo engineer) calibrates calculators, turns the manifold valve, checks punch cards, or cleans/reads when idle.
     * Shelf 1 (coprocessor operator) carries coprocessors and feeds cards when active, or polishes/sorts/winds chronometer when idle.
     */
    static void renderShelfGolems(CryoMainframeBlockEntity be, PoseStack pose, MultiBufferSource buffers, int light, float time, float spin) {
        renderSmooth(be, 0, pose, buffers, light, time, shelf0Pose(be, buffers, light, time, spin), SHELF_GOLEM_SCALE, 1);
        renderSmooth(be, 1, pose, buffers, light, time, shelf1Pose(be, buffers, light, time, spin), SHELF_GOLEM_SCALE, 2);
    }

    private static GolemPose shelf0Pose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = SHELF_0_FLOOR * PIXEL;
        if (spin <= 0.05f) {
            return shelf0IdlePose(be, buffers, light, time, floor);
        }
        // work: calculator calibration, manifold valve, punch card check
        float cycle = ((time / 160f) + 0.12f) % 1.0f;
        if (cycle < 0.40f) {
            float ratchet = Mth.sin(time * 0.75f);
            Consumer<PoseStack> wrench = held(LEVER_STACK.get(), 0.35f, 90f, light, buffers);
            return new GolemPose(spot(-5.3f, floor, CALC_FRONT), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.3f, false, false, 0.26f),
                    new CopperGolemPuppet.Arms(-1.15f + 0.28f * ratchet, 0.12f, -0.65f, -0.1f, 0.15f * Mth.sin(time * 0.3f), wrench, null), null);
        }
        if (cycle < 0.48f) {
            return walk((cycle - 0.40f) / 0.08f, -5.3f, CALC_FRONT, -12.2f, 19.5f, floor, time, held(LEVER_STACK.get(), 0.35f, 90f, light, buffers));
        }
        if (cycle < 0.72f) {
            float valvePump = Mth.sin(time * 0.45f);
            return new GolemPose(spot(-12.2f, floor, 19.5f), new Vector3f(-0.8f, 0f, 0.6f),
                    new CopperGolemPuppet.Anim(time * 0.4f, false, false, 0.16f + 0.10f * valvePump),
                    new CopperGolemPuppet.Arms(-1.3f + 0.15f * valvePump, 0.22f, -1.3f - 0.15f * valvePump, -0.22f, 0f, null, null), null);
        }
        if (cycle < 0.80f) {
            return walk((cycle - 0.72f) / 0.08f, -12.2f, 19.5f, 2.0f, 18.0f, floor, time, null);
        }
        return new GolemPose(spot(2.0f, floor, 18.0f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.2f, false, true, 0.08f),
                new CopperGolemPuppet.Arms(-0.95f, 0.14f, -0.95f, -0.14f, 0.18f * Mth.sin(time * 0.1f), null, null),
                held(CARD_STACK.get(), 0.4f, 0f, light, buffers));
    }

    private static GolemPose shelf0IdlePose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float floor) {
        float cyc = ((time + be.getBlockPos().asLong() % 131) / 720f) % 1f;
        if (cyc < 0.20f) {
            // wipes the door glass from inside in circles
            float a = time * 0.4f;
            return new GolemPose(spot(-9f, floor, 5.5f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.04f),
                    new CopperGolemPuppet.Arms(-1.75f + 0.25f * Mth.sin(a), 0.3f + 0.25f * Mth.cos(a), -0.25f, -0.1f, 0.1f * Mth.sin(time * 0.05f),
                            held(PAPER_STACK.get(), 0.3f, 0f, light, buffers), null), null);
        }
        if (cyc < 0.26f) {
            return walk((cyc - 0.20f) / 0.06f, -9f, 5.5f, -5.3f, 18.5f, floor, time, null);
        }
        if (cyc < 0.46f) {
            // oils a calculator with a honey-bottle oil can
            float pump = Mth.sin(time * 0.8f);
            return new GolemPose(spot(-5.3f, floor, 18.5f), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.32f),
                    new CopperGolemPuppet.Arms(-0.95f + 0.12f * pump, 0.12f, -0.5f, -0.12f, 0.1f * pump,
                            held(HONEY_STACK.get(), 0.32f, 0f, light, buffers), null), null);
        }
        if (cyc < 0.52f) {
            return walk((cyc - 0.46f) / 0.06f, -5.3f, 18.5f, 12f, 17f, floor, time, null);
        }
        if (cyc < 0.74f) {
            // sweeps frost along the shelf with a brush
            float t = (cyc - 0.52f) / 0.22f;
            float x = 12f + 9f * (0.5f - 0.5f * Mth.cos(t * Mth.PI * 2f));
            float dir = Mth.sin(t * Mth.PI * 2f) >= 0f ? 1f : -1f;
            float sweep = Mth.sin(time * 0.6f);
            return new GolemPose(spot(x, floor, 17f), new Vector3f(dir, 0f, 0.15f), new CopperGolemPuppet.Anim(time * 0.25f, true, false, 0.22f),
                    new CopperGolemPuppet.Arms(-0.7f + 0.25f * sweep, 0.1f, -0.7f + 0.25f * sweep, -0.1f, 0f,
                            held(BRUSH_STACK.get(), 0.34f, 90f, light, buffers), null), null);
        }
        if (cyc < 0.80f) {
            return walk((cyc - 0.74f) / 0.06f, 12f, 17f, 2f, 17.5f, floor, time, null);
        }
        // reads the shift log and turns a page now and then
        float flip = Math.max(0f, Mth.sin(time * 0.05f) - 0.9f) * 10f;
        return new GolemPose(spot(2f, floor, 17.5f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.12f),
                new CopperGolemPuppet.Arms(-0.95f - 0.5f * flip, 0.18f, -0.95f, -0.18f, 0.12f * Mth.sin(time * 0.02f),
                        held(BOOK_STACK.get(), 0.34f, 0f, light, buffers), null), null);
    }

    private static GolemPose shelf1Pose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float spin) {
        float floor = SHELF_1_FLOOR * PIXEL;
        if (spin <= 0.05f) {
            return shelf1IdlePose(be, buffers, light, time, floor);
        }
        // work: carries a compressed coprocessor to the assemblers, then feeds a punch card into the reader
        float cycle = ((time / 160f) + 0.62f) % 1.0f;
        if (cycle < 0.42f) {
            float t = cycle / 0.42f;
            return new GolemPose(spot(Mth.lerp(t, 22.0f, 6.5f), floor, 18.0f), new Vector3f(-1f, 0f, 0f),
                    new CopperGolemPuppet.Anim(time * 0.5f, true, true, 0.1f), null, held(COPROCESSOR_STACK.get(), 0.38f, 0f, light, buffers));
        }
        if (cycle < 0.65f) {
            float lift = Mth.clamp((cycle - 0.42f) / 0.06f, 0f, 1f);
            float rX = Mth.lerp(lift, -1.25f, -1.55f);
            return new GolemPose(spot(6.5f, floor, 17.0f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.3f, false, true, 0f),
                    new CopperGolemPuppet.Arms(rX, 0.2f, rX, -0.2f, 0f, null, null), held(COPROCESSOR_STACK.get(), 0.38f, 0f, light, buffers));
        }
        if (cycle < 0.73f) {
            return walk((cycle - 0.65f) / 0.08f, 6.5f, 17.0f, 16.3f, CALC_FRONT, floor, time, held(CARD_STACK.get(), 0.35f, 90f, light, buffers));
        }
        float slide = 0.5f + 0.5f * Mth.sin(time * 0.7f);
        return new GolemPose(spot(16.3f, floor, CALC_FRONT), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.3f, false, false, 0.15f),
                new CopperGolemPuppet.Arms(-1.3f + 0.25f * slide, 0.08f, -0.6f, -0.15f, 0f, held(CARD_STACK.get(), 0.35f, 90f, light, buffers), null), null);
    }

    private static GolemPose shelf1IdlePose(CryoMainframeBlockEntity be, MultiBufferSource buffers, int light, float time, float floor) {
        // calculators in slots 7..12 occupy the back of shelf 1: stop in front of them rather than at the matrix wall
        boolean shelfFilled = be.clientCalcs() > CryoMainframeBlockEntity.CALC_SLOTS / 2;
        float polishZ = shelfFilled ? CALC_FRONT : 25.5f;
        float sortZ = shelfFilled ? CALC_FRONT : 23.5f;
        float cyc = ((time + be.getBlockPos().asLong() % 113) / 760f + 0.5f) % 1f;
        if (cyc < 0.22f) {
            // polishes the glowing matrix wall (or calculators in front of it) side to side
            float a = time * 0.3f;
            return new GolemPose(spot(-2f, floor, polishZ), new Vector3f(0f, 0f, 1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                    new CopperGolemPuppet.Arms((shelfFilled ? -1.45f : -2.35f) + 0.1f * Mth.cos(a), 0.45f * Mth.sin(a), -0.3f, -0.1f,
                            0.2f * Mth.sin(a), held(PAPER_STACK.get(), 0.3f, 0f, light, buffers), null), null);
        }
        if (cyc < 0.28f) {
            return walk((cyc - 0.22f) / 0.06f, -2f, polishZ, 20f, sortZ, floor, time, null);
        }
        if (cyc < 0.50f) {
            // sorts punch cards into stacks alternating hands
            float sw = Mth.sin(time * 0.3f);
            Consumer<PoseStack> card = held(CARD_STACK.get(), 0.35f, 90f, light, buffers);
            return new GolemPose(spot(20f, floor, sortZ), new Vector3f(0.6f, 0f, 0.8f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.2f),
                    new CopperGolemPuppet.Arms(-0.8f - 0.35f * Math.max(0f, sw), 0.15f, -0.8f - 0.35f * Math.max(0f, -sw), -0.15f, 0.3f * sw,
                            sw > 0f ? card : null, sw <= 0f ? card : null), null);
        }
        if (cyc < 0.56f) {
            return walk((cyc - 0.50f) / 0.06f, 20f, sortZ, 8f, 18f, floor, time, null);
        }
        if (cyc < 0.78f) {
            // winds the chronometer held in its left hand
            float a = time * 0.5f;
            return new GolemPose(spot(8f, floor, 18f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0.1f),
                    new CopperGolemPuppet.Arms(-1.05f + 0.1f * Mth.sin(a), 0.35f + 0.1f * Mth.cos(a), -1.05f, -0.2f, -0.15f, null,
                            held(CHRONOMETER_STACK.get(), 0.32f, 90f, light, buffers)), null);
        }
        if (cyc < 0.84f) {
            return walk((cyc - 0.78f) / 0.06f, 8f, 18f, -9f, 19f, floor, time, null);
        }
        // stretches and looks over the matrix
        float t = (cyc - 0.84f) / 0.16f;
        float up = Mth.clamp(Mth.sin(t * Mth.PI) * 1.4f, 0f, 1f);
        return new GolemPose(spot(-9f, floor, 19f), new Vector3f(0f, 0f, -1f), new CopperGolemPuppet.Anim(time * 0.1f, false, false, 0f),
                new CopperGolemPuppet.Arms(-2.9f * up, 0.3f * up, -2.9f * up, -0.3f * up, 0.5f * Mth.sin(time * 0.05f), null, null), null);
    }

    /** Exponentially smooths position and yaw so switching between idle and work routines never teleports a golem. */
    private static void renderSmooth(CryoMainframeBlockEntity be, int idx, PoseStack pose, MultiBufferSource buffers,
                                     int light, float time, GolemPose g, float scale, int variant) {
        float[] s = be.golemSmooth[idx];
        float tyaw = (float) Math.atan2(g.forward().x, g.forward().z);
        float dt = time - s[4];
        if (dt < -36000f) {
            dt += 72000f;
        }
        if (s[5] == 0f || dt < 0f || dt > 40f) {
            s[0] = g.at().x;
            s[1] = g.at().y;
            s[2] = g.at().z;
            s[3] = tyaw;
            s[5] = 1f;
        } else {
            float a = 1f - (float) Math.exp(-dt * 0.2f);
            s[0] += (g.at().x - s[0]) * a;
            s[1] += (g.at().y - s[1]) * a;
            s[2] += (g.at().z - s[2]) * a;
            float dyaw = Mth.wrapDegrees((float) Math.toDegrees(tyaw - s[3]));
            s[3] += (float) Math.toRadians(dyaw) * a;
        }
        s[4] = time;
        float lag = (float) Math.hypot(g.at().x - s[0], g.at().z - s[2]);
        CopperGolemPuppet.Anim anim = g.anim();
        if (lag > 0.03f && !anim.walking()) {
            anim = new CopperGolemPuppet.Anim(time * 0.5f, true, anim.carrying(), anim.bow());
        }
        SCRATCH_FWD.set(Mth.sin(s[3]), 0f, Mth.cos(s[3]));
        SCRATCH_AT.set(s[0], s[1], s[2]);
        CopperGolemPuppet.render(pose, buffers, light, SCRATCH_AT, SCRATCH_FWD, UP, scale, anim, variant, g.chest(), g.arms());
    }

    /** Moving between two shelf points over {@code t} in {@code [0, 1]}, walking and looking along the path. */
    private static GolemPose walk(float t, float x0, float z0, float x1, float z1, float floor, float time,
                                  @Nullable Consumer<PoseStack> inHand) {
        float e = smoothstep(t);
        Vector3f at = spot(Mth.lerp(e, x0, x1), floor, Mth.lerp(e, z0, z1));
        Vector3f fwd = new Vector3f(x1 - x0, 0f, z1 - z0);
        if (fwd.lengthSquared() < 1e-4f) {
            fwd.set(0f, 0f, -1f);
        }
        return new GolemPose(at, fwd.normalize(), new CopperGolemPuppet.Anim(time * 0.5f, true, false, 0f),
                new CopperGolemPuppet.Arms(-0.35f, 0.08f, -0.35f, -0.08f, 0f, inHand, null), null);
    }

    private static Consumer<PoseStack> held(ItemStack stack, float scale, float rotX, int light, MultiBufferSource buffers) {
        return hp -> {
            hp.scale(scale, scale, scale);
            if (rotX != 0f) {
                hp.mulPose(Axis.XP.rotationDegrees(rotX));
            }
            BerBuffers.item(buffers, stack, ItemDisplayContext.FIXED, light, hp, null, 0);
        };
    }

    private static Vector3f spot(float x, float floor, float z) {
        return new Vector3f(x * PIXEL, floor, z * PIXEL);
    }

    private static float mirrorX(boolean mirror, float x) {
        return mirror ? 16f - x : x;
    }
}
